import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { diasHasta, resumenRecordatorios, textoDias } from '../componentes/ProximosEventos.jsx';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';
import { hoyIso } from '../utils/ayudas.js';

const pagina = (contenido) => ({
  data: { eventos: { pagina: 0, tamanoPagina: 20, totalElementos: contenido.length, totalPaginas: 1, contenido } },
});
const evento = (id, nombreEvento, estado = 'RESERVADO') => ({
  id, nombreEvento, fecha: '2026-12-05', tipoEvento: 'QUINCE_ANOS', numInvitados: 105, zona: 'NEIVA', estado,
  cliente: { id: `c-${id}`, nombreCompleto: 'Lucía Fernández Ospina' },
});
const proximos = (contenido) => ({ data: { eventos: { contenido } } });
const SIN_PROXIMOS = proximos([]);
const bloque = () => screen.findByRole('region', { name: 'Próximos 7 días' });

describe('Listado de eventos', () => {
  it('oculta por defecto los eventos "Prueba…" y los muestra con la casilla', async () => {
    iniciarSesionDePrueba();
    const datos = pagina([evento('1', 'Quince años de Lucía'), evento('2', 'Prueba Quince años de Lucía')]);
    const { llamadas } = simularPorOperacion({ Eventos: datos, ProximosEventos: SIN_PROXIMOS });
    renderizarApp('/eventos');

    expect(await screen.findByRole('link', { name: 'Quince años de Lucía' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Prueba Quince años de Lucía' })).toBeNull();
    expect(screen.getByText(/1 de prueba ocultos/)).toBeTruthy();

    fireEvent.click(screen.getByLabelText('Mostrar eventos de prueba'));
    expect(await screen.findByRole('link', { name: 'Prueba Quince años de Lucía' })).toBeTruthy();
    // el filtro es solo de la interfaz: la consulta al gateway no cambia. El enlace aparece al instante (filtro local) y la
    // segunda consulta (por el cambio de la URL) sale en un useEffect: se espera a que salga antes de leerla.
    await waitFor(() => expect(llamadas('Eventos')).toHaveLength(2));
    expect(llamadas('Eventos')[1]).toEqual(llamadas('Eventos')[0]);
  });

  it('envía los filtros de estado, tipo y fechas al gateway', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({ Eventos: pagina([]), ProximosEventos: SIN_PROXIMOS });
    renderizarApp('/eventos?estado=PENDIENTE&tipo=BODA&desde=2027-01-01&hasta=2027-01-31&pagina=2');

    expect(await screen.findByText('No hay eventos próximos con estos filtros.')).toBeTruthy();
    expect(llamadas('Eventos')).toEqual([{
      estado: 'PENDIENTE', tipo: 'BODA', desde: '2027-01-01', hasta: '2027-01-31', page: 2, size: 20, sort: 'fecha,asc',
    }]);
  });
});

describe('Bloque "Próximos 7 días" (Fase 11E)', () => {
  it('pide los RESERVADOS de hoy a hoy + 7 y los muestra con los días que faltan, sin los de prueba', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({
      Eventos: pagina([]),
      ProximosEventos: proximos([
        { id: 'p1', nombreEvento: 'Boda de Ana y Luis', fecha: hoyIso(1), tipoEvento: 'BODA' },
        { id: 'p2', nombreEvento: 'Quince años de Sofía', fecha: hoyIso(6), tipoEvento: 'QUINCE_ANOS' },
        { id: 'p3', nombreEvento: 'Prueba recordatorio', fecha: hoyIso(3), tipoEvento: 'BODA' },
      ]),
    });
    renderizarApp('/eventos');

    const region = await bloque();
    expect(await within(region).findByRole('link', { name: 'Boda de Ana y Luis' })).toBeTruthy();
    expect(within(region).getByRole('link', { name: 'Quince años de Sofía' }).getAttribute('href')).toBe('/eventos/p2');
    expect(within(region).getByText('Mañana')).toBeTruthy();
    expect(within(region).getByText('En 6 días')).toBeTruthy();
    expect(within(region).queryByText('Prueba recordatorio')).toBeNull();
    expect(llamadas('ProximosEventos')).toEqual([{ desde: hoyIso(), hasta: hoyIso(7) }]);
  });

  it('sin eventos próximos lo dice', async () => {
    iniciarSesionDePrueba();
    simularPorOperacion({ Eventos: pagina([]), ProximosEventos: SIN_PROXIMOS });
    renderizarApp('/eventos');

    expect(await within(await bloque()).findByText('No hay eventos reservados en los próximos 7 días.')).toBeTruthy();
  });

  it('administrador: "Ejecutar recordatorios" llama a la mutation, muestra el resultado y recarga el bloque', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({
      Eventos: pagina([]),
      ProximosEventos: proximos([{ id: 'p1', nombreEvento: 'Boda de Ana y Luis', fecha: hoyIso(7), tipoEvento: 'BODA' }]),
      EjecutarRecordatorios: { data: { ejecutarRecordatorios: { revisados: 2, enviados: 1, yaEnviados: 1, recordatorios: [] } } },
    });
    renderizarApp('/eventos');
    const region = await bloque();
    await within(region).findByRole('link', { name: 'Boda de Ana y Luis' });

    fireEvent.click(within(region).getByRole('button', { name: 'Ejecutar recordatorios' }));

    expect(await within(region).findByText('Recordatorios revisados: 1 recordatorio enviado; 1 ya se había enviado (no se repiten).'))
      .toBeTruthy();
    expect(llamadas('EjecutarRecordatorios')).toHaveLength(1);
    expect(llamadas('ProximosEventos')).toHaveLength(2);
  });

  it('administrador: un error del servicio se ve junto al botón', async () => {
    iniciarSesionDePrueba();
    simularPorOperacion({
      Eventos: pagina([]),
      ProximosEventos: SIN_PROXIMOS,
      EjecutarRecordatorios: {
        data: null,
        errors: [{ message: 'No tienes permiso para realizar esta operación.', path: ['ejecutarRecordatorios'],
          extensions: { classification: 'FORBIDDEN', status: 403, servicio: 'gateway' } }],
      },
    });
    renderizarApp('/eventos');
    const region = await bloque();

    fireEvent.click(await within(region).findByRole('button', { name: 'Ejecutar recordatorios' }));

    expect((await within(region).findByRole('alert')).textContent).toMatch(/No tienes permiso/);
  });

  it('asistente: ve el bloque pero no el botón "Ejecutar recordatorios"', async () => {
    iniciarSesionDePrueba({ rol: 'ASISTENTE', nombre: 'Carolina Asistente' });
    simularPorOperacion({
      Eventos: pagina([]),
      ProximosEventos: proximos([{ id: 'p1', nombreEvento: 'Boda de Ana y Luis', fecha: hoyIso(2), tipoEvento: 'BODA' }]),
    });
    renderizarApp('/eventos');
    const region = await bloque();

    expect(await within(region).findByText('En 2 días')).toBeTruthy();
    expect(within(region).queryByRole('button', { name: 'Ejecutar recordatorios' })).toBeNull();
  });

  it('textos: días, plural y singular', () => {
    expect(diasHasta('2026-11-12', '2026-11-05')).toBe(7);
    expect(diasHasta('2026-11-01', '2026-10-31')).toBe(1); // cambio de mes
    expect([textoDias(0), textoDias(1), textoDias(4)]).toEqual(['Hoy', 'Mañana', 'En 4 días']);
    expect(resumenRecordatorios({ revisados: 0, enviados: 0, yaEnviados: 0 }))
      .toBe('Recordatorios revisados: no hay eventos reservados en los próximos días.');
    expect(resumenRecordatorios({ revisados: 3, enviados: 3, yaEnviados: 0 }))
      .toBe('Recordatorios revisados: 3 recordatorios enviados; 0 ya se habían enviado (no se repiten).');
  });
});
