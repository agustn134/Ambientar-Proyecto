$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $PSScriptRoot
$directorioPostal = Join-Path $raizProyecto '.local-data'
New-Item -ItemType Directory -Force -Path $directorioPostal | Out-Null
$urlPostal = 'https://www.correosdemexico.gob.mx/SSLServicios/ConsultaCP/CodigoPostal_Exportar.aspx'
$paginaPostal = Invoke-WebRequest -Uri $urlPostal -UseBasicParsing -SessionVariable sesionPostal
$formularioPostal = @{}
foreach ($entradaPostal in [regex]::Matches($paginaPostal.Content, '<input[^>]*type="hidden"[^>]*>')) {
    $nombrePostal = [regex]::Match($entradaPostal.Value, 'name="([^"]+)"').Groups[1].Value
    $valorPostal = [regex]::Match($entradaPostal.Value, 'value="([^"]*)"').Groups[1].Value
    $formularioPostal[$nombrePostal] = [System.Net.WebUtility]::HtmlDecode($valorPostal)
}
$formularioPostal['cboEdo'] = '00'
$formularioPostal['rblTipo'] = 'txt'
$formularioPostal['btnDescarga.x'] = '10'
$formularioPostal['btnDescarga.y'] = '10'
$rutaTemporalPostal = Join-Path $directorioPostal 'descarga-sepomex.tmp'
$rutaFinalPostal = Join-Path $directorioPostal 'CPdescargatxt.zip'
Invoke-WebRequest -Uri $urlPostal -Method Post -Body $formularioPostal -UseBasicParsing -WebSession $sesionPostal -OutFile $rutaTemporalPostal
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPostal = [System.IO.Compression.ZipFile]::OpenRead($rutaTemporalPostal)
try {
    if (!($zipPostal.Entries | Where-Object { $_.Name -eq 'CPdescarga.txt' })) {
        throw 'La descarga no contiene el TXT nacional esperado; se conserva el catálogo anterior.'
    }
} finally { $zipPostal.Dispose() }
Move-Item -LiteralPath $rutaTemporalPostal -Destination $rutaFinalPostal -Force
Write-Output "Catálogo oficial descargado: $rutaFinalPostal"
Write-Output 'Archivo para uso local; .local-data queda fuera de Git. Consultar las condiciones de SEPOMEX.'
