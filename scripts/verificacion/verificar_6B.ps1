# Verificación en Docker de la subfase 6B: el flujo completo del negocio 100 % por GraphQL (http://localhost:8090/graphql),
# sin ninguna llamada REST desde el script (el token sale de la mutation login). Errores BAD_REQUEST, CONFLICT, NOT_FOUND,
# UNAUTHORIZED y SERVICE_UNAVAILABLE (detiene y arranca contratos-service). La ficha final muestra lo generado por Kafka.
# Requiere el entorno levantado y, en el catálogo, al menos un lugar ACTIVO con capacidad >= 105 y un proveedor activo.
# Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

function Clasificacion($ErrorGql) { if ($ErrorGql) { $ErrorGql.extensions.classification } }
function Llamadas([string]$Log) { , @([regex]::Matches($Log, 'REST -> (\w+) (GET|POST|PUT|PATCH|DELETE) (\S+) (\d{3})') | ForEach-Object { "$($_.Groups[1].Value) $($_.Groups[2].Value) $($_.Groups[3].Value -replace '\?.*$', '')" }) }
function SinErrores($R) { $R.Status -eq 200 -and -not $R.Body.errors }

Write-Host '== 1. login (mutation): el token sale del gateway'
$r = GraphQL 'mutation($c: String!, $p: String!) { login(correo: $c, password: $p) { token tipoToken expiracionEnSegundos } }' @{ c = $script:CorreoOrganizadora; p = $script:PasswordOrganizadora } -SinToken
Check 'login -> token Bearer con expiración' ((SinErrores $r) -and $r.Body.data.login.tipoToken -eq 'Bearer' -and $r.Body.data.login.expiracionEnSegundos -gt 0 -and $r.Body.data.login.token) ''
$script:Token = $r.Body.data.login.token
$r = GraphQL 'mutation($c: String!) { login(correo: $c, password: "contraseña-incorrecta") { token } }' @{ c = $script:CorreoOrganizadora } -SinToken
$e = ErrorGraphQL $r
Check "login con contraseña incorrecta -> UNAUTHORIZED con el mensaje de Auth ('$($e.message)')" ((Clasificacion $e) -eq 'UNAUTHORIZED' -and $e.message -ceq 'Correo o contraseña incorrectos.') "$($r.Texto)"

Write-Host '== 2. crearCliente (y datos inválidos -> BAD_REQUEST con errores[] por campo)'
$s = Sufijo
$r = GraphQL 'mutation($i: ClienteInput!) { crearCliente(input: $i) { id nombreCompleto correo } }' @{ i = @{ nombreCompleto = 'Prueba Valeria Ñáñez Gutiérrez'; telefono = "301$((Get-Random -Minimum 1000000 -Maximum 9999999))"; correo = "valeria.$s@ejemplo.com"; direccion = 'Cra. 5 # 10-20, Neiva' } }
Check 'crearCliente -> cliente con tildes y ñ' ((SinErrores $r) -and $r.Body.data.crearCliente.nombreCompleto -ceq 'Prueba Valeria Ñáñez Gutiérrez') "$($r.Texto)"
$clienteId = $r.Body.data.crearCliente.id
$r = GraphQL 'mutation($i: ClienteInput!) { crearCliente(input: $i) { id } }' @{ i = @{ nombreCompleto = 'X'; telefono = '12'; correo = 'valeria@@ejemplo' } }
$e = ErrorGraphQL $r
$campos = @($e.extensions.errores | ForEach-Object campo | Sort-Object)
Check "crearCliente inválido -> BAD_REQUEST, status 400, servicio clientes, errores[] en campos: $($campos -join ', ')" ((Clasificacion $e) -eq 'BAD_REQUEST' -and $e.extensions.status -eq 400 -and $e.extensions.servicio -eq 'clientes' -and $campos -contains 'correo' -and $campos -contains 'telefono' -and $campos -contains 'nombreCompleto') "$($r.Texto)"
$errCorreo = @($e.extensions.errores | Where-Object campo -eq 'correo')[0]
Check "errores[campo=correo] con mensaje en español ('$($errCorreo.mensaje)')" ($errCorreo.mensaje -match 'correo') ''

Write-Host '== 3. fechasDisponibles -> crearEvento (PENDIENTE); la misma fecha otra vez -> CONFLICT'
$r = GraphQL 'query($d: Date, $h: Date) { fechasDisponibles(desde: $d, hasta: $h) { fecha diaSemana } }' @{ d = (Get-Date).AddDays(1).ToString('yyyy-MM-dd'); h = (Get-Date).AddDays(366).ToString('yyyy-MM-dd') }
# 10B3: solo viernes, sábado y domingo; se usa un sábado de cada fin de semana libre (Resumen cancela lo creado)
$noPermitidos = @($r.Body.data.fechasDisponibles | Where-Object { @('viernes', 'sábado', 'domingo') -notcontains $_.diaSemana })
$sabados = @($r.Body.data.fechasDisponibles | Where-Object diaSemana -eq 'sábado')
Check "fechasDisponibles -> $(@($r.Body.data.fechasDisponibles).Count) fechas, todas viernes, sábado o domingo ($($sabados.Count) sábados libres)" ((SinErrores $r) -and $noPermitidos.Count -eq 0 -and $sabados.Count -ge 2) "no permitidos: $($noPermitidos.fecha -join ', ')"
$martes = MartesFuturo
$r = GraphQL 'mutation($i: CrearEventoInput!) { crearEvento(input: $i) { id } }' @{ i = @{ nombreEvento = 'Prueba evento en martes'; clienteId = $clienteId; fecha = $martes; tipoEvento = 'BODA'; cantidadAdultos = 80; cantidadNinos = 0; zona = 'RURAL' } }
$e = ErrorGraphQL $r
$errFecha = @($e.extensions.errores | Where-Object campo -eq 'fecha')[0]
Check "crearEvento un martes ($martes) -> BAD_REQUEST con errores[campo=fecha] '$($errFecha.mensaje)'" ((Clasificacion $e) -eq 'BAD_REQUEST' -and $e.extensions.status -eq 400 -and $errFecha.mensaje -ceq 'Solo se pueden registrar eventos en viernes, sábado o domingo.') "$($r.Texto)"
$fecha = $sabados[0].fecha
$nombre = 'Prueba Quince años de Sofía Muñoz'
$evIn = @{ nombreEvento = $nombre; clienteId = $clienteId; fecha = $fecha; tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 90; cantidadNinos = 15; zona = 'NEIVA' }
$r = GraphQL 'mutation($i: CrearEventoInput!) { crearEvento(input: $i) { id estado fecha numInvitados cliente { nombreCompleto } } }' @{ i = $evIn }
$ev = $r.Body.data.crearEvento
Check "crearEvento ($fecha) -> PENDIENTE, 105 invitados y el cliente en la misma respuesta" ((SinErrores $r) -and $ev.estado -eq 'PENDIENTE' -and $ev.numInvitados -eq 105 -and $ev.cliente.nombreCompleto -ceq 'Prueba Valeria Ñáñez Gutiérrez') "$($r.Texto)"
$eid = $ev.id
$r = GraphQL 'mutation($i: CrearEventoInput!) { crearEvento(input: $i) { id } }' @{ i = $evIn }
$e = ErrorGraphQL $r
Check "crearEvento en la misma fecha -> CONFLICT ('$($e.message)')" ((Clasificacion $e) -eq 'CONFLICT' -and $e.extensions.status -eq 409 -and $e.message -match "La fecha $fecha no está disponible") "$($r.Texto)"

Write-Host '== 4. generarCotizacion, catalogoServiciosAdicionales, crearContrato'
$r = GraphQL 'mutation($id: ID!) { generarCotizacion(eventoId: $id) { valorBase totalSinAdicionales depositoInicialRequerido moneda } }' @{ id = $eid }
$cot = $r.Body.data.generarCotizacion
Check "generarCotizacion -> total $($cot.totalSinAdicionales) $($cot.moneda), depósito $($cot.depositoInicialRequerido)" ((SinErrores $r) -and [decimal]$cot.totalSinAdicionales -eq 8700000 -and [decimal]$cot.depositoInicialRequerido -eq 2610000) "$($r.Texto)"
$r = GraphQL '{ catalogoServiciosAdicionales { codigo nombre costoUnitario } }'
Check 'catalogoServiciosAdicionales -> incluye decoracion-premium' ((SinErrores $r) -and @($r.Body.data.catalogoServiciosAdicionales | Where-Object codigo -eq 'decoracion-premium').Count -eq 1) ''
$r = GraphQL 'mutation($id: ID!) { crearContrato(eventoId: $id) { estado fechaFirma valorTotal clienteNombre } }' @{ id = $eid }
Check 'crearContrato -> PENDIENTE, sin fechaFirma, valor 8.700.000 y la foto del cliente' ((SinErrores $r) -and $r.Body.data.crearContrato.estado -eq 'PENDIENTE' -and -not $r.Body.data.crearContrato.fechaFirma -and [decimal]$r.Body.data.crearContrato.valorTotal -eq 8700000 -and $r.Body.data.crearContrato.clienteNombre -ceq 'Prueba Valeria Ñáñez Gutiérrez') "$($r.Texto)"

Write-Host '== 5. confirmarEvento sin contrato firmado -> CONFLICT con el mensaje de Eventos'
$r = GraphQL 'mutation($id: ID!) { confirmarEvento(id: $id) { estado } }' @{ id = $eid }
$e = ErrorGraphQL $r
Check "confirmarEvento sin firmar -> CONFLICT: '$($e.message)'" ((Clasificacion $e) -eq 'CONFLICT' -and $e.extensions.servicio -eq 'eventos' -and $e.message -match 'El contrato del evento está en estado PENDIENTE' -and ($e.path -join '.') -eq 'confirmarEvento') "$($r.Texto)"
Check 'Sin stack trace ni clases Java en el error' ($r.Texto -notmatch 'Exception|\tat |com\.maxieventos') ''

Write-Host '== 6. firmarContrato; firmar otra vez -> CONFLICT'
$r = GraphQL 'mutation($id: ID!) { firmarContrato(eventoId: $id) { estado fechaFirma } }' @{ id = $eid }
Check 'firmarContrato -> FIRMADO con fechaFirma' ((SinErrores $r) -and $r.Body.data.firmarContrato.estado -eq 'FIRMADO' -and $r.Body.data.firmarContrato.fechaFirma) "$($r.Texto)"
$r = GraphQL 'mutation($id: ID!) { firmarContrato(eventoId: $id) { estado } }' @{ id = $eid }
$e = ErrorGraphQL $r
Check "firmarContrato otra vez -> CONFLICT ('$($e.message)')" ((Clasificacion $e) -eq 'CONFLICT' -and $e.extensions.servicio -eq 'contratos') "$($r.Texto)"

Write-Host '== 7. registrarPago (idempotente por la clave) y un monto inválido'
$r = GraphQL 'mutation($i: PagoInput!) { registrarPago(input: $i) { id } }' @{ i = @{ eventoId = $eid; clienteId = $clienteId; monto = 0; metodoPago = 'EFECTIVO' } }
$e = ErrorGraphQL $r
Check "registrarPago con monto 0 -> BAD_REQUEST con errores[campo=monto] ('$(@($e.extensions.errores)[0].mensaje)')" ((Clasificacion $e) -eq 'BAD_REQUEST' -and @($e.extensions.errores | Where-Object campo -eq 'monto').Count -eq 1) "$($r.Texto)"
$clave = "abono-6b-$s"
$pagoIn = @{ eventoId = $eid; clienteId = $clienteId; monto = [decimal]$cot.depositoInicialRequerido; metodoPago = 'TRANSFERENCIA'; claveIdempotencia = $clave }
Start-Sleep -Milliseconds 1100
$desde = Get-Date
$r = GraphQL 'mutation($i: PagoInput!) { registrarPago(input: $i) { id monto moneda metodoPago fecha claveIdempotencia } }' @{ i = $pagoIn }
$pago = $r.Body.data.registrarPago
Check "registrarPago del depósito ($($pago.monto) $($pago.moneda)) con claveIdempotencia" ((SinErrores $r) -and [decimal]$pago.monto -eq [decimal]$cot.depositoInicialRequerido -and $pago.moneda -eq 'COP' -and $pago.claveIdempotencia -eq $clave) "$($r.Texto)"
Start-Sleep -Seconds 1
$llamadas = Llamadas (LogsDesde 'gateway' $desde)
Check "La mutation hizo UNA sola llamada REST ($($llamadas -join '; '))" ($llamadas.Count -eq 1 -and $llamadas[0] -eq 'finanzas POST /api/v1/finanzas/pagos') ''
$r = GraphQL 'mutation($i: PagoInput!) { registrarPago(input: $i) { id } }' @{ i = $pagoIn }
Check 'registrarPago repetido con la misma clave -> el mismo pago (no se cobra dos veces)' ((SinErrores $r) -and $r.Body.data.registrarPago.id -eq $pago.id) "$($r.Texto)"

Write-Host '== 8. confirmarEvento -> RESERVADO'
$r = GraphQL 'mutation($id: ID!) { confirmarEvento(id: $id) { estado fecha } }' @{ id = $eid }
Check 'confirmarEvento -> RESERVADO' ((SinErrores $r) -and $r.Body.data.confirmarEvento.estado -eq 'RESERVADO') "$($r.Texto)"
$r = GraphQL 'mutation($id: ID!) { confirmarEvento(id: $id) { estado } }' @{ id = $eid }
Check "confirmarEvento otra vez -> CONFLICT ('$((ErrorGraphQL $r).message)')" ((Clasificacion (ErrorGraphQL $r)) -eq 'CONFLICT') ''

Write-Host '== 9. lugares -> asignarLugar; proveedores -> asignarProveedor; agregarServicioAdicional'
$r = GraphQL '{ lugares(estado: ACTIVO, size: 100) { totalElementos contenido { id nombre capacidad estado } } }'
$lugar = @($r.Body.data.lugares.contenido | Where-Object { $_.capacidad -ge 105 } | Select-Object -First 1)[0]
Check "lugares(estado: ACTIVO) -> $($r.Body.data.lugares.totalElementos) lugares; elegido: $($lugar.nombre) (capacidad $($lugar.capacidad))" ((SinErrores $r) -and $lugar) ''
$r = GraphQL 'mutation($e: ID!, $l: ID!) { asignarLugar(eventoId: $e, lugarId: $l) { estado lugar { nombre capacidad } } }' @{ e = $eid; l = $lugar.id }
Check 'asignarLugar -> el evento con su lugar (Lugares) en la respuesta' ((SinErrores $r) -and $r.Body.data.asignarLugar.lugar.nombre -ceq $lugar.nombre) "$($r.Texto)"
$r = GraphQL '{ proveedores(activo: true, size: 100) { totalElementos contenido { id nombre categoria activo } } }'
$prov = @($r.Body.data.proveedores.contenido)[0]
Check "proveedores(activo: true) -> $($r.Body.data.proveedores.totalElementos) proveedores; elegido: $($prov.nombre) ($($prov.categoria))" ((SinErrores $r) -and $prov -and @($r.Body.data.proveedores.contenido | Where-Object { -not $_.activo }).Count -eq 0) ''
$r = GraphQL 'mutation($i: AsignarProveedorInput!) { asignarProveedor(input: $i) { servicio categoria costoAcordado estado proveedor { nombre } } }' @{ i = @{ eventoId = $eid; proveedorId = $prov.id; servicio = 'Sonido y luces'; costoAcordado = 900000 } }
$asig = $r.Body.data.asignarProveedor
Check 'asignarProveedor -> PENDIENTE, servicio, categoría y proveedor' ((SinErrores $r) -and $asig.estado -eq 'PENDIENTE' -and $asig.servicio -eq 'Sonido y luces' -and $asig.categoria -ceq $prov.categoria -and $asig.proveedor.nombre -ceq $prov.nombre) "$($r.Texto)"
$r = GraphQL 'mutation($i: AsignarProveedorInput!) { asignarProveedor(input: $i) { id } }' @{ i = @{ eventoId = $eid; proveedorId = $prov.id; servicio = 'Sonido y luces'; costoAcordado = 900000 } }
Check "asignarProveedor duplicado -> CONFLICT ('$((ErrorGraphQL $r).message)')" ((Clasificacion (ErrorGraphQL $r)) -eq 'CONFLICT' -and (ErrorGraphQL $r).extensions.servicio -eq 'proveedores') "$($r.Texto)"
$r = GraphQL 'mutation($id: ID!) { agregarServicioAdicional(eventoId: $id, codigo: "decoracion-premium", cantidad: 1) { codigo nombre cantidad valorUnitario subtotal } }' @{ id = $eid }
Check 'agregarServicioAdicional -> Decoración premium x1 = 250.000' ((SinErrores $r) -and $r.Body.data.agregarServicioAdicional.nombre -ceq 'Decoración premium' -and [decimal]$r.Body.data.agregarServicioAdicional.subtotal -eq 250000) "$($r.Texto)"
$r = GraphQL 'mutation($id: ID!) { agregarServicioAdicional(eventoId: $id, codigo: "no-existe", cantidad: 1) { id } }' @{ id = $eid }
Check "agregarServicioAdicional con un código que no existe -> error legible ($((Clasificacion (ErrorGraphQL $r))): '$((ErrorGraphQL $r).message)')" ((Clasificacion (ErrorGraphQL $r)) -in @('BAD_REQUEST', 'NOT_FOUND', 'CONFLICT') -and (ErrorGraphQL $r).extensions.servicio -eq 'finanzas') "$($r.Texto)"

Write-Host '== 10. La ficha completa en UNA query, con lo generado por Kafka (logística, notificaciones, auditoría)'
$ficha = @'
query Ficha($id: ID!) {
  evento(id: $id) {
    id nombreEvento fecha tipoEvento numInvitados zona estado
    cliente { nombreCompleto telefono correo }
    cotizacion { valorBase totalSinAdicionales total depositoInicialRequerido }
    contrato { estado fechaFirma }
    pagos { totalElementos contenido { monto fecha metodoPago } }
    balance { totalCotizado montoAbonado saldoPendiente }
    lugar { nombre capacidad }
    proveedores { contenido { servicio categoria costoAcordado } }
    serviciosAdicionales { nombre cantidad subtotal }
    logistica { meseros personalCocina vehiculos compras(size: 5) { totalElementos } }
    notificaciones { totalElementos contenido { tipo asunto mensaje estado destinatario } }
    auditoria { totalElementos contenido { tipo topico servicioOrigen payload } }
  }
}
'@
$fin = (Get-Date).AddSeconds(30)
do { Start-Sleep -Seconds 3; $r = GraphQL $ficha @{ id = $eid }; $f = $r.Body.data.evento } while (($f.auditoria.totalElementos -lt 3 -or -not $f.logistica -or $f.notificaciones.totalElementos -lt 2) -and (Get-Date) -lt $fin)
Check 'Ficha sin errores GraphQL' (SinErrores $r) "$($r.Texto)"
Check 'Evento RESERVADO con el nombre con tildes' ($f.estado -eq 'RESERVADO' -and $f.nombreEvento -ceq $nombre) ''
Check 'cliente, contrato FIRMADO y lugar' ($f.cliente.nombreCompleto -ceq 'Prueba Valeria Ñáñez Gutiérrez' -and $f.contrato.estado -eq 'FIRMADO' -and $f.contrato.fechaFirma -and $f.lugar.nombre -ceq $lugar.nombre) ''
Check 'cotizacion.total = 8.700.000 + 250.000 del adicional; balance.totalCotizado igual; montoAbonado = depósito' ([decimal]$f.cotizacion.total -eq 8950000 -and [decimal]$f.balance.totalCotizado -eq 8950000 -and [decimal]$f.balance.montoAbonado -eq 2610000 -and [decimal]$f.balance.saldoPendiente -eq 6340000) "$($f.balance | ConvertTo-Json -Compress)"
Check '1 pago (el repetido con la misma clave no se duplicó)' ($f.pagos.totalElementos -eq 1) "pagos=$($f.pagos.totalElementos)"
Check 'proveedor asignado y adicional' (@($f.proveedores.contenido).Count -eq 1 -and @($f.serviciosAdicionales).Count -eq 1) ''
Check "logistica (consumidor Kafka de Logística): meseros $($f.logistica.meseros), cocina $($f.logistica.personalCocina), vehículos $($f.logistica.vehiculos), $($f.logistica.compras.totalElementos) compras" ($f.logistica.meseros -eq 7 -and $f.logistica.personalCocina -eq 4 -and $f.logistica.vehiculos -eq 2 -and $f.logistica.compras.totalElementos -ge 1) ''
$notif = @($f.notificaciones.contenido)
$notConf = @($notif | Where-Object tipo -eq 'EVENTO_CONFIRMADO')[0]
Check "notificaciones (Kafka): 2 GENERADA para cliente:<id> (abono + confirmación)" ($f.notificaciones.totalElementos -eq 2 -and @($notif | Where-Object { $_.estado -eq 'GENERADA' -and $_.destinatario -eq "cliente:$clienteId" }).Count -eq 2) "$($f.notificaciones | ConvertTo-Json -Compress -Depth 4)"
Check "Notificación de la confirmación: '$($notConf.asunto)'" ($notConf.asunto -ceq "Evento confirmado: $nombre" -and $notConf.mensaje -match "«$nombre»") ''
$aud = @($f.auditoria.contenido)
$tiposAud = @($aud | ForEach-Object tipo | Sort-Object) -join ','
Check "auditoria (Kafka): 3 mensajes ($tiposAud) con el sobre original" ($f.auditoria.totalElementos -eq 3 -and $tiposAud -eq 'EVENTO_CONFIRMADO,PAGO_REGISTRADO,SERVICIO_ADICIONAL_AGREGADO') ''
$audConf = @($aud | Where-Object tipo -eq 'EVENTO_CONFIRMADO')[0]
Check 'payload de EVENTO_CONFIRMADO: sobre de eventos-service con datos.nombreEvento' ($audConf.servicioOrigen -eq 'eventos-service' -and $audConf.topico -eq 'eventos.confirmados' -and ($audConf.payload | ConvertFrom-Json).datos.nombreEvento -ceq $nombre) ''

Write-Host '== 11. cancelarEvento (otro evento) y cancelar otra vez -> CONFLICT'
$r = GraphQL 'mutation($i: CrearEventoInput!) { crearEvento(input: $i) { id } }' @{ i = @{ nombreEvento = 'Prueba Boda para cancelar'; clienteId = $clienteId; fecha = $sabados[1].fecha; tipoEvento = 'BODA'; cantidadAdultos = 80; cantidadNinos = 0; zona = 'RURAL' } }
$eid2 = $r.Body.data.crearEvento.id
$r = GraphQL 'mutation($id: ID!) { cancelarEvento(id: $id) { estado } }' @{ id = $eid2 }
Check 'cancelarEvento -> CANCELADO' ((SinErrores $r) -and $r.Body.data.cancelarEvento.estado -eq 'CANCELADO') "$($r.Texto)"
$r = GraphQL 'mutation($id: ID!) { cancelarEvento(id: $id) { estado } }' @{ id = $eid2 }
Check "cancelarEvento otra vez -> CONFLICT ('$((ErrorGraphQL $r).message)')" ((Clasificacion (ErrorGraphQL $r)) -eq 'CONFLICT' -and (ErrorGraphQL $r).message -ceq 'El evento ya está cancelado.') ''
$r = GraphQL 'query($d: Date, $h: Date) { fechasDisponibles(desde: $d, hasta: $h) { fecha } }' @{ d = $sabados[1].fecha; h = $sabados[1].fecha }
Check 'La fecha del evento cancelado vuelve a estar disponible' (@($r.Body.data.fechasDisponibles | Where-Object fecha -eq $sabados[1].fecha).Count -eq 1) ''

Write-Host '== 12. NOT_FOUND, UNAUTHORIZED y SERVICE_UNAVAILABLE'
$r = GraphQL 'query($id: ID!) { evento(id: $id) { id } }' @{ id = [guid]::NewGuid().ToString() }
Check "evento inexistente -> NOT_FOUND ('$((ErrorGraphQL $r).message)')" ((Clasificacion (ErrorGraphQL $r)) -eq 'NOT_FOUND') ''
$r = GraphQL 'mutation($id: ID!) { generarCotizacion(eventoId: $id) { id } }' @{ id = [guid]::NewGuid().ToString() }
Check "generarCotizacion de un evento inexistente -> NOT_FOUND ('$((ErrorGraphQL $r).message)')" ((Clasificacion (ErrorGraphQL $r)) -eq 'NOT_FOUND' -and (ErrorGraphQL $r).extensions.servicio -eq 'finanzas') ''
$r = GraphQL 'mutation($i: ClienteInput!) { crearCliente(input: $i) { id } }' @{ i = @{ nombreCompleto = 'Sin Token'; telefono = '3001234567'; correo = 'sin.token@ejemplo.com' } } -SinToken
Check 'crearCliente sin token -> UNAUTHORIZED' ((Clasificacion (ErrorGraphQL $r)) -eq 'UNAUTHORIZED') "$($r.Texto)"
Compose 'stop contratos-service' | Out-Null
try {
    $r = GraphQL 'mutation($id: ID!) { firmarContrato(eventoId: $id) { estado } }' @{ id = $eid }
    $e = ErrorGraphQL $r
    Check "firmarContrato con contratos-service detenido -> SERVICE_UNAVAILABLE ('$($e.message)')" ((Clasificacion $e) -eq 'SERVICE_UNAVAILABLE' -and $e.extensions.status -eq 503 -and $e.message -ceq 'El servicio de contratos no está disponible en este momento. Intenta de nuevo más tarde.') "$($r.Texto)"
    $r = GraphQL 'query($id: ID!) { evento(id: $id) { estado contrato { estado } balance { saldoPendiente } } }' @{ id = $eid }
    $e = ErrorGraphQL $r
    Check 'Ficha con contratos detenido: contrato -> SERVICE_UNAVAILABLE en evento.contrato; estado y balance se entregan' ((Clasificacion $e) -eq 'SERVICE_UNAVAILABLE' -and ($e.path -join '.') -eq 'evento.contrato' -and $r.Body.data.evento.estado -eq 'RESERVADO' -and [decimal]$r.Body.data.evento.balance.saldoPendiente -eq 6340000) "$($r.Texto)"
} finally {
    Compose 'start contratos-service' | Out-Null
}
Check 'contratos-service vuelve a healthy' ((EsperarHealthy 'contratos-service') -eq 'healthy') ''
Write-Host "  Evento de esta verificación: $eid"

Resumen 'Verificación 6B'
