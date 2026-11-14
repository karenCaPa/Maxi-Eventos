import { estadoVisible, hoyEnZona } from '../utils/fechaPasada.js';
import { formatearFecha } from '../utils/formato.js';
import { PASOS } from '../utils/pasosEvento.js';
import { resumenEvento } from '../utils/resumenEvento.js';
import Aviso from './Aviso.jsx';
import Datos from './Datos.jsx';

const nombrePaso = (id) => PASOS.find((p) => p.id === id)?.nombre ?? id;

/**
 * Fase 11F: contenido del paso 7 "Resumen". Arriba, "Todo listo" o "Quedan N pendientes" con un enlace a cada uno; debajo,
 * una tarjeta por paso con sus datos y un enlace a ese paso. Todo sale de la respuesta de la ficha (sin peticiones nuevas).
 * Lo pendiente se marca con texto e icono (no solo con color). Los enlaces cambian de paso y la guía lleva el foco a su título.
 */
export default function ResumenEvento({ evento, secciones, alElegir, hoy = hoyEnZona() }) {
  // Fase 11I: con la fecha pasada, el resumen lo dice arriba y solo deja como pendiente lo que aún se puede hacer (el saldo)
  const visible = estadoVisible(evento.estado, evento.fecha, hoy);
  const pasado = visible === 'REALIZADO' || visible === 'VENCIDO';
  const { tarjetas, pendientes, listo } = resumenEvento(evento, secciones, { pasado });
  const ir = (paso) => (e) => {
    e.preventDefault();
    alElegir(paso);
  };
  const enlace = (paso, texto) => <a href={`?paso=${paso}`} onClick={ir(paso)}>{texto}</a>;
  const fecha = formatearFecha(evento.fecha, { larga: true }).toLowerCase();

  return (
    <div className="resumen-evento">
      {visible === 'REALIZADO' && <p className="resumen-pasado"><strong>El evento ya se realizó</strong> el {fecha}.</p>}
      {visible === 'VENCIDO' && <p className="resumen-pasado"><strong>El evento venció sin confirmarse</strong> el {fecha}.</p>}
      {listo ? (
        <p className="resumen-estado resumen-listo" role="status">
          <span className="resumen-icono" aria-hidden="true">✓</span> <span className="resumen-estado-texto">{pasado ? 'Sin pendientes' : 'Todo listo'}</span>
        </p>
      ) : (
        <div className="resumen-estado resumen-falta" role="status">
          <p>
            <span className="resumen-icono" aria-hidden="true">!</span>{' '}
            <span className="resumen-estado-texto">{pendientes.length === 1 ? 'Queda 1 pendiente' : `Quedan ${pendientes.length} pendientes`}</span>
          </p>
          <ul aria-label="Pendientes">
            {pendientes.map((p) => (
              <li key={`${p.paso}-${p.texto}`}>{enlace(p.paso, `${p.titulo}: ${p.texto}`)}</li>
            ))}
          </ul>
        </div>
      )}

      <ul className="resumen-tarjetas" aria-label="Resumen por paso">
        {tarjetas.map((t) => {
          const idTitulo = `resumen-${t.id}`;
          const estado = t.errores.length > 0 ? 'error' : t.pendientes.length > 0 ? 'pendiente' : 'listo';
          return (
            <li key={t.id}>
              <article className={`resumen-tarjeta resumen-tarjeta-${estado}`} aria-labelledby={idTitulo}>
                <header className="resumen-tarjeta-cabecera">
                  <h3 id={idTitulo}>{t.titulo}</h3>
                  <span className="resumen-marca">
                    {estado === 'listo'
                      ? <><span aria-hidden="true">✓</span> Listo</>
                      : <><span aria-hidden="true">!</span> {t.pendientes.length === 1 ? '1 pendiente' : `${t.pendientes.length} pendientes`}</>}
                  </span>
                </header>
                {t.errores.map((e, i) => (
                  <Aviso key={i} tipo="error" titulo="No se pudo cargar esta parte.">{e.message}</Aviso>
                ))}
                {t.filas.length > 0 && <Datos filas={t.filas} />}
                {t.notas.map((n) => <p key={n} className="resumen-nota">{n}</p>)}
                {t.pendientes.length > 0 && (
                  <ul className="resumen-pendientes">
                    {t.pendientes.map((p) => (
                      <li key={p}><span className="resumen-icono" aria-hidden="true">!</span> <strong>Pendiente:</strong> {p}</li>
                    ))}
                  </ul>
                )}
                <p className="resumen-ir">{enlace(t.id, `Ir a ${nombrePaso(t.id)}`)}</p>
              </article>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
