import { describe, expect, it } from 'vitest';
import {
  CATEGORIAS_PROVEEDOR, ESTADOS_ASIGNACION, ESTADOS_CONTRATO, etiqueta, formatearFecha, formatearMonto, formatearRecibida,
  haceCuanto, METODOS_PAGO, TIPOS_LUGAR, TIPOS_MENSAJE,
} from './formato.js';

describe('formato es-CO', () => {
  it('fecha larga con solo la primera letra en mayúscula', () => {
    expect(formatearFecha('2026-12-05', { larga: true })).toBe('Sábado, 5 de diciembre de 2026');
    expect(formatearFecha('2027-01-02', { larga: true })).toBe('Sábado, 2 de enero de 2027');
  });

  it('una fecha sin hora no cambia de día por la zona horaria', () => {
    expect(formatearFecha('2027-01-01', { larga: true })).toMatch(/^Viernes, 1 de enero de 2027$/);
  });

  it('montos en pesos colombianos con separador de miles', () => {
    expect(formatearMonto(8880000)).toMatch(/^\$\s?8\.880\.000$/);
    expect(formatearMonto(1044000.5)).toMatch(/1\.044\.000,5/);
    expect(formatearMonto(null)).toBe('—');
  });

  it('etiquetas de los valores del backend en español natural', () => {
    expect(etiqueta(TIPOS_LUGAR, 'SALON')).toBe('Salón');
    expect(etiqueta(TIPOS_LUGAR, 'FINCA')).toBe('Finca');
    expect(etiqueta(METODOS_PAGO, 'TRANSFERENCIA')).toBe('Transferencia');
    expect(etiqueta(METODOS_PAGO, 'EFECTIVO')).toBe('Efectivo');
    expect(etiqueta(METODOS_PAGO, 'TARJETA')).toBe('Tarjeta');
    expect(etiqueta(ESTADOS_CONTRATO, 'FIRMADO')).toBe('Firmado');
    expect(etiqueta(ESTADOS_ASIGNACION, 'CONFIRMADO')).toBe('Confirmado');
    expect(etiqueta(TIPOS_MENSAJE, 'PAGO_REGISTRADO')).toBe('Pago registrado');
    expect(etiqueta(CATEGORIAS_PROVEEDOR, 'fotografia')).toBe('Fotografía'); // sin distinguir mayúsculas
  });

  it('un valor desconocido se muestra con solo la primera letra en mayúscula', () => {
    expect(etiqueta(METODOS_PAGO, 'NEQUI')).toBe('Nequi');
    expect(etiqueta(METODOS_PAGO, 'PAGO_EN_LINEA')).toBe('Pago en linea');
    expect(etiqueta(TIPOS_LUGAR, 'centro de convenciones')).toBe('Centro de convenciones');
    expect(etiqueta(METODOS_PAGO, null)).toBe('—');
  });
});

describe('Hora de recepción de una notificación (Fase 11H, zona America/Bogota)', () => {
  // 6-nov-2026 14:00 en Bogotá = 19:00 UTC
  const ahora = new Date('2026-11-06T19:00:00Z');
  const sinEspacios = (t) => t.replace(/\s/g, ' ');

  it('"Hoy", "Ayer" o la fecha completa, con la hora es-CO en la zona de la configuración', () => {
    expect(sinEspacios(formatearRecibida('2026-11-06T20:45:00Z', new Date('2026-11-06T21:00:00Z')))).toBe('Hoy, 3:45 p. m.');
    expect(sinEspacios(formatearRecibida('2026-11-05T14:10:00Z', ahora))).toBe('Ayer, 9:10 a. m.');
    expect(sinEspacios(formatearRecibida('2026-11-02T14:10:00Z', ahora))).toBe('2 de noviembre de 2026, 9:10 a. m.');
    // 03:00 UTC del 6-nov = 22:00 del 5-nov en Bogotá: es "Ayer" aunque en UTC sea el mismo día
    expect(sinEspacios(formatearRecibida('2026-11-06T03:00:00Z', ahora))).toBe('Ayer, 10:00 p. m.');
    expect(formatearRecibida(null, ahora)).toBe('—');
  });

  it('"hace …" solo para lo de las últimas 24 horas', () => {
    expect(haceCuanto('2026-11-06T18:59:40Z', ahora)).toBe('hace un momento');
    expect(haceCuanto('2026-11-06T18:55:00Z', ahora)).toBe('hace 5 min');
    expect(haceCuanto('2026-11-06T16:00:00Z', ahora)).toBe('hace 3 h');
    expect(haceCuanto('2026-11-04T19:00:00Z', ahora)).toBeNull();
    expect(haceCuanto('2026-11-06T19:10:00Z', ahora)).toBeNull(); // futura
  });
});
