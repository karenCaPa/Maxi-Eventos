# Interfaz web — Maxi Eventos

> Estado: **Fase 10 terminada** (2026-11-03); **Fase 11B** (2026-11-04): documentados los cambios hechos a mano tras la
> Fase 10 (guía paso a paso, asistente de Nuevo evento, agenda, montos con miles, confirmar asignaciones, rediseño y fuentes);
> **Fase 11C**: errores técnicos con mensaje genérico y código de referencia; **Fase 11D**: roles en la interfaz. Arquitectura: [`arquitectura.md`](arquitectura.md). Seguridad:
> [`seguridad.md`](seguridad.md#la-interfaz-web-fase-10-token-csp-y-cabeceras). Gateway: [`graphql.md`](graphql.md).

Requisito no funcional de la Entrega 2: *"La plataforma debe proporcionar una interfaz minimalista y centralizada que permita
consultar la información completa de un evento desde una única vista."* La interfaz es de **uso exclusivo de la
organizadora** y **solo habla con el gateway**: GraphQL en `/graphql` y, para los PDF, las rutas REST de solo lectura
`/documentos` del gateway (desde la Fase 11D la usan dos roles: ADMINISTRADOR y ASISTENTE). No llama a ningún microservicio directamente y no tiene lógica de negocio: las reglas (fecha libre,
un evento por fin de semana, días permitidos, contrato firmado, depósito, saldo…) las decide el backend y sus mensajes se
muestran tal cual.

## Cómo usarla

| Modo | Comando | URL |
|---|---|---|
| **Docker** (normal) | `docker compose up -d --build` (contenedor `frontend`) | http://localhost:5173 |
| Desarrollo (recarga en caliente) | `cd frontend`, `npm ci` y `npm run dev` | http://localhost:5173 |

Los dos usan el puerto **5173** (el origen de `CORS_ALLOWED_ORIGINS`), así que **no pueden estar a la vez**: para usar
`npm run dev`, primero `docker compose stop frontend`. Las credenciales son las de la organizadora (`AUTH_ORGANIZER_EMAIL` y
su contraseña).

## Pantallas

| Ruta | Qué hace |
|---|---|
| `/login` | Login con la mutation `login` ("Te damos la bienvenida"). Un error de credenciales se muestra en el formulario. |
| `/eventos` | **Agenda** paginada (20) y ordenada por fecha, **agrupada por mes**, con una ficha de calendario por evento (día, número y mes; su color sigue el estado, que también va en texto en la insignia). Filtros de estado, tipo y rango en la URL. Oculta por defecto lo que empieza por **"Prueba"** (lo que crean los scripts y Postman); la casilla "Mostrar eventos de prueba" lo muestra. |
| `/eventos/:id` | **La ficha completa en UNA sola query** (`FICHA_EVENTO`, 11 relaciones): cliente, lugar, cotización, balance, contrato, pagos, servicios adicionales, proveedores, logística con la lista de compras, notificaciones y auditoría, más una franja de resumen (total, abonado, **saldo pendiente**, contrato y depósito). Se recorre con la **guía paso a paso** (abajo). Si un servicio está caído, solo su sección muestra el aviso. |
| `/eventos/nuevo` | Nuevo evento como **asistente de 4 pasos** (abajo), con cliente existente o nuevo y la fecha en un **calendario mensual** (10B3e). |
| `/lugares`, `/proveedores` | Directorios con registro, edición y activar / desactivar (10B3b). |

**Acciones de la ficha** (se muestran según el estado, pero quien decide es el servicio; sus 409 se ven en pantalla): confirmar,
cancelar (con confirmación, al pie de la ficha), generar cotización, crear y firmar el contrato, descargar el contrato y el anexo
en PDF, registrar un pago (clave de idempotencia por formulario), asignar o cambiar el lugar, asignar proveedor y **confirmar una
asignación PENDIENTE** (botón "Confirmar" en su fila; mutation `cambiarEstadoAsignacion`), agregar un servicio adicional y la
**lista de compras** (marcar conseguido, agregar, editar y quitar ítems manuales; avance "N de M conseguidos"). **Tras cada
acción exitosa se recarga la ficha completa.** Los botones se desactivan mientras esperan (y un doble clic no repite la
petición). Los montos (pago y costo del proveedor) se escriben con puntos de miles (`componentes/InputMonto.jsx`: "1.500.000";
la coma es el decimal) y se envían como número.

### Roles: administrador y asistente (Fase 11D)

- La barra muestra **el nombre y el rol** del usuario (Administrador, Asistente; "Sin rol" con un token anterior a la 11D). El
  nombre viene en la respuesta del `login` y se guarda en `sessionStorage` junto al token (se borra con él); el rol se lee del
  claim `rol` del token (`sesion/almacen.js`, `obtenerUsuario`).
- **Lo que el rol no puede usar no se muestra:** al asistente no le aparecen "Nuevo lugar" / "Nuevo proveedor", ni la columna
  Acciones (Editar, Activar, Desactivar) de los directorios, que muestran una nota de solo consulta; la ficha **no pide** la
  auditoría (`FICHA_EVENTO` con `auditoria @include(if: $verAuditoria)`) y no dibuja esa sección, así que no hay error por una
  sección que no puede ver. Sí ve y hace todo el flujo del evento, asigna lugar y proveedores existentes y confirma asignaciones.
- Es solo comodidad: **quien autoriza es el backend** (FORBIDDEN en el gateway y 403 en cada servicio; ver
  [`seguridad.md`](seguridad.md#autenticación-y-autorización-roles-fase-11d)). Si alguien forzara la operación, la interfaz
  mostraría el mensaje "No tienes permiso para realizar esta operación." (FORBIDDEN es un error visible, no técnico).

### Mensajes de error: técnicos al log, genéricos en pantalla (Fase 11C)

Recomendación del profesor: la interfaz no muestra el detalle de un error técnico; muestra un mensaje genérico y un **código
de referencia** con el que se encuentra el error real en el log.

| Tipo | Cuándo (classification o caso) | Qué ve el usuario |
|---|---|---|
| **Técnico** | `SERVICE_UNAVAILABLE` (503, servicio caído, timeout), `INTERNAL_ERROR` (500), errores de graphql-java (`ValidationError`…), cualquier clasificación desconocida, respuesta que no es GraphQL (502 de un proxy), 5xx de una descarga | "No se pudo completar la operación. Intenta de nuevo en unos minutos. **Ref: 3F9A12BC**" |
| Técnico sin referencia | Sin conexión con el gateway (la petición no llegó: no hay nada que buscar en su log) | "No se pudo completar la operación. Intenta de nuevo en unos minutos." |
| Técnico en una sección de la ficha | Cualquier error parcial (`path ["evento", "<sección>"]`) | En esa sección: "No se pudo cargar esta sección." + "No se pudo cargar esta información. Intenta de nuevo en unos minutos. Ref: …"; el resto de la ficha se ve normal |
| Error de programación | Una excepción que no es `ErrorApi` | El mensaje genérico (sin referencia) |
| **Negocio y validación** | `CONFLICT` (409: "el contrato debe estar FIRMADO…"), `BAD_REQUEST` (400, con los errores junto a cada campo), `NOT_FOUND` (404), `FORBIDDEN` (403) | El mensaje en español del servicio |
| Sesión y credenciales | `UNAUTHORIZED` del gateway (sesión vencida → vuelta al login) o de `auth` (credenciales incorrectas) | Su mensaje, como antes |

- **Código de referencia:** cada petición (`api/cliente.js` y `api/descargas.js`) envía `X-Request-Id` con un UUID nuevo
  (`crypto.randomUUID`). En pantalla se ven los **8 primeros caracteres** en mayúsculas; en el log está el UUID completo, que
  empieza por ellos: `docker compose logs gateway | Select-String "3f9a12bc"` (sin distinguir mayúsculas) muestra la línea
  `Error técnico ref=3f9a12bc-… /evento/contrato -> contratos (503): …` y todas las líneas `[ref=3f9a12bc-…]` de esa petición,
  y el mismo código aparece en el log del servicio al que se llamó.
- Todo pasa por un solo sitio: `ErrorApi` lleva como `message` el texto genérico con la referencia (`tecnico = true`) y el
  original en `detalle`, que **nunca** se pinta. Así todas las pantallas que muestran `error.message` quedan cubiertas, y
  cada `catch` convierte lo que no sea `ErrorApi` con `aErrorVisible`. En desarrollo (`npm run dev`) el detalle también sale
  en la consola del navegador (`console.warn`); en el build y en las pruebas, no.
- La API no cambia: GraphQL sigue enviando el mensaje real del servicio en `errors[].message` (Postman y GraphiQL lo ven); la
  decisión de ocultarlo es solo de la interfaz.

### Guía paso a paso de la ficha (agregada tras la Fase 10)

- **6 pasos:** Evento → Cotización → Contrato → Abonos → Confirmar → Preparativos (`utils/pasosEvento.js`,
  `componentes/PasosEvento.jsx` y `GuiaPaso.jsx`), y dos vistas aparte: **Historial** (notificaciones y auditoría) y **Ver
  todo junto** (todas las secciones).
- Cada paso muestra qué hacer, una lista con lo hecho y lo pendiente (con enlaces "Ir a …"), Anterior / Siguiente y solo
  **sus** secciones (`Seccion paso="…"`). Lo hecho y el detalle de cada paso ("Pendiente de firma", "Faltan $ X") salen de
  los **mismos datos de la query**, sin peticiones nuevas; si la sección de la que sale un dato falló, el paso no se marca
  como hecho.
- El paso va en la URL (`?paso=contrato`, `?paso=todo`); sin él se abre **el que toca** (el primero sin hacer). Tras una
  acción exitosa, si se estaba viendo el paso que tocaba, la guía avanza sola. Al cambiar de paso el foco va al título.
- **Confirmar (Fase 11B):** el botón "Confirmar evento" está **siempre** en el paso Confirmar de un evento PENDIENTE, junto a
  la lista de requisitos (cotización, contrato firmado, depósito) con lo que falta marcado. Si falta algo, Eventos responde
  409 y su mensaje se ve ahí mismo (es la demostración de "Quince años de Sofía").
- Un evento CANCELADO se ve con todo junto, sin guía.

### Paso 7 "Resumen" (Fase 11F)

Al final de la guía, después de Preparativos (los 6 pasos no cambian; la guía dice ahora "Paso N de 7" y Preparativos
lleva a "Siguiente: Resumen"). `utils/resumenEvento.js` calcula y `componentes/ResumenEvento.jsx` muestra:

- **Arriba, una línea:** "Todo listo" o "Quedan N pendientes" con **un enlace por pendiente** ("Contrato: Contrato sin
  firmar", "Abonos: Depósito: faltan $ X"…). En el camino, el paso 7 dice "Todo listo" o "N pendientes" (y lleva ✓ si no
  queda nada).
- **Debajo, 6 tarjetas**, cada una con "Ir a …" a su paso:

| Tarjeta | Muestra | Pendiente si… |
|---|---|---|
| Evento | nombre, tipo, fecha, invitados, zona, cliente (nombre, teléfono, correo, dirección), lugar y, si salió, el recordatorio de proximidad | no tiene lugar ("Sin lugar asignado") |
| Cotización | valor base y total con adicionales | no hay cotización ("Falta cotizar") |
| Contrato | estado y fecha de firma | "Sin contrato" o "Contrato sin firmar" |
| Abonos | depósito, abonado, saldo y número de pagos | "Depósito: faltan $ X" o, con el depósito cubierto, "Saldo pendiente $ X" |
| Confirmación | si está reservado | "Por confirmar" |
| Preparativos | proveedores con su estado, adicionales con su total, logística y compras "N de M conseguidos" | "Sin proveedores asignados", "N proveedores por confirmar" y "Sin conseguir: …" (los ítems pendientes) |

- Sale de la **misma respuesta `FichaEvento`**: no hace peticiones nuevas. No usa la auditoría, así que funciona igual para
  el administrador y el asistente.
- Lo pendiente se marca con **texto e icono** ("! Pendiente: …", "! 1 pendiente", "✓ Listo"), no solo con color.
- Una sección que no cargó muestra en su tarjeta el **mensaje genérico con su referencia** y suma un pendiente ("No se pudo
  cargar el balance"): nunca cuenta como hecha.
- Sin tablas (listas de datos); a 360 px las tarjetas quedan en **una sola columna**. Los enlaces son `<a href="?paso=…">` y,
  como cualquier cambio de paso, llevan el foco al título del paso.
- "Sin proveedores asignados" cuenta como pendiente (ajuste del 2026-11-06), igual que la regla del paso 6 del camino (✓ solo
  con al menos un proveedor): un evento no sale "Todo listo" si el paso 6 no tiene ✓.

### Eventos con fecha pasada (Fase 11I)

**El estado del backend no cambia** (el evento sigue RESERVADO, PENDIENTE o CANCELADO). La interfaz calcula si la fecha ya
pasó con "hoy" en la zona de la configuración (`ZONA_HORARIA`, por `/config.js`; `utils/fechaPasada.js` → `hoyEnZona()`),
no con la del navegador. **Un evento de hoy todavía no pasó** (pasa a "pasado" al día siguiente), igual que la regla de
Eventos al confirmar.

| Estado del backend | Fecha anterior a hoy → se muestra | Cómo |
|---|---|---|
| RESERVADO | **Realizado** | insignia gris con borde; `aria-label` "Estado: Realizado. El evento estaba reservado y su fecha ya pasó." |
| PENDIENTE | **Vencido** (nunca se confirmó) | insignia gris con borde punteado; `aria-label` "Estado: Vencido. El evento quedó pendiente: su fecha pasó sin que se confirmara." |
| CANCELADO | Cancelado, igual que antes | — |

Las etiquetas llevan **texto** (no solo color); el `aria-label` (y el `title`, para el ratón) explica por qué.

- **Listado:** los próximos (de hoy en adelante, `desde = hoy`) se ven como siempre. Al final, la sección **"Eventos pasados
  (N)"** (`<details>`, **plegada por defecto**, en gris), con su propia consulta `EventosPasados` (`hasta = ayer`,
  `sort: "fecha,desc"`: **la más reciente primero**) y su propia paginación. Los filtros de estado y tipo se aplican a las
  dos listas; si "Hasta" es anterior a hoy, solo se piden los pasados, y si "Desde" es posterior, solo los próximos. La
  sección **se muestra siempre** al final (ajuste del 2026-11-10): vacía dice "Eventos pasados (0)" y, dentro, **"Todavía no
  hay eventos pasados. Los eventos aparecerán aquí automáticamente cuando pase su fecha."**; si hay filtros activos (estado,
  tipo o fechas), "No hay eventos pasados con estos filtros." (para no dar a entender que no existe ninguno). Mientras llega
  la primera respuesta dice "Cargando eventos pasados…" (sin número), y es el mismo `<details>`: si se abrió, sigue abierto.
  El texto de arriba dice "N eventos en total de hoy en adelante".
- **"Próximos 7 días":** pide de hoy (en `ZONA_HORARIA`) a hoy + 7 y descarta en la interfaz cualquier evento de fecha
  anterior (por si la página queda abierta pasada la medianoche).
- **Ficha:** arriba, en todos los pasos, el aviso **"Este evento se realizó el sábado, 5 de diciembre de 2026."** o **"Este
  evento venció el … sin confirmarse."**. Sin `?paso=`, la guía abre el **Resumen**.
  - **Se ocultan:** confirmar (en la guía y abajo), cancelar, asignar o cambiar lugar, generar cotización, crear y firmar
    contrato, agregar servicios adicionales y, en Proveedores, "Asignar proveedor" y "Confirmar" (la columna "Acción" desaparece; ajuste del 2026-11-10). El paso "Confirmar" lo explica ("El evento se confirmó y ya se realizó." /
    "Este evento ya no se puede confirmar: su fecha pasó sin confirmarse.") y la guía deja de decir "Ahora: …".
  - **Se mantienen:** registrar pagos (el saldo se puede pagar después del evento), descargar los PDF (contrato y anexo),
    la lista de compras y el Resumen.
  - El aviso "Recordatorio enviado" ya no se muestra (era sobre un evento próximo).
- **Resumen (paso 7):** arriba "**El evento ya se realizó** el …" (o "venció sin confirmarse"). Si se realizó y queda
  saldo, **el saldo sigue como pendiente** ("Queda 1 pendiente · Abonos: Saldo pendiente $ X"); lo demás que quedó sin
  hacer pasa a nota ("Quedó sin hacer: …"), porque ya no tiene arreglo. Sin nada pendiente dice "**Sin pendientes**" (no
  "Todo listo"). En un evento vencido no queda nada pendiente. Las secciones que no cargaron siguen contando como pendiente.
- **Backend (la única regla nueva):** `PATCH /eventos/{id}/confirmar` con la fecha ya pasada → **409 "No se puede confirmar
  un evento cuya fecha ya pasó."** (eventos-service, `Clock` en `ZONA_HORARIA`). Así, aunque se llame a la API directamente,
  un evento vencido no se confirma.

Pruebas: `utils/fechaPasada.test.js` (hoy en la zona —23:59 y 00:00 de Neiva—, cambio de mes y año, Realizado / Vencido con
hoy, ayer y mañana) y `paginas/EventosPasados.test.jsx` (insignias y su `aria-label`, la sección plegada con su número y
sus consultas, el caso límite del evento de hoy, los filtros, "Próximos 7 días", la ficha con las acciones ocultas y
visibles, el día del evento sin cambios y el Resumen con y sin saldo). **Toda la suite corre con "hoy" = 2026-11-03**
(`test/setup.js`): desde la 11I la interfaz depende de la fecha, y sin esto la ficha de demostración (5-dic-2026) se vería
"Realizada" en cuanto pase ese día. 10 mutaciones de control (p. ej. `<` por `<=`, no ocultar las acciones, quitar el filtro
de "Próximos", abrir la sección por defecto): todas hacen fallar al menos una prueba.

### Modo demostración: fecha simulada (Fase 11I)

Para **mostrar** los eventos pasados en la sustentación (la demostración no tiene ninguno: la API no deja crearlos), la
interfaz puede usar otra fecha como "hoy". **Solo la interfaz**: el backend no cambia.

- **Variable:** `FRONTEND_FECHA_SIMULADA` (opcional, `AAAA-MM-DD`). nginx la pasa a `/config.js` al arrancar el contenedor,
  igual que `FRONTEND_GATEWAY_URL` y la zona horaria; `config.js` → `fechaSimulada()` la valida otra vez (una fecha que
  exista, si no `null`). **Sin definir (lo normal), "hoy" es la fecha real.**
- **Un solo "hoy":** `utils/fechaPasada.js` → `hoyEnZona()` devuelve la fecha simulada cuando existe; `hoyIso()` (calendario
  y validación de Nuevo evento) ahora delega en él. Así la siguen Realizado / Vencido, "Eventos pasados", "Próximos 7 días",
  la ficha, el Resumen y el calendario. Las horas reales (la campanita, "hace 5 min", el vencimiento del token) no cambian.
- **Aviso:** `Layout.jsx` → `AvisoFechaSimulada`: en la barra, en todas las pantallas, una caja ámbar con borde punteado
  "**Modo demostración:** fecha simulada jueves, 10 de diciembre de 2026 (2026-12-10). El servidor sigue con la fecha real."
  (`role="note"`, con `<time datetime>`; a 360 px ocupa su propia línea debajo de la marca). Sin la variable no se muestra.
- **Activar:** `FRONTEND_FECHA_SIMULADA=2026-12-10` en el `.env` y `docker compose up -d frontend` (recrea solo la
  interfaz, sin reconstruir la imagen) y recargar la página. **Desactivar:** quitar o comentar la línea, el mismo comando y
  recargar. Ejemplo con la demostración y 2026-12-10: "Quince años de Lucía" (5-dic, RESERVADO) se ve **Realizado** y
  "Quince años de Sofía" (28-nov, PENDIENTE) **Vencido** (ver [`sustentacion.md`](sustentacion.md#mostrarlo-en-el-navegador-modo-demostración-con-fecha-simulada-solo-la-interfaz)).
- **Limitación:** el backend sigue con su reloj real: lo que la interfaz oculta por "fecha pasada" la API todavía lo
  permite (y el 409 "fecha ya pasó" solo sale con la fecha real); los pagos o cambios que se hagan se guardan de verdad;
  "Ejecutar recordatorios" y `/fechas/disponibles` usan la fecha real. Es para mirar, no para operar.
- **Pruebas:** `paginas/FechaSimulada.test.jsx` (6: sin y con la variable, valores inválidos e ignorados, el aviso en la
  barra con su texto, el ejemplo de Lucía y Sofía con las consultas `desde = 2026-12-10` / `hasta = 2026-12-09` / "Próximos"
  10 a 17, y el calendario que empieza en diciembre). Mutaciones de control: `hoyEnZona` que ignora la fecha simulada
  (3 fallos), la barra sin el aviso (1) y `hoyIso` con la fecha del navegador (2). `verificar_10.ps1` sección 6: la
  variable al arrancar sin reconstruir, `/config.js` con la fecha, el aviso en el log y tres valores inválidos
  (`2026-02-30`, `10/12/2026`, comillas con código) que no arrancan.

### Campanita de notificaciones (Fase 11H)

- **Dónde:** en la cabecera, junto al nombre de la usuaria (`componentes/Campana.jsx`), para los dos roles. En el escritorio
  el panel se abre a la **derecha de la barra lateral**, completo dentro de la pantalla; en el celular ocupa el ancho entre
  la barra de arriba y la de navegación.
- **El número:** las no leídas del usuario actual; oculto en 0 y "9+" con más de 9. El botón dice "Notificaciones, N sin
  leer" (`aria-label`) y `aria-expanded`.
- **El panel:** las 10 últimas de todos los eventos, con "Nueva" (●) o "Leída" en texto, el asunto, el mensaje, el nombre
  del evento y la **hora en que se recibió** en la zona de la configuración (`ZONA_HORARIA`, por `/config.js`): "Hoy,
  3:45 p. m. · hace 5 min", "Ayer, 9:10 a. m." o la fecha completa. Clic en una → se marca como leída y abre la ficha.
  "Marcar todas como leídas". Sin notificaciones: "No tienes notificaciones.". Al abrir, el foco va al título del panel;
  Escape (o un clic fuera) lo cierra y el foco vuelve a la campana.
- **"Leída" es por usuario** (tabla `notificaciones_lecturas` de notificaciones-service, con el `sub` del JWT): lo que lee la
  organizadora sigue sin leer para el asistente.
- **Actualización (`useSinLeer.js`): polling.** Cada 30 s consulta solo el número (`notificacionesSinLeer`, una petición
  pequeña); se **pausa con la pestaña oculta** y consulta enseguida al volver; después de cada acción de la ficha y de
  "Ejecutar recordatorios" consulta otra vez (y de nuevo a los 3 s, porque la notificación la genera Kafka un instante
  después). La lista con el nombre del evento solo se pide al abrir el panel.

**Por qué polling y no SSE ni suscripciones GraphQL** (para responderlo si se pregunta):

- **SSE:** el `EventSource` del navegador **no permite enviar el header `Authorization`**, y todo el sistema autentica con
  `Authorization: Bearer <token>`. Habría que poner el token en la URL (queda en logs e historial) o usar cookies, que la
  arquitectura evita a propósito (el token vive en `sessionStorage`).
- **Suscripciones GraphQL:** exigirían **WebSocket en el gateway** (Spring GraphQL sobre WebSocket con su propia
  autenticación), **en nginx** (proxy con `Upgrade`) y **en la CSP** (`connect-src ws://…`), más reconexiones; todo para un
  dato que cambia unas pocas veces al día.
- **Polling cada 30 s:** usa la misma petición GraphQL autenticada de siempre, no cambia la infraestructura, se pausa en
  segundo plano y el retraso máximo (30 s) es aceptable para avisos internos de una sola organizadora.

### Asistente de Nuevo evento (agregado tras la Fase 10)

- **4 pasos:** Cliente → Fecha → Datos del evento → Revisar y crear. "Siguiente" valida solo los campos de su paso (cliente
  nuevo: nombre, teléfono y correo, igual que Clientes) y marca lo que falta junto a cada campo; "Atrás" conserva lo escrito.
- "Revisar y crear" resume cliente, fecha larga, evento, tipo, invitados, zona y lugar.
- Al crear se hace la validación completa y la comprobación final de la fecha (ver el calendario); si el servicio devuelve un
  error por campo o un 409, el asistente vuelve al paso donde se corrige (la fecha ante un 409).

### Calendario de Nuevo evento (`componentes/CalendarioFechas.jsx`)

- Un mes a la vez, semana de lunes a domingo, en español; no se puede ir antes del mes actual ni más allá del mes de hoy + 366
  días (el máximo de `fechasDisponibles`).
- Cada mes se pide **una vez** con `fechasDisponibles(desde: día 1, hasta: último día)`; mientras carga hay un indicador y, si
  falla, un aviso con **"Reintentar"**.
- **Solo se pueden elegir los días que devuelve la query** (disponible). **No laborable** = lunes a jueves. **No disponible** =
  hoy, días pasados, fines de semana ocupados o bloqueados (un evento por fin de semana) y días fuera del rango. Cada día es un
  botón con `aria-label` = fecha completa + estado.
- Antes de crear el cliente y el evento se valida todo el formulario (`utils/validacionEvento.js`) y se vuelve a preguntar por
  **esa** fecha: si otra pestaña la tomó, se avisa, se quita la selección y el mes se recarga; no se crea nada.
- El caso "evento un martes" ya no se puede provocar desde la interfaz: lo cubren eventos-service, Postman (`04.11b`) y los
  scripts.

## Estructura (`frontend/`)

```
src/
  api/          cliente.js (fetch a /graphql, token, errores por sección), consultas.js (queries y mutations),
                descargas.js (PDF por /documentos), useConsulta.js
  sesion/       almacen.js (token en sessionStorage), SesionContext.jsx
  componentes/  Layout (barra lateral, Marca), Iconos, Seccion, Tabla, Campo, InputMonto, Aviso, Datos, Paginacion, Insignia,
                ResumenFicha, FechaFicha, PasosEvento, GuiaPaso, VistaPaso, CalendarioFechas, ResumenEvento (11F),
                ProximosEventos y AvisoRecordatorio (11E), Campana y useSinLeer (11H)…
  acciones/     botones y formularios de la ficha (useAccion, AccionSimple, FormularioPago, ListaCompras…)
  paginas/      Login, Eventos, FichaEvento, NuevoEvento, Lugares, Proveedores
  utils/        formato.js (fechas y montos es-CO), pasosEvento.js (estado de la guía), resumenEvento.js (paso 7, 11F), fechaPasada.js (Realizado / Vencido, 11I), validacionEvento.js, ayudas.js
  config.js     URL del gateway: window.__CONFIG__.gatewayUrl → VITE_GATEWAY_URL → http://localhost:8090
Dockerfile, docker/   imagen (Node → nginx), plantilla de nginx y validación de FRONTEND_GATEWAY_URL
```

**Pila:** React 19.3.1 + React Router 7.18.4, Vite 8.3.4, en JavaScript; sin librerías de GraphQL ni de componentes (CSS
propio). Fuentes **autoalojadas** `@fontsource-variable/bricolage-grotesque` y `@fontsource-variable/figtree` 5.3.0 (licencia
OFL; Vite copia los `.woff2` a `assets/`, así que la CSP `font-src 'self'` basta y no se llama a ningún servidor de fuentes).
Versiones exactas (`save-exact` y `package-lock.json`). Pruebas con Vitest 5.0.5 + Testing Library + jsdom (`testTimeout`
de 15 s: la primera prueba de cada archivo paga el arranque de jsdom).

**Accesibilidad:** etiquetas en todos los campos, errores junto al campo (`aria-invalid`, `aria-describedby`), avisos con
`role="alert"` / `aria-live`, foco visible de 3 px, "Saltar al contenido", contraste AA, el estado nunca solo por color, tablas
con `caption` y cabeceras, y en pantallas estrechas (≤ 40 rem) las filas se apilan como tarjetas. Toda tabla va en su propio
contenedor con desplazamiento horizontal (`componentes/Tabla.jsx`): nada se sale de su recuadro (revisado a 360 y 1280 px).

## Docker: nginx y configuración en tiempo de arranque

- **Imagen en dos etapas** (`frontend/Dockerfile`): `node:22.16.0-alpine` hace `npm ci` y `vite build`; `nginx:1.28.0-alpine`
  sirve solo los archivos estáticos (versiones fijas, nunca `:latest`). Sin source maps.
- **`FRONTEND_GATEWAY_URL`** (opcional, por defecto `http://localhost:8090`) es la URL del gateway **tal como la ve el
  navegador**. No se compila en el JavaScript: al arrancar, nginx genera `/config.js`
  (`window.__CONFIG__ = Object.freeze({ gatewayUrl: "…", zonaHoraria: "…" })`) y la CSP a partir de la plantilla
  `docker/maxi-eventos.conf.template`. Cambiarla solo exige **recrear** el contenedor (`docker compose up -d frontend`), no
  reconstruir la imagen. `docker/05-validar-gateway-url.sh` comprueba antes que sea un origen (`http(s)://host[:puerto]`, sin
  ruta, comillas ni espacios); si no, el contenedor **no arranca** y el log dice por qué.
- **Zona horaria (Fase 11H):** `/config.js` lleva también `zonaHoraria` desde **`FRONTEND_ZONA_HORARIA`**, que el compose
  toma de `ZONA_HORARIA` (por defecto America/Bogota; la misma que usan Eventos y Notificaciones). Todas las horas de la
  interfaz (la campanita, la ficha) se muestran en esa zona. `docker/06-validar-zona-horaria.sh` solo acepta un nombre IANA
  (p. ej. `America/Bogota`); si no, el contenedor no arranca.
- **Modo demostración, fecha simulada (Fase 11I):** `/config.js` lleva `fechaSimulada` desde **`FRONTEND_FECHA_SIMULADA`**
  (opcional; sin definir → `""` = fecha real, lo normal). `docker/07-validar-fecha-simulada.sh` solo acepta vacía o una fecha
  `AAAA-MM-DD` que exista; si no, el contenedor no arranca. Ver "Modo demostración" más arriba.
- **nginx:** puerto 5173; las rutas de la aplicación (`/eventos/…`) devuelven `index.html`; `/assets/*` con caché larga
  (nombres con hash) y 404 si no existe; `index.html` y `/config.js` sin caché; gzip; `server_tokens off`; 2 procesos de
  trabajo; `/healthz` para el healthcheck. Cabeceras de seguridad en todas las respuestas (ver
  [`seguridad.md`](seguridad.md#la-interfaz-web-fase-10-token-csp-y-cabeceras)).
- **Compose (`frontend`):** `mem_limit: 64m` (usa ≈ 4 MiB), healthcheck con `wget` a `/healthz`, `read_only: true` con
  `tmpfs` para la caché, el pid y la configuración generada, `no-new-privileges`, `depends_on: gateway (healthy)` y **fuera de
  la red de los servicios** (no la necesita: el navegador es quien llama al gateway).

## Pruebas y verificación

| Qué | Cómo | Resultado (2026-11-03) |
|---|---|---|
| Pruebas de la interfaz | `cd frontend` y `npm test` | **146 pruebas, 0 fallos** (19 archivos; 2026-11-10, con "hoy" fijo en 2026-11-03): modo demostración con fecha simulada, eventos con fecha pasada (Realizado / Vencido, sección "Eventos pasados", ficha y Resumen; 11I), campanita (número, panel, marcar, polling y su pausa), resumen del paso 7, bloque "Próximos 7 días" y aviso del recordatorio, roles, cliente GraphQL (token, sesión vencida, errores por campo y por sección), login, listado, ficha (una sola petición, errores parciales, tablas), acciones (doble clic, 409, recarga, pago idempotente, PDF), Nuevo evento y calendario, directorios, lista de compras, formatos es-CO y validación |
| Build | `npm run build` | sin scripts ni estilos en línea (lo exige la CSP) |
| Docker | `scripts\verificacion\verificar_10.ps1` | **80 comprobaciones, 0 fallos** (2026-11-10; la sección 6, FRONTEND_FECHA_SIMULADA, y el calendario que busca días libres hasta 2 meses después; en la 11F, el paso 7 "Resumen" en el navegador real, a 1280 y 360 px): contenedor, cabeceras y CSP, `/config.js`, archivos, rutas, 404, sin source maps ni secretos en el JS, CORS, **recorrido real en Edge sin ventana** (`navegador_10.mjs`: login, ficha, calendario, la CSP bloquea otro sitio y un script inyectado, cerrar sesión) y `FRONTEND_GATEWAY_URL` en el arranque (cambio sin reconstruir y valor inválido que no arranca) |

El recorrido en el navegador usa el protocolo de DevTools con el `WebSocket` de Node 22 (sin dependencias nuevas) y solo
**lee** la demostración. Si no hay Edge ni Chrome, `verificar_10.ps1` lo avisa y omite esa parte.
