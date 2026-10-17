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

    @PostMapping("/listar")
    public ConsultasResponse.Pagina<ConsultasResponse.ResumenCliente> listar(@Valid @RequestBody FiltroClientes filtro,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.listar(filtro,Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/me")
    public ConsultasResponse.DetalleCliente propio(@org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.propio(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/buscar")
    public ConsultasResponse.DetalleCliente buscar(@Valid @RequestBody FiltroClientes filtro,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.buscar(filtro,Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/consultar")
    public ConsultasResponse.DetalleCliente consultar(@Valid @RequestBody OperacionRequest.Id request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.porId(request.id(),Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/cuentas")
    public java.util.List<ConsultasResponse.Cuenta> cuentas(@Valid @RequestBody OperacionRequest.Id request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return consultas.cuentasDeCliente(request.id(),Long.parseLong(jwt.getSubject()));
    }

    @PutMapping
    public ConsultasResponse.DetalleCliente actualizar(@Valid @RequestBody OperacionRequest.Actualizar request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        return mantenimiento.actualizar(request.id(),request.datos(),Long.parseLong(jwt.getSubject()));
    }

    @DeleteMapping
    public ResponseEntity<Void> desactivar(@Valid @RequestBody OperacionRequest.Id request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        mantenimiento.desactivar(request.id(),Long.parseLong(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
