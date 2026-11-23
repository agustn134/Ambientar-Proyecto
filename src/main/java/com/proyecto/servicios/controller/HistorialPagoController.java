package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.RegistroClienteException;
import com.proyecto.servicios.model.cliente.ConsultasResponse.Pagina;
import com.proyecto.servicios.model.gestopago.pago.PagoDtos.ComprobanteHistorial;
import com.proyecto.servicios.repositorys.gestopago.HistorialPagoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/gestopago/pagos/historial")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Historial de pagos", description = "Historial paginado de operaciones propias capturadas (CAPTURED) y detalle trazable.")
public class HistorialPagoController {
    private static final int MAX_PAGE_SIZE = 50;
    private final HistorialPagoRepository historial;

    public HistorialPagoController(HistorialPagoRepository historial) {
        this.historial = historial;
    }

    @GetMapping
    @Operation(summary = "Listar compras exitosas paginadas del usuario autenticado",
            description = "Filtra en servidor exclusivamente operaciones propias en estado CAPTURED ordenadas por fecha de captura descendente.")
    public Pagina<ComprobanteHistorial> listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 0) {
            throw new RegistroClienteException("page", "El número de página debe ser mayor o igual a 0", 400);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new RegistroClienteException("size", "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE, 400);
        }
        return historial.listarCapturadosPorUsuario(Long.parseLong(jwt.getSubject()), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar detalle trazable de una compra exitosa propia",
            description = "Devuelve 404 si la operación no existe, pertenece a otro propietario o no está en estado CAPTURED.")
    public ComprobanteHistorial detalle(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return historial.detalleCapturadoPorUsuario(Long.parseLong(jwt.getSubject()), id);
    }
}
