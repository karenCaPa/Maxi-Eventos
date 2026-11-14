import { useId, useState } from 'react';

/** Botón que abre un formulario debajo; children recibe cerrar() para cerrarlo tras una acción exitosa. */
export default function Desplegable({ etiqueta, children, bloqueado }) {
  const [abierto, setAbierto] = useState(false);
  const id = useId();
  return (
    <div className="desplegable">
      <button type="button" className="boton boton-secundario" aria-expanded={abierto} aria-controls={id}
        onClick={() => setAbierto((a) => !a)} disabled={bloqueado && !abierto}>
        {abierto ? `Cerrar: ${etiqueta.toLowerCase()}` : etiqueta}
      </button>
      {abierto && (
        <div id={id} className="panel-formulario">
          {children(() => setAbierto(false))}
        </div>
      )}
    </div>
  );
}
