package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.List;

@Repository
public class GestoPagoCatalogoRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public GestoPagoCatalogoRepository(@Qualifier("sfDatasource") DataSource dataSource) {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    // JDBC participa en la transacción JPA al compartir sfDatasource.
    // La restricción única arbitra también las descargas concurrentes.
    private static final String UPSERT = """
        INSERT INTO gestopago_productos
            (id_producto, id_servicio, servicio, producto, id_cat_tipo_servicio, tipo_front,
             has_digito_verificador, precio, show_ayuda, tipo_referencia, legend,
             fecha_creacion, fecha_actualizacion, activo)
        VALUES (:idProducto, :idServicio, :servicio, :producto, :idCatTipoServicio, :tipoFront,
                :hasDigitoVerificador, :precio, :showAyuda, :tipoReferencia, :legend,
                clock_timestamp(), clock_timestamp(), TRUE)
        ON CONFLICT (id_producto) DO UPDATE SET
            id_servicio = EXCLUDED.id_servicio,
            servicio = EXCLUDED.servicio,
            producto = EXCLUDED.producto,
            id_cat_tipo_servicio = EXCLUDED.id_cat_tipo_servicio,
            tipo_front = EXCLUDED.tipo_front,
            has_digito_verificador = EXCLUDED.has_digito_verificador,
            precio = EXCLUDED.precio,
            show_ayuda = EXCLUDED.show_ayuda,
            tipo_referencia = EXCLUDED.tipo_referencia,
            legend = EXCLUDED.legend,
            fecha_actualizacion = clock_timestamp(),
            activo = TRUE
        """;

    public void guardarCatalogo(List<GestoPagoProducto> productos) {
        SqlParameterSource[] parametros = productos.stream().map(this::parametros)
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, parametros);
    }

    private SqlParameterSource parametros(GestoPagoProducto producto) {
        return new MapSqlParameterSource()
                .addValue("idProducto", producto.getIdProducto(), Types.INTEGER)
                .addValue("idServicio", producto.getIdServicio(), Types.INTEGER)
                .addValue("servicio", producto.getServicio(), Types.VARCHAR)
                .addValue("producto", producto.getProducto(), Types.VARCHAR)
                .addValue("idCatTipoServicio", producto.getIdCatTipoServicio(), Types.INTEGER)
                .addValue("tipoFront", producto.getTipoFront(), Types.VARCHAR)
                .addValue("hasDigitoVerificador", producto.getHasDigitoVerificador(), Types.BOOLEAN)
                .addValue("precio", producto.getPrecio(), Types.NUMERIC)
                .addValue("showAyuda", producto.getShowAyuda(), Types.BOOLEAN)
                .addValue("tipoReferencia", producto.getTipoReferencia(), Types.VARCHAR)
                .addValue("legend", producto.getLegend(), Types.VARCHAR);
    }
}
