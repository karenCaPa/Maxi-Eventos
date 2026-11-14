# Carga los datos de demostración de Maxi Eventos (Fase 9A, ampliada para la sustentación el 2026-11-03) SOLO por la API
# REST, como lo haría la organizadora: login con credenciales.ps1 (correo y contraseña del .env o pedidos con Read-Host) y
# peticiones a cada servicio. Pensado para una base de datos recién creada: si ya hay clientes, lugares, proveedores o
# eventos, se detiene sin cargar nada. Repetible: pg_dump, docker compose down -v, up -d y este script.
#
# FECHAS FIJAS (las guías, la sustentación y Postman buscan la demostración por nombre y algunas por su fecha). Cada una se
# comprueba antes con GET /fechas/disponibles: si ya pasó o no está libre, la carga se detiene con un mensaje claro.
#
# Carga 9 clientes (con tildes y ñ), 5 lugares (Salón Los Ocobos, Finca La Ceiba, Finca Villa Esperanza —rural—, Kiosco
# Las Palmas y Hacienda El Pórtico INACTIVO), 6 proveedores (Foto Recuerdos del Huila inactivo) y 9 eventos:
#   Los 3 de siempre (mismos nombres, fechas, clientes, flujo y estados; se crean primero):
#   - Quince años de Lucía (sáb 2026-12-05) RESERVADO: lugar, cotización, contrato firmado, dos abonos, confirmación,
#     logística por Kafka, adicional (recalcula la logística y va al anexo), 2 proveedores, notificaciones y auditoría.
#   - Boda de Andrés y Camila (sáb 2027-01-02) PENDIENTE: creado y cotizado.
#   - Quince años de María José (sáb 2027-01-30) CANCELADO: creado y cancelado.
#   Los nuevos (una etapa cada uno):
#   - Boda de Valentina y Santiago (sáb 2026-11-21) RESERVADO y COMPLETO (Fase 11G): pagado por completo (saldo 0), lugar,
#     2 proveedores confirmados y todas las compras conseguidas → su paso 7 "Resumen" sale "Todo listo".
#   - Quince años de Sofía (sáb 2026-11-28) PENDIENTE con contrato generado SIN firmar (para mostrar el 409 al confirmar).
#   - Quince años de Isabella (vie 2027-02-12) RESERVADO con abono parcial (saldo pendiente).
#   - Boda de Camilo y Juliana (sáb 2027-03-20) RESERVADO con 3 servicios adicionales, 3 proveedores y la lista de compras
#     con ítems conseguidos y 2 ítems manuales.
#   - Boda de Sebastián y Laura (sáb 2027-05-15) PENDIENTE recién creado (sin cotización).
#   - Boda de Natalia y Felipe (dom 2027-06-20) PENDIENTE con cotización y lugar.
#   Fase 11E (fecha CALCULADA al cargar, no fija; con un cliente más, Gabriela Perdomo Trujillo):
#   - Boda de Gabriela y Mateo RESERVADO en el primer viernes, sábado o domingo libre de los próximos 7 días, para mostrar
#     el bloque "Próximos 7 días" y "Ejecutar recordatorios". Por eso la carga se hace el MISMO DÍA de la presentación (o
#     el anterior), con RECORDATORIOS_CRON=- en el .env para que la tarea programada no se adelante al botón.
#   Fase 11H: al final marca como leídas todas las notificaciones de la carga para la administradora y para la asistente
#     (lecturas por usuario; la de la asistente necesita VERIFICACION_ASISTENTE_PASSWORD): la campanita empieza en 0.
# Espera y comprueba lo que hacen Logística, Notificaciones y Auditoría al consumir los mensajes de Kafka. Al final imprime
# los ids y una tabla: evento, fecha, cliente, estado y para qué sirve en la demostración.
# Uso (con el entorno levantado):  .\scripts\verificacion\cargar-demo.ps1
# Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
# Los eventos de la demostración se conservan (Resumen cancela lo que crean los demás scripts)
$script:ConservarEventosCreados = $true

# Paso obligatorio: si la respuesta no es la esperada, se detiene (los pasos siguientes dependen de este).
function Exigir([string]$Nombre, $Respuesta, [int]$Esperado) {
    CheckStatus $Nombre $Respuesta $Esperado
    if ($Respuesta.Status -ne $Esperado) { throw "Carga detenida en el paso '$Nombre'." }
    $Respuesta.Body
}

# Repite una consulta hasta que la condición se cumpla (procesos asíncronos por Kafka). Devuelve la última respuesta.
# Hasta 90 s de margen. Antes de la Fase 11E, tras un "down -v" los consumidores tardaban hasta 5 minutos en ver las
# particiones 1 y 2 (tópicos creados con 1 partición) y la espera era de 6 minutos. Con el arreglo de la 11E, comprobado en el
# reinicio de la 11G sobre un Kafka vacío, cada mensaje se procesa en menos de 1 s y toda la carga dura unos 20 s.
function Esperar([scriptblock]$Consulta, [scriptblock]$Condicion, [int]$Segundos = 90) {
    $fin = (Get-Date).AddSeconds($Segundos)
    do {
        $r = & $Consulta
        if (& $Condicion $r) { return $r }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $fin)
    $r
}

# Fecha fija de la demostración: debe ser futura, de un día permitido y estar libre (la API ya excluye los fines de semana
# ocupados por la regla). Si no, se detiene: hay que elegir otra fecha fija en este script (y revisar las guías).
function FechaLibre([string]$Fecha) {
    $r = Exigir "fecha $Fecha disponible" (Api GET "$($Url.eventos)/fechas/disponibles?desde=$Fecha&hasta=$Fecha") 200
    if (@($r | Where-Object fecha -eq $Fecha).Count -ne 1) {
        throw "La fecha fija $Fecha ya pasó o no está libre: cambia las fechas de cargar-demo.ps1 y las de las guías."
    }
    $Fecha
}

function CrearEvento([hashtable]$Datos) {
    Exigir "crear evento $($Datos.nombreEvento) ($($Datos.fecha))" (Api POST "$($Url.eventos)/eventos" $Datos) 201
}

function Pagar([string]$EventoId, [string]$ClienteId, [decimal]$Monto, [string]$Metodo) {
    Exigir "abono de $Monto COP ($Metodo)" (Api POST "$($Url.finanzas)/finanzas/pagos" @{
            eventoId = $EventoId; clienteId = $ClienteId; metodoPago = $Metodo; monto = $Monto; moneda = 'COP' }) 201
}

# Cotiza, crea y firma el contrato, abona el depósito requerido, confirma y espera la logística calculada por Kafka.
function Reservar($Evento, [string]$ClienteId, [string]$MetodoDeposito) {
    $cot = Exigir 'cotizar' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $Evento.id }) 201
    $null = Exigir 'crear contrato' (Api POST "$($Url.contratos)/eventos/$($Evento.id)/contrato") 201
    $null = Exigir 'marcar contrato firmado' (Api PATCH "$($Url.contratos)/eventos/$($Evento.id)/contrato/marcar-firmado") 200
    $deposito = Pagar $Evento.id $ClienteId ([decimal]$cot.montoReservaRequerido) $MetodoDeposito
    $conf = Exigir 'confirmar evento' (Api PATCH "$($Url.eventos)/eventos/$($Evento.id)/confirmar") 200
    Check "$($Evento.nombreEvento): RESERVADO" ($conf.estado -eq 'RESERVADO') "estado=$($conf.estado)"
    $log = Esperar { Api GET "$($Url.logistica)/eventos/$($Evento.id)/logistica" } { param($r) $r.Status -eq 200 }
    $null = Exigir "$($Evento.nombreEvento): logística calculada al consumir EVENTO_CONFIRMADO" $log 200
    [pscustomobject]@{ Cotizacion = $cot; Deposito = $deposito; Logistica = $log.Body }
}

# Espera a que Notificaciones y Auditoría registren los mensajes del evento (pagos + confirmación; adicionales solo auditoría).
# (Ver Esperar: desde la 11E, segundos también tras un "down -v".)
function ComprobarConsumidores([string]$EventoId, [string]$Nombre, [int]$Pagos, [int]$Adicionales) {
    $n = $Pagos + 1; $a = $Pagos + 1 + $Adicionales
    Write-Host "  (esperando a Notificaciones y Auditoría)"
    $notif = Esperar { Api GET "$($Url.notificaciones)/notificaciones?eventoId=$EventoId" } { param($r) $r.Status -eq 200 -and $r.Body.totalElementos -ge $n }
    Check "$($Nombre): $($notif.Body.totalElementos) notificaciones ($Pagos pagos + confirmación)" ($notif.Body.totalElementos -eq $n)
    $audit = Esperar { Api GET "$($Url.auditoria)/auditoria?eventoId=$EventoId" } { param($r) $r.Status -eq 200 -and $r.Body.totalElementos -ge $a }
    Check "$($Nombre): $($audit.Body.totalElementos) mensajes de auditoría ($Pagos pagos, confirmación y $Adicionales adicionales)" ($audit.Body.totalElementos -eq $a)
}

Write-Host '== Login'
$null = Exigir 'login' (Login) 200

Write-Host '== Comprobación: la base de datos debe estar vacía'
$existentes = [ordered]@{
    clientes    = (Api GET "$($Url.clientes)/clientes?size=1").Body.totalElementos
    lugares     = (Api GET "$($Url.lugares)/lugares?size=1").Body.totalElementos
    proveedores = (Api GET "$($Url.proveedores)/proveedores?size=1").Body.totalElementos
    eventos     = (Api GET "$($Url.eventos)/eventos?size=1").Body.totalElementos
}
$conDatos = @($existentes.Keys | Where-Object { $existentes[$_] -gt 0 })
if ($conDatos.Count -gt 0) {
    Write-Host ''
    Write-Host ("AVISO: ya hay datos ({0}). No se carga nada para no mezclar la demostración con otros registros." -f
        (($conDatos | ForEach-Object { "$_=$($existentes[$_])" }) -join ', ')) -ForegroundColor Yellow
    Write-Host 'Los datos de demostración se cargan sobre una base de datos recién creada (ver README).' -ForegroundColor Yellow
    exit 1
}
Check 'sin clientes, lugares, proveedores ni eventos' $true

Write-Host '== Clientes'
$clientes = @(
    @{ nombreCompleto = 'Lucía Fernández Ospina'; telefono = '3101234567'; correo = 'lucia.fernandez@ejemplo.com'; direccion = 'Calle 8 # 5-32, Neiva' }
    @{ nombreCompleto = 'Andrés Muñoz Peña'; telefono = '3157654321'; correo = 'andres.munoz@ejemplo.com'; direccion = 'Carrera 7 # 12-45, Neiva' }
    @{ nombreCompleto = 'María José Cañón Díaz'; telefono = '3209876543'; correo = 'mariajose.canon@ejemplo.com'; direccion = 'Vereda El Caguán, Neiva' }
    @{ nombreCompleto = 'Sebastián Ibáñez Rojas'; telefono = '3014567890'; correo = 'sebastian.ibanez@ejemplo.com'; direccion = 'Avenida La Toma # 20-10, Neiva' }
    # Desde la ampliación del 2026-11-03 (índices 4 a 8)
    @{ nombreCompleto = 'Valentina Rojas Quintero'; telefono = '3166543210'; correo = 'valentina.rojas@ejemplo.com'; direccion = 'Calle 18 # 3-25, barrio Quebradaseca, Neiva' }
    @{ nombreCompleto = 'Patricia Lozada Muñoz'; telefono = '3172345678'; correo = 'patricia.lozada@ejemplo.com'; direccion = 'Carrera 5 # 26-14, Neiva' }
    @{ nombreCompleto = 'Daniela Ñáñez Cárdenas'; telefono = '3188765432'; correo = 'daniela.nanez@ejemplo.com'; direccion = 'Calle 64 # 1W-30, barrio Los Mártires, Neiva' }
    @{ nombreCompleto = 'Camilo Peñaloza Ruiz'; telefono = '3193456789'; correo = 'camilo.penaloza@ejemplo.com'; direccion = 'Vereda San Antonio, Rivera (Huila)' }
    @{ nombreCompleto = 'Natalia Peñuela Ortiz'; telefono = '3204561237'; correo = 'natalia.penuela@ejemplo.com'; direccion = 'Carrera 16 # 41-08, Neiva' }
) | ForEach-Object { Exigir "crear cliente $($_.nombreCompleto)" (Api POST "$($Url.clientes)/clientes" $_) 201 }
Check 'los nombres se guardan con tildes y ñ' ($clientes[1].nombreCompleto -ceq 'Andrés Muñoz Peña' -and $clientes[2].nombreCompleto -ceq 'María José Cañón Díaz')

Write-Host '== Lugares'
$salon = Exigir 'crear lugar Salón Los Ocobos (ACTIVO, 150)' (Api POST "$($Url.lugares)/lugares" @{ nombre = 'Salón de Eventos Los Ocobos'; direccion = 'Calle 21 # 6-40, Neiva'; tipo = 'SALON'; capacidad = 150 }) 201
$finca = Exigir 'crear lugar Finca La Ceiba (ACTIVO, 300)' (Api POST "$($Url.lugares)/lugares" @{ nombre = 'Finca La Ceiba'; direccion = 'Vereda Fortalecillas, Neiva'; tipo = 'FINCA'; capacidad = 300 }) 201
$hacienda = Exigir 'crear lugar Hacienda El Pórtico' (Api POST "$($Url.lugares)/lugares" @{ nombre = 'Hacienda El Pórtico'; direccion = 'Kilómetro 5 vía a Rivera'; tipo = 'HACIENDA'; capacidad = 200 }) 201
$hacienda = Exigir 'inactivar Hacienda El Pórtico' (Api PATCH "$($Url.lugares)/lugares/$($hacienda.id)/estado" @{ estado = 'INACTIVO' }) 200
Check 'Hacienda El Pórtico queda INACTIVO' ($hacienda.estado -eq 'INACTIVO') "estado=$($hacienda.estado)"
$fincaRural = Exigir 'crear lugar Finca Villa Esperanza (rural, 250)' (Api POST "$($Url.lugares)/lugares" @{ nombre = 'Finca Villa Esperanza'; direccion = 'Vereda Río Frío, kilómetro 12 vía Rivera'; tipo = 'FINCA'; capacidad = 250 }) 201
$kiosco = Exigir 'crear lugar Kiosco Las Palmas (80)' (Api POST "$($Url.lugares)/lugares" @{ nombre = 'Kiosco Las Palmas'; direccion = 'Malecón del río Magdalena, Neiva'; tipo = 'KIOSCO'; capacidad = 80 }) 201

Write-Host '== Proveedores'
$proveedores = @(
    @{ nombre = 'Sonido y Luces del Huila'; categoria = 'SONIDO'; contacto = '3112223344' }
    @{ nombre = 'Banquetes Doña Inés'; categoria = 'BANQUETES'; contacto = '3125556677' }
    @{ nombre = 'Floristería El Jardín'; categoria = 'FLORISTERIA'; contacto = '3138889900' }
    @{ nombre = 'Fotografía Ñapa Estudio'; categoria = 'FOTOGRAFIA'; contacto = '3141112233' }
    @{ nombre = 'Decoraciones Ensueño'; categoria = 'DECORACION'; contacto = '3154447788' }
    @{ nombre = 'Foto Recuerdos del Huila'; categoria = 'FOTOGRAFIA'; contacto = '3176668899' }
) | ForEach-Object { Exigir "crear proveedor $($_.nombre)" (Api POST "$($Url.proveedores)/proveedores" $_) 201 }
$inactivo = Exigir 'inactivar Foto Recuerdos del Huila' (Api PATCH "$($Url.proveedores)/proveedores/$($proveedores[5].id)/estado" @{ activo = $false }) 200
Check 'Foto Recuerdos del Huila queda inactivo' ($inactivo.activo -eq $false) "activo=$($inactivo.activo)"

Write-Host '== Evento RESERVADO (flujo completo)'
$fechaReservado = FechaLibre '2026-12-05'
$reservado = Exigir "crear evento Quince años de Lucía ($fechaReservado)" (Api POST "$($Url.eventos)/eventos" @{
        nombreEvento = 'Quince años de Lucía'; clienteId = $clientes[0].id; fecha = $fechaReservado; tipoEvento = 'QUINCE_ANOS'
        cantidadAdultos = 90; cantidadNinos = 15; zona = 'NEIVA' }) 201
$idR = $reservado.id
$null = Exigir 'asignar el Salón Los Ocobos' (Api PATCH "$($Url.eventos)/eventos/$idR/lugar" @{ lugarId = $salon.id }) 200
$cotR = Exigir 'cotizar' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $idR }) 201
$contrato = Exigir 'crear contrato' (Api POST "$($Url.contratos)/eventos/$idR/contrato") 201
$contrato = Exigir 'marcar contrato firmado' (Api PATCH "$($Url.contratos)/eventos/$idR/contrato/marcar-firmado") 200
$reserva = [decimal]$cotR.montoReservaRequerido
$primero = [math]::Round($reserva * 0.4, 2)
$pago1 = Exigir "abono 1 ($primero COP, transferencia)" (Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $idR; clienteId = $clientes[0].id; metodoPago = 'TRANSFERENCIA'; monto = $primero; moneda = 'COP' }) 201
$pago2 = Exigir "abono 2 ($($reserva - $primero) COP, efectivo)" (Api POST "$($Url.finanzas)/finanzas/pagos" @{ eventoId = $idR; clienteId = $clientes[0].id; metodoPago = 'EFECTIVO'; monto = ($reserva - $primero); moneda = 'COP' }) 201
$conf = Exigir 'confirmar evento' (Api PATCH "$($Url.eventos)/eventos/$idR/confirmar") 200
Check 'evento RESERVADO' ($conf.estado -eq 'RESERVADO') "estado=$($conf.estado)"
$log1 = Esperar { Api GET "$($Url.logistica)/eventos/$idR/logistica" } { param($r) $r.Status -eq 200 }
$null = Exigir 'logística calculada al consumir EVENTO_CONFIRMADO' $log1 200
$camiones = [int]$log1.Body.camionesTransporte
$adicional = Exigir 'agregar servicio adicional transporte-adicional x1' (Api POST "$($Url.finanzas)/eventos/$idR/adicionales" @{ codigo = 'transporte-adicional'; cantidad = 1 }) 201
$log2 = Esperar { Api GET "$($Url.logistica)/eventos/$idR/logistica" } { param($r) $r.Status -eq 200 -and [int]$r.Body.camionesTransporte -eq $camiones + 1 }
Check "logística recalculada por el adicional (camiones $camiones -> $($log2.Body.camionesTransporte))" ([int]$log2.Body.camionesTransporte -eq $camiones + 1)
$anexo = Esperar { Api GET "$($Url.contratos)/eventos/$idR/adicionales/anexo-pdf" } { param($r) $r.Status -eq 200 }
Check 'anexo del contrato con el adicional (PDF)' ($anexo.Status -eq 200 -and $anexo.Tipo -eq 'application/pdf') "status=$($anexo.Status)"
$asig1 = Exigir 'asignar Sonido y Luces del Huila' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $idR; proveedorId = $proveedores[0].id; servicioAsignado = 'Sonido e iluminación'; costoAcordado = 1200000; estado = 'CONFIRMADO' }) 201
$asig2 = Exigir 'asignar Banquetes Doña Inés' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $idR; proveedorId = $proveedores[1].id; servicioAsignado = 'Banquete para 105 invitados'; costoAcordado = 6300000 }) 201
$notif = Esperar { Api GET "$($Url.notificaciones)/notificaciones?eventoId=$idR" } { param($r) $r.Status -eq 200 -and $r.Body.totalElementos -ge 3 }
Check "notificaciones del evento: $($notif.Body.totalElementos) (2 pagos + confirmación)" ($notif.Body.totalElementos -eq 3)
$audit = Esperar { Api GET "$($Url.auditoria)/auditoria?eventoId=$idR" } { param($r) $r.Status -eq 200 -and $r.Body.totalElementos -ge 4 }
Check "auditoría del evento: $($audit.Body.totalElementos) mensajes (2 pagos, confirmación y adicional)" ($audit.Body.totalElementos -eq 4)
$balance = Exigir 'balance' (Api GET "$($Url.finanzas)/eventos/$idR/balance") 200

Write-Host '== Evento PENDIENTE (solo cotizado)'
$fechaPendiente = FechaLibre '2027-01-02'
$pendiente = Exigir "crear evento Boda de Andrés y Camila ($fechaPendiente)" (Api POST "$($Url.eventos)/eventos" @{
        nombreEvento = 'Boda de Andrés y Camila'; clienteId = $clientes[1].id; fecha = $fechaPendiente; tipoEvento = 'BODA'
        cantidadAdultos = 120; cantidadNinos = 10; zona = 'RURAL' }) 201
$cotP = Exigir 'cotizar' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $pendiente.id }) 201
Check 'evento PENDIENTE' ($pendiente.estado -eq 'PENDIENTE') "estado=$($pendiente.estado)"

Write-Host '== Evento CANCELADO'
$fechaCancelado = FechaLibre '2027-01-30'
$cancelado = Exigir "crear evento Quince años de María José ($fechaCancelado)" (Api POST "$($Url.eventos)/eventos" @{
        nombreEvento = 'Quince años de María José'; clienteId = $clientes[2].id; fecha = $fechaCancelado; tipoEvento = 'QUINCE_ANOS'
        cantidadAdultos = 60; cantidadNinos = 10; zona = 'NEIVA' }) 201
$cancelado = Exigir 'cancelar' (Api PATCH "$($Url.eventos)/eventos/$($cancelado.id)/cancelar") 200
Check 'evento CANCELADO' ($cancelado.estado -eq 'CANCELADO') "estado=$($cancelado.estado)"
$libre = Api GET "$($Url.eventos)/fechas/disponibles?desde=$fechaCancelado&hasta=$fechaCancelado"
Check "la fecha $fechaCancelado vuelve a estar disponible" (@($libre.Body | Where-Object fecha -eq $fechaCancelado).Count -eq 1)

# ==================================================================== eventos nuevos (ampliación del 2026-11-03)

Write-Host '== Evento RESERVADO y pagado por completo (Boda de Valentina y Santiago)'
$valentina = CrearEvento @{ nombreEvento = 'Boda de Valentina y Santiago'; clienteId = $clientes[4].id; fecha = (FechaLibre '2026-11-21')
    tipoEvento = 'BODA'; cantidadAdultos = 160; cantidadNinos = 20; zona = 'RURAL'; lugarId = $finca.id }
$null = Reservar $valentina $clientes[4].id 'TRANSFERENCIA'
$balV = Exigir 'balance tras el depósito' (Api GET "$($Url.finanzas)/eventos/$($valentina.id)/balance") 200
$null = Pagar $valentina.id $clientes[4].id ([decimal]$balV.saldoPendiente) 'TARJETA'
$balV = Exigir 'balance final' (Api GET "$($Url.finanzas)/eventos/$($valentina.id)/balance") 200
Check "Boda de Valentina y Santiago: saldo 0 (abonado $($balV.totalAbonado) de $($balV.total))" ([decimal]$balV.saldoPendiente -eq 0)
ComprobarConsumidores $valentina.id 'Boda de Valentina y Santiago' 2 0
# Fase 11G: el evento COMPLETO de la demostración, para que su paso 7 "Resumen" salga "Todo listo": lugar (Finca La
# Ceiba, ya asignado al crearlo), proveedores confirmados y todas las compras conseguidas.
$null = Exigir 'asignar Banquetes Doña Inés (confirmado)' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $valentina.id; proveedorId = $proveedores[1].id; servicioAsignado = 'Banquete para 180 invitados'; costoAcordado = 10800000; estado = 'CONFIRMADO' }) 201
$null = Exigir 'asignar Sonido y Luces del Huila (confirmado)' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $valentina.id; proveedorId = $proveedores[0].id; servicioAsignado = 'Sonido e iluminación de la boda'; costoAcordado = 1500000; estado = 'CONFIRMADO' }) 201
$comprasV = (Exigir 'lista de compras calculada' (Api GET "$($Url.logistica)/eventos/$($valentina.id)/compras?size=100") 200).contenido
$null = Exigir 'marcar todas las compras como conseguidas' (Api PUT "$($Url.logistica)/eventos/$($valentina.id)/compras" (ConvertTo-Json -InputObject @($comprasV | ForEach-Object { @{ id = $_.id; comprado = $true } }) -Compress)) 200
$asigV = (Exigir 'proveedores asignados' (Api GET "$($Url.proveedores)/proveedores/eventos/$($valentina.id)?size=100") 200).contenido
$listaV = (Exigir 'lista de compras final' (Api GET "$($Url.logistica)/eventos/$($valentina.id)/compras?size=100") 200).contenido
$evV = Exigir 'evento' (Api GET "$($Url.eventos)/eventos/$($valentina.id)") 200
Check "Boda de Valentina y Santiago COMPLETA: lugar, $(@($asigV).Count) proveedores confirmados y $(@($listaV).Count) de $(@($listaV).Count) compras conseguidas" (
    $evV.lugarId -and @($asigV).Count -eq 2 -and @($asigV | Where-Object estado -ne 'CONFIRMADO').Count -eq 0 -and @($listaV).Count -gt 0 -and @($listaV | Where-Object { -not $_.comprado }).Count -eq 0)

Write-Host '== Evento PENDIENTE con contrato sin firmar (Quince años de Sofía)'
$sofia = CrearEvento @{ nombreEvento = 'Quince años de Sofía'; clienteId = $clientes[5].id; fecha = (FechaLibre '2026-11-28')
    tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 85; cantidadNinos = 15; zona = 'NEIVA'; lugarId = $salon.id }
$null = Exigir 'cotizar' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $sofia.id }) 201
$contratoS = Exigir 'crear contrato (sin firmar)' (Api POST "$($Url.contratos)/eventos/$($sofia.id)/contrato") 201
Check 'Quince años de Sofía: contrato PENDIENTE de firma' ($contratoS.estado -eq 'PENDIENTE') "estado=$($contratoS.estado)"
$intento = Api PATCH "$($Url.eventos)/eventos/$($sofia.id)/confirmar"
Check 'confirmar sin firmar -> 409 (el caso que se muestra en la interfaz)' ($intento.Status -eq 409) "status=$($intento.Status)"
$sigue = Exigir 'el evento sigue PENDIENTE' (Api GET "$($Url.eventos)/eventos/$($sofia.id)") 200
Check 'Quince años de Sofía: PENDIENTE' ($sigue.estado -eq 'PENDIENTE') "estado=$($sigue.estado)"

Write-Host '== Evento RESERVADO con abono parcial (Quince años de Isabella)'
$isabella = CrearEvento @{ nombreEvento = 'Quince años de Isabella'; clienteId = $clientes[6].id; fecha = (FechaLibre '2027-02-12')
    tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 70; cantidadNinos = 10; zona = 'NEIVA'; lugarId = $kiosco.id }
$null = Reservar $isabella $clientes[6].id 'EFECTIVO'
$balI = Exigir 'balance' (Api GET "$($Url.finanzas)/eventos/$($isabella.id)/balance") 200
Check "Quince años de Isabella: saldo pendiente $($balI.saldoPendiente) (abonado el depósito $($balI.totalAbonado))" ([decimal]$balI.saldoPendiente -gt 0)
ComprobarConsumidores $isabella.id 'Quince años de Isabella' 1 0

Write-Host '== Evento RESERVADO completo: adicionales, proveedores y lista de compras (Boda de Camilo y Juliana)'
$camilo = CrearEvento @{ nombreEvento = 'Boda de Camilo y Juliana'; clienteId = $clientes[7].id; fecha = (FechaLibre '2027-03-20')
    tipoEvento = 'BODA'; cantidadAdultos = 180; cantidadNinos = 20; zona = 'RURAL'; lugarId = $fincaRural.id }
$resC = Reservar $camilo $clientes[7].id 'TRANSFERENCIA'
$balC = Exigir 'balance tras el depósito' (Api GET "$($Url.finanzas)/eventos/$($camilo.id)/balance") 200
$null = Pagar $camilo.id $clientes[7].id ([math]::Round([decimal]$balC.saldoPendiente * 0.3, 0)) 'EFECTIVO'
$camionesC = [int]$resC.Logistica.camionesTransporte
foreach ($a in @(@{ codigo = 'decoracion-premium'; cantidad = 1 }, @{ codigo = 'transporte-adicional'; cantidad = 1 }, @{ codigo = 'hora-extra'; cantidad = 2 })) {
    $null = Exigir "agregar servicio adicional $($a.codigo) x$($a.cantidad)" (Api POST "$($Url.finanzas)/eventos/$($camilo.id)/adicionales" $a) 201
}
$logC = Esperar { Api GET "$($Url.logistica)/eventos/$($camilo.id)/logistica" } { param($r) $r.Status -eq 200 -and [int]$r.Body.camionesTransporte -eq $camionesC + 1 }
Check "logística recalculada por el transporte adicional (camiones $camionesC -> $($logC.Body.camionesTransporte))" ([int]$logC.Body.camionesTransporte -eq $camionesC + 1)
$null = Exigir 'asignar Floristería El Jardín' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $camilo.id; proveedorId = $proveedores[2].id; servicioAsignado = 'Arreglos florales y bouquet de la novia'; costoAcordado = 1800000; estado = 'CONFIRMADO' }) 201
$null = Exigir 'asignar Fotografía Ñapa Estudio' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $camilo.id; proveedorId = $proveedores[3].id; servicioAsignado = 'Fotografía y video de la boda'; costoAcordado = 2500000 }) 201
$null = Exigir 'asignar Decoraciones Ensueño' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $camilo.id; proveedorId = $proveedores[4].id; servicioAsignado = 'Decoración de la carpa y las mesas'; costoAcordado = 3200000; estado = 'CONFIRMADO' }) 201
# Lista de compras: dos ítems calculados conseguidos y dos manuales (uno conseguido, con nota, y otro pendiente)
$compras = (Exigir 'lista de compras calculada' (Api GET "$($Url.logistica)/eventos/$($camilo.id)/compras?size=100") 200).contenido
$marcar = @($compras | Where-Object { $_.nombreItem -in @('Bebidas', 'Vasos') } | ForEach-Object { @{ id = $_.id; comprado = $true } })
Check 'la lista calculada tiene Bebidas y Vasos' ($marcar.Count -eq 2) "ítems=$(@($compras | ForEach-Object nombreItem) -join ', ')"
$null = Exigir 'marcar Bebidas y Vasos como conseguidos' (Api PUT "$($Url.logistica)/eventos/$($camilo.id)/compras" (ConvertTo-Json -InputObject $marcar -Compress)) 200
$recuerdos = Exigir 'agregar ítem manual Recuerdos para invitados' (Api POST "$($Url.logistica)/eventos/$($camilo.id)/compras" @{ nombreItem = 'Recuerdos para invitados'; cantidadEstimada = '200 unidades'; nota = 'Bolsitas de dulces con los nombres de los novios' }) 201
$null = Exigir 'marcar Recuerdos para invitados como conseguido' (Api PUT "$($Url.logistica)/eventos/$($camilo.id)/compras" (ConvertTo-Json -InputObject @(@{ id = $recuerdos.id; comprado = $true }) -Compress)) 200
$null = Exigir 'agregar ítem manual Velas aromáticas para las mesas' (Api POST "$($Url.logistica)/eventos/$($camilo.id)/compras" @{ nombreItem = 'Velas aromáticas para las mesas'; cantidadEstimada = '25 cajas'; nota = 'Aroma de vainilla, confirmar con la decoradora' }) 201
$listaC = (Exigir 'lista de compras final' (Api GET "$($Url.logistica)/eventos/$($camilo.id)/compras?size=100") 200).contenido
$conseguidos = @($listaC | Where-Object comprado).Count; $manuales = @($listaC | Where-Object origen -eq 'MANUAL').Count
Check "Boda de Camilo y Juliana: $conseguidos de $(@($listaC).Count) ítems conseguidos y $manuales manuales" ($conseguidos -eq 3 -and $manuales -eq 2 -and @($listaC).Count -eq @($compras).Count + 2)
$asigC = Exigir 'proveedores asignados' (Api GET "$($Url.proveedores)/proveedores/eventos/$($camilo.id)?size=100") 200
Check "Boda de Camilo y Juliana: $($asigC.totalElementos) proveedores asignados" ($asigC.totalElementos -eq 3)
$balC = Exigir 'balance final' (Api GET "$($Url.finanzas)/eventos/$($camilo.id)/balance") 200
Check "Boda de Camilo y Juliana: total con adicionales $($balC.total), saldo $($balC.saldoPendiente)" ([decimal]$balC.adicionales -gt 0 -and [decimal]$balC.saldoPendiente -gt 0)
ComprobarConsumidores $camilo.id 'Boda de Camilo y Juliana' 2 3

Write-Host '== Evento PENDIENTE recién creado (Boda de Sebastián y Laura)'
$sebastian = CrearEvento @{ nombreEvento = 'Boda de Sebastián y Laura'; clienteId = $clientes[3].id; fecha = (FechaLibre '2027-05-15')
    tipoEvento = 'BODA'; cantidadAdultos = 110; cantidadNinos = 10; zona = 'NEIVA' }
$sinCot = Api GET "$($Url.finanzas)/eventos/$($sebastian.id)/cotizacion"
Check 'Boda de Sebastián y Laura: PENDIENTE y sin cotización (404)' ($sebastian.estado -eq 'PENDIENTE' -and $sinCot.Status -eq 404) "estado=$($sebastian.estado) cotizacion=$($sinCot.Status)"

Write-Host '== Evento PENDIENTE con cotización y lugar (Boda de Natalia y Felipe)'
$natalia = CrearEvento @{ nombreEvento = 'Boda de Natalia y Felipe'; clienteId = $clientes[8].id; fecha = (FechaLibre '2027-06-20')
    tipoEvento = 'BODA'; cantidadAdultos = 140; cantidadNinos = 0; zona = 'RURAL'; lugarId = $finca.id }
$cotN = Exigir 'cotizar' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $natalia.id }) 201
Check "Boda de Natalia y Felipe: PENDIENTE con cotización ($($cotN.total) COP, recargo rural $($cotN.recargoZona))" ($natalia.estado -eq 'PENDIENTE' -and [decimal]$cotN.recargoZona -gt 0)

# ==================================================================== Fase 11E: evento próximo (recordatorios)

# Fecha CALCULADA al cargar (no fija): el primer viernes, sábado o domingo libre entre mañana y hoy + 7. Por eso la carga se
# hace el mismo día de la presentación (o el anterior): así el evento cae dentro de los "Próximos 7 días" y el botón
# "Ejecutar recordatorios" le envía su recordatorio EVENTO_PROXIMO. Si no hay ninguno libre, avisa y sigue sin él.
Write-Host '== Evento RESERVADO en los próximos 7 días (Boda de Gabriela y Mateo, para los recordatorios)'
$desdeP = (Get-Date).AddDays(1).ToString('yyyy-MM-dd'); $hastaP = (Get-Date).AddDays(7).ToString('yyyy-MM-dd')
$libresP = Exigir "fechas disponibles de $desdeP a $hastaP" (Api GET "$($Url.eventos)/fechas/disponibles?desde=$desdeP&hasta=$hastaP") 200
$fechaProxima = @($libresP | ForEach-Object { [string]$_.fecha } | Sort-Object) | Select-Object -First 1
$gabriela = $null
if (-not $fechaProxima) {
    Write-Host "  AVISO: no hay viernes, sábado ni domingo libre entre $desdeP y $hastaP; se carga sin el evento próximo." -ForegroundColor Yellow
} else {
    $clienteG = Exigir 'crear cliente Gabriela Perdomo Trujillo' (Api POST "$($Url.clientes)/clientes" @{
            nombreCompleto = 'Gabriela Perdomo Trujillo'; telefono = '3215550123'; correo = 'gabriela.perdomo@ejemplo.com'
            direccion = 'Calle 10 # 12-20, barrio Altico, Neiva' }) 201
    $clientes += $clienteG
    $gabriela = CrearEvento @{ nombreEvento = 'Boda de Gabriela y Mateo'; clienteId = $clienteG.id; fecha = $fechaProxima
        tipoEvento = 'BODA'; cantidadAdultos = 90; cantidadNinos = 10; zona = 'NEIVA'; lugarId = $salon.id }
    $null = Reservar $gabriela $clienteG.id 'TRANSFERENCIA'
    ComprobarConsumidores $gabriela.id 'Boda de Gabriela y Mateo' 1 0
    $diasG = ([datetime]$fechaProxima - (Get-Date).Date).Days
    Check "Boda de Gabriela y Mateo: RESERVADO el $fechaProxima (faltan $diasG días)" ($diasG -ge 1 -and $diasG -le 7)
    # La carga NO ejecuta los recordatorios: se muestran en la presentación con el botón (o salen solos con la tarea programada)
    $recG = Api GET "$($Url.notificaciones)/notificaciones?eventoId=$($gabriela.id)"
    Check 'Boda de Gabriela y Mateo: todavía sin recordatorio (se envía en la demostración)' (@($recG.Body.contenido | Where-Object tipo -eq 'EVENTO_PROXIMO').Count -eq 0)
}

# ==================================================================== Fase 11H: campanita en 0 para las dos usuarias

# Las notificaciones de la carga (pagos y confirmaciones) se marcan como leídas para la administradora y para la asistente
# (las lecturas son POR USUARIO): en la presentación la campanita empieza en 0 y pasa a 1 al ejecutar el recordatorio.
Write-Host '== Campanita: todas las notificaciones de la carga quedan leídas para las dos usuarias'
$totalNotif = (Exigir 'notificaciones generadas' (Api GET "$($Url.notificaciones)/notificaciones?size=1") 200).totalElementos
$marcadasA = Exigir 'administradora: marcar todas como leídas' (Api PATCH "$($Url.notificaciones)/notificaciones/leidas") 200
$sinLeerA = (Exigir 'administradora: sin leer' (Api GET "$($Url.notificaciones)/notificaciones/sin-leer") 200).sinLeer
Check "administradora: $($marcadasA.marcadas) de $totalNotif marcadas y 0 sin leer" ($sinLeerA -eq 0 -and $marcadasA.marcadas -eq $totalNotif) "sinLeer=$sinLeerA"
if (-not $script:PasswordAsistente) {
    Write-Host '  AVISO: falta VERIFICACION_ASISTENTE_PASSWORD en el .env; la campanita de la asistente no queda en 0 (rotar-password.ps1 -Usuario asistente).' -ForegroundColor Yellow
} else {
    $loginAsis = Exigir 'login de la asistente' (Api POST "$($Url.auth)/auth/login" (ConvertTo-Json @{ correo = $script:CorreoAsistente; password = $script:PasswordAsistente } -Compress) -SinToken) 200
    $comoAsis = @{ Authorization = "Bearer $($loginAsis.token)" }
    $marcadasS = Exigir 'asistente: marcar todas como leídas' (Api PATCH "$($Url.notificaciones)/notificaciones/leidas" -SinToken -Cabeceras $comoAsis) 200
    $sinLeerS = (Exigir 'asistente: sin leer' (Api GET "$($Url.notificaciones)/notificaciones/sin-leer" -SinToken -Cabeceras $comoAsis) 200).sinLeer
    Check "asistente: $($marcadasS.marcadas) de $totalNotif marcadas y 0 sin leer" ($sinLeerS -eq 0 -and $marcadasS.marcadas -eq $totalNotif) "sinLeer=$sinLeerS"
}

Write-Host ''
Write-Host '================ RESUMEN DE LOS DATOS DE DEMOSTRACIÓN ================'
Write-Host 'Clientes:'
foreach ($c in $clientes) { Write-Host ("  {0}  {1}" -f $c.id, $c.nombreCompleto) }
Write-Host 'Lugares:'
foreach ($l in @($salon, $finca, $fincaRural, $kiosco, $hacienda)) { Write-Host ("  {0}  {1} ({2}, capacidad {3})" -f $l.id, $l.nombre, $l.estado, $l.capacidad) }
Write-Host 'Proveedores:'
foreach ($p in $proveedores) { Write-Host ("  {0}  {1} ({2}{3})" -f $p.id, $p.nombre, $p.categoria, $(if ($p.id -eq $inactivo.id) { ', inactivo' } else { '' })) }
Write-Host 'Eventos:'
Write-Host ("  RESERVADO  {0}  Quince años de Lucía, {1}, Salón Los Ocobos" -f $idR, $fechaReservado)
Write-Host ("             contrato {0} ({1}); pagos {2} y {3}; adicional {4}" -f $contrato.id, $contrato.estado, $pago1.pagoId, $pago2.pagoId, $adicional.id)
Write-Host ("             total {0} {1}, abonado {2}, saldo {3}; logística: {4} meseros, {5} cocina, {6} camiones" -f
    $balance.total, $balance.moneda, $balance.totalAbonado, $balance.saldoPendiente, $log2.Body.meserosRequeridos,
    $log2.Body.personalCocinaRequerido, $log2.Body.camionesTransporte)
Write-Host ("             proveedores asignados {0} y {1}; {2} notificaciones; {3} mensajes de auditoría" -f
    $asig1.id, $asig2.id, $notif.Body.totalElementos, $audit.Body.totalElementos)
Write-Host ("  PENDIENTE  {0}  Boda de Andrés y Camila, {1}, cotización {2} (total {3} {4})" -f $pendiente.id, $fechaPendiente, $cotP.id, $cotP.total, $cotP.moneda)
Write-Host ("  CANCELADO  {0}  Quince años de María José, {1}" -f $cancelado.id, $fechaCancelado)
foreach ($e in @($valentina, $sofia, $isabella, $camilo, $sebastian, $natalia, $gabriela) | Where-Object { $_ }) { Write-Host ("  {0,-10} {1}  {2}, {3}" -f '', $e.id, $e.nombreEvento, $e.fecha) }

Write-Host ''
Write-Host '================ QUÉ EVENTO USAR PARA MOSTRAR CADA COSA ================'
$esCo = [Globalization.CultureInfo]::GetCultureInfo('es-CO')
$dia = { param($f) $esCo.DateTimeFormat.GetAbbreviatedDayName(([datetime]$f).DayOfWeek).TrimEnd('.') + ' ' + $f }
# (la coma al final de cada fila evita que PowerShell aplane el arreglo de filas)
$tabla = @(
    @('Boda de Valentina y Santiago', $valentina.fecha, 'Valentina Rojas Quintero', 'RESERVADO', 'COMPLETO: Resumen "Todo listo" (saldo $ 0, rural)'),
    @('Quince años de Sofía', $sofia.fecha, 'Patricia Lozada Muñoz', 'PENDIENTE', 'Contrato SIN firmar: Confirmar -> 409'),
    @('Quince años de Lucía', $fechaReservado, 'Lucía Fernández Ospina', 'RESERVADO', 'La ficha completa (Postman, guías, demo 1)'),
    @('Boda de Andrés y Camila', $fechaPendiente, 'Andrés Muñoz Peña', 'PENDIENTE', 'Cotizada (zona rural); siguiente paso: contrato'),
    @('Quince años de María José', $fechaCancelado, 'María José Cañón Díaz', 'CANCELADO', 'Evento cancelado: solo descargas, fecha libre'),
    @('Quince años de Isabella', $isabella.fecha, 'Daniela Ñáñez Cárdenas', 'RESERVADO', 'Abono parcial: saldo pendiente, registrar pago'),
    @('Boda de Camilo y Juliana', $camilo.fecha, 'Camilo Peñaloza Ruiz', 'RESERVADO', '3 adicionales, 3 proveedores, compras con manuales'),
    @('Boda de Sebastián y Laura', $sebastian.fecha, 'Sebastián Ibáñez Rojas', 'PENDIENTE', 'Recién creado: generar la cotización'),
    @('Boda de Natalia y Felipe', $natalia.fecha, 'Natalia Peñuela Ortiz', 'PENDIENTE', 'Cotizada con lugar: crear el contrato')
)
if ($gabriela) {
    $tabla += , @('Boda de Gabriela y Mateo', $gabriela.fecha, 'Gabriela Perdomo Trujillo', 'RESERVADO', 'En los próximos 7 días: Ejecutar recordatorios')
}
Write-Host ("  {0,-30} {1,-15} {2,-25} {3,-10} {4}" -f 'Evento', 'Fecha', 'Cliente', 'Estado', 'Para qué sirve')
Write-Host ("  {0,-30} {1,-15} {2,-25} {3,-10} {4}" -f ('-' * 30), ('-' * 15), ('-' * 25), ('-' * 10), ('-' * 50))
foreach ($f in $tabla) { Write-Host ("  {0,-30} {1,-15} {2,-25} {3,-10} {4}" -f $f[0], (& $dia $f[1]), $f[2], $f[3], $f[4]) }

Resumen 'Carga de demostración'
if ($script:Fallos -gt 0) { exit 1 }
