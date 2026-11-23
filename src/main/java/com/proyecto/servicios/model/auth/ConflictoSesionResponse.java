package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.entity.usuario.PlataformaCliente;
import java.time.Instant;
import java.util.UUID;

public record ConflictoSesionResponse(
    boolean conflictoSesion,
    UUID desafioId,
    String tokenConfirmacion,
    PlataformaCliente plataformaActiva,
    PlataformaCliente plataformaNueva,
    Instant sesionActivaDesde,
    Instant expiraConfirmacionEn
) {
    @Override
    public String toString() {
        return "ConflictoSesionResponse[desafioId=" + desafioId + ", plataformaActiva=" + plataformaActiva
            + ", plataformaNueva=" + plataformaNueva + "]";
    }
}
