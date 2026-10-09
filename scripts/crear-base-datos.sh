#!/bin/sh
# Entrada Linux/POSIX. El esquema completo está en un único SQL.
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DB_NAME=${1:-revision_hasani}
sh "$SCRIPT_DIR/importar-catalogo-postal.sh" "$DB_NAME" --preparar
if [ -n "${DOCKER_CONTAINER:-}" ]; then
    docker exec -i "$DOCKER_CONTAINER" psql -X -v ON_ERROR_STOP=1 -v "base_datos=$DB_NAME" -U "${PGUSER:-postgres}" -d postgres < "$SCRIPT_DIR/crear-base-datos.sql"
else
    command -v psql >/dev/null 2>&1 || { printf '%s\n' 'Se requiere psql de PostgreSQL 17 o DOCKER_CONTAINER configurado.' >&2; exit 1; }
    psql -X -v ON_ERROR_STOP=1 -v "base_datos=$DB_NAME" -d postgres -f "$SCRIPT_DIR/crear-base-datos.sql"
fi
sh "$SCRIPT_DIR/importar-catalogo-postal.sh" "$DB_NAME"
