import { fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fichaDemo } from '../test/fichaDemo.js';
import { fijarHoy, respuestaFechas } from '../test/fechasSimuladas.js';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';

const LUCIA = { id: 'e146c96a-919d-4e82-b1c9-ebda1bca8b92', nombreCompleto: 'Lucía Fernández Ospina', telefono: '3101234567', correo: 'l@ejemplo.com' };
const OCOBOS = { id: 'l-ocobos', nombre: 'Salón Los Ocobos', tipo: 'SALON', capacidad: 80, estado: 'ACTIVO' };
const SABADO = '2026-11-07';
const DIA_SABADO = 'sábado, 7 de noviembre de 2026, disponible';
const MES = { desde: '2026-11-01', hasta: '2026-11-30' };
const base = (extra) => ({
  Clientes: { data: { clientes: { totalElementos: 1, contenido: [LUCIA] } } },
  // el calendario pide el mes; la comprobación final pide UNA fecha (desde = hasta)
  FechasDisponibles: respuestaFechas(),
  Lugares: { data: { lugares: { pagina: 0, totalElementos: 1, totalPaginas: 1, contenido: [OCOBOS] } } },
  ...extra,
});
const cambiar = (etiqueta, value) => fireEvent.change(screen.getByLabelText(etiqueta), { target: { value } });
const siguiente = () => fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
const crear = () => fireEvent.click(screen.getByRole('button', { name: 'Crear evento' }));
const operaciones = (fetch) => fetch.mock.calls.map(([, o]) => /(?:query|mutation)\s+(\w+)/.exec(JSON.parse(o.body).query)[1]);
const pedidasDelMes = (llamadas) => llamadas('FechasDisponibles').filter((v) => v.desde === MES.desde && v.hasta === MES.hasta);

/** Recorre el asistente: cliente (ya elegido o escrito) → fecha → datos → deja la pantalla en "Revisar" (o en "Datos" con paraRevisar: false). */
async function llenarEvento({ nombre = 'Prueba Quince años de Ana', adultos = '60', paraRevisar = true } = {}) {
  siguiente(); // Cliente → Fecha
  fireEvent.click(await screen.findByRole('button', { name: DIA_SABADO }));
  siguiente(); // Fecha → Datos
  cambiar('Nombre del evento', nombre);
  cambiar('Tipo de evento', 'QUINCE_ANOS');
  cambiar('Adultos', adultos);
  cambiar('Niños', '10');
  cambiar('Zona', 'NEIVA');
  if (paraRevisar) siguiente(); // Datos → Revisar
}

async function abrirConClienteNuevo(respuestas) {
  iniciarSesionDePrueba();
  const simulado = simularPorOperacion(base(respuestas));
  renderizarApp('/eventos/nuevo');
  fireEvent.click(screen.getByLabelText('Cliente nuevo'));
  cambiar('Nombre completo', 'Prueba Ana María Rojas');
  cambiar('Teléfono', '3157654321');
  cambiar('Correo', 'ana.rojas@ejemplo.com');
  return simulado;
}

beforeEach(() => fijarHoy(vi));
afterEach(() => vi.useRealTimers());

describe('Nuevo evento', () => {
  it('la fecha se elige en el calendario (ya no hay Desde/Hasta ni campo para escribirla)', async () => {
    iniciarSesionDePrueba();
    simularPorOperacion(base({}));
    renderizarApp('/eventos/nuevo');
    await screen.findByRole('option', { name: /Lucía Fernández Ospina/ });
    cambiar('Cliente', LUCIA.id);
    siguiente();

    fireEvent.click(await screen.findByRole('button', { name: DIA_SABADO }));

    expect(screen.getByText(/Fecha seleccionada:/).textContent).toBe('Fecha seleccionada: sábado, 7 de noviembre de 2026');
    expect(screen.queryByLabelText('Desde')).toBeNull();
    expect(screen.queryByLabelText('Hasta')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Ver fechas' })).toBeNull();
    expect(document.querySelector('input[type="date"]')).toBeNull();
    // un martes no se puede elegir: el caso "martes" queda en el backend (eventos-service, Postman y scripts)
    expect(screen.getByRole('button', { name: 'martes, 10 de noviembre de 2026, no laborable' }).disabled).toBe(true);
  });

  it('validación por paso: nombre, cantidades, sin invitados y capacidad del lugar impiden avanzar (sin peticiones)', async () => {
    const { fetch, llamadas } = await abrirConClienteNuevo({});
    await llenarEvento({ nombre: ' ab ', adultos: '2.5', paraRevisar: false });
    cambiar('Niños', '-1');
    const antes = fetch.mock.calls.length;
    siguiente();

    expect(await screen.findByText('Debe tener entre 3 y 150 caracteres.')).toBeTruthy();
    expect(screen.getByText('Debe ser un número entero.')).toBeTruthy();
    expect(screen.getByText('No puede ser negativa.')).toBeTruthy();
    for (const campo of ['Nombre del evento', 'Adultos', 'Niños']) {
      expect(screen.getByLabelText(campo).getAttribute('aria-invalid')).toBe('true');
    }
    expect(screen.queryByRole('button', { name: 'Crear evento' })).toBeNull(); // sigue en el paso Datos

    cambiar('Nombre del evento', 'Prueba Quince años de Ana');
    cambiar('Adultos', '0');
    cambiar('Niños', '0');
    siguiente();
    expect(await screen.findByText('El evento debe tener al menos un invitado.')).toBeTruthy();

    cambiar('Adultos', '5001');
    siguiente();
    expect(await screen.findByText('No puede ser mayor que 5.000.')).toBeTruthy();

    cambiar('Adultos', '75');
    cambiar('Niños', '10');
    cambiar('Lugar (opcional)', OCOBOS.id);
    siguiente();
    expect(await screen.findByText('Salón Los Ocobos tiene capacidad para 80 personas y el evento tiene 85 invitados.')).toBeTruthy();
    expect(screen.getByLabelText('Lugar (opcional)').getAttribute('aria-invalid')).toBe('true');

    expect(fetch.mock.calls.length).toBe(antes); // ni la comprobación de la fecha, ni el cliente, ni el evento
    expect(llamadas('CrearCliente')).toHaveLength(0);
    expect(llamadas('CrearEvento')).toHaveLength(0);
  });

  it('la fecha deja de estar libre antes de enviar: aviso, sin crear el cliente, selección borrada y el mes recargado', async () => {
    let tomada = false;
    const { llamadas } = await abrirConClienteNuevo({
      FechasDisponibles: (v) => respuestaFechas({ ocupadas: tomada ? ['2026-11-06', SABADO, '2026-11-08'] : [] })(v),
    });
    await llenarEvento();
    tomada = true; // otra pestaña registró un evento ese fin de semana
    crear();

    const alerta = await screen.findByRole('alert');
    expect(alerta.textContent).toMatch(/Esa fecha ya no está libre/);
    expect(screen.getByText(/Esa fecha ya no está libre/, { selector: '#error-fecha' })).toBeTruthy();
    expect(llamadas('FechasDisponibles').some((v) => v.desde === SABADO && v.hasta === SABADO)).toBe(true);
    expect(llamadas('CrearCliente')).toHaveLength(0);
    expect(llamadas('CrearEvento')).toHaveLength(0);
    expect(screen.getByText('Aún no has elegido una fecha.')).toBeTruthy();
    expect(await screen.findByRole('button', { name: 'sábado, 7 de noviembre de 2026, no disponible' })).toBeTruthy();
    expect(pedidasDelMes(llamadas)).toHaveLength(2); // la carga inicial y la recarga
    expect(screen.getByLabelText('Nombre completo').value).toBe('Prueba Ana María Rojas'); // el formulario se conserva
  });

  it('errores del servicio junto a cada campo y el 409 con su mensaje; tras el 409 se recarga el calendario', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion(base({
      CrearEvento: (v, n) => (n === 1
        ? { data: null, errors: [{ message: 'Los datos enviados no son válidos. Revisa los campos indicados.', path: ['crearEvento'],
          extensions: { classification: 'BAD_REQUEST', status: 400, servicio: 'eventos', errores: [
            { campo: 'nombreEvento', mensaje: 'el tamaño debe estar entre 3 y 150' }] } }] }
        : { data: null, errors: [{ message: 'Ya hay un evento el fin de semana del 2026-11-07. Elige otra fecha.', path: ['crearEvento'],
          extensions: { classification: 'CONFLICT', status: 409, servicio: 'eventos' } }] }),
    }));
    renderizarApp('/eventos/nuevo');

    await screen.findByRole('option', { name: /Lucía Fernández Ospina/ });
    cambiar('Cliente', LUCIA.id);
    await llenarEvento();
    crear();

    expect(await screen.findByText('el tamaño debe estar entre 3 y 150')).toBeTruthy();
    expect(screen.getByLabelText('Nombre del evento').getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByLabelText('Zona').getAttribute('aria-invalid')).toBe('false');
    expect(llamadas('CrearEvento')[0].input).toMatchObject({ clienteId: LUCIA.id, fecha: SABADO, cantidadAdultos: 60, cantidadNinos: 10, lugarId: null });

    // el asistente volvió al paso Datos (donde está el error): se corrige y se vuelve a Revisar
    siguiente();
    crear();
    const alerta = await screen.findByText(/Ya hay un evento el fin de semana/);
    expect(alerta.closest('[role="alert"]').textContent).toMatch(/No se puede realizar esta acción en el estado actual/);
    expect(screen.queryByText('el tamaño debe estar entre 3 y 150')).toBeNull();
    await screen.findByRole('button', { name: /sábado, 7 de noviembre de 2026/ });
    expect(pedidasDelMes(llamadas)).toHaveLength(2); // carga inicial + recarga tras el 409
  });

  it('sin elegir lo obligatorio no se avanza ni se envía nada, y se indica junto al campo en cada paso', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion(base({}));
    renderizarApp('/eventos/nuevo');
    await screen.findByRole('option', { name: /Lucía Fernández Ospina/ });

    siguiente(); // paso Cliente sin elegir
    expect(screen.getByText('Elige un cliente o crea uno nuevo.')).toBeTruthy();

    cambiar('Cliente', LUCIA.id);
    siguiente(); // pasa a Fecha
    siguiente(); // paso Fecha sin elegir
    expect(screen.getByText('Elige una fecha en el calendario.')).toBeTruthy();
    expect(screen.getByRole('group', { name: 'Noviembre de 2026' }).getAttribute('aria-describedby')).toBe('ayuda-fecha error-fecha');

    fireEvent.click(await screen.findByRole('button', { name: DIA_SABADO }));
    siguiente(); // pasa a Datos
    siguiente(); // paso Datos vacío
    expect(screen.getByText('Escribe el nombre del evento.')).toBeTruthy();
    expect(screen.getByText('Indica la cantidad de adultos.')).toBeTruthy();
    expect(llamadas('CrearEvento')).toHaveLength(0);
  });

  it('el asistente avanza y retrocede conservando lo escrito, y la revisión resume los datos', async () => {
    iniciarSesionDePrueba();
    simularPorOperacion(base({}));
    renderizarApp('/eventos/nuevo');
    await screen.findByRole('option', { name: /Lucía Fernández Ospina/ });
    cambiar('Cliente', LUCIA.id);
    await llenarEvento();

    expect(screen.getByRole('button', { name: 'Crear evento' })).toBeTruthy();
    expect(screen.getByText('Sábado, 7 de noviembre de 2026')).toBeTruthy();
    expect(screen.getByText('70 (60 adultos, 10 niños)')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    expect(screen.getByLabelText('Nombre del evento').value).toBe('Prueba Quince años de Ana');
    expect(screen.queryByRole('button', { name: 'Crear evento' })).toBeNull();
  });

  it('con un cliente nuevo: comprueba la fecha, crea el cliente, luego el evento con su id, y abre la ficha', async () => {
    const nuevo = { id: 'c-nuevo', nombreCompleto: 'Prueba Ana María Rojas' };
    const { fetch, llamadas } = await abrirConClienteNuevo({
      CrearCliente: { data: { crearCliente: nuevo } },
      CrearEvento: { data: { crearEvento: { id: 'ev-nuevo', nombreEvento: 'Prueba Quince años de Ana', estado: 'PENDIENTE' } } },
      FichaEvento: { data: { evento: { ...fichaDemo(), id: 'ev-nuevo', nombreEvento: 'Prueba Quince años de Ana', estado: 'PENDIENTE',
        cotizacion: null, balance: null, contrato: null, logistica: null } } },
    });
    await llenarEvento();
    crear();

    expect(await screen.findByRole('heading', { level: 1, name: 'Prueba Quince años de Ana' })).toBeTruthy();
    expect(screen.getByText(/creado \(PENDIENTE\)/)).toBeTruthy();
    expect(llamadas('CrearCliente')[0].input).toEqual({ nombreCompleto: 'Prueba Ana María Rojas', telefono: '3157654321', correo: 'ana.rojas@ejemplo.com', direccion: null });
    expect(llamadas('CrearEvento')[0].input).toMatchObject({ clienteId: 'c-nuevo', fecha: SABADO });
    // orden: comprobación de ESA fecha → crearCliente → crearEvento (seguidas)
    const orden = operaciones(fetch);
    const i = orden.indexOf('CrearCliente');
    expect(orden.slice(i - 1, i + 2)).toEqual(['FechasDisponibles', 'CrearCliente', 'CrearEvento']);
    expect(JSON.parse(fetch.mock.calls[i - 1][1].body).variables).toEqual({ desde: SABADO, hasta: SABADO });
    expect(screen.getByRole('button', { name: 'Generar cotización' })).toBeTruthy();
  });
});
