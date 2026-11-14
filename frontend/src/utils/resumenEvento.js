import { ultimoRecordatorio } from '../componentes/AvisoRecordatorio.jsx';
import {
  CATEGORIAS_PROVEEDOR, ESTADOS_ASIGNACION, ESTADOS_CONTRATO, etiqueta, formatearFecha, formatearFechaHora, formatearMonto,
  formatearNumero, TIPOS_EVENTO, ZONAS,
} from './formato.js';

const plural = (n, uno, varios) => `${formatearNumero(n)} ${n === 1 ? uno : varios}`;

/**
 * Fase 11F: datos del paso 7 "Resumen", a partir de la MISMA respuesta de la ficha (sin peticiones nuevas).
 * Devuelve una tarjeta por cada uno de los 6 pasos y la lista de pendientes (cada uno con el paso al que lleva).
 * Reglas:
 *  - Una sección que no cargó (error parcial) NO cuenta como hecha: su tarjeta muestra el aviso genérico y suma un pendiente.
 *  - Es una guía, no una regla de negocio: quien decide (firma, depósito, confirmación) sigue siendo cada servicio.
 * Tarjeta: { id, titulo, filas: [[etiqueta, valor]], pendientes: [texto], errores: [ErrorApi], notas: [texto] }.
 */
export function resumenEvento(evento, secciones = {}, { pasado = false } = {}) {
  const moneda = (secciones.balance ? null : evento.balance?.moneda) ?? evento.cotizacion?.moneda ?? 'COP';
  const m = (valor) => formatearMonto(valor, moneda);
  const confirmado = evento.estado === 'RESERVADO';
  const tarjetas = [];
  const tarjeta = (id, titulo) => {
    const t = { id, titulo, filas: [], pendientes: [], errores: [], notas: [] };
    tarjetas.push(t);
    return t;
  };
  const deError = new Set(); // pendientes que salen de una sección que no cargó (siguen siéndolo aunque la fecha haya pasado)
  /** Si la sección falló, registra su error y un pendiente; devuelve true para no leer sus datos. */
  const fallo = (t, clave, nombre) => {
    if (!secciones[clave]) return false;
    t.errores.push(secciones[clave]);
    const texto = `No se pudo cargar ${nombre}`;
    t.pendientes.push(texto);
    deError.add(texto);
    return true;
  };

  // 1. Evento
  const ev = tarjeta('evento', 'Evento');
  ev.filas.push(
    ['Nombre', evento.nombreEvento],
    ['Tipo', etiqueta(TIPOS_EVENTO, evento.tipoEvento)],
    ['Fecha', formatearFecha(evento.fecha, { larga: true })],
    ['Invitados', `${formatearNumero(evento.numInvitados)} (${formatearNumero(evento.cantidadAdultos)} adultos, ${formatearNumero(evento.cantidadNinos)} niños)`],
    ['Zona', etiqueta(ZONAS, evento.zona)],
  );
  if (!fallo(ev, 'cliente', 'el cliente')) {
    const c = evento.cliente;
    ev.filas.push(['Cliente', c ? [c.nombreCompleto, c.telefono, c.correo, c.direccion].filter(Boolean).join(' · ') : 'No se encontró el cliente']);
  }
  if (!fallo(ev, 'lugar', 'el lugar')) {
    if (evento.lugar) ev.filas.push(['Lugar', `${evento.lugar.nombre} · ${evento.lugar.direccion} (capacidad ${formatearNumero(evento.lugar.capacidad)})`]);
    else ev.pendientes.push('Sin lugar asignado');
  }
  if (!secciones.notificaciones) {
    const recordatorio = ultimoRecordatorio(evento.notificaciones?.contenido);
    if (recordatorio) {
      ev.notas.push(`Recordatorio enviado${recordatorio.faltan ? `: ${recordatorio.faltan}` : ''}`
        + (recordatorio.fechaCreacion ? ` (${formatearFechaHora(recordatorio.fechaCreacion)})` : ''));
    }
  }

  // 2. Cotización
  const cot = tarjeta('cotizacion', 'Cotización');
  if (!fallo(cot, 'cotizacion', 'la cotización')) {
    if (evento.cotizacion) {
      cot.filas.push(['Valor base', m(evento.cotizacion.valorBase)], ['Total con adicionales', m(evento.cotizacion.total)]);
    } else cot.pendientes.push('Falta cotizar');
  }

  // 3. Contrato
  const con = tarjeta('contrato', 'Contrato');
  if (!fallo(con, 'contrato', 'el contrato')) {
    if (!evento.contrato) con.pendientes.push('Sin contrato');
    else {
      con.filas.push(['Estado', etiqueta(ESTADOS_CONTRATO, evento.contrato.estado)]);
      if (evento.contrato.estado === 'FIRMADO') con.filas.push(['Fecha de firma', formatearFechaHora(evento.contrato.fechaFirma)]);
      else con.pendientes.push('Contrato sin firmar');
    }
  }

  // 4. Abonos
  const abo = tarjeta('abonos', 'Abonos');
  const balanceFallo = fallo(abo, 'balance', 'el balance');
  const pagosFallo = fallo(abo, 'pagos', 'los pagos');
  if (!balanceFallo) {
    const b = evento.balance;
    if (!b) abo.pendientes.push('Sin abonos: primero falta cotizar');
    else {
      abo.filas.push(['Depósito requerido', m(b.depositoInicialRequerido)], ['Abonado', m(b.montoAbonado)], ['Saldo pendiente', m(b.saldoPendiente)]);
      const faltaDeposito = Number(b.depositoInicialRequerido) - Number(b.montoAbonado);
      if (faltaDeposito > 0) abo.pendientes.push(`Depósito: faltan ${m(faltaDeposito)}`);
      else if (Number(b.saldoPendiente) > 0) abo.pendientes.push(`Saldo pendiente ${m(b.saldoPendiente)}`);
    }
  }
  if (!pagosFallo) {
    const n = evento.pagos?.totalElementos ?? evento.pagos?.contenido?.length ?? 0;
    abo.filas.push(['Pagos', n === 0 ? 'Ninguno' : plural(n, 'pago', 'pagos')]);
  }

  // 5. Confirmación
  const conf = tarjeta('confirmar', 'Confirmación');
  if (confirmado) conf.filas.push(['Estado', 'Reservado: la fecha está ocupada']);
  else conf.pendientes.push('Por confirmar');

  // 6. Preparativos
  const pre = tarjeta('preparativos', 'Preparativos');
  if (!fallo(pre, 'proveedores', 'los proveedores')) {
    const lista = evento.proveedores?.contenido ?? [];
    // Sin proveedores es un pendiente: coincide con la regla del paso 6 de la guía (✓ solo con al menos un proveedor)
    if (lista.length === 0) pre.pendientes.push('Sin proveedores asignados');
    else {
      pre.filas.push(['Proveedores', lista.map((p) => `${p.proveedor?.nombre ?? 'Proveedor'}${p.categoria ? ` (${etiqueta(CATEGORIAS_PROVEEDOR, p.categoria)})` : ''}: ${etiqueta(ESTADOS_ASIGNACION, p.estado)}`).join(' · ')]);
      const porConfirmar = lista.filter((p) => p.estado === 'PENDIENTE').length;
      if (porConfirmar > 0) pre.pendientes.push(`${plural(porConfirmar, 'proveedor', 'proveedores')} por confirmar`);
    }
  }
  if (!fallo(pre, 'serviciosAdicionales', 'los servicios adicionales')) {
    const adicionales = evento.serviciosAdicionales ?? [];
    const total = adicionales.reduce((suma, s) => suma + Number(s.subtotal ?? 0), 0);
    pre.filas.push(['Servicios adicionales', adicionales.length === 0 ? 'Ninguno' : `${plural(adicionales.length, 'servicio', 'servicios')} · ${m(total)}`]);
  }
  if (!fallo(pre, 'logistica', 'la logística')) {
    const l = evento.logistica;
    if (!l) pre.notas.push(confirmado ? 'La logística aún no está calculada.' : 'La logística y la lista de compras se generan al confirmar el evento.');
    else {
      pre.filas.push(['Logística', `${formatearNumero(l.meseros)} meseros, ${formatearNumero(l.personalCocina)} de cocina, ${formatearNumero(l.vehiculos)} vehículos`]);
      const items = l.compras?.contenido ?? [];
      const conseguidos = items.filter((i) => i.comprado).length;
      pre.filas.push(['Compras', `${formatearNumero(conseguidos)} de ${formatearNumero(items.length)} conseguidos`]);
      const faltan = items.filter((i) => !i.comprado);
      if (faltan.length > 0) pre.pendientes.push(`Sin conseguir: ${faltan.map((i) => i.nombreItem).join(', ')}`);
    }
  }

  // Fase 11I: con la fecha pasada ya no se puede hacer nada de los pasos, salvo pagar. Si el evento se realizó (RESERVADO),
  // el saldo sigue siendo un pendiente; lo demás que quedó sin hacer pasa a ser una nota ("Quedó sin hacer: …"). Si venció
  // sin confirmarse (PENDIENTE), no queda nada pendiente: todo pasa a nota.
  if (pasado) {
    for (const t of tarjetas) {
      const sigue = (texto) => deError.has(texto) || (confirmado && t.id === 'abonos');
      t.notas.push(...t.pendientes.filter((p) => !sigue(p)).map((p) => (
        t.id === 'confirmar' ? 'No se confirmó antes de su fecha' : `Quedó sin hacer: ${p.charAt(0).toLowerCase()}${p.slice(1)}`)));
      t.pendientes = t.pendientes.filter(sigue);
    }
  }

  const pendientes = tarjetas.flatMap((t) => t.pendientes.map((texto) => ({ paso: t.id, titulo: t.titulo, texto })));
  return { tarjetas, pendientes, listo: pendientes.length === 0 };
}
