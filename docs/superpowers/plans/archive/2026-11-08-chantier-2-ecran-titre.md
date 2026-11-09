# Chantier 2 : écran titre — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** L'écran titre de Lost Island continue la scène du chargement sur l'aube vivante, logo en haut à gauche, avec à gauche les boutons Solo, Rejoindre l'île, Options, Quitter, et en bas à gauche une rangée discrète où se rangent aussi les boutons des autres mods.

**Architecture:** `LostIslandTitleScreen` **hérite** de `TitleScreen` et la remplace à l'ouverture (`ScreenEvent.Opening`) : les mods qui testent `instanceof TitleScreen` continuent d'ajouter leurs boutons. Il dessine la scène du jar de service (`EarlyScene`, déjà chargée par le chantier 1) au lieu du panorama, retire Multijoueur et Realms, place ses boutons par une logique pure (`TitleLayout`), et range après tous les autres mods (`ScreenEvent.Init.Post`, priorité la plus basse) chaque bouton qui n'est ni à lui ni à Minecraft. La scène passe de l'overlay du chargement à l'écran titre sans être libérée ; elle est libérée en entrant dans un monde et recréée au retour au titre.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, jar de service `earlywindow` du chantier 1.

**Spec:** `docs/superpowers/specs/archive/2026-11-08-interface-lost-island-design.md`, section « Chantier 2 ». État du chantier 1 : `docs/verifications/chantier-1-chargement.md`.

## Global Constraints

- Branche `interface-titre`, depuis `main`. Commit et push après chaque tâche vérifiée, fichiers ajoutés par nom, jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/` ; pas de `--no-verify` ni `--force` ; messages terminés par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Pas de fusion dans `main` sans l'accord de Killian.
- Disposition B validée par Killian : bannière/scène plein écran, logo de la scène en haut à gauche, boutons empilés à gauche sous le logo, assombrissement à gauche, décor visible à droite.
- Boutons, dans cet ordre : « Solo », « Rejoindre l'île » (doré, mis en avant), « Options », « Quitter le jeu ». **Pas de Multijoueur, pas de Realms** ni sa notification.
- Rejoindre l'île : connexion standard de Minecraft à une **constante** du code (`TitleConfig.SERVER_ADDRESS`, vide aujourd'hui) ; vide → bouton grisé, infobulle « Serveur bientôt disponible » ; pas d'ajout à la liste des serveurs ; refus → écran d'erreur habituel.
- Rangée discrète en bas à gauche : langue, accessibilité, liste des mods de Forge, et **tout bouton ajouté par un autre mod**, replacé sans chevauchement, jamais caché.
- En bas, en petit : version de Minecraft et mention de Mojang (obligatoire). La phrase jaune (splash) est retirée.
- Fond : la scène sur l'aube, toujours animée ; passage du chargement au titre sans coupure ; logo à la même place.
- Textes visibles dans `fr_fr.json` du mod, avec repli `en_us.json`.
- Événements de Forge d'abord ; un mixin seulement là où ils ne suffisent pas, raison écrite dans le commit et la vérification.
- Aucune classe cliente atteignable depuis un chemin serveur (paquet `client`, `Dist.CLIENT`).
- Les fenêtres de jeu gênent Killian : chaque lancement est annoncé avant ; arrêt par PID exact du seul JVM lancé ; aucun client laissé ouvert. Ne jamais toucher `run/options.txt` de Killian.
- Ne jamais écrire `fml.toml` avec `Set-Content -Encoding utf8`. Aucun téléchargement ; Gradle `--offline`.

## Review Focus

1. Un mod qui ajoute plusieurs boutons au titre, ou un bouton très large : la rangée du bas passe à la ligne au lieu de déborder, rien ne se chevauche, tout reste dans l'écran. Testé en tâche 1 (`uneRangeeTropLargePasseALaLigne`, `rienNeDeborde`).
2. Petite fenêtre (854x480, échelle d'interface 4 ou « auto ») : boutons lisibles, sans chevaucher le logo ni la rangée du bas. Testé en tâche 1 (`petiteFenetreEchelleMaximale`).
3. Retour au titre après un monde (scène libérée en jeu) : la scène se recrée sans geler l'écran, fond sombre le temps du chargement, puis fondu. Testé en tâche 2 (`retourAuTitreRecreeLaScene`) et vu en tâche 5.
4. Scène indisponible (jar de service absent, repli du chantier 1, échec GL) : l'écran titre reste utilisable avec un fond de repli (dégradé sombre), aucun plantage. Testé en tâche 2 (`sansSceneLEcranResteUtilisable`) et vu en tâche 5 (lancement sans le jar).
5. Retour de `ConnectScreen`, `OptionsScreen` ou de la liste des mods vers le titre : le titre est rouvert comme `LostIslandTitleScreen`, mêmes boutons, scène continue (pas de reprise du récit au début). Vu en tâche 5.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Garder le titre de Minecraft | Sous-classe de `TitleScreen` substituée à l'ouverture, plutôt qu'un réarrangement de l'instance vanilla par événements | Le dessin du panorama, du logo et du splash est dans `TitleScreen.render` : une sous-classe le remplace proprement, et `instanceof TitleScreen` reste vrai pour les autres mods (ex. `E2eClient` du projet). |
| Repérer les boutons des autres mods | Après `Init.Post` de priorité la plus basse : tout widget qui n'est pas dans la liste de ceux créés par `TitleScreen.init` (repérés par leur clé de traduction) ni par nous | Aucune liste de mods à tenir à jour. |
| Vie de la scène | L'overlay ne libère plus la scène s'il y a un écran titre ; libération en entrant dans un monde, recréation au retour | 57 Mio de textures n'ont pas à rester en mémoire vidéo pendant le jeu. |
| Essai en français | Run de dev séparé `titleFr` (dossier `run-titre/`, `options.txt` en `lang:fr_fr` créé par Gradle s'il manque) | Ne pas toucher aux réglages de Killian dans `run/`. |
| Version et mention Mojang | En bas à droite, en petit, l'une au-dessus de l'autre | La rangée de boutons occupe le bas à gauche. |

---

### Task 0: Branche, run de dev en français

**Files:**
- Modify: `build.gradle` (run `titleFr` : parent `client`, `workingDirectory project.file('run-titre')` ; tâche `prepareTitleRun` qui copie le jar de service dans `run-titre/mods`, écrit `run-titre/config/fml.toml` avec `earlyWindowProvider = "lostisland"` sans BOM, et `run-titre/options.txt` avec `lang:fr_fr` **seulement s'ils manquent** ; `.gitignore` : `run-titre/`)

- [ ] **Step 1:** `git switch -c interface-titre` depuis `main`.
- [ ] **Step 2:** Écrire le run et la tâche (l'écriture des fichiers se fait par Gradle/Java, pas par PowerShell).
- [ ] **Step 3: Run** `./gradlew --offline prepareTitleRun` deux fois → fichiers créés la première fois, inchangés la seconde (dates identiques) ; premier octet de `fml.toml` ≠ BOM.
- [ ] **Step 4: Commit** `build.gradle`, `.gitignore` : `build(titre): run de dev en français`.

### Task 1: Disposition pure (`TitleLayout`)

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/client/title/TitleLayout.java`
- Test: `src/test/java/dev/oneforallsurvival/client/title/TitleLayoutTest.java`

**Interfaces:**
- Produces: `record Rect(int x, int y, int w, int h)` ; `static List<Rect> mainButtons(int screenW, int screenH, Rect logo)` — 4 boutons de largeur `MAIN_W = 160` (réduite jusqu'à 120 si l'écran est étroit), hauteur 20, écart 4, alignés à gauche sur le logo (`x = logo.x()`), commençant 12 px sous le logo ; `static List<Rect> bottomRow(int screenW, int screenH, List<int[]> sizes)` — widgets de tailles données, de gauche à droite à partir de x = 6, à 6 px du bas, écart 4, passage à une ligne au-dessus si la ligne dépasse 45 % de la largeur ; `static Rect logoRect(int screenW, int screenH)` — position du logo de la scène projetée en coordonnées d'interface (même calcul de recadrage centré que `SceneRenderer`, lu dans la classe de rendu existante ou dupliqué avec un test d'égalité).
- [ ] **Step 1: Write the failing tests** : `quatreBoutonsSousLeLogo` (1920x1080 échelle 2 → x = logo.x, y croissants de 24, aucun ne coupe le logo) ; `petiteFenetreEchelleMaximale` (854x480 → largeur ≥ 120, le 4ᵉ bouton finit au-dessus de la rangée du bas) ; `uneRangeeTropLargePasseALaLigne` (10 widgets de 20x20 sur 427 de large → deux lignes) ; `rienNeDeborde` (balayage de tailles d'écran et de 0 à 12 widgets : tout dans l'écran, aucun chevauchement entre boutons principaux et rangée) ; `logoAlignéSurLaScène` (logoRect identique au calcul du rendu).
- [ ] **Step 2: Run** `./gradlew --offline test --tests *TitleLayoutTest` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew --offline test` → vert, comptes lus dans le XML.
- [ ] **Step 5: Commit** `feat(titre): disposition de l'écran titre`.

### Task 2: Vie de la scène entre chargement, titre et jeu

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/client/title/TitleScene.java` (détenteur côté jeu) et `SceneLifecycle.java` (logique pure)
- Modify: `src/main/java/dev/oneforallsurvival/client/loading/LostIslandLoadingOverlay.java` (ne libère plus la scène quand l'écran suivant est un titre : la confie à `TitleScene`)
- Modify (si nécessaire) : `src/earlywindow/java/.../EarlyScene.java` (fabrique pour recréer une scène dans le contexte du jeu, sur l'aube tenue, sans fenêtre anticipée)
- Test: `src/test/java/dev/oneforallsurvival/client/title/SceneLifecycleTest.java`

**Interfaces:**
- Produces: `TitleScene.acquire(): Optional<EarlyScene>` (crée ou rend la scène, jamais d'exception), `TitleScene.releaseForWorld()`, `TitleScene.render(GuiGraphics, float partialTick)` (sous `GlStateGuard`, alpha de la barre à 0, logo gardé, récit qui continue : chargement fini → accélération jusqu'à l'aube puis tenue, donc la devise apparaît) ; `SceneLifecycle` : états `LOADING_OVERLAY, TITLE, IN_WORLD, RECREATING, UNAVAILABLE` et transitions pures.
- [ ] **Step 1: Write the failing tests** : `leChargementConfieLaSceneAuTitre` ; `entrerDansUnMondeLibereLaScene` ; `retourAuTitreRecreeLaScene` ; `sansSceneLEcranResteUtilisable` (UNAVAILABLE ne relance pas de création à chaque image : une tentative, puis repli) ; `unRetourAuTitreNeRecommencePasLeRecit` (heure du récit conservée tant que la scène vit).
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** `./gradlew --offline test` → vert.
- [ ] **Step 5: Commit** `feat(titre): la scène passe du chargement au titre`.

### Task 3: `LostIslandTitleScreen`

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/client/title/LostIslandTitleScreen.java`, `TitleConfig.java` (`SERVER_ADDRESS = ""`, `SERVER_NAME = "Lost Island RP"`), `TitleEvents.java` (abonné `Dist.CLIENT` : `ScreenEvent.Opening` substitue un `TitleScreen` de classe exacte ; `ScreenEvent.Init.Post` en `EventPriority.LOWEST` range les widgets étrangers)
- Modify: `src/main/resources/assets/oneforallsurvival/lang/fr_fr.json`, `en_us.json`

**Interfaces:**
- Consumes: `TitleLayout`, `TitleScene`.
- Clés : `gui.oneforallsurvival.title.solo` « Solo » / « Singleplayer », `.join` « Rejoindre l'île » / « Join the island », `.options` « Options » / « Options », `.quit` « Quitter le jeu » / « Quit Game », `.join.soon` « Serveur bientôt disponible » / « Server coming soon ».
- Comportement : `init()` appelle `super.init()` puis retire Multijoueur (`menu.multiplayer`), Realms (`menu.online`) et ce qui reste des boutons principaux vanilla (`menu.singleplayer`, `menu.options`, `menu.quit`), garde langue, accessibilité, mods de Forge (`fml.menu.mods`) pour la rangée, ajoute les quatre boutons (Solo → `SelectWorldScreen`, Rejoindre → `ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(addr), new ServerData(name, addr, false), false)`, Options → `OptionsScreen`, Quitter → `minecraft.stop()`). Pas de notification Realms. `render()` : scène (ou dégradé de repli), assombrissement horizontal à gauche (opacité 0,55 → 0 sur 45 % de la largeur), widgets avec fondu d'entrée de 0,6 s la première fois, version et mention Mojang en bas à droite ; ni panorama, ni logo Minecraft, ni splash. Bouton Rejoindre doré (texture ou rendu propre, pas de copie de texture de Mojang). Si un mixin s'avère nécessaire, le dire et justifier.
- [ ] **Step 1:** Implémenter ; test JUnit pour le tri « nôtre / Minecraft / étranger » des widgets (fonction pure sur des clés de traduction et des classes).
- [ ] **Step 2: Run** `./gradlew --offline test` vert ; `./gradlew --offline runGameTestServer` → « All N required tests passed » (aucune classe cliente sur le chemin serveur).
- [ ] **Step 3: Commit** `feat(titre): écran titre Lost Island`.

### Task 4: Captures de contrôle (dev)

**Files:**
- Create: `src/devcontent/java/dev/oneforallsurvival/dev/TitleShots.java` (propriété `lostisland.titleShots=<dossier>`, passée par une propriété Gradle `-PtitleShots=` : une fois le titre affiché, capture d'images à 0,5 / 2 / 6 / 30 s, survol du bouton Rejoindre (`glfwSetCursorPos` sur le thread du jeu) et capture de l'infobulle, ouverture puis fermeture d'Options et de la liste des mods avec capture au retour, puis fin des captures ; réutiliser `FilmCapture`)
- [ ] **Step 1:** Implémenter ; rien de devcontent dans le mod livré.
- [ ] **Step 2: Commit** `test(titre): captures de contrôle de l'écran titre`.

### Task 5: Preuve en jeu, film et vérification

**Files:**
- Create: `docs/verifications/chantier-2-ecran-titre.md` ; Modify: `tasks/todo.md`

- [ ] **Step 1: Annoncer à Killian**, puis lancements, chacun arrêté par PID exact :
  1. `./gradlew --offline installEarlyWindow runClient -PwithPack -PtitleShots=frames-titre` → chargement puis titre sans coupure ; boutons de Create et des autres mods dans la rangée ; infobulle « Serveur bientôt disponible » (en anglais si `run/options.txt` est en `en_us` : c'est attendu) ; retour d'Options et des mods identique.
  2. `./gradlew --offline prepareTitleRun runTitleFr -PwithPack -PtitleShots=frames-titre-fr` → textes du chargement et du titre en français ; fondu final de la barre (non vu au chantier 1) ; devise « Là où tout recommence. » visible.
  3. Sans le jar de service dans `run/mods` (le remettre après) → écran de Mojang puis titre au fond de repli, utilisable.
  4. Entrer dans un monde de test puis revenir au titre (scénario dans `TitleShots` ou à la main par le script de dev) → scène recréée, pas de gel.
- [ ] **Step 2:** Film plein écran 60 i/s d'un lancement complet jusqu'au titre avec survol (ffmpeg `C:\Users\killi\AppData\Local\Programs\ffmpeg-9.0.2\bin\ffmpeg.exe`, `ddagrab` 60 i/s, `h264_nvenc`, voir la note BDC « Pour filmer tout l'écran sous Windows… ») → `run/film-titre.mp4` et une copie 720p sous 30 Mo ; regarder une dizaine d'images.
- [ ] **Step 3:** Écrire la vérification (lancements, captures regardées, liste « Non prouvé ») ; mettre à jour `tasks/todo.md`.
- [ ] **Step 4: Commit et push** `docs(titre): vérification du chantier 2`.
