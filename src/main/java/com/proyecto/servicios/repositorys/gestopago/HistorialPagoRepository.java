package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.ConsultasResponse.Pagina;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.ComprobanteHistorial;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.Estado;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Consulta únicamente pagos propios en estado CAPTURED sin exponer credenciales ni payload cifrado. */
@Repository
@Transactional(value = "sfTransactionManager", readOnly = true)
public class HistorialPagoRepository {
    private static final String FILTRO_PROPIETARIO_CAPTURADO = """
            FROM pago_operaciones po
            JOIN cuentas c ON c.id = po.cuenta_id
            JOIN usuarios u ON u.id = ? AND u.cliente_id = c.cliente_id AND u.activo = TRUE
            JOIN clientes cl ON cl.id = c.cliente_id AND cl.estatus = 'ACTIVO'
            LEFT JOIN gestopago_productos gp ON gp.id_producto = po.id_producto
            WHERE po.cliente_id = c.cliente_id AND po.estado = 'CAPTURED'
            """;

    private final JdbcTemplate sql;

    public HistorialPagoRepository(@Qualifier("sfDatasource") DataSource ds) {
        this.sql = new JdbcTemplate(ds);
    }

    public Pagina<ComprobanteHistorial> listarCapturadosPorUsuario(long usuarioId, int page, int size) {
        exigirUsuarioActivo(usuarioId);
        Long totalObj = sql.queryForObject(
                "SELECT COUNT(*) " + FILTRO_PROPIETARIO_CAPTURADO,
                Long.class,
                usuarioId);
        long totalElementos = totalObj != null ? totalObj : 0L;
        int totalPaginas = size > 0 ? (int) ((totalElementos + size - 1) / size) : 0;
        long offset = (long) page * size;

        List<ComprobanteHistorial> contenido = sql.query("""
                SELECT po.id, po.cuenta_id, po.id_servicio, gp.servicio, po.id_producto, gp.producto,
                       po.monto, po.estado, po.upc, po.id_tx, po.creado_en, po.actualizado_en
                """ + FILTRO_PROPIETARIO_CAPTURADO + """
                ORDER BY po.actualizado_en DESC, po.id DESC
                LIMIT ? OFFSET ?
                """, (r, n) -> mapear(r), usuarioId, size, offset);

        return new Pagina<>(contenido, page, size, totalElementos, totalPaginas);
    }

    public ComprobanteHistorial detalleCapturadoPorUsuario(long usuarioId, UUID pagoId) {
        exigirUsuarioActivo(usuarioId);
        return sql.query("""
                SELECT po.id, po.cuenta_id, po.id_servicio, gp.servicio, po.id_producto, gp.producto,
                       po.monto, po.estado, po.upc, po.id_tx, po.creado_en, po.actualizado_en
                """ + FILTRO_PROPIETARIO_CAPTURADO + """
                AND po.id = ?
                """, (r, n) -> mapear(r), usuarioId, pagoId)
                .stream()
                .findFirst()
                .orElseThrow(RecursoNoEncontradoException::new);
    }

    private void exigirUsuarioActivo(long usuarioId) {
        Integer activos = sql.queryForObject("""
                SELECT COUNT(*) FROM usuarios u
                JOIN clientes cl ON cl.id = u.cliente_id
                WHERE u.id = ? AND u.activo = TRUE AND cl.estatus = 'ACTIVO'
                """, Integer.class, usuarioId);
        if (activos == null || activos == 0) {
            throw new RecursoNoEncontradoException();
        }
    }

    private static ComprobanteHistorial mapear(ResultSet r) throws SQLException {
        String rawTx = r.getString("id_tx");
        String folioProveedor = (rawTx != null && !rawTx.isBlank()) ? rawTx.trim() : null;
        return new ComprobanteHistorial(
                r.getObject("id", UUID.class),
                r.getLong("cuenta_id"),
                r.getInt("id_servicio"),
                r.getString("servicio"),
                r.getInt("id_producto"),
                r.getString("producto"),
                r.getBigDecimal("monto"),
                Estado.valueOf(r.getString("estado")),
                r.getString("upc"),
                folioProveedor,
                toLocalDateTime(r.getTimestamp("creado_en")),
                toLocalDateTime(r.getTimestamp("actualizado_en"))
        );
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
