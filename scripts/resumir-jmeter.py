"""Resume JTL CSV. Percentiles: rango más próximo. Sólo biblioteca estándar."""
import argparse, csv, json, math, statistics
from collections import defaultdict
from pathlib import Path

def resumen(rows):
    times=sorted(float(r['elapsed']) for r in rows)
    percentile=lambda p: times[max(0,math.ceil(len(times)*p)-1)]
    start=min(int(r['timeStamp']) for r in rows)
    end=max(int(r['timeStamp'])+int(r['elapsed']) for r in rows)
    seconds=(end-start)/1000
    errors=sum(r['success'].lower()!='true' for r in rows)
    return dict(muestras=len(rows),errores=errors,error_pct=round(errors/len(rows)*100,3),media_ms=round(statistics.mean(times),2),p50_ms=percentile(.50),p95_ms=percentile(.95),p99_ms=percentile(.99),max_ms=max(times),duracion_muestras_s=round(seconds,3),solicitudes_s=round(len(rows)/seconds,3),inicio_epoch_ms=start,fin_epoch_ms=end)

parser=argparse.ArgumentParser();parser.add_argument('directorio',type=Path);args=parser.parse_args()
report=[]
for file in sorted(args.directorio.glob('usuarios-*.jtl'),key=lambda p:int(p.stem.split('-')[1])):
    with file.open(encoding='utf-8-sig',newline='') as stream:rows=list(csv.DictReader(stream))
    if not rows:raise SystemExit('JTL sin muestras: '+str(file))
    labels=defaultdict(list)
    for r in rows:labels[r['label']].append(r)
    report.append(dict(usuarios=int(file.stem.split('-')[1]),archivo=file.name,**resumen(rows),endpoints={k:resumen(v) for k,v in labels.items()}))
if not report:raise SystemExit('No se encontraron JTL.')
(args.directorio/'resumen.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
columns=['usuarios','muestras','errores','error_pct','media_ms','p50_ms','p95_ms','p99_ms','max_ms','solicitudes_s']
with (args.directorio/'resumen.csv').open('w',encoding='utf-8',newline='') as stream:
    writer=csv.DictWriter(stream,fieldnames=columns);writer.writeheader();writer.writerows({k:r[k] for k in columns} for r in report)
print(json.dumps([{k:r[k] for k in columns} for r in report],ensure_ascii=False,indent=2))
