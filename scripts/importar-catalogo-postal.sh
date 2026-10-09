#!/bin/sh
# Reutiliza el importador Java de la API sin iniciar Spring ni proveedores.
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
DB_NAME=${1:-revision_hasani}
POSTAL_FILE=${SEPOMEX_ARCHIVO:-$PROJECT_DIR/datos/CPdescargatxt.zip}
[ -f "$POSTAL_FILE" ] || { printf '%s\n' 'Falta datos/CPdescargatxt.zip. Extrae el paquete completo o configura SEPOMEX_ARCHIVO.' >&2; exit 1; }
command -v java >/dev/null 2>&1 || { printf '%s\n' 'Se requiere JDK 21 en PATH.' >&2; exit 1; }
cd "$PROJECT_DIR"
if [ ! -f build/libs/prueba-1.0.jar ]; then sh ./gradlew bootJar; fi
if [ "${2:-}" = '--preparar' ]; then exit 0; fi
if ! java -Dloader.main=com.proyecto.servicios.service.ImportadorPostalCli -cp build/libs/prueba-1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher "jdbc:postgresql://${PGHOST:-localhost}:${PGPORT:-5432}/$DB_NAME" "${PGUSER:-postgres}" "$POSTAL_FILE"; then
    printf '%s\n' "Falló la importación postal. El esquema se conserva; corrige la conexión o el ZIP y repite: sh scripts/importar-catalogo-postal.sh $DB_NAME" >&2
    exit 1
fi
