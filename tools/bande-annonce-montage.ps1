# Montage de la bande-annonce (FilmBandeAnnonce, tâche runDemoBandeAnnonceFilm) à partir des images du jeu, sans capture
# de bureau. Trois morceaux encodés pareil (1280 x 720, 30 i/s, H.264), puis mis bout à bout :
#   1. le chargement, condensé en -DureeChargement secondes : les images de la fenêtre anticipée
#      (plans/fenetre-chargement/frame_*.png, une image sur deux de la scène) suivies de celles de l'overlay des
#      ressources (plans/chargement-jeu/f*.jpg) ;
#   2. le film (plans/film/f*.jpg, déjà à 30 i/s : écran titre, puis tous les plans), fondu au noir à la fin.
# Usage : powershell -File tools/bande-annonce-montage.ps1 [-Ffmpeg <chemin de ffmpeg.exe>]
param(
    [string]$Dossier = (Join-Path $PSScriptRoot '..\run-demo\bande-annonce\plans'),
    [string]$Sortie = (Join-Path $PSScriptRoot '..\run-demo\sneak-peek-economie.mp4'),
    [string]$Ffmpeg = 'ffmpeg',
    [double]$DureeChargement = 8,
    # Un passage figé garde ce temps (lecture de la légende), le reste est coupé.
    [double]$GardeFige = 2.2,
    # Seuil de freezedetect (dB) : au-dessous, deux images sont tenues pour identiques.
    [int]$SeuilFige = -45,
    # Dernière image du film gardée (0 : toutes) ; sert à couper un plan raté et ce qui le suit.
    [int]$Jusqua = 0
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command $Ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg introuvable ($Ffmpeg) : passer -Ffmpeg <chemin de ffmpeg.exe>"
}
$Dossier = (Resolve-Path $Dossier).Path
$travail = Join-Path $Dossier 'montage'
New-Item -ItemType Directory -Force $travail | Out-Null
$encodage = @('-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', '30')
$cadre = 'scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:black,setsar=1'
$culture = [System.Globalization.CultureInfo]::InvariantCulture

function Lancer([string[]]$arguments) {
    & $Ffmpeg -hide_banner -loglevel error -y @arguments
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg a échoué ($LASTEXITCODE) : $($arguments -join ' ')" }
}

$morceaux = @()

# 1. Le chargement condensé : deux morceaux (PNG de la fenêtre anticipée, JPEG de l'overlay ; le démultiplexeur concat
#    ne mêle pas deux formats d'image), chacun pour sa part du temps.
$parts = @()
$fenetre = Join-Path $Dossier 'fenetre-chargement'
$overlay = Join-Path $Dossier 'chargement-jeu'
if (Test-Path $fenetre) { $parts += ,@(Get-ChildItem $fenetre -Filter 'frame_*.png' | Sort-Object Name) }
if (Test-Path $overlay) { $parts += ,@(Get-ChildItem $overlay -Filter 'f*.jpg' | Sort-Object Name) }
$total = 0
foreach ($p in $parts) { $total += $p.Count }
$k = 0
foreach ($images in $parts) {
    if ($images.Count -eq 0) { continue }
    $k++
    $d = ($DureeChargement / $total).ToString('0.######', $culture)
    $liste = Join-Path $travail "chargement-$k.txt"
    $lignes = foreach ($i in $images) { "file '$($i.FullName -replace "'", "'\''")'"; "duration $d" }
    $lignes += "file '$($images[-1].FullName -replace "'", "'\''")'"
    [System.IO.File]::WriteAllLines($liste, $lignes)
    $seg = Join-Path $travail "1-chargement-$k.mp4"
    Lancer (@('-f', 'concat', '-safe', '0', '-i', $liste, '-vf', "$cadre,fps=30") + $encodage + @($seg))
    $morceaux += $seg
}
Write-Host "chargement : $total images condensées en $DureeChargement s"

# 2. Le film, sans moment vide : tout passage figé (freezedetect) est raccourci à -GardeFige secondes, le temps de lire
#    la légende ; le reste du passage est coupé.
$film = Join-Path $Dossier 'film'
$n = (Get-ChildItem $film -Filter 'f*.jpg').Count
if ($n -eq 0) { throw "aucune image dans $film" }
if ($Jusqua -gt 0 -and $Jusqua -lt $n) {
    # Les images après la coupe sont tenues pour figées jusqu'à la fin : la sélection plus bas les retire.
    $n = $Jusqua
}
$limite = @('-t', ($n / 30.0).ToString('0.###', $culture))
$meta = Join-Path $travail 'fige.txt'
Remove-Item $meta -ErrorAction SilentlyContinue
$metaFiltre = ($meta -replace '\\', '/') -replace ':', '\:'
Lancer (@('-framerate', '30') + $limite + @('-i', (Join-Path $film 'f%05d.jpg'), '-vf', "freezedetect=n=${SeuilFige}dB:d=${GardeFige},metadata=mode=print:file='$metaFiltre'", '-f', 'null', '-'))
$coupes = @()
$debut = $null
foreach ($ligne in (Get-Content $meta -ErrorAction SilentlyContinue)) {
    if ($ligne -match 'freeze_start=([0-9.]+)') { $debut = [double]::Parse($Matches[1], $culture) }
    elseif ($ligne -match 'freeze_end=([0-9.]+)' -and $null -ne $debut) {
        $a = [int][Math]::Round(($debut + $GardeFige) * 30)
        $b = [int][Math]::Round([double]::Parse($Matches[1], $culture) * 30) - 1
        if ($b -gt $a) { $coupes += "between(n,$a,$b)" }
        $debut = $null
    }
}
if ($null -ne $debut) {
    $a = [int][Math]::Round(($debut + $GardeFige) * 30)
    if ($n - 1 -gt $a) { $coupes += "between(n,$a,$($n - 1))" }
}
$gardees = $n
$filtre = $cadre
if ($coupes.Count -gt 0) {
    $filtre = "select='not(" + ($coupes -join '+') + ")',setpts=N/(30*TB)," + $cadre
    foreach ($c in $coupes) { if ($c -match 'between\(n,(\d+),(\d+)\)') { $gardees -= ([int]$Matches[2] - [int]$Matches[1] + 1) } }
}
$duree = $gardees / 30.0
$fin = [Math]::Max(0, $duree - 1.2).ToString('0.###', $culture)
$script = Join-Path $travail 'film-filtre.txt'
[System.IO.File]::WriteAllText($script, "$filtre,fade=t=out:st=${fin}:d=1.2")
$seg = Join-Path $travail '2-film.mp4'
Lancer (@('-framerate', '30') + $limite + @('-i', (Join-Path $film 'f%05d.jpg'), '-/vf', $script) + $encodage + @($seg))
$morceaux += $seg
Write-Host "film : $n images, $($coupes.Count) passages figés raccourcis, $gardees gardées, $([Math]::Round($duree, 1)) s"

# Bout à bout.
$liste = Join-Path $travail 'tout.txt'
[System.IO.File]::WriteAllLines($liste, ($morceaux | ForEach-Object { "file '$($_ -replace "'", "'\''")'" }))
Lancer @('-f', 'concat', '-safe', '0', '-i', $liste, '-c', 'copy', '-movflags', '+faststart', $Sortie)
Write-Host "bande-annonce : $((Resolve-Path $Sortie).Path)"
