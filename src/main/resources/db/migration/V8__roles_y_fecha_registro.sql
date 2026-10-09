ALTER TABLE usuarios ADD COLUMN rol VARCHAR(10) NOT NULL DEFAULT 'CLIENTE';
ALTER TABLE usuarios ADD CONSTRAINT ck_usuario_rol CHECK (rol IN ('CLIENTE','EJECUTIVO'));
CREATE TABLE usuario_cambios_rol (
 id BIGSERIAL PRIMARY KEY, usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
 rol_anterior VARCHAR(10) NOT NULL, rol_nuevo VARCHAR(10) NOT NULL,
 asignado_por VARCHAR(100) NOT NULL, fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- No se inventa la fecha de alta de clientes históricos: permanecen NULL.
ALTER TABLE clientes ADD COLUMN fecha_registro TIMESTAMP;
ALTER TABLE clientes ALTER COLUMN fecha_registro SET DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX idx_cliente_fecha_registro ON clientes(fecha_registro,id);
CREATE INDEX idx_cliente_estatus ON clientes(estatus,id);
