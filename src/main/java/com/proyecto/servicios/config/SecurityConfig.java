package com.proyecto.servicios.config;

import com.proyecto.servicios.security.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors) throws Exception {
        var authorities=new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("rol");
        authorities.setAuthorityPrefix("ROLE_");
        var converter=new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .cacheControl(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,"/clientes","/auth/login","/auth/otp/verificar","/auth/otp/reenviar","/auth/sesion/reemplazar").permitAll()
                .requestMatchers(HttpMethod.GET,"/catalogos/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/catalogos/codigos-postales/consultar").permitAll()
                .requestMatchers(HttpMethod.GET,"/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/clientes/listar","/cuentas/listar").hasRole("EJECUTIVO")
                // APIs antiguas de personas no se exponen a clientes sin una política de permisos.
                .requestMatchers("/personas","/personasActualiza","/personasElimina").denyAll()
                .anyRequest().authenticated())
            .exceptionHandling(errorsConfig -> errorsConfig.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)).authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:5173,http://127.0.0.1:5173,http://localhost:8081,https://ambientar-proyecto.onrender.com}") String rawOrigins) {
        List<String> origins = Arrays.stream(rawOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("Configura al menos un origen CORS explícito sin comodines");
        }
        for (String origin : origins) {
            if (origin.contains("*")) {
                throw new IllegalArgumentException("No se permiten comodines (*) en security.cors.allowed-origins: " + origin);
            }
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Accept",
            "Idempotency-Key", "X-Client-Platform", "X-Requested-With", "Cache-Control"));
        config.setExposedHeaders(List.of("Location", "Retry-After", "WWW-Authenticate"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
