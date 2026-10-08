package com.proyecto.servicios.config;

import com.proyecto.servicios.controller.ClienteController;
import com.proyecto.servicios.exception.RegistroClienteException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Order(-100)
@RestControllerAdvice(assignableTypes=ClienteController.class)
public class RegistroClienteExceptionHandler {
    public record ErrorCampo(String campo, String mensaje) {}
    public record ErrorRegistro(LocalDateTime timestamp, int status, String codigo, String mensaje,
                                String path, List<ErrorCampo> errores) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRegistro> validar(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var errores = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErrorCampo(e.getField(), e.getDefaultMessage())).toList();
        return respuesta(400, "VALIDACION", "Revisa los campos de la petición", errores, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRegistro> json(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String campo = "body";
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
                campo = mapping.getPath().stream().map(p -> p.getFieldName() == null ? "["+p.getIndex()+"]" : p.getFieldName())
                    .collect(java.util.stream.Collectors.joining("."));
                break;
            }
            cause = cause.getCause();
        }
        return respuesta(400, "JSON_INVALIDO", "JSON, tipo de dato o fecha inválidos",
            List.of(new ErrorCampo(campo, "Verifica el tipo y formato; fechas YYYY-MM-DD, texto entre comillas e ingreso numérico")), request);
    }

    @ExceptionHandler(RegistroClienteException.class)
    public ResponseEntity<ErrorRegistro> negocio(RegistroClienteException ex, HttpServletRequest request) {
        return respuesta(ex.getStatus(), ex.getStatus() == 409 ? "DATO_DUPLICADO" : "VALIDACION",
            ex.getMessage(), List.of(new ErrorCampo(ex.getCampo(), ex.getMessage())), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRegistro> conflicto(DataIntegrityViolationException ex, HttpServletRequest request) {
        // La base también protege la unicidad si dos peticiones pasan la consulta previa simultáneamente.
        if (contieneEstado(ex, "23505")) {
            return respuesta(409, "DATO_DUPLICADO", "Un identificador o correo ya está registrado", List.of(), request);
        }
        return respuesta(500, "ERROR_INTERNO", "No fue posible completar el registro", List.of(), request);
    }

    private boolean contieneEstado(Throwable ex, String estado) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && estado.equals(sql.getSQLState())) return true;
        }
        return false;
    }

    private ResponseEntity<ErrorRegistro> respuesta(int status, String codigo, String mensaje,
            List<ErrorCampo> errores, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorRegistro(LocalDateTime.now(), status, codigo,
            mensaje, request.getRequestURI(), errores));
    }
}
