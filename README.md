# Ambientar-Proyecto

## Objetivo
Tomando como base la estructura existente del proyecto Java, realizar la configuración necesaria para habilitar la integración con un nuevo servicio externo y mantener los estándares de desarrollo ya implementados en la aplicación.

## Requerimientos

### Configuración de propiedades
* Agregar las propiedades necesarias para consumir el nuevo servicio externo.
* Mantener la misma estructura y convención utilizada por el proyecto para la definición de endpoints, credenciales y parámetros de configuración.

### Integración de servicio externo
* Implementar el consumo del siguiente endpoint: `GET /sistema/service/getProductList.do`
* La petición debe incluir autenticación mediante Bearer Token.
* El token deberá obtenerse desde la configuración de la aplicación y no deberá quedar hardcodeado dentro del código fuente.

### Arquitectura
Mantener la misma estructura de capas existente en el proyecto:
* Controller
* Service
* Client/Integration
* DTOs o modelos de respuesta
* Configuración

### Manejo de errores
Implementar manejo de excepciones para:
* Errores de comunicación.
* Respuestas no exitosas.
* Timeouts.
* Errores de autenticación.

### Registro y monitoreo
* Registrar en logs el inicio y fin de la invocación.
* Registrar errores de integración sin exponer información sensible.

### Pruebas
* Crear pruebas unitarias para la capa de servicio.
* Simular respuestas exitosas y escenarios de error.

---

## Entregables

A continuación se listan los artefactos desarrollados junto a sus rutas (direccionamientos) en el proyecto:

1. **Configuración de propiedades.**
   *Ruta:* `src/main/resources/application.properties`

2. **Cliente de integración.**
   *Ruta:* `src/main/java/com/proyecto/servicios/client/GestoPagoProductClient.java`

3. **Servicio de negocio.**
   *Interfaz:* `src/main/java/com/proyecto/servicios/service/GestoPagoProductService.java`
   *Implementación:* `src/main/java/com/proyecto/servicios/service/Impl/GestoPagoProductServiceImpl.java`

4. **DTOs necesarios.**
   *Respuesta Externa:* `src/main/java/com/proyecto/servicios/model/gestopago/GestoPagoProductResponse.java`
   *Modelo de Producto:* `src/main/java/com/proyecto/servicios/model/gestopago/ProductoDto.java`
   *Modelo de Errores Genéricos:* `src/main/java/com/proyecto/servicios/model/error/ErrorResponseDto.java`

5. **Pruebas unitarias.**
   *Ruta:* `src/test/java/com/proyecto/servicios/service/GestoPagoProductServiceTest.java`

6. **Breve documentación explicando la solución implementada y las decisiones técnicas tomadas.**
   - **Desacoplamiento Cliente/Servicio:** Se implementó `Spring Cloud OpenFeign` aislando la llamada HTTP del negocio. Se configuró lectura XML directa mediante anotaciones `JAXB`. Si falta el Bearer Token en configuración, el servicio lo solicita autónomamente a la Base de Datos (`GestoPagoTokenService`).
   - **Manejo de Errores Global:** La aplicación es resiliente gracias a la intercepción de errores de Feign (`GestoPagoErrorDecoder`) y una clase con `@ControllerAdvice` (`GlobalExceptionHandler`), que atrapa errores de seguridad (401/403) y conectividad (Timeouts), protegiendo la API con JSON estandarizados.
   - **Logging Desacoplado (AOP):** Para no saturar el código con logs redundantes y por seguridad, se introdujo `Spring AOP`. `LoggingAspect.java` intercepta silenciosamente controladores, servicios y clientes, midiendo inicio, fin (ms) y sanitizando automáticamente los argumentos o respuestas de error si detecta un token o texto demasiado largo.
   - **Testing sin gasto de Cuota:** Todas las pruebas unitarias utilizan `JUnit 5` y `Mockito`. Esto garantiza que los test pasen simulando escenarios de conexión con error, fallbacks y respuestas vacías sin comprometer la estricta cuota de consumo del proveedor GestoPago.
