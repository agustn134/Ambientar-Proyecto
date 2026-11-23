package com.proyecto.servicios.model.auth;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReenviarOtpRequest(
    @NotNull(message = "El identificador del desafío OTP es obligatorio")
    UUID desafioId
) {}
