package com.proyecto.servicios.exception;

/** Solo códigos públicos controlados; nunca mensajes del proveedor. */
public class PagoException extends RuntimeException {
    private final int status;
    private final String codigo;
    public PagoException(int status, String codigo) { super(codigo); this.status=status; this.codigo=codigo; }
    public int getStatus() { return status; }
    public String getCodigo() { return codigo; }
}
