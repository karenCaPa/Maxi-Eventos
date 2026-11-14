// URL del gateway GraphQL (punto único de entrada de la arquitectura).
// 1. En Docker: window.__CONFIG__.gatewayUrl, que nginx genera al arrancar con FRONTEND_GATEWAY_URL (/config.js).
// 2. En desarrollo: VITE_GATEWAY_URL (opcional) o http://localhost:8090.
const URL_POR_DEFECTO = 'http://localhost:8090';

/**
 * Fase 11H: zona horaria de las fechas y horas de la interfaz. En Docker la pone nginx en /config.js desde ZONA_HORARIA (la
 * misma de los servicios); en desarrollo y en las pruebas, America/Bogota.
 */
export const ZONA_POR_DEFECTO = 'America/Bogota';
export function zonaHoraria() {
  const enArranque = typeof window !== 'undefined' ? window.__CONFIG__?.zonaHoraria : undefined;
  return enArranque || ZONA_POR_DEFECTO;
}

/**
 * Fase 11I: MODO DEMOSTRACIÓN. Fecha "AAAA-MM-DD" que la interfaz usa como "hoy" (en Docker la pone nginx en /config.js
 * desde FRONTEND_FECHA_SIMULADA). null (lo normal) = la fecha real. Solo se acepta una fecha que exista: cualquier otro
 * valor se ignora (y nginx ni siquiera arranca con él). El backend no la conoce: sigue con su reloj real.
 */
export function fechaSimulada() {
  const valor = typeof window !== 'undefined' ? window.__CONFIG__?.fechaSimulada : undefined;
  if (typeof valor !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(valor)) return null;
  const [a, m, d] = valor.split('-').map(Number);
  const fecha = new Date(Date.UTC(a, m - 1, d));
  return fecha.getUTCFullYear() === a && fecha.getUTCMonth() === m - 1 && fecha.getUTCDate() === d ? valor : null;
}

export function urlGateway() {
  const enArranque = typeof window !== 'undefined' ? window.__CONFIG__?.gatewayUrl : undefined;
  const url = enArranque || import.meta.env.VITE_GATEWAY_URL || URL_POR_DEFECTO;
  return url.replace(/\/+$/, '');
}
