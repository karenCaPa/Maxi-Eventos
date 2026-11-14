import { render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import CalendarioFechas from '../componentes/CalendarioFechas.jsx';
import { fechaSimulada } from '../config.js';
import { respuestaFechas } from '../test/fechasSimuladas.js';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';
import { hoyIso } from '../utils/ayudas.js';
import { hoyEnZona } from '../utils/fechaPasada.js';

// Fase 11I: MODO DEMOSTRACIÓN. Con FRONTEND_FECHA_SIMULADA (en /config.js) la interfaz usa esa fecha como "hoy" para todo
// lo que calcula y lo avisa en la barra; sin ella, "hoy" es la fecha real (en las pruebas, la fijada en setup.js: 2026-11-03).

const conFecha = (valor) => { window.__CONFIG__ = { fechaSimulada: valor }; };
afterEach(() => { delete window.__CONFIG__; });

const pagina = (contenido) => ({
  data: { eventos: { pagina: 0, tamanoPagina: 20, totalElementos: contenido.length, totalPaginas: 1, contenido } },
});
const evento = (id, nombreEvento, fecha, estado) => ({
  id, nombreEvento, fecha, tipoEvento: 'QUINCE_ANOS', numInvitados: 105, zona: 'NEIVA', estado,
  cliente: { id: `c-${id}`, nombreCompleto: 'Cliente de la demostración' },
});
const aviso = () => document.querySelector('.modo-demo');

describe('fechaSimulada() y el "hoy" de la interfaz', () => {
  it('sin la variable (lo normal): null y "hoy" es la fecha real', () => {
    expect(fechaSimulada()).toBeNull();
    conFecha('');
    expect(fechaSimulada()).toBeNull(); // nginx la deja vacía cuando no está definida
    expect(hoyEnZona()).toBe('2026-11-03');
    expect(hoyIso(1)).toBe('2026-11-04');
  });

  it('con la variable: "hoy" es la fecha simulada (también para el calendario: hoyIso)', () => {
    conFecha('2026-12-10');
    expect(fechaSimulada()).toBe('2026-12-10');
    expect(hoyEnZona()).toBe('2026-12-10');
    expect(hoyEnZona(-1)).toBe('2026-12-09');
    expect(hoyEnZona(7)).toBe('2026-12-17');
    expect(hoyIso()).toBe('2026-12-10');
    expect(hoyIso(30)).toBe('2027-01-09'); // cambio de año
    // con un "ahora" explícito (las pruebas de la zona) se calcula con ese instante, no con la simulada
    expect(hoyEnZona(0, new Date('2026-11-11T04:59:00Z'), 'America/Bogota')).toBe('2026-11-10');
  });

  it('un valor que no es una fecha AAAA-MM-DD que exista se ignora: se usa la fecha real', () => {
    for (const malo of ['2026-02-30', '2026-13-01', '10/12/2026', '2026-12-1', ' 2026-12-10', 'mañana', 20261210, null]) {
      conFecha(malo);
      expect(fechaSimulada(), String(malo)).toBeNull();
      expect(hoyEnZona(), String(malo)).toBe('2026-11-03');
    }
    conFecha('2028-02-29'); // bisiesto: sí existe
    expect(fechaSimulada()).toBe('2028-02-29');
  });
});

describe('Aviso "Modo demostración" en la barra', () => {
  it('sin la variable no hay aviso', async () => {
    iniciarSesionDePrueba();
    simularPorOperacion({ Eventos: pagina([]), ProximosEventos: { data: { eventos: { contenido: [] } } } });
    renderizarApp('/eventos');

    await screen.findByText('No hay eventos próximos con estos filtros.');
    expect(aviso()).toBeNull();
    expect(screen.queryByText(/Modo demostración/)).toBeNull();
  });

  it('con 2026-12-10: el aviso en la barra y el ejemplo de la guía (Lucía Realizado, Sofía Vencido)', async () => {
    conFecha('2026-12-10');
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({
      ProximosEventos: { data: { eventos: { contenido: [] } } },
      Eventos: pagina([evento('a', 'Boda de Andrés y Camila', '2027-01-02', 'PENDIENTE')]),
      EventosPasados: pagina([
        evento('l', 'Quince años de Lucía', '2026-12-05', 'RESERVADO'),
        evento('s', 'Quince años de Sofía', '2026-11-28', 'PENDIENTE'),
      ]),
    });
    renderizarApp('/eventos');

    const pasados = (await screen.findByText('Eventos pasados (2)')).closest('details');
    // El aviso: en la barra (header), con texto, la fecha legible y la ISO, y que el servidor sigue con la real
    const barra = screen.getByRole('banner');
    expect(within(barra).getByRole('note').textContent)
      .toBe('Modo demostración: fecha simulada jueves, 10 de diciembre de 2026 (2026-12-10). El servidor sigue con la fecha real.');
    expect(aviso().querySelector('time').getAttribute('datetime')).toBe('2026-12-10');
    // Lucía (5-dic, RESERVADO) → Realizado; Sofía (28-nov, PENDIENTE) → Vencido; Andrés y Camila (2-ene) sigue Pendiente
    const fila = (nombre) => within(pasados).getByRole('link', { name: nombre }).closest('li');
    expect(within(fila('Quince años de Lucía')).getByRole('img', { name: /Estado: Realizado/ })).toBeTruthy();
    expect(within(fila('Quince años de Sofía')).getByRole('img', { name: /Estado: Vencido/ })).toBeTruthy();
    expect(within(screen.getByRole('link', { name: 'Boda de Andrés y Camila' }).closest('li')).getByText('Pendiente')).toBeTruthy();
    // Las consultas usan la fecha simulada: próximos desde el 10, pasados hasta el 9, "Próximos 7 días" del 10 al 17
    expect(llamadas('Eventos')[0]).toMatchObject({ desde: '2026-12-10' });
    expect(llamadas('EventosPasados')[0]).toMatchObject({ hasta: '2026-12-09' });
    expect(llamadas('ProximosEventos')).toEqual([{ desde: '2026-12-10', hasta: '2026-12-17' }]);
  });

  it('el calendario de Nuevo evento empieza en el mes de la fecha simulada', async () => {
    conFecha('2026-12-10');
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({ FechasDisponibles: respuestaFechas({ hoy: '2026-12-10' }) });
    render(<CalendarioFechas valor="" alElegir={() => {}} />);

    expect(screen.getByRole('heading', { level: 3 }).textContent).toBe('Diciembre de 2026');
    expect(await screen.findByRole('button', { name: 'viernes, 11 de diciembre de 2026, disponible' })).toBeTruthy();
    expect(llamadas('FechasDisponibles')[0].desde).toBe('2026-12-01');
  });
});
