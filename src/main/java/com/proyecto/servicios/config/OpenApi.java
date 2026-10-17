package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApi {
    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI().addServersItem(new Server().url("/").description("Servidor Local"))
            .components(new io.swagger.v3.oas.models.Components().addSecuritySchemes("bearerAuth",
                new io.swagger.v3.oas.models.security.SecurityScheme()
                    .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                    .scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));
    }
    @Bean
    public org.springdoc.core.customizers.OpenApiCustomizer respuestasUniformes() {
        return api -> api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            if(operation.getResponses()==null) return;
            operation.getResponses().forEach((code,response) -> {
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
        }));
    }}
