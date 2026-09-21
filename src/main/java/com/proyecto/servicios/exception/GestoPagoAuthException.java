package com.proyecto.servicios.exception;

public class GestoPagoAuthException extends GestoPagoException {
    
    public GestoPagoAuthException(String message) {
        super(401, message);
    }
}
