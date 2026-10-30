package com.proyecto.servicios.integration.gestopago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@Component
public class GestoPagoReferenceClient implements GestoPagoReferenceVerifier {
    private final GestoPagoTransport transport;
    private final GestoPagoPayloadCipher cipher;
    private final GestoPagoTokenService tokens;
    private final GestoPagoProductoRepository productos;
    private final ObjectMapper mapper;
    private final GestoPagoReferenceParser parser;
    @Value("${gestopago.reference.enabled:false}") private boolean enabled;
    @Value("${gestopago.service.url}") private String url;
    @Value("${gestopago.auth.id-distribuidor}") private Integer distributor;
    @Value("${gestopago.auth.codigo-dispositivo}") private String device;
    @Value("${gestopago.api-key:}") private String apiKey="";
    public GestoPagoReferenceClient(GestoPagoTransport transport,GestoPagoPayloadCipher cipher,GestoPagoTokenService tokens,
            GestoPagoProductoRepository productos,ObjectMapper mapper,GestoPagoReferenceParser parser) {
        this.transport=transport;this.cipher=cipher;this.tokens=tokens;this.productos=productos;this.mapper=mapper;this.parser=parser;
    }
    @Override public Resultado verificar(Integer servicio,Integer producto,String referencia) {
        return verificarDetalle(servicio,producto,referencia).resultado();
    }
    @Override public Detalle verificarDetalle(Integer servicio,Integer producto,String referencia) {
        if(!enabled) throw new GestoPagoException(503,"La validación externa de referencia no está habilitada.");
        if(servicio==null || servicio<=0 || producto==null || producto<=0 || referencia==null
                || !referencia.matches("[A-Za-z0-9]{1,100}")) throw new IllegalArgumentException("Solicitud de referencia inválida.");
        var item=productos.findByIdProducto(producto).orElseThrow(()->new IllegalArgumentException("Producto no disponible."));
        if(!Boolean.TRUE.equals(item.getActivo()) || !servicio.equals(item.getIdServicio()) || !"4".equals(item.getTipoFront()))
            throw new IllegalArgumentException("El producto no admite este contrato de referencia.");
        String signed;
        try { signed=cipher.cifrar(mapper.writeValueAsString(Map.of("idProducto",producto,"idServicio",servicio,"referencia",referencia))); }
        catch(Exception error) { throw new GestoPagoException(503,"No fue posible preparar la validación de referencia."); }
        var token=tokens.obtenerTokenActivo(distributor,device).orElseThrow(()->new GestoPagoException(503,"Sesión del proveedor no disponible."));
        var headers=new HashMap<String,String>();headers.put("Authorization","Bearer "+token.getToken());
        if(!apiKey.isBlank())headers.put("X-API-Key",apiKey);
        try {
            var response=transport.post(URI.create(url+"/sistema/service/verifyReference.do"),headers,
                    Map.of("random",Long.toString(Instant.now().toEpochMilli()),"signed",signed));
            if(response.status()==403 && parser.tokenExpired(response.body()))
                tokens.invalidarToken(distributor,device,token.getToken());
            return parser.parse(response);
        } catch(RuntimeException error) { return new Detalle(Resultado.INDETERMINADA,null); }
    }
}
