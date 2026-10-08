-- Protege también registros simultáneos que sólo difieren por mayúsculas.
-- Si existen correos duplicados históricos, resolverlos antes de aplicar V4.
CREATE UNIQUE INDEX uq_clientes_correo_ignore_case ON clientes (LOWER(correo_electronico));
