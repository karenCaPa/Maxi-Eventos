import { formatearMonto } from './formato.js';
import { resumenEvento } from './resumenEvento.js';

/**
 * Los 6 pasos del procedimiento para registrar un evento, en orden, y (Fase 11F) el paso 7 "Resumen", que no es un trámite:
 * reúne lo de los otros seis. "historial" y "todo" son vistas aparte (no son pasos).
 */
export const PASOS = [
  { id: 'evento', nombre: 'Evento' },
  { id: 'cotizacion', nombre: 'Cotización' },
  { id: 'contrato', nombre: 'Contrato' },
  { id: 'abonos', nombre: 'Abonos' },
  { id: 'confirmar', nombre: 'Confirmar' },
  { id: 'preparativos', nombre: 'Preparativos' },
  { id: 'resumen', nombre: 'Resumen' },
];
export const VISTAS = [...PASOS.map((p) => p.id), 'historial', 'todo'];

/**
 * Qué paso está hecho y cuál toca ahora, a partir de los MISMOS datos de la ficha (sin peticiones nuevas).
 * Es una guía: quien decide cada regla (firma, depósito, confirmación) es el servicio. Si la sección de la que sale un
 * dato falló (servicio caído), ese paso no se marca como hecho ni se inventa su estado.
 */
export function estadoPasos(evento, secciones = {}, { pasado = false } = {}) {
  const cotizado = Boolean(evento.cotizacion) && !secciones.cotizacion;
  const contratoCreado = Boolean(evento.contrato) && !secciones.contrato;
  const firmado = contratoCreado && evento.contrato.estado === 'FIRMADO';
  const balance = secciones.balance ? null : evento.balance;
  const deposito = balance?.depositoInicialRequerido;
  const faltante = balance && deposito !== null && deposito !== undefined ? Number(deposito) - Number(balance.montoAbonado) : null;
  const abonado = faltante !== null && faltante <= 0;
  const saldado = Boolean(balance) && Number(balance.saldoPendiente) <= 0;
  const confirmado = evento.estado === 'RESERVADO';
  const proveedores = secciones.proveedores ? 0 : (evento.proveedores?.contenido?.length ?? 0);
  const adicionales = secciones.serviciosAdicionales ? 0 : (evento.serviciosAdicionales?.length ?? 0);
  const lugar = Boolean(evento.lugar) && !secciones.lugar;
  const moneda = balance?.moneda ?? evento.cotizacion?.moneda ?? 'COP';

  const hecho = {
    evento: true, // si la ficha existe, el evento ya está creado y su fecha apartada
    cotizacion: cotizado,
    contrato: firmado,
    abonos: abonado,
    confirmar: confirmado,
    preparativos: confirmado && proveedores > 0,
  };
  // Fase 11F: el resumen está "hecho" cuando no queda ningún pendiente (con las mismas reglas que muestra el paso 7)
  const { pendientes } = resumenEvento(evento, secciones, { pasado });
  hecho.resumen = pendientes.length === 0;
  const detalle = {
    evento: lugar ? 'Con lugar' : 'Sin lugar aún',
    cotizacion: cotizado ? 'Lista' : 'Falta cotizar',
    contrato: firmado ? 'Firmado' : contratoCreado ? 'Pendiente de firma' : 'Sin contrato',
    abonos: abonado ? 'Cubierto' : faltante !== null ? `Faltan ${formatearMonto(faltante, moneda)}` : 'Sin abonos',
    confirmar: confirmado ? 'Listo' : pasado ? 'No se confirmó' : 'Por confirmar',
    preparativos: proveedores > 0 ? `${proveedores} proveedor${proveedores === 1 ? '' : 'es'}` : 'Sin proveedores',
    resumen: pendientes.length === 0 ? (pasado ? 'Sin pendientes' : 'Todo listo') : `${pendientes.length} pendiente${pendientes.length === 1 ? '' : 's'}`,
  };
  // El paso "actual" es el primero sin hacer entre los 5 primeros; con todo listo, toca organizar los preparativos.
  // Fase 11I: con la fecha pasada ya no hay paso que hacer: se abre el Resumen (que marca el saldo, si queda).
  const actualId = pasado ? 'resumen' : PASOS.slice(0, 5).find((p) => !hecho[p.id])?.id ?? 'preparativos';

  const guias = {
    evento: {
      titulo: 'Revisa el evento',
      intro: 'Revisa quién es el cliente y dónde será. El lugar se puede asignar ahora o más adelante.',
      items: [
        { texto: 'Cliente registrado', hecho: true },
        { texto: 'Fecha apartada (queda pendiente hasta confirmar)', hecho: true },
        { texto: 'Asignar el lugar (opcional)', hecho: lugar, opcional: true },
      ],
    },
    cotizacion: {
      titulo: 'Genera la cotización',
      intro: 'La cotización calcula el valor del evento según el tipo, los invitados y la zona.',
      items: [{ texto: 'Generar la cotización', hecho: cotizado }],
    },
    contrato: {
      titulo: 'Crea y firma el contrato',
      intro: 'El contrato toma los datos del evento y se firma con el cliente.',
      items: [
        { texto: 'Crear el contrato', hecho: contratoCreado, ir: !cotizado ? 'cotizacion' : undefined },
        { texto: 'Descargar el PDF y marcarlo como firmado cuando el cliente lo firme', hecho: firmado },
      ],
    },
    abonos: {
      titulo: 'Registra los abonos',
      intro: 'La fecha se reserva cuando el abono cubre el depósito. Registra cada pago que recibas.',
      items: [
        { texto: deposito !== null && deposito !== undefined ? `Abonar el depósito de ${formatearMonto(deposito, moneda)}` : 'Abonar el depósito de la reserva', hecho: abonado, ir: !cotizado ? 'cotizacion' : undefined },
        { texto: 'Registrar los demás abonos hasta saldar el total (opcional)', hecho: saldado, opcional: true },
      ],
    },
    confirmar: {
      titulo: 'Confirma el evento',
      intro: 'Con el contrato firmado y el depósito abonado, confirma: la fecha queda ocupada y se arma la logística.',
      items: [
        { texto: 'Cotización lista', hecho: cotizado, ir: 'cotizacion' },
        { texto: 'Contrato firmado', hecho: firmado, ir: 'contrato' },
        { texto: 'Depósito abonado', hecho: abonado, ir: 'abonos' },
        { texto: 'Evento confirmado', hecho: confirmado },
      ],
    },
    preparativos: {
      titulo: 'Organiza los preparativos',
      intro: confirmado
        ? 'El evento está confirmado. Organiza lo que falta: proveedores, servicios extras y la lista de compras.'
        : 'Puedes ir adelantando proveedores y servicios extras; la logística se calcula al confirmar el evento.',
      items: [
        { texto: 'Asignar proveedores', hecho: proveedores > 0 },
        { texto: 'Revisar la logística y la lista de compras', hecho: Boolean(evento.logistica) && !secciones.logistica },
        { texto: 'Agregar servicios adicionales (opcional)', hecho: adicionales > 0, opcional: true },
      ],
    },
    resumen: {
      titulo: 'Resumen del evento',
      intro: 'Todo el evento de un vistazo: qué está listo y qué falta. Cada tarjeta y cada pendiente llevan a su paso.',
      items: [],
    },
    historial: {
      titulo: 'Historial',
      intro: 'Los avisos enviados al cliente y el registro de todo lo que ha pasado con este evento.',
      items: [],
    },
    todo: {
      titulo: 'Todo el evento',
      intro: 'Estás viendo todas las secciones juntas. La guía te lleva paso a paso si prefieres ir ordenada.',
      items: [],
    },
  };

  return { hecho, detalle, actualId, guias, confirmable: firmado && abonado && !confirmado };
}
