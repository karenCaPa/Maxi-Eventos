# Ejecuta la colección de Postman (postman/) con Newman (Fase 9C), sin instalarlo globalmente (npx newman@6.2.1).
# - La contraseña sale de VERIFICACION_PASSWORD del .env (o Read-Host, ver credenciales.ps1) y el correo de AUTH_ORGANIZER_EMAIL.
#   Se pasan en una COPIA TEMPORAL del environment en %TEMP% que se borra al terminar: nunca en la línea de comandos, en
#   postman/ ni en los resultados.
# - Reporters cli y junit. Resultados en postman/resultados/newman-<fecha>.txt (salida del cli) y .xml (junit).
# - Al final revisa que los resultados no contengan la contraseña ni un token JWT; si los contienen, los borra y falla.
# Requisitos: el entorno levantado (docker compose up -d) y los datos de demostración cargados (cargar-demo.ps1).
# Uso:  .\scripts\verificacion\ejecutar-postman.ps1 [-Carpeta "10 Flujo completo del negocio"]
# Guardar en UTF-8 con BOM.
param([string]$Carpeta = '')
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\credenciales.ps1"
$raiz = $script:RaizProyecto
$coleccion = Join-Path $raiz 'postman\maxi-eventos.postman_collection.json'
$entorno = Join-Path $raiz 'postman\maxi-eventos-local.postman_environment.json'
$resultados = Join-Path $raiz 'postman\resultados'
New-Item -ItemType Directory -Force $resultados | Out-Null
$sello = (Get-Date).ToString('yyyyMMdd-HHmmss')
$txt = Join-Path $resultados "newman-$sello.txt"
$xml = Join-Path $resultados "newman-$sello.xml"

# Copia temporal del environment con el correo y la contraseña
$temporal = Join-Path ([IO.Path]::GetTempPath()) "maxi_postman_env_$([guid]::NewGuid().ToString('N')).json"
$datosEntorno = [IO.File]::ReadAllText($entorno, [Text.Encoding]::UTF8) | ConvertFrom-Json
foreach ($v in $datosEntorno.values) {
    if ($v.key -eq 'correo') { $v.value = $script:CorreoOrganizadora }
    if ($v.key -eq 'password') { $v.value = $script:PasswordOrganizadora }
    # Fase 11D: el asistente (carpeta "12b Roles")
    if ($v.key -eq 'correoAsistente' -and $script:CorreoAsistente) { $v.value = $script:CorreoAsistente }
    if ($v.key -eq 'passwordAsistente' -and $script:PasswordAsistente) { $v.value = $script:PasswordAsistente }
}
[IO.File]::WriteAllText($temporal, ($datosEntorno | ConvertTo-Json -Depth 10), (New-Object Text.UTF8Encoding $false))
try {
    $folder = if ($Carpeta) { "--folder `"$Carpeta`"" } else { '' }
    Write-Host "Ejecutando Newman (la primera vez npx descarga newman@6.2.1)..."
    cmd /c "npx --yes newman@6.2.1 run `"$coleccion`" -e `"$temporal`" $folder -r cli,junit --reporter-junit-export `"$xml`" --color off --timeout-request 30000 > `"$txt`" 2>&1"
    $codigo = $LASTEXITCODE
} finally {
    Remove-Item $temporal -Force -ErrorAction SilentlyContinue
}

# Ningún archivo de resultados puede contener la contraseña ni un token
$fugas = @()
foreach ($archivo in @($txt, $xml) | Where-Object { Test-Path $_ }) {
    $contenido = [IO.File]::ReadAllText($archivo, [Text.Encoding]::UTF8)
    if ($script:PasswordOrganizadora -and $contenido.Contains($script:PasswordOrganizadora)) { $fugas += "$archivo (contraseña)" }
    if ($script:PasswordAsistente -and $contenido.Contains($script:PasswordAsistente)) { $fugas += "$archivo (contraseña del asistente)" }
    if ($contenido -match 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}') { $fugas += "$archivo (token JWT)" }
}
if ($fugas.Count -gt 0) {
    Remove-Item $txt, $xml -Force -ErrorAction SilentlyContinue
    Write-Host "ERROR: los resultados contenían datos sensibles y se borraron: $($fugas -join '; ')" -ForegroundColor Red
    exit 1
}

# Sin los avisos de npm (descarga de newman); después, la tabla final del reporter cli y los fallos
$lineas = @([IO.File]::ReadAllLines($txt, [Text.Encoding]::UTF8) | Where-Object { $_ -notmatch '^npm (warn|notice)' })
[IO.File]::WriteAllLines($txt, [string[]]$lineas, (New-Object Text.UTF8Encoding $false))
$inicio = [Array]::FindLastIndex($lineas, [Predicate[string]] { param($l) $l -match 'executed\s+[|│]\s+failed' })
if ($inicio -ge 1) { $lineas[($inicio - 1)..([Math]::Min($lineas.Length - 1, $inicio + 16))] | ForEach-Object { Write-Host $_ } }
$fallos = @($lineas | Where-Object { $_ -match '^\s*\d+\.\s+(AssertionError|JSONError|Error)' })
if ($fallos.Count -gt 0) { Write-Host 'Fallos:' -ForegroundColor Red; $fallos | ForEach-Object { Write-Host "  $_" -ForegroundColor Red } }
Write-Host "Resultados: $txt"
Write-Host "            $xml"
if ($codigo -ne 0) { Write-Host "Newman terminó con fallos (código $codigo)." -ForegroundColor Red; exit 1 }
Write-Host 'Colección sin fallos.' -ForegroundColor Green
