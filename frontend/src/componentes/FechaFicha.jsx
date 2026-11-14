import { fechaSinHora } from '../utils/formato.js';

const diaSemana = new Intl.DateTimeFormat('es-CO', { weekday: 'short', timeZone: 'UTC' });
const mes = new Intl.DateTimeFormat('es-CO', { month: 'short', timeZone: 'UTC' });

/** Ficha de calendario (sáb / 5 / dic). Su color sigue el estado del evento; el texto del estado va aparte en la insignia. */
export default function FechaFicha({ fecha, estado }) {
  const f = fechaSinHora(fecha);
  const limpio = (t) => t.replace('.', '');
  return (
    <time className={`fecha-ficha fecha-${String(estado).toLowerCase()}`} dateTime={fecha} aria-hidden="true">
      <span className="dia-semana">{limpio(diaSemana.format(f))}</span>
      <span className="dia-numero">{f.getUTCDate()}</span>
      <span className="mes">{limpio(mes.format(f))}</span>
    </time>
  );
}
