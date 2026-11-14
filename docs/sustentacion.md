# Guía para la sustentación

Nueve demostraciones en vivo (unos 30 minutos en total) y, al final, las preguntas de cierre de las diapositivas (semanas 4 a 13) con la respuesta corta según
**nuestro** proyecto. Las capturas para entregar están en [`evidencias.md`](evidencias.md); esta guía es para mostrar en vivo.

## Antes de empezar (10 minutos antes)

```powershell
docker compose up -d                   # Docker Desktop en marcha; 16 contenedores: esperar 15 healthy (kafdrop no tiene healthcheck)
docker compose ps
. .\scripts\verificacion\comun.ps1      # en la ventana de PowerShell que se va a usar, desde la raíz del proyecto
Login | Select-Object Status            # 200 (no muestra la contraseña)
$demo = @((Api GET "$($Url.eventos)/eventos?estado=RESERVADO&size=100&sort=fechaCreacion,asc").Body.contenido |
          Where-Object nombreEvento -like 'Quince a*os de Luc*a')[0]
$eid = $demo.id; "evento de demostración: $eid ($($demo.fecha))"
```

- Abrir en pestañas: GraphiQL (http://localhost:8090/graphiql, ya con `login`, el token en **Headers** y *Re-fetch schema*;
  ver [P3 §0.2](evidencias-p3e1-graphql.md#02-graphiql-con-la-introspección-protegida-hacerlo-siempre-al-abrir-graphiql)) y
  Kafdrop (http://localhost:9000).
- Letra grande en la terminal y en VS Code. **Nunca** abrir el `.env` ni mostrar el token completo.
- Si los datos de demostración no están (base nueva): `scripts\verificacion\cargar-demo.ps1`.
- **Fase 11E:** la Boda de Gabriela y Mateo tiene fecha **calculada al cargar** (el primer fin de semana libre de los
  próximos 7 días), así que el reinicio con `cargar-demo.ps1` se hace **el mismo día de la presentación (o el anterior)**.
  Ese día, `RECORDATORIOS_CRON=-` en el `.env` (y `docker compose up -d eventos-service`): la tarea de las 7:00 no envía el
  recordatorio y se muestra con el botón (demo 9).

### Datos de demostración: qué evento usar para mostrar cada cosa

Base reiniciada el **2026-11-06** al cerrar la Fase 11 (`pg_dump` a `respaldos_maxi_eventos\antes_fase_11G`, `docker compose
down -v`, `up -d` y `cargar-demo.ps1`: 160 comprobaciones, 0 fallos, 22 s; reinicio repetido al cerrar la Fase 11H y otra vez el **2026-11-10** al cerrar la 11I (`cierre_fase_11I`; 160 / 0, 21 s) y tras sus ajustes finales (`recarga_11I`; 160 / 0, 23 s; desde entonces todo lo que crean los scripts y Postman empieza por "Prueba"), con la campanita en 0 para
las dos usuarias). Hay **10 eventos** (9 activos y 1 cancelado),
uno por fin de semana y ninguno "Prueba", **10 clientes**, 5 lugares y 6 proveedores. En la interfaz, el listado los muestra
todos con la casilla "Mostrar eventos de prueba" desmarcada. **Ningún recordatorio enviado todavía** (se envían en la demo 9).

| Para mostrar… | Evento (fecha) | Dónde |
|---|---|---|
| **La ficha completa en una sola vista / una sola query** | **Quince años de Lucía** (sáb 5-dic-2026), RESERVADO: saldo $ 6.270.000, contrato firmado, 2 pagos, adicional, 2 proveedores, logística 7 / 4 / 3 | Demos 1 y 8; Postman y guías |
| **El 409 al confirmar** (contrato sin firmar) | **Quince años de Sofía** (sáb 28-nov-2026), PENDIENTE con contrato pendiente de firma | Ficha → paso "Confirmar" → "Confirmar evento" → "El contrato del evento está en estado PENDIENTE; debe estar FIRMADO…" (sin cambiar nada) |
| **Resumen "Todo listo"** (paso 7, 11F) | **Boda de Valentina y Santiago** (sáb 21-nov-2026), RESERVADO y COMPLETO: pagada (saldo $ 0), Finca La Ceiba, 2 proveedores confirmados y 5 de 5 compras | Ficha → paso 7 **Resumen** → "✓ Todo listo" y las 6 tarjetas listas (el camino con los 7 pasos en ✓) |
| **Resumen con pendientes** (paso 7, 11F) | **Quince años de Sofía**: "Quedan 4 pendientes" (contrato sin firmar, depósito: faltan $ 2.505.000, por confirmar, sin proveedores). Otros: Lucía 3, Camilo y Juliana 3, Isabella 3, Sebastián y Laura 6 | Ficha → paso 7 **Resumen** → cada pendiente enlaza a su paso |
| Abono parcial (saldo pendiente) | Quince años de Isabella (vie 12-feb-2027), RESERVADO, Kiosco Las Palmas | Saldo $ 5.600.000; "Registrar pago" |
| Adicionales, anexo, proveedores y lista de compras | **Boda de Camilo y Juliana** (sáb 20-mar-2027), RESERVADO: 3 adicionales, 3 proveedores, compras "3 de 7 conseguidos" con 2 "Agregado a mano", logística 14 / 7 / 3 (+1 camión por el transporte adicional) | Ficha: secciones Servicios adicionales, Proveedores y Logística; "Descargar anexo (PDF)" |
| Evento recién creado | Boda de Sebastián y Laura (sáb 15-may-2027), PENDIENTE sin cotización | "Generar cotización" |
| Cotizado con lugar (siguiente paso: contrato) | Boda de Natalia y Felipe (dom 20-jun-2027) y Boda de Andrés y Camila (sáb 2-ene-2027), PENDIENTES | "Crear contrato" |
| Evento cancelado | Quince años de María José (sáb 30-ene-2027), CANCELADO | Solo descargas; su fin de semana vuelve a estar libre en el calendario |
| Regla de un evento por fin de semana | Nuevo evento → calendario: los fines de semana de los eventos activos salen "No disponible" (13-nov, 21-nov, 28-nov, 5-dic, 2-ene, 12-feb, 20-mar, 15-may, 20-jun); el 30-ene está libre (evento cancelado) | Demo 8 |
| Lugar y proveedor inactivos | Hacienda El Pórtico (lugar INACTIVO) y Foto Recuerdos del Huila (proveedor inactivo) | Lugares y Proveedores (filtro de estado) |
| **Recordatorios por Kafka** (11E) | **Boda de Gabriela y Mateo** (fecha calculada al cargar: con la carga del 2026-11-10 quedó el **vie 13-nov-2026**, "En 3 días"), RESERVADO, cliente Gabriela Perdomo Trujillo | Demo 9: Eventos → bloque "Próximos 7 días" → "Ejecutar recordatorios" (solo administrador) → su ficha muestra "Recordatorio enviado: falta 1 día" (o "faltan N días" si se carga antes) |
| **Roles** (11D) | Iniciar sesión como **asistente** (`AUTH_ASISTENTE_EMAIL`; su contraseña está solo en el `.env`) | Lugares y Proveedores sin botones de administración, la ficha sin Auditoría, sin "Ejecutar recordatorios" |
| **Error técnico con código de referencia** (11C) | Cualquier ficha con un servicio detenido (p. ej. `docker compose stop contratos-service`) | La sección Contrato muestra el mensaje genérico con "Ref: XXXXXXXX"; ese código se busca en el log (ver la demo 8) |

#### Eventos con fecha pasada (Fase 11I): por qué la demostración no tiene ninguno

- **La demostración no tiene eventos pasados**, y no puede tenerlos: la API **no permite crear eventos en fechas pasadas**
  (ni siquiera hoy: la fecha debe ser posterior), y los datos se crean **solo por la API** (`cargar-demo.ps1`, Postman y
  los scripts nunca escriben directamente en la base). Por eso, tras cargarla, la sección "Eventos pasados" del listado
  (siempre al final, plegada) dice **"Eventos pasados (0)"** y, al abrirla, "Todavía no hay eventos pasados. Los eventos
  aparecerán aquí automáticamente cuando pase su fecha."; ningún evento se ve "Realizado" ni "Vencido".
- **En uso real, los eventos realizados aparecen solos en "Eventos pasados"** con el paso de los días: el estado en la base
  no cambia (sigue RESERVADO o PENDIENTE); al día siguiente de su fecha, la interfaz lo pasa a esa sección (plegada, con su
  número, en gris, el más reciente primero) como **"Realizado"** (RESERVADO) o **"Vencido"** (PENDIENTE que nunca se
  confirmó). En su ficha, el aviso "Este evento se realizó el …", sin confirmar, cancelar, lugar, cotización, contrato, proveedores ni
  adicionales, pero con pagos, PDF, lista de compras y el Resumen (que sigue marcando el saldo como pendiente). Y la API
  rechaza confirmarlo: **409 "No se puede confirmar un evento cuya fecha ya pasó."**
- **Qué prueba automática lo demuestra:**
  - Las etiquetas **Realizado y Vencido**: `frontend/src/utils/fechaPasada.test.js` (con "hoy" fijo: un evento de ayer →
    Realizado / Vencido; de hoy y de mañana → Reservado / Pendiente; cancelado igual) y
    `frontend/src/paginas/EventosPasados.test.jsx` (la insignia con su texto y su `aria-label`, la sección "Eventos pasados"
    plegada con su número, el caso límite del evento de hoy, "Próximos 7 días" sin pasados, la ficha con las acciones
    ocultas y visibles, y el Resumen con el saldo pendiente). Se ejecutan con `cd frontend` y `npm test`.
  - El **409 del backend**: `EventoServiceConfirmacionTest` (unitaria) y `EventoApiIntegracionTest` →
    `fechaPasada409YElMismoDiaSeConfirma` (PostgreSQL real), las dos con un `Clock` fijo que "viaja" al día siguiente del
    evento. No hay petición de Postman para este 409 (ver [`postman/README.md`](../postman/README.md)).
- Si se pregunta en la sustentación, se muestra la prueba (`npx vitest run src/paginas/EventosPasados.test.jsx` dentro de
  `frontend`) en lugar de la base, **o se activa el modo demostración** (abajo) para verlo en el navegador.

#### Mostrarlo en el navegador: modo demostración con fecha simulada (solo la interfaz)

`FRONTEND_FECHA_SIMULADA` (opcional, `AAAA-MM-DD`) hace que **la interfaz** use esa fecha como "hoy" para todo lo que calcula
ella: Realizado / Vencido, "Eventos pasados", "Próximos 7 días" y el calendario de Nuevo evento. En la barra aparece, en
todas las pantallas, el aviso ámbar **"MODO DEMOSTRACIÓN: fecha simulada jueves, 10 de diciembre de 2026 (2026-12-10). El
servidor sigue con la fecha real."**, para que nadie lo confunda con el uso real. No toca la base.

**Activar** (en la carpeta del proyecto, con el entorno levantado):

1. En el `.env`, agregar la línea `FRONTEND_FECHA_SIMULADA=2026-12-10`.
2. `docker compose up -d frontend` (recrea solo el contenedor de la interfaz, sin reconstruir; unos segundos).
3. Recargar la página en el navegador (`/config.js` no se guarda en caché). Debe verse el aviso ámbar en la barra.

**Desactivar:** borrar (o comentar con `#`) esa línea del `.env`, `docker compose up -d frontend` y recargar la página: el
aviso desaparece. Para comprobarlo sin el navegador: `curl http://localhost:5173/config.js` → `fechaSimulada: ""`.

**Qué se ve con 2026-12-10 y la demostración:**

- Eventos → al final, **"Eventos pasados (4)"** (abrirla): **"Quince años de Lucía"** (sáb 5-dic, RESERVADO) **Realizado**;
  **"Quince años de Sofía"** (sáb 28-nov, PENDIENTE con contrato sin firmar) **Vencido**; y Realizados también la Boda de
  Valentina y Santiago (21-nov) y la de Gabriela y Mateo (13-nov). Arriba siguen los próximos (2-ene en adelante) y
  "Próximos 7 días" (10 a 17 de diciembre) dice que no hay ninguno.
- Ficha de **Lucía** → aviso "Este evento se realizó el sábado, 5 de diciembre de 2026.", se abre el **Resumen** con "El
  evento ya se realizó" y **"Queda 1 pendiente · Abonos: Saldo pendiente $ 6.270.000"**; sin Cancelar, Cambiar lugar,
  adicionales ni proveedores, pero con **Registrar pago** y los PDF.
- Ficha de **Sofía** → "Este evento venció el sábado, 28 de noviembre de 2026 sin confirmarse."; en el paso "Confirmar",
  "Este evento ya no se puede confirmar: su fecha pasó sin confirmarse." y sin el botón.

**Limitación (decirla si se usa):** el **backend no cambia** y sigue con su reloj real. Por eso:

- Lo que se oculta (confirmar, cancelar…) se oculta **solo en la interfaz**: la API seguiría aceptando esas acciones,
  porque para ella la fecha de Lucía o de Sofía todavía no pasó (el 409 "fecha ya pasó" solo aparece con la fecha real).
- **No registrar pagos ni hacer cambios** en este modo durante la demostración: se guardan de verdad, con la fecha real.
- "Ejecutar recordatorios", las fechas libres que devuelve el servidor (`/fechas/disponibles`), las horas de la campanita
  ("hace 5 min") y el vencimiento de la sesión usan la fecha y la hora **reales**. El calendario empieza en el mes simulado,
  pero solo ofrece los días que el servidor da como libres.
- Una fecha que no existe (p. ej. `2026-02-30`) impide que el contenedor arranque (el log de `frontend` dice por qué).
- Al terminar, **desactivarlo** antes de seguir con el resto de la presentación.

Pruebas: `frontend/src/paginas/FechaSimulada.test.jsx` (sin y con la variable, valores inválidos, el aviso en la barra, el
ejemplo de Lucía y Sofía con 2026-12-10, las consultas y el calendario) y `verificar_10.ps1` → sección 6 (la variable al
arrancar sin reconstruir, el aviso en el log, y tres valores inválidos que no arrancan).

> ⚠ **Antes de presentar no ejecutes Postman (`ejecutar-postman.ps1`) ni los scripts de verificación** (`verificar_*.ps1`,
> `probar-3.ps1`): crean clientes y eventos "Prueba …" (aunque los cancelan) y ensucian la base, el listado de auditoría y las
> fechas. Las demos de esta guía que crean datos (la 2, con un evento propio) los cancelan al terminar. Las verificaciones
> ya pasaron en la Fase 11G (antes del reinicio). Para volver a probar, repite el procedimiento: `pg_dump` → `docker compose
> down -v` → `up -d` → esperar los contenedores → `verificar_11E.ps1` si se quiere (siempre **antes** de cargar) →
> `cargar-demo.ps1` (unos 20 segundos: desde la 11E ya no hay demora de Kafka tras el `down -v`, ver
> [`kafka.md`](kafka.md#arranque-sobre-un-kafka-vacío)). Cargar el mismo día de la presentación (o el anterior).

| # | Demostración | Qué demuestra | Duración |
|---|---|---|---|
| 1 | La ficha completa por GraphQL y la query parcial | Gateway, integración con 9 servicios, resolución por campo | 3 min |
| 2 | El outbox con Kafka caído | Productor después del commit, ningún mensaje se pierde | 4 min |
| 3 | El duplicado ignorado y el mensaje inválido en el DLT | Idempotencia y manejo de errores del consumidor | 3 min |
| 4 | La caché: MISS, HIT y Redis caído | Redis como caché, opcional para la disponibilidad | 3 min |
| 5 | Acceso permitido y denegado con el JWT (y CORS) | Seguridad en el gateway y en cada servicio | 3 min |
| 6 | Las pruebas estrella y su mutación | Las pruebas verifican de verdad (fallan si se rompe la regla) | 4 min |
| 7 | La colección de Postman completa y el reto de la semana 8 | API REST estable de principio a fin, errores predecibles | 2 min |
| 8 | La interfaz: la ficha en una sola vista (Fase 10) y su resumen (paso 7, Fase 11F) | El requisito no funcional de la Entrega 2; solo habla con el gateway | 4 min |
| 9 | Recordatorios de eventos próximos (Fase 11E) | Tarea programada, Kafka asíncrono con dos consumidores, sin duplicados, rol administrador | 3 min |

---

## 1. La ficha completa por GraphQL y la query parcial

**Qué demuestra:** el gateway entrega en **una** query la ficha del evento consultando a 9 servicios, y **solo llama a los
servicios de los campos pedidos**.

1. GraphiQL, pestaña **Variables**: `{ "id": "<$eid>" }` (pegar el id que imprimió la preparación).
2. Pegar la query **Ficha** (ejemplo 7 de [`graphql.md`](graphql.md#7-la-ficha-completa-en-una-query)) y ▶.
3. En PowerShell:

   ```powershell
   cls; docker compose logs gateway --since 1m | Select-String "REST -> "
   ```

4. Ahora la query parcial y, justo después, los logs desde ese momento:

   ```graphql
   query($id: ID!) { evento(id: $id) { nombreEvento cliente { nombreCompleto } } }
   ```

   ```powershell
   $t0 = Get-Date        # ejecutar ANTES de pulsar ▶ en GraphiQL
   (LogsDesde 'gateway' $t0) -split '\r?\n' | Select-String "REST -> " | ForEach-Object Line
   ```

**Qué debe verse:** en el paso 2, "Quince años de Lucía" con cliente, cotización, contrato FIRMADO, 2 pagos, balance (saldo
6.270.000), lugar, 2 proveedores, el adicional, la logística (7 meseros, 4 de cocina, 3 vehículos), 3 notificaciones y 4
mensajes de auditoría. En el paso 3, una línea `REST -> <servicio> GET …` por cada servicio consultado. En el paso 4,
**exactamente 2 líneas**: `REST -> eventos GET /api/v1/eventos/<id> 200` y `REST -> clientes GET /api/v1/clientes/<id> 200`;
ninguna a finanzas ni a contratos.

**Para comentar:** las mutations también existen (`crearEvento`, `registrarPago`, `confirmarEvento`…), pero **delegan** en
una sola llamada REST: las reglas de negocio están solo en los servicios. El flujo completo por GraphQL lo recorre
`scripts\verificacion\verificar_6B.ps1`.

## 2. El outbox con Kafka caído

**Qué demuestra:** el mensaje se guarda en la misma transacción que el pago (`finanzas_mensajes_salientes`); si Kafka está
caído, el pago responde **201** igual y el mensaje se publica solo cuando Kafka vuelve. Usa un evento nuevo (no toca la
demostración).

```powershell
# Preparación: cliente, evento (un sábado de un fin de semana libre) y cotización nuevos
$cli = (CrearCliente 'Cliente Demo Outbox').Body
$ev = (Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Demo outbox con Kafka caído'; clienteId = $cli.id; fecha = (FechasLibres)[0]; tipoEvento = 'BODA'; cantidadAdultos = 80; cantidadNinos = 5; zona = 'NEIVA' }).Body
(Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $ev.id }).Status                      # 201

# 1. Kafka abajo y un abono
docker compose stop kafka
(Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $ev.id; clienteId = $cli.id; metodoPago = 'EFECTIVO'; monto = 100000; moneda = 'COP' }).Status
Start-Sleep -Seconds 60     # el productor tarda hasta 30 s en dar el envío por fallido: esperar con Kafka abajo
FilaOutbox 'finanzas_mensajes_salientes' $ev.id 'PAGO_REGISTRADO' | Format-List

# 2. Kafka arriba: la tarea de reenvío (cada 15 s, filas con más de 30 s) lo publica sola
docker compose start kafka
EsperarEstadoOutbox 'finanzas_mensajes_salientes' $ev.id 'PAGO_REGISTRADO' 'PUBLICADO' 180 | Format-List
docker compose logs finanzas-service --since 5m | Select-String "FALLÓ la publicación|Reenvío del outbox" | ForEach-Object Line
(Api GET "$($Url.auditoria)/auditoria?eventoId=$($ev.id)").Body.contenido | Select-Object tipo, topico, servicioOrigen

# Al terminar: cancelar el evento (libera la fecha)
(Api PATCH "$($Url.eventos)/eventos/$($ev.id)/cancelar").Status                             # 200
```

**Qué debe verse:** el abono responde **201** con Kafka detenido; tras el minuto de espera la fila está `PENDIENTE` (o
`ENVIANDO`, si justo la está reintentando la tarea) con `Intentos ≥ 1` y `UltimoError`; al volver Kafka pasa a **`PUBLICADO`**
(puede tardar 1–2 minutos mientras Kafka arranca); en el log, `FALLÓ la publicación de PAGO_REGISTRADO (queda PENDIENTE en el
outbox…)` y después `Reenvío del outbox. idEvento=…, tipo=PAGO_REGISTRADO, … resultado=PUBLICADO`; la auditoría tiene el
`PAGO_REGISTRADO`.

> **No saltarse la espera de 60 s.** Si Kafka vuelve antes de que el productor dé el envío por fallido (hasta 30 s), el
> propio productor entrega el mensaje: queda `PUBLICADO` con 1 intento y sin pasar por el reenvío (comprobado en la 9E). Es
> correcto, pero no muestra el outbox.

**Para comentar:** publicar en `AFTER_COMMIT` evita publicar algo que se revirtió, pero si Kafka cae justo después del
commit el mensaje se perdería: por eso el outbox. Es **entrega al menos una vez**; el reintento lleva el **mismo `idEvento`**
y los consumidores descartan los duplicados (demo 3).

## 3. El duplicado ignorado y el mensaje inválido en el DLT

**Qué demuestra:** idempotencia de los consumidores (tabla `<servicio>_eventos_procesados`) y que un mensaje inválido va
**directo** al DLT con un solo intento, sin bloquear la partición. No cambia la demostración.

```powershell
$m = @(LeerTopico 'pagos.registrados' | Where-Object { $_.Clave -eq $eid })[0]
$antes = Psql "SELECT (SELECT count(*) FROM auditoria_eventos), (SELECT count(*) FROM notificaciones);"
$inv = [ordered]@{ idEvento = [guid]::NewGuid().ToString(); tipo = 'PAGO_DESCONOCIDO'; version = 1; fechaOcurrencia = '2026-10-28T12:00:00Z'; servicioOrigen = 'sustentacion'; eventoId = $eid; datos = @{ monto = 1 } } | ConvertTo-Json -Compress
$desde = Get-Date
InyectarMensajes 'pagos.registrados' @("$eid#$($m.Valor)", "$eid#$inv")      # el mismo pago otra vez + uno inválido
Start-Sleep -Seconds 8
((LogsDesde 'auditoria-service' $desde) + (LogsDesde 'notificaciones-service' $desde)) -split '\r?\n' | Select-String "resultado=" | ForEach-Object Line
"antes: $antes  /  después: $(Psql "SELECT (SELECT count(*) FROM auditoria_eventos), (SELECT count(*) FROM notificaciones);")"
```

Después, Kafdrop → `pagos.registrados.DLT` → *View Messages* (la partición del evento, Count 100).

**Qué debe verse:** 2 líneas `resultado=DUPLICADO_IGNORADO, detalle=idEvento ya procesado por auditoria-group` /
`notificaciones-group` (mismo `idEvento` que el original, otro offset), 2 líneas `resultado=INVALIDO_A_DLT` con
`MensajeInvalidoException: Tipo de mensaje no esperado…` y **ninguna** línea `REINTENTO`; los conteos antes y después son
**iguales**; en Kafdrop, el inválido en el DLT con las cabeceras `kafka_dlt-original-topic`, `-original-offset`,
`-original-consumer-group` y `-exception-message`.

**Para comentar:** los errores transitorios (BD caída) sí se reintentan 3 veces con 1 s y después van al DLT
(`REINTENTOS_AGOTADOS_A_DLT`); sin bucle infinito. Nadie consume los DLT: se revisan a mano.

## 4. La caché: MISS, HIT y Redis caído

**Qué demuestra:** Redis como caché con logs de HIT/MISS, y que es **opcional para la disponibilidad**.

```powershell
$lid = @((Api GET "$($Url.lugares)/lugares?estado=ACTIVO&size=100").Body.contenido | Where-Object nombre -like 'Sal*Ocobos')[0].id
Redis "redis-cli DEL maxi:lugares:lugares::$lid" | Out-Null       # empezar sin la entrada
$t0 = Get-Date
1..2 | ForEach-Object { (Api GET "$($Url.lugares)/lugares/$lid").Status }
(LogsDesde 'lugares-service' $t0) -split '\r?\n' | Select-String "Caché" | ForEach-Object Line
Redis "redis-cli TTL maxi:lugares:lugares::$lid"

# Redis caído
docker compose stop redis
(Api GET "$($Url.lugares)/lugares/$lid").Status
curl.exe -s http://localhost:8083/actuator/health
docker compose logs lugares-service --since 1m | Select-String "Caché no disponible"
docker compose start redis
```

**Qué debe verse:** `Caché MISS  cache=lugares clave=<id> -> se consulta la base de datos` y luego `Caché HIT   cache=lugares
clave=<id>`; el TTL (≤ 1800 s). Con Redis detenido: **200** desde la base de datos, health `{"status":"UP"}` y el WARN
`Caché no disponible al leer cache=lugares …`. Al arrancar Redis, el HIT vuelve solo (la reconexión puede tardar **unos 30 s**).

**Para comentar:** TTL de 10 min (clientes) y 30 min (lugares, proveedores, logística); `@CachePut` al actualizar y
`@CacheEvict` al borrar, así que nunca se entrega un dato viejo; valores en JSON (DTO); pagos y balance **no** se cachean.

## 5. Acceso permitido y denegado con el JWT (y CORS)

**Qué demuestra:** solo `login` y `health` son públicos; el gateway rechaza sin llamar a nadie y **cada servicio** vuelve a
validar el token.

```powershell
. .\scripts\verificacion\credenciales.ps1
$t = (Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes((CuerpoLogin)))).token
curl.exe -s -i http://localhost:8082/api/v1/clientes                                          # sin token
curl.exe -s -i -H "Authorization: Bearer $t" "http://localhost:8082/api/v1/clientes?size=1"   # con token
curl.exe -s -i -H "Authorization: Bearer $($t.Substring(0, $t.Length-2))xx" http://localhost:8081/api/v1/eventos   # manipulado
```

En GraphiQL: borrar la pestaña **Headers** y ejecutar `{ eventos(size: 3) { totalElementos } }`; después
`docker compose logs gateway --since 1m | Select-String "REST -> |rechazado"`. Volver a poner el token y repetir.

CORS (opcional):

```powershell
curl.exe -s -i -X OPTIONS http://localhost:8090/graphql -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST"
curl.exe -s -i -X OPTIONS http://localhost:8090/graphql -H "Origin: http://sitio-ajeno.example" -H "Access-Control-Request-Method: POST"
```

**Qué debe verse:** `401` con `ErrorResponse` "Debes iniciar sesión…" sin token; `200` con token; `401` "El token no es
válido…" con el token manipulado. En GraphQL sin token: `UNAUTHORIZED` (`servicio: gateway`) y en el log solo `GraphQL
rechazado en el gateway…`, **ninguna línea `REST ->`**; con token, los datos y una línea `REST ->`. CORS: `200` con
`Access-Control-Allow-Origin: http://localhost:5173` para el origen permitido y `403 Invalid CORS request` para el ajeno.

## 6. Pruebas estrella y su mutación

**Qué demuestra:** las pruebas verifican la regla de verdad: si se rompe el código, fallan. Docker Desktop en marcha. Se
muestra una en vivo (la 1); las otras quedan en la tabla por si las piden.

```powershell
cd services\finanzas-service
.\gradlew.bat cleanTest test --no-daemon --tests "*dosAbonosSimultaneos*"        # pasa
```

1. En VS Code, `services/finanzas-service/src/main/java/com/maxieventos/finanzas/repository/CotizacionRepository.java`:
   comentar la línea `@Lock(LockModeType.PESSIMISTIC_WRITE)` de `bloquearPorEventoId` y guardar.
2. Ejecutar el mismo comando: **falla** con `[201, 201]` (los dos abonos pasan y el saldo quedaría negativo).
3. **Deshacer el cambio** (Ctrl+Z y guardar) y ejecutar otra vez: pasa. `cd ..\..`

| # | Prueba (módulo) | Qué demuestra | Comando (`--tests`) | Mutación → resultado |
|---|---|---|---|---|
| 1 | `FinanzasApiIntegracionTest` › `dosAbonosSimultaneosQueJuntosSuperanElSaldoUno201YOtro409` (finanzas) | Dos abonos simultáneos de 7 M sobre un saldo de 13 M: uno 201 y otro 409, con PostgreSQL real (no se puede probar con H2) | `"*dosAbonosSimultaneos*"` | Quitar `@Lock(PESSIMISTIC_WRITE)` en `CotizacionRepository.bloquearPorEventoId` → `[201, 201]` |
| 2 | `LogisticaKafkaIntegracionTest` › `unAdicionalDeTransporteRepetidoNoSumaOtroCamion` (logística) | El mismo mensaje dos veces por Kafka real: sigue con 2 camiones | `"*unAdicionalDeTransporteRepetido*"` | En `ConsumoLogisticaService.idempotente`, cambiar `procesadoRepository.existsById(clave)` (línea 130) por `false` → `expected: 2 but was: 3` |
| 3 | `LogisticaKafkaIntegracionTest` › `unMensajeInvalidoVaAlDltConUnSoloIntento` (logística) | Un inválido va al DLT con un solo intento y el log `INVALIDO_A_DLT` | `"*unMensajeInvalidoVaAlDlt*"` | Quitar `handler.addNotRetryableExceptions(MensajeInvalidoException.class)` en `KafkaConsumerConfig` → `TooManyActualInvocations` (3 intentos) |
| 4 | `CacheRedisIntegracionTest` › `elClienteSeGuardaEnRedisComoJsonYSeLeeDeVueltaSinIrALaBaseDeDatos` (clientes) | Redis real: clave, JSON, TTL de 10 min y la segunda lectura sale de Redis | `"*elClienteSeGuardaEnRedis*"` | Cambiar `ConfiguracionCache.tipo(ClienteResponse.class)` por `tipo(Object.class)` en `RedisCacheConfig` → `ClassCastException` |

Comando general: en `services\<módulo>`, `.\gradlew.bat cleanTest test --no-daemon --tests <patrón>` (`cleanTest` obliga a
ejecutarla aunque no haya cambios). Todas las pruebas: `scripts\verificacion\ejecutar-pruebas.ps1` (463, unos 9 minutos). La interfaz: `npm test` en `frontend\` (146).

## 7. La colección de Postman completa y el reto de la semana 8

**Qué demuestra:** la API REST de punta a punta (happy path, 400, 404, 409, filtros, paginación, 401, el flujo completo y
GraphQL) sin un fallo, y que la demostración queda intacta.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-postman.ps1
```

**Qué debe verse:** en unos 25–40 s, la tabla de Newman con **277 requests, 763 assertions, 0 failed**; en la salida, la carpeta
`11 Reto semana 8 (Finanzas)` (moneda distinta → 400 en `moneda`; monto mayor que el saldo → 409) y la `99 Datos de
demostración intactos` en verde. Si hay tiempo, abrir `11.02` en Postman para mostrar el `errores[]`.

### Regla de días permitidos (10B3)

**Qué demuestra:** una regla de negocio nueva que vive en eventos-service (no en la interfaz ni en el gateway) y sale de la
configuración: la empresa solo trabaja **viernes, sábado y domingo** (`maxi-eventos.dias-permitidos`, variable
`DIAS_PERMITIDOS`). En Postman: `04.11b` (crear un martes → **400** con `errores[{campo: "fecha", mensaje: "Solo se pueden
registrar eventos en viernes, sábado o domingo."}]`), `04.11c` (bloquear un martes → 400), `04.11d` (liberarlo → 200) y
`04.11e` (el martes no aparece en `/fechas/disponibles`). Se mantiene la regla de un evento por fin de semana (`04.16`).

**Para comentar:** la validación va **antes** de llamar a Clientes o Lugares (no gasta llamadas REST en una solicitud que
ya se sabe inválida) y el mensaje se arma con los días configurados (`DiasPermitidos`); un valor inválido en la
configuración impide arrancar el servicio. Los eventos que ya existían entre semana siguen siendo válidos: la regla se
aplica al crear y al bloquear. Pruebas: `DiasPermitidosTest`, `EventoServiceCreacionTest`, `FechaServiceTest` y la
integración `EventoApiIntegracionTest$Fechas`.

**En la interfaz (10B3e):** en *Nuevo evento* la fecha se elige en un **calendario mensual** (`CalendarioFechas.jsx`) que
pide cada mes a `fechasDisponibles` y **solo deja seleccionar** lo que esa query devuelve; los lunes a jueves salen "No
laborable" y los fines de semana ocupados (por ejemplo el del 5 de diciembre de 2026, "Quince años de Lucía") "No
disponible". Ya no se puede escribir una fecha a mano, así que **el caso del martes se demuestra con Postman (`04.11b`)**, no
desde la interfaz: la regla sigue siendo del backend, y la interfaz solo la refleja. Justo antes de crear el cliente y el
evento la interfaz vuelve a preguntar por esa fecha; si otra pestaña la tomó, avisa y recarga el mes.

## 8. La interfaz: la ficha en una sola vista (Fase 10)

**Qué demuestra:** el requisito no funcional de la Entrega 2 (*"interfaz minimalista y centralizada que permita consultar la
información completa de un evento desde una única vista"*), servida por nginx desde Docker en **http://localhost:5173**, que
**solo habla con el gateway** ([`frontend.md`](frontend.md)).

1. Abrir http://localhost:5173, iniciar sesión, **Eventos** → filtro Estado = Reservado → "Quince años de Lucía".
2. Con DevTools → **Red** abierto: la ficha completa llega en **una sola** petición `FichaEvento` a
   `http://localhost:8090/graphql` (la misma query de la demo 1). La ficha abre en la **guía paso a paso** (Lucía, ya
   reservada, en "Preparativos"); **"Ver todo junto"** muestra las 11 secciones y la franja con el saldo pendiente
   **$ 6.270.000**. Cambiar de paso **no** hace peticiones nuevas: todo sale de esa misma respuesta.
3. **Nuevo evento:** el calendario de diciembre de 2026 muestra el 4–6 "No disponible" (el evento de Lucía) y los lunes a
   jueves "No laborable": la interfaz solo refleja las reglas de eventos-service.
3b. **Un error controlado:** abrir "Quince años de Sofía" (contrato sin firmar; la guía abre en el paso Contrato) → paso
   **Confirmar** (la lista marca "Contrato firmado (pendiente)") → **Confirmar evento** → el 409 de Eventos junto al botón,
   sin recargar ni cambiar nada. El botón está siempre en ese paso: la interfaz marca lo que falta, pero **decide el servicio**. Si hay tiempo: "Boda de Camilo y Juliana" para los adicionales, los
   proveedores y la lista de compras con ítems manuales.
3c. **El resumen (paso 7, Fase 11F):** en la misma ficha de Sofía, el paso **Resumen** (o "Siguiente: Resumen" desde
   Preparativos): "Quedan 4 pendientes" con sus enlaces (contrato sin firmar, depósito que falta, por confirmar, sin
   proveedores) y las 6 tarjetas con "✓ Listo" o "! 1 pendiente". Clic en "Contrato: Contrato sin firmar" → la guía salta a
   ese paso. Con DevTools → Red abierto: **ninguna petición nueva** (sale de la misma `FichaEvento`). Después, la **Boda de
   Valentina y Santiago** (el evento completo de la demostración): **"✓ Todo listo"**, las 6 tarjetas listas y los 7 pasos
   del camino con ✓.
4. DevTools → Red → la petición a `localhost` → cabecera **`Content-Security-Policy`** (`connect-src 'self'
   http://localhost:8090`); Aplicación → **Session Storage** con `maxi-eventos.token` (no mostrarlo entero). Cerrar sesión
   lo borra.

**Para comentar:**
- Sin lógica de negocio en la interfaz: los botones se muestran según el estado, pero los 409 los decide el servicio y su
  mensaje se ve en pantalla.
- Tras cada acción se recarga la ficha completa.
- El token va en `sessionStorage` y la CSP estricta lo protege del XSS ([`seguridad.md`](seguridad.md#la-interfaz-web-fase-10-token-csp-y-cabeceras)).
- La URL del gateway se configura **al arrancar** el contenedor (`FRONTEND_GATEWAY_URL`) sin reconstruir la imagen.
- Verificación: `scripts\verificacion\verificar_10.ps1` (80 comprobaciones, con un recorrido real en Edge sin ventana,
  incluido el paso 7 a 1280 y 360 px), `verificar_11H.ps1` (la campanita, 47) y `npm test` (146).

---

## 9. Recordatorios de eventos próximos (Fase 11E)

**Qué demuestra:** una tarea programada que publica en Kafka y dos consumidores independientes (Notificaciones y Auditoría),
**sin duplicados** aunque se ejecute varias veces, y una operación reservada al **administrador**. Detalle en
[`kafka.md`](kafka.md#recordatorios-evento_proximo-fase-11e).

Requisito: la base cargada el mismo día (o el anterior) y `RECORDATORIOS_CRON=-` (ver "Antes de empezar"). Ojo: el
recordatorio se envía para eventos de **mañana en adelante**; si la presentación cae el mismo día del evento de Gabriela
(o después), ya no sale en el bloque: hay que volver a cargar la demostración ese día.

1. En la interfaz, como administradora: **Eventos** → arriba, el bloque **"Próximos 7 días"** con la Boda de Gabriela y
   Mateo ("Mañana" / "En N días"). La **campanita** (junto al nombre, arriba) está **sin número**: la carga dejó todo leído.
2. **Ejecutar recordatorios** → "Recordatorios revisados: 1 recordatorio enviado; 0 ya se habían enviado (no se repiten)."
   En unos segundos, **sin recargar la página**, la campanita muestra **1**.
2b. **Abrir la campanita** (Fase 11H): "● Nueva — Falta 1 día para su evento: Boda de Gabriela y Mateo", con el nombre del
   evento y la hora en que se recibió ("Hoy, 3:45 p. m. · hace 1 min"). Clic en ella → se marca como leída, el número
   desaparece y se abre la ficha del evento. En otra ventana privada con la **asistente**, su campanita sigue en **1**: la
   lectura es por usuario.
3. Abrir la Boda de Gabriela y Mateo: el aviso **"Recordatorio enviado: falta 1 día"** (si el evento es mañana; "faltan N
   días" si faltan más) se ve en todos los pasos de la guía;
   en **Historial → Notificaciones**, la notificación "Falta 1 día para su evento: Boda de Gabriela y Mateo" (o "Faltan N
   días…"; tipo
   "Recordatorio de evento próximo") y en **Auditoría** el mensaje `EVENTO_PROXIMO` del tópico `eventos.proximos`.
4. Volver a **Ejecutar recordatorios** → "0 recordatorios enviados; 1 ya se había enviado": no se repite (tabla
   `eventos_recordatorios_enviados` en la misma transacción que el outbox, e `idEvento` determinista).
5. Si hay tiempo: iniciar sesión como **asistente** → el bloque se ve pero sin el botón; por GraphQL o REST, la operación
   responde FORBIDDEN / 403 (Postman 12b.18 y 12b.19).

**Para comentar:**
- Umbrales 7 y 1 (`RECORDATORIOS_DIAS`): se envía el más pequeño que aplica; PENDIENTE y CANCELADO no reciben recordatorio.
- La tarea programada y el botón hacen exactamente la misma revisión; la regla vive en eventos-service.
- Kafka asíncrono: eventos-service no sabe quién consume; Notificaciones y Auditoría tienen su propio grupo, idempotencia y DLT.
- **La campanita usa polling** (cada 30 s, pausado con la pestaña oculta, y al instante tras una acción), **no SSE ni
  suscripciones**: el `EventSource` del navegador no envía el header `Authorization` (todo el sistema autentica con
  `Bearer`), y las suscripciones GraphQL exigirían WebSocket en el gateway, en nginx y en la CSP. Para avisos que llegan
  unas pocas veces al día, una petición pequeña cada 30 s basta y no cambia la infraestructura
  ([`frontend.md`](frontend.md#campanita-de-notificaciones-fase-11h)).
- "Leída" se guarda **por usuario** (`notificaciones_lecturas`, con el `sub` del JWT): la organizadora y el asistente tienen
  cada uno su campanita.

---


## Preguntas de cierre de las diapositivas

Una respuesta corta por pregunta, según **nuestro** proyecto, y dónde se ve (archivo, petición de Postman o demostración de
esta guía). Rutas de código abreviadas: `eventos/…` = `services/eventos-service/src/main/java/com/maxieventos/eventos/…`.

### Semana 4

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿Qué diferencia existe entre DTO y entidad? | La **entidad** es la tabla (JPA, `@Entity`): lo que se guarda. El **DTO** es el contrato de la API: lo que entra y sale por HTTP. Ningún controller devuelve entidades; la caché también guarda DTO en JSON. | `eventos/entity/Evento.java` vs `eventos/dto/CrearEventoRequest.java` y `EventoResponse.java` |
| ¿Dónde debería vivir una regla de negocio? | En la capa **service** del servicio dueño del dato: la regla del fin de semana en `ReglaFinDeSemana` y `EventoService` (y la de días permitidos en `DiasPermitidos`, 10B3), el saldo en `PagoService`. Nunca en el controller ni en el gateway. | `eventos/service/ReglaFinDeSemana.java`; demo 1 (el gateway solo delega) |
| ¿Cuándo es necesaria la paginación? | Cuando un listado puede crecer sin límite: eventos, clientes, pagos, auditoría, notificaciones. Todos usan `page`, `size` (máximo 100) y `sort` con lista blanca. | `eventos/controller/Paginacion.java`; Postman `[paginación]`, `04.04`, `04.05` |
| ¿Qué información debe contener un error útil? | Código, tipo, un mensaje en español que diga qué pasó y cómo corregirlo, la ruta, la fecha y, en validaciones, el detalle por campo. Nunca un stack trace. Es nuestro `ErrorResponse`: `timestamp, status, error, message, path, errores[{campo, mensaje}]`. | `eventos/exception/ErrorResponse.java`; Postman `04.12` |

### Semana 6

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿Por qué el controller no debería contener toda la lógica? | El controller solo traduce HTTP (valida la entrada con `@Valid`, elige 201 + `Location`, 204…). Con la lógica en el service se prueba sin HTTP (pruebas unitarias con Mockito) y se reutiliza: los consumidores Kafka llaman a los mismos services. | `eventos/controller/EventoController.java`; `EventoServiceConfirmacionTest` |
| ¿Qué problema evita un DTO? | Exponer la tabla: campos internos, ciclos de serialización y que el cliente mande lo que no debe. `CrearEventoRequest` **no tiene `estado`**: el evento siempre nace PENDIENTE porque lo decide el service. Además, la API puede cambiar sin cambiar la BD. | `eventos/dto/CrearEventoRequest.java` |
| ¿Dónde se aplica una regla de negocio? | En el service del dueño del dato; si otro servicio la necesita, la consulta por REST: confirmar exige contrato FIRMADO (lo dice Contratos) y abono suficiente (lo dice Finanzas). Las restricciones de la BD (UNIQUE) son el respaldo ante peticiones simultáneas. | `EventoService` (confirmar); [`modelo-datos.md`](modelo-datos.md#restricciones-que-sostienen-reglas-de-negocio) |
| ¿Qué debe pasar cuando un recurso no existe? | **404** con un mensaje claro ("No existe un evento con id …"), también cuando el recurso es de otro servicio (crear un evento con un cliente inexistente → 404). En GraphQL, `NOT_FOUND`. | `RecursoNoEncontradoException`; Postman `04.09`, `04.14`, `12.05` |

### Semana 7

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿Qué información debería ser una entidad? | Lo que el servicio es **dueño** y debe guardar con su identidad: evento, fecha, cotización, pago, contrato… No: los datos de otro servicio (el evento guarda solo `cliente_id`) ni lo calculable (el balance se calcula, no se guarda). Excepción a propósito: la **foto** del contrato, porque un documento firmado no debe cambiar. | [`modelo-datos.md`](modelo-datos.md) |
| ¿Cuándo una relación puede generar problemas? | Entre servicios, una clave foránea los acoplaría: usamos solo ids. Dentro de un servicio: cargas perezosas y consultas N+1, ciclos al serializar o borrados en cascada inesperados. Solo tenemos dos FK reales, y la de proveedores impide borrar uno con asignaciones (409). En GraphQL, el N+1 se atenúa con `@BatchMapping`. | [`modelo-datos.md`](modelo-datos.md#diagrama-entidad-relación); Postman `10.24`; [`graphql.md`](graphql.md#el-problema-n1-y-lo-que-resuelve-batchmapping) |
| ¿Qué ventaja aporta la paginación? | Respuestas de tamaño acotado (memoria, tiempo y red previsibles), `totalElementos` y `totalPaginas` para navegar, y páginas cacheables (catálogo de lugares y proveedores en Redis). | Postman `04.04`; [`README`](../README.md#7-caché-redis) §7 |
| ¿Qué garantiza una transacción? | Todo o nada. Confirmar = evento RESERVADO + fecha OCUPADA + fila del outbox en **una** transacción; el consumidor guarda su cambio y el registro de idempotencia juntos. Con `FOR UPDATE`, dos abonos simultáneos no superan el saldo. | `eventos/kafka/OutboxKafka.java`; demo 6 (prueba estrella 1) |

### Semana 8

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿Qué diferencia hay entre validación de formato y de negocio? | **Formato**: el dato por sí solo (correo válido, monto > 0, fecha futura), con Bean Validation en el DTO → 400. **Negocio**: depende del estado del sistema (fecha ocupada, contrato sin firmar, monto mayor que el saldo) → normalmente 409. En el reto, la moneda distinta a la de la cotización es un 400 en el campo `moneda` y el monto mayor que el saldo, un 409. | Postman `04.12` vs `04.16`; carpeta 11 (reto) |
| ¿Por qué no devolver siempre 500? | Porque 500 dice "falló el servidor" y el cliente no sabe qué corregir. Cada caso tiene su código (400, 401, 404, 409, 503) con un mensaje útil; el 500 queda solo para lo inesperado, con un mensaje neutro y la traza solo en el log. | `eventos/exception/GlobalExceptionHandler.java` |
| ¿Qué debe ocurrir si OpenAPI no coincide con el código? | Es un error: el contrato es la promesa a quien consume la API. Se corrige el código o el contrato, el que esté mal. Lo detectamos con `comparar-openapi.ps1`, que contrasta cada YAML con el `/v3/api-docs` real; en la 9B corregimos 3 diferencias reales y 178 de reglas, y quedaron 0. | `scripts\verificacion\comparar-openapi.ps1`; [`evidencias-p2e1.md`](evidencias-p2e1.md#5-openapi-actualizado) §5.3 |
| ¿Qué es un sad path? | Un caso en que la operación **no** debe salir bien: datos inválidos, recurso inexistente o conflicto. Debe responder un error controlado y predecible, y se prueba igual que el happy path. | Postman `[400]`, `[404]`, `[409]`; `EventoApiIntegracionTest` |

### Semana 9

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿Por qué Kafka desacopla componentes? | El productor no conoce a los consumidores: Finanzas publica `PAGO_REGISTRADO` y no sabe que Auditoría y Notificaciones existen (se agregaron en la 5C sin tocar a Finanzas). También desacopla en el tiempo: si un consumidor está caído, el mensaje lo espera. | [`kafka.md`](kafka.md#productores-consumidores-grupos-y-tópicos); demo 2 |
| ¿Qué diferencia hay entre tópico y evento? | El **tópico** es el canal donde se guardan los mensajes (`pagos.registrados`, 3 particiones). El **evento** es un hecho de negocio que ya ocurrió y viaja como un mensaje (`PAGO_REGISTRADO`, con su sobre e `idEvento` únicos). Un tópico guarda muchos eventos. | Kafdrop; [`kafka.md`](kafka.md#sobre-común) |
| ¿Qué ocurre con dos consumidores del mismo grupo? | Se **reparten las particiones**: cada partición la lee uno solo, así que cada mensaje se procesa una vez por grupo. Con 3 particiones, como máximo 3 consumidores activos. Distintos grupos reciben cada uno **todos** los mensajes. La clave `eventoId` mantiene el orden de cada evento. | `kafka-consumer-groups --describe` ([`evidencias-p2e2.md`](evidencias-p2e2.md#3-consumidores) §3.2) |
| ¿Por qué debemos pensar en idempotencia? | La entrega es **al menos una vez**: reintentos, reenvío del outbox o un rebalanceo pueden repetir un mensaje. Sin idempotencia, un transporte adicional repetido sumaría otro camión. | Demo 3; demo 6 (prueba estrella 2: 2 camiones, no 3) |

### Semana 11

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿En qué momento debería publicarse un evento? | **Después del commit**: si se publica antes y la transacción se revierte, se anuncia algo que no pasó. Con el outbox, el mensaje se guarda en la misma transacción y se publica al confirmar; si Kafka está caído, se reenvía después. | `eventos/kafka/OutboxKafka.java` (`BEFORE_COMMIT` guarda, `AFTER_COMMIT` publica); demo 2 |
| ¿Qué debe hacer un consumidor? | Validar el mensaje (inválido → DLT sin reintentos), comprobar si ya lo procesó, aplicar el cambio y registrar el `idEvento` en una transacción, y dejar un log con grupo, tópico, partición, offset y resultado. Los errores transitorios se reintentan de forma limitada, sin bloquear la partición. | `ConsumoLogisticaService.java` (logística); [`kafka.md`](kafka.md#errores-reintentos-y-dlt) |
| ¿Cómo detectar un efecto duplicado? | Por el `idEvento` (único por mensaje e igual en cada reintento) en la tabla `<servicio>_eventos_procesados`, con clave `(id_evento, consumer_group)`. Si ya está, `DUPLICADO_IGNORADO`; si dos llegan a la vez, la clave primaria lo detecta. Se comprueba con conteos iguales antes y después. | Demo 3; [`evidencias-p2e2.md`](evidencias-p2e2.md#5-idempotencia) §5 |
| ¿Qué evidencia demuestra una integración real? | El recorrido completo con el broker real: la API confirma → el mensaje en Kafdrop (clave, partición, offset) → el log `PROCESADO` del consumidor → el cambio en la BD → visible por la API o en la ficha GraphQL. Además, una prueba con un broker embebido real (`@EmbeddedKafka`). | [`evidencias-p2e2.md`](evidencias-p2e2.md) §1–§4; Postman `10.13`–`10.31`; demo 1 |

### Semana 12

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿Qué es el schema? | El contrato tipado del gateway: tipos, enums, inputs, escalares, 7 queries y 12 mutations. Define qué se puede pedir; un documento que no lo cumple se rechaza antes de llamar a nadie (`ValidationError`). | `gateway/src/main/resources/graphql/schema.graphqls` |
| ¿Qué diferencia hay entre query y mutation? | La **query** lee sin efectos (`evento`, `eventos`, `fechasDisponibles`). La **mutation** cambia el estado (`crearEvento`, `registrarPago`, `confirmarEvento`) y, si hay varias, se ejecutan en orden. | [`graphql.md`](graphql.md#queries) |
| ¿Qué responsabilidad tiene un resolver? | Obtener el dato de **un** campo: llama al servicio dueño por REST, reenvía el JWT y traduce el error a GraphQL. Solo se ejecuta si el campo se pidió, y no tiene reglas de negocio. | `@SchemaMapping` / `@BatchMapping` del gateway; demo 1 (query parcial: 2 llamadas) |
| ¿Por qué GraphQL no debería duplicar toda la lógica REST? | Porque habría dos fuentes de verdad que terminan divergiendo (dos reglas del fin de semana), con doble mantenimiento y doble pruebas. Nuestras mutations hacen **una** llamada REST y devuelven lo que el servicio responde, incluidos sus errores. | [`arquitectura.md`](arquitectura.md#el-gateway-graphql); `MutacionesGraphQlTest` |

### Semana 13

| Pregunta | Respuesta | Respaldo |
|---|---|---|
| ¿Cuál es la diferencia entre autenticación y autorización? | **Autenticación**: quién eres (login → JWT, validado en el gateway y en cada servicio). **Autorización**: qué puedes hacer (roles, permisos). Solo hacemos autenticación: hay una sola usuaria y no hay acciones que distinguir; el 403 queda preparado. | [`seguridad.md`](seguridad.md#por-qué-basta-con-autenticación-sin-roles); demo 5 |
| ¿Qué información no debería ir como secreto dentro de un JWT? | Nada secreto ni personal: el payload solo está en Base64 y cualquiera lo lee. Por eso `sub` es un id opaco y no el correo, y el token lleva solo `sub`, `iss`, `iat` y `exp`. El secreto es la clave de firma, que nunca viaja. | [`seguridad.md`](seguridad.md#el-flujo-del-token); [`evidencias-p3e1-graphql.md`](evidencias-p3e1-graphql.md#10a-acceso-denegado-y-permitido-por-graphql) §10.7 |
| ¿CORS reemplaza autenticación? | No. CORS es una regla **del navegador** (Postman y `curl` la ignoran); la protección real es el JWT. Solo el gateway tiene CORS, con orígenes explícitos (`CORS_ALLOWED_ORIGINS`, nunca `*`). | Demo 5 (preflight permitido 200, ajeno 403) |
| ¿Dónde guardar credenciales de infraestructura? | En variables de entorno, desde un `.env` que **no** está en el repositorio. `.env.example` lleva valores de ejemplo, el compose usa `${VAR:?}` y los `application.yml` no tienen valores por defecto. En producción: un gestor de secretos. Rotación con `rotar-password.ps1`. | [`seguridad.md`](seguridad.md#secretos-env-y-envexample); `docker-compose.yml` |
