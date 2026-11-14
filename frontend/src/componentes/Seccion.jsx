import { useContext } from 'react';
import Aviso from './Aviso.jsx';
import { VistaPaso } from './VistaPaso.js';

/**
 * Sección de la ficha. Si su servicio falló (error parcial de GraphQL), muestra el aviso SOLO aquí
 * y el resto de la ficha se ve normal. Las acciones van al pie (y no se muestran si la sección falló).
 * paso: en qué paso de la guía se muestra (si no se indica, siempre). En la vista "todo" se muestran todas.
 * ancha: ocupa todo el ancho de la rejilla (tablas de muchas columnas, como Notificaciones y Auditoría).
 */
export default function Seccion({ id, titulo, error, acciones, ancha = false, paso, children }) {
  const vista = useContext(VistaPaso);
  if (paso && vista !== 'todo' && vista !== paso) return null;
  const idTitulo = `seccion-${id}`;
  return (
    <section className={ancha ? 'seccion seccion-ancha' : 'seccion'} aria-labelledby={idTitulo} data-seccion={id}>
      <header className="seccion-cabecera">
        <h2 id={idTitulo}>{titulo}</h2>
      </header>
      {error ? (
        <Aviso tipo="error" titulo="No se pudo cargar esta sección.">
          {error.message}
        </Aviso>
      ) : (
        <>
          {children}
          {acciones && <div className="seccion-acciones">{acciones}</div>}
        </>
      )}
    </section>
  );
}
