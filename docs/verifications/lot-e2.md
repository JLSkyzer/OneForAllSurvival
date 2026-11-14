# Vérifications du lot E2 : paiements, étal et factures

Relevés du 2026-11-14, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 7 (preuve de bout en bout, vérification, wiki). Les tâches 1 à 6 ont leurs GameTests et JUnit, relancés ici. Plan : [`2026-11-13-lot-e2-commerce.md`](../superpowers/plans/2026-11-13-lot-e2-commerce.md). Commits du lot : `08a50ef`, `df9cd90` (socle), `cfe2e0a`, `94c8008` (échange), `97de758`, `9bae4a0`, `43e14b9`, `b8fff04` (étal), `3741cc1`, `40dc2a1` (factures), `e5d8e89`, `31d7076`, `f23fdeb` (recettes, visuels, écrans), puis l'essai de bout en bout et ce document.

**État : le lot est prouvé dans les limites de la section « Non prouvé ».** Échange de main à main, étal (vendeur absent compris), factures et attente gardée par le serveur passent un essai de bout en bout sur serveur dédié avec le pack complet, par un joueur sans droits, et sa relance après un vrai `/stop`. **Le second joueur de l'essai n'est pas un vrai client** : un seul client peut tourner à la fois (Killian travaille à distance), le second joueur `Autre` est une connexion simulée côté serveur (`JoueurDeTest`) qui passe par les mêmes événements de connexion et de déconnexion qu'un vrai joueur ; ses gestes passent par les mêmes méthodes du serveur que les paquets (`TradeService`, `StallActionPacket.apply`, `StallCashPacket.apply`, `InvoiceService.issue/pay`), pas par le réseau ni par un écran.

## Décisions du lot

| Sujet | Décision |
|---|---|
| Taxe sur les ventes (71) | 0 % par défaut, réglable ; versée à un compte du trésor gardé par le serveur (sans bloc), que le lot E3 rattachera au bloc du trésor. Un transfert au registre, ni source ni puits. |
| Crochetage de l'étal | Renvoyé au lot du policier (feature 67) ; point d'extension `MarketStallBlock.mayManage`. |
| Étal (71) | Fabriqué par n'importe qui, le poseur en est propriétaire ; 27 cases de stock, 9 offres de vente et 9 de rachat ; caisse en pièces ; trémies garnissent, ne vident jamais, ne touchent pas la caisse ; ne se casse que vide, par le propriétaire ou l'équipe ; registre de 200 lignes. Pas d'achat ni de rachat chez soi. Modèle d'offre en copie (pas de case fantôme). |
| Échange (70) | Proposé à 5 blocs (`/trade` ou accroupi + clic droit main vide), accepté par lien ; 12 cases par côté et boutons « +1 / +10 » qui versent depuis les bourses ; toute modification annule les deux confirmations (version) ; atomique ; fermeture, déconnexion, éloignement, mort : tout revient. Reçu sur demande, un pour chacun. Garde (`HeldGoods`) sauvegardée avec le monde. |
| Facture (72) | Carnet utilisé sur un joueur proche, formulaire (montant, motif) ; facture payable en l'utilisant (écran, bouton « Payer »), tout ou rien ; pièces à l'émetteur : inventaire, sinon coffre-fort, sinon **attente gardée par le serveur** jusqu'à sa connexion ; payée, elle devient un reçu ; le registre des factures fait foi (une copie ne se paie pas). Règlement de main à main déclaré par l'émetteur (`/invoice settle`). Aucun prélèvement à distance (lot E6). |
| Livraison en attente | Livrée à la connexion puis toutes les 30 s ; visible dans `/eco stats` (« Pending »). Exception à la liste de la feature 84 décidée par le contrôleur. |
| Place à l'arrivée | Échange, achat et rachat refusés avant tout mouvement si ce qui arrive ne tient pas ; ce qui revient d'une garde va en attente, jamais au sol. |
| Relance de l'essai | La relance complète du banc échoue sur la couveuse du lot 5 (défaut du banc, ouvert dans `tasks/todo.md`) ; ce lot fait le passage complet avec le pack, puis une **relance ciblée** de l'économie (`E2E_MONNAIE_SEULE=1 runE2eRelance`). |
| Ordre de la relance ciblée | **À la relance, l'étape du commerce passe avant celle de la monnaie** : la première reconnexion d'`Autre` après l'arrêt doit être celle que lit l'étape du commerce (livraison de l'attente, avis de paiement). La relance d'E1 lit ce que porte `Autre` (noté par l'étape du commerce avant l'arrêt, attente livrée comprise : 570) au lieu de 500 fixe, et compare `/eco stats` sans la ligne « Pending » (l'attente a été livrée entre-temps, l'étape du commerce le vérifie). Accepté par le contrôleur le 2026-11-14. |
| Film | Aucun à ce lot (un film unique de l'économie après E6, décision de Killian). |

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test runGameTestServer` (17:19 à 17:21) | « All 719 required tests passed » (`run-gametest/logs/latest.log`, 17:21:08) ; JUnit : 383 tests, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml`) |
| `./gradlew --offline runGameTestServer -PwithPack` (17:21 à 17:23) | « All 719 required tests passed » (17:22:58) |

Malgré `--offline`, la tâche `downloadAssets` de ForgeGradle affiche « Downloading: …/5.json » (l'index des ressources de Minecraft 1.20.1) ; la même ligne figure dans les journaux des lancements précédents. Aucune dépendance nouvelle.

## Serveur dédié et vrai client

`runServer -PwithPack` puis `runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack` et `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`, sous le verrou de lancement. Client `Dev` avec le pack complet, fenêtre ouverte sur le second écran (« [fenetre] second écran (2600, 40) » à chaque lancement, `dev/FenetreSecondEcran`). L'étape est `E2eCommerce.java` (client) et `E2eCommerceServeur.java` (commandes de console réservées au RCON : le second joueur, ses gestes côté serveur, lecture d'un étal, des factures et des comptes), dans `src/devcontent/java/dev/oneforallsurvival/dev/`. Elle suit l'étape de la monnaie (E1). Chaque passage qui fait foi part d'un **monde neuf** ; les anciens sont rangés hors du dépôt, dans le dossier temporaire de la session.

Le joueur est amené sur une aire bâtie (dalle à `départ + (100, 150, 130)`), puis rendu **non opérateur** (`deop` par la console RCON, `hasPermissions(2)` lu faux côté client, survie). Tout ce qui change le monde passe par la console RCON ; ses gestes sont de vrais clics : lien du message, boutons et cases de la fenêtre d'échange, table de fabrication, pose des blocs, clic droit sur l'étal et sur le joueur, onglets, cases et champs tapés de la gestion de l'étal, formulaire de facture, bouton « Payer ».

### Passages

| Passage | Connexion | Bilan | Contrôles |
|---|---|---|---|
| E1 + E2 seuls (`E2E_MONNAIE_SEULE=1 runE2eClient`), monde neuf ; sa relance a échoué sur le banc (incident 3) | 16:44:11 | « TOUT PASSE » à 16:47:17 | 98 `PASS`, 0 `FAIL` |
| E1 + E2 seuls, après la correction, monde neuf | 16:54:20 | « TOUT PASSE » à 16:57:26 | 98 `PASS`, 0 `FAIL` |
| Relance ciblée de ce monde (`E2E_MONNAIE_SEULE=1 runE2eRelance`) | 16:59:17 | « TOUT PASSE » à 16:59:49 | 15 `PASS`, 0 `FAIL` |
| **Passage complet** (`runE2eClient -PwithPack` : lots 1 à 7, E1, E2, puis lot 5), monde neuf | 17:03:36 | « TOUT PASSE » à 17:14:46 | 289 `PASS`, 0 `FAIL` |
| **Relance ciblée** de ce monde (`E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) : E2 puis E1 | 17:16:15 | « TOUT PASSE » à 17:16:46 | 15 `PASS`, 0 `FAIL` |

Les lignes citées plus bas sont celles du premier passage E1 + E2 seuls (16:44) ; les deux autres passages donnent les mêmes 42 contrôles de l'étape du commerce, avec d'autres positions et identifiants, et le même bilan des comptes (11 650 en circulation, inchangé).

« [souris] rendue » est lu dans le journal de chaque client ; aucun client ni serveur n'est resté ouvert (le serveur s'arrête par `/stop`) ; le verrou est vidé après chaque lancement.

### Ce qui passe : l'échange

```
[e2e] PASS le message de proposition arrive chez Dev avec un lien cliquable : /trade accept Autre
[e2e] PASS Dev accepte par le lien du message (/trade accept Autre envoye par le clic) : fenetre d'echange ouverte chez les deux (Trade with Autre)
[e2e] PASS Autre pose 5 pains : Dev les voit dans la moitie de l'autre (POSE case=0 dans=5 curseur=0 version=5)
[e2e] PASS Dev verse 2 or par le bouton « +1 » de la fenetre : sa bourse passe de 900 a 700 cuivres, 2 pieces d'or dans sa moitie, aucune piece libre (0)
[e2e] PASS case Recu cochee (vue par le client : version 8)
[e2e] PASS Dev confirme : l'ecran montre sa confirmation (version 8), l'autre en attente (false)
[e2e] PASS Autre change une case (une pomme) : la confirmation de Dev tombe, lue a l'ecran (selfConfirmed=false, version 8 -> 9)
[e2e] PASS echange fait apres deux confirmations : Dev recoit 5 pains, 1 pomme et un recu (genre trade), il porte 700 cuivres ; Autre : 700 cuivres, objets [bread:3,receipt:1]
[e2e] PASS /eco ledger montre le transfert : ...  2 golds  Trade between players  Dev: Dev -> Autre (total des echanges 200 cuivres)
[e2e] PASS le geste (accroupi, main vide, clic droit sur le joueur) propose l'echange : « Trade offered to Autre »
[e2e] PASS second echange abandonne par l'eloignement de Dev (« too far apart ») : la fenetre se ferme, Dev retrouve sa piece d'or (bourse 700 cuivres), Autre ses 3 pains ([bread:3,receipt:1], 700 cuivres) ; rien au sol
```

### Ce qui passe : l'étal du vendeur absent

```
[e2e] PASS Autre pose son etal en 95, 150, 195 : proprietaire Autre (vu par le client)
[e2e] PASS etal d'Autre garni par ses gestes : ... stock=[bread:15] caisse=100 offres=[0:breadx4@30;] rachats=[0:wheatx8@16,reste5;] ...
[e2e] PASS Autre se deconnecte (PARTI porte=600 ...)
[e2e] PASS Dev ouvre la vitrine de l'etal d'Autre absent par un vrai clic droit (StallShopScreen, pas la gestion) : l'offre 0 annonce 3 lots
[e2e] PASS achat a l'etal du vendeur absent : +4 pains, Dev passe de 700 a 670 cuivres, monnaie rendue dans la bourse (670 cuivres, 0 en vrac), la vitrine annonce maintenant 2 lots
[e2e] PASS rachat par l'etal du vendeur absent : 8 bles vendus, Dev passe de 670 a 686 cuivres
[e2e] PASS Dev ne peut pas gerer l'etal d'Autre : le clic droit ouvre la vitrine, et l'ecriture d'une offre envoyee de lui-meme n'a rien ecrit (offres [0:breadx4@30;])
[e2e] PASS Dev ne casse pas l'etal d'Autre (3 s de clic gauche en survie) : bloc present cote client et serveur (Test passed)
[e2e] PASS l'entonnoir sous l'etal ne prend rien (... []) ; etal : stock [bread:11,wheat:8], caisse 114 (100 + 30 - 16), rachats [0:wheatx8@16,reste4;], registre 4:[GOODS_IN/Autre/bread/15/0;CASH_IN/Autre//0/100;SALE/Dev/bread/4/30;BUYBACK/Dev/wheat/8/16;]
```

L'étal d'`Autre` est posé et garni par ses gestes côté serveur (stock, offre de vente 4 pains pour 30 cuivres, offre de rachat 8 blés pour 16 cuivres, 100 cuivres en caisse), puis `Autre` se déconnecte. `Dev` ouvre la vitrine par un vrai clic droit, achète (la monnaie rendue entre dans sa bourse), revend du blé ; il ne peut ni gérer l'étal (un paquet d'écriture d'offre forgé est refusé), ni le casser ; un entonnoir posé dessous ne prend rien.

### Ce qui passe : l'étal de Dev

```
[e2e] PASS etal fabrique a la table avec sa recette (laine, batons, planches)
[e2e] PASS etal de Dev pose par un clic droit reel en 105, 150, 195 : proprietaire Dev
[e2e] PASS gestion de l'etal ouverte par son proprietaire (StallOwnerScreen, onglet Stock)
[e2e] PASS stock garni par de vrais clics : 5 pains dans la case 0
[e2e] PASS offre definie par de vrais clics (case, curseur, champs tapes, Definir) : StallOffer[2 x bread pour 20] ; le pain reste au joueur (curseur 4)
[e2e] PASS etal de Dev relu sur le serveur : stock [bread:5], offres [0:breadx2@20;], registre 1:[GOODS_IN/Dev/bread/5/0;]
```

### Ce qui passe : les factures

```
[e2e] PASS carnet de factures fabrique a la table avec sa recette (livre, poche d'encre, 2 papiers)
[e2e] PASS formulaire de facture ouvert par le carnet utilise sur Autre : Invoice for Autre
[e2e] PASS facture emise par le formulaire : Autre la recoit ([1:...:150])
[e2e] PASS Autre paie : PAID_TO_INVENTORY, il passe de 600 a 450 cuivres, sa facture devient un recu (invoice_paid) ; Dev passe de 686 a 836 cuivres (bourse 836)
[e2e] PASS Autre emet une facture a Dev (EMISE resultat=DONE distance=2.0 messages=message.oneforallsurvival.invoice.issued) : Dev la porte (120 cuivres, de Autre) ; Autre se deconnecte en portant 450 cuivres
[e2e] PASS Dev paie par l'ecran : il passe de 836 a 716 cuivres, la facture devient un recu (invoice_paid), 120 cuivres en attente pour Autre (absent, sans coffre-fort)
[e2e] PASS une copie de la facture payee ne se paie pas : Dev garde 716 cuivres, la copie est devenue un recu, attente 120 et total des factures 270 inchanges
```

### Ce qui passe : les comptes avant l'arrêt

```
/eco stats : ... In circulation (created minus destroyed): 1 ruby 1 emerald 6 golds 5 silvers
Transferred (neither created nor destroyed):
  Trade between players: 2 golds
  Sale at a stall: 3 silvers
  Sales tax: 0 copper
  Buyback by a stall: 1 silver 6 coppers
  Withdrawn by staff (transfer): 0 copper
  Invoice paid: 2 golds 7 silvers
Treasury: 0 copper
Pending: 1 gold 2 silvers
```

Comptes calculés d'avance : transferts 200 (échange), 30 (vente), 0 (taxe), 16 (rachat), 270 (factures 150 + 120), 0 (équipe) ; trésor 0 ; en attente 120 (dû à `Autre`). **En circulation 11 650, inchangé depuis le début de l'étape** = coffres-forts 10 250 + porté par `Autre` 450 + attente 120 + caisse de l'étal d'`Autre` 114 + caisse de l'étal de `Dev` 716 + porté par `Dev` 0 : aucune pièce créée ni détruite par le commerce (avant l'étape, l'équipe donne 900 cuivres à `Dev` par `/eco give`, comptés dans les 11 650).

### Ce qui passe : après un vrai arrêt du serveur (relance ciblée)

Lignes de la relance de 16:59 ; celle du passage complet (17:16) donne les mêmes 15 contrôles. L'étape du commerce relit d'abord ; c'est donc elle qui voit la première reconnexion d'`Autre` après l'arrêt.

```
[e2e] PASS relance du commerce : /eco stats identique a celui d'avant l'arret (attente comprise) : ...
[e2e] PASS relance : etal d'Autre relu a l'identique (ETAL proprietaire=Autre stock=[bread:11,wheat:8] caisse=114 offres=[0:breadx4@30;] rachats=[0:wheatx8@16,reste4;] registre=4:[...]) ; etal de Dev relu a l'identique (...)
[e2e] PASS relance : la gestion de l'etal de Dev montre 5 pains au stock et l'offre StallOffer[2 x bread pour 20]
[e2e] PASS relance : la caisse de l'etal de Dev affiche 716 cuivres
[e2e] PASS relance : l'onglet Registre de la gestion montre 2 ligne(s), comme avant l'arret (2:[GOODS_IN/Dev/bread/5/0;CASH_IN/Dev//0/716;])
[e2e] PASS relance : Autre se reconnecte, recoit les 120 cuivres en attente (il porte 570, attente 0) et les messages a la connexion : message.oneforallsurvival.commerce.delivered,message.oneforallsurvival.invoice.paid.pending
[e2e] PASS relance : registre des factures identique (FACTURES nombre=2 liste=[...=Autre>Dev:120:PAID;...=Dev>Autre:150:PAID;]) ; /eco stats identique a celui d'avant l'arret, attente passee a 0 copper apres la livraison
[e2e] PASS relance : le compte du coffre-fort est relu a l'identique : 10250 cuivres (1 ruby 2 golds 5 silvers)
[e2e] PASS relance : le second joueur revient apres le redemarrage : somme de depart redonnee 0, il porte toujours 570 cuivres, ouvert=false, retrait NOT_ALLOWED
```

### Incidents de l'essai

Aucun ne vient du code livré ; chacun est corrigé dans le banc avant les passages qui font foi.

1. **Pile de la case 0 écrasée** (2026-11-14, nuit) : `poserEtal` (et `emettre`) faisait `Inventory.add` d'une copie de la main puis `setItem(0, …)` ; l'ajout avait fusionné dans la pile même de la case 0 (7 or d'`Autre`), écrasée ensuite. Corrigé : l'objet est mis dans une case vide sélectionnée, rien n'est ajouté. Le contrôle de la circulation l'aurait aussi attrapé.
2. **Nom de l'écran lu après le clic** : le clic sur « Émettre » ferme le formulaire (la facture était bien partie) ; le journal du banc lisait `mc.screen` après le clic. Le nom est lu avant.
3. **`Autre` « trop loin » du coffre-fort à la relance d'E1** : l'entrée d'un joueur simulé relit la position de son playerdata (celle où l'étape du commerce l'a laissé partir, à ~40 blocs), ce qui écrasait la position donnée ; les refus lus étaient `TOO_FAR` au lieu de `NOT_ALLOWED`. `E2eMonnaieServeur` le ramène devant le coffre-fort après l'entrée.
4. Un passage interrompu (arrêt du PC, 2026-11-14 vers 03:29) a laissé un monde sale : rangé hors du dépôt avant de reprendre.

## Captures

Les captures de la tâche 6 (`run-demo/visuels-captures/`, `echange_*`, `etal_*`, `facture_*`, `recu_echange_infobulle.png`) restent vraies : aucun écran du commerce n'a changé depuis `f23fdeb` (seul `008f9f1`, la liste des joueurs, est venu entre).

Capture de la liste des joueurs (`runClient -PtabShots=frames-tab`, 17:23 à 17:25, `run/options.txt` inchangé, somme MD5 comparée) : dans `run/frames-tab/a-1-joueur.png` (1920 × 1080), les en-têtes « Survivant » et « Métier » **ne se chevauchent plus** (environ 17 pixels d'écran entre les deux mots). Rien n'est corrigé ici dans la liste.

## Non prouvé

- **Crochetage de l'étal** : renvoyé au lot du policier (feature 67) ; rien n'est codé ni essayé.
- **Trésor sans bloc** : la taxe va à un compte du trésor gardé par le serveur ; le bloc du trésor, son écran et son usage sont au lot E3. La taxe est à 0 % par défaut : l'essai de bout en bout lit une taxe nulle ; une taxe non nulle n'est prouvée qu'en GameTest (`laTaxeArriveAuTresor`).
- **Prélèvement des factures** : aucune facture n'est prélevable à distance (feature 86, lot E6).
- **Second joueur simulé, pas un vrai client** : ses gestes passent par les méthodes du serveur, pas par le réseau ni par un écran ; aucun échange n'a eu deux vrais clients, aucune vitrine n'a été ouverte par deux clients à la fois.
- **Relance complète du banc** : toujours en échec sur la couveuse du lot 5 (défaut du banc, ouvert dans `tasks/todo.md`) ; la relance ciblée de l'économie est faite à la place.
- **Stockages de mods non installés face à l'étal** : seuls l'entonnoir vanilla (en vrai) et, en GameTest (tâche 3), les trémies dessus et dessous, l'entonnoir de Create et la capacité Forge exposée ; un mod absent du pack, ou qui vide un bloc sans passer par la capacité Forge, n'est pas couvert.
- **Équilibrage** : prix, taxe, distances (5 blocs) et plafonds (9 offres, 200 lignes, 100 000 000 cuivres par facture) n'ont pas été vécus en partie.
- **Mort pendant un échange, arrêt brutal avec une garde, inventaire plein du receveur, deux acheteurs sur le dernier lot** : GameTests seulement (tâches 1 à 5), pas en vrai.
- **Jar livré** : jamais lancé hors du développement.
