package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.*;
import com.proyecto.servicios.service.ConsultaClienteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cuentas")
@RequiredArgsConstructor
public class CuentaController {
    private final ConsultaClienteService consultas;
    @PostMapping("/listar")
    public ConsultasResponse.Pagina<ConsultasResponse.Cuenta> listar(@Valid @RequestBody FiltroCuentas filtro,@AuthenticationPrincipal Jwt jwt) {
        return consultas.listarCuentas(filtro,Long.parseLong(jwt.getSubject()));
    }
    @PostMapping("/consultar")
    public ConsultasResponse.Cuenta consultar(@Valid @RequestBody OperacionRequest.Cuenta request,@AuthenticationPrincipal Jwt jwt) {
        return consultas.porNumeroCuenta(request.numeroCuenta(),Long.parseLong(jwt.getSubject()));
    }
    @PostMapping("/saldo")
    public ConsultasResponse.Saldo saldo(@Valid @RequestBody OperacionRequest.Cuenta request,@AuthenticationPrincipal Jwt jwt) {
        var cuenta=consultas.porNumeroCuenta(request.numeroCuenta(),Long.parseLong(jwt.getSubject()));
        return new ConsultasResponse.Saldo(cuenta.numeroCuenta(),cuenta.saldo());
    }
}
