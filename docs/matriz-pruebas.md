# Matriz de pruebas — Maxi Eventos

Versión entregada: `v1.0.0`. Las capturas están en [`evidencias/`](evidencias/) y se describen en
[`evidencias-final.md`](evidencias-final.md) (Imagen N). Todas se tomaron sobre los **datos de demostración** (ficticios):
las Imágenes 1 a 23 el 13 de noviembre de 2026 y las Imágenes 24 a 30 el 18 de noviembre de 2026.

**Estados**

- **Aprobado**: hay una captura o un resumen de ejecución que muestra el resultado.
- **Aprobado (prueba automática)**: lo verifica una prueba automática dentro de la cifra total (463 de backend, 146 de la
  interfaz, 277 peticiones de Newman) y no tiene una captura propia.
- **Sin evidencia visual**: existe el script o la guía, pero esta entrega no tiene captura ni resumen de su ejecución.
- **Pendiente**: no está implementado.

Cuando no hay captura propia, la columna Evidencia dice dónde está el código de la prueba. Las cifras totales sí tienen captura.

## 1. Pruebas automáticas y verificación general

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Evidencia |
|---|---|---|---|---|---|
| G01 | Entorno completo con Docker Compose | 16 contenedores en marcha, 15 con healthcheck sano | 16 contenedores; Kafdrop no tiene healthcheck | Aprobado | Imagen 2 |
| G02 | Pruebas del backend (JUnit, Mockito, Testcontainers, EmbeddedKafka, GraphQlTester) | 463 pruebas en 11 módulos, sin fallos ni saltadas | 463 pruebas, 0 fallos, 0 saltadas | Aprobado | Imagen 3; `scripts/verificacion/ejecutar-pruebas.ps1` |
| G03 | Pruebas de la interfaz (Vitest + Testing Library) | 146 pruebas sin fallos | 19 archivos y 146 pruebas pasadas | Aprobado | Imagen 24 |
| G04 | Colección de Postman con Newman | 277 peticiones y 763 aserciones sin fallos | 277 peticiones, 763 aserciones, 0 fallos | Aprobado | Imagen 4; `postman/` |
| G05 | Contratos OpenAPI contra el código | Los 10 contratos de los servicios coinciden con `/v3/api-docs` | Verificación con script; sin captura en esta entrega | Sin evidencia visual | `scripts/verificacion/comparar-openapi.ps1`; `docs/evidencias-p2e1.md` §5 |
| G06 | Swagger de un servicio | Documentación disponible y probable con token | Swagger de eventos-service con sus controladores | Aprobado | Imagen 21 |

## 2. Reglas de negocio y flujos de la interfaz

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Evidencia |
|---|---|---|---|---|---|
| N01 | Inicio de sesión | Pantalla de acceso con JWT | Pantalla de inicio de sesión | Aprobado | Imagen 5 |
| N02 | Agenda de la administradora | Lista de eventos y bloque «Próximos 7 días» | Agenda con «Próximos 7 días» y la lista | Aprobado | Imagen 6 |
| N03 | Ficha completa del evento en una sola vista | Todos los datos del evento en una pantalla | Ficha de «Quince años de Lucía» con la franja de resumen | Aprobado | Imagen 7 |
| N04 | Evento completo (paso 7 «Resumen») | «Todo listo» cuando no hay pendientes | «Boda de Valentina y Santiago»: todo listo | Aprobado | Imagen 8 |
| N05 | Evento con pendientes | Lista de los pendientes | «Quince años de Sofía»: quedan 4 pendientes | Aprobado | Imagen 9 |
| N06 | Confirmar sin contrato firmado | Error de negocio 409 | 409 al confirmar «Quince años de Sofía» con el contrato pendiente | Aprobado | Imagen 10 |
| N07 | Regla de fin de semana (solo viernes a domingo; un evento por fin de semana) | Los días de un fin de semana ocupado no se pueden elegir | Diciembre de 2026: fines de semana ocupados no seleccionables | Aprobado | Imagen 11 |
| N08 | Abonos concurrentes | Dos abonos a la vez nunca superan el saldo (bloqueo y clave de idempotencia) | Sin captura propia | Aprobado (prueba automática) | `services/finanzas-service/src/test` |
| N09 | Servicios adicionales | El total se actualiza; logística recalcula y contratos registra el anexo | Sin captura propia | Aprobado (prueba automática) | Colección Newman, carpeta `10 Flujo completo del negocio`; Imagen 4 |
| N10 | Cancelar un evento | La fecha vuelve a «Disponible» | Sin captura propia | Aprobado (prueba automática) | Colección Newman, carpeta `04 Eventos`; Imagen 4 |
| N11 | Cotización pendiente que no continúa | Limitación: no se vence sola; la organizadora cancela el evento a mano | No implementado el vencimiento automático | Pendiente | `README.md`, sección «Limitaciones de esta versión» |

## 3. Kafka, mensajería y recordatorios

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Evidencia |
|---|---|---|---|---|---|
| K01 | Tópicos y DLT | 4 tópicos con su `.DLT`, 3 particiones | Kafdrop: 9 tópicos (4 + 4 DLT + `__consumer_offsets`) | Aprobado | Imagen 12 |
| K02 | Mensaje `EVENTO_CONFIRMADO` | Mensaje con clave `eventoId` y sobre común | Mensaje visible en `eventos.confirmados` | Aprobado | Imagen 13 |
| K03 | Outbox, idempotencia y DLT | Sin duplicados en el efecto; mensajes inválidos a `.DLT` con un intento | Sin captura propia | Aprobado (prueba automática) | `@EmbeddedKafka` en eventos, finanzas, logística y notificaciones; `docs/kafka.md` |
| K04 | **C06 — Kafka detenido** | El abono se guarda sin Kafka y el aviso llega al volver, sin pérdidas ni duplicados | Con `kafka_broker` detenido unos 5 minutos se registró un abono de $900.000 (5 pagos, saldo $5.220.000); la campanita siguió en 2. Al reiniciar Kafka pasó a 3 y llegó un solo aviso del abono | Aprobado | Imágenes 25 a 30 |
| K05 | Recordatorio con la campanita | Campanita en 0, luego 1 sin recargar, con el aviso «Falta 1 día…» | Campanita 0 → 1 y panel con «Boda de Gabriela y Mateo» | Aprobado | Imágenes 14, 15 y 16 |

## 4. Seguridad y errores

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Evidencia |
|---|---|---|---|---|---|
| S01 | Rol asistente en la interfaz | Rol «Asistente» y sin el botón «Ejecutar recordatorios» | Sesión de la asistente sin ese botón | Aprobado | Imagen 17 |
| S02 | Lugares para la asistente | Solo consulta, sin botones de administración | Lugares sin botones de administración | Aprobado | Imagen 18 |
| S03 | 403 del asistente en el gateway y en cada servicio | El asistente recibe 403 en ambos niveles | Sin captura propia | Aprobado (prueba automática) | Newman, carpeta `12b Roles`; `scripts/verificacion/verificar_11D.ps1` |
| S04 | Error técnico con referencia | Mensaje genérico con «Ref: XXXXXXXX», sin el detalle técnico | «Ref: 220F8EE9» con contratos-service detenido | Aprobado | Imagen 19 |
| S05 | Referencia en el log | La misma referencia en el log del gateway (503) | Log del gateway con la misma referencia | Aprobado | Imagen 20 |

## 5. Interfaz móvil

| ID | Caso | Resultado esperado | Resultado obtenido | Estado | Evidencia |
|---|---|---|---|---|---|
| M01 | Ficha del evento en celular (390 px) | Sin desplazamiento horizontal | Ficha de Lucía sin desplazamiento horizontal | Aprobado | Imagen 22 |
| M02 | Calendario de «Nuevo evento» en celular | Calendario legible a 390 px | Calendario en celular | Aprobado | Imagen 23 |

## 6. Medido, simulado y trabajo futuro

| Clase | Qué incluye |
|---|---|
| **Medido** (con evidencia) | Las pruebas G01 a G04 y G06; los flujos N01 a N07; Kafka K01, K02, K04 y K05; S01, S02, S04 y S05; M01 y M02 |
| **Medido por pruebas automáticas** (sin captura propia) | N08 a N10, K03 y S03 |
| **Con script, sin captura en esta entrega** | G05 |
| **Simulado** | Notificaciones al cliente (se guardan y se muestran, no se envían por correo); datos de demostración ficticios; ejecución local con Docker Compose; tarifas y reglas de ejemplo |
| **Trabajo futuro / pendiente** | Circuit breaker; una base por servicio; correo real; tiempo real para la campanita; despliegue con HTTPS y respaldos; vencimiento automático de cotizaciones pendientes; validación con la empresa; tarifas reales |

## 7. Notas

- La prueba K04 duró unos 5 minutos (Kafka detenido a las 8:38 y reiniciado a las 8:43, hora local), más que los 120 s de la
  prueba original.
- El repositorio no guarda reportes de ejecución: la evidencia son las capturas de esta carpeta y las cifras de los scripts.
