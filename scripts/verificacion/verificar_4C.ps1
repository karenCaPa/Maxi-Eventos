# Verificación en Docker de la subfase 4C (Contratos) + flujo de la Fase 3.
# Requiere el entorno levantado (docker compose up -d). Guardar en UTF-8 con BOM.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

function Dinero($valor) { [decimal]::Round([decimal]$valor, 2) }

CheckStatus 'Login' (Login) 200
Write-Host '== Migración BORRADOR -> PENDIENTE'
Check 'BD: no queda ningún contrato en BORRADOR' ((Psql "SELECT count(*) FROM contratos WHERE estado = 'BORRADOR';").Trim() -eq '0')
# Los contratos anteriores a la Fase 4 (sin la foto de los datos) solo existen en la base de desarrollo; en una base creada
# desde cero (prueba final de la 9E) no hay ninguno y estas dos comprobaciones no aplican.
$antiguo = "$(Psql "SELECT evento_id FROM contratos WHERE cliente_nombre IS NULL ORDER BY creado_en LIMIT 1;")".Trim()
if ($antiguo) {
    $pendientes = [int](Psql "SELECT count(*) FROM contratos WHERE estado = 'PENDIENTE';").Trim()
    Check 'BD: los 4 contratos que estaban en BORRADOR ahora están en PENDIENTE (hay al menos 4)' ($pendientes -ge 4) "pendientes=$pendientes"
    $pdfAntiguo = DescargarPdf "$($Url.contratos)/eventos/$antiguo/contrato/pdf" 'contrato-antiguo'
    Check 'PDF de un contrato anterior a la Fase 4 -> 200 con "No registrado"' ($pdfAntiguo.Status -eq 200 -and $pdfAntiguo.Texto -match (PatronDato 'Nombre' 'No registrado') -and $pdfAntiguo.Texto -notmatch '\{\{') "$($pdfAntiguo.Status)"
} else {
    Write-Host '  (no hay contratos anteriores a la Fase 4 en esta base: se omiten las 2 comprobaciones de la migración)'
}

Write-Host '== Datos de prueba con tildes y ñ'
$fechas = FechasLibres
Check 'Hay al menos 6 fines de semana libres' ($fechas.Count -ge 6)
$s = Sufijo
# Teléfono aleatorio: Clientes no admite teléfonos repetidos y el script se puede ejecutar varias veces.
$telefono = "310$((Get-Random -Minimum 1000000 -Maximum 9999999))"
$cli = Api POST "$($Url.clientes)/clientes" @{ nombreCompleto = 'Prueba María José Peña Ñáñez'; telefono = $telefono; correo = "maria.pena.$s@ejemplo.com"; direccion = 'Carrera 7 # 12-45, Neiva' }
CheckStatus 'Crear cliente "Prueba María José Peña Ñáñez"' $cli 201
$lugar = Api POST "$($Url.lugares)/lugares" @{ nombre = "Prueba Salón Los Ángeles $s"; direccion = 'Vía a Rivera km 3, Neiva'; tipo = 'SALON'; capacidad = 250 }
CheckStatus 'Crear lugar "Prueba Salón Los Ángeles"' $lugar 201
Check 'La API devuelve los nombres con tildes y ñ intactos' ($cli.Body.nombreCompleto -eq 'Prueba María José Peña Ñáñez' -and $lugar.Body.nombre -eq "Prueba Salón Los Ángeles $s")
$ev = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Quince años de Sofía Muñoz'; clienteId = $cli.Body.id; fecha = $fechas[0]; tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 90; cantidadNinos = 15; zona = 'NEIVA'; lugarId = $lugar.Body.id }
CheckStatus 'Crear evento con el lugar' $ev 201
$eid = $ev.Body.id

Write-Host '== Crear contrato (Eventos + Clientes + Lugares + Finanzas)'
CheckStatus 'Contrato de evento sin cotización' (Api POST "$($Url.contratos)/eventos/$eid/contrato") 409
CheckStatus 'Cotizar' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $eid }) 201
$con = Api POST "$($Url.contratos)/eventos/$eid/contrato"
CheckStatus 'Crear contrato sin cuerpo' $con 201
Check 'Estado PENDIENTE' ($con.Body.estado -eq 'PENDIENTE') "estado=$($con.Body.estado)"
Check 'Foto: cliente, fecha, tipo, invitados, lugar y valor total' ($con.Body.clienteNombre -eq 'Prueba María José Peña Ñáñez' -and $con.Body.fechaEvento -eq $fechas[0] -and $con.Body.tipoEvento -eq 'QUINCE_ANOS' -and $con.Body.cantidadAdultos -eq 90 -and $con.Body.cantidadNinos -eq 15 -and $con.Body.lugarNombre -eq "Prueba Salón Los Ángeles $s" -and (Dinero $con.Body.valorTotal) -eq 8700000 -and $con.Body.moneda -eq 'COP') "$($con.Texto)"
Check 'nombreEvento tomado de Eventos' ($con.Body.nombreEvento -eq 'Prueba Quince años de Sofía Muñoz')
$con2 = Api POST "$($Url.contratos)/eventos/$eid/contrato" @{ nombreEvento = 'Otro nombre' }
Check 'Crear otra vez (con cuerpo antiguo) -> 200 el mismo contrato, sin cambios' ($con2.Status -eq 200 -and $con2.Body.id -eq $con.Body.id -and $con2.Body.nombreEvento -eq 'Prueba Quince años de Sofía Muñoz')
CheckStatus 'Contrato de evento inexistente' (Api POST "$($Url.contratos)/eventos/$([guid]::NewGuid())/contrato") 404
$evC = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Boda cancelada'; clienteId = $cli.Body.id; fecha = $fechas[1]; tipoEvento = 'BODA'; cantidadAdultos = 60; cantidadNinos = 0; zona = 'NEIVA' }
CheckStatus 'Crear evento que se cancela' $evC 201
CheckStatus 'Cotizarlo' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $evC.Body.id }) 201
CheckStatus 'Cancelarlo' (Api PATCH "$($Url.eventos)/eventos/$($evC.Body.id)/cancelar") 200
CheckStatus 'Contrato de evento CANCELADO' (Api POST "$($Url.contratos)/eventos/$($evC.Body.id)/contrato") 409

Write-Host '== PDF del contrato'
$pdf = DescargarPdf "$($Url.contratos)/eventos/$eid/contrato/pdf" 'contrato-4C'
CheckStatus 'Descargar PDF del contrato' $pdf 200
Check 'Content-Type application/pdf y archivo PDF válido (%PDF)' ($pdf.Tipo -eq 'application/pdf' -and [Text.Encoding]::ASCII.GetString($pdf.Respuesta.Bytes, 0, 4) -eq '%PDF')
$t = $pdf.Texto
# Fase 11 (PDF rediseñado tras la Fase 10): etiqueta y valor en celdas distintas; mismos datos y misma exigencia (PatronDato)
Check 'PDF: cliente "María José Peña Ñáñez"' ($t -match (PatronDato 'Nombre' 'Prueba María José Peña Ñáñez')) ''
Check 'PDF: evento "Quince años de Sofía Muñoz"' ($t -match (PatronDato 'Evento' 'Prueba Quince años de Sofía Muñoz'))
Check 'PDF: tipo "Quince años"' ($t -match (PatronDato 'Tipo de evento' 'Quince años'))
$fechaLarga = ([datetime]$fechas[0]).ToString("dddd d 'de' MMMM 'de' yyyy", [Globalization.CultureInfo]::GetCultureInfo('es-CO'))
Check "PDF: fecha ($fechaLarga)" ($t -match (PatronDato 'Fecha del evento' $fechaLarga)) ''
Check 'PDF: invitados "105 (90 adultos y 15 niños)"' ($t -match (PatronDato 'Invitados' '105 (90 adultos y 15 niños)'))
Check 'PDF: lugar "Salón Los Ángeles (Vía a Rivera km 3, Neiva)"' ($t -match (PatronDato 'Lugar' "Prueba Salón Los Ángeles $s (Vía a Rivera km 3, Neiva)"))
Check 'PDF: valor total "8.700.000,00 COP"' ($t -match (PatronDato 'Valor total del contrato' '8.700.000,00 COP'))
Check 'PDF: teléfono y correo del cliente' ($t -match (PatronDato 'Teléfono' $telefono) -and $t -match (PatronDato 'Correo' "maria.pena.$s@ejemplo.com"))
Check 'PDF: sin caracteres mal codificados (Ã, Â, �) ni marcadores {{ }}' ($t -notmatch 'Ã|Â|�|\{\{')

Write-Host '== Foto de los datos: cambiar el lugar no cambia el contrato'
CheckStatus 'Renombrar el lugar' (Api PUT "$($Url.lugares)/lugares/$($lugar.Body.id)" @{ nombre = "Salón Renovado $s"; direccion = 'Otra dirección'; tipo = 'SALON'; capacidad = 250 }) 200
$pdfFoto = DescargarPdf "$($Url.contratos)/eventos/$eid/contrato/pdf" 'contrato-4C-foto'
Check 'El PDF conserva "Salón Los Ángeles"' ($pdfFoto.Texto -match (PatronDato 'Lugar' "Prueba Salón Los Ángeles $s (Vía a Rivera km 3, Neiva)") -and $pdfFoto.Texto -notmatch 'Renovado')

Write-Host '== Firma'
$firma = Api PATCH "$($Url.contratos)/eventos/$eid/contrato/marcar-firmado"
CheckStatus 'Firmar' $firma 200
Check 'FIRMADO con firmadoEn' ($firma.Body.estado -eq 'FIRMADO' -and $firma.Body.firmadoEn)
$firma2 = Api PATCH "$($Url.contratos)/eventos/$eid/contrato/marcar-firmado"
CheckStatus 'Firmar dos veces' $firma2 409
Check 'Mensaje: ya está FIRMADO' ($firma2.Body.message -match 'ya está FIRMADO') "$($firma2.Body.message)"
CheckStatus 'Firmar sin contrato' (Api PATCH "$($Url.contratos)/eventos/$([guid]::NewGuid())/contrato/marcar-firmado") 404

Write-Host '== Anexo de servicios adicionales'
$sinAd = Api GET "$($Url.contratos)/eventos/$eid/adicionales/anexo-pdf"
CheckStatus 'Anexo sin adicionales' $sinAd 409
Check 'Mensaje: no tiene servicios adicionales' ($sinAd.Body.message -match 'no tiene servicios adicionales') "$($sinAd.Body.message)"
CheckStatus 'Agregar decoración premium ×1' (Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'decoracion-premium'; cantidad = 1 }) 201
CheckStatus 'Agregar hora adicional ×2' (Api POST "$($Url.finanzas)/eventos/$eid/adicionales" @{ codigo = 'hora-extra'; cantidad = 2 }) 201
# Desde la Fase 5B los adicionales llegan a Contratos por Kafka (asíncrono): se espera hasta que el anexo tenga los dos.
for ($i = 0; $i -lt 10; $i++) {
    $anexo = DescargarPdf "$($Url.contratos)/eventos/$eid/adicionales/anexo-pdf" 'anexo-4C'
    if ($anexo.Status -eq 200 -and $anexo.Texto -match '(?m)^\s*Hora adicional de servicio\s*\d') { break }
    Start-Sleep -Seconds 2
}
CheckStatus 'Descargar PDF del anexo' $anexo 200
$a = $anexo.Texto
Check 'Anexo: cliente y evento con tildes' ($a -match (PatronDato 'Cliente' 'Prueba María José Peña Ñáñez') -and $a -match (PatronDato 'Evento' 'Prueba Quince años de Sofía Muñoz'))
# Tabla de adicionales: cada fila con servicio, cantidad, valor unitario y subtotal, en el orden en que se agregaron
$filaDeco = [regex]::Match($a, (PatronFilaAdicional 'Decoración premium' 1 '250.000,00 COP' '250.000,00 COP'))
$filaHora = [regex]::Match($a, (PatronFilaAdicional 'Hora adicional de servicio' 2 '120.000,00 COP' '240.000,00 COP'))
Check 'Anexo: fila 1 "Decoración premium · 1 · 250.000,00 COP · 250.000,00 COP"' ($filaDeco.Success)
Check 'Anexo: fila 2 "Hora adicional de servicio · 2 · 120.000,00 COP · 240.000,00 COP" (después de la decoración)' ($filaHora.Success -and $filaDeco.Success -and $filaHora.Index -gt $filaDeco.Index)
Check 'Anexo: total de adicionales 490.000,00 COP' ($a -match (PatronDato 'Total de servicios adicionales' '490.000,00 COP'))
Check 'Anexo: el contrato original sigue en 8.700.000,00 COP; con adicionales 9.190.000,00 COP' ($a -match [regex]::Escape('sigue siendo 8.700.000,00 COP') -and $a -match [regex]::Escape('9.190.000,00 COP en total'))
Check 'Anexo: sin caracteres mal codificados' ($a -notmatch 'Ã|Â|�|\{\{')
$despues = Api GET "$($Url.contratos)/eventos/$eid/contrato"
Check 'El contrato no cambió (FIRMADO, valor 8.700.000)' ($despues.Body.estado -eq 'FIRMADO' -and (Dinero $despues.Body.valorTotal) -eq 8700000)
$pdf2 = DescargarPdf "$($Url.contratos)/eventos/$eid/contrato/pdf" 'contrato-4C-despues'
Check 'El PDF del contrato sigue con 8.700.000,00 COP y sin los adicionales' ($pdf2.Texto -match (PatronDato 'Valor total del contrato' '8.700.000,00 COP') -and $pdf2.Texto -notmatch 'Decoración premium')
CheckStatus 'Anexo de evento sin contrato' (Api GET "$($Url.contratos)/eventos/$([guid]::NewGuid())/adicionales/anexo-pdf") 404

Write-Host '== Evento sin lugar'
$evS = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Boda sin lugar todavía'; clienteId = $cli.Body.id; fecha = $fechas[2]; tipoEvento = 'BODA'; cantidadAdultos = 100; cantidadNinos = 10; zona = 'RURAL' }
CheckStatus 'Crear evento sin lugar' $evS 201
CheckStatus 'Cotizarlo' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $evS.Body.id }) 201
$conS = Api POST "$($Url.contratos)/eventos/$($evS.Body.id)/contrato"
Check 'Contrato sin lugar -> 201, lugarNombre nulo, valor 13.200.000 (boda rural)' ($conS.Status -eq 201 -and -not $conS.Body.lugarNombre -and (Dinero $conS.Body.valorTotal) -eq 13200000) "$($conS.Status) $($conS.Texto)"
$pdfS = DescargarPdf "$($Url.contratos)/eventos/$($evS.Body.id)/contrato/pdf" 'contrato-4C-sin-lugar'
Check 'PDF: "Lugar: Por definir" y tipo "Boda"' ($pdfS.Texto -match (PatronDato 'Lugar' 'Por definir') -and $pdfS.Texto -match (PatronDato 'Tipo de evento' 'Boda'))

Write-Host '== Finanzas caído -> 503'
$evF = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Quince para probar 503'; clienteId = $cli.Body.id; fecha = $fechas[3]; tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 50; cantidadNinos = 5; zona = 'NEIVA' }
CheckStatus 'Crear evento' $evF 201
CheckStatus 'Cotizarlo' (Api POST "$($Url.finanzas)/cotizaciones" @{ eventoId = $evF.Body.id }) 201
Compose 'stop finanzas-service' | Out-Null
try {
    CheckStatus 'Crear contrato con Finanzas caído' (Api POST "$($Url.contratos)/eventos/$($evF.Body.id)/contrato") 503
    # Desde la Fase 5B el anexo sale de contrato_adicionales (no consulta a Finanzas): funciona con Finanzas caído.
    CheckStatus 'Anexo con Finanzas caído (sale de contrato_adicionales)' (Api GET "$($Url.contratos)/eventos/$eid/adicionales/anexo-pdf") 200
    CheckStatus 'El contrato existente se consulta sin Finanzas' (Api GET "$($Url.contratos)/eventos/$eid/contrato") 200
} finally {
    Compose 'start finanzas-service' | Out-Null
}
Check 'finanzas-service vuelve a healthy' ((EsperarHealthy 'finanzas-service') -eq 'healthy')
CheckStatus 'Crear contrato con Finanzas de vuelta' (Api POST "$($Url.contratos)/eventos/$($evF.Body.id)/contrato") 201

Write-Host '== Flujo de la Fase 3 (cotización y contrato con los cuerpos nuevos)'
$inicioFlujo = Get-Date
$flujo = FlujoFase3 $fechas[4] { param($e) @{ eventoId = $e.id } } $null
$cf = Api GET "$($Url.contratos)/eventos/$($flujo.EventoId)/contrato"
Check 'Flujo: contrato FIRMADO con la foto de los datos' ($cf.Body.estado -eq 'FIRMADO' -and $cf.Body.clienteNombre -eq $NombreClienteFlujo -and (Dinero $cf.Body.valorTotal) -eq 8700000)
Start-Sleep -Seconds 3
# -cmatch distingue mayúsculas: el nivel ERROR del log, no la palabra "Error" de ErrorHandlingDeserializer
$logC = LogsDesde 'contratos-service' $inicioFlujo
Check 'Contratos consumió EVENTO_CONFIRMADO sin errores ni DLT' ($logC -cnotmatch '\sERROR\s|DLT') ''
CheckStatus 'Sin token' (Api GET "$($Url.contratos)/contratos" -SinToken) 401

Resumen 'Verificación 4C'
