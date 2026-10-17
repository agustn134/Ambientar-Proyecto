package com.proyecto.servicios.model.gestopago;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ProductoResponse(
    Integer id,
    Integer idProducto,
    Integer idServicio,
    String servicio,
    String producto,
    Integer idCatTipoServicio,
    String tipoFront,
    Boolean hasDigitoVerificador,
    BigDecimal precio,
    Boolean showAyuda,
    String tipoReferencia,
    String legend,
    LocalDateTime fechaCreacion,
    LocalDateTime fechaActualizacion,
    Boolean activo) {
    public static ProductoResponse desde(com.proyecto.servicios.entity.gestopago.GestoPagoProducto p) { return new ProductoResponse(p.getId(),p.getIdProducto(),p.getIdServicio(),p.getServicio(),p.getProducto(),p.getIdCatTipoServicio(),p.getTipoFront(),p.getHasDigitoVerificador(),p.getPrecio(),p.getShowAyuda(),p.getTipoReferencia(),p.getLegend(),p.getFechaCreacion(),p.getFechaActualizacion(),p.getActivo()); }
}
