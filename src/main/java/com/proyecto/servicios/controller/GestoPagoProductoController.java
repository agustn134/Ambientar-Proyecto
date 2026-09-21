package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.service.GestoPagoProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gestopago/productos")
@RequiredArgsConstructor
@Tag(name = "GestoPago Productos", description = "Endpoints para consulta y sincronización del catálogo GestoPago")
public class GestoPagoProductoController {

    private final GestoPagoProductService productService;

    @PostMapping("/sincronizar")
    @Operation(summary = "Sincronizar catálogo", description = "Descarga el catálogo de GestoPago, parsea el XML y guarda/actualiza los productos en base de datos.")
    public ResponseEntity<GestoPagoProductResponse> sincronizarCatalogo() {
        return ResponseEntity.ok(productService.sincronizarCatalogoProductos());
    }

    @GetMapping
    @Operation(summary = "Listar productos", description = "Obtiene los productos activos guardados en la base de datos local.")
    public ResponseEntity<List<GestoPagoProducto>> listarProductos() {
        return ResponseEntity.ok(productService.obtenerProductosGuardados());
    }
}
