# Vérifications du lot T1 : socle navigateur et téléphone

Relevés du 2026-11-19 au 2026-11-20, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 12 (preuve du lot). Les tâches 1 à 11 ont leurs JUnit, GameTests et essais réels ; ils sont repris ici d'après leurs rapports et leurs revues, sans rien y ajouter. La tâche 12 n'a lancé aucun Minecraft ni aucune JVM : elle ne fait que lire les journaux et rapports que les tâches ont laissés, et écrire. Cahier : [le téléphone](../superpowers/specs/2026-11-08-telephone-design.md). Plan : [`2026-11-19-lot-t1-telephone-socle.md`](../superpowers/plans/archive/2026-11-19-lot-t1-telephone-socle.md). Commits du lot : `e75c322` (MCEF 2.1.6 en dépendance), `140d1c5` (adresses, appels et messages des pages), `f891d0d` puis `7e19896` (socle MCEF, pont JavaScript, repli), `2449003` puis `93c130f` (objet, code, données), `1ae4010` puis `5d8a77c` (session et paquets), `8a50819` (batterie), `22819e3` puis `b7aab08` (prise murale), `7575efd` puis `9d5b24e` (pages HTML), `243b793` puis `92bd84d` (écran du téléphone et repli), `331d247` (textures et modèles), `91c231e` puis `640131c` (essai réel, performance, WebDisplays, serveur dédié), puis ce document.

**État : le lot est prouvé dans les limites de la section « Non prouvé et limites ».** Un vrai client avec MCEF 2.1.6 (Chromium copié depuis le launcher, aucun téléchargement) ouvre le téléphone par un vrai clic droit, choisit un code (au clic et au clavier), le déverrouille, parcourt les Réglages (fond d'écran, mode avion, changement de code), se fait bloquer 60 s après cinq faux codes, efface le téléphone depuis l'écran, le voit se fermer quand il quitte la main ou quand un `/phone reset` tombe écran ouvert, puis le recharge sur une prise murale. Une page absente ou cassée et un navigateur qui ne répond pas font passer au repli classique, sur le même état. Un serveur dédié sans MCEF démarre ; un client avec MCEF et un client sans MCEF y jouent le parcours. WebDisplays cohabite avec le téléphone. La performance est chiffrée, avec une mesure manquée et deux mesures qui la contredisent, copiées telles quelles. Un défaut grave (plantage de la JVM au repli) a été trouvé par l'essai réel et corrigé. Ce qui n'a pas été essayé est écrit « non vérifié ».

## Ce qui passe

### Tests

| Source | Résultat lu |
|---|---|
| `./gradlew --offline test` (tâche 11, tour 1, 05:29 le 2026-11-20) | 683 tests JUnit, 0 échec, 0 erreur, 0 ignoré. Relu à la tâche 12 : somme des rapports `build/test-results/test/*.xml` = 683 tests, 0 ignoré, 0 échec, 0 erreur |
| `./gradlew --offline runGameTestServer` (05:29:27 à 05:30:53) | « All 1030 required tests passed » |
| `./gradlew --offline runGameTestServer -PwithPack` (05:30:53 à 05:33:06) | « All 1030 required tests passed » ; 113 lignes `createaddition` au journal (le pack est chargé). Relu à la tâche 12 : `run-gametest/logs/latest.log`, ligne de 05:33:01 |

Les trois lignes ci-dessus sont celles du commit `640131c`, avant la revue finale. La revue finale a ajouté un GameTest (`laPriseNeSeDeplacePasAvecCreate`, lot `prise`) et rejoué les passes : voir « Revue finale ».

**GameTests ajoutés par le lot : 43 (42 avant la revue finale), en neuf lots de 8 au plus** : `telephone_a` (8), `telephone_horloge` (6), `telephone_reset` (5), `telephone_b` (8), `telephone_c` (1), `prise` (8 depuis la revue finale), `prise_explosion` (1), `prise_mur` (5), `telephone_recettes` (1). Ils prouvent, côté serveur : l'ouverture et la session (faux joueur refusé, code jamais écrit dans la pile), les cinq faux codes qui bloquent 60 s (y compris par faux effacement et faux changement de code, blocage conservé à la fermeture), le partage entre deux copies du même téléphone, le propriétaire seul qui change le code ou efface, le frein d'un geste par deux tics, une session périmée ou d'un autre numéro qui n'obtient rien, `/phone reset` qui ferme les sessions, la batterie qui se vide écran ouvert et jamais écran fermé, la fermeture par batterie vide, changement de main, mort et déconnexion, le pourcentage poussé exactement quand il change, la prise qui recharge (et seulement avec du courant, sans verser plus qu'elle ne consomme), qui rend le téléphone à la casse, à l'explosion et à la disparition du mur, qui refuse tout autre objet, qui ne sort pas le téléphone pour un faux joueur, dont la forme suit les quatre orientations, et les recettes à la table.

### Mutations (chaque mutation a rougi le test qui la nomme, puis a été restaurée)

| Tâche | Mutations vues rouges (rapports) |
|---|---|
| 1 | 2 (nom d'un paquet de MCEF hors du client, dans les deux sens) |
| 2 | 4 |
| 3 | 4, puis 5 au tour 1 |
| 4 | 4 (une cinquième, la comparaison à temps constant, ne fait rougir aucun test : relue seulement), puis 4 au tour 1 |
| 5 | 4, puis 4 au tour 1 |
| 6 | 6 |
| 7 | 6, puis 2 au tour 1 |
| 8 | 1 |
| 9 | 4, puis 3 au tour 1 |
| 10 | 2 |
| 11 | 4 (seuils de performance, étapes attendues, croissance par ouverture) |
| Revue finale | 1 (tag `create:non_movable` retiré : `laPriseNeSeDeplacePasAvecCreate` rouge, « 1 required tests failed ») |

### Chromium : aucun téléchargement

Chromium vient de l'installation du launcher de Killian (`%APPDATA%\.lostisland\mods\mcef-libraries`, copié en lecture seule vers `run-mcef/mcef-libraries`, 269 Mo relevés, jamais commité). La tâche `prepareMcefLibraries` (avec `-PwithMcef`) le recopie dans `build/mcef-libraries` et pose `skip-download=true` dans la configuration de MCEF. **Le journal de chaque client lancé avec MCEF ne contient aucune ligne « Downloading Chromium Embedded Framework »** (relevé aux tâches 3, 9 et 11) ; une garde qui arrêtait le PID exact à la première de ces lignes tournait pendant chaque client et n'a jamais servi.

**MCEF 2.1.6 fait en revanche une petite requête de somme de contrôle** (fichier `.sha256`, environ 300 octets, vers `mcef-download.cinemamod.com`) **à chaque démarrage**, impossible à couper dans cette version. **Accord tacite de Killian, en développement seulement** (il a repris le travail sans objection ; son launcher fait la même requête à chaque partie). En production, chaque client fait cette requête vers un serveur tiers : à confirmer explicitement.

Jars résolus par Gradle : MCEF `mcef-x91l6OKB.jar`, SHA-1 `6d9fa1433d509be242d1d74282d02a0fbebde110` (identique à l'attendu, résolu par Modrinth) ; WebDisplays `webdisplays-DHWqG6SF.jar`, SHA-1 `f05566e3b898de8542fa7fa79aebd2cff69b89e6` (811 968 octets, identique à l'attendu, contrôlé à la tâche 11).

### Parcours réel avec MCEF

Automate `TelephoneShots` (devcontent), lancé par `runClient -PwithPack -PwithMcef -PtelephoneShots=<dossier>` : monde plat créatif neuf, `/give` du téléphone, batterie chargée à 30 % côté serveur, **ouverture par un vrai `gameMode.useItem`**, clics par les méthodes de l'écran aux centres des boîtes du dernier `layout` de la page (attendu stable avant chaque clic), frappes dans l'ordre de GLFW, chaque geste attendant la réponse du serveur. Fenêtre de 1280 × 720 sur le second écran.

| Passe | Résultat |
|---|---|
| Tâche 9, passe 1 (PID 20156) | 11 échecs : les chiffres tapés au clavier n'avaient aucun effet (voir « Défauts trouvés ») |
| Tâche 9, passe 2 (PID 61524) | 1 échec : clic de l'automate 16 ms après la réponse précédente, sous le frein du serveur (défaut de l'automate, pause ajoutée) |
| Tâche 9, passe 3 (PID 50264) | « TOUT PASSE, 20 image(s) » |
| Tâche 11, passe 1 (PID 61408) | plantage natif de la JVM au premier repli `LOAD_ERROR` (voir « Défauts trouvés ») |
| Tâche 11, passe 2 (PID 59352) | « TOUT PASSE, 31 image(s), 150 pas réussis » |
| Tâche 11, tour 1 (PID 26956) | « TOUT PASSE, 32 image(s), 151 pas réussis » (avec la reprise d'une adresse non permise) |

Le parcours joue : choix du code (clics, puis confirmation au clavier), accueil, Réglages, fond Nuit, mode avion, changement de code, retour au verrouillage, quatre faux codes (clics et clavier) puis le cinquième : « Téléphone bloqué, Réessayez dans N s » décompté, clavier grisé, `layout` réduit à `home-bar` ; batterie 28 % puis 27 % écran allumé ; effacement depuis l'écran (feuille du code, puis alerte) ; téléphone qui quitte la main ; `/phone reset` écran ouvert ; repli en cours de route ; adresse non permise. Les clics sont tombés juste à trois tailles de cadre (153, 221 et 110 px de large, dont l'échelle d'interface 4 sur une fenêtre de 1920 × 1040 : Minecraft ramène l'échelle 4 à 3 sur 720 ou 900 px de haut) : chaque faux code cliqué a été compté par le serveur.

- **Pont** (tâche 11, passe 2) : 42 messages de la page `{ready=7, layout=23, setup=2, wallpaper=1, airplane=1, changeCode=1, unlock=6, erase=1}`, **0 message étranger** (un témoin entoure le pont et signale toute sorte inconnue ou toute clé hors de `a`, `rects`, `code`, `old`, `new`, `id`, `on`, sans jamais lire le contenu).
- **Prise** (tâche 11, passe 2) : « réserve 3960 FE, téléphone 26320 FE (36 %), état RUNNING » après le branchement par un vrai `useItemOn` ; 36 % à la réouverture (27 % avant). Tâche 9 (même geste, huit secondes d'attente) : « réserve 3960 FE, téléphone 26720 FE (37 %), état RUNNING », 28 % avant, 37 % après.
- **Aucun code au journal** : les journaux des passes finales, cherchés pour les deux codes d'essai, n'en contiennent aucun (tâche 9 : `grep -c` à 0 ; tâche 11 : relu par la revue sur les 16 journaux du 2026-11-20 et les deux `latest.log`). Le `toString` de `PhoneActionPacket` masque le code (corrigé à la revue de la tâche 9, gardé par un test).
- Fenêtre « [fenetre] second écran (2600, 40) » et « [souris] rendue » à chaque client ; tous se sont fermés seuls ; `run/options.txt` de SHA-1 `dbc3d03c…` identique avant et après chaque série de clients.
- Jamais la touche F10 ; aucune page externe chargée par le mod : les seules adresses du journal sont celles du démarrage (authentification Mojang, Forge, CraftTweaker, Hospitals, `.sha256` de MCEF).

### Repli sans MCEF

- Tâche 3 : deux `/webessai` sans MCEF écrivent « [web] repli : ABSENT » deux fois au journal, n'ouvrent aucun écran et ne montrent l'avis qu'une fois dans le chat ; aucun `NoClassDefFoundError`.
- Tâche 9 (`runClient -PwithPack`, sans `-PwithMcef`) : deux passes « TOUT PASSE, 21 image(s) » dans l'écran de repli (mêmes pas, clics sur les boutons nommés, frappe dans les trois champs masqués par des astérisques) ; batterie 29 % puis 28 % puis 37 % après la prise ; un seul avis (`fallback.absent`) pour trois ouvertures.
- Tâche 11 (serveur dédié, voir plus bas) : « TOUT PASSE, 16 image(s), 77 pas réussis » avec `runClient2 -PwithPack`.

### Page cassée et repli en cours de route

- `-PphonePage=phone/absente.html` avec MCEF (PID 62620) : « TOUT PASSE, 28 image(s), 120 pas réussis », tout le parcours dans le repli. Chaque ouverture a montré l'écran web d'abord, puis le repli, sur le même état que le dernier reçu, en 0,37 / 0,38 / 0,33 / 0,18 / 0,20 / 0,27 / 0,26 s (sept ouvertures, toutes sous 5 s) ; « [web] repli : LOAD_ERROR » à chaque ouverture, un seul avis dans le chat.
- Repli en cours de route (dans le parcours) : `LOAD_ERROR` (page absente, `ERR_UNKNOWN_URL_SCHEME`) en 0,35 s, `NO_READY` (page chargée sans signal) en 4,98 s ; état montré `SETUP aube 32 %`, égal au dernier reçu ; **un seul avis** en tout. Huit replis `LOAD_ERROR` et un `NO_READY` ont été joués sans plantage après la correction.
- Adresse principale non permise (`about:blank#hors-page`, envoyée par la page) : « [web] adresse non permise … : retour à mod://oneforallsurvival/phone/index.html », la page revient (signal `ready` 8, avant 7), sans plantage.

### Fermetures par le serveur et effacement

Joués dans un vrai client (messages de la barre d'action lus dans `Gui.overlayMessageString`, client en anglais) : téléphone qui quitte la main (`PHONE_GONE`, « The phone is no longer in hand: screen closed. »), `/phone reset` écran ouvert (`ERASED`, « Phone erased. »), effacement depuis l'écran avec MCEF (feuille « Saisissez le code », puis alerte « Effacer le téléphone ? ») et dans le repli (deux clics, code pris dans « Code actuel »), chaque fois suivi d'une réouverture au choix du code. **La fermeture par batterie vide n'est prouvée qu'en GameTest** (`batterieVideEteintLeTelephone`), pas dans un client.

### Serveur dédié sans MCEF (tâche 11)

`runServer -PwithPack -PserverWorld=telephone-essai` (monde neuf, à part de `world`) : « Done (20.033s)! », **0 ligne `mcef` dans le journal du serveur** ; mods chargés : crafttweaker, create, create_new_age, createaddition, esl, farmersdelight, firstaid, flywheel, hospitals, jei, light_bulb_addon…, marbledsfirstaid, mezz_config, minecraft, mixinextras, oneforallsurvival, ponder (ni `mcef` ni `webdisplays`). Un client de chaque sorte, l'un après l'autre :

| Client | Résultat |
|---|---|
| MCEF **avec** WebDisplays (`-PwithMcef`) | **refusé** : `ModMismatchDisconnectedScreen`, « Dev lost connection: Disconnected » (le canal réseau de WebDisplays n'accepte que la version « 2 » et un serveur qui n'a pas le mod est refusé) ; après correction de l'automate, le refus est vu en 3 s (180 s avant) |
| MCEF **sans** WebDisplays (`-PwithMcef -PsansWebDisplays`, PID 19312) | « TOUT PASSE, 15 image(s), 91 pas réussis » : le parcours web complet sur le serveur (choix du code, Réglages, nuit, avion, code changé, cinq faux codes et blocage, `PHONE_GONE`, `/phone reset`) ; 0 avis, 24 messages au pont, 0 étranger |
| sans MCEF (`runClient2 -PwithPack`, PID 39508, `Dev2` mis opérateur par RCON avant, retiré après) | « TOUT PASSE, 16 image(s), 77 pas réussis » en repli |

Arrêt par RCON (`stop`), `run-server/ops.json` identique à avant. Au tour 1, un second serveur sans MCEF (« Done (14.920s) », 0 ligne `mcef`) a rejoué le refus du client avec WebDisplays.

### Performance (tâche 11, puis tour 1)

Machine : AMD Ryzen 7 5800X (8 cœurs, 16 fils), NVIDIA GeForce RTX 4060 (pilote 32.0.16.2114), 31,9 Go de mémoire, Windows 11 Pro 10.0.26200. Client : Temurin 17.0.20.1, Xmx 8172 Mo, fenêtre de 1280 × 720 sur le second écran, échelle d'interface 2, `options.txt` en synchro verticale, plafond 120 img/s, distance de rendu 12, graphismes « fancy ». Un seuil du plan : au moins 80 % des images par seconde pendant l'ouverture (60 s), 90 % après la fermeture, au plus +64 Mo de tas, au plus +20 Mo par ouverture. Chiffres relevés tels quels, sans arrondi ; les tas sont pris après deux `System.gc()`.

**Réglages tels quels** (synchro verticale, plafond 120 ; stabilisation 20 s) :

| Passe (PID) | avant | pendant | après | tas avant → après | tas aux cycles 1 à 5 |
|---|---|---|---|---|---|
| perf 1 (2448) | 112,3 | 111,9 (99,7 %) | 116,6 (103,8 %) | 1130,1 → 1130,3 Mo (+0,2) | 1132,3 / 1132,5 / 1130,3 / 1130,3 / 1130,4 (+0,0 Mo par ouverture) |
| perf 2 (44600) | 115,9 | 117,2 (101,2 %) | 117,5 (101,4 %) | 1133,3 → 1133,4 Mo (+0,2) | 1133,4 / 1133,5 / 1131,4 / 1131,4 / 1131,4 (−0,4) |

Tous les seuils tiennent, mais **le plafond de 120 et la synchro verticale cachent le coût** : ces chiffres ne disent presque rien. D'où les passes suivantes.

**Sans plafond ni synchro verticale** (`-PtelephoneShotsSansPlafond`, en mémoire seulement : plafond à 260) :

| Mesure (PID, heure) | témoin avant | avant | pendant | après | après / avant | seuil 90 % | jcef_helper : avant ouverture / ouvert / après fermeture | tas avant → après |
|---|---|---|---|---|---|---|---|---|
| passe 1 (63108) | — | 375,0 | 595,2 | 800,9 | — | — | — | 1132,5 → 1134,8 |
| tâche 11 (32220, 03:41) | 893,1 | 947,8 | 908,2 (95,8 %) | 830,6 | **87,6 %** | **manqué** | 2 / 5 / 4 | 1133,9 → 1134,0 (−0,4 Mo par ouverture) |
| tour 1, n° 1 (52680, 05:13) | 863,6 | 932,7 | 988,7 (106,0 %) | 955,3 | **102,4 %** | tenu | 2 / 5 / 4 | 1134,5 → 1132,7 (+0,0) |
| tour 1, n° 2 (35932, 05:20) | 1225,4 | 1231,9 | 1255,4 (101,9 %) | 1204,7 | **97,8 %** | tenu | 2 / 5 / 4 | 1133,7 → 1131,9 (−0,0) |

La passe 1 est **non valable** (les images par seconde montaient encore : monde neuf, JIT froid ; stabilisation portée à 90 s et témoin ajouté ensuite), bien que son bilan dise que les seuils tiennent. **Lecture : le seuil « après » varie** (87,6 %, 102,4 % puis 97,8 %) : manqué une fois sur trois, tenu les deux autres. Cela est **compatible avec du bruit de mesure** (deux passes sur trois tiennent le seuil, ce n'est pas une perte constante) : le niveau « avant » lui-même change de 932,7 à 1231,9 img/s d'une passe à l'autre, et le témoin diffère de la mesure « avant » de 0,5 % à 7,4 % dans la même passe. **La cause du 87,6 % n'est pas établie** : que ce soit du bruit n'est pas démontré.

**Mémoire** (`tools/telephone-memoire.ps1`, passe perf 2, PID 44600 ; mémoire de travail, en Mo) :

| Étape | java | jcef_helper (nombre) | jcef_helper | total |
|---|---|---|---|---|
| perf-debut | 4800,3 | 2 | 88,2 | 4888,5 |
| perf-tas-avant | 4862,9 | 2 | 91,7 | 4954,6 |
| ouverture prête | 4900,4 | 5 | 238,4 | 5138,8 |
| fin de « pendant » | 4883,0 | 4 | 184,1 | 5067,1 |
| perf-tas-apres | 4884,0 | 4 | 183,8 | 5067,8 |
| cycles 1 à 5, téléphone fermé | 4884,5 / 4867,4 / 4867,1 / 4866,2 / 4846,5 | 4 / 5 / 4 / 5 / 4 | 185,1 / 243,6 / 186,9 / 240,0 / 188,3 | 5069,6 / 5111,0 / 5054,0 / 5106,2 / 5034,8 |

**Quatre `jcef_helper` restent après la première fermeture** (2 avant la première ouverture, 5 juste après, 4 ensuite ; environ 184 à 190 Mo aux trois mesures du tour), sans croissance sur cinq cycles. Le tas Java ne bouge pas (±2 Mo). La mémoire de travail de `java` (environ 4,8 Go) est celle du client de développement avec tout le pack. Si MCEF garde ces processus exprès : non établi.

### Cohabitation avec WebDisplays (tâche 11)

- Lu dans le jar (javap) : objets `webdisplays:minepad` (tablette), `screen`, `keyboard`, `laserpointer`, `linker`… ; réglage `browser_options.home_page` (défaut `mod://webdisplays/main.html`, page locale du jar) ; le minePad ouvre son étiquette `PadURL`, sinon la page d'accueil ; **la seule adresse externe par défaut est `https://www.google.com`, dans `ScreenData` (les écrans en bloc, jamais posés dans les essais)**, plus des modèles YouTube ; aucun contrôle de mise à jour.
- Réglage de l'essai : `run/config/webdisplays_common.toml`, `home_page = "mod://oneforallsurvival/web/selftest.html"` (dossier de jeu, non commité ; l'original est gardé hors du dépôt), et `PadURL` du minePad sur la même page. **Aucune page externe.**
- Passe 2 (PID 64660) : « TOUT PASSE, 7 image(s) ». Le téléphone s'ouvre d'abord (le routeur de messages existe alors quand le minePad est créé) ; le téléphone déverrouillé s'affiche pendant que le minePad, en main gauche, affiche la page d'essai : **les deux navigateurs rendent en même temps** (capture wd-04). Pont du téléphone : 8 messages `{ready=2, layout=4, setup=1, wallpaper=1}`, **0 étranger**. Aucune exception de WebDisplays ni du mod au journal ; une erreur GL « 65539: Invalid standard cursor 0x00000000 » à la création du minePad, sans suite visible.
- Passe 1 (PID 48284) : le minePad ne se ferme que par Maj + Échap ; il avait été créé avant notre routeur, sa page n'avait donc pas `oneforallQuery` et se renvoyait l'écho à elle-même. Ordre à connaître : le routeur est posé à la première vue du téléphone.
- **Un client avec WebDisplays est refusé par un serveur qui ne l'a pas** (voir plus haut) : **décision de Killian en attente**, voir « Pour le pack ».

### Captures regardées

Toutes ouvertes avec l'outil de lecture d'images, rangées hors du dépôt (`run*/`, `run-demo/`) ; chaque passe réécrit son dossier.

- **Pages dans le navigateur de l'application** (tâche 8, 17 captures puis 2 au tour 1, vue de 390 × 844, copie du dossier `html/` sans la ligne de CSP, servie en local) : choix du code, verrouillé, secousse des points, blocage et décompte, glissement, accueil, Réglages ouverture et bas, feuille du code, « Code modifié », alerte du non-propriétaire, ancien code faux, mode avion et fond Nuit, effacement, accueil et verrouillé de nuit. Le contrôleur en a revu trois (verrouillé, accueil, Réglages) : propres.
- **Avec MCEF** (`run/frames-telephone-mcef/`, 20 images à la tâche 9, 31 puis 32 à la tâche 11 ; `run/frames-web/`, 6 images par passe à la tâche 3) : cadre graphite et coins arrondis nets, aucun liseré entre le verre et la page, clavier rond, échelle 4, blocage, Réglages défilés, feuille et alerte d'effacement, messages de fermeture, repli dans le même cadre, retour après adresse non permise ; à la tâche 3, coins transparents sur le monde (pixels (90, 107, 135) en haut et (39, 51, 26) en bas), orientation (bandeaux « HAUT » et « BAS »), zoom mesuré sur capture (100 px à 1,0, 150 px à 1,5).
- **Repli** (`run/frames-telephone-repli/`, 21 images par passe ; `run/frames-telephone-page-absente/`, 28) : choix du code, accueil, les trois champs masqués, échelle 4, blocage, prise.
- **Textures, modèles et repli à petite taille** (tâche 10, `run-demo/visuels-captures/telephone_*.png`, 32 images par passe, deux passes, 4 revues aussi par le contrôleur) : mur de quatre prises (vide, branchée à l'arrêt, en charge, sans courant), voisins de pierre, profil, quatre orientations sur un pilier, objet en main (1re et 3e personne), barre, inventaire, infobulle, écran de repli en 320 × 240, 480 × 260 et 640 × 360. Défauts vus et corrigés avant la fin : voyant caché par le téléphone posé, plaque qui ressemblait à un visage, barre de charge qui recouvrait le téléphone, « Pas de service » qui chevauchait la batterie.
- **WebDisplays** (`run/frames-telephone-webdisplays/`, 7 images).

## API de MCEF 2.1.6 relevée et schéma retenu

| Fait | Preuve | Niveau |
|---|---|---|
| `MCEF.createBrowser` revient à `new MCEFBrowser(MCEF.getClient(), url, true)`, puis `setCloseAllowed()` et `createImmediately()` ; le mod passe par le même chemin avec une sous-classe qui redéfinit `onPaint` | `javap`, tâche 3 | lu au bytecode |
| La texture GL est créée dès la construction (`MCEFRenderer.initialize` par `Minecraft.submit`) : un identifiant de texture non nul ne prouve pas que la page a peint ; le mod note la première image | `javap`, tâche 3 | lu au bytecode |
| Le schéma `mod://oneforallsurvival/<chemin>` sert `assets/oneforallsurvival/html/<chemin>` : « page chargée mod://oneforallsurvival/web/selftest.html (200) » ; une page absente donne « Resource oneforallsurvival/phone/absente.html NOT found! » et `ERR_UNKNOWN_URL_SCHEME`, reçu par `onLoadError` | journaux, tâches 3 et 11 | observé |
| **Schéma retenu : `mod://oneforallsurvival/`. Pas de bascule vers `https://oneforallsurvival.local/`** (l'étape 7 de la tâche 3 est sans objet) ; chemins en minuscules (`WebPages.url` refuse le reste ; MCEF 2.1.6 n'a pas été essayé avec une majuscule) | tâche 3 | décision, schéma servi vu au journal |
| `CefDownloadMixin.setupLibraryPath` **réécrit lui-même `mcef.libraries.path`** : `../build/mcef-libraries` si `../build` existe (toujours vrai depuis `run/`), sinon `mods/mcef-libraries`. La propriété posée par `build.gradle` à la tâche 1 n'avait donc aucun effet | `javap`, puis journal « …windows_amd64.tar.gz.sha256 -> …/build/mcef-libraries/… » | lu et observé |
| MCEF télécharge à chaque démarrage le fichier `.sha256` ; si la requête échoue, MCEF ne démarre pas (`setFailed`) et le jeu passe au repli | tâche 3 | requête observée au journal ; **l'échec n'a pas été joué** |
| Chromium peint en alpha prémultiplié : `blendFuncSeparate(ONE, ONE_MINUS_SRC_ALPHA, …)`, coins nets sans liseré | capture, tâche 3 | observé (la variante non prémultipliée n'a pas été essayée) |
| `setZoomLevel` vaut `1,2^niveau` (100 px CSS → 150 px à 1,5) ; **Chromium garde l'agrandissement par hôte** (`oneforallsurvival` pour toutes nos pages) et `setZoomLevel` s'applique après le premier script : le niveau est remis à `onLoadStart` et `onLoadEnd`, et la page renvoie `layout` à chaque `resize` | mesure sur capture, tâche 3 | mesuré |
| `MCEFBrowser.sendKeyPress` envoie le code de touche GLFW et le code de balayage GLFW brut (`new CefKeyEvent(KEY_PRESS=1, keyCode, (char) keyCode, mods)`) : les chiffres tapés ne marchaient pas quand la page ne lisait que `e.key` ; elle se rabat sur `e.keyCode` (qui vaut le code Windows ; les chiffres GLFW et VK coïncident). `charTyped` passe les accents, Retour arrière passe par `keyPress` | `javap` ; effet vu (11 échecs, puis plus aucun) | lu au bytecode ; **l'hypothèse sur la valeur de `e.key` (« Unidentified ») n'a pas été relevée directement** |
| Les rappels de CEF tournent sur le fil du rendu, dans `CefApp.N_DoMessageLoopWork` appelé par `GameRenderer.render`, et `Minecraft.execute` y exécute sur place : fermer un navigateur depuis son propre `onLoadError` a planté la JVM (`CefBrowser_N.N_Close`). Les rappels passent par `Minecraft.tell` | `hs_err_pid61408.log`, puis 9 replis sans plantage | observé |
| Le routeur de messages du mod (`oneforallQuery`, nom propre, jamais retiré) et les gestionnaires à liste (`addLoadHandler`, `addDisplayHandler`) sont posés sur le `CefClient` partagé ; aucun gestionnaire à emplacement unique n'est remplacé (WebDisplays s'en sert). Un navigateur de WebDisplays créé avant la première vue du téléphone n'a pas `oneforallQuery` | cohabitation de la tâche 11 (0 message étranger, deux navigateurs qui rendent) | observé ; **que ces gestionnaires soient à emplacement unique n'a pas été relevé dans les rapports** |
| Une erreur GL « 65539: Invalid standard cursor 0x00000000 » apparaît une fois par lancement (survol du bouton, ou création du minePad) | journaux, tâches 3 et 11 | observé, sans effet visible ; cause (curseur « main » demandé à GLFW) **non vérifiée** |

## Décisions du lot

| Sujet | Décision |
|---|---|
| MCEF | 2.1.6 de CinemaMod, celle du pack (WebDisplays s'appuie dessus) ; facultatif, côté client seulement ; `mods.toml` `[2.1.6,2.2)` (2.2 et plus : le fork de Keksuccino, autre API) ; aucune classe de `com.cinemamod` ni de `org.cef` hors de `client/web/mcef/` (garde `McefIsolationTest`) ; `-PwithMcef` refusé avec une tâche serveur ou GameTest |
| Chromium | **Aucun téléchargement de Chromium n'a eu lieu** : copié depuis le launcher de Killian (décision du 2026-11-19). **MCEF 2.1.6 fait une petite requête de somme de contrôle** (`.sha256`, environ 300 octets, `mcef-download.cinemamod.com`) **à chaque démarrage** ; accord tacite de Killian, en développement seulement |
| Pages | Locales seulement : chaque page porte la CSP `default-src mod: 'unsafe-inline'; img-src mod: data:; connect-src 'none'`, aucune adresse `http://` ou `https://` dans `html/`, toute adresse principale hors de `WebPages.isAllowed` ramenée à la page de départ, un message de la page jamais cru (jeu de clés exact côté client, tout revérifié par le serveur) |
| Serveur | Il fait foi pour le code (jamais envoyé au client ni écrit dans la pile : seuls un sel et une empreinte SHA-256 vivent dans `PhoneData`), la batterie, le propriétaire et les réglages ; une vue verrouillée ne contient ni le nom du propriétaire ni le mode avion ; **seul le fond d'écran reste visible** (voulu, comme sur un vrai téléphone) |
| Valeurs (réglables dans `oneforallsurvival-phone.toml`, fichier SERVER) | Batterie 72 000 FE ; 2 FE/t écran allumé (**30 minutes d'écran**, décision du 2026-11-19) ; prise 40 FE/t (charge complète en 90 s) ; 5 essais puis blocage de 60 s |
| Blocage | Base de temps unique (`EconomyClock.now()`), fin de blocage bornée à `now + durée` à chaque lecture et écriture (une horloge murale qui recule ou un NBT corrompu ne bloquent plus pour toujours) |
| **Copies créatives** | **Deux piles de même `phone_id` partagent propriétaire, code et blocage, par choix** (testé : `deuxCopiesPartagentLeBlocage`). **`/phone reset` sur une copie efface l'autre** ; il ferme toutes les sessions de ce téléphone. Chaque geste relit les données, une session déverrouillée garde le sel qui l'a ouverte et se reverrouille si le code change |
| **Prise murale sans propriétaire** | **N'importe quel joueur reprend le téléphone d'un autre**, mais ses données restent protégées par le code. **À garder en tête pour les lois en RP** : rien dans le mod n'interdit de voler un téléphone branché |
| Fabrication | Le téléphone et la prise se fabriquent à la table de fabrication, par tous (tag `table_only`) |
| Repli | Interface classique (écran Minecraft) quand MCEF est absent, non initialisé, échoue à créer, à charger ou ne répond pas dans les 100 ticks ; un seul avis par session de jeu ; mêmes fonctions et même état que la page |
| **WebDisplays** | Le jar du pack (2.0.2) cohabite avec le téléphone (page locale seulement, 0 message étranger). **Un client avec WebDisplays est refusé par un serveur qui ne l'a pas** (canal réseau) : si le pack donne WebDisplays aux joueurs, **le serveur de production doit l'avoir aussi. Décision de Killian en attente** |
| Réseau | `BenchNetwork.PROTOCOL` passe de `"9"` à `"10"` (une seule fois, tâche 5) : client et serveur se livrent ensemble ; `ID_PHONE_STATE = 26`, `ID_PHONE_ACTION = 27` |

## Défauts trouvés et corrigés

| Défaut | Trouvé par | Correction |
|---|---|---|
| **La JVM plantait au repli par page cassée** (`hs_err`, `CefBrowser_N.N_Close` dans `CefApp.N_DoMessageLoopWork`) : `McefHooks` passait par `Minecraft.execute`, qui exécute sur place sur le fil du rendu, donc le navigateur se fermait depuis son propre `onLoadError` ; même chemin dans `onQuery` | essai réel, tâche 11 | `Minecraft.tell` ; `onAddressChange` (revue) corrigé de même et joué une fois en jeu |
| **Le clavier ne marchait pas dans MCEF** (chiffres tapés sans effet) | essai réel, tâche 9 | repli sur `e.keyCode` (`keyOf` de `phone.js`) |
| **`mcef.libraries.path` était sans effet** : MCEF aurait téléchargé Chromium | essai réel, tâche 3 | tâche `prepareMcefLibraries` et `skip-download=true` |
| Agrandissement gardé par hôte : la page suivante partait à l'échelle de la précédente | essai réel, tâche 3 | remis à `onLoadStart` et `onLoadEnd`, la page renvoie `layout` |
| `/phone reset` ne fermait pas les sessions ouvertes : une session survivait et réglait le téléphone du nouveau propriétaire | revue, tâche 5 | `closeAll(ERASED)` et sel revérifié à chaque geste |
| Fin de blocage non bornée (blocage éternel possible) | revue, tâche 4 | bornée à la lecture et à l'écriture |
| Un code posé sur une copie ne reverrouillait l'autre écran qu'à son prochain geste | tâche 9 | `UPDATE` immédiat aux autres sessions (`relockOthers`) |
| Le `Sync` de Gradle effaçait le `.sha256` que MCEF dépose ; une écriture de configuration attachée à une tâche à jour ne tournait pas | revue, tâche 3 | `preserve`, tâche `ensureMcefSkipDownload` à part |
| Un repli décidé pendant `init()` de l'écran, et un navigateur inscrit qui fuit si la création lève | revue, tâche 3 | `FallbackGate` (repli différé par `tell`), `CreateGuard` |
| `toString` de `PhoneActionPacket` écrivait le code en clair | revue, tâche 9 | masqué, avec test |
| Un automate resté 180 s sur l'écran de refus de mods, verrou gardé | revue, tâche 11 | refus reconnu en 3 s |

## Non prouvé et limites

- **Mac à puce Apple : non vérifié** (aucun Mac connu ici ; question ouverte du cahier, section 10). Si MCEF n'y marche pas, l'écran de repli doit suffire.
- **Premier lancement chez un joueur neuf** : le téléchargement de Chromium par MCEF (environ 100 Mo, chiffre de la page CraftBrowser, non mesuré), un **antivirus qui le bloque**, ce que fait le launcher chez un joueur qui n'a pas encore Chromium, et l'échec de la requête `.sha256` (qui doit faire passer au repli) : non vérifiés. En développement, Chromium vient du launcher de Killian.
- **Plusieurs joueurs en même temps** : aucun essai à deux vrais clients ouverts ensemble sur le téléphone (les trois clients du serveur dédié se sont succédé ; les GameTests jouent deux joueurs simulés pour les copies). **Plusieurs joueurs devant WebDisplays** : non vérifié, et **un client avec WebDisplays contre un serveur avec WebDisplays** : non essayé (`-PwithMcef` est refusé avec une tâche serveur).
- **Autres cartes graphiques, autres processeurs, autres tailles de fenêtre** : tout a été joué sur le poste de Killian (RTX 4060, 1280 × 720 et 1920 × 1040) ; plein écran non essayé.
- **Jar de production** jamais lancé (client de développement et serveur de développement avec le pack) ; installer un serveur Forge hors de Gradle demande l'accord de Killian (téléchargement). Le serveur de production n'a pas été relancé avec un téléphone déjà codé : la persistance (blocage, code, batterie) n'est prouvée qu'en GameTest et JUnit, pas par une relance réelle ni un arrêt brutal.
- **Performance** : le seuil « après fermeture » (90 %) a été manqué une fois sur trois, sans cause établie (voir plus haut) ; les chiffres avec les réglages tels quels (plafond 120) ne disent presque rien ; coût sur une machine moins puissante non mesuré. **Quatre `jcef_helper` restent** après la première fermeture (environ 184 à 190 Mo), stables ; leur coût en images par seconde et le fait que MCEF les garde exprès : non établis.
- **Prise vide** : son dessin ressemble encore un peu à un petit visage ; **jugement d'apparence en attente de Killian**. Le voyant n'est qu'une couleur de texture : la nuit il n'éclaire rien (à décider s'il doit être émissif). Captures du repli en `fr_fr` seulement ; « Delete » à l'échelle 4 en anglais couvert par le calcul, pas par une capture.
- **Narration** : `CodeBox` renvoie le libellé seul, jamais la valeur, mais la narration n'a pas été écoutée. Clavier AZERTY : la rangée du haut sans Maj donne des `keyCode` de 48 à 57, pris pour des chiffres (voulu, différent d'un navigateur ordinaire).
- **Comparaison à temps constant du code** : relue seulement, non testable en JUnit.
- **Chemins d'échec jamais joués** : création du navigateur qui lève (le `CreateGuard`), requête `.sha256` en échec, fermeture par batterie vide dans un client réel. Le repli décidé pendant `init` (`FallbackGate.beginInit`, arrivé au commit `7e19896`) **n'a jamais été joué en jeu** : sans MCEF, `PhoneClient.accept` ouvre directement l'écran de repli sans passer par `WebScreen.init`, et les deux `/webessai` sans MCEF de la tâche 3 datent d'avant `FallbackGate`. Dans le jeu livré, ce chemin ne sert que si la création du navigateur échoue (`CREATE_FAILED`), cas jamais joué non plus ; son câblage n'est gardé par aucun test.
- **Autres points parqués** : **Create est dans le pack** : une contraption (piston mécanique, roulement) pourrait emporter une prise occupée, block entity et téléphone compris, alors que `onRemove` lâche déjà le téléphone au retrait du bloc (deux exemplaires du même `phone_id` au désassemblage ; l'ordre de retrait de Create n'a pas été relevé). La prise est donc dans le tag `create:non_movable` (`data/create/tags/blocks/non_movable.json`, tag présent dans Create 6.0.8, gardé par le GameTest `laPriseNeSeDeplacePasAvecCreate`) ; **le déplacement par une contraption n'a pas été joué en jeu** ; une copie dont une autre session change le fond ou le mode avion ne reçoit pas d'`UPDATE` avant son propre geste ; un reverrouillage détecté au tic n'est poussé au client qu'au prochain `UPDATE` (au plus environ 360 tics) ; la dépense de batterie s'applique aussi en créatif, `drainPerTick` à 0 et deux copies drainées en même temps ne sont pas testés ; une image vide possible avant le repli ; cibles de la page de moins de 44 px et chiffres de barre d'état de 10 à 11 px (acceptés) ; les tests de mise en page du repli recopient les hauteurs de `drawCode` et la largeur d'« Effacer » (40 px) ; `-PwithMcef runData` n'est pas refusé (inoffensif) et le refus se lit sur la ligne de commande (une abréviation de tâche y échappe) ; les GameTests du téléphone qui rendent un joueur opérateur écrivent `run-gametest/ops.json` (dossier d'essai seulement).
- **Un voleur patient trouve le code** (revue finale, important I1) : au cinquième faux code le blocage dure toujours 60 s, sans croître, et il suit l'horloge réelle : hors ligne et écran fermé compris. Avec un client modifié qui automatise la saisie : 10 000 codes à 5 par minute, soit **33 h au plus et environ 17 h en moyenne**. Au lot T1, le téléphone ne contient que le fond d'écran et le mode avion. **Blocage croissant (la durée double, plafonnée, son niveau sauvegardé) à faire avant le lot T3 (messages)** ; inscrit au `tasks/todo.md` comme condition de T3.
- **MCEF pas encore démarré** (`NOT_INITIALIZED`) : un joueur qui ouvre le téléphone juste après son entrée dans le monde passe au repli et consomme l'avis unique de la session ; un vrai échec plus tard ne serait pas annoncé.
- **Une page qui échoue est retentée à chaque ouverture** : jusqu'à 5 s sur un cadre vide pour `NO_READY`, environ 0,3 s pour `LOAD_ERROR` (sept fois de suite pendant l'essai de la page absente).
- **`McefWebView.run` ne contrôle pas l'adresse courante** : entre une navigation hors des pages du mod et le retour à la page de départ (différé d'un tic), un rendu livrerait l'état à une page non permise. Risque faible : les pages n'ont aucun lien et la CSP est stricte.
- **Le nom du propriétaire est figé au choix du code** : un changement de pseudo n'apparaît pas dans « Téléphone de … ».
- **Pages en français en dur** : un client en anglais voit la page web en français et l'écran de repli en anglais ; accepté (serveur francophone).
- **Ordre des navigateurs** : le routeur est posé à la première vue du téléphone ; un navigateur de WebDisplays créé avant n'a pas `oneforallQuery` (sans effet pour le téléphone). Le dossier `run/` garde la page d'accueil de WebDisplays réglée sur la page d'essai du mod (original gardé hors du dépôt) : à remettre avant d'y lancer WebDisplays pour autre chose.
- **Le plan des documents de lore** repose maintenant sur ce socle ; il reste à l'adapter (voir le rapport de la tâche 12). Les pages de Killian n'ont pas de CSP : la garde `WebResourcesTest` les refuserait telles quelles dans `html/`.

## Pour le pack

- **MCEF 2.1.6 et WebDisplays 2.0.2 sont déjà dans le pack : rien à ajouter** (d'après le registre du lot ; non revérifié à la tâche 12). La version de MCEF doit rester dans `[2.1.6,2.2)`.
- **Le serveur n'a besoin ni de MCEF ni de WebDisplays** pour le téléphone (il démarre sans, 0 ligne `mcef` au journal).
- **Si le pack donne WebDisplays aux joueurs, le serveur doit l'avoir aussi**, sinon personne n'entre (canal réseau). **Décision de Killian en attente.**
- Le protocole réseau du mod est à `"10"` : le client et le serveur se livrent ensemble.
- MCEF fait une requête `.sha256` à chaque démarrage du client (accord tacite de Killian, en développement seulement) ; ce que le launcher fait pour Chromium chez un joueur neuf n'est pas vérifié.
- Réglages du serveur : `oneforallsurvival-phone.toml` (créé au premier lancement : batterie, dépense, prise, essais, blocage).

## Revue finale

Faite le 2026-11-20 par un relecteur distinct, en lecture seule (base `bf86a2f`, tête `828fea5`, 19 commits) : **0 critique, 3 importants, 9 mineurs ; verdict « prêt après corrections »**. Aucun chemin, même entre deux tâches, ne permet de voir ou de régler un téléphone sans son code (ouverture toujours en `LOCKED` ou `SETUP`, propriétaire vérifié avant l'essai de code, sessions fermées par `/phone reset` et l'effacement, sel revérifié à chaque geste, vue verrouillée sans nom ni avion). Les chiffres de la preuve concordent avec les rapports ; les écarts portaient sur ce qu'elle affirmait.

| Constat | Suite |
|---|---|
| **I1** un voleur patient trouve le code (blocage fixe de 60 s) | **Limite écrite** (« Non prouvé et limites ») et **condition du lot T3** au `tasks/todo.md` : blocage croissant (durée qui double, plafonnée, niveau sauvegardé) avant les messages. Acceptable pour T1 : le téléphone ne contient que des réglages |
| **I2** la preuve disait « joué en jeu » pour `FallbackGate.beginInit` | **Corrigé dans le texte** : jamais joué en jeu depuis `7e19896` |
| **I3** Create est dans le pack : une prise occupée déplacée par une contraption pourrait dupliquer le téléphone | **Corrigé** (commit `fc3806f`) : `data/create/tags/blocks/non_movable.json` (`replace: false`, valeur `oneforallsurvival:wall_socket` ; le tag existe sous ce nom dans le jar de Create 6.0.8 du cache de Gradle, et `BlockMovementChecksImpl`, qui règle le déplacement des blocs, le référence) ; GameTest `laPriseNeSeDeplacePasAvecCreate` (le bloc de la prise est dans le tag, la pierre témoin n'y est pas), vu rouge sans le fichier. **Le déplacement par une contraption n'a pas été joué en jeu** |
| m1 fond d'écran visible sur l'écran verrouillé | Voulu ; texte de la preuve aligné (« ni le nom du propriétaire ni le mode avion ; seul le fond d'écran reste visible ») |
| m2 à m5, m9 | Écrits comme limites (MCEF pas encore démarré, page retentée à chaque ouverture, `McefWebView.run` sans contrôle d'adresse, nom du propriétaire figé, pages en français en dur) |
| m6 « bruit de mesure », « sans coût mesurable » | Reformulés : compatible avec du bruit, cause du 87,6 % et coût des `jcef_helper` non établis |
| m7 requête `.sha256` | Écrite « accord tacite de Killian, en développement seulement » |
| m8 cahiers et plan du lore | Corrigés : cahier du téléphone (choix de l'auteur sur le fork), cahier du lore (MCEF 2.1.6, copie sans téléchargement en développement), plan du lore (le socle ramène l'adresse principale à la page de départ après coup et ne filtre aucune sous-ressource ; `window.oneforall.send`) |

**Tests après les corrections** (sous le verrou de lancement, relus dans les journaux) :

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline runGameTestServer`, tag retiré (13:31 à 13:33) | « 1 required tests failed » : `laprisenesedeplacepasaveccreate`, « la prise devait être dans le tag create:non_movable » ; tag restauré ensuite |
| `./gradlew --offline test runGameTestServer` (13:33 à 13:35) | « All 1031 required tests passed » (13:35:48) ; JUnit : 683 tests, 0 ignoré, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml`) |
| `./gradlew --offline runGameTestServer -PwithPack` (13:42 à 13:45) | « All 1031 required tests passed » (13:44:54) ; 113 lignes `createaddition` au journal (le pack est chargé) |

Le verrou a été cédé entre deux passes à AXIONENGINE, qui l'avait pris à 13:36. Aucun client lancé.

## Assistant de première configuration

Ajouté le 2026-11-20 après la clôture du lot, sur `main`. Un téléphone neuf (état `SETUP`) ne s'ouvre plus directement sur le choix du code : la page montre un assistant à la manière d'iOS, au style du reste de la page (transitions CSS, aucune `requestAnimationFrame`) : **Bonjour** (le mot change de langue en fondu : Bonjour, Hello, Hola, Ciao, Hallo, Olá, Hej, puis Bonjour reste), **Configuration rapide** (annonce le fond d'écran et le code), **Fond d'écran** (le fond choisi en plein écran derrière l'aperçu de l'heure, quatre vignettes), **le code** (l'écran du choix du code, inchangé dans sa logique et ses messages d'erreur), **Tout est prêt** (« Téléphone de <nom> », texte posé par `textContent`, puis Commencer vers l'accueil). Un bouton Retour relie les étapes jusqu'au code compris (la saisie en cours est oubliée). Le lot T2 ajoutera une étape « Carte SIM » entre le code et Tout est prêt (point d'extension noté dans `phone.js`, non construit).

**Le serveur reste l'autorité.** Rien n'est configuré avant l'envoi du code : le geste `setup` porte le code et le fond dans un seul message (`{"a":"setup","code":"…","wallpaper":"…"}`), `PhoneActionPacket` garde sa forme (`first` = code, `second` = fond, 16 caractères au plus ; protocole réseau inchangé). `PhoneService.setup` valide le code comme avant, puis écrit code, propriétaire et fond dans **le même enregistrement** ; un fond hors de `PhoneWallpapers.IDS` est ignoré (le fond en place reste) et le code est posé quand même. `PhoneBridgeMessages.toAction` accepte `wallpaper` sur `setup` seulement, chaîne de la liste, sinon rien ne part. Fermer l'écran au milieu de l'assistant laisse un téléphone neuf ; la réouverture reprend à Bonjour. L'**écran de repli** (sans MCEF) n'a pas d'animation : au choix du code d'un téléphone neuf, un bouton « Fond suivant (…) » (clé de langue existante, `fr_fr` et `en_us`) fait défiler les fonds, affichés tout de suite, envoyés avec le code. Le mode `#mock` de la page part d'un téléphone neuf (`phoneMock.locked()` pour l'ancien départ, code 1234).

### Ce qui est prouvé, et comment

| Preuve | Résultat lu |
|---|---|
| JUnit d'abord rouges : `PhoneBridgeMessagesTest` (champ `wallpaper` : valeur valide, chaque fond de la liste, inconnu, vide, majuscules, espace, nombre, booléen, `null`, tableau, objet, code en chiffres pleine chasse, code trop court, champ de plus, `wallpaper` sur `unlock`/`erase`/`wallpaper`) et `PhoneFallbackLayoutTest` (le bouton « Fond suivant » tient entre le texte et le clavier, de 320 × 240 à 1920 × 1080) | 9 rouges avant le code (1 + 8 tailles), puis `./gradlew --offline test` : **711 tests, 0 échec, 0 erreur, 0 ignoré** (somme des `build/test-results/test/*.xml`) ; `WebResourcesTest` vert (CSP inchangée, aucune adresse externe) |
| GameTests, lot nouveau `telephone_assistant` (6 tests) : `SETUP` avec fond pose code et fond ensemble (puis le code rouvre le téléphone) ; fond inconnu, en majuscules, avec une espace ou de 16 caractères ignoré, code posé ; code non valide avec un fond valide : ni code ni fond ; `SETUP` verrouillé ou déverrouillé refusé, fond non appliqué ; sans fond, le fond enregistré reste (comportement d'avant) ; `SETUP` d'une autre session : rien | rouge d'abord (15:11, « 1 required tests failed » : `unsetupavecfondposelecodeetlefondensemble`, les cinq autres gardent un comportement déjà en place), puis **« All 1037 required tests passed »** sans le pack (15:23:35) et avec `-PwithPack` (15:30:55, 113 lignes `createaddition` au journal) |
| Mutations, chacune restaurée | fond appliqué avant la validation du code : `uncodenonvalideneposenicodenifond` rouge (« vu nuit », 15:32) ; fond hors liste qui passe : `unfondinconnuestignoreetlecodepose` et `sansfondlechoixducoderestecommeavant` rouges (« vu plage », 15:33) ; pont qui laisse passer tout fond : `unFondDeSetupFauxEmpecheLeDepart` rouge (5 cas) ; bouton du repli collé au clavier : `leFondDuChoixDuCode…` rouge (8 tailles) |
| Navigateur de l'application, copie du dossier `html/` sans la ligne CSP servie en local, page dans un cadre (`iframe`) de 390 × 844 : l'émulation de taille du volet passait à 2 pixels par px CSS et coupait les captures | Bonjour (Bonjour, Hello, Hola, Ciao), Configuration rapide, Fond d'écran (Aube, Jungle, Nuit), code (choix, confirmation, codes différents), Tout est prêt, accueil, verrouillé ensuite au fond choisi ; Retour à chaque étape (classes et points relevés) ; le message `setup` part avec le fond choisi |
| Essai réel avec MCEF (`runClient -PwithPack -PwithMcef -PtelephoneShots=frames-assistant-mcef`, PID 27292, 15:43 à 15:47) | **« TOUT PASSE, 44 image(s), 183 pas réussis »** : téléphone neuf fermé au milieu de l'assistant (fond Nuit choisi) puis rouvert sur Bonjour, fond Aube, toujours `SETUP` ; Bonjour en trois langues puis la fin du tour en français ; Retour depuis Configuration rapide et depuis le code ; fond Lagon, code cliqué puis confirmé au clavier ; serveur : `UNLOCKED`, fond `lagon`, propriétaire « Dev » ; Tout est prêt, Commencer, accueil ; le reste du parcours du lot T1 (Réglages, blocage, prise, effacement puis assistant rejoué au fond Jungle, `PHONE_GONE`, `/phone reset` puis retour à Bonjour après une adresse non permise, replis en cours de route). Pont : 60 messages `{ready=9, layout=39, setup=2, wallpaper=1, airplane=1, changeCode=1, unlock=6, erase=1}`, **0 étranger** |
| Essai réel sans MCEF (`runClient -PwithPack -PtelephoneShots=frames-assistant-repli`, PID 42720, 15:39 à 15:42) | **« TOUT PASSE, 31 image(s), 135 pas réussis »** : « Fond suivant » trois fois (Nuit) puis Échap, réouverture neuve au fond Aube ; une fois (Lagon), code, accueil au fond Lagon, propriétaire « Dev » ; plus tard Jungle |

Clics par les méthodes de l'écran aux centres des boîtes du dernier `layout` stable (attendu avant chaque clic). Les deux clients : fenêtre « [fenetre] second écran (2600, 40) », « [souris] rendue » au journal, fermés seuls, `run/options.txt` de SHA-1 `dbc3d03c…` identique avant et après, aucune ligne « Downloading Chromium », aucun des codes d'essai au journal (`grep -c` à 0).

**Captures regardées** (outil de lecture d'images ; dossiers hors du dépôt, réécrits à chaque passe) : `run/frames-assistant-mcef/` : `00-bonjour`, `00b-fond-nuit-choisi-puis-ferme`, `01-bonjour`, `01b-bonjour-langue-2` (Hello), `01c-bonjour-langue-3` (Hola), `01d-bonjour-fin-du-tour` (Bonjour), `01e-configuration-rapide`, `01g-fond-choisi-lagon`, `01i-retour-au-fond`, `02-confirmez-le-code`, `02b-tout-est-pret` (« Téléphone de Dev »), `03-accueil` (fond Lagon), `27b-retour-apres-adresse-non-permise` (Bonjour) ; `run/frames-assistant-repli/` : `00b-fond-nuit-choisi-puis-ferme`, `01b-repli-fond-lagon-avant-le-code`, `03-accueil`.

**Défaut de l'automate trouvé et corrigé** : la première passe avec MCEF (PID 6596, 15:35) a échoué sur la réponse au cinquième faux code (« UPDATE LOCKED NONE … blocage 60 s ») : un `UPDATE` de batterie, poussé quand le pourcentage change, a suivi la réponse `LOCKED_OUT` et l'a remplacée dans `PhoneClient.last()` avant que l'automate la lise (le parcours, plus long d'environ 40 s, fait tomber la baisse de batterie à ce moment-là). L'automate juge maintenant sur ce que la réponse a changé (essais restants, blocage), qui reste vrai dans l'`UPDATE` suivant. Aucun défaut du téléphone en cause.

### Limites

- Le fond choisi n'est envoyé qu'avec le code : deux copies du même téléphone neuf (créatif) qui configurent en même temps, la première l'emporte ; l'autre est reverrouillée comme avant (`relockOthers`), son choix de fond est perdu.
- Le tour des langues tourne par `setTimeout` : dans le navigateur de l'application il n'avance que pendant une capture (leçon du 2026-11-20) ; vu en vrai client sur trois captures et la fin du tour, pas en vidéo. Le fondu lui-même n'a pas été regardé image par image.
- Un client en anglais voit l'assistant en français (pages en français en dur, limite déjà écrite) ; le repli suit la langue du client.
- Le serveur dédié et WebDisplays n'ont pas été rejoués avec l'assistant (le mode `webdisplays` de l'automate est adapté, compilé, non lancé).
- L'étape « Carte SIM » du lot T2 n'existe pas encore.
