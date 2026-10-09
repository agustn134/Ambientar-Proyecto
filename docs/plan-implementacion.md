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

La identidad se obtiene del JWT y se verifica contra el usuario activo, su cliente, rol y versión. No se confía en el ID de la URL como prueba de pertenencia. V8 implementa los roles y las consultas con comprobaciones en el backend; consultar [Consultas, roles y paginación](consultas-permisos.md).

401 corresponde a autenticación ausente/inválida o acceso inactivo; 403 a una operación general sin rol autorizado; 404 a recursos inexistentes o fuera del ámbito de una consulta propia. Los permisos de actualización, baja lógica y cambio de contraseña se definirán junto con sus endpoints.

## Orden del trabajo pendiente

- **Verificación manual de catálogos:** reiniciar App, ejecutar la nueva carpeta Bruno y revisar los domicilios históricos sin vinculación. Modelo, migración, importación nacional y pruebas automáticas implementados.
- **Consultas y permisos validados:** consultas propias, acceso ajeno 404, listados generales denegados al CLIENTE con 403, paginación estable (7 clientes, 4 páginas), filtros y errores 400/404. La comprobación final `/cuentas` se repitió a las 23:28:26 con JWT CLIENTE vigente: 403 ACCESO_DENEGADO, un Test aprobado y cero fallos. Bloque manual cerrado; consolidar las evidencias. Las fechas históricas desconocidas permanecen NULL.
- **Mantenimiento validado:** actualización, contraseña y secuencia final de CURP protegida/baja/consultas posteriores comprobadas en Bruno. Cliente QA 6 INACTIVO, usuario 5 desactivado, JWT rechazado antes de expirar y login tras baja 401. No repetir la secuencia con ese perfil; consolidar las evidencias para la entrega.
- **Entrega y evidencias:** diagrama ER actualizado, migraciones/script de base de datos, documento técnico, matriz de casos y evidencias, pruebas de carga acordadas y README. Excel y JMeter forman parte del plan de entrega acordado; el documento pegado pide evidencias sin imponer esas herramientas por nombre.

## Avance ya validado

Registro y cuenta automáticos, usuario y BCrypt, login/JWT/bloqueo y sincronización GestoPago fueron implementados y probados en los cambios anteriores. La suite de sincronización terminó con 40 pruebas Java aprobadas y el usuario confirmó las tres pruebas de consulta posterior de Bruno, además del rechazo 401 sin token. La auditoría de catálogos revela requisitos adicionales pendientes: esos resultados no prueban catálogos ni los nuevos permisos.

Catálogos: suite completa posterior de 50 pruebas aprobadas y verificación final de 31 pruebas de registro/catálogos aprobadas, incluida carga postal nacional en PostgreSQL aislado.

Consultas y permisos: V8, CLIENTE/EJECUTIVO, provisión auditada y consultas implementadas; suite completa inicial de **60 pruebas aprobadas**, sin omisiones. Asignación solicitada del usuario de prueba 4 a EJECUTIVO completada; bloque manual cerrado el 8 de octubre con el 403 final de listado de cuentas generales bajo CLIENTE.

Mantenimiento: actualización, baja lógica y usuario/contraseña implementados; suite posterior de **67 pruebas aprobadas**, sin omisiones. Dos pruebas Java de regresión aprobaron después la corrección del nombre del campo de validación. La secuencia final manual quedó cerrada con las capturas del 8 de octubre: CURP 400, baja 204, consultas posteriores 200, JWT/login 401. Consultas/paginación también cerradas; pendientes consolidación de evidencias/entregables. Token anterior revocado por cambio de contraseña tiene cobertura Java, con evidencia manual independiente aún pendiente.
