package com.proyecto.servicios.exception;

/** Código estable; nunca adjuntar valores de entrada ni secretos. */
public class ContratoPagoException extends RuntimeException {
    private final String codigo;
    public ContratoPagoException(String codigo) {
        super("La operación no cumple el contrato de pago habilitado.");
        this.codigo = codigo;
    }
    public String getCodigo() { return codigo; }
}
