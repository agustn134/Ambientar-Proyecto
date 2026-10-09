-- AMBIENTAR-PROYECTO | CREACIÓN COMPLETA DE BASE DE DATOS
-- PostgreSQL 17. Cliente psql. Una ejecución; no requiere ejecutar V1–V8 por separado.
-- Fuente: esquema e historial reales de la instalación QA verificada el 09/10/2026.
-- Incluye estructura, índices, restricciones, catálogos personales e historial Flyway.
-- No incluye clientes, usuarios, contraseñas, tokens ni el archivo postal nacional.
-- Ejemplo: psql -X -U postgres -d postgres -v base_datos=revision_hasani -f scripts/crear-base-datos.sql
-- Opcional: -v propietario=usuario_existente. El ejecutor debe poder crear bases y SET ROLE.
-- Si la base existe, se detiene antes de cambiar datos. No contiene DROP ni TRUNCATE.
\set ON_ERROR_STOP on
\if :{?base_datos}
\else
  \set base_datos revision_hasani
\endif
\if :{?propietario}
\else
  SELECT current_user AS propietario \gset
\endif
SELECT :'base_datos' ~ '^[A-Za-z_][A-Za-z0-9_]{0,62}$'
   AND :'propietario' ~ '^[A-Za-z_][A-Za-z0-9_]{0,62}$' AS nombres_validos \gset
\if :nombres_validos
\else
  \echo 'ERROR: utiliza nombres de base/propietario de 1–63 caracteres, con letras, números y guion bajo.'
  DO $$ BEGIN RAISE EXCEPTION 'Instalación detenida; revisa el diagnóstico anterior.'; END $$;
\endif
SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname=:'base_datos') AS base_existe \gset
\if :base_existe
  \echo 'ERROR: la base ya existe. Se conserva intacta. Elige otro nombre para una instalación nueva.'
  DO $$ BEGIN RAISE EXCEPTION 'Instalación detenida; revisa el diagnóstico anterior.'; END $$;
\endif
SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=:'propietario') AS propietario_existe \gset
\if :propietario_existe
\else
  \echo 'ERROR: el propietario indicado no existe en PostgreSQL.'
  DO $$ BEGIN RAISE EXCEPTION 'Instalación detenida; revisa el diagnóstico anterior.'; END $$;
\endif
SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8'' TEMPLATE template0', :'base_datos', :'propietario') \gexec
\connect :base_datos
BEGIN;
SET ROLE :"propietario";
-- 1. ESTRUCTURA COMPLETA E ÍNDICES
--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA IF NOT EXISTS public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: cat_asentamientos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_asentamientos (
    id integer NOT NULL,
    municipio_id integer NOT NULL,
    codigo_postal character(5) NOT NULL,
    descripcion character varying(100) NOT NULL,
    tipo character varying(50) NOT NULL
);


--
-- Name: cat_estados; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_estados (
    id smallint NOT NULL,
    descripcion character varying(100) NOT NULL
);


--
-- Name: cat_estados_civiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_estados_civiles (
    id smallint NOT NULL,
    codigo character varying(20) NOT NULL,
    descripcion character varying(30) NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: cat_municipios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_municipios (
    id integer NOT NULL,
    estado_id smallint NOT NULL,
    descripcion character varying(100) NOT NULL
);


--
-- Name: cat_nacionalidades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_nacionalidades (
    id smallint NOT NULL,
    descripcion character varying(50) NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: cat_paises; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_paises (
    id smallint NOT NULL,
    codigo character(2) NOT NULL,
    descripcion character varying(50) NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: cat_postal_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_postal_version (
    id smallint NOT NULL,
    sha256 character(64) NOT NULL,
    registros integer NOT NULL,
    cargado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: cat_sexos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cat_sexos (
    id smallint NOT NULL,
    codigo character(1) NOT NULL,
    descripcion character varying(20) NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: clientes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clientes (
    id bigint NOT NULL,
    nombre character varying(50) NOT NULL,
    segundo_nombre character varying(50),
    apellido_paterno character varying(50) NOT NULL,
    apellido_materno character varying(50) NOT NULL,
    fecha_nacimiento date NOT NULL,
    curp character varying(18) NOT NULL,
    rfc character varying(13) NOT NULL,
    correo_electronico character varying(100) NOT NULL,
    telefono_movil character varying(10) NOT NULL,
    telefono_alternativo character varying(10),
    estatus character varying(20) DEFAULT 'ACTIVO'::character varying NOT NULL,
    ocupacion character varying(100) NOT NULL,
    empresa character varying(100) NOT NULL,
    ingreso_mensual numeric(15,2) NOT NULL,
    sexo_id smallint NOT NULL,
    nacionalidad_id smallint NOT NULL,
    estado_civil_id smallint NOT NULL,
    fecha_registro timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: clientes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.clientes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: clientes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.clientes_id_seq OWNED BY public.clientes.id;


--
-- Name: cuentas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cuentas (
    id bigint NOT NULL,
    cliente_id bigint NOT NULL,
    numero_cuenta character varying(20) NOT NULL,
    saldo numeric(15,2) DEFAULT 0.00 NOT NULL,
    estatus character varying(20) DEFAULT 'ACTIVA'::character varying NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: cuentas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cuentas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cuentas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cuentas_id_seq OWNED BY public.cuentas.id;


--
-- Name: domicilios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.domicilios (
    id bigint NOT NULL,
    cliente_id bigint NOT NULL,
    calle character varying(100) NOT NULL,
    numero_exterior character varying(20) NOT NULL,
    numero_interior character varying(20),
    colonia character varying(100) NOT NULL,
    municipio character varying(100) NOT NULL,
    estado character varying(100) NOT NULL,
    codigo_postal character varying(10) NOT NULL,
    pais_id smallint NOT NULL,
    asentamiento_id integer
);


--
-- Name: domicilios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.domicilios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: domicilios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.domicilios_id_seq OWNED BY public.domicilios.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: gestopago_productos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gestopago_productos (
    id integer NOT NULL,
    id_producto integer NOT NULL,
    id_servicio integer,
    servicio character varying(255),
    producto character varying(255),
    id_cat_tipo_servicio integer,
    tipo_front character varying(50),
    has_digito_verificador boolean DEFAULT false,
    precio numeric(12,2),
    show_ayuda boolean DEFAULT false,
    tipo_referencia character varying(50),
    legend text,
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL,
    fecha_actualizacion timestamp without time zone DEFAULT now() NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: gestopago_productos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.gestopago_productos_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: gestopago_productos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.gestopago_productos_id_seq OWNED BY public.gestopago_productos.id;


--
-- Name: gestopago_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gestopago_tokens (
    id integer NOT NULL,
    id_distribuidor integer NOT NULL,
    codigo_dispositivo character varying(100) NOT NULL,
    token text NOT NULL,
    token_type character varying(50),
    expires_in bigint,
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL,
    fecha_actualizacion timestamp without time zone DEFAULT now() NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: gestopago_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.gestopago_tokens_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: gestopago_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.gestopago_tokens_id_seq OWNED BY public.gestopago_tokens.id;


--
-- Name: usuario_cambios_rol; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_cambios_rol (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    rol_anterior character varying(10) NOT NULL,
    rol_nuevo character varying(10) NOT NULL,
    asignado_por character varying(100) NOT NULL,
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: usuario_cambios_rol_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuario_cambios_rol_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuario_cambios_rol_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.usuario_cambios_rol_id_seq OWNED BY public.usuario_cambios_rol.id;


--
-- Name: usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuarios (
    id bigint NOT NULL,
    cliente_id bigint NOT NULL,
    correo character varying(100) NOT NULL,
    password_hash character varying(60) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    intentos_fallidos integer DEFAULT 0 NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version_token bigint DEFAULT 0 NOT NULL,
    rol character varying(10) DEFAULT 'CLIENTE'::character varying NOT NULL,
    CONSTRAINT ck_usuario_correo_normalizado CHECK (((correo)::text = lower(TRIM(BOTH FROM correo)))),
    CONSTRAINT ck_usuario_intentos CHECK ((intentos_fallidos >= 0)),
    CONSTRAINT ck_usuario_rol CHECK (((rol)::text = ANY ((ARRAY['CLIENTE'::character varying, 'EJECUTIVO'::character varying])::text[])))
);


--
-- Name: usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.usuarios_id_seq OWNED BY public.usuarios.id;


--
-- Name: clientes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes ALTER COLUMN id SET DEFAULT nextval('public.clientes_id_seq'::regclass);


--
-- Name: cuentas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas ALTER COLUMN id SET DEFAULT nextval('public.cuentas_id_seq'::regclass);


--
-- Name: domicilios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios ALTER COLUMN id SET DEFAULT nextval('public.domicilios_id_seq'::regclass);


--
-- Name: gestopago_productos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gestopago_productos ALTER COLUMN id SET DEFAULT nextval('public.gestopago_productos_id_seq'::regclass);


--
-- Name: gestopago_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gestopago_tokens ALTER COLUMN id SET DEFAULT nextval('public.gestopago_tokens_id_seq'::regclass);


--
-- Name: usuario_cambios_rol id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_cambios_rol ALTER COLUMN id SET DEFAULT nextval('public.usuario_cambios_rol_id_seq'::regclass);


--
-- Name: usuarios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios ALTER COLUMN id SET DEFAULT nextval('public.usuarios_id_seq'::regclass);


--
-- Name: cat_asentamientos cat_asentamientos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_asentamientos
    ADD CONSTRAINT cat_asentamientos_pkey PRIMARY KEY (id);


--
-- Name: cat_estados_civiles cat_estados_civiles_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_estados_civiles
    ADD CONSTRAINT cat_estados_civiles_codigo_key UNIQUE (codigo);


--
-- Name: cat_estados_civiles cat_estados_civiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_estados_civiles
    ADD CONSTRAINT cat_estados_civiles_pkey PRIMARY KEY (id);


--
-- Name: cat_estados cat_estados_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_estados
    ADD CONSTRAINT cat_estados_pkey PRIMARY KEY (id);


--
-- Name: cat_municipios cat_municipios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_municipios
    ADD CONSTRAINT cat_municipios_pkey PRIMARY KEY (id);


--
-- Name: cat_nacionalidades cat_nacionalidades_descripcion_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_nacionalidades
    ADD CONSTRAINT cat_nacionalidades_descripcion_key UNIQUE (descripcion);


--
-- Name: cat_nacionalidades cat_nacionalidades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_nacionalidades
    ADD CONSTRAINT cat_nacionalidades_pkey PRIMARY KEY (id);


--
-- Name: cat_paises cat_paises_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_paises
    ADD CONSTRAINT cat_paises_codigo_key UNIQUE (codigo);


--
-- Name: cat_paises cat_paises_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_paises
    ADD CONSTRAINT cat_paises_pkey PRIMARY KEY (id);


--
-- Name: cat_postal_version cat_postal_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_postal_version
    ADD CONSTRAINT cat_postal_version_pkey PRIMARY KEY (id);


--
-- Name: cat_sexos cat_sexos_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_sexos
    ADD CONSTRAINT cat_sexos_codigo_key UNIQUE (codigo);


--
-- Name: cat_sexos cat_sexos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_sexos
    ADD CONSTRAINT cat_sexos_pkey PRIMARY KEY (id);


--
-- Name: clientes clientes_correo_electronico_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_correo_electronico_key UNIQUE (correo_electronico);


--
-- Name: clientes clientes_curp_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_curp_key UNIQUE (curp);


--
-- Name: clientes clientes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_pkey PRIMARY KEY (id);


--
-- Name: clientes clientes_rfc_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_rfc_key UNIQUE (rfc);


--
-- Name: cuentas cuentas_numero_cuenta_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT cuentas_numero_cuenta_key UNIQUE (numero_cuenta);


--
-- Name: cuentas cuentas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT cuentas_pkey PRIMARY KEY (id);


--
-- Name: domicilios domicilios_cliente_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT domicilios_cliente_id_key UNIQUE (cliente_id);


--
-- Name: domicilios domicilios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT domicilios_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: gestopago_productos gestopago_productos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gestopago_productos
    ADD CONSTRAINT gestopago_productos_pkey PRIMARY KEY (id);


--
-- Name: gestopago_tokens gestopago_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gestopago_tokens
    ADD CONSTRAINT gestopago_tokens_pkey PRIMARY KEY (id);


--
-- Name: gestopago_productos uq_gestopago_productos_id_producto; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gestopago_productos
    ADD CONSTRAINT uq_gestopago_productos_id_producto UNIQUE (id_producto);


--
-- Name: gestopago_tokens uq_gestopago_tokens; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gestopago_tokens
    ADD CONSTRAINT uq_gestopago_tokens UNIQUE (id_distribuidor, codigo_dispositivo);


--
-- Name: usuario_cambios_rol usuario_cambios_rol_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_cambios_rol
    ADD CONSTRAINT usuario_cambios_rol_pkey PRIMARY KEY (id);


--
-- Name: usuarios usuarios_cliente_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_cliente_id_key UNIQUE (cliente_id);


--
-- Name: usuarios usuarios_correo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_correo_key UNIQUE (correo);


--
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_asentamiento_cp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asentamiento_cp ON public.cat_asentamientos USING btree (codigo_postal);


--
-- Name: idx_asentamiento_municipio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asentamiento_municipio ON public.cat_asentamientos USING btree (municipio_id);


--
-- Name: idx_cliente_estatus; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cliente_estatus ON public.clientes USING btree (estatus, id);


--
-- Name: idx_cliente_fecha_registro; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cliente_fecha_registro ON public.clientes USING btree (fecha_registro, id);


--
-- Name: idx_clientes_correo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clientes_correo ON public.clientes USING btree (correo_electronico);


--
-- Name: idx_clientes_curp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clientes_curp ON public.clientes USING btree (curp);


--
-- Name: idx_clientes_rfc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_clientes_rfc ON public.clientes USING btree (rfc);


--
-- Name: idx_cuentas_cliente_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cuentas_cliente_id ON public.cuentas USING btree (cliente_id);


--
-- Name: idx_cuentas_numero_cuenta; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cuentas_numero_cuenta ON public.cuentas USING btree (numero_cuenta);


--
-- Name: idx_gestopago_productos_id_servicio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gestopago_productos_id_servicio ON public.gestopago_productos USING btree (id_servicio);


--
-- Name: idx_gestopago_productos_servicio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gestopago_productos_servicio ON public.gestopago_productos USING btree (servicio);


--
-- Name: idx_municipio_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_municipio_estado ON public.cat_municipios USING btree (estado_id);


--
-- Name: uq_clientes_correo_ignore_case; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_clientes_correo_ignore_case ON public.clientes USING btree (lower((correo_electronico)::text));


--
-- Name: cat_asentamientos cat_asentamientos_municipio_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_asentamientos
    ADD CONSTRAINT cat_asentamientos_municipio_id_fkey FOREIGN KEY (municipio_id) REFERENCES public.cat_municipios(id);


--
-- Name: cat_municipios cat_municipios_estado_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cat_municipios
    ADD CONSTRAINT cat_municipios_estado_id_fkey FOREIGN KEY (estado_id) REFERENCES public.cat_estados(id);


--
-- Name: domicilios domicilios_asentamiento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT domicilios_asentamiento_id_fkey FOREIGN KEY (asentamiento_id) REFERENCES public.cat_asentamientos(id);


--
-- Name: clientes fk_cliente_estado_civil; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT fk_cliente_estado_civil FOREIGN KEY (estado_civil_id) REFERENCES public.cat_estados_civiles(id);


--
-- Name: clientes fk_cliente_nacionalidad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT fk_cliente_nacionalidad FOREIGN KEY (nacionalidad_id) REFERENCES public.cat_nacionalidades(id);


--
-- Name: clientes fk_cliente_sexo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT fk_cliente_sexo FOREIGN KEY (sexo_id) REFERENCES public.cat_sexos(id);


--
-- Name: cuentas fk_cuenta_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuentas
    ADD CONSTRAINT fk_cuenta_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE CASCADE;


--
-- Name: domicilios fk_domicilio_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT fk_domicilio_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id) ON DELETE CASCADE;


--
-- Name: domicilios fk_domicilio_pais; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.domicilios
    ADD CONSTRAINT fk_domicilio_pais FOREIGN KEY (pais_id) REFERENCES public.cat_paises(id);


--
-- Name: usuarios fk_usuario_cliente; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT fk_usuario_cliente FOREIGN KEY (cliente_id) REFERENCES public.clientes(id);


--
-- Name: usuario_cambios_rol usuario_cambios_rol_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_cambios_rol
    ADD CONSTRAINT usuario_cambios_rol_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id);


--
-- PostgreSQL database dump complete
--


-- 2. CATÁLOGOS BÁSICOS E HISTORIAL ORIGINAL DE MIGRACIONES
--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: cat_estados_civiles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.cat_estados_civiles (id, codigo, descripcion, activo) VALUES (1, 'SOLTERO', 'Soltero/a', true);
INSERT INTO public.cat_estados_civiles (id, codigo, descripcion, activo) VALUES (2, 'CASADO', 'Casado/a', true);
INSERT INTO public.cat_estados_civiles (id, codigo, descripcion, activo) VALUES (3, 'DIVORCIADO', 'Divorciado/a', true);
INSERT INTO public.cat_estados_civiles (id, codigo, descripcion, activo) VALUES (4, 'VIUDO', 'Viudo/a', true);
INSERT INTO public.cat_estados_civiles (id, codigo, descripcion, activo) VALUES (5, 'UNION_LIBRE', 'Unión libre', true);
INSERT INTO public.cat_estados_civiles (id, codigo, descripcion, activo) VALUES (6, 'SEPARADO', 'Separado/a', true);
INSERT INTO public.cat_estados_civiles (id, codigo, descripcion, activo) VALUES (7, 'NO_ESPECIFICADO', 'No especificado', true);


--
-- Data for Name: cat_nacionalidades; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.cat_nacionalidades (id, descripcion, activo) VALUES (1, 'Mexicana', true);


--
-- Data for Name: cat_paises; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.cat_paises (id, codigo, descripcion, activo) VALUES (1, 'MX', 'México', true);


--
-- Data for Name: cat_sexos; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.cat_sexos (id, codigo, descripcion, activo) VALUES (1, 'H', 'Masculino', true);
INSERT INTO public.cat_sexos (id, codigo, descripcion, activo) VALUES (2, 'M', 'Femenino', true);


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (1, '1', 'create gestopago tokens', 'SQL', 'V1__create_gestopago_tokens.sql', 1861448253, 'qa_2026100911580943d48d', '2026-10-09 11:58:46.889537', 71, true);
INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (2, '2', 'create gestopago productos', 'SQL', 'V2__create_gestopago_productos.sql', 1600801127, 'qa_2026100911580943d48d', '2026-10-09 11:58:47.091899', 86, true);
INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (3, '3', 'create clientes domicilios cuentas', 'SQL', 'V3__create_clientes_domicilios_cuentas.sql', 895515499, 'qa_2026100911580943d48d', '2026-10-09 11:58:47.286118', 131, true);
INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (4, '4', 'unique correo clientes ignore case', 'SQL', 'V4__unique_correo_clientes_ignore_case.sql', 379645690, 'qa_2026100911580943d48d', '2026-10-09 11:58:47.496598', 25, true);
INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (5, '5', 'create usuarios', 'SQL', 'V5__create_usuarios.sql', 1690143136, 'qa_2026100911580943d48d', '2026-10-09 11:58:47.567453', 41, true);
INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (6, '6', 'version token usuarios', 'SQL', 'V6__version_token_usuarios.sql', -140642946, 'qa_2026100911580943d48d', '2026-10-09 11:58:47.649532', 20, true);
INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (7, '7', 'catalogos mexico', 'SQL', 'V7__catalogos_mexico.sql', 499011389, 'qa_2026100911580943d48d', '2026-10-09 11:58:47.706953', 284, true);
INSERT INTO public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (8, '8', 'roles y fecha registro', 'SQL', 'V8__roles_y_fecha_registro.sql', -1093282346, 'qa_2026100911580943d48d', '2026-10-09 11:58:48.050208', 75, true);


--
-- PostgreSQL database dump complete
--


-- 3. COMPROBACIÓN DE LA INSTALACIÓN
DO $$
BEGIN
  IF (SELECT COUNT(*) FROM public.flyway_schema_history WHERE success AND version IN ('1','2','3','4','5','6','7','8')) <> 8 THEN
    RAISE EXCEPTION 'El historial V1–V8 está incompleto';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history') <> 15 THEN
    RAISE EXCEPTION 'Se esperaban 15 tablas de aplicación';
  END IF;
  IF (SELECT COUNT(*) FROM public.clientes) <> 0 OR (SELECT COUNT(*) FROM public.usuarios) <> 0 OR (SELECT COUNT(*) FROM public.cuentas) <> 0 THEN
    RAISE EXCEPTION 'La instalación debe iniciar sin personas ni cuentas';
  END IF;
END $$;
COMMIT;
RESET ROLE;
SELECT current_database() AS base_creada,
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history') AS tablas_aplicacion,
  (SELECT COUNT(*) FROM public.flyway_schema_history WHERE success) AS versiones_registradas;
\echo 'Esquema listo. Los iniciadores Windows/Linux cargan a continuación el ZIP postal incluido; sigue el README.'
