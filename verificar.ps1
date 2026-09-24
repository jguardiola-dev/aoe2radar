# verificar.ps1 — la comprobación de antes de cada commit (CLAUDE.md): compila y pasa los tests con el harness.
# Uso:  .\verificar.ps1            compila + todos los tests (abre la app ~1 min: no toques el PC)
#       .\verificar.ps1 -Rapido    solo compila y pasa los tests sin pantalla (ComparadorCapturasTest).
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

# lista explícita: un «if» que devuelve un array de un elemento lo convierte en String, y @String pasaría sus letras sueltas
$argumentos = @('test')
if ($Rapido) { $argumentos += '-Dtest=ComparadorCapturasTest' }

# Con el harness: pitido grave al empezar y ventanita roja (fuera de la zona fotografiada, sin robar el foco);
# al terminar, dos pitidos que suben y ventanita verde unos segundos. Se puede mover el ratón en los otros
# monitores, pero sin hacer clic (cambiaría el foco).
$pwsh = (Get-Process -Id $PID).Path
$aviso = Join-Path $PSScriptRoot 'tools\aviso.ps1'
$ventana = $null
if (-not $Rapido) {
    Write-Host "== Tests con harness: NO toques el ratón en el monitor principal (~1 min)" -ForegroundColor Cyan
    [console]::Beep(440, 180)
    $ventana = Start-Process $pwsh -ArgumentList '-NoProfile', '-File', "`"$aviso`"", '-Texto', '"NO TOQUES EL RATÓN · harness en marcha"', '-Color', 'rojo' -WindowStyle Hidden -PassThru
}
try {
    $salida = mvn @argumentos 2>&1
    $codigo = $LASTEXITCODE
} finally {
    if ($ventana) {
        Stop-Process -Id $ventana.Id -ErrorAction SilentlyContinue
        [console]::Beep(523, 120); [console]::Beep(784, 160)
        Start-Process $pwsh -ArgumentList '-NoProfile', '-File', "`"$aviso`"", '-Texto', '"YA PUEDES USAR EL RATÓN"', '-Color', 'verde', '-Segundos', '6' -WindowStyle Hidden
    }
}

# Resumen: capturas que fallan, avisos de reintento y el total de tests
$salida | Select-String -Pattern 'intento \d .*FALLA' | ForEach-Object { ($_.Line -replace ' \? C:.*', '' -replace ' . watch:.*', '') }
$salida | Select-String -Pattern 'AVISO' | ForEach-Object { Write-Host $_.Line -ForegroundColor Yellow }
$salida | Select-String -Pattern 'Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+$' | Select-Object -Last 1 | ForEach-Object { $_.Line }
if ($codigo -eq 0) { Write-Host "VERDE" -ForegroundColor Green } else { Write-Host "ROJO: diferencias en target\capturas (*.diff.png)" -ForegroundColor Red }
exit $codigo
