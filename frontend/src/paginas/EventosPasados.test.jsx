import { render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { InsigniaEstado } from '../componentes/Insignia.jsx';
import { fichaDemo } from '../test/fichaDemo.js';
import { fijarHoy } from '../test/fechasSimuladas.js';
import { diferida, iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';

// Fase 11I: eventos con fecha pasada. El estado del backend no cambia: la interfaz muestra "Realizado" (RESERVADO) o
// "Vencido" (PENDIENTE) según "hoy" en la zona de la configuración. "Hoy" se fija en cada prueba (fijarHoy).

const pagina = (contenido, total = contenido.length) => ({
  data: { eventos: { pagina: 0, tamanoPagina: 20, totalElementos: total, totalPaginas: 1, contenido } },
});
const evento = (id, nombreEvento, fecha, estado) => ({
  id, nombreEvento, fecha, tipoEvento: 'BODA', numInvitados: 100, zona: 'NEIVA', estado,
  cliente: { id: `c-${id}`, nombreCompleto: 'Cliente de prueba' },
});
const SIN_PROXIMOS = { data: { eventos: { contenido: [] } } };

describe('Insignia del estado (Realizado / Vencido)', () => {
  const insignia = (estado, fecha) => {
    const { container, unmount } = render(<InsigniaEstado estado={estado} fecha={fecha} hoy="2026-11-10" />);
    const span = container.querySelector('.insignia');
    const datos = { texto: span.textContent, clase: span.className, aria: span.getAttribute('aria-label') };
    unmount();
    return datos;
  };

  it('RESERVADO de ayer: "Realizado" en gris y el aria-label explica que la fecha pasó; de hoy y de mañana: "Reservado"', () => {
    expect(insignia('RESERVADO', '2026-11-09')).toEqual({
      texto: 'Realizado', clase: 'insignia insignia-realizado',
      aria: 'Estado: Realizado. El evento estaba reservado y su fecha ya pasó.',
    });
    expect(insignia('RESERVADO', '2026-11-10')).toEqual({ texto: 'Reservado', clase: 'insignia insignia-reservado', aria: null });
    expect(insignia('RESERVADO', '2026-11-11')).toEqual({ texto: 'Reservado', clase: 'insignia insignia-reservado', aria: null });
  });

  it('PENDIENTE de ayer: "Vencido"; CANCELADO se ve igual', () => {
    expect(insignia('PENDIENTE', '2026-11-09')).toEqual({
      texto: 'Vencido', clase: 'insignia insignia-vencido',
      aria: 'Estado: Vencido. El evento quedó pendiente: su fecha pasó sin que se confirmara.',
    });
    expect(insignia('PENDIENTE', '2026-11-10').texto).toBe('Pendiente');
    expect(insignia('CANCELADO', '2026-11-09')).toEqual({ texto: 'Cancelado', clase: 'insignia insignia-cancelado', aria: null });
  });
});

describe('Listado: sección "Eventos pasados"', () => {
  it('los próximos se piden de hoy en adelante; los pasados, hasta ayer y el más reciente primero, plegados y con su número', async () => {
    fijarHoy(vi, '2026-11-10');
    iniciarSesionDePrueba();
    const { llamadas, fetch } = simularPorOperacion({
      ProximosEventos: SIN_PROXIMOS,
      Eventos: pagina([evento('f1', 'Boda de Valentina y Santiago', '2026-11-21', 'RESERVADO')]),
      EventosPasados: pagina([
        evento('p1', 'Boda de Gabriela y Mateo', '2026-11-07', 'RESERVADO'),
        evento('p2', 'Quince años de Ana', '2026-10-31', 'PENDIENTE'),
      ]),
    });
    renderizarApp('/eventos');

    const resumen = await screen.findByText('Eventos pasados (2)');
    const seccion = resumen.closest('details');
    expect(seccion.open).toBe(false); // plegada por defecto
    expect(seccion.className).toContain('eventos-pasados');
    // Dentro: Realizado y Vencido (con texto, no solo color); el futuro sigue arriba como "Reservado"
    expect(within(seccion).getByRole('img', { name: /Estado: Realizado/ }).textContent).toBe('Realizado');
    expect(within(seccion).getByRole('img', { name: /Estado: Vencido/ }).textContent).toBe('Vencido');
    expect(within(seccion).queryByText('Boda de Valentina y Santiago')).toBeNull();
    const futuro = screen.getByRole('link', { name: 'Boda de Valentina y Santiago' }).closest('li');
    expect(within(futuro).getByText('Reservado')).toBeTruthy();
    // Orden en la página: los próximos primero, la sección de pasados al final
    expect(futuro.compareDocumentPosition(seccion) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    expect(llamadas('Eventos')).toEqual([{ estado: null, tipo: null, desde: '2026-11-10', hasta: null, page: 0, size: 20, sort: 'fecha,asc' }]);
    expect(llamadas('EventosPasados')).toEqual([{ estado: null, tipo: null, desde: null, hasta: '2026-11-09', page: 0, size: 20 }]);
    const consulta = fetch.mock.calls.map(([, o]) => JSON.parse(o.body).query).find((q) => /query EventosPasados/.test(q));
    expect(consulta).toContain('sort: "fecha,desc"');
  });

  it('caso límite: un evento de HOY está en los próximos (no en pasados)', async () => {
    fijarHoy(vi, '2026-11-07');
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({
      ProximosEventos: SIN_PROXIMOS,
      Eventos: pagina([evento('g1', 'Boda de Gabriela y Mateo', '2026-11-07', 'RESERVADO')]),
      EventosPasados: pagina([]),
    });
    renderizarApp('/eventos');

    const fila = (await screen.findByRole('link', { name: 'Boda de Gabriela y Mateo' })).closest('li');
    expect(within(fila).getByText('Reservado')).toBeTruthy();
    // sin pasados la sección se muestra igual (siempre), vacía: el evento de hoy no está en ella
    const seccion = (await screen.findByText('Eventos pasados (0)')).closest('details');
    expect(within(seccion).queryByText('Boda de Gabriela y Mateo')).toBeNull();
    expect(llamadas('Eventos')[0].desde).toBe('2026-11-07');
    await waitFor(() => expect(llamadas('EventosPasados')).toEqual([expect.objectContaining({ hasta: '2026-11-06' })]));
  });

  it('con los filtros: "hasta" en el pasado solo pide pasados; "desde" en el futuro solo pide próximos', async () => {
    fijarHoy(vi, '2026-11-10');
    iniciarSesionDePrueba();
    const solo = simularPorOperacion({
      ProximosEventos: SIN_PROXIMOS, Eventos: pagina([]),
      EventosPasados: pagina([evento('p1', 'Boda de Gabriela y Mateo', '2026-10-03', 'RESERVADO')]),
    });
    const { unmount } = renderizarApp('/eventos?estado=RESERVADO&hasta=2026-10-31');
    expect(await screen.findByText('Eventos pasados (1)')).toBeTruthy();
    expect(screen.getByText('No hay eventos próximos con estos filtros.')).toBeTruthy();
    expect(solo.llamadas('Eventos')).toEqual([]);
    expect(solo.llamadas('EventosPasados')).toEqual([{ estado: 'RESERVADO', tipo: null, desde: null, hasta: '2026-10-31', page: 0, size: 20 }]);
    unmount();

    const futuro = simularPorOperacion({ ProximosEventos: SIN_PROXIMOS, Eventos: pagina([]) });
    renderizarApp('/eventos?desde=2027-01-01');
    await screen.findByText('No hay eventos próximos con estos filtros.');
    expect(futuro.llamadas('Eventos')[0]).toMatchObject({ desde: '2027-01-01' });
    expect(futuro.llamadas('EventosPasados')).toEqual([]);
    // sin consulta (el filtro es solo hacia el futuro) la sección sigue al final, con 0 y el texto de los filtros
    const seccion = screen.getByText('Eventos pasados (0)').closest('details');
    expect(within(seccion).getByText('No hay eventos pasados con estos filtros.')).toBeTruthy();
  });

  it('SIEMPRE se muestra al final, plegada; vacía y sin filtros dice que los eventos llegarán solos', async () => {
    fijarHoy(vi, '2026-11-10');
    iniciarSesionDePrueba();
    simularPorOperacion({
      ProximosEventos: SIN_PROXIMOS,
      Eventos: pagina([evento('f1', 'Boda de Valentina y Santiago', '2026-11-21', 'RESERVADO')]),
      EventosPasados: pagina([]),
    });
    renderizarApp('/eventos');

    const seccion = (await screen.findByText('Eventos pasados (0)')).closest('details');
    expect(seccion.open).toBe(false); // plegada, como con eventos
    expect(seccion.className).toContain('eventos-pasados');
    expect(within(seccion).getByText(
      'Todavía no hay eventos pasados. Los eventos aparecerán aquí automáticamente cuando pase su fecha.')).toBeTruthy();
    expect(within(seccion).queryByRole('list')).toBeNull();
    // al final del listado: después del último evento próximo
    const futuro = screen.getByRole('link', { name: 'Boda de Valentina y Santiago' }).closest('li');
    expect(futuro.compareDocumentPosition(seccion) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(screen.queryByText('No hay eventos pasados con estos filtros.')).toBeNull();
  });

  it('con filtros y ningún pasado que los cumpla: "No hay eventos pasados con estos filtros."', async () => {
    fijarHoy(vi, '2026-11-10');
    iniciarSesionDePrueba();
    simularPorOperacion({ ProximosEventos: SIN_PROXIMOS, Eventos: pagina([]), EventosPasados: pagina([]) });
    renderizarApp('/eventos?estado=CANCELADO');

    const seccion = (await screen.findByText('Eventos pasados (0)')).closest('details');
    expect(within(seccion).getByText('No hay eventos pasados con estos filtros.')).toBeTruthy();
    expect(within(seccion).queryByText(/Todavía no hay eventos pasados/)).toBeNull();
  });

  it('mientras carga dice "Cargando…" y, si se abrió, sigue abierta al llegar la respuesta (sin parpadeo de "vacía")', async () => {
    fijarHoy(vi, '2026-11-10');
    iniciarSesionDePrueba();
    const respuesta = diferida();
    simularPorOperacion({ ProximosEventos: SIN_PROXIMOS, Eventos: pagina([]), EventosPasados: () => respuesta.promesa });
    renderizarApp('/eventos');

    const titulo = await screen.findByText('Eventos pasados');
    const seccion = titulo.closest('details');
    expect(within(seccion).getByText('Cargando eventos pasados…')).toBeTruthy();
    expect(screen.queryByText(/Todavía no hay eventos pasados/)).toBeNull();
    seccion.open = true;

    respuesta.resolver(pagina([evento('p1', 'Boda de Gabriela y Mateo', '2026-11-07', 'RESERVADO')]));
    expect(await screen.findByText('Eventos pasados (1)')).toBeTruthy();
    expect(screen.getByText('Eventos pasados (1)').closest('details')).toBe(seccion);
    expect(seccion.open).toBe(true);
    expect(within(seccion).getByRole('link', { name: 'Boda de Gabriela y Mateo' })).toBeTruthy();
  });
});

describe('Bloque "Próximos 7 días": solo eventos futuros', () => {
  it('pide desde hoy (en la zona de la configuración) y descarta uno de ayer si llegara', async () => {
    fijarHoy(vi, '2026-11-10');
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({
      Eventos: pagina([]),
      ProximosEventos: { data: { eventos: { contenido: [
        { id: 'a', nombreEvento: 'Boda de ayer', fecha: '2026-11-09', tipoEvento: 'BODA' },
        { id: 'h', nombreEvento: 'Boda de hoy', fecha: '2026-11-10', tipoEvento: 'BODA' },
        { id: 'm', nombreEvento: 'Boda de mañana', fecha: '2026-11-11', tipoEvento: 'BODA' },
      ] } } },
    });
    renderizarApp('/eventos');

    const region = await screen.findByRole('region', { name: 'Próximos 7 días' });
    expect(await within(region).findByRole('link', { name: 'Boda de hoy' })).toBeTruthy();
    expect(within(region).getByRole('link', { name: 'Boda de mañana' })).toBeTruthy();
    expect(within(region).queryByText('Boda de ayer')).toBeNull();
    expect(within(region).getByText('Hoy')).toBeTruthy();
    expect(llamadas('ProximosEventos')).toEqual([{ desde: '2026-11-10', hasta: '2026-11-17' }]);
  });
});

describe('Ficha de un evento con la fecha pasada', () => {
  const boton = (nombre) => screen.queryByRole('button', { name: nombre });

  /** Evento PENDIENTE con contrato sin firmar (para "Vencido"). */
  const pendiente = () => {
    const e = fichaDemo();
    return { ...e, estado: 'PENDIENTE', logistica: null, contrato: { ...e.contrato, estado: 'PENDIENTE', fechaFirma: null } };
  };
  /** La misma ficha con una asignación de proveedor PENDIENTE (la que mostraría "Confirmar"). */
  const conAsignacionPendiente = (e) => {
    const confirmada = e.proveedores.contenido[0];
    const porConfirmar = { ...confirmada, id: 'a2', servicio: 'Fotografía', estado: 'PENDIENTE' };
    return { ...e, proveedores: { totalElementos: 2, contenido: [confirmada, porConfirmar] } };
  };
  const proveedores = () => screen.getByRole('region', { name: 'Proveedores asignados' });

  it('RESERVADO del 5 de diciembre visto el 6: aviso "se realizó", sin las acciones que ya no tienen sentido, con pagos y PDF', async () => {
    fijarHoy(vi, '2026-12-06');
    iniciarSesionDePrueba();
    const base = fichaDemo();
    // Sin cotización ni contrato no se vería si se ocultan "Generar cotización" y "Crear contrato": otra ficha más abajo
    simularPorOperacion({
      FichaEvento: { data: { evento: conAsignacionPendiente({ ...base, contrato: { ...base.contrato, estado: 'PENDIENTE' } }) } },
    });
    renderizarApp(`/eventos/${base.id}?paso=todo`);

    expect(await screen.findByText('Este evento se realizó el sábado, 5 de diciembre de 2026.')).toBeTruthy();
    expect(screen.getByRole('img', { name: /Estado: Realizado/ })).toBeTruthy();
    for (const oculto of ['Confirmar evento', 'Cancelar evento', 'Cambiar lugar', 'Asignar lugar', 'Firmar contrato',
      'Agregar servicio adicional', 'Asignar proveedor']) {
      expect(boton(oculto), oculto).toBeNull();
    }
    expect(screen.queryByRole('group', { name: 'Acciones del evento' })).toBeNull();
    // Proveedores: la asignación PENDIENTE se ve, pero sin "Confirmar" ni la columna "Acción"
    expect(within(proveedores()).getByText('Fotografía')).toBeTruthy();
    expect(within(proveedores()).queryByRole('button', { name: 'Confirmar' })).toBeNull();
    expect(within(proveedores()).queryByRole('columnheader', { name: 'Acción' })).toBeNull();
    // Se mantienen: registrar pagos (queda saldo), los PDF y la lista de compras
    expect(boton('Registrar pago')).toBeTruthy();
    expect(boton('Descargar contrato (PDF)')).toBeTruthy();
    expect(boton('Descargar anexo (PDF)')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Lista de compras' })).toBeTruthy();
    expect(screen.getByText('Servilletas')).toBeTruthy();
  });

  it('sin cotización ni contrato: tampoco "Generar cotización" ni "Crear contrato"', async () => {
    fijarHoy(vi, '2026-12-06');
    iniciarSesionDePrueba();
    const e = conAsignacionPendiente({ ...pendiente(), cotizacion: null, contrato: null, balance: null, lugar: null });
    simularPorOperacion({ FichaEvento: { data: { evento: e } } });
    renderizarApp(`/eventos/${e.id}?paso=todo`);

    expect(await screen.findByText('Este evento venció el sábado, 5 de diciembre de 2026 sin confirmarse.')).toBeTruthy();
    for (const oculto of ['Generar cotización', 'Crear contrato', 'Asignar lugar', 'Confirmar evento', 'Cancelar evento',
      'Asignar proveedor']) {
      expect(boton(oculto), oculto).toBeNull();
    }
    expect(within(proveedores()).queryByRole('button', { name: 'Confirmar' })).toBeNull(); // también en un evento Vencido
  });

  it('el mismo día del evento (caso límite) todo sigue como antes: sin aviso y con sus acciones', async () => {
    fijarHoy(vi, '2026-12-05');
    iniciarSesionDePrueba();
    const base = fichaDemo();
    simularPorOperacion({ FichaEvento: { data: { evento: conAsignacionPendiente(base) } } });
    renderizarApp(`/eventos/${base.id}?paso=todo`);

    expect(await screen.findByRole('button', { name: 'Cancelar evento' })).toBeTruthy();
    expect(boton('Cambiar lugar')).toBeTruthy();
    expect(boton('Agregar servicio adicional')).toBeTruthy();
    expect(boton('Asignar proveedor')).toBeTruthy();
    expect(within(proveedores()).getAllByRole('button', { name: 'Confirmar' })).toHaveLength(1); // solo la pendiente
    expect(within(proveedores()).getByRole('columnheader', { name: 'Acción' })).toBeTruthy();
    expect(screen.queryByText(/Este evento se realizó/)).toBeNull();
    expect(screen.getAllByText('Reservado').length).toBeGreaterThan(0);
  });

  it('Resumen (se abre solo): dice que ya se realizó y el saldo sigue pendiente; lo demás queda como nota', async () => {
    fijarHoy(vi, '2026-12-06');
    iniciarSesionDePrueba();
    const base = fichaDemo();
    simularPorOperacion({ FichaEvento: { data: { evento: base } } });
    renderizarApp(`/eventos/${base.id}`); // sin ?paso: con la fecha pasada abre el Resumen

    expect(await screen.findByRole('heading', { name: 'Resumen del evento' })).toBeTruthy();
    expect(screen.getByText('El evento ya se realizó').closest('p').textContent).toBe('El evento ya se realizó el sábado, 5 de diciembre de 2026.');
    expect(screen.getByText('Queda 1 pendiente')).toBeTruthy();
    const pendientes = screen.getByRole('list', { name: 'Pendientes' });
    expect(within(pendientes).getByRole('link').textContent).toMatch(/^Abonos: Saldo pendiente/);
    expect(screen.getByText('Quedó sin hacer: sin conseguir: Servilletas')).toBeTruthy();
  });

  it('Resumen sin saldo: "Sin pendientes" (no "Todo listo")', async () => {
    fijarHoy(vi, '2026-12-06');
    iniciarSesionDePrueba();
    const base = fichaDemo();
    const pagado = {
      ...base,
      balance: { ...base.balance, montoAbonado: 8880000, saldoPendiente: 0 },
      logistica: { ...base.logistica, compras: { totalElementos: 0, contenido: [] } },
    };
    simularPorOperacion({ FichaEvento: { data: { evento: pagado } } });
    renderizarApp(`/eventos/${base.id}`);

    // en el Resumen y en el paso 7 del camino
    expect(await screen.findAllByText('Sin pendientes')).toHaveLength(2);
    expect(screen.queryByText('Todo listo')).toBeNull();
    expect(boton('Registrar pago')).toBeNull(); // pagado por completo: igual que antes de la 11I
  });

  it('PENDIENTE vencido: paso "Confirmar" sin botón y con la explicación; insignia "Vencido"', async () => {
    fijarHoy(vi, '2026-12-06');
    iniciarSesionDePrueba();
    const e = pendiente();
    simularPorOperacion({ FichaEvento: { data: { evento: e } } });
    renderizarApp(`/eventos/${e.id}?paso=confirmar`);

    expect(await screen.findByText('Este evento ya no se puede confirmar: su fecha pasó sin confirmarse.')).toBeTruthy();
    expect(boton('Confirmar evento')).toBeNull();
    expect(screen.getByRole('img', { name: /Estado: Vencido/ }).textContent).toBe('Vencido');
    expect(screen.queryByText(/^Ahora:/)).toBeNull();
  });
});
