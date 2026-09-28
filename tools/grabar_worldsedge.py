# grabar_worldsedge.py — graba UNA vez respuestas reales de la API «community» de World's Edge para los tests de
# caracterización de api.WorldsEdgeApi (WorldsEdgeApiTest). Los tests NUNCA van a la red: leen estos archivos.
#
# Uso (desde la raíz del repo, con red):   python tools/grabar_worldsedge.py
# Escribe en src/test/resources/fixture/worldsedge/ (sobrescribe; las dos respuestas grandes, en .json.gz). Hace 8 llamadas GET, espaciadas 4 s (cortesía:
# la API no publica límites; la norma de la comunidad es un dígito de peticiones por segundo como mucho), con un
# User-Agent identificable. Al final comprueba el sobre (result.code) y las claves que leen las conversiones: si algo
# no cuadra, sale con código 1 y dice qué, y hay que revisar WorldsEdgeApi antes de aceptar los archivos nuevos.
# Jugadores y partida de ejemplo: los del estudio (docs/DEUDA.md, «Planned for 1.4»): 271202 (Oni.Vinchester),
# 199325 (VIT | Hera) y la partida 508450267. Si caducan (la partida sale del historial reciente), cambiar aquí.
import gzip, json, os, sys, time, urllib.request

HOST = 'https://aoe-api.worldsedgelink.com/community'   # el mismo que api.WorldsEdgeApi.HOST
UA = 'aoe2radar-fixtures (+https://github.com/jguardiola-dev/aoe2radar)'
DEST = os.path.join('src', 'test', 'resources', 'fixture', 'worldsedge')

LLAMADAS = [
    # archivo, ruta y query, claves de primer nivel que tiene que traer
    ('leaderboard_top.json', '/leaderboard/getLeaderBoard2?title=age2&leaderboard_id=3&sortBy=1&start=1&count=3',
     ['statGroups', 'leaderboardStats', 'rankTotal']),
    ('personal_stat.json', '/leaderboard/getPersonalStat?title=age2&profile_ids=%5B271202%5D',
     ['statGroups', 'leaderboardStats']),
    ('recent_history.json.gz', '/leaderboard/getRecentMatchHistory?title=age2&profile_ids=%5B271202%5D',
     ['matchHistoryStats', 'profiles']),
    ('recent_history_varios.json.gz', '/leaderboard/getRecentMatchHistory?title=age2&profile_ids=%5B271202,199325%5D',
     ['matchHistoryStats', 'profiles']),
    ('match_history.json', '/leaderboard/getMatchHistory?title=age2&matchIDs=%5B508450267%5D',
     ['matchHistory']),
    ('alias.json', '/leaderboard/getPersonalStat?title=age2&aliases=%5B%22Oni.Vinchester%22%5D',
     ['statGroups', 'leaderboardStats']),
    ('alias_desconocido.json', '/leaderboard/getPersonalStat?title=age2&aliases=%5B%22oni.vinchester%22%5D',
     []),
    ('available_leaderboards.json', '/leaderboard/getAvailableLeaderboards?title=age2',
     ['leaderboards', 'races', 'matchTypes']),
]


def main():
    os.makedirs(DEST, exist_ok=True)
    errores = []
    for i, (archivo, ruta, claves) in enumerate(LLAMADAS):
        if i:
            time.sleep(4)
        url = HOST + ruta
        req = urllib.request.Request(url, headers={'User-Agent': UA})
        with urllib.request.urlopen(req, timeout=30) as r:
            cuerpo = r.read()
            estado = r.status
        # Las dos respuestas grandes (400-900 KB) se guardan comprimidas (.json.gz); el resto, tal cual.
        with (gzip.open if archivo.endswith('.gz') else open)(os.path.join(DEST, archivo), 'wb') as f:
            f.write(cuerpo)
        datos = json.loads(cuerpo.decode('utf-8'))
        codigo = datos.get('result', {}).get('code')
        esperado = 9 if archivo == 'alias_desconocido.json' else 0   # alias en minúsculas: UNKNOWN_ALIASES
        print(f'{archivo}: HTTP {estado}, result.code {codigo}, {len(cuerpo)} bytes')
        if codigo != esperado:
            errores.append(f'{archivo}: result.code {codigo}, se esperaba {esperado}')
        for c in claves:
            if c not in datos:
                errores.append(f'{archivo}: falta la clave {c}')
    if errores:
        print('\n'.join(['FORMATO INESPERADO:'] + errores))
        sys.exit(1)
    print('OK: formato esperado')


if __name__ == '__main__':
    main()
