# Documents de lore en jeu — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Les 25 documents HTML de lore de Killian se posent sur la map par le staff, sous forme de documents posés, de post-it et d'ordinateurs Nova Corps à mots de passe, et s'affichent en vrai HTML dans le jeu par MCEF.

**Architecture :** un nouveau paquet `dev.oneforallsurvival.lore` (serveur et commun : catalogue, blocs, entités, règles, paquets) et `lore/client` (écrans). Le socle navigateur vit dans `client/web` : le lot T1 du téléphone l'a livré (voir [sa vérification](../../verifications/lot-t1.md)), le lore le réutilise sans le reconstruire. Les pages sont des ressources du mod servies par le schéma `mod://` de MCEF. Le serveur fait foi pour tout état : texte des post-it, arborescence des postes, mots de passe, verrous. Le bureau de l'ordinateur est une page HTML/JS du mod qui parle au serveur par un pont JavaScript → Java → paquet.

**Tech Stack :** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, MCEF 2.1.6-1.20.1 de CinemaMod (`maven.modrinth:mcef:x91l6OKB`, mod_id `mcef`, déjà dans le pack et déjà déclaré par le lot T1), JCEF `CefMessageRouter` (le routeur `oneforallQuery` du socle), JUnit 5, GameTests, Python/PIL pour les textures.

**Spec :** `docs/superpowers/specs/2026-11-15-documents-de-lore-design.md` (toutes les sections ; § 11 liste les décisions de Killian et les choix de l'auteur).

## Écarts assumés par rapport à la spec

Trouvés en vérifiant l'API de MCEF le 2026-11-15 (code du fork de Keksuccino), puis revérifiés sur MCEF 2.1.6 par le lot T1 le 2026-11-19 (lecture du jar, essais réels) :
- **Chemin des pages :** `src/main/resources/assets/oneforallsurvival/html/lore/<catégorie>/<id>.html` et non `assets/oneforallsurvival/lore/…` : le schéma `mod://oneforallsurvival/<chemin>` de MCEF sert `/assets/<modid>/html/<chemin>`. Le lot T1 l'a vu servir sur la 2.1.6 (`mod://oneforallsurvival/web/selftest.html`, code 200) : **schéma `mod://` retenu, sans bascule vers `https://oneforallsurvival.local/`**.
- **Noms de fichiers en minuscules :** MCEF passe le chemin en minuscules (`arr-004.html`) ; `WebPages.url` du socle refuse toute majuscule et `WebResourcesTest` tout chemin non minuscule de `html/`. L'identifiant affiché reste `ARR-004`. Catégories : `arrival`, `mathis`, `enregistrements`.
- **MCEF 2.1.6, pas la 2.2.0 :** décision de Killian du 2026-11-19 (WebDisplays, déjà dans le pack, s'appuie sur la 2.1.6). `mods.toml` déclare `[2.1.6,2.2)` : la 2.2 et plus (le fork de Keksuccino, devenu Rinku en 3.x, mod_id `rinku`, autre API) serait refusée. Passer à Rinku est un chantier à part, qui toucherait aussi le téléphone.
- **Socle navigateur déjà livré (lot T1) :** dépendance Gradle et `mods.toml`, `WebAvailability`, `WebScreen`, `WebBridge`, `WebPages`, pont JavaScript (`web/bridge.js`), repli, isolation de MCEF dans `client/web/mcef/`, garde `WebResourcesTest` des pages de `html/`. Ce plan s'en sert ; la tâche 3 n'ajoute que ce qui est propre au lore.
- **À trancher avant la tâche 1 :** `WebResourcesTest` exige que **chaque** page `.html` de `html/` porte la CSP du socle (`default-src mod: 'unsafe-inline'; img-src mod: data:; connect-src 'none'`) et qu'aucun fichier texte n'y contienne `http://` ni `https://` ; les pages de Killian n'ont pas de CSP (relevé : 0 sur les trois dossiers retenus) et ne doivent pas être modifiées. Il faut décider comment les deux règles s'accordent (garde limitée au dossier du socle et des pages du mod, ou CSP posée à l'affichage) avant de copier les 25 pages dans `html/lore/`.

## Global Constraints

- Contraintes communes du dépôt (voir « Global Constraints » de `docs/superpowers/plans/2026-11-14-lot-e3-tresor-salaires.md`) : ASCII dans `gradle.properties` ; aucune classe cliente atteignable du serveur (écrans, MCEF et rendu dans `lore/client` ou `client/web`, appelés par `DistExecutor`) ; textes dans `fr_fr.json` avec repli `en_us.json`, accents corrects, jamais « SolarTown » ; `DeferredRegister` ; textures produites par script Python/PIL (`tools/gen_textures_lore.py`) ; lots de 8 GameTests au plus ; `JoueurDeTest` pour tout joueur d'essai ; « le staff » = `hasPermissions(2)` ; un client n'est jamais cru sur parole ; « vérifier puis engager ».
- **Le HTML de Killian n'est jamais modifié.** Copie octet pour octet ; seul le nom de fichier passe en minuscules.
- **Documents en jeu : 25** (dossier trié par Killian le 2026-11-15) = 11 de `04_PROGRAMME_ARRIVAL` (`ARR-001` à `ARR-010`, `ARV-001`) + 9 de `05_MATHIS_DELCOURT` (`MAT-001` à `MAT-009`) + 5 de `06_ENREGISTREMENTS` (`AUD-001` à `AUD-004`, `COM-001`). Exclus : `INDEX.html` (cite encore des documents retirés) et le PDF.
- **MCEF facultatif côté client (déjà fait par le lot T1) :** `mods.toml` déclare `modId="mcef"`, `mandatory=false`, `side="CLIENT"`, `versionRange="[2.1.6,2.2)"`. Aucune classe de `com.cinemamod` ni de `org.cef` hors de `client/web/mcef/` (garde `McefIsolationTest`) ; `WebAvailability` ne touche MCEF qu'après `ModList.get().isLoaded("mcef")`. Le serveur ne l'exige pas (il démarre sans, prouvé au lot T1). `-PwithMcef` ne va qu'avec un client.
- **Repli :** MCEF absent, non initialisé ou en échec → document ouvert dans le navigateur du système ; joueur prévenu une fois par session par `WebFallbackNotice.warnOnce` du socle (clés `message.oneforallsurvival.web.fallback.*`).
- **Pas d'accès réseau** depuis les pages : seuls `mod://oneforallsurvival/…` et `about:blank` sont chargés ; toute autre adresse principale est ramenée à la page de départ après coup par le socle (`WebPages.isAllowed`). Le socle ne filtre aucune sous-ressource (aucun `CefRequestHandler`) : seule la CSP de chaque page bloque le réseau, et les pages de Killian n'en ont pas (voir « À trancher avant la tâche 1 »).
- **Bornes :** nom de poste et de dossier 32 caractères, mot de passe 64, texte de post-it 256, heure figée `HH:mm`, profondeur des dossiers 4, 64 nœuds par poste. Texte venant du staff toujours échappé avant d'entrer dans une page.
- **Distance :** toute action réseau sur un bloc exige le joueur à 8 blocs au plus du bloc, dans la même dimension.
- **Date figée du bureau :** `17/11/1998` (constante). Heure par défaut `08:00`, réglable par poste.
- **Comparaison des mots de passe :** minuscules, accents retirés (`Normalizer` NFD puis suppression des diacritiques), espaces de début et de fin retirés. Le mot de passe n'est jamais envoyé au client.
- **Réseau :** nouveaux identifiants sur `BenchNetwork`, pris à la suite du dernier existant au moment de la tâche (le téléphone a pris 26 et 27 ; protocole à `"10"` après le lot T1) : `ID_LORE_OPEN`, `ID_POSTIT_EDIT`, `ID_COMPUTER_VIEW`, `ID_COMPUTER_ACTION`, `ID_COMPUTER_FILE`. Protocole monté **une seule fois** (tâche 3).
- **Preuve des tests :** `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack` ; lire « All N required tests passed » dans `run-gametest/logs/latest.log` et `build/test-results/test/*.xml`, jamais le seul code de sortie.
- **Tout lancement de Minecraft** (GameTests, client, captures) suit la procédure du verrou : section 11 de `C:/Users/killi/.claude/CLAUDE.md` (points 1 à 8). Fenêtres d'essai sur le second écran. Un client lancé ne doit jamais capturer la souris (garde `dev/SourisLibre`).
- **Téléchargements :** seules les dépendances résolues par Gradle sont permises sans demander. **Chromium n'est pas téléchargé en développement** : copié depuis l'installation du launcher dans `run-mcef/mcef-libraries` (jamais commité) et recopié par la tâche `prepareMcefLibraries` avec `-PwithMcef` (voir `docs/verifications/lot-t1.md`). MCEF 2.1.6 fait en plus, à chaque démarrage, une petite requête de somme de contrôle, acceptée par Killian.

## Review Focus

1. **Deux joueurs devant le même poste** quand l'un trouve le mot de passe : l'autre voit le cadenas disparaître sans rouvrir. Test : `ComputerGameTests.leDeverrouillageArriveAuxAutresSpectateurs` (tâche 6).
2. **Poste vidé par le staff pendant qu'un joueur le lit** (dossier supprimé, document retiré) : le joueur reçoit la nouvelle vue, et une demande sur un nœud disparu est refusée sans erreur. Test : `ComputerGameTests.unNoeudDisparuEstRefuse` (tâche 6).
3. **Client trafiqué** qui demande un fichier verrouillé, un poste trop loin ou une édition sans droits : refus côté serveur. Tests : `ComputerGameTests.fichierVerrouilleRefuse`, `editionRefuseeSansDroits`, `actionRefuseeTropLoin` (tâche 6).
4. **Identifiant de document inconnu** dans une entité (document retiré du manifeste dans une version suivante) : le bloc reste lisible et indique « document introuvable », rien ne plante. Test : `LoreCatalogTest.idInconnu` (tâche 1) et `LoreBlockGameTests.idInconnuNePlantePas` (tâche 2).
5. **Texte du staff hostile** (`<script>`, `</iframe>`, guillemets) dans un post-it, un nom de poste ou de dossier : affiché tel quel, jamais interprété. Tests : `LoreHtmlTest.echappeLeTexte` (tâche 4) et l'assertion d'échappement de `ComputerViewTest` (tâche 5).

---

### Task 1 : catalogue des documents

**Files :**
- Create : `tools/import_lore.py`
- Create : `src/main/resources/assets/oneforallsurvival/html/lore/**` (25 pages copiées) et `src/main/resources/assets/oneforallsurvival/html/lore/documents.json`
- Create : `src/main/java/dev/oneforallsurvival/lore/LoreDocument.java`, `LoreShape.java`, `LoreFileKind.java`, `LoreCatalog.java`
- Test : `src/test/java/dev/oneforallsurvival/lore/LoreCatalogTest.java`

**Interfaces :**
- Produces :
  - `enum LoreShape implements StringRepresentable { SHEET, FOLDER, NOTEBOOK, CASSETTE, USB; LoreShape next(); }`
  - `enum LoreFileKind { HTML, AUDIO }`
  - `record LoreDocument(String id, String title, String category, String path, LoreShape shape, LoreFileKind kind)` ; `path` relatif à `html/` (`lore/arrival/arr-004.html`) ; `String url()` → `"mod://oneforallsurvival/" + path`.
  - `final class LoreCatalog` : `static LoreCatalog load(InputStream json)`, `static LoreCatalog get()` (chargé une fois depuis le classpath), `Optional<LoreDocument> find(String id)`, `List<LoreDocument> all()` (ordre du manifeste).

- [ ] **Step 1 : écrire `tools/import_lore.py`** — lit `D:\LostIsland\lore`, copie octet pour octet chaque page retenue vers `html/lore/<catégorie>/<nom en minuscules>.html`, écrit `documents.json` (tableau d'objets `id`, `title`, `category`, `path`, `shape`, `kind`). `title` = contenu de `<title>` déséchappé (`html.unescape`). Seuls les sous-dossiers `04_PROGRAMME_ARRIVAL` → `arrival`, `05_MATHIS_DELCOURT` → `mathis`, `06_ENREGISTREMENTS` → `enregistrements` sont lus ; tout autre fichier HTML est ignoré. Forme et type par défaut : `arrival` → `FOLDER`/`HTML` ; `mathis` → `NOTEBOOK`/`HTML` ; `enregistrements` (AUD, COM) → `CASSETTE`/`AUDIO`. Le script affiche le nombre de documents copiés et échoue s'il diffère de 25.
- [ ] **Step 2 : lancer le script** — `python tools/import_lore.py` ; attendu : « 25 documents ».
- [ ] **Step 3 : écrire les tests qui échouent** dans `LoreCatalogTest` :
  - `vingtCinqDocuments` : `all().size() == 25` ;
  - `identifiantsUniques` ;
  - `chaqueFichierExiste` : la ressource `/assets/oneforallsurvival/html/` + `path` est lisible et commence par `<!doctype html` (sans tenir compte de la casse) ;
  - `cheminsEnMinuscules` : `path.equals(path.toLowerCase(Locale.ROOT))` ;
  - `documentsRetiresAbsents` : aucun identifiant ne commence par `NC-` ni `OMEGA-` ;
  - `indexAbsent` : aucun `path` ne finit par `index.html` ;
  - `titreDechappe` : `find("ARR-006").get().title()` contient `"cohorte d'arrivée"` ;
  - `idInconnu` : `find("XX-000")` est vide.
- [ ] **Step 4 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*LoreCatalogTest*'` ; attendu : échec de compilation (classes absentes).
- [ ] **Step 5 : implémenter** les quatre types (lecture JSON avec Gson, déjà présent dans Minecraft).
- [ ] **Step 6 : vérifier** — même commande ; attendu : 8 tests verts.
- [ ] **Step 7 : commit** — `feat(lore): catalogue des 25 documents`.

### Task 2 : le document posé

**Files :**
- Create : `lore/LoreRegistry.java` (blocs, entités, objets, onglet créatif « Lore »), `lore/LoreDocumentBlock.java`, `lore/LoreDocumentBlockEntity.java`, `lore/LoreDocumentItem.java`, `lore/LoreRules.java`
- Modify : `OneForAllSurvival.java` (enregistrer `LoreRegistry`)
- Create : modèles et états de bloc (`lore_document`, une variante par `LoreShape` et par orientation), textures via `tools/gen_textures_lore.py`, entrées `fr_fr.json` / `en_us.json`
- Test : `src/devcontent/java/dev/oneforallsurvival/dev/LoreBlockGameTests.java`

**Interfaces :**
- Consumes : `LoreCatalog`, `LoreShape` (tâche 1).
- Produces :
  - `LoreRegistry.DOCUMENT` (bloc `lore_document`, propriétés `FACING` horizontal, `SHAPE` : `EnumProperty<LoreShape>`), `LoreRegistry.DOCUMENT_BE`, `LoreRegistry.DOCUMENT_ITEM`, `LoreRegistry.TAB`.
  - `LoreDocumentBlockEntity` : `String documentId()`, `void setDocumentId(String)` ; NBT clé `"document"` ; synchronisé au client (`getUpdatePacket`/`getUpdateTag`).
  - `LoreDocumentItem` : objet unique ; l'identifiant est dans le NBT `"document"` ; `static ItemStack stackFor(LoreDocument)` ; nom affiché = `"<id> — <titre>"` ; posé, il crée le bloc avec la forme par défaut du document.
  - `LoreRules.canStaffEdit(Player)` = `hasPermissions(2) && isCreative()`.
  - `lore/client/LoreHoverOverlay` : quand le réticule vise un document posé, affiche son titre au-dessus de la barre d'action (titre lu dans `LoreCatalog` depuis l'identifiant synchronisé).
  - Propriétés communes des blocs de lore (réutilisées tâches 4 et 5) : `strength(-1.0F, 3600000.0F)`, `pushReaction(PushReaction.BLOCK)`, sans butin, `noOcclusion`, forme de collision plate (2 px de haut ; 1 px pour le post-it).

- [ ] **Step 1 : écrire les GameTests qui échouent** (`LoreBlockGameTests`, un lot) :
  - `incassableEnSurvie` : un `JoueurDeTest` en survie ne casse pas le bloc (`destroySpeed` < 0 et `getDestroyProgress` = 0) ;
  - `lePistonNePoussePas` : un piston activé à côté laisse le bloc en place ;
  - `explosionSansEffet` : une explosion de puissance 4 au contact laisse le bloc ;
  - `formeParDefaut` : poser l'objet de `MAT-003` donne `SHAPE = NOTEBOOK` et `documentId() == "MAT-003"` ;
  - `staffFaitDefilerLaForme` : staff en créatif, accroupi, clic droit → `SHAPE` passe à la forme suivante ; un joueur sans droits ne la change pas ;
  - `idConserveAuRechargement` : l'identifiant survit à une sauvegarde/lecture NBT de l'entité ;
  - `idInconnuNePlantePas` : une entité avec `"XX-000"` se charge, et le clic droit n'envoie rien au client (pas d'exception).
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline runGameTestServer` (procédure du verrou) ; attendu : échec de compilation.
- [ ] **Step 3 : implémenter** bloc, entité, objet, onglet (les 25 piles dans l'ordre du catalogue, plus le post-it et l'ordinateur des tâches suivantes quand ils existent), textures et modèles des cinq formes. Le clic droit d'un joueur appelle `LoreNetwork.openDocument(ServerPlayer, String id)` — **déclaré ici vide**, rempli à la tâche 3.
- [ ] **Step 4 : vérifier** — attendu : « All N required tests passed », les 7 nouveaux compris.
- [ ] **Step 5 : commit** — `feat(lore): document pose et onglet du staff`.

### Task 3 : ouverture d'un document sur le socle du lot T1

Le socle navigateur (dépendance MCEF 2.1.6, `mods.toml`, `WebScreen`, `WebBridge`, pont, repli) est **déjà livré par le lot T1** : cette tâche ne le reconstruit pas, elle ajoute l'ouverture d'un document.

**Files :**
- Modify : `bench/BenchNetwork.java` (identifiants, montée du protocole)
- Create : `client/web/SystemBrowserFallback.java`, `lore/LoreNetwork.java` (paquets du lore, enregistrés sur `BenchNetwork.CHANNEL`), `lore/client/LoreClient.java`, `lore/client/LoreWebScreen.java`
- Test : `src/test/java/dev/oneforallsurvival/client/web/SystemBrowserFallbackTest.java`
- Ni `build.gradle` ni `mods.toml` : MCEF y est déjà (`compileOnly` ; `runtimeOnly` avec `-PwithMcef`).

**Interfaces :**
- Consumes (socle du lot T1, paquet `client/web`) :
  - `abstract class WebScreen extends Screen` : constructeur `WebScreen(Component title, String page, WebBridge bridge)` ; la sous-classe donne `frame(int guiWidth, int guiHeight)` (cadre de la page, en coordonnées d'interface) et `onFallback(WebFailure)` (appelé une fois au plus, jamais après la fermeture, différé d'un tic s'il est décidé pendant `init`) ; `drawFrame`, `scale()` (1 = un pixel CSS par pixel réel), `run(String script)` ; souris relayée dans le cadre, clavier toujours, Échap ferme ; sans signal `{"a":"ready"}` de la page dans les 100 ticks, repli `NO_READY`.
  - `interface WebBridge { void onMessage(JsonObject message); default void onFailure(WebFailure failure) }` : un message est déjà passé par `WebMessage.parse` (objet JSON strict, 4 096 caractères au plus, champ `a` chaîne de 1 à 32) ; la page l'envoie par `window.oneforall.send(message)` (`web/bridge.js`) ; le jeu appelle la page par `WebScript.call("fonction", args)` puis `run(...)`.
  - `WebPages.url("lore/arrival/arr-004.html")` (minuscules, relatif, sans `..`) donne `mod://oneforallsurvival/lore/arrival/arr-004.html` ; `WebPages.isAllowed`.
  - `WebAvailability.ready()` et `failure()` ; `WebFallbackNotice.warnOnce(WebFailure)` (un seul avis par session de jeu).
  - `LoreCatalog`, `LoreDocument.url()` (tâche 1) ; `LoreNetwork.openDocument` (déclaré tâche 2).
- Produces :
  - `LoreWebScreen extends WebScreen` : cadre = toute la fenêtre, page = `LoreDocument.url()`, pont sans autre message utile que la fermeture ; `onFallback` ouvre le document dans le navigateur du système (ci-dessous). Les pages n'ont pas de `prefers-color-scheme` (vérifié) ; seuls les éléments natifs (barres de défilement) suivent le thème : à vérifier sur la capture et à forcer en clair si besoin, sans toucher aux fichiers de Killian.
  - `SystemBrowserFallback.open(String id, String html)` : écrit `<gameDir>/lostisland-lecture/<id>.html` (le dossier est vidé au lancement du client) et l'ouvre par `Util.getPlatform().openFile` ; `static Path pathFor(Path gameDir, String id)` (testable : refuse un identifiant contenant `/`, `\` ou `..`).
  - Paquet `ID_LORE_OPEN` (serveur vers client) : `String documentId`. `LoreNetwork.openDocument` l'envoie ; le client appelle `LoreClient.open(documentId)` qui ouvre `LoreWebScreen` si `WebAvailability.ready()`, sinon lit la ressource, appelle `SystemBrowserFallback.open` et `WebFallbackNotice.warnOnce(WebAvailability.failure())`.

- [ ] **Step 1 : écrire le test qui échoue** — `SystemBrowserFallbackTest` : `pathFor(dir, "ARR-004")` est dans `dir/lostisland-lecture` ; `pathFor(dir, "../x")` et `pathFor(dir, "a/b")` lèvent `IllegalArgumentException`.
- [ ] **Step 2 : vérifier qu'il échoue** — `./gradlew --offline test --tests '*SystemBrowserFallbackTest*'`.
- [ ] **Step 3 : implémenter** `SystemBrowserFallback`, `LoreWebScreen`, le paquet et `LoreClient`. Monter le protocole de `BenchNetwork` d'un cran (une seule fois pour tout ce plan) et réserver les cinq identifiants à la suite du dernier existant.
- [ ] **Step 4 : vérifier les tests** — `./gradlew --offline test runGameTestServer` ; attendu : tout vert, le serveur d'essai démarre **sans** MCEF dans son chemin de classes (preuve que rien de MCEF n'est chargé côté serveur) ; `McefIsolationTest` reste vert.
- [ ] **Step 5 : vérifier en jeu** (procédure du verrou, second écran) : client de dev avec `-PwithPack -PwithMcef` (Chromium copié du launcher, jamais téléchargé), poser `ARR-004`, clic droit → la page s'affiche ; défilement à la molette ; Échap ferme. Capturer et comparer à Chrome. Relancer sans `-PwithMcef` → la page s'ouvre dans le navigateur du système et l'avis apparaît une fois. Jamais la touche F10 (exemple de MCEF qui ouvre une page externe).
- [ ] **Step 6 : commit** — `feat(lore): ouverture des documents sur le socle navigateur`.

### Task 4 : le post-it

**Files :**
- Create : `lore/PostItBlock.java`, `lore/PostItBlockEntity.java`, `lore/LoreHtml.java`, `lore/client/PostItEditScreen.java`, `src/main/resources/assets/oneforallsurvival/html/lore/post-it.html` (gabarit papier)
- Modify : `LoreRegistry`, `LoreNetwork`, textures, langues
- Test : `src/test/java/dev/oneforallsurvival/lore/LoreHtmlTest.java`, ajouts à `LoreBlockGameTests`

**Interfaces :**
- Consumes : socle de la tâche 3 ; propriétés de bloc et `LoreRules.canStaffEdit` (tâche 2).
- Produces :
  - `LoreRegistry.POST_IT`, `POST_IT_BE` ; `PostItBlockEntity.text()` / `setText(String)` (coupé à 256, sans caractères de contrôle sauf `\n`).
  - `LoreHtml.escape(String)` (échappe `& < > " '`) et `LoreHtml.postIt(String text)` → page complète (gabarit + texte échappé, `\n` → `<br>`).
  - Paquet `ID_POSTIT_EDIT` (client → serveur) : `BlockPos pos`, `String text` ; accepté seulement si `canStaffEdit` et distance ≤ 8.
  - Lecture : `ID_LORE_OPEN` étendu d'un champ facultatif `String html` ; s'il est présent, le client l'affiche à la place d'un document (`data:text/html;base64,…` dans `WebScreen`, ou le repli).

- [ ] **Step 1 : tests qui échouent** — `LoreHtmlTest.echappeLeTexte` : `postIt("<script>alert(1)</script>")` contient `&lt;script&gt;` et pas `<script>alert` ; `retoursALaLigne` ; `texteCoupeA256`. GameTests : `postItTexteParLeStaff` (le paquet d'un staff change le texte), `postItRefuseSansDroits`, `postItRefuseTropLoin`.
- [ ] **Step 2 : vérifier qu'ils échouent.**
- [ ] **Step 3 : implémenter.** Accroupi + clic droit en créatif par le staff ouvre `PostItEditScreen` (interface Minecraft : zone de texte multiligne, bouton « Enregistrer ») ; clic droit de tout joueur → lecture.
- [ ] **Step 4 : vérifier** (unitaires puis GameTests, procédure du verrou).
- [ ] **Step 5 : commit** — `feat(lore): post-it du staff`.

### Task 5 : le modèle du poste

**Files :**
- Create : `lore/computer/ComputerTree.java`, `ComputerNode.java`, `PasswordRules.java`, `ComputerView.java`, `ComputerEdit.java`
- Test : `src/test/java/dev/oneforallsurvival/lore/computer/PasswordRulesTest.java`, `ComputerTreeTest.java`, `ComputerViewTest.java`

**Interfaces :**
- Consumes : `LoreCatalog` (tâche 1), `LoreHtml.escape` (tâche 4).
- Produces :
  - `record ComputerNode(int id, int parent, Kind kind, String name, @Nullable String documentId, @Nullable String password, boolean unlocked)` avec `enum Kind { FOLDER, FILE }` ; racine = id 0.
  - `final class ComputerTree` : `String stationName`, `String wallpaper`, `String clock` (`HH:mm`) ; `List<ComputerNode> nodes()` ; `CompoundTag save()` / `static ComputerTree load(CompoundTag)` ; `Optional<ComputerNode> node(int)` ; `boolean isLocked(int id)` (le nœud ou un ancêtre a un mot de passe non déverrouillé) ; `static ComputerTree empty()` (poste « POSTE-NC », fond `nova`, heure `08:00`).
  - `PasswordRules.normalize(String)` et `PasswordRules.matches(String saisi, String attendu)`.
  - `ComputerEdit` : opérations du staff, chacune `Result apply(ComputerTree)` avec contrôle des bornes : `CreateFolder(parent, name)`, `AddFile(parent, documentId)`, `Rename(id, name)`, `Move(id, newParent)`, `Delete(id)` (récursif), `SetPassword(id, password)` (null = retire), `Relock(id)`, `SetStation(name, wallpaper, clock)`.
  - `ComputerView.toJson(ComputerTree, boolean staff)` : JSON envoyé au client ; **aucun mot de passe** ; pour chaque nœud `id`, `parent`, `kind`, `name`, `title`, `fileKind`, `locked` ; le staff reçoit en plus `hasPassword`. Noms échappés côté page (le JSON reste brut, la page n'utilise que `textContent`).

- [ ] **Step 1 : tests qui échouent** :
  - `PasswordRulesTest` : `matches("omega", "OMÉGA")`, `matches("  Arrival-77 ", "arrival-77")`, `!matches("omeg", "omega")`, `!matches("", "omega")` ;
  - `ComputerTreeTest` : aller-retour NBT identique ; `isLocked` hérité d'un dossier verrouillé ; `Delete` d'un dossier retire ses enfants ; refus au-delà de 64 nœuds, de la profondeur 4, d'un nom de 33 caractères, d'un `documentId` absent du catalogue, d'un `Move` d'un dossier dans son propre descendant ; `Relock` remet `unlocked` à faux ; heure `25:00` refusée ;
  - `ComputerViewTest` : le JSON ne contient jamais la chaîne du mot de passe (même en vue staff) ; un nom `"</script><b>"` ressort comme chaîne JSON correctement échappée.
- [ ] **Step 2 : vérifier qu'ils échouent.**
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : vérifier** — attendu : tous verts.
- [ ] **Step 5 : commit** — `feat(lore): arborescence et mots de passe des postes`.

### Task 6 : l'ordinateur côté serveur

**Files :**
- Create : `lore/computer/ComputerBlock.java`, `ComputerBlockEntity.java`, `ComputerService.java`
- Create : `lore/LoreCommands.java` (`/lore poste copier` et `/lore poste coller` : copie l'arborescence du poste visé dans un presse-papiers par joueur du staff, puis la colle sur un autre poste, verrous remis à zéro ; niveau 2)
- Modify : `LoreRegistry` (bloc `nova_computer` : un seul bloc écran-et-tour, orienté), `LoreNetwork`, textures, langues
- Test : `src/devcontent/java/dev/oneforallsurvival/dev/ComputerGameTests.java` (deux lots si plus de 8)

**Interfaces :**
- Consumes : tâche 5 entière ; propriétés de bloc (tâche 2).
- Produces :
  - `ComputerBlockEntity` : `ComputerTree tree()`, `setChanged()` après toute modification ; spectateurs transitoires `Set<UUID> viewers` (non sauvegardés).
  - `ComputerService` (testable avec un `ComputerBlockEntity` et des `ServerPlayer`) :
    - `open(ServerPlayer, BlockPos)` → ajoute le spectateur, envoie `ID_COMPUTER_VIEW` (JSON de `ComputerView`) ;
    - `openFile(ServerPlayer, BlockPos, int nodeId)` → si fichier existant et non verrouillé, envoie `ID_COMPUTER_FILE` (`documentId`) ; sinon envoie un refus ;
    - `tryPassword(ServerPlayer, BlockPos, int nodeId, String saisie)` → si juste, `unlocked = true`, sauvegarde, renvoie la vue à **tous** les spectateurs encore à 8 blocs ; sinon réponse « mot de passe incorrect » au seul joueur ;
    - `edit(ServerPlayer, BlockPos, ComputerEdit)` → refus sans `canStaffEdit` ; sinon applique et renvoie la vue à tous les spectateurs ;
    - `close(ServerPlayer, BlockPos)`.
  - Paquet `ID_COMPUTER_ACTION` (client → serveur) : `BlockPos pos`, `String json` (action et arguments) ; le serveur valide distance, dimension, existence du nœud, bornes, droits, puis appelle `ComputerService`.
  - Paquets serveur → client : `ID_COMPUTER_VIEW` (`BlockPos`, `String json`), `ID_COMPUTER_FILE` (`BlockPos`, `int nodeId`, `String documentId`, ou un code de refus).

- [ ] **Step 1 : GameTests qui échouent** :
  - `motDePasseJusteDeverrouillePourTous` : A tape juste ; B, qui ouvre ensuite, voit `locked:false` ;
  - `motDePasseFauxNeChangeRien` ;
  - `leDeverrouillageArriveAuxAutresSpectateurs` : A et B ouverts, A tape juste, B reçoit une vue sans cadenas (paquet capturé) ;
  - `fichierVerrouilleRefuse` : `openFile` sur un fichier verrouillé n'envoie aucun `documentId` ;
  - `editionRefuseeSansDroits` ;
  - `actionRefuseeTropLoin` : joueur à 9 blocs ;
  - `unNoeudDisparuEstRefuse` : le staff supprime un dossier pendant que B lit ; `openFile` de B sur un enfant est refusé, B reçoit la vue à jour ;
  - `reverrouillage` : après `Relock`, B voit de nouveau le cadenas ;
  - `posteConserveAuRechargement` : arborescence, verrous et heure survivent à une sauvegarde/lecture de l'entité ;
  - `copierCollerUnPoste` : la copie donne la même arborescence sur le second poste, tous verrous fermés ;
  - `ordinateurIncassableEtImmobile` : mêmes protections que le document posé.
- [ ] **Step 2 : vérifier qu'ils échouent** (procédure du verrou).
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : vérifier** — attendu : « All N required tests passed » sans et avec `-PwithPack`.
- [ ] **Step 5 : commit** — `feat(lore): ordinateur Nova Corps cote serveur`.

### Task 7 : le bureau Nova Corps

**Files :**
- Create : `src/main/resources/assets/oneforallsurvival/html/desktop/index.html`, `desktop.css`, `desktop.js`, `wallpapers/*.png` (trois fonds : `nova` par défaut, `labo`, `direction`)
- Create : `lore/client/ComputerScreenController.java` (implémente `WebBridge`)
- Modify : `LoreClient` (réception de `ID_COMPUTER_VIEW` et `ID_COMPUTER_FILE`)

**Interfaces :**
- Consumes : `WebScreen`, `WebBridge` (tâche 3) ; paquets et JSON de la tâche 6.
- Produces (contrat page ↔ Java, messages JSON par `window.oneforall.send(...)`) :
  - page → Java : `{"a":"ready"}`, `{"a":"open","node":N}`, `{"a":"password","node":N,"value":"…"}`, `{"a":"edit","op":"…",…}` (champs de `ComputerEdit`), `{"a":"close"}` ;
  - Java → page : appel de `window.novaOS.render(view)`, `window.novaOS.openFile(nodeId, url, title, fileKind)`, `window.novaOS.passwordRefused(nodeId)` par `executeJavaScript`, avec arguments sérialisés en JSON (jamais concaténés comme du code).

- [ ] **Step 1 : écrire la page** — bureau en plein écran : fond, nom du poste, icônes des dossiers de la racine, explorateur, barre des tâches « Nova Corps » avec la date `17/11/1998` et l'heure figée du poste ; esthétique crédible pour 1998. Fenêtres déplaçables, fermables, agrandissables ; une fenêtre de document s'ouvre à au moins 760 px utiles et charge `document.url()` dans une `iframe` ; un fichier `AUDIO` s'ouvre dans un lecteur factice (boutons inertes, barre de temps) autour de l'`iframe` de transcription. Cadenas sur les nœuds `locked` ; double-clic → champ de mot de passe ; « Mot de passe incorrect » en cas de refus. Lecture seule pour un joueur. Mode édition visible seulement si la vue est `staff` : liste des 25 documents à glisser dans un dossier, créer, renommer, supprimer, déplacer, poser ou retirer un mot de passe, reverrouiller, régler nom, fond et heure. Tout texte inséré par `textContent`.
- [ ] **Step 2 : implémenter `ComputerScreenController`** — à `ready`, pousse la dernière vue reçue ; relaie les actions en `ID_COMPUTER_ACTION` ; à la fermeture envoie `close`.
- [ ] **Step 3 : vérifier en jeu** (procédure du verrou, second écran, deux clients pour le cas 1 de la Review Focus) : poser un poste, le remplir en staff, verrouiller un dossier, cacher le mot de passe sur un post-it ; en joueur, ouvrir, échouer, réussir ; vérifier que le second client voit le cadenas disparaître ; capturer.
- [ ] **Step 4 : commit** — `feat(lore): bureau Nova Corps`.

### Task 8 : repli de l'ordinateur sans MCEF

**Files :**
- Create : `lore/client/ComputerFallbackScreen.java`
- Modify : `LoreClient`

**Interfaces :**
- Consumes : JSON de `ComputerView`, paquets de la tâche 6, `SystemBrowserFallback` (tâche 3).
- Produces : écran Minecraft classique — chemin courant, liste des dossiers et fichiers (cadenas en texte), champ de mot de passe, bouton « Ouvrir » qui ouvre le document dans le navigateur du système. Pas de mode édition (le staff édite depuis un client avec MCEF).

- [ ] **Step 1 : implémenter.**
- [ ] **Step 2 : vérifier en jeu sans MCEF** (procédure du verrou) : naviguer, déverrouiller, ouvrir un document dans le navigateur.
- [ ] **Step 3 : commit** — `feat(lore): ecran de repli de l'ordinateur`.

### Task 9 : preuve finale et pack

**Files :**
- Create : `docs/verifications/lore.md`
- Modify : `wiki/` (page staff : poser un document, un post-it, remplir un poste), `tasks/todo.md`

- [ ] **Step 1 : comparaison visuelle** — capturer en jeu et dans Chrome une page de chacun des 13 gabarits : `briefing` (`ARR-001`), `telemetry` (`ARR-002`), `matrix` (`ARR-003`), `fieldreport` (`ARR-004`), `directive` (`ARR-005`), `timeline` (`ARR-007`), `dossier` (`ARR-008`), `redacted` (`ARR-010`), `diary` (`MAT-001`), `report` (`MAT-004`), `agreement` (`MAT-006`), `transcript` (`AUD-001`), `waveform` (`AUD-002`) ; les mettre côte à côte dans `docs/verifications/lore.md`. Toute différence visible de mise en page est un défaut à corriger.
- [ ] **Step 2 : suite complète** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; consigner les compteurs lus dans les journaux.
- [ ] **Step 3 : Mac à puce Apple** — si un Mac est disponible, lancer le client et noter le résultat ; sinon, l'écrire comme non vérifié dans `docs/verifications/lore.md`.
- [ ] **Step 4 : pack** — MCEF 2.1.6 est déjà dans le pack (lot T1) : rien à ajouter ; contrôler seulement que la version du pack reste dans `[2.1.6,2.2)`.
- [ ] **Step 5 : commit et push** — `docs(lore): preuve des documents de lore`.
