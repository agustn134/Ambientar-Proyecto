package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.exception.SimulacionException;
import com.proyecto.servicios.integration.simulacion.ProveedorSimulado;
import com.proyecto.servicios.model.gestopago.pago.CrearPagoRequest;
import com.proyecto.servicios.model.gestopago.simulacion.SimulacionDtos.*;
import com.proyecto.servicios.repositorys.simulacion.SimulacionRepository;
import com.proyecto.servicios.service.simulacion.PagoSimuladoService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PagoSimuladoPostgresTest {
    private final String schema="test_simulacion_"+UUID.randomUUID().toString().replace("-","");
    JdbcTemplate admin,sql;
    DriverManagerDataSource ds;
    SimulacionRepository repo;
    ProveedorSimulado provider;
    PagoSimuladoService pagos,segundaInstancia;
    ValidatorFactory validation;
    long usuario,cliente,cuenta,otraCuenta,ajena,otroUsuario;
    static final String AES="HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=", IV="p5ldmBPdd/9pjC0bDC/nSg==";
    final String storage=Base64.getEncoder().encodeToString(new byte[32]);
    @BeforeAll void start(){
        var url=System.getenv("POSTGRES_TEST_URL");var user=System.getenv("POSTGRES_TEST_USER");var password=System.getenv("POSTGRES_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));admin.execute("CREATE SCHEMA "+schema);
        ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).load().migrate();
        sql=new JdbcTemplate(ds);validation=Validation.buildDefaultValidatorFactory();
        repo=new SimulacionRepository(ds,new DataSourceTransactionManager(ds));
        provider=new ProveedorSimulado(repo,new ObjectMapper(),AES,IV);
        pagos=servicio(repo,provider);
        var otroRepo=new SimulacionRepository(ds,new DataSourceTransactionManager(ds));
        segundaInstancia=servicio(otroRepo,new ProveedorSimulado(otroRepo,new ObjectMapper(),AES,IV));
        cliente=cliente("CAPP900101HGTBCDA0","CAPP900101001","qa@example.com");
        long otro=cliente("CAPP900102HGTBCDA0","CAPP900102001","otro@example.com");
        usuario=usuario(cliente,"qa@example.com");otroUsuario=usuario(otro,"otro@example.com");
        cuenta=cuenta(cliente,"00000000000000000001");otraCuenta=cuenta(cliente,"00000000000000000002");ajena=cuenta(otro,"00000000000000000003");
    }
    PagoSimuladoService servicio(SimulacionRepository repository,ProveedorSimulado proveedor){
        return new PagoSimuladoService(repository,proveedor,new ObjectMapper(),validation.getValidator(),storage);
    }
    @BeforeEach void clear(){
        for(var table:List.of("simulacion_proveedor","simulacion_eventos","simulacion_movimientos","simulacion_outbox","simulacion_pagos","simulacion_saldos"))sql.update("DELETE FROM "+table);
        sql.update("UPDATE cuentas SET estatus='ACTIVA'");sql.update("UPDATE usuarios SET activo=true");sql.update("UPDATE clientes SET estatus='ACTIVO'");
    }
    @AfterAll void stop(){
        if(validation!=null)validation.close();
        if(admin!=null && schema.matches("test_simulacion_[a-f0-9]{32}"))admin.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    long cliente(String curp,String rfc,String correo){return sql.queryForObject("""
        INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual,sexo_id,nacionalidad_id,estado_civil_id)
        VALUES ('Carlos','Parra','Perez','1990-01-01',?,?,?,'5512345678','QA','Prueba',100,1,1,7) RETURNING id
        """,Long.class,curp,rfc,correo);}
    long usuario(long cliente,String correo){return sql.queryForObject("INSERT INTO usuarios(cliente_id,correo,password_hash) VALUES (?,?,?) RETURNING id",Long.class,cliente,correo,new BCryptPasswordEncoder().encode("PruebaLocal2026!"));}
    long cuenta(long cliente,String numero){return sql.queryForObject("INSERT INTO cuentas(cliente_id,numero_cuenta,saldo) VALUES (?,?,1000) RETURNING id",Long.class,cliente,numero);}
    void fondear(String monto){pagos.fondear(usuario,cuenta,new Fondeo(new BigDecimal(monto)));}
    Solicitud recarga(Escenario escenario){return new Solicitud(cuenta,new CrearPagoRequest(78,219,new BigDecimal("40.00"),"5512345678",null),escenario);}
    Solicitud referencia(Escenario escenario,String ref){return new Solicitud(cuenta,new CrearPagoRequest(9900,990001,new BigDecimal("150.00"),"5512345678",ref),escenario);}
    void saldo(String contable,String reserva){var s=pagos.saldo(usuario,cuenta);assertEquals(0,s.contabilizado().compareTo(new BigDecimal(contable)));assertEquals(0,s.reservado().compareTo(new BigDecimal(reserva)));}
    void codigo(String code,org.junit.jupiter.api.function.Executable executable){assertEquals(code,assertThrows(SimulacionException.class,executable).getCodigo());}
    @Test void referenciaCifradaReservaYCapturaSinAlterarSaldoOriginal(){
        fondear("200");var pago=pagos.crear(usuario,"referencia-01",referencia(Escenario.EXITO,"SIM0000001"));
        assertEquals(Estado.RESERVED,pago.estado());saldo("200","150");
        String guardado=sql.queryForObject("SELECT payload_cifrado FROM simulacion_pagos WHERE id=?",String.class,pago.id());
        assertTrue(guardado.startsWith("v1."));assertFalse(guardado.contains("5512345678"));assertFalse(guardado.contains("SIM0000001"));
        assertEquals(Estado.CAPTURED,pagos.procesar(usuario,pago.id()).estado());saldo("50","0");
        assertEquals(new BigDecimal("1000.00"),sql.queryForObject("SELECT saldo FROM cuentas WHERE id=?",BigDecimal.class,cuenta));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM simulacion_movimientos WHERE tipo='CAPTURA'",Integer.class));
    }
    @Test void rechazoSoloLiberaReserva(){
        fondear("100");var p=pagos.crear(usuario,"rechazo-0001",recarga(Escenario.RECHAZO));
        assertEquals(Estado.FAILED,pagos.procesar(usuario,p.id()).estado());saldo("100","0");
        pagos.procesar(usuario,p.id());pagos.conciliar(usuario,p.id());
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM simulacion_movimientos WHERE tipo='LIBERACION'",Integer.class));
    }
    @Test void timeoutAplicadoMantieneReservaHastaConciliacionSinReenviar(){
        fondear("100");var p=pagos.crear(usuario,"timeout-0001",recarga(Escenario.TIMEOUT_APLICADO));
        assertEquals(Estado.UNKNOWN,pagos.procesar(usuario,p.id()).estado());saldo("100","40");
        assertEquals(Estado.UNKNOWN,pagos.procesar(usuario,p.id()).estado());
        assertEquals(Estado.CAPTURED,segundaInstancia.conciliar(usuario,p.id()).estado());saldo("60","0");
        pagos.conciliar(usuario,p.id());assertEquals(1,sql.queryForObject("SELECT count(*) FROM simulacion_proveedor",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM simulacion_movimientos WHERE tipo='CAPTURA'",Integer.class));
    }
    @Test void timeoutNoAplicadoSoloLiberaConEvidencia(){
        fondear("100");var p=pagos.crear(usuario,"timeout-0002",recarga(Escenario.TIMEOUT_NO_APLICADO));
        pagos.procesar(usuario,p.id());saldo("100","40");
        assertEquals(Estado.FAILED,pagos.conciliar(usuario,p.id()).estado());saldo("100","0");
    }
    @Test void referenciaInvalidaNoReservaNiEncola(){
        fondear("200");codigo("REFERENCIA_INVALIDA",()->pagos.crear(usuario,"ref-invalida",referencia(Escenario.EXITO,"SIM0000000")));
        saldo("200","0");assertEquals(0,sql.queryForObject("SELECT count(*) FROM simulacion_outbox",Integer.class));
    }
    @Test void referenciaIndeterminadaNoReservaNiEncola(){
        fondear("200");codigo("REFERENCIA_NO_CONCLUYENTE",()->pagos.crear(usuario,"ref-incierta",referencia(Escenario.EXITO,"SIM0000002")));
        saldo("200","0");assertEquals(0,sql.queryForObject("SELECT count(*) FROM simulacion_outbox",Integer.class));
    }
    @Test void cuentaDesactivadaDespuesDeReservarCancelaSinLlamarProveedor(){
        fondear("100");var p=pagos.crear(usuario,"desactivada-01",recarga(Escenario.EXITO));
        sql.update("UPDATE cuentas SET estatus='INACTIVA' WHERE id=?",cuenta);
        assertEquals(Estado.FAILED,pagos.procesar(usuario,p.id()).estado());saldo("100","0");
        assertEquals(0,sql.queryForObject("SELECT count(*) FROM simulacion_proveedor",Integer.class));
    }
    @Test void duplicadosSimultaneosEntreInstanciasSoloReservanUnaVez()throws Exception{
        fondear("100");var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            var a=pool.submit(()->{gate.await();return pagos.crear(usuario,"duplicado-paralelo",recarga(Escenario.EXITO));});
            var b=pool.submit(()->{gate.await();return segundaInstancia.crear(usuario,"duplicado-paralelo",recarga(Escenario.EXITO));});
            gate.countDown();assertEquals(a.get(20,TimeUnit.SECONDS).id(),b.get(20,TimeUnit.SECONDS).id());saldo("100","40");
            assertEquals(1,sql.queryForObject("SELECT count(*) FROM simulacion_outbox",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void dosConciliadoresSoloCapturanUnaVez()throws Exception{
        fondear("100");var p=pagos.crear(usuario,"conciliacion-paralela",recarga(Escenario.TIMEOUT_APLICADO));pagos.procesar(usuario,p.id());
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            var a=pool.submit(()->{gate.await();return pagos.conciliar(usuario,p.id());});
            var b=pool.submit(()->{gate.await();return segundaInstancia.conciliar(usuario,p.id());});
            gate.countDown();assertEquals(Estado.CAPTURED,a.get(20,TimeUnit.SECONDS).estado());assertEquals(Estado.CAPTURED,b.get(20,TimeUnit.SECONDS).estado());saldo("60","0");
            assertEquals(1,sql.queryForObject("SELECT count(*) FROM simulacion_movimientos WHERE tipo='CAPTURA'",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void validaCuentaPropiedadSesionYParametros(){
        codigo("RECURSO_NO_ENCONTRADO",()->pagos.fondear(otroUsuario,cuenta,new Fondeo(BigDecimal.TEN)));
        fondear("100");sql.update("UPDATE cuentas SET estatus='INACTIVA' WHERE id=?",cuenta);
        codigo("CUENTA_INACTIVA",()->pagos.crear(usuario,"inactivo-001",recarga(Escenario.EXITO)));
        sql.update("UPDATE cuentas SET estatus='ACTIVA' WHERE id=?",cuenta);sql.update("UPDATE usuarios SET activo=false WHERE id=?",usuario);
        codigo("NO_AUTENTICADO",()->pagos.crear(usuario,"inactivo-002",recarga(Escenario.EXITO)));
        codigo("DATOS_INVALIDOS",()->pagos.fondear(usuario,cuenta,new Fondeo(new BigDecimal("0.001"))));
    }
    @Test void saldoInsuficienteNoCreaOperacion(){
        fondear("30");codigo("SALDO_SIMULADO_INSUFICIENTE",()->pagos.crear(usuario,"sin-saldo-01",recarga(Escenario.EXITO)));
        saldo("30","0");assertEquals(0,sql.queryForObject("SELECT count(*) FROM simulacion_pagos",Integer.class));
    }
    @Test void mismaClaveMismosDatosDevuelvePagoAunSinDisponible(){
        fondear("40");var p=pagos.crear(usuario,"duplicado-01",recarga(Escenario.EXITO));
        assertEquals(p.id(),segundaInstancia.crear(usuario,"duplicado-01",recarga(Escenario.EXITO)).id());
        codigo("IDEMPOTENCIA_CONFLICTO",()->pagos.crear(usuario,"duplicado-01",recarga(Escenario.RECHAZO)));
        saldo("40","40");
    }
    @Test void dosInstanciasNoSobregiranSaldoConSolicitudesSimultaneas()throws Exception{
        fondear("40");var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var tasks=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++){final int index=i;tasks.add(pool.submit(()->{
                gate.await();try{(index==0?pagos:segundaInstancia).crear(usuario,"concurrente-"+index,recarga(Escenario.EXITO));return "RESERVED";}
                catch(SimulacionException ex){return ex.getCodigo();}
            }));}
            gate.countDown();var resultados=new ArrayList<String>();for(var task:tasks)resultados.add(task.get(20,TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(resultados,"RESERVED"));assertEquals(1,Collections.frequency(resultados,"SALDO_SIMULADO_INSUFICIENTE"));saldo("40","40");
        }finally{pool.shutdownNow();}
    }
    @Test void dosWorkersNoDuplicanCaptura()throws Exception{
        fondear("100");var p=pagos.crear(usuario,"workers-0001",recarga(Escenario.EXITO));
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            var a=pool.submit(()->{gate.await();return pagos.procesar(usuario,p.id());});
            var b=pool.submit(()->{gate.await();return segundaInstancia.procesar(usuario,p.id());});
            gate.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            assertEquals(Estado.CAPTURED,pagos.consultar(usuario,p.id()).estado());saldo("60","0");
            assertEquals(1,sql.queryForObject("SELECT count(*) FROM simulacion_proveedor",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void recuperaProcesoInterrumpidoSinInventarFracaso(){
        fondear("100");var p=pagos.crear(usuario,"interrupcion",recarga(Escenario.EXITO));
        assertNotNull(repo.reclamar(usuario,p.id()));
        sql.update("UPDATE simulacion_outbox SET vence_en=clock_timestamp()-interval '1 second' WHERE pago_id=?",p.id());
        segundaInstancia.recuperarInciertos();assertEquals(Estado.UNKNOWN,pagos.conciliar(usuario,p.id()).estado());saldo("100","40");
        assertNull(repo.reclamar(usuario,p.id()));assertEquals(0,sql.queryForObject("SELECT count(*) FROM simulacion_proveedor",Integer.class));
    }
    @Test void recuperaAplicacionDelProveedorTrasCaidaAntesDeCapturar(){
        fondear("100");var p=pagos.crear(usuario,"caida-aplicada",recarga(Escenario.EXITO));repo.reclamar(usuario,p.id());
        repo.registrarProveedor(p.id(),true,p.monto());
        sql.update("UPDATE simulacion_outbox SET vence_en=clock_timestamp()-interval '1 second' WHERE pago_id=?",p.id());
        pagos.recuperarInciertos();assertEquals(Estado.CAPTURED,segundaInstancia.conciliar(usuario,p.id()).estado());saldo("60","0");
    }
    @Test void fallaIntermediaRevierteReservaOperacionYOutbox(){
        fondear("100");
        assertThrows(RuntimeException.class,()->repo.reservar(usuario,cuenta,"fallo-atomicidad","0".repeat(64),new BigDecimal("40"),UUID.randomUUID(),"1".repeat(16),"cifrado"));
        saldo("100","0");assertEquals(0,sql.queryForObject("SELECT count(*) FROM simulacion_pagos",Integer.class));
    }
    @Test void proveedorYConciliacionNoPermitenLeerCuentaAjena(){
        fondear("100");var p=pagos.crear(usuario,"propiedad-01",recarga(Escenario.EXITO));
        codigo("RECURSO_NO_ENCONTRADO",()->pagos.procesar(otroUsuario,p.id()));
        codigo("RECURSO_NO_ENCONTRADO",()->pagos.conciliar(otroUsuario,p.id()));
        saldo("100","40");assertEquals(0,sql.queryForObject("SELECT count(*) FROM simulacion_proveedor",Integer.class));
    }
}
