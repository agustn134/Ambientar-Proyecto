package com.proyecto.servicios.config;

import feign.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import java.io.IOException;
import java.net.URI;
import java.util.*;

/** Configuración exclusiva del cliente catálogo, no se registra en el contexto global. */
public class GestoPagoCatalogoFeignConfig {
    @Bean public Retryer catalogoRetryer() { return Retryer.NEVER_RETRY; }
    @Bean public Client catalogoClient() {
        return (request,options) -> {
            URI uri=URI.create(request.url());
            if(!"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || !request.httpMethod().equals(Request.HttpMethod.GET))
                throw new IOException("Contrato de transporte del catálogo inválido.");
            var config=RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(options.connectTimeoutMillis()))
                    .setResponseTimeout(Timeout.ofMilliseconds(options.readTimeoutMillis())).build();
            try(var client=HttpClients.custom().disableAutomaticRetries().disableRedirectHandling().setDefaultRequestConfig(config).build()) {
                var get=new HttpGet(uri);
                request.headers().forEach((name,values)->values.forEach(value->get.addHeader(name,value)));
                return client.execute(get,response->{
                    byte[] body=new byte[0];
                    if(response.getEntity()!=null) try(var input=response.getEntity().getContent()) {
                        body=input.readNBytes(8*1024*1024+1);
                        if(body.length>8*1024*1024) throw new IOException();
                    }
                    Map<String,Collection<String>> headers=new HashMap<>();
                    for(var header:response.getHeaders()) headers.computeIfAbsent(header.getName(),key->new ArrayList<>()).add(header.getValue());
                    return Response.builder().request(request).status(response.getCode()).reason("GestoPago")
                            .headers(headers).body(body).build();
                });
            } catch(Exception error) { throw new IOException("Comunicación de catálogo no concluyente."); }
        };
    }
}
