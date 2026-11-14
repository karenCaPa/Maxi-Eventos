/** Lista de pares etiqueta → valor. filas: [[etiqueta, valor], …]; las filas con valor undefined se omiten. */
export default function Datos({ filas }) {
  return (
    <dl className="datos">
      {filas
        .filter(([, valor]) => valor !== undefined)
        .map(([etiqueta, valor]) => (
          <div key={etiqueta} className="dato">
            <dt>{etiqueta}</dt>
            <dd>{valor === null || valor === '' ? '—' : valor}</dd>
          </div>
        ))}
    </dl>
  );
}
