import { useEffect, useState } from 'react';
import { aErrorApi, aErrorVisible, ejecutar, ErrorSesion } from './cliente.js';

/**
 * Ejecuta una query al montar (y cuando cambia "clave") y devuelve { data, error, cargando }.
 * Para las listas auxiliares de los formularios (lugares, proveedores, catálogo, clientes, fechas).
 */
export function useConsulta(query, variables, clave = JSON.stringify(variables ?? {})) {
  const [estado, setEstado] = useState({ data: null, error: null, cargando: true });

  useEffect(() => {
    let vigente = true;
    setEstado((previo) => ({ ...previo, cargando: true, error: null }));
    ejecutar(query, variables ?? {})
      .then(({ data, errors, referencia }) => {
        if (!vigente) return;
        setEstado({ data, error: errors.length ? aErrorApi(errors[0], referencia) : null, cargando: false });
      })
      .catch((err) => {
        if (vigente && !(err instanceof ErrorSesion)) setEstado({ data: null, error: aErrorVisible(err), cargando: false });
      });
    return () => {
      vigente = false;
    };
  }, [query, clave]); // las variables entran por "clave"

  return estado;
}
