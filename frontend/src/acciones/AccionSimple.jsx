import { useState } from 'react';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

/**
 * Una acción de un solo botón (generar cotización, crear o firmar contrato, confirmar, cancelar, descargar).
 * operacion: async () => mensaje de éxito. Con "confirmacion", pide un segundo clic explícito antes de ejecutar.
 */
export default function AccionSimple({ etiqueta, textoEnviando, operacion, alTerminar, variante = 'primario', confirmacion, bloqueada }) {
  const { ejecutar, enviando, error } = useAccion(alTerminar);
  const [confirmando, setConfirmando] = useState(false);

  const lanzar = () => {
    setConfirmando(false);
    ejecutar(operacion);
  };

  return (
    <div className="accion">
      {confirmando ? (
        <div className="accion-confirmar" role="group" aria-label={`Confirmar: ${etiqueta}`}>
          <span>{confirmacion}</span>
          <BotonAccion variante="peligro" enviando={enviando} textoEnviando={textoEnviando} onClick={lanzar}>Sí, {etiqueta.toLowerCase()}</BotonAccion>
          <button type="button" className="boton boton-secundario" onClick={() => setConfirmando(false)}>No</button>
        </div>
      ) : (
        <BotonAccion variante={variante} enviando={enviando} textoEnviando={textoEnviando} disabled={bloqueada}
          onClick={confirmacion ? () => setConfirmando(true) : lanzar}>
          {etiqueta}
        </BotonAccion>
      )}
      <ErrorAccion error={error} />
    </div>
  );
}
