# Vérifications du lot E1 : la monnaie

Relevés du 2026-11-13 (nuit du 12 au 13), sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 7 (preuve de bout en bout, film, vérification). Les tâches 1 à 6 ont leurs GameTests et JUnit, relancés ici.

**État : le lot est prouvé dans les limites de la section « Non prouvé ».** Pièces et change, pièce au sol, refus de l'argent hors des rangements permis, bourse, coffre-fort, somme de départ et commandes de l'équipe passent un essai de bout en bout sur serveur dédié avec le pack complet, un joueur sans droits, et sa relance, plus un film. **Un des deux joueurs de l'essai n'est pas un vrai client** : un seul client peut tourner à la fois (Killian travaille à distance), le second joueur est donc une connexion simulée côté serveur (`JoueurDeTest`), qui passe par les mêmes événements de connexion et les mêmes vérifications que celles d'un vrai joueur, mais pas par le réseau ni par un écran.

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test runGameTestServer` (sans pack, 02:31 à 02:32) | « All 612 required tests passed » (`run-gametest/logs/latest.log`, 02:31:58) ; JUnit : 330 tests, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml`) |
| `./gradlew --offline runGameTestServer -PwithPack` (02:32 à 02:34, après un premier lancement refusé par Gradle à la configuration : « No cached version of net.java.dev.jna:jna:5.10.0 available for offline mode », sans rapport avec le code, relancé sans changement) | « All 612 required tests passed » (02:33:50) |

## Serveur dédié et vrai client

`runServer -PwithPack` puis `runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack` et `runE2eRelance -PwithPack`. Client `Dev` avec le pack complet. L'étape est `E2eMonnaie.java` (client) et `E2eMonnaieServeur.java` (quatre commandes de console réservées au RCON : tapis de Create, pièces posées au sol, second joueur), dans `src/devcontent/java/dev/oneforallsurvival/dev/`. Le contrôle de la somme de départ est la **première** chose que fait `E2eClient` après la connexion (les autres lots vident l'inventaire) ; l'étape de la monnaie est branchée **après** le lot 7 et **avant** le lot 5, qui reste le dernier avant l'arrêt.

Le joueur est **non opérateur** pendant l'étape (`deop` par la console RCON, `hasPermissions(2)` lu faux côté client, survie) ; il redevient opérateur à la fin (et au plus tard avant `/stop`). Tout ce qui change le monde passe par la console RCON ; ses gestes sont de vrais clics : table de fabrication (grille, résultat), cases des écrans, boutons des écrans de la bourse et du coffre-fort, pose du coffre-fort, clic droit sur des blocs et des cadres. Le monde du serveur est neuf (l'ancien est rangé hors du dépôt) : le joueur est donc neuf pour l'économie, et le registre part de zéro (l'essai le vérifie avant de commencer et échoue sinon).

**Passage complet** (lots 1 à 7, puis E1, puis lot 5, lancés par Gradle) :

- Premier passage : connexion à 01:56:23, « bilan : TOUT PASSE » à 02:06:02, soit 9 min 39 s ; 238 lignes `PASS`, 0 `FAIL`.
- Relance (nouveau `runServer`, monde conservé) : connexion à 02:08:28, « bilan : TOUT PASSE » à 02:09:31, soit 63 s ; 68 `PASS`, 0 `FAIL`.
- « [souris] rendue » : **absent du journal du premier passage** (0 ligne : la souris n'a jamais été prise par le jeu pendant ce passage, la ligne n'est écrite que quand `SourisLibre` doit la rendre) ; **9 lignes** dans celui de la relance. Aucun client ni serveur n'est resté ouvert (le serveur s'arrête par `/stop`).
- Il a fallu plusieurs lancements pour y arriver (section « Incidents »).

### Ce qui passe : somme de départ, déconnexion, change

```
[e2e] PASS joueur neuf : 500 cuivres recus a sa premiere connexion (5 golds), le registre note 5 golds de somme de depart et une ligne
[e2e] PASS apres une vraie deconnexion et une reconnexion du client : il porte toujours 500 cuivres, le registre n'a rien ajoute (somme de depart 5 golds, Economy ledger, page 1 of 1 (1 lines))
[e2e] PASS change a la table : 5 or -> 50 argent (valeur 500 cuivres, avant 500) ; le serveur a autorise la sortie de pieces de la table
```

Le client se déconnecte vraiment (`level.disconnect()`, retour au titre) puis se reconnecte au serveur dédié : il porte toujours 500 cuivres et le registre n'a rien ajouté. Le change se fait à la vraie table : les 5 pièces d'or posées dans la grille donnent 10 argent, un clic majuscule sur le résultat en donne 50 (500 cuivres avant et après). Avant que les autres lots ne vident l'inventaire, l'argent de départ est retiré par `/eco take 500` (il est donc au registre comme détruit par l'équipe).

### Ce qui passe : les rangements interdits

```
[e2e] PASS pieces refusees par le coffre vanilla : curseur vide, aucune piece dans ses 27 cases, valeur du joueur inchangee (500) ; contenu lu par la console sans aucune piece
[e2e] PASS pieces refusees par la boîte à outils de Create : ... aucune piece dans ses 32 cases ...
[e2e] PASS pieces refusees par l'armoire de Farmer's Delight : ... aucune piece dans ses 27 cases ...
[e2e] PASS clic droit avec une piece sur un depot de Create : refuse, valeur inchangee (500), le depot ne porte aucune piece
[e2e] PASS geste de recharge (accroupi, clic droit sur une source) avec une piece en main : aucune piece perdue, valeur 500
[e2e] PASS temoin : le premier cadre prend un bloc de terre
[e2e] PASS pas de piece dans le cadre (alors que le premier prenait la terre) : cadre vide, valeur inchangee (500)
[e2e] PASS tapis de Create : 3 pile(s) de terre sur 3 (temoin) ont disparu du sol en 6 s (prises par le tapis), la piece d'argent est restee a 0,19 bloc de sa place, entiere (1)
```

Dans chaque stockage (un coffre vanilla, une boîte à outils `create:white_toolbox`, une armoire `farmersdelight:oak_cabinet`) le joueur essaie quatre gestes : prendre la pile au curseur et la poser, clic droit pour en poser une, majuscule-clic, touche numérique. Le curseur reste plein, aucune case du stockage ne montre de pièce, la valeur du joueur ne bouge pas et la console, qui lit le bloc, ne voit aucune pièce. Chaque cas a un témoin qui entre (le cadre prend la terre, le tapis prend la terre). Trois piles de terre sont posées sur les trois tapis, la pièce d'argent sur celui du milieu : en 6 s les trois piles ont été prises par le tapis, la pièce n'a pas bougé (0,19 bloc) ; le joueur la ramasse ensuite (valeur 500 comme avant). « Recharge » : le geste du détecteur du lot 7 (accroupi, clic droit sur une source d'énergie de Create Crafts & Additions) avec une pièce en main ne perd rien.

### Ce qui passe : la bourse et le coffre-fort

```
[e2e] PASS bourse fabriquee a la table avec sa recette (ficelle et trois cuirs) : Purse
[e2e] PASS la bourse ramasse les pieces au sol : 300 cuivres (3 or) dans la bourse, aucune piece libre en plus
[e2e] PASS Deposer tout : l'ecran de la bourse montre 3 or et 50 argent (800 cuivres)
[e2e] PASS Regrouper : l'ecran montre 8 or, 0 argent, valeur 800 (inchangee : 800)
[e2e] PASS /eco take : 5 golds taken from [Ingénieur] Dev (reason: reprise avant le coffre-fort) ; la bourse garde 300 cuivres
[e2e] PASS coffre-fort fabrique a la table avec sa recette
[e2e] PASS coffre-fort pose par un clic droit reel en 138, 150, 447
[e2e] PASS Deposer tout : le compte affiche 300 cuivres (3 or)
[e2e] PASS second coffre-fort refuse : aucun bloc en 142, 150, 447 (client et serveur : Test failed), l'objet reste dans l'inventaire du joueur cote serveur (Found 1 matching item(s) ...) ; cote client la pile est absente (prediction du client non corrigee)
[e2e] PASS second joueur (non operateur, connexion simulee) : il a recu 500 cuivres de depart, ne peut pas ouvrir le coffre-fort (ouvert=false, message [message.oneforallsurvival.safe.not_yours]), retrait et depot refuses (NOT_ALLOWED), compte inchange (0) ; apres sortie et retour il ne recoit rien de plus (depart 0, porte 500)
```

La bourse est fabriquée à la vraie table avec sa recette, ramasse 3 pièces d'or posées au sol à ses pieds (rien ne reste libre), puis ses boutons « Déposer tout » et « Regrouper » (cliqués sur l'écran) donnent 8 or. Le coffre-fort est fabriqué avec sa recette, posé par un vrai clic droit (le propriétaire est celui qui le pose), ouvert, rempli par « Déposer tout » (300 cuivres tirés de la bourse) ; un second est refusé à la pose. Le second joueur n'est pas un vrai client (voir plus haut) : non opérateur, il ne peut pas ouvrir le coffre-fort de Dev, ses retraits et dépôts sont refusés, le compte ne bouge pas.

### Ce qui passe : l'équipe, le registre et les totaux

```
[e2e] PASS /eco give : 1 ruby created for [Ingénieur] Dev (reason: injection d'un rubis) ; le joueur porte 1 piece de rubis
[e2e] PASS /eco take : 5 silvers taken from [Ingénieur] Dev (reason: retrait de controle) ; le joueur porte 9950 cuivres : 9 emeralds 9 golds 5 silvers
[e2e] PASS /eco stats : ... Starting money: 1 emerald | Created by staff: 1 ruby 8 golds | ... Removed by staff: 1 emerald 5 silvers | Vaults (exact mass): 1 ruby 2 golds 5 silvers | ... In circulation (created minus destroyed): 1 ruby 7 golds 5 silvers ; en circulation = coffre de Dev (10250) + ce que porte le second joueur (500) = 10750 : les comptes sont justes
[e2e] PASS /eco ledger : Economy ledger, page 1 of 1 (8 lines) ...
```

L'équipe injecte 1 rubis puis retire 50 cuivres (le rubis est cassé, la monnaie rendue : 9 émeraudes, 9 or, 5 argent). Les comptes attendus sont calculés d'avance : créé 1 000 (deux sommes de départ) et 10 800 par l'équipe (500 de change, 300 posés au sol, 10 000 de rubis), détruit 1 050 par l'équipe (500 avant les autres lots, 500 de reprise, 50 de retrait), coffre 10 250 : **en circulation 10 750 = coffre de Dev 10 250 + 500 portés par le second joueur**. Huit lignes au registre (cinq créations, trois destructions). Le total au coffre-fort ne compte que ce que le coffre tient : tout ce que Dev portait y a été déposé avant l'arrêt.

### Ce qui passe : après un vrai arrêt du serveur

```
[e2e] PASS relance : apres l'arret et la relance du serveur, le joueur ne recoit aucune somme de depart (il porte 0 cuivre) et /eco stats est identique a celui d'avant l'arret : ...
[e2e] PASS relance : le compte du coffre-fort est relu a l'identique : 10250 cuivres (1 ruby 2 golds 5 silvers)
[e2e] PASS relance : totaux du registre identiques a ceux d'avant l'arret : ...
[e2e] PASS relance : le second joueur revient apres le redemarrage : somme de depart redonnee 0, il porte toujours 500 cuivres, ouvert=false, retrait NOT_ALLOWED
```

Avant l'arrêt le joueur rentre à son point de départ (la zone se décharge). À la relance : le client se reconnecte, ne reçoit aucune somme de départ, `/eco stats` donne mot pour mot le texte d'avant l'arrêt et `/eco ledger` la même ligne d'en-tête (8 lignes), le compte du coffre-fort est relu à l'identique en ouvrant l'écran, le second joueur revient sans rien recevoir et sans pouvoir ouvrir le coffre-fort.

## Film

`./gradlew runDemoMonnaieFilm -PwithPack` (`FilmMonnaie`, 02:26 à 02:30) : monde intégré plat, une passe réussie, 818 images, `termine.json` sans échec, encodé en **`run-demo/film-lot-e1-monnaie.mp4`** (1 min 22 s, 1280 x 720, 10 images par seconde ; `ffmpeg` déjà installé sur la machine, rien de téléchargé). Capture des images du jeu lui-même (`FilmCapture`), « [souris] rendue » lu 7 fois dans le journal. **Le temps du monde n'est jamais accéléré.** La bourse et le coffre-fort sont donnés au joueur (leur fabrication est dans l'essai de bout en bout, la légende le dit). Images extraites (une toutes les 3,4 s) et ouvertes ; horodatages de l'incrustation, approximatifs :

- 0:02 à 0:06 : le joueur neuf, 500 cuivres (5 pièces d'or) dans la barre d'action, une table de fabrication devant lui.
- 0:09 à 0:12 : la table : 5 or dans la grille, le résultat propose 10 argent ; clic majuscule : 50 pièces d'argent dans l'inventaire, valeur inchangée.
- 0:16 à 0:19 : la bourse en main, les pièces d'or au sol, « il avance sur les pièces : 300 cuivres entrent dans la bourse » (bandeau : bourse 300).
- 0:23 à 0:33 : l'écran de la bourse (total, une ligne par coupure, « Déposer tout », « Regrouper ») : 3 or, puis 3 or et 50 argent, puis 8 or après « Regrouper », puis 7 or après « Retirer 1 ».
- 0:36 à 0:43 : le coffre ordinaire ; la pièce d'or est prise au curseur, refusée par la case 0 (vide), elle reste au curseur.
- 0:46 à 1:00 : le coffre-fort posé, son écran, « Déposer tout » (800 cuivres), « Retirer 1 » (700), le bloc vu de face.
- 1:03 : le second coffre-fort est refusé (« Vous avez déjà un coffre-fort : un seul par joueur »).
- 1:07 à 1:24 : `/eco give` et `/eco take`, puis `/eco stats` (créé par source, détruit par cause, masse des coffres-forts, en circulation) et `/eco ledger`, affichés dans le chat ouvert.

## Non prouvé

- **Crochetage** : le forçage du coffre-fort par le policier est renvoyé au lot du policier ; rien n'est codé ni essayé.
- **Impôt** : l'impôt du coffre-fort est renvoyé au lot du maire ; rien n'est codé ni essayé.
- **Stockages de mods non installés** : seuls les stockages du pack ont été essayés (coffre vanilla, boîte à outils de Create, armoire de Farmer's Delight en vrai ; douze types d'écran de mods en GameTest, tâche 3). Un mod absent du pack n'est pas couvert, ni un mod qui surcharge la case sans appeler Minecraft ni Forge.
- **Multijoueur à plus de deux** : un seul vrai client ; le second joueur est simulé côté serveur. Aucun joueur n'a vu les pièces au sol d'un autre, ni deux clients sur le même coffre-fort.
- **Équilibrage réel** : les valeurs (500 de départ, 1 / 10 / 100 / 1 000 / 10 000) n'ont pas été vécues en partie.
- **Jar livré** : jamais lancé hors du développement (structure du jar contrôlée seulement, tâche 3) ; les mixins ne sont éprouvés qu'en développement.
- **Pièces perdues à la mort, lave, vide, disparition au sol** : GameTests seulement (tâches 2 et 5), pas en vrai.
- **Mode créatif** : une bourse pleine ou une pile se copie par le clic milieu de l'onglet créatif ; non gardé (connu). Ne pas donner le créatif hors de l'équipe.
- **Pièces d'un `/give` vanilla** : non comptées au registre.
- **Coût serveur** : le balayage des écrans (une fois par seconde par joueur) et les mixins ne sont pas mesurés.
- **Tapis de Create en marche** : le tapis de l'essai n'est pas alimenté (comme en GameTest) ; une pièce posée sur un tapis qui avance n'est pas vue.

## Défauts d'affichage relevés pendant l'essai (non corrigés, hors du périmètre de la tâche)

1. **Inventaire du client périmé pendant les écrans de la bourse et du coffre-fort.** Ces menus n'ont aucune case : le client ne reçoit que les données de l'écran (les comptes), pas les changements de l'inventaire. Après « Déposer tout », la barre d'action du client montre encore les anciennes piles jusqu'à la fermeture de l'écran, où l'inventaire est resynchronisé (relevé dans l'essai : valeur lue côté client attendue seulement après la fermeture ; visible dans le film, 0:26 à 0:33). Ce n'est pas une perte : le compte du serveur est juste.
2. **La pile du second coffre-fort disparaît côté client à la pose refusée.** Le client prédit la pose (il ne connaît pas la règle « un par joueur », vérifiée côté serveur) et retire l'objet de sa main ; le serveur refuse, le message « un seul par joueur » s'affiche, mais l'objet n'est pas renvoyé : côté serveur il est toujours là (`clear ... 0` : « Found 1 matching item »), côté client il manque jusqu'à une resynchronisation.

## Incidents de l'essai lui-même

Aucun défaut du code livré n'a été trouvé en dehors des deux défauts d'affichage ci-dessus. Les échecs venaient du banc, chacun corrigé avant les passages qui font foi.

1. **Les identifiants de blocs.** `create:toolbox` et `farmersdelight:basket` n'existent pas comme blocs (`create:white_toolbox` et `farmersdelight:oak_cabinet`).
2. **Les pièces entrent d'abord dans la bourse.** La pièce de rubis de l'équipe n'apparaissait pas dans l'inventaire : elle était entrée dans la bourse vide restée dans l'inventaire. La bourse est retirée avant l'injection.
3. **Le joueur « tombe dans le vide » à son retour.** La garde du lot 7 et celle de l'étape de la monnaie (« sous la dalle ») restaient actives quand le joueur rentrait à son point de départ ; un joueur neuf apparaît au sol, bien sous la dalle (y = 150). Le lot 7 ne l'avait jamais rencontré (joueur d'un ancien monde, apparu plus haut). Corrigé dans `E2eExploration` (une ligne : la garde cesse avant le retour) et dans `E2eMonnaie`.
4. **Barre d'action pleine.** Les étapes des lots précédents remplissent la barre ; l'étape de la monnaie ouvre les écrans avec la case 8 en main si aucune n'est libre.
5. **Un monde laissé sale par un passage échoué.** Des récepteurs du lot 7 d'un passage échoué répondaient à `/signal send` (« 2 récepteurs ») : les passages qui font foi partent d'un monde neuf (l'ancien est rangé, hors du dépôt).
6. **Témoin du tapis intermittent.** Une pile de terre sur un seul tapis n'était pas prise une fois sur trois ; trois piles sur trois tapis, au moins une doit disparaître.
7. **Le serveur d'un passage essai (étape seule) s'est arrêté sans message** pendant la création des tapis, sans cause établie (aucun rapport de plantage) ; passage rejoué sans modification et réussi.
