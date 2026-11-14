import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import App from '../App.jsx';
import { guardarSesion } from '../sesion/almacen.js';

/**
 * JWT con la forma correcta y "exp" en el futuro (o en el pasado). La firma no importa: la valida el gateway.
 * Fase 11D: claim "rol" (por defecto ADMINISTRADOR; null = token anterior a la 11D, sin rol).
 */
export function tokenDePrueba({ venceEnSegundos = 3600, rol = 'ADMINISTRADOR' } = {}) {
  const b64 = (obj) => btoa(JSON.stringify(obj)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const exp = Math.floor(Date.now() / 1000) + venceEnSegundos;
  const carga = { sub: 'organizadora', iss: 'maxi-eventos-auth', exp, ...(rol ? { rol } : {}) };
  return `${b64({ alg: 'HS256' })}.${b64(carga)}.firma`;
}

export function iniciarSesionDePrueba({ rol = 'ADMINISTRADOR', nombre = 'Organizadora' } = {}) {
  guardarSesion(tokenDePrueba({ rol }), nombre);
}

/** Respuesta HTTP 200 del gateway con el cuerpo GraphQL dado. */
export function respuesta(cuerpo) {
  return Promise.resolve(new Response(JSON.stringify(cuerpo), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

/**
 * Fase 11H: la campanita del Layout consulta su número al montar (y cada 30 s). Las pruebas que no la simulan reciben 0 sin
 * que esa petición cuente en su mock: así sus conteos de peticiones ("una sola petición FichaEvento") no cambian.
 */
const OPERACIONES_CAMPANA = new Set(['NotificacionesSinLeer']);
const nombreOperacion = (opciones) => {
  try {
    return /(?:query|mutation)\s+(\w+)/.exec(JSON.parse(opciones?.body ?? '{}').query ?? '')?.[1] ?? null;
  } catch {
    return null;
  }
};
const CAMPANA_EN_CERO = { data: { notificacionesSinLeer: 0 } };
/**
 * Fase 11I: el listado también pide sus "Eventos pasados". Las pruebas que no la simulan reciben una página vacía (la
 * sección no se muestra), igual que la campanita: sus conteos de peticiones no cambian.
 */
const SIN_PASADOS = { data: { eventos: { pagina: 0, tamanoPagina: 20, totalElementos: 0, totalPaginas: 0, contenido: [] } } };
const POR_DEFECTO = { EventosPasados: SIN_PASADOS };
function conCampana(fetch, simuladas = {}) {
  return (url, opciones = {}) => {
    const nombre = nombreOperacion(opciones);
    if (OPERACIONES_CAMPANA.has(nombre) && simuladas[nombre] === undefined) return respuesta(CAMPANA_EN_CERO);
    if (POR_DEFECTO[nombre] && simuladas[nombre] === undefined) return respuesta(POR_DEFECTO[nombre]);
    return fetch(url, opciones);
  };
}

/** Simula fetch: cada llamada devuelve la siguiente respuesta de la lista. */
export function simularGateway(...cuerpos) {
  const fetch = vi.fn();
  cuerpos.forEach((c) => fetch.mockImplementationOnce(() => respuesta(c)));
  vi.stubGlobal('fetch', conCampana(fetch));
  return fetch;
}

/**
 * Simula el gateway respondiendo según el nombre de la operación GraphQL ("query FichaEvento…" → "FichaEvento").
 * respuestas: { Operacion: cuerpo | (variables, n) => cuerpo | Promise }. Las rutas /documentos se simulan con "documentos".
 * Devuelve el mock y llamadas(nombre) → lista de variables enviadas a esa operación.
 */
export function simularPorOperacion(respuestas) {
  const contador = {};
  const fetch = vi.fn((url, opciones = {}) => {
    if (String(url).includes('/documentos/')) {
      const r = respuestas.documentos;
      return Promise.resolve(typeof r === 'function' ? r(url, opciones) : r);
    }
    const { query, variables } = JSON.parse(opciones.body);
    const nombre = /(?:query|mutation)\s+(\w+)/.exec(query)?.[1] ?? 'anonima';
    contador[nombre] = (contador[nombre] ?? 0) + 1;
    const r = respuestas[nombre];
    if (r === undefined) throw new Error(`Operación no simulada: ${nombre}`);
    const valor = typeof r === 'function' ? r(variables, contador[nombre]) : r;
    return Promise.resolve(valor).then((cuerpo) => (cuerpo instanceof Response ? cuerpo : new Response(JSON.stringify(cuerpo), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    })));
  });
  vi.stubGlobal('fetch', conCampana(fetch, respuestas));
  const llamadas = (nombre) => fetch.mock.calls
    .filter(([u, o]) => !String(u).includes('/documentos/') && new RegExp(`(query|mutation)\\s+${nombre}\\b`).test(JSON.parse(o.body).query))
    .map(([, o]) => JSON.parse(o.body).variables);
  return { fetch, llamadas };
}

/** Promesa que se resuelve a mano: para comprobar el estado "trabajando" mientras se espera la respuesta. */
export function diferida() {
  let resolver;
  const promesa = new Promise((r) => { resolver = r; });
  return { promesa, resolver };
}

/** Cuerpo JSON enviado en la llamada n de fetch. */
export function peticion(fetch, n = 0) {
  return JSON.parse(fetch.mock.calls[n][1].body);
}

export function renderizarApp(ruta = '/') {
  return render(
    <MemoryRouter initialEntries={[ruta]}>
      <App />
    </MemoryRouter>,
  );
}
