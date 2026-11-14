# Evidencias — índice

Hay una guía de capturas por entrega. Cada guía dice, para cada captura, **qué hacer** (comando, petición de Postman o
pantalla) y **qué debe verse**:

| Entrega | Guía | Tema |
|---|---|---|
| P2 · Entrega 1 (semanas 7 y 8) | [`evidencias-p2e1.md`](evidencias-p2e1.md) | API REST, persistencia, validaciones y errores, OpenAPI, Postman, pruebas, Docker Compose, reto de la semana 8, caché Redis |
| P2 · Entrega 2 (semana 11) | [`evidencias-p2e2.md`](evidencias-p2e2.md) | Kafka: tópicos, productor con outbox, consumidores, idempotencia, DLT, logs |
| P3 · Entrega 1 (semana 13) | [`evidencias-p3e1-graphql.md`](evidencias-p3e1-graphql.md) | Gateway GraphQL, queries y mutations, errores, seguridad (JWT, CORS) |

Para la presentación en vivo: [`sustentacion.md`](sustentacion.md).

## Antes de capturar

1. `docker compose up -d --build` y esperar los 16 contenedores (`docker compose ps`); la interfaz queda en http://localhost:5173.
2. Sobre una base nueva, cargar **una vez** los datos de demostración: `scripts\verificacion\cargar-demo.ps1` (evento
   "Quince años de Lucía" RESERVADO y completo, "Boda de Andrés y Camila" PENDIENTE, "Quince años de María José" CANCELADO).
3. Postman: importar la colección y el environment de [`postman/`](../postman/) y ejecutar una vez la colección completa
   (guarda el token y los ids).
4. **Nunca** en una captura: el `.env`, los valores actuales del environment de Postman, la contraseña, el token completo
   (recortarlo) ni la salida de `docker compose config` (muestra los valores del `.env`).

Las abreviaturas de la tabla: **E1 §3.2** = `evidencias-p2e1.md`, captura 3.2; **E2** = `evidencias-p2e2.md`;
**P3** = `evidencias-p3e1-graphql.md`; **`04.12`** = petición de Postman con ese número; **S** = `sustentacion.md`.

## Cada punto de las diapositivas y su evidencia

### P2 · Entrega 1 (semana 7)

| Punto | Captura | Postman / script |
|---|---|---|
| API REST funcional | E1 §1.1 – 1.7 | Colección completa (277 peticiones, 763 pruebas, 0 fallos); `ejecutar-postman.ps1`; carpeta `10 Flujo completo` |
| Persistencia | E1 §2.1 – 2.5 | `04.01`, `05.02` (datos de la demostración); `docker compose down` sin `-v` y `up` |
| Validaciones y errores | E1 §3 | Peticiones `[400]`, `[404]`, `[409]` y `[401]` de las carpetas 01 – 09 |
| Regla de días permitidos (10B3) | S "Regla de días permitidos" | **Postman `04.11b`** (crear un martes → 400 en `fecha`): la interfaz ya no deja escribir fechas, así que este error se captura desde Postman. En la interfaz, captura de *Nuevo evento* con el calendario: lunes a jueves "No laborable" y el fin de semana del 5 de diciembre de 2026 "No disponible" |
| OpenAPI actualizado | E1 §5.1 – 5.4 | `comparar-openapi.ps1` (0 diferencias reales, 0 de reglas) |
| Pruebas iniciales | E1 §6.1 – 6.4 | `ejecutar-pruebas.ps1` (463 pruebas, 0 fallos, 0 saltadas); interfaz: `npm test` en `frontend/` (146 pruebas) |
| Docker Compose y evidencias | E1 §8.1 – 8.4 | `docker compose up --build`; esta guía |

### Semana 8: casos de prueba y reto

| Punto | Captura | Postman / script |
|---|---|---|
| Happy path | E1 §3.1, §1.3 | `04.15`, carpeta `10 Flujo completo`, peticiones `[happy]` |
| Sad path (datos inválidos) | E1 §3.2, §3.3, §3.11, §3.12 | `04.12`, `01.07`, `04.05`, `04.07`, `04.08` |
| Recurso inexistente | E1 §3.4 | `04.09`, `04.14`, `01.09`, `12.05` (GraphQL) |
| Conflicto de negocio | E1 §3.5 – 3.8 | `04.16` (fin de semana), `04.17`, `10.06`, `10.09`, `10.11`, `10.08`, `04.23`, `10.22` |
| Filtros y paginación | E1 §3.9 – 3.11 | `04.02`, `04.03`, `04.04`, `02.02`, `03.02`, `08.02`, `[paginación]` de cada carpeta |
| Reto: un caso de validación | E1 §4.1 | `11.02` (moneda distinta → 400 en `moneda`) |
| Reto: un error de negocio | E1 §4.2, §4.3 | `11.03` (monto mayor que el saldo → 409), `11.01` y `11.04` |
| Reto: documentarlo en OpenAPI | E1 §4.4 | `openapi/finanzas.yaml` (`retoMonedaDistinta`, `retoMontoMayorQueElSaldo`) |
| Reto: una prueba que lo verifique | E1 §4.5 | `FinanzasApiIntegracionTest` › `Pagos` y `PagoServiceTest` |
| Reto: evidencia en Postman | E1 §4.1 – 4.3, §4.6 | Carpeta `11 Reto semana 8 (Finanzas)` y `12.06` (GraphQL) |
| Antes de Kafka: API estable | E1 §1.1, §1.2 | Dos ejecuciones seguidas de la colección sin fallos |
| Antes de Kafka: datos persistidos | E1 §2 | — |
| Antes de Kafka: errores predecibles | E1 §3 | `ErrorResponse` común en todas las peticiones de error |
| Antes de Kafka: contrato actualizado | E1 §5.3 | `comparar-openapi.ps1` |
| Antes de Kafka: pruebas básicas pasando | E1 §6.1 | `ejecutar-pruebas.ps1` |
| Antes de Kafka: Docker Compose reproducible | E1 §8 | Prueba desde cero de la Fase 9E (registro de trabajo, fuera del repositorio) |

### P2 · Entrega 2 (semana 11)

| Punto | Captura | Postman / script |
|---|---|---|
| Tópicos y eventos | E2 §1.1 – 1.5 | Kafdrop (http://localhost:9000) |
| Productor o simulador | E2 §2.1 – 2.5 (incluido Kafka caído y outbox) | `verificar_5A2.ps1`; S demo 2 |
| Consumidores | E2 §3.1 – 3.4 | `kafka-consumer-groups --describe` |
| Procesamiento e integración | E2 §4.1 – 4.7 | `10.16`, `10.19`, `10.30`, `10.31`; `07.01`, `08.01`, `09.01` |
| Idempotencia | E2 §5.1 – 5.3 | Prueba estrella `unAdicionalDeTransporteRepetidoNoSumaOtroCamion`; `05.17` (pago repetido, idempotencia REST); S demo 3 |
| Logs | E2 §7.1, §7.2 | `docker compose logs … \| Select-String "Consumo Kafka"` |
| Docker Compose | E2 §8.1 – 8.4 (incluida la persistencia de Kafka) | `verificar_5A1.ps1` |
| Pruebas | E2 §9.1 – 9.4 | Pruebas estrella `unMensajeInvalidoVaAlDltConUnSoloIntento` y la de idempotencia; `verificar_5B.ps1`, `verificar_5C.ps1` |
| Evidencias | Esta tabla y `evidencias-p2e2.md` | — |
| Flujo mínimo: generar o publicar un evento | E2 §0 (flujo) y §2.1 | `10.13 Confirmar el evento` |
| Flujo mínimo: verificar su llegada al tópico | E2 §1.4, §2.3 | Kafdrop → `eventos.confirmados` |
| Flujo mínimo: consumirlo | E2 §3.2, §7.1 | LAG 0 y la línea `resultado=PROCESADO` |
| Flujo mínimo: producir un cambio observable | E2 §4.1, §4.5, §4.6 | `10.16` (logística calculada), `10.19` (recalculada), `10.20` (anexo) |
| Flujo mínimo: evidencia de principio a fin | E2 §0 – §4 en orden | Carpeta `10 Flujo completo`; S demo 1 |
| DLT y errores del consumidor (complemento) | E2 §6.1 – 6.5 | `verificar_5B.ps1`; S demo 3 |

### P3 · Entrega 1 (semana 13)

| Punto | Captura | Postman / script |
|---|---|---|
| Gateway GraphQL funcional | P3 §0, §1, §8.1 | `verificar_6A.ps1` |
| Queries y mutations | P3 §1 – §4 | `12.01`, `12.02` (ficha completa), `12.03` (`crearCliente`); `verificar_6B.ps1` (flujo 100 % por GraphQL) |
| Integración con servicios | P3 §3.13, §4.3, §5.1 – 5.3 | Log `REST -> ` del gateway; S demo 1 (query parcial: solo 2 servicios) |
| Manejo de errores | P3 §6.1 – 6.9 | `12.04` (BAD_REQUEST), `12.05` (NOT_FOUND), `12.06` (reto) |
| Seguridad básica | P3 §10 | `12.07`, `[401]` de cada carpeta; `verificar_7A.ps1`, `verificar_7B.ps1` |
| Docker Compose, pruebas y evidencias | P3 §8, §9, §10.E | `gateway`: 107 pruebas (`SeguridadGatewayTest`, `ConsultasGraphQlTest`, `MutacionesGraphQlTest`, `DocumentosControllerTest`…) |
| Seguridad: identificar operaciones públicas y protegidas | P3 §10.D (10.16 – 10.19) | [`seguridad.md`](seguridad.md#qué-es-público-y-qué-está-protegido) |
| Seguridad: definir la identidad necesaria | P3 §10.7 (claims del token) | [`seguridad.md`](seguridad.md#por-qué-basta-con-autenticación-sin-roles) |
| Seguridad: proteger una operación REST o GraphQL | P3 §10.1 – 10.3, §10.8 – 10.9 | `04.25`, `12.07` |
| Seguridad: probar acceso permitido y denegado | P3 §10.1, §10.3, §10.4, §10.6, §10.8 – 10.10 | `00.01` (login 401), `00.03` (login 200), `[401]` de cada carpeta, `12.07`; S demo 5 |
| Seguridad: documentar la decisión | — | [`seguridad.md`](seguridad.md) |
| CORS explícito sin permitir cualquier origen | P3 §10.13 – 10.15 | `SeguridadGatewayTest` (preflight permitido, no permitido y `*` rechazado) |
| Secretos en variables de entorno, nunca en el repositorio ni en las evidencias | E1 §8.2, §8.3; P3 §10.15 | `.env.example`, `${VAR:?}` en el compose; `ejecutar-postman.ps1` revisa que los resultados no tengan contraseña ni token; [`seguridad.md`](seguridad.md#secretos-env-y-envexample) |

### Interfaz web (Fase 10; requisito no funcional de la Entrega 2)

*"Interfaz minimalista y centralizada que permita consultar la información completa de un evento desde una única vista."*
Todo desde **http://localhost:5173** (contenedor `frontend`; ver [`frontend.md`](frontend.md)). No mostrar el `.env` ni el
token completo en ninguna captura. Qué evento de la demostración usar para cada cosa:
[`sustentacion.md`](sustentacion.md#datos-de-demostración-qué-evento-usar-para-mostrar-cada-cosa).

| Punto | Captura | Cómo |
|---|---|---|
| Vista única del evento | Ficha de "Quince años de Lucía" completa (las 11 secciones y la franja de resumen con el saldo pendiente $ 6.270.000) | Eventos → filtro Estado = Reservado → abrir la ficha. En DevTools → Red: **una sola** petición `FichaEvento` a `/graphql` |
| Error controlado en la interfaz | El 409 "El contrato del evento está en estado PENDIENTE…" junto al botón Confirmar | Ficha de **Quince años de Sofía** (contrato sin firmar, de la demostración) → paso **Confirmar** de la guía → **Confirmar evento**; no cambia nada |
| Regla de fechas en la interfaz | Nuevo evento: calendario de diciembre de 2026 con el 4–6 "No disponible" y los lunes a jueves "No laborable" | Nuevo evento → "Mes siguiente" (el martes por API: Postman `04.11b`) |
| Lista de chequeo de compras | Logística de **Boda de Camilo y Juliana**: "3 de 7 conseguidos", 2 ítems "Agregado a mano" | Abrir su ficha (ya cargado por `cargar-demo.ps1`; no hace falta modificar nada) |
| Seguridad de la interfaz | DevTools → Red → `localhost` → cabeceras `Content-Security-Policy`, `X-Frame-Options`…; Aplicación → Session Storage con `maxi-eventos.token` (recortado) y Local Storage vacío | [`seguridad.md`](seguridad.md#la-interfaz-web-fase-10-token-csp-y-cabeceras) |
| Interfaz en Docker, verificada | Salida de `verificar_10.ps1` (80 comprobaciones, 0 fallos) y `docker compose ps` con `frontend` healthy | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_10.ps1` |
| Pruebas de la interfaz | Salida de `npm test` (146 pruebas, 0 fallos) | `cd frontend` y `npm test` |

### Decisiones propias del proyecto (no son puntos de las diapositivas)

| Tema | Captura | Postman / script |
|---|---|---|
| Caché Redis (HIT/MISS, TTL, dato nuevo tras actualizar, Redis caído) | E1 §7.1 – 7.7 | `verificar_9A.ps1`; prueba estrella `elClienteSeGuardaEnRedis…`; S demo 4 |
| Concurrencia de abonos (bloqueo `FOR UPDATE`) | E1 §6.4 | Prueba estrella `dosAbonosSimultaneos…`; S demo 6 |
| Datos de demostración intactos tras las pruebas | — | Carpeta `99 Datos de demostración intactos` |
