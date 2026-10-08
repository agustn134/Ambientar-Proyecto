-- Datos mínimos de prueba; no representan la carga nacional.
INSERT INTO cat_estados (id,descripcion) VALUES (11,'Guanajuato');
INSERT INTO cat_municipios (id,estado_id,descripcion) VALUES (11033,11,'San Luis de la Paz');
INSERT INTO cat_asentamientos (id,municipio_id,codigo_postal,descripcion,tipo)
 VALUES (110333891,11033,'37907','San Isidro','Colonia'),(110330001,11033,'37900','Asentamiento de prueba','Colonia');
