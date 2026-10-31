# Ambientar-Proyecto

> El contrato por body, los estados HTTP y V9 se describen en [Entrega de validaciones](docs/validaciones-entrega.md). Swagger corresponde a la versión que esté desplegada en el servidor seleccionado.

API REST para registrar y administrar clientes personas físicas, sus usuarios y cuentas. Incluye catálogos mexicanos, autenticación, permisos por rol e integración con el catálogo de productos GestoPago.

**API desplegada:** https://ambientar-proyecto.onrender.com

**Swagger:** [Abrir documentación interactiva y ejecutar peticiones](https://ambientar-proyecto.onrender.com/swagger-ui/index.html).

Este README reúne el documento técnico de la solución. La revisión de los endpoints se realiza desde Swagger, siguiendo el orden de la sección API REST y consultas.

**Accesos directos:**

- [Diagrama entidad–relación](#diagrama-entidadrelación), [PDF](docs/diagramas/V3%20DIAGRAMA%20E%20R%20Integracion%20de%20clientes.pdf) y [archivo editable](docs/diagramas/modelo-er-v8.drawio.xml).
- [Script único de creación de base de datos](scripts/crear-base-datos.sql).
- [Código fuente Java](src/main/java/) y [recursos y migraciones](src/main/resources/).
- [Respaldo, auditoría y reversión de V9](docs/operacion-validaciones-v9.md).
- [Reglas de negocio](#solución-java-y-reglas-de-negocio) y [secuencia de endpoints](#api-rest-y-consultas).
- [Matriz de pruebas funcionales](https://docs.google.com/spreadsheets/d/1wuJ65P2yGV9EE4FCUEvcD3ppr8lwz2MxvaRizK-WxH0/edit).
- [Resultados de la suite Java](docs/evidencias/suite-java.json) y [plan y resultados de JMeter](docs/jmeter-plan-resultados.md).

## Pagos ficticios desde Swagger

Activa `SPRING_PROFILES_ACTIVE=simulacion` y configura `SIMULACION_STORAGE_KEY_BASE64` con una clave privada de 32 bytes en Base64. Conserva la misma clave al reiniciar o usar varias instancias. Conserva las variables de conexión, JWT y catálogo postal de tu ejecución local. En IntelliJ puedes seleccionar **App (simulación)** si ya preparaste esa configuración local.

Este modo usa tus usuarios y cuentas para comprobar permisos, pero lleva un **saldo ficticio separado**. No cambia `cuentas.saldo`, no necesita credenciales de GestoPago y bloquea sus conexiones externas. El par AES público de ejemplo se usa exclusivamente dentro del simulador; los datos guardados usan otra clave privada. Para detener este modo, quita el perfil y reinicia. Las rutas `/api/simulacion` desaparecen.

En [Swagger local](http://localhost:8080/swagger-ui/index.html), inicia sesión y pulsa **Authorize**. Busca **Pagos ficticios**:

1. Consulta `/api/simulacion/productos` y usa el ID de una cuenta tuya.
2. Agrega fondos con `/cuentas/{cuentaId}/fondear`, por ejemplo `{"monto":200.00}`.
3. Crea `/pagos` con el ejemplo **Con referencia**, tu `cuentaId` e `Idempotency-Key: prueba-0001`. Devuelve `RESERVED` y un ID de pago.
4. Ejecuta `/pagos/{id}/procesar` y consulta el saldo ficticio. `EXITO` captura el importe; `RECHAZO` libera la reserva.
5. Para otro pago cambia la clave y usa `TIMEOUT_APLICADO` o `TIMEOUT_NO_APLICADO`. Queda `UNKNOWN` con reserva. `/pagos/{id}/conciliar` lo resuelve sin reenviar.

El servicio `9900`, producto `990001`, es inventado para probar referencia y `tipoFront=4`: `SIM0000001` es válida, `SIM0000000` inválida y `SIM0000002` simula una verificación sin respuesta. El producto `219` del servicio `78` prueba la recarga ficticia de 40 MXN sin referencia. Los detalles de los cuerpos están en Swagger.

La simulación aprobada no certifica interoperabilidad con GestoPago. El flujo financiero separado se describe abajo; su comprobación con credenciales y un escenario asignados por el proveedor debe acreditarse por separado.

### Integración financiera: perfil `live` o `prod`

Este perfil utiliza el saldo de `cuentas` y puede enviar una operación a GestoPago. **No se activa en el despliegue ni con las claves públicas de ejemplo.** La simulación conserva sus rutas, tablas y dinero separados. Combinar `simulacion` con `live` o `prod` hace fallar el arranque.

El recorrido es: sesión local → producto y cuenta autorizados → referencia externa cuando aplica → reserva y operación guardadas juntas → worker → `sendTx` una sola vez → captura o `UNKNOWN` → `confirmTx`. El envío y la consulta ocurren fuera de la transacción de base de datos. No se agrega un broker porque la tarea pendiente ya queda guardada en PostgreSQL junto con la reserva; varias instancias la reclaman con un bloqueo y una vigencia de 180 segundos.

| Endpoint del perfil financiero | Uso |
|---|---|
| `POST /api/gestopago/pagos` | Recibe `Idempotency-Key` y `{ "cuentaId": 12, "pago": { "idServicio": 78, "idProducto": 219, "montoPago": 40.00, "telefono": "5512345678" } }`. Devuelve 202 y el ID; todavía no acredita un pago aplicado. |
| `GET /api/gestopago/pagos/{id}` | Consulta el estado de una operación propia. |
| `GET /api/gestopago/cuentas/{cuentaId}/saldo` | Devuelve contabilizado, reservado y disponible. |
| `POST /api/gestopago/pagos/{id}/conciliar` | Consulta una operación incierta; nunca vuelve a enviar el pago. Respeta también la programación y la vigencia de la consulta. |

El worker envía automáticamente las operaciones aceptadas cuando está habilitado. El cliente no elige escenarios de éxito o rechazo en este perfil. No existe un endpoint público de fondeo real. Los productos 9900/990001 y las referencias SIM pertenecen exclusivamente a las pruebas ficticias.

`PagoService` aplica las reglas; `PagoRepository` reserva, captura y libera con bloqueos de cuenta; `PagoWorker` procesa los envíos y `PagoReconciliator` selecciona las consultas. La migración V13 agrega reservas, operaciones, movimientos, eventos y outbox. El disponible es `saldo - saldo_reservado`; el importe se descuenta del saldo contabilizado al confirmar la captura. Las restricciones de PostgreSQL impiden reservas negativas o superiores al saldo. Las actualizaciones JPA de estatus no pueden sobrescribir el saldo financiero con un valor antiguo.

La misma clave y los mismos datos recuperan el pago original; cambiar los datos con esa clave devuelve 409. El UPC se genera en el servidor y tiene una restricción única. Para pagos de servicios se puede exigir además el bloqueo documentado de servicio+monto durante 24 horas, compartido entre cuentas e instancias. No se da por hecho que el proveedor proporcione una idempotencia universal.

**Resultados y conciliación.** `sendTx` acredita éxito con `01`, ID, autorización y monto consistentes. Otros códigos, errores HTTP, cortes o respuestas incompletas quedan en `UNKNOWN` con reserva. `confirmTx` usa los mismos datos originales —incluidos UPC y horaLocal— desde los 62 segundos y antes de 24 horas. Su contrato es diferente: `06` con ID y autorización positivos acredita aplicada; `70`, ID_TX=-1 y autorización=0 acredita no aplicada. Una respuesta inconclusa conserva la reserva. Un token expirado se invalida; el pago no se reenvía y la siguiente consulta obtiene una sesión nueva.

Al vencer las 24 horas se marca revisión manual y se conserva la reserva. Si un emisor perdió su vigencia tras iniciar el intento, un `70` requiere revisión manual: no hay un mecanismo externo documentado que impida que un proceso pausado se reanude tarde. Un `06` aún permite capturar. Las evidencias contradictorias se registran para revisión sin repetir movimientos. No se implementa una devolución automática sin un contrato documentado de reverso.

**Configuración necesaria.** Mantén los valores en variables de entorno o en secretos inyectados por la plataforma:

- `SPRING_PROFILES_ACTIVE=live` (o `render,live` si corresponde a ese entorno).
- `GESTOPAGO_PAYMENT_ENABLED=true` y `GESTOPAGO_REFERENCE_ENABLED=true`.
- `GESTOPAGO_PASSWORD`, `GESTOPAGO_TOKEN_CACHE_KEY_BASE64`, `GESTOPAGO_CRYPTO_SECRET_BASE64`, `GESTOPAGO_CRYPTO_IV_BASE64` y `GESTOPAGO_UNIDAD` asignados/configurados correctamente. Basic y API key solo si el proveedor los exige para tu acceso.
- `GESTOPAGO_PAYMENT_STORAGE_KEY_BASE64`: 32 bytes aleatorios en Base64, distinta de la clave del proveedor y de la caché del token. Protege el contrato original y las respuestas en PostgreSQL mediante AES-GCM. Conserva esta clave mientras existan operaciones pendientes; su rotación requiere migrar los datos cifrados.
- Para **cada producto autorizado**, configurar `gestopago.contrato.productos[ID].integracion-aprobada=true` y `servicio-activo=true`, junto con su servicio, tipoFront, categoría, destino, importes y reglas de referencia. No hay productos aprobados para envío por defecto. El producto 219 solo está habilitado para validación local hasta aprobar su integración.
- El contrato general de referencias exige tipoFront=4 y una política con `requiere-referencia=true`, `verificacion-referencia-aprobada=true`, longitudes y servicio/categoría coincidentes con el catálogo. Para pagos de servicios se exige además `bloqueo-servicio-monto24h=true`. El rechazo de un ejemplo negativo de tipoFront=2 no autoriza ese tipo en el flujo financiero. El catálogo local debe estar vigente (24 horas por defecto, configurable entre 1 y 168); la sincronización conserva su cuota compartida de tres intentos externos por ventana de 24 horas.

La aplicación obtiene el Bearer mediante la autenticación del proveedor y lo reutiliza en una caché cifrada con vencimiento. No se pega un Bearer en el código ni se devuelve al usuario. Las evidencias conservan solicitudes/respuestas cifradas, nunca Authorization o contraseñas. Swagger y las respuestas públicas solo muestran datos seguros y el estado de la operación.

| Evidencia | Qué acredita |
|---|---|
| Pruebas manuales `/api/simulacion` | Flujo ficticio: éxito, rechazo, ambos timeouts, reservas y repetición sin cambios de saldo. |
| Pruebas automáticas de `PagoRealAplicacionTest` | Aplicación completa, autenticación local, PostgreSQL, AES, Bearer, HTTP local controlado, idempotencia, concurrencia, rollback y conciliación `06`/`70`. No llaman al proveedor externo. |
| Autenticación y catálogo externos registrados previamente | Acceso a esos servicios; no acredita una venta en `sendTx`. |
| `verifyReference` con GestoPago | Los dos ejemplos públicos de 272/108 respondieron HTTP 200: referencia válida CODIGO 01 y longitud inválida CODIGO 02, con `random` + `signed`. Son diagnósticos técnicos sin pagos. El catálogo local indica tipoFront=2, pero el contrato general exige 4: el producto sigue bloqueado para el flujo financiero hasta aclararlo. El ejemplo negativo especial 271/107 aceptó `body` + `signed`; no cambia el contrato general. |
| `sendTx` / `confirmTx` con GestoPago | Requieren evidencia externa con entorno y caso autorizados; no se presentan como comprobados por la prueba de referencia. |

La comprobación externa sin pagos es opt-in: `GestoPagoExternalReferenceProbeTest`. Solo permite HTTPS a authenticate y verifyReference, reutiliza la caché cifrada y consulta los ejemplos públicos válido e inválido de 272/108 mediante `random` + `signed`. Es un diagnóstico técnico, separado de la autorización de productos: no falsea tipoFront para superar la política del pago. No arranca App ni modifica el esquema, catálogo o saldos. Se ejecuta manualmente con las credenciales locales y las variables `GESTOPAGO_EXTERNAL_REFERENCE_PROBE`, `GESTOPAGO_PROBE_DB_URL`, `GESTOPAGO_PROBE_DB_USER`, `GESTOPAGO_PROBE_TEST_SECRET_BASE64` y `GESTOPAGO_PROBE_TEST_IV_BASE64`. No forma parte de CI ni se habilita por defecto.

**Si aparece «carrier no responde».** Un HTTP 200 con `AUTORIZACION/CODIGO_MENSAJE=03` no acredita un pago ni permite liberar automáticamente el dinero. Ese formato se reprodujo con un proveedor HTTP local a partir de un [reporte de clase](https://github.com/elmanee/prueba/blob/481ddea45b54dc168b3af706ccf12de6865c1a74/README.md); no es una respuesta externa obtenida por este proyecto ni prueba una caída general de GestoPago. Se conserva `UNKNOWN` con reserva y solo se consulta la confirmación, sin repetir la venta. También se bloquea una supuesta venta exitosa con autorización numérica cero o negativa. La respuesta pública no expone el XML ni el mensaje crudo del proveedor; la evidencia queda cifrada.

## Ejecución local en Linux

Desde la raíz del proyecto, configurar la conexión y ejecutar:

```bash
export PGHOST=localhost PGPORT=5432 PGUSER=postgres
read -rsp 'Contraseña de PostgreSQL: ' PGPASSWORD; printf '\n'
export PGPASSWORD
sh scripts/crear-base-datos.sh revision_hasani
sh scripts/iniciar-api.sh revision_hasani
```

La creación ejecuta el SQL único y carga automáticamente `datos/CPdescargatxt.zip`, incluido en el proyecto. V1–V9 se conservan como historial de migraciones; no se ejecutan manualmente por separado. El catálogo contiene 159,340 asentamientos, 2,478 municipios y 32 estados, con [origen y huella SHA-256](datos/README.md) documentados. Para reiniciar una base ya creada, ejecutar únicamente el iniciador de la API.

Swagger local: `http://localhost:8081/swagger-ui/index.html`. El perfil local de revisión prueba clientes y cuentas; la integración externa se revisa en el entorno desplegado. Las mediciones JMeter entregadas corresponden al entorno local.

## Diagrama entidad–relación

![Modelo entidad–relación: clientes, cuentas, usuarios y catálogos](docs/diagramas/modelo-er-v8.png)

<details>
<summary>Ver el diccionario completo de las 15 tablas</summary>

![Diccionario físico PostgreSQL y decisiones del modelo](docs/diagramas/modelo-er-v8-2.png)

</details>

El modelo contiene **15 tablas de aplicación**; `flyway_schema_history` es una tabla adicional de infraestructura. Clientes se relaciona con domicilio y usuario mediante FK `NOT NULL` y `UNIQUE`, y con cuentas mediante una FK sin `UNIQUE`. El esquema permite 0..1 domicilio/usuario y 0..N cuentas por cliente; el servicio de registro garantiza la creación conjunta de las filas del alta nueva.

Estados → municipios → asentamientos forman la jerarquía postal. Un CP puede tener varias colonias. El domicilio selecciona un asentamiento compatible con su CP. La FK postal conserva nulabilidad para domicilios históricos sin correspondencia única; los registros nuevos requieren el asentamiento.

`usuario_cambios_rol` registra las asignaciones de rol. `gestopago_tokens`, `gestopago_productos` y `cat_postal_version` conservan su independencia física; no se dibujan FKs que no existen en SQL. La información laboral está integrada en la tabla `clientes`.

### Tipos de datos, almacenamiento e integridad

| Dato | Tipo implementado | Motivo y límite |
|---|---|---|
| IDs personales y claves de referencia | `BIGSERIAL` / `BIGINT` | Identidad generada y referencias del mismo tipo; enteros de 8 bytes |
| Catálogos personales y estados | `SMALLINT` | Claves de rango pequeño; enteros de 2 bytes |
| Municipios y asentamientos | `INTEGER` | Claves compuestas del catálogo dentro de su rango; enteros de 4 bytes |
| Nacimiento | `DATE` | Fecha sin hora |
| Fechas de registro y auditoría | `TIMESTAMP` | Fecha y hora; históricos desconocidos pueden conservar NULL |
| CURP, RFC y correo | `VARCHAR(18)`, `VARCHAR(13)`, `VARCHAR(100)` | Límites según contrato; restricciones de unicidad |
| Teléfono y cuenta | `VARCHAR(10)` y `VARCHAR(20)` | Identificadores textuales que conservan ceros iniciales |
| CP | `CHAR(5)` en catálogo; `VARCHAR(10)` heredado en domicilio | API de cinco dígitos; no se trata como cantidad numérica |
| Saldo e ingreso | `DECIMAL(15,2)` y `BigDecimal` en Java | Importes exactos con dos decimales |
| Estado habilitado | `BOOLEAN` | Valor lógico de catálogo/usuario/producto |
| Contraseña | `VARCHAR(60)` | Hash BCrypt; no se almacena la contraseña en texto |

Los tamaños de enteros corresponden al valor, sin incluir cabecera de fila ni índices. `VARCHAR(n)` establece un máximo de caracteres, no reserva siempre `n` bytes. `CHAR(n)` utiliza relleno y no aporta una ventaja general de rendimiento. La precisión de `NUMERIC/DECIMAL` tampoco implica almacenamiento fijo de todos sus dígitos. Fuentes: [tipos numéricos PostgreSQL 17](https://www.postgresql.org/docs/17/datatype-numeric.html) y [tipos de caracteres](https://www.postgresql.org/docs/17/datatype-character.html).

Las FKs, `NOT NULL`, `UNIQUE` y los CHECK de rol/intentos preservan las restricciones físicas correspondientes. Las reglas de edad, contraseña, ingreso positivo y opciones habilitadas se implementan en Java; no se atribuyen a un CHECK inexistente. El correo se normaliza y su unicidad se comprueba sin distinguir mayúsculas.

Se usan índices para relaciones, identificadores, CP, estatus y fecha. V3 también conserva índices explícitos sobre algunas columnas con `UNIQUE`; esos índices pueden resultar redundantes y deben revisarse mediante una migración posterior, sin modificar el SQL ya aplicado. Las consultas generales se paginan para limitar filas transferidas; no se afirma una medición de memoria total de la aplicación.

## Solución Java y reglas de negocio

La aplicación administra el registro de una persona y mantiene relacionados sus datos personales, domicilio, cuenta y usuario de acceso. Java comprueba la información recibida antes de guardarla en PostgreSQL. Los controladores reciben las solicitudes, los servicios aplican las reglas y los repositorios consultan o guardan los registros.

### Registro del cliente y creación de cuenta

El registro sólo se acepta si la persona es mayor de edad y cumple las validaciones de identidad, contacto, domicilio, ingreso y contraseña. El nombre y los apellidos admiten letras y espacios. La fecha de nacimiento debe ser válida y anterior al día actual. CURP, RFC y correo no pueden estar registrados previamente; el correo se compara sin distinguir mayúsculas y minúsculas.

El domicilio debe seleccionar un asentamiento existente que pertenezca al código postal indicado. Las opciones personales y el país deben corresponder a valores habilitados del catálogo. El ingreso mensual debe ser mayor que cero y admitir como máximo dos decimales. Estas comprobaciones se realizan en el servidor, independientemente de la herramienta utilizada para enviar la petición.

Cuando el alta es válida, se crean juntos el cliente, su domicilio, una cuenta activa con saldo inicial de cero y un usuario con rol CLIENTE. La aplicación genera el número de cuenta de veinte dígitos. Si alguno de estos registros no puede guardarse, se cancela toda el alta para evitar información incompleta. El número generado es un identificador interno de cuenta y no una CLABE.

### Acceso y protección de la información

La contraseña se guarda mediante BCrypt; las consultas no devuelven la contraseña ni su hash. Para iniciar sesión, el usuario debe estar activo y proporcionar sus credenciales correctas. Tres intentos incorrectos consecutivos bloquean su acceso. Una sesión correcta entrega un JWT con vigencia limitada, que se utiliza para autorizar las peticiones protegidas.

El rol CLIENTE permite consultar y administrar exclusivamente los datos propios. El rol EJECUTIVO permite las consultas generales y la administración de clientes contemplada por la API. El registro público siempre crea un CLIENTE; la asignación de EJECUTIVO se realiza mediante el [procedimiento administrativo auditado](scripts/asignar-ejecutivo.sql).

En las consultas por ID, la aplicación verifica quién inició sesión y si tiene permiso sobre el registro solicitado. Cambiar el identificador en una URL no concede acceso a información ajena. Para un CLIENTE, un registro ajeno responde igual que uno inexistente, sin revelar su existencia. El cambio de contraseña sólo lo puede realizar el propietario, incluso si otro usuario tiene rol EJECUTIVO.

### Actualización, contraseña y baja

La actualización conserva la CURP, el RFC, la cuenta y la fecha original de registro. Si cambia el correo, se mantiene la correspondencia entre el cliente y su usuario de acceso. El cambio de contraseña exige confirmar la contraseña actual y validar la nueva; después invalida los tokens anteriores y obliga a iniciar sesión nuevamente.

La baja es lógica: conserva el historial, marca al cliente y sus cuentas como inactivos y desactiva el usuario. Los tokens anteriores dejan de dar acceso. La operación se deja al final de la revisión porque modifica la disponibilidad del perfil.

### Validación y manejo de errores

Las respuestas de error usan los campos `timestamp`, `status`, `codigo`, `mensaje`, `path` y `errores`. Cuando una entrada no cumple una regla, se indica el campo afectado y un mensaje descriptivo. Los manejadores globales centralizan los errores de validación, negocio e integración; la seguridad devuelve el mismo formato para rechazos de acceso. Los errores inesperados no muestran trazas ni credenciales.

La contraseña nueva requiere al menos ocho caracteres, mayúscula, minúscula, número y símbolo; no admite espacios ni caracteres de control y está limitada a 72 bytes UTF-8. Los IDs deben ser enteros positivos. El servidor rechaza tipos JSON incompatibles con los campos definidos. Las validaciones de CURP y RFC comprueban formato, coherencia y unicidad, sin certificar su autenticidad ante una autoridad.

### Productos GestoPago

La aplicación obtiene el catálogo del proveedor mediante sus credenciales de integración. La sincronización guarda o actualiza los productos por su identificador externo, conserva su identidad local y evita insertar duplicados al repetir la operación. Redis mantiene la caché del catálogo; una sincronización confirmada invalida esa caché. El JWT del usuario y el token del proveedor son independientes.

### API REST y consultas

El contrato de esta rama recibe IDs y filtros por body. Consulta la [tabla de rutas, secuencia y comandos de comprobación](docs/validaciones-entrega.md#contrato-por-body). Inicia sesión con POST /auth/login y autoriza el JWT en Swagger antes de las consultas protegidas. Las rutas antiguas con IDs en la URL ya no están disponibles en esta rama.

## Pruebas y resultados

La ampliación financiera del 30 de octubre de 2026 aprobó **193 pruebas en 27 suites, sin fallos ni omisiones**, usando PostgreSQL 17 temporal. Incluye 25 casos de aplicación financiera con HTTP loopback y pruebas de configuración, confirmación y autorización de referencias por producto. La revisión añadió cuatro regresiones para el reporte de carrier no disponible y las autorizaciones numéricas inválidas: no capturan ni liberan la reserva por una respuesta inconclusa. [Resumen de la ejecución](tests/evidencias/gestopago/financiero-controlado-2026-10-30.json). Por separado, se aprobaron [dos comprobaciones externas de referencia](tests/evidencias/gestopago/verificacion-externa-referencia-2026-10-30.json), válida e inválida, sin ejecutar pagos. CI exige las suites locales y conserva los reportes completos; no ejecuta las pruebas externas opt-in.

La API en Render arrancó desde una base vacía, aplicó las ocho migraciones y cargó 159,340 asentamientos, 2,478 municipios y 32 estados. Se comprobaron respuestas **200** en sexos y código postal `37907`, además del alta **201** y la denegación **403** del listado general a un CLIENTE.

- Suite Java histórica: **67 pruebas aprobadas**, cero fallos y cero omisiones. [Resultado](docs/evidencias/suite-java.json).
- Revisión de usuarios del 10 de octubre de 2026: **42 pruebas de integración aprobadas**, incluyendo acceso propio, permisos por ID, contraseña y revocación de sesión. Esta ejecución verifica las correcciones localmente; no sustituye una comprobación posterior del despliegue.
- [Matriz funcional](https://docs.google.com/spreadsheets/d/1wuJ65P2yGV9EE4FCUEvcD3ppr8lwz2MxvaRizK-WxH0/edit): la ejecución histórica del 9 de octubre registró **96 casos aprobados, 7 fallidos y 2 bloqueados**.
- [Plan y resultados JMeter](docs/jmeter-plan-resultados.md), [muestras y reportes](tests/jmeter/resultados/20261009-120812/): login y consultas QA locales durante 60 segundos por nivel.

| Usuarios JMeter | Muestras | Errores | Media ms | P95 ms | Máximo ms |
|---:|---:|---:|---:|---:|---:|
| 1 | 90 | 0 | 100.41 | 154 | 2606 |
| 10 | 924 | 0 | 73.86 | 170 | 1638 |
| 25 | 2029 | 0 | 140.24 | 417 | 2845 |

Las **3,043 muestras** comprobaron respuesta HTTP y contenido, con pausas entre consultas. Estas cifras corresponden al equipo local documentado; no acreditan la misma capacidad en Render ni una carga máxima de producción.

Las incidencias históricas corresponden a altas repetidas, preparación de datos para duplicados y consultas del catálogo externo. Se mantienen identificadas en la matriz. No se presentan como pruebas aprobadas sin una nueva ejecución.

## Archivos de entrega

El repositorio reúne el código fuente, las migraciones V1–V13, el script SQL de la entrega inicial, el ZIP postal, los diagramas y las evidencias. Para el esquema actualizado de pagos se aplican las migraciones de Flyway. Este README es el documento técnico principal. El paquete opcional se genera en Linux con:

```bash
python3 scripts/empaquetar-entrega.py --incluir-jar
```

El paquete conserva las rutas del proyecto y un manifiesto SHA-256. Excluye configuraciones privadas, cachés y archivos del IDE.
