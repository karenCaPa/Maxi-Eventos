# docker/

Carpeta reservada para scripts de soporte de Docker (por ejemplo, inicialización de la base de datos).

Por ahora no hace falta ningún script: la base `maxi_eventos` la crea la imagen de PostgreSQL a partir de la
variable `POSTGRES_DB`, y cada microservicio crea sus propias tablas al arrancar.

El archivo de orquestación es [`../docker-compose.yml`](../docker-compose.yml) y cada servicio tiene su
`Dockerfile` multi-etapa en `services/<servicio>/Dockerfile` (compila con Gradle dentro de Docker).
