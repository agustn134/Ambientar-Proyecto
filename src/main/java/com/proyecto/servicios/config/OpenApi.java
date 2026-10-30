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
            boolean validacionLocal=ruta.equals("/api/gestopago/pagos/validar");
            boolean financiero=(ruta.startsWith("/api/gestopago/pagos") && !validacionLocal) || ruta.startsWith("/api/gestopago/cuentas/");
            boolean simulacion=ruta.startsWith("/api/simulacion/");
            boolean aceptar=metodo==PathItem.HttpMethod.POST && (ruta.equals("/api/gestopago/pagos") || ruta.equals("/api/simulacion/pagos"));
            boolean externo=ruta.startsWith("/api/gestopago/") && !validacionLocal && !financiero;
            boolean protegido=!registro && !login && !ruta.startsWith("/catalogos/");
            boolean sinCuerpo=ruta.equals("/usuarios/password") || ruta.equals("/clientes") && metodo==PathItem.HttpMethod.DELETE;
            if(sinCuerpo) {
                respuestas.keySet().removeIf(code -> code.startsWith("2"));
                respuestas.addApiResponse("204",new ApiResponse().description("Operación completada correctamente. Sin cuerpo de respuesta."));
            }
            if(registro && respuestas.containsKey("200")) respuestas.addApiResponse("201",respuestas.remove("200"));
            if(aceptar && respuestas.containsKey("200")) respuestas.addApiResponse("202",respuestas.remove("200"));
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
            if(validacionLocal) {
                agregar(respuestas,ruta,422,"MONTO_NO_PERMITIDO","La solicitud no cumple las reglas del producto habilitado.",
                    "El catálogo o la política local rechazan el producto, servicio, monto o destino. No se envía un pago.");
                if(respuestas.get("200") != null) {
                    respuestas.get("200").setDescription("Contrato local válido; no se ejecuta un pago ni se verifica saldo.");
                    respuestas.get("200").getContent().get("application/json").addExamples("VALIDACION_LOCAL",
                        new io.swagger.v3.oas.models.examples.Example().value(Map.of(
                            "timestamp", "2026-10-29T03:05:42", "status", 200, "codigo", "EXITO",
                            "mensaje", "Contrato local válido. No se ha enviado ningún pago.", "path", ruta,
                            "datos", Map.of("contratoLocalValido", true, "idServicio", 78, "idProducto", 219,
                                "montoPago", 40, "requiereVerificacionReferencia", false, "pagoEnviado", false),
                            "errores", List.of())));
                }
            }
            if(financiero || simulacion) {
                agregar(respuestas,ruta,409,"IDEMPOTENCIA_CONFLICTO","La clave ya corresponde a otros datos. Consulta la operación existente.","Conflicto de idempotencia o posible duplicado.");
                agregar(respuestas,ruta,422,"REGLA_FINANCIERA","La cuenta, saldo o producto no permiten esta operación.","Regla de negocio incumplida; no se envía un pago.");
                agregar(respuestas,ruta,503,"REFERENCIA_NO_CONCLUYENTE","No fue posible verificar la referencia. No se reservan fondos.","Verificación previa no disponible.");
                if(aceptar && respuestas.get("202")!=null)respuestas.get("202").setDescription("Pago persistido en RESERVED. No acredita una venta aplicada. Consulta datos.estado.");
            }
            if(externo) {
                agregar(respuestas,ruta,400,"SERVICIO_EXTERNO","GestoPago no pudo completar la operación; intenta nuevamente y, si persiste, contacta al administrador","El proveedor rechazó la solicitud enviada.");
                agregar(respuestas,ruta,429,"SERVICIO_EXTERNO","Se alcanzó el límite de solicitudes a GestoPago; espera antes de reintentar","Límite de peticiones del proveedor alcanzado.");
                if(ruta.startsWith("/api/gestopago/productos/")) {
                    var limitada=respuestas.get("429");
                    limitada.setDescription("Cuota compartida: máximo tres intentos externos en una ventana móvil de 24 horas. Los fallos también consumen intento.");
                    limitada.addHeaderObject("Retry-After",new io.swagger.v3.oas.models.headers.Header()
                            .description("Segundos hasta que venza el intento más antiguo; no dispara un reintento automático.")
                            .schema(new IntegerSchema().example(86400)));
                    limitada.getContent().get("application/json").addExamples("CUOTA_CATALOGO_AGOTADA",
                            ejemplo(ruta,429,"CUOTA_CATALOGO_AGOTADA","Límite de catálogo alcanzado. Consulta los productos guardados localmente."));
                }
                agregar(respuestas,ruta,502,"SERVICIO_EXTERNO","GestoPago no pudo completar la operación; intenta nuevamente y, si persiste, contacta al administrador","Respuesta inválida del proveedor.");
                agregar(respuestas,ruta,503,"SERVICIO_EXTERNO","GestoPago no está disponible temporalmente; intenta más tarde","Proveedor temporalmente no disponible.");
                agregar(respuestas,ruta,504,"SERVICIO_EXTERNO","GestoPago no respondió dentro del tiempo permitido; intenta nuevamente","Tiempo de comunicación agotado.");
                respuestas.get("503").getContent().get("application/json").addExamples("SESION_PROVEEDOR",ejemplo(ruta,503,"SERVICIO_EXTERNO","La conexión con el proveedor no está disponible temporalmente."));
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
