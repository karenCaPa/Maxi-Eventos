import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { aErrorApi, aErrorVisible, ejecutar, ErrorSesion, errorTecnico } from '../api/cliente.js';
import { FECHAS_DISPONIBLES } from '../api/consultas.js';
import { hoyIso } from '../utils/ayudas.js';
import { formatearFecha, primeraMayuscula } from '../utils/formato.js';
import { DIAS_PERMITIDOS } from '../utils/validacionEvento.js';

/** Igual que FechaService.DIAS_MAXIMO de eventos-service: no se ofrecen fechas más allá de hoy + 366 días. */
export const DIAS_MAXIMO = 366;
const SEMANA = [
  ['Lun', 'lunes'], ['Mar', 'martes'], ['Mié', 'miércoles'], ['Jue', 'jueves'], ['Vie', 'viernes'], ['Sáb', 'sábado'], ['Dom', 'domingo'],
];
const nombreMes = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric', timeZone: 'UTC' });

const ESTADOS = {
  disponible: 'disponible',
  'no-disponible': 'no disponible',
  'no-laborable': 'no laborable',
  cargando: 'cargando',
};

// ---- utilidades de fecha (fechas sin hora en UTC, para que la zona horaria no cambie el día)
const aUtc = (iso) => { const [a, m, d] = iso.split('-').map(Number); return new Date(Date.UTC(a, m - 1, d)); };
const aIso = (fecha) => fecha.toISOString().slice(0, 10);
const claveMes = (iso) => iso.slice(0, 7); // "2026-11"
function sumarMeses(clave, n) {
  const [a, m] = clave.split('-').map(Number);
  const f = new Date(Date.UTC(a, m - 1 + n, 1));
  return aIso(f).slice(0, 7);
}
function diasDelMes(clave) {
  const [a, m] = clave.split('-').map(Number);
  const total = new Date(Date.UTC(a, m, 0)).getUTCDate();
  return Array.from({ length: total }, (_, i) => `${clave}-${String(i + 1).padStart(2, '0')}`);
}
/** "sábado, 7 de noviembre de 2026" (en minúscula, para frases y aria-label). */
export function fechaEnTexto(iso) {
  const t = formatearFecha(iso, { larga: true });
  return t.charAt(0).toLocaleLowerCase('es-CO') + t.slice(1);
}

/**
 * CALENDARIO MENSUAL de fechas libres (10B3e). Solo se pueden elegir los días que devuelve fechasDisponibles para ese mes;
 * el resto queda deshabilitado: "no laborable" (días que no están en DIAS_PERMITIDOS: lunes a jueves) y "no disponible"
 * (hoy, días pasados, fines de semana ocupados o bloqueados por la regla de un evento por fin de semana y días fuera del rango
 * de 366 días). Cada mes se pide UNA vez (fechasDisponibles(desde: día 1, hasta: último día)) y queda en memoria.
 * Semana de lunes a domingo; cada día es un <button> con aria-label = fecha completa + estado (Tab y Enter).
 *
 * Props: valor ("2026-11-07" o ""), alElegir(fecha), version (al cambiar se descarta lo cargado y se recarga el mes visible:
 * p. ej. cuando la fecha elegida dejó de estar libre), hoy (ISO; por defecto el día local), describedBy (error del campo).
 */
export default function CalendarioFechas({ valor, alElegir, version = 0, hoy = hoyIso(), describedBy, invalido = false }) {
  const maximo = aIso(new Date(aUtc(hoy).getTime() + DIAS_MAXIMO * 86400000));
  const mesMinimo = claveMes(hoy);
  const mesMaximo = claveMes(maximo);
  const [mes, setMes] = useState(() => {
    const inicial = valor ? claveMes(valor) : mesMinimo;
    return inicial < mesMinimo || inicial > mesMaximo ? mesMinimo : inicial;
  });

  // Meses cargados: clave → { estado: 'cargando' | 'listo' | 'error', fechas: Set, error, ticket }
  const cache = useRef(new Map());
  const [, redibujar] = useReducer((x) => x + 1, 0);

  const cargarMes = useCallback((clave) => {
    if (cache.current.has(clave)) return; // una sola petición por mes (también con StrictMode)
    const ticket = {};
    cache.current.set(clave, { estado: 'cargando', ticket });
    redibujar();
    const dias = diasDelMes(clave);
    const guardar = (entrada) => {
      if (cache.current.get(clave)?.ticket !== ticket) return; // se descartó mientras tanto (recarga)
      cache.current.set(clave, { ...entrada, ticket });
      redibujar();
    };
    ejecutar(FECHAS_DISPONIBLES, { desde: dias[0], hasta: dias[dias.length - 1] })
      .then(({ data, errors, referencia }) => {
        if (!data?.fechasDisponibles) {
          guardar({ estado: 'error', error: errors?.length ? aErrorApi(errors[0], referencia) : errorTecnico({ referencia, detalle: 'fechasDisponibles sin datos' }) });
        } else {
          guardar({ estado: 'listo', fechas: new Set(data.fechasDisponibles.map((f) => f.fecha)) });
        }
      })
      .catch((err) => {
        if (err instanceof ErrorSesion) return; // la aplicación vuelve al login
        guardar({ estado: 'error', error: aErrorVisible(err) });
      });
  }, []);

  useEffect(() => { cargarMes(mes); }, [mes, cargarMes]);

  // Recarga pedida desde fuera (la fecha dejó de estar libre, un 409…): se descarta todo y se pide de nuevo el mes visible.
  const versionPrevia = useRef(version);
  useEffect(() => {
    if (versionPrevia.current === version) return;
    versionPrevia.current = version;
    cache.current.clear();
    cargarMes(mes);
  }, [version, mes, cargarMes]);

  const reintentar = () => {
    cache.current.delete(mes);
    cargarMes(mes);
  };

  const entrada = cache.current.get(mes) ?? { estado: 'cargando' };
  function estadoDe(iso) {
    if (iso <= hoy || iso > maximo) return 'no-disponible'; // hoy, pasado o fuera del rango de 366 días
    if (entrada.estado === 'listo' && entrada.fechas.has(iso)) return 'disponible'; // dentro del rango, lo que dice Eventos manda
    if (!DIAS_PERMITIDOS.includes(aUtc(iso).getUTCDay())) return 'no-laborable';
    return entrada.estado === 'cargando' ? 'cargando' : 'no-disponible'; // ocupado o bloqueado (o sin datos por un error)
  }

  const dias = diasDelMes(mes);
  const huecos = (aUtc(dias[0]).getUTCDay() + 6) % 7; // semana desde el lunes
  const idTitulo = `calendario-titulo-${mes}`;
  const libres = entrada.estado === 'listo' ? dias.filter((d) => estadoDe(d) === 'disponible').length : null;

  return (
    <div className={`calendario${invalido ? ' calendario-invalido' : ''}`} role="group" aria-labelledby={idTitulo}
      aria-describedby={describedBy}>
      <div className="calendario-cabecera">
        <button type="button" className="calendario-flecha" onClick={() => setMes((m) => sumarMeses(m, -1))}
          disabled={mes <= mesMinimo} aria-label="Mes anterior">‹</button>
        <h3 id={idTitulo} className="calendario-titulo" aria-live="polite">{primeraMayuscula(nombreMes.format(aUtc(dias[0])))}</h3>
        <button type="button" className="calendario-flecha" onClick={() => setMes((m) => sumarMeses(m, 1))}
          disabled={mes >= mesMaximo} aria-label="Mes siguiente">›</button>
      </div>

      <div className="calendario-estado" aria-live="polite">
        {entrada.estado === 'cargando' && <p className="texto-suave" role="status">Cargando las fechas del mes…</p>}
        {entrada.estado === 'listo' && <p className="texto-suave">{libres === 1 ? '1 fecha libre' : `${libres} fechas libres`} este mes.</p>}
        {entrada.estado === 'error' && (
          <div className="aviso aviso-error calendario-error" role="alert">
            <span>No se pudieron cargar las fechas de este mes. {entrada.error?.message}</span>
            <button type="button" className="boton boton-secundario" onClick={reintentar}>Reintentar</button>
          </div>
        )}
      </div>

      <div className="calendario-semana" aria-hidden="true">
        {SEMANA.map(([corto, largo]) => <abbr key={corto} title={largo}>{corto}</abbr>)}
      </div>
      <div className="calendario-dias">
        {Array.from({ length: huecos }, (_, i) => <span key={`h${i}`} className="dia-hueco" aria-hidden="true" />)}
        {dias.map((iso) => {
          const estado = estadoDe(iso);
          const elegido = iso === valor;
          return (
            <button key={iso} type="button" className={`dia dia-${estado}${elegido ? ' dia-elegido' : ''}`}
              disabled={estado !== 'disponible'} aria-pressed={elegido}
              aria-label={`${fechaEnTexto(iso)}, ${elegido ? 'seleccionada' : ESTADOS[estado]}`}
              onClick={() => alElegir(iso)}>
              {Number(iso.slice(8))}
            </button>
          );
        })}
      </div>

      <ul className="calendario-leyenda" aria-label="Leyenda del calendario">
        <li><span className="muestra dia-disponible" aria-hidden="true" /> Disponible</li>
        <li><span className="muestra dia-no-disponible" aria-hidden="true" /> No disponible</li>
        <li><span className="muestra dia-no-laborable" aria-hidden="true" /> No laborable</li>
        <li><span className="muestra dia-elegido" aria-hidden="true" /> Seleccionada</li>
      </ul>

      <p className="calendario-seleccion" aria-live="polite">
        {valor ? <>Fecha seleccionada: <strong>{fechaEnTexto(valor)}</strong></> : 'Aún no has elegido una fecha.'}
      </p>
    </div>
  );
}
