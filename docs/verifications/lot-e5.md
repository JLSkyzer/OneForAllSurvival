# Vérifications du lot E5 : quêtes

Relevés du 2026-11-17, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 6 (preuve de bout en bout, vérification, wiki). Les tâches 1 à 5 ont leurs JUnit et GameTests, relancés ici. Plan : [`2026-11-15-lot-e5-quetes.md`](../superpowers/plans/2026-11-15-lot-e5-quetes.md). Commits du lot : `8a21713` (modèles et tirage), `3c05c31` (cibles hors registre, `requires`, tags morts), `e85b70d` (carnet, renouvellement, réclamation), `0d55dc3` (progression et garde-fous), `230d4b9` puis `cecc0b1` (blocs qui tombent, contraptions, chunk saturé, rouleau, rails, tuyaux de Create), `0441ba9` et `52b0efc` (modèles et événements de l'équipe), `9271ef7` et `3f181f7` (écran, touche, réseau, notification), puis l'essai de bout en bout et ce document.

**État : le lot est prouvé dans les limites de la section « Non prouvé ».** Sur serveur dédié avec le pack complet, un vrai client (`Dev`, en survie, sans droits) ouvre l'écran par la touche J (un appui envoyé au gestionnaire du clavier du jeu, `KeyboardHandler.keyPress`, pas un vrai clavier) et y lit ses 3 + 5 quêtes, identiques au carnet du serveur ; un granite reposé par une contraption de Create, une pierre posée puis cassée et dix planches posées puis cassées ne font rien avancer ; quatre pierres naturelles finissent une quête (notification « Quête terminée ») qu'un clic sur « Réclamer » paie (ligne « Quêtes » de `/eco stats` et `/eco quests`) ; « Relancer » change une quête et ne se propose plus ; une cuisine au Cooking Pot de Farmer's Delight, ingrédients et bol mis par Dev, fait avancer une quête. Après un vrai `/stop` et une relance avec l'horloge avancée, les journalières sont renouvelées, la quête finie non réclamée attend avec sa récompense, l'hebdomadaire de la même semaine garde sa progression, une pierre posée avant l'arrêt reste marquée et ne compte pas ; « Réclamer l'attente » la paie ; au lundi suivant, 5 nouvelles hebdomadaires, plafond à 0, relance disponible. **Les quêtes à prouver sont imposées par la console** (`e2equetes imposer`) ; le tirage n'est prouvé qu'en JUnit et GameTests.

## Décisions du lot

| Sujet | Décision |
|---|---|
| Nombre et période (79) | 3 journalières, renouvelées chaque jour à 6 h (fuseau `payTimeZone`) ; 5 hebdomadaires, renouvelées le lundi à 6 h ; une quête non finie expire sans pénalité. |
| Modèles | Fichier de données `data/oneforallsurvival/economy/quests.json` (22 modèles), remplaçable par datapack et `/reload` ; tirage pondéré par modèle et par rôle, jamais deux fois le même modèle dans une série ; hebdomadaire = 5 fois la quantité et la récompense (facteur réglable par modèle). Cible hors registre ou tag vide : modèle écarté avec avertissement ; `requires` (mod absent) : écarté. |
| Récompense | Pièces seulement, `max(1, arrondi(récompense × quantité / min))` ; moyennes de la réserve ~20 cuivres une journalière, ~100 une hebdomadaire (estimation sur les valeurs du fichier, pas une mesure en partie). Source `QUEST` comptée au registre, ligne de journal à partir de 100 cuivres. |
| Plafond | 1 000 cuivres par joueur et par semaine (lundi 6 h) ; une réclamation au-delà est réduite puis refusée ; dit à l'écran. |
| Relance | 1 par jour, jamais vers un modèle de la série. Quand il n'en reste plus, le bouton « Relancer » **disparaît** des quêtes en cours (il n'est pas grisé). |
| Attente | Une quête finie non réclamée au renouvellement attend (8 au plus) ; **au-delà de 8, la plus ancienne est perdue** (message au joueur), conforme au plan, signalé à Killian. |
| Catégories | Demandées : casse, casse précise, pose, combat. Proposées et livrées : récolte, pêche, cuisine (four, marmite, Cooking Pot), élevage, conserve (fumoir, séchoir), métier (réparation de machine). **Livraison au grenier : désactivée** (crochet sans appelant) jusqu'au lot du grenier. Quêtes de métier du médecin et du policier : avec leurs lots. |
| Garde-fous | Bloc posé par un joueur marqué dans son chunk (capacité, 4 096 marques par chunk) : piston, bloc qui tombe (marque à l'atterrissage, pose retirée au départ), contraptions, rouleau mécanique, pavage des rails, tuyaux et Schematicannon de Create, blocs formés par les fluides ; pose retirée si le **même joueur** casse le bloc dans la minute (`questPlaceUndoSeconds`) et avant d'avoir réclamé la quête (un autre joueur, une perceuse de Create ou une explosion ne retirent rien ; le stock se recycle de toute façon après 60 s, borné par le plafond) ; faux joueurs, spectateur et créatif exclus (ni geste compté, ni réclamation) ; combat : mob sauvage seulement (marque du butin d'E4), jamais apprivoisé. |
| Cuisine et conserve (passe finale) | Une sortie de four, fumoir, haut fourneau, séchoir, marmite ou Cooking Pot ne compte **que pour la part que le joueur a mise lui-même dans l'entrée par l'écran** (clic, clic majuscule, glisser, livre de recettes ; entrée = ingrédients et récipient, ni combustible ni sel). Crédit par joueur et par bloc, dans les données du bloc (`ForgeData`, écrites avec le chunk) : +N au dépôt, −N à la reprise d'un objet cru, et à chaque sortie prise `min(pris, crédit)` est compté et retiré du crédit, que l'objet fasse avancer une quête ou non. Ce qui entre par trémie, tapis ou bras de Create ne donne rien : **une ferme à trémie ne fait avancer aucune quête de cuisine ni de conserve.** Le crédit part avec le bloc cassé. |
| Bûches d'un arbre qui pousse | **Non marquées, par choix** : c'est la ressource du monde ; une ferme d'arbres coûte pousse, temps et poudre d'os, et le gain est borné par le plafond hebdomadaire. |
| Chunk saturé | À 4 096 marques, un chunk est **saturé pour toujours** : plus aucune casse n'y compte (côté sûr), sans remise à zéro (aucune commande pour le vider). |
| Équipe | `/eco quest template add|remove|list` (mêmes contrôles que le fichier), `/eco quest event start|stop|list` (titre libre de 64 caractères, affiché tel quel), `/eco quest show <joueur>`, `/eco quests` (tableau de bord). **Une cible s'écrit entre guillemets** (`"minecraft:stone"`, `"#minecraft:logs"`, `"*"`) : Brigadier ne lit hors guillemets que `[A-Za-z0-9_.+-]`. |
| Écran | Touche J (réglable, catégorie Lost Island), ouverture au relâchement, J dans l'écran le referme ; notification discrète (toast), rien au chat. Paquets 22 et 23, protocole 8. |
| Sauvegarde | Aucune écriture forcée : carnet dans les données persistantes du joueur (même fichier que l'inventaire), marques dans le chunk, registre et tableau de l'équipe en `SavedData` marqués sales. |
| Film | Aucun (Killian : aucun film avant la fin du cahier). |

## Ce que la tâche 3 a lu au bytecode

Jars du cache de Gradle, `javap -c` :
- **Cooking Pot (Farmer's Delight 1.20.1-1.3.4)** : `CookingPotResultSlot.checkTakeAchievements` remet `removeCount` à zéro ; `CookingPotMenu.quickMoveStack` déplace la pile sans `remove` : deux mixins (case de sortie et clic majuscule).
- **Four** : `ItemSmeltedEvent` part deux fois au clic majuscule et après la remise à zéro du compte : mixin sur `FurnaceResultSlot.checkTakeAchievements` à la place.
- **`LivingDropsEvent`** est tiré même avec `doMobLoot` faux (le combat compte sans butin).
- **Create 6.0.8** : le déployeur pose et casse en `FakePlayer` (non compté) ; la perceuse casse sans `BreakEvent` (une marque peut rester, ce qui ne peut que refuser une casse future) ; les contraptions (`Contraption.addBlocksToWorld`), le rouleau (`RollerMovementBehaviour.tryFill`), le pavage (`TrackPaver.placeBlockIfFree`), les tuyaux (`FluidReactions`) et le Schematicannon (`BlockHelper.placeSchematicBlock`) posent sans événement : mixins dans une configuration à part, appliqués seulement à la version lue (`[6.0.8]`). **Le rouleau mécanique n'est vérifié qu'au bytecode et à la refmap** (ni GameTest, ni essai : il faut une contraption en marche).

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test runGameTestServer` (2026-11-18, 00:33 à 00:37) | « All 907 required tests passed » (00:37:02) ; JUnit : 537 tests, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml`) |
| `./gradlew --offline runGameTestServer -PwithPack` (00:37 à 00:39) | « All 907 required tests passed » (00:39:40) |
| Passe finale : `./gradlew --offline test runGameTestServer` (2026-11-18, 01:35 à 01:37) | « All 912 required tests passed » (01:37:15) ; JUnit : 537 tests, 0 échec, 0 erreur |
| Passe finale : `./gradlew --offline runGameTestServer -PwithPack` (01:37 à 01:40) | « All 912 required tests passed » (01:40:09) |

La passe finale ajoute 5 GameTests (912 au lieu de 907) : `unFourNourriParUneTremieNeCompteRien`, `huitCrusMisParLeJoueurFontHuitCuits`, `tremieEtJoueurMelangesNeComptentQueLaPartDuJoueur`, `leCreditDeCuisineSurvitAuChunk`, `leSpectateurNeReclamePas`, et refait les trois tests de cuisine (dépôt du joueur avant la sortie, sortie sans dépôt qui ne compte pas) ; tous vus rouges avant la correction. Mutations : crédit ignoré (`CookingCredit.take` rend tout le pris) → 4 rouges (trémie « 8 », mélange « 12 », séchoir et Cooking Pot sans dépôt « 4 ») ; reprise d'un cru non retirée du crédit → `huitCrusMisParLeJoueurFontHuitCuits` rouge ; restaurées.

Avant la passe finale : aucun GameTest ni JUnit n'est ajouté par cette tâche (les classes de l'essai, `E2eQuetes` et `E2eQuetesServeur`, sont dans `devcontent` et chargées par le serveur de GameTests sans y jouer). `ButinGameTests.unZombieNoyeGardeSaMarque`, vu une fois rouge pendant une passe mutée de la tâche 5, passe dans les deux passes ci-dessus (907 sur 907) ; une seule observation rouge, cause non lue : à surveiller.

## Serveur dédié et vrai client

`runServer -PwithPack` puis `E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack -Pe2eHorloge=<minutes>` et `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`, puis le passage complet (`runE2eClient -PwithPack`) et sa relance ciblée, sous le verrou de lancement (pris par un script qui relit verrou et file, vidé sans BOM, deux fois laissé à AXIONENGINE entre deux lancements). Monde neuf à chaque premier passage (les anciens rangés hors du dépôt). Client `Dev` avec le pack complet, fenêtre sur le second écran. L'étape est `E2eQuetes.java` (client) et `E2eQuetesServeur.java` (commandes de console réservées au RCON : imposer une quête, lire le carnet, horloge au lundi suivant 6 h + 1 min et retour, marque d'une case, registre), dans `src/devcontent/java/dev/oneforallsurvival/dev/`.

**Ordre.** Premier passage : monnaie (E1), commerce (E2), butin (E4), **quêtes (E5)**, trésor (E3) : la réécriture des références d'E1 et d'E2 faite à la fin d'E3 compte déjà la pièce des quêtes, que Dev range dans son coffre-fort avant E3. Relance : commerce, monnaie, trésor, butin, **quêtes en dernier** (ses réclamations et son saut au lundi après tous les contrôles exacts). L'étape ne compare que la ligne « Quêtes » de `/eco stats` et `/eco quests`.

**Aire.** Boîte de pierre close et couverte (`fill … stone hollow`, intérieur 13 x 4 x 9 à y = 150), à 640 blocs du départ (à l'opposé de celle du butin) ; paisible et naissances coupées le temps de l'étape, puis rendues. Pierres, bûches, piston mécanique, granite, bloc de magma et Cooking Pot posés par la console (sans joueur : sans marque, vérifié). Dev se tient dans une allée à 2 blocs de chaque bloc.

**Gestes de Dev** (survie, non opérateur) : touche J envoyée au gestionnaire du clavier du jeu (`KeyboardHandler.keyPress`, appui puis relâchement sur deux tics, comme GLFW) ; casse au clic gauche tenu (`startDestroyBlock` / `continueDestroyBlock`) à la pioche et à la hache de diamant ; pose d'un vrai clic droit sur le sol ; clics sur « Réclamer », « Relancer », « Réclamer l'attente » au centre des boutons de la mise en page de l'écran ; Cooking Pot ouvert d'un clic droit, deux champignons et un bol déposés par de vrais clics d'inventaire, plat pris d'un clic majuscule ; « Déposer tout » au coffre-fort. La notification est lue dans la file des notifications du client (`ToastComponent`, par réflexion).

### Passages

| Passage | Connexion | Bilan | Contrôles |
|---|---|---|---|
| E1 + E2 + E4 + E5 + E3 seuls (`E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack`), monde neuf | 22:34:54 | « TOUT PASSE » à 22:44:00 | 181 `PASS` (dont 25 des quêtes), 0 `FAIL` ; « -Pe2eHorloge=4397 » |
| Relance ciblée de ce monde (`runServer -PwithPack -Pe2eHorloge=4397`, puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) : E2, E1, E3, E4, puis E5 | 22:50:25 | « TOUT PASSE » à 22:52:21 | 51 `PASS` (dont 14 des quêtes), 0 `FAIL` |
| Passage complet (`runE2eClient -PwithPack`), monde neuf | 22:57:24 | 1 ÉCHEC à 23:03:19 | 148 `PASS`, puis « RCON injoignable » à l'étape du lot 7 (avant les quêtes) : voir les incidents |
| **Passage complet**, monde neuf | 23:13:09 | « TOUT PASSE » à 23:30:05 | **372 `PASS`** (dont 25 des quêtes), 0 `FAIL` ; « -Pe2eHorloge=4354 » |
| Relance ciblée de ce monde | 23:32 | 1 ÉCHEC à 23:34:45 | 44 `PASS` ; contrôle de l'essai faux sur « Réclamer l'attente » (deux attentes, une seule attendue) : voir les incidents, corrigé |
| Passage complet (contrôle corrigé), monde neuf | 23:51:52 | 1 ÉCHEC à 00:09:10 | 367 `PASS` (dont **25 des quêtes, toutes passées**), 1 `FAIL` à l'étape du lot 5, **après** les quêtes et le trésor (le joueur s'est noyé pendant l'essai du plancher de vie de la clôture électrique) ; « -Pe2eHorloge=5755 » |
| **Relance ciblée** de ce monde (contrôle corrigé) | 00:18 | « TOUT PASSE » à 00:20:20 | 51 `PASS` (dont 14 des quêtes), 0 `FAIL` ; « Réclamer l'attente » paie l'attente lue au carnet |
| Passe finale (cuisine à crédit) : E1 + E2 + E4 + E5 + E3 seuls, monde neuf | 2026-11-18, 01:21:55 | « TOUT PASSE » à 01:30:44 | 181 `PASS`, 0 `FAIL` ; Cooking Pot : ingrédients et bol mis par Dev, `cook_meals 1/2` ; « -Pe2eHorloge=5671 » |
| Passe finale : relance ciblée de ce monde | 01:33 | « TOUT PASSE » à 01:35:10 | 51 `PASS`, 0 `FAIL` ; « Claim pending (1) » paie l'attente lue au carnet |

Le passage complet qui fait foi pour les étapes de tous les lots est celui de 23:13 (372 `PASS`, 0 `FAIL`) ; la relance ciblée qui fait foi est celle de 00:18 (monde du passage de 23:51, dont l'étape des quêtes, du trésor et toutes les étapes d'avant ont passé) ; celle de 22:50 la double sur le monde des étapes de l'économie seules. Le joueur mort à la fin du passage de 23:51 réapparaît à la reconnexion de la relance (« joueur mort a la connexion : reapparition »), et toutes les étapes de la relance passent.

### Ce qui passe : premier passage

```
[e2e] PASS aire des quetes en 649, 150, -105 (joueur parti de 9, 69, -5) ; Dev porte 0 cuivres
[e2e] PASS aire close en 648 149 -106 662 154 -96 ; blocs de la console sans marque (MARQUE pose=false bloc=stone
[e2e] PASS le piston mecanique est sorti (granite parti de sa case) ; moteur retire
[e2e] PASS granite repose par la contraption de Create en 654, 150, -104 : MARQUE pose=true bloc=granite (mixin de Contraption.addBlocksToWorld applique sur serveur dedie)
[e2e] PASS Dev en survie (SURVIVAL), operateur = false ; recu minecraft:diamond_pickaxe 1, minecraft:diamond_axe 1, minecraft:stone 4, minecraft:oak_planks 16, minecraft:brown_mushroom 1, minecraft:red_mushroom 1, minecraft:bowl 1
[e2e] PASS ecran des quetes ouvert par la touche key.keyboard.j au relachement ; 3 journalieres, 5 hebdomadaires sur la copie du client
[e2e] PASS ecran : 3 journalieres [preserve_food 0/10 15 IN_PROGRESS, place_planks 0/33 10 IN_PROGRESS, mine_gold 0/6 36 IN_PROGRESS], 5 hebdomadaires [kill_zombies 1/45 135 IN_PROGRESS, place_planks 0/175 55 IN_PROGRESS, mine_iron 0/40 100 IN_PROGRESS, harvest_wheat 0/235 75 IN_PROGRESS, mine_coal 0/75 65 IN_PROGRESS] ; identiques au carnet (cles 2026-11-17 / 2026-11-16) ; plafond « 0 / 1,000 copper this week » ; bas « Dailies in 7 h 21 — weeklies on Monday » ; 3 + 5 lignes
[e2e] PASS J dans l'ecran le referme ; relachee, la touche ne le rouvre pas (aucun ecran 1 s apres)
[e2e] PASS imposees : IMPOSE serie=daily index=0 q=break_stone:0/4:1:false cle=2026-11-17
[e2e] PASS granite repose par une contraption de Create, casse par Dev : la casse n'avance pas ; carnet : journaliere 0 = break_stone 0/4 (1 cuivres)
[e2e] PASS pierre posee puis cassee par Dev (vrais clics) : casse break_stone 0/4 (1 cuivres), pose place_planks 0/2 (1 cuivres)
[e2e] PASS une planche posee puis cassee dix fois de suite ; carnet : journaliere 1 = place_planks 0/2 (1 cuivres)
[e2e] PASS 4 pierres naturelles cassees : break_stone 4/4 (1 cuivres) ; notification « Quest complete » : « Break 4 stone blocks »
[e2e] PASS ecran des quetes ouvert par la touche key.keyboard.j au relachement ; 3 journalieres, 5 hebdomadaires sur la copie du client
[e2e] PASS « Reclamer » clique : +1 cuivre(s) (1 copper) dans la bourse ou l'inventaire de Dev ; « Quetes » de /eco stats : « Quests: 1 copper » (+1) ; /eco quests nomme Dev (« Dev: 1 copper ») et le modele (« break_stone: 1 copper ») : Quest rewards: 1 copper created, 1 claims|Top 10 earners:|  Dev: 1 copper|Top 10 templates by payout:|  break_stone: 1 copper|Quest events running: 0|Quest templates in use: 22
[e2e] PASS relance : mine_gold -> mine_coal 0/22 18 IN_PROGRESS ; plus de relance aujourd'hui, plus de bouton « Relancer » sur les quetes en cours (carnet : relances 1)
[e2e] PASS J dans l'ecran le referme ; relachee, la touche ne le rouvre pas (aucun ecran 1 s apres)
[e2e] PASS deux planches posees et gardees ; carnet : journaliere 1 = place_planks 2/2 (1 cuivres)
[e2e] PASS trois buches posees par la console (sans joueur) cassees ; carnet : hebdomadaire 0 = break_logs 3/10 (20 cuivres)
[e2e] PASS plat cuit au Cooking Pot en 10.1 s : ragout de champignons en sortie
[e2e] PASS cuisine au Cooking Pot de Farmer's Delight : hebdomadaire 1 = cook_meals 1/2 (50 cuivres) (mixin du menu du Cooking Pot applique sur serveur dedie)
[e2e] PASS avant l'arret : e2e-quetes.txt ecrit : journalieres [break_stone 4/4 (1 cuivres, reclamee), place_planks 2/2 (1 cuivres), mine_coal 0/22 (18 cuivres)] ; hebdomadaires [break_logs 3/10 (20 cuivres), cook_meals 1/2 (50 cuivres), mine_iron 0/40 (100 cuivres), harvest_wheat 0/235 (75 cuivres), mine_coal 0/75 (65 cuivres)] ; attente [] ; pierre gardee MARQUE pose=true bloc=stone ; ligne « Quests: 1 copper » ; /eco quests Quest rewards: 1 copper created, 1 claims|Top 10 earners:|  Dev: 1 copper|Top 10 templates by payout:|  break_stone: 1 copper|Quest events running: 0|Quest templates in use: 22
[e2e] PASS Dev devant son coffre-fort
[e2e] PASS Dev range 1 cuivres (la recompense de quete) : coffre-fort 10253 -> 10254
[e2e] PASS objets de l'etape retires (minecraft:diamond_pickaxe x1, minecraft:diamond_axe x1, minecraft:stone x2, minecraft:oak_planks x12, minecraft:mushroom_stew x1, minecraft:cobblestone x2, minecraft:oak_log x1) ; inventaire comme avant l'etape ; difficulte peaceful, naissances false ; Dev rentre, operateur
```

### Ce qui passe : après un vrai arrêt du serveur (relance ciblée)

```
[e2e] PASS relance des quetes : journalieres renouvelees (cle 2026-11-17 -> 2026-11-20, [fish 0/10 (25 cuivres), mine_iron 0/8 (20 cuivres), mine_coal 0/19 (16 cuivres)]) ; en attente : place_planks 2/2 (1 cuivres) du 2026-11-17 ; meme semaine (2026-11-16) : hebdomadaire 0 gardee, break_logs 3/10 (20 cuivres) ; ligne « Quests: 1 copper » et /eco quests identiques (Quest rewards: 1 copper created, 1 claims|Top 10 earners:|  Dev: 1 copper|Top 10 templates by payout:|  break_stone: 1 copper|Quest events running: 0|Quest templates in use: 22)
[e2e] PASS aire des quetes en 649, 150, -105 (joueur parti de 9, 69, -5) ; Dev porte 3 cuivres
[e2e] PASS relance : pierre de Dev en 658 150 -100 : MARQUE pose=true bloc=stone
[e2e] PASS Dev en survie (SURVIVAL), operateur = false ; recu minecraft:diamond_pickaxe 1
[e2e] PASS imposee : IMPOSE serie=daily index=0 q=break_stone:0/4:1:false cle=2026-11-20
[e2e] PASS pierre posee par Dev avant l'arret, cassee apres la relance : la casse n'avance pas ; carnet : journaliere 0 = break_stone 0/4 (1 cuivres)
[e2e] PASS temoin : une pierre naturelle cassee compte ; carnet : journaliere 0 = break_stone 1/4 (1 cuivres)
[e2e] PASS ecran des quetes ouvert par la touche key.keyboard.j au relachement ; 3 journalieres, 5 hebdomadaires sur la copie du client
[e2e] PASS « Claim pending (1) » clique : +1 cuivre(s) ; « Quetes » de /eco stats : « Quests: 2 coppers » (+1) ; /eco quests : « place_planks: 1 copper »
[e2e] PASS J dans l'ecran le referme ; relachee, la touche ne le rouvre pas (aucun ecran 1 s apres)
[e2e] PASS horloge : HORLOGE avant=2026-11-20T23:09:15.076Z maintenant=2026-11-23T05:01:00Z jour=2026-11-23 semaine=2026-11-23 decalage=457724924 ; carnet renouvele par le tic du serveur : semaine 2026-11-23, jour 2026-11-23, hebdomadaires [break_logs:0/310:115:false;kill_monsters:0/50:75:false;fish:0/35:90:false;mine_iron:0/55:140:false;cook_food:0/50:75:false;], plafond restant 1000
[e2e] PASS ecran des quetes ouvert par la touche key.keyboard.j au relachement ; 3 journalieres, 5 hebdomadaires sur la copie du client
[e2e] PASS lundi : 5 nouvelles hebdomadaires (semaine 2026-11-23) [break_logs 0/310 115 IN_PROGRESS, kill_monsters 0/50 75 IN_PROGRESS, fish 0/35 90 IN_PROGRESS, mine_iron 0/55 140 IN_PROGRESS, cook_food 0/50 75 IN_PROGRESS] ; plafond « 0 / 1,000 copper this week » ; 1 relance(s), bouton « Relancer » present ; bas « Dailies in 23 h 59 — weeklies on Monday »
[e2e] PASS J dans l'ecran le referme ; relachee, la touche ne le rouvre pas (aucun ecran 1 s apres)
[e2e] PASS relance des quetes finie : HORLOGE maintenant=2026-11-20T23:09:20.028Z jour=2026-11-20 semaine=2026-11-16 decalage=263820000 ; retires minecraft:diamond_pickaxe x1, minecraft:cobblestone x1 ; Dev garde les 4 cuivres portes
```

Le client de l'essai est en anglais (langue du dossier `run-e2e`) : « Quest complete », « Claim pending (1) », « Quests: 1 copper ». Les lignes de `/eco stats` et `/eco quests` sont comparées à celles que le serveur compose lui-même (`e2equetes registre`). La récompense de la pierre × 4 et des planches × 2 vaut 1 cuivre (`max(1, arrondi(10 × 4 / 64))`) : de petites sommes, imposées pour que l'essai reste court.

**Mixins de Create et de Farmer's Delight.** Le journal du serveur dédié dit, à chaque démarrage, « Quêtes : mixins de create 6.0.8 appliqués » et « Quêtes : mixins de farmersdelight 1.20.1-1.3.4 appliqués », sans avertissement de version non reconnue. L'essai les montre à l'œuvre : un granite reposé par la démontée d'un vrai piston mécanique (moteur créatif posé puis retiré) est marqué et sa casse ne compte pas ; un plat pris au Cooking Pot fait avancer la quête de cuisine. **C'est le serveur de développement avec le pack** (refmap remappée par `fg.deobf`), **pas le jar de production** : celui-ci n'est pas essayé, car installer un serveur Forge hors de Gradle demande l'accord de Killian (téléchargement).

**Touche J.** L'écran s'ouvre au relâchement de J (`key.keyboard.j`), J dans l'écran le referme, et le relâchement qui suit ne le rouvre pas (aucun écran une seconde après), à chaque ouverture de l'essai.

« [souris] rendue » et « [fenetre] second écran (2600, 40) » sont lus dans le journal de chaque client ; aucun client ni serveur n'est resté ouvert (le serveur s'arrête par `/stop`) ; le verrou est vidé après chaque lancement. Les mondes et journaux sont rangés hors du dépôt, dans le dossier temporaire de la session.

### Incidents de l'essai

- Premier lancement (2026-11-16, 00:11) : arrêté à la demande de Killian pendant l'étape E1 (22 `PASS`, 0 `FAIL`) ; serveur arrêté par RCON, monde rangé hors du dépôt.
- Premier passage complet (2026-11-17, 22:57) : « RCON injoignable » pendant l'étape du lot 7 (148 `PASS`, puis 1 `FAIL`), avant l'étape des quêtes. Le journal du serveur montre « Can't keep up! … 25025ms or 500 ticks behind » à ce moment, puis 2 min 46 s pour écrire les chunks à l'arrêt (vidage d'image du fil du serveur : en attente de `ChunkStorage.flushWorker`) : le serveur a calé sur l'écriture du disque, sans lien avec le lot. Repassé sur un monde neuf : tout passe.
- Relance ciblée de ce second passage complet (23:30) : l'étape des quêtes a échoué sur son propre contrôle (« gain 16, attendu 1 ») : au passage complet, la marmite de l'étape du lot 5 (après les quêtes) avait fini une journalière de cuisine (`cook_meals`, 15 cuivres) tirée un jour avancé par l'horloge d'E3, passée en attente au renouvellement ; « Réclamer l'attente » a payé les deux attentes (1 + 15 cuivres), comme il le doit. **Défaut de l'essai, pas du jeu** : le contrôle attendait la seule attente des planches. Corrigé (somme de toutes les attentes lues au carnet juste avant le clic, réclamations + nombre d'attentes), puis passage complet et relance refaits sur un monde neuf.
- Passage complet de 23:51 : toutes les étapes jusqu'aux quêtes et au trésor passent, puis l'étape du lot 5 échoue sur « Le plancher de vie tient » : le joueur, à faible vie (8,8, membres de First Aid déjà blessés), est mort **noyé** (« Dev drowned », son `hurt_drown`) pendant la série de chocs de la clôture électrique ; 3 chocs en 35 s, contre 15 chocs en 15 s au passage de 23:13 sur la même étape. **Cause non établie** : l'étape ne pose aucune eau ; le serveur tournait nettement plus lentement que lors du passage de 23:13 (cause de la lenteur non relevée). Sans lien avec le lot E5 (étape du lot 5, après celle des quêtes) ; ouvert dans le rapport de la tâche.

## Fermes et équilibrage

- **Fermes de pêche à l'arrêt automatique** (canne posée, clic tenu) : permises, la pêche compte comme un geste du joueur ; bornées par le plafond hebdomadaire.
- **Fermes de cuisine** (trémie ou Create qui nourrit un four, un fumoir, un séchoir ou une marmite) : **ne comptent pas** depuis la passe finale ; seule la part mise par le joueur à l'écran compte (voir « Cuisine et conserve »). Écart connu : le crédit se compte en objets mis, pas en recettes ; au Cooking Pot, plusieurs ingrédients font un plat, donc un joueur qui met 6 ingrédients et un bol peut faire compter jusqu'à 7 plats si une trémie fournit le reste (gain borné par ce qu'il a mis lui-même et par le plafond) ; à la marmite (3 portions par fournée), une recette sans récipient compte moins de portions qu'elle n'en donne (sous-compte, côté sûr).
- **Fermes à arbres** : les bûches d'un arbre qui pousse ne sont pas marquées (choix) ; bornées par le plafond.
- **Après une mise à jour de Create ou de Farmer's Delight**, même mineure, leurs mixins sont **coupés** (avertissement au journal) jusqu'à une nouvelle lecture du bytecode : contraptions, rouleau, rails, tuyaux et Schematicannon ne marquent plus, la cuisine du Cooking Pot ne compte plus. Le gain d'un abus pendant ce temps est **borné par le plafond hebdomadaire de 1 000 cuivres** par joueur.
- **Blocs déplacés par une contraption qui démonte ses blocs ailleurs** : la marque de la case de départ ne suit pas (non gardé) ; un bloc naturel déplacé est marqué à l'arrivée (côté sûr).
- **Équilibrage** : rien n'a été vécu en partie. Moyennes de la réserve (estimation sur `quests.json`, pas une mesure) : ~20 cuivres une journalière, ~100 une hebdomadaire ; 3 journalières par jour pendant 7 jours et 5 hebdomadaires font ~920 cuivres par semaine si tout est fait (estimation), juste sous le plafond de 1 000, qui mord surtout avec des événements, des modèles de l'équipe ou des quêtes au-dessus de la moyenne. L'équipe change les valeurs par datapack et `/reload`, et lit `/eco quests`.

## Non prouvé

- **Jar de production** : jamais lancé ; mixins prouvés sur le serveur de développement avec le pack seulement.
- **Rouleau mécanique de Create** : vérifié au bytecode et à la refmap seulement.
- **Tirage réel sur serveur dédié** : l'essai impose les quêtes ; le tirage pondéré, l'exclusion des doublons et la pondération par rôle ne sont prouvés qu'en JUnit et GameTests. L'essai lit toutefois 3 + 5 quêtes tirées, distinctes, identiques au carnet.
- **Plafond atteint et réclamation partielle** : GameTests seulement (`lePlafondReduitPuisArrete`, `unEvenementFiniSePaieSousLePlafond`).
- **Plus de 8 attentes** : la plus ancienne est perdue (JUnit `laPlusAncienneAttenteEstOubliee`), pas vécu en jeu.
- **Chunk saturé** : GameTest `unChunkSatureNeCompteAucuneCasse` seulement.
- **Quêtes d'événement** : GameTests (`QuestBoardGameTests`, `unEvenementSeReclameParPaquet`) ; la commande `event start` n'est pas jouée par l'essai.
- **Livraison au grenier** : absente, faute de grenier. **Quêtes du médecin et du policier** : à venir avec leurs lots.
- **Limite de Minecraft, réclamation puis arrêt brutal** : la pièce et la marque « payée » sont dans le même playerdata ; la création au registre (`SavedData`) non. Un joueur qui réclame puis se déconnecte (playerdata écrit) avant un arrêt brutal **avant la sauvegarde du monde** garde ses pièces sans que le registre les compte ; jamais de double paie. Même classe de fenêtre que dans [`lot-e4.md`](lot-e4.md) ; elle dure au plus jusqu'à la sauvegarde automatique suivante. **Aucune écriture forcée**, par décision.
- **Arrêt brutal** : l'essai fait un vrai `/stop`, pas un arrêt brutal du processus.
- **Relance complète du banc** : toujours en échec sur la couveuse du lot 5 (défaut du banc, ouvert dans `tasks/todo.md`) ; la relance ciblée de l'économie est faite à la place.
- **Mondes d'avant E5** : les chunks sauvés avant ce lot n'ont aucune marque de pose ; démolir un bâtiment de pierre, de bûches ou de sable bâti avant la mise à jour fait avancer les quêtes de casse, une fois par bloc, borné par le plafond. Sans objet pour un monde neuf. Même chose pour la cuisine : un four rempli avant la passe finale n'a aucun crédit (sous-compte, côté sûr).
- **Un seul monde avec passage complet vert et relance verte** : aucun. Le passage complet vert de 23:13 a eu sa relance en échec (contrôle de l'essai, corrigé) ; celui de 23:51 a échoué au lot 5, après les quêtes. Pour E5 seul, le scénario du cahier (terminer, réclamer, relancer, retrouver, changer de date) est prouvé sur un même monde : passage de 23:51 (étape des quêtes verte) puis relance de 00:18.
- **Cuisine dans les GameTests** : la cuisson elle-même est simulée (l'entrée devient la sortie, sans combustible ni attente) ; les dépôts du joueur (clic, clic majuscule, glisser, livre de recettes), la trémie (vraie trémie posée sur le four), la sortie et la relecture du chunk sont réels.
- **Mode créatif et spectateur** : la réclamation y est refusée (`CREATIVE`, `SPECTATOR`), les gestes ne comptent pas ; la duplication par le clic milieu (leçon du 2026-11-12) n'est pas du ressort de ce lot.
