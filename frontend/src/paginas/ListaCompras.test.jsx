import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { fichaDemo } from '../test/fichaDemo.js';
import { iniciarSesionDePrueba, renderizarApp, simularPorOperacion } from '../test/utilidades.jsx';

const RECUERDOS = { id: 'i3', nombreItem: 'Recuerdos para invitados', cantidadEstimada: '100 unidades', comprado: false, origen: 'MANUAL', nota: 'Bolsas con dulces' };
const seccion = () => screen.getByRole('region', { name: 'Logística' });
const conCompras = (contenido) => {
  const e = fichaDemo();
  return { ...e, logistica: { ...e.logistica, compras: { totalElementos: contenido.length, contenido } } };
};
const base = () => fichaDemo().logistica.compras.contenido; // Bebidas (conseguido) y Servilletas (pendiente), calculados

describe('Lista de compras (10B3)', () => {
  it('muestra el avance, distingue los ítems manuales y solo deja quitar los manuales', async () => {
    iniciarSesionDePrueba();
    const evento = conCompras([...base(), RECUERDOS]);
    simularPorOperacion({ FichaEvento: { data: { evento } } });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);
    await screen.findByText('Recuerdos para invitados');

    const logistica = seccion();
    expect(logistica.querySelector('.avance-texto').textContent).toBe('1 de 3 conseguidos');
    const barra = within(logistica).getByRole('progressbar', { name: '1 de 3 conseguidos' });
    expect(barra.getAttribute('value')).toBe('1');
    expect(barra.getAttribute('max')).toBe('3');
    expect(within(logistica).getAllByText('Agregado a mano')).toHaveLength(1);
    const filaManual = within(logistica).getByLabelText('Recuerdos para invitados').closest('li');
    expect(within(filaManual).getByText('Agregado a mano')).toBeTruthy();
    expect(within(filaManual).getByText('Bolsas con dulces')).toBeTruthy();
    expect(within(logistica).getAllByRole('button', { name: /^Editar / })).toHaveLength(3);
    expect(within(logistica).getAllByRole('button', { name: 'Quitar' })).toHaveLength(1);
    expect(within(filaManual).getByRole('button', { name: 'Quitar' })).toBeTruthy();
    expect(within(logistica).getByLabelText('Bebidas').checked).toBe(true);
    expect(within(logistica).getByText('Conseguido')).toBeTruthy();
  });

  it('agregar ítem: errores por campo, el 409 del nombre repetido y luego éxito con la ficha recargada', async () => {
    iniciarSesionDePrueba();
    const antes = conCompras(base());
    const despues = conCompras([...base(), { ...RECUERDOS, nota: null }]);
    const { llamadas } = simularPorOperacion({
      FichaEvento: (v, n) => ({ data: { evento: n === 1 ? antes : despues } }),
      AgregarItemCompra: (v, n) => [
        { data: null, errors: [{ message: 'Los datos enviados no son válidos. Revisa los campos indicados.', path: ['agregarItemCompra'],
          extensions: { classification: 'BAD_REQUEST', status: 400, servicio: 'logistica', errores: [
            { campo: 'nombreItem', mensaje: 'no debe estar vacío' }, { campo: 'cantidadEstimada', mensaje: 'no debe estar vacío' }] } }] },
        { data: null, errors: [{ message: 'Ya hay un ítem «bebidas» en la lista de compras de este evento.', path: ['agregarItemCompra'],
          extensions: { classification: 'CONFLICT', status: 409, servicio: 'logistica' } }] },
        { data: { agregarItemCompra: { ...RECUERDOS, nota: null } } },
      ][n - 1],
    });
    renderizarApp(`/eventos/${antes.id}?paso=todo`);
    await screen.findByText('Servilletas');
    expect(seccion().querySelector('.avance-texto').textContent).toBe('1 de 2 conseguidos');

    fireEvent.click(within(seccion()).getByRole('button', { name: 'Agregar ítem' }));
    const formulario = screen.getByRole('form', { name: 'Agregar ítem a la lista de compras' });
    fireEvent.click(within(formulario).getByRole('button', { name: 'Agregar ítem' }));
    expect(await within(formulario).findAllByText('no debe estar vacío')).toHaveLength(2);
    expect(within(formulario).getByLabelText('Nombre del ítem').getAttribute('aria-invalid')).toBe('true');
    expect(within(formulario).getByLabelText('Cantidad o descripción').getAttribute('aria-invalid')).toBe('true');

    fireEvent.change(within(formulario).getByLabelText('Nombre del ítem'), { target: { value: 'bebidas' } });
    fireEvent.change(within(formulario).getByLabelText('Cantidad o descripción'), { target: { value: '10' } });
    fireEvent.click(within(formulario).getByRole('button', { name: 'Agregar ítem' }));
    const alerta = await within(formulario).findByText('Ya hay un ítem «bebidas» en la lista de compras de este evento.');
    expect(alerta.closest('[role="alert"]').textContent).toMatch(/No se puede realizar esta acción en el estado actual/);

    fireEvent.change(within(formulario).getByLabelText('Nombre del ítem'), { target: { value: '  Recuerdos para invitados ' } });
    fireEvent.change(within(formulario).getByLabelText('Cantidad o descripción'), { target: { value: '100 unidades' } });
    fireEvent.change(within(formulario).getByLabelText('Nota (opcional)'), { target: { value: '   ' } });
    fireEvent.click(within(formulario).getByRole('button', { name: 'Agregar ítem' }));

    expect(await screen.findByText('«Recuerdos para invitados» agregado a la lista de compras.')).toBeTruthy();
    expect(llamadas('AgregarItemCompra')[2]).toEqual({ eventoId: antes.id, input: { nombreItem: 'Recuerdos para invitados', cantidadEstimada: '100 unidades', nota: null } });
    expect(llamadas('FichaEvento')).toHaveLength(2); // solo el éxito recarga
    expect(seccion().querySelector('.avance-texto').textContent).toBe('1 de 3 conseguidos');
    expect(within(seccion()).getByText('Agregado a mano')).toBeTruthy();
    expect(screen.queryByRole('form', { name: 'Agregar ítem a la lista de compras' })).toBeNull();
  });

  it('marcar un ítem manual como conseguido, editar uno calculado y quitar el manual con confirmación', async () => {
    iniciarSesionDePrueba();
    const evento = conCompras([...base(), RECUERDOS]);
    const { llamadas } = simularPorOperacion({
      FichaEvento: { data: { evento } },
      ActualizarCompras: { data: { actualizarCompras: { totalElementos: 3 } } },
      EditarItemCompra: (v) => ({ data: { editarItemCompra: { ...base()[1], ...v.input, id: v.itemId } } }),
      QuitarItemCompra: (v) => ({ data: { quitarItemCompra: v.itemId } }),
    });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);
    await screen.findByText('Recuerdos para invitados');

    fireEvent.click(within(seccion()).getByLabelText('Recuerdos para invitados'));
    expect(await screen.findByText('«Recuerdos para invitados» marcado como conseguido.')).toBeTruthy();
    expect(llamadas('ActualizarCompras')[0]).toEqual({ eventoId: evento.id, items: [{ id: 'i3', comprado: true }] });

    fireEvent.click(within(seccion()).getByRole('button', { name: 'Editar Servilletas' }));
    const formulario = screen.getByRole('form', { name: 'Editar el ítem Servilletas' });
    expect(within(formulario).getByLabelText('Cantidad o descripción').value).toBe('315 unidades');
    fireEvent.change(within(formulario).getByLabelText('Cantidad o descripción'), { target: { value: '400 unidades' } });
    fireEvent.click(within(formulario).getByRole('button', { name: 'Guardar cambios' }));
    expect(await screen.findByText('«Servilletas» actualizado en la lista de compras.')).toBeTruthy();
    expect(llamadas('EditarItemCompra')[0]).toEqual({ eventoId: evento.id, itemId: 'i2', input: { nombreItem: 'Servilletas', cantidadEstimada: '400 unidades', nota: null } });

    fireEvent.click(within(seccion()).getByRole('button', { name: 'Quitar' }));
    expect(llamadas('QuitarItemCompra')).toEqual([]); // primero pide confirmación
    expect(screen.getByText('¿Quitar «Recuerdos para invitados» de la lista?')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Sí, quitar' }));
    expect(await screen.findByText('«Recuerdos para invitados» quitado de la lista de compras.')).toBeTruthy();
    expect(llamadas('QuitarItemCompra')).toEqual([{ eventoId: evento.id, itemId: 'i3' }]);
  });

  it('si el evento aún no está confirmado: mensaje claro y sin acciones de compras', async () => {
    iniciarSesionDePrueba();
    const evento = { ...fichaDemo(), estado: 'PENDIENTE', logistica: null };
    simularPorOperacion({ FichaEvento: { data: { evento } } });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);

    expect(await screen.findByText('La lista de compras se genera al confirmar el evento.')).toBeTruthy();
    expect(within(seccion()).queryByRole('button', { name: 'Agregar ítem' })).toBeNull();
    expect(within(seccion()).queryByRole('progressbar')).toBeNull();
  });

  it('un evento cancelado muestra la lista y el avance pero sin acciones', async () => {
    iniciarSesionDePrueba();
    const evento = { ...conCompras([...base(), RECUERDOS]), estado: 'CANCELADO' };
    simularPorOperacion({ FichaEvento: { data: { evento } } });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);
    await screen.findByText('Recuerdos para invitados');

    expect(seccion().querySelector('.avance-texto').textContent).toBe('1 de 3 conseguidos');
    expect(within(seccion()).getByText('Agregado a mano')).toBeTruthy();
    expect(within(seccion()).queryByRole('checkbox')).toBeNull();
    expect(within(seccion()).queryByRole('button', { name: /Agregar ítem|Quitar|^Editar / })).toBeNull();
  });
});
