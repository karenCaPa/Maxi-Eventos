#!/bin/sh
# Maxi Eventos (Fase 10C). Lo ejecuta el entrypoint de nginx ANTES de generar la configuración desde la plantilla.
# FRONTEND_GATEWAY_URL termina dentro de la CSP y de /config.js: solo se acepta un origen (esquema, host y puerto opcional,
# sin ruta, comillas, espacios ni barra final). Si no es válido, el contenedor no arranca (y el log dice por qué).
set -eu
url="${FRONTEND_GATEWAY_URL:-}"
if ! printf '%s' "$url" | grep -Eq '^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$'; then
    echo "maxi-eventos: FRONTEND_GATEWAY_URL no es válida. Debe ser un origen como http://localhost:8090 (esquema, host y puerto, sin ruta ni barra final)." >&2
    exit 1
fi
echo "maxi-eventos: la interfaz usará el gateway $url"
