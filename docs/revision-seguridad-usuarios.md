# Correcciones de revisión: usuarios y errores

La consulta GET /usuarios/{id} verifica la sesión activa y permite acceso al propietario o al rol EJECUTIVO. Un CLIENTE recibe 404 al solicitar un ID ajeno, exista o no, para no revelar su existencia. PUT /usuarios/{id}/password sólo permite al propietario, incluso si el actor es EJECUTIVO; confirma la contraseña actual y revoca los JWT anteriores.

GET /usuarios/me permite consultar la ficha propia sin proporcionar un identificador. La respuesta no incluye contraseñas ni hashes.

Los errores de validación y negocio se manejan globalmente mediante RestControllerAdvice. El manejador de proveedor y de errores inesperados utiliza el mismo contrato: timestamp, status, codigo, mensaje, path y errores. Los errores de seguridad conservan ese contrato desde el filtro de autenticación. Las respuestas no exponen trazas ni credenciales.

Se especificaron mensajes descriptivos para el ID y las contraseñas actual y nueva. La suite de integración comprueba propietario, acceso a ID ajeno, acceso de EJECUTIVO, rechazo de cambio de contraseña ajena, validación y revocación de tokens. Se añadió cobertura de /usuarios/me e ID cero.

Estos cambios deben integrarse en GitHub y desplegarse en Render para que estén disponibles públicamente.

Validación local: RegistroClienteIntegrationTest, 42 pruebas aprobadas, cero fallos y cero omitidas (10 de octubre de 2026).
