import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AccionSimple from '../acciones/AccionSimple.jsx';
import { aErrorApi, aErrorVisible, ejecutar, ErrorSesion, mutar } from '../api/cliente.js';
import { EJECUTAR_RECORDATORIOS, PROXIMOS_EVENTOS } from '../api/consultas.js';
import { useSesion } from '../sesion/SesionContext.jsx';
import { avisarCambios } from '../utils/avisos.js';
import { esNombreDePrueba } from '../utils/ayudas.js';
import { esFechaPasada, hoyEnZona } from '../utils/fechaPasada.js';
import { etiqueta, formatearFecha, TIPOS_EVENTO } from '../utils/formato.js';
import Aviso from './Aviso.jsx';

export const DIAS_PROXIMOS = 7;

/** Días entre hoy y la fecha del evento (ambas "AAAA-MM-DD", sin horas). */
export function diasHasta(fecha, hoy) {
  const aUtc = (iso) => {
    const [a, m, d] = iso.split('-').map(Number);
    return Date.UTC(a, m - 1, d);
  };
  return Math.round((aUtc(fecha) - aUtc(hoy)) / 86_400_000);
}

export function textoDias(dias) {
  if (dias <= 0) return 'Hoy';
  if (dias === 1) return 'Mañana';
  return `En ${dias} días`;
}

/** Mensaje tras ejecutar los recordatorios a mano, con lo que devolvió Eventos. */
export function resumenRecordatorios({ revisados, enviados, yaEnviados }) {
  if (revisados === 0) return 'Recordatorios revisados: no hay eventos reservados en los próximos días.';
  const nuevos = enviados === 1 ? '1 recordatorio enviado' : `${enviados} recordatorios enviados`;
  const antes = yaEnviados === 1 ? '1 ya se había enviado' : `${yaEnviados} ya se habían enviado`;
  return `Recordatorios revisados: ${nuevos}; ${antes} (no se repiten).`;
}

/**
 * Fase 11E: bloque "Próximos 7 días" arriba del listado: los eventos RESERVADOS de hoy a hoy + 7 y, solo para el
 * ADMINISTRADOR, el botón "Ejecutar recordatorios" (la misma revisión que la tarea programada de Eventos; no repite los ya
 * enviados). Para el asistente la mutation respondería FORBIDDEN, por eso el botón ni se muestra.
 */
export default function ProximosEventos({ mostrarPrueba = false }) {
  const { esAdministrador } = useSesion();
  const [estado, setEstado] = useState({ cargando: true, eventos: [], error: null });
  const [exito, setExito] = useState(null);
  // Fase 11I: "hoy" en la zona de la configuración (ZONA_HORARIA), la misma que usan Eventos y el resto del listado
  const hoy = hoyEnZona();

  const cargar = useCallback(async () => {
    try {
      const { data, errors, referencia } = await ejecutar(PROXIMOS_EVENTOS, { desde: hoy, hasta: hoyEnZona(DIAS_PROXIMOS) });
      if (!data?.eventos) setEstado({ cargando: false, eventos: [], error: aErrorApi(errors[0], referencia) });
      else setEstado({ cargando: false, eventos: data.eventos.contenido, error: null });
    } catch (err) {
      if (!(err instanceof ErrorSesion)) setEstado({ cargando: false, eventos: [], error: aErrorVisible(err) });
    }
  }, [hoy]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const alTerminar = useCallback(async (mensaje) => {
    setExito(null);
    await cargar();
    setExito(mensaje);
    avisarCambios(); // Fase 11H: la campanita sube en cuanto llega el recordatorio
  }, [cargar]);

  // Solo eventos futuros (o de hoy): si la página queda abierta pasada la medianoche, uno de ayer no se cuela en el bloque.
  const futuros = estado.eventos.filter((e) => !esFechaPasada(e.fecha, hoy));
  const visibles = mostrarPrueba ? futuros : futuros.filter((e) => !esNombreDePrueba(e.nombreEvento));

  return (
    <section className="proximos tarjeta" aria-labelledby="proximos-titulo">
      <div className="proximos-cabecera">
        <div>
          <h2 id="proximos-titulo">Próximos {DIAS_PROXIMOS} días</h2>
          <p className="texto-suave">Eventos reservados. Los recordatorios al cliente salen solos cada mañana.</p>
        </div>
        {esAdministrador && (
          <AccionSimple etiqueta="Ejecutar recordatorios" textoEnviando="Revisando…" variante="secundario"
            alTerminar={alTerminar}
            operacion={async () => resumenRecordatorios((await mutar(EJECUTAR_RECORDATORIOS)).ejecutarRecordatorios)} />
        )}
      </div>
      <div aria-live="polite">{exito && <Aviso tipo="exito">{exito}</Aviso>}</div>
      {estado.error && <Aviso tipo="error" titulo="No se pudieron cargar los próximos eventos.">{estado.error.message}</Aviso>}
      {estado.cargando && <p className="texto-suave" role="status">Cargando próximos eventos…</p>}
      {!estado.cargando && !estado.error && (visibles.length === 0 ? (
        <p className="vacio">No hay eventos reservados en los próximos {DIAS_PROXIMOS} días.</p>
      ) : (
        <ul className="proximos-lista">
          {visibles.map((e) => (
            <li key={e.id}>
              <strong className="proximos-dias">{textoDias(diasHasta(e.fecha, hoy))}</strong>
              <Link to={`/eventos/${e.id}`}>{e.nombreEvento}</Link>
              <span className="texto-suave">{formatearFecha(e.fecha)} · {etiqueta(TIPOS_EVENTO, e.tipoEvento)}</span>
            </li>
          ))}
        </ul>
      ))}
    </section>
  );
}
