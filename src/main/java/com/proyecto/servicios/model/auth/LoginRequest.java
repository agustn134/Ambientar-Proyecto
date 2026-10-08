package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.model.cliente.TextoEstricto;
import com.proyecto.servicios.model.usuario.PasswordEstricto;
import com.proyecto.servicios.validation.PasswordLoginValida;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(
    @NotBlank @Email @Size(max=100) @JsonDeserialize(using=TextoEstricto.class) String correo,
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY)
    @JsonDeserialize(using=PasswordEstricto.class) @PasswordLoginValida String password
) {
    public LoginRequest {
        if (correo != null) correo=correo.toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "LoginRequest[credenciales protegidas]"; }
}
