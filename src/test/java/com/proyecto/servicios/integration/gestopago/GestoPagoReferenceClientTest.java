package com.proyecto.servicios.integration.gestopago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.*;
import com.proyecto.servicios.config.GestoPagoContratoProperties;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.Resultado.*;

class GestoPagoReferenceClientTest {
    final GestoPagoTransport transport=mock(GestoPagoTransport.class);
    final GestoPagoPayloadCipher cipher=mock(GestoPagoPayloadCipher.class);
    final GestoPagoTokenService tokens=mock(GestoPagoTokenService.class);
    final GestoPagoProductoRepository productos=mock(GestoPagoProductoRepository.class);
    final GestoPagoReferenceParser parser=new GestoPagoReferenceParser();
    final ObjectMapper mapper=new ObjectMapper();
    final GestoPagoContratoProperties policies=new GestoPagoContratoProperties();
    final GestoPagoReferenceClient client=new GestoPagoReferenceClient(transport,cipher,tokens,productos,mapper,parser,policies);
    GestoPagoProducto item;
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(client,"enabled",true);ReflectionTestUtils.setField(client,"url","https://proveedor.example");
        ReflectionTestUtils.setField(client,"distributor",83);ReflectionTestUtils.setField(client,"device","test");
        item=GestoPagoProducto.builder().idProducto(272).idServicio(108).tipoFront("4").idCatTipoServicio(1).activo(true).build();
        var rule=new GestoPagoContratoProperties.ReglaProducto();rule.setHabilitado(true);rule.setIdServicio(108);
        rule.setTipoFront("4");rule.setIdCatTipoServicio(1);rule.setRequiereReferencia(true);rule.setVerificacionReferenciaAprobada(true);
        rule.setReferenciaMinima(5);rule.setReferenciaMaxima(40);policies.getProductos().put(272,rule);
        lenient().when(productos.findByIdProducto(272)).thenReturn(Optional.of(item));
        var token=new GestoPagoToken();token.setToken("sintetico");
        lenient().when(tokens.obtenerTokenActivo(83,"test")).thenReturn(Optional.of(token));
        lenient().when(cipher.cifrar(anyString())).thenReturn("cifrado");
    }
    @Test void cifraSoloCamposDeReferenciaYConservaSaldoReferencia() throws Exception {
        when(cipher.cifrar(anyString())).thenAnswer(call->{var json=mapper.readTree((String)call.getArgument(0));
            assertEquals(3,json.size());assertEquals("REF123",json.path("referencia").asText());assertEquals(272,json.path("idProducto").asInt());return "cifrado";});
        when(transport.post(any(),anyMap(),anyMap())).thenAnswer(call->{
            assertEquals("https://proveedor.example/sistema/service/verifyReference.do",call.getArgument(0).toString());
            assertEquals("cifrado",((Map<?,?>)call.getArgument(2)).get("signed"));
            assertEquals(Set.of("random","signed"),((Map<?,?>)call.getArgument(2)).keySet());
            return new GestoPagoTransport.Reply(200,"<RESPONSE><MENSAJE><CODIGO>01</CODIGO><SALDO>-150.00</SALDO></MENSAJE></RESPONSE>");});
        var result=client.verificarDetalle(108,272,"REF123");assertEquals(VALIDA,result.resultado());assertEquals(new BigDecimal("-150.00"),result.saldoReferencia());
        verify(transport,times(1)).post(any(),anyMap(),anyMap());
    }
    @Test void bloqueaProductoInactivoServicioDistintoYTiposNoAdmitidos(){
        item.setTipoFront("1");assertThrows(IllegalArgumentException.class,()->client.verificar(108,272,"REF123"));
        item.setTipoFront("4");item.setActivo(false);assertThrows(IllegalArgumentException.class,()->client.verificar(108,272,"REF123"));
        item.setActivo(true);assertThrows(IllegalArgumentException.class,()->client.verificar(109,272,"REF123"));
        verifyNoInteractions(transport,tokens,cipher);
    }
    @Test void timeoutEsIndeterminadoYNoSeReenvia(){
        when(transport.post(any(),anyMap(),anyMap())).thenThrow(new IllegalStateException("sensible"));
        assertEquals(INDETERMINADA,client.verificar(108,272,"REF123"));verify(transport,times(1)).post(any(),anyMap(),anyMap());
    }
    @Test void expiracionInvalidaSoloSesionUsadaSinReenvio(){
        when(transport.post(any(),anyMap(),anyMap())).thenReturn(new GestoPagoTransport.Reply(403,"{\"token\":\"EXPIRED\"}"));
        assertEquals(INDETERMINADA,client.verificar(108,272,"REF123"));verify(tokens).invalidarToken(83,"test","sintetico");
        verify(transport,times(1)).post(any(),anyMap(),anyMap());
    }
    @Test void deshabilitadoNoLlamaProveedor(){
        ReflectionTestUtils.setField(client,"enabled",false);assertThrows(RuntimeException.class,()->client.verificar(108,272,"REF123"));
        verifyNoInteractions(transport,productos,tokens,cipher);
    }
    @Test void tipo2NoSeAdmiteAunqueTengaPoliticaDeReferenciaAprobada(){
        item.setTipoFront("2");policies.getProductos().get(272).setTipoFront("2");
        assertThrows(IllegalArgumentException.class,()->client.verificar(108,272,"REF123"));verifyNoInteractions(transport,tokens,cipher);
    }
    @Test void flagGlobalNoAutorizaProductosSinPolitica(){
        policies.getProductos().get(272).setVerificacionReferenciaAprobada(false);
        assertThrows(IllegalArgumentException.class,()->client.verificar(108,272,"REF123"));
        verifyNoInteractions(transport,tokens,cipher);
    }
    @Test void parserRechazaXmlAmbiguoXxeYSaldosInvalidos(){
        for(String xml:List.of("<RESPONSE>","<!DOCTYPE RESPONSE [<!ENTITY x SYSTEM 'file:///privado'>]><RESPONSE>&x;</RESPONSE>",
                "<RESPONSE><MENSAJE><CODIGO>01</CODIGO><CODIGO>02</CODIGO></MENSAJE></RESPONSE>",
                "<RESPONSE><MENSAJE><CODIGO>01</CODIGO><SALDO>NaN</SALDO></MENSAJE></RESPONSE>"))
            assertEquals(INDETERMINADA,parser.parse(new GestoPagoTransport.Reply(200,xml)).resultado());
        assertEquals(INVALIDA,parser.parse(new GestoPagoTransport.Reply(200,"<RESPONSE><MENSAJE><CODIGO>02</CODIGO></MENSAJE></RESPONSE>")).resultado());
        assertEquals(INDETERMINADA,parser.parse(new GestoPagoTransport.Reply(500,"<RESPONSE><MENSAJE><CODIGO>01</CODIGO></MENSAJE></RESPONSE>")).resultado());
    }
}
