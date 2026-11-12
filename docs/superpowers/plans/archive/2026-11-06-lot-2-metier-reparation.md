# Lot 2 : métier de réparation — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire exister le métier de réparation : les machines du mod s'usent et tombent en panne, l'ingénieur les diagnostique et les répare avec des pièces, récupère ses matières rares sur l'épave et protège les machines côtières de la corrosion.

**Architecture:** L'usure vit dans le socle `ElectricMachineBlockEntity`, donc toute machine présente et future en hérite. Les règles chiffrées sont de la logique pure testée en JUnit (`MachineWear`, `CoastZones`), le reste se prouve par GameTests. Pièces et recettes sont portées par des tags et du JSON. Les particules partent du serveur (`sendParticles`) : aucun bloc de feu, aucune propriété de BlockState ajoutée, aucun code client pour l'usure.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, recettes `create:sequenced_assembly`, API JEI en `compileOnly`.

**Spec:** `docs/superpowers/specs/archive/2026-11-06-features-lost-island-design.md`, section 4 (« Machine électrique », « Système de rôles », « Détection de l'eau salée ») et section 5 features 1, 2, 3, 4. État précédent : `docs/verifications/lot-1.md`.

## Global Constraints

- `gradle.properties` en ASCII pur. Aucun `${` dans `mods.toml` et `pack.mcmeta` hors propriété définie.
- Mod requis côté client et serveur. Aucune classe cliente atteignable depuis un chemin serveur : tout code d'écran dans un paquet `client`, enregistré par un abonné `Dist.CLIENT`.
- Textes visibles dans `fr_fr.json`, avec repli `en_us.json`. Ne jamais écrire le nom « SolarTown » : le serveur s'appelle Lost Island RP.
- Rien de `devcontent` dans le mod livré. Blocs d'essai et GameTests dans `src/devcontent`. Registres : `DeferredRegister` ou `RegisterEvent`, jamais un initialiseur statique.
- Preuve des GameTests : lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie de Gradle. `./gradlew runGameTestServer` sans le pack, `-PwithPack` pour ce qui exige Create. Les rappels `runAfterDelay` d'un même tick ont un ordre arbitraire : un contrôle par tick.
- Aucun téléchargement hors dépendances déjà en cache. Ne jamais tuer un processus Java autre que ceux qu'on a lancés, par PID exact. Ne jamais écrire les configs Forge avec `Set-Content -Encoding utf8`.
- Commit et push sur la branche `lot-2` après chaque tâche vérifiée, fichiers ajoutés par nom (jamais `art/` ni `docs/assets/`), pas de `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Commentaires et messages en français.
- Dégâts et feu : les flammes de panne ne posent aucun bloc, ne se propagent pas et s'éteignent seules. Le contenu d'une machine en panne n'est jamais perdu.
- Une zone déchargée n'est pas une coupure : ni usure ni perte.

## Review Focus

1. Sauvegarde et rechargement en pleine usure, en panne, protégée : état, type de panne, prochaine panne et protection reviennent à l'identique. Testé en tâche 1.
2. Mauvaise pièce, pièce traitée pour une machine normale, batterie vide, non-ingénieur : rien n'est consommé, ni pièce ni FE. Testé en tâche 2.
3. Clic sur l'établi avec le testeur ou le poste à souder : l'outil agit, le menu de l'établi ne s'ouvre pas. Testé en tâche 2.
4. Débris frappé à la main ou soufflé par une explosion : le stock limité n'est pas perdu sans butin. Testé en tâche 3.
5. Bain cassé, ou entrée retirée en cours de traitement : aucun objet ne disparaît, aucun zinc n'est payé sans résultat. Testé en tâche 5.

## Décisions prises par ce plan (à signaler à Killian)

| Décision | Valeur | Pourquoi |
|---|---|---|
| Durée de vie | 12 h de marche réelle à 100 %, soit plusieurs sessions (config) | La spec demande « plusieurs sessions », seul un essai en jeu tranchera |
| Rendement sous 50 % | La machine paie son FE à chaque tick mais ne travaille que 3 ticks sur 4 (`0.75`, config) | « Rendement réduit » est mesurable et ne touche pas au code de chaque machine |
| Établi | Il s'use et peut tomber en panne. En panne il reste utilisable à la main, sans rien perdre ; sous 50 %, sa vitesse alimentée est multipliée par le rendement sans jamais descendre sous la vitesse à la main | C'est aujourd'hui la seule machine livrée : sans lui le métier n'aurait aucun objet réel |
| Type de panne | Tiré à l'avance et lisible au testeur dès que l'état est sous 100 % | Permet l'entretien préventif et de prévoir sa pièce : intervention de métier plutôt que corvée |
| Réparation préventive | Permise dès que l'état est sous 100 %, ou à 100 % pour poser une pièce traitée sur une machine non protégée | Une machine neuve au bord de l'eau doit pouvoir être protégée |
| Geste de réparation | Poste à souder en main principale, pièce dans l'autre main, clic droit, instantané | Sans mode d'emploi caché : la pièce est visible en main |
| Coût d'une réparation | 400 FE, batterie de 2000 FE (config) | Cinq réparations par charge |
| Recharge du poste | Clic droit accroupi sur un bloc qui fournit du FE (accumulateur, etc.) ou sur une machine du mod, dont on prélève la réserve ; la capability accepte aussi les chargeurs des autres mods | La station de charge d'Advanced Electricity et la bobine Tesla de Crafts & Additions restent utilisables, sans en dépendre |
| Multimètre de Crafts & Additions | Non utilisé | Jar 1.3.3 lu : un modèle et une texture, aucune classe dédiée, aucun fichier de recette à son nom. À confirmer en tâche 6 en cherchant une recette dont il est le résultat ; s'il en a une, il remplace l'électron dans la recette du testeur |
| Fusibles | Le tag `parts/fuse` accepte aussi `fuse_16a` d'Advanced Electricity (optionnel) | Spec : réutiliser l'existant plutôt que dupliquer |
| Pièces traitées | Quatre objets distincts, pas une donnée NBT | Recettes en JSON pur et visibles dans JEI |
| Outil de démontage | Le tag `salvage_tools` : pioches et clé de Create. Sans lui, le débris ne se casse pas du tout | Casser à la main ne doit pas gâcher un stock limité |
| Commandes | `/coast`, réservé au niveau 2 | Calque `/role` |
| Rayon côtier | Disque de 16 blocs, à un seul niveau Y réglable (63 par défaut) | Carte faite à la main, spec |

---

## Fichiers

| Fichier | Rôle |
|---|---|
| `machine/FaultType.java` | Les quatre pannes |
| `machine/MachineWear.java` | Calcul pur de l'usure et du rendement |
| `machine/MachineConfig.java` | Réglages, fichier `oneforallsurvival-machines.toml` |
| `machine/ElectricMachineBlockEntity.java` | Modifié : état, panne, protection, particules |
| `repair/RepairRegistry.java`, `RepairTags.java` | Objets et tags |
| `repair/TesterItem.java`, `MachineReport.java` | Lecture de l'état |
| `repair/WelderItem.java`, `WelderEnergy.java`, `RepairActions.java` | Réparation et batterie |
| `salvage/SalvageBlock.java`, `SalvageRegistry.java` | Débris |
| `coast/CoastZones.java`, `CoastSavedData.java`, `CoastDetector.java`, `CoastCommands.java` | Eau salée |
| `repair/GalvanicBath*.java`, `GalvanicRecipe.java`, `repair/client/GalvanicBathScreen.java` | Bain galvanique |
| `src/devcontent/.../{Usure,Reparation,Debris,Cote,Bain}GameTests.java` | Preuves |

---

### Task 1: Usure et pannes dans le socle

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/machine/FaultType.java`, `MachineWear.java`, `MachineConfig.java`; `src/test/java/dev/oneforallsurvival/machine/MachineWearTest.java`; `src/devcontent/java/dev/oneforallsurvival/dev/UsureGameTests.java`
- Modify: `machine/ElectricMachineBlockEntity.java`, `bench/WorkshopTicker.java`, `OneForAllSurvival.java`, `dev/TestMachineBlockEntity.java`, `dev/MachineGameTests.java`, les deux fichiers de langue

**Interfaces:**
- Produces:
  - `enum FaultType { FUSE, WINDING, BEARING, FOULING }` avec `String translationKey()` valant `fault.oneforallsurvival.<nom en minuscules>` et `static FaultType byName(String)` (null si inconnu).
  - `MachineWear` : `static final double REDUCED_BELOW = 50, SMOKE_BELOW = 20, FULL = 100` ; `static double wearPerTick(double lifetimeTicks, boolean coastal, boolean isProtected, double coastalMultiplier)` ; `static double efficiency(double condition, double reducedEfficiency)`.
  - `MachineConfig.SPEC` et lecteurs avec repli comme `BenchConfig` : `lifetimeTicks()` (depuis `lifetimeHours` défaut 12.0), `reducedEfficiency()` 0.75, `coastalMultiplier()` 2.0, `flamesEnabled()` true, `flameChance()` 0.5, `flameTicks()` 200.
  - Sur `ElectricMachineBlockEntity` : `public double condition()`, `@Nullable public FaultType fault()`, `public FaultType nextFault()`, `public boolean isProtected()`, `public double efficiency()`, `public int flameTicksLeft()`, `public void breakDown(FaultType)`, `public RepairOutcome repair(FaultType part, boolean treated)`, `protected void setCondition(double)`, `protected void igniteFlames()`, `protected boolean isCoastal()` (rend `false` à cette tâche). `enum RepairOutcome { REPAIRED, WRONG_PART, NOTHING_TO_DO }` dans `machine`.
  - Le `setFault(boolean)` protégé disparaît ; `TestMachineBlockEntity.panne(boolean)` appelle `breakDown(FaultType.FUSE)` ou répare par une méthode protégée `clearFault()`.

Règles du tick, dans la branche où l'énergie est payée :
`workCredit += efficiency(); if (workCredit >= 1) { workCredit -= 1; workTick(); }`, puis l'état baisse de `wearPerTick`. Arrivé à 0 : panne de type `nextFault()`, condition à 0, `igniteFlames()` avec la probabilité `flameChance` si `flamesEnabled`. `repair` : pièce différente de la panne ou de `nextFault()` → `WRONG_PART` ; condition à 100 sans panne et (pièce non traitée ou déjà protégée) → `NOTHING_TO_DO` ; sinon condition à 100, panne levée, `isProtected = treated`, prochaine panne retirée au sort. Particules, depuis `serverTick`, par `ServerLevel.sendParticles` : toutes les 20 ticks si condition < 20 et état `RUNNING` ou `FAULT`, `SMOKE` et `ELECTRIC_SPARK` ; toutes les 5 ticks `FLAME` tant que `flameTicksLeft > 0`, qui décroît à chaque tick. NBT : `condition` (double, absente = 100), `fault_type` (string), `next_fault` (string), `protected` (boolean). Les flammes ne se sauvegardent pas.

`WorkshopTicker.speedOf` : si l'établi chargé tourne, `Math.max(manualSpeed, poweredSpeed * bench.efficiency())`, avec cette combinaison extraite dans une méthode statique pure `combinedSpeed(double manual, double powered, double efficiency)`.

- [ ] **Step 1: Écrire les tests JUnit `MachineWearTest`** : `wearPerTick(1000, false, false, 2)` vaut 0.1 ; côtier non protégé 0.2 ; côtier protégé 0.1 ; `efficiency(50, .75)` vaut 1.0 et `efficiency(49.9, .75)` vaut 0.75 ; `combinedSpeed(1, 4, .75)` vaut 3 et `combinedSpeed(1, 4, .1)` vaut 1.
- [ ] **Step 2: Écrire `UsureGameTests`** avec la machine d'essai (`test/plancher`, un `batch` propre par test), en exposant ce qu'il faut sur `TestMachineBlockEntity` (`etat(double)`, `flammes()`) :
  - `lEtatNeBaisseQuEnMarche` : sans FE, condition 100 après 20 ticks ; avec FE, condition < 100.
  - `sousCinquanteLeRendementBaisse` : à 40 %, sur 40 ticks alimentés, progression entre 28 et 32, et FE consommé pour les 40 ticks.
  - `aZeroLaMachineTombeEnPanne` : à 0.0001, avec FE : `state()==FAULT`, `fault()` égale la `nextFault()` lue avant, consommation arrêtée.
  - `lesFlammesNePosentAucunBlocEtSEteignent` : `igniteFlames` forcé, puis après `flameTicks()+5` ticks `flammes()==0`, machine présente, aucun bloc de feu dans un cube de rayon 2.
  - `lEtatSurvitAuRechargement` : à 37 %, protégée (via `repair` puis `etat`), en panne : `load(saveWithoutMetadata())` sur une copie rend état, panne, prochaine panne, protection.
  - `lEtablienPanneContinueALaMain` : suit `WorkshopGameTests` : tête de file sur un établi `breakDown`, elle avance à la vitesse à la main et aucun ingrédient ne disparaît à l'annulation.
- [ ] **Step 3: Lancer, constater l'échec** : `./gradlew test` et `./gradlew runGameTestServer`, FAIL (compilation).
- [ ] **Step 4: Implémenter** les fichiers ci-dessus ; config enregistrée dans le constructeur du mod avec `context.registerConfig(ModConfig.Type.COMMON, MachineConfig.SPEC, "oneforallsurvival-machines.toml")`. Clés de langue des quatre pannes (fusible, bobinage, roulement, encrassement).
- [ ] **Step 5: Vérifier** : `./gradlew test` passe ; « All N required tests passed » dans `run-gametest/logs/latest.log`, anciens tests compris.
- [ ] **Step 6: Commit et push.**

### Task 2: Trousse de diagnostic

**Files:**
- Create: `repair/RepairRegistry.java`, `RepairTags.java`, `TesterItem.java`, `MachineReport.java`, `WelderItem.java`, `WelderEnergy.java`, `RepairActions.java` ; tags `data/oneforallsurvival/tags/items/parts/{fuse,coil,bearing,cleaning_kit}.json` et `treated_parts/{fuse,coil,bearing,cleaning_kit}.json` ; `dev/ReparationGameTests.java`
- Modify: `OneForAllSurvival.java`, `bench/BenchRegistry.java` (onglet créatif), `MachineConfig.java`, langues

**Interfaces:**
- Consumes: l'API de la tâche 1 ; `RoleGate.canDoEngineerWork(ServerPlayer)`.
- Produces:
  - `RepairRegistry.register(IEventBus)`, avec objets `tester`, `welder`, `fuse`, `coil`, `bearing`, `cleaning_kit`, `treated_fuse`, `treated_coil`, `treated_bearing`, `treated_cleaning_kit`, et `static List<RegistryObject<? extends Item>> tabItems()` lu par l'onglet créatif.
  - `RepairTags.partFor(ItemStack) -> Optional<PartMatch>` avec `record PartMatch(FaultType type, boolean treated)`.
  - `MachineReport.lines(ElectricMachineBlockEntity) -> List<Component>` : état en %, état visible, FE, panne s'il y en a une, pièce à prévoir si condition < 100, mention « protégée » ou « exposée à la corrosion ».
  - `RepairActions.repair(ServerPlayer, ElectricMachineBlockEntity, ItemStack welder, ItemStack part) -> Result`, `enum Result { REPAIRED, NOT_ENGINEER, NO_PART, WRONG_PART, NOTHING_TO_DO, NO_ENERGY }`, contrôles dans cet ordre. Pièce et FE consommés seulement sur `REPAIRED`, pièce conservée en créatif. Chaque résultat a son message `message.oneforallsurvival.repair.<nom>` ; `WRONG_PART` nomme la pièce attendue.
  - `WelderEnergy` : capability `ForgeCapabilities.ENERGY` adossée au NBT de la pile (`energy`), `canExtract` faux, `MachineConfig.welderCapacity()` 2000, `repairEnergy()` 400. `RepairActions.charge(ItemStack welder, BlockEntity source, Direction side) -> int` : `extractEnergy` si le bloc fournit, sinon réserve d'une `ElectricMachineBlockEntity` par `public int drainEnergy(int)` à ajouter au socle.

Les deux outils agissent dans `onItemUseFirst`, pour passer avant `Block.use`, et rendent `InteractionResult.sidedSuccess` sur une machine du mod ; le testeur lit pour tout le monde. Poste à souder : accroupi = recharge, debout = réparation avec la pièce de l'autre main ; barre de durabilité et infobulle montrent la batterie. Réparation réussie : son `BLOCKS.ANVIL_USE` à faible volume et `ELECTRIC_SPARK` envoyés par le serveur. Le poste commence vide.

- [ ] **Step 1: Écrire `ReparationGameTests`** avec `JoueurDeTest` (rôle posé par `RoleSavedData`, joueur non opérateur) :
  - `laBonnePieceReparePuisEstConsommee` : panne FUSE, pièce `fuse`, 400 FE retirés, condition 100, `fault()` nulle, pile vide.
  - `laMauvaisePieceEstRefuseeSansRienConsommer`, `sansBatterieRienNestConsomme`, `unCitoyenNePeutPasReparer` (`NOT_ENGINEER`, rien de consommé).
  - `unCitoyenPeutLireLEtat` : `MachineReport.lines` contient la panne et la pièce.
  - `laPieceTraiteeProtegeEtLaNormaleRetire` : `isProtected` vrai puis faux au cycle suivant.
  - `laPieceTraiteeSePoseSurUneMachineNeuve` : à 100 %, `treated` → `REPAIRED` ; pièce normale → `NOTHING_TO_DO` sans consommation.
  - `leTesteurNOuvrePasLEtabli` : `onItemUseFirst` sur l'établi rend un succès, `containerMenu` reste celui de l'inventaire.
  - `lePosteSeRechargeSurUneMachine` : réserve de 1000 FE de la machine → poste à 1000 FE, machine à 0 ; capability du poste : `receiveEnergy` plafonné à la capacité, `extractEnergy` à 0.
- [ ] **Step 2: Constater l'échec**, `./gradlew runGameTestServer`.
- [ ] **Step 3: Implémenter**, avec un modèle `item/generated` et une texture provisoire par objet si besoin (les finales arrivent en tâche 6).
- [ ] **Step 4: Vérifier** par la ligne « All N required tests passed ».
- [ ] **Step 5: Commit et push.**

### Task 3: Débris d'avion

**Files:**
- Create: `salvage/SalvageBlock.java`, `SalvageRegistry.java` ; `data/oneforallsurvival/loot_tables/blocks/debris_{fuselage,wiring,instruments,battery,cabin}.json` ; `data/oneforallsurvival/tags/items/salvage_tools.json` ; `data/minecraft/tags/blocks/mineable/pickaxe.json` ; `dev/DebrisGameTests.java`
- Modify: `OneForAllSurvival.java`, `BenchRegistry.java` (onglet), langues

**Interfaces:**
- Produces: blocs et objets `debris_fuselage`, `debris_wiring`, `debris_instruments`, `debris_battery`, `debris_cabin` ; objets `scrap_metal`, `aviation_wire`, `instrument_core`, `battery_cell`, `incomplete_instrument_core`, `incomplete_battery_cell` ; `SalvageRegistry.tabItems()`.

`SalvageBlock` : `getDestroyProgress` rend 0 si l'objet en main n'est pas dans `salvage_tools`, sauf en créatif ; `strength(3.0F, 1200.0F)` ; `PushReaction.BLOCK`. Pas de `requiresCorrectToolForDrops` : le blocage du cassage suffit, et une perceuse de Create rend le butin. Le tag `salvage_tools` contient `#minecraft:pickaxes` et `create:wrench` en `required: false`.

Butin, tables à objets vanilla et du mod seulement :
| Bloc | Butin |
|---|---|
| fuselage | `scrap_metal` 2-4, `iron_nugget` 3-6 |
| câblage | `aviation_wire` 2-4, `copper_ingot` 0-1 |
| instruments | `instrument_core` à 35 %, `scrap_metal` 1-2, `glass_pane` 0-1, `redstone` 1-2 |
| batterie | `battery_cell` à 50 %, `scrap_metal` 1-2, `redstone` 1-3 |
| cabine | `leather` 1-2, `white_wool` 0-2, `string` 1-3, `scrap_metal` 0-1 |

- [ ] **Step 1: Écrire `DebrisGameTests`** :
  - `sansOutilLeDebrisNeSeCassePas` : `getDestroyProgress` à 0 main vide et avec une pelle ; supérieur à 0 avec une pioche.
  - `chaqueDebrisDonneSonButinPuisDisparait` : pour les cinq, `gameMode.destroyBlock` avec pioche → air, au moins un `ItemEntity` à proximité, aucun n'est le bloc lui-même.
  - `uneExplosionNeDetruitPasLeDebris` : `level.explode` avec interaction `TNT` à 2 blocs, bloc présent.
- [ ] **Step 2: Constater l'échec, implémenter, vérifier** par la ligne « All N required tests passed ».
- [ ] **Step 3: Commit et push.**

### Task 4: Eau salée et corrosion

**Files:**
- Create: `coast/CoastZones.java`, `CoastSavedData.java`, `CoastDetector.java`, `CoastCommands.java` ; `src/test/java/dev/oneforallsurvival/coast/CoastZonesTest.java` ; `dev/CoteGameTests.java`
- Modify: `machine/ElectricMachineBlockEntity.java` (`isCoastal`), `MachineConfig.java`, `OneForAllSurvival.java`, langues

**Interfaces:**
- Produces:
  - `CoastZones` (pur) : `boolean add(String id, ResourceLocation dimension, BlockPos a, BlockPos b)` refusant un doublon, `boolean remove(String id)`, `boolean hasZones(ResourceLocation dimension)`, `boolean contains(ResourceLocation dimension, BlockPos)`, `List<Zone> list()`, `int version()`.
  - `CoastDetector.isCoastal(ServerLevel, BlockPos)`, réutilisé par le dessalinisateur du lot 3 : si la dimension a des zones, seules elles comptent ; sinon, source d'eau à `MachineConfig.seaLevel()` (63) dans un disque de `MachineConfig.coastRadius()` (16). Jamais de chargement de chunk : colonnes non chargées ignorées. Cache par dimension et colonne, 6000 ticks, 200 ticks si une colonne a été ignorée, vidé quand `version()` change.
  - `/coast zone add <id> <from> <to>`, `/coast zone remove <id>`, `/coast zone list`, `/coast check [pos]`, niveau 2.
- Le socle lit `isCoastal` une fois tous les 200 ticks au plus et applique `wearPerTick(..., coastal, isProtected, coastalMultiplier)`.

- [ ] **Step 1: Écrire `CoastZonesTest`** : boîte quelle que soit l'ordre des coins, bornes incluses, autre dimension exclue, doublon refusé, `version` qui change, aller-retour NBT par `CoastSavedData` si la conversion est exposée en pur.
- [ ] **Step 2: Écrire `CoteGameTests`**, sans dépendre du Y réel du gabarit, en posant la source au Y `MachineConfig.seaLevel()` converti en coordonnées absolues et en vidant le cache par une méthode `CoastDetector.clearCache()` :
  - `uneSourceProcheRendCotier`, `uneEauCouranteNeComptePas`, `uneSourceHorsRayonNeCompte`.
  - `uneZoneExclutLeLac` : source proche et zone ailleurs → faux ; position dans la zone → vrai ; zone retirée → vrai par la source.
  - `surLaCoteLaMachineSUseDeuxFoisPlusVite`, `uneMachineProtegeeIgnoreLaCorrosion` : deux machines côte à côte, écart d'usure mesuré ; le test nettoie ses zones et ses blocs.
- [ ] **Step 3: Constater l'échec, implémenter, vérifier** `./gradlew test` et « All N required tests passed ».
- [ ] **Step 4: Commit et push.**

### Task 5: Bain galvanique

**Files:**
- Create: `repair/GalvanicBathBlock.java`, `GalvanicBathBlockEntity.java`, `GalvanicBathMenu.java`, `GalvanicRecipe.java`, `repair/client/GalvanicBathScreen.java`, `repair/client/RepairClient.java` ; `data/oneforallsurvival/recipes/galvanic/{fuse,coil,bearing,cleaning_kit}.json` ; `data/oneforallsurvival/loot_tables/blocks/galvanic_bath.json` ; `dev/BainGameTests.java`
- Modify: `RepairRegistry.java`, `bench/compat/BenchJeiPlugin.java`, langues

**Interfaces:**
- Consumes: tags `parts/*`, objets `treated_*`, tag `forge:ingots/zinc`.
- Produces: `RecipeType`/`RecipeSerializer` `oneforallsurvival:galvanic_bath`, JSON `{ "type", "ingredient", "zinc", "zinc_count", "result", "ticks", "energy_per_tick" }` ; valeurs de départ `zinc` = `#forge:ingots/zinc`, `zinc_count` 1, `ticks` 200, `energy_per_tick` 20. Sans Create, le tag zinc est vide : les GameTests utilisent une recette de `src/devcontent/resources` payée en `minecraft:iron_ingot` et une pièce d'essai.

`GalvanicBathBlockEntity extends ElectricMachineBlockEntity`, capacité 4000 FE, 200 FE reçus. Cases : 0 pièce, 1 zinc, 2 sortie. `wantsToWork` : recette trouvée, zinc suffisant, sortie compatible. Pièce et zinc se prélèvent à la fin seulement. Progression gardée sans courant et en panne, remise à zéro si la pièce change. Capability objets : dessous extrait la sortie, dessus insère la pièce, côtés insèrent le zinc. `onRemove` jette le contenu. Le rendement réduit et l'usure viennent du socle.

Écran : trois cases, flèche de progression, barre de FE, état en toutes lettres, dessinés dans le style de `EngineerBenchScreen`, sans autre dépendance. Catégorie JEI « Bain galvanique » ajoutée au plugin existant, le mod restant chargeable sans JEI.

- [ ] **Step 1: Écrire `BainGameTests`** :
  - `leBainTraiteUnePieceEtPaieSonZinc` : au terme de `ticks`, sortie = pièce traitée, entrée et zinc diminués de un, FE consommé.
  - `sansCourantLaProgressionAttend`, `sansZincRienNeDemarre`, `enPanneRienNeSePerd`.
  - `retirerLaPieceNeCouteRien` : retrait à mi-parcours, zinc intact, progression à 0.
  - `casserLeBainRendSonContenu` : contenu retrouvé en `ItemEntity`, plus le bloc lui-même.
  - `lesEntonnoirsRespectentLesFaces` : `IItemHandler` du dessous n'insère pas et n'extrait que la sortie ; dessus refuse le zinc.
- [ ] **Step 2: Constater l'échec, implémenter, vérifier** la ligne « All N required tests passed ». Avec `-PwithPack`, vérifier en plus que les quatre recettes de `galvanic/` sont chargées (`PackGameTests`).
- [ ] **Step 3: Commit et push.**

### Task 6: Recettes, visuels finaux

**Files:**
- Create: `data/oneforallsurvival/recipes/*.json` du matériel, `data/oneforallsurvival/recipes/sequenced/{instrument_core,battery_cell}.json`, `data/oneforallsurvival/tags/items/wire.json`, textures et modèles sous `assets/oneforallsurvival/`, script de génération `tools/gen_textures_lot2.py`
- Modify: `tags/items/parts/fuse.json`, `dev/PackGameTests.java`, langues

**Interfaces:**
- Consumes: tous les objets et blocs des tâches 2, 3 et 5. Les recettes de table dont le résultat vient du mod partent d'elles-mêmes à l'établi (`BenchCatalog`, `managedMods` contient déjà le mod).

Tag `wire` : `aviation_wire`, et `createaddition:copper_wire` en `required: false`. Tag `parts/fuse` : ajouter `light_bulb_addon_create_crafts_and__addions:fuse_16a` en `required: false`. Toute recette citant un objet d'un autre mod porte une condition `forge:mod_loaded`. Avant d'écrire la recette du testeur, chercher dans le jar de Crafts & Additions une recette produisant `createaddition:multimeter` ; s'il y en a une, le multimètre remplace l'électron.

| Résultat | Ingrédients |
|---|---|
| `fuse` ×2 | `glass_pane`, `#wire`, 2 `iron_nugget` |
| `coil` | 4 `#wire`, `iron_ingot` |
| `bearing` | `create:andesite_alloy`, 4 `iron_nugget`, `#forge:plates/iron` |
| `cleaning_kit` | `create:sand_paper`, `#minecraft:wool`, `string` |
| `tester` | `instrument_core`, `create:electron_tube`, `#wire`, `#forge:plates/brass` |
| `welder` | `battery_cell`, 2 `#forge:plates/brass`, `#wire`, `blaze_powder` |
| `galvanic_bath` | `cauldron`, `create:brass_casing`, 2 `#wire`, 2 `#forge:plates/zinc` |
| `instrument_core` (assemblage séquencé, 3 boucles) | base `#forge:plates/brass` ; `create:electron_tube`, `#wire`, pressage ; transition `incomplete_instrument_core` |
| `battery_cell` (assemblage séquencé, 3 boucles) | base `#forge:plates/zinc` ; `#forge:plates/copper`, `redstone`, pressage ; transition `incomplete_battery_cell` |

Visuels, style bricolage et récupération de `engineer_bench_*.png`, palette rouillée et cohérente, en 16×16 : tous les objets ; cinq faces de débris (tôle rivetée, faisceau de câbles, cadrans, éléments de batterie, sièges déchirés) ; bain galvanique à quatre états (arrêt, marche, sans courant, panne) en bloc non plein s'il reste lisible, avec lampe reprise des textures de l'établi. Générer les PNG par un script Python/PIL reproductible, sans aucune ressource téléchargée. Pièce traitée = pièce normale avec reflet zingué bleuté.

- [ ] **Step 1: Ajouter à `PackGameTests`** : `leMaterielDuMetierEstALEtabliSeulement` (chaque objet du tableau hors assemblage séquencé dans `BenchCatalog`, et aucune recette `CRAFTING` correspondante dans le `RecipeManager`), `lesPiecesRaresSeRefabriquent` (recette `sequenced/*` présente).
- [ ] **Step 2: Constater l'échec** en `-PwithPack`, écrire les données, vérifier la ligne « All N required tests passed » avec et sans pack.
- [ ] **Step 3: Vérifier que chaque clé de langue et chaque modèle existe** : aucune ligne `Missing model`, `Unable to load model` ni `missing texture` pour le mod dans `run-gametest/logs/debug.log` ou un lancement de client de capture, et capture PNG de chaque objet et bloc dans un monde, regardée.
- [ ] **Step 4: Commit et push.**

### Task 7: Preuve de bout en bout, film et vérification

**Files:**
- Create: `dev/FilmReparation.java`, `docs/verifications/lot-2.md`
- Modify: `build.gradle` (lancement `demoReparationFilm`), `dev/E2eClient.java`, `tasks/todo.md`

**Interfaces:**
- Consumes: `FilmCapture`, le schéma de `FilmEtabli` et `E2eClient`.

Film, 1280×656 avec bandeau de légende, comme `FilmEtabli` : machine neuve sous le testeur, usure accélérée par la commande de développement ou le test, fumée sous 20 %, panne avec flammes, testeur qui nomme la pièce, mauvaise pièce refusée, bonne pièce, machine repartie ; recharge du poste ; démontage d'un débris à la pioche et refus à la main ; bain galvanique avec pièce traitée ; machine protégée. Encodage par `MfEncoder.cs` déjà présent dans `C:\Users\killi\AppData\Local\Temp\claude\9509ad0cac247e05\a54dc472-a67f-4b97-9c4b-0266c8c36327\scratchpad`, compilé par le `csc.exe` système, sans rien télécharger. Sortie : `run-demo/lot-2-reparation.mp4`.

E2E, serveur dédié avec le pack et vrai client : ajouter à `E2eClient` une étape où le client pose une machine (établi), `/role set Dev ingenieur`, lit l'état au testeur, reçoit panne et pièces par commande, répare par de vrais paquets et lit le résultat. Si la connexion se bloque, relever `jstack` et l'état du socket avant de fermer, comme décrit dans `docs/verifications/lot-1.md`.

- [ ] **Step 1: Écrire le film, le lancer, regarder au moins dix images**, corriger jusqu'à ce que chaque étape se voie.
- [ ] **Step 2: Lancer l'E2E** : `./gradlew runServer -PwithPack` puis `./gradlew runE2eClient -PwithPack`, lire `[e2e] bilan` dans `run-e2e/logs/latest.log`.
- [ ] **Step 3: Écrire `docs/verifications/lot-2.md`** sur le modèle du lot 1 : preuves, nombre de tests lus, liste « Non prouvé » (équilibrage réel, multijoueur, Citoyen sur serveur dédié, FE réel d'un autre mod dans le poste, câblage réel, JEI du bain s'il n'a pas été vu). Cocher le lot 2 dans `tasks/todo.md`.
- [ ] **Step 4: Vérifier le jar** : `./gradlew build`, aucune classe `dev/oneforallsurvival/dev` dans `build/libs/*.jar`.
- [ ] **Step 5: Commit et push sur `lot-2`. Ne pas fusionner dans `main` : Killian décidera après avoir vu le film.**
