# Datos utilizados en las pruebas de registro

## Registro válido

Se utiliza la información proporcionada por Agustín López Parra para nombre, apellidos, nacimiento (2004-09-05), CURP, RFC, correo, teléfono y domicilio. Ocupación de prueba: Tester / QA.

El JSON se encuentra en `src/test/resources/registro-cliente-valido.json` y en la petición **Registro valido** de Bruno.

Sólo se incluyen campos que existen en el contrato. El estatus fiscal, régimen fiscal y fecha de inicio de operaciones no se agregan: no forman parte del registro de clientes. El estado activo de cliente/usuario lo asigna el sistema; no depende del padrón fiscal.

Estado civil: NO_ESPECIFICADO. Empresa: Empresa de prueba. Ingreso de Agustín: 15000.50. Son valores auxiliares de prueba, no información confirmada de su situación laboral/familiar. La contraseña `PruebaCliente2026!` sigue siendo sintética, no una contraseña personal.

Con V7, estado civil se envía como `estadoCivilId=7`, nacionalidad y país como ID 1. Sexo es ID 1 para Agustín e ID 2 para Dulce. El domicilio usa `asentamientoId=110333891`, San Isidro, CP 37907. Agustín confirmó la equivalencia con su denominación anterior “Nueva San Isidro”. Consultar [catálogos de México](catalogos-mexico.md).

## Perfil para errores

Se utiliza Dulce María López Parra, nacimiento 2005-11-08 y el mismo domicilio, con ocupación Gerente de bodega y empresa Bodega Aurrera según lo proporcionado.

Estos valores son **inventados exclusivamente para las pruebas**:

- CURP: `LOPD051108MGTPRLA8`.
- RFC: `LOPD051108AB1`.
- Correo: `dulce.lopez.parra@example.com`.
- Teléfono: `4680000000`.
- Ingreso mensual: 18000.50.
- Estado civil: NO_ESPECIFICADO.

Los identificadores sólo cumplen el patrón esperado por la aplicación. No son identificadores oficiales ni acreditan su autenticidad. El perfil auxiliar está en `src/test/resources/registro-cliente-hermana.json`.

## Alteraciones deliberadas de cada caso

| Petición | Cambio deliberado | Resultado esperado |
|---|---|---|
| Registro valido | Datos de Agustín | 201 si no existe |
| Nombre invalido | Nombre `Dulce@` | 400 |
| Menor de edad | Nacimiento `2015-11-08`, distinto al real | 400 |
| CURP duplicada | Perfil de Dulce con la CURP de Agustín | 409 |
| RFC duplicado | Perfil de Dulce con el RFC de Agustín | 409 |
| Correo duplicado | Perfil de Dulce con el correo de Agustín | 409 |
| Ingreso con tipo incorrecto | Ingreso como string `"18000.50"` | 400 |
| Password invalida | Contraseña sintética sin complejidad | 400 |
| Password ausente | Se omite password | 400 |
| Password tipo incorrecto | Password numérica | 400 |

Dulce, con su fecha real de 2005, es mayor de edad: no se cambia la regla del sistema para considerarla menor. Los tests de límites conservan strings y fechas artificiales necesarios para probar esos límites.

Ejecutar primero **Registro valido** para los casos de duplicados. Repetir el alta con los mismos datos retorna 409; no se borran ni actualizan registros anteriores automáticamente.

Esta actualización sólo modifica fixtures, peticiones y documentación. Las pruebas Java usan una base temporal; no se enviaron las peticiones de Bruno ni se insertaron estos datos en PostgreSQL desde esta tarea.

Verificación tras personalizar los datos, 8 de octubre de 2026: **16 pruebas Java aprobadas** (12 de registro/BCrypt y 4 de logs), `BUILD SUCCESSFUL in 2m 17s`. Los diez bodies de Bruno fueron revisados como JSON; su ejecución manual con estos datos sigue pendiente.
