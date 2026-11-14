# Evidencias P2 · Entrega 2 — Kafka (guía de capturas)

Guía paso a paso para tomar las capturas de la entrega de mensajería: tópicos y eventos, productor, consumidores,
procesamiento e integración, idempotencia, DLT, logs, Docker Compose y pruebas. Cada captura indica **qué hacer** y
**qué debe verse**. La explicación técnica está en [`kafka.md`](kafka.md).

> Todos los comandos se ejecutan en **PowerShell 5.1 desde la raíz del proyecto** (`maxi_eventos\`), salvo que se indique otra
> carpeta. No se muestra ningún secreto: el login usa el `.env` (ver `scripts/verificacion/credenciales.ps1`) y el token
> no se imprime. Antes de cada captura de logs, limpiar la pantalla con `cls`.

## 0. Preparación (una vez)

```powershell
docker compose up -d --build
docker compose ps
```

Esperar a que los 10 microservicios, `postgres-db`, `redis` y `kafka` estén `healthy` (1–2 minutos). Después, en la misma
ventana, cargar las utilidades de los scripts e iniciar sesión:

```powershell
. .\scripts\verificacion\comun.ps1
Login | Select-Object Status          # debe decir 200
```

Crear el **evento de las evidencias** con el flujo completo (cliente → evento → cotización → contrato firmado → 2 abonos →
confirmación) y un servicio adicional:

```powershell
$f   = FlujoFase3 (FechasLibres)[0]
$eid = $f.EventoId
(Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'decoracion-premium'; cantidad = 1 }).Status   # 201
"eventoId de las evidencias: $eid"
```

Anotar el `eventoId`: se usa en casi todas las capturas. Para Swagger, copiar el token al portapapeles (no se muestra) con
`$script:Token | Set-Clipboard`.

---

## 1. Tópicos y eventos

| # | Acción | Qué debe verse en la captura |
|---|---|---|
| 1.1 | Abrir **Kafdrop**: http://localhost:9000 | La lista de tópicos con `eventos.confirmados`, `pagos.registrados`, `servicios-adicionales.agregados` y sus `*.DLT`, cada uno con **3 particiones**. Ya **no** existe `servicios.adicionales`. |
| 1.2 | `docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic eventos.confirmados` (repetir con los otros dos) | `PartitionCount: 3`, `ReplicationFactor: 1` y las particiones 0, 1 y 2. |
| 1.3 | En VS Code abrir `services/eventos-service/src/main/java/com/maxieventos/eventos/config/KafkaTopicConfig.java` y `services/finanzas-service/src/main/java/com/maxieventos/finanzas/config/KafkaTopicConfig.java` | Los `NewTopic` con `TopicBuilder.name(...).partitions(3)`: **cada tópico lo declara su productor**. |
| 1.4 | Kafdrop → `eventos.confirmados` → **View Messages** (Partition: la del evento o probar 0/1/2, Offset 0, Count 100) → buscar el `eventoId` | El **sobre** con los 7 campos (`idEvento`, `tipo`, `version`, `fechaOcurrencia`, `servicioOrigen`, `eventoId`, `datos`), `datos` con el nombre del evento con tildes y la **clave = eventoId**. |
| 1.5 | Igual que 1.4 en `pagos.registrados` y `servicios-adicionales.agregados` | Los dos `PAGO_REGISTRADO` del evento (en la **misma partición**, porque la clave es el eventoId) y el `SERVICIO_ADICIONAL_AGREGADO` con `codigo`, `valorUnitario` y `subtotal`. |

## 2. Productor (publicación después del commit + outbox)

| # | Acción | Qué debe verse |
|---|---|---|
| 2.1 | `docker compose logs eventos-service --since 10m \| Select-String "EVENTO_CONFIRMADO publicado"` | `EVENTO_CONFIRMADO publicado. idEvento=…, eventoId=<el del evento>, topic=eventos.confirmados, partition=…, offset=…, intento=1`. |
| 2.2 | `docker compose logs finanzas-service --since 10m \| Select-String "publicado"` | Dos `PAGO_REGISTRADO publicado` y un `SERVICIO_ADICIONAL_AGREGADO publicado` con el mismo `eventoId`. |
| 2.3 | `docker compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic pagos.registrados --from-beginning --property print.key=true --property print.partition=true --timeout-ms 8000 \| Select-String $eid` | Cada línea empieza por `Partition:N` y la **clave = eventoId**; los dos pagos en la misma partición. |
| 2.4 | `Psql "SELECT tipo, estado, intentos, publicado_en FROM eventos_mensajes_salientes WHERE clave = '$eid';"` y `Psql "SELECT tipo, estado, intentos FROM finanzas_mensajes_salientes WHERE clave = '$eid';"` | **Outbox**: una fila por mensaje, todas `PUBLICADO` con `intentos = 1`. |
| 2.5 (Kafka caído) | `docker compose stop kafka` → registrar un abono (ver abajo) → `Psql "SELECT estado, intentos, ultimo_error FROM finanzas_mensajes_salientes WHERE clave = '$eid' ORDER BY creado_en DESC LIMIT 1;"` → `docker compose start kafka` → esperar ~60 s → repetir la consulta | El abono responde **201** aunque Kafka esté detenido; la fila queda `PENDIENTE` (o `ENVIANDO`) con el error; al volver Kafka pasa sola a `PUBLICADO` (log `Reenvío del outbox ... resultado=PUBLICADO`). |

Abono para 2.5 (el saldo pendiente del evento permite abonos pequeños):

```powershell
(Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $eid; clienteId = $f.ClienteId; metodoPago = 'EFECTIVO'; monto = 10000; moneda = 'COP' }).Status
```

## 3. Consumidores

| # | Acción | Qué debe verse |
|---|---|---|
| 3.1 | `docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list` | Los 4 grupos de la aplicación: `logistica-group`, `contratos-group`, `auditoria-group`, `notificaciones-group` (los `console-consumer-*` son de las consultas de consola). |
| 3.2 | `docker compose exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group auditoria-group` (repetir con los otros tres) | Qué tópicos y particiones lee cada grupo y **LAG 0**: auditoría los 3 tópicos; notificaciones `eventos.confirmados` y `pagos.registrados`; logística `eventos.confirmados` y `servicios-adicionales.agregados`; contratos solo `servicios-adicionales.agregados`. Ningún grupo lee un `*.DLT`. |
| 3.3 | En VS Code: `services/auditoria-service/.../kafka/AuditoriaKafkaConsumer.java` y `services/notificaciones-service/.../kafka/NotificacionesKafkaConsumer.java` | Los `@KafkaListener` con `topics` y `groupId` explícitos. |
| 3.4 | Tabla "Productores, consumidores, grupos y tópicos" de [`kafka.md`](kafka.md) (vista previa de Markdown en VS Code) | Resumen de productores, consumidores, grupos y DLT. |

## 4. Procesamiento e integración

| # | Acción | Qué debe verse |
|---|---|---|
| 4.1 | `(Api GET "$($Url.logistica)/eventos/$eid/logistica").Body` | Logística calculada al consumir EVENTO_CONFIRMADO: meseros, personal de cocina y camiones. |
| 4.2 | Swagger de auditoría: http://localhost:8087/swagger-ui.html → **Authorize** (pegar el token) → `GET /api/v1/auditoria` con `eventoId` = el del evento | **4 registros** del evento: 2 `PAGO_REGISTRADO`, 1 `EVENTO_CONFIRMADO`, 1 `SERVICIO_ADICIONAL_AGREGADO` (5 si se hizo el abono de 2.5), cada uno con `topico`, `particion`, `offset`, `servicioOrigen` y el `payload` = el sobre original. |
| 4.3 | En el mismo Swagger, `tipo = PAGO_REGISTRADO`, `size = 1`, `sort = fechaOcurrencia,asc` | Filtro por tipo y paginación: `tamanoPagina 1`, `totalPaginas` = número de pagos. |
| 4.4 | Swagger de notificaciones: http://localhost:8089/swagger-ui.html → Authorize → `GET /api/v1/notificaciones` con el `eventoId` | **3 notificaciones** `GENERADA` (2 abonos + confirmación; **ninguna por el adicional**), destinatario `cliente:<clienteId>`, asunto y mensaje en español con tildes, fechas y montos es-CO. |
| 4.5 | `Psql "SELECT tipo, topico, particion, offset_kafka, fecha_recepcion FROM auditoria_eventos WHERE evento_id = '$eid' ORDER BY fecha_recepcion;"` y `Psql "SELECT tipo, destinatario, asunto, estado FROM notificaciones WHERE evento_id = '$eid';"` | El cambio en la base de datos de cada consumidor. |
| 4.6 | `Psql "SELECT codigo, cantidad, subtotal FROM contrato_adicionales WHERE evento_id = '$eid';"` | Contratos guardó el adicional para el anexo (consumo de `servicios-adicionales.agregados`). |
| 4.7 | `GET /api/v1/auditoria` **sin** Authorize (o `(Api GET "$($Url.auditoria)/auditoria" -SinToken).Status`) | **401**: la API está protegida con JWT. |

## 5. Idempotencia

Reenviar al tópico **exactamente el mismo mensaje** (mismo `idEvento`) de la confirmación y de un pago:

```powershell
$mConf = @(LeerTopico 'eventos.confirmados' | Where-Object { $_.Clave -eq $eid })[0]
$mPago = @(LeerTopico 'pagos.registrados'   | Where-Object { $_.Clave -eq $eid })[0]
$antes = Psql "SELECT (SELECT count(*) FROM auditoria_eventos), (SELECT count(*) FROM notificaciones), (SELECT count(*) FROM logistica_eventos);"
$desde = Get-Date
InyectarMensajes 'eventos.confirmados' @("$eid#$($mConf.Valor)")
InyectarMensajes 'pagos.registrados'   @("$eid#$($mPago.Valor)")
Start-Sleep -Seconds 8
"idEvento repetidos: $($mConf.Json.idEvento) / $($mPago.Json.idEvento)"
```

| # | Acción | Qué debe verse |
|---|---|---|
| 5.1 | `((LogsDesde 'auditoria-service' $desde) + (LogsDesde 'notificaciones-service' $desde) + (LogsDesde 'logistica-service' $desde)) -split '\r?\n' \| Select-String "DUPLICADO_IGNORADO" \| ForEach-Object Line` | 5 líneas `resultado=DUPLICADO_IGNORADO, detalle=idEvento ya procesado por <grupo>`: auditoría y notificaciones, los dos mensajes; logística, solo la confirmación (no consume pagos). **Mismo idEvento** que el original y otro **offset**. |
| 5.2 | `"antes: $antes"; Psql "SELECT (SELECT count(*) FROM auditoria_eventos), (SELECT count(*) FROM notificaciones), (SELECT count(*) FROM logistica_eventos);"` | Los mismos números antes y después: **el duplicado no crea filas**. |
| 5.3 | `Psql "SELECT consumer_group, tipo, topico, particion, offset_kafka FROM auditoria_eventos_procesados WHERE id_evento = '$($mConf.Json.idEvento)';"` (y lo mismo en `notificaciones_eventos_procesados` y `logistica_eventos_procesados`) | **Una sola fila** por grupo para ese idEvento (clave `id_evento + consumer_group`), con el offset del primer consumo. |

## 6. DLT (mensajes inválidos y errores)

Inyectar un mensaje con un tipo desconocido y otro que no es JSON en `pagos.registrados`:

```powershell
$idInv = [guid]::NewGuid().ToString()
$inv = [ordered]@{ idEvento = $idInv; tipo = 'PAGO_DESCONOCIDO'; version = 1; fechaOcurrencia = '2026-10-28T12:00:00Z'; servicioOrigen = 'evidencia'; eventoId = $eid; datos = @{ monto = 1 } } | ConvertTo-Json -Compress
$desde = Get-Date
InyectarMensajes 'pagos.registrados' @("$eid#$inv", "$eid#esto no es json")
Start-Sleep -Seconds 10
```

| # | Acción | Qué debe verse |
|---|---|---|
| 6.1 | `((LogsDesde 'auditoria-service' $desde) + (LogsDesde 'notificaciones-service' $desde)) -split '\r?\n' \| Select-String "INVALIDO_A_DLT" \| ForEach-Object Line` | 4 líneas `INVALIDO_A_DLT` (2 por grupo): el tipo desconocido con `MensajeInvalidoException: Tipo de mensaje no esperado…` y el ilegible con `idEvento=-` y `JsonParseException`. **Ninguna línea `REINTENTO`** (no se reintentan). |
| 6.2 | Kafdrop → `pagos.registrados.DLT` → View Messages (partición del evento) | Los dos mensajes, **una copia por grupo**, con las cabeceras `kafka_dlt-original-topic`, `kafka_dlt-original-offset`, `kafka_dlt-original-consumer-group` (`auditoria-group` / `notificaciones-group`) y `kafka_dlt-exception-message`; el ilegible conserva el texto original. |
| 6.3 | Consola con cabeceras: `docker compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic pagos.registrados.DLT --from-beginning --property print.headers=true --timeout-ms 8000` | Lo mismo que 6.2 en texto. |
| 6.4 | `Psql "SELECT count(*) FROM auditoria_eventos WHERE id_evento = '$idInv';"` | `0`: el inválido no se audita ni se notifica. |
| 6.5 (error transitorio) | Ejecutar `scripts\verificacion\verificar_5B.ps1` y capturar la sección **"4. Error transitorio (BD no disponible)"**; o capturar después `docker compose logs logistica-service auditoria-service --since 15m \| Select-String "REINTENTO"` | Con `postgres-db` detenido: `REINTENTO 1`, `REINTENTO 2` y `REINTENTOS_AGOTADOS_A_DLT` (3 intentos con 1 s entre ellos) y **nada más** después: sin bucle infinito. El script vuelve a arrancar PostgreSQL. |

## 7. Logs del consumidor

| # | Acción | Qué debe verse |
|---|---|---|
| 7.1 | `docker compose logs logistica-service contratos-service auditoria-service notificaciones-service --since 15m \| Select-String "Consumo Kafka"` | El **formato fijo** en los cuatro consumidores: `Consumo Kafka. grupo=…, topic=…, particion=…, offset=…, idEvento=…, tipo=…, eventoId=…, resultado=…, detalle=…`, con resultados `PROCESADO`, `DUPLICADO_IGNORADO`, `INVALIDO_A_DLT` (y `REINTENTO n` / `REINTENTOS_AGOTADOS_A_DLT` si se hizo 6.5). |
| 7.2 | `docker compose logs notificaciones-service --since 15m \| Select-String "PROCESADO"` | `detalle=notificación GENERADA para cliente:<clienteId>: Evento confirmado: …` / `: Abono registrado`. |

## 8. Docker Compose

| # | Acción | Qué debe verse |
|---|---|---|
| 8.1 | `docker compose ps` | 16 contenedores: 10 microservicios + gateway (desde la Fase 6), frontend (desde la Fase 10), postgres, redis, kafka (todos **healthy**) y kafdrop. |
| 8.2 | `docker stats --no-stream` | Cada servicio Java por debajo de su límite de **512 MiB** (≈ 300–430 MiB); kafka < 768 MiB. Total ≈ 4,7 GiB (cabe en los 8 GB de WSL). |
| 8.3 | En VS Code, `docker-compose.yml`: bloques `kafka` (volumen `kafka_data`, `CLUSTER_ID`), `kafdrop`, `auditoria-service` y `notificaciones-service` | Puertos 8087 y 8089, `depends_on` con `service_healthy` hacia postgres y kafka, healthcheck `/actuator/health`, `mem_limit` y `JAVA_TOOL_OPTIONS -Xmx256m` heredados de `servicio-base` / `env-base`; ningún secreto escrito (todo `${VAR}`). |
| 8.4 (persistencia) | `docker compose down` (**sin `-v`**) → `docker compose up -d` → esperar healthy → repetir 3.2 y 1.4 | Los mensajes siguen en Kafka y los grupos conservan sus offsets (**LAG 0**): no se reprocesa nada. |

## 9. Pruebas

| # | Acción | Qué debe verse |
|---|---|---|
| 9.1 | `cd services\auditoria-service; .\gradlew.bat clean test --no-daemon; cd ..\..` (repetir con `notificaciones-service`, `logistica-service` y `contratos-service`) | `BUILD SUCCESSFUL`. Pruebas: auditoría 16, notificaciones 16, logística 40 (desde la Fase 8, con `@EmbeddedKafka`), contratos 28. |
| 9.2 | Abrir `services\auditoria-service\build\reports\tests\test\index.html` en el navegador | Informe de JUnit al 100 %: consumo (procesado, duplicado, carrera por la clave primaria, inválidos), DLT `<tópico>.DLT`, payload original, filtros del GET. |
| 9.3 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_5C.ps1` | Las secciones 1 a 8 en verde y al final `Verificación 5C: 71 comprobaciones, 0 fallos`. |
| 9.4 | `powershell -ExecutionPolicy Bypass -File scripts\verificacion\verificar_5B.ps1` | `Verificación 5B: 65 comprobaciones, 0 fallos` (recálculo de logística, duplicado, DLT, BD caída). |
| 9.5 | `scripts\verificacion\verificar_5C_historial.ps1` | **Solo tiene sentido en el primer despliegue** de auditoría y notificaciones (con `-Antes` antes de crear los servicios). Su resultado del 2026-10-28 (33 / 0) quedó en el registro de trabajo (fuera del repositorio); si se quiere repetir, preguntar antes (hay que vaciar las tablas de los dos servicios y borrar sus consumer groups, y los inválidos antiguos vuelven a entrar en los DLT). |

---

### Notas

- Cada ejecución crea datos de prueba nuevos (clientes, eventos y fechas). Desde la Fase 9 hay datos de demostración
  limpios (`scripts\verificacion\cargar-demo.ps1`): para capturas sin crear nada, el evento "Quince años de Lucía" ya tiene
  2 abonos, confirmación y un adicional en Kafka (ver [`evidencias-p2e1.md`](evidencias-p2e1.md#0-preparación-una-vez) para
  obtener su id en `$eid`). Las secciones 5 y 6 inyectan mensajes repetidos o inválidos, pero no cambian la demostración.
- `FlujoFase3` (paso 0) crea el evento "Prueba Quince años de Lucía" (con el prefijo "Prueba" desde la Fase 9E), distinto
  del de la demostración; la colección de Postman toma siempre el evento de demostración más antiguo.
- Índice de todas las guías: [`evidencias.md`](evidencias.md).
- Si un comando de logs no muestra nada, ampliar `--since` (por ejemplo `--since 30m`).
- `Psql`, `Api`, `LeerTopico`, `InyectarMensajes` y `LogsDesde` son utilidades de `scripts/verificacion/comun.ps1` (cargado en
  el paso 0); no muestran credenciales.
