-- Ejecutar tras revisar y corregir los datos históricos por un procedimiento autorizado.
-- Si una validación falla, la transacción revierte; no se modifica ningún dato.
BEGIN;
ALTER TABLE clientes VALIDATE CONSTRAINT ck_cliente_contacto;
ALTER TABLE clientes VALIDATE CONSTRAINT ck_cliente_datos;
ALTER TABLE clientes VALIDATE CONSTRAINT ck_cliente_identidad;
ALTER TABLE usuarios VALIDATE CONSTRAINT ck_usuario_contacto;
ALTER TABLE usuarios VALIDATE CONSTRAINT ck_usuario_hash;
ALTER TABLE cuentas VALIDATE CONSTRAINT ck_cuenta_datos;
ALTER TABLE domicilios VALIDATE CONSTRAINT ck_domicilio_datos;
COMMIT;
