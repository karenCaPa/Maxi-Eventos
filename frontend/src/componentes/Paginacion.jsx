/** Controles de página (pagina empieza en 0, como la API). "etiqueta" distingue dos paginaciones en la misma página. */
export default function Paginacion({ pagina, totalPaginas, alCambiar, etiqueta = 'Paginación' }) {
  if (!totalPaginas || totalPaginas <= 1) return null;
  return (
    <nav className="paginacion" aria-label={etiqueta}>
      <button type="button" className="boton boton-secundario" onClick={() => alCambiar(pagina - 1)} disabled={pagina <= 0}>
        Anterior
      </button>
      <span aria-live="polite">
        Página {pagina + 1} de {totalPaginas}
      </span>
      <button
        type="button"
        className="boton boton-secundario"
        onClick={() => alCambiar(pagina + 1)}
        disabled={pagina + 1 >= totalPaginas}
      >
        Siguiente
      </button>
    </nav>
  );
}
