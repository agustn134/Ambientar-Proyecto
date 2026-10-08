package com.proyecto.servicios.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.proyecto.servicios.security.UsuarioJwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration
public class JwtConfig {
    @Bean
    public SecretKey jwtSecret(@Value("${security.jwt.secret:}") String encoded) {
        try {
            byte[] bytes=Base64.getDecoder().decode(encoded);
            if (bytes.length < 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes,"HmacSHA256");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Configura JWT_SECRET con una clave aleatoria Base64 de al menos 32 bytes");
        }
    }

    @Bean public Clock authClock() { return Clock.systemUTC(); }

    @Bean public JwtEncoder jwtEncoder(SecretKey jwtSecret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecret, UsuarioJwtValidator usuarios,
            @Value("${security.jwt.issuer:http://localhost:8080}") String issuer,
            @Value("${security.jwt.audience:gestopago-api}") String audience) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(jwtSecret).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> aud=token -> token.getAudience() != null && token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiencia inválida", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(issuer), aud, usuarios));
        return decoder;
    }
}
