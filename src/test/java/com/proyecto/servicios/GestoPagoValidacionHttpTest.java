package com.proyecto.servicios;

import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.GestoPagoValidacionController;
import com.proyecto.servicios.exception.ContratoPagoException;
import com.proyecto.servicios.model.gestopago.pago.PagoContratoValidado;
import com.proyecto.servicios.security.SecurityErrorHandler;
import com.proyecto.servicios.service.GestoPagoContratoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GestoPagoValidacionController.class)
@ContextConfiguration(classes = {GestoPagoValidacionController.class, SecurityConfig.class,
        SecurityErrorHandler.class, GlobalExceptionHandler.class, RegistroClienteExceptionHandler.class,
        RespuestaExitosaAdvice.class})
class GestoPagoValidacionHttpTest {
    @Autowired MockMvc mvc;
    @MockBean GestoPagoContratoService contrato;
    @MockBean JwtDecoder decoder;
    private static final String RUTA = "/api/gestopago/pagos/validar";
    private static final String BODY = """
            {"idServicio":78,"idProducto":219,"montoPago":40,"telefono":"5512345678"}
            """;
    private void autenticar() {
        when(decoder.decode("token-prueba")).thenReturn(Jwt.withTokenValue("token-prueba")
                .header("alg", "RS256").subject("1").claim("rol", "CLIENTE").build());
    }
    @Test void exigeAutenticacionAntesDeValidar() throws Exception {
        mvc.perform(post(RUTA).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(contrato);
    }
    @Test void devuelveSoloValidacionSinEcoDeDatosSensibles() throws Exception {
        autenticar();
        when(contrato.validar(any())).thenReturn(new PagoContratoValidado(78, 219,
                new BigDecimal("40"), "5512345678", null, false));
        mvc.perform(post(RUTA).header("Authorization", "Bearer token-prueba")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.contratoLocalValido").value(true))
                .andExpect(jsonPath("datos.pagoEnviado").value(false))
                .andExpect(jsonPath("datos.telefono").doesNotExist())
                .andExpect(jsonPath("mensaje").value("Contrato local válido. No se ha enviado ningún pago."));
    }
    @Test void reglasRechazadasDevuelven422() throws Exception {
        autenticar();
        when(contrato.validar(any())).thenThrow(new ContratoPagoException("MONTO_NO_PERMITIDO"));
        mvc.perform(post(RUTA).header("Authorization", "Bearer token-prueba")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("codigo").value("MONTO_NO_PERMITIDO"));
    }
    @Test void camposObligatoriosDevuelven400SinLlamarServicio() throws Exception {
        autenticar();
        mvc.perform(post(RUTA).header("Authorization", "Bearer token-prueba")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(contrato);
    }
}
