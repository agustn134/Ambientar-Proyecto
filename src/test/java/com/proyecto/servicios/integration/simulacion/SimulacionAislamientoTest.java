package com.proyecto.servicios.integration.simulacion;

import com.proyecto.servicios.controller.PagoSimuladoController;
import com.proyecto.servicios.integration.gestopago.GestoPagoTransport;
import com.proyecto.servicios.service.simulacion.PagoSimuladoService;
import com.proyecto.servicios.config.GestoPagoCatalogoFeignConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import java.net.URI;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SimulacionAislamientoTest {
    @Test void controladorFicticioNoSeRegistraEnPerfilNormal(){
        try(var context=new AnnotationConfigApplicationContext()){
            context.register(PagoSimuladoController.class);context.refresh();
            assertEquals(0,context.getBeansOfType(PagoSimuladoController.class).size());
        }
    }
    @Test void transporteRealSeBloqueaInclusoSiSeConfiguraUnaUrlHttps(){
        try(var context=new AnnotationConfigApplicationContext()){
            context.getEnvironment().setActiveProfiles("simulacion");context.register(GestoPagoTransport.class);context.refresh();
            assertThrows(IllegalStateException.class,()->context.getBean(GestoPagoTransport.class).post(URI.create("https://gestopago.portalventas.net"),Map.of(),Map.of()));
        }
    }
    @Test void catalogoRealSeBloqueaSinAbrirConexiones(){
        var env=new MockEnvironment();env.setActiveProfiles("simulacion");
        var client=new GestoPagoCatalogoFeignConfig().catalogoClient(env);
        var request=feign.Request.create(feign.Request.HttpMethod.GET,"https://gestopago.portalventas.net",Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
        assertThrows(java.io.IOException.class,()->client.execute(request,new feign.Request.Options()));
    }
}
