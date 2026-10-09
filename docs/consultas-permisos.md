# Consultas, roles y paginación

## Roles y acceso

V8 agrega `usuarios.rol`, restringido a CLIENTE/EJECUTIVO. Los usuarios históricos quedan CLIENTE; el registro público fuerza CLIENTE aunque el JSON incluya otro rol. No existe un endpoint público para asignar EJECUTIVO.

El JWT contiene `rol` y `ver`, y se verifica contra el usuario activo, su cliente activo, rol y versión vigentes en la base. Spring Security convierte el rol validado en una autoridad. Cambiar rol invalida el token anterior incluso si una modificación manual no actualizara la versión. Los tokens anteriores a V8 carecen de rol y se rechazan: iniciar sesión nuevamente tras reiniciar App.

| Operación | CLIENTE | EJECUTIVO |
| --- | --- | --- |
| `/auth/me` y `/clientes/me` | Perfil propio | Perfil propio |
| Cliente por ID o identificador y sus cuentas | Sólo propios; ajeno devuelve 404 | Cualquier cliente |
| Cuenta por número y saldo | Sólo propia; ajena devuelve 404 | Cualquier cuenta |
| Listado general de clientes/cuentas | 403 | Permitido |
| Contraseña/hash de usuario | Nunca se devuelven | Nunca se devuelven |

Sin JWT válido se responde 401. Ajeno e inexistente usan el mismo 404 con `RECURSO_NO_ENCONTRADO`, sin distinguir si existe un dato de otra persona. Los listados sin coincidencias devuelven página vacía con 200. Los parámetros inválidos devuelven 400 `VALIDACION` con campo y mensaje.

La autorización también se comprueba en el servicio, usando el usuario y cliente derivados de la sesión; no se confía en un ID de URL como prueba de pertenencia. Un rol ejecutivo no habilita las rutas antiguas `/personas*` ni asignación de roles a través de la API.

## Preparar el ejecutivo de forma controlada

El procedimiento requiere acceso administrativo directo a PostgreSQL y un usuario CLIENTE activo cuyo cliente esté ACTIVO. No cambia contraseña, saldo ni estado bancario. Bloquea el candidato durante la operación, actualiza rol y `version_token`, y registra anterior/nuevo, fecha y usuario de base de datos en `usuario_cambios_rol`.

En Bruno ejecutar **Consultas → Registro ejecutivo de prueba**. Usa un perfil adicional de Agustín con correo `agustin.ejecutivo@example.com`, CURP `LOPA040905HGTXXX09` y RFC `LOPA040905ZZ1` sintéticos; no representan otra identidad oficial. La contraseña es la misma contraseña sintética de QA. El registro crea CLIENTE y devuelve `usuario.usuarioId`; no le da permisos de ejecutivo.

Con ese ID, ejecutar en PowerShell desde la raíz (reemplazar 123 por el ID real):

```powershell
Get-Content -LiteralPath 'scripts/asignar-ejecutivo.sql' -Raw |
  docker exec -i postgres-dev psql -X -v ON_ERROR_STOP=1 -v usuario_id=123 -U postgres -d DBGestoPago
```

El resultado debe mostrar el ID elegido, rol EJECUTIVO y activo true. No se registra ni imprime la contraseña. Si muestra `INSERT 0 0`, no hubo asignación nueva: comprobar si ya era ejecutivo, está inactivo o el ID no existe. Repetirlo sobre un ejecutivo es una operación sin cambios y no duplica auditoría. Revisar cuidadosamente el ID antes de ejecutar; no usar el usuario principal de Agustín si se quiere conservarlo como CLIENTE para probar denegaciones.

Si PowerShell está en `C:\Windows\System32`, la ruta relativa `scripts/...` no existe allí. El wrapper busca el SQL junto a su propio archivo y puede ejecutarse desde cualquier carpeta:

```powershell
& 'Z:\DOCUMENTOS\PROYECTO HASANI\prueba\scripts\asignar-ejecutivo.ps1' -UsuarioId 4
```

Alternativa sin ejecutar un archivo PowerShell, utilizando la ruta absoluta:

```powershell
Get-Content -LiteralPath 'Z:\DOCUMENTOS\PROYECTO HASANI\prueba\scripts\asignar-ejecutivo.sql' -Raw |
  docker exec -i postgres-dev psql -X -v ON_ERROR_STOP=1 -v usuario_id=4 -U postgres -d DBGestoPago
```

La ejecución solicitada para el perfil de prueba usuario 4/cliente 5 se completó el 8 de octubre de 2026, con `INSERT 0 1` y rol EJECUTIVO activo. Su token CLIENTE anterior quedó revocado. Renovar **Login ejecutivo** y comprobar **Perfil ejecutivo** antes de las consultas generales; no se necesita volver a asignar el rol.

Si el registro de prueba retorna 409 porque ya existe, obtener su ID y cuenta localmente:

```sql
SELECT u.id AS usuario_id, c.id AS cliente_id, a.numero_cuenta, u.rol, u.activo
FROM usuarios u JOIN clientes c ON c.id=u.cliente_id
JOIN cuentas a ON a.cliente_id=c.id
WHERE u.correo='agustin.ejecutivo@example.com';
```

Copiar `cliente_id` y `numero_cuenta` a variables de ejecución/entorno de la colección `ejecutivoClienteId` y `ejecutivoNumeroCuenta` si no quedaron capturadas. El script SQL usa una variable psql citada y convertida a bigint; no se concatena un correo recibido por HTTP.

Los tests automatizados elevaron únicamente usuarios en bases/esquemas aislados. No se asignó EJECUTIVO automáticamente a un usuario real.

## Endpoints y filtros

| Petición | Resultado |
| --- | --- |
| `GET /clientes/me` | Detalle del cliente asociado a la sesión |
| `GET /clientes/{id}` | Detalle autorizado o 404 |
| `GET /clientes/{id}/cuentas` | Cuentas del cliente autorizado |
| `GET /clientes/buscar?curp=...` | Un detalle; admite también RFC, correo o número de cuenta |
| `GET /clientes` | Página general; sólo EJECUTIVO |
| `GET /cuentas/{numeroCuenta}` | Cuenta autorizada o 404 |
| `GET /cuentas/{numeroCuenta}/saldo` | Número y saldo, con la misma autorización |
| `GET /cuentas` | Página general; sólo EJECUTIVO |

`GET /clientes` admite `curp`, `rfc`, `correo`, `numeroCuenta`, `nombre` (fragmento), `activo` (true/false), `desde` y `hasta`. Los filtros se combinan con AND, no OR. CURP/RFC se normalizan a mayúsculas y el correo se compara sin distinguir mayúsculas. Buscar por número de cuenta utiliza EXISTS para no duplicar al cliente que tenga varias cuentas.

`GET /clientes/buscar` requiere al menos un identificador único (CURP/RFC/correo/número de cuenta). Para CLIENTE se agrega siempre su propio ID como condición; para EJECUTIVO se busca de forma general. Sin coincidencia devuelve 404.

`GET /cuentas` admite `clienteId` y `activo`. El estado activo se compara con ACTIVA/INACTIVA en cuentas y ACTIVO/INACTIVO en clientes. Una cuenta inactiva puede consultarse por su propietario autenticado; su estado no equivale al bloqueo del usuario.

Los números de cuenta actuales del contrato tienen 20 dígitos y se tratan como texto. IDs positivos; formato incorrecto de parámetros también devuelve 400.

## Paginación y fechas

Parámetros de listados: `page` desde 0, `size` de 1 a 100 (predeterminado 20), `ordenarPor` y `direccion=ASC|DESC`. El máximo de página es un límite técnico de respuesta, no una nueva regla de negocio de registro.

Orden de clientes: `id`, `nombre`, `fechaRegistro`. Orden de cuentas: `id`, `fechaCreacion`, `numeroCuenta`. Cualquier otro campo se rechaza; se agrega ID como desempate cuando sea necesario. No se acepta un nombre de columna arbitrario ni se concatena SQL del usuario.

```json
{"contenido":[],"page":0,"size":20,"totalElementos":0,"totalPaginas":0}
```

`desde`/`hasta` usan fechas reales YYYY-MM-DD. Se incluyen ambos días completos: desde su inicio y antes del inicio del día posterior a hasta. Desde posterior a hasta devuelve 400. Una página fuera del resultado devuelve contenido vacío, conservando sus totales.

V8 agrega `clientes.fecha_registro`. Los registros nuevos obtienen la fecha de creación en el servidor. No se inventa una fecha para los clientes históricos ni se sustituye por la fecha de su cuenta: permanecen NULL y aparecen en listados sin rango, pero se excluyen de filtros de fecha. La respuesta indica `fechaRegistro=null` para esos registros. Los timestamps son locales del servidor y no incluyen zona en la columna; no son fechas oficiales de alta anteriores a V8.

El listado devuelve un resumen explícito y el detalle añade nacimiento, IDs de catálogos, contacto, domicilio, información laboral y cuentas. No se serializa el grafo JPA ni la entidad Usuario.

## Pruebas Bruno

Recargar la colección organizada en Bruno y mantener App ejecutándose. En la barra lateral **Autenticacion y seguridad** corresponde a `Auth` y **Consultas y paginacion** corresponde a `Consultas`. Seleccionar Local o el entorno habitual con `baseUrl`.

Esta secuencia usa al cliente principal de Agustín y al ejecutivo ya existente (usuario 4 / cliente 5). **No ejecutar Registro ejecutivo de prueba ni repetir el SQL de asignación.** El perfil de mantenimiento cliente 6 / usuario 5 ya está inactivo; sólo se consultará con token ejecutivo. No se actualizan datos ni se dan nuevas bajas en este bloque.

### Preparación

| Orden | Ruta | HTTP / variable |
|---|---|---|
| 1 | Auth/Acceso/Login valido | 200; captura `token` del cliente principal. |
| 2 | Consultas/Datos propios/Mi cliente | 200; captura `clienteId` y `numeroCuenta`. |
| 3 | Consultas/Preparacion del ejecutivo/Login ejecutivo | 200; captura `tokenEjecutivo` y vencimiento. |
| 4 | Consultas/Preparacion del ejecutivo/Perfil ejecutivo | 200 y rol EJECUTIVO. |
| 5 | Consultas/Preparacion del ejecutivo/Ficha del ejecutivo | 200; captura `ejecutivoClienteId`, `ejecutivoNumeroCuenta` y `ejecutivoCurp`, sin repetir el alta. |

### Cliente: datos propios y permisos

Antes de este grupo renovar **Auth/Acceso/Login valido** si el JWT venció. Enviar:

| Subcarpeta de Consultas | Peticiones, en orden | HTTP esperado |
|---|---|---|
| Datos propios | Cliente propio por ID; Mi cuenta; Mi saldo; Mis cuentas | 200 en cada una; sólo datos/cuentas propios y saldo numérico. |
| Permisos y errores | Cliente ajeno denegado; Cuenta ajena denegada; Cliente inexistente | 404 en cada una, con RECURSO_NO_ENCONTRADO. Los dos primeros apuntan al ejecutivo existente. |
| Permisos y errores | Listado denegado a cliente; Listado cuentas denegado a cliente | 403 en ambos, con ACCESO_DENEGADO. |
| Permisos y errores | Consulta sin token | 401 NO_AUTENTICADO. Se omite el Bearer deliberadamente. |

Un 401 en una prueba que espera 200/403/404 no sirve para comprobar permisos: renovar **Login valido** y repetir la prueba pendiente. El 404 de un dato ajeno es intencional para no revelar su existencia.

### Ejecutivo: paginación

Renovar **Consultas/Preparacion del ejecutivo/Login ejecutivo** y verificar **Perfil ejecutivo** antes de este grupo. En **Listados y filtros**, enviar estas tres peticiones seguidas, sin registrar o modificar clientes entre ellas:

| Petición | HTTP | Comprobación |
|---|---|---|
| Listado paginado ejecutivo | 200 | page 0, size 2, orden ID ascendente, longitud y totalPaginas coherentes. Guarda IDs y totales. |
| Siguiente pagina ejecutivo | 200 | page 1, mismos totales, IDs distintos de la primera página y orden estable. |
| Pagina fuera del resultado | 200 | contenido vacío, conservando totales. La página se calcula a partir de totalPaginas; no utiliza un conteo fijo. |

Las tres deben tener cero pruebas fallidas. Si vence el JWT entre ellas, renovar Login ejecutivo y volver a enviar las tres. No introducir altas de Sofía/Nancy ni otros cambios entre la primera y segunda página.

### Ejecutivo: filtros y búsquedas

En **Consultas/Listados y filtros**, continuar en este orden:

| Petición | HTTP | Comprobación |
|---|---|---|
| Filtro por CURP | 200 | Un cliente; ID del cliente principal. |
| Buscar por RFC | 200 | Detalle del cliente principal. |
| Buscar por cuenta | 200 | Detalle del dueño de `numeroCuenta`. |
| Clientes por fechas | 200 | Página estructurada para el rango 2026; sólo registros con fecha conocida en ese rango. |
| Cuentas activas | 200 | Todas las cuentas devueltas ACTIVA. |
| Filtros combinados | 200 | Correo principal y activo true: un resultado. |
| Filtros incompatibles | 200 | Correo principal y CURP del ejecutivo: cero resultados. Comprueba AND, pues ambos identificadores existen en clientes distintos. |
| Clientes activos | 200 | Todos los elementos ACTIVO. |
| Clientes inactivos | 200 | Todos INACTIVO y al menos un resultado tras la baja QA confirmada. |
| Cliente inactivo por correo | 200 | Ejecutivo encuentra `dulce.mantenimiento@example.com`, INACTIVO, conservando cuentas INACTIVA. |

Renovar Login ejecutivo antes de este grupo si ya pasaron cinco minutos. Un listado vacío válido devuelve 200, no 404. La búsqueda individual inexistente sí devuelve 404. Las fechas históricas NULL se excluyen del filtro por fechas: no inventar ni rellenar sus fechas para hacer pasar una prueba.

### Ejecutivo: errores controlados

En **Consultas/Permisos y errores**, usando JWT ejecutivo vigente:

| Petición | HTTP / código esperado |
|---|---|
| Busqueda inexistente ejecutivo | 404 RECURSO_NO_ENCONTRADO. |
| Pagina invalida | 400 VALIDACION; size 101 supera el máximo 100. |
| Fecha invalida | 400 VALIDACION; 2026-02-30 no existe. |
| Rango de fechas invertido | 400 VALIDACION; desde es posterior a hasta. |

Los 400/401/403/404 de estos casos son resultados esperados y sus Tests deben aprobarse. Para un fallo, conservar ruta, HTTP real, pestaña Tests y body de error; no pegar el JWT en la evidencia. Guardar especialmente las tres páginas, filtros incompatibles y denegaciones de cliente.

### Resultados manuales reportados el 8 de octubre de 2026

El usuario aportó las respuestas de preparación, consultas propias, permisos, paginación y filtros y reportó los Tests aprobados. La única comprobación que inicialmente respondió 401 por expiración, Listado cuentas denegado a cliente, se repitió con JWT CLIENTE renovado: 403 ACCESO_DENEGADO y Tests (1), Passed (1), Failed (0). El bloque manual de consultas, permisos, filtros y paginación queda cerrado.

| Comprobación | Evidencia recibida |
|---|---|
| Preparación del cliente y ejecutivo | Cliente principal 3, cuenta terminada en 03; ejecutivo usuario 4 / cliente 5, rol EJECUTIVO, cuenta terminada en 05. |
| Cliente propio, cuenta, saldo y cuentas del cliente | Detalle del cliente 3, cuenta propia ACTIVA, saldo 0.00 y array de cuentas propias. |
| Cliente ajeno, cuenta ajena e inexistente | Tres respuestas 404 RECURSO_NO_ENCONTRADO. |
| Listado general de clientes con CLIENTE | 403 ACCESO_DENEGADO a las 23:14:24. |
| Listado general de cuentas con CLIENTE | Repetición correcta: 403 ACCESO_DENEGADO a las 23:28:26.1750782, un Test aprobado y cero fallos. |
| Consulta sin token | 401 NO_AUTENTICADO, correcto. |
| Primera página | page 0, size 2, IDs 1 y 2; totalElementos 7 y totalPaginas 4. |
| Segunda página | page 1, size 2, IDs 3 y 4; mismos totales, sin repetir IDs. |
| Página fuera del resultado | page 4, contenido vacío y mismos totales 7/4. |
| CURP y búsqueda de identificador | Cliente principal 3; el filtro por CURP devuelve un resultado y la búsqueda devuelve su detalle. |
| Clientes activos | IDs 1, 2, 3, 4, 5 y 7: seis ACTIVO; el cliente 6 dado de baja no aparece. |
| Filtros incompatibles | 200 vacío, totalElementos 0 y totalPaginas 0; compatible con AND. |
| Búsqueda inexistente del ejecutivo | 404 RECURSO_NO_ENCONTRADO. |
| Página inválida | 400 VALIDACION, campo size, máximo 100. |
| Fecha inválida y rango invertido | Dos respuestas 400 VALIDACION, campo desde, mensajes correspondientes. |

El JWT del cliente principal vencía a las 2026-10-09T05:14:39.654808600Z, es decir, 23:14:39 del 8 de octubre en México. La consulta `/cuentas` se envió a las 23:14:55.0693104: el rechazo por expiración ocurrió antes de evaluar el permiso de listar. La aserción de esa petición exige 403 y ACCESO_DENEGADO; el body recibido no puede acreditarse como prueba de autorización aprobada.

La repetición se hizo inmediatamente después de **Auth/Acceso/Login valido**. El nuevo JWT vencía a las 2026-10-09T05:33:09.715222500Z (23:33:09 de México); la petición `/cuentas` respondió 403 ACCESO_DENEGADO a las 23:28:26 con token aún vigente. Esta evidencia confirma la denegación por permiso y sustituye el intento anterior vencido. Renovar Login ejecutivo no renueva `token`, que es el Bearer utilizado por esta comprobación.

Los valores fechaRegistro NULL de los clientes históricos siguen siendo correctos. No se modificaron datos ni fechas para validar las páginas. El usuario reportó aprobadas las demás peticiones de Listados y filtros; no se adjuntó un body individual identificado para todas ellas.

### Perfiles permanentes adicionales

La nueva Dulce (cliente 7 / usuario 6) puede comprobarse mediante **Auth/Acceso de perfiles QA/Login Dulce**, **Perfil Dulce** y **Consultas/Datos propios de perfiles QA/Ficha Dulce**, todos 200. Es una comprobación complementaria; no sustituye al cliente principal en los casos que filtran su CURP/RFC/correo. Sofía y Nancy requieren completar primero su alta una sola vez, fuera de la comparación entre páginas.

## Pruebas Java y alcance

Validación inicial ejecutada el 8 de octubre de 2026: **60 pruebas Java aprobadas**, sin fallos ni omisiones, BUILD SUCCESSFUL. Incluye 34 pruebas HTTP de registro/autenticación/consultas, 3 de V8 y procedimiento de roles en PostgreSQL, y las pruebas previas de catálogos, GestoPago y logs. El procedimiento se probó en un esquema aislado y posteriormente se ejecutó la asignación solicitada del usuario 4 en la base de desarrollo. V7 conservó checksum `499011389`. En esa etapa se verificaron archivos de 61 peticiones Bruno; la colección actual tiene 105 después de mantenimiento, perfiles y casos adicionales de consultas. Los resultados manuales recibidos y el cierre del bloque se registran en la sección anterior.

Las pruebas de integración HTTP cubren registro sin escalamiento, cliente propio/ajeno, ejecutivo, IDs, CURP/RFC/correo/cuenta, filtros AND, página estable, fechas inclusivas, activos, varias cuentas, saldo, 400/401/403/404 y revocación por cambio de rol. Las pruebas PostgreSQL verifican V8 y ejecutan el mismo script de asignación con usuario sintético, auditando una sola vez y rechazando inactivos/inexistentes. No modifican `public`.

Actualización, baja lógica y usuario/contraseña se implementaron posteriormente; consultar [Mantenimiento de clientes y credenciales](actualizacion-baja-password.md) y su carpeta Bruno antes de ejecutarlos.
