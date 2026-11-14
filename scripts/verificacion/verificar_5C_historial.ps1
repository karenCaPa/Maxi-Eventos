# Verificación del PRIMER despliegue de auditoria-service y notificaciones-service (subfase 5C).
# Los grupos nuevos empiezan en earliest y consumen el historial de los tópicos. Este script:
#   1. Con -Antes (ANTES de crear los servicios nuevos): guarda cuántos mensajes tiene cada DLT y de qué grupo.
#   2. Sin parámetros (después del despliegue): clasifica cada mensaje histórico como lo haría cada consumidor y
#      comprueba que los válidos quedaron registrados una sola vez (con el JSON original), que los duplicados se
#      ignoraron, que los inválidos fueron al DLT una sola vez sin reintentos, que los mensajes antiguos de los DLT no
#      se reprocesaron (nadie consume un *.DLT) y que el LAG de los dos grupos es 0.
# Guardar en UTF-8 con BOM.
param([switch]$Antes)
. "$PSScriptRoot\comun.ps1"

$foto = Join-Path ([IO.Path]::GetTempPath()) 'maxi_5c_historial_antes.json'
$dlts = 'eventos.confirmados.DLT', 'pagos.registrados.DLT', 'servicios-adicionales.agregados.DLT'
$grupos = 'logistica-group', 'contratos-group', 'auditoria-group', 'notificaciones-group'

function ContarGrupoEnDlt([string]$Texto, [string]$Grupo) { @([regex]::Matches($Texto, "kafka_dlt-original-consumer-group:$Grupo(,|\t|$)")).Count }
function FotoDlt {
    $r = [ordered]@{}
    foreach ($d in $dlts) {
        $texto = if ((TotalMensajes $d) -gt 0) { TextoTopicoConCabeceras $d } else { '' }
        $r[$d] = [ordered]@{ total = [long](TotalMensajes $d) }
        foreach ($g in $grupos) { $r[$d][$g] = ContarGrupoEnDlt $texto $g }
    }
    $r
}

if ($Antes) {
    $datos = FotoDlt
    $datos | ConvertTo-Json -Depth 4 | Set-Content -Path $foto -Encoding UTF8
    Write-Host "Foto de los DLT guardada en $foto"
    $datos | ConvertTo-Json -Depth 4
    return
}
if (-not (Test-Path $foto)) { throw "Falta la foto previa: ejecutar primero con -Antes (antes de desplegar los servicios nuevos)." }
$fotoAntes = Get-Content $foto -Raw | ConvertFrom-Json
AsegurarEntornoArrancado

function EsUuid($v) { $g = [guid]::Empty; ($null -ne $v) -and [guid]::TryParse("$v", [ref]$g) }
function SobreValido($m, [string]$Tipo) {
    $j = $m.Json
    ($null -ne $j) -and ($j -isnot [string]) -and (EsUuid $j.idEvento) -and (EsUuid $j.eventoId) -and $j.tipo -eq $Tipo -and ($null -ne $j.datos)
}
function ValidoParaNotificacion($m, [string]$Tipo) {
    if (-not (SobreValido $m $Tipo)) { return $false }
    $d = $m.Json.datos
    if ("$($d.eventoId)" -ne "$($m.Json.eventoId)" -or -not (EsUuid $d.clienteId)) { return $false }
    if ($Tipo -eq 'EVENTO_CONFIRMADO') { return (-not [string]::IsNullOrWhiteSpace($d.nombreEvento)) -and ($null -ne $d.fecha) }
    return ($null -ne $d.monto) -and ([decimal]$d.monto -gt 0)
}
function EsperarLag0([string]$Grupo, [int]$Segundos = 180) {
    $fin = (Get-Date).AddSeconds($Segundos)
    do {
        $filas = @(Compose "exec -T kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group $Grupo" |
                Where-Object { "$_" -match '^\S+\s+\S+\s+\d+\s+' })
        $lag = ($filas | ForEach-Object { $c = "$_" -split '\s+'; if ($c[5] -match '^\d+$') { [long]$c[5] } else { 999999 } } | Measure-Object -Sum).Sum
        if ($filas.Count -gt 0 -and $lag -eq 0) { return [pscustomobject]@{ Lag = 0; Filas = $filas } }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $fin)
    [pscustomobject]@{ Lag = $lag; Filas = $filas }
}

$topicos = [ordered]@{ 'eventos.confirmados' = 'EVENTO_CONFIRMADO'; 'pagos.registrados' = 'PAGO_REGISTRADO'; 'servicios-adicionales.agregados' = 'SERVICIO_ADICIONAL_AGREGADO' }
$notificables = 'eventos.confirmados', 'pagos.registrados'

Write-Host '== 1. Los dos grupos nuevos terminan de leer el historial (LAG 0)'
$lagA = EsperarLag0 'auditoria-group'
$lagN = EsperarLag0 'notificaciones-group'
Check 'auditoria-group: LAG 0 en las 9 particiones (3 tópicos)' ($lagA.Lag -eq 0 -and $lagA.Filas.Count -eq 9) "LAG=$($lagA.Lag), filas=$($lagA.Filas.Count)"
Check 'notificaciones-group: LAG 0 en las 6 particiones (2 tópicos)' ($lagN.Lag -eq 0 -and $lagN.Filas.Count -eq 6) "LAG=$($lagN.Lag), filas=$($lagN.Filas.Count)"

Write-Host '== 2. Clasificación del historial (como lo hace cada consumidor)'
$esperado = @{}
$payloadEsperado = @{}
$totInvA = 0; $totInvN = 0; $totDupA = 0; $totDupN = 0; $idsNotif = @{}
foreach ($t in $topicos.Keys) {
    $mensajes = @(LeerTopico $t 10000)
    Check "Se leyeron todos los mensajes de $t ($($mensajes.Count))" ($mensajes.Count -eq (TotalMensajes $t)) ''
    $validos = @($mensajes | Where-Object { SobreValido $_ $topicos[$t] })
    $unicos = @{}
    foreach ($m in $validos) { $k = "$($m.Json.idEvento)".ToLower(); if (-not $unicos.ContainsKey($k)) { $unicos[$k] = $m.Valor } }
    $esperado[$t] = $unicos
    foreach ($k in $unicos.Keys) { $payloadEsperado[$k] = $unicos[$k] }
    $invA = $mensajes.Count - $validos.Count
    $totInvA += $invA; $totDupA += $validos.Count - $unicos.Count
    Write-Host ("  {0}: {1} mensajes, {2} válidos ({3} idEvento únicos), {4} inválidos o ilegibles" -f $t, $mensajes.Count, $validos.Count, $unicos.Count, $invA)
    if ($notificables -contains $t) {
        $validosN = @($mensajes | Where-Object { ValidoParaNotificacion $_ $topicos[$t] })
        $unicosN = @{}; foreach ($m in $validosN) { $unicosN["$($m.Json.idEvento)".ToLower()] = $true }
        foreach ($k in $unicosN.Keys) { $idsNotif[$k] = $true }
        $totInvN += $mensajes.Count - $validosN.Count; $totDupN += $validosN.Count - $unicosN.Count
        Write-Host ("    notificaciones: {0} válidos ({1} únicos), {2} inválidos" -f $validosN.Count, $unicosN.Count, ($mensajes.Count - $validosN.Count))
    }
}

Write-Host '== 3. auditoria_eventos: una fila por idEvento válido, con el JSON original'
foreach ($t in $topicos.Keys) {
    $n = [int](Psql "SELECT count(*) FROM auditoria_eventos WHERE topico = '$t';").Trim()
    Check "auditoria_eventos ($t) = $($esperado[$t].Count) filas" ($n -eq $esperado[$t].Count) "hay $n"
}
$filas = @(Psql "SELECT id_evento || chr(9) || payload FROM auditoria_eventos;")
$iguales = 0; $distintos = 0; $extra = 0
foreach ($f in $filas) {
    $p = "$f" -split "`t", 2
    if ($p.Count -lt 2) { continue }
    if (-not $payloadEsperado.ContainsKey($p[0])) { $extra++ } elseif ($payloadEsperado[$p[0]] -ceq $p[1]) { $iguales++ } else { $distintos++ }
}
Check "payload = texto JSON original del tópico en todas las filas ($iguales)" ($iguales -eq $payloadEsperado.Count -and $distintos -eq 0 -and $extra -eq 0) "distintos=$distintos, extra=$extra"
$procA = [int](Psql "SELECT count(*) FROM auditoria_eventos_procesados WHERE consumer_group = 'auditoria-group';").Trim()
Check "auditoria_eventos_procesados = $($payloadEsperado.Count)" ($procA -eq $payloadEsperado.Count) "hay $procA"

Write-Host '== 4. notificaciones: una por EVENTO_CONFIRMADO o PAGO_REGISTRADO válido'
$n = [int](Psql "SELECT count(*) FROM notificaciones;").Trim()
Check "notificaciones = $($idsNotif.Count)" ($n -eq $idsNotif.Count) "hay $n"
$noGeneradas = [int](Psql "SELECT count(*) FROM notificaciones WHERE estado <> 'GENERADA' OR destinatario NOT LIKE 'cliente:%';").Trim()
Check 'Todas GENERADA con destinatario cliente:<clienteId>' ($noGeneradas -eq 0) "otras=$noGeneradas"
$procN = [int](Psql "SELECT count(*) FROM notificaciones_eventos_procesados WHERE consumer_group = 'notificaciones-group';").Trim()
Check "notificaciones_eventos_procesados = $($idsNotif.Count)" ($procN -eq $idsNotif.Count) "hay $procN"

Write-Host '== 5. Logs: duplicados ignorados, inválidos al DLT una vez, sin reintentos'
$logA = Compose 'logs auditoria-service' | Out-String
$logN = Compose 'logs notificaciones-service' | Out-String
function Contar([string]$Log, [string]$Grupo, [string]$Resultado) { @([regex]::Matches($Log, "Consumo Kafka\. grupo=$Grupo, .*resultado=$Resultado")).Count }
Check "auditoria: PROCESADO = $($payloadEsperado.Count)" ((Contar $logA 'auditoria-group' 'PROCESADO') -eq $payloadEsperado.Count) "$(Contar $logA 'auditoria-group' 'PROCESADO')"
Check "auditoria: DUPLICADO_IGNORADO = $totDupA" ((Contar $logA 'auditoria-group' 'DUPLICADO_IGNORADO') -eq $totDupA) "$(Contar $logA 'auditoria-group' 'DUPLICADO_IGNORADO')"
Check "auditoria: INVALIDO_A_DLT = $totInvA" ((Contar $logA 'auditoria-group' 'INVALIDO_A_DLT') -eq $totInvA) "$(Contar $logA 'auditoria-group' 'INVALIDO_A_DLT')"
Check "notificaciones: PROCESADO = $($idsNotif.Count)" ((Contar $logN 'notificaciones-group' 'PROCESADO') -eq $idsNotif.Count) "$(Contar $logN 'notificaciones-group' 'PROCESADO')"
Check "notificaciones: DUPLICADO_IGNORADO = $totDupN" ((Contar $logN 'notificaciones-group' 'DUPLICADO_IGNORADO') -eq $totDupN) "$(Contar $logN 'notificaciones-group' 'DUPLICADO_IGNORADO')"
Check "notificaciones: INVALIDO_A_DLT = $totInvN" ((Contar $logN 'notificaciones-group' 'INVALIDO_A_DLT') -eq $totInvN) "$(Contar $logN 'notificaciones-group' 'INVALIDO_A_DLT')"
Check 'Sin REINTENTO ni REINTENTOS_AGOTADOS_A_DLT en ninguno de los dos' (($logA + $logN) -notmatch 'resultado=REINTENTO') ''
Check 'Sin ERROR distinto de INVALIDO_A_DLT en ninguno de los dos' (@(($logA + $logN) -split "`n" | Where-Object { $_ -cmatch ' ERROR \d+ ---' -and $_ -notmatch 'resultado=INVALIDO_A_DLT' }).Count -eq 0) ''

Write-Host '== 6. DLT: los inválidos antiguos entran una vez por grupo nuevo; lo que ya había no se reprocesa'
$fotoDespues = FotoDlt
$sumaA = 0; $sumaN = 0
foreach ($d in $dlts) {
    $a = $fotoAntes.$d; $b = $fotoDespues[$d]
    foreach ($g in 'logistica-group', 'contratos-group') {
        Check "$d ($g): sigue con $($a.$g) mensajes (no se reprocesaron)" ($b[$g] -eq $a.$g) "ahora $($b[$g])"
    }
    # Deltas: si los grupos nuevos ya se habían desplegado antes (foto con mensajes suyos), solo cuenta lo nuevo.
    $sumaA += $b['auditoria-group'] - $a.'auditoria-group'; $sumaN += $b['notificaciones-group'] - $a.'notificaciones-group'
    Check "$d total = antes + los de los grupos nuevos" ($b.total -eq ($a.total + $b['auditoria-group'] + $b['notificaciones-group'] - $a.'auditoria-group' - $a.'notificaciones-group')) "antes $($a.total), ahora $($b.total)"
}
Check "DLT: +$totInvA con kafka_dlt-original-consumer-group=auditoria-group (un intento por inválido)" ($sumaA -eq $totInvA) "$sumaA"
Check "DLT: +$totInvN con kafka_dlt-original-consumer-group=notificaciones-group" ($sumaN -eq $totInvN) "$sumaN"
$suscritosDlt = @(Compose 'exec -T kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups' |
        Where-Object { "$_" -match '^(logistica|contratos|auditoria|notificaciones)-group\s+\S+\.DLT\s' })
Check 'Ningún consumer group de la aplicación lee un tópico *.DLT' ($suscritosDlt.Count -eq 0) ($suscritosDlt -join '; ')

Resumen 'Verificación 5C (historial)'
