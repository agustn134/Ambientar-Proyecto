package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.CredencialesInvalidasException;
import com.proyecto.servicios.model.auth.*;
import com.proyecto.servicios.service.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final LoginService login;
    private final UsuarioSesionService sesion;

    @PostMapping(value = "/login", consumes = "application/json", produces = "application/json")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<DesafioOtpResponse> login(@Valid @RequestBody LoginRequest request) {
        // Se lanza fuera de LoginService: el contador de intentos ya fue confirmado en la base.
        DesafioOtpResponse response = login.iniciarDesafio(request).orElseThrow(CredencialesInvalidasException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping(value = "/otp/verificar", consumes = "application/json", produces = "application/json")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<LoginResponse> verificarOtp(@Valid @RequestBody VerificarOtpRequest request) {
        LoginResponse response = login.verificarOtp(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping(value = "/otp/reenviar", consumes = "application/json", produces = "application/json")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<DesafioOtpResponse> reenviarOtp(@Valid @RequestBody ReenviarOtpRequest request) {
        DesafioOtpResponse response = login.reenviarOtp(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping(value = "/sesion/reemplazar", consumes = "application/json", produces = "application/json")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<LoginResponse> reemplazarSesion(@Valid @RequestBody ReemplazarSesionRequest request) {
        LoginResponse response = login.reemplazarSesion(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt token) {
        login.cerrarSesion(Long.parseLong(token.getSubject()));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UsuarioSesionService.Perfil> perfil(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(sesion.consultar(Long.parseLong(token.getSubject())));
    }
}
