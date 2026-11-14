import { useId } from 'react';

/**
 * Campo de formulario con su etiqueta y el error del servidor junto al campo (extensions.errores).
 * children recibe { id, invalido, describedBy } para conectarlo al control.
 */
export default function Campo({ etiqueta, error, ayuda, children }) {
  const id = useId();
  const idError = `${id}-error`;
  const idAyuda = `${id}-ayuda`;
  const describedBy = [ayuda ? idAyuda : null, error ? idError : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className={`campo${error ? ' campo-invalido' : ''}`}>
      <label htmlFor={id}>{etiqueta}</label>
      {children({ id, invalido: Boolean(error), describedBy })}
      {ayuda && <small id={idAyuda} className="campo-ayuda">{ayuda}</small>}
      {error && <small id={idError} className="campo-error">{error}</small>}
    </div>
  );
}
