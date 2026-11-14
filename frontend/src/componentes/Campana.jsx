import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { aErrorApi, aErrorVisible, ejecutar, ErrorSesion, mutar } from '../api/cliente.js';
import { MARCAR_NOTIFICACION_LEIDA, MARCAR_TODAS_LEIDAS, MIS_NOTIFICACIONES } from '../api/consultas.js';
import { formatearRecibida, haceCuanto } from '../utils/formato.js';
import { useSinLeer } from './useSinLeer.js';

export const CUANTAS = 10;

/** "9+" con más de 9; vacío con 0 (el número no se muestra). */
export const textoContador = (n) => (n > 9 ? '9+' : n > 0 ? String(n) : '');

const IconoCampana = () => (
  <svg className="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
    strokeLinejoin="round" aria-hidden="true" focusable="false">
    <path d="M6 16.5V11a6 6 0 1 1 12 0v5.5l1.5 2H4.5Z" /><path d="M10 20.5a2 2 0 0 0 4 0" />
  </svg>
);

/**
 * Fase 11H: campanita de notificaciones (los dos roles). El número sale de un polling liviano (useSinLeer); la lista, solo al
 * abrir el panel (las 10 últimas, con el nombre del evento). Al pulsar una: se marca como leída y abre la ficha del evento.
 * Teclado: Enter/Espacio abre (es un botón), Escape cierra y el foco vuelve a la campana. Las no leídas dicen "Nueva".
 */
export default function Campana() {
  const { sinLeer, setSinLeer } = useSinLeer();
  const [abierto, setAbierto] = useState(false);
  const [estado, setEstado] = useState({ cargando: false, lista: [], error: null });
  const [errorAccion, setErrorAccion] = useState(null);
  const boton = useRef(null);
  const panel = useRef(null);
  const titulo = useRef(null);
  const navegar = useNavigate();
  const idPanel = useId();
  const idTitulo = useId();

  // Cada acción (marcar una o todas) sube la versión: una respuesta pedida ANTES de la acción ya no pisa el número ni la lista
  // (sin esto, al reabrir el panel y pulsar enseguida, la respuesta vieja devolvía el número anterior).
  const version = useRef(0);

  const cargar = useCallback(async () => {
    const pedida = version.current;
    // La lista se vacía al abrir: no se puede pulsar una lista vieja mientras llega la nueva
    setEstado({ cargando: true, lista: [], error: null });
    try {
      const { data, errors, referencia } = await ejecutar(MIS_NOTIFICACIONES, { size: CUANTAS });
      if (pedida !== version.current) return;
      if (!data?.misNotificaciones) {
        setEstado({ cargando: false, lista: [], error: aErrorApi(errors[0], referencia) });
        return;
      }
      if (typeof data.notificacionesSinLeer === 'number') setSinLeer(data.notificacionesSinLeer);
      setEstado({ cargando: false, lista: data.misNotificaciones.contenido, error: null });
    } catch (err) {
      if (!(err instanceof ErrorSesion)) setEstado({ cargando: false, lista: [], error: aErrorVisible(err) });
    }
  }, [setSinLeer]);

  const cerrar = useCallback((devolverFoco = true) => {
    setAbierto(false);
    setErrorAccion(null);
    if (devolverFoco) boton.current?.focus();
  }, []);

  const alternar = () => {
    if (abierto) { cerrar(); return; }
    setAbierto(true);
    cargar();
  };

  // Abierto: el foco pasa al título del panel; Escape o un clic fuera lo cierran.
  useEffect(() => {
    if (!abierto) return undefined;
    titulo.current?.focus();
    const alTeclear = (e) => { if (e.key === 'Escape') { e.preventDefault(); cerrar(); } };
    const alPulsarFuera = (e) => {
      if (!panel.current?.contains(e.target) && !boton.current?.contains(e.target)) cerrar(false);
    };
    document.addEventListener('keydown', alTeclear);
    document.addEventListener('mousedown', alPulsarFuera);
    return () => {
      document.removeEventListener('keydown', alTeclear);
      document.removeEventListener('mousedown', alPulsarFuera);
    };
  }, [abierto, cerrar]);

  async function abrirNotificacion(n) {
    setErrorAccion(null);
    try {
      if (!n.leida) {
        version.current += 1;
        await mutar(MARCAR_NOTIFICACION_LEIDA, { id: n.id });
        setSinLeer((s) => Math.max(0, s - 1));
        setEstado((e) => ({ ...e, lista: e.lista.map((x) => (x.id === n.id ? { ...x, leida: true } : x)) }));
      }
      cerrar(false);
      navegar(`/eventos/${n.eventoId}`);
    } catch (err) {
      if (!(err instanceof ErrorSesion)) setErrorAccion(aErrorVisible(err));
    }
  }

  async function marcarTodas() {
    setErrorAccion(null);
    try {
      version.current += 1;
      await mutar(MARCAR_TODAS_LEIDAS);
      setSinLeer(0);
      setEstado((e) => ({ ...e, lista: e.lista.map((x) => ({ ...x, leida: true })) }));
    } catch (err) {
      if (!(err instanceof ErrorSesion)) setErrorAccion(aErrorVisible(err));
    }
  }

  const contador = textoContador(sinLeer);
  const ahora = new Date();
  const hayNoLeidas = sinLeer > 0 || estado.lista.some((n) => !n.leida);

  return (
    <div className="campana">
      <button type="button" ref={boton} className="campana-boton" onClick={alternar}
        aria-label={`Notificaciones, ${sinLeer} sin leer`} aria-expanded={abierto} aria-controls={idPanel}>
        <IconoCampana />
        {contador && <span className="campana-contador" aria-hidden="true">{contador}</span>}
      </button>

      {abierto && (
        <div id={idPanel} ref={panel} className="campana-panel" role="dialog" aria-labelledby={idTitulo}>
          <div className="campana-cabecera">
            <h2 id={idTitulo} ref={titulo} tabIndex={-1}>Notificaciones</h2>
            <button type="button" className="boton-enlace" onClick={marcarTodas} disabled={!hayNoLeidas}>
              Marcar todas como leídas
            </button>
          </div>
          {errorAccion && <p className="campana-error" role="alert">{errorAccion.message}</p>}
          {estado.cargando && estado.lista.length === 0 && <p className="texto-suave" role="status">Cargando notificaciones…</p>}
          {estado.error && <p className="campana-error" role="alert">{estado.error.message}</p>}
          {!estado.cargando && !estado.error && estado.lista.length === 0 && (
            <p className="vacio">No tienes notificaciones.</p>
          )}
          {estado.lista.length > 0 && (
            <ul className="campana-lista">
              {estado.lista.map((n) => {
                const hace = haceCuanto(n.fechaCreacion, ahora);
                return (
                  <li key={n.id}>
                    <button type="button" className={n.leida ? 'campana-item' : 'campana-item campana-nueva'}
                      onClick={() => abrirNotificacion(n)}>
                      <span className="campana-estado">
                        {n.leida ? 'Leída' : <><span className="campana-punto" aria-hidden="true">●</span> Nueva</>}
                      </span>
                      <strong className="campana-asunto">{n.asunto}</strong>
                      <span className="campana-mensaje">{n.mensaje}</span>
                      <span className="campana-detalle">
                        <span>{n.evento?.nombreEvento ?? 'Evento'}</span>
                        <time dateTime={n.fechaCreacion}>{formatearRecibida(n.fechaCreacion, ahora)}{hace ? ` · ${hace}` : ''}</time>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
