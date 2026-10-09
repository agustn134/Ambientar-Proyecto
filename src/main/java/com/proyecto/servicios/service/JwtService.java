package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.usuario.Usuario;
import com.proyecto.servicios.model.auth.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final long ttl;

    public JwtService(JwtEncoder encoder, Clock authClock,
            @Value("${security.jwt.issuer:http://localhost:8080}") String issuer,
            @Value("${security.jwt.audience:gestopago-api}") String audience,
            @Value("${security.jwt.ttl-seconds:300}") long ttl) {
        if (ttl < 1 || ttl > 3600) throw new IllegalArgumentException("JWT TTL debe estar entre 1 y 3600 segundos");
        this.encoder=encoder; this.clock=authClock; this.issuer=issuer; this.audience=audience; this.ttl=ttl;
    }

    public LoginResponse emitir(Usuario usuario) {
        Instant now=clock.instant();
        Instant expires=now.plusSeconds(ttl);
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(usuario.getId().toString()).issuedAt(now).notBefore(now).expiresAt(expires)
                .id(UUID.randomUUID().toString()).claim("ver", usuario.getVersionToken()).claim("rol",usuario.getRol().name()).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return new LoginResponse(token,"Bearer",ttl,expires);
    }
}
