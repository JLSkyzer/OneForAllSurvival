# Lot E1 : la monnaie — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire exister l'argent de Lost Island : cinq pièces avec leur valeur et leur change, une création monétaire limitée aux sources déclarées, des pièces qui ne se rangent qu'aux endroits permis, un coffre-fort et une bourse par joueur, et un tableau de bord pour l'équipe.

**Architecture:** Tout est dans un nouveau paquet `economy`.
- **Valeurs et montants.** Ce sont des fonctions pures testées en JUnit (`CoinMath`).
- **Registre.** Le registre des mouvements et les compteurs de création et de destruction sont un `SavedData` du monde principal. Ce registre sert de modèle aux registres des lots suivants.
- **Coffre-fort.** Son contenu appartient au joueur, pas au bloc : il est gardé dans un `SavedData` indexé par l'UUID du propriétaire. Un prélèvement (lot E6) pourra donc se faire zone déchargée.
- **Rangements interdits.** C'est le premier mixin du mod, sur la case d'inventaire et l'entonnoir. Les pièces au sol deviennent une entité propre, que les automatismes, qui cherchent des `ItemEntity`, ne voient pas.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, Mixin 0.8.5 et MixinGradle 0.7-SNAPSHOT (déjà dans le cache Gradle, rien à télécharger), JUnit 5, GameTests, pack (`-PwithPack`) pour les stockages des autres mods.

**Spec:** `docs/superpowers/specs/2026-11-08-economie-design.md`. Sections utiles :
- section 3 (contraintes) ;
- section 4 (pièces, change, registre) ;
- features 84, 85, 69, 87, 88 et 77 ;
- section 8 (vérification, lignes du lot E1) ;
- section 9 (décisions).

Le rubis s'obtient comme les autres pièces, décision de Killian du 2026-11-12 ; le cahier est à jour.

## Global Constraints

Les contraintes globales du lot 6 restent valables, telles quelles : `docs/superpowers/plans/archive/2026-11-10-lot-6-vie-de-la-ville.md`, section « Global Constraints ». Elles couvrent notamment :
- ASCII dans `gradle.properties` ;
- aucune classe cliente atteignable du serveur ;
- fr et en ;
- `DeferredRegister` ;
- textures produites par script ;
- lecture de « All N required tests passed » ;
- lots de 8 tests au plus ;
- `JoueurDeTest` ;
- un client à la fois ;
- pas de `ddagrab` ;
- commit par nom de fichier.

En plus :
- **Montants en cuivres** (`long`), affichés dans la plus grande coupure : « 2 or 3 argent 5 cuivre ». Valeurs en config `SERVER` : 1, 10, 100, 1 000, 10 000.
- **Aucune recette ne produit une pièce, sauf le change à valeur égale** (10 ↔ 1, du cuivre au rubis). Toute autre recette qui en produirait une, d'un mod ou de CraftTweaker, est **retirée du jeu et signalée en journal** au chargement des données (décision de Killian, 2026-11-12).
- **Les pièces, la bourse et le coffre-fort se fabriquent sur la table, par n'importe qui** : l'établi d'ingénieur ne les prend jamais, quelle que soit sa config.
- **Une commande de l'équipe exige le niveau de permission 2.**
- **Chaque création et chaque destruction de pièces est comptée par source et inscrite au registre**, avec l'auteur, le montant, l'heure et le motif.
- **Mixin.**
  - Les mixins ne visent que des classes de Minecraft et de Forge, jamais un autre mod.
  - Ils sont déclarés dans `oneforallsurvival.mixins.json`, avec une refmap produite par MixinGradle.
  - Ils doivent marcher en développement et dans le jar livré (`reobfJar`). Vérifier les deux.
- **Tout client lancé passe une propriété JVM `oneforallsurvival.*`**, pour que `dev/SourisLibre` rende la souris. Vérifier « [souris] rendue » dans son journal.

## Review Focus

1. **Duplication.**
   - **Situations :** une déconnexion pendant un retrait du coffre-fort ; deux clics rapides sur le même bouton ; un coffre-fort cassé puis reposé ; une bourse vidée puis jetée.
   - **Attendu :** aucune pièce créée. Testé en tâches 4 et 5.
2. **Pièces cachées.**
   - **Cas couverts :** clic, maj-clic et touche numérique dans un coffre, une boîte de Shulker, le coffre de l'Ender, un tonneau ou le stockage d'un mod du pack ; un entonnoir ou un tapis de Create sous une pièce au sol ; un cadre ; un porte-armure ; un allay.
   - **Attendu :** la pièce est refusée partout. Testé en tâche 3.
3. **Une bourse pleine est une pièce** : tout ce qui refuse une pièce refuse aussi une bourse qui en contient. Testé en tâches 3 et 5.
4. **Compte juste.** Après une pièce jetée dans la lave, dans le vide, ou disparue au sol, le total créé moins le total détruit égale ce qui existe encore. Testé en tâche 2.
5. **Somme de départ** donnée une seule fois par joueur : ni à la reconnexion, ni après un changement de nom, ni après la mort. Testé en tâche 2.

## Décisions prises par ce plan (à valider par Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Pièces au sol | Une pièce jetée devient une **entité du mod** (`coin_drop`) au lieu d'un objet ordinaire. Seuls les joueurs la ramassent. Les entonnoirs, les tapis et entonnoirs de Create et les aimants des autres mods ne la voient pas. Elle disparaît après 5 min, compté comme détruit. | « Les automatismes ne prennent pas une pièce au sol », sans mixin dans chaque mod |
| Blocage dans les écrans | Les mixins sur `Slot.mayPlace` et `SlotItemHandler.mayPlace` refusent une pièce, ou une bourse pleine, dans toute case qui n'appartient pas à l'inventaire du joueur ni à un rangement permis. Les rangements permis sont une liste de types d'écran : bourse, coffre-fort, puis ceux des lots suivants. Un fichier de config permet d'en ajouter ou d'en retirer. | Décision de Killian : « mixin sur la case » |
| Clic droit sur un bloc de stockage sans écran (dépôt de Create, etc.) | Avec une pièce ou une bourse pleine en main, le clic droit est refusé sur tout bloc qui expose un stockage d'objets, n'a pas d'écran, et n'est pas un rangement permis. | Ces blocs prennent l'objet tenu sans passer par une case |
| Cadres, porte-armures, allays | Avec une pièce ou une bourse pleine en main, l'interaction est refusée. | Feature 84 |
| Coffre-fort | **Un par joueur.** Seuls le propriétaire et l'équipe l'ouvrent. Le propriétaire ne le casse que vide. L'équipe peut le casser plein : le contenu reste au nom du propriétaire et réapparaît dans son prochain coffre-fort. Pas de limite de quantité, 5 lignes (une par coupure) avec déposer et retirer. Le forçage au crochetage arrive avec le lot du policier (feature 67), l'impôt avec celui du maire. | Feature 85 ; « contenu gardé par le serveur, pas seulement dans le bloc » |
| Bourse | Un objet qui garde le nombre de chaque coupure, sans limite. Portée n'importe où dans l'inventaire, elle **ramasse** les pièces au sol. Clic droit : un écran pour déposer et retirer. Son infobulle donne le total. Elle offre au code l'opération `payer(montant)` qui rend la monnaie exacte, utilisée à partir du lot E2. | Feature 69 |
| Somme de départ | 500 cuivres, soit 5 pièces d'or, donnés à la **première connexion** d'un UUID et notés dans un `SavedData`. Si l'inventaire est plein, le reste tombe au sol aux pieds du joueur. | Feature 88 |
| Commandes de l'équipe | `/eco give <joueur> <montant> <motif>`, `/eco take <joueur> <montant> <motif>` (prend dans l'inventaire puis la bourse, jamais partiel), `/eco stats`, `/eco ledger [page]` | Features 87 et 77 |
| Tableau de bord | Il montre : le créé par source (butin et quêtes à 0 jusqu'aux lots E4 et E5) ; le détruit par cause (lave et feu, vide, disparition au sol, retrait par l'équipe) ; la masse exacte des coffres-forts ; la masse en circulation (créé moins détruit). Les lignes des lots suivants (trésor, salaires, factures, ventes) sont marquées « pas encore en service ». | Feature 77 |
| Registre | 10 000 lignes au plus, les plus anciennes effacées d'abord, mais les totaux par source ne sont jamais effacés. | « Nombre de lignes plafonné » |
| Visuels | Les pièces reprennent les modèles et textures 64 × 64 de `art/pieces-monnaie/`. Bourse et coffre-fort : textures produites par script, dans le style des lots précédents. | Cahier, section 3 |

## Fichiers

| Fichier | Rôle |
|---|---|
| `economy/EconomyRegistry.java`, `EconomyConfig.java` | Registres, config `SERVER` `oneforallsurvival-economy.toml` |
| `economy/Coin.java`, `CoinItem.java`, `CoinMath.java` | Coupures, valeurs, décomposition et affichage |
| `economy/CoinRecipeGuard.java` | Retrait des recettes qui produiraient une pièce |
| `economy/EconomyLedger.java` (SavedData), `MoneySource.java`, `MoneySink.java` | Registre et comptes de création et de destruction |
| `economy/CoinDropEntity.java`, `client/CoinDropRenderer.java` | Pièce au sol |
| `economy/MoneyStorageRules.java`, `mixin/SlotMixin.java`, `mixin/SlotItemHandlerMixin.java`, `mixin/HopperBlockEntityMixin.java`, `oneforallsurvival.mixins.json` | Rangements permis |
| `economy/SafeBlock.java`, `SafeBlockEntity.java`, `SafeAccounts.java` (SavedData), `SafeMenu.java`, `client/SafeScreen.java`, `SafeActionPacket.java` | Coffre-fort |
| `economy/PurseItem.java`, `Purse.java`, `PurseMenu.java`, `client/PurseScreen.java` | Bourse |
| `economy/StartingMoney.java`, `EconomyCommands.java` | Somme de départ, commandes de l'équipe |
| `src/devcontent/.../{Monnaie,Rangement,CoffreFort,Bourse,Registre}GameTests.java`, `E2eMonnaie.java`, `FilmMonnaie.java` | Preuves |
| `tools/gen_textures_e1.py` | Textures de la bourse et du coffre-fort |

---

### Task 1: Pièces, valeurs, change et garde des recettes

**Files:**
- Create : `economy/EconomyRegistry.java`, `EconomyConfig.java`, `Coin.java`, `CoinItem.java`, `CoinMath.java`, `CoinRecipeGuard.java`, les modèles et textures des 5 pièces (copiés depuis `art/pieces-monnaie/assets/`), les 8 recettes de change, `src/test/java/dev/oneforallsurvival/economy/CoinMathTest.java`, `src/devcontent/.../MonnaieGameTests.java`.
- Modify : `OneForAllSurvival.java` (registre, config), `bench/BenchCatalog.java` (les objets de l'économie restent sur la table), l'onglet créatif, les langues.

**Interfaces:**
- Produces :
  - `enum Coin { COPPER, SILVER, GOLD, EMERALD, RUBY }`, avec `value() : long` lu dans la config, `item() : Item` et `fromItem(Item) : Optional<Coin>`.
  - Les ids des objets sont `copper_coin`, `silver_coin`, `gold_coin`, `emerald_coin` et `ruby_coin`.
  - `EconomyConfig` : `coinValue(Coin)` vaut 1, 10, 100, 1000 ou 10000. Les valeurs doivent être strictement croissantes, sinon on revient aux valeurs par défaut et un avertissement est écrit en journal.
- Produces : les fonctions pures de `CoinMath` :
  - `valueOf(ItemStack) : long` (0 pour un objet qui n'est pas une pièce) ;
  - `decompose(long amount) : EnumMap<Coin, Long>`, glouton, de la plus grande à la plus petite coupure ;
  - `format(long amount) : Component`, du type « 2 or 3 argent 5 cuivre », par clés de langue ;
  - `makeChange(EnumMap<Coin,Long> available, long price) : Optional<Payment>`, où `Payment(EnumMap<Coin,Long> taken, EnumMap<Coin,Long> change)`.
    - On prend le moins de pièces possible : d'abord les coupures exactes, puis la plus petite pièce plus grande que le reste dû, que l'on casse.
    - La monnaie rendue est décomposée gloutonnement.
    - Le résultat est vide si `available` ne suffit pas.
- Produces : `CoinRecipeGuard.filter(RecipeManager, RegistryAccess) : List<ResourceLocation>`, qui renvoie les recettes retirées.

Règles :
- Le change se fait par 8 recettes `crafting_shapeless` sous `data/oneforallsurvival/recipes/coin_change/` : 10 cuivre → 1 argent, 1 argent → 10 cuivre, et ainsi de suite jusqu'au rubis.
- `CoinRecipeGuard` passe au même moment que `BenchCatalog` (après `TagsUpdatedEvent` `SERVER_DATA_LOAD`, sur le même `RecipeManager`). Il retire toute recette, de tout type, dont le résultat est une pièce ou une bourse, sauf les 8 recettes de change. Il écrit en journal l'id et la classe de chaque recette retirée.
- L'établi ne prend jamais une pièce, la bourse, le coffre-fort ni une recette de change. Cette règle est dans le code, pas seulement dans la config.
- L'infobulle d'une pièce donne sa valeur ; pour une pile, elle donne aussi le total.
- Une pièce ne brûle pas dans un four (aucune recette de cuisson) et ne se composte pas. Elle reste un objet ordinaire pour la valeur de compostage, 0.

- [ ] **Step 1: Écrire `CoinMathTest`** :
  - `decomposeGlouton` (2305 → 2 or, 3 argent, 5 cuivre) ;
  - `formatPlusGrandeCoupure` ;
  - `monnaieExacte` (12 cuivres pour un prix de 7 → on prend 7, on rend 5) ;
  - `onCasseLaPlusPetitePieceSuffisante` (1 or et 3 argent pour 25 → on prend 3 argent ; or seul pour 25 → on prend 1 or, on rend 7 argent et 5 cuivre) ;
  - `insuffisantVide` ;
  - `valeurDUnePile`.
- [ ] **Step 2: Écrire `MonnaieGameTests`** :
  - `lesCinqPiecesExistent` ;
  - `leChangeVaDansLesDeuxSens` (les 8 recettes, à valeur égale) ;
  - `uneRecetteQuiFabriqueUnePieceEstRetiree` (le test injecte une recette en mémoire, puis appelle `filter`) ;
  - `leChangeResteSurLaTable` (absent du catalogue de l'établi) ;
  - `uneInfobulleDonneLaValeur`.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`**, puis la même chose avec `-PwithPack`. Lire « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(economy): pieces, change et garde des recettes`.

### Task 2: Registre, pièces au sol, sources et tableau de bord

**Files:**
- Create : `economy/EconomyLedger.java`, `MoneySource.java`, `MoneySink.java`, `CoinDropEntity.java`, `client/CoinDropRenderer.java`, `StartingMoney.java`, `EconomyCommands.java`, `src/devcontent/.../RegistreGameTests.java`.
- Modify : `EconomyRegistry` (entité `coin_drop`), `EconomyConfig`, `OneForAllSurvival.java` (commandes), langues.

**Interfaces:**
- Consumes : `Coin`, `CoinMath` (tâche 1).
- Produces :
  - `enum MoneySource { MOB_LOOT, QUEST, STARTING, STAFF }` et `enum MoneySink { FIRE, VOID, DESPAWN, STAFF }`.
  - `EconomyLedger.get(MinecraftServer)` : `created(MoneySource, long amount, @Nullable UUID author, String reason)`, `destroyed(MoneySink, long amount, @Nullable UUID author, String reason)`, `totalCreated(MoneySource) : long`, `totalDestroyed(MoneySink) : long`, `entries(int page) : List<Entry>`.
  - `record Entry(long epochMillis, @Nullable UUID author, long amount, String kind, String reason)`.
  - `EconomyConfig` : `ledgerMaxLines()` vaut 10000 par défaut ; `startingMoney()` vaut 500 par défaut (de 0 à 1 000 000).
  - `CoinDropEntity`, créée par `CoinItem#createEntity` (`hasCustomEntity` vrai) chaque fois qu'une pièce tomberait au sol.
  - `StartingMoney.grantIfNew(ServerPlayer) : boolean`.

Règles :
- **Pièce au sol** (`CoinDropEntity`) :
  - elle n'est pas une `ItemEntity`, elle a la même physique ;
  - elle se dessine comme l'objet ;
  - un joueur qui la touche la ramasse, dans sa bourse si elle en a une (tâche 5), sinon dans l'inventaire ;
  - elle se fond avec ses voisines de même coupure ;
  - elle résiste aux explosions.
  - Une pièce détruite est comptée dans le registre au moment où elle disparaît :
    - feu ou lave → `FIRE` ;
    - sous le monde → `VOID` ;
    - après 6000 ticks au sol → `DESPAWN`.
  - Un déchargement de chunk ou un changement de dimension n'est pas une destruction.
- **Somme de départ** :
  - à `PlayerLoggedInEvent`, si l'UUID est inconnu du `SavedData`, le joueur reçoit `decompose(startingMoney)` dans son inventaire ;
  - ce qui ne rentre pas tombe au sol en `CoinDropEntity` ;
  - la somme est comptée `STARTING` ;
  - l'UUID est noté avant de donner.
- **Commandes** (niveau 2) :
  - `give` crée, comptée `STAFF`.
  - `take` prend dans l'inventaire puis dans la bourse, rend la monnaie si besoin, et n'est jamais partiel ; elle est comptée `STAFF` en destruction. Un montant manquant fait échouer la commande sans rien prendre.
  - `stats` affiche le tableau de bord décrit dans les décisions.
  - `ledger` pagine le registre.

- [ ] **Step 1: Écrire `RegistreGameTests`** :
  - `uneInjectionEstCompteeEtNotee` ;
  - `unRetraitNestJamaisPartiel` ;
  - `laSommeDeDepartUneSeuleFois` (connexion, reconnexion, mort, nouvelle connexion, avec un `JoueurDeTest` dont l'UUID est fixe) ;
  - `inventairePleinLeResteTombe` ;
  - `unePieceDansLaLaveEstDetruite` (créé − détruit = ce qui reste) ;
  - `unePieceDansLeVideEstDetruite` ;
  - `unePieceQuiDisparaitAuSolEstDetruite` ;
  - `unEntonnoirNeRamassePasUnePiece` ;
  - `leRegistreEstPlafonneMaisPasLesTotaux` ;
  - `leRegistreSurvitAuRechargement`.
  - Deux lots si besoin.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`**, puis la même chose avec `-PwithPack`. Lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): registre, pieces au sol, somme de depart et commandes`.

### Task 3: Où les pièces peuvent être rangées (mixin)

**Files:**
- Create : `economy/MoneyStorageRules.java`, `mixin/SlotMixin.java`, `mixin/SlotItemHandlerMixin.java`, `mixin/HopperBlockEntityMixin.java`, `src/main/resources/oneforallsurvival.mixins.json`, `src/devcontent/.../RangementGameTests.java`.
- Modify : `build.gradle` (plugin MixinGradle, processeur d'annotations, `mixin { add …; config … }`, propriétés de lancement, manifeste du jar), `gradle.properties` si besoin, langues.

**Interfaces:**
- Produces : `MoneyStorageRules`, avec :
  - `isMoney(ItemStack) : boolean` : vrai pour une pièce, ou pour une bourse qui contient au moins une pièce ;
  - `isAllowedMenu(AbstractContainerMenu) : boolean` ;
  - `mayHold(Slot slot, ItemStack stack) : boolean` : faux si `isMoney(stack)` et si la case n'est ni dans l'`Inventory` du joueur ni dans un écran permis ;
  - `register(MenuType<?>)`, que les lots suivants appellent pour leurs rangements.
- Produces : `EconomyConfig.extraAllowedMenus()` et `extraForbiddenMenus()`, deux listes d'ids de type d'écran.

Règles :
- **`SlotMixin`** : injection en tête de `Slot#mayPlace`, qui renvoie faux si `!mayHold`. `SlotItemHandlerMixin` fait de même sur la classe de Forge. Ces deux mixins couvrent le clic, le maj-clic, la touche numérique et la répartition par glissé, qui passent tous par `mayPlace`.
- **Retour d'une pièce dans une case interdite** : un balayage de garde rend au joueur toute pièce trouvée dans une case interdite d'un écran ouvert. Il passe à `PlayerContainerEvent.Close` et une fois par seconde tant que l'écran reste ouvert. Ce cas est possible quand un mod surcharge `mayPlace` sans appeler l'original. Chaque retour est noté en journal avec le type d'écran, pour l'ajouter à la liste.
- **`HopperBlockEntityMixin`** : un entonnoir ne pousse jamais `isMoney` dans un conteneur. Ceinture et bretelles : à ce stade, aucune pièce ne devrait se trouver dans un entonnoir.
- **Clic droit sur un bloc** : avec `isMoney` en main, un clic droit sur un bloc dont l'entité expose `ITEM_HANDLER` est refusé (`setUseBlock(DENY)`, `setUseItem(DENY)`), si ce bloc n'ouvre pas d'écran (n'est pas un `MenuProvider`) et n'est pas un rangement permis.
- **Cadres et porte-armures** : avec `isMoney` en main, l'interaction est annulée.
- **Allay** : on ne peut pas lui donner `isMoney`.
- **Coffre de l'Ender et boîte de Shulker** : ils sont couverts par leurs cases. Ajouter une vérification sur la boîte de Shulker cassée qui garde son contenu, au cas où une pièce y serait entrée par un chemin non prévu : elle reste dedans, mais le balayage la signale.

- [ ] **Step 1: Écrire `RangementGameTests`** (joueur `JoueurDeTest`, écran ouvert côté serveur, essais par `clicked`/`quickMoveStack`) :
  - `refuseeDansUnCoffre` ;
  - `refuseeDansUnTonneau` ;
  - `refuseeDansUneBoiteDeShulker` ;
  - `refuseeDansLeCoffreDeLEnder` ;
  - `majClicRefuse` ;
  - `toucheNumeriqueRefusee` ;
  - `uneBourseVidePasse` ;
  - `uneBoursePleineRefusee` ;
  - `acceptéeDansLInventaire` ;
  - `cadreEtPorteArmureRefuses` ;
  - `unEntonnoirNePoussePasDePiece`.
  - Avec le pack, `lesStockagesDesModsInstallesRefusent` : pour chaque type d'entité de bloc d'un autre mod qui est un `MenuProvider`, poser le bloc, ouvrir son écran pour le joueur de test et tenter un maj-clic avec une pièce. La pièce ne doit être dans aucune case de l'écran. La liste des types essayés est écrite en journal.
  - Toujours avec le pack, `unTapisDeCreateNeRamassePasUnePiece`.
  - Lots de 8 au plus.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter**, mixins compris.
- [ ] **Step 4: Lancer sans puis avec `-PwithPack`, lire** « All N required tests passed ». Vérifier dans le journal que les mixins sont appliqués (« Mixing … »). Vérifier aussi le jar livré : `./gradlew --offline build`, puis s'assurer que `oneforallsurvival.mixins.json` et la refmap sont bien dans le jar et que le manifeste porte `MixinConfigs`.
- [ ] **Step 5: Commit** `feat(economy): les pieces ne se rangent qu'aux endroits permis`.

### Task 4: Coffre-fort personnel

**Files:**
- Create : `economy/SafeBlock.java`, `SafeBlockEntity.java`, `SafeAccounts.java`, `SafeMenu.java`, `SafeActionPacket.java`, `client/SafeScreen.java`, `client/EconomyClient.java`, `src/devcontent/.../CoffreFortGameTests.java`.
- Modify : `EconomyRegistry`, `MoneyStorageRules.register(SAFE_MENU)`, le canal réseau du mod (`bench/BenchNetwork.java`), langues.

**Interfaces:**
- Consumes : `Coin`, `CoinMath`, `MoneyStorageRules`.
- Produces :
  - `SafeAccounts.get(MinecraftServer)`, avec :
    - `balance(UUID) : EnumMap<Coin, Long>` ;
    - `total(UUID) : long` ;
    - `deposit(UUID, Coin, long)` ;
    - `withdraw(UUID, Coin, long) : boolean` ;
    - `safePos(UUID) : Optional<GlobalPos>` ;
    - `totalMass() : long`, pour le tableau de bord.
  - `SafeBlockEntity` : `owner() : UUID`.
  - Bloc `safe`.

Règles :
- **Pose** :
  - la pose note le propriétaire ;
  - elle est refusée, avec un message, si le joueur a déjà un coffre-fort ;
  - elle est refusée sans joueur (distributeur, etc.).
- **Ouverture** : seuls le propriétaire et l'équipe (niveau 2) ouvrent ; les autres reçoivent un message.
- **Écran** :
  - il montre 5 lignes, une par coupure, avec le nombre, le total et les boutons « déposer tout » et « retirer N » (1, 10, 64) ;
  - il affiche le total en haut.
- **Paquet** : vérifié côté serveur (propriétaire ou équipe, distance, bloc toujours là).
  - Chaque opération est atomique : retirer ne donne rien si le compte n'a pas assez.
  - Ce qui ne rentre pas dans l'inventaire n'est pas retiré.
- **Casse** :
  - le propriétaire ne le casse que si son compte est à 0 ;
  - l'équipe peut le casser plein, le compte reste et `safePos` est effacé ;
  - personne d'autre ne le casse (dureté réservée, comme le mécanisme ancien) ;
  - il ne lâche que son bloc, le contenu ne tombe jamais ;
  - il résiste aux explosions, au Wither et au dragon (tags), et aux pistons.
- **Persistance** : le compte vit dans `SafeAccounts`, indépendant du chunk.
- **Pas couvert ici** : le crochetage (feature 67) arrive avec le lot du policier, l'impôt avec celui du maire. Le dire dans le rapport.

- [ ] **Step 1: Écrire `CoffreFortGameTests`** :
  - `unSeulParJoueur` ;
  - `unAutreNeLOuvrePas` ;
  - `lEquipeLOuvre` ;
  - `deposerPuisRetirerAValeurEgale` ;
  - `retirerPlusQueLeSoldeNeDonneRien` ;
  - `inventairePleinRienNeSePerd` ;
  - `leProprietaireNeLeCassePasPlein` ;
  - `lEquipeLeCassePleinEtLeCompteReste` ;
  - `leCompteSurvitAuRechargementEtZoneDechargee` ;
  - `explosionEtPistonSansEffet` ;
  - `deuxClicsRapidesNeDupliquentPas`.
  - Deux lots.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`**, puis la même chose avec `-PwithPack`. Lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): coffre-fort personnel`.

### Task 5: Bourse

**Files:**
- Create : `economy/PurseItem.java`, `Purse.java`, `PurseMenu.java`, `client/PurseScreen.java`, `src/devcontent/.../BourseGameTests.java`.
- Modify : `CoinDropEntity` (ramassage dans la bourse), `EconomyCommands` (`take` lit la bourse), `MoneyStorageRules.register(PURSE_MENU)`, `EconomyRegistry`, langues.

**Interfaces:**
- Consumes : `CoinMath.makeChange`, `MoneyStorageRules.isMoney`.
- Produces : `Purse`, avec :
  - `contents(ItemStack) : EnumMap<Coin, Long>` (NBT `Coins`, un `long` par coupure) ;
  - `add(ItemStack, Coin, long)` ;
  - `total(ItemStack) : long` ;
  - `pay(ServerPlayer, long price) : boolean`.
    - Le paiement prend d'abord dans les bourses de l'inventaire, puis dans les pièces de l'inventaire, avec `makeChange`. La monnaie rendue va dans la bourse.
    - Il n'est jamais partiel.
    - Le montant payé est renvoyé à l'appelant ; c'est lui qui dit où il va, à partir du lot E2.
- Produces : `Purse.firstPurse(Player) : Optional<ItemStack>`.
- Objet `purse`.

Règles :
- **Ramassage** : une pièce au sol touchée par un joueur qui porte une bourse va dans la première bourse de son inventaire.
- **Écran** : clic droit en main ouvre l'écran (déposer les pièces de l'inventaire, retirer par coupure), avec le total.
- **Infobulle** : elle donne le total.
- **Pile** : une bourse ne s'empile pas.
- **Bourse jetée** : elle reste une bourse au sol, ordinaire. Elle n'est pas convertie en `CoinDropEntity`, ce qui permet de la perdre ou de se la faire voler. Les automatismes doivent quand même ne pas la ramasser quand elle est pleine : dans ce cas, la bourse jetée devient elle aussi une entité du mod. Ce point est à trancher dans le rapport s'il pose problème.

- [ ] **Step 1: Écrire `BourseGameTests`** :
  - `elleRamasseLesPieces` ;
  - `payerRendLaMonnaieExacte` ;
  - `payerPlusQueLeContenuNeTouchePasLaBourse` ;
  - `retirerParCoupure` ;
  - `uneBoursePleineJeteeNEstPasRamasseeParUnEntonnoir` ;
  - `uneBoursePleineRefuseeDansUnCoffre` (rappel de la tâche 3) ;
  - `viderPuisJeterNeDupliquePas`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`**, puis la même chose avec `-PwithPack`. Lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): bourse`.

### Task 6: Recettes et visuels finaux

**Files:**
- Create :
  - les recettes de table de `purse` (cuir, ficelle) et de `safe` (fer, lingots, un mécanisme simple de vanilla). N'importe qui les fabrique : elles sont **sans** condition `mod_loaded` et ne passent pas à l'établi.
  - les modèles, la texture de l'entité au sol, la bourse (objet), le coffre-fort (bloc orientable, porte), et `tools/gen_textures_e1.py`.
- Modify : `DemoVisuels` (une zone à part, hors du champ des lots précédents), `PackGameTests` (pièces, bourse et coffre-fort fabricables sur la table, jamais à l'établi), langues, `mineable`.

Captures :
- les 5 pièces en main et au sol, avec une pile et son infobulle ;
- la bourse et son écran, avec un total ;
- le coffre-fort seul, contre un mur, et son écran avec un total ;
- la grille des objets à côté de leurs équivalents vanilla.

- [ ] **Step 1: Écrire les tests de `PackGameTests`.**
- [ ] **Step 2: Lancer avec `-PwithPack`, constater l'échec.**
- [ ] **Step 3: Produire recettes, modèles, textures et images.**
- [ ] **Step 4: Lancer `runDemoVisuels`**, ouvrir les captures et les vérifier.
- [ ] **Step 5: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 6: Commit** `feat(e1): recettes et visuels de la monnaie`.

### Task 7: Preuve de bout en bout, film, vérification

**Files:**
- Create : `dev/E2eMonnaie.java`, `dev/FilmMonnaie.java`, `docs/verifications/lot-e1.md`.
- Modify : `dev/E2eClient.java` (étape monnaie), `build.gradle` (`runDemoMonnaieFilm`), `wiki/data/economie.json`, `tasks/todo.md`.
  - Wiki : les entrées `pieces`, `ou-ranger`, `coffre-fort`, `bourse`, `depart`, `injection` et `tableau-de-bord` passent à `dispo`, au format des entrées `dispo` de `electricite.json`. L'entrée `sources` reste `prevu` tant que le butin et les quêtes manquent, mais elle signale la somme de départ et l'injection comme livrées.
  - `todo.md` : une nouvelle section « Économie » avec les lots E1 à E6, E1 coché.

**Essai sur serveur dédié avec le pack**, un passage puis une relance :
- **Joueurs et somme de départ** :
  - un joueur neuf reçoit 500 cuivres ;
  - après une déconnexion et une reconnexion, il ne reçoit rien de plus.
- **Change** : il change 5 or en argent à la table.
- **Rangements interdits** :
  - il tente de ranger une pièce dans un coffre, puis dans le stockage d'un mod du pack : la pièce est refusée ;
  - il jette une pièce sur un tapis de Create : le tapis ne la prend pas.
- **Bourse et coffre-fort** :
  - il fabrique une bourse, qui ramasse les pièces ;
  - il fabrique un coffre-fort, y dépose 300 cuivres, puis échoue à en poser un second ;
  - un second joueur, sans droits, ne l'ouvre pas.
- **Injection et retrait par l'équipe** :
  - l'équipe injecte 1 rubis, puis retire 50 cuivres ;
  - le registre et `/eco stats` donnent les bons totaux.
- **Après `/stop` et relance** :
  - le solde du coffre-fort, le registre et les totaux sont relus ;
  - la somme de départ n'est pas redonnée.

**Film**, en monde intégré : les pièces et le change ; la bourse qui ramasse ; le coffre refusé ; le coffre-fort ; `/eco stats`. Filmer avec `FrameRecorder`/`FilmCapture` ou `gfxcapture`, jamais `ddagrab`.

- [ ] **Step 1: Écrire l'E2E et le film, lancer.** Lire « [e2e] bilan : TOUT PASSE » au passage complet et à la relance, et « [souris] rendue » dans le journal du client.
- [ ] **Step 2: Rouvrir le film en images**, vérifier chaque scène.
- [ ] **Step 3: Écrire `docs/verifications/lot-e1.md`.**
  - Séparer ce qui est prouvé de ce qui ne l'est pas, avec les comptes et les heures.
  - Il couvre au moins :
    - le crochetage, renvoyé au lot du policier ;
    - l'impôt, renvoyé au lot du maire ;
    - les stockages de mods non installés ;
    - le multijoueur à plus de deux ;
    - l'équilibrage réel.
- [ ] **Step 4: Mettre à jour le wiki et `tasks/todo.md`.**
- [ ] **Step 5: Commit et push** `test(e1): E2E et film`, `docs(e1): preuve du lot E1`.
