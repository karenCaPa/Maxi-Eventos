import { cleanup, configure } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { fijarHoy } from './fechasSimuladas.js';

// findBy… / waitFor esperan hasta 5 s (por defecto 1 s): con varios archivos en paralelo la primera pantalla de cada uno
// puede tardar más de 1 s en jsdom (Fase 11C: el login falló así una vez de cinco ejecuciones).
configure({ asyncUtilTimeout: 5000 });

// Fase 11I: desde que la interfaz distingue los eventos con fecha pasada, las pruebas no pueden depender del día en que se
// ejecutan (la ficha de demostración es del 2026-12-05: después de esa fecha se vería "Realizado" y sin botones). Toda la
// suite corre con "hoy" = 2026-11-03 (solo Date; los temporizadores siguen siendo reales). Cada prueba puede moverlo.
beforeEach(() => fijarHoy(vi));

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  window.sessionStorage.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});
