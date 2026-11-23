package com.proyecto.servicios.service;

import com.proyecto.servicios.integration.gestopago.GestoPagoPayloadCipher;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class GestoPagoPayloadCipherTest {
    @Test void coincideConVectorIndependienteDotNet() {
        // Clave/IV públicos de prueba; salida calculada independientemente con .NET AES CBC PKCS7.
        var hex = java.util.HexFormat.of();
        String key = Base64.getEncoder().encodeToString(hex.parseHex(
                "603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4"));
        String iv = Base64.getEncoder().encodeToString(hex.parseHex("000102030405060708090a0b0c0d0e0f"));
        var cipher = new GestoPagoPayloadCipher(key, iv);
        assertEquals("r3mC9nj0mL4FPw7Aa8VHyvgdq5Ai8QCZkc31oCqGSl0=", cipher.cifrar("0123456789abcdef"));
        assertEquals(32, Base64.getDecoder().decode(cipher.cifrar("0123456789abcdef")).length);
    }
    @Test void rechazaSecretosAusentesOInvalidosSinExponerlos() {
        var cipher = new GestoPagoPayloadCipher("secreto-invalido", "iv-invalido");
        var error = assertThrows(IllegalStateException.class, () -> cipher.cifrar("payload sensible"));
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("secreto-invalido"));
        assertThrows(IllegalStateException.class, () -> new GestoPagoPayloadCipher("", "").cifrar("{}"));
    }
}
