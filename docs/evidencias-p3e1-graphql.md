# Evidencias P3 · Entrega 1 — GraphQL (guía de capturas)

Guía paso a paso de las capturas del gateway GraphQL: schema, queries, mutations que delegan en REST, resolución por campo,
errores controlados, Docker y pruebas. Cada captura indica **qué hacer** y **qué debe verse**. Explicación técnica en
[`graphql.md`](graphql.md) y [`arquitectura.md`](arquitectura.md).

> **Seguridad (Fase 7):** el gateway valida el token antes de llamar a nadie (salvo `login`), la introspección del schema exige
> token y hay CORS explícito. Sus capturas están en la [sección 10](#10-seguridad); la explicación, en [`seguridad.md`](seguridad.md).
> **Consecuencia para todas las capturas:** en GraphiQL, primero `login` y el token en Headers (paso 0.2), o el panel Docs sale vacío.

## 0. Preparación (una vez)

```powershell
docker compose up -d --build
docker compose ps
```

Esperar a que los 16 contenedores estén arriba (todos `healthy` salvo Kafdrop, que no tiene healthcheck). El `.env` debe tener
`GATEWAY_SPRING_PROFILES=dev` (viene en `.env.example`) para que GraphiQL esté disponible.

Para las capturas de logs, en PowerShell desde la raíz del proyecto: `cls` y después el comando indicado.

### 0.2 GraphiQL con la introspección protegida (hacerlo SIEMPRE al abrir GraphiQL)

Desde la Fase 7 la introspección (lo que GraphiQL usa para el panel **Docs** y el autocompletado) **exige token**. Al abrir
GraphiQL sin token verás el editor normal, pero **Docs vacío** (o un aviso de error del schema) y sin autocompletado. Es lo
esperado. Pasos:

1. Abrir http://localhost:8090/graphiql. La pestaña **Headers** (abajo) debe estar **vacía**.
2. Pegar en el editor y pulsar ▶ (es la única operación que funciona sin token):

   ```graphql
   mutation { login(correo: "<AUTH_ORGANIZER_EMAIL del .env>", password: "<VERIFICACION_PASSWORD del .env>") { token tipoToken expiracionEnSegundos } }
   ```

   La contraseña es la de la variable **`VERIFICACION_PASSWORD`** del `.env` (abrir el `.env` en VS Code; **nunca** dejarla
   visible en una captura). La de ejemplo de `.env.example` ya **no** sirve en este entorno (se rotó en la Fase 7).
3. Copiar el valor de `token` (sin comillas) y, en la pestaña **Headers**, escribir:

   ```json
   { "Authorization": "Bearer <token>" }
   ```

4. Pulsar **Re-fetch GraphQL schema**: el icono ↻ de la barra lateral izquierda de GraphiQL (o `Shift+Ctrl+R`). Ahora sí
   aparecen **Docs** (icono de libro) y el autocompletado.
5. Para cada captura: el documento en el editor, las variables en la pestaña **Variables**, el token ya en **Headers** y ▶.
   Los documentos completos están en [`graphql.md`](graphql.md#ejemplos-para-pegar-en-graphiql).

Si pasan más de 8 h (`AUTH_TOKEN_TTL`), las consultas responden `UNAUTHORIZED` "El token venció…": repetir los pasos 2 a 4.

---

## 1. Schema

Requiere haber hecho el paso 0.2 (login, token en Headers y Re-fetch schema).

| # | Acción | Qué debe verse |
|---|---|---|
| 1.1 | GraphiQL → icono **Docs** (Documentation Explorer) → `Query` | Las queries `evento`, `eventos`, `fechasDisponibles`, `cliente`, `catalogoServiciosAdicionales`, `lugares`, `proveedores`. |
| 1.2 | Docs → `Mutation` | Las 12 mutations: `login`, `crearCliente`, `crearEvento`, `generarCotizacion`, `crearContrato`, `firmarContrato`, `registrarPago`, `confirmarEvento`, `cancelarEvento`, `agregarServicioAdicional`, `asignarLugar`, `asignarProveedor`, cada una con su descripción ("Eventos: PATCH /eventos/{id}/confirmar…"). |
| 1.3 | Docs → `Evento` | Sus campos y las 11 relaciones (`cliente`, `cotizacion`, `contrato`, `pagos`, `balance`, `lugar`, `proveedores`, `serviciosAdicionales`, `logistica`, `notificaciones`, `auditoria`). |
| 1.4 | En VS Code: `gateway/src/main/resources/graphql/schema.graphqls` | Los enums (`TipoEvento`, `ZonaEvento`, `EstadoEvento`, `EstadoContrato`, `EstadoFecha`…), los tipos y los inputs. |

## 2. login y token

| # | Acción | Qué debe verse |
|---|---|---|
| 2.1 | `mutation { login(correo: "<correo de la organizadora>", password: "<VERIFICACION_PASSWORD>") { token tipoToken expiracionEnSegundos } }` (sin Headers) | `tipoToken: "Bearer"`, `expiracionEnSegundos` (28800 = 8 h) y el token. **Recortar o difuminar el token y no mostrar la contraseña.** |
| 2.2 | Pestaña **Headers**: `{ "Authorization": "Bearer <token>" }` | El header configurado (difuminar el token). |
| 2.3 | `login` con una contraseña incorrecta (por ejemplo, la de ejemplo de `.env.example`) | Error `UNAUTHORIZED` con el mensaje de Auth: "Correo o contraseña incorrectos." (`extensions.servicio: auth`, `status: 401`). |

## 3. El flujo completo del negocio solo por GraphQL

Ejecutar en orden (ejemplos 2 a 6 de [`graphql.md`](graphql.md#ejemplos-para-pegar-en-graphiql)), copiando los ids de cada
respuesta en las variables de la siguiente:

| # | Mutation / query | Qué debe verse |
|---|---|---|
| 3.1 | `crearCliente` | El cliente creado con su `id` (nombre con tildes y ñ). |
| 3.2 | `fechasDisponibles(desde, hasta)` | Fechas con `diaSemana` en español y estado `DISPONIBLE`. |
| 3.3 | `crearEvento` (pidiendo también `cliente { nombreCompleto }`) | Evento `PENDIENTE` y, en la misma respuesta, el cliente. |
| 3.4 | `generarCotizacion` | `valorBase`, `totalSinAdicionales`, `depositoInicialRequerido` (30 %) y `moneda`. |
| 3.5 | `crearContrato` | Contrato `PENDIENTE` con el valor y la foto del cliente. |
| 3.6 | `confirmarEvento` **antes de firmar** | Error `CONFLICT`: "El contrato del evento está en estado PENDIENTE; debe estar FIRMADO para confirmar el evento." (ver 6.3). |
| 3.7 | `firmarContrato` | `FIRMADO` con `fechaFirma`. |
| 3.8 | `registrarPago` (monto = depósito, con `claveIdempotencia`) | El pago con `id`, `monto`, `fecha`. Repetirla: **mismo `id`** (no se cobra dos veces). |
| 3.9 | `confirmarEvento` | `RESERVADO`. |
| 3.10 | `lugares(estado: ACTIVO)` + `asignarLugar` | El evento con `lugar { nombre capacidad }`. |
| 3.11 | `proveedores(activo: true)` + `asignarProveedor` | La asignación con `servicio`, `categoria`, `costoAcordado` y `estado: PENDIENTE`. |
| 3.12 | `catalogoServiciosAdicionales` + `agregarServicioAdicional(codigo: "decoracion-premium")` | El adicional con su `subtotal`. |
| 3.13 | **Log del gateway:** `docker compose logs gateway --since 10m \| Select-String "REST -> "` | **Una línea por mutation**, cada una con el servicio, el método y la ruta REST a la que delegó (`REST -> finanzas POST /api/v1/finanzas/pagos 201 (… ms)`, `REST -> eventos PATCH /api/v1/eventos/…/confirmar 200 …`). |

## 4. La ficha completa del evento en UNA query

| # | Acción | Qué debe verse |
|---|---|---|
| 4.1 | Query **Ficha** (ejemplo 7 de `graphql.md`) con el `id` del evento | En una sola respuesta: cliente, cotización (`total` = cotización + adicional), contrato FIRMADO, pagos, balance (`montoAbonado`, `saldoPendiente`), lugar, proveedores, servicios adicionales, logística (meseros, cocina, vehículos, compras), **notificaciones** (abono y confirmación, `GENERADA`) y **auditoría** (`PAGO_REGISTRADO`, `EVENTO_CONFIRMADO`, `SERVICIO_ADICIONAL_AGREGADO` con el `payload`). Logística, notificaciones y auditoría las generó **Kafka** después de confirmar. |
| 4.2 | Hacer scroll en la respuesta para la segunda captura | Logística, notificaciones (asunto "Evento confirmado: …") y auditoría. |
| 4.3 | `docker compose logs gateway --since 2m \| Select-String "REST -> "` justo después de 4.1 | Las llamadas a los 9 servicios de los campos pedidos (eventos, clientes, finanzas, contratos, lugares, proveedores, logística, notificaciones, auditoría). |

## 5. Solo se llama a los servicios de los campos pedidos

| # | Acción | Qué debe verse |
|---|---|---|
| 5.1 | `cls`; en GraphiQL: `query($id: ID!) { evento(id: $id) { nombreEvento cliente { nombreCompleto } } }` | Solo el nombre del evento y del cliente. |
| 5.2 | `docker compose logs gateway --since 1m \| Select-String "REST -> "` | **Exactamente 2 líneas**: `REST -> eventos GET /api/v1/eventos/<id> 200` y `REST -> clientes GET /api/v1/clientes/<id> 200`. **Ninguna** a finanzas, contratos ni a los demás. |
| 5.3 | Listado: `{ eventos(estado: PENDIENTE, size: 3, sort: "fechaCreacion,desc") { contenido { nombreEvento cliente { nombreCompleto } } } }` justo después de crear 3 eventos del mismo cliente | `@BatchMapping`: en el log, 1 llamada a eventos y **1 sola** a clientes para los 3 eventos (ver la sección N+1 de `graphql.md`). |

## 6. Errores controlados

| # | Acción | Qué debe verse |
|---|---|---|
| 6.1 | `crearCliente` con `correo: "valeria@@ejemplo"`, `telefono: "12"`, `nombreCompleto: "X"` | `classification: BAD_REQUEST`, `status: 400`, `servicio: clientes` y `extensions.errores` con **un elemento por campo** (`correo`, `telefono`, `nombreCompleto`) y su mensaje en español. |
| 6.2 | `registrarPago` con `monto: 0` | `BAD_REQUEST` con `errores: [{ campo: "monto", mensaje: "debe ser mayor que 0" }]`. |
| 6.3 | `confirmarEvento` sin contrato firmado (3.6) | `CONFLICT`, `status: 409`, mensaje en español de Eventos, `path: ["confirmarEvento"]`, **sin stack trace**. |
| 6.4 | `crearEvento` en la misma fecha de 3.3 | `CONFLICT`: "La fecha … no está disponible (estado PENDIENTE). Elige otra fecha." |
| 6.5 | `evento(id: "00000000-0000-0000-0000-000000000000")` | `NOT_FOUND`, `status: 404`: "No existe un evento con id …". |
| 6.6 | `cliente(id: "no-es-uuid")` | `BAD_REQUEST` ("Algún argumento no tiene el formato esperado…"). |
| 6.7 | Borrar el header Authorization y repetir una query | `UNAUTHORIZED` (`status: 401`, `servicio: gateway`) con "Falta el token. Inicia sesión con la mutation login y envía el header Authorization: Bearer &lt;token&gt;.". Desde la Fase 7 lo rechaza el **gateway** sin llamar a ningún servicio (ver 10.1 y 10.2). |
| 6.8 | `docker compose stop contratos-service` → query Ficha con `estado contrato { estado } balance { saldoPendiente }` → `docker compose start contratos-service` | `SERVICE_UNAVAILABLE` (503) solo en `path: ["evento","contrato"]` con "El servicio de contratos no está disponible…", y **el resto de la ficha se entrega** (`estado`, `balance`). Repetir `firmarContrato` con el servicio detenido: SERVICE_UNAVAILABLE en la mutation. |
| 6.9 | `{ evento { nombreEvento } }` (sin el argumento `id`) | `ValidationError` de graphql-java (el documento no cumple el schema; no se llama a ningún servicio). |

## 7. GraphiQL solo en el perfil dev

| # | Acción | Qué debe verse |
|---|---|---|
| 7.1 | `$env:GATEWAY_SPRING_PROFILES='sin-graphiql'; docker compose up -d gateway` → esperar healthy → abrir http://localhost:8090/graphiql | Página de error 404 (GraphiQL apagado). `/graphql` sigue respondiendo. |
| 7.2 | `Remove-Item Env:\GATEWAY_SPRING_PROFILES; docker compose up -d gateway` | Vuelve el perfil `dev` del `.env` y GraphiQL responde de nuevo. |

## 8. Docker Compose

| # | Acción | Qué debe verse |
|---|---|---|
| 8.1 | `docker compose ps` | 16 contenedores, entre ellos `gateway` (8090) y `frontend` (5173) `healthy`. |
| 8.2 | VS Code: bloque `gateway` de `docker-compose.yml` | `build: ./gateway`, puerto 8090, `mem_limit` y `-Xmx256m` (de `servicio-base`), `SPRING_PROFILES_ACTIVE: ${GATEWAY_SPRING_PROFILES:-}`, `AUTH_JWT_SECRET` y `CORS_ALLOWED_ORIGINS` con `${…:?}` (Fase 7), las URLs de los 10 servicios, healthcheck; **sin** base de datos, Redis ni Kafka. |
| 8.3 | `docker stats --no-stream` | El gateway ≈ 200 MiB / 512 MiB; total ≈ 4,9 GiB (cabe en los 8 GB de WSL). |

## 9. Pruebas

| # | Acción | Qué debe verse |
|---|---|---|
| 9.1 | `cd gateway; .\gradlew.bat clean test --no-daemon; cd ..` | `BUILD SUCCESSFUL`, 107 pruebas. |
| 9.2 | Abrir `gateway\build\reports\tests\test\index.html` | 100 %: `ConsultasGraphQlTest` (ficha completa, query parcial, 404, 503, 400 con `errores[]`, batch), `MutacionesGraphQlTest` (una mutation que delega, 409 con su mensaje, 400 por campo, login), `ClienteRemotoTest`, `RestClientConfigTest`, `TokenInterceptorTest` y `SeguridadGatewayTest` (ver 10.13). |
| 9.3 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_6B.ps1` | Las 12 secciones en verde y `Verificación 6B: 48 comprobaciones, 0 fallos` (el flujo completo 100 % por GraphQL). |
| 9.4 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_6A.ps1` | `Verificación 6A: 66 comprobaciones, 0 fallos`. |

## 10. Seguridad

Explicación y tabla de errores en [`seguridad.md`](seguridad.md). Para los comandos `curl.exe` (incluido en Windows 11), obtener
antes un token en la misma ventana de PowerShell, desde la raíz del proyecto (no muestra la contraseña):

```powershell
. .\scripts\verificacion\credenciales.ps1
$t = (Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes((CuerpoLogin)))).token
```

### 10.A Acceso denegado y permitido por GraphQL

| # | Acción | Qué debe verse |
|---|---|---|
| 10.1 | `cls`; GraphiQL con la pestaña **Headers vacía**: `{ eventos(size: 3) { totalElementos } }` | `errors[0]`: `UNAUTHORIZED`, `status: 401`, `servicio: "gateway"`, "Falta el token. Inicia sesión con la mutation login y envía el header Authorization: Bearer &lt;token&gt;."; **sin `data`**. |
| 10.2 | Justo después: `docker compose logs gateway --since 1m \| Select-String "REST -> \|rechazado"` | Solo el WARN `GraphQL rechazado en el gateway: sin token (operación sin nombre); no se llamó a ningún servicio` y **ninguna línea `REST ->`**: el gateway no llamó a ningún servicio. |
| 10.3 | Con el token en Headers, la misma query; después el comando de 10.2 | Los datos, y en el log **una** línea `REST -> eventos GET /api/v1/eventos?page=0&size=3 200`: con token sí delega (y eventos-service vuelve a validar el token). |
| 10.4 | En Headers, **cambiar la última letra** del token y repetir | `UNAUTHORIZED` "El token no es válido. Inicia sesión de nuevo con la mutation login." (firma que no coincide), sin llamadas REST. |
| 10.5 | Headers vacíos: `mutation { login(correo: "…", password: "…") { tipoToken } crearCliente(input: { nombreCompleto: "X Y", telefono: "3001234567", correo: "x@y.co" }) { id } }` | `UNAUTHORIZED`: solo la mutation `login` **sola** pasa sin token. |
| 10.6 | Token vencido, por configuración (vigencia de 15 s): `$env:AUTH_TOKEN_TTL='PT15S'; docker compose up -d auth-service` → esperar `healthy` (`docker compose ps auth-service`) → `login` en GraphiQL (`expiracionEnSegundos: 15`) → poner ese token en Headers → **esperar 20 s** → `{ eventos(size: 1) { totalElementos } }` | `UNAUTHORIZED` "El token venció. Inicia sesión de nuevo con la mutation login.". **Al terminar:** `Remove-Item Env:\AUTH_TOKEN_TTL; docker compose up -d auth-service` (vuelven las 8 h). |
| 10.7 | Decodificar el token (no pegarlo en páginas externas): `$p=$t.Split('.')[1].Replace('-','+').Replace('_','/'); while($p.Length%4){$p+='='}; [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p))` | `{"sub":"3f6c1b9e-…","iss":"maxi-eventos-auth","iat":…,"exp":…}`: un **id opaco**, sin el correo ni datos personales; `exp − iat` = 28800. |

### 10.B Acceso denegado y permitido por REST (defensa en profundidad)

| # | Acción | Qué debe verse |
|---|---|---|
| 10.8 | `curl.exe -s -i http://localhost:8082/api/v1/clientes` | `HTTP/1.1 401` y el cuerpo `ErrorResponse`: `"status":401,"error":"Unauthorized","message":"Debes iniciar sesión para realizar esta operación. Envía el header Authorization: Bearer <token>.","path":"/api/v1/clientes"`. El servicio exige token aunque la llamada no pase por el gateway. |
| 10.9 | `curl.exe -s -i -H "Authorization: Bearer $t" "http://localhost:8082/api/v1/clientes?size=1"` | `HTTP/1.1 200` y la página de clientes. |
| 10.10 | `curl.exe -s -i -H "Authorization: Bearer $($t.Substring(0, $t.Length-2))xx" http://localhost:8081/api/v1/eventos` | `401` con `"message":"El token no es válido. Inicia sesión de nuevo."`. |
| 10.11 | `curl.exe -s -i http://localhost:8085/api/v1/eventos//contrato` | `HTTP/1.1 400` con `ErrorResponse`: "La ruta no es válida: falta un identificador o contiene caracteres no permitidos." (antes de la Fase 7: 401 sin cuerpo). |
| 10.12 | `curl.exe -s -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{\"correo\":\"x@y.co\",\"password\":\"mala\"}'` | `401` con `ErrorResponse` y `"message":"Correo o contraseña incorrectos."`, `"path":"/api/v1/auth/login"`. |

### 10.C CORS

| # | Acción | Qué debe verse |
|---|---|---|
| 10.13 | `curl.exe -s -i -X OPTIONS http://localhost:8090/graphql -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization,content-type"` | `HTTP/1.1 200` con `Access-Control-Allow-Origin: http://localhost:5173`, `Access-Control-Allow-Methods: POST,OPTIONS`, `Access-Control-Allow-Headers: authorization, content-type`, `Access-Control-Max-Age: 3600` y **sin** `Access-Control-Allow-Credentials`. |
| 10.14 | El mismo comando con `-H "Origin: http://sitio-ajeno.example"` | `HTTP/1.1 403` con el texto `Invalid CORS request` y **sin** `Access-Control-Allow-Origin`: el navegador bloquearía la llamada. |
| 10.15 | VS Code: `.env.example` (sección del gateway) y `gateway/src/main/java/com/maxieventos/gateway/security/SecurityConfig.java` (`corsConfigurationSource`) | `CORS_ALLOWED_ORIGINS=http://localhost:5173` y la comprobación que impide `*`. **No capturar el `.env`.** |

### 10.D Actuator y lo que es público

| # | Acción | Qué debe verse |
|---|---|---|
| 10.16 | `curl.exe -s -i http://localhost:8090/actuator/health` y `curl.exe -s -i http://localhost:8080/actuator/health` | `200` `{"status":"UP"…}` sin token (gateway y auth-service). |
| 10.17 | `curl.exe -s -i http://localhost:8090/actuator/metrics` y `curl.exe -s -i http://localhost:8080/actuator/info` | `401` con cuerpo JSON "Debes iniciar sesión…": solo health es público (también en auth-service). |
| 10.18 | `curl.exe -s -i -H "Authorization: Bearer $t" http://localhost:8090/actuator/metrics` | `200` con la lista de métricas. |
| 10.19 | Navegador: http://localhost:8080/swagger-ui/index.html | Swagger de auth-service sin token (la documentación es pública; ver `seguridad.md`). |

### 10.E Pruebas y verificación

| # | Acción | Qué debe verse |
|---|---|---|
| 10.20 | `gateway\build\reports\tests\test\index.html` (tras 9.1) → `SeguridadGatewayTest` | 14 pruebas en verde: sin token, vencido, mal firmado, manipulado, otro emisor, `Basic`, login permitido, login + otra mutation, introspección, preflight permitido y no permitido, CORS en el POST real, `*` rechazado, actuator. |
| 10.21 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_7A.ps1` | `verificar_7A: 41 comprobaciones, 0 fallos` (seguridad del gateway, incluido el token vencido por configuración). |
| 10.22 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_7B.ps1` | `verificar_7B: 125 comprobaciones, 0 fallos` (los 10 servicios: 401 con `ErrorResponse`, `//` → 400, actuator, Swagger, token vencido; contraseña rotada). |

---

### Notas

- Los valores (ids, fechas, lugares y proveedores elegidos) cambian en cada ejecución: cada flujo crea datos nuevos. Desde la
  Fase 9 hay datos de demostración (`scripts\verificacion\cargar-demo.ps1`): para la ficha completa (sección 4) se puede usar
  el evento "Quince años de Lucía", que ya tiene todo (contrato firmado, abonos, logística, adicional, proveedores,
  notificaciones y auditoría); su id lo da `{ eventos(estado: RESERVADO, size: 10, sort: "fechaCreacion,asc") { contenido { id nombreEvento fecha } } }`
  (el **primero** con ese nombre; los scripts de verificación crean los suyos con el prefijo "Prueba").
  En Postman, la carpeta **12 GraphQL (gateway)** tiene la ficha, la mutation, los errores y el acceso sin token.
- Índice de todas las guías: [`evidencias.md`](evidencias.md).
- Nunca dejar visible en una captura el token completo ni la contraseña.
