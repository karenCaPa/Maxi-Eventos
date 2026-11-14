// Guarda el JWT en sessionStorage (decisión D1 de la Fase 10, ver docs/seguridad.md):
// sobrevive a recargar la página, desaparece al cerrar la pestaña y no se comparte con otras pestañas.
// Quien valida el token es el gateway; aquí solo se lee "exp" para no usar un token ya vencido.
const CLAVE = 'maxi-eventos.token';
// Fase 11D: el nombre del usuario (para la barra; no viaja en el token). El rol se lee del claim "rol" del token.
const CLAVE_NOMBRE = 'maxi-eventos.nombre';
export const EVENTO_SESION_VENCIDA = 'maxi-eventos:sesion-vencida';

function almacen() {
  try {
    return window.sessionStorage;
  } catch {
    return null; // almacenamiento bloqueado: la sesión vive solo mientras no se recargue
  }
}

let enMemoria = null;
let nombreEnMemoria = null;

/** Tras el login: el token y el nombre que devolvió la mutation. */
export function guardarSesion(token, nombre) {
  guardarToken(token);
  nombreEnMemoria = nombre ?? null;
  if (nombre) almacen()?.setItem(CLAVE_NOMBRE, nombre);
}

/**
 * { nombre, rol } del usuario con sesión, o null. El rol sale del claim "rol" del token (solo para decidir qué se MUESTRA:
 * quien autoriza de verdad es el backend, el gateway y cada servicio). Un token sin rol (anterior a la Fase 11D) se trata
 * como sin permisos de administrador.
 */
export function obtenerUsuario() {
  const token = obtenerToken();
  if (!token) return null;
  const s = almacen();
  return { nombre: (s ? s.getItem(CLAVE_NOMBRE) : nombreEnMemoria) ?? '', rol: leerCarga(token)?.rol ?? null };
}

export function obtenerToken() {
  const s = almacen();
  const token = s ? s.getItem(CLAVE) : enMemoria;
  if (token && tokenVencido(token)) {
    borrarToken();
    return null;
  }
  return token;
}

export function guardarToken(token) {
  enMemoria = token;
  almacen()?.setItem(CLAVE, token);
}

export function borrarToken() {
  enMemoria = null;
  nombreEnMemoria = null;
  almacen()?.removeItem(CLAVE);
  almacen()?.removeItem(CLAVE_NOMBRE);
}

/** La carga (payload) del JWT, sin validar la firma; null si no tiene la forma de un JWT. */
function leerCarga(token) {
  try {
    const partes = token.split('.');
    if (partes.length !== 3) return null;
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
    const binario = atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='));
    return JSON.parse(new TextDecoder().decode(Uint8Array.from(binario, (c) => c.charCodeAt(0))));
  } catch {
    return null;
  }
}

/** true si el token no tiene la forma de un JWT o su "exp" ya pasó (no valida la firma). */
export function tokenVencido(token, ahora = Date.now()) {
  const carga = leerCarga(token);
  return !carga || typeof carga.exp !== 'number' || carga.exp * 1000 <= ahora;
}

/** Borra el token y avisa a la aplicación (SesionProvider) para volver al login con un mensaje. */
export function avisarSesionVencida(mensaje) {
  borrarToken();
  window.dispatchEvent(new CustomEvent(EVENTO_SESION_VENCIDA, { detail: { mensaje } }));
}
