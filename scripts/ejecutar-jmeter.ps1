param(
    [string]$JMeterHome = (Join-Path $HOME 'Downloads/apache-jmeter-5.6.3'),
    [string]$JavaHome = $env:JAVA_HOME,
    [int[]]$Niveles = @(1,10,25),
    [int]$Duracion = 60,
    [int]$Port = 8081
)
$ErrorActionPreference='Stop'
$raizCarga=Split-Path -Parent $PSScriptRoot
$jmeterCarga=Join-Path $JMeterHome 'bin/jmeter.bat'
$csvCarga=Join-Path $raizCarga '.local-data/entrega-qa/usuarios.csv'
if (!(Test-Path -LiteralPath $jmeterCarga) -or !(Test-Path -LiteralPath $csvCarga)) { throw 'Se requieren JMeter binario y los datos QA preparados.' }
if ($JavaHome) { $env:JAVA_HOME=$JavaHome }
$fechaCarga=Get-Date -Format 'yyyyMMdd-HHmmss'
$salidaCarga=Join-Path $raizCarga "tests/jmeter/resultados/$fechaCarga"
New-Item -ItemType Directory -Force -Path $salidaCarga | Out-Null
foreach($nivelCarga in $Niveles) {
    if ($nivelCarga -lt 1 -or $nivelCarga -gt 25 -or $Duracion -gt 240 -or $Duracion -lt 1) { throw 'Este plan admite 1–25 usuarios y 1–240 segundos para conservar JWT vigente.' }
    $prefijoCarga=Join-Path $salidaCarga "usuarios-$nivelCarga"
    & $jmeterCarga '-n' '-t' (Join-Path $raizCarga 'tests/jmeter/clientes-local.jmx') '-l' ($prefijoCarga+'.jtl') '-j' ($prefijoCarga+'.log') '-e' '-o' ($prefijoCarga+'-html') "-Jusuarios=$nivelCarga" "-Jduracion=$Duracion" '-Jrampa=10' '-Jhost=localhost' "-Jpuerto=$Port" "-Jdatos=$csvCarga" '-Jjmeter.save.saveservice.response_data=false' '-Jjmeter.save.saveservice.samplerData=false' '-Jjmeter.save.saveservice.requestHeaders=false' '-Jjmeter.save.saveservice.responseHeaders=false'
    if ($LASTEXITCODE -ne 0) { throw "JMeter terminó con error en el nivel $nivelCarga." }
}
Write-Output "Resultados JMeter: $salidaCarga"
