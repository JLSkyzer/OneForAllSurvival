# Vérifications du lot 4 : gestion du réseau

Relevés du 2026-11-08, sur la branche `lot-4`. Ce document couvre la tâche 7 (preuve de bout en bout, film). Les tâches 1 à 6 ont leurs GameTests et tests JUnit, relancés ici.

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew test --rerun` | 117 tests JUnit, 0 échec (somme des `build/test-results/test/*.xml`) |
| `./gradlew runGameTestServer` (sans pack), 06:08 | « All 328 required tests passed » (`run-gametest/logs/latest.log`) |
| `./gradlew runGameTestServer -PwithPack`, 06:13 | « All 328 required tests passed » |

## Serveur dédié et vrai client

`./gradlew runServer -PwithPack` puis `./gradlew runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack` et `./gradlew runE2eRelance -PwithPack` (qui finit par `/stop`). Client `Dev`, opérateur de `run-server`. Le client ne voit le serveur que par le réseau : écrans, menus, inventaire, état des blocs, messages. Le même passage rejoue aussi les étapes des lots 1 à 3 (établi, réparation, vivres), qui passent.

L'étape du réseau est `src/devcontent/java/dev/oneforallsurvival/dev/E2eReseau.java`. Son aire est une dalle de pierre en hauteur (y = 150), à 64 blocs du joueur, hors de la protection du point d'apparition. Le montage (moteur créatif de Create, alternateur de Crafts & Additions, tableau, compteur, disjoncteur, machines d'essai) est posé par `/setblock` avec son orientation ; tous les gestes du joueur sont de vrais clics.

### Première phase (journal client archivé `run-e2e/logs/2026-11-08-1.log.gz`, 05:39 à 05:45) : 93 lignes `PASS`, 0 `FAIL`, « bilan : TOUT PASSE »

Lignes de l'étape du réseau, telles que le journal les écrit (les messages du serveur sont en anglais : le client d'essai est en `en_us`) :

```
[e2e] PASS alternateur a 256 tr/min, l'ecran du tableau : etat RUNNING | rang 1 devant FED 10 FE | rang 2 gauche FED 10 FE | rang 3 droite FED 10 FE | rang 4 dessus FED 90 FE
[e2e] PASS alternateur a 32 tr/min, l'ecran du tableau : etat RUNNING | rang 1 devant PARTIAL 45 FE | rang 2 gauche CUT 0 FE | rang 3 droite CUT 0 FE | rang 4 dessus CUT 0 FE (ordre d'usine : devant, gauche, droite, dessus)
[e2e] PASS 3 clics reels sur « monter » : la sortie du dessus passe du rang 4 au rang 1 ; rangs (devant, gauche, droite, dessus) 1230
[e2e] PASS apres le reglage, l'ecran : etat RUNNING | rang 1 dessus PARTIAL 45 FE | rang 2 devant CUT 0 FE | rang 3 gauche CUT 0 FE | rang 4 droite CUT 0 FE
[e2e] PASS ecran d'inscription de l'abonne ouvert chez l'ingenieur (paquet du serveur)
[e2e] PASS nom tape et envoye : le serveur repond Subscriber registered: Ferme 2961
[e2e] PASS testeur sur le compteur : un releve dans l'inventaire ; Reading for Ferme 2961: 1895 FE used since the previous reading.
[e2e] PASS infobulle du releve : {subscriber=Ferme 2961, index=1895, consumption=1895, day=12, date=08/11/2026 05:45, no_anomaly=} (premier releve : consommation = index)
[e2e] PASS role citoyen, et plus operateur cote client (hasPermissions(2) = false)
[e2e] PASS Citoyen non operateur : releve refuse (« Only an Engineer can do this. »), aucun releve donne
[e2e] PASS Citoyen : aucun ecran d'inscription, il lit « Meter of Ferme 2961 »
[e2e] PASS Citoyen : scelle refuse, disjoncteur non scelle, les 2 scelles gardes
[e2e] PASS op par RCON, /role set ingenieur
[e2e] PASS bascule par un vrai clic : disjoncteur coupe (Breaker cut)
[e2e] PASS scelle pose : disjoncteur coupe et scelle, un scelle consomme (2 -> 1)
[e2e] PASS clic main vide sur le disjoncteur scelle : refuse (« Breaker sealed: only an engineer can remove the seal (sneak, empty hand). »), il reste coupe
```

Ce que chaque ligne mesure : les états et les FE viennent du menu du tableau ouvert chez le client (nombres synchronisés par le serveur) ; les rangs changent par de vrais clics de souris sur les boutons de l'écran ; le nom est tapé caractère par caractère dans l'écran d'inscription, puis Entrée ; le relevé est lu dans les lignes de l'infobulle de l'objet ; les refus sont la clé du message reçu et l'absence d'effet (aucun relevé de plus, aucun écran, scellé gardé) ; l'état du disjoncteur est celui du bloc vu par le client. Trois lignes ne mesurent rien : les deux « écran ouvert » (la première montre l'écran avant que ses nombres n'arrivent) et « a retrouver apres relance ».

**Le Citoyen** : un opérateur garde la main d'ingénieur (`RoleGate`), et `Dev` est opérateur. Pour un vrai refus, le joueur passe Citoyen (`/role set`) puis perd l'opérateur par la console RCON du serveur (`deop Dev`, journal : `rcon : deop Dev -> Made Dev no longer a server operator`), et le retrouve de même après les trois refus. RCON est activé dans `run-server/server.properties` (local, mot de passe tiré au hasard, jamais dans le dépôt) : `runServer` ne lit pas son entrée standard, c'est le seul chemin d'administration hors jeu.

### Après un vrai arrêt et une vraie relance du serveur (journal `run-e2e/logs/latest.log`, 05:48 à 05:50) : 41 `PASS`, 0 `FAIL`, « bilan : TOUT PASSE »

```
[e2e] PASS relance : disjoncteur Block{oneforallsurvival:breaker}[closed=false,facing=north,sealed=true]
[e2e] PASS relance : bascule refusee, le scelle tient (« Breaker sealed: only an engineer can remove the seal (sneak, empty hand). »)
[e2e] PASS relance : rangs (devant, gauche, droite, dessus) 1230, avant l'arret 1230 ; etat RUNNING | rang 1 dessus FED 10 FE | rang 2 devant FED 10 FE | rang 3 gauche FED 10 FE | rang 4 droite NONE 0 FE
[e2e] PASS relance : infobulle du second releve {subscriber=Ferme 2961, index=13330, consumption=11435, day=12, date=08/11/2026 05:50, no_anomaly=} ; index du premier 1895 (consommation attendue = index - 1895 : le registre de l'abonne a garde le releve precedent)
[e2e] PASS eclair en 94, 150, 220 : parafoudre 100.0 -> 90.0 (-10 attendu, un coup) ; machine 1 99.86469907407604 -> 99.8577546296317, tableau 99.86388888889087 -> 99.857175925928 (proteges) ; temoin 100.0 -> 75.0, etat du bloc NO_POWER (-25 ou fusible : l'eclair etait reel)
[e2e] PASS avant l'orage : station STABLE / RUNNING, lampe de redstone allumee false, alarme alimentee en redstone false et allumee false, pluie client false orage false
[e2e] PASS orage : station STORM_SOON / RUNNING, lampe de redstone allumee true, alarme alimentee en redstone true et allumee true, pluie client false orage false, 13 ticks apres /weather thunder (vus par le client)
```

- **Rangs, index, registre et scellé relus du disque** : rangs identiques, disjoncteur toujours coupé et scellé (et la bascule refusée), second relevé sans anomalie dont la consommation est exactement `index - index du premier relevé` (11 435 = 13 330 - 1 895) : le registre de l'abonné a gardé le relevé d'avant l'arrêt. La sortie de droite passe « non branchée » : son disjoncteur coupé n'accepte plus rien.
- **Un vrai éclair** (`/summon minecraft:lightning_bolt`, non visuel) à 10 blocs des machines : le parafoudre perd 10 points, une seule fois ; la machine 1 et le tableau, protégés, ne perdent que leur usure ordinaire (moins de 0,01 point) ; une machine témoin à 15 blocs de l'éclair et à 31 blocs du parafoudre (hors de sa portée de 24) perd 25 points. Les états sont lus par `/data get block … condition`.
- **La station météo** alimentée par l'alternateur : `/weather clear 1000000` donne une tendance stable, rien d'allumé ; `/weather thunder` la fait passer à l'orage en 13 ticks vus du client, avec une lampe de redstone et une **lampe d'alarme d'Advanced Electricity** (`light_bulb_addon_create_crafts_and__addions:alarm_lamp`, alimentée par une source Create) allumées par son signal.

Le serveur a écrit 0 ligne `ERROR` ou `FATAL` dans ses deux journaux (`run-server/logs/latest.log` relevés après chaque phase).

## Film

`./gradlew runDemoReseauFilm -PwithPack` (`FilmReseau`) : monde intégré, une passe réussie, 805 images, `termine.json` sans échec, encodé en `run-demo/film-lot4-reseau.mp4` (1 min 20 s, 1280 x 720, 10 images par seconde). **Le temps n'est jamais accéléré.** Images extraites du mp4 et ouvertes (`run-demo/verif-lot4/`) :

- 0:14 et 0:18 : l'écran du tableau en gros plan, source réduite, « 1 Devant Partielle 45 FE, 2 à 4 Coupée », puis après trois clics sur « monter » : « 1 Dessus Alimentée, 2 Devant Partielle, 3 Gauche Coupée, 4 Droite Coupée ».
- 0:25 : l'écran d'inscription, « Ferme Nord » tapé. 0:33 : l'infobulle du relevé (abonné, index 6 528 FE, consommation, « Jour 1 », date réelle, « Aucune anomalie »).
- 0:41 et 0:44 : « Disjoncteur coupé », puis « Disjoncteur scellé. », un scellé consommé (2 vers 1). Le bloc lui-même est en partie caché par une armoire, et le plomb du scellé ne se distingue pas à cette distance : la pose se lit au message et au bandeau, pas sur le modèle.
- 0:54 et 0:57 : l'éclair sans parafoudre, l'armoire à 75 % au testeur. 1:04 et 1:10 : l'éclair avec parafoudre, l'armoire intacte, le parafoudre à 90 % au testeur.
- 1:14 et 1:20 : la station météo (soleil), lampe et alarme éteintes, puis sous l'orage : face à éclair, lampe de redstone allumée, alarme d'Advanced Electricity rouge allumée.

Deux réglages de développement, annoncés dans les légendes : la source réduite à 32 tr/min ne suffit pas à affamer quatre armoires à réserve pleine (16 FE par tick), leurs **réserves sont vidées** (deux fois, pour que le réglage du rang se voie pendant la pénurie). Erreur de légende : la scène de la foudre sans parafoudre dit « une armoire alimentée », elle ne l'était pas (le testeur affiche 0 / 2000 FE) ; la légende est corrigée dans le code, pas dans ce film.

## Non prouvé

- **Équilibrage réel** : débits (4 096 FE par tick), dégâts et risque de la foudre, portée du parafoudre, horizon de la station n'ont jamais été vécus en partie.
- **Une pénurie durable** : à 32 tr/min (le plus lent que tolère l'alternateur, exigence de vitesse moyenne de Create) il produit 45 FE par tick, plus que ce que consomment quatre machines du mod à réserve pleine. La coupure des derniers rangs n'est vue qu'en vidant les réserves, le temps de les remplir (quelques secondes).
- **Multijoueur** : un seul client à la fois.
- **Citoyen sur serveur dédié, au-delà des trois refus** : relevé, écran d'inscription et scellé refusés à un Citoyen non opérateur ; les autres gestes (retirer un scellé, casser un disjoncteur scellé, réparer) ne sont vus qu'en GameTests.
- **New Age et Advanced Electricity dans le délesteur** : seul Crafts & Additions (alternateur, source créative) a fait passer du courant.
- **Les alarmes d'Advanced Electricity sur le signal** : prouvé pour la lampe d'alarme (redstone reçu et lampe allumée, E2E et film) ; les autres blocs d'alarme du mod (balise d'avertissement, etc.) non essayés.
- **La foudre naturelle d'un vrai orage** : seuls des éclairs invoqués (`/summon`, ou créés par le film) ; aucun éclair tombé de lui-même.
- **La station sur un vrai cycle météo** : seules les commandes `/weather` ; l'annonce d'environ 5 minutes avant un orage naturel n'a pas été attendue.

## Limites connues et décisions déjà prises dans ce lot

- Après `/weather clear N`, Minecraft fait commencer pluie et orage à la fin du ciel forcé : la station annonce alors l'orage tout de suite, à juste titre. Avec la météo figée (`doWeatherCycle` à faux), seul l'état présent compte.
- L'horizon d'une station est tiré à sa pose et gardé : un changement de config n'agit pas sur les stations déjà posées.
- Rare fausse annonce possible : un orage armé par temps sec dont la pluie arrive tard dans l'horizon est annoncé alors qu'il a pu finir.
- Les rangs du délesteur se perdent quand on le casse (seule l'usure voyage avec l'objet). Le testeur affiche « 0 / 0 FE » pour le délesteur, qui n'a pas de réserve.
- Avec deux générateurs qui poussent dans le même tick, l'affichage « partielle » ou « alimentée » d'une sortie peut dépendre de l'ordre des poussées (la répartition, elle, reste juste).
- Un disjoncteur reposé revient enclenché et sans scellé. Un compteur ou un disjoncteur cassé à la main sans pioche ne rend rien : l'abonné est perdu. Une explosion peut détruire l'objet compteur (l'anomalie reste visible au relevé suivant).
- Deux compteurs au même nom d'abonné sont facturés en double et signalés « compteur différent » à chaque relevé. Renommer un compteur facture tout son historique au nouvel abonné ; un compteur qui a compté avant d'être inscrit facture tout son index au premier relevé (vu dans l'E2E : consommation = index).
- Un scellé peut être posé sans abonné ; sa casse n'est alors notée nulle part.
- Pick-block créatif sur un compteur : copie son numéro et son index.
- Visuels : l'objet station météo montre le cadran éteint ; le plomb du scellé est petit de loin (voir le film) ; la face « sans courant » du délesteur n'est jamais visible en jeu.

## Incidents de l'essai lui-même

Aucun défaut du code livré n'a été trouvé. Les échecs venaient du banc, chacun corrigé avant le passage qui fait foi :

1. Moteur à 8 tr/min : l'alternateur ne produit rien sous la vitesse moyenne de Create (30 tr/min, lu dans `AlternatorBlock.getMinimumRequiredSpeedLevel`) ; passé à 32 tr/min.
2. À 32 tr/min, la réserve interne pleine de l'alternateur (5 000 FE) et les réserves des machines couvraient la demande : l'alternateur est reposé neuf et les réserves vidées par `/data merge`.
3. Le steak « frais » de l'étape des vivres disparaissait dans une pile plus vieille : des aliments lâchés au sol par l'armoire d'un essai précédent étaient ramassés et fusionnaient (fonction voulue). L'étape détruit désormais les objets au sol et retire ces aliments de l'inventaire avant de commencer.
4. L'aire du réseau, posée près du joueur, effaçait par son `/fill` les machines de l'étape de réparation quand un essai précédent avait laissé le joueur au même endroit ; puis le `/clear` de l'étape effaçait le poste chargé que la relance relit. L'aire est maintenant à 64 blocs du joueur, qui y revient ensuite, et l'inventaire n'est plus vidé.
5. Un démarrage du serveur a échoué sur la `ConcurrentModificationException` intermittente connue de Create : relancé.

Cela fait 9 lancements du client d'essai (6 premières phases, 3 relances ; le dernier couple est celui qui fait foi) et 2 du film (le premier arrêté à la scène du rang, la pénurie s'étant résorbée avant les clics).
