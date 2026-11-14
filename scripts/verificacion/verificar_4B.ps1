# Verificación en Docker de la subfase 4B (Finanzas) + flujo de la Fase 3.
# Requiere el entorno levantado (docker compose up -d). Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

function Dinero($valor) { [decimal]::Round([decimal]$valor, 2) }

CheckStatus 'Login' (Login) 200
$fechas = FechasLibres
Check 'Hay al menos 6 fines de semana libres' ($fechas.Count -ge 6) "fechas=$($fechas.Count)"
$cli = CrearCliente 'Prueba María José Cárdenas Peña'
CheckStatus 'Crear cliente' $cli 201
$clienteId = $cli.Body.id
function CrearEvento([string]$Fecha, [string]$Tipo, [int]$Adultos, [int]$Ninos, [string]$Zona, [string]$Nombre) {
    Api POST "$($Url.eventos)/eventos" @{ nombreEvento = $Nombre; clienteId = $clienteId; fecha = $Fecha; tipoEvento = $Tipo; cantidadAdultos = $Adultos; cantidadNinos = $Ninos; zona = $Zona }
}

Write-Host '== Cotización (datos de Eventos + tarifas de la configuración)'
$e1 = CrearEvento $fechas[0] 'QUINCE_ANOS' 90 15 'NEIVA' 'Prueba Quince años de María José'
CheckStatus 'Crear evento 1 (15 años, 90+15, NEIVA)' $e1 201
$c1 = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $e1.Body.id }
CheckStatus 'Cotizar solo con eventoId' $c1 201
Check 'Location de la cotización' ($c1.Headers['Location'] -like "*/api/v1/eventos/$($e1.Body.id)/cotizacion")
Check 'Datos tomados de Eventos (QUINCE_ANOS, 90 adultos, 15 niños, 105 invitados, NEIVA)' ($c1.Body.tipoEvento -eq 'QUINCE_ANOS' -and $c1.Body.cantidadAdultos -eq 90 -and $c1.Body.cantidadNinos -eq 15 -and $c1.Body.invitados -eq 105 -and $c1.Body.zona -eq 'NEIVA')
Check 'Desglose: 10 adultos adicionales = 700.000; 0 niños adicionales' ($c1.Body.adultosAdicionales -eq 10 -and (Dinero $c1.Body.valorAdultosAdicionales) -eq 700000 -and $c1.Body.ninosAdicionales -eq 0)
Check 'Total 8.700.000 (base 8.000.000, sin recargo) y reserva 30 % = 2.610.000' ((Dinero $c1.Body.valorBase) -eq 8000000 -and (Dinero $c1.Body.recargoZona) -eq 0 -and (Dinero $c1.Body.total) -eq 8700000 -and (Dinero $c1.Body.montoReservaRequerido) -eq 2610000) "total=$($c1.Body.total) reserva=$($c1.Body.montoReservaRequerido)"
$c1b = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $e1.Body.id }
Check 'Cotizar otra vez -> 200 con la misma cotización (no se regenera)' ($c1b.Status -eq 200 -and $c1b.Body.id -eq $c1.Body.id)

$e2 = CrearEvento $fechas[1] 'BODA' 120 12 'RURAL' 'Prueba Boda Cárdenas y Ospina'
CheckStatus 'Crear evento 2 (boda, 120+12, RURAL)' $e2 201
$c2 = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $e2.Body.id; invitados = 5; zona = 'NEIVA' }
CheckStatus 'Cotizar con el cuerpo antiguo (invitados y zona se ignoran)' $c2 201
Check 'Usa los datos de Eventos, no los del cuerpo (BODA, RURAL, 132 invitados)' ($c2.Body.tipoEvento -eq 'BODA' -and $c2.Body.zona -eq 'RURAL' -and $c2.Body.invitados -eq 132)
Check 'Boda rural: subtotal 13.900.000 + recargo 10 % 1.390.000 = 15.290.000; reserva 4.587.000' ((Dinero $c2.Body.subtotal) -eq 13900000 -and (Dinero $c2.Body.recargoZona) -eq 1390000 -and (Dinero $c2.Body.total) -eq 15290000 -and (Dinero $c2.Body.montoReservaRequerido) -eq 4587000) "total=$($c2.Body.total)"
CheckStatus 'Cotizar evento inexistente' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = [guid]::NewGuid() }) 404
$sinId = Api POST "$($Url.finanzas)/cotizaciones" @{ invitados = 10 }
Check 'Cotizar sin eventoId -> 400 con el campo' ($sinId.Status -eq 400 -and @($sinId.Body.errores.campo) -contains 'eventoId') "$($sinId.Status) $($sinId.Texto)"
$e3 = CrearEvento $fechas[2] 'QUINCE_ANOS' 50 10 'NEIVA' 'Prueba Quince que se cancela'
CheckStatus 'Crear evento 3' $e3 201
CheckStatus 'Cancelar evento 3' (Api PATCH "$($Url.eventos)/eventos/$($e3.Body.id)/cancelar") 200
$c3 = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $e3.Body.id }
CheckStatus 'Cotizar evento CANCELADO' $c3 409
$e4 = CrearEvento $fechas[3] 'BODA' 80 5 'NEIVA' 'Prueba Boda sin cotización'
CheckStatus 'Crear evento 4 (sin cotización)' $e4 201

Write-Host '== Validaciones de pagos'
$id1 = $e1.Body.id
function Pago($EventoId, $Monto, [string]$Clave = $null, [string]$Moneda = 'COP', $Cliente = $clienteId) {
    $cab = if ($Clave) { @{ 'X-Idempotency-Key' = $Clave } } else { @{} }
    Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $EventoId; clienteId = $Cliente; metodoPago = 'TRANSFERENCIA'; monto = $Monto; moneda = $Moneda } -Cabeceras $cab
}
CheckStatus 'Pago a evento sin cotización' (Pago $e4.Body.id 1000) 409
CheckStatus 'Pago a evento inexistente' (Pago ([guid]::NewGuid()) 1000) 404
CheckStatus 'Pago a evento CANCELADO' (Pago $e3.Body.id 1000) 409
$pCli = Pago $id1 1000 -Cliente ([guid]::NewGuid())
Check 'Pago con otro cliente -> 400 en clienteId' ($pCli.Status -eq 400 -and @($pCli.Body.errores.campo) -contains 'clienteId') "$($pCli.Status) $($pCli.Texto)"
$pMon = Pago $id1 1000 -Moneda 'USD'
Check 'Pago en otra moneda -> 400 en moneda' ($pMon.Status -eq 400 -and @($pMon.Body.errores.campo) -contains 'moneda') "$($pMon.Status) $($pMon.Texto)"
CheckStatus 'Pago con monto 0' (Pago $id1 0) 400
CheckStatus 'Pago con monto negativo' (Pago $id1 -500) 400
$pMas = Pago $id1 8700000.01
CheckStatus 'Pago mayor que el saldo (8.700.000,01)' $pMas 409
Check 'Mensaje con el saldo pendiente' ($pMas.Body.message -match 'supera el saldo pendiente de 8700000') "$($pMas.Body.message)"
CheckStatus 'Clave de idempotencia de más de 100 caracteres' (Pago $id1 1000 ('x' * 101)) 400

Write-Host '== Idempotencia en PostgreSQL (sin Redis)'
Redis 'for k in $(redis-cli --scan --pattern "idempotency:finanzas:*"); do redis-cli DEL "$k" >/dev/null; done' | Out-Null
$k1 = "f4b-$(Sufijo)"
$p1 = Pago $id1 1000000 $k1
CheckStatus 'Pago con X-Idempotency-Key' $p1 201
Check 'Cabeceras: Location y X-Already-Processed=false' ($p1.Headers['Location'] -like "*/api/v1/eventos/$id1/pagos" -and $p1.Headers['X-Already-Processed'] -eq 'false')
Check 'Respuesta con monto, moneda, cliente y clave' ((Dinero $p1.Body.monto) -eq 1000000 -and $p1.Body.moneda -eq 'COP' -and $p1.Body.clienteId -eq $clienteId -and $p1.Body.eventIdempotencia -eq $k1)
$p1b = Pago $id1 1000000 $k1
Check 'Repetir misma clave y datos -> 200, mismo pagoId, X-Already-Processed=true' ($p1b.Status -eq 200 -and $p1b.Body.pagoId -eq $p1.Body.pagoId -and $p1b.Headers['X-Already-Processed'] -eq 'true') "$($p1b.Status)"
CheckStatus 'Misma clave con otro monto' (Pago $id1 999 $k1) 409
$cuerpoDistinto = Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $id1; clienteId = $clienteId; metodoPago = 'EFECTIVO'; monto = 10; moneda = 'COP'; eventIdempotencia = [guid]::NewGuid() } -Cabeceras @{ 'X-Idempotency-Key' = [guid]::NewGuid() }
CheckStatus 'eventIdempotencia distinta de la cabecera' $cuerpoDistinto 400
Check 'BD: una sola fila con esa clave' ((Psql "SELECT count(*) FROM pagos WHERE clave_idempotencia = '$k1';").Trim() -eq '1')
$k2 = "f4b-par-$(Sufijo)"
$paralelo = ApiEnParalelo (1..5 | ForEach-Object { @{ Metodo = 'POST'; Ruta = "$($Url.finanzas)/finanzas/pagos"; Cuerpo = @{ eventoId = $id1; clienteId = $clienteId; metodoPago = 'EFECTIVO'; monto = 500000; moneda = 'COP' }; Cabeceras = @{ 'X-Idempotency-Key' = $k2 } } })
$codigos = ($paralelo | ForEach-Object { $_.Status } | Sort-Object) -join ','
Check '5 peticiones simultáneas con la misma clave -> un 201 y cuatro 200' ($codigos -eq '200,200,200,200,201') "códigos=$codigos"
Check 'Las 5 devuelven el mismo pagoId' (@($paralelo | ForEach-Object { $_.Body.pagoId } | Sort-Object -Unique).Count -eq 1)
Check 'BD: una sola fila con la clave concurrente' ((Psql "SELECT count(*) FROM pagos WHERE clave_idempotencia = '$k2';").Trim() -eq '1')
$redisIdem = (Redis 'redis-cli --scan --pattern "idempotency:finanzas:*" | wc -l').Trim()
Check 'Redis ya no guarda claves de idempotencia de pagos' ($redisIdem -eq '0') "claves=$redisIdem"
$indice = (Psql "SELECT indexdef FROM pg_indexes WHERE indexname = 'uk_pago_clave_idempotencia';").Trim()
Check 'BD: índice UNIQUE uk_pago_clave_idempotencia' ($indice -match 'UNIQUE.*clave_idempotencia') "$indice"

Write-Host '== Bloqueo del saldo con abonos simultáneos'
$bal = Api GET "$($Url.finanzas)/eventos/$id1/balance"
$saldo = Dinero $bal.Body.saldoPendiente
Check 'Saldo tras 1.500.000 abonados = 7.200.000' ($saldo -eq 7200000) "saldo=$saldo"
$parte = Dinero ($saldo * 0.6)
$simult = ApiEnParalelo (1..3 | ForEach-Object { @{ Metodo = 'POST'; Ruta = "$($Url.finanzas)/finanzas/pagos"; Cuerpo = @{ eventoId = $id1; clienteId = $clienteId; metodoPago = 'TRANSFERENCIA'; monto = $parte; moneda = 'COP' }; Cabeceras = @{ 'X-Idempotency-Key' = "f4b-sim-$_-$(Sufijo)" } } })
$cod2 = ($simult | ForEach-Object { $_.Status } | Sort-Object) -join ','
Check '3 abonos simultáneos del 60 % del saldo -> solo uno pasa (201,409,409)' ($cod2 -eq '201,409,409') "códigos=$cod2"
$bal2 = Api GET "$($Url.finanzas)/eventos/$id1/balance"
Check 'Saldo nunca negativo: queda el 40 %' ((Dinero $bal2.Body.saldoPendiente) -eq ($saldo - $parte)) "saldo=$($bal2.Body.saldoPendiente)"
$resto = Dinero $bal2.Body.saldoPendiente
CheckStatus 'Pagar exactamente el saldo restante' (Pago $id1 $resto) 201
$pagado = Pago $id1 1
CheckStatus 'Otro abono con el evento ya pagado' $pagado 409
Check 'Mensaje: pagado por completo' ($pagado.Body.message -match 'pagado por completo') "$($pagado.Body.message)"

Write-Host '== Adicionales y listados'
$ad0 = Api GET "$($Url.finanzas)/eventos/$id1/adicionales"
Check 'GET adicionales sin adicionales -> 200 []' ($ad0.Status -eq 200 -and @($ad0.Body).Count -eq 0) "$($ad0.Status) $($ad0.Texto)"
$ad = Api POST "$($Url.finanzas)/eventos/$id1/adicionales" @{ codigo = 'decoracion-premium'; cantidad = 1 }
CheckStatus 'Agregar adicional decoración premium' $ad 201
$ad1 = Api GET "$($Url.finanzas)/eventos/$id1/adicionales"
Check 'GET adicionales -> 1 con nombre y valores' ($ad1.Status -eq 200 -and @($ad1.Body).Count -eq 1 -and $ad1.Body[0].nombre -eq 'Decoración premium' -and (Dinero $ad1.Body[0].subtotal) -eq 250000)
CheckStatus 'GET adicionales de evento inexistente' (Api GET "$($Url.finanzas)/eventos/$([guid]::NewGuid())/adicionales") 404
$bal3 = Api GET "$($Url.finanzas)/eventos/$id1/balance"
Check 'El adicional abre saldo de 250.000' ((Dinero $bal3.Body.saldoPendiente) -eq 250000) "saldo=$($bal3.Body.saldoPendiente)"
CheckStatus 'Abono del adicional' (Pago $id1 250000) 201
$lista = Api GET "$($Url.finanzas)/eventos/$id1/pagos"
# 1.000.000 (k1) + 500.000 (k2, concurrente) + 60 % del saldo + resto + adicional = 5 pagos
Check 'GET pagos del evento -> 5 pagos' ($lista.Status -eq 200 -and $lista.Body.totalElementos -eq 5) "$($lista.Status) total=$($lista.Body.totalElementos)"
$pag = Api GET "$($Url.finanzas)/eventos/$id1/pagos?size=2&sort=monto,desc"
Check 'size=2 y sort=monto,desc' ($pag.Status -eq 200 -and $pag.Body.tamanoPagina -eq 2 -and @($pag.Body.contenido).Count -eq 2 -and [decimal]$pag.Body.contenido[0].monto -ge [decimal]$pag.Body.contenido[1].monto -and $pag.Body.totalPaginas -eq 3)
CheckStatus 'sort no permitido' (Api GET "$($Url.finanzas)/eventos/$id1/pagos?sort=clienteId") 400
CheckStatus 'size=101' (Api GET "$($Url.finanzas)/eventos/$id1/pagos?size=101") 400
$vacia = Api GET "$($Url.finanzas)/eventos/$($e2.Body.id)/pagos"
Check 'Evento sin pagos -> página vacía' ($vacia.Status -eq 200 -and $vacia.Body.totalElementos -eq 0)
CheckStatus 'Pagos de evento inexistente' (Api GET "$($Url.finanzas)/eventos/$([guid]::NewGuid())/pagos") 404

Write-Host '== PAGO_REGISTRADO después del commit, clave = eventoId'
$logF = Compose 'logs finanzas-service' | Out-String
# Clave y valor separados por tabulador (separador por defecto de print.key)
$mensajes = Compose "exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic pagos.registrados --from-beginning --property print.key=true --timeout-ms 10000" | Out-String
$linea = ($mensajes -split "`n" | Where-Object { $_ -match $p1.Body.pagoId } | Select-Object -First 1)
Check 'Mensaje en Kafka con clave = eventoId' ($linea -and $linea.StartsWith("$id1`t")) "$linea"
# Desde la Fase 5A1 el log identifica el mensaje por su idEvento (sobre común): el del mensaje de ese pago
$idEventoP1 = if ($linea) { ($linea.Split("`t", 2)[1] | ConvertFrom-Json).idEvento } else { '' }
Check "Log: PAGO_REGISTRADO publicado del pago $($p1.Body.pagoId)" ($idEventoP1 -and $logF -match "PAGO_REGISTRADO publicado\. idEvento=$idEventoP1, eventoId=$id1")
# Desde la Fase 5 el mensaje va en el sobre común: la clave de idempotencia del pago está en datos.claveIdempotencia
Check 'Mensaje (sobre PAGO_REGISTRADO) con pagoId, monto y claveIdempotencia' ($linea -match '"tipo":"PAGO_REGISTRADO"' -and $linea -match '"pagoId"' -and $linea -match '"monto"' -and $linea -match "`"claveIdempotencia`":`"$k1`"")
$repetidos = @($mensajes -split "`n" | Where-Object { $_ -match "`"claveIdempotencia`":`"$k2`"" }).Count
Check 'Las 5 peticiones concurrentes publicaron un solo mensaje' ($repetidos -eq 1) "mensajes=$repetidos"
$delEvento = @($mensajes -split "`n" | Where-Object { $_.StartsWith("$id1`t") }).Count
Check 'Solo los pagos registrados publican: 5 mensajes del evento (los rechazados y las repeticiones no)' ($delEvento -eq 5) "mensajes=$delEvento"

Write-Host '== Eventos caído -> 503'
$e5 = CrearEvento $fechas[4] 'QUINCE_ANOS' 70 10 'NEIVA' 'Prueba Quince para probar 503'
CheckStatus 'Crear evento 5' $e5 201
Compose 'stop eventos-service' | Out-Null
CheckStatus 'Cotizar con Eventos caído' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $e5.Body.id }) 503
CheckStatus 'Pago con Eventos caído' (Pago $id1 1) 503
CheckStatus 'GET pagos con Eventos caído' (Api GET "$($Url.finanzas)/eventos/$id1/pagos") 503
CheckStatus 'GET adicionales con Eventos caído' (Api GET "$($Url.finanzas)/eventos/$id1/adicionales") 503
Check 'Cotización existente se devuelve sin consultar a Eventos -> 200' ((Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $id1 }).Status -eq 200)
Compose 'start eventos-service' | Out-Null
Check 'eventos-service vuelve a healthy' ((EsperarHealthy 'eventos-service') -eq 'healthy')

Write-Host '== Kafka caído después del commit -> el pago queda registrado'
$c5 = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $e5.Body.id }
CheckStatus 'Cotizar evento 5' $c5 201
Compose 'stop kafka' | Out-Null
try {
    $desde = Get-Date
    $reloj = [Diagnostics.Stopwatch]::StartNew()
    $pk = Pago $e5.Body.id 100000
    $reloj.Stop()
    CheckStatus 'Pago con Kafka detenido' $pk 201
    Check 'Responde sin esperar 60 s (max.block.ms = 5 s)' ($reloj.Elapsed.TotalSeconds -lt 12) ("{0:N1} s" -f $reloj.Elapsed.TotalSeconds)
    Check 'El pago quedó en la BD' ($pk.Body.pagoId -and (Psql "SELECT count(*) FROM pagos WHERE id = '$($pk.Body.pagoId)';").Trim() -eq '1')
    # El productor guarda el mensaje en su búfer hasta delivery.timeout.ms (30 s): Kafka sigue detenido más de 30 s.
    Start-Sleep -Seconds 40
    # Desde la Fase 5A2 (outbox): el mensaje no se pierde, queda PENDIENTE y se publica cuando Kafka vuelve.
    Check 'Log (Kafka caído > 30 s): FALLÓ la publicación de PAGO_REGISTRADO, queda PENDIENTE en el outbox' ((LogsDesde 'finanzas-service' $desde) -match "FALLÓ la publicación de PAGO_REGISTRADO \(queda PENDIENTE en el outbox.*eventoId=$($e5.Body.id)")
    $filaK = FilaOutbox 'finanzas_mensajes_salientes' $e5.Body.id 'PAGO_REGISTRADO'
    Check 'Outbox: la fila del pago está PENDIENTE con el error registrado' ($filaK -and $filaK.Estado -eq 'PENDIENTE' -and $filaK.Intentos -ge 1 -and $filaK.UltimoError) "$($filaK | ConvertTo-Json -Compress)"
} finally {
    Compose 'start kafka' | Out-Null
}
Check 'kafka vuelve a healthy' ((EsperarHealthy 'kafka' 180) -eq 'healthy')
$publicadaK = EsperarEstadoOutbox 'finanzas_mensajes_salientes' $e5.Body.id 'PAGO_REGISTRADO' 'PUBLICADO' 120
Check 'Al volver Kafka, el outbox lo reenvía: fila PUBLICADO' ($publicadaK.Estado -eq 'PUBLICADO') "$($publicadaK | ConvertTo-Json -Compress)"
$enKafka = @(LeerTopico 'pagos.registrados' | Where-Object { $_.Clave -eq $e5.Body.id })
Check 'El PAGO_REGISTRADO está en Kafka una sola vez y con el mismo idEvento de la fila' ($enKafka.Count -eq 1 -and $enKafka[0].Json.idEvento -eq $filaK.IdEvento -and $enKafka[0].Json.datos.pagoId -eq $pk.Body.pagoId) "mensajes=$($enKafka.Count)"
Start-Sleep -Seconds 10

Write-Host '== Flujo de la Fase 3 (cotización con el cuerpo nuevo)'
$flujo = FlujoFase3 $fechas[5] { param($ev) @{ eventoId = $ev.id } }
Check 'Flujo: cotización con datos de Eventos (QUINCE_ANOS 90+15 = 8.700.000)' ($flujo.Cotizacion.tipoEvento -eq 'QUINCE_ANOS' -and (Dinero $flujo.Cotizacion.total) -eq 8700000)
CheckStatus 'Sin token' (Api GET "$($Url.finanzas)/eventos/$id1/pagos" -SinToken) 401

Resumen 'Verificación 4B'
