package com.proyecto.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.*;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Auth2faSesionUnicaIntegrationTest.Config.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:auth2fa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
    "auth.otp.buzon-local-pruebas=true",
    "security.otp.ttl-seconds=120",
    "security.otp.max-intentos=3",
    "security.otp.max-reenvios=3"
})
@AutoConfigureMockMvc
class Auth2faSesionUnicaIntegrationTest {
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
        JwtService.class, UsuarioSesionService.class, AuthController.class
    })
    static class Config {
        @Bean(name = "flyway")
        Object schema(DataSource dataSource) {
            new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql"),
                new ClassPathResource("db/migration/V5__create_usuarios.sql"),
                new ClassPathResource("db/migration/V6__version_token_usuarios.sql"),
                new ClassPathResource("db/migration/V7__catalogos_mexico.sql"),
                new ClassPathResource("db/migration/V8__roles_y_fecha_registro.sql"),
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
    @Autowired OtpCorreoService otpCorreo;

    @BeforeEach
    void limpiar() {
        otpCorreo.limpiarBuzonPruebas();
        sql.update("DELETE FROM usuario_desafios_otp");
        sql.update("DELETE FROM usuario_sesiones_activas");
        sql.update("DELETE FROM usuarios");
        sql.update("DELETE FROM cuentas");
        sql.update("DELETE FROM domicilios");
        sql.update("DELETE FROM clientes");
    }

    private ObjectNode registrarClienteValido() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(
            new ClassPathResource("registro-cliente-valido.json").getInputStream());
        mvc.perform(post("/clientes").contentType("application/json").content(body.toString()))
            .andExpect(status().isCreated());
        return body;
    }

    private JsonNode iniciarLogin(String correo, String password, String plataforma) throws Exception {
        ObjectNode req = mapper.createObjectNode().put("correo", correo).put("password", password);
        if (plataforma != null) {
            req.put("plataforma", plataforma);
        }
        String raw = mvc.perform(post("/auth/login").contentType("application/json").content(req.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return mapper.readTree(raw).get("datos");
    }

    @Test
    void loginEmiteDesafioOtpSinJwtYCodigoSeAlmacenaCifradoYDeUnSoloUso() throws Exception {
        ObjectNode cliente = registrarClienteValido();
        String correo = cliente.get("correoElectronico").asText();
        String password = cliente.get("password").asText();

        JsonNode desafio = iniciarLogin(correo, password, "ANDROID");
        assertTrue(desafio.get("requiereOtp").asBoolean());
        assertEquals("ANDROID", desafio.get("plataforma").asText());
        assertNull(desafio.get("accessToken"));
        String desafioId = desafio.get("desafioId").asText();

        String codigo = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        assertNotNull(codigo);
        assertTrue(codigo.matches("[0-9]{6}"));

        String otpHash = sql.queryForObject("SELECT otp_hash FROM usuario_desafios_otp WHERE id = ?", String.class,
            java.util.UUID.fromString(desafioId));
        assertNotEquals(codigo, otpHash);
        assertTrue(otpHash.startsWith("$2a$10$"));

        ObjectNode verificar = mapper.createObjectNode().put("desafioId", desafioId).put("codigoOtp", codigo);
        String loginRaw = mvc.perform(post("/auth/otp/verificar").contentType("application/json").content(verificar.toString()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode loginDatos = mapper.readTree(loginRaw).get("datos");
        String token = loginDatos.get("accessToken").asText();
        assertEquals("ANDROID", loginDatos.get("plataforma").asText());
        assertNotNull(loginDatos.get("sesionId").asText());

        // Reutilizar el mismo OTP es rechazado.
        mvc.perform(post("/auth/otp/verificar").contentType("application/json").content(verificar.toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("codigo").value("OTP_INVALIDO"));

        // El JWT emitido permite consumir perfil y cuentas propias con cuentaId.
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos.correo").value(correo));
        mvc.perform(get("/cuentas/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("datos[0].cuentaId").isNumber())
            .andExpect(jsonPath("datos[0].estatus").value("ACTIVA"));
    }

    @Test
    void rechazaOtpExpiradoIntentosAgotadosYLimiteDeReenvios() throws Exception {
        ObjectNode cliente = registrarClienteValido();
        String correo = cliente.get("correoElectronico").asText();
        String password = cliente.get("password").asText();

        // 1. OTP expirado
        JsonNode desafioExpirado = iniciarLogin(correo, password, "WEB");
        String idExpirado = desafioExpirado.get("desafioId").asText();
        String codigoExpirado = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        sql.update("UPDATE usuario_desafios_otp SET expira_en = ? WHERE id = ?",
            Timestamp.from(Instant.now().minusSeconds(10)), java.util.UUID.fromString(idExpirado));

        ObjectNode verificarExpirado = mapper.createObjectNode().put("desafioId", idExpirado).put("codigoOtp", codigoExpirado);
        mvc.perform(post("/auth/otp/verificar").contentType("application/json").content(verificarExpirado.toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("codigo").value("OTP_EXPIRADO"));

        // 2. Tres intentos fallidos bloquean el desafío
        JsonNode desafioBloqueo = iniciarLogin(correo, password, "WEB");
        String idBloqueo = desafioBloqueo.get("desafioId").asText();
        String codigoReal = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        String codigoIncorrecto = codigoReal.equals("000000") ? "111111" : "000000";
        ObjectNode intentoMalo = mapper.createObjectNode().put("desafioId", idBloqueo).put("codigoOtp", codigoIncorrecto);

        mvc.perform(post("/auth/otp/verificar").contentType("application/json").content(intentoMalo.toString()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value("OTP_INVALIDO"));
        mvc.perform(post("/auth/otp/verificar").contentType("application/json").content(intentoMalo.toString()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value("OTP_INVALIDO"));
        mvc.perform(post("/auth/otp/verificar").contentType("application/json").content(intentoMalo.toString()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value("OTP_BLOQUEADO"));

        // Incluso el código correcto queda rechazado después de bloquear el desafío.
        ObjectNode intentoTarde = mapper.createObjectNode().put("desafioId", idBloqueo).put("codigoOtp", codigoReal);
        mvc.perform(post("/auth/otp/verificar").contentType("application/json").content(intentoTarde.toString()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value("OTP_BLOQUEADO"));

        // 3. Reenvíos: código anterior se invalida y se respeta el límite de 3 reenvíos.
        JsonNode desafioReenvio = iniciarLogin(correo, password, "ANDROID");
        String idReenvio = desafioReenvio.get("desafioId").asText();
        String primerCodigo = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        ObjectNode reqReenvio = mapper.createObjectNode().put("desafioId", idReenvio);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/otp/reenviar").contentType("application/json").content(reqReenvio.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("datos.reenviosRestantes").value(2 - i));
        }
        mvc.perform(post("/auth/otp/reenviar").contentType("application/json").content(reqReenvio.toString()))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("codigo").value("LIMITE_REENVIOS_OTP"));

        String ultimoCodigo = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        if (!primerCodigo.equals(ultimoCodigo)) {
            mvc.perform(post("/auth/otp/verificar").contentType("application/json")
                    .content(mapper.createObjectNode().put("desafioId", idReenvio).put("codigoOtp", primerCodigo).toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value("OTP_INVALIDO"));
        }
        mvc.perform(post("/auth/otp/verificar").contentType("application/json")
                .content(mapper.createObjectNode().put("desafioId", idReenvio).put("codigoOtp", ultimoCodigo).toString()))
            .andExpect(status().isOk());
    }

    @Test
    void conflictoWebAndroidExigeConfirmacionExplicitaYRevocaJwtAnteriorYLogoutRevoca() throws Exception {
        ObjectNode cliente = registrarClienteValido();
        String correo = cliente.get("correoElectronico").asText();
        String password = cliente.get("password").asText();

        // Sesión 1 en WEB
        JsonNode dWeb = iniciarLogin(correo, password, "WEB");
        String cWeb = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        String webJson = mvc.perform(post("/auth/otp/verificar").contentType("application/json")
                .content(mapper.createObjectNode().put("desafioId", dWeb.get("desafioId").asText()).put("codigoOtp", cWeb).toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String tokenWeb = mapper.readTree(webJson).get("datos").get("accessToken").asText();

        // Nuevo intento en ANDROID detecta conflicto tras verificar OTP, sin cerrar WEB todavía.
        JsonNode dAndroid = iniciarLogin(correo, password, "ANDROID");
        String cAndroid = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        String conflictoRaw = mvc.perform(post("/auth/otp/verificar").contentType("application/json")
                .content(mapper.createObjectNode().put("desafioId", dAndroid.get("desafioId").asText()).put("codigoOtp", cAndroid).toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("codigo").value("CONFLICTO_SESION_ACTIVA"))
            .andExpect(jsonPath("datos.plataformaActiva").value("WEB"))
            .andExpect(jsonPath("datos.plataformaNueva").value("ANDROID"))
            .andReturn().getResponse().getContentAsString();
        JsonNode conflicto = mapper.readTree(conflictoRaw).get("datos");

        // Mientras no se confirme explícitamente, WEB sigue activo.
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenWeb))
            .andExpect(status().isOk());

        // Sin confirmarCierre=true es rechazado con 400.
        mvc.perform(post("/auth/sesion/reemplazar").contentType("application/json")
                .content(mapper.createObjectNode()
                    .put("desafioId", conflicto.get("desafioId").asText())
                    .put("tokenConfirmacion", conflicto.get("tokenConfirmacion").asText())
                    .put("confirmarCierre", false).toString()))
            .andExpect(status().isBadRequest());

        // Confirmación explícita reemplaza la sesión y revoca el JWT de WEB.
        String androidRaw = mvc.perform(post("/auth/sesion/reemplazar").contentType("application/json")
                .content(mapper.createObjectNode()
                    .put("desafioId", conflicto.get("desafioId").asText())
                    .put("tokenConfirmacion", conflicto.get("tokenConfirmacion").asText())
                    .put("confirmarCierre", true).toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String tokenAndroid = mapper.readTree(androidRaw).get("datos").get("accessToken").asText();

        assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM usuario_sesiones_activas WHERE activa = TRUE", Integer.class));
        assertEquals("ANDROID", sql.queryForObject("SELECT plataforma FROM usuario_sesiones_activas", String.class));

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenWeb))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenAndroid))
            .andExpect(status().isOk());

        // Reemplazar nuevamente con el mismo tokenConfirmacion es rechazado.
        mvc.perform(post("/auth/sesion/reemplazar").contentType("application/json")
                .content(mapper.createObjectNode()
                    .put("desafioId", conflicto.get("desafioId").asText())
                    .put("tokenConfirmacion", conflicto.get("tokenConfirmacion").asText())
                    .put("confirmarCierre", true).toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("codigo").value("CONFIRMACION_SESION_INVALIDA"));

        // Logout remoto revoca el JWT vigente de ANDROID.
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + tokenAndroid))
            .andExpect(status().isNoContent());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenAndroid))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void iniciosSimultaneosWebYAndroidGarantizanUnUnicoRegistroActivo() throws Exception {
        ObjectNode cliente = registrarClienteValido();
        String correo = cliente.get("correoElectronico").asText();
        String password = cliente.get("password").asText();

        JsonNode dWeb = iniciarLogin(correo, password, "WEB");
        String cWeb = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        JsonNode dAndroid = iniciarLogin(correo, password, "ANDROID");
        String cAndroid = otpCorreo.obtenerUltimoCodigoParaPruebas(correo);
        // Al crear el segundo desafío, el primero quedó cancelado; probemos 2 hilos verificando al mismo tiempo
        // sobre desafíos válidos distintos o dos usuarios/hilos compitiendo por el mismo usuario:
        // Reactivamos dWeb como PENDIENTE para simular dos desafíos válidos concurrentes del mismo usuario.
        sql.update("UPDATE usuario_desafios_otp SET estado = 'PENDIENTE' WHERE id = ?",
            java.util.UUID.fromString(dWeb.get("desafioId").asText()));

        CountDownLatch listo = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> fWeb = pool.submit(() -> {
                listo.await(5, TimeUnit.SECONDS);
                return mvc.perform(post("/auth/otp/verificar").contentType("application/json")
                        .content(mapper.createObjectNode()
                            .put("desafioId", dWeb.get("desafioId").asText())
                            .put("codigoOtp", cWeb).toString()))
                    .andReturn();
            });
            Future<MvcResult> fAndroid = pool.submit(() -> {
                listo.await(5, TimeUnit.SECONDS);
                return mvc.perform(post("/auth/otp/verificar").contentType("application/json")
                        .content(mapper.createObjectNode()
                            .put("desafioId", dAndroid.get("desafioId").asText())
                            .put("codigoOtp", cAndroid).toString()))
                    .andReturn();
            });
            listo.countDown();

            MvcResult r1 = fWeb.get(15, TimeUnit.SECONDS);
            MvcResult r2 = fAndroid.get(15, TimeUnit.SECONDS);
            List<Integer> statuses = List.of(r1.getResponse().getStatus(), r2.getResponse().getStatus());
            assertTrue(statuses.contains(200), "Uno de los inicios simultáneos debe obtener sesión: " + statuses);
            assertTrue(statuses.contains(409), "El segundo inicio simultáneo debe recibir conflicto 409: " + statuses);

            assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM usuario_sesiones_activas WHERE activa = TRUE", Integer.class));
            String ganadorJson = r1.getResponse().getStatus() == 200
                ? r1.getResponse().getContentAsString()
                : r2.getResponse().getContentAsString();
            String tokenGanador = mapper.readTree(ganadorJson).get("datos").get("accessToken").asText();
            mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenGanador))
                .andExpect(status().isOk());
        } finally {
            pool.shutdownNow();
        }
    }
}
