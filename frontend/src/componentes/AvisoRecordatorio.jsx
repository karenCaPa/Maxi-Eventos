import Aviso from './Aviso.jsx';
import { formatearFechaHora } from '../utils/formato.js';

/**
 * Fase 11E: el recordatorio EVENTO_PROXIMO más reciente de las notificaciones del evento (null si no hay). El número de
 * días sale del asunto que redacta Notificaciones ("Faltan 7 días para su evento: …" / "Falta 1 día para su evento: …").
 */
export function ultimoRecordatorio(notificaciones) {
  const recordatorios = (notificaciones ?? []).filter((n) => n.tipo === 'EVENTO_PROXIMO');
  if (recordatorios.length === 0) return null;
  const ultimo = recordatorios.reduce((a, b) => (String(b.fechaCreacion ?? '') > String(a.fechaCreacion ?? '') ? b : a));
  const dias = /^Faltan? (\d+) d[ií]as?/i.exec(ultimo.asunto ?? '');
  const faltan = dias ? (dias[1] === '1' ? 'falta 1 día' : `faltan ${dias[1]} días`) : null;
  return { ...ultimo, faltan };
}

/** Aviso fijo de la ficha (se ve en todos los pasos de la guía) cuando ya salió un recordatorio del evento. */
export default function AvisoRecordatorio({ notificaciones }) {
  const recordatorio = ultimoRecordatorio(notificaciones);
  if (!recordatorio) return null;
  return (
    <Aviso tipo="info" titulo={recordatorio.faltan ? `Recordatorio enviado: ${recordatorio.faltan}` : 'Recordatorio enviado'}>
      Al cliente le llegó «{recordatorio.asunto}»
      {recordatorio.fechaCreacion ? ` el ${formatearFechaHora(recordatorio.fechaCreacion)}` : ''}.
    </Aviso>
  );
}
