/** Iconos de la navegación (decorativos: el texto de cada enlace ya dice a dónde lleva). */
const base = { className: 'icono', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true, focusable: 'false' };

export const IconoAgenda = () => (
  <svg {...base}><rect x="3.5" y="5" width="17" height="15.5" rx="3" /><path d="M8 3v4M16 3v4M3.5 10h17" /></svg>
);
export const IconoMas = () => (
  <svg {...base}><circle cx="12" cy="12" r="8.5" /><path d="M12 8v8M8 12h8" /></svg>
);
export const IconoLugar = () => (
  <svg {...base}><path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" /><circle cx="12" cy="10" r="2.3" /></svg>
);
export const IconoProveedor = () => (
  <svg {...base}><path d="M4 9.5 12 4l8 5.5V20H4Z" /><path d="M9.5 20v-6h5v6" /></svg>
);
export const IconoSalir = () => (
  <svg {...base}><path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4M10 16l-4-4 4-4M6 12h10" /></svg>
);
