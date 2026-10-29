# Entrega de validaciones

Rama de trabajo: `feature/Validaciones`. La adaptación principal se integró en `main` mediante el PR #19. Los ajustes posteriores de mensajes y documentación de estados HTTP deben publicarse para aparecer en Render.

## Mensajes y estados HTTP

Swagger declara las respuestas por operación con su esquema uniforme y ejemplos de `status`, `codigo`, `mensaje`, `path` y `errores`. El alta devuelve 201; las consultas y actualizaciones con resultado devuelven 200; el cambio de contraseña y la baja devuelven 204 sin cuerpo. Un listado sin coincidencias devuelve una lista vacía con 200; consultar un registro concreto inexistente o ajeno devuelve 404 sin revelar información de terceros.

| Estado | Motivo y acción |
|---|---|
| 400 | Campos inválidos, cuerpo ausente, JSON incorrecto o reglas obligatorias incumplidas. Revisar `errores` y los formatos del DTO. |
| 401 | Token ausente, vencido, revocado o inválido; iniciar sesión y enviar Bearer vigente. El login o cambio de contraseña también pueden rechazar credenciales. |
| 403 | El usuario autenticado no tiene permiso para la operación. |
| 404 | Registro inexistente o no disponible para el usuario; también rutas retiradas. |
| 405 | Método HTTP incorrecto para la ruta. La seguridad se evalúa primero. |
| 406 | Formato de respuesta no disponible; utilizar `Accept: application/json`. |
| 409 | Dato duplicado o conflicto con registros relacionados. |
| 415 | Formato de entrada no compatible; utilizar `Content-Type: application/json`. |
| 429 | Límite de peticiones a GestoPago; esperar antes de reintentar. |
| 500 | Error interno controlado; no se muestran SQL, trazas ni datos privados. |
| 502 / 503 / 504 | Respuesta inválida, indisponibilidad o tiempo de espera agotado del proveedor. |

Los errores de GestoPago llevan `codigo: SERVICIO_EXTERNO`, incluso si el proveedor devuelve un 401 o 500; los ejemplos de Swagger distinguen estos casos de los errores de la propia API. Otros estados de error del proveedor mantienen su HTTP y utilizan la respuesta `default` documentada, siempre con mensaje controlado. Las rutas públicas no exigen token, pero un Bearer inválido enviado explícitamente puede producir 401. Los errores que impiden identificar un campo concreto devuelven `errores: []`.

Este ajuste se verificó con **79 pruebas aprobadas, sin fallos ni omisiones**, revisión del contrato de las **28 operaciones de Swagger** y **ocho comprobaciones HTTP** en la instancia local aislada. [Evidencia de mensajes y estados](evidencias/mensajes-http-2026-10-17.json). La verificación del proveedor utiliza respuestas simuladas; no acredita disponibilidad del servicio externo real.

## Cambios implementados

- IDs y filtros se reciben en Request DTO con `@Valid @RequestBody`; no se usan `@PathVariable`, `@RequestParam` ni `@ModelAttribute` en controladores.
- `POST /usuarios/buscar` combina ID, correo, activo y rol con paginación. Un CLIENTE sólo puede obtener su propio usuario; EJECUTIVO puede consultar otros.
- Los cambios de contraseña siguen limitados al propietario y revocan las sesiones anteriores.
- Los productos se devuelven como `ProductoResponse`, sin serializar entidades JPA.
- Se conservaron los mensajes de validación trabajados en clase. `ValidadorCampos` recibe `MethodArgumentNotValidException` y delega al formato estructurado de errores existente.
- El manejador global distingue validación, JSON inválido, duplicados, recursos inexistentes, permisos, métodos HTTP y fallos de integración. No se devuelven consultas SQL ni trazas técnicas.
- Las respuestas exitosas incluyen el DTO específico en `datos` y comparten `timestamp`, `status`, `codigo`, `mensaje`, `path` y `errores`; se conservan los códigos 200/201 y las respuestas 204 sin cuerpo. Los errores comparten `timestamp`, `status`, `codigo`, `mensaje`, `path` y `errores`. El token del login se obtiene de `datos.accessToken`.

## Contrato por body

| Método y ruta | Body |
|---|---|
| `POST /catalogos/codigos-postales/consultar` | `{"codigoPostal":"37907"}` |
| `POST /usuarios/buscar` | `{"correo":"correo del usuario","activo":true,"page":0,"size":20}` |
| `POST /clientes/listar` | Filtros de clientes y paginación; EJECUTIVO |
| `POST /clientes/buscar` | Criterios del cliente autorizado |
| `POST /clientes/consultar` | `{"id":1}` |
| `POST /clientes/cuentas` | `{"id":1}` |
| `POST /cuentas/listar` | Filtros de cuentas y paginación; EJECUTIVO |
| `POST /cuentas/consultar` | `{"numeroCuenta":"número de veinte dígitos"}` |
| `POST /cuentas/saldo` | `{"numeroCuenta":"número de veinte dígitos"}` |
| `PUT /clientes` | `{"id":1,"datos":{...campos editables...}}` |
| `PUT /usuarios/password` | `{"id":1,"datos":{"passwordActual":"...","passwordNueva":"..."}}` |
| `DELETE /clientes` | `{"id":1}` |

Los IDs y correos deben corresponder a registros del entorno revisado. Los filtros opcionales se omiten cuando no se utilizan. Las rutas antiguas se retiraron; la colección actualizada y `tests/jmeter/clientes-body.jmx` usan este contrato.

## Integridad en PostgreSQL: V9

`V9__integridad_validaciones.sql` añade CHECK para contacto, datos obligatorios, ingreso positivo, formatos de identidad, hash BCrypt, cuenta, saldo y domicilio. Los triggers impiden cambiar ID, CURP, RFC o fecha original de registro del cliente, y el ID, cliente asociado o fecha de creación del usuario. Otro trigger exige mayoría de edad al insertar o cambiar nacimiento.

Las restricciones nuevas se crean con `NOT VALID`: conservan las filas históricas existentes sin inventar correcciones, pero PostgreSQL rechaza INSERT y UPDATE que incumplan las condiciones. Una fila histórica inválida también puede bloquear una actualización posterior hasta que se concilie; debe revisarse antes del despliegue. El script de sólo lectura `scripts/auditar-validaciones-v9.sql` devuelve cantidades por regla, sin mostrar datos personales. Después de la conciliación, `scripts/validar-restricciones-v9.sql` comprueba todas las filas y marca las restricciones como validadas.

V1–V8 no se modifican. No se añade un trigger que prohíba cambiar cualquier rol, pues debe seguir funcionando `scripts/asignar-ejecutivo.sql`. La API pública no acepta asignar roles, saldo o identidades generadas. El acceso administrativo directo a la base debe mantenerse fuera de los clientes de la API.

## Ejecución y comprobación

La aplicación ejecuta Flyway al arrancar. No está configurada una tarea Gradle `flywayMigrate`: no utilizar ese comando. Configurar la conexión del entorno y compilar:

```bash
sh gradlew bootJar
sh gradlew test --tests com.proyecto.servicios.RegistroClienteIntegrationTest --tests com.proyecto.servicios.ContratoValidacionesTest
```

Para las pruebas PostgreSQL, usar una base local de pruebas y establecer `POSTGRES_TEST_URL`, `POSTGRES_TEST_USER` y `POSTGRES_TEST_PASSWORD`; la URL es JDBC. La prueba crea un esquema `test_validaciones_<UUID>` y elimina únicamente ese esquema al finalizar:

```bash
sh gradlew test --tests com.proyecto.servicios.service.ValidacionesPostgresTest
```

Sin esas variables, JUnit omite la suite PostgreSQL; una omisión no constituye evidencia de integridad. La prueba cubre migración de V8 a V9, conservación de históricos, rechazo de escrituras inválidas, protección de identidad y asignación administrativa de rol.

Con la aplicación local iniciada, Swagger está en `http://localhost:8080/swagger-ui/index.html`. El script de revisión QA utiliza 8081. Consultar el registro de arranque para identificar el puerto activo.

```bash
curl -i -X POST http://localhost:8080/catalogos/codigos-postales/consultar \
  -H 'Content-Type: application/json' -d '{"codigoPostal":"37907"}'
curl -i -X POST http://localhost:8080/usuarios/buscar \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"activo":true,"page":0,"size":20}'
```

## Despliegue y reversión

El [procedimiento operativo de V9](operacion-validaciones-v9.md) detalla respaldo privado, restauración, conciliación de históricos, validación, administración de roles y reversión. El respaldo y auditoría de Render siguen pendientes; los resultados de la copia local no los sustituyen.

Respaldar la base y comprobar datos históricos en una copia antes de aplicar V9 al entorno compartido. No publicar el código sin actualizar simultáneamente los consumidores del contrato. El SQL único `scripts/crear-base-datos.sql` instala ahora V1–V9 en una base nueva. Sobre una base existente, Flyway aplica únicamente las migraciones pendientes; no ejecutar el instalador completo.

No se utiliza `flyway undo` ni se borran filas de `flyway_schema_history`. Ante un fallo, restaurar la copia de respaldo junto con la versión de aplicación correspondiente, o preparar una migración correctiva posterior. No editar una migración ya aplicada.

## Revisión de entrega

- Arquitectura por capas, Request/Response DTO y validación declarativa.
- Consulta de usuarios con filtros combinables por body y autorización en servicio.
- Errores centralizados con mensajes por campo y sin detalles internos.
- Integridad SQL adicional y pruebas PostgreSQL aisladas.
- Colección y plan de carga adaptados; no se atribuyen resultados de carga antiguos al nuevo contrato.
- Respuestas exitosas con sobre común y datos tipados; las operaciones 204 no incluyen body.

## Resultado comprobado

El 17 de octubre de 2026 se ejecutaron **64 pruebas: cero fallos, cero errores y cero omisiones**, incluidas cuatro pruebas de integridad y migraciones en PostgreSQL real. [Resumen de evidencia](evidencias/validaciones-2026-10-17.json). También se verificaron 77 bodies JSON, el XML del plan JMeter y la sintaxis del script Python. No se ejecutó una nueva prueba de carga ni se desplegó en Render.

La revisión operativa adicional ejecutó **22 comprobaciones HTTP aprobadas** sobre una copia restaurada de la base local en PostgreSQL 17. Se comprobaron registro, duplicados, consultas, permisos por propietario y rol, revocación de token y OpenAPI. Flyway aplicó las nueve migraciones; la auditoría devolvió cero incumplimientos en las ocho reglas y se validaron las siete restricciones CHECK en esa copia. [Evidencia operativa](evidencias/revision-operativa-2026-10-17.json).

El workflow `.github/workflows/validaciones.yml` ejecuta seis suites y exige al menos 79 pruebas aprobadas, sin omitir PostgreSQL. La suite adicional verifica estados HTTP, errores de integridad, protección de mensajes del proveedor y esquemas y ejemplos de Swagger. Su resultado en GitHub para este ajuste permanece pendiente hasta publicar la actualización. Estas comprobaciones locales no acreditan el estado de la base de Render: antes de desplegar deben respaldarse y auditarse los datos del destino.
