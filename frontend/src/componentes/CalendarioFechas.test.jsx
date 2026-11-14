import { fireEvent, render, screen, within } from '@testing-library/react';
import { StrictMode, useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fijarHoy, HOY, respuestaFechas } from '../test/fechasSimuladas.js';
import { iniciarSesionDePrueba, simularPorOperacion } from '../test/utilidades.jsx';
import CalendarioFechas from './CalendarioFechas.jsx';

/** Envoltorio con estado, como lo usa Nuevo evento (y en StrictMode, para comprobar que no se duplican las peticiones). */
function Calendario({ alElegir = () => {}, version = 0 }) {
  const [valor, setValor] = useState('');
  return (
    <StrictMode>
      <CalendarioFechas valor={valor} hoy={HOY} version={version} alElegir={(f) => { setValor(f); alElegir(f); }} />
    </StrictMode>
  );
}
const dia = (nombre) => screen.getByRole('button', { name: nombre });
const titulo = () => screen.getByRole('heading', { level: 3 }).textContent;
const mesesPedidos = (llamadas) => llamadas('FechasDisponibles').map((v) => `${v.desde}..${v.hasta}`);

beforeEach(() => { fijarHoy(vi); iniciarSesionDePrueba(); });
afterEach(() => { vi.useRealTimers(); });

describe('Calendario de fechas libres', () => {
  it('solo los días libres se pueden elegir; los demás quedan deshabilitados con su estado en el aria-label', async () => {
    simularPorOperacion({ FechasDisponibles: respuestaFechas() });
    const alElegir = vi.fn();
    render(<Calendario alElegir={alElegir} />);

    expect(titulo()).toBe('Noviembre de 2026');
    const viernes = await screen.findByRole('button', { name: 'viernes, 6 de noviembre de 2026, disponible' });
    expect(viernes.disabled).toBe(false);
    // semana desde el lunes; el 1 de noviembre de 2026 es domingo → 6 huecos antes
    expect(screen.getByText('Lun').title).toBe('lunes');
    expect(document.querySelectorAll('.dia-hueco')).toHaveLength(6);

    for (const nombre of [
      'martes, 3 de noviembre de 2026, no disponible', // hoy
      'domingo, 1 de noviembre de 2026, no disponible', // pasado
      'martes, 10 de noviembre de 2026, no laborable',
      'jueves, 12 de noviembre de 2026, no laborable',
    ]) {
      expect(dia(nombre).disabled).toBe(true);
      fireEvent.click(dia(nombre));
    }
    expect(alElegir).not.toHaveBeenCalled();
    expect(screen.getByText('12 fechas libres este mes.')).toBeTruthy();

    // fin de semana con evento ("Quince años de Lucía", 5 de diciembre): no disponible
    fireEvent.click(screen.getByRole('button', { name: 'Mes siguiente' }));
    expect(titulo()).toBe('Diciembre de 2026');
    for (const d of ['viernes, 4', 'sábado, 5', 'domingo, 6']) {
      expect((await screen.findByRole('button', { name: `${d} de diciembre de 2026, no disponible` })).disabled).toBe(true);
    }
    expect(dia('sábado, 12 de diciembre de 2026, disponible').disabled).toBe(false);
    const leyenda = within(screen.getByRole('list', { name: 'Leyenda del calendario' }));
    for (const t of ['Disponible', 'No disponible', 'No laborable']) expect(leyenda.getByText(t)).toBeTruthy();
  });

  it('seleccionar un día libre fija la fecha, lo resalta y la muestra en texto', async () => {
    simularPorOperacion({ FechasDisponibles: respuestaFechas() });
    const alElegir = vi.fn();
    render(<Calendario alElegir={alElegir} />);
    expect(screen.getByText('Aún no has elegido una fecha.')).toBeTruthy();

    fireEvent.click(await screen.findByRole('button', { name: 'sábado, 7 de noviembre de 2026, disponible' }));

    expect(alElegir).toHaveBeenCalledWith('2026-11-07');
    const elegido = dia('sábado, 7 de noviembre de 2026, seleccionada');
    expect(elegido.getAttribute('aria-pressed')).toBe('true');
    expect(elegido.className).toMatch(/dia-elegido/);
    expect(screen.getByText(/Fecha seleccionada:/).textContent).toBe('Fecha seleccionada: sábado, 7 de noviembre de 2026');
    expect(dia('viernes, 6 de noviembre de 2026, disponible').getAttribute('aria-pressed')).toBe('false');
  });

  it('cada mes se pide una sola vez (con el primer y el último día) y no se pasa de los límites', async () => {
    const { llamadas } = simularPorOperacion({ FechasDisponibles: respuestaFechas() });
    render(<Calendario />);
    await screen.findByText('12 fechas libres este mes.');
    const anterior = screen.getByRole('button', { name: 'Mes anterior' });
    const siguiente = screen.getByRole('button', { name: 'Mes siguiente' });
    expect(anterior.disabled).toBe(true); // no antes del mes actual

    fireEvent.click(siguiente); // diciembre
    await screen.findByRole('button', { name: 'sábado, 12 de diciembre de 2026, disponible' });
    fireEvent.click(siguiente); // enero
    await screen.findByRole('button', { name: 'sábado, 2 de enero de 2027, disponible' });
    fireEvent.click(anterior); // diciembre (ya cargado)
    fireEvent.click(anterior); // noviembre (ya cargado)
    fireEvent.click(siguiente); // diciembre otra vez
    expect(titulo()).toBe('Diciembre de 2026');
    expect(dia('sábado, 12 de diciembre de 2026, disponible')).toBeTruthy(); // sin esperar: sale de la memoria
    expect(mesesPedidos(llamadas)).toEqual(['2026-11-01..2026-11-30', '2026-12-01..2026-12-31', '2027-01-01..2027-01-31']);

    // hasta el mes de hoy + 366 días (4 de noviembre de 2027) y no más
    for (let i = 0; i < 11; i += 1) fireEvent.click(siguiente);
    expect(titulo()).toBe('Noviembre de 2027');
    expect(siguiente.disabled).toBe(true);
    expect((await screen.findByRole('button', { name: 'jueves, 4 de noviembre de 2027, no laborable' })).disabled).toBe(true);
    // el 5 es viernes y Eventos lo devolvería, pero está fuera del rango de 366 días
    expect(dia('viernes, 5 de noviembre de 2027, no disponible').disabled).toBe(true);
    expect(new Set(mesesPedidos(llamadas)).size).toBe(mesesPedidos(llamadas).length); // ningún mes repetido
  });

  it('si la consulta falla: aviso con "Reintentar", y al reintentar se pide otra vez y quedan los días libres', async () => {
    const { llamadas } = simularPorOperacion({
      FechasDisponibles: (v, n) => (n === 1
        ? { data: null, errors: [{ message: 'El servicio de eventos no está disponible en este momento. Intenta de nuevo más tarde.', path: ['fechasDisponibles'], extensions: { classification: 'SERVICE_UNAVAILABLE', status: 503, servicio: 'eventos' } }] }
        : respuestaFechas()(v)),
    });
    render(<Calendario />);

    const alerta = await screen.findByRole('alert');
    // Fase 11C: técnico → genérico con la referencia; el mensaje del 503 no se muestra
    expect(alerta.textContent).toMatch(/No se pudieron cargar las fechas de este mes\. No se pudo completar la operación\. Intenta de nuevo en unos minutos\. Ref: [0-9A-F]{8}/);
    expect(alerta.textContent).not.toMatch(/servicio de eventos/);
    expect(dia('sábado, 7 de noviembre de 2026, no disponible').disabled).toBe(true); // sin datos, nada se puede elegir

    fireEvent.click(within(alerta).getByRole('button', { name: 'Reintentar' }));

    expect((await screen.findByRole('button', { name: 'sábado, 7 de noviembre de 2026, disponible' })).disabled).toBe(false);
    expect(screen.queryByRole('alert')).toBeNull();
    expect(llamadas('FechasDisponibles')).toHaveLength(2);
  });

  it('al subir "version" descarta lo cargado y vuelve a pedir el mes visible (una vez)', async () => {
    let ocupadas = [];
    const { llamadas } = simularPorOperacion({ FechasDisponibles: (v) => respuestaFechas({ ocupadas })(v) });
    const { rerender } = render(<Calendario version={0} />);
    await screen.findByRole('button', { name: 'sábado, 7 de noviembre de 2026, disponible' });

    ocupadas = ['2026-11-06', '2026-11-07', '2026-11-08']; // otra pestaña tomó ese fin de semana
    rerender(<Calendario version={1} />);

    expect(await screen.findByRole('button', { name: 'sábado, 7 de noviembre de 2026, no disponible' })).toBeTruthy();
    expect(mesesPedidos(llamadas)).toEqual(['2026-11-01..2026-11-30', '2026-11-01..2026-11-30']);
  });
});
