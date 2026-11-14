# Verificación en Docker de la Fase 11H: la campanita de notificaciones (lecturas por usuario y polling).
#  1. Tokens de la administradora y de la asistente; las dos dejan la campanita en 0 (marcar todas).
#  2. Un evento "Prueba …" RESERVADO en un fin de semana libre de los próximos 7 días (para que reciba un recordatorio); sus
#     notificaciones del flujo (abonos y confirmación) se marcan como leídas para las dos.
#  3. Recorrido real en Edge sin ventana (navegador_11H.mjs) como ADMINISTRADORA: campanita en 0 → "Ejecutar recordatorios" →
#     sube SIN recargar, panel con el mensaje, el evento y la hora de hoy en la zona de /config.js, dentro de la pantalla a 1280
#     y 360 px, Escape, polling (un abono por REST la hace subir sola en ≤ 40 s) y clic → leída y ficha del evento.
#  4. Por REST y GraphQL: el recordatorio quedó LEÍDO para la administradora y SIGUE SIN LEER para la asistente; los conteos de
#     las dos lo reflejan; marcar una inexistente → 404.
# Ejecutar los recordatorios también los envía a los eventos de demostración de los próximos 7 días: ejecutar ANTES de
# cargar-demo.ps1 (o repetir la carga después). Al final cancela el evento de prueba. Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
$U = $script:Url
$inicio = Get-Date

function Como([string]$Token, [string]$Metodo, [string]$Ruta, $Cuerpo = $null) {
    Api $Metodo $Ruta $Cuerpo -SinToken -Cabeceras @{ Authorization = "Bearer $Token" }
}
function SinLeer([string]$Token) { [int](Como $Token GET "$($U.notificaciones)/notificaciones/sin-leer").Body.sinLeer }
function Recientes([string]$Token) { , @((Como $Token GET "$($U.notificaciones)/notificaciones/recientes?size=50").Body.contenido | Where-Object { $_ }) }

Write-Host '== 1. Tokens y campanita en 0 para las dos'
$null = Login
$admin = $script:Token
if (-not $script:PasswordAsistente) { throw 'Falta VERIFICACION_ASISTENTE_PASSWORD en el .env (rotar-password.ps1 -Usuario asistente).' }
$asis = (Api POST "$($U.auth)/auth/login" (ConvertTo-Json @{ correo = $script:CorreoAsistente; password = $script:PasswordAsistente } -Compress) -SinToken).Body.token
Check 'login de la administradora y de la asistente' ($admin -and $asis)
foreach ($t in @($admin, $asis)) { $null = Como $t PATCH "$($U.notificaciones)/notificaciones/leidas" }
Check 'las dos con 0 sin leer' ((SinLeer $admin) -eq 0 -and (SinLeer $asis) -eq 0)

Write-Host '== 2. Evento de prueba RESERVADO en los próximos 7 días'
$desde = (Get-Date).AddDays(1).ToString('yyyy-MM-dd'); $hasta = (Get-Date).AddDays(7).ToString('yyyy-MM-dd')
$fecha = @((Api GET "$($U.eventos)/fechas/disponibles?desde=$desde&hasta=$hasta").Body | Where-Object { $_ } | ForEach-Object { [string]$_.fecha } | Sort-Object) | Select-Object -First 1
if (-not $fecha) { throw "No hay ningún viernes, sábado ni domingo libre entre $desde y ${hasta}: no se puede probar el recordatorio." }
$flujo = FlujoFase3 $fecha
$id = $flujo.EventoId
Check "evento de prueba RESERVADO el $fecha" ([bool]$id)
# sus notificaciones del flujo (2 abonos + confirmación) llegan por Kafka; se esperan y se marcan leídas para las dos
$fin = (Get-Date).AddSeconds(60)
do { $n = (Api GET "$($U.notificaciones)/notificaciones?eventoId=$id").Body.totalElementos; if ($n -ge 3) { break }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $fin)
foreach ($t in @($admin, $asis)) { $null = Como $t PATCH "$($U.notificaciones)/notificaciones/leidas" }
Check "las notificaciones del flujo ($n) leídas: las dos otra vez en 0" ($n -ge 3 -and (SinLeer $admin) -eq 0 -and (SinLeer $asis) -eq 0)

Write-Host '== 3. Recorrido real en el navegador (administradora)'
$navegador = @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $navegador -or -not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Hace falta Edge o Chrome y Node para el recorrido en el navegador.' }
$env:MAXI_CORREO = $script:CorreoOrganizadora; $env:MAXI_PASSWORD = $script:PasswordOrganizadora
try {
    $salida = cmd /c "node `"$PSScriptRoot\navegador_11H.mjs`" `"$navegador`" http://localhost:5173 $($U.finanzas) $id $($flujo.ClienteId) `"$($script:NombreEventoFlujo)`" 2>&1"
} finally { Remove-Item Env:MAXI_PASSWORD, Env:MAXI_CORREO -ErrorAction SilentlyContinue }
foreach ($linea in $salida) {
    if ($linea -match '^(OK|FALLO)\s+(.*)$') { Check "navegador: $($Matches[2])" ($Matches[1] -eq 'OK') }
    elseif ($linea -match '^DATO (.*)$') { Write-Host "        $($Matches[1])" }
    elseif ($linea) { Write-Host "        $linea" }
}

Write-Host '== 4. Lecturas por usuario (REST y GraphQL)'
$recordatorio = @((Recientes $admin) | Where-Object { $_.eventoId -eq $id -and $_.tipo -eq 'EVENTO_PROXIMO' })[0]
Check 'el recordatorio del evento de prueba existe' ([bool]$recordatorio) "eventoId=$id"
$deAsistente = @((Recientes $asis) | Where-Object { $_.id -eq $recordatorio.id })[0]
Check 'administradora: el recordatorio quedó LEÍDO (lo abrió en el navegador)' ($recordatorio.leida -eq $true -and $recordatorio.leidaEn)
Check 'asistente: el MISMO recordatorio sigue SIN LEER' ($deAsistente -and $deAsistente.leida -eq $false -and -not $deAsistente.leidaEn)
$nuevas = @((Recientes $asis) | Where-Object { -not $_.leida })
Check "asistente: $($nuevas.Count) sin leer (los recordatorios enviados y el abono), su conteo coincide" ((SinLeer $asis) -eq $nuevas.Count -and $nuevas.Count -ge 2)
$gql = Api POST $U.gateway @{ query = '{ notificacionesSinLeer misNotificaciones(size: 5) { contenido { id leida evento { nombreEvento } } } }'; variables = @{} } -SinToken -Cabeceras @{ Authorization = "Bearer $asis" }
$mia = @($gql.Body.data.misNotificaciones.contenido | Where-Object { $_.id -eq $recordatorio.id })[0]
Check 'GraphQL como asistente: mismo conteo y el recordatorio sin leer, con su evento' (-not $gql.Body.errors -and $gql.Body.data.notificacionesSinLeer -eq $nuevas.Count -and $mia.leida -eq $false -and $mia.evento.nombreEvento -eq $script:NombreEventoFlujo)
$r404 = Como $admin PATCH "$($U.notificaciones)/notificaciones/11111111-1111-1111-1111-111111111111/leida"
Check '404 con ErrorResponse al marcar una inexistente' ($r404.Status -eq 404 -and $r404.Body.message -like 'No existe una notificación con id*')
$filas = "$(Psql "SELECT count(*) FROM notificaciones_lecturas WHERE notificacion_id = '$($recordatorio.id)';")".Trim()
Check 'en la base, una sola lectura de ese recordatorio (la de la administradora)' ($filas -eq '1') "filas=$filas"

Resumen 'Verificación 11H'
if ($script:Fallos -gt 0) { exit 1 }
