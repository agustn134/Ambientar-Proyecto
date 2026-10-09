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
    @GetMapping
    public ConsultasResponse.Pagina<ConsultasResponse.Cuenta> listar(@Valid @ModelAttribute FiltroCuentas filtro,@AuthenticationPrincipal Jwt jwt) {
        return consultas.listarCuentas(filtro,Long.parseLong(jwt.getSubject()));
    }
    @GetMapping("/{numeroCuenta}")
    public ConsultasResponse.Cuenta consultar(@PathVariable @Pattern(regexp="[0-9]{20}") String numeroCuenta,@AuthenticationPrincipal Jwt jwt) {
        return consultas.porNumeroCuenta(numeroCuenta,Long.parseLong(jwt.getSubject()));
    }
    @GetMapping("/{numeroCuenta}/saldo")
    public ConsultasResponse.Saldo saldo(@PathVariable @Pattern(regexp="[0-9]{20}") String numeroCuenta,@AuthenticationPrincipal Jwt jwt) {
        var cuenta=consultas.porNumeroCuenta(numeroCuenta,Long.parseLong(jwt.getSubject()));
        return new ConsultasResponse.Saldo(cuenta.numeroCuenta(),cuenta.saldo());
    }
}
