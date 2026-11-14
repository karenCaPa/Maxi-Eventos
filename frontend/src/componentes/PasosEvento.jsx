import { PASOS } from '../utils/pasosEvento.js';

/**
 * Camino del evento: los 6 pasos del procedimiento (crear → cotizar → contrato → abonos → confirmar → preparativos).
 * Cada paso es un botón que lleva a su vista. "hecho" = completado; "actual" = el que toca ahora; "elegido" = el que se está viendo.
 */
export default function PasosEvento({ pasos, vista, alElegir }) {
  return (
    <nav aria-label="Pasos del evento">
      <ol className="camino">
        {PASOS.map((p) => {
          const clases = [pasos.hecho[p.id] ? 'hecho' : null, p.id === pasos.actualId ? 'actual' : null, p.id === vista ? 'elegido' : null]
            .filter(Boolean).join(' ');
          return (
            <li key={p.id} className={clases || undefined}>
              <button type="button" className="camino-boton" onClick={() => alElegir(p.id)} aria-current={p.id === vista ? 'step' : undefined}>
                <span className="camino-nombre">{p.nombre}</span>
                <span className="camino-detalle">{pasos.detalle[p.id]}</span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
