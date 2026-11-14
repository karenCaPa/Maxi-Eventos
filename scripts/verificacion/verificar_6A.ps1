# Verificación en Docker de la subfase 6A: gateway GraphQL (módulo, schema y queries).
# Los datos se preparan por REST (las mutations son de la 6B); todas las consultas van por GraphQL (http://localhost:8090/graphql).
# Demuestra con el log del gateway (REST -> <servicio> ...) que una query solo llama a los servicios de los campos pedidos.
# Detiene y arranca finanzas-service (503) y reinicia el gateway con otro perfil (GraphiQL apagado). Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

function Llamadas([string]$Log) { , @([regex]::Matches($Log, 'REST -> (\w+) (GET|POST|PUT|PATCH|DELETE) (\S+) (\d{3})') | ForEach-Object { $_.Groups[1].Value }) }
function Contar([string[]]$Lista, [string]$Valor) { @($Lista | Where-Object { $_ -eq $Valor }).Count }
function Clasificacion($ErrorGql) { if ($ErrorGql) { $ErrorGql.extensions.classification } }

CheckStatus 'Login (REST, para el token)' (Login) 200

Write-Host '== 0. Gateway: health, GraphiQL en el perfil dev'
CheckStatus 'gateway /actuator/health' (Api GET 'http://localhost:8090/actuator/health' -SinToken) 200
$gi = Invoke-WebRequest -UseBasicParsing 'http://localhost:8090/graphiql' -MaximumRedirection 5
Check 'GraphiQL disponible con GATEWAY_SPRING_PROFILES=dev (/graphiql -> 200 con la página de GraphiQL)' ($gi.StatusCode -eq 200 -and $gi.Content -match 'graphiql') ''

Write-Host '== 1. Datos de prueba por REST: evento confirmado con lugar, proveedor y adicional'
$f = FlujoFase3 (FechasLibres)[0]
$eid = $f.EventoId
$s = Sufijo
$lugar = Api POST "$($Url.lugares)/lugares" @{ nombre = "Prueba Salón Los Ángeles $s"; direccion = 'Vía a Rivera km 3, Neiva'; tipo = 'SALON'; capacidad = 200 }
CheckStatus 'Crear lugar' $lugar 201
CheckStatus 'Asignar lugar al evento' (Api PATCH "$($Url.eventos)/eventos/$eid/lugar" @{ lugarId = $lugar.Body.id }) 200
$prov = Api POST "$($Url.proveedores)/proveedores" @{ nombre = "Prueba Sonido Huila $s"; categoria = 'Música'; contacto = '3101234567' }
CheckStatus 'Crear proveedor' $prov 201
CheckStatus 'Asignar proveedor' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eid; proveedorId = $prov.Body.id; servicioAsignado = 'Sonido y luces'; costoAcordado = 900000 }) 201
CheckStatus 'Agregar adicional decoración premium' (Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'decoracion-premium'; cantidad = 1 }) 201
Start-Sleep -Seconds 6   # consumo asíncrono de Kafka (notificaciones y auditoría)

Write-Host '== 2. La ficha completa del evento en UNA query'
$ficha = @'
query Ficha($id: ID!) {
  evento(id: $id) {
    id nombreEvento fecha tipoEvento numInvitados zona estado
    cliente { id nombreCompleto telefono correo }
    cotizacion { valorBase totalSinAdicionales total depositoInicialRequerido moneda }
    contrato { estado fechaFirma clienteNombre }
    pagos { totalElementos contenido { monto fecha metodoPago } }
    balance { totalCotizado montoAbonado saldoPendiente adicionales }
    lugar { nombre direccion capacidad estado }
    proveedores { totalElementos contenido { servicio categoria costoAcordado estado proveedor { nombre } } }
    serviciosAdicionales { codigo nombre cantidad subtotal }
    logistica { meseros personalCocina vehiculos compras(size: 3) { totalElementos contenido { nombreItem comprado } } }
    notificaciones { totalElementos contenido { tipo asunto estado } }
    auditoria { totalElementos contenido { tipo topico payload } }
  }
}
'@
$desde = Get-Date
$r = GraphQL $ficha @{ id = $eid }
$ev = $r.Body.data.evento
Check 'HTTP 200 y sin errores GraphQL' ($r.Status -eq 200 -and -not $r.Body.errors) "$($r.Texto)"
Check 'Evento: nombre con tildes, QUINCE_ANOS, NEIVA, RESERVADO, 105 invitados' ($ev.nombreEvento -ceq $NombreEventoFlujo -and $ev.tipoEvento -eq 'QUINCE_ANOS' -and $ev.zona -eq 'NEIVA' -and $ev.estado -eq 'RESERVADO' -and $ev.numInvitados -eq 105) ''
Check "cliente (Clientes): $NombreClienteFlujo" ($ev.cliente.nombreCompleto -ceq $NombreClienteFlujo -and $ev.cliente.id -eq $f.ClienteId) ''
$cot = $f.Cotizacion
Check 'cotizacion (Finanzas): totalSinAdicionales = total de la cotización; total = + 250.000 del adicional; depositoInicialRequerido' ([decimal]$ev.cotizacion.totalSinAdicionales -eq [decimal]$cot.total -and [decimal]$ev.cotizacion.total -eq ([decimal]$cot.total + 250000) -and [decimal]$ev.cotizacion.depositoInicialRequerido -eq [decimal]$cot.montoReservaRequerido) "$($ev.cotizacion | ConvertTo-Json -Compress)"
Check 'contrato (Contratos): FIRMADO con fechaFirma' ($ev.contrato.estado -eq 'FIRMADO' -and $ev.contrato.fechaFirma) ''
Check 'pagos (Finanzas): 2 abonos' ($ev.pagos.totalElementos -eq 2 -and @($ev.pagos.contenido).Count -eq 2) ''
Check 'balance (Finanzas): montoAbonado = depósito, totalCotizado = total con adicionales, adicionales = 250.000' ([decimal]$ev.balance.montoAbonado -eq [decimal]$cot.montoReservaRequerido -and [decimal]$ev.balance.totalCotizado -eq [decimal]$ev.cotizacion.total -and [decimal]$ev.balance.adicionales -eq 250000) "$($ev.balance | ConvertTo-Json -Compress)"
Check 'lugar (Lugares): Salón Los Ángeles, capacidad 200, ACTIVO' ($ev.lugar.nombre -ceq "Prueba Salón Los Ángeles $s" -and $ev.lugar.capacidad -eq 200 -and $ev.lugar.estado -eq 'ACTIVO') ''
$pa = @($ev.proveedores.contenido)[0]
Check 'proveedores (Proveedores): servicio, categoría Música, PENDIENTE y datos del proveedor' ($ev.proveedores.totalElementos -eq 1 -and $pa.servicio -eq 'Sonido y luces' -and $pa.categoria -ceq 'Música' -and $pa.estado -eq 'PENDIENTE' -and $pa.proveedor.nombre -ceq "Prueba Sonido Huila $s") ''
Check 'serviciosAdicionales (Finanzas): Decoración premium' (@($ev.serviciosAdicionales).Count -eq 1 -and $ev.serviciosAdicionales[0].nombre -ceq 'Decoración premium') ''
Check 'logistica (Logística): meseros 7, personalCocina 4, vehiculos 2 y compras' ($ev.logistica.meseros -eq 7 -and $ev.logistica.personalCocina -eq 4 -and $ev.logistica.vehiculos -eq 2 -and $ev.logistica.compras.totalElementos -ge 1 -and @($ev.logistica.compras.contenido).Count -le 3) "$($ev.logistica | ConvertTo-Json -Compress -Depth 4)"
Check 'notificaciones (Notificaciones): 3 GENERADA (2 abonos + confirmación)' ($ev.notificaciones.totalElementos -eq 3 -and @($ev.notificaciones.contenido | Where-Object estado -ne 'GENERADA').Count -eq 0) "total=$($ev.notificaciones.totalElementos)"
$audTipos = @($ev.auditoria.contenido | ForEach-Object tipo)
Check 'auditoria (Auditoría): 4 mensajes (2 pagos, confirmación, adicional) con el payload JSON' ($ev.auditoria.totalElementos -eq 4 -and (Contar $audTipos 'PAGO_REGISTRADO') -eq 2 -and ($ev.auditoria.contenido[0].payload | ConvertFrom-Json).eventoId -eq $eid) "total=$($ev.auditoria.totalElementos)"
Start-Sleep -Seconds 1
$llamadas = Llamadas (LogsDesde 'gateway' $desde)
Check "Log del gateway: la ficha consultó a los 9 servicios de sus campos ($(($llamadas | Sort-Object -Unique) -join ', '))" ((@($llamadas | Sort-Object -Unique) -join ',') -eq 'auditoria,clientes,contratos,eventos,finanzas,logistica,lugares,notificaciones,proveedores') ''
Check 'Proveedores: 2 llamadas (asignaciones + 1 proveedor para proveedor y categoria, por el DataLoader)' ((Contar $llamadas 'proveedores') -eq 2) "proveedores=$(Contar $llamadas 'proveedores')"

Write-Host '== 3. Query parcial: SOLO se llama a los servicios de los campos pedidos'
Start-Sleep -Milliseconds 1100
$desde = Get-Date
$r = GraphQL 'query($id: ID!) { evento(id: $id) { nombreEvento cliente { nombreCompleto } } }' @{ id = $eid }
Start-Sleep -Seconds 1
$llamadas = Llamadas (LogsDesde 'gateway' $desde)
Check 'Query { nombreEvento cliente } -> datos correctos' ($r.Body.data.evento.cliente.nombreCompleto -ceq $NombreClienteFlujo -and -not $r.Body.errors) ''
Check "Log: exactamente 1 llamada a eventos y 1 a clientes ($($llamadas -join ', '))" ($llamadas.Count -eq 2 -and (Contar $llamadas 'eventos') -eq 1 -and (Contar $llamadas 'clientes') -eq 1) ''
Check 'Log: ninguna llamada a finanzas ni a contratos' ((Contar $llamadas 'finanzas') -eq 0 -and (Contar $llamadas 'contratos') -eq 0) ''
Start-Sleep -Milliseconds 1100
$desde = Get-Date
$r = GraphQL 'query($id: ID!) { evento(id: $id) { cotizacion { valorBase totalSinAdicionales } } }' @{ id = $eid }
Start-Sleep -Seconds 1
$llamadas = Llamadas (LogsDesde 'gateway' $desde)
Check "Query { cotizacion { valorBase totalSinAdicionales } } -> solo eventos y finanzas, 1 llamada a finanzas (sin el balance) ($($llamadas -join ', '))" ($llamadas.Count -eq 2 -and (Contar $llamadas 'finanzas') -eq 1 -and (Contar $llamadas 'clientes') -eq 0) ''

Write-Host '== 4. Listado: @BatchMapping -> una llamada por cliente DISTINTO'
Start-Sleep -Milliseconds 1100
$desde = Get-Date
$r = GraphQL '{ eventos(estado: RESERVADO, size: 20, sort: "fechaCreacion,desc") { totalElementos contenido { id clienteId cliente { nombreCompleto } } } }'
Start-Sleep -Seconds 1
$llamadas = Llamadas (LogsDesde 'gateway' $desde)
$contenido = @($r.Body.data.eventos.contenido)
$distintos = @($contenido | ForEach-Object clienteId | Sort-Object -Unique).Count
Check "eventos(estado: RESERVADO, size: 20) -> $($contenido.Count) eventos de $distintos clientes distintos" ($contenido.Count -ge 1 -and -not $r.Body.errors) "$($r.Texto)"
Check "Log: 1 llamada a eventos y $distintos a clientes (no $($contenido.Count))" ((Contar $llamadas 'eventos') -eq 1 -and (Contar $llamadas 'clientes') -eq $distintos) "eventos=$(Contar $llamadas 'eventos'), clientes=$(Contar $llamadas 'clientes')"
$cMismo = CrearCliente 'Prueba Andrés Muñoz Peña'
CheckStatus 'Cliente para 3 eventos' $cMismo 201
$fechasLibres = FechasLibres
foreach ($i in 1..3) {
    CheckStatus "Evento PENDIENTE $i del mismo cliente" (Api POST "$($Url.eventos)/eventos" @{ nombreEvento = "Prueba Boda Muñoz $i"; clienteId = $cMismo.Body.id; fecha = $fechasLibres[$i]; tipoEvento = 'BODA'; cantidadAdultos = 100; cantidadNinos = 5; zona = 'RURAL' }) 201
}
Start-Sleep -Milliseconds 1100
$desde = Get-Date
$r = GraphQL '{ eventos(estado: PENDIENTE, size: 3, sort: "fechaCreacion,desc") { contenido { nombreEvento cliente { nombreCompleto } } } }'
Start-Sleep -Seconds 1
$llamadas = Llamadas (LogsDesde 'gateway' $desde)
Check 'Los 3 eventos más recientes son del mismo cliente' ((@($r.Body.data.eventos.contenido | Where-Object { $_.cliente.nombreCompleto -ceq 'Prueba Andrés Muñoz Peña' }).Count) -eq 3) "$($r.Texto)"
Check "Log: 3 eventos del mismo cliente -> 1 sola llamada a clientes (@BatchMapping) ($($llamadas -join ', '))" ((Contar $llamadas 'eventos') -eq 1 -and (Contar $llamadas 'clientes') -eq 1) ''
$r = GraphQL '{ eventos(tipo: BODA, size: 5) { contenido { tipoEvento } } }'
Check 'Filtro tipo: BODA -> todos BODA' (@($r.Body.data.eventos.contenido | Where-Object tipoEvento -ne 'BODA').Count -eq 0) ''
# Fase 11: ventana de 60 días (antes 20). Con un solo evento por fin de semana, 20 días son apenas 3 fines de semana y
# bastaba con que dos estuvieran ocupados (demostración o eventos manuales) y el propio evento de este script tomara el
# tercero para que la lista saliera vacía.
$desdeF = (Get-Date).AddDays(1).ToString('yyyy-MM-dd'); $hastaF = (Get-Date).AddDays(60).ToString('yyyy-MM-dd')
$r = GraphQL 'query($d: Date, $h: Date) { fechasDisponibles(desde: $d, hasta: $h) { fecha diaSemana estado } }' @{ d = $desdeF; h = $hastaF }
$fechas = @($r.Body.data.fechasDisponibles)
Check 'fechasDisponibles: lista con diaSemana en español y estado DISPONIBLE' ($fechas.Count -ge 1 -and @($fechas | Where-Object estado -ne 'DISPONIBLE').Count -eq 0 -and $fechas[0].diaSemana -match '^(lunes|martes|miércoles|jueves|viernes|sábado|domingo)$') "$($r.Texto)"
$r = GraphQL '{ catalogoServiciosAdicionales { codigo nombre costoUnitario } }'
Check 'catalogoServiciosAdicionales: incluye decoracion-premium, hora-extra y transporte-adicional' ((@($r.Body.data.catalogoServiciosAdicionales | ForEach-Object codigo | Sort-Object) -join ',') -match 'decoracion-premium' -and ($r.Texto -match 'hora-extra') -and ($r.Texto -match 'transporte-adicional')) "$($r.Texto)"
$r = GraphQL 'query($id: ID!) { cliente(id: $id) { nombreCompleto correo } }' @{ id = $f.ClienteId }
Check 'cliente(id): datos del cliente' ($r.Body.data.cliente.nombreCompleto -ceq $NombreClienteFlujo) ''

Write-Host '== 5. Errores GraphQL legibles'
$inex = [guid]::NewGuid().ToString()
$r = GraphQL 'query($id: ID!) { evento(id: $id) { nombreEvento } }' @{ id = $inex }
$e = ErrorGraphQL $r
Check 'evento inexistente -> NOT_FOUND con el mensaje de Eventos, path evento, status 404' ((Clasificacion $e) -eq 'NOT_FOUND' -and $e.message -match $inex -and $e.path[0] -eq 'evento' -and $e.extensions.status -eq 404 -and $e.extensions.servicio -eq 'eventos') "$($r.Texto)"
Check 'Sin stack trace ni nombres de clases Java en la respuesta' ($r.Texto -notmatch 'Exception|\tat |com\.maxieventos') ''
$r = GraphQL '{ eventos(size: 101) { totalElementos } }'
$e = ErrorGraphQL $r
Check 'eventos(size: 101) -> BAD_REQUEST con el mensaje de Eventos y extensions.errores[campo, mensaje]' ((Clasificacion $e) -eq 'BAD_REQUEST' -and @($e.extensions.errores).Count -ge 1 -and $e.extensions.errores[0].campo -and $e.extensions.errores[0].mensaje) "$($r.Texto)"
$r = GraphQL '{ cliente(id: "no-es-uuid") { nombreCompleto } }'
Check 'id con formato inválido -> BAD_REQUEST' ((Clasificacion (ErrorGraphQL $r)) -eq 'BAD_REQUEST') "$($r.Texto)"
$r = GraphQL 'query($id: ID!) { evento(id: $id) { nombreEvento } }' @{ id = $eid } -SinToken
$e = ErrorGraphQL $r
Check 'Sin token -> UNAUTHORIZED con un mensaje entendible (el servicio lo rechazó: el gateway solo reenvía el token)' ((Clasificacion $e) -eq 'UNAUTHORIZED' -and $e.message -match 'Inicia sesión con la mutation login') "$($r.Texto)"
$r = GraphQL '{ evento { nombreEvento } }'
Check 'Error de validación del schema (falta id) -> ValidationError de graphql-java' ((Clasificacion (ErrorGraphQL $r)) -eq 'ValidationError') "$($r.Texto)"

Write-Host '== 6. Servicio detenido (finanzas-service) -> SERVICE_UNAVAILABLE en su campo y el resto de la ficha'
Compose 'stop finanzas-service' | Out-Null
try {
    $r = GraphQL 'query($id: ID!) { evento(id: $id) { nombreEvento cliente { nombreCompleto } cotizacion { valorBase } contrato { estado } } }' @{ id = $eid }
    $e = ErrorGraphQL $r
    Check 'cotizacion -> SERVICE_UNAVAILABLE (status 503, servicio finanzas), path evento.cotizacion' ((Clasificacion $e) -eq 'SERVICE_UNAVAILABLE' -and $e.extensions.status -eq 503 -and $e.extensions.servicio -eq 'finanzas' -and ($e.path -join '.') -eq 'evento.cotizacion') "$($r.Texto)"
    Check "Mensaje: '$($e.message)'" ($e.message -ceq 'El servicio de finanzas no está disponible en este momento. Intenta de nuevo más tarde.') ''
    Check 'El resto de la ficha se entrega (cliente y contrato) y cotizacion es null' ($r.Body.data.evento.cliente.nombreCompleto -ceq $NombreClienteFlujo -and $r.Body.data.evento.contrato.estado -eq 'FIRMADO' -and $null -eq $r.Body.data.evento.cotizacion) ''
} finally {
    Compose 'start finanzas-service' | Out-Null
}
Check 'finanzas-service vuelve a healthy' ((EsperarHealthy 'finanzas-service') -eq 'healthy') ''

Write-Host '== 7. GraphiQL apagado sin el perfil dev'
$env:GATEWAY_SPRING_PROFILES = 'sin-graphiql'
try {
    Compose 'up -d gateway' | Out-Null
    Check 'gateway healthy con otro perfil' ((EsperarHealthy 'gateway') -eq 'healthy') ''
    $codigo = try { (Invoke-WebRequest -UseBasicParsing 'http://localhost:8090/graphiql').StatusCode } catch { [int]$_.Exception.Response.StatusCode }
    Check "/graphiql sin el perfil dev -> 404 (obtuvo $codigo)" ($codigo -eq 404) ''
    Check '/graphql sigue funcionando' ((GraphQL '{ catalogoServiciosAdicionales { codigo } }').Status -eq 200) ''
} finally {
    Remove-Item Env:\GATEWAY_SPRING_PROFILES -ErrorAction SilentlyContinue
    Compose 'up -d gateway' | Out-Null
}
Check 'gateway vuelve al perfil del .env (dev) y healthy' ((EsperarHealthy 'gateway') -eq 'healthy') ''
Write-Host "  Evento de esta verificación: $eid"

Resumen 'Verificación 6A'
