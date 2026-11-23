CREATE TABLE gestopago_catalogo_consultas (
    id BIGSERIAL PRIMARY KEY,
    solicitada_en TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    origen VARCHAR(32) NOT NULL DEFAULT 'CONSULTA'
);
CREATE INDEX ix_gestopago_catalogo_ventana ON gestopago_catalogo_consultas(solicitada_en);
-- Reserva conservadora de un cupo al activar el control: ya hubo una consulta manual
-- antes de contar con este registro. No representa una nueva llamada al proveedor.
INSERT INTO gestopago_catalogo_consultas(origen) VALUES ('RESERVA_INICIAL');
