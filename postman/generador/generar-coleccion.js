// Genera postman/maxi-eventos.postman_collection.json y postman/maxi-eventos-local.postman_environment.json (Fase 9C).
// Uso (desde la raíz del proyecto): node postman/generador/generar-coleccion.js [carpeta-destino]
// Sin argumento escribe en postman/. Solo usa módulos de Node (fs, path); no necesita npm install. Ver README.md de esta carpeta.
const fs = require('fs');
const path = require('path');
const destino = process.argv[2] || path.join(__dirname, '..');

// ------------------------------------------------------------------ utilidades de scripts
const L = (...lineas) => lineas.flat();
const st = (n) => `pm.test("HTTP ${n}", function () { pm.response.to.have.status(${n}); });`;
const js = () => `const r = pm.response.json();`;
const err = (n) => L(st(n), js(),
  `pm.test("Formato ErrorResponse (${n})", function () { pm.expect(r.status).to.eql(${n}); pm.expect(r.message).to.be.a("string").and.not.empty; pm.expect(r.path).to.match(/^\\/api\\/v1\\//); pm.expect(r.timestamp).to.be.a("string"); });`);
const campo = (c) => `pm.test("errores[] incluye el campo ${c}", function () { pm.expect((r.errores || []).map(function (e) { return e.campo; })).to.include("${c}"); });`;
const msg = (t) => `pm.test("Mensaje: contiene '${t.replace(/'/g, "\\'")}'", function () { pm.expect(r.message).to.include(${JSON.stringify(t)}); });`;
const e400 = (...campos) => L(err(400), campos.map(campo));
const e401 = () => L(err(401), msg('Debes iniciar sesión'));
const e404 = (t) => L(err(404), t ? msg(t) : []);
const e409 = (t) => L(err(409), t ? msg(t) : []);
const location = () => `pm.test("Cabecera Location", function () { pm.expect(pm.response.headers.get("Location")).to.be.a("string").and.not.empty; });`;
const pagina = (size) => L(js(), `pm.test("Formato PaginaResponse", function () { ["contenido","pagina","tamanoPagina","totalElementos","totalPaginas"].forEach(function (k) { pm.expect(r).to.have.property(k); }); pm.expect(r.contenido).to.be.an("array"); });`,
  size ? `pm.test("tamanoPagina = ${size} y contenido ≤ ${size}", function () { pm.expect(r.tamanoPagina).to.eql(${size}); pm.expect(r.contenido.length).to.be.at.most(${size}); });` : []);
const setC = (k, expr) => `pm.collectionVariables.set("${k}", ${expr});`;
// Fase 11E: cuántos registros de la página no son recordatorios EVENTO_PROXIMO (estos dependen de la fecha de ejecución)
const SIN_RECORDATORIOS = 'String(r.contenido.filter(function (x) { return x.tipo !== "EVENTO_PROXIMO"; }).length)';
const setE = (k, expr) => `pm.environment.set("${k}", ${expr});`;
const pdf = () => L(st(200), `pm.test("Es un PDF", function () { pm.expect(pm.response.headers.get("Content-Type")).to.include("application/pdf"); pm.expect(pm.response.responseSize).to.be.above(500); });`);
// Fase 10: descargas de PDF por el gateway (/documentos). La interfaz pide PDF o JSON (el JSON es el ErrorResponse de un error).
const ACEPTA_PDF = [{ key: 'Accept', value: 'application/pdf, application/json' }];
const pdfGateway = (archivo) => L(pdf(),
  `pm.test("Adjunto ${archivo}-<eventoId>.pdf, sin caché, y el cuerpo empieza por %PDF", function () { pm.expect(pm.response.headers.get("Content-Disposition")).to.eql('attachment; filename="${archivo}-' + pm.collectionVariables.get("flujoEventoId") + '.pdf"'); pm.expect(pm.response.headers.get("Cache-Control")).to.eql("no-store"); pm.expect(pm.response.text().substring(0, 4)).to.eql("%PDF"); });`);
const errDoc = (n, t) => L(st(n), js(),
  `pm.test("Formato ErrorResponse del gateway (${n})", function () { pm.expect(r.status).to.eql(${n}); pm.expect(r.message).to.be.a("string").and.not.empty; pm.expect(r.path).to.match(/^\\/documentos\\/eventos\\//); pm.expect(r.timestamp).to.be.a("string"); });`,
  t ? msg(t) : []);
// Reintento para lo asíncrono (Kafka): repite la misma petición hasta 20 veces (2 s entre intentos) si no se cumple la condición.
const reintentar = (cond, tests) => L(
  `const clave = "reintentos_" + pm.info.requestName.replace(/[^A-Za-z0-9]/g, "_");`,
  `const intentos = Number(pm.collectionVariables.get(clave) || 0);`,
  `let listo = false; try { listo = (function () { ${cond} })(); } catch (e) { listo = false; }`,
  `if (!listo && intentos < 20) { pm.collectionVariables.set(clave, intentos + 1); postman.setNextRequest(pm.info.requestName); }`,
  `else { pm.collectionVariables.unset(clave);`, tests, `}`);
const espera = () => [`const clave = "reintentos_" + pm.info.requestName.replace(/[^A-Za-z0-9]/g, "_");`,
  `if (Number(pm.collectionVariables.get(clave) || 0) > 0) { setTimeout(function () {}, 2000); }`];

// ------------------------------------------------------------------ peticiones
function req(nombre, metodo, url, opc = {}) {
  const item = { name: nombre, event: [], request: { method: metodo, header: [], url } };
  if (opc.desc) item.request.description = opc.desc;
  if (opc.pre) item.event.push({ listen: 'prerequest', script: { type: 'text/javascript', exec: L(opc.pre) } });
  item.event.push({ listen: 'test', script: { type: 'text/javascript', exec: L(opc.test || []) } });
  if (opc.sinToken) item.request.auth = { type: 'noauth' };
  if (opc.body !== undefined) {
    item.request.header.push({ key: 'Content-Type', value: 'application/json' });
    item.request.body = { mode: 'raw', raw: typeof opc.body === 'string' ? opc.body : JSON.stringify(opc.body, null, 2), options: { raw: { language: 'json' } } };
  }
  (opc.headers || []).forEach(h => item.request.header.push(h));
  return item;
}
function gql(nombre, query, variables, test, opc = {}) {
  const item = { name: nombre, event: [{ listen: 'test', script: { type: 'text/javascript', exec: L(test) } }],
    request: { method: 'POST', auth: { type: 'noauth' }, header: [{ key: 'Content-Type', value: 'application/json' }],
      body: { mode: 'graphql', graphql: { query, variables: JSON.stringify(variables || {}, null, 2) } }, url: '{{gatewayUrl}}' } };
  if (!opc.sinToken) item.request.header.push({ key: 'Authorization', value: 'Bearer {{token}}' });
  if (opc.desc) item.request.description = opc.desc;
  return item;
}
const carpeta = (name, description, item) => ({ name, description, item });
const NX = '11111111-1111-1111-1111-111111111111';
const U = { auth: '{{urlAuth}}', ev: '{{urlEventos}}', cli: '{{urlClientes}}', lug: '{{urlLugares}}', fin: '{{urlFinanzas}}',
  con: '{{urlContratos}}', prov: '{{urlProveedores}}', aud: '{{urlAuditoria}}', log: '{{urlLogistica}}', not: '{{urlNotificaciones}}',
  gw: '{{gatewayBaseUrl}}' };

// ------------------------------------------------------------------ 00 Preparación
const prep = carpeta('00 Preparación', 'Login (guarda el token), datos de demostración (ids por nombre y foto inicial), fechas libres y el cliente propio de la colección.', [
  req('00.01 Login [401] contraseña incorrecta', 'POST', `${U.auth}/auth/login`, { sinToken: true,
    body: '{\n  "correo": "{{correo}}",\n  "password": "contrasena-incorrecta-postman"\n}',
    test: L(e401().slice(0, 3), msg('Correo o contraseña incorrectos.')) }),
  req('00.02 Login [400] datos inválidos', 'POST', `${U.auth}/auth/login`, { sinToken: true,
    body: { correo: 'no-es-un-correo', password: '' }, test: e400('correo', 'password') }),
  req('00.03 Login [happy] guarda el token', 'POST', `${U.auth}/auth/login`, { sinToken: true,
    desc: 'La contraseña sale de la variable de environment `password` (vacía en el archivo exportado; ver postman/README.md). El cuerpo se arma en el pre-request para que cualquier carácter de la contraseña quede bien escapado.',
    body: '{}',
    pre: [`pm.request.body.update(JSON.stringify({ correo: pm.environment.get("correo"), password: pm.environment.get("password") || "" }));`],
    test: L(`pm.test("La variable password está definida (VERIFICACION_PASSWORD del .env)", function () { pm.expect(pm.environment.get("password"), "Define la variable password del environment").to.be.a("string").and.not.empty; });`,
      st(200), js(),
      `pm.test("Token Bearer con expiración", function () { pm.expect(r.tipoToken).to.eql("Bearer"); pm.expect(r.expiracionEnSegundos).to.be.above(0); pm.expect(r.token).to.be.a("string").and.not.empty; });`,
      `pm.test("Fase 11D: rol ADMINISTRADOR en la respuesta y en el claim rol del token (sin el correo)", function () { pm.expect(r.rol).to.eql("ADMINISTRADOR"); pm.expect(r.nombre).to.be.a("string").and.not.empty; const c = JSON.parse(atob(r.token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))); pm.expect(c.rol).to.eql("ADMINISTRADOR"); pm.expect(JSON.stringify(c)).to.not.include("@"); });`,
      setC('token', 'r.token')) }),
  // Fase 9E: orden por fechaCreacion ascendente y find() = el evento MÁS ANTIGUO con ese nombre (el de cargar-demo.ps1 sobre
  // una base nueva), aunque otros scripts o pruebas creen eventos con el mismo nombre más tarde.
  req('00.04 Demo: evento RESERVADO (Quince años de Lucía)', 'GET', `${U.ev}/eventos?estado=RESERVADO&size=100&sort=fechaCreacion,asc`, {
    test: L(st(200), js(), `const e = r.contenido.find(function (x) { return x.nombreEvento === "Quince años de Lucía"; });`,
      `pm.test("Existe el evento de demostración RESERVADO (ejecuta antes cargar-demo.ps1)", function () { pm.expect(e).to.be.an("object"); });`,
      `if (e) { ${setE('demoEventoReservadoId', 'e.id')} ${setE('demoClienteId', 'e.clienteId')} ${setE('demoLugarId', 'e.lugarId')} ${setC('fotoReservado', 'JSON.stringify(e)')} }`) }),
  req('00.05 Demo: evento PENDIENTE (Boda de Andrés y Camila)', 'GET', `${U.ev}/eventos?estado=PENDIENTE&size=100&sort=fechaCreacion,asc`, {
    test: L(st(200), js(), `const e = r.contenido.find(function (x) { return x.nombreEvento === "Boda de Andrés y Camila"; });`,
      `pm.test("Existe el evento de demostración PENDIENTE", function () { pm.expect(e).to.be.an("object"); });`,
      `if (e) { ${setE('demoEventoPendienteId', 'e.id')} ${setC('fotoPendiente', 'JSON.stringify(e)')} }`) }),
  req('00.06 Demo: evento CANCELADO (Quince años de María José)', 'GET', `${U.ev}/eventos?estado=CANCELADO&size=100&sort=fechaCreacion,asc`, {
    test: L(st(200), js(), `const e = r.contenido.find(function (x) { return x.nombreEvento === "Quince años de María José"; });`,
      `pm.test("Existe el evento de demostración CANCELADO", function () { pm.expect(e).to.be.an("object"); });`,
      `if (e) { ${setE('demoEventoCanceladoId', 'e.id')} ${setC('fotoCancelado', 'JSON.stringify(e)')} }`) }),
  req('00.07 Demo: lugar INACTIVO (Hacienda El Pórtico)', 'GET', `${U.lug}/lugares?estado=INACTIVO&nombre=Hacienda&size=100`, {
    test: L(st(200), js(), `const l = r.contenido.find(function (x) { return x.nombre === "Hacienda El Pórtico"; });`,
      `pm.test("Existe el lugar INACTIVO de demostración", function () { pm.expect(l).to.be.an("object"); });`,
      `if (l) { ${setE('demoLugarInactivoId', 'l.id')} }`) }),
  req('00.08 Demo: proveedor (Sonido y Luces del Huila)', 'GET', `${U.prov}/proveedores?categoria=SONIDO&size=100`, {
    test: L(st(200), js(), `const p = r.contenido.find(function (x) { return x.nombre === "Sonido y Luces del Huila"; });`,
      `pm.test("Existe el proveedor de demostración", function () { pm.expect(p).to.be.an("object"); });`,
      `if (p) { ${setE('demoProveedorId', 'p.id')} }`) }),
  req('00.09 Demo: foto del balance', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/balance`, {
    test: L(st(200), js(), setC('fotoBalance', 'JSON.stringify(r)')) }),
  req('00.10 Demo: foto del contrato', 'GET', `${U.con}/eventos/{{demoEventoReservadoId}}/contrato`, {
    test: L(st(200), js(), setE('demoContratoId', 'r.id'), setC('fotoContrato', 'JSON.stringify({ estado: r.estado, firmadoEn: r.firmadoEn, valorTotal: r.valorTotal })')) }),
  req('00.11 Demo: foto de los pagos', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/pagos?size=100`, {
    test: L(st(200), js(), setC('fotoPagos', 'String(r.totalElementos)')) }),
  req('00.12 Demo: foto de los adicionales', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/adicionales`, {
    test: L(st(200), js(), setC('fotoAdicionales', 'String(r.length)')) }),
  req('00.13 Demo: foto de las asignaciones de proveedores', 'GET', `${U.prov}/proveedores/eventos/{{demoEventoReservadoId}}?size=100`, {
    test: L(st(200), js(), setC('fotoAsignaciones', 'String(r.totalElementos)')) }),
  // Fase 11E: las fotos no cuentan los recordatorios EVENTO_PROXIMO (cuántos hay depende de la fecha en que se ejecute)
  req('00.14 Demo: foto de las notificaciones', 'GET', `${U.not}/notificaciones?eventoId={{demoEventoReservadoId}}&size=100`, {
    test: L(st(200), js(), setC('fotoNotificaciones', SIN_RECORDATORIOS)) }),
  req('00.15 Demo: foto de la auditoría', 'GET', `${U.aud}/auditoria?eventoId={{demoEventoReservadoId}}&size=100`, {
    test: L(st(200), js(), setC('fotoAuditoria', SIN_RECORDATORIOS)) }),
  req('00.16 Fechas libres para la colección', 'GET', `${U.ev}/fechas/disponibles?desde={{fechaDesde}}&hasta={{fechaHasta}}`, {
    desc: 'Elige tres sábados de fines de semana distintos entre hoy+30 y hoy+365 (desde la 10B3 solo hay fechas en viernes, sábado o domingo, y la colección cancela todo evento que crea) y un martes (día no permitido, para los 400). Genera también el sufijo único de esta ejecución (nombres, teléfonos y correos).',
    pre: [`const d = function (dias) { const f = new Date(); f.setUTCDate(f.getUTCDate() + dias); return f.toISOString().slice(0, 10); };`,
      setC('fechaDesde', 'd(30)'), setC('fechaHasta', 'd(365)')],
    test: L(st(200), js(),
      `const dia = function (f) { return new Date(f + "T00:00:00Z").getUTCDay(); };`,
      `const sumar = function (f, n) { const x = new Date(f + "T00:00:00Z"); x.setUTCDate(x.getUTCDate() + n); return x.toISOString().slice(0, 10); };`,
      `const libres = r.map(function (x) { return x.fecha; });`,
      `const sab1 = libres.find(function (f) { return dia(f) === 6 && libres.indexOf(sumar(f, 1)) >= 0; });`,
      `const sab2 = sab1 && libres.find(function (f) { return dia(f) === 6 && f >= sumar(sab1, 7); });`,
      `const sab3 = sab2 && libres.find(function (f) { return dia(f) === 6 && f >= sumar(sab2, 7); });`,
      `let martes = sumar(pm.collectionVariables.get("fechaDesde"), 0); while (dia(martes) !== 2) { martes = sumar(martes, 1); }`,
      `pm.test("Solo hay fechas en viernes, sábado o domingo", function () { pm.expect(libres.filter(function (f) { return [5, 6, 0].indexOf(dia(f)) < 0; })).to.eql([]); });`,
      `pm.test("Hay tres sábados libres de fines de semana distintos (el primero con su domingo)", function () { pm.expect(sab1).to.be.a("string"); pm.expect(sab2).to.be.a("string"); pm.expect(sab3).to.be.a("string"); });`,
      setC('fechaSabado1', 'sab1'), setC('fechaDomingo1', 'sumar(sab1, 1)'), setC('fechaSabado2', 'sab2'), setC('fechaSabado3', 'sab3'), setC('fechaMartes', 'martes'),
      `const sufijo = String(Date.now()).slice(-8);`, setC('sufijo', 'sufijo')) }),
  req('00.17 Cliente propio de la colección [happy]', 'POST', `${U.cli}/clientes`, {
    desc: 'Cliente de los eventos que crea la colección (nombre, teléfono y correo únicos por ejecución). Los datos de demostración no se modifican.',
    body: '{\n  "nombreCompleto": "Prueba Postman Cliente Ñandú {{sufijo}}",\n  "telefono": "31{{sufijo}}",\n  "correo": "postman.{{sufijo}}@ejemplo.com",\n  "direccion": "Calle 1 # 2-3, Neiva"\n}',
    test: L(st(201), location(), js(), `pm.test("Nombre con ñ", function () { pm.expect(r.nombreCompleto).to.include("Ñandú"); });`, setC('clientePostmanId', 'r.id')) }),
]);

// ------------------------------------------------------------------ 01 Clientes
const clientes = carpeta('01 Clientes', 'Semana 8: happy path, datos inválidos (400), inexistente (404), conflicto (409: correo repetido), filtro por nombre, paginación y 401. Las escrituras usan un cliente propio que se borra al final.', [
  req('01.01 [happy] Cliente de demostración (tildes y ñ)', 'GET', `${U.cli}/clientes/{{demoClienteId}}`, {
    test: L(st(200), js(), `pm.test("Lucía Fernández Ospina", function () { pm.expect(r.nombreCompleto).to.eql("Lucía Fernández Ospina"); });`) }),
  req('01.02 [filtro] Por nombre (María)', 'GET', `${U.cli}/clientes?nombre=Mar%C3%ADa&size=100`, {
    test: L(st(200), pagina(), `pm.test("Todos contienen 'María' e incluye a María José Cañón Díaz", function () { r.contenido.forEach(function (c) { pm.expect(c.nombreCompleto.toLowerCase()).to.include("maría"); }); pm.expect(r.contenido.map(function (c) { return c.nombreCompleto; })).to.include("María José Cañón Díaz"); });`) }),
  req('01.03 [paginación] page=1, size=2', 'GET', `${U.cli}/clientes?page=1&size=2&sort=nombreCompleto,asc`, {
    test: L(st(200), pagina(2), `pm.test("pagina = 1 y totalPaginas = ceil(total / 2)", function () { pm.expect(r.pagina).to.eql(1); pm.expect(r.totalPaginas).to.eql(Math.ceil(r.totalElementos / 2)); });`) }),
  req('01.04 [400] size mayor que 100', 'GET', `${U.cli}/clientes?size=500`, { test: e400('size') }),
  req('01.05 [happy] Crear cliente', 'POST', `${U.cli}/clientes`, {
    body: '{\n  "nombreCompleto": "Prueba Postman Valeria Ñáñez Gutiérrez {{sufijo}}",\n  "telefono": "32{{sufijo}}",\n  "correo": "valeria.{{sufijo}}@ejemplo.com",\n  "direccion": "Cra. 5 # 10-20, Neiva"\n}',
    test: L(st(201), location(), js(), `pm.test("Datos guardados", function () { pm.expect(r.nombreCompleto).to.include("Valeria Ñáñez Gutiérrez"); pm.expect(r.correo).to.eql("valeria." + pm.collectionVariables.get("sufijo") + "@ejemplo.com"); });`, setC('clientePruebaId', 'r.id')) }),
  req('01.06 [409] Correo repetido', 'POST', `${U.cli}/clientes`, {
    body: '{\n  "nombreCompleto": "Prueba Postman Otra persona",\n  "telefono": "33{{sufijo}}",\n  "correo": "valeria.{{sufijo}}@ejemplo.com"\n}',
    test: e409('ya se encuentra registrado') }),
  req('01.07 [400] Datos inválidos (detalle por campo)', 'POST', `${U.cli}/clientes`, {
    body: { nombreCompleto: 'Lu', telefono: '12', correo: 'lucia@@ejemplo' }, test: e400('nombreCompleto', 'telefono', 'correo') }),
  req('01.08 [happy] Actualizar cliente', 'PUT', `${U.cli}/clientes/{{clientePruebaId}}`, {
    body: '{\n  "nombreCompleto": "Prueba Postman Valeria Ñáñez Gutiérrez {{sufijo}}",\n  "telefono": "32{{sufijo}}",\n  "correo": "valeria.{{sufijo}}@ejemplo.com",\n  "direccion": "Avenida La Toma # 20-10, Neiva"\n}',
    test: L(st(200), js(), `pm.test("Dirección nueva", function () { pm.expect(r.direccion).to.eql("Avenida La Toma # 20-10, Neiva"); });`) }),
  req('01.09 [404] Cliente inexistente', 'GET', `${U.cli}/clientes/${NX}`, { test: e404('No existe un cliente con id') }),
  req('01.10 [400] Id que no es UUID', 'GET', `${U.cli}/clientes/abc`, { test: e400('id') }),
  req('01.11 [happy] Borrar el cliente creado', 'DELETE', `${U.cli}/clientes/{{clientePruebaId}}`, { test: L(st(204)) }),
  req('01.12 [404] El cliente borrado ya no existe', 'GET', `${U.cli}/clientes/{{clientePruebaId}}`, { test: e404('No existe un cliente') }),
  req('01.13 [401] Sin token', 'GET', `${U.cli}/clientes`, { sinToken: true, test: e401() }),
]);

// ------------------------------------------------------------------ 02 Lugares
const lugares = carpeta('02 Lugares', 'Happy path, filtros por estado y tipo, paginación, 400, 404 y 401. Las escrituras usan un lugar propio que se borra al final.', [
  req('02.01 [happy] Lugar de demostración', 'GET', `${U.lug}/lugares/{{demoLugarId}}`, {
    test: L(st(200), js(), `pm.test("Salón de Eventos Los Ocobos, ACTIVO, capacidad ≥ 105", function () { pm.expect(r.nombre).to.eql("Salón de Eventos Los Ocobos"); pm.expect(r.estado).to.eql("ACTIVO"); pm.expect(r.capacidad).to.be.at.least(105); });`) }),
  req('02.02 [filtro] estado=ACTIVO y tipo=salon', 'GET', `${U.lug}/lugares?estado=ACTIVO&tipo=salon&size=100`, {
    test: L(st(200), pagina(), `pm.test("Todos ACTIVO y SALON", function () { pm.expect(r.contenido.length).to.be.above(0); r.contenido.forEach(function (l) { pm.expect(l.estado).to.eql("ACTIVO"); pm.expect(l.tipo.toUpperCase()).to.eql("SALON"); }); });`) }),
  req('02.03 [filtro] estado=INACTIVO', 'GET', `${U.lug}/lugares?estado=INACTIVO&size=100`, {
    test: L(st(200), pagina(), `pm.test("Todos INACTIVO e incluye Hacienda El Pórtico", function () { r.contenido.forEach(function (l) { pm.expect(l.estado).to.eql("INACTIVO"); }); pm.expect(r.contenido.map(function (l) { return l.nombre; })).to.include("Hacienda El Pórtico"); });`) }),
  req('02.04 [paginación] page=1, size=1', 'GET', `${U.lug}/lugares?page=1&size=1`, {
    test: L(st(200), pagina(1), `pm.test("pagina = 1", function () { pm.expect(r.pagina).to.eql(1); });`) }),
  req('02.05 [400] estado no permitido en el filtro', 'GET', `${U.lug}/lugares?estado=CERRADO`, { test: e400('estado') }),
  req('02.06 [400] Crear con datos inválidos', 'POST', `${U.lug}/lugares`, { body: { nombre: '', direccion: 'Calle 1', tipo: 'SALON', capacidad: 0 }, test: e400('nombre', 'capacidad') }),
  req('02.07 [happy] Crear lugar', 'POST', `${U.lug}/lugares`, {
    body: '{\n  "nombre": "Prueba Postman Kiosco {{sufijo}}",\n  "direccion": "Vereda El Caguán, Neiva",\n  "tipo": "KIOSCO",\n  "capacidad": 80\n}',
    test: L(st(201), location(), js(), `pm.test("Nace ACTIVO", function () { pm.expect(r.estado).to.eql("ACTIVO"); });`, setC('lugarPruebaId', 'r.id')) }),
  req('02.08 [happy] Actualizar lugar', 'PUT', `${U.lug}/lugares/{{lugarPruebaId}}`, {
    body: '{\n  "nombre": "Prueba Postman Kiosco {{sufijo}}",\n  "direccion": "Vereda El Caguán, Neiva",\n  "tipo": "KIOSCO",\n  "capacidad": 90\n}',
    test: L(st(200), js(), `pm.test("capacidad = 90", function () { pm.expect(r.capacidad).to.eql(90); });`) }),
  req('02.09 [400] Estado no permitido', 'PATCH', `${U.lug}/lugares/{{lugarPruebaId}}/estado`, { body: { estado: 'CERRADO' }, test: L(e400('estado'), msg('Los datos enviados no son válidos')) }),
  req('02.10 [happy] Inactivar lugar', 'PATCH', `${U.lug}/lugares/{{lugarPruebaId}}/estado`, { body: { estado: 'INACTIVO' },
    test: L(st(200), js(), `pm.test("INACTIVO", function () { pm.expect(r.estado).to.eql("INACTIVO"); });`) }),
  req('02.11 [happy] Borrar lugar', 'DELETE', `${U.lug}/lugares/{{lugarPruebaId}}`, { test: L(st(204)) }),
  req('02.12 [404] Lugar borrado', 'GET', `${U.lug}/lugares/{{lugarPruebaId}}`, { test: e404('No existe un lugar con id') }),
  req('02.13 [400] Id que no es UUID', 'GET', `${U.lug}/lugares/abc`, { test: e400('id') }),
  req('02.14 [401] Sin token', 'GET', `${U.lug}/lugares`, { sinToken: true, test: e401() }),
]);

// ------------------------------------------------------------------ 03 Proveedores
const proveedores = carpeta('03 Proveedores', 'Happy path, filtros por categoría y estado, paginación, 400, 404, 409 (nombre repetido) y 401. Crea un proveedor propio (lo usa el flujo y se inactiva en la limpieza).', [
  req('03.01 [happy] Proveedor de demostración', 'GET', `${U.prov}/proveedores/{{demoProveedorId}}`, {
    test: L(st(200), js(), `pm.test("Sonido y Luces del Huila, activo", function () { pm.expect(r.nombre).to.eql("Sonido y Luces del Huila"); pm.expect(r.activo).to.eql(true); });`) }),
  req('03.02 [filtro] categoria=sonido y activo=true', 'GET', `${U.prov}/proveedores?categoria=sonido&activo=true&size=100`, {
    test: L(st(200), pagina(), `pm.test("Todos SONIDO y activos", function () { pm.expect(r.contenido.length).to.be.above(0); r.contenido.forEach(function (p) { pm.expect(p.categoria.toUpperCase()).to.eql("SONIDO"); pm.expect(p.activo).to.eql(true); }); });`) }),
  req('03.03 [paginación] size=2', 'GET', `${U.prov}/proveedores?size=2&sort=nombre,asc`, { test: L(st(200), pagina(2)) }),
  req('03.04 [happy] Asignaciones del evento de demostración', 'GET', `${U.prov}/proveedores/eventos/{{demoEventoReservadoId}}`, {
    test: L(st(200), pagina(), `pm.test("Las mismas asignaciones de la foto inicial", function () { pm.expect(String(r.totalElementos)).to.eql(pm.collectionVariables.get("fotoAsignaciones")); });`) }),
  req('03.05 [404] Asignaciones de un evento inexistente', 'GET', `${U.prov}/proveedores/eventos/${NX}`, { test: e404('No existe un evento con id') }),
  req('03.06 [400] Crear con datos inválidos', 'POST', `${U.prov}/proveedores`, { body: { nombre: '', categoria: '', contacto: '' }, test: e400('nombre', 'categoria', 'contacto') }),
  req('03.07 [happy] Crear proveedor', 'POST', `${U.prov}/proveedores`, {
    body: '{\n  "nombre": "Prueba Postman Decoraciones {{sufijo}}",\n  "categoria": "DECORACION",\n  "contacto": "34{{sufijo}}"\n}',
    test: L(st(201), location(), js(), `pm.test("Nace activo", function () { pm.expect(r.activo).to.eql(true); });`, setC('proveedorPruebaId', 'r.id'), setC('proveedorPruebaNombre', 'r.nombre')) }),
  req('03.08 [409] Nombre repetido (sin distinguir mayúsculas)', 'POST', `${U.prov}/proveedores`, {
    body: '{\n  "nombre": "prueba postman decoraciones {{sufijo}}",\n  "categoria": "DECORACION",\n  "contacto": "3000000000"\n}',
    test: e409('Ya existe un proveedor con el nombre') }),
  req('03.09 [happy] Actualizar proveedor', 'PUT', `${U.prov}/proveedores/{{proveedorPruebaId}}`, {
    body: '{\n  "nombre": "Prueba Postman Decoraciones {{sufijo}}",\n  "categoria": "DECORACION",\n  "contacto": "35{{sufijo}}"\n}',
    test: L(st(200), js(), `pm.test("Contacto nuevo", function () { pm.expect(r.contacto).to.eql("35" + pm.collectionVariables.get("sufijo")); });`) }),
  req('03.10 [happy] Inactivar proveedor', 'PATCH', `${U.prov}/proveedores/{{proveedorPruebaId}}/estado`, { body: { activo: false },
    test: L(st(200), js(), `pm.test("activo = false", function () { pm.expect(r.activo).to.eql(false); });`) }),
  req('03.11 [happy] Activar proveedor de nuevo', 'PATCH', `${U.prov}/proveedores/{{proveedorPruebaId}}/estado`, { body: { activo: true },
    test: L(st(200), js(), `pm.test("activo = true", function () { pm.expect(r.activo).to.eql(true); });`) }),
  req('03.12 [404] Proveedor inexistente', 'GET', `${U.prov}/proveedores/${NX}`, { test: e404('No existe un proveedor con id') }),
  req('03.13 [401] Sin token', 'GET', `${U.prov}/proveedores`, { sinToken: true, test: e401() }),
]);

// ------------------------------------------------------------------ 04 Eventos
const eventos = carpeta('04 Eventos', 'Happy path, filtros (estado, tipo, rango de fechas), paginación, 400, 404, 409 (regla del fin de semana, fecha ocupada, confirmar sin contrato, lugar inactivo, fecha con evento, cancelar dos veces) y 401. Crea un evento propio (E1) y lo cancela.', [
  req('04.01 [happy] Evento de demostración', 'GET', `${U.ev}/eventos/{{demoEventoReservadoId}}`, {
    test: L(st(200), js(), `pm.test("Quince años de Lucía, RESERVADO, 105 invitados", function () { pm.expect(r.nombreEvento).to.eql("Quince años de Lucía"); pm.expect(r.estado).to.eql("RESERVADO"); pm.expect(r.numInvitados).to.eql(105); });`) }),
  req('04.02 [filtro] estado=RESERVADO y tipo=QUINCE_ANOS', 'GET', `${U.ev}/eventos?estado=RESERVADO&tipo=QUINCE_ANOS&size=100`, {
    test: L(st(200), pagina(), `pm.test("Todos RESERVADO y QUINCE_ANOS", function () { pm.expect(r.contenido.length).to.be.above(0); r.contenido.forEach(function (e) { pm.expect(e.estado).to.eql("RESERVADO"); pm.expect(e.tipoEvento).to.eql("QUINCE_ANOS"); }); });`) }),
  req('04.03 [filtro] Rango de fechas (desde = hasta = fecha de la demo)', 'GET', `${U.ev}/eventos?desde={{demoFecha}}&hasta={{demoFecha}}`, {
    pre: [`pm.collectionVariables.set("demoFecha", JSON.parse(pm.collectionVariables.get("fotoReservado")).fecha);`],
    test: L(st(200), pagina(), `pm.test("Todos en esa fecha e incluye el evento de demostración", function () { r.contenido.forEach(function (e) { pm.expect(e.fecha).to.eql(pm.collectionVariables.get("demoFecha")); }); pm.expect(r.contenido.map(function (e) { return e.id; })).to.include(pm.environment.get("demoEventoReservadoId")); });`) }),
  req('04.04 [paginación] page=0, size=1, sort=fecha,desc', 'GET', `${U.ev}/eventos?page=0&size=1&sort=fecha,desc`, {
    test: L(st(200), pagina(1), `pm.test("totalPaginas = totalElementos con size 1", function () { pm.expect(r.totalPaginas).to.eql(r.totalElementos); });`) }),
  req('04.05 [400] size mayor que 100', 'GET', `${U.ev}/eventos?size=500`, { test: e400('size') }),
  req('04.06 [400] sort con un campo no permitido', 'GET', `${U.ev}/eventos?sort=clave,asc`, { test: L(e400('sort'), msg('campos permitidos')) }),
  req('04.07 [400] Id que no es UUID', 'GET', `${U.ev}/eventos/abc`, { test: e400('id') }),
  req('04.08 [400] Ruta sin identificador (//)', 'PATCH', `${U.ev}/eventos//confirmar`, { test: L(err(400), msg('La ruta no es válida')) }),
  req('04.09 [404] Evento inexistente', 'GET', `${U.ev}/eventos/${NX}`, { test: e404('No existe un evento con id') }),
  req('04.10 [happy] Agenda de fechas (la fecha de la demo está OCUPADA)', 'GET', `${U.ev}/fechas?desde={{demoFecha}}&hasta={{demoFecha}}`, {
    test: L(st(200), js(), `pm.test("OCUPADA con el evento de demostración", function () { pm.expect(r[0].estado).to.eql("OCUPADA"); pm.expect(r[0].eventoId).to.eql(pm.environment.get("demoEventoReservadoId")); });`) }),
  req('04.11 [400] Rango de fechas invertido', 'GET', `${U.ev}/fechas/disponibles?desde=2027-01-10&hasta=2027-01-01`, { test: L(e400('hasta'), msg('debe ser posterior o igual a desde')) }),
  // 10B3: regla de días permitidos (por defecto viernes, sábado y domingo)
  req('04.11b [400] Crear un evento un martes (día no permitido)', 'POST', `${U.ev}/eventos`, {
    desc: 'Regla de negocio (10B3): la empresa solo trabaja viernes, sábado y domingo (maxi-eventos.dias-permitidos). No se crea nada.',
    body: '{\n  "nombreEvento": "Prueba Postman Boda en martes {{sufijo}}",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "{{fechaMartes}}",\n  "tipoEvento": "BODA",\n  "cantidadAdultos": 80,\n  "cantidadNinos": 0,\n  "zona": "NEIVA"\n}',
    test: L(e400('fecha'), `pm.test("errores[fecha] con el mensaje de la regla", function () { const e = r.errores.find(function (x) { return x.campo === "fecha"; }); pm.expect(e.mensaje).to.eql("Solo se pueden registrar eventos en viernes, sábado o domingo."); });`) }),
  req('04.11c [400] Bloquear a mano un martes', 'PUT', `${U.ev}/fechas/{{fechaMartes}}/estado`, {
    body: { estado: 'OCUPADA' }, test: L(e400('fecha'), msg('Solo se pueden registrar eventos en viernes, sábado o domingo.')) }),
  req('04.11d [happy] Liberar un martes se permite (no tiene evento)', 'PUT', `${U.ev}/fechas/{{fechaMartes}}/estado`, {
    body: { estado: 'DISPONIBLE' }, test: L(st(200), js(), `pm.test("DISPONIBLE", function () { pm.expect(r.estado).to.eql("DISPONIBLE"); pm.expect(r.fecha).to.eql(pm.collectionVariables.get("fechaMartes")); });`) }),
  req('04.11e [happy] Las fechas disponibles de esa semana: solo viernes, sábado y domingo', 'GET', `${U.ev}/fechas/disponibles?desde={{fechaMartes}}&hasta={{fechaMartes}}`, {
    test: L(st(200), js(), `pm.test("El martes no aparece", function () { pm.expect(r).to.eql([]); });`) }),
  req('04.12 [400] Crear con datos inválidos (fecha pasada, nombre corto, adultos negativos)', 'POST', `${U.ev}/eventos`, {
    body: '{\n  "nombreEvento": "X",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "2020-01-01",\n  "tipoEvento": "BODA",\n  "cantidadAdultos": -1,\n  "cantidadNinos": 0,\n  "zona": "NEIVA"\n}',
    test: e400('nombreEvento', 'cantidadAdultos') }),
  req('04.13 [400] Tipo de evento no permitido', 'POST', `${U.ev}/eventos`, {
    body: '{\n  "nombreEvento": "Prueba Postman Grado",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "{{fechaSabado1}}",\n  "tipoEvento": "GRADO",\n  "cantidadAdultos": 10,\n  "cantidadNinos": 0,\n  "zona": "NEIVA"\n}',
    test: L(e400('tipoEvento')) }),
  req('04.14 [404] Crear con un cliente inexistente', 'POST', `${U.ev}/eventos`, {
    body: `{\n  "nombreEvento": "Boda de prueba",\n  "clienteId": "${NX}",\n  "fecha": "{{fechaSabado1}}",\n  "tipoEvento": "BODA",\n  "cantidadAdultos": 10,\n  "cantidadNinos": 0,\n  "zona": "NEIVA"\n}`,
    test: e404('No existe un cliente con id') }),
  req('04.15 [happy] Crear evento E1 (sábado libre)', 'POST', `${U.ev}/eventos`, {
    body: '{\n  "nombreEvento": "Prueba Postman Boda E1 {{sufijo}}",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "{{fechaSabado1}}",\n  "tipoEvento": "BODA",\n  "cantidadAdultos": 80,\n  "cantidadNinos": 0,\n  "zona": "NEIVA"\n}',
    test: L(st(201), location(), js(), `pm.test("PENDIENTE con 80 invitados", function () { pm.expect(r.estado).to.eql("PENDIENTE"); pm.expect(r.numInvitados).to.eql(80); });`, setC('evento1Id', 'r.id')) }),
  req('04.16 [409] Otro evento el domingo del mismo fin de semana', 'POST', `${U.ev}/eventos`, {
    body: '{\n  "nombreEvento": "Prueba Postman Quince años {{sufijo}}",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "{{fechaDomingo1}}",\n  "tipoEvento": "QUINCE_ANOS",\n  "cantidadAdultos": 50,\n  "cantidadNinos": 5,\n  "zona": "NEIVA"\n}',
    test: e409('Solo se permite un evento por fin de semana') }),
  req('04.17 [409] Otro evento en la misma fecha', 'POST', `${U.ev}/eventos`, {
    body: '{\n  "nombreEvento": "Prueba Postman Boda repetida {{sufijo}}",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "{{fechaSabado1}}",\n  "tipoEvento": "BODA",\n  "cantidadAdultos": 50,\n  "cantidadNinos": 0,\n  "zona": "NEIVA"\n}',
    test: e409('no está disponible') }),
  req('04.18 [409] Confirmar sin contrato', 'PATCH', `${U.ev}/eventos/{{evento1Id}}/confirmar`, { test: e409('El evento no tiene contrato') }),
  req('04.19 [409] Asignar un lugar INACTIVO', 'PATCH', `${U.ev}/eventos/{{evento1Id}}/lugar`, {
    body: '{\n  "lugarId": "{{demoLugarInactivoId}}"\n}', test: e409('INACTIVO') }),
  req('04.20 [404] Asignar un lugar inexistente', 'PATCH', `${U.ev}/eventos/{{evento1Id}}/lugar`, {
    body: `{\n  "lugarId": "${NX}"\n}`, test: e404('No existe un lugar con id') }),
  req('04.21 [409] Liberar a mano una fecha que tiene evento', 'PUT', `${U.ev}/fechas/{{fechaSabado1}}/estado`, {
    body: { estado: 'DISPONIBLE' }, test: e409('Para cambiarla, cancela o confirma el evento') }),
  req('04.22 [happy] Cancelar E1', 'PATCH', `${U.ev}/eventos/{{evento1Id}}/cancelar`, {
    test: L(st(200), js(), `pm.test("CANCELADO", function () { pm.expect(r.estado).to.eql("CANCELADO"); });`) }),
  req('04.23 [409] Cancelar E1 otra vez', 'PATCH', `${U.ev}/eventos/{{evento1Id}}/cancelar`, { test: e409('El evento ya está cancelado.') }),
  req('04.24 [happy] La fecha de E1 vuelve a estar disponible', 'GET', `${U.ev}/fechas/disponibles?desde={{fechaSabado1}}&hasta={{fechaSabado1}}`, {
    test: L(st(200), js(), `pm.test("La fecha está en la lista de disponibles", function () { pm.expect(r.map(function (f) { return f.fecha; })).to.include(pm.collectionVariables.get("fechaSabado1")); });`) }),
  req('04.25 [401] Sin token', 'GET', `${U.ev}/eventos`, { sinToken: true, test: e401() }),
]);

// ------------------------------------------------------------------ 05 Finanzas
const finanzas = carpeta('05 Finanzas', 'Consultas de la demostración, cotización (201 / 200 / 404 / 409), pagos con idempotencia (201 / 200 / 409), 400 por campo, adicionales y 401. Crea un evento propio (E2, un sábado) y lo cancela. El reto de la semana 8 está en la carpeta 11.', [
  req('05.01 [happy] Cotización de la demostración', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/cotizacion`, {
    test: L(st(200), js(), `pm.test("Cotización QUINCE_ANOS con reserva", function () { pm.expect(r.tipoEvento).to.eql("QUINCE_ANOS"); pm.expect(r.montoReservaRequerido).to.be.above(0); pm.expect(r.moneda).to.eql("COP"); });`) }),
  req('05.02 [happy] Balance de la demostración', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/balance`, {
    test: L(st(200), js(), `pm.test("Igual a la foto inicial", function () { pm.expect(r).to.eql(JSON.parse(pm.collectionVariables.get("fotoBalance"))); });`) }),
  req('05.03 [paginación] Pagos de la demostración (size=1)', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/pagos?size=1&sort=creadoEn,asc`, {
    test: L(st(200), pagina(1), `pm.test("Total igual a la foto inicial", function () { pm.expect(String(r.totalElementos)).to.eql(pm.collectionVariables.get("fotoPagos")); });`) }),
  req('05.04 [happy] Adicionales de la demostración', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/adicionales`, {
    test: L(st(200), js(), `pm.test("Mismos adicionales que la foto inicial", function () { pm.expect(String(r.length)).to.eql(pm.collectionVariables.get("fotoAdicionales")); });`) }),
  req('05.05 [happy] Catálogo de servicios adicionales', 'GET', `${U.fin}/servicios-adicionales`, {
    test: L(st(200), js(), `pm.test("Incluye transporte-adicional", function () { pm.expect(r.map(function (a) { return a.codigo; })).to.include("transporte-adicional"); });`) }),
  req('05.06 [404] Balance de un evento sin cotización', 'GET', `${U.fin}/eventos/${NX}/balance`, { test: e404('No existe una cotización para el evento') }),
  req('05.07 [400] Id que no es UUID', 'GET', `${U.fin}/eventos/abc/balance`, { test: e400('eventoId') }),
  req('05.08 [happy] Crear evento E2 (tercer sábado libre)', 'POST', `${U.ev}/eventos`, {
    body: '{\n  "nombreEvento": "Prueba Postman Quince años E2 {{sufijo}}",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "{{fechaSabado3}}",\n  "tipoEvento": "QUINCE_ANOS",\n  "cantidadAdultos": 90,\n  "cantidadNinos": 15,\n  "zona": "NEIVA"\n}',
    test: L(st(201), js(), setC('evento2Id', 'r.id')) }),
  req('05.09 [happy] Cotizar E2', 'POST', `${U.fin}/cotizaciones`, { body: '{\n  "eventoId": "{{evento2Id}}"\n}',
    test: L(st(201), location(), js(), `pm.test("Total = subtotal (NEIVA) y reserva > 0", function () { pm.expect(r.total).to.eql(r.subtotal); pm.expect(r.recargoZona).to.eql(0); pm.expect(r.montoReservaRequerido).to.be.above(0); });`, setC('cotizacion2Id', 'r.id')) }),
  req('05.10 [happy] Cotizar E2 otra vez → 200 con la misma cotización', 'POST', `${U.fin}/cotizaciones`, { body: '{\n  "eventoId": "{{evento2Id}}"\n}',
    test: L(st(200), js(), `pm.test("Misma cotización", function () { pm.expect(r.id).to.eql(pm.collectionVariables.get("cotizacion2Id")); });`) }),
  req('05.11 [409] Cotizar un evento cancelado (E1)', 'POST', `${U.fin}/cotizaciones`, { body: '{\n  "eventoId": "{{evento1Id}}"\n}', test: e409('Solo se puede cotizar un evento PENDIENTE') }),
  req('05.12 [404] Cotizar un evento inexistente', 'POST', `${U.fin}/cotizaciones`, { body: { eventoId: NX }, test: e404('No existe un evento con id') }),
  req('05.13 [400] Pago con datos inválidos', 'POST', `${U.fin}/finanzas/pagos`, {
    body: '{\n  "eventoId": "{{evento2Id}}",\n  "clienteId": "{{clientePostmanId}}",\n  "metodoPago": "",\n  "monto": -5,\n  "moneda": ""\n}',
    test: e400('metodoPago', 'monto', 'moneda') }),
  req('05.14 [400] Pago con un cliente que no es el del evento', 'POST', `${U.fin}/finanzas/pagos`, {
    body: '{\n  "eventoId": "{{evento2Id}}",\n  "clienteId": "{{demoClienteId}}",\n  "metodoPago": "EFECTIVO",\n  "monto": 1000,\n  "moneda": "COP"\n}',
    test: L(e400('clienteId'), msg('no corresponde al cliente del evento')) }),
  req('05.15 [404] Pago de un evento inexistente', 'POST', `${U.fin}/finanzas/pagos`, {
    body: `{\n  "eventoId": "${NX}",\n  "clienteId": "{{clientePostmanId}}",\n  "metodoPago": "EFECTIVO",\n  "monto": 1000,\n  "moneda": "COP"\n}`,
    test: e404('No existe un evento con id') }),
  req('05.16 [happy] Pago con clave de idempotencia', 'POST', `${U.fin}/finanzas/pagos`, {
    headers: [{ key: 'X-Idempotency-Key', value: 'postman-{{sufijo}}' }],
    body: '{\n  "eventoId": "{{evento2Id}}",\n  "clienteId": "{{clientePostmanId}}",\n  "metodoPago": "NEQUI",\n  "monto": 1000,\n  "moneda": "COP"\n}',
    test: L(st(201), location(), js(), `pm.test("PAGADO con la clave enviada", function () { pm.expect(r.estado).to.eql("PAGADO"); pm.expect(r.eventIdempotencia).to.eql("postman-" + pm.collectionVariables.get("sufijo")); });`, setC('pago2Id', 'r.pagoId')) }),
  req('05.17 [happy] Repetir el pago (misma clave) → 200, no se cobra dos veces', 'POST', `${U.fin}/finanzas/pagos`, {
    headers: [{ key: 'X-Idempotency-Key', value: 'postman-{{sufijo}}' }],
    body: '{\n  "eventoId": "{{evento2Id}}",\n  "clienteId": "{{clientePostmanId}}",\n  "metodoPago": "NEQUI",\n  "monto": 1000,\n  "moneda": "COP"\n}',
    test: L(st(200), js(), `pm.test("Mismo pago y cabecera X-Already-Processed", function () { pm.expect(r.pagoId).to.eql(pm.collectionVariables.get("pago2Id")); pm.expect(pm.response.headers.get("X-Already-Processed")).to.eql("true"); });`) }),
  req('05.18 [409] Misma clave con otro monto', 'POST', `${U.fin}/finanzas/pagos`, {
    headers: [{ key: 'X-Idempotency-Key', value: 'postman-{{sufijo}}' }],
    body: '{\n  "eventoId": "{{evento2Id}}",\n  "clienteId": "{{clientePostmanId}}",\n  "metodoPago": "NEQUI",\n  "monto": 2000,\n  "moneda": "COP"\n}',
    test: e409('ya se usó para otro pago con datos distintos') }),
  req('05.19 [happy] Pagos de E2 (solo uno)', 'GET', `${U.fin}/eventos/{{evento2Id}}/pagos`, {
    test: L(st(200), pagina(), `pm.test("Un solo pago", function () { pm.expect(r.totalElementos).to.eql(1); });`) }),
  req('05.20 [400] Adicional que no existe en el catálogo', 'POST', `${U.fin}/eventos/{{evento2Id}}/adicionales`, {
    body: { codigo: 'karaoke', cantidad: 1 }, test: L(e400('codigo'), msg('no existe en el catálogo')) }),
  req('05.21 [happy] Agregar adicional hora-extra a E2', 'POST', `${U.fin}/eventos/{{evento2Id}}/adicionales`, {
    body: { codigo: 'hora-extra', cantidad: 1 },
    test: L(st(201), location(), js(), `pm.test("Hora adicional de servicio", function () { pm.expect(r.codigo).to.eql("hora-extra"); pm.expect(r.subtotal).to.be.above(0); });`) }),
  req('05.22 [happy] Balance de E2 (cotización + adicional − abono)', 'GET', `${U.fin}/eventos/{{evento2Id}}/balance`, {
    test: L(st(200), js(), `pm.test("saldo = total − abonado y total = valorBase + adicionales", function () { pm.expect(r.totalAbonado).to.eql(1000); pm.expect(r.total).to.eql(r.valorBase + r.adicionales); pm.expect(r.saldoPendiente).to.eql(r.total - r.totalAbonado); });`) }),
  req('05.23 [happy] Cancelar E2', 'PATCH', `${U.ev}/eventos/{{evento2Id}}/cancelar`, { test: L(st(200)) }),
  req('05.24 [409] Pago de un evento cancelado', 'POST', `${U.fin}/finanzas/pagos`, {
    body: '{\n  "eventoId": "{{evento2Id}}",\n  "clienteId": "{{clientePostmanId}}",\n  "metodoPago": "EFECTIVO",\n  "monto": 1000,\n  "moneda": "COP"\n}',
    test: e409('El evento está CANCELADO y no admite abonos.') }),
  req('05.25 [401] Sin token', 'GET', `${U.fin}/servicios-adicionales`, { sinToken: true, test: e401() }),
]);

// ------------------------------------------------------------------ 06 Contratos
const contratos = carpeta('06 Contratos', 'Consultas y PDF de la demostración, paginación, 400, 404, 409 y 401. Los 409 de firma y del anexo están en el flujo (carpeta 10), sobre el evento propio.', [
  req('06.01 [happy] Contrato de la demostración', 'GET', `${U.con}/eventos/{{demoEventoReservadoId}}/contrato`, {
    test: L(st(200), js(), `pm.test("FIRMADO con la foto de los datos (tildes)", function () { pm.expect(r.estado).to.eql("FIRMADO"); pm.expect(r.clienteNombre).to.eql("Lucía Fernández Ospina"); pm.expect(r.lugarNombre).to.eql("Salón de Eventos Los Ocobos"); });`) }),
  req('06.02 [happy] Contrato por id', 'GET', `${U.con}/contratos/{{demoContratoId}}`, {
    test: L(st(200), js(), `pm.test("Mismo evento", function () { pm.expect(r.eventoId).to.eql(pm.environment.get("demoEventoReservadoId")); });`) }),
  req('06.03 [paginación] Contratos (size=1)', 'GET', `${U.con}/contratos?size=1&sort=creadoEn,desc`, { test: L(st(200), pagina(1)) }),
  req('06.04 [happy] PDF del contrato de la demostración', 'GET', `${U.con}/eventos/{{demoEventoReservadoId}}/contrato/pdf`, { test: pdf() }),
  req('06.05 [happy] PDF del anexo de la demostración', 'GET', `${U.con}/eventos/{{demoEventoReservadoId}}/adicionales/anexo-pdf`, { test: pdf() }),
  req('06.06 [404] Contrato de un evento que no lo tiene (demo PENDIENTE)', 'GET', `${U.con}/eventos/{{demoEventoPendienteId}}/contrato`, { test: e404('No existe un contrato para el evento') }),
  req('06.07 [400] Id que no es UUID', 'GET', `${U.con}/eventos/abc/contrato`, { test: e400('eventoId') }),
  req('06.08 [409] Crear el contrato de un evento cancelado (E1)', 'POST', `${U.con}/eventos/{{evento1Id}}/contrato`, { test: e409('CANCELADO') }),
  req('06.09 [404] Crear el contrato de un evento inexistente', 'POST', `${U.con}/eventos/${NX}/contrato`, { test: e404('No existe un evento con id') }),
  req('06.10 [401] Sin token', 'GET', `${U.con}/contratos`, { sinToken: true, test: e401() }),
]);

// ------------------------------------------------------------------ 07-09 Logística, auditoría y notificaciones
const logistica = carpeta('07 Logística', 'Consultas de la demostración, paginación, 400, 404 y 401. El PUT de compras (200, 400 y 404) está en el flujo, sobre el evento propio.', [
  req('07.01 [happy] Logística de la demostración', 'GET', `${U.log}/eventos/{{demoEventoReservadoId}}/logistica`, {
    test: L(st(200), js(), `pm.test("Meseros, cocina y camiones", function () { pm.expect(r.meserosRequeridos).to.be.above(0); pm.expect(r.personalCocinaRequerido).to.be.above(0); pm.expect(r.camionesTransporte).to.be.above(0); });`) }),
  req('07.02 [paginación] Compras de la demostración (size=2)', 'GET', `${U.log}/eventos/{{demoEventoReservadoId}}/compras?size=2&sort=nombreItem,asc`, { test: L(st(200), pagina(2)) }),
  req('07.03 [404] Logística de un evento sin confirmar (demo PENDIENTE)', 'GET', `${U.log}/eventos/{{demoEventoPendienteId}}/logistica`, { test: e404('No existe logística para el evento') }),
  req('07.04 [400] Id que no es UUID', 'GET', `${U.log}/eventos/abc/logistica`, { test: e400('eventoId') }),
  req('07.05 [401] Sin token', 'GET', `${U.log}/eventos/{{demoEventoReservadoId}}/logistica`, { sinToken: true, test: e401() }),
]);
const auditoria = carpeta('08 Auditoría', 'Filtros por evento y tipo, paginación, 400 y 401.', [
  req('08.01 [filtro] Por evento (demostración)', 'GET', `${U.aud}/auditoria?eventoId={{demoEventoReservadoId}}&size=100`, {
    test: L(st(200), pagina(), `pm.test("Mismo número de mensajes que la foto inicial (sin recordatorios) y todos del evento", function () { pm.expect(${SIN_RECORDATORIOS}).to.eql(pm.collectionVariables.get("fotoAuditoria")); r.contenido.forEach(function (a) { pm.expect(a.eventoId).to.eql(pm.environment.get("demoEventoReservadoId")); pm.expect(a.payload).to.be.an("object"); }); });`) }),
  req('08.02 [filtro] Por evento y tipo PAGO_REGISTRADO', 'GET', `${U.aud}/auditoria?eventoId={{demoEventoReservadoId}}&tipo=PAGO_REGISTRADO`, {
    test: L(st(200), pagina(), `pm.test("Todos PAGO_REGISTRADO del tópico pagos.registrados", function () { pm.expect(r.contenido.length).to.be.above(0); r.contenido.forEach(function (a) { pm.expect(a.tipo).to.eql("PAGO_REGISTRADO"); pm.expect(a.topico).to.eql("pagos.registrados"); }); });`) }),
  req('08.03 [paginación] size=1', 'GET', `${U.aud}/auditoria?size=1&sort=fechaRecepcion,desc`, { test: L(st(200), pagina(1)) }),
  req('08.04 [400] Tipo no permitido', 'GET', `${U.aud}/auditoria?tipo=OTRO`, { test: e400('tipo') }),
  req('08.05 [401] Sin token', 'GET', `${U.aud}/auditoria`, { sinToken: true, test: e401() }),
]);
const notificaciones = carpeta('09 Notificaciones', 'Filtro por evento, paginación, 400 y 401.', [
  req('09.01 [filtro] Por evento (demostración)', 'GET', `${U.not}/notificaciones?eventoId={{demoEventoReservadoId}}&size=100`, {
    test: L(st(200), pagina(), `pm.test("Mismo número que la foto inicial (sin recordatorios), GENERADA y para el cliente del evento", function () { pm.expect(${SIN_RECORDATORIOS}).to.eql(pm.collectionVariables.get("fotoNotificaciones")); r.contenido.forEach(function (n) { pm.expect(n.estado).to.eql("GENERADA"); pm.expect(n.destinatario).to.eql("cliente:" + pm.environment.get("demoClienteId")); }); });`) }),
  req('09.02 [paginación] size=1', 'GET', `${U.not}/notificaciones?eventoId={{demoEventoReservadoId}}&size=1`, { test: L(st(200), pagina(1)) }),
  req('09.03 [400] eventoId que no es UUID', 'GET', `${U.not}/notificaciones?eventoId=abc`, { test: e400('eventoId') }),
  req('09.04 [401] Sin token', 'GET', `${U.not}/notificaciones`, { sinToken: true, test: e401() }),
]);

// ------------------------------------------------------------------ 10 Flujo completo
const pago = (monto, metodo) => `{\n  "eventoId": "{{flujoEventoId}}",\n  "clienteId": "{{clientePostmanId}}",\n  "metodoPago": "${metodo}",\n  "monto": ${monto},\n  "moneda": "COP"\n}`;
const flujo = carpeta('10 Flujo completo del negocio', 'Pasos 1 a 11 del flujo, en orden, sobre un evento propio (sábado libre): evento → lugar → cotización → contrato → firma → abonos → confirmación → logística por Kafka → adicional → recálculo y anexo → proveedores → compras → notificaciones y auditoría. Incluye los 409 de cada paso.', [
  req('10.01 Crear el evento (PENDIENTE)', 'POST', `${U.ev}/eventos`, {
    body: '{\n  "nombreEvento": "Prueba Postman Quince años {{sufijo}}",\n  "clienteId": "{{clientePostmanId}}",\n  "fecha": "{{fechaSabado2}}",\n  "tipoEvento": "QUINCE_ANOS",\n  "cantidadAdultos": 90,\n  "cantidadNinos": 15,\n  "zona": "NEIVA"\n}',
    test: L(st(201), location(), js(), `pm.test("PENDIENTE", function () { pm.expect(r.estado).to.eql("PENDIENTE"); });`, setC('flujoEventoId', 'r.id')) }),
  req('10.02 La fecha queda PENDIENTE', 'GET', `${U.ev}/fechas?desde={{fechaSabado2}}&hasta={{fechaSabado2}}`, {
    test: L(st(200), js(), `pm.test("PENDIENTE con el evento", function () { pm.expect(r[0].estado).to.eql("PENDIENTE"); pm.expect(r[0].eventoId).to.eql(pm.collectionVariables.get("flujoEventoId")); });`) }),
  req('10.03 Asignar el lugar (Salón Los Ocobos, ACTIVO)', 'PATCH', `${U.ev}/eventos/{{flujoEventoId}}/lugar`, { body: '{\n  "lugarId": "{{demoLugarId}}"\n}',
    test: L(st(200), js(), `pm.test("lugarId asignado", function () { pm.expect(r.lugarId).to.eql(pm.environment.get("demoLugarId")); });`) }),
  req('10.04 Cotizar', 'POST', `${U.fin}/cotizaciones`, { body: '{\n  "eventoId": "{{flujoEventoId}}"\n}',
    test: L(st(201), js(), `pm.test("105 invitados, reserva > 0", function () { pm.expect(r.invitados).to.eql(105); pm.expect(r.montoReservaRequerido).to.be.above(0); });`,
      setC('flujoReserva', 'String(r.montoReservaRequerido)'), setC('flujoAbono1', 'String(Math.round(r.montoReservaRequerido * 0.4))'), setC('flujoAbono2', 'String(Math.round((r.montoReservaRequerido - Math.round(r.montoReservaRequerido * 0.4)) * 100) / 100)')) }),
  req('10.05 Crear el contrato (PENDIENTE, con la foto de los datos)', 'POST', `${U.con}/eventos/{{flujoEventoId}}/contrato`, {
    test: L(st(201), location(), js(), `pm.test("PENDIENTE con cliente y lugar", function () { pm.expect(r.estado).to.eql("PENDIENTE"); pm.expect(r.clienteNombre).to.include("Prueba Postman Cliente Ñandú"); pm.expect(r.lugarNombre).to.eql("Salón de Eventos Los Ocobos"); });`) }),
  req('10.06 [409] Confirmar con el contrato sin firmar', 'PATCH', `${U.ev}/eventos/{{flujoEventoId}}/confirmar`, { test: e409('debe estar FIRMADO') }),
  req('10.07 Firmar el contrato', 'PATCH', `${U.con}/eventos/{{flujoEventoId}}/contrato/marcar-firmado`, {
    test: L(st(200), js(), `pm.test("FIRMADO con fecha de firma", function () { pm.expect(r.estado).to.eql("FIRMADO"); pm.expect(r.firmadoEn).to.be.a("string"); });`) }),
  req('10.08 [409] Firmar dos veces', 'PATCH', `${U.con}/eventos/{{flujoEventoId}}/contrato/marcar-firmado`, { test: e409('ya está FIRMADO') }),
  req('10.09 [409] Confirmar sin abonos', 'PATCH', `${U.ev}/eventos/{{flujoEventoId}}/confirmar`, { test: e409('no alcanza el monto de reserva requerido') }),
  req('10.10 Primer abono (40 % de la reserva)', 'POST', `${U.fin}/finanzas/pagos`, { body: pago('{{flujoAbono1}}', 'TRANSFERENCIA'),
    test: L(st(201), js(), `pm.test("PAGADO", function () { pm.expect(r.estado).to.eql("PAGADO"); });`) }),
  req('10.11 [409] Confirmar con abono insuficiente', 'PATCH', `${U.ev}/eventos/{{flujoEventoId}}/confirmar`, { test: e409('Faltan') }),
  req('10.12 Segundo abono (resto de la reserva)', 'POST', `${U.fin}/finanzas/pagos`, { body: pago('{{flujoAbono2}}', 'EFECTIVO'), test: L(st(201)) }),
  req('10.13 Confirmar el evento (RESERVADO)', 'PATCH', `${U.ev}/eventos/{{flujoEventoId}}/confirmar`, {
    test: L(st(200), js(), `pm.test("RESERVADO", function () { pm.expect(r.estado).to.eql("RESERVADO"); });`) }),
  // Fase 11I: el 409 "No se puede confirmar un evento cuya fecha ya pasó." NO está en la colección. La API no deja crear
  // eventos en fechas pasadas y los datos se crean solo por la API (no se siembran en la base desde Postman ni desde los
  // scripts), así que esa regla la prueban solo EventoServiceConfirmacionTest y EventoApiIntegracionTest, con un Clock fijo.
  req('10.14 [409] Confirmar otra vez','PATCH', `${U.ev}/eventos/{{flujoEventoId}}/confirmar`, { test: e409('ya está confirmado') }),
  req('10.15 La fecha queda OCUPADA', 'GET', `${U.ev}/fechas?desde={{fechaSabado2}}&hasta={{fechaSabado2}}`, {
    test: L(st(200), js(), `pm.test("OCUPADA", function () { pm.expect(r[0].estado).to.eql("OCUPADA"); });`) }),
  req('10.16 Logística calculada por Kafka (EVENTO_CONFIRMADO)', 'GET', `${U.log}/eventos/{{flujoEventoId}}/logistica`, { pre: espera(),
    test: reintentar('return pm.response.code === 200;', L(st(200), js(), `pm.test("Requerimientos calculados", function () { pm.expect(r.meserosRequeridos).to.be.above(0); });`, setC('flujoCamiones', 'String(r.camionesTransporte)'))) }),
  req('10.17 [409] Anexo sin servicios adicionales', 'GET', `${U.con}/eventos/{{flujoEventoId}}/adicionales/anexo-pdf`, { test: e409('no tiene servicios adicionales') }),
  req('10.17b [409] Anexo por el gateway (/documentos) sin servicios adicionales', 'GET', `${U.gw}/documentos/eventos/{{flujoEventoId}}/anexo.pdf`, {
    desc: 'Fase 10: la interfaz descarga los PDF por el gateway. El 409 de Contratos llega como ErrorResponse JSON con el mensaje del servicio.',
    headers: ACEPTA_PDF, test: errDoc(409, 'no tiene servicios adicionales') }),
  // ---- 10B3: lista de chequeo de compras (solo sobre el evento propio del flujo; la demostración no se toca)
  req('10.17c Lista de compras recién calculada (todo CALCULADO y sin marcar)', 'GET', `${U.log}/eventos/{{flujoEventoId}}/compras?size=100`, {
    test: L(st(200), pagina(), `pm.test("Ítems calculados por el sistema", function () { pm.expect(r.contenido.length).to.be.above(2); r.contenido.forEach(function (i) { pm.expect(i.origen).to.eql("CALCULADO"); pm.expect(i.comprado).to.eql(false); }); });`,
      `const id = function (n) { const i = r.contenido.find(function (x) { return x.nombreItem === n; }); return i ? i.id : ""; };`,
      setC('flujoHieloId', 'id("Hielo")'), setC('flujoVasosId', 'id("Vasos")'),
      `pm.test("Tiene Hielo y Vasos", function () { pm.expect(pm.collectionVariables.get("flujoHieloId")).to.not.be.empty; pm.expect(pm.collectionVariables.get("flujoVasosId")).to.not.be.empty; });`) }),
  req('10.17d [happy] Agregar un ítem manual', 'POST', `${U.log}/eventos/{{flujoEventoId}}/compras`, {
    body: { nombreItem: 'Recuerdos para invitados', cantidadEstimada: '105 unidades', nota: 'Bolsas de tela con dulces' },
    test: L(st(201), location(), js(), `pm.test("MANUAL, sin marcar y con su nota", function () { pm.expect(r.origen).to.eql("MANUAL"); pm.expect(r.comprado).to.eql(false); pm.expect(r.nota).to.eql("Bolsas de tela con dulces"); });`,
      setC('flujoManualId', 'r.id')) }),
  req('10.17e [400] Agregar un ítem sin nombre ni cantidad', 'POST', `${U.log}/eventos/{{flujoEventoId}}/compras`, {
    body: { nombreItem: ' ', cantidadEstimada: '' }, test: e400('nombreItem', 'cantidadEstimada') }),
  req('10.17f [409] Agregar un ítem con un nombre que ya está (minúsculas y espacios)', 'POST', `${U.log}/eventos/{{flujoEventoId}}/compras`, {
    body: { nombreItem: '  recuerdos PARA invitados ', cantidadEstimada: '1' }, test: e409('Ya hay un ítem «recuerdos PARA invitados» en la lista de compras') }),
  req('10.17g [404] Agregar un ítem a un evento sin logística', 'POST', `${U.log}/eventos/${NX}/compras`, {
    body: { nombreItem: 'Velas', cantidadEstimada: '20' }, test: e404('No existe logística para el evento') }),
  req('10.17h Marcar como conseguidos el ítem manual y el hielo', 'PUT', `${U.log}/eventos/{{flujoEventoId}}/compras?size=100`, {
    body: '[\n  { "id": "{{flujoManualId}}", "comprado": true },\n  { "id": "{{flujoHieloId}}", "comprado": true }\n]',
    test: L(st(200), pagina(), `pm.test("Los dos quedan conseguidos", function () { [pm.collectionVariables.get("flujoManualId"), pm.collectionVariables.get("flujoHieloId")].forEach(function (k) { pm.expect(r.contenido.find(function (x) { return x.id === k; }).comprado).to.eql(true); }); });`) }),
  req('10.17i Editar un ítem calculado (conserva origen y marca)', 'PUT', `${U.log}/eventos/{{flujoEventoId}}/compras/{{flujoHieloId}}`, {
    body: { nombreItem: 'Hielo en cubos', cantidadEstimada: '15 bolsas', nota: 'Pedirlo el viernes' },
    test: L(st(200), js(), `pm.test("Editado, CALCULADO y sigue conseguido", function () { pm.expect(r.nombreItem).to.eql("Hielo en cubos"); pm.expect(r.origen).to.eql("CALCULADO"); pm.expect(r.comprado).to.eql(true); });`) }),
  req('10.17j [409] Quitar un ítem calculado', 'DELETE', `${U.log}/eventos/{{flujoEventoId}}/compras/{{flujoVasosId}}`, {
    test: e409('Los ítems calculados por el sistema no se pueden quitar') }),
  req('10.17k [404] Quitar un ítem que no es del evento', 'DELETE', `${U.log}/eventos/{{flujoEventoId}}/compras/${NX}`, {
    test: e404('No existe el ítem de compra') }),
  req('10.18 Agregar el adicional transporte-adicional', 'POST', `${U.fin}/eventos/{{flujoEventoId}}/adicionales`, { body: { codigo: 'transporte-adicional', cantidad: 1 },
    test: L(st(201), js(), `pm.test("Transporte adicional", function () { pm.expect(r.nombre).to.eql("Transporte adicional"); });`) }),
  req('10.19 Logística recalculada (un camión más)', 'GET', `${U.log}/eventos/{{flujoEventoId}}/logistica`, { pre: espera(),
    test: reintentar('return pm.response.code === 200 && pm.response.json().camionesTransporte === Number(pm.collectionVariables.get("flujoCamiones")) + 1;',
      L(st(200), js(), `pm.test("camiones + 1", function () { pm.expect(r.camionesTransporte).to.eql(Number(pm.collectionVariables.get("flujoCamiones")) + 1); });`)) }),
  req('10.19b Tras el recálculo: el ítem manual, las marcas y la edición siguen igual', 'GET', `${U.log}/eventos/{{flujoEventoId}}/compras?size=100`, {
    desc: '10B3: el recálculo por SERVICIO_ADICIONAL_AGREGADO (10.18 → 10.19) no borra ítems manuales ni marcas de "conseguido".',
    test: L(st(200), pagina(), `const m = r.contenido.find(function (x) { return x.id === pm.collectionVariables.get("flujoManualId"); }); const h = r.contenido.find(function (x) { return x.id === pm.collectionVariables.get("flujoHieloId"); });`,
      `pm.test("El ítem manual sigue, conseguido y con su nota", function () { pm.expect(m.nombreItem).to.eql("Recuerdos para invitados"); pm.expect(m.origen).to.eql("MANUAL"); pm.expect(m.comprado).to.eql(true); pm.expect(m.nota).to.eql("Bolsas de tela con dulces"); });`,
      `pm.test("El calculado editado sigue editado y conseguido", function () { pm.expect(h.nombreItem).to.eql("Hielo en cubos"); pm.expect(h.comprado).to.eql(true); });`) }),
  req('10.19c Quitar el ítem manual', 'DELETE', `${U.log}/eventos/{{flujoEventoId}}/compras/{{flujoManualId}}`, { test: L(st(204)) }),
  req('10.19d [404] Quitarlo otra vez', 'DELETE', `${U.log}/eventos/{{flujoEventoId}}/compras/{{flujoManualId}}`, { test: e404('No existe el ítem de compra') }),
  req('10.20 Anexo del contrato con el adicional (PDF)', 'GET', `${U.con}/eventos/{{flujoEventoId}}/adicionales/anexo-pdf`, { pre: espera(),
    test: reintentar('return pm.response.code === 200;', pdf()) }),
  req('10.21 Asignar el proveedor propio (Decoración)', 'POST', `${U.prov}/proveedores/eventos`, {
    body: '{\n  "eventoId": "{{flujoEventoId}}",\n  "proveedorId": "{{proveedorPruebaId}}",\n  "servicioAsignado": "Decoración del salón",\n  "costoAcordado": 900000,\n  "estado": "CONFIRMADO"\n}',
    test: L(st(201), location(), js(), `pm.test("CONFIRMADO", function () { pm.expect(r.estado).to.eql("CONFIRMADO"); });`, setC('flujoAsignacionConfirmadaId', 'r.id')) }),
  req('10.22 [409] El mismo proveedor para el mismo servicio', 'POST', `${U.prov}/proveedores/eventos`, {
    body: '{\n  "eventoId": "{{flujoEventoId}}",\n  "proveedorId": "{{proveedorPruebaId}}",\n  "servicioAsignado": "Decoración del salón",\n  "costoAcordado": 900000\n}',
    test: L(e409('ya está asignado a este evento para el servicio «Decoración del salón»'), `pm.test("El mensaje nombra al proveedor", function () { pm.expect(r.message).to.include("«" + pm.collectionVariables.get("proveedorPruebaNombre") + "»"); });`) }),
  req('10.23 El mismo proveedor para otro servicio (201)', 'POST', `${U.prov}/proveedores/eventos`, {
    body: '{\n  "eventoId": "{{flujoEventoId}}",\n  "proveedorId": "{{proveedorPruebaId}}",\n  "servicioAsignado": "Centros de mesa",\n  "costoAcordado": 300000\n}',
    test: L(st(201), js(), `pm.test("PENDIENTE por defecto", function () { pm.expect(r.estado).to.eql("PENDIENTE"); });`, setC('flujoAsignacionPendienteId', 'r.id')) }),
  req('10.24 [409] Borrar un proveedor con asignaciones', 'DELETE', `${U.prov}/proveedores/{{proveedorPruebaId}}`, { test: e409('tiene asignaciones a eventos') }),
  req('10.25 Asignaciones del evento', 'GET', `${U.prov}/proveedores/eventos/{{flujoEventoId}}`, {
    test: L(st(200), pagina(), `pm.test("Dos asignaciones", function () { pm.expect(r.totalElementos).to.eql(2); });`) }),
  // ---- Fase 11B: confirmar una asignación PENDIENTE (endpoint agregado a mano tras la Fase 10)
  req('10.25b Confirmar la asignación pendiente (Centros de mesa)', 'PATCH', `${U.prov}/proveedores/eventos/asignaciones/{{flujoAsignacionPendienteId}}/estado`, {
    body: '{\n  "estado": "CONFIRMADO"\n}',
    test: L(st(200), js(), `pm.test("La misma asignación, ahora CONFIRMADO", function () { pm.expect(r.id).to.eql(pm.collectionVariables.get("flujoAsignacionPendienteId")); pm.expect(r.estado).to.eql("CONFIRMADO"); pm.expect(r.servicioAsignado).to.eql("Centros de mesa"); });`) }),
  req('10.25c [409] Confirmarla otra vez', 'PATCH', `${U.prov}/proveedores/eventos/asignaciones/{{flujoAsignacionPendienteId}}/estado`, {
    body: '{\n  "estado": "CONFIRMADO"\n}', test: e409('Solo se puede confirmar una asignación pendiente; esta ya está confirmada.') }),
  req('10.25d [409] Pedir otro estado que no es CONFIRMADO', 'PATCH', `${U.prov}/proveedores/eventos/asignaciones/{{flujoAsignacionConfirmadaId}}/estado`, {
    body: '{\n  "estado": "CANCELADO"\n}', test: e409('se pidió dejarla cancelada') }),
  req('10.25e [404] Asignación inexistente', 'PATCH', `${U.prov}/proveedores/eventos/asignaciones/${NX}/estado`, {
    body: '{\n  "estado": "CONFIRMADO"\n}', test: e404('No existe una asignación con id') }),
  req('10.25f [400] Sin estado', 'PATCH', `${U.prov}/proveedores/eventos/asignaciones/{{flujoAsignacionPendienteId}}/estado`, {
    body: '{}', test: e400('estado') }),
  req('10.26 Lista de compras del evento', 'GET', `${U.log}/eventos/{{flujoEventoId}}/compras?size=5`, {
    test: L(st(200), pagina(5), `pm.test("Hay ítems sin comprar", function () { pm.expect(r.contenido.length).to.be.above(0); });`, setC('flujoItemId', 'r.contenido[0].id')) }),
  req('10.27 Marcar un ítem como comprado', 'PUT', `${U.log}/eventos/{{flujoEventoId}}/compras?size=20`, {
    body: '[\n  { "id": "{{flujoItemId}}", "comprado": true }\n]',
    test: L(st(200), pagina(), `pm.test("El ítem quedó comprado", function () { const i = r.contenido.find(function (x) { return x.id === pm.collectionVariables.get("flujoItemId"); }); pm.expect(i.comprado).to.eql(true); });`) }),
  req('10.28 [404] Marcar un ítem que no es del evento', 'PUT', `${U.log}/eventos/{{flujoEventoId}}/compras`, {
    body: `[\n  { "id": "${NX}", "comprado": true }\n]`, test: e404('No existe el ítem de compra') }),
  req('10.29 [400] Lista vacía', 'PUT', `${U.log}/eventos/{{flujoEventoId}}/compras`, { body: '[]', test: e400('solicitudes') }),
  req('10.30 Notificaciones del evento (2 abonos + confirmación)', 'GET', `${U.not}/notificaciones?eventoId={{flujoEventoId}}`, { pre: espera(),
    test: reintentar('return pm.response.code === 200 && pm.response.json().totalElementos >= 3;',
      L(st(200), pagina(), `pm.test("3 notificaciones GENERADA", function () { pm.expect(r.totalElementos).to.eql(3); r.contenido.forEach(function (n) { pm.expect(n.estado).to.eql("GENERADA"); }); });`)) }),
  req('10.31 Auditoría del evento (2 pagos, confirmación y adicional)', 'GET', `${U.aud}/auditoria?eventoId={{flujoEventoId}}`, { pre: espera(),
    test: reintentar('return pm.response.code === 200 && pm.response.json().totalElementos >= 4;',
      L(st(200), pagina(), `pm.test("4 mensajes de los 3 tópicos", function () { pm.expect(r.totalElementos).to.eql(4); const tipos = r.contenido.map(function (a) { return a.tipo; }); ["EVENTO_CONFIRMADO", "PAGO_REGISTRADO", "SERVICIO_ADICIONAL_AGREGADO"].forEach(function (t) { pm.expect(tipos).to.include(t); }); });`)) }),
]);

// ------------------------------------------------------------------ 11 Reto semana 8
const reto = carpeta('11 Reto semana 8 (Finanzas)', 'Reto del equipo: un caso de validación (moneda distinta → 400 en el campo moneda) y un error de negocio (monto mayor que el saldo → 409 con el saldo en el mensaje), documentados en openapi/finanzas.yaml (ejemplos retoMonedaDistinta y retoMontoMayorQueElSaldo) y verificados por FinanzasApiIntegracionTest.Pagos (monedaDistintaALaDeLaCotizacion400EnElCampo y montoMayorQueElSaldo409). Se ejecuta sobre el evento del flujo.', [
  req('11.01 Saldo pendiente antes del reto', 'GET', `${U.fin}/eventos/{{flujoEventoId}}/balance`, {
    test: L(st(200), js(), `pm.test("Hay saldo pendiente", function () { pm.expect(r.saldoPendiente).to.be.above(0); });`, setC('retoSaldo', 'String(r.saldoPendiente)'), setC('retoAbonado', 'String(r.totalAbonado)'),
      setC('retoMontoMayor', 'String(Math.floor(r.saldoPendiente) + 1)')) }),
  req('11.02 [400 reto] Abono en una moneda distinta (USD)', 'POST', `${U.fin}/finanzas/pagos`, { body: pago(100000, 'TRANSFERENCIA').replace('"COP"', '"USD"'),
    test: L(e400('moneda'), `pm.test("Reto 400: errores[0] = {campo: moneda, mensaje: debe ser COP, la moneda de la cotización}", function () { pm.expect(r.errores[0].campo).to.eql("moneda"); pm.expect(r.errores[0].mensaje).to.eql("debe ser COP, la moneda de la cotización"); });`) }),
  req('11.03 [409 reto] Abono mayor que el saldo pendiente', 'POST', `${U.fin}/finanzas/pagos`, { body: pago('{{retoMontoMayor}}', 'TRANSFERENCIA'),
    test: L(e409('supera el saldo pendiente de'), `pm.test("Reto 409: el mensaje indica el monto y el saldo", function () { const saldo = Number(pm.collectionVariables.get("retoSaldo")).toFixed(2); pm.expect(r.message).to.eql("El monto " + pm.collectionVariables.get("retoMontoMayor") + " supera el saldo pendiente de " + saldo + " COP."); });`) }),
  req('11.04 Ningún abono se registró (saldo igual)', 'GET', `${U.fin}/eventos/{{flujoEventoId}}/balance`, {
    test: L(st(200), js(), `pm.test("Saldo y abonado sin cambios", function () { pm.expect(String(r.saldoPendiente)).to.eql(pm.collectionVariables.get("retoSaldo")); pm.expect(String(r.totalAbonado)).to.eql(pm.collectionVariables.get("retoAbonado")); });`) }),
]);

// ------------------------------------------------------------------ 12 GraphQL
const sinErrores = `pm.test("Sin errores GraphQL", function () { pm.expect(r.errors, JSON.stringify(r.errors)).to.be.undefined; });`;
const errGql = (c) => L(st(200), js(), `pm.test("Error GraphQL ${c}", function () { pm.expect(r.errors).to.be.an("array").that.is.not.empty; pm.expect(r.errors[0].extensions.classification).to.eql("${c}"); });`);
const FICHA = `query Ficha($id: ID!) {
  evento(id: $id) {
    id nombreEvento fecha tipoEvento numInvitados zona estado
    cliente { nombreCompleto telefono correo }
    cotizacion { valorBase totalSinAdicionales total depositoInicialRequerido }
    contrato { estado fechaFirma }
    pagos { totalElementos contenido { monto fecha metodoPago } }
    balance { totalCotizado montoAbonado saldoPendiente }
    lugar { nombre capacidad }
    proveedores { contenido { servicio categoria costoAcordado } }
    serviciosAdicionales { nombre cantidad subtotal }
    logistica { meseros personalCocina vehiculos compras(size: 5) { contenido { nombreItem comprado } } }
    notificaciones { contenido { tipo asunto mensaje estado } }
    auditoria { contenido { tipo topico servicioOrigen payload } }
  }
}`;
const graphql = carpeta('12 GraphQL (gateway)', 'Gateway en {{gatewayUrl}}: la ficha completa en UNA query (demostración y evento del flujo), una mutation, errores controlados (BAD_REQUEST, NOT_FOUND) y acceso sin token (UNAUTHORIZED). El token va en el header Authorization; los errores llegan con HTTP 200 en errors[]. Fase 10 (lo que usa la interfaz web): query clientes, mutation actualizarCompras sobre el evento propio del flujo y la descarga de los PDF por las rutas REST /documentos del gateway ({{gatewayBaseUrl}}). Fase 10B3: crear, editar y activar/desactivar lugares y proveedores (BAD_REQUEST por campo, CONFLICT del proveedor duplicado, NOT_FOUND) sobre entidades "Prueba Postman GraphQL …" que se borran en 13; agregar, editar y quitar ítems de la lista de compras del evento propio del flujo (CONFLICT por nombre repetido y por quitar un calculado).', [
  gql('12.01 Ficha completa del evento de demostración (una query)', FICHA, { id: '{{demoEventoReservadoId}}' },
    L(st(200), js(), sinErrores, `const e = r.data.evento;`,
      `pm.test("Ficha con datos de los 9 servicios", function () { pm.expect(e.estado).to.eql("RESERVADO"); pm.expect(e.cliente.nombreCompleto).to.eql("Lucía Fernández Ospina"); pm.expect(e.contrato.estado).to.eql("FIRMADO"); pm.expect(e.lugar.nombre).to.eql("Salón de Eventos Los Ocobos"); pm.expect(String(e.pagos.totalElementos)).to.eql(pm.collectionVariables.get("fotoPagos")); pm.expect(e.logistica.vehiculos).to.be.above(0); pm.expect(e.serviciosAdicionales.length).to.be.above(0); pm.expect(e.proveedores.contenido.length).to.be.above(0); pm.expect(e.notificaciones.contenido.length).to.be.above(0); pm.expect(e.auditoria.contenido.length).to.be.above(0); });`)),
  gql('12.02 Ficha completa del evento del flujo', FICHA, { id: '{{flujoEventoId}}' },
    L(st(200), js(), sinErrores, `pm.test("RESERVADO con 2 pagos y 2 proveedores", function () { const e = r.data.evento; pm.expect(e.estado).to.eql("RESERVADO"); pm.expect(e.pagos.totalElementos).to.eql(2); pm.expect(e.proveedores.contenido.length).to.eql(2); pm.expect(e.balance.saldoPendiente).to.be.above(0); });`)),
  gql('12.03 Mutation crearCliente', 'mutation($i: ClienteInput!) { crearCliente(input: $i) { id nombreCompleto correo } }',
    { i: { nombreCompleto: 'Prueba Postman Valeria Ñáñez GraphQL {{sufijo}}', telefono: '36{{sufijo}}', correo: 'graphql.{{sufijo}}@ejemplo.com', direccion: 'Cra. 5 # 10-20, Neiva' } },
    L(st(200), js(), sinErrores, `pm.test("Cliente creado por GraphQL (delegado a REST)", function () { pm.expect(r.data.crearCliente.nombreCompleto).to.include("Valeria Ñáñez GraphQL"); });`, setC('clienteGraphqlId', 'r.data.crearCliente.id'))),
  gql('12.04 [error] crearCliente con datos inválidos → BAD_REQUEST', 'mutation($i: ClienteInput!) { crearCliente(input: $i) { id } }',
    { i: { nombreCompleto: 'X', telefono: '12', correo: 'valeria@@ejemplo' } },
    L(errGql('BAD_REQUEST'), `pm.test("status 400 y errores[] por campo del servicio de clientes", function () { const x = r.errors[0].extensions; pm.expect(x.status).to.eql(400); pm.expect(x.servicio).to.eql("clientes"); pm.expect(x.errores.map(function (e) { return e.campo; })).to.include("correo"); });`)),
  gql('12.05 [error] Evento inexistente → NOT_FOUND', 'query($id: ID!) { evento(id: $id) { id nombreEvento } }', { id: NX },
    L(errGql('NOT_FOUND'), `pm.test("Mensaje del servicio de eventos", function () { pm.expect(r.errors[0].message).to.include("No existe un evento con id"); });`)),
  gql('12.06 [error reto] registrarPago con moneda distinta → BAD_REQUEST en moneda', 'mutation($i: PagoInput!) { registrarPago(input: $i) { id } }',
    { i: { eventoId: '{{flujoEventoId}}', clienteId: '{{clientePostmanId}}', monto: 100000, moneda: 'USD', metodoPago: 'TRANSFERENCIA' } },
    L(errGql('BAD_REQUEST'), `pm.test("errores[] en el campo moneda", function () { pm.expect(r.errors[0].extensions.errores[0].campo).to.eql("moneda"); });`)),
  gql('12.07 [401] Sin token → UNAUTHORIZED', 'query($id: ID!) { evento(id: $id) { id } }', { id: '{{demoEventoReservadoId}}' },
    L(errGql('UNAUTHORIZED'), `pm.test("Mensaje en español y sin datos", function () { pm.expect(r.errors[0].message).to.include("Falta el token"); pm.expect(r.data == null || r.data.evento == null).to.eql(true); });`), { sinToken: true }),
  // ---- Fase 10: lo que añadió el gateway para la interfaz web (solo sobre entidades propias de la colección)
  gql('12.08 [filtro] Query clientes por nombre (el cliente propio)', 'query($n: String) { clientes(nombre: $n, size: 10) { totalElementos contenido { id nombreCompleto } } }',
    { n: 'Prueba Postman Cliente Ñandú {{sufijo}}' },
    L(st(200), js(), sinErrores, `pm.test("Encuentra al cliente propio (delegado a GET /clientes)", function () { const p = r.data.clientes; pm.expect(p.totalElementos).to.eql(1); pm.expect(p.contenido[0].id).to.eql(pm.collectionVariables.get("clientePostmanId")); });`)),
  gql('12.09 [paginación] Query clientes con size 2', '{ clientes(page: 0, size: 2, sort: "nombreCompleto,asc") { pagina tamanoPagina totalElementos totalPaginas contenido { id } } }', {},
    L(st(200), js(), sinErrores, `pm.test("Página de 2 con el formato de siempre", function () { const p = r.data.clientes; pm.expect(p.tamanoPagina).to.eql(2); pm.expect(p.contenido.length).to.be.at.most(2); pm.expect(p.totalPaginas).to.eql(Math.ceil(p.totalElementos / 2)); });`)),
  gql('12.10 Compras del evento del flujo (para elegir un ítem pendiente)', 'query($id: ID!) { evento(id: $id) { logistica { compras(size: 100) { contenido { id nombreItem comprado } } } } }', { id: '{{flujoEventoId}}' },
    L(st(200), js(), sinErrores, `const pendiente = r.data.evento.logistica.compras.contenido.find(function (i) { return !i.comprado; });`,
      `pm.test("Hay un ítem pendiente", function () { pm.expect(pendiente, "un ítem con comprado = false").to.be.an("object"); });`, setC('flujoItemGqlId', 'pendiente.id'))),
  gql('12.11 Mutation actualizarCompras: marcar comprado', 'mutation($e: ID!, $i: [ItemCompraInput!]!) { actualizarCompras(eventoId: $e, items: $i) { totalElementos contenido { id comprado } } }',
    { e: '{{flujoEventoId}}', i: [{ id: '{{flujoItemGqlId}}', comprado: true }] },
    L(st(200), js(), sinErrores, `pm.test("Devuelve la lista actualizada con el ítem comprado", function () { const i = r.data.actualizarCompras.contenido.find(function (x) { return x.id === pm.collectionVariables.get("flujoItemGqlId"); }); pm.expect(i.comprado).to.eql(true); });`)),
  gql('12.12 Mutation actualizarCompras: volver a pendiente', 'mutation($e: ID!, $i: [ItemCompraInput!]!) { actualizarCompras(eventoId: $e, items: $i) { contenido { id comprado } } }',
    { e: '{{flujoEventoId}}', i: [{ id: '{{flujoItemGqlId}}', comprado: false }] },
    L(st(200), js(), sinErrores, `pm.test("El ítem vuelve a pendiente", function () { const i = r.data.actualizarCompras.contenido.find(function (x) { return x.id === pm.collectionVariables.get("flujoItemGqlId"); }); pm.expect(i.comprado).to.eql(false); });`)),
  gql('12.13 [error] actualizarCompras con un ítem de otro evento → NOT_FOUND', 'mutation($e: ID!, $i: [ItemCompraInput!]!) { actualizarCompras(eventoId: $e, items: $i) { totalElementos } }',
    { e: '{{flujoEventoId}}', i: [{ id: NX, comprado: true }] },
    L(errGql('NOT_FOUND'), `pm.test("Mensaje y servicio de Logística", function () { pm.expect(r.errors[0].message).to.include("No existe el ítem de compra"); pm.expect(r.errors[0].extensions.servicio).to.eql("logistica"); pm.expect(r.errors[0].extensions.status).to.eql(404); });`)),
  req('12.14 [happy] PDF del contrato por el gateway (/documentos)', 'GET', `${U.gw}/documentos/eventos/{{flujoEventoId}}/contrato.pdf`, {
    desc: 'Ruta REST de solo lectura del gateway que reenvía a contratos-service con el mismo token (GraphQL no sirve bien binarios).',
    headers: ACEPTA_PDF, test: pdfGateway('contrato') }),
  req('12.15 [happy] PDF del anexo por el gateway (/documentos)', 'GET', `${U.gw}/documentos/eventos/{{flujoEventoId}}/anexo.pdf`, {
    headers: ACEPTA_PDF, test: pdfGateway('anexo') }),
  req('12.16 [401] PDF del contrato sin token', 'GET', `${U.gw}/documentos/eventos/{{flujoEventoId}}/contrato.pdf`, {
    sinToken: true, headers: ACEPTA_PDF, test: errDoc(401, 'Debes iniciar sesión') }),
  req('12.17 [404] PDF del contrato de un evento sin contrato', 'GET', `${U.gw}/documentos/eventos/${NX}/contrato.pdf`, {
    headers: ACEPTA_PDF, test: errDoc(404, 'No existe un contrato para el evento') }),
  // ---- Fase 10B3: registro de lugares y proveedores desde la interfaz (entidades propias "Prueba Postman GraphQL …", se borran en 13)
  gql('12.18 Mutation crearLugar', `mutation($i: LugarInput!) { crearLugar(input: $i) { ${'id nombre tipo capacidad estado'} } }`,
    { i: { nombre: 'Prueba Postman GraphQL Salón {{sufijo}}', direccion: 'Calle 8 # 5-30, Neiva', tipo: 'SALON', capacidad: 120 } },
    L(st(200), js(), sinErrores, `pm.test("Lugar ACTIVO con los datos enviados (delegado a POST /lugares)", function () { const l = r.data.crearLugar; pm.expect(l.estado).to.eql("ACTIVO"); pm.expect(l.capacidad).to.eql(120); pm.expect(l.nombre).to.include("Prueba Postman GraphQL Salón"); });`,
      setC('lugarGqlId', 'r.data.crearLugar.id'))),
  gql('12.19 [error] crearLugar sin nombre ni capacidad → BAD_REQUEST por campo', 'mutation($i: LugarInput!) { crearLugar(input: $i) { id } }',
    { i: { nombre: '', direccion: 'Calle 8', tipo: 'SALON', capacidad: null } },
    L(errGql('BAD_REQUEST'), `pm.test("errores[] de lugares en nombre y capacidad", function () { const x = r.errors[0].extensions; pm.expect(x.status).to.eql(400); pm.expect(x.servicio).to.eql("lugares"); const c = x.errores.map(function (e) { return e.campo; }); pm.expect(c).to.include("nombre"); pm.expect(c).to.include("capacidad"); });`)),
  gql('12.20 Mutation actualizarLugar', 'mutation($id: ID!, $i: LugarInput!) { actualizarLugar(id: $id, input: $i) { id capacidad tipo estado } }',
    { id: '{{lugarGqlId}}', i: { nombre: 'Prueba Postman GraphQL Salón {{sufijo}}', direccion: 'Calle 8 # 5-30, Neiva', tipo: 'FINCA', capacidad: 150 } },
    L(st(200), js(), sinErrores, `pm.test("Capacidad y tipo nuevos; el estado no cambia", function () { const l = r.data.actualizarLugar; pm.expect(l.capacidad).to.eql(150); pm.expect(l.tipo).to.eql("FINCA"); pm.expect(l.estado).to.eql("ACTIVO"); });`)),
  gql('12.21 Mutation cambiarEstadoLugar → INACTIVO', 'mutation($id: ID!) { cambiarEstadoLugar(id: $id, estado: INACTIVO) { estado } }', { id: '{{lugarGqlId}}' },
    L(st(200), js(), sinErrores, `pm.test("INACTIVO", function () { pm.expect(r.data.cambiarEstadoLugar.estado).to.eql("INACTIVO"); });`)),
  gql('12.22 [error] actualizarLugar de un lugar inexistente → NOT_FOUND', 'mutation($id: ID!, $i: LugarInput!) { actualizarLugar(id: $id, input: $i) { id } }',
    { id: NX, i: { nombre: 'Prueba Postman X', direccion: 'Calle 1', tipo: 'SALON', capacidad: 10 } },
    L(errGql('NOT_FOUND'), `pm.test("Mensaje de Lugares", function () { pm.expect(r.errors[0].message).to.include("No existe un lugar con id"); pm.expect(r.errors[0].extensions.servicio).to.eql("lugares"); });`)),
  gql('12.23 Mutation crearProveedor', 'mutation($i: ProveedorInput!) { crearProveedor(input: $i) { id nombre categoria activo } }',
    { i: { nombre: 'Prueba Postman GraphQL Flores {{sufijo}}', categoria: 'FLORISTERIA', contacto: '315{{sufijo}}' } },
    L(st(200), js(), sinErrores, `pm.test("Proveedor activo (delegado a POST /proveedores)", function () { pm.expect(r.data.crearProveedor.activo).to.eql(true); pm.expect(r.data.crearProveedor.categoria).to.eql("FLORISTERIA"); });`,
      setC('proveedorGqlId', 'r.data.crearProveedor.id'))),
  gql('12.24 [error] crearProveedor con el mismo nombre en minúsculas → CONFLICT', 'mutation($i: ProveedorInput!) { crearProveedor(input: $i) { id } }',
    { i: { nombre: '  prueba postman graphql flores {{sufijo}} ', categoria: 'FLORISTERIA', contacto: '3150000000' } },
    L(errGql('CONFLICT'), `pm.test("409 de Proveedores con su mensaje", function () { const e = r.errors[0]; pm.expect(e.message).to.include("Ya existe un proveedor con el nombre"); pm.expect(e.extensions.status).to.eql(409); pm.expect(e.extensions.servicio).to.eql("proveedores"); });`)),
  gql('12.25 Mutation actualizarProveedor', 'mutation($id: ID!, $i: ProveedorInput!) { actualizarProveedor(id: $id, input: $i) { contacto activo } }',
    { id: '{{proveedorGqlId}}', i: { nombre: 'Prueba Postman GraphQL Flores {{sufijo}}', categoria: 'FLORISTERIA', contacto: 'flores.{{sufijo}}@ejemplo.com' } },
    L(st(200), js(), sinErrores, `pm.test("Contacto nuevo; sigue activo", function () { pm.expect(r.data.actualizarProveedor.contacto).to.include("@ejemplo.com"); pm.expect(r.data.actualizarProveedor.activo).to.eql(true); });`)),
  gql('12.26 Mutation cambiarEstadoProveedor → inactivo', 'mutation($id: ID!) { cambiarEstadoProveedor(id: $id, activo: false) { activo } }', { id: '{{proveedorGqlId}}' },
    L(st(200), js(), sinErrores, `pm.test("Inactivo", function () { pm.expect(r.data.cambiarEstadoProveedor.activo).to.eql(false); });`)),
  // ---- Fase 10B3c: ítems de la lista de compras por GraphQL (evento propio del flujo)
  gql('12.27 Mutation agregarItemCompra', 'mutation($e: ID!, $i: ItemCompraDatosInput!) { agregarItemCompra(eventoId: $e, input: $i) { id nombreItem origen comprado nota } }',
    { e: '{{flujoEventoId}}', i: { nombreItem: 'Velas para las mesas', cantidadEstimada: 'una por mesa', nota: null } },
    L(st(200), js(), sinErrores, `pm.test("MANUAL y sin marcar (delegado a POST /compras)", function () { const i = r.data.agregarItemCompra; pm.expect(i.origen).to.eql("MANUAL"); pm.expect(i.comprado).to.eql(false); });`,
      setC('itemGqlId', 'r.data.agregarItemCompra.id'))),
  gql('12.28 Mutation editarItemCompra', 'mutation($e: ID!, $id: ID!, $i: ItemCompraDatosInput!) { editarItemCompra(eventoId: $e, itemId: $id, input: $i) { nombreItem cantidadEstimada origen } }',
    { e: '{{flujoEventoId}}', id: '{{itemGqlId}}', i: { nombreItem: 'Velas aromáticas', cantidadEstimada: '30 unidades', nota: 'Color blanco' } },
    L(st(200), js(), sinErrores, `pm.test("Editado", function () { pm.expect(r.data.editarItemCompra.nombreItem).to.eql("Velas aromáticas"); pm.expect(r.data.editarItemCompra.origen).to.eql("MANUAL"); });`)),
  gql('12.29 [error] agregarItemCompra con un nombre repetido → CONFLICT', 'mutation($e: ID!, $i: ItemCompraDatosInput!) { agregarItemCompra(eventoId: $e, input: $i) { id } }',
    { e: '{{flujoEventoId}}', i: { nombreItem: 'VELAS AROMÁTICAS', cantidadEstimada: '1' } },
    L(errGql('CONFLICT'), `pm.test("409 de Logística", function () { pm.expect(r.errors[0].message).to.include("Ya hay un ítem"); pm.expect(r.errors[0].extensions.servicio).to.eql("logistica"); });`)),
  gql('12.30 Mutation quitarItemCompra (devuelve el id)', 'mutation($e: ID!, $id: ID!) { quitarItemCompra(eventoId: $e, itemId: $id) }',
    { e: '{{flujoEventoId}}', id: '{{itemGqlId}}' },
    L(st(200), js(), sinErrores, `pm.test("Devuelve el id quitado", function () { pm.expect(r.data.quitarItemCompra).to.eql(pm.collectionVariables.get("itemGqlId")); });`)),
  gql('12.31 [error] quitarItemCompra de un ítem calculado → CONFLICT', 'mutation($e: ID!, $id: ID!) { quitarItemCompra(eventoId: $e, itemId: $id) }',
    { e: '{{flujoEventoId}}', id: '{{flujoVasosId}}' },
    L(errGql('CONFLICT'), `pm.test("Mensaje de Logística", function () { pm.expect(r.errors[0].message).to.include("no se pueden quitar"); pm.expect(r.errors[0].extensions.status).to.eql(409); });`)),
  // ---- Fase 11B: cambiarEstadoAsignacion (las dos asignaciones del flujo ya están CONFIRMADO tras 10.21 y 10.25b)
  gql('12.32 [error] cambiarEstadoAsignacion de una asignación ya confirmada → CONFLICT', 'mutation($id: ID!) { cambiarEstadoAsignacion(id: $id, estado: CONFIRMADO) { id estado } }',
    { id: '{{flujoAsignacionConfirmadaId}}' },
    L(errGql('CONFLICT'), `pm.test("409 de Proveedores con su mensaje", function () { const e = r.errors[0]; pm.expect(e.message).to.include("esta ya está confirmada"); pm.expect(e.extensions.status).to.eql(409); pm.expect(e.extensions.servicio).to.eql("proveedores"); });`)),
  gql('12.33 [error] cambiarEstadoAsignacion de una asignación inexistente → NOT_FOUND', 'mutation($id: ID!) { cambiarEstadoAsignacion(id: $id, estado: CONFIRMADO) { id } }',
    { id: NX },
    L(errGql('NOT_FOUND'), `pm.test("Mensaje de Proveedores", function () { pm.expect(r.errors[0].message).to.include("No existe una asignación con id"); pm.expect(r.errors[0].extensions.servicio).to.eql("proveedores"); });`)),
]);

// ------------------------------------------------------------------ 12b Roles (Fase 11D)
// El ASISTENTE no administra lugares, proveedores, el bloqueo de fechas, el borrado de clientes ni ve la auditoría. Se
// comprueba en los DOS niveles: directo contra cada servicio (403 con ErrorResponse) y por el gateway (FORBIDDEN). Nada de
// esto cambia datos: son 403 y lecturas. (El caso "token sin rol" necesita firmar un token: lo cubren las pruebas automáticas
// y la verificación de la 11D.)
const SIN_PERMISO = 'No tienes permiso para realizar esta operación.';
const COMO_ASISTENTE = { sinToken: true, headers: [{ key: 'Authorization', value: 'Bearer {{tokenAsistente}}' }] };
const e403 = () => L(err(403), msg(SIN_PERMISO), `pm.test("Error Forbidden", function () { pm.expect(r.error).to.eql("Forbidden"); });`);
const asis = (nombre, metodo, url, opc = {}) => req(nombre, metodo, url, { ...COMO_ASISTENTE, ...opc });
const gqlAsis = (nombre, query, variables, test) => {
  const item = gql(nombre, query, variables, test, { sinToken: true });
  item.request.header.push({ key: 'Authorization', value: 'Bearer {{tokenAsistente}}' });
  return item;
};
const prohibidoGql = (campo) => L(errGql('FORBIDDEN'), `pm.test("FORBIDDEN del gateway en ${campo} (sin llamar al servicio)", function () { const e = r.errors[0]; pm.expect(e.message).to.eql(${JSON.stringify(SIN_PERMISO)}); pm.expect(e.extensions.status).to.eql(403); pm.expect(e.extensions.servicio).to.eql("gateway"); pm.expect(e.path[e.path.length - 1]).to.eql("${campo}"); });`);
const roles = carpeta('12b Roles: el asistente y los 403 (Fase 11D; recordatorios 11E)', 'Login del ASISTENTE (correoAsistente / passwordAsistente del environment = AUTH_ASISTENTE_EMAIL / VERIFICACION_ASISTENTE_PASSWORD del .env) y lo que su rol NO puede hacer: 403 directo en cada servicio y FORBIDDEN en el gateway. También lo que SÍ puede (consultar); lo del asistente no cambia datos. Al final (Fase 11E) el asistente no puede ejecutar los recordatorios y el administrador sí, dos veces: la primera envía los EVENTO_PROXIMO que falten (eventos RESERVADOS de los próximos 7 días) y la segunda no repite ninguno.', [
  req('12b.01 Login del asistente [happy] guarda su token', 'POST', `${U.auth}/auth/login`, { sinToken: true, body: '{}',
    pre: [`pm.request.body.update(JSON.stringify({ correo: pm.environment.get("correoAsistente"), password: pm.environment.get("passwordAsistente") || "" }));`],
    test: L(`pm.test("La variable passwordAsistente está definida (VERIFICACION_ASISTENTE_PASSWORD del .env)", function () { pm.expect(pm.environment.get("passwordAsistente"), "Define passwordAsistente: rotar-password.ps1 -Usuario asistente").to.be.a("string").and.not.empty; });`,
      st(200), js(),
      `pm.test("Rol ASISTENTE en la respuesta y en el claim del token", function () { pm.expect(r.rol).to.eql("ASISTENTE"); const c = JSON.parse(atob(r.token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))); pm.expect(c.rol).to.eql("ASISTENTE"); pm.expect(c.sub).to.not.eql(""); });`,
      setC('tokenAsistente', 'r.token')) }),
  // ---- lo que SÍ puede: consultar
  asis('12b.02 [happy] El asistente consulta los lugares', 'GET', `${U.lug}/lugares?size=5`, { test: L(st(200), pagina()) }),
  asis('12b.03 [happy] El asistente consulta las asignaciones de proveedores de un evento', 'GET', `${U.prov}/proveedores/eventos/{{demoEventoReservadoId}}`, { test: L(st(200), pagina()) }),
  asis('12b.04 [happy] El asistente consulta las fechas disponibles', 'GET', `${U.ev}/fechas/disponibles`, { test: L(st(200)) }),
  // ---- 403 directo en cada servicio (segundo nivel de la defensa en profundidad)
  asis('12b.05 [403] Lugares: crear un lugar', 'POST', `${U.lug}/lugares`, {
    body: '{\n  "nombre": "Prueba Postman Asistente",\n  "direccion": "Calle 1",\n  "tipo": "SALON",\n  "capacidad": 50\n}', test: e403() }),
  asis('12b.06 [403] Lugares: desactivar el lugar de demostración', 'PATCH', `${U.lug}/lugares/{{demoLugarId}}/estado`, {
    body: '{\n  "estado": "INACTIVO"\n}', test: e403() }),
  asis('12b.07 [403] Proveedores: crear un proveedor', 'POST', `${U.prov}/proveedores`, {
    body: '{\n  "nombre": "Prueba Postman Asistente",\n  "categoria": "SONIDO",\n  "contacto": "3100000000"\n}', test: e403() }),
  asis('12b.08 [403] Proveedores: desactivar el proveedor propio', 'PATCH', `${U.prov}/proveedores/{{proveedorPruebaId}}/estado`, {
    body: '{\n  "activo": false\n}', test: e403() }),
  asis('12b.09 [403] Proveedores: borrar el proveedor propio', 'DELETE', `${U.prov}/proveedores/{{proveedorPruebaId}}`, { test: e403() }),
  asis('12b.10 [403] Eventos: bloquear una fecha a mano', 'PUT', `${U.ev}/fechas/{{fechaSabado3}}/estado`, {
    body: '{\n  "estado": "OCUPADA"\n}', test: e403() }),
  asis('12b.11 [403] Auditoría: consultar la de un evento', 'GET', `${U.aud}/auditoria?eventoId={{demoEventoReservadoId}}`, { test: e403() }),
  asis('12b.12 [403] Clientes: borrar el cliente propio', 'DELETE', `${U.cli}/clientes/{{clientePruebaId}}`, { test: e403() }),
  // ---- FORBIDDEN en el gateway (primer nivel)
  gqlAsis('12b.13 [error] crearLugar como asistente → FORBIDDEN', 'mutation($i: LugarInput!) { crearLugar(input: $i) { id } }',
    { i: { nombre: 'Prueba Postman Asistente', direccion: 'Calle 1', tipo: 'SALON', capacidad: 50 } }, prohibidoGql('crearLugar')),
  gqlAsis('12b.14 [error] cambiarEstadoProveedor como asistente → FORBIDDEN', 'mutation($id: ID!) { cambiarEstadoProveedor(id: $id, activo: false) { id } }',
    { id: '{{proveedorPruebaId}}' }, prohibidoGql('cambiarEstadoProveedor')),
  gqlAsis('12b.15 La ficha con auditoría como asistente: datos sí, auditoría FORBIDDEN', 'query($id: ID!) { evento(id: $id) { nombreEvento notificaciones { totalElementos } auditoria { totalElementos } } }',
    { id: '{{demoEventoReservadoId}}' },
    L(prohibidoGql('auditoria'), `pm.test("El resto de la ficha llega", function () { pm.expect(r.data.evento.nombreEvento).to.eql("Quince años de Lucía"); pm.expect(r.data.evento.notificaciones.totalElementos).to.be.above(0); pm.expect(r.data.evento.auditoria).to.eql(null); pm.expect(r.errors).to.have.length(1); });`)),
  gqlAsis('12b.16 [happy] La ficha sin auditoría como asistente (como la pide la interfaz)', 'query($id: ID!, $v: Boolean!) { evento(id: $id) { nombreEvento auditoria @include(if: $v) { totalElementos } } }',
    { id: '{{demoEventoReservadoId}}', v: false },
    L(st(200), js(), sinErrores, `pm.test("Sin el campo auditoria y sin errores", function () { pm.expect(r.data.evento.nombreEvento).to.eql("Quince años de Lucía"); pm.expect(r.data.evento).to.not.have.property("auditoria"); });`)),
  // ---- el administrador sigue pudiendo
  req('12b.17 [happy] El administrador sí consulta la auditoría', 'GET', `${U.aud}/auditoria?eventoId={{demoEventoReservadoId}}`, { test: L(st(200), pagina()) }),
  // ---- Fase 11E: ejecución manual de los recordatorios EVENTO_PROXIMO (solo ADMINISTRADOR)
  asis('12b.18 [403] Eventos: ejecutar los recordatorios como asistente', 'POST', `${U.ev}/recordatorios/ejecutar`, { test: e403() }),
  gqlAsis('12b.19 [error] ejecutarRecordatorios como asistente → FORBIDDEN', 'mutation { ejecutarRecordatorios { enviados } }', {},
    L(prohibidoGql('ejecutarRecordatorios'), `pm.test("Sin datos", function () { pm.expect(r.data).to.eql(null); });`)),
  gql('12b.20 [happy] El administrador ejecuta los recordatorios', 'mutation { ejecutarRecordatorios { hoy umbralesDias revisados enviados yaEnviados recordatorios { eventoId nombreEvento fecha diasRestantes umbralDias resultado } } }', {},
    L(st(200), js(), sinErrores, `pm.test("Resultado coherente: cada RESERVADO próximo con el umbral más pequeño que aplica", function () { const x = r.data.ejecutarRecordatorios; pm.expect(x.umbralesDias).to.be.an("array").that.is.not.empty; const max = Math.max.apply(null, x.umbralesDias); pm.expect(x.enviados + x.yaEnviados).to.eql(x.revisados); pm.expect(x.recordatorios).to.have.length(x.revisados); x.recordatorios.forEach(function (e) { pm.expect(["ENVIADO", "YA_ENVIADO"]).to.include(e.resultado); pm.expect(e.diasRestantes).to.be.within(1, max); const menor = x.umbralesDias.filter(function (u) { return u >= e.diasRestantes; }).sort(function (a, b) { return a - b; })[0]; pm.expect(e.umbralDias).to.eql(menor); }); });`)),
  req('12b.21 [happy] Otra vez por REST: no repite ningún recordatorio', 'POST', `${U.ev}/recordatorios/ejecutar`, {
    test: L(st(200), js(), `pm.test("0 enviados: todos YA_ENVIADO (tabla eventos_recordatorios_enviados)", function () { pm.expect(r.enviados).to.eql(0); pm.expect(r.yaEnviados).to.eql(r.revisados); r.recordatorios.forEach(function (e) { pm.expect(e.resultado).to.eql("YA_ENVIADO"); }); });`) }),
]);

// ------------------------------------------------------------------ 12c Campanita (Fase 11H)
// "Leída" es POR USUARIO (sub del JWT): lo que marca la administradora no cambia para la asistente. Usa {{tokenAsistente}}
// (12b.01). Solo cambia lecturas (tabla notificaciones_lecturas), no las notificaciones ni la demostración.
const sinLeerTest = (k) => L(st(200), js(), `pm.test("Formato {sinLeer} (entero ≥ 0)", function () { pm.expect(r.sinLeer).to.be.a("number").and.at.least(0); });`, setC(k, 'String(r.sinLeer)'));
const campana = carpeta('12c Campanita: lecturas por usuario (Fase 11H)', 'Listado de las notificaciones recientes con la lectura del usuario, conteo de no leídas, marcar una (idempotente) y todas, 404 / 400 / 401, y que lo que marca la administradora no cambia para la asistente. También por GraphQL (misNotificaciones con el evento, notificacionesSinLeer y las mutations).', [
  req('12c.01 [happy] Administradora: sin leer', 'GET', `${U.not}/notificaciones/sin-leer`, { test: sinLeerTest('campanaAdmin') }),
  asis('12c.02 [happy] Asistente: sin leer', 'GET', `${U.not}/notificaciones/sin-leer`, { test: sinLeerTest('campanaAsistente') }),
  req('12c.03 [happy] Administradora: recientes (más nuevas primero, con su lectura)', 'GET', `${U.not}/notificaciones/recientes?size=5`, {
    test: L(st(200), pagina(), `pm.test("Más recientes primero, cada una con leida", function () { pm.expect(r.contenido).to.not.be.empty; for (let i = 1; i < r.contenido.length; i++) { pm.expect(new Date(r.contenido[i - 1].fechaCreacion).getTime() >= new Date(r.contenido[i].fechaCreacion).getTime()).to.eql(true); } r.contenido.forEach(function (n) { pm.expect(n.leida).to.be.a("boolean"); }); });`,
      setC('campanaId', 'r.contenido[0].id'), setC('campanaEraLeida', 'String(r.contenido[0].leida)')) }),
  req('12c.04 [happy] Administradora: marcar la primera como leída', 'PATCH', `${U.not}/notificaciones/{{campanaId}}/leida`, {
    test: L(st(200), js(), `pm.test("Leída por ella, con fecha", function () { pm.expect(r.id).to.eql(pm.collectionVariables.get("campanaId")); pm.expect(r.leida).to.eql(true); pm.expect(r.leidaEn).to.be.a("string"); });`, setC('campanaLeidaEn', 'r.leidaEn')) }),
  req('12c.05 [idempotente] Marcarla otra vez no cambia la primera lectura', 'PATCH', `${U.not}/notificaciones/{{campanaId}}/leida`, {
    test: L(st(200), js(), `pm.test("Misma leidaEn", function () { pm.expect(r.leidaEn).to.eql(pm.collectionVariables.get("campanaLeidaEn")); });`) }),
  req('12c.06 Administradora: el conteo bajó en 1 (si no la había leído antes)', 'GET', `${U.not}/notificaciones/sin-leer`, {
    test: L(st(200), js(), `pm.test("sin leer = antes − (1 si era nueva)", function () { const antes = Number(pm.collectionVariables.get("campanaAdmin")); const era = pm.collectionVariables.get("campanaEraLeida") === "true"; pm.expect(r.sinLeer).to.eql(era ? antes : antes - 1); });`) }),
  asis('12c.07 [por usuario] La asistente no cambió', 'GET', `${U.not}/notificaciones/sin-leer`, {
    test: L(st(200), js(), `pm.test("Igual que antes: lo que lee la administradora no cambia para la asistente", function () { pm.expect(String(r.sinLeer)).to.eql(pm.collectionVariables.get("campanaAsistente")); });`) }),
  req('12c.08 [404] Marcar una notificación que no existe', 'PATCH', `${U.not}/notificaciones/${NX}/leida`, { test: e404('No existe una notificación con id') }),
  req('12c.09 [400] Marcar con un id que no es UUID', 'PATCH', `${U.not}/notificaciones/abc/leida`, { test: e400('id') }),
  req('12c.10 [401] Sin token', 'GET', `${U.not}/notificaciones/sin-leer`, { sinToken: true, test: e401() }),
  gql('12c.11 Query notificacionesSinLeer (administradora, igual que REST)', '{ notificacionesSinLeer }', {},
    L(st(200), js(), sinErrores, `pm.test("Un entero ≥ 0", function () { pm.expect(r.data.notificacionesSinLeer).to.be.a("number").and.at.least(0); });`)),
  gql('12c.12 Query misNotificaciones con el nombre del evento (@BatchMapping)', '{ misNotificaciones(page: 0, size: 5) { totalElementos contenido { id asunto fechaCreacion leida evento { id nombreEvento } } } }', {},
    L(st(200), js(), sinErrores, `pm.test("La primera es la marcada en 12c.04, leída, con su evento", function () { const c = r.data.misNotificaciones.contenido; pm.expect(c[0].id).to.eql(pm.collectionVariables.get("campanaId")); pm.expect(c[0].leida).to.eql(true); pm.expect(c[0].evento.nombreEvento).to.be.a("string").and.not.empty; });`)),
  gql('12c.13 [error] marcarNotificacionLeida de una inexistente → NOT_FOUND', 'mutation($id: ID!) { marcarNotificacionLeida(id: $id) { id } }', { id: NX },
    L(errGql('NOT_FOUND'), `pm.test("Mensaje de Notificaciones", function () { pm.expect(r.errors[0].message).to.include("No existe una notificación con id"); pm.expect(r.errors[0].extensions.servicio).to.eql("notificaciones"); });`)),
  gqlAsis('12c.14 [happy] La asistente usa la campanita por GraphQL (sin FORBIDDEN)', '{ notificacionesSinLeer }', {},
    L(st(200), js(), sinErrores, `pm.test("Su propio conteo", function () { pm.expect(String(r.data.notificacionesSinLeer)).to.eql(pm.collectionVariables.get("campanaAsistente")); });`)),
  gql('12c.15 [happy] marcarTodasNotificacionesLeidas (administradora)', 'mutation { marcarTodasNotificacionesLeidas }', {},
    L(st(200), js(), sinErrores, `pm.test("Un entero ≥ 0", function () { pm.expect(r.data.marcarTodasNotificacionesLeidas).to.be.a("number").and.at.least(0); });`)),
  req('12c.16 Administradora: 0 sin leer', 'GET', `${U.not}/notificaciones/sin-leer`, { test: L(st(200), js(), `pm.test("0", function () { pm.expect(r.sinLeer).to.eql(0); });`) }),
  asis('12c.17 [por usuario] La asistente sigue igual', 'GET', `${U.not}/notificaciones/sin-leer`, {
    test: L(st(200), js(), `pm.test("Igual que en 12c.02", function () { pm.expect(String(r.sinLeer)).to.eql(pm.collectionVariables.get("campanaAsistente")); });`) }),
]);

// ------------------------------------------------------------------ 13 Limpieza y 99 verificación
const limpieza = carpeta('13 Limpieza', 'Cancela el evento del flujo (libera su fecha), inactiva el proveedor propio y borra el cliente, el lugar y el proveedor creados por GraphQL. E1 y E2 ya se cancelaron en sus carpetas.', [
  req('13.01 Cancelar el evento del flujo', 'PATCH', `${U.ev}/eventos/{{flujoEventoId}}/cancelar`, {
    test: L(st(200), js(), `pm.test("CANCELADO", function () { pm.expect(r.estado).to.eql("CANCELADO"); });`) }),
  req('13.02 Su fecha vuelve a estar disponible', 'GET', `${U.ev}/fechas/disponibles?desde={{fechaSabado2}}&hasta={{fechaSabado2}}`, {
    test: L(st(200), js(), `pm.test("Disponible", function () { pm.expect(r.map(function (f) { return f.fecha; })).to.include(pm.collectionVariables.get("fechaSabado2")); });`) }),
  req('13.03 Inactivar el proveedor propio', 'PATCH', `${U.prov}/proveedores/{{proveedorPruebaId}}/estado`, { body: { activo: false }, test: L(st(200)) }),
  req('13.04 Borrar el cliente creado por GraphQL', 'DELETE', `${U.cli}/clientes/{{clienteGraphqlId}}`, { test: L(st(204)) }),
  req('13.05 Borrar el lugar creado por GraphQL (10B3)', 'DELETE', `${U.lug}/lugares/{{lugarGqlId}}`, { test: L(st(204)) }),
  req('13.06 Borrar el proveedor creado por GraphQL (10B3, sin asignaciones)', 'DELETE', `${U.prov}/proveedores/{{proveedorGqlId}}`, { test: L(st(204)) }),
]);
const igualFoto = (k, campos) => `pm.test("Igual a la foto inicial (${campos.join(', ')})", function () { const f = JSON.parse(pm.collectionVariables.get("${k}")); ${campos.map(c => `pm.expect(r.${c}).to.eql(f.${c});`).join(' ')} });`;
const verif = carpeta('99 Datos de demostración intactos', 'Compara el estado final de la demostración con la foto tomada en 00 Preparación: la colección no modificó nada de la 9A.', [
  req('99.01 Evento RESERVADO intacto', 'GET', `${U.ev}/eventos/{{demoEventoReservadoId}}`, { test: L(st(200), js(), igualFoto('fotoReservado', ['estado', 'fecha', 'lugarId', 'numInvitados']), `pm.test("RESERVADO", function () { pm.expect(r.estado).to.eql("RESERVADO"); });`) }),
  req('99.02 Evento PENDIENTE intacto', 'GET', `${U.ev}/eventos/{{demoEventoPendienteId}}`, { test: L(st(200), js(), igualFoto('fotoPendiente', ['estado', 'fecha', 'lugarId']), `pm.test("PENDIENTE", function () { pm.expect(r.estado).to.eql("PENDIENTE"); });`) }),
  req('99.03 Evento CANCELADO intacto', 'GET', `${U.ev}/eventos/{{demoEventoCanceladoId}}`, { test: L(st(200), js(), igualFoto('fotoCancelado', ['estado', 'fecha']), `pm.test("CANCELADO", function () { pm.expect(r.estado).to.eql("CANCELADO"); });`) }),
  req('99.04 Balance intacto', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/balance`, { test: L(st(200), js(), `pm.test("Igual a la foto inicial", function () { pm.expect(r).to.eql(JSON.parse(pm.collectionVariables.get("fotoBalance"))); });`) }),
  req('99.05 Contrato intacto', 'GET', `${U.con}/eventos/{{demoEventoReservadoId}}/contrato`, { test: L(st(200), js(), igualFoto('fotoContrato', ['estado', 'firmadoEn', 'valorTotal'])) }),
  req('99.06 Pagos intactos', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/pagos?size=100`, { test: L(st(200), js(), `pm.test("Mismo número de pagos", function () { pm.expect(String(r.totalElementos)).to.eql(pm.collectionVariables.get("fotoPagos")); });`) }),
  req('99.07 Adicionales intactos', 'GET', `${U.fin}/eventos/{{demoEventoReservadoId}}/adicionales`, { test: L(st(200), js(), `pm.test("Mismos adicionales", function () { pm.expect(String(r.length)).to.eql(pm.collectionVariables.get("fotoAdicionales")); });`) }),
  req('99.08 Asignaciones intactas', 'GET', `${U.prov}/proveedores/eventos/{{demoEventoReservadoId}}?size=100`, { test: L(st(200), js(), `pm.test("Mismas asignaciones", function () { pm.expect(String(r.totalElementos)).to.eql(pm.collectionVariables.get("fotoAsignaciones")); });`) }),
  req('99.09 Notificaciones intactas', 'GET', `${U.not}/notificaciones?eventoId={{demoEventoReservadoId}}&size=100`, { test: L(st(200), js(), `pm.test("Mismas notificaciones (sin contar los recordatorios)", function () { pm.expect(${SIN_RECORDATORIOS}).to.eql(pm.collectionVariables.get("fotoNotificaciones")); });`) }),
  req('99.10 Auditoría intacta', 'GET', `${U.aud}/auditoria?eventoId={{demoEventoReservadoId}}&size=100`, { test: L(st(200), js(), `pm.test("Mismos mensajes (sin contar los recordatorios)", function () { pm.expect(${SIN_RECORDATORIOS}).to.eql(pm.collectionVariables.get("fotoAuditoria")); });`) }),
  req('99.11 Lugares y proveedor de la demostración intactos', 'GET', `${U.lug}/lugares/{{demoLugarInactivoId}}`, { test: L(st(200), js(), `pm.test("Hacienda El Pórtico sigue INACTIVO", function () { pm.expect(r.estado).to.eql("INACTIVO"); });`) }),
  req('99.12 Proveedor de la demostración intacto', 'GET', `${U.prov}/proveedores/{{demoProveedorId}}`, { test: L(st(200), js(), `pm.test("Sigue activo con su nombre", function () { pm.expect(r.activo).to.eql(true); pm.expect(r.nombre).to.eql("Sonido y Luces del Huila"); });`) }),
]);

// ------------------------------------------------------------------ colección y environment
const variablesColeccion = ['token', 'sufijo', 'fechaDesde', 'fechaHasta', 'fechaSabado1', 'fechaDomingo1', 'fechaSabado2', 'fechaSabado3', 'fechaMartes', 'demoFecha',
  'fotoReservado', 'fotoPendiente', 'fotoCancelado', 'fotoBalance', 'fotoContrato', 'fotoPagos', 'fotoAdicionales', 'fotoAsignaciones', 'fotoNotificaciones', 'fotoAuditoria',
  'clientePostmanId', 'clientePruebaId', 'lugarPruebaId', 'proveedorPruebaId', 'proveedorPruebaNombre', 'evento1Id', 'evento2Id', 'cotizacion2Id', 'pago2Id',
  'flujoEventoId', 'flujoReserva', 'flujoAbono1', 'flujoAbono2', 'flujoCamiones', 'flujoItemId', 'retoSaldo', 'retoAbonado', 'retoMontoMayor', 'clienteGraphqlId', 'lugarGqlId', 'proveedorGqlId', 'flujoManualId', 'flujoHieloId', 'flujoVasosId', 'itemGqlId',
  'flujoItemGqlId', 'flujoAsignacionConfirmadaId', 'flujoAsignacionPendienteId', 'tokenAsistente',
  'campanaAdmin', 'campanaAsistente', 'campanaId', 'campanaEraLeida', 'campanaLeidaEn']
  .map(key => ({ key, value: '', type: 'string' }));
const coleccion = {
  info: { name: 'Maxi Eventos', _postman_id: '6f1d0c52-8a4e-4b7c-9e21-3c5a9b7d2e10',
    description: 'Colección de la Fase 9C: login, una carpeta por servicio (happy path, 400, 404, 409, filtros, paginación y 401), el flujo completo del negocio, el reto de la semana 8 y el gateway GraphQL. No modifica los datos de demostración (cargar-demo.ps1): solo los consulta, crea sus propias entidades con un sufijo único y las cancela o borra al final; la carpeta 99 lo comprueba. Ver postman/README.md.',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json' },
  auth: { type: 'bearer', bearer: [{ key: 'token', value: '{{token}}', type: 'string' }] },
  item: [prep, clientes, lugares, proveedores, eventos, finanzas, contratos, logistica, auditoria, notificaciones, flujo, reto, graphql, roles, campana, limpieza, verif],
  variable: variablesColeccion,
};
const env = (k, v, tipo = 'default') => ({ key: k, value: v, type: tipo, enabled: true });
const entorno = { id: '0b8e7a4c-2f51-4d3a-9c6e-7d1f2a3b4c5d', name: 'Maxi Eventos - local', values: [
  env('urlAuth', 'http://localhost:8080/api/v1'), env('urlEventos', 'http://localhost:8081/api/v1'), env('urlClientes', 'http://localhost:8082/api/v1'),
  env('urlLugares', 'http://localhost:8083/api/v1'), env('urlFinanzas', 'http://localhost:8084/api/v1'), env('urlContratos', 'http://localhost:8085/api/v1'),
  env('urlProveedores', 'http://localhost:8086/api/v1'), env('urlAuditoria', 'http://localhost:8087/api/v1'), env('urlLogistica', 'http://localhost:8088/api/v1'),
  env('urlNotificaciones', 'http://localhost:8089/api/v1'), env('gatewayUrl', 'http://localhost:8090/graphql'),
  env('gatewayBaseUrl', 'http://localhost:8090'),
  env('correo', 'organizadora@maxieventos.com'), env('password', '', 'secret'),
  // Fase 11D: el usuario ASISTENTE (carpeta 12b); la contraseña, como la de la organizadora, vacía en el archivo
  env('correoAsistente', 'asistente@maxieventos.com'), env('passwordAsistente', '', 'secret'),
  env('demoEventoReservadoId', ''), env('demoEventoPendienteId', ''), env('demoEventoCanceladoId', ''), env('demoClienteId', ''),
  env('demoLugarId', ''), env('demoLugarInactivoId', ''), env('demoProveedorId', ''), env('demoContratoId', ''),
], _postman_variable_scope: 'environment' };

fs.writeFileSync(path.join(destino, 'maxi-eventos.postman_collection.json'), JSON.stringify(coleccion, null, 2) + '\n', 'utf8');
fs.writeFileSync(path.join(destino, 'maxi-eventos-local.postman_environment.json'), JSON.stringify(entorno, null, 2) + '\n', 'utf8');
let n = 0; coleccion.item.forEach(c => { n += c.item.length; });
console.log(`Colección: ${coleccion.item.length} carpetas, ${n} peticiones`);
