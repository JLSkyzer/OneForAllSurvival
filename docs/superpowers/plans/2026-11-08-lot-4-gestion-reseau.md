# Lot 4 : gestion du réseau — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Donner à l'ingénieur la gestion du réseau : arbitrer la pénurie au tableau de délestage, compter et facturer chaque abonné, couper et sceller une ligne, protéger les machines de la foudre et voir venir l'orage.

**Architecture:** Le compteur et le disjoncteur héritent du bloc en ligne du lot 0 (`InlineEnergyBlockEntity`), sans réserve ni usure. Le tableau de délestage, le parafoudre et la station météo héritent de `ElectricMachineBlockEntity`, donc de l'usure, de la réparation, du testeur et de la casse sans perte d'état du lot 2. Les règles chiffrées sont de la logique pure testée en JUnit : répartition par priorité (`PriorityRelay`), prévision (`WeatherForecast`), facture (`MeterLedger`). Le registre des abonnés est un `SavedData`. Aucun mixin.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, capability `ForgeCapabilities.ENERGY`, `EntityJoinLevelEvent` pour la foudre, `ServerLevelData` pour la météo.

**Spec:** `docs/superpowers/specs/2026-11-06-features-lost-island-design.md`, section 4 (« Machine électrique » : compteur et disjoncteur sans usure, parafoudre à usure propre ; « Système de rôles »), section 5 features 5, 6, 7, 8, section 7 (GameTests de la priorité du délestage et de la protection du parafoudre). Choix de Killian du 2026-11-08 : le délesteur s'use et coupe tout en panne ; le relevé porte le jour de jeu et la date réelle ; la station annonce environ 5 minutes à l'avance ; seul l'ingénieur inscrit le nom de l'abonné.

## Global Constraints

- `gradle.properties` en ASCII pur. Aucun `${` dans `mods.toml` et `pack.mcmeta` hors propriété définie.
- Mod requis côté client et serveur. Aucune classe cliente atteignable depuis un chemin serveur : écrans dans un paquet `client`, enregistrés par un abonné `Dist.CLIENT`.
- Textes visibles dans `fr_fr.json`, avec repli `en_us.json`. Ne jamais écrire le nom « SolarTown » : le serveur s'appelle Lost Island RP.
- Rien de `devcontent` dans le mod livré. Registres : `DeferredRegister` ou `RegisterEvent`, jamais un initialiseur statique.
- Preuve des GameTests : lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie de Gradle. `./gradlew runGameTestServer` sans le pack, `-PwithPack` pour ce qui exige Create. Une `ConcurrentModificationException` au démarrage est intermittente : relancer.
- Tout GameTest qui exige qu'une machine ait tourné porte `setupTicks = 20`.
- Dans un GameTest, lever `GameTestAssertException`, jamais `AssertionError`, y compris dans les aides.
- Aucun téléchargement hors dépendances déjà en cache. Ne jamais tuer un processus Java autre que ceux qu'on a lancés, par PID exact. Ne jamais écrire les configs Forge avec `Set-Content -Encoding utf8`.
- Commit et push sur la branche `lot-4` après chaque tâche vérifiée, fichiers ajoutés par nom (jamais `art/` ni `docs/assets/`), pas de `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Pas de fusion dans `main`.
- Une zone déchargée n'est pas une coupure. Le contenu ou les données d'un bloc ne se perdent ni en panne ni sans courant.
- Actions réservées à l'Ingénieur (ou opérateur, `RoleGate.canDoEngineerWork`) : inscrire un abonné, relever un compteur, poser ou retirer un scellé, réparer. Les autres reçoivent un message clair. Poser, casser et basculer un disjoncteur non scellé : ouvert à tous.
- Les fenêtres de jeu se voient sur l'écran de Killian : aucune boucle de lancements du client sans le lui annoncer, et tout script ferme lui-même ses clients, quel que soit l'écran où ils s'arrêtent.

## Review Focus

1. Un générateur qui pousse plusieurs fois par tick dans le délesteur : la priorité reste respectée sur l'ensemble du tick, le plafond aussi, et une simulation ne change ni les compteurs ni l'affichage. Testé en tâche 1 (`plusieursPoussesRespectentLaPriorite`, `uneSimulationNeChangeRien`).
2. Deux blocs en ligne face à face, ou un délesteur dont une sortie revient sur son entrée : aucune récursion ni énergie créée. Testé en tâche 1 (`uneBoucleNeCreeRien`).
3. Casser puis reposer un compteur : l'index suit l'objet, et le relevé suivant signale la dépose ; un compteur remplacé par un neuf est signalé aussi. Testé en tâche 2.
4. Un éclair dans une zone en partie déchargée : aucun chunk chargé par la recherche, aucun plantage. Testé en tâche 4 (`laFoudreNeChargeAucunChunk`).
5. Météo réglée par `/weather clear 600` ou `/weather thunder` : la tendance suit tout de suite, sans tendance fausse pendant la durée forcée. Testé en JUnit et en tâche 5.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Faces du délesteur | Entrée à l'arrière ; sorties devant, à gauche, à droite et dessus ; rien dessous | Quatre sorties lisibles, posables contre un mur |
| Réglage des priorités | Ouvert à tous, par l'écran du tableau : chaque sortie reçoit un rang de 1 à 4, échangé avec la sortie qui l'avait | Le cahier des charges ne le réserve pas à l'ingénieur |
| Affichage des sorties | Alimentée, partielle, coupée, rien de branché, plus les FE du dernier tick | « L'écran indique quelles sorties sont coupées » |
| Délesteur usé | Sous 50 %, son débit baisse au même rendement ; en panne, tout est coupé | Choix de Killian ; même règle que les autres machines |
| Abonné | Seul l'ingénieur inscrit le nom, par un petit écran (clic droit main vide). Les autres voient « Compteur de X » | Choix de Killian |
| Relevé | Objet papier, rendu à l'ingénieur qui utilise le testeur sur un compteur : abonné, index, consommation depuis le relevé précédent du même abonné, « Jour N » et date réelle du serveur, anomalies | Choix de Killian pour la date ; les anomalies portent la « trace » du cahier des charges |
| Anomalies d'un relevé | Compteur déposé depuis le dernier relevé, compteur différent (remplacé), index en baisse, scellé brisé | La casse reste possible, le relevé la montre |
| Compteur déplacé | L'objet garde index, numéro et abonné ; la dépose est notée au registre de l'abonné | Un compteur se démonte sans perdre son index |
| Disjoncteur | Basculé par tous d'un clic droit. Scellé seulement quand il est coupé, par l'ingénieur, avec un objet scellé consommé. Retiré par l'ingénieur accroupi, main vide : le scellé est détruit | « Un disjoncteur scellé ne se réenclenche pas » |
| Disjoncteur cassé scellé | Objet rendu sans scellé, « scellé brisé » noté au registre de son abonné, qu'on inscrit comme pour le compteur | Trace visible au relevé suivant |
| Foudre | Machines du mod dans 16 blocs : −25 points d'état, 25 % de risque de panne de type fusible. Un éclair purement visuel ne fait rien | Valeurs de départ, en config |
| Parafoudre | Sans courant ni consommation. Protège toute machine à 24 blocs ou moins. −10 points par coup encaissé, une seule fois par éclair. À 0, en panne et sans effet jusqu'à réparation | « S'use à chaque coup encaissé » ; 10 coups pour un neuf |
| Ni compteur ni disjoncteur ne sont touchés par la foudre | Ils n'ont pas d'usure | Section 4 du cahier des charges |
| Station météo | 2 FE par tick. Horizon 6 000 ticks, décalé de ±20 % par station, tiré à la pose et gardé. Affichage sur la face avant : soleil, nuage, éclair ; face éteinte sans courant | Choix de Killian pour l'horizon |
| Signal de la station | Redstone 15 sur toutes les faces quand un orage est proche ou en cours, 0 sinon et sans courant | « Signal à l'approche d'un orage » |

## Fichiers

| Fichier | Rôle |
|---|---|
| `grid/GridConfig.java`, `GridRegistry.java` | Réglages `SERVER` (`oneforallsurvival-grid.toml`), blocs, objets, menus |
| `grid/PriorityRelay.java` | Règle pure : répartition d'une offre entre sorties ordonnées, état de chaque sortie |
| `grid/LoadShedderBlock.java`, `LoadShedderBlockEntity.java`, `LoadShedderMenu.java`, `client/LoadShedderScreen.java` | Tableau de délestage |
| `grid/MeterBlock.java`, `MeterBlockEntity.java`, `MeterLedger.java`, `SubscriberSavedData.java`, `MeterReadingItem.java` | Compteur, registre, relevé |
| `grid/BreakerBlock.java`, `BreakerBlockEntity.java`, `SealItem.java` | Disjoncteur et scellé |
| `grid/SubscriberNamePacket.java`, `client/SubscriberNameScreen.java` | Inscription de l'abonné, commune au compteur et au disjoncteur |
| `grid/LightningEvents.java`, `SurgeArresterBlock.java`, `SurgeArresterBlockEntity.java` | Foudre et parafoudre |
| `grid/WeatherForecast.java`, `WeatherStationBlock.java`, `WeatherStationBlockEntity.java` | Station météo |
| `src/test/java/.../grid/{PriorityRelay,MeterLedger,WeatherForecast}Test.java` | Règles pures |
| `src/devcontent/.../{Delestage,Compteur,Disjoncteur,Foudre,Meteo}GameTests.java` | Preuves |

---

### Task 1: Tableau de délestage

**Files:**
- Create: `grid/GridConfig.java`, `GridRegistry.java`, `PriorityRelay.java`, `LoadShedderBlock.java`, `LoadShedderBlockEntity.java`, `LoadShedderMenu.java`, `client/LoadShedderScreen.java`, `client/GridClient.java` ; `src/test/java/dev/oneforallsurvival/grid/PriorityRelayTest.java` ; `src/devcontent/.../DelestageGameTests.java` ; langues.
- Modify: `OneForAllSurvival.java`, `bench/BenchRegistry.java` (onglet créatif), `PackGameTests.java`.

**Interfaces:**
- Consumes: `ElectricMachineBlock`, `ElectricMachineBlockEntity` (`efficiency()`, `fault()`), le motif de voisinage de `InlineEnergyBlockEntity.neighbor()` (jamais de chunk chargé par un appel).
- Produces:
  - `GridConfig.SPEC`, `ModConfig.Type.SERVER`, fichier `oneforallsurvival-grid.toml`, lecteurs avec repli comme `FoodConfig` : `shedderMaxPerTick()` 4096, `meterMaxPerTick()` 4096, `breakerMaxPerTick()` 4096. Les tâches suivantes y ajoutent leurs clés.
  - `enum OutputStatus { NONE, FED, PARTIAL, CUT }`.
  - `PriorityRelay` : `interface Sink { int accept(int amount, boolean simulate); }` ; `static int distribute(int offer, List<Sink> ordered, boolean simulate, int[] delivered, boolean[] starved)` : sert les sorties dans l'ordre, renvoie le total accepté, marque `starved[i]` une sortie atteinte quand l'offre était épuisée et qui acceptait encore (simulation) ; en simulation, ni `delivered` ni `starved` ne changent. `static OutputStatus status(boolean present, int delivered, boolean starved)`.
  - `LoadShedderBlockEntity` : `int[] priorities()` (permutation des 4 sorties) ; `void setPriority(int output, int rank)` qui échange avec la sortie qui avait ce rang ; `OutputStatus status(int output)` et `int lastDelivered(int output)`, figés à la fin de chaque tick serveur ; constantes `OUTPUT_FRONT`, `OUTPUT_LEFT`, `OUTPUT_RIGHT`, `OUTPUT_TOP`.

Règles : énergie exposée sur la seule face arrière, aucune réserve (`energyPerTick` 0). Plafond par tick : `shedderMaxPerTick × efficiency()`. `wantsToWork` vrai si du courant est passé au tick précédent : l'usure n'avance qu'en passant du courant. En panne, rien ne passe et toutes les sorties branchées sont coupées. Le menu synchronise rangs, états et FE par `ContainerData`. Priorités sauvegardées.

- [ ] **Step 1: Écrire `PriorityRelayTest`** : offre 100, sorties qui acceptent 60, 60, 60 : 60, 40, 0 livrés, la deuxième et la troisième `starved` ; offre suffisante : aucune `starved` ; sortie pleine (accepte 0) jamais `starved` ; simulation : tableaux inchangés et total identique ; offre 0 : toutes les sorties qui acceptent sont `starved` ; `status` pour les quatre cas.
- [ ] **Step 2: Écrire `DelestageGameTests`** (`setupTicks = 20`, source créative et machines de test de `dev`) : `lesSortiesSontServiesDansLOrdre`, `quandIlManqueLesDernieresSontCoupees` (états affichés CUT et PARTIAL), `changerUnRangEchangeLesDeux`, `plusieursPoussesRespectentLaPriorite`, `uneSimulationNeChangeRien`, `uneBoucleNeCreeRien`, `enPanneToutEstCoupe`, `useIlLaissePasserMoins`, `ilNeSUseQueQuandLeCourantPasse`, `lesRangsSurviventAuRechargement`, `casseIlGardeSonUsure`, `leTesteurLitLeTableau`.
- [ ] **Step 3: Avec le pack, dans `PackGameTests`** : `leCourantDeCraftsAdditionsTraverseLeDelesteur`, au motif de la preuve des réseaux du lot 0.
- [ ] **Step 4: Lancer, constater l'échec** : `./gradlew test`, `./gradlew runGameTestServer`, FAIL (compilation).
- [ ] **Step 5: Implémenter**, écran au style de `BenchTheme` : les quatre sorties, leur rang (boutons « monter » et « descendre »), leur état en couleur et leurs FE.
- [ ] **Step 6: Lancer, lire** 0 échec JUnit et « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 7: Commit** `feat(grid): tableau de delestage`.

### Task 2: Compteur, abonné et relevé

**Files:**
- Create: `grid/MeterBlock.java`, `MeterBlockEntity.java`, `MeterLedger.java`, `SubscriberSavedData.java`, `MeterReadingItem.java`, `SubscriberNamePacket.java`, `client/SubscriberNameScreen.java` ; `src/test/.../grid/MeterLedgerTest.java` ; `src/devcontent/.../CompteurGameTests.java`.
- Modify: `GridRegistry.java`, `GridClient.java`, `repair/TesterItem.java`, réseau du mod (paquet enregistré comme ceux de `BenchNetwork`).

**Interfaces:**
- Consumes: `InlineEnergyBlockEntity` (`totalTransferred()`), `RoleGate`.
- Produces:
  - `MeterBlockEntity extends InlineEnergyBlockEntity` : `UUID serial()` tiré à la première pose, `String subscriber()` (vide si non inscrit), `void setSubscriber(String)` ; objet lâché avec index, numéro et abonné (`BlockEntityTag`) ; `onRemove` hors déplacement par piston : note `REMOVED` au registre si inscrit.
  - `SubscriberSavedData` : par abonné, nom normalisé (`trim`, minuscules) : dernier relevé (numéro, index, jour, date) et événements depuis (`REMOVED`, `SEAL_BROKEN`, avec jour). `static SubscriberSavedData get(MinecraftServer)`.
  - `MeterLedger` (pur) : `record Reading(String subscriber, long index, long consumption, long day, String realDate, List<Anomaly> anomalies)` ; `enum Anomaly { METER_REMOVED, METER_CHANGED, INDEX_DECREASED, SEAL_BROKEN }` ; `static Reading read(String subscriber, UUID serial, long index, long day, String realDate, @Nullable Previous previous, List<Event> events)`. Premier relevé : consommation = index. Compteur changé : consommation = index du nouveau.
  - `MeterReadingItem` : NBT du `Reading`, infobulle ligne par ligne, anomalies en rouge. Jour = `dayTime / 24000 + 1`. Date réelle formatée par le serveur (`dd/MM/yyyy HH:mm`, fuseau du serveur), stockée en texte.

Gestes : clic droit main vide sur un compteur ou un disjoncteur par l'ingénieur : écran d'inscription (16 caractères, lettres, chiffres, espace, tiret). Par un autre : « Compteur de X » ou « Compteur sans abonné ». Testeur sur un compteur : ingénieur : relevé donné (ou lâché si inventaire plein), registre mis à jour ; autre : message réservé, rien d'écrit. Compteur sans abonné : refus avec message. Le paquet d'inscription vérifie côté serveur le rôle, la distance et le texte.

- [ ] **Step 1: Écrire `MeterLedgerTest`** : premier relevé ; consommation entre deux relevés ; compteur changé ; index en baisse ; événements repris puis vidés ; nom normalisé.
- [ ] **Step 2: Écrire `CompteurGameTests`** : `ilCompteLesFEQuiPassent`, `seulLIngenieurInscritLAbonne` (paquet refusé pour un Citoyen), `unNomInvalideEstRefuse`, `leReleveDonneLaConsommationDepuisLePrecedent`, `unCitoyenNeReleveRien`, `sansAbonnePasDeReleve`, `deposeEtReposeIlGardeSonIndexEtLeReleveLeSignale`, `unCompteurNeufALaPlaceEstSignale`, `leRegistreSurvitAuRechargement`, `ilNeSUsePas`.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer, lire** 0 échec JUnit et « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(grid): compteur, abonne et releve`.

### Task 3: Disjoncteur d'abonné et scellé

**Files:**
- Create: `grid/BreakerBlock.java`, `BreakerBlockEntity.java`, `SealItem.java`, `src/devcontent/.../DisjoncteurGameTests.java`.
- Modify: `GridRegistry.java`, `SubscriberSavedData.java` (s'il manque un accès), langues.

**Interfaces:**
- Consumes: `InlineEnergyBlockEntity.isOpen()`, `SubscriberSavedData`, écran et paquet d'inscription de la tâche 2, `RoleGate`.
- Produces: `BreakerBlock` avec propriétés `CLOSED` (courant passe) et `SEALED` ; `BreakerBlockEntity` : `boolean isOpen()` = `CLOSED` ; `String subscriber()`, `setSubscriber(String)` ; `GridRegistry.SEAL`.

Gestes : clic droit main vide : bascule (tous), refusé si scellé avec message ; l'ingénieur ouvre l'écran d'inscription en clic droit main vide accroupi s'il n'est pas scellé. Scellé en main sur un disjoncteur coupé : l'ingénieur le scelle, un scellé consommé hors créatif ; sur un disjoncteur enclenché : refus. Ingénieur accroupi main vide sur un disjoncteur scellé : scellé retiré et détruit. Un autre joueur : message réservé. Casse d'un disjoncteur scellé : objet sans scellé, `SEAL_BROKEN` au registre de son abonné.

- [ ] **Step 1: Écrire `DisjoncteurGameTests`** : `coupeIlNeLaissePasRien`, `toutLeMondePeutLeBasculer`, `seulLIngenieurScelle`, `onNeScellePasUnDisjoncteurEnclenche`, `scelleIlNeSeReenclenchePas`, `retirerLeScelleLeDetruit`, `casseScelleLeReleveLeSignale`, `lEtatSurvitAuRechargement`, `ilNeSUsePas`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(grid): disjoncteur d'abonne et scelle`.

### Task 4: Foudre et parafoudre

**Files:**
- Create: `grid/LightningEvents.java`, `SurgeArresterBlock.java`, `SurgeArresterBlockEntity.java`, `src/devcontent/.../FoudreGameTests.java`.
- Modify: `GridRegistry.java`, `GridConfig.java`, `OneForAllSurvival.java`, `machine/ElectricMachineBlockEntity.java` (une méthode `protected` ou publique pour retirer de l'état sans passer par le travail, si `setCondition` ne suffit pas).

**Interfaces:**
- Consumes: `ElectricMachineBlockEntity` (`condition()`, `breakDown`, `fault()`).
- Produces:
  - `GridConfig` : `lightningRadius()` 16, `lightningDamage()` 25.0, `lightningFuseChance()` 0.25, `arresterRadius()` 24, `arresterWearPerStrike()` 10.0.
  - `LightningEvents.strike(ServerLevel, Vec3, RandomSource)` : appelé par `EntityJoinLevelEvent` pour un `LightningBolt` non visuel, côté serveur. Ne parcourt que les chunks déjà chargés (`getChunkSource().getChunkNow`).
  - `SurgeArresterBlockEntity` : `wantsToWork` faux, aucune consommation ; `boolean isActive()` = sans panne.

Règle : pour chaque machine du mod hors parafoudre, à 16 blocs ou moins de l'éclair et sans panne : protégée si un parafoudre actif est à 24 blocs ou moins d'elle ; sinon −25 points, et 25 % de risque de `breakDown(FUSE)`. Chaque parafoudre qui a protégé au moins une machine perd 10 points, une seule fois par éclair ; à 0, il tombe en panne. Le parafoudre n'attire pas la foudre : le paratonnerre vanilla garde ce rôle.

- [ ] **Step 1: Écrire `FoudreGameTests`** (éclairs créés par `EntityType.LIGHTNING_BOLT`, risque de panne forcé à 0 ou 1 par la config du test et restauré) : `laFoudreAbimeLesMachinesProches`, `auDelaDe16BlocsRien`, `elleProvoqueUnePanneDeFusible`, `unEclairVisuelNeFaitRien`, `leParafoudreProtegeA24Blocs`, `auDelaDe24IlNeProtegePas`, `ilSUseUneFoisParEclair`, `useAZeroIlNeProtegePlus`, `repareIlProtegeDeNouveau`, `leCompteurEtLeDisjoncteurNeCraignentRien`, `laFoudreNeChargeAucunChunk`.
- [ ] **Step 2: Lancer, constater l'échec.**
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer, lire** « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(grid): foudre et parafoudre`.

### Task 5: Station météo

**Files:**
- Create: `grid/WeatherForecast.java`, `WeatherStationBlock.java`, `WeatherStationBlockEntity.java`, `src/test/.../grid/WeatherForecastTest.java`, `src/devcontent/.../MeteoGameTests.java`.
- Modify: `GridRegistry.java`, `GridConfig.java`.

**Interfaces:**
- Produces:
  - `enum Trend { STABLE, RAIN_SOON, STORM_SOON }`.
  - `WeatherForecast.trend(boolean raining, int rainTime, boolean thundering, int thunderTime, int clearTime, int horizon)` → `Trend`. Ciel forcé clair au-delà de l'horizon : `STABLE`. Pluie maintenant ou qui commence dans l'horizon : au moins `RAIN_SOON`. `STORM_SOON` si, dans l'horizon, l'intervalle de pluie croise l'intervalle d'orage (l'orage vanilla n'a d'effet que sous la pluie).
  - `GridConfig` : `weatherHorizon()` 6000, `weatherJitter()` 0.2, `weatherEnergy()` 2.
  - `WeatherStationBlock` : propriétés `FACING`, `STATE` (héritée), `TREND` ; signal de redstone 15 sur toutes les faces en marche avec `STORM_SOON`.
  - `WeatherStationBlockEntity` : horizon propre tiré à la pose dans `[1 − jitter, 1 + jitter] × horizon` et sauvegardé ; relecture de `ServerLevelData` toutes les 20 ticks, voisins prévenus au changement.

- [ ] **Step 1: Écrire `WeatherForecastTest`** : ciel clair forcé ; pluie dans l'horizon et au-delà ; orage en cours ; orage prévu pendant une pluie ; orage prévu après la fin de la pluie : `RAIN_SOON` seulement ; bornes exactes de l'horizon.
- [ ] **Step 2: Écrire `MeteoGameTests`** (météo posée par `ServerLevel.setWeatherParameters`, remise au clair à la fin) : `parBeauTempsStable`, `pluieProche`, `orageProcheEtSignal`, `sansCourantRienNiSignal`, `lHorizonPropreSurvitAuRechargement`, `elleSUseEnMarche`.
- [ ] **Step 3: Lancer, constater l'échec.**
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer, lire** 0 échec JUnit et « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(grid): station meteo`.

### Task 6: Recettes du matériel et visuels finaux

**Files:**
- Create: recettes de table `load_shedder`, `meter`, `breaker`, `seal`, `surge_arrester`, `weather_station` ; modèles, états de blocs, textures ; `tools/gen_textures_lot4.py`.
- Modify: `DemoVisuels` (captures), `PackGameTests`, langues, tags `mineable/pickaxe`, loot tables.

Le matériel se fabrique à l'établi d'ingénieur, comme celui des lots 2 et 3 : `PackGameTests` vérifie qu'aucune recette de table ne le donne une fois le pack chargé. Ingrédients : plaques, tubes et rouages de Create, fil de cuivre de Crafts & Additions, pièces du lot 2 (fusible, bobine). Le relevé ne se fabrique pas.

Visuels, style bricolage et récupération, palette cohérente avec les lots 2 et 3, 16 x 16, script Python/PIL reproductible, aucun fichier téléchargé : tableau à quatre interrupteurs et quatre voyants ; compteur à cadran et roue ; disjoncteur à manette, avec et sans plomb de scellé ; parafoudre à tige et céramique ; station à girouette et cadran, trois faces de tendance et une éteinte ; objets scellé et relevé. Écrans du délesteur et de l'inscription au style de `BenchTheme`.

- [ ] **Step 1: Écrire les tests de `PackGameTests`.**
- [ ] **Step 2: Lancer, constater l'échec** avec `-PwithPack`.
- [ ] **Step 3: Produire recettes, modèles, textures.**
- [ ] **Step 4: Lancer `runDemoVisuels`**, ouvrir les captures, vérifier blocs, objets et écrans lisibles.
- [ ] **Step 5: Lancer, lire** « All N required tests passed », sans puis avec `-PwithPack`.
- [ ] **Step 6: Commit** `feat(grid): recettes du materiel et visuels finaux`.

### Task 7: Preuve de bout en bout, film et vérification

**Files:**
- Create: `dev/E2eReseau.java`, `dev/FilmReseau.java`, `docs/verifications/lot-4.md`.
- Modify: `dev/E2eClient.java`, `build.gradle` (`runDemoReseauFilm`), `tasks/todo.md`.

**E2E sur serveur dédié, avec le pack, un seul passage :** un alternateur de Crafts & Additions alimente un délesteur, quatre machines derrière ; source réduite : les dernières sorties coupées et l'écran le montre ; compteur inscrit par un ingénieur, relevé obtenu, un Citoyen refusé ; disjoncteur coupé, scellé, bascule refusée ; `/stop`, relance : rangs, index, registre et scellé relus ; un éclair près des machines, parafoudre usé d'un coup ; `/weather thunder` : la station passe à l'orage et donne son signal. Le script ferme son client quel que soit l'écran atteint, et l'essai se termine par `/stop` : ne jamais relancer un client sur un serveur arrêté.

**Film**, monde intégré, annoncé à l'écran quand le temps est accéléré : la pénurie qui coupe les sorties dans l'ordre et le réglage d'un rang ; un relevé lu en infobulle ; le scellé posé ; un éclair sans puis avec parafoudre ; la station qui voit venir l'orage et une alarme qui s'allume. Puis envoi dans la conversation.

- [ ] **Step 1: Écrire E2E et film, lancer.** Lire `[e2e] bilan : TOUT PASSE` dans le journal.
- [ ] **Step 2: Rouvrir le film en images**, vérifier chaque scène.
- [ ] **Step 3: Écrire `docs/verifications/lot-4.md`** : prouvé et non prouvé, séparés. Au moins : équilibrage réel, multijoueur, Citoyen sur serveur dédié, New Age et Advanced Electricity dans le délesteur, alarmes d'Advanced Electricity sur le signal, foudre naturelle d'un vrai orage.
- [ ] **Step 4: Cocher le lot dans `tasks/todo.md`**, avec ce qui reste ouvert.
- [ ] **Step 5: Commit et push** `test(grid): E2E et film du lot 4`, `docs(grid): preuve du lot 4`.
