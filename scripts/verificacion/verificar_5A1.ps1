# Verificación en Docker de la subfase 5A1: sobre común, tópicos con NewTopic, productores AFTER_COMMIT con
# clave = eventoId, consumidores migrados y persistencia de Kafka (volumen kafka_data).
# Requiere el entorno levantado (docker compose up -d). Guardar en UTF-8 con BOM.
# AL FINAL hace docker compose down y up (sin -v) para comprobar que Kafka conserva mensajes y offsets.
. "$PSScriptRoot\comun.ps1"

function EsUuid($v) { "$v" -match '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' }
function EsIso($v) { "$v" -match '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$' }
function SobreValido($m, [string]$Tipo, [string]$Origen) {
    $campos = @($m.Json.PSObject.Properties.Name | Sort-Object) -join ','
    ($campos -eq 'datos,eventoId,fechaOcurrencia,idEvento,servicioOrigen,tipo,version') -and (EsUuid $m.Json.idEvento) -and
        $m.Json.tipo -eq $Tipo -and $m.Json.version -eq 1 -and $m.Json.servicioOrigen -eq $Origen -and
        (EsIso $m.Json.fechaOcurrencia) -and $m.Json.eventoId -eq $m.Clave -and $m.Json.datos.eventoId -eq $m.Clave
}

AsegurarEntornoArrancado
CheckStatus 'Login' (Login) 200

Write-Host '== Tópicos declarados por su productor (3 particiones)'
$descripcion = Compose 'exec -T kafka kafka-topics --bootstrap-server localhost:9092 --describe' | Out-String
foreach ($t in 'eventos.confirmados', 'pagos.registrados', 'servicios-adicionales.agregados') {
    Check "Tópico $t con 3 particiones" ($descripcion -match "Topic: $([regex]::Escape($t))\s+TopicId: \S+\s+PartitionCount: 3") ''
}
Check 'Ya no existe el tópico antiguo servicios.adicionales' ($descripcion -notmatch 'Topic: servicios\.adicionales\s')

Write-Host '== Flujo completo: EVENTO_CONFIRMADO y PAGO_REGISTRADO'
$fechas = FechasLibres
$inicio = Get-Date
$flujo = FlujoFase3 $fechas[0] { param($e) @{ eventoId = $e.id } } $null
$eid = $flujo.EventoId
CheckStatus 'Agregar transporte adicional ×2' (Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'transporte-adicional'; cantidad = 2 }) 201
Start-Sleep -Seconds 3

$confirmados = @(LeerTopico 'eventos.confirmados' | Where-Object { $_.Clave -eq $eid })
Check 'eventos.confirmados: 1 mensaje del evento con clave = eventoId' ($confirmados.Count -eq 1) "mensajes=$($confirmados.Count)"
$c = $confirmados[0]
Check 'Sobre EVENTO_CONFIRMADO completo (7 campos, idEvento UUID, version 1, eventos-service, fecha ISO, eventoId = clave)' (SobreValido $c 'EVENTO_CONFIRMADO' 'eventos-service') "$($c.Valor)"
Check 'datos: nombre con tildes, tipo, fecha ISO, adultos, niños, invitados, zona' ($c.Json.datos.nombreEvento -eq $NombreEventoFlujo -and $c.Json.datos.tipoEvento -eq 'QUINCE_ANOS' -and $c.Json.datos.fecha -eq $fechas[0] -and $c.Json.datos.cantidadAdultos -eq 90 -and $c.Json.datos.cantidadNinos -eq 15 -and $c.Json.datos.numInvitados -eq 105 -and $c.Json.datos.zona -eq 'NEIVA' -and (EsUuid $c.Json.datos.clienteId)) "$($c.Valor)"

$pagos = @(LeerTopico 'pagos.registrados' | Where-Object { $_.Clave -eq $eid })
Check 'pagos.registrados: 2 mensajes (los 2 abonos) con clave = eventoId' ($pagos.Count -eq 2) "mensajes=$($pagos.Count)"
Check 'Sobres PAGO_REGISTRADO completos (finanzas-service)' (@($pagos | Where-Object { SobreValido $_ 'PAGO_REGISTRADO' 'finanzas-service' }).Count -eq 2)
$ids = @($flujo.Pagos | ForEach-Object { $_.pagoId })
Check 'datos: pagoId de cada abono, monto, moneda, clave de idempotencia y fecha ISO' (@($pagos | Where-Object { $ids -contains $_.Json.datos.pagoId -and $_.Json.datos.moneda -eq 'COP' -and $_.Json.datos.monto -gt 0 -and $_.Json.datos.claveIdempotencia -and (EsIso $_.Json.datos.fechaPago) }).Count -eq 2)
Check 'Los 2 abonos del evento van a la misma partición' (@($pagos.Particion | Sort-Object -Unique).Count -eq 1)
Check 'Cada mensaje tiene su propio idEvento' (@($pagos.Json.idEvento | Sort-Object -Unique).Count -eq 2)

Write-Host '== SERVICIO_ADICIONAL_AGREGADO después del commit'
$adicionales = @(LeerTopico 'servicios-adicionales.agregados' | Where-Object { $_.Clave -eq $eid })
Check 'servicios-adicionales.agregados: 1 mensaje con clave = eventoId' ($adicionales.Count -eq 1) "mensajes=$($adicionales.Count)"
$a = $adicionales[0]
Check 'Sobre SERVICIO_ADICIONAL_AGREGADO completo (finanzas-service)' (SobreValido $a 'SERVICIO_ADICIONAL_AGREGADO' 'finanzas-service') "$($a.Valor)"
Check 'datos: código, nombre, cantidad, valor unitario, subtotal y moneda' ($a.Json.datos.codigo -eq 'transporte-adicional' -and $a.Json.datos.nombre -eq 'Transporte adicional' -and $a.Json.datos.cantidad -eq 2 -and [decimal]$a.Json.datos.valorUnitario -eq 180000 -and [decimal]$a.Json.datos.subtotal -eq 360000 -and $a.Json.datos.moneda -eq 'COP' -and (EsUuid $a.Json.datos.adicionalId))

Write-Host '== Kafdrop muestra los mensajes'
function VisibleEnKafdrop([string]$Topico, $Mensaje) {
    # Kafdrop puede tardar unos segundos en ver un tópico recién creado: hasta 3 intentos.
    # count como máximo 100: con valores mayores Kafdrop 4.0.1 devuelve la página sin mensajes.
    for ($i = 0; $i -lt 3; $i++) {
        $pagina = Invoke-WebRequest -UseBasicParsing "http://localhost:9000/topic/$Topico/messages?partition=$($Mensaje.Particion)&offset=0&count=100&keyFormat=DEFAULT&format=DEFAULT"
        if ($pagina.StatusCode -eq 200 -and $pagina.Content -match $Mensaje.Json.idEvento) { return $true }
        Start-Sleep -Seconds 5
    }
    $false
}
foreach ($par in @(@('eventos.confirmados', $c), @('pagos.registrados', $pagos[0]), @('servicios-adicionales.agregados', $a))) {
    Check "Kafdrop: $($par[1].Json.tipo) visible en $($par[0]) (idEvento $($par[1].Json.idEvento))" ($par[1] -and (VisibleEnKafdrop $par[0] $par[1])) ''
}

Write-Host '== Logs de productores y consumidores'
$logE = LogsDesde 'eventos-service' $inicio
Check "Eventos: EVENTO_CONFIRMADO publicado con idEvento y partición" ($logE -match "EVENTO_CONFIRMADO publicado\. idEvento=$($c.Json.idEvento), eventoId=$eid, topic=eventos\.confirmados, partition=$($c.Particion)") ''
$logF = LogsDesde 'finanzas-service' $inicio
Check 'Finanzas: PAGO_REGISTRADO y SERVICIO_ADICIONAL_AGREGADO publicados' ($logF -match "PAGO_REGISTRADO publicado\. idEvento=.*eventoId=$eid" -and $logF -match "SERVICIO_ADICIONAL_AGREGADO publicado\. idEvento=$($a.Json.idEvento), eventoId=$eid, topic=servicios-adicionales\.agregados") ''
$logL = LogsDesde 'logistica-service' $inicio
# Desde la Fase 5B, línea fija del consumidor: "Consumo Kafka. grupo=..., topic=..., ..., idEvento=..., tipo=..., eventoId=..., resultado=..."
Check 'Logística leyó el sobre (tipo e idEvento) y calculó la logística' ($logL -match "Consumo Kafka\. grupo=logistica-group, topic=eventos\.confirmados, particion=\d+, offset=\d+, idEvento=$($c.Json.idEvento), tipo=EVENTO_CONFIRMADO, eventoId=$eid, resultado=PROCESADO") ''
$logC = LogsDesde 'contratos-service' $inicio
Check 'Contratos consumió el sobre sin errores' ($logC -cnotmatch '\sERROR\s') ''
$log = Api GET "$($Url.logistica)/eventos/$eid/logistica"
# Desde la Fase 5B el "transporte adicional ×2" agregado arriba recalcula la logística: 2 camiones base + 2 = 4.
Check 'Logística del evento: 7 meseros, 4 de cocina, 2 + 2 camiones (105 invitados + transporte adicional ×2)' ($log.Status -eq 200 -and $log.Body.meserosRequeridos -eq 7 -and $log.Body.personalCocinaRequerido -eq 4 -and $log.Body.camionesTransporte -eq 4) "$($log.Texto)"

Write-Host '== Adicional con Kafka caído: 201 (ya no 503)'
Compose 'stop kafka' | Out-Null
try {
    $desde = Get-Date
    $adK = Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'hora-extra'; cantidad = 1 }
    CheckStatus 'Agregar adicional con Kafka detenido' $adK 201
    Check 'El adicional quedó en la BD' ($adK.Body.id -and (Psql "SELECT count(*) FROM adicionales_evento WHERE id = '$($adK.Body.id)';").Trim() -eq '1')
    Start-Sleep -Seconds 35
    Check 'Log: FALLÓ la publicación de SERVICIO_ADICIONAL_AGREGADO (el outbox de la 5A2 lo reenviará)' ((LogsDesde 'finanzas-service' $desde) -match 'FALLÓ la publicación de SERVICIO_ADICIONAL_AGREGADO') ''
} finally {
    # Siempre se vuelve a arrancar, aunque el script falle: un Kafka detenido arruina la siguiente ejecución.
    Compose 'start kafka' | Out-Null
}
Check 'kafka vuelve a healthy' ((EsperarHealthy 'kafka' 180) -eq 'healthy')

Write-Host '== Persistencia de Kafka: down -> up (sin -v)'
$antesDown = @(LeerTopico 'eventos.confirmados').Count
$offsetsAntes = Compose 'exec -T kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group logistica-group' | Out-String
Check 'Antes del down: hay mensajes y offsets de logistica-group' ($antesDown -ge 1 -and $offsetsAntes -match 'eventos\.confirmados') "mensajes=$antesDown"
Compose 'down' | Out-Null
# Volumen del proyecto de Compose actual (nombre de la carpeta normalizado, o COMPOSE_PROJECT_NAME): en la 9E convivían el
# del usuario (maxi_eventos_kafka_data) y el de la copia de la prueba final.
$proyecto = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { (Split-Path $script:Raiz -Leaf).ToLower() -replace '[^a-z0-9_-]', '' }
Check "El volumen ${proyecto}_kafka_data sigue después del down" (@(docker volume ls --format '{{.Name}}' | Where-Object { $_ -eq "${proyecto}_kafka_data" }).Count -eq 1)
Compose 'up -d' | Out-Null
$todos = 'auth-service', 'clientes-service', 'eventos-service', 'lugares-service', 'finanzas-service', 'contratos-service', 'proveedores-service', 'logistica-service'
foreach ($s in $todos) { EsperarHealthy $s 240 | Out-Null }
$despues = @(LeerTopico 'eventos.confirmados')
Check 'Después del up: los mensajes de eventos.confirmados siguen ahí' ($despues.Count -eq $antesDown -and @($despues | Where-Object { $_.Json.idEvento -eq $c.Json.idEvento }).Count -eq 1) "antes=$antesDown después=$($despues.Count)"
$offsetsDespues = Compose 'exec -T kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group logistica-group' | Out-String
$lag = [regex]::Matches($offsetsDespues, 'eventos\.confirmados\s+\d+\s+(\d+|-)\s+(\d+)\s+(\d+|-)') | ForEach-Object { $_.Groups[3].Value }
# LAG "-" = partición sin mensajes
Check 'Offsets de logistica-group conservados (LAG 0: no reprocesa)' (@($lag | Where-Object { $_ -eq '0' }).Count -ge 1 -and @($lag | Where-Object { $_ -ne '0' -and $_ -ne '-' }).Count -eq 0) "$offsetsDespues"
$desdeUp = (Get-Date).AddMinutes(-5)
# Tras el up solo puede llegar el adicional "hora-extra" creado arriba con Kafka detenido (el outbox lo tenía PENDIENTE y lo
# publica al volver): es un mensaje nuevo. Ningún mensaje antiguo se vuelve a entregar (ni eventos.confirmados ni duplicados).
$tras = @([regex]::Matches((LogsDesde 'logistica-service' $desdeUp), 'Consumo Kafka\.[^\r\n]*') | ForEach-Object { $_.Value })
$ajenos = @($tras | Where-Object { $_ -notmatch "topic=servicios-adicionales\.agregados, [^\r\n]*eventoId=$eid, resultado=PROCESADO, detalle=adicional hora-extra" })
Check 'Logística no volvió a procesar mensajes antiguos (solo el adicional que el outbox tenía pendiente)' ($ajenos.Count -eq 0) "$($ajenos -join ' | ')"
Check 'Los tópicos siguen con 3 particiones' ((Compose 'exec -T kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic pagos.registrados' | Out-String) -match 'PartitionCount: 3')
Login | Out-Null
CheckStatus 'La API responde tras el reinicio (logística del evento)' (Api GET "$($Url.logistica)/eventos/$eid/logistica") 200

Resumen 'Verificación 5A1'
