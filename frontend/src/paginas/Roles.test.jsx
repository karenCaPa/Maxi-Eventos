import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { fichaDemo } from '../test/fichaDemo.js';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion, tokenDePrueba } from '../test/utilidades.jsx';

/**
 * Fase 11D: la interfaz OCULTA lo que el rol no puede usar y muestra quién tiene la sesión. Es solo comodidad: quien
 * autoriza es el backend (FORBIDDEN en el gateway y 403 en los servicios).
 */
const pagina = (campo, contenido) => ({ data: { [campo]: { pagina: 0, totalElementos: contenido.length, totalPaginas: 1, contenido } } });
const OCOBOS = { id: 'l-1', nombre: 'Salón de Eventos Los Ocobos', direccion: 'Calle 21', tipo: 'SALON', capacidad: 150, estado: 'ACTIVO' };
const SONIDO = { id: 'p-1', nombre: 'Sonido y Luces del Huila', categoria: 'SONIDO', contacto: '3110000000', activo: true };
const barra = () => screen.getByRole('banner');

describe('Roles en la interfaz (Fase 11D)', () => {
  it('asistente: la barra muestra su nombre y su rol; Lugares y Proveedores solo se consultan', async () => {
    iniciarSesionDePrueba({ rol: 'ASISTENTE', nombre: 'Carolina Asistente' });
    simularPorOperacion({ Lugares: pagina('lugares', [OCOBOS]), Proveedores: pagina('proveedores', [SONIDO]) });
    renderizarApp('/lugares');

    expect(await screen.findByText('Salón de Eventos Los Ocobos')).toBeTruthy();
    expect(within(barra()).getByText('Carolina Asistente')).toBeTruthy();
    expect(within(barra()).getByText('Asistente')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Nuevo lugar' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Editar/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Desactivar' })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Acciones' })).toBeNull();
    expect(screen.getByText(/Solo el administrador registra o modifica lugares/)).toBeTruthy();

    fireEvent.click(within(barra()).getByRole('link', { name: /Proveedores/ }));
    expect(await screen.findByText('Sonido y Luces del Huila')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Nuevo proveedor' })).toBeNull();
    expect(screen.queryByRole('button', { name: /Editar/ })).toBeNull();
    expect(screen.getByText(/Solo el administrador registra o modifica proveedores/)).toBeTruthy();
  });

  it('administrador: ve su rol y los controles de administración de los directorios', async () => {
    iniciarSesionDePrueba({ rol: 'ADMINISTRADOR', nombre: 'Organizadora' });
    simularPorOperacion({ Lugares: pagina('lugares', [OCOBOS]) });
    renderizarApp('/lugares');

    expect(await screen.findByText('Salón de Eventos Los Ocobos')).toBeTruthy();
    expect(within(barra()).getByText('Administrador')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Nuevo lugar' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Editar Salón de Eventos Los Ocobos' })).toBeTruthy();
    expect(screen.queryByText(/Solo el administrador/)).toBeNull();
  });

  it('asistente: la ficha NO pide la auditoría (verAuditoria: false) y no muestra ni la sección ni un error por ella', async () => {
    iniciarSesionDePrueba({ rol: 'ASISTENTE', nombre: 'Carolina Asistente' });
    const { auditoria, ...sinAuditoria } = fichaDemo(); // el gateway no la devuelve si no se pide
    const { llamadas } = simularPorOperacion({ FichaEvento: { data: { evento: sinAuditoria } } });
    renderizarApp(`/eventos/${sinAuditoria.id}?paso=todo`);

    expect(await screen.findByRole('heading', { level: 2, name: 'Notificaciones' })).toBeTruthy();
    expect(llamadas('FichaEvento')).toEqual([{ id: sinAuditoria.id, verAuditoria: false }]);
    expect(screen.queryByRole('heading', { level: 2, name: 'Auditoría' })).toBeNull();
    expect(screen.queryAllByRole('alert')).toHaveLength(0);
    expect(auditoria).toBeTruthy(); // (la del administrador sí existe en los datos de prueba)
  });

  it('un token sin rol (anterior a la 11D) se trata como sin permisos de administrador', async () => {
    guardarSinRol();
    simularPorOperacion({ Lugares: pagina('lugares', [OCOBOS]) });
    renderizarApp('/lugares');

    expect(await screen.findByText('Salón de Eventos Los Ocobos')).toBeTruthy();
    expect(within(barra()).getByText('Sin rol')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Nuevo lugar' })).toBeNull();
  });

  it('el login guarda el nombre que devuelve el gateway y la barra lo muestra con el rol del token', async () => {
    const token = tokenDePrueba({ rol: 'ASISTENTE' });
    simularPorOperacion({
      Login: { data: { login: { token, tipoToken: 'Bearer', expiracionEnSegundos: 28800, nombre: 'Carolina Asistente', rol: 'ASISTENTE' } } },
      Eventos: { data: { eventos: { pagina: 0, tamanoPagina: 20, totalElementos: 0, totalPaginas: 0, contenido: [] } } },
      ProximosEventos: { data: { eventos: { contenido: [] } } }, // Fase 11E
    });
    renderizarApp('/eventos');
    fireEvent.change(screen.getByLabelText('Correo'), { target: { value: 'asistente@maxieventos.com' } });
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'clave' } });
    fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('heading', { name: 'Eventos' })).toBeTruthy();
    expect(within(barra()).getByText('Carolina Asistente')).toBeTruthy();
    expect(within(barra()).getByText('Asistente')).toBeTruthy();

    fireEvent.click(within(barra()).getByRole('button', { name: /Cerrar sesión/ }));
    expect(window.sessionStorage.getItem('maxi-eventos.nombre')).toBeNull(); // se borra con el token
  });
});

function guardarSinRol() {
  window.sessionStorage.setItem('maxi-eventos.token', tokenDePrueba({ rol: null }));
}
