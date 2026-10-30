package com.proyecto.servicios;

import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.PagoSimuladoController;
import com.proyecto.servicios.exception.SimulacionException;
import com.proyecto.servicios.model.gestopago.simulacion.SimulacionDtos.*;
import com.proyecto.servicios.security.SecurityErrorHandler;
import com.proyecto.servicios.service.simulacion.PagoSimuladoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PagoSimuladoController.class)
@ActiveProfiles("simulacion")
@ContextConfiguration(classes={PagoSimuladoController.class,SecurityConfig.class,SecurityErrorHandler.class,
        GlobalExceptionHandler.class,RegistroClienteExceptionHandler.class,RespuestaExitosaAdvice.class})
class PagoSimuladoHttpTest {
    @Autowired MockMvc mvc;
    @MockBean PagoSimuladoService pagos;
    @MockBean JwtDecoder decoder;
    static final String BODY="{\"cuentaId\":1,\"pago\":{\"idServicio\":78,\"idProducto\":219,\"montoPago\":40,\"telefono\":\"5512345678\"},\"escenario\":\"EXITO\"}";
    void autenticar(){when(decoder.decode("token-prueba")).thenReturn(Jwt.withTokenValue("token-prueba").header("alg","RS256").subject("1").claim("rol","CLIENTE").build());}
    @Test void sinSesionNoReserva()throws Exception{
        mvc.perform(post("/api/simulacion/pagos").contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());verifyNoInteractions(pagos);
    }
    @Test void devuelve202LocationYNoExponeDestinoOCifrado()throws Exception{
        autenticar();var id=UUID.randomUUID();
        when(pagos.crear(eq(1L),eq("prueba-0001"),any())).thenReturn(new Pago(id,1,new BigDecimal("40.00"),Estado.RESERVED,true,false));
        mvc.perform(post("/api/simulacion/pagos").header("Authorization","Bearer token-prueba").header("Idempotency-Key","prueba-0001").contentType("application/json").content(BODY))
                .andExpect(status().isAccepted()).andExpect(header().string("Location","/api/simulacion/pagos/"+id))
                .andExpect(jsonPath("datos.estado").value("RESERVED")).andExpect(jsonPath("datos.simulado").value(true))
                .andExpect(jsonPath("datos.enviadoAProveedorReal").value(false)).andExpect(jsonPath("datos.telefono").doesNotExist())
                .andExpect(jsonPath("datos.payloadCifrado").doesNotExist());
    }
    @Test void encabezadoYCuerpoObligatoriosDevuelven400()throws Exception{
        autenticar();
        mvc.perform(post("/api/simulacion/pagos").header("Authorization","Bearer token-prueba").contentType("application/json").content(BODY)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/simulacion/pagos").header("Authorization","Bearer token-prueba").header("Idempotency-Key","prueba-0001").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(pagos);
    }
    @Test void conflictoDeIdempotenciaDevuelve409Seguro()throws Exception{
        autenticar();when(pagos.crear(anyLong(),anyString(),any())).thenThrow(new SimulacionException(409,"IDEMPOTENCIA_CONFLICTO"));
        mvc.perform(post("/api/simulacion/pagos").header("Authorization","Bearer token-prueba").header("Idempotency-Key","prueba-0001").contentType("application/json").content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("codigo").value("IDEMPOTENCIA_CONFLICTO"));
    }
}
