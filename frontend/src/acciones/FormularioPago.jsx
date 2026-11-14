import { useId, useState } from 'react';
import { mutar } from '../api/cliente.js';
import { REGISTRAR_PAGO } from '../api/consultas.js';
import Campo from '../componentes/Campo.jsx';
import InputMonto from '../componentes/InputMonto.jsx';
import { numeroONulo, nuevaClave } from '../utils/ayudas.js';
import { formatearMonto } from '../utils/formato.js';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

const SUGERENCIAS = ['Efectivo', 'Transferencia', 'Tarjeta'];

/**
 * Registrar un abono. La clave de idempotencia se crea UNA vez por formulario: si la respuesta se pierde y se reintenta,
 * Finanzas devuelve el mismo pago (no cobra dos veces). Las reglas (monto > 0, no superar el saldo, moneda…) son de Finanzas.
 */
export default function FormularioPago({ evento, alTerminar, cerrar }) {
  const [clave] = useState(nuevaClave);
  const [monto, setMonto] = useState('');
  const [metodo, setMetodo] = useState('');
  const idLista = useId();
  const { ejecutar, enviando, error } = useAccion(async (mensaje) => {
    await alTerminar(mensaje);
    cerrar();
  });
  const moneda = evento.cotizacion?.moneda ?? evento.balance?.moneda ?? 'COP';
  const balance = evento.balance;
  const faltaDeposito = balance ? Math.max(0, Number(balance.depositoInicialRequerido ?? 0) - Number(balance.montoAbonado)) : null;

  function enviar(e) {
    e.preventDefault();
    ejecutar(async () => {
      const datos = await mutar(REGISTRAR_PAGO, {
        input: {
          eventoId: evento.id,
          clienteId: evento.clienteId,
          monto: numeroONulo(monto) ?? 0,
          moneda,
          metodoPago: metodo.trim(),
          claveIdempotencia: clave,
        },
      });
      return `Pago de ${formatearMonto(datos.registrarPago.monto, moneda)} registrado.`;
    });
  }

  const campos = error?.campos ?? {};
  return (
    <form onSubmit={enviar} noValidate aria-label="Registrar pago">
      {balance && (
        <p className="texto-suave">
          Saldo pendiente: {formatearMonto(balance.saldoPendiente, moneda)}
          {faltaDeposito > 0 && ` · Faltan ${formatearMonto(faltaDeposito, moneda)} para cubrir el depósito`}
        </p>
      )}
      <Campo etiqueta={`Monto (${moneda})`} error={campos.monto}>
        {({ id, invalido, describedBy }) => (
          <InputMonto id={id} valor={monto} alCambiar={setMonto}
            aria-invalid={invalido} aria-describedby={describedBy} />
        )}
      </Campo>
      <Campo etiqueta="Método de pago" error={campos.metodoPago} ayuda="Elige una sugerencia o escribe otro método.">
        {({ id, invalido, describedBy }) => (
          <input id={id} list={idLista} value={metodo} onChange={(e) => setMetodo(e.target.value)} autoComplete="off"
            aria-invalid={invalido} aria-describedby={describedBy} />
        )}
      </Campo>
      <datalist id={idLista}>{SUGERENCIAS.map((s) => <option key={s} value={s} />)}</datalist>
      <ErrorAccion error={error} />
      <BotonAccion type="submit" enviando={enviando} textoEnviando="Registrando pago…">Registrar pago</BotonAccion>
      <p className="texto-suave texto-pequeno">Protección contra doble cobro: clave {clave}</p>
    </form>
  );
}
