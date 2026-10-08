# Plan de implementación conforme a la actividad

## Alcance confirmado

La actividad de onboarding de personas físicas exige registro, cuenta y usuario automáticos, autenticación, consultas generales y por identificadores, actualización, baja lógica, validaciones, persistencia y evidencias. El 8 de octubre de 2026 el usuario aclaró los catálogos solicitados por el profesor y eligió separar los accesos de EJECUTIVO y CLIENTE.

Los roles son una decisión del proyecto; no un requisito explícito del documento del classroom. No sustituyen ni eliminan las consultas generales que sí exige la actividad.

## Catálogos y datos de México

Implementación realizada: V7, IDs con FKs, consultas de catálogos y carga nacional postal desde archivo local ignorado. Contrato y preparación en [Catálogos de México](catalogos-mexico.md). Pendiente la comprobación manual de Bruno tras reiniciar App y revisar direcciones históricas sin correspondencia única.

Estado auditado antes de esta implementación: `RegistroClienteRequest` y las entidades usan texto para sexo, nacionalidad, estado civil y país. Sus validaciones exigen presencia y longitud, pero no pertenencia a un catálogo. El código postal exige cinco dígitos, pero no comprueba existencia ni correspondencia con estado, municipio o colonia.

Cambios requeridos:

- Catálogo de sexo con identificador compacto y descripción. El cliente guardará la llave foránea; el request enviará `sexoId`, no texto libre ni un carácter arbitrario.
- Catálogo de nacionalidad con la opción Mexicana habilitada para este proyecto; `nacionalidadId` debe existir y estar habilitado.
- Catálogo de país con México habilitado; el domicilio enviará `paisId`.
- Catálogo de estado civil para reemplazar también las etiquetas libres por valores definidos.
- Catálogo postal mexicano con relaciones entre estados, municipios y asentamientos. Un código postal puede permitir varias colonias: la selección debe comprobar la combinación, no asignar una sola colonia automáticamente.
- El código postal seguirá siendo un identificador textual de cinco dígitos, no un entero, para conservar ceros iniciales. Los demás identificadores de selección serán numéricos.
- Agregar endpoints de consulta de catálogos y documentar los IDs utilizados en Bruno. Sólo los valores habilitados deben aparecer como opciones.
- Agregar migraciones posteriores a V6, transformar los valores históricos reconocidos sin borrar datos y detectar valores no mapeables antes de imponer las nuevas restricciones. No editar migraciones ya aplicadas.
- Rechazar referencias inexistentes o combinaciones inválidas con 400 y mensajes de campo, antes de intentar guardar. Las llaves foráneas deben conservar la integridad en PostgreSQL.

Fuente prevista para el catálogo postal: [Catálogo Nacional de Códigos Postales de Correos de México/SEPOMEX](https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/ConsultaCP.aspx/ConsultaCP.aspx). La carga nacional, su versión y su procedimiento de actualización siguen pendientes; los datos de una sola dirección de prueba no equivalen al catálogo completo. La consulta oficial no se presenta como una API REST pública.

Las anotaciones de Bean Validation seguirán validando presencia, formato, longitud y tipos de los DTO. La existencia de referencias y las reglas entre datos se comprobarán en servicios. Los exception handlers centralizarán las respuestas, sin try/catch repetidos en los controladores. Lombok no valida campos.

La aclaración oral del profesor conserva nombre de 3 a 38 caracteres; el documento escrito indica nombres y apellidos de 2 a 50. Los apellidos actuales cumplen 2 a 50. RFC se adecuó a 12 o 13 caracteres como exige el documento, con validación de fecha interna para ambas longitudes.

## Permisos confirmados

| Operación | CLIENTE | EJECUTIVO |
| --- | --- | --- |
| Perfil y consulta de sus propios datos/cuentas | Permitido | Permitido |
| Consultar todos los clientes y aplicar filtros/paginación | Denegado | Permitido |
| Buscar otros clientes por ID, CURP, RFC, correo o cuenta | Denegado | Permitido |
| Consultar clientes activos, cuentas activas y rangos de registro generales | Denegado | Permitido |
| Ver contraseñas o hashes | Nunca | Nunca |

El registro público creará CLIENTE y no aceptará un rol proporcionado por el usuario. La provisión de EJECUTIVO deberá ser explícita, documentada y con contraseña BCrypt; no convertir todos los usuarios históricos en ejecutivos ni publicar credenciales reales en el repositorio.

La identidad se obtendrá del JWT y se verificará contra el usuario activo y su cliente. No confiar en un ID recibido desde la URL como prueba de pertenencia. Los permisos deben comprobarse en el backend. Falta implementar los roles, su persistencia y sus comprobaciones; esta tabla expresa el alcance acordado.

401 corresponde a autenticación ausente/inválida o acceso inactivo; 403 a una operación general sin rol autorizado; 404 a recursos inexistentes o fuera del ámbito de una consulta propia. Los permisos de actualización, baja lógica y cambio de contraseña se definirán junto con sus endpoints.

## Orden del trabajo pendiente

- **Verificación manual de catálogos:** reiniciar App, ejecutar la nueva carpeta Bruno y revisar los domicilios históricos sin vinculación. Modelo, migración, importación nacional y pruebas automáticas implementados.
- **Permisos de consultas:** roles CLIENTE/EJECUTIVO, provisión controlada y pruebas de acceso permitido/denegado.
- **Consultas y paginación:** todos los clientes, ID, CURP, RFC, correo, número de cuenta, activos, saldo, filtros combinados y rango de fechas. Agregar fecha de registro del cliente si falta; no utilizar la fecha de su cuenta como sustituto sin documentarlo.
- **Actualización y baja lógica:** completar PUT/DELETE, conservar CURP/RFC/número de cuenta, desactivar usuario y cuentas al desactivar cliente, probar consistencia transaccional.
- **Consulta de usuario y contraseña:** completar los endpoints requeridos `GET /usuarios/{id}` y `PUT /usuarios/{id}/password`, con permisos y revocación de tokens cuando corresponda.
- **Entrega y evidencias:** diagrama ER actualizado, migraciones/script de base de datos, documento técnico, matriz de casos y evidencias, pruebas de carga acordadas y README. Excel y JMeter forman parte del plan de entrega acordado; el documento pegado pide evidencias sin imponer esas herramientas por nombre.

## Avance ya validado

Registro y cuenta automáticos, usuario y BCrypt, login/JWT/bloqueo y sincronización GestoPago fueron implementados y probados en los cambios anteriores. La suite de sincronización terminó con 40 pruebas Java aprobadas y el usuario confirmó las tres pruebas de consulta posterior de Bruno, además del rechazo 401 sin token. La auditoría de catálogos revela requisitos adicionales pendientes: esos resultados no prueban catálogos ni los nuevos permisos.

Catálogos: suite completa posterior de 50 pruebas aprobadas y verificación final de 31 pruebas de registro/catálogos aprobadas, incluida carga postal nacional en PostgreSQL aislado. La carpeta Bruno está preparada; falta su ejecución manual. Los nuevos permisos todavía no se implementaron.
