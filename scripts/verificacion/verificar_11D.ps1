# Verificación en Docker de la Fase 11D: roles ADMINISTRADOR y ASISTENTE, autorización en DOS niveles por separado.
#  1. Login de los dos usuarios (claim rol, nombre y rol en la respuesta) y un token SIN rol firmado aquí (como los anteriores
#     a la 11D) con AUTH_JWT_SECRET del .env (no se muestra).
#  2. Nivel 1, DIRECTO contra cada servicio (sin pasar por el gateway): con el asistente y con el token sin rol, cada acción de
#     administración responde 403 con ErrorResponse y la entidad no cambia; con el ADMINISTRADOR la MISMA acción sobre la
#     MISMA entidad funciona (la regla es por rol, no por la petición).
#  3. Nivel 2, por GraphQL: las 6 mutations de administración y Evento.auditoria → FORBIDDEN del gateway sin llamar al
#     servicio (se comprueba en el log con el X-Request-Id de cada petición); con el administrador, funcionan.
#  4. Lo que el asistente SÍ puede: consultar, crear clientes, la ficha sin auditoría.
# Crea solo entidades "Prueba 11D …" (lugar, proveedor, clientes) y las borra al final; bloquea y libera una fecha libre.
# Requiere el entorno levantado y VERIFICACION_ASISTENTE_PASSWORD en el .env (rotar-password.ps1 -Usuario asistente).
# Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
$U = $script:Url
$SIN_PERMISO = 'No tienes permiso para realizar esta operación.'
$inicio = Get-Date

function B64Url([byte[]]$Bytes) { [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
function FirmarJwt([hashtable]$Claims) {
    $cab = B64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $carga = B64Url ([Text.Encoding]::UTF8.GetBytes((ConvertTo-Json $Claims -Compress)))
    $hmac = New-Object Security.Cryptography.HMACSHA256 (, [Text.Encoding]::UTF8.GetBytes((LeerVariableEnv 'AUTH_JWT_SECRET')))
    try { "$cab.$carga." + (B64Url $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$cab.$carga"))) } finally { $hmac.Dispose() }
}
function Carga([string]$Token) {
    $p = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/'); while ($p.Length % 4) { $p += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
}
# Petición REST con un token concreto (y un X-Request-Id propio)
function Como([string]$Token, [string]$Metodo, [string]$Ruta, $Cuerpo = $null) {
    Api $Metodo $Ruta $Cuerpo -SinToken -Cabeceras @{ Authorization = "Bearer $Token"; 'X-Request-Id' = [guid]::NewGuid().ToString() }
}
# GraphQL con un token concreto; devuelve también la referencia enviada
function GqlComo([string]$Token, [string]$Documento, [hashtable]$Variables = @{}) {
    $ref = [guid]::NewGuid().ToString()
    $r = Api POST $U.gateway @{ query = $Documento; variables = $Variables } -SinToken -Cabeceras @{ Authorization = "Bearer $Token"; 'X-Request-Id' = $ref }
    $r | Add-Member -NotePropertyName Ref -NotePropertyValue $ref -PassThru
}
# ¿La fecha está libre? (en PowerShell 5.1, ConvertFrom-Json '[]' da $null: se compara por la fecha, no por el conteo)
function Libre([string]$Fecha) {
    [bool](@((Como $admin GET "$($U.eventos)/fechas/disponibles?desde=$Fecha&hasta=$Fecha").Body) | Where-Object { $_ -and $_.fecha -eq $Fecha })
}
function Es403($R, [string]$Ruta) {
    $R.Status -eq 403 -and $R.Body.status -eq 403 -and $R.Body.error -eq 'Forbidden' -and $R.Body.message -eq $SIN_PERMISO -and $R.Body.path -eq $Ruta
}
function EsForbidden($R, [string]$Campo) {
    $e = ErrorGraphQL $R
    $e -and $e.extensions.classification -eq 'FORBIDDEN' -and $e.extensions.status -eq 403 -and $e.extensions.servicio -eq 'gateway' -and
        $e.message -eq $SIN_PERMISO -and @($e.path)[-1] -eq $Campo
}

Write-Host '== 1. Tokens'
$rAdmin = Login
$admin = $script:Token
$cAdmin = Carga $admin
Check 'Login del administrador: rol ADMINISTRADOR en la respuesta y en el claim' ($rAdmin.Status -eq 200 -and $rAdmin.Body.rol -eq 'ADMINISTRADOR' -and $cAdmin.rol -eq 'ADMINISTRADOR') "status=$($rAdmin.Status) rol=$($rAdmin.Body.rol)"
Check 'Nombre del administrador = AUTH_ORGANIZER_NOMBRE' ($rAdmin.Body.nombre -eq (LeerVariableEnv 'AUTH_ORGANIZER_NOMBRE')) "nombre=$($rAdmin.Body.nombre)"
if (-not $script:PasswordAsistente) { throw 'Falta VERIFICACION_ASISTENTE_PASSWORD en el .env (rotar-password.ps1 -Usuario asistente).' }
$rAsis = Api POST "$($U.auth)/auth/login" (ConvertTo-Json @{ correo = $script:CorreoAsistente; password = $script:PasswordAsistente } -Compress) -SinToken
$asis = $rAsis.Body.token
$cAsis = Carga $asis
Check 'Login del asistente: rol ASISTENTE, su propio sub, sin correo en el token' ($rAsis.Status -eq 200 -and $rAsis.Body.rol -eq 'ASISTENTE' -and $cAsis.rol -eq 'ASISTENTE' -and $cAsis.sub -eq (LeerVariableEnv 'AUTH_ASISTENTE_ID') -and $cAsis.sub -ne $cAdmin.sub -and -not ((ConvertTo-Json $cAsis) -match '@')) "rol=$($rAsis.Body.rol)"
$rMala = Api POST "$($U.auth)/auth/login" (ConvertTo-Json @{ correo = $script:CorreoAsistente; password = $script:PasswordOrganizadora } -Compress) -SinToken
Check 'La contraseña del administrador NO sirve para el asistente (401)' ($rMala.Status -eq 401)
$ahora = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$sinRol = FirmarJwt ([ordered]@{ sub = $cAdmin.sub; iss = 'maxi-eventos-auth'; iat = $ahora; exp = $ahora + 600 })
Check 'Token sin rol: válido para consultar (200)' ((Como $sinRol GET "$($U.lugares)/lugares?size=1").Status -eq 200)
$noPermitidos = [ordered]@{ asistente = $asis; 'sin rol' = $sinRol }

Write-Host '== 2. Entidades de prueba (las crea el administrador)'
$s = Sufijo
$lugar = (Como $admin POST "$($U.lugares)/lugares" @{ nombre = "Prueba 11D Lugar $s"; direccion = 'Calle 1'; tipo = 'SALON'; capacidad = 50 }).Body
$prov = (Como $admin POST "$($U.proveedores)/proveedores" @{ nombre = "Prueba 11D Proveedor $s"; categoria = 'SONIDO'; contacto = '3100000000' }).Body
$cli = (Como $admin POST "$($U.clientes)/clientes" @{ nombreCompleto = "Prueba 11D Cliente $s"; telefono = "301$((Get-Random -Minimum 1000000 -Maximum 9999999))"; correo = "p11d.$s@ejemplo.com" }).Body
$libres = FechasLibres   # ya devuelve un arreglo (no se vuelve a envolver con @())
$fecha = [string]$libres[$libres.Count - 1]
$evento = @((Como $admin GET "$($U.eventos)/eventos?estado=RESERVADO&size=100&sort=fechaCreacion,asc").Body.contenido | Where-Object { $_.nombreEvento -eq 'Quince años de Lucía' })[0]
Check 'Lugar, proveedor y cliente de prueba creados; hay una fecha libre y el evento de demostración' ($lugar.id -and $prov.id -and $cli.id -and $fecha -and $evento.id)

Write-Host '== 3. Nivel 1: directo contra cada servicio (asistente y sin rol → 403; administrador → sí)'
$acciones = @(
    @{ n = 'Lugares: crear';                 m = 'POST';   u = "$($U.lugares)/lugares";                       p = '/api/v1/lugares'; c = @{ nombre = "Prueba 11D Otro $s"; direccion = 'Calle 2'; tipo = 'SALON'; capacidad = 40 }; ok = 201 }
    @{ n = 'Lugares: editar';                m = 'PUT';    u = "$($U.lugares)/lugares/$($lugar.id)";          p = "/api/v1/lugares/$($lugar.id)"; c = @{ nombre = "Prueba 11D Lugar $s"; direccion = 'Calle 1'; tipo = 'SALON'; capacidad = 60 }; ok = 200 }
    @{ n = 'Lugares: desactivar';            m = 'PATCH';  u = "$($U.lugares)/lugares/$($lugar.id)/estado";   p = "/api/v1/lugares/$($lugar.id)/estado"; c = @{ estado = 'INACTIVO' }; ok = 200 }
    @{ n = 'Proveedores: crear';             m = 'POST';   u = "$($U.proveedores)/proveedores";               p = '/api/v1/proveedores'; c = @{ nombre = "Prueba 11D Otro $s"; categoria = 'SONIDO'; contacto = '3100000001' }; ok = 201 }
    @{ n = 'Proveedores: editar';            m = 'PUT';    u = "$($U.proveedores)/proveedores/$($prov.id)";   p = "/api/v1/proveedores/$($prov.id)"; c = @{ nombre = "Prueba 11D Proveedor $s"; categoria = 'SONIDO'; contacto = '3109999999' }; ok = 200 }
    @{ n = 'Proveedores: desactivar';        m = 'PATCH';  u = "$($U.proveedores)/proveedores/$($prov.id)/estado"; p = "/api/v1/proveedores/$($prov.id)/estado"; c = @{ activo = $false }; ok = 200 }
    @{ n = 'Eventos: bloquear una fecha';    m = 'PUT';    u = "$($U.eventos)/fechas/$fecha/estado";          p = "/api/v1/fechas/$fecha/estado"; c = @{ estado = 'OCUPADA' }; ok = 200 }
    @{ n = 'Auditoría: consultar';           m = 'GET';    u = "$($U.auditoria)/auditoria?eventoId=$($evento.id)"; p = '/api/v1/auditoria'; c = $null; ok = 200 }
)
foreach ($a in $acciones) {
    foreach ($quien in $noPermitidos.Keys) {
        $r = Como $noPermitidos[$quien] $a.m $a.u $a.c
        Check "$($a.n) con $quien → 403 ErrorResponse" (Es403 $r $a.p) "status=$($r.Status) $($r.Texto)"
    }
}
# Nada cambió por esos intentos
$l0 = (Como $admin GET "$($U.lugares)/lugares/$($lugar.id)").Body
$p0 = (Como $admin GET "$($U.proveedores)/proveedores/$($prov.id)").Body
$otros = @(@((Como $admin GET "$($U.lugares)/lugares?size=100").Body.contenido) + @((Como $admin GET "$($U.proveedores)/proveedores?size=100").Body.contenido) | Where-Object { $_ -and $_.nombre -eq "Prueba 11D Otro $s" })
Check 'Tras los 403 nada cambió: lugar 50 y ACTIVO, proveedor activo, la fecha sigue libre, no se creó nada' ($l0.capacidad -eq 50 -and $l0.estado -eq 'ACTIVO' -and $p0.activo -eq $true -and (Libre $fecha) -and $otros.Count -eq 0)
# El ADMINISTRADOR, la MISMA acción sobre la MISMA entidad
$creados = @{}
foreach ($a in $acciones) {
    $r = Como $admin $a.m $a.u $a.c
    Check "$($a.n) con el ADMINISTRADOR → $($a.ok)" ($r.Status -eq $a.ok) "status=$($r.Status) $($r.Texto)"
    if ($a.m -eq 'POST' -and $r.Body.id) { $creados[$a.n] = $r.Body.id }
}
$l1 = (Como $admin GET "$($U.lugares)/lugares/$($lugar.id)").Body
Check 'Con el administrador sí cambió: lugar 60 e INACTIVO; la fecha quedó ocupada' ($l1.capacidad -eq 60 -and $l1.estado -eq 'INACTIVO' -and -not (Libre $fecha))
CheckStatus 'El administrador libera la fecha otra vez' (Como $admin PUT "$($U.eventos)/fechas/$fecha/estado" @{ estado = 'DISPONIBLE' }) 200
# Borrados (lo creado por el administrador): primero intentos no permitidos, luego el administrador
$borrados = @(
    @{ n = 'Clientes: borrar';    u = "$($U.clientes)/clientes/$($cli.id)";       p = "/api/v1/clientes/$($cli.id)" }
    @{ n = 'Lugares: borrar';     u = "$($U.lugares)/lugares/$($lugar.id)";       p = "/api/v1/lugares/$($lugar.id)" }
    @{ n = 'Proveedores: borrar'; u = "$($U.proveedores)/proveedores/$($prov.id)"; p = "/api/v1/proveedores/$($prov.id)" }
)
foreach ($b in $borrados) {
    foreach ($quien in $noPermitidos.Keys) {
        $r = Como $noPermitidos[$quien] DELETE $b.u
        Check "$($b.n) con $quien → 403 ErrorResponse" (Es403 $r $b.p) "status=$($r.Status) $($r.Texto)"
    }
    CheckStatus "$($b.n) con el ADMINISTRADOR" (Como $admin DELETE $b.u) 204
}

Write-Host '== 4. Nivel 2: GraphQL (FORBIDDEN en el gateway, sin llamar al servicio; el administrador sí)'
$lugar2 = (Como $admin POST "$($U.lugares)/lugares" @{ nombre = "Prueba 11D GQL Lugar $s"; direccion = 'Calle 3'; tipo = 'SALON'; capacidad = 70 }).Body
$prov2 = (Como $admin POST "$($U.proveedores)/proveedores" @{ nombre = "Prueba 11D GQL Proveedor $s"; categoria = 'SONIDO'; contacto = '3100000002' }).Body
$mut = @(
    @{ n = 'crearLugar'; d = 'mutation($i: LugarInput!) { crearLugar(input: $i) { id } }'; v = @{ i = @{ nombre = "Prueba 11D GQL Nuevo $s"; direccion = 'Calle 4'; tipo = 'SALON'; capacidad = 30 } } }
    @{ n = 'actualizarLugar'; d = 'mutation($id: ID!, $i: LugarInput!) { actualizarLugar(id: $id, input: $i) { capacidad } }'; v = @{ id = $lugar2.id; i = @{ nombre = "Prueba 11D GQL Lugar $s"; direccion = 'Calle 3'; tipo = 'SALON'; capacidad = 75 } } }
    @{ n = 'cambiarEstadoLugar'; d = 'mutation($id: ID!) { cambiarEstadoLugar(id: $id, estado: INACTIVO) { estado } }'; v = @{ id = $lugar2.id } }
    @{ n = 'crearProveedor'; d = 'mutation($i: ProveedorInput!) { crearProveedor(input: $i) { id } }'; v = @{ i = @{ nombre = "Prueba 11D GQL Nuevo $s"; categoria = 'SONIDO'; contacto = '3100000003' } } }
    @{ n = 'actualizarProveedor'; d = 'mutation($id: ID!, $i: ProveedorInput!) { actualizarProveedor(id: $id, input: $i) { contacto } }'; v = @{ id = $prov2.id; i = @{ nombre = "Prueba 11D GQL Proveedor $s"; categoria = 'SONIDO'; contacto = '3108888888' } } }
    @{ n = 'cambiarEstadoProveedor'; d = 'mutation($id: ID!) { cambiarEstadoProveedor(id: $id, activo: false) { activo } }'; v = @{ id = $prov2.id } }
)
$refsDenegadas = @()
foreach ($m in $mut) {
    foreach ($quien in $noPermitidos.Keys) {
        $r = GqlComo $noPermitidos[$quien] $m.d $m.v
        $refsDenegadas += $r.Ref
        Check "$($m.n) con $quien → FORBIDDEN del gateway" (EsForbidden $r $m.n) "$($r.Texto)"
    }
}
Start-Sleep -Seconds 2
$logGw = LogsDesde 'gateway' $inicio
$sinLlamadas = $true
foreach ($ref in $refsDenegadas) {
    $lineas = @($logGw -split "`n" | Where-Object { $_ -match [regex]::Escape("[ref=$ref]") })
    if (-not ($lineas -match 'Acceso denegado en el gateway') -or ($lineas -match 'REST -> (lugares|proveedores)')) { $sinLlamadas = $false }
}
Check "Log del gateway: las $($refsDenegadas.Count) peticiones denegadas dicen 'Acceso denegado' y NO llamaron a Lugares ni a Proveedores" $sinLlamadas
$l2 = (Como $admin GET "$($U.lugares)/lugares/$($lugar2.id)").Body
$p2 = (Como $admin GET "$($U.proveedores)/proveedores/$($prov2.id)").Body
Check 'Tras los FORBIDDEN nada cambió (lugar 70 ACTIVO, proveedor activo)' ($l2.capacidad -eq 70 -and $l2.estado -eq 'ACTIVO' -and $p2.activo -eq $true)
foreach ($m in $mut) {
    $r = GqlComo $admin $m.d $m.v
    Check "$($m.n) con el ADMINISTRADOR → sin errores" ($r.Status -eq 200 -and -not $r.Body.errors) "$($r.Texto)"
    if ($m.n -like 'crear*' -and $r.Body.data.($m.n).id) { $creados[$m.n] = $r.Body.data.($m.n).id }
}
# La auditoría en la ficha
$fichaAsis = GqlComo $asis 'query($id: ID!) { evento(id: $id) { nombreEvento notificaciones { totalElementos } auditoria { totalElementos } } }' @{ id = $evento.id }
Check 'Ficha con auditoría como asistente: el resto llega y SOLO evento.auditoria es FORBIDDEN' ((EsForbidden $fichaAsis 'auditoria') -and @($fichaAsis.Body.errors).Count -eq 1 -and $fichaAsis.Body.data.evento.nombreEvento -eq 'Quince años de Lucía' -and $fichaAsis.Body.data.evento.notificaciones.totalElementos -gt 0)
$fichaAdmin = GqlComo $admin 'query($id: ID!) { evento(id: $id) { auditoria { totalElementos } } }' @{ id = $evento.id }
Check 'Ficha con auditoría como ADMINISTRADOR: sin errores y con registros' (-not $fichaAdmin.Body.errors -and $fichaAdmin.Body.data.evento.auditoria.totalElementos -gt 0)

Write-Host '== 5. Lo que el asistente SÍ puede'
$fichaUi = GqlComo $asis 'query($id: ID!, $v: Boolean!) { evento(id: $id) { nombreEvento auditoria @include(if: $v) { totalElementos } } }' @{ id = $evento.id; v = $false }
Check 'La ficha como la pide la interfaz (auditoria @include false): sin errores' (-not $fichaUi.Body.errors -and $fichaUi.Body.data.evento.nombreEvento -eq 'Quince años de Lucía')
foreach ($c in @(@{ n = 'lugares'; u = "$($U.lugares)/lugares?size=5" }, @{ n = 'proveedores'; u = "$($U.proveedores)/proveedores?size=5" },
                 @{ n = 'fechas disponibles'; u = "$($U.eventos)/fechas/disponibles" }, @{ n = 'eventos'; u = "$($U.eventos)/eventos?size=5" },
                 @{ n = 'notificaciones del evento'; u = "$($U.notificaciones)/notificaciones?eventoId=$($evento.id)" },
                 @{ n = 'asignaciones de proveedores del evento'; u = "$($U.proveedores)/proveedores/eventos/$($evento.id)" })) {
    CheckStatus "El asistente consulta $($c.n)" (Como $asis GET $c.u) 200
}
$cliAsis = Como $asis POST "$($U.clientes)/clientes" @{ nombreCompleto = "Prueba 11D Cliente del asistente $s"; telefono = "302$((Get-Random -Minimum 1000000 -Maximum 9999999))"; correo = "p11d.asis.$s@ejemplo.com" }
CheckStatus 'El asistente crea un cliente' $cliAsis 201
CheckStatus 'El asistente edita ese cliente' (Como $asis PUT "$($U.clientes)/clientes/$($cliAsis.Body.id)" @{ nombreCompleto = "Prueba 11D Cliente del asistente $s"; telefono = $cliAsis.Body.telefono; correo = $cliAsis.Body.correo; direccion = 'Calle 9' }) 200

Write-Host '== 6. Limpieza (lo creado por esta verificación)'
$limpio = $true
foreach ($id in @($lugar2.id, $creados['Lugares: crear'], $creados['crearLugar'])) { if ($id -and (Como $admin DELETE "$($U.lugares)/lugares/$id").Status -ne 204) { $limpio = $false } }
foreach ($id in @($prov2.id, $creados['Proveedores: crear'], $creados['crearProveedor'])) { if ($id -and (Como $admin DELETE "$($U.proveedores)/proveedores/$id").Status -ne 204) { $limpio = $false } }
if ((Como $admin DELETE "$($U.clientes)/clientes/$($cliAsis.Body.id)").Status -ne 204) { $limpio = $false }
Check 'Lugares, proveedores y clientes "Prueba 11D" borrados; la fecha quedó libre' ($limpio -and (Libre $fecha))

Resumen 'Verificación 11D'
