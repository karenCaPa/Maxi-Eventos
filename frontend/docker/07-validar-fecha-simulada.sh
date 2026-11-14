#!/bin/sh
# Maxi Eventos (Fase 11I). Lo ejecuta el entrypoint de nginx ANTES de generar la configuración desde la plantilla.
# FRONTEND_FECHA_SIMULADA es OPCIONAL: vacía (lo normal) = la interfaz usa la fecha real. Si se define, debe ser una fecha
# AAAA-MM-DD que exista (termina dentro de /config.js: no se acepta nada más). La interfaz la usa como "hoy" SOLO para lo que
# calcula ella (Realizado / Vencido, "Eventos pasados", "Próximos 7 días", calendario) y lo avisa en la barra; el backend
# sigue con su reloj real. Si no es válida, el contenedor no arranca (y el log dice por qué).
set -eu
fecha="${FRONTEND_FECHA_SIMULADA:-}"
if [ -z "$fecha" ]; then
    echo "maxi-eventos: sin fecha simulada (la interfaz usa la fecha real)"
    exit 0
fi
if ! printf '%s' "$fecha" | grep -Eq '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' || ! printf '%s' "$fecha" | awk -F- '{
        a = $1 + 0; m = $2 + 0; d = $3 + 0
        split("31 28 31 30 31 30 31 31 30 31 30 31", dias, " ")
        if (a % 4 == 0 && (a % 100 != 0 || a % 400 == 0)) dias[2] = 29
        exit !(a >= 2000 && m >= 1 && m <= 12 && d >= 1 && d <= dias[m])
    }'; then
    echo "maxi-eventos: FRONTEND_FECHA_SIMULADA no es válida. Debe ser una fecha AAAA-MM-DD que exista (o quedar vacía)." >&2
    exit 1
fi
echo "maxi-eventos: MODO DEMOSTRACIÓN, la interfaz usará la fecha simulada $fecha como hoy (el backend sigue con la real)"
