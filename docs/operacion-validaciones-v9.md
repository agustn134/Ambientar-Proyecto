# Operación de V9 y revisión de entrega

## Estado comprobado y límites

- La copia aislada de la base **local** se respaldó y restauró, aplicó V1–V9 y pasó la auditoría de ocho reglas con cero incumplimientos. Se validaron las siete restricciones CHECK en esa copia.
- Las 79 pruebas locales incluyen cuatro pruebas sobre PostgreSQL real y la asignación administrativa de rol con auditoría. Las evidencias están en `docs/evidencias/`.
- El script `scripts/respaldar-postgres.ps1` se probó contra la copia local: el nuevo dump se restauró en otra base aislada y conservó nueve migraciones, ocho reglas sin incumplimientos y siete CHECK validados. [Evidencia del procedimiento](evidencias/respaldo-operativo-local-2026-10-17.json).
- Los checks del PR #19 aprobaron las 64 pruebas de esa versión. El ajuste posterior agrega 15 pruebas; su resultado remoto debe comprobarse en el nuevo PR, sin atribuirle los checks anteriores.
- **No se dispone todavía de un respaldo de Render ni de una auditoría de sus datos.** No se considera completada esa comprobación con los resultados locales.

Si V9 ya se ejecutó en Render, no volver a aplicarla manualmente ni editar su archivo. Crear un respaldo del estado actual y revisar `flyway_schema_history` y las restricciones. Ese respaldo posterior no sustituye uno anterior a la migración.

## Respaldo privado del destino

El propietario del entorno debe registrar el commit desplegado, fecha/hora del respaldo y ventana de mantenimiento. Si existe un responsable DBA/operaciones, debe aprobar la conciliación y la reversión; no se presupone una aprobación inexistente.

Para descargar una copia con el script del proyecto, usar los valores de conexión externa de PostgreSQL de Render. La contraseña se solicita de forma oculta; no pegarla en Classroom, GitHub ni la conversación:

```powershell
./scripts/respaldar-postgres.ps1 -SourceHost 'HOST_EXTERNO' -SourceDatabase 'BASE' -SourceUser 'USUARIO'
```

El script sólo lee el origen mediante `pg_dump`, comprueba el catálogo del archivo con `pg_restore --list` y guarda el dump y su SHA256 en `.local-data/respaldos/`, excluido de Git. El contenedor temporal se elimina al terminar. Comprobar un catálogo no acredita una restauración: completar el paso siguiente. El cliente PostgreSQL debe ser compatible con la versión del servidor; el parámetro `-Image` permite seleccionar la imagen adecuada.

## Restauración y auditoría en staging

1. Crear una base nueva aislada, con credenciales distintas de producción. Restaurar el dump completo con `pg_restore --exit-on-error --no-owner --no-privileges`, apuntando explícitamente a esa base vacía. No usar `--clean` sobre una base compartida ni sustituir producción.
2. Confirmar que la restauración termina sin errores. Registrar sólo fecha, SHA256, versión PostgreSQL y resultado; conservar los datos personales dentro del entorno privado.
3. En esa copia, consultar `flyway_schema_history`. Si está en V8, ejecutar la aplicación de la rama con la conexión de staging y perfil `qa` para que Flyway aplique V9; si V9 ya consta como exitosa, no repetirla. Verificar nueve migraciones exitosas. No ejecutar directamente el SQL de V9 dejando Flyway desincronizado.
4. Ejecutar la auditoría de sólo lectura y guardar el resultado privado. Con un servicio de conexión `staging_validaciones` configurado fuera de Git:

```bash
psql 'service=staging_validaciones' -X -v ON_ERROR_STOP=1 -f scripts/auditar-validaciones-v9.sql
```

5. Si alguna cantidad es mayor que cero, revisar las filas de esa regla dentro del entorno privado. El responsable debe confirmar el dato correcto y preparar una corrección específica y revisable. No inventar CURP/RFC, teléfonos o fechas, eliminar filas ni deshabilitar triggers para conseguir cero incumplimientos. Las columnas protegidas requieren un procedimiento de conciliación aprobado; un UPDATE ordinario puede ser rechazado intencionadamente.
6. Repetir la auditoría después de la conciliación. Con todas las cantidades en cero, validar:

```bash
psql 'service=staging_validaciones' -X -v ON_ERROR_STOP=1 -f scripts/validar-restricciones-v9.sql
psql 'service=staging_validaciones' -X -v ON_ERROR_STOP=1 -f scripts/auditar-validaciones-v9.sql
```

Las siete restricciones deben mostrar `historico_validado = t`. El script valida dentro de una transacción; un fallo revierte la validación y no corrige datos. `ON_ERROR_STOP` hace que el proceso falle ante un error SQL. La regla de mayoría de edad se comprueba aparte en la auditoría porque es un trigger, no un CHECK que pueda marcarse como validado.

7. Ejecutar las seis suites de CI y las pruebas HTTP sobre staging. La auditoría local anterior no garantiza el resultado de esta copia de Render. Antes de validar en producción, repetir el respaldo y la auditoría del destino durante la ventana acordada para contemplar escrituras posteriores al dump. VALIDATE puede consumir recursos y tomar bloqueos; programarlo con el responsable del entorno.

## Administración de roles

`scripts/asignar-ejecutivo.sql` sólo se ejecuta con conexión administrativa y un ID previamente verificado:

```bash
psql 'service=staging_validaciones' -X -v ON_ERROR_STOP=1 -v usuario_id=ID_VERIFICADO -f scripts/asignar-ejecutivo.sql
```

Comprobar una fila en `usuario_cambios_rol`, rol EJECUTIVO, incremento de `version_token`, rechazo del JWT anterior y nuevo login. Si el usuario no existe, está inactivo o ya es EJECUTIVO, el script puede no efectuar cambios; verificar el resultado. V9 no bloquea este procedimiento ni permite asignar roles desde el registro público. No modificar los identificadores, CURP, RFC ni fechas originales protegidos por los triggers.

## Contrato y revisión del PR

`@RequestParam` en `GestoPagoAuthClient` construye una solicitud **saliente** al proveedor. No recibe filtros de los endpoints públicos de esta API. Las restricciones del entregable se verifican en los controladores; los filtros propios siguen llegando mediante Request DTO por body con `@Valid`.

El workflow proporciona `POSTGRES_TEST_URL`, `POSTGRES_TEST_USER` y `POSTGRES_TEST_PASSWORD` a una base efímera. Deben pasar las seis suites, al menos 79 pruebas, cero omisiones y las cuatro pruebas de PostgreSQL. No se requieren credenciales de producción para CI.

El PR debe contener sólo los cambios de aplicación, pruebas, workflow y documentación acordados. No incluir `.idea`, configuraciones del entorno, dumps, contraseñas ni planes temporales. Los archivos locales no incluidos se conservan; no usar `git clean` ni `git add .` para esta entrega. Los archivos IDE ya versionados históricamente no se eliminan del disco ni se reescribe el historial para esta revisión. Revisar `git diff --cached --name-only` antes de confirmar el commit.

## Despliegue y reversión

Antes de promover, registrar responsable, commit exacto, respaldo restaurado con éxito, auditoría del destino y resultado de CI. Coordinar consumidores: las búsquedas ahora usan POST con body y el login devuelve el token dentro de `datos.accessToken`.

Ante un fallo, detener temporalmente las escrituras y conservar un respaldo del estado fallido para investigación. Restaurar el respaldo probado en una base nueva, verificarla y configurar la versión compatible de la aplicación para usarla. Restaurar un respaldo implica perder o conciliar las escrituras posteriores a su fecha; el responsable debe aceptar ese punto de recuperación. Volver sólo al código anterior no revierte los cambios de base ni los triggers. No borrar `flyway_schema_history`, editar V9 aplicada ni suponer que existe `flyway undo`.

La aprobación de DBA, el respaldo de Render y su auditoría se registran cuando realmente existan; no se marcan como completados en la evidencia de la copia local.
