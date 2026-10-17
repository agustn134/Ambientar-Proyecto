# Entrega de validaciones

Rama de trabajo: `feature/Validaciones`. Las modificaciones locales posteriores al commit `edf0586` todavía no están publicadas. Render conserva el contrato desplegado anteriormente hasta que se autorice su actualización.

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

Respaldar la base y comprobar datos históricos en una copia antes de aplicar V9 al entorno compartido. No publicar el código sin actualizar simultáneamente los consumidores del contrato. El SQL único de instalación anterior corresponde a V8; al arrancar la aplicación nueva, Flyway aplica V9 pendiente.

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

Antes de publicar: revisar el diff y la auditoría de datos históricos del destino. La publicación permanece pendiente de autorización del propietario.
