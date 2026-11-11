# Lot 7 : exploration — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Donner aux joueurs des outils d'exploration (détecteur de métaux, lampe frontale) et à l'équipe du serveur des outils de scénario (caches enfouies, récepteur de signaux, mécanisme ancien), sans embarquer aucun contenu de lore.

**Architecture:** Tout vit dans un nouveau paquet `explore`. Le récepteur et le mécanisme ancien ne passent pas par `ElectricMachineBlockEntity`, parce qu'ils ne s'usent pas (cahier, section 4) : ce sont des entités de bloc simples, avec leur propre `EnergyStorage`. Les objets à batterie partagent une classe `machine/ItemBattery`, tirée de `repair/WelderEnergy`. Les caches et les récepteurs sont indexés par des `SavedData` de dimension, pour que détecteur et commande ne balaient jamais le monde. Les règles chiffrées sont de la logique pure, testée en JUnit. Aucun mixin.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, pack (`-PwithPack`) pour les recettes et l'essai.

**Spec:** `docs/superpowers/specs/2026-11-06-features-lost-island-design.md`, section 4 (machine électrique : « sans usure : … récepteur de signaux et mécanisme ancien »), section 5, features 21 à 24, sections 6 et 7.

## Global Constraints

Celles du lot 6 restent valables, telles quelles : `docs/superpowers/plans/archive/2026-11-10-lot-6-vie-de-la-ville.md`, section « Global Constraints ». En plus :

- **Le mod n'embarque aucun message, aucun texte de lore, aucune explication du mystère** (feature 23). Les seuls textes sont ceux de l'interface et des commandes.
- **Récepteur et mécanisme ancien ne s'usent pas et ne tombent jamais en panne** (section 4).
- **Caches, récepteurs et mécanismes ne s'obtiennent que par l'équipe** : créatif ou `/give`, sans recette. Détecteur, lampe et récepteur se fabriquent à l'établi d'ingénieur.
- **Une commande de l'équipe exige le niveau de permission 2**, comme `RoleCommands`.
- **Tout client lancé passe une propriété JVM `oneforallsurvival.*`**, pour que `dev/SourisLibre` rende la souris. Vérifier « [souris] rendue » dans le journal du client.

## Review Focus

1. **Une lampe frontale ne laisse jamais de bloc de lumière derrière elle**, que le joueur se déconnecte, meure, change de dimension, retire la lampe, vide la batterie, ou que le serveur plante. Testé en tâche 3.
2. **La lampe n'écrase jamais un bloc** et ne retire jamais un bloc de lumière qu'elle n'a pas posé (blocs `minecraft:light` de l'équipe). Testé en tâche 3.
3. **Une commande `/signal`** n'atteint que les récepteurs alimentés, réglés sur la bonne fréquence, dans la zone et dans la dimension ; elle dit à l'équipe combien de récepteurs l'ont reçue. Testé en tâche 4.
4. **Le mécanisme ancien** ne se casse pas en survie, ne saute pas à l'explosion et ne se pousse pas au piston. Testé en tâche 5.
5. **Une cache cassée en survie donne son butin une seule fois** et sort de l'index, si bien que le détecteur se tait. Cassée en créatif, elle ne donne rien. Testé en tâches 1 et 2.

## Décisions prises par ce plan

| Sujet | Décision | Pourquoi |
|---|---|---|
| **Lumière de la lampe** (validé par Killian le 2026-11-11) | Côté serveur, un bloc `minecraft:light` invisible, niveau 14, suit la tête du joueur. Il n'est posé que dans l'air ou dans l'eau (en version imbibée). Chaque bloc posé est noté, pour être retiré dès que le joueur change de bloc, et nettoyé au chargement du chunk après un plantage. | « Lisible et légère sur serveur » : tous les joueurs voient la même lumière, sans mixin dans le moteur de lumière et sans mod client en plus. Le pack n'a aucun mod de lumière dynamique. |
| **Mécanisme une fois actionné** (validé par Killian le 2026-11-11) | Il reste actif, avec un signal de redstone 15, jusqu'à ce que l'équipe le remette à zéro par `/mechanism reset`. Une coupure avant la fin de la durée remet la progression à zéro. | Une ruine ouverte reste ouverte. L'autre choix serait « actif seulement tant qu'il est alimenté ». |
| **Récepteur hors service** (validé par Killian le 2026-11-11) | Un récepteur sans courant ou dans un chunk déchargé manque le message, sans file d'attente. La commande dit combien de récepteurs l'ont reçu. | Fidèle à une radio. L'équipe sait tout de suite si son message est passé. |
| Détecteur | Batterie de 24 000 FE. Il consomme 2 FE par tick, tenu dans une main, soit environ 10 min d'usage. Portée de 24 blocs, en distance réelle. Un bip toutes les 40 ticks au bord de la portée, puis toutes les 2 ticks au contact. Seul le porteur l'entend, et la hauteur du son monte en approchant. | « Son de plus en plus rapide » |
| Lampe | Emplacement du casque, protection 1, aucune durabilité. Batterie de 36 000 FE, 2 FE par tick quand elle est portée, soit environ 15 min. Allumée dès qu'elle est portée et chargée. | « Choisir entre protection et lumière » |
| Recharge | Les deux objets se rechargent par un clic droit sur un bloc qui fournit des FE, comme le poste à souder. | Même geste partout |
| Cache enfouie | Une caisse rouillée, que l'on casse à la hache ou à la main. Son butin vient de la table `oneforallsurvival:caches/default` (débris du lot 2, pépites), que `/cache loot <pos> <table>` remplace cache par cache. | « Butin d'une loot table », réglable par l'équipe |
| Récepteur | Il consomme 4 FE par tick pour écouter et garde un tampon de 2 000 FE. Fréquence de 1 à 9999, réglée à 1 par défaut. Il archive les 50 derniers messages, 256 caractères au plus chacun. Un voyant signale un message non lu. | À équilibrer |
| Commande de signal | `/signal send <fréquence> <rayon> <message>`, rayon horizontal de 1 à 30 000 blocs autour de la source de la commande, dans sa dimension | « Vers une fréquence et une zone » |
| Mécanisme | Réglé par défaut à 100 FE par tick pendant 10 s. `/mechanism set <pos> <fe_par_tick> <secondes>`. Dureté -1, résistance 3 600 000, poussée au piston bloquée. | « Seuil et durée réglés par l'équipe » ; « incassable en survie » |

## Fichiers

| Fichier | Rôle |
|---|---|
| `explore/ExploreRegistry.java`, `explore/ExploreConfig.java` | Registres, config `SERVER` `oneforallsurvival-explore.toml` |
| `explore/CacheBlock.java`, `CacheBlockEntity.java`, `CacheIndex.java` (SavedData), `ExploreCommands.java` | Caches enfouies, `/cache`, `/signal`, `/mechanism` |
| `machine/ItemBattery.java` | Batterie d'objet partagée (NBT `energy`, capacité en paramètre, recharge sur un bloc) |
| `explore/DetectorItem.java`, `DetectorRules.java` | Détecteur |
| `explore/HeadlampItem.java`, `HeadlampLights.java`, `HeadlampLightData.java` (SavedData) | Lampe frontale et suivi des blocs de lumière |
| `explore/ReceiverBlock.java`, `ReceiverBlockEntity.java`, `ReceiverIndex.java` (SavedData), `ReceiverMenu.java`, `ReceiverFrequencyPacket.java`, `client/ReceiverScreen.java` | Récepteur |
| `explore/MechanismBlock.java`, `MechanismBlockEntity.java`, `MechanismRules.java` | Mécanisme ancien |
| `src/devcontent/.../{Cache,Detecteur,Lampe,Recepteur,Mecanisme}GameTests.java`, `E2eExploration.java`, `FilmExploration.java` | Preuves |
| `tools/gen_textures_lot7.py` | Textures |

---

### Task 1: Caches enfouies

**Files:** `explore/ExploreRegistry.java`, `ExploreConfig.java`, `CacheBlock.java`, `CacheBlockEntity.java`, `CacheIndex.java`, `ExploreCommands.java`, `data/oneforallsurvival/loot_tables/caches/default.json`, `src/devcontent/.../CacheGameTests.java`. Modifier : `OneForAllSurvival.java` (registre, config, commandes), l'onglet créatif, les langues.

**Interfaces:**
- Produces : `ExploreRegistry.CACHE`, `CACHE_ITEM`, `CACHE_BE`, `register(IEventBus)` et `tabItems()`.
- Produces : `CacheBlockEntity` avec `lootTable() : ResourceLocation` (valeur par défaut `oneforallsurvival:caches/default`) et `setLootTable(ResourceLocation)`.
- Produces : `CacheIndex.get(ServerLevel) : CacheIndex`, avec `add(BlockPos)`, `remove(BlockPos)` et `nearest(BlockPos from, double range) : Optional<BlockPos>`.
- Produces : `ExploreCommands.register(CommandDispatcher)`.

Règles :
- `onPlace` ajoute la position à l'index, `onRemove` l'en retire.
- Cassée par un joueur en survie, la cache lâche le butin de sa table, généré avec la position et le joueur. Elle ne lâche pas son propre bloc. Cassée en créatif, elle ne lâche rien.
- `/cache loot <pos> <table>` règle la table, avec une table qui doit exister. `/cache list` donne les positions de la dimension.

- [ ] **Step 1: Écrire `CacheGameTests`** :
  - `poseeElleEstIndexee` ;
  - `casseeEnSurvieElleDonneSonButin` (des objets tombent et viennent de la table) ;
  - `casseeEnCreatifRien` ;
  - `casseeElleQuitteLIndex` ;
  - `laTableSeRegleParCommande` ;
  - `uneTableInconnueEstRefusee` ;
  - `laTableSurvitAuRechargement`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(explore): caches enfouies`.

### Task 2: Batterie d'objet et détecteur de métaux

**Files:** `machine/ItemBattery.java`, `explore/DetectorItem.java`, `DetectorRules.java`, `src/test/java/dev/oneforallsurvival/explore/DetectorRulesTest.java`, `src/devcontent/.../DetecteurGameTests.java`. Modifier : `repair/WelderEnergy.java` (délègue à `ItemBattery`, sans changer de comportement), `ExploreRegistry`, `ExploreConfig`, les langues.

**Interfaces:**
- Consumes : `CacheIndex.nearest` (tâche 1).
- Produces : `ItemBattery`, en méthodes statiques :
  - `stored(ItemStack, int capacity) : int` ;
  - `store(ItemStack, int fe, int capacity)` ;
  - `spend(ItemStack, int fe, int capacity) : boolean` ;
  - `chargeFrom(ItemStack, BlockEntity source, Direction side, int capacity) : int`, qui reprend la logique de `RepairActions.charge` ;
  - et un fournisseur de capability `ItemBattery.Provider(ItemStack, IntSupplier capacity)`.
- Produces : `DetectorRules.beepInterval(double distance, double range) : int`. Il vaut -1 au-delà de la portée. Sinon, `round(2 + 38 × distance / range)`, borné entre 2 et 40.
- Produces : `DetectorRules.pitch(double distance, double range) : float`, de 0,5 au bord à 2,0 au contact.
- `ExploreConfig` : `detectorCapacity()` 24000, `detectorEnergy()` 2, `detectorRange()` 24.

Règles :
- Le détecteur agit côté serveur, à chaque tick où il est tenu dans une main, chargé et dans une dimension qui a des caches.
- Il dépense `detectorEnergy`.
- Il cherche `nearest` et joue `NOTE_BLOCK_BIT` au seul porteur (`ServerPlayer.playNotifySound`) quand le compteur de ticks atteint l'intervalle.
- Sans charge, il se tait et ne dépense rien.
- Le clic droit sur un bloc à FE le recharge.
- La barre de l'objet montre la charge.

- [ ] **Step 1: Écrire `DetectorRulesTest`** :
  - `auBordQuarante` ;
  - `auContactDeux` ;
  - `horsPorteeMoinsUn` ;
  - `plusPresPlusRapide` (monotone) ;
  - `lePitchMonte`.
- [ ] **Step 2: Écrire `DetecteurGameTests`** :
  - `tenuPresDUneCacheIlBipe` (le compteur de bips augmente, la charge baisse de 2 par tick) ;
  - `sansCacheIlSeTait` ;
  - `deChargeIlSeTaitEtNeDepenseRien` ;
  - `uneCacheCasseeNeSonnePlus` ;
  - `dansLeSacIlNeDepenseRien` ;
  - `ilSeRechargeSurUneSource`.
  - Ajouter le GameTest existant du poste à souder, relancé tel quel, pour prouver que `WelderEnergy` n'a pas changé.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer, lire** « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(explore): detecteur de metaux`.

### Task 3: Lampe frontale

**Files:** `explore/HeadlampItem.java`, `HeadlampLights.java`, `HeadlampLightData.java`, `src/devcontent/.../LampeGameTests.java`. Modifier : `ExploreRegistry`, `ExploreConfig`, les langues.

**Interfaces:**
- Consumes : `ItemBattery` (tâche 2).
- Produces : `HeadlampItem extends ArmorItem` (tête, matériau du mod : protection 1, durabilité 0, enchantable 0), avec `ExploreConfig` : `headlampCapacity()` 36000, `headlampEnergy()` 2, `headlampLight()` 14 (de 1 à 15).
- Produces : `HeadlampLights.update(ServerPlayer)`, appelé à la fin de chaque tick serveur de joueur, et `HeadlampLights.clear(ServerPlayer)`.
- Produces : `HeadlampLightData.get(ServerLevel)`, avec `placed(BlockPos)`, `released(BlockPos)` et `isOurs(BlockPos) : boolean`.

Règles :
- **Allumage.** La lampe s'allume si elle est portée, chargée, et si le joueur n'est pas spectateur. Elle dépense `headlampEnergy` par tick.
- **Position.** La cible est le bloc des yeux. Si ce bloc est de l'air, on y pose `minecraft:light` au niveau `headlampLight`. Si c'est de l'eau source, on le pose imbibé. Sinon, on ne pose rien et on n'écrase rien.
- **Changement de bloc.** Quand la cible change, l'ancien bloc est retiré seulement si `isOurs` le confirme et s'il est toujours un `minecraft:light`.
- **Plusieurs joueurs.** Un compte par position permet à deux joueurs de partager un bloc, qui n'est retiré qu'au dernier départ.
- **Extinction.** `clear` est appelé à la déconnexion, à la mort, au changement de dimension, quand la lampe est retirée ou vide, et à l'arrêt du serveur.
- **Après un plantage.** Au chargement d'un chunk, tout bloc de lumière noté dans ce chunk qui n'appartient à aucun joueur présent est retiré, et sa note effacée.

- [ ] **Step 1: Écrire `LampeGameTests`** (joueurs `JoueurDeTest`, sous un plafond) :
  - `porteeElleEclaire` (un bloc de lumière de niveau 14 à la tête, et la charge baisse) ;
  - `enMarchantLAncienneLumiereDisparait` ;
  - `retireeToutDisparait` ;
  - `videeToutDisparait` ;
  - `elleNEcraseRien` (tête dans un bloc de verre) ;
  - `elleNeRetirePasLaLumiereDeLEquipe` (un `minecraft:light` posé avant, au même endroit, reste) ;
  - `sousLEauLumiereImbibee` ;
  - `deuxJoueursUnBlocPartage` ;
  - `deconnexionNettoie` ;
  - `orphelineNettoyeeAuChargement` (une note sans joueur, puis un chunk rechargé : le bloc est parti).
  - Prévoir deux lots si les tests dépassent 8.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 5: Commit** `feat(explore): lampe frontale`.

### Task 4: Récepteur de signaux

**Files:** `explore/ReceiverBlock.java`, `ReceiverBlockEntity.java`, `ReceiverIndex.java`, `ReceiverMenu.java`, `ReceiverFrequencyPacket.java`, `client/ReceiverScreen.java`, `client/ExploreClient.java`, `src/devcontent/.../RecepteurGameTests.java`. Modifier : `ExploreCommands`, `ExploreRegistry`, `ExploreConfig`, le canal réseau du mod (`bench/BenchNetwork.java`, comme `SubscriberNamePacket`), les langues.

**Interfaces:**
- Produces : `ReceiverBlockEntity`, avec :
  - `CAPACITY = 2000` ;
  - `frequency() : int` et `setFrequency(int)`, de 1 à 9999 ;
  - `listening() : boolean`, vrai si au moins `receiverEnergy` est en réserve ;
  - `deliver(SignalMessage) : boolean` ;
  - `archive() : List<SignalMessage>`, 50 messages au plus, le plus récent en premier ;
  - `markRead()`.
- Produces : `record SignalMessage(long gameTime, int frequency, String text)`. Le texte est coupé à 256 caractères.
- Produces : `ReceiverBlock` avec les propriétés `listening` et `unread`.
- Produces : `ReceiverIndex.get(ServerLevel)`, avec `add(BlockPos)`, `remove(BlockPos)` et `within(BlockPos centre, int radius) : List<BlockPos>`.
- Produces : la commande `/signal send <fréquence> <rayon> <message>`.

Règles :
- **Écoute.** Le récepteur consomme `receiverEnergy` (4) par tick tant qu'il en a.
- **Envoi.** La commande parcourt `within`. Elle livre seulement aux récepteurs dont le chunk est chargé, qui écoutent et qui sont réglés sur la bonne fréquence. Elle répond « Message reçu par N récepteur(s) » et donne N = 0 sans erreur.
- **Livraison.** Une livraison met `unread` à vrai. Ouvrir l'écran met `unread` à faux.
- **Écran.**
  - Un champ de fréquence avec un bouton « Régler ». Le paquet est vérifié côté serveur : distance du joueur, bornes de la fréquence.
  - La liste de l'archive, défilante.
- **Sauvegarde.** La fréquence et l'archive sont sauvegardées.
- **Pas d'usure.**

- [ ] **Step 1: Écrire `RecepteurGameTests`** :
  - `alimenteIlRecoit` ;
  - `sansCourantIlManque` ;
  - `mauvaiseFrequenceRien` ;
  - `horsZoneRien` ;
  - `autreDimensionRien` (seulement si le gabarit le permet ; sinon tester `within` à part) ;
  - `laCommandeCompte` ;
  - `archiveBorneeACinquante` ;
  - `archiveEtFrequenceSurviventAuRechargement` ;
  - `frequenceHorsBornesRefusee`.
  - Deux lots si besoin.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(explore): recepteur de signaux`.

### Task 5: Mécanisme ancien

**Files:** `explore/MechanismBlock.java`, `MechanismBlockEntity.java`, `MechanismRules.java`, `src/test/java/dev/oneforallsurvival/explore/MechanismRulesTest.java`, `src/devcontent/.../MecanismeGameTests.java`. Modifier : `ExploreCommands`, `ExploreRegistry`, les langues.

**Interfaces:**
- Produces : `MechanismRules.next(int progress, int receivedThisTick, int threshold, int durationTicks) : int`.
  - La progression repart à 0 si `receivedThisTick < threshold`. Sinon, elle vaut `progress + 1`, plafonnée à `durationTicks`.
  - `isComplete(int progress, int durationTicks) : boolean`.
- Produces : `MechanismBlockEntity`, avec :
  - `threshold()` 100 et `durationTicks()` 200, que l'équipe règle par commande ;
  - `active() : boolean`, qui une fois vrai le reste jusqu'à `reset()` ;
  - une capability d'énergie qui accepte au plus `2 × threshold` par tick et compte ce qu'elle reçoit.
- Produces : `MechanismBlock` avec les propriétés `stage` (de 0 à 3, selon la progression) et `active`. Le bloc est une source de redstone 15 quand `active`.
- Produces : les commandes `/mechanism set <pos> <fe_par_tick> <secondes>`, `/mechanism reset <pos>` et `/mechanism info <pos>`.

Règles :
- **Solidité.** Dureté -1, résistance 3 600 000, `PushReaction.BLOCK`, aucun butin. Il se casse seulement en créatif.
- **Énergie.** Il reçoit des FE de tout câble, du mod ou d'un autre mod. L'énergie reçue est consommée, jamais stockée d'un tick à l'autre.
- **Sauvegarde.** Seuil, durée, progression et `active` sont sauvegardés.
- **Chunk déchargé.** Rien n'avance, et rien ne se perd.
- **Pas d'usure.**

- [ ] **Step 1: Écrire `MechanismRulesTest`** :
  - `alimenteAuSeuilAvance` ;
  - `sousLeSeuilRepartAZero` ;
  - `plafonneALaDuree` ;
  - `completAPileALaDuree`.
- [ ] **Step 2: Écrire `MecanismeGameTests`** :
  - `assezLongtempsIlSActive` (un signal de redstone lu sur un bloc voisin) ;
  - `uneCoupureRemetAZero` ;
  - `actifIlLeReste` ;
  - `resetLEteint` ;
  - `incassableEnSurvie` ;
  - `ilResisteALExplosion` ;
  - `lePistonNeLePoussePas` ;
  - `reglageEtEtatSurviventAuRechargement`.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer, lire** « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(explore): mecanisme ancien`.

### Task 6: Recettes et visuels finaux

**Files:**
- Créer :
  - les recettes de table de `metal_detector`, `headlamp` et `signal_receiver`, avec les conditions `forge:mod_loaded`, comme `dryer.json` ;
  - les tables de butin des blocs ;
  - les états, modèles et textures ;
  - la texture de couche d'armure de la lampe (`textures/models/armor/headlamp_layer_1.png`) ;
  - `tools/gen_textures_lot7.py`.
- Modifier : `DemoVisuels`, `PackGameTests`, les langues, les tags `mineable/*`.

Ces trois objets se fabriquent à l'établi d'ingénieur, avec des pièces du lot 2 (bobine, fusible, tag `oneforallsurvival:wire`), des plaques de Create et du verre pour la lampe. Caches et mécanismes n'ont pas de recette. `PackGameTests` vérifie :
- qu'aucune recette de table ne les donne une fois le pack chargé ;
- qu'une cache et un mécanisme n'ont aucune recette.

Visuels, dans le style bricolage et récupération, en 16 × 16, cohérents avec les lots 2 à 6 :
- un détecteur au manche de tuyau et au disque de tôle ;
- une lampe frontale à sangle de cuir et réflecteur, en objet et portée ;
- une cache : caisse rouillée cerclée ;
- un récepteur : poste de radio de récupération, avec un voyant de message non lu et un état sans courant ;
- un mécanisme ancien : pierre taillée sombre veinée de cuivre, dont les veines s'allument selon `stage` et `active`. C'est le seul indice de lore, sans aucun texte.

Captures :
- chaque bloc seul et collé à un mur ;
- la lampe portée, de nuit dans une grotte ;
- l'écran du récepteur avec deux messages ;
- le mécanisme à chaque étape.

- [ ] **Step 1: Écrire les tests de `PackGameTests`.**
- [ ] **Step 2: Lancer avec `-PwithPack`, constater l'échec.**
- [ ] **Step 3: Produire recettes, butins, modèles, textures et images.**
- [ ] **Step 4: Lancer `runDemoVisuels`**, ouvrir les captures, vérifier.
- [ ] **Step 5: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 6: Commit** `feat(lot7): recettes et visuels finaux`.

### Task 7: Preuve de bout en bout, film, vérification

**Files:** Créer : `dev/E2eExploration.java`, `dev/FilmExploration.java`, `docs/verifications/lot-7.md`. Modifier :
- `dev/E2eClient.java` et `build.gradle` (`runDemoExplorationFilm`) ;
- `wiki/data/electricite.json` : les entrées `detecteur-metaux`, `lampe-frontale`, `recepteur-signaux` et `mecanismes-ruines` passent à `dispo`, au format de `phare` ;
- `tasks/todo.md`.

**Essai sur serveur dédié, avec le pack, en un passage puis une relance.** Un joueur non opérateur fait toute la séquence :
- il recharge détecteur et lampe sur une source ;
- il trouve une cache, les bips s'accélérant (intervalles relevés dans le journal), la casse et ramasse son butin ;
- il porte la lampe dans une grotte fermée : le niveau de lumière est lu à sa tête, puis disparaît quand il retire la lampe ;
- l'équipe envoie `/signal send` : le récepteur alimenté le reçoit, celui qui est coupé ne le reçoit pas, et la commande donne le bon compte ;
- le mécanisme, alimenté par un vrai câble d'un autre mod du pack, s'active et ouvre une porte de fer par redstone ;
- le joueur ne peut pas le casser.

Après `/stop` puis la relance, on vérifie :
- l'archive du récepteur ;
- le mécanisme, toujours actif ;
- l'absence de tout bloc de lumière orphelin autour du dernier point de la lampe.

**Film**, en monde intégré : la recherche d'une cache au détecteur, avec le son annoncé à l'écran ; la lampe dans une grotte ; un message reçu ; le mécanisme qui s'éveille et ouvre la porte. Filmer avec `FrameRecorder`/`FilmCapture` ou `gfxcapture`, jamais `ddagrab`.

- [ ] **Step 1: Écrire l'E2E et le film, lancer.** Lire `[e2e] bilan : TOUT PASSE` au passage complet et à la relance, ainsi que « [souris] rendue ».
- [ ] **Step 2: Rouvrir le film en images** et vérifier chaque scène.
- [ ] **Step 3: Écrire `docs/verifications/lot-7.md`.** Séparer le prouvé du non prouvé, donner les comptes et les heures. Couvrir au moins : l'équilibrage réel, le multijoueur, le coût serveur des lampes (non mesuré), la lumière de la lampe vue par un second client, et les FE d'un autre mod.
- [ ] **Step 4: Mettre le wiki à jour et cocher le lot dans `tasks/todo.md`**, en listant ce qui reste ouvert.
- [ ] **Step 5: Commit et push** `test(lot7): E2E et film`, puis `docs(lot7): preuve du lot 7`.
