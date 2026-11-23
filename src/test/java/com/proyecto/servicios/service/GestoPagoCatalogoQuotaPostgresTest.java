package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.CuotaCatalogoException;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoCatalogoQuota;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GestoPagoCatalogoQuotaPostgresTest {
    final String schema="test_cuota_"+UUID.randomUUID().toString().replace("-","");
    JdbcTemplate admin,sql;
    DriverManagerDataSource ds;
    GestoPagoCatalogoQuota first,second;
    @BeforeAll void start(){
        String url=System.getenv("POSTGRES_TEST_URL"),user=System.getenv("POSTGRES_TEST_USER"),password=System.getenv("POSTGRES_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));admin.execute("CREATE SCHEMA "+schema);
        ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__gestopago_cuota_catalogo.sql")).execute(ds);
        sql=new JdbcTemplate(ds);
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM gestopago_catalogo_consultas WHERE origen='RESERVA_INICIAL'",Integer.class));
        first=new GestoPagoCatalogoQuota(ds,new DataSourceTransactionManager(ds));
        second=new GestoPagoCatalogoQuota(ds,new DataSourceTransactionManager(ds));
    }
    @BeforeEach void clear(){sql.update("DELETE FROM gestopago_catalogo_consultas");}
    @AfterAll void stop(){if(admin!=null && schema.matches("test_cuota_[a-f0-9]{32}"))admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    @Test void dosInstanciasSoloAdmitenTresIntentosConcurrentes() throws Exception {
        var executor=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);var accepted=new AtomicInteger();var blocked=new AtomicInteger();
        try {
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<8;i++){var quota=i%2==0?first:second;futures.add(executor.submit(()->{
                try{start.await();quota.reservarIntento();accepted.incrementAndGet();}
                catch(CuotaCatalogoException ex){blocked.incrementAndGet();}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException();}
            }));}
            start.countDown();for(var future:futures)future.get(20,TimeUnit.SECONDS);
            assertEquals(3,accepted.get());assertEquals(5,blocked.get());
            assertEquals(3,sql.queryForObject("SELECT count(*) FROM gestopago_catalogo_consultas",Integer.class));
        }finally{executor.shutdownNow();}
    }
    @Test void reservaSobreviveRollbackDelSolicitanteYReinicio(){
        var outer=new TransactionTemplate(new DataSourceTransactionManager(ds));
        outer.executeWithoutResult(status->{first.reservarIntento();status.setRollbackOnly();});
        new GestoPagoCatalogoQuota(ds,new DataSourceTransactionManager(ds)).reservarIntento();
        second.reservarIntento();
        var blocked=assertThrows(CuotaCatalogoException.class,first::reservarIntento);
        assertTrue(blocked.getRetryAfterSeconds()>0 && blocked.getRetryAfterSeconds()<=86400);
    }
    @Test void soloLiberaIntentosFueraDeLaVentana(){
        sql.update("INSERT INTO gestopago_catalogo_consultas(solicitada_en) VALUES (now()-interval '25 hours'),(now()-interval '23 hours'),(now()-interval '23 hours')");
        first.reservarIntento();assertThrows(CuotaCatalogoException.class,second::reservarIntento);
        sql.update("UPDATE gestopago_catalogo_consultas SET solicitada_en=now()-interval '25 hours'");
        assertDoesNotThrow(second::reservarIntento);
    }
    @Test void reservaInicialReducePresupuestoSinLlamarProveedor(){
        sql.update("INSERT INTO gestopago_catalogo_consultas(origen) VALUES ('RESERVA_INICIAL')");
        first.reservarIntento();second.reservarIntento();assertThrows(CuotaCatalogoException.class,first::reservarIntento);
    }
}
