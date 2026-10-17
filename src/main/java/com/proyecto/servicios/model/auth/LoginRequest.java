package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.proyecto.servicios.model.cliente.TextoEstricto;
import com.proyecto.servicios.model.usuario.PasswordEstricto;
import com.proyecto.servicios.validation.PasswordLoginValida;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres")
    @JsonDeserialize(using = TextoEstricto.class) String correo,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonDeserialize(using = PasswordEstricto.class)
    @PasswordLoginValida(message = "La contraseña es obligatoria y no puede superar 72 bytes UTF-8") String password
) {
    public LoginRequest {
        if (correo != null) correo = correo.toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "LoginRequest[credenciales protegidas]"; }
}
