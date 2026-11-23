package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.pago.PagoDtos.*;
import com.proyecto.servicios.service.PagoReconciliator;
import com.proyecto.servicios.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@Profile("(live | prod) & !simulacion")
@RequestMapping("/api/gestopago")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Pagos GestoPago",description="Perfil financiero real. Reserva saldo de cuentas y procesa una sola vez mediante outbox. UNKNOWN no se reenvía.")
public class PagoController {
    private final PagoService pagos;
    private final PagoReconciliator reconciliator;
    public PagoController(PagoService pagos,PagoReconciliator reconciliator){this.pagos=pagos;this.reconciliator=reconciliator;}
    @PostMapping("/pagos")
    @Operation(summary="Reservar saldo y aceptar operación financiera",description="No confirma que el proveedor haya aplicado el pago. Consulta el estado con el ID devuelto. Repite la misma clave y datos para recuperar la misma operación.")
    @ApiResponse(responseCode="202",description="Operación persistida y fondos reservados; procesamiento pendiente")
    public ResponseEntity<Pago> crear(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Solicitud solicitud) {
        var pago=pagos.crear(Long.parseLong(jwt.getSubject()),key,solicitud);
        return ResponseEntity.accepted().location(URI.create("/api/gestopago/pagos/"+pago.id())).body(pago);
    }
    @GetMapping("/pagos/{id}")
    @Operation(summary="Consultar estado financiero de tu operación")
    public Pago consultar(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return pagos.consultar(Long.parseLong(jwt.getSubject()),id);}
    @GetMapping("/cuentas/{cuentaId}/saldo")
    @Operation(summary="Consultar saldo real, reserva y disponible de tu cuenta")
    public Saldo saldo(@AuthenticationPrincipal Jwt jwt,@PathVariable long cuentaId){return pagos.saldo(Long.parseLong(jwt.getSubject()),cuentaId);}
    @PostMapping("/pagos/{id}/conciliar")
    @Operation(summary="Consultar confirmación de una operación UNKNOWN",description="Solo realiza confirmTx desde 62 segundos hasta antes de 24h, con los parámetros originales. No reenvía sendTx. Fuera de la ventana mantiene reserva y requiere revisión manual.")
    public Pago conciliar(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
        return reconciliator.conciliar(Long.parseLong(jwt.getSubject()),id);
    }
}
