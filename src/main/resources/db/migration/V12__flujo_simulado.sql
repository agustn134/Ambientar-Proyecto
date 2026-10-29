-- Dinero y operaciones ficticios. Ninguna sentencia modifica cuentas.saldo.
CREATE TABLE simulacion_saldos (
    cuenta_id BIGINT PRIMARY KEY REFERENCES cuentas(id),
    contabilizado NUMERIC(15,2) NOT NULL DEFAULT 0,
    reservado NUMERIC(15,2) NOT NULL DEFAULT 0,
    CHECK (reservado >= 0 AND contabilizado >= reservado)
);
CREATE TABLE simulacion_pagos (
    id UUID PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    cuenta_id BIGINT NOT NULL REFERENCES simulacion_saldos(cuenta_id),
    idempotencia VARCHAR(128) NOT NULL,
    huella CHAR(64) NOT NULL,
    monto NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('RESERVED','PROCESSING','UNKNOWN','CAPTURED','FAILED')),
    upc VARCHAR(15) NOT NULL UNIQUE,
    payload_cifrado TEXT NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (cliente_id, idempotencia)
);
CREATE TABLE simulacion_outbox (
    pago_id UUID PRIMARY KEY REFERENCES simulacion_pagos(id),
    estado VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (estado IN ('PENDING','CLAIMED','DONE')),
    vence_en TIMESTAMPTZ
);
CREATE TABLE simulacion_movimientos (
    id BIGSERIAL PRIMARY KEY,
    cuenta_id BIGINT NOT NULL REFERENCES simulacion_saldos(cuenta_id),
    pago_id UUID REFERENCES simulacion_pagos(id),
    tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('FONDEO','RESERVA','CAPTURA','LIBERACION')),
    monto NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (pago_id, tipo)
);
CREATE TABLE simulacion_eventos (
    id BIGSERIAL PRIMARY KEY,
    pago_id UUID NOT NULL REFERENCES simulacion_pagos(id),
    estado VARCHAR(16) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
-- Libro independiente del proveedor ficticio; permite resolver un timeout sin reenviar.
CREATE TABLE simulacion_proveedor (
    pago_id UUID PRIMARY KEY REFERENCES simulacion_pagos(id),
    id_tx BIGSERIAL UNIQUE,
    aplicado BOOLEAN NOT NULL,
    monto NUMERIC(15,2) NOT NULL CHECK (monto > 0)
);
CREATE INDEX idx_simulacion_outbox_estado ON simulacion_outbox(estado, vence_en);
CREATE INDEX idx_simulacion_pagos_cuenta ON simulacion_pagos(cuenta_id);
