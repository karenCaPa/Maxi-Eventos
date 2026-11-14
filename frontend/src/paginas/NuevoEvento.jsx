import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import BotonAccion from '../acciones/BotonAccion.jsx';
import ErrorAccion from '../acciones/ErrorAccion.jsx';
import { aErrorApi, aErrorVisible, ejecutar, ErrorApi, ErrorSesion, errorTecnico, mutar } from '../api/cliente.js';
import { CLIENTES, CREAR_CLIENTE, CREAR_EVENTO, FECHAS_DISPONIBLES, LUGARES } from '../api/consultas.js';
import { useConsulta } from '../api/useConsulta.js';
import Aviso from '../componentes/Aviso.jsx';
import CalendarioFechas from '../componentes/CalendarioFechas.jsx';
import Campo from '../componentes/Campo.jsx';
import { hoyIso, numeroONulo } from '../utils/ayudas.js';
import { etiqueta, formatearFecha, formatearNumero, TIPOS_EVENTO, TIPOS_LUGAR, ZONAS } from '../utils/formato.js';
import { MENSAJE_FECHA_OCUPADA, validarEvento } from '../utils/validacionEvento.js';

const CLIENTE_VACIO = { nombreCompleto: '', telefono: '', correo: '', direccion: '' };
/** Pasos del asistente y los campos que valida cada uno. */
const PASOS_FORMULARIO = ['Cliente', 'Fecha', 'Datos del evento', 'Revisar'];
const CAMPOS_PASO = [
  ['clienteId', 'nombreCompleto', 'telefono', 'correo', 'direccion'],
  ['fecha'],
  ['nombreEvento', 'tipoEvento', 'cantidadAdultos', 'cantidadNinos', 'zona', 'lugarId'],
];
const pasoDelCampo = (campo) => Math.max(0, CAMPOS_PASO.findIndex((campos) => campos.includes(campo)));
const soloDelPaso = (errores, paso) => Object.fromEntries(Object.entries(errores).filter(([campo]) => CAMPOS_PASO[paso]?.includes(campo)));
const EVENTO_VACIO = { nombreEvento: '', fecha: '', tipoEvento: '', cantidadAdultos: '', cantidadNinos: '', zona: '', lugarId: '' };

/**
 * Nuevo evento: cliente existente (query clientes) o nuevo (crearCliente), fecha elegida en el CALENDARIO mensual (10B3e:
 * solo se pueden seleccionar las fechas libres que devuelve fechasDisponibles) y los demás datos.
 * 10B3d: ANTES de crear un cliente nuevo se validan todos los datos del evento (validacionEvento.js: nombre, fecha posterior
 * a hoy y en día permitido, cantidades, capacidad del lugar) y se comprueba con fechasDisponibles que la fecha siga libre;
 * así un evento que no se puede crear no deja un cliente creado. El servicio sigue decidiendo: sus errores por campo
 * (extensions.errores) se muestran junto a cada campo y sus 409 con su mensaje.
 */
export default function NuevoEvento() {
  const navegar = useNavigate();
  const enCurso = useRef(false);

  const [modo, setModo] = useState('existente');
  const [busqueda, setBusqueda] = useState('');
  const [nombreBuscado, setNombreBuscado] = useState('');
  const [clienteId, setClienteId] = useState('');
  const [clienteCreado, setClienteCreado] = useState(null);
  const [cliente, setCliente] = useState(CLIENTE_VACIO);
  const [evento, setEvento] = useState(EVENTO_VACIO);
  const [paso, setPaso] = useState(0); // 0 Cliente · 1 Fecha · 2 Datos del evento · 3 Revisar y crear
  const [versionCalendario, setVersionCalendario] = useState(0); // al subir, el calendario recarga el mes visible

  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState(null);
  const [erroresCliente, setErroresCliente] = useState({});
  const [erroresEvento, setErroresEvento] = useState({});
  const [aviso, setAviso] = useState(null);

  const clientes = useConsulta(CLIENTES, { nombre: nombreBuscado || null, page: 0, size: 50 });
  const lugares = useConsulta(LUGARES, { estado: 'ACTIVO', page: 0, size: 100 });

  const listaClientes = clientes.data?.clientes?.contenido ?? [];
  const opcionesClientes = clienteCreado && !listaClientes.some((c) => c.id === clienteCreado.id)
    ? [clienteCreado, ...listaClientes] : listaClientes;

  const listaLugares = lugares.data?.lugares?.contenido ?? [];

  const cambiarEvento = (campo) => (e) => setEvento((v) => ({ ...v, [campo]: e.target.value }));
  const cambiarCliente = (campo) => (e) => setCliente((v) => ({ ...v, [campo]: e.target.value }));
  const elegirFecha = (fecha) => {
    setEvento((v) => ({ ...v, fecha }));
    setErroresEvento(({ fecha: _quitado, ...resto }) => resto);
  };

  /** Validación completa en la interfaz (cliente elegido + datos del evento). */
  function validar() {
    const errores = validarEvento(evento, { hoy: hoyIso(), lugar: listaLugares.find((l) => l.id === evento.lugarId) ?? null });
    if (modo === 'existente' && !clienteId) errores.clienteId = 'Elige un cliente o crea uno nuevo.';
    return errores;
  }

  /** Comprobación ligera del cliente nuevo; las reglas finas (teléfono, correo) las sigue decidiendo clientes-service. */
  function validarClienteNuevo() {
    const errores = {};
    if (!cliente.nombreCompleto.trim()) errores.nombreCompleto = 'Escribe el nombre del cliente.';
    if (!cliente.telefono.trim()) errores.telefono = 'Escribe un teléfono de contacto.';
    if (!cliente.correo.trim()) errores.correo = 'Escribe el correo del cliente.';
    return errores;
  }

  /** Avanza al siguiente paso solo si lo de este paso está completo; si no, marca lo que falta junto a cada campo. */
  function siguiente() {
    setError(null);
    setAviso(null);
    const delPaso = soloDelPaso(validar(), paso);
    const delCliente = paso === 0 && modo === 'nuevo' ? validarClienteNuevo() : {};
    setErroresEvento(delPaso);
    setErroresCliente(delCliente);
    if (Object.keys(delPaso).length || Object.keys(delCliente).length) return;
    setPaso((p) => Math.min(p + 1, PASOS_FORMULARIO.length - 1));
  }

  /** ¿La fecha sigue libre? (otra persona o pestaña pudo tomarla). Devuelve false y muestra el error si no lo está. */
  async function fechaSigueLibre() {
    const { data, errors, referencia } = await ejecutar(FECHAS_DISPONIBLES, { desde: evento.fecha, hasta: evento.fecha });
    if (!data?.fechasDisponibles) {
      setError(errors?.length ? aErrorApi(errors[0], referencia) : errorTecnico({ referencia, detalle: 'fechasDisponibles sin datos' }));
      return false;
    }
    if (!data.fechasDisponibles.some((f) => f.fecha === evento.fecha)) {
      // Ya no está libre: aviso, se quita la selección y el calendario recarga el mes (ese día saldrá "no disponible")
      setErroresEvento({ fecha: MENSAJE_FECHA_OCUPADA });
      setError(new ErrorApi(MENSAJE_FECHA_OCUPADA, { clasificacion: 'CONFLICT' }));
      setEvento((v) => ({ ...v, fecha: '' }));
      setVersionCalendario((n) => n + 1);
      setPaso(1); // de vuelta al calendario para elegir otra
      return false;
    }
    return true;
  }

  async function enviar(e) {
    e.preventDefault();
    if (paso < PASOS_FORMULARIO.length - 1) { // Enter en un paso intermedio = "Siguiente"
      siguiente();
      return;
    }
    if (enCurso.current) return;
    setError(null);
    setAviso(null);
    setErroresCliente({});
    const locales = validar();
    const deCliente = modo === 'nuevo' ? validarClienteNuevo() : {};
    setErroresEvento(locales);
    setErroresCliente(deCliente);
    const campos = [...Object.keys(deCliente), ...Object.keys(locales)];
    if (campos.length) { // nada se envía: ni el cliente ni el evento; se vuelve al primer paso con faltantes
      setPaso(Math.min(...campos.map(pasoDelCampo)));
      return;
    }

    enCurso.current = true;
    setEnviando(true);
    let clienteCreadoAhora = false;
    try {
      if (!(await fechaSigueLibre())) return;
      let idCliente = clienteId;
      if (modo === 'nuevo') {
        try {
          const datos = await mutar(CREAR_CLIENTE, {
            input: { ...cliente, nombreCompleto: cliente.nombreCompleto.trim(), direccion: cliente.direccion.trim() || null },
          });
          idCliente = datos.crearCliente.id;
          clienteCreadoAhora = true;
          // El cliente ya existe: si el evento falla, se reintenta con él (no se crea dos veces).
          setClienteCreado(datos.crearCliente);
          setClienteId(idCliente);
          setModo('existente');
          setCliente(CLIENTE_VACIO);
        } catch (err) {
          if (!(err instanceof ErrorSesion)) {
            setErroresCliente(err.campos ?? {});
            setError(aErrorVisible(err));
            setPaso(0);
          }
          return;
        }
      }
      const datos = await mutar(CREAR_EVENTO, {
        input: {
          nombreEvento: evento.nombreEvento.trim(),
          clienteId: idCliente,
          fecha: evento.fecha,
          tipoEvento: evento.tipoEvento,
          cantidadAdultos: numeroONulo(evento.cantidadAdultos),
          cantidadNinos: numeroONulo(evento.cantidadNinos),
          zona: evento.zona,
          lugarId: evento.lugarId || null,
        },
      });
      navegar(`/eventos/${datos.crearEvento.id}`, {
        state: { aviso: `Evento «${datos.crearEvento.nombreEvento}» creado (PENDIENTE). Siguiente paso: generar la cotización.` },
      });
    } catch (err) {
      if (err instanceof ErrorSesion) return;
      setErroresEvento(err.campos ?? {});
      setError(aErrorVisible(err));
      // de vuelta al paso donde se corrige: la fecha (409 o error de fecha) o el primer campo con error
      const campos = Object.keys(err.campos ?? {});
      if (err.esConflicto) setPaso(1);
      else if (campos.length) setPaso(Math.min(...campos.map(pasoDelCampo)));
      if (clienteCreado || clienteCreadoAhora) setAviso('El cliente se creó y quedó seleccionado; corrige los datos del evento.');
      if (err.esConflicto) setVersionCalendario((n) => n + 1); // la fecha pudo ocuparse: recargar el calendario
    } finally {
      enCurso.current = false;
      setEnviando(false);
    }
  }

  const ce = erroresCliente;
  const ee = erroresEvento;
  const ultimo = paso === PASOS_FORMULARIO.length - 1;
  const nombreCliente = modo === 'nuevo' ? cliente.nombreCompleto.trim() : (opcionesClientes.find((c) => c.id === clienteId)?.nombreCompleto ?? '—');
  const lugarElegido = listaLugares.find((l) => l.id === evento.lugarId);
  const invitados = (numeroONulo(evento.cantidadAdultos) ?? 0) + (numeroONulo(evento.cantidadNinos) ?? 0);
  return (
    <>
      <p className="ficha-volver"><Link to="/eventos">← Volver a eventos</Link></p>
      <h1>Nuevo evento</h1>
      <p className="subtitulo">Te guiamos en 4 pasos. Después de crearlo, la ficha te lleva por la cotización, el contrato y los abonos.</p>
      <nav aria-label="Pasos para crear el evento" className="asistente">
        <ol className="camino camino-4">
          {PASOS_FORMULARIO.map((nombre, i) => (
            <li key={nombre} className={[i < paso ? 'hecho' : null, i === paso ? 'actual elegido' : null].filter(Boolean).join(' ') || undefined}>
              <button type="button" className="camino-boton" disabled={i > paso} aria-current={i === paso ? 'step' : undefined}
                onClick={() => setPaso(i)}>
                <span className="camino-nombre">{nombre}</span>
              </button>
            </li>
          ))}
        </ol>
      </nav>
      <form className="formulario tarjeta" onSubmit={enviar} noValidate aria-label="Nuevo evento">
        <fieldset hidden={paso !== 0}>
          <legend>Cliente</legend>
          <p className="paso-ayuda">¿Para quién es el evento? Busca un cliente que ya exista o regístralo ahora.</p>
          <div className="opciones" role="radiogroup" aria-label="Tipo de cliente">
            <label><input type="radio" name="modo" value="existente" checked={modo === 'existente'} onChange={() => setModo('existente')} /> Cliente existente</label>
            <label><input type="radio" name="modo" value="nuevo" checked={modo === 'nuevo'} onChange={() => setModo('nuevo')} /> Cliente nuevo</label>
          </div>

          {modo === 'existente' ? (
            <>
              <div className="buscador">
                <Campo etiqueta="Buscar por nombre">
                  {({ id }) => (
                    <input id={id} type="search" value={busqueda} onChange={(e) => setBusqueda(e.target.value)}
                      onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); setNombreBuscado(busqueda.trim()); } }} />
                  )}
                </Campo>
                <button type="button" className="boton boton-secundario" onClick={() => setNombreBuscado(busqueda.trim())}>Buscar</button>
              </div>
              {clientes.error && <Aviso tipo="error">{clientes.error.message}</Aviso>}
              <Campo etiqueta="Cliente" error={ee.clienteId}
                ayuda={clientes.cargando ? 'Cargando clientes…' : `${formatearNumero(clientes.data?.clientes?.totalElementos ?? 0)} clientes encontrados (se muestran hasta 50).`}>
                {({ id, invalido, describedBy }) => (
                  <select id={id} value={clienteId} onChange={(e) => setClienteId(e.target.value)} aria-invalid={invalido} aria-describedby={describedBy}>
                    <option value="">Elige un cliente</option>
                    {opcionesClientes.map((c) => (
                      <option key={c.id} value={c.id}>{c.nombreCompleto}{c.telefono ? ` · ${c.telefono}` : ''}</option>
                    ))}
                  </select>
                )}
              </Campo>
            </>
          ) : (
            <div className="rejilla-campos">
              <Campo etiqueta="Nombre completo" error={ce.nombreCompleto}>
                {({ id, invalido, describedBy }) => <input id={id} value={cliente.nombreCompleto} onChange={cambiarCliente('nombreCompleto')} autoComplete="off" aria-invalid={invalido} aria-describedby={describedBy} />}
              </Campo>
              <Campo etiqueta="Teléfono" error={ce.telefono} ayuda="Entre 7 y 15 dígitos.">
                {({ id, invalido, describedBy }) => <input id={id} type="tel" value={cliente.telefono} onChange={cambiarCliente('telefono')} autoComplete="off" aria-invalid={invalido} aria-describedby={describedBy} />}
              </Campo>
              <Campo etiqueta="Correo" error={ce.correo}>
                {({ id, invalido, describedBy }) => <input id={id} type="email" value={cliente.correo} onChange={cambiarCliente('correo')} autoComplete="off" aria-invalid={invalido} aria-describedby={describedBy} />}
              </Campo>
              <Campo etiqueta="Dirección (opcional)" error={ce.direccion}>
                {({ id, invalido, describedBy }) => <input id={id} value={cliente.direccion} onChange={cambiarCliente('direccion')} autoComplete="off" aria-invalid={invalido} aria-describedby={describedBy} />}
              </Campo>
            </div>
          )}
        </fieldset>

        <fieldset hidden={paso !== 1}>
          <legend>Fecha</legend>
          <p className="paso-ayuda">Toca un día verde del calendario para apartarlo.</p>
          <p id="ayuda-fecha" className="campo-ayuda texto-pequeno calendario-ayuda">
            Solo se pueden elegir viernes, sábados y domingos libres (un evento por fin de semana), desde mañana y hasta 366 días.
          </p>
          <CalendarioFechas valor={evento.fecha} alElegir={elegirFecha} version={versionCalendario} invalido={Boolean(ee.fecha)}
            describedBy={ee.fecha ? 'ayuda-fecha error-fecha' : 'ayuda-fecha'} />
          {ee.fecha && <p id="error-fecha" className="campo-error calendario-error-campo">{ee.fecha}</p>}
        </fieldset>

        <fieldset hidden={paso !== 2}>
          <legend>Datos del evento</legend>
          <p className="paso-ayuda">Nombre, tipo, cuántos invitados habrá y dónde será. El lugar puede quedar para después.</p>
          <div className="rejilla-campos">
            <Campo etiqueta="Nombre del evento" error={ee.nombreEvento} ayuda="Entre 3 y 150 caracteres.">
              {({ id, invalido, describedBy }) => <input id={id} value={evento.nombreEvento} onChange={cambiarEvento('nombreEvento')} autoComplete="off" aria-invalid={invalido} aria-describedby={describedBy} />}
            </Campo>
            <Campo etiqueta="Tipo de evento" error={ee.tipoEvento}>
              {({ id, invalido, describedBy }) => (
                <select id={id} value={evento.tipoEvento} onChange={cambiarEvento('tipoEvento')} aria-invalid={invalido} aria-describedby={describedBy}>
                  <option value="">Elige el tipo</option>
                  {Object.entries(TIPOS_EVENTO).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
                </select>
              )}
            </Campo>
            <Campo etiqueta="Adultos" error={ee.cantidadAdultos}>
              {({ id, invalido, describedBy }) => <input id={id} type="number" inputMode="numeric" step="1" value={evento.cantidadAdultos} onChange={cambiarEvento('cantidadAdultos')} aria-invalid={invalido} aria-describedby={describedBy} />}
            </Campo>
            <Campo etiqueta="Niños" error={ee.cantidadNinos}>
              {({ id, invalido, describedBy }) => <input id={id} type="number" inputMode="numeric" step="1" value={evento.cantidadNinos} onChange={cambiarEvento('cantidadNinos')} aria-invalid={invalido} aria-describedby={describedBy} />}
            </Campo>
            <Campo etiqueta="Zona" error={ee.zona}>
              {({ id, invalido, describedBy }) => (
                <select id={id} value={evento.zona} onChange={cambiarEvento('zona')} aria-invalid={invalido} aria-describedby={describedBy}>
                  <option value="">Elige la zona</option>
                  {Object.entries(ZONAS).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
                </select>
              )}
            </Campo>
            <Campo etiqueta="Lugar (opcional)" error={ee.lugarId}>
              {({ id, invalido, describedBy }) => (
                <select id={id} value={evento.lugarId} onChange={cambiarEvento('lugarId')} aria-invalid={invalido} aria-describedby={describedBy}>
                  <option value="">Sin lugar por ahora</option>
                  {listaLugares.map((l) => (
                    <option key={l.id} value={l.id}>{l.nombre} · {etiqueta(TIPOS_LUGAR, l.tipo)} · capacidad {formatearNumero(l.capacidad)}</option>
                  ))}
                </select>
              )}
            </Campo>
          </div>
        </fieldset>

        <fieldset hidden={!ultimo}>
          <legend>Revisar y crear</legend>
          <p className="paso-ayuda">Confirma que todo esté bien. Al crear, la fecha queda apartada como pendiente.</p>
          <dl className="datos resumen-revision">
            <div className="dato"><dt>Cliente</dt><dd>{nombreCliente}{modo === 'nuevo' && ' (nuevo)'}</dd></div>
            <div className="dato"><dt>Fecha</dt><dd>{formatearFecha(evento.fecha, { larga: true })}</dd></div>
            <div className="dato"><dt>Evento</dt><dd>{evento.nombreEvento.trim() || '—'}</dd></div>
            <div className="dato"><dt>Tipo</dt><dd>{etiqueta(TIPOS_EVENTO, evento.tipoEvento)}</dd></div>
            <div className="dato"><dt>Invitados</dt><dd>{formatearNumero(invitados)} ({formatearNumero(numeroONulo(evento.cantidadAdultos) ?? 0)} adultos, {formatearNumero(numeroONulo(evento.cantidadNinos) ?? 0)} niños)</dd></div>
            <div className="dato"><dt>Zona</dt><dd>{etiqueta(ZONAS, evento.zona)}</dd></div>
            <div className="dato"><dt>Lugar</dt><dd>{lugarElegido ? lugarElegido.nombre : 'Sin lugar por ahora'}</dd></div>
          </dl>
        </fieldset>

        {aviso && <Aviso tipo="info">{aviso}</Aviso>}
        <ErrorAccion error={error} />
        <div className="acciones-formulario asistente-acciones">
          {paso > 0 && <button type="button" className="boton boton-secundario" onClick={() => setPaso(paso - 1)}>Atrás</button>}
          {ultimo
            ? <BotonAccion type="submit" enviando={enviando} textoEnviando="Creando evento…">Crear evento</BotonAccion>
            : <button type="submit" className="boton boton-primario">Siguiente</button>}
        </div>
      </form>
    </>
  );
}
