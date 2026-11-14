import { useEffect, useRef } from 'react';
import { PASOS } from '../utils/pasosEvento.js';

/**
 * Guía del paso que se está viendo: qué hacer, qué falta y a dónde ir. El botón de la acción vive en la sección de abajo
 * (excepto "Confirmar", que llega como children). El foco pasa al título al cambiar de paso, para teclado y lectores de pantalla.
 */
export default function GuiaPaso({ vista, pasos, alElegir, pasado = false, children }) {
  const guia = pasos.guias[vista];
  const indice = PASOS.findIndex((p) => p.id === vista);
  const titulo = useRef(null);
  const primera = useRef(true);
  useEffect(() => {
    if (primera.current) { primera.current = false; return; }
    titulo.current?.focus();
  }, [vista]);

  const pendiente = guia.items.find((i) => !i.hecho && !i.opcional);
  return (
    <section className="guia tarjeta" aria-labelledby="guia-titulo">
      <div className="guia-cabecera">
        <h2 id="guia-titulo" ref={titulo} tabIndex={-1}>{guia.titulo}</h2>
        {indice >= 0 && <span className="guia-posicion">Paso {indice + 1} de {PASOS.length}</span>}
        {indice >= 0 && (
          <span className="guia-vistas">
            <button type="button" className="boton-enlace" onClick={() => alElegir('historial')}>Historial</button>
            <button type="button" className="boton-enlace" onClick={() => alElegir('todo')}>Ver todo junto</button>
          </span>
        )}
        {(vista === 'historial' || vista === 'todo') && (
          <button type="button" className="boton boton-secundario" onClick={() => alElegir(pasos.actualId)}>Volver a la guía</button>
        )}
      </div>
      <p className="guia-intro">{guia.intro}</p>

      {guia.items.length > 0 && (
        <ul className="guia-lista">
          {guia.items.map((i) => (
            <li key={i.texto} className={i.hecho ? 'hecho' : undefined}>
              <span className="guia-marca" aria-hidden="true">{i.hecho ? '✓' : ''}</span>
              <span>
                {i.texto}
                <span className="solo-lectores">{i.hecho ? ' (hecho)' : i.opcional ? ' (opcional, pendiente)' : ' (pendiente)'}</span>
              </span>
              {!i.hecho && i.ir && (
                <button type="button" className="boton-enlace" onClick={() => alElegir(i.ir)}>
                  Ir a {PASOS.find((p) => p.id === i.ir).nombre.toLowerCase()}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {/* Fase 11I: con la fecha pasada no se invita a hacer lo que ya no tiene sentido (sus botones están ocultos) */}
      {indice >= 0 && indice < 5 && pendiente && vista !== 'confirmar' && !pasado && (
        <p className="guia-ahora"><strong>Ahora:</strong> {pendiente.texto.charAt(0).toLowerCase() + pendiente.texto.slice(1)}.</p>
      )}
      {children}

      {indice >= 0 && (
        <div className="guia-navegacion">
          {indice > 0 && (
            <button type="button" className="boton boton-secundario" onClick={() => alElegir(PASOS[indice - 1].id)}>
              Anterior: {PASOS[indice - 1].nombre}
            </button>
          )}
          {indice < PASOS.length - 1 ? (
            <button type="button" className="boton boton-secundario" onClick={() => alElegir(PASOS[indice + 1].id)}>
              Siguiente: {PASOS[indice + 1].nombre}
            </button>
          ) : (
            <button type="button" className="boton boton-secundario" onClick={() => alElegir('historial')}>Ver historial</button>
          )}
        </div>
      )}
    </section>
  );
}
