# Lot E5 : quêtes journalières et hebdomadaires — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chaque joueur a 3 quêtes journalières et 5 hebdomadaires, tirées pour lui dans une réserve de modèles en données, renouvelées sur le calendrier réel, qui avancent par de vrais gestes (casser, poser, tuer, récolter, pêcher, cuisiner, conserver, élever, métier) et paient en pièces, à réclamer dans un écran à touche réglable, sous un plafond de 1 000 cuivres par semaine et par joueur. Chaque pièce versée est comptée à la source « quêtes » du registre.

**Architecture:** Un nouveau paquet `economy/quest/`, posé sur le socle d'E1 (pièces, registre, `InventoryFit`, `PlayerCoins`), le calendrier d'E3 (`EconomyCalendar`, `EconomyClock`) et les règles du butin d'E4 (`MobLoot.creditedPlayer`, `MobOrigin.isWild`).
- **Modèles et tirage** (règles pures) : `QuestTemplates` lit `data/oneforallsurvival/economy/quests.json` (datapack et `/reload`, comme `mob_coins.json`), fusionné avec les modèles ajoutés par commande ; `QuestGenerator` tire une série sans doublon, pondérée par rôle.
- **État du joueur** : `QuestBook`, dans les données persistantes du joueur (`Player.PERSISTED_NBT_TAG`, écrites dans son playerdata avec son inventaire, comme `TradeEscrow`) ; `QuestService` renouvelle, relance, réclame, et compte au registre (`MoneySource.QUEST`, qui existe déjà avec son libellé et sa ligne de `/eco stats`).
- **Progression** : `QuestProgress` reçoit les gestes (écouteurs Forge, crochets dans la réparation, la marmite, le séchoir, et un mixin sur le Cooking Pot de Farmer's Delight) ; `PlacedBlocks`, une capacité de chunk, marque les blocs posés (écrite avec le chunk, donc avec le bloc).
- **Équipe** : `QuestBoard` (`SavedData`) garde les modèles ajoutés par commande et les quêtes d'événement.
- **Écran** : touche réglable (J), `QuestScreen` lu sur une copie client envoyée par le serveur, notification par un toast.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, MixinGradle (déjà en place), Gson, JUnit 5, GameTests, pack (`-PwithPack`). Farmer's Delight 1.20.1-1.3.4 (déjà en `implementation`). Deux paquets réseau nouveaux sur `BenchNetwork` (protocole `7` → `8`).

**Spec:** `docs/superpowers/specs/archive/2026-11-08-economie-design.md`. Sections utiles :
- section 2 (« Ce qui serait un échec » : créer de l'argent par une **quête répétée en boucle** ; des quêtes qui deviennent **une corvée quotidienne obligatoire** ; règle des quatre sources, les quêtes en sont une, comptée à part) ;
- section 3 (données et config réglables sans recompiler ; montants en cuivres ; échéances au calendrier réel, fuseau réglable) ;
- feature **79** en entier (3 + 5 quêtes ; renouvellement chaque jour et chaque lundi à heure fixe ; expiration sans pénalité ; tirage pondéré de modèles en données ; catégorie, cible facultative, fourchette, récompense de base ; pas deux fois le même modèle par série ; hebdomadaire ≈ 5 × journalière ; pondération par rôle sans obligation ; une relance par jour ; écran à touche réglable ; notification discrète ; récompense à réclamer, vers la bourse ou l'inventaire ; pièces seulement ; source comptée ; **plafond de 1 000 cuivres par semaine et par joueur**, réglable, et l'écran le dit ; catégories demandées et proposées ; garde-fous ; suivi des blocs posés borné ; modèles et quêtes d'événement par commande) et **77** (total créé par source) ;
- section 7 (lot E5 = 79 ; « les quêtes de métier arrivent avec chaque métier ») ;
- section 8 (« trois journalières différentes sont générées, et une relance en donne une autre » ; « un bloc posé puis cassé ne fait avancer aucune quête » ; « une quête de métier n'est pas proposée à un autre rôle » ; scénario d'abus « boucle poser, casser » ; **scénario des quêtes** : se connecter, lire ses 3 + 5 quêtes, en finir une, réclamer, relancer le serveur, retrouver sa progression, changer la date et voir le renouvellement) ;
- section 9 (quêtes en pièces seulement ; calendrier réel, une relance par jour, expiration sans pénalité ; quêtes de métier au seul rôle concerné).

Modèles de forme et ce qui est livré : `docs/superpowers/plans/archive/2026-11-12-lot-e1-monnaie.md` à `archive/2026-11-15-lot-e4-butin.md`, et leurs preuves `docs/verifications/lot-e1.md` à `lot-e4.md`.

## Global Constraints

Les contraintes globales du lot 6, d'E1, d'E2, d'E3 et d'E4 restent valables telles quelles (sections « Global Constraints » des plans archivés), notamment : textes en `fr_fr.json` avec repli `en_us.json`, accents corrects, jamais « SolarTown » ; lots de 8 GameTests au plus ; `JoueurDeTest` pour tout joueur d'essai ; montants en cuivres (`long`) affichés par `CoinMath.format` ; une commande de l'équipe exige le niveau 2 ; un client n'est jamais cru sur parole ; aucune classe cliente atteignable du serveur.

En plus :
- **Une seule source créée par ce lot : `MoneySource.QUEST`.** Toute pièce que le lot fait entrer chez un joueur est comptée à cette source **dans le même appel** qui la fait entrer ; rien d'autre ne crée d'argent. Une réclamation est **atomique** : tout entre (contrôle `InventoryFit.fits` avant de rien bouger) ou rien ne bouge.
- **Aucune écriture forcée hors de la sauvegarde du monde** (leçons du 2026-11-14). L'état des quêtes du joueur, son compteur de plafond et ses récompenses en attente vivent dans `Player.PERSISTED_NBT_TAG` (même fichier que l'inventaire où arrivent les pièces) ; la marque « posé » vit dans une capacité du chunk (même fichier que le bloc) ; `QuestBoard` et les compteurs de `EconomyLedger` sont des `SavedData` marqués sales.
- **Horloge et aléatoire injectables** : tout ce qui dépend du jour prend `long now` (lu par `EconomyClock.now()` à l'appelant) ; tout tirage reçoit un `RandomSource`. Les tests passent une horloge fixe (`EconomyClock.setClock`, rendue par `reset()` en `finally` ou `@AfterBatch`), une réserve « d'essai » par `QuestTemplates.setForTest`, rendue à `null` en fin de test.
- **Services testables sans état global** : `QuestBook` (règles pures sur un état sérialisable), `QuestGenerator` et `QuestService.claim/reroll/refresh` prennent réserve, registre, horloge en paramètre ; les GameTests passent un registre détaché (`EconomyLedger.load(new CompoundTag())`).
- **Le monde des GameTests est plat, au biome `the_void`, sans apparition de mobs** (leçon du 2026-11-11) : les mobs « sauvages » s'y font par `MobOrigin.markWild` ou `EntityType.spawn(…, NATURAL)`. Un mob gardé plus d'un tick reçoit `setPersistenceRequired()` (leçon du 2026-11-15). Comptes et rangements d'entités dans la boîte du gabarit (leçon du 2026-11-15). Un GameTest qui règle l'horloge de l'économie ou une règle du monde a un lot à lui avec `@BeforeBatch`/`@AfterBatch` ; un GameTest qui pose un datapack le retire aussi dans un `@AfterBatch` (leçon du 2026-11-15).
- **Preuve des tests** : `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack` ; lire « All N required tests passed » dans `run-gametest/logs/latest.log` et le rapport JUnit (`build/test-results/test/*.xml`), jamais le seul code de sortie.
- **Tout lancement de Minecraft** (GameTests, client, serveur, captures, E2E) suit la procédure du verrou : section 11 de `C:/Users/killi/.claude/CLAUDE.md` (points 1 à 8) et la note `D:/BDC/Minecraft/Deux sessions d'agents qui lancent Minecraft sur la même machine passent par un fichier de verrou et une file d'attente.md`. AXIONENGINE s'en sert souvent.
  - Lire `C:\Users\killi\.claude\lancement-minecraft.txt` (un fichier qui ne contient que des blancs ou un BOM est vide) ; rempli : s'inscrire en fin de `lancement-minecraft-attente.txt` (`<projet> — <heure>`), surveiller en arrière-plan, jamais d'attente au premier plan ; vide : ne le prendre que si la file est vide ou si l'on est en tête.
  - **Relire juste avant d'écrire, et refuser s'il est pris** : la prise passe par un script qui relit le verrou et la file et écrit dans la même commande, et refuse si le verrou n'est pas vide (`[ -s ... ] && exit 1` ou équivalent), jamais un `printf >` nu enchaîné à un lancement (leçon du 2026-11-15). Écrire sa ligne (`<projet> — <ce qu'on lance> — <heure>`), retirer sa ligne de la file, lancer.
  - À la fin, réussite ou échec, **vider** le verrou sans le supprimer et **sans BOM** (`: > fichier` en bash, `[IO.File]::WriteAllText($chemin, '')` en PowerShell ; jamais `Set-Content -Encoding utf8`). File non vide à la libération : se remettre en fin de file.
  - **Se parler entre sessions** (point 8) : en s'inscrivant derrière une autre session, lui écrire qu'on attend, ce qu'on lancera et pour combien de temps, et lui demander son heure de fin ; en prenant le verrou, prévenir celles qui attendent ; en le libérant, prévenir la suivante. Un désaccord se règle par message, jamais en forçant le verrou ; le fichier reste la règle si un message n'arrive pas. Un sous-agent qui ne peut pas écrire à une autre session le signale à la session qui l'a lancé.
  - Fenêtres d'essai sur le second écran (`dev/FenetreSecondEcran` ; propriété `oneforallsurvival.fenetre.secondEcran`) ; lire « [fenetre] second écran » dans le journal.
  - Tout client passe une propriété JVM `oneforallsurvival.*` (souris rendue par `dev/SourisLibre`) ; lire « [souris] rendue » dans son journal. **Aucun client laissé ouvert.**
  - N'arrêter que ses propres PID (notés au lancement) ; **ne jamais toucher une JVM dont la ligne de commande contient `AXIONENGINE`**. Un serveur resté ouvert s'arrête par RCON (`stop`), jamais par un kill.
  - Ne jamais modifier `run/options.txt`. Pas de Prism. Seuls les téléchargements faits par Gradle lui-même sont permis.
  - Ces règles sont recopiées dans le brief de tout sous-agent qui lance Minecraft.
- **Pas de film** (décision de Killian : aucun film avant la fin du cahier).
- **Captures de l'écran de quêtes** par `runDemoVisuels` (`DemoVisuels`), **ouvertes et regardées** avant d'être citées : contenu le plus long et le plus court (leçons du 2026-11-13 et du 2026-11-14), dans la plus petite interface automatique (320 × 240) et dans une grande.
- **E2E sur serveur dédié avec le pack**, passage puis relance ciblée de l'économie (`E2E_MONNAIE_SEULE`) avec horloge décalée (`-Pe2eHorloge`), puis passage complet.
- **Git** : travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, par `git commit -- <chemins>` (jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/` ; jamais `git add -A`, `git mv` ni `git rm` : leçons du 2026-11-08 et du 2026-11-15), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Push refusé : `git pull --rebase` puis push.

## Review Focus

1. **Boucle « poser, casser » et blocs qui ne viennent pas du monde** : bloc posé puis cassé (casse : rien ; pose : la pose est retirée si le même joueur le casse dans les 60 s), un seul bloc posé et cassé 64 fois (pose nette : 0), pierre ou pierre taillée formée par la lave et l'eau (générateur de pierre), bloc poussé par un piston, bloc posé ou cassé par un déployeur de Create (faux joueur), joueur en créatif. Attendu : aucune progression. Testé en tâche 3 (`unBlocPosePuisCasseNAvanceRien`, `unSeulBlocRecycleNeFaitPas64Poses`, `laPierreDUnGenerateurNeComptePas`, `unBlocPousseParUnPistonNeComptePas`, `leCreatifNAvanceRien`) et avec le pack (`unDeployeurDeCreateNAvanceRien`).
2. **Horloge qui recule ou saute** : horloge reculée d'un jour (série gardée, relance déjà prise toujours prise, plafond non remis à zéro), avance de dix jours (un seul renouvellement, pas dix), passage d'un lundi, changement d'heure d'été, heure de renouvellement dans l'heure sautée. Testé en tâche 2 (`QuestBookTest`).
3. **Réclamation** : deux paquets de réclamation dans le même tick, réclamation d'une quête d'une série déjà renouvelée (clic périmé), inventaire et bourses pleins, plafond partiel, plafond atteint, joueur en créatif. Attendu : jamais deux paiements, jamais de pièce hors registre, rien ne bouge si tout n'entre pas. Testé en tâche 2 (`QuestClaimGameTests`) et tâche 5 (`unClicPerimeNeReclameRien`).
4. **Quête de métier et rôle** : un citoyen ne reçoit jamais une quête de métier, ni par tirage ni par relance ; un ingénieur la reçoit plus souvent sans y être obligé ; un joueur qui change de rôle garde sa série. Testé en tâche 1 (`uneQueteDeMetierResteAuRole`, `lAgriculteurRecoitPlusDeRecoltes`).
5. **Données de l'équipe invalides** : fichier mal formé, catégorie inconnue, catégorie sans moyen d'être comptée (`deliver`), action de métier inconnue, `min > max`, récompense nulle, identifiant en double, modèle de commande qui reprend un identifiant du fichier, quête d'événement à cible inconnue. Attendu : la réserve précédente reste, une erreur qui dit pourquoi, jamais de plantage ni de réserve vide silencieuse. Testé en tâche 1 (`QuestTemplatesTest`) et tâche 4 (`QuestBoardGameTests`).

## Décisions du contrôleur (inscrites telles quelles)

| Sujet | Décision |
|---|---|
| Quêtes (79) | 3 journalières, 5 hebdomadaires, renouvellement à heure fixe sur le calendrier réel (chaque jour ; chaque lundi), expiration sans pénalité, une relance par jour. |
| Récompenses | Pièces seulement, source `QUEST`, plafond de 1 000 cuivres par semaine et par joueur (réglable). |
| Garde-fous | Bloc posé par un joueur : pas de casse ; posé puis cassé aussitôt : pas de pose ; mobs selon les règles du butin. |
| Sauvegarde | Aucune écriture forcée hors de la sauvegarde du monde ; une donnée qui suit un inventaire vit dans le même fichier que lui. |
| Lancements | Procédure du verrou (Global Constraints). |
| Film | Aucun. Captures de l'écran de quêtes, ouvertes. E2E sur serveur dédié avec le pack, relance ciblée économie avec horloge décalée. |

## Choix de l'auteur du plan (à valider par Killian)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Réserve de départ | 22 modèles (tableau plus bas) : casse (pierre, bûches, sable), minerais (charbon, cuivre, fer, or), pose (planches, briques), combat (tout monstre, zombies, squelettes, araignées), récolte (blé, carottes, pommes de terre), pêche, cuisine (tout aliment cuit, plats de Farmer's Delight), conserve, élevage, métier (réparer des machines, ingénieur seul). Aucun modèle de livraison. | Couvre toutes les catégories comptables aujourd'hui ; aucune cible introuvable dans le pack |
| Formule | `récompense(q) = max(1, arrondi(base × q / min))` ; la quantité est tirée uniformément de `min` à `max` (au plus 2 × `min` dans la réserve livrée, donc la récompense va de `base` à 2 × `base`). Hebdomadaire : quantité et récompense × `weeklyFactor` (5 par défaut, réglable par modèle). Base de 10 à 25 cuivres : une journalière rapporte ~10 à 50, en moyenne ~20 ; une hebdomadaire ~50 à 250, en moyenne ~100. | Tout faire rapporte ~875 cuivres par semaine en moyenne (21 × 19 + 5 × 95) : le plafond de 1 000 ne mord que pour celui qui fait tout, les journalières seules donnent ~400 (moins que la somme de départ de 500), et les métiers restent la vraie source |
| Où vivent les quêtes | `QuestBook` dans `Player.PERSISTED_NBT_TAG` du joueur (clé `oneforallsurvival:quests`) : séries, progression, relance, compteur de plafond de la semaine, récompenses en attente. Jamais en `SavedData`. | Leçon d'E2 : la réclamation fait entrer des pièces dans l'inventaire et marque la quête payée ; les deux doivent être dans le même fichier, sinon un arrêt brutal entre deux écritures paie deux fois. `PERSISTED_NBT_TAG` survit à la mort (copié par Forge), comme `TradeEscrow`. Le registre (`EconomyLedger`) reste un `SavedData` : même limite de Minecraft que le butin d'E4 (création comptée et pièce sur le disque à deux instants), à écrire telle quelle |
| Quand naissent les séries | À la demande (connexion, ouverture de l'écran, tic de contrôle toutes les 20 ticks pour les joueurs en ligne) : `QuestService.refresh(player, now)` compare les clés de période. Rien pour un joueur hors ligne. Graine du tirage : `hash(uuid, série, clé de période, numéro de relance)`. | Pas de travail pour les absents ; tirage reproductible, testable |
| Heure de renouvellement | `questResetHour` = 6 (0 à 23), fuseau `payTimeZone` d'E3. Clé journalière = `EconomyCalendar.payDay(now, zone, questResetHour)` ; clé hebdomadaire = clé journalière ramenée au lundi (`previousOrSame(MONDAY)`). | À 6 h, celui qui joue après minuit garde les quêtes de sa soirée ; heure d'été traitée par `EconomyCalendar` |
| Horloge qui recule ou saute | Une série n'est remplacée que si la clé présente est **strictement après** la clé gardée ; le plafond n'est remis à zéro que si la semaine présente est strictement après celle du compteur ; la relance n'est rendue que si le jour présent est strictement après le jour de la dernière relance. Horloge reculée : tout est gardé tel quel, en pause, jusqu'à ce que l'heure rattrape (logique d'E3, `daysToClose` vide). Saut en avant : un seul renouvellement, sans rattrapage (une quête manquée n'est pas due). | Reculer l'horloge ne rend jamais une relance, un plafond ni une série ; avancer ne crée qu'une série |
| Pas deux fois le même modèle | Dans une série, tirage pondéré **sans remise**. Une relance tire hors des modèles de la série (et hors de celui qu'elle remplace). Journalières et hebdomadaires sont deux séries : un même modèle peut être dans les deux. Réserve plus petite que la série : on en tire autant qu'il y en a, avertissement au journal. | Lettre du cahier ; la réserve livrée a 21 modèles généraux, toujours assez |
| Pondération par rôle | Champ `role_weights` (multiplicateur ≥ 0 par identifiant de rôle) : agriculteur × 3 sur la récolte, × 2 sur la cuisine, la conserve et l'élevage. Jamais d'obligation : les autres modèles restent tirables. | « plus souvent, sans jamais l'y obliger » |
| Quêtes de métier | Catégorie `job`, cible obligatoire = une **action** de `QuestJobs` liée à un rôle ; le modèle n'est tiré que pour ce rôle (et n'a pas besoin de `roles`). Action livrée : `oneforallsurvival:repair_machine` (ingénieur, comptée quand `RepairActions.repair` rend `REPAIRED`). Point d'extension : `QuestJobs.register(id, roleId)` et `QuestProgress.job(player, id, count)`, que les lots du médecin (soigner) et du policier (contravention) appelleront avec leurs modèles. | Seul l'ingénieur a un geste de métier codé aujourd'hui ; le cahier dit « les quêtes de métier arrivent avec chaque métier » |
| Livraison | Pas de grenier communal dans le code (cherché : rien) : la catégorie `deliver` existe dans `QuestCategory` avec `counted = false`, et un modèle `deliver` est **refusé** au chargement (« aucun grenier communal »). Le lot du grenier passera `counted` à vrai et appellera `QuestProgress.deliver(player, stack)`. | Une quête impossible à finir serait une corvée ; refuser vaut mieux qu'un modèle silencieusement mort |
| Comment chaque catégorie compte | Seulement un vrai `ServerPlayer` de la liste des joueurs, ni `FakePlayer` ni spectateur ni créatif. **Casse** : `BlockEvent.BreakEvent` (`LOWEST`, non annulé), bloc **non marqué posé**, et `canHarvestBlock` vrai (le bloc lâcherait quelque chose). **Pose** : `EntityPlaceEvent` (`LOWEST`, non annulé). **Combat** : `LivingDropsEvent` (`LOWEST`, `receiveCanceled = true` : un autre mod qui annule les objets n'annule pas la mort), joueur de `MobLoot.creditedPlayer`, mob `MobOrigin.isWild` ; sans cible : catégorie `MONSTER`. **Récolte** : casse d'un `CropBlock` à `isMaxAge`. **Pêche** : `ItemFishedEvent`, objets du tag cible (sans cible : tout objet pêché). **Élevage** : `BabyEntitySpawnEvent`, `getCausedByPlayer()`. **Cuisine** : aliment sorti d'un four (`ItemSmeltedEvent` avec un `FurnaceMenu` ouvert), de la marmite de cantine (case de sortie de `CanteenPotMenu`), du Cooking Pot de Farmer's Delight (mixin sur `CookingPotResultSlot.checkTakeAchievements`, lu au bytecode : aucun événement de Forge n'y est tiré). **Conserve** : sortie du séchoir (`DryerMenu`) ou aliment d'un fumoir (`ItemSmeltedEvent` avec un `SmokerMenu`). | Le geste compte quand il a vraiment produit quelque chose ; une ferme automatique (trémie, déployeur) ne compte pas |
| Suivi des blocs posés | Capacité `PlacedBlocks` sur chaque `LevelChunk` (sauvée par Forge avec le chunk, `setUnsaved(true)` à chaque changement) : positions locales tassées dans un `int`, ordre d'arrivée, au plus `questPlacedMarksPerChunk` = 4 096 par chunk, les plus anciennes oubliées. Marquées : pose par toute entité (joueur, faux joueur, déployeur), bloc formé par un fluide (`FluidPlaceBlockEvent`), destination d'un bloc poussé par un piston (`PistonEvent.Pre`). Retirée : casse du bloc. | Même fichier que le bloc (rien à tenir d'accord ailleurs), mémoire seulement pour les chunks chargés, bornée ; une marque en trop ne fait que refuser une progression, jamais créer de l'argent |
| Posé puis cassé aussitôt | Mémoire vive par joueur des 256 dernières poses (dimension, position, heure de jeu) : un joueur qui casse un bloc qu'il a posé depuis moins de `questPlaceUndoSeconds` = 60 s retire 1 à la quête de pose qu'il avait fait avancer, si elle n'est pas réclamée. Perdue à l'arrêt (60 s seulement). | Un seul bloc recyclé ne fait jamais avancer une pose ; poser 64 briques et les garder reste le geste voulu |
| Réclamation | Dans l'écran, une quête finie à la fois. Somme payée = `min(récompense, plafond − déjà payé)` ; plafond atteint : bouton grisé, « plafond de la semaine atteint : à réclamer après lundi », la quête attend. Pièces de `CoinMath.decompose`, dans les bourses puis l'inventaire (`PlayerCoins.receive`). | Lettre du cahier (« ne rapporte plus rien jusqu'au lundi, et l'écran le dit ») ; réduit à ce qui reste, comme le plafond du butin |
| Inventaire plein | **Refus** de la réclamation, rien ne bouge, message « Pas assez de place » ; la quête reste à réclamer. Jamais le coffre-fort ni l'attente. | Le coffre-fort et l'attente sont des `SavedData` : y verser séparerait la pièce de la marque « payée » (leçon d'E2) ; les pièces d'une récompense tiennent en 2 ou 3 cases |
| Finie mais pas réclamée au renouvellement | Elle passe dans les **récompenses en attente** du joueur (au plus 8, la plus ancienne oubliée au-delà, avec un message), réclamables plus tard, dans le plafond de la semaine où elles sont réclamées. Une quête non finie expire sans rien. | Ne pas perdre un effort fait parce qu'on s'est déconnecté avant minuit ; la somme reçue par semaine reste plafonnée |
| Relance | Une par jour (`questRerollsPerDay` = 1, 0 à 5), sur une journalière ou une hebdomadaire **non finie** ; la progression de la quête remplacée est perdue. | « une relance par jour » ; relancer une quête finie serait une façon de la payer deux fois |
| Quêtes d'événement | `/eco quest event start <id> <catégorie> <cible|*> <quantité> <récompense> <jours> [titre]` : communes à tous, progression propre à chacun, mêmes garde-fous et même plafond, fin à l'heure de renouvellement après N jours (1 à 30) ; `stop`, `list`. Gardées dans `QuestBoard` ; la progression de chacun dans son `QuestBook`. | Levier de l'équipe sans nouvelle source : au-delà du plafond, l'équipe a `/eco give` (injection, comptée à part) |
| Modèles de l'équipe | `/eco quest template add <id> <catégorie> <cible|*> <min> <max> <récompense> [poids]`, `remove`, `list` ; gardés dans `QuestBoard`, ajoutés à ceux du fichier ; un identifiant déjà pris est refusé. Mêmes contrôles que le fichier. | « par commande », sans redémarrer ni écrire de datapack |
| Écran | Touche **J** (« Quêtes », catégorie Lost Island, réglable dans Contrôles ; libre en vanilla, à vérifier avec le pack). Cadre de 300 × 226 pixels d'interface, qui tient dans la plus petite interface automatique (320 × 240). Haut : plafond (« 640 / 1 000 cuivres cette semaine »), ligne « N récompenses en attente » si besoin. Deux sections (« Aujourd'hui » : 3 lignes ; « Cette semaine » : 5 lignes) de 20 pixels : icône de la cible, titre, barre et « 87 / 128 », récompense, bouton « Réclamer » ou « Relancer ». Bas : « Journalières dans 5 h 12 — hebdomadaires lundi ». Onglet « Événements » seulement s'il y en a. Titre trop long : « … » et bulle complète ; récompense mesurée (`font.width`). | Lisible à la plus petite échelle (leçons du 2026-11-13 et du 2026-11-14 : mesurer, pire cas capturé) |
| Notification | Un toast discret (« Quête terminée : <titre> », 5 s, coin haut droit) à la quête qui vient de finir ; rien au chat. | « notification discrète » |
| Registre et tableau de bord | `EconomyLedger.questCreated` : total `QUEST`, nombre de réclamations, total par joueur, par modèle ; ligne de journal seulement pour une réclamation d'au moins `questJournalThreshold` = 100 cuivres (les hebdomadaires ; 0 = toutes). `/eco stats` : ligne « Quêtes » (déjà là). Nouvelle `/eco quests` (niveau 2) : total, réclamations, 10 premiers joueurs, 10 premiers modèles, modèles chargés, événements en cours. `/eco quest show <joueur>` (niveau 2) : ses séries. | Comme le butin d'E4 : voir venir un abus sans noyer le journal, où les injections doivent se lire en entier |

### Réserve de départ (`quests.json`)

Récompense de base en cuivres pour la quantité minimale ; poids de tirage ; `—` = sans cible.

| Identifiant | Catégorie | Cible | Quantité | Base | Poids | Rôles |
|---|---|---|---|---|---|---|
| `break_stone` | break | `#minecraft:base_stone_overworld` | 64–128 | 10 | 10 | |
| `break_logs` | break | `#minecraft:logs` | 32–64 | 12 | 10 | |
| `break_sand` | break | `#minecraft:sand` | 32–64 | 10 | 6 | |
| `mine_coal` | break | `#forge:ores/coal` | 12–24 | 10 | 8 | |
| `mine_copper` | break | `#forge:ores/copper` | 8–16 | 12 | 6 | |
| `mine_iron` | break | `#forge:ores/iron` | 6–12 | 15 | 8 | |
| `mine_gold` | break | `#forge:ores/gold` | 3–6 | 18 | 4 | |
| `place_planks` | place | `#minecraft:planks` | 32–64 | 10 | 8 | |
| `place_bricks` | place | `minecraft:bricks` | 16–32 | 12 | 5 | |
| `kill_monsters` | kill | — (catégorie `MONSTER`) | 10–20 | 15 | 8 | |
| `kill_zombies` | kill | `minecraft:zombie` | 5–10 | 15 | 6 | |
| `kill_skeletons` | kill | `minecraft:skeleton` | 5–10 | 15 | 6 | |
| `kill_spiders` | kill | `minecraft:spider` | 4–8 | 15 | 5 | |
| `harvest_wheat` | harvest | `minecraft:wheat` | 32–64 | 10 | 6 | agriculteur × 3 |
| `harvest_carrots` | harvest | `minecraft:carrots` | 32–64 | 10 | 5 | agriculteur × 3 |
| `harvest_potatoes` | harvest | `minecraft:potatoes` | 32–64 | 10 | 5 | agriculteur × 3 |
| `fish` | fish | `#minecraft:fishes` | 6–12 | 15 | 6 | |
| `cook_food` | cook | — (tout aliment) | 8–16 | 12 | 6 | agriculteur × 2 |
| `cook_meals` | cook | `#farmersdelight:meals` | 3–6 | 15 | 4 | agriculteur × 2 |
| `preserve_food` | preserve | — (tout aliment) | 8–16 | 12 | 5 | agriculteur × 2 |
| `breed_animals` | breed | — (tout animal) | 4–8 | 12 | 5 | agriculteur × 2 |
| `repair_machines` | job | `oneforallsurvival:repair_machine` | 2–3 | 25 | 6 | ingénieur seul ; `weekly_factor` 3 |

## Fichiers

Racine du code : `src/main/java/dev/oneforallsurvival/` (abrégée) ; essais : `src/devcontent/java/dev/oneforallsurvival/dev/` (abrégé `dev/`) ; JUnit : `src/test/java/dev/oneforallsurvival/`.

| Fichier | Rôle |
|---|---|
| `economy/quest/QuestCategory.java`, `QuestTarget.java`, `QuestTemplate.java`, `QuestTemplates.java`, `QuestJobs.java`, `Quest.java`, `QuestGenerator.java`, `src/main/resources/data/oneforallsurvival/economy/quests.json`, JUnit `economy/quest/QuestTemplatesTest.java`, `QuestGeneratorTest.java` | Modèles, réserve, tirage |
| `economy/quest/QuestPeriods.java`, `QuestRef.java`, `QuestBook.java`, `QuestService.java`, `economy/EconomyLedger.java`, `EconomyConfig.java`, `EconomyCommands.java` (modifiés), JUnit `QuestPeriodsTest.java`, `QuestBookTest.java`, `EconomyLedgerTest.java` (étendu), `dev/QuestClaimGameTests.java` | État du joueur, renouvellement, relance, réclamation, registre |
| `economy/quest/QuestProgress.java`, `PlacedBlocks.java`, `RecentPlacements.java`, `mixin/CookingPotResultSlotMixin.java`, `oneforallsurvival.mixins.json`, `repair/RepairActions.java`, `food/CanteenPotMenu.java`, `food/DryerMenu.java` (modifiés), `dev/QuestProgressGameTests.java`, `dev/PackGameTests.java` (modifié) | Progression et garde-fous |
| `economy/quest/QuestBoard.java`, `QuestCommands.java`, `dev/QuestBoardGameTests.java` | Modèles et événements de l'équipe |
| `economy/quest/QuestSyncPacket.java`, `QuestActionPacket.java`, `bench/BenchNetwork.java` (modifié), `economy/quest/client/QuestClientCache.java`, `QuestKeys.java`, `QuestScreen.java`, `QuestScreenLayout.java`, `QuestToast.java`, JUnit `economy/quest/client/QuestScreenLayoutTest.java`, `dev/DemoVisuels.java` (modifié), langues | Écran, touche, réseau, notification |
| `dev/E2eQuetes.java`, `dev/E2eQuetesServeur.java`, `dev/E2eClient.java` (modifié), `docs/verifications/lot-e5.md`, `wiki/data/economie.json`, `tasks/todo.md` | Preuve |

`OneForAllSurvival.java` reçoit les abonnements de chaque tâche.

---

### Task 1: Modèles de quêtes et tirage

**Files:**
- Create : `economy/quest/QuestCategory.java`, `QuestTarget.java`, `QuestTemplate.java`, `QuestTemplates.java`, `QuestJobs.java`, `Quest.java`, `QuestGenerator.java`, `src/main/resources/data/oneforallsurvival/economy/quests.json`, JUnit `economy/quest/QuestTemplatesTest.java`, `QuestGeneratorTest.java`.
- Modify : `OneForAllSurvival.java` (`QuestTemplates::onAddReloadListener`), langues (titres des 22 modèles livrés : `quest.oneforallsurvival.template.<id>` avec `%s` pour la quantité, « Casser %s blocs de pierre », « Extraire %s minerais de fer »… ; titres génériques par catégorie `quest.oneforallsurvival.category.<cat>` avec quantité et nom de la cible, pour les modèles de l'équipe).

**Interfaces:**
- Consumes : rien du lot ; `RoleBook.DEFAULT_ROLE_ID`, `RoleBook.ENGINEER_ROLE_ID`.
- Produces :
  - `enum QuestCategory { BREAK, PLACE, KILL, HARVEST, FISH, COOK, PRESERVE, BREED, JOB, DELIVER }` : `String key()` (`break`…), `static Optional<QuestCategory> byKey(String)`, `TargetKind targetKind()` (`BLOCK` pour break, place, harvest ; `ENTITY` pour kill, breed ; `ITEM` pour fish, cook, preserve, deliver ; `ACTION` pour job), `boolean counted()` (faux pour `DELIVER`).
  - `record QuestTarget(ResourceLocation id, boolean tag)` : `static Optional<QuestTarget> parse(String)` (`#ns:chemin` → tag) ; `String text()`.
  - `record QuestTemplate(String id, QuestCategory category, @Nullable QuestTarget target, int min, int max, long reward, double weight, Map<String, Double> roleWeights, OptionalInt weeklyFactor, @Nullable String titleKey)` : `long rewardFor(int quantity)` (formule des choix) ; `double weightFor(String roleId)` (`weight × roleWeights.getOrDefault(roleId, 1)`, et 0 pour un modèle `JOB` dont l'action n'est pas liée à ce rôle).
  - `QuestJobs` : `static final ResourceLocation REPAIR_MACHINE = oneforallsurvival:repair_machine` ; `static void register(ResourceLocation action, String roleId)` (déjà fait pour la réparation, à l'ingénieur) ; `static Optional<String> roleOf(ResourceLocation action)`.
  - `QuestTemplates` : format `{"templates": [{"id", "category", "target"?, "min", "max", "reward", "weight"?, "role_weights"?, "weekly_factor"?, "title"?}]}` ; `static List<QuestTemplate> parse(JsonObject)` (lève `JsonParseException` qui nomme le modèle et le champ : identifiant `[a-z0-9_]{1,32}` unique ; catégorie connue et `counted` ; cible obligatoire pour `JOB` et action enregistrée ; identifiant de cible (pas un tag) présent dans le registre de son genre ; `1 <= min <= max <= 10 000` ; `1 <= reward <= 10 000` ; `weight > 0` ; poids de rôle `>= 0` ; `weekly_factor` de 1 à 20) ; `static List<QuestTemplate> current()` (fichier, puis modèles de l'équipe ajoutés par `setStaffTemplates`) ; `static void setStaffTemplates(List<QuestTemplate>)` (appelée par la tâche 4) ; `static void setForTest(@Nullable List<QuestTemplate>)` ; `static void onAddReloadListener(AddReloadListenerEvent)` (absent ou invalide : réserve précédente gardée, vide au premier chargement, erreur au journal ; valide : `INFO` avec le nombre de modèles).
  - `record Quest(String templateId, QuestCategory category, @Nullable QuestTarget target, int goal, long reward, int progress, boolean claimed, @Nullable String titleKey)` : `boolean complete()` ; `Quest withProgress(int)` (borné à `goal`) ; `Quest claimedNow()` ; `CompoundTag save()` ; `static Quest load(CompoundTag)`.
  - `QuestGenerator` : `enum Series { DAILY, WEEKLY }` ; `static List<Quest> draw(List<QuestTemplate> pool, String roleId, Series series, int count, Set<String> excluded, int weeklyFactor, RandomSource random)` (sans remise, poids `weightFor(roleId)`, modèles à poids nul et `excluded` écartés ; quantité uniforme de `min` à `max`, puis × facteur du modèle ou `weeklyFactor` en hebdomadaire ; récompense `rewardFor(q)`, × le même facteur en hebdomadaire ; progression 0).

- [ ] **Step 1: Écrire `QuestTemplatesTest`** (JUnit, le fichier livré lu depuis `src/main/resources`) : `leFichierLivreSeLit` (22 modèles) ; `chaqueCategorieComptableALeSien` (au moins un modèle par catégorie sauf `DELIVER`) ; `uneLivraisonEstRefusee` ; `uneActionInconnueEstRefusee` ; `unFichierInvalideEstRefuse` (paramétré : JSON mal formé, catégorie `voler`, `min` 3 > `max` 2, `reward` 0, `weight` 0, identifiant en double, `weekly_factor` 30 → `JsonParseException` qui nomme le champ) ; `laRecompenseSuitLaQuantite` (`break_stone` : 64 → 10, 96 → 15, 128 → 20 ; hebdomadaire de 96 × 5 → 75).
- [ ] **Step 2: Écrire `QuestGeneratorTest`** (graine fixe, réserve livrée) : `troisJournalieresDifferentes` (1 000 tirages : toujours 3 modèles distincts) ; `cinqHebdomadairesDifferentes` ; `uneRelanceEnDonneUneAutre` (`excluded` = la série et la quête remplacée : le modèle tiré n'y est jamais) ; `uneQueteDeMetierResteAuRole` (10 000 tirages pour `citoyen`, `agriculteur`, `medecin` : jamais `repair_machines` ; pour `ingenieur` : au moins une fois) ; `lAgriculteurRecoitPlusDeRecoltes` (fréquence des récoltes chez l'agriculteur entre 2 et 4 fois celle du citoyen, et l'agriculteur reçoit aussi des quêtes d'autres catégories) ; `uneReserveTropPetiteDonneCeQuElleA` (2 modèles pour 3 journalières → 2) ; `lHebdomadaireVautCinqJournalieres`.
- [ ] **Step 3: Lancer `./gradlew --offline test`** (pas de Minecraft, pas de verrou) ; constater l'échec.
- [ ] **Step 4: Implémenter** les classes, le fichier livré (tableau « Réserve de départ »), les titres fr/en.
- [ ] **Step 5: Lancer `./gradlew --offline test`**, lire le rapport JUnit.
- [ ] **Step 6: Commit** `feat(quests): modeles de quetes en donnees et tirage`, push.

### Task 2: Quêtes du joueur, renouvellement, relance et réclamation

**Files:**
- Create : `economy/quest/QuestPeriods.java`, `QuestRef.java`, `QuestBook.java`, `QuestService.java`, JUnit `QuestPeriodsTest.java`, `QuestBookTest.java`, `dev/QuestClaimGameTests.java`.
- Modify : `economy/EconomyLedger.java`, `EconomyConfig.java`, `EconomyCommands.java` (`/eco quests`), `OneForAllSurvival.java` (connexion, tic de contrôle toutes les 20 ticks), JUnit `economy/EconomyLedgerTest.java`, langues.

**Interfaces:**
- Consumes : `Quest`, `QuestTemplates.current()`, `QuestGenerator.draw`, `EconomyCalendar.payDay/boundaryAfter/configuredZone`, `EconomyClock.now()`, `RoleSavedData.get(server).book().roleOf(uuid)`, `CoinMath.decompose/format`, `InventoryFit.fits`, `PlayerCoins.receive`.
- Produces :
  - `QuestPeriods` : `static LocalDate dayKey(long now, ZoneId zone, int hour)` ; `static LocalDate weekKey(long now, ZoneId zone, int hour)` (lundi de la semaine du jour) ; `static long nextDailyReset(long now, ZoneId, int)` ; `static long nextWeeklyReset(long now, ZoneId, int)`.
  - `EconomyConfig` : `questDailyCount()` 3 (0 à 10), `questWeeklyCount()` 5 (0 à 10), `questWeeklyFactor()` 5 (1 à 20), `questResetHour()` 6 (0 à 23), `questRerollsPerDay()` 1 (0 à 5), `questWeeklyCap()` 1 000 (0 à 1 000 000 000 ; 0 = aucun plafond), `questPendingMax()` 8 (0 à 64), `questJournalThreshold()` 100, `questPlaceUndoSeconds()` 60 (0 à 3 600), `questPlacedMarksPerChunk()` 4 096 (256 à 65 536).
  - `QuestBook` (état pur, sérialisable ; clé `oneforallsurvival:quests` dans `Player.PERSISTED_NBT_TAG`) : champs `dailyKey`, `daily` (liste de `Quest`), `weeklyKey`, `weekly`, `rerollDay`, `rerollsUsed`, `capWeek`, `capPaid`, `pending` (liste de `Quest` finies non réclamées), `events` (identifiant d'événement → `Quest`, tâche 4) ; `boolean refresh(LocalDate today, LocalDate week, Function<QuestGenerator.Series, List<Quest>> drawer, int pendingMax)` (règles « Horloge qui recule ou saute » ; les finies non réclamées d'une série remplacée passent en `pending` ; vrai si quelque chose a changé) ; `boolean canReroll(LocalDate today, int perDay)` ; `Optional<Quest> reroll(Series, int index, LocalDate today, Function<Set<String>, Optional<Quest>> drawer)` ; `long capRemaining(LocalDate week, long cap)` (`Long.MAX_VALUE` si `cap == 0`) ; `void recordPaid(LocalDate week, long amount)` ; `static QuestBook of(ServerPlayer)` / `void saveTo(ServerPlayer)` (lecture et écriture dans `getPersistentData()`, sans autre écriture).
  - `enum QuestRef.Kind { DAILY, WEEKLY, PENDING, EVENT }` ; `record QuestRef(Kind kind, int index, String templateId, LocalDate seriesKey)` (identifie la quête que le client a vue ; une référence dont le modèle ou la clé ne correspond plus est refusée).
  - `QuestService` : `enum ClaimResult { PAID, CAPPED_PARTIAL, CAP_REACHED, NO_ROOM, NOT_COMPLETE, ALREADY_CLAIMED, STALE, CREATIVE }` ; `static boolean refresh(ServerPlayer, long now)` ; `static ClaimResult claim(ServerPlayer, QuestRef, long now, EconomyLedger)` (créatif refusé ; somme = `min(reward, capRemaining)` ; 0 → `CAP_REACHED`, rien ne bouge ; `InventoryFit.fits` faux → `NO_ROOM`, rien ne bouge ; sinon pièces par `PlayerCoins.receive`, quête marquée, `recordPaid`, `ledger.questCreated(...)`, `saveTo`) ; `static boolean reroll(ServerPlayer, QuestRef, long now)` ; `static void markDirty(ServerPlayer)` (la tâche 5 y branche l'envoi de l'état).
  - `EconomyLedger` : `void questCreated(UUID player, String templateId, long amount, boolean journal)` (ajoute à `created[QUEST]`, au nombre de réclamations, au total du joueur et du modèle ; ligne `created.quest` « quête : <id> » seulement si `journal`) ; `long questClaims()` ; `Map<UUID, Long> questByPlayer()` ; `Map<String, Long> questByTemplate()` (copies). NBT : composé `quest` ; un fichier d'avant le lot se relit à zéro.
  - `/eco quests` (niveau 2) : total (`CoinMath.format`), réclamations, 10 premiers joueurs, 10 premiers modèles, nombre de modèles chargés. Textes fr/en.

- [ ] **Step 1: Écrire `QuestPeriodsTest`** (JUnit) : `avantSixHeuresCEstLaVeille` ; `laSemaineCommenceLeLundi` (dimanche 23 h → lundi d'avant ; lundi 5 h 59 → lundi d'avant ; lundi 6 h → ce lundi) ; `heureDEteSansJourPerdu` (Europe/Paris, fin mars et fin octobre) ; `prochainRenouvellement`.
- [ ] **Step 2: Écrire `QuestBookTest`** (JUnit, tireur factice qui numérote ses quêtes) : `premiereConnexionDonneTroisEtCinq` ; `memeJourRienNeChange` ; `lendemainNouvellesJournalieresMemeHebdomadaires` ; `lundiNouvellesHebdomadaires` ; `horlogeReculeeToutEstGarde` (série, relance prise, plafond payé : rien ne revient) ; `dixJoursSautesUnSeulRenouvellement` ; `uneFinieNonReclameePasseEnAttente` et `laPlusAncienneAttenteEstOubliee` (9 pour un maximum de 8) ; `uneNonFinieExpireSansRien` ; `uneRelanceParJour` (deuxième refusée le même jour, rendue le lendemain) ; `onNeRelancePasUneFinie` ; `lePlafondSeRemetLeLundi` ; `sauvegardeEtRelectureIdentiques` (NBT aller-retour, toutes les listes).
- [ ] **Step 3: Étendre `EconomyLedgerTest`** : `lesQuetesComptentALaSourceEtParJoueur` ; `pasDeLigneSousLeSeuil` ; `lesQuetesSurviventALaSauvegarde` ; `unAncienRegistreSeRelitAZero`.
- [ ] **Step 4: Écrire `QuestClaimGameTests`** (registre détaché, réserve d'essai par `setForTest`, horloge fixe : lot à lui, `@BeforeBatch`/`@AfterBatch` ; une quête finie posée par `QuestBook`) :
  - `reclamerPaieDansLaBourseEtCompte` (bourse dans l'inventaire : pièces dans la bourse ; hausse de `totalCreated(QUEST)` = récompense ; quête marquée) ;
  - `deuxReclamationsNePaientQuUneFois` (deux `claim` de suite : `PAID` puis `ALREADY_CLAIMED`, total inchangé) ;
  - `inventairePleinRienNeBouge` (36 cases pleines d'autre chose, pas de bourse : `NO_ROOM`, inventaire, quête et registre inchangés) ;
  - `lePlafondReduitPuisArrete` (plafond 1 000, déjà payé 980, récompense 50 : 20 payés, `CAPPED_PARTIAL` ; suivante : `CAP_REACHED`, rien ne bouge, quête toujours à réclamer) ;
  - `uneReferencePerimeeEstRefusee` (référence d'une série d'hier : `STALE`) ;
  - `leCreatifNeReclamePas` ;
  - `laQueteSuitLeJoueurApresSaMort` (`PERSISTED_NBT_TAG` recopié par `restoreFrom` : progression intacte) ;
  - `ecoQuestsMontreLesTotaux` (registre du serveur, lot à lui).
- [ ] **Step 5: Lancer** `./gradlew --offline test` puis (sous le verrou) `runGameTestServer` ; constater l'échec.
- [ ] **Step 6: Implémenter.**
- [ ] **Step 7: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 8: Commit** `feat(quests): quetes du joueur, renouvellement et reclamation`, push.

### Task 3: Progression par les gestes et garde-fous

**Files:**
- Create : `economy/quest/QuestProgress.java`, `PlacedBlocks.java`, `RecentPlacements.java`, `mixin/CookingPotResultSlotMixin.java`, `dev/QuestProgressGameTests.java`.
- Modify : `oneforallsurvival.mixins.json`, `OneForAllSurvival.java` (écouteurs, capacité de chunk), `repair/RepairActions.java` (après `REPAIRED`), `food/CanteenPotMenu.java` et `food/DryerMenu.java` (`onTake` de la case de sortie), `dev/PackGameTests.java`.

**Interfaces:**
- Consumes : `QuestBook`, `QuestService.markDirty`, `MobLoot.creditedPlayer`, `MobOrigin.isWild`, `QuestJobs`, `EconomyConfig.questPlaceUndoSeconds/questPlacedMarksPerChunk`.
- Produces :
  - `PlacedBlocks` (capacité de `LevelChunk`, `ICapabilitySerializable<IntArrayTag>`) : `static boolean isPlaced(Level, BlockPos)` ; `static void mark(Level, BlockPos)` ; `static void unmark(Level, BlockPos)` (chacun met le chunk à sauver) ; `static int pack(BlockPos, int minBuildHeight)` / `static BlockPos unpack(...)` ; borne par chunk, la plus ancienne retirée.
  - `RecentPlacements` (mémoire vive, par joueur, 256 au plus) : `void record(UUID, ResourceKey<Level>, BlockPos, long gameTime, String countedTemplateId)` ; `Optional<String> takeIfRecent(UUID, ResourceKey<Level>, BlockPos, long gameTime, long windowTicks)`.
  - `QuestProgress` : `static boolean eligible(@Nullable Entity)` (un `ServerPlayer` de la liste des joueurs du serveur, ni `FakePlayer`, ni spectateur, ni créatif) ; `static void add(ServerPlayer, QuestCategory, Predicate<QuestTarget> matches, int amount)` (fait avancer toutes les quêtes non finies et non réclamées du joueur — journalières, hebdomadaires, événements — de cette catégorie dont la cible correspond ou qui n'en ont pas ; une quête qui devient finie est signalée à `QuestService.markDirty`) ; écouteurs `onBreak(BlockEvent.BreakEvent)`, `onPlace(BlockEvent.EntityPlaceEvent)`, `onFluidPlace(BlockEvent.FluidPlaceBlockEvent)`, `onPiston(PistonEvent.Pre)`, `onLivingDrops(LivingDropsEvent)`, `onFished(ItemFishedEvent)`, `onBabySpawn(BabyEntitySpawnEvent)`, `onSmelted(PlayerEvent.ItemSmeltedEvent)` ; entrées des crochets `static void cooked(ServerPlayer, ItemStack)`, `static void preserved(ServerPlayer, ItemStack)`, `static void job(ServerPlayer, ResourceLocation action, int count)`, `static void deliver(ServerPlayer, ItemStack)` (sans appelant tant que `DELIVER` n'est pas compté). Règles par catégorie : tableau « Comment chaque catégorie compte ».

- [ ] **Step 1: Lire au bytecode** (`javap -c` sur les jars du cache de Gradle) et noter dans le rapport de la tâche : `CookingPotResultSlot.checkTakeAchievements` de Farmer's Delight 1.3.4 (nom SRG de la méthode surchargée de `Slot`, pour le mixin et la refmap) ; que `FurnaceResultSlot` tire `ItemSmeltedEvent` aussi au clic majuscule ; que `LivingDropsEvent` est tiré même avec `doMobLoot` faux ; que les capacités de `LevelChunk` sont écrites avec le chunk (`ChunkSerializer`) ; si le déployeur et la perceuse de Create passent par `BreakEvent`/`EntityPlaceEvent` avec un faux joueur. Un chemin qui ne se comporte pas comme le plan le suppose s'écrit dans le rapport, ne se contourne pas sans en parler.
- [ ] **Step 2: Écrire `QuestProgressGameTests`** (réserve d'essai à une quête par catégorie et quantités de 2 à 4 ; `JoueurDeTest` en survie ; lots de 8 au plus) :
  - `casserDeLaPierreNaturelleAvance` et `unBlocPosePuisCasseNAvanceRien` (pierre posée par le joueur puis cassée : casse inchangée ; pose : retirée) ;
  - `unSeulBlocRecycleNeFaitPas64Poses` (poser-casser le même bloc 10 fois : pose à 0) ; `poserEtGarderAvance` (au-delà de 60 s d'horloge de jeu simulée, la casse ne retire plus rien) ;
  - `laPierreDUnGenerateurNeComptePas` (lave qui coule sur de l'eau : la pierre formée est marquée, sa casse n'avance rien) ; `unBlocPousseParUnPistonNeComptePas` ;
  - `laMarqueSurvitAuChunk` (sérialisation de la capacité aller-retour ; borne : 4 097 marques → la première oubliée) ;
  - `sansLeBonOutilRienNAvance` (minerai de fer cassé à la main) ;
  - `unZombieSauvageAvanceUnZombieDeGenerateurNon` et `unFauxJoueurNAvanceRien` ;
  - `uneRecolteMureAvanceUnePousseNon` ;
  - `peche` (`ItemFishedEvent` d'essai avec une morue et une botte : 1) ; `elevage` (deux vaches nourries par le joueur : 1 petit, 1) ;
  - `cuisineAuFourEtConserveAuFumoir` (sortie d'un `FurnaceMenu` et d'un `SmokerMenu` par `quickMoveStack`) ; `sortieDuSechoirEtDeLaMarmite` ; `unPlatDuCookingPotDeFarmersDelight` (vrai `CookingPotBlockEntity`, sortie prise par le menu du joueur) ;
  - `reparerUneMachineAvanceLaQueteDeMetier` (ingénieur, machine en panne, `RepairActions.repair` → `REPAIRED`) ;
  - `leCreatifNAvanceRien` ; `uneQueteFinieNAvancePlus` (bornée au but, réclamée : plus rien).
- [ ] **Step 3: Écrire le test du pack** dans `PackGameTests` (seulement avec `-PwithPack`) : `unDeployeurDeCreateNAvanceRien` (déployeur qui pose des planches puis un autre qui casse de la pierre naturelle, devant un joueur qui a les deux quêtes : progression nulle ; les planches posées par le déployeur sont marquées).
- [ ] **Step 4: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 5: Implémenter.**
- [ ] **Step 6: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 7: Commit** `feat(quests): progression des quetes et garde-fous`, push.

### Task 4: Modèles et quêtes d'événement de l'équipe

**Files:**
- Create : `economy/quest/QuestBoard.java`, `QuestCommands.java`, `dev/QuestBoardGameTests.java`.
- Modify : `economy/EconomyCommands.java` (branche `/eco quest`), `QuestBook.java` et `QuestService.java` (événements dans `refresh`, `claim` et `QuestProgress.add`), `OneForAllSurvival.java`, langues.

**Interfaces:**
- Consumes : `QuestTemplates.parse` (mêmes contrôles, sur un objet JSON construit depuis les arguments), `QuestTemplates.setStaffTemplates`, `QuestPeriods`.
- Produces :
  - `record QuestEvent(String id, QuestCategory category, @Nullable QuestTarget target, int goal, long reward, long startMillis, long endMillis, String title, UUID author)`.
  - `QuestBoard extends SavedData` (clé `oneforallsurvival_quests`) : `static QuestBoard get(MinecraftServer)` ; `Result addTemplate(QuestTemplate)` (identifiant pris dans le fichier ou la liste : refusé) ; `boolean removeTemplate(String)` ; `List<QuestTemplate> templates()` ; `Result startEvent(QuestEvent)` ; `boolean stopEvent(String)` ; `List<QuestEvent> activeEvents(long now)` ; chaque changement appelle `QuestTemplates.setStaffTemplates` et `setDirty()`.
  - Dans `QuestBook.refresh` : une quête d'événement est ajoutée à `events` à la première vue d'un événement en cours, retirée quand l'événement n'y est plus (finie non réclamée → `pending`). une `QuestRef` de genre `EVENT` porte l'identifiant de l'événement dans `templateId` et la date de début dans `seriesKey`.
  - Commandes (niveau 2), textes fr/en, chaque geste noté au journal du serveur avec l'auteur : `/eco quest template add <id> <catégorie> <cible|*> <min> <max> <récompense> [poids]`, `remove <id>`, `list` ; `/eco quest event start <id> <catégorie> <cible|*> <quantité> <récompense> <jours> [titre]`, `stop <id>`, `list` ; `/eco quest show <joueur>`. Suggestions des catégories comptées.

- [ ] **Step 1: Écrire `QuestBoardGameTests`** (registre détaché, horloge fixe, lot à lui) : `unModeleAjouteEstTirable` ; `unIdentifiantPrisEstRefuse` (pris dans le fichier, puis dans la liste) ; `unModeleInvalideEstRefuseEtLaReserveReste` (catégorie `deliver`, `min > max`, cible inconnue) ; `leModeleSurvitALaRelanceDuMonde` (sauvegarde et relecture de `QuestBoard`) ; `unEvenementApparaitChezChacun` (deux joueurs, progression propre à chacun) ; `unEvenementFiniSePaieSousLePlafond` ; `unEvenementTermineEstRetire` (horloge après la fin : retiré, finie non réclamée en attente) ; `showLitLesSeries`.
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 5: Commit** `feat(quests): modeles et quetes d'evenement de l'equipe`, push.

### Task 5: Écran de quêtes, touche, réseau et notification

**Files:**
- Create : `economy/quest/QuestSyncPacket.java`, `QuestActionPacket.java`, `economy/quest/client/QuestClientCache.java`, `QuestKeys.java`, `QuestScreen.java`, `QuestScreenLayout.java`, `QuestToast.java`, JUnit `economy/quest/client/QuestScreenLayoutTest.java`.
- Modify : `bench/BenchNetwork.java` (`ID_QUEST_SYNC = 22` vers le client, `ID_QUEST_ACTION = 23` vers le serveur, protocole `"8"`), `QuestService.java` (envoi : à la connexion, à la demande, au renouvellement, et pour un joueur marqué sale au plus une fois toutes les 10 ticks), `dev/DemoVisuels.java` (scène de l'écran de quêtes), `dev/QuestClaimGameTests.java` (paquets), langues (`key.oneforallsurvival.quests`, textes de l'écran et du toast).

**Interfaces:**
- Consumes : `QuestBook`, `QuestService.claim/reroll/refresh`, `QuestRef`, `QuestPeriods.nextDailyReset/nextWeeklyReset`, `CoinMath.format`.
- Produces :
  - `QuestSyncPacket` : la vue d'un joueur (pour chaque quête : `QuestRef`, titre en `Component` traduit chez le client, icône `ItemStack`, progression, but, récompense, somme payable maintenant, état ; plafond payé et plafond ; relance disponible ; prochains renouvellements en millisecondes ; nombre en attente ; événements). Taille bornée (au plus 64 quêtes, titres de 64 caractères).
  - `QuestActionPacket(int action, QuestRef ref)` : `OPEN` (demande d'état), `CLAIM`, `REROLL`, `CLAIM_PENDING_ALL` ; le serveur rejoue `QuestService` avec `EconomyClock.now()`, répond par un message en barre d'action (`ClaimResult`) et un nouvel état. Une référence inconnue ou périmée ne fait rien d'autre que renvoyer l'état.
  - `QuestKeys` : `KeyMapping("key.oneforallsurvival.quests", GLFW_KEY_J, "key.categories.oneforallsurvival")`, enregistrée à `RegisterKeyMappingsEvent` ; au relâchement en jeu sans écran ouvert : ouvre `QuestScreen` sur la copie du client et envoie `OPEN`.
  - `QuestScreenLayout` (pur, testable) : `static Layout of(int guiWidth, int guiHeight, ToIntFunction<String> textWidth, View view)` → positions du cadre, des lignes, des boutons et des textes coupés (« … ») ; cadre de 300 × 226, centré.
  - `QuestToast` : toast de 5 s « Quête terminée » et le titre, ajouté par `QuestClientCache` quand une quête passe de non finie à finie dans deux états successifs (jamais à la première réception).

- [ ] **Step 1: Écrire `QuestScreenLayoutTest`** (largeur de texte factice à 6 pixels par caractère) : `toutTientEn320Par240` (contenu le plus long : titres de 64 caractères, récompense à cinq coupures, 3 + 5 lignes, 8 en attente, événements : aucun rectangle hors de l'écran ni du cadre) ; `unTitreTropLongEstCoupe` ; `leContenuLePlusCourtResteAligne`.
- [ ] **Step 2: Étendre `QuestClaimGameTests`** (paquets traités sur place, leçon du 2026-11-12) : `unClicPerimeNeReclameRien` ; `deuxPaquetsDeReclamationDansLeMemeTick` (un seul paiement) ; `uneRelanceParPaquetPuisRefus` ; `unIndexHorsDesBornesNeFaitRien`.
- [ ] **Step 3: Lancer** `./gradlew --offline test` puis (sous le verrou) `runGameTestServer` ; constater l'échec.
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Captures** (sous le verrou) : `./gradlew --offline runDemoVisuels` ; `DemoVisuels` ouvre l'écran avec un état imposé : (a) contenu le plus long dans une interface de 320 × 240, (b) le même dans une grande interface, (c) contenu le plus court (une quête, aucune attente, aucun événement), (d) plafond atteint (« à réclamer après lundi »), (e) bulle d'un titre coupé, (f) toast. **Ouvrir chaque image** ; rien ne déborde du cadre, tout se lit. Lire « [souris] rendue » et « [fenetre] second écran » au journal ; aucun client laissé ouvert, verrou vidé sans BOM. Vérifier aussi dans Contrôles, avec le pack (`runClient -PwithPack`, même procédure), que J n'est pas en conflit (pas en rouge) ; s'il l'est, choisir une touche libre et le dire au rapport.
- [ ] **Step 7: Commit** `feat(quests): ecran de quetes, touche et notification`, push.

### Task 6: Preuve de bout en bout, vérification, wiki, suivi

**Files:**
- Create : `dev/E2eQuetes.java` (client), `dev/E2eQuetesServeur.java` (commandes de console niveau 4 réservées au RCON, sur le modèle d'`E2eButinServeur` : bâtir l'aire close et couverte, imposer une quête connue à un joueur (`imposer <joueur> daily|weekly <index> <modèle> <quantité>`), lire son `QuestBook`, avancer l'horloge au lundi suivant par `HorlogeDEssai` (`horloge lundi`), lire le registre et `/eco quests`), `docs/verifications/lot-e5.md`.
- Modify : `dev/E2eClient.java` (phase `QUETES`), `wiki/data/economie.json`, `tasks/todo.md`.

**Ordre des étapes** (leçons du 2026-11-11 et du 2026-11-15 : lister d'abord ce que chaque relance relit).
- Passage : `MONNAIE` → `COMMERCE` → `BUTIN` → **`QUETES`** → `TRESOR` : la réécriture des références d'E1 et d'E2 faite à la fin d'E3 compte déjà les pièces des quêtes ; `QUETES` range ce que Dev a reçu dans son coffre-fort (« Déposer tout ») avant `TRESOR`, qui part d'un Dev qui ne porte rien.
- Relance : `COMMERCE` → `MONNAIE` → `TRESOR` → `BUTIN` → **`QUETES` en dernier** : ses réclamations et son saut d'horloge au lundi viennent après tous les contrôles exacts des autres étapes. `QUETES` ne compare que la ligne « Quêtes » de `/eco stats` et `/eco quests`.
- `QUETES` a son aire à part, close et couverte, loin du point de départ comme celle du butin ; il rend les droits et ne range que ses propres objets.

**Essai sur serveur dédié avec le pack** (client `Dev` en survie, non opérateur pour les gestes ; monde neuf, l'ancien rangé hors du dépôt) :
- **Lire ses quêtes** : Dev appuie sur J (vraie touche) ; l'écran montre 3 journalières et 5 hebdomadaires distinctes, lues sur la copie du client et comparées à `QuestBook` lu par RCON.
- **Imposer** (RCON) : journalière 0 = `break_stone` × 4 ; journalière 1 = `place_planks` × 2 ; hebdomadaire 0 = `break_logs` × 10.
- **Abus** : Dev pose une pierre et la casse (vrais clics) : casse et pose inchangées. Il pose une planche et la casse aussitôt, dix fois : pose à 0.
- **Finir et réclamer** : Dev casse 4 pierres naturelles (piochées dans l'aire) : toast « Quête terminée » vu par le client ; vrai clic sur « Réclamer » : pièces dans sa bourse ou son inventaire, hausse de « Quêtes » dans `/eco stats` = récompense, `/eco quests` le nomme.
- **Laisser en attente** : Dev pose 2 planches et les garde : journalière 1 finie, **non réclamée**. Il casse 3 bûches posées par l'équipe à la génération de l'aire (non marquées : `setblock` sans joueur) : hebdomadaire 0 à 3 / 10.
- **Relance** : vrai clic sur « Relancer » d'une journalière non finie : autre modèle, bouton grisé ensuite.
- Avant l'arrêt : état du `QuestBook`, ligne « Quêtes » et `/eco quests` écrits dans `e2e-quetes.txt` ; une pierre posée par Dev laissée en place pour la relance.
- **Relance du serveur** (`-Pe2eHorloge` : deux jours de plus, celui d'E3) : journalières renouvelées (clé changée, 3 nouvelles) ; la journalière 1 finie non réclamée est **en attente** avec sa récompense ; l'hebdomadaire 0 garde 3 / 10 si la semaine n'a pas changé, sinon elle est renouvelée (l'étape lit la clé et vérifie le cas qui s'applique) ; la pierre posée avant l'arrêt, cassée par Dev, ne fait pas avancer une casse (marque relue depuis le chunk) ; ligne « Quêtes » et `/eco quests` relus identiques avant toute réclamation.
- **Réclamer l'attente** (vrai clic), puis **changer la date** : `e2equetes horloge lundi` (lundi suivant, 6 h + 1 min) ; à la réouverture de l'écran, 5 nouvelles hebdomadaires, plafond de la semaine à 0, relance disponible.

- [ ] **Step 1: Écrire l'E2E** (client et serveur) et lancer sous le verrou : `runServer -PwithPack` puis `E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack` ; nouveau `runServer -PwithPack -Pe2eHorloge=<minutes>` puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack` ; puis le **passage complet** (`runE2eClient -PwithPack`) et sa relance ciblée. Lire « [e2e] bilan : TOUT PASSE » à chaque passage, « [souris] rendue » et « [fenetre] second écran » dans le journal du client ; aucun client laissé ouvert, PID notés, verrou vidé sans BOM.
- [ ] **Step 2: Écrire `docs/verifications/lot-e5.md`**, au format de `lot-e4.md` : commandes, heures et comptes lus ; décisions du lot ; captures ouvertes ; séparer prouvé et non prouvé. Couvre au moins : ce que la tâche 3 a lu au bytecode ; blocs déplacés par une machine de Create (contraption qui démonte ses blocs ailleurs : leurs marques ne suivent pas, non gardé) ; fermes de pêche à l'arrêt automatique et fermes à arbres (permises, bornées par le plafond) ; livraison absente faute de grenier ; quêtes de métier du médecin et du policier à venir avec leurs lots ; limite de Minecraft d'une réclamation puis arrêt brutal avant la sauvegarde du monde (la pièce et la marque « payée » sont dans le même playerdata, la création du registre non : même classe que `lot-e4.md`) ; équilibrage (moyennes de la réserve, plafond).
- [ ] **Step 3: Mettre à jour le wiki** : entrée `quetes` de `wiki/data/economie.json` à `dispo`, au format des entrées `dispo` d'E4 (étapes : touche J, réclamer, relancer, attente ; chiffres : 3 et 5, renouvellement à 6 h et le lundi, 1 relance, plafond, ~20 et ~100 cuivres ; bloc `staff` : `quests.json` et `/reload`, `/eco quest template|event|show`, `/eco quests`, garde-fous, réglages) ; entrée des sources (toutes les sources sont en service) et `tableau-de-bord` (`/eco quests`).
- [ ] **Step 4: Mettre à jour `tasks/todo.md`** : ligne E5 cochée avec le résumé et les liens (plan déplacé dans `archive/` par `mv`, vérification) ; restes relevés.
- [ ] **Step 5: Commit et push** par nom de fichier : `test(e5): essai de bout en bout des quetes`, puis `docs(e5): preuve du lot E5`.

---

## Auto-relecture (faite à l'écriture)

- **Couverture du cahier** : 3 + 5 quêtes, tirage pondéré en données, pas deux fois le même modèle, hebdomadaire ≈ 5 ×, pondération par rôle → tâche 1 ; renouvellement à heure fixe chaque jour et chaque lundi, expiration sans pénalité, une relance par jour, réclamation vers la bourse ou l'inventaire, pièces seulement, source comptée, plafond de 1 000 par semaine dit à l'écran → tâches 2 et 5 ; catégories demandées (casse, casse précise, pose, combat) et proposées (récolte, pêche, cuisine, élevage, métier, conserve) → tâche 3, livraison écartée et dite (« Choix de l'auteur du plan ») ; garde-fous et suivi borné des blocs posés → tâche 3 ; modèles et quêtes d'événement par commande → tâche 4 ; écran à touche réglable, notification discrète → tâche 5 ; 77 → tâche 2 (`/eco quests`, ligne existante) ; section 8 : « trois journalières différentes… une relance en donne une autre » → `troisJournalieresDifferentes`, `uneRelanceEnDonneUneAutre` ; « un bloc posé puis cassé ne fait avancer aucune quête » → `unBlocPosePuisCasseNAvanceRien` ; « une quête de métier n'est pas proposée à un autre rôle » → `uneQueteDeMetierResteAuRole` ; scénario d'abus « poser, casser » → tâche 3 et E2E ; scénario des quêtes → tâche 6.
- **Noms** : `QuestCategory`, `QuestTarget`, `QuestTemplate.rewardFor/weightFor`, `QuestTemplates.current/parse/setStaffTemplates/setForTest`, `QuestJobs.REPAIR_MACHINE/register/roleOf`, `QuestGenerator.Series/draw`, `Quest`, `QuestRef`, `QuestBook.refresh/reroll/capRemaining/recordPaid/of/saveTo`, `QuestService.refresh/claim/reroll/markDirty/ClaimResult`, `QuestPeriods.dayKey/weekKey/nextDailyReset/nextWeeklyReset`, `EconomyLedger.questCreated/questClaims/questByPlayer/questByTemplate`, `QuestProgress.add/eligible/cooked/preserved/job/deliver`, `PlacedBlocks.isPlaced/mark/unmark`, `QuestBoard.addTemplate/startEvent/activeEvents` sont les mêmes dans toutes les tâches.
- **Réseau** : deux paquets nouveaux (22 et 23), protocole `8`.
- **Review Focus** : chaque ligne a son test dans la tâche qui possède le code (tâches 1, 2, 3, 4, 5).
