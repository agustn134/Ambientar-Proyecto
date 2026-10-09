# Índice de la entrega

La entrega reúne el proyecto reproducible y los resultados reales obtenidos el 9 de octubre de 2026. Los errores de pruebas quedan documentados; este cierre documental no los convierte en aprobados.

| Entregable | Ruta |
|---|---|
| Código Java, configuración y pruebas JUnit | `src/` |
| Migraciones aplicadas desde una base vacía | `src/main/resources/db/migration/V1__…` a `V8__…` |
| Esquema y carga postal con una orden | `scripts/crear-base-datos.ps1/.sh`, DDL completo en `scripts/crear-base-datos.sql` |
| Catálogo postal nacional incluido y origen | `datos/CPdescargatxt.zip`, `datos/README.md` |
| Iniciadores Windows y Linux para la revisión | `scripts/crear-base-datos.ps1/.sh`, `scripts/iniciar-api.ps1/.sh` |
| Instalación y ejecución | `docs/instalacion-ejecucion.md` |
| Comprobación del primer arranque y reinicio | `docs/evidencias/instalacion-*.json` |
| Colección Bruno de 105 casos | `tests/bruno/` |
| Respuestas y Tests de ejecución funcional | `docs/evidencias/bruno-resultados-2026-10-09.json` |
| Matriz actual | [Google Sheets](https://docs.google.com/spreadsheets/d/1wuJ65P2yGV9EE4FCUEvcD3ppr8lwz2MxvaRizK-WxH0/edit) |
| Libro Excel aportado por el usuario | `docs/diagramas/Pruebas Clientes.xlsx` |
| Diagrama editable | `docs/diagramas/modelo-er-v8.drawio.xml` |
| Diagrama PDF y vistas PNG | `docs/diagramas/` |
| Diccionario y relaciones | `docs/modelo-datos.md` |
| Plan JMeter | `tests/jmeter/clientes-local.jmx` |
| Plan, resultados y límites de carga | `docs/jmeter-plan-resultados.md` |
| JTL, registros y dashboards HTML | `tests/jmeter/resultados/` |
| Scripts de instalación, carga, roles y paquete | `scripts/` |
| Configuración de compilación y Wrapper | `build.gradle`, `settings.gradle`, `gradle/`, `gradlew*` |

El libro Excel se incluye tal como fue aportado; la hoja enlazada es la referencia actual de los 105 resultados. No se reemplazan sus fallos con respuestas simuladas.

## Comprobaciones funcionales y límites

- Suite Java: 67 pruebas aprobadas, cero fallos y cero omisiones, incluidas las pruebas PostgreSQL y el archivo postal nacional.
- Bruno automatizado: 96 casos aprobados, 7 fallidos y 2 bloqueados. Las aserciones se tomaron de los `.bru` originales y se evaluaron con Chai; fue una ejecución HTTP por script, no una ejecución mediante la interfaz de Bruno.
- Los fallos funcionales corresponden a dos altas ya existentes (409 en lugar de 201), dos fixtures de duplicados que rechazan CURP antes del campo esperado y tres respuestas 500 en los endpoints locales GestoPago.
- Dos casos GestoPago no se enviaron porque fallaron las precondiciones del catálogo. No se atribuye el 500 al proveedor sin revisar los registros del backend.
- Sofía y Nancy quedaron registradas en la base habitual durante la ejecución funcional. Mantenimiento utilizó un perfil sintético nuevo, posteriormente dado de baja. La carga utiliza otra base QA y otros 25 perfiles.
- Los resultados de JMeter se limitan a login, consultas propias y catálogo postal. No prueban integración externa, bajas ni escrituras concurrentes.

## Crear el paquete

```powershell
.\scripts\empaquetar-entrega.ps1
# Opcional: añadir el JAR ya compilado.
.\scripts\empaquetar-entrega.ps1 -IncluirJar
```

El ZIP se genera en `outputs/entrega-<fecha>/`. Contiene un manifiesto SHA-256 por archivo. Se excluyen `.local-data`, `.git`, configuraciones IDE, cachés, dumps y credenciales privadas. Se incluye el ZIP nacional SEPOMEX en `datos/`; el receptor sólo configura su conexión PostgreSQL. No se incluyen instaladores de JDK, PostgreSQL o JMeter.

En Linux: `python3 scripts/empaquetar-entrega.py --incluir-jar`. El paquete de revisión con JAR permite arrancar la API con JDK 21 sin una compilación previa; las fuentes y el Wrapper se incluyen para reproducirla. El SQL único contiene la estructura y las referencias básicas de una instantánea verificada V1–V8, incluido su historial original de Flyway; no contiene personas, cuentas ni credenciales.

## Lectura recomendada

1. README y guía de instalación.
2. Diagrama y modelo de datos.
3. Contratos de registro, login, consultas y mantenimiento.
4. Matriz y resultados funcionales.
5. Plan JMeter, resultados resumidos y dashboards HTML.

El paquete conserva el código y los artefactos en su estructura de proyecto. No se ha creado un commit, publicado una entrega ni modificado el estado de una PR.
