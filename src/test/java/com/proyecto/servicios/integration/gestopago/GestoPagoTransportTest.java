package com.proyecto.servicios.integration.gestopago;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class GestoPagoTransportTest {
    HttpServer server;
    URI uri;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/test");
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void codificaFormularioYNoSigueRedireccion() {
        var count=new AtomicInteger(); var body=new AtomicReference<String>();
        server.createContext("/test", request -> {
            count.incrementAndGet(); body.set(new String(request.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            request.getResponseHeaders().add("Location",uri.toString());
            request.sendResponseHeaders(302,-1);request.close();
        }); server.start();
        var result=new GestoPagoTransport().execute(uri,Map.of("Authorization","Bearer sintetico"),Map.of("signed","a+b/c=","random","123"));
        assertEquals(302,result.status()); assertEquals(1,count.get());
        assertTrue(body.get().contains("signed=a%2Bb%2Fc%3D"));
        assertFalse(result.toString().contains("sintetico"));
    }
    @Test void timeoutNoReenvia() {
        var count=new AtomicInteger();
        server.createContext("/test", request -> {count.incrementAndGet();
            try {Thread.sleep(2500);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            request.close();
        });server.start();
        var error=assertThrows(IllegalStateException.class,()->new GestoPagoTransport(1).execute(uri,Map.of(),Map.of("signed","dato")));
        assertEquals(1,count.get());assertNull(error.getCause());
    }
    @Test void produccionRechazaHttpSinEnviar() {
        assertThrows(IllegalStateException.class,()->new GestoPagoTransport().post(uri,Map.of(),Map.of()));
    }
}
