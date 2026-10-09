package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.service.RegistroClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {
    private final RegistroClienteService registro;
    private final com.proyecto.servicios.service.ConsultaClienteService consultas;
    private final com.proyecto.servicios.service.MantenimientoClienteService mantenimiento;

    @PostMapping(consumes="application/json", produces="application/json")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<RegistroClienteResponse> registrar(@Valid @RequestBody RegistroClienteRequest request) {
        return ResponseEntity.status(201).body(registro.registrar(request));
    }

    @GetMapping
    public ConsultasResponse.Pagina<ConsultasResponse.ResumenCliente> listar(@Valid @ModelAttribute FiltroClientes filtro,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.listar(filtro,Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/me")
    public ConsultasResponse.DetalleCliente propio(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.propio(Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/buscar")
    public ConsultasResponse.DetalleCliente buscar(@Valid @ModelAttribute FiltroClientes filtro,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.buscar(filtro,Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public ConsultasResponse.DetalleCliente consultar(@PathVariable @jakarta.validation.constraints.Positive long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.porId(id,Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/{id}/cuentas")
    public java.util.List<ConsultasResponse.Cuenta> cuentas(@PathVariable @jakarta.validation.constraints.Positive long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.cuentasDeCliente(id,Long.parseLong(jwt.getSubject()));
    }

    @PutMapping("/{id}")
    public ConsultasResponse.DetalleCliente actualizar(@PathVariable @jakarta.validation.constraints.Positive long id,
            @Valid @RequestBody ActualizarClienteRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return mantenimiento.actualizar(id,request,Long.parseLong(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable @jakarta.validation.constraints.Positive long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        mantenimiento.desactivar(id,Long.parseLong(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
