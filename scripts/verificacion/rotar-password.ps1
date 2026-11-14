# Rota la contraseña de la organizadora en el .env LOCAL (Fase 7). No muestra ningún valor.
# - Genera una contraseña aleatoria fuerte (24 caracteres, generador criptográfico, letras, dígitos, '.' y '_').
# - Calcula su hash BCrypt con HashBcrypt.java (spring-security-crypto de la caché de Gradle, el mismo encoder que Auth).
# - Escribe la contraseña en VERIFICACION_PASSWORD (la leen los scripts; úsala también en GraphiQL y Postman) y el hash
#   en AUTH_ORGANIZER_PASSWORD_HASH (entre comillas simples, por los '$'), conservando el resto del .env y sus finales LF.
# Después: docker compose up -d auth-service   (recrea Auth con el hash nuevo).
# Fase 11D: -Usuario asistente hace lo mismo para el ASISTENTE: VERIFICACION_ASISTENTE_PASSWORD y
#   AUTH_ASISTENTE_PASSWORD_HASH; si faltan, agrega también AUTH_ASISTENTE_EMAIL (asistente@<dominio de la organizadora>),
#   AUTH_ASISTENTE_ID (UUID nuevo) y AUTH_ASISTENTE_NOMBRE (Asistente). Sin -Usuario: la organizadora, como antes.
# Requiere haber compilado antes algún servicio (para tener spring-security-crypto en la caché de Gradle: %GRADLE_USER_HOME%, o ~\.gradle si no está definida). Guardar en UTF-8 con BOM.
param([ValidateSet('organizadora', 'asistente')][string]$Usuario = 'organizadora')
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$archivoEnv = Join-Path $raiz '.env'
if (-not (Test-Path $archivoEnv)) { throw "No existe $archivoEnv (copia antes .env.example a .env)." }

# 1. Contraseña: 64 símbolos posibles (6 bits por carácter, sin sesgo) → 144 bits; con mayúscula, minúscula, dígito y símbolo.
$alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._'
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
do {
    $bytes = New-Object byte[] 24
    $rng.GetBytes($bytes)
    $password = -join ($bytes | ForEach-Object { $alfabeto[$_ % 64] })
} until ($password -cmatch '[A-Z]' -and $password -cmatch '[a-z]' -and $password -match '[0-9]' -and $password -match '[._]')

# 2. Hash BCrypt (la contraseña viaja por la entrada estándar, nunca como argumento)
$gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { "$env:USERPROFILE\.gradle" }
$cache = "$gradleHome\caches\modules-2\files-2.1"
$crypto = Get-ChildItem "$cache\org.springframework.security\spring-security-crypto" -Recurse -Filter 'spring-security-crypto-*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object Name -Descending | Select-Object -First 1
$jcl = Get-ChildItem "$cache\org.springframework\spring-jcl" -Recurse -Filter 'spring-jcl-*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object Name -Descending | Select-Object -First 1
if (-not $crypto -or -not $jcl) { throw 'No se encontró spring-security-crypto o spring-jcl en la caché de Gradle: compila auth-service antes.' }
# Sin esto PowerShell puede anteponer un BOM a lo que escribe en la tubería y el hash no correspondería a la contraseña
$OutputEncoding = New-Object Text.UTF8Encoding $false
$hash =($password | java -cp "$($crypto.FullName);$($jcl.FullName)" "$PSScriptRoot\HashBcrypt.java" | Select-Object -Last 1)
if ($LASTEXITCODE -ne 0 -or $hash -notmatch '^\$2[aby]\$10\$.{53}$') { throw 'No se pudo calcular el hash BCrypt.' }

# 3. Escribir el .env (UTF-8 sin BOM, finales LF, el resto de líneas sin cambios)
$lineas = [Collections.Generic.List[string]]([IO.File]::ReadAllText($archivoEnv, [Text.Encoding]::UTF8) -split "`r?`n")
if ($lineas.Count -gt 0 -and $lineas[$lineas.Count - 1] -eq '') { $lineas.RemoveAt($lineas.Count - 1) }
$valor = { param($clave) $l = $lineas | Where-Object { $_ -match "^$clave=" } | Select-Object -First 1; if ($l) { ($l -split '=', 2)[1].Trim().Trim("'").Trim('"') } else { '' } }
if ($Usuario -eq 'asistente') {
    $nuevos = [ordered]@{ AUTH_ASISTENTE_PASSWORD_HASH = "'$hash'"; VERIFICACION_ASISTENTE_PASSWORD = $password }
    if (-not (& $valor 'AUTH_ASISTENTE_EMAIL')) {
        $dominio = ((& $valor 'AUTH_ORGANIZER_EMAIL') -split '@', 2)[1]
        if (-not $dominio) { throw 'Define AUTH_ORGANIZER_EMAIL o AUTH_ASISTENTE_EMAIL en el .env.' }
        $nuevos['AUTH_ASISTENTE_EMAIL'] = "asistente@$dominio"
    }
    if (-not (& $valor 'AUTH_ASISTENTE_ID')) { $nuevos['AUTH_ASISTENTE_ID'] = [guid]::NewGuid().ToString() }
    if (-not (& $valor 'AUTH_ASISTENTE_NOMBRE')) { $nuevos['AUTH_ASISTENTE_NOMBRE'] = 'Asistente' }
} else {
    $nuevos = [ordered]@{ AUTH_ORGANIZER_PASSWORD_HASH = "'$hash'"; VERIFICACION_PASSWORD = $password }
}
foreach ($clave in $nuevos.Keys) {
    $i = $lineas.FindIndex([Predicate[string]] { param($l) $l -match "^$clave=" })
    if ($i -ge 0) { $lineas[$i] = "$clave=$($nuevos[$clave])" } else { $lineas.Add("$clave=$($nuevos[$clave])") }
}
[IO.File]::WriteAllText($archivoEnv, (($lineas -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
$password = $null; $hash = $null
if ($Usuario -eq 'asistente') {
    Write-Host 'Contraseña del ASISTENTE en .env: VERIFICACION_ASISTENTE_PASSWORD (contraseña) y AUTH_ASISTENTE_PASSWORD_HASH (hash BCrypt). No se muestran.'
} else {
    Write-Host 'Contraseña rotada en .env: VERIFICACION_PASSWORD (contraseña) y AUTH_ORGANIZER_PASSWORD_HASH (hash BCrypt). No se muestran.'
}
Write-Host 'Siguiente paso: docker compose up -d auth-service'
