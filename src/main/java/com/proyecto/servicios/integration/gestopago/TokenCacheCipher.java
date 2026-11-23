package com.proyecto.servicios.integration.gestopago;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Protección local AES-GCM, independiente de la clave y el IV de GestoPago. */
@Component
public final class TokenCacheCipher {
    private final String key;
    public TokenCacheCipher(@Value("${gestopago.token-cache-key-base64:}") String key) { this.key = key; }
    public String encrypt(String value) { return transform(value, true); }
    public String decrypt(String value) { return transform(value, false); }
    private String transform(String value, boolean encrypt) {
        try {
            byte[] rawKey = Base64.getDecoder().decode(key);
            if (rawKey.length != 32) throw new IllegalArgumentException();
            byte[] nonce = new byte[12];
            byte[] data;
            if (encrypt) {
                new SecureRandom().nextBytes(nonce);
                data = value.getBytes(StandardCharsets.UTF_8);
            } else {
                var parts = value.split("\\.");
                if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
                nonce = Base64.getDecoder().decode(parts[1]);
                if (nonce.length != 12) throw new IllegalArgumentException();
                data = Base64.getDecoder().decode(parts[2]);
            }
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(rawKey, "AES"), new GCMParameterSpec(128, nonce));
            var result = cipher.doFinal(data);
            return encrypt ? "v1." + Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(result)
                    : new String(result, StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Caché segura de GestoPago no disponible."); }
    }
}
