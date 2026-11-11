# Vérifications du lot 6 : vie de la ville

Relevés du 2026-11-11, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 8 (preuve de bout en bout, film). Les tâches 1, 2, 5 et 7 ont leurs GameTests, relancés ici.

**Retirés le 2026-11-11, avant cette preuve : la salle de projection (feature 26), que remplace WaterFrames, et le projecteur orientable de la feature 17 (le phare reste seul).** Rien d'eux n'est construit, éprouvé, filmé ni documenté ici.

**État : le lot est prouvé dans les limites de la section « Non prouvé ».** Chauffe-eau, douche, effet Bien-être et phare passent un essai de bout en bout sur serveur dédié avec le pack complet (First Aid compris) et sa relance, et un film. L'essai n'a trouvé aucun défaut du code livré ; un GameTest de la tâche 5 attendait la lumière du ciel avec un délai trop court et a été corrigé (section « Incidents »).

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew cleanTest test` (18:46) | 287 tests JUnit, 0 échec (somme des `build/test-results/test/*.xml`) |
| `./gradlew runGameTestServer` (sans pack, 18:45, après le dernier changement de code) | « All 436 required tests passed » (`run-gametest/logs/latest.log`) |
| `./gradlew runGameTestServer -PwithPack` (18:47, même état du code) | « All 436 required tests passed », avec **deux lignes `[SKIP]`** : `unJoueurFaibleNeMeurtPas` et `unJoueurEstBlesse` (clôture du lot 5, sautés quand First Aid est chargé) ; 72 lignes Create / Additions au journal |

Les GameTests de la douche et du Bien-être (lot `douche_a`, `douche_b`) ont donc tourné avec First Aid et passent, le joueur d'essai étant un `JoueurDeTest`.

## Serveur dédié et vrai client

`./gradlew runServer -PwithPack` puis `./gradlew runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack` et `./gradlew runE2eRelance -PwithPack`. Client `Dev`, opérateur de `run-server`, rôle Ingénieur, avec le pack complet **First Aid compris**. L'étape du lot 6 est `src/devcontent/java/dev/oneforallsurvival/dev/E2eVille.java`, branchée dans `E2eClient` après l'étape du réseau et **avant** celle du lot 5 (voir « Incidents »). Son aire est une dalle de pierre à 70 blocs à l'ouest et 60 au sud du joueur, en hauteur (y = 150), hors des aires des lots précédents. Le montage est posé par `/setblock` ; les gestes du joueur (clic droit sur le chauffe-eau) sont de vrais clics ; les états sont lus côté client (blocs, menu du chauffe-eau, effets du joueur) ou par la console RCON (`data get block`, `data get entity`). Les clients sont lancés par ces tâches Gradle et portent une propriété `oneforallsurvival.*` : le garde `SourisLibre` est actif (« [souris] rendue » lu dans chaque journal) ; aucun client ni serveur n'est resté ouvert (`tasklist` : aucun `java.exe` à la fin).

**Un passage unique de bout en bout** (étapes des lots 1 à 6) puis la relance : première phase **153 lignes `PASS`, 0 `FAIL`, « bilan : TOUT PASSE »** (7 min 11 s, de 18:20 à 18:28, serveur et client lancés par Gradle) ; relance (nouveau `runServer`, `runE2eRelance`, qui rejoue les lots 2 à 6) **53 `PASS`, 0 `FAIL`, « bilan : TOUT PASSE »** (1 min 26 s, de 18:29 à 18:30). Il a fallu plusieurs lancements pour y arriver (voir « Incidents »). La mise au point de l'étape seule a utilisé la variable d'environnement `E2E_VILLE_SEULE`, qui saute les étapes des lots précédents.

### Ce qui passe : la chaîne d'eau et la douche

Une vraie chaîne, par de vrais tuyaux (`create:fluid_pipe`), trois pompes (`create:mechanical_pump`) entraînées chacune par un engrenage et un moteur créatif : un puits de quatre sources, une pompe, le dessalinisateur, une pompe, le chauffe-eau, une pompe montante, trois tuyaux, la pomme de douche. Chaque machine touche une source créative de Create Crafts & Additions. Lignes du passage final (journal du client, `en_us`) :

```
[e2e] PASS avant les moteurs, apres 4 s : dessalinisateur 0 mB d'eau, chauffe-eau 0 / 0, douche 0 (rien ne coule sans pompe)
[e2e] PASS les trois pompes tournent (vitesses lues par la console : -16.0, -16.0, 16.0 tr/min)
[e2e] PASS dessalinisateur : cuve d'eau 152 mB, potable 0 mB, venue du puits par la pompe A et deux tuyaux
[e2e] PASS deop par RCON, hasPermissions(2) cote client = false ; joueur sous la pomme seche en BlockPos{x=-277, y=150, z=403}, gamemode SURVIVAL
[e2e] PASS sans eau chaude : 8 s sous la pomme, aucun Bien-etre, cuve de la pomme 0 mB, pomme eteinte (...)
[e2e] PASS ecran du chauffe-eau : eau potable 0 mB, eau chaude 120 mB, energie 8000/8000 FE, etat STOPPED
[e2e] PASS pomme reliee : cuve de 312 mB d'eau chaude arrivee du chauffe-eau (apres 5 s) ; chauffe-eau potable 0 chaude 88 ; dessalinisateur potable 200
[e2e] PASS depart de la douche : vie client 10.588236 | vie serveur 10.588236 | First Aid membres [head=1.0, ... body=6.0] (somme 18.0) ; effets benefiques []
[e2e] PASS Bien-etre recu apres 4,4 s d'eau chaude continue sous la pomme (...), niveau 0, duree 7199 ticks, pomme active true
[e2e] PASS une douche de plus ne cumule pas : duree maximale vue 7199 ticks pour un plafond de 7200 (6 min), minimale 7100, l'effet reste actif
[e2e] PASS le Bien-etre ne rend aucun point de vie : vie client 10.588236 -> 10.588236, somme des membres de First Aid 18.0 -> 18.0 (naturalRegeneration coupee), seul effet benefique : [effect.oneforallsurvival.well_being(niveau 0, durée 7136)] ; cuve de la pomme 400 -> 344 mB
```

Ce que chaque ligne mesure, et ses limites :

- **Rien sans moteur, tout avec** : avant les moteurs, les cuves d'aval restent à 0 (la pompe est donc le seul chemin) ; après, l'eau arrive au dessalinisateur, puis au chauffe-eau (l'écran, ouvert par un vrai clic droit, montre eau potable, eau chaude et énergie lues par le client), puis à la pomme.
- **Joueur non opérateur** : `deop` par la console RCON, `hasPermissions(2)` faux côté client, survie. Tous les déplacements du joueur passent par `/tp` de la console. Sa vie est abaissée par `/damage` (starve) ; la régénération naturelle est coupée (`gamerule naturalRegeneration false`).
- **Cinq secondes continues** : sous la pomme **sans tuyau** (cuve vide), 8 s sans aucun effet (plus que les 5 s exigées) ; sous la pomme reliée, le Bien-être arrive entre 4,2 et 4,8 s après la téléportation sur les quatre passages (4,4 s au passage final). Cette durée est mesurée côté client à partir de l'ordre de téléportation : le seuil de 5 s du serveur compte des relevés d'une seconde dont la phase est inconnue, d'où un écart sous 5 s. L'essai échoue si l'effet vient avant 3,8 s.
- **Pas de soin** : vie vanilla du client et du serveur, somme des huit membres de First Aid (relevée par `data get entity ... ForgeCaps."firstaid:cap_adv_dmg_mdl"`) identiques avant et après l'effet, et le seul effet bénéfique du joueur est le Bien-être. C'est la preuve du « jamais de soin » en jeu sous First Aid.
- **Pas de cumul** : sept secondes de plus sous la pomme, la durée vue ne dépasse jamais 7199 (plafond 7200 = 6 min) et retombe à 7200 au second octroi (minimale vue 7100 : le second octroi a bien eu lieu).

### Ce qui passe : le phare

```
[e2e] PASS phare allume en tour libre : y = 153 (mer a 63), etat RUNNING, lit vrai, lumiere 15
[e2e] PASS sous un toit : lit faux, etat STOPPED (la source creative est toujours la : il ne consomme rien faute de vouloir travailler)
[e2e] PASS toit retire : phare rallume, etat RUNNING, lit vrai
```

Le phare, sur trois blocs de pierre, s'allume (état RUNNING, propriété `lit`, lumière 15), s'éteint sous un toit de pierre posé au-dessus de lui (état STOPPED, `lit` faux) et se rallume quand le toit est retiré. « Il ne consomme rien » est déduit de l'état STOPPED (la machine ne veut pas travailler) : la source créative ne permet pas de mesurer la consommation, qui n'est vue qu'en GameTest (`sousUnToitIlNeConsommeRien`).

### Ce qui passe : après un vrai arrêt du serveur

Avant l'arrêt, la douche est coupée (moteur de la pompe C retiré : l'eau chaude s'accumule dans le chauffe-eau), puis le courant du chauffe-eau (l'eau potable s'y accumule), puis tout le reste ; les cuves, relevées stables pendant 2 s, sont écrites dans `run-e2e/e2e-ville.txt` et le joueur rentre à son point de départ (la zone se décharge). À la relance :

```
[e2e] PASS relance : chauffe-eau relu du disque en -282, 150, 403, avant l'arret potable 184 chaude 400 douche 480
[e2e] PASS relance : cuves du chauffe-eau relues du disque : eau potable 184 mB (avant l'arret 184), eau chaude 400 mB (avant 400), etat NO_POWER, energie 0 FE
[e2e] PASS relance : cuve de la pomme de douche 480 mB (avant l'arret 480), relue par la console RCON
[e2e] PASS phare allume en tour libre : ... etat RUNNING, lit vrai ...
[e2e] PASS sous un toit : lit faux, etat STOPPED (...)
[e2e] PASS toit retire : phare rallume, etat RUNNING, lit vrai
```

Les deux cuves du chauffe-eau (eau potable 184 mB, eau chaude 400 mB, valeurs **non nulles et arrivées par les pompes**) et la cuve de la pomme (480 mB) sont relues **à l'identique** après un vrai arrêt et une vraie relance du serveur. Le phare est relu allumé, puis refait son cycle toit / sans toit après la relance.

## Film

`./gradlew runDemoVilleFilm -PwithPack` (`FilmVille`, 18:35 à 18:37) : monde intégré plat, une passe réussie, 646 images, `termine.json` sans échec, encodé en `run-demo/film-lot6-ville.mp4` (1 min 4 s, 1280 x 720, 10 images par seconde ; `ffmpeg` déjà installé sur la machine, rien de téléchargé). **Le temps du monde n'est jamais accéléré** ; une seule durée est abrégée, en mémoire, et la légende le dit à l'écran (« ABRÉGÉ (film) : 800 mB d'eau potable déjà produits » : le dessalinisateur reçoit 800 mB d'avance au lieu de les produire en 20 s). Capture des images du jeu lui-même (`FilmCapture`) : aucune autre fenêtre ne peut y figurer. Images extraites du mp4 (par `ffmpeg`) et ouvertes :

- 0:02 : le monde de jour ; de gauche à droite le puits de pierre, la pompe, le dessalinisateur, la pompe, le chauffe-eau ; au-dessus, la pompe qui monte et le tuyau coudé jusqu'à la pomme de douche. Le bandeau annonce « pas encore de moteur » et des cuves à 0.
- 0:08 : les trois moteurs (cubes violets) et leurs engrenages tournent ; la légende annonce l'abrègement ; le bandeau donne des cuves qui se remplissent (dessalinisateur potable 512 mB, chauffe-eau chaude 180 mB).
- 0:12 : près du chauffe-eau (zinc riveté, cerclages de cuivre), le tuyau montant au-dessus de lui.
- 0:16 : l'écran du chauffe-eau (clic droit) : « État : Chauffe », 8000 FE ; le bandeau donne eau potable et eau chaude en mB.
- 0:20 : la pomme de douche au bout du tuyau, vue de côté, avec le bandeau « pomme 516 mB ».
- 0:24 : le joueur (vue de face) sous la pomme, les jets en filets bleus tombent sur lui ; le bandeau compte 2/5 secondes, « Bien-être : aucun ».
- 0:28 : l'icône du Bien-être est dans le coin haut droit, « Bien-être : oui, 358 s restantes », le compteur est reparti à 1/5 (l'effet est donné, le compteur de la pomme retombe à zéro), vie 20/20.
- 0:31 : vue à la première personne, regard vers la pomme, l'icône du Bien-être en haut à droite.
- 0:35 : la nuit tombe ; le phare au sommet de sa tour, vu depuis une plateforme plus basse : deux faisceaux opposés, blanc-jaune sur fond noir, d'abord en diagonale ; le bandeau donne « RUNNING | allumé ».
- 0:38 à 0:46 : les faisceaux ont tourné et sont à plat ; un motif brun passe devant la caméra à 0:38 et 0:46 (particule du jeu, non identifiée).
- 0:50 : le même motif brun, grand, devant la caméra ; les faisceaux sont toujours là (la légende est encore « Deux faisceaux opposés »).
- 0:54 : sous le toit de pierre le phare est éteint, **les faisceaux ont disparu** ; le bandeau donne « STOPPED | éteint ».
- 0:58 : le toit est retiré, le phare est rallumé, les faisceaux sont revenus (« RUNNING | allumé »).
- 1:03 : dernière image, « Fin du lot 6 » : un faisceau en diagonale, le phare allumé.

Dans le film, le joueur est à 20/20 : le « aucun soin » n'y est pas démontré (le bandeau le montre sans le prouver) ; la preuve est l'essai de bout en bout.

## Non prouvé

- **Équilibrage réel** : 5 mB et 40 FE par tick pour le chauffe-eau, 2 mB par tick et 5 s pour la douche, +5 % de marche et +10 % de minage, 6 minutes, 32 FE par tick pour le phare : jamais vécus en partie. En particulier, l'effet réel des +5 % et +10 % n'est lu ni sur la vitesse de marche ni sur le minage dans l'essai de bout en bout (le bonus est éprouvé en JUnit et en GameTest seulement).
- **Multijoueur** : un seul client à la fois ; deux joueurs sous la même pomme, ou un joueur qui sort puis revient, ne sont éprouvés qu'en GameTest.
- **Rôle Citoyen** : le joueur de l'essai est Ingénieur (rôle du début de l'essai) sans être opérateur ; l'usage de la douche par un Citoyen n'est pas essayé.
- **FE d'un autre mod** : seule la source créative de Create Crafts & Additions a alimenté les machines ; ni New Age ni Advanced Electricity.
- **Faisceau vu depuis une autre zone chargée** : le faisceau est un rendu du client ; il n'est vu que depuis une plateforme à 16 blocs de la tour (film), jamais de très loin, d'un autre chunk éloigné, ni depuis une autre zone chargée, ni par un second client ; l'angle identique entre deux clients (formule sur le temps de jeu) n'est pas vu.
- **Coût serveur** : jamais mesuré (chauffe-eau et phare en machines électriques de base, pomme toutes les 20 ticks).
- **Déchargement réel d'un chunk** : le joueur retourne à 70 blocs avant l'arrêt, mais aucun chunk n'a été observé déchargé puis rechargé en jeu ; la preuve de persistance est celle d'un arrêt et d'une relance du serveur.
- **Eau coupée en pleine douche, en vrai** : l'essai prouve une pomme sans eau chaude (cuve vide, jamais alimentée), pas une pomme dont l'alimentation est coupée pendant que le joueur est dessous (GameTest seulement).
- **Consommation du phare en vrai** : l'état STOPPED sous un toit est vu, pas la consommation (source créative).
- **Pompes de Create** : l'essai ne cherche pas à expliquer le sens de rotation (vitesses lues -16, -16 et 16 tr/min, toutes trois dans le bon sens). Un puits à **une seule source** ne donne que 1 000 mB (la pompe la vide) : l'essai et le film utilisent un puits de quatre sources, qui se refait.
- **Rendu du Bien-être** : l'icône et le nom en jeu ne sont vus que dans le film (français) ; l'essai de bout en bout est en `en_us`.
- **Son** : aucun son de la douche ni du phare n'est vérifié (images seulement).

## Incidents de l'essai lui-même

Aucun défaut du code livré n'a été trouvé. Les échecs venaient du banc (ou, pour le dernier, d'un test), chacun corrigé avant le passage qui fait foi :

1. Une source d'eau seule ne donne que 1 000 mB à la pompe de Create : le chauffe-eau finissait vide, donc rien à retrouver après la relance. Le puits est passé à quatre sources, qui se refont par la règle vanille des deux sources voisines.
2. Le Bien-être (6 minutes) est sauvegardé avec le joueur : un second passage, lancé moins de six minutes après le précédent, voyait l'effet avant la pomme sèche. L'étape le retire (`/effect clear`) au départ et l'exige absent.
3. Un joueur qui n'est plus opérateur est expulsé « pour spam » s'il envoie une dizaine de commandes d'affilée : après un échec sous la douche, les commandes de rangement des autres étapes l'ont expulsé et `/stop` n'est jamais parti (le serveur est resté ouvert, arrêté proprement par la console RCON). `E2eClient` rend désormais l'opérateur au joueur en premier, avant toute commande de rangement.
4. L'étape du lot 6 posée **après** celle du lot 5 a allongé de plusieurs minutes l'attente avant l'arrêt du serveur : la seconde couveuse du lot 5, qui doit être coupée depuis moins de 60 s à l'arrêt, avait perdu son œuf (relance : « compteur 0, état STOPPED », 1 échec). L'étape du lot 6 passe maintenant avant celle du lot 5 au premier passage, et après celles des lots 2 à 5 à la relance.
5. Dans le film, un clic juste après la téléportation de la caméra tombait hors de portée du chauffe-eau (le clic « réussissait » côté client, le menu ne s'ouvrait jamais) : une étape de placement les sépare. Les légendes trop longues recouvraient le titre : elles tiennent désormais en 60 caractères.
6. **Un GameTest de la tâche 5 échouait** : `PhareGameTests.sousUnToitApresCoup`, « STOPPED attendu, vu RUNNING », aux deux passages de 18:40 et 18:41 (il passait aux passages de la tâche 7, et à 17:36). « Voir le ciel » est la lumière du ciel au-dessus du phare (`Level.canSeeSky`), que le moteur de lumière met à jour de façon asynchrone : un relevé tick par tick (GameTest temporaire, retiré) montre l'état RUNNING pendant deux ticks après la pose du toit, puis STOPPED. Le test attendait cinq ticks fixes ; il attend maintenant l'état (`thenWaitUntil`, jusqu'à son délai de 300 ticks) et exige les mêmes états finals. **Hypothèse non vérifiée** : le délai de la lumière dépasse cinq ticks sous la charge de la suite complète. L'asynchronisme est lu dans le code et relevé sur deux ticks ; que le moteur de lumière dépasse cinq ticks lors des échecs n'a pas été mesuré.
