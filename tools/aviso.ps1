# aviso.ps1 — ventanita de aviso para el harness. No roba el foco (WS_EX_NOACTIVATE) y va fuera de la zona que se
# fotografía (la app ocupa 0..1500 x 0..950 del monitor principal). No hace falta usarla a mano.
# (la lanza AvisoHarness, dentro de RegresionCapturas)
# Uso: aviso.ps1 -Texto "..." -Color rojo|verde [-Segundos 6]   (sin -Segundos se queda hasta que la cierren)
param([string]$Texto = "Harness en marcha", [ValidateSet('rojo', 'verde')][string]$Color = 'rojo', [int]$Segundos = 0)

Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type -ReferencedAssemblies System.Windows.Forms, System.Drawing, System.ComponentModel.Primitives, System.Windows.Forms.Primitives -TypeDefinition @"
using System.Windows.Forms;
public class AvisoSinFoco : Form {
    protected override bool ShowWithoutActivation { get { return true; } }
    protected override CreateParams CreateParams {
        get { var cp = base.CreateParams; cp.ExStyle |= 0x08000000 | 0x00000008 | 0x00000080; return cp; }   // NOACTIVATE | TOPMOST | TOOLWINDOW
    }
}
"@

$f = New-Object AvisoSinFoco
$f.FormBorderStyle = 'None'
$f.StartPosition = 'Manual'
$f.Location = New-Object System.Drawing.Point(1560, 20)
$f.Size = New-Object System.Drawing.Size(460, 70)
$f.BackColor = if ($Color -eq 'rojo') { [System.Drawing.Color]::FromArgb(0xb7, 0x1c, 0x1c) } else { [System.Drawing.Color]::FromArgb(0x2e, 0x7d, 0x32) }
$l = New-Object System.Windows.Forms.Label
$l.Dock = 'Fill'; $l.TextAlign = 'MiddleCenter'; $l.ForeColor = [System.Drawing.Color]::White
$l.Font = New-Object System.Drawing.Font('Segoe UI', 13, [System.Drawing.FontStyle]::Bold)
$l.Text = $Texto
$f.Controls.Add($l)
if ($Segundos -gt 0) {
    $t = New-Object System.Windows.Forms.Timer
    $t.Interval = $Segundos * 1000
    $t.Add_Tick({ $f.Close() })
    $t.Start()
}
[System.Windows.Forms.Application]::Run($f)
