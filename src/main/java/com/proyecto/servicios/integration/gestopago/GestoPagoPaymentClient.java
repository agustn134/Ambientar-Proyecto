package com.proyecto.servicios.integration.gestopago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.model.gestopago.pago.PagoContratoValidado;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class GestoPagoPaymentClient {
    private final GestoPagoTransport transport;
    private final GestoPagoPayloadCipher cipher;
    private final ObjectMapper mapper;
    private final GestoPagoTokenService tokens;
    private final GestoPagoOutcomeParser parser;
    @Value("${gestopago.payment.enabled:false}") private boolean enabled;
    @Value("${gestopago.service.url}") private String url;
    @Value("${gestopago.auth.id-distribuidor}") private Integer distributor;
    @Value("${gestopago.auth.codigo-dispositivo}") private String device;
    @Value("${gestopago.api-key:}") private String apiKey;
    @Value("${gestopago.payment.zone:America/Mexico_City}") private String zone;
    public GestoPagoPaymentClient(GestoPagoTransport transport, GestoPagoPayloadCipher cipher,
            ObjectMapper mapper, GestoPagoTokenService tokens, GestoPagoOutcomeParser parser) {
        this.transport=transport; this.cipher=cipher; this.mapper=mapper; this.tokens=tokens; this.parser=parser;
    }
    /** Solo podrá invocarlo el worker financiero de la siguiente entrega. No hay controller de envío. */
    public GestoPagoOutcomeParser.Result enviar(PagoContratoValidado pago, String upc, String unidad) {
        if (!enabled) throw new IllegalStateException("El envío financiero todavía no está habilitado.");
        if (pago == null || pago.requiereVerificacionReferencia())
            throw new IllegalStateException("Contrato de referencia externa pendiente.");
        if (upc == null || !upc.matches("[A-Za-z0-9]{3,15}")
                || unidad == null || !unidad.matches("[A-Za-z0-9_-]{3,25}"))
            throw new IllegalArgumentException("Identificadores de integración inválidos.");
        var payload = new LinkedHashMap<String,Object>();
        payload.put("idServicio",pago.idServicio()); payload.put("idProducto",pago.idProducto());
        payload.put("montoPago",pago.montoPago()); payload.put("telefono",pago.telefono());
        payload.put("horaLocal", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(ZonedDateTime.now(ZoneId.of(zone))));
        payload.put("upc",upc); payload.put("unidad",unidad);
        String signed;
        try { signed=cipher.cifrar(mapper.writeValueAsString(payload)); }
        catch (Exception error) { throw new IllegalStateException("No se pudo preparar el contrato externo."); }
        var token = tokens.obtenerTokenActivo(distributor,device)
                .orElseThrow(() -> new IllegalStateException("Sesión del proveedor no disponible."));
        var headers = new HashMap<String,String>(); headers.put("Authorization","Bearer " + token.getToken());
        if (!apiKey.isBlank()) headers.put("X-API-Key", apiKey);
        try {
            var response=transport.post(URI.create(url + "/sistema/service/sendTx.do"),headers,
                    Map.of("random",Long.toString(Instant.now().toEpochMilli()),"signed",signed));
            var result=parser.parse(response, pago.montoPago());
            if(result.tokenExpired()) tokens.invalidarToken(distributor,device,token.getToken());
            return result;
        } catch (RuntimeException error) {
            return GestoPagoOutcomeParser.Result.unknown();
        }
    }
}
