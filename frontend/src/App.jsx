import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Layout from './componentes/Layout.jsx';
import Eventos from './paginas/Eventos.jsx';
import FichaEvento from './paginas/FichaEvento.jsx';
import Login from './paginas/Login.jsx';
import Lugares from './paginas/Lugares.jsx';
import NuevoEvento from './paginas/NuevoEvento.jsx';
import Proveedores from './paginas/Proveedores.jsx';
import { SesionProvider, useSesion } from './sesion/SesionContext.jsx';

/** Sin sesión → al login, recordando a dónde quería ir. */
function RutaProtegida({ children }) {
  const { autenticada } = useSesion();
  const ubicacion = useLocation();
  if (!autenticada) return <Navigate to="/login" replace state={{ desde: ubicacion.pathname + ubicacion.search }} />;
  return children;
}

function NoEncontrada() {
  return (
    <>
      <h1>Página no encontrada</h1>
      <p>La dirección no existe. <Link to="/eventos">Ir a eventos</Link></p>
    </>
  );
}

/** Rutas de la aplicación (el Router lo pone main.jsx; las pruebas usan MemoryRouter). */
export default function App() {
  return (
    <SesionProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<RutaProtegida><Layout /></RutaProtegida>}>
          <Route index element={<Navigate to="/eventos" replace />} />
          <Route path="/eventos" element={<Eventos />} />
          <Route path="/eventos/nuevo" element={<NuevoEvento />} />
          <Route path="/eventos/:id" element={<FichaEvento />} />
          <Route path="/lugares" element={<Lugares />} />
          <Route path="/proveedores" element={<Proveedores />} />
          <Route path="*" element={<NoEncontrada />} />
        </Route>
      </Routes>
    </SesionProvider>
  );
}
