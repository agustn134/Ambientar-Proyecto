package com.proyecto.servicios.integration.simulacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.repositorys.simulacion.SimulacionRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProveedorSimuladoTest {
    final SimulacionRepository repo=mock(SimulacionRepository.class);
    final ProveedorSimulado proveedor=new ProveedorSimulado(repo,new ObjectMapper(),
            "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=","p5ldmBPdd/9pjC0bDC/nSg==");
    @Test void serializaCifraCodificaYDescifraSinAccesoRed()throws Exception{
        String json="{\"referencia\":\"SIM0000001\",\"detalle\":\"á+&=\"}";
        var request=proveedor.preparar(json);
        assertTrue(request.body().contains("&signed="));assertFalse(request.body().contains("SIM0000001"));
        assertEquals(new ObjectMapper().readTree(json),proveedor.recibir(request));
        var jwt=com.auth0.jwt.JWT.decode(request.authorization().substring(7));
        assertEquals(86400,jwt.getExpiresAtAsInstant().getEpochSecond()-jwt.getIssuedAtAsInstant().getEpochSecond());
        assertEquals(request.authorization(),proveedor.preparar(json).authorization());verifyNoInteractions(repo);
    }
    @Test void rechazaTokenFormOCifradoManipulados(){
        var request=proveedor.preparar("{}");
        assertThrows(IllegalStateException.class,()->proveedor.recibir(new ProveedorSimulado.Intercambio("Bearer falso",request.body())));
        assertThrows(IllegalStateException.class,()->proveedor.recibir(new ProveedorSimulado.Intercambio(request.authorization(),request.body()+"&signed=mal")));
        assertFalse(request.toString().contains(request.authorization()));
    }
    @Test void referenciaValidaEInvalidaInterpretanXmlSinReservar(){
        assertEquals(com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.Resultado.VALIDA,proveedor.verificarReferencia("{\"referencia\":\"SIM0000001\"}"));
        assertEquals(com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.Resultado.INVALIDA,proveedor.verificarReferencia("{\"referencia\":\"SIM0000000\"}"));
        assertEquals(com.proyecto.servicios.integration.gestopago.GestoPagoReferenceVerifier.Resultado.INDETERMINADA,proveedor.verificarReferencia("{\"referencia\":\"SIM0000002\"}"));verifyNoInteractions(repo);
    }
}
