import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { obtenerToken } from '../sesion/almacen.js';
import { peticion, renderizarApp, simularGateway, simularPorOperacion, tokenDePrueba } from '../test/utilidades.jsx';

const LISTADO_VACIO = { data: { eventos: { pagina: 0, tamanoPagina: 20, totalElementos: 0, totalPaginas: 0, contenido: [] } } };
const PROXIMOS_VACIO = { data: { eventos: { contenido: [] } } };

function escribirCredenciales(correo, password) {
  fireEvent.change(screen.getByLabelText('Correo'), { target: { value: correo } });
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
}

describe('Login', () => {
  it('correcto: envía la mutation login sin token, guarda el token y abre el listado de eventos', async () => {
    const token = tokenDePrueba();
    const { fetch, llamadas } = simularPorOperacion({
      Login: { data: { login: { token, tipoToken: 'Bearer', expiracionEnSegundos: 28800 } } },
      Eventos: LISTADO_VACIO,
      ProximosEventos: PROXIMOS_VACIO, // Fase 11E: bloque "Próximos 7 días" del listado
    });
    renderizarApp('/eventos'); // sin sesión → redirige al login

    escribirCredenciales('organizadora@maxieventos.com', 'clave-de-prueba');

    expect(await screen.findByRole('heading', { name: 'Eventos' })).toBeTruthy();
    // El título se pinta antes de que el listado haga su consulta (useEffect): se espera a que salga en lugar de suponer
    // que ya salió (con la máquina cargada llegaba un instante después).
    await waitFor(() => expect(llamadas('Eventos')).toHaveLength(1));
    const login = peticion(fetch, 0);
    expect(login.query).toMatch(/mutation Login/);
    expect(login.variables).toEqual({ correo: 'organizadora@maxieventos.com', password: 'clave-de-prueba' });
    expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
    expect(obtenerToken()).toBe(token);
    // Todas las consultas posteriores al login llevan el token
    for (const [, opciones] of fetch.mock.calls.slice(1)) expect(opciones.headers.Authorization).toBe(`Bearer ${token}`);
  });

  it('incorrecto: muestra el mensaje de Auth, no guarda token y sigue en el login', async () => {
    simularGateway({
      data: null,
      errors: [{ message: 'Correo o contraseña incorrectos.', path: ['login'],
        extensions: { classification: 'UNAUTHORIZED', status: 401, servicio: 'auth' } }],
    });
    renderizarApp('/login');

    escribirCredenciales('organizadora@maxieventos.com', 'equivocada');

    expect((await screen.findByRole('alert')).textContent).toBe('Correo o contraseña incorrectos.');
    expect(obtenerToken()).toBeNull();
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeTruthy();
    expect(screen.getByLabelText('Contraseña').value).toBe('');
  });
});
