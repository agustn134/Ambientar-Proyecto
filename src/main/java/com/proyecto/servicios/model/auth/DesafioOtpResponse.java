package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.entity.usuario.PlataformaCliente;
import java.time.Instant;
import java.util.UUID;

public record DesafioOtpResponse(
    UUID desafioId,
    String correoEnmascarado,
    PlataformaCliente plataforma,
    boolean requiereOtp,
    long expiresIn,
    Instant expiresAt,
    int intentosRestantes,
    int reenviosRestantes
) {
    @Override
    public String toString() {
        return "DesafioOtpResponse[desafioId=" + desafioId + ", plataforma=" + plataforma + "]";
    }
}
