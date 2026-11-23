package com.proyecto.servicios;

import com.proyecto.servicios.config.RegistroClienteExceptionHandler;
import com.proyecto.servicios.config.SecurityConfig;
import com.proyecto.servicios.security.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = CorsYSeguridadHttpTest.ProbeController.class,
    excludeAutoConfiguration = { DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class }
)
@TestPropertySource(properties = {
    "security.cors.allowed-origins=http://localhost:5173,https://ambientar-proyecto.onrender.com"
})
class CorsYSeguridadHttpTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class })
    @Import({ SecurityConfig.class, SecurityErrorHandler.class, RegistroClienteExceptionHandler.class, ProbeController.class })
    static class TestApp {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if ("token-cliente-valido".equals(token)) {
                    return Jwt.withTokenValue(token)
                        .header("alg", "HS256")
                        .subject("1")
                        .claim("rol", "CLIENTE")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build();
                }
                throw new org.springframework.security.oauth2.jwt.BadJwtException("Token inválido");
            };
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/catalogos/probe")
        Map<String, Object> publico() {
            return Map.of("ok", true);
        }

        @PostMapping("/clientes/listar")
        Map<String, Object> soloEjecutivo() {
            return Map.of("ok", true);
        }

        @GetMapping("/auth/me")
        Map<String, Object> protegido() {
            return Map.of("ok", true);
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void corsPermiteOrigenExplicitoYCabecerasPermitidas() throws Exception {
        mvc.perform(options("/api/gestopago/pagos")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,Idempotency-Key,X-Client-Platform"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Retry-After")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("WWW-Authenticate")))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void corsRechazaOrigenNoAutorizadoSinPermitirComodin() throws Exception {
        mvc.perform(options("/api/gestopago/pagos")
                .header(HttpHeaders.ORIGIN, "https://sitio-externo.malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void configuracionCorsRechazaComodinAsterisco() {
        var security = new SecurityConfig();
        assertThrows(IllegalArgumentException.class, () -> security.corsConfigurationSource("*"));
        assertThrows(IllegalArgumentException.class, () -> security.corsConfigurationSource("http://localhost:5173, *"));
        assertThrows(IllegalArgumentException.class, () -> security.corsConfigurationSource(" , "));
    }

    @Test
    void respuestasIncluyenCabecerasDeSeguridadY401y403Controlados() throws Exception {
        mvc.perform(get("/catalogos/probe")
                .secure(true)
                .header(HttpHeaders.ORIGIN, "https://ambientar-proyecto.onrender.com"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://ambientar-proyecto.onrender.com"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
            .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));

        mvc.perform(get("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(jsonPath("codigo").value("NO_AUTENTICADO"));

        mvc.perform(post("/clientes/listar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-cliente-valido"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("codigo").value("ACCESO_DENEGADO"));
    }
}
