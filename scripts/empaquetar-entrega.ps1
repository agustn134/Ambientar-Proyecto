param([switch]$IncluirJar)
$ErrorActionPreference='Stop'
$raizEntrega=Split-Path -Parent $PSScriptRoot
$fechaEntrega=Get-Date -Format 'yyyyMMdd-HHmmss'
$directorioEntrega=Join-Path $raizEntrega "outputs/entrega-$fechaEntrega"
$contenidoEntrega=Join-Path $directorioEntrega 'Ambientar-Proyecto'
New-Item -ItemType Directory -Force -Path $contenidoEntrega | Out-Null
foreach($carpetaEntrega in @('src','tests','scripts','docs','gradle','datos')) {
    Copy-Item -LiteralPath (Join-Path $raizEntrega $carpetaEntrega) -Destination $contenidoEntrega -Recurse
}
foreach($archivoEntrega in @('README.md','build.gradle','settings.gradle','gradlew','gradlew.bat','.gitignore','.gitattributes','Dockerfile','.dockerignore')) {
    Copy-Item -LiteralPath (Join-Path $raizEntrega $archivoEntrega) -Destination $contenidoEntrega
}
if($IncluirJar) {
    New-Item -ItemType Directory -Force -Path (Join-Path $contenidoEntrega 'build/libs') | Out-Null
    Get-ChildItem (Join-Path $raizEntrega 'build/libs') -Filter '*.jar' | Where-Object { $_.Name -notlike '*-plain.jar' } | Copy-Item -Destination (Join-Path $contenidoEntrega 'build/libs')
}
# Sólo se copian carpetas de entrega enumeradas. No se copia .local-data,
# .git, .idea, .gradle, configuraciones privadas ni dumps.
$archivosManifest=Get-ChildItem -LiteralPath $contenidoEntrega -File -Recurse
$manifestEntrega=foreach($archivoManifest in $archivosManifest) {
    [pscustomobject]@{ruta=[IO.Path]::GetRelativePath($contenidoEntrega,$archivoManifest.FullName).Replace('\','/');bytes=$archivoManifest.Length;sha256=(Get-FileHash -LiteralPath $archivoManifest.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$manifestEntrega | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $contenidoEntrega 'MANIFEST-SHA256.json') -Encoding utf8
$zipEntrega=Join-Path $directorioEntrega 'Ambientar-Proyecto-entrega.zip'
Compress-Archive -LiteralPath $contenidoEntrega -DestinationPath $zipEntrega -CompressionLevel Optimal
Write-Output "Paquete final: $zipEntrega"
Write-Output "SHA-256: $((Get-FileHash -LiteralPath $zipEntrega -Algorithm SHA256).Hash.ToLowerInvariant())"
