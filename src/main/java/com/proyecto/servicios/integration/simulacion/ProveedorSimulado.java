package com.proyecto.servicios.integration.simulacion;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.integration.gestopago.*;
import com.proyecto.servicios.model.gestopago.simulacion.SimulacionDtos.*;
import com.proyecto.servicios.repositorys.simulacion.SimulacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** Proveedor EN MEMORIA + libro ficticio en BD. No contiene un cliente HTTP ni acepta URLs. */
@Component
@Profile("simulacion")
public class ProveedorSimulado {
    private final SimulacionRepository repo;
    private final ObjectMapper mapper;
    private final String secret,iv;
    private final GestoPagoPayloadCipher cipher;
    private final GestoPagoReferenceParser referencias=new GestoPagoReferenceParser();
    private final GestoPagoOutcomeParser respuestas=new GestoPagoOutcomeParser();
    private final Algorithm firma;
    private String token;
    private Instant vence=Instant.EPOCH;
    public ProveedorSimulado(SimulacionRepository repo,ObjectMapper mapper,
            @Value("${simulacion.crypto.secret-base64}") String secret,
            @Value("${simulacion.crypto.iv-base64}") String iv) {
        this.repo=repo;this.mapper=mapper;this.secret=secret;this.iv=iv;
        cipher=new GestoPagoPayloadCipher(secret,iv);
        var key=new byte[32];new SecureRandom().nextBytes(key);firma=Algorithm.HMAC256(key);
    }
    public record Intercambio(String authorization,String body) {
        @Override public String toString(){return "Intercambio[datos protegidos]";}
    }
    private synchronized String token() {
        if(vence.isBefore(Instant.now().plusSeconds(60))){
            vence=Instant.now().plusSeconds(86400);
            token=JWT.create().withIssuer("simulacion-local").withAudience("simulacion")
                    .withIssuedAt(Instant.now()).withExpiresAt(vence).sign(firma);
        }
        return token;
    }
    public Intercambio preparar(String json) {
        String random=UUID.randomUUID().toString().replace("-","");
        return new Intercambio("Bearer "+token(),"random="+encode(random)+"&signed="+encode(cipher.cifrar(json)));
    }
    /** Receptor ficticio: valida su JWT y descifra el form tal como lo haría el simulador remoto. */
    public JsonNode recibir(Intercambio request) {
        try {
            if(!request.authorization().startsWith("Bearer ") || request.body().length()>16384)throw new IllegalArgumentException();
            JWT.require(firma).withIssuer("simulacion-local").withAudience("simulacion").build().verify(request.authorization().substring(7));
            var form=new HashMap<String,String>();
            for(var field:request.body().split("&")){
                var pair=field.split("=",2);
                if(pair.length!=2 || form.put(decode(pair[0]),decode(pair[1]))!=null)throw new IllegalArgumentException();
            }
            if(!form.keySet().equals(Set.of("random","signed")) || !form.get("random").matches("[a-f0-9]{32}"))throw new IllegalArgumentException();
            var aes=Cipher.getInstance("AES/CBC/PKCS5Padding");
            aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(secret),"AES"),
                    new IvParameterSpec(Base64.getDecoder().decode(iv)));
            return mapper.readTree(new String(aes.doFinal(Base64.getDecoder().decode(form.get("signed"))),StandardCharsets.UTF_8));
        }catch(Exception ex){throw new IllegalStateException("Intercambio simulado inválido.");}
    }
    public GestoPagoReferenceVerifier.Resultado verificarReferencia(String json) {
        var payload=recibir(preparar(json));
        boolean valida=payload.path("referencia").asText().matches("SIM[0-9]{7}")
                && !payload.path("referencia").asText().equals("SIM0000000");
        var xml="<RESPONSE><MENSAJE><CODIGO>"+(valida?"01":"02")+"</CODIGO></MENSAJE></RESPONSE>";
        var response=payload.path("referencia").asText().equals("SIM0000002")
                ? new GestoPagoTransport.Reply(504,"") : new GestoPagoTransport.Reply(200,xml);
        return referencias.parse(response).resultado();
    }
    public Estado enviar(UUID id,String json,BigDecimal esperado) {
        var payload=recibir(preparar(json));
        if(!payload.path("horaLocal").asText().matches("[0-9]{14}")
                || !payload.path("upc").asText().matches("[0-9]{15}")
                || payload.path("unidad").asInt()!=1
                || payload.path("montoPago").decimalValue().compareTo(esperado)!=0)throw new IllegalStateException("Contrato simulado inválido.");
        var escenario=Escenario.valueOf(payload.path("escenario").asText());
        boolean aplicado=escenario==Escenario.EXITO || escenario==Escenario.TIMEOUT_APLICADO;
        repo.registrarProveedor(id,aplicado,esperado);
        var evidencia=repo.evidencia(id).orElseThrow();
        GestoPagoTransport.Reply response;
        if(escenario==Escenario.TIMEOUT_APLICADO || escenario==Escenario.TIMEOUT_NO_APLICADO){
            response=new GestoPagoTransport.Reply(504,"");
        }else if(!evidencia.aplicado()){
            // 05 es SOLO una convención de este simulador. No habilita rechazos reales de GestoPago.
            response=new GestoPagoTransport.Reply(200,"<RESPONSE><MENSAJE><CODIGO>05</CODIGO></MENSAJE></RESPONSE>");
        }else{
            response=new GestoPagoTransport.Reply(200,"<RESPONSE><MENSAJE><CODIGO>01</CODIGO></MENSAJE><ID_TX>"+evidencia.idTx()
                    +"</ID_TX><NUM_AUTORIZACION>SIM"+evidencia.idTx()+"</NUM_AUTORIZACION><MONTO>"+esperado.toPlainString()+"</MONTO></RESPONSE>");
        }
        var result=respuestas.parse(response,esperado);
        if(result.state()==GestoPagoOutcomeParser.State.CONFIRMED)return Estado.CAPTURED;
        if(response.status()==200 && "05".equals(result.code()))return Estado.FAILED;
        return Estado.UNKNOWN;
    }
    public Estado consultar(UUID id,BigDecimal esperado) {
        // Consulta SOLO el libro ficticio; no implementa ni afirma verificar confirmTx real.
        return repo.evidencia(id).filter(e->e.monto().compareTo(esperado)==0)
                .map(e->e.aplicado()?Estado.CAPTURED:Estado.FAILED).orElse(Estado.UNKNOWN);
    }
    private static String encode(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static String decode(String v){return URLDecoder.decode(v,StandardCharsets.UTF_8);}
}
