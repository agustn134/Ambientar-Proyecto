package com.proyecto.servicios.exception;
public class GestoPagoExpiredTokenException extends GestoPagoAuthException {
    public GestoPagoExpiredTokenException() { super("La sesión del proveedor requiere renovación."); }
}
