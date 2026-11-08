# Chantier 1 : moteur de scène et écran de chargement — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La scène Lost Island animée s'affiche dès la première image de la fenêtre, raconte la chute de l'avion jusqu'à l'aube, suit le vrai chargement par sa barre et ses messages, et tient l'écran jusqu'au menu, sans écran Mojang ni Forge.

**Architecture:** Un jar de service (`oneforallsurvival-earlywindow`), construit par un nouveau sourceSet `earlywindow`, déclare un `ImmediateWindowProvider` nommé `lostisland`. Il crée la fenêtre, dessine la scène en OpenGL direct dans son propre thread pendant la phase Forge, puis, par `loadingOverlay`, désigne la classe `LostIslandLoadingOverlay` du mod, qui dessine la même scène dans le contexte de Minecraft pendant le chargement des ressources. Le rendu (`SceneRenderer`) et la logique pure (horloge du récit, barre, messages) vivent dans le jar de service, sans aucune classe de Minecraft. En cas d'échec à l'initialisation, tout est délégué au `DisplayWindow` de Forge.

**Tech Stack:** Forge 1.20.1-47.4.28 (fmlloader, fmlearlydisplay), LWJGL 3 (GLFW, OpenGL 3.2 core, STB image), Gson, Java 17, ForgeGradle 6, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-08-interface-lost-island-design.md`, section « Chantier 1 ». Référence du rendu : `art/loading-screen/tools/compose.py` et `art/loading-screen/preview/lost-island-chargement.mp4`.

## Global Constraints

- Branche `interface-chargement`. Commit et push après chaque tâche vérifiée, fichiers ajoutés par nom, jamais `art/`, `docs/assets/` ni `.superpowers/` ; pas de `--no-verify` ni `--force` ; message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Pas de fusion dans `main` sans l'accord de Killian.
- Aucune classe de Minecraft dans le sourceSet `earlywindow` : il tourne avant que le jeu existe.
- `ImmediateWindowProvider.name()` renvoie exactement `"lostisland"` ; le pack règle `earlyWindowProvider = "lostisland"` dans `config/fml.toml`.
- Calques livrés tels quels : **aucune recompression, aucune limite de taille** (décision de Killian). Les 48 images de mer sont toutes gardées.
- `art/` reste hors du dépôt : la construction du jar de service copie `art/loading-screen/assets/` au moment du build, et échoue avec un message clair si le dossier manque.
- Récit : `DURATION = 36.0`, `IMPACT = 4.4`, `BAR_IN = 5.2` (valeurs de `compose.py`). Après le récit : l'aube, toujours animée.
- La barre ne recule jamais, de la phase Forge à la fin des ressources de Minecraft.
- Messages : table de la spec, texte français exact, détail réel entre parenthèses.
- Échec d'initialisation (contexte, shader, texture, jar du mod absent) : retour à l'écran de Forge, jamais un plantage. Échec d'un seul effet : effet retiré, le reste s'affiche.
- Ne jamais écrire `fml.toml` avec `Set-Content -Encoding utf8` (BOM). L'écrire avec l'outil d'édition de fichiers, en UTF-8 sans BOM.
- Les fenêtres de jeu gênent Killian : chaque lancement de `runClient` est annoncé avant, et aucun client n'est laissé ouvert (arrêt par PID exact du processus lancé). Les rendus de contrôle de la scène se font hors du jeu, dans une fenêtre GLFW cachée.
- Aucun téléchargement : tout vient du cache Gradle (lancer Gradle avec `--offline` si une résolution tente le réseau).

## Review Focus

1. Le jar de service présent mais le jar du mod absent (ou la classe de l'overlay introuvable) : la phase ressources retombe sur l'overlay de Forge, pas d'exception. Testé en tâche 7 (`sansLeModLOverlayDeForgePrendLaMain`, vérifié dans le journal).
2. Une carte graphique qui refuse un shader d'effet (GLSL non compilé) : l'effet disparaît, la scène reste. Testé en tâche 4 (`unShaderRefuseRetireSeulementLEffet`, injection d'une source GLSL fautive).
3. Fenêtre redimensionnée ou plein écran pendant le chargement, ratio différent de 16/9 : la scène couvre la fenêtre sans bandes, recadrée au centre. Testé en tâche 4 (rendus 1280x1024 et 2560x1080 dans `SceneSnapshot`).
4. Chargement très court (moins de 36 s) : le récit accélère jusqu'à l'aube, aucun saut d'image. Testé en tâche 2 (`unChargementCourtAccelereJusquALAube`).
5. `/reload` ou changement de pack de ressources en jeu : le vanilla `LoadingOverlay` de Mojang s'affiche (hors du périmètre de la spec), sans plantage et sans fuite de textures de la scène, libérées après l'écran titre. Vérifié en tâche 8 et écrit dans la vérification.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Copie des calques | Copiés depuis `art/` au build, pas commités | `art/` reste hors du dépôt comme convenu ; le dépôt ne gonfle pas de 57 Mo. Conséquence : seul un poste qui a `art/` peut construire le jar de service. |
| Code du jar de service | sourceSet `earlywindow` du projet, tâche `earlyWindowJar` | Réutilise les dépendances de Minecraft déjà résolues (fmlloader, LWJGL, Gson), sans nouveau projet ni téléchargement. |
| Fenêtre et repli | Notre provider enveloppe un `DisplayWindow` de Forge et lui délègue tout si notre initialisation échoue | Repli garanti, identique à l'écran de Forge. |
| Essais en développement | Jar de service copié dans `run/mods` avant `runClient`, `run/config/fml.toml` réglé | Même chemin qu'en jeu réel (`ModDirTransformerDiscoverer`). |
| Contrôle visuel | `SceneSnapshot`, petit programme de dev qui rend des images de la scène hors du jeu, comparées aux images de la vidéo de référence | Aucune fenêtre de jeu pour vérifier le rendu. |
| Film de preuve | Le provider et l'overlay enregistrent eux-mêmes leurs images si `-Dlostisland.frames=<dossier>` est posé | Même principe que les films des lots précédents, sans capture de l'écran de Killian. |

---

### Task 0: Branche, jar de service vide, preuve du branchement

**Files:**
- Modify: `.gitignore` (ajouter `.superpowers/brainstorm/`)
- Modify: `build.gradle` (sourceSet `earlywindow`, tâche `earlyWindowJar`, tâche `installEarlyWindow` qui copie le jar dans `run/mods`)
- Create: `src/earlywindow/java/dev/oneforallsurvival/earlywindow/LostIslandWindow.java`
- Create: `src/earlywindow/resources/META-INF/services/net.minecraftforge.fml.loading.ImmediateWindowProvider` (une ligne : `dev.oneforallsurvival.earlywindow.LostIslandWindow`)
- Modify: `run/config/fml.toml` (`earlyWindowProvider = "lostisland"`), sans BOM

**Interfaces:**
- Produces: `public final class LostIslandWindow implements ImmediateWindowProvider` ; pour cette tâche, chaque méthode délègue à un champ `private final DisplayWindow forge = new DisplayWindow()`, sauf `name()` → `"lostisland"`. Elle journalise aussi, une fois par seconde depuis `periodicTick`, chaque `ProgressMeter` de `StartupNotificationManager.getCurrentProgress()` (nom, current, steps) et chaque nouveau message de `getMessages()`, avec l'horodatage depuis `initialize`, dans `logs/lostisland-earlywindow.log`.

- [ ] **Step 1:** Créer la branche, le `.gitignore`, le sourceSet (`compileClasspath` étendu de celui de `main` pour avoir fmlloader, fmlearlydisplay, LWJGL, Gson) et le jar (`archiveBaseName = "${mod_id}-earlywindow"`, sans `mods.toml`, classes `earlywindow` seulement).
- [ ] **Step 2: Run** `./gradlew earlyWindowJar` → jar produit ; `jar tf` montre le fichier de service et aucune classe `dev/oneforallsurvival/` autre que `earlywindow/`.
- [ ] **Step 3:** Écrire `LostIslandWindow` (délégation + journal) et `installEarlyWindow`.
- [ ] **Step 4: Annoncer à Killian**, puis lancer `./gradlew installEarlyWindow runClient -PwithPack` ; attendre le menu, fermer le client par son PID exact. Attendu dans `run/logs/latest.log` : `Loading ImmediateWindowProvider lostisland` ; dans `run/logs/lostisland-earlywindow.log` : les noms réels des barres de Forge et leurs messages, avec leurs temps. Le jeu atteint le menu comme avant.
- [ ] **Step 5:** Copier les noms de barres, les messages caractéristiques et les durées de chaque phase relevés dans `docs/verifications/chantier-1-chargement.md` (section « Relevé du démarrage »). Ils fixent la tâche 2.
- [ ] **Step 6: Commit** `.gitignore`, `build.gradle`, les deux fichiers `earlywindow`, la vérification : `feat(earlywindow): fournisseur lostisland branché, délégué à Forge`. Ne pas commiter `run/`.

### Task 1: Catalogue des calques

**Files:**
- Modify: `build.gradle` (`processEarlywindowResources` copie `art/loading-screen/assets/**` vers `lostisland/loading/` ; échec explicite si absent)
- Create: `src/earlywindow/java/dev/oneforallsurvival/earlywindow/scene/SceneManifest.java`
- Test: `src/earlywindowTest/java/dev/oneforallsurvival/earlywindow/scene/SceneManifestTest.java` (sourceSet de test `earlywindowTest`, JUnit, branché sur la tâche `test`)

**Interfaces:**
- Produces: `record SceneManifest(int outW, int outH, int plateW, int plateH, Sea sea, float[] sunUv, float[] moonUv, List<Fire> fires, Logo logo, …)` lu par `static SceneManifest parse(Reader json)` (Gson). Chaque champ de `manifest.json` utilisé par `compose.py` y a son équivalent ; noms Java en camelCase du JSON.

- [ ] **Step 1: Write the failing test** `leManifesteReelSeLit` : parse `art/loading-screen/assets/manifest.json` (le test est ignoré avec un message si le fichier manque) → `outW == 1920`, `outH == 1080`, `plateW == 2112`, `plateH == 1188`, `sea.frames == 48`, `fires.size() > 0`, `logo.letters.size() == 10`. Et `unChampManquantDonneUneErreurClaire` sur un JSON sans `output`.
- [ ] **Step 2: Run** `./gradlew test --tests *SceneManifestTest` → FAIL.
- [ ] **Step 3: Implement** ; relever chaque clé utilisée par `compose.py` (`A.m[...]`) et la couvrir.
- [ ] **Step 4: Run** `./gradlew test` → vert, comptes lus dans `build/test-results`. `./gradlew earlyWindowJar` → le jar contient `lostisland/loading/manifest.json` et les 48 images de mer ; noter son poids.
- [ ] **Step 5: Commit** `feat(earlywindow): catalogue des calques`.

### Task 2: Horloge du récit, barre et messages (logique pure)

**Files:**
- Create: `src/earlywindow/java/dev/oneforallsurvival/earlywindow/scene/StoryClock.java`, `ProgressModel.java`, `StatusMessages.java`
- Test: `src/earlywindowTest/java/.../scene/StoryClockTest.java`, `ProgressModelTest.java`, `StatusMessagesTest.java`

**Interfaces:**
- Produces:
  - `StoryClock` : `float storyTime(double realSeconds, boolean loadingDone)` — temps du récit ; égal au temps réel jusqu'à `DURATION` ; ensuite `DAWN_LOOP_START` + temps qui boucle sur la plage d'aube ; si `loadingDone` avant la fin, avance à `ACCELERATION = 4.0` fois jusqu'à `DURATION`. Constantes publiques `DURATION = 36f`, `IMPACT = 4.4f`, `BAR_IN = 5.2f`, `DAWN = 29f` (fin de `LOAD_END` dans `compose.py`, à confirmer sur la vidéo : première image où l'ambiance est l'aube complète). Méthode `boolean storyFinished(double realSeconds, boolean loadingDone)`.
  - `ProgressModel` : `enum Phase { FORGE_START, MOD_DISCOVERY, MOD_LOADING, REGISTRIES, RESOURCES, DONE }` ; `void report(Phase phase, float fractionInPhase)` ; `float displayed(double dtSeconds)` — part fixe par phase (`WEIGHTS`, tirées du relevé de la tâche 0, total 1), jamais décroissante, lissée (rattrape la cible à `CATCH_UP = 3.0` par seconde au plus).
  - `StatusMessages` : `record Status(String theme, String detail)` ; `Status of(Phase phase, String rawForgeLabel, int current, int steps)`. Textes exacts de la spec : « Les moteurs toussent… », « Inventaire de la soute… », « Tri des caisses de l'épave… », « Le camp s'organise… », « Exploration du littoral… », « Bienvenue sur Lost Island. » ; détail : `démarrage`, `recherche des mods`, `mods 23/154 : create`, `registres`, `textures`. Phase déduite du nom de barre et des messages relevés en tâche 0 par `static Phase phaseOf(String meterName, String message)`.
- [ ] **Step 1: Write the failing tests**
  - `StoryClockTest` : `leRecitSuitLeTempsReel` (10 s → 10 f) ; `apresLeRecitLAubeBoucle` (100 s, non fini → valeur dans `[DAWN, DURATION]`, et deux instants séparés d'une boucle donnent la même valeur) ; `unChargementCourtAccelereJusquALAube` (fini à 10 s → à 10 + (36−10)/4 s on est à `DURATION`, et la courbe est continue : écart < 0,1 s entre deux pas de 1/60 s) ; `leTempsNeReculeJamais` (balayage 0..200 s, chargement fini à 20 s).
  - `ProgressModelTest` : `lesPartsFontUn` ; `laBarreNeReculeJamais` (phase RESOURCES à 0,1 après MOD_LOADING à 0,9) ; `unRapportEnRetardEstIgnore` ; `laBarreRattrapeEnDouceur` (saut de cible 0 → 0,5 : après 0,1 s affichée ≤ 0,3) ; `doneDonneUn`.
  - `StatusMessagesTest` : un test par ligne de la table de la spec, avec les libellés réels relevés en tâche 0 ; `unLibelleInconnuGardeLaPhaseCourante` ; `leDetailDesModsPorteLeCompteEtLeNom` (`"mods 23/154 : create"`).
- [ ] **Step 2: Run** `./gradlew test --tests *Story* --tests *Progress* --tests *Status*` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test` → vert, comptes lus.
- [ ] **Step 5: Commit** `feat(earlywindow): horloge du récit, barre et messages`.

### Task 3: Rendu de base (ciel, ambiances, plans, mer, caméra) et `SceneSnapshot`

**Files:**
- Create: `src/earlywindow/java/dev/oneforallsurvival/earlywindow/scene/SceneRenderer.java`, `TextureStore.java`, `GlQuad.java`, `shaders/` (GLSL 150 dans `src/earlywindow/resources/lostisland/shaders/`)
- Create: `src/earlywindowTest/java/dev/oneforallsurvival/earlywindow/scene/SceneSnapshot.java` (programme `main`, pas un test JUnit)
- Modify: `build.gradle` (tâche `sceneSnapshot` de type `JavaExec`)

**Interfaces:**
- Consumes: `SceneManifest`, `StoryClock`.
- Produces: `SceneRenderer(SceneManifest manifest, TextureStore textures)` ; `void render(int fbWidth, int fbHeight, SceneState state)` où `record SceneState(float storyTime, float progress, Status status, float uiAlpha, double realTime)` ; `void close()`. `TextureStore` : chargement STB dans un thread de décodage, `upload()` appelé sur le thread GL, `boolean ready(String group)` par groupe (`sky`, `plates`, `sea`, `fx`, `ui`) pour l'affichage progressif. `SceneSnapshot` : `--t 3,12,22,33 --p 0.69 --size 1920x1080 --out build/scene-snapshots` → un PNG par temps, dans une fenêtre GLFW cachée.

- [ ] **Step 1:** Porter de `compose.py` la caméra (zoom `1 + 0.042 * sstep(0, DURATION, t)`, dérive `ox`), `lerp3` des ambiances nuit / pré-aube / lever, `draw_clouds`, `draw_bank`, `draw_sea` (48 images, `horizon_row`), `layer_rgba` pour `far`, `mid`, `fg`, `precrash_mid` avant l'impact. Couverture de la fenêtre : recadrage centré, sans bandes, quel que soit le ratio.
- [ ] **Step 2: Run** `./gradlew sceneSnapshot` → 4 images ; extraire les images de la vidéo aux mêmes temps (`ffmpeg -ss <t> -frames:v 1`, ffmpeg de `common.py`) ; les comparer côte à côte (Read des deux images) : mêmes ambiances, même cadrage, même mer. Noter les écarts.
- [ ] **Step 3: Run** `./gradlew sceneSnapshot --args="--t 33 --size 1280x1024"` puis `2560x1080` → aucune bande, sujet centré.
- [ ] **Step 4: Commit** `feat(earlywindow): rendu du ciel, du décor et de la mer`.

### Task 4: Feux, fumées, braises, halos ; repli effet par effet

**Files:**
- Modify: `SceneRenderer.java` ; Create: `scene/effects/FireEffect.java`, `SmokeEffect.java`, `EmberEffect.java`, `HaloEffect.java`, interface `SceneEffect`
- Test: `src/earlywindowTest/java/.../scene/EffectFallbackTest.java`

**Interfaces:**
- Produces: `interface SceneEffect { String name(); void init(TextureStore t, ShaderLoader s) throws EffectUnavailable; void render(SceneState s, View v); void close(); }`. `SceneRenderer` retire un effet dont `init` ou `render` lève, le journalise une fois, et continue.
- [ ] **Step 1: Write the failing test** `unShaderRefuseRetireSeulementLEffet` : un `ShaderLoader` truqué qui rend une source fautive pour la fumée → après `init`, `renderer.activeEffects()` contient tout sauf `smoke`. (Test sans GL : `ShaderLoader` est une interface, la compilation est simulée.)
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** en portant `draw_fires` (bandes `fire_*_strip`, positions et phases du manifeste), `draw_halos`, `draw_smoke`, `draw_embers` (déterministes en temps, comme `hash01`).
- [ ] **Step 4: Run** `./gradlew test` → vert ; `./gradlew sceneSnapshot` aux temps 12, 22, 33, 60 et 120 → feux et fumée présents et animés (deux images à 1/10 s d'écart diffèrent), comparés à la vidéo.
- [ ] **Step 5: Commit** `feat(earlywindow): feux, fumées, braises et halos`.

### Task 5: Orage et crash (pluie, éclairs, avion)

**Files:**
- Create: `scene/effects/RainEffect.java`, `BoltEffect.java`, `CrashEffect.java`

- [ ] **Step 1: Implement** en portant `draw_rain`, `flash_at`, `draw_bolt`, `draw_falling_plane` (trajectoire de `crash_track.py`, images `fx/crash/plane_*.png`), `draw_crash_trail`, `draw_crash_particles`. Tout est déterminé par le temps du récit : rien après l'impact sauf ce que `compose.py` garde.
- [ ] **Step 2: Run** `./gradlew sceneSnapshot --args="--t 0.5,2,3.5,4.4,5"` → avion visible et en chute, éclair et pluie présents avant l'impact, comparés à la vidéo aux mêmes temps.
- [ ] **Step 3: Commit** `feat(earlywindow): orage et chute de l'avion`.

### Task 6: Logo, barre, texte ; feuillage au vent

**Files:**
- Create: `scene/effects/LogoEffect.java`, `ProgressUi.java`, `FoliageWind.java` ; `scene/BitmapFont.java`
- Create (calque) : `art/loading-screen/assets/plates/foliage_far.png`, `foliage_mid.png`, `foliage_fg.png` (non commités, comme le reste d'`art/`), et le script qui les produit `art/loading-screen/tools/foliage_masks.py` (non commité). La méthode est rapportée dans la vérification.

**Interfaces:**
- Produces: `BitmapFont` lit `assets/minecraft/textures/font/ascii.png` et accents (`accented.png`) depuis le jar du client trouvé par le classpath de lancement (comme `jar_image` de `common.py`) ; repli sur `Monocraft.ttf` de fmlearlydisplay si introuvable.
- [ ] **Step 1:** Porter `draw_logo` (lettres, `glow.png` additif, respiration, reflet `face_mask.png`) et `draw_ui` (traînée dorée = barre, `plane_icon.png`, texte d'état, « LOST ISLAND RP » et « ÎLE INCONNUE » comme la vidéo). La barre et le texte lisent `SceneState.progress` et `SceneState.status`, pas le temps du récit.
- [ ] **Step 2:** Produire les masques de feuillage : depuis `lost-island-scene.blend` par Blender (MCP), même caméra que `render_plates.py`, matériaux de feuilles en blanc et le reste en noir, une passe par plan ; à défaut, par couleur sur les plans existants. Shader de vent : déplacement de quelques pixels, bruit lent, pondéré par le masque.
- [ ] **Step 3: Run** `./gradlew sceneSnapshot --args="--t 22,33,90 --p 0.69"` → logo, barre à 69 %, texte, comparés à la vidéo ; deux images à 0,5 s d'écart sur l'aube montrent le feuillage déplacé (différence non nulle limitée aux zones du masque).
- [ ] **Step 4: Commit** `feat(earlywindow): logo, barre, texte et feuillage au vent`.

### Task 7: Fenêtre anticipée : la scène dans la phase Forge

**Files:**
- Modify: `LostIslandWindow.java` (notre fenêtre et notre thread de rendu ; délégation complète à `DisplayWindow` si l'initialisation échoue)
- Create: `src/earlywindow/java/dev/oneforallsurvival/earlywindow/EarlyRenderLoop.java`, `FrameRecorder.java`

**Interfaces:**
- Produces: `LostIslandWindow.sceneState()` (accès partagé au `StoryClock`, `ProgressModel`, `TextureStore` pour la phase ressources) ; `FrameRecorder.maybe(String dir)` actif si `-Dlostisland.frames=<dossier>` (une image sur deux, PNG numérotés).
- [ ] **Step 1: Implement** en suivant `DisplayWindow` pour la création de la fenêtre GLFW, le thread de rendu, l'échange avec Minecraft (`setupMinecraftWindow` rend la fenêtre et son contexte au thread principal), `positionWindow`, `updateFramebufferSize`, `periodicTick`, `getGLVersion`. Progression : lecture de `StartupNotificationManager` à chaque image, `StatusMessages.phaseOf`, `ProgressModel.report`. Le premier affichage n'attend que le groupe `sky` ; les autres groupes apparaissent en fondu dès qu'ils sont prêts.
- [ ] **Step 2:** `loadingOverlay` : chercher dans la couche du jeu le module `oneforallsurvival`, la classe `dev.oneforallsurvival.client.loading.LostIslandLoadingOverlay` et sa méthode statique `newInstance` ; si absent ou en erreur, déléguer à `forge.loadingOverlay(...)` (overlay de Forge) et le journaliser (`sansLeModLOverlayDeForgePrendLaMain`).
- [ ] **Step 3: Annoncer à Killian**, lancer `./gradlew installEarlyWindow runClient -PwithPack -PsceneFrames=frames-chargement` ; dans `build.gradle`, le run `client` reçoit `property 'lostisland.frames', project.sceneFrames` seulement si la propriété Gradle existe (un `-D` sur la ligne de Gradle n'atteindrait pas le jeu). Fermer par PID après le menu. Attendu : la première image enregistrée montre déjà la scène ; aucun « Loading ImmediateWindowProvider fmlearlywindow » ; aucune exception dans `latest.log`.
- [ ] **Step 4:** Repli : relancer avec `-Dlostisland.earlywindow.fail=true` (lève dans notre initialisation) → écran de Forge, jeu jusqu'au menu ; et sans le jar dans `run/mods` → écran de Forge. Fermer chaque client par PID.
- [ ] **Step 5: Commit** `feat(earlywindow): scène dans la fenêtre anticipée, repli sur Forge`.

### Task 8: Phase ressources de Minecraft et fin

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/client/loading/LostIslandLoadingOverlay.java`
- Modify: `build.gradle` (`main` compile contre la sortie `earlywindow` en `compileOnly` ; cette sortie n'est **pas** ajoutée au classpath des runs : à l'exécution, ces classes viennent du jar de service dans `run/mods`, sinon elles existeraient en double dans deux couches)

**Interfaces:**
- Consumes: `LostIslandWindow.sceneState()`, `SceneRenderer`.
- Vérifier d'abord que le module du mod (couche du jeu) lit le module du jar de service (couche des services) : un mod est un module automatique, il devrait tout lire ; sinon `LostIslandWindow.updateModuleReads` ajoute la lecture nécessaire, comme `DisplayWindow` le fait pour `forge`.
- Produces: `public static Supplier<LoadingOverlay> newInstance(Supplier<Minecraft>, Supplier<ReloadInstance>, Consumer<Optional<Throwable>>, Object window)` (même forme que `ForgeLoadingOverlay.newInstance`). Rend la scène plein écran dans le contexte de Minecraft, `ProgressModel.report(RESOURCES, reload.getActualProgress())`, puis à la fin : `progress = DONE`, la barre et le texte s'effacent en 1 s, l'overlay se retire (`minecraft.setOverlay(null)`), et l'état de la scène reste disponible pour le chantier 2. Textures de la scène libérées quand l'écran titre n'en veut pas (chantier 1 seul : à la fermeture de l'overlay).
- [ ] **Step 1: Implement** en reprenant la structure de `ForgeLoadingOverlay` (gestion de `reload`, `onFinish`, erreurs) sans ses bandes de couleur.
- [ ] **Step 2: Annoncer à Killian**, lancer comme en tâche 7 avec enregistrement ; fermer par PID. Attendu : images continues de la phase Forge à la phase ressources (même scène, barre croissante), puis le menu ; durée du démarrage relevée dans le journal (premier horodatage → menu).
- [ ] **Step 3:** Même lancement avec `earlyWindowProvider = "fmlearlywindow"` (écran de Forge) pour la durée de référence. Remettre `lostisland` ensuite. En jeu, un `F3 + T` montre l'overlay de Mojang sans plantage (hors périmètre, à noter).
- [ ] **Step 4: Commit** `feat(loading): la scène tient le chargement des ressources`.

### Task 9: Étalonnage, film et vérification

**Files:**
- Modify: `ProgressModel.java` (`WEIGHTS` étalonnés sur deux démarrages réels avec le pack)
- Create/Modify: `docs/verifications/chantier-1-chargement.md`, `tasks/todo.md`

- [ ] **Step 1:** Étalonner `WEIGHTS` sur les durées mesurées ; `./gradlew test` vert.
- [ ] **Step 2: Annoncer à Killian**, un lancement final enregistré ; assembler le film avec ffmpeg (`run/frames-chargement` → `run/film-chargement.mp4`) ; l'envoyer à Killian (SendUserFile). Fermer le client par PID.
- [ ] **Step 3:** Écrire la vérification : commandes, durées avec et sans la scène, poids du jar de service, méthode du masque de feuillage, comparaison aux images de référence, replis essayés, et la liste « Non prouvé » (vieilles cartes graphiques, macOS et Linux, écrans HiDPI, overlay de Mojang au `/reload`, joueur sans le réglage `fml.toml`).
- [ ] **Step 4: Commit et push** `docs(chargement): vérification du chantier 1`. Mettre à jour `tasks/todo.md` (chantier 1 fait, chantiers 2 et 3 à planifier).
