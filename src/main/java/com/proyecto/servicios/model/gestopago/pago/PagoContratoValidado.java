package com.proyecto.servicios.model.gestopago.pago;

import java.math.BigDecimal;

/** Validación local únicamente: NO acredita permisos, saldo ni verifyReference. */
public record PagoContratoValidado(Integer idServicio, Integer idProducto, BigDecimal montoPago,
        String telefono, String referencia, boolean requiereVerificacionReferencia) {
    @Override public String toString() { return "PagoContratoValidado[datos protegidos]"; }
}
