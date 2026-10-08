package com.proyecto.servicios.security;

import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsuarioJwtValidator implements OAuth2TokenValidator<Jwt> {
    private final UsuarioRepository usuarios;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            long id=Long.parseLong(token.getSubject());
            Object version=token.getClaim("ver");
            if (id > 0 && version instanceof Number number && number.doubleValue() == number.longValue() && token.getExpiresAt() != null
                    && token.getIssuedAt() != null
                    && usuarios.existsByIdAndActivoTrueAndVersionTokenAndClienteEstatus(id, number.longValue(), "ACTIVO")) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException ex) {
            // El subject no identifica un usuario válido.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Acceso no disponible", null));
    }
}
