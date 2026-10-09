# Modelo de datos: onboarding y catálogo GestoPago

Modelo físico de PostgreSQL correspondiente a las migraciones V1–V8. La imagen fue proporcionada por Agustín después de combinar el PR #13 en `develop`; se contrastaron los nombres, campos principales y relaciones visibles con los archivos SQL del repositorio. No se ejecutaron migraciones ni modificaciones de datos durante esta revisión.

![Modelo entidad–relación de la base de datos](diagramas/modelo-er-v8.png)

## Organización del esquema

La captura contiene 16 tablas, incluida la tabla de control que crea Flyway:

| Grupo | Tablas | Propósito |
|---|---|---|
| Clientes y acceso | clientes, domicilios, cuentas, usuarios | Datos personales/contacto/laborales, domicilio, cuenta y credenciales de acceso. |
| Catálogos personales | cat_sexos, cat_nacionalidades, cat_estados_civiles, cat_paises | Referencias numéricas para datos controlados. |
| Catálogo postal | cat_estados, cat_municipios, cat_asentamientos, cat_postal_version | Jerarquía territorial, CP/asentamiento y control de la importación nacional. |
| Auditoría de acceso | usuario_cambios_rol | Historial de asignaciones de CLIENTE/EJECUTIVO realizadas por el procedimiento controlado. |
| Proveedor | gestopago_tokens, gestopago_productos | Autenticación externa y catálogo descargado de GestoPago. |
| Migraciones | flyway_schema_history | Historial de versiones y checksums gestionado por Flyway. |

La información laboral se almacena en `clientes.ocupacion`, `empresa` e `ingreso_mensual`. En Java se agrupa como `@Embedded InformacionLaboral`; no existe otra tabla laboral ni hace falta inventar esa relación en el diagrama.

## Claves y cardinalidades físicas

La cardinalidad describe lo permitido por las FKs, nulabilidad y restricciones UNIQUE del SQL. Una FK en una tabla hija no obliga por sí misma a que cada padre tenga una fila hija.

| Padre → hijo | Cardinalidad | Clave foránea / restricción |
|---|---|---|
| clientes → domicilios | 1 → 0..1 | domicilios.cliente_id NOT NULL, UNIQUE y FK a clientes.id. Cada domicilio pertenece a un cliente. |
| clientes → usuarios | 1 → 0..1 | usuarios.cliente_id NOT NULL, UNIQUE y FK a clientes.id. Un usuario no se comparte entre clientes. |
| clientes → cuentas | 1 → 0..N | cuentas.cliente_id NOT NULL y FK; no es UNIQUE. Un cliente puede tener varias cuentas. |
| usuarios → usuario_cambios_rol | 1 → 0..N | usuario_cambios_rol.usuario_id NOT NULL y FK. |
| cat_sexos → clientes | 1 → 0..N | clientes.sexo_id SMALLINT NOT NULL y FK. |
| cat_nacionalidades → clientes | 1 → 0..N | clientes.nacionalidad_id SMALLINT NOT NULL y FK. |
| cat_estados_civiles → clientes | 1 → 0..N | clientes.estado_civil_id SMALLINT NOT NULL y FK. |
| cat_paises → domicilios | 1 → 0..N | domicilios.pais_id SMALLINT NOT NULL y FK. |
| cat_estados → cat_municipios | 1 → 0..N | cat_municipios.estado_id SMALLINT NOT NULL y FK. |
| cat_municipios → cat_asentamientos | 1 → 0..N | cat_asentamientos.municipio_id INTEGER NOT NULL y FK. |
| cat_asentamientos → domicilios | 1 → 0..N; padre opcional para el domicilio | domicilios.asentamiento_id INTEGER nullable y FK. Cada domicilio tiene 0..1 asentamiento; pueden existir históricos aún sin correspondencia única. |

En el flujo de alta nuevo, la aplicación crea **un cliente, un domicilio, una cuenta ACTIVA y un usuario CLIENTE** dentro de una transacción. Eso es una garantía del servicio de registro, además de las restricciones físicas. Los clientes históricos no reciben usuarios ni contraseñas inventadas. Por eso el modelo físico no debe afirmar que absolutamente todo cliente histórico ya tiene un usuario.

`cat_estados` no tiene FK a `cat_paises`: el archivo postal importado es el catálogo nacional de México. `cat_postal_version` guarda SHA-256, cantidad de registros y fecha de carga; no tiene FKs hacia los asentamientos. No dibujar relaciones que no están en el SQL.

## Tipos e integridad

| Dato | Tipo o restricción física | Regla complementaria de aplicación |
|---|---|---|
| IDs de cliente/domicilio/cuenta/usuario | BIGSERIAL / BIGINT | Referencias positivas; identidad autorizada desde la sesión. |
| CURP | VARCHAR(18), NOT NULL, UNIQUE | Formato, fecha interna y normalización a mayúsculas. |
| RFC | VARCHAR(13), NOT NULL, UNIQUE | Se aceptan 12 o 13 caracteres y se valida su fecha interna. |
| Correo de cliente | VARCHAR(100), NOT NULL, UNIQUE; índice único sobre LOWER(correo_electronico) | Se normaliza a minúsculas y se valida formato. |
| Correo de usuario | VARCHAR(100), NOT NULL, UNIQUE; CHECK de minúsculas/TRIM | Se sincroniza con el correo del cliente al actualizarlo. |
| Número de cuenta | VARCHAR(20), NOT NULL, UNIQUE | Contrato de 20 dígitos; se conserva como texto para mantener los ceros iniciales. |
| Saldo e ingreso | DECIMAL(15,2) | Saldo inicial 0; ingreso positivo y límites de precisión mediante validación. El SQL original no añade un CHECK de positividad. |
| Teléfonos | VARCHAR(10) | Contrato de diez dígitos; no se almacenan como números aritméticos. |
| CP | cat_asentamientos.codigo_postal CHAR(5); domicilios.codigo_postal VARCHAR(10) heredado | El contrato exige exactamente cinco dígitos y correspondencia con el asentamiento seleccionado. |
| Sexo/nacionalidad/civil/país | IDs SMALLINT con FKs | Se permite seleccionar únicamente opciones habilitadas del catálogo. |
| Contraseña | usuarios.password_hash VARCHAR(60), NOT NULL | El servicio guarda BCrypt; los DTO de consulta no exponen contraseña/hash. |
| Rol | VARCHAR(10), NOT NULL, default CLIENTE; CHECK CLIENTE/EJECUTIVO | Registro público CLIENTE y asignación de EJECUTIVO mediante procedimiento auditado. |
| Intentos fallidos | INTEGER, NOT NULL, default 0; CHECK >= 0 | El login bloquea al alcanzar tres intentos incorrectos consecutivos. |
| Versión de token | BIGINT, NOT NULL, default 0 | Cambio de credenciales, rol o desactivación invalida tokens anteriores mediante la versión y el estado vigente. |
| Fecha de registro | TIMESTAMP nullable; default CURRENT_TIMESTAMP para nuevas filas | Los registros históricos sin fecha confirmada permanecen NULL; no se cambia en PUT. |

La base física tiene nombres VARCHAR(50); los DTO actuales limitan el nombre a 3–38 y los apellidos a 2–50 conforme a las aclaraciones del proyecto. La capacidad de una columna no reemplaza la validación del request. El modelo no debe atribuir a PostgreSQL validaciones que están implementadas en Java.

## Domicilio y catálogo postal

Un CP puede tener varios asentamientos. La selección es `asentamientoId` junto con `codigoPostal`, no una colonia arbitraria ni una relación uno-a-uno entre CP y colonia.

El servicio comprueba la referencia y obtiene colonia, municipio y estado del catálogo. `domicilios` conserva esos textos como datos del domicilio, además de la FK al asentamiento. La nulabilidad de esta FK conserva domicilios históricos cuyo vínculo no pudo resolverse de forma única; las altas y actualizaciones nuevas exigen una selección válida.

Opciones actuales: sexo 1 Masculino / 2 Femenino, nacionalidad 1 Mexicana y país 1 México. La tabla de estados civiles incluye una opción No especificado; no se sustituye por texto libre. El rol utiliza CHECK, no un catálogo adicional.

## Baja lógica y conservación

El DELETE de la API mantiene las filas: cliente INACTIVO, todas sus cuentas INACTIVA, usuario activo false y versión de token incrementada. Conserva números de cuenta, saldos y datos históricos. El ejecutivo puede consultar el registro dado de baja; su propio usuario ya no puede acceder.

Las FKs domicilio/cliente y cuenta/cliente contienen ON DELETE CASCADE en V3. Eso describe un borrado físico SQL; **la baja lógica implementada por el servicio no ejecuta ese borrado**. La FK de usuario a cliente y la de auditoría a usuario no tienen ON DELETE CASCADE.

## Catálogo externo y tablas sin relaciones

`gestopago_productos.id` es el ID local; `id_producto` es el identificador del proveedor y tiene UNIQUE. La sincronización usa ese identificador para actualizar o insertar, conservando el ID local y la fecha de creación. `id_servicio` e `id_cat_tipo_servicio` son datos del proveedor; no son FKs hacia los catálogos personales/postales del onboarding.

`gestopago_tokens` conserva el token externo de GestoPago; no contiene los JWT de login de clientes. Tiene UNIQUE(id_distribuidor, codigo_dispositivo). No existe una FK entre esta tabla y los productos: ambas pueden aparecer aisladas en el esquema sin indicar un error de integridad.

`flyway_schema_history` es metadato de infraestructura. Aparece en la vista física completa, pero no necesita incluirse como entidad de negocio en una versión simplificada del diagrama.

## SQL de referencia y alcance

La fuente reproducible son los archivos de [migraciones](../src/main/resources/db/migration): V1/V2 para proveedor, V3/V4 para onboarding/unicidad, V5/V6 para usuario/versiones y V7/V8 para catálogos, roles, auditoría y fecha de registro. No se editaron migraciones aplicadas para producir esta documentación.

Los índices incluyen búsquedas por identificadores/cuenta, relaciones postales, CP y fecha/estatus de cliente. Las columnas y relaciones visibles del esquema recibido corresponden a esas migraciones. Esta revisión documental no sustituye una consulta de los constraints del servidor ni una exportación completa del DDL para la entrega.

La imagen sirve como evidencia del esquema físico. Para presentación, acompañarla de estas cardinalidades, tipos y restricciones, y del SQL versionado; la imagen por sí sola no demuestra las reglas transaccionales ni los permisos de la API.
