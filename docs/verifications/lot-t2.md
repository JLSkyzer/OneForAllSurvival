# Vérifications du lot T2 : réseau, antenne, opérateur et carte SIM

Relevés du 2026-11-20 au 2026-11-25, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 14 (preuve du lot). Les tâches 1 à 13, puis la tâche 13b (icône « Antenne » grisée), ont leurs JUnit, GameTests, essais réels, rapports et revues ; ils sont repris ici d'après ces rapports, les journaux et le code. La tâche 14 a rejoué la suite complète (JUnit et GameTests, sans puis avec le pack) et relu le code et les journaux pour les affirmations sensibles ; **elle n'a lancé aucun client ni aucun serveur dédié**. La revue finale du lot a suivi, puis un **passage de corrections et de nettoyage** (le même jour), repris ici d'après les journaux de ses passes. Cahier : [le téléphone](../superpowers/specs/2026-11-08-telephone-design.md). Plan : [`2026-11-20-lot-t2-telephone-reseau.md`](../superpowers/plans/archive/2026-11-20-lot-t2-telephone-reseau.md) (pas encore archivé : la relecture ciblée du passage de nettoyage reste à faire).

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
| nettoyage | corrections de la revue finale (I1 à I3, mineurs, recette, essais) | `92cb95b`, `2cd3797`, `b395a22`, `2f834d4`, `3834b36`, `bd6fabe`, `0f395fb`, puis les réglages de l'automate et ce document |

**État : le lot est prouvé dans les limites des sections « Défauts connus » et « Non prouvé et limites ».** Un joueur fonde un opérateur en nommant une antenne ; l'antenne consomme du courant, garde son chunk chargé et tombe hors service sans courant ; le fondateur émet, suspend, réactive et remplace des cartes SIM et fixe le tarif du lendemain, au panneau de l'antenne ou dans l'appli « Antenne » de son téléphone ; une carte glissée dans un téléphone lui donne un numéro, un opérateur et un signal de 0 à 4 barres ; une copie de carte faite avant l'insertion est morte ; l'ingénieur n'a la main qu'avec son outil de maintenance ; le maire plafonne, modifie et supprime. Après le nettoyage, un vrai client sur serveur dédié avec le pack a joué le parcours du téléphone et sa relance (étape seule), quatre passages complets ont abouti sur sept (les trois échecs sont dans des étapes de lots plus anciens), et les modes `signal` et `antenne` ont passé avec MCEF. **La relance complète n'aboutit pas avec les derniers réglages de l'automate** : celle de la nuit s'est arrêtée sur la couveuse du lot 5, avant la partie téléphone. **Aucun appel n'existe encore** (lot T3) et **aucune pièce n'est prélevée** (lot T6). La revue finale du lot est faite (à corriger, corrigée) ; la relecture ciblée de ces corrections aussi (code approuvé, texte et mineurs repris).

## Ce qui passe

### Suite complète de la tâche 14 (2026-11-25, code de `ff53009`)

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test --rerun runGameTestServer` (18:55:22 à 18:57:27) | JUnit : **982 tests, 0 échec, 0 erreur, 0 ignoré** (somme des 105 rapports `build/test-results/test/*.xml`, réécrits par cette passe) ; GameTests : « **All 1165 required tests passed** » (18:57:23, `run-gametest/logs/latest.log`) |
| `./gradlew --offline runGameTestServer -PwithPack` (18:59:49 à 19:02:31) | « **All 1165 required tests passed** » (19:02:27) ; 113 lignes `createaddition` au journal (le pack est chargé) |

Les deux passes ont été jouées sous le verrou de lancement, cédé entre elles à AXIONENGINE. La compilation (`compileJava compileDevcontentJava compileTestJava`) a été faite avant la prise.

### Suite complète du passage de nettoyage (2026-11-25)

| Commande | Résultat lu |
|---|---|
| `compileJava compileDevcontentJava compileTestJava test` (19:30:25 à 19:30:59) | JUnit : « BUILD SUCCESSFUL ». **984 tests** d'après la passe rouge jouée juste avant sur les mêmes essais (19:26:55 : « 984 tests completed, 2 failed ») ; la somme des XML de la passe verte n'a pas été relevée (rapports réécrits à 19:35 par une mutation jouée sur une seule classe) |
| `runGameTestServer` (19:31 à 19:32:17) | « **All 1173 required tests passed** » |
| `runGameTestServer -PwithPack` (19:32:53 à 19:34:29) | « **All 1173 required tests passed** » ; 113 lignes `createaddition` ; les seules pièces expirées sont les deux voulues par leurs essais (aucun cuivre oublié) |

Juste après ces passes, dans `src/main`, seule une espace a été ajoutée dans une Javadoc (`TownBlockRules`, 19:41) ; les mutations ont été retirées par recopie des fichiers sauvegardés ; l'automate de bout en bout a changé (`E2eProtection`, `E2eElectricite`, `E2eTelephone`). La prise à travers son mur (`be68d64`, `05d80e3`) a ensuite changé `src/main` et `build.gradle` : ses passes sont dans sa section.

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
| nettoyage | 984 | 1173 |

Le nombre de GameTests est le même avec et sans le pack : les essais propres au pack passent sans rien faire quand le mod visé est absent. Entre la tâche 1 et ses corrections, la correction de la revue de l'assistant de première configuration (hors lot, `4b3d759`) a ajouté 8 JUnit.

JUnit des paquets du téléphone à la fin de la tâche 14 : `phone.net` 126 tests, `phone.client` 226 (dont ceux du lot T1), le reste de `phone` 36 (somme des rapports de la passe de la tâche 14).

### GameTests ajoutés par le lot : 135, en 29 lots de 8 au plus (127 en 25 lots avant le passage de nettoyage)

| Classe | Lots (nombre) | Ce qu'ils prouvent côté serveur |
|---|---|---|
| `AntenneGameTests` (12) | `antenne_a` (8), `antenne_chunk` (3), `antenne_chunk_sans` (1) | recette à l'établi seulement, pose puis nom, veille et cartes facturées, hors service sans courant, antenne démontée qui garde son opérateur, aucune inscription fantôme, antenne loin de tous en service, tickets rendus, sans chunk forcé une antenne déchargée est hors tension |
| `AntenneDroitsGameTests` (14) | `outil_maintenance` (6), `antenne_droits` (8) | recette et tag de l'outil, rôle **et** outil, outil volé sans effet, trésor et tableau, sans droit rien ne bouge, faux joueur, piston, eau, hauban, explosion, forme, fondation au nom d'un autre |
| `PackGameTests` (4) | `pack_antenne_deployeur`, `_perceuse`, `_engin`, `_tag` (1 chacun) | déployeur, perceuse et engin de Create, tag `create:non_movable` |
| `CarteSimGameTests` (17) | `sim_a` (8), `sim_copie` (7), `sim_remplace` (2) | émission, insertion, retrait, numéro qui suit la carte, copies et cartes forgées, jeton retiré du paquet, NBT corrompu, remplacement |
| `RelaisGameTests` (13) | `relais_a` (8), `relais_trafic` (5) | signal selon la distance, opérateur et urgences, trajet entre deux opérateurs et raisons d'échec, mode avion, consommation du trafic ouvert, message |
| `TelephoneReseauGameTests` (25) | `telephone_reseau` (8), `telephone_sim` (7), `telephone_icone` (7), `telephone_fuite` (3) | barres, écran verrouillé, mode avion, coupure, gestes de carte, demande, session périmée, protocole 10 refusé, icône « Antenne » vive, grisée ou absente, et qui suit un changement de fondateur ; fermeture sans rien d'un autre téléphone tenu (`PHONE_GONE`, ouverture d'un téléphone vide, effacement) |
| `PanneauOperateurGameTests` (30) | `panneau_a` (8), `panneau_cartes` (8), `panneau_vue` (5), `panneau_tarif` (5), `panneau_nom` (2), `panneau_plafond` (1), `panneau_arret` (1) | nom, consommation, caisse, droits, cartes, vue sans jeton, tarif au lendemain sous plafond, horloge qui recule ; champ du nom après un déplacement et après un changement de fondateur ; plafond de cartes ; panneaux oubliés à l'arrêt |
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
| nettoyage | passe rouge d'abord : **8 GameTests** (3 de la fuite, 2 du nom, plafond, arrêt, recette) et **2 JUnit** (plafond, tarif ramené) rouges sur le code d'avant, eux seuls ; puis 5 mutations rouges : icône au changement de fondateur, pose et bris du tableau par un faux joueur (deux), message du plafond au panneau, lecture de `n.founderApp` par la page (que l'ancien essai laissait passer) ; l'essai de l'icône rougit dès son témoin |

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
| `simCardsMax` | 256 (de 1 à 2 048) | cartes non désactivées (actives ou suspendues) par opérateur ; au-delà, plus aucune émission (le plafond est définitif : aucun geste ne désactive une carte seule, seule la suppression de l'opérateur), « Remplacer la carte » reste possible (passage de nettoyage) |
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

Les passages complets et leurs relances de ce tableau **précèdent** le durcissement (`214e574`) et l'icône grisée (`ff53009`) ; ceux qui les suivent sont ci-dessous.

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

### Après le passage de nettoyage (code du mod de `0f395fb`, 2026-11-25)

Serveur dédié avec le pack, monde neuf à chaque premier passage. Chaque client a journalisé « [fenetre] second écran (2600, 40) » et « [souris] rendue ». Les réglages de l'automate ont changé en cours de série (couveuse à partir de 20:20, aire de l'électricité à partir de 20:57).

| Passage | Heures | Résultat lu |
|---|---|---|
| Passage complet | 19:44 à 20:04 | **TOUT PASSE, 474 PASS** |
| Relance complète | 20:07 à 20:09 | 1 échec : couveuse (lot 5), cause établie et corrigée (automate) |
| Modes `signal` puis `antenne`, MCEF (PID 34668 puis 30548) | 20:12 à 20:17 | **TOUT PASSE** les deux (21 images et 158 pas ; 10 images et 28 pas) ; pont : 74 puis 19 messages, 0 étranger ; 0 « Downloading Chromium » |
| Passage complet | 20:20 à 20:26 | 1 échec : détecteur (lot 7), instabilité |
| Passage complet | 20:26 à 20:48 | **TOUT PASSE, 474 PASS** |
| Relance complète | 20:50 à 20:53 | 1 échec : minuit de l'électricité (E6), cause établie et corrigée (automate) |
| Passage complet | 20:57 à 21:08 | 1 échec : zombie du butin (E4), instabilité |
| Passage complet | 21:10 à 21:17 | 1 échec : cache enterrée (lot 7), instabilité |
| **Étape du téléphone seule** | 21:19 à 21:22 | **TOUT PASSE, 57 PASS** |
| **Relance de l'étape seule** | 21:22 à 21:23 | **TOUT PASSE, 12 PASS** |
| Passage complet | 21:23 à 21:43 | **TOUT PASSE, 474 PASS** |
| Relance complète | 21:43 à 21:45 | **sans résultat** : arrêtée à la demande de Killian (pause), 63 PASS avant l'arrêt ; le « FAIL … `mc.player` is null » vient de l'arrêt du serveur par RCON (21:45:33), pas d'un défaut |
| Passage complet (avec la prise à travers le mur, `be68d64`) | 23:47 à 00:08 | **TOUT PASSE, 474 PASS** (320 avant minuit et 154 après, le journal du client tourne à minuit) |
| Relance complète | 00:10 à 00:13 | 1 échec : couveuse (lot 5), 6 PASS avant ; la suite, dont le téléphone, n'a pas été jouée |

Dans les quatre passages complets aboutis, l'étape du téléphone a passé, source de Mairie dans le chunk de son antenne (asserté). **La partie téléphone de la relance n'a passé que dans l'étape seule** : aucune relance complète n'est arrivée jusqu'à elle après le nettoyage (la dernière s'est arrêtée à la couveuse).

#### Étapes d'anciens lots tombées en échec

Fréquences relevées sur les journaux de bout en bout du jour (`run-e2e/logs`, 13:47 à 21:45), passages arrivés jusqu'à l'étape. Aucune attente n'a été affaiblie.

| Heure | Message exact (journal du client, `[e2e] FAIL`) | Fréquence | Cause |
|---|---|---|---|
| 20:26:10 | « Le détecteur en main à 20 blocs de la cache : intervalle moyen 39,2 ticks pour 34 attendus a 20,0 blocs (34 34 34 128 0 0 ) » | 1 sur 10 | **non établie** : 128 ticks sans bip puis des bips relevés au même instant ; aucun « Can't keep up » au serveur (blocage passager du client : hypothèse) |
| 21:08:21 | « Dev tue le zombie A à l'épée (vrais coups) : mob Bete[uuid=46dc37a0-1ced-4017-8221-9569134b4133, type=zombie, sauvage=true, origine=NATURAL, bebe=false, garde=false, amour=false, vivant=true, x=89.5, y=150.0, z=596.5], 42 coup(s) » | 1 sur 8 (3 coups suffisent dans les passages réussis relus) | **non établie** : rien au journal du serveur |
| 21:17:03 | « Le joueur casse la cache à la main (survie) et ramasse son butin : bloc Block{oneforallsurvival:buried_cache} apres 30,0 s » | 1 sur 9 (3,3 s d'ordinaire, 8,6 s une fois) | **non établie** |
| 16:05:59 (tâche 13) | « Origines du monde neuf : un animal apparu à la génération des chunks est marqué sauvage : ORIGINES animaux=0 […] » | 1 sur 10 | la graine du monde : aucun animal né à la génération (déjà vu au lot E6) |
| 15:49:57 et 20:09:41 | « Relance : le compteur de coupure a été relu, l'œuf est toujours là : case 0 0 air, compteur 0 (avant l'arret 300 puis 301) […] » | 2 sur 4 avant ce réglage (la première avant le correctif de la tâche 13) ; puis passé à 20:50 et 21:44, et échec à 00:13 (ligne suivante) | **établie** : le compteur de la couveuse, dans les chunks de l'apparition, repart au démarrage ; menu ouvert 46 s après « Done » à 20:09 pour 45 s de marge. La coupure du premier passage s'arrête à 100 tics au lieu de 300 (55 s de marge) ; l'assertion de la relance est inchangée |
| 00:13:33 (26/11) | « Relance : le compteur de coupure a été relu, l'œuf est toujours là : case 0 0 air, compteur 0 (avant l'arret 101), seuil 1200, etat STOPPED » | 2 sur 4 depuis le début du nettoyage, 1 sur 3 depuis le réglage | **établie**, même cause : menu ouvert environ 56 s après « Done » (compilation et démarrage du client) pour 55 s de marge ; le contrôle tombe à 80 s, après 25 s d'attente. **Non corrigée** : il faudrait relire la couveuse par la console au démarrage, pas par le menu du client |
| 20:53:37 | « Relance : à minuit (au démarrage), deux jours clos ; l'aire n'a jamais été chargée depuis : […] {charge=true, joueurs=1} » | 1 sur 4 | **établie** par `data/chunks.dat` : un fil de l'aire traversait une frontière de chunk en z, l'addon du pack forçait ses deux chunks et le compteur, dans le chunk voisin, était chargé. Origine alignée aussi en z ; correction éprouvée par la relance du 27/11 (« Reprise des restes ») |

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

- Objet `maintenance_tool` (un par pile, sans usure), tag `oneforallsurvival:maintenance_tools`. Recette de table **absente** de `table_only`, donc rangée par `BenchCatalog` à l'établi d'ingénieur (`oneforallsurvival` est un mod géré) ; prouvé par `lOutilNeSeFabriqueQuALEtabli` et `leMaterielDuMetierEstALEtabliSeulement`. Recette : celle du plan, `[" IR", " CI", "S  "]`, soit **2 lingots de fer, 1 redstone, 1 lingot de cuivre, 1 bâton** (rétablie au passage de nettoyage ; la tâche 4 l'avait livrée sans redstone). `leMaterielDuMetierEstALEtabliSeulement` compte les 5 ingrédients, et chacun (vu rouge sur l'ancienne recette). Aucune autre recette du mod n'a cette forme, et elle ne vit qu'à l'établi : aucun conflit possible à la table.
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

**En attente** : **Q7** (ajouter à la base de connaissances la note « le bloc d'énergie créative de Crafts & Additions remplit directement ses voisins ») ; **Q8** (toucher l'icône grisée : l'appli commence à s'ouvrir puis se referme avec « Pas de réseau. » ; retenu : garder ; autre choix : l'alerte seule) ; **Q9** (recette de l'outil de maintenance ; retenu et fait : celle du plan, avec redstone ; autre choix : sans redstone) ; **Q10** (cartes non désactivées par opérateur ; retenu et fait : 256, réglable ; autre choix : plus ou moins) ; **Q11** (numéro et titulaire diffusés aux clients voisins ; retenu : trancher avant le lot T3, rien changé ; autre choix : les retirer dès maintenant).

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
| Pièces oubliées par des essais, qui faisaient rougir un autre | tâche 4 | écouteur `PiecesOubliees`, deux essais rangés (le troisième a été corrigé au nettoyage) |
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
| La fermeture d'une session envoyait la ligne (numéro, titulaire, vue propriétaire) et la batterie d'un **autre** téléphone tenu : `PHONE_GONE`, ouverture d'un autre téléphone vide, `/phone reset` après un changement de case | revue 13b, revue finale (I1) | vue réseau vide et batterie 0 si la pile n'est pas le téléphone de la session (`92cb95b`) |
| Champ du nom jugé sur le poseur et non sur `idleOwner()` comme la fondation | revue finale (I2) | `2cd3797` |
| Émission de cartes sans borne (registre sans fin, recomptage à chaque tic) | revue finale (I3) | `simCardsMax`, 256 par défaut (`2f834d4`) |
| Panneaux non oubliés à l'arrêt du serveur ; `phoneTariffMax` abaissé sans effet sur un tarif en vigueur ; `stillValid` qui redisait la règle de l'appli ; deux Javadoc fausses | revue finale (m3 à m5), revue 13b (mineur 4) | `b395a22`, `2f834d4` |
| Recette de l'outil de maintenance sans la redstone du plan | tâche 14 | recette du plan (`3834b36`) |
| Pièces de deux essais de quêtes expirées dans la fenêtre d'un autre (avec le pack) ; faux joueur non essayé sur le tableau ; essai expiré non rangé ; trace de mise au point | revue de la tâche 4 (m-A à m-E) | `bd6fabe`, `3834b36` |
| L'essai de lecture des clés par la page ne prouvait rien pour `founderApp` | revue 13b (mineur 1) | `0f395fb` |
| Banc : source d'une antenne parfois hors de son chunk ; mode de jeu non rendu après un échec précoce de l'étape | relecture, tâche 13 (N1, N3) | origine alignée en x et en z et assertée ; rangement armé dès le passage en spectateur (non provoqué par un échec) |
| Banc : couveuse du lot 5 et minuit de l'électricité en échec à la relance | passes du nettoyage | coupure arrêtée à 100 tics ; aire alignée en z (voir « Essais réels ») |

## Défauts connus

Les défauts de la revue finale, la recette, la fuite de pièces et les mineurs repris sont corrigés (tableau précédent). Restent, laissés par choix :

- Repli : le bouton « Antenne » grisé n'a ni surbrillance au survol ou au clavier ni mention au narrateur (revue 13b, mineur 2).
- Automate : le pas « sans fil » de l'étape du téléphone est sans effet sur la preuve (relecture 13, N2) ; les assertions de tarif durcies ne sont pas rejouées en repli ni en solo (N5).
- Les pages gardent `'unsafe-inline'` dans leur CSP (`textContent` est la barrière) ; au repli, le cadre chevauche la barre d'action à 320 × 240.
- Historique : le commit `b395a22` ne compile pas seul (ses essais appellent un réglage ajouté au commit suivant, `2f834d4`).
- Les limites m1, m2, m6, m7 et m8 de la revue finale sont écrites ci-dessous.

## Non prouvé et limites

### Réseau, antenne et opérateur

- **Aucun appel, aucun message** (lot T3). **Le trafic n'a jamais été porté par un vrai appel** : `openTraffic` et `messageSent` ne sont appelés que par les JUnit et les GameTests. Une poignée oubliée coûterait 9 FE par tic (24 en visio) à chaque antenne du trajet jusqu'au rechargement du monde (calcul : environ 15,5 millions de FE par jour réel pour un appel ; non mesuré).
- **Un générateur hors du chunk de l'antenne n'est pas gardé chargé** : seul le chunk de l'antenne porte le ticket.
- **Coût serveur de nombreux chunks forcés non mesuré** (une antenne = un chunk qui tique en permanence).
- Réglage `antennaKeepsChunkLoaded` coupé, serveur relancé, puis rallumé : les antennes loin des joueurs restent déchargées, donc hors service, jusqu'au passage d'un joueur.
- **Antenne restée posée d'un opérateur supprimé** : elle ne rejoint pas d'elle-même un nouvel opérateur de l'ancien fondateur (le fondateur au moment de la suppression) ; il la refonde au panneau (refusé s'il en a déjà fondé un autre) ou la démonte et la repose, même si un ingénieur l'avait déplacée entre-temps (GameTests du lot `panneau_nom`). Avant le passage de nettoyage, le champ du nom suivait le poseur et cette phrase était fausse dans ces deux cas (revue finale, I2). Non essayé en jeu.
- **Numéro et titulaire diffusés aux clients voisins** (revue finale, m1) : `getShareTag` du téléphone et de la carte ne retire que le jeton ; un client modifié relie nom et numéro de qui tient son téléphone. À trancher avant le lot T3 (question Q11).
- **Tickets du mod rendus au démarrage** (m2) : `AntennaChunks.validate` rend tout ticket du mod qui n'est pas une antenne ; aujourd'hui l'antenne est seule à en poser, un futur bloc perdrait les siens.
- **Listes tronquées sans le dire** (m6) : la vue ne montre que 32 demandes (alors que `simRequestsMax` va jusqu'à 10 000) et 32 opérateurs au relais.
- **Chunks forcés sans borne** (m7) : une antenne inscrite tient son chunk même sans courant ; seul le coût de fabrication freine leur nombre.
- **Noms d'opérateur** (m8) : les caractères de format (U+202E…) ne sont pas refusés ; un nom peut s'afficher retourné ou en imiter un autre (hérité d'E6).
- **La caisse d'un opérateur n'est alimentée par rien au lot T2** : le versement au fondateur n'a été vu **qu'à 0** en jeu ; une caisse non vide n'est prouvée qu'en GameTest (`laCaisseVaAuFondateur`). Un versement qui lèverait après avoir payé une partie ferait remettre la caisse entière (duplication théorique ; contrat écrit dans la Javadoc, aucun cas connu).
- Aucune pièce prélevée ; le tarif n'a d'effet que son affichage et ses avis.
- Non prouvé contre l'antenne : `/clone`, copie de chunk, mods du pack autres que Create ; un membre de l'équipe accroupi en créatif peut copier le bloc.
- Non joués dans un vrai client : textes au chat de `/operator`, bouton de confirmation de la suppression, panneau fermé par la suppression vu à l'écran. Les confirmations vivent en mémoire (perdues à l'arrêt).

### Carte SIM et téléphone

- **Deux copies d'un téléphone faites côté serveur partagent la ligne** (même `phone_id` : choix du lot T1, `uneCopieDuTelephoneAvecSaCarte`).
- **Un membre de l'équipe en créatif qui déplace une carte** (seule ou logée) **lui fait perdre son jeton** (`getShareTag` le retire de ce que le client renvoie) : la carte devient morte, « Remplacer la carte » la rend.
- **Le jeton peut voyager vers un client** par des chemins qui n'appellent pas `getShareTag` : carte dans un conteneur porté comme un objet (boîte de Shulker, sac), ou posée dans une machine de Create (déployeur, dépôt, tapis : `DeployerBlockEntity.write` envoie sa pile entière, lu par `javap` à la revue de la tâche 15). Le survol du nom d'une pile dans un message (`ItemStack.getDisplayName` construit `HoverEvent.ItemStackInfo(this)`, qui copie `getTag()`) est fermé à la tâche 15 (`HoverEventItemStackInfoMixin`, voir plus bas). Exploiter un jeton demande une pile à NBT libre, et la première insertion le fait tourner : jamais deux lignes.
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
3. Depuis le nettoyage, chaque source est dans le chunk de son antenne (origine alignée en x et en z, assertée) : l'alimentation par un fil qui traverse une frontière de chunk, source dans un chunk voisin, n'est pas jouée.
4. Clics par les méthodes de l'écran, pas par la souris du système ; « Accueil » avant la première vue par deux clics JavaScript (ouverture et fermeture prouvées par le pont). Pas de rouge joué pour ce contrôle ni pour celui du tarif.
5. Joueurs simulés (demande de carte, carte ou copie glissée, intrus) par le chemin serveur, sans vrai client ; l'appel s'arrête à `PhoneNetwork.route`. **Aucun essai à deux vrais clients en même temps.**
6. La caisse au fondateur n'a jamais été vue avec de l'argent : 0 avant, 0 en attente, 0 porté ne prouvent aucun transfert.
7. Suppression d'opérateur : les cartes sont relues au registre, mais un seul téléphone (celui de la fondatrice) l'est.
8. Le ticket au démarrage n'est prouvé que par Lagon : le fil de C&A, ou l'addon du pack qui force les chunks des connecteurs, peut tenir celui de Mairie.
9. Appli « Antenne » : la vue est comparée côté Java ; le texte affiché, ligne d'état comprise, n'est contrôlé que sur captures (le refus de tarif est prouvé par le message de barre d'action).
10. Repli : relevé à 100 blocs de Mairie seulement ; les assertions de tarif durcies n'ont pas été rejouées en repli ni en solo.
11. Panneau à 320 × 240 : un bouton ou un cadre hors limites fait échouer ; les textes coupés sont comptés, non assertés. La bulle du repli posée au rendu n'a ni essai ni capture au survol.
12. La ligne d'état de l'appli laisse passer les touchers : les boutons cachés dessous restent atteignables (« Remplacer » n'a pas de confirmation).
13. Couveuse du lot 5 à la relance : **corrigée** (`07d9874`), voir « Reprise des restes ».
14. La relance complète **aboutit** (27/11, « Reprise des restes »). La cache enterrée, le détecteur et le zombie restent instables (tableau des « Essais réels »).
15. Après un échec de l'étape : inventaire et position non rendus ; rien n'est rangé si le client est déconnecté (`Dev` peut rester non opérateur) ; la difficulté rendue est ensuite remise à « peaceful » par le rangement commun.
16. Non joués : écran de mort réel, autre dimension que le Nether.
17. L'absence du code d'essai dans le jar livré n'est vue que sur le jar du 2026-11-14 (aucune classe `dev/`) et dans `build.gradle` ; jar reconstruit et contrôlé sans être lancé le 26/11 (« Reprise des restes »).

Et aussi : le passage « vive ↔ grisée » écran d'accueil ouvert n'a pas été filmé en jeu (GameTests) ; la fermeture par batterie vide et les chemins d'échec du lot T1 restent comme au lot T1.

### Hérité et inchangé

- **Mac à puce Apple : toujours non vérifié.** Premier lancement chez un joueur neuf (téléchargement de Chromium, antivirus), autres cartes graphiques, performance sur une machine moins puissante : non vérifiés (voir [le lot T1](lot-t1.md)). **Jar de production jamais lancé.**
- Condition du lot T3 toujours ouverte : blocage croissant du code.
- `unZombieNoyeGardeSaMarque` : corrigé par `setPersistenceRequired` à la tâche 6 ; la cause (retrait au loin) reste une forte présomption, sans preuve directe.

## Pour le pack

- **Client et serveur se livrent ensemble, depuis le même commit (`0f395fb` ou après).** Le protocole est `"11"` (un client du protocole `"10"` est refusé, `unClientDuProtocole10EstRefuse`), mais le paquet d'état a gagné un champ à la tâche 13b **sans nouvelle montée** : un jar T2 antérieur serait accepté à la connexion puis lirait mal les états. Le nettoyage n'a changé aucun paquet, mais il ajoute le message du plafond de cartes aux langues. Aucun jar du lot T2 n'a été livré.
- Réglages du serveur : `oneforallsurvival-phone.toml` (voir « Valeurs livrées »). Garder les générateurs dans le chunk de leur antenne, ou chargés autrement.
- MCEF et WebDisplays : rien de nouveau par rapport au lot T1 (décision WebDisplays sur le serveur de production toujours en attente).

## Revues

Chaque tâche a eu sa revue par un relecteur distinct, en lecture seule. Tâches 1 à 8 : corrections demandées, faites dans un tour (re-revue ciblée à la tâche 4 ; la tâche 5 relue avec la tâche 9). Tâche 9 : approuvée. Tâche 10 : approuvée, mineurs corrigés sans nouvelle revue. Tâche 11 : approuvée. Tâche 12 : à corriger, puis relecture à corriger (barre de défilement), corrigée à la tâche 13. Tâche 13 : à corriger (preuve du fil), corrigée, relecture **approuvée**. Tâche 13b : **approuvée**. **Revue finale du lot** : à corriger (I1 à I3 et huit mineurs) ; le passage de nettoyage a corrigé I1 à I3, m3 à m5, la recette, les restes de la tâche 4 (m-A à m-E), les mineurs 1, 3 (changement de fondateur) et 4 de la revue 13b, et N1, N3, N4 de la relecture 13. **Relecture ciblée de ces corrections** (26/11) : code approuvé ; ses points de texte et ses mineurs sont repris (comptes corrigés, écouteur unique du mixin, message du tarif ramené). Le plan est archivé. Après ces retouches : JUnit 985, 0 échec ; « All 1178 required tests passed » sans puis avec le pack (01:16 et 01:19 le 26/11).

## La prise à travers son mur (demande de Killian, 2026-11-25)

Un connecteur de Crafts & Additions posé sur n'importe quelle face du bloc qui porte la prise l'alimente (`phone/WallFeed`, mixin optionnel `ConnectorWallSocketMixin` pour la version 1.20.1-1.3.3) : `be68d64`, corrigé par `05d80e3` après relecture (réglage à la clé respecté, mur à bloc-entité non pris, même plage de version, prise qui a besoin de courant servie). Décision de Killian (2026-11-26) : prendre ainsi le courant d'un relais d'autrui est une affaire de RP, le mod ne l'interdit pas.

- Tests : JUnit 985, 0 échec ; GameTests « All 1178 required tests passed » sans puis avec le pack (00:25 et 00:28 le 26/11), lot `prise_fil` (5).
- Mutations vues rouges : mixin retiré (téléphone à 0 FE) ; garde du mur à bloc-entité retirée (« la prise a pris le courant de la machine ») ; mise en sortie répétée (« le réglage à la clé a été écrasé »).
- Non prouvé (le vrai client et le jar construit sont dans « Reprise des restes ») : jar livré jamais lancé avec ce mixin ; grands connecteurs, connecteurs lumineux, deux prises sur un bloc, mur devenu machine sans mise à jour, rechargement du monde non essayés. `05d80e3` a été relu (corrigé). Du nettoyage restent sans preuve d'essai : l'oubli des panneaux à l'arrêt (l'essai passe même sans l'inscription au bus), le rangement d'un essai expiré (m-C) et le rangement après un échec précoce de l'automate (N3).

## Tâche 15 : numéro caché, « Désactiver », icône grisée (décisions de Killian, 2026-11-26)

- **Numéro et titulaire cachés aux autres joueurs** : `ClientboundSetEquipmentPacketMixin` (constructeur serveur `(int, List)`) passe chaque pile par `phone/PhonePrivacy` ; dans tout paquet d'équipement (début du suivi comme changement en cours, joueur ou porte-armure), un téléphone perd `phone_id` et sa carte logée (`sim`), une carte perd identifiant, jeton, numéro, opérateur et titulaire ; l'objet et la batterie restent ; la pile du serveur n'est jamais touchée ; le porteur garde tout (paquets d'inventaire). Jar livré vérifié par `javap` : champ renommé `f_133199_`, comme dans Forge 1.20.1-47.4.28. **Limite** : une pile posée au sol, dans un cadre, dans un conteneur ouvert ou dans une machine de Create (déployeur, dépôt, tapis) garde ses données (objet physiquement posé) ; les conteneurs portés restent comme écrit plus haut.
- **« Désactiver »** au panneau (bloc et repli) et dans l'appli « Antenne » : fondateur seul, jugé par `OperatorActions.apply` ; second appui sur « Confirmer ? » dans les 5 s (attente tenue par l'écran ou la page) ; message `{"a":"opDeactivate","id":…}`. Effets : `DEACTIVATED` pour toujours, téléphone sans signal, titulaire prévenu (à sa connexion s'il est absent), place libérée sous le plafond.
- **Icône « Antenne » grisée** : le toucher n'ouvre rien, le geste part, seul « Pas de réseau. » répond ; si le serveur ouvre quand même (état changé entre-temps), sa vue montre l'appli. Le repli n'ouvrait déjà rien avant la réponse (relu).
- Tests : JUnit 989, 0 échec ; GameTests « All 1184 required tests passed » sans le pack (02:09 et 02:27) et avec (02:16) le 26/11 ; lots `telephone_confidentialite` (2), `panneau_desactiver` (3), `panneau_plafond` (+1).
- Mutations vues rouges : mixin retiré du `mixins.json` (les deux essais de confidentialité : « Ecoute Discrete », « sim_number ») ; « Désactiver » sans le droit des cartes (« le maire ne désactive rien ») ; titulaire non prévenu ; confirmation sans délai, icône grisée animée, `opDeactivate` lu comme « Remplacer » (JUnit).
- Vu : `#mock` (390 × 844, sans CSP) des deux états du bouton, de l'expiration à 5 s, de l'icône grisée ; un vrai client sans MCEF (automate `antenne`, 56 pas, « TOUT PASSE ») : « Confirmer ? » puis « Désactivée » au panneau Minecraft, et « Pas de réseau. » sans panneau au toucher de l'icône grisée.
- Non prouvé (la page dans un vrai client MCEF est dans « Reprise des restes ») : un second client réel regardant le porteur (le paquet est prouvé par GameTest, pas à l'œil) ; un porteur en partie solo ouverte en réseau local.
- **Corrections de la revue** : le survol d'un objet dans un message (`HoverEvent.ItemStackInfo(ItemStack)`, par exemple le message de mort d'un tueur qui tient un téléphone ou une carte renommés, envoyé à tout le serveur) passe par `PhonePrivacy` (`HoverEventItemStackInfoMixin`, champ `f_130890_` dans le jar livré) ; GameTest `leMessageDeMortNeDitRienDeLaCarte` (JSON du message : aucun secret, ni texte ni identifiant en NBT texte ; une épée renommée garde son survol), mixin retiré vu rouge. « Désactiver » : un second appui à moins de 400 ms du premier ne confirme pas (panneau et page, fenêtre toujours de 5 s ; JUnit, mutation vue rouge ; `#mock` : double-toucher à 100 ms, rien envoyé). Attente cachée de l'icône grisée : quitter l'accueil (Réglages) ferme l'attente et envoie `opClose` (`#mock`). Limite Create ajoutée.

## Reprise des restes (tâche 16, 2026-11-26 et 27)

**État : l'appli et la prise sont vues dans un vrai client ; le passage complet et sa relance passent tous les deux.**

### Passage complet et relance sur serveur dédié avec le pack (27/11, code de `8756a7c`)

| Passage | Heure | Résultat |
|---|---|---|
| Passage complet, monde neuf | 00:49 à 01:08 | **TOUT PASSE, 476 PASS** |
| Relance complète | 01:09 à 01:12 | 122 PASS, 1 échec : quêtes (E5) |
| Passage complet, monde neuf (essai des quêtes corrigé) | 01:23 à 01:42 | **TOUT PASSE, 476 PASS** |
| Relance complète | 01:43 à 01:47 | **TOUT PASSE, 137 PASS** |

- **Joués pour la première fois dans une relance complète depuis le nettoyage** : la couveuse (chunk dit non chargé par la console avant l'arrivée du joueur ; compteur 352 ticks 2,8 s après l'arrivée pour 301 à l'arrêt, œuf présent), le minuit de l'électricité (compteur d'Abonne dans un chunk `charge=false`, factures et caisse justes) et la partie téléphone (antenne de Mairie, tarif du lendemain, opérateur supprimé, cartes relues).
- **Échec des quêtes à 01:12, défaut de l'essai** : le relevé de l'étape a l'hebdomadaire `cook_meals` à 1/2 ; le seul repas retrouvé ensuite au journal est le ragoût du lot 5, et elle vaut 2/2 à la relance. L'horloge de la relance tombe un lundi (30/11, passage joué un vendredi) : la semaine change, l'hebdomadaire finie passe dans les récompenses en attente, ce que le jeu doit faire, et l'assertion l'interdisait. **Corrigé** : `E2eQuetes` relève le carnet juste avant le `/stop` et en déduit ce qui doit attendre. À 01:47, branche « semaine changée » jouée (`cook_meals` 2/2 attendue en attente, puis réclamée avec les planches, +51 cuivres). La branche « même semaine » n'a pas été rejouée depuis la correction, et aucun jeu volontairement cassé n'a été essayé contre elle.
- Le zombie du butin, instable, a été tué en 3 coups ; son nouveau journal de combat (`E2eButin`) n'a donc pas servi.
- Avant ce passage : un passage le 26/11 (23:32 à 23:44) a échoué sur ce zombie (266 PASS, 1 échec), et un autre (23:51) a été interrompu quand le client d'essai a pris la souris de Killian (corrigé par `8756a7c`). Pendant les deux passages et relances du 27/11, un relevé extérieur toutes les 30 ms (20 111 puis 17 154 relevés) n'a vu aucune prise du premier plan ni du curseur.

### Vrai client (26/11)

- **Appli « Antenne » dans MCEF** (mode `antenne`, monde solo, « TOUT PASSE », 66 pas) : double appui à 102 ms sur « Désactiver » : deux clics reçus par la page, aucun `opDeactivate`, carte restée suspendue ; montants relus dans la page ; icône grisée : appli jamais montrée, panneau fermé au serveur, seul « Pas de réseau. » ; captures « Confirmer ? » et icône grisée regardées.
- **Défaut trouvé là et corrigé** (`70f1c57`) : l'identifiant du bouton « Désactiver » dépassait les 48 caractères que le jeu garde d'une boîte de la page.
- **Prise à travers le mur** (mode `prise`, monde solo, « TOUT PASSE », 17 pas) : sans fil, téléphone inchangé (3 600 FE), réserve de la prise 0 ; fil tiré : la charge monte (7 600 FE à la capture) ; connecteur du mur remis sur « aucun » à la clé : il le reste 16 s et la prise n'a plus de courant (réserve 0). Captures en charge et « aucun » regardées.
- Mutations vues rouges (messages des commits) : délai de 400 ms retiré de la page, icône grisée qui ouvre l'appli ; compteur de la couveuse non relu, œufs non relus.

### Jar livré : contrôle statique seulement (code de `9635021`)

Manifeste (les quatre configurations de mixins), mixins et refmap présents, champs `f_133199_` et `f_130890_` renommés, cible et appels du mixin du connecteur en noms SRG, aucune classe d'essai. **Aucun serveur ni client de production lancé** : il faudrait une installation Forge 1.20.1-47.4.28 hors développement, dont le téléchargement demande l'accord de Killian. `8756a7c` modifie ensuite le jar de service de la fenêtre (sous la seule propriété d'essai).

### Tests

« All 1185 required tests passed » sans puis avec le pack le 26/11 (23:26 et 23:30) ; après `8756a7c`, JUnit vert et 1 185 GameTests sans le pack (00:35 le 27/11).

