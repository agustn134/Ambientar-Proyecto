package com.proyecto.servicios.model.usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CambiarPasswordUsuarioRequest(
    @NotNull(message="El ID del usuario es obligatorio") @Positive(message="El ID debe ser mayor que cero") Long id,
    @NotNull(message="Los datos de contraseña son obligatorios") @Valid CambiarPasswordRequest datos
) {
    @Override public String toString() { return "CambiarPasswordUsuarioRequest[datos protegidos]"; }
}
