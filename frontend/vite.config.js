import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// El puerto 5173 es el origen que el gateway permite en CORS_ALLOWED_ORIGINS (igual en desarrollo y en Docker).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  preview: { port: 5173, strictPort: true },
  build: { sourcemap: false },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.test.{js,jsx}'],
    // La primera prueba de cada archivo paga el arranque de jsdom y de la página (el asistente de Nuevo evento tardaba > 5 s).
    testTimeout: 15000,
  },
});
