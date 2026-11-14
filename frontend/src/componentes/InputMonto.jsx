/**
 * Campo de dinero con puntos de miles mientras se escribe (1500000 → 1.500.000). La coma es el separador decimal
 * (es-CO). Hacia afuera entrega el número "limpio" como texto ("1500000" o "1500000.5"), listo para numeroONulo().
 */
export function agrupar(crudo) {
  if (!crudo) return '';
  const [entero, decimal] = String(crudo).split('.');
  const miles = entero.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return decimal === undefined ? miles : `${miles},${decimal}`;
}

export function limpiar(texto) {
  const normal = String(texto).replace(/\./g, '').replace(',', '.').replace(/[^\d.]/g, '');
  const [entero, ...resto] = normal.split('.');
  const sinCeros = entero.replace(/^0+(?=\d)/, '');
  return resto.length ? `${sinCeros}.${resto.join('').slice(0, 2)}` : sinCeros;
}

export default function InputMonto({ valor, alCambiar, ...resto }) {
  return (
    <input {...resto} type="text" inputMode="decimal" autoComplete="off" value={agrupar(valor)}
      onChange={(e) => alCambiar(limpiar(e.target.value))} />
  );
}
