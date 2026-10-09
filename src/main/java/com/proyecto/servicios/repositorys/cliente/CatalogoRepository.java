package com.proyecto.servicios.repositorys.cliente;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

@Repository
public class CatalogoRepository {
    public record Opcion(int id, String descripcion) {}
    public record Asentamiento(int id, String codigoPostal, String colonia, String tipo,
                              int municipioId, String municipio, int estadoId, String estado) {}
    public enum Tipo {
        SEXO("cat_sexos"), NACIONALIDAD("cat_nacionalidades"), PAIS("cat_paises"), ESTADO_CIVIL("cat_estados_civiles");
        private final String tabla;
        Tipo(String tabla) { this.tabla=tabla; }
    }
    private final JdbcTemplate sql;
    public CatalogoRepository(@Qualifier("sfDatasource") DataSource dataSource) { sql=new JdbcTemplate(dataSource); }

    public List<Opcion> opciones(Tipo tipo) {
        return sql.query("SELECT id,descripcion FROM " + tipo.tabla + " WHERE activo=TRUE ORDER BY id",
                (rs,n) -> new Opcion(rs.getInt(1),rs.getString(2)));
    }
    public boolean habilitado(Tipo tipo, Integer id) {
        return id != null && Boolean.TRUE.equals(sql.queryForObject("SELECT COUNT(*) > 0 FROM " + tipo.tabla + " WHERE id=? AND activo=TRUE",Boolean.class,id));
    }
    private static final String ASENTAMIENTO = """
        SELECT a.id,a.codigo_postal,a.descripcion,a.tipo,m.id AS municipio_id,m.descripcion AS municipio,
               e.id AS estado_id,e.descripcion AS estado
        FROM cat_asentamientos a JOIN cat_municipios m ON m.id=a.municipio_id JOIN cat_estados e ON e.id=m.estado_id
        """;
    private final org.springframework.jdbc.core.RowMapper<Asentamiento> mapper=(rs,n) -> new Asentamiento(rs.getInt("id"),rs.getString("codigo_postal"),
            rs.getString("descripcion"),rs.getString("tipo"),rs.getInt("municipio_id"),rs.getString("municipio"),rs.getInt("estado_id"),rs.getString("estado"));
    public List<Asentamiento> porCodigoPostal(String codigoPostal) {
        return sql.query(ASENTAMIENTO + " WHERE a.codigo_postal=? ORDER BY a.descripcion,a.id",mapper,codigoPostal);
    }
    public Optional<Asentamiento> porId(Integer id) {
        if (id==null) return Optional.empty();
        return sql.query(ASENTAMIENTO + " WHERE a.id=?",mapper,id).stream().findFirst();
    }
}
