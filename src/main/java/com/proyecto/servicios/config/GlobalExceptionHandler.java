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
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.proyecto.servicios.exception.SimulacionException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleSimulacion(
            com.proyecto.servicios.exception.SimulacionException ex, HttpServletRequest request) {
        return buildErrorResponse(HttpStatus.valueOf(ex.getStatus()),
                "Revisa los datos, el estado de la cuenta y el saldo ficticio. No se envió un pago real.",
                request.getRequestURI(), ex.getCodigo());
    }
    @ExceptionHandler(com.proyecto.servicios.exception.CuotaCatalogoException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleCuotaCatalogo(
            com.proyecto.servicios.exception.CuotaCatalogoException ex, HttpServletRequest request) {
        var response=buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS,
                "Límite de catálogo alcanzado. Consulta los productos guardados localmente.",
                request.getRequestURI(),"CUOTA_CATALOGO_AGOTADA");
        return ResponseEntity.status(response.getStatusCode()).header("Retry-After",Long.toString(ex.getRetryAfterSeconds())).body(response.getBody());
    }

    @ExceptionHandler(com.proyecto.servicios.exception.ContratoPagoException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleContratoPago(
            com.proyecto.servicios.exception.ContratoPagoException ex, HttpServletRequest request) {
        HttpStatus status = "DATOS_INVALIDOS".equals(ex.getCodigo())
                ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        return buildErrorResponse(status, "La solicitud no cumple las reglas del producto habilitado.",
                request.getRequestURI(), ex.getCodigo());
    }

    @ExceptionHandler(GestoPagoAuthException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoAuthException(GestoPagoAuthException ex, HttpServletRequest request) {
        log.warn("Sesión del proveedor no disponible; status=503");
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "La conexión con el proveedor no está disponible temporalmente.", request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoException(GestoPagoException ex, HttpServletRequest request) {
        log.error("Error GestoPago; status={}; tipo={}", ex.getStatus(), ex.getClass().getSimpleName());
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null || !status.isError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return buildErrorResponse(status, status.value()==429 ? "Se alcanzó el límite de solicitudes a GestoPago; espera antes de reintentar" : "GestoPago no pudo completar la operación; intenta nuevamente y, si persiste, contacta al administrador", request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoNotFoundException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoNotFoundException(GestoPagoNotFoundException ex, HttpServletRequest request) {
        log.warn("Recurso no encontrado en GestoPago; status=404");
        return buildErrorResponse(HttpStatus.NOT_FOUND, "El recurso solicitado no está disponible en GestoPago", request.getRequestURI());
    }

    @ExceptionHandler(GestoPagoServiceUnavailableException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGestoPagoServiceUnavailableException(GestoPagoServiceUnavailableException ex, HttpServletRequest request) {
        log.error("Servicio GestoPago no disponible; status={}", ex.getStatus());
        HttpStatus status = ex.getStatus() == 504 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        return buildErrorResponse(status, status == HttpStatus.GATEWAY_TIMEOUT ? "GestoPago no respondió dentro del tiempo permitido; intenta nuevamente" : "GestoPago no está disponible temporalmente; intenta más tarde", request.getRequestURI());
    }

    @ExceptionHandler({RetryableException.class, SocketTimeoutException.class})
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleTimeoutException(Exception ex, HttpServletRequest request) {
        log.error("Error de comunicación; status=504; tipo={}", ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.GATEWAY_TIMEOUT, "Error de comunicación o timeout al intentar conectar con el servicio externo.", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Error interno; status=500; tipo={}", ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado en el servidor.", request.getRequestURI(), "ERROR_INTERNO");
    }

    private ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> buildErrorResponse(HttpStatus status, String message, String path) {
        return buildErrorResponse(status,message,path,"SERVICIO_EXTERNO");
    }
    private ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> buildErrorResponse(HttpStatus status, String message, String path,String codigo) {
        var errorResponse = new RegistroClienteExceptionHandler.ErrorRegistro(LocalDateTime.now(), status.value(), codigo, message, path, java.util.List.of());
        return new ResponseEntity<>(errorResponse, status);
    }
}
