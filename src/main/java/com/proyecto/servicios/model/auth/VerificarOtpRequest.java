package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.entity.usuario.PlataformaCliente;
import com.proyecto.servicios.model.cliente.TextoEstricto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record VerificarOtpRequest(
    @NotNull(message = "El identificador del desafío OTP es obligatorio")
    UUID desafioId,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "El código OTP es obligatorio")
    @Pattern(regexp = "^[0-9]{6}$", message = "El código OTP debe tener 6 dígitos numéricos")
    @JsonDeserialize(using = TextoEstricto.class)
    String codigoOtp,
    PlataformaCliente plataforma
) {
    @Override
    public String toString() {
        return "VerificarOtpRequest[desafioId=" + desafioId + ", codigo protegido]";
    }
}
