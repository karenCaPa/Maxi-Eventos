# Verificación en Docker de la subfase 5B: consumidores completos (evidencias de la entrega P2 · E2).
# Duplicado ignorado, mensajes inválidos e ilegibles al DLT sin reintentos, error transitorio con reintentos limitados,
# recálculo de logística por un adicional, adicional antes del contrato, anexo con Finanzas detenido.
# Requiere el entorno levantado. Detiene y arranca postgres-db y finanzas-service. Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

function Sobre($IdEvento, [string]$Tipo, $EventoId, $Datos) {
    [ordered]@{ idEvento = $IdEvento; tipo = $Tipo; version = 1; fechaOcurrencia = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); servicioOrigen = 'verificacion'; eventoId = $EventoId; datos = $Datos } | ConvertTo-Json -Compress -Depth 5
}
function Lineas([string]$Log, [string]$Patron) { @([regex]::Matches($Log, $Patron)).Count }
# Mensajes de un DLT rechazados por un grupo concreto: desde la 5C auditoria-group y notificaciones-group también
# escriben en los mismos DLT (cada grupo envía su copia), así que el total del tópico ya no sirve para comprobarlo.
function DltDeGrupo([string]$Topico, [string]$Grupo) {
    @([regex]::Matches((TextoTopicoConCabeceras $Topico), "kafka_dlt-original-consumer-group:$Grupo(,|\s|$)")).Count
}
function Consumo([string]$Grupo, [string]$IdEvento, [string]$Resultado) {
    "Consumo Kafka\. grupo=$Grupo, topic=[^,]+, particion=\d+, offset=\d+, idEvento=$IdEvento, tipo=[^,]+, eventoId=[^,]+, resultado=$Resultado"
}

CheckStatus 'Login' (Login) 200
$fechas = FechasLibres
$inicio = Get-Date
$redisIdemAntes = (Redis 'redis-cli --scan --pattern "idempotency:*" | wc -l').Trim()

Write-Host '== 1. Recálculo: adicional de transporte sobre un evento YA confirmado'
$flujo = FlujoFase3 $fechas[0] { param($e) @{ eventoId = $e.id } } $null
$eid = $flujo.EventoId
$antes = Api GET "$($Url.logistica)/eventos/$eid/logistica"
Check 'Logística inicial (105 invitados): 7 meseros, 4 de cocina, 2 camiones (queda en caché)' ($antes.Body.camionesTransporte -eq 2 -and $antes.Body.meserosRequeridos -eq 7) "$($antes.Texto)"
CheckStatus 'GET logística otra vez (sale de la caché)' (Api GET "$($Url.logistica)/eventos/$eid/logistica") 200
$desdeAd = Get-Date
$ad = Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'transporte-adicional'; cantidad = 1 }
CheckStatus 'Agregar transporte adicional ×1 (evento confirmado)' $ad 201
Start-Sleep -Seconds 4
$mAd = @(LeerTopico 'servicios-adicionales.agregados' | Where-Object { $_.Clave -eq $eid -and $_.Json.datos.adicionalId -eq $ad.Body.id })[0]
$idAd = $mAd.Json.idEvento
$logL = LogsDesde 'logistica-service' $desdeAd
$logC = LogsDesde 'contratos-service' $desdeAd
Check 'Log Logística: PROCESADO con el recálculo 7/4/2 -> 7/4/3' ($logL -match ((Consumo 'logistica-group' $idAd 'PROCESADO') + ', detalle=logística recalculada por transporte-adicional x1: meseros/cocina/camiones 7/4/2 -> 7/4/3')) ''
Check 'Log Contratos: PROCESADO (guardado para el anexo)' ($logC -match ((Consumo 'contratos-group' $idAd 'PROCESADO') + ', detalle=adicional transporte-adicional x1 guardado para el anexo')) ''
Check 'BD: logistica_eventos.camiones_transporte = 3' ((Psql "SELECT camiones_transporte FROM logistica_eventos WHERE evento_id = '$eid';").Trim() -eq '3')
$despues = Api GET "$($Url.logistica)/eventos/$eid/logistica"
Check 'GET logística devuelve 3 camiones (no el valor viejo de la caché)' ($despues.Body.camionesTransporte -eq 3) "$($despues.Texto)"
$logCache = LogsDesde 'logistica-service' $desdeAd
Check 'Log de caché: EVICT por el recálculo y MISS -> base de datos en el GET siguiente' ($logCache -match "Caché EVICT cache=logistica-requerimientos clave=$eid" -and $logCache -match "Caché MISS  cache=logistica-requerimientos clave=$eid -> se consulta la base de datos") ''
$hora = Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'hora-extra'; cantidad = 2 }
CheckStatus 'Agregar hora extra ×2 (sin efecto en logística, regla del application.yml)' $hora 201
Start-Sleep -Seconds 4
Check 'Log Logística: hora-extra PROCESADO sin efecto' ((LogsDesde 'logistica-service' $desdeAd) -match 'resultado=PROCESADO, detalle=adicional hora-extra sin efecto en logística') ''
Check 'BD: los camiones siguen en 3' ((Psql "SELECT camiones_transporte FROM logistica_eventos WHERE evento_id = '$eid';").Trim() -eq '3')

Write-Host '== 2. Duplicado: el mismo mensaje (mismo idEvento) inyectado otra vez'
$antesDup = (Psql "SELECT (SELECT camiones_transporte FROM logistica_eventos WHERE evento_id = '$eid') || '|' || (SELECT count(*) FROM logistica_adicionales WHERE evento_id = '$eid') || '|' || (SELECT count(*) FROM contrato_adicionales WHERE evento_id = '$eid') || '|' || (SELECT count(*) FROM logistica_eventos_procesados WHERE id_evento = '$idAd') || '|' || (SELECT count(*) FROM contratos_eventos_procesados WHERE id_evento = '$idAd');").Trim()
$desdeDup = Get-Date
InyectarMensajes 'servicios-adicionales.agregados' @("$($mAd.Clave)#$($mAd.Valor)")
Start-Sleep -Seconds 5
$logL2 = LogsDesde 'logistica-service' $desdeDup
$logC2 = LogsDesde 'contratos-service' $desdeDup
Check 'Logística: DUPLICADO_IGNORADO para ese idEvento' ((Lineas $logL2 (Consumo 'logistica-group' $idAd 'DUPLICADO_IGNORADO')) -eq 1) ''
Check 'Contratos: DUPLICADO_IGNORADO para ese idEvento' ((Lineas $logC2 (Consumo 'contratos-group' $idAd 'DUPLICADO_IGNORADO')) -eq 1) ''
$logTodo = (LogsDesde 'logistica-service' $inicio) + (LogsDesde 'contratos-service' $inicio)
Check 'En total: 1 PROCESADO y 1 DUPLICADO_IGNORADO por grupo para ese idEvento' ((Lineas $logTodo (Consumo 'logistica-group' $idAd 'PROCESADO')) -eq 1 -and (Lineas $logTodo (Consumo 'logistica-group' $idAd 'DUPLICADO_IGNORADO')) -eq 1 -and (Lineas $logTodo (Consumo 'contratos-group' $idAd 'PROCESADO')) -eq 1 -and (Lineas $logTodo (Consumo 'contratos-group' $idAd 'DUPLICADO_IGNORADO')) -eq 1) ''
$despuesDup = (Psql "SELECT (SELECT camiones_transporte FROM logistica_eventos WHERE evento_id = '$eid') || '|' || (SELECT count(*) FROM logistica_adicionales WHERE evento_id = '$eid') || '|' || (SELECT count(*) FROM contrato_adicionales WHERE evento_id = '$eid') || '|' || (SELECT count(*) FROM logistica_eventos_procesados WHERE id_evento = '$idAd') || '|' || (SELECT count(*) FROM contratos_eventos_procesados WHERE id_evento = '$idAd');").Trim()
Check 'BD sin cambios en el segundo: camiones|adicionales logística|adicionales contrato|procesados logística|procesados contratos' ($antesDup -eq $despuesDup -and $despuesDup -eq '3|2|2|1|1') "antes=$antesDup después=$despuesDup"
# Las claves que quedan son de fases anteriores (TTL de 24 h): lo que se comprueba es que los consumidores ya no crean nuevas.
Check 'Redis: los consumidores ya no crean claves de idempotencia (el conteo no cambió)' ((Redis 'redis-cli --scan --pattern "idempotency:*" | wc -l').Trim() -eq $redisIdemAntes) "antes=$redisIdemAntes"

Write-Host '== 3. Mensajes inválidos e ilegibles -> DLT sin reintentos'
$s = Sufijo
$x1 = [guid]::NewGuid().ToString(); $x2 = [guid]::NewGuid().ToString(); $falso = [guid]::NewGuid().ToString()
$marcaIlegible = "esto-no-es-json-$s"
$dltConfAntes = DltDeGrupo 'eventos.confirmados.DLT' 'logistica-group'
$dltAdAntesL = DltDeGrupo 'servicios-adicionales.agregados.DLT' 'logistica-group'
$dltAdAntesC = DltDeGrupo 'servicios-adicionales.agregados.DLT' 'contratos-group'
$desdeInv = Get-Date
InyectarMensajes 'eventos.confirmados' @(
    "sin-evento-$s#$(Sobre $x1 'EVENTO_CONFIRMADO' $null @{ nombreEvento = 'Sin eventoId' })",
    "$falso#$(Sobre $x2 'EVENTO_DESCONOCIDO' $falso @{ eventoId = $falso })")
InyectarMensajes 'servicios-adicionales.agregados' @("$falso#{$marcaIlegible")
Start-Sleep -Seconds 8
$logL3 = LogsDesde 'logistica-service' $desdeInv
$logC3 = LogsDesde 'contratos-service' $desdeInv
Check 'Sobre sin eventoId -> INVALIDO_A_DLT (una vez, detalle: Sobre incompleto)' ((Lineas $logL3 ("idEvento=$x1, tipo=EVENTO_CONFIRMADO, eventoId=-, resultado=INVALIDO_A_DLT, detalle=MensajeInvalidoException: Sobre incompleto")) -eq 1) ''
Check 'Tipo desconocido -> INVALIDO_A_DLT (una vez)' ((Lineas $logL3 ("idEvento=$x2, tipo=EVENTO_DESCONOCIDO, eventoId=$falso, resultado=INVALIDO_A_DLT, detalle=MensajeInvalidoException: Tipo de mensaje no esperado")) -eq 1) ''
Check 'Sin reintentos para los inválidos (ninguna línea REINTENTO)' ($logL3 -notmatch "idEvento=($x1|$x2)[^\r\n]*resultado=REINTENTO") ''
$patronIlegible = 'topic=servicios-adicionales\.agregados, particion=\d+, offset=\d+, idEvento=-, tipo=-, eventoId=-, resultado=INVALIDO_A_DLT'
Check 'JSON ilegible -> INVALIDO_A_DLT en Logística y en Contratos (idEvento=-), una vez cada uno, sin reintentos' ((Lineas $logL3 $patronIlegible) -eq 1 -and (Lineas $logC3 $patronIlegible) -eq 1 -and ($logL3 + $logC3) -notmatch 'idEvento=-[^\r\n]*resultado=REINTENTO') ''
Check 'eventos.confirmados.DLT: +2 mensajes de logistica-group' ((DltDeGrupo 'eventos.confirmados.DLT' 'logistica-group') - $dltConfAntes -eq 2) "antes=$dltConfAntes"
Check 'servicios-adicionales.agregados.DLT: el ilegible +1 de logistica-group y +1 de contratos-group' (((DltDeGrupo 'servicios-adicionales.agregados.DLT' 'logistica-group') - $dltAdAntesL -eq 1) -and ((DltDeGrupo 'servicios-adicionales.agregados.DLT' 'contratos-group') - $dltAdAntesC -eq 1)) "antes=$dltAdAntesL/$dltAdAntesC"
$dltConf = TextoTopicoConCabeceras 'eventos.confirmados.DLT'
$dltAd = TextoTopicoConCabeceras 'servicios-adicionales.agregados.DLT'
Check 'DLT con el mensaje original y la cabecera kafka_dlt-original-consumer-group:logistica-group' ($dltConf -match $x1 -and $dltConf -match $x2 -and $dltConf -match 'kafka_dlt-original-consumer-group:logistica-group') ''
Check 'DLT del ilegible: los bytes originales, con logistica-group y contratos-group' ($dltAd -match [regex]::Escape("{$marcaIlegible") -and $dltAd -match 'kafka_dlt-original-consumer-group:logistica-group' -and $dltAd -match 'kafka_dlt-original-consumer-group:contratos-group') ''
$visible = $false
foreach ($p in 0..2) {
    $pagina = Invoke-WebRequest -UseBasicParsing "http://localhost:9000/topic/eventos.confirmados.DLT/messages?partition=$p&offset=0&count=100&keyFormat=DEFAULT&format=DEFAULT"
    if ($pagina.Content -match $x1 -and $pagina.Content -match 'kafka_dlt-original-consumer-group') { $visible = $true }
}
Check 'Kafdrop: el inválido visible en eventos.confirmados.DLT con las cabeceras del DLT' $visible ''

Write-Host '== 4. Error transitorio (BD no disponible) -> reintentos limitados y DLT, sin bucle infinito'
$x3 = [guid]::NewGuid().ToString(); $e3 = [guid]::NewGuid().ToString()
$valido = Sobre $x3 'EVENTO_CONFIRMADO' $e3 ([ordered]@{ eventoId = $e3; nombreEvento = 'Prueba de BD caída'; tipoEvento = 'BODA'; fecha = '2027-03-03'; cantidadAdultos = 50; cantidadNinos = 0; numInvitados = 50; zona = 'NEIVA' })
$dltConfAntes2 = DltDeGrupo 'eventos.confirmados.DLT' 'logistica-group'
Compose 'stop postgres-db' | Out-Null
try {
    $desdeBd = Get-Date
    InyectarMensajes 'eventos.confirmados' @("$e3#$valido")
    $fin = (Get-Date).AddSeconds(90)
    do { Start-Sleep -Seconds 5; $logBd = LogsDesde 'logistica-service' $desdeBd } while ($logBd -notmatch "idEvento=$x3[^\r\n]*REINTENTOS_AGOTADOS_A_DLT" -and (Get-Date) -lt $fin)
} finally {
    Compose 'start postgres-db' | Out-Null
}
Check 'REINTENTO 1 y REINTENTO 2 (BD no disponible)' ((Lineas $logBd (Consumo 'logistica-group' $x3 'REINTENTO 1')) -eq 1 -and (Lineas $logBd (Consumo 'logistica-group' $x3 'REINTENTO 2')) -eq 1) ''
Check 'Después del 3.er intento: REINTENTOS_AGOTADOS_A_DLT (una vez)' ((Lineas $logBd (Consumo 'logistica-group' $x3 'REINTENTOS_AGOTADOS_A_DLT')) -eq 1) ''
Check 'La causa registrada es de conexión a la BD' ($logBd -match "idEvento=$x3[^\r\n]*REINTENTO 1, detalle=[^\r\n]*(Connection|connect|JDBC|acquire|CannotCreateTransaction|UnknownHost|postgres)") ''
Check 'eventos.confirmados.DLT: +1 de logistica-group (el mensaje válido que no se pudo procesar)' ((DltDeGrupo 'eventos.confirmados.DLT' 'logistica-group') - $dltConfAntes2 -eq 1)
Check 'postgres-db vuelve a healthy' ((EsperarHealthy 'postgres-db' 120) -eq 'healthy')
AsegurarEntornoArrancado
Start-Sleep -Seconds 15
$logDespues = LogsDesde 'logistica-service' $desdeBd
Check 'Sin bucle infinito: después del DLT no hay más intentos para ese mensaje' ((Lineas $logDespues "idEvento=$x3") -eq 3) "líneas=$(Lineas $logDespues "idEvento=$x3")"
Login | Out-Null
CheckStatus 'El evento del mensaje no tiene logística (quedó en el DLT, no se procesó)' (Api GET "$($Url.logistica)/eventos/$e3/logistica") 404

Write-Host '== 5. Adicional ANTES de que exista el contrato (y antes de confirmar)'
$c5 = CrearCliente 'Prueba Tomás Ñúñez Peña'
CheckStatus 'Crear cliente' $c5 201
$ev5 = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Quince años de Inés'; clienteId = $c5.Body.id; fecha = $fechas[1]; tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 90; cantidadNinos = 15; zona = 'NEIVA' }
CheckStatus 'Crear evento' $ev5 201
$e5 = $ev5.Body.id
$cot5 = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $e5 }
CheckStatus 'Cotizar' $cot5 201
$desde5 = Get-Date
CheckStatus 'Agregar transporte adicional ×2 SIN contrato ni confirmación' (Api POST "$($Url.finanzas)/eventos/$e5/adicionales" @{ codigo = 'transporte-adicional'; cantidad = 2 }) 201
Start-Sleep -Seconds 4
Check 'Contratos: PROCESADO, guardado aunque el evento aún no tiene contrato (no va al DLT)' ((LogsDesde 'contratos-service' $desde5) -match "eventoId=$e5, resultado=PROCESADO, detalle=adicional transporte-adicional x2 guardado para el anexo \(el evento aún no tiene contrato") ''
Check 'Logística: PROCESADO, se aplicará al confirmar' ((LogsDesde 'logistica-service' $desde5) -match "eventoId=$e5, resultado=PROCESADO, detalle=el evento aún no tiene logística; el adicional transporte-adicional se aplicará al confirmarlo") ''
CheckStatus 'Crear el contrato después' (Api POST "$($Url.contratos)/eventos/$e5/contrato") 201
CheckStatus 'Firmar' (Api PATCH "$($Url.contratos)/eventos/$e5/contrato/marcar-firmado") 200
CheckStatus 'Abonar la reserva' (Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $e5; clienteId = $c5.Body.id; metodoPago = 'TRANSFERENCIA'; monto = [decimal]$cot5.Body.montoReservaRequerido; moneda = 'COP' }) 201
CheckStatus 'Confirmar' (Api PATCH "$($Url.eventos)/eventos/$e5/confirmar") 200
$log5 = $null
for ($i = 0; $i -lt 10 -and (-not $log5 -or $log5.Status -ne 200); $i++) { Start-Sleep -Seconds 2; $log5 = Api GET "$($Url.logistica)/eventos/$e5/logistica" }
Check 'Al confirmar se aplicó el adicional que llegó antes: 2 + 2 = 4 camiones' ($log5.Status -eq 200 -and $log5.Body.camionesTransporte -eq 4) "$($log5.Texto)"
Check 'Log Logística: EVENTO_CONFIRMADO PROCESADO con adicionalesAplicados=1' ((LogsDesde 'logistica-service' $desde5) -match "tipo=EVENTO_CONFIRMADO, eventoId=$e5, resultado=PROCESADO, detalle=meseros=7, cocina=4, camiones=4, compras=4, adicionalesAplicados=1") ''
$anexo5 = DescargarPdf "$($Url.contratos)/eventos/$e5/adicionales/anexo-pdf" 'anexo-5B'
# Fase 11 (PDF rediseñado tras la Fase 10): los adicionales van en una tabla; la fila 1 es la que sigue a la cabecera
$fila1 = '(?m)^\s*Servicio\s*Cantidad\s*Valor unitario\s*Subtotal[ \t]*\r?\n' + (PatronFilaAdicional 'Transporte adicional' 2 '180.000,00 COP' '360.000,00 COP').Substring(4)
Check 'El anexo (contrato creado después) incluye el adicional: fila 1 "Transporte adicional · 2 · 180.000,00 COP · 360.000,00 COP"' ($anexo5.Status -eq 200 -and $anexo5.Texto -match $fila1) "$($anexo5.Status)"

Write-Host '== 6. Anexo generado desde contrato_adicionales con Finanzas detenido'
Compose 'stop finanzas-service' | Out-Null
try {
    $anexoSinF = DescargarPdf "$($Url.contratos)/eventos/$eid/adicionales/anexo-pdf" 'anexo-5B-sin-finanzas'
    CheckStatus 'Anexo con Finanzas detenido' $anexoSinF 200
    $filaT = [regex]::Match($anexoSinF.Texto, (PatronFilaAdicional 'Transporte adicional' 1 '180.000,00 COP' '180.000,00 COP'))
    $filaH = [regex]::Match($anexoSinF.Texto, (PatronFilaAdicional 'Hora adicional de servicio' 2 '120.000,00 COP' '240.000,00 COP'))
    Check 'Anexo: transporte ×1 y hora extra ×2 (desde contrato_adicionales, en ese orden), cliente con tildes' ($filaT.Success -and $filaH.Success -and $filaH.Index -gt $filaT.Index -and $anexoSinF.Texto -match (PatronDato 'Cliente' $NombreClienteFlujo))
    Check 'BD: contrato_adicionales del evento = 2 (el duplicado no se guardó)' ((Psql "SELECT count(*) FROM contrato_adicionales WHERE evento_id = '$eid';").Trim() -eq '2')
} finally {
    Compose 'start finanzas-service' | Out-Null
}
Check 'finanzas-service vuelve a healthy' ((EsperarHealthy 'finanzas-service') -eq 'healthy')

Write-Host '== 7. Contratos ya no consume eventos.confirmados; tablas'
Check 'Contratos no tiene consumo de eventos.confirmados en esta verificación' ((LogsDesde 'contratos-service' $inicio) -notmatch 'grupo=contratos-group, topic=eventos\.confirmados') ''
# La tabla antigua sigue existiendo (en la base de desarrollo con sus filas; en una base nueva, vacía) y no recibe filas nuevas.
Check 'processed_events se conserva (no se borró) y ya no recibe filas nuevas' ((Psql "SELECT count(*) FROM processed_events WHERE procesado_en >= '$($inicio.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))';").Trim() -eq '0' -and (Psql "SELECT to_regclass('processed_events') IS NOT NULL;").Trim() -eq 't')

Resumen 'Verificación 5B'
