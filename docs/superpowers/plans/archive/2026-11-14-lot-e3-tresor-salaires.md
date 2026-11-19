# Lot E3 : trésor de la ville et salaires publics — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La ville a des finances : un trésor unique, tenu par le maire, qui encaisse (taxe, dépôts, injection de l'équipe), dépense publiquement, paie chaque nuit à minuit, au calendrier réel, les salaires gagnés au temps passé connecté dans un rôle public, et qui peut manquer : un salaire impayé reste dû, visible de tous.

**Architecture:** Trois morceaux posés sur le socle d'E1 et d'E2.
- **Trésor.** `TreasuryAccount` (déjà là, reçoit la taxe) gagne un **registre**, un **retrait à valeur exacte**, et l'**emplacement** de son bloc unique. Le bloc (`economy/town/TreasuryBlock`) n'est qu'une porte : le contenu reste gardé par le serveur, comme le coffre-fort. L'écran est l'écran commun `CoinAccountMenu`/`CoinAccountScreen`.
- **Calendrier et paie.** Une horloge injectable (`EconomyClock`) et un calendrier pur (`EconomyCalendar`) fixent le « jour de paie » (minuit, heure et fuseau réglables) ; ils serviront aussi au lot E6. La paie (`economy/payroll/`) compte côté serveur le temps connecté par rôle, dans un `SavedData` (`Payroll`), clôt chaque jour en salaires, et paie la file des salaires dus par ancienneté, sur le trésor, vers le coffre-fort, sinon l'inventaire, sinon l'attente.
- **Tableau de la mairie.** Un bloc d'affichage (`economy/town/TownBoardBlock`) qui montre le solde public et la dette de la ville, données poussées à tous les clients par un paquet.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, Mixin (aucun nouveau mixin), JUnit 5, GameTests, pack (`-PwithPack`).

**Spec:** `docs/superpowers/specs/archive/2026-11-08-economie-design.md`. Sections utiles :
- sections 2 et 3 (objectifs ; contraintes, dont « Échéances au calendrier réel » : minuit, heure et fuseau réglables, une fois par jour manqué) ;
- section 4 (le maire ; tableau « qui fabrique, qui pose, qui gère » : trésor et tableau de la mairie) ;
- features **73** (trésor) et **75** (salaires), et 87 (« ou en verser au trésor, par commande ») ;
- section 7 (lot E3 : « La ville a des finances : elle encaisse, elle paie, elle peut manquer ») ;
- section 8 (la taxe arrive au trésor ; un salaire impayé reste dû ; un salaire arrive au coffre-fort à minuit, ou attend la connexion d'un joueur sans coffre-fort ; scénario d'acceptation : « l'équipe désigne un maire et injecte de l'argent au trésor ; le maire verse un salaire ») ;
- section 9.

Et `docs/superpowers/specs/2026-11-08-metier-maire-design.md` pour le trésor (104 : dépense avec motif), le bureau (102, **hors lot**), le tableau (103 et 108 : annonces et registre des décisions, **hors lot**, point d'extension seulement) et le salaire du maire (section 4).

Modèles de forme et ce qui est livré : `docs/superpowers/plans/archive/2026-11-12-lot-e1-monnaie.md`, `archive/2026-11-13-lot-e2-commerce.md`, et leurs preuves `docs/verifications/lot-e1.md`, `lot-e2.md` (sections « Passe finale » et « Seconde passe » d'E2 en particulier).

## Global Constraints

Les contraintes globales du lot 6, d'E1 et d'E2 restent valables telles quelles (sections « Global Constraints » de `archive/2026-11-10-lot-6-vie-de-la-ville.md`, `archive/2026-11-12-lot-e1-monnaie.md`, `archive/2026-11-13-lot-e2-commerce.md`), notamment : ASCII dans `gradle.properties` ; aucune classe cliente atteignable du serveur (écrans et rendus dans `economy/client`, appelés par `DistExecutor`) ; textes en `fr_fr.json` avec repli `en_us.json`, accents corrects, jamais « SolarTown » ; `DeferredRegister` ; textures produites par script Python/PIL ; lots de 8 GameTests au plus ; `JoueurDeTest` pour tout joueur d'essai ; montants en cuivres (`long`) affichés par `CoinMath.format` ; une commande de l'équipe exige le niveau 2 ; « l'équipe » = `hasPermissions(2)` ; « vérifier puis engager » ; un client n'est jamais cru sur parole ; chaque écran qui peut contenir de l'argent est déclaré par `MoneyStorageRules.register`.

En plus :
- **Aucune création d'argent par ce lot.** Un salaire est un **transfert** du trésor vers le joueur (`EconomyLedger.transferred`), jamais une source. Le trésor ne s'alimente que par la taxe, les dépôts et l'**injection de l'équipe** (`/eco treasury give`, source déclarée `MoneySource.STAFF`, feature 87). `inCirculation()` ne bouge que par cette injection et son retrait (`MoneySink.STAFF`).
- **Aucune écriture forcée hors de la sauvegarde du monde** (leçon du 2026-11-14, `tasks/lessons.md`). Toutes les nouvelles données (registre du trésor, emplacement du bloc, temps de rôle, dettes, dernier jour réglé, avis non lus) vivent en `SavedData` du monde principal, marquées sales (`setDirty`) à chaque changement, écrites par le jeu avec le reste du monde. Le registre du trésor vit dans le même `SavedData` que son solde.
- **Ne jamais attendre un vrai minuit** : tout ce qui dépend de l'heure lit `EconomyClock.now()` (ou reçoit `now` en paramètre) ; les tests passent leur heure.
- **Services testables sans état global** : la paie et le trésor prennent leurs données en paramètre (`Payroll`, `TreasuryAccount`, `now`) ; les GameTests passent des instances détachées (`load(new CompoundTag())`) ; seul un test d'intégration du tick touche l'horloge et la paie du serveur, dans un lot à lui, avec `@BeforeBatch`/`@AfterBatch` qui remettent l'horloge.
- **Réseau** : identifiants `ID_TREASURY_ACTION = 20` (client → serveur) et `ID_TOWN_FINANCES = 21` (serveur → client) ; protocole de `BenchNetwork` de « 6 » à « 7 », une seule fois, à la tâche 2.
- **Preuve des tests** : `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack` ; lire « All N required tests passed » dans `run-gametest/logs/latest.log` et le rapport JUnit (`build/test-results/test/*.xml`), jamais le seul code de sortie.
- **Tout lancement de Minecraft** (GameTests, client, serveur, E2E, captures) suit la procédure du verrou : section 11 de `C:/Users/killi/.claude/CLAUDE.md` (points 1 à 8) et la note `D:/BDC/Minecraft/Deux sessions d'agents qui lancent Minecraft sur la même machine passent par un fichier de verrou et une file d'attente.md`.
  - Lire `C:\Users\killi\.claude\lancement-minecraft.txt` ; rempli : s'inscrire en fin de `lancement-minecraft-attente.txt` (`<projet> — <heure>`), surveiller en arrière-plan, jamais d'attente au premier plan ; vide : ne le prendre que si la file est vide ou si l'on est en tête, écrire sa ligne (`<projet> — <ce qu'on lance> — <heure>`), retirer sa ligne de la file, lancer ; à la fin, réussite ou échec, **vider** le verrou (ne pas le supprimer) ; file non vide à la libération : se remettre en fin de file.
  - **Se parler entre sessions** (point 8) : en s'inscrivant derrière une autre session, lui écrire qu'on attend, ce qu'on lancera et pour combien de temps, et lui demander son heure de fin ; en prenant le verrou, prévenir celles qui attendent ; en le libérant, prévenir la suivante. Un désaccord se règle par message, jamais en forçant le verrou ; le fichier reste la règle si un message n'arrive pas. Un sous-agent qui ne peut pas écrire à une autre session le signale à la session qui l'a lancé.
  - Fenêtres d'essai sur le second écran (`dev/FenetreSecondEcran` ; propriété `oneforallsurvival.fenetre.secondEcran` pour la fenêtre de chargement) ; lire « [fenetre] second écran » dans le journal.
  - Tout client passe une propriété JVM `oneforallsurvival.*` (souris rendue par `dev/SourisLibre`) ; lire « [souris] rendue » dans son journal. Aucun client laissé ouvert.
  - N'arrêter que ses propres PID ; ne jamais toucher une JVM dont la ligne de commande contient `AXIONENGINE`. Un serveur resté ouvert s'arrête par RCON (`stop`), jamais par un kill.
  - Ne jamais modifier `run/options.txt`. Pas de Prism. Seuls les téléchargements faits par Gradle lui-même sont permis.
  - Ces règles sont recopiées dans le brief de tout sous-agent qui lance Minecraft.
- **Git** : travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, par `git commit -- <chemins>` (jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/`), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Push refusé : `git pull --rebase` puis push.
- **Pas de film à ce lot** (un film unique de l'économie après E6). Captures vérifiées et E2E sur serveur dédié avec le pack, relance comprise.

## Review Focus

1. **L'horloge bouge mal** : changement d'heure (heure d'été), horloge qui recule (NTP, réglage à la main), serveur éteint plusieurs jours ou plus d'un an. Attendu : chaque jour de paie est clos **une seule fois**, jamais un jour qui n'existe pas, rien de payé deux fois, au plus `payMaxMissedDays` jours rattrapés, un avertissement au journal si l'horloge recule. Testé en tâche 3 (`EconomyCalendarTest`) et tâche 4 (`uneHorlogeQuiReculeNeRepaiePas`, `quatreCentsJoursEteintsSontBornes`).
2. **Relance ou arrêt brutal autour de minuit** : le temps du jour survit à une relance avant minuit, n'est pas compté deux fois après, et un jour réglé ne se règle pas une seconde fois si le serveur repart d'une sauvegarde faite juste après la clôture (dernier jour réglé, dettes et temps dans le **même** `SavedData`). Testé en tâches 3 et 4 (`leTempsDuJourSurvitAUneRelance`, `uneClotureRelueNeSeRejouePas`).
3. **Trésor juste insuffisant, plusieurs dettes** : jamais de paiement partiel d'un salaire, ordre d'ancienneté strict (une dette plus jeune ne passe pas devant une plus vieille impayable), l'argent qui arrive au trésor paie la file au tick suivant, une dépense du maire ne paie pas une dette par erreur. Testé en tâche 4 (`pasDePaiementPartiel`, `lesDettesSePaientParAnciennete`, `unDepotPaieLaFileAuTickSuivant`).
4. **Déplacement du trésor** : cassé puis objet ramassé par un autre, reposé par quelqu'un sans droit, second trésor posé pendant qu'un objet traîne, cassé en créatif sans s'accroupir, bloc posé par `/setblock`, explosion, piston. Attendu : un seul trésor, contenu et registre intacts, déplacement noté. Testé en tâche 2.
5. **Destinataire difficile** : joueur absent sans coffre-fort, coffre-fort au plafond, inventaire plein, joueur renommé, rôle changé ou maire nommé en cours de journée. Attendu : le salaire entier arrive une fois, au bon endroit (coffre-fort, sinon inventaire, sinon attente), sous le bon rôle. Testé en tâches 3 et 4.

## Décisions du contrôleur (inscrites telles quelles)

| Sujet | Décision |
|---|---|
| Trésor (73) | **Bloc unique** posé par l'équipe seulement (onglet créatif, aucune recette : objet du cahier qui ne se fabrique pas, par conception). Contenu gardé par le serveur dans `TreasuryAccount` (rattaché, pas recréé). Ouvert seulement par le maire et l'équipe (écran commun `CoinAccountMenu` : déposer, retirer, Change). Second trésor refusé. Le bloc cassé redevient le trésor unique, avec son contenu et son registre, là où on le repose ; en créatif, l'équipe casse (accroupie) puis pose un neuf de l'onglet, qui reprend le trésor unique. Sans emplacement, prélèvements et salaires continuent ; seule l'ouverture est impossible. Déplacement noté au registre. |
| Qui casse le trésor | Le maire, l'équipe, et un **ingénieur**. L'outil de maintenance de l'ingénieur n'existe pas encore (lot du téléphone) : à ce lot, le **rôle** d'ingénieur suffit ; point d'extension nommé (`TownBlockRules.engineerMayMove`) pour exiger l'outil plus tard. |
| Crochetage du trésor | Renvoyé au lot du policier (feature 67), comme coffre-fort et étal. Point d'extension : `TownBlockRules.mayOpenTreasury`. |
| Retraits et dépôts | Un retrait par le maire ou l'équipe est une **dépense publique** : notée au registre du trésor et au registre global comme transfert (`MoneyTransfer.TREASURY_SPEND`), **motif obligatoire** (saisi à l'écran ou par commande). Les dépôts sont notés aussi (`TREASURY_DEPOSIT`). |
| Tableau de la mairie | Bloc posé par le maire (plusieurs) ou l'équipe ; déplaçable par un ingénieur, le maire ou l'équipe. Affiche le solde public du trésor et les salaires dus (dette de la ville). Annonces et registre des décisions : lot du maire (point d'extension, pas de contenu). Lisible de près. Visuel final. |
| Salaires (75) | Par rôle, réglés en config et par commande de l'équipe (`/eco salary …`) ; proposés à la création : policier, médecin, maire (le maire est un statut qui s'ajoute au métier : son salaire s'ajoute). Dus au **temps passé connecté dans le rôle** (taux par heure, au prorata ; un rôle changé en cours de journée compte pour chaque rôle), arrêtés chaque jour à **minuit au calendrier réel** (heure et fuseau réglables), jours manqués serveur éteint traités au démarrage, une fois par jour manqué. Le temps se compte côté serveur et survit aux relances. |
| Paiement | Sur le trésor, à distance : coffre-fort du joueur ; sans coffre-fort, son inventaire s'il est connecté ; sinon l'attente jusqu'à sa connexion (`CommerceDelivery`/`HeldGoods`). Trésor insuffisant : le salaire reste **dû**, jamais payé en partie ; à chaque minuit et quand le trésor reçoit de l'argent, la file est payée **par ancienneté**, salaire entier par salaire entier. Dettes visibles : tableau de la mairie, `/eco stats` (la ligne « Salaires » d'E1 passe en service), message au joueur à la connexion. |
| Création d'argent | Aucune. Injection de l'équipe au trésor ajoutée (E1 ne la permettait pas) : `/eco treasury give`, comptée à la source déclarée de l'équipe (feature 87). |
| Sauvegarde | Aucune écriture forcée hors de la sauvegarde du monde ; nouvelles données en `SavedData`, marquées sales. |
| Lancements | Procédure du verrou (Global Constraints). |
| Film | Aucun. E2E sur serveur dédié avec le pack, relance ciblée économie (`E2E_MONNAIE_SEULE`, étendue à E3), captures vérifiées ; horloge injectable pour minuit. |

## Choix de l'auteur du plan (à valider par Killian)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Calcul du salaire | Chaque seconde connectée dans un rôle payé ajoute `taux` « cuivre-secondes » ; à la clôture, salaire = cuivre-secondes ÷ 3 600, **arrondi vers le bas**, un salaire par rôle et par jour. Un changement de taux compte dès l'instant où il est fait. | Prorata exact, un taux changé à 23 h ne réécrit pas la journée ; aucun cuivre fractionnaire |
| Plafond par jour | 8 h comptées au plus par joueur, par jour et par salaire (`salaryMaxHoursPerDay`, 1 à 24). L'inactivité au clavier n'est pas détectée. | Un joueur laissé connecté toute la nuit ne vide pas le trésor ; le reste relève du RP |
| Valeurs de départ | Policier 60, médecin 60, maire 60 cuivres par heure (1 cuivre par minute). | Lisible ; à équilibrer avec la somme de départ (500) |
| Clés de salaire | L'identifiant du rôle (`policier`, `medecin`, tout rôle créé) et la clé réservée `mayor` pour le statut de maire. `/eco salary set` écrit la valeur de config (`set` + `save` de Forge) : config et commande ne divergent jamais. Ce fichier de config n'est pas une donnée du monde. | Une seule source de vérité ; aucun rôle ne peut s'appeler comme le statut |
| Comptage du temps | Le serveur échantillonne chaque seconde (20 ticks) les joueurs connectés : écart depuis le dernier échantillon, plafonné à 60 s (serveur figé ou machine en veille ne payent pas des heures), découpé à la frontière du jour de paie ; à la connexion l'échantillon part de zéro, à la déconnexion le temps est crédité jusqu'à l'instant. | Exact à la seconde, sans compter le temps hors ligne |
| Premier démarrage, jours manqués | Le premier démarrage avec E3 ne paie rien de rétroactif (dernier jour réglé = la veille). Au plus 400 jours rattrapés (`payMaxMissedDays`), le reste est sauté avec un avertissement ; une horloge qui recule ne règle rien et avertit. Chaque jour réglé laisse une ligne « Clôture du jour » au registre du trésor. | Pas de boucle sans fin ; « une fois par jour manqué » se lit au registre |
| Ordre de la file | Strict : la plus ancienne dette impayable bloque les suivantes, même plus petites. Dans un même jour, ordre du joueur (UUID) puis de la clé. | « Par ancienneté » ; personne ne passe devant |
| Destination d'un salaire | `CommerceDelivery.payToSafeFirst` : coffre-fort (dans la limite du plafond), puis inventaire si connecté, puis attente. Distinct de `payTo` (factures : inventaire d'abord). | Ordre de la décision ; `payTo` reste inchangé pour E2 |
| Dettes payées quand | À chaque clôture, et au tick suivant toute entrée d'argent au trésor (taxe, dépôt, injection), par un drapeau transitoire levé par `TreasuryAccount.credit`, jamais dans la transaction qui verse. | Simple ; une vente à l'étal ne fait pas de paiement au milieu de son engagement |
| Motif d'une dépense | Au clic droit (retrait) sur l'écran du trésor, une petite fenêtre « Motif de la dépense » (1 à 80 caractères, `treasuryReasonMax`) s'ouvre, puis revient à l'écran. Dépôt sans motif ; Change (casser, regrouper) non noté (valeur constante). | Le cadre de 312 × 232 tient déjà juste dans la plus petite interface (320 × 240) |
| Registre du trésor | Dans `TreasuryAccount` (même fichier que le solde), 1 000 lignes au plus (`treasuryLedgerLines`, 100 à 100 000) : taxe, dépôt, dépense, injection, retrait de l'équipe, salaire payé, salaire dû, clôture du jour, pose, casse. Lu par `/treasury log [page]` (maire et équipe) et `/eco treasury log [page]`. | Le lot du maire (bureau, onglet Trésor) le lira tel quel |
| Commandes | `/treasury` (tous : solde, dette de la ville, ce que la ville vous doit, votre temps compté aujourd'hui) ; `/treasury log [page]` et `/treasury board` (maire et équipe) ; `/eco treasury [give|take <montant> <motif> | log [page]]` ; `/eco salary [list | set <clé> <taux> | owed [page]]`. En anglais, comme `/eco`. | Le maire n'est pas opérateur : il a sa racine à lui |
| Pose du trésor | Permise à l'équipe ; au maire et à l'ingénieur seulement s'il n'existe **aucun** trésor vivant (repose après un déplacement). Un bloc de trésor qui n'est pas à l'emplacement noté (posé par `/setblock`) n'ouvre rien et le dit. | L'objet ramassé par un autre ne fait pas un second trésor |
| Créatif | Casser le trésor ou le tableau en créatif exige d'être accroupi, pour tout le monde. | « Pour éviter un accident » |
| Tableau : affichage | Rendu de l'entité de bloc sur la face avant (« Trésor de la ville », solde, « Salaires dus : N (montant) » ou « Aucun salaire dû »), textes mesurés et rétrécis ; clic droit : le même texte dans le chat. Données poussées à tous les clients par `TownFinancesPacket` à la connexion et quand elles changent (vérifié une fois par seconde). | Public par nature, aucun écran à apprendre |
| Tableau : obtention | Onglet créatif, et `/treasury board` (maire ou équipe : un tableau, refusé s'il en porte déjà un). Cassé, il rend son objet. Aucune limite de nombre. | Le maire n'a pas de recette et pose « plusieurs » tableaux |
| Forme des blocs | Trésor : cube plein (pas de `noOcclusion`). Tableau : panneau sur pieds, `noOcclusion()` et `getShape` exacte. | Leçon du 2026-11-10 sur l'occlusion |
| Avis au joueur | À la connexion : « La ville vous doit … (N salaires) » s'il a des dettes, et « Salaires versés pendant votre absence : … (coffre-fort / en attente) ». Connecté : un message à chaque salaire payé ou laissé dû. | « Prévenu, message à la connexion » |
| Relance de l'E2E | E3 passe **en dernier** à la relance (E2, E1, puis E3) ; avant l'arrêt, E3 réécrit les références de relance d'E1 et d'E2 avec l'état lu à sa fin, et leurs relances ignorent la ligne « Salaires dus » de `/eco stats`, que l'étape E3 contrôle exactement. Voir tâche 7. | L'injection de la relance E3 change les comptes ; la première reconnexion d'`Autre` reste celle d'E2 |

## Fichiers

Racine du code : `src/main/java/dev/oneforallsurvival/` (abrégée).

| Fichier | Rôle |
|---|---|
| `economy/TreasuryAccount.java` (modifié), `economy/town/TreasuryLedger.java`, `economy/MoneyTransfer.java` (modifié), `economy/town/TreasuryCommands.java`, `economy/EconomyCommands.java` (modifié), `economy/EconomyConfig.java` (modifié), `economy/stall/StallTrade.java` (modifié : taxe par `credit`) | Socle du trésor |
| `economy/town/TreasuryBlock.java`, `TreasuryBlockEntity.java`, `TreasuryStore.java`, `TreasuryMenu.java`, `TreasuryActionPacket.java`, `TownBlockRules.java`, `economy/client/TreasuryScreen.java`, `economy/client/TreasuryReasonScreen.java` | Bloc et écran du trésor |
| `economy/EconomyClock.java`, `economy/EconomyCalendar.java`, `economy/payroll/Payroll.java` (SavedData), `SalaryMath.java`, `SalaryRates.java`, `RoleTimeTracker.java`, `SalaryCommands.java` | Calendrier, temps de rôle, taux |
| `economy/payroll/PayrollService.java`, `economy/CommerceDelivery.java` (modifié : `payToSafeFirst`) | Paie de minuit, dettes, avis |
| `economy/town/TownBoardBlock.java`, `TownBoardBlockEntity.java`, `TownFinances.java`, `TownFinancesPacket.java`, `economy/client/TownFinancesCache.java`, `economy/client/TownBoardRenderer.java` | Tableau de la mairie |
| `src/test/java/dev/oneforallsurvival/economy/{TreasuryLedgerTest,EconomyCalendarTest,SalaryMathTest,PayrollTest}.java` | JUnit |
| `src/devcontent/java/dev/oneforallsurvival/dev/{TresorSocle,TresorBloc,TempsDeRole,Salaire,TableauMairie}GameTests.java`, `E2eTresor.java`, `E2eTresorServeur.java`, `HorlogeDEssai.java` | Preuves |
| `tools/gen_textures_e3.py` | Textures du trésor et du tableau |

---

### Task 1: Socle du trésor (registre, retrait, injection, transferts)

**Files:**
- Create : `economy/town/TreasuryLedger.java`, `economy/town/TreasuryCommands.java`, `src/test/java/dev/oneforallsurvival/economy/TreasuryLedgerTest.java`, `src/devcontent/.../TresorSocleGameTests.java`.
- Modify : `TreasuryAccount.java`, `MoneyTransfer.java`, `EconomyCommands.java` (`/eco treasury`, ligne du trésor), `EconomyConfig.java`, `stall/StallTrade.java`, `CommerceSocleGameTests` et `EtalVenteGameTests` (appels de `deposit`), langues.

**Interfaces:**
- Consumes : `CommerceMath.payFromBox(Map<Coin, Long>, long, ToLongFunction<Coin>)`, `CoinMath.decompose/format`, `EconomyLedger.created/destroyed/transferred`, `RoleGate.canDoMayorWork`.
- Produces :
  - `MoneyTransfer` gagne, à la fin : `TREASURY_DEPOSIT` (dépôt au trésor), `TREASURY_SPEND` (dépense publique : retrait du trésor par le maire ou l'équipe), `SALARY` (salaire versé par le trésor). Libellés fr/en.
  - `TreasuryLedger` : `record Line(long epochMillis, @Nullable UUID actor, String actorName, Kind kind, long amount, String reason)` ; `enum Kind { TAX, DEPOSIT, SPEND, INJECTION, STAFF_TAKE, SALARY_PAID, SALARY_OWED, DAY_CLOSED, PLACED, REMOVED }` ; `add(Line, int maxLines)` (les plus anciennes effacées d'abord), `page(int) : List<Line>` (10 par page, la plus récente d'abord), `size()`, `pageCount()`, `save() : ListTag`, `static load(ListTag)`.
  - `TreasuryAccount` (même clé `oneforallsurvival_treasury`, ancien fichier relu tel quel) :
    - `credit(Map<Coin, Long> coins, TreasuryLedger.Kind kind, @Nullable UUID actor, String actorName, String reason)` remplace `deposit(Map)` (supprimé ; `StallTrade` passe `Kind.TAX`) ; lève le drapeau transitoire des fonds arrivés ;
    - `count(Coin)`, `room(Coin)` (plafond `CoinStore.MAX_PER_COIN`), `add(Coin, long)`, `remove(Coin, long) : boolean`, `group() : boolean` (pour `TreasuryStore`, tâche 2) ;
    - `payValue(long amount) : boolean` (retire exactement cette valeur, en cassant ses pièces au besoin, par `payFromBox` ; faux et rien ne bouge si le solde ne suffit pas) ;
    - `ledger() : TreasuryLedger`, `note(TreasuryLedger.Line)` ;
    - `position() : Optional<GlobalPos>`, `setPosition(GlobalPos)`, `clearPosition(GlobalPos)` (efface seulement si c'est la même) ;
    - `takeFundsArrived() : boolean` (lit et baisse le drapeau ; non sauvegardé).
  - `EconomyConfig` : `treasuryLedgerLines()` 1 000 (100–100 000), `treasuryReasonMax()` 80 (1–200).
  - Commandes (niveau 2, branchées dans `/eco` par `TreasuryCommands.ecoBranch()`) : `/eco treasury` (solde, emplacement ou « sans emplacement », nombre et total des dettes : 0 à cette tâche) ; `/eco treasury give <montant> <motif>` (crée la somme au trésor : `created(MoneySource.STAFF, montant, auteur, "tresor : <motif>")`, ligne `INJECTION`) ; `/eco treasury take <montant> <motif>` (`payValue`, `destroyed(MoneySink.STAFF, …)`, ligne `STAFF_TAKE` ; refusé sans rien bouger si le solde manque) ; `/eco treasury log [page]`. Racine `/treasury` (niveau 0, enregistrée par `TreasuryCommands.register`) : `/treasury` (tous : solde public) et `/treasury log [page]` (maire et équipe, `RoleGate.canDoMayorWork` ; refus avec message sinon).

- [ ] **Step 1: Écrire `TreasuryLedgerTest`** (JUnit) : `uneLigneSeRelitALIdentique` (save/load, auteur nul compris), `lesPlusAnciennesPartentAuPlafond` (plafond 100, 105 lignes → 100, la première est la 6e), `laPageUnEstLaPlusRecente`, `unMotifTropLongEstCoupe` (200 caractères au plus, comme `EconomyLedger.MAX_REASON`).
- [ ] **Step 2: Écrire `TresorSocleGameTests`** (instances détachées sauf mention ; un lot de 8 au plus) :
  - `laTaxeArriveAuTresorEtAuRegistre` (vente à 10 % sur un étal d'essai, config rendue après : trésor + 3, ligne `TAX` de 3, transfert `SALES_TAX` de 3) ;
  - `payValueCasseSesPiecesEtNeDonneJamaisPlus` (1 or, payer 25 → reste 7 argent 5 cuivre ; payer 200 sur 100 → faux, rien ne bouge) ;
  - `injectionDeLEquipeAuTresor` (`/eco treasury give 500 relance` lu sur la source de commande : trésor + 500, `totalCreated(STAFF)` + 500, ligne `INJECTION` avec le motif) ;
  - `retraitDeLEquipeRefuseSiLeSoldeManque` puis accepté (`totalDestroyed(STAFF)`) ;
  - `leRegistreEtLEmplacementSurviventAuRechargement` (save/load du `SavedData` : solde, lignes, position) ;
  - `unAncienFichierDuTresorSeRelit` (NBT au format d'E2 : seulement `coins` → solde lu, registre vide, sans emplacement) ;
  - `treasuryLogReserveAuMaireEtALEquipe` (un `JoueurDeTest` citoyen : refus ; nommé maire par le `RoleBook` du serveur : accepté ; l'ancien maire rendu en fin de test) ;
  - `ecoStatsMontreLesNouveauxTransferts` (lignes « Dépôt au trésor », « Dépense publique », « Salaire versé » présentes).
- [ ] **Step 3: Lancer** (sous le verrou) `./gradlew --offline test runGameTestServer` ; constater l'échec (compilation ou tests nommés).
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou). Lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Commit** `feat(economy): socle du tresor (registre, retrait, injection)` par nom de fichier, push.

### Task 2: Bloc du trésor (73) — pose unique, ouverture, dépense motivée, déplacement

**Files:**
- Create : `economy/town/TreasuryBlock.java`, `TreasuryBlockEntity.java`, `TreasuryStore.java`, `TreasuryMenu.java`, `TreasuryActionPacket.java`, `TownBlockRules.java`, `economy/client/TreasuryScreen.java` (fine sous-classe de `CoinAccountScreen`, comme `SafeScreen`), `economy/client/TreasuryReasonScreen.java`, `src/devcontent/.../TresorBlocGameTests.java`.
- Modify : `EconomyRegistry` (bloc et objet `treasury`, entité de bloc, menu `treasury`, `MoneyStorageRules.register(TREASURY_MENU)`, `tabItems` ; pas de recette), `bench/BenchNetwork` (`ID_TREASURY_ACTION = 20`, protocole « 7 »), `EconomyClientEvents` (écran), `OneForAllSurvival` (`BreakEvent`), tags (`wither_immune`, `dragon_immune`), table de butin (le bloc se lâche lui-même), langues.

**Interfaces:**
- Consumes : `TreasuryAccount` (tâche 1), `CoinActions.apply`, `CoinRequest`, `CoinAccountMenu`, `RoleGate.canDoMayorWork/isEngineer`, `RoleSavedData`, `EconomyLedger.transferred`.
- Produces :
  - `TownBlockRules` : `mayOpenTreasury(Player) : boolean` (maire ou équipe ; point d'extension du crochetage) ; `mayMove(Player) : boolean` (équipe, maire, ou `engineerMayMove`) ; `engineerMayMove(Player) : boolean` (à ce lot : le rôle d'ingénieur ; **point d'extension** : le lot du téléphone y exigera l'outil de maintenance en main) ; `mayPlaceTreasury(Player, boolean liveTreasuryExists) : boolean` (équipe ; maire et ingénieur seulement si aucun trésor vivant) ; `mayPlaceBoard(Player)` (maire ou équipe ; tâche 5) ; `creativeNeedsCrouch(Player) : boolean` (créatif et pas accroupi). Côté client (rôles inconnus), ces règles laissent faire et le serveur tranche, comme `SafeBlock.mayBreak`.
  - `TreasuryBlock` (orientable, cube plein) : `static boolean hasLiveTreasury(MinecraftServer)` (même lecture que `SafeBlock.hasLiveSafe` : emplacement noté, chunk non chargé = vivant, bloc absent = note périmée) ; `static boolean open(ServerPlayer, BlockPos) : boolean`.
  - `TreasuryStore implements CoinStore` sur `TreasuryAccount` (`usesPurses()` vrai, comme le coffre-fort).
  - `TreasuryMenu extends CoinAccountMenu` (`pos()`, `stillValid` : bloc présent à l'emplacement noté, 8 blocs, `mayOpenTreasury`).
  - `TreasuryActionPacket(BlockPos pos, CoinRequest request, String reason)` ; `static CoinActions.Result apply(ServerPlayer, TreasuryActionPacket)` ; résultat de plus : le retrait sans motif valide (vide, blanc, saut de ligne, au-delà de `treasuryReasonMax`) renvoie `BAD_REQUEST` sans rien bouger.

Règles :
- **Pose** : refusée (avec message et `sendAllDataToRemote`, comme le second coffre-fort) sans joueur, par `FakePlayer`, si `!mayPlaceTreasury`, ou si un trésor vivant existe (message « Le trésor existe déjà en x, y, z »). Acceptée : `setPosition`, ligne `PLACED` (auteur, position) ; journal `INFO`.
- **Ouverture** : seulement le bloc posé à l'emplacement noté et par `mayOpenTreasury` ; sinon un message (« réservé au maire et à l'équipe » ; « ce bloc n'est pas le trésor de la ville » pour un bloc hors emplacement). Titre « Trésor de la ville ».
- **Écran** : celui du coffre-fort (lignes 1 / 10 / 64, Déposer tout, Regrouper, Change). Clic droit (retrait) : `TreasuryReasonScreen` (champ motif, « Confirmer », « Annuler ») puis retour à `TreasuryScreen` ; le paquet porte le motif. Maj + clic droit garde son sens (« autant que possible ») avec le même motif.
- **Notes** : chaque retrait : ligne `SPEND` (montant, motif) et `transferred(TREASURY_SPEND, montant, joueur, "<nom> : <motif>")` ; chaque dépôt : ligne `DEPOSIT` et `transferred(TREASURY_DEPOSIT, …)` ; casser et regrouper : rien. Le montant est la variation de valeur lue avant/après (comme `StallCashPacket.note`).
- **Casse** : dureté -1, résistance maximale, `PushReaction.BLOCK`, tags Wither et dragon ; progression seulement pour `mayMove` (et pas pour `creativeNeedsCrouch`) ; `BreakEvent` annulé sinon, avec un message. Cassé : `clearPosition`, ligne `REMOVED` (auteur, position) ; en survie l'objet tombe, en créatif rien ; le solde ne bouge pas, les pièces ne tombent jamais.

- [ ] **Step 1: Écrire `TresorBlocGameTests`** (gabarit `test/plancher` vidé au-dessus ; équipe par `ServerOpListEntry` niveau 4 retirée en fin de test ; maire et ingénieur par le `RoleBook` du serveur, rendus en fin de test ; ces tests touchent l'emplacement du trésor du serveur : **un lot à eux**, `@BeforeBatch` range l'emplacement noté et `@AfterBatch` le rend) ; deux lots :
  - `lEquipePoseLeTresor` (emplacement noté, ligne `PLACED`) ; `unCitoyenNePosePasLeTresor` ; `unSecondTresorEstRefuse` (l'objet reste dans l'inventaire) ;
  - `seulsLeMaireEtLEquipeOuvrent` (citoyen et ingénieur refusés ; maire et équipe ouvrent) ;
  - `unRetraitSansMotifEstRefuse` (`BAD_REQUEST`, solde inchangé) ; `unRetraitMotiveEstUneDepensePublique` (inventaire + 1 or, ligne `SPEND` avec le motif, transfert `TREASURY_SPEND`, `inCirculation` inchangé) ; `unDepotEstNote` ;
  - `leChangeNeNoteRien` ;
  - `lIngenieurLeMaireEtLEquipeLeCassent` et `unCitoyenNeLeCassePas` ; `casseLeContenuResteEtLaPositionSEfface` (ligne `REMOVED`, solde intact, objet au sol en survie) ;
  - `reposeIlRedevientLeTresor` (même solde, même registre, nouvelle position, ligne `PLACED`) ; `unObjetRamassePasUnCitoyenNeSePosePas` ;
  - `enCreatifIlFautSAccroupir` (non accroupi : refus ; accroupi : cassé, aucun objet ; un trésor neuf de l'onglet reprend le solde) ;
  - `sansEmplacementLaTaxeArriveQuandMeme` ; `unBlocHorsEmplacementNOuvreRien` (`setBlock` direct) ;
  - `explosionEtPistonSansEffet` ; `unAutrePaquetQueLeMenuOuvertEstRefuse` (`NOT_OPEN`, `TOO_FAR`, `NOT_ALLOWED`).
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter**, écrans compris (bulle du clic droit : « Clic droit : retirer N (motif demandé) » ; textes mesurés pour tenir, voir `tasks/lessons.md` du 2026-11-13).
- [ ] **Step 4: Lancer sans puis avec `-PwithPack`** (sous le verrou), lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): bloc du tresor, depense motivee et deplacement`, push.

### Task 3: Calendrier réel, horloge injectable, temps passé dans le rôle, taux

**Files:**
- Create : `economy/EconomyClock.java`, `economy/EconomyCalendar.java`, `economy/payroll/Payroll.java`, `SalaryMath.java`, `SalaryRates.java`, `RoleTimeTracker.java`, `SalaryCommands.java`, `src/test/java/dev/oneforallsurvival/economy/{EconomyCalendarTest,SalaryMathTest,PayrollTest}.java`, `src/devcontent/.../TempsDeRoleGameTests.java`.
- Modify : `EconomyConfig.java`, `EconomyCommands.java` (branche `salary`), `OneForAllSurvival.java` (abonnements : tick du serveur, connexion, déconnexion), langues.

**Interfaces:**
- Consumes : `RoleSavedData.get(server).book()` (`roleOf`, `isMayor`, `role(id)`), `ServerPlayer.getGameProfile().getName()`.
- Produces :
  - `EconomyClock` : `static long now()` (millisecondes), `static void setClock(LongSupplier)`, `static void reset()` ; même forme que `TradeService.setClock`.
  - `EconomyCalendar` (pur) : `static ZoneId zone(String configured)` (vide ou invalide → `ZoneId.systemDefault()`, un invalide avertit une fois) ; `static LocalDate payDay(long epochMillis, ZoneId zone, int hour)` (le jour de paie qui contient cet instant : date locale de l'instant moins `hour` heures) ; `static long boundaryAfter(LocalDate day, ZoneId zone, int hour) : long` (instant où ce jour se clôt) ; `static List<LocalDate> daysToClose(@Nullable LocalDate lastClosed, LocalDate today, int maxDays)` (les jours strictement après `lastClosed` et avant `today`, au plus `maxDays`, les plus récents gardés ; vide si l'horloge recule ou si `lastClosed` est nul).
  - `SalaryMath` (pur) : `static long amount(long rateSeconds)` (÷ 3 600, vers le bas) ; `static long[] split(long fromMillis, long toMillis, long boundaryMillis)` (part avant et après la frontière, en secondes entières, reste en millisecondes reporté) ; `static long creditable(long alreadySeconds, long addSeconds, long maxSeconds)`.
  - `SalaryRates` : `static final String MAYOR_KEY = "mayor"` ; `rateFor(String key) : long` (0 si absent) ; `all() : Map<String, Long>` ; `set(String key, long rate)` (écrit la config). Config : `salaryPerHour` (liste `"policier=60"`, `"medecin=60"`, entrée illisible ignorée avec un avertissement), `mayorSalaryPerHour` 60 (0–1 000 000), `salaryMaxHoursPerDay` 8 (1–24), `payHour` 0 (0–23), `payTimeZone` "" (identifiant IANA, vide = fuseau du système), `payMaxMissedDays` 400 (1–3 650).
  - `Payroll` (`SavedData`, clé `oneforallsurvival_payroll`, `get(MinecraftServer)`, constructeur et `load` accessibles aux tests) :
    - `credit(UUID player, String name, LocalDate day, String key, long seconds, long rate, long maxSeconds)` (ajoute au plus jusqu'au plafond ; cumule secondes et cuivre-secondes ; `setDirty`) ;
    - `secondsFor(UUID player, LocalDate day) : Map<String, Long>` ;
    - `@Nullable LocalDate lastClosed()`, `setLastClosed(LocalDate)` ;
    - (tâche 4) `record Debt(long id, UUID player, String playerName, String key, LocalDate day, long seconds, long amount)` ; `closeDay(LocalDate day) : List<Debt>` (transforme les compteurs de ce jour en dettes de montant non nul, ordre joueur puis clé, ids croissants ; retire les compteurs du jour) ; `debts() : List<Debt>` (par id) ; `removeDebt(long id)` ; `owedTotal() : long` ; `owedTo(UUID) : List<Debt>` ; `addUnseenPaid(UUID, long amount, CommerceDelivery.Destination)` ; `takeUnseenPaid(UUID)` ;
    - NBT : jours en `LocalDate.toString()`, clés par nom.
  - `RoleTimeTracker` (fil du serveur) : `sample(MinecraftServer server, Payroll payroll, long now)` (chaque joueur connecté : écart depuis son dernier échantillon, plafonné à 60 s, découpé par `SalaryMath.split` à la frontière du jour de paie ; crédité à la clé de son rôle si son taux est non nul, et à `mayor` s'il est maire et que ce taux est non nul) ; `onLoggedIn(ServerPlayer, long now)` (départ à zéro) ; `onLoggedOut(ServerPlayer, Payroll, long now)` (crédite jusqu'à l'instant, oublie). Le dernier échantillon de chaque joueur est transitoire (mémoire).
  - Commandes (niveau 2) : `/eco salary list` (clé, libellé du rôle, taux par heure formaté) ; `/eco salary set <clé> <taux>` (clé = identifiant d'un rôle existant ou `mayor`, taux 0–1 000 000 ; refus d'une clé inconnue).

Règles :
- L'échantillon tourne tous les 20 ticks (fin de tick du serveur) avec `EconomyClock.now()`. Le temps compté du jour en cours reste dans `Payroll` (sauvegardé avec le monde) : une relance avant minuit ne perd que ce qui suit la dernière sauvegarde du monde, comme tout le reste.

- [ ] **Step 1: Écrire les JUnit.**
  - `EconomyCalendarTest` : `minuitParDefaut` (23 h 59 → jour J, 00 h 00 → J + 1) ; `heureDePaieA6h` (05 h 59 → J − 1) ; `fuseauExplicite` (même instant, `Europe/Paris` et `America/New_York`, jours différents) ; `passageALHeureDEte` et `retourALHeureDHiver` (`Europe/Paris`, 2026-03-29 et 2026-10-25 : un seul jour de paie chacun, frontière juste) ; `troisJoursManques` (dernier clos J, aujourd'hui J + 3 → J + 1, J + 2) ; `horlogeQuiRecule` (dernier clos J + 2, aujourd'hui J → vide) ; `plafondDesJoursManques` (500 jours, plafond 400 → les 400 plus récents) ; `fuseauInvalideDonneLeSysteme`.
  - `SalaryMathTest` : `soixanteCuivresParHeurePendant90Minutes` (90) ; `arrondiVersLeBas` (59 min 59 s à 60/h → 59) ; `decoupageALaFrontiere` (23 h 59 min 30 s → 00 h 00 min 20 s : 30 et 20) ; `plafondJournalier` (7 h 59 + 5 min, plafond 8 h → 60 s créditées).
  - `PayrollTest` : `leCreditSurvitALaSauvegarde` ; `deuxClesLeMemeJour` (policier et `mayor` séparés) ; `unTauxChangeEnCoursDeJournee` (30 min à 60, 30 min à 120 → 90 à la clôture).
- [ ] **Step 2: Écrire `TempsDeRoleGameTests`** (instances détachées de `Payroll`, `now` passé à la main ; `JoueurDeTest` ; rôles par le `RoleBook` du serveur, rendus en fin de test) :
  - `unPolicierConnecteAccumuleSonTemps` (10 échantillons d'1 s → 10 s sous `policier`) ;
  - `unCitoyenSansTauxNAccumuleRien` ;
  - `leMaireAccumuleDeuxSalaires` (ingénieur maire, taux de l'ingénieur mis à 30 le temps du test : `ingenieur` et `mayor`) ;
  - `unRoleChangeEnCoursDeJourneeComptePourChacun` (5 s policier, puis médecin, 5 s) ;
  - `unEcartDeDeuxHeuresNeCompteQue60s` ;
  - `laDeconnexionCrediteJusquALInstant` et `rienNeCompteHorsLigne` ;
  - `leTempsDuJourSurvitAUneRelance` (crédit, `save` puis `Payroll.load` : mêmes secondes ; nouvel échantillon après la relance : additionné, pas remis à zéro) ;
  - `ecoSalarySetEtList` (`set policier 120` lu par `list` et par la config ; `set inconnu 5` refusé ; `set mayor 0`).
- [ ] **Step 3: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis avec `-PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Commit** `feat(economy): calendrier reel et temps passe dans le role`, push.

### Task 4: Paie de minuit, dettes de la ville, jours manqués (75)

**Files:**
- Create : `economy/payroll/PayrollService.java`, `src/devcontent/.../SalaireGameTests.java`.
- Modify : `Payroll.java` (dettes et avis, voir tâche 3), `CommerceDelivery.java` (`payToSafeFirst`), `EconomyCommands.java` (ligne « Salaires dus » de `stats`, `/eco salary owed`, total des dettes dans `/eco treasury`), `TreasuryCommands.java` (`/treasury` : ce que la ville vous doit, votre temps du jour), `OneForAllSurvival.java` (démarrage, tick, connexion), langues (la clé `eco.stats.later`/`not_yet` est remplacée).

**Interfaces:**
- Consumes : `Payroll`, `RoleTimeTracker.sample`, `EconomyCalendar`, `EconomyClock`, `SalaryMath.amount`, `TreasuryAccount.payValue/note/takeFundsArrived`, `HeldGoods.addPending`, `SafeAccounts.safePos/room/deposit`, `PlayerCoins.receive`, `EconomyLedger.transferred(SALARY, …)`.
- Produces :
  - `CommerceDelivery.payToSafeFirst(MinecraftServer server, UUID payee, long amount) : Destination` : pièces de `decompose(amount)` dans son coffre-fort s'il en a un (dans la limite du plafond), puis dans son inventaire s'il est connecté et vivant, puis en attente ; renvoie la destination la plus lointaine.
  - `PayrollService` (fil du serveur, tout prend `now`) :
    - `closeDueDays(MinecraftServer server, Payroll payroll, TreasuryAccount treasury, long now) : int` (jours clos) : pour chaque jour de `daysToClose(lastClosed, payDay(now), payMaxMissedDays)` dans l'ordre : `closeDay`, ligne `SALARY_OWED` par nouvelle dette, `payDebts`, ligne `DAY_CLOSED` (montant = total des salaires du jour, motif : le jour et le nombre de salaires), `setLastClosed(jour)`. Premier appel (`lastClosed` nul) : `setLastClosed(payDay(now) − 1 jour)`, rien d'autre. Jours au-delà du plafond : `setLastClosed` saute au plus ancien gardé moins un, avec un avertissement.
    - `payDebts(MinecraftServer server, Payroll payroll, TreasuryAccount treasury) : int` (salaires payés) : par id croissant ; la première dette dont `payValue(amount)` échoue arrête la boucle ; sinon `payToSafeFirst`, `removeDebt`, ligne `SALARY_PAID`, `transferred(SALARY, amount, null, "salaire <cle> <jour> -> <nom>")`, avis au joueur.
    - `onServerStarted` (rattrapage des jours manqués : `closeDueDays` avec `EconomyClock.now()`) ; `onServerTick` (tous les 20 ticks, fin de tick : `RoleTimeTracker.sample`, `closeDueDays`, puis `payDebts` si `treasury.takeFundsArrived()`) ; `onLoggedIn` (avis : dettes dues au joueur, salaires versés pendant son absence, `takeUnseenPaid`).
  - `/eco stats` : la ligne « Salaires : pas encore en service » devient « Salaires dus : <total> (<n>) ». `/eco salary owed [page]` : la file, du plus ancien au plus récent (jour, joueur, clé, temps, montant).

Règles :
- **Avis** : connecté, au paiement : « Salaire du <jour> (<rôle>, <h min>) : <montant>, versé dans votre coffre-fort / votre inventaire / en attente » ; à la création d'une dette qui ne se paie pas : « La ville n'a pas pu payer votre salaire du <jour> (<montant>) : il reste dû. » Absent : `addUnseenPaid`, lu à la connexion. Libellé du rôle par `RoleBook.role(key)` ; `mayor` → « Maire » ; rôle supprimé → l'identifiant.
- **Sans emplacement du trésor** : rien ne change, la paie ne lit que `TreasuryAccount`.
- **Aucune création** : un salaire payé retire exactement son montant au trésor et le livre ; une dette n'est pas de l'argent (rien au registre global tant qu'elle n'est pas payée).

- [ ] **Step 1: Écrire `SalaireGameTests`** (instances détachées de `Payroll` et `TreasuryAccount`, `now` à la main ; trois lots de 8 au plus ; un quatrième lot à lui pour le test du tick, voir Global Constraints) :
  - `unSalaireArriveAuCoffreFortAMinuit` (policier connecté 1 h à 60/h avec coffre-fort ; trésor 100 ; minuit → `SafeAccounts.total` + 60, trésor 40, ligne `SALARY_PAID`, transfert `SALARY` de 60, `inCirculation` inchangé) ;
  - `sansCoffreFortConnecteDansLInventaire` ; `sansCoffreFortAbsentAttendSaConnexion` (`PENDING`, puis à la connexion : pièces livrées et avis « versé pendant votre absence ») ;
  - `coffreFortAuPlafondLeResteVaALInventaire` ;
  - `unSalaireImpayeResteDu` (trésor 30 pour un salaire de 60 → rien payé, trésor 30, dette de 60, ligne `SALARY_OWED`, `/eco stats` « Salaires dus : 6 argent (1) », avis au joueur) ;
  - `pasDePaiementPartiel` (trésor 59 pour 60 → 59 restent) ;
  - `lesDettesSePaientParAnciennete` (dettes de J à 60 et de J + 1 à 10, trésor 40 : rien ; trésor 70 : J payée seule ; trésor 80 de plus : J + 1) ;
  - `unDepotPaieLaFileAuTickSuivant` (lot à lui : horloge et paie du serveur ; dette due, `credit` d'une taxe → payée au tick suivant, pas dans l'appel de `credit`) ;
  - `leMaireToucheSonSalaireEnPlusDeSonMetier` (deux dettes ou deux paiements, rôles libellés) ;
  - `deuxJoursManquesDeuxCloturesUneSeuleFois` (dernier clos J, temps crédité à J + 1, démarrage à J + 3 → deux lignes `DAY_CLOSED` (J + 1 avec 1 salaire, J + 2 avec 0), le salaire de J + 1 payé une fois ; second appel : rien) ;
  - `uneClotureRelueNeSeRejouePas` (clôture, `save`/`load` de `Payroll` et `TreasuryAccount`, nouvel appel le même jour : rien) ;
  - `uneHorlogeQuiReculeNeRepaiePas` ; `quatreCentsJoursEteintsSontBornes` (500 jours → 400 clôtures, avertissement) ;
  - `premierDemarrageRienDeRetroactif` ;
  - `sansEmplacementLesSalairesPassent` ;
  - `unJoueurRenommeRecoitSonSalaire` (même UUID, autre nom : payé à l'UUID) ;
  - `aucunePieceCreeeNiDetruite` (après paiements et dettes : `inCirculation` inchangé ; trésor + coffres-forts + inventaires + attente inchangés) ;
  - `laConnexionAnnonceLesDettes`.
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`, puis avec `-PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 5: Commit** `feat(economy): paie de minuit et dette de la ville`, push.

### Task 5: Tableau de la mairie

**Files:**
- Create : `economy/town/TownBoardBlock.java`, `TownBoardBlockEntity.java`, `TownFinances.java`, `TownFinancesPacket.java`, `economy/client/TownFinancesCache.java`, `economy/client/TownBoardRenderer.java`, `src/devcontent/.../TableauMairieGameTests.java`.
- Modify : `EconomyRegistry` (bloc et objet `town_board`, entité de bloc, `tabItems`), `BenchNetwork` (`ID_TOWN_FINANCES = 21`), `EconomyClientEvents` (rendu), `TreasuryCommands` (`/treasury board`), `OneForAllSurvival` (connexion, tick, `BreakEvent`), langues.

**Interfaces:**
- Consumes : `TreasuryAccount.total`, `Payroll.owedTotal/debts`, `TownBlockRules.mayPlaceBoard/mayMove/creativeNeedsCrouch`.
- Produces :
  - `TownFinances` : `record Snapshot(long balance, long owedTotal, int owedCount)` ; `static Snapshot of(MinecraftServer)` ; `static void broadcastIfChanged(MinecraftServer)` (une fois par seconde, envoie à tous si l'instantané a changé depuis le dernier envoi) ; `static void sendTo(ServerPlayer)` (à la connexion) ; `static Component describe(Snapshot)` (le texte du chat).
  - `TownFinancesPacket(long balance, long owedTotal, int owedCount)` (serveur → client) ; côté client, `TownFinancesCache.set/get` (instantané vide tant que rien n'est reçu : le tableau affiche « … »).
  - `TownBoardBlock` (orientable, panneau sur pieds, `noOcclusion()`, `getShape` exacte) ; `TownBoardBlockEntity` (aucune donnée ; **point d'extension** du lot du maire : annonces et registre des décisions s'afficheront ici).
  - `/treasury board` : maire ou équipe ; donne un tableau, refusé s'il en porte déjà un.

Règles :
- **Pose** : `mayPlaceBoard` (maire ou équipe), sans limite de nombre ; sinon refus avec message et inventaire renvoyé au client.
- **Casse** : `mayMove` (ingénieur, maire, équipe), accroupi en créatif ; lâche son objet en survie ; autres : progression nulle et `BreakEvent` annulé. Explosions et pistons sans effet (comme le trésor).
- **Affichage** : face avant, trois lignes (« Trésor de la ville » ; « Solde : <montant> » ; « Salaires dus : <n> (<montant>) » ou « Aucun salaire dû »), chaque ligne mesurée (`font.width`) et rétrécie pour tenir dans la face ; lisible à 4 blocs ; rendu jusqu'à 16 blocs.
- **Clic droit** : tout joueur reçoit `TownFinances.describe` dans le chat (aucun écran).

- [ ] **Step 1: Écrire `TableauMairieGameTests`** :
  - `leMairePoseDesTableaux` (deux) ; `unCitoyenNePosePas` ;
  - `ingenieurMaireEtEquipeLeDeplacent` ; `unCitoyenNeLeCassePas` ; `enCreatifIlFautSAccroupir` ;
  - `leSnapshotSuitLeTresorEtLesDettes` (instances détachées) ;
  - `lePaquetPartAQuandLesComptesChangent` (lot à lui : un dépôt au trésor du serveur → un `JoueurDeTest` reçoit le paquet au plus 20 ticks après ; rien de plus si rien ne change) ;
  - `leClicDroitDitLesComptes` ;
  - `treasuryBoardDonneUnTableauAuMaire` ; `laFormeSuitLeModele` (forme ≠ cube plein ; un bloc voisin garde sa face).
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter** (modèle provisoire acceptable ; le visuel final est en tâche 6).
- [ ] **Step 4: Lancer sans puis avec `-PwithPack`** (sous le verrou), lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): tableau de la mairie`, push.

### Task 6: Visuels finaux et captures

**Files:**
- Create : modèles de bloc et d'objet du trésor (coffre bardé, plaque, cube plein, états orientés) et du tableau (panneau de bois sur deux pieds, cadre, au gabarit de `getShape`) ; `tools/gen_textures_e3.py` et ses textures.
- Modify : `TownBoardBlock.getShape` (union exacte des boîtes du modèle), `DemoVisuels` (une zone à part, hors du champ des lots précédents), `PackGameTests`, langues si un texte change.

Captures (`runDemoVisuels`, sous le verrou), chacune ouverte et vérifiée :
- le trésor seul, de face et de trois quarts, de jour et de nuit ;
- le tableau collé à un mur et entouré de blocs pleins (aucune face voisine manquante), lu de près, avec un solde et une dette aux montants les plus longs (« 9 rubis 9 émeraude 9 or 9 argent 9 cuivre » ; « Salaires dus : 99 (…) ») et sans dette ;
- l'écran du trésor (cadre, panneau Change, inventaire) et la fenêtre « Motif de la dépense » remplie au plus long ;
- la grille des objets du lot à côté de leurs équivalents (coffre-fort, panneau).

- [ ] **Step 1: Écrire les tests de `PackGameTests`** (`niLeTresorNiLeTableauNeSeFabriquent` : aucune recette chargée ne les produit, ni à la table ni à l'établi) et vérifier que `TableauMairieGameTests.laFormeSuitLeModele` suit le modèle final.
- [ ] **Step 2: Lancer avec `-PwithPack`** (sous le verrou), constater l'échec s'il y a lieu.
- [ ] **Step 3: Produire modèles, textures, forme.**
- [ ] **Step 4: Lancer `runDemoVisuels`** (sous le verrou, « [souris] rendue » et « [fenetre] second écran » dans le journal), ouvrir chaque capture et la vérifier ; corriger et relancer tant qu'un texte déborde ou qu'une face manque.
- [ ] **Step 5: Lancer sans puis avec `-PwithPack`**, lire « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(e3): visuels du tresor et du tableau de la mairie`, push.

### Task 7: Preuve de bout en bout, vérification, wiki, suivi

**Files:**
- Create : `dev/E2eTresor.java` (client), `dev/E2eTresorServeur.java` (commandes de console niveau 4 réservées au RCON, sur le modèle d'`E2eCommerceServeur` : joueur simulé `Agent`, ses gestes, lecture du trésor, de la paie et de l'horloge ; `e2etresor horloge <minutes>` règle le décalage de `EconomyClock`), `dev/HorlogeDEssai.java` (sur `ServerAboutToStartEvent`, lit la propriété `oneforallsurvival.e2e.horloge` en minutes et installe l'horloge décalée), `docs/verifications/lot-e3.md`.
- Modify : `dev/E2eClient.java` (phase `TRESOR`), `E2eMonnaie.java` et `E2eCommerce.java` (comparaisons de `/eco stats` à la relance sans la ligne « Salaires dus »), `build.gradle` (`runServer` passe `-Pe2eHorloge` en propriété `oneforallsurvival.e2e.horloge`), `wiki/data/economie.json`, `tasks/todo.md`.

**Ordre des étapes.**
- Passage : `MONNAIE` → `COMMERCE` → `TRESOR` → (passage complet) `PROTECTION`, le lot 5 restant le dernier avant l'arrêt (leçon du 2026-11-11). `E2E_MONNAIE_SEULE` : `MONNAIE` → `COMMERCE` → `TRESOR` → `ARRET`.
- Relance : `COMMERCE` → `MONNAIE` → **`TRESOR` en dernier**. Justification : la première reconnexion d'`Autre` doit rester celle que lit E2 ; l'étape E3 injecte de l'argent au trésor à la relance, ce qui changerait les comptes que relisent E1 et E2. Avant l'arrêt, E3 laisse le trésor à 0 : le rattrapage du démarrage ne paie donc rien et ne change que la ligne « Salaires dus » (le salaire du temps d'`Agent` devient une dette), que les relances d'E1 et d'E2 ignorent et qu'E3 contrôle exactement.
- **Références de relance** : l'étape E3 passe après E1 et E2 et change ce qu'elles relisent (injection, salaires au coffre-fort de `Dev`, transferts). En fin d'étape, juste avant l'arrêt, E3 **réécrit les références** d'E1 et d'E2 (`e2e-monnaie.txt`, `e2e-commerce.txt` : texte de `/eco stats`, total du coffre-fort de `Dev`, ce que porte `Autre` s'il a changé) avec les valeurs lues à cet instant ; leurs relances comparent ainsi l'état d'avant l'arrêt, sans la ligne « Salaires dus ». Ce qui est relu reste le même : seules les valeurs attendues sont mises à jour.

**Essai sur serveur dédié avec le pack** (client `Dev`, `Autre` et `Agent` simulés ; monde neuf, l'ancien rangé hors du dépôt ; joueur amené sur une aire bâtie d'abord, garde « sous la dalle » coupée avant de le ramener ; droits rendus, rôle d'ingénieur et statut de maire rendus, objets propres seulement rangés en sortie ; clics dépendants en pas séparés qui attendent la réponse du serveur ; voir `tasks/lessons.md`) :
- **Pose** : `Dev` opérateur reçoit le trésor par `/give`, le pose par un vrai clic droit ; un second, posé de même, est refusé (objet visible côté client). `/eco treasury give 500 <motif>` par la console.
- **Maire** : `/role mayor set Dev`, `Dev` rendu non opérateur ; il ouvre le trésor par un vrai clic droit, dépose 2 or (clic gauche), retire 1 or par un clic droit et un motif tapé dans la fenêtre du motif ; l'écran et l'inventaire du client suivent ; `/treasury log` montre `PLACED`, `INJECTION`, `DEPOSIT`, `SPEND` avec le motif ; `/eco ledger` montre les transferts. `Autre` (citoyen) ne l'ouvre pas (`NOT_ALLOWED`), ne le casse pas.
- **Tableau** : `Dev` maire prend un tableau par `/treasury board`, le pose par un vrai clic droit ; le cache du client et le rendu affichent le solde ; un clic droit donne le même texte au chat.
- **Minuit** : salaires `policier` et `mayor` à 600 par heure ; `Dev` policier et maire, `Agent` policier sans coffre-fort ; horloge réglée à 2 min de la frontière (`e2etresor horloge`) ; au passage, `Dev` est payé dans son coffre-fort (deux salaires), `Agent` connecté dans son inventaire ; registre `DAY_CLOSED`, `SALARY_PAID`. Puis le maire dépense le reste du trésor (motif), un nouveau minuit : salaires dus, `/eco stats` « Salaires dus », tableau qui affiche la dette, avis à `Dev`. `Dev` est rendu ingénieur et n'est plus maire avant que l'horloge n'avance encore ; `Agent` reste connecté quelques secondes sous son rôle (temps du jour en cours), puis le serveur s'arrête.
- **Déplacement** : `Dev` (ingénieur, non opérateur) casse le trésor par un vrai clic gauche, le repose ailleurs ; solde et registre identiques, lignes `REMOVED` et `PLACED`.
- **Comptes avant l'arrêt** : `/eco stats` : en circulation = avant l'étape + 500 (injection), transferts attendus calculés d'avance.
- **Relance** (`-Pe2eHorloge` = deux jours de plus que le décalage d'avant l'arrêt) : le rattrapage du démarrage a clos deux jours (deux `DAY_CLOSED`, le premier avec le salaire du temps compté d'`Agent` avant l'arrêt : le temps du jour a survécu), les dettes sont relues dans le même ordre, le tableau les affiche ; `/eco treasury give` par la console paie la file par ancienneté au tick suivant (coffre-fort de `Dev`, attente pour `Agent` absent) ; `Agent` se connecte et reçoit son attente et l'avis ; trésor, registre du trésor et emplacement relus.

- [ ] **Step 1: Écrire l'E2E** (client et serveur) et lancer sous le verrou : `runServer -PwithPack` puis `E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack`, nouveau `runServer -PwithPack -Pe2eHorloge=<…>` puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack` ; puis le **passage complet** `runE2eClient -PwithPack` sur monde neuf et sa relance ciblée. Lire « [e2e] bilan : TOUT PASSE » à chaque passage, « [souris] rendue » et « [fenetre] second écran » dans le journal du client ; aucun client laissé ouvert, verrou vidé.
- [ ] **Step 2: Relire les captures de la tâche 6** si un écran a changé depuis ; refaire celles qui ne sont plus vraies.
- [ ] **Step 3: Écrire `docs/verifications/lot-e3.md`**, au format de `lot-e2.md` : commandes, heures et comptes lus ; décisions du lot ; séparer prouvé et non prouvé. Couvre au moins : crochetage du trésor (lot du policier) ; outil de maintenance de l'ingénieur (lot du téléphone : le rôle suffit à ce lot) ; bureau, annonces, registre des décisions, alerte de dépense au-delà d'un seuil (lot du maire) ; vrai minuit jamais attendu (horloge décalée) ; changement d'heure prouvé en JUnit seulement ; inactivité au clavier non détectée ; joueurs simulés ; relance complète du banc toujours en échec sur la couveuse (relance ciblée faite à la place) ; limite de Minecraft d'un salaire versé dans l'inventaire puis arrêt brutal (même fenêtre que celle décrite dans `lot-e2.md`, « Seconde passe ») ; équilibrage des taux.
- [ ] **Step 4: Mettre à jour le wiki** : entrées `tresor` et `salaires` de `wiki/data/economie.json` à `dispo`, au format des entrées `dispo` d'E2 (étapes, chiffres, bloc `staff` : commandes, config, déplacement, créatif accroupi), images des captures ; entrées `injection` (au trésor) et `tableau-de-bord` (« Salaires dus ») mises à jour.
- [ ] **Step 5: Mettre à jour `tasks/todo.md`** : ligne E3 cochée avec le résumé et les liens (plan archivé dans `archive/`, vérification) ; restes relevés.
- [ ] **Step 6: Commit et push** par nom de fichier : `test(e3): essai de bout en bout du tresor et des salaires`, puis `docs(e3): preuve du lot E3`.

---

## Auto-relecture (faite à l'écriture)

- **Couverture du cahier** : 73 → tâches 1 (registre, taxe, injection), 2 (bloc unique, ouverture maire/équipe, déplacement, créatif, sans emplacement), 5 (tableau, solde public) ; 75 → tâches 3 (temps connecté par rôle, taux en config et par commande, calendrier réel) et 4 (minuit, coffre-fort puis inventaire puis attente, dette, jours manqués) ; 87 « verser au trésor » → tâche 1 ; section 8 : « la taxe arrive au trésor » (`laTaxeArriveAuTresorEtAuRegistre`), « un salaire impayé reste dû » (`unSalaireImpayeResteDu`), « un salaire arrive au coffre-fort à minuit, ou attend la connexion » (`unSalaireArriveAuCoffreFortAMinuit`, `sansCoffreFortAbsentAttendSaConnexion`), scénario d'acceptation (maire désigné, injection, salaire versé) → tâche 7. Crochetage, outil de l'ingénieur, bureau et annonces sont renvoyés et dits dans la vérification.
- **Noms** : `TreasuryAccount.credit/payValue/position/takeFundsArrived`, `TreasuryLedger.Kind`, `TownBlockRules`, `EconomyClock`, `EconomyCalendar.payDay/daysToClose/boundaryAfter`, `Payroll.credit/closeDay/debts/lastClosed`, `RoleTimeTracker.sample`, `PayrollService.closeDueDays/payDebts`, `CommerceDelivery.payToSafeFirst`, `SalaryRates.MAYOR_KEY`, `TownFinances.Snapshot` sont les mêmes dans toutes les tâches.
- **Réseau** : identifiants 20 et 21, protocole « 7 » posé une fois (tâche 2).
- **Review Focus** : chaque ligne a son test dans la tâche qui possède le code (tâches 2, 3, 4).
