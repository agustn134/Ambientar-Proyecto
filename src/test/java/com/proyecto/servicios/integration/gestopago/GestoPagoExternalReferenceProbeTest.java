package com.proyecto.servicios.integration.gestopago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.GestoPagoContratoProperties;
import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoSecureTokenCache;
import com.proyecto.servicios.service.Impl.GestoPagoTokenServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Diagnóstico externo opt-in. Solo authenticate y el caso NEGATIVO publicado de verifyReference.
 * No arranca App, no migra esquemas ni instancia clientes/servicios financieros.
 */
@EnabledIfEnvironmentVariable(named="GESTOPAGO_EXTERNAL_REFERENCE_PROBE",matches="true")
class GestoPagoExternalReferenceProbeTest {
    @Test void casoNegativoDocumentadoConAesDePruebasSinPago() throws Exception {
        var ds=new DriverManagerDataSource(env("GESTOPAGO_PROBE_DB_URL"),env("GESTOPAGO_PROBE_DB_USER"),env("DB_PASSWORD"));
        var sql=new JdbcTemplate(ds);
        var row=sql.queryForMap("SELECT id_servicio,id_cat_tipo_servicio,tipo_front,activo FROM gestopago_productos WHERE id_producto=271");
        assertEquals(107,((Number)row.get("id_servicio")).intValue(),"El ejemplo debe coincidir con el catálogo local");
        assertEquals(Boolean.TRUE,row.get("activo"),"El producto documentado debe estar activo");
        var transport=new ReadOnlyTransport();
        var cache=new GestoPagoSecureTokenCache(ds,new DataSourceTransactionManager(ds),new TokenCacheCipher(env("GESTOPAGO_TOKEN_CACHE_KEY_BASE64")));
        var mapper=new ObjectMapper();
        var tokens=new GestoPagoTokenServiceImpl(transport,cache,mapper);
        ReflectionTestUtils.setField(tokens,"idDistribuidor",83);
        ReflectionTestUtils.setField(tokens,"codigoDispositivo","GPS83-TPV-17");
        ReflectionTestUtils.setField(tokens,"password",env("GESTOPAGO_PASSWORD"));
        ReflectionTestUtils.setField(tokens,"basic",optional("GESTOPAGO_BASIC_AUTHORIZATION"));
        ReflectionTestUtils.setField(tokens,"apiKey",optional("GESTOPAGO_API_KEY"));
        ReflectionTestUtils.setField(tokens,"url","https://gestopago.portalventas.net");
        ReflectionTestUtils.setField(tokens,"endpoint","/sistema/app/jwt-gp/authenticate/");
        var token=tokens.obtenerTokenActivo(83,"GPS83-TPV-17").orElseThrow();
        String stored=sql.queryForObject("SELECT token_cifrado FROM gestopago_token_cache WHERE distribuidor=83 AND dispositivo='GPS83-TPV-17'",String.class);
        assertTrue(stored.startsWith("v1."),"La sesión externa debe estar cifrada localmente");
        assertTrue(token.getToken().equals(tokens.obtenerTokenActivo(83,"GPS83-TPV-17").orElseThrow().getToken()),"Debe reutilizar el Bearer");
        assertTrue(stored.equals(sql.queryForObject("SELECT token_cifrado FROM gestopago_token_cache WHERE distribuidor=83 AND dispositivo='GPS83-TPV-17'",String.class)),"La reutilización no debe emitir otra sesión");
        var cipher=new GestoPagoPayloadCipher(env("GESTOPAGO_PROBE_TEST_SECRET_BASE64"),env("GESTOPAGO_PROBE_TEST_IV_BASE64"));
        // Se lee metadata real; solo se sustituye el repositorio JPA para evitar arrancar/migrar App.
        var repository=mock(GestoPagoProductoRepository.class);
        var item=GestoPagoProducto.builder().idProducto(271).idServicio(107).activo(true)
                .tipoFront((String)row.get("tipo_front")).idCatTipoServicio(((Number)row.get("id_cat_tipo_servicio")).intValue()).build();
        when(repository.findByIdProducto(271)).thenReturn(Optional.of(item));
        var policies=new GestoPagoContratoProperties();var rule=new GestoPagoContratoProperties.ReglaProducto();
        rule.setHabilitado(true);rule.setIdServicio(107);rule.setTipoFront(item.getTipoFront());rule.setIdCatTipoServicio(item.getIdCatTipoServicio());
        rule.setRequiereReferencia(true);rule.setVerificacionReferenciaAprobada(true);rule.setReferenciaMinima(1);rule.setReferenciaMaxima(100);
        policies.getProductos().put(271,rule); // Autoriza SOLO esta consulta de prueba, nunca pagos.
        var referenceClient=new GestoPagoReferenceClient(transport,cipher,tokens,repository,mapper,new GestoPagoReferenceParser(),policies);
        ReflectionTestUtils.setField(referenceClient,"enabled",true);ReflectionTestUtils.setField(referenceClient,"url","https://gestopago.portalventas.net");
        ReflectionTestUtils.setField(referenceClient,"distributor",83);ReflectionTestUtils.setField(referenceClient,"device","GPS83-TPV-17");
        ReflectionTestUtils.setField(referenceClient,"apiKey",optional("GESTOPAGO_API_KEY"));
        var detail=referenceClient.verificarDetalle(107,271,"TMX00000000000000000000000");
        System.out.printf("GESTOPAGO_PRUEBA_EXTERNA adaptadorReal=true referenciaNegativa=true http=%d codigo=%s resultado=%s tipoFrontCatalogo=%s tokenReutilizado=true authRequests=%d referenceRequests=%d pagosEjecutados=0%n",
                transport.http,transport.code,detail.resultado(),row.get("tipo_front"),transport.auths,transport.references);
        assertEquals(1,transport.references,"Solo debe ejecutarse una verificación externa");
        assertEquals(200,transport.http,"La comprobación no obtuvo el HTTP documentado; no se reintenta automáticamente");
        assertEquals(GestoPagoReferenceVerifier.Resultado.INVALIDA,detail.resultado(),"El proveedor no confirmó el caso negativo documentado (CODIGO 02)");
    }
    /** Lista cerrada: el propio diagnóstico impide sendTx, confirmTx y descarga de catálogo. */
    private static class ReadOnlyTransport extends GestoPagoTransport {
        int http,auths,references;String code="NO_IDENTIFICADO";
        @Override public Reply post(URI uri,Map<String,String> headers,Map<String,String> form) {
            if(!"https".equals(uri.getScheme()) || !"gestopago.portalventas.net".equals(uri.getHost())
                    || !Set.of("/sistema/app/jwt-gp/authenticate/","/sistema/service/verifyReference.do").contains(uri.getPath()))
                throw new IllegalStateException("Ruta bloqueada por la comprobación sin pagos.");
            boolean reference=uri.getPath().endsWith("verifyReference.do");
            if(reference)references++;else auths++;
            var reply=super.post(uri,headers,form);
            if(reference){http=reply.status();var m=java.util.regex.Pattern.compile("<CODIGO>\\s*([0-9]{2})\\s*</CODIGO>").matcher(reply.body());if(m.find())code=m.group(1);}
            return reply;
        }
    }
    private static String optional(String name){return System.getenv(name)==null?"":System.getenv(name);}
    private static String env(String name){String value=optional(name);if(value.isBlank())throw new IllegalStateException("Falta una variable para la comprobación externa: "+name);return value;}
}
