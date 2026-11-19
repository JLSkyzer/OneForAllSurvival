# Lot E6 : prélèvement automatique et fournisseur d'électricité — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Les factures publiques se paient seules à minuit, en pièces, en entier ou pas du tout (inventaire, puis attente, puis coffre-fort, monnaie rendue), et n'importe quel joueur peut vendre son électricité : un poste de fournisseur fabriqué par l'ingénieur, des compteurs jumelés et acceptés, une facture quotidienne au prix du fournisseur sous le plafond du maire, payée dans la caisse du poste. C'est le **dernier lot** du cahier de l'économie.

**Architecture:** Deux paquets nouveaux, posés sur le socle d'E1 à E5.
- **Prélèvement (86)**, `economy/debit/` : `DebitPlan` (règle pure : quelle source paie, quelle monnaie revient où) ; `DirectDebit` (applique le plan sur l'inventaire par `PlayerCoins`, l'attente par `HeldGoods`, le coffre-fort par `SafeAccounts`) ; `DebitPayee`/`DebitPayees` (le **point d'extension nommé** : un genre de facture publique et le créancier qui reçoit les pièces) ; `DailyDebit` (la clôture de minuit : chaque genre émet ses factures du jour, puis toutes les factures publiques dues sont présentées, de la plus ancienne à la plus récente). Les factures publiques sont des factures d'E2 (`InvoiceRegistry`) d'un genre autre que « privée ».
- **Fournisseur (83)**, `grid/supplier/` : le bloc `power_station` (« poste de fournisseur », bloc en ligne qui compte ce qui sort, comme `InlineEnergyBlockEntity`) ; `PowerSuppliers` (`SavedData` : fournisseurs, caisses, clients, consommation du jour, tarifs, plafond, taxe) ; jumelage au testeur, consentement du client, acceptation au panneau ; le compteur et le disjoncteur du lot 4 **modifiés** (feature 8, section 7 du cahier) : courant bloqué tant que le compteur n'est pas jumelé et accepté, consommation du jour notée à chaque passage, coupure à distance, protection ; `PowerBilling` (le genre `electricity` de `DebitPayees`) ; panneau du poste (écran) ; gestes du maire et de l'équipe (`/power`).

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, MixinGradle (en place, aucun mixin prévu à ce lot), JUnit 5, GameTests, pack (`-PwithPack` : Create 6.0.8, Create: Crafts & Additions, Create: New Age 1.2.0, Create Advanced Electricity). Paquets réseau nouveaux sur `BenchNetwork` (protocole `"8"` → `"9"`, identifiants 24 et 25).

**Spec:** `docs/superpowers/specs/2026-11-08-economie-design.md`. Sections utiles :
- section 2 (règle de conception : la feature dit d'où vient l'argent et où il va ; ici, rien n'est créé : les pièces passent du client au fournisseur ou au trésor ; « un joueur absent qui perd tout son argent sans avoir pu réagir » est un échec) ;
- section 3 (**échéances au calendrier réel**, à minuit, heure et fuseau réglables ; serveur éteint à minuit : ce qui était dû est traité au démarrage, **une fois par jour manqué** ; montants en cuivres) ;
- section 4 (le maire plafonne le prix de l'électricité, peut modifier ou supprimer un fournisseur ; sans maire, l'équipe ; tableau « qui fabrique » : le **poste de fournisseur est fabriqué par l'ingénieur**, posé par n'importe quel joueur, géré par le fournisseur, non volable ; registre) ;
- features **86** (prélèvement) et **83** (fournisseur) en entier, **84** (la caisse d'un poste est un rangement permis des pièces), **72** (l'objet facture), **77** (factures dues au tableau de bord) ;
- section 6 (ingénieur : fabrique les postes, pose et rebranche les compteurs, scellés ; policier et communication : amendes et abonnement du téléphone prélevés, **à venir avec leurs lots**) ;
- section 7 (E6 **modifie le compteur et le disjoncteur de la feature 8**, livrés au lot 4 : jumelage, protection, facture quotidienne, coupure à distance) ;
- section 8 (lignes du prélèvement et du fournisseur, citées dans les tâches ; scénario d'acceptation : « à minuit, sa facture d'électricité est prélevée dans ce coffre-fort ; on relance le serveur ») ;
- section 9 (factures automatiques : inventaire, puis coffre-fort, sinon refus et joueur prévenu ; électricité prélevée chaque jour ; fournisseurs sous plafond du maire, poste, compteurs et disjoncteurs protégés, câbles de n'importe quel mod, ligne vulnérable ; prélèvement jamais partiel ; pièces prélevées dans une caisse que le créancier vide).

Autres sources : feature 8 du cahier archivé de l'électricité (`docs/superpowers/specs/archive/2026-11-06-features-lost-island-design.md`, compteur, relevé au testeur, disjoncteur et scellé) et son code (`grid/MeterBlock*`, `grid/BreakerBlock*`, `grid/MeterLedger`, `grid/SubscriberSavedData`, `repair/TesterItem`, `machine/InlineEnergyBlockEntity`) ; feature 107 du cahier du maire (`2026-11-08-metier-maire-design.md` : liste des fournisseurs, plafond, « un fournisseur au-dessus d'un nouveau plafond y est ramené le lendemain à minuit, et prévenu », suppression avec confirmation, motif, prévenant fondateur et clients). Modèles de forme : plans archivés `docs/superpowers/plans/archive/2026-11-12-lot-e1-monnaie.md` à `2026-11-15-lot-e5-quetes.md` et preuves `docs/verifications/lot-e1.md` à `lot-e5.md` ; « À reprendre » de `lot-e2.md` (l'attente doit compter comme argent porté).

## Global Constraints

Les contraintes globales du lot 6 et d'E1 à E5 restent valables telles quelles (sections « Global Constraints » des plans archivés), notamment : textes en `fr_fr.json` avec repli `en_us.json`, accents corrects, jamais « SolarTown » ; lots de 8 GameTests au plus ; `JoueurDeTest` pour tout joueur d'essai (opérateur par `getOps().add(new ServerOpListEntry(profil, 4, false))`, leçon du 2026-11-12) ; montants en cuivres (`long`) affichés par `CoinMath.format` ; une commande de l'équipe exige le niveau 2 ; un client n'est jamais cru sur parole ; aucune classe cliente atteignable du serveur.

En plus :
- **Ce lot ne crée ni ne détruit aucune pièce.** Tout mouvement est un transfert (`MoneyTransfer`) : le client paie, la caisse du poste et le trésor reçoivent. Aucune source (`MoneySource`) ni aucun puits (`MoneySink`) nouveau. Un prélèvement est **tout ou rien** : le plan est calculé et vérifié en entier (place pour la monnaie comprise) avant de bouger une seule pièce.
- **Aucune écriture forcée hors de la sauvegarde du monde** (leçons du 2026-11-14). Les factures, la date de la dernière clôture et les fournisseurs (caisses comprises) sont des `SavedData` marqués sales ; la consommation du jour vit dans `PowerSuppliers`, pas dans le chunk du compteur, pour être facturable zone déchargée. Ce qui reste est la limite de Minecraft déjà écrite en E2 (pièces retirées d'un inventaire, reçues dans un `SavedData`, puis arrêt brutal avant la sauvegarde) : à écrire telle quelle dans la preuve.
- **Horloge injectable** : tout ce qui dépend du jour prend `long now`, lu par `EconomyClock.now()` à l'appelant ; jours par `EconomyCalendar.payDay(now, EconomyCalendar.configuredZone(), EconomyConfig.payHour())` (minuit par défaut) ; rattrapage borné par `EconomyConfig.payMaxMissedDays()`. Un GameTest qui règle l'horloge a un lot à lui avec `@BeforeBatch`/`@AfterBatch` (`EconomyClock.reset()`).
- **Services testables sans état global** : `DebitPlan` et `PowerBillMath` sont purs (JUnit) ; `DirectDebit`, `DailyDebit` et `PowerBilling` prennent leurs registres en paramètre (instances détachées dans les GameTests).
- **Câbles de n'importe quel mod** : le mod n'ajoute aucun câble. Poste, compteur et disjoncteur gardent la règle du lot 4 (`InlineEnergyBlockEntity` : entrée à l'arrière, sortie devant, seul ce que le voisin accepte passe et est compté). **Aucun mixin n'est prévu** ; si la lecture de la tâche 3 en exige un pour un mod de câbles, il va dans une configuration à part (`oneforallsurvival.<mod>.mixins.json`, `"required": false`, plugin `OptionalModMixinPlugin` avec la plage de version lue au bytecode), jamais de plantage si le mod manque, et la tâche le dit dans son rapport.
- **Preuve des tests** : `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack` ; lire « All N required tests passed » dans `run-gametest/logs/latest.log` et le rapport JUnit (`build/test-results/test/*.xml`), jamais le seul code de sortie.
- **Tout lancement de Minecraft** (GameTests, client, serveur, captures, E2E) suit la procédure du verrou : section 11 de `C:/Users/killi/.claude/CLAUDE.md` (points 1 à 8) et la note `D:/BDC/Minecraft/Deux sessions d'agents qui lancent Minecraft sur la même machine passent par un fichier de verrou et une file d'attente.md`. AXIONENGINE s'en sert souvent.
  - Lire `C:\Users\killi\.claude\lancement-minecraft.txt` (un fichier qui ne contient que des blancs ou un BOM est vide) ; rempli : s'inscrire en fin de `lancement-minecraft-attente.txt` (`<projet> — <heure>`), surveiller en arrière-plan, jamais d'attente au premier plan ; vide : ne le prendre que si la file est vide ou si l'on est en tête.
  - **Relire juste avant d'écrire, et refuser s'il est pris** : la prise passe par un script qui relit le verrou et la file et écrit dans la même commande, et refuse si le verrou n'est pas vide (`[ -s ... ] && exit 1` ou équivalent), jamais un `printf >` nu enchaîné à un lancement (leçon du 2026-11-15). Écrire sa ligne (`OneForAllSurvival — <ce qu'on lance> — <heure>`), retirer sa ligne de la file, lancer.
  - À la fin, réussite ou échec, **vider** le verrou sans le supprimer et **sans BOM** (`: > fichier` en bash, `[IO.File]::WriteAllText($chemin, '')` en PowerShell ; jamais `Set-Content -Encoding utf8`). **Céder le verrou entre deux passes** (sans puis avec le pack, passage puis relance) si la file n'est pas vide : se remettre en fin de file.
  - **Se parler entre sessions** (point 8) : en s'inscrivant derrière une autre session, lui écrire qu'on attend, ce qu'on lancera et pour combien de temps, et lui demander son heure de fin ; en prenant le verrou, prévenir celles qui attendent ; en le libérant, prévenir la suivante. Un désaccord se règle par message, jamais en forçant le verrou ; le fichier reste la règle si un message n'arrive pas. Un sous-agent qui ne peut pas écrire à une autre session le signale à la session qui l'a lancé.
  - Fenêtres d'essai sur le second écran (`dev/FenetreSecondEcran` ; propriété `oneforallsurvival.fenetre.secondEcran`) ; lire « [fenetre] second écran » dans le journal.
  - Tout client passe une propriété JVM `oneforallsurvival.*` (souris rendue par `dev/SourisLibre`) ; lire « [souris] rendue » dans son journal. **Aucun client laissé ouvert.**
  - N'arrêter que ses propres PID (notés au lancement) ; **ne jamais toucher une JVM dont la ligne de commande contient `AXIONENGINE`**. Un serveur resté ouvert s'arrête par RCON (`stop`), jamais par un kill.
  - Ne jamais modifier `run/options.txt`. Pas de Prism. Seuls les téléchargements faits par Gradle lui-même sont permis.
  - Ces règles sont recopiées dans le brief de tout sous-agent qui lance Minecraft.
- **Pas de film à ce lot.** Après la clôture du lot, une tâche à part « film de toute l'économie », lancée seulement avec le feu vert de Killian (fin du plan).
- **Captures des nouveaux écrans** (panneau du poste, facture d'électricité) et du bloc du poste par `runDemoVisuels` (`DemoVisuels`), **ouvertes et regardées** avant d'être citées : contenu le plus long et le plus court (leçons du 2026-11-13 et du 2026-11-14), plus petite interface automatique (320 × 240) et grande ; le poste collé à un mur (leçon du 2026-11-10 : `noOcclusion()` ou forme exacte si le modèle n'occupe pas le cube).
- **E2E sur serveur dédié avec le pack**, passage puis relance ciblée de l'économie (`E2E_MONNAIE_SEULE`) avec horloge décalée (`-Pe2eHorloge`), puis passage complet.
- **Git** : travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, par `git commit -- <chemins>` (jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/` ; jamais `git add -A`, `git mv` ni `git rm` : leçons du 2026-11-08 et du 2026-11-15), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Push refusé : `git pull --rebase` puis push.

## Review Focus

1. **Payer deux fois ou créer de l'argent** : clôture de minuit et démarrage dans le même tic, deux clôtures du même jour (horloge qui recule puis revient), paiement « tout de suite » par l'objet facture pendant qu'elle est présentée, copie de l'objet facture, facture d'un fournisseur supprimé, joueur qui se déconnecte au milieu. Attendu : une facture n'est payée qu'une fois, la somme des pièces (débiteur + caisse + trésor) est conservée à chaque test. Tests : `uneFactureNeSePaieQuUneFois`, `deuxCloturesDuMemeJourNePrelevePasDeuxFois` (tâche 1), `uneHorlogeQuiReculeNeFactureRien` (tâche 4).
2. **Monnaie rendue et sources mêlées** : un rubis seul pour une facture de 3 cuivres, pièces partagées entre bourse, inventaire, attente et coffre-fort, coffre-fort au plafond pour la monnaie, inventaire plein, joueur mort ou déconnecté, bourse posée en attente. Attendu : jamais partiel, monnaie exacte rendue là où elle peut aller, jamais au sol pour un absent. Tests : `DebitPlanTest` et `PrelevementGameTests` (tâche 1).
3. **Le compteur du lot 4 change de règle** : un compteur non jumelé ne laisse plus rien passer ; les GameTests du lot 4 (`CompteurGameTests`, `ReseauxGameTests`) et l'étape `E2eReseau` qui font passer du courant par un compteur doivent le raccorder d'abord, sinon ils échouent à tort. Un compteur raccordé cassé puis reposé ailleurs ne doit pas facturer son ancien client dans une autre maison. Tests : `unCompteurNonJumeleNeLaissePasPasser`, `unCompteurCasseEstDetache` (tâche 3).
4. **Un câble d'un autre mod** : fil suspendu de Crafts & Additions, câble de New Age, câble d'Advanced Electricity ; un câble qui tire (extraction) au lieu de pousser ; chunk déchargé pendant le passage ; plafond par tic. Attendu : la même consommation mesurée qu'avec la liaison directe, sinon un rapport qui dit pourquoi. Tests : `laMesureNeDependPasDuCable` avec le pack (tâche 3).
5. **Droits et consentement** : un client qui veut réenclencher son disjoncteur coupé à distance, un joueur quelconque, un faux joueur (déployeur de Create), une explosion, un fournisseur qui inscrit le nom d'un autre joueur pour le faire payer. Attendu : rien ne bouge sans droit ; personne n'est facturé sans avoir accepté. Tests : `unSansDroitNeTouchePasAuPoste`, `personneNEstFactureSansSonAccord` (tâches 2 et 3).

## Décisions (inscrites telles quelles)

| Sujet | Décision |
|---|---|
| Fabrication du poste | **Seul l'ingénieur** le fabrique (décision de Killian du 2026-11-17, exception à « tout objet de l'économie se fabrique sans métier »). Comme les autres objets d'ingénierie du mod : une recette de table ordinaire, **absente** du tag `oneforallsurvival:table_only`, que `BenchCatalog` retire de la table et range à l'établi d'ingénieur, réservé à l'ingénieur (`BenchOrders`, `EngineerBenchMenu`). |
| L'attente compte comme argent porté | Ordre du prélèvement : **inventaire** (bourses comprises, joueur connecté et vivant) → **attente** du joueur (`HeldGoods`) → **coffre-fort** (`SafeAccounts`, même déconnecté, zone non chargée). Toujours tout ou rien, monnaie rendue. |
| Factures publiques | À ce lot, seules les factures d'**électricité** existent. Amendes (lot du policier), impôt (lot du maire) et abonnement du téléphone (lot du téléphone) viendront par le point d'extension `DebitPayees.register` / `DailyDebit.registerIssuer`, documenté et non codé. Un joueur ne peut pas émettre une facture prélevable : le carnet, `/invoice` et les paquets d'E2 ne créent que des factures privées, jamais prélevées. |
| Payer tout de suite | En utilisant l'objet facture (écran d'E2, bouton « Payer »), qui prend dans l'inventaire ; le téléphone n'existe pas encore. |
| Taxe sur l'électricité | 0 % par défaut, versée au trésor (`TreasuryAccount`), notée au registre. |
| Plafond du maire | Réglé par le maire (`RoleGate.canDoMayorWork` : maire ou équipe) par **commande** (`/power cap`), noté au registre ; pas d'écran (le bureau du maire n'existe pas). |
| Intégration aux mods de câbles | Comme E5 : configuration à part, non requise, plage de version vérifiée, jamais de plantage si le mod manque ; aucune prévue tant que la lecture n'en montre pas le besoin. |
| Film | Aucun à ce lot ; « film de toute l'économie » après la clôture, sur feu vert de Killian. |
| Lancements | Procédure du verrou (Global Constraints). |

## Choix de l'auteur du plan (à valider par Killian)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Sources mêlées | Une seule source qui suffit seule paie, dans l'ordre inventaire → attente → coffre-fort, monnaie rendue à cette source. Si aucune ne suffit seule mais que leur total suffit : tout ce qui est porté et tout ce qui attend est pris, le reste vient du coffre-fort, monnaie rendue au coffre-fort (s'il est au plafond : en attente). Total insuffisant : refus, rien ne bouge. | Jamais partiel, moins de refus pour un joueur qui a l'argent, aucune pièce au sol |
| Attente | Pièces en vrac et contenu des bourses gardées en attente. Les objets non monétaires ne comptent pas. | Fermer la cachette relevée en E2 sans toucher aux objets |
| Pause de l'horloge | La clôture de minuit d'E6 partage la pause d'E3 (`Payroll.paused()`, horloge qui a sauté de plus de 26 h) : en pause, rien n'est facturé ni prélevé ; `/eco salary resume` et `resetday` lèvent la pause pour les deux, et leur texte le dit. Date de la dernière clôture d'E6 à part (dans `InvoiceRegistry`, même fichier que les factures). | Une seule horloge, une seule pause : jamais une paie close et des factures non closes pour le même saut |
| Prix | En cuivres par **100 000 FE** (`powerPriceUnitFe`, réglable), arrondi au cuivre inférieur par facture ; prix d'un poste neuf : 0. Plafond de départ : 20 cuivres par 100 000 FE (`powerDefaultPriceCap`). Abonnement : 0 à `powerMaxSubscription` = 50 cuivres par jour. | Un foyer qui tire ~10 FE/tic pendant 8 h consomme ~5,8 millions de FE : ~58 cuivres par jour à 1 cuivre, du même ordre qu'une heure de salaire de police (60) ; au FE, aucun prix entier ne serait raisonnable |
| Changement de tarif | Prix, abonnement, plafond et taxe changés dans la journée s'appliquent au **jour suivant** : la facture de minuit qui clôt le jour du changement garde l'ancien tarif. Les clients sont prévenus tout de suite. Un prix au-dessus d'un nouveau plafond est ramené au plafond à la même clôture, fournisseur prévenu (cahier du maire, 107). | Personne n'est facturé d'une journée commencée à un autre prix (« la facture suivante ») |
| Demande de raccordement | Le fournisseur ou un ingénieur jumelle le compteur (testeur : poste, puis compteur). Le **débiteur** est le joueur dont le nom est inscrit sur le compteur (écran du lot 4), qui doit être connu du serveur. Ce joueur **demande** le raccordement en cliquant « Accepter » dans le message reçu (ou à sa connexion) : `/power consent <code>`, ou refuse (`/power refuse <code>`). Le fournisseur **accepte** ensuite au panneau. Le courant passe et la facturation commence à l'acceptation. | Sinon un fournisseur pourrait facturer n'importe qui en écrivant son nom sur un compteur |
| Résiliation | Le client peut résilier (`/power resign`) : dernière facture à la minuit suivante, puis compteur détaché (courant coupé). | Un client ne peut pas toucher à son compteur ; sans résiliation, un abonnement relevé le lierait sans issue |
| Disjoncteur | Jumelé par le même geste (testeur : poste, puis disjoncteur) au client du même fournisseur dont le nom d'abonné est le sien. Coupure à distance au panneau : le disjoncteur s'ouvre (état noté chez le fournisseur, appliqué au bloc dès qu'il est chargé) ; sans disjoncteur jumelé, le bouton est grisé. Réenclencher : fournisseur, ingénieur, maire, équipe ; jamais le client. | Lettre du cahier (« en ouvrant son disjoncteur à distance ») |
| Portée de la protection | Un compteur ou un disjoncteur **jumelé** appartient au fournisseur ; non jumelé, il garde les règles du lot 4. Le poste appartient à son fondateur dès la pose. Ayants droit : fondateur, ingénieur, maire, équipe ; jamais le client. Dureté et résistance comme le coffre-fort (`strength(-1, 3600000)`), casse rendue aux ayants droit par `getDestroyProgress`, tout autre bris annulé (`BreakEvent`). | Même mécanisme que le coffre-fort et l'étal, déjà prouvé |
| Compteur cassé | Un compteur jumelé cassé (par un ayant droit) est **détaché** : sa pose ailleurs exige un nouveau jumelage et un nouvel accord du client. Sa consommation du jour reste due à la clôture. | Un compteur déplacé ne doit pas facturer un client pour la maison d'un autre |
| Poste | Un fournisseur par joueur, un poste par fournisseur ; nom de 1 à 24 caractères, unique (sans casse), donné au premier panneau. Le poste cassé lâche un objet qui porte l'identifiant du fournisseur ; reposé, il reprend le même fournisseur (caisse et clients gardés par le serveur) ; un second poste du même fournisseur est refusé. Un poste ne laisse passer le courant que si son fournisseur existe. Nom affiché au clic et dans l'infobulle, pas sur le bloc. | Modèle du trésor (E3) : la caisse n'est jamais dans le bloc |
| Consommation facturée | Chaque passage par un compteur accepté ajoute ce qui a passé à la **consommation du jour** de ce client, tenue par `PowerSuppliers` ; la clôture la facture et la remet à zéro. L'index du bloc et le relevé au testeur du lot 4 restent tels quels. | Facturer à minuit un compteur dans une zone déchargée ; même mesure quel que soit le câble |
| Facture nulle | Une journée à 0 cuivre (prix 0 ou consommation nulle, sans abonnement) n'émet pas de facture. | Pas de papier inutile ; un serveur éteint deux jours émet bien deux factures dès qu'il y a un abonnement |
| Facture refusée | Reste due, présentée à chaque minuit ; débiteur prévenu (tout de suite s'il est connecté, sinon à sa connexion) avec un lien « [Recevoir la facture] » (`/invoice copy <id>`, débiteur seulement, facture due seulement) ; fondateur prévenu de même, colonne « impayés » au panneau. | Le papier n'est qu'une vue (E2) : une copie ne paie jamais deux fois |
| Taxe | Retenue sur ce que reçoit la caisse, arrondie au cuivre inférieur, versée au trésor (`MoneyTransfer.POWER_TAX`, ligne `TreasuryLedger.Kind.POWER_TAX`) ; réglée par le maire ou l'équipe (`/power tax`, 0 à `powerTaxMaxPercent` = 20), au jour suivant. | Comme la taxe sur les ventes d'E2 |
| Fournisseur supprimé | Par le maire ou l'équipe (`/power remove <nom> <motif>`, puis `/power remove <nom> confirm` dans les 60 s) : clients détachés et prévenus, fondateur prévenu, factures dues **annulées** (statut `CANCELLED`), caisse versée au fondateur (`CommerceDelivery.payToSafeFirst`), ligne au registre. | Aucune pièce envoyée nulle part ni détruite |
| La ville fournisseur | Pas de cas à part : le maire fonde un fournisseur et verse la caisse au trésor (dépôt d'E3). Le maire ou l'équipe peut changer le fondateur (`/power owner`). | Lettre du cahier (« comme les autres ») sans code de plus |
| Écart du poste | Panneau : sortie du poste moins somme des compteurs, pour aujourd'hui et pour hier ; en rouge au-delà de 10 % et de 100 000 FE (`powerGapWarnPercent`). | Signaler une perte ou un piquage sans fausse alarme pour un petit réseau |
| Registre | Chaque prélèvement payé : transfert `MoneyTransfer.PUBLIC_BILL` (motif : genre, facture, source) ; taxe : `POWER_TAX` ; refus : compteur `refusedDebits` du registre et ligne de journal ; changements de tarif, plafond, taxe, propriétaire et suppression : lignes du journal. `/eco stats` : factures publiques dues (nombre, total) et électricité (factures payées, taxe). | Section 4 (« chaque mouvement est noté ») et 77 |

## Fichiers

Racine du code : `src/main/java/dev/oneforallsurvival/` (abrégée) ; essais : `src/devcontent/java/dev/oneforallsurvival/dev/` (abrégé `dev/`) ; JUnit : `src/test/java/dev/oneforallsurvival/`.

| Fichier | Rôle |
|---|---|
| `economy/debit/DebitPlan.java`, `DirectDebit.java`, `DebitPayee.java`, `DebitPayees.java`, `DailyDebit.java`, `economy/invoice/InvoiceRegistry.java`, `InvoiceService.java`, `InvoiceCommands.java`, `economy/HeldGoods.java`, `MoneyTransfer.java`, `EconomyLedger.java`, `EconomyCommands.java` (modifiés), JUnit `economy/debit/DebitPlanTest.java`, `dev/PrelevementGameTests.java`, `dev/PayeurDEssai.java` | Prélèvement générique (86) |
| `grid/supplier/PowerStationBlock.java`, `PowerStationBlockEntity.java`, `PowerSupplier.java`, `PowerClient.java`, `PowerSuppliers.java`, `PowerRules.java`, `PowerTillStore.java`, `grid/GridRegistry.java` (modifié), recette `power_station.json`, modèle, texture, butin, `dev/PosteGameTests.java` | Poste, fournisseurs, caisse, protection du poste |
| `grid/supplier/PowerPairing.java`, `PowerCommands.java` (partie joueurs), `grid/MeterBlock.java`, `MeterBlockEntity.java`, `BreakerBlock.java`, `BreakerBlockEntity.java`, `repair/TesterItem.java` (modifiés), `dev/RaccordementGameTests.java`, `dev/FournisseurDEssai.java`, `dev/CompteurGameTests.java`, `ReseauxGameTests.java`, `E2eReseau.java`, `PackGameTests.java` (modifiés) | Jumelage, consentement, acceptation, coupure, protection des compteurs et disjoncteurs, mesure par câble |
| `grid/supplier/PowerBillMath.java`, `PowerBilling.java`, JUnit `grid/supplier/PowerBillMathTest.java`, `dev/FactureElectriciteGameTests.java` | Facture quotidienne |
| `grid/supplier/PowerPanelPacket.java`, `PowerActionPacket.java`, `grid/supplier/client/PowerPanelScreen.java`, `PowerPanelLayout.java`, `bench/BenchNetwork.java` (modifié), JUnit `grid/supplier/client/PowerPanelLayoutTest.java`, `dev/DemoVisuels.java` (modifié) | Panneau du poste |
| `grid/supplier/PowerCommands.java` (partie maire et équipe), `economy/town/TreasuryLedger.java`, `economy/payroll/SalaryCommands.java` (modifiés), `dev/MaireElectriciteGameTests.java` | Gestes du maire et de l'équipe, tableau de bord |
| `dev/E2eElectricite.java`, `dev/E2eElectriciteServeur.java`, `dev/E2eClient.java` (modifié), `docs/verifications/lot-e6.md`, `wiki/data/economie.json`, `tasks/todo.md` | Preuve |

`OneForAllSurvival.java` reçoit les abonnements de chaque tâche ; `EconomyConfig.java` les réglages nommés ; langues à chaque tâche.

---

### Task 1: Prélèvement générique (feature 86)

**Files:**
- Create : `economy/debit/DebitPlan.java`, `DirectDebit.java`, `DebitPayee.java`, `DebitPayees.java`, `DailyDebit.java`, JUnit `economy/debit/DebitPlanTest.java`, `dev/PrelevementGameTests.java`, `dev/PayeurDEssai.java` (genre `test` enregistré par les essais seulement).
- Modify : `economy/invoice/InvoiceRegistry.java`, `InvoiceService.java`, `InvoiceCommands.java`, `economy/HeldGoods.java`, `economy/MoneyTransfer.java`, `EconomyLedger.java`, `EconomyCommands.java` (`/eco stats`), `OneForAllSurvival.java`, langues.

**Interfaces:**
- Consumes : `PlayerCoins.held/remove/giveOrDrop/take`, `CoinMath.makeChange/decompose/valueOf`, `SafeAccounts.balance/withdraw/deposit/room`, `HeldGoods`, `CommerceDelivery.Destination`, `EconomyCalendar`, `EconomyClock`, `Payroll.paused()`, `EconomyConfig.payHour/payMaxMissedDays`.
- Produces :
  - `InvoiceRegistry` : `Invoice` gagne `String payee` (`""` : facture privée d'E2 ; sinon `<genre>:<référence>`, ex. `electricity:3f2a9c1b`) et le statut `CANCELLED` ; `boolean isPublic()` ; `Invoice issuePublic(String kind, String ref, UUID creditorId, String creditorName, UUID debtor, String debtorName, long amount, String reason, long now)` (aucun chemin joueur n'y mène) ; `List<Invoice> duePublic()` (ordre d'émission) ; `boolean cancel(UUID id, long now)` ; `@Nullable LocalDate lastDebitDay()` / `void setLastDebitDay(LocalDate)` ; lecture des anciennes sauvegardes sans `payee` = privée.
  - `HeldGoods` : `EnumMap<Coin, Long> pendingCoins(UUID owner)` (pièces en vrac + contenu des bourses en attente) ; `void takeCoins(UUID owner, Map<Coin, Long> coins)` (en vrac d'abord, puis bourses) ; `void addCoins(UUID owner, Map<Coin, Long> coins)`.
  - `record DebitPlan(Source single, EnumMap<Coin,Long> fromHeld, EnumMap<Coin,Long> fromPending, EnumMap<Coin,Long> fromSafe, EnumMap<Coin,Long> change, Source changeTo)` avec `enum Source { HELD, PENDING, SAFE, MIXED }` ; `static Optional<DebitPlan> plan(@Nullable EnumMap<Coin,Long> held, EnumMap<Coin,Long> pending, EnumMap<Coin,Long> safe, long amount)` (`held` nul : joueur absent ou mort) — règle du tableau « Sources mêlées ».
  - `interface DebitPayee` : `boolean exists(MinecraftServer, String ref)` ; `void receive(MinecraftServer, Invoice, long amount)` (verse les pièces, note le paiement là où la facture a été émise) ; `void refused(MinecraftServer, Invoice)` (prévient le créancier) ; `Component describe(Invoice)` (motif lisible, traduit chez le client).
  - `DebitPayees` : `static void register(String kind, DebitPayee)` ; `static Optional<DebitPayee> get(String kind)`. Javadoc : **point d'extension** des amendes et cautions (lot du policier, créancier : trésor et casier), de l'impôt (lot du maire, trésor) et de l'abonnement du téléphone (lot du téléphone, caisse de l'antenne) ; rien de tout cela n'est codé ici.
  - `DirectDebit` : `enum Outcome { PAID_HELD, PAID_PENDING, PAID_SAFE, PAID_MIXED, REFUSED, CANCELLED, NOT_DUE }` ; `static Outcome present(MinecraftServer, InvoiceRegistry, Invoice, long now)` (créancier disparu : `cancel`, `CANCELLED` ; plan vide : `refused` du créancier, avis au débiteur, compteur `refusedDebits` ; sinon : pièces retirées selon le plan, monnaie rendue, `markPaid`, `receive`, transfert `MoneyTransfer.PUBLIC_BILL`) ; `static PayResult payNow(ServerPlayer, Invoice)` (inventaire seulement, appelé par `InvoiceService.pay` pour une facture publique).
  - `DailyDebit` : `interface DayIssuer { void issue(MinecraftServer, LocalDate day, long now); }` ; `static void registerIssuer(String kind, DayIssuer)` (second point d'extension, même Javadoc) ; `static int closeDueDays(MinecraftServer, InvoiceRegistry, long now)` : pour chaque jour fini depuis `lastDebitDay` (au plus `payMaxMissedDays`, les plus récents) : chaque émetteur émet ses factures du jour, puis `present` de chaque facture publique due (plus ancienne d'abord), puis le jour est noté clos ; rien si `Payroll.paused()` ; premier appel : la veille notée close ; horloge qui recule : rien. Appelé au tic (après la vérification d'horloge de la paie) et au démarrage (`ServerStartedEvent`, après la paie).
  - `InvoiceCommands` : `/invoice copy <id>` (débiteur seulement, facture publique due seulement : l'objet facture d'E2 dans l'inventaire, sinon en attente).
  - Avis : au débiteur (`message.oneforallsurvival.debit.refused` avec le lien de copie ; payé : montant et source), à la connexion pour les refus non lus ; `/eco stats` : « Factures publiques dues : N (total) ».

- [ ] **Step 1: Écrire `DebitPlanTest`** : `linventaireSeulPaie` ; `sansInventaireLAttentePaie` ; `sansAttenteLeCoffreFortPaie` ; `aucuneSourceSeuleMaisLeTotalSuffit` (tout porté et tout en attente pris, reste au coffre-fort, monnaie au coffre-fort) ; `unRubisPourTroisCuivresRendLaMonnaie` (9 999 → monnaie en `CoinMath.decompose`, exacte) ; `absentLInventaireNeCompteJamais` ; `totalInsuffisantRefuse` (plan vide) ; `jamaisPartiel` (somme prise − monnaie = montant, pour 1 000 montants tirés).
- [ ] **Step 2: Écrire `PrelevementGameTests`** (genre `test` de `PayeurDEssai`, qui compte ce qu'il reçoit ; registre et comptes détachés ; joueurs `JoueurDeTest` ; lots de 8 au plus) :
  - `dabordLInventaireBourseComprise` ; `puisLAttente` (pièces et bourse pleine en attente) ; `puisLeCoffreFort` ;
  - `deconnecteLeCoffreFortPaieMemeZoneNonChargee` (coffre-fort posé loin, chunk déchargé, joueur retiré de la liste) ;
  - `sansAssezRienNeBougeEtLaFactureResteDue` (comptes identiques, statut `DUE`, avis au débiteur et `refused` du créancier) ;
  - `uneFactureDueEstPresenteeAChaqueMinuit` (horloge à 23 h 59 puis 0 h 01 deux jours de suite : présentée deux fois, payée la seconde après un dépôt) ;
  - `elleSePaieAussiEnUtilisantLObjet` (`InvoiceService.pay` : inventaire, créancier crédité, reçu dans la case) ; `uneCopieNeSePaiePas` ;
  - `uneFactureNeSePaieQuUneFois` (présentation et paiement par l'objet dans le même tic) ; `deuxCloturesDuMemeJourNePrelevePasDeuxFois` ;
  - `unJoueurNePeutPasEmettreDeFacturePrelevable` (facture du carnet d'E2 due à minuit : jamais présentée) ;
  - `unCreancierDisparuAnnuleLaFacture` ; `laPauseDeLaPaieArreteLePrelevement` ;
  - à chaque test, la somme (débiteur + attente + coffre-fort + créancier) est conservée.
- [ ] **Step 3: Lancer** `./gradlew --offline test` puis (sous le verrou) `runGameTestServer` ; constater l'échec.
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou, cédé entre les deux si quelqu'un attend), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Commit** `feat(economy): prelevement automatique des factures publiques`, push.

### Task 2: Poste de fournisseur, registre des fournisseurs et caisse

**Files:**
- Create : `grid/supplier/PowerStationBlock.java`, `PowerStationBlockEntity.java`, `PowerSupplier.java`, `PowerClient.java`, `PowerSuppliers.java`, `PowerRules.java`, `PowerTillStore.java`, `src/main/resources/data/oneforallsurvival/recipes/power_station.json`, table de butin, blockstate, modèle et texture finals, `dev/PosteGameTests.java`.
- Modify : `grid/GridRegistry.java` (bloc, objet, `POWER_STATION_BE`), `economy/MoneyStorageRules.java` si la caisse doit y être déclarée (feature 84 : rangement permis), `EconomyConfig.java`, `OneForAllSurvival.java`, langues.

**Interfaces:**
- Consumes : `InlineEnergyBlockEntity`, `CoinAccountMenu` et `CoinStore` (même écran que le coffre-fort et la caisse de l'étal), `RoleGate.isEngineer/isMayor`, `SafeBlock` (modèle de protection), `BenchCatalog` (la recette part à l'établi), `CommerceDelivery`.
- Produces :
  - `record PowerSupplier(String id, String name, UUID founder, String founderName, long price, long subscription, @Nullable Long nextPrice, @Nullable Long nextSubscription, @Nullable GlobalPos station, EnumMap<Coin,Long> till, long outToday, long outYesterday, long metersYesterday)` ; `record PowerClient(UUID meterSerial, String supplierId, UUID player, String playerName, String subscriber, State state, long feToday, long feYesterday, Set<UUID> breakers, boolean cut)` avec `enum State { AWAITING_CONSENT, AWAITING_ACCEPT, ACTIVE, RESIGNING }`.
  - `PowerSuppliers extends SavedData` (clé `oneforallsurvival_power`) : `static PowerSuppliers get(MinecraftServer)` ; `Result found(UUID founder, String founderName, String name)` (un par joueur, nom unique) ; `Optional<PowerSupplier> byId/byName/byFounder` ; `Optional<PowerClient> clientOf(UUID meterSerial)` ; `boolean passes(UUID meterSerial)` (client `ACTIVE` ou `RESIGNING`, fournisseur existant ; lecture O(1)) ; `void recordOut(String supplierId, int fe)` ; `void recordMeter(UUID meterSerial, int fe)` ; caisse : `till/deposit/withdraw` ; `long priceCap()`, `int taxPercent()` et leurs valeurs du lendemain ; chaque changement `setDirty()`.
  - `PowerStationBlockEntity extends InlineEnergyBlockEntity` : `isOpen()` vrai seulement si son fournisseur existe ; chaque FE passé → `recordOut`.
  - `PowerRules` : `static boolean mayManage(Player, PowerSupplier)` (fondateur, ingénieur, maire, équipe ; côté client : vrai, le serveur tranche) ; `static boolean isClientOf(...)` ; utilisé par le poste, le compteur et le disjoncteur.
  - Réglages : `powerPriceUnitFe` (100 000), `powerDefaultPriceCap` (20), `powerMaxSubscription` (50), `powerTaxMaxPercent` (20), `powerGapWarnPercent` (10).
  - Recette : `crafting_shaped`, conditionnée à `create` comme celle du compteur (compteur, disjoncteur, deux bobines, boîtier en laiton, plaques de cuivre, redstone) ; absente de `table_only`.

- [ ] **Step 1: Écrire `PosteGameTests`** :
  - `lePosteNeSeFabriqueQuALEtabli` (la recette n'est pas dans le `RecipeManager` après le catalogue, elle est dans `BenchCatalog.current()` ; un citoyen ne peut pas commander à l'établi) ;
  - `poserFondeLeFournisseur` (nom donné, un seul par joueur, nom pris refusé) ;
  - `lePosteCompteCeQuiSort` (source d'essai → poste → charge : `outToday` = FE reçus par la charge) ; `sansFournisseurLePosteNeLaissePasser` ;
  - `unSansDroitNeTouchePasAuPoste` (citoyen : ni ouvrir ni casser ; fondateur, ingénieur, maire, équipe : oui ; un déployeur de Create avec le pack dans `PackGameTests`) ;
  - `leFondateurVideLaCaisse` (écran commun ; caisse gardée par le serveur, zone déchargée) ;
  - `casseEtReposeGardeLeFournisseur` (objet avec l'identifiant ; second poste refusé) ;
  - `lesDonneesSurviventALaRelance` (sauvegarde et relecture de `PowerSuppliers`).
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter** (modèle et texture finals ; `noOcclusion()` ou forme exacte si le modèle n'occupe pas le cube).
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(power): poste de fournisseur et registre des fournisseurs`, push.

### Task 3: Compteur et disjoncteur raccordés (feature 8 modifiée)

**Files:**
- Create : `grid/supplier/PowerPairing.java`, `PowerCommands.java` (`/power consent|refuse|resign|list`), `dev/RaccordementGameTests.java`, `dev/FournisseurDEssai.java` (`raccorder(helper, compteur, joueur)` : fournisseur, jumelage, accord et acceptation en un appel).
- Modify : `grid/MeterBlock.java`, `MeterBlockEntity.java`, `BreakerBlock.java`, `BreakerBlockEntity.java`, `repair/TesterItem.java`, `dev/CompteurGameTests.java`, `ReseauxGameTests.java`, `E2eReseau.java`, `PackGameTests.java`, `OneForAllSurvival.java`, langues.

**Interfaces:**
- Consumes : `PowerSuppliers`, `PowerRules`, `MeterBlockEntity.serial()/subscriber()`, `SubscriberHolder`, `RoleGate`, `GameProfileCache`.
- Produces :
  - `PowerPairing` : testeur sur un poste → le testeur retient le fournisseur 60 s (NBT de l'objet, message) ; puis sur un compteur → `pairMeter(ServerPlayer, PowerSupplier, MeterBlockEntity)` (ayant droit seulement ; abonné inscrit et joueur connu ; client `AWAITING_CONSENT`, message cliquable au joueur, code à usage unique) ; sur un disjoncteur → `pairBreaker(...)` (client du même fournisseur au même nom d'abonné). Sans fournisseur retenu, le testeur garde le relevé du lot 4.
  - `/power consent <code>` → `AWAITING_ACCEPT` et avis au fondateur ; `/power refuse <code>` → client retiré ; `/power resign` → `RESIGNING` ; `/power list` → ses raccordements, prix et abonnement.
  - `MeterBlockEntity.isOpen()` : `PowerSuppliers.passes(serial())` ; chaque FE passé → `recordMeter`. Casse d'un compteur jumelé : client détaché (`onRemove`), sa consommation du jour gardée pour la clôture.
  - `BreakerBlockEntity.isOpen()` : `CLOSED` et pas `cut` ; un disjoncteur chargé dont le client est coupé passe à `CLOSED = false` (au chargement et à la coupure).
  - Protection d'un compteur ou d'un disjoncteur jumelé : ouvrir, inscrire, basculer, sceller, casser réservés à `PowerRules.mayManage` (le scellé reste un geste d'ingénieur) ; message clair au client et aux autres. Non jumelés : règles du lot 4 inchangées.

- [ ] **Step 1: Lire et noter dans le rapport de la tâche** (jars du cache de Gradle, `javap -c`, et un essai GameTest avec le pack) : comment le fil suspendu et le connecteur de Crafts & Additions, le câble de Create: New Age 1.2.0 et celui de Create Advanced Electricity livrent l'énergie à un bloc qui expose `ForgeCapabilities.ENERGY` sur une seule face (poussée par `receiveEnergy`, ou extraction), et s'ils se branchent à la face de sortie qui n'expose rien. Rappel : au lot 4, seul Crafts & Additions a fait passer du courant dans le délesteur (`docs/verifications/lot-4.md`, « Non prouvé »). Un câble qui ne passe pas par FE, ou qui exige une face qui n'existe pas, s'écrit dans le rapport avec la solution proposée (adaptateur du mod, intégration à part selon les Global Constraints) avant d'être codé.
- [ ] **Step 2: Écrire `RaccordementGameTests`** :
  - `unCompteurNonJumeleNeLaissePasPasser` ; `jumeleSansAccordDuClientRienNePasse` ; `accordeMaisNonAccepteRienNePasse` ; `accepteLeCourantPasseEtSeCompte` (consommation du jour = FE reçus par la charge) ;
  - `personneNEstFactureSansSonAccord` (nom d'un autre joueur inscrit : il refuse, client retiré ; joueur inconnu : jumelage refusé) ;
  - `seulUnAyantDroitJumelle` (citoyen et client refusés) ; `leDisjoncteurSeJumelleAuMemeClient` ;
  - `lePosteCoupeADistance` (disjoncteur jumelé ouvert, rien ne passe ; le client ne peut pas le réenclencher ; le fournisseur, si) ;
  - `unSansDroitNeTouchePasAuCompteurNiAuDisjoncteur` (client compris : ni ouvrir, ni basculer, ni casser ; fournisseur, ingénieur, maire, équipe : oui) ;
  - `unCompteurCasseEstDetache` ; `unCompteurNonJumeleGardeLesReglesDuLot4` ; `laResiliationCoupeApresLaDerniereFacture` (avec la tâche 4 : ici, état `RESIGNING` et courant qui passe encore).
- [ ] **Step 3: Écrire le test du pack** dans `PackGameTests` (seulement avec `-PwithPack`) : `laMesureNeDependPasDuCable` — même source, même charge d'essai, même durée, poste → câble → compteur → disjoncteur → charge, une fois par câble (fil de Crafts & Additions par `ReseauxDev`, câble de New Age, câble d'Advanced Electricity) et une fois en liaison directe : les quatre consommations du jour sont égales entre elles et à ce que la charge a reçu ; un déployeur de Create ne casse ni n'ouvre un compteur jumelé.
- [ ] **Step 4: Mettre à jour les essais du lot 4** qui font passer du courant par un compteur (`CompteurGameTests`, `ReseauxGameTests`, `E2eReseau`) : `FournisseurDEssai.raccorder` avant le passage ; leurs assertions ne changent pas.
- [ ] **Step 5: Lancer** (sous le verrou), constater l'échec des nouveaux tests.
- [ ] **Step 6: Implémenter.**
- [ ] **Step 7: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed ».
- [ ] **Step 8: Commit** `feat(power): raccordement des compteurs et des disjoncteurs`, push.

### Task 4: Facture quotidienne d'électricité

**Files:**
- Create : `grid/supplier/PowerBillMath.java`, `PowerBilling.java`, JUnit `grid/supplier/PowerBillMathTest.java`, `dev/FactureElectriciteGameTests.java`.
- Modify : `PowerSuppliers.java` (clôture du jour), `economy/MoneyTransfer.java` (`POWER_TAX`), `economy/town/TreasuryLedger.java` (`Kind.POWER_TAX`), `OneForAllSurvival.java`, langues (motif de la facture, avis).

**Interfaces:**
- Consumes : `DebitPayees.register`, `DailyDebit.registerIssuer`, `InvoiceRegistry.issuePublic`, `TreasuryAccount.credit`, `PowerSuppliers`.
- Produces :
  - `PowerBillMath` : `static long energyCost(long fe, long price, long unitFe)` (arrondi inférieur, sans dépassement : `Math.multiplyHigh` ou `BigInteger`) ; `static long amount(long fe, long price, long unitFe, long subscription)` ; `static long tax(long amount, int percent)` (arrondi inférieur).
  - `PowerBilling implements DebitPayee, DailyDebit.DayIssuer` (genre `electricity`) : `issue(server, day, now)` — pour chaque client `ACTIVE` ou `RESIGNING` : facture `amount(feToday, prix du jour, unité, abonnement du jour)` si > 0, motif (jour, kFE, prix, abonnement) ; `feYesterday = feToday`, `feToday = 0` ; `RESIGNING` → client détaché ; puis tarifs du lendemain appliqués (prix ramené au plafond, fondateur prévenu ; clients prévenus d'un changement) ; `outYesterday`/`metersYesterday` notés. `receive` : taxe au trésor, reste à la caisse, ligne de paiement au relevé du client (« impayés » mis à jour). `refused` : avis au fondateur. `describe` : motif traduit.

- [ ] **Step 1: Écrire `PowerBillMathTest`** : `consommationFoisPrixPlusAbonnement` ; `arrondiAuCuivreInferieur` ; `grandsNombresSansDepassement` (`Long.MAX_VALUE / 2` FE) ; `taxeArrondieEnDessous` ; `prixNulEtAbonnementNulDonnentZero`.
- [ ] **Step 2: Écrire `FactureElectriciteGameTests`** (lot à lui, horloge fixe) :
  - `aMinuitLaFactureValantConsommationFoisPrixPlusAbonnement` (2 300 000 FE à 5 cuivres par 100 000 FE + 10 = 125 cuivres) **et les pièces arrivent dans la caisse du poste** ;
  - `prelevementDansLeCoffreFortAMinuit` (client déconnecté, coffre-fort zone non chargée : scénario d'acceptation) ;
  - `unServeurEteintDeuxJoursEmetDeuxFactures` (démarrage deux jours après : deux factures par compteur, abonnement > 0) ;
  - `unChangementDePrixNeToucheQueLaFactureSuivante` ;
  - `unPrixAuDessusDuNouveauPlafondEstRameneAMinuit` (fondateur prévenu) ;
  - `laTaxeVaAuTresor` (10 % : 12 au trésor, 113 à la caisse, lignes au registre) ;
  - `unFournisseurSupprimeNeFacturePlus` (avec la tâche 6 : ici par l'appel du service) ; `uneHorlogeQuiReculeNeFactureRien` ;
  - `unRefusPrevientLeClientEtLeFournisseur` (colonne « impayés », facture présentée à la minuit suivante).
- [ ] **Step 3: Lancer** `./gradlew --offline test` puis (sous le verrou) `runGameTestServer` ; constater l'échec.
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Commit** `feat(power): facture quotidienne d'electricite`, push.

### Task 5: Panneau du poste

**Files:**
- Create : `grid/supplier/PowerPanelPacket.java`, `PowerActionPacket.java`, `grid/supplier/client/PowerPanelScreen.java`, `PowerPanelLayout.java`, JUnit `grid/supplier/client/PowerPanelLayoutTest.java`.
- Modify : `bench/BenchNetwork.java` (`ID_POWER_PANEL = 24` vers le client, `ID_POWER_ACTION = 25` vers le serveur, protocole `"9"`), `PowerStationBlock.java` (clic droit d'un ayant droit : panneau ; d'un autre : « Poste de <nom> »), `dev/DemoVisuels.java`, langues.

**Interfaces:**
- Consumes : `PowerSuppliers`, `PowerRules.mayManage`, `PowerTillStore`, `CoinMath.format`.
- Produces :
  - `PowerPanelPacket` (serveur → client) : nom (vide pour un poste neuf), prix et abonnement du jour et du lendemain, plafond, taxe, sortie et écart d'aujourd'hui et d'hier, total de la caisse, au plus 64 clients (nom d'abonné, joueur, état, kFE d'aujourd'hui et d'hier, impayés, disjoncteur jumelé, coupé). Taille bornée.
  - `PowerActionPacket(int action, ...)` (client → serveur, rejoué par le serveur, ayant droit seulement, position à portée) : `NAME`, `SET_PRICE`, `SET_SUBSCRIPTION`, `ACCEPT`, `REFUSE`, `CUT`, `RESTORE`, `DETACH`, `OPEN_TILL` ; répond par un message en barre d'action et un nouvel état. Un client inconnu ou périmé ne fait rien d'autre que renvoyer l'état.
  - `PowerPanelLayout` (pur, testable) : `static Layout of(int guiWidth, int guiHeight, ToIntFunction<String> textWidth, View view)` ; cadre qui tient en 320 × 240 ; liste des clients défilante ; textes mesurés, coupés (« … ») avec bulle complète ; écart en rouge au-delà du seuil.

- [ ] **Step 1: Écrire `PowerPanelLayoutTest`** (largeur de texte factice à 6 pixels par caractère) : `toutTientEn320Par240` (contenu le plus long : nom de 24 caractères, 64 clients aux noms de 16 caractères, montants à cinq coupures, écart en alerte) ; `unNomTropLongEstCoupe` ; `unPosteNeufMontreLeChampDuNom`.
- [ ] **Step 2: Étendre `RaccordementGameTests`** (paquets traités sur place, leçon du 2026-11-12) : `unPaquetDUnSansDroitNeFaitRien` ; `unPrixAuDessusDuPlafondEstRefuse` ; `accepterEtCouperParPaquet`.
- [ ] **Step 3: Lancer** `./gradlew --offline test` puis (sous le verrou) `runGameTestServer` ; constater l'échec.
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Captures** (sous le verrou) : `./gradlew --offline runDemoVisuels` ; `DemoVisuels` montre (a) le panneau au contenu le plus long en 320 × 240, (b) le même en grande interface, (c) un poste neuf (champ du nom), (d) un client coupé et des impayés, (e) une bulle de nom coupé, (f) l'écran d'une facture d'électricité (motif traduit), (g) le poste posé, collé à un mur, sous deux angles. **Ouvrir chaque image** : rien ne déborde, tout se lit, aucune face voisine transparente. Lire « [souris] rendue » et « [fenetre] second écran » au journal ; aucun client laissé ouvert, verrou vidé sans BOM.
- [ ] **Step 7: Commit** `feat(power): panneau du poste de fournisseur`, push.

### Task 6: Gestes du maire et de l'équipe, tableau de bord

**Files:**
- Create : `dev/MaireElectriciteGameTests.java`.
- Modify : `grid/supplier/PowerCommands.java` (partie maire et équipe), `economy/payroll/SalaryCommands.java` (textes de `resume`/`resetday` : la pause vaut aussi pour le prélèvement), `economy/EconomyCommands.java` (`/eco stats`, `/eco power`), `EconomyLedger.java`, langues.

**Interfaces:**
- Consumes : `RoleGate.canDoMayorWork`, `PowerSuppliers`, `InvoiceRegistry.cancel`, `CommerceDelivery.payToSafeFirst`, `EconomyLedger`.
- Produces (maire ou équipe, refus clair sinon ; chaque geste au journal du registre avec l'auteur) :
  - `/power cap <cuivres>` (par 100 000 FE, au lendemain) ; `/power tax <pourcent>` (0 à `powerTaxMaxPercent`, au lendemain) ;
  - `/power suppliers` (fondateur, nombre de clients, prix, abonnement : feature 107 du maire) ;
  - `/power set <fournisseur> price|subscription <valeur>` (au lendemain, sous le plafond) ; `/power owner <fournisseur> <joueur>` ;
  - `/power remove <fournisseur> <motif>` puis `/power remove <fournisseur> confirm` (60 s) : règle « Fournisseur supprimé ».
  - Équipe (niveau 2) : `/eco power` (tous les fournisseurs, caisses, écarts d'hier, factures dues par fournisseur) ; `/eco stats` : lignes « Factures publiques dues » et « Électricité » (payées, taxe).

- [ ] **Step 1: Écrire `MaireElectriciteGameTests`** : `unFournisseurNePeutPasDepasserLePlafond` (panneau et `/power set` refusés au-dessus) ; `seulsLeMaireEtLEquipeReglentLePlafond` ; `leMaireModifieUnFournisseur` ; `unFournisseurSupprimeParLeMaireNeFacturePlusPersonne` (clients détachés et prévenus, courant coupé, factures dues annulées, caisse au fondateur, somme conservée) ; `laSuppressionDemandeUneConfirmation` ; `sansMaireLEquipeFaitCesGestes` ; `chaqueGesteEstAuRegistre` ; `ecoStatsCompteLesFacturesDues`.
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou), lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(power): gestes du maire et tableau de bord de l'electricite`, push.

### Task 7: Preuve de bout en bout, captures, vérification, wiki, suivi

**Files:**
- Create : `dev/E2eElectricite.java` (client), `dev/E2eElectriciteServeur.java` (commandes de console niveau 4 réservées au RCON, sur le modèle d'`E2eQuetesServeur` : bâtir l'aire close et couverte, créer les joueurs simulés `Abonne` et `Fauche`, déposer dans un coffre-fort, appliquer tout de suite les tarifs du lendemain (`tarifs-maintenant`, pour que la facture du jour porte le prix), lire `PowerSuppliers`, `InvoiceRegistry` et le registre), `docs/verifications/lot-e6.md`.
- Modify : `dev/E2eClient.java` (phase `ELECTRICITE`), `wiki/data/economie.json`, `tasks/todo.md`.

**Ordre des étapes** (leçons du 2026-11-11 et du 2026-11-15 : lister d'abord ce que chaque relance relit).
- Passage : `MONNAIE` → `COMMERCE` → `BUTIN` → `QUETES` → **`ELECTRICITE`** → `TRESOR`. `ELECTRICITE` n'utilise que ses joueurs simulés et laisse la caisse pleine : Dev ne porte rien de plus en entrant dans `TRESOR`.
- Relance : `COMMERCE` → `MONNAIE` → `TRESOR` → `BUTIN` → **`ELECTRICITE`** → `QUETES` en dernier (saut d'horloge au lundi). Les prélèvements de minuit ont lieu au démarrage, chez `Abonne` et `Fauche` seulement ; Dev ne vide la caisse qu'à l'étape `ELECTRICITE`, après les contrôles exacts des autres étapes. Taxe laissée à 0 % (le trésor relu par `TRESOR` ne bouge pas ; la taxe est prouvée en GameTests).
- Aire à part, close et couverte, loin du point de départ ; l'étape rend les droits (`op`) et ne range que ses propres objets.

**Essai sur serveur dédié avec le pack** (client `Dev`, ingénieur, en survie, non opérateur pour les gestes ; monde neuf, l'ancien rangé hors du dépôt) :
- **Poste** : Dev reçoit un poste (sa fabrication à l'établi est prouvée en GameTest), le pose en tête d'un réseau (moteur créatif et alternateur de Crafts & Additions posés par RCON), nomme son fournisseur au panneau (vraie saisie), fixe prix 5 et abonnement 10 (vrais clics : « au lendemain » affiché), puis `tarifs-maintenant`.
- **Raccordement** : fil suspendu de Crafts & Additions tiré vers la maison d'`Abonne` (compteur au nom d'`Abonne`, disjoncteur, machine d'essai). Dev jumelle au testeur (vrais clics : poste, compteur, disjoncteur). `Abonne` donne son accord (commande jouée en son nom). Avant l'acceptation, la machine ne reçoit rien ; Dev accepte au panneau (vrai clic), la machine tourne ; consommation du panneau = consommation relue par RCON.
- **Refus** : même chose pour `Fauche` (coffre-fort vide, rien porté).
- **Coupure** : Dev coupe `Fauche` au panneau, le disjoncteur s'ouvre ; un joueur simulé sans droit tente de le réenclencher et de casser le compteur : refusé.
- **Plafond** : le maire simulé `/power cap 3` ; le panneau annonce le prix ramené à 3 au lendemain.
- Avant l'arrêt : consommations du jour, caisse, factures et registre écrits dans `e2e-electricite.txt` ; `Abonne` et `Fauche` déconnectés, aire hors des chunks chargés.
- **Relance du serveur** (`-Pe2eHorloge` : deux jours de plus, celui d'E3) : **à minuit (au démarrage), la facture d'`Abonne` est prélevée dans son coffre-fort**, joueur absent, zone non chargée (scénario d'acceptation) ; deux factures par compteur (jour de l'essai : consommation × 5 + 10 ; lendemain : 10 au prix ramené à 3, consommation nulle) ; coffre-fort d'`Abonne` diminué d'autant, caisse augmentée d'autant ; `Fauche` : deux factures dues, refus au registre, `/eco stats` « Factures publiques dues : 2 » ; à sa connexion (simulée), l'avis avec le lien de copie. Dev ouvre le panneau : impayés de `Fauche`, écart d'hier ; il vide la caisse (vrai clic) : pièces dans son inventaire, total identique.

- [ ] **Step 1: Écrire l'E2E** (client et serveur) et lancer sous le verrou : `runServer -PwithPack` puis `E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack` ; nouveau `runServer -PwithPack -Pe2eHorloge=<minutes>` puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack` ; puis le **passage complet** (`runE2eClient -PwithPack`, l'étape du réseau du lot 4 raccordée) et sa relance ciblée. Céder le verrou entre deux passages si quelqu'un attend. Lire « [e2e] bilan : TOUT PASSE » à chaque passage, « [souris] rendue » et « [fenetre] second écran » dans le journal du client ; aucun client laissé ouvert, PID notés, verrou vidé sans BOM.
- [ ] **Step 2: Captures en jeu** du panneau et du poste sur le serveur dédié avec le pack (pendant l'E2E), **ouvertes et regardées**, en plus de celles de la tâche 5.
- [ ] **Step 3: Écrire `docs/verifications/lot-e6.md`**, au format de `lot-e5.md` : commandes, heures et comptes lus ; décisions et choix du lot ; captures ouvertes ; séparer prouvé et non prouvé. Couvre au moins : ce que la tâche 3 a lu sur les câbles (et toute intégration ajoutée) ; changement de règle du compteur du lot 4 (non jumelé : bloqué) ; points d'extension `DebitPayees`/`DailyDebit` pour amendes, impôt et téléphone, non codés ; notification du téléphone absente ; limite de Minecraft (pièces retirées d'un inventaire puis arrêt brutal avant la sauvegarde) ; ligne vulnérable (câbles non protégés, écart du panneau) ; équilibrage des prix non vécu.
- [ ] **Step 4: Mettre à jour le wiki** : entrées `prelevement` et `fournisseur` de `wiki/data/economie.json` à `dispo`, au format des entrées `dispo` d'E5 (`flow` du prélèvement : « Inventaire », « Attente », « Coffre-fort », « Refusé » ; `who.fabrique` du poste : « Ingénieur (établi) » ; chiffres : minuit, prix par 100 000 FE, plafond 20, abonnement jusqu'à 50, taxe 0 % ; étapes du jumelage, de l'accord du client et de l'acceptation ; bloc `staff` : `/power`, `/eco power`, `/eco stats`, réglages) ; entrée `maire` (plafond, taxe, suppression) et `tableau-de-bord` (factures dues).
- [ ] **Step 5: Mettre à jour `tasks/todo.md`** : ligne E6 cochée avec le résumé et les liens (plan déplacé dans `archive/` par `mv`, vérification) ; restes relevés ; noter que le cahier de l'économie est entièrement livré.
- [ ] **Step 6: Commit et push** par nom de fichier : `test(e6): essai de bout en bout du prelevement et du fournisseur`, puis `docs(e6): preuve du lot E6`.

---

## Après la clôture du lot

- [ ] **Film de toute l'économie** (E1 à E6, décision de Killian : un film unique à la fin du cahier) — tâche à part, **lancée seulement avec le feu vert de Killian**.

---

## Auto-relecture (faite à l'écriture)

- **Couverture du cahier** : 86 — paiement à distance, ordre inventaire (bourse comprise) puis coffre-fort, attente en plus (décision), tout ou rien, monnaie rendue, refus qui laisse la facture due et prévient débiteur et créancier, représentation à chaque minuit, paiement par l'objet facture, marque « payée » là où la facture est émise, registre → tâches 1 et 4 ; factures publiques non émises par un joueur → `unJoueurNePeutPasEmettreDeFacturePrelevable` ; amendes, impôt, téléphone → point d'extension (tâche 1), non codés. 83 — poste fabriqué par l'ingénieur, en tête du réseau, nom du fournisseur → tâche 2 ; prix sous plafond, abonnement, nouveau prix sur la facture suivante, clients prévenus → tâches 4, 5, 6 ; branchement par câbles de tout mod et mesure juste → tâche 3 ; jumelage au testeur, compteur non rattaché ou non accepté qui bloque → tâche 3 ; panneau des clients, accepter, refuser, couper → tâche 5 ; écart (vol de courant) → tâches 2 et 5 ; facture automatique et quotidienne prélevée dans la caisse → tâche 4 ; taxe au trésor → tâche 4 ; sécurité, client compris → tâches 2 et 3 ; maire qui modifie ou supprime → tâche 6 ; ligne vulnérable → dite dans la preuve. Section 8 : chaque ligne du prélèvement et du fournisseur a son test nommé (tâches 1 à 6) ; scénario d'acceptation (« à minuit, sa facture d'électricité est prélevée dans ce coffre-fort ; on relance le serveur ») → tâche 7.
- **Noms** : `DebitPlan.plan`, `DirectDebit.present/payNow/Outcome`, `DebitPayee.exists/receive/refused/describe`, `DebitPayees.register/get`, `DailyDebit.registerIssuer/closeDueDays/DayIssuer`, `InvoiceRegistry.issuePublic/duePublic/cancel/lastDebitDay`, `HeldGoods.pendingCoins/takeCoins/addCoins`, `PowerSuppliers.found/passes/recordOut/recordMeter/clientOf`, `PowerRules.mayManage`, `PowerPairing.pairMeter/pairBreaker`, `PowerBillMath.energyCost/amount/tax`, `PowerBilling`, `MoneyTransfer.PUBLIC_BILL/POWER_TAX`, `TreasuryLedger.Kind.POWER_TAX` sont les mêmes dans toutes les tâches.
- **Réseau** : deux paquets nouveaux (24 et 25), protocole `9`.
- **Review Focus** : chaque ligne a son test dans la tâche qui possède le code (tâches 1, 2, 3, 4).
