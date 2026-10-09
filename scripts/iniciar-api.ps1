param([string]$Base='revision_hasani', [int]$Puerto=8081)
$ErrorActionPreference='Stop'
$raizRevision=Split-Path -Parent $PSScriptRoot
$javaRevision=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
if (!(Get-Command $javaRevision -ErrorAction SilentlyContinue)) { throw 'Configura JAVA_HOME con JDK 21.' }
if (!$env:SEPOMEX_ARCHIVO) { $env:SEPOMEX_ARCHIVO=Join-Path $raizRevision 'datos/CPdescargatxt.zip' }
if (!(Test-Path -LiteralPath $env:SEPOMEX_ARCHIVO)) { throw 'Falta datos/CPdescargatxt.zip. Extrae el paquete completo o configura SEPOMEX_ARCHIVO.' }
if (!$env:DB_PASSWORD) { $env:DB_PASSWORD=$env:PGPASSWORD }
if (!$env:DB_PASSWORD) { throw 'Configura PGPASSWORD o DB_PASSWORD con la contraseña PostgreSQL.' }
if (!$env:JWT_SECRET) {
    $bytesRevision=New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytesRevision)
    $env:JWT_SECRET=[Convert]::ToBase64String($bytesRevision)
}
$hostRevision=if($env:PGHOST){$env:PGHOST}else{'localhost'}
$puertoDBRevision=if($env:PGPORT){$env:PGPORT}else{'5432'}
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://${hostRevision}:${puertoDBRevision}/$Base"
$env:SPRING_DATASOURCE_USERNAME=if($env:PGUSER){$env:PGUSER}else{'postgres'}
Push-Location $raizRevision
try {
    if (!(Test-Path -LiteralPath 'build/libs/prueba-1.0.jar')) {
        & .\gradlew.bat bootJar
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación.' }
    }
    Write-Output "API de revisión: http://localhost:$Puerto/swagger-ui.html. Ctrl+C para detener."
    & $javaRevision '-jar' 'build/libs/prueba-1.0.jar' '--spring.profiles.active=qa' "--server.port=$Puerto"
    if ($LASTEXITCODE -ne 0) { throw 'La API no pudo iniciar; revisa el error mostrado.' }
} finally { Pop-Location }
