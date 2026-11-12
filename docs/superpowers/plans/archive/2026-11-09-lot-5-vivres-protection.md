# Lot 5 : vivres et protection — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter la couveuse, la cuisine de cantine (marmite et plaque électrique) et la clôture électrique, pour qu'une coupure menace une couvée, un repas collectif et la protection de la ville.

**Architecture:** Les quatre machines héritent de `ElectricMachineBlockEntity`, donc de l'usure, des pannes, de la casse sans perte d'état et du testeur. Les règles chiffrées (œufs configurés, parcours de la ligne, coût, blessure non mortelle) sont de la logique pure testée en JUnit. La marmite réutilise les recettes `farmersdelight:cooking` ; la plaque rejoint le tag de chaleur de Farmer's Delight. La clôture n'a pas de block entity : l'électrificateur parcourt la ligne et traite les contacts. Aucun mixin.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, Farmer's Delight 1.20.1-1.3.4 (`implementation`), API JEI en `compileOnly`.

**Spec:** `docs/superpowers/specs/archive/2026-11-06-features-lost-island-design.md`, section 4 (machine électrique), section 5 features 12, 13, 16, section 7. Les autres cahiers ne changent pas ces features ; le cahier du médecin (feature 35) ajoutera plus tard la répartition First Aid de nos dégâts sur les bras.

## Global Constraints

- `gradle.properties` en ASCII pur. Aucun `${` dans `mods.toml` et `pack.mcmeta` hors propriété définie.
- Mod requis côté client et serveur. Aucune classe cliente atteignable depuis un chemin serveur : écrans dans un paquet `client`, enregistrés par un abonné `Dist.CLIENT`.
- Textes visibles dans `fr_fr.json`, avec repli `en_us.json`. Ne jamais écrire le nom « SolarTown » : le serveur s'appelle Lost Island RP.
- Rien de `devcontent` dans le mod livré. Registres : `DeferredRegister`, jamais un initialiseur statique.
- Tous les dégâts du mod passent par une source de dégâts normale, sans modifier directement les points de vie (First Aid).
- Recettes, durées et listes portées par des données (JSON, tags, config `SERVER`), avec les valeurs de ce plan comme valeurs de départ.
- Une zone déchargée n'est pas une coupure : la machine se fige, sans usure ni perte. Le contenu d'une machine en panne n'est jamais perdu, couveuse comprise. La couveuse est la seule machine pour laquelle une coupure détruit le contenu.
- Preuve des GameTests : lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie de Gradle. `./gradlew --offline runGameTestServer` sans le pack, `-PwithPack` pour ce qui exige Create. Une `ConcurrentModificationException` au démarrage est intermittente : relancer.
- Tout GameTest qui exige qu'une machine ait tourné porte `setupTicks = 20`. Gabarit : `test/plancher`.
- Aucun téléchargement hors dépendances déjà en cache. N'arrêter que des PID exacts lancés par soi. Jamais `Set-Content -Encoding utf8` sur une config Forge. `run/options.txt` ne se modifie jamais.
- Travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, par `git commit -- <chemins>` (jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/`), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Pendant qu'un agent travaille dans le dossier, ne rien laisser dans l'index (`tasks/lessons.md`).
- Un client à la fois, refermé par le script lui-même quel que soit l'écran atteint.

## Review Focus

1. Coupure de la couveuse à cheval sur un arrêt du serveur : le temps éteint ne compte pas, le compteur est relu. Testé en tâche 1 (`laCoupureSurvitAuRechargement`).
2. Couveuse en panne et sans courant : la couvée reste, et le compteur de coupure ne bouge pas. Testé en tâche 1 (`enPanneLaCouveeResteMemeSansCourant`).
3. Marmite : sortie presque pleine, récipients insuffisants, ingrédient avarié, seau rendu par un ingrédient : rien créé, rien perdu. Testé en tâche 3.
4. Clôture : un joueur ou un animal à faible vie n'en meurt pas, un joueur en créatif n'est pas touché, deux électrificateurs ne doublent pas le choc. Testé en tâche 4.
5. Ligne : boucle, ligne plus longue que 64 blocs, bloc retiré au milieu, chunk non chargé (jamais de chargement forcé). Testé en tâche 4.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Durée d'incubation | 20 minutes de marche, par œuf, en config | Le serveur ne tourne qu'en session ; à équilibrer |
| Début de la coupure | Le compteur d'une minute part quand la réserve de la couveuse est vide (2 000 FE, soit ≈ 12 s) | C'est l'état « sans courant » du socle |
| Couvée perdue | Œufs détruits, fumée et son, décompte visible dans le menu | La spec n'admet cette perte que pour cette machine ; le joueur doit la voir venir |
| Éclosion sans place | L'œuf attend, sans se perdre, et l'animal sort dès qu'il y a de la place | « À coup sûr » |
| Portions de la marmite | Trois fois le résultat pour un seul jeu d'ingrédients, 30 FE par tick, même durée qu'un Cooking Pot | « Plus de portions » ; à équilibrer |
| Récipients | La marmite exige d'avance un bol par portion, payés en fin de cuisson avec les ingrédients | Sinon une fournée sans bols resterait bloquée ; plus simple que la case d'attente du Cooking Pot |
| Expérience | La marmite n'en donne pas | Évite un gain triplé |
| Plaque | Consomme et s'use dès qu'un ustensile de Farmer's Delight est posé dessus, même vide | Une cuisinière oubliée allumée coûte du courant ; rien posé dessus : à l'arrêt |
| Mobs de la clôture | Les mobs hostiles peuvent en mourir ; animaux et autres mobs passifs s'arrêtent à 4 points de vie, comme les joueurs | « Blesse légèrement », sans décimer un enclos (échec cité par le cahier de l'agriculteur) |
| Portée de la ligne | Clôtures contiguës par une face (y compris en hauteur), les 64 plus proches de l'électrificateur ; relue toutes les 2 s | Cahier : 64 au plus ; pas d'événement à maintenir |
| Connexion visuelle | La clôture se raccorde aux clôtures non en bois, pas à celles en bois | Règle de `FenceBlock` ; une clôture ordinaire n'est pas électrifiée |
| Végétation | Tag `oneforallsurvival:fence_vegetation` (feuilles, herbes, fleurs, pousses, lianes) lu sur 4 côtés et au-dessus de chaque bloc | Réglable sans recompiler |
| JEI | La marmite s'ajoute comme catalyseur de la catégorie Cuisson de Farmer's Delight ; pas de catégorie pour la couveuse | Réutilise l'existant |

## Fichiers

| Fichier | Rôle |
|---|---|
| `food/IncubatorEggs.java` | Lecture de la liste œuf → animal |
| `food/IncubatorBlock.java`, `IncubatorBlockEntity.java`, `IncubatorMenu.java`, `client/IncubatorScreen.java` | Couveuse |
| `food/HotPlateBlock.java`, `HotPlateBlockEntity.java` | Plaque électrique |
| `food/CanteenPotBlock.java`, `CanteenPotBlockEntity.java`, `CanteenPotMenu.java`, `client/CanteenPotScreen.java` | Marmite |
| `food/FoodOutput.java`, `food/FacedSlots.java` | Extraits du séchoir : test d'avarié, sortie datée, faces d'accès |
| `fence/FenceLine.java`, `FenceRules.java` | Règles pures |
| `fence/FenceRegistry.java`, `FenceConfig.java`, `ElectricFenceBlock.java`, `ElectrifierBlock.java`, `ElectrifierBlockEntity.java` | Clôture |
| `src/devcontent/.../{Couveuse,Plaque,Marmite,Cloture}GameTests.java`, `E2eProtection.java`, `FilmProtection.java` | Preuves |
| `tools/gen_textures_lot5.py` | Textures reproductibles |

---

### Task 1: Couveuse

**Files:**
- Create: `food/IncubatorEggs.java`, `IncubatorBlock.java`, `IncubatorBlockEntity.java`, `IncubatorMenu.java`, `client/IncubatorScreen.java`, `src/test/java/dev/oneforallsurvival/food/IncubatorEggsTest.java`, `src/devcontent/.../CouveuseGameTests.java`.
- Modify: `FoodRegistry.java` (`INCUBATOR`, `INCUBATOR_ITEM`, `INCUBATOR_BE`, `INCUBATOR_MENU`, `tabItems`), `FoodConfig.java`, `client/FoodClient.java`, langues.

**Interfaces:**
- Consumes: `ElectricMachineBlockEntity` (`wantsToWork`, `energyPerTick`, `workTick`, `afterTick(Level, MachineState)`), `ElectricMachineBlock`, modèle de `DryerBlock` et `DryerMenu`.
- Produces :
  - `IncubatorEggs.parse(List<String>) : Map<String, String>` (entrées `item=entité`, entrée mal formée ignorée et journalisée) ; `IncubatorEggs.entityFor(ItemStack) : @Nullable EntityType<?>`, avec cache sur l'identité de la liste de config.
  - `FoodConfig.incubatorEggs()` (départ `minecraft:egg=minecraft:chicken`, `minecraft:turtle_egg=minecraft:turtle`), `incubatorHatchTicks()` (`incubatorHatchMinutes`, 20), `incubatorOutageTicks()` (`incubatorOutageSeconds`, 60), `incubatorEnergy()` (8, borné à `MAX_RECEIVE`).
  - `IncubatorBlockEntity` : `CAPACITY = 2000`, `MAX_RECEIVE = 40`, `SLOTS = 4` (une place par œuf), `inventory()`, `data()`, `progress(int slot)`, `outageTicks()` ; constantes `DATA_*` pour 4 progressions, durée, énergie, capacité, état, coupure, durée de coupure.

Règles : `wantsToWork` : au moins un œuf dont l'animal est connu. Chaque `workTick` avance chaque œuf ; une case vidée repart de zéro, détecté au tick. À maturité : `type.create`, `finalizeSpawn` en `MobSpawnType.BREEDING`, `AgeableMob.setBaby(true)`, position au-dessus de la machine puis sur ses 4 côtés si `level.noCollision` ; sans place, l'œuf reste mûr et réessaie toutes les 20 ticks. `afterTick` : `NO_POWER` incrémente la coupure, `RUNNING` la remet à zéro, `FAULT` la laisse telle quelle, plus aucun œuf la remet à zéro. Au seuil dépassé : cases vidées, `SMOKE`, son d'extinction. Sauvegarde : progressions (`progress`, tableau d'entiers) et coupure (`outage`). Faces : le dessus insère, aucune face n'extrait. Le bloc jette ses œufs quand il disparaît.

- [ ] **Step 1: Écrire `IncubatorEggsTest`** : `lisLesEntreesValides`, `ignoreUneEntreeMalFormee`, `listeVideDonneRien`.

```java
assertEquals(Map.of("minecraft:egg", "minecraft:chicken"), IncubatorEggs.parse(List.of("minecraft:egg=minecraft:chicken", "n'importe quoi")));
```

- [ ] **Step 2: Écrire `CouveuseGameTests`** : `unOeufDePouleDonneUnPoussin`, `uneTortueAussi` (bébé, `isBaby()` vrai), `quatreOeufsEclosent`, `unObjetQuelconqueEstRefuse`, `sansCourantLaProgressionReste`, `uneCoupureCourteNePerdRien` (59 s d'état `NO_POWER` puis courant : quatre œufs présents, compteur à 0), `uneCoupureDePlusDUneMinutePerdLaCouvee`, `enPanneLaCouveeResteMemeSansCourant`, `laCoupureSurvitAuRechargement` (compteur relu après `saveWithoutMetadata`/`load`), `laProgressionSurvitAuRechargement`, `sansPlaceLOeufAttend` (machine couverte puis libérée : l'animal sort, l'œuf part une seule fois), `casseElleRendSesOeufs`, `leClicMajusculeFonctionne`. La maturité et la coupure s'atteignent par la sauvegarde, comme `SechoirGameTests.presqueFini`.
- [ ] **Step 3: Lancer, constater l'échec** de compilation ou des tests.
- [ ] **Step 4: Implémenter.** Écran : une barre par œuf, énergie, état, et le décompte « couvée perdue dans N s » uniquement en coupure.
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, lire** « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(food): couveuse`.

### Task 2: Plaque électrique

**Files:**
- Create: `food/HotPlateBlock.java`, `HotPlateBlockEntity.java`, `src/main/resources/data/farmersdelight/tags/blocks/heat_sources.json` (`"replace": false`, `oneforallsurvival:hot_plate`), `src/devcontent/.../PlaqueGameTests.java`.
- Modify: `FoodRegistry.java` (`HOT_PLATE`, `HOT_PLATE_ITEM`, `HOT_PLATE_BE`, `tabItems`), `FoodConfig.java` (`hotPlateEnergy()`, 10), langues.

**Interfaces:**
- Consumes: `HeatableBlockEntity`, `ModTags.Blocks.HEAT_CONDUCTORS` de Farmer's Delight. Relevé dans le jar : `isHeated` lit `BlockStateProperties.LIT` si la source le porte, et accepte un bloc conducteur entre la source et l'ustensile.
- Produces : `HotPlateBlock` porte `STATE` et `LIT` ; `HotPlateBlockEntity` : `CAPACITY = 2000`, `MAX_RECEIVE = 60`.

Règles : `wantsToWork` : un `HeatableBlockEntity` au-dessus, ou un bloc du tag conducteur au-dessus avec un `HeatableBlockEntity` encore au-dessus. `workTick` ne fait rien. `afterTick` pose `LIT` égal à `tickState == RUNNING` sur l'état courant du bloc, seulement s'il change. Lumière 7 si allumée. Aucun dégât ni feu. Pas de menu.

- [ ] **Step 1: Écrire `PlaqueGameTests`** : `alimenteeElleChauffeLaMarmite` (`CookingPotBlockEntity.isHeated()` vrai), `sansCourantElleNeChauffePlus`, `enPanneElleNeChauffePlus`, `sansUstensileElleResteALarret` (ni consommation ni usure), `elleChauffeAussiLaPoele`, `uneVraieRecetteCuit` (`beef_stew` dans un Cooking Pot posé dessus), `lEtatSurvitAuRechargement`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(food): plaque electrique`.

### Task 3: Marmite de cantine

**Files:**
- Create: `food/FoodOutput.java`, `FacedSlots.java`, `CanteenPotBlock.java`, `CanteenPotBlockEntity.java`, `CanteenPotMenu.java`, `client/CanteenPotScreen.java`, `src/devcontent/.../MarmiteGameTests.java`.
- Modify: `DryerBlockEntity.java` (utilise `FoodOutput` et `FacedSlots`, comportement inchangé), `FoodRegistry.java` (`CANTEEN_POT`, `CANTEEN_POT_ITEM`, `CANTEEN_POT_BE`, `CANTEEN_POT_MENU`, `tabItems`), `FoodConfig.java`, `client/FoodClient.java`, `bench/compat/BenchJeiPlugin.java`, langues.

**Interfaces:**
- Consumes: `CookingPotRecipe` (`matches(RecipeWrapper, Level)`, `getResultItem(RegistryAccess)`, `getOutputContainer()`, `getCookTime()`, `INPUT_SLOTS = 6`), `ModRecipeTypes.COOKING`, `FDRecipeTypes.COOKING`, `FoodEvents.merge`, `FoodEvents.sameWithoutStamp`.
- Produces :
  - `FoodOutput.isSpoiled(Level, ItemStack) : boolean`, `FoodOutput.fits(ItemStack out, ItemStack result) : boolean`, `FoodOutput.place(IItemHandlerModifiable, int slot, ItemStack result, Level) : void`.
  - `FacedSlots(IItemHandlerModifiable, int from, int to, boolean canInsert, boolean canExtract)`.
  - `FoodConfig.canteenPotEnergy()` (30, borné à `MAX_RECEIVE`), `canteenPotPortions()` (3, entre 2 et 16).
  - `CanteenPotBlockEntity` : `CAPACITY = 6000`, `MAX_RECEIVE = 200`, `SLOT_INPUT_FIRST = 0`, `SLOT_CONTAINER = 6`, `SLOT_OUTPUT = 7`, `SLOTS = 8`, `inventory()`, `data()`, `progress()`, `containersNeeded()`.

Règles : recette cherchée par `RecipeWrapper` sur les 6 premières cases, en cache comme au séchoir (gestionnaire de recettes compris). Le récipient est `getOutputContainer()`, sinon `result.getCraftingRemainingItem()`. Une fournée donne `count × portions` ; elle exige autant de récipients, qui se paient en fin de cuisson avec un seul jeu d'ingrédients. `wantsToWork` : recette, aucun ingrédient avarié, récipients présents, sortie assez grande. Progression gardée sans courant, en panne, faute de bols, sortie pleine ; remise à zéro si l'identifiant de la recette change, vu au tick. Les restes d'ingrédients (seau, bouteille) sont jetés au-dessus du bloc, comme `ejectIngredientRemainder`. Faces : dessus → cases d'entrée, côtés → récipient, dessous → extraction de la sortie. Écran : six cases, récipient, sortie, barre, et « Récipients requis : N » seulement s'il en manque. JEI : `registration.addRecipeCatalyst(new ItemStack(FoodRegistry.CANTEEN_POT_ITEM.get()), FDRecipeTypes.COOKING)`.

- [ ] **Step 1: Écrire `MarmiteGameTests`** : `leRagoutDonneTroisPortions` (`beef_stew`, trois bols payés, un jeu d'ingrédients payé), `sansBolsElleAttend`, `avecDeuxBolsSurTroisElleAttend`, `sortieTropPleineElleAttend`, `leCourantCoupeGardeLaProgression`, `laRecetteChangeeRepartDeZero`, `leClicNeRemetPasLaProgressionAZero`, `unIngredientDateEstAccepte`, `unIngredientAvarieEstRefuse`, `leResteDUnIngredientEstRendu` (une recette avec un seau de lait : le seau tombe), `laProgressionSurvitAuRechargement`, `casseElleRendSonContenu`, `leClicMajusculeFonctionne`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Extraire `FoodOutput` et `FacedSlots` du séchoir, lancer `SechoirGameTests` :** ils restent verts sans modification.
- [ ] **Step 4: Implémenter la marmite et le catalyseur JEI.**
- [ ] **Step 5: Lancer, lire** « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(food): marmite de cantine`.

### Task 4: Clôture électrique

**Files:**
- Create: `fence/FenceLine.java`, `FenceRules.java`, `FenceConfig.java`, `FenceRegistry.java`, `ElectricFenceBlock.java`, `ElectrifierBlock.java`, `ElectrifierBlockEntity.java`, `data/oneforallsurvival/damage_type/electric_fence.json` (`exhaustion` 0, `message_id` `oneforallsurvival.electric_fence`, `scaling` `never`), `data/minecraft/tags/damage_type/bypasses_armor.json` (`"replace": false`), `data/oneforallsurvival/tags/blocks/fence_vegetation.json`, `data/minecraft/tags/blocks/fences.json` (`"replace": false`), `src/test/java/dev/oneforallsurvival/fence/{FenceLineTest,FenceRulesTest}.java`, `src/devcontent/.../ClotureGameTests.java`.
- Modify: `OneForAllSurvival.java` (`FenceRegistry.register`, config `SERVER` `oneforallsurvival-fence.toml`), l'onglet créatif, langues (`death.attack.oneforallsurvival.electric_fence`).

**Interfaces:**
- Produces :
  - `FenceLine.scan(BlockPos start, Predicate<BlockPos> isFence, int max) : List<BlockPos>` : parcours en largeur sur les 6 faces, ordre de `Direction.values()`, sans compter `start`, chaque bloc une fois.
  - `FenceRules.energyPerTick(int fences, int vegetation, boolean storm, double base, double perFence, double perVegetation, double stormMultiplier, int max) : int` : 0 si aucune clôture, sinon `ceil((base + fences × perFence + vegetation × perVegetation) × (storm ? stormMultiplier : 1))`, borné à `max`.
  - `FenceRules.damageFor(float health, float amount, float floor, boolean lethal) : float` : `amount` si `lethal`, sinon `max(0, min(amount, health - floor))`.
  - `FenceConfig` : `maxLength()` 64 (entre 1 et 64), `baseEnergy()` 2, `perFenceEnergy()` 0.25, `perVegetationEnergy()` 1, `stormMultiplier()` 2, `mobDamage()` 2.0, `playerDamage()` 1.0, `safeHealth()` 4.0, `lethalForPlayers()` faux, `knockback()` 0.6.
  - `FenceRegistry.ELECTRIC_FENCE`, `ELECTRIFIER`, leurs objets, `ELECTRIFIER_BE`, `DAMAGE_TYPE : ResourceKey<DamageType>`, `tabItems()`.
  - `ElectrifierBlockEntity` : `CAPACITY = 4000`, `MAX_RECEIVE = 200`, `line() : List<BlockPos>`, `shock(LivingEntity entity, Vec3 wire) : void`.

Règles : ligne relue toutes les 40 ticks, au premier appel d'`energyPerTick` du tick ; seuls les blocs dont le chunk est chargé comptent, jamais de chargement forcé. `wantsToWork` : ligne non vide. Tous les 10 ticks de travail : entités `LivingEntity` dans la boîte englobante de la ligne, gardées si leur boîte gonflée de 0,08 touche la forme de collision d'une clôture. Exclus : créatif, spectateur, `ArmorStand`, invulnérable. Dégâts : hostile (`Enemy`) `mobDamage`, mortel ; joueur `playerDamage` avec `lethalForPlayers` ; autre vivant `mobDamage` non mortel ; `damageFor` tranche, plancher `safeHealth`, `hurt` seulement si le résultat est positif. Le repoussement s'applique à chaque contact (`knockback` puis `hurtMarked = true`), même sans dégât. Étincelles sur une clôture au hasard toutes les 20 ticks. Sans courant, la clôture est un bloc ordinaire. L'électrificateur doit toucher une clôture par une face.

- [ ] **Step 1: Écrire `FenceLineTest`** : `suitUneLigneDroite`, `sArreteAuTrou`, `neDepassePasLeMaximum`, `gardeLesPlusProches`, `traverseUneBranche`, `neBouclePasSurUnCircuit`, `monteEnHauteur`, `neCompteJamaisLeDepart`, `sansClotureRendVide`.
- [ ] **Step 2: Écrire `FenceRulesTest`** :

```java
assertEquals(0, FenceRules.energyPerTick(0, 0, true, 2, 0.25, 1, 2, 200));
assertEquals(18, FenceRules.energyPerTick(64, 0, false, 2, 0.25, 1, 2, 200));
assertEquals(36, FenceRules.energyPerTick(64, 0, true, 2, 0.25, 1, 2, 200));
assertEquals(23, FenceRules.energyPerTick(64, 5, false, 2, 0.25, 1, 2, 200));
assertEquals(200, FenceRules.energyPerTick(64, 500, true, 2, 0.25, 1, 2, 200));
assertEquals(1f, FenceRules.damageFor(20f, 1f, 4f, false));
assertEquals(0.5f, FenceRules.damageFor(4.5f, 1f, 4f, false));
assertEquals(0f, FenceRules.damageFor(4f, 1f, 4f, false));
assertEquals(0f, FenceRules.damageFor(1f, 1f, 4f, false));
assertEquals(1f, FenceRules.damageFor(1f, 1f, 4f, true));
```

- [ ] **Step 3: Écrire `ClotureGameTests`** : `unZombieEstBlesseEtRepousse`, `sansCourantRienNArrive`, `enPanneRienNArrive`, `unAnimalNeMeurtPas` (vache à 4 PV après plusieurs contacts), `unJoueurFaibleNeMeurtPas` et `unJoueurEstBlesse` (`makeMockPlayer` n'est pas dans le monde : appeler `shock` directement), `lOrageCouteDavantage`, `laVegetationCouteDavantage`, `aucuneClotureAucuneConsommation`, `uneClotureOrdinaireNEstPasElectrifiee`, `unBlocRetireCoupeLaSuite` (après 40 ticks), `deuxElectrificateursNeDoublentPasLeChoc`, `laClotureSeRelieAuxClotures`, `etatSurvitAuRechargement`.
- [ ] **Step 4: Lancer, constater l'échec.**
- [ ] **Step 5: Implémenter.**
- [ ] **Step 6: Lancer `./gradlew --offline test runGameTestServer`, lire** « All N required tests passed ».
- [ ] **Step 7: Commit** `feat(fence): cloture electrique`.

### Task 5: Recettes du matériel et visuels finaux

**Files:**
- Create: recettes de table `incubator`, `hot_plate`, `canteen_pot`, `electric_fence`, `electrifier` (conditions `forge:mod_loaded`, comme `dryer.json`) ; tables de butin des cinq blocs ; états de blocs, modèles, textures ; `tools/gen_textures_lot5.py`.
- Modify: `DemoVisuels`, `PackGameTests`, langues, tag `mineable/pickaxe`.

Les cinq objets se fabriquent à l'établi d'ingénieur : `PackGameTests` vérifie qu'aucune recette de table ne les donne une fois le pack chargé. Ingrédients : plaques et tubes de Create, pièces du lot 2 (bobine, fusible, tag `oneforallsurvival:wire`) ; `createaddition:barbed_wire` pour la clôture, dont une recette donne 8 blocs ; un Cooking Pot pour la marmite.

Visuels, style bricolage et récupération, palette cohérente avec les lots 2 à 4, en 16 x 16, script Python/PIL reproductible, aucun fichier téléchargé : quatre états de lampe pour chaque machine ; couveuse en caisse isolée à vitre et lampe chauffante ; marmite en grand fût de tôle avec couvercle ; plaque à résistance rouge quand elle est allumée (`lit`) ; électrificateur en boîtier à isolateurs ; clôture en poteaux et fil barbelé, modèles `fence_post`/`fence_side`/`fence_inventory`. Fonds d'écran en cohérence avec `BenchTheme`.

- [ ] **Step 1: Écrire les tests de `PackGameTests`.**
- [ ] **Step 2: Lancer avec `-PwithPack`, constater l'échec.**
- [ ] **Step 3: Produire recettes, butins, modèles, textures.**
- [ ] **Step 4: Lancer `runDemoVisuels`**, ouvrir les captures, vérifier objets, blocs et écrans lisibles.
- [ ] **Step 5: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 6: Commit** `feat(lot5): recettes du materiel et visuels finaux`.

### Task 6: Preuve de bout en bout, film, vérification

**Files:**
- Create: `dev/E2eProtection.java`, `dev/FilmProtection.java`, `docs/verifications/lot-5.md`.
- Modify: `dev/E2eClient.java`, `build.gradle` (`runDemoProtectionFilm`), `wiki/data/electricite.json` (entrées `couveuse`, `cuisine-cantine`, `cloture-electrique` au statut et au format de l'entrée `sechoir`), `tasks/todo.md`.

**E2E sur serveur dédié, avec le pack et First Aid, un seul passage :** couveuse alimentée, œuf mûri par durée raccourcie, poussin vu ; coupure courte sans perte ; `/stop` en pleine coupure, relance : compteur relu ; coupure longue : couvée perdue ; plaque sous un Cooking Pot : ragoût cuit, plus de chaleur sans courant ; marmite : trois portions ; clôture : un zombie blessé et repoussé ; un joueur non opérateur touché plusieurs fois à faible vie reste en vie sous First Aid ; sans courant, plus rien. Le script ferme son client quel que soit l'écran atteint et se termine par `/stop`.

**Film**, monde intégré, durées raccourcies en mémoire et annoncées à l'écran : une éclosion, une couvée perdue, une fournée de cantine, un zombie sur la clôture puis le courant coupé. Filmer avec `FrameRecorder`/`FilmCapture` ou `gfxcapture`, jamais `ddagrab` : aucune fenêtre privée à l'image. Puis envoi dans la conversation.

- [ ] **Step 1: Écrire E2E et film, lancer.** Lire `[e2e] bilan : TOUT PASSE` dans le journal.
- [ ] **Step 2: Rouvrir le film en images**, vérifier chaque scène.
- [ ] **Step 3: Écrire `docs/verifications/lot-5.md`** : prouvé et non prouvé, séparés. Au moins : équilibrage réel, multijoueur, FE d'un autre mod, répartition First Aid (feature 35, à venir), mobs réels dans une longue ligne, coût serveur non mesuré.
- [ ] **Step 4: Mettre le wiki à jour et cocher le lot dans `tasks/todo.md`**, avec ce qui reste ouvert.
- [ ] **Step 5: Commit et push** `test(lot5): E2E et film`, `docs(lot5): preuve du lot 5`.
