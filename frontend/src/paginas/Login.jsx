import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { aErrorVisible } from '../api/cliente.js';
import Aviso from '../componentes/Aviso.jsx';
import Campo from '../componentes/Campo.jsx';
import { Marca } from '../componentes/Layout.jsx';
import { useSesion } from '../sesion/SesionContext.jsx';

export default function Login() {
  const { autenticada, aviso, iniciarSesion } = useSesion();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const destino = ubicacion.state?.desde ?? '/eventos';

  const [correo, setCorreo] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);

  if (autenticada) return <Navigate to={destino} replace />;

  async function enviar(e) {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await iniciarSesion(correo.trim(), password);
      navegar(destino, { replace: true });
    } catch (err) {
      setError(aErrorVisible(err)); // credenciales incorrectas: el mensaje de Auth; técnico: el genérico con Ref
      setPassword('');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="login" id="contenido">
      <div className="login-marca">
        <div className="marca"><Marca /></div>
        <div>
          <p className="login-lema">Cada fecha, contrato y pago bajo control.</p>
          <p className="login-apoyo">Agenda de eventos, cotizaciones y logística en un solo lugar.</p>
        </div>
      </div>
      <div className="login-panel">
      <form className="tarjeta login-tarjeta" onSubmit={enviar} noValidate>
        <h1>Te damos la bienvenida</h1>
        <p className="texto-suave">Ingresa con tu correo y contraseña.</p>
        {aviso && !error && <Aviso tipo="info">{aviso}</Aviso>}
        {error && <Aviso tipo="error">{error.message}</Aviso>}
        <Campo etiqueta="Correo" error={error?.campos?.correo}>
          {({ id, invalido, describedBy }) => (
            <input id={id} type="email" autoComplete="username" required value={correo}
              onChange={(e) => setCorreo(e.target.value)} aria-invalid={invalido} aria-describedby={describedBy} />
          )}
        </Campo>
        <Campo etiqueta="Contraseña" error={error?.campos?.password}>
          {({ id, invalido, describedBy }) => (
            <input id={id} type="password" autoComplete="current-password" required value={password}
              onChange={(e) => setPassword(e.target.value)} aria-invalid={invalido} aria-describedby={describedBy} />
          )}
        </Campo>
        <button type="submit" className="boton boton-primario boton-bloque" disabled={enviando || !correo || !password}>
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
      </div>
    </main>
  );
}
