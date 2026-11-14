// Cliente GraphQL propio (sin librerías): fetch hacia el gateway, token, y traducción de errores.
// Formato de los errores del gateway (docs/graphql.md): message en español, path, y en extensions
// classification, status, servicio y, en los 400, errores: [{campo, mensaje}].
//
// Fase 11C — mensajes técnicos al log, mensaje genérico en la interfaz:
// - Cada petición lleva un código de referencia (header X-Request-Id, un UUID). El gateway lo escribe en sus logs junto al
//   error real y lo reenvía a los servicios.
// - Errores de NEGOCIO y de VALIDACIÓN (CONFLICT, BAD_REQUEST con errores por campo, NOT_FOUND, UNAUTHORIZED, FORBIDDEN):
//   se muestra el mensaje del servicio, que le sirve al usuario.
// - Errores TÉCNICOS (SERVICE_UNAVAILABLE, INTERNAL_ERROR, sin conexión, respuesta que no es GraphQL, errores de graphql-java
//   y cualquier clasificación desconocida): se muestra MENSAJE_TECNICO + "Ref: XXXXXXXX"; el detalle queda en `detalle` (y
//   en el log del gateway), nunca en pantalla.
import { urlGateway } from '../config.js';
import { avisarSesionVencida, obtenerToken } from '../sesion/almacen.js';

export const MENSAJE_SESION_VENCIDA = 'Tu sesión venció o ya no es válida. Inicia sesión de nuevo.';
export const MENSAJE_TECNICO = 'No se pudo completar la operación. Intenta de nuevo en unos minutos.';
/** El error parcial de una sección de la ficha (su servicio falló): siempre genérico. */
export const MENSAJE_SECCION = 'No se pudo cargar esta información. Intenta de nuevo en unos minutos.';
export const CABECERA_REFERENCIA = 'X-Request-Id';

/** Clasificaciones cuyo mensaje se muestra tal cual (negocio, validación, permisos y sesión). */
const VISIBLES = new Set(['BAD_REQUEST', 'UNAUTHORIZED', 'FORBIDDEN', 'NOT_FOUND', 'CONFLICT']);
export const esTecnica = (clasificacion) => !VISIBLES.has(clasificacion);

/** Un UUID nuevo por petición (crypto.randomUUID existe en contexto seguro: https o localhost). */
export function nuevaReferencia() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  const h = Array.from(globalThis.crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`;
}

/** "3f9a12bc-…" → "3F9A12BC": lo que se muestra. En el log del gateway está el UUID completo (empieza por esos 8). */
export function codigoReferencia(referencia) {
  return referencia ? referencia.replace(/-/g, '').slice(0, 8).toUpperCase() : null;
}

/** Error de una operación, con lo necesario para mostrarlo junto a cada campo o en un aviso. */
export class ErrorApi extends Error {
  constructor(mensaje, {
    clasificacion = 'INTERNAL_ERROR', status = null, servicio = null, campos = {}, path = null, referencia = null,
    tecnico = false, detalle = null,
  } = {}) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.clasificacion = clasificacion;
    this.status = status;
    this.servicio = servicio;
    this.campos = campos; // { campo: mensaje }
    this.path = path;
    this.referencia = referencia; // UUID enviado en X-Request-Id (null si la petición no llegó a salir)
    this.tecnico = tecnico; // true = el mensaje es el genérico; el real está en `detalle`
    this.detalle = detalle;
  }

  get esConflicto() {
    return this.clasificacion === 'CONFLICT';
  }

  /** "Ref: 3F9A12BC" o null. */
  get codigo() {
    return codigoReferencia(this.referencia);
  }
}

/** Error técnico: mensaje genérico + "Ref: …" en pantalla; el detalle solo en `detalle` (y en la consola en desarrollo). */
export function errorTecnico({ mensaje = MENSAJE_TECNICO, referencia = null, detalle = null, ...resto } = {}) {
  const codigo = codigoReferencia(referencia);
  registrarDetalle(codigo, detalle);
  return new ErrorApi(codigo ? `${mensaje} Ref: ${codigo}` : mensaje, { ...resto, referencia, tecnico: true, detalle });
}

/** Cualquier cosa capturada en un catch → algo que se puede mostrar: un ErrorApi tal cual o, si no, el genérico. */
export function aErrorVisible(error) {
  return error instanceof ErrorApi ? error : errorTecnico({ detalle: String(error?.message ?? error) });
}

/** Solo en desarrollo (npm run dev): el detalle técnico en la consola del navegador. Nunca en el build ni en las pruebas. */
function registrarDetalle(codigo, detalle) {
  const entorno = import.meta.env ?? {};
  if (entorno.DEV && entorno.MODE !== 'test' && detalle) console.warn(`[Ref ${codigo ?? '-'}] ${detalle}`);
}

/** La sesión venció: ya se borró el token y se avisó a la aplicación. */
export class ErrorSesion extends ErrorApi {
  constructor() {
    super(MENSAJE_SESION_VENCIDA, { clasificacion: 'UNAUTHORIZED', status: 401 });
    this.name = 'ErrorSesion';
  }
}

/** Convierte un error GraphQL del gateway en ErrorApi (genérico si es técnico). referencia = la de la petición. */
export function aErrorApi(error, referencia = null) {
  const ext = error?.extensions ?? {};
  const datos = {
    clasificacion: ext.classification ?? 'INTERNAL_ERROR',
    status: ext.status ?? null,
    servicio: ext.servicio ?? null,
    path: error?.path ?? null,
    referencia,
  };
  if (esTecnica(datos.clasificacion)) return errorTecnico({ ...datos, detalle: error?.message ?? null });
  return new ErrorApi(error?.message || MENSAJE_TECNICO, { ...datos, campos: erroresPorCampo(error) });
}

/** extensions.errores → { campo: mensaje } (si un campo trae varios mensajes, se unen). */
export function erroresPorCampo(error) {
  const lista = error?.extensions?.errores;
  if (!Array.isArray(lista)) return {};
  return lista.reduce((acc, { campo, mensaje }) => {
    const clave = campo ?? '_';
    acc[clave] = acc[clave] ? `${acc[clave]} ${mensaje}` : mensaje;
    return acc;
  }, {});
}

/**
 * Errores parciales de una consulta, agrupados por la sección (segundo elemento de path) de la raíz indicada:
 * path ["evento","contrato"] → { contrato: ErrorApi }. Así cada sección de la ficha muestra solo su aviso.
 * Fase 11C: el aviso de una sección es SIEMPRE el genérico (MENSAJE_SECCION + Ref), sea cual sea la causa.
 */
export function erroresPorSeccion(errores, raiz, referencia = null) {
  const resultado = {};
  for (const error of errores ?? []) {
    const path = error?.path;
    if (Array.isArray(path) && path[0] === raiz && path.length >= 2) {
      const seccion = path[1];
      if (!resultado[seccion]) {
        const ext = error?.extensions ?? {};
        resultado[seccion] = errorTecnico({
          mensaje: MENSAJE_SECCION, referencia, detalle: error?.message ?? null, path,
          clasificacion: ext.classification ?? 'INTERNAL_ERROR', status: ext.status ?? null, servicio: ext.servicio ?? null,
        });
      }
    }
  }
  return resultado;
}

/** Un UNAUTHORIZED que no viene del login (servicio auth) significa token ausente, vencido o inválido. */
function esSesionVencida(error) {
  return error?.extensions?.classification === 'UNAUTHORIZED' && error?.extensions?.servicio !== 'auth';
}

/**
 * Ejecuta una operación y devuelve { data, errors, referencia } tal como los envía el gateway (respuestas parciales
 * incluidas); referencia = el X-Request-Id enviado. Lanza ErrorSesion si la sesión venció y un ErrorApi técnico si no hubo
 * respuesta GraphQL (red, CORS, HTTP ≠ 200).
 */
export async function ejecutar(query, variables = {}, { autenticar = true } = {}) {
  const referencia = nuevaReferencia();
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json', [CABECERA_REFERENCIA]: referencia };
  if (autenticar) {
    const token = obtenerToken(); // descarta el token si ya venció
    if (!token) {
      avisarSesionVencida(MENSAJE_SESION_VENCIDA);
      throw new ErrorSesion();
    }
    headers.Authorization = `Bearer ${token}`;
  }

  let respuesta;
  try {
    respuesta = await fetch(`${urlGateway()}/graphql`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ query, variables }),
    });
  } catch (falla) {
    // La petición no llegó al gateway (sin red, gateway caído, CORS): no hay referencia que buscar en su log.
    throw errorTecnico({ clasificacion: 'SERVICE_UNAVAILABLE', detalle: `Sin conexión con el gateway: ${falla?.message ?? falla}` });
  }

  let cuerpo = null;
  try {
    cuerpo = await respuesta.json();
  } catch {
    // sin cuerpo JSON: se trata abajo
  }
  if (!cuerpo || (!cuerpo.data && !cuerpo.errors)) {
    throw errorTecnico({ status: respuesta.status, referencia, detalle: `El gateway respondió HTTP ${respuesta.status} sin respuesta GraphQL` });
  }

  const errors = cuerpo.errors ?? [];
  if (autenticar && errors.some(esSesionVencida)) {
    avisarSesionVencida(MENSAJE_SESION_VENCIDA);
    throw new ErrorSesion();
  }
  return { data: cuerpo.data ?? null, errors, referencia };
}

/** Para mutations: devuelve data o lanza el primer error como ErrorApi (con sus errores por campo, o genérico si es técnico). */
export async function mutar(query, variables = {}, opciones = {}) {
  const { data, errors, referencia } = await ejecutar(query, variables, opciones);
  if (errors.length > 0) throw aErrorApi(errors[0], referencia);
  return data;
}
