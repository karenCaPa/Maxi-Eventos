# Credenciales de la organizadora para los scripts de verificación. Nunca se escriben en los scripts:
# - el correo sale de AUTH_ORGANIZER_EMAIL del .env de la raíz del proyecto;
# - la contraseña sale de VERIFICACION_PASSWORD del mismo .env; si no está definida, se pide con Read-Host (sin mostrarla).
# Uso: . "$PSScriptRoot\credenciales.ps1"   y luego   CuerpoLogin   (JSON listo para POST /auth/login).

$script:RaizProyecto = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function LeerVariableEnv([string]$Nombre) {
    $archivo = Join-Path $script:RaizProyecto '.env'
    if (-not (Test-Path $archivo)) { return $null }
    $linea = Get-Content $archivo -Encoding UTF8 | Where-Object { $_ -match "^$Nombre=" } | Select-Object -First 1
    if (-not $linea) { return $null }
    $valor = ($linea -replace "^$Nombre=", '').Trim().Trim("'", '"')
    if ($valor) { $valor } else { $null }
}

$script:CorreoOrganizadora = LeerVariableEnv 'AUTH_ORGANIZER_EMAIL'
if (-not $script:CorreoOrganizadora) { $script:CorreoOrganizadora = Read-Host 'Correo de la organizadora' }

$script:PasswordOrganizadora = LeerVariableEnv 'VERIFICACION_PASSWORD'
if (-not $script:PasswordOrganizadora) {
    $segura = Read-Host 'Contraseña de la organizadora' -AsSecureString
    $puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
    try { $script:PasswordOrganizadora = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero) }
}

# Fase 11D: el ASISTENTE (opcional; no se pide por teclado): AUTH_ASISTENTE_EMAIL y VERIFICACION_ASISTENTE_PASSWORD.
# Los escribe rotar-password.ps1 -Usuario asistente. Si faltan, los casos de 403 del asistente fallan y lo dicen.
$script:CorreoAsistente = LeerVariableEnv 'AUTH_ASISTENTE_EMAIL'
$script:PasswordAsistente = LeerVariableEnv 'VERIFICACION_ASISTENTE_PASSWORD'

function CuerpoLogin {
    ConvertTo-Json @{ correo = $script:CorreoOrganizadora; password = $script:PasswordOrganizadora } -Compress
}
