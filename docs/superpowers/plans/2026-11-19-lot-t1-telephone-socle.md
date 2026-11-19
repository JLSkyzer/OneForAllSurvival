# Lot T1 du téléphone : socle navigateur, objet, code, accueil, Réglages, batterie et repli — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** N'importe quel joueur fabrique un téléphone, le protège par un code à 4 chiffres gardé par le serveur, l'ouvre sur un écran verrouillé puis sur un accueil animé façon iOS en HTML (MCEF 2.1.6), règle son téléphone dans Réglages, voit sa batterie se vider écran allumé et la recharge sur une prise murale alimentée en FE. Si MCEF manque ou échoue, le même téléphone s'ouvre en interface Minecraft classique, et le joueur en est prévenu une fois. Le socle navigateur (`client/web`) sert ensuite aux documents de lore.

**Architecture:**
- **Socle navigateur commun** (`client/web`, client seulement) : `WebView` (interface sans type de MCEF), `WebScreen` (écran qui dessine une vue dans un cadre, relaie souris et clavier, attend le signal `ready` de la page), `WebBridge` (messages JSON de la page), `WebPages` (adresses `mod://oneforallsurvival/…` permises), `WebScript` (appel JavaScript sérialisé en JSON, jamais concaténé), `WebAvailability` et `WebFallbackNotice` (repli et avis unique). **Seul** `client/web/mcef/` touche `com.cinemamod.mcef` et `org.cef`, et il n'est chargé qu'après `ModList.get().isLoaded("mcef")`. Les gestionnaires de MCEF sont posés **une fois** (listes de `MCEFClient`, routeur de messages à nom propre) et filtrés par navigateur : rien ne remplace un gestionnaire partagé avec WebDisplays.
- **Serveur du téléphone** (`phone/`) : objet `phone` (batterie par `ItemBattery`, identifiant de téléphone dans le NBT, posé au premier usage), `PhoneData` (`SavedData` : propriétaire, empreinte du code, essais, blocage, fond d'écran, mode avion), `PhoneService` (sessions d'écran ouvertes, transitoires ; chaque geste de la page revalidé ; batterie dépensée tant que l'écran est ouvert), deux paquets sur `BenchNetwork`. Le serveur fait foi : la page ne reçoit que la vue que la session a le droit de voir.
- **Prise murale** (`phone/WallSocket*`) : machine de base du lot 0 (`ElectricMachineBlockEntity`) fixée au mur, qui reçoit des FE par n'importe quel câble et recharge le téléphone branché.
- **Pages** dans `src/main/resources/assets/oneforallsurvival/html/` : `web/bridge.js` et `web/selftest.html` (socle), `phone/index.html`, `phone/phone.css`, `phone/phone.js`. Écran de repli : `phone/client/PhoneFallbackScreen`.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, MCEF 2.1.6-1.20.1 de CinemaMod (mod_id `mcef`, paquet `com.cinemamod.mcef`, JCEF du commit `a78e832f`, LGPL-2.1, client seulement), WebDisplays 2.0.2-1.20.1 (essai de cohabitation seulement), Gson (dans Minecraft), JUnit 5, GameTests, Python/PIL pour les textures. Paquets réseau nouveaux sur `BenchNetwork` : protocole `"9"` → `"10"`, identifiants 26 et 27.

**Spec:** `docs/superpowers/specs/2026-11-08-telephone-design.md` (validé le 2026-11-19). Sections utiles : 3 (MCEF, version du pack qui fait foi, socle commun, livraison, client seulement, repli, serveur qui fait foi), 4 (le téléphone : objet fabriqué par tous, batterie rechargée sur une prise FE, code à 4 chiffres, données liées au téléphone), feature 101 (Réglages), 7 (lot T1), 8 (vérification, scénario de performance), 9 (décisions du 2026-11-19). Autres sources : `docs/superpowers/specs/2026-11-15-documents-de-lore-design.md` § 4 et le plan `docs/superpowers/plans/2026-11-15-documents-de-lore.md` (tâche 3 « socle navigateur », écrite pour le fork 2.2.0 : ce lot la remplace).

## API de MCEF 2.1.6 relevée dans le jar du pack (javap, 2026-11-19)

Jar : `mcef-forge-2.1.6-1.20.1.jar`, 218 417 octets, SHA-1 `6d9fa1433d509be242d1d74282d02a0fbebde110` (identique au fichier Modrinth `x91l6OKB`). Rien d'autre n'est à supposer ; une signature absente d'ici se lit dans le jar avant d'être écrite.

| Besoin | Ce que le jar donne |
|---|---|
| Disponibilité | `MCEF.isInitialized()`, `MCEF.initialize()` (appelé par MCEF lui-même), `MCEF.scheduleForInit(MCEFInitListener)` avec `MCEFInitListener.onInit(boolean)` |
| Navigateur | `MCEF.createBrowser(String url, boolean transparent)` et `(String, boolean, int, int)` → `MCEFBrowser` (sous-classe de `org.cef.browser.CefBrowserOsr`) ; `resize(int, int)` ; `close()` ; `getRenderer().getTextureID()` (identifiant de texture GL, pas de `ResourceLocation`) ; `setZoomLevel(double)`, `loadURL(String)`, `executeJavaScript(String code, String url, int line)`, `getURL()`, `stopLoad()`, `setFocus(boolean)` |
| Dessin | l'exemple `com.cinemamod.mcef.example.ExampleScreen` : `RenderSystem.setShader(GameRenderer::<m_172820_>)`, `RenderSystem.setShaderTexture(0, getTextureID())`, quatre sommets `POSITION_TEX_COLOR` (`DefaultVertexFormat.f_85819_`) en `QUADS` par `Tesselator`, test de profondeur coupé. MCEF met la texture à jour en tête de `GameRenderer.render` (mixin `CefRenderUpdateMixin`) et règle lui-même `GL_UNPACK_ROW_LENGTH/SKIP_PIXELS/SKIP_ROWS` (3314/3316/3315) avant chaque envoi |
| Souris, clavier | `sendMouseMove(int, int)`, `sendMousePress(int x, int y, int button)`, `sendMouseRelease(int, int, int)`, `sendMouseWheel(int x, int y, double amount, int modifiers)` (avec Ctrl, MCEF change le zoom de ±9 : passer `modifiers = 0` hors Ctrl), `sendKeyPress(int keyCode, long scanCode, int modifiers)`, `sendKeyRelease(int, long, int)`, `sendKeyTyped(char, int)`. Coordonnées en pixels réels : l'exemple multiplie par `Window.getGuiScale()` |
| Pont JavaScript → Java | `org.cef.browser.CefMessageRouter.create(CefMessageRouterConfig, CefMessageRouterHandler)` ; `new CefMessageRouterConfig(String jsQueryFunction, String jsCancelFunction)` ; `CefMessageRouterHandler.onQuery(CefBrowser, CefFrame, long queryId, String request, boolean persistent, CefQueryCallback)` ; `CefQueryCallback.success(String)` / `failure(int, String)` ; `CefClient.addMessageRouter(CefMessageRouter)` / `removeMessageRouter`, joint par `MCEF.getClient().getHandle()` |
| Gestionnaires partagés | `MCEFClient.addLoadHandler`, `addDisplayHandler`, `addContextMenuHandler` : des **listes**, sans retrait. `CefDisplayHandler.onAddressChange(CefBrowser, CefFrame, String)`, `CefLoadHandler.onLoadError(CefBrowser, CefFrame, ErrorCode, String, String)`. `CefClient.addRequestHandler`, `addKeyboardHandler`, `addLifeSpanHandler`… sont des **emplacements uniques** du client partagé : **interdits** (ils retireraient ceux de WebDisplays) |
| Pages du mod | `MCEF.initialize()` enregistre le schéma `mod` (`registerSchemeHandlerFactory("mod", "", …)`) ; `ModScheme.processRequest` lit `ModScheme.class.getClassLoader().getResourceAsStream("/assets/<modid>/html/<chemin>")`, modid **et** chemin passés en minuscules ; type MIME par l'extension (`MIMEUtil.mimeFromExtension`) ; ressource absente : « Resource … NOT found! » au journal et requête annulée |
| Téléchargement | `CefDownloadMixin` (sur `ClientPackSource`) télécharge `${host}/java-cef-builds/${java-cef-commit}/${platform}.tar.gz` depuis `https://mcef-download.cinemamod.com` (réglage `download-mirror`, fichier `config/mcef/mcef.properties`, clé `skip-download`), dans `mods/mcef-libraries/` du dossier du jeu, ou dans le dossier donné par la propriété `mcef.libraries.path` (nom lu dans le jar, effet à confirmer à la tâche 3). `CefInitMixin` remplace l'écran titre (sous-classes comprises) par `MCEFDownloaderMenu` tant que le téléchargement n'est pas fini |
| Développement | hors production (`FMLEnvironment.production` faux), MCEF inscrit son exemple : touche **F10** (« Open Browser ») qui ouvre `https://www.google.com`. **Ne jamais appuyer sur F10** dans un client de développement |
| Côté serveur | mixins MCEF tous `client` ; `ForgeMCEFMod.serverSetup` ne fait rien. `mods.toml` de MCEF : dépendances `forge [47.0.1,)` et `minecraft [1.20,1.20.1]` seulement |

Points que le jar ne tranche pas, vérifiés en jeu à la tâche 3 avant tout le reste : la ressource du mod servie malgré la barre oblique de tête (chargeur de classes de Forge) ; `window.oneforallQuery` présent dans une page chargée après l'ajout du routeur ; l'échelle `1,2^niveau` de `setZoomLevel` ; la transparence (`transparent = true`) ; l'orientation de la texture.

## Global Constraints

Les contraintes globales des plans archivés restent valables (voir « Global Constraints » de `docs/superpowers/plans/archive/2026-11-17-lot-e6-prelevement-fournisseur.md`) : textes dans `fr_fr.json` avec repli `en_us.json`, accents corrects, jamais « SolarTown » ; `DeferredRegister` ; lots de 8 GameTests au plus ; `JoueurDeTest` pour tout joueur d'essai (opérateur par `getOps().add(new ServerOpListEntry(profil, 4, false))`) ; une commande de l'équipe exige le niveau 2 ; un client n'est jamais cru sur parole ; aucune classe cliente atteignable du serveur (écrans et MCEF dans `client/web` et `phone/client`, appelés par `DistExecutor`) ; textures produites par script Python/PIL (`tools/gen_textures_t1.py`) ; tout bloc dont le modèle n'occupe pas le cube reçoit `noOcclusion()` et une `getShape` exacte, et sa capture le montre collé à un mur.

En plus :
- **`gradle.properties` en ASCII seulement** (Java le lit en ISO-8859-1) : commentaires sans accents.
- **MCEF client seulement, facultatif.** `mods.toml` : `modId="mcef"`, `mandatory=false`, `versionRange="[2.1.6,2.2)"` (2.2 et plus : le fork de Keksuccino, autre API), `ordering="NONE"`, `side="CLIENT"`. Le serveur dédié démarre sans MCEF. Aucune classe de `com.cinemamod` ni de `org.cef` n'est nommée hors de `client/web/mcef/` (gardé par `McefIsolationTest`).
- **Dépendances par Gradle seulement.** MCEF : `maven.modrinth:mcef:x91l6OKB` (dépôt Modrinth déjà déclaré ; même fichier que le pack, SHA-1 ci-dessus ; repli si Modrinth refuse : `curse.maven:mcef-636236:5828187`, 213,3 Ko, page CurseForge relevée le 2026-11-19). WebDisplays : `maven.modrinth:webdisplays:DHWqG6SF` (2.0.2-1.20.1, SHA-1 `f05566e3b898de8542fa7fa79aebd2cff69b89e6`). `compileOnly` pour MCEF toujours ; `runtimeOnly` de MCEF et de WebDisplays **seulement avec `-PwithMcef`**, refusé avec une tâche serveur ou GameTest.
- **Chromium n'est pas un téléchargement de Gradle.** Au premier client lancé avec `-PwithMcef`, MCEF télécharge environ 100 Mo (chiffre de la page CraftBrowser, non mesuré) depuis `mcef-download.cinemamod.com`. **Accord explicite de Killian avant ce premier lancement** (tâche 3, étape 1). Sans accord, tout ce qui n'exige pas Chromium continue (repli, serveur, GameTests) et la preuve dit ce qui manque.
- **Le serveur fait foi** pour le code, la batterie, le propriétaire et les réglages. Le code n'est jamais envoyé au client ni écrit dans la pile (seule une empreinte salée vit dans `PhoneData`). Une vue verrouillée ne contient ni le nom du propriétaire ni les réglages. Chaque message de la page est relu par `PhoneBridgeMessages` côté client, puis tout est revérifié par `PhoneService` côté serveur (session ouverte, même téléphone en main, propriétaire, format, blocage).
- **Pages locales seulement** : chaque page porte `<meta http-equiv="Content-Security-Policy" content="default-src mod: 'unsafe-inline'; img-src mod: data:; connect-src 'none'">` ; aucune adresse `http://` ou `https://` dans `html/` ; toute adresse principale hors de `WebPages.isAllowed` est ramenée à la page de départ.
- **Réseau** : `BenchNetwork.PROTOCOL` passe de `"9"` à `"10"` **une seule fois** (tâche 5) ; `ID_PHONE_STATE = 26` (serveur → client), `ID_PHONE_ACTION = 27` (client → serveur).
- **Valeurs du lot** (réglables dans `oneforallsurvival-phone.toml`, fichier SERVER) : batterie 24 000 FE ; dépense écran allumé 2 FE/t (10 minutes d'écran) ; prise 40 FE/t (charge complète en 30 s) ; 5 essais de code, puis blocage de 60 s.
- **Preuve des tests** : `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack` ; lire « All N required tests passed » dans `run-gametest/logs/latest.log` et le rapport JUnit (`build/test-results/test/*.xml`), jamais le seul code de sortie.
- **Tout lancement de Minecraft** (GameTests, client, serveur, captures) suit la procédure du verrou : section 11 de `C:/Users/killi/.claude/CLAUDE.md` (points 1 à 8) et la note `D:/BDC/Minecraft/Deux sessions d'agents qui lancent Minecraft sur la même machine passent par un fichier de verrou et une file d'attente.md`.
  - Lire `C:/Users/killi/.claude/lancement-minecraft.txt` (blancs ou BOM seuls = vide) ; rempli : s'inscrire en fin de `lancement-minecraft-attente.txt` (`<projet> — <heure>`), surveiller en arrière-plan, jamais d'attente au premier plan ; vide : ne le prendre que si la file est vide ou si l'on est en tête.
  - Prise par un script qui relit verrou et file et écrit dans la même commande, et refuse si le verrou n'est pas vide (leçon du 2026-11-15) ; ligne `OneForAllSurvival — <ce qu'on lance> — <heure>`.
  - À la fin, réussite ou échec, vider le verrou sans le supprimer et sans BOM (`: > fichier`, ou `[IO.File]::WriteAllText($chemin, '')`), **seulement s'il contient sa propre ligne**, dans le `finally` du script qui l'a pris (leçon du 2026-11-18). Céder le verrou entre deux passes si la file n'est pas vide.
  - Se parler entre sessions (point 8). Un sous-agent qui ne peut pas écrire à une autre session le signale à la session qui l'a lancé.
  - Fenêtres d'essai sur le second écran (x = 2560, `dev/FenetreSecondEcran`, propriété `oneforallsurvival.fenetre.secondEcran`) : lire « [fenetre] second écran » au journal. Tout client passe une propriété `oneforallsurvival.*` (souris rendue par `dev/SourisLibre`) : lire « [souris] rendue ». Aucun client laissé ouvert.
  - N'arrêter que ses propres PID ; ne jamais toucher une JVM dont la ligne de commande contient `AXIONENGINE`. Un serveur resté ouvert s'arrête par RCON (`stop`). Ne jamais modifier `run/options.txt`.
  - Ces règles sont recopiées dans le brief de tout sous-agent qui lance Minecraft.
- **Git** : travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, par `git commit -- <chemins>` (jamais `git add -A`, `git mv` ni `git rm` : leçons du 2026-11-08 et du 2026-11-15 ; supprimer par `rm` seul), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/` (dont `run-mcef/`) ni `waveletNoiseTile.bin` dans un commit. Push refusé : `git pull --rebase` puis push.

## Review Focus

1. **Code forcé ou rejoué** : un client trafiqué qui enchaîne les codes, qui envoie un geste avec un ancien numéro de session, ou deux piles qui portent le même identifiant (copie créative). Attendu : 5 essais puis 60 s de blocage par téléphone (pas par joueur), aucun geste hors de la session ouverte. Tests : `cinqEssaisBloquentUneMinute` (tâche 5, lot `telephone_horloge`), `uneAutreSessionNeComptePas`, `deuxCopiesPartagentLeBlocage` (tâche 5).
2. **Le téléphone quitte la main écran ouvert** (main changée par commande, mort, objet retiré par un autre mod, déconnexion) : la session se ferme, la batterie cesse de baisser, l'écran du client se ferme. Tests : `changerDeMainFermeLeTelephone`, `mourirFermeLeTelephone`, `deconnexionFermeLaSession` (tâche 6).
3. **MCEF à moitié là** : chargé mais pas initialisé (téléchargement refusé par un antivirus), page absente, pont absent, aucune image peinte. Attendu : écran de repli avec le même état, avis une seule fois par session de jeu, jamais d'écran noir. Tests : `WebFallbackNoticeTest.uneSeuleFois` (tâche 3), passage réel avec `-PphonePage=phone/absente.html` (tâche 11).
4. **Le téléphone dans une prise cassée, explosée ou déchargée** : jamais perdu ni dupliqué ; sa charge et son identifiant restent dans la pile. Tests : `casserLaPriseRendLeTelephone`, `explosionRendLeTelephone`, `laChargeSurvitAuRechargement` (tâche 7).
5. **Entrée hostile de la page** : JSON invalide, chaîne de 100 000 caractères, geste inconnu, code en chiffres pleine chasse (`１２３４`), types faux. Attendu : rien n'est envoyé au serveur ; le serveur refuserait de toute façon. Tests : `PhoneBridgeMessagesTest` (tâche 9), `PhoneCodeTest.chiffresPleineChasseRefuses` (tâche 4).

## Choix de l'auteur du plan (à valider par Killian)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Bloc de recharge | **Prise murale** (`wall_socket`), fixée au mur, téléphone branché d'un clic droit et rendu à la main vide ; fabriquée **par tous** (tag `table_only`) | Le cahier dit « une prise alimentée en FE » ; un téléphone pour tous doit se recharger sans métier |
| Usure de la prise | Elle hérite de l'usure et des pannes du lot 2 (`ElectricMachineBlockEntity`) | Machine de base commune, réparée par l'ingénieur comme les autres |
| Autres chargeurs | La pile expose `ForgeCapabilities.ENERGY` en réception (`ItemBattery.Provider`), comme la lampe frontale | Un chargeur d'objets d'un autre mod reste une prise FE |
| Batterie | Elle ne baisse qu'écran allumé (écran verrouillé compris) ; vide, l'écran se ferme et le téléphone refuse de s'ouvrir | « Se vide à l'usage » ; la veille viendra avec le réseau (T2) si besoin |
| Propriétaire | Le premier joueur qui ouvre un téléphone neuf choisit le code et en devient propriétaire ; seul lui change le code ou efface le téléphone (« Effacer le téléphone » dans Réglages, code exigé) ; l'équipe : `/phone reset` sur le téléphone tenu | Donner ou vendre un téléphone sans l'équipe ; code oublié réglé par l'équipe |
| Ouverture | Chaque ouverture repart verrouillée | Comme un vrai téléphone ; un vol reste verrouillé |
| Réglages du lot | Code, Batterie, Fond d'écran (4, en CSS), Mode avion (gardé, sans effet avant T2), Informations ; « Carte SIM », « Opérateur » et « Abonnement » affichés « Aucune carte SIM » | Feature 101 ; la SIM arrive au lot T2 |
| Accueil | Widgets heure et batterie, l'appli Réglages seule (aucune icône d'appli qui n'existe pas) | Ne rien promettre que le lot ne livre pas |
| Rendu | Cadre du téléphone dessiné par le jeu, page transparente aux coins ; mise en page pour 390 px de large, mise à l'échelle par `setZoomLevel` | Même rendu à toutes les tailles de fenêtre |
| Seuils de performance | Pendant : au moins 80 % des images par seconde d'avant ; après fermeture : au moins 90 % ; tas après GC au plus 64 Mo au-dessus d'avant ; cinq ouvertures de suite sans croissance de plus de 20 Mo par ouverture | Le cahier demande la mesure, pas un seuil ; ceux-ci rendent l'échec lisible |
| Ombre, urgences | Rien au lot T1 (pas de bouton « Urgence » sur l'écran verrouillé) | Lot T3 |

---

### Task 1 : dépendances de MCEF et garde d'isolement

**Files:**
- Modify: `build.gradle` (bloc `-PwithMcef`, propriétés du test `oneforallsurvival.mainClasses` = dossiers des classes de `main`, `oneforallsurvival.mainResources` = `src/main/resources`), `gradle.properties` (`mcef_version=x91l6OKB`, `webdisplays_version=DHWqG6SF`, commentaires ASCII), `src/main/resources/META-INF/mods.toml` (dépendance facultative), `.gitignore` si `run-mcef/` n'y tombe pas déjà sous `run*/`
- Test: `src/test/java/dev/oneforallsurvival/client/web/McefIsolationTest.java`

**Interfaces:**
- Produces : `compileOnly fg.deobf("maven.modrinth:mcef:${mcef_version}")` ; avec `-PwithMcef` : `runtimeOnly` de MCEF et de `maven.modrinth:webdisplays:${webdisplays_version}`, propriété de lancement `mcef.libraries.path` = `${projectDir}/run-mcef/mcef-libraries` pour `client` et ses enfants ; `-PwithMcef` avec une tâche dont le nom contient `Server` ou `GameTest` → `GradleException("MCEF est client seulement : -PwithMcef ne va qu'avec un client")`.
- Produces : la garde lue par toutes les tâches suivantes : aucune classe hors de `dev/oneforallsurvival/client/web/mcef/` ne référence `com/cinemamod/` ni `org/cef/` dans son pool de constantes ; aucune classe hors d'un paquet `…/client/…` ne référence `dev/oneforallsurvival/client/web/mcef/`.

- [ ] **Step 1 : écrire le test** — `McefIsolationTest` parcourt les `.class` du dossier donné par `System.getProperty("oneforallsurvival.mainClasses")` et lit leur pool de constantes en octets :
  - `seulLePaquetMcefNommeMcef` : la liste des classes fautives est vide (message : la liste) ;
  - `rienHorsDuClientNeNommeLePaquetMcef` ;
  - `leDossierEstLu` : au moins 500 classes lues (garde contre un dossier vide).
- [ ] **Step 2 : le lancer avant le réglage** — `./gradlew --offline test --tests '*McefIsolationTest*'` ; attendu : `leDossierEstLu` échoue (propriété absente).
- [ ] **Step 3 : écrire le bloc Gradle, `gradle.properties` et `mods.toml`.** Première résolution en ligne permise (téléchargement de Gradle) : `./gradlew compileJava`.
- [ ] **Step 4 : contrôler le jar résolu** — chercher `mcef-x91l6OKB.jar` (ou le nom remappé) dans le cache de Gradle et comparer son SHA-1 du jar d'origine à `6d9fa1433d509be242d1d74282d02a0fbebde110` ; différent : arrêter et le dire.
- [ ] **Step 5 : vérifier** — `./gradlew --offline test` : `McefIsolationTest` vert (3 tests), aucun autre test changé ; `./gradlew --offline runGameTestServer -PwithMcef` échoue avec le message ci-dessus (sans lancer Minecraft).
- [ ] **Step 6 : commit** — `build(telephone): MCEF 2.1.6 en dependance client facultative`.

### Task 2 : outils purs du socle web

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/client/web/WebPages.java`, `WebScript.java`, `WebMessage.java`, `WebFailure.java`
- Test: `src/test/java/dev/oneforallsurvival/client/web/WebPagesTest.java`, `WebScriptTest.java`, `WebMessageTest.java`, `WebResourcesTest.java`

**Interfaces:**
- Produces :
  - `final class WebPages` : `static final String BASE = "mod://oneforallsurvival/"` ; `static String url(String path)` (refuse `IllegalArgumentException` : vide, majuscule, `..`, `\`, `/` de tête, `:`) ; `static boolean isAllowed(String url)` (vrai pour `about:blank` et ce qui commence par `BASE` sans `..`) ; `static String resourcePath(String path)` = `"/assets/oneforallsurvival/html/" + path`.
  - `final class WebScript` : `static String call(String function, JsonElement... args)` ; `function` doit valoir `[A-Za-z_$][A-Za-z0-9_$.]*`, sinon `IllegalArgumentException` ; arguments par `Gson` (`disableHtmlEscaping` interdit) ; `\u2028` et `\u2029` toujours échappés.
  - `final class WebMessage` : `static final int MAX_LENGTH = 4096` ; `static Optional<JsonObject> parse(String raw)` : vide si `raw` nul, plus long que `MAX_LENGTH`, JSON invalide, pas un objet, ou sans champ `"a"` chaîne de 1 à 32 caractères.
  - `enum WebFailure { ABSENT, NOT_INITIALIZED, CREATE_FAILED, LOAD_ERROR, NO_READY }` ; `String translationKey()` = `"message.oneforallsurvival.web.fallback." + name().toLowerCase(Locale.ROOT)`.

- [ ] **Step 1 : tests qui échouent** :
  - `WebPagesTest` : `url("phone/index.html")` = `"mod://oneforallsurvival/phone/index.html"` ; `url("Phone/x.html")`, `url("../x")`, `url("/x")`, `url("a\\b")` lèvent ; `isAllowed("about:blank")`, `isAllowed(url("web/selftest.html"))` vrais ; `isAllowed("https://www.google.com")`, `isAllowed("mod://webdisplays/x.html")`, `isAllowed("mod://oneforallsurvival/../x")`, `isAllowed("file:///C:/x")` faux.
  - `WebScriptTest` : `call("phone.render", obj)` commence par `phone.render(` et finit par `)` ; une chaîne `"</script>\u2028x"` ressort avec `\u2028` échappé et se relit à l'identique par `JsonParser` ; `call("alert(1);x", …)` lève.
  - `WebMessageTest` : `parse("{\"a\":\"ready\"}")` présent ; vides pour `null`, `"[]"`, `"{"`, `"{\"a\":5}"`, `"{\"b\":\"x\"}"`, une chaîne de 100 000 caractères, `"{\"a\":\"\"}"`.
  - `WebResourcesTest` (parcourt tout `assets/oneforallsurvival/html/` sous le dossier `oneforallsurvival.mainResources`, ce qui couvre aussi les pages des tâches 3 et 8 et celles du lore plus tard) : chaque chemin est en minuscules ; chaque `.html` contient la balise CSP des Global Constraints ; aucun fichier ne contient `http://` ni `https://` ; `dossierAbsentToleré` : tant que `html/` n'existe pas, le test passe sans rien lire.
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*client.web*'` ; attendu : échec de compilation.
- [ ] **Step 3 : implémenter** les quatre classes (aucune classe de Minecraft cliente ; Gson seulement).
- [ ] **Step 4 : vérifier** — même commande ; attendu : tout vert.
- [ ] **Step 5 : commit** — `feat(web): adresses, appels et messages du socle navigateur`.

### Task 3 : socle MCEF et page d'essai

**Files:**
- Create: `client/web/WebView.java`, `client/web/WebBridge.java`, `client/web/WebAvailability.java`, `client/web/WebViews.java`, `client/web/WebScreen.java`, `client/web/WebFallbackNotice.java`, `client/web/mcef/McefWebView.java`, `client/web/mcef/McefHooks.java`, `client/web/mcef/McefProbe.java`
- Create: `src/main/resources/assets/oneforallsurvival/html/web/bridge.js`, `html/web/selftest.html`
- Create: `src/devcontent/java/dev/oneforallsurvival/dev/WebEssai.java` (commande cliente `/webessai` en développement seulement)
- Modify: `fr_fr.json`, `en_us.json` (clés `message.oneforallsurvival.web.fallback.*`)
- Test: `src/test/java/dev/oneforallsurvival/client/web/WebFallbackNoticeTest.java`

**Interfaces:**
- Consumes : `WebPages`, `WebScript`, `WebMessage`, `WebFailure` (tâche 2).
- Produces :
  - `interface WebView` : `void resize(int pixelWidth, int pixelHeight)`, `void setScale(double scale)`, `void draw(GuiGraphics g, int x, int y, int width, int height)`, `void mouseMove(int px, int py)`, `void mousePress(int px, int py, int button)`, `void mouseRelease(int px, int py, int button)`, `void mouseWheel(int px, int py, double amount)`, `void keyPress(int key, int scan, int modifiers)`, `void keyRelease(int key, int scan, int modifiers)`, `void charTyped(char c, int modifiers)`, `void focus(boolean)`, `void run(String script)`, `boolean painted()`, `void close()`. Aucun type de MCEF dans la signature.
  - `interface WebBridge { void onMessage(JsonObject message); default void onFailure(WebFailure failure) {} }` ; les messages sont déjà passés par `WebMessage.parse`, sur le fil du client (`Minecraft.execute`).
  - `WebAvailability.ready()` : `ModList.get().isLoaded("mcef") && McefProbe.initialized()` ; `WebAvailability.failure()` → `ABSENT` ou `NOT_INITIALIZED`, nul si prêt. Propriété de développement `oneforallsurvival.web.forceFallback=true` : `ready()` faux, `failure()` = `ABSENT`.
  - `WebViews.open(String url, WebBridge bridge)` → `Optional<WebView>` : vide si pas `ready()` ou si la création lève (`bridge.onFailure(CREATE_FAILED)`) ; n'appelle `McefWebView` qu'après la vérification.
  - `McefHooks.install()` : une fois par session de jeu (appelé par `McefWebView` à la première création) : `MCEF.getClient().addLoadHandler` (`onLoadError` du cadre principal → `LOAD_ERROR` pour le navigateur visé), `addDisplayHandler` (`onAddressChange` du cadre principal hors `WebPages.isAllowed` → `loadURL` de la page de départ), routeur `CefMessageRouter.create(new CefMessageRouterConfig("oneforallQuery", "oneforallQueryCancel"), handler)` ajouté au client partagé ; chaque rappel ne sert que les navigateurs inscrits par `McefHooks.register(CefBrowser, McefWebView)` et ignore les autres (ceux de WebDisplays). Le routeur n'est jamais retiré ; `unregister` à la fermeture.
  - `McefWebView implements WebView` : `createBrowser(url, true)` ; `setScale(s)` → `setZoomLevel(Math.log(s) / Math.log(1.2))` ; `draw` selon l'exemple (shader de position, texture et couleur, mélange activé pour les coins transparents) ; `mouseWheel` sans modificateur ; `run` par `executeJavaScript(script, getURL(), 0)` ; `painted()` vrai après la première texture non nulle.
  - `abstract class WebScreen extends Screen` : `protected WebScreen(Component title, String page, WebBridge bridge)` ; `protected abstract Rect frame(int guiWidth, int guiHeight)` (record `Rect(int x, int y, int width, int height)` dans `WebScreen`) ; `protected abstract void onFallback(WebFailure failure)` ; `protected void drawFrame(GuiGraphics g, Rect frame)` (vide par défaut) ; `protected final void run(String script)`. Vue créée dans `init`, taille = cadre × `getGuiScale()`, mise à l'échelle par la sous-classe ; souris relayée seulement dans le cadre (le relâchement toujours) ; touches relayées sauf Échap, qui ferme ; `isPauseScreen()` faux ; si `{"a":"ready"}` n'arrive pas dans les 100 ticks de client : `onFallback(NO_READY)` ; `removed()` ferme la vue.
  - `WebFallbackNotice.warnOnce(WebFailure)` : la première fois de la session de jeu seulement, message de chat traduit (`failure.translationKey()`) ; `static boolean shouldWarn()` et `static void markWarned()` pour le test, `resetForTest()`.
  - `html/web/bridge.js` : `window.oneforall = { send(message), onReady(fn) }` ; `send` appelle `window.oneforallQuery({request: JSON.stringify(message), onSuccess(){}, onFailure(){}})` ; absent (hors jeu) et adresse finissant par `#mock` : journal de la console et rappel `window.oneforall.mock(message)` si la page en définit un ; menu contextuel coupé.
  - `html/web/selftest.html` : envoie `{"a":"ready"}`, affiche les messages reçus par `window.selftest.echo(x)`, un champ texte (clavier), une liste longue (molette), un bouton (clic) qui envoie `{"a":"clic"}` ; coins transparents.
  - `WebEssai` (devcontent, client) : `/webessai` ouvre un `WebScreen` plein cadre sur `web/selftest.html` dont le pont répond à chaque message par `selftest.echo(message)` et l'écrit au journal `[web] reçu …`.

- [ ] **Step 1 : demander l'accord de Killian** pour le téléchargement de Chromium par MCEF au premier client `-PwithMcef` (hôte `mcef-download.cinemamod.com`, environ 100 Mo d'après CraftBrowser, dossier `run-mcef/mcef-libraries`, jamais commité). Alternative à lui proposer : copier le dossier `mcef-libraries` d'une installation du pack déjà faite sur ce PC, puis vérifier que MCEF ne télécharge rien (journal sans « Downloading Chromium Embedded Framework »). **Sans réponse, faire les étapes 2 à 5 et 8, et noter les étapes 6 et 7 comme en attente.**
- [ ] **Step 2 : test qui échoue** — `WebFallbackNoticeTest.uneSeuleFois` : après `resetForTest()`, `shouldWarn()` vrai ; après `markWarned()`, faux pour toute autre `WebFailure`.
- [ ] **Step 3 : vérifier qu'il échoue** — `./gradlew --offline test --tests '*WebFallbackNoticeTest*'`.
- [ ] **Step 4 : implémenter** les classes, `bridge.js`, `selftest.html`, `WebEssai` et les clés de langue.
- [ ] **Step 5 : vérifier sans Minecraft** — `./gradlew --offline test` : tout vert, `McefIsolationTest` compris ; `selftest.html#mock` ouvert dans Chrome affiche la page (contrôle de mise en page seulement).
- [ ] **Step 6 : premier lancement réel** (procédure du verrou, second écran) — `./gradlew runClient -PwithPack -PwithMcef` avec une propriété `oneforallsurvival.*` ; monde neuf de développement ; `/webessai`. Relever et écrire dans le rapport de tâche : la page s'affiche (sinon « Resource … NOT found! » au journal), coins transparents, orientation, `[web] reçu {"a":"ready"}`, écho visible, clic, molette, frappe au clavier, zoom à 1,0 et 1,5 mesuré sur capture (largeur d'un bloc de 100 px CSS), Échap ferme, réouverture dix fois sans erreur au journal. **Ne pas appuyer sur F10.**
- [ ] **Step 7 : si la ressource n'est pas servie** — remplacer dans `WebPages.BASE` le schéma par `https://oneforallsurvival.local/`, servi par une fabrique `CefApp.registerSchemeHandlerFactory("https", "oneforallsurvival.local", …)` posée par `McefHooks.install()` et une classe `client/web/mcef/ModResourceHandler implements CefResourceHandler` qui lit `WebPages.resourcePath` par le chargeur de classes du mod (signatures de `CefResourceHandler` lues dans le jar avant d'écrire) ; adapter la CSP (`default-src https://oneforallsurvival.local 'unsafe-inline'`) et `WebPagesTest` ; rejouer l'étape 6. Le noter dans le rapport et dans la preuve : le lore en dépend.
- [ ] **Step 8 : passage sans MCEF** — `./gradlew runClient -PwithPack` ; `/webessai` → aucun écran web, message `fallback.absent` une seule fois sur deux appels ; journal sans `ClassNotFoundException` ni `NoClassDefFoundError`.
- [ ] **Step 9 : commit** — `feat(web): socle navigateur MCEF, pont JavaScript et repli`.

### Task 4 : l'objet téléphone et ses données

**Files:**
- Create: `phone/PhoneRegistry.java`, `phone/PhoneConfig.java`, `phone/PhoneItem.java`, `phone/PhoneCode.java`, `phone/PhoneLock.java`, `phone/PhoneData.java`, `phone/PhoneWallpapers.java`, `phone/PhoneCommands.java`, `phone/PhoneService.java` (amorce)
- Modify: `OneForAllSurvival.java` (registre, config `oneforallsurvival-phone.toml` en SERVER, commandes), `bench/BenchRegistry.java` (onglet : `PhoneRegistry.tabItems()`), `data/oneforallsurvival/tags/items/table_only.json` (`oneforallsurvival:phone`), `data/oneforallsurvival/recipes/phone.json`, `models/item/phone.json`, `fr_fr.json`, `en_us.json`
- Test: `src/test/java/dev/oneforallsurvival/phone/PhoneCodeTest.java`, `PhoneLockTest.java`, `PhoneDataTest.java`

**Interfaces:**
- Produces :
  - `PhoneRegistry.PHONE` (objet `phone`, `stacksTo(1)`), `PhoneRegistry.register(IEventBus)`, `tabItems()`.
  - `PhoneConfig` (mêmes lecteurs à repli que `LightConfig`) : `capacity()` 24 000 (1 à 10 000 000), `drainPerTick()` 2 (0 à 1 000), `socketChargePerTick()` 40 (1 à 200), `maxAttempts()` 5 (1 à 100), `lockoutSeconds()` 60 (0 à 86 400).
  - `PhoneItem extends Item` : `static Optional<UUID> phoneId(ItemStack)` (NBT `phone_id`), `static UUID ensurePhoneId(ItemStack)` (serveur), batterie par `ItemBattery` (`stored`, `store`, barre, `reequip`), `initCapabilities` → `ItemBattery.Provider` ; `use` : côté serveur `PhoneService.open(player, hand)`, `sidedSuccess`.
  - Amorce `PhoneService` : `enum Outcome { OPENED, EMPTY_BATTERY, DONE, REFUSED, NO_SESSION }` et `static Outcome open(ServerPlayer, InteractionHand)` qui rend `NO_SESSION` sans rien faire ; la tâche 5 la remplit.
  - `PhoneCode` : `static boolean isValid(String code)` (exactement quatre caractères `'0'`…`'9'`, rien d'autre) ; `static String newSalt(RandomSource)` (16 octets en hexadécimal) ; `static String hash(String salt, String code)` (SHA-256 hexadécimal de `salt + ":" + code`) ; `static boolean matches(String salt, String hash, String code)` (comparaison à temps constant, `MessageDigest.isEqual`).
  - `PhoneLock` (pur) : `record Outcome(boolean accepted, int failures, long lockedUntil)` ; `static Outcome attempt(boolean correct, int failures, long lockedUntil, long now, int maxAttempts, long lockoutMillis)` : bloqué (`now < lockedUntil`) → refusé sans compter ; juste → `failures = 0` ; faux → `failures + 1`, et à `maxAttempts` : `lockedUntil = now + lockoutMillis`, `failures = 0`.
  - `PhoneData extends SavedData` (`oneforallsurvival_phones`, sur l'overworld) : `record PhoneRecord(@Nullable UUID owner, String ownerName, @Nullable String salt, @Nullable String codeHash, int failures, long lockedUntil, String wallpaper, boolean airplane)` ; `static PhoneData get(MinecraftServer)` ; `PhoneRecord record(UUID phoneId)` (neuf : sans propriétaire, fond `PhoneWallpapers.DEFAULT`) ; `void put(UUID phoneId, PhoneRecord)` (marque sale) ; `void erase(UUID phoneId)` ; `CompoundTag save(CompoundTag)` / `static PhoneData load(CompoundTag)`.
  - `PhoneWallpapers` : `DEFAULT = "aube"`, `IDS = List.of("aube", "lagon", "jungle", "nuit")`, `isValid(String)`.
  - `PhoneCommands` : `/phone reset` (niveau 2) efface `PhoneData` du téléphone tenu en main principale par l'exécutant ; message `message.oneforallsurvival.phone.reset` ou `…phone.reset.none`.
  - Recette de table (choix de l'auteur, ingrédients vanilla, aucune condition de mod) : `["NGN","RCR","NIN"]`, `N` pépite de fer, `G` vitre, `R` redstone, `C` lingot de cuivre, `I` lingot de fer.

- [ ] **Step 1 : tests qui échouent** :
  - `PhoneCodeTest` : `isValid("0000")`, `isValid("9137")` vrais ; `"123"`, `"12345"`, `"12a4"`, `" 1234"`, `"1234 "`, `""`, `null` faux ; `chiffresPleineChasseRefuses` : `"１２３４"` et `"١٢٣٤"` faux ; `matches(sel, hash(sel, "4821"), "4821")` vrai, `"4822"` faux ; deux sels différents donnent deux empreintes différentes ; l'empreinte ne contient pas `"4821"`.
  - `PhoneLockTest` : quatre faux → `failures == 4`, pas de blocage ; cinquième faux à `now = 1 000` → `lockedUntil == 61 000`, `failures == 0` ; juste à `now = 30 000` → refusé, `lockedUntil` inchangé ; juste à `now = 61 000` → accepté ; juste après deux faux → `failures == 0`.
  - `PhoneDataTest` : aller-retour `save`/`load` identique pour deux téléphones (propriétaire, sel, empreinte, essais, blocage, fond, avion) ; `record` d'un identifiant inconnu donne un téléphone neuf sans propriétaire ; `erase` puis `record` donne un neuf ; un fond inconnu relu depuis le NBT redevient `DEFAULT`.
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*phone*'`.
- [ ] **Step 3 : implémenter** les classes, la recette, le tag, le modèle (texture `item/phone`, produite à la tâche 10 ; violette d'ici là) et les textes (`item.oneforallsurvival.phone` : « Téléphone » / « Phone »).
- [ ] **Step 4 : vérifier** — `./gradlew --offline test` ; attendu : tout vert.
- [ ] **Step 5 : commit** — `feat(telephone): objet, code et donnees du telephone`.

### Task 5 : session, paquets et code côté serveur

**Files:**
- Create: `phone/PhoneSession.java`, `phone/PhoneStatePacket.java`, `phone/PhoneActionPacket.java`
- Modify: `phone/PhoneService.java` (amorce de la tâche 4), `bench/BenchNetwork.java` (protocole `"10"`, `ID_PHONE_STATE = 26`, `ID_PHONE_ACTION = 27`), `phone/PhoneItem.java`, `OneForAllSurvival.java` (tic serveur, déconnexion), `src/devcontent/java/dev/oneforallsurvival/dev/JoueurDeTest.java` (`etatsTelephoneRecus()`), langues
- Test: `src/test/java/dev/oneforallsurvival/phone/PhoneStatePacketTest.java`, `src/devcontent/java/dev/oneforallsurvival/dev/TelephoneGameTests.java` (lots `telephone_a` et `telephone_horloge`)

**Interfaces:**
- Consumes : tâche 4 entière ; `EconomyClock.now()`, `EconomyCalendar.configuredZone()`.
- Produces :
  - `record PhoneStatePacket(int session, Kind kind, Lock lock, int battery, long serverMillis, int zoneOffsetSeconds, String wallpaper, boolean airplane, boolean owner, String ownerName, int attemptsLeft, int lockoutSeconds, Result last, CloseReason reason)` ; `enum Kind { OPEN, UPDATE, CLOSE }`, `enum Lock { SETUP, LOCKED, UNLOCKED }`, `enum Result { NONE, WRONG_CODE, LOCKED_OUT, INVALID_CODE, CODE_CHANGED, NOT_OWNER, ERASED }`, `enum CloseReason { NONE, CLOSED, BATTERY_EMPTY, PHONE_GONE, ERASED }` ; `encode`/`decode`/`handle` (client : `DistExecutor` vers `PhoneClient.accept`, tâche 9 ; d'ici là un appel vide). Hors `UNLOCKED`, `ownerName` vaut `""`, `wallpaper` vaut le fond (visible sur l'écran verrouillé), `airplane` faux.
  - `record PhoneActionPacket(int session, Action action, String first, String second, boolean flag)` ; `enum Action { UNLOCK, SETUP, LOCK, CHANGE_CODE, WALLPAPER, AIRPLANE, ERASE, CLOSE }` ; `MAX_WIRE_TEXT = 16` (au-delà, le paquet ne se lit pas).
  - `PhoneSession` (transitoire) : `int id`, `UUID phoneId`, `InteractionHand hand`, `Lock lock`, `ItemBattery.Prepaid prepaid`.
  - `PhoneService` (`Outcome` de la tâche 4) : `static Outcome open(ServerPlayer, InteractionHand)` (pose l'identifiant, refuse une batterie vide par message `message.oneforallsurvival.phone.empty`, remplace une session ouverte, numéro de session croissant, envoie `OPEN`) ; `static Outcome act(ServerPlayer, PhoneActionPacket)` (session absente ou d'un autre numéro, faux joueur, téléphone plus en main : `NO_SESSION`, rien n'est envoyé ; frein : un geste tous les 2 tics) ; `static Optional<PhoneSession> session(UUID player)` ; `static void close(ServerPlayer, CloseReason)` (envoie `CLOSE`) ; `onServerTick`, `onLoggedOut`. Règles des gestes :
    - `SETUP(first)` : seulement en `SETUP` ; code valide → propriétaire = joueur, sel et empreinte posés, `UNLOCKED` ; sinon `INVALID_CODE`.
    - `UNLOCK(first)` : par `PhoneLock.attempt` avec `PhoneConfig` ; juste → `UNLOCKED` ; faux → `WRONG_CODE` et `attemptsLeft` ; bloqué → `LOCKED_OUT` et `lockoutSeconds`.
    - `LOCK` → `LOCKED`. `WALLPAPER(first)` et `AIRPLANE(flag)` : `UNLOCKED` seulement, fond dans `PhoneWallpapers.IDS`.
    - `CHANGE_CODE(first = ancien, second = nouveau)` et `ERASE(first = code)` : `UNLOCKED`, propriétaire seulement (sinon `NOT_OWNER`), ancien code juste (compte un essai faux sinon) ; `ERASE` efface `PhoneData` et ferme par `CloseReason.ERASED`.
    - `CLOSE` : fin de session, rien d'autre.
  - `JoueurDeTest.etatsTelephoneRecus()` : les `PhoneStatePacket` reçus depuis le dernier appel, décodés des octets envoyés.

- [ ] **Step 1 : tests qui échouent** :
  - `PhoneStatePacketTest.allerRetour` : `encode` puis `decode` rend un record égal (chaque énumération essayée) ; `actionTropLongueNeSeLitPas` : un `first` de 17 caractères lève au décodage.
  - `TelephoneGameTests`, lot `telephone_a` (8 au plus) :
    - `premiereOuvertureDemandeUnCode` : `OPEN`, `SETUP`, `ownerName` vide ;
    - `leCodeChoisiVerrouilleLaReouverture` : `SETUP("4821")` → `UNLOCKED`, propriétaire = joueur ; fermer, rouvrir → `LOCKED` ;
    - `mauvaisCodePuisBonCode` : `"0000"` → `WRONG_CODE`, `attemptsLeft == 4` ; `"4821"` → `UNLOCKED` ;
    - `unAutreJoueurNeVoitRienAvantLeCode` : B ouvre le téléphone de A → `LOCKED`, `ownerName == ""`, `owner` faux ; `WALLPAPER` de B refusé (aucun changement dans `PhoneData`) ;
    - `seulLeProprietaireChangeLeCode` : B, déverrouillé avec le bon code, `CHANGE_CODE` → `NOT_OWNER` ; A le fait → `CODE_CHANGED`, l'ancien code ne déverrouille plus ;
    - `uneAutreSessionNeComptePas` : un `UNLOCK` juste avec un numéro de session périmé ne déverrouille rien et ne renvoie rien ;
    - `sansSessionRienNeBouge` : `act` sans `open` → `NO_SESSION`, aucun paquet ;
    - `effacerRendLeTelephoneNeuf` : `ERASE` par le propriétaire → `CLOSE` raison `ERASED` ; réouverture → `SETUP`.
  - Lot `telephone_horloge` (`@BeforeBatch` : `EconomyClock.setClock` fixe ; `@AfterBatch` : `EconomyClock.reset()`) :
    - `cinqEssaisBloquentUneMinute` : cinq faux → `LOCKED_OUT`, `lockoutSeconds == 60` ; le bon code à +30 s refusé ; à +61 s accepté ;
    - `deuxCopiesPartagentLeBlocage` : deux piles au même `phone_id` (copie créative), cinq faux sur la première → la seconde est bloquée aussi ;
    - `leBlocageSurvitALaFermeture` : bloqué, fermer, rouvrir → toujours `LOCKED_OUT` au premier essai.
- [ ] **Step 2 : vérifier qu'ils échouent** (JUnit, puis GameTests par la procédure du verrou) ; attendu : échec de compilation.
- [ ] **Step 3 : implémenter.** Monter le protocole d'un cran (une seule fois pour le lot), enregistrer les deux paquets, brancher `PhoneItem.use`.
- [ ] **Step 4 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed », les 11 nouveaux compris.
- [ ] **Step 5 : commit** — `feat(telephone): session, code et paquets cote serveur`.

### Task 6 : la batterie à l'usage

**Files:**
- Modify: `phone/PhoneService.java` (dépense au tic, mises à jour, fermetures), langues
- Test: `TelephoneGameTests` (lot `telephone_b`)

**Interfaces:**
- Consumes : `PhoneSession.prepaid`, `ItemBattery.Prepaid.pay(stack, PhoneConfig.drainPerTick(), PhoneConfig.capacity())` (dépense par blocs de 20 tics), `PhoneService.close`.
- Produces : à chaque tic serveur, pour chaque session : joueur hors ligne, mort, ou pile en main d'un autre `phone_id` → `close(PHONE_GONE)` ; `pay` faux → batterie mise à 0, `close(BATTERY_EMPTY)`, message `message.oneforallsurvival.phone.battery_empty` ; pourcentage changé (`stored * 100 / capacity`, arrondi vers le bas) → `UPDATE`. Écran fermé : aucune dépense.

- [ ] **Step 1 : GameTests qui échouent** (lot `telephone_b`) :
  - `laBatterieSeVideEcranOuvert` : 24 000 FE, ouvert 200 tics → entre 23 560 et 23 600 FE ;
  - `ecranFermeRienNeSeVide` : ouvert puis `CLOSE`, 200 tics → charge inchangée ;
  - `batterieVideEteintLeTelephone` : 30 FE → `CLOSE` raison `BATTERY_EMPTY` dans les 40 tics, charge 0, session absente ;
  - `unTelephoneVideNeSOuvrePas` : 0 FE → `open` rend `EMPTY_BATTERY`, aucun `OPEN` ;
  - `changerDeMainFermeLeTelephone` : la pile passe dans une autre case (`setItem`) → `CLOSE` raison `PHONE_GONE` au tic suivant, plus de dépense ;
  - `mourirFermeLeTelephone` : `kill` du joueur d'essai → `CLOSE` ;
  - `deconnexionFermeLaSession` : `quitter()` → session absente, charge de la pile figée ;
  - `lePourcentageSuitLaCharge` : un `UPDATE` reçu à chaque point de pourcentage perdu, jamais deux fois le même.
- [ ] **Step 2 : vérifier qu'ils échouent** (procédure du verrou).
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed ».
- [ ] **Step 5 : commit** — `feat(telephone): batterie qui se vide ecran allume`.

### Task 7 : la prise murale

**Files:**
- Create: `phone/WallSocketBlock.java`, `phone/WallSocketBlockEntity.java`
- Modify: `phone/PhoneRegistry.java` (bloc, objet, entité), `table_only.json` (`oneforallsurvival:wall_socket`), `recipes/wall_socket.json`, `blockstates/wall_socket.json`, `models/block/wall_socket*.json`, `models/item/wall_socket.json`, `loot_tables/blocks/wall_socket.json`, langues
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/PriseGameTests.java` (lot `prise`)

**Interfaces:**
- Consumes : `ElectricMachineBlock`, `ElectricMachineBlockEntity` (lot 0), `ItemBattery`, `PhoneItem`, `PhoneConfig.socketChargePerTick()`.
- Produces :
  - `WallSocketBlock extends ElectricMachineBlock` : propriétés `FACING` (horizontal, face qui regarde la pièce) et `PLUGGED` ; posé contre la face cliquée d'un mur (refusé au sol et au plafond) ; `canSurvive` exige un mur plein derrière, sinon il tombe et rend le téléphone ; forme plate de 2 px contre le mur (`getShape` exacte), `noOcclusion()` ; `use` : téléphone en main et prise vide → branché (pile entière déplacée) ; main vide et prise occupée → téléphone rendu en main ; autre objet ou prise occupée → message `message.oneforallsurvival.wall_socket.busy` ; `onRemove` lâche le téléphone (`Containers.dropItemStack`).
  - `WallSocketBlockEntity extends ElectricMachineBlockEntity` : capacité 4 000, réception `TOWN_MAX_RECEIVE` ; `wantsToWork()` = téléphone branché et pas plein ; `energyPerTick()` = `PhoneConfig.socketChargePerTick()` ; `workTick()` ajoute ce montant à la pile, borné à `PhoneConfig.capacity()` ; `ItemStack phone()`, `void setPhone(ItemStack)` ; NBT `phone` ; `PLUGGED` réglé par `adjustState`. Pas de capacité d'objets : ni entonnoir ni machine n'en sort le téléphone.
  - Recette de table (choix de l'auteur) : `["SCS","SRS"]`, `S` pierre lisse, `C` lingot de cuivre, `R` redstone.

- [ ] **Step 1 : GameTests qui échouent** (lot `prise`, source `DevBlocks.testSource()` raccordée derrière la prise) :
  - `laPriseAlimenteeRecharge` : téléphone à 0, 100 tics → au moins 3 600 FE (usure et rendement compris), état `RUNNING` ; plein → `STOPPED`, charge = capacité ;
  - `sansCourantRienNeCharge` : sans source → `NO_POWER`, charge 0 ;
  - `brancherPuisReprendre` : clic droit d'un `JoueurDeTest` avec le téléphone → main vide, `PLUGGED` vrai ; clic main vide → téléphone rendu avec le même `phone_id` et sa charge ;
  - `unePriseOccupeeRefuse` : second téléphone refusé, il reste en main ;
  - `unObjetQuelconqueNeSeBranchePas` : un lingot de fer reste en main, `PLUGGED` faux ;
  - `casserLaPriseRendLeTelephone` : `destroyBlock` → un `ItemEntity` du téléphone, même `phone_id` et même charge, et un seul ;
  - `explosionRendLeTelephone` : explosion de puissance 4 au contact → le téléphone est au sol, jamais perdu ;
  - `laChargeSurvitAuRechargement` : `saveWithoutMetadata` puis `load` sur une copie → même pile.
  - Lot `telephone_recettes` : `leTelephoneEtLaPriseSeFabriquentALaTable` : `BenchCatalog.isTableOnly` vrai pour les deux, et une recette de table dont le résultat est chacun d'eux existe dans le `RecipeManager`.
- [ ] **Step 2 : vérifier qu'ils échouent** (procédure du verrou).
- [ ] **Step 3 : implémenter** bloc, entité, recette, état de bloc (variantes `facing` × `plugged` × `state`, écrites par `tools/gen_textures_t1.py` à la tâche 10 ; ici, modèles provisoires du même nom), butin.
- [ ] **Step 4 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed ».
- [ ] **Step 5 : commit** — `feat(telephone): prise murale qui recharge le telephone`.

### Task 8 : les pages du téléphone

**Files:**
- Create: `src/main/resources/assets/oneforallsurvival/html/phone/index.html`, `phone/phone.css`, `phone/phone.js`
- Test: `WebResourcesTest` (tâche 2, inchangé : il lit les nouveaux fichiers)

**Interfaces:**
- Consumes : `html/web/bridge.js` (tâche 3) ; la vue JSON de `PhoneViewJson` (tâche 9) : `{session, lock: "SETUP"|"LOCKED"|"UNLOCKED", battery, serverMillis, zoneOffsetSeconds, wallpaper, airplane, owner, ownerName, attemptsLeft, lockoutSeconds, last}`.
- Produces (contrat page ↔ Java, à recopier tel quel dans la tâche 9) :
  - Java → page : `window.phone.render(view)` à chaque état reçu.
  - Page → Java (par `oneforall.send`) : `{"a":"ready"}` ; `{"a":"layout","rects":{"<id>":[x,y,w,h],…}}` en px CSS, envoyé après chaque changement d'écran (touches du clavier `key-0` à `key-9`, `key-back`, `app-settings`, `home-bar`, lignes de Réglages `row-<nom>`) ; `{"a":"unlock","code":"…"}` ; `{"a":"setup","code":"…"}` ; `{"a":"lock"}` ; `{"a":"changeCode","old":"…","new":"…"}` ; `{"a":"wallpaper","id":"…"}` ; `{"a":"airplane","on":true|false}` ; `{"a":"erase","code":"…"}`.
  - Mise en page pour une largeur de 390 px CSS et une hauteur de 844 px CSS, fond transparent hors de l'écran arrondi (rayon 48 px).

- [ ] **Step 1 : écrire les pages** dans le style d'iOS, animées :
  - **barre d'état** : heure (`serverMillis` + décalage, avancée chaque seconde par la page), « Pas de service », avion si actif, batterie en icône et pourcentage ; **île dynamique** dessinée, sans contenu à ce lot ;
  - **écran de choix du code** (`SETUP`) : « Choisissez un code », quatre points, clavier ; deuxième saisie de confirmation dans la page, puis un seul `setup` ;
  - **écran verrouillé** (`LOCKED`) : heure et date en grand sur le fond, « Saisissez le code », clavier circulaire, secousse des points à `WRONG_CODE`, « Essais restants : N », « Réessayez dans N s » décompté à `LOCKED_OUT` ; chiffres et Retour arrière au clavier physique ;
  - **déverrouillage** : glissement vers le haut, puis **accueil** : widgets heure et batterie, icône Réglages, barre d'accueil ;
  - **Réglages** : ouverture en zoom depuis l'icône, liste groupée et défilable : Carte SIM (« Aucune carte SIM »), Mode avion (interrupteur), Opérateur et Abonnement (« — »), Code (« Modifier le code » : ancien, nouveau, confirmation ; `NOT_OWNER` → « Seul le propriétaire peut modifier le code »), Batterie (pourcentage et barre), Fond d'écran (quatre vignettes `aube`, `lagon`, `jungle`, `nuit`, en dégradés CSS), Informations (« Téléphone de <ownerName> »), « Verrouiller », « Effacer le téléphone » (code demandé, confirmation) ; retour par la barre d'accueil ;
  - tout texte venu de la vue posé par `textContent`, jamais `innerHTML` ; aucun `eval` ; aucune adresse externe ; police système (`-apple-system, "Segoe UI", sans-serif`) ;
  - mode `#mock` : une fausse vue et un faux serveur dans `phone.js` (code `1234`) pour voir chaque écran dans Chrome.
- [ ] **Step 2 : vérifier hors du jeu** — `phone/index.html#mock` dans Chrome, fenêtre de 390 × 844 : capturer choix du code, verrouillé, mauvais code (secousse), blocage, accueil, Réglages défilés en bas, modification du code, fond `nuit` ; regarder chaque capture.
- [ ] **Step 3 : vérifier** — `./gradlew --offline test --tests '*WebResourcesTest*'` ; attendu : vert (CSP, minuscules, pas d'adresse externe).
- [ ] **Step 4 : commit** — `feat(telephone): ecran verrouille, accueil et Reglages en HTML`.

### Task 9 : le téléphone côté client, web et repli

**Files:**
- Create: `phone/client/PhoneClient.java`, `phone/client/PhoneWebScreen.java`, `phone/client/PhoneFallbackScreen.java`, `phone/client/PhoneFrameLayout.java`, `phone/client/PhoneViewJson.java`, `phone/client/PhoneBridgeMessages.java`
- Create: `textures/gui/phone_frame.png` (par `tools/gen_textures_t1.py`, écrit ici pour le cadre seulement)
- Modify: `phone/PhoneStatePacket.java` (`handle` vers `PhoneClient.accept`), langues (écran de repli, raisons de fermeture)
- Test: `src/test/java/dev/oneforallsurvival/phone/client/PhoneBridgeMessagesTest.java`, `PhoneViewJsonTest.java`, `PhoneFrameLayoutTest.java`

**Interfaces:**
- Consumes : `WebScreen`, `WebBridge`, `WebFailure`, `WebFallbackNotice`, `WebAvailability`, `WebScript` (tâches 2 et 3) ; paquets de la tâche 5 ; contrat de la tâche 8.
- Produces :
  - `PhoneFrameLayout` (pur) : `static WebScreen.Rect fit(int guiWidth, int guiHeight)` : rapport 390 : 844, hauteur 92 % de `guiHeight` au plus, centré, largeur au plus 90 % de `guiWidth` ; `static double scale(int pixelWidth)` = `pixelWidth / 390.0`.
  - `PhoneViewJson.toJson(PhoneStatePacket)` → `JsonObject` aux clés du contrat (énumérations en noms).
  - `PhoneBridgeMessages.toAction(JsonObject message, int session)` → `Optional<PhoneActionPacket>` : seuls `unlock`, `setup` (`code` valide selon `PhoneCode.isValid`), `lock`, `changeCode` (deux codes valides), `wallpaper` (`PhoneWallpapers.isValid`), `airplane` (booléen JSON), `erase` (code valide) ; tout autre champ ou type → vide. `ready`, `layout` et `close` ne vont pas au serveur.
  - `PhoneClient.accept(PhoneStatePacket)` : `OPEN` → `PhoneWebScreen` si `WebAvailability.ready()`, sinon `PhoneFallbackScreen` et `WebFallbackNotice.warnOnce(WebAvailability.failure())` ; `UPDATE` → l'écran ouvert de la même session ; `CLOSE` de la même session → ferme l'écran, message en barre d'action selon la raison ; `static Optional<PhoneStatePacket> last()`.
  - `PhoneWebScreen extends WebScreen` : page `WebPages.url("phone/index.html")` (propriété de développement `oneforallsurvival.phonePage` pour la remplacer) ; cadre par `PhoneFrameLayout.fit`, `drawFrame` dessine `phone_frame.png` autour ; à `ready` et à chaque état : `run(WebScript.call("phone.render", PhoneViewJson.toJson(state)))` ; `layout` gardé (`Map<String, int[]> rects()`, lu par l'automate de la tâche 11) ; `onFallback(failure)` → `PhoneFallbackScreen` sur le dernier état, `warnOnce(failure)` ; `removed()` envoie `CLOSE` sauf si la fermeture vient du serveur ou d'un passage au repli.
  - `PhoneFallbackScreen extends Screen` : même cadre ; écran du code (clavier de boutons 0 à 9, Effacer, points), accueil (bouton Réglages), Réglages (boutons : Mode avion, Fond suivant, Modifier le code par trois `EditBox` filtrés aux chiffres, Verrouiller, Effacer le téléphone) ; envoie les mêmes `PhoneActionPacket` ; messages des `Result` traduits ; Échap ferme et envoie `CLOSE`.

- [ ] **Step 1 : tests qui échouent** :
  - `PhoneBridgeMessagesTest` : `{"a":"unlock","code":"4821"}` → `UNLOCK` avec `first == "4821"` et la session ; vides pour `{"a":"unlock","code":"482"}`, `{"a":"unlock","code":4821}`, `{"a":"unlock","code":"１２３４"}`, `{"a":"wallpaper","id":"x"}`, `{"a":"airplane","on":"true"}`, `{"a":"rm -rf"}`, `{"a":"ready"}`, `{"a":"layout","rects":{}}` ; `changeCode` avec un nouveau code invalide → vide.
  - `PhoneViewJsonTest` : vue verrouillée sans `ownerName` non vide ; énumérations en noms (`"LOCKED"`) ; `ownerName` `"</script>\u2028"` relu à l'identique après `WebScript.call` puis `JsonParser` sur l'argument.
  - `PhoneFrameLayoutTest` : `fit(480, 270)` (1920 × 1080 à l'échelle 4) a une hauteur de 248 et une largeur de 114 ou 115, centrée ; `fit(320, 240)` tient dans la fenêtre ; `scale(390) == 1.0`.
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*phone.client*'`.
- [ ] **Step 3 : implémenter** ; ajouter le cadre au script `tools/gen_textures_t1.py` (tâche 10 le complète).
- [ ] **Step 4 : vérifier** — `./gradlew --offline test runGameTestServer` ; attendu : tout vert, `McefIsolationTest` compris.
- [ ] **Step 5 : vérifier en jeu avec MCEF** (procédure du verrou, second écran ; accord de la tâche 3 obtenu) : `runClient -PwithPack -PwithMcef`, monde neuf ; `/give` du téléphone ; clic droit → choix du code ; `4821` deux fois → accueil ; Réglages, fond `nuit`, mode avion ; Échap ; rouvrir → verrouillé, le fond `nuit` visible ; cinq faux codes → décompte ; batterie qui baisse dans la barre d'état ; brancher sur une prise alimentée par une source créative, reprendre, batterie remontée. Redimensionner la fenêtre et passer l'échelle de l'interface de 2 à 4 écran ouvert : cadre et clics toujours justes. Capturer chaque écran et regarder chaque capture.
- [ ] **Step 6 : vérifier en jeu sans MCEF** : `runClient -PwithPack` ; même parcours dans l'écran de repli ; un seul avis « mode simplifié » sur trois ouvertures.
- [ ] **Step 7 : commit** — `feat(telephone): ecran web du telephone et ecran de repli`.

### Task 10 : visuels finaux du téléphone et de la prise

**Files:**
- Create: `tools/gen_textures_t1.py` (complété), `textures/item/phone.png`, `textures/block/wall_socket.png`, `textures/block/wall_socket_lit.png`, `textures/gui/phone_frame.png`
- Modify: `models/item/phone.json`, `models/block/wall_socket.json`, `wall_socket_plugged.json`, `wall_socket_charging.json`, `blockstates/wall_socket.json`, `models/item/wall_socket.json`, `src/devcontent/java/dev/oneforallsurvival/dev/DemoVisuels.java` (scène `telephone`)

**Interfaces:**
- Consumes : `WallSocketBlock.FACING`, `PLUGGED`, `ElectricMachineBlock.STATE` (tâche 7) ; `PhoneFallbackScreen` (tâche 9).
- Produces : `-PvisuelsSeul=telephone` ; captures dans `run-demo/visuels-captures/telephone-*.png` : objet en main (première et troisième personne) et dans l'inventaire avec sa barre de charge pleine, à moitié et vide ; prise collée à un mur de pierre, sur les quatre orientations, vide, branchée à l'arrêt, en charge (voyant allumé), sans courant ; écran de repli verrouillé, accueil et Réglages à l'interface automatique la plus petite (320 × 240) et grande.

- [ ] **Step 1 : écrire le script** (16 × 16 : téléphone noir à bords arrondis, écran bleu nuit, île dynamique ; prise : plaque claire 6 × 6 px, deux trous, voyant vert à la charge, téléphone posé visible quand `plugged`) et lancer `python tools/gen_textures_t1.py`.
- [ ] **Step 2 : écrire les modèles et l'état de bloc** (modèle plat de 2 px contre le mur, tourné par `facing`).
- [ ] **Step 3 : capturer** — `./gradlew runDemoVisuels -PvisuelsSeul=telephone` (procédure du verrou, second écran) ; ouvrir et regarder chaque image : aucune face manquante ni voisin transparent derrière la prise (leçon du 2026-11-10), lisibilité de l'objet dans la barre.
- [ ] **Step 4 : commit** — `art(telephone): textures et modeles du telephone et de la prise`.

### Task 11 : essai réel, performance, WebDisplays et serveur dédié

**Files:**
- Create: `src/devcontent/java/dev/oneforallsurvival/dev/TelephoneShots.java`
- Create: `tools/telephone-memoire.ps1` (relève la mémoire de `java` et des `jcef_helper` à des instants donnés)
- Modify: `build.gradle` (propriétés `-PtelephoneShots=<dossier>`, `-PtelephoneShotsMode=parcours|perf|webdisplays`, `-PtelephoneShotsServer=<hôte:port>` vers `oneforallsurvival.telephoneShots*`, `-PphonePage=<chemin>` vers `oneforallsurvival.phonePage`, second écran)

**Interfaces:**
- Consumes : `PhoneWebScreen.rects()` et `PhoneFrameLayout` (clics réels aux centres des rectangles, convertis en coordonnées d'interface), `PhoneClient.last()`, `PhoneFallbackScreen`.
- Produces : journal `[telephone] …` lisible par la preuve ; aucune touche F10 ; aucune adresse externe.

- [ ] **Step 1 : écrire `TelephoneShots`** — monde neuf (ou serveur donné), téléphone donné par commande, utilisation par `gameMode.useItem` ; mode `parcours` : choix du code, déverrouillage, Réglages, fond, verrouillage, cinq faux codes, par **vrais clics** et **vraie frappe** relayés par l'écran ; captures à chaque écran ; mode `perf` : 30 s sans téléphone (images par seconde moyennes et tas après `System.gc()`), téléphone ouvert 60 s (images par seconde), fermé 30 s, puis cinq cycles ouverture/fermeture de 10 s ; journal `[telephone] perf avant=… pendant=… apres=… tas_avant=… tas_apres=… cycle=<n> tas=…` et un horodatage par étape pour `telephone-memoire.ps1`.
- [ ] **Step 2 : parcours réel avec le pack et MCEF** (procédure du verrou) — `runClient -PwithPack -PwithMcef -PtelephoneShots -PtelephoneShotsMode=parcours` ; regarder chaque capture ; journal sans exception.
- [ ] **Step 3 : performance** — même lancement en mode `perf` ; `tools/telephone-memoire.ps1` relève la mémoire de travail de `java` et la somme des `jcef_helper` aux horodatages. Comparer aux seuils des choix de l'auteur ; un seuil manqué est un défaut à décrire, pas à arrondir. Mesures écrites telles quelles, avec la machine (processeur, carte graphique, taille de fenêtre).
- [ ] **Step 4 : repli par page cassée** — mode `parcours` avec `-PphonePage=phone/absente.html` : le repli s'ouvre dans les 5 s avec le même état, un seul avis `fallback.load_error` ou `fallback.no_ready`.
- [ ] **Step 5 : cohabitation avec WebDisplays** — lire dans le jar de WebDisplays (cache de Gradle) ses objets et son réglage de page d'accueil ; régler la page d'accueil sur `mod://oneforallsurvival/web/selftest.html` **avant** de lancer (jamais une page externe ; si aucun réglage ne l'évite : ne pas lancer WebDisplays et demander à Killian). Mode `webdisplays` : ouvrir le navigateur de WebDisplays (sa tablette, si elle existe, par `useItem`), puis le téléphone, puis encore WebDisplays : les deux affichent leur page, le pont du téléphone répond pendant que WebDisplays est ouvert, aucun message de la page d'essai de WebDisplays n'arrive au pont du téléphone (`[web] reçu` absent pour lui), journal sans exception de l'un ou de l'autre.
- [ ] **Step 6 : serveur dédié sans MCEF** — `./gradlew runServer -PwithPack` (sans `-PwithMcef`) : lire « Done » au journal et l'absence de `mcef` dans la liste des mods ; puis, dans deux lancements Gradle séparés, un client avec MCEF (`runClient -PwithPack -PwithMcef -PtelephoneShots -PtelephoneShotsServer=127.0.0.1:25565`) et un client sans MCEF (`runClient2 -PwithPack -PtelephoneShots -PtelephoneShotsServer=127.0.0.1:25565`) : chacun fait le parcours, le second en repli ; arrêt du serveur par RCON.
- [ ] **Step 7 : Mac à puce Apple** — aucun Mac n'est connu sur ce poste : l'écrire comme non vérifié (question ouverte du cahier, section 10).
- [ ] **Step 8 : commit** — `test(telephone): essai reel, performance et cohabitation`.

### Task 12 : preuve du lot T1

**Files:**
- Create: `docs/verifications/lot-t1.md`
- Modify: `tasks/todo.md` (section « Téléphone » et sa ligne T1), `wiki/data/telephone.json` (téléphone, code, Réglages, batterie, prise : `"status": "dispo"`, image), `docs/superpowers/plans/2026-11-15-documents-de-lore.md` (sa tâche 3 retirée au profit de `client/web`, MCEF 2.1.6 et coordonnées Modrinth, schéma retenu à la tâche 3 de ce plan)

- [ ] **Step 1 : suite complète** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; consigner les compteurs lus dans les journaux et le rapport JUnit.
- [ ] **Step 2 : écrire `docs/verifications/lot-t1.md`** sur le modèle de `docs/verifications/lot-e6.md` : ce qui est prouvé et comment (JUnit, GameTests, parcours réel avec MCEF, repli sans MCEF, page cassée, serveur dédié sans MCEF avec un client de chaque sorte, performance chiffrée, WebDisplays, captures regardées) ; l'API de MCEF relevée et le schéma retenu ; « Non prouvé » (Mac à puce Apple, antivirus qui bloque le téléchargement, plusieurs joueurs devant WebDisplays, cartes graphiques autres que celle du poste, jar de production) ; « Pour le pack » : MCEF 2.1.6 et WebDisplays déjà dans le pack, rien à ajouter ; le serveur n'a pas besoin de MCEF.
- [ ] **Step 3 : ajouter à `tasks/todo.md`** une section « Téléphone » ([cahier](../docs/superpowers/specs/2026-11-08-telephone-design.md)) avec la ligne `- [x] **Lot T1 : socle et téléphone.** [Plan détaillé](../docs/superpowers/plans/2026-11-19-lot-t1-telephone-socle.md). …` qui résume en une phrase ce qui est prouvé, renvoie à `docs/verifications/lot-t1.md` et liste les restes.
- [ ] **Step 4 : `tasks/lessons.md`** — une leçon par correction significative rencontrée pendant le lot, au format du dépôt ; la forme générale d'un fait sur MCEF ou JCEF (schéma `mod`, routeur partagé, gestionnaires à emplacement unique) va dans `D:/BDC` après lecture de `D:/BDC/Accueil.md` et `D:/BDC/Conventions.md`, écritures groupées.
- [ ] **Step 5 : commit et push** — `docs(telephone): preuve du lot T1`, chemins listés un par un.
