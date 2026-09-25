package com.proyecto.servicios.scheduler;

import com.proyecto.servicios.service.GestoPagoProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Componente que gestiona las tareas programadas (Timed Tasks / Cron Tasks) 
 * para la sincronización de los productos.
 * 
 * @author Agustin Lopez Parra
 * @version 1.0
 * @see com.proyecto.servicios.service.GestoPagoProductService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GestoPagoProductScheduler {

    private final GestoPagoProductService productService;

    /**
     * Tarea programada (Cron Task) que se ejecuta durante la noche (a las 12 AM / medianoche)
     * para actualizar la lista de productos (getlistproduct). El flujo actualizará primero 
     * en PostgreSQL a través de la API y luego la caché de Redis será purgada o actualizada.
     * 
     * @see com.proyecto.servicios.service.GestoPagoProductService#sincronizarCatalogoProductos()
     * @deprecated Este enfoque de cron estático podría ser modificado en un futuro por un esquema de Webhooks.
     */
    @Scheduled(cron = "0 0 0 * * ?") // 12 de la noche (medianoche)
    public void cronTaskGetListProduct() {
        log.info("Iniciando Timed Task Nocturna para actualizar productos en Redis y Postgres...");
        ejecutarActualizacionAutomatica("cron-nocturno");
    }

    /**
     * Método auxiliar que ejecuta la actualización del catálogo y permite verificar el éxito de la tarea.
     *
     * @param origen Cadena de texto que indica el origen que dispara la tarea (ej. "cron-nocturno", "trigger-manual").
     * @return true si la sincronización hacia Redis y Postgres fue ejecutada exitosamente, false si ocurrió un error.
     * @see com.proyecto.servicios.service.GestoPagoProductService#sincronizarCatalogoProductos()
     * @author Agustin Lopez Parra
     * @version 1.1
     */
    public boolean ejecutarActualizacionAutomatica(String origen) {
        log.info("Ejecutando actualización automática con origen: {}", origen);
        try {
            // Llama al servicio que consulta la API, guarda en Postgres y limpia la caché de Redis
            productService.sincronizarCatalogoProductos();
            log.info("Actualización de getlistproduct completada con éxito.");
            return true;
        } catch (Exception e) {
            log.error("Error crítico en la actualización automática de productos: {}", e.getMessage(), e);
            return false;
        }
    }
}
