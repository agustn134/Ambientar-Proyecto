package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.pago.*;
import com.proyecto.servicios.service.GestoPagoContratoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gestopago/pagos")
@RequiredArgsConstructor
@Tag(name = "GestoPago Validación local")
@SecurityRequirement(name = "bearerAuth")
public class GestoPagoValidacionController {
    private final GestoPagoContratoService contrato;

    @PostMapping(value = "/validar", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Validar contrato local sin ejecutar un pago",
            description = "Consulta exclusivamente el catálogo local y la política configurada. No verifica saldo, "
                    + "cuenta, referencia externa ni duplicados. No cifra, no envía a GestoPago ni reserva fondos.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            examples = @ExampleObject(name = "Oui Movil 40 MXN", value = """
                    {"idServicio":78,"idProducto":219,"montoPago":40.00,"telefono":"5512345678"}
                    """)))
    public ValidacionLocalResponse validar(@Valid @RequestBody CrearPagoRequest request) {
        var resultado = contrato.validar(request);
        return new ValidacionLocalResponse(true, resultado.idServicio(), resultado.idProducto(),
                resultado.montoPago(), resultado.requiereVerificacionReferencia(), false);
    }
}
