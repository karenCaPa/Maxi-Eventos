import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { fichaDemo } from '../test/fichaDemo.js';
import { diferida, iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';

const seccion = (titulo) => screen.getByRole('region', { name: titulo });
const conflicto = (message, path) => ({
  data: null, errors: [{ message, path: [path], extensions: { classification: 'CONFLICT', status: 409, servicio: 'eventos' } }],
});

/** Evento PENDIENTE con cotización, contrato sin firmar y sin logística (aún no confirmado). */
function eventoPendiente() {
  const e = fichaDemo();
  return {
    ...e, estado: 'PENDIENTE', logistica: null, notificaciones: { totalElementos: 0, contenido: [] }, auditoria: { totalElementos: 0, contenido: [] },
    contrato: { ...e.contrato, estado: 'PENDIENTE', fechaFirma: null },
  };
}

describe('Acciones de la ficha', () => {
  it('proveedores: una asignación PENDIENTE se confirma con un botón; las confirmadas no lo muestran', async () => {
    iniciarSesionDePrueba();
    const base = fichaDemo();
    const pendiente = { ...base.proveedores.contenido[0], id: 'a2', servicio: 'Fotografía', estado: 'PENDIENTE' };
    const conPendiente = { ...base, proveedores: { totalElementos: 2, contenido: [base.proveedores.contenido[0], pendiente] } };
    const confirmada = { ...conPendiente, proveedores: { totalElementos: 2, contenido: [base.proveedores.contenido[0], { ...pendiente, estado: 'CONFIRMADO' }] } };
    const { llamadas } = simularPorOperacion({
      FichaEvento: (v, n) => ({ data: { evento: n === 1 ? conPendiente : confirmada } }),
      ConfirmarAsignacion: { data: { cambiarEstadoAsignacion: { id: 'a2', estado: 'CONFIRMADO' } } },
    });
    renderizarApp(`/eventos/${base.id}?paso=todo`);

    const tabla = await screen.findByRole('region', { name: 'Proveedores asignados' });
    const botones = within(tabla).getAllByRole('button', { name: 'Confirmar' });
    expect(botones).toHaveLength(1); // solo la pendiente
    fireEvent.click(botones[0]);

    expect(await screen.findByText(/quedó confirmado para «Fotografía»/)).toBeTruthy();
    expect(llamadas('ConfirmarAsignacion')).toEqual([{ id: 'a2' }]);
    expect(llamadas('FichaEvento')).toHaveLength(2);
    expect(within(screen.getByRole('region', { name: 'Proveedores asignados' })).queryByRole('button', { name: 'Confirmar' })).toBeNull();
  });

  it('confirmar sin contrato firmado: el botón trabaja mientras espera y el 409 se muestra con el mensaje de Eventos', async () => {
    iniciarSesionDePrueba();
    const evento = eventoPendiente();
    const respuesta = diferida();
    const { llamadas } = simularPorOperacion({ FichaEvento: { data: { evento } }, ConfirmarEvento: () => respuesta.promesa });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);

    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar evento' }));

    const trabajando = screen.getByRole('button', { name: 'Confirmando…' });
    expect(trabajando.disabled).toBe(true);
    fireEvent.click(trabajando); // doble clic: no hace una segunda petición
    respuesta.resolver(conflicto('El contrato del evento está en estado PENDIENTE; debe estar FIRMADO para confirmar el evento.', 'confirmarEvento'));

    const alerta = await screen.findByRole('alert');
    expect(alerta.textContent).toMatch(/No se puede realizar esta acción en el estado actual/);
    expect(alerta.textContent).toMatch(/debe estar FIRMADO para confirmar el evento/);
    expect(llamadas('ConfirmarEvento')).toEqual([{ id: evento.id }]);
    expect(llamadas('FichaEvento')).toHaveLength(1); // ante un error NO se recarga
    expect(screen.getByRole('button', { name: 'Confirmar evento' }).disabled).toBe(false);
  });

  it('Fase 11C: si Eventos no responde (503), la acción muestra el mensaje genérico con la referencia, no el técnico', async () => {
    iniciarSesionDePrueba();
    const evento = eventoPendiente();
    const { fetch } = simularPorOperacion({
      FichaEvento: { data: { evento } },
      ConfirmarEvento: { data: null, errors: [{ message: 'El servicio de contratos no está disponible en este momento. Intenta de nuevo más tarde.',
        path: ['confirmarEvento'], extensions: { classification: 'SERVICE_UNAVAILABLE', status: 503, servicio: 'eventos' } }] },
    });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);
    fireEvent.click(await screen.findByRole('button', { name: 'Confirmar evento' }));

    const alerta = await screen.findByRole('alert');
    const llamada = fetch.mock.calls.find(([, o]) => /mutation ConfirmarEvento/.test(JSON.parse(o.body).query));
    const ref = llamada[1].headers['X-Request-Id'].replace(/-/g, '').slice(0, 8).toUpperCase();
    expect(alerta.textContent).toBe(`No se pudo realizar la acción.No se pudo completar la operación. Intenta de nuevo en unos minutos. Ref: ${ref}`);
    expect(alerta.textContent).not.toMatch(/contratos|disponible|503/);
  });

  it('una acción exitosa recarga la ficha COMPLETA y muestra el estado real del backend', async () => {
    iniciarSesionDePrueba();
    const sinCotizacion = { ...eventoPendiente(), cotizacion: null, balance: null, contrato: null, pagos: { totalElementos: 0, contenido: [] } };
    const conCotizacion = { ...sinCotizacion, cotizacion: fichaDemo().cotizacion, balance: { ...fichaDemo().balance, montoAbonado: 0, saldoPendiente: 8880000 } };
    const { llamadas } = simularPorOperacion({
      FichaEvento: (v, n) => ({ data: { evento: n === 1 ? sinCotizacion : conCotizacion } }),
      GenerarCotizacion: { data: { generarCotizacion: { id: 'c1' } } },
    });
    renderizarApp(`/eventos/${sinCotizacion.id}?paso=todo`);

    const boton = await screen.findByRole('button', { name: 'Generar cotización' });
    fireEvent.click(boton);
    fireEvent.click(boton);

    expect((await screen.findByText('Cotización generada.')).closest('[role="status"]')).toBeTruthy();
    expect(llamadas('GenerarCotizacion')).toHaveLength(1);
    expect(llamadas('FichaEvento')).toHaveLength(2); // carga inicial + recarga completa tras la acción
    expect(within(seccion('Cotización')).getByText(/8\.880\.000/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Generar cotización' })).toBeNull(); // ya tiene cotización
    expect(within(seccion('Contrato')).getByRole('button', { name: 'Crear contrato' })).toBeTruthy();
  });

  it('registrar pago: error junto al campo, y la misma clave de idempotencia al reintentar el mismo formulario', async () => {
    iniciarSesionDePrueba();
    const evento = eventoPendiente();
    const { llamadas } = simularPorOperacion({
      FichaEvento: { data: { evento } },
      RegistrarPago: (v, n) => (n === 1
        ? { data: null, errors: [{ message: 'Los datos enviados no son válidos. Revisa los campos indicados.', path: ['registrarPago'],
          extensions: { classification: 'BAD_REQUEST', status: 400, servicio: 'finanzas', errores: [{ campo: 'monto', mensaje: 'debe ser mayor que 0' }] } }] }
        : { data: { registrarPago: { id: 'p3', monto: 1000 } } }),
    });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);

    fireEvent.click(await screen.findByRole('button', { name: 'Registrar pago' }));
    const monto = screen.getByLabelText('Monto (COP)');
    fireEvent.change(monto, { target: { value: '0' } });
    fireEvent.change(screen.getByLabelText('Método de pago'), { target: { value: 'Transferencia' } });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar pago' }));

    expect(await screen.findByText('debe ser mayor que 0')).toBeTruthy();
    expect(monto.getAttribute('aria-invalid')).toBe('true');

    fireEvent.change(monto, { target: { value: '1000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar pago' }));
    expect(await screen.findByText(/Pago de .*1\.000 registrado/)).toBeTruthy();

    const [primero, segundo] = llamadas('RegistrarPago').map((v) => v.input);
    expect(primero.claveIdempotencia).toMatch(/^interfaz-/);
    expect(segundo.claveIdempotencia).toBe(primero.claveIdempotencia);
    expect(segundo).toMatchObject({ eventoId: evento.id, clienteId: evento.clienteId, monto: 1000, moneda: 'COP', metodoPago: 'Transferencia' });
    expect(llamadas('FichaEvento')).toHaveLength(2);
    expect(screen.queryByLabelText('Monto (COP)')).toBeNull(); // el formulario se cerró
  });

  it('descargar PDF: pide PDF o JSON con el token; el 409 del anexo se muestra con el mensaje de Contratos', async () => {
    iniciarSesionDePrueba();
    const evento = fichaDemo();
    URL.createObjectURL = vi.fn(() => 'blob:contrato');
    URL.revokeObjectURL = vi.fn();
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const { fetch, llamadas } = simularPorOperacion({
      FichaEvento: { data: { evento } },
      documentos: (url) => (url.endsWith('/anexo.pdf')
        ? new Response(JSON.stringify({ status: 409, error: 'Conflict', message: 'El evento no tiene servicios adicionales; no hay anexo que generar.' }),
          { status: 409, headers: { 'Content-Type': 'application/json' } })
        : new Response('%PDF-1.7 contrato', { status: 200, headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="contrato-${evento.id}.pdf"` } })),
    });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);

    fireEvent.click(await screen.findByRole('button', { name: 'Descargar contrato (PDF)' }));
    expect(await screen.findByText(`Se descargó contrato-${evento.id}.pdf.`)).toBeTruthy();
    const [url, opciones] = fetch.mock.calls.find(([u]) => String(u).includes('/documentos/'));
    expect(url).toBe(`http://localhost:8090/documentos/eventos/${evento.id}/contrato.pdf`);
    expect(opciones.headers).toMatchObject({ Accept: 'application/pdf, application/json', Authorization: expect.stringMatching(/^Bearer /) });
    expect(clic).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Descargar anexo (PDF)' }));
    expect((await within(seccion('Contrato')).findByRole('alert')).textContent).toMatch(/no tiene servicios adicionales/);
    expect(llamadas('FichaEvento')).toHaveLength(1); // descargar es solo lectura: no recarga
  });

  it('un evento CANCELADO no muestra acciones de cambio', async () => {
    iniciarSesionDePrueba();
    const evento = { ...fichaDemo(), estado: 'CANCELADO' };
    simularPorOperacion({ FichaEvento: { data: { evento } } });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);

    expect(await screen.findByText('Cancelado')).toBeTruthy();
    for (const nombre of ['Confirmar evento', 'Cancelar evento', 'Registrar pago', 'Asignar proveedor', 'Cambiar lugar', 'Agregar servicio adicional']) {
      expect(screen.queryByRole('button', { name: nombre })).toBeNull();
    }
    expect(screen.queryAllByRole('checkbox')).toHaveLength(0);
    expect(screen.getByRole('button', { name: 'Descargar contrato (PDF)' })).toBeTruthy();
  });
});
