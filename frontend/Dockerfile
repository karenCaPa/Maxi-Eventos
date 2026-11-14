# syntax=docker/dockerfile:1
# Interfaz de Maxi Eventos (Fase 10C): se compila con Node dentro de Docker y se sirve con nginx.
# Versiones fijas (nunca :latest). La URL del gateway NO se compila en el JavaScript: nginx la entrega en /config.js y en la
# CSP al arrancar, con FRONTEND_GATEWAY_URL (la misma imagen sirve para cualquier entorno).

# ---------- Etapa 1: compilación (npm ci + vite build) ----------
FROM node:22.16.0-alpine AS build
WORKDIR /app
# Primero las dependencias, para aprovechar la caché de capas; npm ci respeta package-lock.json (versiones exactas)
COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY index.html vite.config.js ./
COPY public public
COPY src src
RUN npm run build \
    # el /config.js de desarrollo (vacío) no se publica: en el contenedor lo genera nginx
    && rm -f dist/config.js

# ---------- Etapa 2: nginx (solo archivos estáticos) ----------
FROM nginx:1.28.0-alpine
# Solo se sustituyen las variables que empiezan por FRONTEND_ (las de nginx, como $uri, quedan intactas).
# Fase 11I: FRONTEND_FECHA_SIMULADA es opcional; vacía = fecha real (ver docker/07-validar-fecha-simulada.sh).
ENV NGINX_ENVSUBST_FILTER=^FRONTEND_ \
    FRONTEND_GATEWAY_URL=http://localhost:8090 \
    FRONTEND_ZONA_HORARIA=America/Bogota \
    FRONTEND_FECHA_SIMULADA=""
COPY docker/maxi-eventos.conf.template /etc/nginx/templates/maxi-eventos.conf.template
COPY docker/05-validar-gateway-url.sh /docker-entrypoint.d/05-validar-gateway-url.sh
COPY docker/06-validar-zona-horaria.sh /docker-entrypoint.d/06-validar-zona-horaria.sh
COPY docker/07-validar-fecha-simulada.sh /docker-entrypoint.d/07-validar-fecha-simulada.sh
COPY --from=build /app/dist /usr/share/nginx/html
# Sin el sitio por defecto del puerto 80; 2 procesos de trabajo (con "auto" abre uno por núcleo del equipo, demasiados para
# 64 MB y una sola usuaria); finales de línea LF (los archivos pueden venir de Windows) y permiso de ejecución
RUN rm -f /etc/nginx/conf.d/default.conf \
    && sed -i 's/^worker_processes .*/worker_processes 2;/' /etc/nginx/nginx.conf \
    && sed -i 's/\r$//' /etc/nginx/templates/maxi-eventos.conf.template /docker-entrypoint.d/05-validar-gateway-url.sh \
        /docker-entrypoint.d/06-validar-zona-horaria.sh /docker-entrypoint.d/07-validar-fecha-simulada.sh \
    && chmod 0755 /docker-entrypoint.d/05-validar-gateway-url.sh /docker-entrypoint.d/06-validar-zona-horaria.sh \
        /docker-entrypoint.d/07-validar-fecha-simulada.sh
EXPOSE 5173
