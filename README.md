# Integración GestoPago (Ambientar-Proyecto)

## Descripción del proyecto
Este proyecto tiene como objetivo integrar la aplicación existente en Java (Spring Boot) con los servicios externos de **GestoPago**. La funcionalidad principal consiste en consumir de manera segura la lista de productos de GestoPago, parsear su respuesta XML a entidades utilizables y guardarlas de forma eficiente en una base de datos PostgreSQL utilizando una capa intermedia de caché con Redis para optimizar tiempos de respuesta.

**Características principales:**
- **Sincronización:** Job programado (`Cron Task`) gestionado en la clase [`GestoPagoProductScheduler.java`](./src/main/java/com/proyecto/servicios/scheduler/GestoPagoProductScheduler.java) para mantener siempre actualizados los catálogos en PostgreSQL y limpiar/actualizar la caché de Redis.
- **Eficiencia con Caché:** Implementación de Redis (`@Cacheable`, `@CacheEvict`) para despachar el catálogo rápidamente sin sobrecargar la base de datos o el proveedor externo.
- **Desacoplamiento HTTP:** Comunicación con la API de GestoPago implementada a través de [`Spring Cloud OpenFeign`](./src/main/java/com/proyecto/servicios/client/GestoPagoProductClient.java) y [`JAXB`](./src/main/java/com/proyecto/servicios/model/gestopago/GestoPagoProductResponse.java) para el parseo de XML.
- **Manejo Dinámico de Tokens:** Gestión de Bearer Tokens, con posibilidad de leer de las propiedades del sistema o auto-renovar desde la base de datos sin requerir _hardcodeo_.

---

## Instalación

Sigue estos pasos para instalar y ejecutar el proyecto en tu máquina local.

**Requisitos previos:**
- Java 17+ (o la versión que estés utilizando)
- Base de datos PostgreSQL ejecutándose.
- Servidor Redis ejecutándose.
- Git.

1. **Clona el repositorio:**
```bash
git clone https://github.com/tu-usuario/ambientar-proyecto.git
cd ambientar-proyecto
```

2. **Configura las variables de entorno:**
Asegúrate de configurar los parámetros en `src/main/resources/application.properties` con tus credenciales de PostgreSQL, Redis y GestoPago (token y credenciales de acceso).

3. **Ejecuta el proyecto con Gradle:**
Dependiendo de tu terminal (Bash, PowerShell), ejecuta:
```bash
./gradlew bootRun
```
*(Si usas CMD estándar en Windows, usa `gradlew.bat bootRun`)*

---

## Uso

Una vez que el proyecto esté corriendo en `http://localhost:8080`, puedes interactuar con los siguientes endpoints principales utilizando clientes HTTP (como Liteclient, Postman o Swagger).

### 1. Consultar Catálogo Externo
Este endpoint realiza una petición directa a GestoPago (`GET /sistema/service/getProductList.do`) y devuelve la respuesta parseada al cliente sin pasar por base de datos o caché, ideal para pruebas de conectividad.

* **URL:** `GET http://localhost:8080/api/gestopago/productos/consultar-externo`
<img width="1593" height="1910" alt="image" src="https://github.com/user-attachments/assets/7c34045c-2579-4d84-8a8e-dde7b69eb95e" />


> **Respuesta Esperada (Status 200 OK):**  
> Cuando el servidor responde con un status `200`, significa que la autenticación (Bearer Token) fue exitosa y la API externa retornó correctamente el catálogo en formato XML. Nuestra aplicación lo interceptó, lo transformó automáticamente de XML a JSON mediante los [`DTOs`](./src/main/java/com/proyecto/servicios/model/gestopago/ProductoDto.java) y lo está entregando estructurado en el cuerpo de la respuesta.

### 2. Sincronizar Catálogo (PostgreSQL & Redis)
Este endpoint fuerza la sincronización manual. Llama a la API de GestoPago, limpia el caché actual en Redis, procesa los productos usando [`MapStruct`](./src/main/java/com/proyecto/servicios/mapper/GestoPagoProductoMapper.java), y los guarda/actualiza permanentemente en PostgreSQL.

* **URL:** `POST http://localhost:8080/api/gestopago/productos/sincronizar`
<img width="1790" height="1795" alt="image" src="https://github.com/user-attachments/assets/0a04e145-708a-4587-93ab-ab83ed08fd9b" />

> **Respuesta Esperada (Status 200 OK):**  
> Al recibir un `200 OK`, el flujo completo se ha ejecutado sin errores: 
> 1) La API externa entregó los productos. 
> 2) [`MapStruct`](./src/main/java/com/proyecto/servicios/mapper/GestoPagoProductoMapper.java) mapeó exitosamente los datos a la [Entidad Java](./src/main/java/com/proyecto/servicios/entity/gestopago/GestoPagoProducto.java).
> 3) Se guardaron en [PostgreSQL](./src/main/java/com/proyecto/servicios/repositorys/gestopago/GestoPagoProductoRepository.java) exitosamente. 
> 4) La caché en Redis se invalidó para obligar a que la próxima consulta `GET` lea los datos recién actualizados.

### Posibles Códigos de Error y Excepciones
Gracias a la implementación del [`GlobalExceptionHandler`](./src/main/java/com/proyecto/servicios/config/GlobalExceptionHandler.java) y el interceptor de Feign ([`GestoPagoErrorDecoder`](./src/main/java/com/proyecto/servicios/config/GestoPagoErrorDecoder.java)), si algo sale mal con la API externa o la autenticación, la aplicación nunca explota; en su lugar devuelve respuestas JSON limpias. Se implementó un manejo granular con **excepciones personalizadas**:

* **`401 Unauthorized` / `403 Forbidden`:** Ocurre si el Bearer Token configurado es inválido, ha expirado o no hay permisos. Se lanza [`GestoPagoAuthException`](./src/main/java/com/proyecto/servicios/exception/GestoPagoAuthException.java).
* **`404 Not Found`:** Si la URL de GestoPago cambia o el endpoint dejó de existir. Se lanza [`GestoPagoNotFoundException`](./src/main/java/com/proyecto/servicios/exception/GestoPagoNotFoundException.java).
* **`429 Too Many Requests`:** Se lanza si GestoPago bloquea la petición por exceso de llamadas en poco tiempo (Rate Limit).
* **`502 Bad Gateway`:** Si la API externa responde HTTP 200, pero el XML viene vacío o con errores reportados dentro del propio XML.
* **`503 Service Unavailable` / `504 Gateway Timeout`:** Si los servidores de GestoPago están caídos o no responden a tiempo. Se lanza [`GestoPagoServiceUnavailableException`](./src/main/java/com/proyecto/servicios/exception/GestoPagoServiceUnavailableException.java).
* **`500 Internal Server Error`:** Errores no controlados, problemas de parseo en nuestra app o fallo al conectar con PostgreSQL/Redis local.
---

## Flujo de Datos y Caché (Jerarquía)

A continuación se describe el ciclo de vida de una petición para obtener los productos. El sistema sigue una jerarquía de 3 capas (Redis -> PostgreSQL -> API GestoPago) para garantizar la mayor velocidad posible y evitar sobrecargar al proveedor externo.


<img width="1024" height="554" alt="image" src="https://github.com/user-attachments/assets/04990cfb-d6ef-4e87-a013-7bc561232cc2" />


### Explicación simple del flujo paso a paso:

1. **Punto de Entrada (Inicio):**
   El cliente hace una petición para obtener la lista de productos.

2. **Capa 1: Memoria Rápida (Redis)**
   * **Decisión:** *¿Están los productos ya guardados en nuestra memoria rápida?*
   * **Si SÍ:** Se devuelven inmediatamente al cliente. Es el escenario más rápido (respuesta casi instantánea).
   * **Si NO:** El sistema sigue buscando en la siguiente capa.

3. **Capa 2: Base de Datos Local (PostgreSQL)**
   * **Decisión:** *¿Están los productos guardados en nuestra base de datos?*
   * **Si SÍ:** Se sacan de la base de datos, se guardan en la memoria rápida (Redis) para que la próxima vez sea más rápido, y se entregan al cliente.
   * **Si NO:** Significa que no tenemos los datos localmente, así que vamos a buscarlos al proveedor.

4. **Capa 3: Proveedor Externo (API GestoPago)**
   * **Acción:** El sistema se autentica y pide los datos a GestoPago.
   * **Decisión:** *¿La petición fue exitosa?*
   * **Si hay ERROR:** El sistema atrapa el fallo (como falta de internet, permisos denegados o caída del proveedor) y le avisa al cliente de forma segura.
   * **Si fue EXITOSA:** Recibimos el catálogo de GestoPago, lo traducimos a nuestro formato, lo guardamos permanentemente en nuestra base de datos (PostgreSQL), luego lo subimos a la memoria rápida (Redis) y, finalmente, se lo entregamos al cliente. 

> **Nota sobre las Tareas Programadas:**  
> Existe un proceso automático nocturno que se salta directamente a la **Capa 3** (Proveedor Externo) para traer los datos más nuevos de GestoPago y actualizar nuestras bases de datos mientras nadie está usando el sistema.

---

## Arquitectura y Decisiones Técnicas
* **Manejo de Errores Global:** Resiliencia lograda con [`@ControllerAdvice`](./src/main/java/com/proyecto/servicios/config/GlobalExceptionHandler.java) y [`GestoPagoErrorDecoder`](./src/main/java/com/proyecto/servicios/config/GestoPagoErrorDecoder.java) (Feign), interceptando errores (Timeouts, HTTP 401) para devolver JSON estandarizados.
* **Aspectos (AOP):** Interceptores (como [`LoggingAspect.java`](./src/main/java/com/proyecto/servicios/config/LoggingAspect.java)) para medir rendimiento (tiempo en milisegundos) e imprimir logs automáticos de forma segura, sanitizando argumentos críticos.
* **Cron Task Automática:** Clase [`GestoPagoProductScheduler.java`](./src/main/java/com/proyecto/servicios/scheduler/GestoPagoProductScheduler.java) ejecutándose diariamente a medianoche para mantener la integridad de los datos sin intervención humana, habilitada desde la configuración principal en [`App.java`](./src/main/java/com/proyecto/servicios/App.java).

---

## Contribuciones
Si deseas aportar a este repositorio:
1. Haz un **Fork** del proyecto.
2. Crea tu rama de característica (`git checkout -b feature/nueva-caracteristica`).
3. Haz un commit de tus cambios (`git commit -m 'feat: Agrega nueva característica'`).
4. Haz push a la rama (`git push origin feature/nueva-caracteristica`).
5. Abre un **Pull Request**.
