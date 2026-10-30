package com.proyecto.servicios.model.gestopago.pago;

import java.math.BigDecimal;

/** Sin eco del teléfono o referencia y sin afirmar que un pago ha sido ejecutado. */
public record ValidacionLocalResponse(boolean contratoLocalValido, Integer idServicio,
        Integer idProducto, BigDecimal montoPago, boolean requiereVerificacionReferencia,
        boolean pagoEnviado) { }
