import { ESTADOS_CONTRATO, etiqueta, formatearMonto } from '../utils/formato.js';

const SIN_DATO = '—';

/**
 * Franja de resumen de la cabecera de la ficha. Sale de la MISMA query de la ficha (balance y contrato), sin
 * peticiones nuevas. Si la sección de la que sale un dato falló, ese dato muestra "—".
 * "Cubierto" solo informa de la comparación abonado ≥ depósito; quien decide si se puede confirmar es Eventos.
 */
export default function ResumenFicha({ evento, secciones }) {
  const balance = secciones.balance ? null : evento.balance;
  const moneda = balance?.moneda ?? evento.cotizacion?.moneda ?? 'COP';
  const monto = (valor) => (balance ? formatearMonto(valor, moneda) : SIN_DATO);

  let contrato = SIN_DATO;
  if (!secciones.contrato) contrato = evento.contrato ? etiqueta(ESTADOS_CONTRATO, evento.contrato.estado) : 'Sin contrato';

  const deposito = balance?.depositoInicialRequerido;
  let estadoDeposito = null;
  if (balance && deposito !== null && deposito !== undefined) {
    const faltante = Number(deposito) - Number(balance.montoAbonado);
    estadoDeposito = faltante <= 0
      ? <small className="cubierto">Cubierto</small>
      : <small className="no-cubierto">Faltan {formatearMonto(faltante, moneda)}</small>;
  }

  return (
    <dl className="resumen" aria-label="Resumen del evento">
      <div className="resumen-dato">
        <dt>Total con adicionales</dt>
        <dd>{monto(balance?.totalCotizado)}</dd>
      </div>
      <div className="resumen-dato">
        <dt>Total abonado</dt>
        <dd>{monto(balance?.montoAbonado)}</dd>
      </div>
      <div className="resumen-dato resumen-destacado">
        <dt>Saldo pendiente</dt>
        <dd>{monto(balance?.saldoPendiente)}</dd>
      </div>
      <div className="resumen-dato">
        <dt>Contrato</dt>
        <dd>{contrato}</dd>
      </div>
      <div className="resumen-dato">
        <dt>Depósito requerido</dt>
        <dd>
          {balance && deposito !== null && deposito !== undefined ? formatearMonto(deposito, moneda) : SIN_DATO}
          {estadoDeposito}
        </dd>
      </div>
    </dl>
  );
}
