/**
 * Fase 11H: aviso interno "algo cambió, puede haber notificaciones nuevas". Lo lanzan la ficha tras cada acción y
 * "Ejecutar recordatorios"; la campanita lo escucha y vuelve a consultar el número sin esperar los 30 s.
 */
export const EVENTO_NOTIFICACIONES = 'maxi-eventos:notificaciones';

export function avisarCambios() {
  window.dispatchEvent(new Event(EVENTO_NOTIFICACIONES));
}
