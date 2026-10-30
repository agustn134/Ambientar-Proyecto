package com.proyecto.servicios.integration.gestopago;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Transporte acotado: sin redirecciones, retries ni mensajes crudos del cliente HTTP. */
@Component
public class GestoPagoTransport {
    private boolean simulacion;
    @org.springframework.beans.factory.annotation.Autowired
    void configurarEntorno(org.springframework.core.env.Environment env) {
        simulacion=env.matchesProfiles("simulacion");
    }
    private final int responseTimeoutSeconds;
    public GestoPagoTransport() { this(70); }
    GestoPagoTransport(int responseTimeoutSeconds) { this.responseTimeoutSeconds = responseTimeoutSeconds; }
    public record Reply(int status, String body) {
        @Override public String toString() { return "Reply[status=" + status + ", body protegido]"; }
    }
    public Reply post(URI uri, Map<String, String> headers, Map<String, String> form) {
        if(simulacion)throw new IllegalStateException("Transporte real bloqueado durante la simulación.");
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalStateException("La integración requiere HTTPS.");
        }
        return execute(uri, headers, form);
    }
    // Acceso de paquete para pruebas de transporte sobre un servidor loopback controlado.
    Reply execute(URI uri, Map<String, String> headers, Map<String, String> form) {
        var config = RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(responseTimeoutSeconds)).build();
        try (var client = HttpClients.custom().disableAutomaticRetries().disableRedirectHandling()
                .setDefaultRequestConfig(config).build()) {
            var request = new HttpPost(uri);
            headers.forEach(request::setHeader);
            request.setEntity(new UrlEncodedFormEntity(form.entrySet().stream()
                    .map(e -> new BasicNameValuePair(e.getKey(), e.getValue())).toList(), StandardCharsets.UTF_8));
            return client.execute(request, response -> {
                if (response.getEntity() == null) return new Reply(response.getCode(), "");
                try (var input = response.getEntity().getContent()) {
                    byte[] body = input.readNBytes(65537);
                    if (body.length > 65536) throw new java.io.IOException();
                    return new Reply(response.getCode(), new String(body, StandardCharsets.UTF_8));
                }
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Comunicación con GestoPago no concluyente.");
        }
    }
}
