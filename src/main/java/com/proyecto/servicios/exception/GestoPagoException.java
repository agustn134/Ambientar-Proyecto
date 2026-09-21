package com.proyecto.servicios.exception;

import lombok.Getter;

@Getter
public class GestoPagoException extends RuntimeException {
    
    private final int status;

    public GestoPagoException(int status, String message) {
        super(message);
        this.status = status;
    }
}
