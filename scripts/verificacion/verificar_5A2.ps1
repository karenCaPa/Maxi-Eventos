# Verificación en Docker de la subfase 5A2: outbox (eventos_mensajes_salientes y finanzas_mensajes_salientes).
# Kafka caído más de 30 s: pago, adicional y confirmación responden con éxito, las filas quedan PENDIENTE y al volver Kafka
# se publican solas (mismo idEvento, estado PUBLICADO), Logística procesa una sola vez. Requiere el entorno levantado.
# Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

CheckStatus 'Login' (Login) 200
$fechas = FechasLibres

Write-Host '== Envío normal (Kafka arriba): la fila pasa a PUBLICADO en el primer intento'
$c = CrearCliente 'Prueba Ángela Muñoz Peña'
CheckStatus 'Crear cliente' $c 201
$ev = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Boda de Ángela y Tomás'; clienteId = $c.Body.id; fecha = $fechas[0]; tipoEvento = 'BODA'; cantidadAdultos = 120; cantidadNinos = 12; zona = 'RURAL' }
CheckStatus 'Crear evento' $ev 201
$eid = $ev.Body.id
$cot = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $eid }
CheckStatus 'Cotizar' $cot 201
CheckStatus 'Crear contrato' (Api POST "$($Url.contratos)/eventos/$eid/contrato") 201
CheckStatus 'Firmar contrato' (Api PATCH "$($Url.contratos)/eventos/$eid/contrato/marcar-firmado") 200
$reserva = [decimal]$cot.Body.montoReservaRequerido
CheckStatus 'Abono de la reserva (Kafka arriba)' (Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $eid; clienteId = $c.Body.id; metodoPago = 'TRANSFERENCIA'; monto = $reserva; moneda = 'COP' }) 201
$normal = EsperarEstadoOutbox 'finanzas_mensajes_salientes' $eid 'PAGO_REGISTRADO' 'PUBLICADO' 20
Check 'Outbox: el abono quedó PUBLICADO en el primer intento' ($normal.Estado -eq 'PUBLICADO' -and $normal.Intentos -eq 1) "$($normal | ConvertTo-Json -Compress)"

Write-Host '== Kafka detenido más de 30 s'
Compose 'stop kafka' | Out-Null
try {
    $desde = Get-Date
    $tiempos = @{}
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    $pago = Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $eid; clienteId = $c.Body.id; metodoPago = 'EFECTIVO'; monto = 500000; moneda = 'COP' }
    $tiempos.pago = $reloj.Elapsed.TotalSeconds; $reloj.Restart()
    $adicional = Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'decoracion-premium'; cantidad = 1 }
    $tiempos.adicional = $reloj.Elapsed.TotalSeconds; $reloj.Restart()
    $confirmar = Api PATCH "$($Url.eventos)/eventos/$eid/confirmar"
    $tiempos.confirmar = $reloj.Elapsed.TotalSeconds
    CheckStatus 'Pago con Kafka detenido' $pago 201
    CheckStatus 'Adicional con Kafka detenido' $adicional 201
    CheckStatus 'Confirmar con Kafka detenido' $confirmar 200
    Check 'Evento RESERVADO' ($confirmar.Body.estado -eq 'RESERVADO')
    Check 'Ninguna respuesta tarda más de 12 s' (@($tiempos.Values | Where-Object { $_ -gt 12 }).Count -eq 0) (($tiempos.GetEnumerator() | ForEach-Object { "{0}={1:N1}s" -f $_.Key, $_.Value }) -join ' ')
    # 120 s: el intento inmediato falla a los 5 s (sin metadatos) o a los 30 s (delivery.timeout.ms); la fila es candidata
    # al reenvío 30 s después; la tarea pasa cada 15 s y su envío puede esperar otros 30 s en el búfer del productor
    # (si Kafka vuelve en ese plazo, se publica). A los 120 s hubo con seguridad al menos un reenvío FALLIDO.
    Write-Host '   (esperando 120 s con Kafka detenido)'
    Start-Sleep -Seconds 120
    $fPago = FilaOutbox 'finanzas_mensajes_salientes' $eid 'PAGO_REGISTRADO'
    $fAdic = FilaOutbox 'finanzas_mensajes_salientes' $eid 'SERVICIO_ADICIONAL_AGREGADO'
    $fConf = FilaOutbox 'eventos_mensajes_salientes' $eid 'EVENTO_CONFIRMADO'
    foreach ($f in @(@('PAGO_REGISTRADO', $fPago), @('SERVICIO_ADICIONAL_AGREGADO', $fAdic), @('EVENTO_CONFIRMADO', $fConf))) {
        # PENDIENTE, o ENVIANDO si en ese momento la tarea de reenvío lo está reintentando: lo importante es que no está
        # publicado y tiene el error del intento fallido.
        Check "Outbox: $($f[0]) sin publicar (PENDIENTE o en reintento) con intentos y error registrados" ($f[1] -and @('PENDIENTE', 'ENVIANDO') -contains $f[1].Estado -and $f[1].Intentos -ge 1 -and $f[1].UltimoError) "$($f[1] | ConvertTo-Json -Compress)"
    }
    Check 'Outbox: la fila del pago es la del abono nuevo (no la del primero)' ($fPago.IdEvento -ne $normal.IdEvento)
    $logF = LogsDesde 'finanzas-service' $desde
    $logE = LogsDesde 'eventos-service' $desde
    Check 'Log: FALLÓ ... queda PENDIENTE en el outbox (pago y adicional)' ($logF -match "FALLÓ la publicación de PAGO_REGISTRADO \(queda PENDIENTE en el outbox.*idEvento=$($fPago.IdEvento)" -and $logF -match "FALLÓ la publicación de SERVICIO_ADICIONAL_AGREGADO \(queda PENDIENTE en el outbox.*idEvento=$($fAdic.IdEvento)") ''
    Check 'Log: FALLÓ ... queda PENDIENTE en el outbox (confirmación)' ($logE -match "FALLÓ la publicación de EVENTO_CONFIRMADO \(queda PENDIENTE en el outbox.*idEvento=$($fConf.IdEvento)") ''
    $sinLogistica = Api GET "$($Url.logistica)/eventos/$eid/logistica"
    Check 'Logística aún no existe (el mensaje no llegó)' ($sinLogistica.Status -eq 404)
} finally {
    Compose 'start kafka' | Out-Null
}
Check 'kafka vuelve a healthy' ((EsperarHealthy 'kafka' 180) -eq 'healthy')

Write-Host '== Al volver Kafka, el outbox publica solo'
$pPago = EsperarEstadoOutbox 'finanzas_mensajes_salientes' $eid 'PAGO_REGISTRADO' 'PUBLICADO' 120
$pAdic = EsperarEstadoOutbox 'finanzas_mensajes_salientes' $eid 'SERVICIO_ADICIONAL_AGREGADO' 'PUBLICADO' 60
$pConf = EsperarEstadoOutbox 'eventos_mensajes_salientes' $eid 'EVENTO_CONFIRMADO' 'PUBLICADO' 60
foreach ($f in @(@('PAGO_REGISTRADO', $pPago, $fPago), @('SERVICIO_ADICIONAL_AGREGADO', $pAdic, $fAdic), @('EVENTO_CONFIRMADO', $pConf, $fConf))) {
    Check "Outbox: $($f[0]) PUBLICADO con el mismo idEvento y más de un intento" ($f[1].Estado -eq 'PUBLICADO' -and $f[1].IdEvento -eq $f[2].IdEvento -and $f[1].Intentos -ge 2) "$($f[1] | ConvertTo-Json -Compress)"
}
$logF2 = LogsDesde 'finanzas-service' $desde
$logE2 = LogsDesde 'eventos-service' $desde
Check 'Log de reenvío del pago: idEvento, tópico, intento y resultado=PUBLICADO' ($logF2 -match "Reenvío del outbox\. idEvento=$($pPago.IdEvento), tipo=PAGO_REGISTRADO, eventoId=$eid, topic=pagos\.registrados, intento=$($pPago.Intentos), resultado=PUBLICADO") ''
Check 'Log de reenvío del adicional' ($logF2 -match "Reenvío del outbox\. idEvento=$($pAdic.IdEvento), tipo=SERVICIO_ADICIONAL_AGREGADO, .*resultado=PUBLICADO") ''
Check 'Log de reenvío de la confirmación' ($logE2 -match "Reenvío del outbox\. idEvento=$($pConf.IdEvento), tipo=EVENTO_CONFIRMADO, eventoId=$eid, topic=eventos\.confirmados, intento=$($pConf.Intentos), resultado=PUBLICADO") ''
Check 'Log: los reenvíos fallidos mientras Kafka estaba caído también quedan registrados (resultado=FALLÓ)' (($logF2 + $logE2) -match 'Reenvío del outbox\. .*resultado=FALLÓ') ''

Start-Sleep -Seconds 20   # un ciclo más del reenvío: no debe volver a publicar lo ya publicado
$kPago = @(LeerTopico 'pagos.registrados' | Where-Object { $_.Clave -eq $eid })
$kAdic = @(LeerTopico 'servicios-adicionales.agregados' | Where-Object { $_.Clave -eq $eid })
$kConf = @(LeerTopico 'eventos.confirmados' | Where-Object { $_.Clave -eq $eid })
Check 'Kafka: los 2 abonos del evento, cada uno una sola vez' ($kPago.Count -eq 2 -and @($kPago.Json.idEvento | Sort-Object -Unique).Count -eq 2) "mensajes=$($kPago.Count)"
Check 'Kafka: el abono reenviado tiene el mismo idEvento que su fila del outbox' (@($kPago | Where-Object { $_.Json.idEvento -eq $pPago.IdEvento -and $_.Json.datos.pagoId -eq $pago.Body.pagoId }).Count -eq 1)
Check 'Kafka: el adicional reenviado, una vez, mismo idEvento' ($kAdic.Count -eq 1 -and $kAdic[0].Json.idEvento -eq $pAdic.IdEvento -and $kAdic[0].Json.datos.adicionalId -eq $adicional.Body.id)
Check 'Kafka: la confirmación reenviada, una vez, mismo idEvento' ($kConf.Count -eq 1 -and $kConf[0].Json.idEvento -eq $pConf.IdEvento -and $kConf[0].Json.tipo -eq 'EVENTO_CONFIRMADO')
foreach ($par in @(@('eventos.confirmados', $kConf[0]), @('pagos.registrados', ($kPago | Where-Object { $_.Json.idEvento -eq $pPago.IdEvento })), @('servicios-adicionales.agregados', $kAdic[0]))) {
    $m = $par[1]
    $visible = $false
    for ($i = 0; $i -lt 3 -and -not $visible; $i++) {
        $pagina = Invoke-WebRequest -UseBasicParsing "http://localhost:9000/topic/$($par[0])/messages?partition=$($m.Particion)&offset=0&count=100&keyFormat=DEFAULT&format=DEFAULT"
        $visible = $pagina.Content -match $m.Json.idEvento
        if (-not $visible) { Start-Sleep -Seconds 5 }
    }
    Check "Kafdrop: $($m.Json.tipo) reenviado visible en $($par[0])" $visible ''
}

Write-Host '== Logística procesa una sola vez el evento confirmado que llegó por el outbox'
$log = $null
for ($i = 0; $i -lt 10 -and (-not $log -or $log.Status -ne 200); $i++) { Start-Sleep -Seconds 2; $log = Api GET "$($Url.logistica)/eventos/$eid/logistica" }
CheckStatus 'Logística calculada (llegó por el outbox)' $log 200
Check 'Boda rural 132 invitados: 9 meseros, 5 de cocina, 2 camiones' ($log.Body.meserosRequeridos -eq 9 -and $log.Body.personalCocinaRequerido -eq 5 -and $log.Body.camionesTransporte -eq 2) "$($log.Texto)"
$logL = LogsDesde 'logistica-service' $desde
# Desde la Fase 5B: línea fija del consumidor y tabla logistica_eventos_procesados (antes processed_events)
Check 'Logística recibió el mensaje una vez' (@([regex]::Matches($logL, "Consumo Kafka\. grupo=logistica-group, topic=eventos\.confirmados, [^\r\n]*idEvento=$($pConf.IdEvento), tipo=EVENTO_CONFIRMADO")).Count -eq 1) ''
Check 'Logística lo procesó una vez' (@([regex]::Matches($logL, "idEvento=$($pConf.IdEvento), tipo=EVENTO_CONFIRMADO, eventoId=$eid, resultado=PROCESADO")).Count -eq 1) ''
Check 'BD de Logística: una fila procesada para el evento, con el idEvento del outbox' ((Psql "SELECT count(*) FROM logistica_eventos_procesados WHERE evento_id = '$eid' AND id_evento = '$($pConf.IdEvento)' AND consumer_group = 'logistica-group';").Trim() -eq '1')
$compras = Api GET "$($Url.logistica)/eventos/$eid/compras"
Check 'Lista de compras generada una sola vez (5 ítems de boda)' ($compras.Status -eq 200 -and $compras.Body.totalElementos -eq 5) "$($compras.Body.totalElementos)"

Write-Host '== Registro: las filas PUBLICADO se conservan'
Check 'BD: filas PUBLICADO del evento siguen en finanzas_mensajes_salientes (2 pagos + 1 adicional)' ((Psql "SELECT count(*) FROM finanzas_mensajes_salientes WHERE clave = '$eid' AND estado = 'PUBLICADO';").Trim() -eq '3')
Check 'BD: ninguna fila del outbox quedó PENDIENTE o ENVIANDO' ((Psql "SELECT (SELECT count(*) FROM finanzas_mensajes_salientes WHERE estado <> 'PUBLICADO') + (SELECT count(*) FROM eventos_mensajes_salientes WHERE estado <> 'PUBLICADO');").Trim() -eq '0')

Resumen 'Verificación 5A2'
