# Compara cada contrato de openapi/ con el /v3/api-docs que genera Springdoc en el servicio en marcha (Fase 9B).
# Para cada servicio: descarga /v3/api-docs (público), ejecuta CompararOpenApi.java y clasifica las diferencias:
#   REALES  - el YAML no coincide con el código: rutas, métodos, parámetros (nombre, ubicación, obligatorio, tipo, valores)
#             y campos de los DTO de request y response. Hay que corregirlas.
#   REGLAS  - lo que /v3/api-docs no puede mostrar y el contrato debe tener: 401 en las operaciones protegidas, 400 con
#             parámetros de ruta, consulta o cuerpo, 500, bearerAuth, ErrorResponse y ejemplos. Hay que corregirlas.
#   COSMÉTICAS - solo se informan: nombres de esquemas, formatos, tipos de contenido, "required" que Springdoc no deduce
#             (@NotBlank) y el 201/204 que Springdoc muestra como 200 (lo verifican Postman y las pruebas).
# También valida antes los 10 YAML con ValidarOpenApi.java (UTF-8, YAML y $ref).
# Uso (entorno levantado):  .\scripts\verificacion\comparar-openapi.ps1 [-Servicios eventos,finanzas] [-Detalle]
# El detalle completo de cada servicio queda en %TEMP%\maxi_openapi\<servicio>.txt. Sale con 1 si hay reales o reglas.
# Guardar en UTF-8 con BOM.
param([string[]]$Servicios = @(), [switch]$Detalle)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$puertos = [ordered]@{ auth = 8080; eventos = 8081; clientes = 8082; lugares = 8083; finanzas = 8084; contratos = 8085
    proveedores = 8086; auditoria = 8087; logistica = 8088; notificaciones = 8089 }
# Operaciones públicas permitidas (relativas al servidor del YAML); todo lo demás debe exigir bearerAuth.
$publicas = @{ auth = 'POST /auth/login' }
if ($Servicios.Count -eq 1 -and $Servicios[0] -match ',') { $Servicios = $Servicios[0] -split ',' }
if ($Servicios.Count -eq 0) { $Servicios = @($puertos.Keys) }

$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { "$env:USERPROFILE\.gradle" }
$jar = (Get-ChildItem "$gradleHome\caches\modules-2\files-2.1\org.yaml\snakeyaml" -Recurse -Filter 'snakeyaml-*.jar' |
        Where-Object Name -notmatch 'sources|javadoc' | Select-Object -First 1).FullName
if (-not $jar) { throw "No se encontró snakeyaml en $gradleHome (compila cualquier servicio con Gradle primero)." }
$carpeta = Join-Path ([IO.Path]::GetTempPath()) 'maxi_openapi'
New-Item -ItemType Directory -Force $carpeta | Out-Null

Write-Host '== ValidarOpenApi (UTF-8, YAML y $ref internas)'
$yamls = (@($Servicios | ForEach-Object { '"' + (Join-Path $raiz "openapi\$_.yaml") + '"' })) -join ' '
cmd /c "java -cp `"$jar`" `"$PSScriptRoot\ValidarOpenApi.java`" $yamls 2>&1" | ForEach-Object { Write-Host "  $_" }
$validacionOk = ($LASTEXITCODE -eq 0)

Write-Host '== Comparación con /v3/api-docs'
$filas = @(); $hayErrores = -not $validacionOk
foreach ($s in $Servicios) {
    $docs = Join-Path $carpeta "$s-api-docs.json"
    try {
        $r = Invoke-WebRequest "http://localhost:$($puertos[$s])/v3/api-docs" -UseBasicParsing -TimeoutSec 20
        [IO.File]::WriteAllBytes($docs, $r.RawContentStream.ToArray())
    } catch {
        Write-Host "  $s : no responde /v3/api-docs ($($_.Exception.Message)). ¿Está levantado?" -ForegroundColor Red
        $filas += [pscustomobject]@{ Servicio = $s; Reales = '?'; Reglas = '?'; Cosmeticas = '?' }; $hayErrores = $true; continue
    }
    $salida = @(cmd /c "java -cp `"$jar`" `"$PSScriptRoot\CompararOpenApi.java`" `"$(Join-Path $raiz "openapi\$s.yaml")`" `"$docs`" `"$($publicas[$s])`" 2>&1")
    $informe = Join-Path $carpeta "$s.txt"
    [IO.File]::WriteAllLines($informe, [string[]]$salida, (New-Object Text.UTF8Encoding $true))
    $resumen = $salida | Where-Object { $_ -like 'RESUMEN|*' } | Select-Object -Last 1
    if (-not $resumen) {
        Write-Host "  $s : el comparador falló (ver $informe)" -ForegroundColor Red
        $salida | Select-Object -First 10 | ForEach-Object { Write-Host "    $_" }
        $filas += [pscustomobject]@{ Servicio = $s; Reales = '?'; Reglas = '?'; Cosmeticas = '?' }; $hayErrores = $true; continue
    }
    $p = $resumen -split '\|'
    $filas += [pscustomobject]@{ Servicio = $s; Reales = [int]$p[1]; Reglas = [int]$p[2]; Cosmeticas = [int]$p[3] }
    if ([int]$p[1] + [int]$p[2] -gt 0) { $hayErrores = $true }
    $mostrar = if ($Detalle) { $salida | Where-Object { $_ -match '^(REAL|REGLA|COSM) ' } } else { $salida | Where-Object { $_ -match '^(REAL|REGLA) ' } }
    foreach ($l in $mostrar) {
        $color = if ($l -like 'COSM*') { 'DarkGray' } else { 'Yellow' }
        Write-Host "  [$s] $l" -ForegroundColor $color
    }
}

Write-Host ''
$filas | Format-Table -AutoSize | Out-String | Write-Host
$validas = @($filas | Where-Object { $_.Reales -is [int] })
$suma = { param($c) ($validas | ForEach-Object { $_.$c } | Measure-Object -Sum).Sum }
Write-Host ("Total: {0} reales, {1} reglas, {2} cosméticas (detalle en {3})" -f (& $suma 'Reales'), (& $suma 'Reglas'), (& $suma 'Cosmeticas'), $carpeta)
if ($hayErrores) { Write-Host 'Hay diferencias por corregir (REALES o REGLAS) o la validación falló.' -ForegroundColor Red; exit 1 }
Write-Host 'Contratos alineados con el código: 0 reales y 0 reglas.' -ForegroundColor Green
