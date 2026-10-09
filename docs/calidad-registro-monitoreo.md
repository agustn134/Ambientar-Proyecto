# Calidad de código, registro y monitoreo

## Registro de invocaciones

`LoggingAspect` centraliza los logs para controller, service y client, manteniendo la estructura AOP y SLF4J existente. Registra inicio, fin, duración en milisegundos y resultado EXITO/ERROR. El fin se escribe en `finally`, también cuando una invocación falla. La duración usa un reloj monotónico (`System.nanoTime`). No se necesitan bloques de logging repetidos en cada método.

Los logs no serializan argumentos ni respuestas. Tampoco incluyen mensajes completos de excepciones o stack traces que puedan contener SQL, cuerpos externos, tokens, correos o contraseñas. Se conserva clase/método y tipo de excepción para identificar la operación. Esto sustituye la sanitización por longitud: una contraseña corta también es sensible.

## Errores de integración

- `GestoPagoErrorDecoder` registra operación Feign y status HTTP sin imprimir URL con query, headers ni body del proveedor.
- Los handlers, renovación de token y tarea programada registran categoría/status/tipo en lugar del mensaje crudo de la excepción.
- Los errores de catálogo vacío usan un mensaje controlado, sin reproducir texto arbitrario del proveedor.
- Feign queda con loggerLevel NONE y logger de clientes en INFO; el aspecto proporciona los logs de invocación.
- Se desactiva `SqlExceptionHelper` para evitar que los mensajes SQL incluyan identificadores/correos duplicados. El handler de registro conserva logs de status/código y número de campos inválidos.

No se afirma que los logs de todas las bibliotecas o cualquier configuración futura sean seguros. Evitar habilitar trazas de headers/body, SQL con parámetros o DEBUG indiscriminado en entregas y producción. No existe todavía un sistema de métricas, alertas ni correlación distribuida; los logs implementados son locales.

## Calidad de código

- Inyección por constructor con `@RequiredArgsConstructor` y campos `final` en el servicio nuevo; `PasswordEncoder` es un bean reutilizable.
- `registrar` coordina validación, unicidad, persistencia y respuesta. Métodos privados con nombres explícitos construyen cliente, domicilio, cuenta y usuario.
- Se evita duplicar el manejo de excepciones con try/catch en los controladores. Los advice producen respuestas centralizadas.
- El formatter estricto de fechas se comparte como constante; imports reemplazan nombres de clase completos dentro del servicio.
- Se mantienen las capas controller/service/repository/entity/DTO y las convenciones Lombok, Spring Data, Flyway y BCrypt.
- La transacción explícita con `sfTransactionManager` se conserva al reorganizar el código.

No se certifica Clean Code para todo el repositorio: existe un servicio de clientes anterior con validaciones parciales que deberá consolidarse al implementar actualización/baja, y dos tests antiguos de GestoPago documentados como pendientes.

## Verificación

```powershell
.\gradlew.bat test --tests com.proyecto.servicios.RegistroClienteIntegrationTest --tests com.proyecto.servicios.config.LoggingSecurityTest
```

Las pruebas de logging capturan salida para comprobar inicio/fin, fin ante error, ausencia de secretos en argumentos/respuestas/mensajes y ausencia de body/query externos. Las pruebas de registro conservan la cobertura HTTP, persistencia y atomicidad, ahora con el aspecto activado.

Resultado del 8 de octubre de 2026: **16 pruebas aprobadas** (12 de registro/BCrypt y 4 de logs), `BUILD SUCCESSFUL in 1m 53s`. El refactor conserva el registro y la atomicidad; se comprobó que los logs capturados no imprimen los secretos sintéticos de prueba. No se repitió la suite completa con los dos fallos antiguos de GestoPago.
