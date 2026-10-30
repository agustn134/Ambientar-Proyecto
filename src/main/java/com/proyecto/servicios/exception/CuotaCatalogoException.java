package com.proyecto.servicios.exception;

public final class CuotaCatalogoException extends RuntimeException {
    private final long retryAfterSeconds;
    public CuotaCatalogoException(long retryAfterSeconds) {
        super("Se alcanzó el límite de consulta del catálogo.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
