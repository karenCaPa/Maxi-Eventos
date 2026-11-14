import { useState } from 'react';
import { mutar } from '../api/cliente.js';
import { ASIGNAR_PROVEEDOR, PROVEEDORES } from '../api/consultas.js';
import { useConsulta } from '../api/useConsulta.js';
import Aviso from '../componentes/Aviso.jsx';
import Campo from '../componentes/Campo.jsx';
import InputMonto from '../componentes/InputMonto.jsx';
import { numeroONulo } from '../utils/ayudas.js';
import { CATEGORIAS_PROVEEDOR, ESTADOS_ASIGNACION, etiqueta } from '../utils/formato.js';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

/** Asignar un proveedor ACTIVO al evento (servicio y costo acordado). Duplicado o inactivo → 409 de Proveedores. */
export default function FormularioProveedor({ evento, alTerminar, cerrar }) {
  const proveedores = useConsulta(PROVEEDORES, { activo: true, page: 0, size: 100 });
  const [proveedorId, setProveedorId] = useState('');
  const [servicio, setServicio] = useState('');
  const [costo, setCosto] = useState('');
  const [estado, setEstado] = useState('PENDIENTE');
  const { ejecutar, enviando, error } = useAccion(async (mensaje) => {
    await alTerminar(mensaje);
    cerrar();
  });
  const lista = proveedores.data?.proveedores?.contenido ?? [];

  function enviar(e) {
    e.preventDefault();
    if (!proveedorId) return;
    ejecutar(async () => {
      await mutar(ASIGNAR_PROVEEDOR, {
        input: { eventoId: evento.id, proveedorId, servicio: servicio.trim(), costoAcordado: numeroONulo(costo) ?? 0, estado },
      });
      return `Proveedor asignado: ${lista.find((p) => p.id === proveedorId)?.nombre ?? ''}.`;
    });
  }

  const campos = error?.campos ?? {};
  return (
    <form onSubmit={enviar} noValidate aria-label="Asignar proveedor">
      {proveedores.error && <Aviso tipo="error">{proveedores.error.message}</Aviso>}
      <Campo etiqueta="Proveedor" error={campos.proveedorId}>
        {({ id, invalido, describedBy }) => (
          <select id={id} value={proveedorId} onChange={(e) => setProveedorId(e.target.value)} disabled={proveedores.cargando}
            aria-invalid={invalido} aria-describedby={describedBy}>
            <option value="">{proveedores.cargando ? 'Cargando proveedores…' : 'Elige un proveedor activo'}</option>
            {lista.map((p) => <option key={p.id} value={p.id}>{p.nombre} · {etiqueta(CATEGORIAS_PROVEEDOR, p.categoria)}</option>)}
          </select>
        )}
      </Campo>
      <Campo etiqueta="Servicio que presta en el evento" error={campos.servicioAsignado ?? campos.servicio}>
        {({ id, invalido, describedBy }) => (
          <input id={id} value={servicio} onChange={(e) => setServicio(e.target.value)} aria-invalid={invalido} aria-describedby={describedBy} />
        )}
      </Campo>
      <Campo etiqueta="Costo acordado (COP)" error={campos.costoAcordado}>
        {({ id, invalido, describedBy }) => (
          <InputMonto id={id} valor={costo} alCambiar={setCosto}
            aria-invalid={invalido} aria-describedby={describedBy} />
        )}
      </Campo>
      <Campo etiqueta="Estado de la asignación" error={campos.estado}>
        {({ id }) => (
          <select id={id} value={estado} onChange={(e) => setEstado(e.target.value)}>
            {['PENDIENTE', 'CONFIRMADO'].map((v) => <option key={v} value={v}>{ESTADOS_ASIGNACION[v]}</option>)}
          </select>
        )}
      </Campo>
      <ErrorAccion error={error} />
      <BotonAccion type="submit" enviando={enviando} textoEnviando="Asignando…" disabled={!proveedorId}>Asignar proveedor</BotonAccion>
    </form>
  );
}
