#!/bin/sh
# API para la revisión local. Ejecutar después de crear la base.
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
DB_NAME=${1:-revision_hasani}
command -v java >/dev/null 2>&1 || { printf '%s\n' 'Se requiere JDK 21 en PATH.' >&2; exit 1; }
POSTAL_FILE=${SEPOMEX_ARCHIVO:-$PROJECT_DIR/datos/CPdescargatxt.zip}
[ -f "$POSTAL_FILE" ] || { printf '%s\n' 'Falta datos/CPdescargatxt.zip. Extrae el paquete completo o configura SEPOMEX_ARCHIVO.' >&2; exit 1; }
export DB_PASSWORD=${DB_PASSWORD:-${PGPASSWORD:-}}
[ -n "$DB_PASSWORD" ] || { printf '%s\n' 'Configura PGPASSWORD o DB_PASSWORD con la contraseña PostgreSQL.' >&2; exit 1; }
if [ -z "${JWT_SECRET:-}" ]; then
    command -v openssl >/dev/null 2>&1 || { printf '%s\n' 'Configura JWT_SECRET en Base64 o instala openssl para generarlo.' >&2; exit 1; }
    JWT_SECRET=$(openssl rand -base64 32)
fi
export JWT_SECRET
export SEPOMEX_ARCHIVO=$POSTAL_FILE
export SPRING_DATASOURCE_URL="jdbc:postgresql://${PGHOST:-localhost}:${PGPORT:-5432}/$DB_NAME"
export SPRING_DATASOURCE_USERNAME=${PGUSER:-postgres}
cd "$PROJECT_DIR"
if [ ! -f build/libs/prueba-1.0.jar ]; then sh ./gradlew bootJar; fi
printf '%s\n' "API de revisión: http://localhost:${REVISION_PORT:-8081}/swagger-ui.html. Ctrl+C para detener."
exec java -jar build/libs/prueba-1.0.jar --spring.profiles.active=qa "--server.port=${REVISION_PORT:-8081}"
