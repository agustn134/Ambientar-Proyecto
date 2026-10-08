package com.proyecto.servicios.config;

import com.proyecto.servicios.security.SecurityErrorHandler;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,"/clientes","/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**").permitAll()
                // APIs antiguas de personas no se exponen a clientes sin una política de permisos.
                .requestMatchers("/personas","/personasActualiza","/personasElimina").denyAll()
                .anyRequest().authenticated())
            .exceptionHandling(errorsConfig -> errorsConfig.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}).authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .build();
    }
}
