package com.proyecto.servicios.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

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

        // Evitar registrar invocaciones si log nivel INFO no está activado
        if (log.isInfoEnabled()) {
            log.info("Iniciando ejecución de {}.{}() con argumentos: {}",
                    className, methodName,
                    sanitizeArguments(joinPoint.getArgs()));
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long elapsedTime = System.currentTimeMillis() - start;
            
            if (log.isInfoEnabled()) {
                log.info("Finalizando ejecución de {}.{}() en {} ms.", className, methodName, elapsedTime);
            }
            
            return result;
        } catch (IllegalArgumentException e) {
            log.error("Argumento ilegal en {}.{}(): {}", className, methodName, Arrays.toString(joinPoint.getArgs()));
            throw e;
        } catch (Exception e) {
            long elapsedTime = System.currentTimeMillis() - start;
            log.error("Excepción durante ejecución de {}.{}() luego de {} ms. Causa: {}", 
                      className, methodName, elapsedTime, e.getMessage());
            throw e;
        }
    }

    /**
     * Filtra los argumentos evitando imprimir información de tokens, contraseñas o datos muy largos.
     */
    private String sanitizeArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(arg -> {
                    if (arg == null) return "null";
                    String argStr = arg.toString();
                    if (argStr.toLowerCase().contains("bearer") || argStr.length() > 200) {
                        return "[PROTEGIDO/EXTENSO]";
                    }
                    return argStr;
                })
                .collect(Collectors.toList())
                .toString();
    }
}
