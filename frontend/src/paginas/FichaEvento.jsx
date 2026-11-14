import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import AccionSimple from '../acciones/AccionSimple.jsx';
import Desplegable from '../acciones/Desplegable.jsx';
import FormularioAdicional from '../acciones/FormularioAdicional.jsx';
import FormularioLugar from '../acciones/FormularioLugar.jsx';
import FormularioPago from '../acciones/FormularioPago.jsx';
import FormularioProveedor from '../acciones/FormularioProveedor.jsx';
import ListaCompras from '../acciones/ListaCompras.jsx';
import { aErrorApi, aErrorVisible, ejecutar, erroresPorSeccion, ErrorSesion, mutar } from '../api/cliente.js';
import {
  CANCELAR_EVENTO, CONFIRMAR_ASIGNACION, CONFIRMAR_EVENTO, CREAR_CONTRATO, FICHA_EVENTO, FIRMAR_CONTRATO, GENERAR_COTIZACION,
} from '../api/consultas.js';
import { descargarDocumento } from '../api/descargas.js';
import Aviso from '../componentes/Aviso.jsx';
import GuiaPaso from '../componentes/GuiaPaso.jsx';
import Datos from '../componentes/Datos.jsx';
import { InsigniaEstado } from '../componentes/Insignia.jsx';
import PasosEvento from '../componentes/PasosEvento.jsx';
import AvisoRecordatorio from '../componentes/AvisoRecordatorio.jsx';
import ResumenEvento from '../componentes/ResumenEvento.jsx';
import { avisarCambios } from '../utils/avisos.js';
import ResumenFicha from '../componentes/ResumenFicha.jsx';
import Seccion from '../componentes/Seccion.jsx';
import Tabla from '../componentes/Tabla.jsx';
import { VistaPaso } from '../componentes/VistaPaso.js';
import {
  CATEGORIAS_PROVEEDOR, ESTADOS_ASIGNACION, ESTADOS_CONTRATO, ESTADOS_LUGAR, ESTADOS_NOTIFICACION, etiqueta, formatearFecha,
  formatearFechaHora, formatearJson, formatearMonto, formatearNumero, METODOS_PAGO, SERVICIOS, TIPOS_EVENTO, TIPOS_LUGAR,
  TIPOS_MENSAJE, ZONAS,
} from '../utils/formato.js';
import { estadoPasos, VISTAS } from '../utils/pasosEvento.js';
import { esFechaPasada, hoyEnZona, textoAvisoPasado } from '../utils/fechaPasada.js';
import { useSesion } from '../sesion/SesionContext.jsx';

/**
 * FICHA DEL EVENTO — la vista única del requisito no funcional: toda la información del evento con UNA query.
 * Si una relación falla (servicio caído), su sección muestra el aviso y el resto se ve normal.
 */
export default function FichaEvento() {
  const { id } = useParams();
  const { esAdministrador } = useSesion();
  const [estado, setEstado] = useState({ cargando: true, evento: null, secciones: {}, error: null });

  /** Carga (o recarga) la ficha completa desde el backend. Fase 11D: la auditoría solo se pide si es ADMINISTRADOR. */
  const cargar = useCallback(async () => {
    setEstado((previo) => ({ ...previo, cargando: true }));
    try {
      const { data, errors, referencia } = await ejecutar(FICHA_EVENTO, { id, verAuditoria: esAdministrador });
      if (!data?.evento) {
        const principal = errors.find((e) => !e.path || e.path.length <= 1) ?? errors[0];
        setEstado({ cargando: false, evento: null, secciones: {}, error: principal ? aErrorApi(principal, referencia) : null });
        return;
      }
      setEstado({ cargando: false, evento: data.evento, secciones: erroresPorSeccion(errors, 'evento', referencia), error: null });
    } catch (err) {
      if (err instanceof ErrorSesion) return; // la aplicación vuelve al login
      setEstado({ cargando: false, evento: null, secciones: {}, error: aErrorVisible(err) });
    }
  }, [id, esAdministrador]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  // Mensaje de la última acción exitosa (o el que deja "Nuevo evento" al crear).
  const ubicacion = useLocation();
  const [exito, setExito] = useState(() => ubicacion.state?.aviso ?? null);

  // Qué paso se ve: el de la URL (?paso=contrato) o, si no hay, el que toca ahora. "todo" muestra todas las secciones.
  const [params, setParams] = useSearchParams();
  const vistaRef = useRef(null);
  const actualRef = useRef(null);
  const irA = useCallback((paso) => {
    setParams((previo) => {
      const siguiente = new URLSearchParams(previo);
      siguiente.set('paso', paso);
      return siguiente;
    });
  }, [setParams]);

  /** Tras cada acción exitosa: recargar la ficha COMPLETA (el estado real del backend) y avisar. */
  const alTerminar = useCallback(async (mensaje) => {
    setExito(null);
    // Si estaba viendo el paso que tocaba, la guía avanza sola al siguiente pendiente (quita el paso fijado en la URL).
    if (vistaRef.current && vistaRef.current === actualRef.current) {
      setParams((previo) => {
        const siguiente = new URLSearchParams(previo);
        siguiente.delete('paso');
        return siguiente;
      }, { replace: true });
    }
    await cargar();
    setExito(mensaje ?? 'Acción realizada.');
    avisarCambios(); // Fase 11H: la campanita vuelve a consultar (un pago o una confirmación generan notificaciones)
  }, [cargar, setParams]);

  const { evento, secciones, error, cargando } = estado;

  if (!evento) {
    return (
      <>
        <p><Link to="/eventos">← Volver a eventos</Link></p>
        {cargando && <p className="texto-suave" role="status">Cargando la ficha del evento…</p>}
        {!cargando && error && <Aviso tipo="error" titulo="No se pudo abrir la ficha.">{error.message}</Aviso>}
        {!cargando && !error && <Aviso tipo="error">El evento no existe.</Aviso>}
      </>
    );
  }

  const activo = evento.estado !== 'CANCELADO';
  // Fase 11I: con la fecha ya pasada se ocultan las acciones que ya no tienen sentido (confirmar, cancelar, lugar, contrato,
  // cotización, adicionales y proveedores —asignar y confirmar—). Siguen: pagos (el saldo se paga después), los PDF, la lista
  // de compras y el Resumen.
  const hoy = hoyEnZona();
  const pasado = activo && esFechaPasada(evento.fecha, hoy);
  const editable = activo && !pasado;
  const pasos = estadoPasos(evento, secciones, { pasado });
  const pedido = params.get('paso');
  const vista = !activo ? 'todo' : VISTAS.includes(pedido) ? pedido : pasos.actualId;
  vistaRef.current = vista;
  actualRef.current = pasos.actualId;

  const moneda = evento.cotizacion?.moneda ?? evento.balance?.moneda ?? 'COP';
  const m = (valor) => formatearMonto(valor, moneda);

  // Qué acciones se MUESTRAN según el estado. Es solo comodidad: la regla la decide el servicio (sus 409 se ven en pantalla).
  const tieneCotizacion = Boolean(evento.cotizacion) && !secciones.cotizacion;
  const comun = { alTerminar, bloqueada: cargando };
  const descargar = (documento) => async () => {
    const nombre = await descargarDocumento(evento.id, documento);
    setExito(`Se descargó ${nombre}.`); // solo lectura: no cambia el evento, así que no hace falta recargar
    return null;
  };

  return (
    <div className={cargando ? 'cargando' : undefined} aria-busy={cargando}>
      <p className="ficha-volver"><Link to="/eventos">← Volver a eventos</Link></p>

      <header className="ficha-cabecera tarjeta">
        <div>
          <div className="ficha-titulo">
            <h1>{evento.nombreEvento}</h1>
            <InsigniaEstado estado={evento.estado} fecha={evento.fecha} hoy={hoy} />
          </div>
          <p className="ficha-fecha">{formatearFecha(evento.fecha, { larga: true })}</p>
        </div>
        <Datos
          filas={[
            ['Tipo', etiqueta(TIPOS_EVENTO, evento.tipoEvento)],
            ['Zona', etiqueta(ZONAS, evento.zona)],
            ['Invitados', `${formatearNumero(evento.numInvitados)} (${formatearNumero(evento.cantidadAdultos)} adultos, ${formatearNumero(evento.cantidadNinos)} niños)`],
            ['Creado', formatearFechaHora(evento.fechaCreacion)],
          ]}
        />
        {activo && <PasosEvento pasos={pasos} vista={vista} alElegir={irA} />}
      </header>

      <div aria-live="polite">
        {exito && !cargando && <Aviso tipo="exito">{exito}</Aviso>}
      </div>

      {/* Fase 11E: fuera de la guía, así se ve en todos los pasos. Solo mientras el evento sigue RESERVADO. */}
      {/* Fase 11I: arriba, en todos los pasos. El estado del backend no cambia; solo la interfaz sabe que la fecha pasó. */}
      {pasado && <Aviso tipo="pasado">{textoAvisoPasado(evento, hoy)}</Aviso>}

      {evento.estado === 'RESERVADO' && !pasado && <AvisoRecordatorio notificaciones={evento.notificaciones?.contenido} />}

      {activo && (
        <GuiaPaso vista={vista} pasos={pasos} alElegir={irA} pasado={pasado}>
          {vista === 'confirmar' && pasado && (
            <p className="guia-ahora">
              {evento.estado === 'RESERVADO'
                ? 'El evento se confirmó y ya se realizó.'
                : 'Este evento ya no se puede confirmar: su fecha pasó sin confirmarse.'}
            </p>
          )}
          {/* El botón está siempre (decisión de la Fase 11): la lista de arriba marca lo que falta y Eventos decide; si falta algo, su 409 se ve aquí. */}
          {vista === 'confirmar' && evento.estado === 'PENDIENTE' && !pasado && (
            <>
              {!pasos.confirmable && (
                <p className="guia-ahora">Aún falta algo de la lista. Si confirmas ahora, el sistema te dirá qué falta.</p>
              )}
              <AccionSimple etiqueta="Confirmar evento" textoEnviando="Confirmando…" {...comun}
                operacion={async () => { await mutar(CONFIRMAR_EVENTO, { id: evento.id }); return 'Evento confirmado: quedó RESERVADO y la fecha OCUPADA. La logística se calcula en unos segundos.'; }} />
            </>
          )}
          {vista === 'confirmar' && evento.estado === 'RESERVADO' && !pasado && (
            <p className="guia-ahora">El evento ya está confirmado. Sigue con los preparativos.</p>
          )}
          {/* Fase 11F: paso 7, con los mismos datos de la ficha (sin peticiones nuevas) */}
          {vista === 'resumen' && <ResumenEvento evento={evento} secciones={secciones} alElegir={irA} hoy={hoy} />}
        </GuiaPaso>
      )}

      <ResumenFicha evento={evento} secciones={secciones} />

      <VistaPaso.Provider value={vista}>
      <div className="ficha-rejilla">
        <Seccion id="cliente" paso="evento" titulo="Cliente" error={secciones.cliente}>
          {evento.cliente ? (
            <Datos
              filas={[
                ['Nombre', evento.cliente.nombreCompleto],
                ['Teléfono', evento.cliente.telefono],
                ['Correo', evento.cliente.correo],
                ['Dirección', evento.cliente.direccion],
              ]}
            />
          ) : <p className="vacio">No se encontró el cliente.</p>}
        </Seccion>

        <Seccion id="lugar" paso="evento" titulo="Lugar" error={secciones.lugar}
          acciones={editable && (
            <Desplegable etiqueta={evento.lugar ? 'Cambiar lugar' : 'Asignar lugar'} bloqueado={cargando}>
              {(cerrar) => <FormularioLugar evento={evento} alTerminar={alTerminar} cerrar={cerrar} />}
            </Desplegable>
          )}>
          {evento.lugar ? (
            <Datos
              filas={[
                ['Nombre', evento.lugar.nombre],
                ['Dirección', evento.lugar.direccion],
                ['Tipo', etiqueta(TIPOS_LUGAR, evento.lugar.tipo)],
                ['Capacidad', formatearNumero(evento.lugar.capacidad)],
                ['Estado', etiqueta(ESTADOS_LUGAR, evento.lugar.estado)],
              ]}
            />
          ) : <p className="vacio">Sin lugar asignado.</p>}
        </Seccion>

        <Seccion id="cotizacion" paso="cotizacion" titulo="Cotización" error={secciones.cotizacion}
          acciones={editable && !evento.cotizacion && (
            <AccionSimple etiqueta="Generar cotización" textoEnviando="Generando cotización…" {...comun}
              operacion={async () => { await mutar(GENERAR_COTIZACION, { eventoId: evento.id }); return 'Cotización generada.'; }} />
          )}>
          {evento.cotizacion ? (
            <Datos
              filas={[
                ['Valor base', m(evento.cotizacion.valorBase)],
                ['Adultos adicionales', `${formatearNumero(evento.cotizacion.adultosAdicionales)} · ${m(evento.cotizacion.valorAdultosAdicionales)}`],
                ['Niños adicionales', `${formatearNumero(evento.cotizacion.ninosAdicionales)} · ${m(evento.cotizacion.valorNinosAdicionales)}`],
                ['Recargo por zona', m(evento.cotizacion.recargoZona)],
                ['Total sin adicionales', m(evento.cotizacion.totalSinAdicionales)],
                ['Total con adicionales', m(evento.cotizacion.total)],
                ['Depósito requerido', m(evento.cotizacion.depositoInicialRequerido)],
              ]}
            />
          ) : <p className="vacio">El evento aún no tiene cotización.</p>}
        </Seccion>

        <Seccion id="balance" paso="abonos" titulo="Balance" error={secciones.balance}>
          {evento.balance ? (
            <Datos
              filas={[
                ['Total cotizado', m(evento.balance.totalCotizado)],
                ['Cotización', m(evento.balance.valorCotizacion)],
                ['Adicionales', m(evento.balance.adicionales)],
                ['Abonado', m(evento.balance.montoAbonado)],
                ['Saldo pendiente', <strong key="saldo">{m(evento.balance.saldoPendiente)}</strong>],
                ['Depósito requerido', m(evento.balance.depositoInicialRequerido)],
              ]}
            />
          ) : <p className="vacio">Sin balance: el evento aún no tiene cotización.</p>}
        </Seccion>

        <Seccion id="contrato" paso="contrato" titulo="Contrato" error={secciones.contrato}
          acciones={(
            <>
              {editable && tieneCotizacion && !evento.contrato && (
                <AccionSimple etiqueta="Crear contrato" textoEnviando="Creando contrato…" {...comun}
                  operacion={async () => { await mutar(CREAR_CONTRATO, { eventoId: evento.id }); return 'Contrato creado (pendiente de firma).'; }} />
              )}
              {editable && evento.contrato?.estado === 'PENDIENTE' && (
                <AccionSimple etiqueta="Firmar contrato" textoEnviando="Firmando…" {...comun}
                  confirmacion="¿Marcar el contrato como firmado?"
                  operacion={async () => { await mutar(FIRMAR_CONTRATO, { eventoId: evento.id }); return 'Contrato firmado.'; }} />
              )}
              {evento.contrato && (
                <AccionSimple etiqueta="Descargar contrato (PDF)" textoEnviando="Descargando…" variante="secundario"
                  alTerminar={null} operacion={descargar('contrato')} />
              )}
              {evento.contrato && evento.serviciosAdicionales?.length > 0 && (
                <AccionSimple etiqueta="Descargar anexo (PDF)" textoEnviando="Descargando…" variante="secundario"
                  alTerminar={null} operacion={descargar('anexo')} />
              )}
            </>
          )}>
          {evento.contrato ? (
            <Datos
              filas={[
                ['Estado', etiqueta(ESTADOS_CONTRATO, evento.contrato.estado)],
                ['Fecha de firma', formatearFechaHora(evento.contrato.fechaFirma)],
                ['Creado', formatearFechaHora(evento.contrato.creadoEn)],
                ['Cliente', evento.contrato.clienteNombre],
                ['Lugar', evento.contrato.lugarNombre],
                ['Valor total', formatearMonto(evento.contrato.valorTotal, evento.contrato.moneda ?? moneda)],
              ]}
            />
          ) : <p className="vacio">El evento aún no tiene contrato.</p>}
        </Seccion>

        <Seccion id="pagos" paso="abonos" titulo="Pagos" error={secciones.pagos}
          acciones={activo && tieneCotizacion && !(evento.balance && Number(evento.balance.saldoPendiente) <= 0) && (
            <Desplegable etiqueta="Registrar pago" bloqueado={cargando}>
              {(cerrar) => <FormularioPago evento={evento} alTerminar={alTerminar} cerrar={cerrar} />}
            </Desplegable>
          )}>
          {evento.pagos?.contenido?.length ? (
            <Tabla titulo="Tabla de pagos" columnas={['Fecha', 'Monto', 'Método']}>
              {evento.pagos.contenido.map((p) => (
                <tr key={p.id}>
                  <td data-etiqueta="Fecha">{formatearFechaHora(p.fecha)}</td>
                  <td data-etiqueta="Monto">{formatearMonto(p.monto, p.moneda)}</td>
                  <td data-etiqueta="Método">{etiqueta(METODOS_PAGO, p.metodoPago)}</td>
                </tr>
              ))}
            </Tabla>
          ) : <p className="vacio">Sin pagos registrados.</p>}
        </Seccion>

        <Seccion id="serviciosAdicionales" paso="preparativos" titulo="Servicios adicionales" error={secciones.serviciosAdicionales} ancha
          acciones={editable && tieneCotizacion && (
            <Desplegable etiqueta="Agregar servicio adicional" bloqueado={cargando}>
              {(cerrar) => <FormularioAdicional evento={evento} alTerminar={alTerminar} cerrar={cerrar} />}
            </Desplegable>
          )}>
          {evento.serviciosAdicionales?.length ? (
            <Tabla titulo="Tabla de servicios adicionales" columnas={['Servicio', 'Cantidad', 'Valor unitario', 'Subtotal']}>
              {evento.serviciosAdicionales.map((s) => (
                <tr key={s.id}>
                  <td data-etiqueta="Servicio">{s.nombre}</td>
                  <td data-etiqueta="Cantidad">{formatearNumero(s.cantidad)}</td>
                  <td data-etiqueta="Valor unitario">{m(s.valorUnitario)}</td>
                  <td data-etiqueta="Subtotal">{m(s.subtotal)}</td>
                </tr>
              ))}
            </Tabla>
          ) : <p className="vacio">Sin servicios adicionales.</p>}
        </Seccion>

        <Seccion id="proveedores" paso="preparativos" titulo="Proveedores asignados" error={secciones.proveedores} ancha
          acciones={editable && (
            <Desplegable etiqueta="Asignar proveedor" bloqueado={cargando}>
              {(cerrar) => <FormularioProveedor evento={evento} alTerminar={alTerminar} cerrar={cerrar} />}
            </Desplegable>
          )}>
          {evento.proveedores?.contenido?.length ? (
            <Tabla titulo="Tabla de proveedores asignados" columnas={editable ? ['Proveedor', 'Servicio', 'Costo acordado', 'Estado', 'Acción'] : ['Proveedor', 'Servicio', 'Costo acordado', 'Estado']}>
              {evento.proveedores.contenido.map((p) => (
                <tr key={p.id}>
                  <td data-etiqueta="Proveedor">
                    <span>
                      {p.proveedor?.nombre ?? '—'}
                      {p.categoria && <span className="texto-suave"> · {etiqueta(CATEGORIAS_PROVEEDOR, p.categoria)}</span>}
                    </span>
                  </td>
                  <td data-etiqueta="Servicio">{p.servicio}</td>
                  <td data-etiqueta="Costo acordado">{m(p.costoAcordado)}</td>
                  <td data-etiqueta="Estado">{etiqueta(ESTADOS_ASIGNACION, p.estado)}</td>
                  {editable && (
                    <td data-etiqueta="Acción">
                      {p.estado === 'PENDIENTE' ? (
                        <AccionSimple etiqueta="Confirmar" textoEnviando="Confirmando…" variante="secundario" {...comun}
                          operacion={async () => { await mutar(CONFIRMAR_ASIGNACION, { id: p.id }); return `${p.proveedor?.nombre ?? 'El proveedor'} quedó confirmado para «${p.servicio}».`; }} />
                      ) : <span className="texto-suave">—</span>}
                    </td>
                  )}
                </tr>
              ))}
            </Tabla>
          ) : <p className="vacio">Sin proveedores asignados.</p>}
        </Seccion>

        <Seccion id="logistica" paso="preparativos" titulo="Logística" error={secciones.logistica} ancha>
          {evento.logistica ? (
            <>
              <Datos
                filas={[
                  ['Meseros', formatearNumero(evento.logistica.meseros)],
                  ['Personal de cocina', formatearNumero(evento.logistica.personalCocina)],
                  ['Vehículos', formatearNumero(evento.logistica.vehiculos)],
                ]}
              />
              <h3>Lista de compras</h3>
              <ListaCompras eventoId={evento.id} items={evento.logistica.compras?.contenido} editable={activo}
                alTerminar={alTerminar} />
            </>
          ) : (
            <>
              <p className="vacio">El personal y los vehículos se calculan cuando el evento se confirma.</p>
              <h3>Lista de compras</h3>
              <p className="vacio">La lista de compras se genera al confirmar el evento.</p>
            </>
          )}
        </Seccion>

        <Seccion id="notificaciones" paso="historial" titulo="Notificaciones" error={secciones.notificaciones} ancha>
          {evento.notificaciones?.contenido?.length ? (
            <Tabla titulo="Tabla de notificaciones" columnas={['Asunto', 'Tipo', 'Fecha', 'Estado', 'Mensaje']}>
              {evento.notificaciones.contenido.map((n) => (
                <tr key={n.id}>
                  <td data-etiqueta="Asunto"><strong>{n.asunto}</strong></td>
                  <td data-etiqueta="Tipo">{etiqueta(TIPOS_MENSAJE, n.tipo)}</td>
                  <td data-etiqueta="Fecha">{formatearFechaHora(n.fechaCreacion)}</td>
                  <td data-etiqueta="Estado">{etiqueta(ESTADOS_NOTIFICACION, n.estado)}</td>
                  <td data-etiqueta="Mensaje">{n.mensaje}</td>
                </tr>
              ))}
            </Tabla>
          ) : <p className="vacio">Sin notificaciones.</p>}
        </Seccion>

        {/* Fase 11D: solo para el ADMINISTRADOR (para el asistente ni se pide en la query) */}
        {esAdministrador && (
        <Seccion id="auditoria" paso="historial" titulo="Auditoría" error={secciones.auditoria} ancha>
          {evento.auditoria?.contenido?.length ? (
            <Tabla titulo="Tabla de auditoría" columnas={['Tipo', 'Recibido', 'Origen', 'Tópico', 'Mensaje']}>
              {evento.auditoria.contenido.map((a) => (
                <tr key={a.idEvento}>
                  <td data-etiqueta="Tipo"><strong>{etiqueta(TIPOS_MENSAJE, a.tipo)}</strong></td>
                  <td data-etiqueta="Recibido">{formatearFechaHora(a.fechaRecepcion)}</td>
                  <td data-etiqueta="Origen">{etiqueta(SERVICIOS, a.servicioOrigen)}</td>
                  <td data-etiqueta="Tópico">
                    <span>
                      <span className="texto-partible">{a.topico}</span>
                      <span className="texto-suave"> · partición {a.particion}, offset {a.offset}</span>
                    </span>
                  </td>
                  <td data-etiqueta="Mensaje" className="celda-mensaje">
                    <details>
                      <summary>Ver mensaje</summary>
                      <pre>{formatearJson(a.payload)}</pre>
                    </details>
                  </td>
                </tr>
              ))}
            </Tabla>
          ) : <p className="vacio">Sin registros de auditoría.</p>}
        </Seccion>
        )}
      </div>
      </VistaPaso.Provider>

      {editable && (
          <div className="ficha-acciones tarjeta" aria-label="Acciones del evento" role="group">
            {evento.estado === 'PENDIENTE' && vista === 'todo' && (
              <AccionSimple etiqueta="Confirmar evento" textoEnviando="Confirmando…" {...comun}
                operacion={async () => { await mutar(CONFIRMAR_EVENTO, { id: evento.id }); return 'Evento confirmado: quedó RESERVADO y la fecha OCUPADA. La logística se calcula en unos segundos.'; }} />
            )}
            <AccionSimple etiqueta="Cancelar evento" textoEnviando="Cancelando…" variante="peligro" {...comun}
              confirmacion="¿Cancelar el evento? Su fecha vuelve a quedar disponible."
              operacion={async () => { await mutar(CANCELAR_EVENTO, { id: evento.id }); return 'Evento cancelado: la fecha vuelve a estar disponible.'; }} />
          </div>
        )}
    </div>
  );
}
