package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;

import java.util.List;

public interface GestoPagoProductService {

    /**
     * Consulta el catálogo en GestoPago vía Feign, parsea el XML y guarda/actualiza los productos en la base de datos.
     *
     * @return Respuesta procesada de GestoPago
     */
    GestoPagoProductResponse sincronizarCatalogoProductos();

    /**
     * Consume directamente el endpoint GET /sistema/service/getProductList.do con Bearer Token
     * obtenido de la configuración de la aplicación y retorna la respuesta XML procesada.
     *
     * @return Respuesta procesada de GestoPago
     */
    GestoPagoProductResponse consultarCatalogoGestoPago();

    /**
     * Retorna la lista de productos activos almacenados en la base de datos local.
     *
     * @return Lista de entidades GestoPagoProducto
     */
    List<GestoPagoProducto> obtenerProductosGuardados();
}
