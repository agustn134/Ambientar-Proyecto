CREATE TABLE gestopago_token_cache (
    distribuidor INTEGER NOT NULL,
    dispositivo VARCHAR(100) NOT NULL,
    token_cifrado TEXT NOT NULL,
    expira TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (distribuidor, dispositivo)
);
-- Caché derivada, no historial financiero: retirar tokens antiguos almacenados sin cifrar.
UPDATE gestopago_tokens SET token = '[RETIRADO]', activo = FALSE;
