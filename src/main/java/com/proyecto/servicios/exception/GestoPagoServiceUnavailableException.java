package com.proyecto.servicios.exception;

/**
 * Excepción lanzada cuando el servicio externo de GestoPago no está disponible
 * (HTTP 503 Service Unavailable o 504 Gateway Timeout).
 * Indica que el servidor de GestoPago está caído, saturado o no responde a tiempo.
 *
 * @author Agustin Lopez Parra
 * @version 1.0
 * @see com.proyecto.servicios.exception.GestoPagoException
 * @see com.proyecto.servicios.config.GestoPagoErrorDecoder
 */
public class GestoPagoServiceUnavailableException extends GestoPagoException {

    public GestoPagoServiceUnavailableException(int status, String message) {
        super(status, message);
    }
}
