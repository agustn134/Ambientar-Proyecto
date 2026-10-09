# Actualización, baja lógica y cambio de contraseña

## Permisos y contrato

| Operación | CLIENTE | EJECUTIVO |
| --- | --- | --- |
| `PUT /clientes/{id}` | Cliente propio | Cualquier cliente |
| `DELETE /clientes/{id}` | Baja propia | Baja de cualquier cliente |
| `GET /usuarios/{id}` | Usuario propio | Cualquier usuario |
| `PUT /usuarios/{id}/password` | Usuario propio con contraseña actual | Sólo su propio usuario con contraseña actual |

Siempre requieren JWT. Sin sesión válida: 401; recurso inexistente o ajeno: 404 controlado. El ejecutivo no puede usar el endpoint de contraseña como restablecimiento administrativo. No hay un endpoint de reactivación ni recuperación de contraseña en este cambio.

Los DTO de usuario no incluyen contraseña ni hash. Las peticiones de cambio de contraseña son write-only y sus `toString` ocultan credenciales. Los logs existentes no imprimen argumentos/respuestas ni mensajes crudos de error.

## Actualización

PUT es una actualización completa de los campos editables: nombre/segundo nombre/apellidos, nacimiento, IDs de sexo/nacionalidad/estado civil, correo y teléfonos, domicilio e información laboral. El body utiliza el contrato del registro, **omitiendo CURP, RFC y contraseña**.

CURP, RFC, número de cuenta, rol y estatus no pueden modificarse por esta petición. Si se envían esos campos con valor, se devuelve 400 por campo. Tampoco se modifican saldo, cuentas, ID del cliente ni fecha de registro. Los campos JSON desconocidos se ignoran por la configuración existente y no alteran las propiedades no incluidas en el DTO.

Se reutilizan las mismas validaciones de formato, edad, catálogos, domicilio y tipos estrictos. El código postal debe corresponder al asentamiento. La selección no acepta texto libre de estado/municipio/colonia.

El correo se normaliza a minúsculas y se comprueba su unicidad excluyendo al cliente/usuario actual. Un duplicado devuelve 409. Si cambia, cliente y usuario se actualizan dentro de `sfTransactionManager` y se incrementa `version_token`; los JWT anteriores quedan revocados. Después de esa respuesta 200, iniciar sesión con el correo nuevo y la contraseña vigente. Un cliente histórico sin usuario conserva esa condición; no se crea acceso sin una contraseña proporcionada en un registro.

El usuario se bloquea antes que el cliente para serializar cambios con login, provisión y baja. Las filas se refrescan tras el bloqueo para no sobrescribir un estado de usuario previamente bloqueado. Una actualización de datos no desbloquea usuarios ni reactiva clientes/cuentas.

## Baja lógica

DELETE devuelve 204 y conserva las filas. En la misma transacción:

- Cliente queda INACTIVO.
- Todas sus cuentas quedan INACTIVA, conservando número y saldo.
- Usuario asociado queda inactivo y se revocan sus JWT.

Si falla una modificación, se revierte todo. El ejecutivo puede repetir una baja ya completada y recibir 204 sin volver a incrementar la versión del token. Un cliente que se da de baja pierde inmediatamente el acceso y no puede reutilizar su token para repetirla. Los datos permanecen consultables para un ejecutivo activo.

## Contraseña y usuario

GET `/usuarios/{id}` devuelve usuarioId, clienteId, correo, activo, rol y fechas, con los permisos de la tabla.

PUT `/usuarios/{id}/password` utiliza:

```json
{"passwordActual":"PruebaCliente2026!","passwordNueva":"NuevaPrueba2026!"}
```

Son contraseñas sintéticas de QA. La nueva cumple las reglas existentes: mínimo 8, mayúscula, minúscula, número y especial, sin espacios y máximo 72 bytes UTF-8 para BCrypt. No se convierte un número JSON a contraseña textual ni se recortan espacios de la contraseña actual.

Contraseña actual incorrecta: 401 controlado, sin cambiar hash o versión. Formato/nueva contraseña inválidos: 400. Este rechazo no incrementa el contador de intentos de login; ese contador pertenece a `/auth/login`. La petición exitosa devuelve 204, guarda exclusivamente BCrypt e incrementa versión para revocar todos los tokens anteriores. Iniciar sesión con la nueva contraseña después. No devuelve un token ni acepta rol, estado o correo.

## Probar en Bruno

Consultar la [guía manual de Bruno](bruno-guia-manual.md) para las nuevas categorías y el flujo para retomar el perfil existente. `Mantenimiento` se divide en preparación, actualización, contraseña y baja lógica. El PUT válido ahora incluye el teléfono confirmado de Dulce `4681046222`; ejecutarlo para actualizar el registro existente antes de la baja.

Reiniciar App para cargar los endpoints. Renovar **Auth/Acceso/Login valido** y **Consultas/Preparacion del ejecutivo/Login ejecutivo**; el usuario de prueba 4 ya fue elevado de forma controlada. Verificar **Perfil ejecutivo**.

La carpeta **Mantenimiento** tiene un perfil adicional de Dulce con correo `dulce.mantenimiento@example.com`, CURP `LOPD051108MGTXXX09` y RFC `LOPD051108ZZ2` sintéticos. No se usa el cliente principal ni el ejecutivo como destinatario de la baja.

Ejecutar en orden:

- **Registro QA mantenimiento** (201) captura clienteMantId/usuarioMantId/cuentaMant; **Login mantenimiento** captura tokenMantenimiento.
- Consulta de usuario y actualización válida: 200. El número exterior 844 y empresa “Bodega Aurrera QA” son cambios deliberados de prueba, no datos confirmados de la dirección/trabajo reales.
- CURP enviada en PUT: 400; correo del cliente principal duplicado: 409; actualizar el cliente principal con token de mantenimiento: 404. Ejecutar previamente **Consultas/Datos propios/Mi cliente** para capturar el clienteId principal.
- Contraseña actual incorrecta: 401; nueva inválida: 400; cambio correcto: 204; token anterior: 401; login con contraseña nueva: 200.
- Al final, **Baja QA por ejecutivo**: 204. Consultar con tokenEjecutivo confirma cliente INACTIVO, cuentas INACTIVA y usuario inactivo; token/login del perfil dado de baja: 401.

No repetir toda la carpeta después de la baja: ese perfil ya queda inactivo y sus identificadores siguen ocupados. Un segundo registro devuelve 409 y no debe borrarse la base para hacerlo funcionar. Para otra ejecución completa se necesita un perfil QA nuevo con identificadores y correo sintéticos únicos. No cambiar los números de cuenta a mano.

La ejecución manual inicial confirmó registro, usuario propio, actualización, rechazo de correo duplicado y acceso ajeno, validación de contraseñas, cambio de contraseña y login con la nueva. El perfil creado fue cliente 6 / usuario 5. La primera prueba CURP devolvió 400 con el campo incorrecto `request`; se corrigió el handler, se aprobaron dos pruebas Java de regresión y la nueva captura confirmó `errores[0].campo=curp` con el mensaje «CURP no se puede modificar».

La baja inicial a las 21:48:08 devolvió 401 porque el JWT ejecutivo vencía a las 21:46:54; no modificó al cliente. Al renovar el login ejecutivo y completar la secuencia, el 8 de octubre de 2026 se confirmó la baja correcta del cliente 6 / usuario 5.

| Comprobación manual | Respuesta observada |
|---|---|
| Login con contraseña nueva, recuperación de perfil/ficha y actualización | 200; se trabajó con cliente 6 / usuario 5. El PUT envió el teléfono corregido 4681046222. |
| CURP no editable | 400 VALIDACION, campo `curp` y mensaje de campo protegido. |
| Login y perfil ejecutivo | 200; usuario 4 / cliente 5, rol EJECUTIVO. |
| Baja QA por ejecutivo | 204, sin body. |
| Cliente conserva datos tras baja | 200; conserva el registro y muestra cliente INACTIVO. La petición comprueba también cuentas INACTIVA. |
| Usuario desactivado | 200; usuario 5 con `activo=false`. |
| JWT del cliente dado de baja | 401 NO_AUTENTICADO a las 22:49:36.4976582. |
| Login tras baja con contraseña nueva | 401 CREDENCIALES_INVALIDAS a las 22:50:01.0630857. |

El JWT de mantenimiento vencía a las 2026-10-09T04:49:46.849384200Z, equivalentes a las 22:49:46 del 8 de octubre en México. Su rechazo a las 22:49:36 ocurrió antes de expirar: esta evidencia verifica la pérdida de acceso tras la baja sin confundirla con expiración. El bloque de CURP corregida y baja con consultas posteriores queda cerrado.

También se creó el perfil permanente de Dulce: cliente 7 / usuario 6, correo `dulce.maria.lopez.parra.qa@gmail.com`, cuenta terminada en 07, respuesta 201. Es un registro distinto del perfil de mantenimiento dado de baja; no se utilizó como destinatario del DELETE. No se aportó todavía evidencia de las altas de Sofía o Nancy.

La petición independiente Token anterior revocado, específica del cambio de contraseña, todavía no tiene evidencia manual; su comportamiento está cubierto por las pruebas Java. No repetir el ciclo con el cliente 6, que ya está inactivo. El agente únicamente ejecutó la asignación solicitada del usuario de prueba 4 a EJECUTIVO; las peticiones de actualización, contraseña y baja sobre el perfil QA fueron enviadas por el usuario desde Bruno.

## Pruebas automatizadas

El 8 de octubre de 2026 la suite completa pasó **67 pruebas**, sin fallos ni omisiones. Incluye modificación válida e identificadores inmutables, correo sincronizado y revocación, rechazo de edad/catálogo/acceso ajeno, rollback si falla usuario, baja de varias cuentas preservando saldos, rollback ante fallo bancario, BCrypt nuevo y rechazo/revocación de tokens, permisos de usuario y contraseña.

Estas pruebas utilizan datos aislados: H2 para HTTP/JPA y esquemas temporales de PostgreSQL para catálogo, sincronización y roles. No ejecutaron bajas o cambios de contraseña sobre los registros reales del usuario.

No se modificaron V7 ni V8; las nuevas operaciones no requieren cambios de esquema. Se completaron mantenimiento y la comprobación manual de consultas/paginación. Quedan la consolidación de evidencias, diagrama ER y documento técnico del proyecto.
