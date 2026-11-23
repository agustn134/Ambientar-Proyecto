package com.proyecto.servicios.model.gestopago.pago;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class PagoDtos {
    private PagoDtos() {}
    public enum Estado { RESERVED, PROCESSING, UNKNOWN, CAPTURED, FAILED }
    public record Solicitud(@NotNull @Positive Long cuentaId, @NotNull @Valid CrearPagoRequest pago) {
        @Override public String toString() { return "Solicitud[pago protegido]"; }
    }
    public record Pago(UUID id, long cuentaId, BigDecimal monto, Estado estado, boolean revisionManual) {}
    public record Saldo(long cuentaId, BigDecimal contabilizado, BigDecimal reservado, BigDecimal disponible) {}
    public record ComprobanteHistorial(
            UUID id,
            long cuentaId,
            int idServicio,
            String servicio,
            int idProducto,
            String producto,
            BigDecimal monto,
            Estado estado,
            String referenciaInternaUpc,
            String folioProveedorIdTx,
            LocalDateTime creadoEn,
            LocalDateTime capturadoEn) {}
}
