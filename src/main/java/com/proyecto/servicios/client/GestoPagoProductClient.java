package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "gestoPagoService", url = "${gestopago.service.url}")
public interface GestoPagoProductClient {

    @GetMapping(
            value = "${gestopago.service.endpoint:/sistema/service/getProductList.do}",
            consumes = {
                    MediaType.APPLICATION_XML_VALUE,
                    MediaType.TEXT_XML_VALUE,
                    MediaType.ALL_VALUE
            }
    )
    GestoPagoProductResponse getProductList(
            @RequestHeader("Authorization") String authorization);
}
