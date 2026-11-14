import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { fichaDemo } from '../test/fichaDemo.js';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';

/**
 * Fase 11F: paso 7 "Resumen" de la guía. Sale de la MISMA respuesta FichaEvento (sin peticiones nuevas): una línea
 * "Todo listo" / "Quedan N pendientes" con enlaces y una tarjeta por paso con su enlace.
 */

/** Evento completo: pagado, contrato firmado, proveedor confirmado y todas las compras conseguidas. */
const completo = () => {
  const e = fichaDemo();
  return {
    ...e, id: 'ev-ok',
    balance: { ...e.balance, montoAbonado: 8880000, saldoPendiente: 0 },
    logistica: { ...e.logistica, compras: { totalElementos: 2, contenido: e.logistica.compras.contenido.map((i) => ({ ...i, comprado: true })) } },
  };
};
/** A medias (como "Quince años de Sofía"): cotizado y con lugar, contrato sin firmar, sin abonos y sin confirmar. */
const aMedias = () => ({
  ...fichaDemo(), id: 'ev-medio', estado: 'PENDIENTE',
  contrato: { ...fichaDemo().contrato, estado: 'PENDIENTE', fechaFirma: null },
  pagos: { totalElementos: 0, contenido: [] },
  balance: { ...fichaDemo().balance, montoAbonado: 0, saldoPendiente: 8880000 },
  proveedores: { totalElementos: 0, contenido: [] }, serviciosAdicionales: [], logistica: null,
});

function abrir(evento, { rol = 'ADMINISTRADOR', errors } = {}) {
  iniciarSesionDePrueba({ rol });
  const simulado = simularPorOperacion({ FichaEvento: { data: { evento }, ...(errors ? { errors } : {}) } });
  renderizarApp(`/eventos/${evento.id}?paso=resumen`);
  return simulado;
}
const tarjeta = (titulo) => screen.getByRole('article', { name: titulo });
/** La línea de arriba del resumen (el camino también dice "Todo listo" / "N pendientes" en el paso 7). */
const estado = () => {
  const linea = document.querySelector('.resumen-estado-texto');
  if (!linea) throw new Error('No está la línea de estado del resumen');
  return linea;
};

describe('Paso 7 "Resumen" (Fase 11F)', () => {
  it('es el paso 7 de la guía y un evento completo dice "Todo listo", con las 6 tarjetas listas', async () => {
    const { llamadas, fetch } = abrir(completo());
    expect(await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' })).toBeTruthy();
    expect(screen.getByText('Paso 7 de 7')).toBeTruthy();
    const nav = screen.getByRole('navigation', { name: 'Pasos del evento' });
    const boton = within(nav).getByRole('button', { name: /Resumen/ });
    expect(boton.getAttribute('aria-current')).toBe('step');
    expect(boton.textContent).toContain('Todo listo');
    expect(boton.closest('li').className).toContain('hecho');

    expect(estado().textContent).toBe('Todo listo');
    expect(screen.queryByRole('list', { name: 'Pendientes' })).toBeNull();
    for (const t of ['Evento', 'Cotización', 'Contrato', 'Abonos', 'Confirmación', 'Preparativos']) {
      expect(within(tarjeta(t)).getByText('Listo')).toBeTruthy();
      expect(within(tarjeta(t)).queryByText(/Pendiente:/)).toBeNull();
    }
    expect(screen.queryByRole('table')).toBeNull(); // sin tablas
    // sin peticiones nuevas: solo la de la ficha
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(llamadas('FichaEvento')).toHaveLength(1);
  });

  it('cada tarjeta muestra los datos de su paso', async () => {
    abrir(completo());
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    const ev = tarjeta('Evento');
    expect(within(ev).getByText('Quince años de Lucía')).toBeTruthy();
    expect(within(ev).getByText('Quince años')).toBeTruthy();
    expect(within(ev).getByText('Sábado, 5 de diciembre de 2026')).toBeTruthy();
    expect(within(ev).getByText('105 (90 adultos, 15 niños)')).toBeTruthy();
    expect(within(ev).getByText('Neiva')).toBeTruthy();
    expect(within(ev).getByText(/Lucía Fernández Ospina · 3101234567 · lucia@ejemplo\.com · Neiva/)).toBeTruthy();
    expect(within(ev).getByText(/Salón de Eventos Los Ocobos · Neiva \(capacidad 150\)/)).toBeTruthy();

    const cot = tarjeta('Cotización');
    expect(within(cot).getByText('Valor base').nextSibling.textContent).toMatch(/7\.000\.000/);
    expect(within(cot).getByText('Total con adicionales').nextSibling.textContent).toMatch(/8\.880\.000/);

    const con = tarjeta('Contrato');
    expect(within(con).getByText('Firmado')).toBeTruthy();
    expect(within(con).getByText('Fecha de firma').nextSibling.textContent).toMatch(/2026/);

    const abo = tarjeta('Abonos');
    expect(within(abo).getByText('Depósito requerido').nextSibling.textContent).toMatch(/2\.610\.000/);
    expect(within(abo).getByText('Abonado').nextSibling.textContent).toMatch(/8\.880\.000/);
    expect(within(abo).getByText('Saldo pendiente').nextSibling.textContent).toMatch(/\$\s?0$/);
    expect(within(abo).getByText('2 pagos')).toBeTruthy();

    expect(within(tarjeta('Confirmación')).getByText('Reservado: la fecha está ocupada')).toBeTruthy();

    const pre = tarjeta('Preparativos');
    expect(within(pre).getByText('Sonido y Luces del Huila (Sonido): Confirmado')).toBeTruthy();
    expect(within(pre).getByText('Servicios adicionales').nextSibling.textContent).toMatch(/^1 servicio · \$\s?180\.000$/);
    expect(within(pre).getByText('7 meseros, 4 de cocina, 3 vehículos')).toBeTruthy();
    expect(within(pre).getByText('2 de 2 conseguidos')).toBeTruthy();
  });

  it('un evento a medias marca lo pendiente con texto e icono y lo enlaza', async () => {
    abrir(aMedias());
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    expect(estado().textContent).toBe('Quedan 4 pendientes');
    const lista = screen.getByRole('list', { name: 'Pendientes' });
    expect(within(lista).getAllByRole('link').map((a) => a.textContent)).toEqual([
      'Contrato: Contrato sin firmar',
      expect.stringMatching(/^Abonos: Depósito: faltan \$\s?2\.610\.000$/),
      'Confirmación: Por confirmar',
      'Preparativos: Sin proveedores asignados',
    ]);
    // en su tarjeta: "Pendiente:" en texto (no solo color) y la marca del encabezado
    expect(within(tarjeta('Contrato')).getByText('1 pendiente')).toBeTruthy();
    expect(within(tarjeta('Contrato')).getByText('Pendiente:').parentElement.textContent).toMatch(/Contrato sin firmar$/);
    expect(within(tarjeta('Abonos')).getByText('Ninguno')).toBeTruthy(); // 0 pagos
    expect(within(tarjeta('Confirmación')).getByText('Pendiente:').parentElement.textContent).toMatch(/Por confirmar$/);
    // lo que sí está: lugar y cotización
    expect(within(tarjeta('Evento')).getByText('Listo')).toBeTruthy();
    expect(within(tarjeta('Cotización')).getByText('Listo')).toBeTruthy();
    // sin proveedores es un pendiente (como la regla del paso 6); la logística se explica
    const pre = tarjeta('Preparativos');
    expect(within(pre).getByText('Pendiente:').parentElement.textContent).toMatch(/Sin proveedores asignados$/);
    expect(within(pre).getByText('La logística y la lista de compras se generan al confirmar el evento.')).toBeTruthy();
    expect(within(pre).getByText('1 pendiente')).toBeTruthy();
    // en la guía, el paso 7 dice cuántos quedan
    expect(within(screen.getByRole('navigation', { name: 'Pasos del evento' })).getByRole('button', { name: /Resumen/ }).textContent)
      .toContain('4 pendientes');
  });

  it('saldo, compras, proveedores por confirmar y lugar faltante cuentan como pendientes', async () => {
    const e = fichaDemo();
    abrir({
      ...e, id: 'ev-prep', lugar: null, lugarId: null,
      proveedores: { totalElementos: 2, contenido: [...e.proveedores.contenido, { ...e.proveedores.contenido[0], id: 'a2', estado: 'PENDIENTE', proveedor: { nombre: 'Banquetes Doña Inés' }, categoria: 'BANQUETES' }] },
    });
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    expect(estado().textContent).toBe('Quedan 4 pendientes');
    const textos = within(screen.getByRole('list', { name: 'Pendientes' })).getAllByRole('link').map((a) => a.textContent);
    expect(textos).toEqual([
      'Evento: Sin lugar asignado',
      expect.stringMatching(/^Abonos: Saldo pendiente \$\s?6\.270\.000$/),
      'Preparativos: 1 proveedor por confirmar',
      'Preparativos: Sin conseguir: Servilletas',
    ]);
    expect(within(tarjeta('Preparativos')).getByText('1 de 2 conseguidos')).toBeTruthy();
    expect(within(tarjeta('Preparativos')).getByText('2 pendientes')).toBeTruthy();
  });

  it('sin proveedores no sale "Todo listo": coincide con el paso 6 de la guía, que tampoco tiene ✓', async () => {
    abrir({ ...completo(), id: 'ev-sinprov', proveedores: { totalElementos: 0, contenido: [] } });
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    expect(estado().textContent).toBe('Queda 1 pendiente');
    expect(within(screen.getByRole('list', { name: 'Pendientes' })).getByRole('link').textContent).toBe('Preparativos: Sin proveedores asignados');
    const nav = screen.getByRole('navigation', { name: 'Pasos del evento' });
    expect(within(nav).getByRole('button', { name: /Preparativos/ }).closest('li').className).not.toContain('hecho');
    expect(within(nav).getByRole('button', { name: /Resumen/ }).closest('li').className).not.toContain('hecho');
  });

  it('los enlaces de los pendientes y de las tarjetas llevan a su paso y le pasan el foco', async () => {
    abrir(aMedias());
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    const nav = screen.getByRole('navigation', { name: 'Pasos del evento' });

    fireEvent.click(within(screen.getByRole('list', { name: 'Pendientes' })).getByRole('link', { name: 'Contrato: Contrato sin firmar' }));
    const titulo = screen.getByRole('heading', { level: 2, name: 'Crea y firma el contrato' });
    await waitFor(() => expect(document.activeElement).toBe(titulo));
    expect(within(nav).getByRole('button', { name: /Contrato/ }).getAttribute('aria-current')).toBe('step');

    const destinos = [
      ['Evento', 'Ir a Evento', 'Revisa el evento'], ['Cotización', 'Ir a Cotización', 'Genera la cotización'],
      ['Contrato', 'Ir a Contrato', 'Crea y firma el contrato'], ['Abonos', 'Ir a Abonos', 'Registra los abonos'],
      ['Confirmación', 'Ir a Confirmar', 'Confirma el evento'], ['Preparativos', 'Ir a Preparativos', 'Organiza los preparativos'],
    ];
    for (const [t, enlace, encabezado] of destinos) {
      fireEvent.click(within(nav).getByRole('button', { name: /Resumen/ }));
      const a = within(tarjeta(t)).getByRole('link', { name: enlace });
      expect(a.getAttribute('href')).toMatch(/\?paso=/);
      fireEvent.click(a);
      const h = screen.getByRole('heading', { level: 2, name: encabezado });
      await waitFor(() => expect(document.activeElement).toBe(h));
    }
  });

  it('una sección que no cargó muestra el mensaje genérico con su referencia y no cuenta como hecha', async () => {
    const e = { ...completo(), balance: null };
    const { fetch } = abrir(e, {
      errors: [{ message: 'Connection refused: finanzas', path: ['evento', 'balance'], extensions: { classification: 'SERVICE_UNAVAILABLE', status: 503, servicio: 'finanzas' } }],
    });
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    const referencia = fetch.mock.calls[0][1].headers['X-Request-Id'].replace(/-/g, '').slice(0, 8).toUpperCase();
    const abo = tarjeta('Abonos');
    expect(within(abo).getByRole('alert').textContent)
      .toBe(`No se pudo cargar esta parte.No se pudo cargar esta información. Intenta de nuevo en unos minutos. Ref: ${referencia}`);
    expect(within(abo).queryByText('Listo')).toBeNull();
    expect(within(abo).getByText('Pendiente:').parentElement.textContent).toMatch(/No se pudo cargar el balance$/);
    expect(abo.textContent).not.toContain('Connection refused'); // el detalle técnico no se muestra
    expect(estado().textContent).toBe('Queda 1 pendiente');
  });

  it('el recordatorio de proximidad enviado se ve en la tarjeta Evento', async () => {
    const e = completo();
    abrir({ ...e, notificaciones: { totalElementos: 2, contenido: [...e.notificaciones.contenido,
      { id: 'r1', tipo: 'EVENTO_PROXIMO', destinatario: 'cliente:e146', estado: 'GENERADA', fechaCreacion: '2026-11-28T12:00:00Z',
        asunto: 'Faltan 7 días para su evento: Quince años de Lucía', mensaje: 'Hola.' }] } });
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    expect(within(tarjeta('Evento')).getByText(/^Recordatorio enviado: faltan 7 días \(/)).toBeTruthy();
    expect(estado().textContent).toBe('Todo listo'); // el recordatorio no es un pendiente
  });

  it('funciona igual para el asistente (sin pedir la auditoría)', async () => {
    const { llamadas } = abrir(aMedias(), { rol: 'ASISTENTE' });
    await screen.findByRole('heading', { level: 2, name: 'Resumen del evento' });
    expect(estado().textContent).toBe('Quedan 4 pendientes');
    expect(screen.getAllByRole('article')).toHaveLength(6);
    expect(llamadas('FichaEvento')).toEqual([{ id: 'ev-medio', verAuditoria: false }]);
  });

  it('desde Preparativos, "Siguiente: Resumen" abre el paso 7', async () => {
    iniciarSesionDePrueba();
    simularPorOperacion({ FichaEvento: { data: { evento: completo() } } });
    renderizarApp('/eventos/ev-ok');
    fireEvent.click(await screen.findByRole('button', { name: 'Siguiente: Resumen' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Resumen del evento' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Anterior: Preparativos' })).toBeTruthy();
  });
});
