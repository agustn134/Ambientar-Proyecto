-- Sólo con acceso administrativo directo a PostgreSQL. Variable psql usuario_id obligatoria.
BEGIN;
WITH candidato AS (
 SELECT u.id,u.rol FROM usuarios u JOIN clientes c ON c.id=u.cliente_id
 WHERE u.id=:'usuario_id'::bigint AND u.activo=TRUE AND c.estatus='ACTIVO' AND u.rol='CLIENTE'
 FOR UPDATE OF u,c
), cambio AS (
 UPDATE usuarios u SET rol='EJECUTIVO',version_token=version_token+1,fecha_actualizacion=CURRENT_TIMESTAMP
 FROM candidato c WHERE u.id=c.id RETURNING u.id,c.rol AS anterior,u.rol AS nuevo
)
INSERT INTO usuario_cambios_rol(usuario_id,rol_anterior,rol_nuevo,asignado_por)
 SELECT id,anterior,nuevo,CURRENT_USER FROM cambio;
COMMIT;
SELECT id,rol,activo FROM usuarios WHERE id=:'usuario_id'::bigint;
