# Verificación en Docker de la subfase 5C: auditoria-service (8087) y notificaciones-service (8089).
# Paso a paso: un pago -> 1 auditoría y 1 notificación; la confirmación -> 1 y 1; un adicional -> solo auditoría.
# Duplicados ignorados por los dos (DUPLICADO_IGNORADO), inválidos e ilegibles al DLT sin reintentos, filtros,
# paginación, errores 400 y 401. Requiere el entorno levantado (los 10 servicios). Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado
$script:Url.auditoria = 'http://localhost:8087/api/v1'
$script:Url.notificaciones = 'http://localhost:8089/api/v1'

function Consumo([string]$Grupo, [string]$IdEvento, [string]$Resultado) {
    "Consumo Kafka\. grupo=$Grupo, topic=[^,]+, particion=\d+, offset=\d+, idEvento=$IdEvento, tipo=[^,]+, eventoId=[^,]+, resultado=$Resultado"
}
function Lineas([string]$Log, [string]$Patron) { @([regex]::Matches($Log, $Patron)).Count }
function Auditoria([string]$Consulta) { Api GET "$($Url.auditoria)/auditoria?$Consulta" }
function Notificaciones([string]$Consulta) { Api GET "$($Url.notificaciones)/notificaciones?$Consulta" }
# Espera a que el total del listado llegue al valor esperado (el consumo es asíncrono).
function EsperarTotal([scriptblock]$Consulta, [int]$Esperado, [int]$Segundos = 30) {
    $fin = (Get-Date).AddSeconds($Segundos)
    do { $r = & $Consulta; if ($r.Status -eq 200 -and $r.Body.totalElementos -eq $Esperado) { return $r }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $fin)
    $r
}
function Contar([string]$Sql) { [int](Psql $Sql).Trim() }

CheckStatus 'Login' (Login) 200
$inicio = Get-Date
$fecha = (FechasLibres)[0]
$es = [Globalization.CultureInfo]::GetCultureInfo('es-CO')

Write-Host '== 0. Servicios: health y Swagger públicos, API protegida'
foreach ($s in @(@{ n = 'auditoria'; p = 8087; r = 'auditoria' }, @{ n = 'notificaciones'; p = 8089; r = 'notificaciones' })) {
    CheckStatus "$($s.n): /actuator/health sin token" (Api GET "http://localhost:$($s.p)/actuator/health" -SinToken) 200
    $docs = Api GET "http://localhost:$($s.p)/v3/api-docs" -SinToken
    CheckStatus "$($s.n): /v3/api-docs (Springdoc) sin token" $docs 200
    Check "$($s.n): Springdoc documenta GET /api/v1/$($s.r)" ($docs.Texto -match "/api/v1/$($s.r)") ''
    CheckStatus "$($s.n): GET /api/v1/$($s.r) sin token" (Api GET "http://localhost:$($s.p)/api/v1/$($s.r)" -SinToken) 401
    CheckStatus "$($s.n): GET /api/v1/$($s.r) con token inválido" (Api GET "http://localhost:$($s.p)/api/v1/$($s.r)" -SinToken -Cabeceras @{ Authorization = 'Bearer abc.def.ghi' }) 401
}
Check 'auditoria: health sin Redis (no tiene el componente redis)' ((Api GET 'http://localhost:8087/actuator/health' -SinToken).Texto -notmatch 'redis') ''

Write-Host "== 1. Evento nuevo (fecha $fecha): un pago -> 1 auditoría y 1 notificación"
$nombre = 'Prueba Quince años de Valentína Ñáñez'
$c = CrearCliente 'Prueba Valentína Ñáñez Gómez'
CheckStatus 'Crear cliente' $c 201
$clienteId = $c.Body.id
$ev = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = $nombre; clienteId = $clienteId; fecha = $fecha; tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 90; cantidadNinos = 15; zona = 'NEIVA' }
CheckStatus 'Crear evento' $ev 201
$eid = $ev.Body.id
$cot = Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $eid }
CheckStatus 'Cotizar' $cot 201
CheckStatus 'Crear contrato' (Api POST "$($Url.contratos)/eventos/$eid/contrato") 201
CheckStatus 'Firmar contrato' (Api PATCH "$($Url.contratos)/eventos/$eid/contrato/marcar-firmado") 200
Start-Sleep -Seconds 3
Check 'Antes del pago: 0 auditorías y 0 notificaciones del evento' ((Auditoria "eventoId=$eid").Body.totalElementos -eq 0 -and (Notificaciones "eventoId=$eid").Body.totalElementos -eq 0) ''
$reserva = [decimal]$cot.Body.montoReservaRequerido
$pago = Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $eid; clienteId = $clienteId; metodoPago = 'TRANSFERENCIA'; monto = $reserva; moneda = 'COP' }
CheckStatus 'Pago de la reserva' $pago 201
$a1 = EsperarTotal { Auditoria "eventoId=$eid" } 1
$n1 = EsperarTotal { Notificaciones "eventoId=$eid" } 1
Check 'Tras el pago: 1 auditoría PAGO_REGISTRADO' ($a1.Body.totalElementos -eq 1 -and $a1.Body.contenido[0].tipo -eq 'PAGO_REGISTRADO') "$($a1.Texto)"
Check 'Tras el pago: 1 notificación PAGO_REGISTRADO' ($n1.Body.totalElementos -eq 1 -and $n1.Body.contenido[0].tipo -eq 'PAGO_REGISTRADO') "$($n1.Texto)"
$montoTexto = $reserva.ToString('#,##0.00', $es)
$np = $n1.Body.contenido[0]
Check "Notificación del pago: asunto 'Abono registrado', destinatario cliente:<clienteId>, GENERADA" ($np.asunto -eq 'Abono registrado' -and $np.destinatario -eq "cliente:$clienteId" -and $np.estado -eq 'GENERADA') "$($n1.Texto)"
Check "Mensaje del pago en español con el monto es-CO ($montoTexto COP (TRANSFERENCIA))" ($np.mensaje -like "Hola. Registramos su abono de $montoTexto COP (TRANSFERENCIA) el *") "$($np.mensaje)"
$ap = $a1.Body.contenido[0]
Check 'Auditoría del pago: origen finanzas-service, tópico pagos.registrados, payload = sobre con datos.pagoId' ($ap.servicioOrigen -eq 'finanzas-service' -and $ap.topico -eq 'pagos.registrados' -and $ap.payload.idEvento -eq $ap.idEvento -and $ap.payload.datos.pagoId -eq $pago.Body.pagoId) "$($a1.Texto)"
Check 'BD: auditoria_eventos y notificaciones con 1 fila del pago' ((Contar "SELECT count(*) FROM auditoria_eventos WHERE evento_id = '$eid';") -eq 1 -and (Contar "SELECT count(*) FROM notificaciones WHERE evento_id = '$eid';") -eq 1) ''

Write-Host '== 2. Confirmar el evento -> 1 auditoría y 1 notificación más'
$conf = Api PATCH "$($Url.eventos)/eventos/$eid/confirmar"
CheckStatus 'Confirmar' $conf 200
$a2 = EsperarTotal { Auditoria "eventoId=$eid" } 2
$n2 = EsperarTotal { Notificaciones "eventoId=$eid" } 2
Check 'Tras confirmar: 2 auditorías (PAGO_REGISTRADO + EVENTO_CONFIRMADO)' ($a2.Body.totalElementos -eq 2 -and (Auditoria "eventoId=$eid&tipo=EVENTO_CONFIRMADO").Body.totalElementos -eq 1) "$($a2.Texto)"
Check 'Tras confirmar: 2 notificaciones' ($n2.Body.totalElementos -eq 2) "$($n2.Texto)"
$ne = @($n2.Body.contenido | Where-Object tipo -eq 'EVENTO_CONFIRMADO')[0]
Check "Notificación de la confirmación: asunto 'Evento confirmado: $nombre' (tildes y ñ)" ($ne.asunto -ceq "Evento confirmado: $nombre") "$($ne.asunto)"
$fechaLarga = ([datetime]$fecha).ToString("dddd d 'de' MMMM 'de' yyyy", $es)
Check "Mensaje: «$nombre» (Quince años) del $fechaLarga, 105 invitados (90 adultos y 15 niños)" ($ne.mensaje -ceq "Hola. Le confirmamos que su evento «$nombre» (Quince años) del $fechaLarga quedó reservado para 105 invitados (90 adultos y 15 niños). La fecha ya está apartada para usted. Gracias por confiar en Maxi Eventos.") "$($ne.mensaje)"
$ae = @((Auditoria "eventoId=$eid&tipo=EVENTO_CONFIRMADO").Body.contenido)[0]
Check 'Auditoría de la confirmación: origen eventos-service y payload con datos.nombreEvento con tildes' ($ae.servicioOrigen -eq 'eventos-service' -and $ae.payload.datos.nombreEvento -ceq $nombre -and $ae.fechaOcurrencia -and $ae.fechaRecepcion) ''
$sinTildesMal = Contar "SELECT count(*) FROM notificaciones WHERE evento_id = '$eid' AND (mensaje LIKE '%Ã%' OR asunto LIKE '%Ã%');"
Check 'BD: sin tildes mal codificadas (Ã) en las notificaciones' ($sinTildesMal -eq 0) ''

Write-Host '== 3. Agregar un adicional -> solo auditoría'
$ad = Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'decoracion-premium'; cantidad = 1 }
CheckStatus 'Agregar decoración premium' $ad 201
$a3 = EsperarTotal { Auditoria "eventoId=$eid" } 3
Check 'Tras el adicional: 3 auditorías (1 de cada tipo)' ($a3.Body.totalElementos -eq 3 -and (Auditoria "eventoId=$eid&tipo=SERVICIO_ADICIONAL_AGREGADO").Body.totalElementos -eq 1) "$($a3.Texto)"
Start-Sleep -Seconds 5
Check 'Tras el adicional: siguen 2 notificaciones (Notificaciones no consume adicionales)' ((Notificaciones "eventoId=$eid").Body.totalElementos -eq 2) ''

Write-Host '== 4. Duplicados: el mismo mensaje otra vez no crea filas en ninguno de los dos'
$mConf = @(LeerTopico 'eventos.confirmados' | Where-Object { $_.Clave -eq $eid })[0]
$mPago = @(LeerTopico 'pagos.registrados' | Where-Object { $_.Clave -eq $eid })[0]
$mAd = @(LeerTopico 'servicios-adicionales.agregados' | Where-Object { $_.Clave -eq $eid })[0]
Check 'Se encontraron los 3 mensajes del evento en Kafka' ($mConf -and $mPago -and $mAd) ''
$antes = '{0}|{1}|{2}|{3}' -f (Contar "SELECT count(*) FROM auditoria_eventos;"), (Contar "SELECT count(*) FROM auditoria_eventos_procesados;"), (Contar "SELECT count(*) FROM notificaciones;"), (Contar "SELECT count(*) FROM notificaciones_eventos_procesados;")
$desdeDup = Get-Date
InyectarMensajes 'eventos.confirmados' @("$eid#$($mConf.Valor)")
InyectarMensajes 'pagos.registrados' @("$eid#$($mPago.Valor)")
InyectarMensajes 'servicios-adicionales.agregados' @("$eid#$($mAd.Valor)")
Start-Sleep -Seconds 8
$logA = LogsDesde 'auditoria-service' $desdeDup
$logN = LogsDesde 'notificaciones-service' $desdeDup
foreach ($m in @($mConf, $mPago, $mAd)) {
    $t = $m.Json.tipo
    Check "auditoria-group: DUPLICADO_IGNORADO del $t (idEvento $($m.Json.idEvento))" ((Lineas $logA ((Consumo 'auditoria-group' $m.Json.idEvento 'DUPLICADO_IGNORADO') + ', detalle=idEvento ya procesado por auditoria-group')) -eq 1) ''
}
foreach ($m in @($mConf, $mPago)) {
    Check "notificaciones-group: DUPLICADO_IGNORADO del $($m.Json.tipo)" ((Lineas $logN ((Consumo 'notificaciones-group' $m.Json.idEvento 'DUPLICADO_IGNORADO') + ', detalle=idEvento ya procesado por notificaciones-group')) -eq 1) ''
}
$despues = '{0}|{1}|{2}|{3}' -f (Contar "SELECT count(*) FROM auditoria_eventos;"), (Contar "SELECT count(*) FROM auditoria_eventos_procesados;"), (Contar "SELECT count(*) FROM notificaciones;"), (Contar "SELECT count(*) FROM notificaciones_eventos_procesados;")
Check "BD sin cambios tras los duplicados (auditoría|procesados|notificaciones|procesados = $antes)" ($antes -eq $despues) "después $despues"
Check 'El evento sigue con 3 auditorías y 2 notificaciones' ((Auditoria "eventoId=$eid").Body.totalElementos -eq 3 -and (Notificaciones "eventoId=$eid").Body.totalElementos -eq 2) ''
Check 'Sin PROCESADO nuevo en los dos servicios' ((Lineas ($logA + $logN) 'resultado=PROCESADO') -eq 0) ''

Write-Host '== 5. Inválido e ilegible en pagos.registrados -> DLT con un solo intento (los dos grupos)'
$dltAntes = TotalMensajes 'pagos.registrados.DLT'
$idInv = [guid]::NewGuid().ToString()
$sobreInv = [ordered]@{ idEvento = $idInv; tipo = 'PAGO_DESCONOCIDO'; version = 1; fechaOcurrencia = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); servicioOrigen = 'verificacion'; eventoId = $eid; datos = @{ monto = 1 } } | ConvertTo-Json -Compress
$desdeInv = Get-Date
InyectarMensajes 'pagos.registrados' @("$eid#$sobreInv", "$eid#esto no es json 5C")
Start-Sleep -Seconds 10
$logA = LogsDesde 'auditoria-service' $desdeInv
$logN = LogsDesde 'notificaciones-service' $desdeInv
Check 'auditoria: tipo desconocido -> INVALIDO_A_DLT (1 vez)' ((Lineas $logA ((Consumo 'auditoria-group' $idInv 'INVALIDO_A_DLT') + ', detalle=MensajeInvalidoException: Tipo de mensaje no esperado')) -eq 1) ''
Check 'notificaciones: tipo desconocido -> INVALIDO_A_DLT (1 vez)' ((Lineas $logN ((Consumo 'notificaciones-group' $idInv 'INVALIDO_A_DLT') + ', detalle=MensajeInvalidoException: Tipo de mensaje no esperado')) -eq 1) ''
Check 'auditoria y notificaciones: JSON ilegible -> INVALIDO_A_DLT con idEvento=-' ((Lineas ($logA + $logN) 'topic=pagos\.registrados, particion=\d+, offset=\d+, idEvento=-, tipo=-, eventoId=-, resultado=INVALIDO_A_DLT') -eq 2) ''
Check 'Sin REINTENTO para los inválidos' ((Lineas ($logA + $logN) 'resultado=REINTENTO') -eq 0) ''
Check 'pagos.registrados.DLT: +4 (2 mensajes x 2 grupos)' ((TotalMensajes 'pagos.registrados.DLT') -eq ($dltAntes + 4)) "antes $dltAntes, ahora $(TotalMensajes 'pagos.registrados.DLT')"
$textoDlt = TextoTopicoConCabeceras 'pagos.registrados.DLT'
Check 'DLT con kafka_dlt-original-consumer-group auditoria-group y notificaciones-group' ($textoDlt -match 'kafka_dlt-original-consumer-group:auditoria-group' -and $textoDlt -match 'kafka_dlt-original-consumer-group:notificaciones-group') ''
Check 'DLT: el ilegible conserva sus bytes originales' ($textoDlt -match 'esto no es json 5C') ''
Check 'El inválido no se auditó ni se notificó' ((Contar "SELECT count(*) FROM auditoria_eventos WHERE id_evento = '$idInv';") -eq 0 -and (Contar "SELECT count(*) FROM notificaciones WHERE id_evento = '$idInv';") -eq 0) ''

Write-Host '== 6. GET /auditoria: filtros, orden y paginación'
$r = Auditoria "eventoId=$eid&tipo=PAGO_REGISTRADO"
Check 'Filtro eventoId + tipo=PAGO_REGISTRADO -> 1' ($r.Status -eq 200 -and $r.Body.totalElementos -eq 1 -and $r.Body.contenido[0].tipo -eq 'PAGO_REGISTRADO') "$($r.Texto)"
$r = Auditoria "eventoId=$eid"
Check 'Orden por defecto fechaRecepcion desc: el primero es el adicional' ($r.Body.contenido[0].tipo -eq 'SERVICIO_ADICIONAL_AGREGADO') ''
$r = Auditoria "eventoId=$eid&sort=fechaOcurrencia,asc"
Check 'sort=fechaOcurrencia,asc: PAGO -> EVENTO_CONFIRMADO -> ADICIONAL' ((@($r.Body.contenido | ForEach-Object tipo) -join ',') -eq 'PAGO_REGISTRADO,EVENTO_CONFIRMADO,SERVICIO_ADICIONAL_AGREGADO') "$(@($r.Body.contenido | ForEach-Object tipo) -join ',')"
$r = Auditoria "eventoId=$eid&size=1&page=1"
Check 'size=1&page=1 -> 1 elemento, pagina 1, tamanoPagina 1, totalPaginas 3' ($r.Body.contenido.Count -eq 1 -and $r.Body.pagina -eq 1 -and $r.Body.tamanoPagina -eq 1 -and $r.Body.totalPaginas -eq 3) "$($r.Texto)"
$r = Auditoria 'tipo=SERVICIO_ADICIONAL_AGREGADO&size=100'
Check 'Filtro solo por tipo: todos SERVICIO_ADICIONAL_AGREGADO' ($r.Body.totalElementos -ge 1 -and @($r.Body.contenido | Where-Object tipo -ne 'SERVICIO_ADICIONAL_AGREGADO').Count -eq 0) ''
$r = Auditoria 'tipo=OTRO'
Check 'tipo=OTRO -> 400 con errores[campo=tipo]' ($r.Status -eq 400 -and $r.Body.errores[0].campo -eq 'tipo') "$($r.Status) $($r.Texto)"
$r = Auditoria 'eventoId=no-es-uuid'
Check 'eventoId inválido -> 400 con errores[campo=eventoId]' ($r.Status -eq 400 -and $r.Body.errores[0].campo -eq 'eventoId') "$($r.Status) $($r.Texto)"
CheckStatus 'size=101' (Auditoria 'size=101') 400
CheckStatus 'page=-1' (Auditoria 'page=-1') 400
$r = Auditoria 'sort=payload,asc'
Check 'sort=payload -> 400 con errores[campo=sort]' ($r.Status -eq 400 -and $r.Body.errores[0].campo -eq 'sort') "$($r.Status) $($r.Texto)"

Write-Host '== 7. GET /notificaciones: filtro y paginación'
$r = Notificaciones "eventoId=$eid"
Check 'Orden por defecto fechaCreacion desc: la primera es la confirmación' ($r.Body.contenido[0].tipo -eq 'EVENTO_CONFIRMADO') ''
$r = Notificaciones "eventoId=$eid&size=1&page=1"
Check 'size=1&page=1 -> la del pago, totalPaginas 2' ($r.Body.contenido.Count -eq 1 -and $r.Body.contenido[0].tipo -eq 'PAGO_REGISTRADO' -and $r.Body.totalPaginas -eq 2) "$($r.Texto)"
$r = Notificaciones 'size=5'
Check 'Sin filtro: lista de todas las notificaciones (tamanoPagina 5)' ($r.Status -eq 200 -and $r.Body.tamanoPagina -eq 5 -and $r.Body.totalElementos -ge 2) ''
$r = Notificaciones "eventoId=$([guid]::NewGuid())"
Check 'Evento sin notificaciones -> página vacía' ($r.Status -eq 200 -and $r.Body.totalElementos -eq 0) ''
$r = Notificaciones 'eventoId=x'
Check 'eventoId inválido -> 400 con errores[campo=eventoId]' ($r.Status -eq 400 -and $r.Body.errores[0].campo -eq 'eventoId') "$($r.Status) $($r.Texto)"
CheckStatus 'size=0' (Notificaciones 'size=0') 400
$r = Notificaciones 'sort=mensaje'
Check 'sort=mensaje -> 400 con errores[campo=sort]' ($r.Status -eq 400 -and $r.Body.errores[0].campo -eq 'sort') "$($r.Status) $($r.Texto)"

Write-Host '== 8. Logs de los dos servicios desde el inicio del script'
$logA = LogsDesde 'auditoria-service' $inicio
$logN = LogsDesde 'notificaciones-service' $inicio
Check 'Sin REINTENTO ni REINTENTOS_AGOTADOS_A_DLT' (($logA + $logN) -notmatch 'resultado=REINTENTO') ''
Check 'Sin errores de conexión a la BD (too many clients / Hikari)' (($logA + $logN) -notmatch 'too many clients|Connection is not available') ''
Write-Host "  Evento de esta verificación: $eid (cliente $clienteId)"

Resumen 'Verificación 5C'
