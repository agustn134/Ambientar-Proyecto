package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.usuario.CambiarPasswordRequest;
import com.proyecto.servicios.service.MantenimientoUsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final MantenimientoUsuarioService usuarios;
    @GetMapping("/me")
    public MantenimientoUsuarioService.UsuarioResponse propio(@AuthenticationPrincipal Jwt jwt) {
        long id=Long.parseLong(jwt.getSubject());
        return usuarios.consultar(id,id);
    }
    @GetMapping("/{id}")
    public MantenimientoUsuarioService.UsuarioResponse consultar(@PathVariable @Positive(message="El ID del usuario debe ser un número entero mayor que cero") long id,@AuthenticationPrincipal Jwt jwt) {
        return usuarios.consultar(id,Long.parseLong(jwt.getSubject()));
    }
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> password(@PathVariable @Positive(message="El ID del usuario debe ser un número entero mayor que cero") long id,@Valid @RequestBody CambiarPasswordRequest request,@AuthenticationPrincipal Jwt jwt) {
        usuarios.cambiarPassword(id,request,Long.parseLong(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
