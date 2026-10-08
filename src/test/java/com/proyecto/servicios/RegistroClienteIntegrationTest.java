package com.proyecto.servicios;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.controller.ClienteController;
import com.proyecto.servicios.service.RegistroClienteService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP real + JPA + SQL V3, aislado de GestoPago y de las bases del usuario. */
@SpringBootTest(classes=RegistroClienteIntegrationTest.Config.class, properties={
    "spring.datasource.url=jdbc:h2:mem:registro;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=false",
    "security.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
@AutoConfigureMockMvc
class RegistroClienteIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ConfigDB.class, RegistroClienteService.class, ClienteController.class,
        com.proyecto.servicios.repositorys.cliente.CatalogoRepository.class,com.proyecto.servicios.controller.CatalogoController.class,
        RegistroClienteExceptionHandler.class, GlobalExceptionHandler.class, PasswordConfig.class, LoggingAspect.class,
        JwtConfig.class,SecurityConfig.class,com.proyecto.servicios.security.UsuarioJwtValidator.class,
        com.proyecto.servicios.security.SecurityErrorHandler.class,com.proyecto.servicios.service.LoginService.class,
        com.proyecto.servicios.service.JwtService.class,com.proyecto.servicios.service.UsuarioSesionService.class,
        com.proyecto.servicios.controller.AuthController.class})
    static class Config {
        @Bean(name="flyway")
        Object schema(DataSource dataSource) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql"),
                    new ClassPathResource("db/migration/V5__create_usuarios.sql"),
                    new ClassPathResource("db/migration/V6__version_token_usuarios.sql"),
                    new ClassPathResource("db/migration/V7__catalogos_mexico.sql"),
                    new ClassPathResource("catalogo-postal-prueba.sql"))
                .execute(dataSource);
            return new Object();
        }
        @Bean(name="catalogoPostal") Object postalPrueba() { return new Object(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test void catalogosPublicosPermitenPrepararElRegistro() throws Exception {
        mvc.perform(get("/catalogos/sexos")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1)).andExpect(jsonPath("$[1].id").value(2));
        mvc.perform(get("/catalogos/nacionalidades")).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].descripcion").value("Mexicana"));
        mvc.perform(get("/catalogos/paises")).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].descripcion").value("México"));
        mvc.perform(get("/catalogos/codigos-postales/37907")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(110333891)).andExpect(jsonPath("$[0].colonia").value("San Isidro"));
        mvc.perform(get("/catalogos/codigos-postales/00000")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/catalogos/codigos-postales/ABC")).andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("VALIDACION"));
    }

    @Test void registroRechazaReferenciasInexistentesSinGuardar() throws Exception {
        for (String campo : new String[]{"sexoId","nacionalidadId","estadoCivilId","paisId","asentamientoId"}) {
            ObjectNode body=valido();
            ObjectNode destino=campo.equals("paisId") || campo.equals("asentamientoId") ? (ObjectNode)body.get("domicilio") : body;
            destino.put(campo,999);
            enviar(body).andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("VALIDACION"));
            vacio();
        }
    }

    @Test void idsDeCatalogoNoAceptanTextoDecimalesNiBooleanos() throws Exception {
        for (String valor : new String[]{"\"1\"","1.0","true","2147483648"}) {
            ObjectNode body=valido();
            body.set("sexoId",mapper.readTree(valor));
            enviar(body).andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("JSON_INVALIDO"));
            vacio();
        }
    }

    @Test void domicilioDebeCorresponderAlAsentamientoYSeDerivaDelCatalogo() throws Exception {
        ObjectNode body=valido();
        ((ObjectNode)body.get("domicilio")).put("codigoPostal","37900");
        enviar(body).andExpect(status().isBadRequest());
        vacio();
        enviar(valido()).andExpect(status().isCreated());
        assertEquals("San Isidro",sql.queryForObject("SELECT colonia FROM domicilios",String.class));
        assertEquals(110333891,sql.queryForObject("SELECT asentamiento_id FROM domicilios",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT sexo_id FROM clientes",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> sql.update("UPDATE clientes SET sexo_id=999"));
    }

    @Test void opcionDeshabilitadaNoPuedeRegistrarse() throws Exception {
        sql.update("UPDATE cat_sexos SET activo=FALSE WHERE id=1");
        try {
            enviar(valido()).andExpect(status().isBadRequest());
            vacio();
            mvc.perform(get("/catalogos/sexos")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        } finally { sql.update("UPDATE cat_sexos SET activo=TRUE WHERE id=1"); }
    }

    @Test void admiteRfcDeDoceCaracteresYRechazaSuFechaImposible() throws Exception {
        ObjectNode body=hermana();
        body.put("rfc","ABC051332AB1");
        enviar(body).andExpect(status().isBadRequest());
        vacio();
        body.put("rfc","ABC051108AB1");
        enviar(body).andExpect(status().isCreated());
        assertEquals("ABC051108AB1",sql.queryForObject("SELECT rfc FROM clientes",String.class));
    }

    @BeforeEach void limpiar() {
        sql.update("DELETE FROM usuarios");
        sql.update("DELETE FROM cuentas");
        sql.update("DELETE FROM domicilios");
        sql.update("DELETE FROM clientes");
    }

    ObjectNode valido() throws Exception {
        return (ObjectNode) mapper.readTree(new ClassPathResource("registro-cliente-valido.json").getInputStream());
    }

    ObjectNode hermana() throws Exception {
        return (ObjectNode) mapper.readTree(new ClassPathResource("registro-cliente-hermana.json").getInputStream());
    }

    org.springframework.test.web.servlet.ResultActions enviar(ObjectNode body) throws Exception {
        return mvc.perform(post("/clientes").contentType("application/json").content(body.toString()));
    }

    void vacio() {
        for (String tabla : new String[]{"clientes", "domicilios", "cuentas", "usuarios"}) {
            assertEquals(0, sql.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class));
        }
    }

    @Test void registraNormalizaYRelaciona() throws Exception {
        ObjectNode body = valido();
        body.put("nombre", "  Agustín   López ");
        body.put("correoElectronico", "  " + body.get("correoElectronico").asText().toUpperCase(java.util.Locale.ROOT) + " ");
        body.put("curp", body.get("curp").asText().toLowerCase());
        String result = enviar(body).andExpect(status().isCreated())
            .andExpect(jsonPath("cuenta.saldo").value(0))
            .andExpect(jsonPath("cuenta.estatus").value("ACTIVA")).andReturn().getResponse().getContentAsString();
        var response = mapper.readTree(result);
        assertTrue(response.get("cuenta").get("numeroCuenta").asText().matches("[0-9]{20}"));
        assertEquals("Agustín López", sql.queryForObject("SELECT nombre FROM clientes", String.class));
        assertEquals(valido().get("correoElectronico").asText(), sql.queryForObject("SELECT correo_electronico FROM clientes", String.class));
        assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM domicilios d JOIN clientes c ON d.cliente_id=c.id JOIN cuentas a ON a.cliente_id=c.id", Integer.class));
    }

    @Test void rechazaCamposInvalidosSinPersistir() throws Exception {
        ObjectNode body=hermana();
        body.put("nombre", "Dulce@");
        body.put("telefonoMovil", "123");
        ((ObjectNode) body.get("domicilio")).put("codigoPostal", "ABCDE");
        ((ObjectNode) body.get("informacionLaboral")).put("ingresoMensual", -1);
        enviar(body).andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("VALIDACION"));
        vacio();
    }

    @Test void rechazaCoercionesYFechaImposible() throws Exception {
        for (String campo : new String[]{"nombre", "telefonoMovil", "fechaNacimiento", "ingresoMensual"}) {
            ObjectNode body=valido();
            switch (campo) {
                case "nombre" -> body.put(campo, 1234);
                case "telefonoMovil" -> body.put(campo, 5512345678L);
                case "fechaNacimiento" -> body.put(campo, "2000-02-30");
                default -> ((ObjectNode) body.get("informacionLaboral")).put(campo, "15000");
            }
            enviar(body).andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("JSON_INVALIDO"));
            vacio();
        }
    }

    @Test void rechazaMenorYAceptaExactamente18() throws Exception {
        ObjectNode body=valido();
        body.put("fechaNacimiento", java.time.LocalDate.now().minusYears(18).plusDays(1).toString());
        enviar(body).andExpect(status().isBadRequest());
        vacio();
        body.put("fechaNacimiento", java.time.LocalDate.now().minusYears(18).toString());
        enviar(body).andExpect(status().isCreated());
    }

    @Test void duplicadosRetornan409SinCrearOtraCuenta() throws Exception {
        ObjectNode primero=valido();
        enviar(primero).andExpect(status().isCreated());
        for (String campo : new String[]{"curp", "rfc", "correoElectronico"}) {
            ObjectNode otro=hermana();
            String repetido=primero.get(campo).asText();
            otro.put(campo, campo.equals("correoElectronico") ? repetido.toUpperCase(java.util.Locale.ROOT) : repetido);
            enviar(otro).andExpect(status().isConflict()).andExpect(jsonPath("errores[0].campo").value(campo));
        }
        assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class));
        assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM cuentas", Integer.class));
    }

    @Test void fallaAlGuardarCuentaRevierteClienteYDomicilio() throws Exception {
        sql.execute("ALTER TABLE cuentas ADD CONSTRAINT fallo_prueba CHECK (saldo > 0)");
        try {
            enviar(valido()).andExpect(status().isInternalServerError());
            vacio();
        } finally {
            sql.execute("ALTER TABLE cuentas DROP CONSTRAINT fallo_prueba");
        }
    }

    @Test void limitesNombresOpcionalesYCamposFaltantes() throws Exception {
        for (int length : new int[]{2,39}) {
            ObjectNode body=valido(); body.put("nombre", "A".repeat(length));
            enviar(body).andExpect(status().isBadRequest()); vacio();
        }
        ObjectNode body=valido(); body.remove("domicilio");
        enviar(body).andExpect(status().isBadRequest()); vacio();
        body=valido(); body.put("nombre", "A".repeat(38));
        body.put("apellidoPaterno", "Á".repeat(50));
        enviar(body).andExpect(status().isCreated());
    }

    @Test void creaUsuarioConHashSinExponerPassword() throws Exception {
        ObjectNode body=valido();
        String password=body.get("password").asText();
        String response=enviar(body).andExpect(status().isCreated())
            .andExpect(jsonPath("usuario.activo").value(true))
            .andExpect(jsonPath("usuario.correo").value(body.get("correoElectronico").asText()))
            .andExpect(jsonPath("usuario.password").doesNotExist())
            .andExpect(jsonPath("usuario.passwordHash").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String hash=sql.queryForObject("SELECT password_hash FROM usuarios", String.class);
        assertNotEquals(password, hash);
        assertTrue(hash.startsWith("$2a$10$"));
        assertTrue(passwordEncoder.matches(password, hash));
        assertFalse(passwordEncoder.matches("OtroPassword123!", hash));
        assertFalse(response.contains(password));
        assertEquals(0, sql.queryForObject("SELECT intentos_fallidos FROM usuarios", Integer.class));
        assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM usuarios u JOIN clientes c ON c.id=u.cliente_id WHERE u.correo=c.correo_electronico", Integer.class));
        var request=mapper.treeToValue(body, com.proyecto.servicios.model.cliente.RegistroClienteRequest.class);
        assertEquals(password, request.password());
        assertFalse(request.toString().contains(password));
        assertFalse(mapper.writeValueAsString(request).contains(password));
    }

    @Test void passwordInvalidaNoCreaNingunRegistro() throws Exception {
        for (String password : new String[]{"", "Ab1!", "sinmayuscula1!", "SINMINUSCULA1!",
                "SinNumeros!", "SinEspecial123", " ConEspacio123!", "ConEspacio123! ",
                "Abc123!" + "x".repeat(66), "Abc123!" + "é".repeat(33), "Abc123!\n"}) {
            ObjectNode body=valido(); body.put("password", password);
            enviar(body).andExpect(status().isBadRequest()).andExpect(jsonPath("errores[0].campo").value("password"));
            vacio();
        }
        for (boolean omit : new boolean[]{true,false}) {
            ObjectNode body=valido();
            if (omit) body.remove("password"); else body.putNull("password");
            enviar(body).andExpect(status().isBadRequest()); vacio();
        }
    }

    @Test void passwordNumericaNoSeConvierteATexto() throws Exception {
        ObjectNode body=valido(); body.put("password", 12345678);
        enviar(body).andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("JSON_INVALIDO"));
        vacio();
    }

    @Test void passwordLimite72BytesAceptadaConSaltDistinto() throws Exception {
        ObjectNode body=valido();
        String password="Abc123!" + "x".repeat(65);
        body.put("password", password);
        enviar(body).andExpect(status().isCreated());
        String first=sql.queryForObject("SELECT password_hash FROM usuarios", String.class);
        body=hermana();
        body.put("password", password);
        enviar(body).andExpect(status().isCreated());
        var hashes=sql.queryForList("SELECT password_hash FROM usuarios", String.class);
        assertEquals(2,hashes.size()); assertNotEquals(hashes.get(0), hashes.get(1));
        assertTrue(hashes.stream().allMatch(h -> passwordEncoder.matches(password,h)));
        assertTrue(passwordEncoder.matches(password, first));
    }

    @Test void fallaUsuarioRevierteClienteDomicilioYCuenta() throws Exception {
        sql.execute("ALTER TABLE usuarios ADD CONSTRAINT fallo_usuario_prueba CHECK (activo = FALSE)");
        try {
            enviar(valido()).andExpect(status().isInternalServerError());
            vacio();
        } finally {
            sql.execute("ALTER TABLE usuarios DROP CONSTRAINT fallo_usuario_prueba");
        }
    }

    String registrarYToken() throws Exception {
        enviar(valido()).andExpect(status().isCreated());
        return iniciarSesion(valido().get("password").asText()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    org.springframework.test.web.servlet.ResultActions iniciarSesion(String password) throws Exception {
        ObjectNode request=mapper.createObjectNode();
        request.put("correo",valido().get("correoElectronico").asText());
        request.put("password",password);
        return mvc.perform(post("/auth/login").contentType("application/json").content(request.toString()));
    }

    String extraerToken(String response) throws Exception { return mapper.readTree(response).get("accessToken").asText(); }

    String tokenFirmado(java.time.Instant expiry, String issuer, String audience, long version) {
        Long id=sql.queryForObject("SELECT id FROM usuarios",Long.class);
        var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(issuer)
            .audience(java.util.List.of(audience)).subject(id.toString())
            .issuedAt(java.time.Instant.now().minusSeconds(400)).notBefore(java.time.Instant.now().minusSeconds(400))
            .expiresAt(expiry).claim("ver",version).build();
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }

    @Test void loginEmiteJwtYPerfilPropioSinDatosSensibles() throws Exception {
        String response=registrarYToken();
        var body=mapper.readTree(response);
        assertEquals("Bearer",body.get("tokenType").asText()); assertEquals(300,body.get("expiresIn").asLong());
        String token=extraerToken(response);
        var jwt=jwtDecoder.decode(token);
        assertEquals("http://localhost:8080",jwt.getIssuer().toString());
        assertTrue(jwt.getAudience().contains("gestopago-api"));
        assertFalse(jwt.getClaims().containsKey("correo")); assertFalse(jwt.getClaims().containsKey("password"));
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("correo").value(valido().get("correoElectronico").asText()))
            .andExpect(jsonPath("passwordHash").doesNotExist());
    }

    @Test void rechazoConfirmaContadorYExitoLoReinicia() throws Exception {
        enviar(valido()).andExpect(status().isCreated());
        iniciarSesion("Incorrecta123!").andExpect(status().isUnauthorized());
        assertEquals(1,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
        iniciarSesion("Incorrecta123!").andExpect(status().isUnauthorized());
        assertEquals(2,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
        iniciarSesion(valido().get("password").asText()).andExpect(status().isOk());
        assertEquals(0,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
    }

    @Test void tresIntentosBloqueanSinDesactivarCuentaYRevocanJwt() throws Exception {
        String token=extraerToken(registrarYToken());
        for (int i=0;i<3;i++) iniciarSesion("Incorrecta123!").andExpect(status().isUnauthorized())
            .andExpect(jsonPath("codigo").value("CREDENCIALES_INVALIDAS"));
        assertEquals(3,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
        assertFalse(sql.queryForObject("SELECT activo FROM usuarios",Boolean.class));
        assertEquals("ACTIVA",sql.queryForObject("SELECT estatus FROM cuentas",String.class));
        assertEquals(1,sql.queryForObject("SELECT version_token FROM usuarios",Long.class));
        iniciarSesion(valido().get("password").asText()).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        sql.update("UPDATE usuarios SET activo=TRUE, intentos_fallidos=0");
        // Reactivar no vuelve a habilitar tokens anteriores al bloqueo.
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }

    @Test void tokenAusenteAlteradoVencidoOConClaimsIncorrectosSeRechaza() throws Exception {
        String token=extraerToken(registrarYToken());
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        var parts=token.split("\\.");
        parts[2]=(parts[2].charAt(0)=='A'?"B":"A")+parts[2].substring(1);
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+String.join(".",parts))).andExpect(status().isUnauthorized());
        String expired=tokenFirmado(java.time.Instant.now().minusSeconds(1),"http://localhost:8080","gestopago-api",0);
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        for (String invalid : new String[]{
            tokenFirmado(java.time.Instant.now().plusSeconds(300),"otro-emisor","gestopago-api",0),
            tokenFirmado(java.time.Instant.now().plusSeconds(300),"http://localhost:8080","otra-audiencia",0),
            tokenFirmado(java.time.Instant.now().plusSeconds(300),"http://localhost:8080","gestopago-api",9)}) {
            mvc.perform(get("/auth/me").header("Authorization","Bearer "+invalid)).andExpect(status().isUnauthorized());
        }
    }

    @Test void usuarioInexistenteOInactivoTieneMismoErrorGenerico() throws Exception {
        String nonexistent=iniciarSesion("Incorrecta123!").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals("CREDENCIALES_INVALIDAS",mapper.readTree(nonexistent).get("codigo").asText());
        enviar(valido()).andExpect(status().isCreated());
        sql.update("UPDATE usuarios SET activo=FALSE");
        iniciarSesion(valido().get("password").asText()).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("mensaje").value("Credenciales inválidas o acceso no disponible"));
        assertEquals(0,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
    }

    @Test void loginNormalizaCorreoPeroNoPasswordYRechazaTiposIncorrectos() throws Exception {
        enviar(valido()).andExpect(status().isCreated());
        var request=mapper.createObjectNode().put("correo","  "+valido().get("correoElectronico").asText().toUpperCase()+" ")
            .put("password",valido().get("password").asText());
        mvc.perform(post("/auth/login").contentType("application/json").content(request.toString())).andExpect(status().isOk());
        iniciarSesion(" "+valido().get("password").asText()).andExpect(status().isUnauthorized());
        assertEquals(1,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
        request.put("password",12345678);
        mvc.perform(post("/auth/login").contentType("application/json").content(request.toString())).andExpect(status().isBadRequest());
        request.put("password","A".repeat(73));
        mvc.perform(post("/auth/login").contentType("application/json").content(request.toString())).andExpect(status().isBadRequest());
        assertEquals(1,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
    }

    @Test void cuentaInactivaPermiteLoginPeroClienteInactivoNo() throws Exception {
        enviar(valido()).andExpect(status().isCreated());
        sql.update("UPDATE cuentas SET estatus='INACTIVA'");
        String token=extraerToken(iniciarSesion(valido().get("password").asText()).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        sql.update("UPDATE clientes SET estatus='INACTIVO'");
        iniciarSesion(valido().get("password").asText()).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }

    @Test void solicitudesConcurrentesNoPierdenIntentos() throws Exception {
        enviar(valido()).andExpect(status().isCreated());
        var executor=java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i=0;i<3;i++) futures.add(executor.submit(() -> iniciarSesion("Incorrecta123!")
                .andReturn().getResponse().getStatus()));
            for (var future : futures) assertEquals(401,future.get(30,java.util.concurrent.TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(3,sql.queryForObject("SELECT intentos_fallidos FROM usuarios",Integer.class));
        assertFalse(sql.queryForObject("SELECT activo FROM usuarios",Boolean.class));
    }

    @Test void autenticadoSinPermisoObtiene403() throws Exception {
        String token=extraerToken(registrarYToken());
        mvc.perform(post("/personas").header("Authorization","Bearer "+token).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("codigo").value("ACCESO_DENEGADO"));
    }
}
