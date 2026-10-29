package com.proyecto.servicios.model.usuario;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.validation.PasswordLoginValida;
import com.proyecto.servicios.validation.PasswordValida;

public record CambiarPasswordRequest(
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @JsonDeserialize(using=PasswordEstricto.class)
    @PasswordLoginValida(message="La contraseña actual es obligatoria y no puede superar 72 bytes UTF-8") String passwordActual,
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @JsonDeserialize(using=PasswordEstricto.class)
    @PasswordValida(message="La contraseña nueva debe contener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial; no admite espacios ni más de 72 bytes UTF-8") String passwordNueva
) {
    @Override public String toString() { return "CambiarPasswordRequest[contraseñas protegidas]"; }
}
