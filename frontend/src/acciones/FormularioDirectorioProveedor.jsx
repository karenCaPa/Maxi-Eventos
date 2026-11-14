import { useId, useState } from 'react';
import { mutar } from '../api/cliente.js';
import { ACTUALIZAR_PROVEEDOR, CREAR_PROVEEDOR } from '../api/consultas.js';
import Campo from '../componentes/Campo.jsx';
import { CATEGORIAS_PROVEEDOR } from '../utils/formato.js';
import BotonAccion from './BotonAccion.jsx';
import ErrorAccion from './ErrorAccion.jsx';
import { useAccion } from './useAccion.js';

/**
 * Crear un proveedor (sin "proveedor") o editarlo. Las reglas (obligatorios, nombre repetido → 409) son de
 * proveedores-service; los errores por campo se muestran junto a cada campo y el 409 con su mensaje.
 * La categoría es texto con sugerencias (como el método de pago): proveedores-service la acepta libre.
 */
export default function FormularioDirectorioProveedor({ proveedor, alGuardar, cerrar }) {
  const [datos, setDatos] = useState(() => ({
    nombre: proveedor?.nombre ?? '', categoria: proveedor?.categoria ?? '', contacto: proveedor?.contacto ?? '',
  }));
  const idLista = useId();
  const { ejecutar, enviando, error } = useAccion(async (mensaje) => {
    await alGuardar(mensaje);
    cerrar();
  });
  const cambiar = (campo) => (e) => setDatos((d) => ({ ...d, [campo]: e.target.value }));

  function enviar(e) {
    e.preventDefault();
    const input = { nombre: datos.nombre.trim(), categoria: datos.categoria.trim(), contacto: datos.contacto.trim() };
    ejecutar(async () => {
      if (proveedor) {
        const r = await mutar(ACTUALIZAR_PROVEEDOR, { id: proveedor.id, input });
        return `Proveedor «${r.actualizarProveedor.nombre}» actualizado.`;
      }
      const r = await mutar(CREAR_PROVEEDOR, { input });
      return `Proveedor «${r.crearProveedor.nombre}» registrado.`;
    });
  }

  const campos = error?.campos ?? {};
  return (
    <form onSubmit={enviar} noValidate aria-label={proveedor ? `Editar el proveedor ${proveedor.nombre}` : 'Nuevo proveedor'}>
      <div className="rejilla-campos">
        <Campo etiqueta="Nombre" error={campos.nombre}>
          {({ id, invalido, describedBy }) => <input id={id} value={datos.nombre} onChange={cambiar('nombre')} autoComplete="off"
            aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
        <Campo etiqueta="Categoría" error={campos.categoria} ayuda="Elige una sugerencia (SONIDO, BANQUETES…) o escribe otra.">
          {({ id, invalido, describedBy }) => <input id={id} list={idLista} value={datos.categoria} onChange={cambiar('categoria')}
            autoComplete="off" aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
        <Campo etiqueta="Contacto" error={campos.contacto} ayuda="Teléfono o correo.">
          {({ id, invalido, describedBy }) => <input id={id} value={datos.contacto} onChange={cambiar('contacto')} autoComplete="off"
            aria-invalid={invalido} aria-describedby={describedBy} />}
        </Campo>
      </div>
      <datalist id={idLista}>
        {Object.entries(CATEGORIAS_PROVEEDOR).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
      </datalist>
      <ErrorAccion error={error} />
      <div className="acciones-formulario">
        <BotonAccion type="submit" enviando={enviando} textoEnviando="Guardando…">{proveedor ? 'Guardar cambios' : 'Registrar proveedor'}</BotonAccion>
        <button type="button" className="boton boton-secundario" onClick={cerrar} disabled={enviando}>Cancelar</button>
      </div>
    </form>
  );
}
