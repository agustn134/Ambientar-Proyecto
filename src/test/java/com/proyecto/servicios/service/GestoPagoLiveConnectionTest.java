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
        properties={"gestopago.auth.background-enabled=false", "gestopago.payment.enabled=false",
                "gestopago.reference.enabled=${GESTOPAGO_LIVE_REFERENCE:false}"})
class GestoPagoLiveConnectionTest {
    @MockBean GestoPagoProductScheduler scheduler;
    @Autowired GestoPagoTokenService tokens;
    @Autowired GestoPagoProductService productos;
    @Autowired org.springframework.jdbc.core.JdbcTemplate sql;
    @Autowired com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier references;
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
        // Esta comprobación no debe emitir otro token ni renovar el existente.
        assertTrue(Boolean.TRUE.equals(sql.queryForObject(
                "SELECT expira > now() + interval '5 minutes' FROM gestopago_token_cache WHERE distribuidor=? AND dispositivo=?",
                Boolean.class,distributor,device)), "Se requiere una sesión guardada con vigencia suficiente");
        String stored=tokenGuardado();
        var catalogo=productos.consultarCatalogoGestoPago();
        assertNotNull(catalogo);
        assertNotNull(catalogo.getMensaje());
        assertEquals("01",catalogo.getMensaje().getCodigo());
        assertNotNull(catalogo.getProductos());
        assertFalse(catalogo.getProductos().isEmpty());
        assertTrue(stored.equals(tokenGuardado()), "Consultar catálogo debe reutilizar el token guardado");
        System.out.printf("GESTOPAGO_CATALOGO_VALIDADO codigo=01 productos=%d tokenReutilizado=true%n",catalogo.getProductos().size());
    }
    private String tokenGuardado() {
        return sql.queryForObject("SELECT token_cifrado FROM gestopago_token_cache WHERE distribuidor=? AND dispositivo=?",
                String.class,distributor,device);
    }
    @Test
    @EnabledIfEnvironmentVariable(named="GESTOPAGO_LIVE_REFERENCE", matches="true")
    void verificaCifradoConReferenciaValidaAutorizadaSinPago() {
        String servicio=System.getenv("GESTOPAGO_REFERENCE_TEST_SERVICE");
        String producto=System.getenv("GESTOPAGO_REFERENCE_TEST_PRODUCT");
        String referencia=System.getenv("GESTOPAGO_REFERENCE_TEST_VALUE");
        assertTrue(servicio!=null && producto!=null && referencia!=null,"Falta un caso de referencia autorizado");
        assertEquals(com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.Resultado.VALIDA,
                references.verificar(Integer.valueOf(servicio),Integer.valueOf(producto),referencia));
    }
}
