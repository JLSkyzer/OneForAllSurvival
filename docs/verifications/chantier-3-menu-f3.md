# Vérifications du chantier 3 : menu F3 Lost Island

Relevés du 2026-11-08, sur `main`. Spec : `docs/superpowers/specs/2026-11-08-interface-lost-island-design.md`
(chantier 3) ; plan : `docs/superpowers/plans/archive/2026-11-08-chantier-3-menu-f3.md`.

Poste de tous les lancements : celui de Killian, Windows 11, RTX 4060, pack complet (`-PwithPack`), Forge 47.4.28.
Les preuves en jeu viennent du scénario `F3Shots` (devcontent, jamais empaqueté : aucune classe `dev/` dans
`build/libs/oneforallsurvival-0.1.0.jar`) : journal préfixé `[f3-essai]`, images PNG, `termine.json`. Chaque client
s'est arrêté seul à la fin du scénario (code 0) ; aucune autre JVM n'a été touchée.

## Commandes

- Français : `./gradlew --offline runTitleFr -PwithPack -PdebugShots=frames-f3-fr` (`run-titre/`, `lang:fr_fr`).
- Anglais : `./gradlew --offline installEarlyWindow runClient -PwithPack -PdebugShots=frames-f3` (`run/`, `en_us`).
- Film (`run-titre/`) : `./gradlew --offline runTitleFr -PwithPack -PdebugShots=film-f3 -PdebugShotsFilm
  "-PgameArgs=--width 2560 --height 1440"`, avec ffmpeg 9.0.2 lancé dès que la fenêtre existe (voir « Film »).
- `./gradlew --offline test --rerun-tasks` : BUILD SUCCESSFUL ; XML : `test` 244 tests, `earlywindowTest` 90 tests,
  0 échec, 0 erreur, 0 ignoré. `./gradlew --offline runGameTestServer` : `All 345 required tests passed :)`.

## `run/options.txt`

SHA-256 relevée avant et après le lancement dans `run/`, puis après le film et les tests :
`012d88e54818f9da5335b6fdb96a9fc6787e10c0d12555e6a1ebc6d07dfa5adc`, identique à celle des chantiers 1 et 2.
`F3Shots` ne change `pauseOnLostFocus` et `tutorialStep` qu'en mémoire et ne presse jamais F3 + H ni F3 + P, qui
appellent `Options.save` (lu au javap de `KeyboardHandler.handleDebugKeys`, cas 72 et 80).

## Comment les touches sont passées

`WM_KEYDOWN` / `WM_KEYUP` postés à la fenêtre du jeu (`User32.PostMessage`, code virtuel et code de balayage réels).
GLFW les reçoit par sa boucle de messages : `glfwGetKey` les voit, `KeyboardHandler.keyPress` et `InputEvent.Key` suivent
le chemin normal. Aucun appel direct de `DebugKeys` ni de `DebugHud`. C'est le chemin logiciel complet **à partir de la
boucle de messages de la fenêtre** ; ce n'est pas un vrai doigt sur un clavier. Le survol du titre passe, comme au
chantier 2, par le rappel de position de GLFW.

## Lancements : français et anglais

Premier lancement français : 2 échecs, `compteur=null`. **Défaut du scénario** : le porte-armure posé par
`F3Shots` était entre les yeux et le compteur, le F3 visait donc l'entité (image regardée). Déplacé derrière la rangée
de blocs. Ce même lancement a montré le chat par-dessus la dernière ligne de la carte Lost Island, et l'infobulle du
tutoriel sur le bas de la carte Performances (voir « Constats ») ; le scénario vide le chat et arrête le tutoriel
ensuite pour lire les cartes.

Lancements retenus : français (bilan à 20:13:47) et anglais (bilan à 20:16:14). `termine.json` : fin normale, **0 échec, 26 contrôles, 22 images**
chacun ; 0 ligne `GlDebug` dans `latest.log` (les deux, et aussi après le film).

Contrôles (extraits du journal français) :

```
PASS f3-ouvre : renderDebug=true
PASS ingenieur-usure : Machine[state=STOPPED, coastal=false, wearPercent=25.0, failure=null], opérateur=false
PASS citoyen-sans-usure : Machine[state=STOPPED, coastal=false, wearPercent=null, failure=null], rôle=citoyen, opérateur=false
PASS citoyen-compteur : Meter[subscriber=Mairie, fePerTick=0, index=null]
PASS ingenieur-compteur : Meter[subscriber=Mairie, fePerTick=0, index=0]
PASS disjoncteur : Breaker[closed=true, sealed=false]
PASS reduit-actif : réduit=true, cache=null
PASS tab-garde-f3-1 à 5 : renderDebug=true, liste des joueurs=false, vue 1/4, 2/4, 3/4, 4/4, 0/4
PASS f3-b-oui : hitbox=true, F3 ouvert=true ; PASS f3-b-non : hitbox=false, F3 ouvert=true
PASS graphes : renderDebug=true, renderFpsChart=true
PASS f1 : hideGui=true
```

Rôle réglé par `/role set Dev <rôle>` sur le serveur intégré ; monde créé **sans triche** (le propriétaire n'est pas
opérateur, sinon `RoleGate` le traite en équipe : `opérateur=false` au journal). `/gamerule reducedDebugInfo`, `/time`
de même. `F3 + Tab` : relâcher F3 ne referme pas le F3 (`renderDebug=true` à chaque page) ; la réflexion
`handledDebugKey` a réussi (0 ligne `handledDebugKey` dans `latest.log`, où l'échec journaliserait un avertissement) ; la liste des joueurs reste fermée.

### Images regardées (22 + 22)

`run-titre/frames-f3-fr/` et `run/frames-f3/`, 854x480 (échelle 2) sauf 24 à 26. Toutes les images des deux dossiers ont
été ouvertes ; ce qui suit vaut pour les deux langues sauf mention.

- `02-f3-jour-machine-ingenieur.png` : cartes Position (X rouge, Y vert, Z bleu), Monde, Lost Island à bord bleu
  (« Machine : Dessalinisateur · à l'arrêt », « Usure : 25 % ») ; Performances (FPS en grand, petit graphe, mémoire et
  barre, TPS) ; Cible (identifiant coupé par « … », état, distance). Titres ambrés. Rien de coupé hors de l'identifiant.
- `04-f3-machine-citoyen.png` : la même sans la ligne d'usure.
- `05` / `06` (compteur) : « Abonné : Mairie » et « Débit : 0 FE/t » pour les deux ; « Index : 0 FE » seulement en Ingénieur.
- `07-f3-disjoncteur.png` : « Disjoncteur : fermé ».
- `03-f3-nuit.png` : cartes lisibles sur la nuit. Sur le ciel clair (`02`), la carte du haut prend la teinte du ciel
  (fond translucide) : texte lisible, rendu plus gris-bleu que la maquette.
- `08-f3-reduit.png` : ni Monde, ni Cible, ni Lost Island ; Position réduite à « Chunk relatif » ; Performances reste.
  `09` : tout revient.
- `11` à `14-detail-page-*.png` : quatre pages (Position, Monde, Lost Island ; Mods avec les lignes de Flywheel ; Détails
  de Minecraft ; Performances et Cible), lignes brutes coupées à la largeur, pastille « 1/4 » à « 4/4 ».
- `20-f3-b-hitbox.png` : boîte du porte-armure dessinée, message « Boîtes de collision : visibles », F3 resté ouvert.
- `21-f3-ferme.png`, `01-sans-f3.png`, `27-fin-sans-f3.png` : barre d'objets, chat et viseur normaux, aucun
  artefact après la fermeture du F3. `90-retour-titre.png` : le titre et sa scène, normaux, au retour du monde.
- `22-f3-graphes.png` : deux cartes « Temps d'image » et « Temps de tick » en bas, repères 30/60 FPS et 20 TPS,
  pastille remontée au-dessus. **M-5 regardé** : les objets de la barre sont dessinés **par-dessus** les cartes des
  graphes (le pic, l'épée, etc. restent visibles, sauf le fond des cases, couvert à cette échelle). Le chat passe
  devant aussi.
- `23-f1-f3-ouvert.png` : aucun panneau, ni barre ni viseur.
- `24-echelle-3-1280x720.png`, `25-echelle-4-1920x1080.png`, `26-echelle-4-detail.png` : cartes lisibles et titres nets
  aux échelles 3 et 4.

## Film final (Killian)

`run-titre/film-final.mp4` : h264, 2560x1420 (`run-titre/film-final-720p.mp4` : 1280x710, 7,7 Mo), 6 442 images,
**128,05 s**, 236 Mo ; `avg_frame_rate` 50,3 i/s (capture à cadence variable, plafond 60). Le dernier fragment (fenêtre
fermée, gris foncé, 2 s) est retiré par coupe sans réencodage. Photos : `run-titre/photos-finales/` (8 PNG, 2560x1420,
regardées : crash, aube avec la barre, titre, survol, F3 normal, F3 de nuit, F3 détaillé, graphes). Le scénario du film :
0 échec, 22 contrôles.

- **Capture** : `gfxcapture` de ffmpeg 9.0.2, sur le HWND de la fenêtre (`hwnd=…`, relevé par `Get-Process`), avec
  `capture_cursor=0`, `h264_nvenc -preset p5 -cq 18`. Attention : ne pas forcer `-pix_fmt yuv420p` (les trames sont en
  `d3d11`, ffmpeg refuse la conversion ; essai observé) ; le codeur convertit seul.
- **Plein écran : pas filmable ainsi.** Essai `--fullscreen --width 2560 --height 1440` (`run-titre/film-essai-plein-ecran.mp4`,
  10,4 s, 543 images) : les images s'arrêtent à la bascule en plein écran (10,4 s après le début de la capture, « Backend
  library » du jeu 10,8 s après l'apparition de la fenêtre), ffmpeg tourne encore 2 min sans rien produire. Le scénario du jeu est passé à
  0 échec, mais le rendu en plein écran n'a donc pas été vu. Repli demandé : **fenêtre** `--width 2560 --height 1440`.
  Windows la ramène à 2560x1421 de zone cliente (journal de la fenêtre ; échelle d'interface automatique 5 d'après le calcul de Minecraft, soit 512x284), filmée sans
  redimensionnement (`width=-2:height=-2`).
- **Rien d'autre que la fenêtre** : les images regardées (2,4 premières secondes, 22,2 à 23,8 s, 20 à 26 s, toutes les 8 s,
  1,5 s autour de l'ouverture du F3, fin) ne montrent que le jeu, jamais le bureau.

### Ce que le film montre sur les deux points jamais vus

- **Passage chargement → titre** : images à 0,1 s d'écart de 22,2 à 23,8 s du film. L'aube continue sans coupure
  (mêmes nuages, soleil qui monte, fumée de l'épave) ; barre et texte « Bienvenue sur Lost Island. » s'estompent, puis
  boutons et devise apparaissent en fondu sur la même scène. Pas de noir ni de rupture visible à ce pas, et aucune
  image à changement brutal détectée par le filtre `scene` (`gt(scene,0.15)`) entre 11,8 et 42,3 s ; le seul seuil franchi à 42,3 s
  est l'entrée dans le monde. Prouvé à 10 i/s d'échantillonnage, pas image par image sur 60 i/s.
- **Fondu d'arrivée de la barre** : images à 0,2 s d'écart. À 0 s : noir. À 0,2 s : texte et barre à mi-opacité, légèrement
  plus bas ; à 0,4 s : opaques, en place, le pourcentage court déjà. La scène de la mer n'arrive qu'à partir de ≈ 1,6-2 s
  (journal : « mer prête à 2.27 s (fondu de 0.8 s) »). Vu au tout début du film : **fondu vu, de bout en bout**, à 0,2 s d'écart.

### Ouverture filmée du F3 (film ≈ 55,1 à 56,5 s, images à 12 i/s)

Les cartes arrivent l'une après l'autre (Position, Monde, Cible…), en fondu et petit glissement. Les FPS descendent de
118 à 113 par pas sur plusieurs images (chiffre lissé, pas de saut).

### Combien du début a-t-on perdu ?

Estimation, pas une mesure directe : fenêtre vue par la boucle d'attente à 20:22:51.889, ffmpeg lancé 34 ms après. Le fondu
de la barre commence à 0,79 s du journal de la fenêtre et au tout début du film (≈ 0,2 s), soit un début de film à ≈ 0,6 s
de ce journal ; la fenêtre, elle, est créée à 0,33 s (noire). **≈ 0,2-0,3 s perdues**, toutes sur un écran noir ; la barre et
le récit sont intégralement dans le film.

## Constats à trancher (rien corrigé ici, consigne)

1. **Pastille « Vue détaillée… » sur la barre d'objets, cosmétique.** En page détaillée, à 427 et 480 px d'interface
   (`11-detail-page-1.png`, `26-echelle-4-detail.png`), la pastille déborde sur la dernière case (texte « Détaillée 1/4 »
   mangé par le cadre). En vue normale, elle touche presque la case (`02`).
2. **Chat et toasts passent devant les cartes.** Vu au premier lancement : le chat couvre le bas de la carte Lost Island ;
   l'infobulle du tutoriel couvre le bas de Performances. Ordre des couches de Minecraft, pas comparé au F3 vanilla.
3. **Graphes et barre** : voir M-5 plus haut (objets au-dessus, fond de cases couvert à l'échelle 2). Pas comparé au vanilla.
4. Cible : l'identifiant du bloc est coupé (« … ») à 427 de largeur (`oneforallsurvival:de…`), l'identifiant complet est
   dans la vue détaillée (`14-detail-page-4.png`).

## Non prouvé

- **Plein écran réel** : la bascule s'est faite (log, 0 échec) mais aucun rendu vu. Fenêtre maximale 2560x1421 filmée à la place.
- **Vraie frappe au clavier** : touches postées à la fenêtre (chemin GLFW réel), pas un clavier physique. Survol par le rappel GLFW.
- **Client de production** (jar réobfusqué) : `f_90873_` utilisé en dev seulement ; repli F9 jamais exercé.
- **Serveur dédié et multijoueur** : ligne de ping, Citoyen sur un vrai serveur, abonné/compteur avec un vrai flux, deux joueurs.
- **Machine en panne, usure élevée, zone d'eau salée « oui », disjoncteur ouvert ou scellé, débit > 0** à l'écran (seules
  les valeurs ci-dessus ont été vues ; les autres couvertes par GameTests et tests de `IslandLines`).
- **F3 + Shift (camembert), F3 + G et autres combinaisons** : pas essayées, sauf F3 + B et F3 + Alt. F3 + H et F3 + P : jamais
  pressées (`Options.save`).
- **Ligne d'un autre mod** : carte Mods en vue normale vue dans le film seulement (fenêtre 2560x1421) ; à 854x480 elle passe en vue détaillée ; seul Flywheel y met des lignes dans ce pack.
- **Glissement des cartes et chiffres lissés** : vus à 12 i/s dans le film, pas mesurés.
- **Autres tailles** : 854x480, 1280x720, 1920x1080, fenêtre 2560x1421 ; pas de 4K, HiDPI, échelles 1 ni 6, très petites fenêtres.
- **Coût en images du F3** : pas mesuré (les FPS affichés montrent ≈ 113-119 sans comparaison à F3 fermé).

## Note

`gfxcapture` marche pour une fenêtre ; il ne filme pas la bascule en plein écran exclusif (essai ci-dessus). À reporter à
la note BDC « Pour filmer tout l'écran… », par le contrôleur.
