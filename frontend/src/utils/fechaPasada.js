// Fase 11I: eventos con fecha pasada. El estado del backend NO cambia (sigue RESERVADO, PENDIENTE o CANCELADO): la interfaz
// calcula si la fecha ya pasó con "hoy" en la zona de la configuración (ZONA_HORARIA de /config.js) y lo muestra con su
// propia etiqueta. Un evento de HOY todavía no pasó (igual que la regla de Eventos al confirmar).
import { fechaSimulada, zonaHoraria } from '../config.js';
import { formatearFecha } from './formato.js';

/**
 * "Hoy" (o hoy ± dias) en la zona horaria del negocio, como "AAAA-MM-DD". "ahora" se puede pasar en las pruebas.
 * Fase 11I: con el MODO DEMOSTRACIÓN (FRONTEND_FECHA_SIMULADA en /config.js) y sin "ahora", "hoy" es esa fecha. Es el único
 * "hoy" de la interfaz: lo usan Realizado / Vencido, "Eventos pasados", "Próximos 7 días" y el calendario de Nuevo evento.
 */
export function hoyEnZona(dias = 0, ahora = undefined, zona = zonaHoraria()) {
  const simulada = ahora === undefined ? fechaSimulada() : null;
  if (simulada) {
    const [a, m, d] = simulada.split('-').map(Number);
    return new Date(Date.UTC(a, m - 1, d + dias)).toISOString().slice(0, 10);
  }
  const partes = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', { timeZone: zona, year: 'numeric', month: '2-digit', day: '2-digit' })
      .formatToParts(ahora ?? new Date()).map((p) => [p.type, p.value]),
  );
  const dia = new Date(Date.UTC(Number(partes.year), Number(partes.month) - 1, Number(partes.day) + dias));
  return dia.toISOString().slice(0, 10);
}

/** true si la fecha del evento ("AAAA-MM-DD") es anterior a hoy. */
export function esFechaPasada(fecha, hoy = hoyEnZona()) {
  return Boolean(fecha) && String(fecha) < hoy;
}

/** Etiquetas de la interfaz para los eventos con fecha pasada (no son estados del backend). */
export const ESTADOS_PASADOS = {
  REALIZADO: {
    texto: 'Realizado',
    descripcion: 'Estado: Realizado. El evento estaba reservado y su fecha ya pasó.',
  },
  VENCIDO: {
    texto: 'Vencido',
    descripcion: 'Estado: Vencido. El evento quedó pendiente: su fecha pasó sin que se confirmara.',
  },
};

/**
 * Lo que se MUESTRA del estado: REALIZADO (reservado con fecha pasada), VENCIDO (pendiente con fecha pasada) o el estado
 * real del backend. Un evento cancelado se ve igual, pase o no su fecha.
 */
export function estadoVisible(estado, fecha, hoy = hoyEnZona()) {
  if (!esFechaPasada(fecha, hoy)) return estado;
  if (estado === 'RESERVADO') return 'REALIZADO';
  if (estado === 'PENDIENTE') return 'VENCIDO';
  return estado;
}

/** Aviso de la ficha para un evento con fecha pasada (null si no aplica). */
export function textoAvisoPasado(evento, hoy = hoyEnZona()) {
  const visible = estadoVisible(evento.estado, evento.fecha, hoy);
  const fecha = formatearFecha(evento.fecha, { larga: true }).toLowerCase();
  if (visible === 'REALIZADO') return `Este evento se realizó el ${fecha}.`;
  if (visible === 'VENCIDO') return `Este evento venció el ${fecha} sin confirmarse.`;
  return null;
}
