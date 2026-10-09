param([int]$Usuarios = 25, [int]$Port = 8081)
$ErrorActionPreference='Stop'
$raizCarga=Split-Path -Parent $PSScriptRoot
$configCarga=Get-Content (Join-Path $raizCarga '.local-data/entrega-qa/config-local.json') -Raw | ConvertFrom-Json
if ($configCarga.port -ne $Port -or $configCarga.database -notlike 'entrega_qa_*') { throw 'Sólo se preparan datos en el entorno QA creado por el script.' }
$filasCarga=@()
for($iCarga=1;$iCarga -le $Usuarios;$iCarga++) {
    $numeroCarga=$iCarga.ToString('000')
    $sufijoCarga=('{0:X2}' -f $iCarga)
    $datosCarga=@{ nombre='Cliente'; apellidoPaterno='Carga'; apellidoMaterno='Prueba'; fechaNacimiento='1990-01-01'; curp=('CAPP900101HGTBCD'+$sufijoCarga.Substring(0,1)+($iCarga%10)); rfc=('CAPP900101'+$numeroCarga); correoElectronico="carga.$numeroCarga@example.com"; password='PruebaCarga2026!'; telefonoMovil='4680000000'; sexoId=1; nacionalidadId=1; estadoCivilId=7; domicilio=@{calle='Calle QA';numeroExterior=$numeroCarga;codigoPostal='37907';asentamientoId=110333891;paisId=1}; informacionLaboral=@{ocupacion='Pruebas';empresa='QA';ingresoMensual=10000} }
    # CURP: el último par debe ser único y conservar carácter alfanumérico + dígito.
    $datosCarga.curp='CAPP900101HGTBCD'+[char](65+[int][math]::Floor(($iCarga-1)/10))+(($iCarga-1)%10)
    $respuestaCarga=Invoke-RestMethod -Uri "http://localhost:$Port/clientes" -Method Post -ContentType 'application/json' -Body ($datosCarga | ConvertTo-Json -Depth 5)
    if (!$respuestaCarga.usuario.usuarioId -or !$respuestaCarga.cuenta.numeroCuenta) { throw 'Registro QA incompleto.' }
    $filasCarga+= [pscustomobject]@{correo=$datosCarga.correoElectronico;password=$datosCarga.password;numeroCuenta=$respuestaCarga.cuenta.numeroCuenta}
}
$rutaCsvCarga=Join-Path $raizCarga '.local-data/entrega-qa/usuarios.csv'
$filasCarga | ConvertTo-Csv -NoTypeInformation | Set-Content -LiteralPath $rutaCsvCarga -Encoding utf8NoBOM
Write-Output "Preparados $Usuarios usuarios CLIENTE QA. CSV local: $rutaCsvCarga"
