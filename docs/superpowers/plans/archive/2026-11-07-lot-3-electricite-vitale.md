# Lot 3 : électricité vitale — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire de l'électricité une affaire de nourriture : les aliments périment partout, l'armoire frigorifique ralentit cette péremption tant qu'elle a du courant, le séchoir en fait des rations durables et le dessalinisateur produit eau potable et sel.

**Architecture:** La péremption tient dans une date posée sur la pile, en NBT, arrondie au quart d'heure pour que les aliments proches s'empilent seuls. L'état se calcule à la lecture : une zone déchargée n'a donc rien à faire tourner. Les règles chiffrées sont de la logique pure testée en JUnit (`FoodDecay`). Les trois machines héritent de `ElectricMachineBlockEntity`, donc de l'usure, des pannes et de la casse sans perte d'état du lot 2. Aucun mixin.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, fluide Forge (`FluidType`, `ForgeFlowingFluid`, `FluidTank`), recette JSON dédiée, API JEI en `compileOnly`.

**Spec:** `docs/superpowers/specs/archive/2026-11-06-features-lost-island-design.md`, section 5 features 9, 10, 11, section 7 (« Scénario d'acceptation du lot 3 »). Choix de Killian du 2026-11-07 : 6 h de frais et 6 h de rassis ; fusion sans mixin ; le résultat d'une cuisson est frais ; trois rations et leurs trois versions salées.

## Global Constraints

- `gradle.properties` en ASCII pur. Aucun `${` dans `mods.toml` et `pack.mcmeta` hors propriété définie.
- Mod requis côté client et serveur. Aucune classe cliente atteignable depuis un chemin serveur : écrans et infobulles dans un paquet `client`, enregistrés par un abonné `Dist.CLIENT`.
- Textes visibles dans `fr_fr.json`, avec repli `en_us.json`. Ne jamais écrire le nom « SolarTown » : le serveur s'appelle Lost Island RP.
- Rien de `devcontent` dans le mod livré. Registres : `DeferredRegister` ou `RegisterEvent`, jamais un initialiseur statique.
- Preuve des GameTests : lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie de Gradle. `./gradlew runGameTestServer` sans le pack, `-PwithPack` pour ce qui exige Create. Une `ConcurrentModificationException` au démarrage est intermittente : relancer.
- Tout GameTest qui exige qu'une machine ait tourné porte `setupTicks = 20` : un bloc posé en début de test peut ne pas tourner tout de suite.
- Aucun téléchargement hors dépendances déjà en cache. Ne jamais tuer un processus Java autre que ceux qu'on a lancés, par PID exact. Ne jamais écrire les configs Forge avec `Set-Content -Encoding utf8`.
- Commit et push sur la branche `lot-3` après chaque tâche vérifiée, fichiers ajoutés par nom (jamais `art/` ni `docs/assets/`), pas de `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Pas de fusion dans `main`.
- Une zone déchargée n'est pas une coupure. Le contenu d'une machine en panne ou sans courant n'est jamais perdu.
- Les fenêtres de jeu se voient sur l'écran de Killian : aucune boucle de lancements du client sans le lui annoncer, et tout script ferme lui-même ses clients, quel que soit l'écran où ils s'arrêtent.
- Infobulle : l'état seulement, jamais une date ni une durée.

## Review Focus

1. Un aliment daté reste un ingrédient : table de craft et livre de recettes, four, marmite de Farmer's Delight, ingrédient du séchoir. Testé en tâche 2 (`unAlimentDateResteUnIngredient`), marmite en tâche 7.
2. La fusion ne crée ni ne perd d'objet : clic sur une pile pleine, clic droit, inventaire plein, regroupement à la fermeture d'un coffre. Testé en tâche 2.
3. Coupure de courant de l'armoire : aucune perte immédiate, les aliments repartent à la vitesse normale. Testé en tâche 3 et en E2E.
4. Un aliment qui s'avarie pendant qu'on le mange, ou tenu en main : pas de plantage, pas d'objet gratuit, pas de faim négative. Testé en tâche 2.
5. Serveur et client aux réglages différents : l'infobulle suit le serveur. Couvert par la config `SERVER`, synchronisée par Forge. Vu en tâche 7.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Quand un aliment reçoit sa date | À sa première observation : inventaire d'un joueur, ouverture d'un menu, objet au sol, armoire | Dater dès la création exigerait une capability non synchronisée vers le client. Limite : un aliment produit et stocké par des machines sans passer par un joueur ne vieillit pas avant d'être vu |
| Avarié | Remplacé par `spoiled_food`, compostable, à l'observation. Un bol de plat Farmer's Delight est perdu | Simple et lisible ; un aliment avarié jamais vu est refusé à la consommation |
| Rassis | Rend la moitié de la nourriture et de la saturation | Cahier des charges : « moins nourrissant » |
| Séchées | Durée de vie multipliée par 8. Salées : jamais. Tag `non_perishable` vide au départ pour les conserves | « ne périment pas ou très lentement » |
| Armoire | 27 cases, 4 FE par tick, seulement si elle contient un périssable. Ses gains de froid se comptent par quart d'heure entier | Garde les piles d'une armoire empilables entre elles |
| Armoire usée | Sous 50 %, elle rafraîchit moins, comme toute machine | Conséquence directe du rendement du lot 2 |
| Eau du dessalinisateur | Prise gratuite s'il est en position côtière, sinon de l'eau par tuyau. **L'eau d'une pompe intérieure marche aussi** | Rien ne distingue l'eau d'une pompe côtière sans créer un fluide « eau de mer » |
| Rendement | Cycle de 100 ticks à 40 FE : 250 mB d'eau donnent 200 mB d'eau potable, et 1 sel tous les 4 cycles | À équilibrer en jeu ; en config |
| Fluide | Eau potable sans seau ni bloc posable, circule par tuyau, bouteille à remplir dans la machine | Pas de soif : ingrédient seulement. Le seau se rajoute facilement |
| Machine cassée | Les réservoirs se vident, le contenu des cases tombe | Comme le reste du jeu |
| Valeurs nutritives | Viande séchée 4, poisson 3, fruit 2 ; salées pareil | Moins qu'un steak cuit (8), à équilibrer |

## Fichiers

| Fichier | Rôle |
|---|---|
| `food/FoodDecay.java`, `DecayState.java` | Règles pures : arrondi, état, moyenne pondérée |
| `food/FoodStamp.java` | Lecture et écriture de la date sur une pile, horloge du monde |
| `food/FoodTags.java`, `FoodConfig.java`, `FoodRegistry.java` | Tags, réglages `SERVER`, objets, blocs, fluide |
| `food/FoodEvents.java` | Observation, fusion, consommation |
| `food/client/FoodClient.java` | Infobulle et écrans |
| `food/FridgeBlock.java`, `FridgeBlockEntity.java`, `FridgeMenu.java`, `client/FridgeScreen.java` | Armoire |
| `food/DesalinatorBlock.java`, `DesalinatorBlockEntity.java`, `DesalinatorMenu.java`, `client/DesalinatorScreen.java` | Dessalinisateur |
| `food/DryerBlock.java`, `DryerBlockEntity.java`, `DryerMenu.java`, `DryingRecipe.java`, `client/DryerScreen.java` | Séchoir |
| `src/devcontent/.../{Peremption,Armoire,Dessalinisateur,Sechoir}GameTests.java` | Preuves |

---

### Task 1: Date, états, tags et déchet

**Files:**
- Create: `food/DecayState.java`, `FoodDecay.java`, `FoodStamp.java`, `FoodTags.java`, `FoodConfig.java`, `FoodRegistry.java`, `client/FoodClient.java`; `src/test/java/dev/oneforallsurvival/food/FoodDecayTest.java` ; tags `perishable.json`, `slow_perishable.json`, `non_perishable.json` ; langues.
- Modify: `OneForAllSurvival.java`, `bench/BenchRegistry.java` (onglet créatif).

**Interfaces:**
- Produces:
  - `enum DecayState { FRESH, STALE, SPOILED }`.
  - `FoodDecay` : `static final long BUCKET_TICKS = 18000` ; `static long roundDown(long tick)` ; `static DecayState stateAt(long stamp, long now, long freshTicks, long staleTicks)` ; `static long weightedStamp(long stampA, int countA, long stampB, int countB)`, arrondi au quart d'heure inférieur.
  - `FoodConfig.SPEC` de type `ModConfig.Type.SERVER`, fichier `oneforallsurvival-food.toml`, lecteurs avec repli comme `BenchConfig` : `freshTicks()` (`freshHours` 6.0), `staleTicks()` (`staleHours` 6.0), `slowFactor()` 8.0, `fridgeFactor()` 4.0.
  - `FoodTags.PERISHABLE`, `SLOW_PERISHABLE`, `NON_PERISHABLE`, tags d'objets du mod.
  - `FoodStamp` : `static boolean isPerishable(ItemStack)` (`NON_PERISHABLE` l'emporte, puis l'un des deux autres) ; `static boolean isStamped(ItemStack)` ; `static long stamp(ItemStack)` ; `static void setStamp(ItemStack, long)` (clé `oneforallsurvival_stamp`) ; `static boolean stampIfNeeded(ItemStack, long now)` ; `static DecayState state(ItemStack, long now)`, frais si sans date, durées multipliées par `slowFactor` pour le tag lent ; `static long now(Level)` = `level.getGameTime()`.
  - `FoodRegistry.SPOILED_FOOD`, objet non comestible, compostable à 0.5 dans `FMLCommonSetupEvent`.

Tag `perishable` : `#forge:raw_beef`, `raw_chicken`, `raw_pork`, `raw_mutton`, `raw_bacon`, `raw_fishes`, les `cooked_*` et `cooked_fishes` correspondants lus dans le jar de Farmer's Delight, `#minecraft:fishes`, `#farmersdelight:meals`, lapin cru et cuit. Une entrée absente du jar se corrige, ne s'invente pas.

- [ ] **Step 1: Écrire `FoodDecayTest`** : `roundDown(18000)` = 18000, `roundDown(35999)` = 18000 ; `stateAt` à 0, à `fresh - 1` : FRESH ; à `fresh` et `fresh + stale - 1` : STALE ; à `fresh + stale` : SPOILED ; `weightedStamp(0, 3, 72000, 1)` = 18000 ; `weightedStamp(0, 1, 18000, 1)` = 0 ; ordre des piles sans effet ; pas de débordement à de grandes valeurs de ticks.
- [ ] **Step 2: Écrire `PeremptionGameTests`** : `unBoeufEstPerissableMaisPasLaPierre` ; `unPlatDeFarmersDelightEstPerissable` (`farmersdelight:beef_stew`) ; `nonPerishableLEmporte` ; `lEtatSuitLaDate` (dates posées à la main) ; `leLentDureHuitFoisPlus` ; `sansDateCEstFrais` ; `leDechetSeComposte`.
- [ ] **Step 3: Lancer, constater l'échec** : `./gradlew test`, `./gradlew runGameTestServer`, FAIL (compilation).
- [ ] **Step 4: Implémenter**, avec `ItemTooltipEvent` dans `FoodClient` : ligne `food.oneforallsurvival.fresh|stale|spoiled`, vert, jaune, rouge, sur tout périssable, date lue par `Minecraft.getInstance().level`, rien si pas de monde. Texture provisoire du déchet.
- [ ] **Step 5: Lancer, lire** 66 + N JUnit à 0 échec et « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(food): date de peremption, etats, tags et dechet`.

### Task 2: Observation, fusion et consommation

**Files:**
- Create: `food/FoodEvents.java`, tests ajoutés à `PeremptionGameTests`.
- Modify: `OneForAllSurvival.java`.

**Interfaces:**
- Consumes: `FoodStamp`, `FoodDecay`, `FoodRegistry.SPOILED_FOOD`.
- Produces :
  - `FoodEvents.observe(ItemStack, long now)` → `ItemStack` : date si besoin, rend le déchet (même nombre) si avarié, sinon la pile elle-même, sans en changer l'instance.
  - `FoodEvents.consolidate(List<Slot>)` : regroupe les piles d'un même objet qui ne diffèrent que par la date, moyenne pondérée, en respectant `slot.getMaxStackSize(stack)`.
  - `FoodEvents.merge(ItemStack into, ItemStack from, int max)` → `int` déplacé.

Abonnements :
- `TickEvent.PlayerTickEvent`, serveur, une fois par seconde : `observe` puis `consolidate` sur l'inventaire du joueur.
- `PlayerContainerEvent.Open` : `observe` sur tous les emplacements du menu. `Close` : `consolidate` sur les emplacements dont le conteneur est un `BaseContainerBlockEntity` ou une armoire. Jamais sur les autres machines : un mod peut y réserver des cases.
- `EntityJoinLevelEvent`, `ItemEntity` côté serveur : date seulement.
- `ItemStackedOnOtherEvent`, clic gauche ou droit d'une pile portée sur une pile du même objet à date différente : fusion par `merge` avec moyenne, un seul objet au clic droit, puis `setCanceled(true)`.
- `LivingEntityUseItemEvent.Start`, joueur : avarié, annule la consommation et remplace par le déchet. `Finish` : rassis, reprend la moitié de ce que le repas a donné, d'après une photo de la faim prise à `Start`, sans passer sous cette photo.

- [ ] **Step 1: Écrire les GameTests** avec les joueurs factices de `dev` :
  - `lInventaireDateSesAliments`, `unObjetAuSolEstDate`, `lAvarieDevientUnDechetAvecLeMemeNombre`.
  - `laFusionFaitLaMoyenne` : 3 à 0, 1 à 72000 : 4 à 18000.
  - `laFusionNePerdNiNeCreeRien` : piles 60 + 10 : 64 + 6, somme gardée, pile portée mise à jour ; clic droit : un seul déplacé ; objets différents : événement non annulé.
  - `laFermetureDUnCoffreRegroupe`, `laFermetureNeToucheJamaisUneAutreMachine` : bain galvanique inchangé.
  - `unAlimentDateResteUnIngredient` : `Ingredient.of(#forge:raw_beef).test`, recette de four trouvée par `RecipeManager`, et sortie sans date.
  - `onNeMangePasUnAvarie`, `leRassisNourritDeMoitie` (d'après `FoodProperties` du steak), `unAvarieEnPleineBoucheNePlantePas`, `mangerSansFaimNeRendRienEnNegatif`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.** Coût surveillé : une seconde, un joueur, une seule passe sur 41 cases. Aucun `getTag` sur une pile non périssable.
- [ ] **Step 4: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 5: Commit** `feat(food): observation, fusion ponderee et consommation`.

### Task 3: Armoire frigorifique

**Files:**
- Create: `food/FridgeBlock.java`, `FridgeBlockEntity.java`, `FridgeMenu.java`, `client/FridgeScreen.java`, `src/devcontent/.../ArmoireGameTests.java`.
- Modify: `FoodRegistry.java`, `FoodClient.java`, `FoodEvents.java` (l'armoire compte comme coffre).

**Interfaces:**
- Consumes: `ElectricMachineBlock`, `ElectricMachineBlockEntity`, `FoodStamp`, `FoodDecay`, `FoodEvents.observe`, `FoodConfig.fridgeFactor()`.
- Produces: `FridgeBlockEntity` : capacité 2000 FE, réception 100, `energyPerTick` 4, `wantsToWork()` vrai s'il y a un périssable, `ItemStackHandler` de 27 cases exposé sur toutes les faces ; `public long coldCredit()` ; pour les tests, `void debugSetLastTick(long)` ; `double coldFraction()` = `1 - 1 / fridgeFactor`, soit 0.75.

Règle du froid :
- À chaque tick de travail (donc payé et au rendement de la machine) : `coldCredit += coldFraction`. Dès `BUCKET_TICKS` atteint : `setStamp(stamp + BUCKET_TICKS)` sur chaque pile datée, jamais au-delà de `now`, et le crédit baisse d'autant.
- Toutes les 100 ticks : `observe` sur les 27 cases, sans menu ouvert, donc date les objets reçus par trémie et rend les déchets.
- `lastTick` et `coldWhenLeft` sauvegardés à chaque tick où l'état change, et au `saveAdditional`. Au premier tick après un chargement : si `coldWhenLeft`, `coldCredit += (now - lastTick) * coldFraction`, sans payer d'énergie. Une zone déchargée n'est pas une coupure.
- Sans courant : plus de crédit, aucune date ne bouge en arrière, aucune perte. Bloc cassé : contenu au sol, usure sur l'objet (lot 2), crédit perdu.

- [ ] **Step 1: Écrire `ArmoireGameTests`**, `setupTicks = 20` :
  - `alimenteeLeFroidAvance` : crédit après 100 ticks entre 74 et 75.
  - `leCreditDecaleLesDates`, `leFroidNeDepassePasMaintenant`.
  - `quatreFoisPlusLentement` : sur une durée simulée, un aliment de l'armoire est plus jeune que son témoin, d'un facteur 4 à un quart d'heure près.
  - `sansCourantRienNeSePerd` (scénario du cahier des charges) : dates et nombres inchangés au moment de la coupure, crédit stable.
  - `dechargeeElleRattrapeSiElleEtaitFroide`, `dechargeeElleNeRattrapePasSiElleEtaitCoupee`.
  - `uneTremieYDepose` : `insertItem`, date posée sous 100 ticks.
  - `lAvarieYDevientDechet`, `videElleNeConsommeRien`, `casseeElleRendSonContenu`, `lEtatSurvitAuRechargement`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter**, menu de 27 cases, écran au fond du style de l'établi (`BenchTheme`).
- [ ] **Step 4: Lancer, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(food): armoire frigorifique`.

### Task 4: Dessalinisateur, eau potable et sel

**Files:**
- Create: `food/DesalinatorBlock.java`, `DesalinatorBlockEntity.java`, `DesalinatorMenu.java`, `client/DesalinatorScreen.java`, `src/devcontent/.../DessalinisateurGameTests.java`.
- Modify: `FoodRegistry.java`, `FoodClient.java`, `FoodConfig.java`, tag `forge:salt`.

**Interfaces:**
- Consumes: `CoastDetector.isCoastal(ServerLevel, BlockPos)`, `ElectricMachineBlockEntity`.
- Produces :
  - `FoodRegistry.SALT`, `POTABLE_WATER_BOTTLE` (reste : `glass_bottle`), `POTABLE_WATER`, `POTABLE_WATER_FLOWING`, `POTABLE_WATER_TYPE` (textures de l'eau vanilla teintées).
  - `DesalinatorBlockEntity` : deux `FluidTank` de 8000 mB, `IFluidHandler` sur toutes les faces, qui ne remplit que l'eau (`FluidTags.WATER`) et ne vide que l'eau potable ; cases : sel en sortie, bouteille vide en entrée, bouteille pleine en sortie.
  - `FoodConfig` : `desalinatorEnergy()` 40, `desalinatorCycle()` 100, `waterPerCycle()` 250, `potablePerCycle()` 200, `cyclesPerSalt()` 4.

Règles : si la machine est côtière (relu toutes les 200 ticks), le réservoir d'eau se remplit seul, 100 mB par seconde. `wantsToWork` : assez d'eau, place pour le produit et, au cycle du sel, place pour le sel. Sel plein : elle s'arrête, sans rien perdre. Bouteille : 250 mB d'eau potable, sans FE.

- [ ] **Step 1: Écrire `DessalinisateurGameTests`** : `sansEauRienNeSeFait`, `unCycleConsommeEtProduit` (chiffres de la config), `leSelTombeTousLesQuatreCycles`, `leTagSaltEstPorte`, `enZoneCotiereLEauArriveSeule` (zone `/coast`, nettoyée dans le test), `horsZoneIlFautUnTuyau`, `leTuyauNeRemplitQueDeLEau`, `leTuyauNeVideQueLEauPotable`, `sansCourantRienNeSePerd`, `pleinIlSArrete`, `laBouteilleSeRemplit`, `lesReservoirsSurviventAuRechargement`.
- [ ] **Step 2: Avec le pack, dans `PackGameTests`** : `unePompeCreateRemplitLeDessalinisateur` ou, si le montage est trop lourd, `leTuyauCreateVoitLeFluide`, par la capability `FLUID_HANDLER`. Dire lequel a été tenu.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter**, écran avec deux jauges et barre de progression.
- [ ] **Step 5: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 6: Commit** `feat(food): dessalinisateur, eau potable et sel`.

### Task 5: Séchoir et rations

**Files:**
- Create: `food/DryerBlock.java`, `DryerBlockEntity.java`, `DryerMenu.java`, `DryingRecipe.java`, `client/DryerScreen.java`, recettes `data/oneforallsurvival/recipes/drying/*.json`, tag `dryable_fruits`, `src/devcontent/.../SechoirGameTests.java`.
- Modify: `FoodRegistry.java`, `FoodClient.java`, tags `slow_perishable`, `non_perishable`, plugin JEI du mod.

**Interfaces:**
- Consumes: `FoodTags`, `FoodRegistry.SALT`, tag `forge:salt`, conventions de `GalvanicRecipe` (`/reload`, barrière d'étiquette vide écartée).
- Produces :
  - `FoodRegistry.DRIED_MEAT`, `DRIED_FISH`, `DRIED_FRUIT`, `SALTED_MEAT`, `SALTED_FISH`, `SALTED_FRUIT` ; nourriture 4, 3, 2 ; saturation 0.4.
  - `DryingRecipe` : type `oneforallsurvival:drying`, JSON `{ ingredient, result, salted_result, ticks }` ; `ItemStack resultFor(boolean salted)`.
  - `DryerBlockEntity` : 3000 FE, 20 FE par tick, cases entrée, sel, sortie. Recette par défaut 1200 ticks.

Règles, comme le bain galvanique : paiement en fin de travail, progression gardée sans courant, remise à zéro seulement si la pièce change réellement, détectée au tick. Sel présent : version salée, 1 sel consommé. Un déchet n'est pas une entrée. Le résultat ne garde aucune date.

- [ ] **Step 1: Écrire `SechoirGameTests`** : `leBoeufDonneDeLaViandeSechee`, `avecDuSelCestSale`, `lePoissonEtLeFruit`, `unAlimentRassisSeSecheAussi`, `leSecheEstLent`, `leSaleNePerimeJamais`, `unDechetEstRefuse`, `sansCourantLaProgressionReste`, `laProgressionSurvitAuRechargement`, `leClicNeRemetPasLaProgressionAZero`, `casseIlRendSonContenu`, `leClicMajusculeFonctionne`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter**, avec catégorie JEI à la manière de celle du bain.
- [ ] **Step 4: Lancer, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(food): sechoir electrique et rations`.

### Task 6: Recettes du matériel et visuels finaux

**Files:**
- Create: recettes de table `fridge`, `desalinator`, `dryer` ; modèles, états de blocs, textures ; `tools/gen_textures_lot3.py`.
- Modify: `DemoVisuels` (captures), `PackGameTests`, langues, tags `mineable/pickaxe`.

Les trois machines se fabriquent à l'établi d'ingénieur, comme le matériel du lot 2 : `PackGameTests` vérifie qu'aucune recette de table ne les donne une fois le pack chargé. Ingrédients : plaques et tubes de Create, pièces du lot 2 (bobine, fusible), glace bleue pour l'armoire. Sel et rations ne se fabriquent qu'aux machines.

Visuels, style bricolage et récupération, palette cohérente avec le lot 2, en 16 x 16, script Python/PIL reproductible, aucun fichier téléchargé : quatre états de lampe pour chaque machine ; armoire à l'air de vieux réfrigérateur de cabine, dessalinisateur à cuve et serpentin, séchoir à claies ; objets déchet, sel, bouteille, six rations reconnaissables à côté d'un steak ; fluide teinté. Fonds d'écran en cohérence avec `BenchTheme`.

- [ ] **Step 1: Écrire les tests de `PackGameTests`.**
- [ ] **Step 2: Lancer, constater l'échec** avec `-PwithPack`.
- [ ] **Step 3: Produire recettes, modèles, textures.**
- [ ] **Step 4: Lancer `runDemoVisuels`**, ouvrir les captures, vérifier objets et écrans lisibles.
- [ ] **Step 5: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 6: Commit** `feat(food): recettes du materiel et visuels finaux`.

### Task 7: Preuve de bout en bout, film et vérification

**Files:**
- Create: `dev/E2eVivres.java`, `dev/FilmVivres.java`, `docs/verifications/lot-3.md`.
- Modify: `dev/E2eClient.java`, `build.gradle` (`runDemoVivresFilm`), `tasks/todo.md`.

**E2E sur serveur dédié, avec le pack, un seul passage :** un boeuf reçu est daté et s'empile avec un second ; une date vieillie par commande donne la bonne infobulle ; une marmite de Farmer's Delight accepte un ingrédient daté ; armoire alimentée puis source retirée : aucune perte immédiate (scénario du cahier des charges) ; `/stop`, relance : dates et armoire relues ; dessalinisateur sur une zone `/coast` : eau potable et sel ; bouteille remplie ; séchoir : ration salée ; manger un rassis ; un avarié refusé. Le script ferme son client quel que soit l'écran atteint, et l'essai se termine par `/stop` : ne jamais relancer un client sur un serveur arrêté.

**Film**, monde intégré, durées raccourcies en mémoire et annoncées à l'écran : un steak qui passe de frais à rassis puis déchet à côté de son jumeau dans l'armoire ; le courant coupé ; le dessalinisateur ; le séchoir et les rations. Puis envoi dans la conversation.

- [ ] **Step 1: Écrire E2E et film, lancer.** Lire `[e2e] bilan : TOUT PASSE` dans le journal.
- [ ] **Step 2: Rouvrir le film en images**, vérifier chaque scène.
- [ ] **Step 3: Écrire `docs/verifications/lot-3.md`** : prouvé et non prouvé, séparés. Au moins : équilibrage réel, multijoueur, Citoyen sur serveur dédié, FE d'un autre mod, machines de Create entre un aliment et un coffre, aliment jamais observé.
- [ ] **Step 4: Cocher le lot dans `tasks/todo.md`**, avec ce qui reste ouvert.
- [ ] **Step 5: Commit et push** `test(food): E2E et film du lot 3`, `docs(food): preuve du lot 3`.
