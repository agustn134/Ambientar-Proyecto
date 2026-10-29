package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import java.util.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApi {
    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI().addServersItem(new Server().url("/").description("Servidor actual"))
            .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearerAuth",
                new io.swagger.v3.oas.models.security.SecurityScheme()
                    .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                    .scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));
    }
    @Bean
    public org.springdoc.core.customizers.OpenApiCustomizer respuestasUniformes() {
        return api -> api.getPaths().forEach((ruta,path) -> path.readOperationsMap().forEach((metodo,operation) -> {
            if(operation.getResponses()==null) operation.setResponses(new ApiResponses());
            var respuestas=operation.getResponses();
            respuestas.keySet().removeIf(code -> !code.startsWith("2"));
            boolean registro=ruta.equals("/clientes") && metodo==PathItem.HttpMethod.POST;
            boolean login=ruta.equals("/auth/login");
            boolean externo=ruta.startsWith("/api/gestopago/");
            boolean protegido=!registro && !login && !ruta.startsWith("/catalogos/");
            boolean sinCuerpo=ruta.equals("/usuarios/password") || ruta.equals("/clientes") && metodo==PathItem.HttpMethod.DELETE;
            if(sinCuerpo) {
                respuestas.keySet().removeIf(code -> code.startsWith("2"));
                respuestas.addApiResponse("204",new ApiResponse().description("Operación completada correctamente. Sin cuerpo de respuesta."));
            }
            if(registro && respuestas.containsKey("200")) respuestas.addApiResponse("201",respuestas.remove("200"));
            operation.getResponses().forEach((code,response) -> {
                response.setDescription(code.equals("204") ? "Operación completada correctamente. Sin cuerpo de respuesta."
                    : registro ? "Cliente, usuario, domicilio y cuenta creados correctamente" : "Operación realizada correctamente. El resultado se encuentra en datos; una lista sin coincidencias devuelve una lista vacía.");
                if(!code.startsWith("2") || code.equals("204") || response.getContent()==null) return;
                response.getContent().values().forEach(media -> {
                    var datos=media.getSchema();
                    var schema=new io.swagger.v3.oas.models.media.ObjectSchema();
                    schema.addProperty("timestamp",new io.swagger.v3.oas.models.media.DateTimeSchema());
                    schema.addProperty("status",new io.swagger.v3.oas.models.media.IntegerSchema());
                    schema.addProperty("codigo",new io.swagger.v3.oas.models.media.StringSchema().example("EXITO"));
                    schema.addProperty("mensaje",new io.swagger.v3.oas.models.media.StringSchema());
                    schema.addProperty("path",new io.swagger.v3.oas.models.media.StringSchema());
                    schema.addProperty("datos",datos);
                    schema.addProperty("errores",new io.swagger.v3.oas.models.media.ArraySchema().items(new io.swagger.v3.oas.models.media.ObjectSchema()));
                    media.setSchema(schema);
                });
            });
            if(operation.getRequestBody()!=null) {
                agregar(respuestas,ruta,400,"VALIDACION","Revisa los campos de la petición","Campos inválidos o reglas de negocio incumplidas. errores contiene los mensajes por campo.");
                respuestas.get("400").getContent().get("application/json").addExamples("JSON_INVALIDO",ejemplo(ruta,400,"JSON_INVALIDO","El cuerpo está ausente o contiene JSON, tipos o fechas inválidos"));
                agregar(respuestas,ruta,415,"FORMATO_NO_PERMITIDO","Envía el cuerpo con Content-Type application/json","Formato del cuerpo no compatible.");
            }
            agregar(respuestas,ruta,401,"NO_AUTENTICADO","Inicia sesión y envía un token Bearer vigente; el token está ausente, vencido, revocado o no es válido",
                protegido ? "Se requiere una sesión válida." : "La ruta permite acceso sin token. Si se envía un Bearer inválido, el filtro rechaza la petición.");
            if(login || ruta.equals("/usuarios/password")) respuestas.get("401").getContent().get("application/json")
                .addExamples("CREDENCIALES_INVALIDAS",ejemplo(ruta,401,"CREDENCIALES_INVALIDAS","Credenciales inválidas o acceso no disponible"));
            if(protegido) agregar(respuestas,ruta,403,"ACCESO_DENEGADO","No tienes permiso para esta operación","El rol o los permisos no permiten esta operación.");
            if(protegido && !ruta.endsWith("/listar") && !ruta.equals("/auth/me")) agregar(respuestas,ruta,404,"RECURSO_NO_ENCONTRADO","Recurso no encontrado","Registro inexistente o no disponible para este usuario. No se revela la existencia de registros ajenos.");
            if(registro || ruta.equals("/clientes") && metodo==PathItem.HttpMethod.PUT) {
                agregar(respuestas,ruta,409,"DATO_DUPLICADO","Un identificador o correo ya está registrado","Datos duplicados o conflicto con registros relacionados.");
                respuestas.get("409").getContent().get("application/json").addExamples("CONFLICTO_DATOS",ejemplo(ruta,409,"CONFLICTO_DATOS","La operación entra en conflicto con registros relacionados; verifica las referencias enviadas"));
            }
            agregar(respuestas,ruta,405,"METODO_NO_PERMITIDO","El método HTTP no está permitido para esta ruta; consulta Swagger","Se utilizó un método HTTP distinto sobre esta ruta; primero se evalúa la seguridad.");
            agregar(respuestas,ruta,406,"RESPUESTA_NO_ACEPTABLE","Solicita una respuesta JSON con Accept: application/json","Formato de respuesta no disponible.");
            agregar(respuestas,ruta,500,"ERROR_INTERNO","Ha ocurrido un error inesperado en el servidor.","Error interno controlado, sin trazas ni detalles técnicos.");
            if(externo) {
                agregar(respuestas,ruta,400,"SERVICIO_EXTERNO","GestoPago no pudo completar la operación; intenta nuevamente y, si persiste, contacta al administrador","El proveedor rechazó la solicitud enviada.");
                agregar(respuestas,ruta,429,"SERVICIO_EXTERNO","Se alcanzó el límite de solicitudes a GestoPago; espera antes de reintentar","Límite de peticiones del proveedor alcanzado.");
                agregar(respuestas,ruta,502,"SERVICIO_EXTERNO","GestoPago no pudo completar la operación; intenta nuevamente y, si persiste, contacta al administrador","Respuesta inválida del proveedor.");
                agregar(respuestas,ruta,503,"SERVICIO_EXTERNO","GestoPago no está disponible temporalmente; intenta más tarde","Proveedor temporalmente no disponible.");
                agregar(respuestas,ruta,504,"SERVICIO_EXTERNO","GestoPago no respondió dentro del tiempo permitido; intenta nuevamente","Tiempo de comunicación agotado.");
                respuestas.get("401").getContent().get("application/json").addExamples("SERVICIO_EXTERNO",ejemplo(ruta,401,"SERVICIO_EXTERNO","No fue posible autenticar la conexión con GestoPago; contacta al administrador"));
                respuestas.get("404").getContent().get("application/json").addExamples("SERVICIO_EXTERNO",ejemplo(ruta,404,"SERVICIO_EXTERNO","El recurso solicitado no está disponible en GestoPago"));
                respuestas.get("500").getContent().get("application/json").addExamples("SERVICIO_EXTERNO",ejemplo(ruta,500,"SERVICIO_EXTERNO","GestoPago no pudo completar la operación; intenta nuevamente y, si persiste, contacta al administrador"));
                respuestas.addApiResponse("default",new ApiResponse().description("Otro error HTTP 4xx/5xx del proveedor con mensaje controlado y codigo SERVICIO_EXTERNO.")
                    .content(new Content().addMediaType("application/json",new MediaType().schema(esquemaError()))));
            }
            if(ruta.equals("/personas") || ruta.equals("/personasActualiza") || ruta.equals("/personasElimina")) {
                respuestas.keySet().removeIf(code -> code.startsWith("2"));
                operation.setDescription("Ruta heredada deshabilitada por seguridad. Utiliza clientes y usuarios.");
            }
        }));
    }
    private static ObjectSchema esquemaError() {
        var campo=new ObjectSchema();
        campo.addProperty("campo",new StringSchema());
        campo.addProperty("mensaje",new StringSchema());
        var schema=new ObjectSchema();
        schema.addProperty("timestamp",new DateTimeSchema());
        schema.addProperty("status",new IntegerSchema());
        schema.addProperty("codigo",new StringSchema());
        schema.addProperty("mensaje",new StringSchema());
        schema.addProperty("path",new StringSchema());
        schema.addProperty("errores",new ArraySchema().items(campo));
        schema.setRequired(List.of("timestamp","status","codigo","mensaje","path","errores"));
        return schema;
    }
    private static io.swagger.v3.oas.models.examples.Example ejemplo(String ruta,int status,String codigo,String mensaje) {
        var errores=codigo.equals("VALIDACION") ? List.of(Map.of("campo","campoDeLaSolicitud","mensaje","Respeta la regla indicada para este campo"))
            : codigo.equals("JSON_INVALIDO") ? List.of(Map.of("campo","body","mensaje","Respeta los tipos y formatos indicados en Swagger")) : List.of();
        return new io.swagger.v3.oas.models.examples.Example().value(Map.of("timestamp","2026-10-17T13:00:00","status",status,"codigo",codigo,"mensaje",mensaje,"path",ruta,"errores",errores));
    }
    private static void agregar(ApiResponses respuestas,String ruta,int status,String codigo,String mensaje,String descripcion) {
        respuestas.addApiResponse(Integer.toString(status),new ApiResponse().description(descripcion)
            .content(new Content().addMediaType("application/json",new MediaType().schema(esquemaError()).addExamples(codigo,ejemplo(ruta,status,codigo,mensaje)))));
    }
}
