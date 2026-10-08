package com.proyecto.servicios.exception;

public class RegistroClienteException extends RuntimeException {
    private final String campo;
    private final int status;
    public RegistroClienteException(String campo, String mensaje, int status) {
        super(mensaje);
        this.campo = campo;
        this.status = status;
    }
    public String getCampo() { return campo; }
    public int getStatus() { return status; }
}
