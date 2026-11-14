# Scripts de verificación

Scripts de PowerShell 5.1 que prueban en Docker los casos de cada fase (`docker compose up -d` antes de ejecutarlos).
En la Fase 9 servirán como base para las evidencias (`docs/evidencias.md`) y para una prueba de regresión completa.

| Script | Qué verifica |
|---|---|
| `probar-2a.ps1` | Fase 2A: formato de error, 400/404/405/409 (histórico; algunos casos cambiaron después). |
| `probar-2b.ps1` | Fase 2B: 201 + `Location`, paginación, caché Redis (histórico). |
| `probar-3.ps1` | Fase 3: flujo completo de eventos (histórico). |
| `verificar_4A.ps1` | Subfase 4A: Auth, Lugares, Proveedores, Logística y el flujo de la Fase 3. |
| `verificar_4B.ps1` | Subfase 4B: cotización, validación de pagos, idempotencia y concurrencia, Kafka tras el commit, 503, flujo de la Fase 3. Detiene y arranca eventos-service y kafka. |
| `verificar_4C.ps1` | Subfase 4C: contrato con la foto de los datos, PDF del contrato y del anexo (texto con tildes y ñ), firma doble, 503, flujo de la Fase 3. Detiene y arranca finanzas-service. |
| `verificar_5A1.ps1` | Subfase 5A1: sobre común en los 3 tópicos (Kafka y Kafdrop), clave = eventoId, consumidores migrados, adicional con Kafka caído → 201, persistencia de Kafka (**hace `docker compose down` y `up`, sin `-v`**). |
| `verificar_5A2.ps1` | Subfase 5A2: outbox. Kafka detenido 120 s: pago, adicional y confirmación con éxito, filas sin publicar, al volver Kafka se publican solas (mismo idEvento, PUBLICADO, Kafdrop), Logística procesa una vez. |
| `verificar_5B.ps1` | Subfase 5B (evidencias P2·E2): recálculo por adicional (BD y GET sin caché), duplicado ignorado, inválidos e ilegible al DLT sin reintentos (cabecera del grupo, Kafdrop), BD caída → reintentos limitados y DLT, adicional antes del contrato, anexo con Finanzas detenido. **Detiene y arranca postgres-db y finanzas-service.** |
| `verificar_5C.ps1` | Subfase 5C: auditoria-service y notificaciones-service. Pago → 1 auditoría y 1 notificación; confirmación → 1 y 1; adicional → solo auditoría; duplicados ignorados por los dos (BD sin cambios); inválido e ilegible en `pagos.registrados.DLT` con un intento; filtros, orden y paginación de los dos GET; 400 y 401; health y Swagger públicos. |
| `verificar_5C_historial.ps1` | Primer despliegue de los grupos nuevos (`earliest`): con `-Antes` guarda la foto de los DLT **antes** de crear los servicios; sin parámetros clasifica el historial de los tópicos como cada consumidor y comprueba filas, payload original, duplicados, inválidos al DLT una vez por grupo, que los DLT antiguos no se reprocesan y LAG 0. |
| `verificar_6A.ps1` | Subfase 6A (gateway GraphQL): GraphiQL con `dev` y apagado sin él, la ficha completa en una query, query parcial que solo llama a eventos y clientes (log `REST -> ` del gateway), `@BatchMapping` (3 eventos del mismo cliente → 1 llamada), filtros, NOT_FOUND, BAD_REQUEST con `errores[]`, UNAUTHORIZED y SERVICE_UNAVAILABLE con finanzas detenido. **Datos por REST; reinicia el gateway con otro perfil y detiene finanzas-service.** |
| `verificar_6B.ps1` | Subfase 6B: el flujo completo **100 % por GraphQL** (login → cliente → fechas → evento → cotización → contrato → firma → pago idempotente → confirmación → lugar, proveedor y adicional → ficha con lo generado por Kafka → cancelación), errores BAD_REQUEST (por campo), CONFLICT, NOT_FOUND, UNAUTHORIZED y SERVICE_UNAVAILABLE. **Detiene y arranca contratos-service.** Requiere un lugar ACTIVO con capacidad ≥ 105 y un proveedor activo en el catálogo. |
| `verificar_7A.ps1` | Subfase 7A (seguridad del gateway): claims del JWT (`sub` = id opaco, `iss`, sin correo), login sin token, UNAUTHORIZED en el gateway **sin ninguna línea `REST ->`** (sin token, manipulado, firma alterada, no JWT, Basic, login + otra mutation), introspección con token, CORS (preflight y POST desde un origen permitido y uno ajeno), actuator del gateway (solo health público), ruta con `//` → 400, **token vencido** (recrea auth-service con `AUTH_TOKEN_TTL=PT15S` y lo devuelve a la normalidad). |
| `verificar_7B.ps1` | Subfase 7B (seguridad de los 10 servicios): la contraseña de ejemplo de `.env.example` → 401 "Correo o contraseña incorrectos." y la del `.env` → 200; auth-service con Spring Security (login, health y Swagger sin token); en los 10: health público, info/metrics 401 con `ErrorResponse` y 200 con token, `/v3/api-docs` público, 401 con `ErrorResponse` (sin token, manipulado, **sin `iss`** = emitido antes de la 7A, otro emisor), `//` → 400; el token sin `iss` también por GraphQL; **token vencido** en los 10 (recrea auth-service con `AUTH_TOKEN_TTL=PT15S` y lo devuelve). |
| `cargar-demo.ps1` | Fase 9A, ampliado el 2026-11-03 para la sustentación (130 comprobaciones). Carga los **datos de demostración** solo por la API: 9 clientes con tildes y ñ, 5 lugares (uno INACTIVO), 6 proveedores (uno inactivo) y **9 eventos con fechas fijas**, uno por etapa. Son los 3 de siempre (Quince años de Lucía RESERVADO completo, Boda de Andrés y Camila PENDIENTE cotizada, Quince años de María José CANCELADO) y 6 más: pagado por completo, contrato sin firmar (409), abono parcial, adicionales + proveedores + compras con ítems manuales, recién creado, y cotizado con lugar. Espera hasta 6 minutos a Logística, Notificaciones y Auditoría, e imprime los ids y la tabla "qué evento usar para mostrar cada cosa". Se detiene sin cargar nada si ya hay datos, o si una fecha fija ya pasó o está ocupada. **Después de cargarlo, para la sustentación, no ejecutar Postman ni los demás scripts.** |
| `verificar_9A.ps1` | Subfase 9A: con Redis detenido los 8 servicios siguen con health 200 UP y las consultas de los 4 con caché responden desde la BD con el WARN de caché; "Caché HIT" en clientes, lugares, proveedores y logística; proveedor repetido para el mismo evento y servicio → 409 con mensaje claro. No crea datos (usa los de `cargar-demo.ps1`). **Detiene y arranca redis.** |
| `verificar_10.ps1` | Fase 10C (interfaz en Docker; **80 comprobaciones** desde la 11I, que agregó la sección 6: `FRONTEND_FECHA_SIMULADA` al arrancar —modo demostración— y tres valores inválidos que no arrancan; el calendario busca días libres en el mes actual o en los 2 siguientes): contenedor `frontend` (healthy, 64 MB, solo lectura, `no-new-privileges`, nginx 1.28.0 con 2 procesos, fuera de la red de los servicios); CSP exacta y cabeceras de seguridad; `/config.js` con `FRONTEND_GATEWAY_URL`; archivos del build (gzip, caché), 404 de un archivo inexistente, rutas de la aplicación → `index.html`, sin source maps ni secretos del `.env` en el JS; CORS desde `:5173` (200) y otro origen (403); **recorrido real en Edge o Chrome sin ventana** (`navegador_10.mjs`: login, ficha de la demostración, calendario, la CSP bloquea otro sitio y un script inyectado, cerrar sesión); `FRONTEND_GATEWAY_URL` cambiada **al arrancar** sin reconstruir y un valor inválido que impide arrancar. Solo lee la demostración. **Recrea el contenedor frontend ocho veces** (y lo deja con los valores del `.env`). |
| `navegador_10.mjs` | Lo usa `verificar_10.ps1`: maneja Edge o Chrome sin ventana por el protocolo de DevTools con el `WebSocket` de Node 22 (sin dependencias). Credenciales por variables del proceso; nunca se imprimen. Fase 11F: recorre también el paso 7 "Resumen" de la ficha (6 tarjetas, "Paso 7 de 7", sin tablas, sin desplazamiento horizontal y en una sola columna a 360 px, y el enlace "Ir a Abonos" que lleva el foco al paso). Con `MAXI_CAPTURAS=<carpeta>` guarda capturas del resumen a 1280 y 360 px. |
| `comparar-openapi.ps1` | Subfase 9B: valida los 10 contratos con `ValidarOpenApi.java` y compara cada uno con el `/v3/api-docs` del servicio en marcha (`CompararOpenApi.java`). Clasifica las diferencias en **REALES** (rutas, métodos, parámetros, campos y tipos de los DTO), **REGLAS** (401, 400, 500, `bearerAuth`, `ErrorResponse` y ejemplos que el contrato debe tener) y **COSMÉTICAS** (solo se informan). Sale con 1 si hay reales o reglas. `-Servicios eventos,finanzas` para comparar solo algunos y `-Detalle` para ver también las cosméticas. |
| `ejecutar-postman.ps1` | Subfase 9C: ejecuta la colección de `postman/` con `npx newman@6.2.1` (reporters cli y junit en `postman/resultados/`). La contraseña va en una copia temporal del environment que se borra; revisa que los resultados no contengan la contraseña ni un token. Ver `postman/README.md`. |
| `CompararOpenApi.java` | Comparador que usa `comparar-openapi.ps1` (SnakeYAML de la caché de Gradle). UTF-8 **sin** BOM. |
| `ejecutar-pruebas.ps1` | Fase 8: las pruebas automáticas (`gradlew.bat clean test --no-daemon`) de los 10 servicios y el gateway, uno por uno, con un resumen por módulo (pruebas, fallos, **saltadas**) y el total; avisa si se saltaron pruebas porque Docker no está en marcha. No necesita el entorno de Docker Compose ni el `.env`. Ver "Pruebas automáticas". |
| `rotar-password.ps1` | Rota la contraseña de la organizadora en el `.env` local sin mostrarla: contraseña aleatoria de 24 caracteres → `VERIFICACION_PASSWORD` y su hash BCrypt → `AUTH_ORGANIZER_PASSWORD_HASH`. **Fase 11D: `-Usuario asistente`** hace lo mismo para el ASISTENTE (`VERIFICACION_ASISTENTE_PASSWORD` y `AUTH_ASISTENTE_PASSWORD_HASH`; si faltan, agrega `AUTH_ASISTENTE_EMAIL`, `_ID` y `_NOMBRE`). Después: `docker compose up -d auth-service`. |
| `verificar_11D.ps1` | Fase 11D (roles), 74 comprobaciones: login de los dos usuarios (claim `rol`, nombre) y un token **sin rol** firmado en el script; **nivel 1, directo contra cada servicio**: el asistente y el token sin rol reciben 403 con `ErrorResponse` en las 11 acciones de administración (crear / editar / desactivar / borrar lugares y proveedores, bloquear una fecha, la auditoría, borrar un cliente) y la entidad no cambia, y **el administrador hace la MISMA acción sobre la MISMA entidad** con éxito; **nivel 2, GraphQL**: las 6 mutations de administración → FORBIDDEN del gateway (el log muestra "Acceso denegado" y ninguna llamada a Lugares ni Proveedores con esa referencia) y el administrador sí; la auditoría de la ficha; lo que el asistente sí puede. Crea solo "Prueba 11D …" y lo borra. |
| `verificar_11H.ps1` | Fase 11H (campanita), 47 comprobaciones: las dos usuarias en 0; un evento "Prueba …" RESERVADO en los próximos 7 días; **recorrido real en Edge sin ventana** (`navegador_11H.mjs`) como administradora: campanita en 0 → "Ejecutar recordatorios" → sube **sin recargar la página**; el panel con el recordatorio "Nueva", su evento y la hora de hoy en la zona de `/config.js`; dentro de la pantalla y encima del contenido a 1280 y 360 px (`elementFromPoint`); Escape y foco; **polling** (un abono por REST la sube sola en ≤ 40 s); clic → leída y ficha. Después, por REST y GraphQL: el recordatorio leído para la administradora y **sin leer para la asistente**, 404 y una sola fila de lectura. Con `MAXI_CAPTURAS=<carpeta>` guarda capturas del panel. **Ejecutarlo antes de `cargar-demo.ps1`** (ejecuta los recordatorios). |
| `navegador_11H.mjs` | Lo usa `verificar_11H.ps1` (mismo método que `navegador_10.mjs`, sin dependencias; credenciales por variables del proceso, nunca se imprimen). |
| `verificar_11E.ps1` | Fase 11E (recordatorios EVENTO_PROXIMO), 24 comprobaciones: el broker sin creación automática de tópicos y los tópicos (con `eventos.proximos` y su DLT) con 3 particiones; un evento "Prueba …" RESERVADO en un fin de semana libre de los próximos 7 días (si no hay, usa el primer RESERVADO que ya exista en esa ventana); el asistente → 403 en Eventos y FORBIDDEN en el gateway, sin escribir nada; el administrador → ENVIADO con el umbral más pequeño que aplica, fila en `eventos_recordatorios_enviados`, outbox PUBLICADO con el mismo idEvento, notificación "Faltan N días…" y auditoría en `eventos.proximos`; la segunda ejecución (GraphQL) → YA_ENVIADO sin nada nuevo. **Ejecutarlo antes de `cargar-demo.ps1`**: ejecutar los recordatorios también los envía a los eventos de demostración de los próximos 7 días. |
| `HashBcrypt.java` | Hash BCrypt de una contraseña leída por la entrada estándar (lo usa `rotar-password.ps1`; spring-security-crypto de la caché de Gradle). UTF-8 **sin** BOM. |
| `comun.ps1` | Utilidades de los scripts nuevos (`Api`, `ApiEnParalelo`, `Check`, `Login`, `FlujoFase3`, `DescargarPdf`, `Redis`, `Psql`, `Compose`...). |
| `TextoPdf.java` | Extrae el texto de un PDF a un archivo UTF-8 (lo usa `DescargarPdf`; OpenPDF de la caché de Gradle). |
| `credenciales.ps1` | Lee el correo y la contraseña de la organizadora (ver abajo). |
| `ValidarOpenApi.java` | Valida los contratos de `openapi/`: UTF-8, YAML y `$ref` internas. |

## Regresión

Ejecutar en orden `probar-3.ps1`, `verificar_4A.ps1`, `verificar_4B.ps1`, `verificar_4C.ps1` (cierre de la Fase 4: 294
comprobaciones, 0 fallos) y después `verificar_5A1.ps1`, `verificar_5A2.ps1`, `verificar_5B.ps1` y `verificar_5C.ps1`
(cierre de la Fase 5: 525 comprobaciones, 0 fallos; ver PROGRESO.md). Cada ejecución crea datos nuevos y consume fechas libres entre semana de los próximos 90 días
(y `probar-3` un fin de semana): con la base de desarrollo actual quedan para varias ejecuciones; la Fase 9 prepara datos limpios.
`probar-2a.ps1` y `probar-2b.ps1` son históricos (algunos de sus casos cambiaron a propósito en fases posteriores).

Los datos que crean los scripts llevan el prefijo **"Prueba"** en los nombres que podrían coincidir con los datos de
demostración de `cargar-demo.ps1` (`FlujoFase3` crea "Prueba Quince años de Lucía" con el cliente "Prueba Lucía Fernández
Ospina", definidos en `$NombreEventoFlujo` y `$NombreClienteFlujo` de `comun.ps1`; Fase 9E). Así la colección de Postman y las
guías de evidencias siempre encuentran la demostración.

**Regla (desde el 2026-11-10): TODO dato que crean los scripts o Postman lleva el prefijo "Prueba"** (eventos, clientes,
lugares y proveedores), no solo los que podrían coincidir con la demostración: la interfaz oculta lo que empieza por
"Prueba" (casilla "Mostrar eventos de prueba"), así que un script ejecutado sobre la demostración no la ensucia a la vista.
`CrearCliente` (en `comun.ps1`) detiene el script si el nombre no empieza por "Prueba ". Solo `cargar-demo.ps1` queda fuera
(sus datos son la demostración). Los únicos nombres sin prefijo que quedan son los de peticiones que la API **rechaza**
(400 / 404 / 409 / 401: no crean nada), p. ej. "X" o el nombre del contrato que se ignora en 4C.

Cada script empieza con `AsegurarEntornoArrancado` (arranca lo que esté detenido y espera a que esté healthy) y vuelve a
arrancar en un `finally` lo que detiene: una ejecución interrumpida no deja el entorno a medias para la siguiente.
Las fechas libres se buscan en los próximos 366 días (el máximo de la API).

## Pruebas automáticas (Fase 8)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-pruebas.ps1                     # los 11 módulos
powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-pruebas.ps1 -Modulos eventos,finanzas
```

- **Docker Desktop debe estar en marcha** para las pruebas de integración (Testcontainers con PostgreSQL `postgres:15-alpine` y
  Redis `redis:7-alpine`, en eventos, finanzas, logística y clientes). Sin Docker **se saltan** (no fallan) y el script lo avisa
  al final: las saltadas **no cuentan como aprobadas**.
- Testcontainers está fijado en **1.21.4** en esos `build.gradle`: el 1.20.6 de Spring Boot 3.4.5 no funciona con Docker Engine 29.
- Logs completos de Gradle en `%TEMP%\maxi_pruebas\<módulo>.log`. Código de salida 1 si algún módulo falla o no compila.
- Los módulos se ejecutan uno por uno (en paralelo la JVM se ha caído en esta máquina): la regresión completa tarda unos 5 minutos (326 pruebas al cerrar la Fase 8).

## Credenciales

Los scripts **nunca** llevan credenciales escritas:

- el correo sale de `AUTH_ORGANIZER_EMAIL` del `.env` de la raíz;
- la contraseña sale de `VERIFICACION_PASSWORD` del mismo `.env`; si está vacía o no existe, se pide con `Read-Host` (sin mostrarla).

La clave de Redis y el usuario de PostgreSQL se usan dentro de los contenedores (variables de entorno del compose), no en el script.

## Uso

```powershell
docker compose up -d
powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_4A.ps1
```

Validar los contratos OpenAPI y compararlos con el código en marcha (Fase 9B; incluye `ValidarOpenApi`):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verificacion\comparar-openapi.ps1
```

Solo validar UTF-8, YAML y `$ref` (sin el entorno levantado), con el jar de SnakeYAML de la caché de Gradle:

```powershell
$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { "$env:USERPROFILE\.gradle" }
$jar = (Get-ChildItem "$gradleHome\caches\modules-2\files-2.1\org.yaml\snakeyaml" -Recurse -Filter 'snakeyaml-*.jar' |
        Where-Object Name -notmatch 'sources|javadoc' | Select-Object -First 1).FullName
java -cp $jar scripts\verificacion\ValidarOpenApi.java (Get-ChildItem openapi\*.yaml).FullName
```

(Hasta la Fase 9 el filtro era solo `sources`: en la caché también está `snakeyaml-2.3-javadoc.jar`, y si se elige ese la
compilación falla con "package org.yaml.snakeyaml does not exist". En la 9E falló por lo mismo `rotar-password.ps1`, que tomó
`spring-security-crypto-6.4.5-javadoc.jar` ("No se pudo calcular el hash BCrypt"); desde entonces **todos** los filtros de JAR
de los scripts —`comparar-openapi.ps1`, `rotar-password.ps1` y `DescargarPdf` de `comun.ps1`— excluyen `sources|javadoc`.)

## Codificación

Los `.ps1` se guardan en **UTF-8 con BOM**: sin BOM, PowerShell 5.1 los lee como ANSI y las tildes de los datos de prueba se corrompen
(lección de la Fase 3). Los `.java` (`ValidarOpenApi`, `CompararOpenApi`, `HashBcrypt`, `TextoPdf`) van en UTF-8 **sin** BOM
(javac no acepta el BOM).
