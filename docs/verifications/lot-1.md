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

### Repartition des temps avec le pack (1125 recettes au catalogue, hors recettes d'essai du mod)

Algorithme (corrige apres relecture) : plus petit point fixe, independant de l'ordre. Chaque ingredient
liste tous les fabricants possibles du catalogue (pour un tag, ceux de tous ses objets) ; on retient le
moins cher, et la rarete la plus basse. Une boucle sans entree (lingot, bloc, pepite) compte ses
fabricants comme matiere premiere. Duree calculee d'au moins 1 s ; une duree imposee est plafonnee a
86400 s.

min 5 s, mediane 8 s, max 86 s, 0 recette au plafond de 600 s.

| Les cinq plus longues | Temps |
|---|---|
| light_bulb_addon_create_crafts_and__addions:wind_turbine_large | 86 s |
| light_bulb_addon_create_crafts_and__addions:asphalt_line_white | 61 s |
| light_bulb_addon_create_crafts_and__addions:asphalt_line_yellow | 61 s |
| light_bulb_addon_create_crafts_and__addions:andesite_catwalk_stairs | 52 s |
| light_bulb_addon_create_crafts_and__addions:wind_turbine_medium | 51 s |

La premiere mesure (mediane 10 s, max 148 s, blocs de laiton et de zinc en tete) etait un artefact de
l'ancien algorithme : le bloc heritait d'une chaine de boucle choisie selon l'ordre des identifiants.
A juger par Killian : le plafond de 600 s reste inutilise.

## Tache 8 : JEI, film et preuve de bout en bout (2026-11-06)

### JEI

JEI 15.74.0.234 (derniere 15.x pour 1.20.1 au 2026-11-06, depot maven.blamejared.com) : API en
`compileOnly` (`jei-1.20.1-common-api` et `-forge-api`), JEI complet (`jei-1.20.1-forge`) en
`runtimeOnly` dans le pack seulement. Le depot est restreint par groupes : il faut y ajouter `mezz.jei` et
`net.mezzdev.config` (dependance de JEI). `dependencies --configuration runtimeClasspath -PwithPack` montre
`jei-1.20.1-forge` et ses `-common`, `-lib`, `-gui` : des artefacts Forge, pas Fabric.

Plugin `bench/compat/BenchJeiPlugin` (seule classe qui nomme JEI) : categorie « Etabli d'ingenieur » batie
sur le catalogue du client, l'etabli en catalyseur. Le catalogue arrive par paquets, parfois apres le
demarrage de JEI : `BenchClientState` previent ses abonnes, le plugin retire les anciennes recettes
(`hideRecipes`) et ajoute les nouvelles (`addRecipes`). Le serveur envoie desormais le catalogue des la
connexion (`BenchNetwork.onLogin`), plus seulement a la premiere ouverture d'un etabli.

Preuve (client reel, JEI charge, `-Doneforallsurvival.jeicheck`) :

```
JEI: catalogue de l'etabli rafraichi, 1129 recettes (generation 2)
JEI-CHECK (nouveau catalogue) 777 objets distincts sur 1129 recettes d'etabli : 0 avec une recette de table (violations), 0 sans recette d'etabli []
```

Mesure : pour CHAQUE objet distinct fabrique par une recette de l'etabli (777), recherche JEI des recettes
`RecipeTypes.CRAFTING` et de la categorie de l'etabli ayant cet objet pour sortie. 0 objet avec une recette
de table, 0 objet sans recette d'etabli. (Premiere version du controle : un seul objet, `acacia_window`.)
Ne mesure pas les recettes de table qui produiraient l'objet par un autre type que `CRAFTING`.

Resultat : JEI ne montre aucune recette de table pour un objet gere, et montre celle de l'etabli. La purge du
`RecipeManager` precede bien l'envoi aux clients (question laissee ouverte a la tache 1). Les recettes de
`create:mechanical_crafting` et autres types de machines restent dans JEI : ce sont des machines.

### Film de demonstration

`./gradlew runDemoEtabliFilm -PwithPack` (dev seulement) : un client qui se pilote seul dans un monde
integre, fenetre 1280 x 656 + bandeau de 64 px (legende, valeurs lues en direct), 10 images par seconde,
assemblees par Windows Media Foundation (rien de telecharge). `run-demo/lot-1-etabli.mp4` : 65,4 s,
1280 x 720, H.264, 19,3 Mo. Scenes : le bloc dans le monde ; vraie commande `/role set Dev ingenieur` ;
table de craft (temoin : 2 planches donnent 4 batons ; grille d'une recette geree : aucun resultat, journal :
« 0 recette(s) de table fabriquent 1 cardboard_boots ») ; ecran de l'etabli (catalogue 1129 recettes) ;
recherche ; plusieurs commandes ; annulation (Alliage d'andesite : 2 -> 3) ; vitesse a la main puis
alimentee par un vrai bloc `createaddition:creative_energy` pose contre l'etabli, vitesses mesurees sur
la tete de file ; stock et retrait ; retrait des triches du monde puis Citoyen : « Seul un Ingenieur peut
faire cela. »

Piege rencontre : le propre joueur d'un monde solo avec triches est operateur, et `RoleGate` laisse la
main aux operateurs (niveau 2) : sans retirer les triches, un « Citoyen » ouvrait l'etabli. Ce n'est pas
un defaut (choix de depannage documente), mais le film doit l'eviter.

### Serveur dedie et vrai client

`./gradlew runServer -PwithPack` puis `./gradlew runE2eClient -PwithPack` (client `Dev`, op du serveur,
connexion a `127.0.0.1` (le serveur ecoute desormais sur `server-ip=127.0.0.1` : `localhost` peut se resoudre en `::1`, refuse), helper `E2eClient` de `devcontent`). Lignes du journal client :

```
[e2e] PASS connecte a un serveur distant (aucun serveur integre) : 127.0.0.1
[e2e] PASS catalogue recu par le reseau a la connexion : 1129 recettes, generation 2, apres 0,1 s
[e2e] PASS commandes /role et /give executees par le serveur, provisions recues
[e2e] PASS etabli pose par un clic droit reel en BlockPos{x=-34, y=80, z=238}
[e2e] PASS ecran de l'etabli ouvert par clic droit reel (role Ingenieur accepte par le serveur)
[e2e] PASS commande acceptee : ingredients preleves (2 -> 0), file de 2 fabrication(s), vitesses serveur main x1.0 / alimente x4.0
[e2e] PASS craft termine cote serveur : stock 0 -> 8 objet(s) apres 10,4 s
[e2e] PASS ecran de stock ouvert : 1 pile(s)
[e2e] PASS stock retire : +8 brass_bars dans l'inventaire
[e2e] bilan : TOUT PASSE
```

Journal serveur : `Done (6.361s)`, connexion de `Dev`, `/stop` de l'op client (« Stopping the server »),
« All dimensions are saved », aucune ligne ERROR ni FATAL, et aucun processus Java restant. Film du client
reel : `run-demo/lot-1-e2e-serveur.mp4` (remplacee depuis par le film de la relance de la revue finale, voir plus bas). Port 25565 ferme et aucun Java restant a la fin.

Defaut trouve et corrige par cet essai : le menu de l'etabli n'a aucune case, donc le serveur ne
synchronisait pas l'inventaire du joueur a travers lui, et le client n'accepte d'un menu ouvert que la
barre d'action. Resultat : apres une commande, le serveur avait 2 lingots, l'ecran en montrait 4 (compteurs
« possede » de l'ecran faux jusqu'a la fermeture). Correctif : `EngineerBenchMenu` renvoie chaque tick les cases modifiees par `ClientboundContainerSetSlotPacket(-2, ...)`. L'essai passe de « 2 -> 2 » a « 2 -> 0 ».
Le mecanisme vanille (`inventoryMenu.broadcastChanges()`) ne convient pas : `BenchOrders.order` l'appelait deja, et le client restait faux ; le bytecode de `ClientPacketListener.handleContainerSetSlot` montre que l'identifiant 0 n'est applique qu'aux cases de la barre d'action (`isHotbarSlot`) quand un autre menu est ouvert. GameTest `unAchatPendantLEcranMetLInventaireDuClientAJour` : modele le client avec ces regles ; vu en echec sans le correctif (« case 20 : le serveur a 3 iron_ingot, le client verrait 5 iron_ingot »), puis passe avec.

Deux GameTests ont du changer avec l'envoi a la connexion (`ouvrirExigeLeRoleEtEnvoieLeCatalogue`,
`leCatalogueEstEnvoyeUneFoisParGeneration`) : ils rejouent l'evenement de synchronisation (`connecter`, qui
publie maintenant `OnDatapackSyncEvent`).

### Revue finale : correctifs et preuves (2026-11-06)

Le catalogue part maintenant sur `OnDatapackSyncEvent` (et non plus `PlayerLoggedInEvent`). Ordre lu dans le
bytecode (`javap` sur le jar Forge 47.4.28 mappe officiel) : `placeNewPlayer` publie l'evenement apres
`ClientboundLoginPacket` avec le joueur ; `MinecraftServer.reloadResources` appelle `updateRegistryTags` (qui
publie `TagsUpdatedEvent`, donc installe le nouveau catalogue) AVANT `PlayerList.reloadResources`, qui publie
l'evenement (joueur nul : tous les joueurs connectes) avant d'envoyer etiquettes et recettes. Le nouveau
catalogue est donc bien installe au moment de l'envoi.

Preuves par GameTest : `apresUnReloadLeJoueurConnecteRecoitLeNouveauCatalogue` (vrai `/reload` sur le serveur
d'essai, joueur sur connexion embarquee : il recoit tous les fragments de la nouvelle generation, taille
egale a celle du catalogue serveur ; vu en echec avant le correctif : « aucun catalogue recu apres le
reload ») et `lEntreeEnJeuEnvoieLeCatalogue` (la vraie entree en jeu). Cote ecran, un catalogue qui change
pendant qu'un `EngineerBenchScreen` est ouvert est relu par `syncCatalog` : la recette choisie disparue donne
`selected = null` (le volet de detail et la commande testent deja `null`), et l'onglet d'un mod disparu
revient a « Tout ». Cette partie cliente n'a pas de test automatique.

Autres correctifs, chacun vu en echec avant : piles payees d'une fabrication au resultat inconnu remises au stock
(`unResultatInconnuRendLesPilesPayeesAuStock`, journal WARN), pile payee illisible sautee sans perdre
l'entree, annulation vers un joueur mort ou creatif a inventaire plein (`Inventory.add` vide la pile en
creatif quand rien n'entre : lu dans le bytecode) envoyee au stock, controle de portee du menu avant la lecture
du bloc (`unMenuLointainNeChargePasDeChunk`), recette de l'etabli exemptee en dur de la gestion meme si
`tableAllowedItems` est vide. Le `catch` de `deliver` ne recopie plus la pile payee (risque de doublon apres
une insertion partielle) : pas de test, l'exception n'est pas provocable proprement.

### Serveur dedie et vrai client, relance de la revue finale (2026-11-06)

Cette relance a ete precedee de plusieurs echecs, tous expliques :
- Un essai a atteint « l'ecran ne s'est pas ouvert » : l'`E2eClient` posait l'etabli jusqu'a 6 blocs des pieds ;
  Forge 47.4.28 refuse un clic a plus de 4,5 + 1,5 blocs de l'oeil (`canReach(pos, 1.5)` dans
  `handleUseItemOn`, lu au bytecode), donc ni la pose ni l'ouverture n'atteignaient le serveur (aucune ligne
  « File envoyee » au journal serveur). Les places proches etaient prises par les etablis des essais
  precedents. Correctif de l'`E2eClient` seulement (devcontent) : cible a 2 a 4 blocs, dans toutes les directions.
- Trois lancements du client sont restes bloques en phase CONNEXION (le serveur avait envoye ses 27 messages
  de poignee de main FML, le client n'en a recu aucun et a fini par se deconnecter). Aucun lien etabli avec le
  code du mod : les lancements voisins, avec les memes binaires, se sont connectes en 10 a 16 s. Le premier
  blocage (13:58) a eu lieu avec les correctifs de la revue deja en place, alors que les essais de 12:53 et 13:21
  passaient sans eux ; des lancements avec ces correctifs passent aussi. Voir l'enquete ci-dessous.

### Blocage a la connexion du client d'essai : enquete (2026-11-06)

**Cause non trouvee a ce stade ; mecanisme releve depuis, voir [le lot 2](lot-2.md).** 5 blocages entre 13:58 et 15:13 (dont 2 reproduits par une boucle serveur + client), puis
14 lancements de suite sans blocage (15:16 a 16:22), sans changement du code du mod. Non reproductible a ce jour.

Ecarte, par un releve pendant le blocage :
- interblocage de fils : `jstack` des deux JVM (15:04) sans interblocage, `Netty Client IO` et `Netty Server IO`
  au repos dans `WEPoll.wait`, serveur en attente de son tick ;
- lecture coupee par `Connection.sendPacket` : `autoRead=true`, protocole `LOGIN`, canal ouvert et actif (15:13) ;
- mixin de Create charge sur `Server Connector #1` : la meme chose dans des essais reussis (16:06, 16:16, 16:21).

Constate : le serveur envoie ses 27 messages de poignee de main FML, le client ne journalise aucune reception
(pas de `Recieved login wrapper packet`), puis les deux sessions tombent apres 30 s. Le code du mod n'envoie rien
a ce stade : le catalogue part apres l'entree en jeu. Cela ne prouve pas que le mod n'y est pour rien.

Pas fait, faute de blocage : etat d'inscription du socket (`interestOps`, `readPending`) et relance par
`channel.read()` ; la meme serie sans l'etabli, sans valeur tant que le taux de blocage est nul. Non prouve : si de
vrais joueurs sont touches. Prochaine etape si cela revient : `jstack` pendant le blocage, et ces deux releves.
- Ces blocages ont ete signales comme une possible intervention exterieure ; rien ne le confirme.

Essai final, passe : toutes les lignes `[e2e] PASS`, « bilan : TOUT PASSE », arret par `/stop`, 0 ERROR ni
FATAL au journal serveur, port 25565 ferme et aucun Java restant. Le craft a pris 28,2 s au lieu de 10,4 s :
le serveur signalait « Can't keep up » pendant le demarrage ; non mesure ailleurs. Film refait
(`run-demo/lot-1-e2e-serveur.mp4`, 45,5 s, 455 images a 10 i/s, 13,8 Mo, H.264 1280x720, relu par le verificateur).

### Tout relance

`./gradlew test build runGameTestServer -PwithPack` : build reussi, « All 119 required tests passed » (journal du passage, 13:13). Jar
(`build/libs/oneforallsurvival-0.1.0.jar`, 207581 octets) : aucune classe de `devcontent`, de film ou
d'essai ; `BenchJeiPlugin` et `BenchJeiPlugin$Category` y sont, JEI n'est ni embarque ni requis.
La course de demarrage de Create (`ConcurrentModificationException` a la creation du mod `create`) est
apparue une fois sur trois lancements de `runGameTestServer -PwithPack` ce jour (Gradle rend alors un code
0 malgre l'echec : lire le journal), relance sans autre changement. Serveur dedie et client : 0 sur les
lancements finaux.

Revue finale : `./gradlew test build runGameTestServer -PwithPack` puis la meme commande sans `-PwithPack` :
build reussi, « All 128 required tests passed » dans `run-gametest/logs/latest.log` aux deux passages (13:47
et 13:50, journal relu), jar de 209889 octets sans classe de `oneforallsurvival/dev/`. La course de Create
au demarrage est aussi apparue une fois sur le serveur dedie ce jour (crash de chargement, relance sans
changement).

### Temps de fabrication, recomptes par relance (journal `[etabli]` de `run-gametest/logs/latest.log`)

```
[etabli] catalogue : 1129 recettes
[etabli] TEMPS n=1125 min=5 mediane=8 max=86 auPlafond=0
[etabli] TEMPS-TOP 86 s | light_bulb_addon_create_crafts_and__addions:wind_turbine_large
[etabli] TEMPS-TOP 61 s | light_bulb_addon_create_crafts_and__addions:asphalt_line_white
[etabli] TEMPS-TOP 61 s | light_bulb_addon_create_crafts_and__addions:asphalt_line_yellow
[etabli] TEMPS-TOP 52 s | light_bulb_addon_create_crafts_and__addions:andesite_catwalk_stairs
[etabli] TEMPS-TOP 51 s | light_bulb_addon_create_crafts_and__addions:wind_turbine_medium
[etabli] SPECIALE create:crafting/curiosities/item_copying | ...ItemCopyingRecipe | classe non standard
[etabli] SPECIALE create:crafting/curiosities/toolbox_dyeing | ...ToolboxDyeingRecipe | classe non standard
[etabli] SPECIALE light_bulb_addon_create_crafts_and__addions:key_copy | ...KeyCopyRecipe | classe non standard
```

Les chiffres de la tache 2 (mediane 8 s, max 86 s) sont confirmes. Les trois recettes speciales restent
sur la table, comme releve a la tache 1.

### Ce qui est prouve, ce qui ne l'est pas

Prouve : catalogue envoye a la connexion par un vrai reseau ; pose, ouverture, commande, craft, stock et
retrait par un vrai client contre un serveur dedie ; arret propre par `/stop` ; JEI sans recette de table
pour un objet gere ; refus d'un Citoyen non operateur (film) ; vitesses main et alimentee mesurees.

Prouve en plus (revue finale) : le serveur renvoie le catalogue aux joueurs deja connectes apres un `/reload`
(GameTest sur un vrai `/reload` et une connexion embarquee, voir ci-dessus). Jusqu'ou : jusqu'aux paquets
decodes et a `BenchClientState` du test, pas jusqu'a un vrai client.

Non prouve : le rafraichissement de JEI dans un vrai client apres un `/reload` en jeu (seul le cas « le
catalogue arrive apres le demarrage de JEI » est observe ; l'abonne `BenchJeiPlugin` est declenche par
l'arrivee du catalogue, ce qui n'a pas ete execute apres un `/reload` reel) ; le role Citoyen contre un serveur dedie (le
client d'essai est operateur, et l'operateur passe `RoleGate` : l'essai ne separe pas role et operateur) ;
le film montre une source C&A collee a l'etabli, pas un fil tire (les fils sont prouves au lot 0) ;
plusieurs joueurs a la fois ; une partie longue. Le serveur d'essai tourne avec `online-mode=false` sur le
port 25565 : un scanneur externe a tente de s'y connecter pendant l'essai (rejete : client non Forge) ;
ne pas le laisser tourner hors essai.

Sans le pack (`./gradlew runGameTestServer`, JEI absent, 0 mention de JEI au journal) : « All 118 required tests passed » (avant l'ajout du 119e test), le mod se charge normalement. Observation sur le pack : 118 tests passent avec comme sans le pack, car les essais « avecLePack_ » s'ecartent d'eux-memes quand il manque ; le compte ne distingue donc pas les deux, il faut lire les lignes `[etabli]` pour savoir si le pack etait la.

Compromis de la connexion : le catalogue (1129 recettes, fragments d'au plus 256 Kio) part maintenant a chaque connexion de chaque joueur, ingenieur ou non, meme sans jamais ouvrir d'etabli ; sa taille totale n'a pas ete mesuree.
