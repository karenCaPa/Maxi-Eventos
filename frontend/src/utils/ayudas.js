import { hoyEnZona } from './fechaPasada.js';

/** Los scripts de verificación y Postman crean entidades "Prueba …"; la interfaz las oculta por defecto. */
export const esNombreDePrueba = (nombre) => /^prueba/i.test(String(nombre ?? '').trim());

/**
 * Clave de idempotencia para un pago: una por formulario. Si la respuesta se pierde y se reintenta, Finanzas devuelve el mismo
 * pago en lugar de cobrar dos veces. crypto.randomUUID existe en contexto seguro (https o localhost).
 */
export function nuevaClave() {
  if (globalThis.crypto?.randomUUID) return `interfaz-${globalThis.crypto.randomUUID()}`;
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  return `interfaz-${Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')}`;
}

/** "" → null y número en texto → número (los campos Int/BigDecimal del schema no aceptan texto). */
export function numeroONulo(valor) {
  if (valor === '' || valor === null || valor === undefined) return null;
  const n = Number(valor);
  return Number.isFinite(n) ? n : null;
}

/**
 * Hoy (± dias) en formato ISO: "2026-12-05". Fase 11I: es el mismo "hoy" de toda la interfaz (en la zona de la configuración
 * y, en el modo demostración, la fecha simulada): así el calendario y la validación de Nuevo evento lo siguen también.
 */
export function hoyIso(dias = 0) {
  return hoyEnZona(dias);
}
