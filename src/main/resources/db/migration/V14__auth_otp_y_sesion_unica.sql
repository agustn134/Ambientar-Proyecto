CREATE TABLE usuario_desafios_otp (
    id UUID PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    otp_hash VARCHAR(60) NOT NULL,
    plataforma VARCHAR(16) NOT NULL CHECK (plataforma IN ('ANDROID', 'WEB')),
    estado VARCHAR(32) NOT NULL CHECK (estado IN ('PENDIENTE', 'CONFLICTO_PENDIENTE', 'CONSUMIDO', 'EXPIRADO', 'BLOQUEADO', 'CANCELADO')),
    intentos_fallidos INTEGER NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    reenvios INTEGER NOT NULL DEFAULT 0 CHECK (reenvios >= 0),
    token_confirmacion_hash VARCHAR(60),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_en TIMESTAMP NOT NULL,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_desafios_otp_usuario_estado ON usuario_desafios_otp(usuario_id, estado);

CREATE TABLE usuario_sesiones_activas (
    usuario_id BIGINT PRIMARY KEY REFERENCES usuarios(id),
    sesion_id UUID NOT NULL UNIQUE,
    plataforma VARCHAR(16) NOT NULL CHECK (plataforma IN ('ANDROID', 'WEB')),
    version_token BIGINT NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_en TIMESTAMP NOT NULL,
    actualizada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sesiones_activas_sesion_id ON usuario_sesiones_activas(sesion_id);
