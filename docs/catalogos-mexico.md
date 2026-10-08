# Catálogos de registro y domicilio en México

V7 reemplaza textos libres por IDs `SMALLINT` con llaves foráneas. El registro comprueba que las opciones existan y estén habilitadas antes de guardar.

| Campo del request | Opciones iniciales |
| --- | --- |
| `sexoId` | 1 Masculino, 2 Femenino; códigos H/M sólo en el catálogo |
| `nacionalidadId` | 1 Mexicana |
| `estadoCivilId` | 1 Soltero/a, 2 Casado/a, 3 Divorciado/a, 4 Viudo/a, 5 Unión libre, 6 Separado/a, 7 No especificado |
| `domicilio.paisId` | 1 México |
| `domicilio.asentamientoId` | ID obtenido al consultar el código postal |

Los IDs son enteros JSON: se rechazan cadenas, decimales, booleanos, números fuera del tipo, negativos y ceros. Los campos anteriores `sexo`, `nacionalidad`, `estadoCivil` y `domicilio.pais` dejan de formar parte del contrato. No sustituyen los nuevos IDs obligatorios.

El domicilio recibe calle, números exterior/interior, CP, asentamiento y país. El backend obtiene colonia, municipio y estado del catálogo; ya no recibe esas etiquetas como selección libre. El CP permanece como texto de cinco dígitos para conservar ceros iniciales. Puede tener varios asentamientos: se debe seleccionar uno que corresponda.

```json
{
  "calle": "Guerrero",
  "numeroExterior": "843",
  "codigoPostal": "37907",
  "asentamientoId": 110333891,
  "paisId": 1
}
```

Agustín confirmó que “Nueva San Isidro” corresponde a “San Isidro” en el catálogo oficial. Se adaptaron sus fixtures y los de Dulce: sexo 1/2, nacionalidad y país 1, estado civil 7 como dato sintético. No se crean nuevos clientes para actualizar los fixtures.

RFC acepta 12 o 13 caracteres conforme al documento escrito de la actividad, con regex y fecha interna real. No se verifica autenticidad oficial. Nombre conserva 3–38 por la aclaración oral y apellidos 2–50. Los campos JSON desconocidos siguen ignorándose por la configuración existente.

## Consulta de opciones

GET públicos, sin información personal, disponibles antes del registro:

- `/catalogos/sexos`
- `/catalogos/nacionalidades`
- `/catalogos/paises`
- `/catalogos/estados-civiles`
- `/catalogos/codigos-postales/{codigoPostal}`

Las opciones simples devuelven `id` y `descripcion`. La consulta postal devuelve `id`, `codigoPostal`, `colonia`, `tipo`, `municipioId`, `municipio`, `estadoId` y `estado`. CP inexistente: lista vacía. Formato postal inválido: 400. Asentamiento inexistente o incompatible con el CP al registrar: 400 por campo.

## Fuente y preparación

Descarga nacional TXT realizada el 8 de octubre de 2026 desde [SEPOMEX oficial](https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx): **159,340 asentamientos de las 32 entidades**. El importador registra SHA-256 y cantidad en `cat_postal_version`.

El aviso del archivo oficial prohíbe redistribuirlo. El ZIP se mantiene en `.local-data/CPdescargatxt.zip`, ignorado por Git; no se incluye en recursos ni en el PR. Se versionan la migración, el importador, las pruebas y el procedimiento de descarga.

Otra máquina debe descargar su archivo local:

```powershell
.\scripts\descargar-sepomex.ps1
$env:SEPOMEX_ARCHIVO = (Resolve-Path '.local-data/CPdescargatxt.zip').Path
# Configurar también DB_PASSWORD, GESTOPAGO_PASSWORD y JWT_SECRET.
.\gradlew.bat bootRun
```

En esta máquina ya quedó la ruta en App de IntelliJ, ignorada por Git. Detener por completo y volver a ejecutar App para heredar esa variable. Flyway aplica V7; la importación termina antes de crear el EntityManagerFactory. La primera carga exige el archivo; los siguientes arranques pueden utilizar el catálogo ya importado. Si se conserva la ruta, el hash evita repetir la carga del mismo ZIP.

Se valida cabecera, CP, IDs, duplicados, 32 entidades y más de 100,000 filas antes de escribir. Estados, municipios, asentamientos y versión se guardan en una transacción. Los IDs combinan claves oficiales de entidad, municipio y asentamiento. Un ZIP inválido se rechaza sin reemplazar la versión vigente.

## Migración histórica

V7 convierte etiquetas reconocidas. Si encuentra un valor desconocido, NOT NULL provoca rollback de la migración en PostgreSQL. Revisar sexo, nacionalidad, estado civil y país históricos y corregir el dato, sin asignar opciones arbitrarias ni borrar clientes. V1–V6 permanecen intactas.

Tras el primer arranque, un ajuste posterior del archivo V7 produjo un error de validación por checksum diferente. Se restauró exactamente la versión aplicada, con checksum `499011389`, sin ejecutar repair ni modificar el historial de la base. V7 queda congelada; cualquier cambio posterior del esquema debe utilizar otra migración. No editar ni borrar `flyway_schema_history` para evitar la validación.

La importación vincula domicilios históricos sólo con coincidencia exacta y única de CP, colonia, municipio y estado, sin distinguir mayúsculas. Incluye la equivalencia San Isidro confirmada por Agustín. Una colonia antigua “Centro” ambigua se conserva sin FK postal hasta revisarla. Todos los registros nuevos exigen asentamiento válido.

Las columnas históricas de colonia/municipio/estado se conservan como copia del domicilio; en registros nuevos se llenan exclusivamente desde el catálogo. La eliminación completa de esas copias requiere resolver previamente los domicilios históricos pendientes. No se cambia la identidad de cliente, cuenta o usuario.

```sql
SELECT id, cliente_id, codigo_postal, colonia, municipio, estado
FROM domicilios WHERE asentamiento_id IS NULL;
```

Una descarga posterior agrega/actualiza claves, sin borrar asentamientos referenciados ni desactivar ausencias automáticamente. No hay cron postal; descargar la nueva versión y reiniciar con su ruta.

## Pruebas

Validación ejecutada el 8 de octubre de 2026: suite completa con **50 pruebas aprobadas**, sin fallos ni omisiones. Tras los ajustes del handler se repitieron las **31 pruebas** de registro/autenticación y catálogos, todas aprobadas. La prueba PostgreSQL importó el ZIP nacional real y comprobó repetición y rollback. V7 se restauró posteriormente a la versión aplicada, ya utilizada en la suite completa, para resolver el conflicto de checksum. Se validaron además JSON y sintaxis de los scripts de 39 peticiones Bruno y la sintaxis del script PowerShell. Eso no equivale a ejecutar manualmente las peticiones Bruno: sus 11 nuevos casos quedan pendientes en el backend local.

Las pruebas HTTP cubren opciones públicas, IDs inválidos/deshabilitados, tipos estrictos, CP incompatible, domicilio derivado y FKs. H2 usa datos mínimos de prueba, no el catálogo nacional.

`CatalogosPostgresTest` verifica conversión histórica, rollback ante etiqueta desconocida, archivo inválido y, con la variable de archivo, carga nacional y repetición sin duplicados en esquemas aleatorios. No toca `public`.

```powershell
$env:POSTGRES_TEST_URL = 'jdbc:postgresql://localhost:5432/DBGestoPago'
$env:POSTGRES_TEST_USER = 'postgres'
$env:POSTGRES_TEST_PASSWORD = $env:DB_PASSWORD
$env:POSTAL_TEST_ARCHIVO = (Resolve-Path '.local-data/CPdescargatxt.zip').Path
.\gradlew.bat test
```

Sin esas variables, las pruebas PostgreSQL/nacionales se omiten explícitamente. `DB_PASSWORD` debe existir en la terminal: las variables de IntelliJ no se transfieren a PowerShell.

Bruno: abrir `tests/bruno` y ejecutar **Catalogos**, cinco consultas y seis registros inválidos. Los registros existentes y el caso de usuario para bloqueo ya usan IDs. Repetir un registro válido ya existente devuelve 409; no necesita eliminarse ni recrearse para consultar las opciones.

Roles EJECUTIVO/CLIENTE y consultas/paginación siguen pendientes del siguiente cambio. Este cambio no otorga permisos de ejecutivo ni habilita las rutas antiguas de personas.
