package com.proyecto.servicios.integration.gestopago;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Cifrado del contrato documentado; no transmite ni registra el payload. */
@Component
public final class GestoPagoPayloadCipher {
    private final String secret;
    private final String iv;
    public GestoPagoPayloadCipher(
            @Value("${gestopago.crypto.secret-base64:}") String secret,
            @Value("${gestopago.crypto.iv-base64:}") String iv) {
        this.secret = secret;
        this.iv = iv;
    }

    public String cifrar(String json) {
        try {
            byte[] clave = Base64.getDecoder().decode(secret);
            byte[] vector = Base64.getDecoder().decode(iv);
            if (clave.length != 32 || vector.length != 16 || json == null || json.isBlank()) {
                throw new IllegalArgumentException();
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave, "AES"), new IvParameterSpec(vector));
            return Base64.getEncoder().encodeToString(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // No conservar causa: puede incluir detalles de configuración o entrada.
            throw new IllegalStateException("No fue posible preparar el payload de GestoPago.");
        }
    }
}
