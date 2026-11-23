-- Saldo real: reserva y captura solo dentro de una transacción con bloqueo de cuenta.
ALTER TABLE cuentas ADD COLUMN saldo_reservado NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE cuentas ADD CONSTRAINT cuentas_reserva_valida CHECK (saldo_reservado >= 0 AND saldo >= saldo_reservado);
CREATE TABLE pago_operaciones (
    id UUID PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    cuenta_id BIGINT NOT NULL REFERENCES cuentas(id),
    idempotencia VARCHAR(128) NOT NULL,
    huella CHAR(64) NOT NULL,
    monto NUMERIC(15,2) NOT NULL CHECK(monto > 0),
    estado VARCHAR(16) NOT NULL CHECK(estado IN ('RESERVED','PROCESSING','UNKNOWN','CAPTURED','FAILED')),
    upc VARCHAR(15) NOT NULL UNIQUE,
    id_servicio INTEGER NOT NULL,
    id_producto INTEGER NOT NULL,
    catalogo_version TIMESTAMP NOT NULL,
    payload_cifrado TEXT NOT NULL,
    enviado_en TIMESTAMPTZ,
    envio_recuperado BOOLEAN NOT NULL DEFAULT false,
    revision_manual BOOLEAN NOT NULL DEFAULT false,
    codigo_proveedor VARCHAR(16),
    id_tx VARCHAR(60),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(cliente_id,idempotencia)
);
CREATE TABLE pago_outbox (
    pago_id UUID PRIMARY KEY REFERENCES pago_operaciones(id),
    tarea VARCHAR(16) NOT NULL DEFAULT 'SEND' CHECK(tarea IN ('SEND','CONFIRM','DONE')),
    owner UUID,
    lease_hasta TIMESTAMPTZ,
    siguiente_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    intentos_confirmacion INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE pago_movimientos (
    id BIGSERIAL PRIMARY KEY,
    pago_id UUID NOT NULL REFERENCES pago_operaciones(id),
    cuenta_id BIGINT NOT NULL REFERENCES cuentas(id),
    tipo VARCHAR(16) NOT NULL CHECK(tipo IN ('RESERVA','CAPTURA','LIBERACION')),
    monto NUMERIC(15,2) NOT NULL CHECK(monto > 0),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(pago_id,tipo)
);
-- Evidencia protegida, sin Authorization ni credenciales. No es un log público.
CREATE TABLE pago_eventos (
    id BIGSERIAL PRIMARY KEY,
    pago_id UUID NOT NULL REFERENCES pago_operaciones(id),
    tipo VARCHAR(40) NOT NULL,
    codigo VARCHAR(16),
    evidencia_cifrada TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX pago_outbox_pendientes ON pago_outbox(tarea,siguiente_en);
CREATE INDEX pago_operaciones_cuenta ON pago_operaciones(cuenta_id);
CREATE INDEX pago_operaciones_servicio ON pago_operaciones(id_servicio,creado_en);
