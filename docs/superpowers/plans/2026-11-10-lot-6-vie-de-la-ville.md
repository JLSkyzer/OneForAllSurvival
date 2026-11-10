# Lot 6 : vie de la ville — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter l'effet Bien-être, le chauffe-eau et la douche, la salle de projection, le phare et le projecteur, pour que le courant serve aussi au confort et aux repères de la ville.

**Architecture:** Chauffe-eau, projecteur de cinéma, phare et projecteur héritent de `ElectricMachineBlockEntity`, donc de l'usure, des pannes et du testeur. La douche est un simple bloc de plomberie sans courant. L'eau chaude est un fluide du mod, transporté par les tuyaux de Create comme l'eau potable du dessalinisateur. Les bobines sont des données (`data/<ns>/cinema_reels/*.json`) et leurs images des ressources du client. Les règles chiffrées (spectateurs, rayon du projecteur, tour libre) sont de la logique pure testée en JUnit. Aucun mixin.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, Farmer's Delight 1.20.1-1.3.4 (`implementation`), API JEI en `compileOnly`, Create en `-PwithPack` pour les tuyaux.

**Spec:** `docs/superpowers/specs/2026-11-06-features-lost-island-design.md`, section 4 (machine électrique, Bien-être), section 5 features 17, 25, 26, sections 7 et 9 (« le chauffe-eau exige de l'eau potable », « pas de jauge de moral, mais un effet Bien-être sans soin »). Aucun autre cahier ne touche ces features ; le Bien-être animal du cahier de l'agriculteur (feature 28) est un autre sujet.

## Global Constraints

- `gradle.properties` en ASCII pur. Aucun `${` dans `mods.toml` et `pack.mcmeta` hors propriété définie.
- Mod requis côté client et serveur. Aucune classe cliente atteignable depuis un chemin serveur : écrans et rendus dans un paquet `client`, enregistrés par un abonné `Dist.CLIENT`.
- Textes visibles dans `fr_fr.json`, avec repli `en_us.json`. Ne jamais écrire le nom « SolarTown » : le serveur s'appelle Lost Island RP.
- Rien de `devcontent` dans le mod livré. Registres : `DeferredRegister`, jamais un initialiseur statique.
- Le Bien-être **ne rend aucun point de vie**, directement ou non (ni régénération, ni absorption, ni soin). Les effets de Farmer's Delight ne sont pas réutilisés.
- Recettes, durées et listes portées par des données (JSON, tags, config `SERVER`), avec les valeurs de ce plan comme valeurs de départ.
- Aucun contenu téléchargé depuis Internet, ni par le mod ni pour le construire : images des bobines et textures produites par script Python/PIL dans le dépôt.
- Une zone déchargée n'est pas une coupure : la machine se fige, sans usure ni perte. Jamais de chargement forcé de chunk. Une coupure met en pause sans perte (séance comprise).
- Tout bloc dont le modèle n'occupe pas le cube entier reçoit `noOcclusion()` ou une forme exacte, et sa capture le montre collé à un mur (`tasks/lessons.md`).
- Preuve des GameTests : lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie de Gradle. `./gradlew --offline test runGameTestServer` sans le pack, `-PwithPack` pour ce qui exige Create. Lots de 8 tests au plus. Une `ConcurrentModificationException` au démarrage est intermittente : relancer.
- Tout GameTest qui garde un vivant plusieurs secondes le met sous un plafond (`degager` de `ClotureGameTests`) et suit les individus vus plutôt que de recompter dans un rayon. Un joueur qui doit être trouvé dans le monde (douche, spectateurs) est un `JoueurDeTest`, pas `makeMockPlayer`, qui n'est pas dans le monde. Tout GameTest qui exige qu'une machine ait tourné porte `setupTicks = 20`. Gabarit : `test/plancher`.
- Aucun téléchargement hors dépendances déjà en cache. N'arrêter que des PID exacts lancés par soi. Jamais `Set-Content -Encoding utf8` sur une config Forge. `run/options.txt` ne se modifie jamais. Clients lancés par les tâches Gradle du projet seulement, jamais par Prism.
- Travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, par `git commit -- <chemins>` (jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/`), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Un client à la fois, refermé par le script lui-même quel que soit l'écran atteint. Filmer sans `ddagrab`.

## Review Focus

1. Projecteur : aucun bloc de lumière orphelin quand il est cassé, coupé, réorienté, en panne, déchargé ou à l'arrêt du serveur ; jamais un bloc écrasé, jamais une lumière d'autrui retirée. Testé en tâche 6.
2. Douche : deux joueurs, un joueur qui sort puis revient, l'eau chaude qui manque en pleine douche : l'effet seulement après 5 s continues d'eau chaude, pour chacun. Testé en tâche 2.
3. Séance : coupure en pleine séance (pause, reprise au même endroit), bobine retirée (séance finie sans effet), bobine inconnue (refusée), spectateur parti avant la fin (rien). Testé en tâche 3.
4. Bien-être : ne soigne jamais, ne se cumule pas au-delà de sa durée, ne dépasse pas son niveau. Testé en tâche 2.
5. Chauffe-eau : n'accepte que l'eau potable du mod (ni eau vanilla, ni lave), ne rend que l'eau chaude, ne perd rien sans courant. Testé en tâche 1.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Bonus du Bien-être | +5 % de vitesse de marche et +10 % de vitesse de minage, un seul niveau, en config | « Léger bonus d'activité » ; rien qui touche la vie |
| Durée du Bien-être | 6 minutes après une douche, 10 minutes après une séance ; une nouvelle source remet la durée au maximum des deux, sans cumul | La séance demande plus de temps au joueur |
| Eau chaude | Un fluide du mod, `hot_water`, transporté par les tuyaux de Create entre le chauffe-eau et la douche ; pas de seau, pas de bloc d'eau chaude | « Par tuyau Create » ; une douche peut être à l'étage |
| Chauffe-eau | 5 mB d'eau potable par tick deviennent 5 mB d'eau chaude, 40 FE par tick, deux cuves de 4 000 mB | À équilibrer ; débouché pour le dessalinisateur |
| Douche | Pomme de douche sans courant ni usure, cuve de 1 000 mB ; 2 mB par tick tant qu'un joueur est dessous ; effet après 5 s continues, de 1 à 3 blocs sous la pomme | « Quelques secondes sous l'eau chaude » |
| Bobines | Données `data/<ns>/cinema_reels/<id>.json` (titre, nombre d'images, secondes par image) et images `assets/<ns>/textures/cinema/<id>/<n>.png` ; une bobine de démonstration livrée, dessinée par script ; données par commande `/cinema bobine` (équipe du serveur) | Contenu du mod ou du pack, rien d'Internet ; l'équipe choisit ce qui passe |
| Écran | Blocs `cinema_screen` formant un rectangle vertical plein, 12 × 7 au plus, de 3 à 24 blocs devant le projecteur, sur son axe | Multi-bloc simple, réglable |
| Séance | Lancée depuis le menu du projecteur ; 30 FE par tick ; une image manquante s'affiche comme une carte « image absente » | Rien ne démarre seul dans une salle vide |
| Spectateurs | Joueurs à 24 blocs au plus du centre de l'écran, du côté du projecteur, présents à 80 % des relevés (un par seconde de marche) | « Présents » à la séance complète, sans exiger l'immobilité |
| Phare | Marche seulement s'il voit le ciel au-dessus et se trouve à 8 blocs au moins au-dessus du niveau de la mer ; 32 FE par tick ; deux faisceaux opposés, un tour en 8 s, 96 blocs de long | « Au sommet d'une tour libre » ; « visible de loin » |
| Projecteur | Orienté par le regard du joueur qui le pose ou qui le clique main vide ; portée 48 blocs ; un bloc `minecraft:light` de niveau 15 au point visé ; les mobs à 1,5 bloc de l'axe reçoivent Surbrillance 2 s ; 16 FE par tick | « S'oriente, éclaire le point visé, fait ressortir les mobs » |
| Consommation des lumières | Phare et projecteur consomment au moins le double de la plus forte lampe relevée dans les jars de Crafts & Additions, New Age et Advanced Electricity ; les valeurs de départ sont relevées si ce n'est pas le cas | « Consomment plus que les lampes des autres mods » |

## Fichiers

| Fichier | Rôle |
|---|---|
| `town/WellBeing.java`, `town/WellBeingEffect.java` | Effet Bien-être et son attribution sans cumul |
| `town/TownRegistry.java`, `town/TownConfig.java` | Registres et config `SERVER` `oneforallsurvival-town.toml` |
| `town/HotWaterType.java`, `town/WaterHeaterBlock.java`, `town/WaterHeaterBlockEntity.java`, `town/WaterHeaterMenu.java`, `town/client/WaterHeaterScreen.java` | Chauffe-eau |
| `town/ShowerHeadBlock.java`, `town/ShowerHeadBlockEntity.java`, `town/ShowerRules.java` | Douche |
| `town/CinemaReel.java`, `town/CinemaReels.java`, `town/FilmReelItem.java`, `town/CinemaCommands.java` | Bobines |
| `town/CinemaScreenBlock.java`, `town/CinemaScreens.java`, `town/CinemaRules.java` | Écran et règles de séance |
| `town/CinemaProjectorBlock.java`, `town/CinemaProjectorBlockEntity.java`, `town/CinemaProjectorMenu.java`, `town/client/{CinemaProjectorScreen,CinemaProjectorRenderer}.java` | Projecteur de cinéma |
| `light/LightRegistry.java`, `light/LightConfig.java`, `light/LightRules.java` | Registres, config `oneforallsurvival-light.toml`, règles pures |
| `light/LighthouseBlock.java`, `light/LighthouseBlockEntity.java`, `light/client/LighthouseRenderer.java` | Phare |
| `light/SearchlightBlock.java`, `light/SearchlightBlockEntity.java`, `light/client/SearchlightRenderer.java` | Projecteur |
| `src/devcontent/.../{ChauffeEau,Douche,Cinema,Phare,Projecteur}GameTests.java`, `E2eVille.java`, `FilmVille.java` | Preuves |
| `tools/gen_textures_lot6.py` | Textures et images de la bobine de démonstration |

---

### Task 1: Chauffe-eau et eau chaude

**Files:**
- Create: `town/TownRegistry.java`, `TownConfig.java`, `HotWaterType.java`, `WaterHeaterBlock.java`, `WaterHeaterBlockEntity.java`, `WaterHeaterMenu.java`, `client/WaterHeaterScreen.java`, `client/TownClient.java`, `src/devcontent/.../ChauffeEauGameTests.java`.
- Modify: `OneForAllSurvival.java` (`TownRegistry.register`, config `SERVER` `oneforallsurvival-town.toml`), l'onglet créatif (`bench/BenchRegistry.java`), langues.

**Interfaces:**
- Consumes: `ElectricMachineBlockEntity` (`wantsToWork`, `energyPerTick`, `workTick`, `afterTick`), `ElectricMachineBlock`, `FoodRegistry.POTABLE_WATER`, modèle de `DesalinatorBlockEntity` (deux cuves, un `IFluidHandler` combiné) et de `PotableWaterType`/`PotableWaterRendering`.
- Produces :
  - `TownRegistry.HOT_WATER_TYPE`, `HOT_WATER` (source), `HOT_WATER_FLOWING`, `WATER_HEATER`, `WATER_HEATER_ITEM`, `WATER_HEATER_BE`, `WATER_HEATER_MENU`, `tabItems() : List<RegistryObject<Item>>`, `register(IEventBus)`.
  - `TownConfig` : `heaterMbPerTick()` 5 (1 à 100), `heaterEnergy()` 40, et les valeurs des tâches 2 et 3.
  - `WaterHeaterBlockEntity` : `CAPACITY = 8000`, `MAX_RECEIVE = 200`, `TANK_CAPACITY = 4000`, `potable() : FluidTank`, `hot() : FluidTank`, `fluids() : IFluidHandler` (remplir n'accepte que `POTABLE_WATER`, vider ne rend que `HOT_WATER`, toutes faces).

Règles : `wantsToWork` : au moins 1 mB d'eau potable et 1 mB de place en eau chaude. Chaque `workTick` transfère `heaterMbPerTick`, ou moins pour le dernier pas : l'usure espace déjà les ticks de travail dans `ElectricMachineBlockEntity`, elle ne réduit pas le débit une seconde fois. Sans courant ni en panne : rien ne se perd. Sauvegarde des deux cuves. Écran : deux jauges, énergie, état. Eau chaude dessinée avec les textures de l'eau, teinte orangée.

- [ ] **Step 1: Écrire `ChauffeEauGameTests`** : `lEauPotableDevientChaude`, `lEauVanillaEstRefusee`, `laLaveEstRefusee`, `seuleLEauChaudeSort` (vider ne rend jamais d'eau potable), `sansCourantRienNeSePerd`, `enPanneRienNeSePerd`, `cuvePleineIlSArrete` (état `STOPPED`, aucune consommation), `lesCuvesSurviventAuRechargement`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(town): chauffe-eau`.

### Task 2: Effet Bien-être et douche

**Files:**
- Create: `town/WellBeingEffect.java`, `WellBeing.java`, `ShowerRules.java`, `ShowerHeadBlock.java`, `ShowerHeadBlockEntity.java`, `client/ShowerParticles.java` si besoin, `src/test/java/dev/oneforallsurvival/town/{WellBeingTest,ShowerRulesTest}.java`, `src/devcontent/.../DoucheGameTests.java`.
- Modify: `TownRegistry.java` (`WELL_BEING`, `SHOWER_HEAD`, son objet, `SHOWER_HEAD_BE`), `TownConfig.java`, langues (`effect.oneforallsurvival.well_being`).

**Interfaces:**
- Consumes: `TownRegistry.HOT_WATER` (tâche 1).
- Produces :
  - `WellBeingEffect` : `MobEffectCategory.BENEFICIAL`, couleur `0xF2B45A`, modificateur de vitesse de marche `MULTIPLY_TOTAL` lu dans la config ; vitesse de minage par `PlayerEvent.BreakSpeed` ; ne surcharge ni `applyEffectTick` ni `isDurationEffectTick` pour soigner.
  - `WellBeing.grant(LivingEntity entity, int ticks) : void` : niveau 0 toujours ; si l'effet est déjà là, durée = max(restante, `ticks`).
  - `WellBeing.newDuration(int remaining, int granted) : int` (pure).
  - `ShowerRules.isUnder(Vec3 head, AABB player) : boolean` : pieds de 1 à 3 blocs sous la pomme, centre à 0,6 bloc au plus de son axe vertical.
  - `TownConfig` : `wellBeingSpeedBonus()` 0.05, `wellBeingMiningBonus()` 0.10, `showerSeconds()` 5, `showerMbPerTick()` 2, `showerWellBeingMinutes()` 6.
  - `ShowerHeadBlockEntity` : `TANK_CAPACITY = 1000`, `tank() : FluidTank` (remplir n'accepte que `HOT_WATER`, aucune face ne vide), `secondsUnder(UUID) : int`.

Règles : toutes les 20 ticks côté serveur, la pomme relève les joueurs dessous (`isUnder`, rien de plein entre la pomme et le joueur, joueurs en spectateur exclus). S'il y en a un et que la cuve a 2 × 20 mB, elle consomme 40 mB et chaque joueur dessous gagne une seconde ; sinon les compteurs repartent à zéro. Un joueur qui n'est plus dessous repart de zéro. À `showerSeconds` : `WellBeing.grant(showerWellBeingMinutes)`, compteur à zéro. La propriété de bloc `active` (eau qui coule) suit la dernière consommation ; particules d'eau côté client. Compteurs non sauvegardés ; la cuve l'est. Pas d'usure, pas de courant.

- [ ] **Step 1: Écrire `WellBeingTest`** : `garderLaPlusLongue` (`newDuration(100, 200) == 200`, `newDuration(300, 200) == 300`), `zeroResteZero`.
- [ ] **Step 2: Écrire `ShowerRulesTest`** : `sousLaPommeAUnBloc`, `aTroisBlocs`, `pasAQuatre`, `pasAColeDeLAxe`, `pasAuDessus`.
- [ ] **Step 3: Écrire `DoucheGameTests`** : `cinqSecondesDonnentLeBienEtre`, `quatreSecondesNeSuffisentPas`, `sansEauChaudeRien`, `lEauManqueEnPleineDouche` (compteur à zéro), `sortirRemetAZero`, `deuxJoueursChacunLeSien`, `leBienEtreNeSoignePas` (vie, absorption et effets de soin inchangés après 30 s d'effet sur un joueur blessé), `pasDeCumulAuDela` (deux douches : durée ≤ `showerWellBeingMinutes`), `lEffetDonneSesBonus` (attribut de vitesse modifié de +5 %), `laPommeNAccepteQueLEauChaude`. Joueurs `JoueurDeTest` sous un plafond, comme `ClotureGameTests`.
- [ ] **Step 4: Lancer, constater l'échec.**
- [ ] **Step 5: Implémenter.**
- [ ] **Step 6: Lancer, lire** « All N required tests passed ».
- [ ] **Step 7: Commit** `feat(town): douche et effet bien-etre`.

### Task 3: Bobines et séance de cinéma

**Files:**
- Create: `town/CinemaReel.java`, `CinemaReels.java`, `FilmReelItem.java`, `CinemaCommands.java`, `CinemaScreenBlock.java`, `CinemaScreens.java`, `CinemaRules.java`, `CinemaProjectorBlock.java`, `CinemaProjectorBlockEntity.java`, `CinemaProjectorMenu.java`, `client/CinemaProjectorScreen.java`, `data/oneforallsurvival/cinema_reels/arrivee.json`, `src/test/java/dev/oneforallsurvival/town/{CinemaRulesTest,CinemaReelTest}.java`, `src/devcontent/.../CinemaGameTests.java`.
- Modify: `TownRegistry.java`, `TownConfig.java`, `OneForAllSurvival.java` (rechargement des bobines, commandes), langues.

**Interfaces:**
- Produces :
  - `CinemaReel(ResourceLocation id, String title, int frames, int secondsPerFrame)` ; `CinemaReel.parse(ResourceLocation, JsonObject) : CinemaReel` (rejette `frames < 1`, `secondsPerFrame < 1`, titre vide) ; `durationTicks() : int` ; `frameAt(int ticks) : int`.
  - `CinemaReels` : `SimpleJsonResourceReloadListener` sur `cinema_reels`, `get(ResourceLocation) : @Nullable CinemaReel`, `ids()`. Un fichier invalide est journalisé et ignoré.
  - `FilmReelItem` : NBT `reel` ; `reelId(ItemStack) : @Nullable ResourceLocation` ; `withReel(ResourceLocation) : ItemStack` ; infobulle : titre et durée, ou « bobine illisible ».
  - `/cinema bobine <joueurs> <id>` (niveau 2, suggestions depuis `ids()`), `/cinema bobines` (liste).
  - `CinemaScreens.find(Level level, BlockPos projector, Direction facing, int minDistance, int maxDistance, int maxWidth, int maxHeight) : @Nullable ScreenRect` : premier bloc non vide de l'axe, de `minDistance` à `maxDistance` ; ce doit être un `cinema_screen` ; rectangle plein des écrans reliés dans le plan perpendiculaire, `null` s'il est troué ou trop grand. `ScreenRect(BlockPos min, BlockPos max, Direction facing)` : `center()`, `width()`, `height()`.
  - `CinemaRules.completed(int samplesPresent, int samplesTotal, double ratio) : boolean` (≥ ratio et `samplesTotal > 0`) ; `CinemaRules.isSpectator(Vec3 player, ScreenRect screen, BlockPos projector, double radius) : boolean` (à `radius` du centre et du côté du projecteur).
  - `TownConfig` : `cinemaEnergy()` 30, `cinemaRadius()` 24, `cinemaRatio()` 0.8, `cinemaWellBeingMinutes()` 10, `screenMinDistance()` 3, `screenMaxDistance()` 24, `screenMaxWidth()` 12, `screenMaxHeight()` 7.
  - `CinemaProjectorBlockEntity` : `CAPACITY = 6000`, `MAX_RECEIVE = 150`, une case de bobine, `screen() : @Nullable ScreenRect`, `reel()`, `ticks()`, `frame()`, `running() : boolean`, `start()`, `stop()` ; données de menu `DATA_*` ; bouton de menu 0 = lancer ou arrêter (`clickMenuButton`, sans paquet du mod).

Règles : `wantsToWork` : séance lancée, bobine connue, écran trouvé (relu toutes les 40 ticks). Chaque `workTick` avance la séance d'un tick ; toutes les 20 ticks de marche, relevé des spectateurs (`isSpectator`, compteur par joueur, total). Fin : `WellBeing.grant(cinemaWellBeingMinutes)` pour ceux qui passent `completed`, puis séance remise à zéro, bobine gardée. Bobine retirée ou écran perdu : séance finie sans effet. Sans courant ni en panne : pause au même endroit. L'image courante change le bloc côté client par `sendBlockUpdated` (une fois par image, pas par tick). Sauvegarde : bobine, ticks, séance lancée, relevés.

- [ ] **Step 1: Écrire `CinemaReelTest`** : `lisUneBobine`, `refuseZeroImage`, `refuseUnTitreVide`, `dureeEnTicks` (`new CinemaReel(id, "t", 6, 5).durationTicks() == 600`), `imageAuTemps` (`frameAt(99) == 0`, `frameAt(100) == 1`, `frameAt(599) == 5`, `frameAt(600) == 5`).
- [ ] **Step 2: Écrire `CinemaRulesTest`** : `quatreVingtPourCentSuffisent` (`completed(8, 10, 0.8)`), `septSurDixNon`, `aucunReleveNon`, `derriereLEcranNon`, `tropLoinNon`.
- [ ] **Step 3: Écrire `CinemaGameTests`** : `unEcranPleinEstTrouve`, `unEcranTroueNon`, `unObstacleDevantNon`, `uneSeanceCompleteDonneLeBienEtre`, `unSpectateurPartiNeRecoitRien`, `uneCoupureMetEnPauseSansPerte`, `bobineRetireeFinSansEffet`, `bobineInconnueRefusee`, `laSeanceSurvitAuRechargement`. Séances courtes par une bobine de test, `src/devcontent/resources/data/oneforallsurvival/cinema_reels/essai.json` (2 images d'1 s) ; spectateurs `JoueurDeTest`.
- [ ] **Step 4: Lancer, constater l'échec.**
- [ ] **Step 5: Implémenter.**
- [ ] **Step 6: Lancer, lire** « All N required tests passed ».
- [ ] **Step 7: Commit** `feat(town): seance de cinema`.

### Task 4: Image à l'écran

**Files:**
- Create: `town/client/CinemaProjectorRenderer.java`, `src/devcontent/.../CinemaRendu.java` (captures), `tools/gen_textures_lot6.py` (pour l'instant les deux images unies de la bobine `essai`, dans `src/devcontent/resources/assets/oneforallsurvival/textures/cinema/essai/`), `src/devcontent/resources/data/oneforallsurvival/cinema_reels/essai_sans_images.json`.
- Modify: `town/client/TownClient.java` (rendu de block entity), langues.

**Interfaces:**
- Consumes: `CinemaProjectorBlockEntity.screen()`, `reel()`, `frame()`, `running()` (tâche 3), synchronisés par `getUpdateTag`/`getUpdatePacket`.
- Produces : `CinemaProjectorRenderer.texture(ResourceLocation reel, int frame) : ResourceLocation` = `<ns>:textures/cinema/<path>/<frame>.png`.

Règles : le rendu du projecteur dessine l'image courante sur toute la face de l'écran tournée vers lui, décalée de 0,01 bloc, pleine luminosité, proportions de l'image gardées (bandes noires). Image absente du gestionnaire de ressources : une carte noire « image absente » au lieu du damier. Séance en pause ou sans courant : écran éteint. `getRenderBoundingBox` couvre le projecteur et l'écran ; `shouldRenderOffScreen` vrai.

- [ ] **Step 1: Écrire `CinemaRendu`** : pose une salle, lance la bobine `essai`, capture l'écran à ses deux images, puis la bobine `essai_sans_images`. Lancement par `runDemoVisuels` ou une tâche à part, client refermé par le script.
- [ ] **Step 2: Lancer, constater** l'écran vide.
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Relancer, ouvrir les captures**, vérifier image, changement d'image, carte « image absente ».
- [ ] **Step 5: Lancer la suite, lire** « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(town): image du cinema a l'ecran`.

### Task 5: Phare

**Files:**
- Create: `light/LightRegistry.java`, `LightConfig.java`, `LightRules.java`, `LighthouseBlock.java`, `LighthouseBlockEntity.java`, `client/LighthouseRenderer.java`, `client/LightClient.java`, `src/test/java/dev/oneforallsurvival/light/LightRulesTest.java`, `src/devcontent/.../PhareGameTests.java`.
- Modify: `OneForAllSurvival.java` (`LightRegistry.register`, config `SERVER` `oneforallsurvival-light.toml`), l'onglet créatif, langues.

**Interfaces:**
- Produces :
  - `LightRules.isFreeTower(boolean seesSky, int y, int seaLevel, int minAboveSea) : boolean`.
  - `LightConfig` : `lighthouseEnergy()` 32, `lighthouseMinAboveSea()` 8, `searchlightEnergy()` 16, `searchlightRange()` 48, `searchlightGlowRadius()` 1.5.
  - `LightRegistry.LIGHTHOUSE`, `SEARCHLIGHT`, objets, `LIGHTHOUSE_BE`, `SEARCHLIGHT_BE`, `tabItems()`.
  - `LighthouseBlock` : propriété `lit`, lumière 15 quand `lit`.

Règles : `wantsToWork` : `isFreeTower(level.canSeeSky(pos.above()), …)`. `lit` suit l'état `RUNNING`. Posé par un joueur hors d'une tour libre : message « Le phare doit voir le ciel, à 8 blocs au moins au-dessus de la mer ». Rendu : deux faisceaux opposés, un tour en 8 s calé sur le temps de jeu (tous les clients voient le même angle), 96 blocs, translucides, seulement si `lit` ; boîte de rendu infinie, distance de vue maximale.

Avant d'écrire les valeurs : relever dans les jars en cache la consommation des lampes de Crafts & Additions (`small_light_connector`), New Age (`lamp_post`, `street_light`) et Advanced Electricity (`alarm_lamp` et autres lampes) ; si `lighthouseEnergy` ou `searchlightEnergy` n'atteint pas le double de la plus forte, les relever et le dire dans le rapport.

- [ ] **Step 1: Écrire `LightRulesTest`** : `tourLibre`, `sousUnToitNon`, `tropBasNon`.
- [ ] **Step 2: Écrire `PhareGameTests`** : `libreIlSAllume`, `sousUnToitIlNeConsommeRien`, `sansCourantIlSEteint`, `enPanneIlSEteint`, `etatSurvitAuRechargement`.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer, lire** « All N required tests passed » ; capture de nuit du faisceau dans `DemoVisuels`, regardée.
- [ ] **Step 6: Commit** `feat(light): phare`.

### Task 6: Projecteur

**Files:**
- Create: `light/SearchlightBlock.java`, `SearchlightBlockEntity.java`, `client/SearchlightRenderer.java`, `src/devcontent/.../ProjecteurGameTests.java`.
- Modify: `LightRegistry.java`, `LightRules.java`, `LightConfig.java`, langues.

**Interfaces:**
- Produces :
  - `LightRules.lightSpot(Vec3 from, Vec3 hit) : Vec3` (le point 0,5 bloc avant l'impact, vers la source) ; `LightRules.nearAxis(Vec3 from, Vec3 to, Vec3 point, double radius) : boolean` (distance au segment).
  - `SearchlightBlockEntity` : `yaw()`, `pitch()`, `aim(float yaw, float pitch)`, `lightPos() : @Nullable BlockPos`, `beamLength() : float`.

Règles : posé, il prend le regard du joueur ; un clic main vide le réoriente (ouvert à tous). Toutes les 10 ticks en marche : rayon `ClipContext.Block.COLLIDER` sur `searchlightRange`, sans traverser de chunk non chargé. Au point `lightSpot`, si le bloc est de l'air (ou notre lumière précédente) : y poser `minecraft:light` niveau 15 ; l'ancienne lumière n'est retirée que si elle est encore un bloc `minecraft:light` à la position suivie. Aucune lumière si le rayon ne touche rien ou si le point n'est pas de l'air. `LivingEntity` non joueurs à `searchlightGlowRadius` de l'axe : Surbrillance 40 ticks. Arrêt, coupure, panne, réorientation, casse, déchargement du chunk, `setRemoved` : lumière retirée. Position suivie sauvegardée, pour la retirer après une relance. Rendu : la tête tournée selon `yaw`/`pitch`, un faisceau translucide jusqu'à `beamLength`, synchronisés par `getUpdateTag`.

- [ ] **Step 1: Compléter `LightRulesTest`** : `lumiereAvantLImpact`, `surLAxe`, `horsDeLAxe`, `auDelaDuSegmentNon`.
- [ ] **Step 2: Écrire `ProjecteurGameTests`** : `ilEclaireLePointVise`, `reorienteLAncienneLumiereDisparait`, `casseRienNeReste`, `coupureRienNeReste`, `enPanneRienNeReste`, `jamaisUnBlocEcrase` (vitre au point visé : intacte, pas de lumière), `uneLumiereDAutruiReste` (lumière posée à la main hors du point suivi : intacte), `unZombieDansLAxeBrille`, `horsDeLAxeNon`, `rechargeLaLumiereSuivieEstRetiree` (relue après `saveWithoutMetadata`/`load`, puis arrêt). Lots de 8 au plus.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer, lire** « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(light): projecteur`.

### Task 7: Recettes du matériel et visuels finaux

**Files:**
- Create: recettes de table `water_heater`, `shower_head`, `cinema_projector`, `cinema_screen` (une recette donne 4 blocs), `lighthouse`, `searchlight` (conditions `forge:mod_loaded`, comme `dryer.json`) ; tables de butin ; états de blocs, modèles, textures ; icône d'effet `textures/mob_effect/well_being.png` ; images de la bobine `arrivee` ; `tools/gen_textures_lot6.py` complété.
- Modify: `DemoVisuels`, `PackGameTests`, langues, tag `mineable/pickaxe`.

Les six objets se fabriquent à l'établi d'ingénieur : `PackGameTests` vérifie qu'aucune recette de table ne les donne une fois le pack chargé et que les six blocs tombent à la pioche. Ingrédients : plaques, tubes et réservoir de fluide de Create, pièces du lot 2 (bobine, fusible, tag `oneforallsurvival:wire`), verre pour le phare et le projecteur. La bobine `film_reel` ne se fabrique pas : elle vient de la commande.

Visuels, style bricolage et récupération, palette cohérente avec les lots 2 à 5, en 16 x 16, script Python/PIL reproductible, aucun fichier téléchargé : quatre états de lampe pour chaque machine ; chauffe-eau en ballon de tôle cerclé, manomètre ; pomme de douche en arrosoir de récupération (modèle partiel, `noOcclusion`) ; projecteur de cinéma à deux bobines ; écran en toile tendue sur cadre ; phare en lanterne à lentille sous dôme ; projecteur en tambour sur fourche. La bobine `arrivee` : six images (crash de l'avion, plage, premiers abris, ruines, premier courant, la ville au soir), 64 x 36 au moins, dessinées par le script, dans l'esprit du lore. Captures : chaque bloc seul, collé à un mur, de jour et de nuit pour les lumières, la salle de projection en marche.

- [ ] **Step 1: Écrire les tests de `PackGameTests`.**
- [ ] **Step 2: Lancer avec `-PwithPack`, constater l'échec.**
- [ ] **Step 3: Produire recettes, butins, modèles, textures, images.**
- [ ] **Step 4: Lancer `runDemoVisuels`**, ouvrir les captures, vérifier objets, blocs, écrans et faisceaux lisibles.
- [ ] **Step 5: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 6: Commit** `feat(lot6): recettes du materiel et visuels finaux`.

### Task 8: Preuve de bout en bout, film, vérification

**Files:**
- Create: `dev/E2eVille.java`, `dev/FilmVille.java`, `docs/verifications/lot-6.md`.
- Modify: `dev/E2eClient.java`, `build.gradle` (`runDemoVilleFilm`), `wiki/data/electricite.json` (entrées `bien-etre`, `chauffe-eau-douche`, `salle-projection`, `phare-projecteurs` au statut et au format de l'entrée `cloture-electrique`), `tasks/todo.md`.

**E2E sur serveur dédié, avec le pack, en un seul passage puis relance :** dessalinisateur relié au chauffe-eau, puis le chauffe-eau à la douche, par de vrais tuyaux et une pompe de Create ; un joueur non opérateur sous la douche reçoit le Bien-être après 5 s, sans gain de vie ; sans eau chaude, rien ; séance courte avec deux relevés au moins : le spectateur présent reçoit l'effet, un joueur parti tôt non ; coupure en pleine séance, `/stop`, relance : la séance reprend au même endroit ; phare allumé sur une tour, éteint sous un toit ; projecteur : lumière posée, retirée après casse, zombie en Surbrillance. Le script ferme son client quel que soit l'écran atteint et se termine par `/stop`.

**Film**, monde intégré, durées raccourcies en mémoire et annoncées à l'écran : une douche, une séance avec la bobine de démonstration, le phare de nuit, le projecteur qui suit un zombie. Filmer avec `FrameRecorder`/`FilmCapture` ou `gfxcapture`, jamais `ddagrab`.

- [ ] **Step 1: Écrire E2E et film, lancer.** Lire `[e2e] bilan : TOUT PASSE` dans le journal, passage complet et relance.
- [ ] **Step 2: Rouvrir le film en images**, vérifier chaque scène.
- [ ] **Step 3: Écrire `docs/verifications/lot-6.md`** : prouvé et non prouvé, séparés, comptes et heures des derniers passages. Au moins : équilibrage réel, multijoueur, FE d'un autre mod, bobines d'un vrai pack de ressources, faisceau vu depuis une autre zone chargée, son absent par choix, coût serveur non mesuré.
- [ ] **Step 4: Mettre le wiki à jour et cocher le lot dans `tasks/todo.md`**, avec ce qui reste ouvert, sans écrire « fusionné ».
- [ ] **Step 5: Commit et push** `test(lot6): E2E et film`, `docs(lot6): preuve du lot 6`.
