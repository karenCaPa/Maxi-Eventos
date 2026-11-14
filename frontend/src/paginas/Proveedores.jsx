import { useState } from 'react';
import AccionSimple from '../acciones/AccionSimple.jsx';
import FormularioDirectorioProveedor from '../acciones/FormularioDirectorioProveedor.jsx';
import { mutar } from '../api/cliente.js';
import { CAMBIAR_ESTADO_PROVEEDOR, PROVEEDORES } from '../api/consultas.js';
import { useConsulta } from '../api/useConsulta.js';
import Aviso from '../componentes/Aviso.jsx';
import Campo from '../componentes/Campo.jsx';
import Paginacion from '../componentes/Paginacion.jsx';
import Tabla from '../componentes/Tabla.jsx';
import { useFiltrosUrl } from '../componentes/useFiltrosUrl.js';
import { useSesion } from '../sesion/SesionContext.jsx';
import { esNombreDePrueba } from '../utils/ayudas.js';
import { CATEGORIAS_PROVEEDOR, etiqueta, formatearNumero } from '../utils/formato.js';

const TAMANO = 20;
const ACTIVO = { '': null, si: true, no: false };

/**
 * Directorio de proveedores (query proveedores del gateway) con registro, edición y activar / desactivar (10B3: mutations
 * crearProveedor, actualizarProveedor y cambiarEstadoProveedor). Un nombre repetido lo rechaza proveedores-service (409).
 */
export default function Proveedores() {
  // Fase 11D: el catálogo lo administra el ADMINISTRADOR; el asistente consulta y asigna proveedores desde la ficha.
  const { esAdministrador } = useSesion();
  const f = useFiltrosUrl();
  const prueba = f.leer('prueba') === '1';
  const [recarga, setRecarga] = useState(0);
  const [formulario, setFormulario] = useState(null); // null | { proveedor?: {...} }
  const [exito, setExito] = useState(null);
  const { data, error, cargando } = useConsulta(PROVEEDORES, {
    categoria: f.leer('categoria') || null, activo: ACTIVO[f.leer('activo')] ?? null, page: f.pagina, size: TAMANO,
  }, `${f.clave}|${recarga}`);
  const pagina = data?.proveedores;
  const todos = pagina?.contenido ?? [];
  const visibles = prueba ? todos : todos.filter((p) => !esNombreDePrueba(p.nombre));

  const alCambiar = async (mensaje) => {
    setExito(mensaje);
    setRecarga((r) => r + 1);
  };
  const abrir = (proveedor) => {
    setExito(null);
    setFormulario(proveedor ? { proveedor } : {});
  };

  return (
    <>
      <div className="titulo-pagina">
        <h1>Proveedores</h1>
        {esAdministrador && (
          <button type="button" className="boton boton-primario" onClick={() => abrir(null)}
            aria-expanded={Boolean(formulario && !formulario.proveedor)}>Nuevo proveedor</button>
        )}
      </div>
      {!esAdministrador && <p className="texto-suave">Solo el administrador registra o modifica proveedores. Puedes consultarlos y asignarlos a un evento desde su ficha.</p>}

      {esAdministrador && formulario && (
        <section className="tarjeta panel-directorio" aria-labelledby="titulo-formulario-proveedor">
          <h2 id="titulo-formulario-proveedor">{formulario.proveedor ? `Editar «${formulario.proveedor.nombre}»` : 'Nuevo proveedor'}</h2>
          <FormularioDirectorioProveedor key={formulario.proveedor?.id ?? 'nuevo'} proveedor={formulario.proveedor}
            alGuardar={alCambiar} cerrar={() => setFormulario(null)} />
        </section>
      )}
      <div aria-live="polite">{exito && <Aviso tipo="exito">{exito}</Aviso>}</div>

      <form className="filtros tarjeta" onSubmit={(e) => e.preventDefault()} aria-label="Filtros de proveedores">
        <Campo etiqueta="Categoría">
          {({ id }) => (
            <select id={id} value={f.leer('categoria')} onChange={(e) => f.cambiar({ categoria: e.target.value })}>
              <option value="">Todas</option>
              {Object.entries(CATEGORIAS_PROVEEDOR).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
            </select>
          )}
        </Campo>
        <Campo etiqueta="Estado">
          {({ id }) => (
            <select id={id} value={f.leer('activo')} onChange={(e) => f.cambiar({ activo: e.target.value })}>
              <option value="">Todos</option>
              <option value="si">Activos</option>
              <option value="no">Inactivos</option>
            </select>
          )}
        </Campo>
        <div className="campo campo-casilla">
          <input id="proveedores-prueba" type="checkbox" checked={prueba} onChange={(e) => f.cambiar({ prueba: e.target.checked, pagina: f.pagina })} />
          <label htmlFor="proveedores-prueba">Mostrar proveedores de prueba</label>
        </div>
        <button type="button" className="boton boton-secundario" onClick={f.limpiar} disabled={f.clave === ''}>Limpiar filtros</button>
      </form>

      {error && <Aviso tipo="error" titulo="No se pudo cargar el directorio.">{error.message}</Aviso>}
      {cargando && !pagina && <p className="texto-suave" role="status">Cargando proveedores…</p>}
      {pagina && (
        <div className={cargando ? 'cargando' : undefined} aria-busy={cargando}>
          <p className="texto-suave" role="status">
            {formatearNumero(pagina.totalElementos)} proveedores{todos.length !== visibles.length && ` · ${todos.length - visibles.length} de prueba ocultos en esta página`}
          </p>
          {visibles.length === 0 ? <p className="vacio">No hay proveedores con estos filtros.</p> : (
            <Tabla titulo="Proveedores" columnas={['Nombre', 'Categoría', 'Contacto', 'Estado', ...(esAdministrador ? ['Acciones'] : [])]}>
              {visibles.map((p) => (
                <tr key={p.id}>
                  <td data-etiqueta="Nombre">{p.nombre}</td>
                  <td data-etiqueta="Categoría">{etiqueta(CATEGORIAS_PROVEEDOR, p.categoria)}</td>
                  <td data-etiqueta="Contacto">{p.contacto ?? '—'}</td>
                  <td data-etiqueta="Estado">{p.activo ? 'Activo' : 'Inactivo'}</td>
                  {esAdministrador && <td data-etiqueta="Acciones">
                    <div className="acciones-fila">
                      <button type="button" className="boton boton-secundario" onClick={() => abrir(p)}
                        aria-label={`Editar ${p.nombre}`}>Editar</button>
                      {p.activo ? (
                        <AccionSimple etiqueta="Desactivar" textoEnviando="Desactivando…" variante="secundario" alTerminar={alCambiar}
                          confirmacion={`¿Desactivar «${p.nombre}»? No se podrá asignar a eventos nuevos.`}
                          operacion={async () => {
                            await mutar(CAMBIAR_ESTADO_PROVEEDOR, { id: p.id, activo: false });
                            return `Proveedor «${p.nombre}» desactivado.`;
                          }} />
                      ) : (
                        <AccionSimple etiqueta="Activar" textoEnviando="Activando…" variante="secundario" alTerminar={alCambiar}
                          operacion={async () => {
                            await mutar(CAMBIAR_ESTADO_PROVEEDOR, { id: p.id, activo: true });
                            return `Proveedor «${p.nombre}» activado.`;
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
