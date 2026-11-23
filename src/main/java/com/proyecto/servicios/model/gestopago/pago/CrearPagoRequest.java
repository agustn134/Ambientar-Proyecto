package com.proyecto.servicios.model.gestopago.pago;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Intención de pago; la identidad, UPC, unidad y fecha se resuelven en el servidor. */
public record CrearPagoRequest(
        @NotNull @Positive Integer idServicio,
        @NotNull @Positive Integer idProducto,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal montoPago,
        @Size(max = 20) String telefono,
        @Size(max = 100) String referencia) {
    @Override public String toString() { return "CrearPagoRequest[datos protegidos]"; }
}
