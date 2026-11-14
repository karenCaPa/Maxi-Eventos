// Documentos GraphQL de la interfaz. Todos van al gateway (POST /graphql).

export const LOGIN = /* GraphQL */ `
  mutation Login($correo: String!, $password: String!) {
    login(correo: $correo, password: $password) { token tipoToken expiracionEnSegundos nombre rol }
  }
`;

export const EVENTOS = /* GraphQL */ `
  query Eventos($estado: EstadoEvento, $tipo: TipoEvento, $desde: Date, $hasta: Date, $page: Int, $size: Int, $sort: String) {
    eventos(estado: $estado, tipo: $tipo, desde: $desde, hasta: $hasta, page: $page, size: $size, sort: $sort) {
      pagina tamanoPagina totalElementos totalPaginas
      contenido {
        id nombreEvento fecha tipoEvento numInvitados zona estado
        cliente { id nombreCompleto }
      }
    }
  }
`;

/**
 * Fase 11I: sección "Eventos pasados" del listado (fecha anterior a hoy, la más reciente primero). Los mismos campos que
 * EVENTOS; otro nombre de operación para que se distinga en las pruebas y en el gateway.
 */
export const EVENTOS_PASADOS = /* GraphQL */ `
  query EventosPasados($estado: EstadoEvento, $tipo: TipoEvento, $desde: Date, $hasta: Date, $page: Int, $size: Int) {
    eventos(estado: $estado, tipo: $tipo, desde: $desde, hasta: $hasta, page: $page, size: $size, sort: "fecha,desc") {
      pagina tamanoPagina totalElementos totalPaginas
      contenido {
        id nombreEvento fecha tipoEvento numInvitados zona estado
        cliente { id nombreCompleto }
      }
    }
  }
`;

/** Fase 11E: bloque "Próximos 7 días" del listado (eventos RESERVADOS de hoy a hoy + 7). */
export const PROXIMOS_EVENTOS = /* GraphQL */ `
  query ProximosEventos($desde: Date!, $hasta: Date!) {
    eventos(estado: RESERVADO, desde: $desde, hasta: $hasta, page: 0, size: 20, sort: "fecha,asc") {
      contenido { id nombreEvento fecha tipoEvento }
    }
  }
`;

/** Fase 11H (campanita): solo el número, cada 30 s. */
export const NOTIFICACIONES_SIN_LEER = /* GraphQL */ `
  query NotificacionesSinLeer { notificacionesSinLeer }
`;
/** Fase 11H: las últimas, al abrir el panel (con el nombre del evento: el gateway lo resuelve en lote). */
export const MIS_NOTIFICACIONES = /* GraphQL */ `
  query MisNotificaciones($size: Int) {
    notificacionesSinLeer
    misNotificaciones(page: 0, size: $size) {
      contenido { id tipo asunto mensaje fechaCreacion leida eventoId evento { id nombreEvento } }
    }
  }
`;
export const MARCAR_NOTIFICACION_LEIDA = /* GraphQL */ `
  mutation MarcarNotificacionLeida($id: ID!) { marcarNotificacionLeida(id: $id) { id leida } }
`;
export const MARCAR_TODAS_LEIDAS = /* GraphQL */ `
  mutation MarcarTodasNotificacionesLeidas { marcarTodasNotificacionesLeidas }
`;

export const CLIENTES = /* GraphQL */ `
  query Clientes($nombre: String, $page: Int, $size: Int) {
    clientes(nombre: $nombre, page: $page, size: $size, sort: "nombreCompleto,asc") {
      totalElementos contenido { id nombreCompleto telefono correo }
    }
  }
`;

export const FECHAS_DISPONIBLES = /* GraphQL */ `
  query FechasDisponibles($desde: Date, $hasta: Date) {
    fechasDisponibles(desde: $desde, hasta: $hasta) { fecha diaSemana estado }
  }
`;

export const LUGARES = /* GraphQL */ `
  query Lugares($nombre: String, $tipo: String, $estado: EstadoLugar, $page: Int, $size: Int) {
    lugares(nombre: $nombre, tipo: $tipo, estado: $estado, page: $page, size: $size) {
      pagina totalElementos totalPaginas
      contenido { id nombre direccion tipo capacidad estado }
    }
  }
`;

export const PROVEEDORES = /* GraphQL */ `
  query Proveedores($categoria: String, $activo: Boolean, $page: Int, $size: Int) {
    proveedores(categoria: $categoria, activo: $activo, page: $page, size: $size) {
      pagina totalElementos totalPaginas
      contenido { id nombre categoria contacto activo }
    }
  }
`;

export const CATALOGO_ADICIONALES = /* GraphQL */ `
  query Catalogo { catalogoServiciosAdicionales { codigo nombre costoUnitario moneda } }
`;

// ---------------------------------------------------------------- mutations (la ficha se recarga completa después)
// 10B3: registro y mantenimiento de los directorios (delegan en lugares-service y proveedores-service)
const CAMPOS_LUGAR = 'id nombre direccion tipo capacidad estado';
const CAMPOS_PROVEEDOR = 'id nombre categoria contacto activo';
export const CREAR_LUGAR = /* GraphQL */ `
  mutation CrearLugar($input: LugarInput!) { crearLugar(input: $input) { ${CAMPOS_LUGAR} } }
`;
export const ACTUALIZAR_LUGAR = /* GraphQL */ `
  mutation ActualizarLugar($id: ID!, $input: LugarInput!) { actualizarLugar(id: $id, input: $input) { ${CAMPOS_LUGAR} } }
`;
export const CAMBIAR_ESTADO_LUGAR = /* GraphQL */ `
  mutation CambiarEstadoLugar($id: ID!, $estado: EstadoLugar!) { cambiarEstadoLugar(id: $id, estado: $estado) { ${CAMPOS_LUGAR} } }
`;
export const CREAR_PROVEEDOR = /* GraphQL */ `
  mutation CrearProveedor($input: ProveedorInput!) { crearProveedor(input: $input) { ${CAMPOS_PROVEEDOR} } }
`;
export const ACTUALIZAR_PROVEEDOR = /* GraphQL */ `
  mutation ActualizarProveedor($id: ID!, $input: ProveedorInput!) { actualizarProveedor(id: $id, input: $input) { ${CAMPOS_PROVEEDOR} } }
`;
export const CAMBIAR_ESTADO_PROVEEDOR = /* GraphQL */ `
  mutation CambiarEstadoProveedor($id: ID!, $activo: Boolean!) { cambiarEstadoProveedor(id: $id, activo: $activo) { ${CAMPOS_PROVEEDOR} } }
`;

export const CREAR_CLIENTE = /* GraphQL */ `
  mutation CrearCliente($input: ClienteInput!) { crearCliente(input: $input) { id nombreCompleto } }
`;
export const CREAR_EVENTO = /* GraphQL */ `
  mutation CrearEvento($input: CrearEventoInput!) { crearEvento(input: $input) { id nombreEvento estado } }
`;
export const GENERAR_COTIZACION = /* GraphQL */ `
  mutation GenerarCotizacion($eventoId: ID!) { generarCotizacion(eventoId: $eventoId) { id } }
`;
export const CREAR_CONTRATO = /* GraphQL */ `
  mutation CrearContrato($eventoId: ID!) { crearContrato(eventoId: $eventoId) { id estado } }
`;
export const FIRMAR_CONTRATO = /* GraphQL */ `
  mutation FirmarContrato($eventoId: ID!) { firmarContrato(eventoId: $eventoId) { id estado } }
`;
export const REGISTRAR_PAGO = /* GraphQL */ `
  mutation RegistrarPago($input: PagoInput!) { registrarPago(input: $input) { id monto } }
`;
export const CONFIRMAR_EVENTO = /* GraphQL */ `
  mutation ConfirmarEvento($id: ID!) { confirmarEvento(id: $id) { id estado } }
`;
/** Fase 11E: revisión manual de los recordatorios EVENTO_PROXIMO (solo ADMINISTRADOR). */
export const EJECUTAR_RECORDATORIOS = /* GraphQL */ `
  mutation EjecutarRecordatorios {
    ejecutarRecordatorios {
      revisados enviados yaEnviados
      recordatorios { eventoId nombreEvento diasRestantes resultado }
    }
  }
`;
export const CANCELAR_EVENTO = /* GraphQL */ `
  mutation CancelarEvento($id: ID!) { cancelarEvento(id: $id) { id estado } }
`;
export const ASIGNAR_LUGAR = /* GraphQL */ `
  mutation AsignarLugar($eventoId: ID!, $lugarId: ID!) { asignarLugar(eventoId: $eventoId, lugarId: $lugarId) { id lugarId } }
`;
export const ASIGNAR_PROVEEDOR = /* GraphQL */ `
  mutation AsignarProveedor($input: AsignarProveedorInput!) { asignarProveedor(input: $input) { id } }
`;
export const CONFIRMAR_ASIGNACION = /* GraphQL */ `
  mutation ConfirmarAsignacion($id: ID!) { cambiarEstadoAsignacion(id: $id, estado: CONFIRMADO) { id estado } }
`;
export const AGREGAR_ADICIONAL = /* GraphQL */ `
  mutation AgregarAdicional($eventoId: ID!, $codigo: String!, $cantidad: Int!) {
    agregarServicioAdicional(eventoId: $eventoId, codigo: $codigo, cantidad: $cantidad) { id }
  }
`;
// 10B3: ítems de la lista de compras (agregar y editar devuelven el ítem; quitar, el id)
const CAMPOS_ITEM = 'id nombreItem cantidadEstimada comprado origen nota';
export const AGREGAR_ITEM_COMPRA = /* GraphQL */ `
  mutation AgregarItemCompra($eventoId: ID!, $input: ItemCompraDatosInput!) {
    agregarItemCompra(eventoId: $eventoId, input: $input) { ${CAMPOS_ITEM} }
  }
`;
export const EDITAR_ITEM_COMPRA = /* GraphQL */ `
  mutation EditarItemCompra($eventoId: ID!, $itemId: ID!, $input: ItemCompraDatosInput!) {
    editarItemCompra(eventoId: $eventoId, itemId: $itemId, input: $input) { ${CAMPOS_ITEM} }
  }
`;
export const QUITAR_ITEM_COMPRA = /* GraphQL */ `
  mutation QuitarItemCompra($eventoId: ID!, $itemId: ID!) { quitarItemCompra(eventoId: $eventoId, itemId: $itemId) }
`;
export const ACTUALIZAR_COMPRAS = /* GraphQL */ `
  mutation ActualizarCompras($eventoId: ID!, $items: [ItemCompraInput!]!) {
    actualizarCompras(eventoId: $eventoId, items: $items) { totalElementos }
  }
`;

/**
 * FICHA DEL EVENTO: la vista única del requisito, en UNA sola query.
 * Cada relación la resuelve su servicio; si uno falla, el gateway devuelve el resto y un error con
 * path ["evento", "<relación>"], que la ficha muestra solo en esa sección.
 */
// Fase 11D: $verAuditoria = el usuario es ADMINISTRADOR. Con @include la auditoría ni se pide para el asistente, así que la
// ficha no muestra un error por una sección que su rol no puede ver (el gateway respondería FORBIDDEN en ese campo).
export const FICHA_EVENTO = /* GraphQL */ `
  query FichaEvento($id: ID!, $verAuditoria: Boolean!) {
    evento(id: $id) {
      id nombreEvento fecha tipoEvento numInvitados cantidadAdultos cantidadNinos zona estado fechaCreacion
      clienteId lugarId
      cliente { id nombreCompleto telefono correo direccion }
      cotizacion {
        id valorBase totalSinAdicionales total depositoInicialRequerido moneda invitados
        cantidadAdultos cantidadNinos adultosAdicionales ninosAdicionales
        valorAdultosAdicionales valorNinosAdicionales subtotal recargoZona creadoEn
      }
      contrato {
        id estado fechaFirma creadoEn clienteNombre fechaEvento tipoEvento
        cantidadAdultos cantidadNinos lugarNombre valorTotal moneda
      }
      pagos(page: 0, size: 50) {
        totalElementos
        contenido { id monto moneda metodoPago fecha estado }
      }
      balance { totalCotizado montoAbonado saldoPendiente valorCotizacion adicionales depositoInicialRequerido moneda }
      lugar { id nombre direccion tipo capacidad estado }
      proveedores(page: 0, size: 50) {
        totalElementos
        contenido { id proveedorId servicio categoria costoAcordado estado proveedor { nombre contacto } }
      }
      serviciosAdicionales { id codigo nombre cantidad valorUnitario subtotal fecha }
      logistica {
        meseros personalCocina vehiculos
        compras(page: 0, size: 100) {
          totalElementos
          contenido { id nombreItem cantidadEstimada comprado origen nota }
        }
      }
      notificaciones(page: 0, size: 20) {
        totalElementos
        contenido { id tipo destinatario asunto mensaje estado fechaCreacion }
      }
      auditoria(page: 0, size: 20) @include(if: $verAuditoria) {
        totalElementos
        contenido { idEvento tipo servicioOrigen topico particion offset fechaOcurrencia fechaRecepcion payload }
      }
    }
  }
`;
