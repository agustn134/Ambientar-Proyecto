package com.proyecto.servicios;

import com.proyecto.servicios.controller.*;
import com.proyecto.servicios.entity.gestopago.GestoPagoProducto;
import com.proyecto.servicios.model.gestopago.ProductoResponse;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

class ContratoValidacionesTest {
    @Test void entradasDeControladoresUsanBodyValidadoSinParametrosDeRutaOConsulta() {
        for(var tipo:new Class<?>[]{UsuarioController.class,ClienteController.class,CuentaController.class,CatalogoController.class,AuthController.class}) {
            for(var metodo:tipo.getDeclaredMethods()) for(var parametro:metodo.getParameters()) {
                assertFalse(parametro.isAnnotationPresent(PathVariable.class),metodo.toString());
                assertFalse(parametro.isAnnotationPresent(RequestParam.class),metodo.toString());
                assertFalse(parametro.isAnnotationPresent(ModelAttribute.class),metodo.toString());
                if(parametro.isAnnotationPresent(RequestBody.class)) assertTrue(parametro.isAnnotationPresent(Valid.class),metodo.toString());
                assertFalse(parametro.getType().isAnnotationPresent(jakarta.persistence.Entity.class));
            }
        }
    }

    @Test void respuestaDeProductoEsUnDtoIndependienteDeLaEntidad() {
        var producto=GestoPagoProducto.builder().id(3).idProducto(41).producto("Producto QA").precio(new java.math.BigDecimal("20.50")).build();
        var respuesta=ProductoResponse.desde(producto);
        assertFalse(ProductoResponse.class.isAnnotationPresent(jakarta.persistence.Entity.class));
        assertEquals(41,respuesta.idProducto());
        assertEquals(new java.math.BigDecimal("20.50"),respuesta.precio());
        producto.setProducto("Actualizado");
        assertEquals("Producto QA",respuesta.producto());
    }
}
