import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { mutar } from '../api/cliente.js';
import { LOGIN } from '../api/consultas.js';
import { borrarToken, EVENTO_SESION_VENCIDA, guardarSesion, obtenerToken, obtenerUsuario } from './almacen.js';

const SesionContext = createContext(null);

/** Nombres legibles de los roles (Fase 11D). */
export const NOMBRES_ROL = { ADMINISTRADOR: 'Administrador', ASISTENTE: 'Asistente' };

export function SesionProvider({ children }) {
  // Al cargar: obtenerToken() descarta el token si ya venció.
  const [token, setToken] = useState(() => obtenerToken());
  const [aviso, setAviso] = useState(null);

  useEffect(() => {
    const alVencer = (e) => {
      setToken(null);
      setAviso(e.detail?.mensaje ?? null);
    };
    window.addEventListener(EVENTO_SESION_VENCIDA, alVencer);
    return () => window.removeEventListener(EVENTO_SESION_VENCIDA, alVencer);
  }, []);

  const iniciarSesion = useCallback(async (correo, password) => {
    const data = await mutar(LOGIN, { correo, password }, { autenticar: false });
    guardarSesion(data.login.token, data.login.nombre);
    setAviso(null);
    setToken(data.login.token);
  }, []);

  const cerrarSesion = useCallback(() => {
    borrarToken();
    setToken(null);
    setAviso('Cerraste la sesión.');
  }, []);

  const valor = useMemo(() => {
    // Fase 11D: el rol solo decide qué se MUESTRA (menús y botones); quien autoriza es el backend (gateway y servicios).
    const usuario = token ? obtenerUsuario() : null;
    return {
      autenticada: Boolean(token), aviso, iniciarSesion, cerrarSesion, usuario,
      esAdministrador: usuario?.rol === 'ADMINISTRADOR',
    };
  }, [token, aviso, iniciarSesion, cerrarSesion]);
  return <SesionContext.Provider value={valor}>{children}</SesionContext.Provider>;
}

export function useSesion() {
  const contexto = useContext(SesionContext);
  if (!contexto) throw new Error('useSesion debe usarse dentro de SesionProvider');
  return contexto;
}
