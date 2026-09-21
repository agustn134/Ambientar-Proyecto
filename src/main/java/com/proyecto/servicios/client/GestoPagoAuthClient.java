package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gestoPagoAuth", url = "${gestopago.auth.url}")
public interface GestoPagoAuthClient {

    @PostMapping("/sistema/app/jwt-gp/authenticate/")
    GestoPagoAuthResponse authenticate(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("idDistribuidor") Integer idDistribuidor,
            @RequestParam("codigoDispositivo") String codigoDispositivo,
            @RequestParam("password") String password);

    @GetMapping(
            value = "/sistema/service/getProductList.do",
            consumes = {org.springframework.http.MediaType.APPLICATION_XML_VALUE, org.springframework.http.MediaType.TEXT_XML_VALUE, org.springframework.http.MediaType.ALL_VALUE}
    )
    GestoPagoProductResponse getProductList(
            @RequestHeader("Authorization") String authorization);
}
