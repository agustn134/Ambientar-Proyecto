package com.proyecto.servicios.exception;

public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException() {
        super("Credenciales inválidas o acceso no disponible");
    }
}
