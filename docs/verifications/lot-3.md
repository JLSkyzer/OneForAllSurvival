# Vérifications du lot 3 : électricité vitale

Relevés du 2026-11-07, sur la branche `lot-3`. Ce document couvre la tâche 7 (preuve de bout en bout, film). Les tâches 1 à 6 ont leurs GameTests, relancés ici.

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew runGameTestServer` (sans pack), après la barre de péremption | « All 272 required tests passed » (`run-gametest/logs/latest.log`, 19:37) |
| Après le dernier changement de code : `./gradlew runGameTestServer` (sans pack, 19:58) puis `-PwithPack` (19:59) | « All 272 required tests passed » aux deux passages |
| `./gradlew test` | 82 tests JUnit, 0 échec (somme des `build/test-results/test/*.xml`) |
| `./gradlew jar` | `build/libs/oneforallsurvival-0.1.0.jar`, 404 entrées, aucune entrée `devcontent`, `dev/`, `Film` ni `E2e` (liste de l'archive) |

La barre de péremption (demande de Killian : « dans le tooltip d'avoir une barre pour la péremption ») a été ajoutée dans cette tâche, commit `feat(food): barre de peremption dans l'infobulle` : `FoodDecay.remainingFraction` et `FoodDecay.filledCells` (6 tests JUnit, vus échouer d'abord à la compilation : symboles absents), `FoodStamp.remainingFraction` (factorise les durées avec `state`, un GameTest `laVieRestanteSuitLeTagLent`), et une ligne de 20 traits sous la ligne d'état dans `FoodClient`, texte seulement, vert si frais, or si rassis, gris foncé pour le reste, arrondi vers le haut.

## Serveur dédié et vrai client

`./gradlew runServer -PwithPack` puis `./gradlew runE2eClient -PwithPack`, puis `/stop`, nouveau `runServer` et `./gradlew runE2eRelance -PwithPack`. Client `Dev`, opérateur de `run-server` (`online-mode=false`, `server-ip=127.0.0.1`). Le client ne voit le serveur que par le réseau : inventaire, menus, état des blocs, messages. Les dates sont posées par de vraies commandes (`/give`, `/item replace`, `/summon`). Le serveur passe en difficulté facile (en paisible la faim ne baisse pas) et le monde est remis en paisible à la fin. Le client se ferme seul : fin normale par `/stop`, délai de garde de 25 min (10 min en relance) puis arrêt de la JVM par un second fil, écran de déconnexion et connexion de plus de 90 s traités comme des échecs.

### Première phase (journal client `run-e2e/logs/latest.log`, 19:45 à 19:48) : 72 lignes `PASS`, 0 `FAIL`, « bilan : TOUT PASSE »

Lignes lues, extraits :

```
[e2e] PASS pose par un clic droit reel en -39, 90, 228 : Refrigerated Cabinet   (idem Desalinator, Electric Dryer)
[e2e] PASS bouteilles dans le dessalinisateur : 3 ; il se met au travail dans la zone /coast
[e2e] PASS deux boeufs ramasses : une pile de 2, datee (288000 ticks, soit un multiple de 18000 : true), tag {oneforallsurvival_stamp:288000L}
[e2e] PASS infobulles : steak frais -> fresh barre 20/20 green ; cotelette vieillie -> stale barre 10/20 gold ; pile avariee construite -> spoiled barre 0/20 red
[e2e] PASS repas frais : faim 9 -> 17, gain 8 (attendu 8 : steak cuit = 8)
[e2e] PASS repas rassis, moitie moins : faim 10 -> 14, gain 4 (attendu 4 : steak cuit = 8)
[e2e] PASS useItem d'un steak avarie tenu 3 s sur un vrai client (a la phase 6 du balayage, donc avant lui) : jamais en train de manger, faim 14 -> 14, case apres : 1 spoiled_food
[e2e] PASS dessalinisateur sur zone /coast, courant d'une source Create : eau a traiter 1250 mB, eau potable 50 mB, sel 1, bouteilles pleines 3 ..., etat du bloc RUNNING
[e2e] PASS dans le sechoir : 1 cod et 1 salt (sel du dessalinisateur)
[e2e] PASS la marmite accepte un boeuf date comme ingredient : 2 beef (tag {oneforallsurvival_stamp:288000L}), chauffee : true
[e2e] PASS ragout de boeuf cuit avec le boeuf date : affichage 0 air, sortie 1 beef_stew
[e2e] PASS le ragout (plat de Farmer's Delight) est date dans l'inventaire : 288000, infobulle fresh
[e2e] PASS armoire alimentee par une source Create, avec des aliments : etat RUNNING ; contenu [0:cooked_beefx4@252000, 1:cooked_chickenx2@180000] ; energie 2000/2000 ; barre du steak de l'armoire 20/20 green
[e2e] PASS source retiree et reserve videe : l'etat du bloc, lu par le client, est NO_POWER
[e2e] PASS juste apres la coupure, meme contenu, memes dates
[e2e] PASS 5 s sans courant ... : aucun aliment perdu, aucune date changee
[e2e] PASS sechoir : sortie 1 salted_fish ; entree 0 air, sel 0 air
[e2e] PASS ration salee dans l'inventaire : 1 salted_fish, tag null, infobulle null
```

Les étapes de l'établi et de la réparation du lot 2 précèdent (même passage), elles passent aussi.

### Après un vrai arrêt et une vraie relance du serveur (19:49, 21 `PASS`, 0 `FAIL`)

L'armoire, ses piles et leurs dates sont relues du disque (`avant l'arret [...] ; apres [...]` identiques, armoire toujours `NO_POWER`), le ragoût garde sa date 288000, la ration salée est là sans date, le temps du monde passe de 295784 à 297070 (il ne court que serveur allumé), la source remise fait repartir l'armoire (`RUNNING`) sans toucher aux piles. Les trois machines du lot 2 et la zone `/coast` sont relues aussi. Les machines et la zone d'essai sont retirées à la fin.

### Prouvé en plus, signalé par les relectures

- **Le chemin réel d'un avarié** : `MultiPlayerGameMode.useItem`, touche « utiliser » tenue 3 s sur un vrai client, jamais en train de manger, faim inchangée, la pile n'est pas consommée et devient un déchet. Une sonde (un cabillaud sans date, vu daté au balayage d'une seconde du serveur) cale la phase : l'avarié est arrivé et utilisé à la phase 6 sur 20, donc avant le balayage suivant, ce n'est pas le balayage qui l'a remplacé. Le serveur remet bien l'avarié en main au retour de `useItem` (le déchet n'arrive qu'au balayage qui suit, moins d'une seconde plus tard) : le refus se mesure à « jamais en train de manger, faim inchangée ».
- **Un joueur réellement animé par le monde** : le vieillissement et le remplacement par le déchet dans l'inventaire se déroulent en vrai (balayage serveur, ligne ci-dessus), et le repas rassis rend moitié moins (gain 4 contre 8).
- **Le dessalinisateur et le séchoir sur serveur dédié** démarrent, travaillent, ouvrent leurs menus et sauvegardent sans plantage de classes client : les deux phases ont tourné, 0 ligne `ERROR` ou `FATAL` aux deux journaux serveur (`run-server/logs/latest.log` copiés), le dessalinisateur a produit eau potable, sel et bouteilles pleines, le séchoir une ration salée.
- **Une marmite de Farmer's Delight accepte un ingrédient daté**, le plat cuit est daté à son tour.

## Film

`./gradlew runDemoVivresFilm -PwithPack` : monde intégré, une passe, 906 images, `termine.json` sans échec, encodé en `run-demo/film-lot3-vivres.mp4` (1 min 30 s, 1280 x 720). Images extraites et ouvertes une à une : steak frais dans l'armoire et dans l'inventaire (infobulle en gros plan, barre pleine verte), steak dehors rassis (barre or, à moitié), déchet dehors pendant que le jumeau de l'armoire est encore frais, courant coupé (écran « Sans courant », le steak reste), le jumeau qui passe rassis puis déchet, dessalinisateur en zone côtière (eau, cycle, sel, bouteille pleine), séchoir (ration salée sans ligne d'état, ration séchée « Frais » avec barre).

Deux réglages de développement, annoncés dans les légendes : le **temps du monde est accéléré** (2 h de jeu par seconde réelle, par saut de l'horloge du monde à chaque tick) et le **séchage est ramené de 60 s à 5 s** en mémoire. Les durées d'aliment ne sont pas raccourcies comme la consigne le suggérait : elles se comptent par quarts d'heure (18000 ticks), donc des durées plus courtes que le quart d'heure ne marcheraient pas ; le saut de l'horloge fait aussi vivre le rattrapage de l'armoire. Le compteur de cycles du dessalinisateur est avancé à 3 pour voir le sel sans attendre 15 s (annoncé).

## Non prouvé

- **Équilibrage réel** : 6 h de frais, 6 h de rassis, armoire, rendement du dessalinisateur et du séchoir n'ont jamais été vécus en temps réel. Les durées du séchage et du cycle sont celles de la config, jamais jouées en entier hors des 1200 ticks du séchoir de l'E2E.
- **Multijoueur** : un seul client à la fois.
- **Citoyen sur serveur dédié** : le client d'essai est Ingénieur.
- **FE d'un autre mod** dans les machines (seule la source Create Crafts & Additions est essayée).
- **Machines de Create entre un aliment et un coffre** (entonnoir, tapis, déployeur) : non essayées, la péremption touche l'inventaire du joueur, les menus, les objets au sol et l'armoire.
- **Aliment jamais observé** : un aliment produit et stocké par des machines sans jamais passer par un joueur, un menu ou le sol ne reçoit pas de date.
- **La pompe Create en marche** : seul le raccord du tuyau est testé, aucune pompe n'a tourné contre le dessalinisateur.
- **Le retour à la vitesse normale après la coupure** n'est vu qu'au film (jumeau qui passe rassis puis déchet en temps accéléré) et aux GameTests ; l'E2E ne mesure que « aucune perte immédiate » (5 s sans courant).
- **En panne, le dessalinisateur ne fait plus rien** (ni eau gratuite, ni bouteilles) : décision de la tâche 4, non vue au client.
- **Les cas limites déjà notés aux revues** (crédit de froid global, `Shulker` avarié jusqu'au contact, coffre de mod à menu propre) restent tels que listés dans le journal du lot.

## Incidents de l'essai lui-même

Aucun défaut du code livré n'a été trouvé. Les échecs venaient du banc : (1) deux steaks de dates différentes dans la barre d'action fusionnent en une pile (fonction voulue : moyenne des dates), le test de rassis utilise donc une côtelette ; (2) un essai interrompu laissait une zone `/coast` et des machines : l'essai les retire avant de commencer ; (3) un écran de chat s'est ouvert sur un client (touche étrangère pendant l'essai, supposé, non établi) : l'essai ferme désormais un écran de chat ; (4) au film, un boeuf cru donné au départ avariait pendant le temps accéléré : il est donné à la scène du séchoir.
