package com.proyecto.servicios.integration.gestopago;

/** Validación externa autorizada por producto y contrastada con el catálogo; no ejecuta pagos. */
public interface GestoPagoReferenceVerifier {
    Resultado verificar(Integer idServicio, Integer idProducto, String referencia);
    Detalle verificarDetalle(Integer idServicio, Integer idProducto, String referencia);
    record Detalle(Resultado resultado, java.math.BigDecimal saldoReferencia) {
        @Override public String toString() { return "Detalle[resultado="+resultado+"]"; }
    }
    enum Resultado { VALIDA, INVALIDA, INDETERMINADA }
}
