# Verificación en Docker de la Fase 11E: recordatorios EVENTO_PROXIMO por Kafka y el arreglo del Kafka vacío.
#  1. Kafka: creación automática de tópicos desactivada en el broker; los 4 tópicos principales (eventos.proximos incluido)
#     y sus DLT con 3 particiones.
#  2. Un evento "Prueba …" RESERVADO en un fin de semana libre de los próximos 7 días (flujo completo por la API). Si no hay
#     ninguno libre, se usa el primer evento RESERVADO que ya exista en esa ventana (si ya tenía su recordatorio, se espera
#     YA_ENVIADO y que no se genere nada nuevo).
#  3. El asistente no ejecuta los recordatorios: 403 directo en Eventos y FORBIDDEN en el gateway, sin escribir nada.
#  4. El administrador sí (REST): el evento recibe el recordatorio del umbral más pequeño que aplica (ENVIADO), con su fila en
#     eventos_recordatorios_enviados y el outbox PUBLICADO con el idEvento determinista; Notificaciones genera "Faltan N
#     días…" / "Falta 1 día…" y Auditoría lo registra en eventos.proximos.
#  5. Segunda ejecución (GraphQL): YA_ENVIADO, sin filas, notificaciones ni mensajes nuevos.
# Al final cancela el evento de prueba. OJO: ejecutar los recordatorios también los envía a los eventos de demostración que
# estén dentro de los próximos 7 días; por eso este script se ejecuta ANTES de cargar-demo.ps1, no después.
# Requiere el entorno levantado y VERIFICACION_ASISTENTE_PASSWORD en el .env. Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
$U = $script:Url
$SIN_PERMISO = 'No tienes permiso para realizar esta operación.'
$inicio = Get-Date

function Como([string]$Token, [string]$Metodo, [string]$Ruta, $Cuerpo = $null) {
    Api $Metodo $Ruta $Cuerpo -SinToken -Cabeceras @{ Authorization = "Bearer $Token" }
}
function GqlComo([string]$Token, [string]$Documento) {
    Api POST $U.gateway @{ query = $Documento; variables = @{} } -SinToken -Cabeceras @{ Authorization = "Bearer $Token" }
}
function Contar([string]$Sql) { [int]("$(Psql $Sql)".Trim()) }
# (la coma evita que PowerShell desenvuelva un arreglo de un solo elemento al devolverlo: .Count de un objeto suelto es $null en 5.1)
function Recordatorios([string]$EventoId) {
    , @((Api GET "$($U.notificaciones)/notificaciones?eventoId=$EventoId&size=100").Body.contenido | Where-Object { $_ -and $_.tipo -eq 'EVENTO_PROXIMO' })
}
function AuditoriaProximo([string]$EventoId) {
    , @((Api GET "$($U.auditoria)/auditoria?eventoId=$EventoId&tipo=EVENTO_PROXIMO&size=100").Body.contenido | Where-Object { $_ })
}
$EJECUTAR_GQL = 'mutation { ejecutarRecordatorios { revisados enviados yaEnviados recordatorios { eventoId diasRestantes umbralDias resultado } } }'

Write-Host '== 1. Kafka: sin creación automática y todos los tópicos con 3 particiones'
$auto = "$(Compose 'exec -T kafka printenv KAFKA_AUTO_CREATE_TOPICS_ENABLE')".Trim()
Check 'Broker con KAFKA_AUTO_CREATE_TOPICS_ENABLE=false' ($auto -eq 'false') "valor=$auto"
foreach ($t in 'eventos.confirmados', 'pagos.registrados', 'servicios-adicionales.agregados', 'eventos.proximos', 'eventos.proximos.DLT') {
    $desc = Compose "exec -T kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic $t" | Out-String
    $particiones = if ($desc -match 'PartitionCount:\s*(\d+)') { [int]$Matches[1] } else { 0 }
    Check "Tópico $t con 3 particiones" ($particiones -eq 3) "particiones=$particiones"
}

Write-Host '== 2. Tokens y evento de prueba RESERVADO en los próximos 7 días'
$null = Login
$admin = $script:Token
if (-not $script:PasswordAsistente) { throw 'Falta VERIFICACION_ASISTENTE_PASSWORD en el .env (rotar-password.ps1 -Usuario asistente).' }
$asis = (Api POST "$($U.auth)/auth/login" (ConvertTo-Json @{ correo = $script:CorreoAsistente; password = $script:PasswordAsistente } -Compress) -SinToken).Body.token
Check 'Login del administrador y del asistente' ($admin -and $asis)
$desde = (Get-Date).AddDays(1).ToString('yyyy-MM-dd'); $hasta = (Get-Date).AddDays(7).ToString('yyyy-MM-dd')
$fecha = @((Api GET "$($U.eventos)/fechas/disponibles?desde=$desde&hasta=$hasta").Body | Where-Object { $_ } | ForEach-Object { [string]$_.fecha } | Sort-Object) | Select-Object -First 1
if ($fecha) {
    $flujo = FlujoFase3 $fecha
    $id = $flujo.EventoId
    $nombre = $script:NombreEventoFlujo
} else {
    # Sin fin de semana libre: se usa el primer evento RESERVADO que ya hay en la ventana (no se crea ni se cambia nada suyo;
    # si ya tenía su recordatorio, la ejecución debe dar YA_ENVIADO y no generar nada nuevo).
    $existente = @((Api GET "$($U.eventos)/eventos?estado=RESERVADO&desde=$desde&hasta=$hasta&size=20&sort=fecha,asc").Body.contenido | Where-Object { $_ })[0]
    if (-not $existente) { throw "No hay fin de semana libre ni evento RESERVADO entre $desde y ${hasta}: no se puede probar el recordatorio." }
    Write-Host "  (sin fin de semana libre: se usa el evento RESERVADO existente del $($existente.fecha))" -ForegroundColor Yellow
    $id = $existente.id; $fecha = [string]$existente.fecha; $nombre = $existente.nombreEvento
}
$dias = ([datetime]$fecha - (Get-Date).Date).Days
$umbral = if ($dias -le 1) { 1 } else { 7 }
$previo = Contar "SELECT count(*) FROM eventos_recordatorios_enviados WHERE evento_id = '$id' AND umbral_dias = $umbral;"
$esperado = if ($previo -eq 0) { 'ENVIADO' } else { 'YA_ENVIADO' }
Check "Evento RESERVADO el $fecha (faltan $dias días → umbral $umbral; se espera $esperado)" ($id -and $dias -ge 1 -and $dias -le 7)
Check 'Antes de ejecutar: sin recordatorio de ese umbral o con exactamente uno' ($previo -le 1 -and (Recordatorios $id).Count -eq $previo)

Write-Host '== 3. El asistente no puede ejecutar los recordatorios'
$r403 = Como $asis POST "$($U.eventos)/recordatorios/ejecutar"
Check 'Directo en Eventos: 403 con ErrorResponse' ($r403.Status -eq 403 -and $r403.Body.message -eq $SIN_PERMISO -and $r403.Body.path -eq '/api/v1/recordatorios/ejecutar') "status=$($r403.Status)"
$rGql = GqlComo $asis $EJECUTAR_GQL
$e = ErrorGraphQL $rGql
Check 'Por el gateway: FORBIDDEN (403, servicio gateway) y data null' ($e -and $e.extensions.classification -eq 'FORBIDDEN' -and $e.extensions.status -eq 403 -and $e.extensions.servicio -eq 'gateway' -and $e.message -eq $SIN_PERMISO -and $null -eq $rGql.Body.data) "error=$($e.message)"
Check 'Sin token: 401' ((Api POST "$($U.eventos)/recordatorios/ejecutar" -SinToken).Status -eq 401)
Check 'Nada escrito' ((Contar "SELECT count(*) FROM eventos_recordatorios_enviados WHERE evento_id = '$id' AND umbral_dias = $umbral;") -eq $previo)

Write-Host '== 4. El administrador ejecuta los recordatorios (REST)'
$r1 = Como $admin POST "$($U.eventos)/recordatorios/ejecutar"
$mio = @($r1.Body.recordatorios | Where-Object { $_.eventoId -eq $id })
Check 'HTTP 200 con el resultado' ($r1.Status -eq 200 -and @($r1.Body.umbralesDias).Count -ge 1 -and ($r1.Body.enviados + $r1.Body.yaEnviados) -eq $r1.Body.revisados) "status=$($r1.Status)"
Check "El evento: $esperado, diasRestantes $dias, umbral $umbral" ($mio.Count -eq 1 -and $mio[0].resultado -eq $esperado -and $mio[0].diasRestantes -eq $dias -and $mio[0].umbralDias -eq $umbral) "$(ConvertTo-Json $mio -Compress)"
Check 'Una fila en eventos_recordatorios_enviados' ((Contar "SELECT count(*) FROM eventos_recordatorios_enviados WHERE evento_id = '$id' AND umbral_dias = $umbral;") -eq 1)
$fila = EsperarEstadoOutbox 'eventos_mensajes_salientes' $id 'EVENTO_PROXIMO' 'PUBLICADO' 60
$idEsperado = "$(Psql "SELECT id_evento FROM eventos_recordatorios_enviados WHERE evento_id = '$id';")".Trim()
Check 'Outbox: EVENTO_PROXIMO PUBLICADO con el mismo idEvento de la fila del recordatorio' ($fila -and $fila.Estado -eq 'PUBLICADO' -and $fila.IdEvento -eq $idEsperado) "estado=$($fila.Estado)"
$fin = (Get-Date).AddSeconds(90)
do { $notif = Recordatorios $id; $aud = AuditoriaProximo $id; if ($notif.Count -ge 1 -and $aud.Count -ge 1) { break }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $fin)
# El asunto lleva los días que faltaban cuando se envió (si ya estaba enviado, pudo ser otro día)
$diasEnvio = [int]"$(Psql "SELECT dias_restantes FROM eventos_recordatorios_enviados WHERE evento_id = '$id' AND umbral_dias = $umbral;")".Trim()
$faltanEnvio = if ($diasEnvio -eq 1) { 'Falta 1 día' } else { "Faltan $diasEnvio días" }
Check "Notificaciones: una notificación '$faltanEnvio para su evento: …' GENERADA" ($notif.Count -eq 1 -and $notif[0].asunto -eq "$faltanEnvio para su evento: $nombre" -and $notif[0].estado -eq 'GENERADA' -and $notif[0].idEvento -eq $idEsperado) "asunto=$($notif[0].asunto)"
Check 'Notificaciones: el mensaje dice cuántos días faltan' ($notif.Count -eq 1 -and $notif[0].mensaje -like "Hola. $faltanEnvio para su evento «$nombre»*")
Check 'Auditoría: un mensaje EVENTO_PROXIMO del tópico eventos.proximos' ($aud.Count -eq 1 -and $aud[0].topico -eq 'eventos.proximos' -and $aud[0].servicioOrigen -eq 'eventos-service' -and $aud[0].idEvento -eq $idEsperado) "n=$($aud.Count)"
$logEv = LogsDesde 'eventos-service' $inicio
Check "Log de Eventos: Recordatorio EVENTO_PROXIMO. origen=MANUAL … resultado=$esperado" ($logEv -match "Recordatorio EVENTO_PROXIMO\. origen=MANUAL, eventoId=$id, .*resultado=$esperado")
if ($esperado -eq 'ENVIADO') {
    foreach ($s in 'notificaciones-service', 'auditoria-service') {
        $patron = "idEvento=$idEsperado, tipo=EVENTO_PROXIMO, eventoId=$id, resultado=PROCESADO"
        $fin = (Get-Date).AddSeconds(30); do { $ok = (LogsDesde $s $inicio) -match $patron; if ($ok) { break }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $fin)
        Check "Log de $($s): PROCESADO del EVENTO_PROXIMO" $ok
    }
}

Write-Host '== 5. Segunda ejecución (GraphQL): no se repite'
$r2 = GqlComo $admin $EJECUTAR_GQL
$mio2 = @($r2.Body.data.ejecutarRecordatorios.recordatorios | Where-Object { $_.eventoId -eq $id })
Check 'GraphQL del administrador sin errores; el evento sale YA_ENVIADO' (-not $r2.Body.errors -and $mio2.Count -eq 1 -and $mio2[0].resultado -eq 'YA_ENVIADO') "$(ConvertTo-Json $mio2 -Compress)"
Start-Sleep -Seconds 8
Check 'Sigue una sola fila, una sola notificación y un solo mensaje de auditoría' ((Contar "SELECT count(*) FROM eventos_recordatorios_enviados WHERE evento_id = '$id';") -eq 1 -and (Recordatorios $id).Count -eq 1 -and (AuditoriaProximo $id).Count -eq 1 -and (Contar "SELECT count(*) FROM eventos_mensajes_salientes WHERE clave = '$id' AND tipo = 'EVENTO_PROXIMO';") -eq 1)
Check 'Log de Eventos: la segunda vez resultado=YA_ENVIADO' ((LogsDesde 'eventos-service' $inicio) -match "Recordatorio EVENTO_PROXIMO\. origen=MANUAL, eventoId=$id, .*resultado=YA_ENVIADO")

Resumen 'Verificación 11E'
if ($script:Fallos -gt 0) { exit 1 }
