package com.proyecto.servicios.exception;

import lombok.Getter;

@Getter
public class OtpException extends RuntimeException {
    private final int status;
    private final String codigo;

    public OtpException(int status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
