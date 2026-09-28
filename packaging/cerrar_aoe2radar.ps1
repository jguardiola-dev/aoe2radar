# cerrar_aoe2radar.ps1 - lo usa el instalador (y el desinstalador) de aoe2radar antes de tocar la carpeta de la app.
#
# El lanzador de jpackage (aoe2radar.exe) arranca la app como DOS procesos aoe2radar.exe: el lanzador y un hijo
# (el mismo exe) que lleva la JVM y la ventana. El Restart Manager de Windows no consigue cerrarlos, y un WM_CLOSE
# al lanzador lo rompe ("GetMessage() failed. System error 1400" y se queda colgado). Por eso:
#   1. Se pide el cierre SOLO a los procesos con ventana (los que no son padre de otro aoe2radar.exe), con
#      taskkill /PID sin /F: es como pulsar la X, la app guarda lo suyo y aplica una actualizacion pendiente.
#   2. Se espera a que no quede ninguno: el lanzador sale solo cuando acaba su hijo.
#   3. Un lanzador que sigue vivo 5 s despues de quedarse sin hijo se cierra con /F (no tiene nada que guardar).
#      Nunca /F sobre un proceso con ventana: perderia lo que guarda al cerrar.
# Solo cuenta los aoe2radar.exe que estan dentro de -Dir (otra copia en zip en otra carpeta no se toca).
# Salida: 0 si ya no queda ninguno; 1 si sigue abierta al acabar la espera. Solo ASCII en este archivo.
param(
    [Parameter(Mandatory = $true)][string]$Dir,
    [int]$Espera = 20,
    [string]$Salida = ''
)
$ErrorActionPreference = 'Stop'

# Lo que pasa, por pantalla y (si se pide) a un archivo que el instalador copia a su log.
function Decir([string]$t) {
    Write-Output $t
    # .NET y no Add-Content: no depende de cargar modulos (lanzado desde pwsh 7, powershell.exe hereda un PSModulePath ajeno)
    if ($Salida) { [IO.File]::AppendAllText($Salida, $t + "`r`n", [Text.Encoding]::ASCII) }
}

function Procesos {
    $pref = $Dir.TrimEnd('\') + '\'
    @(Get-CimInstance Win32_Process -Filter "Name='aoe2radar.exe'" | Where-Object {
        $_.ExecutablePath -and $_.ExecutablePath.StartsWith($pref, [StringComparison]::OrdinalIgnoreCase)
    })
}

try {
    $ps = Procesos
    if ($ps.Count -eq 0) { Decir 'no hay ninguno abierto'; exit 0 }
    $pids = @($ps | ForEach-Object { [int]$_.ProcessId })
    # Lanzador: el aoe2radar.exe que es padre de otro aoe2radar.exe.
    $lanzadores = @($ps | Where-Object { $pids -contains [int]$_.ParentProcessId } | ForEach-Object { [int]$_.ParentProcessId } | Sort-Object -Unique)
    foreach ($p in $ps) {
        $id = [int]$p.ProcessId
        if ($lanzadores -contains $id) { continue }
        Decir "pido el cierre (como la X) a $id"
        & taskkill.exe /PID $id | Out-Null
    }
    $fin = (Get-Date).AddSeconds($Espera)
    $sinHijoDesde = @{}
    while ((Get-Date) -lt $fin) {
        Start-Sleep -Milliseconds 500
        $ps = Procesos
        if ($ps.Count -eq 0) { Decir 'cerrada'; exit 0 }
        $vivos = @($ps | ForEach-Object { [int]$_.ProcessId })
        foreach ($l in $lanzadores) {
            if ($vivos -notcontains $l) { continue }
            $conHijo = @($ps | Where-Object { [int]$_.ParentProcessId -eq $l }).Count -gt 0
            if ($conHijo) { $sinHijoDesde.Remove($l); continue }
            if (-not $sinHijoDesde.ContainsKey($l)) { $sinHijoDesde[$l] = Get-Date }
            elseif (((Get-Date) - $sinHijoDesde[$l]).TotalSeconds -ge 5) {
                Decir "el lanzador $l sigue sin hijo: /F"
                & taskkill.exe /F /PID $l | Out-Null
            }
        }
    }
    Decir ('sigue abierta: ' + (@(Procesos | ForEach-Object { $_.ProcessId }) -join ', '))
    exit 1
} catch {
    Decir ('error: ' + $_)
    exit 1
}
