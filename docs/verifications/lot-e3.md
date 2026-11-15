# Vérifications du lot E3 : trésor et salaires

Relevés du 2026-11-15, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 7 (preuve de bout en bout, vérification, wiki). Les tâches 1 à 6 ont leurs GameTests et JUnit, relancés ici. Plan : [`2026-11-14-lot-e3-tresor-salaires.md`](../superpowers/plans/2026-11-14-lot-e3-tresor-salaires.md). Commits du lot : `e43c922` (socle du trésor), `b57a5bf` (bloc), `daa19f5` (calendrier et temps de rôle), `f663105`, `47a38f8` (paie de minuit et dette), `98c680e` (tableau), `1653467`, `139a819`, `75071a5` (visuels, tableau 3 x 2, liquides), `6173ed5` (seule la police est salariée par défaut), puis l'essai de bout en bout et ce document.

**État : le lot est prouvé dans les limites de la section « Non prouvé ».** Pose du trésor unique, ouverture par le maire non opérateur, dépôts, dépense motivée tapée dans la fenêtre du motif, registre du trésor, tableau de la mairie, paie de minuit (coffre-fort, inventaire), salaires dus quand le trésor est vide, déplacement par l'ingénieur, rattrapage de deux jours manqués au démarrage et paiement de la file par ancienneté passent un essai de bout en bout sur serveur dédié avec le pack complet, puis sa relance après un vrai `/stop`. **Le vrai minuit n'est jamais attendu** : l'horloge de l'économie est décalée (`dev/HorlogeDEssai`). **Le second joueur `Agent` n'est pas un vrai client** : connexion simulée côté serveur (`JoueurDeTest`), qui passe par les mêmes événements de connexion et de déconnexion qu'un vrai joueur.

## Décisions du lot

| Sujet | Décision |
|---|---|
| Trésor (73) | Bloc unique posé par l'équipe (onglet créatif, aucune recette) ; un second est refusé. Contenu et registre gardés par le serveur (`TreasuryAccount`, même `SavedData`). Ouvert par le maire et l'équipe (écran du coffre-fort). Déplaçable par l'ingénieur (le rôle suffit à ce lot), le maire et l'équipe ; en créatif, casser exige d'être accroupi. Sans emplacement, taxe et salaires continuent ; seule l'ouverture est impossible. |
| Dépense publique | Un retrait du maire ou de l'équipe demande un motif (fenêtre « Motif de la dépense », 1 à 80 caractères), noté au registre du trésor (`SPEND`) et au registre global (`TREASURY_SPEND`). Dépôts notés (`DEPOSIT`, `TREASURY_DEPOSIT`). |
| Injection (87) | `/eco treasury give` ou `take <montant> <motif>` : source et puits déclarés de l'équipe, seuls changements de la masse en circulation par ce lot. |
| Tableau de la mairie | 3 blocs de large, 2 de haut (Killian, 2026-11-14) ; solde public et salaires dus ; clic droit : le même texte au chat. Posé par le maire (`/treasury board`) ou l'équipe. |
| Salaires (75) | Au temps passé connecté dans le rôle, à la seconde, côté serveur ; arrêtés à minuit au calendrier réel ; coffre-fort, sinon inventaire, sinon attente ; trésor insuffisant : dû, jamais en partie ; file payée par ancienneté à chaque minuit et au tick qui suit une entrée d'argent au trésor ; jours manqués serveur éteint rattrapés une fois chacun au démarrage (400 au plus). |
| **Taux par défaut** | **Seule la police est salariée par défaut** (Killian, 2026-11-15) : policier 60 cuivres par heure, médecin 0, maire 0 ; les joueurs gagnent leur argent eux-mêmes, l'équipe fixe un autre salaire par `/eco salary set`. Commit `6173ed5`. |
| Plafond | 8 h comptées au plus par joueur, par jour et par salaire. |
| Sauvegarde | Aucune écriture forcée hors de la sauvegarde du monde ; nouvelles données en `SavedData`, marquées sales. |
| Relance de l'essai | La relance complète du banc échoue toujours sur la couveuse du lot 5 (défaut du banc, ouvert dans `tasks/todo.md`) : passage complet avec le pack, puis **relance ciblée** de l'économie (`E2E_MONNAIE_SEULE=1 runE2eRelance`). |
| Ordre de la relance ciblée | Commerce (E2), monnaie (E1), puis **trésor (E3) en dernier** : la première reconnexion d'`Autre` reste celle que lit E2, et l'injection de la relance E3 changerait les comptes qu'E1 et E2 relisent. Avant l'arrêt, E3 réécrit les références de relance d'E1 et d'E2 (texte de `/eco stats`, ligne du registre, total du coffre-fort de Dev) ; leurs relances comparent `/eco stats` **sans la ligne « Salaires dus »**, que le rattrapage du démarrage change et qu'E3 contrôle exactement. La relance d'E1 ne range plus son aire : le coffre-fort de Dev y est, et E3 y paie des salaires ; E3 range les deux aires à la fin de sa relance. |
| Second joueur de l'étape E3 | Le brief demandait qu'`Autre` (citoyen) essaie d'ouvrir et de casser le trésor. `Autre` a une attente de 120 cuivres laissée par E2 : le connecter pendant E3 la lui livrerait et casserait la relance d'E2. **C'est `Agent`, encore citoyen, qui fait ces essais**, avant d'être fait policier. Même chemin (`TreasuryBlock.open`, `TreasuryActionPacket.apply`, casse du mode de jeu du serveur). |
| Film | Aucun (Killian : aucun film avant la fin complète du cahier de l'économie). |

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test runGameTestServer`, après le passage à « seule la police » (02:00 à 02:04) | « All 807 required tests passed » (02:04:52) ; JUnit : 419 tests, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml`, dont `PayrollTest.seuleLaPoliceEstSalarieeParDefaut`) |
| `./gradlew --offline runGameTestServer -PwithPack` (02:06 à 02:08) | « All 807 required tests passed » (02:08:27) ; pack chargé (69 lignes `createaddition`) |
| Mutation : anciens taux par défaut remis (médecin 60, maire 60) (02:08 à 02:10) | « 1 required tests failed » : `ecoSalarySetEtList` (« défauts : policier 60, maire 60, médecin 60 ») ; JUnit rouge : `seuleLaPoliceEstSalarieeParDefaut` (« expected: <[policier=60]> but was: <[policier=60, medecin=60]> ») ; restauré |
| Mutation : seul le maire remis à 60 (JUnit, 02:11) | `seuleLaPoliceEstSalarieeParDefaut` rouge (« expected: <0> but was: <60> ») ; restauré |
| `./gradlew --offline test runGameTestServer`, avec l'essai de bout en bout (02:58 à 03:00) | « All 807 required tests passed » (03:00:30) ; JUnit : 419 tests, 0 échec, 0 erreur |
| `./gradlew --offline runGameTestServer -PwithPack` (03:01 à 03:03) | « All 807 required tests passed » (03:03:38) ; 69 lignes `createaddition` |

Les GameTests qui supposaient le médecin ou le maire payés fixent leur taux le temps de l'essai et le rendent (`leMaireAccumuleDeuxSalaires`, `unRoleChangeEnCoursDeJourneeComptePourChacun`, `leMaireToucheSonSalaireEnPlusDeSonMetier`) ; `ecoSalarySetEtList` vérifie les défauts du monde d'essai neuf puis règle le maire à 45 et à 0.

## Serveur dédié et vrai client

`runServer -PwithPack` puis `runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack -Pe2eHorloge=<minutes>` et `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`, sous le verrou de lancement. Client `Dev` avec le pack complet, fenêtre sur le second écran. L'étape est `E2eTresor.java` (client), `E2eTresorServeur.java` (commandes de console réservées au RCON : `Agent`, ses gestes, lecture du trésor, du registre, de la paie, des coffres-forts, de l'attente ; `e2etresor horloge` règle le décalage) et `HorlogeDEssai.java` (au démarrage, `-Pe2eHorloge` en minutes, passé par `runServer` comme propriété `oneforallsurvival.e2e.horloge`), dans `src/devcontent/java/dev/oneforallsurvival/dev/`. Elle suit l'étape du commerce (E2). Chaque passage qui fait foi part d'un **monde neuf** ; les anciens sont rangés hors du dépôt, dans le dossier temporaire de la session.

Le joueur est amené sur une aire bâtie (dalle à `départ + (100, 150, 170)`, garde « sous la dalle » coupée avant chaque déplacement vers le coffre-fort de l'aire de la monnaie), opérateur pour la pose, puis **non opérateur** (`deop` par la console) pour tout le reste. Ses gestes sont de vrais clics : pose du trésor et du tableau, clic droit sur le trésor, le coffre-fort et le tableau, boutons de l'écran du trésor et du coffre-fort, motif tapé caractère par caractère puis bouton « Confirmer », casse au clic gauche tenu. Les commandes du maire (`/treasury log`, `/treasury board`) sont envoyées en son nom.

### Passages

| Passage | Connexion | Bilan | Contrôles |
|---|---|---|---|
| E1 + E2 + E3 seuls (`E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack`), monde neuf | 02:24:42 | « TOUT PASSE » à 02:31:29 | 134 `PASS` (98 d'E1 et E2, 36 d'E3), 0 `FAIL` |
| Relance ciblée de ce monde (`runServer -PwithPack -Pe2eHorloge=5610`, puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) : E2, E1, puis E3 | 02:34:24 | « TOUT PASSE » à 02:35:10 | 23 `PASS` (15 d'E1 et E2, 8 d'E3), 0 `FAIL` |
| **Passage complet** (`runE2eClient -PwithPack` : lots 1 à 7, E1, E2, E3, puis lot 5), monde neuf | 02:38:17 | « TOUT PASSE » à 02:53:03 | 325 `PASS`, 0 `FAIL` |
| **Relance ciblée** de ce monde (`runServer -PwithPack -Pe2eHorloge=5591`, puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`) | 02:57:03 | « TOUT PASSE » à 02:57:53 | 23 `PASS`, 0 `FAIL` |

Chaque premier passage écrit à son dernier pas « [e2e] horloge pour la relance : -Pe2eHorloge=N » (5610, puis 5591), repris tel quel pour la relance. Dans le passage complet, `Agent` reste connecté pendant l'étape du lot 5, après E3 : son temps du jour de l'arrêt (42 s à la lecture) devient une dette de 36 cuivres à la relance, dans la borne que la relance calcule (ce qui était compté, plus ce que le temps écoulé a pu ajouter à 600 par heure).

Journaux des clients et du serveur gardés hors du dépôt (dossier temporaire de la session) ; les images du passage complet sont dans `run-e2e/frames-e2e/` (non commitées).

« [souris] rendue » et « [fenetre] second écran (2600, 40) » sont lus dans le journal de chaque client ; aucun client ni serveur n'est resté ouvert (le serveur s'arrête par `/stop`) ; le verrou est vidé après chaque lancement.

### Horloge

Au premier passage, l'horloge de l'économie est réglée à 100 s de la frontière du jour de paie (`e2etresor horloge frontiere 100`), puis, après la dépense du reste, à 30 s de la frontière suivante. Le décalage est relevé en minutes entières arrondies vers le haut ; la relance part de ce décalage plus deux jours (`-Pe2eHorloge`), si bien que l'économie lit, au démarrage de la relance, deux jours de paie de plus qu'à l'arrêt. Journal du serveur de la relance ciblée :

```
[horloge] decalage de 5610 minutes (336600000 ms) au demarrage : l'economie lit 2026-11-18T23:02:46.292Z
```

### Ce qui passe : pose, citoyen, maire

Lignes du passage E1 + E2 + E3 seuls (02:27 à 02:31) ; le passage complet (02:46 à 02:50 pour l'étape E3) donne les mêmes contrôles avec d'autres positions et des salaires du second jour de 15 cuivres au lieu de 16.

```
[e2e] PASS depart du tresor : TRESOR total=0 position=aucune lignes=0 ...
[e2e] PASS tresor pose par un clic droit reel en 323, 150, 36 ; le serveur note l'emplacement (TRESOR total=0 position=323,150,36 lignes=1
[e2e] PASS second tresor refuse : bloc vide en 325, 150, 36, l'objet reste dans l'inventaire du client (1), message « The treasury already exists at 323, 150, 36 »
[e2e] PASS /eco treasury give 500 : 5 golds created in the treasury (reason: Dotation de depart de la ville) ; tresor 500 cuivres ; en circulation 11650 -> 12150 (source declaree de l'equipe)
[e2e] PASS Agent connecte (simule, non operateur), il porte 500 cuivres (somme de depart d'un joueur neuf)
[e2e] PASS citoyen : clic droit refuse ([message.oneforallsurvival.treasury.open_denied], menu InventoryMenu), retrait force NOT_ALLOWED ; casse refusee ([message.oneforallsurvival.treasury.break_denied]), le tresor reste en place avec ses 500 cuivres
[e2e] PASS Dev porte 2 or sortis de son coffre-fort (10250 -> 10050 cuivres)
[e2e] PASS ecran du tresor ouvert par le maire (non operateur) : 500 cuivres, 5 or
[e2e] PASS depot 1 : l'ecran montre 600 cuivres, l'inventaire du client 1 or
[e2e] PASS depot 2 : l'ecran montre 700 cuivres, l'inventaire du client 0 or
[e2e] PASS fenetre du motif ouverte (champ «  »)
[e2e] PASS depense confirmee : l'ecran du tresor montre 600 cuivres, Dev porte 1 or
[e2e] PASS /treasury log (maire) : Treasury ledger, page 1 of 1 (5 lines)|... -1 gold  Public spending  Dev: Achat de lanternes pour la place|... +1 gold  Deposit  Dev: |... +1 gold  Deposit  Dev: |... +5 golds  Staff injection  Rcon: Dotation de depart de la ville|...    Treasury placed  Dev: 323, 150, 36 ; registre du serveur : [PLACED/Dev/0/..., INJECTION/Rcon/500/..., DEPOSIT/Dev/100/, DEPOSIT/Dev/100/, SPEND/Dev/100/Achat de lanternes pour la place] ; /eco ledger note les transferts (« Dev : depot au tresor », « Dev : Achat de lanternes pour la place »)
```

Le client de l'essai est en anglais (langue du dossier `run-e2e`) : les textes lus au chat sont ceux d'`en_us.json`, comparés aux traductions du client.

### Ce qui passe : tableau de la mairie

```
[e2e] PASS tableau pose en 331, 150, 36 (six parties vues par le client)
[e2e] PASS tableau : cache du client Snapshot[balance=600, owedTotal=0, owedCount=0] ; lignes du rendu « Balance: 6 golds » et « No wages owed »
[e2e] PASS clic droit sur le tableau : « Town Treasury|Balance: 6 golds|No wages owed » dans le chat, comme le rendu
```

Le rendu a été regardé sur l'image `run-e2e/frames-e2e/f05072.png` du passage complet (02:47:03, pas « Le cache du client et le rendu affichent le solde du trésor ») : le tableau de 3 x 2 porte « Town Treasury », « Balance: 6 golds », « No wages owed », lisibles, sur le parchemin. Le contrôle automatique, lui, porte sur le cache du client et sur les lignes que le rendu et le chat partagent (`TownFinances`), pas sur les pixels.

### Ce qui passe : minuit

Salaires d'essai fixés par `/eco salary set` (policier et maire à 600 par heure, pour que quelques minutes donnent des montants lisibles) ; Dev policier et maire, avec son coffre-fort ; Agent policier, sans coffre-fort.

```
[e2e] PASS minuit (horloge decalee) : jour 2026-11-15 clos ; [SALARY_OWED/Agent/26/salaire policier 2026-11-15, SALARY_OWED/Dev/26/salaire mayor 2026-11-15, SALARY_OWED/Dev/26/salaire policier 2026-11-15, SALARY_PAID/Agent/26/..., SALARY_PAID/Dev/26/salaire mayor 2026-11-15, SALARY_PAID/Dev/26/salaire policier 2026-11-15, DAY_CLOSED//78/2026-11-15 : 3 salaire(s)] ; Dev paye dans son coffre-fort 10050 -> 10102 (+52, deux salaires) ; Agent paye dans son inventaire 500 -> 526 (+26) ; tresor 522
[e2e] PASS avis : Dev lit deux fois « paid into your safe » ([Wage for 2026-11-15 (Mayor, 0 h 2 min): 2 silvers 6 coppers, paid into your safe, Wage for 2026-11-15 (Policier, 0 h 2 min): 2 silvers 6 coppers, paid into your safe]) ; Agent recoit [message.oneforallsurvival.salary.paid]
[e2e] PASS le maire depense le reste (522 cuivres) en 9 retrait(s), chacun note SPEND avec son motif ; Dev porte 622
[e2e] PASS second minuit, tresor vide : [SALARY_OWED/Agent/16/..., SALARY_OWED/Dev/16/salaire mayor 2026-11-16, SALARY_OWED/Dev/16/salaire policier 2026-11-16, DAY_CLOSED//48/2026-11-16 : 3 salaire(s)] ; dettes [4:Agent:policier:2026-11-16:16, 5:Dev:mayor:2026-11-16:16, 6:Dev:policier:2026-11-16:16] (48 cuivres) ; /eco stats : ...|Wages owed: 4 silvers 8 coppers (3)|...
[e2e] PASS tableau : cache Snapshot[balance=0, owedTotal=48, owedCount=3] (« Wages owed: 3 (4 silvers 8 coppers) ») ; Dev prevenu : [The town could not pay your wage for 2026-11-16 (1 silver 6 coppers): it is still owed., (idem, maire)]
[e2e] PASS roles : MAIRE nom=aucun roleDev=ingenieur
```

La dépense du reste a été faite en 9 retraits, chacun avec la fenêtre du motif ; le premier motif est tapé, les suivants sont proposés par l'écran (dernier motif confirmé) et confirmés.

### Ce qui passe : déplacement, comptes avant l'arrêt

```
[e2e] PASS tresor casse par l'ingenieur en 7,6 s
[e2e] PASS objet du tresor ramasse ; serveur : {total=0, position=aucune, lignes=26} ; registre : [REMOVED/Dev/0/323, 150, 36]
[e2e] PASS tresor repose par l'ingenieur en 327, 150, 36 : solde 0 (identique), 27 lignes au registre, les dernieres : [REMOVED/Dev/0/323, 150, 36, PLACED/Dev/0/327, 150, 36] ; dettes inchangees
[e2e] PASS Dev range 622 cuivres : coffre-fort 10724, il ne porte plus rien
[e2e] PASS /eco stats : ...|Vaults (exact mass): 1 ruby 7 golds 2 silvers 4 coppers|Wages owed: 4 silvers 8 coppers (3)|In circulation (created minus destroyed): 1 ruby 2 emeralds 6 golds 5 silvers|... ; les 500 injectes sont tous chez les joueurs : coffre-fort de Dev +474, Agent +26 ; tresor 0, 48 cuivres de salaires dus (une dette n'est pas de l'argent)
[e2e] PASS avant l'arret : jour 2026-11-17, temps compte du jour [Dev:mayor:2:1200;Dev:policier:2:1200;Agent:policier:43:25800;], dettes [...] ; relance a lancer avec -Pe2eHorloge=5610 (decalage 2730 min + deux jours)
```

Comptes contrôlés avant l'arrêt (cuivres, calculés d'avance à partir de ce qu'a lu l'étape) : en circulation = avant l'étape (11 650) + 500 (injection) + 500 (somme de départ d'`Agent`, joueur neuf : le brief n'en parlait pas) = 12 650 ; dépôts au trésor + 200 ; dépenses publiques + 100 + le reste (522) ; salaires versés + 78 ; trésor 0 ; attente inchangée (les 120 d'`Autre`) ; masse des coffres-forts − 200 + 100 + 522 + 52.

### Ce qui passe : après un vrai arrêt du serveur (relance ciblée)

```
[e2e] PASS relance : apres l'arret et la relance du serveur, le joueur ne recoit aucune somme de depart (il porte 0 cuivre) et /eco stats est identique a celui d'avant l'arret : ...|Wages owed: 5 silvers 5 coppers (4)|...
[e2e] PASS relance du commerce : /eco stats identique a celui d'avant l'arret (attente comprise) : ...
[e2e] PASS relance : le compte du coffre-fort est relu a l'identique : 10724 cuivres (1 ruby 7 golds 2 silvers 4 coppers)
[e2e] PASS relance du tresor : horloge decalee de 5610 min (-Pe2eHorloge), jour de paie 2026-11-19 (deux de plus que 2026-11-17)
[e2e] PASS relance : le bloc du tresor est relu en 327, 150, 36
[e2e] PASS relance : tresor relu ({total=0, position=327,150,36, lignes=30}), 27 lignes d'avant l'arret identiques ; le rattrapage du demarrage a clos 2026-11-17 et 2026-11-18 : [SALARY_OWED/Agent/7/salaire policier 2026-11-17, DAY_CLOSED//7/2026-11-17 : 1 salaire(s), DAY_CLOSED//0/2026-11-18 : 0 salaire(s)] (temps compte avant l'arret : [Dev:mayor:2:1200;Dev:policier:2:1200;Agent:policier:43:25800;], le temps du jour a survecu a l'arret)
[e2e] PASS relance : dettes [4:Agent:policier:2026-11-16:16, 5:Dev:mayor:2026-11-16:16, 6:Dev:policier:2026-11-16:16, 7:Agent:policier:2026-11-17:7] (les 3 d'avant l'arret en tete, memes identifiants) ; tableau : cache Snapshot[balance=0, owedTotal=55, owedCount=4] (« Wages owed: 4 (5 silvers 5 coppers) »)
[e2e] PASS relance : le tableau dit « Town Treasury|Balance: 0 copper|Wages owed: 4 (5 silvers 5 coppers) »
[e2e] PASS /eco treasury give 500 : 5 golds created in the treasury (reason: Paie des salaires dus) ; registre : [INJECTION/Rcon/500/Paie des salaires dus, SALARY_PAID/Agent/16/salaire policier 2026-11-16, SALARY_PAID/Dev/16/salaire mayor 2026-11-16, SALARY_PAID/Dev/16/salaire policier 2026-11-16, SALARY_PAID/Agent/7/salaire policier 2026-11-17] (INJECTION puis les salaires dans l'ordre des dettes) ; coffre-fort de Dev 10724 -> 10756 (+32, 2 avis au chat) ; Agent absent : 23 cuivres en attente ; tresor 445
[e2e] PASS relance : Agent revient, recoit 23 cuivres (il porte 526 -> 549, aucune nouvelle somme de depart) et les avis [message.oneforallsurvival.commerce.delivered,message.oneforallsurvival.salary.absent_paid] : « [A pending delivery has been handed over to you. | Wages paid while you were away: 2 silvers 3 coppers (held, delivered when you log in).] »
```

Le temps d'`Agent` du jour de l'arrêt (43 s comptées à la lecture, 7 cuivres à 600 par heure) devient sa dette du 2026-11-17 : la relance la vérifie entre ce qui était compté à la lecture et ce que le temps écoulé jusqu'à la relance a pu ajouter (Agent est resté connecté jusqu'au `/stop`). Les dettes de Dev ce jour-là (2 s dans chaque salaire) valent 0 cuivre : aucune dette, comme prévu (pas de dette de montant nul).

### Incidents de l'essai

Aucun échec : les quatre lancements ont passé au premier essai. « [souris] rendue » est lu 20 fois dans chaque premier passage et 8 fois dans la première relance ; aucune ligne dans la seconde relance (le jeu n'a pas repris la souris, comme à la relance d'E2 du 2026-11-14).

## Captures

Les captures de la tâche 6 (`run-demo/visuels-captures/`, non commitées : trésor, tableau 3 x 2, écran du trésor, bulle, fenêtre du motif, grille) restent vraies : aucun écran ni rendu n'a changé depuis (la tâche 7 ne touche que les valeurs par défaut de la config, les essais et la documentation). Elles ne sont pas refaites.

## Passe finale (revue finale)

Relevés du 2026-11-15, 03:20 à 04:00, sur `main`. Commits `8df2bfa` (paie en pause sur saut d'horloge) et `756ffd9` (mineurs de la revue finale).

### Ce qui change

| Point de la revue | Correction |
|---|---|
| Important 1 : horloge qui avance par erreur puis revient | Pendant que le serveur tourne, si l'horloge a avancé de plus de 26 h depuis la vérification précédente (une par seconde), la **paie se met en pause** (`PayrollService.onClock`) : aucun jour clos, avertissement au journal et aux opérateurs connectés (et à la connexion d'un opérateur) avec la date vue et la date attendue, ligne « Paie en pause » dans `/eco stats`. La pause est gardée dans `Payroll` (une relance ne la lève pas). L'équipe choisit : `/eco salary resume` (l'heure est juste : les jours dus se closent, comme un rattrapage) ou `/eco salary resetday` (l'heure était fausse : dernier jour réglé ramené à la veille selon l'horloge actuelle, jamais avancé ; temps compté sur des jours futurs reporté sur aujourd'hui). Le rattrapage du démarrage reste normal : la première vérification après un chargement n'a pas de référence. L'avertissement de l'horloge qui recule dit maintenant de faire `/eco salary resetday`. |
| M1 | `TreasuryActionPacket.apply` contrôle le droit (`NOT_ALLOWED`) avant l'emplacement (`NO_SAFE`). |
| M2 | Le motif de `/eco treasury give` et `take` passe par `TreasuryActionPacket.validReason` : ni « § » ni caractère de contrôle. |
| M3 | Le démarrage (`PayrollService.startUp`) paie la file après le rattrapage. |
| M4 | `payToSafeFirst` renvoie `INVENTORY` dès qu'une part va à l'inventaire. |
| M5 | GameTest `unMaireDestitueEcranOuvertNeRetireRien`. |
| M6 | `TreasuryBlock.getDrops` : rien ne tombe si le bloc cassé n'était pas le trésor noté (relevé dans `playerWillDestroy`, avant le retrait). |
| M7 | Le temps reporté d'un jour réglé (et celui ramené par `resetday`) respecte le plafond de 8 h du jour qui le reçoit ; les cuivre-secondes suivent au prorata. |
| M10 | La barre verticale de « give, take » du tableau des décisions est retirée (« `give` ou `take` »). |
| Trouvé en passant | `Payroll.carryClosedDays` levait une `ConcurrentModificationException` dès que deux jours étaient à reporter (ajout du jour cible pendant le parcours) ; vu rouge par `PayrollTest.leReportRespecteLePlafondDuJour`, corrigé en relevant les jours d'abord. |

### Preuves

| Commande | Résultat lu |
|---|---|
| Nouveaux tests sur l'ancien code (bouchons pour les nouvelles méthodes), JUnit (03:25) | 3 échecs : `leReportRespecteLePlafondDuJour` (`ConcurrentModificationException`), `laPauseSurvitALaSauvegarde`, `ramenerLaDateReporteLesJoursFutursSurAujourdHui` |
| Idem, `runGameTestServer` (03:28 à 03:30) | « 10 required tests failed » : `unSautDHorlogeMetLaPaieEnPause` (« aucun jour clos pendant le saut, vu 10 »), `reprendreClotLesJoursUneFois`, `ramenerLaDateApresLeRetourDeLHeure`, `ramenerLaDateApresDesJoursClosParErreur`, `enPauseLeDemarrageNeClotRien`, `auDemarrageLaFileEstPayee`, `coffreFortAuPlafondLeResteVaALInventaire` (avis « coffre-fort »), `unBlocHorsEmplacementNOuvreRien` (objet lâché), `unAutrePaquetQueLeMenuOuvertEstRefuse` (`NO_SAFE`), `injectionDeLEquipeAuTresor` (« § » accepté). `unMaireDestitueEcranOuvertNeRetireRien` et `unDemarrageApresDixJoursEteintRattrapeSansPause` passaient déjà : ils gardent un comportement existant. |
| `./gradlew --offline test runGameTestServer` (03:32 à 03:35) | « All 815 required tests passed » (03:34:50) ; JUnit : 422 tests, 0 échec, 0 erreur |
| `./gradlew --offline runGameTestServer -PwithPack` (03:35 à 03:37) | « All 815 required tests passed » (03:37:14) ; pack chargé (69 lignes `createaddition`) |
| Mutation : détection du saut désactivée dans `onClock` (03:37 à 03:39) | « 3 required tests failed » : `unSautDHorlogeMetLaPaieEnPause` (« aucun jour clos pendant le saut, vu 10 »), `reprendreClotLesJoursUneFois`, `ramenerLaDateApresLeRetourDeLHeure` ; restaurée |
| E2E ciblé économie, monde neuf, avec le pack (`E2E_MONNAIE_SEULE=1 runE2eClient -PwithPack`, 03:43 à 03:53) | « [e2e] bilan : TOUT PASSE » (03:53:16), 134 `PASS`, 0 `FAIL` ; « -Pe2eHorloge=5528 » ; aucune pause au journal du serveur (les deux réglages d'horloge de l'essai sautent de moins de 24 h) |
| Relance ciblée (`runServer -PwithPack -Pe2eHorloge=5528`, puis `E2E_MONNAIE_SEULE=1 runE2eRelance -PwithPack`, 03:56 à 03:59) | « [e2e] bilan : TOUT PASSE » (03:58:51), 23 `PASS`, 0 `FAIL` ; « [horloge] decalage de 5528 minutes » ; rattrapage de deux jours sans pause |

« [fenetre] second écran (2600, 40) » et « [souris] rendue » lus dans le journal des deux clients ; aucun Java resté ouvert. Ancien monde du serveur rangé hors du dépôt.

### Limites

- Le seuil de 26 h laisse passer un saut plus court (une horloge en avance d'un jour clôt ce jour une fois) ; `resetday` ramène alors la date, sans double paiement.
- Un serveur réellement figé plus de 26 h (machine en veille) met aussi la paie en pause : l'équipe fait `resume`.
- Le temps reporté au-delà du plafond de 8 h n'est pas payé (règle du plafond), le journal le dit.
- Le vrai saut d'horloge du système n'a pas été vécu : l'horloge est injectée dans les GameTests.

## Non prouvé

- **Vrai minuit jamais attendu** : toute la paie de l'essai passe par l'horloge décalée (`EconomyClock`, `dev/HorlogeDEssai`) ; le passage d'un vrai minuit sur un serveur en marche n'a pas été vécu.
- **Changement d'heure** (heure d'été, heure d'hiver) : prouvé en JUnit seulement (`EconomyCalendarTest`), jamais en vrai.
- **Inactivité au clavier** : non détectée, par décision (plafond de 8 h par jour et par salaire) ; un joueur laissé connecté dans son rôle est payé.
- **Joueurs simulés** : `Agent` n'est pas un vrai client ; ses gestes passent par les méthodes du serveur, pas par le réseau ni par un écran.
- **Relance complète du banc** : toujours en échec sur la couveuse du lot 5 (défaut du banc, ouvert dans `tasks/todo.md`) ; la relance ciblée de l'économie est faite à la place.
- **Limite de Minecraft, salaire versé dans l'inventaire puis arrêt brutal** : un salaire payé dans l'inventaire d'un joueur **sans coffre-fort**, suivi de sa déconnexion (son playerdata est écrit, avec le salaire) puis d'un arrêt brutal **avant la sauvegarde du monde**, est reversé au redémarrage : la paie (dette, dernier jour réglé) sur le disque est celle d'avant le paiement, la dette existe encore et sera payée une seconde fois. C'est la classe de fenêtre décrite dans [`lot-e2.md`](lot-e2.md), « Seconde passe » (tout passage de données du monde vers un inventaire) ; elle dure au plus jusqu'à la sauvegarde automatique suivante. **Aucune écriture forcée**, par décision (toute écriture partielle ouvre une fenêtre plus large). Un salaire versé au coffre-fort ou en attente vit, lui, dans les données du monde, écrites avec la paie.
- **Équilibrage des taux** : 60 cuivres par heure pour le policier, 0 pour le médecin et le maire, plafond de 8 h, somme de départ de 500 : rien n'a été vécu en partie. L'essai fixe 600 par heure pour obtenir des montants lisibles en quelques minutes.
- **Crochetage du trésor** : renvoyé au lot du policier (feature 67) ; point d'extension `TownBlockRules.mayOpenTreasury` ; rien n'est codé ni essayé.
- **Outil de maintenance de l'ingénieur** : renvoyé au lot du téléphone ; à ce lot, le **rôle** d'ingénieur suffit pour déplacer le trésor et le tableau (point d'extension `TownBlockRules.engineerMayMove`).
- **Bureau, annonces, registre des décisions, alerte de dépense au-delà d'un seuil** : renvoyés au lot du maire. Le tableau a un point d'extension pour les annonces (`TownBoardBlockEntity.notices()`, vide) ; aucune alerte de dépense n'existe.
- **Tableau d'un bloc d'un ancien monde** (conversion en 3 x 2 au chargement) : prouvé en GameTest par `onPlace` seulement, pas sur un vrai ancien monde.
- **Arrêt brutal** : simulé en GameTest seulement (`uneClotureRelueNeSeRejouePas`, `leTempsDuJourSurvitAUneRelance`) ; l'essai de bout en bout fait un vrai `/stop`, pas un arrêt brutal du processus.
- **Jar livré** : jamais lancé hors du développement.
