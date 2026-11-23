package com.proyecto.servicios.exception;

/** Solo códigos controlados: nunca incluir entradas, credenciales o causas. */
public final class SimulacionException extends RuntimeException {
    private final int status;
    private final String codigo;
    public SimulacionException(int status, String codigo) {
        super("No se puede completar la operación simulada.");
        this.status = status;
        this.codigo = codigo;
    }
    public int getStatus() { return status; }
    public String getCodigo() { return codigo; }
}
