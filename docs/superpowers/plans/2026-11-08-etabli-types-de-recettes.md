# Établi : types de recettes transférés par configuration — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Une clé de config `transferredTypes` fait passer à l'établi d'ingénieur toutes les recettes des types listés, en les retirant de leur machine, sauf celles à fluide ou illisibles.

**Architecture:** La lecture de la config est une fonction pure (`BenchTransfers.parse`), testée en JUnit. L'examen d'une recette (`RecipeInspector`) dit si elle porte un fluide, par réflexion sur ses champs, et donne son objet garanti, sans dépendance de compilation à Create. `BenchCatalog.extract` reçoit l'ensemble des types transférés et les traite à côté de la règle actuelle par `managedMods`. Le client affiche la machine d'origine dans l'infobulle de la liste.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, `ForgeConfigSpec`, réflexion Java.

**Spec:** `docs/superpowers/specs/2026-11-08-etabli-types-de-recettes-design.md` (approuvé par Killian le 2026-11-08).

## Global Constraints

- Aucune dépendance de compilation à Create ni à un autre mod : Create n'est chargé qu'avec `-PwithPack` (`runtimeOnly`).
- Défaut de `transferredTypes` : liste vide. Config vide = catalogue et `RecipeManager` identiques à aujourd'hui.
- En cas de doute, ne pas transférer : la recette reste dans sa machine et le journal dit pourquoi.
- Une ligne fautive ne bloque jamais le démarrage ni le reste de la config.
- `oneforallsurvival:engineer_bench` n'est jamais transféré (`BenchCatalog.ALWAYS_ON_TABLE`).
- Textes visibles dans `fr_fr.json`, avec repli `en_us.json`. Ne jamais écrire « SolarTown » : le serveur s'appelle Lost Island RP.
- Aucune classe cliente atteignable depuis un chemin serveur.
- Preuve des GameTests : lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie. `./gradlew runGameTestServer` sans le pack, puis `./gradlew runGameTestServer -PwithPack`. Une `ConcurrentModificationException` au démarrage est intermittente : relancer.
- Dans un GameTest, lever `GameTestAssertException` (via `helper.assertTrue`), jamais `AssertionError`.
- Ne jamais appeler `ConfigValue.set` dans un test : passer les valeurs à `extract` en paramètre.
- Aucun téléchargement. Ne tuer que des PID exacts lancés par soi. Ne jamais écrire une config Forge avec `Set-Content -Encoding utf8`.
- Branche `etabli-types`. Commit et push après chaque tâche vérifiée, fichiers ajoutés par nom (jamais `art/` ni `docs/assets/`), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Pas de fusion dans `main` sans l'accord de Killian.
- Aucun lancement de client (runClient, runE2eClient, runDemo*) : ce plan n'en a pas besoin.

## Review Focus

1. Une recette Create à fluide en entrée seulement (mixage eau + objet → objet) : elle reste dans sa machine. Testé en tâche 4 (`avecLePack_uneRecetteAFluideResteDansSaMachine`).
2. Une recette dont la première sortie est aléatoire et aucune n'est garantie : refusée, pas transférée avec un objet au hasard. Testé en tâche 2 (`sansSortieGarantieRefusee`).
3. `minecraft:crafting` listé : l'établi reste sur la table, et une recette de table de classe non standard aussi. Testé en tâche 3 (`laTableListeeGardeSesExemptions`).
4. Un champ de fluide illisible par réflexion (exception d'accès) : la recette est refusée, rien ne plante. Testé en tâche 2 (`unChampIllisibleRefuse`).
5. Un `/reload` reconstruit le catalogue depuis un manager neuf : deux extractions des mêmes recettes donnent le même catalogue, sans doublon. Testé en tâche 3 (`deuxExtractionsDonnentLeMemeCatalogue`) ; le `/reload` réel reste couvert par `leReloadReconstruitLeCatalogue`.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Détection des fluides | Réflexion sur tous les champs d'instance de la classe et de ses parents : un champ dont le type (ou un argument générique) a un nom simple contenant `Fluid`, `Gas` ou `Chemical`, et dont la valeur est non nulle et non vide, rend la recette refusée. Lecture impossible : refusée aussi. | Couvre Create (`fluidIngredients`, `fluidResults`, vérifié par javap sur Create 6.0.8) sans le connaître à la compilation, et la plupart des mods à fluide ou gaz. |
| Objet garanti | Si la recette a une méthode publique sans argument `getRollableResults()` dont les éléments ont `getChance()` et `getStack()` : première sortie de chance ≥ 1. Aucune : refusée. Sinon : `getResultItem`. | `getResultItem` de Create renvoie la première sortie, garantie ou non (vérifié par javap). |
| Nom de la machine | Clé `gui.oneforallsurvival.bench.machine.<ns>.<path>` de notre traduction, sinon `<ns>.recipe.<path>` (convention de Create, vérifiée dans son `fr_fr.json` : « Pressage »), sinon l'id du type. | Les types de recette n'ont pas de nom traduit standard. |
| Recettes refusées | Rangées dans `specials()` avec leur raison, journalisées une par une en WARN, puis un total par type en INFO. | Même canal que les recettes spéciales actuelles. |

---

### Task 1: Lecture de la config `transferredTypes`

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/bench/BenchTransfers.java`
- Modify: `src/main/java/dev/oneforallsurvival/bench/BenchConfig.java`
- Test: `src/test/java/dev/oneforallsurvival/bench/BenchTransfersTest.java`

**Interfaces:**
- Produces: `BenchTransfers.Parsed parse(List<? extends String> lines, Predicate<String> modLoaded, Predicate<ResourceLocation> typeKnown)` ; `record Parsed(Set<ResourceLocation> types, List<String> warnings)` ; `BenchTransfers.ALWAYS_REFUSED = Set.of(create:deploying, create:sequenced_assembly)` ; `BenchConfig.transferredTypes(): Set<ResourceLocation>`.

- [ ] **Step 1: Write the failing tests** in `BenchTransfersTest` (prédicats : `create` chargé, `absent` non chargé ; types connus : `create:pressing`, `create:cutting`, `minecraft:smelting`, `create:deploying`) :
  - `uneLigneValideDonneSesTypes` : `"create=create:pressing, create:cutting"` → types `{create:pressing, create:cutting}`, aucun avertissement (espaces tolérés).
  - `unModAbsentEstIgnoreSansBruit` : `"absent=create:pressing"` → types vides, warnings vides.
  - `unTypeDUnAutreNamespaceEstPermis` : `"create=minecraft:smelting"` → `{minecraft:smelting}`.
  - `unTypeInconnuAvertit` : `"create=create:inconnu"` → types vides, 1 avertissement contenant `create:inconnu`.
  - `uneLigneMalFormeeAvertitSansBloquerLesAutres` : `["create", "=x", "create=", "create=pas une id", "create=create:pressing"]` → `{create:pressing}`, 4 avertissements.
  - `unTypeToujoursRefuseAvertit` : `"create=create:deploying"` → types vides, 1 avertissement.
  - `uneListeVideNeDonneRien`.
- [ ] **Step 2: Run** `./gradlew test --tests dev.oneforallsurvival.bench.BenchTransfersTest` → FAIL (classe absente).
- [ ] **Step 3: Implement** `BenchTransfers.parse` : découpe au premier `=`, mod id non vide, types séparés par `,` et `trim`, `ResourceLocation.tryParse`. Mod non chargé → ligne ignorée avant tout contrôle de ses types. Dans `BenchConfig`, ajouter `transferredTypes` (`defineListAllowEmpty`, défaut `List.of()`, commentaire en français : « Lignes mod=type1,type2 : toutes les recettes de ces types quittent leur machine pour l'établi. Les recettes à liquide ou gaz restent dans leur machine. ») et `transferredTypes()` qui appelle `parse` avec `ModList.get()::isLoaded` et `BuiltInRegistries.RECIPE_TYPE::containsKey`, journalise chaque avertissement en WARN, et replie sur `Set.of()` tant que la config n'est pas chargée (même schéma que `read`).
- [ ] **Step 4: Run** `./gradlew test` → tous verts ; lire les comptes dans `build/test-results/test/TEST-dev.oneforallsurvival.bench.BenchTransfersTest.xml` (7 tests, 0 échec).
- [ ] **Step 5: Commit** `BenchTransfers.java`, `BenchConfig.java`, `BenchTransfersTest.java` : `feat(bench): lecture de transferredTypes`.

### Task 2: Examen d'une recette (fluide, objet garanti)

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/bench/RecipeInspector.java`
- Test: `src/test/java/dev/oneforallsurvival/bench/RecipeInspectorTest.java`

**Interfaces:**
- Produces: `static Optional<String> fluidRefusal(Object recipe)` (raison `"fluide ou gaz"` ou `"champ illisible : <nom>"`, vide si rien) ; `static Optional<ItemStack> guaranteedResult(Recipe<?> recipe, RegistryAccess access)` (vide si aucune sortie garantie ou résultat vide/illisible). Pour les tests JUnit sans registre : `static Optional<Object> guaranteedRollable(Object recipe)` qui renvoie le `getStack()` brut de la première sortie garantie, ou `Optional.empty()` si la méthode `getRollableResults` n'existe pas ; `guaranteedResult` s'appuie dessus. Pour tester l'échec de lecture : `@FunctionalInterface interface FieldReader { Object read(Field f, Object target) throws ReflectiveOperationException; }` et la surcharge package-private `fluidRefusal(Object recipe, FieldReader reader)` ; la version publique passe `Field::get` après `setAccessible(true)`.

- [ ] **Step 1: Write the failing tests** avec de fausses classes internes au test (aucun objet Minecraft dans `fluidRefusal`) :
  - `sansChampFluideRienARefuser` : classe avec `List<String> ingredients` → vide.
  - `uneListeDeFluideNonVideRefuse` : classe interne `FakeFluidStack` et champ `List<FakeFluidStack> fluidResults = List.of(new FakeFluidStack())` → `"fluide ou gaz"`.
  - `uneListeDeFluideVideNeRefusePas` : même champ, `List.of()` → vide.
  - `unChampGazRefuse` : champ `FakeGasStack gas` non nul → refus.
  - `unChampHeriteEstVu` : le champ fluide est dans la classe parente → refus.
  - `unChampIllisibleRefuse` : appeler la surcharge interne `fluidRefusal(Object, FieldReader)` avec un lecteur qui lève `IllegalAccessException` → raison commençant par `"champ illisible"`.
  - `laPremiereSortieGarantieEstPrise` : `getRollableResults()` renvoie `[sortie(0.25,"pépite"), sortie(1.0,"poudre")]` → `"poudre"`.
  - `sansSortieGarantieRefusee` : toutes les chances < 1 → `Optional.empty()`.
  - `sansRollableRetourneVide` : classe sans la méthode → `Optional.empty()` et `guaranteedResult` utilisera `getResultItem` (vérifié en tâche 3).
- [ ] **Step 2: Run** `./gradlew test --tests dev.oneforallsurvival.bench.RecipeInspectorTest` → FAIL.
- [ ] **Step 3: Implement.** Parcours des champs d'instance non statiques de la classe et de ses parents jusqu'à `Object`. Le nom testé est le `getSimpleName()` du type du champ, et des arguments de son `ParameterizedType` s'il en a. Une valeur « vide » est `null`, une `Collection` vide, ou un objet dont la méthode publique `isEmpty()` renvoie vrai (`FluidStack.EMPTY`). `setAccessible(true)` ; toute exception de lecture → refus « champ illisible ». Réflexion sur `getRollableResults`, `getChance`, `getStack` par nom, sans import de Create.
- [ ] **Step 4: Run** `./gradlew test` → vert, comptes lus dans le XML de `RecipeInspectorTest` (9 tests, 0 échec).
- [ ] **Step 5: Commit** `RecipeInspector.java`, `RecipeInspectorTest.java` : `feat(bench): détection des fluides et objet garanti`.

### Task 3: Transfert dans le catalogue

**Files:**
- Modify: `src/main/java/dev/oneforallsurvival/bench/BenchCatalog.java`
- Modify: `src/main/java/dev/oneforallsurvival/bench/BenchRecipe.java` (javadoc de `category`)
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/BenchCatalogGameTests.java`

**Interfaces:**
- Consumes: `BenchConfig.transferredTypes()`, `BenchTransfers.ALWAYS_REFUSED`, `RecipeInspector.fluidRefusal`, `RecipeInspector.guaranteedResult`.
- Produces: nouvelle surcharge `extract(RecipeManager, RegistryAccess, Set<String> mods, Set<ResourceLocation> tableAllowed, Set<ResourceLocation> excluded, Map<…> overrides, BenchTimes.Params params, Set<ResourceLocation> transferredTypes)` ; l'ancienne signature reste et délègue avec `Set.of()`. Catégorie d'une recette transférée : `"crafting"`, `"stonecutting"`, `"smithing"` pour les trois types vanilla, sinon l'id du type en texte (`"minecraft:smelting"`).

Règle de transfert d'une recette dont le type est listé, dans cet ordre (premier échec = reste, avec sa raison dans `specials`) : type dans `ALWAYS_REFUSED` (« type toujours refusé ») ; type vanilla géré et `!hasStandardClass` (« classe non standard ») ; `fluidRefusal` ; `guaranteedResult` vide (« aucun objet garanti ») ; résultat dans `stays(...)` (reste sans être spécial, comme aujourd'hui) ; aucun ingrédient non vide (« sans ingrédient »). Une recette gérée par la règle actuelle reste gérée de la même façon. `onTagsUpdated` passe `BenchConfig.transferredTypes()` et journalise un total INFO par type transféré.

- [ ] **Step 1: Write the failing GameTests** (batch `etabli_catalogue`, `RecipeManager` construit dans le test, comme `unObjetExcluQuitteLaTableSansEntrerAuCatalogue`) :
  - `uneRecetteDeFourListeePasseALEtabli` : `SmeltingRecipe` id `oneforallsurvival:four_essai`, fer brut → lingot de fer, types `{minecraft:smelting}`, mods vides → absente du manager, au catalogue, résultat lingot de fer ×1, 1 ingrédient, catégorie `"minecraft:smelting"`.
  - `sansLaCleRienNeBouge` : même recette, types vides → reste au manager, absente du catalogue.
  - `unObjetAutoriseResteDansSaMachine` : types `{minecraft:smelting}`, `tableAllowed = {minecraft:iron_ingot}` → reste au manager, pas dans `specials`.
  - `unObjetExcluQuitteLaMachineSansEntrer` : `excluded = {minecraft:iron_ingot}` → absente du manager et du catalogue.
  - `laTableListeeGardeSesExemptions` : types `{minecraft:crafting}`, mods vides ; la recette de l'établi (comme dans `lEtabliResteSurLaTableMemeSiLaConfigEstVide`) reste au manager ; une sous-classe anonyme de `ShapedRecipe` reste, avec la raison « classe non standard » dans `specials` ; une `ShapelessRecipe` vanilla (bâton → planche, par exemple) part au catalogue avec la catégorie `"crafting"`.
  - `deuxExtractionsDonnentLeMemeCatalogue` : deux `extract` successifs, chacun sur un manager neuf contenant les mêmes recettes (four d'essai + recette de table gérée), types `{minecraft:smelting}` → mêmes ids dans le même ordre, sans doublon, et les deux managers privés de la recette de four.
- [ ] **Step 2: Run** `./gradlew runGameTestServer` → les nouveaux tests échouent (surcharge absente : erreur de compilation acceptée comme échec).
- [ ] **Step 3: Implement** la surcharge et la règle ci-dessus dans `BenchCatalog`, `toBench` prenant le résultat garanti et la catégorie ; mettre à jour la javadoc de `BenchRecipe.category`.
- [ ] **Step 4: Run** `./gradlew runGameTestServer` → lire « All N required tests passed » dans `run-gametest/logs/latest.log` ; `./gradlew test` toujours vert.
- [ ] **Step 5: Commit** `BenchCatalog.java`, `BenchRecipe.java`, `BenchCatalogGameTests.java` : `feat(bench): transfert des types de recettes configurés`.

### Task 4: Preuve avec Create

**Files:**
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/BenchCatalogGameTests.java`

**Interfaces:**
- Consumes: la surcharge `extract` de la tâche 3, appelée sur une copie des recettes Create du manager du serveur (`new RecipeManager()` + `replaceRecipes(liste filtrée)`), pour ne pas toucher au manager réel.

- [ ] **Step 1: Write the GameTests** (batch `etabli_pack`, garde `packAbsent`) :
  - `avecLePack_lePressageEntreALEtabli` : toutes les recettes `create:pressing` → au moins une transférée, chacune avec un résultat non vide et au moins un ingrédient ; aucune restée sans raison dans `specials`.
  - `avecLePack_uneRecetteAFluideResteDansSaMachine` : recettes `create:mixing` → au moins une restée avec la raison « fluide ou gaz » ; aucune recette transférée dont la classe a un champ fluide non vide (recontrôle par `RecipeInspector.fluidRefusal`).
  - `avecLePack_leBroyageNeDonneQueLObjetGaranti` : recettes `create:crushing` → pour chaque transférée, le résultat est la première sortie de chance ≥ 1 lue par réflexion ; journaliser trois exemples (id, résultat).
  - `avecLePack_lesQuantitesDeCreate` : une recette `create:compacting` ou `create:pressing` qui demande plusieurs fois le même objet → l'établi demande autant d'entrées. Si aucune n'existe, journaliser « non vérifié » et le reporter dans la vérification, sans échouer.
  - `avecLePack_leDeploiementEstToujoursRefuse` : types `{create:deploying}` → aucune transférée, toutes restées avec « type toujours refusé ».
- [ ] **Step 2: Run** `./gradlew runGameTestServer -PwithPack` → lire « All N required tests passed » ; relever dans le journal les lignes `[etabli]` utiles à la vérification.
- [ ] **Step 3: Run** `./gradlew runGameTestServer` sans le pack → tous verts (les essais `avecLePack_` s'écartent).
- [ ] **Step 4: Commit** `BenchCatalogGameTests.java` : `test(bench): transfert des recettes de Create`.

### Task 5: Repère de machine dans l'infobulle

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/bench/client/BenchMachineName.java`
- Modify: `src/main/java/dev/oneforallsurvival/bench/client/RecipeListPane.java:170-192`
- Modify: `src/main/resources/assets/oneforallsurvival/lang/fr_fr.json`, `en_us.json`
- Test: `src/test/java/dev/oneforallsurvival/bench/client/BenchMachineNameTest.java`

**Interfaces:**
- Produces: `static Optional<String> machineKeyOrId(String category, Predicate<String> keyExists)` — vide pour `crafting`, `stonecutting`, `smithing` ; sinon la première clé existante parmi `gui.oneforallsurvival.bench.machine.<ns>.<path>` puis `<ns>.recipe.<path>`, sinon l'id brut préfixé `"id:"`. `RecipeListPane` en tire la ligne `Component.translatable("gui.oneforallsurvival.bench.machine", nom)` en `DARK_AQUA`, juste après la ligne de durée.

- [ ] **Step 1: Write the failing tests** : `crafting` → vide ; `create:pressing` avec seule `create.recipe.pressing` existante → cette clé ; avec notre clé existante aussi → la nôtre ; `minecraft:smelting` sans clé → `"id:minecraft:smelting"` ; catégorie sans `:` autre que les trois → vide.
- [ ] **Step 2: Run** `./gradlew test --tests dev.oneforallsurvival.bench.client.BenchMachineNameTest` → FAIL.
- [ ] **Step 3: Implement** ; traductions : `gui.oneforallsurvival.bench.machine` = « Machine : %s » / « Machine: %s » ; `gui.oneforallsurvival.bench.machine.minecraft.smelting` = « Four » / « Furnace », `…minecraft.blasting` = « Haut fourneau » / « Blast furnace », `…minecraft.smoking` = « Fumoir » / « Smoker », `…minecraft.campfire_cooking` = « Feu de camp » / « Campfire ». `BenchAssetsTest` vérifie peut-être la parité des deux fichiers : le lancer.
- [ ] **Step 4: Run** `./gradlew test` → vert, comptes lus dans les XML.
- [ ] **Step 5: Commit** `BenchMachineName.java`, `RecipeListPane.java`, les deux fichiers de langue, `BenchMachineNameTest.java` : `feat(bench): machine d'origine dans l'infobulle`.

### Task 6: Vérification et suivi

**Files:**
- Create: `docs/verifications/etabli-types-de-recettes.md`
- Modify: `tasks/todo.md` (ligne « Idées pour plus tard » → fait, avec lien)

- [ ] **Step 1:** Écrire la vérification : commandes lancées, « All N required tests passed » avec et sans pack (N relus), comptes JUnit, exemples relevés en tâche 4, et la liste « Non prouvé » : infobulle jamais vue en jeu (aucun client lancé), JEI côté machine non regardé, résultat dynamique d'un type modé non détectable, quantités portées hors de `Ingredient` par d'autres mods, conditions perdues (chaleur, vitesse, durée, expérience), toute ligne `avecLePack_lesQuantitesDeCreate` « non vérifié ».
- [ ] **Step 2:** Mettre à jour `tasks/todo.md`.
- [ ] **Step 3: Commit et push** les deux fichiers : `docs(bench): vérification des types de recettes transférés`.
