# Verificación histórica de la Fase 3 (flujo completo de eventos). Algunos casos cambiaron en fases posteriores.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\credenciales.ps1"
$script:fallos = 0; $script:paso = 0
function Llamar($metodo, $url, $cuerpo, $token) {
    $h = @{}; if ($token) { $h["Authorization"] = "Bearer $token" }
    $params = @{ Uri = $url; Method = $metodo; Headers = $h; UseBasicParsing = $true; TimeoutSec = 30 }
    if ($null -ne $cuerpo) { $params.Body = [Text.Encoding]::UTF8.GetBytes($cuerpo); $params.ContentType = "application/json; charset=utf-8" }
    try { $r = Invoke-WebRequest @params; $code = [int]$r.StatusCode; $txt = $r.Content }
    catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return [pscustomobject]@{ Codigo = 0; Texto = $_.Exception.Message; Json = $null } }
        $code = [int]$resp.StatusCode
        $sr = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $txt = $sr.ReadToEnd()
    }
    $json = $null; try { $json = $txt | ConvertFrom-Json } catch {}
    [pscustomobject]@{ Codigo = $code; Texto = $txt; Json = $json }
}
function Paso($nombre, $esperado, $r, $detalle) {
    $script:paso++
    $ok = $r.Codigo -eq $esperado
    if (-not $ok) { $script:fallos++ }
    $msg = if ($r.Json -and $r.Json.PSObject.Properties.Name -contains 'message') { " | message: $($r.Json.message)" } else { "" }
    "{0} {1,2}. {2,-62} HTTP {3} (esperado {4}){5}{6}" -f $(if ($ok) { "OK " } else { "MAL" }), $script:paso, $nombre, $r.Codigo, $esperado, $(if ($detalle) { " | $detalle" } else { "" }), $msg
}
function Ok($nombre, $condicion, $detalle) { if ($condicion) { "OK      $nombre  $detalle" } else { $script:fallos++; "MAL     $nombre  $detalle" } }

$b = "http://localhost"
$rnd = Get-Random -Minimum 100000 -Maximum 999999

# 1. Login
$login = Llamar POST "${b}:8080/api/v1/auth/login" (CuerpoLogin) $null
$token = $login.Json.token
Paso "Login" 200 $login "token obtenido: $([bool]$token)"

# 2. Crear cliente
$cli = Llamar POST "${b}:8082/api/v1/clientes" "{`"nombreCompleto`":`"Prueba Ana Fase3 $rnd`",`"telefono`":`"311$rnd`",`"correo`":`"ana.f3.$rnd@example.com`",`"direccion`":`"Neiva`"}" $token
$clienteId = $cli.Json.id
Paso "Crear cliente" 201 $cli "clienteId=$clienteId"

# 3. Fechas disponibles ANTES: elegir un fin de semana completamente libre (viernes, sábado y domingo)
$disp1 = Llamar GET "${b}:8081/api/v1/fechas/disponibles?desde=$((Get-Date).AddDays(30).ToString('yyyy-MM-dd'))&hasta=$((Get-Date).AddDays(366).ToString('yyyy-MM-dd'))" $null $token
$libres = @($disp1.Json | ForEach-Object { $_.fecha })
# 10B3: solo se registran eventos en viernes, sábado o domingo; el lunes siguiente nunca aparece (día no permitido).
$sabado = $libres | Where-Object { ([datetime]$_).DayOfWeek -eq 'Saturday' -and $libres -contains ([datetime]$_).AddDays(-1).ToString('yyyy-MM-dd') -and $libres -contains ([datetime]$_).AddDays(1).ToString('yyyy-MM-dd') } | Select-Object -First 1
Ok "  fechas/disponibles solo trae viernes, sábado y domingo" (@($libres | Where-Object { @('Friday', 'Saturday', 'Sunday') -notcontains ([datetime]$_).DayOfWeek.ToString() }).Count -eq 0) ""
$viernes = ([datetime]$sabado).AddDays(-1).ToString('yyyy-MM-dd'); $domingo = ([datetime]$sabado).AddDays(1).ToString('yyyy-MM-dd'); $lunes = ([datetime]$sabado).AddDays(2).ToString('yyyy-MM-dd')
Paso "GET fechas/disponibles (antes)" 200 $disp1 "$($libres.Count) fechas; fin de semana elegido: vie $viernes / sáb $sabado / dom $domingo"
Ok "  viernes, sábado y domingo aparecen disponibles" (($libres -contains $viernes) -and ($libres -contains $sabado) -and ($libres -contains $domingo)) ""

# 4. Crear evento el sábado
$cuerpoEvento = "{`"nombreEvento`":`"Prueba Boda de Ana y Luis $rnd`",`"clienteId`":`"$clienteId`",`"fecha`":`"$sabado`",`"tipoEvento`":`"BODA`",`"cantidadAdultos`":100,`"cantidadNinos`":20,`"zona`":`"RURAL`"}"
$ev = Llamar POST "${b}:8081/api/v1/eventos" $cuerpoEvento $token
$eventoId = $ev.Json.id
Paso "Crear evento (sábado $sabado)" 201 $ev "eventoId=$eventoId estado=$($ev.Json.estado) invitados=$($ev.Json.numInvitados)"

# 5. Fechas disponibles DESPUÉS de crear
$disp2 = Llamar GET "${b}:8081/api/v1/fechas/disponibles?desde=$viernes&hasta=$lunes" $null $token
$libres2 = @($disp2.Json | ForEach-Object { $_.fecha })
Paso "GET fechas/disponibles (después de crear)" 200 $disp2 "disponibles en vie..lun: $($libres2 -join ', ')"
Ok "  el fin de semana desaparece (y el lunes no se ofrece: no es día permitido)" ((-not ($libres2 -contains $viernes)) -and (-not ($libres2 -contains $sabado)) -and (-not ($libres2 -contains $domingo)) -and (-not ($libres2 -contains $lunes))) ""

# 6. Otro evento el mismo fin de semana (domingo) → 409
$otro = Llamar POST "${b}:8081/api/v1/eventos" $cuerpoEvento.Replace($sabado, $domingo) $token
Paso "Otro evento el mismo fin de semana (domingo)" 409 $otro ""
# Casos extra de validación
Paso "Evento con fecha de hoy (400 en campo fecha)" 400 (Llamar POST "${b}:8081/api/v1/eventos" $cuerpoEvento.Replace($sabado, (Get-Date).ToString('yyyy-MM-dd')) $token) ""
$lunesR = Llamar POST "${b}:8081/api/v1/eventos" $cuerpoEvento.Replace($sabado, $lunes) $token
Paso "Evento un lunes (400 en campo fecha: día no permitido)" 400 $lunesR ""
Ok "  errores[campo=fecha] con el mensaje de la regla" ($lunesR.Json.errores[0].campo -eq 'fecha' -and $lunesR.Json.errores[0].mensaje -ceq 'Solo se pueden registrar eventos en viernes, sábado o domingo.') "$($lunesR.Texto)"
$sabadoSiguiente = ([datetime]$sabado).AddDays(7).ToString('yyyy-MM-dd')
Paso "Evento con cliente inexistente (404)" 404 (Llamar POST "${b}:8081/api/v1/eventos" $cuerpoEvento.Replace($clienteId, [guid]::NewGuid().ToString()).Replace($sabado, $sabadoSiguiente) $token) ""

# 7. Cotizar
$cot = Llamar POST "${b}:8084/api/v1/cotizaciones" "{`"eventoId`":`"$eventoId`",`"invitados`":120,`"zona`":`"RURAL`"}" $token
$reserva = $cot.Json.montoReservaRequerido
Paso "Cotizar" 201 $cot "total=$($cot.Json.total) montoReservaRequerido=$reserva"

# 8. Crear contrato y confirmar sin firmar → 409
$con = Llamar POST "${b}:8085/api/v1/eventos/$eventoId/contrato" "{`"nombreEvento`":`"Prueba Boda de Ana y Luis $rnd`"}" $token
Paso "Crear contrato" 201 $con "estado=$($con.Json.estado)"
Paso "GET contrato del evento (endpoint nuevo)" 200 (Llamar GET "${b}:8085/api/v1/eventos/$eventoId/contrato" $null $token) ""
Paso "Confirmar sin firmar el contrato" 409 (Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/confirmar" $null $token) ""

# 9. Firmar y confirmar sin abono → 409
Paso "Marcar contrato como firmado" 200 (Llamar PATCH "${b}:8085/api/v1/eventos/$eventoId/contrato/marcar-firmado" $null $token) ""
Paso "Confirmar sin abono" 409 (Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/confirmar" $null $token) ""

# 10. Abonar (un abono parcial y luego el resto de la reserva) y confirmar → 200
$parcial = [math]::Round([double]$reserva / 2, 2)
$p1 = Llamar POST "${b}:8084/api/v1/finanzas/pagos" "{`"eventoId`":`"$eventoId`",`"clienteId`":`"$clienteId`",`"metodoPago`":`"TRANSFERENCIA`",`"monto`":$($parcial.ToString([Globalization.CultureInfo]::InvariantCulture)),`"moneda`":`"COP`"}" $token
Paso "Abono parcial (50 % de la reserva)" 201 $p1 "monto=$parcial"
Paso "Confirmar con abono insuficiente" 409 (Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/confirmar" $null $token) ""
$resto = [math]::Round([double]$reserva - $parcial, 2)
$p2 = Llamar POST "${b}:8084/api/v1/finanzas/pagos" "{`"eventoId`":`"$eventoId`",`"clienteId`":`"$clienteId`",`"metodoPago`":`"TRANSFERENCIA`",`"monto`":$($resto.ToString([Globalization.CultureInfo]::InvariantCulture)),`"moneda`":`"COP`"}" $token
Paso "Abono del resto de la reserva" 201 $p2 "monto=$resto"
$conf = Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/confirmar" $null $token
Paso "Confirmar evento" 200 $conf "estado=$($conf.Json.estado)"
Paso "Confirmar otra vez (ya RESERVADO)" 409 (Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/confirmar" $null $token) ""
$agenda = Llamar GET "${b}:8081/api/v1/fechas?desde=$sabado&hasta=$sabado" $null $token
Ok "  la fecha del evento quedó OCUPADA" ($agenda.Json[0].estado -eq 'OCUPADA' -and $agenda.Json[0].eventoId -eq $eventoId) "estado=$($agenda.Json[0].estado)"

# 11. Mensaje en Kafka (log del productor + Kafdrop) y logística generada
Start-Sleep 5
# Desde la Fase 5A1 el log incluye primero el idEvento del sobre: "EVENTO_CONFIRMADO publicado. idEvento=..., eventoId=..."
$linea = docker logs eventos_service 2>&1 | Select-String "EVENTO_CONFIRMADO publicado\. idEvento=\S+, eventoId=$eventoId" | Select-Object -Last 1
Ok "  log del productor: publicado después del commit" ([bool]$linea) $(if ($linea) { ($linea.Line -replace '^.*EVENTO_CONFIRMADO', 'EVENTO_CONFIRMADO') } else { "" })
if ($linea -and $linea.Line -match 'partition=(\d+), offset=(\d+)') {
  $part = $Matches[1]; $off = $Matches[2]
  try { $kd = Invoke-WebRequest "http://localhost:9000/topic/eventos.confirmados/messages?partition=$part&offset=$off&count=1" -UseBasicParsing -TimeoutSec 30
        $html = [Text.Encoding]::UTF8.GetString($kd.RawContentStream.ToArray())
        # Desde la Fase 5A1: sobre común (idEvento, tipo, ..., datos) en lugar de los 12 campos planos.
        Ok "  Kafdrop muestra el mensaje (partición $part, offset $off) con eventoId y el sobre completo" ($html.Contains($eventoId) -and $html.Contains('idEvento') -and $html.Contains('EVENTO_CONFIRMADO') -and $html.Contains('servicioOrigen') -and $html.Contains('cantidadNinos')) "HTTP $($kd.StatusCode)" }
  catch { Ok "  Kafdrop muestra el mensaje" $false $_.Exception.Message }
}
$log = Llamar GET "${b}:8088/api/v1/eventos/$eventoId/logistica" $null $token
Paso "Logística generada por el consumidor Kafka" 200 $log "meseros=$($log.Json.meserosRequeridos) cocina=$($log.Json.personalCocinaRequerido) camiones=$($log.Json.camionesTransporte)"
$comp = Llamar GET "${b}:8088/api/v1/eventos/$eventoId/compras" $null $token
Paso "Lista de compras generada" 200 $comp "ítems=$($comp.Json.totalElementos)"
$conLog = docker logs contratos_service --since 2m 2>&1 | Select-String -Pattern "Se rechaza|Error procesando Kafka|DLT"
Ok "  Contratos: el consumidor no creó duplicado ni mandó al DLT" (-not $conLog) $(if ($conLog) { $conLog[0].Line } else { "sin errores en el log" })
$conEv = Llamar GET "${b}:8085/api/v1/eventos/$eventoId/contrato" $null $token
Ok "  el contrato sigue siendo el mismo y FIRMADO" ($conEv.Json.id -eq $con.Json.id -and $conEv.Json.estado -eq 'FIRMADO') ""

# 12. Asignar un proveedor (valida el evento contra GET /eventos/{id}) y un lugar
$prov = Llamar POST "${b}:8086/api/v1/proveedores" "{`"nombre`":`"Prueba Sonido Fase3 $rnd`",`"categoria`":`"SONIDO`",`"contacto`":`"3009998877`"}" $token
$asig = Llamar POST "${b}:8086/api/v1/proveedores/eventos" "{`"eventoId`":`"$eventoId`",`"proveedorId`":`"$($prov.Json.id)`",`"servicioAsignado`":`"Sonido`",`"costoAcordado`":800000,`"estado`":`"PENDIENTE`"}" $token
Paso "Asignar proveedor al evento" 201 $asig ""
$lugGrande = Llamar POST "${b}:8083/api/v1/lugares" "{`"nombre`":`"Prueba Hacienda Fase3 $rnd`",`"direccion`":`"Km 8 vía Rivera`",`"tipo`":`"HACIENDA`",`"capacidad`":200}" $token
$lugChico = Llamar POST "${b}:8083/api/v1/lugares" "{`"nombre`":`"Prueba Salón Fase3 $rnd`",`"direccion`":`"Cra 5`",`"tipo`":`"SALON`",`"capacidad`":50}" $token
Paso "Asignar lugar con capacidad insuficiente (50 < 120)" 409 (Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/lugar" "{`"lugarId`":`"$($lugChico.Json.id)`"}" $token) ""
$asigLug = Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/lugar" "{`"lugarId`":`"$($lugGrande.Json.id)`"}" $token
Paso "Asignar lugar (capacidad 200)" 200 $asigLug "lugarId=$($asigLug.Json.lugarId)"
$lista = Llamar GET "${b}:8081/api/v1/eventos?estado=RESERVADO&tipo=BODA&desde=$sabado&hasta=$sabado" $null $token
Paso "Listar eventos con filtros (RESERVADO, BODA, fecha)" 200 $lista "totalElementos=$($lista.Json.totalElementos)"

# 13. Cancelar, verificar que la fecha vuelve, cancelar otra vez
$can = Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/cancelar" $null $token
Paso "Cancelar evento" 200 $can "estado=$($can.Json.estado)"
$disp3 = Llamar GET "${b}:8081/api/v1/fechas/disponibles?desde=$viernes&hasta=$lunes" $null $token
$libres3 = @($disp3.Json | ForEach-Object { $_.fecha })
Paso "GET fechas/disponibles (después de cancelar)" 200 $disp3 "disponibles en vie..lun: $($libres3 -join ', ')"
Ok "  viernes, sábado y domingo vuelven a estar disponibles" (($libres3 -contains $viernes) -and ($libres3 -contains $sabado) -and ($libres3 -contains $domingo)) ""
Paso "Cancelar otra vez" 409 (Llamar PATCH "${b}:8081/api/v1/eventos/$eventoId/cancelar" $null $token) ""
Paso "GET evento sin token" 401 (Llamar GET "${b}:8081/api/v1/eventos/$eventoId" $null $null) ""

"`nTOTAL DE FALLOS: $script:fallos"
