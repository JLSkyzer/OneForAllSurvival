# Verifications du lot 1

## Tache 1 : recettes hors du RecipeManager (2026-11-06)

Mecanisme : `AddReloadListenerEvent` garde le `RecipeManager` du rechargement, puis `TagsUpdatedEvent`
de cause `SERVER_DATA_LOAD` en retire les recettes gerees (`replaceRecipes`) et les range dans
`BenchCatalog`. Aucun mixin. Un `/reload` reconstruit le catalogue (GameTest
`leReloadReconstruitLeCatalogue`).

Preuve : `./gradlew runGameTestServer` puis `./gradlew runGameTestServer -PwithPack`, a chaque fois
« All N required tests passed » lu dans `run-gametest/logs/latest.log` (43 avec le pack comme sans ;
sans pack, les trois essais « avecLePack_ » s'ecartent en le journalisant).

Avec le pack : 1126 recettes au catalogue (create 933, light_bulb_addon_create_crafts_and__addions 133, create_new_age 34, createaddition 25, oneforallsurvival 1 : la recette d essai), 3 recettes speciales
restees sur la table. `create:milling` (55 recettes) et 4 recettes de four vers un mod gere restent
au manager.

### Ordre contre CraftTweaker : mesure

Script `run-gametest/scripts/zz_bench_probe.zs` (ignore par git, retire apres la mesure) :

```
craftingTable.addShaped("zz_bench_ct_probe", <item:create:andesite_alloy>, [
    [<item:minecraft:iron_ingot>, <item:minecraft:iron_ingot>],
    [<item:minecraft:iron_ingot>, <item:minecraft:copper_ingot>]
]);
```

Resultat avec le pack complet : CraftTweaker ajoute sa recette **avant** notre retrait (le script est
charge a la phase des recettes, `TagsUpdatedEvent` vient apres). Un GameTest temporaire a lu
`manager=0 catalogue=1` pour `crafttweaker:zz_bench_ct_probe`, y compris apres un `/reload`.

**Premier passage, en echec** : la recette restait au manager, rangee en « speciale », car CraftTweaker
declare ses recettes de table dans ses propres classes `CTShapedRecipe` et `CTShapelessRecipe`
(sous-classes de `ShapedRecipe` et `ShapelessRecipe`), pas dans les classes exactes. Ces deux classes
sont donc admises par leur nom (ingredients et resultat statiques ; sa fonction de recette ne touche que
l'objet assemble). Sans cette admission, tout script CraftTweaker aurait contourne l'etabli.

Synchronisation client : non mesuree (pas de client lance). Le bytecode de
`MinecraftServer.reloadResources` montre `updateRegistryTags` avant les appels a `PlayerList` qui
envoient tags et recettes ; le catalogue vide donc le manager avant l'envoi. A confirmer avec JEI en jeu.

### Recettes speciales (relevees a l'execution, avec le pack)

Restent sur la table : un resultat vide ou une classe autre que `ShapedRecipe`, `ShapelessRecipe`,
`StonecutterRecipe`, `SmithingTransformRecipe` (et les deux classes CraftTweaker).

| Recette | Classe | Peut-elle fabriquer une machine ? |
|---|---|---|
| `create:crafting/curiosities/item_copying` | `ItemCopyingRecipe` | Non : copie un objet porteur de donnees (schematique, presse-papier) |
| `create:crafting/curiosities/toolbox_dyeing` | `ToolboxDyeingRecipe` | Non : teint une caisse a outils deja fabriquee |
| `light_bulb_addon_create_crafts_and__addions:key_copy` | `KeyCopyRecipe` | Non : copie une cle |

Aucune des trois ne fabrique de machine sans ingenieur. Les trois exigent d'avoir deja l'objet source.

### Hors perimetre, a savoir

Seuls `CRAFTING`, `STONECUTTING` et `SMITHING` sont deplaces. Les recettes d'autres types dont le
resultat est un objet de mod gere (`create:mechanical_crafting`, assemblages sequences, remplissages)
restent a leurs machines : non denombrees ici.

## Tache 2 : temps de fabrication et surcharges (2026-11-06)

Formule (`BenchTimes`, logique pure testee en JUnit) : temps = base 4 + par ingredient (1 + poids de
rarete + moitie du temps de la recette qui fabrique l'ingredient), plafonne a 600. Poids : COMMON 0,
UNCOMMON 2, RARE 5, EPIC 10. Un cycle est coupe (la recette en cours compte 0). Reglages en config
COMMON : `baseSeconds`, `secondsPerIngredient`, `maxSeconds`.

Surcharges : `data/<espace>/bench_overrides/*.json`, lues par `BenchOverrides` (reload listener, donc
avant le `TagsUpdatedEvent` qui construit le catalogue ; confirme par le GameTest `/reload`, qui relit
les surcharges avant de reconstruire). Fichier invalide : WARN avec son nom, ignore. Surcharge sur un
resultat sans recette : WARN. `excludedItems` agit comme `hidden`.

Preuve : `./gradlew test runGameTestServer` puis `./gradlew runGameTestServer -PwithPack`, a chaque fois
« All 53 required tests passed » lu dans `run-gametest/logs/latest.log`. Avant l'ajout des fichiers de
surcharge, les 6 essais de surcharge echouaient (« 6 required tests failed »).

### Repartition des temps avec le pack (1129 recettes au catalogue, mesure du GameTest)

min 5 s, mediane 10 s, max 148 s, 0 recette au plafond de 600 s.

| Les cinq plus longues | Temps |
|---|---|
| create:crafting/materials/brass_block_from_compacting | 148 s |
| create:crafting/materials/zinc_block_from_compacting | 148 s |
| create:crafting/kinetics/flywheel | 140 s |
| oneforallsurvival:test_bench_alt (surcharge d'essai) | 120 s |
| oneforallsurvival:test_bench_shaped (surcharge d'essai) | 120 s |

Les deux recettes d'essai a 120 s sont des surcharges imposees, pas du calcul. Les recettes calculees les
plus longues sont les blocs compactes (9 lingots dont chacun hérite de la chaine des pepites) et le volant.
A juger par Killian : la mediane de 10 s et un maximum de 148 s laissent le plafond de 600 s inutilise.
