import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { obtenerToken } from '../sesion/almacen.js';
import { fichaDemo } from '../test/fichaDemo.js';
import { iniciarSesionDePrueba, peticion, renderizarApp, simularGateway } from '../test/utilidades.jsx';

const seccion = (titulo) => screen.getByRole('region', { name: titulo });
const SECCIONES = ['Cliente', 'Lugar', 'Cotización', 'Balance', 'Contrato', 'Pagos', 'Servicios adicionales',
  'Proveedores asignados', 'Logística', 'Notificaciones', 'Auditoría'];

describe('Ficha del evento', () => {
  it('muestra todas las secciones con UNA sola query, y el fallo de un servicio solo en su sección', async () => {
    iniciarSesionDePrueba();
    const evento = { ...fichaDemo(), contrato: null };
    const fetch = simularGateway({
      data: { evento },
      errors: [{
        message: 'El servicio de contratos no está disponible en este momento. Intenta de nuevo más tarde.',
        path: ['evento', 'contrato'],
        extensions: { classification: 'SERVICE_UNAVAILABLE', status: 503, servicio: 'contratos' },
      }],
    });

    renderizarApp(`/eventos/${evento.id}?paso=todo`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Quince años de Lucía' })).toBeTruthy();
    // una sola petición, la query de la ficha con el id del evento
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(peticion(fetch).query).toMatch(/query FichaEvento/);
    expect(peticion(fetch).variables).toEqual({ id: evento.id, verAuditoria: true }); // Fase 11D: administrador

    for (const titulo of SECCIONES) expect(seccion(titulo)).toBeTruthy();

    // el aviso aparece SOLO en Contrato, genérico y con la referencia de la petición (Fase 11C); el detalle técnico no se ve
    const ref = fetch.mock.calls[0][1].headers['X-Request-Id'].replace(/-/g, '').slice(0, 8).toUpperCase();
    const aviso = within(seccion('Contrato')).getByRole('alert').textContent;
    expect(aviso).toBe(`No se pudo cargar esta sección.No se pudo cargar esta información. Intenta de nuevo en unos minutos. Ref: ${ref}`);
    expect(aviso).not.toMatch(/contratos|disponible|503/);
    expect(screen.getAllByRole('alert')).toHaveLength(1);

    // el resto se ve normal, con formatos es-CO
    expect(within(seccion('Cliente')).getByText('Lucía Fernández Ospina')).toBeTruthy();
    expect(within(seccion('Lugar')).getByText('Salón de Eventos Los Ocobos')).toBeTruthy();
    expect(within(seccion('Balance')).getByText(/6\.270\.000/)).toBeTruthy();
    expect(within(seccion('Cotización')).getByText(/8\.880\.000/)).toBeTruthy();
    expect(within(seccion('Pagos')).getAllByRole('row')).toHaveLength(3); // cabecera + 2 pagos
    expect(within(seccion('Logística')).getByText('Servilletas')).toBeTruthy();
    expect(within(seccion('Logística')).getByText('Pendiente')).toBeTruthy();
    expect(within(seccion('Proveedores asignados')).getByText('Sonido y Luces del Huila')).toBeTruthy();
    expect(within(seccion('Notificaciones')).getByText('Evento confirmado: Quince años de Lucía')).toBeTruthy();
    expect(screen.getByText('Sábado, 5 de diciembre de 2026')).toBeTruthy();
    expect(screen.getByText('Reservado')).toBeTruthy();

    // franja de resumen: de la misma query; el contrato falló → "—"
    const resumen = within(screen.getByLabelText('Resumen del evento'));
    expect(resumen.getByText('Saldo pendiente').nextSibling.textContent).toMatch(/6\.270\.000/);
    expect(resumen.getByText('Total con adicionales').nextSibling.textContent).toMatch(/8\.880\.000/);
    expect(resumen.getByText('Total abonado').nextSibling.textContent).toMatch(/2\.610\.000/);
    expect(resumen.getByText('Contrato').nextSibling.textContent).toBe('—');
    expect(resumen.getByText('Depósito requerido').nextSibling.textContent).toMatch(/2\.610\.000\s*Cubierto/);
  });

  it('resumen: si el balance falla sus datos muestran "—"; etiquetas legibles en las secciones', async () => {
    iniciarSesionDePrueba();
    const evento = { ...fichaDemo(), balance: null };
    evento.pagos.contenido[1].metodoPago = 'NEQUI';
    const fetch = simularGateway({
      data: { evento },
      errors: [{ message: 'El servicio de finanzas no está disponible en este momento. Intenta de nuevo más tarde.',
        path: ['evento', 'balance'], extensions: { classification: 'SERVICE_UNAVAILABLE', status: 503, servicio: 'finanzas' } }],
    });

    renderizarApp(`/eventos/${evento.id}?paso=todo`);
    const resumen = within(await screen.findByLabelText('Resumen del evento'));

    for (const dato of ['Total con adicionales', 'Total abonado', 'Saldo pendiente', 'Depósito requerido']) {
      expect(resumen.getByText(dato).nextSibling.textContent).toBe('—');
    }
    expect(resumen.getByText('Contrato').nextSibling.textContent).toBe('Firmado');
    expect(fetch).toHaveBeenCalledTimes(1); // sin peticiones nuevas
    expect(within(seccion('Balance')).getByRole('alert').textContent).toMatch(/No se pudo cargar esta información\. Intenta de nuevo en unos minutos\. Ref: [0-9A-F]{8}$/);
    expect(within(seccion('Balance')).getByRole('alert').textContent).not.toMatch(/finanzas/);

    expect(within(seccion('Lugar')).getByText('Salón')).toBeTruthy();
    expect(within(seccion('Pagos')).getByText('Transferencia')).toBeTruthy();
    expect(within(seccion('Pagos')).getByText('Nequi')).toBeTruthy();
    expect(within(seccion('Proveedores asignados')).getByText('· Sonido')).toBeTruthy();
    expect(within(seccion('Proveedores asignados')).getByText('Confirmado')).toBeTruthy();
    expect(within(seccion('Contrato')).getByText('Firmado')).toBeTruthy();
    expect(within(seccion('Auditoría')).getByText('Evento confirmado')).toBeTruthy();
    expect(within(seccion('Auditoría')).getByRole('cell', { name: 'Eventos' })).toBeTruthy(); // columna Origen
  });

  it('toda tabla de la ficha va dentro de su contenedor con desplazamiento horizontal (región con nombre, enfocable)', async () => {
    iniciarSesionDePrueba();
    const evento = fichaDemo();
    simularGateway({ data: { evento } });
    renderizarApp(`/eventos/${evento.id}?paso=todo`);
    await screen.findByRole('heading', { level: 1, name: evento.nombreEvento });

    const tablas = screen.getAllByRole('table');
    // Pagos, Servicios adicionales, Proveedores asignados, Notificaciones y Auditoría
    expect(tablas.map((t) => t.closest('section').getAttribute('data-seccion')))
      .toEqual(['pagos', 'serviciosAdicionales', 'proveedores', 'notificaciones', 'auditoria']);
    for (const tabla of tablas) {
      const contenedor = tabla.parentElement;
      expect(contenedor.className).toBe('tabla-contenedor');
      expect(contenedor.getAttribute('role')).toBe('region');
      expect(contenedor.getAttribute('tabindex')).toBe('0');
      expect(contenedor.getAttribute('aria-label')).toBe(tabla.querySelector('caption').textContent);
      // cada celda lleva la etiqueta de su columna (filas apiladas en pantalla estrecha)
      const columnas = [...tabla.querySelectorAll('th')].map((th) => th.textContent);
      for (const fila of tabla.querySelectorAll('tbody tr')) {
        expect([...fila.children].map((td) => td.getAttribute('data-etiqueta'))).toEqual(columnas);
      }
    }
    // las secciones de listas y tablas de varias columnas ocupan todo el ancho; las de datos van en la rejilla
    for (const t of ['Servicios adicionales', 'Proveedores asignados', 'Logística', 'Notificaciones', 'Auditoría']) {
      expect(seccion(t).className).toMatch(/seccion-ancha/);
    }
    for (const t of ['Cliente', 'Lugar', 'Cotización', 'Balance', 'Contrato', 'Pagos']) {
      expect(seccion(t).className).not.toMatch(/seccion-ancha/);
    }
    expect(within(seccion('Notificaciones')).getByRole('cell', { name: 'Tu evento quedó reservado.' })).toBeTruthy();
  });

  it('evento inexistente: aviso con el mensaje del servicio en lugar de la ficha', async () => {
    iniciarSesionDePrueba();
    simularGateway({
      data: { evento: null },
      errors: [{ message: 'No existe el evento con id 00000000-0000-0000-0000-000000000000', path: ['evento'],
        extensions: { classification: 'NOT_FOUND', status: 404, servicio: 'eventos' } }],
    });

    renderizarApp('/eventos/00000000-0000-0000-0000-000000000000');

    expect((await screen.findByRole('alert')).textContent).toMatch(/No existe el evento/);
    expect(screen.queryByRole('region', { name: 'Cliente' })).toBeNull();
  });

  it('token vencido al abrir la ficha: vuelve al login con un mensaje claro', async () => {
    iniciarSesionDePrueba();
    simularGateway({
      errors: [{ message: 'El token venció. Inicia sesión de nuevo con la mutation login.',
        extensions: { classification: 'UNAUTHORIZED', status: 401, servicio: 'gateway' } }],
    });

    renderizarApp('/eventos/af79495b-9065-4b83-b08e-83ca7edabc5a?paso=todo');

    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeTruthy();
    expect(screen.getByRole('status').textContent).toMatch(/Tu sesión venció/);
    expect(obtenerToken()).toBeNull();
  });
});
