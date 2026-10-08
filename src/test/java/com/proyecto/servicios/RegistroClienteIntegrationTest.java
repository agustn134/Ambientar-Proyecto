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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP real + JPA + SQL V3, aislado de GestoPago y de las bases del usuario. */
@SpringBootTest(classes=RegistroClienteIntegrationTest.Config.class, properties={
    "spring.datasource.url=jdbc:h2:mem:registro;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class RegistroClienteIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ConfigDB.class, RegistroClienteService.class, ClienteController.class,
        RegistroClienteExceptionHandler.class, GlobalExceptionHandler.class, PasswordConfig.class, LoggingAspect.class})
    static class Config {
        @Bean(name="flyway")
        Object schema(DataSource dataSource) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql"),
                    new ClassPathResource("db/migration/V5__create_usuarios.sql"))
                .execute(dataSource);
            return new Object();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
}
