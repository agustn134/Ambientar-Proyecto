package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.simulacion.SimulacionDtos.*;
import com.proyecto.servicios.service.simulacion.PagoSimuladoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@Profile("simulacion")
@RequestMapping("/api/simulacion")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Pagos ficticios",description="Solo perfil simulacion. Usa tu sesión y cuenta, con saldo ficticio separado. No llama a GestoPago. Orden: productos, saldo, fondear, crear pago, procesar, consultar y conciliar si queda UNKNOWN.")
public class PagoSimuladoController {
    private final PagoSimuladoService pagos;
    public PagoSimuladoController(PagoSimuladoService pagos){this.pagos=pagos;}
    @GetMapping("/productos")
    @Operation(summary="Ver los dos productos de prueba; no descarga catálogo externo")
    public List<Producto> productos(){return pagos.productos();}
    @GetMapping("/cuentas/{cuentaId}/saldo")
    @Operation(summary="Consultar saldo ficticio de tu cuenta")
    public Saldo saldo(@AuthenticationPrincipal Jwt jwt,@PathVariable long cuentaId){return pagos.saldo(usuario(jwt),cuentaId);}
    @PostMapping("/cuentas/{cuentaId}/fondear")
    @Operation(summary="Agregar dinero ficticio (no modifica cuentas.saldo)")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content=@Content(examples=@ExampleObject(value="{\"monto\":200.00}")))
    public Saldo fondear(@AuthenticationPrincipal Jwt jwt,@PathVariable long cuentaId,@Valid @RequestBody Fondeo request){return pagos.fondear(usuario(jwt),cuentaId,request);}
    @PostMapping("/pagos")
    @Operation(summary="Reservar fondos ficticios y guardar pago (202)",description="Idempotency-Key: usa prueba-0001 y cámbiala para cada pago nuevo. Repetir clave y datos devuelve el mismo pago. Cambiar datos con la misma clave da 409. Para referencia: 9900/990001, 150 MXN, SIM0000001 válida; SIM0000000 inválida (422); SIM0000002 indeterminada (503, sin reserva). El producto tipoFront=4 y sus reglas son ficticios, no confirman el contrato externo.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content=@Content(examples={
        @ExampleObject(name="Con referencia",value="{\"cuentaId\":1,\"pago\":{\"idServicio\":9900,\"idProducto\":990001,\"montoPago\":150.00,\"telefono\":\"5512345678\",\"referencia\":\"SIM0000001\"},\"escenario\":\"EXITO\"}"),
        @ExampleObject(name="Recarga con timeout",value="{\"cuentaId\":1,\"pago\":{\"idServicio\":78,\"idProducto\":219,\"montoPago\":40.00,\"telefono\":\"5512345678\"},\"escenario\":\"TIMEOUT_APLICADO\"}")
    }))
    public ResponseEntity<Pago> crear(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Solicitud request){
        var pago=pagos.crear(usuario(jwt),key,request);
        return ResponseEntity.accepted().location(java.net.URI.create("/api/simulacion/pagos/"+pago.id())).body(pago);
    }
    @GetMapping("/pagos/{id}")
    @Operation(summary="Consultar estado del pago ficticio")
    public Pago consultar(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return pagos.consultar(usuario(jwt),id);}
    @PostMapping("/pagos/{id}/procesar")
    @Operation(summary="Ejecutar una vez contra el proveedor ficticio",description="EXITO captura; RECHAZO libera; TIMEOUT_APLICADO y TIMEOUT_NO_APLICADO dejan UNKNOWN y mantienen la reserva. No reenvía UNKNOWN ni pagos terminados.")
    public Pago procesar(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return pagos.procesar(usuario(jwt),id);}
    @PostMapping("/pagos/{id}/conciliar")
    @Operation(summary="Resolver UNKNOWN con el libro del proveedor ficticio",description="Captura si fue aplicado o libera si consta como no aplicado. No reenvía. No usa confirmTx ni certifica el contrato real del proveedor.")
    public Pago conciliar(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return pagos.conciliar(usuario(jwt),id);}
    private long usuario(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
