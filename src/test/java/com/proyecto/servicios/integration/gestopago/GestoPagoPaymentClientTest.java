package com.proyecto.servicios.integration.gestopago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.gestopago.pago.PagoContratoValidado;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class GestoPagoPaymentClientTest {
    final GestoPagoTransport transport=mock(GestoPagoTransport.class);
    final GestoPagoPayloadCipher cipher=mock(GestoPagoPayloadCipher.class);
    final GestoPagoTokenService tokens=mock(GestoPagoTokenService.class);
    final GestoPagoPaymentClient client=new GestoPagoPaymentClient(transport,cipher,new ObjectMapper(),tokens,new GestoPagoOutcomeParser());
    final PagoContratoValidado pago=new PagoContratoValidado(78,219,new BigDecimal("40"),"5512345678",null,false);
    @Test void bloqueadoPorDefectoYReferenciaSinContrato(){
        assertThrows(IllegalStateException.class,()->client.enviar(pago,"ABC123","083"));
        ReflectionTestUtils.setField(client,"enabled",true);
        assertThrows(IllegalStateException.class,()->client.enviar(new PagoContratoValidado(78,219,new BigDecimal("40"),"5512345678","REF",true),"ABC123","083"));
        verifyNoInteractions(transport,tokens,cipher);
    }
    @Test void timeoutNoReintentaNiDevuelveFalloDefinitivo(){
        ReflectionTestUtils.setField(client,"enabled",true);ReflectionTestUtils.setField(client,"url","https://proveedor.example");
        ReflectionTestUtils.setField(client,"distributor",83);ReflectionTestUtils.setField(client,"device","test");
        ReflectionTestUtils.setField(client,"apiKey","");ReflectionTestUtils.setField(client,"zone","America/Mexico_City");
        when(cipher.cifrar(anyString())).thenReturn("cifrado");var token=new GestoPagoToken();token.setToken("sintetico");
        when(tokens.obtenerTokenActivo(83,"test")).thenReturn(Optional.of(token));
        when(transport.post(any(),anyMap(),anyMap())).thenThrow(new IllegalStateException("detalle privado"));
        assertEquals(GestoPagoOutcomeParser.State.UNKNOWN,client.enviar(pago,"ABC123","083").state());
        verify(transport,times(1)).post(any(),anyMap(),argThat(form->form.containsKey("signed")&&form.containsKey("random")));
    }
}
