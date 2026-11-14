import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fichaDemo } from '../test/fichaDemo.js';
import { diferida, iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';
import { EVENTO_NOTIFICACIONES } from '../utils/avisos.js';
import { formatearRecibida } from '../utils/formato.js';
import { textoContador } from './Campana.jsx';
import { INTERVALO_MS } from './useSinLeer.js';

/**
 * Fase 11H: la campanita. Se prueba sobre la aplicación real (Layout) en /lugares, una página con una sola consulta propia.
 * El número sale de NotificacionesSinLeer (polling) y la lista de MisNotificaciones (solo al abrir el panel).
 */
const LUGARES = { data: { lugares: { pagina: 0, totalElementos: 0, totalPaginas: 0, contenido: [] } } };
const sinLeer = (n) => ({ data: { notificacionesSinLeer: n } });
const hace = (minutos) => new Date(Date.now() - minutos * 60_000).toISOString();
const notificacion = (id, asunto, { leida = false, minutos = 5, eventoId = 'af79495b-9065-4b83-b08e-83ca7edabc5a', nombre = 'Quince años de Lucía' } = {}) => ({
  id, tipo: 'EVENTO_PROXIMO', asunto, mensaje: `Hola. ${asunto}.`, fechaCreacion: hace(minutos), leida, eventoId,
  evento: { id: eventoId, nombreEvento: nombre },
});
const lista = (n, contenido) => ({ data: { notificacionesSinLeer: n, misNotificaciones: { contenido } } });

const campana = () => screen.getByRole('button', { name: /^Notificaciones, \d+ sin leer$/ });
const contador = () => campana().querySelector('.campana-contador');
const panel = () => screen.getByRole('dialog', { name: 'Notificaciones' });

function abrir(respuestas, { rol = 'ADMINISTRADOR', ruta = '/lugares' } = {}) {
  iniciarSesionDePrueba({ rol });
  const simulado = simularPorOperacion({ Lugares: LUGARES, ...respuestas });
  renderizarApp(ruta);
  return simulado;
}

afterEach(() => {
  vi.useRealTimers();
  delete document.visibilityState; // vuelve al getter de jsdom ("visible")
});

describe('Campanita de notificaciones (Fase 11H)', () => {
  it('con 0 sin leer no muestra número; el aria-label lo dice igual', async () => {
    abrir({ NotificacionesSinLeer: sinLeer(0) });
    expect(await screen.findByRole('button', { name: 'Notificaciones, 0 sin leer' })).toBeTruthy();
    expect(contador()).toBeNull();
  });

  it('muestra el número de no leídas y "9+" con más de 9', async () => {
    abrir({ NotificacionesSinLeer: (v, n) => sinLeer(n === 1 ? 3 : 12) });
    await screen.findByRole('button', { name: 'Notificaciones, 3 sin leer' });
    expect(contador().textContent).toBe('3');
    expect([textoContador(0), textoContador(1), textoContador(9), textoContador(10), textoContador(250)]).toEqual(['', '1', '9', '9+', '9+']);
  });

  it('la ve también la asistente', async () => {
    abrir({ NotificacionesSinLeer: sinLeer(2) }, { rol: 'ASISTENTE' });
    expect(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' })).toBeTruthy();
  });

  it('el panel muestra las últimas con mensaje, evento y hora de recepción; las nuevas dicen "Nueva"', async () => {
    const nueva = notificacion('n1', 'Falta 1 día para su evento: Boda de Gabriela y Mateo', { minutos: 5, nombre: 'Boda de Gabriela y Mateo' });
    const vieja = notificacion('n2', 'Abono registrado', { leida: true, minutos: 3 * 24 * 60 });
    const { llamadas } = abrir({ NotificacionesSinLeer: sinLeer(1), MisNotificaciones: lista(1, [nueva, vieja]) });
    fireEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }));

    const p = panel();
    expect(await within(p).findByText('Falta 1 día para su evento: Boda de Gabriela y Mateo')).toBeTruthy();
    expect(llamadas('MisNotificaciones')).toEqual([{ size: 10 }]);
    const [primera, segunda] = within(p).getAllByRole('listitem');
    expect(within(primera).getByText('Nueva')).toBeTruthy();
    expect(within(primera).getByText('Boda de Gabriela y Mateo')).toBeTruthy();
    expect(within(primera).getByText(/Hola\. Falta 1 día/)).toBeTruthy();
    expect(primera.querySelector('time').textContent).toBe(`${formatearRecibida(nueva.fechaCreacion)} · hace 5 min`);
    expect(primera.querySelector('time').textContent).toMatch(/^Hoy, \d{1,2}:\d{2}\s?[ap]\.\s?m\. · hace 5 min$/);
    expect(within(segunda).getByText('Leída')).toBeTruthy();
    expect(segunda.querySelector('time').textContent).toMatch(/^\d{1,2} de \w+ de \d{4}, /); // más antigua: la fecha completa
  });

  it('sin notificaciones dice "No tienes notificaciones."', async () => {
    abrir({ NotificacionesSinLeer: sinLeer(0), MisNotificaciones: lista(0, []) });
    fireEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 0 sin leer' }));
    expect(await within(panel()).findByText('No tienes notificaciones.')).toBeTruthy();
    expect(within(panel()).getByRole('button', { name: 'Marcar todas como leídas' }).disabled).toBe(true);
  });

  it('al pulsar una no leída la marca como leída, baja el número y abre la ficha del evento', async () => {
    const evento = fichaDemo();
    const { llamadas } = abrir({
      NotificacionesSinLeer: sinLeer(2),
      MisNotificaciones: lista(2, [notificacion('n1', 'Falta 1 día para su evento', { eventoId: evento.id }), notificacion('n2', 'Abono registrado')]),
      MarcarNotificacionLeida: { data: { marcarNotificacionLeida: { id: 'n1', leida: true } } },
      FichaEvento: { data: { evento } },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    fireEvent.click(await within(panel()).findByRole('button', { name: /Falta 1 día para su evento/ }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Quince años de Lucía' })).toBeTruthy();
    expect(llamadas('MarcarNotificacionLeida')).toEqual([{ id: 'n1' }]);
    expect(screen.queryByRole('dialog', { name: 'Notificaciones' })).toBeNull();
    expect(campana().getAttribute('aria-label')).toBe('Notificaciones, 1 sin leer');
  });

  it('una ya leída lleva a la ficha sin volver a marcarla', async () => {
    const evento = fichaDemo();
    const { llamadas } = abrir({
      NotificacionesSinLeer: sinLeer(0),
      MisNotificaciones: lista(0, [notificacion('n1', 'Abono registrado', { leida: true, eventoId: evento.id })]),
      FichaEvento: { data: { evento } },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 0 sin leer' }));
    fireEvent.click(await within(panel()).findByRole('button', { name: /Abono registrado/ }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Quince años de Lucía' })).toBeTruthy();
    expect(llamadas('MarcarNotificacionLeida')).toHaveLength(0);
  });

  it('"Marcar todas como leídas" llama a la mutation, deja el número en 0 y todas como "Leída"', async () => {
    const { llamadas } = abrir({
      NotificacionesSinLeer: sinLeer(2),
      MisNotificaciones: lista(2, [notificacion('n1', 'Primera'), notificacion('n2', 'Segunda')]),
      MarcarTodasNotificacionesLeidas: { data: { marcarTodasNotificacionesLeidas: 2 } },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    await within(panel()).findByText('Primera');
    fireEvent.click(within(panel()).getByRole('button', { name: 'Marcar todas como leídas' }));

    await waitFor(() => expect(campana().getAttribute('aria-label')).toBe('Notificaciones, 0 sin leer'));
    expect(llamadas('MarcarTodasNotificacionesLeidas')).toHaveLength(1);
    expect(within(panel()).getAllByText('Leída')).toHaveLength(2);
    expect(within(panel()).queryByText('Nueva')).toBeNull();
    expect(contador()).toBeNull();
  });

  it('teclado: es un botón (Enter / Espacio), el foco pasa al panel, Escape cierra y el foco vuelve a la campana', async () => {
    abrir({ NotificacionesSinLeer: sinLeer(1), MisNotificaciones: lista(1, [notificacion('n1', 'Primera')]) });
    const boton = await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' });
    expect(boton.tagName).toBe('BUTTON');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(boton);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(within(panel()).getByRole('heading', { name: 'Notificaciones' }));

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Notificaciones' })).toBeNull();
    expect(document.activeElement).toBe(boton);
    expect(boton.getAttribute('aria-expanded')).toBe('false');
  });

  it('polling: consulta cada 30 s sin recargar y el número sube de 0 a 1', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const { llamadas } = abrir({ NotificacionesSinLeer: (v, n) => sinLeer(n >= 2 ? 1 : 0) });
    await screen.findByRole('button', { name: 'Notificaciones, 0 sin leer' });
    expect(llamadas('NotificacionesSinLeer')).toHaveLength(1);

    await act(async () => { vi.advanceTimersByTime(INTERVALO_MS - 1); });
    expect(llamadas('NotificacionesSinLeer')).toHaveLength(1);
    await act(async () => { vi.advanceTimersByTime(1); });
    expect(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' })).toBeTruthy();
    expect(contador().textContent).toBe('1');
    expect(llamadas('NotificacionesSinLeer')).toHaveLength(2);
  });

  it('con la pestaña oculta se pausa; al volver consulta enseguida y sigue cada 30 s', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const { llamadas } = abrir({ NotificacionesSinLeer: sinLeer(0) });
    await screen.findByRole('button', { name: 'Notificaciones, 0 sin leer' });

    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
    act(() => { document.dispatchEvent(new Event('visibilitychange')); });
    await act(async () => { vi.advanceTimersByTime(3 * INTERVALO_MS); });
    expect(llamadas('NotificacionesSinLeer')).toHaveLength(1); // nada mientras está oculta

    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
    act(() => { document.dispatchEvent(new Event('visibilitychange')); });
    await waitFor(() => expect(llamadas('NotificacionesSinLeer')).toHaveLength(2)); // enseguida al volver
    await act(async () => { vi.advanceTimersByTime(INTERVALO_MS); });
    await waitFor(() => expect(llamadas('NotificacionesSinLeer')).toHaveLength(3));
  });

  it('al reabrir el panel no se ve la lista vieja mientras llega la nueva (no se puede pulsar algo desactualizado)', async () => {
    const segunda = diferida();
    abrir({
      NotificacionesSinLeer: sinLeer(1),
      MisNotificaciones: (v, n) => (n === 1 ? lista(1, [notificacion('n1', 'Primera')]) : segunda.promesa),
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }));
    await within(panel()).findByText('Primera');
    fireEvent.keyDown(document, { key: 'Escape' });

    fireEvent.click(campana());
    expect(within(panel()).getByText('Cargando notificaciones…')).toBeTruthy();
    expect(within(panel()).queryByText('Primera')).toBeNull();
    await act(async () => { segunda.resolver(lista(1, [notificacion('n1', 'Primera')])); });
    expect(await within(panel()).findByText('Primera')).toBeTruthy();
  });

  it('una consulta del número pedida ANTES de marcar no vuelve a poner el número viejo (regresión del recorrido en Docker)', async () => {
    const evento = fichaDemo();
    const vieja = diferida();
    abrir({
      // 1.ª consulta: 2; 2.ª (la que dispara el aviso, todavía en camino al marcar): responde 2 DESPUÉS del clic
      NotificacionesSinLeer: (v, n) => (n === 1 ? sinLeer(2) : n === 2 ? vieja.promesa : sinLeer(1)),
      MisNotificaciones: lista(2, [notificacion('n1', 'Falta 1 día para su evento', { eventoId: evento.id }), notificacion('n2', 'Otra')]),
      MarcarNotificacionLeida: { data: { marcarNotificacionLeida: { id: 'n1', leida: true } } },
      FichaEvento: { data: { evento } },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    await within(panel()).findByText('Falta 1 día para su evento');
    act(() => { window.dispatchEvent(new Event(EVENTO_NOTIFICACIONES)); }); // consulta en camino
    fireEvent.click(within(panel()).getByRole('button', { name: /Falta 1 día para su evento/ }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Quince años de Lucía' })).toBeTruthy();
    expect(campana().getAttribute('aria-label')).toBe('Notificaciones, 1 sin leer');

    await act(async () => { vieja.resolver(sinLeer(2)); }); // llega la respuesta vieja
    expect(campana().getAttribute('aria-label')).toBe('Notificaciones, 1 sin leer');
  });

  it('el aviso interno de la ficha la actualiza sin esperar los 30 s', async () => {
    const { llamadas } = abrir({ NotificacionesSinLeer: (v, n) => sinLeer(n >= 2 ? 1 : 0) });
    await screen.findByRole('button', { name: 'Notificaciones, 0 sin leer' });
    act(() => { window.dispatchEvent(new Event(EVENTO_NOTIFICACIONES)); });
    expect(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' })).toBeTruthy();
    expect(llamadas('NotificacionesSinLeer').length).toBeGreaterThanOrEqual(2);
  });
});
