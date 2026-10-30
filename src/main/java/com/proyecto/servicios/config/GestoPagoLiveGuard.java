package com.proyecto.servicios.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Base64;

/** Falla antes de publicar rutas financieras si el entorno no fue preparado. */
@Component
@Profile("live | prod")
public class GestoPagoLiveGuard {
    public GestoPagoLiveGuard(Environment env) {
        if(env.matchesProfiles("simulacion")) fail();
        for(String flag : new String[]{"gestopago.payment.enabled","gestopago.reference.enabled"})
            if(!env.getProperty(flag,Boolean.class,false)) fail();
        for(String name : new String[]{"gestopago.service.url","gestopago.auth.url"}) {
            try {
                URI uri=URI.create(env.getRequiredProperty(name));
                if(!"https".equals(uri.getScheme()) || !"gestopago.portalventas.net".equals(uri.getHost())
                        || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                        || uri.getPort()!=-1 || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) fail();
            } catch(RuntimeException ex) { fail(); }
        }
        String aes=key(env,"gestopago.crypto.secret-base64",32);
        key(env,"gestopago.crypto.iv-base64",16);
        String storage=key(env,"gestopago.payment.storage-key-base64",32);
        String cache=key(env,"gestopago.token-cache-key-base64",32);
        if(aes.equals("HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=")
                || aes.equals(storage) || aes.equals(cache) || storage.equals(cache)) fail();
        if(env.getProperty("gestopago.auth.password","").isBlank()
                || !env.getProperty("gestopago.payment.unidad","").matches("[A-Za-z0-9_-]{3,25}")) fail();
    }
    private static String key(Environment env,String name,int length) {
        try {
            String value=env.getRequiredProperty(name);
            if(Base64.getDecoder().decode(value).length!=length) fail();
            return value;
        } catch(RuntimeException ex) { fail(); return ""; }
    }
    private static void fail() { throw new IllegalStateException("Perfil financiero incompleto o incompatible. Revisa los secretos y permisos de GestoPago."); }
}
