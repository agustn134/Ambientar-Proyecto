package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.integration.gestopago.TokenCacheCipher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.function.Supplier;

@Repository
public class GestoPagoSecureTokenCache {
    public record Token(String value, Instant expiresAt) {
        @Override public String toString() { return "Token[protegido]"; }
    }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TokenCacheCipher cipher;
    public GestoPagoSecureTokenCache(@Qualifier("sfDatasource") DataSource ds,
            @Qualifier("sfTransactionManager") PlatformTransactionManager manager, TokenCacheCipher cipher) {
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(manager); this.cipher = cipher;
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Token get(int distributor, String device, Instant now, Supplier<Token> authenticate) {
        cipher.encrypt(""); // Validar clave local antes de emitir una nueva credencial externa.
        return tx.execute(status -> {
            // Dos instancias no pueden emitir un token simultáneamente para el mismo dispositivo.
            Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?, hashtext(?))", Boolean.class, distributor, device);
            if (!Boolean.TRUE.equals(locked)) throw new IllegalStateException("Renovación de GestoPago en curso.");
            var rows = jdbc.query("SELECT token_cifrado, expira FROM gestopago_token_cache WHERE distribuidor=? AND dispositivo=?",
                    (rs, row) -> new Token(cipher.decrypt(rs.getString(1)), rs.getTimestamp(2).toInstant()), distributor, device);
            Token old = rows.isEmpty() ? null : rows.get(0);
            if (old != null && old.expiresAt().isAfter(now.plusSeconds(300))) return old;
            Token fresh;
            try {
                fresh = authenticate.get();
                if (fresh == null || fresh.value() == null || fresh.value().isBlank()
                        || !fresh.expiresAt().isAfter(Instant.now().plusSeconds(30)))
                    throw new IllegalStateException("Token nuevo sin vigencia suficiente.");
            }
            catch (RuntimeException failure) {
                if (old != null && old.expiresAt().isAfter(Instant.now().plusSeconds(30))) return old;
                throw failure;
            }
            jdbc.update("INSERT INTO gestopago_token_cache(distribuidor,dispositivo,token_cifrado,expira) VALUES(?,?,?,?) "
                    + "ON CONFLICT(distribuidor,dispositivo) DO UPDATE SET token_cifrado=EXCLUDED.token_cifrado,expira=EXCLUDED.expira",
                    distributor, device, cipher.encrypt(fresh.value()), java.sql.Timestamp.from(fresh.expiresAt()));
            return fresh;
        });
    }
    public void invalidate(int distributor, String device, String expectedToken) {
        tx.executeWithoutResult(status -> {
            Boolean locked=jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?, hashtext(?))",Boolean.class,distributor,device);
            if(!Boolean.TRUE.equals(locked)) return; // Otra instancia está renovando; no invalidar su resultado.
            var rows=jdbc.queryForList("SELECT token_cifrado FROM gestopago_token_cache WHERE distribuidor=? AND dispositivo=?",String.class,distributor,device);
            if(!rows.isEmpty() && cipher.decrypt(rows.get(0)).equals(expectedToken))
                jdbc.update("UPDATE gestopago_token_cache SET expira=? WHERE distribuidor=? AND dispositivo=?",
                        java.sql.Timestamp.from(Instant.EPOCH),distributor,device);
        });
    }
}
