# Verificación en Docker de la subfase 7B: seguridad de los 10 servicios.
# - Contraseña rotada: la de ejemplo de .env.example → 401 "Correo o contraseña incorrectos." (ErrorResponse); la del .env → 200.
# - auth-service con Spring Security: login, health y Swagger sin token; actuator info/metrics con token.
# - En los 10: 401 con ErrorResponse (sin token, manipulado, sin iss = emitido antes de la 7A, otro emisor, vencido),
#   ruta con id vacío (//) → 400 con ErrorResponse, solo health público en actuator, /v3/api-docs público.
# - El mismo rechazo por GraphQL (gateway) para el token sin iss.
# Los tokens sin iss y de otro emisor se firman en memoria con AUTH_JWT_SECRET del .env (nunca se muestran).
# Recrea auth-service dos veces (AUTH_TOKEN_TTL=PT15S para el token vencido y de vuelta). Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

$servicios = [ordered]@{
    auth           = @{ Puerto = 8080; Ruta = '/actuator/info' }
    eventos        = @{ Puerto = 8081; Ruta = '/api/v1/eventos' }
    clientes       = @{ Puerto = 8082; Ruta = '/api/v1/clientes' }
    lugares        = @{ Puerto = 8083; Ruta = '/api/v1/lugares' }
    finanzas       = @{ Puerto = 8084; Ruta = '/api/v1/servicios-adicionales' }
    contratos      = @{ Puerto = 8085; Ruta = '/api/v1/contratos' }
    proveedores    = @{ Puerto = 8086; Ruta = '/api/v1/proveedores' }
    auditoria      = @{ Puerto = 8087; Ruta = '/api/v1/auditoria' }
    logistica      = @{ Puerto = 8088; Ruta = '/api/v1/eventos/3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b/compras' }
    notificaciones = @{ Puerto = 8089; Ruta = '/api/v1/notificaciones' }
}
$MSG_SIN = 'Debes iniciar sesión*'; $MSG_INV = 'El token no es válido*'; $MSG_VEN = 'El token venció*'

function B64Url([byte[]]$Bytes) { [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
function FirmarJwt([hashtable]$Claims) {
    $cab = B64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $carga = B64Url ([Text.Encoding]::UTF8.GetBytes((ConvertTo-Json $Claims -Compress)))
    $hmac = New-Object Security.Cryptography.HMACSHA256 (, [Text.Encoding]::UTF8.GetBytes((LeerVariableEnv 'AUTH_JWT_SECRET')))
    try { "$cab.$carga." + (B64Url $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$cab.$carga"))) } finally { $hmac.Dispose() }
}
function ErrorResponse401($R, [string]$Ruta, [string]$Mensaje) {
    $R.Status -eq 401 -and $R.Body.status -eq 401 -and $R.Body.error -eq 'Unauthorized' -and $R.Body.message -like $Mensaje -and
        $R.Body.path -eq $Ruta -and $R.Body.timestamp
}
function ConCabecera([string]$Url, [string]$Token) { Api GET $Url -SinToken -Cabeceras @{ Authorization = "Bearer $Token" } }

Write-Host '== 1. Contraseña rotada (el script lee VERIFICACION_PASSWORD del .env)'
$ejemplo = [regex]::Match((Get-Content (Join-Path $script:RaizProyecto '.env.example') -Raw -Encoding UTF8), 'contraseña de ejemplo "([^"]+)"').Groups[1].Value
Check 'Se leyó la contraseña de ejemplo de .env.example (sin mostrarla)' ([bool]$ejemplo)
$r = Api POST "$($Url.auth)/auth/login" @{ correo = $script:CorreoOrganizadora; password = $ejemplo } -SinToken
Check 'login con la contraseña de ejemplo -> 401 "Correo o contraseña incorrectos." (ErrorResponse)' ($r.Status -eq 401 -and $r.Body.status -eq 401 -and $r.Body.error -eq 'Unauthorized' -and $r.Body.message -eq 'Correo o contraseña incorrectos.' -and $r.Body.path -eq '/api/v1/auth/login') $r.Texto
$r = Login
Check 'login con VERIFICACION_PASSWORD (la nueva) -> 200 con token' ($r.Status -eq 200 -and $script:Token)
$ejemplo = $null

Write-Host '== 2. auth-service con Spring Security: qué sigue público'
CheckStatus 'auth: login sin token (POST /auth/login)' $r 200
CheckStatus 'auth: GET /actuator/health sin token' (Api GET 'http://localhost:8080/actuator/health' -SinToken) 200
CheckStatus 'auth: GET /v3/api-docs sin token' (Api GET 'http://localhost:8080/v3/api-docs' -SinToken) 200
CheckStatus 'auth: GET /swagger-ui/index.html sin token' (Api GET 'http://localhost:8080/swagger-ui/index.html' -SinToken) 200
CheckStatus 'auth: GET /actuator/metrics con token' (Api GET 'http://localhost:8080/actuator/metrics') 200

Write-Host '== 3. Los 10 servicios: actuator, documentación, 401 con ErrorResponse y rutas con id vacío'
$token = $script:Token
$p = $token.Split('.')
$cargaManipulada = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($p[1].Replace('-', '+').Replace('_', '/') + '==='.Substring(0, (4 - $p[1].Length % 4) % 4)))).Replace('"sub":"', '"sub":"x')
$manipulado = "$($p[0]).$(B64Url ([Text.Encoding]::UTF8.GetBytes($cargaManipulada))).$($p[2])"
$ahora = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$sinIss = FirmarJwt @{ sub = 'organizadora@antes-de-la-7a'; iat = $ahora; exp = $ahora + 3600 }
$otroEmisor = FirmarJwt @{ sub = '3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b'; iss = 'otro-emisor'; iat = $ahora; exp = $ahora + 3600 }
foreach ($s in $servicios.Keys) {
    # (no llamar $url a la variable: PowerShell no distingue mayúsculas y pisaría la tabla $Url de comun.ps1)
    $base = "http://localhost:$($servicios[$s].Puerto)"; $ruta = $servicios[$s].Ruta; $destino = "$base$ruta"
    CheckStatus "$s`: /actuator/health sin token" (Api GET "$base/actuator/health" -SinToken) 200
    foreach ($ep in 'info', 'metrics') {
        Check "$s`: /actuator/$ep sin token -> 401 ErrorResponse" (ErrorResponse401 (Api GET "$base/actuator/$ep" -SinToken) "/actuator/$ep" $MSG_SIN)
    }
    CheckStatus "$s`: /actuator/metrics con token" (Api GET "$base/actuator/metrics") 200
    CheckStatus "$s`: /v3/api-docs sin token (documentación pública)" (Api GET "$base/v3/api-docs" -SinToken) 200
    Check "$s`: $ruta sin token -> 401 'Debes iniciar sesión…'" (ErrorResponse401 (Api GET $destino -SinToken) $ruta $MSG_SIN)
    Check "$s`: token manipulado -> 401 'El token no es válido…'" (ErrorResponse401 (ConCabecera $destino $manipulado) $ruta $MSG_INV)
    Check "$s`: token SIN iss (emitido antes de la 7A) -> 401 'no es válido'" (ErrorResponse401 (ConCabecera $destino $sinIss) $ruta $MSG_INV)
    Check "$s`: token de otro emisor -> 401 'no es válido'" (ErrorResponse401 (ConCabecera $destino $otroEmisor) $ruta $MSG_INV)
    $vacio = Api GET "$base/api/v1/eventos//contrato"
    Check "$s`: /api/v1/eventos//contrato (id vacío) -> 400 ErrorResponse 'La ruta no es válida…'" ($vacio.Status -eq 400 -and $vacio.Body.status -eq 400 -and $vacio.Body.error -eq 'Bad Request' -and $vacio.Body.message -like 'La ruta no es válida*') "status=$($vacio.Status) $($vacio.Texto)"
}
CheckStatus 'contratos: GET /eventos/{id}/contrato con token (la ruta normal sigue funcionando; 404 si no hay contrato)' (Api GET "$($Url.contratos)/eventos/3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b/contrato") 404

Write-Host '== 4. El mismo token sin iss por GraphQL: lo rechaza el gateway'
$r = Api POST $Url.gateway @{ query = '{ eventos(size: 1) { totalElementos } }' } -SinToken -Cabeceras @{ Authorization = "Bearer $sinIss" }
$e = ErrorGraphQL $r
Check 'gateway: token sin iss -> UNAUTHORIZED "El token no es válido…"' ($e.extensions.classification -eq 'UNAUTHORIZED' -and $e.message -like $MSG_INV)

Write-Host '== 5. Token vencido en los 10 servicios (AUTH_TOKEN_TTL=PT15S solo para esta prueba)'
$env:AUTH_TOKEN_TTL = 'PT15S'
try {
    Compose 'up -d auth-service' | Out-Null
    Check 'auth-service healthy con AUTH_TOKEN_TTL=PT15S' ((EsperarHealthy 'auth-service') -eq 'healthy')
    $r = Api POST "$($Url.auth)/auth/login" (CuerpoLogin) -SinToken
    Check 'login -> expiracionEnSegundos = 15' ($r.Body.expiracionEnSegundos -eq 15) $r.Texto
    $corto = $r.Body.token
    CheckStatus 'token de 15 s recién emitido: eventos' (ConCabecera "$($Url.eventos)/eventos?size=1" $corto) 200
    Start-Sleep -Seconds 20
    foreach ($s in $servicios.Keys) {
        $ruta = $servicios[$s].Ruta
        Check "$s`: token vencido -> 401 'El token venció…'" (ErrorResponse401 (ConCabecera "http://localhost:$($servicios[$s].Puerto)$ruta" $corto) $ruta $MSG_VEN)
    }
} finally {
    Remove-Item Env:\AUTH_TOKEN_TTL -ErrorAction SilentlyContinue
    Compose 'up -d auth-service' | Out-Null
}
Check 'auth-service de vuelta con la vigencia normal' ((EsperarHealthy 'auth-service') -eq 'healthy')
$r = Login
Check 'login -> vigencia normal otra vez (> 15 s)' ($r.Body.expiracionEnSegundos -gt 15)

Resumen 'verificar_7B'
