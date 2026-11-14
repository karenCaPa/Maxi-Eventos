import { useState } from 'react';
import { mutar } from '../api/cliente.js';
import { AGREGAR_ITEM_COMPRA, EDITAR_ITEM_COMPRA } from '../api/consultas.js';
import Campo from '../componentes/Campo.jsx';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

/**
 * Agregar un ítem a la lista de compras (sin "item") o editarlo (calculado o manual). Las reglas (obligatorios, largo,
 * nombre repetido en el evento → 409) son de logistica-service; sus errores se muestran junto a cada campo o con el mensaje.
 * Al terminar bien, alTerminar recarga la ficha completa.
 */
export default function FormularioItemCompra({ eventoId, item, alTerminar, cerrar }) {
  const [datos, setDatos] = useState(() => ({
    nombreItem: item?.nombreItem ?? '', cantidadEstimada: item?.cantidadEstimada ?? '', nota: item?.nota ?? '',
  }));
  const { ejecutar, enviando, error } = useAccion(async (mensaje) => {
    await alTerminar(mensaje);
    cerrar();
  });
  const cambiar = (campo) => (e) => setDatos((d) => ({ ...d, [campo]: e.target.value }));

  function enviar(e) {
    e.preventDefault();
    const input = {
      nombreItem: datos.nombreItem.trim(), cantidadEstimada: datos.cantidadEstimada.trim(),
      nota: datos.nota.trim() === '' ? null : datos.nota.trim(),
    };
    ejecutar(async () => {
      if (item) {
        const r = await mutar(EDITAR_ITEM_COMPRA, { eventoId, itemId: item.id, input });
        return `«${r.editarItemCompra.nombreItem}» actualizado en la lista de compras.`;
      }
      const r = await mutar(AGREGAR_ITEM_COMPRA, { eventoId, input });
      return `«${r.agregarItemCompra.nombreItem}» agregado a la lista de compras.`;
    });
  }

  const campos = error?.campos ?? {};
  return (
    <form onSubmit={enviar} noValidate aria-label={item ? `Editar el ítem ${item.nombreItem}` : 'Agregar ítem a la lista de compras'}>
      <div className="rejilla-campos">
        <Campo etiqueta="Nombre del ítem" error={campos.nombreItem}>
          {({ id, invalido, describedBy }) => <input id={id} value={datos.nombreItem} onChange={cambiar('nombreItem')} autoComplete="off"
            maxLength={120} aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
        <Campo etiqueta="Cantidad o descripción" error={campos.cantidadEstimada} ayuda="Por ejemplo: 100 unidades, una por mesa.">
          {({ id, invalido, describedBy }) => <input id={id} value={datos.cantidadEstimada} onChange={cambiar('cantidadEstimada')}
            autoComplete="off" maxLength={80} aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
        <Campo etiqueta="Nota (opcional)" error={campos.nota}>
          {({ id, invalido, describedBy }) => <input id={id} value={datos.nota} onChange={cambiar('nota')} autoComplete="off"
            maxLength={255} aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
      </div>
      <ErrorAccion error={error} />
      <div className="acciones-formulario">
        <BotonAccion type="submit" enviando={enviando} textoEnviando="Guardando…">{item ? 'Guardar cambios' : 'Agregar ítem'}</BotonAccion>
        <button type="button" className="boton boton-secundario" onClick={cerrar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  );
}
