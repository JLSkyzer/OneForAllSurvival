# Releve la memoire du client Minecraft (java) et de ses processus Chromium (jcef_helper) pendant un essai de
# performance du telephone (TelephoneShots, mode perf).
#
#   powershell -File tools/telephone-memoire.ps1 -Dossier run/frames-telephone-perf
#
# Le client ecrit son PID dans <Dossier>/pid.txt, puis un horodatage par etape dans <Dossier>/etapes.txt
# ("<millisecondes epoch>;<etape>"). Le script attend pid.txt (le plus recent : il doit etre ecrit apres le debut du
# script), releve chaque seconde la memoire de travail et la memoire privee de java et la somme de celles des
# jcef_helper descendants de ce java (jamais ceux d'un autre client), jusqu'a la fin du client. Il ecrit :
#   <Dossier>/memoire.csv         un releve par seconde ;
#   <Dossier>/memoire-etapes.csv  le releve le plus proche de chaque horodatage d'etape (au plus 1 s d'ecart).
# Le script ne fait que lire : il n'arrete aucun processus.
param(
    [Parameter(Mandatory = $true)][string]$Dossier,
    [int]$AttentePidSecondes = 900
)

$ErrorActionPreference = 'Stop'
$debut = Get-Date
$pidFichier = Join-Path $Dossier 'pid.txt'
$etapesFichier = Join-Path $Dossier 'etapes.txt'
$csv = Join-Path $Dossier 'memoire.csv'
$csvEtapes = Join-Path $Dossier 'memoire-etapes.csv'

# Attente d'un pid.txt ecrit apres le lancement du script (un ancien fichier ne compte pas).
$javaPid = $null
while (-not $javaPid) {
    if ((Test-Path $pidFichier) -and (Get-Item $pidFichier).LastWriteTime -gt $debut) {
        $javaPid = [int]((Get-Content $pidFichier -Raw).Trim())
    } elseif (((Get-Date) - $debut).TotalSeconds -gt $AttentePidSecondes) {
        Write-Output "[memoire] pid.txt jamais ecrit en $AttentePidSecondes s : abandon"
        exit 1
    } else {
        Start-Sleep -Milliseconds 500
    }
}
# Le processus doit etre un java de ce projet : sa ligne de commande contient OneForAllSurvival (regle de
# cohabitation des sessions ; jamais la memoire d'une JVM d'un autre projet). Controle refait a chaque releve.
function Test-NotreJava($processus) {
    return $processus -and $processus.Name -eq 'java.exe' -and $processus.CommandLine -like '*OneForAllSurvival*'
}
$cible = Get-CimInstance Win32_Process -Filter "ProcessId = $javaPid" -Property ProcessId, Name, CommandLine
if (-not (Test-NotreJava $cible)) {
    Write-Output "[memoire] le PID $javaPid n'est pas un java vivant de OneForAllSurvival : abandon"
    exit 1
}
Write-Output "[memoire] client java PID $javaPid (ligne de commande de OneForAllSurvival)"

function Get-Descendants([int]$racine, $tous) {
    $enfants = @($tous | Where-Object { $_.ParentProcessId -eq $racine })
    $resultat = @()
    foreach ($e in $enfants) {
        $resultat += $e
        $resultat += Get-Descendants $e.ProcessId $tous
    }
    return $resultat
}

$Mo = 1MB
$lignes = New-Object System.Collections.Generic.List[string]
$lignes.Add('epoch_ms;heure;java_ws_mo;java_prive_mo;jcef_nombre;jcef_ws_mo;jcef_prive_mo;total_ws_mo')
$releves = New-Object System.Collections.Generic.List[object]
while ($true) {
    $tous = Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, Name, CommandLine, WorkingSetSize,
            PrivatePageCount
    $cible = $tous | Where-Object { $_.ProcessId -eq $javaPid } | Select-Object -First 1
    if (-not $cible) { break }
    # Un processus qui se termine rend une ligne de commande vide : c'est la fin du client, pas un autre java.
    if (-not $cible.CommandLine) { break }
    if (-not (Test-NotreJava $cible)) {
        Write-Output "[memoire] le PID $javaPid n'est plus un java de OneForAllSurvival (PID reutilise ?) : abandon"
        exit 1
    }
    $java = Get-Process -Id $javaPid -ErrorAction SilentlyContinue
    if (-not $java) { break }
    $jcef = @(Get-Descendants $javaPid $tous | Where-Object { $_.Name -eq 'jcef_helper.exe' })
    $jcefWs = ($jcef | Measure-Object -Property WorkingSetSize -Sum).Sum
    $jcefPrive = ($jcef | Measure-Object -Property PrivatePageCount -Sum).Sum
    if (-not $jcefWs) { $jcefWs = 0 }
    if (-not $jcefPrive) { $jcefPrive = 0 }
    $epoch = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $r = [pscustomobject]@{
        Epoch = $epoch
        Heure = (Get-Date).ToString('HH:mm:ss')
        JavaWs = [math]::Round($java.WorkingSet64 / $Mo, 1)
        JavaPrive = [math]::Round($java.PrivateMemorySize64 / $Mo, 1)
        JcefNombre = $jcef.Count
        JcefWs = [math]::Round($jcefWs / $Mo, 1)
        JcefPrive = [math]::Round($jcefPrive / $Mo, 1)
    }
    $releves.Add($r)
    # Virgule decimale (culture fr) remplacee par un point ; le texte est forme avant l'appel de Add (dans les
    # parentheses d'un appel, une virgule separe les arguments).
    $texte = '{0};{1};{2};{3};{4};{5};{6};{7}' -f $r.Epoch, $r.Heure, $r.JavaWs, $r.JavaPrive, $r.JcefNombre,
            $r.JcefWs, $r.JcefPrive, [math]::Round($r.JavaWs + $r.JcefWs, 1)
    $texte = $texte -replace ',', '.'
    $lignes.Add($texte)
    [IO.File]::WriteAllLines($csv, $lignes)
    Start-Sleep -Seconds 1
}
Write-Output "[memoire] client termine, $($releves.Count) releves"

# Le releve le plus proche de chaque etape.
$sortie = New-Object System.Collections.Generic.List[string]
$sortie.Add('etape;epoch_ms;ecart_ms;java_ws_mo;java_prive_mo;jcef_nombre;jcef_ws_mo;jcef_prive_mo;total_ws_mo')
if (Test-Path $etapesFichier) {
    foreach ($l in Get-Content $etapesFichier) {
        $morceaux = $l.Split(';')
        if ($morceaux.Count -ne 2) { continue }
        $t = [long]$morceaux[0]
        $proche = $releves | Sort-Object { [math]::Abs($_.Epoch - $t) } | Select-Object -First 1
        if (-not $proche) { continue }
        $ecart = $proche.Epoch - $t
        $ligne = '{0};{1};{2};{3};{4};{5};{6};{7};{8}' -f $morceaux[1], $t, $ecart, $proche.JavaWs, $proche.JavaPrive,
                $proche.JcefNombre, $proche.JcefWs, $proche.JcefPrive, [math]::Round($proche.JavaWs + $proche.JcefWs, 1)
        $ligne = $ligne -replace ',', '.'
        $sortie.Add($ligne)
        if ([math]::Abs($ecart) -gt 1000) {
            Write-Output "[memoire] etape $($morceaux[1]) : releve le plus proche a $ecart ms"
        }
    }
}
[IO.File]::WriteAllLines($csvEtapes, $sortie)
Get-Content $csvEtapes | ForEach-Object { Write-Output "[memoire] $_" }
