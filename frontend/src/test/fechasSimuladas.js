// Simulación de fechasDisponibles para las pruebas del calendario (10B3e): devuelve los viernes, sábados y domingos del rango
// posteriores a "hoy", menos los ocupados (por defecto el fin de semana de "Quince años de Lucía": 4, 5 y 6 de diciembre de 2026).
export const HOY = '2026-11-03'; // martes
export const OCUPADAS = ['2026-12-04', '2026-12-05', '2026-12-06'];

export function libresEntre(desde, hasta, { hoy = HOY, ocupadas = OCUPADAS } = {}) {
  const libres = [];
  for (let d = new Date(`${desde}T00:00:00Z`); d.toISOString().slice(0, 10) <= hasta; d.setUTCDate(d.getUTCDate() + 1)) {
    const iso = d.toISOString().slice(0, 10);
    if (iso > hoy && [5, 6, 0].includes(d.getUTCDay()) && !ocupadas.includes(iso)) {
      libres.push({ fecha: iso, diaSemana: ['DOMINGO', '', '', '', '', 'VIERNES', 'SÁBADO'][d.getUTCDay()], estado: 'DISPONIBLE' });
    }
  }
  return libres;
}

/** Respuesta de la operación FechasDisponibles para simularPorOperacion. */
export const respuestaFechas = (opciones) => (v) => ({ data: { fechasDisponibles: libresEntre(v.desde, v.hasta, opciones) } });

/** Fija la fecha del sistema (solo Date; los temporizadores siguen siendo reales). */
export function fijarHoy(vi, iso = HOY) {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(`${iso}T15:00:00Z`)); // 10:00 en Colombia
}
