import io, re, glob, os
from collections import defaultdict
# capas.py — auditoría de capas: qué paquete importa a cuál, frente a la regla de docs/ARQUITECTURA.md.
# Se ejecuta desde la raíz del repo (verificar.ps1 lo lanza). Sale con código 1 si hay alguna dependencia hacia fuera.
import sys
base = 'src/main/java/dev/tirador/aoe2radar/'
aristas = defaultdict(set)
for f in glob.glob(base + '*/*.java'):
    pk = os.path.basename(os.path.dirname(f))
    for l in io.open(f, encoding='utf-8'):
        m = re.match(r'import (?:static )?dev\.tirador\.aoe2radar\.(\w+)\.(\w+)', l)
        if m and m.group(1) != pk:
            aristas[pk].add((m.group(1), m.group(2), os.path.basename(f)))
permitido = {'util': {'model'}, 'model': set(), 'cache': {'model', 'util'}, 'api': {'model', 'util', 'cache'},
             'sfrdata': {'model', 'util', 'api', 'cache'}, 'techtree': {'model', 'util', 'api', 'cache'},
             'service': {'api', 'sfrdata', 'techtree', 'cache', 'model', 'util'}, 'ui': {'service', 'model', 'util'}}
malas = 0
for pk in sorted(aristas):
    for dest, clase, fich in sorted(aristas[pk]):
        ok = dest in permitido.get(pk, set())
        malas += not ok
        if not ok or '-v' in sys.argv: print(('ok ' if ok else 'MAL') + f' {pk:8} -> {dest}.{clase:14} (en {fich})')
# Criterio de hecho de la fase 3 (docs/ARQUITECTURA.md): ninguna clase de ui importa java.net (la red va por service/api).
for f in glob.glob(base + 'ui/*.java'):
    for n, l in enumerate(io.open(f, encoding='utf-8'), 1):
        if re.match(r'import (?:static )?java\.net\.', l):
            malas += 1
            print(f'MAL ui       -> java.net (en {os.path.basename(f)}:{n})')
print(malas, 'dependencias fuera de la regla')
sys.exit(1 if malas else 0)
