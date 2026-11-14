# Contratos OpenAPI

Única fuente de los contratos REST de Maxi Eventos (OpenAPI 3.0.3). Un archivo por microservicio:

| Archivo | Servicio | Puerto local |
|---|---|---|
| `auth.yaml` | auth-service | 8080 |
| `eventos.yaml` | eventos-service | 8081 |
| `clientes.yaml` | clientes-service | 8082 |
| `lugares.yaml` | lugares-service | 8083 |
| `finanzas.yaml` | finanzas-service | 8084 |
| `contratos.yaml` | contratos-service | 8085 |
| `proveedores.yaml` | proveedores-service | 8086 |
| `auditoria.yaml` | auditoria-service | 8087 |
| `logistica.yaml` | logistica-service | 8088 |
| `notificaciones.yaml` | notificaciones-service | 8089 |

Cada servicio expone además su documentación generada por Springdoc en
`http://localhost:<puerto>/swagger-ui.html`.

## Estado (Fase 9B)

Los 10 contratos están alineados con el código: `scripts\verificacion\comparar-openapi.ps1` los compara con el
`/v3/api-docs` de cada servicio en marcha y da **0 diferencias reales y 0 reglas incumplidas**.

- Seguridad `bearerAuth` (JWT) en todas las operaciones salvo `POST /auth/login`; **401** documentado en todas las protegidas.
- **400** en toda operación con parámetros de ruta, consulta o cuerpo (incluida la ruta sin identificador, por ejemplo
  `/eventos//confirmar`), **500** en todas, y 404/409/503 donde el servicio los produce. Todos los errores usan `ErrorResponse`.
- Listados con `PaginaBase` (`contenido, pagina, tamanoPagina, totalElementos, totalPaginas`), `page`, `size` (máximo 100) y
  `sort` con los campos permitidos de cada operación.
- Ejemplos con los **datos de demostración** de `cargar-demo.ps1` (por ejemplo "Lucía Fernández Ospina" y "Salón de Eventos Los
  Ocobos") y los **mensajes de error reales** de cada servicio. Los ids de los ejemplos son los de la carga del 2026-10-30;
  cambian si se vuelve a cargar sobre una base nueva.
- **Reto de la semana 8** en `finanzas.yaml` (`POST /finanzas/pagos`): ejemplos `retoMonedaDistinta` (400 en el campo `moneda`)
  y `retoMontoMayorQueElSaldo` (409 con el saldo en el mensaje).

Lo que `/v3/api-docs` no puede comprobar (y por eso el comparador lo trata como regla del contrato o como cosmético):
Springdoc muestra **200** en todas las operaciones (no ve el 201/204 de `ResponseEntity`) ni los errores del advice y de
seguridad; los códigos reales se verifican con Postman y con las pruebas automáticas.
