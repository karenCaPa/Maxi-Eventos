import { useCallback, useRef, useState } from 'react';
import { aErrorVisible, ErrorSesion } from '../api/cliente.js';

/**
 * Estado de una acción: "enviando" mientras espera la respuesta (los botones se desactivan: sin doble clic) y el error
 * del servicio (mensaje en español y errores por campo). Si sale bien, llama a alExito (la ficha se recarga completa).
 * Un ErrorSesion no se muestra aquí: la aplicación ya vuelve al login.
 */
export function useAccion(alExito) {
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState(null);
  const enCurso = useRef(false);

  const ejecutar = useCallback(async (operacion) => {
    if (enCurso.current) return undefined; // segundo clic mientras espera: se ignora
    enCurso.current = true;
    setEnviando(true);
    setError(null);
    try {
      const resultado = await operacion();
      if (alExito) await alExito(resultado);
      return resultado;
    } catch (err) {
      if (!(err instanceof ErrorSesion)) setError(aErrorVisible(err));
      return undefined;
    } finally {
      enCurso.current = false;
      setEnviando(false);
    }
  }, [alExito]);

  return { ejecutar, enviando, error, limpiarError: () => setError(null) };
}
