package com.proyecto.servicios.controller;

import com.proyecto.servicios.config.RegistroClienteExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Adaptación del ejercicio de clase al contrato uniforme de errores del proyecto. */
@Order(-200)
@RestControllerAdvice(assignableTypes=PersonaController.class)
@RequiredArgsConstructor
public class ValidadorCampos {
    private final RegistroClienteExceptionHandler errores;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RegistroClienteExceptionHandler.ErrorRegistro> dtoValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        return errores.validar(exception,request);
    }
}
