package com.proyecto.servicios;

import com.proyecto.servicios.config.*;
import com.proyecto.servicios.exception.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErroresHttpContratoTest {
    @Test void ejemploDeValidacionLocalNuncaIndicaPagoEnviado() {
        var op = new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()
                .content(new Content().addMediaType("application/json", new MediaType().schema(new ObjectSchema())))));
        var api = new OpenApi().openAPI().paths(new Paths().addPathItem("/api/gestopago/pagos/validar", new PathItem().post(op)));
        new OpenApi().respuestasUniformes().customise(api);
        var ejemplo = (java.util.Map<?, ?>) op.getResponses().get("200").getContent().get("application/json")
                .getExamples().get("VALIDACION_LOCAL").getValue();
        assertEquals(200, ejemplo.get("status"));
        assertEquals(false, ((java.util.Map<?, ?>) ejemplo.get("datos")).get("pagoEnviado"));
        assertTrue(op.getResponses().containsKey("422"));
        assertFalse(op.getResponses().containsKey("502"));
    }
    @RestController
    static class ErroresControlados {
        @GetMapping(value="/json",produces="application/json") Object json() { return java.util.Map.of("dato",1); }
        @PostMapping("/sql") Object sql(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String,String> body) {
            throw new DataIntegrityViolationException("detalle SQL privado",new java.sql.SQLException("SELECT secreto",body.get("estado")));
        }
        @PostMapping("/externo") Object externo(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String,Integer> body) {
            int status=body.get("status");
            if(status==401) throw new GestoPagoAuthException("token proveedor secreto");
            if(status==503 || status==504) throw new GestoPagoServiceUnavailableException(status,"detalle privado");
            throw new GestoPagoException(status,"detalle privado");
        }
    }
    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ErroresControlados())
            .setControllerAdvice(new RegistroClienteExceptionHandler(),new GlobalExceptionHandler()).build();
    }
    @ParameterizedTest
    @CsvSource({"23505,409,DATO_DUPLICADO","23514,400,VALIDACION","23502,400,VALIDACION","23503,409,CONFLICTO_DATOS","99999,500,ERROR_INTERNO"})
    void integridadDevuelveEstadoCoherenteSinExponerSql(String sqlstate,int status,String codigo) throws Exception {
        mvc().perform(post("/sql").contentType("application/json").content("{\"estado\":\""+sqlstate+"\"}"))
            .andExpect(status().is(status)).andExpect(jsonPath("status").value(status))
            .andExpect(jsonPath("codigo").value(codigo)).andExpect(jsonPath("errores").isArray())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT secreto"))));
    }
    @ParameterizedTest
    @CsvSource({"401,401","429,429","500,500","502,502","503,503","504,504","200,502","999,502"})
    void proveedorNoFiltraDetallesNiProduceExitoAnteUnaExcepcion(int providerStatus,int expected) throws Exception {
        mvc().perform(post("/externo").contentType("application/json").content("{\"status\":"+providerStatus+"}"))
            .andExpect(status().is(expected)).andExpect(jsonPath("status").value(expected))
            .andExpect(jsonPath("codigo").value("SERVICIO_EXTERNO"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("privado"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));
    }
    @Test void tiposJsonYAcceptInvalidoTienenRespuestaControlada() throws Exception {
        mvc().perform(post("/sql").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("JSON_INVALIDO"));
        mvc().perform(post("/sql").contentType("application/xml").content("<dato/>"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("codigo").value("FORMATO_NO_PERMITIDO"));
        mvc().perform(get("/json").accept("application/xml"))
            .andExpect(status().isNotAcceptable()).andExpect(jsonPath("codigo").value("RESPUESTA_NO_ACEPTABLE"));
        mvc().perform(get("/sql"))
            .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("codigo").value("METODO_NO_PERMITIDO"));
    }
    @Test void swaggerDocumentaErroresSinInventarCuerpoEn204NiEstado200EnAltas() {
        var api=new OpenApi().openAPI().paths(new Paths());
        for(String ruta:new String[]{"/clientes","/usuarios/buscar","/usuarios/password","/api/gestopago/productos","/catalogos/paises"}) {
            var op=new Operation().responses(new ApiResponses().addApiResponse("200",new ApiResponse()
                .content(new Content().addMediaType("application/json",new MediaType().schema(new ObjectSchema())))));
            if(!ruta.equals("/catalogos/paises") && !ruta.contains("gestopago")) op.setRequestBody(new RequestBody());
            var path=new PathItem();
            if(ruta.equals("/usuarios/password")) path.put(op);
            else if(ruta.equals("/catalogos/paises") || ruta.contains("gestopago")) path.get(op);
            else path.post(op);
            api.getPaths().addPathItem(ruta,path);
        }
        new OpenApi().respuestasUniformes().customise(api);
        assertFalse(api.getPaths().get("/clientes").getPost().getResponses().containsKey("200"));
        assertTrue(api.getPaths().get("/clientes").getPost().getResponses().containsKey("201"));
        var password=api.getPaths().get("/usuarios/password").getPut().getResponses();
        assertNull(password.get("204").getContent());
        assertFalse(password.containsKey("200"));
        for(var path:api.getPaths().values()) for(var op:path.readOperations()) {
            for(String code:new String[]{"401","405","406","500"}) {
                var response=op.getResponses().get(code);
                assertNotNull(response);
                var example=(java.util.Map<?,?>)response.getContent().get("application/json").getExamples().values().iterator().next().getValue();
                assertEquals(Integer.parseInt(code),example.get("status"));
                assertTrue(response.getContent().get("application/json").getSchema().getProperties().containsKey("errores"));
            }
        }
        assertTrue(api.getPaths().get("/usuarios/buscar").getPost().getResponses().keySet().containsAll(java.util.List.of("400","403","404","415")));
        assertTrue(api.getPaths().get("/api/gestopago/productos").getGet().getResponses().keySet().containsAll(java.util.List.of("502","503","504","default")));
        assertFalse(api.getPaths().get("/catalogos/paises").getGet().getResponses().containsKey("403"));
    }
}
