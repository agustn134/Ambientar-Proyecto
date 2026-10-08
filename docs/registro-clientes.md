# Registro de clientes, validaciones y manejo de errores

## Alcance

Se implementa `POST /clientes` con cliente, domicilio y cuenta, validaciones y errores por campo. Usuarios, contraseña, JWT, búsquedas, actualización y baja se incorporarán después; las peticiones de esas etapas en Bruno siguen pendientes.

El servicio `RegistroClienteService` usa explícitamente `sfTransactionManager`. La relación JPA fue corregida para usar `domicilios.cliente_id`, que ya existe desde V3. V3 no se modifica: V4 agrega un índice único para correo sin distinguir mayúsculas.

## Ejecutar y probar

- Iniciar Docker Desktop y `docker start postgres-dev redis-dev`.
- Ejecutar la configuración App de IntelliJ con `DB_PASSWORD` y `GESTOPAGO_PASSWORD`, o usar PowerShell con esas variables antes de `./gradlew.bat bootRun`.
- Reiniciar el backend para cargar el endpoint y aplicar V4. Confirmar el log `Started App`.
- En Bruno abrir la colección de la carpeta `tests/bruno` y seleccionar el entorno **Local**. No inicializar otro repositorio Git dentro de ella.
- Ejecutar **Registro valido**. Esperado: 201, `clienteId` y `cuenta.numeroCuenta`.
- Ese caso captura ambos valores como variables runtime mediante `bru.setVar`; sirven dentro de esta colección. En otras colecciones se deben copiar al entorno global. El número de cuenta es texto de 20 dígitos.
- Ejecutar los casos inválidos y duplicados. Repetir el registro válido sin cambiar sus datos devuelve 409: es una prueba de unicidad, no un fallo.

La colección no contiene contraseñas reales. El JSON válido es `src/test/resources/registro-cliente-valido.json` y usa identificadores sintéticos que cumplen el formato, no una identidad oficial verificada.

## Contrato

`Content-Type: application/json`, `Accept: application/json`. Ejemplo de respuesta 201:

```json
{"clienteId":1,"estatus":"ACTIVO","cuenta":{"numeroCuenta":"00000000000000000001","saldo":0,"estatus":"ACTIVA"}}
```

El ID de secuencia de cliente genera una cuenta única de 20 dígitos. No es una CLABE. Saldo inicial definido por el sistema: 0. No se aceptan saldo, IDs ni estatus como datos de registro: se generan en el servicio (los campos JSON desconocidos actualmente se ignoran).

Ejemplo 400:

```json
{"timestamp":"2026-10-08T12:00:00","status":400,"codigo":"VALIDACION","mensaje":"Revisa los campos de la petición","path":"/clientes","errores":[{"campo":"nombre","mensaje":"mensaje de la restricción"}]}
```

| Condición | HTTP | Código |
|---|---|---|
| Registro correcto | 201 | — |
| Restricción de campo o menor de edad | 400 | VALIDACION |
| JSON mal formado, fecha imposible, tipo incorrecto | 400 | JSON_INVALIDO |
| CURP, RFC o correo duplicado | 409 | DATO_DUPLICADO |
| Fallo interno inesperado | 500 | ERROR_INTERNO o manejador global |

Las respuestas no incluyen valores rechazados ni detalles SQL. El manejador específico de clientes conserva las respuestas existentes de GestoPago.

## Decisiones de validación

- Nombres: 3–38; apellidos: 2–50; letras Unicode y espacios. Se quitan espacios extremos y se agrupan espacios repetidos antes de validar. Sólo espacios no es un nombre.
- Segundo nombre y teléfono alternativo: omitir o enviar null cuando no existan; texto vacío no equivale a un valor válido. Número interior es opcional.
- CURP: 18, formato con sexo y entidad; RFC de persona física: 13. Se convierten a mayúsculas y se verifica que el segmento de fecha sea válido. No se verifica expedición oficial ni coincidencia de identidad con nombre/fecha.
- Correo: formato válido, máximo 100; normalización a minúsculas; consulta previa e índice único `lower(correo_electronico)` para concurrencia.
- Nacimiento: texto YYYY-MM-DD, fecha real sin hora, pasada y edad de al menos 18 usando la fecha del servidor.
- Teléfonos: texto de 10 dígitos. CP: texto de 5 dígitos (conserva ceros iniciales).
- Ingreso: número JSON positivo; hasta 13 dígitos enteros y 2 decimales. Se rechaza `"15000"`, booleanos y texto; se acepta 15000 o 15000.50.
- Domicilio y datos laborales: obligatorios con límites compatibles con V3. Sexo/nacionalidad/estado civil son texto obligatorio limitado; no se implementa aún un catálogo de valores permitidos.
- V4 falla si hay correos históricos duplicados ignorando mayúsculas. No elimina ni modifica datos para resolverlo.

## Evidencias y casos

| Caso | Petición | Esperado | Evidencia necesaria |
|---|---|---|---|
| Registro valido | Registro válido | 201, saldo 0, cuenta ACTIVA | JSON de respuesta y filas relacionadas |
| Nombre invalido | Nombre `@@@` | 400, error nombre | Body de error, sin filas nuevas |
| Menor de edad | Nacimiento menor de 18 | 400 | Sin filas nuevas |
| CURP duplicada | Repetir CURP | 409 | Una sola cuenta del cliente |
| Correo duplicado | Correo existente, distinto RFC/CURP | 409 | Sin cliente adicional |
| Ingreso con tipo incorrecto | Ingreso `"15000"` | 400 JSON_INVALIDO | Campo informacionLaboral.ingresoMensual |
| RFC duplicado | RFC existente, distinta CURP/correo | 409 | Sin filas adicionales |

Las pruebas de Bruno validan los códigos y campos de respuesta. Guardar el body observado y captura de Tests para el Excel de entrega; no confundir ejemplo esperado con evidencia ejecutada.

Comprobación SQL (sustituir el ID obtenido):

```sql
SELECT c.id, c.nombre, d.cliente_id, a.numero_cuenta, a.saldo, a.estatus
FROM clientes c JOIN domicilios d ON d.cliente_id=c.id
JOIN cuentas a ON a.cliente_id=c.id
WHERE c.id = 1;
```

## Pruebas automatizadas

```powershell
.\gradlew.bat test --tests com.proyecto.servicios.RegistroClienteIntegrationTest
```

Usan HTTP de prueba, JPA y el SQL V3 en H2 modo PostgreSQL sin conectarse a la base del usuario. Cubren persistencia de las tres tablas, normalización, duplicados, tipos, fecha imposible, mayoría de edad, límites y rollback al fallar la cuenta. H2 no reemplaza la verificación de V4 y del flujo en PostgreSQL real.

### Resultado ejecutado el 8 de octubre de 2026

- Pruebas de registro: **7 ejecutadas, 7 aprobadas**.
- Suite completa: **14 ejecutadas, 12 aprobadas, 2 fallidas** en `GestoPagoProductServiceTest` existente. El test de sincronización no proporciona el mapper requerido y el de respuesta vacía espera éxito aunque el servicio lanza una excepción. No se modificó el servicio GestoPago ni esos tests en esta etapa.
- El usuario repitió las pruebas de registro: `BUILD SUCCESSFUL in 58s` el 8 de octubre de 2026.
- El usuario informó que ejecutó los siete casos de Bruno contra el backend local. La captura compartida del caso **RFC duplicado** confirma HTTP **409**, código **DATO_DUPLICADO**, en **39 ms**. Los resultados individuales de los otros seis casos fueron reportados por el usuario; falta conservar sus respuestas/capturas para el Excel de entrega.
- Las pruebas automatizadas no registran clientes en la base local del usuario. La ejecución manual de Bruno sí genera un cliente, domicilio y cuenta cuando el registro devuelve 201. La aplicación de V4 debe comprobarse en `flyway_schema_history` si se requiere evidencia de migración.

## Trabajo pendiente

- Conservar esta documentación y la colección junto con el código en Git; guardar evidencias de respuesta sin credenciales.
- Implementar usuarios y autenticación: usuario automático, BCrypt, login JWT y bloqueo por intentos. Extender el registro transaccional para incluir al usuario.
- Completar consultas de clientes/cuentas, filtros y paginación, actualización y baja lógica; mantener independientes los estados de cuenta y login.
- Consolidar Excel de pruebas, README y diagrama ER. El Excel es un entregable adicional, no queda reemplazado por este Markdown.
