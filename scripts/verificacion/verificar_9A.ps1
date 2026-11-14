# Verificación en Docker de la subfase 9A:
#  1. Redis es opcional para la disponibilidad. Con Redis detenido:
#     - auth, eventos, finanzas y contratos (ya sin Redis) y clientes, lugares, proveedores y logística (Redis como caché,
#       sin indicador de Redis en su health) responden /actuator/health 200 UP;
#     - una consulta real por id en los 4 servicios con caché responde 200 desde la base de datos y su log muestra el WARN
#       "Caché no disponible ... -> se consulta la base de datos".
#     Redis se vuelve a arrancar siempre (también si el script falla).
#  2. Con Redis en marcha, la segunda consulta por id da "Caché HIT" en el log de clientes, lugares, proveedores y logística.
#  3. Asignar a un evento un proveedor ya asignado para el MISMO servicio -> 409 con el mensaje
#     "El proveedor «X» ya está asignado a este evento para el servicio «Y»" (para otro servicio es válido: lo cubre la
#     prueba unitaria, aquí no se crea nada).
# Usa datos existentes (no crea nada). Requiere el entorno levantado y los datos de cargar-demo.ps1.
# Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado
CheckStatus 'login' (Login) 200

function PrimerId([string]$Ruta) { $r = Api GET $Ruta; if ($r.Status -eq 200) { @($r.Body.contenido)[0].id } }
$eventoConLogistica = ("$(Psql 'SELECT evento_id FROM logistica_eventos LIMIT 1;')").Trim()
$casos = @(
    @{ Servicio = 'clientes-service'; Cache = 'clientes'; Id = (PrimerId "$($Url.clientes)/clientes?size=1"); Ruta = "$($Url.clientes)/clientes/{id}" }
    @{ Servicio = 'lugares-service'; Cache = 'lugares'; Id = (PrimerId "$($Url.lugares)/lugares?size=1"); Ruta = "$($Url.lugares)/lugares/{id}" }
    @{ Servicio = 'proveedores-service'; Cache = 'proveedores'; Id = (PrimerId "$($Url.proveedores)/proveedores?size=1"); Ruta = "$($Url.proveedores)/proveedores/{id}" }
    @{ Servicio = 'logistica-service'; Cache = 'logistica-requerimientos'; Id = $eventoConLogistica; Ruta = "$($Url.logistica)/eventos/{id}/logistica" }
)
foreach ($c in $casos) { Check "$($c.Cache): hay un dato para consultar" ([bool]$c.Id) '(no hay datos; ejecuta cargar-demo.ps1)' }

Write-Host '== 1. Redis detenido: health UP y consultas desde la base de datos'
$puertos = [ordered]@{ auth = 8080; eventos = 8081; finanzas = 8084; contratos = 8085; clientes = 8082; lugares = 8083; proveedores = 8086; logistica = 8088 }
Compose 'stop redis' | Out-Null
try {
    Start-Sleep -Seconds 5
    foreach ($s in $puertos.Keys) {
        $h = Api GET "http://localhost:$($puertos[$s])/actuator/health" -SinToken
        Check "$s con Redis detenido -> health 200 UP" ($h.Status -eq 200 -and $h.Body.status -eq 'UP') "$($h.Status) $($h.Texto)"
    }
    foreach ($c in $casos | Where-Object { $_.Id }) {
        $desde = (Get-Date).AddSeconds(-2)
        $r = Api GET ($c.Ruta -replace '\{id\}', $c.Id)
        Start-Sleep -Seconds 1
        $log = LogsDesde $c.Servicio $desde
        Check "$($c.Cache): GET 200 desde la base de datos con el WARN de caché en el log" ($r.Status -eq 200 -and $log -match "WARN.*Caché no disponible al leer cache=$([regex]::Escape($c.Cache)) clave=$($c.Id)") "$($r.Status)"
    }
} finally {
    Compose 'start redis' | Out-Null
}
Check 'Redis arrancado de nuevo -> healthy' ((EsperarHealthy 'redis' 60) -eq 'healthy') ''

Write-Host '== 2. Caché HIT en los 4 servicios con Redis'
# Tras arrancar Redis, el cliente Lettuce de cada servicio se reconecta con espera creciente (hasta ~30 s); mientras tanto
# las consultas siguen saliendo de la base de datos con el WARN. Por eso se reintenta hasta 60 s.
foreach ($c in $casos | Where-Object { $_.Id }) {
    $ruta = $c.Ruta -replace '\{id\}', $c.Id
    $fin = (Get-Date).AddSeconds(60)
    do {
        $desde = (Get-Date).AddSeconds(-2)
        $r1 = Api GET $ruta; $r2 = Api GET $ruta
        Start-Sleep -Seconds 1
        $log = LogsDesde $c.Servicio $desde
        $hit = $log -match "Caché HIT\s+cache=$([regex]::Escape($c.Cache)) clave=$($c.Id)"
        if (-not $hit) { Start-Sleep -Seconds 4 }
    } while (-not $hit -and (Get-Date) -lt $fin)
    Check "$($c.Cache): dos GET 200 y la segunda da 'Caché HIT' en el log" ($r1.Status -eq 200 -and $r2.Status -eq 200 -and $log -match "Caché HIT\s+cache=$([regex]::Escape($c.Cache)) clave=$($c.Id)") "$($r1.Status)/$($r2.Status)"
}

Write-Host '== 3. Proveedor ya asignado al evento para el mismo servicio -> 409 con mensaje claro'
$fila = ("$(Psql 'SELECT ep.evento_id, ep.proveedor_id, p.nombre, ep.servicio_asignado FROM evento_proveedores ep JOIN proveedores p ON p.id = ep.proveedor_id JOIN eventos e ON e.id = ep.evento_id WHERE p.activo LIMIT 1;')").Trim()
if (-not $fila) {
    Check 'hay una asignación de un proveedor activo' $false '(no hay datos; ejecuta cargar-demo.ps1)'
} else {
    $eventoId, $proveedorId, $nombre, $servicio = $fila -split '\|', 4
    $r = Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eventoId; proveedorId = $proveedorId; servicioAsignado = $servicio; costoAcordado = 100000 }
    CheckStatus "asignar de nuevo «$nombre» para «$servicio»" $r 409
    Check "mensaje: '$($r.Body.message)'" ($r.Body.message -ceq "El proveedor «$nombre» ya está asignado a este evento para el servicio «$servicio»") $r.Texto
}

Resumen 'Verificación 9A'
if ($script:Fallos -gt 0) { exit 1 }
