## Cambio

Describir el problema y el comportamiento final.

## Verificación

- [ ] Request/Response DTO, `@Valid` y errores centralizados; no se reciben ni devuelven entidades JPA.
- [ ] Los controladores no usan `@PathVariable` ni `@RequestParam`; filtros combinables por body.
- [ ] Permisos por propietario y rol comprobados; Swagger y consumidores actualizados.
- [ ] CI de este commit aprobado: seis suites, al menos 79 pruebas, cero omisiones y cuatro pruebas PostgreSQL.
- [ ] Diff revisado sin cambios IDE, credenciales, dumps ni archivos temporales.
- [ ] Evidencia de pruebas enlazada y alcance local/remoto identificado.

## Operación de base de datos, si aplica

- [ ] Respaldo del destino identificado y restaurado correctamente en una base aislada.
- [ ] Auditoría del destino y conciliación revisadas; restricciones validadas en staging.
- [ ] Responsable, ventana de despliegue y reversión registrados.

No marcar como terminado un paso pendiente. Procedimiento: `docs/operacion-validaciones-v9.md`.
