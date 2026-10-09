package com.proyecto.servicios.model.usuario;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.validation.PasswordLoginValida;
import com.proyecto.servicios.validation.PasswordValida;

public record CambiarPasswordRequest(
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @JsonDeserialize(using=PasswordEstricto.class)
    @PasswordLoginValida String passwordActual,
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @JsonDeserialize(using=PasswordEstricto.class)
    @PasswordValida String passwordNueva
) {
    @Override public String toString() { return "CambiarPasswordRequest[contraseñas protegidas]"; }
}
