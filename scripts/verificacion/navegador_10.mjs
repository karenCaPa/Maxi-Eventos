// Fase 10C: recorrido REAL de la interfaz servida por el contenedor (http://localhost:5173), en Edge o Chrome sin ventana,
// manejado por el protocolo de DevTools (WebSocket de Node 22; sin dependencias). Lo llama verificar_10.ps1.
// Comprueba que la aplicación funciona con la CSP y el CORS reales: login, listado, ficha de la demostración (solo lectura),
// calendario de Nuevo evento, que la CSP BLOQUEA una petición a otro sitio y un script inyectado, y cerrar sesión.
// Fase 11F: el paso 7 "Resumen" de la ficha (6 tarjetas, sin tablas, una columna a 360 px, enlaces que llevan el foco).
// Las credenciales llegan por variables del proceso (MAXI_CORREO, MAXI_PASSWORD); nunca se imprimen.
// Uso: node navegador_10.mjs <ruta-del-navegador> [url-de-la-interfaz]
// Salida: una línea "OK    …" o "FALLO …" por comprobación; código 0 si no hay fallos.
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const [, , navegador, URL_UI = 'http://localhost:5173'] = process.argv;
const PUERTO = 9300 + Math.floor(Math.random() * 500);
const perfil = mkdtempSync(join(tmpdir(), 'maxi-navegador-'));
let fallos = 0;
const comprobar = (cond, texto) => { console.log(`${cond ? 'OK   ' : 'FALLO'} ${texto}`); if (!cond) fallos += 1; };
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

const proceso = spawn(navegador, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PUERTO}`, `--user-data-dir=${perfil}`, '--window-size=1280,900', 'about:blank'], { stdio: 'ignore' });

async function conectar() {
  for (let i = 0; i < 60; i += 1) {
    try {
      const lista = await (await fetch(`http://127.0.0.1:${PUERTO}/json/list`)).json();
      const pagina = lista.find((t) => t.type === 'page');
      if (pagina) return pagina.webSocketDebuggerUrl;
    } catch { /* todavía arrancando */ }
    await esperar(250);
  }
  throw new Error('El navegador no abrió el puerto de DevTools.');
}

const ws = new WebSocket(await conectar());
await new Promise((r, e) => { ws.onopen = r; ws.onerror = e; });
let siguiente = 0;
const pendientes = new Map();
const consola = []; // mensajes de error de la página (incluidas las violaciones de CSP)
ws.onmessage = (m) => {
  const msg = JSON.parse(m.data);
  if (msg.id && pendientes.has(msg.id)) { pendientes.get(msg.id)(msg); pendientes.delete(msg.id); return; }
  if (msg.method === 'Log.entryAdded' && msg.params.entry.level === 'error') consola.push(msg.params.entry.text);
  if (msg.method === 'Runtime.exceptionThrown') consola.push(msg.params.exceptionDetails.text);
};
const cdp = (method, params = {}) => new Promise((r) => { siguiente += 1; pendientes.set(siguiente, r); ws.send(JSON.stringify({ id: siguiente, method, params })); });
async function js(expresion) {
  const r = await cdp('Runtime.evaluate', { expression: expresion, awaitPromise: true, returnByValue: true });
  return r.result?.result?.value;
}
async function hasta(expresion, ms = 15000) {
  const fin = Date.now() + ms;
  while (Date.now() < fin) { if (await js(expresion)) return true; await esperar(200); }
  return false;
}
const ir = async (ruta) => { await cdp('Page.navigate', { url: `${URL_UI}${ruta}` }); await esperar(300); };
const texto = () => js('document.body.innerText');
const violacionesCsp = () => consola.filter((t) => /Content Security Policy/i.test(t));
// Escribe en un input controlado por React (setter nativo + evento input)
const escribir = (selector, valor) => js(`(() => { const el = document.querySelector(${JSON.stringify(selector)});
  Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ${JSON.stringify(valor)});
  el.dispatchEvent(new Event('input', { bubbles: true })); return true; })()`);
const clic = (expresionElemento) => js(`(() => { const el = ${expresionElemento}; if (!el) return false; el.click(); return true; })()`);
const boton = (nombre) => `[...document.querySelectorAll('button')].find((b) => b.textContent.trim() === ${JSON.stringify(nombre)})`;
// Fase 11F: con MAXI_CAPTURAS=<carpeta>, guarda capturas de página completa para la revisión visual (opcional)
async function captura(nombre) {
  if (!process.env.MAXI_CAPTURAS) return;
  const r = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  if (r.result?.data) writeFileSync(join(process.env.MAXI_CAPTURAS, `${nombre}.png`), Buffer.from(r.result.data, 'base64'));
}

try {
  await cdp('Page.enable'); await cdp('Runtime.enable'); await cdp('Log.enable');

  // 1. Sin sesión: la ruta protegida lleva al login, pintado con la CSP real
  await ir('/eventos');
  comprobar(await hasta(`!!document.querySelector('input[type=email]')`), 'sin sesión, /eventos muestra el login (la aplicación arranca con la CSP)');

  // 2. Login real contra el gateway (CORS desde http://localhost:5173)
  await escribir('input[type=email]', process.env.MAXI_CORREO ?? '');
  await escribir('input[type=password]', process.env.MAXI_PASSWORD ?? '');
  await clic(boton('Ingresar'));
  comprobar(await hasta(`document.body.innerText.includes('eventos en total')`), 'login → listado de eventos ("… eventos en total")');
  comprobar(await js(`!!sessionStorage.getItem('maxi-eventos.token')`), 'el token queda en sessionStorage (decisión D1)');
  comprobar(await js(`localStorage.length === 0 && !document.cookie`), 'nada en localStorage ni en cookies');

  // 3. Ficha de la demostración (solo lectura). Filtro RESERVADO en la URL: con muchos eventos cancelados, la demostración
  //    puede no estar en la primera página del listado completo.
  await ir('/eventos?estado=RESERVADO');
  const enlaceLucia = `[...document.querySelectorAll('a')].find((a) => a.textContent.trim() === 'Quince años de Lucía')`;
  comprobar(await hasta(`!!${enlaceLucia}`), 'el filtro Estado = Reservado (en la URL) lista "Quince años de Lucía"');
  await clic(enlaceLucia);
  comprobar(await hasta(`document.querySelector('h1')?.textContent === 'Quince años de Lucía'`), 'abre la ficha de "Quince años de Lucía"');
  // Desde la guía paso a paso (cambio manual tras la Fase 10) cada paso muestra solo sus secciones; "Ver todo junto" = ?paso=todo.
  comprobar(await hasta(`document.querySelector('nav[aria-label="Pasos del evento"]') !== null`), 'la ficha abre con la guía de pasos');
  await ir(`${await js('location.pathname')}?paso=todo`);
  comprobar(await hasta(`document.querySelectorAll('section.seccion').length === 11`), 'la ficha con "Ver todo junto" muestra las 11 secciones');
  const t = await texto();
  comprobar(/6\.270\.000/.test(t) && /Firmado/.test(t), 'saldo pendiente $ 6.270.000 y contrato Firmado');
  comprobar(await js(`document.querySelectorAll('.aviso-error').length === 0`), 'ninguna sección con error');
  comprobar(await js(`[...document.querySelectorAll('table')].every((t) => t.parentElement.classList.contains('tabla-contenedor'))`), 'todas las tablas dentro de su contenedor');

  // 3b. Fase 11F: paso 7 "Resumen" (sale de la misma respuesta de la ficha; solo lectura), a 1280 y a 360 px
  await ir(`${await js('location.pathname')}?paso=resumen`);
  comprobar(await hasta(`document.querySelector('#guia-titulo')?.textContent === 'Resumen del evento' && document.querySelectorAll('.resumen-tarjeta').length === 6`), 'paso 7 "Resumen": la guía lo abre con sus 6 tarjetas');
  comprobar(await js(`document.querySelector('.guia-posicion')?.textContent === 'Paso 7 de 7' && !!document.querySelector('.resumen-estado-texto') && document.querySelectorAll('.resumen-evento table').length === 0`), '"Paso 7 de 7", con la línea de pendientes y sin tablas');
  comprobar(/6\.270\.000/.test(await texto()) && await js(`document.querySelectorAll('.resumen-evento .aviso-error').length === 0`), 'el resumen muestra el saldo pendiente $ 6.270.000 y ninguna parte con error');
  await captura('resumen-1280');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 360, height: 800, deviceScaleFactor: 1, mobile: true });
  await esperar(500);
  comprobar(await js(`document.documentElement.scrollWidth <= 360`), 'a 360 px, sin desplazamiento horizontal');
  comprobar(await js(`(() => { const x = [...document.querySelectorAll('.resumen-tarjeta')].map((a) => a.getBoundingClientRect().left); return x.length === 6 && x.every((v) => Math.abs(v - x[0]) < 1); })()`), 'a 360 px, las 6 tarjetas en una sola columna');
  await captura('resumen-360');
  await cdp('Emulation.clearDeviceMetricsOverride');
  await esperar(300);
  await clic(`[...document.querySelectorAll('.resumen-ir a')].find((a) => a.textContent === 'Ir a Abonos')`);
  comprobar(await hasta(`document.activeElement?.id === 'guia-titulo' && document.activeElement.textContent === 'Registra los abonos'`), 'el enlace "Ir a Abonos" lleva a su paso y le pasa el foco');

  // 4. Calendario de Nuevo evento (sin crear nada)
  await clic(`[...document.querySelectorAll('nav a, a')].find((a) => a.textContent.trim() === 'Nuevo evento')`);
  // El mes actual puede no tener ningún fin de semana libre (2026-11-10: los tres que quedaban de noviembre los ocupa la
  // demostración), así que se buscan días disponibles en el mes actual y, si no hay, en los dos siguientes ("Mes siguiente").
  const hayDisponibles = `[...document.querySelectorAll('button.dia')].some((b) => /, disponible$/.test(b.getAttribute('aria-label')))`;
  // Mes cargado: ya hay días y no está el aviso "Cargando las fechas del mes…" (CalendarioFechas.jsx)
  const mesCargado = `[...document.querySelectorAll('button.dia')].length > 0 && !document.body.innerText.includes('Cargando las fechas del mes')`;
  let conDisponibles = false;
  let mesesRevisados = 0;
  for (; mesesRevisados < 3 && !conDisponibles; mesesRevisados += 1) {
    if (mesesRevisados > 0) { await clic(`document.querySelector('[aria-label="Mes siguiente"]')`); await esperar(150); }
    await hasta(mesCargado);
    conDisponibles = Boolean(await js(hayDisponibles));
  }
  const mesConDisponibles = await js(`document.querySelector('.calendario-titulo')?.textContent`);
  comprobar(conDisponibles, `Nuevo evento: el calendario carga días disponibles (${mesConDisponibles}; ${mesesRevisados} mes(es) revisado(s))`);
  for (let i = 0; i < 12 && !(await js(`/^Diciembre de 2026$/.test(document.querySelector('.calendario-titulo')?.textContent)`)); i += 1) {
    await clic(`document.querySelector('[aria-label="Mes siguiente"]')`); await esperar(150);
  }
  comprobar(await hasta(`(() => { const d = [...document.querySelectorAll('button.dia')].find((b) => b.getAttribute('aria-label').startsWith('sábado, 5 de diciembre de 2026')); return d && d.disabled && /no disponible$/.test(d.getAttribute('aria-label')); })()`),
    'diciembre de 2026: el sábado 5 ("Quince años de Lucía") sale no disponible y deshabilitado');

  // 5. La CSP bloquea lo que no es del propio origen ni del gateway
  const antes = violacionesCsp().length;
  comprobar(antes === 0, `ninguna violación de CSP durante el uso normal (${antes})`);
  const externo = await js(`fetch('https://example.com/').then(() => 'permitido', () => 'bloqueado')`);
  comprobar(externo === 'bloqueado', `fetch a otro sitio (https://example.com) → ${externo}`);
  const inyectado = await js(`(() => { const s = document.createElement('script'); s.textContent = 'window.__inyectado = 1'; document.body.appendChild(s); return window.__inyectado === 1 ? 'ejecutado' : 'bloqueado'; })()`);
  comprobar(inyectado === 'bloqueado', `script en línea inyectado → ${inyectado}`);
  await esperar(500);
  comprobar(violacionesCsp().length >= antes + 2, `el navegador registró las violaciones de CSP (${violacionesCsp().length - antes})`);

  // 6. Cerrar sesión: se borra el token
  await clic(boton('Cerrar sesión'));
  comprobar(await hasta(`!!document.querySelector('input[type=email]')`), 'cerrar sesión → login');
  comprobar(await js(`sessionStorage.getItem('maxi-eventos.token') === null`), 'el token se borró de sessionStorage');
  const otros = consola.filter((m) => !/Content Security Policy|example\.com/i.test(m));
  comprobar(otros.length === 0, `sin otros errores en la consola${otros.length ? `: ${otros.slice(0, 3).join(' | ')}` : ''}`);
} catch (err) {
  comprobar(false, `error del recorrido: ${err.message}`);
} finally {
  try { await cdp('Browser.close'); } catch { /* ya cerrado */ }
  ws.close();
  proceso.kill();
  await esperar(500);
  try { rmSync(perfil, { recursive: true, force: true }); } catch { /* el navegador puede tardar en soltar el perfil */ }
}
process.exit(fallos ? 1 : 0);
