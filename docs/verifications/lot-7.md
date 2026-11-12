# Vérifications du lot 7 : exploration

Relevés du 2026-11-12 (nuit du 11 au 12), sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 7 (preuve de bout en bout, film). Les tâches 1 à 6 ont leurs GameTests et JUnit, relancés ici.

**État : le lot est prouvé dans les limites de la section « Non prouvé ».** Caches, détecteur, lampe frontale, récepteur de signaux et mécanisme ancien passent un essai de bout en bout sur serveur dédié avec le pack complet, un joueur sans droits, et sa relance, plus un film. L'essai n'a trouvé aucun défaut du code livré : les échecs rencontrés venaient du banc (section « Incidents »).

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew --offline test runGameTestServer` (sans pack, 01:38 à 01:39) | « All 508 required tests passed » (`run-gametest/logs/latest.log`) ; JUnit : 311 tests, 0 échec, 0 erreur (somme des `build/test-results/test/*.xml`) |
| `./gradlew --offline runGameTestServer -PwithPack` (01:40 à 01:42) | « All 508 required tests passed » |

## Serveur dédié et vrai client

`runServer -PwithPack` puis `runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack` et `runE2eRelance -PwithPack`. Client `Dev` avec le pack complet. L'étape est `src/devcontent/java/dev/oneforallsurvival/dev/E2eExploration.java`, branchée dans `E2eClient` après le lot 6 et **avant** le lot 5 (le lot 5 reste le dernier avant l'arrêt). Aire : une dalle de pierre à 80 blocs à l'est et 60 au nord du point de départ, à y = 150 ; une grotte close de pierre (7 x 4 x 7, rien qui éclaire) y est creusée.

Le joueur est **non opérateur** : `deop` par la console RCON, `hasPermissions(2)` lu faux côté client, survie. Tout ce qui change le monde passe par la console RCON (le joueur sans droits ne peut pas envoyer de commandes) ; ses gestes sont de vrais clics, de vrais coups et de vrais déplacements d'inventaire. Il redevient opérateur à la fin de l'étape (les étapes suivantes envoient des commandes) et, au plus tard, avant `/stop`.

**Passage complet** (lots 1 à 7, puis la relance, lancés par Gradle) :

- Premier passage : connexion à 01:12:40, « bilan : TOUT PASSE » à 01:20:37, soit 7 min 57 s ; 191 lignes `PASS`, 0 `FAIL`. L'étape du lot 7 compte 35 `PASS` (environ 93 s, de 01:16 à 01:18) et un `PASS` pour la lampe portée à l'arrêt.
- Relance (nouveau `runServer`) : connexion à 01:24:30, « bilan : TOUT PASSE » à 01:25:17, soit 47 s ; 62 `PASS`, 0 `FAIL`. Lot 7 : 1 `PASS` de lampe et 6 de relecture.
- « [souris] rendue » est lu dans les deux journaux (20 fois au premier passage, 1 à la relance) ; aucun client ni serveur n'est resté ouvert (le serveur s'arrête par `/stop`).
- Ces deux passages ont suivi plusieurs passages d'essai (section « Incidents »).

### Ce qui passe : le détecteur et la cache

```
[e2e] PASS detecteur recharge par un vrai clic droit sur une source d'un autre mod : 24000 FE (lue sur la pile du client)
[e2e] PASS lampe rechargee par un vrai clic droit sur la meme source : 36000 FE
[e2e] PASS a 40,0 blocs (portee 24) : aucun bip en 6 s
[e2e] PASS bips a 20,0 blocs : intervalles releves dans le journal du client [34 34 34 34 34 34] ticks, ... (attendu 34), hauteur 0,75
[e2e] PASS bips a 12,0 blocs : ... [21 21 21 21 21 21] ticks ... (attendu 21), hauteur 1,25
[e2e] PASS bips a 6,0 blocs : ... [12 12 12 12 12 12] ticks ... (attendu 12), hauteur 1,62
[e2e] PASS bips a 1,7 blocs : ... [5 5 5 5 5 5] ticks ... (attendu 5), hauteur 1,90
[e2e] PASS de loin a pres, intervalle moyen [34,0, 21,0, 12,0, 5,0] ticks (strictement decroissant), hauteur [0,75, 1,25, 1,62, 1,90] (strictement croissante)
[e2e] PASS cache cassee a la main en survie, en 3,3 s
[e2e] PASS butin unique : ... apres 4 s encore, aucun objet au sol, /cache list ne la montre plus
[e2e] PASS detecteur en main a 0,85 bloc de l'ancienne cache pendant 5 s : aucun bip
```

Ces lignes sont celles d'un passage d'essai de l'étape seule (même code) ; le butin tiré (au hasard dans la table) et la charge restante varient d'un passage à l'autre, les intervalles ont été identiques d'un passage à l'autre. Les bips sont relevés par l'événement de son du client (`PlaySoundEvent`, `block.note_block.bit`), à leur arrivée réelle ; l'intervalle est la moyenne du deuxième au dernier bip (six intervalles), comparée à `DetectorRules.beepInterval` pour la distance lue (tolérance : 3 ticks ou 15 %). Ce que cela prouve : la cadence et la hauteur du bip suivent la distance, en vrai, sur un serveur dédié. Ce que cela ne prouve pas : le son tel qu'on l'entend (l'essai lit des événements, pas de l'audio).

### Ce qui passe : la lampe dans la grotte close

```
[e2e] PASS dans la grotte close, lumiere de bloc a la tete (348, 151, 55) : 0 (avant la lampe)
[e2e] PASS lampe sur la tete : lumiere de bloc a la tete 14 (de 0 sans la lampe), bloc Block{minecraft:light}[level=14,waterlogged=false] en 348, 151, 55, serveur : Test passed
[e2e] PASS lampe retiree (deplacement rapide de l'armure) : lumiere a la tete 0, serveur : Test failed
[e2e] PASS 0 bloc minecraft:light dans le cube de rayon 3 autour de 348, 151, 55 (343 positions lues cote client)
```

La lumière de bloc est lue à la tête du joueur par `Level.getBrightness(LightLayer.BLOCK, ...)` côté client : 0, puis 14 lampe sur la tête (le bloc `minecraft:light` est lu côté client et confirmé côté serveur par `execute if block`), puis 0 une fois la lampe retirée (« Test failed » est ici la bonne réponse : plus de bloc de lumière).

**Lampe portée à l'arrêt du serveur.** Juste avant `/stop`, le joueur remet la lampe (déplacement rapide vers la tête) et l'essai attend la lumière à sa tête (14, bloc `minecraft:light`) avant d'écrire sa position (215, 151, 104 au passage final). À la relance, la lampe, toujours portée, est retirée en premier (avant l'étape du lot 5) puis on compte les blocs `minecraft:light` dans le cube de rayon 3 autour de ce point : **0** (343 positions lues côté client). L'étape relit aussi le dernier point de la lampe dans la grotte (rayon 3, et cube de rayon 4 autour du centre de la grotte) : 0. Limite : si l'arrêt n'avait pas nettoyé, le balayage au chargement du chunk l'aurait fait avant la connexion du joueur ; l'essai prouve donc « aucun orphelin après une relance », pas « le nettoyage a eu lieu à l'arrêt plutôt qu'au chargement ».

### Ce qui passe : le récepteur de signaux

```
[e2e] PASS recepteur 1 (courant, frequence 1) et 3 (courant, frequence 7) a l'ecoute, recepteur 2 coupe (courant 0, 0 FE)
[e2e] PASS /signal send 1 : "Message received by 1 receiver(s)" (alimente et sur 1 : un ; coupe et mauvaise frequence : rien)
[e2e] PASS voyant non lu : recepteur 1 oui, recepteurs 2 et 3 non
[e2e] PASS ecran du recepteur : archive d'un message "Essai de reception depuis la base" sur la frequence 1, ecoute true, reserve 2000 FE
[e2e] PASS /signal send 7 : "Message received by 1 receiver(s)" (le troisieme), /signal send 2 : "Message received by 0 receiver(s)" (zero, sans erreur)
```

Trois récepteurs : le premier alimenté (source créative de Create Crafts & Additions contre lui) sur la fréquence 1, le deuxième coupé (aucune source), le troisième alimenté sur la fréquence 7. `/signal send 1` (console RCON, rayon 5 000) est reçu par **un seul** récepteur, et la commande le dit ; le voyant « non lu » du seul récepteur touché s'allume (propriété du bloc lue côté client). Un vrai clic droit ouvre l'écran (`ReceiverScreen`) : archive d'un message, texte identique à celui envoyé, voyant éteint ensuite. `/signal send 7` atteint le troisième, `/signal send 2` personne (« 0 », sans erreur).

### Ce qui passe : le mécanisme, par un vrai câble d'un autre mod

```
[e2e] PASS connecteur sur la source ... : Block{createaddition:connector}[facing=west,mode=pull,...]
[e2e] PASS connecteur contre le mecanisme ... : Block{createaddition:connector}[facing=east,mode=push,...]
[e2e] PASS sans cable, apres 4 s : progression 0, mecanisme inactif, porte fermee (la source creative n'est pas contre lui)
[e2e] PASS mecanisme actif, alimente par un cable de Create Crafts & Additions : progression maximale vue 100 ticks (duree 100), Mechanism at ...: threshold 20 FE per tick, duration 5 s, progress 5 s, active
[e2e] PASS porte de fer ouverte (open=true) par le signal du mecanisme actif, sans levier ni bouton
[e2e] PASS 6 s a miner le mecanisme en survie avec une pioche de netherite (non operateur : true) : il est toujours la, Block{oneforallsurvival:ancient_mechanism}[active=true,stage=3]
```

Câble réel : deux connecteurs `createaddition:connector` posés par de vrais clics droits (l'un contre une source créative, l'autre contre le mécanisme ; les modes `pull` et `push` sont posés par le mod), un fil de cuivre tiré d'un connecteur à l'autre avec la bobine `createaddition:copper_spool` (deux clics droits). Réglage par `/mechanism set` : 20 FE par tick pendant 5 s. **Sans câble, 4 s : progression 0 et porte fermée** ; une fois le fil tiré, la progression est lue par la console jusqu'à 100 ticks, le mécanisme s'active, la porte de fer s'ouvre par son signal. Le joueur, sans droits, frappe le mécanisme 6 s avec une pioche de netherite : il reste. (Explosion, Wither, dragon et piston sont des GameTests de la tâche 5, non rejoués en vrai.)

### Ce qui passe : après un vrai arrêt du serveur

```
[e2e] PASS relance : lampe retiree, aucun bloc minecraft:light dans un rayon de 3 autour de 215, 151, 104 (...)
[e2e] PASS relance : mecanisme toujours actif (Active=1b, etat actif cote client), porte de fer ouverte, Mechanism at ...: threshold 20 FE per tick, duration 5 s, progress 5 s, active
[e2e] PASS relance : archive du recepteur relue du disque : "Essai de reception depuis la base" (frequence 1, ..., console : ... [{Frequency: 1, Time: ..., Text: "Essai de reception depuis la base"}])
[e2e] PASS relance : archive du recepteur sur la frequence 7 relue : ... [{Frequency: 7, ..., Text: "Second essai sur la frequence sept"}]
[e2e] PASS relance : 0 bloc minecraft:light dans un rayon de 3 autour du dernier point de la lampe dans la grotte ...
```

Avant l'arrêt le joueur retourne à son point de départ (la zone se décharge). À la relance : le mécanisme est relu **toujours actif** (`Active=1b`, état `active` côté client, porte ouverte), les archives des deux récepteurs sont relues à l'identique (console et écran), aucun bloc de lumière orphelin.

## Film

`./gradlew runDemoExplorationFilm -PwithPack` (`FilmExploration`, 01:36 à 01:37) : monde intégré plat, une passe réussie, 841 images, `termine.json` sans échec, encodé en **`run-demo/film-lot7-exploration.mp4`** (1 min 24 s, 1280 x 720, 10 images par seconde ; `ffmpeg` déjà installé sur la machine, rien de téléchargé). Capture des images du jeu lui-même (`FilmCapture`). **Le temps du monde n'est jamais accéléré, aucune durée n'est abrégée** (le détecteur et la lampe sont donnés chargés : leur recharge est dans l'essai de bout en bout). Images rouvertes une à une ; les horodatages sont ceux de l'incrustation, approximatifs :

- 0:08 à 0:20 : la recherche d'une cache au détecteur, vue à la première personne : « À 8 blocs : encore plus vite », la caisse rouillée au loin ; le bandeau annonce chaque bip (« BIP », intervalle en ticks, hauteur, nombre de bips reçus) puisque le son ne se filme pas.
- 0:20 à 0:25 : la cache est cassée à la main, le butin tombe autour du joueur et est ramassé ; l'index passe à 0 cache et le bandeau n'annonce plus de bip.
- 0:29 : la grotte close de nuit, **sans lampe** : lumière à la tête 0/15, tout est sombre.
- 0:31 à 0:39 : **lampe sur la tête** : lumière 14/15, la grotte est éclairée ; vue de face puis à la première personne ; la batterie baisse (35 960, 35 640 FE).
- 0:44 : lampe retirée : le noir est revenu, 0/15.
- 0:49 : un récepteur alimenté (le cube violet est la source) ; le chat montre « Message reçu par 1 récepteur(s) » et le bandeau « voyant non lu allumé ».
- 0:54 : l'écran du récepteur (gros plan) : « En écoute », fréquence 1, le message de l'équipe archivé.
- 0:58 à 1:12 : le mécanisme et sa porte de fer, la source à 6 blocs : sans câble, rien (progression 0) ; deux connecteurs et le fil de cuivre ; la progression monte (6,5 / 8 s, étage 2, veines qui s'allument).
- 1:19 : actif (8,0 / 8 s, étage 3), porte de fer ouverte ; 1:23 : le joueur le frappe, il reste (le film a été rejoué après une première vue où la caméra regardait dans le mauvais sens).

La lumière de la lampe à 4 blocs des murs reste modeste (niveau 14 moins la distance) ; vue à la luminosité par défaut.

## Visuels des mécanismes (reprise de la tâche 6)

`DemoVisuels.reglerLesEtages` posait l'étage 3 à 3 x 33 400 sur une durée de 100 000 : plus que la durée, donc un mécanisme complet, c'est-à-dire **actif**, pas à l'étage 3. Corrigé : étages 1 et 2 à 40 % et 70 % de la durée (strictement en dessous). L'étage 3 n'existe à l'écran qu'à la progression complète, instant où le mécanisme devient actif : `DemoVisuels` force donc le bloc à l'étage 3, inactif, à chaque tic, pour pouvoir le regarder (contrôle de texture, pas un état tenable en jeu). `runDemoVisuels` relancé (180 images, « sans erreur », « [souris] rendue » lu) ; `mecanismes_les_cinq` et `mecanisme_etage_3` rouverts : les étages 0, 1, 2 et 3 sont distincts (veines de plus en plus nombreuses) et l'état actif (cœur clair, halo orange, lampe de redstone allumée à côté) se distingue nettement de l'étage 3.

## Non prouvé

- **Équilibrage réel** : batterie du détecteur (24 000 FE, environ 10 min), de la lampe (36 000 FE, environ 15 min), 4 FE par tick du récepteur, seuil et durée du mécanisme : jamais vécus en partie, seulement en essai court.
- **Multijoueur** : un seul client à la fois. Ni le bip entendu par un autre joueur, ni deux porteurs de lampe qui partagent un bloc de lumière ne sont vus en vrai (GameTests de la tâche 3 seulement).
- **Coût serveur des lampes** : jamais mesuré (un bloc de lumière posé et retiré par seconde et par porteur ; aucun chronométrage de tick avec plusieurs lampes).
- **Lumière de la lampe vue par un second client** : non vue ; seul le client de l'essai a lu le bloc `minecraft:light`.
- **FE d'un autre mod** : seule Create Crafts & Additions a servi (source créative pour la recharge et l'alimentation, connecteurs et fil de cuivre pour le mécanisme) ; ni New Age ni Advanced Electricity.
- **Arrêt brutal du serveur** : le plantage réel n'est pas essayé ; le `/stop` ordinaire l'est, avec la lampe portée. Le déchargement et le rechargement réels d'un chunk en cours de partie n'ont pas été observés.
- **Son** : aucun son n'est vérifié à l'oreille (le client lit des événements de son).
- **Explosion, Wither, dragon, piston contre le mécanisme** : GameTests de la tâche 5 seulement ; l'essai prouve « pas cassable en survie ».
- **Rôle** : le joueur de l'essai est Ingénieur sans être opérateur ; l'usage par un Citoyen n'est pas essayé.
- **Lampe hors d'une grotte close** (plein jour, sous l'eau) : vue en GameTest seulement.
- **Caches des passages d'essai** : le monde de l'essai garde des caches des passages précédents, loin de l'aire (hors de portée du détecteur) ; `/cache list` en montre plus d'une. L'essai ne vérifie que la sortie de la cache cassée.

## Incidents de l'essai lui-même

Aucun défaut du code livré n'a été trouvé. Les échecs venaient du banc, chacun corrigé avant les passages qui font foi.

1. **Incident : chute dans le vide (signalé par Killian pendant l'essai).** Le joueur se téléportait en créatif à y = 155 au centre de l'aire avant que la dalle existe : sans sol, il est tombé pendant les 4 s d'attente (journal du serveur : « Dev moved too quickly », puis « Dev fell from a high place »), est mort, et les étapes suivantes lisaient un monde vide (`void_air`, détecteur et lampe absents). La cause n'était pas le monde du serveur (plat, généré, correct) : l'aire était construite après l'arrivée du joueur. Correction : le joueur passe en **spectateur** (sans pesanteur) pour le transfert, on attend que les chunks de l'aire soient reçus (`hasChunk`), la dalle est posée, puis seulement le joueur est replacé dessus et repasse en survie. **L'essai garde désormais le joueur : dès que la dalle est posée, chaque pas échoue (critique) si le joueur est mort ou à plus de 3 blocs sous la dalle, et l'étape de préparation exige sa vie > 0.** Les passages qui font foi n'ont aucune mort ; un essai où le joueur meurt n'est jamais compté comme réussi.
2. **Le détecteur « ne bipait pas » dans l'essai.** `SoundInstance.getPitch()` lève une `NullPointerException` à la publication de `PlaySoundEvent` (le son résolu n'existe pas encore) ; l'écouteur plantait avant d'enregistrer le bip. La hauteur est lue dans le champ de l'instance, par réflexion.
3. **Intervalle moyen faussé** (8,3 ticks pour 12 attendus) par un paquet arrivé avec le suivant (le client traite les paquets une fois par tick) : l'intervalle est la moyenne sur sept bips, du deuxième au dernier.
4. **Une pile du butin hors de portée** : le joueur debout contre la cache ne ramassait qu'une pile. L'étape attend qu'il ne reste aucun objet au sol et, au besoin, place le joueur sur la case de la cache.
5. **Le joueur resté sans droits** après l'étape du lot 7 : l'étape du lot 5 envoyait ses commandes (« Unknown or incomplete command ») et échouait. L'étape rend l'opérateur au joueur à sa fin.
6. **Un `/clear` général effaçait la batterie du poste du lot 2** : la relance du lot 2 relisait 0 FE au lieu de 200. L'étape n'efface plus que ses propres objets.
7. **Plus de case libre dans la barre d'action** : détecteur, lampe et butin occupaient les cases que l'étape du lot 4 de la relance cherche vides (le clic droit sur le disjoncteur se faisait avec une bouteille d'eau en main). L'étape retire détecteur, butin et lampe dès qu'ils ne servent plus.
8. **Serveur démarré trop lentement** : deux lancements successifs se sont chevauchés (le script d'essai abandonnait au bout de 5 min) ; un serveur resté ouvert après un client planté a été arrêté par la console RCON (`stop`), jamais tué.
9. **Passage « seule »** : l'étape a été mise au point seule par la variable d'environnement `E2E_EXPLORATION_SEULE`, qui saute les lots 1 à 6.
