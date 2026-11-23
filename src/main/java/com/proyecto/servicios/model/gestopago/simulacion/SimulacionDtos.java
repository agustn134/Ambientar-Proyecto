package com.proyecto.servicios.model.gestopago.simulacion;

import com.proyecto.servicios.model.gestopago.pago.CrearPagoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class SimulacionDtos {
    private SimulacionDtos() {}
    public enum Escenario { EXITO, RECHAZO, TIMEOUT_APLICADO, TIMEOUT_NO_APLICADO }
    public enum Estado { RESERVED, PROCESSING, UNKNOWN, CAPTURED, FAILED }
    public record Fondeo(@NotNull @DecimalMin("0.01") @DecimalMax("10000.00")
                         @Digits(integer=5, fraction=2) BigDecimal monto) {}
    public record Solicitud(@NotNull @Positive Long cuentaId, @NotNull @Valid CrearPagoRequest pago,
                             @NotNull Escenario escenario) {
        @Override public String toString() { return "Solicitud[simulacion]"; }
    }
    public record Saldo(long cuentaId, BigDecimal contabilizado, BigDecimal reservado,
                         BigDecimal disponible, boolean simulado) {}
    public record Pago(UUID id, long cuentaId, BigDecimal monto, Estado estado,
                        @io.swagger.v3.oas.annotations.media.Schema(example="true") boolean simulado,
                        @io.swagger.v3.oas.annotations.media.Schema(example="false") boolean enviadoAProveedorReal) {}
    public record Producto(int idServicio, int idProducto, int tipoFront, String nombre,
                            BigDecimal minimo, BigDecimal maximo, boolean requiereReferencia,
                            String referenciaDePrueba) {}
}
