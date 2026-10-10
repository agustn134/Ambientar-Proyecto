package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.exception.GestoPagoNotFoundException;
import com.proyecto.servicios.exception.GestoPagoServiceUnavailableException;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GestoPagoAuthException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoAuthException(GestoPagoAuthException ex, HttpServletRequest request) {
        log.warn("Error de autenticación GestoPago; status=401");
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoException(GestoPagoException ex, HttpServletRequest request) {
        log.error("Error GestoPago; status={}; tipo={}", ex.getStatus(), ex.getClass().getSimpleName());
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return buildErrorResponse(status, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoNotFoundException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoNotFoundException(GestoPagoNotFoundException ex, HttpServletRequest request) {
        log.warn("Recurso no encontrado en GestoPago; status=404");
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoServiceUnavailableException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoServiceUnavailableException(GestoPagoServiceUnavailableException ex, HttpServletRequest request) {
        log.error("Servicio GestoPago no disponible; status={}", ex.getStatus());
        HttpStatus status = ex.getStatus() == 504 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        return buildErrorResponse(status, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({RetryableException.class, SocketTimeoutException.class})
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleTimeoutException(Exception ex, HttpServletRequest request) {
        log.error("Error de comunicación; status=504; tipo={}", ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.GATEWAY_TIMEOUT, "Error de comunicación o timeout al intentar conectar con el servicio externo.", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Error interno; status=500; tipo={}", ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado en el servidor.", request.getRequestURI());
    }

    private ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> buildErrorResponse(HttpStatus status, String message, String path) {
        var errorResponse = new RegistroClienteExceptionHandler.ErrorRegistro(LocalDateTime.now(), status.value(), status == HttpStatus.INTERNAL_SERVER_ERROR ? "ERROR_INTERNO" : "SERVICIO_EXTERNO", message, path, java.util.List.of());
        return new ResponseEntity<>(errorResponse, status);
    }
}
