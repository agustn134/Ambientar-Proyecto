package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.service.Impl.GestoPagoTokenServiceImpl;
import com.proyecto.servicios.integration.gestopago.GestoPagoTransport;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoSecureTokenCache;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoSecureTokenCache.Token;
import com.proyecto.servicios.exception.GestoPagoAuthException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class GestoPagoTokenManagerTest {
    final GestoPagoTransport transport=mock(GestoPagoTransport.class);
    final GestoPagoSecureTokenCache cache=mock(GestoPagoSecureTokenCache.class);
    final GestoPagoTokenServiceImpl service=new GestoPagoTokenServiceImpl(transport,cache,new ObjectMapper());
    GestoPagoTokenManagerTest(){
        ReflectionTestUtils.setField(service,"idDistribuidor",83);
        ReflectionTestUtils.setField(service,"codigoDispositivo","test-device");
        ReflectionTestUtils.setField(service,"basic","Basic sintetico");
        ReflectionTestUtils.setField(service,"password","sintetico");
        ReflectionTestUtils.setField(service,"url","https://proveedor.example");
        ReflectionTestUtils.setField(service,"endpoint","/auth");
    }
    @Test void cacheVigenteNoSolicitaOtroToken(){
        when(cache.get(eq(83),eq("test-device"),any(),any())).thenReturn(new Token("sintetico",Instant.now().plusSeconds(3600)));
        assertEquals("sintetico",service.obtenerTokenActivo(83,"test-device").orElseThrow().getToken());
        verifyNoInteractions(transport);
    }
    @SuppressWarnings("unchecked")
    @Test void autenticaPorFormularioSinSecretosEnUrl(){
        when(cache.get(eq(83),eq("test-device"),any(),any())).thenAnswer(call -> ((Supplier<Token>)call.getArgument(3)).get());
        when(transport.post(any(),anyMap(),anyMap())).thenAnswer(call -> {
            assertEquals("https://proveedor.example/auth",call.getArgument(0).toString());
            assertEquals("sintetico",((java.util.Map<?,?>)call.getArgument(2)).get("password"));
            return new GestoPagoTransport.Reply(200,"{\"token\":\"sintetico\",\"expires_in\":86400}");
        });
        assertTrue(service.obtenerTokenActivo(83,"test-device").isPresent());
    }
    @Test void errorNoFiltraCausaYDispositivoAjenoNoAccede(){
        when(cache.get(eq(83),eq("test-device"),any(),any())).thenThrow(new IllegalStateException("token privado"));
        var error=assertThrows(GestoPagoAuthException.class,()->service.obtenerTokenActivo(83,"test-device"));
        assertFalse(error.getMessage().contains("privado"));assertNull(error.getCause());
        assertTrue(service.obtenerTokenActivo(83,"otro").isEmpty());
    }
    @SuppressWarnings("unchecked")
    @Test void basicNoEsObligatorioYApiKeySoloSeEnviaSiFueAsignada(){
        ReflectionTestUtils.setField(service,"basic","");
        ReflectionTestUtils.setField(service,"apiKey","api-key-sintetica");
        when(cache.get(eq(83),eq("test-device"),any(),any())).thenAnswer(call -> ((Supplier<Token>)call.getArgument(3)).get());
        when(transport.post(any(),anyMap(),anyMap())).thenAnswer(call -> {
            java.util.Map<?,?> headers=call.getArgument(1);
            assertFalse(headers.containsKey("Authorization"));
            assertEquals("api-key-sintetica",headers.get("X-API-Key"));
            return new GestoPagoTransport.Reply(200,"{\"token\":\"sintetico\",\"success\":true}");
        });
        assertTrue(service.obtenerTokenActivo(83,"test-device").isPresent());
    }
}
