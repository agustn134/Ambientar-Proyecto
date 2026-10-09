# Instalación y ejecución reproducible

## Ejecución para la revisión: Windows y Linux

La base completa se prepara con **una orden**, utilizando el iniciador Windows o Linux mostrado abajo. El DDL está reunido en [`scripts/crear-base-datos.sql`](../scripts/crear-base-datos.sql): crea una base nueva, las 15 tablas, sus secuencias, índices y restricciones, los catálogos personales y el historial de Flyway correspondiente al esquema entregado. A continuación, el iniciador carga el ZIP postal incluido mediante el importador Java. Ejecutar únicamente el SQL prepara el esquema; los iniciadores completan también sus datos postales.

V1–V8 se conservan como historial del desarrollo. No es necesario ejecutarlas por separado al utilizar el SQL de entrega. Su historial procede de la base QA comprobada; al arrancar, Flyway valida los archivos originales y reconoce el esquema en versión 8.

### Requisitos previos

| Requisito | Uso |
|---|---|
| JDK 21 | Ejecutar el JAR y compilar si se usa el código fuente |
| PostgreSQL 17 | Persistencia; usuario con permiso para crear una base |
| Cliente `psql` o PostgreSQL en Docker | Ejecutar el SQL único |
| ZIP postal incluido en `datos/` | Cargar automáticamente el catálogo al crear la base |
| Python 3 y JMeter 5.6.3 | Opcionales, para repetir la carga |

PostgreSQL debe estar iniciado. Si `psql` no está en PATH, añadir la carpeta `bin` de PostgreSQL o utilizar la alternativa Docker descrita más abajo. No se necesita instalar Gradle: el proyecto incluye su Wrapper. En una primera compilación necesita Internet para descargar dependencias.

El catálogo nacional ya está incluido en `datos/CPdescargatxt.zip`: **159,340 asentamientos, 2,478 municipios y 32 entidades**. Procede de [Correos de México](https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx); se conserva completo, con su aviso original. La orden de creación ejecuta el SQL y después importa este ZIP en una transacción. No hay que descargarlo, descomprimirlo ni indicar una ruta. El [registro de origen y SHA-256](../datos/README.md) permite verificar la copia entregada.

### Windows — PowerShell 7

Desde la raíz del proyecto, configurar la conexión y crear la base:

```powershell
java -version # Debe mostrar Java 21; configurar PATH o JAVA_HOME si hace falta.
$env:PGHOST = 'localhost'
$env:PGPORT = '5432'
$env:PGUSER = 'postgres'
$claveRevision = Read-Host 'Contraseña de PostgreSQL' -AsSecureString
$env:PGPASSWORD = [System.Net.NetworkCredential]::new('', $claveRevision).Password

.\scripts\crear-base-datos.ps1 -Base revision_hasani
```

Resultado esperado: `15` tablas de aplicación, `8` versiones de Flyway y el mensaje **Catálogo postal listo: 159340 asentamientos, 32 estados, 2478 municipios**. El catálogo queda cargado antes de iniciar la API:

```powershell
.\scripts\iniciar-api.ps1 -Base revision_hasani
```

El iniciador genera la clave JWT en memoria si no se configuró y compila el JAR si hace falta. Utiliza la contraseña de PostgreSQL del entorno; no la guarda en archivos. Esperar el mensaje `Started App` y abrir **http://localhost:8081/swagger-ui.html**. La API permanece en esa terminal; `Ctrl+C` la detiene.

### Linux — terminal

Con Java 21 y PostgreSQL 17 disponibles, desde la raíz del proyecto:

```bash
java -version
export PGHOST=localhost PGPORT=5432 PGUSER=postgres
read -rsp 'Contraseña de PostgreSQL: ' PGPASSWORD; printf '\n'
export PGPASSWORD

sh scripts/crear-base-datos.sh revision_hasani
```

La misma orden carga el ZIP incluido. Cuando termine, iniciar la API:

```bash
sh scripts/iniciar-api.sh revision_hasani
```

El iniciador necesita `java` en PATH. Utiliza `openssl` para generar la clave JWT si no existe `JWT_SECRET`. El JAR y el esquema son los mismos que en Windows. Si no hay JAR, compila mediante `sh gradlew bootJar`. Esperar `Started App`, abrir **http://localhost:8081/swagger-ui.html** y detener con `Ctrl+C`.

La contraseña se solicita sin mostrarla ni escribirla literalmente en el historial de comandos. Las credenciales pertenecen al entorno del revisor.

### Alternativa: PostgreSQL en Docker

Si se utiliza un contenedor existente, sustituir únicamente la orden de creación:

```powershell
# Windows; nombre del contenedor y puerto según su instalación.
.\scripts\crear-base-datos.ps1 -Base revision_hasani -Contenedor postgres-dev
```

```bash
# Linux.
export DOCKER_CONTAINER=postgres-dev
sh scripts/crear-base-datos.sh revision_hasani
```

La API se ejecuta en el equipo y se conecta al puerto publicado en `PGPORT`. Para un contenedor nuevo, después de configurar `PGPASSWORD`, puede usarse:

```powershell
docker run -d --name postgres-revision -e "POSTGRES_PASSWORD=$env:PGPASSWORD" -p 5433:5432 postgres:17-alpine
$env:PGPORT = '5433'
docker exec postgres-revision pg_isready -U postgres
# Cuando informe accepting connections:
.\scripts\crear-base-datos.ps1 -Base revision_hasani -Contenedor postgres-revision
```

```bash
docker run -d --name postgres-revision -e "POSTGRES_PASSWORD=$PGPASSWORD" -p 5433:5432 postgres:17-alpine
export PGPORT=5433 DOCKER_CONTAINER=postgres-revision
docker exec postgres-revision pg_isready -U postgres
# Cuando informe accepting connections:
sh scripts/crear-base-datos.sh revision_hasani
```

En Docker, la importación Java se conecta desde el equipo al puerto publicado: `PGHOST`, `PGPORT`, `PGUSER` y `PGPASSWORD` deben corresponder a ese contenedor. Si falla sólo la importación, el esquema creado se conserva; corregir la conexión y ejecutar `scripts/importar-catalogo-postal.ps1 -Base revision_hasani` o `sh scripts/importar-catalogo-postal.sh revision_hasani`.

Estas alternativas ejecutan **el mismo SQL**; no son ocho pasos de migración. No ejecutar la creación dos veces sobre el mismo nombre: el script detecta una base existente y se detiene sin modificarla. Para volver a abrir una base creada, ejecutar sólo `iniciar-api`. Para otra instalación desde cero, elegir otro nombre de base.

### Qué comprobar al iniciar

1. Swagger abre en el puerto 8081.
2. `GET /catalogos/codigos-postales/37907` devuelve asentamientos con el CP solicitado.
3. `POST /clientes` crea cliente, domicilio, cuenta y usuario; devuelve 201.
4. `POST /auth/login` entrega un JWT. Usarlo en **Authorize** para consultar `/auth/me` y `/clientes/me`.
5. Consultar la cuenta y el saldo del mismo cliente; no se exponen contraseñas ni hashes.

El perfil de revisión `qa` usa PostgreSQL y las reglas reales de clientes. Desactiva la caché y dirige GestoPago a una dirección local para que la revisión no consuma el proveedor. No acredita la integración externa. Para esta última se necesitan Redis, credenciales GestoPago y la configuración normal del proyecto.

### Repetir JMeter en ambos sistemas

Preparar perfiles sintéticos mientras la API de revisión está iniciada:

```powershell
python scripts/preparar-datos-jmeter.py
.\scripts\ejecutar-jmeter.ps1 -JMeterHome (Join-Path $HOME 'Downloads/apache-jmeter-5.6.3')
```

```bash
python3 scripts/preparar-datos-jmeter.py
export JMETER_HOME="$HOME/Downloads/apache-jmeter-5.6.3" # Ajustar a su ruta.
sh scripts/ejecutar-jmeter.sh
```

Se ejecutan 1, 10 y 25 usuarios durante 60 segundos por nivel. Los JTL y dashboards se guardan en una carpeta nueva de `tests/jmeter/resultados/`. Para revisar el `.jmx` en la interfaz: `bin/jmeter.bat` en Windows o `sh "$JMETER_HOME/bin/jmeter"` en Linux, y **Archivo → Abrir → `tests/jmeter/clientes-local.jmx`**.

La preparación Python admite repetir la recuperación de sus propios perfiles QA sin borrar registros. No utiliza los datos personales del proyecto. El CSV queda en `.local-data`, fuera de Git y del paquete. Los [reportes ya entregados](../tests/jmeter/resultados/20261009-120812/) se pueden abrir sin repetir la carga.

## Comprobaciones conservadas

### Suite Java en Windows y Linux

Con la conexión a la base de revisión y el archivo postal ya configurados, ejecutar la suite fuera de la medición JMeter. Las pruebas PostgreSQL crean y eliminan sus propios esquemas aleatorios.

```powershell
$env:POSTGRES_TEST_URL = "jdbc:postgresql://$($env:PGHOST):$($env:PGPORT)/revision_hasani"
$env:POSTGRES_TEST_USER = $env:PGUSER
$env:POSTGRES_TEST_PASSWORD = $env:PGPASSWORD
$env:POSTAL_TEST_ARCHIVO = (Resolve-Path 'datos/CPdescargatxt.zip').Path
.\gradlew.bat test --rerun-tasks
```

```bash
export POSTGRES_TEST_URL="jdbc:postgresql://$PGHOST:$PGPORT/revision_hasani"
export POSTGRES_TEST_USER="$PGUSER" POSTGRES_TEST_PASSWORD="$PGPASSWORD"
export POSTAL_TEST_ARCHIVO="$PWD/datos/CPdescargatxt.zip"
sh gradlew test --rerun-tasks
```

Sustituir el nombre si se creó otra base. Sin las variables PostgreSQL/postal se omiten las pruebas que dependen de esos recursos.

- [Base vacía preparada mediante V1–V8](evidencias/instalacion-inicial.json).
- [Reinicio sin duplicados](evidencias/instalacion-reinicio.json).
- [Instalación con SQL único](evidencias/instalacion-sql-unico.json).
- [Creación y carga automática del ZIP incluido](evidencias/instalacion-catalogo-incluido.json).
- [67 pruebas Java aprobadas](evidencias/suite-java.json).

La preparación anterior mediante `preparar-entorno-qa.ps1` se conserva para comprobar el proceso incremental de migraciones. Para la revisión del profesor, la entrada recomendada es `crear-base-datos.sql`, que instala el esquema completo en una ejecución. No se utiliza baseline/repair ni se modifican los checksums originales.

El SQL de entrega representa una instantánea verificada del esquema V1–V8. Incluye el historial original para permitir su validación posterior por Flyway; las fechas del historial corresponden a la instalación de referencia, no al momento en que otro revisor crea su copia. Ese historial no sustituye la prueba de arranque de la copia, que se verificó por separado.

Referencia de [psql](https://www.postgresql.org/docs/17/app-psql.html) y [exportación de estructuras PostgreSQL](https://www.postgresql.org/docs/17/app-pgdump.html).
