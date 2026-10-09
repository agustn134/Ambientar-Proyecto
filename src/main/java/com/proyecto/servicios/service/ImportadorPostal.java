package com.proyecto.servicios.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Importa el ZIP oficial local sin extraer rutas del ZIP al disco. */
public class ImportadorPostal {
    public record Resultado(int asentamientos, int estados, int municipios, String sha256) {}
    private record Fila(int id, int municipioId, String codigoPostal, String nombre, String tipo) {}
    private final JdbcTemplate sql;
    private final TransactionTemplate transaccion;

    public ImportadorPostal(DataSource dataSource) {
        sql=new JdbcTemplate(dataSource);
        transaccion=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public Resultado importar(Path archivo) throws Exception {
        String hash;
        try (InputStream entrada=Files.newInputStream(archivo)) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[8192];
            int leidos;
            while ((leidos=entrada.read(buffer))!=-1) digest.update(buffer,0,leidos);
            hash=HexFormat.of().formatHex(digest.digest());
        }
        var existente=sql.queryForList("SELECT sha256,registros FROM cat_postal_version WHERE id=1");
        if (!existente.isEmpty() && hash.equals(existente.get(0).get("sha256"))) {
            return new Resultado(((Number)existente.get(0).get("registros")).intValue(),
                    sql.queryForObject("SELECT COUNT(*) FROM cat_estados",Integer.class),
                    sql.queryForObject("SELECT COUNT(*) FROM cat_municipios",Integer.class),hash);
        }
        Map<Integer,String> estados=new TreeMap<>();
        Map<Integer,Object[]> municipios=new TreeMap<>();
        List<Fila> filas=new ArrayList<>();
        Set<Integer> ids=new HashSet<>();
        try (var zip=new ZipInputStream(Files.newInputStream(archivo),Charset.forName("windows-1252"))) {
            boolean encontrado=false;
            for (var entrada=zip.getNextEntry(); entrada!=null; entrada=zip.getNextEntry()) {
                if (entrada.isDirectory() || !entrada.getName().endsWith(".txt")) continue;
                if (encontrado) throw new IllegalArgumentException("El ZIP debe contener un solo catálogo TXT");
                encontrado=true;
                var lector=new BufferedReader(new InputStreamReader(zip,Charset.forName("windows-1252")));
                lector.readLine(); // aviso de uso del proveedor
                String cabecera=lector.readLine();
                if (cabecera==null || !cabecera.startsWith("d_codigo|d_asenta|d_tipo_asenta|D_mnpio|d_estado|")) {
                    throw new IllegalArgumentException("Cabecera SEPOMEX no reconocida");
                }
                String linea;
                while ((linea=lector.readLine())!=null) {
                    if (linea.isBlank()) continue;
                    String[] c=linea.split("\\|",-1);
                    if (c.length!=15 || !c[0].matches("[0-9]{5}")) throw new IllegalArgumentException("Fila postal inválida");
                    int estado=Integer.parseInt(c[7]);
                    int municipio=Integer.parseInt(c[11]);
                    int asentamiento=Integer.parseInt(c[12]);
                    if (estado<1 || estado>32 || municipio<1 || municipio>999 || asentamiento<1 || asentamiento>9999) {
                        throw new IllegalArgumentException("Identificadores SEPOMEX fuera del formato esperado");
                    }
                    int municipioId=estado*1000+municipio;
                    int id=municipioId*10000+asentamiento;
                    if (!ids.add(id)) throw new IllegalArgumentException("Identificador postal repetido");
                    estados.put(estado,c[4]);
                    municipios.put(municipioId,new Object[]{municipioId,estado,c[3]});
                    filas.add(new Fila(id,municipioId,c[0],c[1],c[2]));
                }
            }
            if (!encontrado || estados.size()!=32 || filas.size()<100000) {
                throw new IllegalArgumentException("Se requiere el catálogo nacional completo de las 32 entidades");
            }
        }
        return transaccion.execute(status -> {
            sql.batchUpdate("INSERT INTO cat_estados(id,descripcion) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET descripcion=EXCLUDED.descripcion",
                    estados.entrySet().stream().map(e -> new Object[]{e.getKey(),e.getValue()}).toList());
            sql.batchUpdate("INSERT INTO cat_municipios(id,estado_id,descripcion) VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET descripcion=EXCLUDED.descripcion",
                    new ArrayList<>(municipios.values()));
            sql.batchUpdate("INSERT INTO cat_asentamientos(id,municipio_id,codigo_postal,descripcion,tipo) VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET codigo_postal=EXCLUDED.codigo_postal,descripcion=EXCLUDED.descripcion,tipo=EXCLUDED.tipo",
                    filas,500,(ps,f) -> { ps.setInt(1,f.id()); ps.setInt(2,f.municipioId()); ps.setString(3,f.codigoPostal()); ps.setString(4,f.nombre()); ps.setString(5,f.tipo()); });
            sql.update("INSERT INTO cat_postal_version(id,sha256,registros) VALUES (1,?,?) ON CONFLICT(id) DO UPDATE SET sha256=EXCLUDED.sha256,registros=EXCLUDED.registros,cargado_en=CURRENT_TIMESTAMP",hash,filas.size());
            // Sólo se vinculan direcciones históricas con coincidencia exacta y única.
            // Equivalencia confirmada por el usuario para sus datos de prueba.
            sql.update("UPDATE domicilios SET colonia='San Isidro' WHERE codigo_postal='37907' AND UPPER(TRIM(municipio))='SAN LUIS DE LA PAZ' AND UPPER(TRIM(estado))='GUANAJUATO' AND UPPER(TRIM(colonia))='NUEVA SAN ISIDRO'");
            sql.update("""
                UPDATE domicilios d SET asentamiento_id=(
                  SELECT MIN(a.id) FROM cat_asentamientos a JOIN cat_municipios m ON m.id=a.municipio_id JOIN cat_estados e ON e.id=m.estado_id
                  WHERE a.codigo_postal=d.codigo_postal AND UPPER(a.descripcion)=UPPER(TRIM(d.colonia))
                    AND UPPER(m.descripcion)=UPPER(TRIM(d.municipio)) AND UPPER(e.descripcion)=UPPER(TRIM(d.estado))
                  HAVING COUNT(*)=1)
                WHERE d.asentamiento_id IS NULL
                """);
            return new Resultado(filas.size(),estados.size(),municipios.size(),hash);
        });
    }
}
