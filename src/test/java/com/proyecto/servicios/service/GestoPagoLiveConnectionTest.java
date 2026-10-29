package com.proyecto.servicios.service;

import com.proyecto.servicios.scheduler.GestoPagoProductScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in manual. El catálogo requiere una autorización adicional; nunca invoca sendTx. */
@EnabledIfEnvironmentVariable(named="GESTOPAGO_LIVE_CHECK", matches="true")
@SpringBootTest(classes=com.proyecto.servicios.App.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"gestopago.auth.background-enabled=false", "gestopago.payment.enabled=false"})
class GestoPagoLiveConnectionTest {
    @MockBean GestoPagoProductScheduler scheduler;
    @Autowired GestoPagoTokenService tokens;
    @Autowired GestoPagoProductService productos;
    @Autowired org.springframework.jdbc.core.JdbcTemplate sql;
    @Value("${gestopago.auth.id-distribuidor}") Integer distributor;
    @Value("${gestopago.auth.codigo-dispositivo}") String device;
    @Test void autenticaYReutilizaTokenCifradoSinConsultarCatalogo() {
        var first=tokens.obtenerTokenActivo(distributor,device).orElseThrow();
        String stored=tokenGuardado();
        assertTrue(stored.startsWith("v1."), "La caché debe utilizar el formato cifrado");
        assertFalse(stored.contains(first.getToken()), "La caché no debe contener el token en claro");
        var second=tokens.obtenerTokenActivo(distributor,device).orElseThrow();
        assertTrue(first.getToken().equals(second.getToken()), "Debe reutilizar la sesión del proveedor");
        assertTrue(stored.equals(tokenGuardado()), "Reutilizar el token no debe generar otra credencial ni reescribir la caché");
    }
    @Test
    @EnabledIfEnvironmentVariable(named="GESTOPAGO_LIVE_CATALOG", matches="true")
    void consultaCatalogoUnaVez() {
        var catalogo=productos.consultarCatalogoGestoPago();
        assertNotNull(catalogo);
        assertNotNull(catalogo.getMensaje());
        assertEquals("01",catalogo.getMensaje().getCodigo());
        assertNotNull(catalogo.getProductos());
        assertFalse(catalogo.getProductos().isEmpty());
    }
    private String tokenGuardado() {
        return sql.queryForObject("SELECT token_cifrado FROM gestopago_token_cache WHERE distribuidor=? AND dispositivo=?",
                String.class,distributor,device);
    }
}
