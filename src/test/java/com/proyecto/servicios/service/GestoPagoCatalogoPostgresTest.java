package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoCatalogoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL real, esquema aleatorio aislado; nunca modifica public ni llama al proveedor. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GestoPagoCatalogoPostgresTest {
    private final String schema = "test_catalogo_" + UUID.randomUUID().toString().replace("-", "");
    private JdbcTemplate admin;
    private JdbcTemplate sql;
    private AnnotationConfigApplicationContext context;
    private GestoPagoCatalogoService service;
    private ConcurrentMapCacheManager cache;

    @Configuration
    @EnableTransactionManagement
    @Import({GestoPagoCatalogoRepository.class, GestoPagoCatalogoService.class})
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.proyecto.servicios.entity.gestopago");
            factory.setPersistenceUnitName("sfDatasource");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }
        @Bean(name = "sfTransactionManager") JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean ConcurrentMapCacheManager cacheManager() { return new ConcurrentMapCacheManager("productosCache"); }
    }

    @BeforeAll void iniciar() {
        String url = System.getenv("POSTGRES_TEST_URL");
        String usuario = System.getenv("POSTGRES_TEST_USER");
        String password = System.getenv("POSTGRES_TEST_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, usuario, password));
        admin.execute("CREATE SCHEMA " + schema);
        var dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, usuario, password);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__create_gestopago_productos.sql")).execute(dataSource);
        context = new AnnotationConfigApplicationContext();
        context.registerBean("sfDatasource", DataSource.class, () -> dataSource);
        context.register(Config.class);
        context.refresh();
        service = context.getBean(GestoPagoCatalogoService.class);
        cache = context.getBean(ConcurrentMapCacheManager.class);
        sql = new JdbcTemplate(dataSource);
    }

    @AfterAll void terminar() {
        if (context != null) context.close();
        if (admin != null && schema.matches("test_catalogo_[a-f0-9]{32}")) admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @BeforeEach void limpiar() {
        sql.execute("TRUNCATE gestopago_productos RESTART IDENTITY");
        cache.getCache("productosCache").clear();
    }

    @Test void repetirActualizaConservandoIdentidadYFechaDeCreacion() {
        service.guardarCatalogo(List.of(producto(101, "Original", "10.00")));
        var original = sql.queryForMap("SELECT id, fecha_creacion, fecha_actualizacion FROM gestopago_productos");
        sql.update("UPDATE gestopago_productos SET activo=FALSE");
        cache.getCache("productosCache").put("catalogo", "viejo");
        var actualizado = producto(101, "Actualizado", "25.50");
        actualizado.setLegend("Ayuda actualizada");
        actualizado.setIdServicio(8);
        service.guardarCatalogo(List.of(actualizado, producto(102, "Nuevo", "12.00")));
        service.guardarCatalogo(List.of(actualizado, producto(102, "Nuevo", "12.00")));
        assertEquals(2, sql.queryForObject("SELECT COUNT(*) FROM gestopago_productos", Integer.class));
        var actual = sql.queryForMap("SELECT * FROM gestopago_productos WHERE id_producto=101");
        assertEquals(original.get("id"), actual.get("id"));
        assertEquals(original.get("fecha_creacion"), actual.get("fecha_creacion"));
        assertNotEquals(original.get("fecha_actualizacion"), actual.get("fecha_actualizacion"));
        assertEquals("Actualizado", actual.get("producto"));
        assertEquals(new BigDecimal("25.50"), actual.get("precio"));
        assertEquals(8, actual.get("id_servicio"));
        assertEquals("Ayuda actualizada", actual.get("legend"));
        assertEquals(true, actual.get("activo"));
        assertNull(cache.getCache("productosCache").get("catalogo"));
    }

    @Test void falloEnElLoteRevierteCambiosYConservaCache() {
        service.guardarCatalogo(List.of(producto(101, "Original", "10.00")));
        cache.getCache("productosCache").put("catalogo", "vigente");
        assertThrows(RuntimeException.class, () -> service.guardarCatalogo(List.of(
                producto(101, "Cambio que se revierte", "20.00"), producto(102, "X".repeat(256), "15.00"))));
        assertEquals("Original", sql.queryForObject("SELECT producto FROM gestopago_productos", String.class));
        assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM gestopago_productos", Integer.class));
        assertEquals("vigente", cache.getCache("productosCache").get("catalogo", String.class));
    }

    @Test void descargasConcurrentesNoDuplicanProductos() throws Exception {
        var inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> tarea = () -> {
                inicio.await();
                service.guardarCatalogo(List.of(producto(102, "Segundo", "12.00"), producto(101, "Primero", "10.00")));
                return null;
            };
            var uno = executor.submit(tarea);
            var dos = executor.submit(tarea);
            inicio.countDown();
            uno.get(20, TimeUnit.SECONDS);
            dos.get(20, TimeUnit.SECONDS);
            assertEquals(2, sql.queryForObject("SELECT COUNT(*) FROM gestopago_productos", Integer.class));
            assertEquals(2, sql.queryForObject("SELECT COUNT(DISTINCT id_producto) FROM gestopago_productos", Integer.class));
        } finally { executor.shutdownNow(); }
    }

    static GestoPagoProducto producto(int id, String nombre, String precio) {
        return GestoPagoProducto.builder().idProducto(id).producto(nombre).precio(new BigDecimal(precio)).build();
    }
}
