# Plan y resultados de JMeter

Ejecución real: **9 de octubre de 2026, 12:08–12:12, America/Mexico_City**. Herramienta: Apache JMeter **5.6.3**, distribución binaria para Windows.

## Objetivo y alcance

Medir el comportamiento de login y consultas del backend local con usuarios sintéticos CLIENTE. Se utilizaron niveles de **1, 10 y 25 usuarios**, elegidos para una prueba local breve y configurables en el script; no se presentan como cifras exigidas por el profesor.

Cada nivel dura **60 segundos**, incluyendo una rampa de **10 segundos**. Cada hilo inicia sesión una vez y repite cuatro consultas, con una pausa de **500 ms antes de cada consulta**. El token local dura 300 segundos; el script limita cada prueba a 240 segundos y detiene un hilo si falla una muestra.

| Petición | Validación |
|---|---|
| `POST /auth/login` | 200 y JWT con tres segmentos |
| `GET /auth/me` | 200, correo de la sesión y rol CLIENTE |
| `GET /clientes/me` | 200, correo propio y cuenta propia |
| `GET /cuentas/{numeroCuenta}/saldo` | 200, cuenta de la sesión y saldo no negativo |
| `GET /catalogos/codigos-postales/37907` | 200, lista no vacía con CP solicitado |

El CSV selecciona datos QA; al iniciar sesión se conservan el correo y la cuenta de ese hilo para las comprobaciones posteriores. JWT y contraseña no se guardan en los JTL. No se realizan altas, cambios de contraseña, bloqueos ni bajas durante la medición. Los 25 perfiles se prepararon antes en una base independiente.

No se llama al catálogo GestoPago ni se mide Redis. No se ejecutó una prueba de estrés hasta saturación, una prueba prolongada ni una mezcla de roles. No hay una prueba de escritura concurrente. Estos resultados corresponden únicamente al alcance descrito.

## Entorno

- API QA: `http://localhost:8081`, perfil `qa`; base PostgreSQL distinta de `DBGestoPago`.
- PostgreSQL 17.11 en Docker local; migraciones V1–V8 y catálogo nacional cargado.
- Java 21.0.12, Spring Boot 3.4.0; pool Hikari configurado en 10 conexiones.
- Windows; CPU Intel Core i5-8250U, 4 núcleos y 8 procesadores lógicos.
- Generador JMeter, API y PostgreSQL comparten el equipo. Las cifras incluyen esa competencia por recursos.
- La suite Java había terminado antes de iniciar las mediciones. Se conservó una ejecución inicial de cinco segundos que sólo verificó apertura del plan y login; no forma parte de los resultados siguientes.

## Resultados

Las muestras exitosas requieren **HTTP y contenido correctos**, no sólo ausencia de error de conexión.

| Usuarios | Muestras | Errores | Media ms | P50 ms | P95 ms | P99 ms | Máximo ms | Solicitudes/s |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 90 | 0 (0%) | 100.41 | 59 | 154 | 2606 | 2606 | 1.604 |
| 10 | 924 | 0 (0%) | 73.86 | 50 | 170 | 418 | 1638 | 16.495 |
| 25 | 2029 | 0 (0%) | 140.24 | 77 | 417 | 1031 | 2845 | 37.718 |

Total: **3,043 muestras, cero errores**. Cada nivel contiene el login inicial y las consultas; no se mezclan las tres ejecuciones para calcular percentiles. Los percentiles de esta tabla usan rango más próximo (`ceil(n × p)`), calculado desde los JTL por `scripts/resumir-jmeter.py`. JMeter puede interpolar percentiles en su dashboard.

Solicitudes/s se calcula entre el inicio de la primera muestra y el final de la última, incluyendo las pausas intermedias. Esas ventanas son menores que los 60 segundos programados porque no incluyen preparación del primer muestreo; por eso no coinciden necesariamente con la línea `summary` de consola. El tiempo de respuesta excluye la pausa de 500 ms.

Se observó que los 25 usuarios completaron consultas con P95 agregado de 417 ms. Los picos de hasta 2,845 ms y la diferencia entre niveles incluyen login y calentamiento de JVM; no se puede atribuir su causa a PostgreSQL o al backend sin más mediciones. No se fijó un SLA previo ni se demuestra aquí capacidad máxima de producción.

## Archivos de evidencia

Directorio de la medición: [`tests/jmeter/resultados/20261009-120812`](../tests/jmeter/resultados/20261009-120812/).

- [Resumen CSV](../tests/jmeter/resultados/20261009-120812/resumen.csv) y [JSON con desglose por endpoint](../tests/jmeter/resultados/20261009-120812/resumen.json).
- [Dashboard 1 usuario](../tests/jmeter/resultados/20261009-120812/usuarios-1-html/index.html).
- [Dashboard 10 usuarios](../tests/jmeter/resultados/20261009-120812/usuarios-10-html/index.html).
- [Dashboard 25 usuarios](../tests/jmeter/resultados/20261009-120812/usuarios-25-html/index.html).
- Cada nivel conserva su `.jtl` de muestras y `.log` de JMeter.

Los HTML deben abrirse con su carpeta completa de recursos; no copiar únicamente `index.html`.

## Abrir y usar JMeter

### Linux

La distribución binaria de JMeter también funciona en Linux. Con Java en PATH y la API de revisión iniciada:

```bash
export JMETER_HOME="$HOME/Downloads/apache-jmeter-5.6.3" # Ajustar a su carpeta.
python3 scripts/preparar-datos-jmeter.py
sh scripts/ejecutar-jmeter.sh
```

Para abrir la interfaz: `sh "$JMETER_HOME/bin/jmeter"`. El plan `.jmx` es el mismo en ambos sistemas; el iniciador Linux ejecuta los tres niveles y genera JTL y dashboards. La configuración completa de base y API está en el [README](../README.md#linux). El iniciador se comprobó sintácticamente; las mediciones aquí publicadas corresponden al equipo Windows documentado.

### Windows

JMeter no requiere instalador. Abrir `bin/jmeter.bat` dentro de la carpeta donde se extrajo JMeter. Si la ventana se cierra por Java, ejecutar desde PowerShell con `JAVA_HOME` configurado para ver el mensaje:

```powershell
$jmeterRevision = Join-Path $HOME 'Downloads/apache-jmeter-5.6.3' # Ajustar si se extrajo en otra carpeta.
& (Join-Path $jmeterRevision 'bin/jmeter.bat')
```

En la ventana: **Archivo → Abrir → `tests/jmeter/clientes-local.jmx`**. Se verán el grupo de usuarios, archivo CSV, login y consultas. El plan por defecto utiliza localhost:8081 y un usuario. En **Usuarios QA (archivo local)** seleccionar la ruta absoluta de `.local-data/entrega-qa/usuarios.csv` si la interfaz se abrió desde otra carpeta. Iniciar primero la API de revisión según el README.

Para medir y generar reportes se usa consola, siguiendo la [recomendación oficial de JMeter](https://jmeter.apache.org/usermanual/get-started.html):

```powershell
# Primera preparación: base nueva y API QA arrancada.
python scripts/preparar-datos-jmeter.py
.\scripts\ejecutar-jmeter.ps1 -Niveles 1,10,25 -Duracion 60
```

Si los datos ya están preparados, no repetir las altas; reiniciar la API QA y ejecutar sólo JMeter. El script genera una carpeta distinta por ejecución y utiliza `-n -t -l -j -e -o`; no mezcla JTL anteriores ni escribe sobre reportes existentes. Los parámetros del plan se pueden cambiar con `-Jusuarios`, `-Jduracion`, `-Jrampa`, `-Jpuerto` y `-Jdatos`. El destino se restringe a localhost/127.0.0.1.

Para volver a calcular el resumen, con Python 3 instalado:

```powershell
python scripts/resumir-jmeter.py tests/jmeter/resultados/<fecha>
```

Referencia de [componentes oficiales](https://jmeter.apache.org/usermanual/component_reference.html): Thread Group, CSV Data Set, Once Only Controller, HTTP Request y aserciones JSR223.
