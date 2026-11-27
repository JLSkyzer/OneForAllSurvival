# Usage (outil PowerShell, depuis la racine du dépôt) : & .\tools\minecraft\lancer.ps1 -Libelle "..." -Gradle "runWikiStudio -PwikiSeul=telephone"
# Attend le verrou Minecraft (file commune), met le coupe-circuit en place, lance Gradle, libère. Un seul à la fois.
# Code de sortie : 0 réussite ; 1 refus ou échec ; 2 alerte du coupe-circuit (client fermé : comprendre avant de relancer).
param([Parameter(Mandatory)][string]$Libelle, [Parameter(Mandatory)][string]$Gradle, [int]$MaxMinutes = 55)
$racine = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$ici = $PSScriptRoot
$journaux = Join-Path $racine 'build\lancements-minecraft'
$verrou = Join-Path $env:USERPROFILE '.claude\lancement-minecraft.txt'
$file = Join-Path $env:USERPROFILE '.claude\lancement-minecraft-attente.txt'
$projet = 'OneForAllSurvival'
$utf8 = New-Object System.Text.UTF8Encoding $false

function Read-Lignes($chemin) { if (Test-Path $chemin) { @([IO.File]::ReadAllLines($chemin) | Where-Object { $_.Trim() }) } else { @() } }
function Test-Libre { (Read-Lignes $verrou).Count -eq 0 -and ((Read-Lignes $file).Count -eq 0 -or (Read-Lignes $file)[0].StartsWith($projet)) }

if (-not (Test-Libre) -and -not (Read-Lignes $file | Where-Object { $_.StartsWith($projet) })) {
    [IO.File]::AppendAllText($file, "$projet - $Libelle - $(Get-Date -Format HH:mm)`r`n", $utf8)
    Write-Output "En file ; verrou : $((Read-Lignes $verrou) -join ' | ')"
}
$limite = (Get-Date).AddMinutes(8)
while (-not (Test-Libre)) {
    if ((Get-Date) -gt $limite) { Write-Output 'Toujours occupé après 8 min : relancer ce script en arrière-plan pour attendre.'; exit 1 }
    Start-Sleep -Seconds 5
}

$dossier = Join-Path $journaux (Get-Date -Format 'dd-HHmmss')
New-Item -ItemType Directory -Force $dossier | Out-Null
$guet = Start-Job -FilePath (Join-Path $ici 'guet.ps1') -ArgumentList $dossier, $MaxMinutes
$journal = Join-Path $dossier 'guet.log'
$essais = 0
while (-not (Test-Path $journal) -or -not (Select-String -Path $journal -Pattern 'guet en place' -Quiet)) {
    if (++$essais -gt 60) { Write-Output "Pas de coupe-circuit : rien n'est lancé."; Receive-Job $guet; Stop-Job $guet; exit 1 }
    Start-Sleep -Seconds 1
}
if ((Read-Lignes $verrou).Count) { Write-Output 'Verrou repris entre-temps : rien.'; New-Item (Join-Path $dossier 'fini') -ItemType File | Out-Null; Wait-Job $guet | Out-Null; exit 1 }
$marque = "pid $PID"
[IO.File]::WriteAllText($verrou, "$projet - $Libelle - $(Get-Date -Format HH:mm) - $marque`r`n", $utf8)
$reste = @(Read-Lignes $file | Where-Object { -not $_.StartsWith($projet) })
[IO.File]::WriteAllText($file, (($reste | ForEach-Object { "$_`r`n" }) -join ''), $utf8)
try {
    Write-Output "Verrou pris à $(Get-Date -Format HH:mm:ss) ; journaux : $dossier"
    $log = Join-Path $dossier 'gradle.log'
    Set-Location $racine
    cmd /c ".\gradlew.bat --offline --priority low $Gradle > `"$log`" 2>&1"
    $code = $LASTEXITCODE
    Write-Output "Gradle terminé à $(Get-Date -Format HH:mm:ss), code $code"
} finally {
    if ((Read-Lignes $verrou) -join '' -match [regex]::Escape($marque)) { [IO.File]::WriteAllText($verrou, '', $utf8) }
    New-Item (Join-Path $dossier 'fini') -ItemType File -Force | Out-Null
    Wait-Job $guet -Timeout 10 | Out-Null
    Stop-Job $guet -ErrorAction SilentlyContinue
}
if (Test-Path (Join-Path $dossier 'alerte')) { Write-Output 'ALERTE du coupe-circuit :'; Select-String -Path $journal -Pattern 'ALERTE' -Context 3, 0; exit 2 }
if ($code -ne 0) { Get-Content $log -Tail 30; exit 1 }
exit 0
