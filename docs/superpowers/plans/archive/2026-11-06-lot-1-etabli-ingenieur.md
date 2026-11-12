# Lot 1 : établi d'ingénieur — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire de l'établi d'ingénieur le seul accès aux crafts de table, de tailleur de pierre et de forgeron des mods d'ingénierie, avec une interface complète, une file d'attente annulable et un stock illimité.

**Architecture:** Après le chargement des données, les recettes concernées sortent du `RecipeManager` et entrent dans un catalogue de l'établi ; table, assembleurs mécaniques, recipe book et JEI ne les voient donc plus, sans mixin. File et stock vivent dans une `SavedData` par joueur et avancent à chaque tick du serveur, indépendamment de l'établi. Le serveur décide de tout : l'écran n'envoie que des demandes qu'il revalide.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, `SimpleChannel`, JUnit 5, GameTests.

**Spec:** `docs/superpowers/specs/archive/2026-11-06-features-lost-island-design.md`, section 4 « Établi d'ingénieur ». État du lot précédent : `docs/verifications/lot-0.md`.

## Global Constraints

- `gradle.properties` en ASCII pur. Aucun `${` dans `mods.toml` et `pack.mcmeta` hors propriété définie.
- Mod requis côté client et serveur. Aucune classe cliente atteignable depuis un chemin serveur : tout le code d'écran est dans `dev.oneforallsurvival.bench.client`, enregistré par un abonné `Dist.CLIENT`.
- Textes visibles dans `fr_fr.json`, avec repli `en_us.json`.
- Rien de `devcontent` dans le mod livré. Vérifier `build/libs/*.jar` en fin de lot.
- Preuve des tests : lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie. Les tests qui exigent le pack se lancent avec `-PwithPack`.
- Les blocs d'essai et GameTests vont dans `src/devcontent`. Registres : créer les objets dans le `RegisterEvent` ou un `DeferredRegister`, jamais dans un initialiseur statique.
- Commit et push après chaque tâche vérifiée, fichiers ajoutés par nom, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Visuels finaux compris dans le lot, style bricolage et récupération (spec, décision 5).

## Review Focus

1. Arrêt du serveur en pleine file : tout reprend au même point, ingrédients et progression compris.
2. Annulation avec l'inventaire plein : rien ne disparaît, le surplus va au stock.
3. Commande avec une partie seulement des ingrédients : rien n'est prélevé.
4. `/reload` ou recette retirée alors qu'un craft l'attend : le craft se termine ou s'annule normalement.
5. Paquet forgé par un client : rôle, quantité, recette et distance à l'établi sont revérifiés côté serveur.

Choix de cet auteur à signaler à Killian au compte rendu : la section « Décisions » ci-dessous.

## Décisions prises par ce plan

| Décision | Valeur |
|---|---|
| Quantité | Une commande de N crée N entrées de file. Limite de 64 entrées par joueur, configurable |
| Annulation | Les objets reviennent dans l'inventaire, le surplus va au stock |
| Plusieurs recettes pour un même objet | Une ligne par recette dans la liste, doublons exacts fusionnés |
| Temps | `base + Σ ingrédients (1 s + rareté + moitié du temps de l'ingrédient s'il se fabrique lui-même sur l'établi)`, plafonné à 600 s |
| Vitesse | 1 à la main, 4 alimenté. Consommation 20 FE/t, unique par établi quel que soit le nombre d'ingénieurs |
| Rôle retiré | Les crafts continuent, rien n'est perdu ; le joueur ne peut plus ouvrir l'établi tant qu'il n'est pas redevenu Ingénieur |
| Config | Fichier COMMON, car le catalogue se construit avant le chargement des configs SERVER |

---

## Fichiers (sous `src/main/java/dev/oneforallsurvival/bench/`)

| Fichier | Rôle |
|---|---|
| `BenchConfig.java` | Mods gérés, objets exclus, vitesses, formule |
| `BenchRecipe.java` | Une entrée du catalogue |
| `BenchCatalog.java` | Sort les recettes du `RecipeManager` et construit le catalogue |
| `BenchTimes.java` | Formule de temps, logique pure |
| `BenchOverrides.java` | Surcharges par données, `data/<ns>/engineer_bench/*.json` |
| `Withdrawal.java` | Plan de prélèvement atomique, logique pure |
| `WorkshopEntry.java`, `PlayerWorkshop.java` | File et stock d'un joueur |
| `WorkshopSavedData.java`, `WorkshopTicker.java` | Persistance et avancement |
| `BenchOrders.java` | Commande et annulation, avec toutes les vérifications |
| `EngineerBenchBlock.java`, `EngineerBenchBlockEntity.java` | Le bloc |
| `EngineerBenchMenu.java`, `BenchStockMenu.java` | Menus serveur |
| `BenchNetwork.java` et ses paquets | Canal réseau |
| `client/EngineerBenchScreen.java`, `client/BenchStockScreen.java`, `client/BenchClientState.java` | Écrans |

---

### Task 1 : déplacer les recettes hors du `RecipeManager`

Le risque du lot : s'il échoue, tout le reste change. Cette tâche seule décide de la suite.

**Files:**
- Create: `bench/BenchConfig.java`, `bench/BenchRecipe.java`, `bench/BenchCatalog.java`
- Modify: `OneForAllSurvival.java`
- Create: `src/devcontent/resources/data/oneforallsurvival/recipes/test_bench_shaped.json`, `src/devcontent/java/dev/oneforallsurvival/dev/BenchCatalogGameTests.java`
- Create: `docs/verifications/lot-1.md`

**Interfaces:**
- Produces:
  - `record BenchRecipe(ResourceLocation id, ItemStack result, List<Ingredient> ingredients, String category, int seconds)`
  - `BenchCatalog.current() : BenchCatalog`, `Collection<BenchRecipe> recipes()`, `Optional<BenchRecipe> byId(ResourceLocation)`
  - `static boolean BenchCatalog.isManaged(Recipe<?> recipe, RegistryAccess access, Set<String> mods)`
  - `BenchConfig.SPEC` (COMMON) : `managedMods` = `create`, `createaddition`, `create_new_age`, `light_bulb_addon_create_crafts_and__addions`, `oneforallsurvival` ; `excludedItems` (vide) ; `tableAllowedItems` = `oneforallsurvival:engineer_bench`

**Mécanisme.** Garder le `RecipeManager` à `AddReloadListenerEvent` (`getServerResources().getRecipeManager()`), puis à `TagsUpdatedEvent` de cause `SERVER_DATA_LOAD` : filtrer `getRecipes()` selon `isManaged`, appeler `replaceRecipes` avec le reste, ranger les retirées au catalogue. Types concernés : `RecipeType.CRAFTING`, `STONECUTTING`, `SMITHING`. Vérifié par `javap` : les méthodes existent et `updateRegistryTags` publie l'événement ; **non vérifié : l'ordre vis-à-vis de CraftTweaker.**

- [ ] **Step 1 : écrire les GameTests qui échouent**

```java
laRecetteDEssaiQuitteLeManagerEtEntreAuCatalogue: manager.byKey(test_bench_shaped) vide ; BenchCatalog.current().byId(...) présent, ingrédients et résultat conformes
uneRecetteVanillaResteSurLaTable: minecraft:crafting_table toujours dans le manager, absente du catalogue
lEtabliRestePrenableALaMain: tableAllowedItems garde sa recette de table
leReloadReconstruitLeCatalogue: performPrefixedCommand("reload"), après attente : mêmes résultats, sans doublon
avecLePack_aucuneRecetteDeTableDeCreate: ignoré sans pack ; aucune recette CRAFTING/STONECUTTING/SMITHING au résultat d'un mod géré ; le catalogue en contient plus de 1000
avecLePack_lesFoursEtMachinesRestent: ignoré sans pack ; create:milling et les recettes SMELTING vers ces mods restent au manager
```

- [ ] **Step 2 : vérifier l'échec**, `./gradlew.bat runGameTestServer` : compilation en échec.

- [ ] **Step 3 : implémenter**

- [ ] **Step 4 : vérifier le succès**, avec et sans `-PwithPack`.

- [ ] **Step 5 : mesurer CraftTweaker**

Déposer dans `run-gametest/scripts/` un script ajoutant une recette de table au résultat `create:andesite_alloy` sous un nom unique, lancer avec le pack, vérifier par un GameTest temporaire si elle a été déplacée. Si CraftTweaker passe après notre retrait, changer le moment du retrait (au premier tick serveur et sur `OnDatapackSyncEvent`) et consigner pourquoi.

- [ ] **Step 6 : recenser les recettes spéciales**

Dans `docs/verifications/lot-1.md` : liste des recettes au résultat vide ou de classe autre que `ShapedRecipe`, `ShapelessRecipe`, `StonecutterRecipe`, `SmithingTransformRecipe`, relevée à l'exécution avec le pack. Une recette spéciale ne peut pas se payer à l'avance : **elle reste sur la table** et la liste le dit (`key_copy` d'Advanced Electricity en est une). Ajouter ce cas au test. Si un cas permet de fabriquer un objet de machine sans ingénieur, le signaler dans le rapport.

- [ ] **Step 7 : commit**

```bash
git add src/main/java/dev/oneforallsurvival src/devcontent docs/verifications/lot-1.md
git commit -m "feat(etabli): retire les recettes des mods d'ingenierie de la table"
git push
```

---

### Task 2 : temps de fabrication et surcharges

**Files:**
- Create: `bench/BenchTimes.java`, `bench/BenchOverrides.java`
- Modify: `bench/BenchCatalog.java`, `bench/BenchConfig.java`
- Test: `src/test/java/dev/oneforallsurvival/bench/BenchTimesTest.java`, modifier `BenchCatalogGameTests.java`

**Interfaces:**
- Consumes: `BenchCatalog`
- Produces:
  - `record BenchTimes.Params(int baseSeconds, int perIngredient, int maxSeconds)`, défaut `(4, 1, 600)` en config
  - `record BenchTimes.Slot(int rarityWeight, String madeBy)` : `madeBy` est l'identifiant de la recette de l'ingrédient, ou `null`
  - `static Map<String, Integer> BenchTimes.compute(Map<String, List<Slot>> recipes, Params params)`
  - Rareté par `Item.getRarity` : `COMMON` 0, `UNCOMMON` 2, `RARE` 5, `EPIC` 10
  - Surcharge JSON : `{"result":"<id>","ingredients":[…],"seconds":120,"hidden":false}`, tous champs optionnels sauf `result`

- [ ] **Step 1 : écrire les tests qui échouent**

```java
sansIngredient: compute({"a":[]}, (4,1,600)).get("a") == 4
lesIngredientsEtLaRaretePesent: un Slot(0,null) et un Slot(5,null) donnent 4 + 1 + 1 + 5 == 11
laComplexiteSeTransmet: "b" à 11 s, "a" contenant Slot(0,"b") donne 4 + 1 + 5 == 10 (moitié entière de 11)
leTempsEstPlafonne: dépassement de 600 donne 600
unCycleNeBouclePas: "a" contient "b" et "b" contient "a" : se termine, résultat ≤ plafond
```

Dans `BenchCatalogGameTests` : une surcharge de données de `devcontent` fixe `seconds`, une autre `hidden`, une autre remplace les ingrédients.

- [ ] **Step 2 : vérifier l'échec**, `./gradlew.bat test --tests "*BenchTimesTest"`.

- [ ] **Step 3 : implémenter**, le cycle est coupé par un ensemble « en cours de calcul » qui compte 0.

- [ ] **Step 4 : vérifier le succès**, `./gradlew.bat test runGameTestServer`.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/bench src/test/java/dev/oneforallsurvival/bench src/devcontent
git commit -m "feat(etabli): calcule le temps de fabrication et lit les surcharges"
git push
```

---

### Task 3 : file, stock et avancement

**Files:**
- Create: `bench/Withdrawal.java`, `bench/WorkshopEntry.java`, `bench/PlayerWorkshop.java`, `bench/WorkshopSavedData.java`, `bench/WorkshopTicker.java`
- Modify: `bench/BenchConfig.java` (`manualSpeed` 1.0, `poweredSpeed` 4.0, `maxQueue` 64)
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/WorkshopGameTests.java`

**Interfaces:**
- Produces:
  - `static Optional<int[]> Withdrawal.plan(List<Ingredient> needed, List<ItemStack> inventory)` : par ingrédient, l'index de case retenu ; vide s'il en manque un ; ne modifie rien ; une même case ne sert pas au-delà de son nombre
  - `record WorkshopEntry(UUID id, ResourceLocation recipeId, ItemStack result, List<ItemStack> paid, List<ItemStack> remainders, int totalTicks, double doneTicks, ResourceKey<Level> benchLevel, BlockPos benchPos, boolean lastKnownPowered)`, sérialisable en NBT
  - `PlayerWorkshop` : `List<WorkshopEntry> queue()`, `List<ItemStack> stock()`, `void add(WorkshopEntry)`, `Optional<WorkshopEntry> cancel(UUID)`, `void advance(double ticks)` (seule la tête avance), `void addToStock(ItemStack)` (fusionne si possible, ne perd rien au-delà de la taille maximale d'une pile)
  - `static WorkshopSavedData.get(MinecraftServer)`, clé `oneforallsurvival_workshops`, `PlayerWorkshop of(UUID)`, `setDirty()` après chaque changement
  - `WorkshopTicker` : à `ServerTickEvent` phase END, pour chaque atelier : vitesse puis `advance`

Dans cette tâche, le ticker applique `manualSpeed` ; la vitesse selon l'établi arrive en Task 4, avec le bloc. À la fin d'un craft : résultat et `remainders` dans le stock, entrée suivante en tête.

Les entrées gardent les piles exactes payées, NBT et usure compris. `remainders` vient de `getCraftingRemainingItem()` à la commande.

- [ ] **Step 1 : écrire les GameTests qui échouent**

```java
lePlanEstAtomique: besoin de 2 lingots et 1 absent : vide, inventaire intact
unePileNeSertPasDeuxFoisAuDelaDeSonNombre: besoin de 2 sur une pile de 1 : vide
lesTagsSontAcceptes: ingrédient par tag servi par n'importe quel membre
seuleLaTeteAvance: deux entrées, advance(10) : seule la première progresse
laFinVersLeStock: résultat et seau rendu dans le stock, entrée retirée
annulerEnAttenteOuEnCoursRendLesPiles: cancel retourne les piles payées avec NBT ; UUID inconnu : vide
fileEtStockSurviventALaSauvegarde: save puis load : progression, piles, NBT, positions identiques
unStockTresLong: 2000 piles différentes : conservées et rechargées
deuxJoueursIndependants: chacun avance et garde son stock
```

- [ ] **Step 2 : vérifier l'échec.** **Step 3 : implémenter.** **Step 4 : vérifier le succès.**

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/bench src/devcontent
git commit -m "feat(etabli): ajoute la file et le stock par joueur"
git push
```

---

### Task 4 : bloc, commandes et réseau

**Files:**
- Create: `bench/EngineerBenchBlock.java`, `bench/EngineerBenchBlockEntity.java`, `bench/EngineerBenchMenu.java`, `bench/BenchOrders.java`, `bench/BenchNetwork.java`, paquets `OrderPacket`, `CancelPacket`, `RecipeListPacket`, `QueuePacket`, `OpenStockPacket`
- Create: `bench/BenchRegistry.java` (`DeferredRegister` : bloc, objet, block entity, menus, onglet créatif)
- Modify: `OneForAllSurvival.java`, fichiers de langue
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/BenchOrdersGameTests.java`

**Interfaces:**
- Consumes: `ElectricMachineBlock`, `ElectricMachineBlockEntity`, `RoleGate.requireEngineer`, `WorkshopSavedData`, `Withdrawal`, `BenchCatalog`
- Produces:
  - `oneforallsurvival:engineer_bench`
  - `EngineerBenchBlockEntity extends ElectricMachineBlockEntity` : capacité 1000, 20 FE/t ; `wantsToWork()` vrai si une entrée de tête est rattachée à cette position
  - `enum OrderResult { OK, NOT_ENGINEER, UNKNOWN_RECIPE, MISSING_INGREDIENTS, QUEUE_FULL, TOO_FAR, BAD_COUNT }`
  - `static OrderResult BenchOrders.order(ServerPlayer p, ResourceLocation recipe, int count, BlockPos bench)`, `static boolean BenchOrders.cancel(ServerPlayer p, UUID entry)`

Vitesse de la tête de file, dans `WorkshopTicker` : établi chargé (`level.isLoaded`) et `EngineerBenchBlockEntity` présent → `poweredSpeed` s'il est `RUNNING`, sinon `manualSpeed`, et `lastKnownPowered` mis à jour ; chargé mais bloc absent → `manualSpeed` ; non chargé → `lastKnownPowered`. Le ticker ne doit **jamais** charger un chunk.

`order` : vérifie rôle, distance ≤ 8 blocs, `count` entre 1 et 64, recette connue et non exclue, place en file, plan de prélèvement pour chaque unité ; prélève tout ou rien. `cancel` : rend dans l'inventaire, surplus au stock. Pas de vérification de distance pour annuler, mais le rôle est requis.

Ouverture : `use` côté serveur appelle `RoleGate.requireEngineer` puis ouvre le menu et envoie le catalogue. `RecipeListPacket` envoyé à l'ouverture si le client ne l'a pas pour la génération courante du catalogue ; `/reload` incrémente la génération. Le paquet doit tenir dans la limite d'un paquet personnalisé : mesurer sa taille avec le pack et fractionner au besoin. `QueuePacket` est envoyé à chaque changement et une fois par seconde tant que le menu est ouvert.

- [ ] **Step 1 : écrire les GameTests qui échouent**, avec un `ServerPlayer` sur `EmbeddedChannel` (`D:\BDC\Minecraft\Dans un GameTest de Forge 47 un ServerPlayer sur une EmbeddedChannel entre en jeu comme un joueur distant.md`) :

```java
unCitoyenEstRefuse: order -> NOT_ENGINEER, inventaire et file intacts
unIngenieurCommandeEtPaie: OK, ingrédients prélevés, une entrée en file
ingredientsManquantsNeRienPrelever: MISSING_INGREDIENTS, inventaire identique
quantiteInvalide: 0, -1, 65 -> BAD_COUNT
troisUnitesPartiellementPayables: rien n'est prélevé
tropLoin: TOO_FAR
fileEtDeconnexion: commande, retrait du joueur, avancement, le stock contient le résultat au retour
annulationInventairePlein: tout revient, le surplus est dans le stock
lEtabliConsommeSeulementQuandIlTravaille: STOPPED sans file, RUNNING et consommation avec courant et file
laVitesse: alimenté 4 fois plus vite qu'à la main ; bloc cassé : vitesse à la main, file intacte ; état inconnu après déchargement : dernier état, sans chargement du chunk
recetteDisparueAuReload: après suppression du catalogue, l'entrée en file se termine et reste annulable
```

- [ ] **Step 2 : vérifier l'échec. Step 3 : implémenter. Step 4 : vérifier le succès.** Un client connecté à un serveur dédié est prouvé en Task 8.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival src/main/resources/assets src/devcontent
git commit -m "feat(etabli): ajoute le bloc, les commandes et le reseau"
git push
```

---

### Task 5 : écran principal

**Files:**
- Create: `bench/client/EngineerBenchScreen.java`, `bench/client/BenchClientState.java`, `bench/client/BenchClientEvents.java`
- Modify: fichiers de langue

**Interfaces:**
- Consumes: `EngineerBenchMenu`, paquets de la Task 4
- Produces: `static List<BenchRecipe> BenchClientState.filter(List<BenchRecipe> all, String category, String query)` : logique pure testée en JUnit, recherche sans casse ni accents sur le nom affiché et l'identifiant

**Écran.** À gauche, onglets de catégories par mod, plus « Tout ». Au centre, barre de recherche et liste à défilement virtuel : seules les lignes visibles sont dessinées, car le catalogue dépasse 1000 lignes. À droite, le détail de l'objet choisi : ingrédients avec possédé / requis, ce qui manque en rouge, temps, vitesse selon le courant, quantité, bouton **Crafter**. En bas, la file : craft en cours avec barre et temps restant, crafts en attente, bouton **Annuler** par ligne. Bouton **Stock** avec le nombre d'objets en attente. Taille adaptée à la fenêtre, utilisable en 1280x720 et à l'échelle GUI 3.

Le bouton **Crafter** se grise localement si l'ingrédient manque, mais c'est le résultat serveur qui fait foi, et un refus affiche son message.

- [ ] **Step 1 : tests JUnit de `filter`, en échec** : catégorie, sans casse, sans accents, requête vide. **Step 2 : implémenter l'écran.**

- [ ] **Step 3 : capture de contrôle.** Réutiliser le pilotage de `src/devcontent/.../DemoReseaux.java` pour ouvrir l'écran et écrire une image. Lire l'image : tout est lisible, rien ne déborde, avec le pack complet.

- [ ] **Step 4 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/bench src/main/resources/assets src/test/java src/devcontent
git commit -m "feat(etabli): ajoute l'ecran principal"
git push
```

---

### Task 6 : écran de stock

**Files:**
- Create: `bench/BenchStockMenu.java`, `bench/client/BenchStockScreen.java`
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/StockMenuGameTests.java`

**Interfaces:**
- Consumes: `PlayerWorkshop`, `OpenStockPacket`
- Produces: `BenchStockMenu` : fenêtre de 6 lignes de 9 sur le stock, décalage de lignes changé par `clickMenuButton`, inventaire du joueur en dessous

Les cases de stock n'acceptent aucun dépôt (`mayPlace` faux). Prendre une pile, ou maj-clic vers l'inventaire, la retire du stock ; une case libérée décale la suite sans trou. La validité du menu exige le rôle et la proximité de l'établi d'origine. Le défilement se fait à la molette ou par une barre, côté client, avec un message au serveur.

- [ ] **Step 1 : GameTests, en échec**

```java
prendreRetireDuStock: quickMoveStack déplace la pile dans l'inventaire et réduit le stock
inventairePleinRienNeDisparait: le surplus reste dans le stock
deposerEstRefuse: mayPlace est faux sur chaque case de stock
leDecalageMontreLaSuite: 100 piles, décalage de 10 lignes : la première case montre la 91e pile
decalageHorsLimites: borné, sans exception
unNonIngenieurEstEjecte: stillValid faux après retrait du rôle
```

- [ ] **Step 2 : implémenter. Step 3 : vérifier. Step 4 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/bench src/devcontent
git commit -m "feat(etabli): ajoute l'ecran de stock"
git push
```

---

### Task 7 : visuels finaux et recette de l'établi

**Files:**
- Create: `assets/oneforallsurvival/blockstates/engineer_bench.json`, `models/block/engineer_bench*.json`, `models/item/engineer_bench.json`, `textures/block/engineer_bench_*.png`, `textures/gui/bench*.png`
- Create: `data/oneforallsurvival/recipes/engineer_bench.json`, `data/oneforallsurvival/loot_tables/blocks/engineer_bench.json`, `data/minecraft/tags/blocks/mineable/axe.json`

**Visuel.** Un plan de travail fait d'une table en bois de récupération, d'un étau et d'un panneau de fuselage riveté avec des fils apparents, en textures 16x16 cohérentes avec Minecraft. Une variante par état de la machine : éteint, à la main, alimenté (lampe allumée), sans courant. Écran dans les mêmes tons : tôle, rivets, bois. Rien de propre ni de futuriste. Produire les PNG par script Python et PIL (disponible) gardé hors du dépôt.

Recette de table avec matériaux vanilla uniquement : 3 planches, 2 lingots de fer, 2 cuivres, 1 table de craft. Elle reste sur la table (`tableAllowedItems`).

- [ ] **Step 1 : GameTest** : la recette reste au manager et ne contient que `minecraft:` ; casser l'établi rend l'objet et la file reste intacte.

- [ ] **Step 2 : produire les visuels. Step 3 : capture de contrôle.** Lire une image du bloc dans le monde pour chaque état et une de chaque écran : aucun cube violet et noir, style conforme, texte lisible.

- [ ] **Step 4 : commit**

```bash
git add src/main/resources/assets src/main/resources/data src/devcontent
git commit -m "feat(etabli): ajoute les visuels et la recette de l'etabli"
git push
```

---

### Task 8 : JEI et preuve de bout en bout

**Files:**
- Create: `bench/compat/BenchJeiPlugin.java`
- Modify: `build.gradle`, `gradle.properties` (JEI 1.20.1 Forge dans le pack de développement, dernière version 15.x au moment de la tâche)
- Modify: `docs/verifications/lot-1.md`, `tasks/todo.md`

**Interfaces:**
- Consumes: `BenchClientState`, catalogue reçu
- Produces: `@JeiPlugin BenchJeiPlugin` : catégorie « Établi d'ingénieur », compile contre l'API en `compileOnly`, et le mod reste chargeable sans JEI

- [ ] **Step 1 : plugin JEI.** Vérifier sans JEI : `runGameTestServer` et chargement normal.

- [ ] **Step 2 : démonstration filmée `runDemoEtabli`**, sur le modèle de `runDemo` et de son encodage par Windows Media Foundation, **sans rien télécharger** : tentative de craft sur la table refusée, ouverture de l'écran, recherche, commande de plusieurs crafts, annulation avec retour des objets, arrivée dans le stock, récupération, comparaison à la main et alimenté, refus pour un Citoyen. Les valeurs à l'écran sont réelles. Envoyer la vidéo à Killian.

- [ ] **Step 3 : serveur dédié et client.** `./gradlew.bat runServer` si l'EULA est déjà acceptée, `runClient` connecté, l'établi s'ouvre et un craft se termine, avec le pack complet. Cette preuve se pilote ou se fait avec Killian.

- [ ] **Step 4 : tout relancer**, `./gradlew.bat test build runGameTestServer -PwithPack`, et vérifier le jar.

- [ ] **Step 5 : consigner et publier.** `docs/verifications/lot-1.md` : ce qui est prouvé, ce qui ne l'est pas, recettes spéciales laissées sur la table. Cocher le lot 1 dans `tasks/todo.md`. Notes générales dans `D:\BDC` selon ses conventions, index compris.

```bash
git add src/main/java/dev/oneforallsurvival/bench/compat build.gradle gradle.properties docs/verifications/lot-1.md tasks/todo.md src/devcontent
git commit -m "feat(etabli): ajoute JEI et consigne la preuve du lot 1"
git push
```
