# Verificación en Docker de la subfase 7A: el gateway valida el JWT (firma, vigencia y emisor) antes de llamar a ningún
# servicio, salvo la mutation login; CORS explícito (CORS_ALLOWED_ORIGINS); actuator del gateway solo health público;
# introspección con token; el token de Auth ya no lleva el correo (sub = AUTH_ORGANIZER_ID, iss = maxi-eventos-auth).
# Recrea auth-service dos veces (vigencia corta para el token vencido y de vuelta a la normal).
# Requiere el entorno levantado con el perfil dev en el gateway (GATEWAY_SPRING_PROFILES=dev). Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

$Base = 'http://localhost:8090'
function Clasificacion($R) { $e = ErrorGraphQL $R; if ($e) { $e.extensions.classification } }
function LlamadasRest([string]$Log) { @([regex]::Matches($Log, 'REST -> \w+ ') ).Count }
function Carga([string]$Token) {
    $c = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/'); while ($c.Length % 4) { $c += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($c))
}
$consulta = '{ eventos(size: 1) { totalElementos } }'
$origenPermitido = (LeerVariableEnv 'CORS_ALLOWED_ORIGINS').Split(',')[0].Trim()
$origenAjeno = 'http://sitio-ajeno.example'

Write-Host '== 1. login sin token (única operación permitida sin token) y claims del JWT'
$r = GraphQL 'mutation($c: String!, $p: String!) { login(correo: $c, password: $p) { token tipoToken expiracionEnSegundos } }' @{ c = $script:CorreoOrganizadora; p = $script:PasswordOrganizadora } -SinToken
Check 'login sin token -> token Bearer' ($r.Status -eq 200 -and -not $r.Body.errors -and $r.Body.data.login.tipoToken -eq 'Bearer')
$script:Token = $r.Body.data.login.token
$jwt = Carga $script:Token | ConvertFrom-Json
$idEsperado = LeerVariableEnv 'AUTH_ORGANIZER_ID'
Check 'sub = id opaco (UUID) de AUTH_ORGANIZER_ID' ($jwt.sub -match '^[0-9a-f-]{36}$' -and (-not $idEsperado -or $jwt.sub -eq $idEsperado)) "sub=$($jwt.sub)"
Check 'iss = maxi-eventos-auth' ($jwt.iss -eq 'maxi-eventos-auth') "iss=$($jwt.iss)"
Check 'El correo NO viaja en el token' (-not ((Carga $script:Token) -match '@')) 'la carga contiene una @'
# Fase 11D: además el claim rol (no es un dato sensible); la organizadora es ADMINISTRADOR
Check 'Solo los claims sub, iss, iat, exp y rol' (((@($jwt.PSObject.Properties.Name) | Sort-Object) -join ',') -eq 'exp,iat,iss,rol,sub') "claims=$(@($jwt.PSObject.Properties.Name) -join ',')"
Check 'rol = ADMINISTRADOR (la organizadora)' ($jwt.rol -eq 'ADMINISTRADOR') "rol=$($jwt.rol)"
Check 'Vigencia = AUTH_TOKEN_TTL (exp - iat = expiracionEnSegundos)' (([long]$jwt.exp - [long]$jwt.iat) -eq [long]$r.Body.data.login.expiracionEnSegundos)

Write-Host '== 2. Con token válido: el gateway llama a los servicios y ellos vuelven a validar el token'
$desde = Get-Date; Start-Sleep -Milliseconds 1200
$r = GraphQL $consulta
Check 'query con token -> datos' ($r.Status -eq 200 -and -not $r.Body.errors -and $null -ne $r.Body.data.eventos.totalElementos)
Start-Sleep -Seconds 1
# (docker logs --since trabaja con segundos completos: puede incluir el login del mismo segundo; se cuentan solo las de eventos)
$n = @([regex]::Matches((LogsDesde 'gateway' $desde), 'REST -> eventos GET /api/v1/eventos\?')).Count
Check 'log del gateway: 1 llamada REST a eventos' ($n -eq 1) "llamadas=$n"
CheckStatus 'Defensa en profundidad: REST directo a eventos-service sin token' (Api GET "$($Url.eventos)/eventos?size=1" -SinToken) 401
CheckStatus 'REST directo a eventos-service con el token nuevo (sub UUID, iss)' (Api GET "$($Url.eventos)/eventos?size=1") 200

Write-Host '== 3. Rechazos en el gateway: UNAUTHORIZED sin llamar a ningún servicio'
$partes = $script:Token.Split('.')
$cargaManipulada = (Carga $script:Token).Replace('"sub":"', '"sub":"x')
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($cargaManipulada)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$casos = [ordered]@{
    'sin token'                    = @{ Cab = $null;                                                    Msg = 'Falta el token' }
    'token manipulado (carga)'     = @{ Cab = "Bearer $($partes[0]).$b64.$($partes[2])";                 Msg = 'El token no es válido' }
    'firma alterada'               = @{ Cab = "Bearer $($partes[0]).$($partes[1]).$($partes[2].Substring(0, $partes[2].Length - 4))AAAA"; Msg = 'El token no es válido' }
    'texto que no es un JWT'       = @{ Cab = 'Bearer esto-no-es-un-jwt';                                Msg = 'El token no es válido' }
    'esquema Basic'                = @{ Cab = 'Basic b3JnYW5pemFkb3JhOng=';                              Msg = 'El token no es válido' }
}
$desde = Get-Date; Start-Sleep -Milliseconds 1200
foreach ($nombre in $casos.Keys) {
    $c = $casos[$nombre]
    $cab = if ($c.Cab) { @{ Authorization = $c.Cab } } else { @{} }
    $r = Api POST $Url.gateway @{ query = $consulta } -SinToken -Cabeceras $cab
    $e = ErrorGraphQL $r
    Check "$nombre -> UNAUTHORIZED '$($c.Msg)…' (status 401, servicio gateway, sin data)" ($r.Status -eq 200 -and $e.extensions.classification -eq 'UNAUTHORIZED' -and $e.message.StartsWith($c.Msg) -and $e.extensions.status -eq 401 -and $e.extensions.servicio -eq 'gateway' -and $null -eq $r.Body.data) "$($r.Texto)"
}
$r = GraphQL 'mutation { login(correo: "a@b.co", password: "x") { token } cancelarEvento(id: "3f6c1b9e-2d4a-4e8f-b7a5-9c0d1e2f3a4b") { estado } }' -SinToken
Check 'login + otra mutation sin token -> UNAUTHORIZED' ((Clasificacion $r) -eq 'UNAUTHORIZED')
Start-Sleep -Seconds 1
$logRechazos = LogsDesde 'gateway' $desde
Check 'Ninguna línea "REST ->" durante los rechazos (no se llamó a ningún servicio)' ((LlamadasRest $logRechazos) -eq 0) "llamadas=$(LlamadasRest $logRechazos)"
Check 'WARN "GraphQL rechazado en el gateway" en el log (6), sin el token' (@([regex]::Matches($logRechazos, 'GraphQL rechazado en el gateway')).Count -eq 6 -and -not $logRechazos.Contains($partes[2]))

Write-Host '== 4. Introspección y GraphiQL (perfil dev)'
$intro = '{ __schema { queryType { name } mutationType { name } } }'
$r = GraphQL $intro -SinToken
Check 'introspección sin token -> UNAUTHORIZED' ((Clasificacion $r) -eq 'UNAUTHORIZED')
$r = GraphQL $intro
Check 'introspección con token (dev) -> schema' ($r.Body.data.__schema.queryType.name -eq 'Query' -and $r.Body.data.__schema.mutationType.name -eq 'Mutation') $r.Texto
$g = Api GET "$Base/graphiql?path=/graphql" -SinToken
Check 'GraphiQL (página estática) responde 200 con el perfil dev' ($g.Status -eq 200) "status=$($g.Status)"

Write-Host '== 5. CORS'
$pre = @{ Origin = $origenPermitido; 'Access-Control-Request-Method' = 'POST'; 'Access-Control-Request-Headers' = 'authorization,content-type' }
$r = Api OPTIONS $Url.gateway -SinToken -Cabeceras $pre
Check "preflight desde $origenPermitido -> 200 con Access-Control-Allow-Origin" ($r.Status -eq 200 -and $r.Headers['Access-Control-Allow-Origin'] -eq $origenPermitido) "status=$($r.Status) cab=$($r.Headers.Keys -join ',')"
Check 'preflight permitido: métodos POST, cabecera authorization, max-age 3600, sin credenciales' ($r.Headers['Access-Control-Allow-Methods'] -match 'POST' -and $r.Headers['Access-Control-Allow-Headers'] -match '(?i)authorization' -and $r.Headers['Access-Control-Max-Age'] -eq '3600' -and -not $r.Headers.ContainsKey('Access-Control-Allow-Credentials'))
$pre.Origin = $origenAjeno
$r = Api OPTIONS $Url.gateway -SinToken -Cabeceras $pre
Check "preflight desde $origenAjeno -> 403 sin Access-Control-Allow-Origin" ($r.Status -eq 403 -and -not $r.Headers.ContainsKey('Access-Control-Allow-Origin')) "status=$($r.Status)"
$r = Api POST $Url.gateway @{ query = $consulta } -Cabeceras @{ Origin = $origenPermitido }
Check 'POST real desde el origen permitido -> datos + Access-Control-Allow-Origin' ($r.Status -eq 200 -and $r.Headers['Access-Control-Allow-Origin'] -eq $origenPermitido -and -not $r.Body.errors)
$r = Api POST $Url.gateway @{ query = $consulta } -Cabeceras @{ Origin = $origenAjeno }
Check 'POST real desde un origen ajeno -> 403' ($r.Status -eq 403) "status=$($r.Status)"

Write-Host '== 6. Actuator del gateway: solo health es público'
CheckStatus 'GET /actuator/health sin token' (Api GET "$Base/actuator/health" -SinToken) 200
foreach ($ep in 'info', 'metrics') {
    $r = Api GET "$Base/actuator/$ep" -SinToken
    Check "GET /actuator/$ep sin token -> 401 con cuerpo JSON (status, message, path)" ($r.Status -eq 401 -and $r.Body.status -eq 401 -and $r.Body.message -like 'Debes iniciar sesión*' -and $r.Body.path -eq "/actuator/$ep") "status=$($r.Status) $($r.Texto)"
}
CheckStatus 'GET /actuator/metrics con token' (Api GET "$Base/actuator/metrics") 200
$r = Api GET "$Base/actuator/info" -SinToken -Cabeceras @{ Authorization = "Bearer $($partes[0]).$b64.$($partes[2])" }
Check 'GET /actuator/info con token manipulado -> 401 "El token no es válido"' ($r.Status -eq 401 -and $r.Body.message -like 'El token no es válido*')
$r = Api GET "$Base/actuator//info" -SinToken
Check 'Ruta con // en el gateway -> 400 con cuerpo JSON' ($r.Status -eq 400 -and $r.Body.status -eq 400) "status=$($r.Status) $($r.Texto)"

Write-Host '== 7. Token vencido (vigencia corta por configuración: AUTH_TOKEN_TTL=PT15S solo para esta prueba)'
$env:AUTH_TOKEN_TTL = 'PT15S'
try {
    Compose 'up -d auth-service' | Out-Null
    Check 'auth-service healthy con AUTH_TOKEN_TTL=PT15S' ((EsperarHealthy 'auth-service') -eq 'healthy')
    $r = GraphQL 'mutation($c: String!, $p: String!) { login(correo: $c, password: $p) { token expiracionEnSegundos } }' @{ c = $script:CorreoOrganizadora; p = $script:PasswordOrganizadora } -SinToken
    Check 'login -> expiracionEnSegundos = 15 (sale de la configuración)' ($r.Body.data.login.expiracionEnSegundos -eq 15) $r.Texto
    $corto = $r.Body.data.login.token
    $r = Api POST $Url.gateway @{ query = $consulta } -SinToken -Cabeceras @{ Authorization = "Bearer $corto" }
    Check 'token de 15 s recién emitido -> datos' (-not $r.Body.errors)
    Start-Sleep -Seconds 20
    $desde = Get-Date; Start-Sleep -Milliseconds 1200
    $r = Api POST $Url.gateway @{ query = $consulta } -SinToken -Cabeceras @{ Authorization = "Bearer $corto" }
    $e = ErrorGraphQL $r
    Check 'a los 20 s -> UNAUTHORIZED "El token venció…"' ($e.extensions.classification -eq 'UNAUTHORIZED' -and $e.message -like 'El token venció*') $r.Texto
    Start-Sleep -Seconds 1
    Check 'token vencido: ninguna llamada REST' ((LlamadasRest (LogsDesde 'gateway' $desde)) -eq 0)
    $r = Api GET "$Base/actuator/info" -SinToken -Cabeceras @{ Authorization = "Bearer $corto" }
    Check 'actuator/info con el token vencido -> 401 "El token venció…"' ($r.Status -eq 401 -and $r.Body.message -like 'El token venció*')
    CheckStatus 'Defensa en profundidad: eventos-service también rechaza el token vencido' (Api GET "$($Url.eventos)/eventos?size=1" -SinToken -Cabeceras @{ Authorization = "Bearer $corto" }) 401
} finally {
    Remove-Item Env:\AUTH_TOKEN_TTL -ErrorAction SilentlyContinue
    Compose 'up -d auth-service' | Out-Null
}
Check 'auth-service de vuelta con la vigencia normal' ((EsperarHealthy 'auth-service') -eq 'healthy')
$r = GraphQL 'mutation($c: String!, $p: String!) { login(correo: $c, password: $p) { expiracionEnSegundos } }' @{ c = $script:CorreoOrganizadora; p = $script:PasswordOrganizadora } -SinToken
Check 'login -> vigencia normal otra vez (> 15 s)' ($r.Body.data.login.expiracionEnSegundos -gt 15) $r.Texto

Resumen 'verificar_7A'
