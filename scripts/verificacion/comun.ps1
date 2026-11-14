# Utilidades comunes de verificación (Fase 4). Guardar en UTF-8 CON BOM para que PowerShell 5.1 lea bien las tildes.
Add-Type -AssemblyName System.Net.Http
$ErrorActionPreference = 'Stop'
# La salida de docker (logs, kafka-console-consumer) llega en UTF-8: sin esto las tildes se leen mal.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
. "$PSScriptRoot\credenciales.ps1"
$script:Raiz = $script:RaizProyecto
$script:Url = @{
    auth = 'http://localhost:8080/api/v1'; eventos = 'http://localhost:8081/api/v1'; clientes = 'http://localhost:8082/api/v1'
    lugares = 'http://localhost:8083/api/v1'; finanzas = 'http://localhost:8084/api/v1'; contratos = 'http://localhost:8085/api/v1'
    proveedores = 'http://localhost:8086/api/v1'; logistica = 'http://localhost:8088/api/v1'
    auditoria = 'http://localhost:8087/api/v1'; notificaciones = 'http://localhost:8089/api/v1'
    gateway = 'http://localhost:8090/graphql'
}
# .NET Framework abre solo 2 conexiones simultáneas por servidor: se sube para las pruebas de concurrencia.
[Net.ServicePointManager]::DefaultConnectionLimit = 20
$script:Http = New-Object System.Net.Http.HttpClient
$script:Http.Timeout = [TimeSpan]::FromSeconds(30)
$script:Token = $null
$script:Fallos = 0
$script:Pasos = 0
# Eventos creados por el script (POST /eventos 201 y crearEvento por GraphQL): Resumen los cancela al terminar para liberar
# sus fechas (desde la 10B3 solo hay un evento por fin de semana y los fines de semana son pocos). cargar-demo.ps1 pone
# ConservarEventosCreados = $true para no cancelar la demostración.
$script:EventosCreados = New-Object System.Collections.Generic.List[string]
$script:ConservarEventosCreados = $false

function NuevoMensaje([string]$Metodo, [string]$Ruta, $Cuerpo = $null, [switch]$SinToken, [hashtable]$Cabeceras = @{}) {
    $msg = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::new($Metodo)), $Ruta
    if ($script:Token -and -not $SinToken) { $msg.Headers.TryAddWithoutValidation('Authorization', "Bearer $($script:Token)") | Out-Null }
    foreach ($k in $Cabeceras.Keys) { $msg.Headers.TryAddWithoutValidation($k, [string]$Cabeceras[$k]) | Out-Null }
    if ($null -ne $Cuerpo) {
        $json = if ($Cuerpo -is [string]) { $Cuerpo } else { ConvertTo-Json $Cuerpo -Depth 10 -Compress }
        $msg.Content = New-Object System.Net.Http.ByteArrayContent (, [Text.Encoding]::UTF8.GetBytes($json))
        $msg.Content.Headers.ContentType = 'application/json; charset=utf-8'
    }
    $msg
}

function Api([string]$Metodo, [string]$Ruta, $Cuerpo = $null, [switch]$SinToken, [hashtable]$Cabeceras = @{}) {
    $msg = NuevoMensaje $Metodo $Ruta $Cuerpo -SinToken:$SinToken -Cabeceras $Cabeceras
    $r = LeerRespuesta ($script:Http.SendAsync($msg).GetAwaiter().GetResult())
    RegistrarEventoCreado $Metodo $Ruta $r
    $r
}

# Anota el id si la respuesta es la creación de un evento en eventos-service (POST /api/v1/eventos -> 201).
function RegistrarEventoCreado([string]$Metodo, [string]$Ruta, $Respuesta) {
    if ($Metodo -eq 'POST' -and $Respuesta.Status -eq 201 -and $Respuesta.Body -and $Respuesta.Body.id -and
            $Ruta -match ('^' + [regex]::Escape($script:Url.eventos) + '/eventos/?(\?.*)?$')) {
        $script:EventosCreados.Add([string]$Respuesta.Body.id)
    }
}

# Cancela los eventos que creó el script (los ya cancelados responden 409 y se cuentan aparte). La llama Resumen.
function CancelarEventosCreados {
    if ($script:ConservarEventosCreados -or $script:EventosCreados.Count -eq 0) { return }
    $ids = @($script:EventosCreados | Select-Object -Unique)
    $script:EventosCreados.Clear()
    $cancelados = 0; $yaCancelados = 0; $malos = @()
    foreach ($id in $ids) {
        $r = Api PATCH "$($script:Url.eventos)/eventos/$id/cancelar"
        if ($r.Status -eq 401) { Login | Out-Null; $r = Api PATCH "$($script:Url.eventos)/eventos/$id/cancelar" }
        if ($r.Status -eq 200) { $cancelados++ } elseif ($r.Status -eq 409) { $yaCancelados++ } else { $malos += "$id (HTTP $($r.Status))" }
    }
    Check "Limpieza: los $($ids.Count) eventos creados por el script quedan cancelados ($cancelados ahora, $yaCancelados ya lo estaban)" ($malos.Count -eq 0) ($malos -join '; ')
}

# Envía varias peticiones a la vez (pruebas de concurrencia). Cada elemento: @{ Metodo; Ruta; Cuerpo; Cabeceras }.
function ApiEnParalelo([object[]]$Peticiones) {
    $tareas = foreach ($p in $Peticiones) {
        $cab = if ($p.Cabeceras) { $p.Cabeceras } else { @{} }
        $script:Http.SendAsync((NuevoMensaje $p.Metodo $p.Ruta $p.Cuerpo -Cabeceras $cab))
    }
    [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($tareas))
    $lista = @($tareas)
    for ($i = 0; $i -lt $lista.Count; $i++) {
        $r = LeerRespuesta $lista[$i].Result
        RegistrarEventoCreado $Peticiones[$i].Metodo $Peticiones[$i].Ruta $r
        $r
    }
}

function LeerRespuesta($resp) {
    $bytes = $resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $tipo = if ($resp.Content.Headers.ContentType) { $resp.Content.Headers.ContentType.MediaType } else { '' }
    $texto = if ($tipo -like '*json*') { [Text.Encoding]::UTF8.GetString($bytes) } else { '' }
    $obj = if ($texto) { try { $texto | ConvertFrom-Json } catch { $null } } else { $null }
    $cab = @{}; foreach ($h in $resp.Headers) { $cab[$h.Key] = ($h.Value -join ',') }
    [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $obj; Texto = $texto; Bytes = $bytes; Tipo = $tipo; Headers = $cab }
}

function Check([string]$Nombre, [bool]$Condicion, [string]$Detalle = '') {
    $script:Pasos++
    if ($Condicion) { Write-Host ("  OK    {0}" -f $Nombre) }
    else { $script:Fallos++; Write-Host ("  FALLO {0} {1}" -f $Nombre, $Detalle) -ForegroundColor Red }
}

function CheckStatus([string]$Nombre, $Respuesta, [int]$Esperado) {
    $det = if ($Respuesta.Status -ne $Esperado) { "(obtuvo $($Respuesta.Status): $($Respuesta.Texto))" } else { '' }
    Check "$Nombre -> $Esperado" ($Respuesta.Status -eq $Esperado) $det
}

function Login {
    $r = Api POST "$($script:Url.auth)/auth/login" (CuerpoLogin) -SinToken
    if ($r.Status -eq 200) { $script:Token = $r.Body.token }
    return $r
}

function Sufijo { (Get-Date).ToString('HHmmss') + (Get-Random -Maximum 999) }

# Regla (2026-11-10): todo dato creado por los scripts o por Postman lleva el prefijo "Prueba" (la interfaz lo oculta y no se
# confunde con la demostración). Aquí se exige: un nombre sin el prefijo detiene el script antes de crear nada.
function CrearCliente([string]$Nombre) {
    if ($Nombre -notmatch '^Prueba ') { throw "CrearCliente: '$Nombre' debe empezar por 'Prueba ' (regla de los datos de prueba)." }
    $s = Sufijo
    Api POST "$($script:Url.clientes)/clientes" @{ nombreCompleto = $Nombre; telefono = "300$((Get-Random -Minimum 1000000 -Maximum 9999999))"; correo = "cliente.$s@ejemplo.com"; direccion = 'Calle 10 # 5-20, Neiva' }
}

# Una fecha (el sábado) por cada fin de semana libre, en el rango máximo de la API (366 días). Desde la 10B3 solo se
# registran eventos en viernes, sábado o domingo y hay un evento por fin de semana, así que cada fecha devuelta es de un
# fin de semana distinto y entero libre (fechas/disponibles ya quita el fin de semana completo si un día está tomado).
# Los scripts cancelan al final lo que crean (Resumen). Siempre devuelve un arreglo (la coma evita que PowerShell
# convierta un arreglo de un elemento en un texto).
function FechasLibres {
    $desde = (Get-Date).AddDays(1).ToString('yyyy-MM-dd'); $hasta = (Get-Date).AddDays(366).ToString('yyyy-MM-dd')
    $r = Api GET "$($script:Url.eventos)/fechas/disponibles?desde=$desde&hasta=$hasta"
    , @($r.Body | Where-Object { ([datetime]$_.fecha).DayOfWeek -eq 'Saturday' } | ForEach-Object { $_.fecha })
}

# Un martes futuro (día no permitido) para comprobar el 400 de la regla de días permitidos.
function MartesFuturo([int]$Semanas = 3) {
    $d = (Get-Date).Date.AddDays(7 * $Semanas)
    while ($d.DayOfWeek -ne 'Tuesday') { $d = $d.AddDays(1) }
    $d.ToString('yyyy-MM-dd')
}

function Redis([string]$Comando) {
    Push-Location $script:Raiz
    try { docker compose exec -T redis sh -c "export REDISCLI_AUTH=`"`$REDIS_PASSWORD`"; $Comando" } finally { Pop-Location }
}

function Psql([string]$Sql) {
    Push-Location $script:Raiz
    try {
        $u = LeerVariableEnv 'POSTGRES_USER'
        docker compose exec -T postgres-db psql -U $u -d maxi_eventos -t -A -c $Sql
    } finally { Pop-Location }
}

function LogsDesde([string]$Servicio, [datetime]$Desde) {
    Compose "logs $Servicio --since $($Desde.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))" | Out-String
}

# docker compose sin que PowerShell 5.1 convierta su salida de stderr en un error que detiene el script.
function Compose([string]$Argumentos) {
    Push-Location $script:Raiz
    try { cmd /c "docker compose $Argumentos 2>&1" } finally { Pop-Location }
}

# Arranca los servicios que estén detenidos (por ejemplo, si un script anterior se interrumpió tras un "stop") y espera
# a que todos estén healthy. Se llama al empezar cada script de verificación.
function AsegurarEntornoArrancado([int]$Segundos = 240) {
    Compose 'start' | Out-Null
    $fin = (Get-Date).AddSeconds($Segundos)
    do {
        $pendientes = @(Compose 'ps --format "{{.Service}} {{.State}} {{.Health}}"' | Where-Object { $_ -match ' (exited|created|starting|unhealthy)' })
        if ($pendientes.Count -gt 0) { Start-Sleep -Seconds 5 }
    } while ($pendientes.Count -gt 0 -and (Get-Date) -lt $fin)
    if ($pendientes.Count -gt 0) { Write-Host "  AVISO servicios sin arrancar: $($pendientes -join '; ')" -ForegroundColor Yellow }
}

function EsperarHealthy([string]$Servicio, [int]$Segundos = 150) {
    Push-Location $script:Raiz
    try {
        $fin = (Get-Date).AddSeconds($Segundos)
        do { Start-Sleep -Seconds 5; $h = docker inspect --format '{{.State.Health.Status}}' (docker compose ps -q $Servicio) } while ($h -ne 'healthy' -and (Get-Date) -lt $fin)
        return $h
    } finally { Pop-Location }
}

# Nombres del flujo de verificación: con el prefijo "Prueba" para no coincidir con los datos de demostración de
# cargar-demo.ps1 (Postman los busca por nombre). Fase 9E.
$script:NombreClienteFlujo = 'Prueba Lucía Fernández Ospina'
$script:NombreEventoFlujo = 'Prueba Quince años de Lucía'

# Flujo de la Fase 3: crear evento -> cotizar -> contrato -> firmar -> abonar -> confirmar. Devuelve el evento confirmado.
# $CotizarCuerpo / $ContratoCuerpo permiten ajustar los cuerpos cuando cambian en una subfase.
function FlujoFase3([string]$Fecha, [scriptblock]$CotizarCuerpo = $null, $ContratoCuerpo = 'normal') {
    Write-Host "-- Flujo de la Fase 3 (fecha $Fecha)"
    $c = CrearCliente $script:NombreClienteFlujo
    CheckStatus 'Flujo: crear cliente' $c 201
    $ev = Api POST "$($script:Url.eventos)/eventos" @{ nombreEvento = $script:NombreEventoFlujo; clienteId = $c.Body.id; fecha = $Fecha; tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 90; cantidadNinos = 15; zona = 'NEIVA' }
    CheckStatus 'Flujo: crear evento' $ev 201
    $id = $ev.Body.id
    $cuerpo = if ($CotizarCuerpo) { & $CotizarCuerpo $ev.Body } else { @{ eventoId = $id; invitados = 105; zona = 'NEIVA' } }
    $cot = Api POST "$($script:Url.finanzas)/cotizaciones" $cuerpo
    CheckStatus 'Flujo: cotizar' $cot 201
    $cc = if ($ContratoCuerpo -eq 'normal') { @{ nombreEvento = $script:NombreEventoFlujo } } else { $ContratoCuerpo }
    $con = Api POST "$($script:Url.contratos)/eventos/$id/contrato" $cc
    CheckStatus 'Flujo: crear contrato' $con 201
    CheckStatus 'Flujo: confirmar sin firmar' (Api PATCH "$($script:Url.eventos)/eventos/$id/confirmar") 409
    CheckStatus 'Flujo: firmar contrato' (Api PATCH "$($script:Url.contratos)/eventos/$id/contrato/marcar-firmado") 200
    CheckStatus 'Flujo: confirmar sin abono' (Api PATCH "$($script:Url.eventos)/eventos/$id/confirmar") 409
    $reserva = [decimal]$cot.Body.montoReservaRequerido
    $mitad = [math]::Round($reserva / 2, 2)
    $p1 = Api POST "$($script:Url.finanzas)/finanzas/pagos" @{ eventoId = $id; clienteId = $c.Body.id; metodoPago = 'TRANSFERENCIA'; monto = $mitad; moneda = 'COP' }
    CheckStatus 'Flujo: abono parcial' $p1 201
    CheckStatus 'Flujo: confirmar con abono insuficiente' (Api PATCH "$($script:Url.eventos)/eventos/$id/confirmar") 409
    $p2 = Api POST "$($script:Url.finanzas)/finanzas/pagos" @{ eventoId = $id; clienteId = $c.Body.id; metodoPago = 'EFECTIVO'; monto = ($reserva - $mitad); moneda = 'COP' }
    CheckStatus 'Flujo: abono del resto de la reserva' $p2 201
    $conf = Api PATCH "$($script:Url.eventos)/eventos/$id/confirmar"
    CheckStatus 'Flujo: confirmar' $conf 200
    Check 'Flujo: evento RESERVADO' ($conf.Body.estado -eq 'RESERVADO') "estado=$($conf.Body.estado)"
    $log = $null
    for ($i = 0; $i -lt 10 -and (-not $log -or $log.Status -ne 200); $i++) { Start-Sleep -Seconds 2; $log = Api GET "$($script:Url.logistica)/eventos/$id/logistica" }
    CheckStatus 'Flujo: logística calculada por Kafka' $log 200
    [pscustomobject]@{ EventoId = $id; ClienteId = $c.Body.id; Cotizacion = $cot.Body; Pagos = @($p1.Body, $p2.Body) }
}

# Descarga un PDF y extrae su texto con TextoPdf.java (OpenPDF de la caché de Gradle). Los archivos quedan en %TEMP%\maxi_verificacion.
function DescargarPdf([string]$Ruta, [string]$Nombre) {
    $r = Api GET $Ruta
    $carpeta = Join-Path ([IO.Path]::GetTempPath()) 'maxi_verificacion'
    New-Item -ItemType Directory -Force $carpeta | Out-Null
    $pdf = Join-Path $carpeta "$Nombre.pdf"; $txt = Join-Path $carpeta "$Nombre.txt"
    $texto = ''
    if ($r.Status -eq 200) {
        [IO.File]::WriteAllBytes($pdf, $r.Bytes)
        $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { "$env:USERPROFILE\.gradle" }
        $jar = (Get-ChildItem "$gradleHome\caches\modules-2\files-2.1\com.github.librepdf\openpdf" -Recurse -Filter 'openpdf-*.jar' |
                Where-Object Name -notmatch 'sources|javadoc' | Select-Object -First 1).FullName
        cmd /c "java -cp `"$jar`" `"$PSScriptRoot\TextoPdf.java`" `"$pdf`" `"$txt`" 2>&1" | Out-Null
        if (Test-Path $txt) { $texto = [IO.File]::ReadAllText($txt, [Text.Encoding]::UTF8) }
    }
    [pscustomobject]@{ Status = $r.Status; Tipo = $r.Tipo; Headers = $r.Headers; Texto = $texto; Archivo = $pdf; Respuesta = $r }
}

# Patrones para el texto de los PDF rediseñados tras la Fase 10 (diagnóstico 11A): la etiqueta y el valor van en celdas
# distintas, así que el texto extraído es "Nombre María José…", "Tipo de eventoBoda" (pegados) o la etiqueta en una línea y el
# valor en la siguiente ("Valor total del contrato" / "8.700.000,00 COP"). Se exige la etiqueta seguida EXACTAMENTE del valor
# y nada más en esa línea (la misma exigencia que el antiguo "Etiqueta: valor").
function PatronDato([string]$Etiqueta, [string]$Valor) {
    '(?m)^\s*' + [regex]::Escape($Etiqueta) + '\s*' + [regex]::Escape($Valor) + '[ \t]*\r?$'
}
# Una fila de la tabla de adicionales del anexo: servicio, cantidad, valor unitario y subtotal (celdas pegadas en el texto),
# sola en su línea. Equivale al antiguo "N. Servicio: cantidad × unitario = subtotal".
function PatronFilaAdicional([string]$Servicio, [int]$Cantidad, [string]$Unitario, [string]$Subtotal) {
    '(?m)^\s*' + [regex]::Escape($Servicio) + '\s*' + $Cantidad + '\s*' + [regex]::Escape($Unitario) + '\s*' + [regex]::Escape($Subtotal) + '[ \t]*\r?$'
}

# Fila del outbox (eventos_mensajes_salientes o finanzas_mensajes_salientes) de un evento y tipo de mensaje.
function FilaOutbox([string]$Tabla, [string]$EventoId, [string]$Tipo) {
    $l = Psql "SELECT id_evento, estado, intentos, coalesce(ultimo_error, '') FROM $Tabla WHERE clave = '$EventoId' AND tipo = '$Tipo' ORDER BY creado_en DESC LIMIT 1;"
    $p = "$l".Trim() -split '\|', 4
    if ($p.Count -lt 3) { return $null }
    [pscustomobject]@{ IdEvento = $p[0]; Estado = $p[1]; Intentos = [int]$p[2]; UltimoError = $p[3] }
}

# Espera (hasta $Segundos) a que la fila del outbox llegue al estado indicado; devuelve la fila.
function EsperarEstadoOutbox([string]$Tabla, [string]$EventoId, [string]$Tipo, [string]$Estado, [int]$Segundos = 90) {
    $fin = (Get-Date).AddSeconds($Segundos)
    do {
        $fila = FilaOutbox $Tabla $EventoId $Tipo
        if ($fila -and $fila.Estado -eq $Estado) { return $fila }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $fin)
    $fila
}

# Lee todos los mensajes de un tópico (desde el principio). Devuelve objetos con Particion, Clave, Valor (texto) y Json.
function LeerTopico([string]$Topico, [int]$EsperaMs = 8000) {
    $lineas = Compose "exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic $Topico --from-beginning --property print.key=true --property print.partition=true --timeout-ms $EsperaMs"
    foreach ($l in $lineas) {
        $partes = "$l" -split "`t", 3
        if ($partes.Count -eq 3 -and $partes[0] -match '^Partition:(\d+)$') {
            $json = $null; try { $json = $partes[2] | ConvertFrom-Json } catch { }
            [pscustomobject]@{ Particion = [int]$Matches[1]; Clave = $partes[1]; Valor = $partes[2]; Json = $json }
        }
    }
}

# Publica mensajes directamente en un tópico (para probar duplicados, mensajes inválidos o ilegibles). Cada línea es
# "clave#valor". El archivo va en UTF-8 SIN BOM: un BOM quedaría pegado al primer mensaje.
function InyectarMensajes([string]$Topico, [string[]]$Lineas) {
    $archivo = Join-Path ([IO.Path]::GetTempPath()) "maxi_inyectar_$(Get-Random).txt"
    [IO.File]::WriteAllText($archivo, ($Lineas -join "`n") + "`n", (New-Object Text.UTF8Encoding $false))
    try {
        Push-Location $script:Raiz
        cmd /c "docker compose exec -T kafka kafka-console-producer --bootstrap-server localhost:9092 --topic $Topico --property parse.key=true --property key.separator=# < `"$archivo`" 2>&1" | Out-Null
    } finally {
        Pop-Location
        Remove-Item $archivo -ErrorAction SilentlyContinue
    }
}

# Total de mensajes de un tópico (suma de los offsets finales de sus particiones).
function TotalMensajes([string]$Topico) {
    $total = 0
    foreach ($l in (Compose "exec -T kafka kafka-get-offsets --bootstrap-server localhost:9092 --topic $Topico")) {
        if ("$l" -match ':(\d+)$') { $total += [long]$Matches[1] }
    }
    $total
}

# Texto completo de un tópico con cabeceras (para buscar en los DLT el mensaje y kafka_dlt-original-consumer-group).
function TextoTopicoConCabeceras([string]$Topico, [int]$EsperaMs = 8000) {
    Compose "exec -T kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic $Topico --from-beginning --property print.headers=true --property print.key=true --timeout-ms $EsperaMs" | Out-String
}

# Petición GraphQL al gateway (Fase 6). Devuelve la respuesta de Api; en Body están data y errors (HTTP siempre 200).
function GraphQL([string]$Documento, [hashtable]$Variables = @{}, [switch]$SinToken) {
    $r = Api POST $script:Url.gateway @{ query = $Documento; variables = $Variables } -SinToken:$SinToken
    if ($r.Body -and $r.Body.data -and $r.Body.data.crearEvento -and $r.Body.data.crearEvento.id) {
        $script:EventosCreados.Add([string]$r.Body.data.crearEvento.id)
    }
    $r
}

# Primer error GraphQL de una respuesta (o $null): message, path, extensions.classification/status/errores.
function ErrorGraphQL($Respuesta) {
    if ($Respuesta.Body -and $Respuesta.Body.errors) { @($Respuesta.Body.errors)[0] } else { $null }
}

function Resumen([string]$Titulo) {
    CancelarEventosCreados
    Write-Host ''
    $color = if ($script:Fallos -eq 0) { 'Green' } else { 'Red' }
    Write-Host ("{0}: {1} comprobaciones, {2} fallos" -f $Titulo, $script:Pasos, $script:Fallos) -ForegroundColor $color
}
