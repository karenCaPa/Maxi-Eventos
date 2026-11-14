import { useState } from 'react';
import { mutar } from '../api/cliente.js';
import { AGREGAR_ADICIONAL, CATALOGO_ADICIONALES } from '../api/consultas.js';
import { useConsulta } from '../api/useConsulta.js';
import Aviso from '../componentes/Aviso.jsx';
import Campo from '../componentes/Campo.jsx';
import { numeroONulo } from '../utils/ayudas.js';
import { formatearMonto } from '../utils/formato.js';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

/** Agregar un servicio adicional del catálogo de Finanzas (actualiza el total; Logística y Contratos reaccionan por Kafka). */
export default function FormularioAdicional({ evento, alTerminar, cerrar }) {
  const catalogo = useConsulta(CATALOGO_ADICIONALES, {});
  const [codigo, setCodigo] = useState('');
  const [cantidad, setCantidad] = useState('1');
  const { ejecutar, enviando, error } = useAccion(async (mensaje) => {
    await alTerminar(mensaje);
    cerrar();
  });
  const servicios = catalogo.data?.catalogoServiciosAdicionales ?? [];

  function enviar(e) {
    e.preventDefault();
    if (!codigo) return;
    ejecutar(async () => {
      await mutar(AGREGAR_ADICIONAL, { eventoId: evento.id, codigo, cantidad: numeroONulo(cantidad) ?? 0 });
      return `Servicio adicional agregado: ${servicios.find((s) => s.codigo === codigo)?.nombre ?? codigo}. Logística y el anexo se actualizan en unos segundos.`;
    });
  }

  return (
    <form onSubmit={enviar} noValidate aria-label="Agregar servicio adicional">
      {catalogo.error && <Aviso tipo="error">{catalogo.error.message}</Aviso>}
      <Campo etiqueta="Servicio" error={error?.campos?.codigo}>
        {({ id, invalido, describedBy }) => (
          <select id={id} value={codigo} onChange={(e) => setCodigo(e.target.value)} disabled={catalogo.cargando}
            aria-invalid={invalido} aria-describedby={describedBy}>
            <option value="">{catalogo.cargando ? 'Cargando catálogo…' : 'Elige un servicio'}</option>
            {servicios.map((s) => <option key={s.codigo} value={s.codigo}>{s.nombre} · {formatearMonto(s.costoUnitario, s.moneda ?? 'COP')}</option>)}
          </select>
        )}
      </Campo>
      <Campo etiqueta="Cantidad" error={error?.campos?.cantidad}>
        {({ id, invalido, describedBy }) => (
          <input id={id} type="number" inputMode="numeric" step="1" value={cantidad} onChange={(e) => setCantidad(e.target.value)}
            aria-invalid={invalido} aria-describedby={describedBy} />
        )}
      </Campo>
      <ErrorAccion error={error} />
      <BotonAccion type="submit" enviando={enviando} textoEnviando="Agregando…" disabled={!codigo}>Agregar servicio</BotonAccion>
    </form>
  );
}
