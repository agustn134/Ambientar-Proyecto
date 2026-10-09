package com.proyecto.servicios.service;

import com.proyecto.servicios.repositorys.cliente.CatalogoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="POSTGRES_TEST_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogosPostgresTest {
    private final String schema="test_catalogos_"+UUID.randomUUID().toString().replace("-","");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate admin;
    private JdbcTemplate sql;

    @BeforeAll void preparar() throws Exception {
        String url=System.getenv("POSTGRES_TEST_URL");
        String usuario=System.getenv("POSTGRES_TEST_USER");
        String password=System.getenv("POSTGRES_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,usuario,password));
        admin.execute("CREATE SCHEMA "+schema);
        dataSource=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,usuario,password);
        sql=new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql")).execute(dataSource);
        sql.update("""
            INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,sexo,nacionalidad,estado_civil,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual)
            VALUES ('Cliente','De','Prueba','1990-01-01','CURP_SINTETICA_001','RFC_SINTETICO','MASCULINO','Mexicana','NO_ESPECIFICADO','migracion@example.com','4680000000','QA','Prueba',100)
            """);
        sql.update("INSERT INTO domicilios(cliente_id,calle,numero_exterior,colonia,municipio,estado,codigo_postal,pais) VALUES (1,'Calle de prueba','1','Nueva San Isidro','San Luis de la Paz','Guanajuato','37907','México')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__catalogos_mexico.sql")).execute(dataSource);
    }

    @AfterAll void limpiar() {
        if (admin!=null && schema.matches("test_catalogos_[a-f0-9]{32}")) admin.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
    }

    @Test void migracionConservaClienteYDomicilioConLlavesForaneas() {
        assertEquals("Cliente",sql.queryForObject("SELECT nombre FROM clientes WHERE id=1",String.class));
        assertEquals(1,sql.queryForObject("SELECT sexo_id FROM clientes WHERE id=1",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT nacionalidad_id FROM clientes WHERE id=1",Integer.class));
        assertEquals(7,sql.queryForObject("SELECT estado_civil_id FROM clientes WHERE id=1",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT pais_id FROM domicilios WHERE cliente_id=1",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> sql.update("UPDATE clientes SET sexo_id=99 WHERE id=1"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named="POSTAL_TEST_ARCHIVO",matches=".+")
    void importaCatalogoNacionalYRepetirNoDuplica() throws Exception {
        var importador=new ImportadorPostal(dataSource);
        var archivo=Path.of(System.getenv("POSTAL_TEST_ARCHIVO"));
        var primero=importador.importar(archivo);
        assertEquals(32,primero.estados());
        assertTrue(primero.asentamientos()>100000);
        var opciones=new CatalogoRepository(dataSource).porCodigoPostal("37907");
        assertTrue(opciones.size()>1);
        assertTrue(opciones.stream().anyMatch(a -> a.id()==110333891 && a.colonia().equals("San Isidro")));
        assertFalse(new CatalogoRepository(dataSource).porCodigoPostal("01000").isEmpty());
        assertEquals(110333891,sql.queryForObject("SELECT asentamiento_id FROM domicilios WHERE cliente_id=1",Integer.class));
        assertEquals(primero,importador.importar(archivo));
        assertEquals(primero.asentamientos(),sql.queryForObject("SELECT COUNT(*) FROM cat_asentamientos",Integer.class));
    }

    @Test void archivoInvalidoNoModificaVersionNiCatalogo() throws Exception {
        int antes=sql.queryForObject("SELECT COUNT(*) FROM cat_asentamientos",Integer.class);
        var version=sql.queryForList("SELECT * FROM cat_postal_version");
        Path temporal=Files.createTempFile("sepomex-invalido-",".zip");
        try {
            Files.writeString(temporal,"Archivo sintético inválido");
            assertThrows(IllegalArgumentException.class,() -> new ImportadorPostal(dataSource).importar(temporal));
            assertEquals(antes,sql.queryForObject("SELECT COUNT(*) FROM cat_asentamientos",Integer.class));
            assertEquals(version,sql.queryForList("SELECT * FROM cat_postal_version"));
        } finally { Files.deleteIfExists(temporal); }
    }

    @Test void valorHistoricoDesconocidoRevierteLaMigracionSinBorrarDatos() throws Exception {
        String aislado="test_catalogos_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+aislado);
        String url=System.getenv("POSTGRES_TEST_URL");
        var fuente=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+aislado,
                System.getenv("POSTGRES_TEST_USER"),System.getenv("POSTGRES_TEST_PASSWORD"));
        try {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__create_clientes_domicilios_cuentas.sql")).execute(fuente);
            var local=new JdbcTemplate(fuente);
            local.update("""
                INSERT INTO clientes(nombre,apellido_paterno,apellido_materno,fecha_nacimiento,curp,rfc,sexo,nacionalidad,estado_civil,correo_electronico,telefono_movil,ocupacion,empresa,ingreso_mensual)
                VALUES ('Cliente','De','Prueba','1990-01-01','CURP_SINTETICA_002','RFC_SINT_002','SIN_MAPEO','Mexicana','NO_ESPECIFICADO','sinmapa@example.com','4680000000','QA','Prueba',100)
                """);
            try (var conexion=fuente.getConnection()) {
                conexion.setAutoCommit(false);
                assertThrows(RuntimeException.class,() -> new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__catalogos_mexico.sql")).populate(conexion));
                conexion.rollback();
            }
            assertEquals("SIN_MAPEO",local.queryForObject("SELECT sexo FROM clientes WHERE id=1",String.class));
            assertEquals(1,local.queryForObject("SELECT COUNT(*) FROM clientes",Integer.class));
            assertEquals(0,local.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='clientes' AND column_name='sexo_id'",Integer.class,aislado));
        } finally {
            if (aislado.matches("test_catalogos_[a-f0-9]{32}")) admin.execute("DROP SCHEMA "+aislado+" CASCADE");
        }
    }
}
