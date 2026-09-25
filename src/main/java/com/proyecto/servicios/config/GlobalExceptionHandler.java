package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.exception.GestoPagoNotFoundException;
import com.proyecto.servicios.exception.GestoPagoServiceUnavailableException;
import com.proyecto.servicios.model.error.ErrorResponseDto;
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
    public ResponseEntity<ErrorResponseDto> handleGestoPagoAuthException(GestoPagoAuthException ex, HttpServletRequest request) {
        log.error("Error de autenticación capturado: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoException.class)
    public ResponseEntity<ErrorResponseDto> handleGestoPagoException(GestoPagoException ex, HttpServletRequest request) {
        log.error("Error de GestoPago capturado: {}", ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return buildErrorResponse(status, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleGestoPagoNotFoundException(GestoPagoNotFoundException ex, HttpServletRequest request) {
        log.error("Recurso no encontrado en GestoPago: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoServiceUnavailableException.class)
    public ResponseEntity<ErrorResponseDto> handleGestoPagoServiceUnavailableException(GestoPagoServiceUnavailableException ex, HttpServletRequest request) {
        log.error("Servicio de GestoPago no disponible: {}", ex.getMessage());
        HttpStatus status = ex.getStatus() == 504 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        return buildErrorResponse(status, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({RetryableException.class, SocketTimeoutException.class})
    public ResponseEntity<ErrorResponseDto> handleTimeoutException(Exception ex, HttpServletRequest request) {
        log.error("Timeout de comunicación capturado: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.GATEWAY_TIMEOUT, "Error de comunicación o timeout al intentar conectar con el servicio externo.", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Error interno del servidor capturado: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado en el servidor.", request.getRequestURI());
    }

    private ResponseEntity<ErrorResponseDto> buildErrorResponse(HttpStatus status, String message, String path) {
        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return new ResponseEntity<>(errorResponse, status);
    }
}
