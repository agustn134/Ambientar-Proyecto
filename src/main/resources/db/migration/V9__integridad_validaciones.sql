-- Se preservan V1-V8. NOT VALID conserva filas históricas sin corregirlas ni borrarlas;
-- PostgreSQL sí aplica estas restricciones a las nuevas escrituras.
ALTER TABLE clientes ADD CONSTRAINT ck_cliente_contacto CHECK (
    correo_electronico = lower(btrim(correo_electronico))
    AND correo_electronico ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
    AND telefono_movil ~ '^[0-9]{10}$'
    AND (telefono_alternativo IS NULL OR telefono_alternativo ~ '^[0-9]{10}$')
) NOT VALID;
ALTER TABLE clientes ADD CONSTRAINT ck_cliente_datos CHECK (
    length(btrim(nombre)) BETWEEN 3 AND 38
    AND length(btrim(apellido_paterno)) BETWEEN 2 AND 50
    AND length(btrim(apellido_materno)) BETWEEN 2 AND 50
    AND length(btrim(ocupacion)) > 0 AND length(btrim(empresa)) > 0
    AND ingreso_mensual > 0 AND estatus IN ('ACTIVO','INACTIVO')
) NOT VALID;
ALTER TABLE clientes ADD CONSTRAINT ck_cliente_identidad CHECK (
    curp ~ '^[A-Z][AEIOUX][A-Z]{2}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$'
    AND rfc ~ '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$'
) NOT VALID;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuario_contacto CHECK (
    correo ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
) NOT VALID;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuario_hash CHECK (
    password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
) NOT VALID;
ALTER TABLE cuentas ADD CONSTRAINT ck_cuenta_datos CHECK (
    numero_cuenta ~ '^[0-9]{20}$' AND saldo >= 0 AND estatus IN ('ACTIVA','INACTIVA')
) NOT VALID;
ALTER TABLE domicilios ADD CONSTRAINT ck_domicilio_datos CHECK (
    codigo_postal ~ '^[0-9]{5}$' AND length(btrim(calle)) > 0 AND length(btrim(numero_exterior)) > 0
) NOT VALID;

-- La edad depende de la fecha de la escritura: se usa trigger, no un CHECK
-- cuya condición cambiaría con el paso del tiempo.
CREATE FUNCTION validar_nacimiento_cliente() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.fecha_nacimiento > (CURRENT_DATE - INTERVAL '18 years')::date THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='El cliente debe ser mayor de edad';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_cliente_nacimiento BEFORE INSERT OR UPDATE OF fecha_nacimiento
ON clientes FOR EACH ROW EXECUTE FUNCTION validar_nacimiento_cliente();

CREATE FUNCTION proteger_identidad_cliente() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.curp IS DISTINCT FROM OLD.curp
       OR NEW.rfc IS DISTINCT FROM OLD.rfc OR NEW.fecha_registro IS DISTINCT FROM OLD.fecha_registro THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='No se permite modificar la identidad original del cliente';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_cliente_identidad BEFORE UPDATE ON clientes
FOR EACH ROW EXECUTE FUNCTION proteger_identidad_cliente();

CREATE FUNCTION proteger_identidad_usuario() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.cliente_id IS DISTINCT FROM OLD.cliente_id
       OR NEW.fecha_creacion IS DISTINCT FROM OLD.fecha_creacion THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='No se permite modificar la identidad original del usuario';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_usuario_identidad BEFORE UPDATE ON usuarios
FOR EACH ROW EXECUTE FUNCTION proteger_identidad_usuario();

-- No se bloquea el rol: el procedimiento administrativo auditado debe poder
-- asignarlo. El rol, el saldo y las credenciales no se reciben en el alta pública.
