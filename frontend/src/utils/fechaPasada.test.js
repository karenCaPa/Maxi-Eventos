import { describe, expect, it } from 'vitest';
import { esFechaPasada, estadoVisible, hoyEnZona, textoAvisoPasado } from './fechaPasada.js';

// Fase 11I: "hoy" en la zona del negocio y las etiquetas Realizado / Vencido según la fecha.
describe('hoyEnZona', () => {
  it('usa la zona de la configuración, no la del navegador: a las 23:59 de Neiva sigue siendo el mismo día', () => {
    expect(hoyEnZona(0, new Date('2026-11-11T04:59:00Z'), 'America/Bogota')).toBe('2026-11-10'); // 23:59 en Neiva
    expect(hoyEnZona(0, new Date('2026-11-11T05:00:00Z'), 'America/Bogota')).toBe('2026-11-11'); // 00:00 en Neiva
    expect(hoyEnZona(0, new Date('2026-11-11T04:59:00Z'), 'Europe/Madrid')).toBe('2026-11-11');
  });

  it('suma y resta días, también al cambiar de mes y de año', () => {
    const ahora = new Date('2026-12-31T15:00:00Z');
    expect(hoyEnZona(1, ahora, 'America/Bogota')).toBe('2027-01-01');
    expect(hoyEnZona(-1, new Date('2026-11-01T15:00:00Z'), 'America/Bogota')).toBe('2026-10-31');
  });

  it('sin "ahora" toma la fecha del sistema (en las pruebas, 2026-11-03 a las 10:00 de Neiva)', () => {
    expect(hoyEnZona()).toBe('2026-11-03');
    expect(hoyEnZona(-1)).toBe('2026-11-02');
  });
});

describe('estadoVisible: Realizado y Vencido según la fecha', () => {
  const HOY = '2026-11-10';

  it('RESERVADO: ayer → Realizado; hoy y mañana → Reservado (caso límite: el día del evento todavía no pasó)', () => {
    expect(estadoVisible('RESERVADO', '2026-11-09', HOY)).toBe('REALIZADO');
    expect(estadoVisible('RESERVADO', '2026-11-10', HOY)).toBe('RESERVADO');
    expect(estadoVisible('RESERVADO', '2026-11-11', HOY)).toBe('RESERVADO');
  });

  it('PENDIENTE: ayer → Vencido; hoy y mañana → Pendiente', () => {
    expect(estadoVisible('PENDIENTE', '2026-11-09', HOY)).toBe('VENCIDO');
    expect(estadoVisible('PENDIENTE', '2026-11-10', HOY)).toBe('PENDIENTE');
    expect(estadoVisible('PENDIENTE', '2026-11-11', HOY)).toBe('PENDIENTE');
  });

  it('CANCELADO se ve igual, pase o no su fecha', () => {
    expect(estadoVisible('CANCELADO', '2026-01-01', HOY)).toBe('CANCELADO');
    expect(estadoVisible('CANCELADO', '2027-01-01', HOY)).toBe('CANCELADO');
  });

  it('esFechaPasada compara días (no horas) y sin fecha no hay nada pasado', () => {
    expect(esFechaPasada('2026-10-31', '2026-11-01')).toBe(true); // cambio de mes
    expect(esFechaPasada('2026-11-01', '2026-11-01')).toBe(false);
    expect(esFechaPasada(null, '2026-11-01')).toBe(false);
  });

  it('el texto del aviso de la ficha', () => {
    expect(textoAvisoPasado({ estado: 'RESERVADO', fecha: '2026-11-07' }, HOY)).toBe('Este evento se realizó el sábado, 7 de noviembre de 2026.');
    expect(textoAvisoPasado({ estado: 'PENDIENTE', fecha: '2026-11-07' }, HOY)).toBe('Este evento venció el sábado, 7 de noviembre de 2026 sin confirmarse.');
    expect(textoAvisoPasado({ estado: 'RESERVADO', fecha: '2026-11-10' }, HOY)).toBeNull();
    expect(textoAvisoPasado({ estado: 'CANCELADO', fecha: '2026-11-07' }, HOY)).toBeNull();
  });
});
