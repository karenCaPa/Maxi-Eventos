// Respuesta de la ficha con los datos de demostración de la Fase 9A ("Quince años de Lucía", RESERVADO).
export function fichaDemo() {
  return {
    id: 'af79495b-9065-4b83-b08e-83ca7edabc5a',
    nombreEvento: 'Quince años de Lucía',
    fecha: '2026-12-05',
    tipoEvento: 'QUINCE_ANOS',
    numInvitados: 105,
    cantidadAdultos: 90,
    cantidadNinos: 15,
    zona: 'NEIVA',
    estado: 'RESERVADO',
    fechaCreacion: '2026-10-30T15:00:00.000Z',
    clienteId: 'e146c96a-919d-4e82-b1c9-ebda1bca8b92',
    lugarId: '246c1c43-72c7-4d34-bafe-6beb822562e3',
    cliente: { id: 'e146c96a-919d-4e82-b1c9-ebda1bca8b92', nombreCompleto: 'Lucía Fernández Ospina', telefono: '3101234567', correo: 'lucia@ejemplo.com', direccion: 'Neiva' },
    cotizacion: {
      id: 'c1', valorBase: 7000000, totalSinAdicionales: 8700000, total: 8880000, depositoInicialRequerido: 2610000, moneda: 'COP',
      invitados: 105, cantidadAdultos: 90, cantidadNinos: 15, adultosAdicionales: 10, ninosAdicionales: 5,
      valorAdultosAdicionales: 1200000, valorNinosAdicionales: 500000, subtotal: 8700000, recargoZona: 0, creadoEn: '2026-10-30T15:01:00.000Z',
    },
    contrato: {
      id: 'f97377d7-d146-4912-a8cd-d3e387921905', estado: 'FIRMADO', fechaFirma: '2026-10-30T15:05:00.000Z', creadoEn: '2026-10-30T15:02:00.000Z',
      clienteNombre: 'Lucía Fernández Ospina', fechaEvento: '2026-12-05', tipoEvento: 'QUINCE_ANOS', cantidadAdultos: 90, cantidadNinos: 15,
      lugarNombre: 'Salón de Eventos Los Ocobos', valorTotal: 8700000, moneda: 'COP',
    },
    pagos: {
      totalElementos: 2,
      contenido: [
        { id: 'p1', monto: 1044000, moneda: 'COP', metodoPago: 'TRANSFERENCIA', fecha: '2026-10-30T15:06:00.000Z', estado: null },
        { id: 'p2', monto: 1566000, moneda: 'COP', metodoPago: 'EFECTIVO', fecha: '2026-10-30T15:07:00.000Z', estado: null },
      ],
    },
    balance: { totalCotizado: 8880000, montoAbonado: 2610000, saldoPendiente: 6270000, valorCotizacion: 8700000, adicionales: 180000, depositoInicialRequerido: 2610000, moneda: 'COP' },
    lugar: { id: '246c1c43-72c7-4d34-bafe-6beb822562e3', nombre: 'Salón de Eventos Los Ocobos', direccion: 'Neiva', tipo: 'SALON', capacidad: 150, estado: 'ACTIVO' },
    proveedores: {
      totalElementos: 1,
      contenido: [{ id: 'a1', proveedorId: 'pr1', servicio: 'Sonido y luces', categoria: 'SONIDO', costoAcordado: 900000, estado: 'CONFIRMADO', proveedor: { nombre: 'Sonido y Luces del Huila', contacto: '3200000000' } }],
    },
    serviciosAdicionales: [{ id: 's1', codigo: 'transporte-adicional', nombre: 'Transporte adicional', cantidad: 1, valorUnitario: 180000, subtotal: 180000, fecha: '2026-10-30T15:10:00.000Z' }],
    logistica: {
      meseros: 7, personalCocina: 4, vehiculos: 3,
      compras: { totalElementos: 2, contenido: [
        { id: 'i1', nombreItem: 'Bebidas', cantidadEstimada: '210 unidades', comprado: true, origen: 'CALCULADO', nota: null },
        { id: 'i2', nombreItem: 'Servilletas', cantidadEstimada: '315 unidades', comprado: false, origen: 'CALCULADO', nota: null },
      ] },
    },
    notificaciones: { totalElementos: 1, contenido: [{ id: 'n1', tipo: 'EVENTO_CONFIRMADO', destinatario: 'cliente:e146', asunto: 'Evento confirmado: Quince años de Lucía', mensaje: 'Tu evento quedó reservado.', estado: 'GENERADA', fechaCreacion: '2026-10-30T15:08:00.000Z' }] },
    auditoria: { totalElementos: 1, contenido: [{ idEvento: 'k1', tipo: 'EVENTO_CONFIRMADO', servicioOrigen: 'eventos-service', topico: 'eventos.confirmados', particion: 1, offset: 0, fechaOcurrencia: '2026-10-30T15:08:00.000Z', fechaRecepcion: '2026-10-30T15:08:01.000Z', payload: '{"tipo":"EVENTO_CONFIRMADO"}' }] },
  };
}
