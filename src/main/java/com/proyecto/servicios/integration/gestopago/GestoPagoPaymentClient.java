package com.proyecto.servicios.integration.gestopago;

import com.proyecto.servicios.service.GestoPagoTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@Component
public class GestoPagoPaymentClient {
    private final GestoPagoTransport transport;
    private final GestoPagoPayloadCipher cipher;
    private final GestoPagoTokenService tokens;
    @Value("${gestopago.payment.enabled:false}") private boolean enabled;
    @Value("${gestopago.service.url}") private String url;
    @Value("${gestopago.auth.id-distribuidor}") private Integer distributor;
    @Value("${gestopago.auth.codigo-dispositivo}") private String device;
    @Value("${gestopago.api-key:}") private String apiKey;
    public record Prepared(String signed,String bearer) {
        @Override public String toString(){return "Prepared[protegido]";}
    }
    /** Prepara sesión/cifrado ANTES de registrar el intento de envío financiero. */
    public Prepared preparar(String originalJson) {
        if(!enabled)throw new IllegalStateException("Envío financiero no habilitado.");
        String signed=cipher.cifrar(originalJson);
        var token=tokens.obtenerTokenActivo(distributor,device)
                .orElseThrow(()->new IllegalStateException("Sesión del proveedor no disponible."));
        return new Prepared(signed,token.getToken());
    }
    /** Una invocación = un único intento HTTP. No hace refresh + reenvío financiero. */
    public GestoPagoTransport.Reply ejecutar(Prepared prepared,boolean confirmation) {
        if(!enabled)throw new IllegalStateException("Envío financiero no habilitado.");
        var headers=new HashMap<String,String>();headers.put("Authorization","Bearer "+prepared.bearer());
        if(!apiKey.isBlank())headers.put("X-API-Key",apiKey);
        var reply=transport.post(URI.create(url+"/sistema/service/"+(confirmation?"confirmTx.do":"sendTx.do")),headers,
                Map.of("random",Long.toString(Instant.now().toEpochMilli()),"signed",prepared.signed()));
        if(reply.status()==403 && new GestoPagoReferenceParser().tokenExpired(reply.body()))
            tokens.invalidarToken(distributor,device,prepared.bearer());
        return reply;
    }
    public GestoPagoPaymentClient(GestoPagoTransport transport, GestoPagoPayloadCipher cipher, GestoPagoTokenService tokens) {
        this.transport=transport; this.cipher=cipher; this.tokens=tokens;
    }
}
