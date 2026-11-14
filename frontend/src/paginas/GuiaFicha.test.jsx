import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { fichaDemo } from '../test/fichaDemo.js';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';

const sinCotizacion = () => ({ ...fichaDemo(), id: 'ev-1', estado: 'PENDIENTE', cotizacion: null, balance: null, contrato: null, logistica: null, pagos: { contenido: [] } });
const firmadoSinAbono = () => ({
  ...fichaDemo(), id: 'ev-2', estado: 'PENDIENTE', logistica: null,
  balance: { ...fichaDemo().balance, montoAbonado: 0, saldoPendiente: 8880000 }, pagos: { contenido: [] },
});
const abrir = (evento, ruta = '') => {
  iniciarSesionDePrueba();
  simularPorOperacion({ FichaEvento: { data: { evento } } });
  renderizarApp(`/eventos/${evento.id}${ruta}`);
};
const pasos = () => screen.findByRole('navigation', { name: 'Pasos del evento' });

describe('Guía paso a paso de la ficha', () => {
  it('abre en el paso que toca: sin cotización → Cotización, con su botón y sin las demás secciones', async () => {
    abrir(sinCotizacion());
    const nav = await pasos();
    expect(within(nav).getByRole('button', { name: /Cotización/ }).getAttribute('aria-current')).toBe('step');
    expect(screen.getByRole('heading', { level: 2, name: 'Genera la cotización' })).toBeTruthy();
    expect(screen.getByText('Paso 2 de 7')).toBeTruthy(); // Fase 11F: paso 7 "Resumen"
    expect(screen.getByRole('button', { name: 'Generar cotización' })).toBeTruthy();
    expect(screen.queryByRole('heading', { level: 2, name: 'Pagos' })).toBeNull();
    expect(screen.queryByRole('heading', { level: 2, name: 'Auditoría' })).toBeNull();
  });

  it('con el contrato firmado y sin abono toca Abonos; Confirmar marca lo que falta, ofrece el botón y muestra el 409 del servicio', async () => {
    iniciarSesionDePrueba();
    const evento = firmadoSinAbono();
    const { llamadas } = simularPorOperacion({
      FichaEvento: { data: { evento } },
      ConfirmarEvento: {
        data: null,
        errors: [{ message: 'El abono registrado no cubre el depósito de la reserva.', path: ['confirmarEvento'],
          extensions: { classification: 'CONFLICT', status: 409, servicio: 'eventos' } }],
      },
    });
    renderizarApp(`/eventos/${evento.id}`);
    const nav = await pasos();
    expect(within(nav).getByRole('button', { name: /Abonos/ }).getAttribute('aria-current')).toBe('step');
    expect(screen.getByRole('button', { name: 'Registrar pago' })).toBeTruthy();

    fireEvent.click(within(nav).getByRole('button', { name: /Confirmar/ }));
    expect(screen.getByRole('heading', { level: 2, name: 'Confirma el evento' })).toBeTruthy();
    expect(screen.getByText('Depósito abonado').parentElement.textContent).toMatch(/\(pendiente\)/);
    expect(screen.getByText(/Aún falta algo de la lista/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar evento' }));
    expect((await screen.findByRole('alert')).textContent).toMatch(/no cubre el depósito/);
    expect(llamadas('ConfirmarEvento')).toEqual([{ id: evento.id }]);
    expect(llamadas('FichaEvento')).toHaveLength(1); // ante el 409 no se recarga

    fireEvent.click(screen.getByRole('button', { name: 'Ir a abonos' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Registra los abonos' })).toBeTruthy();
  });

  it('con todo listo, Confirmar ofrece el botón sin el aviso de lo que falta', async () => {
    abrir({ ...fichaDemo(), id: 'ev-3', estado: 'PENDIENTE' }, '?paso=confirmar');
    await pasos();
    expect(screen.getByRole('button', { name: 'Confirmar evento' })).toBeTruthy();
    expect(screen.queryByText(/Aún falta algo de la lista/)).toBeNull();
  });

  it('Anterior / Siguiente recorren los pasos y "Ver todo junto" muestra todas las secciones', async () => {
    abrir(sinCotizacion());
    await pasos();
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente: Contrato' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Crea y firma el contrato' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Anterior: Cotización' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Genera la cotización' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Ver todo junto' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Auditoría' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Volver a la guía' }));
    expect(screen.queryByRole('heading', { level: 2, name: 'Auditoría' })).toBeNull();
  });

  it('un evento reservado abre en Preparativos y el historial queda en su propia vista', async () => {
    abrir({ ...fichaDemo(), id: 'ev-4' });
    const nav = await pasos();
    expect(within(nav).getByRole('button', { name: /Preparativos/ }).getAttribute('aria-current')).toBe('step');
    expect(screen.getByRole('heading', { level: 2, name: 'Proveedores asignados' })).toBeTruthy();
    expect(screen.queryByRole('heading', { level: 2, name: 'Notificaciones' })).toBeNull();
    // Fase 11F: después de Preparativos viene el Resumen, y desde ahí el historial
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente: Resumen' }));
    expect(screen.getByText('Paso 7 de 7')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Ver historial' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Notificaciones' })).toBeTruthy();
  });
});

describe('Aviso de recordatorio enviado (Fase 11E)', () => {
  const recordatorio = (dias, fechaCreacion) => ({
    id: `r${dias}`, tipo: 'EVENTO_PROXIMO', destinatario: 'cliente:e146', estado: 'GENERADA', fechaCreacion,
    asunto: `${dias === 1 ? 'Falta 1 día' : `Faltan ${dias} días`} para su evento: Quince años de Lucía`, mensaje: 'Hola.',
  });
  const conRecordatorios = (estado, ...lista) => {
    const base = fichaDemo();
    return { ...base, id: 'ev-r', estado, notificaciones: { totalElementos: 1 + lista.length, contenido: [...base.notificaciones.contenido, ...lista] } };
  };
  // El aviso fijo (el paso Resumen también menciona el recordatorio, en su tarjeta Evento)
  const aviso = () => screen.queryAllByText(/^Recordatorio enviado/).find((el) => el.classList.contains('aviso-titulo')) ?? null;

  it('se ve en todos los pasos de la guía y muestra el recordatorio más reciente', async () => {
    abrir(conRecordatorios('RESERVADO', recordatorio(7, '2026-11-28T12:00:00Z'), recordatorio(1, '2026-12-04T12:00:00Z')));
    const nav = await pasos();
    expect(aviso().textContent).toBe('Recordatorio enviado: falta 1 día');

    for (const paso of [/Cotización/, /Abonos/, /Confirmar/, /Preparativos/, /Resumen/]) {
      fireEvent.click(within(nav).getByRole('button', { name: paso }));
      expect(aviso().textContent).toBe('Recordatorio enviado: falta 1 día');
    }
    fireEvent.click(screen.getByRole('button', { name: 'Ver historial' }));
    expect(aviso()).toBeTruthy();
  });

  it('en plural, con el asunto que recibió el cliente', async () => {
    abrir(conRecordatorios('RESERVADO', recordatorio(7, '2026-11-28T12:00:00Z')));
    await pasos();
    expect(aviso().textContent).toBe('Recordatorio enviado: faltan 7 días');
    expect(screen.getByText(/Al cliente le llegó «Faltan 7 días para su evento: Quince años de Lucía»/)).toBeTruthy();
  });

  it('no aparece si el evento no tiene recordatorios', async () => {
    abrir(conRecordatorios('RESERVADO'));
    await pasos();
    expect(aviso()).toBeNull();
  });

  it('no aparece en un evento cancelado aunque tenga recordatorios', async () => {
    abrir(conRecordatorios('CANCELADO', recordatorio(7, '2026-11-28T12:00:00Z')));
    expect(await screen.findByRole('heading', { level: 1, name: 'Quince años de Lucía' })).toBeTruthy();
    expect(aviso()).toBeNull();
  });
});
