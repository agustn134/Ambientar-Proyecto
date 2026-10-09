param([switch]$Detener)
$ErrorActionPreference='Stop'
$raizReinicio=Split-Path -Parent $PSScriptRoot
$rutaReinicio=Join-Path $raizReinicio '.local-data/entrega-qa'
$configReinicio=Get-Content (Join-Path $rutaReinicio 'config-local.json') -Raw | ConvertFrom-Json
$procesoReinicio=Get-CimInstance Win32_Process -Filter "ProcessId = $($configReinicio.pid)"
if ($procesoReinicio) {
    if ($procesoReinicio.CommandLine -notlike '*--spring.profiles.active=qa*' -or $procesoReinicio.CommandLine -notlike "*--server.port=$($configReinicio.port)*" -or $procesoReinicio.CommandLine -notlike "*$raizReinicio*build*libs*") { throw 'El PID no corresponde a la API QA del proyecto. No se detiene.' }
    Stop-Process -Id $configReinicio.pid
    Wait-Process -Id $configReinicio.pid -Timeout 20 -ErrorAction SilentlyContinue
}
if ($Detener) { Write-Output 'API QA detenida. La base se conserva.'; exit }
if (Get-NetTCPConnection -LocalPort $configReinicio.port -State Listen -ErrorAction SilentlyContinue) { throw 'El puerto está ocupado; no se inicia otro proceso.' }
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:$(if($configReinicio.dbPort){$configReinicio.dbPort}else{5432})/$($configReinicio.database)"
$env:SPRING_DATASOURCE_USERNAME=$configReinicio.user
$env:DB_PASSWORD=$configReinicio.password
$env:JWT_SECRET=$configReinicio.jwtSecret
$env:SEPOMEX_ARCHIVO=$configReinicio.postalPath
$jarReinicio=Get-ChildItem (Join-Path $raizReinicio 'build/libs') -Filter '*.jar' | Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
$procesoNuevo=Start-Process -FilePath $configReinicio.java -ArgumentList @('-jar', ('"'+$jarReinicio.FullName+'"'),'--spring.profiles.active=qa',"--server.port=$($configReinicio.port)") -WorkingDirectory $raizReinicio -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $rutaReinicio 'reinicio.log') -RedirectStandardError (Join-Path $rutaReinicio 'reinicio-error.log')
$configReinicio.pid=$procesoNuevo.Id
$configReinicio | ConvertTo-Json | Set-Content (Join-Path $rutaReinicio 'config-local.json') -Encoding utf8
Write-Output "API QA reiniciada en $($configReinicio.port); PID $($procesoNuevo.Id)."
