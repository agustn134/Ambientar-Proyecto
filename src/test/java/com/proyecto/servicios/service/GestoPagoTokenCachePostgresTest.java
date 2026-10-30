package com.proyecto.servicios.service;

import com.proyecto.servicios.integration.gestopago.TokenCacheCipher;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoSecureTokenCache;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoSecureTokenCache.Token;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
class GestoPagoTokenCachePostgresTest {
    @Test void dosInstanciasCompartenTokenCifradoYNoRenuevanSimultaneamente() throws Exception {
        String schema="test_token_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("POSTGRES_TEST_URL"), user=System.getenv("POSTGRES_TEST_USER"), password=System.getenv("POSTGRES_TEST_PASSWORD");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        admin.execute("CREATE SCHEMA "+schema);
        try {
            var ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__create_gestopago_tokens.sql"),
                    new ClassPathResource("db/migration/V10__gestopago_token_cache_seguro.sql")).execute(ds);
            var cipher=new TokenCacheCipher(Base64.getEncoder().encodeToString(new byte[32]));
            var first=new GestoPagoSecureTokenCache(ds,new DataSourceTransactionManager(ds),cipher);
            var second=new GestoPagoSecureTokenCache(ds,new DataSourceTransactionManager(ds),cipher);
            var started=new CountDownLatch(1);var release=new CountDownLatch(1);var issued=new AtomicInteger();
            var executor=Executors.newSingleThreadExecutor();
            try {
                var future=executor.submit(()->first.get(83,"test",Instant.now(),()->{
                    issued.incrementAndGet();started.countDown();
                    try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException();}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException();}
                    return new Token("sintetico",Instant.now().plusSeconds(86400));
                }));
                assertTrue(started.await(5,TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class,()->second.get(83,"test",Instant.now(),()->{issued.incrementAndGet();throw new IllegalStateException();}));
                release.countDown(); assertEquals("sintetico",future.get(5,TimeUnit.SECONDS).value());
                assertEquals("sintetico",second.get(83,"test",Instant.now(),()->{throw new AssertionError("No debe renovar");}).value());
                assertEquals(1,issued.get());
                assertNotEquals("sintetico",new JdbcTemplate(ds).queryForObject("SELECT token_cifrado FROM gestopago_token_cache",String.class));
                var sql=new JdbcTemplate(ds);
                sql.update("UPDATE gestopago_token_cache SET expira=?",java.sql.Timestamp.from(Instant.now().plusSeconds(120)));
                assertEquals("sintetico",second.get(83,"test",Instant.now(),()->{throw new IllegalStateException("sin red");}).value());
                second.invalidate(83,"test","token-otro");
                assertTrue(sql.queryForObject("SELECT expira > now() FROM gestopago_token_cache",Boolean.class));
                second.invalidate(83,"test","sintetico");
                assertThrows(IllegalStateException.class,()->second.get(83,"test",Instant.now(),()->{throw new IllegalStateException("sin red");}));
            } finally {release.countDown();executor.shutdownNow();}
        } finally {admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}
