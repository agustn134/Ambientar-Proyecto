package com.proyecto.servicios.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Intercepta métodos en los paquetes controller, service y client.
     */
    @Pointcut("within(com.proyecto.servicios.controller..*) || " +
              "within(com.proyecto.servicios.service..*) || " +
              "within(com.proyecto.servicios.client..*)")
    public void applicationPackagePointcut() {
        // Pointcut vacío, se usa para definir dónde aplicará el Aspecto.
    }

    @Around("applicationPackagePointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        // Nunca serializar argumentos, respuestas, headers ni mensajes de excepciones.
        log.info("Iniciando ejecución de {}.{}()", className, methodName);
        long start = System.nanoTime();
        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable error) {
            log.error("Error en {}.{}(); tipo={}", className, methodName, error.getClass().getSimpleName());
            throw error;
        } finally {
            long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("Finalizando ejecución de {}.{}() en {} ms; resultado={}",
                    className, methodName, elapsedTime, success ? "EXITO" : "ERROR");
        }
    }
}
