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
    @Operation(summary = "Obtener o sincronizar catálogo", description = "Jerarquía de caché: Redis -> PostgreSQL -> API GestoPago. Si no está en Redis busca en BD, y si no está en BD sincroniza desde la API.")
    public ResponseEntity<List<GestoPagoProducto>> sincronizarCatalogo() {
        return ResponseEntity.ok(productService.obtenerOSincronizarProductos());
    }

    @PostMapping("/forzar-sincronizacion")
    @Operation(summary = "Forzar sincronización de catálogo", description = "Descarga el catálogo forzadamente desde GestoPago e invalida la caché.")
    public ResponseEntity<GestoPagoProductResponse> forzarSincronizacion() {
        return ResponseEntity.ok(productService.sincronizarCatalogoProductos());
    }

    @GetMapping("/consultar-externo")
    @Operation(summary = "Consultar catálogo externo", description = "Consume directamente el endpoint GET /sistema/service/getProductList.do de GestoPago con Bearer Token desde configuración.")
    public ResponseEntity<GestoPagoProductResponse> consultarCatalogoExterno() {
        return ResponseEntity.ok(productService.consultarCatalogoGestoPago());
    }

    @GetMapping
    @Operation(summary = "Listar productos", description = "Obtiene los productos activos guardados en la base de datos local.")
    public ResponseEntity<List<GestoPagoProducto>> listarProductos() {
        return ResponseEntity.ok(productService.obtenerProductosGuardados());
    }
}
