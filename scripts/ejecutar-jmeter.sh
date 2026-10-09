#!/bin/sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
: "${JMETER_HOME:?Configura JMETER_HOME con la carpeta binaria de JMeter 5.6.3}"
DATA_FILE=${JMETER_DATOS:-$PROJECT_DIR/.local-data/entrega-qa/usuarios.csv}
[ -f "$DATA_FILE" ] || { printf '%s\n' 'Primero prepara el CSV de usuarios QA.' >&2; exit 1; }
DURATION=${JMETER_DURACION:-60}
case $DURATION in ''|*[!0-9]*) printf '%s\n' 'Duración inválida.' >&2; exit 1;; esac
[ "$DURATION" -ge 1 ] && [ "$DURATION" -le 240 ] || { printf '%s\n' 'Duración permitida: 1–240 segundos.' >&2; exit 1; }
OUT_DIR=$PROJECT_DIR/tests/jmeter/resultados/$(date +%Y%m%d-%H%M%S)
mkdir -p "$OUT_DIR"
for USERS in 1 10 25; do
    sh "$JMETER_HOME/bin/jmeter" -n -t "$PROJECT_DIR/tests/jmeter/clientes-local.jmx" -l "$OUT_DIR/usuarios-$USERS.jtl" -j "$OUT_DIR/usuarios-$USERS.log" -e -o "$OUT_DIR/usuarios-$USERS-html" "-Jusuarios=$USERS" "-Jduracion=$DURATION" -Jrampa=10 -Jhost=localhost "-Jpuerto=${REVISION_PORT:-8081}" "-Jdatos=$DATA_FILE" -Jjmeter.save.saveservice.response_data=false -Jjmeter.save.saveservice.samplerData=false -Jjmeter.save.saveservice.requestHeaders=false -Jjmeter.save.saveservice.responseHeaders=false
done
printf '%s\n' "Resultados: $OUT_DIR"
