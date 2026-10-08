# Login, JWT y bloqueo por intentos

## Estado y contrato

Implementados `POST /auth/login` y `GET /auth/me`, autenticación Bearer JWT y bloqueo después de tres contraseñas incorrectas consecutivas. No se implementa todavía recuperación de contraseña, desbloqueo público ni refresh token.

La consulta de lectura realizada antes de este cambio confirmó que el cliente **3** tiene el usuario **2**, activo y con cero intentos. El 409 mostrado en Bruno era un registro repetido, no ausencia de usuario. No se modificó ese usuario ni se probó su login real desde las pruebas automatizadas.

`POST /auth/login` es público; requiere JSON:

```json
{
  "correo": "agustinlopezparra13@gmail.com",
  "password": "PruebaCliente2026!"
}
```

La contraseña anterior es la del fixture, no una contraseña personal. Debe coincidir con la usada al registrar el usuario; no se sobrescribe una contraseña existente automáticamente.

Respuesta 200:

```json
{
  "accessToken": "JWT_EMITIDO_POR_EL_SERVIDOR",
  "tokenType": "Bearer",
  "expiresIn": 300,
  "expiresAt": "fecha UTC de expiración"
}
```

Las credenciales incorrectas, usuario inexistente o acceso inactivo devuelven **401 CREDENCIALES_INVALIDAS**, con el mismo mensaje: `Credenciales inválidas o acceso no disponible`. No se revela si el correo existe, el hash ni el número de intentos. JSON/tipos/formato inválidos devuelven 400 y no incrementan el contador.

El correo se recorta y convierte a minúsculas; la contraseña se conserva exactamente. El login sólo comprueba que exista texto y que no supere 72 bytes UTF-8; una contraseña incorrecta no tiene que cumplir la complejidad de registro para contar como intento fallido.

## Contador y concurrencia

- La fila del usuario se lee con bloqueo pesimista de escritura dentro de `sfTransactionManager` para serializar intentos concurrentes.
- Un fallo devuelve un resultado vacío desde el servicio, confirma el contador y sólo entonces el controller produce el error 401. No se pierde el incremento por rollback de una excepción HTTP.
- Un éxito reinicia los intentos a cero.
- En el tercer fallo: intentos=3, activo=false y se incrementa version_token.
- Usuarios ya bloqueados no reciben más incrementos ni tokens. Una contraseña correcta no desbloquea al usuario.
- Bloquear login no modifica cuentas ni cliente. Cuenta INACTIVA permite login y consultar perfil; cliente INACTIVO no permite acceso.
- Se realiza una comparación BCrypt con un hash ficticio cuando el usuario no existe o está inactivo, evitando omitir completamente el trabajo de verificación de contraseña.

La migración **V6__version_token_usuarios.sql** agrega version_token sin cambiar passwords ni estados existentes.

## JWT y protección HTTP

JWT firmado con **HS256**, clave Base64 externa de al menos 32 bytes y vigencia predeterminada de cinco minutos. El token está firmado, no cifrado; contiene subject (ID de usuario), issuer, audience, iat, nbf, exp, jti y ver, sin correo, domicilio, password ni hash.

Spring Security Resource Server verifica firma y algoritmo, expiración/not-before sin tolerancia adicional, emisor y audiencia. Se consulta en base el estado activo, versión del token y cliente activo para cada autenticación Bearer. Esto revoca inmediatamente los JWT del usuario bloqueado; reactivar el usuario sin restaurar la versión anterior no rehabilita los tokens previos.

Configuración: issuer `http://localhost:8080`, audience `gestopago-api`, TTL 300 segundos. La clave no tiene valor por defecto: el backend falla al arrancar si JWT_SECRET falta o es demasiado corta. No se implementa OAuth2 completo ni Keycloak: es emisión local de JWT para esta API.

Rutas públicas: POST /clientes, POST /auth/login y documentación Swagger/OpenAPI de lectura. El resto exige Bearer. Los endpoints antiguos `/personas`, `/personasActualiza` y `/personasElimina` quedan denegados mientras no exista una política de permisos adecuada. Las consultas futuras de clientes/cuentas deberán comprobar propiedad o roles; no se implementaron esas consultas en este bloque.

- Token ausente, alterado, vencido, emisor/audiencia/versión incorrectos o usuario inactivo: **401 NO_AUTENTICADO**.
- Usuario autenticado sin permiso para una operación: **403 ACCESO_DENEGADO**.
- `GET /auth/me` devuelve únicamente el perfil del usuario indicado por el JWT. No recibe un ID ajeno por URL y no expone hashes.

No se crean sesiones ni se usa autenticación por cookies. Se desactiva CSRF para el flujo Bearer explícito de esta API. Login y perfil devuelven Cache-Control: no-store. No se agregaron límites de tasa globales ni recuperación de cuentas; siguen siendo trabajo adicional.

## Configurar el backend local

Antes de reiniciar, generar una clave aleatoria en PowerShell:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

Para ejecutar desde esa misma ventana, configurar también DB_PASSWORD y GESTOPAGO_PASSWORD, entrar en la carpeta del proyecto y ejecutar `./gradlew.bat bootRun`.

Para IntelliJ, consultar localmente `$env:JWT_SECRET`, copiarlo al campo de variables de entorno de la configuración App y conservar las otras variables. No subir la clave a Git, a capturas ni al README. No es la contraseña PostgreSQL y no se configura en Bruno.

En este workspace ya se generó una clave aleatoria de 32 bytes y se añadió a `.idea/runConfigurations/App.xml` conservando las variables existentes. Esa carpeta está excluida de Git mediante `.gitignore`. Reiniciar completamente **App** desde IntelliJ para heredar la variable; un reinicio automático de DevTools no cambia las variables del proceso. En otro equipo se debe generar/configurar su propia clave.

Mantener la misma clave para reinicios si se desean conservar los JWT aún vigentes. Generar otra clave invalida los tokens anteriores. Para producción debe proporcionarse una clave protegida y usarse HTTPS.

## Pruebas manuales en Bruno

Recargar `tests/bruno` y seleccionar Local o un entorno global con baseUrl. La nueva carpeta **Auth** contiene las peticiones.

- **Login valido**: usar el correo y contraseña de registro de Agustín. Esperado 200; captura `token` runtime dentro de esta colección.
- **Perfil autenticado**: esperado 200 y correo propio. Token ausente: 401. Token alterado: 401; el script modifica la firma del token capturado.
- **Login formato invalido**: 400. **Login inexistente**: 401 sin revelar existencia.
- **Registro usuario para bloqueo**: usa a Dulce con los datos sintéticos documentados. Esperado 201 sólo la primera vez; repetir devuelve 409. No borrar clientes para repetir automáticamente la prueba.
- **Login usuario de bloqueo**: 200 y captura `tokenBloqueo` antes de bloquearlo.
- Ejecutar **Intento incorrecto usuario de bloqueo** tres veces. Cada petición devuelve 401 y el tercer intento bloquea sólo a ese usuario de prueba.
- **Login usuario bloqueado** con contraseña correcta: 401. **Token usuario bloqueado** con el JWT previamente emitido: 401.
- **Operacion sin permiso**: 403 usando el token válido de Agustín.

No ejecutar una colección completa repetidamente sin revisar estado: los casos de bloqueo tienen efectos reales sobre el usuario de prueba. Después del bloqueo no existe desbloqueo público; para otro ciclo usar una nueva identidad sintética o definir un procedimiento administrativo.

**Token vencido** requiere un token válido previamente emitido cuyo expiresAt ya pasó. Guardarlo como variable `tokenVencido` (en Vars de la petición o en el entorno), esperar hasta esa hora y ejecutar: esperado 401. No basta alterar exp en el payload: eso prueba firma alterada. Los tests Java emiten un JWT firmado ya vencido y comprueban el rechazo sin esperar cinco minutos.

Las variables runtime capturadas en Auth no se comparten automáticamente con colecciones separadas. Para productos en otra colección, configurar su Auth como Bearer y proporcionar el token de login; no introducir JWT_SECRET en el cliente.

SQL para evidencias, sin mostrar hashes:

```sql
SELECT id, cliente_id, correo, activo, intentos_fallidos, version_token
FROM usuarios ORDER BY id;

SELECT cliente_id, numero_cuenta, estatus, saldo FROM cuentas ORDER BY id;

SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

## Pruebas automatizadas y evidencia

```powershell
.\gradlew.bat test --tests com.proyecto.servicios.RegistroClienteIntegrationTest --tests com.proyecto.servicios.config.LoggingSecurityTest
```

Resultado del 8 de octubre de 2026: **25 pruebas aprobadas** (21 de registro/auth y 4 de logs), BUILD SUCCESSFUL. Incluyen concurrencia, persistencia de tres fallos, reinicio del contador, revocación tras bloqueo/reactivación, cuenta inactiva independiente, tokens vencidos/alterados/ausentes y 403.

Se usa H2 modo PostgreSQL, un usuario temporal y una clave fija exclusivamente de tests. La comprobación del usuario real fue sólo una consulta de lectura. La colección Bruno fue preparada; la ejecución manual de login con estos cambios y la aplicación de V6 en PostgreSQL siguen pendientes.

La suite completa de este cambio también pasó: **32 pruebas ejecutadas, 32 aprobadas**, BUILD SUCCESSFUL in 1m 25s. Se actualizaron los tests antiguos de GestoPago para configurar el mapper y esperar 502 ante catálogo vacío. La corrección posterior del 500 por productos duplicados se documenta por separado en [Sincronización del catálogo GestoPago](sincronizacion-gestopago.md), junto con la validación actual de 40 pruebas.

Referencias: [Spring Security JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html), [bloqueo de repositorios Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/3.5-SNAPSHOT/jpa/locking.html).
