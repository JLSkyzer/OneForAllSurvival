# Vérifications du chantier 1 : écran de chargement Lost Island

Relevés du 2026-11-08, sur la branche `interface-chargement` (non fusionnée). Spec :
`docs/superpowers/specs/2026-11-08-interface-lost-island-design.md` (chantier 1) ; plan :
`docs/superpowers/plans/2026-11-08-chantier-1-ecran-de-chargement.md`.

Poste de tous les lancements : celui de Killian, Windows 11, NVIDIA GeForce RTX 4060 (pilote 621.14, GL 4.6),
pack complet (`-PwithPack`), Forge 47.4.28, fenêtre 854x480. Le bilan (tâche 9) est en fin de document.

## Tâche 0 : branchement du fournisseur `lostisland`

`./gradlew earlyWindowJar --offline` : `build/libs/oneforallsurvival-earlywindow-0.1.0.jar` (5 128 octets). `jar tf` n'y montre que `META-INF/MANIFEST.MF`, `META-INF/services/net.minecraftforge.fml.loading.ImmediateWindowProvider` et `dev/oneforallsurvival/earlywindow/LostIslandWindow.class`.

`run/config/fml.toml` : `earlyWindowProvider = "lostisland"`, écrit sans BOM (premiers octets lus : `23 45 61`, « #Ea »).

`./gradlew installEarlyWindow runClient -PwithPack --offline`, client arrêté par son PID une fois l'écran titre atteint. Lu dans `run/logs/latest.log` :

```
[11:00:16.409] [main/INFO] [net.minecraftforge.fml.loading.ImmediateWindowHandler/]: Loading ImmediateWindowProvider lostisland
```

Le jeu atteint le menu comme avant (« Sound engine started » à 11:00:59.126, vérificateur Realms de l'écran titre à 11:01:00.311). Aucune erreur liée au fournisseur dans le journal.

## Relevé du démarrage

Source : `run/logs/lostisland-earlywindow.log` (temps depuis `initialize`, t0 ≈ 11:00:16.4) croisé avec `run/logs/latest.log`. Un seul lancement, pack complet, poste de Killian : ces durées sont **un relevé, pas une mesure répétée**.

### Barres de Forge (`StartupNotificationManager.getCurrentProgress()`)

| Nom exact de la barre | Pas | Libellé observé |
|---|---|---|
| `EARLY` | 0/0 (sans pas) | `Loading mods` |
| `Mod Gather` | 9/18 → 18/18 | `Mod Gather: dispatching CONSTRUCT`, puis `Mod Gather working` |
| `State transition LOAD_REGISTRIES running` | 0/0 | (aucun) |

### Messages caractéristiques (`getMessages()`)

- `Forge loading 47.4.28`
- `Forge version 47.4.28`
- `REGISTERING forge:structure_modifier_serializers`
- `REGISTERING minecraft:worldgen/biome`

Relevés une fois par seconde : les messages qui passent entre deux relevés ne sont pas tous vus (`getMessages()` renvoie une liste courte, les plus récents en tête).

### Phases et durées

| Phase | Début (t) | Fin (t) | Durée | Repère |
|---|---|---|---|---|
| Fenêtre créée (`initialize` du `DisplayWindow`) | 0,0 s | 0,7 s | 0,7 s | journal du fournisseur |
| Découverte, services, couche du jeu | 0,7 s | 6,4 s | 5,7 s | `updateModuleReads` à 6,4 s |
| Préparation du jeu (mixins, classes de Minecraft) | 6,4 s | 20,5 s | 14,1 s | « Setting user: Dev » à 11:00:36.5 ; `setupMinecraftWindow` à 20,5 s |
| Chargement des mods (barres `EARLY`, `Mod Gather`, registres) | 20,5 s | 29,9 s | 9,4 s | `Mod Gather` 9/18 à 21,9 s, 18/18 à 28,9 s |
| Ressources de Minecraft (`loadingOverlay`) | 29,9 s | ≈ 43,9 s | ≈ 14 s | « Reloading ResourceManager » à 11:00:46.1 ; écran titre ≈ 11:01:00.3 |

Total jusqu'à l'écran titre : environ 44 s depuis le chargement du fournisseur.

`periodicTick` n'est appelé que pendant le chargement des mods (de 20,5 s à 29,9 s). Pendant la découverte et la préparation du jeu, puis pendant les ressources (que dessine l'overlay), il ne l'est pas : le journal n'a aucune ligne entre 0,7 s et 20,5 s, ni après 29,9 s.

## Tâche 8 : phase des ressources et fin (overlay du mod)

Lancements du 8 novembre 2026 (`./gradlew --offline installEarlyWindow runClient -PwithPack`, poste de Killian, RTX 4060).

- `loadingOverlay` trouve `LostIslandLoadingOverlay` ; le module du mod (couche du jeu) lit le jar de service (couche des services) sans `addReads` : journal `module oneforallsurvival (couche de 20 modules) lit oneforallsurvival.earlywindow (couche de 1 modules) : true`.
- La barre reste en découverte pendant les libellés « Scanning mod candidates », « Launching minecraft », « Loading bootstrap resources » de la barre EARLY, et ne passe aux mods qu'à « Loading mods » (12,9 s).
- Fin : rechargement fini, la barre et le texte s'effacent en 1 s (logo gardé), l'overlay se retire, la scène est libérée (`overlay du mod : scène libérée`).
- Durée jusqu'à l'écran titre (première ligne de `latest.log` → ligne Realms) : **24,3 s** avec la scène (sans film), **27,1 s** avec la scène et le film d'images, **23,8 s** avec l'écran de Forge (`fmlearlywindow`). Un lancement de chaque, pas une moyenne.
- `reload.getActualProgress()` est très en avance sur le temps : 0,70 à la première image, 0,95 une seconde plus tard, 1,00 à 5 s, alors que le rechargement finit à 7,6 s. La barre passe donc vite à ≈ 97 % et y reste.
- Review Focus 5 (`-PreloadAfterTitle`, `ReloadAfterTitle` en devcontent) : `Minecraft.reloadResourcePacks()` 4 s après l'écran titre ; overlay vu `net.minecraft.client.gui.screens.LoadingOverlay` (celui de Mojang), rechargement fini en 5,9 s, retour à `TitleScreen`, aucune exception nouvelle (mêmes messages que sans rechargement, répétés par le second chargement des modèles). La scène était libérée avant : plus rien ne la dessine.

## Tâche 9 : bilan du chantier

### Commandes

- Tests : `./gradlew --offline test --rerun-tasks` (comptes lus dans les XML de `build/test-results/`).
- Lancement : `./gradlew --offline installEarlyWindow runClient -PwithPack`, avec au besoin :
  - `-PsceneFrames=<dossier>` : film de la scène (une image sur deux, PNG, dossier relatif à `run/`) ;
  - `-PprogressTrace` : relevé, une fois par seconde, de l'avancement réel des ressources et de la barre montrée ;
  - `-PearlyWindowFail[=true|late|render]` : repli de la fenêtre anticipée sur celle de Forge (tâche 7) ;
  - `-PoverlayFail=first` : première image de l'overlay du mod en échec (tâche 9) ;
  - `-PreloadAfterTitle` : rechargement des ressources une fois l'écran titre atteint (tâche 8).
- Arrêt : le seul JVM `bootstraplauncher` de ce dépôt, trouvé par `Win32_Process`, arrêté par son PID exact une
  fois la ligne Realms de l'écran titre écrite dans `latest.log`, puis confirmé arrêté. Aucun autre java touché.
- Film : `ffmpeg -f concat -safe 0 -i film-concat.txt -vf "fps=30,format=yuv420p" -c:v libx264 -crf 18
  run/film-chargement.mp4`, où `film-concat.txt` donne à chaque image la durée qui la sépare de la suivante
  (dates d'écriture des PNG). Voir « Film ».

Lancements de la tâche 9 (heure de la première ligne de `latest.log`) : **A** 13:35:33 (étalonnage,
`-PprogressTrace`), **B** 13:37:27 (film, `-PsceneFrames=frames-film -PprogressTrace`), **C** 13:39:28
(`-PoverlayFail=first`).

### Étalonnage de la barre (`ProgressModel`)

Durée de chaque phase lue dans `run/logs/lostisland-earlywindow.log` (changements de phase ; les ressources
jusqu'à « rechargement fini »), sur **deux démarrages avec la scène et sans film** : le lancement 3 de la tâche 8
(13:21) et le lancement A.

| Phase | 13:21 (tâche 8) | A 13:35 | Moyenne retenue | Part de la barre |
|---|---|---|---|---|
| `FORGE_START` (fenêtre → « Forge loading ») | 0,58 s | 0,59 s | 0,59 s | 2,4 % |
| `MOD_DISCOVERY` (→ barre EARLY « Loading mods ») | 11,16 s | 11,37 s | 11,27 s | 46,7 % |
| `MOD_LOADING` (→ registres) | 2,70 s | 2,62 s | 2,66 s | 11,0 % |
| `REGISTRIES` (→ overlay des ressources) | 2,01 s | 2,11 s | 2,06 s | 8,5 % |
| `RESOURCES` (→ rechargement fini) | 7,53 s | 7,60 s | 7,56 s | 31,3 % |
| Total | 23,98 s | 24,29 s | 24,14 s | 100 % |

Changements de phase lus dans les deux journaux (temps depuis `initialize`) :
```
13:21 (tâche 8, lancement 3 ; journal gardé par la rotation de run/logs/)
[   0.579 s] phase MOD_DISCOVERY : Inventaire de la soute… (recherche des mods)
[  11.743 s] phase MOD_LOADING : Tri des caisses de l'épave… (mods)
[  14.445 s] phase REGISTRIES : Le camp s'organise… (registres)
[  16.452 s] phase RESOURCES : Exploration du littoral… (textures)
[  23.980 s] overlay du mod : rechargement fini en 7.58 s, fondu de la barre et du texte (1000 ms)

A 13:35 (tâche 9)
[   0.594 s] phase MOD_DISCOVERY : Inventaire de la soute… (recherche des mods)
[  11.963 s] phase MOD_LOADING : Tri des caisses de l'épave… (mods)
[  14.581 s] phase REGISTRIES : Le camp s'organise… (registres)
[  16.693 s] phase RESOURCES : Exploration du littoral… (textures)
[  24.288 s] overlay du mod : rechargement fini en 7.66 s, fondu de la barre et du texte (1000 ms)
```
Chaque durée est la différence entre deux lignes consécutives (`FORGE_START` : de 0 à la première ;
`RESOURCES` : de « phase RESOURCES » à « rechargement fini »). Deux lancements d'un même poste : un ordre de
grandeur, pas une moyenne stable.

Les durées attendues (`ProgressModel.EXPECTED_SECONDS`) donnent les parts (`WEIGHTS`, normalisées) et servent à
l'avancement au temps des phases sans étapes (`ProgressTracker`) et des ressources. Le relevé de la tâche 0
(≈ 44 s, un seul lancement avec la fenêtre de Forge, relevé à 1 Hz) n'est plus utilisé : il donnait 45 % de la
barre à la préparation du jeu et 32 % aux ressources, sur un démarrage presque deux fois plus long que ceux
d'aujourd'hui. Le film B (étalonnage appliqué) montre « Inventaire de la soute… » à 44 % vers 11 s, 69 % au début
des ressources, 80 % puis 94 % pendant, 100 % à « Bienvenue sur Lost Island. ».

### Avancement des ressources (ruling 26)

`reload.getActualProgress()` court devant le temps : 0,703 à la première image, 0,946 une seconde plus tard, 0,979
pendant trois secondes, 1,000 à 5 s, pour un rechargement fini à 7,6 s (mêmes valeurs relevées une fois par
seconde aux lancements 3 de la tâche 8, A et B). La fraction montrée est désormais `min(réel, temps écoulé / 7,56 s)`, plafonnée à 0,98 jusqu'à
la fin réelle (`ProgressModel.resourcesFraction`, DONE donne ensuite 1).

**Écart au ruling, assumé** : le ruling écrit `max(réel, estimé)`. Avec le relevé ci-dessus, `max` vaut 0,95 dès
la première seconde, c'est-à-dire la barre presque pleine que le ruling veut éviter. `min` garde l'intention :
c'est cette estimation au temps qui étale la barre sur la phase ; elle ne recule pas parce que `ProgressModel`
garde le maximum de ce qu'il a montré (propriété du calcul, testée). Que les deux termes du `min` soient eux-mêmes
croissants est une **supposition** pour `reload.getActualProgress()` : on l'a vu croître au relevé d'une ligne par
seconde du lancement A (cité ci-dessous ; un tel relevé ne verrait pas un recul bref entre deux lignes), sans
garantie lue dans le code de Mojang. La fraction ne dépasse pas l'avancement réel et reste sous 0,98 avant la fin réelle
(propriétés du calcul, couvertes par les tests ci-dessous). Sur un poste plus lent que celui de l'étalonnage, la
barre s'arrête à 98 % de la phase jusqu'à la fin ; plus rapide, la fin arrive avant l'estimation et la barre saute
à 100 %. `CATCH_UP` (3 barres entières par seconde) ne fait que borner la vitesse d'un saut : il franchit les
31 % de la phase en ≈ 0,1 s, ce n'est pas un lissage visible. Ces deux cas sont déduits du calcul, aucun autre
poste n'a été essayé.

Relevé du lancement A (`-PprogressTrace`), barre entière :
```
[  16.695 s] overlay du mod : rechargement à 0.703, barre à 0.686
[  17.697 s] overlay du mod : rechargement à 0.946, barre à 0.728
[  18.701 s] overlay du mod : rechargement à 0.979, barre à 0.770
[  19.701 s] overlay du mod : rechargement à 0.979, barre à 0.811
[  20.708 s] overlay du mod : rechargement à 0.979, barre à 0.853
[  21.819 s] overlay du mod : rechargement à 1.000, barre à 0.900
[  22.825 s] overlay du mod : rechargement à 1.000, barre à 0.942
[  24.287 s] overlay du mod : rechargement à 1.000, barre à 0.994
[  24.288 s] overlay du mod : rechargement fini en 7.66 s, fondu de la barre et du texte (1000 ms)
```
Avant (tâche 8), la barre était à ≈ 97 % dès la deuxième seconde. Le lancement B donne les mêmes valeurs à quelques
millièmes près (0,687 → 0,937, puis 0,994 à la fin). Tests : `ProgressModelTest` (quatre nouveaux, sur le relevé ci-dessus : temps plutôt qu'avance, jamais
devant le rechargement, plafond jusqu'à DONE, jamais de recul, barre < 85 % une seconde après le début des
ressources).

### Durées jusqu'à l'écran titre

Première ligne de `latest.log` → ligne du vérificateur Realms de l'écran titre.

| Configuration | Lancements | Durées | Remarque |
|---|---|---|---|
| Scène, sans film | 2 | 24,3 s (tâche 8, 13:21) ; 24,6 s (A) | moyenne 24,5 s |
| Scène avec film | 2 | 27,1 s (tâche 8, 13:17) ; 25,0 s (B) | le film lit les pixels et écrit des PNG |
| Écran de Forge (`fmlearlywindow`) | 1 | 23,8 s (tâche 8, 13:20) | |
| Overlay du mod en échec → overlay de Forge + pont | 1 | 29,7 s (C) | toutes les phases plus lentes, dès la découverte |

Écart scène / écran de Forge : **non établi**. Chiffres relevés : 24,3 et 24,6 s avec la scène sans film (deux
lancements), 23,8 s avec l'écran de Forge (**un seul** lancement). Or les démarrages avec la scène s'étalent de
24,3 à 29,7 s selon les lancements (≈ 5 s de dispersion, film et repli compris) : un écart de 0,5 à 0,8 s contre
un lancement unique ne dit rien du coût de la scène.
Le lancement C est plus lent dès la découverte des mods : entrée dans le chargement des mods (ligne
`phase MOD_LOADING` de `lostisland-earlywindow.log`, temps depuis l'ouverture de la fenêtre) à 15,14 s, contre
11,96 s (A) et 12,39 s (B), 11,74 s au lancement 3 de la tâche 8 :
```
C  [  15.139 s] [Render thread] phase MOD_LOADING : Tri des caisses de l'épave… (mods)
A  [  11.963 s] [Render thread] phase MOD_LOADING : Tri des caisses de l'épave… (mods)
B  [  12.393 s] [Render thread] phase MOD_LOADING : Tri des caisses de l'épave… (mods)
T8 [  11.743 s] [Render thread] phase MOD_LOADING : Tri des caisses de l'épave… (mods)
```
Le repli n'intervient qu'à 20,6 s : la cause de cette lenteur n'est pas établie, et sa durée ne mesure pas le coût
du repli.

### Jar de service

`build/libs/oneforallsurvival-earlywindow-0.1.0.jar` (= `run/mods/`) : **59 933 939 octets (57,2 Mio)**, 444
entrées. Tailles compressées : mer 27,1 Mio, plans du décor 19,0 Mio, effets (avion, feux, fumée…) 7,8 Mio, ciel
2,9 Mio, interface 0,1 Mio, classes 0,15 Mio, shaders négligeables. Aucune texture de Mojang : nuages et police
sont lus en place dans le jar du client.

### Masque du feuillage (vent)

Aucun `blender.exe` sur la machine (ruling 15) : les masques sont **dérivés des plans par la couleur**, pas
rendus par matériau. Plaque `<plan>_sun.png`, teinte HSV 55 à 150°, saturation > 0,30, alpha > 0,5, ouverture
1 px, fermeture 2 px, dilatation 2 px, flou gaussien σ 1,2. Couverture : lointain 1,5 %, milieu 43,5 %, premier
plan 0 % (masque noir voulu : sol et bûche seulement). Limite (ruling 17) : l'herbe des terrasses, du même vert
que les feuilles, est dans le masque du milieu ; l'herbe étant lisse, son ondulation ne se voit qu'aux bords des
terrasses. Mesuré en tâche 6 : avec / sans vent à 90 s, 56 720 pixels changent de plus de 8 niveaux, 100 % dans le
masque projeté. Un masque exact par matériau reste possible depuis `render_plates.py` (lanceur de rayons numpy qui
connaît les matériaux des feuilles).

### Comparaison à la vidéo de référence et à `compose.py`

Rendus hors jeu (`./gradlew sceneSnapshot`, 1920x1080) comparés à la vidéo et, en tâche 6, à des images sans perte
de `compose.py` (même script que la vidéo). Chiffres repris des rapports des tâches 3 à 6 :

- Tâche 3 (décor, ciel, mer, sans effets) : cadrage et ambiances conformes à t = 12, 22 et 33 ; écart restant à
  t=33 attribué au bloom, non porté alors.
- Tâche 4 (épave, feux, fumée) : zone de l'épave, moyenne RVB nous / vidéo : t=12 26/34/31 contre 27/34/30 ;
  t=22 64/46/39 contre 65/45/38 ; t=33 82/83/53 contre 83/83/52.
- Tâche 5 (avion, pluie, éclairs) : moyennes RVB nous / vidéo à 0,5 / 2 / 2,7 / 3,5 / 5 s à moins de 1,5 niveau
  près par canal (ex. 2,7 s : 28,9/50,5/59,8 contre 28,2/49,5/59,4) ; impact (4,4 s) 54 % saturé contre 65 %,
  faute de bloom.
- Tâche 6 (logo, barre, texte, vent, bloom) : écart absolu moyen à `compose.py` sans perte **0,51** (t=4,4),
  **1,77** (t=22), **2,66** (t=33) ; part saturée de l'impact 56,7 %, celle de `compose.py` ; logo à 3,2 niveaux de
  la vidéo (ciel derrière). Images : `build/scene-snapshots/pair_t6_*.png`.

En jeu, les images du film des tâches 7, 8 et 9 ont été regardées (ouverture depuis le noir, chute, épave, aube,
fin) : à l'œil, même scène que les rendus hors jeu, à l'endroit, du début à l'écran titre (aucune mesure d'écart
sur les images en jeu, qui sont en 854x480).

### Replis essayés en jeu

| Repli | Commande | Preuve |
|---|---|---|
| Échec avant notre fenêtre | `-PearlyWindowFail` (tâche 7) | `initialize : échec de notre fenêtre (… -Dlostisland.earlywindow.fail=true), repli sur le DisplayWindow de Forge`, `EARLYDISPLAY: Trying GL version 4.6` dans `latest.log`, titre atteint |
| Échec après notre fenêtre et la scène | `-PearlyWindowFail=late` (tâche 7) | `notre fenêtre est prête, thread de rendu lancé` puis `échec … =late, repli sur le DisplayWindow de Forge` à 0,58 s, `DisplayWindow de Forge prêt` à 0,69 s, titre atteint |
| Échec du thread de rendu | `-PearlyWindowFail=render` (tâche 7) | `thread de rendu arrêté` à 1,73 s, `repli sur Forge après l'échec du thread de rendu` à 3,93 s (fenêtre figée entre les deux), titre atteint |
| Jar de service absent | jar retiré de `run/mods` (tâche 7) | `Failed to find ImmediateWindowProvider lostisland, disabling` : **aucune** fenêtre anticipée (ni la nôtre ni celle de Forge), écran de Mojang, titre atteint (ruling 22) |
| Première image de l'overlay du mod en échec | `-PoverlayFail=first` (tâche 9, lancement C) | voir ci-dessous |

Lancement C, `lostisland-earlywindow.log` :
```
[  20.572 s] [Render thread] loadingOverlay : overlay du mod (dev.oneforallsurvival.client.loading.LostIslandLoadingOverlay)
[  20.627 s] [Render thread] overlay du mod : première image en échec (java.lang.IllegalStateException: échec demandé par -Dlostisland.overlay.fail=first), l'overlay de Forge prend la main
[  20.634 s] [Render thread] loadingOverlay : overlay de Forge, qui dessine notre scène
[  20.673 s] [Render thread] overlay de Forge : première image de la scène dans le contexte du jeu
[  20.673 s] [Render thread] phase RESOURCES : Exploration du littoral… (textures)
[  29.368 s] [Render thread] phase DONE : Bienvenue sur Lost Island.
[  31.343 s] [Render thread] overlay de Forge : fin du fondu, scène libérée
```
`latest.log` : `Sound engine started` 13:39:57.165, ligne Realms 13:39:58.045 (écran titre) ; 22 lignes
« exception », mêmes messages distincts qu'au lancement A (diff vide). Le journal montre que l'overlay de Forge
a pris la main et dessiné la scène par le pont jusqu'à la fin ; ce lancement n'avait pas de film et la fenêtre
n'a pas été regardée : la continuité à l'écran n'est pas vue. Dans ce repli, la barre des ressources suit les étapes de « Minecraft Progress » de
Forge, sans le lissage du ruling 26.

### Film

`run/film-chargement.mp4` (non commité) : lancement B, 634 images 854x480 écrites (1 277 rendues, une sur deux
retenue, 5 abandonnées faute d'écriture à temps), **25,20 s**, H.264, 30 i/s, 10,1 Mo. Les images ne sont pas
régulières : médiane 0,035 s entre deux, moyenne 25,2 i/s, et deux trous d'environ 0,8 s aux images 416-418 (le
passage Forge → ressources, ruling 27). Chaque image reçoit donc pour durée l'écart entre la date d'écriture de
son PNG et celle du suivant. Ce sont des dates d'écriture (deux threads, file d'attente), pas de capture : leur
décalage avec l'instant rendu n'est pas mesuré. Le film dure 25,20 s pour 25,15 s de scène au journal
(0,65 → 25,80 s) : sa durée totale est celle du chargement ; le rythme à l'intérieur suit les dates d'écriture,
saccade du passage comprise. Images vérifiées dans le film à
1, 4, 11, 17, 20, 23, 24,5 et 25,1 s : noir (attente des groupes, ruling 23), chute de l'avion, « Inventaire de la
soute… » 44 %, « Exploration du littoral… » 69 / 80 / 94 %, « Bienvenue sur Lost Island. » 100 %, aube sans barre
ni texte, logo gardé. La devise « Là où tout recommence. » n'apparaît pas : elle suit le temps du récit
(ruling 16, ≈ 29 s de récit) et le chargement finit avant sur ce poste.

### Tests

`./gradlew --offline test --rerun-tasks` : BUILD SUCCESSFUL ; XML : `earlywindowTest` 62 tests, 0 échec, 0 erreur ;
`test` (mod) 128 tests, 0 échec, 0 erreur.

### Non prouvé

- **Vieilles cartes graphiques** : un seul poste (RTX 4060, GL 4.6). Les versions GL 4.6 → 3.2 sont essayées
  comme chez Forge, mais aucun GL 3.2 réel, aucune carte intégrée ancienne, aucun pilote AMD ou Intel.
- **macOS et Linux** : jamais lancés. Le pont de la tâche 10 est propre à Windows (WGL) et ne s'active pas
  ailleurs : l'image s'y fige au passage comme avant (décision de `BridgePolicy`, testée sans GL, jamais vue sur
  ces systèmes). Le reste n'a pas d'appel propre à Windows relevé à la relecture, mais GLFW, contexte partagé et
  HiDPI s'y comportent autrement (macOS impose notamment le thread principal pour certaines opérations de fenêtre).
- **Écrans HiDPI** : seulement 854x480 à l'échelle 100 % ; `fbScale` est ignoré (rendu à la taille réelle du
  tampon), non vu sur un écran mis à l'échelle.
- **Jar du client en installation réelle** : trouvé par le classpath de développement (`client-extra.jar`) ; la
  recherche sous `libraryDirectory` (installation Forge du launcher) n'a jamais servi. S'il manque : nuages omis
  et police de repli (Monocraft), sans plantage, d'après le code seulement.
- **`onFinish` qui remplace l'overlay** (retour aux packs par défaut après une erreur de ressources) : chemin relu,
  jamais provoqué.
- **≈ 2 s de noir au départ** (ruling 23) : la fenêtre s'ouvre en 0,3 s mais le récit attend ciel, décor et effets
  (récit lancé entre 1,85 et 2,11 s aux cinq lancements avec la scène relevés en tâches 7 à 9). Accepté pour ce chantier ; piste : démarrer sur ciel + décor et ne retarder
  que l'avion.
- **Image figée au passage** des mods aux ressources (ruling 27) : ≈ 1,6 s avant la tâche 10 (images 416-418 du
  film B) ; avec le pont, ≈ 0,2 s sur ce poste (0,18 à 0,26 s à la jonction avec l'overlay selon le lancement, voir « Tâche 10 »), mais toujours ≈ 1,6 s quand le pont est coupé
  ou désactivé (≤ 2 cœurs, rendu logiciel, hors Windows, réglage `false`). Non élucidé : le relevé d'une ligne par seconde saute ≈ 1,4 s juste
  avant la fin du rechargement (22,8 → 24,3 s au lancement A, 23,3 → 24,7 s au lancement B), alors que les dates
  des images du film B n'y montrent aucun trou de plus de 0,18 s.
- **Overlay de Mojang au F3+T et au rechargement des ressources** : comportement vanilla, hors du chantier ; vu une
  fois par `-PreloadAfterTitle` (tâche 8), F3+T jamais pressé à la main.
- **Joueur sans le jar mais avec `fml.toml` réglé sur `lostisland`** : aucune fenêtre anticipée du tout, ni la
  nôtre ni celle de Forge (ruling 22). Le launcher maison doit livrer les deux ensemble ; un pack qui garderait
  `fmlearlywindow` montrerait l'écran de Forge.
- **Coût GPU du bloom et du vent** : non mesuré (aucun minuteur GPU). Seul indice, qui ne mesure pas ce coût : 1 277
  images ont été rendues en 25,2 s au lancement B (≈ 51 i/s de moyenne, film et passage compris, boucle
  plafonnée à 60 i/s) sur une RTX 4060. Les durées de démarrage ne permettent pas de conclure (voir « Durées »).
- **Nom du mod dans les libellés de Forge** (ruling 8) : jamais présent dans les barres réelles ; le détail affiché
  est « mods N/M ». Le format avec nom reste géré mais n'a jamais servi.
- **Écart scène / écran de Forge** : un seul lancement côté Forge, voir « Durées ».
- **Postes plus lents ou plus rapides** : l'étalonnage repose sur deux démarrages d'un seul poste ; ailleurs, les
  parts de la barre seront fausses si les durées relatives des phases diffèrent (la barre ne recule pas et
  n'annonce pas la fin avant elle : propriétés du calcul, couvertes par les tests, non vues sur un autre poste).
- **Masque du feuillage par matériau** : non fait (méthode par couleur, voir plus haut).

## Tâche 10 : saccade du passage aux ressources, film plein écran

Décision de Killian : garder un fil de dessin vivant après la remise de la fenêtre, avec un second contexte OpenGL
partagé, jusqu'à la première image de l'overlay des ressources. Code : `GapBridge` (le pont), `BridgePolicy`
(décision et réglage), `LostIslandWindow` (création, verrou avec `periodicTick`, arrêt au signal).

### Comment c'est fait, et pourquoi le jeu et le pont ne dessinent jamais ensemble

- Pendant le trou, le thread du jeu garde son contexte et s'en sert (constructeur de `Minecraft`, lu dans le code
  décompilé : textures, cibles, shaders de `TextureManager`, `GameRenderer`, `LevelRenderer`, `resizeDisplay`…) ;
  il ne vide ni ne présente la fenêtre avant `Minecraft.runTick`. On ne peut donc pas lui prendre son contexte.
- Le pont crée, sur le thread principal (`setupMinecraftWindow`, aucun contexte courant à ce moment), une fenêtre
  GLFW cachée dont le contexte partage les objets de celui du jeu (`glfwCreateWindow(…, share)`). Sur son thread, il
  rend ce contexte courant **sur la fenêtre du jeu** par WGL, avec son propre DC (`GetDCEx(DCX_CACHE)`), après avoir
  vérifié que les deux fenêtres ont le même format de pixel (`wglMakeCurrent` : « same device and same pixel
  format » ; « supplying each thread with its own rendering context and device context »). Windows seulement.
- Aucun appel GLFW sur le thread du pont : le rappel d'erreur de GLFW est global et celui de Minecraft exige le
  thread du jeu (premier essai, ci-dessous). `glfwCreateWindow` et `glfwDestroyWindow` (« main thread only ») sont
  appelés sur le thread du jeu, qui est celui de `glfwInit` ; la fenêtre cachée n'est détruite qu'après la fin du
  thread du pont (« must not be current on any other thread »). La taille de la fenêtre est lue par
  `GetClientRect` (`glfwGetFramebufferSize` est réservé au thread principal).
- Exclusion : chaque image (rendu + échange, et `glFinish` pour le pont) se fait sous un verrou commun au pont et à
  `periodicTick` (qui saute son image si le pont dessine). Le pont ne dessine que si aucune image n'est venue depuis
  150 ms ; le jeu reprend la main à son image suivante. Ordre GPU entre contextes : barrière `glFenceSync` + `glFlush`
  après chaque image du jeu, attendue par le pont ; `glFinish` à chaque image du pont.
- Fin (après la revue, voir « Correctifs de la revue ») : `loadingOverlay` demande l'arrêt et attend, 1 s au
  plus, que le pont ne puisse plus dessiner (verrou des images pris après la demande, ou thread fini), **avant** que
  la boucle du jeu ne vide le tampon de la fenêtre (`RenderSystem.clear` au début de `runTick`, avant l'overlay).
  Tant que ce n'est pas le cas, rien ne touche la scène : ni l'entrée en phase des ressources, ni les images de
  l'overlay (noir à la place), ni sa libération à la fin (fuite assumée). **Limite** : si le pont restait bloqué
  plus d'une seconde au milieu d'une image (pilote figé), la boucle du jeu présenterait quand même sa propre image
  (noire) : la fenêtre pourrait montrer un mélange ; aucun état ni objet partagé n'est touché par le jeu.
- Objets : le pont a son propre rendu (`EarlyScene.createLightRenderer`) : FBO, VAO, programmes, atlas de police
  créés dans son contexte et détruits par lui. Il ne lit que les textures de la scène et n'en détruit aucune.
- Garde-fous : (1) toute erreur (création, partage, format, rendu, échange) arrête le pont, l'image se fige comme
  avant ; les appels GLFW de la création (et la destruction de la fenêtre cachée) passent sous un rappel d'erreur
  local, sans quoi le rappel de démarrage de Minecraft (`Window.bootCrash`) ouvrirait une boîte « mettez à jour vos
  pilotes » et arrêterait le jeu ; (2) version allégée : ni bloom ni vent (`SceneRenderer(…, light = true)`), 30 i/s, priorité de thread
  abaissée ; (3) désactivé si `availableProcessors() ≤ 2`, GL < 3.2, rendu logiciel (GDI Generic, llvmpipe,
  softpipe, SwiftShader, Microsoft Basic Render), ou hors Windows ; (4) réglage ci-dessous.

### Réglage

`config/lostisland-earlywindow.properties`, créé au premier démarrage (UTF-8 sans BOM) :
```
bridgeThread=auto
```
`auto` (défaut), `false` (coupé : saccade d'avant), `force` (actif même sur une petite machine ; ne passe pas outre
la plateforme). La propriété `-Dlostisland.bridgeThread=auto|false|force` l'emporte (Gradle : `-PbridgeThread=…`).
Essai du repli : `-PbridgeThreadFail` (`-Dlostisland.bridgeThread.fail=true`), qui demande depuis la revue un
contexte OpenGL 99 : vraie erreur de GLFW à la création. Essai du pont en retard : `-PbridgeSlowStop=<ms>`
(`-Dlostisland.bridgeThread.slowStop`). Petit PC simulé : affinité du JVM du
jeu, ou `-PactiveProcessors=2` (`-XX:ActiveProcessorCount`, non utilisé ici). Tests sans GL : `BridgePolicyTest`
(9 : cœurs, version, rendu logiciel, réglage, fichier créé sans BOM, propriété prioritaire).

### Lancements (six, tous `./gradlew --offline installEarlyWindow runClient -PwithPack …`, arrêt par PID)

| # | Options | Résultat | Titre (1ʳᵉ ligne de `latest.log` → ligne Realms) |
|---|---|---|---|
| 1 | `-PsceneFrames=frames-saccade` | pont actif, puis **thread mort** à son arrêt (voir ci-dessous) ; ressources en noir, titre atteint | non retenu |
| 2 | `-PsceneFrames=frames-saccade` (correctif) | pont actif, arrêté au signal, overlay normal | 27,1 s (avec film d'images) |
| 3 | affinité 2 cœurs | pont **désactivé** par la règle | 69,9 s |
| 4 | affinité 2 cœurs, `-PbridgeThread=force` | pont actif | 72,7 s |
| 5 | `-PbridgeThreadFail` | second contexte refusé, comportement d'avant | 24,1 s |
| 6 | film plein écran (ffmpeg) | pont actif | 24,3 s |

Toutes : 22 lignes « exception » dans `latest.log`, le compte des lancements précédents (préexistantes).

**Lancement 1** : `pont : prend l'écran` puis plus rien ; `stdout` de Gradle :
`Exception in thread "lostisland-pont" java.lang.IllegalStateException: Rendersystem called from wrong thread`, levée
par `Window.defaultErrorCallback` de Minecraft depuis `glfwMakeContextCurrent(NULL)` appelé en fin de pont. Le thread
est mort sans signaler sa fin ; l'overlay a attendu sans dessiner la scène (« pas encore arrêté au signal »), la
phase des ressources est restée noire, sans plantage, titre atteint. Corrigé : plus aucun appel GLFW sur le pont
(WGL seul), fin signalée avant toute écriture au journal, et un thread mort est reconnu (`dead()`) pour que
l'overlay reprenne la main.

**Lancement 2**, `lostisland-earlywindow.log` :
```
[  12.517 s] [Render thread] pont : actif (16 cœurs, GL 4.6, NVIDIA GeForce RTX 4060/PCIe/SSE2, availableProcessors=16)
[  12.548 s] [Render thread] pont : second contexte créé (fenêtre cachée, GL 4.6, partagé avec celui du jeu)
[  12.549 s] [Render thread] setupMinecraftWindow : la fenêtre et son contexte passent à Minecraft
[  12.600 s] [lostisland-pont] pont : prêt (format de pixel 12, rendu allégé [halo, smoke, fire, embers, rain, bolts, crash, logo, ui] compilé en 0.02 s, 30 i/s, prise de l'écran après 150 ms sans image)
[  12.699 s] [lostisland-pont] pont : prend l'écran (aucune image depuis 150 ms)
[  12.807 s] [Render thread] pont : le jeu reprend l'écran après 3 images du pont
[  15.683 s] [Render thread] phase REGISTRIES : Le camp s'organise… (registres)
[  15.858 s] [lostisland-pont] pont : prend l'écran (aucune image depuis 151 ms)
[  17.263 s] [Render thread] pont : le jeu reprend l'écran après 38 images du pont
[  17.414 s] [lostisland-pont] pont : prend l'écran (aucune image depuis 150 ms)
[  18.003 s] [lostisland-pont] pont : thread terminé (57 images dessinées, 3 prises d'écran)
[  18.003 s] [Render thread] pont : arrêté au signal en 23.0 ms, 57 images dessinées en tout
[  18.031 s] [Render thread] loadingOverlay : overlay du mod (dev.oneforallsurvival.client.loading.LostIslandLoadingOverlay)
[  18.104 s] [Render thread] overlay du mod : première image de la scène dans le contexte du jeu, 854x480
```
Le trou n'est pas d'un seul tenant : le jeu ne dessine plus pendant ≈ 1,4 s au milieu des registres (15,86 →
17,26 s), redessine, puis se tait jusqu'à l'overlay (17,41 → 18,10 s). Le pont couvre aussi le petit trou de la
remise de la fenêtre (12,55 → 12,81 s).

**Écarts entre images** (dates d'écriture des PNG de `run/frames-saccade/`, 657 images, une sur deux retenue,
53 abandonnées faute d'écriture à temps) : de l'image 405 à l'overlay (images 413 à 428 dessinées par le pont),
plus grand écart **0,182 s** (image 422, la reprise du jeu puis du pont), environ 0,07 s entre deux images
retenues du pont. Le plus grand écart de tout le passage est **0,241 s** (image 438), une seconde après la première
image de l'overlay : c'est l'overlay lui-même, dessiné par le thread du jeu, lent au début du rechargement (déjà vu
en tâche 9 aux images 420-424) ; les images 438 et 439 sont écrites dans le désordre (deux threads d'écriture), la
mesure est donc grossière. Avant : deux trous de ≈ 0,8 s (≈ 1,6 s) aux images 416-418 du film B de la tâche 9.
Images regardées (planche des images 405, 412, 418, 422, 426, 428, 429, 432, 440) : même scène, même cadrage, à
l'endroit, barre 61 → 70 % sans recul, texte « Le camp s'organise… » puis « Exploration du littoral… » ; les
images du pont ont un peu moins de lueur autour des feux (pas de bloom).

**Petit PC simulé** : affinité du JVM du jeu fixée à 2 cœurs 20 ms après son apparition
(`ProcessorAffinity = 3`, relue 3). Vérifié avant, sur un petit programme Java : `availableProcessors()` passe de 16
à 2 dès que l'affinité change (Java 17, Windows) ; d'où l'affinité plutôt que `-XX:ActiveProcessorCount`.
```
lancement 3 [  33.858 s] [Render thread] pont : désactivé (2 cœurs logiques (≤ 2), availableProcessors=2)
lancement 4 [  34.615 s] [Render thread] pont : actif (forcé par le réglage (bridgeThread=force), 2 cœurs, GL 4.6, NVIDIA GeForce RTX 4060/PCIe/SSE2, availableProcessors=2)
lancement 4 [  46.264 s] [Render thread] pont : arrêté au signal en 31.5 ms, 77 images dessinées en tout
```
Durées jusqu'au titre : **69,9 s sans le pont, 72,7 s avec**, **un lancement chacun** ; l'écart (2,8 s, 4 %) n'est
pas établi au-delà du bruit d'un lancement unique. Sur 2 cœurs, le jeu ne dessinait plus pendant de longs moments
des mods (5 prises d'écran, dont 2,2 s au milieu des registres) : c'est exactement là que le pont prend du temps au
jeu, d'où la règle ≤ 2 cœurs gardée.

**Repli** (lancement 5) :
```
[  11.374 s] [Render thread] pont : actif (16 cœurs, GL 4.6, NVIDIA GeForce RTX 4060/PCIe/SSE2, availableProcessors=16)
[  11.403 s] [Render thread] pont : second contexte impossible, l'image se figera au passage comme avant : java.lang.IllegalStateException: échec demandé par -Dlostisland.bridgeThread.fail=true
[  11.403 s] [Render thread] setupMinecraftWindow : la fenêtre et son contexte passent à Minecraft
[  16.654 s] [Render thread] overlay du mod : première image de la scène dans le contexte du jeu, 854x480
```
Titre atteint. Ce mode (avant la revue) créait la fenêtre cachée puis la détruisait et levait : il n'exerçait pas
une vraie erreur de GLFW ; il le fait depuis (lancement 7). Un échec sur le thread du pont (format de pixel,
`wglMakeCurrent`, rendu) n'est que relu ; la mort du thread du lancement 1 a montré que l'overlay ne dessine pas à
deux et que le jeu va au bout.

### Film plein écran (lancement 6)

`run/film-plein-ecran.mp4` (non commité) : ffmpeg `ddagrab` 60 i/s, `h264_nvenc` `-cq 19`, démarré juste avant
Gradle, arrêté par `q` 3 s après le titre. ffprobe : `h264`, **2560x1440**, `r_frame_rate=60/1`,
`avg_frame_rate=60/1`, 2 481 images, **41,35 s**, 56 796 951 octets (ffmpeg : 59 images dupliquées, 5 abandonnées).
La fenêtre du jeu est en 854x480 au milieu du bureau ; **le bureau de Killian est visible** autour.
Images regardées : 0,5 et 11,5 s (bureau seul, pas encore de fenêtre) ; une image toutes les 0,25 s de 13,5 à
17,5 s, recadrée sur la fenêtre : la fenêtre s'ouvre noire vers 13,8 s, la scène sort du noir vers 15,5 s, éclair
et chute, **aucun écran de Forge ni de Mojang** ; 20 et 26,5 s (chute, épave, barre des mods) ; une image toutes les
0,25 s de 27,5 à 31,5 s (le passage) : barre 59 → 72 %, « Tri des caisses… » → « Le camp s'organise… » →
« Exploration du littoral… », ciel et nuages qui bougent, pas d'image répétée visible ; 33, 37 s (ressources,
aube) ; 40,5 s (écran titre de Minecraft).
Immobilité mesurée (`freezedetect`, zone de la fenêtre, -60 dB, ≥ 0,1 s ; une image presque identique compte comme
immobile) : au passage, 0,13 et 0,12 s aux deux prises d'écran du pont (le seuil de 150 ms), **0,20 s** entre
l'arrêt du pont et les premières images de l'overlay, puis 0,18 et 0,22 s pendant le début du rechargement (overlay
dessiné par le jeu) ; 0,22 s à la remise de la fenêtre. Ailleurs : **0,57 s juste avant la fin du rechargement**
(film 36,9 → 37,5 s, journal ≈ 23,4 → 24,0 s, avant `phase DONE`) : c'est l'overlay du jeu, hors du pont ; il
recoupe le saut d'une seconde et demie du relevé par seconde noté en tâche 9, toujours non élucidé.

### Tests

`./gradlew --offline test --rerun-tasks` : BUILD SUCCESSFUL ; XML : `earlywindowTest` 72 tests (62 + 10 de
`BridgePolicyTest`, après la revue), 0 échec, 0 erreur ; `test` (mod) 128 tests, 0 échec, 0 erreur.

### Non prouvé (tâche 10)

- Un seul poste (RTX 4060, pilote NVIDIA 621.14) : deux contextes sur une même fenêtre par deux DC, et un DC du
  cache pour OpenGL, ne sont vus que sur ce pilote. Sur un autre (AMD, Intel), un échec de `wglMakeCurrent` ou du
  format de pixel retombe sur la saccade ; un pilote qui accepterait mais dessinerait mal n'est pas exclu.
- Échec sur le thread du pont (format, `wglMakeCurrent`, rendu) : chemin relu ; seuls l'échec de création
  (lancements 5 et 7) et la mort du thread (lancement 1, avant correctif) ont été vus.
- Pont bloqué plus d'une seconde dans le pilote : simulé par un sommeil sous le verrou (lancement 9), pas par un
  vrai pilote figé ; dans ce cas réel, voir la limite de « Fin » plus haut.
- Coût sur 2 cœurs : un lancement avec, un sans (voir plus haut).
- Démarrage très long : au-delà de 5 s sans que le thread du jeu traite les messages, Windows peut remplacer la
  fenêtre par son image « ne répond pas », que le pont ne peut empêcher ; non observé (trous ≤ 2,2 s ici).
- Un mod qui dessinerait dans la fenêtre pendant le trou (événements du constructeur de `Minecraft`) : aucun vu ;
  ce ne serait qu'un défaut d'image.

### Correctifs de la revue (trois lancements de plus : 7, 8, 9)

Revue : `.superpowers/sdd/…/task-10-review.md` (non commitée). Corrigé :

- **Boîte modale au lieu du repli (I1)** : `createBridge` tourne pendant le constructeur du `Window` de Minecraft,
  sous son rappel d'erreur de démarrage (`Window.bootCrash` : boîte « GLFW error … update your drivers » puis
  `WindowInitFailed`). Tous les appels GLFW de `GapBridge.create` (indices, `glfwCreateWindow`, poignées, destruction
  sur échec) passent maintenant sous un rappel local qui note l'erreur, l'ancien est remis dans un `finally` ; de
  même pour la destruction de la fenêtre cachée. Les indices de fenêtre sont remis par défaut même sur exception
  (M3), et la version est lue avant de les toucher. Le mode d'essai échoue désormais par une **vraie** erreur de GLFW
  (contexte OpenGL 99 demandé).
- **Pont en retard (I2)** : l'arrêt n'attend plus seulement la fin du thread mais que le pont **ne puisse plus
  dessiner** (`GapBridge.stopAndQuiesce` : verrou des images pris après la demande d'arrêt ; le pont relit l'arrêt
  sous ce verrou avant chaque image). Tant que ce n'est pas le cas : `loadingOverlay` n'entre pas en phase des
  ressources (la première image de l'overlay le fait), l'overlay du mod et celui de Forge ne dessinent pas la scène
  et **ne la libèrent pas** à la fin (textures gardées plutôt que détruites sous le pont). Attente initiale portée à
  1 s. Limite écrite plus haut (« Fin ») : un pont figé plus d'une seconde au milieu d'une image laisse la boucle du
  jeu présenter son image.
- M1 : si la barrière du jeu n'est pas franchie en 50 ms, le pont saute son image (journalisé une fois). M2 : la
  barrière n'est plus perdue si le pont s'arrête sans contexte (supprimée par le jeu). M6 : deux tests de plus
  (version « 3.2.0 » / « 4.6 » avec espaces ; une propriété reconnue ne crée pas le fichier, désormais écrit dans la
  javadoc de `resolve`). M4 (essai d'échec sur le thread du pont) et M5 (`-PactiveProcessors`, gardé) : non faits.

**Lancement 7** (`-PbridgeThreadFail`, vraie erreur GLFW) :
```
[  11.444 s] [Render thread] pont : actif (16 cœurs, GL 4.6, NVIDIA GeForce RTX 4060/PCIe/SSE2, availableProcessors=16)
[  11.472 s] [Render thread] pont : second contexte impossible, l'image se figera au passage comme avant : java.lang.IllegalStateException: second contexte non créé (glfwCreateWindow, erreur GLFW : 0x10007 WGL: Driver does not support OpenGL version 99.6, essai demandé par -Dlostisland.bridgeThread.fail=true)
[  11.473 s] [Render thread] setupMinecraftWindow : la fenêtre et son contexte passent à Minecraft
[  16.344 s] [Render thread] overlay du mod : première image de la scène dans le contexte du jeu, 854x480
```
`0x10007` est `GLFW_VERSION_UNAVAILABLE`, levée par WGL. Aucune ligne « GLFW error » ni `WindowInitFailed` dans
`latest.log` ni dans la sortie de Gradle ; titre atteint à 23,9 s (14:34:59.366 → 14:35:23.275) : `bootCrash` n'a
pas été appelé (il aurait arrêté le jeu). La boîte modale elle-même n'a pas été regardée à l'écran ; son absence se
déduit du titre atteint.

**Lancement 8** (`-PsceneFrames=frames-saccade`, normal) : pont actif, trois prises d'écran (13,73 ; 16,97 ;
18,49 s), `pont : ne dessine plus (signal attendu 0.0 ms), 52 images dessinées en tout` à 19,006 s, overlay à
19,136 s ; titre à 27,3 s (film d'images) ; 22 lignes « exception ». Écarts des dates d'écriture des PNG
(664 images) : pendant les images du pont ≤ 0,168 s ; **0,259 s** entre la dernière image retenue du pont
(image 448) et la première retenue de l'overlay (449). Au journal, l'overlay dessine 0,13 s après l'arrêt du pont ;
l'écart écrit est plus long parce qu'une image sur deux seulement est gardée et que les premières images de
l'overlay (thread du jeu, début du rechargement) viennent à 0,1-0,2 s d'intervalle (0,203 et 0,179 s aux images 453
et 454). Le critère « < 0,2 s dans les temps d'écriture » est donc tenu pendant le pont mais **pas** à la jonction
avec l'overlay sur ce lancement (0,182 s au lancement 2) : c'est la cadence de l'overlay du jeu, pas un arrêt du
pont. Planche des images 436 à 458 regardée : même scène, barre 64 → 70 %, « Le camp s'organise… » puis
« Exploration du littoral… ».

**Lancement 9** (`-PbridgeSlowStop=2500`, pont en retard simulé), troisième lancement, nécessaire : la preuve du
retard ne pouvait pas partager le lancement 7 (pas de pont) ni le 8 (le retard aurait faussé la mesure des écarts) :
```
[  17.097 s] [lostisland-pont] pont : essai du pont en retard, verrou gardé 2500 ms
[  18.096 s] [Render thread] pont : pas encore arrêté au signal, l'overlay attend sans toucher la scène
[  18.111 s] [Render thread] loadingOverlay : overlay du mod (dev.oneforallsurvival.client.loading.LostIslandLoadingOverlay)
[  19.598 s] [lostisland-pont] pont : thread terminé (135 images dessinées, 1 prises d'écran)
[  19.600 s] [Render thread] pont : ne dessine plus (signal attendu 0.0 ms, après un retard), 135 images dessinées en tout
[  19.614 s] [Render thread] phase RESOURCES : Exploration du littoral… (textures)
[  19.617 s] [Render thread] overlay du mod : première image de la scène dans le contexte du jeu, 854x480
[  26.031 s] [Render thread] overlay du mod : scène libérée
```
`loadingOverlay` a attendu 1 s puis continué sans toucher la phase ; l'overlay n'a dessiné la scène qu'une fois le
pont arrêté ; la phase des ressources est entrée par l'overlay ; titre atteint (25,4 s), 22 lignes « exception ».
Effet de l'essai lui-même : pour rester bloquable, le pont y garde le verrou entre ses images, si bien que le jeu ne
l'a jamais repris pendant les mods (une seule prise d'écran, 135 images allégées) ; ce comportement n'existe que
dans ce mode. L'image (≈ 2,5 s figée puis noire) n'a pas été filmée.
