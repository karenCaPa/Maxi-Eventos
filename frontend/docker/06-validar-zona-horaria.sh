#!/bin/sh
# Maxi Eventos (Fase 11H). Lo ejecuta el entrypoint de nginx ANTES de generar la configuración desde la plantilla.
# FRONTEND_ZONA_HORARIA (= ZONA_HORARIA del .env, la misma de los servicios) termina dentro de /config.js: solo se acepta un
# nombre de zona IANA como America/Bogota (letras, números, "_", "+", "-" y "/"). Si no es válido, el contenedor no arranca.
set -eu
zona="${FRONTEND_ZONA_HORARIA:-}"
if ! printf '%s' "$zona" | grep -Eq '^[A-Za-z_]+(/[A-Za-z0-9_+-]+){0,2}$'; then
    echo "maxi-eventos: FRONTEND_ZONA_HORARIA no es válida. Debe ser una zona IANA como America/Bogota." >&2
    exit 1
fi
echo "maxi-eventos: la interfaz mostrará las horas en la zona $zona"
