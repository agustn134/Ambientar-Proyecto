package com.proyecto.servicios;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients
@EnableCaching
@Slf4j

public class App implements CommandLineRunner {

    @Autowired
    private ApplicationContext context;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            displayInfo(context.getBean(BuildProperties.class));
        } catch (Exception e) {
            log.info("Iniciando aplicación (BuildProperties no generado en este arranque)");
        }
    }

    private static void displayInfo(BuildProperties buildProperties) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        String out = formatter.format(buildProperties.getTime());
        log.info("Nombre artefacto: " + buildProperties.getName() + "\n"
                + "Versión: " + buildProperties.getVersion() + "\n"
                + "Fecha Compilación: " + out + "\n"
                + "Artefacto: " + buildProperties.getArtifact() + "\n"
                + "Grupo: " + buildProperties.getGroup());

    }
}
