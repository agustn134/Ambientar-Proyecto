package com.proyecto.servicios.integration.gestopago;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class GestoPagoPaymentClientTest {
    final GestoPagoTransport transport=mock(GestoPagoTransport.class);
    final GestoPagoPayloadCipher cipher=mock(GestoPagoPayloadCipher.class);
    final GestoPagoTokenService tokens=mock(GestoPagoTokenService.class);
    final GestoPagoPaymentClient client=new GestoPagoPaymentClient(transport,cipher,tokens);
    @Test void bloqueadoPorDefectoSinCifrarNiObtenerToken(){
        assertThrows(IllegalStateException.class,()->client.preparar("{}"));
        verifyNoInteractions(transport,tokens,cipher);
    }
    @Test void transporteNoReintentaDespuesDeUnFallo(){
        ReflectionTestUtils.setField(client,"enabled",true);ReflectionTestUtils.setField(client,"url","https://proveedor.example");
        ReflectionTestUtils.setField(client,"distributor",83);ReflectionTestUtils.setField(client,"device","test");
        ReflectionTestUtils.setField(client,"apiKey","");
        when(cipher.cifrar(anyString())).thenReturn("cifrado");var token=new GestoPagoToken();token.setToken("sintetico");
        when(tokens.obtenerTokenActivo(83,"test")).thenReturn(Optional.of(token));
        when(transport.post(any(),anyMap(),anyMap())).thenThrow(new IllegalStateException("detalle privado"));
        var prepared=client.preparar("{\"upc\":\"ABC123\"}");
        assertThrows(IllegalStateException.class,()->client.ejecutar(prepared,false));
        verify(transport,times(1)).post(any(),anyMap(),argThat(form->form.containsKey("signed")&&form.containsKey("random")));
    }
}
