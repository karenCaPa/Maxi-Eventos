/**
 * Tabla de datos de la interfaz (10B3d). Va SIEMPRE dentro de su propio contenedor con desplazamiento horizontal: si las
 * columnas no caben en la tarjeta, se desplaza la tabla y no la página, y nada se sale del recuadro.
 * En pantallas estrechas (≤ 40rem) cada fila se apila como una tarjeta: cada <td> lleva data-etiqueta con su columna.
 * El contenedor es una región con nombre y enfocable, para poder desplazarlo con el teclado.
 */
export default function Tabla({ titulo, columnas, children }) {
  return (
    <div className="tabla-contenedor" role="region" aria-label={titulo} tabIndex={0}>
      <table className="tabla tabla-apilable">
        <caption className="solo-lectores">{titulo}</caption>
        <thead>
          <tr>{columnas.map((c) => <th key={c} scope="col">{c}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
