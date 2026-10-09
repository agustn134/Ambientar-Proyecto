# Despliegue en Render

El servicio se construye desde `main` con el `Dockerfile` del repositorio. La imagen utiliza Java 21, ejecuta el JAR con el perfil `render` e incluye el ZIP postal. PostgreSQL y Render Key Value se crean por separado, en la misma región Oregon que la API.

La [comprobación local de la imagen](evidencias/render-docker-local.json) verificó compilación Linux, arranque con límite de 512 MiB, ocho migraciones, carga postal, registro, login, perfil y saldo. El despliegue remoto y la integración GestoPago con sus credenciales siguen pendientes; esta comprobación no reproduce el límite de CPU de Render Free.

## Servicios

| Servicio | Nombre sugerido | Plan para demostración |
|---|---|---|
| PostgreSQL 17 | `onboarding-clientes-db` | Free |
| Key Value, compatible con Redis | `onboarding-clientes-cache` | Free |
| Web Service, Docker | `onboarding-clientes-api` | Free |

El perfil `render` conserva la integración real con GestoPago y requiere sus credenciales. No utiliza el perfil QA ni cambia las reglas de negocio. Los planes gratuitos son para la demostración; PostgreSQL gratuito caduca a los 30 días y el servicio web se suspende por inactividad.

## Formulario del servicio web

| Campo | Valor |
|---|---|
| Repository | `https://github.com/agustn134/Ambientar-Proyecto` |
| Name | `onboarding-clientes-api` |
| Project / Environment | `onboarding-clientes` / `Demostracion` |
| Language | Docker |
| Branch | `main` |
| Region | Oregon |
| Root Directory | Vacío |
| Dockerfile Path | `./Dockerfile` |
| Docker Command | Vacío; la imagen define su entrada |
| Compute | Free |
| Health Check Path | `/catalogos/sexos` |
| Pre-Deploy Command | Vacío |

Con Docker, Render utiliza las instrucciones del Dockerfile para construir e iniciar el servicio; no se requieren comandos de Node ni `npm`.

## Variables del servicio web

Introducir los valores en **Environment Variables**, sin guardarlos en Git. Los datos PostgreSQL se toman de **Info → Connections**; para Key Value, utilizar su URL interna.

| Variable | Valor |
|---|---|
| `DB_HOST` | Hostname interno de PostgreSQL; sin esquema `postgresql://` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `onboarding_clientes` |
| `DB_USER` | Username asignado por Render |
| `DB_PASSWORD` | Password de PostgreSQL |
| `REDIS_URL` | Internal URL de Key Value, con esquema `redis://` o `rediss://` |
| `JWT_SECRET` | Clave aleatoria Base64 de al menos 32 bytes |
| `GESTOPAGO_PASSWORD` | Contraseña vigente del distribuidor GestoPago |

El perfil `render`, el puerto y la ruta postal ya están definidos en la imagen/configuración. Render proporciona `PORT` y `RENDER_EXTERNAL_URL`; no hay que asignarlos manualmente.

Generar la clave JWT desde Git Bash o Linux:

```bash
openssl rand -base64 32
```

Copiar el resultado directamente al campo `JWT_SECRET` de Render y mantener el mismo valor entre despliegues. El botón Generate sólo es adecuado si su salida cumple el formato Base64 requerido por el proyecto.

## Primer arranque

Render ya crea la base PostgreSQL. No ejecutar `crear-base-datos.sql` sobre el servicio administrado: Flyway aplica automáticamente V1–V8 en su base nueva. Después, el importador carga el ZIP incluido: 32 entidades, 2,478 municipios y 159,340 asentamientos. En reinicios con el mismo archivo, reconoce el hash guardado y conserva el catálogo.

La primera carga puede tardar más en una instancia Free. Consultar Logs y esperar los mensajes de migraciones, catálogo listo y `Started App`. El health check consulta un catálogo público y no requiere JWT.

Una vez desplegado:

1. Abrir `https://<servicio>.onrender.com/swagger-ui.html`.
2. Consultar `/catalogos/sexos` y `/catalogos/codigos-postales/37907`.
3. Registrar un cliente sintético con datos únicos; comprobar HTTP 201, cuenta y usuario.
4. Iniciar sesión y consultar el perfil y saldo utilizando el JWT obtenido.
5. Comprobar GestoPago con Redis disponible y sus credenciales vigentes; registrar el resultado real.

Los resultados locales previos no acreditan por sí solos un despliegue remoto. La URL pública y su comprobación se añadirán cuando Render termine el despliegue.

Referencias: [Docker en Render](https://render.com/docs/docker), [servicios web y puerto](https://render.com/docs/web-services), [Key Value](https://render.com/docs/key-value) y [planes gratuitos](https://render.com/docs/free).
