"""Paquete de fuentes y evidencias; equivalente portable del script PowerShell."""
import argparse,hashlib,json,zipfile,datetime
from pathlib import Path
root=Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser();parser.add_argument('--incluir-jar',action='store_true');args=parser.parse_args()
directory=root/'outputs'/('entrega-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
directory.mkdir(parents=True,exist_ok=False)
files=[]
for folder in ['src','tests','scripts','docs','gradle','datos']:
    files.extend(p for p in (root/folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
files.extend(root/p for p in ['README.md','build.gradle','settings.gradle','gradlew','gradlew.bat','.gitignore'])
if (root/'.gitattributes').exists():files.append(root/'.gitattributes')
files.extend(root/p for p in ['Dockerfile','.dockerignore'] if (root/p).exists())
if args.incluir_jar:
    binary=root/'build/libs/prueba-1.0.jar'
    if not binary.exists():raise SystemExit('Compila bootJar antes de incluir el binario.')
    files.append(binary)
manifest=[]
archive=directory/'Ambientar-Proyecto-entrega.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as zip_file:
    for file in sorted(files):
        relative=file.relative_to(root).as_posix();data=file.read_bytes()
        zip_file.writestr('Ambientar-Proyecto/'+relative,data)
        manifest.append(dict(ruta=relative,bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
    zip_file.writestr('Ambientar-Proyecto/MANIFEST-SHA256.json',json.dumps(manifest,ensure_ascii=False,indent=2))
with zipfile.ZipFile(archive) as check:
    if check.testzip():raise SystemExit('Falló la comprobación del ZIP.')
print('Paquete:',archive)
print('SHA-256:',hashlib.sha256(archive.read_bytes()).hexdigest())
