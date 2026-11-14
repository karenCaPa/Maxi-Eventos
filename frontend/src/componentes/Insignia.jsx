import { ESTADOS_PASADOS, estadoVisible } from '../utils/fechaPasada.js';
import { ESTADOS_EVENTO, etiqueta } from '../utils/formato.js';

/**
 * Estado del evento con color y texto (el color nunca es la única señal).
 * Fase 11I: con la fecha, un RESERVADO cuya fecha ya pasó se ve "Realizado" y un PENDIENTE, "Vencido" (en gris); el
 * aria-label explica que la fecha pasó. "hoy" se puede pasar en las pruebas.
 */
export function InsigniaEstado({ estado, fecha, hoy }) {
  const visible = fecha ? estadoVisible(estado, fecha, hoy) : estado;
  const pasado = ESTADOS_PASADOS[visible];
  if (pasado) {
    return (
      <span className={`insignia insignia-${visible.toLowerCase()}`} role="img" aria-label={pasado.descripcion}
        title={pasado.descripcion}>
        {pasado.texto}
      </span>
    );
  }
  return <span className={`insignia insignia-${String(estado).toLowerCase()}`}>{etiqueta(ESTADOS_EVENTO, estado)}</span>;
}
