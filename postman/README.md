# Postman — Maxi Eventos

Colección de la API REST de los 10 servicios y del gateway GraphQL, con pruebas (`pm.test`) en cada petición.

| Archivo | Qué es |
|---|---|
| `maxi-eventos.postman_collection.json` | Colección (formato Postman v2.1): 17 carpetas, 277 peticiones, 763 pruebas (Fase 11H; en la 11G eran 16 / 260 / 720 y en la 10B3c 15 / 232 / 631). |
| `maxi-eventos-local.postman_environment.json` | Environment "Maxi Eventos - local": URLs de los servicios (puertos 8080–8090), `gatewayUrl` (`/graphql`), `gatewayBaseUrl` (`http://localhost:8090`, para las rutas `/documentos`; Fase 10), `correo` y `password` (**vacía**). Fase 11D: `correoAsistente` y `passwordAsistente` (**vacía**, tipo secret) para la carpeta **"12b Roles"** (login del ASISTENTE y sus 403 directos en cada servicio y FORBIDDEN en el gateway; `ejecutar-postman.ps1` las toma de `AUTH_ASISTENTE_EMAIL` y `VERIFICACION_ASISTENTE_PASSWORD` del `.env`). Fase 11E: 12b.18–12b.21 (el asistente no ejecuta los recordatorios: 403 en Eventos y FORBIDDEN en el gateway; el administrador sí, y la segunda vez no repite ninguno). Las fotos de notificaciones y auditoría del evento de demostración no cuentan los recordatorios `EVENTO_PROXIMO` (dependen de la fecha). Fase 11H: carpeta **"12c Campanita"** (17 peticiones: recientes, sin leer, marcar una —idempotente— y todas, 404 / 400 / 401, GraphQL, y que lo que marca la administradora no cambia para la asistente). Si se importó antes, volver a importarlo. |
| `resultados/` | Resultados de Newman (`newman-<fecha>.txt` del reporter cli y `.xml` junit). No contienen el token ni la contraseña. |
| `generador/` | `generar-coleccion.js`: script de Node (sin paquetes de npm) que genera los dos JSON anteriores. Ver `generador/README.md`. |

## Cómo se mantiene la colección

Hay dos formas, y conviene elegir una:

1. **Regenerarla con el script**: editar `generador/generar-coleccion.js` y ejecutar
   `node postman\generador\generar-coleccion.js` desde la raíz (sobrescribe los dos JSON de esta carpeta). Con los archivos
   actuales produce exactamente la misma colección y el mismo environment (comparado byte a byte, 2026-10-31). Se usó así
   en la Fase 10B1 (nombres con "Prueba" y peticiones nuevas).
2. **Editarla en Postman y volver a exportarla** (Collection v2.1) sobre `maxi-eventos.postman_collection.json`. Desde ese
   momento el script queda desactualizado: si se vuelve a ejecutar, borra lo editado en Postman.

En los dos casos, ejecutar después la colección completa con Newman (abajo) y confirmar 0 fallos.

### Datos que crea la colección: siempre "Prueba …"

Regla del proyecto (2026-11-10): **todo dato creado por Postman o por los scripts lleva el prefijo "Prueba"**. La colección
ya la cumple: clientes, lugares, proveedores y eventos se llaman "Prueba Postman …" (revisado en el generador). Los únicos
nombres sin prefijo están en peticiones que la API rechaza y no crean nada (04.12 "X" → 400, 04.14 "Boda de prueba" con un
cliente inexistente → 404, las de 12b del asistente → 403).

### Regla que no está en la colección (Fase 11I)

`PATCH /eventos/{id}/confirmar` responde **409 "No se puede confirmar un evento cuya fecha ya pasó."** cuando la fecha del
evento es anterior a hoy (en `ZONA_HORARIA`; un evento de hoy todavía se confirma). **No hay petición de Postman para este
409**, a propósito: la API no deja crear eventos en fechas pasadas, y los datos de la colección, de los scripts y de la
demostración se crean **solo por la API** (nunca se siembran directamente en la base). Sin un evento con fecha pasada no hay
cómo provocarlo desde aquí sin esperar a que pase la fecha. La regla la comprueban, con un `Clock` fijo:

- `EventoServiceConfirmacionTest` (unitaria): un día después de la fecha → 409 sin consultar Contratos ni Finanzas; el
  mismo día → se confirma.
- `EventoApiIntegracionTest` → "PATCH confirmar / cancelar" → `fechaPasada409YElMismoDiaSeConfirma` (PostgreSQL real): el
  evento se crea por la API con fecha futura, el reloj de prueba pasa al día siguiente → 409 con el mensaje exacto y nada
  cambia (evento PENDIENTE, fecha PENDIENTE, sin mensajes en el outbox); el reloj vuelve al día del evento → 200 RESERVADO.

La regla también está documentada en `openapi/eventos.yaml` (ejemplo `fechaPasada` del 409). El generador lo anota en un
comentario junto a 10.14; la colección generada no cambia (277 peticiones, idéntica byte a byte).

## Antes de ejecutar

1. Entorno levantado: `docker compose up -d` (los 15 contenedores healthy).
2. Datos de demostración cargados **una vez** sobre una base recién creada: `.\scripts\verificacion\cargar-demo.ps1`.
   La colección busca por nombre el evento "Quince años de Lucía" (RESERVADO), "Boda de Andrés y Camila" (PENDIENTE),
   "Quince años de María José" (CANCELADO), "Hacienda El Pórtico" (lugar INACTIVO) y "Sonido y Luces del Huila", y guarda sus
   ids en el environment (carpeta 00). Por eso funciona con cualquier carga, aunque los ids cambien. Los eventos se buscan
   ordenados por `fechaCreacion` ascendente y se toma el **más antiguo** con ese nombre (el de la demostración), aunque
   después se hayan creado otros con el mismo nombre (los scripts de verificación usan además el prefijo "Prueba").

## Importar en Postman

1. **Import** → arrastrar los dos archivos JSON de esta carpeta.
2. Arriba a la derecha, elegir el environment **Maxi Eventos - local**.
3. En el environment, variable `password`: escribir la contraseña de la organizadora (el valor de `VERIFICACION_PASSWORD`
   del `.env`) **solo en la columna "Current value"** y guardar. El "Initial value" se queda vacío: es lo que se exporta y se
   comparte, así que la contraseña nunca sale del equipo. Si se cambia `correo`, también en "Current value".
4. No exportar el environment con los valores actuales ni hacer capturas de esa pantalla (secretos fuera de las evidencias).

El token no se escribe a mano: `00.03 Login [happy]` lo guarda en la variable de colección `token` y la colección lo envía
en todas las peticiones (`Authorization: Bearer {{token}}`). Las peticiones GraphQL llevan el header explícito.

## Orden de ejecución

La colección se ejecuta **completa y en orden** (00 → 99) con el **Runner** de Postman (*Run collection*) o con Newman: cada
carpeta usa ids que guardan las anteriores (`pm.collectionVariables.set` / `pm.environment.set`). Se puede ejecutar varias
veces seguidas sin limpiar nada.

| Carpeta | Contenido |
|---|---|
| 00 Preparación | Login 401 / 400 / 200 (guarda el token), ids y **foto inicial** de la demostración, fechas libres (tres sábados de fines de semana distintos desde `/fechas/disponibles`, que desde la 10B3 solo trae viernes, sábado y domingo, y un martes calculado para los 400 de la regla de días permitidos), sufijo único de la ejecución y el cliente propio de la colección. |
| 01 Clientes … 09 Notificaciones | Una carpeta por servicio: `[happy]`, `[400]`, `[404]`, `[409]`, `[filtro]`, `[paginación]` y `[401]`. **10B3** (carpeta 04): crear un evento un martes → 400 en `fecha` ("Solo se pueden registrar eventos en viernes, sábado o domingo."), bloquear un martes → 400, liberarlo → 200 y el martes no aparece en `/fechas/disponibles`. |
| 10 Flujo completo del negocio | Evento → lugar → cotización → contrato → firma → abonos → confirmación → logística por Kafka → adicional → recálculo y anexo → proveedores → compras → notificaciones y auditoría, con los 409 de cada paso (incluido **10.17b**: el anexo sin adicionales por la ruta `/documentos` del gateway → 409 `ErrorResponse`). Lo asíncrono (Kafka) se reintenta hasta 20 veces cada 2 s. **10B3 (lista de compras):** 10.17c–k antes del adicional (lista recién calculada, todo CALCULADO; agregar un ítem manual 201; 400 por campo; 409 nombre repetido en minúsculas; 404 evento sin logística; marcar el manual y el hielo; editar un calculado; quitar un calculado → 409; ítem ajeno → 404) y 10.19b–d después del recálculo (el manual, las marcas y la edición siguen igual; quitar el manual 204 y otra vez 404). |
| 11 Reto semana 8 (Finanzas) | Moneda distinta → 400 en el campo `moneda`; monto mayor que el saldo → 409 con el saldo en el mensaje. |
| 12 GraphQL (gateway) | Ficha completa en una query, mutation `crearCliente`, errores BAD_REQUEST y NOT_FOUND, el reto por GraphQL y acceso sin token (UNAUTHORIZED). **Fase 10:** query `clientes` (filtro por el nombre del cliente propio y paginación), mutation `actualizarCompras` sobre el evento del flujo (marcar comprado, volver a pendiente, ítem ajeno → NOT_FOUND) y los PDF por el gateway (`/documentos/…/contrato.pdf` y `anexo.pdf` 200, sin token 401, evento sin contrato 404). **10B3:** `crearLugar`, `actualizarLugar`, `cambiarEstadoLugar`, `crearProveedor`, `actualizarProveedor` y `cambiarEstadoProveedor` sobre entidades "Prueba Postman GraphQL …" (BAD_REQUEST por campo, NOT_FOUND y el CONFLICT del proveedor duplicado en minúsculas); 12.27–12.31 `agregarItemCompra`, `editarItemCompra`, nombre repetido → CONFLICT, `quitarItemCompra` (devuelve el id) y quitar un calculado → CONFLICT, sobre el evento del flujo. |
| 13 Limpieza | Cancela el evento del flujo, inactiva el proveedor propio y borra el cliente, el lugar y el proveedor creados por GraphQL. |
| 99 Datos de demostración intactos | Compara la demostración con la foto inicial (estados, fechas, balance, contrato, pagos, adicionales, asignaciones, notificaciones y auditoría). |

### No ensucia los datos de demostración

- Sobre la demostración **solo hay lecturas** (GET).
- Toda escritura (POST, PUT, PATCH, DELETE) va contra entidades **propias** con un sufijo único por ejecución en nombres,
  teléfonos y correos (Clientes rechaza teléfonos y correos repetidos): un cliente, un lugar (se borra), un proveedor (se
  inactiva al final), el evento E1 (sábado, se cancela), el evento E2 (otro sábado, se cancela) y el evento del flujo (sábado, se
  cancela en la limpieza, que libera su fecha). Las fechas salen de `/fechas/disponibles`. **Desde la 10B3 cada evento que
  crea la colección se cancela al final**: hay un solo evento por fin de semana y solo se trabaja de viernes a domingo, así
  que una fecha sin liberar agotaría los fines de semana disponibles.
- **Desde la Fase 10, el nombre de TODO lo que crea la colección empieza por "Prueba"** ("Prueba Postman Cliente Ñandú …",
  "Prueba Postman Kiosco …", "Prueba Postman Decoraciones …", "Prueba Postman Quince años E2 …", "Prueba Postman Boda E1 …"…),
  como los scripts de verificación. La interfaz web oculta por defecto los eventos cuyo nombre empieza por "Prueba" (casilla
  "Mostrar eventos de prueba"). Lo creado por ejecuciones anteriores a la Fase 10 conserva el nombre antiguo.

### Lo que deja cada ejecución

Cada ejecución completa **deja en la base de datos** (a propósito, no es un error):

- **Un cliente de prueba** (nombre con el sufijo de la ejecución) con **sus 3 eventos CANCELADOS** (E1, E2 y el del flujo),
  con su cotización, contrato, pagos, adicional, logística, asignaciones, notificaciones y mensajes de auditoría. La colección
  no lo borra a propósito: Clientes no consulta a Eventos antes de borrar, así que borrarlo dejaría esos eventos apuntando a un
  cliente inexistente (y la ficha GraphQL de esos eventos daría NOT_FOUND en `cliente`).
- **Un proveedor inactivo** (no se borra porque tiene asignaciones; `DELETE` con asignaciones da 409).

Las fechas de esos eventos quedan libres de nuevo. Tras varias ejecuciones, los listados (`GET /clientes`, `GET /eventos`,
`GET /proveedores`) muestran estos registros; para capturas limpias, filtrar (p. ej. `estado=RESERVADO`, `activo=true`) o
reiniciar la base y volver a cargar la demostración (`cargar-demo.ps1`).

## Semana 8: qué carpeta demuestra cada punto

| Punto de la semana 8 | Dónde |
|---|---|
| Happy path | Carpeta 10 (flujo completo) y las peticiones `[happy]` de 01 a 09 |
| Sad path (datos inválidos) | Peticiones `[400]` de cada carpeta (detalle por campo en `errores[]`) |
| Recurso inexistente | Peticiones `[404]` de cada carpeta y 12.05 (GraphQL NOT_FOUND) |
| Conflicto de negocio | Peticiones `[409]` de 01, 03, 04, 05, 06 y 10 (fin de semana, fecha ocupada, firma doble, confirmar sin contrato o sin abono, proveedor repetido…) |
| Filtros y paginación | Peticiones `[filtro]` y `[paginación]` de 01 a 09 |
| Reto (validación + error de negocio + OpenAPI + prueba + Postman) | Carpeta 11 (y 12.06 por GraphQL); OpenAPI en `openapi/finanzas.yaml` (`retoMonedaDistinta`, `retoMontoMayorQueElSaldo`); pruebas `FinanzasApiIntegracionTest` › `Pagos` › `monedaDistintaALaDeLaCotizacion400EnElCampo` y `montoMayorQueElSaldo409` |
| Seguridad (P3 · E1): acceso permitido y denegado | 00.03 (login), `[401]` de cada carpeta y 12.07 (GraphQL sin token) |

## Ejecutar con Newman

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verificacion\ejecutar-postman.ps1
```

- Usa `npx --yes newman@6.2.1` (no instala nada globalmente; la primera vez lo descarga). Necesita Node.js.
- Toma el correo y la contraseña del `.env` (`AUTH_ORGANIZER_EMAIL`, `VERIFICACION_PASSWORD`; si falta la contraseña la pide
  sin mostrarla) y los pasa en una copia temporal del environment que se borra al terminar.
- Reporters `cli` y `junit`: `postman/resultados/newman-<fecha>.txt` y `.xml`. Revisa que no contengan la contraseña ni un
  token; si los contuvieran, los borra y falla.
- Una sola carpeta (por ejemplo para una captura): `-Carpeta "11 Reto semana 8 (Finanzas)"`; las carpetas que dependen de
  otras necesitan que antes se haya ejecutado `00 Preparación` en la misma ejecución, así que lo normal es la colección completa.

Resultado de referencia (2026-10-30, dos ejecuciones seguidas): 189 requests, 505 pruebas, 0 fallos en cada una; los datos de
demostración quedaron iguales. **Fase 10B1 (2026-11-03, dos ejecuciones seguidas): 200 requests, 538 pruebas, 0 fallos** en
cada una; demostración intacta (carpeta 99 y una comprobación independiente de la ficha). **Fase 10B3a: 204 / 551** y
**10B3b: 215 / 580** y **10B3c: 232 requests, 631 pruebas, 0 fallos** (dos ejecuciones seguidas cada vez); demostración
intacta (incluida su lista de compras).
