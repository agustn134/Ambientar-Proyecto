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

    @PostMapping(value="/login",consumes="application/json",produces="application/json")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Se lanza fuera de LoginService: el contador ya fue confirmado en la base.
        LoginResponse response=login.autenticar(request).orElseThrow(CredencialesInvalidasException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping("/me")
    @SecurityRequirement(name="bearerAuth")
    public ResponseEntity<UsuarioSesionService.Perfil> perfil(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sesion.consultar(Long.parseLong(token.getSubject())));
    }
}
