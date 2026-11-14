# Verificación histórica de la Fase 2B (capas, paginación y caché). Algunos casos cambiaron en fases posteriores.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\credenciales.ps1"
$script:fallos = 0
function Llamar($metodo, $url, $cuerpo, $token, $extra) {
    $h = @{}; if ($token) { $h["Authorization"] = "Bearer $token" }; if ($extra) { $extra.Keys | ForEach-Object { $h[$_] = $extra[$_] } }
    $params = @{ Uri = $url; Method = $metodo; Headers = $h; UseBasicParsing = $true; TimeoutSec = 30 }
    if ($null -ne $cuerpo) { $params.Body = [Text.Encoding]::UTF8.GetBytes($cuerpo); $params.ContentType = "application/json; charset=utf-8" }
    try { $r = Invoke-WebRequest @params; $code = [int]$r.StatusCode; $txt = $r.Content; $loc = $r.Headers["Location"] }
    catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return [pscustomobject]@{ Codigo = 0; Texto = $_.Exception.Message; Location = $null; Json = $null } }
        $code = [int]$resp.StatusCode; $loc = $null
        $sr = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $txt = $sr.ReadToEnd()
    }
    $json = $null; try { $json = $txt | ConvertFrom-Json } catch {}
    [pscustomobject]@{ Codigo = $code; Texto = $txt; Location = $loc; Json = $json }
}
function Ok($nombre, $condicion, $detalle) {
    if ($condicion) { "OK  $nombre  $detalle" } else { $script:fallos++; "MAL $nombre  $detalle" }
}
function LogsCache($contenedor, $desde) {
    docker logs $contenedor --since $desde 2>&1 | Select-String "maxieventos.cache" | ForEach-Object { "      " + ($_.Line -replace '^.*maxieventos\.cache\s*:\s*', '') }
}
function Ahora() { (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss") + "Z" }

$b = "http://localhost"
$token = (Llamar POST "${b}:8080/api/v1/auth/login" (CuerpoLogin) $null).Json.token
Ok "Login" ([bool]$token) ""
$sufijo = Get-Random -Minimum 100000 -Maximum 999999

"`n===== 1. Logística (antes 500): dos consultas, la segunda debe salir de la caché"
$ev = Llamar POST "${b}:8081/api/v1/simulador/confirmar-evento" "{`"nombreEvento`":`"Prueba Boda 2B $sufijo`",`"tipoEvento`":`"BODA`",`"numeroInvitados`":120,`"zona`":`"RURAL`"}" $token
$eventoId = $ev.Json.eventoId
Ok "Evento confirmado (simulador)" ($ev.Codigo -eq 202) "HTTP $($ev.Codigo) eventoId=$eventoId"
Start-Sleep 4
$t0 = Ahora
$l1 = Llamar GET "${b}:8088/api/v1/eventos/$eventoId/logistica" $null $token
$l2 = Llamar GET "${b}:8088/api/v1/eventos/$eventoId/logistica" $null $token
Ok "GET logística 1ª vez" ($l1.Codigo -eq 200) "HTTP $($l1.Codigo) meseros=$($l1.Json.meserosRequeridos) cocina=$($l1.Json.personalCocinaRequerido) camiones=$($l1.Json.camionesTransporte)"
Ok "GET logística 2ª vez (mismo resultado)" ($l2.Codigo -eq 200 -and $l2.Texto -eq $l1.Texto) "HTTP $($l2.Codigo)"
Start-Sleep 1; "   Log de caché de logistica_service:"; LogsCache logistica_service $t0

"`n===== 2. Lugar por id: MISS, HIT, actualización (PUT) y dato nuevo"
$lug = Llamar POST "${b}:8083/api/v1/lugares" "{`"nombre`":`"Prueba Hacienda 2B $sufijo`",`"direccion`":`"Km 5 vía Rivera`",`"tipo`":`"HACIENDA`",`"capacidad`":200}" $token
$lugarId = $lug.Json.id
Ok "POST lugar → 201 + Location" ($lug.Codigo -eq 201 -and $lug.Location -like "*/api/v1/lugares/$lugarId") "Location=$($lug.Location)"
$t0 = Ahora
$g1 = Llamar GET "${b}:8083/api/v1/lugares/$lugarId" $null $token
$g2 = Llamar GET "${b}:8083/api/v1/lugares/$lugarId" $null $token
$put = Llamar PUT "${b}:8083/api/v1/lugares/$lugarId" "{`"nombre`":`"Prueba Hacienda 2B $sufijo`",`"direccion`":`"Km 5 vía Rivera`",`"tipo`":`"HACIENDA`",`"capacidad`":350}" $token
$g3 = Llamar GET "${b}:8083/api/v1/lugares/$lugarId" $null $token
Ok "GET lugar antes del PUT" ($g1.Json.capacidad -eq 200 -and $g2.Json.capacidad -eq 200) "capacidad=$($g1.Json.capacidad)/$($g2.Json.capacidad)"
Ok "PUT lugar → 200" ($put.Codigo -eq 200) ""
Ok "GET lugar después del PUT devuelve el dato NUEVO" ($g3.Json.capacidad -eq 350) "capacidad=$($g3.Json.capacidad)"
Start-Sleep 1; "   Log de caché de lugares_service:"; LogsCache lugares_service $t0

"`n===== 3. Listado de lugares cacheado (PaginaResponse) y CLEAR al crear"
$t0 = Ahora
$p1 = Llamar GET "${b}:8083/api/v1/lugares?size=100" $null $token
$p2 = Llamar GET "${b}:8083/api/v1/lugares?size=100" $null $token
Ok "Formato PaginaResponse" ($p1.Json.PSObject.Properties.Name -join ',' -eq 'contenido,pagina,tamanoPagina,totalElementos,totalPaginas') "campos=$($p1.Json.PSObject.Properties.Name -join ',')"
$nuevo = Llamar POST "${b}:8083/api/v1/lugares" "{`"nombre`":`"Prueba Salón 2B $sufijo`",`"direccion`":`"Cra 5 # 10-20`",`"tipo`":`"SALON`",`"capacidad`":80}" $token
$p3 = Llamar GET "${b}:8083/api/v1/lugares?size=100" $null $token
Ok "Listado tras crear incluye el nuevo (no dato viejo)" ($p3.Json.totalElementos -eq $p1.Json.totalElementos + 1) "totalElementos $($p1.Json.totalElementos) → $($p3.Json.totalElementos)"
Start-Sleep 1; "   Log de caché de lugares_service:"; LogsCache lugares_service $t0

"`n===== 4. Proveedor por id (GET /proveedores/{id} nuevo) y cambio de estado"
$prov = Llamar POST "${b}:8086/api/v1/proveedores" "{`"nombre`":`"Prueba Sonido 2B $sufijo`",`"categoria`":`"SONIDO`",`"contacto`":`"3001234567`"}" $token
$provId = $prov.Json.id
Ok "POST proveedor → 201 + Location" ($prov.Codigo -eq 201 -and $prov.Location -like "*/api/v1/proveedores/$provId") "Location=$($prov.Location)"
$t0 = Ahora
$pv1 = Llamar GET "${b}:8086/api/v1/proveedores/$provId" $null $token
$pv2 = Llamar GET "${b}:8086/api/v1/proveedores/$provId" $null $token
$est = Llamar PATCH "${b}:8086/api/v1/proveedores/$provId/estado" '{"activo":false}' $token
$pv3 = Llamar GET "${b}:8086/api/v1/proveedores/$provId" $null $token
Ok "GET proveedor (2 veces) activo=true" ($pv1.Json.activo -eq $true -and $pv2.Json.activo -eq $true) ""
Ok "Tras PATCH estado, GET devuelve activo=false (no dato viejo)" ($est.Codigo -eq 200 -and $pv3.Json.activo -eq $false) "activo=$($pv3.Json.activo)"
Ok "GET /proveedores/{uuid inexistente} → 404" ((Llamar GET "${b}:8086/api/v1/proveedores/$([guid]::NewGuid())" $null $token).Codigo -eq 404) ""
Llamar PATCH "${b}:8086/api/v1/proveedores/$provId/estado" '{"activo":true}' $token | Out-Null
$asig = Llamar POST "${b}:8086/api/v1/proveedores/eventos" "{`"eventoId`":`"$eventoId`",`"proveedorId`":`"$provId`",`"servicioAsignado`":`"Sonido`",`"costoAcordado`":500000,`"estado`":`"PENDIENTE`"}" $token
Ok "POST asignación → 201 + Location" ($asig.Codigo -eq 201 -and $asig.Location -like "*/api/v1/proveedores/eventos/$eventoId") "Location=$($asig.Location)"
Start-Sleep 1; "   Log de caché de proveedores_service:"; LogsCache proveedores_service $t0

"`n===== 5. Cliente por id: caché, PUT, DELETE"
$cli = Llamar POST "${b}:8082/api/v1/clientes" "{`"nombreCompleto`":`"Prueba Ana $sufijo`",`"telefono`":`"310$sufijo`",`"correo`":`"ana.$sufijo@example.com`",`"direccion`":`"Neiva`"}" $token
$cliId = $cli.Json.id
Ok "POST cliente → 201 + Location" ($cli.Codigo -eq 201 -and $cli.Location -like "*/api/v1/clientes/$cliId") "Location=$($cli.Location)"
$t0 = Ahora
$c1 = Llamar GET "${b}:8082/api/v1/clientes/$cliId" $null $token
$c2 = Llamar GET "${b}:8082/api/v1/clientes/$cliId" $null $token
$cput = Llamar PUT "${b}:8082/api/v1/clientes/$cliId" "{`"nombreCompleto`":`"Prueba Ana Actualizada $sufijo`",`"telefono`":`"310$sufijo`",`"correo`":`"ana.$sufijo@example.com`",`"direccion`":`"Neiva`"}" $token
$c3 = Llamar GET "${b}:8082/api/v1/clientes/$cliId" $null $token
Ok "Tras PUT, GET devuelve el nombre nuevo" ($c3.Json.nombreCompleto -eq "Ana Actualizada $sufijo") "nombre=$($c3.Json.nombreCompleto)"
$del = Llamar DELETE "${b}:8082/api/v1/clientes/$cliId" $null $token
$c4 = Llamar GET "${b}:8082/api/v1/clientes/$cliId" $null $token
Ok "DELETE → 204 y luego GET → 404 (no se sirve de la caché)" ($del.Codigo -eq 204 -and $c4.Codigo -eq 404) "DELETE=$($del.Codigo) GET=$($c4.Codigo)"
$lc = Llamar GET "${b}:8082/api/v1/clientes?nombre=Prueba" $null $token
Ok "Listado de clientes con PaginaResponse" ($lc.Codigo -eq 200 -and ($lc.Json.PSObject.Properties.Name -contains 'contenido')) ""
Start-Sleep 1; "   Log de caché de clientes_service:"; LogsCache clientes_service $t0

"`n===== 6. Claves y TTL en Redis (solo nombres de clave y TTL)"
$claves = docker exec redis_cache sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern "maxi:*"'
foreach ($k in ($claves | Sort-Object)) { $ttl = docker exec redis_cache sh -c "redis-cli -a `"`$REDIS_PASSWORD`" --no-auth-warning TTL '$k'"; "   $k  TTL=$ttl s" }
Ok "Hay claves de caché con prefijo maxi:" (@($claves).Count -gt 0) "total=$(@($claves).Count)"
$valor = docker exec redis_cache sh -c "redis-cli -a `"`$REDIS_PASSWORD`" --no-auth-warning GET 'maxi:lugares:lugares::$lugarId'"
Ok "Valor del lugar en Redis es JSON legible" ($valor -like '*"capacidad":350*') "valor=$valor"

"`n===== 7. 201/200 en cotización, contrato y pago; 201 en adicional"
$cot1 = Llamar POST "${b}:8084/api/v1/cotizaciones" "{`"eventoId`":`"$eventoId`",`"invitados`":120,`"zona`":`"RURAL`"}" $token
$cot2 = Llamar POST "${b}:8084/api/v1/cotizaciones" "{`"eventoId`":`"$eventoId`",`"invitados`":120,`"zona`":`"RURAL`"}" $token
Ok "Cotización nueva → 201 + Location" ($cot1.Codigo -eq 201 -and $cot1.Location -like "*/api/v1/eventos/$eventoId/cotizacion") "Location=$($cot1.Location)"
Ok "Cotización repetida → 200 (misma cotización)" ($cot2.Codigo -eq 200 -and $cot2.Json.id -eq $cot1.Json.id) ""
$con1 = Llamar POST "${b}:8085/api/v1/eventos/$([guid]::NewGuid())/contrato" '{"nombreEvento":"Contrato 2B"}' $token
$eventoContrato = ($con1.Json.eventoId)
$con2 = Llamar POST "${b}:8085/api/v1/eventos/$eventoContrato/contrato" '{"nombreEvento":"Contrato 2B"}' $token
Ok "Contrato nuevo → 201 + Location" ($con1.Codigo -eq 201 -and $con1.Location -like "*/api/v1/contratos/$($con1.Json.id)") "Location=$($con1.Location)"
Ok "Contrato repetido → 200 (mismo contrato)" ($con2.Codigo -eq 200 -and $con2.Json.id -eq $con1.Json.id) ""
$clave = [guid]::NewGuid()
$pago = "{`"eventoId`":`"$eventoId`",`"clienteId`":`"$([guid]::NewGuid())`",`"metodoPago`":`"TRANSFERENCIA`",`"monto`":500000,`"moneda`":`"COP`"}"
$pg1 = Llamar POST "${b}:8084/api/v1/finanzas/pagos" $pago $token @{ "X-Idempotency-Key" = "$clave" }
$pg2 = Llamar POST "${b}:8084/api/v1/finanzas/pagos" $pago $token @{ "X-Idempotency-Key" = "$clave" }
Ok "Pago nuevo → 201 + Location (antes 202)" ($pg1.Codigo -eq 201 -and $pg1.Location -like "*/api/v1/eventos/$eventoId/pagos") "Location=$($pg1.Location)"
Ok "Pago repetido (misma clave) → 200, mismo pagoId" ($pg2.Codigo -eq 200 -and $pg2.Json.pagoId -eq $pg1.Json.pagoId) ""
$ad = Llamar POST "${b}:8084/api/v1/eventos/$eventoId/adicionales" '{"codigo":"hora-extra","cantidad":2}' $token
Ok "Adicional → 201 + Location" ($ad.Codigo -eq 201 -and $ad.Location -like "*/api/v1/eventos/$eventoId/adicionales") "Location=$($ad.Location)"
$bal = Llamar GET "${b}:8084/api/v1/eventos/$eventoId/balance" $null $token
Ok "Balance (BalanceResponse)" ($bal.Codigo -eq 200 -and $null -ne $bal.Json.saldoPendiente) "total=$($bal.Json.total) abonado=$($bal.Json.totalAbonado) saldo=$($bal.Json.saldoPendiente)"
Ok "Catálogo de adicionales (no cacheado)" ((Llamar GET "${b}:8084/api/v1/servicios-adicionales" $null $token).Codigo -eq 200) ""

"`n===== 8. Paginación validada en todos los listados"
foreach ($u in "${b}:8082/api/v1/clientes","${b}:8083/api/v1/lugares","${b}:8086/api/v1/proveedores","${b}:8086/api/v1/proveedores/eventos/$eventoId","${b}:8085/api/v1/contratos","${b}:8088/api/v1/eventos/$eventoId/compras") {
  $r1 = Llamar GET "${u}?size=101" $null $token; $r2 = Llamar GET "${u}?sort=noExiste,asc" $null $token; $r3 = Llamar GET "${u}?page=-1" $null $token; $r4 = Llamar GET "${u}?page=0&size=2" $null $token
  Ok ("{0,-48}" -f ($u -replace 'http://localhost','')) ($r1.Codigo -eq 400 -and $r2.Codigo -eq 400 -and $r3.Codigo -eq 400 -and $r4.Codigo -eq 200 -and $r4.Json.tamanoPagina -eq 2) "size=101→$($r1.Codigo) sort inválido→$($r2.Codigo) page=-1→$($r3.Codigo) size=2→$($r4.Codigo) tamanoPagina=$($r4.Json.tamanoPagina)"
}
$err = Llamar GET "${b}:8083/api/v1/lugares?sort=noExiste,asc" $null $token
"   ejemplo de error de sort: $($err.Json.errores[0].campo): $($err.Json.errores[0].mensaje)"

"`n===== 9. Redis caído: la aplicación sigue respondiendo desde la base de datos"
docker stop redis_cache | Out-Null
$t0 = Ahora
$rd1 = Llamar GET "${b}:8083/api/v1/lugares/$lugarId" $null $token
$rd2 = Llamar GET "${b}:8088/api/v1/eventos/$eventoId/logistica" $null $token
Ok "Con Redis detenido: GET lugar → 200 desde la BD" ($rd1.Codigo -eq 200 -and $rd1.Json.capacidad -eq 350) "HTTP $($rd1.Codigo)"
Ok "Con Redis detenido: GET logística → 200 desde la BD" ($rd2.Codigo -eq 200) "HTTP $($rd2.Codigo)"
Start-Sleep 1; "   Log de caché de lugares_service:"; LogsCache lugares_service $t0
docker start redis_cache | Out-Null
"`nTOTAL DE FALLOS: $script:fallos"
