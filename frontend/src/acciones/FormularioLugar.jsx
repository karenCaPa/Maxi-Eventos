import { useState } from 'react';
import { mutar } from '../api/cliente.js';
import { ASIGNAR_LUGAR, LUGARES } from '../api/consultas.js';
import { useConsulta } from '../api/useConsulta.js';
import Aviso from '../componentes/Aviso.jsx';
import Campo from '../componentes/Campo.jsx';
import { etiqueta, formatearNumero, TIPOS_LUGAR } from '../utils/formato.js';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

/** Asignar (o cambiar) el lugar. Lista los lugares ACTIVOS; capacidad y estado los valida Eventos (409). */
export default function FormularioLugar({ evento, alTerminar, cerrar }) {
  const lugares = useConsulta(LUGARES, { estado: 'ACTIVO', page: 0, size: 100 });
  const [lugarId, setLugarId] = useState('');
  const { ejecutar, enviando, error } = useAccion(async (mensaje) => {
    await alTerminar(mensaje);
    cerrar();
  });
  const opciones = (lugares.data?.lugares?.contenido ?? []).filter((l) => l.id !== evento.lugarId);

  function enviar(e) {
    e.preventDefault();
    if (!lugarId) return;
    ejecutar(async () => {
      await mutar(ASIGNAR_LUGAR, { eventoId: evento.id, lugarId });
      return `Lugar asignado: ${opciones.find((l) => l.id === lugarId)?.nombre ?? ''}.`;
    });
  }

  return (
    <form onSubmit={enviar} aria-label="Asignar lugar">
      {lugares.error && <Aviso tipo="error">{lugares.error.message}</Aviso>}
      <Campo etiqueta="Lugar" error={error?.campos?.lugarId}>
        {({ id, invalido, describedBy }) => (
          <select id={id} value={lugarId} onChange={(e) => setLugarId(e.target.value)} disabled={lugares.cargando}
            aria-invalid={invalido} aria-describedby={describedBy}>
            <option value="">{lugares.cargando ? 'Cargando lugares…' : 'Elige un lugar activo'}</option>
            {opciones.map((l) => (
              <option key={l.id} value={l.id}>
                {l.nombre} · {etiqueta(TIPOS_LUGAR, l.tipo)} · capacidad {formatearNumero(l.capacidad)}
              </option>
            ))}
          </select>
        )}
      </Campo>
      <p className="texto-suave">Invitados del evento: {formatearNumero(evento.numInvitados)}</p>
      <ErrorAccion error={error} />
      <BotonAccion type="submit" enviando={enviando} textoEnviando="Asignando…" disabled={!lugarId}>Asignar lugar</BotonAccion>
    </form>
  );
}
