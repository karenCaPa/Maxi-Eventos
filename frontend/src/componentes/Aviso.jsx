/** Mensaje destacado. tipo: error | info | exito. Los errores se anuncian a los lectores de pantalla (role="alert"). */
export default function Aviso({ tipo = 'info', titulo, children }) {
  return (
    <div className={`aviso aviso-${tipo}`} role={tipo === 'error' ? 'alert' : 'status'}>
      {titulo && <strong className="aviso-titulo">{titulo}</strong>}
      <div>{children}</div>
    </div>
  );
}
