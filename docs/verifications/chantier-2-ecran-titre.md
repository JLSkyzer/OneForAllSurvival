# Vérifications du chantier 2 : écran titre Lost Island

Relevés du 2026-11-08, sur `main`. Spec : `docs/superpowers/specs/2026-11-08-interface-lost-island-design.md`
(chantier 2) ; plan : `docs/superpowers/plans/2026-11-08-chantier-2-ecran-titre.md`.

Poste de tous les lancements : celui de Killian, Windows 11, RTX 4060, pack complet (`-PwithPack`), Forge 47.4.28,
fenêtre 854x480, échelle d'interface automatique (427 x 240 px d'interface). Les preuves en jeu viennent du
scénario `TitleShots` (devcontent) : journal préfixé `[titre-essai]`, images PNG de la fenêtre du jeu, `termine.json`.

## Commandes

- Serveur local : `./gradlew --offline runServer -PwithPack` (`run-server/`, `online-mode=false`,
  `server-ip=127.0.0.1`, port 25565), lancé en tâche de fond, prêt à « Done », arrêté par la commande `stop`
  envoyée en RCON (sauvegarde normale, PID vérifié absent ensuite).
- Lancement 1 : `./gradlew --offline installEarlyWindow runClient -PwithPack -PtitleShots=frames-titre
  -PtitleShotsServer=127.0.0.1:25565`.
- Lancement 2 : `./gradlew --offline runTitleFr -PwithPack -PtitleShots=frames-titre-fr` (`prepareTitleRun`
  s'exécute avant, `run-titre/`, `lang:fr_fr`).
- Lancement 3 : `./gradlew --offline runClient -PwithPack -PtitleShots=frames-titre-sansjar`, jar de service
  sorti de `run/mods` le temps du lancement puis remis (empreintes identiques avant/après, `fml.toml` inchangé :
  `67e7eb3c…`).
- Lancement 4 (film) : ffmpeg 9.0.2 `ddagrab` 60 i/s, `h264_nvenc -preset p4 -cq 19`, démarré avant
  `./gradlew --offline installEarlyWindow runClient -PwithPack -PtitleShots=frames-film`, arrêté par `q` 3 s
  après le survol.
- Chaque client s'est arrêté seul (fin du scénario, code de sortie 0), sauf le premier essai, arrêté par un crash
  (ci-dessous). Aucune JVM laissée ouverte, aucune autre JVM touchée.

## `run/options.txt` (décision 7)

SHA-256 relevée avant et après chaque lancement qui utilise `run/` (cinq lancements : premier essai, essai
`localhost`, lancement 1, 3 et 4) : **toujours** `012d88e54818f9da5335b6fdb96a9fc6787e10c0d12555e6a1ebc6d07dfa5adc`.
Le lancement 2 tourne dans `run-titre/`. Hors `run-titre`, `TitleShots` saute Options (« Options sauté (dossier de
Killian) »).

## Premier essai : crash au retour d'un monde (corrigé)

Le premier lancement (17:12) a planté le client au premier rendu de la scène recréée après un monde :
`EXCEPTION_ACCESS_VIOLATION` dans `nvoglv64.dll`, pile `GL11C.nglTexImage2D` ← `FontAtlas.ensure()` ←
`LogoEffect.render` ← … ← `LostIslandTitleScreen.render` (`run/hs_err_pid21376.log`). Cause prouvée par un lancement
instrumenté : dans le contexte de Minecraft, `GL_UNPACK_SKIP_ROWS` valait 90 ; l'atlas ne réglait que l'alignement et
le pilote lisait au-delà du tampon. Correctif `dd36e20` (`PixelUnpack` : état de lecture neutre autour de chaque envoi
depuis la mémoire du processus), qui fait aussi disparaître 5 lignes `GL_INVALID_OPERATION` vues à l'entrée dans le
monde. Rapport du correctif : `.superpowers/sdd/2026-11-08-chantier-2-ecran-titre/fix-crash-report.md` (non commité).
Depuis : 0 ligne `GlDebug` dans `latest.log` aux lancements 1, 2 et 4 (`run-titre/logs/latest.log` pour le 2).

Dans ce même essai, deux ouvertures de la liste des mods hors scénario étaient des clics de Killian dans la fenêtre
(confirmé par lui), pas un défaut.

## Lancement 1 : titre, survol, mods, monde, serveur (17:35)

Un essai juste avant avec `-PtitleShotsServer=localhost:25565` a tout passé sauf la connexion :
`Connection refused: getsockopt: localhost/[0:0:0:0:0:0:0:1]:25565`. `localhost` s'est résolu en IPv6 alors que le
serveur n'écoute que sur `127.0.0.1` (`server-ip`). Réglage d'essai, pas défaut du mod : relancé avec `127.0.0.1`.

Journal (`termine.json` : fin normale, 0 échec, « bilan : TOUT PASSE, 12 image(s) ») :

```
[avant le titre, +14.983 s] scène : null -> LOADING_OVERLAY
[avant le titre, +15.965 s] scène : LOADING_OVERLAY -> TITLE
[titre +0.002 s] PASS titre-affiche : …LostIslandTitleScreen, 16.0 s après le début du jeu, scène TITLE
[titre +1.020 s] PASS widgets-debut : 4 boutons à nous (attendu 4), 0 widget(s) étranger(s) visibles
[titre +30.528 s] position du curseur non reçue par le jeu après glfwSetCursorPos : rappel GLFW appelé directement
[titre +31.020 s] PASS survol-rejoindre : survolé=true, infobulle « Server coming soon », souris du jeu (192, 252), par le rappel GLFW
[titre +31.031 s] PASS 06-options : sauté (dossier de Killian, pas run-titre)
[titre +33.054 s] PASS 09-retour-mods : …LostIslandTitleScreen, même instance qu'avant, scène TITLE
[titre +44.156 s] scène : TITLE -> IN_WORLD
[titre +51.749 s] setScreen du titre au retour du monde : 24.7 ms (recréation de la scène comprise)
[titre +51.755 s] PASS retour-monde : …LostIslandTitleScreen, scène RECREATING
[titre +52.970 s] PASS scene-recreee : première image de la scène recréée 1245 ms après le retour au titre
[titre +54.478 s] PASS widgets-retour : 4 boutons à nous (attendu 4), 0 widget(s) étranger(s) visibles
[titre +57.794 s] serveur 127.0.0.1:25565 : joueur présent, … scène IN_WORLD
[titre +62.108 s] déconnexion faite (serveur), écran ouvert : …LostIslandTitleScreen
[titre +64.124 s] PASS deconnexion-serveur : écran ouvert par setScreen(new JoinMultiplayerScreen(new TitleScreen())) :
                  …LostIslandTitleScreen ; deux secondes plus tard : …LostIslandTitleScreen, scène TITLE
```

- **Passage chargement → titre** : états de la scène `LOADING_OVERLAY -> TITLE`, sans libération ni recréation
  entre les deux (la libération n'apparaît qu'à l'entrée dans le monde : « scène libérée (entrée dans un monde),
  textures ≈ 302.7 Mio » à l'essai précédent). La continuité à l'écran n'a pas été vue (voir « Film »).
- **Widgets** (relevé à 1 s, 427 x 240) : nos 4 boutons en x=16, l=160, y = 92 / 116 / 140 / 164, « Join the island »
  `actif=false` ; rangée du bas : Mods (6, 214), langue (50, 214), accessibilité (74, 214) ; ligne Mojang
  (229, 230). Ni Multijoueur ni Realms. Relevé identique au retour du monde.
- **Retour de la liste des mods** : même instance de titre, scène TITLE (pas de reprise du récit).
- **Monde** : `lostisland-essai` (monde plat créé au premier essai dans `run/saves`), scène libérée en jeu
  (`IN_WORLD`), retour par les appels de `PauseScreen.onDisconnect`. **Recréation synchrone : 24,7 ms** pour le
  `setScreen` du titre, recréation comprise (TitleScene : « scène recréée sur l'aube en … ms (textures en
  chargement, fond de repli) ») ; première image de la scène recréée **1 245 ms** après le retour, pendant lesquels le
  fond de repli est dessiné. Autres relevés du même pas : 25,4 / 1 176 / 1 160 / 1 178 ms aux autres lancements.
  La scène recréée est tenue sur l'aube : journal « phase DONE : Welcome to Lost Island. », aucune chute de l'avion
  sur l'image 12.
- **Déconnexion d'un serveur dédié** (décision 6) : le joueur rejoint le serveur local (`[Ingénieur] Dev joined
  the game` à 17:37:18 au journal du serveur, `Dev lost connection: Disconnected` à 17:37:21), puis la sortie
  reproduit la pause (`clearLevel()`, `setScreen(new JoinMultiplayerScreen(new TitleScreen()))`) : l'écran obtenu est
  **notre titre**, pas la liste Multijoueur. La lecture par réflexion de `JoinMultiplayerScreen.lastScreen`
  (`f_99676_`) ne journalise rien quand elle réussit ; son échec écrirait « parent de la liste Multijoueur
  illisible » : **0** occurrence dans `latest.log`. La réussite de la réflexion se déduit donc de l'écran obtenu et
  de l'absence de cette ligne.
- **Survol** : `glfwSetCursorPos` n'atteint pas le jeu (la position de la souris du jeu ne bouge pas en 0,5 s) ;
  le repli appelle le rappel de position que Minecraft a installé, ce qui met à jour `mouseHandler` : le bouton est
  vu survolé et l'infobulle est dessinée (image 05). Le survol est donc prouvé par l'état de Minecraft, pas par un
  vrai mouvement de souris.
- **Infobulle en anglais** : `run/options.txt` est en `en_us`, attendu.

Images regardées (`run/frames-titre/`) :

- `01-titre-0.5s.png` et `04-titre-30s.png` : l'aube (épave, cascade, forêt, mer à gauche), logo LOST ISLAND en
  haut à gauche, devise « Where everything begins again. », les 4 boutons sous le logo (« Join the island » grisé
  doré), rangée du bas Mods / globe / personnage, « Minecraft 1.20.1 » et la ligne de Mojang en bas à droite, pas de
  phrase jaune. Entre 0,5 et 30 s, nuages et lumière ont bougé : la scène est animée.
- `05-survol-rejoindre.png` : infobulle « Server coming soon » sous le bouton grisé.
- `11-retour-monde.png` (premier essai et lancement 1) : fond de repli bleu nuit en dégradé, sans logo, mêmes
  boutons : l'état RECREATING.
- `12-retour-monde-scene.png` : la scène recréée complète, identique d'aspect à l'image 01.
- `13-serveur.png` : le joueur sur le monde plat du serveur (avertissement de chat non vérifié, compte hors ligne).
- `14-deconnexion.png` : notre titre, scène complète, après la déconnexion du serveur.

## Lancement 2 : français (`run-titre/`, 17:38)

« bilan : TOUT PASSE, 12 image(s) » ; `lostisland-earlywindow.log` : `langue : fr_fr (options.txt : fr_fr)`, phases
« Inventaire de la soute… », « Tri des caisses de l'épave… », « Le camp s'organise… », « Exploration du littoral… »,
« Bienvenue sur Lost Island. ». Ces textes du chargement sont **lus au journal, pas vus** (aucune image du
chargement dans ce lancement).

- `PASS survol-rejoindre : … infobulle « Serveur bientôt disponible »`.
- `PASS 06-options : Options ouvert : …OptionsScreen` puis `PASS 07-retour-options : …LostIslandTitleScreen, même
  instance qu'avant, scène TITLE` (Options seulement ici, dans `run-titre`).
- Liste des mods, monde, recréation (1 178 ms) : PASS comme au lancement 1. Pas de serveur dans ce lancement.

Images regardées (`run-titre/frames-titre-fr/`) : `01-titre-0.5s.png` : « Solo », « Rejoindre l'île », « Options »,
« Quitter le jeu », et la devise **« Là où tout recommence. »** sous le logo ; `05-survol-rejoindre.png` : infobulle
« Serveur bientôt disponible » ; `07-retour-options.png` : le titre au retour d'Options, scène complète. La ligne de
Mojang reste « Copyright Mojang AB. Do not distribute! » (texte de Minecraft, non traduit par le jeu).

## Lancement 3 : sans le jar de service (17:40)

`latest.log` : `Loading ImmediateWindowProvider lostisland` puis `Failed to find ImmediateWindowProvider lostisland,
disabling` ; `TitleScene` : « Écran titre : pas de scène animée pour cette session, fond de repli ». Scénario :
titre ouvert (`LostIslandTitleScreen`, scène `UNAVAILABLE`), survol avec infobulle, liste des mods aller-retour, monde
aller-retour : PASS. Un seul FAIL, **attendu par construction** : `scene-recreee : pas de scène au retour du monde
(état UNAVAILABLE après 0.0 s), fond de repli` (le scénario attend une scène recréée, il n'y en a pas sans le jar).
22 lignes « exception » dans `latest.log`, le compte relevé au chantier 1 (préexistantes) ; aucune nouvelle erreur du
mod relevée.

Image regardée : `run/frames-titre-sansjar/05-survol-rejoindre.png` : dégradé bleu nuit, pas de logo (la colonne de
boutons remonte, centrée verticalement), 4 boutons, infobulle « Server coming soon », rangée du bas et ligne Mojang.
L'écran de Mojang pendant le chargement n'a pas été vu (le scénario ne capture qu'à partir du titre) : il se déduit
du fournisseur désactivé, comme au chantier 1.

## Lancement 4 : film plein écran (17:42)

`run/film-titre.mp4` (non commité) : ffprobe h264, 2560x1440, `r_frame_rate=60/1`, `avg_frame_rate=60/1`, 4 921
images, **82,02 s**, 19,2 Mo (ffmpeg : 77 images dupliquées, 0 abandonnée). Copie `run/film-titre-720p.mp4` :
h264, 1280x720, 60/1, 4 921 images, 82,02 s, **3,3 Mo**. Le scénario du même lancement : « bilan : TOUT PASSE, 10
image(s) », survol PASS.

**Le film ne montre pas le jeu.** Images regardées à 16, 20, 30, 40, 46, 48, 60, 70 et 78 s (réduites) : la fenêtre
du jeu est restée **derrière** les fenêtres de Killian (application Claude, navigateur, explorateur) pendant tout le
film ; seul un bout de la ligne de Mojang dépasse à 46-48 et 60 s. À 78 s, une fenêtre de discussion privée occupe
la droite de l'écran. Ce film ne prouve donc ni la continuité chargement → titre, ni le fondu d'arrivée de la barre ;
il montre le bureau de Killian et **ne doit pas être diffusé**. Pour un film utile, il faut que la fenêtre du jeu soit
au premier plan, poste libre.

## Décisions de Killian (2026-11-08, pendant la tâche 5)

- **Pas de bouton Create sur notre titre** : accepté ; ses réglages restent accessibles par Mods → Create → Config.
  Raison relevée au javap (Create 6.0.8, `OpenCreateMenuButton$OpenConfigButtonHandler`) : Create n'ajoute son bouton
  au titre que s'il trouve, par le texte traduit, un bouton vanilla de la ligne `mainMenuConfigButtonRow` (2 dans
  `run/config/create-client.toml`) ; nos boutons ont nos clés et Multijoueur est retiré, il n'ajoute donc rien.
  Aucun autre mod du pack n'a ajouté de bouton au titre (0 widget étranger aux lancements 1 à 4).
- **Les deux ouvertures de la liste des mods du premier essai** étaient ses clics : pas un défaut.

## Tests

`./gradlew --offline test --rerun-tasks` : BUILD SUCCESSFUL ; XML : `test` (mod) 150 tests, `earlywindowTest` 90
tests, 0 échec, 0 erreur. Le rangement des boutons d'autres mods n'est couvert **que** par les tests :
`TitleWidgetsTest` (tri OURS / VANILLA_KEPT / VANILLA_REMOVED / FOREIGN) et `TitleLayoutTest`
(`uneRangeeTropLargePasseALaLigne`, `rienNeDeborde`, `laRangeeLaisseLaPlaceALaMentionDeMojang`). En jeu, aucun bouton
étranger n'a jamais été rangé.

## Non prouvé

- **Bouton d'un autre mod rangé en jeu** : aucun mod du pack n'en ajoute ; enchaînement réel `arrange()` jamais vu
  avec un widget FOREIGN ; widget remplacé sans changement de nombre (mineur de la tâche 3) non replacé.
- **Autres mods que le pack** (menus personnalisés, mods qui remplacent le titre) : jamais essayés.
- **Continuité à l'écran du passage chargement → titre** et **fondu d'arrivée de la barre** (règle finale du
  chantier 1) : non vus, le film n'a pas montré la fenêtre du jeu. Le passage n'est prouvé que par les états de la
  scène (`LOADING_OVERLAY -> TITLE`, pas de recréation).
- **Textes du chargement en français à l'écran** : lus au journal seulement.
- **Vrai bouton de la pause et vraie souris** : la sortie de monde et de serveur reproduit les appels de
  `PauseScreen.onDisconnect` (décision 6) ; le survol passe par le rappel GLFW, pas par un mouvement réel.
- **Connexion réelle par « Rejoindre l'île »** : `TitleConfig.SERVER_ADDRESS` est vide, le bouton est grisé ;
  connexion, refus et écran d'erreur depuis ce bouton jamais essayés.
- **Navigation au clavier seul** (Tab, Entrée) : non essayée.
- **Vérificateur Realms** : toujours démarré par `super.init` (`Realms Notification Availability checker` dans
  `latest.log`), alors que Realms n'est plus affiché.
- **Lignes de Forge du titre** (version, nombre de mods, étoile de mise à jour) : non dessinées par notre écran.
- **Installation réelle hors développement** (jar réobfusqué, launcher) : jamais lancée.
- **Autres tailles de fenêtre, HiDPI, plein écran** : seulement 854x480, échelle automatique.
- **Retour au titre depuis l'écran d'erreur de connexion** (`ConnectScreen` refusé) : vu une fois en passant, avec
  `localhost` refusé (le scénario est revenu au titre par `new TitleScreen()`, pas par le bouton de l'écran
  d'erreur) ; pas de preuve dédiée.
- **Fuite de mémoire vidéo sur plusieurs allers-retours** : un seul aller-retour par lancement (deux au lancement 1 :
  monde puis serveur).
