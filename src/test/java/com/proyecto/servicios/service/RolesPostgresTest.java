package com.proyecto.servicios.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RolesPostgresTest {
    private final String schema="test_roles_"+UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin;
    private JdbcTemplate sql;
    private DriverManagerDataSource dataSource;

    @BeforeAll void preparar() {
        String url=System.getenv("POSTGRES_TEST_URL"),usuario=System.getenv("POSTGRES_TEST_USER"),password=System.getenv("POSTGRES_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,usuario,password));
        admin.execute("CREATE SCHEMA "+schema);
        dataSource=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,usuario,password);
        sql=new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql"),
                new ClassPathResource("db/migration/V5__create_usuarios.sql"),new ClassPathResource("db/migration/V6__version_token_usuarios.sql"))
                .execute(dataSource);
        sql.update("""
            INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,sexo,nacionalidad,estado_civil,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual)
            VALUES ('Cliente','De','Prueba','1990-01-01','CURP_ROLE_TEST','RFC_ROLE_TEST','MASCULINO','Mexicana','SOLTERO','rol@example.com','4680000000','QA','Prueba',100)
            """);
        sql.update("INSERT INTO usuarios(cliente_id,correo,password_hash) VALUES (1,'rol@example.com',?)","hash-sintetico-de-prueba".repeat(3).substring(0,60));
        sql.update("INSERT INTO cuentas(cliente_id,numero_cuenta,saldo,estatus) VALUES (1,'11111111111111111111',250,'ACTIVA')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V8__roles_y_fecha_registro.sql")).execute(dataSource);
        assertEquals("CLIENTE",sql.queryForObject("SELECT rol FROM usuarios WHERE id=1",String.class));
    }
    @AfterAll void limpiar() {
        if (admin!=null && schema.matches("test_roles_[a-f0-9]{32}")) admin.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
    }

    @BeforeEach void restaurarUsuario() {
        sql.update("DELETE FROM usuario_cambios_rol");
        sql.update("UPDATE usuarios SET rol='CLIENTE',activo=TRUE,version_token=0 WHERE id=1");
    }

    @Test void usuariosHistoricosSonClientesYNoSeInventaFecha() {
        assertEquals("CLIENTE",sql.queryForObject("SELECT column_default FROM information_schema.columns WHERE table_schema=? AND table_name='usuarios' AND column_name='rol'",String.class,schema).replace("'","").replace("::character varying",""));
        assertNull(sql.queryForObject("SELECT fecha_registro FROM clientes WHERE id=1",java.sql.Timestamp.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> sql.update("UPDATE usuarios SET rol='ADMIN'"));
    }

    @Test void procedimientoElevaUnaVezAuditaYNoModificaCuentaNiPassword() throws Exception {
        String hash=sql.queryForObject("SELECT password_hash FROM usuarios WHERE id=1",String.class);
        String script=Files.readString(Path.of("scripts/asignar-ejecutivo.sql"));
        assertTrue(script.contains(":'usuario_id'::bigint"));
        script=script.replace(":'usuario_id'::bigint","1");
        try (var conexion=dataSource.getConnection();var statement=conexion.createStatement()) { statement.execute(script); }
        assertEquals("EJECUTIVO",sql.queryForObject("SELECT rol FROM usuarios WHERE id=1",String.class));
        assertEquals(1,sql.queryForObject("SELECT version_token FROM usuarios WHERE id=1",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM usuario_cambios_rol",Integer.class));
        try (var conexion=dataSource.getConnection();var statement=conexion.createStatement()) { statement.execute(script); }
        assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM usuario_cambios_rol",Integer.class));
        assertEquals(hash,sql.queryForObject("SELECT password_hash FROM usuarios WHERE id=1",String.class));
        assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM cuentas",Integer.class));
        assertEquals(new java.math.BigDecimal("250.00"),sql.queryForObject("SELECT saldo FROM cuentas",java.math.BigDecimal.class));
        assertEquals("ACTIVA",sql.queryForObject("SELECT estatus FROM cuentas",String.class));
    }

    @Test void usuarioInactivoONoExistenteNoSeEleva() throws Exception {
        sql.update("UPDATE usuarios SET activo=FALSE WHERE id=1");
        String plantilla=Files.readString(Path.of("scripts/asignar-ejecutivo.sql"));
        for (String id: new String[]{"1","999"}) {
            try (var conexion=dataSource.getConnection();var statement=conexion.createStatement()) {
                statement.execute(plantilla.replace(":'usuario_id'::bigint",id));
            }
        }
        assertEquals("CLIENTE",sql.queryForObject("SELECT rol FROM usuarios",String.class));
        assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM usuario_cambios_rol",Integer.class));
    }
}
