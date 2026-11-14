import { useCallback, useEffect, useRef, useState } from 'react';
import { ejecutar } from '../api/cliente.js';
import { NOTIFICACIONES_SIN_LEER } from '../api/consultas.js';
import { EVENTO_NOTIFICACIONES } from '../utils/avisos.js';

/** Cada cuánto se consulta el número de no leídas (solo con la pestaña visible). */
export const INTERVALO_MS = 30_000;
/** Tras un aviso de la ficha, otra consulta un poco después: la notificación la genera Kafka un instante más tarde. */
export const SEGUNDA_CONSULTA_MS = 3_000;

/**
 * Fase 11H: número de notificaciones sin leer del usuario, por polling. Consulta al montar, cada 30 s mientras la pestaña está
 * visible, enseguida al volver a ella (y se pausa al ocultarla) y al recibir el aviso interno de cambios. Un fallo de red no
 * borra el número que ya se mostraba (la sesión vencida la maneja el cliente: vuelve al login).
 */
export function useSinLeer() {
  const [sinLeer, setSinLeerInterno] = useState(0);
  const montado = useRef(true);
  // Un cambio local (marcar una o todas) invalida las consultas que estuvieran en camino: no vuelven a poner el número viejo
  const cambios = useRef(0);
  const setSinLeer = useCallback((valor) => {
    cambios.current += 1;
    setSinLeerInterno(valor);
  }, []);

  const consultar = useCallback(async () => {
    const pedida = cambios.current;
    try {
      const { data } = await ejecutar(NOTIFICACIONES_SIN_LEER);
      if (montado.current && pedida === cambios.current && typeof data?.notificacionesSinLeer === 'number') {
        setSinLeerInterno(data.notificacionesSinLeer);
      }
    } catch {
      // se conserva el último número; la próxima consulta lo corrige
    }
  }, []);

  useEffect(() => {
    montado.current = true;
    let intervalo = null;
    let segunda = null;
    const iniciar = () => { if (intervalo === null) intervalo = setInterval(consultar, INTERVALO_MS); };
    const pausar = () => { clearInterval(intervalo); intervalo = null; };
    const alCambiarVisibilidad = () => {
      if (document.visibilityState === 'hidden') pausar();
      else { consultar(); iniciar(); }
    };
    const alAviso = () => {
      consultar();
      clearTimeout(segunda);
      segunda = setTimeout(consultar, SEGUNDA_CONSULTA_MS);
    };
    consultar();
    if (document.visibilityState !== 'hidden') iniciar();
    document.addEventListener('visibilitychange', alCambiarVisibilidad);
    window.addEventListener(EVENTO_NOTIFICACIONES, alAviso);
    return () => {
      montado.current = false;
      pausar();
      clearTimeout(segunda);
      document.removeEventListener('visibilitychange', alCambiarVisibilidad);
      window.removeEventListener(EVENTO_NOTIFICACIONES, alAviso);
    };
  }, [consultar]);

  return { sinLeer, setSinLeer, consultar };
}
