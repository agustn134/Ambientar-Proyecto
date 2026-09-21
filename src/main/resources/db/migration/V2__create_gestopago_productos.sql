CREATE TABLE IF NOT EXISTS gestopago_productos (
    id                      SERIAL PRIMARY KEY,
    id_producto             INTEGER         NOT NULL,
    id_servicio             INTEGER,
    servicio                VARCHAR(255),
    producto                VARCHAR(255),
    id_cat_tipo_servicio    INTEGER,
    tipo_front              VARCHAR(50),
    has_digito_verificador  BOOLEAN         DEFAULT FALSE,
    precio                  NUMERIC(12, 2),
    show_ayuda              BOOLEAN         DEFAULT FALSE,
    tipo_referencia         VARCHAR(50),
    legend                  TEXT,
    fecha_creacion          TIMESTAMP       NOT NULL DEFAULT NOW(),
    fecha_actualizacion     TIMESTAMP       NOT NULL DEFAULT NOW(),
    activo                  BOOLEAN         NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_gestopago_productos_id_producto UNIQUE (id_producto)
);

CREATE INDEX IF NOT EXISTS idx_gestopago_productos_id_servicio ON gestopago_productos (id_servicio);
CREATE INDEX IF NOT EXISTS idx_gestopago_productos_servicio ON gestopago_productos (servicio);
