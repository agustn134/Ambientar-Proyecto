package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.GestoPagoContratoProperties;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.integration.gestopago.*;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.*;
import com.proyecto.servicios.repositorys.gestopago.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Profile("(live | prod) & !simulacion")
public class PagoService {
    private final PagoRepository repo;
    private final GestoPagoContratoService contratos;
    private final GestoPagoContratoProperties politicas;
    private final GestoPagoProductoRepository productos;
    private final GestoPagoReferenceVerifier referencias;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final TokenCacheCipher storage;
    private final String unidad;
    private final long catalogMaxAge;
    public PagoService(PagoRepository repo,GestoPagoContratoService contratos,GestoPagoContratoProperties politicas,
            GestoPagoProductoRepository productos,GestoPagoReferenceVerifier referencias,Validator validator,ObjectMapper mapper,
            @Value("${gestopago.payment.storage-key-base64:}") String key,
            @Value("${gestopago.payment.unidad:}") String unidad,
            @Value("${gestopago.payment.catalog-max-age-hours:24}") long catalogMaxAge) {
        this.repo=repo;this.contratos=contratos;this.politicas=politicas;this.productos=productos;this.referencias=referencias;
        this.validator=validator;this.mapper=mapper;this.storage=new TokenCacheCipher(key);this.unidad=unidad;this.catalogMaxAge=catalogMaxAge;
        if(catalogMaxAge<1 || catalogMaxAge>168)throw new IllegalStateException("Vigencia de catálogo inválida.");
    }
    public Pago crear(long usuario,String key,Solicitud request) {
        if(request==null || !validator.validate(request).isEmpty())throw new PagoException(400,"DATOS_INVALIDOS");
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}"))throw new PagoException(400,"IDEMPOTENCIA_INVALIDA");
        String huella=huella(request);
        var previo=repo.porClave(usuario,request.cuentaId(),key,huella);if(previo!=null)return previo;
        var pago=contratos.validar(request.pago());
        validarPolitica(pago.idProducto());
        var regla=politicas.getProductos().get(pago.idProducto());
        var producto=productos.findByIdProducto(pago.idProducto()).orElseThrow(()->new PagoException(422,"PRODUCTO_NO_DISPONIBLE"));
        // Autoriza identidad/cuenta antes de cualquier llamada externa. Se vuelve a comprobar al reservar/enviar.
        repo.prevalidar(usuario,request.cuentaId(),pago.montoPago());
        if(pago.requiereVerificacionReferencia()) {
            var resultado=referencias.verificar(pago.idServicio(),pago.idProducto(),pago.referencia());
            if(resultado==GestoPagoReferenceVerifier.Resultado.INVALIDA)throw new PagoException(422,"REFERENCIA_INVALIDA");
            if(resultado!=GestoPagoReferenceVerifier.Resultado.VALIDA)throw new PagoException(503,"REFERENCIA_NO_CONCLUYENTE");
        }
        UUID id=UUID.randomUUID();
        String upc=new BigInteger(id.toString().replace("-",""),16).mod(BigInteger.TEN.pow(15)).toString();
        upc="0".repeat(15-upc.length())+upc;
        var json=mapper.createObjectNode().put("idProducto",pago.idProducto()).put("idServicio",pago.idServicio())
                .put("telefono",pago.telefono()).put("montoPago",pago.montoPago().setScale(2))
                .put("horaLocal",LocalDateTime.now(ZoneId.of("America/Mexico_City")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                .put("upc",upc).put("unidad",unidad);
        if(pago.referencia()!=null)json.put("referencia",pago.referencia());
        try {
            return repo.reservar(usuario,request.cuentaId(),key,huella,pago.montoPago(),id,upc,pago.idServicio(),pago.idProducto(),
                    producto.getFechaActualizacion(),storage.encrypt(json.toString()),regla.isBloqueoServicioMonto24h());
        } catch(DuplicateKeyException ex) {
            var existente=repo.porClave(usuario,request.cuentaId(),key,huella);
            if(existente!=null)return existente;
            throw new PagoException(409,"COLISION_IDENTIFICADOR");
        }
    }
    public void validarPolitica(int producto) {
        var regla=politicas.getProductos().get(producto);
        if(regla==null || !regla.isHabilitado() || !regla.isIntegracionAprobada() || !regla.isServicioActivo())
            throw new PagoException(422,"INTEGRACION_PRODUCTO_NO_AUTORIZADA");
        var item=productos.findByIdProducto(producto).orElseThrow(()->new PagoException(422,"PRODUCTO_NO_DISPONIBLE"));
        var fecha=item.getFechaActualizacion();
        if(fecha==null || fecha.isBefore(LocalDateTime.now().minusHours(catalogMaxAge)) || fecha.isAfter(LocalDateTime.now().plusMinutes(5)))
            throw new PagoException(422,"CATALOGO_VENCIDO");
        if(!Boolean.TRUE.equals(item.getActivo()) || !Objects.equals(item.getIdServicio(),regla.getIdServicio())
                || !Objects.equals(item.getTipoFront(),regla.getTipoFront()) || !Objects.equals(item.getIdCatTipoServicio(),regla.getIdCatTipoServicio())
                || (regla.getMontoFijo()!=null && (item.getPrecio()==null || item.getPrecio().compareTo(regla.getMontoFijo())!=0)))
            throw new PagoException(422,"CATALOGO_CAMBIO");
        if(regla.isRequiereReferencia() && (!regla.isVerificacionReferenciaAprobada() || !"4".equals(item.getTipoFront()) || !regla.isBloqueoServicioMonto24h()))
            throw new PagoException(422,"CONTRATO_REFERENCIA_NO_SOPORTADO");
    }
    public Pago consultar(long usuario,UUID id){return repo.consultar(usuario,id);}
    public Saldo saldo(long usuario,long cuenta){return repo.saldo(usuario,cuenta);}
    private String huella(Solicitud r) {
        var p=r.pago();
        var json=mapper.createObjectNode().put("cuenta",r.cuentaId()).put("servicio",p.idServicio()).put("producto",p.idProducto())
                .put("monto",p.montoPago().setScale(2)).put("telefono",p.telefono()).put("referencia",p.referencia());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("No fue posible identificar la operación.");}
    }
}
