# Verificación en Docker de la subfase 4A (Auth, Lugares, Proveedores, Logística) + flujo de la Fase 3.
. "$PSScriptRoot\comun.ps1"
AsegurarEntornoArrancado

Write-Host '== Auth'
Redis 'for k in $(redis-cli --scan --pattern "auth:token:*"); do redis-cli DEL "$k" >/dev/null; done' | Out-Null
$login = Login
CheckStatus 'Login' $login 200
$campos = @($login.Body.PSObject.Properties.Name)
# Fase 11D: además nombre y rol
Check 'LoginResponse = token, tipoToken, expiracionEnSegundos, nombre, rol' ((($campos | Sort-Object) -join ',') -eq 'expiracionEnSegundos,nombre,rol,tipoToken,token') "campos=$($campos -join ',')"
Check 'rol = ADMINISTRADOR' ($login.Body.rol -eq 'ADMINISTRADOR') "rol=$($login.Body.rol)"
Check 'tipoToken = Bearer' ($login.Body.tipoToken -eq 'Bearer')
$carga = $login.Body.token.Split('.')[1].Replace('-', '+').Replace('_', '/'); while ($carga.Length % 4) { $carga += '=' }
$jwt = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($carga)) | ConvertFrom-Json
Check 'expiracionEnSegundos = exp - iat del JWT' ([long]$login.Body.expiracionEnSegundos -eq ([long]$jwt.exp - [long]$jwt.iat)) "exp-iat=$($jwt.exp - $jwt.iat)"
$tokensRedis = (Redis 'redis-cli --scan --pattern "auth:token:*" | wc -l').Trim()
Check 'El token ya no se guarda en Redis' ($tokensRedis -eq '0') "claves=$tokensRedis"
CheckStatus 'Contraseña incorrecta' (Api POST "$($Url.auth)/auth/login" @{ correo = 'otra@ejemplo.com'; password = 'x' } -SinToken) 401

Write-Host '== Lugares'
$s = Sufijo
$lugar = Api POST "$($Url.lugares)/lugares" @{ nombre = "Prueba Finca La Ceiba $s"; direccion = 'Vereda El Caguán, Neiva'; tipo = 'FINCA'; capacidad = 300 }
CheckStatus 'Crear lugar' $lugar 201
Check 'Lugar nuevo ACTIVO' ($lugar.Body.estado -eq 'ACTIVO') "estado=$($lugar.Body.estado)"
$lid = $lugar.Body.id
$desde = Get-Date
CheckStatus 'GET lugar (1ª, MISS)' (Api GET "$($Url.lugares)/lugares/$lid") 200
$g2 = Api GET "$($Url.lugares)/lugares/$lid"
Check 'GET lugar (2ª, HIT) con estado ACTIVO' ($g2.Status -eq 200 -and $g2.Body.estado -eq 'ACTIVO')
$valorRedis = Redis "redis-cli GET 'maxi:lugares:lugares::$lid'"
Check 'Valor en Redis incluye estado' ("$valorRedis" -match '"estado":"ACTIVO"') "$valorRedis"
$activos1 = Api GET "$($Url.lugares)/lugares?estado=ACTIVO&size=100&nombre=$s"
Check 'Filtro estado=ACTIVO incluye el lugar' (@($activos1.Body.contenido.id) -contains $lid)
$porTipo = Api GET "$($Url.lugares)/lugares?tipo=finca&nombre=$s"
Check 'Filtro tipo=finca (minúsculas) encuentra FINCA' ($porTipo.Status -eq 200 -and @($porTipo.Body.contenido.id) -contains $lid)
CheckStatus 'Filtro estado inválido' (Api GET "$($Url.lugares)/lugares?estado=CERRADO") 400
CheckStatus 'sort=estado permitido' (Api GET "$($Url.lugares)/lugares?sort=estado,desc") 200

$fechas = FechasLibres
Check 'Hay al menos 4 fines de semana libres' ($fechas.Count -ge 4) "fechas=$($fechas.Count)"
$cli = CrearCliente 'Prueba Andrés Gómez Peña'
CheckStatus 'Crear cliente' $cli 201
$ev1 = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Boda Gómez y Ramírez'; clienteId = $cli.Body.id; fecha = $fechas[0]; tipoEvento = 'BODA'; cantidadAdultos = 120; cantidadNinos = 10; zona = 'RURAL'; lugarId = $lid }
CheckStatus 'Eventos: crear evento con lugar ACTIVO' $ev1 201

$inac = Api PATCH "$($Url.lugares)/lugares/$lid/estado" @{ estado = 'INACTIVO' }
CheckStatus 'PATCH estado INACTIVO' $inac 200
Check 'Respuesta con estado INACTIVO' ($inac.Body.estado -eq 'INACTIVO')
$g3 = Api GET "$($Url.lugares)/lugares/$lid"
Check 'GET tras PATCH devuelve INACTIVO (caché actualizada)' ($g3.Body.estado -eq 'INACTIVO') "estado=$($g3.Body.estado)"
$activos2 = Api GET "$($Url.lugares)/lugares?estado=ACTIVO&size=100&nombre=$s"
Check 'Listado ACTIVO ya no lo incluye (caché del listado limpiada)' (-not (@($activos2.Body.contenido.id) -contains $lid))
$inactivos = Api GET "$($Url.lugares)/lugares?estado=INACTIVO&size=100"
Check 'Filtro estado=INACTIVO lo incluye' (@($inactivos.Body.contenido.id) -contains $lid)
CheckStatus 'PATCH estado sin cuerpo válido' (Api PATCH "$($Url.lugares)/lugares/$lid/estado" @{ estado = 'X' }) 400
CheckStatus 'PATCH estado de lugar inexistente' (Api PATCH "$($Url.lugares)/lugares/$([guid]::NewGuid())/estado" @{ estado = 'ACTIVO' }) 404
# Log de caché (Fase 11I: robusto e igual de exigente —o más—). Antes se leía el log UNA vez y bastaba cualquier "Caché PUT"
# (lo cumplía hasta el PUT del primer GET, el MISS) y cualquier EVICT/CLEAR. Falló de forma intermitente (las dos a la vez,
# tras Newman y varios scripts seguidos): la lectura de `docker compose logs` puede llegar incompleta con la máquina cargada.
# Ahora: (1) se reintenta la lectura hasta 30 s, siempre con la misma ventana ($desde, antes del primer GET), como en 9A; y
# (2) se exige el ORDEN dentro del log del contenedor (no depende de relojes): después del "Caché HIT" del segundo GET de
# ESTE lugar, un "Caché PUT cache=lugares clave=<id>" (el @CachePut del PATCH) y un CLEAR o EVICT de "lugares-listado".
$patronHit = "Caché HIT\s+cache=lugares clave=$lid"
$patronPut = "Caché PUT\s+cache=lugares clave=$lid"
$patronListado = 'Caché (CLEAR|EVICT) cache=lugares-listado'
$fin = (Get-Date).AddSeconds(30); $lecturas = 0
do {
    $lecturas++
    $logL = LogsDesde 'lugares-service' $desde
    $trasHit = ''
    $m = [regex]::Match($logL, $patronHit)
    if ($m.Success) { $trasHit = $logL.Substring($m.Index + $m.Length) }
    $hayPut = $trasHit -match $patronPut
    $hayListado = $trasHit -match $patronListado
    if (-not ($hayPut -and $hayListado)) { Start-Sleep -Seconds 2 }
} while (-not ($hayPut -and $hayListado) -and (Get-Date) -lt $fin)
# Si falla, el detalle muestra las líneas de caché leídas (incluidos los WARN "Caché no disponible") para ver la causa.
$lineasCache = (($logL -split "`n") | Where-Object { $_ -match 'Caché' } | ForEach-Object { ($_ -replace '^.*?\|\s*', '').Trim() } |
    Select-Object -Last 12) -join ' / '
$detalle = if ($hayPut -and $hayListado) { "$lecturas lectura(s) del log" } else { "$lecturas lecturas en 30 s; HIT previo: $($m.Success); líneas: $lineasCache" }
Check 'Log de caché: PUT del lugar tras el cambio de estado (después del HIT)' $hayPut $detalle
Check 'Log de caché: CLEAR/EVICT del listado tras el cambio de estado (después del HIT)' $hayListado $detalle

$ev2 = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Boda con lugar inactivo'; clienteId = $cli.Body.id; fecha = $fechas[1]; tipoEvento = 'BODA'; cantidadAdultos = 50; cantidadNinos = 0; zona = 'NEIVA'; lugarId = $lid }
CheckStatus 'Eventos (sin cambios): crear evento con lugar INACTIVO' $ev2 409
Check 'Mensaje menciona INACTIVO' ($ev2.Body.message -match 'INACTIVO') "$($ev2.Body.message)"
$ev3 = Api POST "$($Url.eventos)/eventos" @{ nombreEvento = 'Prueba Quince sin lugar'; clienteId = $cli.Body.id; fecha = $fechas[1]; tipoEvento = 'QUINCE_ANOS'; cantidadAdultos = 60; cantidadNinos = 20; zona = 'NEIVA' }
CheckStatus 'Crear evento sin lugar' $ev3 201
CheckStatus 'Eventos: asignar lugar INACTIVO' (Api PATCH "$($Url.eventos)/eventos/$($ev3.Body.id)/lugar" @{ lugarId = $lid }) 409
CheckStatus 'Reactivar lugar' (Api PATCH "$($Url.lugares)/lugares/$lid/estado" @{ estado = 'ACTIVO' }) 200
CheckStatus 'Eventos: asignar lugar reactivado' (Api PATCH "$($Url.eventos)/eventos/$($ev3.Body.id)/lugar" @{ lugarId = $lid }) 200
$put = Api PUT "$($Url.lugares)/lugares/$lid" @{ nombre = "Prueba Finca La Ceiba $s"; direccion = 'Vereda El Caguán km 3'; tipo = 'FINCA'; capacidad = 320 }
Check 'PUT no cambia el estado' ($put.Status -eq 200 -and $put.Body.estado -eq 'ACTIVO')

Write-Host '== Proveedores'
$nombreP = "Prueba Sonido Andino $s"
$p1 = Api POST "$($Url.proveedores)/proveedores" @{ nombre = $nombreP; categoria = 'SONIDO'; contacto = '3101234567' }
CheckStatus 'Crear proveedor' $p1 201
$dup = Api POST "$($Url.proveedores)/proveedores" @{ nombre = "  $($nombreP.ToUpper()) "; categoria = 'SONIDO'; contacto = '3100000000' }
CheckStatus 'Crear duplicado (mayúsculas y espacios)' $dup 409
Check 'Mensaje de duplicado' ($dup.Body.message -like 'Ya existe un proveedor con el nombre*') "$($dup.Body.message)"
$p2 = Api POST "$($Url.proveedores)/proveedores" @{ nombre = "Prueba Flores del Huila $s"; categoria = 'Floristería'; contacto = '3201234567' }
CheckStatus 'Crear segundo proveedor' $p2 201
CheckStatus 'Actualizar con el nombre de otro proveedor' (Api PUT "$($Url.proveedores)/proveedores/$($p2.Body.id)" @{ nombre = $nombreP.ToLower(); categoria = 'Floristería'; contacto = '3201234567' }) 409
$propio = Api PUT "$($Url.proveedores)/proveedores/$($p1.Body.id)" @{ nombre = $nombreP; categoria = 'SONIDO'; contacto = '3109999999' }
Check 'Actualizar conservando su propio nombre -> 200' ($propio.Status -eq 200 -and $propio.Body.contacto -eq '3109999999')
$cat = Api GET "$($Url.proveedores)/proveedores?categoria=floristería&size=100"
Check 'Filtro categoria (minúsculas, con tilde)' ($cat.Status -eq 200 -and @($cat.Body.contenido.id) -contains $p2.Body.id -and -not (@($cat.Body.contenido.id) -contains $p1.Body.id))
CheckStatus 'Desactivar segundo proveedor' (Api PATCH "$($Url.proveedores)/proveedores/$($p2.Body.id)/estado" @{ activo = $false }) 200
$inactP = Api GET "$($Url.proveedores)/proveedores?activo=false&size=100"
Check 'Filtro activo=false lo incluye' (@($inactP.Body.contenido.id) -contains $p2.Body.id -and -not (@($inactP.Body.contenido.id) -contains $p1.Body.id))
$actP = Api GET "$($Url.proveedores)/proveedores?activo=true&categoria=SONIDO&size=100"
Check 'Filtro activo=true + categoria' (@($actP.Body.contenido.id) -contains $p1.Body.id)

$eid = $ev1.Body.id
$vacia = Api GET "$($Url.proveedores)/proveedores/eventos/$($ev3.Body.id)"
Check 'Asignaciones de evento existente sin asignaciones -> 200 vacía' ($vacia.Status -eq 200 -and $vacia.Body.totalElementos -eq 0) "$($vacia.Status) $($vacia.Texto)"
CheckStatus 'Asignaciones de evento inexistente' (Api GET "$($Url.proveedores)/proveedores/eventos/$([guid]::NewGuid())") 404
CheckStatus 'Asignar con costoAcordado 0' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eid; proveedorId = $p1.Body.id; servicioAsignado = 'Sonido'; costoAcordado = 0 }) 400
CheckStatus 'Asignar con estado inválido' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eid; proveedorId = $p1.Body.id; servicioAsignado = 'Sonido'; costoAcordado = 800000; estado = 'APROBADO' }) 400
$asig = Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eid; proveedorId = $p1.Body.id; servicioAsignado = 'Sonido y luces'; costoAcordado = 800000 }
CheckStatus 'Asignar sin estado' $asig 201
Check 'Asignación queda PENDIENTE' ($asig.Body.estado -eq 'PENDIENTE')
CheckStatus 'Asignación duplicada' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eid; proveedorId = $p1.Body.id; servicioAsignado = 'Sonido y luces'; costoAcordado = 800000 }) 409
CheckStatus 'Asignar con estado CONFIRMADO' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eid; proveedorId = $p1.Body.id; servicioAsignado = 'DJ'; costoAcordado = 400000; estado = 'CONFIRMADO' }) 201
CheckStatus 'Asignar proveedor inactivo' (Api POST "$($Url.proveedores)/proveedores/eventos" @{ eventoId = $eid; proveedorId = $p2.Body.id; servicioAsignado = 'Flores'; costoAcordado = 300000 }) 409
$conAsig = Api GET "$($Url.proveedores)/proveedores/eventos/$eid"
Check 'Asignaciones del evento -> 2' ($conAsig.Status -eq 200 -and $conAsig.Body.totalElementos -eq 2)
$delCon = Api DELETE "$($Url.proveedores)/proveedores/$($p1.Body.id)"
CheckStatus 'DELETE proveedor con asignaciones' $delCon 409
Check 'Mensaje sugiere inactivar' ($delCon.Body.message -match 'Inactívalo') "$($delCon.Body.message)"
CheckStatus 'DELETE proveedor sin asignaciones' (Api DELETE "$($Url.proveedores)/proveedores/$($p2.Body.id)") 204
CheckStatus 'GET proveedor eliminado (caché borrada)' (Api GET "$($Url.proveedores)/proveedores/$($p2.Body.id)") 404
CheckStatus 'DELETE proveedor inexistente' (Api DELETE "$($Url.proveedores)/proveedores/$([guid]::NewGuid())") 404

Write-Host '   (deteniendo eventos-service para probar el 503)'
Compose 'stop eventos-service' | Out-Null
CheckStatus 'Asignaciones con Eventos caído' (Api GET "$($Url.proveedores)/proveedores/eventos/$eid") 503
Compose 'start eventos-service' | Out-Null
Check 'eventos-service vuelve a healthy' ((EsperarHealthy 'eventos-service') -eq 'healthy')

Write-Host '== Flujo de la Fase 3 + Logística'
$flujo = FlujoFase3 $fechas[2]
$compras = Api GET "$($Url.logistica)/eventos/$($flujo.EventoId)/compras"
CheckStatus 'GET compras' $compras 200
$item = $compras.Body.contenido[0]
$putC = Api PUT "$($Url.logistica)/eventos/$($flujo.EventoId)/compras" @(@{ id = $item.id; comprado = $true })
CheckStatus 'PUT compras' $putC 200
$marcado = @($putC.Body.contenido | Where-Object { $_.id -eq $item.id })
Check 'PUT devuelve la página con el ítem comprado' ($putC.Body.totalElementos -eq $compras.Body.totalElementos -and $marcado.Count -eq 1 -and $marcado[0].comprado -eq $true)
$putS = Api PUT "$($Url.logistica)/eventos/$($flujo.EventoId)/compras?size=2&sort=nombreItem,desc" @(@{ id = $item.id; comprado = $true })
Check 'PUT respeta size y sort' ($putS.Status -eq 200 -and $putS.Body.tamanoPagina -eq 2 -and @($putS.Body.contenido).Count -le 2)
$otro = @($compras.Body.contenido)[1]
$ajeno = Api PUT "$($Url.logistica)/eventos/$($flujo.EventoId)/compras" @(@{ id = $otro.id; comprado = $true }, @{ id = [guid]::NewGuid(); comprado = $true })
CheckStatus 'PUT con un ítem ajeno' $ajeno 404
$tras = Api GET "$($Url.logistica)/eventos/$($flujo.EventoId)/compras"
Check 'El 404 no aplica cambios (el otro ítem sigue sin comprar)' (-not (@($tras.Body.contenido | Where-Object { $_.id -eq $otro.id })[0].comprado))
CheckStatus 'PUT con lista vacía' (Api PUT "$($Url.logistica)/eventos/$($flujo.EventoId)/compras" '[]') 400
CheckStatus 'PUT en evento sin logística' (Api PUT "$($Url.logistica)/eventos/$([guid]::NewGuid())/compras" @(@{ id = $item.id; comprado = $true })) 404
Check 'Mensajes de error sin detalles internos' (-not ($ajeno.Texto -match 'Exception|org\.|SQL|hibernate'))
CheckStatus 'Sin token' (Api GET "$($Url.lugares)/lugares" -SinToken) 401

Resumen 'Verificación 4A'
