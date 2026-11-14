import { useState } from 'react';
import AccionSimple from '../acciones/AccionSimple.jsx';
import FormularioDirectorioLugar from '../acciones/FormularioDirectorioLugar.jsx';
import { mutar } from '../api/cliente.js';
import { CAMBIAR_ESTADO_LUGAR, LUGARES } from '../api/consultas.js';
import { useConsulta } from '../api/useConsulta.js';
import Aviso from '../componentes/Aviso.jsx';
import Campo from '../componentes/Campo.jsx';
import Paginacion from '../componentes/Paginacion.jsx';
import Tabla from '../componentes/Tabla.jsx';
import { useFiltrosUrl } from '../componentes/useFiltrosUrl.js';
import { useSesion } from '../sesion/SesionContext.jsx';
import { esNombreDePrueba } from '../utils/ayudas.js';
import { ESTADOS_LUGAR, etiqueta, formatearNumero, TIPOS_LUGAR } from '../utils/formato.js';

const TAMANO = 20;

/**
 * Directorio de lugares (query lugares del gateway) con registro, edición y activar / desactivar (10B3: mutations
 * crearLugar, actualizarLugar y cambiarEstadoLugar). Tras cada cambio se recarga la página del listado.
 */
export default function Lugares() {
  // Fase 11D: registrar, editar y activar / desactivar es del ADMINISTRADOR; el asistente solo consulta (Lugares responde
  // 403 de todos modos si se llama a la API).
  const { esAdministrador } = useSesion();
  const f = useFiltrosUrl();
  const prueba = f.leer('prueba') === '1';
  const [recarga, setRecarga] = useState(0);
  const [formulario, setFormulario] = useState(null); // null | { lugar?: {...} }
  const [exito, setExito] = useState(null);
  const { data, error, cargando } = useConsulta(LUGARES, {
    nombre: f.leer('nombre') || null, tipo: f.leer('tipo') || null, estado: f.leer('estado') || null, page: f.pagina, size: TAMANO,
  }, `${f.clave}|${recarga}`);
  const pagina = data?.lugares;
  const todos = pagina?.contenido ?? [];
  const visibles = prueba ? todos : todos.filter((l) => !esNombreDePrueba(l.nombre));

  const alCambiar = async (mensaje) => {
    setExito(mensaje);
    setRecarga((r) => r + 1);
  };
  const abrir = (lugar) => {
    setExito(null);
    setFormulario(lugar ? { lugar } : {});
  };

  return (
    <>
      <div className="titulo-pagina">
        <h1>Lugares</h1>
        {esAdministrador && (
          <button type="button" className="boton boton-primario" onClick={() => abrir(null)}
            aria-expanded={Boolean(formulario && !formulario.lugar)}>Nuevo lugar</button>
        )}
      </div>
      {!esAdministrador && <p className="texto-suave">Solo el administrador registra o modifica lugares. Puedes consultarlos y asignarlos a un evento desde su ficha.</p>}

      {esAdministrador && formulario && (
        <section className="tarjeta panel-directorio" aria-labelledby="titulo-formulario-lugar">
          <h2 id="titulo-formulario-lugar">{formulario.lugar ? `Editar «${formulario.lugar.nombre}»` : 'Nuevo lugar'}</h2>
          <FormularioDirectorioLugar key={formulario.lugar?.id ?? 'nuevo'} lugar={formulario.lugar} alGuardar={alCambiar}
            cerrar={() => setFormulario(null)} />
        </section>
      )}
      <div aria-live="polite">{exito && <Aviso tipo="exito">{exito}</Aviso>}</div>

      <form className="filtros tarjeta" onSubmit={(e) => e.preventDefault()} aria-label="Filtros de lugares">
        <Campo etiqueta="Nombre">
          {({ id }) => <input id={id} key={f.leer('nombre')} type="search" defaultValue={f.leer('nombre')} onBlur={(e) => f.cambiar({ nombre: e.target.value.trim() })}
            onKeyDown={(e) => { if (e.key === 'Enter') f.cambiar({ nombre: e.currentTarget.value.trim() }); }} />}
        </Campo>
        <Campo etiqueta="Tipo">
          {({ id }) => (
            <select id={id} value={f.leer('tipo')} onChange={(e) => f.cambiar({ tipo: e.target.value })}>
              <option value="">Todos</option>
              {Object.entries(TIPOS_LUGAR).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
            </select>
          )}
        </Campo>
        <Campo etiqueta="Estado">
          {({ id }) => (
            <select id={id} value={f.leer('estado')} onChange={(e) => f.cambiar({ estado: e.target.value })}>
              <option value="">Todos</option>
              {Object.entries(ESTADOS_LUGAR).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
            </select>
          )}
        </Campo>
        <div className="campo campo-casilla">
          <input id="lugares-prueba" type="checkbox" checked={prueba} onChange={(e) => f.cambiar({ prueba: e.target.checked, pagina: f.pagina })} />
          <label htmlFor="lugares-prueba">Mostrar lugares de prueba</label>
        </div>
        <button type="button" className="boton boton-secundario" onClick={f.limpiar} disabled={f.clave === ''}>Limpiar filtros</button>
      </form>

      {error && <Aviso tipo="error" titulo="No se pudo cargar el directorio.">{error.message}</Aviso>}
      {cargando && !pagina && <p className="texto-suave" role="status">Cargando lugares…</p>}
      {pagina && (
        <div className={cargando ? 'cargando' : undefined} aria-busy={cargando}>
          <p className="texto-suave" role="status">
            {formatearNumero(pagina.totalElementos)} lugares{todos.length !== visibles.length && ` · ${todos.length - visibles.length} de prueba ocultos en esta página`}
          </p>
          {visibles.length === 0 ? <p className="vacio">No hay lugares con estos filtros.</p> : (
            <Tabla titulo="Lugares" columnas={['Nombre', 'Tipo', 'Capacidad', 'Dirección', 'Estado', ...(esAdministrador ? ['Acciones'] : [])]}>
              {visibles.map((l) => (
                <tr key={l.id}>
                  <td data-etiqueta="Nombre">{l.nombre}</td>
                  <td data-etiqueta="Tipo">{etiqueta(TIPOS_LUGAR, l.tipo)}</td>
                  <td data-etiqueta="Capacidad">{formatearNumero(l.capacidad)}</td>
                  <td data-etiqueta="Dirección">{l.direccion ?? '—'}</td>
                  <td data-etiqueta="Estado">{etiqueta(ESTADOS_LUGAR, l.estado)}</td>
                  {esAdministrador && <td data-etiqueta="Acciones">
                    <div className="acciones-fila">
                      <button type="button" className="boton boton-secundario" onClick={() => abrir(l)}
                        aria-label={`Editar ${l.nombre}`}>Editar</button>
                      {l.estado === 'INACTIVO' ? (
                        <AccionSimple etiqueta="Activar" textoEnviando="Activando…" variante="secundario" alTerminar={alCambiar}
                          operacion={async () => {
                            await mutar(CAMBIAR_ESTADO_LUGAR, { id: l.id, estado: 'ACTIVO' });
                            return `Lugar «${l.nombre}» activado.`;
                          }} />
                      ) : (
                        <AccionSimple etiqueta="Desactivar" textoEnviando="Desactivando…" variante="secundario" alTerminar={alCambiar}
                          confirmacion={`¿Desactivar «${l.nombre}»? No se podrá asignar a eventos nuevos.`}
                          operacion={async () => {
                            await mutar(CAMBIAR_ESTADO_LUGAR, { id: l.id, estado: 'INACTIVO' });
                            return `Lugar «${l.nombre}» desactivado.`;
                          }} />
                      )}
                    </div>
                  </td>}
                </tr>
              ))}
            </Tabla>
          )}
          <Paginacion pagina={pagina.pagina} totalPaginas={pagina.totalPaginas} alCambiar={(p) => f.cambiar({ pagina: p })} />
        </div>
      )}
    </>
  );
}
