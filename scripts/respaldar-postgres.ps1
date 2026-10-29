param(
    [Parameter(Mandatory=$true)][string]$SourceHost,
    [Parameter(Mandatory=$true)][string]$SourceDatabase,
    [Parameter(Mandatory=$true)][string]$SourceUser,
    [int]$SourcePort=5432,
    [ValidateSet('require','verify-full','disable')][string]$SslMode='require',
    [Security.SecureString]$Password,
    [string]$OutputDirectory='.local-data/respaldos',
    [string]$Image='postgres:17-alpine'
)
$ErrorActionPreference='Stop'
if(-not $Password){$Password=Read-Host 'Contraseña PostgreSQL (no se muestra ni se guarda en Git)' -AsSecureString}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$backupFile=Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path ('postgres-'+(Get-Date -Format yyyyMMddHHmmss)+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)+'.dump')
$envNames=@('PGHOST','PGPORT','PGDATABASE','PGUSER','PGPASSWORD','PGSSLMODE','PGCONNECT_TIMEOUT')
$previousEnv=@{}
foreach($envName in $envNames){$previousEnv[$envName]=[Environment]::GetEnvironmentVariable($envName,'Process')}
$backupContainer=$null
try {
    $env:PGHOST=$SourceHost; $env:PGPORT=[string]$SourcePort
    $env:PGDATABASE=$SourceDatabase; $env:PGUSER=$SourceUser; $env:PGSSLMODE=$SslMode
    $env:PGCONNECT_TIMEOUT='20'
    $env:PGPASSWORD=[System.Net.NetworkCredential]::new('', $Password).Password
    # Docker recibe los valores por entorno, nunca mediante argumentos con contraseñas.
    $backupContainer=docker create --env PGHOST --env PGPORT --env PGDATABASE --env PGUSER --env PGPASSWORD --env PGSSLMODE --env PGCONNECT_TIMEOUT $Image sh -c 'pg_dump --no-password --format=custom --file=/tmp/respaldo.dump && pg_restore --list /tmp/respaldo.dump >/dev/null'
    if($LASTEXITCODE -ne 0 -or $backupContainer -notmatch '^[a-f0-9]{64}$'){throw 'No se pudo crear el contenedor temporal de respaldo. Revisa la imagen PostgreSQL local.'}
    docker start --attach $backupContainer
    if($LASTEXITCODE -ne 0){throw 'Falló pg_dump; no hay un respaldo válido. Revisa conectividad y permisos de lectura.'}
    $dumpExitCode=docker inspect --format '{{.State.ExitCode}}' $backupContainer
    if($LASTEXITCODE -ne 0 -or $dumpExitCode -ne '0'){throw 'El respaldo o su catálogo fallaron; no se copiará el archivo.'}
    # pg_restore --list comprueba el catálogo del archivo; no restaura ni modifica el origen.
    docker cp ($backupContainer+':/tmp/respaldo.dump') $backupFile | Out-Null
    if($LASTEXITCODE -ne 0 -or -not(Test-Path -LiteralPath $backupFile)){throw 'No se pudo copiar el respaldo al equipo.'}
    if((Get-Item -LiteralPath $backupFile).Length -eq 0){throw 'El respaldo está vacío.'}
    $backupHash=(Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash
    $backupHash | Set-Content -LiteralPath ($backupFile+'.sha256')
    Write-Output ('Respaldo privado creado: '+$backupFile)
    Write-Output ('SHA256: '+$backupHash)
    Write-Output 'Pendiente: restaurar en una base aislada, auditar y validar. Crear el archivo no acredita una restauración exitosa.'
} finally {
    if($backupContainer -match '^[a-f0-9]{64}$'){docker rm $backupContainer | Out-Null}
    foreach($envName in $envNames){[Environment]::SetEnvironmentVariable($envName,$previousEnv[$envName],'Process')}
}
