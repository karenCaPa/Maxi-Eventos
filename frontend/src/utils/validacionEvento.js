/**
 * 10B3e: la fecha se elige en el calendario (solo fechas libres), así que las reglas de la fecha quedan aquí como defensa
 * adicional; el caso "martes" lo cubren las pruebas de eventos-service, Postman y los scripts.
 *
 * Validación de los datos del evento EN LA INTERFAZ (10B3d), antes de crear un cliente nuevo: si el evento no se va a poder
 * crear, no se crea el cliente. Repite las reglas de eventos-service (CrearEventoRequest y EventoService.crear), que sigue
 * siendo quien decide: sus errores por campo y sus 409 se muestran igual que antes.
 *
 * Días permitidos: los de la configuración por defecto de eventos-service (DIAS_PERMITIDOS = FRIDAY,SATURDAY,SUNDAY). Si se
 * configurara otra lista, la comprobación de la fecha con fechasDisponibles (que solo devuelve días permitidos) y el propio
 * servicio siguen protegiendo la regla.
 */
import { formatearNumero } from './formato.js';

/** Días de la semana permitidos, como Date.getUTCDay(): 5 = viernes, 6 = sábado, 0 = domingo. */
export const DIAS_PERMITIDOS = [5, 6, 0];
/** El mismo mensaje que devuelve eventos-service (DiasPermitidos.mensaje()). */
export const MENSAJE_DIA_NO_PERMITIDO = 'Solo se pueden registrar eventos en viernes, sábado o domingo.';
export const MENSAJE_FECHA_OCUPADA = 'Esa fecha ya no está libre: se registró otro evento o un bloqueo ese fin de semana. Elige otra fecha en el calendario.';

const MAX_INVITADOS_POR_TIPO = 5000; // @Max(5000) de cantidadAdultos y cantidadNinos

/** "2026-12-05" → número de día (0 = domingo); null si no es una fecha real. */
export function diaDeLaSemana(fechaIso) {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(fechaIso ?? ''));
  if (!partes) return null;
  const [a, m, d] = partes.slice(1).map(Number);
  const fecha = new Date(Date.UTC(a, m - 1, d));
  if (fecha.getUTCFullYear() !== a || fecha.getUTCMonth() !== m - 1 || fecha.getUTCDate() !== d) return null;
  return fecha.getUTCDay();
}

/** Texto del formulario → entero, o un mensaje de error. */
function cantidad(valor, nombre) {
  if (valor === '' || valor === null || valor === undefined) return { error: `Indica la cantidad de ${nombre}.` };
  const n = Number(valor);
  if (!Number.isInteger(n)) return { error: 'Debe ser un número entero.' };
  if (n < 0) return { error: 'No puede ser negativa.' };
  if (n > MAX_INVITADOS_POR_TIPO) return { error: `No puede ser mayor que ${formatearNumero(MAX_INVITADOS_POR_TIPO)}.` };
  return { valor: n };
}

/**
 * Valida el evento del formulario. Devuelve { campo: mensaje } (vacío si todo está bien).
 * @param evento valores del formulario (texto)
 * @param hoy    fecha de hoy en ISO ("2026-11-03"); la fecha debe ser POSTERIOR
 * @param lugar  lugar elegido (con su capacidad), o null
 */
export function validarEvento(evento, { hoy, lugar = null }) {
  const errores = {};

  const nombre = String(evento.nombreEvento ?? '').trim();
  if (!nombre) errores.nombreEvento = 'Escribe el nombre del evento.';
  else if (nombre.length < 3 || nombre.length > 150) errores.nombreEvento = 'Debe tener entre 3 y 150 caracteres.';

  if (!evento.fecha) {
    errores.fecha = 'Elige una fecha en el calendario.';
  } else {
    const dia = diaDeLaSemana(evento.fecha);
    if (dia === null) errores.fecha = 'La fecha no es válida.';
    else if (evento.fecha <= hoy) errores.fecha = 'Debe ser una fecha posterior a hoy.';
    else if (!DIAS_PERMITIDOS.includes(dia)) errores.fecha = MENSAJE_DIA_NO_PERMITIDO;
  }

  if (!evento.tipoEvento) errores.tipoEvento = 'Elige el tipo de evento.';
  if (!evento.zona) errores.zona = 'Elige la zona.';

  const adultos = cantidad(evento.cantidadAdultos, 'adultos');
  const ninos = cantidad(evento.cantidadNinos, 'niños (puede ser 0)');
  if (adultos.error) errores.cantidadAdultos = adultos.error;
  if (ninos.error) errores.cantidadNinos = ninos.error;
  if (!adultos.error && !ninos.error) {
    const invitados = adultos.valor + ninos.valor;
    if (invitados < 1) errores.cantidadAdultos = 'El evento debe tener al menos un invitado.';
    else if (lugar?.capacidad != null && lugar.capacidad < invitados) {
      errores.lugarId = `${lugar.nombre} tiene capacidad para ${formatearNumero(lugar.capacidad)} personas y el evento tiene ${formatearNumero(invitados)} invitados.`;
    }
  }
  return errores;
}
