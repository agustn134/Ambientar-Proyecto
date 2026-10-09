param([string]$Base='revision_hasani', [switch]$SoloPreparar)
$ErrorActionPreference='Stop'
$raizPostal=Split-Path -Parent $PSScriptRoot
$zipPostal=if($env:SEPOMEX_ARCHIVO){$env:SEPOMEX_ARCHIVO}else{Join-Path $raizPostal 'datos/CPdescargatxt.zip'}
if (!(Test-Path -LiteralPath $zipPostal -PathType Leaf)) { throw 'No se encontró datos/CPdescargatxt.zip. Extrae el paquete completo o configura SEPOMEX_ARCHIVO.' }
$javaPostal=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
if (!(Get-Command $javaPostal -ErrorAction SilentlyContinue)) { throw 'Se requiere JDK 21 en PATH o JAVA_HOME.' }
$jarPostal=Join-Path $raizPostal 'build/libs/prueba-1.0.jar'
if (!(Test-Path -LiteralPath $jarPostal)) {
    Push-Location $raizPostal
    try {
        & .\gradlew.bat bootJar
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación del importador.' }
    } finally { Pop-Location }
}
if ($SoloPreparar) { return }
$hostPostal=if($env:PGHOST){$env:PGHOST}else{'localhost'}
$puertoPostal=if($env:PGPORT){$env:PGPORT}else{'5432'}
$usuarioPostal=if($env:PGUSER){$env:PGUSER}else{'postgres'}
& $javaPostal '-Dloader.main=com.proyecto.servicios.service.ImportadorPostalCli' '-cp' $jarPostal 'org.springframework.boot.loader.launch.PropertiesLauncher' "jdbc:postgresql://${hostPostal}:${puertoPostal}/$Base" $usuarioPostal $zipPostal
if ($LASTEXITCODE -ne 0) { throw "Falló la importación postal. El esquema se conserva; corrige la conexión o el ZIP y repite scripts/importar-catalogo-postal.ps1 -Base $Base." }
