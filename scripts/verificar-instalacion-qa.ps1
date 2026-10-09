param([string]$Salida='docs/evidencias/instalacion-inicial.json')
$ErrorActionPreference='Stop'
$raizVerificacion=Split-Path -Parent $PSScriptRoot
$configVerificacion=Get-Content (Join-Path $raizVerificacion '.local-data/entrega-qa/config-local.json') -Raw | ConvertFrom-Json
$sqlVerificacion=@'
SELECT json_build_object(
 'postgresql', current_setting('server_version'),
 'database', current_database(),
 'migraciones', (SELECT json_agg(json_build_object('version',version,'success',success,'checksum',checksum) ORDER BY installed_rank) FROM flyway_schema_history WHERE version IS NOT NULL),
 'tablas_aplicacion',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'),
 'estados',(SELECT COUNT(*) FROM cat_estados),
 'municipios',(SELECT COUNT(*) FROM cat_municipios),
 'asentamientos',(SELECT COUNT(*) FROM cat_asentamientos),
 'postal_version',(SELECT json_build_object('sha256',sha256,'registros',registros) FROM cat_postal_version WHERE id=1),
 'clientes',(SELECT COUNT(*) FROM clientes),
 'usuarios',(SELECT COUNT(*) FROM usuarios),
 'cuentas',(SELECT COUNT(*) FROM cuentas)
);
'@
$jsonVerificacion=$sqlVerificacion | docker exec -i $configVerificacion.container psql -X -At -v ON_ERROR_STOP=1 -U postgres -d $configVerificacion.database
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar la base QA.' }
$resultadoVerificacion=$jsonVerificacion | ConvertFrom-Json
if ($resultadoVerificacion.migraciones.Count -ne 8 -or ($resultadoVerificacion.migraciones | Where-Object { !$_.success }) -or $resultadoVerificacion.estados -ne 32 -or $resultadoVerificacion.asentamientos -lt 100000) { throw 'No se cumplen las comprobaciones de instalación.' }
$resultadoVerificacion | Add-Member -NotePropertyName tablas_antes_del_arranque -NotePropertyValue $configVerificacion.initialTables
$resultadoVerificacion | Add-Member -NotePropertyName comprobado_en -NotePropertyValue (Get-Date).ToString('o')
$resultadoVerificacion | Add-Member -NotePropertyName postal_archivo_sha256 -NotePropertyValue (Get-FileHash -LiteralPath $configVerificacion.postalPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($resultadoVerificacion.postal_version.sha256 -ne $resultadoVerificacion.postal_archivo_sha256) { throw 'La versión postal no corresponde al ZIP local.' }
$catalogoVerificacion=Invoke-RestMethod -Uri "http://localhost:$($configVerificacion.port)/catalogos/codigos-postales/37907"
if (!($catalogoVerificacion | Where-Object {$_.id -eq 110333891 -and $_.codigoPostal -eq '37907'})) { throw 'La consulta HTTP no contiene el asentamiento QA esperado.' }
$resultadoVerificacion | Add-Member -NotePropertyName consulta_postal_http -NotePropertyValue 200
$rutaSalidaVerificacion=Join-Path $raizVerificacion $Salida
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $rutaSalidaVerificacion) | Out-Null
$resultadoVerificacion | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $rutaSalidaVerificacion -Encoding utf8
Write-Output "Instalación comprobada: 8 migraciones; $($resultadoVerificacion.asentamientos) asentamientos; 32 estados. Resultado: $Salida"
