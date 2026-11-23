package com.proyecto.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.*;
import com.proyecto.servicios.repositorys.gestopago.HistorialPagoRepository;
import com.proyecto.servicios.security.SecurityErrorHandler;
import com.proyecto.servicios.security.UsuarioJwtValidator;
import com.proyecto.servicios.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = HistorialPagosIntegrationTest.Config.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:historial_m4;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
    "auth.otp.buzon-local-pruebas=true"
})
@AutoConfigureMockMvc
class HistorialPagosIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        ConfigDB.class, RegistroClienteService.class, ClienteController.class,
        com.proyecto.servicios.repositorys.cliente.CatalogoRepository.class, CatalogoController.class,
        ConsultaClienteService.class, CuentaController.class,
        ValidacionDatosCliente.class, MantenimientoClienteService.class,
        MantenimientoUsuarioService.class, UsuarioController.class,
        RegistroClienteExceptionHandler.class, GlobalExceptionHandler.class,
        RespuestaExitosaAdvice.class, PasswordConfig.class, LoggingAspect.class,
        JwtConfig.class, SecurityConfig.class, UsuarioJwtValidator.class,
        SecurityErrorHandler.class, LoginService.class, OtpCorreoService.class,
        JwtService.class, UsuarioSesionService.class, AuthController.class,
        HistorialPagoRepository.class, HistorialPagoController.class
    })
    static class Config {
        @Bean(name = "flyway")
        Object schema(DataSource dataSource) throws Exception {
            var sql = new JdbcTemplate(dataSource);
            sql.execute("CREATE ALIAS IF NOT EXISTS clock_timestamp AS $$ java.sql.Timestamp clockTimestamp() { return new java.sql.Timestamp(System.currentTimeMillis()); } $$");
            String v13 = new String(new ClassPathResource("db/migration/V13__pagos_reales.sql").getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
            new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__create_gestopago_tokens.sql"),
                new ClassPathResource("db/migration/V2__create_gestopago_productos.sql"),
                new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql"),
                new ClassPathResource("db/migration/V5__create_usuarios.sql"),
                new ClassPathResource("db/migration/V6__version_token_usuarios.sql"),
                new ClassPathResource("db/migration/V7__catalogos_mexico.sql"),
                new ClassPathResource("db/migration/V8__roles_y_fecha_registro.sql"),
                new ByteArrayResource(v13.getBytes(StandardCharsets.UTF_8), "V13__pagos_reales.sql"),
                new ClassPathResource("db/migration/V14__auth_otp_y_sesion_unica.sql"),
                new ClassPathResource("catalogo-postal-prueba.sql")
            ).execute(dataSource);
            return new Object();
        }

        @Bean(name = "catalogoPostal")
        Object postalPrueba() {
            return new Object();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired OtpCorreoService otpCorreo;

    private long clienteA, usuarioA, cuentaA;
    private long clienteB, usuarioB, cuentaB;
    private String tokenA, tokenB;

    @BeforeEach
    void prepararDatos() throws Exception {
        otpCorreo.limpiarBuzonPruebas();
        sql.update("DELETE FROM pago_eventos");
        sql.update("DELETE FROM pago_movimientos");
        sql.update("DELETE FROM pago_outbox");
        sql.update("DELETE FROM pago_operaciones");
        sql.update("DELETE FROM gestopago_productos");
        sql.update("DELETE FROM usuario_desafios_otp");
        sql.update("DELETE FROM usuario_sesiones_activas");
        sql.update("DELETE FROM usuarios");
        sql.update("DELETE FROM cuentas");
        sql.update("DELETE FROM domicilios");
        sql.update("DELETE FROM clientes");

        sql.update("""
            INSERT INTO gestopago_productos(id_producto, id_servicio, servicio, producto, id_cat_tipo_servicio, tipo_front, precio, activo)
            VALUES (219, 78, 'Oui Movil', 'Recarga 40', 13, '1', 40.00, TRUE)
            """);

        String hash = passwordEncoder.encode("SoloPrueba2026!");
        clienteA = insertarCliente("Ana", "CAPP900101HMXABCA0", "CAPP900101AA1", "ana.hist@example.com");
        usuarioA = insertarUsuario(clienteA, "ana.hist@example.com", hash);
        cuentaA = insertarCuenta(clienteA, "00000000000000000101");

        clienteB = insertarCliente("Bruno", "BAPP900102HMXBCBA0", "BAPP900102BB2", "bruno.hist@example.com");
        usuarioB = insertarUsuario(clienteB, "bruno.hist@example.com", hash);
        cuentaB = insertarCuenta(clienteB, "00000000000000000202");

        tokenA = autenticarUsuario("ana.hist@example.com", "SoloPrueba2026!");
        tokenB = autenticarUsuario("bruno.hist@example.com", "SoloPrueba2026!");
    }

    private long insertarCliente(String nombre, String curp, String rfc, String correo) {
        sql.update("""
            INSERT INTO clientes(nombre, apellido_paterno, apellido_materno, fecha_nacimiento, curp, rfc,
                                 correo_electronico, telefono_movil, ocupacion, empresa, ingreso_mensual,
                                 sexo_id, nacionalidad_id, estado_civil_id)
            VALUES (?, 'Perez', 'Lopez', '1990-01-01', ?, ?, ?, '5512345678', 'QA', 'Prueba', 100.00, 1, 1, 7)
            """, nombre, curp, rfc, correo);
        return sql.queryForObject("SELECT id FROM clientes WHERE correo_electronico = ?", Long.class, correo);
    }

    private long insertarUsuario(long clienteId, String correo, String passwordHash) {
        sql.update("INSERT INTO usuarios(cliente_id, correo, password_hash) VALUES (?, ?, ?)", clienteId, correo, passwordHash);
        return sql.queryForObject("SELECT id FROM usuarios WHERE correo = ?", Long.class, correo);
    }

    private long insertarCuenta(long clienteId, String numeroCuenta) {
        sql.update("INSERT INTO cuentas(cliente_id, numero_cuenta, saldo) VALUES (?, ?, 1000.00)", clienteId, numeroCuenta);
        return sql.queryForObject("SELECT id FROM cuentas WHERE numero_cuenta = ?", Long.class, numeroCuenta);
    }

    private String autenticarUsuario(String correo, String password) throws Exception {
        String loginRaw = mvc.perform(post("/auth/login").contentType("application/json")
                .content(mapper.createObjectNode().put("correo", correo).put("password", password).put("plataforma", "ANDROID").toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String desafioId = mapper.readTree(loginRaw).get("datos").get("desafioId").asText();
        String otp = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        String verifyRaw = mvc.perform(post("/auth/otp/verificar").contentType("application/json")
                .content(mapper.createObjectNode().put("desafioId", desafioId).put("codigoOtp", otp).toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(verifyRaw).get("datos").get("accessToken").asText();
    }

    private UUID insertarOperacion(
            long clienteId,
            long usuarioId,
            long cuentaId,
            String idempotencia,
            String upc,
            String estado,
            String idTx,
            BigDecimal monto,
            LocalDateTime timestamp) {
        UUID id = UUID.randomUUID();
        Timestamp ts = Timestamp.valueOf(timestamp);
        sql.update("""
            INSERT INTO pago_operaciones(id, cliente_id, usuario_id, cuenta_id, idempotencia, huella, monto, estado, upc,
                                         id_servicio, id_producto, catalogo_version, payload_cifrado, id_tx, creado_en, actualizado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 78, 219, ?, 'v1.cifrado', ?, ?, ?)
            """,
            id, clienteId, usuarioId, cuentaId, idempotencia, "a".repeat(64), monto, estado, upc, ts, idTx, ts, ts);
        return id;
    }

    @Test
    void historialFiltraExclusivamenteCapturadasDePropietarioConPaginacionYDetalleTrazable() throws Exception {
        UUID capReciente = insertarOperacion(clienteA, usuarioA, cuentaA, "k-1", "100000000000001", "CAPTURED", "TX-9001", new BigDecimal("40.00"), LocalDateTime.of(2026, 11, 21, 10, 30, 0));
        UUID capMedia = insertarOperacion(clienteA, usuarioA, cuentaA, "k-2", "100000000000002", "CAPTURED", null, new BigDecimal("80.00"), LocalDateTime.of(2026, 11, 21, 9, 15, 0));
        UUID capAntigua = insertarOperacion(clienteA, usuarioA, cuentaA, "k-3", "100000000000003", "CAPTURED", "TX-7003", new BigDecimal("120.00"), LocalDateTime.of(2026, 11, 20, 18, 0, 0));

        // Estados no capturados propios jamás deben listarse en historial ni abrirse en detalle.
        UUID failedA = insertarOperacion(clienteA, usuarioA, cuentaA, "k-4", "100000000000004", "FAILED", null, new BigDecimal("40.00"), LocalDateTime.of(2026, 11, 21, 11, 0, 0));
        UUID reservedA = insertarOperacion(clienteA, usuarioA, cuentaA, "k-5", "100000000000005", "RESERVED", null, new BigDecimal("40.00"), LocalDateTime.of(2026, 11, 21, 11, 5, 0));
        UUID processingA = insertarOperacion(clienteA, usuarioA, cuentaA, "k-6", "100000000000006", "PROCESSING", null, new BigDecimal("40.00"), LocalDateTime.of(2026, 11, 21, 11, 10, 0));
        UUID unknownA = insertarOperacion(clienteA, usuarioA, cuentaA, "k-7", "100000000000007", "UNKNOWN", null, new BigDecimal("40.00"), LocalDateTime.of(2026, 11, 21, 11, 15, 0));

        // Operación capturada de otro cliente (Bruno): jamás visible para Ana.
        UUID capturedB = insertarOperacion(clienteB, usuarioB, cuentaB, "kb-1", "200000000000001", "CAPTURED", "TX-B01", new BigDecimal("200.00"), LocalDateTime.of(2026, 11, 21, 11, 20, 0));

        // Página 0 (size=2)
        String p0Raw = mvc.perform(get("/api/gestopago/pagos/historial?page=0&size=2")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.page").value(0))
            .andExpect(jsonPath("datos.size").value(2))
            .andExpect(jsonPath("datos.totalElementos").value(3))
            .andExpect(jsonPath("datos.totalPaginas").value(2))
            .andExpect(jsonPath("datos.contenido.length()").value(2))
            .andExpect(jsonPath("datos.contenido[0].id").value(capReciente.toString()))
            .andExpect(jsonPath("datos.contenido[0].producto").value("Recarga 40"))
            .andExpect(jsonPath("datos.contenido[0].servicio").value("Oui Movil"))
            .andExpect(jsonPath("datos.contenido[0].referenciaInternaUpc").value("100000000000001"))
            .andExpect(jsonPath("datos.contenido[0].folioProveedorIdTx").value("TX-9001"))
            .andExpect(jsonPath("datos.contenido[0].estado").value("CAPTURED"))
            .andExpect(jsonPath("datos.contenido[1].id").value(capMedia.toString()))
            .andExpect(jsonPath("datos.contenido[1].folioProveedorIdTx").value(nullValue()))
            .andReturn().getResponse().getContentAsString();
        assertFalse(p0Raw.contains("v1.cifrado"));
        assertFalse(p0Raw.contains("FAILED"));

        // Página 1 (size=2)
        mvc.perform(get("/api/gestopago/pagos/historial?page=1&size=2")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.contenido.length()").value(1))
            .andExpect(jsonPath("datos.contenido[0].id").value(capAntigua.toString()));

        // Detalle propio CAPTURED
        mvc.perform(get("/api/gestopago/pagos/historial/" + capMedia)
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.id").value(capMedia.toString()))
            .andExpect(jsonPath("datos.referenciaInternaUpc").value("100000000000002"))
            .andExpect(jsonPath("datos.folioProveedorIdTx").value(nullValue()))
            .andExpect(jsonPath("datos.monto").value(80.00));

        // Acceso cruzado (operación CAPTURED de Bruno) -> 404 RECURSO_NO_ENCONTRADO
        mvc.perform(get("/api/gestopago/pagos/historial/" + capturedB)
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("codigo").value("RECURSO_NO_ENCONTRADO"));

        // Bruno sí puede ver su propia operación
        mvc.perform(get("/api/gestopago/pagos/historial/" + capturedB)
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.id").value(capturedB.toString()));

        // Detalle de estados no capturados propios -> 404 RECURSO_NO_ENCONTRADO
        for (UUID noCapturado : new UUID[]{failedA, reservedA, processingA, unknownA}) {
            mvc.perform(get("/api/gestopago/pagos/historial/" + noCapturado)
                    .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("codigo").value("RECURSO_NO_ENCONTRADO"));
        }

        // Validación de parámetros
        mvc.perform(get("/api/gestopago/pagos/historial?page=-1&size=10")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("codigo").value("VALIDACION"));
        mvc.perform(get("/api/gestopago/pagos/historial?page=0&size=51")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("codigo").value("VALIDACION"));

        // Sin token o tras logout -> 401 NO_AUTENTICADO
        mvc.perform(get("/api/gestopago/pagos/historial"))
            .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/gestopago/pagos/historial")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("codigo").value("NO_AUTENTICADO"));
    }
}
