package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.ProductoDto;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductoRepository;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GestoPagoProductServiceImpl implements GestoPagoProductService {

    private final GestoPagoProductClient gestoPagoProductClient;
    private final GestoPagoTokenService gestoPagoTokenService;
    private final GestoPagoProductoRepository productoRepository;

    @Value("${gestopago.service.id-distribuidor:${gestopago.auth.id-distribuidor}}")
    private Integer idDistribuidor;

    @Value("${gestopago.service.codigo-dispositivo:${gestopago.auth.codigo-dispositivo}}")
    private String codigoDispositivo;

    @Value("${gestopago.service.token:${gestopago.auth.token:}}")
    private String tokenConfigurado;

    /**
     * Tarea programada (Job) para sincronizar productos automáticamente a una hora configurable del día.
     * Por defecto: todos los días a las 03:00 AM (cron: "0 0 3 * * ?").
     */
    @Scheduled(cron = "${gestopago.service.cron:${gestopago.productos.cron:0 0 3 * * ?}}")
    public void ejecutarJobSincronizacionDiaria() {
        log.info("Iniciando Job programado de sincronización de catálogo GestoPago...");
        try {
            sincronizarCatalogoProductos();
            log.info("Job de sincronización finalizado exitosamente.");
        } catch (Exception e) {
            log.error("Error al ejecutar el Job de sincronización de productos GestoPago: {}", e.getMessage(), e);
        }
    }

    @Override
    public GestoPagoProductResponse consultarCatalogoGestoPago() {
        // 1. Obtener Bearer Token desde la configuración de la aplicación (o fallback activo en BD)
        String authHeader = obtenerBearerToken();

        // 2. Invocar al endpoint GET /sistema/service/getProductList.do
        log.info("Consultando endpoint GET /sistema/service/getProductList.do con Bearer Token...");
        return gestoPagoProductClient.getProductList(authHeader);
    }

    @Override
    @Transactional("sfTransactionManager")
    @CacheEvict(value = "productosCache", allEntries = true)
    public GestoPagoProductResponse sincronizarCatalogoProductos() {
        log.info("Iniciando sincronización de catálogo de productos GestoPago para distribuidor={}", idDistribuidor);

        GestoPagoProductResponse response = consultarCatalogoGestoPago();

        if (response == null || response.getProductos() == null || response.getProductos().isEmpty()) {
            log.warn("La respuesta de GestoPago no contiene productos. Mensaje: {}",
                    response != null && response.getMensaje() != null ? response.getMensaje().getTexto() : "Sin respuesta");
            return response;
        }

        log.info("Se recibieron {} productos desde GestoPago. Procesando guardado en base de datos...",
                response.getProductos().size());

        // 4. Guardar / Actualizar productos en base de datos (Upsert)
        List<GestoPagoProducto> entidadesParaGuardar = new ArrayList<>();

        for (ProductoDto dto : response.getProductos()) {
            if (dto.getIdProducto() == null) {
                continue;
            }

            Optional<GestoPagoProducto> existenteOpt = productoRepository.findByIdProducto(dto.getIdProducto());

            GestoPagoProducto producto = existenteOpt.orElseGet(() -> GestoPagoProducto.builder()
                    .idProducto(dto.getIdProducto())
                    .build());

            // Actualizar datos del catálogo
            producto.setIdServicio(dto.getIdServicio());
            producto.setServicio(dto.getServicio());
            producto.setProducto(dto.getProducto());
            producto.setIdCatTipoServicio(dto.getIdCatTipoServicio());
            producto.setTipoFront(dto.getTipoFront());
            producto.setHasDigitoVerificador(dto.getHasDigitoVerificador());
            producto.setPrecio(dto.getPrecio() != null ? BigDecimal.valueOf(dto.getPrecio()) : null);
            producto.setShowAyuda(dto.getShowAyuda());
            producto.setTipoReferencia(dto.getTipoReferencia());
            producto.setLegend(dto.getLegend());
            producto.setActivo(true);

            entidadesParaGuardar.add(producto);
        }

        productoRepository.saveAll(entidadesParaGuardar);
        log.info("Sincronización finalizada. Se persistieron {} productos en la tabla gestopago_productos.",
                entidadesParaGuardar.size());

        return response;
    }

    @Override
    @Cacheable(value = "productosCache")
    public List<GestoPagoProducto> obtenerProductosGuardados() {
        return productoRepository.findByActivoTrue();
    }

    /**
     * Obtiene el Bearer Token para la petición. Prioriza el token configurado en properties (no hardcodeado).
     * Si no está presente en la configuración, recurre al token activo o renovado en la base de datos.
     */
    private String obtenerBearerToken() {
        if (tokenConfigurado != null && !tokenConfigurado.trim().isEmpty()) {
            String tokenLimpio = tokenConfigurado.trim();
            log.info("Utilizando Bearer Token obtenido desde la configuración de la aplicación");
            return tokenLimpio.startsWith("Bearer ") ? tokenLimpio : "Bearer " + tokenLimpio;
        }

        log.info("Token no configurado explícitamente en properties; obteniendo token activo desde BD...");
        GestoPagoToken tokenEntity = obtenerOAsegurarToken();
        return "Bearer " + tokenEntity.getToken();
    }

    /**
     * Asegura la obtención de un token válido. Si es la primera vez o la BD está vacía,
     * ejecuta renovarToken() antes de consultar.
     */
    private GestoPagoToken obtenerOAsegurarToken() {
        return gestoPagoTokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                .orElseGet(() -> {
                    log.info("No se encontró token activo en BD. Solicitando uno nuevo por primera vez...");
                    gestoPagoTokenService.renovarToken();
                    return gestoPagoTokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                            .orElseThrow(() -> new IllegalStateException(
                                     "No se pudo generar ni obtener un token activo de GestoPago para distribuidor " + idDistribuidor));
                });
    }
}
