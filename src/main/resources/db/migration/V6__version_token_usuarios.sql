ALTER TABLE usuarios ADD COLUMN version_token BIGINT NOT NULL DEFAULT 0;
-- Al bloquear/cambiar credenciales se incrementa para revocar tokens anteriores.
