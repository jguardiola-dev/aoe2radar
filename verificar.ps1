# verificar.ps1 — la comprobación de antes de cada commit (CLAUDE.md): compila y pasa los tests con el harness.
# Uso:  .\verificar.ps1            compila + todos los tests (abre la app ~1 min: no toques el PC)
#       .\verificar.ps1 -Rapido    compila y pasa todos los tests salvo el harness (sin pantalla).
#                                   Sirve para iterar; NO vale antes de un commit (no pasa el harness).
param([switch]$Rapido)

# Variables de usuario, por si la consola se abrió antes de configurarlas
$jh = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if ($jh) { $env:JAVA_HOME = $jh }
$env:Path = $env:Path + ';' + [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
Set-Location $PSScriptRoot
# si mvn no existe, $LASTEXITCODE no cambia y un paso fallido parecería correcto
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Write-Host "No encuentro mvn: revisa el Path (C:\maven\apache-maven-3.9.14\bin)" -ForegroundColor Red; exit 1 }

Write-Host "== Compilando" -ForegroundColor Cyan
mvn -q -DskipTests test-compile
if ($LASTEXITCODE -ne 0) { Write-Host "NO COMPILA" -ForegroundColor Red; exit 1 }

# Regla de capas (docs/ARQUITECTURA.md): ningún paquete puede importar una capa de fuera
if (Get-Command python -ErrorAction SilentlyContinue) {
    python tools\capas.py
    if ($LASTEXITCODE -ne 0) { Write-Host "CAPAS: hay dependencias hacia fuera (arriba el detalle)" -ForegroundColor Red; exit 1 }
} else { Write-Host "(sin python: no se comprueban las capas)" -ForegroundColor Yellow }

# lista explícita: un «if» que devuelve un array de un elemento lo convierte en String, y @String pasaría sus letras sueltas
$argumentos = @('test')
if ($Rapido) { $argumentos += '-Dtest=!RegresionCapturas' } else { $argumentos += '-Dharness=si' }   # sin -Dharness=si el harness no corre (RegresionCapturas)

# Con el harness: el propio RegresionCapturas avisa (AvisoHarness: pitido grave y ventanita roja al empezar; dos
# pitidos y ventanita verde al acabar), así avisa también si se lanza con mvn a mano o desde un subagente. Se puede
# mover el ratón en los otros monitores, pero sin hacer clic (cambiaría el foco).
if (-not $Rapido) { Write-Host "== Tests con harness: NO toques el ratón en el monitor principal (~1 min)" -ForegroundColor Cyan }
$salida = mvn @argumentos 2>&1
$codigo = $LASTEXITCODE

# Resumen: capturas que fallan, avisos de reintento y el total de tests
$salida | Select-String -Pattern 'intento \d .*FALLA' | ForEach-Object { ($_.Line -replace ' \? C:.*', '' -replace ' . watch:.*', '') }
$salida | Select-String -Pattern 'AVISO' | ForEach-Object { Write-Host $_.Line -ForegroundColor Yellow }
# tests unitarios que fallan: «[ERROR]   Clase.metodo:linea …»
$salida | Select-String -Pattern '^\[ERROR\]\s+\w+\.\w+:\d+' | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
$salida | Select-String -Pattern 'Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$' | Select-Object -Last 1 | ForEach-Object { $_.Line }
if ($codigo -eq 0) { Write-Host "VERDE" -ForegroundColor Green } else { Write-Host "ROJO: mira arriba qué falló (capturas: target\capturas\*.diff.png)" -ForegroundColor Red }
exit $codigo
