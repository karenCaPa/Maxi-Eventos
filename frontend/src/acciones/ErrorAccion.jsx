import Aviso from '../componentes/Aviso.jsx';

/** Error de una acción con el mensaje en español del servicio (409, 400, 404, 503…). */
export default function ErrorAccion({ error }) {
  if (!error) return null;
  const titulo = error.clasificacion === 'CONFLICT'
    ? 'No se puede realizar esta acción en el estado actual.'
    : error.clasificacion === 'BAD_REQUEST'
      ? 'Revisa los datos.'
      : 'No se pudo realizar la acción.';
  return <Aviso tipo="error" titulo={titulo}>{error.message}</Aviso>;
}
