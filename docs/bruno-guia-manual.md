# Guía de Bruno: perfiles y pruebas manuales

Abrir la colección de `tests/bruno`, **GestoPago - Clientes, seguridad y catalogos**, y seleccionar **Local** con `baseUrl=http://localhost:8080`. Las peticiones están ordenadas mediante `seq`; los nombres de archivo no llevan números. La pestaña **Docs** de cada carpeta y petición explica propósito, autenticación, resultado HTTP y variables capturadas.

No ejecutar la colección completa como una única secuencia: contiene altas que se realizan una vez, bloqueo por intentos y una baja al final. Ejecutar el flujo correspondiente al estado actual del perfil. Mover las peticiones no modifica PostgreSQL.

## Categorías

| Categoría | Subcarpetas | Uso |
|---|---|---|
| Catalogos de Mexico (`Catalogos`) | Opciones personales, Domicilios, Validaciones | Opciones públicas y rechazo de IDs/asentamientos incorrectos. |
| Registro de clientes (`Registro`) | Altas de clientes, Validaciones, Duplicados | Altas de Agustín, Sofía, Nancy y Dulce; formato y unicidad. |
| Autenticacion y seguridad (`Auth`) | Acceso, Tokens y permisos, Bloqueo por intentos, Acceso de perfiles QA | Login/perfil principal y perfiles nuevos; tokens y bloqueo separado. |
| Consultas y paginacion (`Consultas`) | Preparacion del ejecutivo, Datos propios, Listados y filtros, Permisos y errores, Datos propios de perfiles QA | CLIENTE consulta lo propio; EJECUTIVO consulta listados y filtros. |
| Mantenimiento de clientes (`Mantenimiento`) | Preparacion del perfil QA, Actualizacion de datos, Contrasena, Baja logica | Perfil independiente de Dulce para actualización, contraseña y baja. |
| Catalogo del proveedor GestoPago (`GestoPago`) | Consulta del catalogo, Sincronizacion forzada | Catálogo guardado y secuencia antes → forzar → después. |

El registro público crea CLIENTE. **Registro ejecutivo de prueba** tampoco asigna EJECUTIVO: el procedimiento se documenta en [consultas y permisos](consultas-permisos.md). El ejecutivo existente ya está preparado; no registrarlo otra vez.

## Perfiles permanentes nuevos

| Perfil | Datos personales y domicilio | Correo propuesto de QA | Datos auxiliares |
|---|---|---|---|
| Sofía Jimena Salazar Godínez | CURP `SAGS050618MGTLDFA4`, teléfono `4181788464`, Av. de los Héroes, Héroes de San Cristóbal, CP `37803`, Dolores Hidalgo Cuna de la Independencia Nacional, Guanajuato. Asentamiento `110140023`. | `sofia.jimena.salazar.godinez.qa@gmail.com` | Nacimiento `2005-06-18` inferido de la CURP; exterior `S/N` provisional; RFC `SAGS050618ZZ4`, empresa e ingreso 12000.00 de QA. Área proporcionada: Licenciatura en Turismo. |
| Nancy Guadalupe Enríquez Parra | CURP `EIPN980227MDFNRN03`, nacimiento **1998-02-27**, teléfono `4681205803`, Nopaltzin 106, Banda de Arriba, CP `37903`, San Luis de la Paz, Guanajuato. Asentamiento `110333883`. | `nancy.guadalupe.enriquez.parra.qa@hotmail.com` | RFC `EIPN980227ZZ3`, empresa e ingreso 14000.00 de QA. Área proporcionada: Ingeniería Industrial. |
| Dulce María López Parra | Teléfono confirmado `4681046222`; fecha y domicilio del fixture previo. | `dulce.maria.lopez.parra.qa@gmail.com` | CURP `LOPD051108MGTXXX08`, RFC `LOPD051108ZZ8` e ingreso 18000.50 de QA; perfil permanente independiente del mantenimiento. |

Los correos son propuestas sintéticas con los dominios solicitados, no direcciones confirmadas. Las peticiones no envían correo. Los RFC cumplen el formato, sin presentarse como oficiales. Las áreas de estudio se usan como texto de ocupación para QA, sin afirmar empleo o salario reales. Para los tres perfiles: sexoId 2, nacionalidadId 1 (Mexicana), paisId 1 (México), estadoCivilId 7 (no especificado de prueba). La entidad de nacimiento DF en la CURP de Nancy no cambia su domicilio actual en Guanajuato.

Sofía: apellidos y CP confirmados; número exterior no proporcionado. `S/N` sirve exclusivamente para preparar el fixture, no confirma que el domicilio carezca de número. Nancy: la fecha confirmada de febrero sustituye la fecha inicialmente indicada de septiembre.

Contraseña inicial sintética: `PruebaCliente2026!`. Fixtures en `src/test/resources/registro-cliente-{sofia,nancy,dulce}-qa.json`.

Ejecutar por perfil:

1. **Catalogos/Domicilios/Codigo postal Sofia** o **Codigo postal Nancy**: 200 y asentamiento esperado.
2. **Registro/Altas de clientes/Registro Sofia**, **Registro Nancy** o **Registro Dulce**: 201 una sola vez. Repetir devuelve 409.
3. **Auth/Acceso de perfiles QA/Login [nombre]** y **Perfil [nombre]**: 200, rol CLIENTE.
4. **Consultas/Datos propios de perfiles QA/Ficha [nombre]**: 200, teléfono y domicilio propios.

Variables separadas: `tokenSofia`, `tokenNancy`, `tokenDulce`; `sofiaClienteId/UsuarioId/Cuenta`, `nancyClienteId/UsuarioId/Cuenta`, `dulceClienteId/UsuarioId/Cuenta`. No sobrescriben `token`, `tokenEjecutivo` ni `tokenMantenimiento`. El agente preparó las peticiones sin enviarlas; posteriormente el usuario aportó Registro Dulce 201 (cliente 7 / usuario 6). Sofía y Nancy aún no tienen evidencia manual de alta.

## Secuencia de mantenimiento ejecutada y cerrada

El 8 de octubre de 2026 se completó esta secuencia sobre `dulce.mantenimiento@example.com`, cliente 6 / usuario 5. **El cliente ahora está INACTIVO y el usuario desactivado: no repetir la secuencia de login, recuperación y actualización con ese perfil.** La tabla se conserva como evidencia del orden ejecutado; un nuevo ciclo completo necesita otro perfil QA con identificadores únicos.

El nuevo perfil permanente de Dulce, `dulce.maria.lopez.parra.qa@gmail.com`, se registró por separado con respuesta 201: cliente 7 / usuario 6. No fue el destinatario de la baja. No se mostraron todavía las altas de Sofía y Nancy.

Secuencia ejecutada:

| Orden | Ruta de la petición | Resultado y propósito |
|---|---|---|
| 1 | Mantenimiento/Contrasena/Login con nueva password | 200; renueva tokenMantenimiento. |
| 2 | Mantenimiento/Preparacion del perfil QA/Recuperar perfil mantenimiento | 200; recupera clienteMantId y usuarioMantId. |
| 3 | Mantenimiento/Preparacion del perfil QA/Recuperar ficha mantenimiento | 200; recupera cuentaMant y consulta el estado actual. |
| 4 | Mantenimiento/Actualizacion de datos/Actualizar datos QA | 200; guarda el teléfono corregido `4681046222` y comprueba conservación de RFC/cuenta. |
| 5 | Mantenimiento/Actualizacion de datos/CURP no editable | 400; **HTTP esperado** y **Campo protegido** aprobados. El rechazo no actualiza datos. |
| 6 | Consultas/Preparacion del ejecutivo/Login ejecutivo y Perfil ejecutivo | 200 en ambos; rol EJECUTIVO. |
| 7 | Mantenimiento/Baja logica/Baja QA por ejecutivo | 204. Renovar login ejecutivo si han pasado 5 minutos. |
| 8 | Mantenimiento/Baja logica/Cliente conserva datos tras baja | 200 con token ejecutivo; cliente INACTIVO y cuentas INACTIVA. |
| 9 | Mantenimiento/Baja logica/Usuario desactivado | 200 con token ejecutivo; activo false. |
| 10 | Mantenimiento/Baja logica/JWT del cliente dado de baja | 401 con el último token de mantenimiento. |
| 11 | Mantenimiento/Baja logica/Login tras baja | 401 con contraseña nueva. |

Las capturas nuevas muestran los resultados esperados de la tabla: CURP rechazada con campo `curp`, DELETE 204, cliente conservado INACTIVO, usuario con activo false y rechazos 401 de token/login. La baja anterior con JWT ejecutivo vencido no se ejecutó; la nueva sí. El JWT de mantenimiento fue rechazado a las 22:49:36, antes de vencer a las 22:49:46: el rechazo tras la baja no se explica por expiración.

**Token anterior revocado**, en Contrasena, requiere `tokenMantAnterior` guardado por el cambio de contraseña. Si sigue disponible, enviarlo para obtener 401; no inventarlo ni sustituirlo por el token nuevo. Si se perdió, probar la revocación en otro perfil QA preparado expresamente para un nuevo ciclo. Un 401 de un token vencido no distingue por sí mismo revocación de expiración; las pruebas Java comprueban revocación con JWT aún vigente.

## Evidencias

El flujo está detallado en [Consultas: preparación, permisos, paginación, filtros y errores](consultas-permisos.md#pruebas-bruno). Usa al cliente principal y al ejecutivo existente; Ficha del ejecutivo recupera su cuenta sin repetir el alta. La colección tiene 105 peticiones. El bloque de consultas/paginación quedó cerrado: Listado cuentas denegado a cliente se repitió a las 23:28:26 con token vigente, respondió 403 ACCESO_DENEGADO y tuvo un Test aprobado y cero fallos. El 401 anterior de las 23:14:55 era expiración y no se utiliza como evidencia de autorización.

CURP corregida y baja con consultas posteriores ya cuentan con evidencia manual aportada por el usuario. Ver [resultados de mantenimiento](actualizacion-baja-password.md). Guardar Tests y respuesta de cada comprobación para la entrega. Las altas de Sofía y Nancy todavía no tienen evidencia manual; se mantienen separadas del cierre de consultas del cliente principal y ejecutivo.

Se conserva `.bru`; las carpetas son la organización nativa documentada por [Bruno](https://docs.usebruno.com/get-started/bruno-basics/create-a-folder).
