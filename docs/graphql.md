# Gateway GraphQL — Maxi Eventos

> Estado: **Fase 6** y **Fase 7** completas (P3 · Entrega 1); revisado en la Fase 9D (2026-10-31: errores de
> `registrarPago` con el reto de la semana 8 y decisión sobre los endpoints por lotes); **Fase 10B1** (2026-11-03): query
> `clientes`, mutation `actualizarCompras` y rutas REST `/documentos` para la interfaz web; **Fase 10B3a**: `fechasDisponibles`
> y `crearEvento` siguen la regla nueva de días permitidos de Eventos (solo viernes, sábado y domingo; el gateway no cambia);
> **Fase 10B3b**: mutations `crearLugar`, `actualizarLugar`, `cambiarEstadoLugar`, `crearProveedor`, `actualizarProveedor` y
> `cambiarEstadoProveedor` (ejemplo 11); **Fase 10B3c**: `ItemCompra.origen` y `.nota`, mutations `agregarItemCompra`,
> `editarItemCompra` y `quitarItemCompra` (ejemplo 12); **tras la Fase 10** (agregada a mano, documentada en la 11B): mutation
> `cambiarEstadoAsignacion` (confirmar una asignación de proveedor PENDIENTE; Postman `10.25b`–`10.25f`, `12.32`, `12.33`). Seguridad (JWT en el gateway, introspección, CORS):
> [`seguridad.md`](seguridad.md).
> Schema: [`gateway/src/main/resources/graphql/schema.graphqls`](../gateway/src/main/resources/graphql/schema.graphqls).
> Arquitectura: [`arquitectura.md`](arquitectura.md). Capturas para la entrega: [`evidencias-p3e1-graphql.md`](evidencias-p3e1-graphql.md).

## Cómo usarlo

| Qué | Dónde |
|---|---|
| Endpoint | `POST http://localhost:8090/graphql` (cuerpo `{"query": "...", "variables": {...}}`; la respuesta HTTP es siempre 200 y los errores van en `errors`) |
| GraphiQL | http://localhost:8090/graphiql (solo con el perfil `dev`: `GATEWAY_SPRING_PROFILES=dev` en el `.env`) |
| Token | Ejecutar la mutation `login` (la única operación sin token), copiar `token` y, en GraphiQL, pestaña **Headers**: `{ "Authorization": "Bearer <token>" }`. Después pulsar **Re-fetch GraphQL schema** (↻): la introspección exige token, así que el panel Docs está vacío hasta entonces. |
| Logs | `docker compose logs gateway \| Select-String "REST -> "`: una línea por cada llamada REST que hizo el gateway |

El gateway **no tiene lógica de negocio ni base de datos**: cada query y cada mutation delega en el servicio REST dueño del
dato y reenvía el header `Authorization`. Desde la Fase 7 el gateway **valida el token antes** (firma, vigencia y emisor) y
los servicios lo vuelven a validar; sin token, vencido o inválido → `UNAUTHORIZED` sin llamar a ningún servicio.

## El schema

**Escalares:** `ID` (UUID), `Date` (`2026-12-05`), `DateTime` (`2026-10-28T15:30:00.000Z`), `BigDecimal` (montos sin pérdida
de precisión) y `Long`, estos cuatro de `graphql-java-extended-scalars` 22.0 (misma línea que el graphql-java 22.3 de Spring
Boot 3.4.5).

**Enums:** `TipoEvento` (QUINCE_ANOS, BODA), `ZonaEvento` (NEIVA, RURAL), `EstadoEvento` (PENDIENTE, RESERVADO, CANCELADO),
`EstadoContrato` (PENDIENTE, FIRMADO), `EstadoFecha` (DISPONIBLE, PENDIENTE, OCUPADA), `EstadoLugar` (ACTIVO, INACTIVO),
`EstadoAsignacion` (PENDIENTE, CONFIRMADO, CANCELADO), `EstadoNotificacion` (GENERADA) y `TipoMensaje` (EVENTO_CONFIRMADO,
PAGO_REGISTRADO, SERVICIO_ADICIONAL_AGREGADO y, desde la 11E, EVENTO_PROXIMO); `ResultadoRecordatorio` (ENVIADO,
YA_ENVIADO; 11E).

### Evento y sus relaciones

`Evento` tiene sus datos (`id, nombreEvento, fecha, tipoEvento, numInvitados, cantidadAdultos, cantidadNinos, zona, estado,
fechaCreacion, clienteId, lugarId`) y **11 relaciones; cada una llama a su servicio solo si la query la pide**:

| Campo | Servicio (REST) | Si no hay dato |
|---|---|---|
| `cliente: Cliente` | clientes `GET /clientes/{id}` | `null` |
| `cotizacion: Cotizacion` | finanzas `GET /eventos/{id}/cotizacion` | `null` (aún sin cotizar) |
| `contrato: Contrato` | contratos `GET /eventos/{id}/contrato` | `null` (aún sin contrato) |
| `pagos(page, size): PaginaPago` | finanzas `GET /eventos/{id}/pagos` | `null` |
| `balance: Balance` | finanzas `GET /eventos/{id}/balance` | `null` (aún sin cotizar) |
| `lugar: Lugar` | lugares `GET /lugares/{id}` | `null` sin llamar a Lugares si el evento no tiene `lugarId` |
| `proveedores(page, size): PaginaProveedorAsignado` | proveedores `GET /proveedores/eventos/{id}` | página vacía |
| `serviciosAdicionales: [ServicioAdicional]` | finanzas `GET /eventos/{id}/adicionales` | `[]` |
| `logistica: Logistica` (+ `compras(page, size)`) | logística `GET /eventos/{id}/logistica` (+ `/compras`) | `null` hasta que el evento se confirma (lo calcula el consumidor Kafka) |
| `notificaciones(page, size): PaginaNotificacion` | notificaciones `GET /notificaciones?eventoId` | página vacía |
| `auditoria(tipo, page, size): PaginaAuditoria` | auditoría `GET /auditoria?eventoId&tipo` | página vacía |

Campos de segundo nivel que también consultan solo si se piden: `Cotizacion.total` (balance de Finanzas),
`Logistica.compras` (Logística), `ProveedorAsignado.proveedor` y `ProveedorAsignado.categoria` (Proveedores, con un DataLoader
compartido: una llamada por proveedor aunque se pidan los dos).

Los listados usan el mismo formato de página que la API REST: `contenido, pagina, tamanoPagina, totalElementos, totalPaginas`.

### Queries

| Query | Delegación |
|---|---|
| `evento(id)` | eventos `GET /eventos/{id}` (+ las relaciones pedidas). Inexistente → `NOT_FOUND`. |
| `eventos(estado, tipo, desde, hasta, page, size, sort)` | eventos `GET /eventos?…` (size ≤ 100; `sort` = `campo,asc\|desc`). |
| `fechasDisponibles(desde, hasta)` | eventos `GET /fechas/disponibles` (desde la 10B3 **solo viernes, sábado y domingo**, los días permitidos de la configuración de Eventos; respeta la regla de un evento por fin de semana; por defecto hoy+1 … hoy+90, máximo 366 días). |
| `cliente(id)` | clientes `GET /clientes/{id}`. Inexistente → `NOT_FOUND`. |
| `clientes(nombre, page, size, sort)` | clientes `GET /clientes?…` (Fase 10: directorio para elegir el `clienteId` de `crearEvento` en la interfaz; `nombre` = coincidencia parcial; size ≤ 100; `sort` = `nombreCompleto,asc`…). |
| `catalogoServiciosAdicionales` | finanzas `GET /servicios-adicionales` (códigos para `agregarServicioAdicional`). |
| `lugares(nombre, tipo, estado, page, size)` | lugares `GET /lugares?…` (para elegir el `lugarId` de `asignarLugar`). |
| `proveedores(categoria, activo, page, size)` | proveedores `GET /proveedores?…` (para elegir el `proveedorId` de `asignarProveedor`). |
| `misNotificaciones(page, size): PaginaMiNotificacion` (11H) | notificaciones `GET /notificaciones/recientes?page&size`: todas las de todos los eventos, más recientes primero, con `leida` / `leidaEn` **del usuario del token** (size ≤ 100). `MiNotificacion.evento` se resuelve con `@BatchMapping` (una llamada a Eventos por evento distinto, solo si se pide). Los dos roles. |
| `notificacionesSinLeer: Int` (11H) | notificaciones `GET /notificaciones/sin-leer`: el número de la campanita del usuario del token. |

`lugares` y `proveedores` se añadieron en la 6B (consultas de solo lectura de los catálogos) para que el flujo completo se
pueda recorrer 100 % por GraphQL, sin pedir ids por REST. `clientes` se añadió en la Fase 10 por lo mismo: la interfaz web
necesita elegir un cliente existente al crear un evento.

### Mutations (todas delegan en una sola llamada REST)

| Mutation | Delegación | Errores típicos (del servicio) |
|---|---|---|
| `login(correo, password): AuthPayload` | auth `POST /auth/login` (sin token). Fase 11D: `AuthPayload` trae también `nombre` y `rol: Rol` (ADMINISTRADOR \| ASISTENTE) | UNAUTHORIZED "Correo o contraseña incorrectos." |
| `crearCliente(input: ClienteInput)` | clientes `POST /clientes` | BAD_REQUEST con `errores[]`; CONFLICT si ya existe |
| `crearEvento(input: CrearEventoInput)` | eventos `POST /eventos` | BAD_REQUEST (fecha pasada…; **día no permitido** (10B3): `extensions.errores` = `[{campo: "fecha", mensaje: "Solo se pueden registrar eventos en viernes, sábado o domingo."}]`); CONFLICT (fecha ocupada, fin de semana); NOT_FOUND (cliente) |
| `generarCotizacion(eventoId)` | finanzas `POST /cotizaciones` (devuelve la existente si ya había) | NOT_FOUND; CONFLICT (evento cancelado) |
| `crearContrato(eventoId)` | contratos `POST /eventos/{id}/contrato` | CONFLICT (sin cotización) |
| `firmarContrato(eventoId)` | contratos `PATCH /eventos/{id}/contrato/marcar-firmado` | CONFLICT (ya firmado) |
| `registrarPago(input: PagoInput)` | finanzas `POST /finanzas/pagos` (`claveIdempotencia` → cabecera `X-Idempotency-Key`; `moneda` por defecto COP) | BAD_REQUEST (monto ≤ 0; **`moneda` distinta a la de la cotización**: `extensions.errores` = `[{campo: "moneda", mensaje: "debe ser COP, la moneda de la cotización"}]`, el reto de la semana 8; `clienteId` distinto al del evento); CONFLICT (supera el saldo, evento cancelado, sin cotización) |
| `confirmarEvento(id)` | eventos `PATCH /eventos/{id}/confirmar` | CONFLICT (contrato sin firmar, depósito insuficiente, ya confirmado); SERVICE_UNAVAILABLE |
| `cancelarEvento(id)` | eventos `PATCH /eventos/{id}/cancelar` | CONFLICT (ya cancelado) |
| `agregarServicioAdicional(eventoId, codigo, cantidad)` | finanzas `POST /eventos/{id}/adicionales` | BAD_REQUEST (código no existe) |
| `asignarLugar(eventoId, lugarId)` | eventos `PATCH /eventos/{id}/lugar` | CONFLICT (lugar inactivo o sin capacidad) |
| `asignarProveedor(input: AsignarProveedorInput)` | proveedores `POST /proveedores/eventos` | CONFLICT (inactivo o asignación duplicada) |
| `cambiarEstadoAsignacion(id, estado: EstadoAsignacion): ProveedorAsignado` (tras la Fase 10) | proveedores `PATCH /proveedores/eventos/asignaciones/{id}/estado` con `{estado}`; **solo PENDIENTE → CONFIRMADO** | CONFLICT "Solo se puede confirmar una asignación pendiente; esta ya está confirmada." o "…se pidió dejarla cancelada."; NOT_FOUND "No existe una asignación con id …" |
| `crearLugar(input: LugarInput)` (10B3) | lugares `POST /lugares` (queda ACTIVO) | BAD_REQUEST con `errores[]` (nombre, dirección y tipo obligatorios; `capacidad` obligatoria y > 0; el input admite `capacidad: null` para que el error llegue en ese campo) |
| `actualizarLugar(id, input: LugarInput)` (10B3) | lugares `PUT /lugares/{id}` (no cambia el estado) | BAD_REQUEST por campo; NOT_FOUND "No existe un lugar con id …" |
| `cambiarEstadoLugar(id, estado: EstadoLugar)` (10B3) | lugares `PATCH /lugares/{id}/estado` con `{estado}` | NOT_FOUND |
| `crearProveedor(input: ProveedorInput)` (10B3) | proveedores `POST /proveedores` (queda activo) | BAD_REQUEST por campo; CONFLICT "Ya existe un proveedor con el nombre …" (sin distinguir mayúsculas ni espacios al inicio o final) |
| `actualizarProveedor(id, input: ProveedorInput)` (10B3) | proveedores `PUT /proveedores/{id}` (no cambia el estado) | BAD_REQUEST; NOT_FOUND; CONFLICT (nombre de otro proveedor) |
| `cambiarEstadoProveedor(id, activo: Boolean)` (10B3) | proveedores `PATCH /proveedores/{id}/estado` con `{activo}` | NOT_FOUND |
| `agregarItemCompra(eventoId, input: ItemCompraDatosInput)` (10B3) | logística `POST /eventos/{id}/compras` (ítem MANUAL, sin marcar) | BAD_REQUEST por campo (`nombreItem` ≤ 120 y `cantidadEstimada` ≤ 80 obligatorios; `nota` ≤ 255); NOT_FOUND "No existe logística para el evento …" (aún no confirmado); CONFLICT "Ya hay un ítem «…» en la lista de compras de este evento." (sin distinguir mayúsculas ni espacios) |
| `editarItemCompra(eventoId, itemId, input: ItemCompraDatosInput)` (10B3) | logística `PUT /eventos/{id}/compras/{itemId}` (calculado o manual; conserva origen y marca) | BAD_REQUEST; NOT_FOUND (ítem de otro evento); CONFLICT (nombre de otro ítem) |
| `quitarItemCompra(eventoId, itemId): ID` (10B3) | logística `DELETE /eventos/{id}/compras/{itemId}` (204; devuelve el id quitado) | CONFLICT "Los ítems calculados por el sistema no se pueden quitar; puedes editarlos o dejarlos sin marcar."; NOT_FOUND |
| `actualizarCompras(eventoId, items: [ItemCompraInput!]!, page, size): PaginaItemCompra` | logística `PUT /eventos/{id}/compras?page&size` con `[{id, comprado}]` (Fase 10); devuelve la lista **ya actualizada** (por defecto `size` 100) | NOT_FOUND "No existe el ítem de compra …" (ítem de otro evento); BAD_REQUEST (lista vacía) |
| `marcarNotificacionLeida(id): MiNotificacion` (11H) | notificaciones `PATCH /notificaciones/{id}/leida` (para el usuario del token; repetirlo no cambia nada) | NOT_FOUND "No existe una notificación con id …" |
| `marcarTodasNotificacionesLeidas: Int` (11H) | notificaciones `PATCH /notificaciones/leidas`: cuántas se marcaron ahora (las de los demás usuarios no cambian) | — |
| `ejecutarRecordatorios: ResultadoRecordatorios` (11E) | eventos `POST /recordatorios/ejecutar` (sin cuerpo): revisa ya los RESERVADOS próximos y envía los EVENTO_PROXIMO que falten. **Solo ADMINISTRADOR** | FORBIDDEN (asistente o token sin rol; no se llama a Eventos, que además respondería 403) |

El resultado de cada mutation es un tipo del schema, así que se pueden pedir sus relaciones en la misma petición (por
ejemplo `crearEvento { id estado cliente { nombreCompleto } }`).

## Equivalencias con el esquema de la Entrega 2

El esquema de la Entrega 2 estaba descrito en texto; se tomaron sus nombres como base y se completó con los DTO REST reales.
Donde el esquema original y el DTO nombran distinto lo mismo, **el schema usa el nombre del esquema original** y el gateway
traduce (anotaciones `@JsonProperty` en sus DTO):

| Tipo | Esquema de la Entrega 2 (texto) | Campo GraphQL | Campo del DTO REST |
|---|---|---|---|
| Evento | identificador | `id` | `id` |
| Evento | tipo de evento, número de invitados, zona, estado | `tipoEvento`, `numInvitados`, `zona`, `estado` | iguales |
| Evento | proveedores asignados | `proveedores` | `GET /proveedores/eventos/{id}` |
| Cliente | identificación | `id` | `id` (el modelo no tiene número de documento) |
| Cotizacion | valor base | `valorBase` | `valorBase` |
| Cotizacion | total incluyendo servicios adicionales | `total` | `total` del **balance** (cotización + adicionales) |
| Cotizacion | — (total de la cotización) | `totalSinAdicionales` | `total` de la cotización |
| Cotizacion | depósito inicial requerido | `depositoInicialRequerido` | `montoReservaRequerido` |
| Contrato | fecha de firma | `fechaFirma` | `firmadoEn` |
| Pago | fecha | `fecha` | `creadoEn` |
| Pago | — | `id`, `claveIdempotencia` | `pagoId`, `eventIdempotencia` |
| Balance | total cotizado | `totalCotizado` | `total` (cotización + adicionales) |
| Balance | monto abonado | `montoAbonado` | `totalAbonado` |
| Balance | — | `valorCotizacion`, `depositoInicialRequerido` | `valorBase`, `montoReservaRequerido` |
| ProveedorAsignado | servicio específico / categoría | `servicio` / `categoria` | `servicioAsignado` / `categoria` del proveedor (`GET /proveedores/{id}`) |
| ServicioAdicional | servicios extra | `fecha` | `creadoEn` |
| Logistica | cantidad de meseros | `meseros` | `meserosRequeridos` |
| Logistica | personal de cocina | `personalCocina` | `personalCocinaRequerido` |
| Logistica | vehículos | `vehiculos` | `camionesTransporte` |

Añadidos respecto a la Entrega 2: enums `EstadoFecha`, `EstadoLugar`, `EstadoAsignacion`, `EstadoNotificacion` y `TipoMensaje`;
`Evento.notificaciones` y `Evento.auditoria` (servicios de la Fase 5C); `Logistica.compras`; las queries
`catalogoServiciosAdicionales`, `lugares`, `proveedores`, `cliente` y `clientes` (Fase 10); los filtros `desde/hasta/sort` de
`eventos`; los inputs `LugarInput`, `ProveedorInput` e `ItemCompraDatosInput`, el enum `OrigenItemCompra` y `ItemCompra.origen/nota` (10B3); y **las mutations**, incluidas `actualizarCompras` (Fase 10) y
las de los directorios (10B3) (en la Entrega 2 GraphQL solo consultaba; ver
[`arquitectura.md`](arquitectura.md)).

## Ejemplos para pegar en GraphiQL

En GraphiQL: el documento en el editor, las variables en la pestaña **Variables** y el token en **Headers**.

### 1. login (sin token)

```graphql
mutation {
  login(correo: "organizadora@ejemplo.com", password: "<contraseña>") { token tipoToken expiracionEnSegundos }
}
```

Copiar `token` en Headers: `{ "Authorization": "Bearer eyJhbGciOi..." }`.

### 2. crearCliente

```graphql
mutation($i: ClienteInput!) { crearCliente(input: $i) { id nombreCompleto correo } }
```
```json
{ "i": { "nombreCompleto": "Valeria Ñáñez Gutiérrez", "telefono": "3011234567", "correo": "valeria@ejemplo.com", "direccion": "Cra. 5 # 10-20, Neiva" } }
```

### 3. fechasDisponibles

```graphql
{ fechasDisponibles(desde: "2027-05-01", hasta: "2027-05-31") { fecha diaSemana estado } }
```

### 4. crearEvento → generarCotizacion → crearContrato → firmarContrato

```graphql
mutation($i: CrearEventoInput!) {
  crearEvento(input: $i) { id estado fecha numInvitados cliente { nombreCompleto } }
}
```
```json
{ "i": { "nombreEvento": "Quince años de Sofía Muñoz", "clienteId": "<id del cliente>", "fecha": "2027-05-29",
         "tipoEvento": "QUINCE_ANOS", "cantidadAdultos": 90, "cantidadNinos": 15, "zona": "NEIVA" } }
```

```graphql
mutation($id: ID!) { generarCotizacion(eventoId: $id) { valorBase totalSinAdicionales depositoInicialRequerido moneda } }
```
```graphql
mutation($id: ID!) { crearContrato(eventoId: $id) { estado valorTotal clienteNombre } }
```
```graphql
mutation($id: ID!) { firmarContrato(eventoId: $id) { estado fechaFirma } }
```
Variables: `{ "id": "<id del evento>" }`.

### 5. registrarPago → confirmarEvento

```graphql
mutation($i: PagoInput!) { registrarPago(input: $i) { id monto moneda metodoPago fecha claveIdempotencia } }
```
```json
{ "i": { "eventoId": "<id del evento>", "clienteId": "<id del cliente>", "monto": 2610000, "metodoPago": "TRANSFERENCIA", "claveIdempotencia": "abono-1" } }
```
(Repetir con la misma `claveIdempotencia` devuelve el mismo pago: no se cobra dos veces.)

```graphql
mutation($id: ID!) { confirmarEvento(id: $id) { estado fecha } }
```

### 6. Lugar, proveedor y servicio adicional

```graphql
{ lugares(estado: ACTIVO, size: 20) { contenido { id nombre capacidad } }
  proveedores(activo: true, size: 20) { contenido { id nombre categoria } }
  catalogoServiciosAdicionales { codigo nombre costoUnitario } }
```
```graphql
mutation($e: ID!, $l: ID!) { asignarLugar(eventoId: $e, lugarId: $l) { estado lugar { nombre capacidad } } }
```
```graphql
mutation($i: AsignarProveedorInput!) { asignarProveedor(input: $i) { servicio categoria costoAcordado estado proveedor { nombre } } }
```
```json
{ "i": { "eventoId": "<id del evento>", "proveedorId": "<id del proveedor>", "servicio": "Sonido y luces", "costoAcordado": 900000 } }
```
```graphql
mutation($id: ID!) { agregarServicioAdicional(eventoId: $id, codigo: "decoracion-premium", cantidad: 1) { nombre cantidad subtotal } }
```

### 7. La ficha completa en UNA query

```graphql
query Ficha($id: ID!) {
  evento(id: $id) {
    id nombreEvento fecha tipoEvento numInvitados zona estado
    cliente { nombreCompleto telefono correo }
    cotizacion { valorBase totalSinAdicionales total depositoInicialRequerido }
    contrato { estado fechaFirma }
    pagos { totalElementos contenido { monto fecha metodoPago } }
    balance { totalCotizado montoAbonado saldoPendiente }
    lugar { nombre capacidad }
    proveedores { contenido { servicio categoria costoAcordado } }
    serviciosAdicionales { nombre cantidad subtotal }
    logistica { meseros personalCocina vehiculos compras(size: 5) { contenido { nombreItem comprado } } }
    notificaciones { contenido { tipo asunto mensaje estado } }
    auditoria { contenido { tipo topico servicioOrigen payload } }
  }
}
```

### 8. Query parcial (solo llama a Eventos y a Clientes)

```graphql
query($id: ID!) { evento(id: $id) { nombreEvento cliente { nombreCompleto } } }
```

Log real del gateway (verificación 6B): solo dos llamadas, ninguna a Finanzas ni a Contratos.

```
REST -> eventos GET /api/v1/eventos/af85094c-d17c-4fda-9d2b-71d71c19166b 200 (8 ms)
REST -> clientes GET /api/v1/clientes/dbb8e626-0f98-4400-b7d2-5124d9eb64d2 200 (10 ms)
```

### 9. Listado y cancelación

```graphql
{ eventos(estado: RESERVADO, tipo: QUINCE_ANOS, size: 10, sort: "fecha,asc") {
    totalElementos contenido { id nombreEvento fecha cliente { nombreCompleto } } } }
```
```graphql
mutation($id: ID!) { cancelarEvento(id: $id) { estado } }
```

### 10. Directorio de clientes y lista de compras (Fase 10)

```graphql
{ clientes(nombre: "Lucía", size: 10, sort: "nombreCompleto,asc") {
    totalElementos contenido { id nombreCompleto telefono correo } } }
```

```graphql
mutation($e: ID!, $i: [ItemCompraInput!]!) {
  actualizarCompras(eventoId: $e, items: $i) { totalElementos contenido { id nombreItem cantidadEstimada comprado } }
}
```
```json
{ "e": "<id del evento>", "i": [ { "id": "<id del ítem>", "comprado": true } ] }
```

La respuesta es la lista de compras completa ya actualizada (la interfaz no necesita otra consulta). Un ítem que no es del
evento → `NOT_FOUND` con el mensaje de Logística y `extensions.servicio: "logistica"`.

### 11. Registro de lugares y proveedores (Fase 10B3)

Requisito de la Entrega 2 (directorio de lugares y proveedores, incluido el registro de nuevos lugares). Las mutations están en
`DirectoriosController` del gateway y delegan en Lugares y Proveedores, que validan.

```graphql
mutation($i: LugarInput!) { crearLugar(input: $i) { id nombre tipo capacidad estado } }
```
```json
{ "i": { "nombre": "Hacienda Las Ceibas", "direccion": "Km 5 vía Rivera", "tipo": "HACIENDA", "capacidad": 250 } }
```
```graphql
mutation($id: ID!, $i: LugarInput!) { actualizarLugar(id: $id, input: $i) { capacidad } }
mutation($id: ID!) { cambiarEstadoLugar(id: $id, estado: INACTIVO) { estado } }
```
```graphql
mutation($i: ProveedorInput!) { crearProveedor(input: $i) { id nombre categoria activo } }
```
```json
{ "i": { "nombre": "Flores del Huila", "categoria": "FLORISTERIA", "contacto": "3104567890" } }
```
```graphql
mutation($id: ID!) { cambiarEstadoProveedor(id: $id, activo: false) { activo } }
```

Un proveedor con el mismo nombre (por ejemplo, en minúsculas) → `CONFLICT` con `extensions.servicio: "proveedores"` y
`status: 409`; un lugar sin capacidad → `BAD_REQUEST` con `extensions.errores: [{campo: "capacidad", mensaje: "no debe ser nulo"}]`.

### 12. Lista de chequeo de compras y suministros (Fase 10B3)

Requisito de la Entrega 2. Cada `ItemCompra` trae `origen` (`CALCULADO`: lo generó Logística al confirmar el evento;
`MANUAL`: lo agregó la organizadora) y `nota`. Se pueden agregar ítems manuales, editar cualquiera y **quitar solo los
manuales**; la marca de "conseguido" sigue siendo `actualizarCompras`. El recálculo por un servicio adicional **no toca la
lista** (conserva los ítems manuales y las marcas).

```graphql
mutation($e: ID!, $i: ItemCompraDatosInput!) {
  agregarItemCompra(eventoId: $e, input: $i) { id nombreItem cantidadEstimada comprado origen nota }
}
```
```json
{ "e": "<id del evento confirmado>", "i": { "nombreItem": "Recuerdos para invitados", "cantidadEstimada": "100 unidades", "nota": "Bolsas de tela" } }
```
```graphql
mutation($e: ID!, $id: ID!, $i: ItemCompraDatosInput!) { editarItemCompra(eventoId: $e, itemId: $id, input: $i) { nombreItem origen comprado } }
mutation($e: ID!, $id: ID!) { quitarItemCompra(eventoId: $e, itemId: $id) }
```

Quitar un ítem calculado → `CONFLICT` con el mensaje de Logística; agregar a un evento aún no confirmado → `NOT_FOUND` "No
existe logística para el evento …" (la interfaz muestra "La lista de compras se genera al confirmar el evento.").

### 13. Recordatorios de eventos próximos (Fase 11E, solo ADMINISTRADOR)

La misma revisión que la tarea programada de Eventos (`RECORDATORIOS_CRON`), ejecutada ya. Cada evento RESERVADO entre
mañana y hoy + 7 recibe el recordatorio del umbral más pequeño que aplica (7 o 1 día), **una sola vez**; si ya lo tenía,
sale `YA_ENVIADO`. Notificaciones genera "Faltan N días para su evento…" (tipo `EVENTO_PROXIMO`, visible en
`Evento.notificaciones`) y Auditoría lo registra.

```graphql
mutation {
  ejecutarRecordatorios {
    hoy umbralesDias revisados enviados yaEnviados
    recordatorios { eventoId nombreEvento fecha diasRestantes umbralDias resultado }
  }
}
```

Con el token del asistente → `FORBIDDEN` (`extensions.status: 403`, `servicio: "gateway"`, "No tienes permiso para realizar
esta operación.") y `data: null`.

### 14. La campanita de notificaciones (Fase 11H, los dos roles)

El estado "leída" es **por usuario**: el servicio lo saca del `sub` del token, así que la misma query da resultados distintos
para la organizadora y para el asistente. La interfaz consulta `notificacionesSinLeer` cada 30 s (polling) y pide la lista
con el nombre del evento solo al abrir el panel.

```graphql
query { notificacionesSinLeer }

query {
  misNotificaciones(page: 0, size: 10) {
    contenido { id asunto mensaje fechaCreacion leida leidaEn evento { nombreEvento } }
  }
}

mutation($id: ID!) { marcarNotificacionLeida(id: $id) { id leida leidaEn } }
mutation { marcarTodasNotificacionesLeidas }
```

Con 10 notificaciones de 3 eventos, `evento { nombreEvento }` hace **3** llamadas a Eventos, no 10 (`@BatchMapping`). Un id
que no existe → `NOT_FOUND` con `extensions.servicio: "notificaciones"`.

## Errores

Los errores de los servicios llegan como errores GraphQL: `message` = el mensaje en español del servicio, `path` = el campo,
`extensions.classification`, `extensions.status` (código HTTP del servicio), `extensions.servicio` y, si el servicio lo envió,
`extensions.errores: [{campo, mensaje}]`. **Nunca hay stack traces** (lo inesperado se registra en el log del gateway y se
responde `INTERNAL_ERROR` con un mensaje neutro).

| Servicio | classification |
|---|---|
| 400 | `BAD_REQUEST` (con `errores[]`) |
| 401 | `UNAUTHORIZED` (el de Auth trae su mensaje: "Correo o contraseña incorrectos."). Sin token, vencido o inválido lo rechaza **el propio gateway** antes de llamar a nadie (`servicio: gateway`; mensajes en [`seguridad.md`](seguridad.md#tabla-de-errores-de-seguridad-para-la-sustentación)) |
| 403 | `FORBIDDEN`. **Fase 11D:** además, el propio gateway responde FORBIDDEN (`servicio: gateway`, `status: 403`, "No tienes permiso para realizar esta operación.") **sin llamar a ningún servicio** cuando el rol del token no es ADMINISTRADOR en `crearLugar`, `actualizarLugar`, `cambiarEstadoLugar`, `crearProveedor`, `actualizarProveedor`, `cambiarEstadoProveedor` y el campo `Evento.auditoria` (en la ficha solo ese campo falla). La interfaz pide la ficha con `auditoria @include(if: $verAuditoria)`. Matriz en [`seguridad.md`](seguridad.md#autenticación-y-autorización-roles-fase-11d) |
| 404 | `NOT_FOUND` (en las queries raíz; en un campo anidado, 404 = `null` sin error) |
| 409 | `CONFLICT` |
| 502/503/504, sin respuesta o timeout | `SERVICE_UNAVAILABLE` |
| otro | `INTERNAL_ERROR` |

**Código de referencia (Fase 11C):** cada petición puede traer el header `X-Request-Id` (la interfaz envía un UUID; si falta
o no es `[A-Za-z0-9-]` de 8 a 64 caracteres, el gateway genera uno). El gateway lo pone en el MDC (cada línea de su log lleva
`[ref=…]`), lo copia al `GraphQLContext`, lo reenvía a cada servicio en el mismo header (también en las llamadas del
DataLoader de proveedores) y lo devuelve en la respuesta. Un error técnico (status ≥ 500 o inesperado) se registra como
`Error técnico ref=<uuid> <path> -> <servicio> (<status>): <mensaje>`. La respuesta GraphQL **no cambia**; es la interfaz la
que muestra un mensaje genérico con "Ref: <8 caracteres>" para los errores técnicos (ver
[`frontend.md`](frontend.md#mensajes-de-error-técnicos-al-log-genéricos-en-pantalla-fase-11c)). Prueba:
`ReferenciaPeticionTest` (servidor HTTP real que hace de los servicios).

Un argumento con formato inválido (por ejemplo un id que no es UUID) → `BAD_REQUEST`. Un documento que no cumple el schema
(falta un argumento obligatorio, un campo que no existe) lo rechaza graphql-java con `ValidationError` antes de llamar a nadie.

Ejemplos reales (verificación 6B):

```json
{ "errors": [{ "message": "El contrato del evento está en estado PENDIENTE; debe estar FIRMADO para confirmar el evento.",
               "path": ["confirmarEvento"],
               "extensions": { "servicio": "eventos", "status": 409, "classification": "CONFLICT" } }],
  "data": null }
```

```json
{ "errors": [{ "message": "Los datos enviados no son válidos. Revisa los campos indicados.",
               "path": ["crearCliente"],
               "extensions": { "servicio": "clientes", "status": 400, "classification": "BAD_REQUEST",
                               "errores": [{ "campo": "correo", "mensaje": "debe ser una dirección de correo electrónico con formato correcto" }, … ] } }],
  "data": null }
```

Con un servicio detenido solo falla su campo y **el resto de la respuesta se entrega** (contratos-service detenido):

```json
{ "errors": [{ "message": "El servicio de contratos no está disponible en este momento. Intenta de nuevo más tarde.",
               "path": ["evento", "contrato"],
               "extensions": { "servicio": "contratos", "status": 503, "classification": "SERVICE_UNAVAILABLE" } }],
  "data": { "evento": { "estado": "RESERVADO", "contrato": null, "balance": { "saldoPendiente": 6340000.00 } } } }
```

(En las mutations el tipo de retorno no admite `null` — por ejemplo `Evento!` —, así que ante un error `data` es `null`.)

## Descarga de los PDF: rutas REST `/documentos` del gateway (Fase 10)

GraphQL no sirve bien contenido binario (habría que mandar el PDF en base64 dentro del JSON: un 33 % más pesado y todo el
archivo en memoria), así que para la interfaz web el gateway tiene **dos rutas REST de solo lectura** que reenvían a
contratos-service con el mismo token, sin lógica de negocio (`DocumentosController`). Contrato:
[`openapi/gateway-documentos.yaml`](../openapi/gateway-documentos.yaml).

| Ruta del gateway | Reenvía a (contratos-service) | Errores |
|---|---|---|
| `GET /documentos/eventos/{eventoId}/contrato.pdf` | `GET /api/v1/eventos/{id}/contrato/pdf` | 404 sin contrato |
| `GET /documentos/eventos/{eventoId}/anexo.pdf` | `GET /api/v1/eventos/{id}/adicionales/anexo-pdf` | 404 sin contrato; **409 sin servicios adicionales** |

- **Token:** header `Authorization: Bearer <token>` (nunca en la URL, para que no quede en logs ni en el historial). Lo valida
  el `JwtAuthenticationFilter` del gateway antes de llamar a Contratos (sin token, vencido o inválido → 401 sin ninguna
  línea `REST ->`), y contratos-service lo valida otra vez.
- **Respuesta:** `200 application/pdf`, `Content-Disposition: attachment; filename="contrato-<eventoId>.pdf"` (o `anexo-…`),
  `Cache-Control: no-store` y `X-Content-Type-Options: nosniff`.
- **Errores** con el formato `ErrorResponse` (`timestamp, status, error, message, path`) y el mensaje en español de
  Contratos: 400 (id que no es UUID), 401, 404, 409; 503 si Contratos no responde (o responde 502/503/504) y 502 ante otro 5xx.
  Para recibirlos en JSON, la petición envía `Accept: application/pdf, application/json`.
- **CORS** (los orígenes de `CORS_ALLOWED_ORIGINS`): `GET` y `OPTIONS`, cabeceras `Authorization` y `Accept`, y expone
  `Content-Disposition`. La regla de `/graphql` no cambió (ver [`seguridad.md`](seguridad.md#cors)).
- En la interfaz: `fetch` con el header, el PDF como blob, un enlace temporal con `download` y se libera.

## El problema N+1 y lo que resuelve `@BatchMapping`

En un listado como `eventos { contenido { cliente { … } cotizacion { … } } }`, sin precauciones el gateway haría 1 llamada
para la página y luego **N llamadas por cada campo** (una por evento): el problema N+1.

- **Qué resuelve `@BatchMapping`** (`Evento.cliente`, `Evento.lugar`) y el DataLoader de proveedores: graphql-java agrupa
  todos los eventos de la respuesta y el gateway hace **una llamada por id distinto**. 20 eventos de 3 clientes → 3 llamadas
  a Clientes, no 20 (verificado: 3 eventos del mismo cliente → 1 llamada). Eventos sin `lugarId` no llaman a Lugares.
  `ProveedorAsignado.proveedor` y `.categoria` comparten la carga: una llamada por proveedor aunque se pidan los dos.
- **Qué NO resuelve:** los servicios REST solo tienen endpoints de **un** recurso (`GET /clientes/{id}`,
  `GET /eventos/{id}/cotizacion`…). Por eso:
  - con clientes, lugares y proveedores distintos sigue habiendo una llamada por cada uno;
  - `cotizacion`, `balance`, `contrato`, `pagos`, `proveedores`, `serviciosAdicionales`, `logistica`, `notificaciones` y
    `auditoria` son **propios de cada evento**: no hay ids repetidos que agrupar y siguen siendo **una llamada por evento y
    por campo** (con 100 eventos y 3 de esos campos: hasta 300 llamadas).
  - Mitigaciones actuales: los campos solo se resuelven si se piden, `size` ≤ 100, la caché Redis de Clientes, Lugares,
    Proveedores y Logística, y que la ficha completa se pide por evento (`evento(id)`), no por listado.
- **Endpoints por lotes que harían falta** (mejora posible; **decisión del 2026-10-29: no se implementan** en este proyecto,
  basta con esta explicación):

  | Endpoint | Para |
  |---|---|
  | `GET /api/v1/clientes?ids=a,b,c` | `Evento.cliente` en una llamada por página |
  | `GET /api/v1/lugares?ids=…` y `GET /api/v1/proveedores?ids=…` | `Evento.lugar`, `ProveedorAsignado.proveedor` |
  | `GET /api/v1/cotizaciones?eventoIds=…` y `GET /api/v1/balances?eventoIds=…` | `cotizacion`, `balance`, `Cotizacion.total` |
  | `GET /api/v1/contratos?eventoIds=…` | `contrato` (hoy `GET /contratos` lista sin filtro por evento) |
  | `GET /api/v1/pagos?eventoIds=…`, `GET /api/v1/adicionales?eventoIds=…` | `pagos`, `serviciosAdicionales` |
  | `GET /api/v1/logistica?eventoIds=…` | `logistica` |

  Con ellos, cada campo de un listado sería **una sola llamada** (`@BatchMapping` que recibe la lista de eventos y llama al
  endpoint por lotes con todos los ids).

## Pruebas

- Tras la Fase 10: **87 pruebas** (+1 en `MutacionesGraphQlTest`: `cambiarEstadoAsignacion` reenvía el id y el estado).
- Fase 10B3c: **86 pruebas** (+8): `ItemsCompraGraphQlTest` (6: agregar, editar y quitar delegan;
  quitar devuelve el id; quitar un calculado → CONFLICT con el mensaje de Logística; agregar inválido → BAD_REQUEST con
  `errores[]`; evento sin logística → NOT_FOUND) y 2 en `DirectoriosRemotoTest` (POST, PUT y DELETE a Logística con el
  cuerpo y el token; el 409 se conserva).
- Fase 10B3b: **78 pruebas** (+10): `DirectoriosGraphQlTest` (6: crear, actualizar y cambiar el estado
  de lugares y proveedores delegan; lugar sin capacidad → BAD_REQUEST con `errores[]`; proveedor duplicado → CONFLICT con el
  mensaje de Proveedores; lugar inexistente → NOT_FOUND) y `DirectoriosRemotoTest` (4, `MockRestServiceServer`: método, ruta,
  cuerpo y token que llegan a Lugares y Proveedores; el 409 conserva el mensaje).
- Fase 10B1: **68 pruebas** (+19): `ClientesYComprasGraphQlTest` (4: `clientes` delega filtro, paginación y
  orden, y usa los valores por defecto del schema; `actualizarCompras` envía los ítems tal cual y devuelve la lista; ítem de
  otro evento → NOT_FOUND con el mensaje de Logística), `DocumentosControllerTest` (10, MockMvc: PDF adjunto con
  `no-store`, token reenviado, sin token / vencido / manipulado → 401 **sin llamar a Contratos**, 409 del anexo, 404, 503, id
  que no es UUID → 400, preflight desde `http://localhost:5173` 200 y desde otro origen o con otro método 403,
  `Content-Disposition` expuesto) y `DocumentosYComprasRemotoTest` (5, `MockRestServiceServer`: lo que se envía a Contratos,
  Logística y Clientes).
- Hasta la Fase 9: **49 pruebas** desde la Fase 7 (+ `SeguridadGatewayTest` 14 y `TokenInterceptorTest` 4 → 6; ver
  [`seguridad.md`](seguridad.md#pruebas-y-verificación)). En la Fase 6 eran 33 (`gradlew.bat clean build`): `ConsultasGraphQlTest` (8) y `MutacionesGraphQlTest` (8) con
  `GraphQlTester` y los clientes REST simulados (ficha completa, query parcial sin llamar a Finanzas ni Contratos, batch,
  404, 409 con su mensaje, 400 con `errores[]` por campo, 503 parcial, mutations que delegan, login), `ClienteRemotoTest` (10,
  `MockRestServiceServer`), `RestClientConfigTest` (3, contra un servidor HTTP real: PATCH y el 401 de un POST) y
  `TokenInterceptorTest` (4, MockMvc: el header Authorization llega al cliente REST; GraphiQL apagado sin `dev`).
- Docker: `scripts/verificacion/verificar_6A.ps1` (queries, ficha, query parcial, batch, errores) y `verificar_6B.ps1` (el flujo
  completo 100 % por GraphQL, errores de las mutations y la ficha con lo generado por Kafka).
