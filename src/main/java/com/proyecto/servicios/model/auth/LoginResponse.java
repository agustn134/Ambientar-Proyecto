package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.entity.usuario.PlataformaCliente;
import java.time.Instant;
import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt,
    UUID sesionId,
    PlataformaCliente plataforma
) {
    public LoginResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {
        this(accessToken, tokenType, expiresIn, expiresAt, null, null);
    }

    @Override
    public String toString() {
        return "LoginResponse[token protegido]";
    }
}
