package com.proyecto.servicios.security;

import com.proyecto.servicios.entity.usuario.RolUsuario;
import com.proyecto.servicios.repositorys.usuario.UsuarioRepository;
import com.proyecto.servicios.repositorys.usuario.UsuarioSesionActivaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UsuarioJwtValidator implements OAuth2TokenValidator<Jwt> {
    private final UsuarioRepository usuarios;
    private final UsuarioSesionActivaRepository sesiones;
    private final Clock authClock;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            long id = Long.parseLong(token.getSubject());
            Object version = token.getClaim("ver");
            var rol = RolUsuario.valueOf(token.getClaimAsString("rol"));
            String sidRaw = token.getClaimAsString("sid");
            if (sidRaw == null || sidRaw.isBlank()) {
                return falla();
            }
            UUID sesionId = UUID.fromString(sidRaw);
            LocalDateTime ahora = LocalDateTime.ofInstant(authClock.instant(), ZoneOffset.UTC);
            if (id > 0 && version instanceof Number number && number.doubleValue() == number.longValue()
                    && token.getExpiresAt() != null && token.getIssuedAt() != null
                    && usuarios.existsByIdAndActivoTrueAndVersionTokenAndClienteEstatusAndRol(
                        id, number.longValue(), "ACTIVO", rol)
                    && sesiones.existsByUsuarioIdAndSesionIdAndVersionTokenAndActivaTrueAndExpiraEnAfter(
                        id, sesionId, number.longValue(), ahora)) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException ex) {
            // El subject o los claims de sesión no identifican una sesión activa válida.
        }
        return falla();
    }

    private static OAuth2TokenValidatorResult falla() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Acceso no disponible", null));
    }
}
