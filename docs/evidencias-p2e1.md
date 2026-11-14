# Evidencias P2 · Entrega 1 — API REST (guía de capturas)

Guía paso a paso de las capturas de la primera entrega del segundo parcial (semanas 7 y 8): API REST funcional,
persistencia, validaciones y errores, OpenAPI, pruebas, Docker Compose, el reto de la semana 8 y la caché Redis. Cada captura
indica **qué hacer** y **qué debe verse**. Índice de todas las guías: [`evidencias.md`](evidencias.md).

> Comandos en **PowerShell 5.1 desde la raíz del proyecto** (`maxi_eventos\`). **Ningún secreto en las capturas:** no mostrar
> el `.env`, la pestaña de valores del environment de Postman ni el token completo (recortarlo), y **no capturar
> `docker compose config`** (imprime los valores del `.env` ya sustituidos). Antes de cada captura de consola, `cls`.

## 0. Preparación (una vez)

```powershell
docker compose up -d --build
docker compose ps                      # 16 contenedores, todos healthy (Kafdrop no tiene healthcheck)
```

Si la base es nueva (primera vez o después de un reinicio), cargar los datos de demostración **una sola vez**:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verificacion\cargar-demo.ps1
```

En la misma ventana: utilidades, login (no muestra la contraseña) y el id del evento de demostración:

```powershell
. .\scripts\verificacion\comun.ps1
Login | Select-Object Status                                       # 200
$demo = @((Api GET "$($Url.eventos)/eventos?estado=RESERVADO&size=100&sort=fechaCreacion,asc").Body.contenido |
          Where-Object nombreEvento -like 'Quince a*os de Luc*a')[0]
$eid = $demo.id; "evento de demostración: $eid ($($demo.fecha))"     # la demostración: 2026-12-05 con la carga de la 9A
$script:Token | Set-Clipboard                                      # para Authorize en Swagger (no se muestra)
```

**Postman:** importar `postman/maxi-eventos.postman_collection.json` y `postman/maxi-eventos-local.postman_environment.json`,
elegir el environment **Maxi Eventos - local** y poner la contraseña solo en "Current value" de `password` (ver
[`postman/README.md`](../postman/README.md)). Ejecutar **una vez la colección completa** con el Runner (*Run collection*):
así quedan guardados el token y todos los ids. Para cada captura de abajo, abrir la petición indicada en el **resultado del
Runner** (muestra la respuesta y los `pm.test`) o, si es una lectura o un error, pulsar **Send** de nuevo.

---

## 1. API REST funcional

| # | Acción | Qué debe verse |
|---|---|---|
| 1.1 | Resumen del Runner tras ejecutar la colección completa | **277 peticiones, 763 pruebas, 0 fallidas**, las 17 carpetas (00 a 99). |
| 1.2 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-postman.ps1` | La tabla de Newman (`iterations`, `requests 277`, `assertions 763`, `failed 0`) y los archivos en `postman/resultados/`. Lo mismo que 1.1, desde la consola y reproducible. |
| 1.3 | Carpeta **10 Flujo completo del negocio** en el Runner | Los 31 pasos en verde: evento → lugar → cotización → contrato → firma → abonos → confirmación → logística → adicional → anexo → proveedores → compras → notificaciones y auditoría. |
| 1.4 | `04.01 [happy] Evento de demostración` | `200` con el evento "Quince años de Lucía" `RESERVADO`, 90 adultos + 15 niños, NEIVA, con tildes correctas. |
| 1.5 | `05.02 [happy] Balance de la demostración` | Total 8.880.000, abonado 2.610.000, saldo 6.270.000 COP. |
| 1.6 | `06.04 [happy] PDF del contrato de la demostración` → **Save Response → Save to a file** y abrir el PDF | El contrato con los datos del cliente, el evento, el lugar y "Estado del contrato: FIRMADO". |
| 1.7 | `05.16` y `05.17` (pago con clave de idempotencia y su repetición) | `201` y luego `200` con el **mismo `pagoId`**: no se cobra dos veces. |

## 2. Persistencia

| # | Acción | Qué debe verse |
|---|---|---|
| 2.1 | `Psql "SELECT nombre_evento, fecha, estado FROM eventos ORDER BY fecha;"` | Los eventos guardados en PostgreSQL (los 3 de la demostración y los CANCELADOS que deja cada ejecución de Postman). |
| 2.2 | `Psql "SELECT fecha, estado FROM fechas_evento WHERE evento_id = '$eid';"` | La fecha del evento confirmado en `OCUPADA`. |
| 2.3 | `Psql "SELECT monto, moneda, clave_idempotencia FROM pagos WHERE evento_id = '$eid';"` | Los 2 abonos de la demostración. |
| 2.4 | `docker compose down` (**sin `-v`**) → `docker compose up -d` → esperar healthy → `Login` → repetir 1.4 | Los datos **siguen ahí** después de recrear los contenedores (volumen `postgres_data`). |
| 2.5 | VS Code: `services/eventos-service/src/main/resources/schema.sql` y [`modelo-datos.md`](modelo-datos.md) | Las tablas de cada servicio y el ER del modelo. |

## 3. Validaciones y errores (semana 8)

Todas las respuestas de error tienen el **mismo formato** (`ErrorResponse`): `timestamp, status, error, message, path` y, en
las validaciones, `errores: [{campo, mensaje}]`. Mensajes en español.

| # | Punto de la semana 8 | Petición de Postman | Qué debe verse |
|---|---|---|---|
| 3.1 | Happy path (crear) | `04.15 [happy] Crear evento E1 (sábado libre)` | `201`, cabecera **`Location`** y el evento `PENDIENTE`. |
| 3.2 | Sad path: datos inválidos | `04.12 [400] Crear con datos inválidos (fecha pasada, nombre corto, adultos negativos)` | `400` con `errores[]`: **un elemento por cada campo inválido** (entre ellos `nombreEvento` y `cantidadAdultos`), cada uno con su mensaje. |
| 3.3 | Sad path: otro servicio | `01.07 [400] Datos inválidos (detalle por campo)` | `400` con `errores[]` de Clientes (correo, teléfono, nombre). |
| 3.4 | Recurso inexistente | `04.09 [404] Evento inexistente` y `04.14 [404] Crear con un cliente inexistente` | `404` "No existe un evento con id …"; al crear, Eventos valida por REST que el cliente exista. |
| 3.5 | Conflicto de negocio: fin de semana | `04.16 [409] Otro evento el domingo del mismo fin de semana` | `409` "Solo se permite un evento por fin de semana…" (viernes, sábado y domingo forman un mismo fin de semana). |
| 3.6 | Conflicto: confirmar sin requisitos | `10.06 [409] Confirmar con el contrato sin firmar` y `10.11 [409] Confirmar con abono insuficiente` | `409` "El contrato del evento está en estado PENDIENTE; debe estar FIRMADO…" y, con abono menor que la reserva, el mensaje que dice cuánto falta ("Faltan …"). |
| 3.7 | Conflicto: firma doble y cancelación doble | `10.08 [409] Firmar dos veces` y `04.23 [409] Cancelar E1 otra vez` | `409` con mensajes claros. |
| 3.8 | Conflicto: duplicados | `01.06 [409] Correo repetido` y `10.22 [409] El mismo proveedor para el mismo servicio` | `409` "El proveedor «…» ya está asignado a este evento para el servicio «…»". |
| 3.9 | Filtros | `04.02 [filtro] estado=RESERVADO y tipo=QUINCE_ANOS` y `02.02 [filtro] estado=ACTIVO y tipo=salon` | Solo los registros que cumplen el filtro. |
| 3.10 | Paginación | `04.04 [paginación] page=0, size=1, sort=fecha,desc` | `PaginaResponse`: `contenido` (1), `pagina`, `tamanoPagina`, `totalElementos`, `totalPaginas`. |
| 3.11 | Paginación con tope | `04.05 [400] size mayor que 100` y `04.06 [400] sort con un campo no permitido` | `400`: `size` máximo 100 y `sort` con lista blanca. |
| 3.12 | Identificador inválido | `04.07 [400] Id que no es UUID` y `04.08 [400] Ruta sin identificador (//)` | `400` con mensaje claro (no un 500 ni un 401 vacío). |
| 3.13 | Seguridad | `04.25 [401] Sin token` | `401` "Debes iniciar sesión para realizar esta operación…". |

## 4. Reto de la semana 8

Un caso de validación y un error de negocio en **Finanzas** (`POST /api/v1/finanzas/pagos`), documentados en OpenAPI, con
prueba automática y evidencia en Postman:

| # | Acción | Qué debe verse |
|---|---|---|
| 4.1 | Postman, carpeta **11 Reto semana 8 (Finanzas)**: `11.02 [400 reto] Abono en una moneda distinta (USD)` | `400` con `errores: [{ "campo": "moneda", "mensaje": "debe ser COP, la moneda de la cotización" }]`. |
| 4.2 | `11.03 [409 reto] Abono mayor que el saldo pendiente` | `409` "El monto … supera el saldo pendiente de … COP." (con la demostración: saldo 6270000.00). |
| 4.3 | `11.01` y `11.04` | El saldo es el mismo antes y después: **ningún abono se registró**. |
| 4.4 | VS Code: `openapi/finanzas.yaml`, operación `POST /finanzas/pagos` (buscar `retoMonedaDistinta` y `retoMontoMayorQueElSaldo`) | Los dos casos documentados con ejemplo de petición y de respuesta (400 y 409). |
| 4.5 | `cd services\finanzas-service; .\gradlew.bat cleanTest test --no-daemon --tests "*monedaDistinta*" --tests "*montoMayorQueElSaldo*"; cd ..\..` | `BUILD SUCCESSFUL`. Las pruebas `FinanzasApiIntegracionTest` › `Pagos` (PostgreSQL real) y `PagoServiceTest` (unitarias) de los dos casos. |
| 4.6 | Postman `12.06 [error reto] registrarPago con moneda distinta → BAD_REQUEST en moneda` | El mismo reto por GraphQL: `BAD_REQUEST` con `extensions.errores` en `moneda`. |

## 5. OpenAPI actualizado

| # | Acción | Qué debe verse |
|---|---|---|
| 5.1 | Navegador: http://localhost:8081/swagger-ui.html → **Authorize** (pegar el token del portapapeles) → `GET /api/v1/eventos/{id}` con `$eid` → *Execute* | Swagger funcionando con JWT: `200` y el evento. Repetir sin Authorize: `401`. |
| 5.2 | En el mismo Swagger, `POST /api/v1/eventos` → *Try it out* y la sección **Schemas** (abajo) | El esquema del cuerpo con sus campos y validaciones, y `ErrorResponse` con `errores[]`. (Los ejemplos y todos los códigos de respuesta están en el contrato de `openapi/`, 5.4.) |
| 5.3 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\comparar-openapi.ps1` | `ValidarOpenApi` OK en los 10 contratos y, por servicio, **0 diferencias reales y 0 de reglas** (solo cosméticas): los YAML de `openapi/` coinciden con el código en marcha. |
| 5.4 | VS Code: `openapi/eventos.yaml` | `securitySchemes.bearerAuth`, `PaginaResponse`, filtros, ejemplos y los errores 400/401/404/409/503 con `ErrorResponse`. |

## 6. Pruebas

| # | Acción | Qué debe verse |
|---|---|---|
| 6.1 | Docker Desktop **en marcha** → `powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-pruebas.ps1` | La tabla por módulo (pruebas, fallos, **saltadas = 0**) y el total: **460 pruebas, 0 fallos**. |
| 6.2 | Abrir `services\eventos-service\build\reports\tests\test\index.html` | Informe de JUnit al 100 %: reglas del fin de semana (viernes, sábado, domingo, entre semana, cruce de meses), confirmación y `EventoApiIntegracionTest`. |
| 6.3 | `cd services\eventos-service; .\gradlew.bat cleanTest test --no-daemon --tests "*EventoApiIntegracionTest*"; cd ..\..` | Los casos de la semana 8 en una prueba de integración con PostgreSQL real (Testcontainers): 201 + `Location`, 400 por campo, 404, 409 del fin de semana, paginación y filtros, 401. |
| 6.4 | La prueba estrella de concurrencia (ver [`sustentacion.md`](sustentacion.md#6-pruebas-estrella-y-su-mutación)) | Dos abonos simultáneos: uno 201 y otro 409; el saldo nunca queda negativo. |

## 7. Caché Redis

Redis se usa como caché en Clientes, Lugares, Proveedores y Logística (tabla de cachés en el
[`README`](../README.md#7-caché-redis)). Para no tocar la demostración, se usa un cliente nuevo:

```powershell
$c = (CrearCliente 'Cliente Caché Evidencia').Body; $cid = $c.id; "cliente: $cid"
```

| # | Acción | Qué debe verse |
|---|---|---|
| 7.1 | `cls`; `(Api GET "$($Url.clientes)/clientes/$cid").Status` dos veces → `docker compose logs clientes-service --since 1m \| Select-String "Caché"` | La primera `Caché MISS  cache=clientes clave=<id> -> se consulta la base de datos` (crear no guarda en caché) y la segunda **`Caché HIT   cache=clientes clave=<id>`**. |
| 7.2 | `Redis 'redis-cli --scan --pattern "maxi:clientes:*"'` y `Redis "redis-cli TTL maxi:clientes:clientes::$cid"` | La clave `maxi:clientes:clientes::<id>` y su TTL (≤ 600 s). La contraseña de Redis no aparece (se lee dentro del contenedor). |
| 7.3 | `Redis "redis-cli GET maxi:clientes:clientes::$cid"` | El valor en **JSON** (el DTO `ClienteResponse`, no un objeto serializado de Java). |
| 7.4 | Actualizar: `(Api PUT "$($Url.clientes)/clientes/$cid" @{ nombreCompleto = 'Cliente Caché Actualizado'; telefono = $c.telefono; correo = $c.correo; direccion = $c.direccion }).Status` → `(Api GET "$($Url.clientes)/clientes/$cid").Body.nombreCompleto` | `200` y el GET devuelve **el nombre nuevo** (`@CachePut`: la caché nunca entrega el dato viejo). |
| 7.5 | Redis caído: `docker compose stop redis` → `(Api GET "$($Url.clientes)/clientes/$cid").Status` → `curl.exe -s http://localhost:8082/actuator/health` → `docker compose logs clientes-service --since 1m \| Select-String "Caché no disponible"` → `docker compose start redis` | `200` desde la base de datos, health **`UP`** y el WARN `Caché no disponible al leer cache=clientes …-> se consulta la base de datos`. La caché es opcional para la disponibilidad. |
| 7.6 | Tras `docker compose start redis`, repetir el GET dos veces (esperar unos **30 s** si no aparece el HIT) y el comando de logs de 7.1 | Vuelve el `Caché HIT`: el cliente Lettuce se reconecta solo (con espera creciente). |
| 7.7 (opcional) | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_9A.ps1` | Lo mismo automatizado en los 4 servicios con caché: `24 comprobaciones, 0 fallos`. Detiene y vuelve a arrancar Redis. |

Al terminar, borrar el cliente de la evidencia: `(Api DELETE "$($Url.clientes)/clientes/$cid").Status` (`204`).

## 8. Docker Compose

| # | Acción | Qué debe verse |
|---|---|---|
| 8.1 | `docker compose ps` | 16 contenedores: 10 servicios + gateway, frontend, postgres, redis y kafka **healthy**, y kafdrop. |
| 8.2 | VS Code: `docker-compose.yml` (bloques `x-env-base`, `postgres-db` y `eventos-service`) | Todos los secretos como `${VAR:?…}` (**ninguno escrito**), healthchecks, `depends_on` con `service_healthy`, `mem_limit` y versiones fijas de las imágenes. |
| 8.3 | VS Code: `.env.example` | Todas las variables con valores **de ejemplo** (no capturar el `.env`). |
| 8.4 | VS Code: `services/eventos-service/Dockerfile` | Dockerfile **multi-etapa**: compila con Gradle dentro de Docker, así que `docker compose up --build` funciona sin compilar antes. |

## 9. "Antes de integrar Kafka" (cierre de la semana 8)

| Criterio de la diapositiva | Evidencia |
|---|---|
| API estable | 1.1 / 1.2 (colección completa sin fallos, dos ejecuciones seguidas) |
| Datos persistidos | 2.1 – 2.4 |
| Errores predecibles | Sección 3 (`ErrorResponse` común, 400/404/409/401) |
| Contrato actualizado | 5.3 (comparador: 0 diferencias reales) |
| Pruebas básicas pasando | 6.1 |
| Docker Compose reproducible | 8.1 – 8.4 y la prueba desde cero de la Fase 9E (registro de trabajo, fuera del repositorio) |

---

### Notas

- Cada ejecución de la colección deja un cliente de prueba con sus 3 eventos CANCELADOS y un proveedor inactivo (ver
  [`postman/README.md`](../postman/README.md#lo-que-deja-cada-ejecución)); la demostración no cambia. Para listados limpios,
  filtrar (`estado=RESERVADO`, `activo=true`).
- `Api`, `Psql`, `Redis`, `CrearCliente` y `Login` son utilidades de `scripts/verificacion/comun.ps1`; no muestran credenciales.
- Los scripts de verificación crean sus datos con el prefijo **"Prueba"** (p. ej. "Prueba Quince años de Lucía") para no
  coincidir con la demostración, y tanto esta guía como la colección de Postman toman el evento **más antiguo** con el
  nombre de la demostración (`sort=fechaCreacion,asc`). Así las capturas funcionan antes o después de la regresión.
