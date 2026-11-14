// Descarga de los PDF por las rutas REST de solo lectura del gateway (/documentos, ver docs/graphql.md).
// El token va en el header (nunca en la URL). Accept pide PDF o JSON: el JSON es el ErrorResponse de un error (404, 409, 503).
import { urlGateway } from '../config.js';
import { avisarSesionVencida, obtenerToken } from '../sesion/almacen.js';
import {
  CABECERA_REFERENCIA, ErrorApi, errorTecnico, esTecnica, ErrorSesion, MENSAJE_SESION_VENCIDA, nuevaReferencia,
} from './cliente.js';

const CLASIFICACION = { 400: 'BAD_REQUEST', 401: 'UNAUTHORIZED', 403: 'FORBIDDEN', 404: 'NOT_FOUND', 409: 'CONFLICT', 502: 'SERVICE_UNAVAILABLE', 503: 'SERVICE_UNAVAILABLE' };

/**
 * documento: "contrato" | "anexo". Descarga el archivo en el navegador o lanza ErrorApi: con el mensaje del servicio si es
 * de negocio (400, 403, 404, 409) o el genérico con "Ref: …" si es técnico (5xx, sin conexión, respuesta inesperada).
 */
export async function descargarDocumento(eventoId, documento) {
  const token = obtenerToken();
  if (!token) {
    avisarSesionVencida(MENSAJE_SESION_VENCIDA);
    throw new ErrorSesion();
  }
  const referencia = nuevaReferencia();
  let respuesta;
  try {
    respuesta = await fetch(`${urlGateway()}/documentos/eventos/${encodeURIComponent(eventoId)}/${documento}.pdf`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/pdf, application/json', [CABECERA_REFERENCIA]: referencia },
    });
  } catch (falla) {
    throw errorTecnico({ clasificacion: 'SERVICE_UNAVAILABLE', detalle: `Sin conexión con el gateway: ${falla?.message ?? falla}` });
  }

  if (respuesta.status === 401) {
    avisarSesionVencida(MENSAJE_SESION_VENCIDA);
    throw new ErrorSesion();
  }
  if (!respuesta.ok) {
    const clasificacion = CLASIFICACION[respuesta.status] ?? 'INTERNAL_ERROR';
    let mensaje = null;
    try {
      mensaje = (await respuesta.json())?.message ?? null;
    } catch {
      // sin cuerpo JSON
    }
    const datos = { status: respuesta.status, clasificacion, servicio: 'contratos', referencia };
    if (esTecnica(clasificacion) || !mensaje) {
      throw errorTecnico({ ...datos, detalle: `Descarga ${documento}: HTTP ${respuesta.status} ${mensaje ?? ''}`.trim() });
    }
    throw new ErrorApi(mensaje, datos);
  }

  const archivo = await respuesta.blob();
  const nombre = nombreArchivo(respuesta.headers.get('Content-Disposition')) ?? `${documento}-${eventoId}.pdf`;
  const url = URL.createObjectURL(archivo);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return nombre;
}

/** attachment; filename="contrato-….pdf" → contrato-….pdf */
export function nombreArchivo(contentDisposition) {
  const coincide = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(contentDisposition ?? '');
  return coincide ? decodeURIComponent(coincide[1]) : null;
}
