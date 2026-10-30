package com.proyecto.servicios.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidacionesPostgresTest {
    private final String schema="test_validaciones_"+java.util.UUID.randomUUID().toString().replace("-","");
    private JdbcTemplate admin,sql;
    private long cliente,usuario;
    private DriverManagerDataSource fuente;

    @BeforeAll void migrar() {
        String url=System.getenv("POSTGRES_TEST_URL"),user=System.getenv("POSTGRES_TEST_USER"),password=System.getenv("POSTGRES_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        admin.execute("CREATE SCHEMA "+schema);
        fuente=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        sql=new JdbcTemplate(fuente);
        Flyway.configure().dataSource(fuente).schemas(schema).defaultSchema(schema).target("8").load().migrate();
        sql.update("""
            INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual,sexo_id,nacionalidad_id,estado_civil_id)
            VALUES ('Historico','De','Prueba','1990-01-01','HISTORICO','HISTORICO','sin-formato','123','QA','Prueba',1,1,1,7)
            """);
        Flyway.configure().dataSource(fuente).schemas(schema).defaultSchema(schema).load().migrate();
    }
    @AfterAll void limpiar() {
        if(admin!=null && schema.matches("test_validaciones_[a-f0-9]{32}")) admin.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    @BeforeEach void preparar() {
        sql.update("DELETE FROM usuario_cambios_rol");sql.update("DELETE FROM usuarios");
        sql.update("DELETE FROM clientes WHERE curp<>'HISTORICO'");
        cliente=sql.queryForObject("""
            INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual,sexo_id,nacionalidad_id,estado_civil_id)
            VALUES ('Carlos','Parra','Perez','1990-01-01','CAPP900101HGTBCDA0','CAPP900101001','qa@example.com','4681234567','QA','Prueba',100,1,1,7) RETURNING id
            """,Long.class);
        usuario=sql.queryForObject("INSERT INTO usuarios(cliente_id,correo,password_hash) VALUES (?,'qa@example.com',?) RETURNING id",Long.class,cliente,new BCryptPasswordEncoder().encode("PruebaCarga2026!"));
    }
    @Test void migracionConservaHistoricosYEsRepetible() {
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM clientes WHERE curp='HISTORICO'",Integer.class));
        var migrador=Flyway.configure().dataSource(fuente).schemas(schema).defaultSchema(schema).load();
        assertEquals(0,migrador.migrate().migrationsExecuted);
        assertDoesNotThrow(migrador::validate);
        assertEquals(0,migrador.info().pending().length,"Todas las migraciones disponibles deben quedar aplicadas");
    }
    @Test void rechazaDatosInvalidosEnEscrituraDirecta() {
        for(String cambio:new String[]{"correo_electronico='incorrecto'","telefono_movil='123'","ingreso_mensual=0","estatus='DESCONOCIDO'","fecha_nacimiento=CURRENT_DATE"})
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->sql.update("UPDATE clientes SET "+cambio+" WHERE id=?",cliente));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->sql.update("UPDATE usuarios SET password_hash='texto-plano' WHERE id=?",usuario));
    }
    @Test void protegeIdentidadYPermiteMantenimientoAutorizado() throws Exception {
        for(String cambio:new String[]{"id=id+10000","curp='CAPP900101HGTBCDA1'","rfc='CAPP900101002'","fecha_registro=CURRENT_TIMESTAMP+INTERVAL '1 second'"})
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->sql.update("UPDATE clientes SET "+cambio+" WHERE id=?",cliente));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->sql.update("UPDATE usuarios SET fecha_creacion=fecha_creacion+INTERVAL '1 second' WHERE id=?",usuario));
        sql.update("UPDATE clientes SET telefono_movil='4687654321' WHERE id=?",cliente);
        String script=java.nio.file.Files.readString(java.nio.file.Path.of("scripts/asignar-ejecutivo.sql")).replace(":'usuario_id'::bigint",Long.toString(usuario));
        try(var conexion=fuente.getConnection();var statement=conexion.createStatement()) { statement.execute(script); }
        assertEquals("EJECUTIVO",sql.queryForObject("SELECT rol FROM usuarios WHERE id=?",String.class,usuario));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM usuario_cambios_rol",Integer.class));
    }
    @Test void unicidadDeCorreoSeMantieneEnLaBase() {
        long otro=sql.queryForObject("""
            INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual,sexo_id,nacionalidad_id,estado_civil_id)
            VALUES ('Carlos','Parra','Perez','1990-01-02','CAPP900102HGTBCDA0','CAPP900102001','otro@example.com','4681234567','QA','Prueba',100,1,1,7) RETURNING id
            """,Long.class);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->sql.update("INSERT INTO usuarios(cliente_id,correo,password_hash) VALUES (?,'qa@example.com',?)",otro,new BCryptPasswordEncoder().encode("PruebaCarga2026!")));
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->sql.update("UPDATE clientes SET correo_electronico='qa@example.com' WHERE id=?",otro));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->sql.update("UPDATE usuarios SET correo='QA@EXAMPLE.COM' WHERE id=?",usuario));
    }
}
