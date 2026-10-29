# Validación local de GestoPago

Esta rama permite probar el contrato en Swagger sin realizar recargas ni tocar el saldo.

- Iniciar sesión con POST /auth/login y pegar datos.accessToken en Authorize.
- Abrir POST /api/gestopago/pagos/validar.
- Enviar servicio 78, producto 219, monto 40 y un teléfono de diez dígitos.
- Omitir referencia: este primer caso es la recarga Oui Movil de $40.
- Un 200 confirma solo las reglas locales; pagoEnviado siempre es false.
- Monto distinto de 40, teléfono inválido o servicio incorrecto devuelven 422.
- Sin una sesión vigente se devuelve 401 antes de revisar los datos.

El catálogo local debe conservar producto activo, servicio 78, categoría 13, tipoFront 1 y precio 40.
Los demás productos permanecen bloqueados hasta definir sus reglas.

El envío, token del proveedor, saldo, duplicados y conciliación se completan en las siguientes ramas.
El plan y las evidencias se mantienen en las issues y en el PR de GitHub.
