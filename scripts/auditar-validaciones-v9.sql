-- Sólo lectura. Ejecutar sobre la copia de revisión antes de desplegar V9.
SELECT 'clientes_contacto' AS regla, count(*) AS registros_a_revisar FROM clientes
WHERE NOT (correo_electronico=lower(btrim(correo_electronico))
 AND correo_electronico ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
 AND telefono_movil ~ '^[0-9]{10}$'
 AND (telefono_alternativo IS NULL OR telefono_alternativo ~ '^[0-9]{10}$'))
UNION ALL
SELECT 'clientes_datos',count(*) FROM clientes
WHERE NOT (length(btrim(nombre)) BETWEEN 3 AND 38
 AND length(btrim(apellido_paterno)) BETWEEN 2 AND 50
 AND length(btrim(apellido_materno)) BETWEEN 2 AND 50
 AND length(btrim(ocupacion))>0 AND length(btrim(empresa))>0
 AND ingreso_mensual>0 AND estatus IN ('ACTIVO','INACTIVO'))
UNION ALL
SELECT 'clientes_identidad',count(*) FROM clientes
WHERE NOT (curp ~ '^[A-Z][AEIOUX][A-Z]{2}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$'
 AND rfc ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$')
UNION ALL
SELECT 'clientes_edad',count(*) FROM clientes
WHERE fecha_nacimiento > (CURRENT_DATE-INTERVAL '18 years')::date
UNION ALL
SELECT 'usuarios_contacto',count(*) FROM usuarios
WHERE correo !~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
UNION ALL
SELECT 'usuarios_hash',count(*) FROM usuarios
WHERE password_hash !~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
UNION ALL
SELECT 'cuentas_datos',count(*) FROM cuentas
WHERE NOT (numero_cuenta ~ '^[0-9]{20}$' AND saldo>=0 AND estatus IN ('ACTIVA','INACTIVA'))
UNION ALL
SELECT 'domicilios_datos',count(*) FROM domicilios
WHERE NOT (codigo_postal ~ '^[0-9]{5}$' AND length(btrim(calle))>0 AND length(btrim(numero_exterior))>0);

-- Tras aplicar V9: false indica que queda por validar el histórico completo.
SELECT conrelid::regclass AS tabla,conname AS restriccion,convalidated AS historico_validado
FROM pg_constraint WHERE connamespace=current_schema()::regnamespace
 AND conname IN ('ck_cliente_contacto','ck_cliente_datos','ck_cliente_identidad',
 'ck_usuario_contacto','ck_usuario_hash','ck_cuenta_datos','ck_domicilio_datos')
ORDER BY tabla,restriccion;
