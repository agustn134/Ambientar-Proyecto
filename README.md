# Ambientar-Proyecto

Este repositorio contiene la implementación y el módulo de integración del proyecto con los servicios externos de **GestoPago**.

---

## Entregables Completados

De acuerdo a los requerimientos, a continuación se listan los entregables técnicos finalizados:

1. **Configuración de propiedades**
   - Todas las credenciales, tiempos máximos de conexión (timeouts para Feign) y configuración de endpoints externos fueron extraídas y centralizadas. No existe código *hardcodeado*.
   - Ubicación: `src/main/resources/application.properties`

2. **Cliente de integración**
   - Uso de **Spring Cloud OpenFeign** para la declaración e invocación de la API de GestoPago. Inyecta dinámicamente el `Bearer Token` de autorización.
   - Ubicación: `src/main/java/com/proyecto/servicios/client/GestoPagoProductClient.java`

3. **Servicio de negocio**
   - Implementación de la capa lógica aplicando inyección de dependencias. Contiene el *fallback* para buscar el token en BD (mediante `GestoPagoTokenService`) si el properties no lo provee y la lógica para persistir la sincronización del catálogo en la base de datos local usando Flyway/JPA.
   - Ubicación: `src/main/java/com/proyecto/servicios/service/Impl/GestoPagoProductServiceImpl.java`

4. **DTOs necesarios**
   - Clases de respuesta preparadas con JAXB (`@XmlRootElement`) para deserializar automáticamente la estructura en formato XML que devuelve GestoPago sin parseo engorroso.
   - Ubicación: `src/main/java/com/proyecto/servicios/model/gestopago/`

5. **Pruebas unitarias**
   - Conjunto de pruebas creadas con **JUnit 5** y **Mockito**. Estas validan el camino feliz (sincronización y persistencia), respuestas vacías de la API y el control de excepciones / fallbacks del token, sin consumir la cuota limitada real de peticiones al proveedor.
   - Ubicación: `src/test/java/com/proyecto/servicios/service/GestoPagoProductServiceTest.java`

6. **Documentación y Decisiones Técnicas**
   - Documentado en esta misma sección (ver sección de abajo).

---

## Decisiones Técnicas Implementadas

1. **Desacoplamiento de Capas:**
   Se aplicó estrictamente el patrón MVC junto con la separación Cliente/Servicio. El controlador no tiene lógica de parseo, los clientes no tienen lógica de negocio, y el servicio delega la responsabilidad a sus repositorios.

2. **Manejo de Errores Global (Resiliencia):**
   - **`GlobalExceptionHandler` (`@ControllerAdvice`)**: Centraliza la captura de todas las excepciones, asegurando que el cliente del *front-end* reciba un JSON estandarizado unificado (`ErrorResponseDto`), protegiéndolo de fallos críticos o *Timeouts* de la red externa.
   - **`GestoPagoErrorDecoder`**: Se usó el interceptor de Feign. Traduce los errores HTTP nativos del proveedor (4xx, 5xx) a excepciones de negocio controladas (`GestoPagoException`, `GestoPagoAuthException`).

3. **Logging, Monitoreo y Seguridad (AOP):**
   - Se implementó **Spring AOP (Programación Orientada a Aspectos)** a través de la clase `LoggingAspect`. Esto evita ensuciar la lógica con logs repetitivos e invasivos tipo `log.info()`. 
   - Intercepta llamadas, cronometrando los tiempos de ejecución de forma transparente.
   - Se aplicó lógica de **sanitización automática**: cualquier variable, argumento o cuerpo de error que contenga información confidencial (como el string `Bearer`) se enmascara en los logs (`[PROTEGIDO]`) para asegurar cumplimiento de seguridad.
