param(
    [string]$PostgresContainer = 'postgres-dev',
    [int]$DbPort = 5432,
    [int]$Port = 8081,
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21.0.12'
)
$ErrorActionPreference = 'Stop'
$raizQA = Split-Path -Parent $PSScriptRoot
$rutaQA = Join-Path $raizQA '.local-data/entrega-qa'
New-Item -ItemType Directory -Force -Path $rutaQA | Out-Null
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "El puerto $Port ya está ocupado. No se detiene ese proceso." }
$javaQA = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javaQA)) { throw 'Indica -JavaHome con un JDK instalado.' }
$jarQA = Get-ChildItem (Join-Path $raizQA 'build/libs') -Filter '*.jar' | Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
if (!$jarQA) { throw 'Primero ejecuta .\gradlew.bat bootJar.' }
if (!$env:SEPOMEX_ARCHIVO) { $env:SEPOMEX_ARCHIVO = Join-Path $raizQA '.local-data/CPdescargatxt.zip' }
if (!(Test-Path -LiteralPath $env:SEPOMEX_ARCHIVO)) { throw 'Descarga SEPOMEX y configura SEPOMEX_ARCHIVO antes de preparar QA.' }
$idQA = (Get-Date -Format 'yyyyMMddHHmmss') + ([guid]::NewGuid().ToString('N').Substring(0,6))
$baseQA = 'entrega_qa_' + $idQA
$usuarioQA = 'qa_' + $idQA
$claveQA = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$jwtQA = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
# Los identificadores se generan aquí, sin interpolar nombres del usuario.
"CREATE ROLE $usuarioQA LOGIN PASSWORD '$claveQA'; CREATE DATABASE $baseQA OWNER $usuarioQA;" |
    docker exec -i $PostgresContainer psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base QA.' }
$tablasInicialesQA = docker exec $PostgresContainer psql -X -At -U postgres -d $baseQA -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
if ($LASTEXITCODE -ne 0 -or [int]$tablasInicialesQA -ne 0) { throw 'La base no está vacía. No se inicia la aplicación.' }
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$DbPort/$baseQA"
$env:SPRING_DATASOURCE_USERNAME = $usuarioQA
$env:DB_PASSWORD = $claveQA
$env:JWT_SECRET = $jwtQA
$configQA = @{ database=$baseQA; user=$usuarioQA; password=$claveQA; jwtSecret=$jwtQA; container=$PostgresContainer; dbPort=$DbPort; port=$Port; initialTables=[int]$tablasInicialesQA; createdAt=(Get-Date).ToString('o'); postalPath=$env:SEPOMEX_ARCHIVO; java=$javaQA }
$configQA | ConvertTo-Json | Set-Content (Join-Path $rutaQA 'config-local.json') -Encoding utf8
$procesoQA = Start-Process -FilePath $javaQA -ArgumentList @('-jar', ('"'+$jarQA.FullName+'"'), '--spring.profiles.active=qa', "--server.port=$Port") -WorkingDirectory $raizQA -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $rutaQA 'arranque.log') -RedirectStandardError (Join-Path $rutaQA 'arranque-error.log')
$configQA.pid=$procesoQA.Id
$configQA | ConvertTo-Json | Set-Content (Join-Path $rutaQA 'config-local.json') -Encoding utf8
Write-Output "Base QA nueva: $baseQA; tablas iniciales: $tablasInicialesQA; API: http://localhost:$Port; PID: $($procesoQA.Id)"
Write-Output 'La configuración privada está en .local-data/entrega-qa, ignorada por Git.'
