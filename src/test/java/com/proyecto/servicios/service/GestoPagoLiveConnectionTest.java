package com.proyecto.servicios.service;

import com.proyecto.servicios.scheduler.GestoPagoProductScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in manual: una consulta de catálogo. Nunca invoca sendTx ni imprime credenciales. */
@EnabledIfEnvironmentVariable(named="GESTOPAGO_LIVE_CHECK", matches="true")
@SpringBootTest(classes=com.proyecto.servicios.App.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"gestopago.auth.background-enabled=false", "gestopago.payment.enabled=false"})
class GestoPagoLiveConnectionTest {
    @MockBean GestoPagoProductScheduler scheduler;
    @Autowired GestoPagoTokenService tokens;
    @Autowired GestoPagoProductService productos;
    @Value("${gestopago.auth.id-distribuidor}") Integer distributor;
    @Value("${gestopago.auth.codigo-dispositivo}") String device;
    @Test void autenticaReutilizaTokenYConsultaCatalogoUnaVez() {
        var first=tokens.obtenerTokenActivo(distributor,device).orElseThrow();
        var second=tokens.obtenerTokenActivo(distributor,device).orElseThrow();
        assertTrue(first.getToken().equals(second.getToken()), "Debe reutilizar la sesión del proveedor");
        var catalogo=productos.consultarCatalogoGestoPago();
        assertNotNull(catalogo);
        assertNotNull(catalogo.getMensaje());
        assertEquals("01",catalogo.getMensaje().getCodigo());
        assertNotNull(catalogo.getProductos());
        assertFalse(catalogo.getProductos().isEmpty());
    }
}
