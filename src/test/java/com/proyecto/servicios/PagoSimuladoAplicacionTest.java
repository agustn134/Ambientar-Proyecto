package com.proyecto.servicios;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Arranca App real: login, JWT, JPA+JDBC, Flyway, cifrado y HTTP; solo se omite la carga postal. */
@SpringBootTest(classes=App.class)
@AutoConfigureMockMvc
@ActiveProfiles("simulacion")
@DirtiesContext
@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PagoSimuladoAplicacionTest {
    static final String SCHEMA="test_sim_app_"+UUID.randomUUID().toString().replace("-","");
    static JdbcTemplate admin;
    @MockitoBean(name="catalogoPostal") Object postal;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired @Qualifier("sfDatasource") DataSource dataSource;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties){
        String url=System.getenv("POSTGRES_TEST_URL"),user=System.getenv("POSTGRES_TEST_USER"),password=System.getenv("POSTGRES_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));admin.execute("CREATE SCHEMA "+SCHEMA);
        properties.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA);
        properties.add("spring.datasource.username",()->user);properties.add("spring.datasource.password",()->password);
        properties.add("spring.flyway.schemas",()->SCHEMA);
        properties.add("security.jwt.secret",()->Base64.getEncoder().encodeToString(new byte[32]));
        properties.add("simulacion.storage-key-base64",()->Base64.getEncoder().encodeToString(new byte[32]));
    }
    @AfterAll void cleanup(){if(admin!=null && SCHEMA.matches("test_sim_app_[a-f0-9]{32}"))admin.execute("DROP SCHEMA "+SCHEMA+" CASCADE");}
    @Test void desdeLoginHastaTimeoutYConciliacionConConfiguracionReal()throws Exception{
        var sql=new JdbcTemplate(dataSource);
        long cliente=sql.queryForObject("""
            INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual,sexo_id,nacionalidad_id,estado_civil_id)
            VALUES ('Carlos','Parra','Perez','1990-01-01','CAPP900101HGTBCDA0','CAPP900101001','sim@example.com','5512345678','QA','Prueba',100,1,1,7) RETURNING id
            """,Long.class);
        sql.update("INSERT INTO usuarios(cliente_id,correo,password_hash) VALUES (?,'sim@example.com',?)",cliente,new BCryptPasswordEncoder().encode("SoloPrueba2026!"));
        long cuenta=sql.queryForObject("INSERT INTO cuentas(cliente_id,numero_cuenta,saldo) VALUES (?,'00000000000000000001',1000) RETURNING id",Long.class,cliente);
        var login=mvc.perform(post("/auth/login").contentType("application/json").content("{\"correo\":\"sim@example.com\",\"password\":\"SoloPrueba2026!\"}"))
                .andExpect(status().isOk()).andReturn();
        String auth="Bearer "+mapper.readTree(login.getResponse().getContentAsString()).path("datos").path("accessToken").asText();
        mvc.perform(get("/api/simulacion/productos").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("datos.length()").value(2));
        mvc.perform(post("/api/simulacion/cuentas/"+cuenta+"/fondear").header("Authorization",auth).contentType("application/json").content("{\"monto\":200.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.disponible").value(200));
        String body="{\"cuentaId\":"+cuenta+",\"pago\":{\"idServicio\":9900,\"idProducto\":990001,\"montoPago\":150,\"telefono\":\"5512345678\",\"referencia\":\"SIM0000001\"},\"escenario\":\"TIMEOUT_APLICADO\"}";
        var created=mvc.perform(post("/api/simulacion/pagos").header("Authorization",auth).header("Idempotency-Key","app-prueba-0001").contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("datos.estado").value("RESERVED")).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).path("datos").path("id").asText();
        mvc.perform(post("/api/simulacion/pagos/"+id+"/procesar").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.estado").value("UNKNOWN"));
        mvc.perform(get("/api/simulacion/cuentas/"+cuenta+"/saldo").header("Authorization",auth))
                .andExpect(jsonPath("datos.reservado").value(150)).andExpect(jsonPath("datos.disponible").value(50));
        mvc.perform(post("/api/simulacion/pagos/"+id+"/conciliar").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("datos.estado").value("CAPTURED"));
        mvc.perform(get("/api/simulacion/cuentas/"+cuenta+"/saldo").header("Authorization",auth))
                .andExpect(jsonPath("datos.reservado").value(0)).andExpect(jsonPath("datos.contabilizado").value(50));
        assertEquals(new java.math.BigDecimal("1000.00"),sql.queryForObject("SELECT saldo FROM cuentas WHERE id=?",java.math.BigDecimal.class,cuenta));
        var spec=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(spec.contains("/api/simulacion/pagos"));assertFalse(spec.contains("consultar-externo"));
    }
}
