# Vérifications du lot T2 : réseau, antenne, opérateur et carte SIM

Relevés du 2026-11-20 au 2026-11-25, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 14 (preuve du lot). Les tâches 1 à 13, puis la tâche 13b (icône « Antenne » grisée), ont leurs JUnit, GameTests, essais réels, rapports et revues ; ils sont repris ici d'après ces rapports, les journaux et le code. La tâche 14 a rejoué la suite complète (JUnit et GameTests, sans puis avec le pack) et relu le code et les journaux pour les affirmations sensibles ; **elle n'a lancé aucun client ni aucun serveur dédié**. Cahier : [le téléphone](../superpowers/specs/2026-11-08-telephone-design.md). Plan : [`2026-11-20-lot-t2-telephone-reseau.md`](../superpowers/plans/2026-11-20-lot-t2-telephone-reseau.md) (pas encore archivé : la revue finale du lot reste à faire).

Commits du lot, par tâche :

| Tâche | Objet | Commits (corrections de revue après « puis ») |
|---|---|---|
| 1 | règles pures (signal, consommation, numéros, relais) | `7eb7216` puis `cc4f29c` |
| 2 | registre des opérateurs, des cartes et des antennes | `02325b2` puis `b6f09de` |
| 3 | antenne, fondation, consommation, chunk | `4dc58e8` puis `7619608` |
| 4 | outil de maintenance, droits, protection | `0c3352e` puis `f587fb1` |
| 5 | carte SIM, insertion, jeton | `b0b6be5` puis `bd86f57` |
| 6 | façade du réseau, relais, trafic | `5b894cd` puis `6576558` |
| 7 | téléphone en réseau, côté serveur | `f6b2a7a` puis `2f21be8` |
| 8 | pages et écran de repli | `bef369c` puis `908cd5d`, `adb00f3` |
| 9 | panneau de l'opérateur, tarif au lendemain | `ed49e10` |
| 10 | appli « Antenne » en HTML, panneau dans le repli | `af41a62` puis `302ea13` |
| 11 | gestes du maire (`/operator`) | `97ca368` puis `30302e4` |
| 12 | textures et modèles | `208fdbe` puis `6c8f729` |
| 13 | essais réels | `78004b3`, `394ecbe`, `c69ab9c` puis `214e574` |
| 13b | icône « Antenne » grisée | `ff53009` |

**État : le lot est prouvé dans les limites des sections « Défauts connus » et « Non prouvé et limites ».** Un joueur fonde un opérateur en nommant une antenne ; l'antenne consomme du courant, garde son chunk chargé et tombe hors service sans courant ; le fondateur émet, suspend, réactive et remplace des cartes SIM et fixe le tarif du lendemain, au panneau de l'antenne ou dans l'appli « Antenne » de son téléphone ; une carte glissée dans un téléphone lui donne un numéro, un opérateur et un signal de 0 à 4 barres ; une copie de carte faite avant l'insertion est morte ; l'ingénieur n'a la main qu'avec son outil de maintenance ; le maire plafonne, modifie et supprime. Un vrai client sur serveur dédié a joué le parcours avec le pack, relance comprise, avant les derniers durcissements ; un client avec MCEF et un client sans MCEF ont montré les barres et l'appli. **Aucun appel n'existe encore** (lot T3) et **aucune pièce n'est prélevée** (lot T6). La revue de la dernière tâche (13b) est approuvée ; la revue finale du lot entier n'est pas faite.

## Ce qui passe

### Suite complète de la tâche 14 (2026-11-25, code de `ff53009`)

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test --rerun runGameTestServer` (18:55:22 à 18:57:27) | JUnit : **982 tests, 0 échec, 0 erreur, 0 ignoré** (somme des 105 rapports `build/test-results/test/*.xml`, réécrits par cette passe) ; GameTests : « **All 1165 required tests passed** » (18:57:23, `run-gametest/logs/latest.log`) |
| `./gradlew --offline runGameTestServer -PwithPack` (18:59:49 à 19:02:31) | « **All 1165 required tests passed** » (19:02:27) ; 113 lignes `createaddition` au journal (le pack est chargé) |

Les deux passes ont été jouées sous le verrou de lancement, cédé entre elles à AXIONENGINE. La compilation (`compileJava compileDevcontentJava compileTestJava`) a été faite avant la prise.

### Compteurs tâche par tâche (rapports)

| Après la tâche | JUnit | GameTests (« All N required tests passed », sans puis avec le pack) |
|---|---|---|
| avant le lot | 711 | 1037 (1038 après la correction de la revue de l'assistant, hors lot) |
| 1 | 755 puis 770 | non rejoués (règles pures) |
| 2 | 796 puis 806 | non rejoués (JUnit seulement) |
| 3 | 810 | 1049 puis 1050 |
| 4 | 810 | 1066 puis 1067 |
| 5 | 810 | 1080 puis 1084 |
| 6 | 823 | 1097 |
| 7 | 825 puis 827 | 1112 |
| 8 | 877, 886, 912 | 1112 au premier tour ; non rejoués aux deux tours de corrections (code client seul) |
| 9 | 921 | 1138 |
| 10 | 940 puis 954 | 1150 |
| 11 | 960 | 1158 |
| 12 | 961 puis 970 | 1158 puis 1159 |
| 13 | 971 | 1159 (non rejoués après `214e574`, code d'essai seul) |
| 13b | 982 | 1165 |

Le nombre de GameTests est le même avec et sans le pack : les essais propres au pack passent sans rien faire quand le mod visé est absent. Entre la tâche 1 et ses corrections, la correction de la revue de l'assistant de première configuration (hors lot, `4b3d759`) a ajouté 8 JUnit.

JUnit des paquets du téléphone à la fin : `phone.net` 126 tests, `phone.client` 226 (dont ceux du lot T1), le reste de `phone` 36 (somme des rapports de la passe de la tâche 14).

### GameTests ajoutés par le lot : 127, en 25 lots de 8 au plus

| Classe | Lots (nombre) | Ce qu'ils prouvent côté serveur |
|---|---|---|
| `AntenneGameTests` (12) | `antenne_a` (8), `antenne_chunk` (3), `antenne_chunk_sans` (1) | recette à l'établi seulement, pose puis nom, veille et cartes facturées, hors service sans courant, antenne démontée qui garde son opérateur, aucune inscription fantôme, antenne loin de tous en service, tickets rendus, sans chunk forcé une antenne déchargée est hors tension |
| `AntenneDroitsGameTests` (14) | `outil_maintenance` (6), `antenne_droits` (8) | recette et tag de l'outil, rôle **et** outil, outil volé sans effet, trésor et tableau, sans droit rien ne bouge, faux joueur, piston, eau, hauban, explosion, forme, fondation au nom d'un autre |
| `PackGameTests` (4) | `pack_antenne_deployeur`, `_perceuse`, `_engin`, `_tag` (1 chacun) | déployeur, perceuse et engin de Create, tag `create:non_movable` |
| `CarteSimGameTests` (17) | `sim_a` (8), `sim_copie` (7), `sim_remplace` (2) | émission, insertion, retrait, numéro qui suit la carte, copies et cartes forgées, jeton retiré du paquet, NBT corrompu, remplacement |
| `RelaisGameTests` (13) | `relais_a` (8), `relais_trafic` (5) | signal selon la distance, opérateur et urgences, trajet entre deux opérateurs et raisons d'échec, mode avion, consommation du trafic ouvert, message |
| `TelephoneReseauGameTests` (21) | `telephone_reseau` (8), `telephone_sim` (7), `telephone_icone` (6) | barres, écran verrouillé, mode avion, coupure, gestes de carte, demande, session périmée, protocole 10 refusé, icône « Antenne » vive, grisée ou absente |
| `PanneauOperateurGameTests` (26) | `panneau_a` (8), `panneau_cartes` (8), `panneau_vue` (5), `panneau_tarif` (5) | nom, consommation, caisse, droits, cartes, vue sans jeton, tarif au lendemain sous plafond, horloge qui recule |
| `AppliAntenneGameTests` (12) | `appli_antenne` (4), `appli_antenne_b` (8) | appli périmée, non-fondateur, caisse jamais vidée à distance, fermeture, frein, mort, dimension, pages, antenne principale, chunk non chargé |
| `MaireTelephoneGameTests` (8) | `maire_telephone` (6), `maire_telephone_b` (2) | droits des commandes, tarif, fondateur, suppression (rien d'actif, caisse conservée au cuivre, aucune résurrection), plafond au lendemain, confirmation |

### Mutations (rapports : chacune a rougi le test qui la nomme, puis a été restaurée, sauf mention)

| Tâche | Mutations vues rouges |
|---|---|
| 1 | 7, puis 5 au tour 1 |
| 2 | 10, puis 16 au tour 1 |
| 3 | 7, puis 3 au tour 1 (l'une d'elles d'abord masquée par une autre correction, puis vue rouge seule) |
| 4 | 8, puis 3 au tour 1 ; **une reste verte** (`forceSolidOn` retiré : la forme actuelle suffit contre l'eau) ; aucune mutation ne mord sur le piston ni sur l'engin de Create (la vanilla et la dureté -1 suffisent) |
| 5 | 6, puis 6 au tour 1 |
| 6 | 8 (6 en GameTest, 2 en JUnit), puis 1 au tour 1 et une contre-épreuve |
| 7 | 7, puis 5 au tour 1 (une d'abord verte, l'essai corrigé puis vue rouge) |
| 8 | 7, puis 7 au tour 1, puis 5 au tour 2 |
| 9 | 6 en GameTest et 4 en JUnit (une première mutation JUnit restée verte : l'échelle exacte du texte n'est pas gardée) |
| 10 | 4 en JUnit et la passe rouge des corrections ; 3 à la ronde de corrections |
| 11 | 4 |
| 12 | 3 ; la ronde de corrections a des passes rouges, pas de mutation |
| 13 | aucune mutation du produit ; au durcissement, deux gestes neutralisés ont fait échouer l'essai réel (« sans outil », « copie ») |
| 13b | 5 |

Plusieurs tâches ont écrit du code avant ses essais (3, `replaceCard` à la tâche 5, 11, M1 et M2 à la tâche 12) : la preuve que ces essais mordent vient des mutations ou d'une passe rouge obtenue après coup, pas d'une passe rouge initiale.

## API des tickets de chargement de Forge (tâche 3)

Relevée par `javap` sur `forge-1.20.1-47.4.28_mapped_official_1.20.1.jar` et dans `forge-1.20.1-47.4.28-sources.jar` (cache de Gradle).

| Fait | Niveau |
|---|---|
| `ForgeChunkManager.forceChunk(ServerLevel, String modId, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking)` (surcharges `Entity` et `UUID`). L'ajout passe par `TicketTracker.add` (faux si déjà là), charge le chunk, marque sale `ForcedChunksSavedData` (données `chunks`) et pose un ticket de région de niveau 2 (`forge:block_ticking` si `ticking`) | lu au bytecode et aux sources |
| `setForcedChunkLoadingCallback(String modId, LoadingValidationCallback)` : à appeler depuis `FMLCommonSetupEvent` par `enqueueWork` ; `validateTickets(ServerLevel, TicketHelper)` est appelé au chargement de chaque monde, avant la remise des tickets | lu (Javadoc et sources) |
| `TicketHelper` : constructeur privé ; `getBlockTickets()`, `getEntityTickets()`, `removeAllTickets(BlockPos / UUID)`, `removeTicket(…)` | lu ; **le rappel de démarrage n'a donc pas d'essai unitaire** |
| `TicketOwner` : champs et constructeur privés, aucun accès public au propriétaire : `AntennaChunks.held` lit les tickets dans ce qu'écrit `ForcedChunksSavedData.save` (`ForgeForced[{Mod, ModForced[{Chunk, Blocks, TickingBlocks}]}]`), sans rien écrire sur le disque | lu ; la lecture est exercée par les GameTests |

Schéma retenu : un ticket « qui tique » par antenne, propriétaire = sa position, sur son seul chunk ; revu par l'antenne toutes les 20 tics, rendu à la casse, à la suppression de l'opérateur et au démarrage s'il n'a plus lieu d'être. Au démarrage, le ticket a été vu agir en essai réel (voir plus bas).

## Valeurs livrées (`oneforallsurvival-phone.toml`, fichier SERVER)

Relues dans `PhoneConfig` et dans `run-gametest/world/serverconfig/oneforallsurvival-phone.toml`.

| Clé | Valeur de départ | Sens |
|---|---|---|
| `antennaRange` | 270 | portée en blocs, en ligne droite, même dimension. Barres : 4 sous un quart de la portée (67,5 blocs), 3 sous la moitié (135), 2 sous les trois quarts (202,5), 1 jusqu'à la portée, 0 au-delà |
| `antennaStandbyFe` / `antennaPerSimFe` | 40 / 6 | FE par tic : veille, plus chaque carte **active** de l'opérateur, par antenne |
| `antennaCallFe` / `antennaFacetimeFe` / `antennaMessageFe` | 9 / 24 / 100 | FE par tic d'appel ou de visio ouverte ; FE d'un coup par message |
| `antennaCapacity` / `antennaMaxReceive` | 40 000 / 4 000 | réserve, réception par tic |
| `antennaKeepsChunkLoaded` / `antennaStaleTicks` | vrai / 40 | chunk gardé chargé ; au-delà de 40 tics sans nouvelle, une antenne est hors service |
| `simRequestsMax` | 32 | demandes en attente par opérateur |
| `phoneTariffMax` / `phoneDefaultTariffCap` | 1 000 / 20 | tarif maximal ; plafond du maire au départ (cuivres par jour) |
| `signalRefreshTicks` | 20 | tics entre deux relevés du signal d'un téléphone |

## Essais réels

Tous sous le verrou de lancement ; chaque client a journalisé « [fenetre] second écran (2600, 40) » et « [souris] rendue », aucun « Downloading Chromium » (relevés des tâches). Les clics passent par les méthodes de l'écran, pas par la souris du système.

### Bout en bout sur serveur dédié avec le pack, et relance (tâche 13)

L'étape `TELEPHONE` (`E2eTelephone`, commandes RCON `e2etel` d'`E2eTelephoneServeur`) est jouée par un vrai joueur (`Dev`, ingénieur, en survie et non opérateur) ; la fondatrice de Lagon, les abonnés et l'intrus sont des **joueurs simulés** par le chemin serveur.

| Passage | Heures (2026-11-25) | Résultat lu (rapports) |
|---|---|---|
| E1 + E2 + E4 + E5 + téléphone + E3 + E6, monde neuf (`E2eTresor` modifié à la tâche 4 compris) | 14:33 à 14:46 | TOUT PASSE, 278 PASS ; relance (14:50) : 75 PASS |
| Passage complet, monde neuf | 16:35 à 16:55 | **TOUT PASSE, 473 PASS** |
| Relance complète | 16:55 à 16:59 | **TOUT PASSE, 135 PASS** : la première relance complète qui passe depuis le lot 5 (deux défauts du banc corrigés) |
| Après durcissement des essais (`214e574`) : étape seule, puis sa relance | 17:54 puis 17:59 | TOUT PASSE, 57 puis 12 PASS |
| Étape seule, code final de la ronde | 18:14 à 18:17 | TOUT PASSE, 57 PASS (relu à la tâche 14 dans `run-e2e/logs/latest.log` : 57 lignes PASS, « bilan : TOUT PASSE ») |

Les passages complets et leurs relances **précèdent** le durcissement (`214e574`) et l'icône grisée (`ff53009`).

Relevés de l'étape (premier passage et relance) :

- **Sans outil**, l'ingénieur ne reçoit que « Antenna of Lagon » (client en anglais) ; **avec l'outil** en seconde main, le panneau s'ouvre sans droit sur les cartes, le tarif, la caisse ni le nom ; 41 cartes sur 2 pages.
- Dev pose et nomme « Mairie » d'un vrai clic ; tarif 7 enregistré « pour demain » (tarif du jour 0) ; à la relance, un jour plus tard, tarif 7.
- Demandes de carte faites par la feuille ; « Émettre une SIM sans nom », « Émettre », « Refuser », « Remplacer » (deux piles, une authentique, une morte) et « Désigner » par vrais clics.
- Consommation : panneau `40+12+0` FE/t = console = 40 + 6 × 2 cartes actives = **52 FE/t**.
- Usager à 100 blocs : 3 barres. Source de Lagon coupée : `CALLEE_ANTENNA_DOWN`, « Unreachable · Lagon antenna is down » (serveur en anglais).
- Copie créative de la carte glissée par un autre : `REFUSED` / `SIM_UNKNOWN`, encore après la relance.
- **Alimentation par un fil de Crafts & Additions** (après le durcissement, relevé de 17:51 ; Mairie, source à deux blocs, un bloc d'air entre elles) : sans bobine `enService=false, reserve=0, NO_POWER` ; avec la bobine, `RUNNING`, réserve 18 300 en montée progressive.
- Relance : Mairie et Lagon en service sans joueur à moins de 300 blocs, tickets présents ; `/operator remove Lagon` avec un motif illisible refusé ; puis suppression : 41 cartes désactivées au registre, antenne `STOPPED`, désinscrite, sans ticket ; la fondatrice reçoit l'avis à sa connexion, téléphone `DEACTIVATED`, caisse 0.

La première version de la preuve du fil était vide (source collée à l'antenne : le bloc d'énergie créative remplit ses voisins), trouvée par la revue de la tâche 13 et corrigée (`214e574`).

### Vrai client à deux téléphones, MCEF (tâche 13, mode `signal`, PID 45860)

Un seul client, deux téléphones (A sur Mairie, B sur Lagon) sur serveur dédié. Barres reçues par le client égales à celles du serveur à chaque ouverture :

| Place | A (Mairie) | B (Lagon) |
|---|---|---|
| Mairie + 0 | 4 | 4 |
| Mairie + 100 | 3 | 3 |
| Mairie + 200 | 2 | 1 |
| Mairie + 300 | 0 | 0 |
| Lagon + 0 | 4 | 4 |
| Lagon + 100 | 3 | 3 |
| Lagon + 200 | 1 | 2 |
| Lagon + 300 | 0 | 0 |
| Lagon coupée (Mairie + 0) | 4 | **0** |

Mode avion par un vrai clic (« Mode avion », 0 barre, puis 4) ; carte retirée puis remise par la feuille. Pont : 73 messages, **0 étranger**. Rejoué après durcissement (18:01) : « TOUT PASSE », 21 images (`termine.json` relu à la tâche 14).

Appli « Antenne » (MCEF, PID 27272, rejouée à 18:05, PID 60224) : vue de la page égale à la console ; suspendre puis réactiver par vrais clics ; tarif 21 refusé (plafond 20), 20 accepté au clavier, montants formatés (« À partir de demain : 2 argent ») ; source coupée : « Sans courant » en direct, relais « 2 antennes, 1 en service » ; **page rechargée appli ouverte : le serveur ferme le panneau** ; « Accueil » touché avant la première vue : panneau fermé. Pont : 0 étranger.

### Repli sans MCEF (tâche 13, `runClient2`, PID 16232)

À 100 blocs de Mairie, 3 barres (= serveur) ; Lagon coupée : B 0, A 4 ; mode avion ; carte retirée et remise ; « Antenne » ouvre le **panneau Minecraft** par-dessus le repli, Échap rend le téléphone (session ouverte, panneau fermé au serveur) ; changement de dimension sous le panneau (Nether, par RCON) : session et panneau fermés. « TOUT PASSE », 9 images.

Les tâches 8 et 10 avaient déjà joué les mêmes écrans en solo, avec et sans MCEF, en français (feuille « Carte SIM », file de demandes pleine, carte suspendue et désactivée, appli et panneau du repli).

## Appli « Antenne » en HTML et panneau dans le repli : le contrat tel que livré

**Une vue, un seul jeu de règles.** Le serveur construit `OperatorView` (`OperatorViews.build`) et rejoue tout geste par `OperatorActions.apply`. Trois écrans la montrent : le panneau Minecraft du bloc (`OperatorPanelScreen`, onglets « Réseau », « Cartes », « Tarif et caisse »), l'appli « Antenne » de la page, et le même panneau Minecraft ouvert par « Antenne » dans le repli.

**Réseau.** Protocole `"11"`. Paquets du téléphone inchangés en numéro (26 état, 27 geste), avec une vue réseau et les gestes `INSERT_SIM`, `REMOVE_SIM`, `REQUEST_SIM`, `OPEN_OPERATOR_APP` ; paquets nouveaux **28** `OperatorPanelPacket` (vue), **29** `OperatorActionPacket` (`NAME`, `SET_TARIFF`, `ISSUE`, `REFUSE`, `ISSUE_ANONYMOUS`, `SUSPEND`, `REACTIVATE`, `REPLACE`, `SET_MAIN`, `PAGE`, `OPEN_TILL`, `CLOSE`) et **30** `OperatorNoticePacket` (ligne d'état, ou fermeture par le serveur).

**Page → Java** (relu dans `AntennaBridgeMessages` et l'en-tête de `phone.js`) : `{"a":"insertSim","slot":N}` (entier de 0 à 40), `removeSim`, `requestSim` avec `op` (12 hexadécimaux), `antennaApp` ; `opTariff` avec `value` (0 à 1 000 000) ; `opIssue`, `opRefuse`, `opSuspend`, `opReactivate`, **`opReplace`**, `opMain` avec `id` (UUID canonique en minuscules) ; `opAnonymous` ; `opPage` avec `n` (0 à 63) ; `opClose`. Exactement ces champs, sinon rien ne part. Aucun message pour nommer ni pour ouvrir la caisse.

**Java → page.** `phone.render(view)` avec `view.net = {sim, operator, bars, number, holder, founderApp, founderAppAvailable, choices: [{slot, number, operator, validity}], requestable: [{id, name, bars}]}` ; verrouillé, ni `number` ni `holder`, listes vides, les deux drapeaux faux, `sim` ramené à « aucune », « non reconnue » ou « présente ». `phone.antenna(vue | null)` et **`phone.antennaStatus(texte)`**. La vue de l'appli (`OperatorViewJson`) : `session, name, rights {cards, tariff}, tariff, tariffText, nextTariff, nextTariffText, cap, capText, antennas [{id, dimension, x, y, z, state, standby, clients, traffic, total, feToday, feYesterday, main, overReceive}], cards {page, pages, total, items [{id, number, holder, state}]}, requests [{id, name, at}], relay [{name, antennas, inService, status}]`. Absents par choix : `source`, `pos`, `operatorId`, `till`, les droits `till` et `name`.

**Boîtes de `layout`** : `row-sim`, `sim-choice-<case>`, `sim-remove`, `sim-request`, `request-<id>`, `app-antenna`, `status-bars`, `sim-close` ; `op-close`, `op-tab-network`, `op-tab-cards`, `op-tab-tariff`, `op-tariff-field`, `op-tariff-save`, `op-anonymous`, `op-issue-<id>`, `op-refuse-<id>`, `op-card-<id>`, **`op-replace-<id>`**, **`op-main-<id>`**, `op-page-prev`, `op-page-next` (identifiants de 48 caractères au plus).

**Écarts au contrat du plan** (livrés, notés ici car le plan ne les porte pas tous) : `opReplace` et `op-replace-` (geste « Remplacer la carte ») ; `op-main-` ; `antennaStatus` ; paquet 30 ; `tariffText`, `nextTariffText`, `capText` (montants écrits par le jeu, le champ restant en cuivres) ; boîtes jusqu'à 48 caractères ; `net.founderAppAvailable` ; identifiant d'opérateur de 12 caractères et non 8 ; la caisse voyage en `long` (cuivres) et non en texte formaté par le serveur.

**Bornes** : 32 antennes, cartes par page, demandes et opérateurs au relais ; textes de 32 caractères ; côté téléphone, 41 cartes d'inventaire, 16 opérateurs demandables.

**Sûreté de la page** : tout texte par `textContent`, aucun `eval`, aucune adresse externe, balise CSP inchangée (gardes `WebResourcesTest`). La CSP garde `'unsafe-inline'` : `textContent` est la barrière.

## Outil de maintenance

- Objet `maintenance_tool` (un par pile, sans usure), tag `oneforallsurvival:maintenance_tools`. Recette de table **absente** de `table_only`, donc rangée par `BenchCatalog` à l'établi d'ingénieur (`oneforallsurvival` est un mod géré) ; prouvé par `lOutilNeSeFabriqueQuALEtabli` et `leMaterielDuMetierEstALEtabliSeulement`. Recette livrée : **2 lingots de fer, 1 lingot de cuivre, 1 bâton** (voir « Défauts connus » : le plan prévoyait en plus une redstone).
- L'antenne suit la même voie (1 paratonnerre, 2 lingots de cuivre, 1 bloc de redstone, 2 lingots de fer, 1 barreaux de fer ; `lAntenneNeSeFabriqueQuALEtabli`). La carte SIM n'a aucune recette (`aucuneRecetteNeDonneDeCarte`).
- **Rôle et outil** (`AntennaRules.mayManage`) : fondateur, maire, équipe (niveau 2), ou ingénieur **tenant l'outil dans une main**. Un citoyen qui tient l'outil n'a rien. Un faux joueur n'a jamais rien : le faux joueur du déployeur de Create rend l'UUID de qui l'a posé (lu dans le jar de Create 6.0.8).
- **Trésor et tableau** : `TownBlockRules.engineerMayMove` exige désormais le même outil (point d'extension laissé par E3), la repose du trésor comprise. Brèche trouvée en corrigeant la revue, puis fermée : le déployeur posé par le maire pouvait casser le trésor ou le tableau.
- Droits plus fins : cartes au fondateur seul ; tarif au fondateur, au maire et à l'équipe ; caisse au fondateur et à l'équipe, en personne sur l'antenne principale.
- Protection de l'antenne : dureté -1, résistance maximale, piston bloqué, tags du Wither, du dragon et `create:non_movable`, `forceSolidOn`, bris annulé pour qui n'a pas la main.

## L'icône « Antenne » grisée (tâche 13b, `ff53009`, revue approuvée)

- L'icône se montre au propriétaire d'un téléphone déverrouillé qui fonde un opérateur, **mode avion compris** (avant : masquée en mode avion).
- Elle est **grisée exactement quand le serveur refuserait de l'ouvrir** : une seule condition, `OperatorPanels.appOperator` (déverrouillé, même téléphone en main, propriétaire, hors mode avion, carte `ACTIVE` de **son** opérateur, une barre au moins), lue à la fois par la vue (`canOpenFromPhone` → `net.founderAppAvailable`) et par le refus (`openFromPhone`).
- Toucher l'icône grisée envoie le geste ; le serveur répond « Pas de réseau. ». D'après le code, la page commence à ouvrir l'appli puis la referme (question Q8, en attente).
- Page : classe `off` posée d'après ce seul champ. Repli : le bouton prend l'apparence d'un bouton inactif et reste actif au clic.
- Preuves : 6 GameTests (`telephone_icone`), 5 mutations rouges, clients mode `reseau` sans MCEF (PID 11356, 32 images, 105 pas) et avec MCEF (PID 46056, 20 images, 95 pas) « TOUT PASSE ». La revue (rapport hors dépôt) est rendue **approuvée**, sans défaut critique ni important ; elle juge, par raisonnement sur le code, qu'une mutation du seul côté du refus ferait échouer l'assertion « grisée = refusée » : aucune mutation ne l'a montré.

## Captures regardées

Ouvertes par les tâches (outil de lecture d'images), rangées hors du dépôt (`run*/`) ; chaque passe réécrit son dossier. La tâche 14 en a rouvert trois : `run/frames-reseau-mcef-t13b/r03-accueil-icone-antenne-grisee.png`, `run/frames-reseau-repli-t13b/r03-accueil-icone-antenne-grisee.png` et `run-e2e/captures-t2/t2_mairie_en_jeu.png` (antenne, fil de cuivre tendu jusqu'à la source ; l'outil de maintenance en seconde main y paraît bien grand et sombre).

| Tâche | Où | Ce qu'on y voit |
|---|---|---|
| 8 | navigateur intégré (`#mock`), `run/frames-reseau-mcef*`, `run/frames-reseau-repli*` | barre d'état par état de carte, feuille « Carte SIM », file pleine, repli à 320 × 240 et 960 × 520 ; défauts vus et corrigés : libellés coupés, message de file pleine illisible |
| 10 | `#mock`, `run/frames-antenne-mcef-3/`, `run/frames-antenne-repli-2/` | les trois onglets, ligne d'état, refus du tarif, source coupée en direct, appli fermée par le serveur |
| 12 | `run-demo/visuels-captures/telephone_reseau-*.png` (42 images), `#mock` | antennes de jour et de nuit (seul le voyant en service luit), sous un bloc et contre un mur, outil en main, infobulles de la carte, panneau à 320 × 240 et 640 × 360, repli ; défauts vus et corrigés : voyant invisible de nuit, semelle bruitée, carte coupée sans bulle, outil en main, troisième opérateur caché, haubans |
| 13 | `run-e2e/captures-t2/`, `run/frames-mcef*-signal/`, `…-antenne/`, `run-client2/frames-repli*-signal/` | panneau (demandes, désigner, 320 × 240), barres, « Pas de service », mode avion, appli en direct |
| 13b | `run/frames-reseau-*-t13b/`, `#mock` | icône grisée et vive, page et repli |

## Décisions

### De Killian

| Date | Décision |
|---|---|
| 2026-11-20 | Chunk de l'antenne **gardé chargé** ; chaque antenne paie **6 FE/t par carte active** ; l'outil de maintenance est un **objet nouveau** de l'ingénieur ; l'appli « Antenne » est une **page HTML façon iOS** |
| 2026-11-25 | **Q1 à Q4 : « Oui, garde tout »** : (Q1) l'objet d'antenne d'un opérateur supprimé ne se repose que par les ayants droit de l'ancien fondateur ; (Q2) contour de l'antenne jusqu'aux haubans, collision limitée à la semelle et au mât ; (Q3) voyant d'une antenne en panne éteint comme sans courant ; (Q4) l'outil de maintenance en vue à la première personne reste tel quel (grand et assez sombre) |
| 2026-11-25 | **Q5 : « Attendre le lot T6 »** : aucun dépôt de développement ; le versement d'une caisse non vide s'essaiera en jeu avec les factures |
| 2026-11-25 | **Q6 : « La griser »** : l'icône « Antenne » sous « Pas de service » (tâche 13b) |

**En attente** : **Q7** (ajouter à la base de connaissances la note « le bloc d'énergie créative de Crafts & Additions remplit directement ses voisins ») ; **Q8** (toucher l'icône grisée : l'appli commence à s'ouvrir puis se referme avec « Pas de réseau. » ; retenu : garder ; autre choix : l'alerte seule).

Les « choix de l'auteur du plan (à valider par Killian) » n'ont pas été revalidés avant l'exécution (décision du contrôleur, autonomie demandée).

### Prises pendant l'exécution

| Sujet | Décision |
|---|---|
| Numéros et jetons | `sim_id` et jeton par `UUID.randomUUID()` ; le générateur du jeu ne tire que le numéro (revue de la tâche 2) |
| Copie de carte | Une copie faite avant une insertion est refusée ; de deux piles au jeton courant, la **dernière** glissée prend la ligne. Jamais deux lignes |
| Carte morte en créatif | Limite acceptée (pas de mixin), compensée par « Remplacer la carte » : même numéro, jeton neuf, ancienne pile morte (revue de la tâche 5) ; ce geste sert aussi à une carte perdue ou détruite |
| Écran verrouillé | Opérateur et barres visibles, comme un vrai téléphone ; état de la carte grossier (aucune, non reconnue, présente) ; ni numéro ni titulaire |
| Signal avant l'appelé | `route` vérifie le signal de l'appelant avant le numéro appelé : sans réseau, on n'apprend rien d'un numéro |
| Trafic | Aucun garde-fou au lot T2 : le contrat de fermeture des poignées est écrit pour le lot T3 |
| Ingénieur outillé | Il voit les cartes (numéros, titulaires) et les demandes de l'opérateur au panneau, sans pouvoir y toucher ni voir de bouton de caisse (accepté : ayant droit de l'antenne) |
| Fondation au nom du poseur | Le poseur, le maire et l'équipe seulement ; l'ingénieur, même outillé, non. Un ingénieur outillé qui démonte puis repose une antenne pas encore fondée en devient le poseur (accepté : il fonde alors en son nom) |
| Tarif dans « Abonnement » | Le téléphone affiche « Prélèvement à venir » ; le tarif y viendra au lot T6 |
| Banc d'essai | Relance complète : le client part avec le serveur et attend la console RCON |
| Base de connaissances | La note écrite à la tâche 13 dans `D:\BDC` (commit `863f052` du coffre) est gardée (Killian, 2026-11-25) |

## Ce que T2 prépare (interfaces telles que livrées)

### Pour le lot T3 (appels, messages, urgences)

- `PhoneNetwork.status(porteur, téléphone, modeAvion)` → `NetworkStatus(sim, number, holderName, operatorName, bars, airplane)`.
- `PhoneNetwork.route(serveur, appelant, téléphone, modeAvion, numéro, positionDeLAppelé)` → `Either<RelayPlanner.Route, RelayFailure>` ; `positionDeLAppelé` nulle → `CALLEE_NO_COVERAGE`. `RelayFailure.describe()` ne nomme que l'opérateur (antenne de l'appelé hors tension). Le trajet est figé : le refaire à chaque relevé.
- `PhoneNetwork.lineOf(serveur, numéro)` : la carte (jeton et UUID du titulaire compris : **ne rien sérialiser ni journaliser depuis elle**).
- `PhoneNetwork.emergencyAntenna(serveur, GlobalPos)` : toute antenne en service d'un opérateur existant.
- `PhoneNetwork.openTraffic(serveur, trajet, TrafficKind.CALL | FACETIME)` → `TrafficHandle` (`close()` idempotent) ; `messageSent(serveur, trajet)` : `antennaMessageFe` retiré une fois de la réserve de chaque antenne du trajet dont le chunk est chargé.
- **Reste à T3** : retrouver le joueur qui porte le téléphone appelé, la voix, les sonneries, la coupure à zéro barre, le grésillement à une barre ; **fermer chaque poignée en un seul point** (raccrocher, refus, zéro barre, déconnexion, mort, changement de dimension, téléphone ou carte retiré, arrêt du serveur), avec un GameTest « l'appelant se déconnecte : la consommation revient à la veille » ; **limiter le débit** des appels et des messages par joueur (« Numéro inconnu », « Carte suspendue » et « Opérateur supprimé » se distinguent : un composeur automatique balaierait les numéros) ; et le **blocage croissant** du code, condition d'entrée du lot (`tasks/todo.md`).

### Pour le lot T6 (abonnement)

- `PhoneOperators.cardsOf(opérateur)` (état, titulaire, nul pour une carte sans nom), `activeCards`, le tarif du jour dans `PhoneOperator.tariff()`, la caisse (`till`, `deposit`, `withdraw`, `tillRoom`).
- `OperatorDay`, émetteur de jour `phone` déjà inscrit au prélèvement : **émettre les factures avant `applyNextTariffs`**.
- **Reste à T6** : `DebitPayees.register("phone", …)`, une facture par carte active, carte suspendue à minuit si elle ne paie pas, impayés, taxe (`/operator tax`), `/eco stats`, annulation des factures dues d'un opérateur supprimé, **qui paie une carte sans nom** (question à poser), le tarif sur le téléphone, et décider si les FE d'un message comptent dans la consommation du jour (`messageSent` ne les ajoute pas à `feToday`).

## Défauts trouvés et corrigés pendant le lot

| Défaut | Trouvé par | Correction |
|---|---|---|
| Numéros écrits en chiffres arabes sur une JVM `ar-SA` ou `fa-IR` | revue, tâche 1 | `Locale.ROOT` |
| Carte active possible pour un opérateur supprimé ; jeton prévisible (générateur du jeu) | revue, tâche 2 | refus et désactivation ; `UUID.randomUUID()` |
| Antenne posée avant la fondation inerte pour toujours ; inscription fantôme qui perdait le ticket | revue, tâche 3 | rattachement toutes les 20 tics ; recherche de toutes les inscriptions |
| Le déployeur du maire pouvait casser le trésor et le tableau | revue, tâche 4 | un faux joueur n'a aucun droit sur les blocs de la ville |
| Pièces oubliées par des essais, qui faisaient rougir un autre | tâche 4 | écouteur `PiecesOubliees`, deux essais rangés (un troisième reste, voir plus bas) |
| Un téléphone forgé rendait ou logeait autre chose qu'une carte | revue, tâche 5 | seule une carte valide est rendue ou logée |
| L'écran verrouillé donnait l'état exact de la carte ; une liste trop longue déconnectait le client ; file pleine muette | revue, tâche 7 | vue grossière, bornes, `REQUESTS_FULL` |
| Carte désactivée affichée « non reconnue » ; textes fixes coupés au repli | revue, tâche 8 | table de textes partagée et testée ; retour à la ligne, plancher 0,75 |
| Avis du téléphone gardés au registre jamais remis | tâche 9 | remis à la connexion |
| Session du téléphone qui restait ouverte sous un panneau remplacé ; panneaux fantômes ; montants en cuivres | revue, tâche 10 | fermeture au retrait de l'écran, montants formatés |
| Objet d'antenne d'un opérateur supprimé rattaché à l'opérateur d'un tiers ; motif de suppression vide accepté | tâche 11 et revue | antenne à fonder pour l'ancien fondateur ; `remove_reason_required` |
| Outil en main raté, troisième opérateur caché, perchoir sur un hauban, icône noircie | revue, tâche 12 | modèle et texture, barre de défilement, collision semelle et mât |
| La barre de défilement coupait les numéros à 320 × 240 | relecture, tâche 12 | barre dans la marge |
| La ligne d'état de l'appli retenait les touchers (« Réactiver » sans effet) | essai réel, tâche 13 | `pointer-events: none` |
| Banc : relance complète en échec depuis le lot 5 (couveuse, ragoûts) | tâche 13 | client lancé avec le serveur ; rangement date par date |
| Preuve du fil vide ; contrôles qui passaient sans geste ; droits non rendus après un échec | revue, tâche 13 | essais durcis (`214e574`) |

## Défauts connus, à corriger au passage de nettoyage qui suit

- **Fermeture `PHONE_GONE` avec un autre téléphone en main** (antérieur à la tâche 13b, depuis `f6b2a7a`) : `PhoneService.netView` calcule la ligne de la pile tenue avec la session déverrouillée du premier téléphone ; le paquet `CLOSE` porte alors **le numéro et le titulaire de cet autre téléphone, même verrouillé** (lisible par un client modifié). Trouvé par la revue de la tâche 13b, confirmé à la lecture du code à la tâche 14 (`view`, `netView`, `tick`) ; **non reproduit**.
- **À rejouer** depuis `ff53009` : les modes `antenne` et `signal` de l'automate sur serveur dédié, et le bout en bout (E2E), compilés seulement.
- **Recette de l'outil de maintenance** : le plan prévoyait `[" IR", " CI", "S  "]` (fer, **redstone**, cuivre, bâton) ; la recette livrée est `[" I ", " CI", "S  "]`, sans redstone. Aucun rapport ne signale l'écart ; **à trancher** (constaté à la tâche 14).
- Mineurs de la revue de la tâche 13b : `PhoneViewJsonTest` ne prouve pas que la page lit `founderApp` (« n.founderApp » est un préfixe de « n.founderAppAvailable ») ; au repli, le bouton grisé n'a ni surbrillance au survol ou au clavier ni mention au narrateur ; l'icône n'est pas visitée pour une carte désactivée, un opérateur supprimé, un changement de fondateur ni une autre dimension ; `OperatorPanels.stillValid` redit la règle sans passer par `appOperator`.
- **Fuite de pièces avec le pack**, toujours là : `QuestProgressGameTests.leCreatifNAvanceRien` tue un zombie et se termine dans le même tic ; les pièces naissent au tic suivant et restent au sol (code relu à la tâche 14). La passe avec le pack de la tâche 14 relève **deux** grappes de 3 cuivres expirées après 6 000 tics vécus (« [pieces-oubliees] ») : une près du joueur d'essai `QueteCreatif` (ce test), l'autre près de `QueteZombie` ; que la seconde vienne de `unZombieSauvageAvanceUnZombieDeGenerateurNon`, bâti de la même façon, est une **hypothèse** tirée de la seule proximité. La passe sans le pack n'en relève aucune. Les deux passes montrent aussi deux expirations voulues par leurs essais. Inscrit au `tasks/todo.md`.
- Mineurs de la relecture de la tâche 13 : la source du fil n'est pas toujours dans le même chunk que l'antenne (la Javadoc d'`E2eTelephone.sourceMairie` dit « même chunk » : **faux en général**) ; fenêtre d'échec où le mode du joueur n'est pas rendu ; saut de ligne final perdu.
- Restes de la tâche 4 (relus à la tâche 14, toujours là) : le refus d'un faux joueur n'est pas essayé sur la pose du tableau, sa progression de casse ni les bris annulés ; `lEauNEmportePasLAntenne` ne range pas son essai s'il expire ; une trace de mise au point reste dans `PackGameTests` ; une double négation dans la Javadoc de `TownBlockRules`.

## Non prouvé et limites

### Réseau, antenne et opérateur

- **Aucun appel, aucun message** (lot T3). **Le trafic n'a jamais été porté par un vrai appel** : `openTraffic` et `messageSent` ne sont appelés que par les JUnit et les GameTests. Une poignée oubliée coûterait 9 FE par tic (24 en visio) à chaque antenne du trajet jusqu'au rechargement du monde (calcul : environ 15,5 millions de FE par jour réel pour un appel ; non mesuré).
- **Un générateur hors du chunk de l'antenne n'est pas gardé chargé** : seul le chunk de l'antenne porte le ticket.
- **Coût serveur de nombreux chunks forcés non mesuré** (une antenne = un chunk qui tique en permanence).
- Réglage `antennaKeepsChunkLoaded` coupé, serveur relancé, puis rallumé : les antennes loin des joueurs restent déchargées, donc hors service, jusqu'au passage d'un joueur.
- **Antenne restée posée d'un opérateur supprimé** : elle ne rejoint pas d'elle-même un nouvel opérateur de l'ancien fondateur ; il la refonde au panneau (refusé s'il en a déjà fondé un autre) ou la démonte et la repose. Non essayé en jeu.
- **La caisse d'un opérateur n'est alimentée par rien au lot T2** : le versement au fondateur n'a été vu **qu'à 0** en jeu ; une caisse non vide n'est prouvée qu'en GameTest (`laCaisseVaAuFondateur`). Un versement qui lèverait après avoir payé une partie ferait remettre la caisse entière (duplication théorique ; contrat écrit dans la Javadoc, aucun cas connu).
- Aucune pièce prélevée ; le tarif n'a d'effet que son affichage et ses avis.
- Non prouvé contre l'antenne : `/clone`, copie de chunk, mods du pack autres que Create ; un membre de l'équipe accroupi en créatif peut copier le bloc.
- Non joués dans un vrai client : textes au chat de `/operator`, bouton de confirmation de la suppression, panneau fermé par la suppression vu à l'écran. Les confirmations vivent en mémoire (perdues à l'arrêt).

### Carte SIM et téléphone

- **Deux copies d'un téléphone faites côté serveur partagent la ligne** (même `phone_id` : choix du lot T1, `uneCopieDuTelephoneAvecSaCarte`).
- **Un membre de l'équipe en créatif qui déplace une carte** (seule ou logée) **lui fait perdre son jeton** (`getShareTag` le retire de ce que le client renvoie) : la carte devient morte, « Remplacer la carte » la rend.
- **Le jeton peut voyager vers un client** par des chemins qui n'appellent pas `getShareTag` : carte dans un conteneur porté comme un objet (boîte de Shulker, sac), ou survol du nom d'une pile dans un message. Ce second chemin est vérifié à la tâche 14 par `javap` : `ItemStack.getDisplayName` construit `HoverEvent.ItemStackInfo(this)`, qui copie `getTag()`. Exploiter un jeton demande une pile à NBT libre, et la première insertion le fait tourner : jamais deux lignes.
- **Un téléphone détruit emporte sa carte**, qui reste `ACTIVE`, donc comptée dans la consommation, jusqu'à ce que le fondateur la remplace ou la suspende.
- **Carte sans nom et abonnement** : qui paie ? (question du lot T6).
- Ce qu'un écran verrouillé laisse deviner : une carte suspendue garde son opérateur à 0 barre, une désactivée n'en a plus.
- **Un outil de maintenance perdu ou volé n'a aucun effet sans le rôle, mais rien ne le rend à l'ingénieur** : il en refait un.
- **Outil de maintenance en vue à la première personne : grand et assez sombre** (décision de Killian : le garder).
- L'étape « Carte SIM » de l'assistant de première configuration n'est pas construite.
- Repli : après « Demande envoyée », seul le bandeau le dit ; les listes ne défilent qu'à la molette ; les messages des écrans du lot T1 restent réduits sur une ligne ; le cadre chevauche la barre d'action à 320 × 240 ; la logique JavaScript de la page n'est exécutée par aucun JUnit (textes relus, comportement vu en `#mock` et MCEF) ; largeurs du repli estimées et contrôlées en jeu en français seulement.
- Pages en français en dur (limite du lot T1). Aucune bulle de texte coupé n'a pu être montrée au panneau avec des données réelles.

### Essais réels : ce qu'ils ne prouvent pas

1. **Le fil de Crafts & Additions n'est prouvé que pour l'antenne de Mairie** (sans bobine : hors service, réserve 0 ; avec : en service). Lagon est alimentée par une source collée et ne prouve rien du fil.
2. Après la relance, l'assertion n'exige que « en service » et le ticket : une réserve pleine suffirait environ 38 s à 52 FE/t (calcul). L'alimentation par le fil après redémarrage n'est que vue au journal (réserve 39 948 à 18:00:51), source et antenne alors dans le même chunk.
3. La position de la source par rapport à la frontière de chunk en z dépend du point de départ (au passage de 18:15 : antenne en 640 150 640, chunk (40, 40) ; source en 642 150 638, chunk (40, 39)). La relance n'a pas été jouée avec la source dans le chunk voisin.
4. Clics par les méthodes de l'écran, pas par la souris du système ; « Accueil » avant la première vue par deux clics JavaScript (ouverture et fermeture prouvées par le pont). Pas de rouge joué pour ce contrôle ni pour celui du tarif.
5. Joueurs simulés (demande de carte, carte ou copie glissée, intrus) par le chemin serveur, sans vrai client ; l'appel s'arrête à `PhoneNetwork.route`. **Aucun essai à deux vrais clients en même temps.**
6. La caisse au fondateur n'a jamais été vue avec de l'argent : 0 avant, 0 en attente, 0 porté ne prouvent aucun transfert.
7. Suppression d'opérateur : les cartes sont relues au registre, mais un seul téléphone (celui de la fondatrice) l'est.
8. Le ticket au démarrage n'est prouvé que par Lagon : le fil de C&A, ou l'addon du pack qui force les chunks des connecteurs, peut tenir celui de Mairie.
9. Appli « Antenne » : la vue est comparée côté Java ; le texte affiché, ligne d'état comprise, n'est contrôlé que sur captures (le refus de tarif est prouvé par le message de barre d'action).
10. Repli : relevé à 100 blocs de Mairie seulement ; les assertions de tarif durcies n'ont pas été rejouées en repli ni en solo.
11. Panneau à 320 × 240 : un bouton ou un cadre hors limites fait échouer ; les textes coupés sont comptés, non assertés. La bulle du repli posée au rendu n'a ni essai ni capture au survol.
12. La ligne d'état de l'appli laisse passer les touchers : les boutons cachés dessous restent atteignables (« Remplacer » n'a pas de confirmation).
13. Relance complète : marge de la couveuse du lot 5 de 8 à 12 s (relevé de la revue de la tâche 13).
14. Le passage complet et sa relance n'ont pas été rejoués après les durcissements (473 et 135 PASS sur le code d'avant) ; la relance de l'étape seule a été jouée à 17:59, avant la dernière retouche de `rangerAvantArret`.
15. Après un échec de l'étape : inventaire et position non rendus ; rien n'est rangé si le client est déconnecté (`Dev` peut rester non opérateur) ; mode de jeu non rendu si l'échec tombe avant le passage en survie ; la difficulté rendue est ensuite remise à « peaceful » par le rangement commun.
16. Non joués : écran de mort réel, autre dimension que le Nether.
17. L'absence du code d'essai dans le jar livré n'est vue que sur le jar du 2026-11-14 (aucune classe `dev/`) et dans `build.gradle` ; **le jar n'a pas été reconstruit**.

Et aussi : le passage « vive ↔ grisée » écran d'accueil ouvert n'a pas été filmé en jeu (GameTests) ; la fermeture par batterie vide et les chemins d'échec du lot T1 restent comme au lot T1.

### Hérité et inchangé

- **Mac à puce Apple : toujours non vérifié.** Premier lancement chez un joueur neuf (téléchargement de Chromium, antivirus), autres cartes graphiques, performance sur une machine moins puissante : non vérifiés (voir [le lot T1](lot-t1.md)). **Jar de production jamais lancé.**
- Condition du lot T3 toujours ouverte : blocage croissant du code.
- `unZombieNoyeGardeSaMarque` : corrigé par `setPersistenceRequired` à la tâche 6 ; la cause (retrait au loin) reste une forte présomption, sans preuve directe.

## Pour le pack

- **Client et serveur se livrent ensemble, depuis le même commit (`ff53009` ou après).** Le protocole est `"11"` (un client du protocole `"10"` est refusé, `unClientDuProtocole10EstRefuse`), mais le paquet d'état a gagné un champ à la tâche 13b **sans nouvelle montée** : un jar T2 antérieur serait accepté à la connexion puis lirait mal les états. Aucun jar du lot T2 n'a été livré.
- Réglages du serveur : `oneforallsurvival-phone.toml` (voir « Valeurs livrées »). Garder les générateurs dans le chunk de leur antenne, ou chargés autrement.
- MCEF et WebDisplays : rien de nouveau par rapport au lot T1 (décision WebDisplays sur le serveur de production toujours en attente).

## Revues

Chaque tâche a eu sa revue par un relecteur distinct, en lecture seule. Tâches 1 à 8 : corrections demandées, faites dans un tour (re-revue ciblée à la tâche 4 ; la tâche 5 relue avec la tâche 9). Tâche 9 : approuvée. Tâche 10 : approuvée, mineurs corrigés sans nouvelle revue. Tâche 11 : approuvée. Tâche 12 : à corriger, puis relecture à corriger (barre de défilement), corrigée à la tâche 13. Tâche 13 : à corriger (preuve du fil), corrigée, relecture **approuvée**. Tâche 13b : **approuvée**. **La revue finale du lot reste à faire**, avec le passage de nettoyage et l'archivage du plan.
