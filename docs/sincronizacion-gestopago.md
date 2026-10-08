# Sincronización del catálogo GestoPago

## Problema y solución

La sincronización forzada convertía cada descarga en entidades nuevas sin ID local y ejecutaba `saveAll`. Al repetirla, PostgreSQL rechazaba el INSERT por la restricción única de `id_producto`, aunque los productos ya fueran parte del catálogo. El resultado era un error 500.

El guardado ahora utiliza `INSERT ... ON CONFLICT (id_producto) DO UPDATE`, en un lote JDBC dentro de una transacción con `sfTransactionManager` y `sfDatasource`. PostgreSQL decide de forma atómica si inserta o actualiza, incluso cuando dos descargas coinciden. Referencia: [INSERT y ON CONFLICT en PostgreSQL](https://www.postgresql.org/docs/17/sql-insert.html).

| Situación | Resultado |
| --- | --- |
| Producto nuevo | Se agrega una fila con ID local y fechas de auditoría |
| `idProducto` existente | Se actualizan los campos del proveedor y `fecha_actualizacion`; se conservan ID local y `fecha_creacion` |
| Producto existente inactivo que vuelve en la descarga | Se actualiza y reactiva |
| Producto local ausente en la descarga | Se conserva; esta corrección no lo elimina ni desactiva |
| Catálogo vacío, nulo, con ID ausente, no positivo o repetido dentro de la respuesta | Error 502 antes de guardar |
| Falla al guardar una fila | Se revierte todo el lote y se conserva la caché previa |
| Guardado confirmado | Se invalida `productosCache`; la siguiente consulta lee PostgreSQL y vuelve a llenar Redis |

Repetir una descarga conserva la cantidad de productos cuando el catálogo externo no cambia. La fecha de actualización registra cada sincronización exitosa. Los IDs locales no se renumeran; la secuencia de PostgreSQL puede tener saltos por los intentos de inserción del upsert.

La descarga externa se realiza antes de abrir la transacción de guardado. Los lotes se ordenan por `idProducto` para adquirir las filas en un orden consistente. Se mantiene la restricción única creada en V2; no se modifica una migración aplicada ni se requiere otra migración.

La persistencia compartida se utiliza en la sincronización forzada, la tarea nocturna y la carga inicial cuando no hay productos activos. La ruta `/sincronizar` sigue devolviendo el catálogo desde Redis o PostgreSQL cuando está disponible.

## Peticiones HTTP

Se necesita un JWT local válido en `Authorization: Bearer <token>`, obtenido con `POST /auth/login`. El backend administra por separado el token del proveedor GestoPago.

| Petición | Comportamiento |
| --- | --- |
| `POST /api/gestopago/productos/forzar-sincronizacion` | Descarga externa y guardado; responde 200 con `mensaje` y `productos` de GestoPago |
| `GET /api/gestopago/productos` | Lista local de productos activos, incluyendo ID local y fechas |
| `POST /api/gestopago/productos/sincronizar` | Flujo Redis → PostgreSQL → proveedor |

Ninguna de estas peticiones requiere body. Sin JWT se devuelve 401 antes de ejecutar la integración. Los logs conservan conteos, operación y tipo de error; no imprimen tokens ni cuerpos de respuesta.

## Comprobación en Bruno

Reiniciar el backend con los cambios. Abrir la colección `tests/bruno`, seleccionar el entorno Local y ejecutar `Auth/Login valido`. El JWT dura cinco minutos; renovar el login si vence.

En la carpeta `GestoPago`, ejecutar en este orden:

- **Productos antes de sincronizar:** captura el catálogo actual en `catalogoAntes`.
- **Forzar sincronizacion:** debe responder 200; captura la descarga en `catalogoDescargado`.
- **Productos despues de sincronizar:** verifica productos únicos, conservación de IDs y fechas de creación y actualización de los campos del proveedor.
- Repetir las tres peticiones para comprobar una segunda sincronización sin conflicto.
- **Sincronizacion sin token:** debe responder 401.

Los datos capturados son variables de ejecución locales de la colección. No es necesario declarar manualmente estas variables. Ejecutar el ciclo sin otra sincronización simultánea para comparar con la misma descarga. Los tests Java sí verifican concurrencia de forma aislada.

La comparación de `fechaCreacion` normaliza ambas respuestas a microsegundos, la precisión de [TIMESTAMP en PostgreSQL](https://www.postgresql.org/docs/17/datatype-datetime.html). Una respuesta antigua en Redis puede conservar los nanosegundos del objeto Java previo al guardado; por ejemplo, `.1293513` y `.129351` representan la misma fecha a precisión de base de datos. Se conserva la comprobación del ID y se detectan diferencias de fecha desde un microsegundo.

En DBeaver pueden comprobarse conteos y duplicados sin modificar información:

```sql
SELECT COUNT(*) AS total, COUNT(DISTINCT id_producto) AS productos_unicos
FROM gestopago_productos;

SELECT id_producto, COUNT(*)
FROM gestopago_productos
GROUP BY id_producto
HAVING COUNT(*) > 1;

SELECT id, id_producto, producto, precio, activo, fecha_creacion, fecha_actualizacion
FROM gestopago_productos
ORDER BY id_producto;
```

La segunda consulta debe devolver cero filas. El conteo sólo debe aumentar si hay productos nuevos; no se fija una cantidad porque depende del proveedor.

## Pruebas automatizadas

Validación ejecutada el 8 de octubre de 2026: `gradlew.bat test` terminó con **BUILD SUCCESSFUL**. Pasaron **40 pruebas**, sin fallos ni omisiones: 21 de registro y autenticación, 4 de logs, 9 del servicio GestoPago, 3 del guardado y caché y 3 de PostgreSQL real.

`GestoPagoProductServiceTest` comprueba ambos flujos de guardado y que los datos locales evitan llamar al proveedor. `GestoPagoCatalogoServiceTest` comprueba validación de IDs, orden del lote y limpieza de caché únicamente después del commit.

`GestoPagoCatalogoPostgresTest` prueba el SQL real en un esquema aleatorio `test_catalogo_*`, utilizando V2 y el gestor de transacciones JPA. Comprueba repetición, cambios de precio y nombre, conservación de identidad, reactivación, rollback del lote, caché y dos descargas concurrentes. Elimina su propio esquema al terminar; no toca `public` ni invoca GestoPago. No utiliza H2 para simular `ON CONFLICT`.

`node tests/bruno/verificar-fechas.cjs` verifica la función real de comparación de fechas: pérdida de precisión submicrosegundo, redondeo con cambio de segundo y día, detección de cambios reales y rechazo de formato inválido. No realiza peticiones HTTP.

Para ejecutar las pruebas de PostgreSQL en PowerShell, configurar las credenciales de la base local en variables de entorno:

```powershell
$env:POSTGRES_TEST_URL = 'jdbc:postgresql://localhost:5432/DBGestoPago'
$env:POSTGRES_TEST_USER = 'postgres'
$env:POSTGRES_TEST_PASSWORD = $env:DB_PASSWORD
.\gradlew.bat test --tests '*GestoPago*'
```

`DB_PASSWORD` debe existir en esa terminal; las variables de IntelliJ no se transfieren automáticamente a PowerShell. El usuario de PostgreSQL debe poder crear y eliminar el esquema de prueba. Sin `POSTGRES_TEST_URL`, las pruebas PostgreSQL se omiten explícitamente; las unitarias siguen ejecutándose.

## Evidencia manual en Bruno

El 8 de octubre de 2026 el usuario ejecutó las peticiones en secuencia contra el backend local y compartió capturas. La sincronización forzada llamó al proveedor y se repitió con éxito sobre el catálogo existente.

| Comprobación | Evidencia |
| --- | --- |
| Login | HTTP 200 y JWT recibido |
| Consulta anterior | HTTP 200 y catálogo local |
| Sincronización forzada | HTTP 200; `mensaje.codigo=01`, operación exitosa del proveedor; una ejecución registrada en 7.33 s |
| Consulta posterior final | HTTP 200 en 40 ms; **3 pruebas aprobadas, 0 fallidas** |
| Sin duplicados y conservación | Prueba Bruno aprobada: IDs únicos, conservación de ID local y fecha de creación a precisión de PostgreSQL |
| Campos descargados | Prueba Bruno aprobada: la consulta refleja los campos del catálogo descargado |
| Sincronización sin token | HTTP 401 con `NO_AUTENTICADO` |

La comparación final utiliza segundos y fracción decimal separados, compatible con el analizador de Bruno y con la precisión de microsegundos de PostgreSQL. El script de comprobación local también pasó sus ocho verificaciones. La validación manual de esta corrección queda completada.

## Límites

Esta corrección no cambia las credenciales del proveedor, el cron ni las políticas de autorización. Un fallo del proveedor puede seguir devolviendo su error controlado. Si Redis falla después del commit, el catálogo ya está guardado y la petición puede devolver error al invalidar la caché; no hay una transacción distribuida entre PostgreSQL y Redis. Tampoco se garantiza que una descarga que empezó antes contenga datos más recientes que otra concurrente: prevalece el último guardado.

Las pruebas Java no invocan el proveedor real. La petición manual de sincronización forzada sí fue ejecutada desde Bruno contra el backend y el proveedor; su evidencia se describe arriba.
