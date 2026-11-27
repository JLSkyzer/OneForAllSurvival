param([string]$Dossier, [int]$MaxMinutes = 20)
# Coupe-circuit de l'essai : ferme notre client des qu'il prend le premier plan ou enferme le curseur.
Add-Type @"
using System; using System.Runtime.InteropServices;
public static class W {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool GetClipCursor(out RECT r);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
  [StructLayout(LayoutKind.Sequential)] public struct LASTINPUT { public uint cbSize; public uint dwTime; }
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
  [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(POINT p);
  [DllImport("user32.dll")] public static extern short GetAsyncKeyState(int k);
  [DllImport("user32.dll")] public static extern bool GetLastInputInfo(ref LASTINPUT i);
  [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr h, int i);
}
"@
$journal = Join-Path $Dossier 'guet.log'
$fini = Join-Path $Dossier 'fini'
$alerte = Join-Path $Dossier 'alerte'
function Write-Guet($m) { ('{0:HH:mm:ss.fff} {1}' -f (Get-Date), $m) | Out-File -Append -Encoding utf8 $journal }

$clients = @{}
$fenetres = @{}
$dernierPlan = ''
$releves = 0
$prises = 0
$prochainInventaire = Get-Date
$limite = (Get-Date).AddMinutes($MaxMinutes)
Write-Guet 'guet en place'
while (-not (Test-Path $fini) -and (Get-Date) -lt $limite) {
    if ((Get-Date) -ge $prochainInventaire) {
        $prochainInventaire = (Get-Date).AddMilliseconds(700)
        $vus = @{}
        Get-CimInstance Win32_Process -Filter "Name LIKE 'java%'" | ForEach-Object {
            if ($_.CommandLine -match 'OneForAllSurvival' -and $_.CommandLine -cmatch '-D(oneforallsurvival|lostisland)\.') {
                $vus[[int]$_.ProcessId] = $true
                if (-not $clients.ContainsKey([int]$_.ProcessId)) { Write-Guet "client vu : $($_.ProcessId)" }
            }
        }
        $clients = $vus
    }
    $h = [W]::GetForegroundWindow()
    $idPlan = [uint32]0
    [void][W]::GetWindowThreadProcessId($h, [ref]$idPlan)
    $releves++
    $nom = (Get-Process -Id ([int]$idPlan) -ErrorAction SilentlyContinue).ProcessName
    $etiquette = "$nom ($idPlan)"
    if ($etiquette -ne $dernierPlan) { Write-Guet "premier plan : $etiquette"; $dernierPlan = $etiquette }
    $clip = New-Object W+RECT
    [void][W]::GetClipCursor([ref]$clip)
    foreach ($id in @($clients.Keys)) {
        $p = Get-Process -Id $id -ErrorAction SilentlyContinue
        if ($null -eq $p) { continue }
        $fautif = $null
        if ([int]$idPlan -eq $id) { $fautif = 'premier plan pris' }
        $f = $p.MainWindowHandle
        if ($f -ne [IntPtr]::Zero) {
            $r = New-Object W+RECT
            [void][W]::GetWindowRect($f, [ref]$r)
            $place = "$($r.L),$($r.T) $($r.R - $r.L)x$($r.B - $r.T)"
            $sansActivation = ([W]::GetWindowLong($f, -20) -band 0x08000000) -ne 0
            $place = "$place, clic sans activation : $sansActivation"
            if ($fenetres[$id] -ne $place) { Write-Guet "fenetre du client $id : $place"; $fenetres[$id] = $place }
            if ($clip.L -ge $r.L -and $clip.T -ge $r.T -and $clip.R -le $r.R -and $clip.B -le $r.B -and ($clip.R - $clip.L) -gt 0) {
                $fautif = 'curseur enferme dans la fenetre'
            }
        }
        if ($fautif) {
            $pt = New-Object W+POINT
            [void][W]::GetCursorPos([ref]$pt)
            $sous = [uint32]0
            [void][W]::GetWindowThreadProcessId([W]::WindowFromPoint($pt), [ref]$sous)
            $dernier = New-Object W+LASTINPUT
            $dernier.cbSize = 8
            [void][W]::GetLastInputInfo([ref]$dernier)
            $depuis = [Environment]::TickCount - [int]$dernier.dwTime
            Write-Guet "  curseur $($pt.X),$($pt.Y) sur le processus $sous ; bouton gauche $([W]::GetAsyncKeyState(1)), droit $([W]::GetAsyncKeyState(2)) ; derniere saisie il y a $depuis ms"
            $prises++
            Stop-Process -Id $id -Force -Confirm:$false
            Write-Guet "ALERTE : $fautif, client $id ferme"
            'alerte' | Out-File -Encoding ascii $alerte
        }
    }
    Start-Sleep -Milliseconds 30
}
Write-Guet "fin : $releves releves, $prises alerte(s)"
