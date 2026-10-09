package com.proyecto.servicios.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.RegistroClienteExceptionHandler.ErrorRegistro;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        response.setHeader("WWW-Authenticate","Bearer");
        responder(request,response,401,"NO_AUTENTICADO","Token ausente, inválido o acceso no disponible");
    }
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        responder(request,response,403,"ACCESO_DENEGADO","No tienes permiso para esta operación");
    }
    private void responder(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
        log.warn("Acceso rechazado; status={}; codigo={}",status,code);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        mapper.writeValue(response.getOutputStream(),new ErrorRegistro(LocalDateTime.now(),status,code,message,request.getRequestURI(),List.of()));
    }
}
