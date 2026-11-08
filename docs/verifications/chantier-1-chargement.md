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
c'est cette estimation au temps qui étale la barre sur la phase ; elle ne recule pas (deux termes croissants, et
`ProgressModel` garde le maximum), ne dépasse pas l'avancement réel et reste sous 0,98 avant la fin réelle
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
Le lancement C est plus lent dès la découverte des mods (fenêtre → « Loading mods » 15,1 s contre 11,7 à 12,4 s
aux lancements A et B), alors que le repli n'intervient qu'à 20,6 s : la cause de cette lenteur n'est pas établie,
et sa durée ne mesure pas le coût du repli.

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
- **macOS et Linux** : jamais lancés. Aucun appel propre à Windows relevé à la relecture du code, mais GLFW, contexte partagé et HiDPI s'y
  comportent autrement (macOS impose notamment le thread principal pour certaines opérations de fenêtre).
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
- **≈ 1,6 s d'image figée au passage** des mods aux ressources (ruling 27) : le thread du jeu est occupé, personne
  ne dessine. Vu dans le film (images 416-418). Non élucidé : le relevé d'une ligne par seconde saute ≈ 1,4 s juste
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
