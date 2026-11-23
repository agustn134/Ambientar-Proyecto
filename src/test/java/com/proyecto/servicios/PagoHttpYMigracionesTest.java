package com.proyecto.servicios;

import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.PagoController;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.*;
import com.proyecto.servicios.security.SecurityErrorHandler;
import com.proyecto.servicios.service.PagoReconciliator;
import com.proyecto.servicios.service.PagoService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PagoController.class)
@ActiveProfiles("live")
@ContextConfiguration(classes = {
    PagoController.class, SecurityConfig.class, SecurityErrorHandler.class,
    GlobalExceptionHandler.class, RegistroClienteExceptionHandler.class,
    RespuestaExitosaAdvice.class
})
class PagoHttpYMigracionesTest {
    @Autowired MockMvc mvc;
    @MockitoBean PagoService pagos;
    @MockitoBean PagoReconciliator reconciliator;
    @MockitoBean JwtDecoder decoder;

    private static final String SOLICITUD_JSON = """
        {"cuentaId":45,"pago":{"idServicio":78,"idProducto":219,"montoPago":40.00,"telefono":"5512345678"}}
        """;

    private void autenticar() {
        when(decoder.decode("token-m3")).thenReturn(Jwt.withTokenValue("token-m3")
            .header("alg", "RS256").subject("7").claim("rol", "CLIENTE").build());
    }

    @Test
    void crearPagoExigeJwtYIdempotencyKeyYDevuelve202SinAcreditarCaptura() throws Exception {
        mvc.perform(post("/api/gestopago/pagos")
                .header("Idempotency-Key", "idem-m3-0001")
                .contentType("application/json").content(SOLICITUD_JSON))
            .andExpect(status().isUnauthorized());

        autenticar();
        mvc.perform(post("/api/gestopago/pagos")
                .header("Authorization", "Bearer token-m3")
                .contentType("application/json").content(SOLICITUD_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("codigo").value("ENCABEZADO_REQUERIDO"));

        UUID id = UUID.randomUUID();
        when(pagos.crear(eq(7L), eq("idem-m3-0001"), any()))
            .thenReturn(new Pago(id, 45L, new BigDecimal("40.00"), Estado.RESERVED, false));

        mvc.perform(post("/api/gestopago/pagos")
                .header("Authorization", "Bearer token-m3")
                .header("Idempotency-Key", "idem-m3-0001")
                .contentType("application/json").content(SOLICITUD_JSON))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/gestopago/pagos/" + id))
            .andExpect(jsonPath("datos.id").value(id.toString()))
            .andExpect(jsonPath("datos.cuentaId").value(45))
            .andExpect(jsonPath("datos.estado").value("RESERVED"))
            .andExpect(jsonPath("datos.revisionManual").value(false))
            .andExpect(jsonPath("datos.telefono").doesNotExist());
    }

    @Test
    void crearPagoPropagaConflicto409YReglaFinanciera422() throws Exception {
        autenticar();
        when(pagos.crear(eq(7L), eq("idem-conflicto"), any()))
            .thenThrow(new PagoException(409, "IDEMPOTENCIA_CONFLICTO"));

        mvc.perform(post("/api/gestopago/pagos")
                .header("Authorization", "Bearer token-m3")
                .header("Idempotency-Key", "idem-conflicto")
                .contentType("application/json").content(SOLICITUD_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("codigo").value("IDEMPOTENCIA_CONFLICTO"));

        when(pagos.crear(eq(7L), eq("idem-saldo"), any()))
            .thenThrow(new PagoException(422, "SALDO_INSUFICIENTE"));

        mvc.perform(post("/api/gestopago/pagos")
                .header("Authorization", "Bearer token-m3")
                .header("Idempotency-Key", "idem-saldo")
                .contentType("application/json").content(SOLICITUD_JSON))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("codigo").value("SALDO_INSUFICIENTE"));
    }

    @Test
    void consultarConciliarYSaldoDevuelvenDtosReales() throws Exception {
        autenticar();
        UUID id = UUID.randomUUID();
        when(pagos.consultar(7L, id))
            .thenReturn(new Pago(id, 45L, new BigDecimal("40.00"), Estado.UNKNOWN, true));

        mvc.perform(get("/api/gestopago/pagos/" + id).header("Authorization", "Bearer token-m3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.estado").value("UNKNOWN"))
            .andExpect(jsonPath("datos.revisionManual").value(true));

        when(reconciliator.conciliar(7L, id))
            .thenReturn(new Pago(id, 45L, new BigDecimal("40.00"), Estado.CAPTURED, false));

        mvc.perform(post("/api/gestopago/pagos/" + id + "/conciliar")
                .header("Authorization", "Bearer token-m3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.estado").value("CAPTURED"))
            .andExpect(jsonPath("datos.revisionManual").value(false));

        when(pagos.saldo(7L, 45L))
            .thenReturn(new Saldo(45L, new BigDecimal("960.00"), new BigDecimal("0.00"), new BigDecimal("960.00")));

        mvc.perform(get("/api/gestopago/cuentas/45/saldo").header("Authorization", "Bearer token-m3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.cuentaId").value(45))
            .andExpect(jsonPath("datos.contabilizado").value(960.00))
            .andExpect(jsonPath("datos.reservado").value(0.00))
            .andExpect(jsonPath("datos.disponible").value(960.00));
    }

    @Test
    void catalogoMigracionesFlywayV1AV14SinColisionesYCoexistenciaEnEsquemaDescartable() throws IOException {
        var ds = new DriverManagerDataSource(
            "jdbc:h2:mem:m3_flyway_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa", ""
        );
        var flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .outOfOrder(true)
            .load();

        var infos = flyway.info().all();
        assertEquals(14, infos.length, "Deben existir exactamente 14 migraciones V1..V14 sin colisiones");
        var versiones = Arrays.stream(infos).map(m -> m.getVersion().getVersion()).toList();
        assertEquals(List.of("1","2","3","4","5","6","7","8","9","10","11","12","13","14"), versiones);

        var sql = new JdbcTemplate(ds);
        try {
            sql.execute("CREATE ALIAS IF NOT EXISTS clock_timestamp AS $$ java.sql.Timestamp clockTimestamp() { return new java.sql.Timestamp(System.currentTimeMillis()); } $$");
            new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__create_gestopago_tokens.sql"),
                new ClassPathResource("db/migration/V2__create_gestopago_productos.sql"),
                new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql"),
                new ClassPathResource("db/migration/V5__create_usuarios.sql"),
                new ClassPathResource("db/migration/V6__version_token_usuarios.sql"),
                new ClassPathResource("db/migration/V7__catalogos_mexico.sql"),
                new ClassPathResource("db/migration/V8__roles_y_fecha_registro.sql"),
                h2CompatResource("db/migration/V10__gestopago_token_cache_seguro.sql"),
                h2CompatResource("db/migration/V11__gestopago_cuota_catalogo.sql"),
                h2CompatResource("db/migration/V12__flujo_simulado.sql"),
                h2CompatResource("db/migration/V13__pagos_reales.sql"),
                new ClassPathResource("db/migration/V14__auth_otp_y_sesion_unica.sql")
            ).execute(ds);

            assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM gestopago_catalogo_consultas", Integer.class));
            assertEquals(0, sql.queryForObject("SELECT COUNT(*) FROM pago_operaciones", Integer.class));
            assertEquals(0, sql.queryForObject("SELECT COUNT(*) FROM usuario_sesiones_activas", Integer.class));
        } finally {
            sql.execute("DROP ALL OBJECTS");
        }
    }

    private static Resource h2CompatResource(String classpathLocation) throws IOException {
        String raw = new String(new ClassPathResource(classpathLocation).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String adapted = raw.replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
        return new ByteArrayResource(adapted.getBytes(StandardCharsets.UTF_8), classpathLocation);
    }
}
