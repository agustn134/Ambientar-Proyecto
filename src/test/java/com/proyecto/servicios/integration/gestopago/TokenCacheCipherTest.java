package com.proyecto.servicios.integration.gestopago;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class TokenCacheCipherTest {
    @Test void cifraConNonceDistintoYDetectaAlteraciones() {
        var cipher=new TokenCacheCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var first=cipher.encrypt("token-prueba");var second=cipher.encrypt("token-prueba");
        assertNotEquals(first,second);assertFalse(first.contains("token-prueba"));
        assertEquals("token-prueba",cipher.decrypt(first));
        var parts=first.split("\\.");var bytes=Base64.getDecoder().decode(parts[2]);bytes[0]^=1;
        assertThrows(IllegalStateException.class,()->cipher.decrypt(parts[0]+"."+parts[1]+"."+Base64.getEncoder().encodeToString(bytes)));
    }
    @Test void noPermiteClaveAusenteNiTokenPlano() {
        assertThrows(IllegalStateException.class,()->new TokenCacheCipher("").encrypt("dato"));
        assertThrows(IllegalStateException.class,()->new TokenCacheCipher(Base64.getEncoder().encodeToString(new byte[32])).decrypt("texto-plano"));
    }
}
