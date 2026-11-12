# Lot 0 : socle électrique et système de rôles — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prouver que le courant des trois réseaux arrive dans nos machines et traverse un bloc en ligne, et livrer le système de rôles avec préfixes dans le chat, au-dessus de la tête et dans le TAB.

**Architecture:** Les rôles sont une logique pure (`RoleBook`) persistée par une `SavedData`, et affichée par des équipes de scoreboard vanilla : le préfixe d'une équipe apparaît déjà dans ces trois endroits et se synchronise sans paquet ni code client. La machine est une classe de base exposant la capability Forge Energy. Blocs d'essai et GameTests vivent dans un source set `devcontent`, jamais empaqueté.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests Forge.

**Spec:** `docs/superpowers/specs/archive/2026-11-06-features-lost-island-design.md` (sections 3, 4 et 7). Lots suivants : `tasks/todo.md`.

## Global Constraints

- `gradle.properties` reste en ASCII pur ; les textes accentués vont dans les fichiers de langue (`fr_fr.json`, repli `en_us.json`) ou dans du code Java en UTF-8.
- N'écrire `${` dans `mods.toml` et `pack.mcmeta` que pour une propriété réellement définie.
- Constructeur `@Mod` : `FMLJavaModLoadingContext` en paramètre, jamais `IEventBus`. Borne Forge `[47.4,)`.
- Toutes les machines consomment du Forge Energy par `ForgeCapabilities.ENERGY`.
- Rien de `devcontent` dans le mod livré : vérifier le contenu de `build/libs/*.jar` en fin de lot.
- La consommation et la capacité viennent de la config pour chaque machine réelle, à partir du lot 2. Les valeurs du lot 0 ne concernent que le contenu d'essai.
- Dépôts `fg.deobf` déclarés avec `content { includeGroup … }`, jamais `exclusiveContent`.
- Tâches `run*` configurées par `tasks.matching {}.configureEach`, jamais `tasks.named`.
- Commit et push après chaque tâche vérifiée, en ajoutant les fichiers par nom. Message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Claude n'accepte pas l'EULA de Minecraft à la place de Killian : `run-server/eula.txt` est validé par lui.

## Review Focus

1. Un joueur dont le rôle est supprimé alors qu'il est hors ligne doit redevenir Citoyen à son retour.
2. Un libellé accentué (« Pêcheur ») doit survivre à la commande, à la sauvegarde et au redémarrage.
3. Après un redémarrage, une équipe `ofas_` orpheline disparaît, sans toucher aux équipes créées à la main par l'équipe du serveur.
4. Un bloc en ligne sans voisin ou face à une machine pleine doit refuser le courant, sans en faire disparaître.
5. Une machine qui perd le courant garde sa progression, y compris après rechargement du monde.

---

## Fichiers

| Fichier (sous `dev/oneforallsurvival/`) | Rôle |
|---|---|
| `src/main/java/.../role/Role.java` | Donnée d'un rôle |
| `src/main/java/.../role/RoleBook.java` | Logique pure : rôles, attributions, NBT |
| `src/main/java/.../role/RoleSavedData.java` | Persistance dans le monde |
| `src/main/java/.../role/RoleTeams.java` | Traduction en équipes de scoreboard |
| `src/main/java/.../role/RoleEvents.java` | Démarrage du serveur et connexion |
| `src/main/java/.../role/RoleCommands.java` | Commande `/role` |
| `src/main/java/.../role/RoleGate.java` | Réserve des actions à l'Ingénieur |
| `src/main/java/.../machine/MachineState.java` | Les quatre états |
| `src/main/java/.../machine/MachineEnergyStorage.java` | Réserve de FE |
| `src/main/java/.../machine/ElectricMachineBlock.java` | Bloc de base |
| `src/main/java/.../machine/ElectricMachineBlockEntity.java` | Logique de base |
| `src/main/java/.../machine/InlineEnergyBlockEntity.java` | Passage de courant, base du compteur et du délestage |
| `src/devcontent/java/.../dev/` | Blocs d'essai et GameTests |
| `src/test/java/.../role/RoleBookTest.java` | Tests JUnit de la logique des rôles |

---

### Task 1 : environnement de test et pack de développement

**Files:**
- Modify: `build.gradle`, `gradle.properties`, `.gitignore`, `src/main/resources/META-INF/mods.toml`
- Create: `src/devcontent/resources/data/oneforallsurvival/structures/test/plancher.nbt` (copie de `D:\Mods Minecraft\AXIONENGINE\java\axion-mod\src\devcontent\resources\data\axion\structures\test\plancher.nbt`)
- Create: `docs/verifications/lot-0.md`

**Interfaces:**
- Produces: source set `devcontent`, runs `client`, `client2`, `server`, `gameTestServer`, tests JUnit, et gabarit de GameTest `test/plancher`.

- [ ] **Step 1 : déclarer les dépôts et le source set**

Dépôts avec `content { includeGroup }` : `https://api.modrinth.com/maven` (`maven.modrinth`), `https://cursemaven.com` (`curse.maven`), `https://maven.blamejared.com` (`com.blamejared.crafttweaker`). Source set `devcontent` calqué sur `AXIONENGINE/java/axion-mod/build.gradle` : `devcontentImplementation.extendsFrom implementation`, sortie de `main` en dépendance, ajouté à `mods { "${mod_id}" { source … } }`.

- [ ] **Step 2 : déclarer les dépendances**

Fixer les versions en `gradle.properties` (ASCII). `implementation fg.deobf("maven.modrinth:farmers-delight:1.20.1-1.3.4")`, et le pack en `runtimeOnly fg.deobf(...)`, sauf si une tâche demandée contient `GameTest` (`gradle.startParameter.taskNames`), pour garder les GameTests rapides :

```text
maven.modrinth:create:mc1.20.1-6.0.8
maven.modrinth:createaddition:forge-1.20.1-1.3.3
maven.modrinth:create-new-age:1.2.0+mc1.20.1
curse.maven:create-advanced-electricity-addon-1743391:9362725
maven.modrinth:hospitals:4.0.0
curse.maven:medical-remedies-1185929:8023446
curse.maven:first-aid-276837:5531834
com.blamejared.crafttweaker:CraftTweaker-forge-1.20.1:14.0.60
```

JUnit : `testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'`, `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`, `useJUnitPlatform()`. Dans `mods.toml`, Farmer's Delight (`farmersdelight`) devient une dépendance obligatoire de plage `[0,)`, à resserrer une fois lu le format de version que déclare son jar.

- [ ] **Step 3 : déclarer les runs**

Dans `configureEach` : `mixin.env.remapRefMap=true` et `mixin.env.refMapRemappingFile=${projectDir}/build/createSrgToMcp/output.srg`. `server` dans `run-server`, `gameTestServer` dans `run-gametest` avec effacement de son monde en `doFirst`, `client2` (`parent minecraft.runs.client`, dossier `run-client2`, `--username Dev2`). Ajouter ces trois dossiers au `.gitignore`.

- [ ] **Step 4 : lancer le pack complet**

Run: `./gradlew runClient`, attendre le menu principal puis arrêter le processus.
Expected : `run/logs/latest.log` montre le chargement de tous les mods et aucun `Failed to create mod instance`. Noter dans `docs/verifications/lot-0.md` les mod ids lus et les versions. Si un mod du pack refuse de se charger (Hospitals recommande Forge 47.4.10), **s'arrêter et le signaler à Killian**, sans le retirer.

- [ ] **Step 5 : commit**

```bash
git add build.gradle gradle.properties .gitignore src/main/resources/META-INF/mods.toml src/devcontent docs/verifications/lot-0.md
git commit -m "build: ajoute le pack de developpement et l'environnement de test"
git push
```

---

### Task 2 : logique des rôles

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/role/Role.java`, `RoleBook.java`
- Test: `src/test/java/dev/oneforallsurvival/role/RoleBookTest.java`

**Interfaces:**
- Produces:
  - `record Role(String id, String label, ChatFormatting color, boolean builtin)`
  - `RoleBook.DEFAULT_ROLE_ID = "citoyen"`, `RoleBook.ENGINEER_ROLE_ID = "ingenieur"`
  - `static RoleBook withBuiltins()`, `Collection<Role> roles()`, `Optional<Role> role(String id)`, `Role roleOf(UUID player)`
  - `void assign(UUID player, String roleId)`, `void createRole(String id, String label, ChatFormatting color)`, `void editRole(String id, String label, ChatFormatting color)`, `void deleteRole(String id)`
  - `CompoundTag save()`, `static RoleBook load(CompoundTag tag)`

Rôles codés en dur : `medecin` « Médecin » `RED`, `ingenieur` « Ingénieur » `GOLD`, `policier` « Policier » `BLUE`, `agriculteur` « Agriculteur » `GREEN`, `citoyen` « Citoyen » `GRAY`. Identifiant : `[a-z0-9_]{1,32}`.

- [ ] **Step 1 : écrire les tests qui échouent**

```java
@Test void unJoueurInconnuEstCitoyen() { assertEquals("citoyen", RoleBook.withBuiltins().roleOf(UUID.randomUUID()).id()); }
@Test void lesCinqRolesExistent() { assertEquals(Set.of("medecin","ingenieur","policier","agriculteur","citoyen"), ids(RoleBook.withBuiltins())); }
@Test void unRoleCodeEnDurNeSeSupprimePas() { assertThrows(IllegalStateException.class, () -> book.deleteRole("ingenieur")); }
@Test void supprimerUnRoleRendCitoyenSesPorteurs() { book.createRole("pecheur","Pêcheur",BLUE); book.assign(joueur,"pecheur"); book.deleteRole("pecheur"); assertEquals("citoyen", book.roleOf(joueur).id()); }
@Test void attribuerUnRoleInconnuEstRefuse() { assertThrows(IllegalArgumentException.class, () -> book.assign(joueur,"inconnu")); }
@Test void unIdentifiantInvalideOuDoubleEstRefuse() { /* "Pêcheur", "", "citoyen" : IllegalArgumentException */ }
@Test void unRoleEtSonLibelleAccentueSurviventALaSauvegarde() { /* créer « Pêcheur », assigner, save puis load : libellé, couleur et porteur intacts */ }
@Test void lesRolesCodesEnDurReapparaissentSiAbsentsDeLaSauvegarde() { assertTrue(RoleBook.load(new CompoundTag()).role("ingenieur").isPresent()); }
```

- [ ] **Step 2 : vérifier l'échec**

Run: `./gradlew test --tests "*RoleBookTest"`. Expected : FAIL, classes absentes.

- [ ] **Step 3 : implémenter `Role` et `RoleBook`**

Maps ordonnées (`LinkedHashMap`). Couleur sauvegardée par `ChatFormatting.getName()`.

- [ ] **Step 4 : vérifier le succès**

Run: `./gradlew test --tests "*RoleBookTest"`. Expected : PASS, 8 tests.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/role src/test/java/dev/oneforallsurvival/role
git commit -m "feat(roles): ajoute la logique des roles"
git push
```

---

### Task 3 : persistance et affichage par équipes

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/role/RoleSavedData.java`, `RoleTeams.java`, `RoleEvents.java`
- Modify: `src/main/java/dev/oneforallsurvival/OneForAllSurvival.java`
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/RoleGameTests.java`

**Interfaces:**
- Consumes: `RoleBook`, `Role`
- Produces:
  - `static RoleSavedData RoleSavedData.get(MinecraftServer)` (monde principal, clé `oneforallsurvival_roles`), `RoleBook book()`, `setDirty()` après chaque modification
  - `static String RoleTeams.teamName(String roleId)` → `"ofas_" + roleId`
  - `static void RoleTeams.syncTeams(Scoreboard, RoleBook)` : crée ou met à jour une équipe par rôle, retire les équipes `ofas_` sans rôle, ne touche à aucune autre
  - `static void RoleTeams.applyPlayer(Scoreboard, String playerName, Role)`
  - `static void RoleEvents.refreshAll(MinecraftServer)` : `syncTeams` puis réapplique chaque joueur connecté

Préfixe : `Component.literal("[" + label + "] ").withStyle(color)`. La couleur du pseudo reste celle par défaut. `RoleEvents` écoute `ServerStartedEvent` (appelle `syncTeams`) et `PlayerEvent.PlayerLoggedInEvent` (appelle `applyPlayer`) sur `MinecraftForge.EVENT_BUS`.

- [ ] **Step 1 : écrire les GameTests qui échouent**

`@GameTestHolder("oneforallsurvival")`, `@PrefixGameTestTemplate(false)`, `@GameTest(template = "test/plancher")` :

```java
syncCreeUneEquipeParRole: après syncTeams(scoreboard, withBuiltins()), getPlayerTeam("ofas_ingenieur").getPlayerPrefix().getString() vaut "[Ingénieur] "
syncRetireLesEquipesOrphelines: une équipe "ofas_fantome" disparaît après syncTeams
syncNeTouchePasAuxAutresEquipes: une équipe "staff" reste, avec ses membres
applyPlayerRemplaceLEquipePrecedente: applyPlayer("Dev","ingenieur") puis applyPlayer("Dev","citoyen") : getPlayersTeam("Dev") est ofas_citoyen
```

Chaque test retire ses équipes à la fin : le scoreboard appartient au serveur entier.

- [ ] **Step 2 : vérifier l'échec**

Run: `./gradlew runGameTestServer`. Expected : compilation en échec, classes absentes.

- [ ] **Step 3 : implémenter les trois classes et l'enregistrement dans le constructeur `@Mod`**

- [ ] **Step 4 : vérifier le succès**

Run: `./gradlew runGameTestServer`. Expected : tous les tests requis passent.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival src/devcontent/java/dev/oneforallsurvival/dev/RoleGameTests.java
git commit -m "feat(roles): affiche les prefixes par des equipes de scoreboard"
git push
```

---

### Task 4 : commande `/role` et réserve à l'Ingénieur

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/role/RoleCommands.java`, `RoleGate.java`
- Create: `src/main/resources/assets/oneforallsurvival/lang/fr_fr.json`, `en_us.json`
- Test: modifier `RoleGameTests.java`

**Interfaces:**
- Consumes: `RoleSavedData`, `RoleEvents.refreshAll`
- Produces:
  - `static boolean RoleGate.isEngineer(RoleBook, UUID)`
  - `static boolean RoleGate.canDoEngineerWork(ServerPlayer)` : rôle Ingénieur ou `hasPermissions(2)`
  - `static boolean RoleGate.requireEngineer(ServerPlayer)` : comme ci-dessus, et envoie `message.oneforallsurvival.engineer_only` en cas de refus

Commandes, toutes niveau de permission 2, à l'exception de `get` sans cible et de `list` (niveau 0) : `/role list`, `/role get [joueur]`, `/role set <joueur> <role>` (`GameProfileArgument`, pour accepter un joueur hors ligne), `/role create <id> <couleur> <libellé>`, `/role edit <id> <couleur> <libellé>`, `/role delete <id>`. `<libellé>` en `StringArgumentType.greedyString()`, `<role>` avec suggestions, `<couleur>` par `ColorArgument`. Chaque modification appelle `setDirty()` puis `refreshAll`. Une erreur de `RoleBook` devient un message d'échec de commande, jamais une exception.

- [ ] **Step 1 : écrire les GameTests qui échouent**

Commandes exécutées par `server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), …)` :

```java
createPuisDeleteMetAJourLeLivreEtLesEquipes: "role create pecheur blue Pêcheur" crée le rôle et l'équipe ; "role delete pecheur" les retire
deleteDUnRoleCodeEnDurEchoueSansLever: "role delete ingenieur" laisse le rôle présent
laCibleHorsLigneRedevientCitoyen: assigner un UUID absent à un rôle dynamique, le supprimer : roleOf(UUID) vaut citoyen
isEngineerNeRepondOuiQuePourIngenieur: vrai pour ingenieur, faux pour citoyen et medecin
```

- [ ] **Step 2 : vérifier l'échec**

Run: `./gradlew runGameTestServer`. Expected : compilation en échec.

- [ ] **Step 3 : implémenter les commandes, la réserve et les fichiers de langue**

- [ ] **Step 4 : vérifier le succès**

Run: `./gradlew runGameTestServer`. Expected : tous les tests requis passent.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/role src/main/resources/assets/oneforallsurvival/lang src/devcontent/java/dev/oneforallsurvival/dev/RoleGameTests.java
git commit -m "feat(roles): ajoute la commande role et la reserve a l'Ingenieur"
git push
```

---

### Task 5 : machine électrique de base

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/machine/MachineState.java`, `MachineEnergyStorage.java`, `ElectricMachineBlock.java`, `ElectricMachineBlockEntity.java`
- Create: `src/devcontent/java/dev/oneforallsurvival/dev/DevBlocks.java`, `TestMachineBlockEntity.java`
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/MachineGameTests.java`

**Interfaces:**
- Produces:
  - `enum MachineState implements StringRepresentable { STOPPED, RUNNING, NO_POWER, FAULT }`
  - `ElectricMachineBlock` : propriété `EnumProperty<MachineState> STATE`, ticker côté serveur uniquement
  - `abstract class ElectricMachineBlockEntity extends BlockEntity` :
    - constructeur `(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int maxReceive)`
    - à définir : `protected abstract boolean wantsToWork()`, `protected abstract int energyPerTick()`, `protected abstract void workTick()`
    - `MachineState state()`, `protected void setFault(boolean fault)`, `IEnergyStorage energy()`
  - `MachineEnergyStorage extends EnergyStorage` : `canExtract()` faux vers l'extérieur, `int consume(int amount)` en interne

Règle du tick : en `FAULT`, rien. Sinon, si `!wantsToWork()` → `STOPPED`. Sinon, si la réserve couvre `energyPerTick()` → consommer, `workTick()`, `RUNNING`. Sinon → `NO_POWER`, sans toucher à la progression. Le BlockState n'est écrit que si l'état change. `LazyOptional` invalidé dans `invalidateCaps`. Sauvegarde : énergie et état de panne.

`TestMachineBlockEntity` : 10 FE par tick, compteur `progress`, `wantsToWork()` vrai. `DevBlocks` enregistre `test_machine` par `RegisterEvent` dans `devcontent`, avec un clic droit qui affiche état, FE et progression dans le chat, pour la Task 7.

- [ ] **Step 1 : écrire les GameTests qui échouent**

```java
sansCourantLaMachineEstEnPauseSansPerdreSaProgression: 100 FE reçus, RUNNING et progress == 10 après 10 ticks ; puis NO_POWER et progress inchangé 20 ticks plus tard
leCourantLaFaitRepartir: après NO_POWER, recevoir 100 FE : RUNNING et progress qui augmente
laProgressionEtLaReserveSurviventAuRechargement: saveWithoutMetadata puis load sur une nouvelle instance : énergie identique ; progress identique pour la machine d'essai
laCapabilityRefuseDExtraire: extractEnergy(10,false) vaut 0, receiveEnergy au-delà de la capacité ne dépasse pas la capacité
enPanneLaMachineNeConsommeRien: setFault(true) : FAULT et énergie constante
```

Vérifier avec `helper.runAfterDelay` à des ticks distincts : l'ordre de plusieurs rappels d'un même tick est arbitraire.

- [ ] **Step 2 : vérifier l'échec**

Run: `./gradlew runGameTestServer`. Expected : compilation en échec.

- [ ] **Step 3 : implémenter le socle et le contenu d'essai**

- [ ] **Step 4 : vérifier le succès**

Run: `./gradlew runGameTestServer`. Expected : tous les tests requis passent.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/machine src/devcontent/java/dev/oneforallsurvival/dev
git commit -m "feat(machine): ajoute la machine electrique de base"
git push
```

---

### Task 6 : passage du courant en ligne

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/machine/InlineEnergyBlockEntity.java`
- Modify: `src/devcontent/java/dev/oneforallsurvival/dev/DevBlocks.java`
- Test: `src/devcontent/java/dev/oneforallsurvival/dev/InlineGameTests.java`

**Interfaces:**
- Consumes: `ForgeCapabilities.ENERGY`, `TestMachineBlockEntity`
- Produces:
  - `abstract class InlineEnergyBlockEntity extends BlockEntity`, avec `int maxPerTick` au constructeur, `long totalTransferred()`, `protected boolean isOpen()` (vrai par défaut)
  - Le bloc porte `HORIZONTAL_FACING` : la face de `FACING` est la sortie, l'opposée l'entrée

Aucune réserve : `receiveEnergy` sur la face d'entrée transmet aussitôt à la capability du voisin de la face de sortie, renvoie ce que le voisin a accepté, et ajoute cette quantité au total en mode réel. Plafond cumulé par tick de jeu. `getCapability` ne rend rien sur les autres faces. `totalTransferred` est sauvegardé. Le voisin n'est pas mis en cache en première version.

- [ ] **Step 1 : écrire les GameTests qui échouent**

```java
leCourantTraverseLeBloc: receiveEnergy(100,false) sur l'entrée : machine en sortie à 100 FE, totalTransferred == 100
sansVoisinLeCourantEstRefuse: receiveEnergy(100,false) vaut 0, totalTransferred == 0
faceAUneMachinePleineRienNeSePerd: le retour égale ce que la machine a accepté, et le total ne compte que cela
laSimulationNeTransmetRien: receiveEnergy(100,true) vaut 100, machine à 0 FE, total à 0
fermeRienNePasse: isOpen() faux : retour 0
leTotalSurvitAuRechargement: après save et load, totalTransferred identique
lePlafondParTickEstRespecte: deux appels de 80 avec maxPerTick 100 dans le même tick : somme acceptée de 100
```

- [ ] **Step 2 : vérifier l'échec**

Run: `./gradlew runGameTestServer`. Expected : compilation en échec.

- [ ] **Step 3 : implémenter le bloc et sa variante d'essai `test_inline`**

- [ ] **Step 4 : vérifier le succès**

Run: `./gradlew runGameTestServer`. Expected : tous les tests requis passent.

- [ ] **Step 5 : commit**

```bash
git add src/main/java/dev/oneforallsurvival/machine/InlineEnergyBlockEntity.java src/devcontent/java/dev/oneforallsurvival/dev
git commit -m "feat(machine): ajoute le passage du courant en ligne"
git push
```

---

### Task 7 : preuve en jeu (critère de sortie du lot)

Les GameTests ne chargent ni les réseaux ni de vrais clients : cette tâche est la preuve du lot. Elle se fait avec Killian ou par pilotage du client.

**Files:**
- Modify: `docs/verifications/lot-0.md`, `tasks/todo.md`
- Modify éventuel : `D:\BDC\Projets\OneForAllSurvival.md`, `D:\BDC\Index.md`

- [ ] **Step 1 : préparer le serveur dédié**

Dans `run-server/server.properties` : `online-mode=false`. Killian valide `run-server/eula.txt`.

Run: `./gradlew runServer`. Expected : `Done` dans le log, tous les mods chargés.

- [ ] **Step 2 : prouver les trois réseaux**

Dans un client connecté, pour **chaque** réseau (Crafts & Additions, Advanced Electricity, New Age) : produire du courant, relier par les connecteurs du mod à une `test_machine`, puis à une `test_inline` suivie d'une `test_machine`. Expected : l'état passe à `RUNNING` et les FE montent (clic droit), avec et sans bloc en ligne. Noter le résultat de chaque cas dans `docs/verifications/lot-0.md`.

**Si un réseau échoue : s'arrêter.** La spec laisse à Killian le choix entre un adaptateur et le retrait du mod.

- [ ] **Step 3 : prouver les rôles avec deux clients**

Lancer `runClient` puis `runClient2`, tous deux connectés au serveur. `/role set Dev ingenieur` puis `/role create pecheur blue Pêcheur` et `/role set Dev2 pecheur`. Expected, vu **depuis l'autre client** : préfixe et accents corrects dans le chat, au-dessus de la tête et dans le TAB. Redémarrer le serveur : `/role list` montre « Pêcheur » et les préfixes reviennent à la reconnexion. `/role delete pecheur` : Dev2 passe aussitôt en `[Citoyen]`. Sans droit d'opérateur, `/role set` est refusé.

- [ ] **Step 4 : vérifier le livrable**

Run: `./gradlew build`, puis lister `build/libs/*.jar`. Expected : aucune classe `dev/oneforallsurvival/dev/`, aucun `plancher.nbt`.

- [ ] **Step 5 : consigner et publier**

Reporter les résultats dans `docs/verifications/lot-0.md`, cocher le lot 0 dans `tasks/todo.md`, signaler dans `D:\BDC` les faits généraux découverts (une note par fait, plus l'index). Commit et push de chaque dépôt, en ajoutant les fichiers par nom.
