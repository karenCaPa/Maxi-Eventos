# Verificación histórica de la Fase 2A (manejo de errores). Algunos casos cambiaron en fases posteriores.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\credenciales.ps1"
function Llamar($metodo, $url, $cuerpo, $token) {
    $h = @{}; if ($token) { $h["Authorization"] = "Bearer $token" }
    $params = @{ Uri = $url; Method = $metodo; Headers = $h; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($null -ne $cuerpo) { $params.Body = [Text.Encoding]::UTF8.GetBytes($cuerpo); $params.ContentType = "application/json; charset=utf-8" }
    try { $r = Invoke-WebRequest @params; $code = [int]$r.StatusCode; $txt = $r.Content }
    catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return [pscustomobject]@{ Codigo = 0; Texto = $_.Exception.Message } }
        $code = [int]$resp.StatusCode
        $sr = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $txt = $sr.ReadToEnd()
    }
    [pscustomobject]@{ Codigo = $code; Texto = $txt }
}
function Mostrar($nombre, $esperado, $r) {
    $ok = if ($r.Codigo -eq $esperado) { "OK " } else { "MAL" }
    $det = ""
    try {
        $j = $r.Texto | ConvertFrom-Json
        if ($j.PSObject.Properties.Name -contains 'message') {
            $det = "message='$($j.message)'"
            if ($j.errores) { $det += " errores=[" + (($j.errores | ForEach-Object { "$($_.campo): $($_.mensaje)" }) -join '; ') + "]" }
            $faltan = @('timestamp','status','error','message','path') | Where-Object { $j.PSObject.Properties.Name -notcontains $_ }
            if ($faltan) { $det += " FALTAN=" + ($faltan -join ',') }
        } elseif ($j.PSObject.Properties.Name -contains 'content') { $det = "página: totalElements=$($j.totalElements)" }
        elseif ($j -is [array]) { $det = "lista de $($j.Count) elementos" }
        else { $det = ($r.Texto.Substring(0, [Math]::Min(80, $r.Texto.Length))) }
    } catch { $det = ($r.Texto.Substring(0, [Math]::Min(80, $r.Texto.Length))) }
    "{0} {1,-58} esperado={2} real={3}  {4}" -f $ok, $nombre, $esperado, $r.Codigo, $det
}

$base = "http://localhost"
$login = Llamar POST "${base}:8080/api/v1/auth/login" (CuerpoLogin) $null
$token = ($login.Texto | ConvertFrom-Json).token
"Login: HTTP $($login.Codigo), token obtenido: $([bool]$token)"
$u = [guid]::NewGuid()

"`n--- auth-service (8080)"
Mostrar "GET /actuator/health" 200 (Llamar GET "${base}:8080/actuator/health" $null $null)
Mostrar "POST /auth/login {} (inválido)" 400 (Llamar POST "${base}:8080/api/v1/auth/login" '{}' $null)
Mostrar "POST /auth/login correo con formato inválido" 400 (Llamar POST "${base}:8080/api/v1/auth/login" '{"correo":"no-es-correo","password":"x"}' $null)
Mostrar "POST /auth/login contraseña incorrecta" 401 (Llamar POST "${base}:8080/api/v1/auth/login" (ConvertTo-Json @{ correo = $CorreoOrganizadora; password = 'Incorrecta1' } -Compress) $null)

"`n--- clientes-service (8082)"
Mostrar "GET /clientes" 200 (Llamar GET "${base}:8082/api/v1/clientes" $null $token)
Mostrar "POST /clientes {} (inválido)" 400 (Llamar POST "${base}:8082/api/v1/clientes" '{}' $token)
Mostrar "GET /clientes/{uuid inexistente}" 404 (Llamar GET "${base}:8082/api/v1/clientes/$u" $null $token)
Mostrar "GET /clientes/abc (UUID inválido)" 400 (Llamar GET "${base}:8082/api/v1/clientes/abc" $null $token)
Mostrar "GET /clientes?sort=clave,asc (sort inválido)" 400 (Llamar GET "${base}:8082/api/v1/clientes?sort=clave,asc" $null $token)
Mostrar "GET /clientes?size=500" 400 (Llamar GET "${base}:8082/api/v1/clientes?size=500" $null $token)
Mostrar "GET /clientes sin token" 401 (Llamar GET "${base}:8082/api/v1/clientes" $null $null)
Mostrar "GET /api/v1/no-existe (ruta inexistente)" 404 (Llamar GET "${base}:8082/api/v1/no-existe" $null $token)
Mostrar "DELETE /clientes (método no permitido)" 405 (Llamar DELETE "${base}:8082/api/v1/clientes" $null $token)

"`n--- eventos-service (8081)"
Mostrar "GET /fechas" 200 (Llamar GET "${base}:8081/api/v1/fechas" $null $token)
Mostrar "POST /simulador/confirmar-evento {} (inválido)" 400 (Llamar POST "${base}:8081/api/v1/simulador/confirmar-evento" '{}' $token)
Mostrar "POST /simulador/confirmar-evento zona inválida" 400 (Llamar POST "${base}:8081/api/v1/simulador/confirmar-evento" '{"nombreEvento":"X","tipoEvento":"BODA","numeroInvitados":10,"zona":"BOGOTA"}' $token)
Mostrar "GET /eventos/{uuid inexistente} (404 con cuerpo)" 404 (Llamar GET "${base}:8081/api/v1/eventos/$u" $null $token)
Mostrar "PUT /fechas/2026-13-40/estado (fecha inválida)" 400 (Llamar PUT "${base}:8081/api/v1/fechas/2026-13-40/estado" '{"estado":"OCUPADA"}' $token)
Mostrar "PUT /fechas/2026-12-05/estado estado inválido" 400 (Llamar PUT "${base}:8081/api/v1/fechas/2026-12-05/estado" '{"estado":"LIBRE"}' $token)

"`n--- lugares-service (8083)"
Mostrar "GET /lugares" 200 (Llamar GET "${base}:8083/api/v1/lugares" $null $token)
Mostrar "POST /lugares {} (inválido)" 400 (Llamar POST "${base}:8083/api/v1/lugares" '{}' $token)
Mostrar "GET /lugares/{uuid inexistente}" 404 (Llamar GET "${base}:8083/api/v1/lugares/$u" $null $token)

"`n--- finanzas-service (8084)"
Mostrar "GET /servicios-adicionales" 200 (Llamar GET "${base}:8084/api/v1/servicios-adicionales" $null $token)
Mostrar "POST /cotizaciones {} (inválido)" 400 (Llamar POST "${base}:8084/api/v1/cotizaciones" '{}' $token)
Mostrar "POST /cotizaciones zona inválida" 400 (Llamar POST "${base}:8084/api/v1/cotizaciones" "{`"eventoId`":`"$u`",`"invitados`":10,`"zona`":`"BOGOTA`"}" $token)
Mostrar "GET /eventos/{uuid}/cotizacion inexistente" 404 (Llamar GET "${base}:8084/api/v1/eventos/$u/cotizacion" $null $token)

"`n--- contratos-service (8085)"
Mostrar "GET /contratos" 200 (Llamar GET "${base}:8085/api/v1/contratos" $null $token)
Mostrar "POST /eventos/{uuid}/contrato {} (inválido)" 400 (Llamar POST "${base}:8085/api/v1/eventos/$u/contrato" '{}' $token)
Mostrar "GET /contratos/{uuid inexistente} (404 con cuerpo)" 404 (Llamar GET "${base}:8085/api/v1/contratos/$u" $null $token)

"`n--- proveedores-service (8086)"
Mostrar "GET /proveedores" 200 (Llamar GET "${base}:8086/api/v1/proveedores" $null $token)
Mostrar "POST /proveedores {} (inválido)" 400 (Llamar POST "${base}:8086/api/v1/proveedores" '{}' $token)
$prov = Llamar POST "${base}:8086/api/v1/proveedores" '{"nombre":"Prueba 2A","categoria":"SONIDO","contacto":"3000000000"}' $token
$provId = ($prov.Texto | ConvertFrom-Json).id
Llamar PATCH "${base}:8086/api/v1/proveedores/$provId/estado" '{"activo":false}' $token | Out-Null
$evento = Llamar POST "${base}:8081/api/v1/simulador/confirmar-evento" '{"nombreEvento":"Prueba 2A","tipoEvento":"BODA","numeroInvitados":50,"zona":"NEIVA"}' $token
$eventoId = ($evento.Texto | ConvertFrom-Json).eventoId
Mostrar "POST /proveedores/eventos proveedor inactivo (antes 404)" 409 (Llamar POST "${base}:8086/api/v1/proveedores/eventos" "{`"eventoId`":`"$eventoId`",`"proveedorId`":`"$provId`",`"servicioAsignado`":`"Sonido`",`"costoAcordado`":100,`"estado`":`"PENDIENTE`"}" $token)
Llamar PATCH "${base}:8086/api/v1/proveedores/$provId/estado" '{"activo":true}' $token | Out-Null
$asig = "{`"eventoId`":`"$eventoId`",`"proveedorId`":`"$provId`",`"servicioAsignado`":`"Sonido`",`"costoAcordado`":100,`"estado`":`"PENDIENTE`"}"
Mostrar "POST /proveedores/eventos asignación OK" 201 (Llamar POST "${base}:8086/api/v1/proveedores/eventos" $asig $token)
Mostrar "POST /proveedores/eventos duplicada (antes 500)" 409 (Llamar POST "${base}:8086/api/v1/proveedores/eventos" $asig $token)
Mostrar "POST /proveedores/eventos evento inexistente" 404 (Llamar POST "${base}:8086/api/v1/proveedores/eventos" "{`"eventoId`":`"$u`",`"proveedorId`":`"$provId`",`"servicioAsignado`":`"Sonido`",`"costoAcordado`":100,`"estado`":`"PENDIENTE`"}" $token)

"`n--- logistica-service (8088)"
Mostrar "GET /eventos/{uuid}/logistica inexistente" 404 (Llamar GET "${base}:8088/api/v1/eventos/$u/logistica" $null $token)
Mostrar "PUT /eventos/{uuid}/compras [{}] (inválido)" 400 (Llamar PUT "${base}:8088/api/v1/eventos/$u/compras" '[{}]' $token)
Mostrar "PUT /eventos/{uuid}/compras cuerpo no JSON" 400 (Llamar PUT "${base}:8088/api/v1/eventos/$u/compras" 'esto no es json' $token)
if ($eventoId) {
  Start-Sleep 3
  Mostrar "GET /eventos/{evento real}/logistica (consumidor Kafka)" 200 (Llamar GET "${base}:8088/api/v1/eventos/$eventoId/logistica" $null $token)
}
