package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.integration.gestopago.GestoPagoTransport;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoSecureTokenCache;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoSecureTokenCache.Token;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GestoPagoTokenServiceImpl implements GestoPagoTokenService {
    private final GestoPagoTransport transport;
    private final GestoPagoSecureTokenCache cache;
    private final ObjectMapper mapper;
    @Value("${gestopago.auth.id-distribuidor}") private Integer idDistribuidor;
    @Value("${gestopago.auth.codigo-dispositivo}") private String codigoDispositivo;
    @Value("${gestopago.auth.password:}") private String password;
    @Value("${gestopago.auth.basic-authorization:}") private String basic;
    @Value("${gestopago.api-key:}") private String apiKey = "";
    @Value("${gestopago.auth.url}") private String url;
    @Value("${gestopago.auth.endpoint:/sistema/app/jwt-gp/authenticate/}") private String endpoint;
    @Value("${gestopago.auth.background-enabled:false}") private boolean background;

    @Override
    @Scheduled(fixedDelayString="${gestopago.auth.refresh-rate-ms:3600000}", initialDelay=60000)
    public void renovarToken() {
        if (!background) return;
        try { obtenerTokenActivo(idDistribuidor, codigoDispositivo); }
        catch (RuntimeException error) { log.warn("No se pudo preparar la sesión del proveedor."); }
    }
    @Override
    public Optional<GestoPagoToken> obtenerTokenActivo(Integer distributor, String device) {
        if (!java.util.Objects.equals(distributor, idDistribuidor)
                || !java.util.Objects.equals(device, codigoDispositivo)) return Optional.empty();
        try {
            Token token = cache.get(distributor, device, Instant.now(), this::authenticate);
            // Objeto efímero de compatibilidad. Nunca se guarda mediante el repositorio JPA antiguo.
            var result = new GestoPagoToken();
            result.setToken(token.value()); result.setActivo(true); result.setTokenType("Bearer");
            return Optional.of(result);
        } catch (RuntimeException error) {
            throw new GestoPagoAuthException("No fue posible preparar la sesión del proveedor.");
        }
    }
    private Token authenticate() {
        if (password == null || password.isBlank()) throw new IllegalStateException("Credenciales del proveedor no configuradas.");
        var headers = new java.util.HashMap<String,String>();
        // La documentación no exige Basic en authenticate. Solo enviarlo si fue asignado/configurado.
        if (basic != null && !basic.isBlank()) {
            if (!basic.startsWith("Basic ") || basic.contains("\r") || basic.contains("\n"))
                throw new IllegalStateException("Configuración de autenticación inválida.");
            headers.put("Authorization", basic);
        }
        if (!apiKey.isBlank()) headers.put("X-API-Key", apiKey);
        Instant started = Instant.now();
        var response = transport.post(URI.create(url + endpoint), headers,
                Map.of("idDistribuidor", idDistribuidor.toString(), "codigoDispositivo", codigoDispositivo, "password", password));
        try {
            if (response.status() != 200) throw new IllegalStateException();
            var json = mapper.readTree(response.body());
            String token = json.path("token").asText("");
            if (token.isBlank() || token.equals("EXPIRED") || token.chars().anyMatch(Character::isWhitespace)
                    || (json.has("success") && !json.path("success").asBoolean())) throw new IllegalStateException();
            long ttl = json.has("expires_in") ? json.path("expires_in").asLong(0) : 86400;
            if (ttl <= 30 || ttl > 86400) throw new IllegalStateException();
            return new Token(token, started.plusSeconds(ttl));
        } catch (Exception error) { throw new IllegalStateException("Respuesta de autenticación no válida."); }
    }
    @Override public void invalidarToken(Integer distributor, String device, String token) {
        if (!java.util.Objects.equals(distributor,idDistribuidor) || !java.util.Objects.equals(device,codigoDispositivo)) return;
        cache.invalidate(distributor,device,token);
    }
}
