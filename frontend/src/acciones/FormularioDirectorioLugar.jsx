import { useState } from 'react';
import { mutar } from '../api/cliente.js';
import { ACTUALIZAR_LUGAR, CREAR_LUGAR } from '../api/consultas.js';
import Campo from '../componentes/Campo.jsx';
import { TIPOS_LUGAR } from '../utils/formato.js';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

/**
 * Crear un lugar (sin "lugar") o editarlo (con "lugar"). Las reglas (obligatorios, capacidad > 0) son de lugares-service:
 * sus errores se muestran junto a cada campo. En la interfaz solo se evita enviar una capacidad que no es un número entero
 * (el schema la declara Int y la rechazaría sin decir el campo). El estado no se edita aquí (botón Activar / Desactivar).
 */
export default function FormularioDirectorioLugar({ lugar, alGuardar, cerrar }) {
  const [datos, setDatos] = useState(() => ({
    nombre: lugar?.nombre ?? '', direccion: lugar?.direccion ?? '', tipo: lugar?.tipo ?? '',
    capacidad: lugar?.capacidad != null ? String(lugar.capacidad) : '',
  }));
  const [errorLocal, setErrorLocal] = useState({});
  const { ejecutar, enviando, error } = useAccion(async (mensaje) => {
    await alGuardar(mensaje);
    cerrar();
  });
  const cambiar = (campo) => (e) => setDatos((d) => ({ ...d, [campo]: e.target.value }));

  function enviar(e) {
    e.preventDefault();
    const capacidad = datos.capacidad.trim();
    if (capacidad !== '' && !/^-?\d+$/.test(capacidad)) {
      setErrorLocal({ capacidad: 'debe ser un número entero' });
      return;
    }
    setErrorLocal({});
    const input = {
      nombre: datos.nombre.trim(), direccion: datos.direccion.trim(), tipo: datos.tipo,
      capacidad: capacidad === '' ? null : Number(capacidad),
    };
    ejecutar(async () => {
      if (lugar) {
        const r = await mutar(ACTUALIZAR_LUGAR, { id: lugar.id, input });
        return `Lugar «${r.actualizarLugar.nombre}» actualizado.`;
      }
      const r = await mutar(CREAR_LUGAR, { input });
      return `Lugar «${r.crearLugar.nombre}» registrado.`;
    });
  }

  const campos = { ...(error?.campos ?? {}), ...errorLocal };
  const tipoDesconocido = datos.tipo && !TIPOS_LUGAR[datos.tipo];
  return (
    <form onSubmit={enviar} noValidate aria-label={lugar ? `Editar el lugar ${lugar.nombre}` : 'Nuevo lugar'}>
      <div className="rejilla-campos">
        <Campo etiqueta="Nombre" error={campos.nombre}>
          {({ id, invalido, describedBy }) => <input id={id} value={datos.nombre} onChange={cambiar('nombre')} autoComplete="off"
            aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
        <Campo etiqueta="Dirección" error={campos.direccion}>
          {({ id, invalido, describedBy }) => <input id={id} value={datos.direccion} onChange={cambiar('direccion')} autoComplete="off"
            aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
        <Campo etiqueta="Tipo" error={campos.tipo}>
          {({ id, invalido, describedBy }) => (
            <select id={id} value={datos.tipo} onChange={cambiar('tipo')} aria-invalid={invalido} aria-describedby={describedBy}>
              <option value="">Elige un tipo</option>
              {Object.entries(TIPOS_LUGAR).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
              {tipoDesconocido && <option value={datos.tipo}>{datos.tipo}</option>}
            </select>
          )}
        </Campo>
        <Campo etiqueta="Capacidad (personas)" error={campos.capacidad}>
          {({ id, invalido, describedBy }) => <input id={id} type="number" inputMode="numeric" min="1" step="1" value={datos.capacidad}
            onChange={cambiar('capacidad')} aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
      </div>
      <ErrorAccion error={error} />
      <div className="acciones-formulario">
        <BotonAccion type="submit" enviando={enviando} textoEnviando="Guardando…">{lugar ? 'Guardar cambios' : 'Registrar lugar'}</BotonAccion>
        <button type="button" className="boton boton-secundario" onClick={cerrar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  );
}
