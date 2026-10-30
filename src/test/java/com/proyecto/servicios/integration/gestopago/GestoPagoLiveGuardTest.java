package com.proyecto.servicios.integration.gestopago;

import com.proyecto.servicios.config.GestoPagoLiveGuard;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class GestoPagoLiveGuardTest {
    static String key(int size,int value){byte[] bytes=new byte[size];java.util.Arrays.fill(bytes,(byte)value);return Base64.getEncoder().encodeToString(bytes);}
    MockEnvironment env(){return new MockEnvironment().withProperty("gestopago.payment.enabled","true").withProperty("gestopago.reference.enabled","true")
            .withProperty("gestopago.service.url","https://gestopago.portalventas.net").withProperty("gestopago.auth.url","https://gestopago.portalventas.net")
            .withProperty("gestopago.crypto.secret-base64",key(32,1)).withProperty("gestopago.crypto.iv-base64",key(16,2))
            .withProperty("gestopago.payment.storage-key-base64",key(32,3)).withProperty("gestopago.token-cache-key-base64",key(32,4))
            .withProperty("gestopago.auth.password","solo-prueba-controlada").withProperty("gestopago.payment.unidad","QA1");}
    @Test void aceptaConfiguracionCompleta(){assertDoesNotThrow(()->new GestoPagoLiveGuard(env()));}
    @Test void simulacionNuncaSeCombinaConLive(){var e=env();e.setActiveProfiles("live","simulacion");assertThrows(IllegalStateException.class,()->new GestoPagoLiveGuard(e));}
    @Test void rechazaFaltaDeSecretosFlagsYHostIncorrecto(){
        for(var name:new String[]{"gestopago.auth.password","gestopago.payment.unidad","gestopago.crypto.secret-base64","gestopago.crypto.iv-base64","gestopago.payment.storage-key-base64","gestopago.token-cache-key-base64"})
            assertThrows(IllegalStateException.class,()->new GestoPagoLiveGuard(env().withProperty(name,"")));
        assertThrows(IllegalStateException.class,()->new GestoPagoLiveGuard(env().withProperty("gestopago.payment.enabled","false")));
        assertThrows(IllegalStateException.class,()->new GestoPagoLiveGuard(env().withProperty("gestopago.reference.enabled","false")));
        for(String url:new String[]{"http://gestopago.portalventas.net","https://otro.example","https://gestopago.portalventas.net@otro.example","https://gestopago.portalventas.net:444"})
            assertThrows(IllegalStateException.class,()->new GestoPagoLiveGuard(env().withProperty("gestopago.service.url",url)));
    }
    @Test void noAdmiteClavePublicaNiReutilizarClaveLocal(){
        assertThrows(IllegalStateException.class,()->new GestoPagoLiveGuard(env().withProperty("gestopago.crypto.secret-base64","HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=")));
        assertThrows(IllegalStateException.class,()->new GestoPagoLiveGuard(env().withProperty("gestopago.payment.storage-key-base64",key(32,1))));
    }
}
