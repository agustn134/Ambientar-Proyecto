CREATE TABLE cat_sexos (id SMALLINT PRIMARY KEY, codigo CHAR(1) NOT NULL UNIQUE, descripcion VARCHAR(20) NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE);
INSERT INTO cat_sexos (id,codigo,descripcion) VALUES (1,'H','Masculino'),(2,'M','Femenino');
CREATE TABLE cat_nacionalidades (id SMALLINT PRIMARY KEY, descripcion VARCHAR(50) NOT NULL UNIQUE, activo BOOLEAN NOT NULL DEFAULT TRUE);
INSERT INTO cat_nacionalidades (id,descripcion) VALUES (1,'Mexicana');
CREATE TABLE cat_paises (id SMALLINT PRIMARY KEY, codigo CHAR(2) NOT NULL UNIQUE, descripcion VARCHAR(50) NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE);
INSERT INTO cat_paises (id,codigo,descripcion) VALUES (1,'MX','México');
CREATE TABLE cat_estados_civiles (id SMALLINT PRIMARY KEY, codigo VARCHAR(20) NOT NULL UNIQUE, descripcion VARCHAR(30) NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE);
INSERT INTO cat_estados_civiles (id,codigo,descripcion) VALUES
 (1,'SOLTERO','Soltero/a'),(2,'CASADO','Casado/a'),(3,'DIVORCIADO','Divorciado/a'),
 (4,'VIUDO','Viudo/a'),(5,'UNION_LIBRE','Unión libre'),(6,'SEPARADO','Separado/a'),(7,'NO_ESPECIFICADO','No especificado');

ALTER TABLE clientes ADD COLUMN sexo_id SMALLINT;
ALTER TABLE clientes ADD COLUMN nacionalidad_id SMALLINT;
ALTER TABLE clientes ADD COLUMN estado_civil_id SMALLINT;
UPDATE clientes SET sexo_id = CASE UPPER(TRIM(sexo)) WHEN 'MASCULINO' THEN 1 WHEN 'H' THEN 1 WHEN 'HOMBRE' THEN 1 WHEN 'FEMENINO' THEN 2 WHEN 'M' THEN 2 WHEN 'MUJER' THEN 2 END;
UPDATE clientes SET nacionalidad_id=1 WHERE UPPER(TRIM(nacionalidad)) IN ('MEXICANA','MEXICANO');
UPDATE clientes SET estado_civil_id=(SELECT id FROM cat_estados_civiles WHERE codigo=UPPER(REPLACE(TRIM(clientes.estado_civil),' ','_')));
-- Si hay un valor histórico desconocido, NOT NULL falla y PostgreSQL revierte la migración.
ALTER TABLE clientes ALTER COLUMN sexo_id SET NOT NULL;
ALTER TABLE clientes ALTER COLUMN nacionalidad_id SET NOT NULL;
ALTER TABLE clientes ALTER COLUMN estado_civil_id SET NOT NULL;
ALTER TABLE clientes ADD CONSTRAINT fk_cliente_sexo FOREIGN KEY (sexo_id) REFERENCES cat_sexos(id);
ALTER TABLE clientes ADD CONSTRAINT fk_cliente_nacionalidad FOREIGN KEY (nacionalidad_id) REFERENCES cat_nacionalidades(id);
ALTER TABLE clientes ADD CONSTRAINT fk_cliente_estado_civil FOREIGN KEY (estado_civil_id) REFERENCES cat_estados_civiles(id);
ALTER TABLE clientes DROP COLUMN sexo;
ALTER TABLE clientes DROP COLUMN nacionalidad;
ALTER TABLE clientes DROP COLUMN estado_civil;

ALTER TABLE domicilios ADD COLUMN pais_id SMALLINT;
UPDATE domicilios SET pais_id=1 WHERE UPPER(TRIM(pais)) IN ('MÉXICO','MEXICO','MX');
ALTER TABLE domicilios ALTER COLUMN pais_id SET NOT NULL;
ALTER TABLE domicilios ADD CONSTRAINT fk_domicilio_pais FOREIGN KEY (pais_id) REFERENCES cat_paises(id);
ALTER TABLE domicilios DROP COLUMN pais;

CREATE TABLE cat_estados (id SMALLINT PRIMARY KEY, descripcion VARCHAR(100) NOT NULL);
CREATE TABLE cat_municipios (id INTEGER PRIMARY KEY, estado_id SMALLINT NOT NULL REFERENCES cat_estados(id), descripcion VARCHAR(100) NOT NULL);
CREATE TABLE cat_asentamientos (
 id INTEGER PRIMARY KEY, municipio_id INTEGER NOT NULL REFERENCES cat_municipios(id),
 codigo_postal CHAR(5) NOT NULL, descripcion VARCHAR(100) NOT NULL, tipo VARCHAR(50) NOT NULL
);
CREATE INDEX idx_asentamiento_cp ON cat_asentamientos(codigo_postal);
CREATE INDEX idx_municipio_estado ON cat_municipios(estado_id);
CREATE INDEX idx_asentamiento_municipio ON cat_asentamientos(municipio_id);
CREATE TABLE cat_postal_version (id SMALLINT PRIMARY KEY, sha256 CHAR(64) NOT NULL, registros INTEGER NOT NULL, cargado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
-- Las direcciones históricas se conservan hasta poder vincularlas sin ambigüedad.
ALTER TABLE domicilios ADD COLUMN asentamiento_id INTEGER REFERENCES cat_asentamientos(id);
