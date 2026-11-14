import { NavLink, Outlet } from 'react-router-dom';
import { fechaSimulada } from '../config.js';
import { formatearFecha } from '../utils/formato.js';
import { NOMBRES_ROL, useSesion } from '../sesion/SesionContext.jsx';
import Campana from './Campana.jsx';
import { IconoAgenda, IconoLugar, IconoMas, IconoProveedor, IconoSalir } from './Iconos.jsx';

/** Marca: sello con la inicial y el nombre. Se usa en la barra lateral y en el inicio de sesión. */
export function Marca() {
  return (
    <>
      <span className="marca-sello" aria-hidden="true">M</span>
      <span className="marca-texto">Maxi Eventos<small>Bodas y quince años</small></span>
    </>
  );
}

/**
 * Fase 11I: MODO DEMOSTRACIÓN. Con FRONTEND_FECHA_SIMULADA, la interfaz calcula todo con esa fecha como "hoy"; este aviso
 * fijo en la barra (en todas las pantallas) lo deja claro para que nadie lo confunda con el uso real. Sin la variable no
 * se muestra nada.
 */
export function AvisoFechaSimulada() {
  const fecha = fechaSimulada();
  if (!fecha) return null;
  return (
    <p className="modo-demo" role="note">
      <strong>Modo demostración:</strong> fecha simulada{' '}
      <time dateTime={fecha}>{formatearFecha(fecha, { larga: true }).toLowerCase()}</time>
      <span className="modo-demo-detalle"> ({fecha}). El servidor sigue con la fecha real.</span>
    </p>
  );
}

export default function Layout() {
  const { cerrarSesion, usuario } = useSesion();
  return (
    <div className="app">
      <a className="saltar" href="#contenido">Saltar al contenido</a>
      <header className="lateral">
        <NavLink to="/eventos" className="marca" aria-label="Maxi Eventos, ir a eventos"><Marca /></NavLink>
        <AvisoFechaSimulada />
        <nav aria-label="Principal" className="menu">
          <NavLink to="/eventos/nuevo" className="menu-nuevo"><IconoMas />Nuevo evento</NavLink>
          <NavLink to="/eventos" end><IconoAgenda />Eventos</NavLink>
          <NavLink to="/lugares"><IconoLugar />Lugares</NavLink>
          <NavLink to="/proveedores"><IconoProveedor />Proveedores</NavLink>
        </nav>
        {usuario && (
          <div className="usuario-barra">
            <p className="usuario-actual">
              <span className="usuario-nombre">{usuario.nombre || 'Usuario'}</span>
              <span className="usuario-rol">{NOMBRES_ROL[usuario.rol] ?? 'Sin rol'}</span>
            </p>
            {/* Fase 11H: campanita de notificaciones, para los dos roles */}
            <Campana />
          </div>
        )}
        <button type="button" className="boton boton-enlace" onClick={cerrarSesion}>
          <IconoSalir /><span>Cerrar sesión</span>
        </button>
      </header>
      <main id="contenido" className="contenido" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}
