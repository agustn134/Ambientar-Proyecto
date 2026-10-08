# Usuarios de acceso y contraseña BCrypt

## Implementado

El registro `POST /clientes` requiere ahora `password`. Tras validar los datos se crea el cliente, domicilio, cuenta y usuario en una única transacción con `sfTransactionManager`. Si falla cualquiera, ninguno queda persistido.

La migración **V5__create_usuarios.sql** crea `usuarios`. Cada cliente puede tener un único usuario (`cliente_id` único); el correo es único y se almacena normalizado a minúsculas. Se guardan `password_hash`, `activo`, `intentos_fallidos`, `fecha_creacion` y `fecha_actualizacion`.

El nuevo usuario queda activo y con cero intentos fallidos. V5 no genera accesos ni contraseñas para clientes históricos. Login, JWT y bloqueo ya están implementados: consultar [login, JWT y bloqueo](login-jwt.md). V6 agrega la versión usada para revocar tokens.

## Contraseña

- Obligatoria y de tipo string JSON.
- Al menos ocho caracteres, incluyendo mayúscula, minúscula, número ASCII y carácter especial.
- Sin espacios ni caracteres de control. No se recorta ni normaliza la contraseña.
- Máximo **72 bytes UTF-8**, límite de BCrypt; caracteres multibyte pueden alcanzar el límite antes de 72 caracteres.
- BCrypt con costo 10 y salt generado por la biblioteca. Se usa `PasswordEncoder`, no un algoritmo casero ni cifrado reversible.
- El password no se serializa en respuestas ni aparece en `toString` del DTO. La entidad excluye el hash de JSON. El aspecto de logs no imprime argumentos, respuestas ni mensajes crudos de excepciones. Ver [calidad de código, registro y monitoreo](calidad-registro-monitoreo.md).
- Spring Security se alinea a 6.4.5 mediante dependencia administrada: incluye las correcciones de longitud de BCrypt indicadas en [CVE-2025-22228](https://spring.io/security/cve-2025-22228/) y [CVE-2025-22234](https://spring.io/security/cve-2025-22234/). La protección HTTP y los starters JWT se documentan en [login-jwt.md](login-jwt.md).

No usar contraseñas personales en las colecciones de Git. `PruebaCliente2026!` es un valor sintético de prueba, no una credencial del servidor ni de un usuario real.

## Request y response

Agregar al mismo body de registro:

```json
"password": "PruebaCliente2026!"
```

Ejemplo completo de body en `src/test/resources/registro-cliente-valido.json` y en la colección `tests/bruno`.

Respuesta esperada 201 (IDs ilustrativos):

```json
{
  "clienteId": 2,
  "estatus": "ACTIVO",
  "cuenta": {
    "numeroCuenta": "00000000000000000002",
    "saldo": 0,
    "estatus": "ACTIVA"
  },
  "usuario": {
    "usuarioId": 1,
    "correo": "agustinlopezparra13@gmail.com",
    "activo": true
  }
}
```

El nuevo objeto `usuario` no expone password ni hash. Si la contraseña es inválida, respuesta 400 con `VALIDACION` y un error del campo `password`. Si se envía un número en lugar de texto, respuesta 400 con `JSON_INVALIDO`. Las respuestas no incluyen el valor rechazado.

## Pruebas con Bruno

- Reiniciar el backend para aplicar V5. Abrir la colección `tests/bruno` y elegir **Local** (o entorno global con el mismo `baseUrl`).
- Ejecutar **Registro valido**. El JSON utiliza los datos proporcionados por Agustín. Los errores utilizan el perfil de Dulce con identificadores sintéticos: ver [datos de prueba](datos-prueba.md). Al repetir los mismos datos se espera 409.
- El test captura `clienteId`, `numeroCuenta` y `usuarioId` como variables runtime de esta colección.
- Ejecutar **Password invalida**, **Password ausente** y **Password tipo incorrecto**: deben devolver 400 y error sobre `password`.
- Repetir los casos de validación y duplicados del registro. Sus bodies ya incluyen una contraseña válida.
- Los casos de Bruno verifican HTTP/JSON; el hash y la atomicidad se verifican con SQL y las pruebas Java.

SQL para comprobar el nuevo usuario sin mostrar el hash completo (sustituir el ID):

```sql
SELECT id, cliente_id, correo, activo, intentos_fallidos,
       LENGTH(password_hash) AS longitud_hash,
       LEFT(password_hash, 7) AS algoritmo_costo
FROM usuarios
WHERE cliente_id = 2;
```

Se espera longitud 60, prefijo `$2a$10$`, activo true y cero intentos. El prefijo por sí solo no prueba que la contraseña sea correcta; el test Java comprueba `PasswordEncoder.matches`.

## Pruebas automatizadas

```powershell
.\gradlew.bat test --tests com.proyecto.servicios.RegistroClienteIntegrationTest
```

La base temporal H2 aplica V3 y V5. No registra usuarios en PostgreSQL local. Se mantienen las siete pruebas de registro y se agregan cinco para usuario/BCrypt: hash verificable sin exposición, contraseñas inválidas, rechazo de coerción numérica, límite 72 bytes con salt distinto y rollback ante fallo del usuario.

Resultado del 8 de octubre de 2026: **12 pruebas ejecutadas, 12 aprobadas**, `BUILD SUCCESSFUL in 6m 14s`. Se verificó la creación relacionada, el hash y su comprobación con BCrypt, contraseñas inválidas, límites en bytes, ausencia de exposición y rollback de los cuatro registros.

Pendiente ejecutar la colección actualizada en Bruno contra el backend local y comprobar V5 en PostgreSQL. No se reinició ni modificó la base PostgreSQL del usuario desde estas pruebas. Los dos fallos previamente documentados de tests existentes de GestoPago siguen fuera de este bloque; la suite completa no se volvió a ejecutar aquí.

## Trabajo siguiente

Los puntos de login, tres intentos, JWT y verificación de estado de la siguiente lista se completaron en `feature/login-jwt`; ver contrato y pruebas en [login-jwt.md](login-jwt.md). Continúan pendientes recuperación/cambio de contraseña, desbloqueo y alta de acceso para clientes históricos.

- `POST /auth/login` con respuesta genérica para credenciales incorrectas.
- Tres intentos incorrectos consecutivos bloquean el acceso; éxito reinicia el contador. El bloqueo se debe persistir incluso cuando la petición devuelve error.
- JWT firmado con expiración, clave externa al repositorio y validación estándar de Spring Security.
- Comprobar estado del usuario en cada petición protegida para impedir el acceso con tokens emitidos antes del bloqueo.
- Probar tokens ausentes, manipulados y vencidos, usuario inactivo y control de acceso a recursos de otros clientes.
- Documentar el proceso de alta de acceso para clientes históricos: no asignar passwords conocidas por defecto.
