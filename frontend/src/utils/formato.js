// Formatos es-CO: fechas, montos y nombres legibles de los valores del backend.
import { zonaHoraria } from '../config.js';

const LOCALE = 'es-CO';
// Fase 11H: la zona sale de /config.js (ZONA_HORARIA en Docker); por defecto America/Bogota
const ZONA = zonaHoraria();

const fechaLarga = new Intl.DateTimeFormat(LOCALE, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
const fechaCorta = new Intl.DateTimeFormat(LOCALE, { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' });
const fechaHora = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short', timeZone: ZONA });

/** "2026-12-05" (fecha sin hora) → Date en UTC, para que la zona horaria no cambie el día. */
export function fechaSinHora(valor) {
  const [a, m, d] = String(valor).split('-').map(Number);
  return new Date(Date.UTC(a, m - 1, d));
}

/** Solo la primera letra en mayúscula ("sábado, 5 de diciembre…" → "Sábado, 5 de diciembre…"). */
export function primeraMayuscula(texto) {
  const s = String(texto ?? '');
  return s ? s.charAt(0).toLocaleUpperCase(LOCALE) + s.slice(1) : s;
}

/** Corta: "05 dic 2026". Larga: "Sábado, 5 de diciembre de 2026". */
export function formatearFecha(valor, { larga = false } = {}) {
  if (!valor) return '—';
  const fecha = fechaSinHora(valor);
  return larga ? primeraMayuscula(fechaLarga.format(fecha)) : fechaCorta.format(fecha);
}

export function formatearFechaHora(valor) {
  if (!valor) return '—';
  const fecha = new Date(valor);
  return Number.isNaN(fecha.getTime()) ? '—' : fechaHora.format(fecha);
}

// ---- Fase 11H: hora en que se recibió una notificación (campanita)
const soloHora = new Intl.DateTimeFormat(LOCALE, { hour: 'numeric', minute: '2-digit', timeZone: ZONA });
const diaEnZona = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: ZONA });
const fechaLargaEnZona = new Intl.DateTimeFormat(LOCALE, { day: 'numeric', month: 'long', year: 'numeric', timeZone: ZONA });
const DIA_MS = 86_400_000;

/**
 * Cuándo se recibió, en la zona de la configuración: "Hoy, 3:45 p. m.", "Ayer, 9:10 a. m." o "5 de noviembre de 2026,
 * 9:10 a. m." (más antigua). `ahora` se puede pasar en las pruebas.
 */
export function formatearRecibida(valor, ahora = new Date()) {
  const fecha = new Date(valor);
  if (!valor || Number.isNaN(fecha.getTime())) return '—';
  const dia = diaEnZona.format(fecha);
  const hora = soloHora.format(fecha);
  if (dia === diaEnZona.format(ahora)) return `Hoy, ${hora}`;
  if (dia === diaEnZona.format(new Date(ahora.getTime() - DIA_MS))) return `Ayer, ${hora}`;
  return `${fechaLargaEnZona.format(fecha)}, ${hora}`;
}

/** "hace un momento", "hace 5 min", "hace 2 h" (menos de un día); null si es más antigua o futura. */
export function haceCuanto(valor, ahora = new Date()) {
  const ms = ahora.getTime() - new Date(valor).getTime();
  if (Number.isNaN(ms) || ms < 0 || ms >= DIA_MS) return null;
  const minutos = Math.floor(ms / 60_000);
  if (minutos < 1) return 'hace un momento';
  if (minutos < 60) return `hace ${minutos} min`;
  return `hace ${Math.floor(minutos / 60)} h`;
}

const formatosMoneda = new Map();
/** Montos (BigDecimal llega como número): $ 8.880.000 (COP sin decimales si no los tiene). */
export function formatearMonto(valor, moneda = 'COP') {
  if (valor === null || valor === undefined || valor === '') return '—';
  const clave = moneda || 'COP';
  if (!formatosMoneda.has(clave)) {
    formatosMoneda.set(clave, new Intl.NumberFormat(LOCALE, { style: 'currency', currency: clave, minimumFractionDigits: 0, maximumFractionDigits: 2 }));
  }
  return formatosMoneda.get(clave).format(Number(valor));
}

const entero = new Intl.NumberFormat(LOCALE);
export function formatearNumero(valor) {
  return valor === null || valor === undefined ? '—' : entero.format(valor);
}

// Etiquetas en español natural para los valores que llegan del backend.
export const TIPOS_EVENTO = { QUINCE_ANOS: 'Quince años', BODA: 'Boda' };
export const ZONAS = { NEIVA: 'Neiva', RURAL: 'Rural' };
export const ESTADOS_EVENTO = { PENDIENTE: 'Pendiente', RESERVADO: 'Reservado', CANCELADO: 'Cancelado' };
export const ESTADOS_CONTRATO = { PENDIENTE: 'Pendiente de firma', FIRMADO: 'Firmado' };
export const ESTADOS_ASIGNACION = { PENDIENTE: 'Pendiente', CONFIRMADO: 'Confirmado', CANCELADO: 'Cancelado' };
export const ESTADOS_LUGAR = { ACTIVO: 'Activo', INACTIVO: 'Inactivo' };
export const ESTADOS_NOTIFICACION = { GENERADA: 'Generada' };
export const TIPOS_LUGAR = { SALON: 'Salón', FINCA: 'Finca', HACIENDA: 'Hacienda', KIOSCO: 'Kiosco' };
export const METODOS_PAGO = { TRANSFERENCIA: 'Transferencia', EFECTIVO: 'Efectivo', TARJETA: 'Tarjeta' };
export const CATEGORIAS_PROVEEDOR = {
  SONIDO: 'Sonido', BANQUETES: 'Banquetes', FLORISTERIA: 'Floristería', FOTOGRAFIA: 'Fotografía', DECORACION: 'Decoración',
};
export const TIPOS_MENSAJE = {
  EVENTO_CONFIRMADO: 'Evento confirmado',
  PAGO_REGISTRADO: 'Pago registrado',
  SERVICIO_ADICIONAL_AGREGADO: 'Servicio adicional agregado',
  EVENTO_PROXIMO: 'Recordatorio de evento próximo',
};
export const SERVICIOS = {
  'EVENTOS-SERVICE': 'Eventos', 'FINANZAS-SERVICE': 'Finanzas', 'CONTRATOS-SERVICE': 'Contratos',
  'LOGISTICA-SERVICE': 'Logística', 'PROVEEDORES-SERVICE': 'Proveedores',
};

/**
 * Nombre legible de un valor del backend. Busca en el mapa sin distinguir mayúsculas; si el valor no se conoce,
 * lo muestra con solo la primera letra en mayúscula ("NEQUI" → "Nequi", "PAGO_EN_LINEA" → "Pago en linea").
 */
export function etiqueta(mapa, valor) {
  if (valor === null || valor === undefined || valor === '') return '—';
  const conocido = mapa[String(valor).toUpperCase()];
  return conocido ?? primeraMayuscula(String(valor).replace(/_/g, ' ').toLocaleLowerCase(LOCALE));
}

/** Payload JSON de la auditoría, con sangría (si no es JSON válido, el texto original). */
export function formatearJson(texto) {
  try {
    return JSON.stringify(typeof texto === 'string' ? JSON.parse(texto) : texto, null, 2);
  } catch {
    return String(texto ?? '');
  }
}
