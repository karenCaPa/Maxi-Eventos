import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';

const OCOBOS = { id: 'b1f0c2d3-0000-4000-8000-000000000001', nombre: 'Salón Los Ocobos', direccion: 'Calle 7 # 4-10', tipo: 'SALON', capacidad: 180, estado: 'ACTIVO' };
const SONIDO = { id: 'c2a1b3d4-0000-4000-8000-000000000002', nombre: 'Sonido y Luces del Huila', categoria: 'SONIDO', contacto: '3001112233', activo: true };
const pagina = (clave, contenido) => ({ data: { [clave]: { pagina: 0, totalElementos: contenido.length, totalPaginas: 1, contenido } } });
// Los filtros de la página tienen etiquetas iguales (Nombre, Tipo, Categoría): se busca dentro del formulario abierto.
const panel = () => screen.getByRole('form', { name: /^(Nuevo|Editar)/ });
const campo = (etiqueta) => within(panel()).getByLabelText(etiqueta);
const cambiar = (etiqueta, value) => fireEvent.change(campo(etiqueta), { target: { value } });
const errorPorCampo = (servicio, errores) => ({ data: null, errors: [{ message: 'Los datos enviados no son válidos. Revisa los campos indicados.',
  path: ['x'], extensions: { classification: 'BAD_REQUEST', status: 400, servicio, errores } }] });

describe('Directorio de lugares (10B3)', () => {
  it('registra un lugar: errores por campo, después éxito y la lista se recarga', async () => {
    iniciarSesionDePrueba();
    const nuevo = { id: 'd3c2b1a0-0000-4000-8000-000000000003', nombre: 'Hacienda Las Ceibas', direccion: 'Km 5 vía Rivera', tipo: 'HACIENDA', capacidad: 250, estado: 'ACTIVO' };
    const { llamadas } = simularPorOperacion({
      Lugares: (v, n) => (n === 1 ? pagina('lugares', [OCOBOS]) : pagina('lugares', [nuevo, OCOBOS])),
      CrearLugar: (v, n) => (n === 1
        ? errorPorCampo('lugares', [{ campo: 'nombre', mensaje: 'no debe estar vacío' }, { campo: 'capacidad', mensaje: 'no debe ser nulo' }])
        : { data: { crearLugar: nuevo } }),
    });
    renderizarApp('/lugares');
    await screen.findByText('Salón Los Ocobos');

    fireEvent.click(screen.getByRole('button', { name: 'Nuevo lugar' }));
    const formulario = screen.getByRole('form', { name: 'Nuevo lugar' });
    fireEvent.click(within(formulario).getByRole('button', { name: 'Registrar lugar' }));
    expect(await screen.findByText('no debe estar vacío')).toBeTruthy();
    expect(screen.getByText('no debe ser nulo')).toBeTruthy();
    expect(within(formulario).getByLabelText('Nombre').getAttribute('aria-invalid')).toBe('true');
    expect(within(formulario).getByLabelText('Capacidad (personas)').getAttribute('aria-invalid')).toBe('true');
    expect(llamadas('CrearLugar')[0].input).toEqual({ nombre: '', direccion: '', tipo: '', capacidad: null });

    cambiar('Nombre', ' Hacienda Las Ceibas ');
    cambiar('Dirección', 'Km 5 vía Rivera');
    cambiar('Tipo', 'HACIENDA');
    cambiar('Capacidad (personas)', '250');
    fireEvent.click(within(formulario).getByRole('button', { name: 'Registrar lugar' }));

    expect(await screen.findByText('Lugar «Hacienda Las Ceibas» registrado.')).toBeTruthy();
    expect(llamadas('CrearLugar')[1].input).toEqual({ nombre: 'Hacienda Las Ceibas', direccion: 'Km 5 vía Rivera', tipo: 'HACIENDA', capacidad: 250 });
    expect(await screen.findByRole('cell', { name: 'Hacienda Las Ceibas' })).toBeTruthy();
    expect(llamadas('Lugares').length).toBe(2);
    expect(screen.queryByRole('form', { name: 'Nuevo lugar' })).toBeNull();
  });

  it('una capacidad que no es entera no se envía', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({ Lugares: pagina('lugares', [OCOBOS]) });
    renderizarApp('/lugares');
    await screen.findByText('Salón Los Ocobos');
    fireEvent.click(screen.getByRole('button', { name: 'Nuevo lugar' }));
    cambiar('Capacidad (personas)', '12.5');
    fireEvent.click(screen.getByRole('button', { name: 'Registrar lugar' }));
    expect(await screen.findByText('debe ser un número entero')).toBeTruthy();
    expect(llamadas('CrearLugar')).toEqual([]);
  });

  it('edita un lugar con sus datos cargados y lo desactiva con confirmación', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({
      Lugares: (v, n) => pagina('lugares', [n >= 3 ? { ...OCOBOS, capacidad: 200, estado: 'INACTIVO' } : n === 2 ? { ...OCOBOS, capacidad: 200 } : OCOBOS]),
      ActualizarLugar: (v) => ({ data: { actualizarLugar: { ...OCOBOS, ...v.input, id: v.id } } }),
      CambiarEstadoLugar: (v) => ({ data: { cambiarEstadoLugar: { ...OCOBOS, capacidad: 200, estado: v.estado } } }),
    });
    renderizarApp('/lugares');
    fireEvent.click(await screen.findByRole('button', { name: 'Editar Salón Los Ocobos' }));
    expect(campo('Nombre').value).toBe('Salón Los Ocobos');
    expect(campo('Tipo').value).toBe('SALON');
    cambiar('Capacidad (personas)', '200');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    expect(await screen.findByText('Lugar «Salón Los Ocobos» actualizado.')).toBeTruthy();
    expect(llamadas('ActualizarLugar')[0]).toEqual({ id: OCOBOS.id, input: { nombre: 'Salón Los Ocobos', direccion: 'Calle 7 # 4-10', tipo: 'SALON', capacidad: 200 } });

    fireEvent.click(await screen.findByRole('button', { name: 'Desactivar' }));
    expect(llamadas('CambiarEstadoLugar')).toEqual([]); // primero pide confirmación
    fireEvent.click(screen.getByRole('button', { name: 'Sí, desactivar' }));
    expect(await screen.findByText('Lugar «Salón Los Ocobos» desactivado.')).toBeTruthy();
    expect(llamadas('CambiarEstadoLugar')[0]).toEqual({ id: OCOBOS.id, estado: 'INACTIVO' });
    expect(await screen.findByRole('button', { name: 'Activar' })).toBeTruthy();
  });
});

describe('Directorio de proveedores (10B3)', () => {
  it('el proveedor duplicado muestra el 409 de Proveedores y no cierra el formulario', async () => {
    iniciarSesionDePrueba();
    const { llamadas } = simularPorOperacion({
      Proveedores: pagina('proveedores', [SONIDO]),
      CrearProveedor: { data: null, errors: [{ message: 'Ya existe un proveedor con el nombre sonido y luces del huila', path: ['crearProveedor'],
        extensions: { classification: 'CONFLICT', status: 409, servicio: 'proveedores' } }] },
    });
    renderizarApp('/proveedores');
    await screen.findByText('Sonido y Luces del Huila');
    fireEvent.click(screen.getByRole('button', { name: 'Nuevo proveedor' }));
    cambiar('Nombre', 'sonido y luces del huila');
    cambiar('Categoría', 'SONIDO');
    cambiar('Contacto', '3009998877');
    fireEvent.click(screen.getByRole('button', { name: 'Registrar proveedor' }));

    const alerta = await screen.findByText('Ya existe un proveedor con el nombre sonido y luces del huila');
    expect(alerta.closest('[role="alert"]').textContent).toMatch(/No se puede realizar esta acción en el estado actual/);
    expect(screen.getByRole('form', { name: 'Nuevo proveedor' })).toBeTruthy();
    expect(llamadas('CrearProveedor')[0].input).toEqual({ nombre: 'sonido y luces del huila', categoria: 'SONIDO', contacto: '3009998877' });
    expect(llamadas('Proveedores').length).toBe(1); // ante un error no se recarga
  });

  it('registra, edita con errores por campo y activa un proveedor inactivo', async () => {
    iniciarSesionDePrueba();
    const flores = { id: 'e4d3c2b1-0000-4000-8000-000000000004', nombre: 'Flores del Huila', categoria: 'FLORISTERIA', contacto: '3104567890', activo: false };
    const { llamadas } = simularPorOperacion({
      Proveedores: (v, n) => pagina('proveedores', n === 1 ? [flores] : [{ ...flores, activo: true }]),
      ActualizarProveedor: errorPorCampo('proveedores', [{ campo: 'contacto', mensaje: 'no debe estar vacío' }]),
      CambiarEstadoProveedor: (v) => ({ data: { cambiarEstadoProveedor: { ...flores, activo: v.activo } } }),
    });
    renderizarApp('/proveedores?prueba=1');
    fireEvent.click(await screen.findByRole('button', { name: 'Editar Flores del Huila' }));
    cambiar('Contacto', '   ');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    expect(await screen.findByText('no debe estar vacío')).toBeTruthy();
    expect(campo('Contacto').getAttribute('aria-invalid')).toBe('true');
    expect(llamadas('ActualizarProveedor')[0]).toEqual({ id: flores.id, input: { nombre: 'Flores del Huila', categoria: 'FLORISTERIA', contacto: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    fireEvent.click(screen.getByRole('button', { name: 'Activar' }));
    expect(await screen.findByText('Proveedor «Flores del Huila» activado.')).toBeTruthy();
    expect(llamadas('CambiarEstadoProveedor')[0]).toEqual({ id: flores.id, activo: true });
    expect(await screen.findByRole('button', { name: 'Desactivar' })).toBeTruthy();
  });
});
