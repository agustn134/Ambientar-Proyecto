package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.exception.CuotaCatalogoException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;

/** Un presupuesto global por BD, conservador frente al límite por IP del proveedor. */
@Repository
public class GestoPagoCatalogoQuota {
    private final JdbcTemplate sql;
    private final TransactionTemplate tx;
    public GestoPagoCatalogoQuota(@Qualifier("sfDatasource") DataSource ds,
            @Qualifier("sfTransactionManager") PlatformTransactionManager manager) {
        sql=new JdbcTemplate(ds); tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(5);
    }
    public void reservarIntento() {
        tx.executeWithoutResult(status -> {
            // Exclusión entre Swagger, cron e instancias; se libera ANTES de HTTP.
            sql.queryForList("SELECT pg_advisory_xact_lock(734219, 11)");
            var now=sql.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();
            var attempts=sql.query("SELECT solicitada_en FROM gestopago_catalogo_consultas WHERE solicitada_en > ? ORDER BY solicitada_en",
                    (rs,row)->rs.getTimestamp(1).toInstant(),Timestamp.from(now.minus(Duration.ofHours(24))));
            if(attempts.size()>=3) {
                long retry=Math.max(1,(Duration.between(now,attempts.get(0).plus(Duration.ofHours(24))).toMillis()+999)/1000);
                throw new CuotaCatalogoException(retry);
            }
            sql.update("INSERT INTO gestopago_catalogo_consultas(solicitada_en,origen) VALUES (?,'CONSULTA')",Timestamp.from(now));
        });
    }
}
