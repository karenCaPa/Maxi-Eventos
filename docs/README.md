# Documentación de Maxi Eventos

Documentación técnica del proyecto. La guía general (requisitos, `.env`, cómo levantar y probar) está en el
[`README.md`](../README.md) de la raíz.

| Documento | Contenido |
|---|---|
| [`arquitectura.md`](arquitectura.md) | Servicios, puertos, responsabilidades, diagrama Mermaid, el gateway (mutations que delegan en REST), comunicación síncrona y flujo asíncrono |
| [`modelo-datos.md`](modelo-datos.md) | Tablas de cada servicio en la base `maxi_eventos`, ER en Mermaid, outbox e idempotencia, diferencias con el modelo de la Entrega 2 |
| [`kafka.md`](kafka.md) | Tópicos, productores, consumidores, consumer groups, sobre, outbox, idempotencia, errores y DLT, mejoras futuras |
| [`graphql.md`](graphql.md) | Schema, queries, mutations, equivalencias con la Entrega 2, ejemplos para GraphiQL, errores y N+1 |
| [`seguridad.md`](seguridad.md) | Público y protegido, sin roles, flujo y claims del token, `/graphql` e introspección, defensa en profundidad, CORS, token y CSP de la interfaz, secretos |
| [`frontend.md`](frontend.md) | Interfaz web (Fase 10): pantallas, la ficha en una sola vista, calendario de fechas, estructura, Docker con nginx y `FRONTEND_GATEWAY_URL`, pruebas |
| [`evidencias.md`](evidencias.md) | Índice de las guías de capturas y tabla de cada punto de las diapositivas con su evidencia |
| [`evidencias-p2e1.md`](evidencias-p2e1.md) | Guía de capturas de la P2 · Entrega 1 (API REST, OpenAPI, Postman, pruebas, Docker Compose) |
| [`evidencias-p2e2.md`](evidencias-p2e2.md) | Guía de capturas de la P2 · Entrega 2 (Kafka) |
| [`evidencias-p3e1-graphql.md`](evidencias-p3e1-graphql.md) | Guía de capturas de la P3 · Entrega 1 (GraphQL y seguridad) |
| [`sustentacion.md`](sustentacion.md) | Demostraciones en vivo para la presentación y las preguntas de cierre de las diapositivas (semanas 4 a 13) con su respuesta |

Los contratos REST están en [`../openapi/`](../openapi/) y la colección de Postman en [`../postman/`](../postman/).
