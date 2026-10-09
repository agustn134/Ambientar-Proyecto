# Catálogo postal incluido

`CPdescargatxt.zip` es el catálogo nacional TXT descargado de [Correos de México / SEPOMEX](https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx) el 8 de octubre de 2026. Se incorpora al entregable académico para reproducir la instalación sin una descarga adicional. El ZIP se conserva sin modificaciones, incluido el aviso original dentro de `CPdescarga.txt`; su contenido y autoría corresponden a Correos de México.

- SHA-256: `7c4c61f673998ee1f261df87aa6e49353541d643f53acfae4c85026c87b2696d`.
- Contenido verificado: 32 entidades, 2,478 municipios y 159,340 asentamientos.
- La orden `crear-base-datos.ps1` o `crear-base-datos.sh` crea el esquema y carga este archivo con el importador Java del proyecto. No requiere Internet si se utiliza el paquete que incluye el JAR.
- La importación es transaccional. Registra el hash y la cantidad en `cat_postal_version`; una repetición con el mismo archivo conserva los registros existentes.

Para utilizar una versión posterior, configurar `SEPOMEX_ARCHIVO` con su ruta. La entrega utiliza esta instantánea para que sus resultados sean reproducibles.
