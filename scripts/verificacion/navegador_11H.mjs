// Fase 11H: la campanita en el navegador real (Edge o Chrome sin ventana, protocolo de DevTools con el WebSocket de Node 22;
// sin dependencias). Lo llama verificar_11H.ps1. Credenciales de la ADMINISTRADORA por variables del proceso (MAXI_CORREO,
// MAXI_PASSWORD; nunca se imprimen). Con MAXI_CAPTURAS=<carpeta> guarda capturas del panel a 1280 y 360 px.
// Uso: node navegador_11H.mjs <navegador> <url-ui> <url-finanzas> <eventoId> <clienteId> <nombreEvento>
// 1. Login y la campanita en 0 (sin número). 2. "Ejecutar recordatorios" → el número sube SIN recargar la página.
// 3. El panel: el recordatorio del evento de prueba "Nueva", con su evento y la hora de hoy en la zona de /config.js.
// 4. Panel dentro de la pantalla a 1280 y a 360 px; Escape cierra y el foco vuelve a la campana.
// 5. Polling: un abono registrado por REST (sin tocar la página) hace subir el número en ≤ 40 s.
// 6. Clic en el recordatorio: se marca como leído, baja el número y abre la ficha del evento.
// Salida: "OK    …" / "FALLO …" por comprobación y "DATO clave=valor"; código 0 si no hay fallos.
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const [, , navegador, URL_UI, URL_FINANZAS, EVENTO_ID, CLIENTE_ID, NOMBRE_EVENTO] = process.argv;
const PUERTO = 9600 + Math.floor(Math.random() * 300);
const perfil = mkdtempSync(join(tmpdir(), 'maxi-campana-'));
let fallos = 0;
const comprobar = (cond, texto) => { console.log(`${cond ? 'OK   ' : 'FALLO'} ${texto}`); if (!cond) fallos += 1; };
const dato = (clave, valor) => console.log(`DATO ${clave}=${valor}`);
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

const proceso = spawn(navegador, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PUERTO}`, `--user-data-dir=${perfil}`, '--window-size=1280,900', 'about:blank'], { stdio: 'ignore' });
let url;
for (let i = 0; i < 60 && !url; i += 1) {
  try { url = (await (await fetch(`http://127.0.0.1:${PUERTO}/json/list`)).json()).find((t) => t.type === 'page')?.webSocketDebuggerUrl; } catch { /* arrancando */ }
  if (!url) await esperar(250);
}
const ws = new WebSocket(url);
await new Promise((r, e) => { ws.onopen = r; ws.onerror = e; });
let n = 0; const p = new Map();
ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && p.has(d.id)) { p.get(d.id)(d); p.delete(d.id); } };
const cdp = (method, params = {}) => new Promise((r) => { n += 1; p.set(n, r); ws.send(JSON.stringify({ id: n, method, params })); });
const js = async (e) => (await cdp('Runtime.evaluate', { expression: e, awaitPromise: true, returnByValue: true })).result?.result?.value;
const hasta = async (e, ms = 15000) => { const f = Date.now() + ms; while (Date.now() < f) { if (await js(e)) return true; await esperar(250); } return false; };
const ir = async (r) => { await cdp('Page.navigate', { url: `${URL_UI}${r}` }); await esperar(400); };
const escribir = (s, v) => js(`(() => { const el = document.querySelector(${JSON.stringify(s)}); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ${JSON.stringify(v)}); el.dispatchEvent(new Event('input', { bubbles: true })); return true; })()`);
const clic = (e) => js(`(() => { const el = ${e}; if (!el) return false; el.click(); return true; })()`);
const boton = (t) => `[...document.querySelectorAll('button')].find((b) => b.textContent.trim() === ${JSON.stringify(t)})`;
const CAMPANA = `document.querySelector('.campana-boton')`;
const sinLeer = async () => Number(/^Notificaciones, (\d+) sin leer$/.exec(await js(`${CAMPANA}?.getAttribute('aria-label') ?? ''`))?.[1] ?? NaN);
async function captura(nombre) {
  if (!process.env.MAXI_CAPTURAS) return;
  const r = await cdp('Page.captureScreenshot', { format: 'png' });
  if (r.result?.data) writeFileSync(join(process.env.MAXI_CAPTURAS, `${nombre}.png`), Buffer.from(r.result.data, 'base64'));
}
/** Hora "h:mm" actual en la zona de la interfaz (la de /config.js), como la escribe es-CO. */
const horaAhora = (zona, desfaseMin = 0) => new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit', timeZone: zona })
  .format(new Date(Date.now() - desfaseMin * 60_000)).replace(/\s/g, ' ');

try {
  await cdp('Page.enable'); await cdp('Runtime.enable');
  // 1. Login de la administradora; la campanita empieza en 0
  await ir('/eventos');
  await hasta(`!!document.querySelector('input[type=email]')`);
  await escribir('input[type=email]', process.env.MAXI_CORREO ?? '');
  await escribir('input[type=password]', process.env.MAXI_PASSWORD ?? '');
  await clic(boton('Ingresar'));
  comprobar(await hasta(`document.body.innerText.includes('eventos en total')`), 'login de la administradora → listado');
  comprobar(await hasta(`${CAMPANA}?.getAttribute('aria-label') === 'Notificaciones, 0 sin leer'`), 'la campanita empieza en 0 ("Notificaciones, 0 sin leer")');
  comprobar(await js(`!document.querySelector('.campana-contador')`), 'con 0 no se muestra número');
  const zona = await js('window.__CONFIG__?.zonaHoraria');
  dato('zona', zona);

  // 2. Ejecutar recordatorios en la interfaz: el número sube sin recargar la página
  const marca = await js(`(window.__sinRecargar = Math.random(), window.__sinRecargar)`);
  comprobar(await hasta(`!!(${boton('Ejecutar recordatorios')})`), 'el botón "Ejecutar recordatorios" está (administradora)');
  const t0 = Date.now();
  await clic(boton('Ejecutar recordatorios'));
  comprobar(await hasta(`/Recordatorios revisados/.test(document.body.innerText)`), 'Ejecutar recordatorios → "Recordatorios revisados: …"');
  const subio = await hasta(`/^Notificaciones, [1-9]\\d* sin leer$/.test(${CAMPANA}?.getAttribute('aria-label') ?? '')`, 40000);
  dato('segundos_recordatorio', ((Date.now() - t0) / 1000).toFixed(1));
  const tras = await sinLeer();
  comprobar(subio, `la campanita sube de 0 a ${tras} tras el recordatorio (${((Date.now() - t0) / 1000).toFixed(1)} s)`);
  comprobar(await js(`window.__sinRecargar === ${marca}`), 'sin recargar la página (la misma ventana JavaScript)');
  comprobar(await js(`document.querySelector('.campana-contador')?.textContent`) === (tras > 9 ? '9+' : String(tras)), `el número visible es "${tras > 9 ? '9+' : tras}"`);

  // 3. El panel: el recordatorio del evento de prueba
  await clic(CAMPANA);
  comprobar(await hasta(`!!document.querySelector('.campana-panel li')`), 'el panel se abre con la lista');
  const item = await js(`(() => { const li = [...document.querySelectorAll('.campana-item')].find((b) => b.textContent.includes(${JSON.stringify(NOMBRE_EVENTO)}) && /para su evento/.test(b.textContent));
    return li ? { estado: li.querySelector('.campana-estado').textContent.trim(), asunto: li.querySelector('.campana-asunto').textContent,
      evento: li.querySelector('.campana-detalle span').textContent, hora: li.querySelector('time').textContent.replace(/\\s/g, ' ') } : null; })()`);
  comprobar(!!item, `el recordatorio del evento "${NOMBRE_EVENTO}" está en el panel`);
  if (item) {
    dato('asunto', item.asunto); dato('hora', item.hora);
    comprobar(/Nueva$/.test(item.estado), `marcado como "Nueva" en texto (${item.estado})`);
    comprobar(/^Faltan? \d+ días? para su evento: /.test(item.asunto), `mensaje del recordatorio: "${item.asunto}"`);
    comprobar(item.evento === NOMBRE_EVENTO, `con el nombre del evento (${item.evento})`);
    const horas = [0, 1, 2].map((d) => horaAhora(zona, d));
    comprobar(horas.some((h) => item.hora.startsWith(`Hoy, ${h}`)) && /hace (un momento|\d+ min)$/.test(item.hora),
      `hora de recepción real en ${zona}: "${item.hora}" (ahora ${horas[0]})`);
  }
  // 4. Dentro de la pantalla a 1280 y 360; Escape devuelve el foco
  // dentro de la pantalla y ENCIMA de todo: en una rejilla de puntos del panel, lo que se ve es el propio panel (nada lo tapa)
  const dentro = `(() => { const el = document.querySelector('.campana-panel'); const r = el.getBoundingClientRect();
    if (!(r.left >= 0 && r.top >= 0 && r.right <= innerWidth && r.bottom <= innerHeight)) return false;
    for (const fx of [0.1, 0.5, 0.9]) for (const fy of [0.1, 0.5, 0.9]) {
      const x = r.left + r.width * fx, y = r.top + r.height * fy; const arriba = document.elementFromPoint(x, y);
      if (!arriba || !el.contains(arriba)) return false; }
    return true; })()`;
  comprobar(await js(dentro) && await js(`document.querySelector('.campana-panel').getBoundingClientRect().left >= document.querySelector('.lateral').getBoundingClientRect().right`),
    'a 1280 px el panel se abre a la derecha de la barra lateral, completo dentro de la pantalla y encima del contenido');
  await captura('campana-1280');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 360, height: 780, deviceScaleFactor: 1, mobile: true });
  await esperar(500);
  comprobar(await js(dentro) && await js(`document.documentElement.scrollWidth <= 360`), 'a 360 px el panel ocupa el ancho, dentro de la pantalla, encima del contenido y sin desplazamiento horizontal');
  await captura('campana-360');
  await cdp('Emulation.clearDeviceMetricsOverride');
  await esperar(300);
  await cdp('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
  await cdp('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
  comprobar(await hasta(`!document.querySelector('.campana-panel') && document.activeElement === ${CAMPANA}`), 'Escape cierra el panel y el foco vuelve a la campana');

  // 5. Polling: una notificación nueva sin tocar la página (un abono por REST)
  const antes = await sinLeer();
  const token = await js(`sessionStorage.getItem('maxi-eventos.token')`);
  const pago = await fetch(`${URL_FINANZAS}/finanzas/pagos`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ eventoId: EVENTO_ID, clienteId: CLIENTE_ID, metodoPago: 'EFECTIVO', monto: 1000, moneda: 'COP' }) });
  comprobar(pago.status === 201, `abono de prueba por REST (sin tocar la página) → ${pago.status}`);
  const t1 = Date.now();
  const porPolling = await hasta(`Number(/(\\d+) sin leer/.exec(${CAMPANA}?.getAttribute('aria-label') ?? '')?.[1]) === ${antes + 1}`, 40000);
  dato('segundos_polling', ((Date.now() - t1) / 1000).toFixed(1));
  comprobar(porPolling, `el polling la sube de ${antes} a ${antes + 1} sola, en ${((Date.now() - t1) / 1000).toFixed(1)} s (≤ 40 s)`);

  // 6. Clic en el recordatorio: leído, baja el número y abre la ficha
  const previo = await sinLeer();
  await clic(CAMPANA);
  await hasta(`!!document.querySelector('.campana-item')`);
  await clic(`[...document.querySelectorAll('.campana-item')].find((b) => b.textContent.includes(${JSON.stringify(NOMBRE_EVENTO)}) && /para su evento/.test(b.textContent))`);
  comprobar(await hasta(`location.pathname === '/eventos/${EVENTO_ID}'`), 'el clic abre la ficha del evento');
  comprobar(await hasta(`document.querySelector('h1')?.textContent === ${JSON.stringify(NOMBRE_EVENTO)}`), 'la ficha muestra el evento');
  comprobar(await hasta(`Number(/(\\d+) sin leer/.exec(${CAMPANA}?.getAttribute('aria-label') ?? '')?.[1]) === ${previo - 1}`), `el número baja de ${previo} a ${previo - 1}`);
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
