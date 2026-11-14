import { useSearchParams } from 'react-router-dom';

/** Filtros guardados en la URL (el botón Atrás y recargar los conservan). Cambiar un filtro vuelve a la primera página. */
export function useFiltrosUrl() {
  const [params, setParams] = useSearchParams();
  const leer = (k) => params.get(k) || '';
  const pagina = Math.max(0, Number(params.get('pagina') || 0));
  const cambiar = (nuevos) => {
    const siguiente = new URLSearchParams(params);
    for (const [k, v] of Object.entries(nuevos)) {
      if (v === '' || v === null || v === false || v === 0) siguiente.delete(k);
      else siguiente.set(k, v === true ? '1' : String(v));
    }
    if (!('pagina' in nuevos)) siguiente.delete('pagina');
    setParams(siguiente);
  };
  return { leer, pagina, cambiar, limpiar: () => setParams(new URLSearchParams()), clave: params.toString() };
}
