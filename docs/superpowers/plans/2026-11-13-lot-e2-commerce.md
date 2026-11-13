# Lot E2 : le commerce — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Les joueurs commercent entre eux, même absents : une fenêtre d'échange sûre entre deux joueurs proches, un étal de marché qui vend et rachète sans son propriétaire, et des factures payables en pièces dont l'argent arrive toujours à l'émetteur.

**Architecture:** Trois sous-paquets d'`economy` (`trade`, `stall`, `invoice`) posés sur un socle commun d'`economy`.
- **Socle (tâche 1).** Le registre apprend les **transferts** (ni source ni puits, comptés à part). Deux `SavedData` minimaux : le **compte du trésor** (reçoit la taxe, sans bloc ni écran, rattaché au bloc au lot E3) et la **garde** (`HeldGoods`), qui tient les objets d'un échange en cours et les **livraisons en attente** d'un joueur absent. Une seule porte de livraison (`CommerceDelivery`) décide où va ce qui revient à quelqu'un : inventaire, coffre-fort ou attente, jamais le sol.
- **Argent.** Tout paiement d'un joueur passe par `PlayerCoins.take` (inventaire et bourses, monnaie rendue, jamais partiel) ; toute entrée chez un joueur par `PlayerCoins.receive`. Une caisse d'étal est un `CoinStore`, servie par `CoinActions` et l'écran commun `CoinAccountScreen`.
- **Atomicité.** Chaque opération vérifie tout, puis engage sans rien qui puisse encore échouer ; les gestes du client portent la version de l'état qu'il a vu, et le serveur refuse un état périmé.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, Mixin (déjà en place, aucun nouveau mixin prévu), JUnit 5, GameTests, pack (`-PwithPack`).

**Spec:** `docs/superpowers/specs/2026-11-08-economie-design.md`. Sections utiles :
- sections 2, 3 et 4 (objectifs, contraintes, socle et tableau « qui fabrique, qui pose, qui gère ») ;
- features 70, 71 et 72, et 84 (où l'argent peut se trouver) ;
- section 7 (lot E2 : « Les joueurs commercent entre eux, même absents ») ;
- section 8 (lignes du lot E2 : échange après deux confirmations, étal vendeur absent, trémie qui ne vide pas, étal plein incassable, taxe au trésor, facture payée en l'utilisant ; scénarios d'acceptation et d'abus) ;
- section 9.

Modèle de forme : le plan du lot E1 (`docs/superpowers/plans/archive/2026-11-12-lot-e1-monnaie.md`) et sa preuve (`docs/verifications/lot-e1.md`).

## Global Constraints

Les contraintes globales du lot 6 et du lot E1 restent valables telles quelles (`docs/superpowers/plans/archive/2026-11-10-lot-6-vie-de-la-ville.md` et `archive/2026-11-12-lot-e1-monnaie.md`, sections « Global Constraints »), notamment : ASCII dans `gradle.properties` ; aucune classe cliente atteignable du serveur (écrans et rendus dans `economy/client`) ; textes en `fr_fr.json` avec repli `en_us.json`, jamais « SolarTown » ; `DeferredRegister` ; textures produites par script Python/PIL ; lots de 8 GameTests au plus ; `JoueurDeTest` pour tout joueur d'essai ; montants en cuivres (`long`) affichés par `CoinMath.format` ; une commande de l'équipe exige le niveau 2 ; « l'équipe » = `hasPermissions(2)`, comme `SafeBlock.mayAccess`.

En plus :
- **Aucune pièce créée ni détruite par ce lot.** Chaque mouvement d'argent entre deux personnes (ou vers le trésor) est un **transfert** noté au registre (`EconomyLedger.transferred`) ; `inCirculation()` ne bouge pas.
- **Payer** = `PlayerCoins.take` ; **recevoir** = `PlayerCoins.receive` / `CommerceDelivery`. Aucun code de ce lot ne retire ni n'ajoute une pièce à un inventaire autrement.
- **Vérifier puis engager.** Une opération qui peut échouer (place, argent, stock, version) échoue avant le premier changement. Après la vérification, rien ne peut plus échouer ; si l'imprévu arrive quand même (place calculée fausse), le reste va en attente (`HeldGoods`), jamais au sol ni au néant.
- **Un client n'est jamais cru sur parole** : chaque paquet revalide joueur connecté, distance, droit, menu ouvert, version vue (`BenchNetwork`). Le protocole passe de « 5 » à « 6 » à la tâche 2, une seule fois pour tout le lot.
- **Rangements de l'argent (feature 84)** : chaque nouvel écran qui peut contenir de l'argent est déclaré par `MoneyStorageRules.register` ; une case qui ne doit pas en recevoir le refuse elle-même (`mayPlace`), et son stockage Forge (`isItemValid`) aussi. Aucune cachette : le contenu d'une fenêtre d'échange ne lui survit jamais.
- **Objets du commerce fabriqués à la table par n'importe qui** (étal, carnet de factures), recettes en données, jamais à l'établi (même garde que les objets d'E1 dans `BenchCatalog`).
- **Tout bloc dont le modèle n'occupe pas le cube entier** reçoit `noOcclusion()` et une `getShape` exacte ; sa capture le montre collé à un mur.
- **Preuve des tests** : `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack` ; lire « All N required tests passed » dans `run-gametest/logs/latest.log`, jamais le seul code de sortie.
- **Tout lancement de Minecraft** (GameTests, client, serveur, E2E, captures) suit la procédure du verrou : section 11 de `C:/Users/killi/.claude/CLAUDE.md` et la note `D:/BDC/Minecraft/Deux sessions d'agents qui lancent Minecraft sur la même machine passent par un fichier de verrou et une file d'attente.md`.
  - Lire `C:\Users\killi\.claude\lancement-minecraft.txt` ; rempli : s'inscrire en fin de `lancement-minecraft-attente.txt`, surveiller en arrière-plan, jamais d'attente au premier plan ; vide : ne le prendre que si la file est vide ou si l'on est en tête, écrire sa ligne, retirer sa ligne de la file, lancer ; à la fin, réussite ou échec, **vider** le verrou ; file non vide à la libération : se remettre en fin de file.
  - Tout client passe une propriété JVM `oneforallsurvival.*` (souris rendue par `dev/SourisLibre`) ; lire « [souris] rendue » dans son journal. Aucun client laissé ouvert.
  - N'arrêter que ses propres PID ; ne jamais toucher une JVM dont la ligne de commande contient `AXIONENGINE`. Un serveur resté ouvert s'arrête par RCON (`stop`).
  - Ne jamais modifier `run/options.txt`. Pas de Prism. Rien télécharger.
  - Ces règles sont recopiées dans le brief de tout sous-agent qui lance Minecraft.
- **Git** : travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, par `git commit -- <chemins>` (jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/`), sans `--no-verify` ni `--force`, message terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Pas de film à ce lot** (décision de Killian : un film unique de toute l'économie après E6). Captures vérifiées et E2E sur serveur dédié, relance comprise.

## Review Focus

1. **Départ pendant un échange** (fermeture, déconnexion, éloignement, mort, arrêt brutal du serveur avec des objets en garde) : chaque objet et chaque pièce revient à celui qui l'a posé, une fois ; rien au sol, rien de perdu, rien de dupliqué. Testé en tâches 1 et 2 (`uneGardeSurvitAUnArretBrutal`, `deconnexionRendToutAvantLaSauvegarde`).
2. **Course entre un changement et un clic** : une confirmation d'échange envoyée sur une version périmée, un achat à l'étal dont le prix ou le lot a changé entre l'affichage et le clic, deux acheteurs sur le dernier lot dans le même tick. Attendu : refus sans aucun mouvement. Testé en tâches 2 et 4.
3. **Inventaire plein** du receveur (échange, achat, rachat, livraison d'une facture, retour d'une garde) : l'opération est refusée avant tout mouvement, ou ce qui revient va en attente ; jamais au sol. Testé en tâches 1, 2, 4 et 5.
4. **Facture rejouée** : une facture copiée (clone créatif), payée deux fois, payée par un autre que le débiteur, ou dont l'identifiant est inconnu du serveur ne se paie pas. Testé en tâche 5.
5. **Cases de l'autre dans la fenêtre d'échange** : clic, maj-clic, touche numérique, double-clic « tout ramasser », glisser, touche de la main gauche sur les cases de l'autre côté ne prennent ni ne posent rien. Testé en tâche 2 (`lesCasesDeLAutreNeSePrennentPas`).

## Décisions du contrôleur (inscrites telles quelles)

| Sujet | Décision |
|---|---|
| Taxe sur les ventes (71) | 0 % par défaut, réglable en config. Elle va dans un **compte du trésor gardé par le serveur** (`SavedData` minimal, sans bloc ni écran) que le lot E3 rattachera au bloc du trésor. Mouvement noté au registre : un transfert, ni source ni puits. |
| Crochetage de l'étal | Renvoyé au lot du policier (feature 67), comme pour le coffre-fort en E1. Point d'extension : `MarketStallBlock.mayManage`. |
| Étal (71) | Bloc fabriqué par n'importe qui (recette en données) ; le poseur en est propriétaire, autant d'étals qu'il veut. Stock en cases ; **offres de vente** (objet, quantité par lot, prix en cuivres) et **offres de rachat** (objet, prix, quantité max) payées sur la caisse. L'acheteur paie avec les pièces de son inventaire et de ses bourses, monnaie rendue, vendeur absent ou non, atomiquement. La caisse est un stockage de pièces permis dans le bloc ; seuls le propriétaire et l'équipe retirent marchandise et recettes. Trémies et automatismes garnissent le stock, ne le vident jamais, ne touchent jamais la caisse. Il ne se casse **que vide**, par le propriétaire ou l'équipe (l'équipe le vide d'abord). Registre de l'étal (ventes, rachats, retraits) lisible par le propriétaire. Visuel final, `noOcclusion()` et forme exacte. |
| Échange (70) | Proposé à un joueur proche (distance réglable, 5 blocs) ; l'autre accepte ; fenêtre à deux côtés, objets et pièces ; toute modification annule les deux confirmations ; l'échange n'a lieu qu'après les deux confirmations sur l'état final, atomiquement. Fermeture, déconnexion, éloignement, mort : tout revient à son propriétaire (jamais au sol si évitable, jamais perdu, jamais dupliqué). Reçu sur demande (case à cocher) : un reçu pour chacun. La fenêtre est admise par les règles de stockage sans ouvrir de cachette. |
| Facture (72) | Un **carnet de factures** (recette en données), utilisé sur un joueur proche, ouvre un formulaire (montant en cuivres, motif) ; le débiteur reçoit une **facture** (émetteur, débiteur, motif, montant, date, identifiant unique). Payable de main à main, ou en l'utilisant (débiteur seulement) : inventaire et bourses, monnaie rendue, tout ou rien. Les pièces vont à l'émetteur : son inventaire s'il est connecté et a la place, sinon son coffre-fort (`SafeAccounts`, marche hors ligne), sinon **en attente gardée par le serveur** jusqu'à sa connexion. Payée, la facture devient un **reçu**. Identifiant enregistré côté serveur : une copie ne se paie pas deux fois. Aucune facture entre joueurs n'est prélevable à distance (feature 86, lot E6) ; le débiteur peut la jeter. |
| Lancements | Procédure du verrou (voir Global Constraints). |
| Film | Aucun à ce lot ; E2E sur serveur dédié avec le pack, relance comprise, et captures vérifiées. |
| Relance de l'E2E | Voir tâche 7 : relance ciblée économie (`E2E_MONNAIE_SEULE`, étendue à E1 + E2), justifiée là. |

## Choix de l'auteur du plan (à valider par Killian)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Proposer un échange | `/trade <joueur>`, ou accroupi + clic droit main vide sur le joueur. L'autre reçoit un message avec deux liens cliquables `[Accepter]` `[Refuser]` (`/trade accept|deny <joueur>`), ou fait le même geste en retour. La demande expire après 30 s ; un joueur n'a qu'un échange à la fois. | Un geste en jeu et une commande pour ceux qui préfèrent ; aucune touche nouvelle à apprendre |
| Pièces dans l'échange | Les pièces se posent comme des objets dans les 12 cases de son côté ; des boutons « +1 / +10 » par coupure y versent des pièces prises dans les bourses d'abord, puis l'inventaire. Une bourse pleine s'échange comme un objet. | Les pièces d'une bourse sont sinon inaccessibles depuis la fenêtre ; une seule mécanique (des cases) au lieu de deux |
| Garde de l'échange | Les objets posés quittent l'inventaire et vivent dans la garde du serveur (`HeldGoods`, sauvegardée avec le monde). Après un arrêt brutal, la garde retrouvée au démarrage devient une livraison en attente pour son propriétaire. | « Jamais perdu » même si le serveur tombe pendant l'échange ; la sauvegarde de la garde et celle des joueurs ont lieu au même tick, donc rien ne se double |
| Course à la confirmation | Chaque changement (case, reçu coché) incrémente la **version** de l'échange et efface les deux confirmations ; le bouton « Confirmer » envoie la version vue, refusée si elle n'est plus la bonne. | Empêche l'arnaque « je change au dernier moment » |
| Place à l'arrivée | L'échange (et l'achat, le rachat) est **refusé** si ce qui arrive ne tient pas dans l'inventaire du receveur, calculé avant tout mouvement. Les pièces reçues entrent d'abord dans la première bourse. | Rien au sol ; un refus explicite vaut mieux qu'une livraison différée pour un joueur présent |
| Reçu | Une seule case à cocher partagée : cochée au moment de l'exécution, chacun reçoit un **reçu** (objet `receipt`, empilable à 1) listant ce qu'il a donné et reçu, la date et l'autre partie. Le même objet sert de reçu de facture. Le reçu n'est pas de l'argent et se range partout. | « Un reçu pour chacun » ; un seul objet pour deux usages |
| Retour d'une garde | Le propriétaire connecté : dans son inventaire (pièces par `PlayerCoins.receive`), le reste en attente avec un message. À la déconnexion : rendu sur `PlayerLoggedOutEvent`, qui passe avant la sauvegarde du joueur (à prouver par un GameTest qui relit le playerdata), le reste en attente. Mort : rendu avant les lâchers (`LivingDeathEvent`, priorité la plus haute). | `clearContainer` de Minecraft lâche au sol pour un joueur déconnecté ; on ne s'en sert pas |
| Livraison en attente | Livrée à la connexion, puis toutes les 30 s tant qu'il en reste ; ce qui ne tient pas reste en attente, avec un message. Visible au tableau de bord (`/eco stats`, ligne « en attente »). | Exception à la liste de la feature 84 décidée par le contrôleur ; personne ne peut y déposer à volonté, ce n'est pas une cachette |
| Étal : nombre d'offres | 9 offres de vente et 9 offres de rachat au plus par étal (config `stallMaxOffers`), stock de 27 cases. | Un écran lisible, une caisse de bonne taille |
| Étal : modèle d'une offre | Le propriétaire tient l'objet sur le curseur et clique « Définir » : le serveur en garde une **copie de modèle** (quantité 1, NBT compris) dans les données de l'offre, jamais dans une case. Le modèle doit correspondre exactement (`ItemStack.isSameItemSameTags`). Une pièce ou une bourse pleine est refusée comme modèle. | Les cases fantômes ont déjà dupliqué de l'argent (leçon du 2026-11-12) : pas de case fantôme |
| Étal : rachat | Offre de rachat = objet, quantité par lot, prix par lot, nombre de lots restant ; chaque rachat le décrémente. Refusé si la caisse ne peut pas payer ou si le stock n'a pas la place. **Pas de taxe sur un rachat** (« taxe sur les ventes »). | Lecture littérale de la feature 71 |
| Étal : paiement par la caisse | La caisse est un compte par coupure ; elle paie exactement le prix, en cassant ses propres pièces au besoin (valeur conservée, `CoinMath.makeChange` sur son contenu). | La caisse ne manque jamais de monnaie si elle a la valeur |
| Étal : taxe | `tax = price * salesTaxPercent / 100`, arrondi vers le bas, entier de 0 à 100. La caisse reçoit `price - tax`. | En faveur du vendeur ; aucun cuivre fractionnaire |
| Étal : achat chez soi | Le propriétaire ne peut pas acheter à son propre étal ni lui revendre. | Évite de payer la taxe par erreur |
| Étal : écrans | Le propriétaire et l'équipe ouvrent la **gestion** (onglets Stock, Offres, Caisse, Registre) ; un accroupi + clic droit du propriétaire ouvre la **vitrine** comme un client, pour se voir. Les autres ouvrent la vitrine (offres, disponibilité, Acheter / Vendre). La caisse réutilise l'écran commun du coffre-fort (`CoinAccountMenu`). | Réutilise E1, rien de neuf à apprendre |
| Étal : registre | 200 lignes par étal au plus (config), gardées dans l'entité du bloc : heure, acteur, genre (vente, rachat, retrait d'objets, retrait ou dépôt de pièces, retrait par l'équipe), objet et quantité, montant. Les transferts entre personnes vont aussi au registre global. | « Registre lisible par le propriétaire » ; les dépôts du propriétaire dans sa propre caisse ne sont pas des transferts |
| Étal : protection | Résiste aux explosions, au Wither, au dragon et aux pistons (comme le coffre-fort) ; impossible à casser pour les autres (progrès de casse nul + `BreakEvent` annulé, créatif compris). | Feature 71 |
| Étal : affichage | Les trois premières offres de vente s'affichent posées sur le comptoir (rendu de l'entité de bloc). | Un passant voit ce qui est vendu sans ouvrir |
| Facture : paiement | Utiliser la facture (débiteur) ouvre un écran qui la montre, avec un bouton « Payer » ; aucun paiement au premier clic. | Un mauvais clic ne doit pas payer une grosse somme |
| Facture : main à main | Le débiteur paie comme il veut (fenêtre d'échange, pièces jetées) ; l'**émetteur** la déclare réglée par `/invoice settle <id>` (lien cliquable dans `/invoice list`). La facture devient alors un reçu « réglée de main à main ». | Seul le créancier peut dire qu'il a été payé ; aucune pièce ne bouge par le mod, rien à compter |
| Facture : suivi | `/invoice list` (niveau 0) : factures émises et reçues, dues ou réglées, avec l'identifiant court. L'émetteur est prévenu d'un paiement (message, ou à sa connexion s'il était absent). | Pas d'écran de plus pour un besoin rare |
| Facture : vérité | L'objet n'est qu'une vue ; le registre des factures (`SavedData`) fait foi. Une facture dont le registre dit « réglée » se change en reçu dans l'inventaire au tick suivant ; un identifiant inconnu ne se paie jamais. Les factures réglées sont oubliées après 90 jours (config). | Copie, clone créatif et vieux papiers sans effet |
| Facture : bornes | Montant de 1 à 100 000 000 cuivres (config), motif de 1 à 80 caractères, pas à soi-même ; carnet utilisable à 5 blocs (config `invoiceMaxDistance`), jeté ou non. Si le débiteur n'a pas la place, la facture lui arrive en attente. | Défauts sûrs, réglables |
| Commandes | `/trade` et `/invoice` en anglais, comme `/eco`. | Cohérence avec E1 |
| Tableau de bord | `/eco stats` gagne : total par genre de transfert (échanges, ventes, taxe, rachats, factures, retraits de l'équipe), solde du trésor, masse en attente. | Les lignes « pas encore en service » d'E1 pour les ventes et factures passent en service |

## Fichiers

| Fichier | Rôle |
|---|---|
| `economy/MoneyTransfer.java`, `EconomyLedger.java` (modifié), `EconomyCommands.java` (modifié), `EconomyConfig.java` (modifié) | Transferts au registre, tableau de bord, réglages |
| `economy/TreasuryAccount.java` (SavedData), `HeldGoods.java` (SavedData), `CommerceDelivery.java`, `InventoryFit.java` | Socle : trésor, garde, attente, livraison, calcul de place |
| `economy/trade/TradeService.java`, `TradeSession.java`, `TradeMenu.java`, `TradeActionPacket.java`, `TradeCommands.java`, `client/TradeScreen.java` | Échange (70) |
| `economy/ReceiptItem.java`, `Receipt.java` | Reçu (échange et facture) |
| `economy/stall/MarketStallBlock.java`, `MarketStallBlockEntity.java`, `StallStockHandler.java`, `StallCashStore.java`, `StallOwnerMenu.java`, `StallCashMenu.java`, `StallCashPacket.java`, `StallOffer.java`, `StallBuyback.java`, `StallTrade.java`, `StallLedger.java`, `StallShopMenu.java`, `StallActionPacket.java`, `StallLedgerPacket.java`, `CommerceMath.java`, `client/StallOwnerScreen.java`, `client/StallCashScreen.java`, `client/StallShopScreen.java`, `client/StallRenderer.java` | Étal (71) |
| `economy/invoice/InvoiceBookItem.java`, `InvoiceItem.java`, `InvoiceRegistry.java` (SavedData), `InvoiceService.java`, `InvoiceCommands.java`, `InvoiceFormPacket.java`, `InvoiceIssuePacket.java`, `InvoicePayPacket.java`, `client/InvoiceFormScreen.java`, `client/InvoiceScreen.java` | Facture (72) |
| `src/test/java/dev/oneforallsurvival/economy/{EconomyLedgerTransferTest,CommerceMathTest}.java` | JUnit |
| `src/devcontent/java/dev/oneforallsurvival/dev/{CommerceSocle,Echange,EtalStock,EtalVente,Facture}GameTests.java`, `E2eCommerce.java`, `E2eCommerceServeur.java` | Preuves |
| `tools/gen_textures_e2.py` | Textures de l'étal, du carnet, de la facture, du reçu |

---

### Task 1: Socle du commerce (transferts, trésor, garde, livraison)

**Files:**
- Create : `economy/MoneyTransfer.java`, `TreasuryAccount.java`, `HeldGoods.java`, `CommerceDelivery.java`, `InventoryFit.java`, `src/test/java/dev/oneforallsurvival/economy/EconomyLedgerTransferTest.java`, `src/devcontent/.../CommerceSocleGameTests.java`.
- Modify : `EconomyLedger.java` (transferts), `EconomyCommands.java` (`stats`), `EconomyConfig.java`, `OneForAllSurvival.java` ou `EconomyRegistry` (abonnements : connexion, tick, démarrage), langues.

**Interfaces:**
- Consumes : `Coin`, `CoinMath.decompose`, `PlayerCoins.receive`, `SafeAccounts.get(server).safePos/deposit`, `EconomyLedger`.
- Produces :
  - `enum MoneyTransfer { TRADE, STALL_SALE, SALES_TAX, STALL_BUYBACK, STAFF_WITHDRAW, INVOICE }`, avec `kind()` → `"transfer.trade"`… et `langKey()`, comme `MoneySource`.
  - `EconomyLedger.transferred(MoneyTransfer type, long amount, @Nullable UUID author, String reason)` (montant ≤ 0 ignoré) et `totalTransferred(MoneyTransfer) : long`. Variante package-privée avec `maxLines` et `now`, comme `created`. Totaux gardés par nom sous `"transferred"` ; `inCirculation()` inchangé.
  - `TreasuryAccount.get(MinecraftServer)` (clé `oneforallsurvival_treasury`) : `deposit(Map<Coin, Long>)`, `balance() : EnumMap<Coin, Long>`, `total() : long`. Pas de retrait à ce lot (E3).
  - `HeldGoods.get(MinecraftServer)` (clé `oneforallsurvival_held`) :
    - `openEscrow(UUID owner, int size) : SimpleContainer` (une garde par joueur ; marque le `SavedData` sale à chaque changement) ; `closeEscrow(UUID owner) : List<ItemStack>` (vide et oublie la garde, rend son contenu) ;
    - `addPending(UUID owner, List<ItemStack> items, Map<Coin, Long> coins)` ; `hasPending(UUID) : boolean` ; `pendingMass() : long` (valeur des pièces et des bourses en attente) ;
    - au chargement, toute garde retrouvée passe en attente de son propriétaire (le serveur s'est arrêté pendant un échange).
  - `InventoryFit.fits(Player player, List<ItemStack> incoming) : boolean` : simulation sur une copie des 36 cases principales, même algorithme qu'`Inventory.add` (piles existantes, puis cases vides), pièces exclues quand le joueur a une bourse (elles y entrent).
  - `CommerceDelivery` :
    - `giveBack(ServerPlayer owner, List<ItemStack> stacks) : int` : pièces par `PlayerCoins.receive`, objets par `Inventory.add` lu avant/après (jamais la pile rendue : en créatif `Inventory.add` vide la pile sans rien placer, voir `PlayerCoins.receiveLoose`) ; le reste en attente, avec un message ; renvoie le nombre de piles mises en attente.
    - `giveBack(MinecraftServer, UUID owner, List<ItemStack>)` : joueur absent → tout en attente.
    - `enum Destination { INVENTORY, SAFE, PENDING }` ; `payTo(MinecraftServer server, UUID creditor, long amount) : Destination` : connecté → `PlayerCoins.receive` de `decompose(amount)` ; ce qui n'est pas entré → son coffre-fort s'il en a un (`safePos` présent) → sinon en attente. Renvoie la destination de la dernière part (la plus lointaine).
    - `deliverPending(ServerPlayer) : boolean` (vrai s'il reste quelque chose), appelée à `PlayerLoggedInEvent` puis toutes les 600 ticks tant que `hasPending`.
  - `EconomyConfig` : `tradeMaxDistance()` 5.0 (1–32), `tradeRequestSeconds()` 30, `salesTaxPercent()` 0 (0–100), `stallMaxOffers()` 9 (1–18), `stallLedgerLines()` 200, `invoiceMaxDistance()` 5.0, `invoiceMaxAmount()` 100 000 000, `invoiceReasonMax()` 80, `invoiceKeepDays()` 90.

Règles :
- `/eco stats` ajoute, après les lignes d'E1 : chaque `MoneyTransfer` avec son total, « Trésor : … » (`TreasuryAccount.total`), « En attente : … » (`pendingMass`). La ligne « en circulation » reste créé − détruit.
- Une pièce en attente reste une pièce qui existe : le tableau de bord la montre, elle n'est ni créée ni détruite.

- [ ] **Step 1: Écrire `EconomyLedgerTransferTest`** (JUnit, constructeur package-privé) :
  - `unTransfertNeChangePasLaCirculation` (créé 500, transfert 200 → `inCirculation` = 500, `totalTransferred(TRADE)` = 200) ;
  - `unTransfertEstUneLigneDuRegistre` (genre `transfer.trade`, auteur, motif) ;
  - `lesTotauxDeTransfertSurviventALaSauvegarde` (`save` puis `load`) ;
  - `unTransfertNulOuNegatifNEstPasNote`.
- [ ] **Step 2: Écrire `CommerceSocleGameTests`** (un lot de 8 au plus) :
  - `uneLivraisonEnAttenteArriveALaConnexion` (`JoueurDeTest` absent, `addPending` d'1 pain et 3 or, connexion → il les porte, `hasPending` faux) ;
  - `uneLivraisonQuiNeTientPasResteEnAttente` (inventaire plein de pierre → le pain reste en attente, les pièces entrent dans sa bourse) ;
  - `payToConnecteAvecPlace` → `INVENTORY` ; `payToSansPlaceAvecCoffreFort` → `SAFE`, `SafeAccounts.total` augmenté du montant ; `payToHorsLigneSansCoffreFort` → `PENDING`, puis livré à la connexion ;
  - `uneGardeSurvitAUnArretBrutal` (`openEscrow`, poser 5 pain, `save` puis `HeldGoods.load` sur une copie → la garde est devenue une attente du même propriétaire, 5 pain, une seule fois) ;
  - `leTresorGardeSonSoldeAuRechargement` ;
  - `ecoStatsMontreTransfertsTresorEtAttente` (texte de `stats` lu sur la source de commande).
- [ ] **Step 3: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 4: Implémenter.**
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack`** (sous le verrou). Lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Commit** `feat(economy): socle du commerce (transferts, tresor, garde, attente)` par nom de fichier, push.

### Task 2: Échange de main à main (70) et reçu

**Files:**
- Create : `economy/trade/TradeService.java`, `TradeSession.java`, `TradeMenu.java`, `TradeActionPacket.java`, `TradeCommands.java`, `economy/client/TradeScreen.java`, `economy/ReceiptItem.java`, `economy/Receipt.java`, `src/devcontent/.../EchangeGameTests.java`.
- Modify : `EconomyRegistry` (menu `trade`, objet `receipt`, `MoneyStorageRules.register(TRADE_MENU)`), `bench/BenchNetwork.java` (`ID_TRADE_ACTION = 13`, protocole « 6 »), `EconomyClientEvents` (écran), langues.

**Interfaces:**
- Consumes : `HeldGoods.openEscrow/closeEscrow`, `CommerceDelivery.giveBack`, `InventoryFit.fits`, `PlayerCoins.held/remove/receive`, `EconomyLedger.transferred(TRADE, …)`, `CoinMath.valueOf`, `Purse.total`.
- Produces :
  - `TradeService` (fil du serveur) :
    - `enum RequestResult { SENT, ACCEPTED, SELF, TOO_FAR, BUSY, NO_REQUEST, EXPIRED, DENIED }` ;
    - `request(ServerPlayer from, ServerPlayer to) : RequestResult` ; `accept(ServerPlayer to, ServerPlayer from) : RequestResult` (ouvre la fenêtre aux deux) ; `deny(ServerPlayer to, ServerPlayer from) : RequestResult` ;
    - `@Nullable TradeSession sessionOf(UUID player)` ;
    - `enum CancelReason { CLOSED, LOGGED_OUT, TOO_FAR, DIED, OTHER_LEFT, SERVER_STOP }` ; `cancel(TradeSession, CancelReason)`.
  - `TradeSession` : `id() : UUID`, `version() : int`, `receipt() : boolean`, `offer(UUID player) : Container` (la garde de ce joueur), `confirmed(UUID) : boolean`, `other(UUID) : UUID`.
    - `confirm(ServerPlayer, int seenVersion) : ConfirmResult` avec `enum ConfirmResult { WAITING, DONE, STALE, NO_ROOM, CHANGED_HANDS }` ;
    - `toggleReceipt(ServerPlayer, int seenVersion)` ; `addCoins(ServerPlayer, Coin coin, int count) : boolean`.
  - `TradeMenu` (type `oneforallsurvival:trade`) : cases 0–11 son offre, 12–23 l'offre de l'autre (affichage), 24–59 l'inventaire ; données synchronisées : version, confirmations, reçu coché.
  - `TradeActionPacket(int action, int seenVersion, int coin, int count)`, actions `CONFIRM`, `TOGGLE_RECEIPT`, `ADD_COINS`, `CANCEL`.
  - `ReceiptItem` (`receipt`, pile de 1) ; `Receipt.forTrade(ServerPlayer self, ServerPlayer other, List<ItemStack> given, List<ItemStack> received, long when) : ItemStack` ; `Receipt.forInvoice(InvoiceRegistry.Invoice, Status) : ItemStack` (tâche 5) ; infobulle : genre, date, autre partie, lignes (au plus 24, « … et N autres »).

Règles :
- **Demande** : cible connectée, même dimension, à `tradeMaxDistance` au plus, ni l'un ni l'autre déjà dans un échange, pas soi-même. Le geste : accroupi, main principale vide, clic droit sur un joueur (`EntityInteract`) ; s'il existe déjà une demande de la cible vers soi, le geste l'accepte.
- **Fenêtre** : chaque côté a 12 cases ; poser, retirer, maj-clic, glisser dans ses propres cases. Toute écriture dans une garde (case, pièces ajoutées) et chaque bascule du reçu incrémente la version et efface **les deux** confirmations. Les cases de l'autre : `mayPickup` et `mayPlace` faux, et `clicked` ignore tout clic dont l'indice tombe dans 12–23 (quel que soit le `ClickType`), `canTakeItemForPickAll` et `canDragTo` faux.
- **Boutons de pièces** : « +1 » et « +10 » par coupure : pris par `PlayerCoins.remove` après contrôle `held` (bourses d'abord), versés dans sa garde ; refusés si la garde n'a pas la place.
- **Exécution** (à la seconde confirmation, version égale des deux côtés) : vérifier les deux joueurs connectés, vivants, à portée, fenêtre ouverte sur cette session ; `InventoryFit.fits` pour chacun avec ce qu'il reçoit (plus son reçu si coché) ; sinon `NO_ROOM`, les confirmations tombent et un message dit qui manque de place. Puis : vider les deux gardes (`closeEscrow`), livrer à chacun ce que l'autre a posé (pièces par `PlayerCoins.receive`, objets par `Inventory.add`), reçus, registre : un `transferred(TRADE, valeur, auteur = celui qui donne, "A -> B")` par sens où de l'argent passe (pièces et bourses pleines). Fermer les deux fenêtres.
- **Annulation** (fermeture de l'une des fenêtres, `/trade` refusé, éloignement vérifié toutes les 10 ticks, changement de dimension, déconnexion, mort, arrêt du serveur) : `closeEscrow` de chacun puis `CommerceDelivery.giveBack` à son propriétaire, fenêtre de l'autre fermée avec un message. À la déconnexion, sur `PlayerLoggedOutEvent` ; à la mort, sur `LivingDeathEvent` priorité `HIGHEST` ; à l'arrêt, sur `ServerStoppingEvent`.
- **Rangements** : `TRADE_MENU` déclaré permis (pièces et bourses pleines y passent), ses gardes ne sont pas un `BlockEntity` (le balayage de `MoneyGuard` ne les touche pas) et ne survivent jamais à la fenêtre.

- [ ] **Step 1: Écrire `EchangeGameTests`** (deux `JoueurDeTest`, gestes par `TradeService` et `TradeActionPacket` via `JoueurDeTest.envoyer`, cases par `clicked`) ; deux lots :
  - `demandeTropLoinRefusee`, `demandeExpireApres30s` (horloge du service injectable), `pasDeuxEchangesALaFois` ;
  - `unEchangeNALieuQuApresLesDeuxConfirmations` (une seule → rien n'a bougé ; deux → 5 pain contre 2 or, totaux du registre : créé et détruit inchangés, `totalTransferred(TRADE)` = 200) ;
  - `toucherAuContenuAnnuleLesConfirmations` ;
  - `uneConfirmationSurUneVersionPerimeeEstRefusee` (`STALE`, rien ne bouge) ;
  - `lesCasesDeLAutreNeSePrennentPas` (PICKUP, QUICK_MOVE, SWAP touche 1 et main gauche, PICKUP_ALL double-clic, QUICK_CRAFT glisser sur les cases 12–23 : contenus inchangés) ;
  - `fermerRendToutASonProprietaire` ; `eloignementAnnuleEtRend` ; `mortAnnuleEtRend` ;
  - `deconnexionRendToutAvantLaSauvegarde` (le joueur sort, son playerdata relu du disque contient ses objets posés, une seule fois ; l'autre récupère les siens) ;
  - `inventairePleinRefuseSansRienPerdre` (`NO_ROOM`, gardes intactes, puis fermeture → tout rendu) ;
  - `lesPiecesDeLaBourseSAjoutent` (bourse de 3 or, « +1 or » trois fois → bourse vide, garde 3 or ; annulation → les 3 or reviennent, dans la bourse) ;
  - `uneBoursePleineSEchange` et `leRecuListeCeQuiAEteEchange` (deux reçus, lignes données/reçues, date, nom de l'autre).
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter**, écran compris : deux colonnes (« Vous donnez » / « {nom} donne »), boutons de pièces, case « Reçu », bouton « Confirmer » et état des deux confirmations ; textes mesurés (`font.width`) pour tenir dans le cadre.
- [ ] **Step 4: Lancer sans puis avec `-PwithPack`** (sous le verrou), lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): echange de main a main et recu`, push.

### Task 3: Étal de marché — bloc, propriétaire, stock, caisse, casse

**Files:**
- Create : `economy/stall/MarketStallBlock.java`, `MarketStallBlockEntity.java`, `StallStockHandler.java`, `StallCashStore.java`, `StallOwnerMenu.java`, `StallCashMenu.java`, `StallCashPacket.java`, `StallLedger.java`, `economy/client/StallOwnerScreen.java`, `economy/client/StallCashScreen.java` (fine sous-classe de `CoinAccountScreen`, comme `SafeScreen`), `src/devcontent/.../EtalStockGameTests.java`.
- Modify : `EconomyRegistry` (bloc et objet `market_stall`, entité de bloc, menus ; `MoneyStorageRules.register(STALL_CASH_MENU)`), `BenchNetwork` (`ID_STALL_CASH = 14`), tags (`mineable/axe`, `wither_immune`, `dragon_immune`), langues.

**Interfaces:**
- Consumes : `CoinStore`, `CoinActions.apply`, `CoinAccountMenu`, `CoinRequest`, `MoneyStorageRules.isMoney`, `EconomyLedger.transferred(STAFF_WITHDRAW, …)`.
- Produces :
  - `MarketStallBlock` (orientable) : `static boolean mayManage(Player, @Nullable UUID owner)` (équipe ou propriétaire ; point d'extension du crochetage, lot du policier) ; `static boolean isEmpty(MarketStallBlockEntity)`.
  - `MarketStallBlockEntity` : `owner() : @Nullable UUID`, `ownerName() : String`, `stock() : StallStockHandler` (27 cases), `cash() : EnumMap<Coin, Long>`, `ledger() : StallLedger`, `version() : int` (incrémentée à chaque changement d'offre, tâche 4).
  - `StallStockHandler extends ItemStackHandler` : `isItemValid` faux pour `isMoney` ; `insertOnly() : IItemHandler` (vue exposée en capacité `ITEM_HANDLER` sur toutes les faces : `extractItem` renvoie toujours `ItemStack.EMPTY`).
  - `StallCashStore implements CoinStore` (sur l'entité ; `usesPurses()` vrai, plafond `CoinStore.MAX_PER_COIN`).
  - `StallLedger` : `record Line(long epochMillis, UUID actor, String actorName, Kind kind, String itemId, int count, long amount)`, `enum Kind { SALE, BUYBACK, GOODS_OUT, GOODS_IN, CASH_OUT, CASH_IN, STAFF }` ; `add(Line)` (plafond `stallLedgerLines`), `page(int) : List<Line>`, NBT.
  - `StallCashPacket(BlockPos pos, CoinRequest request)` : même contrôle que `SafeActionPacket.apply` (connecté, distance 8 blocs, bloc présent, `mayManage`, `StallCashMenu` ouvert sur ce bloc).

Règles :
- **Pose** : le poseur (un joueur, sinon refus) devient propriétaire ; aucune limite de nombre.
- **Ouverture** : `mayManage` → `StallOwnerMenu` (onglet Stock : 27 cases + inventaire ; boutons vers Caisse, Offres et Registre, ces deux derniers remplis en tâche 4) ; les autres → la vitrine (tâche 4 ; à cette tâche, un message « vitrine » suffit à prouver le refus de la gestion).
- **Stock** : les cases du stock refusent l'argent (`mayPlace` et `isItemValid`) ; `StallOwnerMenu` n'est **pas** déclaré permis. Chaque objet retiré ou posé par la gestion note `GOODS_OUT`/`GOODS_IN` (par l'équipe : `STAFF`).
- **Caisse** : `StallCashMenu extends CoinAccountMenu` sur `StallCashStore`, déclaré permis ; dépôt et retrait par le propriétaire ou l'équipe, notés `CASH_IN`/`CASH_OUT` (`STAFF` et `transferred(STAFF_WITHDRAW)` pour un retrait de l'équipe dans l'étal d'un autre).
- **Automatismes** : la capacité exposée n'est que la vue `insertOnly` ; l'entité n'implémente pas `Container` (l'entonnoir vanilla ne la voit que par la capacité de Forge) ; la caisse n'est exposée nulle part.
- **Casse** : seulement si stock vide **et** caisse à 0, et seulement par `mayManage` (l'équipe aussi doit vider d'abord) ; sinon progrès de casse nul pour les autres, `BreakEvent` annulé avec un message pour tous (créatif compris). Ne lâche que son bloc. `PushReaction.BLOCK`, résistance aux explosions comme le coffre-fort.
- **Forme** : comptoir, montants et auvent tenus dans le cube ; `noOcclusion()` et `getShape` = union exacte des boîtes du modèle (fixées à la tâche 6, une forme provisoire égale au comptoir suffit ici).

- [ ] **Step 1: Écrire `EtalStockGameTests`** (gabarit `test/plancher` vidé, joueurs `JoueurDeTest`, équipe par `ServerOpListEntry` niveau 4 retirée en fin de test) ; deux lots :
  - `lePoseurEnEstProprietaire` et `unJoueurPoseAutantDEtalsQuIlVeut` ;
  - `unAutreNOuvrePasLaGestion` ; `lEquipeOuvreLaGestion` ;
  - `leStockRefuseLArgent` (pièce, bourse pleine : clic, maj-clic, `insertItem`) ;
  - `uneTremieGarnitLeStock` ; `uneTremieSousLEtalNeVideRien` (entonnoir dessous 5 s, stock et caisse inchangés) ; `unEntonnoirNePoussePasDePieceDansLEtal` ;
  - `laCaisseDeposeEtRetireAValeurEgale` (via `StallCashPacket`) ; `unAutreNeTouchePasALaCaisse` (`NOT_ALLOWED`) ;
  - `pleinNeSeCassePas` (stock non vide ; caisse non vide ; par le propriétaire et par l'équipe en survie et en créatif) ; `videSeCasseParLeProprietaire` ; `unAutreNeLeCassePasMemeVide` ;
  - `explosionEtPistonSansEffet` ; `contenuEtCaisseSurviventAuRechargement` (`saveWithoutMetadata`/`load` sur une copie, puis rechargement de chunk) ;
  - `leRegistreNoteRetraitsEtDepots`.
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter.**
- [ ] **Step 4: Lancer sans puis avec `-PwithPack`** (avec le pack, ajouter `unEntonnoirDeCreateNeVidePasLEtal`), lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): etal de marche, stock et caisse`, push.

### Task 4: Étal de marché — offres, achat, rachat, taxe, registre

**Files:**
- Create : `economy/stall/StallOffer.java`, `StallBuyback.java`, `StallTrade.java`, `StallShopMenu.java`, `StallActionPacket.java`, `StallLedgerPacket.java`, `CommerceMath.java`, `economy/client/StallShopScreen.java`, `src/test/java/dev/oneforallsurvival/economy/CommerceMathTest.java`, `src/devcontent/.../EtalVenteGameTests.java`.
- Modify : `MarketStallBlockEntity` (offres, rachats, version, synchronisation client des offres et de leur disponibilité), `StallOwnerMenu`/`StallOwnerScreen` (onglets Offres et Registre), `BenchNetwork` (`ID_STALL_ACTION = 15`, `ID_STALL_LEDGER = 16`), langues.

**Interfaces:**
- Consumes : `PlayerCoins.held/take/receive`, `CoinMath.makeChange/decompose`, `InventoryFit.fits`, `TreasuryAccount.deposit`, `EconomyLedger.transferred(STALL_SALE | SALES_TAX | STALL_BUYBACK, …)`, `EconomyConfig.salesTaxPercent/stallMaxOffers`.
- Produces :
  - `record StallOffer(ItemStack template, int lot, long price)` (lot 1–64, prix 1–`invoiceMaxAmount`) ; `record StallBuyback(ItemStack template, int lot, long price, int lotsLeft)` (lots 1–9 999) ; NBT des deux.
  - `MarketStallBlockEntity` : `offers() : List<@Nullable StallOffer>`, `buybacks() : List<@Nullable StallBuyback>`, `setOffer(int, @Nullable StallOffer)`, `setBuyback(int, @Nullable StallBuyback)`, `available(int offerIndex) : int` (lots en stock).
  - `CommerceMath` (pur) : `salesTax(long price, int percent) : long` ; `payFromBox(EnumMap<Coin, Long> box, long price, ToLongFunction<Coin> value) : Optional<EnumMap<Coin, Long>>` (nouvelle caisse après paiement, via `makeChange` : caisse − pris + rendu).
  - `enum StallResult { DONE, NO_OFFER, CHANGED, OUT_OF_STOCK, CANNOT_PAY, NO_ROOM, CASH_SHORT, STOCK_FULL, NOTHING_TO_SELL, OWN_STALL, TOO_FAR, NOT_OPEN, GONE, BAD_TEMPLATE }`.
  - `StallTrade.buy(ServerPlayer buyer, MarketStallBlockEntity stall, int index, long seenPrice, int seenLot) : StallResult` ; `StallTrade.sellBack(ServerPlayer seller, MarketStallBlockEntity stall, int index, long seenPrice, int seenLot) : StallResult`.
  - `StallActionPacket(BlockPos pos, int action, int index, long price, int lot, int lots)`, actions `BUY`, `SELL_BACK`, `SET_OFFER` (modèle = la pile du curseur côté serveur), `CLEAR_OFFER`, `SET_BUYBACK`, `CLEAR_BUYBACK`, `REQUEST_LEDGER`.
  - `StallLedgerPacket` (serveur → client) : une page du registre, envoyée seulement à `mayManage`.

Règles :
- **Achat** (`buy`) — vérifier : offre présente avec ce prix et ce lot (sinon `CHANGED`) ; acheteur ≠ propriétaire ; `lot` objets identiques au modèle en stock ; `CoinMath.makeChange(PlayerCoins.held(buyer), price)` possible ; `InventoryFit.fits` pour le lot (découpé en piles de taille maximale). Puis engager : `PlayerCoins.take(buyer, price)`, retirer le lot du stock, le donner, `tax = salesTax(price, salesTaxPercent)`, `TreasuryAccount.deposit(decompose(tax))`, caisse `+ decompose(price - tax)`, `transferred(STALL_SALE, price - tax, buyer, "<acheteur> -> etal de <proprio> en x y z")`, `transferred(SALES_TAX, tax, buyer, …)` si non nul, ligne `SALE` au registre de l'étal.
- **Rachat** (`sellBack`) — vérifier : rachat présent, même prix et lot, `lotsLeft > 0`, vendeur ≠ propriétaire, `lot` objets identiques au modèle dans l'inventaire du vendeur, place dans le stock (simulation sur une copie du handler), `payFromBox(cash, price)` possible (sinon `CASH_SHORT`). Puis engager : objets retirés du vendeur vers le stock, caisse remplacée, `PlayerCoins.receive` de `decompose(price)` chez le vendeur (place : pièces en bourse, sinon comprises dans la vérification), `lotsLeft - 1`, `transferred(STALL_BUYBACK, price, owner, …)`, ligne `BUYBACK`.
- **Offres** : seul `mayManage` les écrit ; un modèle `isMoney` est refusé (`BAD_TEMPLATE`) ; chaque écriture incrémente `version()` et resynchronise les clients qui regardent.
- **Vitrine** (`StallShopMenu`, ouverte par tous ; `StallShopScreen`) : pour chaque offre, l'objet, le lot, le prix formaté, la disponibilité ; boutons « Acheter » / « Vendre ». Le bouton envoie le prix et le lot affichés. Le propriétaire absent ou hors ligne ne change rien : l'étal est dans un chunk chargé par l'acheteur.
- **Registre** : onglet de la gestion, paginé, lu par `StallLedgerPacket` ; jamais envoyé à un autre joueur.

- [ ] **Step 1: Écrire `CommerceMathTest`** : `taxeZeroParDefaut`, `taxeArrondieVersLeBas` (999 à 10 % → 99), `taxeCentPourCent`, `laCaissePaieEnCassantSesPieces` (1 or pour 25 → caisse 7 argent 5 cuivre), `laCaisseNePaiePasPlusQuElleNA`, `valeurConservee` (valeur avant = valeur après + prix).
- [ ] **Step 2: Écrire `EtalVenteGameTests`** ; deux lots :
  - `lEtalVendVendeurAbsent` (le propriétaire `JoueurDeTest` sort avant l'achat ; l'acheteur reçoit 4 pain, la caisse 30 cuivres, registre `SALE`) ;
  - `lAcheteurPaieAvecSaBourseEtRecoitLaMonnaie` (bourse 1 or, prix 30 → bourse 7 argent) ;
  - `pasAssezDArgentRienNeBouge` ; `inventairePleinRienNeBouge` ; `stockInsuffisantRefuse` ;
  - `prixChangeEntreLAffichageEtLAchatRefuse` (`CHANGED`, rien ne bouge) ;
  - `deuxAcheteursLeDernierLot` (deux paquets dans le même tick : un `DONE`, un `OUT_OF_STOCK`, argent total conservé) ;
  - `unObjetAuNbtDifferentNeSeVendPas` (épée nommée en stock, modèle sans nom) ;
  - `laTaxeArriveAuTresor` (config à 10 % le temps du test, rendue après : trésor + 3, caisse + 27, registre global) ;
  - `rachatPayeSurLaCaisse` (8 blé contre 16 cuivres, `lotsLeft` décrémenté) ; `rachatCaisseInsuffisanteRefuse` ; `rachatStockPleinRefuse` ; `rachatEpuiseRefuse` ;
  - `leProprietaireNAchetePasChezLui` ; `unePieceRefuseeCommeModele` ;
  - `leRegistreDeLEtalNEstEnvoyeQuAuProprietaire` ;
  - `aucunePieceCreeeNiDetruite` (après une vente et un rachat : `inCirculation` inchangé, somme inventaires + caisse + trésor inchangée).
- [ ] **Step 3: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 4: Implémenter**, écrans compris (textes mesurés et rétrécis pour tenir, prix les plus longs essayés).
- [ ] **Step 5: Lancer `./gradlew --offline test runGameTestServer`, puis avec `-PwithPack`** (sous le verrou), lire « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6: Commit** `feat(economy): offres, achat, rachat et taxe de l'etal`, push.

### Task 5: Facture (72) — carnet, facture, paiement, reçu

**Files:**
- Create : `economy/invoice/InvoiceBookItem.java`, `InvoiceItem.java`, `InvoiceRegistry.java`, `InvoiceService.java`, `InvoiceCommands.java`, `InvoiceFormPacket.java`, `InvoiceIssuePacket.java`, `InvoicePayPacket.java`, `economy/client/InvoiceFormScreen.java`, `economy/client/InvoiceScreen.java`, `src/devcontent/.../FactureGameTests.java`.
- Modify : `EconomyRegistry` (objets `invoice_book`, `invoice`), `Receipt.forInvoice`, `BenchNetwork` (`ID_INVOICE_FORM = 17`, `ID_INVOICE_ISSUE = 18`, `ID_INVOICE_PAY = 19`), langues.

**Interfaces:**
- Consumes : `PlayerCoins.take`, `CommerceDelivery.payTo/giveBack`, `HeldGoods.addPending`, `EconomyLedger.transferred(INVOICE, …)`, `Receipt`, `EconomyConfig.invoice*`.
- Produces :
  - `InvoiceRegistry.get(MinecraftServer)` (clé `oneforallsurvival_invoices`) :
    - `record Invoice(UUID id, UUID issuer, String issuerName, UUID debtor, String debtorName, long amount, String reason, long issuedAt, Status status, long settledAt)` ; `enum Status { DUE, PAID, SETTLED_BY_HAND }` ;
    - `issue(ServerPlayer issuer, ServerPlayer debtor, long amount, String reason, long now) : Invoice` ; `get(UUID) : Optional<Invoice>` ; `markPaid(UUID id, long now) : boolean` (vrai seulement de `DUE` à `PAID`) ; `settleByHand(UUID id, UUID issuer, long now) : boolean` ;
    - `issuedBy(UUID)`, `owedBy(UUID) : List<Invoice>` ; `unseenPaid(UUID issuer) : List<Invoice>` et `markSeen(UUID issuer)` (avis à la connexion) ; oubli des factures réglées après `invoiceKeepDays`.
  - `InvoiceItem` (`invoice`, pile de 1) : NBT `Id`, `Issuer`, `IssuerName`, `Debtor`, `DebtorName`, `Amount`, `Reason`, `IssuedAt` ; `static ItemStack of(Invoice)` ; `static Optional<UUID> idOf(ItemStack)`.
  - `InvoiceService` :
    - `enum IssueResult { DONE, SELF, TOO_FAR, BAD_AMOUNT, BAD_REASON, NO_BOOK, GONE }` ; `issue(ServerPlayer issuer, UUID debtor, long amount, String reason) : IssueResult` ;
    - `enum PayResult { PAID_TO_INVENTORY, PAID_TO_SAFE, PAID_PENDING, NOT_DEBTOR, ALREADY_SETTLED, UNKNOWN, CANNOT_PAY }` ; `pay(ServerPlayer payer, int slot) : PayResult`.
  - `InvoiceFormPacket(UUID target, String targetName)` (serveur → client, ouvre le formulaire) ; `InvoiceIssuePacket(UUID target, long amount, String reason)` ; `InvoicePayPacket(int slot)`.
  - Commandes niveau 0 : `/invoice list`, `/invoice settle <id>` (identifiant complet ou court de 8 caractères, unique parmi celles de l'émetteur).

Règles :
- **Émettre** : carnet en main, clic droit sur un joueur à `invoiceMaxDistance` au plus → `InvoiceFormPacket` ; le formulaire envoie montant et motif ; le serveur revérifie tout (carnet toujours en main, cible connectée et proche, montant 1–`invoiceMaxAmount`, motif 1–`invoiceReasonMax` caractères sans saut de ligne, pas soi-même). Le carnet ne s'use pas. La facture va dans l'inventaire du débiteur, sinon en attente (`giveBack`) ; les deux sont prévenus.
- **Payer** (`pay`, depuis l'écran de la facture, bouton « Payer ») : la pile de la case porte un identifiant connu, `DUE`, dont le débiteur est le payeur ; `PlayerCoins.take(payer, amount)` (bourses et inventaire, monnaie rendue, tout ou rien) ; `markPaid` ; `CommerceDelivery.payTo(server, issuer, amount)` ; `transferred(INVOICE, amount, payer, "facture <id court> <debiteur> -> <emetteur>")` ; la pile devient `Receipt.forInvoice(…, PAID)` dans la même case. Un clone d'une facture payée répond `ALREADY_SETTLED` et devient reçu.
- **Main à main** : `/invoice settle` par l'émetteur seulement ; aucun mouvement de pièces ; la facture du débiteur devient un reçu « réglée de main à main ».
- **Vue** : `InvoiceItem.inventoryTick` (serveur, une fois par seconde) change en reçu toute facture que le registre dit réglée ; infobulle : émetteur, débiteur, motif, montant formaté, date (`dd/MM/yyyy HH:mm`, fuseau du serveur), identifiant court. Une facture se jette, se range partout ; elle n'est pas de l'argent.
- **Avis** : l'émetteur connecté reçoit un message au paiement ; absent, à sa connexion (`unseenPaid`), avec la destination des pièces (inventaire, coffre-fort, en attente).
- **Non prélevable** : rien ne prend une facture entre joueurs à distance ; ce sera la feature 86 (lot E6), qui ajoutera un genre public au registre des factures.

- [ ] **Step 1: Écrire `FactureGameTests`** ; deux lots :
  - `leCarnetSurUnJoueurProcheOuvreLeFormulaire` (paquet reçu par le `JoueurDeTest`) ; `tropLoinRefuse` ; `montantsEtMotifsInvalidesRefuses` (0, −5, max + 1, motif vide, motif de 81 caractères, soi-même) ;
  - `emettreDonneUneFactureAuDebiteur` (NBT complet, registre `DUE`) ;
  - `payerPrendInventaireEtBoursesEtRendLaMonnaie` (facture 250, bourse 2 or + 1 émeraude en vrac → 250 payés, monnaie juste) ; `pasAssezRienNeBouge` (`CANNOT_PAY`, facture toujours due) ; `seulLeDebiteurPaie` (`NOT_DEBTOR`) ;
  - `emetteurConnecteRecoitDansSonInventaire` ; `emetteurHorsLigneAvecCoffreFort` (`PAID_TO_SAFE`, `SafeAccounts.total` + montant) ; `emetteurSansCoffreFortEnAttenteJusquaSaConnexion` (`PAID_PENDING`, livré et avisé à la connexion) ;
  - `payeeDevientUnRecu` ; `uneCopieNeSePaiePasDeuxFois` (copie de la pile avant paiement : la seconde répond `ALREADY_SETTLED`, aucune pièce ne bouge, elle devient reçu) ; `unIdentifiantInconnuNeSePaiePas` ;
  - `acquitterDeMainAMain` (seul l'émetteur ; la facture devient reçu au tick suivant) ;
  - `leRegistreDesFacturesSurvitAuRechargement` ;
  - `aucunePieceCreeeNiDetruite` (`inCirculation` inchangé ; `totalTransferred(INVOICE)` = montant).
- [ ] **Step 2: Lancer** (sous le verrou), constater l'échec.
- [ ] **Step 3: Implémenter**, écrans compris : formulaire (montant en cuivres avec aperçu formaté, motif, « Émettre ») ; facture (texte complet, « Payer » pour le débiteur seulement, grisé sinon).
- [ ] **Step 4: Lancer sans puis avec `-PwithPack`** (sous le verrou), lire « All N required tests passed ».
- [ ] **Step 5: Commit** `feat(economy): factures entre joueurs`, push.

### Task 6: Recettes et visuels finaux

**Files:**
- Create :
  - recettes de table `data/oneforallsurvival/recipes/market_stall.json` (forme `WWW` / `S S` / `PPP` : `#minecraft:wool`, `minecraft:stick`, `#minecraft:planks`) et `invoice_book.json` (sans forme : `minecraft:book`, `minecraft:ink_sac`, `minecraft:feather`) ; sans condition `mod_loaded`, jamais à l'établi ; aucune recette pour `invoice` ni `receipt` ;
  - modèle de bloc de l'étal (comptoir, quatre montants, auvent rayé, dans le cube), états orientés, modèle d'objet ; modèles d'objet du carnet, de la facture, du reçu ; `tools/gen_textures_e2.py` et ses textures ;
  - `economy/client/StallRenderer.java` (les trois premières offres posées sur le comptoir).
- Modify : `MarketStallBlock.getShape` (union exacte des boîtes du modèle), `EconomyRegistry.tabItems`, `bench/BenchCatalog` (si la garde des objets d'E1 n'est pas générique), `DemoVisuels` (une zone à part, hors du champ des lots précédents), `PackGameTests` (étal et carnet fabricables à la table, jamais à l'établi), langues.

Captures (`runDemoVisuels`, sous le verrou), chacune ouverte et vérifiée :
- l'étal seul, collé à un mur et entouré de blocs pleins (aucune face voisine manquante), de jour et de nuit, avec trois offres sur le comptoir ;
- la vitrine avec 9 offres aux prix les plus longs ; la gestion, onglets Stock, Offres, Caisse (écran commun), Registre plein ;
- la fenêtre d'échange des deux côtés, avec pièces, bourse, reçu coché et une confirmation ;
- le formulaire de facture rempli ; l'écran de la facture ; les infobulles de la facture et des deux reçus ;
- la grille des objets du lot à côté de leurs équivalents vanilla (livre, papier, établi).

- [ ] **Step 1: Écrire les tests de `PackGameTests`** (`lEtalSeFabriqueALaTable`, `leCarnetSeFabriqueALaTable`, `niLEtalNiLeCarnetNiLaFactureALEtabli`) et `EtalStockGameTests.laFormeSuitLeModele` (forme ≠ cube plein, `noOcclusion` : un bloc voisin garde sa face).
- [ ] **Step 2: Lancer avec `-PwithPack`** (sous le verrou), constater l'échec.
- [ ] **Step 3: Produire recettes, modèles, textures, rendu.**
- [ ] **Step 4: Lancer `runDemoVisuels`** (sous le verrou, « [souris] rendue » dans le journal), ouvrir chaque capture et la vérifier ; corriger et relancer tant qu'un texte déborde ou qu'une face manque.
- [ ] **Step 5: Lancer sans puis avec `-PwithPack`**, lire « All N required tests passed ».
- [ ] **Step 6: Commit** `feat(e2): recettes et visuels du commerce`, push.

### Task 7: Preuve de bout en bout, captures, vérification, wiki

**Files:**
- Create : `dev/E2eCommerce.java` (client), `dev/E2eCommerceServeur.java` (commandes de console niveau 4, réservées au RCON, sur le modèle d'`E2eMonnaieServeur` : second joueur `Autre` sur connexion simulée par `JoueurDeTest`, ses gestes côté serveur, pose de l'étal d'`Autre`), `docs/verifications/lot-e2.md`.
- Modify : `dev/E2eClient.java` (phase `COMMERCE` juste après `MONNAIE`, avant les étapes qui doivent finir juste avant l'arrêt ; `E2E_MONNAIE_SEULE` enchaîne `MONNAIE` puis `COMMERCE`, au passage comme à la relance), `wiki/data/economie.json`, `tasks/todo.md`.

**Relance : choix et justification.** La relance complète échoue aujourd'hui sur la couveuse du lot 5 (compteur parti au démarrage dans les chunks de départ, ~1 min 50 de connexion du client pour 45 s de marge). Ce plan **ne corrige pas** ce banc : la correction toucherait la preuve d'un autre lot (déplacer la couveuse hors des chunks de départ, ou changer son seuil) et sa justesse se prouverait sur le lot 5, pas ici. Il fait :
- le **passage complet** (tous les lots, puis E1 et E2) avec le pack, qui garde la non-régression des autres lots jusqu'à l'arrêt ;
- une **relance ciblée économie** (`E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) qui relit E1 puis E2 après `/stop` : c'est exactement ce que la relance doit prouver pour ce lot (garde, attente, étal, caisse, offres, registres, factures relus).
- Le défaut du banc reste ouvert dans `tasks/todo.md`, tel qu'il est écrit.

**Essai sur serveur dédié avec le pack** (client `Dev` sans droits, `Autre` simulé ; monde neuf, l'ancien rangé hors du dépôt ; le joueur amené sur une aire bâtie d'abord, garde « sous la dalle » coupée avant de le ramener ; droits rendus et objets propres seulement rangés en sortie ; clics dépendants en pas séparés qui attendent la réponse du serveur, voir `tasks/lessons.md`) :
- **Échange** : `Autre` propose ; `Dev` accepte par le lien du message ; `Dev` verse 2 or par le bouton de sa bourse, `Autre` pose 5 pain ; `Dev` confirme, `Autre` change une case : la confirmation de `Dev` tombe (lue à l'écran) ; deux confirmations ; reçu coché ; inventaires et reçus contrôlés, `/eco ledger` montre le transfert. Puis un second échange abandonné par l'éloignement de `Dev` : tout revient.
- **Étal vendeur absent** : `Autre` pose et garnit un étal (pain, offre 4 pain pour 30 cuivres, rachat 8 blé pour 16 cuivres), dépose 100 cuivres en caisse, puis **se déconnecte** ; `Dev` ouvre la vitrine par un vrai clic droit, achète (monnaie rendue dans la bourse), revend du blé ; `Dev` ne peut ni ouvrir la gestion, ni casser l'étal ; un entonnoir sous l'étal ne prend rien.
- **Étal par `Dev`** : il fabrique un étal à la table, le pose, ouvre la gestion, garnit le stock et définit une offre par de vrais clics.
- **Factures** : `Dev` utilise le carnet sur `Autre` revenu, tape montant et motif, émet ; `Autre` paie côté serveur → pièces dans l'inventaire de `Dev`. `Autre` émet une facture à `Dev`, se déconnecte ; `Dev` la paie par l'écran → les pièces vont en attente pour `Autre` (pas de coffre-fort) et la facture devient un reçu ; une copie de la facture remise à `Dev` par la console ne se paie pas.
- **Comptes** : `/eco stats` avant `/stop` : en circulation inchangé par le lot, transferts attendus calculés d'avance, en attente = ce qui est dû à `Autre`.
- **Après `/stop` et relance ciblée** : étal (stock, caisse, offres, registre) relu ; `Autre` se reconnecte et reçoit son attente, avec l'avis de paiement ; registre des factures et `/eco stats` identiques, en attente à 0 après la livraison.

- [ ] **Step 1: Écrire l'E2E** (client et serveur), lancer sous le verrou : `runServer -PwithPack` puis `runE2eClient -PwithPack` (passage complet), nouveau `runServer -PwithPack` puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`. Lire « [e2e] bilan : TOUT PASSE » aux deux passages et « [souris] rendue » dans le journal du client ; aucun client laissé ouvert, verrou vidé.
- [ ] **Step 2: Relire les captures de la tâche 6** si un écran a changé depuis ; refaire celles qui ne sont plus vraies.
- [ ] **Step 3: Écrire `docs/verifications/lot-e2.md`**, au format de `lot-e1.md` : commandes, heures et comptes lus ; séparer prouvé et non prouvé. Couvre au moins : crochetage de l'étal (lot du policier) ; trésor sans bloc (lot E3) ; prélèvement des factures (lot E6) ; second joueur simulé, pas un vrai client ; relance complète du banc toujours en échec sur la couveuse (relance ciblée faite à la place) ; stockages de mods non installés face à l'étal ; équilibrage.
- [ ] **Step 4: Mettre à jour le wiki** : entrées `main-a-main`, `etal` et `facture` de `wiki/data/economie.json` à `dispo`, au format des entrées `dispo` d'E1 (étapes, chiffres, bloc `staff` : taxe, distances, attente), images des captures.
- [ ] **Step 5: Mettre à jour `tasks/todo.md`** : ligne E2 cochée avec le résumé et les liens (plan archivé dans `archive/`, vérification) ; restes relevés.
- [ ] **Step 6: Commit et push** par nom de fichier : `test(e2): essai de bout en bout du commerce`, puis `docs(e2): preuve du lot E2`.

---

## Auto-relecture (faite à l'écriture)

- **Couverture du cahier** : 70 → tâche 2 ; 71 → tâches 3 et 4 (taxe, rachat, vendeur absent, trémie, casse vide, registre) ; 72 → tâche 5 ; feature 84 pour les nouveaux écrans → tâches 2, 3 ; « aucune pièce créée » → tâche 1 (transferts) et un test `aucunePieceCreeeNiDetruite` par tâche d'argent ; lignes E2 de la section 8 (échange après deux confirmations, étal vendeur absent, trémie, étal plein, taxe au trésor, facture payée en l'utilisant) → tâches 2 à 5 ; scénario d'abus « déconnexion pendant un échange » et « casse d'un étal » → tâches 2 et 3 ; scénario d'acceptation « pain à l'étal de l'agriculteur absent » → tâche 7. Crochetage, trésor en bloc et prélèvement sont renvoyés et dits dans la vérification.
- **Noms** : `TradeService`/`TradeSession`, `MarketStallBlockEntity`, `StallTrade`, `InvoiceService`, `CommerceDelivery.payTo/giveBack`, `HeldGoods.openEscrow/closeEscrow/addPending`, `MoneyTransfer` sont les mêmes dans toutes les tâches.
- **Réseau** : identifiants 13 à 19, protocole « 6 » posé une fois (tâche 2).
