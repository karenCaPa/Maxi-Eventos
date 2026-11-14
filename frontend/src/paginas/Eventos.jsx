import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { aErrorApi, aErrorVisible, ejecutar, ErrorSesion } from '../api/cliente.js';
import { EVENTOS, EVENTOS_PASADOS } from '../api/consultas.js';
import Aviso from '../componentes/Aviso.jsx';
import Campo from '../componentes/Campo.jsx';
import { InsigniaEstado } from '../componentes/Insignia.jsx';
import Paginacion from '../componentes/Paginacion.jsx';
import FechaFicha from '../componentes/FechaFicha.jsx';
import ProximosEventos from '../componentes/ProximosEventos.jsx';
import { esNombreDePrueba } from '../utils/ayudas.js';
import { estadoVisible, hoyEnZona } from '../utils/fechaPasada.js';
import { ESTADOS_EVENTO, etiqueta, fechaSinHora, formatearNumero, primeraMayuscula, TIPOS_EVENTO, ZONAS } from '../utils/formato.js';

const nombreMes = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric', timeZone: 'UTC' });

/** Agrupa los eventos (ya ordenados por fecha) por mes, para leer la agenda de corrido. */
function agruparPorMes(eventos) {
  const grupos = [];
  for (const e of eventos) {
    const titulo = primeraMayuscula(nombreMes.format(fechaSinHora(e.fecha)));
    const ultimo = grupos[grupos.length - 1];
    if (ultimo?.titulo === titulo) ultimo.eventos.push(e);
    else grupos.push({ titulo, eventos: [e] });
  }
  return grupos;
}

const TAMANO_PAGINA = 20;
/** Los scripts de verificación y Postman crean eventos "Prueba …"; se ocultan solo en la interfaz. */
const esEventoDePrueba = (evento) => esNombreDePrueba(evento.nombreEvento);
const PAGINA_VACIA = { pagina: 0, tamanoPagina: TAMANO_PAGINA, totalElementos: 0, totalPaginas: 0, contenido: [] };

/** Una fila de la agenda. Fase 11I: con la fecha pasada, la ficha de fecha y la insignia se ven en gris (Realizado / Vencido). */
function FilaEvento({ evento: e, hoy }) {
  return (
    <li className="evento-fila">
      <FechaFicha fecha={e.fecha} estado={estadoVisible(e.estado, e.fecha, hoy)} />
      <div>
        <Link to={`/eventos/${e.id}`} className="evento-nombre">{e.nombreEvento}</Link>
        <p className="evento-datos">
          <span>{e.cliente?.nombreCompleto ?? '—'}</span>
          <span>{etiqueta(TIPOS_EVENTO, e.tipoEvento)}</span>
          <span>{formatearNumero(e.numInvitados)} invitados</span>
          <span>{etiqueta(ZONAS, e.zona)}</span>
        </p>
      </div>
      <InsigniaEstado estado={e.estado} fecha={e.fecha} hoy={hoy} />
    </li>
  );
}

function Agenda({ eventos, hoy }) {
  return agruparPorMes(eventos).map((grupo) => (
    <section key={grupo.titulo} className="agenda-mes" aria-label={grupo.titulo}>
      <h2>{grupo.titulo}</h2>
      <ul className="agenda">
        {grupo.eventos.map((e) => <FilaEvento key={e.id} evento={e} hoy={hoy} />)}
      </ul>
    </section>
  ));
}

/**
 * Fase 11I: el rango de cada lista a partir de los filtros. Los próximos van de hoy en adelante (o desde el filtro, si es
 * posterior); los pasados, hasta ayer (o hasta el filtro, si es anterior). Si los filtros dejan una lista sin rango, no se pide.
 */
export function rangos(filtros, hoy, ayer) {
  const desdeProximos = filtros.desde && filtros.desde > hoy ? filtros.desde : hoy;
  const hastaPasados = filtros.hasta && filtros.hasta < ayer ? filtros.hasta : ayer;
  return {
    proximos: !filtros.hasta || filtros.hasta >= desdeProximos ? { desde: desdeProximos, hasta: filtros.hasta || null } : null,
    pasados: !filtros.desde || filtros.desde <= hastaPasados ? { desde: filtros.desde || null, hasta: hastaPasados } : null,
  };
}

/** Respuesta de una consulta de eventos → { resultado, error } (los errores de sesión no se muestran: la app vuelve al login). */
function consultar(documento, variables) {
  return ejecutar(documento, variables)
    .then(({ data, errors, referencia }) => (data?.eventos
      ? { resultado: data.eventos, error: null }
      : { resultado: null, error: aErrorApi(errors[0], referencia) }))
    .catch((err) => (err instanceof ErrorSesion ? null : { resultado: null, error: aErrorVisible(err) }));
}

export default function Eventos() {
  const [params, setParams] = useSearchParams();
  const filtros = {
    estado: params.get('estado') || '',
    tipo: params.get('tipo') || '',
    desde: params.get('desde') || '',
    hasta: params.get('hasta') || '',
  };
  const pagina = Math.max(0, Number(params.get('pagina') || 0));
  const mostrarPrueba = params.get('prueba') === '1';

  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState(null);
  const [cargando, setCargando] = useState(true);
  // Fase 11I: "Eventos pasados", con su propia página (no va en la URL: vuelve a la primera al cambiar los filtros)
  const [pasados, setPasados] = useState({ resultado: null, error: null, cargando: true });
  const clave = params.toString();
  const [elegidaPasados, setElegidaPasados] = useState({ clave, pagina: 0 });
  const paginaPasados = elegidaPasados.clave === clave ? elegidaPasados.pagina : 0;

  // "Hoy" en la zona de la configuración (ZONA_HORARIA). Se recalcula en cada render: si el listado queda abierto pasada la
  // medianoche, la próxima consulta ya usa el día nuevo.
  const hoy = hoyEnZona();
  const ayer = hoyEnZona(-1);
  const rango = rangos(filtros, hoy, ayer);

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError(null);
    if (!rango.proximos) {
      // los filtros piden solo fechas pasadas: no hay próximos que consultar
      setResultado(PAGINA_VACIA);
      setCargando(false);
      return undefined;
    }
    const variables = {
      estado: filtros.estado || null,
      tipo: filtros.tipo || null,
      desde: rango.proximos.desde,
      hasta: rango.proximos.hasta,
      page: pagina,
      size: TAMANO_PAGINA,
      sort: 'fecha,asc',
    };
    consultar(EVENTOS, variables).then((r) => {
      if (!vigente || !r) return;
      setResultado(r.resultado);
      setError(r.error);
    }).finally(() => vigente && setCargando(false));
    return () => {
      vigente = false;
    };
  }, [clave, hoy]); // los filtros salen de la URL: "clave" cambia cuando cambia cualquiera de ellos

  useEffect(() => {
    let vigente = true;
    if (!rango.pasados) {
      // los filtros solo piden fechas futuras: no hay pasados que consultar (la sección dice que está vacía)
      setPasados({ resultado: null, error: null, cargando: false });
      return undefined;
    }
    // mientras llega la respuesta se conserva la anterior (sin parpadeo); la primera vez dice "Cargando…"
    setPasados((previo) => ({ ...previo, cargando: true }));
    const variables = {
      estado: filtros.estado || null,
      tipo: filtros.tipo || null,
      desde: rango.pasados.desde,
      hasta: rango.pasados.hasta,
      page: paginaPasados,
      size: TAMANO_PAGINA,
    };
    consultar(EVENTOS_PASADOS, variables).then((r) => vigente && r && setPasados({ ...r, cargando: false }));
    return () => {
      vigente = false;
    };
  }, [clave, hoy, paginaPasados]);

  function cambiar(nuevos) {
    const siguiente = new URLSearchParams(params);
    for (const [k, v] of Object.entries(nuevos)) {
      if (v === '' || v === null || v === false) siguiente.delete(k);
      else siguiente.set(k, v === true ? '1' : String(v));
    }
    setParams(siguiente);
  }

  const todos = resultado?.contenido ?? [];
  const visibles = mostrarPrueba ? todos : todos.filter((e) => !esEventoDePrueba(e));
  const ocultos = todos.length - visibles.length;

  return (
    <>
      <div className="titulo-pagina">
        <div>
          <h1>Eventos</h1>
          <p className="subtitulo">Tu agenda ordenada por fecha. Abre un evento para ver su ficha completa.</p>
        </div>
      </div>

      <ProximosEventos mostrarPrueba={mostrarPrueba} />

      <form className="filtros tarjeta" onSubmit={(e) => e.preventDefault()} aria-label="Filtros de eventos">
        <Campo etiqueta="Estado">
          {({ id }) => (
            <select id={id} value={filtros.estado} onChange={(e) => cambiar({ estado: e.target.value, pagina: '' })}>
              <option value="">Todos</option>
              {Object.entries(ESTADOS_EVENTO).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
            </select>
          )}
        </Campo>
        <Campo etiqueta="Tipo">
          {({ id }) => (
            <select id={id} value={filtros.tipo} onChange={(e) => cambiar({ tipo: e.target.value, pagina: '' })}>
              <option value="">Todos</option>
              {Object.entries(TIPOS_EVENTO).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
            </select>
          )}
        </Campo>
        <Campo etiqueta="Desde">
          {({ id }) => (
            <input id={id} type="date" value={filtros.desde} onChange={(e) => cambiar({ desde: e.target.value, pagina: '' })} />
          )}
        </Campo>
        <Campo etiqueta="Hasta">
          {({ id }) => (
            <input id={id} type="date" value={filtros.hasta} onChange={(e) => cambiar({ hasta: e.target.value, pagina: '' })} />
          )}
        </Campo>
        <div className="campo campo-casilla">
          <input id="mostrar-prueba" type="checkbox" checked={mostrarPrueba} onChange={(e) => cambiar({ prueba: e.target.checked })} />
          <label htmlFor="mostrar-prueba">Mostrar eventos de prueba</label>
        </div>
        <button type="button" className="boton boton-secundario"
          onClick={() => setParams(new URLSearchParams())} disabled={clave === ''}>
          Limpiar filtros
        </button>
      </form>

      {error && <Aviso tipo="error" titulo="No se pudo cargar el listado.">{error.message}</Aviso>}

      {cargando && !resultado && <p className="texto-suave" role="status">Cargando eventos…</p>}

      {resultado && (
        <div className={cargando ? 'cargando' : undefined} aria-busy={cargando}>
          <p className="texto-suave" role="status">
            {formatearNumero(resultado.totalElementos)} eventos en total de hoy en adelante
            {ocultos > 0 && ` · ${ocultos} de prueba ocultos en esta página`}
          </p>
          {visibles.length === 0 ? (
            <div className="vacio-grande">
              <p className="vacio">No hay eventos próximos con estos filtros.</p>
              <p className="texto-suave">Prueba con otras fechas o limpia los filtros.</p>
            </div>
          ) : (
            <Agenda eventos={visibles} hoy={hoy} />
          )}
          <Paginacion pagina={resultado.pagina} totalPaginas={resultado.totalPaginas} alCambiar={(p) => cambiar({ pagina: p || '' })} />
        </div>
      )}

      <EventosPasados {...pasados} hoy={hoy} mostrarPrueba={mostrarPrueba}
        hayFiltros={Boolean(filtros.estado || filtros.tipo || filtros.desde || filtros.hasta)}
        alCambiarPagina={(p) => setElegidaPasados({ clave, pagina: p })} />
    </>
  );
}

/**
 * Fase 11I: los eventos cuya fecha ya pasó, al final y plegados por defecto (<details>), con su número y en gris.
 * El más reciente primero. SIEMPRE se muestra (ajuste del 2026-11-10): vacía, explica que los eventos llegarán solos cuando
 * pase su fecha (o, con filtros, que no hay ninguno con esos filtros).
 */
function EventosPasados({ resultado, error, cargando, hoy, mostrarPrueba, hayFiltros, alCambiarPagina }) {
  // Un solo <details> en todos los estados: si la usuaria lo abre mientras carga, sigue abierto al llegar los datos.
  // El número va en el título cuando ya se sabe (sin consulta —filtros solo hacia el futuro— es 0).
  const primeraCarga = cargando && !resultado && !error;
  const total = primeraCarga ? null : resultado?.totalElementos ?? (error ? null : 0);
  const todos = resultado?.contenido ?? [];
  const visibles = mostrarPrueba ? todos : todos.filter((e) => !esEventoDePrueba(e));
  const ocultos = todos.length - visibles.length;
  return (
    <details className="eventos-pasados tarjeta">
      <summary>Eventos pasados{total !== null && ` (${formatearNumero(total)})`}</summary>
      {primeraCarga ? (
        <p className="texto-suave" role="status">Cargando eventos pasados…</p>
      ) : error ? (
        <Aviso tipo="error" titulo="No se pudieron cargar los eventos pasados.">{error.message}</Aviso>
      ) : total === 0 ? (
        <p className="vacio">
          {hayFiltros
            ? 'No hay eventos pasados con estos filtros.'
            : 'Todavía no hay eventos pasados. Los eventos aparecerán aquí automáticamente cuando pase su fecha.'}
        </p>
      ) : (
        <>
          <p className="texto-suave">
            Su fecha ya pasó; el más reciente primero. Un evento reservado se ve «Realizado» y uno que nunca se confirmó, «Vencido».
            {ocultos > 0 && ` ${ocultos} de prueba ocultos en esta página.`}
          </p>
          {visibles.length > 0 && <Agenda eventos={visibles} hoy={hoy} />}
          <Paginacion etiqueta="Paginación de eventos pasados" pagina={resultado.pagina} totalPaginas={resultado.totalPaginas}
            alCambiar={alCambiarPagina} />
        </>
      )}
    </details>
  );
}
