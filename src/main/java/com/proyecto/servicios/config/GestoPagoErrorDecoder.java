package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.exception.GestoPagoException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

@Slf4j
@Configuration
public class GestoPagoErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String requestUrl = response.request().url();
        int status = response.status();
        
        log.error("Error detectado en cliente Feign al invocar {}. Estado: {}", requestUrl, status);
        
        // Intentar extraer el cuerpo del error si lo hay (Opcional, para logs)
        String responseBody = "";
        try (InputStream bodyIs = response.body().asInputStream()) {
            responseBody = new String(bodyIs.readAllBytes());
            String logBody = responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
            log.error("Cuerpo de la respuesta de error (truncado): {}", logBody);
        } catch (Exception e) {
            log.warn("No se pudo leer el cuerpo de la respuesta de error.");
        }

        if (status == 401 || status == 403) {
            return new GestoPagoAuthException("Error de Autenticación con GestoPago (" + status + ")");
        } else if (status >= 400 && status <= 499) {
            return new GestoPagoException(status, "Petición inválida hacia GestoPago (" + status + ")");
        } else if (status >= 500 && status <= 599) {
            return new GestoPagoException(status, "Error interno en el servidor de GestoPago (" + status + ")");
        }

        return defaultErrorDecoder.decode(methodKey, response);
    }
}
