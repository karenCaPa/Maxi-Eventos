import { useState } from 'react';
import { mutar } from '../api/cliente.js';
import { ACTUALIZAR_COMPRAS, QUITAR_ITEM_COMPRA } from '../api/consultas.js';
import { formatearNumero } from '../utils/formato.js';
import AccionSimple from './AccionSimple.jsx';
import Desplegable from './Desplegable.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import FormularioItemCompra from './FormularioItemCompra.jsx';
import { useAccion } from './useAccion.js';

/**
 * Lista de chequeo de compras y suministros (requisito de la Entrega 2; 10B3). Indicador de avance ("3 de 8 conseguidos"),
 * casilla de "conseguido" por ítem (mutation actualizarCompras) y los ítems MANUALES distinguidos de los CALCULADOS por el
 * sistema. Con "editable": agregar ítem, editar (cualquiera) y quitar (solo los manuales; un calculado lo rechaza Logística
 * con 409). Mientras se guarda una marca, las casillas quedan desactivadas; tras cada cambio se recarga la ficha completa.
 */
export default function ListaCompras({ eventoId, items, editable, alTerminar }) {
  const { ejecutar, enviando, error } = useAccion(alTerminar);
  const [itemEnCurso, setItemEnCurso] = useState(null);
  const [editando, setEditando] = useState(null); // id del ítem en edición

  function cambiar(item, comprado) {
    setItemEnCurso(item.id);
    ejecutar(async () => {
      await mutar(ACTUALIZAR_COMPRAS, { eventoId, items: [{ id: item.id, comprado }] });
      return `«${item.nombreItem}» marcado como ${comprado ? 'conseguido' : 'pendiente'}.`;
    }).finally(() => setItemEnCurso(null));
  }

  const lista = items ?? [];
  const conseguidos = lista.filter((c) => c.comprado).length;
  return (
    <>
      {lista.length === 0 ? <p className="vacio">Sin ítems de compra.</p> : (
        <>
          <div className="avance-compras">
            <p className="avance-texto" id={`avance-${eventoId}`}>
              <strong>{formatearNumero(conseguidos)} de {formatearNumero(lista.length)}</strong> conseguidos
            </p>
            <progress value={conseguidos} max={lista.length} aria-labelledby={`avance-${eventoId}`} />
          </div>
          <ul className="compras">
            {lista.map((c) => (
              <li key={c.id} className={`compra-item${c.comprado ? ' comprado' : ''}`}>
                {editando === c.id ? (
                  <div className="panel-formulario compra-edicion">
                    <FormularioItemCompra eventoId={eventoId} item={c} alTerminar={alTerminar} cerrar={() => setEditando(null)} />
                  </div>
                ) : (
                  <>
                    <div className="compra-principal">
                      {editable ? (
                        <span className="compra-casilla">
                          <input id={`compra-${c.id}`} type="checkbox" checked={c.comprado} disabled={enviando}
                            aria-busy={itemEnCurso === c.id || undefined} onChange={(e) => cambiar(c, e.target.checked)} />
                          <label htmlFor={`compra-${c.id}`}>{c.nombreItem}</label>
                        </span>
                      ) : <span className="compra-nombre">{c.nombreItem}</span>}
                      {c.origen === 'MANUAL' && <span className="insignia insignia-manual">Agregado a mano</span>}
                    </div>
                    <div className="compra-meta">
                      <span className="texto-suave">{c.cantidadEstimada}</span>
                      <span className="estado-compra">{itemEnCurso === c.id ? 'Guardando…' : c.comprado ? 'Conseguido' : 'Pendiente'}</span>
                    </div>
                    {c.nota && <p className="compra-nota texto-suave">{c.nota}</p>}
                    {editable && (
                      <div className="acciones-fila compra-acciones">
                        <button type="button" className="boton boton-secundario" onClick={() => setEditando(c.id)}
                          disabled={enviando} aria-label={`Editar ${c.nombreItem}`}>Editar</button>
                        {c.origen === 'MANUAL' && (
                          <AccionSimple etiqueta="Quitar" textoEnviando="Quitando…" variante="secundario" alTerminar={alTerminar}
                            confirmacion={`¿Quitar «${c.nombreItem}» de la lista?`}
                            operacion={async () => {
                              await mutar(QUITAR_ITEM_COMPRA, { eventoId, itemId: c.id });
                              return `«${c.nombreItem}» quitado de la lista de compras.`;
                            }} />
                        )}
                      </div>
                    )}
                  </>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
      <ErrorAccion error={error} />
      {editable && (
        <div className="seccion-acciones">
          <Desplegable etiqueta="Agregar ítem">
            {(cerrar) => <FormularioItemCompra eventoId={eventoId} alTerminar={alTerminar} cerrar={cerrar} />}
          </Desplegable>
        </div>
      )}
    </>
  );
}
