package com.proyecto.servicios.config;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Contrato uniforme para respuestas exitosas; 204 y errores se conservan. */
@RestControllerAdvice(basePackages="com.proyecto.servicios.controller")
public class RespuestaExitosaAdvice implements ResponseBodyAdvice<Object> {
    public record Respuesta(LocalDateTime timestamp,int status,String codigo,String mensaje,
            String path,Object datos,List<Object> errores) {}
    @Override public boolean supports(MethodParameter metodo,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body,MethodParameter metodo,MediaType media,
            Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        int status=response instanceof ServletServerHttpResponse servlet ? servlet.getServletResponse().getStatus():200;
        if(body==null || status<200 || status>=300 || body instanceof Respuesta) return body;
        String mensaje = body instanceof com.proyecto.servicios.model.gestopago.pago.ValidacionLocalResponse
                ? "Contrato local válido. No se ha enviado ningún pago."
                : request.getURI().getPath().startsWith("/api/simulacion/") ? "Operación ficticia. No se ha enviado ningún pago a GestoPago."
                : status==201?"Registro creado correctamente":"Operación realizada correctamente";
        return new Respuesta(LocalDateTime.now(),status,"EXITO",mensaje,
                request.getURI().getPath(),body,List.of());
    }
}
