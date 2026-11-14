# Verificación en Docker de la Fase 10C: la interfaz (React + Vite) servida por nginx en http://localhost:5173.
#  1. Contenedor: healthy, 64 MB, sistema de archivos de solo lectura, no-new-privileges, 2 procesos de nginx, fuera de la red
#     de los servicios.
#  2. Cabeceras: CSP estricta con el gateway de FRONTEND_GATEWAY_URL, nosniff, DENY, no-referrer…; caché de cada tipo.
#  3. /config.js generado al arrancar; archivos del build; rutas de la aplicación; 404 de un archivo que no existe; sin
#     source maps ni secretos del .env en el JavaScript; CORS del gateway para el origen de la interfaz.
#  4. Recorrido real en Edge o Chrome sin ventana (navegador_10.mjs): login, listado, ficha de la demostración, calendario,
#     CSP que bloquea otro sitio y un script inyectado, cerrar sesión. Si no hay navegador, se avisa y se omite.
#  5. FRONTEND_GATEWAY_URL se aplica AL ARRANCAR (sin reconstruir la imagen) y un valor inválido impide arrancar.
#     Recrea el contenedor frontend tres veces y lo deja como estaba.
# Solo lee la demostración (no crea ni cambia datos). Requiere el entorno levantado. Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

$Ui = 'http://localhost:5173'
$GatewayUrl = LeerVariableEnv 'FRONTEND_GATEWAY_URL'; if (-not $GatewayUrl) { $GatewayUrl = 'http://localhost:8090' }
$CspEsperada = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; font-src 'self'; connect-src 'self' {0}; manifest-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'"

# GET a la interfaz con HttpClient (sin descompresión automática): estado, cabeceras (respuesta + contenido) y texto UTF-8
function Pedir([string]$Ruta, [hashtable]$Cabeceras = @{}) {
    $msg = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::Get), "$Ui$Ruta"
    foreach ($k in $Cabeceras.Keys) { $msg.Headers.TryAddWithoutValidation($k, [string]$Cabeceras[$k]) | Out-Null }
    try { $resp = $script:Http.SendAsync($msg).GetAwaiter().GetResult() } catch { return [pscustomobject]@{ Status = 0; H = @{}; Texto = '' } }
    $h = @{}
    foreach ($x in $resp.Headers) { $h[$x.Key] = ($x.Value -join ',') }
    foreach ($x in $resp.Content.Headers) { $h[$x.Key] = ($x.Value -join ',') }
    $bytes = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    [pscustomobject]@{ Status = [int]$resp.StatusCode; H = $h; Texto = [Text.Encoding]::UTF8.GetString($bytes); Bytes = $bytes }
}
function Inspeccionar([string]$Formato) { cmd /c "docker inspect frontend --format `"$Formato`" 2>&1" }
function EsperarFrontend([int]$Segundos = 60) {
    $fin = (Get-Date).AddSeconds($Segundos)
    do { Start-Sleep -Seconds 2; $h = Inspeccionar '{{.State.Health.Status}}' } while ($h -ne 'healthy' -and (Get-Date) -lt $fin)
    $h
}
function RecrearFrontend { Compose 'up -d --no-deps --force-recreate frontend' | Out-Null }

Write-Host '== 1. Contenedor'
Check 'frontend healthy' ((EsperarFrontend) -eq 'healthy')
Check 'mem_limit 64 MB' ((Inspeccionar '{{.HostConfig.Memory}}') -eq '67108864')
Check 'sistema de archivos de solo lectura (read_only)' ((Inspeccionar '{{.HostConfig.ReadonlyRootfs}}') -eq 'true')
Check 'no-new-privileges' ((Inspeccionar '{{json .HostConfig.SecurityOpt}}') -match 'no-new-privileges:true')
$redes = Inspeccionar '{{json .NetworkSettings.Networks}}'
Check 'no está en la red de los servicios (maxi-eventos-net)' (-not ($redes -match 'maxi-eventos-net')) $redes
$version = cmd /c "docker exec frontend nginx -v 2>&1"
Check 'nginx 1.28.0 (versión fija)' ($version -match 'nginx/1\.28\.0') $version
$trabajadores = @(cmd /c "docker exec frontend sh -c `"ps -o args | grep -c '[w]orker process'`" 2>&1")[0]
Check 'nginx con 2 procesos de trabajo' ($trabajadores -eq '2') "procesos=$trabajadores"
$memoria = cmd /c "docker stats frontend --no-stream --format {{.MemUsage}} 2>&1"
Write-Host "        memoria en uso: $memoria"

Write-Host "== 2. Página y cabeceras de seguridad (gateway = $GatewayUrl)"
$r = Pedir '/'
Check 'GET / -> 200 text/html; charset=utf-8' ($r.Status -eq 200 -and $r.H['Content-Type'] -eq 'text/html; charset=utf-8') "$($r.Status) $($r.H['Content-Type'])"
Check 'index.html: <div id="root"> y lang="es-CO"' ($r.Texto -match '<div id="root"></div>' -and $r.Texto -match 'lang="es-CO"')
$scriptsEnLinea = [regex]::Matches($r.Texto, '<script(?![^>]*\bsrc=)[^>]*>').Count
Check 'sin scripts en línea ni atributos style' ($scriptsEnLinea -eq 0 -and $r.Texto -notmatch '\sstyle=' -and $r.Texto -notmatch '<style') "en línea=$scriptsEnLinea"
Check 'CSP exacta (connect-src = propio origen + gateway)' ($r.H['Content-Security-Policy'] -eq ($CspEsperada -f $GatewayUrl)) $r.H['Content-Security-Policy']
Check 'X-Content-Type-Options: nosniff' ($r.H['X-Content-Type-Options'] -eq 'nosniff')
Check 'X-Frame-Options: DENY' ($r.H['X-Frame-Options'] -eq 'DENY')
Check 'Referrer-Policy: no-referrer' ($r.H['Referrer-Policy'] -eq 'no-referrer')
Check 'Permissions-Policy sin cámara, micrófono ni ubicación' ($r.H['Permissions-Policy'] -match 'camera=\(\)' -and $r.H['Permissions-Policy'] -match 'geolocation=\(\)')
Check 'Cross-Origin-Opener-Policy y Cross-Origin-Resource-Policy: same-origin' ($r.H['Cross-Origin-Opener-Policy'] -eq 'same-origin' -and $r.H['Cross-Origin-Resource-Policy'] -eq 'same-origin')
Check 'index.html sin caché (Cache-Control: no-cache)' ($r.H['Cache-Control'] -eq 'no-cache') $r.H['Cache-Control']
Check 'Server sin versión (server_tokens off)' ($r.H['Server'] -eq 'nginx') $r.H['Server']

Write-Host '== 3. /config.js, archivos del build y rutas'
$c = Pedir '/config.js'
Check '/config.js -> 200 application/javascript' ($c.Status -eq 200 -and $c.H['Content-Type'] -like 'application/javascript*') "$($c.Status) $($c.H['Content-Type'])"
# Fase 11H: también la zona horaria (ZONA_HORARIA del .env, por defecto America/Bogota)
$ZonaEsperada = LeerVariableEnv 'ZONA_HORARIA'; if (-not $ZonaEsperada) { $ZonaEsperada = 'America/Bogota' }
# Fase 11I: y la fecha simulada del modo demostración (FRONTEND_FECHA_SIMULADA del .env; lo normal: sin definir → "")
$FechaSimuladaEsperada = LeerVariableEnv 'FRONTEND_FECHA_SIMULADA'; if (-not $FechaSimuladaEsperada) { $FechaSimuladaEsperada = '' }
Check '/config.js con la URL del gateway de FRONTEND_GATEWAY_URL, la zona de ZONA_HORARIA y la fecha simulada de FRONTEND_FECHA_SIMULADA' ($c.Texto -eq "window.__CONFIG__ = Object.freeze({ gatewayUrl: `"$GatewayUrl`", zonaHoraria: `"$ZonaEsperada`", fechaSimulada: `"$FechaSimuladaEsperada`" });") $c.Texto
Check '/config.js sin caché y con la misma CSP' ($c.H['Cache-Control'] -eq 'no-cache' -and $c.H['Content-Security-Policy'] -eq ($CspEsperada -f $GatewayUrl))
$archivos = @([regex]::Matches($r.Texto, '(?:src|href)="(/assets/[^"]+)"') | ForEach-Object { $_.Groups[1].Value })
Check 'index.html enlaza el JS y el CSS del build (/assets/…)' (@($archivos | Where-Object { $_ -like '*.js' }).Count -ge 1 -and @($archivos | Where-Object { $_ -like '*.css' }).Count -ge 1) ($archivos -join ', ')
$js = ''
foreach ($a in $archivos) {
    $x = Pedir $a @{ 'Accept-Encoding' = 'gzip' }
    $tipo = if ($a -like '*.js') { 'application/javascript' } else { 'text/css' }
    Check "$a -> 200 $tipo, comprimido y con caché larga" ($x.Status -eq 200 -and $x.H['Content-Type'] -like "$tipo*" -and $x.H['Content-Encoding'] -eq 'gzip' -and $x.H['Cache-Control'] -eq 'max-age=315360000') "$($x.Status) $($x.H['Content-Type']) $($x.H['Content-Encoding']) $($x.H['Cache-Control'])"
    if ($a -like '*.js') {
        $js += (Pedir $a).Texto
        CheckStatus "sin source map ($a.map)" (Pedir "$a.map") 404
    }
}
$no = Pedir '/assets/no-existe.js'
Check '/assets/no-existe.js -> 404 (no index.html) y con la CSP' ($no.Status -eq 404 -and $no.H['Content-Security-Policy']) "$($no.Status)"
$idDemo = 'af79495b-9065-4b83-b08e-83ca7edabc5a'
foreach ($ruta in '/eventos', '/eventos/nuevo', "/eventos/$idDemo", '/lugares', '/proveedores', '/ruta/que/no/existe') {
    $x = Pedir $ruta
    Check "ruta de la aplicación $ruta -> 200 index.html" ($x.Status -eq 200 -and $x.Texto -match '<div id="root"></div>') "$($x.Status)"
}
$hz = Pedir '/healthz'
Check '/healthz -> 200 ok' ($hz.Status -eq 200 -and $hz.Texto -eq 'ok')
$secretos = @('AUTH_JWT_SECRET', 'POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'VERIFICACION_PASSWORD', 'AUTH_ORGANIZER_PASSWORD_HASH') |
    ForEach-Object { LeerVariableEnv $_ } | Where-Object { $_ -and $_.Length -ge 6 }
$filtrados = @($secretos | Where-Object { $js.Contains($_) }).Count
Check "el JavaScript publicado no contiene ningún secreto del .env ($($secretos.Count) revisados, sin mostrarlos)" ($js.Length -gt 0 -and $filtrados -eq 0) "coincidencias=$filtrados"
$pre = Api OPTIONS $script:Url.gateway -SinToken -Cabeceras @{ Origin = $Ui; 'Access-Control-Request-Method' = 'POST'; 'Access-Control-Request-Headers' = 'authorization,content-type' }
Check "CORS: preflight desde $Ui -> 200 con Access-Control-Allow-Origin" ($pre.Status -eq 200 -and $pre.Headers['Access-Control-Allow-Origin'] -eq $Ui) "$($pre.Status)"
$ajeno = Api OPTIONS $script:Url.gateway -SinToken -Cabeceras @{ Origin = 'http://localhost:5174'; 'Access-Control-Request-Method' = 'POST' }
CheckStatus 'CORS: preflight desde otro origen (http://localhost:5174)' $ajeno 403

Write-Host '== 4. Recorrido real en el navegador (Edge o Chrome sin ventana)'
$navegador = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $navegador -or -not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host '  AVISO no se encontró Edge/Chrome o Node: se omite el recorrido en el navegador' -ForegroundColor Yellow
} else {
    $env:MAXI_CORREO = $script:CorreoOrganizadora; $env:MAXI_PASSWORD = $script:PasswordOrganizadora
    try {
        $salida = cmd /c "node `"$PSScriptRoot\navegador_10.mjs`" `"$navegador`" $Ui 2>&1"
    } finally { Remove-Item Env:MAXI_PASSWORD, Env:MAXI_CORREO -ErrorAction SilentlyContinue }
    foreach ($linea in $salida) {
        if ($linea -match '^(OK|FALLO)\s+(.*)$') { Check "navegador: $($Matches[2])" ($Matches[1] -eq 'OK') }
        elseif ($linea) { Write-Host "        $linea" }
    }
}

Write-Host '== 5. FRONTEND_GATEWAY_URL se aplica al arrancar (sin reconstruir la imagen)'
$imagenAntes = Inspeccionar '{{.Image}}'
$otra = 'http://127.0.0.1:8090'
$env:FRONTEND_GATEWAY_URL = $otra
try {
    RecrearFrontend
    Check "recreado con FRONTEND_GATEWAY_URL=$otra -> healthy" ((EsperarFrontend) -eq 'healthy')
    Check 'la misma imagen (no se reconstruyó)' ((Inspeccionar '{{.Image}}') -eq $imagenAntes)
    Check '/config.js con la URL nueva' ((Pedir '/config.js').Texto -match [regex]::Escape("gatewayUrl: `"$otra`""))
    Check 'la CSP con la URL nueva en connect-src' ((Pedir '/').H['Content-Security-Policy'] -eq ($CspEsperada -f $otra))

    $env:FRONTEND_GATEWAY_URL = 'http://localhost:8090"; alert(1); "'
    RecrearFrontend
    Start-Sleep -Seconds 6
    $estado = Inspeccionar '{{.State.Status}} {{.State.Restarting}}'
    Check 'con un valor inválido (comillas y código) el contenedor NO arranca' ($estado -notmatch '^running false') $estado
    $log = cmd /c "docker logs frontend 2>&1" | Out-String
    Check 'el log explica por qué (FRONTEND_GATEWAY_URL no es válida)' ($log -match 'FRONTEND_GATEWAY_URL no es v')
    Check 'no sirve nada mientras tanto (la página no responde)' ((Pedir '/').Status -eq 0)
} finally {
    Remove-Item Env:FRONTEND_GATEWAY_URL -ErrorAction SilentlyContinue
    RecrearFrontend
}
Check 'restaurado con el valor del .env -> healthy' ((EsperarFrontend) -eq 'healthy')
Check "/config.js otra vez con $GatewayUrl" ((Pedir '/config.js').Texto -match [regex]::Escape("gatewayUrl: `"$GatewayUrl`""))

Write-Host '== 6. FRONTEND_FECHA_SIMULADA (modo demostración, Fase 11I) se aplica al arrancar'
$env:FRONTEND_FECHA_SIMULADA = '2026-12-10'
try {
    RecrearFrontend
    Check 'recreado con FRONTEND_FECHA_SIMULADA=2026-12-10 -> healthy' ((EsperarFrontend) -eq 'healthy')
    Check 'la misma imagen (no se reconstruyó)' ((Inspeccionar '{{.Image}}') -eq $imagenAntes)
    Check '/config.js con fechaSimulada "2026-12-10"' ((Pedir '/config.js').Texto -match [regex]::Escape('fechaSimulada: "2026-12-10"'))
    $log = cmd /c "docker logs frontend 2>&1" | Out-String
    Check 'el log avisa del MODO DEMOSTRACIÓN' ($log -match 'MODO DEMOSTRACI')

    foreach ($malo in @('2026-02-30', '10/12/2026', '2026-12-10"; alert(1); "')) {
        $env:FRONTEND_FECHA_SIMULADA = $malo
        RecrearFrontend
        Start-Sleep -Seconds 6
        $estado = Inspeccionar '{{.State.Status}} {{.State.Restarting}}'
        $log = cmd /c "docker logs frontend 2>&1" | Out-String
        Check "con FRONTEND_FECHA_SIMULADA='$malo' el contenedor NO arranca y el log dice por qué" ($estado -notmatch '^running false' -and $log -match 'FRONTEND_FECHA_SIMULADA no es v') $estado
    }
} finally {
    Remove-Item Env:FRONTEND_FECHA_SIMULADA -ErrorAction SilentlyContinue
    RecrearFrontend
}
Check 'restaurado con el valor del .env -> healthy' ((EsperarFrontend) -eq 'healthy')
Check "/config.js otra vez con fechaSimulada `"$FechaSimuladaEsperada`"" ((Pedir '/config.js').Texto -match [regex]::Escape("fechaSimulada: `"$FechaSimuladaEsperada`""))

Resumen 'Verificación 10C'
