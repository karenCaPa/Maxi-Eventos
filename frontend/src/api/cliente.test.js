import { describe, expect, it, vi } from 'vitest';
import { iniciarSesionDePrueba, simularGateway, tokenDePrueba } from '../test/utilidades.jsx';
import { EVENTO_SESION_VENCIDA, guardarToken, obtenerToken } from '../sesion/almacen.js';
import {
  aErrorVisible, ejecutar, erroresPorSeccion, ErrorApi, ErrorSesion, MENSAJE_SECCION, MENSAJE_TECNICO, mutar,
} from './cliente.js';
import { descargarDocumento } from './descargas.js';

describe('cliente GraphQL', () => {
  it('envía el token en Authorization y la operación al /graphql del gateway', async () => {
    iniciarSesionDePrueba();
    const fetch = simularGateway({ data: { ok: true } });
    await ejecutar('{ ok }', { a: 1 });
    const [url, opciones] = fetch.mock.calls[0];
    expect(url).toBe('http://localhost:8090/graphql');
    expect(opciones.headers.Authorization).toMatch(/^Bearer .+\..+\..+$/);
    expect(JSON.parse(opciones.body)).toEqual({ query: '{ ok }', variables: { a: 1 } });
  });

  it('UNAUTHORIZED del gateway (token vencido): borra el token, avisa a la aplicación y lanza ErrorSesion', async () => {
    iniciarSesionDePrueba();
    simularGateway({
      errors: [{ message: 'El token venció. Inicia sesión de nuevo con la mutation login.',
        extensions: { classification: 'UNAUTHORIZED', status: 401, servicio: 'gateway' } }],
    });
    const aviso = vi.fn();
    window.addEventListener(EVENTO_SESION_VENCIDA, aviso);

    await expect(ejecutar('{ eventos { totalElementos } }')).rejects.toBeInstanceOf(ErrorSesion);

    expect(obtenerToken()).toBeNull();
    expect(window.sessionStorage.length).toBe(0);
    expect(aviso).toHaveBeenCalledTimes(1);
    expect(aviso.mock.calls[0][0].detail.mensaje).toMatch(/sesión venció/);
    window.removeEventListener(EVENTO_SESION_VENCIDA, aviso);
  });

  it('un token ya vencido se descarta sin llamar al gateway', async () => {
    guardarToken(tokenDePrueba({ venceEnSegundos: -10 }));
    const fetch = simularGateway();
    await expect(ejecutar('{ ok }')).rejects.toBeInstanceOf(ErrorSesion);
    expect(fetch).not.toHaveBeenCalled();
    expect(window.sessionStorage.length).toBe(0);
  });

  it('BAD_REQUEST con extensions.errores: el error trae el mensaje de cada campo', async () => {
    iniciarSesionDePrueba();
    simularGateway({
      data: null,
      errors: [{
        message: 'Los datos enviados no son válidos. Revisa los campos indicados.',
        path: ['crearCliente'],
        extensions: {
          classification: 'BAD_REQUEST', status: 400, servicio: 'clientes',
          errores: [
            { campo: 'correo', mensaje: 'debe ser una dirección de correo electrónico con formato correcto' },
            { campo: 'telefono', mensaje: 'no debe estar vacío' },
          ],
        },
      }],
    });

    const error = await mutar('mutation { crearCliente }').catch((e) => e);

    expect(error).toBeInstanceOf(ErrorApi);
    expect(error.clasificacion).toBe('BAD_REQUEST');
    expect(error.servicio).toBe('clientes');
    expect(error.message).toBe('Los datos enviados no son válidos. Revisa los campos indicados.');
    expect(error.campos).toEqual({
      correo: 'debe ser una dirección de correo electrónico con formato correcto',
      telefono: 'no debe estar vacío',
    });
  });

  it('error parcial: devuelve los datos y la sección muestra el mensaje genérico con la referencia (el detalle no se ve)', async () => {
    iniciarSesionDePrueba();
    const servicioCaido = {
      message: 'El servicio de contratos no está disponible en este momento. Intenta de nuevo más tarde.',
      path: ['evento', 'contrato'],
      extensions: { classification: 'SERVICE_UNAVAILABLE', status: 503, servicio: 'contratos' },
    };
    const fetch = simularGateway({ data: { evento: { estado: 'RESERVADO', contrato: null } }, errors: [servicioCaido] });

    const { data, errors, referencia } = await ejecutar('query { evento(id: "x") { estado contrato { estado } } }');
    const secciones = erroresPorSeccion(errors, 'evento', referencia);

    expect(data.evento.estado).toBe('RESERVADO');
    expect(Object.keys(secciones)).toEqual(['contrato']);
    expect(secciones.contrato.clasificacion).toBe('SERVICE_UNAVAILABLE');
    expect(secciones.contrato.message).toBe(`${MENSAJE_SECCION} Ref: ${codigo(fetch)}`);
    expect(secciones.contrato.message).not.toMatch(/contratos/);
    expect(secciones.contrato.detalle).toMatch(/contratos no está disponible/);
  });

  it('sin conexión con el gateway: mensaje genérico, sin referencia (la petición no llegó al gateway)', async () => {
    iniciarSesionDePrueba();
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))));
    const error = await ejecutar('{ ok }').catch((e) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect(error.tecnico).toBe(true);
    expect(error.message).toBe(MENSAJE_TECNICO);
    expect(error.detalle).toMatch(/Failed to fetch/);
  });
});

/** Código mostrado = los 8 primeros caracteres (sin guiones, en mayúsculas) del X-Request-Id de la llamada n. */
const codigo = (fetch, n = 0) => fetch.mock.calls[n][1].headers['X-Request-Id'].replace(/-/g, '').slice(0, 8).toUpperCase();
const errorGql = (classification, message, servicio = 'eventos') => ({
  data: null, errors: [{ message, path: ['operacion'], extensions: { classification, servicio } }],
});

describe('Fase 11C: código de referencia y clasificación de los errores', () => {
  it('cada petición lleva su propio X-Request-Id (UUID)', async () => {
    iniciarSesionDePrueba();
    const fetch = simularGateway({ data: { ok: 1 } }, { data: { ok: 2 } });
    const a = await ejecutar('{ ok }');
    const b = await ejecutar('{ ok }');
    const [ida, idb] = fetch.mock.calls.map(([, o]) => o.headers['X-Request-Id']);
    expect(ida).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    expect(idb).not.toBe(ida);
    expect([a.referencia, b.referencia]).toEqual([ida, idb]);
  });

  it.each([
    ['SERVICE_UNAVAILABLE', 'El servicio de finanzas no está disponible en este momento.'],
    ['INTERNAL_ERROR', 'Error interno del gateway. Intenta de nuevo; si persiste, revisa el log del gateway.'],
    ['ValidationError', "Validation error (FieldUndefined@[evento/x]) : Field 'x' in type 'Evento' is undefined"],
    ['ALGO_NUEVO', 'java.lang.IllegalStateException: detalle interno'],
  ])('técnico %s → mensaje genérico con "Ref", sin el detalle en pantalla', async (clasificacion, detalle) => {
    iniciarSesionDePrueba();
    const fetch = simularGateway(errorGql(clasificacion, detalle));
    const error = await mutar('mutation Operacion { operacion }').catch((e) => e);
    expect(error.tecnico).toBe(true);
    expect(error.message).toBe(`${MENSAJE_TECNICO} Ref: ${codigo(fetch)}`);
    expect(error.message).not.toContain(detalle);
    expect(error.detalle).toBe(detalle);
    expect(error.clasificacion).toBe(clasificacion);
  });

  it.each([
    ['CONFLICT', 'El contrato del evento está en estado PENDIENTE; debe estar FIRMADO para confirmar el evento.'],
    ['NOT_FOUND', 'No existe un evento con id 11111111-1111-1111-1111-111111111111'],
    ['BAD_REQUEST', 'Los datos enviados no son válidos. Revisa los campos indicados.'],
    ['FORBIDDEN', 'No tienes permiso para realizar esta operación.'],
  ])('negocio / validación %s → se muestra el mensaje del servicio, sin "Ref"', async (clasificacion, mensaje) => {
    iniciarSesionDePrueba();
    simularGateway(errorGql(clasificacion, mensaje));
    const error = await mutar('mutation Operacion { operacion }').catch((e) => e);
    expect(error.tecnico).toBe(false);
    expect(error.message).toBe(mensaje);
  });

  it('credenciales incorrectas en el login (UNAUTHORIZED de auth) → su mensaje, sin cerrar sesión', async () => {
    simularGateway(errorGql('UNAUTHORIZED', 'Correo o contraseña incorrectos.', 'auth'));
    const error = await mutar('mutation Login { login }', {}, { autenticar: false }).catch((e) => e);
    expect(error).not.toBeInstanceOf(ErrorSesion);
    expect(error.message).toBe('Correo o contraseña incorrectos.');
  });

  it('respuesta que no es GraphQL (HTTP 502 de un proxy) → genérico con la referencia', async () => {
    iniciarSesionDePrueba();
    const fetch = vi.fn(() => Promise.resolve(new Response('<html>Bad Gateway</html>', { status: 502 })));
    vi.stubGlobal('fetch', fetch);
    const error = await ejecutar('{ ok }').catch((e) => e);
    expect(error.message).toBe(`${MENSAJE_TECNICO} Ref: ${codigo(fetch)}`);
    expect(error.message).not.toMatch(/502|HTTP/);
    expect(error.status).toBe(502);
  });

  it('un error de programación (no ErrorApi) también se muestra como genérico', () => {
    const error = aErrorVisible(new TypeError("Cannot read properties of undefined (reading 'x')"));
    expect(error.message).toBe(MENSAJE_TECNICO);
    expect(error.detalle).toMatch(/Cannot read properties/);
    const negocio = new ErrorApi('Ya existe un proveedor con ese nombre.', { clasificacion: 'CONFLICT' });
    expect(aErrorVisible(negocio)).toBe(negocio);
  });

  it('descarga de un PDF: 503 → genérico con la referencia; 409 → el mensaje de Contratos; ambas envían X-Request-Id', async () => {
    iniciarSesionDePrueba();
    const json = (status, message) => new Response(JSON.stringify({ status, message }), { status, headers: { 'Content-Type': 'application/json' } });
    const fetch = vi.fn()
      .mockImplementationOnce(() => Promise.resolve(json(503, 'El servicio de contratos no está disponible en este momento.')))
      .mockImplementationOnce(() => Promise.resolve(json(409, 'El evento no tiene servicios adicionales; no hay anexo que generar.')));
    vi.stubGlobal('fetch', fetch);

    const caido = await descargarDocumento('ev-1', 'contrato').catch((e) => e);
    expect(caido.message).toBe(`${MENSAJE_TECNICO} Ref: ${codigo(fetch, 0)}`);
    const conflicto = await descargarDocumento('ev-1', 'anexo').catch((e) => e);
    expect(conflicto.message).toBe('El evento no tiene servicios adicionales; no hay anexo que generar.');
    expect(fetch.mock.calls[1][1].headers['X-Request-Id']).toMatch(/^[0-9a-f-]{36}$/);
  });
});
