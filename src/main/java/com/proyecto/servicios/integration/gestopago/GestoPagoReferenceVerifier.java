package com.proyecto.servicios.integration.gestopago;

/** Puerto sin implementación HTTP hasta disponer del contrato completo de verifyReference. */
public interface GestoPagoReferenceVerifier {
    Resultado verificar(Integer idServicio, Integer idProducto, String referencia);
    enum Resultado { VALIDA, INVALIDA, INDETERMINADA }
}
