param([string]$Base='revision_hasani', [string]$Contenedor='')
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'importar-catalogo-postal.ps1') -Base $Base -SoloPreparar
$sqlCreacion=Join-Path $PSScriptRoot 'crear-base-datos.sql'
if ($Contenedor) {
    $usuarioCreacion=if($env:PGUSER){$env:PGUSER}else{'postgres'}
    Get-Content -LiteralPath $sqlCreacion -Raw | docker exec -i $Contenedor psql -X -v ON_ERROR_STOP=1 -v "base_datos=$Base" -U $usuarioCreacion -d postgres
} else {
    if (!(Get-Command psql -ErrorAction SilentlyContinue)) { throw 'Añade el bin de PostgreSQL 17 al PATH o usa -Contenedor con PostgreSQL en Docker.' }
    psql -X -v ON_ERROR_STOP=1 -v "base_datos=$Base" -d postgres -f $sqlCreacion
}
if ($LASTEXITCODE -ne 0) { throw 'La creación se detuvo. Revisa el mensaje SQL; las bases existentes se conservan.' }
& (Join-Path $PSScriptRoot 'importar-catalogo-postal.ps1') -Base $Base
