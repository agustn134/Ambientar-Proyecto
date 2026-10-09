"""Usuarios sintéticos para la API de revisión local; compatible con Windows y Linux."""
import argparse, csv, json, urllib.request, urllib.error, urllib.parse
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--url',default='http://localhost:8081')
parser.add_argument('--usuarios',type=int,default=25)
args=parser.parse_args()
target=urllib.parse.urlparse(args.url)
if target.scheme!='http' or target.hostname not in ('localhost','127.0.0.1') or target.port not in (8081,8082):
    raise SystemExit('Sólo se admite la API de revisión local en 8081/8082.')
if not 1<=args.usuarios<=25:raise SystemExit('Usuarios permitidos: 1–25.')
def request(path,data=None,token=None):
    headers={'Accept':'application/json','Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    req=urllib.request.Request(args.url.rstrip('/')+path,data=json.dumps(data).encode() if data is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=30) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as error:
        return error.code,json.load(error)

rows=[]
for number in range(1,args.usuarios+1):
    correo=f'carga.{number:03d}@example.com'
    data=dict(nombre='Cliente',apellidoPaterno='Carga',apellidoMaterno='Prueba',fechaNacimiento='1990-01-01',curp='CAPP900101HGTBCD'+chr(65+(number-1)//10)+str((number-1)%10),rfc=f'CAPP900101{number:03d}',correoElectronico=correo,password='PruebaCarga2026!',telefonoMovil='4680000000',sexoId=1,nacionalidadId=1,estadoCivilId=7,domicilio=dict(calle='Calle QA',numeroExterior=f'{number:03d}',codigoPostal='37907',asentamientoId=110333891,paisId=1),informacionLaboral=dict(ocupacion='Pruebas',empresa='QA',ingresoMensual=10000))
    status,body=request('/clientes',data)
    if status==201:cuenta=body['cuenta']['numeroCuenta']
    elif status==409:
        status,login=request('/auth/login',dict(correo=correo,password=data['password']))
        if status!=200:raise SystemExit('El perfil ya existe y no corresponde a las credenciales QA. No se modifica.')
        status,body=request('/clientes/me',token=login['accessToken'])
        if status!=200 or body['cliente']['correo']!=correo:raise SystemExit('No se pudo recuperar el perfil QA propio.')
        cuenta=body['cuentas'][0]['numeroCuenta']
    else:raise SystemExit(f'El alta QA {number} respondió HTTP {status}; se detiene la preparación.')
    rows.append(dict(correo=correo,password=data['password'],numeroCuenta=cuenta))
out=Path(__file__).resolve().parent.parent/'.local-data/entrega-qa/usuarios.csv'
out.parent.mkdir(parents=True,exist_ok=True)
with out.open('w',encoding='utf-8',newline='') as file:
    writer=csv.DictWriter(file,fieldnames=['correo','password','numeroCuenta']);writer.writeheader();writer.writerows(rows)
print(f'Preparados {len(rows)} perfiles QA. CSV local: {out}')
