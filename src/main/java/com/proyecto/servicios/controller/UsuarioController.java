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
    public com.proyecto.servicios.model.usuario.UsuarioResponse propio(@AuthenticationPrincipal Jwt jwt) {
        long id=Long.parseLong(jwt.getSubject());
        return usuarios.consultar(id,id);
    }
    @PostMapping("/buscar")
    public com.proyecto.servicios.model.cliente.ConsultasResponse.Pagina<com.proyecto.servicios.model.usuario.UsuarioResponse> buscar(
        @Valid @RequestBody com.proyecto.servicios.model.usuario.BuscarUsuariosRequest request,@AuthenticationPrincipal Jwt jwt) {
        return usuarios.buscar(request,Long.parseLong(jwt.getSubject()));
    }
    @PutMapping("/password")
    public ResponseEntity<Void> password(@Valid @RequestBody com.proyecto.servicios.model.usuario.CambiarPasswordUsuarioRequest request,@AuthenticationPrincipal Jwt jwt) {
        usuarios.cambiarPassword(request.id(),request.datos(),Long.parseLong(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
