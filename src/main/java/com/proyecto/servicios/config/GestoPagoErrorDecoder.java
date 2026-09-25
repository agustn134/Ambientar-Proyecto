package com.proyecto.servicios.config;

import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.exception.GestoPagoNotFoundException;
import com.proyecto.servicios.exception.GestoPagoServiceUnavailableException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

/**
 * Interceptor de errores para el cliente Feign que consume la API de GestoPago.
 * Convierte los códigos de estado HTTP de error en excepciones personalizadas del dominio
 * para que el {@link GlobalExceptionHandler} pueda manejarlas de forma centralizada.
 *
 * @author Agustin Lopez Parra
 * @version 1.1
 * @see GlobalExceptionHandler
 * @see com.proyecto.servicios.exception.GestoPagoException
 * @see com.proyecto.servicios.exception.GestoPagoAuthException
 * @see com.proyecto.servicios.exception.GestoPagoNotFoundException
 * @see com.proyecto.servicios.exception.GestoPagoServiceUnavailableException
 */
@Slf4j
@Configuration
public class GestoPagoErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    /**
     * Decodifica la respuesta de error de GestoPago y lanza la excepción personalizada correspondiente.
     *
     * @param methodKey Identificador del método de Feign que realizó la petición.
     * @param response  Respuesta HTTP recibida desde la API de GestoPago con el status de error.
     * @return Una excepción del dominio que describe el error ocurrido de forma controlada.
     * @see com.proyecto.servicios.exception.GestoPagoAuthException
     * @see com.proyecto.servicios.exception.GestoPagoNotFoundException
     * @see com.proyecto.servicios.exception.GestoPagoServiceUnavailableException
     * @see com.proyecto.servicios.exception.GestoPagoException
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        String requestUrl = response.request().url();
        int status = response.status();

        log.error("Error detectado en cliente Feign al invocar '{}'. Status HTTP: {}", requestUrl, status);

        // Intentar extraer el cuerpo del error para incluirlo en el log
        String responseBody = "";
        try (InputStream bodyIs = response.body().asInputStream()) {
            responseBody = new String(bodyIs.readAllBytes());
            String logBody = responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
            log.error("Cuerpo de la respuesta de error (truncado a 500 chars): {}", logBody);
        } catch (Exception e) {
            log.warn("No se pudo leer el cuerpo de la respuesta de error desde GestoPago.");
        }

        // 401 Unauthorized / 403 Forbidden: Token inválido o sin permisos
        if (status == 401 || status == 403) {
            log.error("Fallo de autenticación con GestoPago. Verificar Bearer Token en la configuración.");
            return new GestoPagoAuthException(
                    "Acceso denegado por GestoPago (Status " + status + "). El token puede ser inválido o haber expirado.");
        }

        // 404 Not Found: El endpoint de GestoPago no existe o cambió de URL
        if (status == 404) {
            log.error("El endpoint de GestoPago no fue encontrado: {}", requestUrl);
            return new GestoPagoNotFoundException(
                    "El recurso solicitado no fue encontrado en GestoPago (Status 404). URL: " + requestUrl);
        }

        // 429 Too Many Requests: Se superó el límite de peticiones al proveedor
        if (status == 429) {
            log.error("GestoPago rechazó la petición por exceso de solicitudes (Rate Limit).");
            return new GestoPagoException(429,
                    "Demasiadas peticiones hacia GestoPago (Status 429). Espere antes de reintentar.");
        }

        // 4xx genérico: Petición mal formada o parámetros incorrectos
        if (status >= 400 && status <= 499) {
            log.error("Petición inválida hacia GestoPago. Status: {}", status);
            return new GestoPagoException(status,
                    "La petición enviada a GestoPago es inválida (Status " + status + ").");
        }

        // 503 Service Unavailable / 504 Gateway Timeout: GestoPago no responde
        if (status == 503 || status == 504) {
            log.error("El servicio de GestoPago no está disponible o no respondió a tiempo. Status: {}", status);
            return new GestoPagoServiceUnavailableException(status,
                    "El servicio de GestoPago no está disponible en este momento (Status " + status + "). Intente más tarde.");
        }

        // 5xx genérico: Error interno en los servidores de GestoPago
        if (status >= 500 && status <= 599) {
            log.error("Error interno en el servidor de GestoPago. Status: {}", status);
            return new GestoPagoException(status,
                    "Error interno en los servidores de GestoPago (Status " + status + "). El problema es del proveedor, no de la aplicación.");
        }

        // Para cualquier otro código no contemplado, delega al decoder por defecto de Feign
        log.warn("Código de error no contemplado recibido de GestoPago: {}. Delegando al decoder por defecto.", status);
        return defaultErrorDecoder.decode(methodKey, response);
    }
}

