import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import InputMonto, { agrupar, limpiar } from './InputMonto.jsx';

describe('InputMonto', () => {
  it('agrupa miles con punto y usa coma para decimales', () => {
    expect(agrupar('1500000')).toBe('1.500.000');
    expect(agrupar('900')).toBe('900');
    expect(agrupar('1234.5')).toBe('1.234,5');
    expect(agrupar('')).toBe('');
  });

  it('limpia lo escrito o pegado y entrega un número crudo', () => {
    expect(limpiar('1.500.000')).toBe('1500000');
    expect(limpiar('1.234,56')).toBe('1234.56');
    expect(limpiar('$ 12abc')).toBe('12');
    expect(limpiar('007')).toBe('7');
  });

  it('muestra los puntos al escribir y guarda el valor sin ellos', () => {
    let guardado = '';
    function Prueba() {
      const [v, setV] = useState('');
      guardado = v;
      return <InputMonto aria-label="Monto" valor={v} alCambiar={setV} />;
    }
    render(<Prueba />);
    const campo = screen.getByLabelText('Monto');
    fireEvent.change(campo, { target: { value: '1500000' } });
    expect(campo.value).toBe('1.500.000');
    expect(guardado).toBe('1500000');
  });
});
