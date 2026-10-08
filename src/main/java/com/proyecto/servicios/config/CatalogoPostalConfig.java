package com.proyecto.servicios.config;

import com.proyecto.servicios.service.ImportadorPostal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.nio.file.Path;

@Configuration
@Slf4j
public class CatalogoPostalConfig {
    @Bean(name="catalogoPostal")
    @DependsOn("flyway")
    public Object cargar(@Qualifier("sfDatasource") DataSource dataSource,
                         @Value("${catalogos.postal.archivo:}") String archivo) throws Exception {
        if (!archivo.isBlank()) {
            var resultado=new ImportadorPostal(dataSource).importar(Path.of(archivo));
            log.info("Catálogo postal listo; asentamientos={}; estados={}; municipios={}",resultado.asentamientos(),resultado.estados(),resultado.municipios());
        } else if (new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM cat_postal_version",Integer.class)==0) {
            throw new IllegalStateException("Configura SEPOMEX_ARCHIVO con el ZIP nacional TXT oficial para cargar el catálogo postal");
        }
        return new Object();
    }
}
