import { describe, expect, it } from 'vitest';
import { diaDeLaSemana, MENSAJE_DIA_NO_PERMITIDO, validarEvento } from './validacionEvento.js';

const HOY = '2026-11-03'; // martes
const valido = { nombreEvento: 'Quince años de Ana', fecha: '2026-11-06', tipoEvento: 'QUINCE_ANOS', cantidadAdultos: '80', cantidadNinos: '0', zona: 'NEIVA', lugarId: '' };
const validar = (cambios, extra = {}) => validarEvento({ ...valido, ...cambios }, { hoy: HOY, ...extra });

describe('validación del evento en la interfaz', () => {
  it('día de la semana sin desfase de zona horaria y fechas imposibles', () => {
    expect(diaDeLaSemana('2026-11-06')).toBe(5); // viernes
    expect(diaDeLaSemana('2026-11-08')).toBe(0); // domingo
    expect(diaDeLaSemana('2027-02-30')).toBeNull();
    expect(diaDeLaSemana('06/11/2026')).toBeNull();
  });

  it('viernes, sábado y domingo posteriores a hoy son válidos; de lunes a jueves, no', () => {
    for (const fecha of ['2026-11-06', '2026-11-07', '2026-11-08', '2027-01-01']) expect(validar({ fecha })).toEqual({});
    for (const fecha of ['2026-11-09', '2026-11-10', '2026-11-11', '2026-11-12']) {
      expect(validar({ fecha })).toEqual({ fecha: MENSAJE_DIA_NO_PERMITIDO });
    }
  });

  it('la fecha debe ser posterior a hoy (hoy mismo no vale)', () => {
    expect(validar({ fecha: HOY }).fecha).toBe('Debe ser una fecha posterior a hoy.');
    expect(validar({ fecha: '2026-10-30' }).fecha).toBe('Debe ser una fecha posterior a hoy.'); // viernes pasado
    expect(validar({ fecha: '' }).fecha).toBe('Elige una fecha en el calendario.');
    expect(validar({ fecha: '2027-02-30' }).fecha).toBe('La fecha no es válida.');
  });

  it('nombre entre 3 y 150 caracteres sin contar los espacios de los extremos', () => {
    expect(validar({ nombreEvento: '  ab  ' }).nombreEvento).toBe('Debe tener entre 3 y 150 caracteres.');
    expect(validar({ nombreEvento: '   ' }).nombreEvento).toBe('Escribe el nombre del evento.');
    expect(validar({ nombreEvento: 'x'.repeat(150) })).toEqual({});
    expect(validar({ nombreEvento: 'x'.repeat(151) }).nombreEvento).toBeTruthy();
  });

  it('cantidades enteras de 0 a 5000 y al menos un invitado', () => {
    expect(validar({ cantidadAdultos: '5000', cantidadNinos: '5000' })).toEqual({});
    expect(validar({ cantidadAdultos: '5001' }).cantidadAdultos).toBe('No puede ser mayor que 5.000.');
    expect(validar({ cantidadNinos: '-1' }).cantidadNinos).toBe('No puede ser negativa.');
    expect(validar({ cantidadAdultos: '1.5' }).cantidadAdultos).toBe('Debe ser un número entero.');
    expect(validar({ cantidadAdultos: '' }).cantidadAdultos).toBe('Indica la cantidad de adultos.');
    expect(validar({ cantidadAdultos: '0', cantidadNinos: '0' }).cantidadAdultos).toBe('El evento debe tener al menos un invitado.');
    expect(validar({ cantidadAdultos: '0', cantidadNinos: '1' })).toEqual({});
  });

  it('tipo y zona obligatorios; capacidad del lugar frente al total de invitados', () => {
    expect(validar({ tipoEvento: '', zona: '' })).toEqual({ tipoEvento: 'Elige el tipo de evento.', zona: 'Elige la zona.' });
    const lugar = { nombre: 'Kiosco El Prado', capacidad: 80 };
    expect(validar({}, { lugar })).toEqual({});
    expect(validar({ cantidadNinos: '1' }, { lugar }).lugarId).toBe('Kiosco El Prado tiene capacidad para 80 personas y el evento tiene 81 invitados.');
    expect(validar({ cantidadNinos: '1' }, { lugar: { nombre: 'Sin dato', capacidad: null } })).toEqual({});
  });
});
