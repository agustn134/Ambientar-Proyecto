package com.proyecto.servicios.service;

import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import java.nio.file.Path;

/** Entrada de instalación: carga el catálogo sin arrancar la API ni contactar proveedores. */
public final class ImportadorPostalCli {
    private ImportadorPostalCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Uso: ImportadorPostalCli <jdbc-url> <usuario> <zip-postal>");
        }
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) password = System.getenv("PGPASSWORD");
        var dataSource = new SimpleDriverDataSource(new org.postgresql.Driver(), args[0], args[1],
                password == null ? "" : password);
        var resultado = new ImportadorPostal(dataSource).importar(Path.of(args[2]));
        System.out.printf("Catálogo postal listo: %d asentamientos, %d estados, %d municipios. SHA-256: %s%n",
                resultado.asentamientos(), resultado.estados(), resultado.municipios(), resultado.sha256());
    }
}
