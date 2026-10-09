param(
    [Parameter(Mandatory=$true)]
    [ValidateRange(1,9223372036854775807)]
    [long]$UsuarioId
)
$ErrorActionPreference = 'Stop'
$archivoRol = Join-Path $PSScriptRoot 'asignar-ejecutivo.sql'
if (!(Test-Path -LiteralPath $archivoRol)) { throw 'No se encuentra el procedimiento SQL junto al script.' }
Get-Content -LiteralPath $archivoRol -Raw |
    docker exec -i postgres-dev psql -X -v ON_ERROR_STOP=1 -v "usuario_id=$UsuarioId" -U postgres -d DBGestoPago
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL no pudo completar la asignación; revisa el mensaje anterior.' }
