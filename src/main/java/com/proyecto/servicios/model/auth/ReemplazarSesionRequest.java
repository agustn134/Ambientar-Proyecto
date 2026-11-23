package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.model.cliente.TextoEstricto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReemplazarSesionRequest(
    @NotNull(message = "El identificador del desafío OTP es obligatorio")
    UUID desafioId,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "El token de confirmación es obligatorio")
    @Size(min = 16, max = 128, message = "El token de confirmación no tiene un formato válido")
    @JsonDeserialize(using = TextoEstricto.class)
    String tokenConfirmacion,
    @NotNull(message = "Debes confirmar explícitamente el cierre de la sesión anterior")
    @AssertTrue(message = "Debes confirmar explícitamente el cierre de la sesión anterior")
    Boolean confirmarCierre
) {
    @Override
    public String toString() {
        return "ReemplazarSesionRequest[desafioId=" + desafioId + ", token protegido]";
    }
}
