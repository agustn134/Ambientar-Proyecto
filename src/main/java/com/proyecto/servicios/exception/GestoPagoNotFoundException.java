package com.proyecto.servicios.exception;

/**
 * Excepción lanzada cuando el endpoint de GestoPago no es encontrado (HTTP 404).
 * Indica que la URL o el recurso solicitado no existe en el servidor externo.
 *
 * @author Agustin Lopez Parra
 * @version 1.0
 * @see com.proyecto.servicios.exception.GestoPagoException
 * @see com.proyecto.servicios.config.GestoPagoErrorDecoder
 */
public class GestoPagoNotFoundException extends GestoPagoException {

    public GestoPagoNotFoundException(String message) {
        super(404, message);
    }
}
