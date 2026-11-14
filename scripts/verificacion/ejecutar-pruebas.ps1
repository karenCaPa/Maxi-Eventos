# Ejecuta las pruebas automáticas (Fase 8): gradlew.bat clean test --no-daemon en los 10 servicios y el gateway, UNO POR UNO
# (en paralelo la JVM se ha caído en esta máquina), y muestra un resumen por módulo (pruebas, fallos, saltadas) y el total.
# - Las pruebas de integración usan Testcontainers (PostgreSQL y Redis reales): necesitan Docker Desktop en marcha. Sin Docker
#   se SALTAN (no fallan) y el script lo avisa al final.
# - Los logs completos de Gradle quedan en %TEMP%\maxi_pruebas\<módulo>.log.
# - Código de salida: 0 si no hubo fallos; 1 si algún módulo falló o no compiló.
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-pruebas.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-pruebas.ps1 -Modulos eventos,finanzas
# No necesita el .env ni el entorno de Docker Compose. Guardar en UTF-8 con BOM.
param(
    [string[]]$Modulos = @('auth', 'eventos', 'clientes', 'lugares', 'finanzas', 'contratos', 'proveedores', 'logistica',
        'auditoria', 'notificaciones', 'gateway')
)
$ErrorActionPreference = 'Stop'
# Con -File, "-Modulos eventos,finanzas" llega como un solo texto: se separa por comas
$Modulos = @($Modulos | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$carpetaLogs = Join-Path ([IO.Path]::GetTempPath()) 'maxi_pruebas'
New-Item -ItemType Directory -Force $carpetaLogs | Out-Null

function RutaModulo([string]$modulo) {
    if ($modulo -eq 'gateway') { return Join-Path $raiz 'gateway' }
    Join-Path $raiz "services\$modulo-service"
}

# ------------------------------------------------------------------ comprobaciones previas

foreach ($m in $Modulos) {
    if (-not (Test-Path (Join-Path (RutaModulo $m) 'gradlew.bat'))) { throw "Módulo desconocido o sin gradlew.bat: $m" }
}
# Regla del proyecto: al menos 3 GB libres en C: y D: antes de una operación pesada
foreach ($unidad in 'C', 'D') {
    $disco = Get-PSDrive $unidad -ErrorAction SilentlyContinue
    if ($disco -and $disco.Free -lt 3GB) {
        throw ("La unidad {0}: tiene {1:N1} GB libres (mínimo 3 GB). Libera espacio antes de ejecutar las pruebas." -f $unidad, ($disco.Free / 1GB))
    }
}
$dockerActivo = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $versionDocker = cmd /c "docker info --format {{.ServerVersion}} 2>nul"
    $dockerActivo = ($LASTEXITCODE -eq 0) -and -not [string]::IsNullOrWhiteSpace("$versionDocker")
}
Write-Host ''
Write-Host '=== Pruebas automáticas de Maxi Eventos (gradlew.bat clean test --no-daemon) ===' -ForegroundColor Cyan
if ($dockerActivo) {
    Write-Host "Docker: en marcha (Engine $versionDocker). Las pruebas de integración con Testcontainers se ejecutan." -ForegroundColor Green
} else {
    Write-Host 'Docker: NO está en marcha. Las pruebas de integración con Testcontainers se SALTARÁN.' -ForegroundColor Yellow
}
Write-Host ("Módulos: {0}. Logs: {1}" -f ($Modulos -join ', '), $carpetaLogs)
Write-Host ''

# ------------------------------------------------------------------ ejecución, uno por uno

$resultados = @()
$i = 0
foreach ($m in $Modulos) {
    $i++
    $ruta = RutaModulo $m
    $log = Join-Path $carpetaLogs "$m.log"
    Write-Host ("[{0}/{1}] {2} ..." -f $i, $Modulos.Count, $m) -NoNewline
    $inicio = Get-Date
    # Sin resultados de una ejecución anterior: si Gradle falla antes del clean, no se cuentan XML viejos
    Remove-Item (Join-Path $ruta 'build\test-results\test') -Recurse -Force -ErrorAction SilentlyContinue
    # cd /d dentro de cmd (Push-Location no cambia la carpeta del proceso que hereda cmd) y gradlew.bat con su ruta
    # completa (con NoDefaultCurrentDirectoryInExePath, cmd no busca ejecutables en la carpeta actual)
    cmd /c "cd /d `"$ruta`" && `"$ruta\gradlew.bat`" clean test --no-daemon --console=plain > `"$log`" 2>&1"
    $codigo = $LASTEXITCODE
    $segundos = [int]((Get-Date) - $inicio).TotalSeconds

    $pruebas = 0; $fallos = 0; $saltadas = 0
    $fallidas = @(); $clasesSaltadas = @()
    $xmls = @(Get-ChildItem (Join-Path $ruta 'build\test-results\test') -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    foreach ($xml in $xmls) {
        $suite = ([xml][IO.File]::ReadAllText($xml.FullName)).testsuite
        $pruebas += [int]$suite.tests
        $fallos += [int]$suite.failures + [int]$suite.errors
        $saltadas += [int]$suite.skipped
        foreach ($caso in @($suite.testcase)) {
            if ($caso -and ($caso.failure -or $caso.error)) { $fallidas += "$($suite.name) > $($caso.name)" }
        }
        if ([int]$suite.skipped -gt 0) { $clasesSaltadas += $suite.name }
    }
    if ($codigo -ne 0 -and $fallos -eq 0) {
        $estado = 'ERROR (no compiló o Gradle falló; ver el log)'
    } elseif ($fallos -gt 0) {
        $estado = 'FALLÓ'
    } elseif ($saltadas -gt 0) {
        $estado = 'OK con saltadas'
    } else {
        $estado = 'OK'
    }
    $color = 'Green'
    if ($estado -like 'OK con*') { $color = 'Yellow' } elseif ($estado -ne 'OK') { $color = 'Red' }
    Write-Host (" {0} ({1} pruebas, {2} fallos, {3} saltadas, {4} s)" -f $estado, $pruebas, $fallos, $saltadas, $segundos) -ForegroundColor $color
    $resultados += [pscustomobject]@{
        Modulo = $m; Pruebas = $pruebas; Fallos = $fallos; Saltadas = $saltadas; Segundos = $segundos; Estado = $estado
        Fallidas = $fallidas; ClasesSaltadas = $clasesSaltadas; Log = $log
        CodigoGradle = $codigo
    }
}

# ------------------------------------------------------------------ resumen

$total = [pscustomobject]@{
    Pruebas = ($resultados | Measure-Object Pruebas -Sum).Sum
    Fallos = ($resultados | Measure-Object Fallos -Sum).Sum
    Saltadas = ($resultados | Measure-Object Saltadas -Sum).Sum
    Segundos = ($resultados | Measure-Object Segundos -Sum).Sum
}
Write-Host ''
Write-Host '=== Resumen por módulo ===' -ForegroundColor Cyan
$resultados | Format-Table @{ Label = 'Módulo'; Expression = { $_.Modulo } }, Pruebas, Fallos, Saltadas,
    @{ Label = 'Tiempo (s)'; Expression = { $_.Segundos } }, @{ Label = 'Resultado'; Expression = { $_.Estado } } -AutoSize |
    Out-String -Width 200 | Write-Host
Write-Host ("TOTAL: {0} pruebas, {1} fallos, {2} saltadas en {3} módulos ({4} min {5} s)" -f $total.Pruebas, $total.Fallos,
    $total.Saltadas, $resultados.Count, [int][Math]::Floor($total.Segundos / 60), ($total.Segundos % 60)) -ForegroundColor Cyan

$conProblemas = @($resultados | Where-Object { $_.Fallos -gt 0 -or $_.CodigoGradle -ne 0 })
foreach ($r in $conProblemas) {
    Write-Host ''
    Write-Host ("Módulo {0}: {1}. Log: {2}" -f $r.Modulo, $r.Estado, $r.Log) -ForegroundColor Red
    $r.Fallidas | ForEach-Object { Write-Host "  X $_" -ForegroundColor Red }
}

if ($total.Saltadas -gt 0) {
    $detalle = $resultados | Where-Object Saltadas -gt 0 | ForEach-Object { "{0}: {1} saltadas" -f $_.Modulo, $_.Saltadas }
    Write-Host ''
    Write-Host '#####################################################################################' -ForegroundColor Yellow
    if (-not $dockerActivo) {
        Write-Host ("  AVISO: {0} PRUEBAS SALTADAS PORQUE DOCKER DESKTOP NO ESTÁ EN MARCHA." -f $total.Saltadas) -ForegroundColor Yellow
        Write-Host '  Son las pruebas de integración con Testcontainers (PostgreSQL y Redis reales).' -ForegroundColor Yellow
        Write-Host '  NO cuentan como aprobadas: arranca Docker Desktop y vuelve a ejecutar el script.' -ForegroundColor Yellow
    } else {
        Write-Host ("  AVISO: {0} pruebas SALTADAS aunque Docker está en marcha. Revisa los logs." -f $total.Saltadas) -ForegroundColor Yellow
    }
    $detalle | ForEach-Object { Write-Host "    - $_" -ForegroundColor Yellow }
    Write-Host '#####################################################################################' -ForegroundColor Yellow
}

Write-Host ''
if ($conProblemas.Count -gt 0) {
    Write-Host 'RESULTADO: HAY FALLOS.' -ForegroundColor Red
    exit 1
}
if ($total.Saltadas -gt 0) {
    Write-Host 'RESULTADO: sin fallos, pero con pruebas SALTADAS (ver el aviso).' -ForegroundColor Yellow
} else {
    Write-Host 'RESULTADO: todas las pruebas pasaron (0 fallos, 0 saltadas).' -ForegroundColor Green
}
exit 0
