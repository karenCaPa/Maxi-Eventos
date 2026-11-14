/** Botón de una acción: desactivado y con el texto "trabajando" mientras espera la respuesta (evita el doble clic). */
export default function BotonAccion({ enviando, textoEnviando, children, variante = 'primario', disabled, type = 'button', ...resto }) {
  return (
    <button
      type={type}
      className={`boton boton-${variante}`}
      disabled={enviando || disabled}
      aria-busy={enviando || undefined}
      {...resto}
    >
      {enviando ? (textoEnviando ?? 'Procesando…') : children}
    </button>
  );
}
