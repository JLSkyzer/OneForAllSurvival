# Vérifications du lot E4 : butin des mobs

Relevés du 2026-11-15, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 4 (preuve de bout en bout, vérification, wiki). Les tâches 1 à 3 ont leurs JUnit et GameTests, relancés ici. Plan : [`2026-11-15-lot-e4-butin.md`](../superpowers/plans/2026-11-15-lot-e4-butin.md). Commits du lot : `5be10c2` (table de butin), `46d2d3b` (origine des mobs), `36f0ed7` (sortie du joueur d'essai du générateur), `e6448d1` (butin à la mort, registre, `/eco loot`), `c5f08c8` (leçons), `34b0ffd` (délai des boss, faux joueurs, tests à vide), puis l'essai de bout en bout et ce document.

**État : le lot est prouvé dans les limites de la section « Non prouvé ».** Un zombie né naturellement et tué par un vrai joueur, sans droits, à l'épée, sur serveur dédié avec le pack complet, lâche des pièces que le joueur ramasse ; la ligne « Butin des créatures » de `/eco stats` monte d'autant et `/eco loot` le nomme. Un zombie de générateur, un zombie d'œuf posé par le joueur et un veau d'élevage grandi ne donnent rien. Après un vrai `/stop` et une relance, le registre du butin est relu à l'identique, un zombie marqué avant l'arrêt donne encore, un second veau né après la relance ne donne rien. **La table de l'essai est « certaine »** (un zombie courant donne 3 cuivres à coup sûr, `e2ebutin certain on`) : les chances de la table livrée ne sont prouvées qu'en JUnit et GameTests.

## Décisions du lot

| Sujet | Décision |
|---|---|
| Butin (78) | Valeurs de départ du cahier : courant 1 à 3 cuivres une fois sur 3 ; dangereux 1 à 2 argent une fois sur 2 ; boss 5 or, plus 1 émeraude une fois sur 10 et 1 rubis une fois sur 100 ; animal sauvage 1 cuivre une fois sur 4. Plafond horaire par joueur ajouté à la passe finale (200 cuivres sur 60 minutes glissantes, boss exemptés) ; plafond de raid et délai des boss en plus. |
| Table | Fichier de données `data/oneforallsurvival/economy/mob_coins.json`, remplaçable par un datapack du monde et `/reload` ; fichier invalide : la table précédente reste, erreur au journal. Marchand ambulant et ses lamas à `none` (ruling, RP). |
| Origine | Marque positive `oneforallsurvival:wild` posée à `MobSpawnEvent.FinalizeSpawn` pour `NATURAL`, `CHUNK_GENERATION`, `STRUCTURE` (hors piglin né dans un portail), `PATROL`, `EVENT`, `TRIGGERED`. Tout le reste (générateur, œuf, distributeur, commande, seau, élevage, renfort, vex, jockey, slime divisé, mob créé sans événement) n'a pas de marque et ne donne rien. |
| Qui a tué | Un vrai joueur (coup, flèche, trident, TNT allumée, animal apprivoisé : son maître), ou un mob frappé par un joueur dans les 5 s (règle de Minecraft). Jamais une `FakePlayer`, **ni un joueur absent de la liste des joueurs du serveur** (passe finale de la tâche 3, commit `34b0ffd`), ni un spectateur. `doMobLoot` faux : rien. |
| Raids (Killian, 2026-11-15) | Payés, plafonnés à 100 cuivres par raid, tous joueurs confondus (`raidLootCap`). |
| Boss (Killian, 2026-11-15, puis ruling) | Le délai d'un butin par joueur, par type et par jour de paie (`bossLootPerDay` = 1) **ne vise que les boss sans marque sauvage** : Wither construit, dragon (le dragon de l'End est créé sans événement d'apparition, le premier comme un ranimé). Un boss né de lui-même (gardien ancien d'un monument, `STRUCTURE`) paie à chaque fois : il ne se refait pas en survie. Commit `34b0ffd`. |
| Compté quand | Dans `LivingDropsEvent`, priorité `LOWEST`, sans les événements annulés : les pièces sont ajoutées aux objets lâchés et comptées à `MOB_LOOT` dans le même appel. |
| Tableau de bord | Ligne « Butin des créatures » de `/eco stats` (existante depuis E1) ; `/eco loot` (niveau 2) : total, morts payantes, par palier, 10 premiers joueurs. Ligne de registre pour un butin d'au moins `mobLootJournalThreshold` (100) cuivres. |
| Sauvegarde | Aucune écriture forcée : marque dans les données persistantes de l'entité (écrites avec le chunk), compteurs dans `EconomyLedger` et `MobLootLimits` (`SavedData` marqués sales). |
| Film | Aucun (Killian : aucun film avant la fin du cahier) ; rien de visuel à ce lot, pas de capture. |

## Tests relancés

| Commande | Résultat lu |
|---|---|
| Nouveaux GameTests sur l'ancien `MobLoot` (`runGameTestServer`, 15:44 à 15:45) | « 3 required tests failed » : `unJoueurAbsentDeLaListeNeDonneRien` (« pièces au sol : 6 cuivres, attendu 3 » : le joueur hors liste était payé), `unBossSauvagePaieAChaqueFois` (« trois gardiens anciens sauvages le même jour : 500, attendu 1500 »), `leDragonNeDonneQuUneFois` (« le dragon n'est pas en phase de mort : Takeoff » : défaut de l'essai, dragon créé posé ; corrigé en le mettant en vol comme le fait `EndDragonFight`) |
| `./gradlew --offline test runGameTestServer`, avec les corrections (15:46 à 15:48) | « All 851 required tests passed » (15:48:39) ; JUnit : 451 tests, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml`) |
| `./gradlew --offline runGameTestServer -PwithPack` (15:48 à 15:50) | « All 851 required tests passed » (15:50:47) ; `unDeployeurDeCreateNeDonneRien` y joue un vrai déployeur de Create |

Nouveaux GameTests de cette tâche (`ButinGameTests`) :
- `unBossSauvagePaieAChaqueFois` : trois gardiens anciens marqués sauvages tués le même jour de paie → 1 500 cuivres ; deux Withers sans marque le même jour → 500.
- `unJoueurAbsentDeLaListeNeDonneRien` : un `ServerPlayer` jamais entré dans la liste des joueurs (pas une `FakePlayer`) tue un zombie sauvage : rien ; le vrai joueur témoin touche ses 3 cuivres.
- `leDragonNeDonneQuUneFois` : dragon en vol (`HOLDING_PATTERN`), coup fatal puis trois coups pendant la phase de mort : un seul `LivingDropsEvent`, une mort payante, 500 cuivres au sol et au registre.
- Tests à vide relevés par la revue de la tâche 3 : `unLoupApprivoiseNeDonneRien` et `unVeauNeDonneRienUneVacheSauvageDonne` vérifient maintenant que la bête est morte ; `desObjetsAnnulesNeComptentPas` et `doMobLootFauxNeDonneRien` ont un témoin qui paie (un second zombie non visé, et la règle rendue le temps d'un second zombie) : le total attendu est 3, pas 0.

**Mort du dragon** (lecture du bytecode de `EnderDragon`, Forge 47.4.28, `javap -c`) : `hurt(part, source, dégâts)` appelle `reallyHurt` → `Mob.hurt` → `die` (une seule fois, garde `dead` de `LivingEntity`), puis remet la vie à 1 et passe en phase `DYING`, où `hurt` rend faux ; `tickDeath` (200 ticks : expérience, portail, retrait) n'appelle ni `die` ni le lâcher des objets. Les pièces tombent où le dragon a reçu le coup fatal.

## Serveur dédié et vrai client

`runServer -PwithPack` puis `E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack -Pe2eHorloge=<minutes>` et `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`, sous le verrou de lancement. Client `Dev` avec le pack complet, fenêtre sur le second écran. L'étape est `E2eButin.java` (client) et `E2eButinServeur.java` (commandes de console réservées au RCON : table certaine, apparition d'un mob par `EntityType.spawn` avec le type d'apparition voulu, mobs sans IA, croissance, lecture des mobs, des pièces au sol, des origines et du registre), dans `src/devcontent/java/dev/oneforallsurvival/dev/`. `E2eButinServeur` relève aussi, à `FinalizeSpawn`, le type d'apparition de chaque mob (en mémoire) : c'est ce qui dit qu'un animal est né à la génération des chunks.

**Ordre.** Premier passage : monnaie (E1), commerce (E2), **butin (E4)**, trésor (E3) : la réécriture des références de relance d'E1 et d'E2 faite à la fin d'E3 compte déjà les pièces du butin, que Dev range dans son coffre-fort avant E3 (E3 part d'un Dev qui ne porte rien). Relance : commerce, monnaie, trésor, **butin en dernier** : ses nouvelles pièces viennent après tous les contrôles exacts des autres étapes. Le butin ne compare que la ligne « Butin des créatures » et `/eco loot`, jamais tout `/eco stats`.

**Aire.** Une boîte de pierre close et couverte (`fill … stone hollow`, intérieur 13 x 4 x 9 à y = 150), entités du dedans retirées avant, **à 640 blocs du point de départ** : l'étape du trésor met le monde en paisible, ce qui retire tout monstre d'un chunk qui tourne, même gardé (`Mob.checkDespawn`) ; à cette distance, l'aire est hors de la distance de simulation (10 chunks) pendant les autres étapes, et le zombie D gardé pour la relance survit. Difficulté facile et naissances coupées le temps de l'étape, puis rendues. Les zombies sont rendus sans IA (`e2ebutin figer`) pour que le joueur, en survie, ne soit pas attaqué ; les vaches gardent la leur (l'élevage en a besoin).

**Gestes de Dev** (survie, non opérateur) : œuf d'apparition posé d'un vrai clic droit sur le sol, blé donné d'un vrai clic droit sur chaque vache, coups d'épée de diamant (`gameMode.attack`, un coup chargé toutes les 0,9 s), ramassage des pièces en marchant dessus, « Déposer tout » au coffre-fort.

### Passages

| Passage | Connexion | Bilan | Contrôles |
|---|---|---|---|
| E1 + E2 + E4 + E3 seuls (`E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack`), monde neuf | 16:14:49 | « TOUT PASSE » à 16:22:13 | 155 `PASS` (dont 21 du butin), 0 `FAIL` ; « -Pe2eHorloge=4779 » |
| Relance ciblée de ce monde (`runServer -PwithPack -Pe2eHorloge=4779`, puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) : E2, E1, E3, puis E4 | 16:25:58 | « TOUT PASSE » à 16:27:11 | 36 `PASS` (dont 13 du butin), 0 `FAIL` |
| **Passage complet** (`runE2eClient -PwithPack` : lots 1 à 7, E1, E2, E4, E3, puis lot 5), monde neuf | 16:33:00 | « TOUT PASSE » à 16:48:21 | 346 `PASS` (dont 21 du butin), 0 `FAIL` ; « -Pe2eHorloge=4756 » |
| **Relance ciblée** de ce monde (`runServer -PwithPack -Pe2eHorloge=4756`, puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) | 16:50:50 | « TOUT PASSE » à 16:52:02 | 36 `PASS` (dont 13 du butin), 0 `FAIL` ; zombie D (4d5d1be7) relu et payé (6 cuivres au total), second veau : rien |

### Ce qui passe : premier passage

```
[e2e] PASS aire du butin en 96, 150, 741 (joueur parti de -4, 74, 101) ; Dev porte 0 cuivres, inventaire {minecraft:bread=4, minecraft:apple=1}
[e2e] PASS origines : ORIGINES animaux=29 sauvages=29 generation=29 generationMarques=29 exemple=chicken@75,64,103 releves=203 (tous les animaux nes a la generation des chunks sont marques)
[e2e] PASS aire close de pierre en 95 149 740 109 154 750 ; difficulte avant peaceful, naissances false ; table certaine (...) ; registre {total=0, morts=0, dev=0, ligne=[  Mob loot: 0 copper], ligneDev=[  Dev: 0 copper]}
[e2e] PASS Dev en survie (SURVIVAL), operateur = false, epee et ble en main
[e2e] PASS A zombie(dfb68139, sauvage=true, origine=NATURAL, bebe=false) ; D zombie(8d6fba04, sauvage=true, origine=NATURAL, bebe=false), garde=true ; vaches cow(07f41982, sauvage=true, origine=NATURAL, ...), cow(6905996c, sauvage=true, origine=NATURAL, ...) ; zombies sans IA (FIGER n=2)
[e2e] PASS B sorti du generateur en 1.1 s : zombie(40235338, sauvage=false, origine=SPAWNER, bebe=false) ; bloc retire
[e2e] PASS C ne de l'oeuf pose par Dev : zombie(eefbc437, sauvage=false, origine=SPAWN_EGG, bebe=false) ; oeufs restants 0
[e2e] PASS vache 1 nourrie de ble par un vrai clic droit : cow(07f41982, sauvage=true, ...), en amour (ble en main : 4 restant)
[e2e] PASS vache 2 nourrie de ble par un vrai clic droit : cow(6905996c, sauvage=true, ...), en amour (ble en main : 2 restant)
[e2e] PASS veau ne de l'elevage en 2.9 s : cow(0e43fe7c, sauvage=false, origine=aucune, bebe=true) (aucun evenement d'apparition, aucune marque) ; les parents restent sauvages
[e2e] PASS veau grandi : cow(0e43fe7c, sauvage=false, origine=aucune, bebe=false) ; toujours sans marque
[e2e] PASS a la mort de le zombie A (3 coups d'epee) : 1 pile(s) de pieces au sol (3 cuivres), 0 deja ramasse(s)
[e2e] PASS Dev ramasse 3 cuivres (3 coppers) ; « Butin des creatures » de /eco stats : « Mob loot: 3 coppers » (+3) ; /eco loot le nomme : « Dev: 3 coppers » ; Mob loot: 3 coppers created, 1 paying kills|By tier:|  common: 3 coppers|Top 10 earners:|  Dev: 3 coppers
[e2e] PASS le zombie B (generateur) tue par Dev a l'epee : aucune piece au sol (0), registre inchange (total 3, 1 mort(s) payante(s))
[e2e] PASS le zombie C (oeuf) tue par Dev a l'epee : aucune piece au sol (0), registre inchange (total 3, 1 mort(s) payante(s))
[e2e] PASS le veau grandi (elevage) tue par Dev a l'epee : aucune piece au sol (0), registre inchange (total 3, 1 mort(s) payante(s))
[e2e] PASS avant l'arret : e2e-butin.txt ecrit : zombie D zombie(8d6fba04, sauvage=true, origine=NATURAL, bebe=false) ; ligne « Mob loot: 3 coppers » ; /eco loot ...
[e2e] PASS Dev range 3 cuivres (le butin) : coffre-fort 10250 -> 10253
[e2e] PASS objets de l'etape retires (minecraft:diamond_sword x1, minecraft:wheat x2, minecraft:rotten_flesh x2, minecraft:beef x3) ; inventaire comme avant l'etape ; difficulte peaceful, naissances false ; Dev rentre, operateur
```

Le passage complet donne les mêmes contrôles (aire en 111, 150, 815 ; origines : 85 animaux chargés, 77 nés à la génération des chunks, tous marqués ; Dev range 3 cuivres, coffre-fort 10 250 -> 10 253). Il y lit « oeufs restants 1 » juste après la pose de l'œuf : l'inventaire du client n'avait pas encore reçu la mise à jour ; le rangement final n'a trouvé aucun œuf à retirer (consommé côté serveur).

Le client de l'essai est en anglais (langue du dossier `run-e2e`) ; les lignes de `/eco stats` et `/eco loot` sont comparées à celles que le serveur compose lui-même (`e2ebutin registre`, même composant, même langue que la console). L'étape du trésor qui suit passe entièrement : son départ voit le coffre-fort de Dev à 10 253 cuivres (butin compris) et un Dev qui ne porte rien, et ses références de relance d'E1 et d'E2 comptent les 3 cuivres.

### Ce qui passe : après un vrai arrêt du serveur (relance ciblée)

```
[e2e] PASS relance du butin : /eco loot identique (Mob loot: 3 coppers created, 1 paying kills|By tier:|  common: 3 coppers|Top 10 earners:|  Dev: 3 coppers) ; ligne « Mob loot: 3 coppers » relue dans /eco stats
[e2e] PASS relance : aire relue en 96, 150, 741 ; CERTAIN on common=Tier[id=common, rolls=[Roll[coin=COPPER, chance=1.0, min=3, max=3]], anyOrigin=false] ; difficulte avant easy
[e2e] PASS relance : zombie D relu du disque : zombie(8d6fba04, sauvage=true, origine=aucune, bebe=false), garde=true
[e2e] PASS a la mort de le zombie D (relu) (3 coups d'epee) : 1 pile(s) de pieces au sol (3 cuivres), 0 deja ramasse(s)
[e2e] PASS Dev ramasse 3 cuivres (3 coppers) ; « Butin des creatures » de /eco stats : « Mob loot: 6 coppers » (+3) ; /eco loot le nomme : « Dev: 6 coppers » ; Mob loot: 6 coppers created, 2 paying kills|...
[e2e] PASS parents : cow(07f41982, sauvage=true, origine=aucune, bebe=false), cow(6905996c, sauvage=true, origine=aucune, bebe=false)
[e2e] PASS veau ne de l'elevage en 2.9 s : cow(f25f34ee, sauvage=false, origine=aucune, bebe=true) (aucun evenement d'apparition, aucune marque) ; les parents restent sauvages
[e2e] PASS le second veau grandi (ne apres la relance) tue par Dev a l'epee : aucune piece au sol (0), registre inchange (total 6, 2 mort(s) payante(s))
[e2e] PASS relance du butin finie : CERTAIN off ... ; retires minecraft:diamond_sword x1, minecraft:wheat x2, minecraft:rotten_flesh x2 ; Dev garde les 3 cuivres du butin (son coffre-fort a ete range par l'etape du tresor)
```

Le zombie D porte encore sa marque sauvage après l'arrêt : elle a été relue du chunk (données persistantes de l'entité). Son « origine » vaut `aucune` à la relance parce que le relevé des types d'apparition d'`E2eButinServeur` vit en mémoire seulement. La table certaine a dû être remise (`e2ebutin certain on`) : l'override d'essai ne survit pas à l'arrêt, par conception. Les étapes E2, E1 et E3 de la relance ont toutes passé avant le butin, avec les références réécrites par E3 (qui comptaient déjà les 3 cuivres du butin rangés au coffre-fort).

### Incidents de l'essai

- Premier lancement (15:59) : le serveur s'est arrêté sur `ClassNotFoundException: dev.oneforallsurvival.economy.HeldGoods` 30 s après son démarrage, pendant que Gradle compilait pour le client (un fichier modifié après la compilation du serveur) ; le client n'a pas pu se connecter. Le script de l'essai compile désormais avant de lancer le serveur. Défaut du banc, pas du jeu.
- Second lancement (16:05) : `e2ebutin apparaitre minecraft:zombie …` refusé par la console (« Expected whitespace to end one argument » : un argument `word` ne prend pas de « : ») ; corrigé par un argument d'identifiant (`ResourceLocationArgument`). Les 102 contrôles d'E1 et d'E2 avaient passé.
- Le passage complet et sa relance ont passé au premier essai.
- Le générateur d'essai avait d'abord des règles d'apparition sur mesure (`custom_spawn_rules`) ; elles ont été retirées avant le passage qui fait foi, la boîte close étant noire (le zombie est sorti en 1,1 s).

« [souris] rendue » et « [fenetre] second écran (2600, 40) » sont lus dans le journal de chaque client ; aucun client ni serveur n'est resté ouvert (le serveur s'arrête par `/stop`) ; le verrou est vidé après chaque lancement. Les anciens mondes du serveur sont rangés hors du dépôt, dans le dossier temporaire de la session, ainsi que les journaux.

## Chemins d'apparition (relevés à la tâche 2)

Source : jar `forge-1.20.1-47.4.28_mapped_official_1.20.1.jar`, classes décompilées, et `coremods/finalize_spawn_targets.json` du jar universel. Forge ne patche pas `Mob.finalizeSpawn` : un coremod remplace l'appel par `ForgeEventFactory.onFinalizeSpawn` dans 22 classes seulement ; `BaseSpawner` publie l'événement lui-même (`SPAWNER`).

| Marquent (sauvage) | Ne marquent pas (rien au butin) |
|---|---|
| Apparition naturelle, structures comprises (`NATURAL`) ; phantoms ; chats des villages | Générateur (`SPAWNER`) ; œuf sur le sol (`SPAWN_EGG`) ou sur un mob (aucun événement) ; distributeur ; `/summon` ; seau |
| Génération des chunks (`CHUNK_GENERATION`) : **prouvée sur serveur dédié seulement** (le monde des GameTests n'a aucune apparition) | Renfort de zombie, vex, golem de fer des villageois ; jockeys poulet et arpenteur |
| Patrouille (`PATROL`) ; raid et siège de zombies (`EVENT`) ; marchand ambulant (`EVENT`, mais palier `none`) | Golems de citrouille, Wither, dragon (créés sans événement ; les boss paient par `any_origin`) |
| Warden, piège des chevaux squelettes (`TRIGGERED`) | Slime divisé, poule d'un œuf lancé, tortue, têtard, abeilles de ruche |
| Gardiens anciens, cabane de sorcière, ruines sous-marines, manoir, gabarits de structure (`STRUCTURE`) | Petit d'élevage (marque effacée en plus par `BabyEntitySpawnEvent`) |
| Conversions avec `LivingConversionEvent.Post` : la marque de l'ancien mob suit (zombie → noyé…) | Piglin zombifié né dans un bloc de portail (marque effacée) ; têtard → grenouille ; squelette → vagabond ; champimeuh tondue (aucun `Post`) |

Les chemins naturels qui ne passent pas par l'événement donnent « rien » : c'est le côté sûr pour l'argent (un squelette sauvage devenu vagabond dans la neige poudreuse ne paie plus).

## Fermes et équilibrage

- **Fermes semi-automatiques permises par la règle retenue** (« tué par un joueur », celle de Minecraft) : à équilibrer en jeu, avec `/eco loot` pour les voir venir.
  - Des **loups apprivoisés** qui tuent seuls paient leur maître (crédit du maître d'un `OwnableEntity`).
  - Les **épines** d'une armure tuent au nom du joueur : une ferme où le joueur reste immobile paie.
  - Un **coup du joueur, puis une ferme qui achève** (chute, lave, cactus) **dans les 5 s**, paie ce joueur.
  - Une **boule de neige ou un bouchon de canne à pêche** (0 dégât) ouvre aussi les 5 s : un joueur qui en lance dans la chambre de mort d'une ferme automatique est payé. **Déduit des sources de Minecraft (`lastHurtByPlayer` posé même à 0 dégât), sans essai en jeu.**
  - Les **fermes actives** où le joueur achève chaque mob lui-même (salle de chute vers un point de frappe) sont permises, mais **bornées par le plafond horaire par joueur** (200 cuivres sur 60 minutes glissantes, `mobLootHourlyCap`, décision de Killian du 2026-11-15 ; boss exemptés).
  - Ce qui ne paie pas : générateur, déployeur de Create et tout faux joueur, mob tué sans joueur, raids au-delà de 100 cuivres, Wither et dragon au-delà d'un par jour et par joueur.
- **Équilibrage** : rien n'a été vécu en partie (taux de mort par heure, part du butin dans la masse en circulation). Les chances et montants de départ sont ceux du cahier ; l'équipe les change par datapack et `/reload`.

## Non prouvé

- **Chances de la table livrée sur serveur dédié** : l'essai de bout en bout passe une table certaine ; la table livrée n'est tirée qu'en GameTest (`centMortsComptesJustes`, 200 morts, graine fixe) et en JUnit (`tiragesDeDepartSurDixMille`).
- **Mobs nés avant le lot** : ils n'ont pas de marque d'origine et ne donnent rien (sauf les boss). Prouvé en GameTest par un mob sans marque, pas sur un vrai ancien monde. Le serveur n'est pas encore ouvert.
- **Mob d'un autre mod** : aucun mob moddé dans le pack d'aujourd'hui ; un futur mob suit sa catégorie (monstre : courant, créature : animal sauvage). Seul le chemin par catégorie est testé, avec des mobs vanilla.
- **Pièce ajoutée par un datapack ou CraftTweaker à une table de butin vanilla** : elle tomberait sans passer par le registre (aucune source `MOB_LOOT` comptée) ; **non gardé**, comme les recettes l'étaient avant `CoinRecipeGuard`. À ne pas faire, ou à garder dans un lot à venir.
- **Autre mod qui annulerait les objets après nous** : un écouteur `LOWEST` d'un autre mod, abonné après le nôtre, qui annulerait `LivingDropsEvent` **ou viderait la liste des objets lâchés** laisserait le butin compté sans pièce lâchée (le registre gonfle, aucun argent n'est créé). Aucun cas connu dans le pack.
- **Dragon de l'End** : sa mort est prouvée en GameTest (un seul événement, 500 au sol) et par le bytecode, pas dans un vrai combat. Le butin tombe où le coup fatal a été porté ; **au-dessus du vide, il est compté puis détruit** (cause « sous le monde », `CoinDropEntity`) : aucune création d'argent, une perte pour le joueur.
- **Raids et boss** : plafond et délai prouvés en GameTest seulement (`unRaidPlafonneSonButin`, `unBossUneFoisParJourEtParJoueur`, `unBossSauvagePaieAChaqueFois`), pas en vrai raid ni en vrai combat de boss. Le délai des boss ne se rouvre plus quand le jour de paie recule (horloge, `payHour` ou `payTimeZone` changés) : corrigé à la passe finale.
- **Limite de Minecraft, pièce ramassée puis arrêt brutal** : un joueur ramasse des pièces du butin, se déconnecte (son playerdata est écrit, avec les pièces), puis le serveur s'arrête brutalement **avant la sauvegarde du monde** : la création n'est pas sur le disque (registre d'avant), la pièce si. Même classe de fenêtre que dans [`lot-e2.md`](lot-e2.md), « Seconde passe » ; elle dure au plus jusqu'à la sauvegarde automatique suivante. **Aucune écriture forcée**, par décision.
- **Arrêt brutal** : l'essai fait un vrai `/stop`, pas un arrêt brutal du processus.
- **Relance complète du banc** : toujours en échec sur la couveuse du lot 5 (défaut du banc, ouvert dans `tasks/todo.md`) ; la relance ciblée de l'économie est faite à la place.
- **Jar livré** : jamais lancé hors du développement.

## Passe finale

Relevés du 2026-11-15, après la revue finale (`4ae4588..5834012`). Corrections et ajout de la décision de Killian du jour.

| Point | Correction | Preuve |
|---|---|---|
| Délai des boss rouvert quand le jour de paie recule | `MobLootLimits` : une entrée plus récente n'est jamais remplacée par une plus ancienne, un jour antérieur au dernier noté compte comme épuisé | JUnit `unJourDePaieQuiReculeNeRouvrePasLeButinDuBoss` : rouge sur chacune des deux mutations (retour de l'ancien `bossCount`, retrait de la garde d'`addBoss`), vert avec la correction |
| Identifiant ou tag inconnu accepté en silence | Avertissement au journal à chaque chargement des données (`TagsUpdatedEvent`, cause `SERVER_DATA_LOAD`), une ligne par règle sans effet ; la table reste acceptée | JUnit `unIdentifiantOuUnTagInconnuEstSignale` ; GameTest `uneRegleSansEffetEstSignaleeAuJournal` (vrai `/reload`, rouge quand l'avertissement est coupé) |
| Tests « rien » sans preuve de mort | `!isAlive()` vérifié dans `unZombieSansMarqueNeDonneRien`, `unZombieDeGenerateurNeDonneRien`, `unFauxJoueurNeDonneRien`, `laLaveSansJoueurNeDonneRien` | GameTests verts |
| Datapack d'essai resté en place | `butin_invalide` supprimé au début du test ; `@AfterBatch` qui retire et recharge pour les deux datapacks d'essai (un échec en route laissait la table à 1 palier au test suivant, observé pendant la mutation) | GameTests verts |
| Documentation | liste d'objets vidée nommée, boule de neige et canne à pêche, jour de paie qui recule | ce document |

Non fait : `lootCreated` daté par `EconomyClock` (optionnel, esthétique, seule la date des lignes du registre est touchée).

### Plafond horaire par joueur (décision de Killian, 2026-11-15)

200 cuivres de butin au plus par joueur sur 60 minutes glissantes (`mobLootHourlyCap`, 0 = aucun plafond), horloge `EconomyClock`, gains notés dans `MobLootLimits` (`SavedData` marqué sale, aucune écriture forcée). Paliers courant, dangereux, animal sauvage et ceux de l'équipe qui n'ignorent pas l'origine, raids compris (plafond par raid en plus : le plus petit des deux restes l'emporte) ; les paliers qui ignorent l'origine (boss) en sont exemptés. Un butin qui dépasse est réduit à ce qui reste (décomposé en pièces), puis plus rien ; le registre compte ce qui est lâché. `/eco loot` lancé par un joueur ajoute ses gains de l'heure glissante. Fenêtre : un gain sort exactement 60 minutes après (`instant <= maintenant - 60 min`).

- JUnit : `lePlafondHoraireTronqueEtSeRouvreApresSoixanteMinutes`, `lePlafondHoraireSurvitALaSauvegarde`.
- GameTests (horloge injectée) : `lePlafondHoraireTronqueEtSeRouvreApresUneHeure` (169 sous le plafond, tronqué à 200, rien au plafond, rien à 59 minutes, 3 de plus à 60 minutes, pièces au sol égales au registre), `lesBossNeSontPasLimitesParLePlafondHoraire` (Wither à 500 au plafond).
- Rouge vu : plafond coupé (`hourly = false`) → « butin tronqué : 209, attendu 200 ».
- Équilibre : 200 cuivres par heure = 20 argents ; la table livrée donne en moyenne 0,67 cuivre par mob courant (estimation), le plafond ne se touche qu'avec une ferme active.

### Relevés de la passe finale

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test runGameTestServer` | « All 854 required tests passed » (17:22:03) ; JUnit : 455 tests, 0 échec, 0 erreur |
| `./gradlew --offline runGameTestServer -PwithPack` | « All 854 required tests passed » (17:24:41) |
| Mutation (plafond coupé + avertissement coupé) | 3 échecs lus : `uneRegleSansEffetEstSignaleeAuJournal`, `lePlafondHoraireTronqueEtSeRouvreApresUneHeure`, et `laTableLivreeSeChargeAvecLePack` (conséquence : le datapack d'essai resté en place) |
| E2E ciblé (`E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack`, monde neuf) | « TOUT PASSE » à 17:35:56, 155 `PASS`, 0 `FAIL` ; `-Pe2eHorloge=4705` |
| Relance (`runServer -PwithPack -Pe2eHorloge=4705`, puis `runE2eRelance`) | « TOUT PASSE » à 17:39:07, 36 `PASS`, 0 `FAIL` |

L'essai de bout en bout ne gagne que 3 puis 6 cuivres d'un coup : bien sous le plafond, aucune adaptation. Le plafond n'est pas vécu en partie, il n'est prouvé qu'en JUnit et GameTest.
