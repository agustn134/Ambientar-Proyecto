package com.proyecto.servicios.service.simulacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.exception.SimulacionException;
import com.proyecto.servicios.integration.gestopago.TokenCacheCipher;
import com.proyecto.servicios.integration.simulacion.ProveedorSimulado;
import com.proyecto.servicios.model.gestopago.simulacion.SimulacionDtos.*;
import com.proyecto.servicios.repositorys.simulacion.SimulacionRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Profile("simulacion")
public class PagoSimuladoService {
    private final SimulacionRepository repo;
    private final ProveedorSimulado proveedor;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final TokenCacheCipher storage;
    public PagoSimuladoService(SimulacionRepository repo,ProveedorSimulado proveedor,ObjectMapper mapper,
            Validator validator,@Value("${simulacion.storage-key-base64}") String key) {
        this.repo=repo;this.proveedor=proveedor;this.mapper=mapper;this.validator=validator;
        // Falla al arrancar el perfil si falta protección local. No usa la clave AES pública para persistir datos.
        try{if(Base64.getDecoder().decode(key).length!=32)throw new IllegalArgumentException();}
        catch(Exception ex){throw new IllegalStateException("Configura SIMULACION_STORAGE_KEY_BASE64 con 32 bytes Base64.");}
        storage=new TokenCacheCipher(key);
    }
    public List<Producto> productos(){return List.of(
        new Producto(78,219,1,"Recarga Oui Movil 40 (simulada)",new BigDecimal("40.00"),new BigDecimal("40.00"),false,null),
        new Producto(9900,990001,4,"Servicio ficticio con referencia",new BigDecimal("10.00"),new BigDecimal("5000.00"),true,"SIM0000001"));}
    public Saldo saldo(long usuario,long cuenta){return repo.saldo(usuario,cuenta);}
    public Saldo fondear(long usuario,long cuenta,Fondeo request){
        validarBean(request);return repo.fondear(usuario,cuenta,request.monto());
    }
    public Pago crear(long usuario,String key,Solicitud request){
        validarBean(request);
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,128}"))throw new SimulacionException(400,"IDEMPOTENCIA_INVALIDA");
        var p=request.pago();
        var producto=productos().stream().filter(c->c.idProducto()==p.idProducto() && c.idServicio()==p.idServicio())
                .findFirst().orElseThrow(()->new SimulacionException(422,"PRODUCTO_SIMULADO_NO_HABILITADO"));
        if(p.montoPago().compareTo(producto.minimo())<0 || p.montoPago().compareTo(producto.maximo())>0)
            throw new SimulacionException(422,"MONTO_NO_PERMITIDO");
        if(p.telefono()==null || !p.telefono().matches("[0-9]{10}"))throw new SimulacionException(422,"TELEFONO_INVALIDO");
        if(producto.requiereReferencia()){
            if(p.referencia()==null || !p.referencia().matches("SIM[0-9]{7}"))throw new SimulacionException(422,"REFERENCIA_INVALIDA");
        }else if(p.referencia()!=null && !p.referencia().isEmpty())throw new SimulacionException(422,"REFERENCIA_NO_PERMITIDA");
        var json=mapper.createObjectNode().put("cuentaId",request.cuentaId()).put("idServicio",p.idServicio())
                .put("idProducto",p.idProducto()).put("montoPago",p.montoPago().setScale(2))
                .put("telefono",p.telefono()).put("referencia",p.referencia()==null?"":p.referencia())
                .put("escenario",request.escenario().name());
        String huella=huella(json.toString());
        var anterior=repo.porClave(usuario,request.cuentaId(),key,huella);
        if(anterior!=null)return anterior;
        if(producto.requiereReferencia()){
            var resultado=proveedor.verificarReferencia(json.toString());
            if(resultado==com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.Resultado.INDETERMINADA)
                throw new SimulacionException(503,"REFERENCIA_NO_CONCLUYENTE");
            if(resultado!=com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.Resultado.VALIDA)
                throw new SimulacionException(422,"REFERENCIA_INVALIDA");
        }
        UUID id=UUID.randomUUID();
        String upc=new java.math.BigInteger(id.toString().replace("-",""),16).mod(java.math.BigInteger.TEN.pow(15)).toString();
        upc="0".repeat(15-upc.length())+upc;
        json.put("horaLocal",LocalDateTime.now(ZoneId.of("America/Mexico_City")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                .put("upc",upc).put("unidad",1);
        try{return repo.reservar(usuario,request.cuentaId(),key,huella,p.montoPago(),id,upc,storage.encrypt(json.toString()));}
        catch(DuplicateKeyException ex){
            var pago=repo.porClave(usuario,request.cuentaId(),key,huella);
            if(pago!=null)return pago;throw new SimulacionException(409,"COLISION_IDENTIFICADOR");
        }
    }
    public Pago consultar(long usuario,UUID id){return repo.consultar(usuario,id);}
    public Pago procesar(long usuario,UUID id){
        ejecutar(repo.reclamar(usuario,id));return repo.consultar(usuario,id);
    }
    public void procesarPendientes(){for(var id:repo.pendientes())ejecutar(repo.reclamarInterno(id));}
    private void ejecutar(SimulacionRepository.Pendiente pendiente){
        if(pendiente==null)return;
        Estado resultado;
        try{resultado=proveedor.enviar(pendiente.id(),storage.decrypt(pendiente.cifrado()),pendiente.monto());}
        catch(Exception ex){resultado=Estado.UNKNOWN;}
        repo.finalizar(pendiente.id(),resultado);
    }
    public Pago conciliar(long usuario,UUID id){
        var pago=repo.consultar(usuario,id);
        if(pago.estado()==Estado.UNKNOWN)repo.finalizar(id,proveedor.consultar(id,pago.monto()));
        return repo.consultar(usuario,id);
    }
    public void recuperarInciertos(){repo.recuperarInciertos();}
    private void validarBean(Object value){if(value==null || !validator.validate(value).isEmpty())throw new SimulacionException(400,"DATOS_INVALIDOS");}
    private static String huella(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException("No fue posible preparar la operación simulada.");}
    }
}
