package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;

/** Evita serializar el grafo bidireccional de entidades o exponer datos personales. */
public record RegistroClienteResponse(Long clienteId, String estatus, CuentaResponse cuenta) {
    public record CuentaResponse(String numeroCuenta, BigDecimal saldo, String estatus) {}
}
