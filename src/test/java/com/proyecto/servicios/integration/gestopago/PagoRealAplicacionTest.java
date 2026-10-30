package com.proyecto.servicios.integration.gestopago;

import com.proyecto.servicios.App;
import com.proyecto.servicios.config.GestoPagoContratoProperties;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.pago.*;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.*;
import com.proyecto.servicios.repositorys.gestopago.PagoRepository;
import com.proyecto.servicios.scheduler.*;
import com.proyecto.servicios.service.PagoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** App + login/JWT + PostgreSQL/JPA + HTTP/AES reales contra proveedor local controlado. Cero tráfico externo. */
@SpringBootTest(classes=App.class)
@AutoConfigureMockMvc
@ActiveProfiles("live")
@DirtiesContext
@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PagoRealAplicacionTest {
    static final String SCHEMA="test_pago_real_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate admin;
    @MockitoBean(name="catalogoPostal") Object postal;
    @MockitoBean GestoPagoProductScheduler catalogScheduler;
    @MockitoBean GestoPagoTransport transport;
    @Autowired PagoService pagos;
    @Autowired PagoRepository repo;
    @Autowired PagoWorker worker;
    @Autowired GestoPagoPaymentClient client;
    @Autowired GestoPagoContratoProperties policies;
    @Autowired com.proyecto.servicios.repositorys.cliente.CuentaRepository cuentasJpa;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("sfDatasource") DataSource ds;
    JdbcTemplate sql;
    HttpServer server;
    ExecutorService httpExecutor;
    long cuenta,usuario;
    String jwt;
    final AtomicReference<String> scenario=new AtomicReference<>("EXITO");
    final AtomicInteger auths=new AtomicInteger(),sends=new AtomicInteger(),confirms=new AtomicInteger(),references=new AtomicInteger();
    final Map<String,String> providerBook=new ConcurrentHashMap<>();
    final Queue<Throwable> httpErrors=new ConcurrentLinkedQueue<>();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        String url=System.getenv("POSTGRES_TEST_URL"),u=System.getenv("POSTGRES_TEST_USER"),pw=System.getenv("POSTGRES_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,u,pw));admin.execute("CREATE SCHEMA "+SCHEMA);
        p.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA);
        p.add("spring.datasource.username",()->u);p.add("spring.datasource.password",()->pw);p.add("spring.flyway.schemas",()->SCHEMA);
        p.add("security.jwt.secret",()->GestoPagoLiveGuardTest.key(32,9));
        p.add("gestopago.payment.enabled",()->true);p.add("gestopago.reference.enabled",()->true);
        p.add("gestopago.payment.worker-enabled",()->false);
        p.add("gestopago.auth.password",()->"clave-sintetica-controlada");
        p.add("gestopago.auth.basic-authorization",()->"");p.add("gestopago.api-key",()->"");
        p.add("gestopago.crypto.secret-base64",()->GestoPagoLiveGuardTest.key(32,1));p.add("gestopago.crypto.iv-base64",()->GestoPagoLiveGuardTest.key(16,2));
        p.add("gestopago.payment.storage-key-base64",()->GestoPagoLiveGuardTest.key(32,3));
        p.add("gestopago.token-cache-key-base64",()->GestoPagoLiveGuardTest.key(32,4));p.add("gestopago.payment.unidad",()->"QA1");
        p.add("spring.cache.type",()->"none");
    }
    @BeforeAll void start() throws Exception {
        sql=new JdbcTemplate(ds);
        long cliente=sql.queryForObject("""
            INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual,sexo_id,nacionalidad_id,estado_civil_id)
            VALUES ('Carlos','Parra','Perez','1990-01-01','CAPP900101HGTBCDA0','CAPP900101001','realtest@example.com','5512345678','QA','Prueba',100,1,1,7) RETURNING id
            """,Long.class);
        usuario=sql.queryForObject("INSERT INTO usuarios(cliente_id,correo,password_hash) VALUES (?,'realtest@example.com',?) RETURNING id",Long.class,cliente,new BCryptPasswordEncoder().encode("SoloPrueba2026!"));
        cuenta=sql.queryForObject("INSERT INTO cuentas(cliente_id,numero_cuenta,saldo) VALUES (?,'00000000000000000001',1000) RETURNING id",Long.class,cliente);
        sql.update("INSERT INTO gestopago_productos(id_producto,id_servicio,tipo_front,id_cat_tipo_servicio,precio) VALUES (219,78,'1',13,40)");
        // Fixture inventado y aislado; no se presenta como un producto autorizado del catálogo real.
        sql.update("INSERT INTO gestopago_productos(id_producto,id_servicio,tipo_front,id_cat_tipo_servicio,precio) VALUES (900001,9000,'4',1,40)");
        var refRule=new GestoPagoContratoProperties.ReglaProducto();
        refRule.setHabilitado(true);refRule.setIntegracionAprobada(true);refRule.setServicioActivo(true);
        refRule.setIdServicio(9000);refRule.setTipoFront("4");refRule.setIdCatTipoServicio(1);
        refRule.setDestino(GestoPagoContratoProperties.Destino.REFERENCIA);refRule.setRequiereReferencia(true);
        refRule.setVerificacionReferenciaAprobada(true);
        refRule.setReferenciaMinima(5);refRule.setReferenciaMaxima(20);refRule.setMontoMinimo(new BigDecimal("40"));
        refRule.setMontoMaximo(new BigDecimal("40"));refRule.setMontoFijo(new BigDecimal("40"));refRule.setBloqueoServicioMonto24h(true);
        policies.getProductos().put(900001,refRule);
        sql.update("INSERT INTO gestopago_productos(id_producto,id_servicio,tipo_front,id_cat_tipo_servicio,precio) VALUES (900002,9000,'2',1,40)");
        var fixedRule=new GestoPagoContratoProperties.ReglaProducto();fixedRule.setHabilitado(true);fixedRule.setIntegracionAprobada(true);
        fixedRule.setServicioActivo(true);fixedRule.setIdServicio(9000);fixedRule.setTipoFront("2");fixedRule.setIdCatTipoServicio(1);
        fixedRule.setDestino(GestoPagoContratoProperties.Destino.FIJO);fixedRule.setRequiereReferencia(true);
        fixedRule.setVerificacionReferenciaAprobada(true);fixedRule.setReferenciaMinima(5);fixedRule.setReferenciaMaxima(40);
        fixedRule.setMontoMinimo(new BigDecimal("40"));fixedRule.setMontoMaximo(new BigDecimal("40"));fixedRule.setMontoFijo(new BigDecimal("40"));
        fixedRule.setBloqueoServicioMonto24h(true);policies.getProductos().put(900002,fixedRule);
        var login=mvc.perform(post("/auth/login").contentType("application/json").content("{\"correo\":\"realtest@example.com\",\"password\":\"SoloPrueba2026!\"}"))
                .andExpect(status().isOk()).andReturn();
        jwt="Bearer "+mapper.readTree(login.getResponse().getContentAsString()).path("datos").path("accessToken").asText();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        httpExecutor=Executors.newCachedThreadPool();server.setExecutor(httpExecutor);
        server.createContext("/",exchange->{
            try {
                String path=exchange.getRequestURI().getPath();
                var fields=form(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                String body;int status=200;
                if(path.endsWith("authenticate/")) {
                    assertEquals("clave-sintetica-controlada",fields.get("password"));
                    body="{\"success\":true,\"token\":\"CONTROLLED_"+auths.incrementAndGet()+"\",\"expires_in\":86400}";
                } else {
                    assertTrue(exchange.getRequestHeaders().getFirst("Authorization").startsWith("Bearer CONTROLLED_"));
                    String nonce=path.endsWith("verifyReference.do")?"body":"random";
                    assertNotNull(fields.get(nonce));assertEquals(Set.of(nonce,"signed"),fields.keySet());
                    String json=decrypt(fields.get("signed"));var node=mapper.readTree(json);
                    if(path.endsWith("verifyReference.do")) {
                        references.incrementAndGet();body="<RESPONSE><MENSAJE><CODIGO>"+("INVALID_REF".equals(scenario.get())?"02":"01")+"</CODIGO></MENSAJE></RESPONSE>";
                    } else if(path.endsWith("sendTx.do")) {
                        sends.incrementAndGet();providerBook.put(node.path("upc").asText(),json);
                        if(scenario.get().startsWith("TIMEOUT")){exchange.close();return;}
                        if("EXPIRED".equals(scenario.get())){status=403;body="{\"token\":\"EXPIRED\"}";}
                        else if("INCOMPLETA".equals(scenario.get()))body="<RESPONSE/>";
                        else if("RECHAZO".equals(scenario.get()))body="<RESPONSE><MENSAJE><CODIGO>02</CODIGO></MENSAJE></RESPONSE>";
                        else body="<RESPONSE><ID_TX>123</ID_TX><NUM_AUTORIZACION>100</NUM_AUTORIZACION><MONTO>40.00</MONTO><MENSAJE><CODIGO>01</CODIGO></MENSAJE></RESPONSE>";
                    } else if(path.endsWith("confirmTx.do")) {
                        confirms.incrementAndGet();assertEquals(providerBook.get(node.path("upc").asText()),json,"confirmTx debe conservar todos los parámetros originales");
                        if("INCOMPLETA".equals(scenario.get()))body="<RESPONSE/>";
                        else if(Set.of("TIMEOUT_NO_APLICADO","RECHAZO").contains(scenario.get()))body=confirm("70","-1","0");
                        else body=confirm("06","123","100");
                    } else throw new AssertionError("Ruta no autorizada por la prueba");
                }
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch(Throwable ex) { httpErrors.add(ex);exchange.sendResponseHeaders(500,-1); }
            finally {exchange.close();}
        });
        server.start();
    }
    void routeTransport() {
        // Solo sustituye el destino. El transporte Apache, form, tiempos y cifrado son los de producción.
        var realTransport=new GestoPagoTransport(1);
        when(transport.post(any(),anyMap(),anyMap())).thenAnswer(inv->{
            URI destination=inv.getArgument(0);
            assertEquals("gestopago.portalventas.net",destination.getHost());
            return realTransport.execute(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+destination.getPath()),inv.getArgument(1),inv.getArgument(2));
        });
    }
    @BeforeEach void resetDatabase() {
        routeTransport();httpErrors.clear();
        for(String table:List.of("pago_eventos","pago_movimientos","pago_outbox","pago_operaciones","gestopago_token_cache"))sql.update("DELETE FROM "+table);
        sql.update("UPDATE cuentas SET saldo=1000,saldo_reservado=0,estatus='ACTIVA'");sql.update("UPDATE usuarios SET activo=true");
        sql.update("UPDATE gestopago_productos SET activo=true,precio=40,fecha_actualizacion=LOCALTIMESTAMP");
        policies.getProductos().get(219).setIntegracionAprobada(true);policies.getProductos().get(219).setServicioActivo(true);
        providerBook.clear();scenario.set("EXITO");auths.set(0);sends.set(0);confirms.set(0);references.set(0);
    }
    @AfterEach void providerAssertions(){assertTrue(httpErrors.isEmpty(),()->"El servidor controlado rechazó el contrato: "+httpErrors);}
    Solicitud request(String telefono){return new Solicitud(cuenta,new CrearPagoRequest(78,219,new BigDecimal("40.00"),telefono,null));}
    Pago crear(String key){return pagos.crear(usuario,key,request("5512345678"));}
    void saldo(String total,String reserved){var s=pagos.saldo(usuario,cuenta);assertEquals(new BigDecimal(total),s.contabilizado());assertEquals(new BigDecimal(reserved),s.reservado());}
    void age(UUID id){sql.update("UPDATE pago_operaciones SET enviado_en=clock_timestamp()-interval '63 seconds' WHERE id=?",id);sql.update("UPDATE pago_outbox SET siguiente_en=clock_timestamp()-interval '1 second' WHERE pago_id=?",id);}
    @AfterAll void stop(){if(server!=null)server.stop(0);if(httpExecutor!=null)httpExecutor.shutdownNow();if(admin!=null && SCHEMA.matches("test_pago_real_[a-f0-9]{32}"))admin.execute("DROP SCHEMA "+SCHEMA+" CASCADE");}
    @Test void loginSwaggerReservaCapturaYCifradoConTokenReutilizado()throws Exception {
        String body=mapper.writeValueAsString(request("5512345678"));
        var response=mvc.perform(post("/api/gestopago/pagos").header("Authorization",jwt).header("Idempotency-Key","real-http-0001").contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("datos.estado").value("RESERVED")).andReturn();
        UUID id=UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("datos").path("id").asText());
        saldo("1000.00","40.00");worker.ejecutar(id);saldo("960.00","0.00");
        assertEquals(Estado.CAPTURED,pagos.consultar(usuario,id).estado());
        var second=crear("real-http-0002");worker.ejecutar(second.id());assertEquals(1,auths.get());assertEquals(2,sends.get());
        String snapshot=sql.queryForObject("SELECT payload_cifrado FROM pago_operaciones WHERE id=?",String.class,id);
        assertFalse(snapshot.contains("5512345678"));assertTrue(snapshot.startsWith("v1."));
        String cache=sql.queryForObject("SELECT token_cifrado FROM gestopago_token_cache",String.class);assertFalse(cache.contains("CONTROLLED_"));
        var spec=mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(spec.path("paths").path("/api/gestopago/pagos").path("post").path("responses").has("202"));
        assertFalse(spec.path("paths").has("/api/simulacion/pagos"));
        mvc.perform(post("/api/gestopago/pagos").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }
    @Test void timeoutAplicadoConfirma06SinReenvioYParametrosIdenticos() {
        scenario.set("TIMEOUT_APLICADO");var p=crear("real-timeout-01");worker.ejecutar(p.id());
        assertEquals(Estado.UNKNOWN,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","40.00");
        worker.ejecutar(p.id());assertEquals(0,confirms.get());age(p.id());worker.ejecutar(p.id());
        assertEquals(Estado.CAPTURED,pagos.consultar(usuario,p.id()).estado());saldo("960.00","0.00");assertEquals(1,sends.get());
        worker.ejecutar(p.id());assertEquals(1,confirms.get());saldo("960.00","0.00");
    }
    @Test void timeoutNoAplicadoConfirma70YLiberaUnaVez() {
        scenario.set("TIMEOUT_NO_APLICADO");var p=crear("real-timeout-02");worker.ejecutar(p.id());age(p.id());worker.ejecutar(p.id());
        assertEquals(Estado.FAILED,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","0.00");
        worker.ejecutar(p.id());assertEquals(1,sends.get());assertEquals(1,sql.queryForObject("SELECT count(*) FROM pago_movimientos WHERE tipo='LIBERACION'",Integer.class));
    }
    @Test void rechazoSendNoSeClasificaSinConfirmacion() {
        scenario.set("RECHAZO");var p=crear("real-rechazo-01");worker.ejecutar(p.id());
        assertEquals(Estado.UNKNOWN,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","40.00");age(p.id());worker.ejecutar(p.id());
        assertEquals(Estado.FAILED,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","0.00");
    }
    @Test void respuestaIncompletaNiConciliacionAmbiguaLiberanReserva() {
        scenario.set("INCOMPLETA");var p=crear("real-incompleta-01");worker.ejecutar(p.id());age(p.id());worker.ejecutar(p.id());
        assertEquals(Estado.UNKNOWN,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","40.00");assertEquals(1,sends.get());
    }
    @Test void tokenExpiradoNoReenviaVentaYSeRenuevaParaConsultar() {
        scenario.set("EXPIRED");var p=crear("real-expira-01");worker.ejecutar(p.id());
        assertEquals(Estado.UNKNOWN,pagos.consultar(usuario,p.id()).estado());age(p.id());worker.ejecutar(p.id());
        assertEquals(2,auths.get());assertEquals(1,sends.get());assertEquals(Estado.CAPTURED,pagos.consultar(usuario,p.id()).estado());
    }
    @Test void mismaClaveConcurrenteReservaUnaVezYCambioDeDatosDa409()throws Exception {
        var pool=Executors.newFixedThreadPool(2);
        try {
            var gate=new CountDownLatch(1);Callable<Pago> call=()->{gate.await();return crear("real-clave-0001");};
            var a=pool.submit(call);var b=pool.submit(call);gate.countDown();assertEquals(a.get(15,TimeUnit.SECONDS).id(),b.get(15,TimeUnit.SECONDS).id());
        }finally{pool.shutdownNow();}
        saldo("1000.00","40.00");
        assertEquals("IDEMPOTENCIA_CONFLICTO",assertThrows(PagoException.class,()->pagos.crear(usuario,"real-clave-0001",request("5512345679"))).getCodigo());
    }
    @Test void dosWorkersYDosConciliadoresCapturanUnaVez()throws Exception {
        scenario.set("TIMEOUT_APLICADO");var p=crear("real-workers-01");parallel(()->worker.ejecutar(p.id()));assertEquals(1,sends.get());
        age(p.id());parallel(()->worker.ejecutar(p.id()));assertEquals(1,confirms.get());saldo("960.00","0.00");
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM pago_movimientos WHERE tipo='CAPTURA'",Integer.class));
    }
    @Test void reservasSimultaneasNoGastanElMismoSaldo()throws Exception {
        sql.update("UPDATE cuentas SET saldo=40 WHERE id=?",cuenta);
        var count=new AtomicInteger();var pool=Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> calls=new ArrayList<>();
            for(int i=0;i<2;i++){String key="real-saldo-000"+i;calls.add(()->{try{crear(key);count.incrementAndGet();}catch(PagoException ex){assertEquals("SALDO_INSUFICIENTE",ex.getCodigo());}return null;});}
            for(var f:pool.invokeAll(calls))f.get(15,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertEquals(1,count.get());saldo("40.00","40.00");
    }
    @Test void falloAtomicoRevierteOperacionReservaYOutbox(){
        sql.execute("ALTER TABLE pago_outbox ADD CONSTRAINT prueba_fallo CHECK(false) NOT VALID");
        try{assertThrows(RuntimeException.class,()->crear("real-rollback-01"));}
        finally{sql.execute("ALTER TABLE pago_outbox DROP CONSTRAINT prueba_fallo");}
        saldo("1000.00","0.00");assertEquals(0,sql.queryForObject("SELECT count(*) FROM pago_operaciones",Integer.class));
    }
    @Test void caidaDespuesDelPostSeRecuperaConsultandoSinReenviar(){
        var p=crear("real-crash-0001");var t=repo.reclamar(p.id());var json=new TokenCacheCipher(GestoPagoLiveGuardTest.key(32,3)).decrypt(t.payloadCifrado());
        var prepared=client.preparar(json);assertTrue(repo.comenzarHttp(t));client.ejecutar(prepared,false);
        sql.update("UPDATE pago_outbox SET lease_hasta=clock_timestamp()-interval '1 second' WHERE pago_id=?",p.id());repo.recuperar();
        assertEquals(Estado.UNKNOWN,pagos.consultar(usuario,p.id()).estado());age(p.id());worker.ejecutar(p.id());saldo("960.00","0.00");assertEquals(1,sends.get());
    }
    @Test void leaseSinIntentoSeLiberaYWorkerAntiguoNoPuedeEnviar(){
        var p=crear("real-crash-0002");var t=repo.reclamar(p.id());
        sql.update("UPDATE pago_outbox SET lease_hasta=clock_timestamp()-interval '1 second' WHERE pago_id=?",p.id());repo.recuperar();
        assertFalse(repo.comenzarHttp(t));saldo("1000.00","0.00");assertEquals(Estado.FAILED,pagos.consultar(usuario,p.id()).estado());assertEquals(0,sends.get());
    }
    @Test void inciertoMayor24hSeEscalaSinConsultarNiLiberar(){
        scenario.set("TIMEOUT_APLICADO");var p=crear("real-vencido-01");worker.ejecutar(p.id());
        sql.update("UPDATE pago_operaciones SET enviado_en=clock_timestamp()-interval '25 hours' WHERE id=?",p.id());repo.recuperar();worker.ejecutar(p.id());
        assertTrue(pagos.consultar(usuario,p.id()).revisionManual());assertEquals(Estado.UNKNOWN,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","40.00");assertEquals(0,confirms.get());
    }
    @Test void cuentaInactivaYCatalogoCambiadoAntesDelEnvioNoConsumenSaldo(){
        var p=crear("real-inactiva-01");sql.update("UPDATE cuentas SET estatus='INACTIVA' WHERE id=?",cuenta);worker.ejecutar(p.id());
        assertEquals(Estado.FAILED,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","0.00");assertEquals(0,sends.get());
    }
    @Test void productoNoAprobadoVencidoCuentaAjenaYSinSaldoSeBloquean(){
        policies.getProductos().get(219).setIntegracionAprobada(false);
        assertEquals("INTEGRACION_PRODUCTO_NO_AUTORIZADA",assertThrows(PagoException.class,()->crear("real-noaprobado")).getCodigo());
        policies.getProductos().get(219).setIntegracionAprobada(true);sql.update("UPDATE gestopago_productos SET fecha_actualizacion=LOCALTIMESTAMP-interval '25 hours'");
        assertEquals("CATALOGO_VENCIDO",assertThrows(PagoException.class,()->crear("real-noaprobado")).getCodigo());
        assertEquals(404,assertThrows(PagoException.class,()->pagos.saldo(usuario+900,cuenta)).getStatus());assertEquals(0,sends.get());
    }
    @Test void evidenciaContradictoriaSeAuditaSinSegundoMovimiento(){
        var p=crear("real-discrepancia");var t=repo.reclamar(p.id());assertTrue(repo.comenzarHttp(t));
        repo.finalizar(t,Estado.CAPTURED,"01","123",null);
        var confirmTask=new PagoRepository.Trabajo(p.id(),UUID.randomUUID(),true,t.payloadCifrado(),t.monto());
        repo.finalizar(confirmTask,Estado.FAILED,"70",null,null);
        assertTrue(pagos.consultar(usuario,p.id()).revisionManual());assertEquals(Estado.CAPTURED,pagos.consultar(usuario,p.id()).estado());saldo("960.00","0.00");
    }
    Solicitud requestReferencia(String referencia){return new Solicitud(cuenta,new CrearPagoRequest(9000,900001,new BigDecimal("40"),null,referencia));}
    @Test void referenciaTipo4SeVerificaAntesDeReservarYCifraDentroDelContrato(){
        var p=pagos.crear(usuario,"real-referencia01",requestReferencia("REFERENCIA01"));
        assertEquals(1,references.get());saldo("1000.00","40.00");worker.ejecutar(p.id());
        assertEquals(Estado.CAPTURED,pagos.consultar(usuario,p.id()).estado());saldo("960.00","0.00");
        assertTrue(providerBook.values().iterator().next().contains("REFERENCIA01"));
    }
    @Test void referenciaInvalidaOInciertaNoReservaNiEnvia(){
        scenario.set("INVALID_REF");
        assertEquals("REFERENCIA_INVALIDA",assertThrows(PagoException.class,()->pagos.crear(usuario,"real-referencia02",requestReferencia("REFERENCIA02"))).getCodigo());
        saldo("1000.00","0.00");assertEquals(0,sends.get());
    }
    @Test void reglaServicioMonto24hBloqueaOtraClaveInclusoTrasTerminar(){
        var p=pagos.crear(usuario,"real-duplicado01",requestReferencia("REFERENCIA03"));worker.ejecutar(p.id());
        assertEquals("POSIBLE_DUPLICADO_SERVICIO_MONTO",assertThrows(PagoException.class,()->pagos.crear(usuario,"real-duplicado02",requestReferencia("REFERENCIA04"))).getCodigo());
        saldo("960.00","0.00");assertEquals(1,sends.get());
    }
    @Test void cambioDeCatalogoDespuesDeReservarCancelaAntesDelPost(){
        var p=crear("real-catalogo01");sql.update("UPDATE gestopago_productos SET precio=41,fecha_actualizacion=LOCALTIMESTAMP WHERE id_producto=219");
        worker.ejecutar(p.id());assertEquals(Estado.FAILED,pagos.consultar(usuario,p.id()).estado());saldo("1000.00","0.00");assertEquals(0,sends.get());
    }
    @Test void referenciaTipo2VerificaYEnviaTelefonoFijoSegunPolitica(){
        var r=new Solicitud(cuenta,new CrearPagoRequest(9000,900002,new BigDecimal("40"),null,"REFERENCIA05"));
        var p=pagos.crear(usuario,"real-referencia-tipo2",r);assertEquals(1,references.get());worker.ejecutar(p.id());
        assertEquals(Estado.CAPTURED,pagos.consultar(usuario,p.id()).estado());saldo("960.00","0.00");
        assertTrue(providerBook.values().iterator().next().contains("\"telefono\":\"1111111111\""));
    }
    @Test void actualizarEstatusConEntidadAntiguaNoRestauraSaldoCapturado(){
        var antigua=cuentasJpa.findById(cuenta).orElseThrow();var p=crear("real-jpa-saldo01");worker.ejecutar(p.id());
        antigua.setEstatus("INACTIVA");cuentasJpa.save(antigua);saldo("960.00","0.00");
    }
    @Test void leaseVencidoConEmisorPosiblementePausadoNoLiberaCon70(){
        var p=crear("real-fencing-001");var t=repo.reclamar(p.id());assertTrue(repo.comenzarHttp(t));
        sql.update("UPDATE pago_outbox SET lease_hasta=clock_timestamp()-interval '1 second' WHERE pago_id=?",p.id());repo.recuperar();age(p.id());
        var confirmation=repo.reclamar(p.id());assertNotNull(confirmation);assertTrue(repo.comenzarHttp(confirmation));
        repo.finalizar(confirmation,Estado.FAILED,"70",null,null);
        assertTrue(pagos.consultar(usuario,p.id()).revisionManual());saldo("1000.00","40.00");
        repo.finalizar(t,Estado.CAPTURED,"01","123",null);saldo("960.00","0.00");
    }
    void parallel(Runnable work)throws Exception{var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(work);var b=pool.submit(work);a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);}finally{pool.shutdownNow();}}
    static Map<String,String> form(String body){var fields=new HashMap<String,String>();for(String part:body.split("&")){var pair=part.split("=",2);fields.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}return fields;}
    static String decrypt(String signed)throws Exception{var cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(Base64.getDecoder().decode(GestoPagoLiveGuardTest.key(32,1)),"AES"),new IvParameterSpec(Base64.getDecoder().decode(GestoPagoLiveGuardTest.key(16,2))));return new String(cipher.doFinal(Base64.getDecoder().decode(signed)),StandardCharsets.UTF_8);}
    static String confirm(String code,String id,String auth){return "<RESPONSE><NUM_AUTORIZACION>"+auth+"</NUM_AUTORIZACION><MENSAJE><CODIGO>"+code+"</CODIGO><ID_TX>"+id+"</ID_TX></MENSAJE></RESPONSE>";}
}
