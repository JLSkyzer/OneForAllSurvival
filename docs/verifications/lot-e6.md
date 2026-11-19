# Vérifications du lot E6 : prélèvement de minuit et fournisseur d'électricité

Relevés du 2026-11-19, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 7 (preuve de bout en bout, captures, vérification, wiki). Les tâches 1 à 6 ont leurs JUnit et GameTests, relancés ici. Plan : [`2026-11-17-lot-e6-prelevement-fournisseur.md`](../superpowers/plans/archive/2026-11-17-lot-e6-prelevement-fournisseur.md). Commits du lot : `8c8101b` puis `b021537` (prélèvement générique), `07fad7b` puis `7ff8f22` (poste, registre des fournisseurs, caisse), `b134749` puis `666e408` (raccordement des compteurs et des disjoncteurs), `4b803f6`, `928c27a` (cahier : client coupé) puis `0b397ae` (facture quotidienne), `0a72dcf` puis `e7f238b` (panneau du poste), `8dde364` puis `119b726` (gestes du maire, tableau de bord), puis l'essai de bout en bout et ce document.

**État : le lot est prouvé dans les limites de la section « Non prouvé et limites ».** Sur serveur dédié avec le pack complet, un vrai client (`Dev`, ingénieur, en survie, sans droits d'opérateur) pose son poste d'un vrai clic contre un alternateur de Crafts & Additions entraîné par un moteur créatif de Create, le nomme en tapant dans le panneau et fixe ses tarifs (« au lendemain » affiché) ; un fil suspendu de Crafts & Additions mène à deux maisons. Dev jumelle au testeur (poste, compteur, disjoncteur) ; `Abonne`, connecté, donne son accord par le lien du message ; avant l'acceptation sa machine ne reçoit rien ; Dev clique « Accepter », la machine tourne. `Fauche`, **hors ligne, est reconnu par le cache des profils** au jumelage et reçoit la demande à sa connexion. Dev coupe `Fauche` au panneau (disjoncteur ouvert), Fauche lui-même ne peut ni le réenclencher ni casser son compteur ; un maire simulé fixe le plafond à 3 (`/power cap`), le panneau l'annonce au lendemain ; Dev rétablit Fauche avant l'arrêt. Après un vrai `/stop` et une relance avec l'horloge avancée de deux jours, **à minuit (au démarrage du serveur), la facture d'Abonne est prélevée dans son coffre-fort, joueur absent, zone non chargée** (scénario d'acceptation) : deux factures par compteur (jour de l'essai : consommation × 5 + 10 ; lendemain : 10 au prix ramené à 3), coffre-fort d'Abonne diminué d'autant, caisse augmentée d'autant ; les deux factures de Fauche restent dues (refus au registre, « Public bills due: 2 »), l'avis et le lien de copie lui arrivent à sa connexion ; Dev lit au panneau les impayés de Fauche et l'écart d'hier, puis vide la caisse d'un vrai clic : les pièces sont dans son inventaire, total identique.

## Écart au brief : Fauche coupé puis rétabli

Le brief de la tâche 7 faisait couper `Fauche` au panneau et attendait ensuite, à la relance, « Fauche : deux factures dues ». Depuis la décision de Killian du 2026-11-18 (cahier de l'économie, feature 83), **un client coupé au moment de la relève ne paie pas l'abonnement, et un client coupé toute la journée ne reçoit aucune facture** : ce scénario ne tenait plus. Décision du contrôleur, appliquée telle quelle : Dev coupe `Fauche` au panneau ; Fauche (joueur simulé sans droit, le client lui-même) tente de réenclencher le disjoncteur et de casser le compteur, refusé ; **Dev rétablit Fauche au panneau, d'un vrai clic, avant l'arrêt**. Fauche reçoit ainsi ses deux factures dues à la relance (jour de l'essai : sa consommation × 5 + 10 ; lendemain : 10). La règle « coupé à la relève : pas d'abonnement » est prouvée en GameTest (`unClientResilieDetacheOuCoupeEstFactureSelonSonContrat`), pas par l'essai de bout en bout.

## Écart au brief : l'électricité après le trésor au premier passage

Le brief plaçait l'étape `ELECTRICITE` avant `TRESOR` au premier passage. Or l'étape du trésor (E3) avance l'horloge de l'économie de **deux minuits** pendant le premier passage (`e2etresor horloge frontiere 100`, puis `30`) : placée avant, l'électricité du jour de l'essai aurait été facturée et prélevée **avant l'arrêt**, et le contrôle exact des coffres-forts de l'étape du trésor (« coffres » de `/eco stats`) aurait vu le prélèvement d'Abonne. L'étape passe donc **après** le trésor au premier passage (`MONNAIE` → `COMMERCE` → `BUTIN` → `QUETES` → `TRESOR` → `ELECTRICITE`, puis le lot 5 au passage complet, toujours le dernier avant l'arrêt) ; ses deux clôtures tombent au démarrage de la relance, exactement comme le scénario d'acceptation le veut, et Dev entre dans l'étape sans rien porter de plus. Comme ses joueurs simulés changent les comptes (somme de départ d'Abonne, ligne du plafond au registre), elle **réécrit à sa fin les références de relance** des étapes de la monnaie et du commerce, comme le fait l'étape du trésor. L'ordre de la relance est celui du brief : `COMMERCE` → `MONNAIE` → `TRESOR` → `BUTIN` → `ELECTRICITE` → `QUETES`.

Les prélèvements de minuit ont lieu au démarrage de la relance, avant tout contrôle : ils changent six lignes de `/eco stats` (coffres-forts, factures publiques dues, refus, caisses des postes, électricité, prélèvements) et le nombre de lignes du registre, que relisent les étapes de la monnaie et du commerce. Ces relances comparent désormais le texte d'avant l'arrêt **corrigé de ces seules lignes** (`E2eElectricite.statsCorrigees`, `journalCorrige`), et seulement si les chiffres du serveur le prouvent au cuivre près (le coffre-fort d'Abonne a perdu exactement ce que la masse des coffres-forts a perdu et ce que les caisses, les prélèvements et l'électricité ont gagné ; les lignes ajoutées au journal depuis l'arrêt ne sont que des prélèvements payés ou refusés) ; sinon la comparaison échoue. L'étape de l'électricité contrôle ensuite ces lignes exactement. `e2ecommerce factures`, qui liste les factures émises par Dev et Autre, ne compte plus les factures publiques (Dev est le créancier des factures d'électricité, contrôlées par leur étape).

## Décisions du lot

| Sujet | Décision |
|---|---|
| Prélèvement (86) | Chaque minuit (`payHour` 0, fuseau `payTimeZone`), chaque facture publique due est présentée, la plus ancienne d'abord : inventaire (bourses comprises, joueur connecté et vivant), puis attente (`HeldGoods`), puis coffre-fort (`SafeAccounts`, même absent, zone non chargée). Une source qui suffit seule paie et reçoit la monnaie ; sinon, si le total suffit, tout le porté et toute l'attente, le reste au coffre-fort, monnaie au coffre-fort (au plafond : en attente) ; total insuffisant : refus, rien ne bouge. Jamais au sol. |
| Attente comptée comme argent porté | Décision inscrite au plan (Global Constraints, « Décisions ») : l'ordre inventaire → attente → coffre-fort, l'attente comptant comme de l'argent porté (elle ferme la cachette relevée en E2). |
| Refus | La facture reste due, représentée à chaque minuit ; débiteur prévenu (tout de suite ou à sa connexion) avec « [Recevoir la facture] » (`/invoice copy <id>`, un papier à la fois, débiteur seulement) ; fondateur prévenu s'il est connecté, résumé des impayés à sa connexion, colonne « impayés » au panneau. |
| Factures publiques | Seule l'électricité à ce lot. Amendes (lot du policier), impôt (lot du maire), abonnement du téléphone (lot du téléphone) : points d'extension `DebitPayees.register` et `DailyDebit.registerIssuer`, documentés, **non codés**. Un joueur ne peut pas émettre de facture prélevable. Notification par le téléphone : absente (le téléphone n'existe pas). |
| Payer tout de suite | Par l'objet facture (bouton « Payer » de l'écran d'E2), qui prend dans l'inventaire. |
| Poste (83) | Fabriqué par l'**ingénieur seul**, à l'établi (décision de Killian du 2026-11-17, exception à « fabricable par tous ») ; entrée à l'arrière, sortie devant, comme le compteur du lot 4 (décision de Killian du 2026-11-18) ; un fournisseur par joueur, un poste par fournisseur, nom de 1 à 24 caractères, unique ; la caisse vit chez le serveur (`PowerSuppliers`), jamais dans le bloc ; retrait de la caisse : fondateur et équipe seulement (ingénieur et maire en lecture). |
| Compteur du lot 4 | **Changement de règle** : un compteur non jumelé à un fournisseur, ou jumelé mais pas accepté, ne laisse plus rien passer. Les GameTests du lot 4 et l'étape du réseau de l'E2E le raccordent d'abord (`FournisseurDEssai`). Dureté `-1` sur tout compteur et disjoncteur, jumelé ou non (pas de dureté par état sans mixin) : un compteur non jumelé n'est plus pris par la perceuse ni par un engin de Create ; un joueur le casse comme avant. |
| Raccordement | Testeur sur le poste (60 s), puis sur le compteur au nom d'un joueur connu du serveur (connecté ou dans le cache des profils), puis sur le disjoncteur ; le joueur donne son accord (lien « [Accepter] », `/power consent`) ; le fournisseur accepte au panneau ; le courant passe et se compte à l'acceptation. Personne n'est facturé sans son accord. |
| Client coupé | Décision de Killian (2026-11-18, cahier, feature 83) : coupé au moment de la relève, pas d'abonnement ce jour-là ; consommation d'avant la coupure facturée ; coupé toute la journée, aucune facture. |
| Prix | En cuivres par 100 000 FE (`powerPriceUnitFe`), arrondi au cuivre inférieur ; plafond de départ 20 (`powerDefaultPriceCap`) ; abonnement 0 à 50 par jour (`powerMaxSubscription`) ; taxe 0 % (au plus 20, `powerTaxMaxPercent`), retenue sur ce que reçoit la caisse, versée au trésor. Tout changement (prix, abonnement, plafond, taxe) vaut au lendemain ; un prix au-dessus d'un nouveau plafond est ramené à la clôture. Valeurs lues dans `EconomyConfig` : identiques à celles du plan. |
| Tarif au panneau | Fondateur et équipe ; jamais un client du fournisseur, même ingénieur ou maire (le maire modifie un fournisseur par `/power set`, tracé). |
| Écart | Panneau : sortie du poste moins somme des compteurs, aujourd'hui et hier ; rouge au-delà de 10 % (`powerGapWarnPercent`) et de 100 000 FE. |
| Maire | `/power cap`, `/power tax`, `/power set`, `/power owner`, `/power remove` (confirmation dans les 60 s : factures dues annulées, clients détachés, caisse versée au fondateur) ; chaque geste au registre, avec son auteur ; pas d'écran (le bureau du maire n'existe pas). |
| Pause et resetday | La clôture de minuit partage la pause de la paie (E3). `resetday` ramène le dernier jour **présenté**, jamais le plus grand jour **émis** : aucun jour n'est facturé deux fois. |
| Sauvegarde | Aucune écriture forcée : fournisseurs, caisses, consommation du jour, factures, jours clos en `SavedData` marqués sales. |
| Film | Aucun à ce lot ; film de toute l'économie après la clôture, sur feu vert de Killian. |

## Ce que la tâche 3 a lu au bytecode (câbles)

Jars remappés du cache de Gradle, `javap -c -p` : Create Crafts & Additions `1.3.3`, Create: New Age `1.2.0` (avec ESL `1.1.3`), Create Advanced Electricity (fichier CurseForge `9362725`). Nos blocs en ligne exposent l'énergie Forge sur l'entrée seulement (réception relayée au voisin de sortie, extraction 0) ; la sortie n'expose rien.
- **Crafts & Additions** (connecteur et fil) : en mode « push », il **pousse** par `receiveEnergy` (simulation d'abord), relu à chaque tic ; en « pull », il extrait (0 chez nous). Un connecteur posé sur la sortie d'un poste reçoit en passif (`connector_allow_passive_io`, vrai). Aucune adaptation.
- **New Age** : le réseau **pousse** chez ses consommateurs, mais ne les recense qu'au changement de voisin : un compteur qui devient passant (accepté, rétabli) prévient ses voisins (`level.updateNeighborsAt`). Piège de la simulation : voir « Non prouvé ».
- **Advanced Electricity** : la boîte d'alimentation pousse (relue à chaque tic) ; le câble mural et sous-marin **extrait** d'une face qui expose l'énergie : il entre dans un compteur, il ne sort pas d'un poste ni d'un compteur (laissé tel quel par Killian).
- **Conclusion** : aucune intégration à part, aucun mixin. GameTest `laMesureNeDependPasDuCable` (avec le pack) : consommation = index = reçu par la charge pour la liaison directe, C&A, New Age et C&A + boîte d'Advanced Electricity.

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test runGameTestServer` (2026-11-19, 01:44 à 01:45) | « All 982 required tests passed » (01:45:16, `run-gametest/logs/latest.log`) ; JUnit : 556 tests, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml` ; la tâche `test` est à jour, aucun changement du code principal depuis `119b726`) |
| `./gradlew --offline runGameTestServer -PwithPack` (01:46 à 01:47) | « All 982 required tests passed » (01:47:35) ; le pack est chargé (`laMesureNeDependPasDuCable` : direct 20 500 FE, Crafts & Additions 19 800 FE, consommation = index = reçu par la charge) |

Cette tâche n'ajoute aucun GameTest ni JUnit : ses classes (`E2eElectricite`, `E2eElectriciteServeur`) et les retouches de `JoueurDeTest` (messages gardés tels quels, liens cliquables), `FournisseurDEssai` (`fournisseurdessai detacher`), `E2eReseau`, `E2eMonnaie`, `E2eCommerce` et `E2eCommerceServeur` sont dans `devcontent`, chargées par le serveur de GameTests sans y jouer. Les passes ci-dessus sont faites sur le code final, après les essais de bout en bout.

## Serveur dédié et vrai client

`runServer -PwithPack` puis `E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack -Pe2eHorloge=<minutes>` et `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`, puis le passage complet (`runE2eClient -PwithPack`, l'étape du réseau du lot 4 raccordée par `fournisseurdessai raccorder`) et sa relance ciblée, sous le verrou de lancement (pris par un script qui relit verrou et file et refuse s'ils ne sont pas libres, vidé sans BOM seulement s'il contient sa ligne, dans un `trap EXIT`). Monde neuf à chaque premier passage (les anciens rangés hors du dépôt, dans le dossier temporaire de la session). Client `Dev` avec le pack complet, fenêtre sur le second écran. L'étape est `E2eElectricite.java` (client) et `E2eElectriciteServeur.java` (commandes de console réservées au RCON, niveau 4 : joueurs simulés `Abonne`, `Fauche` et un maire, coffre-fort posé par le crochet de pose du bloc et « Déposer tout », lien d'accord joué au nom du joueur, tentatives du sans-droit, tarifs du lendemain appliqués tout de suite, fil suspendu, vidange des machines d'essai, lectures de `PowerSuppliers`, `InvoiceRegistry` et du registre), dans `src/devcontent/java/dev/oneforallsurvival/dev/`.

**Aire.** Boîte de pierre close et couverte (`fill … stone hollow`, intérieur 15 x 4 x 9 à y = 150), éclairée (pierre lumineuse au plafond), à 640 blocs du départ en x et en z ; paisible et naissances coupées le temps de l'étape, puis rendues. Réseau (console) : moteur créatif de Create (256 tr/min), alternateur de Crafts & Additions, puis le poste posé par Dev ; connecteur de Crafts & Additions sur la sortie du poste (réception passive, « pull »), un sur l'entrée de chaque compteur (« push »), deux fils tirés avec la vraie bobine de cuivre (`ReseauxDev.cliquer`, `tirerFil`, joueur factice). Maisons (console) : compteur au nom du client (`{subscriber:"Abonne"}`), disjoncteur, machine d'essai (`test_machine`), coffre-fort. Les deux machines sont vidées à 20 FE à chaque tic (comme `laMesureNeDependPasDuCable`), pour qu'elles tirent ~100 FE par tic au lieu de 10.

**Joueurs simulés** (`JoueurDeTest`, mêmes événements de connexion qu'un vrai joueur) : `Abonne` reçoit sa somme de départ (500) et la range dans son coffre-fort ; `Fauche` n'a pas de somme de départ (notée donnée avant sa première entrée : aucune pièce créée), son coffre-fort est vide, il ne porte rien ; un maire est élu, joue `/power cap 3` de sa propre source et est rendu dans le même tic (aucun temps de rôle compté pour la paie). Leurs messages sont lus tels quels, liens cliquables compris (`JoueurDeTest.composantsALaConnexion`, `composantsRecus`, `commandes`).

**Gestes de Dev** (ingénieur, survie, non opérateur) : pose du poste d'un vrai clic droit, regard à l'est ; panneau ouvert d'un clic droit, main vide ; nom tapé caractère par caractère dans le champ puis « Found » ; prix et abonnement tapés dans leurs champs, puis « OK » ; testeur sur le poste, puis sur le compteur, puis (de nouveau sur le poste) sur le disjoncteur ; « Accept », « Cut off », « Restore » au centre des boutons de la ligne du client ; « Till » ; retraits de la caisse par les boutons de l'écran (clic droit), un pas par retrait (chaque clic attend la réponse du serveur).

### Passages

| Passage | Connexion | Bilan | Contrôles |
|---|---|---|---|
| E1 + E2 + E4 + E5 + E3 + E6 seules (`E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack`), monde neuf, premier jet de l'étape | 00:10:56 | « TOUT PASSE » à 00:21:04 | 228 `PASS` (dont 47 de l'électricité), 0 `FAIL` ; « -Pe2eHorloge=5741 » |
| Relance ciblée de ce monde (`runServer -PwithPack -Pe2eHorloge=5741`, puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) : E2, E1, E3, E4, **E6**, puis E5 | 00:24:06 | « TOUT PASSE » à 00:25:56 | 65 `PASS` (dont 14 de l'électricité), 0 `FAIL` |
| Passage complet (`runE2eClient -PwithPack`), monde neuf | 00:28:00 | 1 ÉCHEC à 00:30:58 | 85 `PASS`, puis l'étape du réseau du lot 4 (avant l'électricité) : voir les incidents, corrigé |
| Passage complet (lot 4 corrigé), monde neuf | 00:34:36 | 1 ÉCHEC à 00:51:50 | 398 `PASS` (dont les **47 de l'électricité, toutes passées**), puis l'étape du lot 5, après l'électricité : voir les incidents, corrigé |
| Passage complet (rangement de l'inventaire corrigé), monde neuf | 00:55:37 | 1 ÉCHEC à 01:03:48 | 255 `PASS`, puis l'étape du butin (E4), avant l'électricité : monde sans animal né à la génération près du départ (voir les incidents) |
| **Passage complet**, monde neuf | 01:06:09 | « TOUT PASSE » à 01:24:18 | **423 `PASS`** (dont 47 de l'électricité), 0 `FAIL` ; « -Pe2eHorloge=5681 » |
| **Relance ciblée** de ce monde | 01:26:10 | « TOUT PASSE » à 01:28:01 | 65 `PASS` (dont 14 de l'électricité), 0 `FAIL` |
| **E1 + E2 + E4 + E5 + E3 + E6 seules**, monde neuf, code final (lot 4 et rangement corrigés) | 01:30:04 | « TOUT PASSE » à 01:40:12 | 228 `PASS` (dont 47 de l'électricité), 0 `FAIL` ; « -Pe2eHorloge=5662 » |
| **Relance ciblée** de ce monde | 01:41:51 | « TOUT PASSE » à 01:43:41 | 65 `PASS` (dont 14 de l'électricité), 0 `FAIL` |

Font foi : le passage complet de 01:06 et sa relance de 01:26 (toutes les étapes de tous les lots, code final), et le passage des étapes de l'économie seules de 01:30 avec sa relance de 01:41 (même code) ; les journaux cités plus bas sont ceux de ce dernier monde. Le premier jet de 00:10 / 00:24 (même déroulé de l'électricité, rangement de l'inventaire par `/clear`) passait déjà. Tous les mondes et journaux sont rangés hors du dépôt, dans le dossier temporaire de la session.

### Ce qui passe : premier passage (électricité)

```
[e2e] PASS depart 6, 65, 34 ; inventaire {minecraft:bread=4, minecraft:apple=1} ; porte 0 ; difficulte peaceful, naissances true, role de Dev citoyen
[e2e] PASS aire de l'electricite en -634, 150, -606 (joueur parti de 6, 65, 34) ; Dev porte 0 cuivres
[e2e] PASS aire close en -635 149 -607 -619 154 -597 ; alternateur en -632, 150, -605 ; maisons : compteur, disjoncteur, machine en -625, 150, -605 (Abonne) et -625, 150, -599 (Fauche)
[e2e] PASS Abonne recoit 500 cuivres (somme de depart) et les range (« Deposer tout ») : coffre-fort 500, il ne porte plus rien ; Fauche (somme de depart notee donnee, rien cree) : coffre-fort pose et vide, rien porte ; Fauche est reparti (il est connu du serveur, absent)
[e2e] PASS Dev en survie, operateur = false, role ingenieur ; recu oneforallsurvival:power_station 1, oneforallsurvival:tester 1
[e2e] PASS poste pose en -631, 150, -605 par Dev (sortie east, entree contre l'alternateur) ; fournisseur au serveur : AUCUN
[e2e] PASS panneau ouvert : « New station », 0 client(s)
[e2e] PASS poste neuf : « New station », « Supplier name (1 to 24 characters): » ; capture .\captures-e6\e6_panneau_poste_neuf.png
[e2e] PASS nom tape dans le champ (15 caracteres) et « Fonder » clique : le serveur fonde [Lumière du Port] au nom de Dev ; le panneau passe aux tarifs
[e2e] PASS prix tape et « OK » clique : le panneau affiche « Price: 0 copper, tomorrow 5 coppers » (au lendemain) ; serveur : prix demain 5
[e2e] PASS abonnement tape et « OK » clique : « Subscription: 0 copper, tomorrow 1 silver » (au lendemain) ; capture .\captures-e6\e6_panneau_tarifs_au_lendemain.png
[e2e] PASS tarifs en vigueur : TARIFS avant=0/0 prix=5 abonnement=10 plafonne=false (la facture du jour portera le prix)
[e2e] PASS reseau : RESEAU etats=connector:None,None,None journal=[createaddition:connector_sur_-631,_150,_-605_face_east_->_CONSUME_|_createaddition:connector_sur_-625,_150,_-605_face_west_->_CONSUME_|_createaddition:connector_sur_-625,_150,_-599_face_west_->_CONSUME]
[e2e] PASS testeur sur le poste : « Tester set to supplier Lumière du Port for 60 s: touch a meter or a breaker to pair it. »
[e2e] PASS testeur sur le compteur d'Abonne : message.oneforallsurvival.power.paired au chat
[e2e] PASS compteur d'Abonne jumele : Cl[abonne=Abonne, joueur=Abonne, etat=AWAITING_CONSENT, feJour=0, feHier=0, coupe=false, disjoncteurs=0]
[e2e] PASS testeur sur le poste : « Tester set to supplier Lumière du Port for 60 s: touch a meter or a breaker to pair it. »
[e2e] PASS testeur sur le disjoncteur d'Abonne : message.oneforallsurvival.power.breaker_paired au chat
[e2e] PASS accord d'Abonne : ACCORD nom=Abonne demande=message.oneforallsurvival.power.request lien=/power_consent_7qbkqdh3 resultat=1 messages=[command.oneforallsurvival.power.consented] ; client Cl[abonne=Abonne, joueur=Abonne, etat=AWAITING_ACCEPT, feJour=0, feHier=0, coupe=false, disjoncteurs=1]
[e2e] PASS accord donne, pas encore accepte : machine d'Abonne {energie=0, progres=0}, consommation du jour 0 FE
[e2e] PASS panneau ouvert : « Lumière du Port », 1 client(s)
[e2e] PASS demande au panneau : « To accept » ; capture .\captures-e6\e6_panneau_demande_a_accepter.png ; « Accepter » clique : Abonne ACTIVE auj. 0 hier 0 impayes 0 (disjoncteur)
[e2e] PASS accepte : machine d'Abonne {energie=20, progres=61} ; client Cl[abonne=Abonne, joueur=Abonne, etat=ACTIVE, feJour=6080, feHier=0, coupe=false, disjoncteurs=1]
[e2e] PASS testeur sur le poste : « Tester set to supplier Lumière du Port for 60 s: touch a meter or a breaker to pair it. »
[e2e] PASS testeur sur le compteur de Fauche (Fauche absent) : message.oneforallsurvival.power.paired au chat
[e2e] PASS Fauche hors ligne (joueurs connectes : 3) reconnu par le cache des profils : client Cl[abonne=Fauche, joueur=Fauche, etat=AWAITING_CONSENT, feJour=0, feHier=0, coupe=false, disjoncteurs=0]
[e2e] PASS testeur sur le poste : « Tester set to supplier Lumière du Port for 60 s: touch a meter or a breaker to pair it. »
[e2e] PASS testeur sur le disjoncteur de Fauche : message.oneforallsurvival.power.breaker_paired au chat
[e2e] PASS Fauche a la connexion : [message.oneforallsurvival.power.request] (liens [/power_consent_tadapkh5,/power_refuse_tadapkh5]) ; accord : /power_consent_tadapkh5 ; client Cl[abonne=Fauche, joueur=Fauche, etat=AWAITING_ACCEPT, feJour=0, feHier=0, coupe=false, disjoncteurs=1]
[e2e] PASS panneau ouvert : « Lumière du Port », 2 client(s)
[e2e] PASS « Accepter » clique : Fauche ACTIVE auj. 0 hier 0 impayes 0 (disjoncteur)
[e2e] PASS Fauche raccorde : machine {energie=20, progres=62} ; client Cl[abonne=Fauche, joueur=Fauche, etat=ACTIVE, feJour=6300, feHier=0, coupe=false, disjoncteurs=1]
[e2e] PASS « Couper » clique : Fauche ACTIVE auj. 6400 hier 0 impayes 0 coupe (disjoncteur) ; disjoncteur ouvert (vu par le client) ; serveur Cl[abonne=Fauche, joueur=Fauche, etat=ACTIVE, feJour=6400, feHier=0, coupe=true, disjoncteurs=1] ; capture .\captures-e6\e6_panneau_fauche_coupe.png
[e2e] PASS Fauche coupe : consommation figee a 6400 FE
[e2e] PASS refuse : INTRUS nom=Fauche fermeAvant=false clic=CONSUME fermeApres=false messagesClic=[message.oneforallsurvival.power.touch_client] casse=false compteurLa=true messagesCasse=[message.oneforallsurvival.power.touch_client] ; toujours coupe
[e2e] PASS maire simule : MAIRE elu=true operateur=false resultat=1 cles=[command.oneforallsurvival.power.cap_set,economy.oneforallsurvival.unit.copper.plural] texte=[Electricity price cap: 3 coppers per 100000 FE, from tomorrow (midnight).] avant=aucun plafondDemain=3 ; Dev lit « The electricity price cap becomes 3 coppers per 100000 FE tomorrow: the price of Lumière du Port will be lowered to it at midnight. »
[e2e] PASS panneau ouvert : « Lumière du Port », 2 client(s)
[e2e] PASS panneau : « Cap: 2 silvers per 100,000 FE · tax 0 % (tomorrow: 3 coppers, tax 0 %) », prix du jour « Price: 5 coppers » ; capture .\captures-e6\e6_panneau_plafond_au_lendemain.png
[e2e] PASS « Retablir » clique : Fauche ACTIVE auj. 6400 hier 0 impayes 0 (disjoncteur) ; disjoncteur referme
[e2e] PASS Fauche retabli : 8600 -> 12600 FE
[e2e] PASS courant arrete : sortie/Abonne/Fauche = 74560/58760/15800
[e2e] PASS panneau ouvert : « Lumière du Port », 2 client(s)
[e2e] PASS panneau = console : Abonne 58760 FE, Fauche 15800 FE ; ecart du jour : sortie 74560 FE, compteurs 74560 FE (« 0 FE (0 %) ») ; capture .\captures-e6\e6_panneau_avant_arret.png
[e2e] PASS capture .\captures-e6\e6_poste_en_jeu.png
[e2e] PASS deconnectes : PARTI nom=Abonne porte=0
[e2e] PASS objets de l'etape retires case par case (oneforallsurvival:testerx1 (case 3)) ; chaque case du sac et de la barre comme au depart ; Dev porte 0 cuivres comme avant ; il rentre, operateur
[e2e] PASS e2e-electricite.txt ecrit : jour 2026-11-21, Abonne 58760 FE, Fauche 15800 FE, caisse 0, factures 0 ; registre REGISTRE refus=0 payees=0 paye=0 public=0 taxe=0 lignes=33 coffres=11228 caisses=0 fournisseurs=1 dues=0 duesTotal=0 circulation=13154 reel=1795048812269  [...] ; references de la monnaie et du commerce reecrites|Created:|  Mob loot: 3 coppers|  Quests: 1 copper|  Starting money: 2 emeralds|  Created by sta […]
```

### Ce qui passe : après un vrai arrêt du serveur (relance ciblée)

```
[e2e] PASS depart 6, 65, 34 ; inventaire {minecraft:bread=4, minecraft:apple=1} ; porte 3 ; difficulte easy, naissances true, role de Dev citoyen
[e2e] PASS relance : jour 2026-11-23, jours presentes jusqu'au 2026-11-22 (essai le 2026-11-21) ; compteur d'Abonne dans un chunk non charge : {charge=false, joueurs=1}
[e2e] PASS factures : [Abonne:12:PAID:2026-11-21:58760:5:10, Fauche:10:DUE:2026-11-21:15800:5:10, Abonne:10:PAID:2026-11-22:0:3:10, Fauche:10:DUE:2026-11-22:0:3:10] (jour de l'essai : 58760 FE x 5 / 100 000 + 10 ; lendemain : 0 FE au prix ramene a 3, abonnement 10) ; les 22 cuivres d'Abonne etaient deja preleves au premier controle du client, juste apres sa connexion (client lance a 2026-11-19T00:41:22.017Z)
[e2e] PASS coffre-fort d'Abonne 500 -> 478 (-22) ; caisse 0 -> 22 (+22, taxe 0 %) ; registre : {refus=3, payees=2, paye=22, public=22, taxe=0, lignes=43, coffres=11236, caisses=22, fournisseurs=1, dues=2, duesTotal=20, circulation=13657, reel=1795048981420} ; journal depuis l'arret : [transfer.salary:4;created.staff:1;debit.refused:3;transfer.public_bill:2;] ; /eco stats : « Public bills due: 2 (2 silvers) », « Direct debits refused: 3 »
[e2e] PASS aire de l'electricite en -634, 150, -606 (joueur parti de 6, 65, 34) ; Dev porte 3 cuivres
[e2e] PASS Abonne a la connexion : [message.oneforallsurvival.debit.paid.safe,message.oneforallsurvival.debit.paid.safe] (deux fois « Bill … (no. …) debited: …, taken from your safe ») ; il ne porte rien, aucune somme de depart redonnee
[e2e] PASS Fauche a la connexion : [message.oneforallsurvival.debit.refused,message.oneforallsurvival.debit.refused] ; liens « [Get the bill] » : [/invoice_copy_d9b45f5e-2fb8-4ef4-b91b-761a1867a413,/invoice_copy_6c3b0729-6a03-47a3-81a1-b2b2cd2fa235]
[e2e] PASS Dev en survie, operateur = false, role ingenieur ; recu 
[e2e] PASS panneau ouvert : « Lumière du Port », 2 client(s)
[e2e] PASS panneau : Fauche « Unpaid: 2 silvers » (20 = factures dues) ; ecart d'hier : sortie 0, compteurs 0 (« 0 FE (— %) ») ; « Price: 3 coppers » ; caisse « Till: 2 silvers 2 coppers » ; Dev avait lu a sa connexion « founder_unpaid_summary » ; capture .\captures-e6\e6_panneau_relance_impayes.png
[e2e] PASS caisse ouverte : 22 cuivres (2 silvers 2 coppers) ; capture .\captures-e6\e6_caisse_relance.png
[e2e] PASS caisse videe en 4 retrait(s)
[e2e] PASS Dev porte 3 -> 25 cuivres (+22 = la caisse) ; caisse du serveur 0
[e2e] PASS relance de l'electricite finie : Electricity price cap: 2 silvers per 100000 FE, from tomorrow (midnight).| ;  ; Dev porte 25 cuivres
```

Le client de l'essai est en anglais (langue du dossier `run-e2e`) : « Found », « Accept », « Cut off », « Public bills due: 2 (2 silvers) ». Les montants sont petits (une machine d'essai d'environ 100 FE par tic pendant une minute) : 58 760 FE × 5 / 100 000 = 2 cuivres (arrondi au cuivre inférieur), plus 10 d'abonnement ; Fauche, 15 800 FE : 0 + 10. Mêmes factures dans le monde du passage complet (58 460 FE et 16 000 FE).

**Consommation et écart.** Avant l'arrêt, le moteur retiré et les valeurs stables pendant 3 s, le panneau et la console disent la même chose au FE près : Abonne 58 760 FE, Fauche 15 800 FE, sortie du poste 74 560 FE = somme des compteurs (passage complet : 58 460, 16 000 et 74 460). Pendant l'essai, l'écart du jour n'était pas nul (80 FE de sortie pour 0 compteur avant l'acceptation, 1 480 FE, 3 à 4 % selon le passage, à la coupure de Fauche) : c'est la réserve du réseau de Crafts & Additions qui se remplit, vue au panneau comme le serait une perte (« ligne vulnérable »). Elle se vide dans les compteurs une fois le moteur arrêté.

**Joueur absent reconnu par le cache des profils** (point ouvert de la tâche 3) : prouvé. `Fauche`, venu une fois puis reparti, est jumelé hors ligne (« joueurs connectés : 3 » : Dev, Abonne, Agent) ; le serveur l'identifie par la table du cache des profils (`PowerPairing.knownPlayer`), le client attend son accord, et la demande d'accord lui est envoyée à sa connexion (`[message.oneforallsurvival.power.request]`, liens `/power consent …` et `/power refuse …`).

**Au démarrage.** Le journal du serveur de la relance dit « Prelevement du 2026-11-21 : 1 facture(s) payee(s), 1 refusee(s) » puis « Prelevement du 2026-11-22 : 1 facture(s) payee(s), 2 refusee(s) » à 01:41:08, avant la connexion du client (01:41:51 ; passage complet : 01:25:27, client à 01:26:10) ; le premier contrôle du client (la somme de départ de la monnaie, dès la connexion) lit déjà les 22 cuivres prélevés chez Abonne ; le compteur d'Abonne est dans un chunk non chargé quand l'étape commence.

« [souris] rendue » et « [fenetre] second écran (2600, 40) » sont lus dans le journal de chaque client ; aucun client ni serveur n'est resté ouvert (le serveur s'arrête par `/stop`) ; le verrou est vidé après chaque lancement. PID notés à chaque lancement (enveloppe Gradle du serveur, JVM du serveur, enveloppe Gradle du client, JVM du client) : 01:04, 23392, 25392, 52792, 52768 ; 01:24, 2592, 34104, 30876, 57732 ; 01:28, 40748, 57480, 51536, 40156 ; 01:40, 8288, 63884, 50260, 31236 ; aucune JVM du projet restante après chaque lancement (relevé à la fin du script). Aucune JVM d'AXIONENGINE touchée ; la file d'attente était vide à chaque prise du verrou.

### Incidents de l'essai

- **Passage complet de 00:28, étape du réseau du lot 4** : « Clic droit main vide sur le compteur : l'ingénieur ouvre l'écran d'inscription » échoue (`message.oneforallsurvival.power.reinscribe_paired`). La tâche 3 avait raccordé le compteur de cette étape juste après sa pose (sinon le tableau de délestage ne le voit pas passer) ; son tour de corrections a ensuite interdit de réinscrire un compteur jumelé, et l'étape, compilée, n'avait pas été lancée. **Défaut de l'essai** : l'étape détache maintenant le compteur le temps de l'inscription (`fournisseurdessai detacher`, nouvelle commande de la console d'essai), le raccorde pour le relevé (« le compteur compte »), puis le détache pour les gestes du Citoyen (règles du lot 4 : il lit le nom de l'abonné). Leçon écrite (`tasks/lessons.md`).
- **Passage complet de 00:34, étape du lot 5** (après l'électricité) : « Ouverture de la marmite de cantine » échoue, le joueur tient un testeur. Au premier jet, l'étape de l'électricité donnait à Dev un second testeur, puis le retirait par `/clear`, qui a pris **le premier** testeur de l'inventaire, celui de l'étape de la réparation ; la barre d'action n'était plus celle que les étapes suivantes connaissent. **Défaut de l'essai** : l'étape se sert du testeur de Dev s'il en a un, retire case par case ce qu'elle a mis dans une case vide au départ (`/item replace … container.<n> with air`), rend la case tenue, et contrôle que chaque case du sac et de la barre est comme au départ.
- **Passage complet de 00:55, étape du butin (E4)** : « Origines du monde neuf : un animal apparu à la génération des chunks est marqué sauvage » échoue sur `animaux=0` : aucun animal né à la génération autour du départ dans ce monde (graine tirée au hasard). Sans lien avec ce lot (étape d'E4, avant l'électricité) ; monde suivant : 57 animaux, tous marqués. À surveiller : l'essai d'E4 dépend de la graine du monde neuf.

## Captures

Prises pendant l'essai, sur le serveur dédié avec le pack, par l'étape elle-même (`Screenshot.takeScreenshot`, fenêtre de 1280 x 656), **toutes ouvertes et regardées**, rangées hors du dépôt dans `run-e2e/captures-e6/` (chaque passage les réécrit : celles du dossier sont celles du monde de 01:30 et de sa relance ; celles du passage complet de 01:06, regardées elles aussi, sont gardées dans le dossier temporaire de la session) (en plus des 21 captures de la tâche 5, prises par `runDemoVisuels` avec des états imposés) :

- `e6_panneau_poste_neuf.png` : poste neuf, « New station », champ du nom vide, « Found » grisé ;
- `e6_panneau_tarifs_au_lendemain.png` : « Price: 0 copper, tomorrow 5 coppers », « Subscription: 0 copper, tomorrow 1 sil… » (coupé par « … » à cette taille ; la bulle donne le texte entier), aucun client ;
- `e6_panneau_demande_a_accepter.png` : Abonne « To accept », boutons « Accept » et « Refuse » ; sortie 80 FE pour 0 au compteur (la réserve du fil) ;
- `e6_panneau_fauche_coupe.png` : Fauche « Cut off » en rouge, bouton « Restore » ; écart 1 480 FE (3 %) en vert ;
- `e6_panneau_plafond_au_lendemain.png` : « Cap: 2 silvers per 100,000 FE · tax 0 % (tomorrow: 3 copp… » (coupé par « … », bulle entière) ; prix du jour 5 ;
- `e6_panneau_avant_arret.png` : deux clients actifs, Abonne 58 kFE, Fauche 15 kFE, sortie 74 kFE = compteurs 74 kFE, écart 0 ;
- `e6_poste_en_jeu.png` : le poste contre l'alternateur, le fil suspendu de Crafts & Additions qui part de son connecteur vers les maisons ;
- `e6_panneau_relance_impayes.png` (relance) : « Price: 3 coppers », « Cap: 3 coppers per 100,000 FE », « Till: 2 silvers 2 coppers », Fauche « Unpaid: 2 silvers » en rouge, Abonne sans impayé ;
- `e6_caisse_relance.png` (relance) : la caisse « Lumière du Port till », « Total: 2 silvers 2 coppers », dépôt refusé (« the till only receives collected bills »).

Rien ne déborde du cadre ; deux lignes (abonnement du lendemain, plafond du lendemain) sont coupées par « … » à cette taille de fenêtre, leur texte entier restant dans la bulle (limite déjà écrite à la tâche 5 pour les valeurs extrêmes : ici à des valeurs courantes).

## Non prouvé et limites

Par l'essai de bout en bout, seulement en GameTests et JUnit (tâches 1 à 6) :
- les sources **inventaire**, **attente** et **mêlées** du prélèvement (l'essai prouve le coffre-fort, joueur absent, et le refus) ; la monnaie rendue en attente ; le paiement « tout de suite » par l'objet facture (l'essai voit le lien « [Get the bill] » et sa commande `/invoice copy <id>`, il ne la joue pas) ;
- la règle « coupé à la relève : pas d'abonnement » (voir l'écart au brief) ; la taxe sur l'électricité (laissée à 0 % dans l'essai, pour que le trésor relu par l'étape du trésor ne bouge pas) ; la caisse ou le trésor plein ;
- refuser ou détacher au panneau, la résiliation (`/power resign`), `/power tax`, `/power set`, `/power owner`, la suppression d'un fournisseur, `/eco power`, `resetday` ;
- la casse d'un compteur jumelé par un ayant droit (détachement), les automates de Create (déployeur, perceuse, engin) : GameTests avec le pack ;
- les fils de New Age et d'Advanced Electricity (l'essai utilise le fil suspendu de Crafts & Additions, cas de référence du cahier) : `laMesureNeDependPasDuCable`.

Aides propres à l'essai, pas des gestes de jeu : `e2eelec tarifs` (les tarifs du lendemain appliqués tout de suite, par la clôture du jour du fournisseur, pour que la facture du jour porte le prix), la vidange des machines d'essai à 20 FE par tic, la somme de départ notée donnée pour Fauche et le maire simulé, le maire élu le temps d'un tic, les blocs des maisons et le fil posés par la console (joueur factice pour le connecteur et la bobine).


Points ouverts relevés aux tâches 1 à 6 (registre du contrôleur, « Parqué » et « Inquiétudes »), écrits ici sans correction :

- **Câble mural (et sous-marin) d'Advanced Electricity en sortie** : il amène le courant dans une entrée de poste ou de compteur mais ne le reprend pas à une sortie, car il n'aspire que d'une face qui expose l'énergie et nos sorties n'exposent rien (lecture au bytecode, tâche 3). En sortie : fil de Crafts & Additions et boîte d'alimentation, ou New Age. **Laissé tel quel par Killian** (2026-11-18). La solution proposée (une face de sortie qui n'accepte que l'extraction, avec garde de réentrance) n'est pas codée.
- **Simulation de Create: New Age** : une simulation poussée dans un réseau New Age fait réellement passer du courant (transaction annulée côté ESL, extraction refusée chez nous). La mesure « compteur = reçu par la charge » reste juste, mais la sortie du poste peut être sous-comptée : faux écart au panneau. Défaut d'ESL / New Age, non corrigé (cesser de relayer les simulations casserait Crafts & Additions).
- **Avis aux seuls joueurs connectés** : refus de prélèvement (au fondateur), prix ramené au plafond, changement de tarif, caisse pleine ne partent qu'aux joueurs connectés. Rattrapage : le débiteur a ses refus à la connexion (avec le lien) ; le fondateur, le résumé des impayés ; un client hors ligne lit le nouveau tarif sur la facture suivante. Les avis de suppression, de changement de propriétaire, de tarif fixé par la mairie et de plafond baissé, eux, sont gardés pour les absents (`PowerPairing.tell`).
- **`resetday` avec une horloge très en avance au démarrage** : après `resetday`, plus aucune émission jusqu'à la date du plus grand jour émis (abonnements de ces jours non facturés, jamais de double facture). Envisager une commande d'équipe ; non codé.
- **Démos du lot 4 non raccordées** : `FilmReseau`, `F3Shots`, `DemoVisuels` posent des compteurs nus, qui ne laissent plus passer le courant depuis E6 ; non modifiés, non relancés.
- **Ligne vulnérable** : les câbles ne sont pas protégés (cahier) ; un piquage avant un compteur ne se voit qu'à l'écart du panneau (sortie moins compteurs), que fausse aussi la réserve d'un réseau de Crafts & Additions (sortie > compteurs pendant qu'il se remplit, vu dans l'essai) et la simulation de New Age (ci-dessus). Le policier prend le relais en RP.
- **Limite de Minecraft** (tâche 1) : un prélèvement prend des pièces dans l'**inventaire** (source portée ou mêlée, ou objet facture) ; les pièces sortent du playerdata, la facture payée et la caisse vivent dans des `SavedData` ; le joueur se déconnecte (playerdata écrit) puis le serveur s'arrête brutalement avant la sauvegarde du monde : au redémarrage les pièces sont **perdues** pour le joueur, la facture est **de nouveau due** et le créancier n'a rien reçu. Des pièces peuvent être détruites, **jamais dupliquées**. Pour un absent (attente, coffre-fort), tout vit dans des `SavedData` écrits ensemble ; seul un arrêt pendant l'écriture des fichiers les sépare. Aucune écriture forcée, par décision.
- **Équilibrage des prix** : jamais vécu en partie. Estimation du plan, pas une mesure : un foyer qui tire ~10 FE/tic pendant 8 h consomme ~5,8 millions de FE, ~58 cuivres par jour à 1 cuivre les 100 000 FE.
- **Extensions non codées** : amendes, impôt, abonnement du téléphone (points d'extension seulement) ; notification du téléphone absente.
- **Caisse ou trésor plein** : prouvé en GameTest (montant rendu, facture due) ; le refus arrive après le mouvement des pièces du débiteur (rendues par `CommerceDelivery.payTo`), parqué pour la revue finale.
- **Divers parqués** : mutation du `catch` du créancier en faute non faite (tâche 1) ; plafond `stationMaxPerTick` et `PowerRules.isClientOf` sans test dédié, coût par passage non mesuré (tâche 2) ; copie NBT créative d'un compteur jumelé, mondes d'essai d'avant le tour 1 de la tâche 3 inertes, mutation M5 de la tâche 3 non rejouée avec le pack ; contrat refait par le même joueur chez le même fournisseur sur le même compteur qui partage le relevé, jours au-delà de `payMaxMissedDays` non émis (tâche 4) ; client hors ligne non prévenu du tarif, quatre clients visibles à la fois, valeurs extrêmes coupées par « … », motif de la facture qui commence par une minuscule (« d'électricité de … », tâche 5) ; exception improbable de `payToSafeFirst` sans repli, demande de suppression oubliée au redémarrage, essais qui touchent l'état du serveur d'essai, repli d'une ancienne sauvegarde sans essai de rechargement, pas d'annonce publique à la suppression (tâche 6) ; test intermittent `TempsDeRoleGameTests.ecoSalarySetEtList` (vu une fois, tâche 1).
- **Jar de production** : jamais lancé (serveur de développement avec le pack seulement) ; installer un serveur Forge hors de Gradle demande l'accord de Killian (téléchargement).
- **Arrêt brutal** : l'essai fait un vrai `/stop`, pas un arrêt brutal du processus (voir la limite de Minecraft ci-dessus).
- **Relance complète du banc** : toujours en échec sur la couveuse du lot 5 (défaut du banc, ouvert dans `tasks/todo.md`) ; la relance ciblée de l'économie est faite à la place.
- **Graine du monde neuf** : l'essai du butin (E4) échoue si aucun animal n'est né à la génération près du départ (vu une fois, passage complet de 00:55) ; sans lien avec ce lot.
