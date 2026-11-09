# Vérifications du lot 5 : vivres et protection

Relevés du 2026-11-09, sur `main` (le travail est fait directement sur `main`). Ce document couvre la tâche 6 (preuve de bout en bout, film). Les tâches 1 à 5 ont leurs GameTests, relancés ici.

**État : le lot est prouvé dans les limites de la section « Non prouvé », après la correction d'un défaut que l'essai sur serveur dédié a trouvé** : sous First Aid, la clôture pouvait tuer un joueur malgré son plancher de vie (section « Le défaut trouvé : le plancher de vie sous First Aid »). Corrigé dans `fix(fence): choc non mortel sous First Aid` (puis `fix(fence): sans lecture des membres, pas de degats`), puis rejoué : un passage unique de bout en bout et sa relance, « bilan : TOUT PASSE » aux deux.

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew test` | 275 tests JUnit, 0 échec (somme des `build/test-results/test/*.xml`), après le dernier changement de code (23:42) |
| `./gradlew runGameTestServer` (sans pack, 23:42, après le dernier changement de code) | « All 403 required tests passed » (`run-gametest/logs/latest.log`), aucune ligne `[SKIP]` |
| `./gradlew runGameTestServer -PwithPack` (23:47) | « All 403 required tests passed », avec **deux lignes `[SKIP]`** : `unJoueurFaibleNeMeurtPas` et `unJoueurEstBlesse` |

Les GameTests `ClotureGameTests.unJoueurFaibleNeMeurtPas` et `unJoueurEstBlesse` restent **sautés** quand First Aid est chargé (deux lignes `[SKIP]` du journal ; ils réussissent sans rien éprouver) : le joueur factice des GameTests n'est pas un vrai joueur de First Aid. Avec le pack, **seule l'étape du serveur dédié ci-dessous prouve donc le plancher de vie du joueur**. La règle elle-même (marge des membres, plus petit des deux planchers) est prouvée en JUnit (`FenceRulesTest`).

## Corrections de la revue finale

Commits `fix(food): couveuse, eclosion a la taille du petit et coupure reelle` et `fix(fence): plages de config sures` : le petit est mesuré avant de chercher sa place (une tortue adulte ne passait pas contre un mur ni sous une trémie), le compteur de coupure de la couveuse ne court que si un œuf au moins a déjà une progression positive (une machine neuve ou reposée, à 0 FE, ne détruit plus ses œufs au bout de 60 s), les essais d'un œuf mûr suivent un compteur propre à la machine, `safeHealth` vaut au moins 0,5 et `baseEnergy` au moins 1. Quatre GameTests (lot `couveuse_c`) et un JUnit (`FenceConfigTest`, quatre tests) les couvrent ; les deux GameTests de coupure déjà présents posent désormais une progression avant d'éprouver la coupure.

Le passage de bout en bout ci-dessous a été refait après ces changements. **Aucune scène de l'essai ni du film ne s'appuyait sur des œufs perdus sans progression préalable** : dans l'essai, chaque œuf entre dans une couveuse alimentée et l'essai attend l'état RUNNING (donc une progression positive) avant de couper la source ; le film fait de même (« Il entre à son tour », état RUNNING exigé, puis courant coupé). Le film n'a pas été retourné, aucune scène n'étant devenue fausse.

Un échec passager a été vu pendant ces relectures : `dessalinisateur` (`sanseauriennesefait`, « les cuves devaient rester vides ») au premier passage `-PwithPack` de 23:44, hors de la zone modifiée ; le passage suivant de 23:47 est complet (403 sur 403).

## Serveur dédié et vrai client

`./gradlew runServer -PwithPack` puis `./gradlew runE2eClient -PwithPack` (qui finit par `/stop`), nouveau `runServer -PwithPack` et `./gradlew runE2eRelance -PwithPack`. Client `Dev`, opérateur de `run-server`, avec le pack complet, **First Aid compris** (`runtimeOnly` de `build.gradle`). L'étape du lot 5 est `src/devcontent/java/dev/oneforallsurvival/dev/E2eProtection.java`, branchée après l'étape du réseau dans `E2eClient`. Son aire est une dalle de pierre à 70 blocs à l'ouest du joueur, en hauteur (y = 150), hors du point d'apparition. Le montage est posé par `/setblock` ; les gestes du joueur (ouvrir un menu, déplacer une pile, pousser contre une clôture) sont de vrais clics et de vraies touches ; les états sont lus côté client (blocs, menus, entités) ou, pour la vie, côté serveur par la console RCON (`data get entity Dev Health`, `data get entity Dev ForgeCaps."firstaid:cap_adv_dmg_mdl"`).

**Un passage unique de bout en bout** (étapes des lots 1 à 5, joueur du lot 5 compris), refait après le dernier changement de code (corrections de la revue finale, voir ci-dessous) puis la relance : première phase **131 lignes `PASS`, 0 `FAIL`, « bilan : TOUT PASSE »** (6 min 12 s, de 23:49 à 23:55, serveur et client lancés par Gradle) ; relance (nouveau `runServer`, `runE2eRelance`, qui rejoue les lots 2 à 4 et le lot 5) **46 `PASS`, 0 `FAIL`, « bilan : TOUT PASSE »** (1 min 11 s, de 23:57 à 23:58). Il a fallu plusieurs lancements pour y arriver (voir « Incidents de l'essai lui-même »). Les premiers essais du lot 5, faits avant la correction, ont été joués étape seule (variable d'environnement `E2E_PROTECTION_SEULE`, qui saute les étapes des lots précédents) : ce sont eux qui ont trouvé le défaut.

### Ce qui passe : couveuse, plaque, marmite, zombie

Lignes d'un passage antérieur à la correction (même étape, sans le pas du joueur ; le passage final en rejoue les contrôles), telles que le journal les écrit (les messages d'un client en `en_us` sont en anglais) :

```
[e2e] PASS couveuse alimentee par une source Create : etat RUNNING, progression 22/24000 ticks (duree lue cote client, config synchronisee : 20 min), energie 1984/2000
[e2e] PASS progression de l'oeuf : 23880/24000 (abregee par commande ; l'eclosion qui suit est reelle)
[e2e] PASS poussin vu par le client en 107, 151, 217 (bebe true), la case de l'oeuf est vide, etat de la couveuse RUNNING
[e2e] PASS sans courant : etat NO_POWER, compteur de coupure 200/1200 ticks (visible dans le menu), l'oeuf est toujours la : 1 egg
[e2e] PASS coupure courte sans perte : l'oeuf est la, progression 12 -> 14 (gardee), compteur de coupure remis a 0
[e2e] PASS couvee perdue : le compteur est monte jusqu'a 1200/1200 ticks (vu dans le menu) puis la case de l'oeuf est vide ; duree reelle de l'attente 60 s
[e2e] PASS plaque : etat RUNNING, LIT vrai, le menu de Farmer's Delight lit isHeated() vrai (sa propre regle, pas une copie)
[e2e] PASS ragout de boeuf cuit sans feu, sur la plaque : sortie 1 beef_stew
[e2e] PASS plaque sans courant : etat NO_POWER, LIT faux, isHeated() faux, progression de cuisson 0, sortie vide apres 8 s (les memes ingredients cuisaient en 10 s avec le courant)
[e2e] PASS la marmite exige 3 bols (un par portion), 0 manquant : 5 bowl
[e2e] PASS sortie de la marmite : 3 beef_stew ; ingredients restants boeuf 1, carotte 1, pomme de terre 1, bols 2
[e2e] PASS sur 2 jeux et 5 bols donnes, il reste boeuf 1, carotte 1, pomme de terre 1, bols 2 : un jeu et trois bols payes pour trois portions
[e2e] PASS zombie contre la cloture : vie minimale vue 18.0/20 (hostile : il peut en mourir : false), repousse de 0,41 bloc apres avoir touche la barre (z le plus proche 235,95, barre en z=235.5
[e2e] PASS clôture sans courant : le zombie sans IA reste contre la barre (à 0,025 bloc) pendant 10 s, garde ses 20.0 points de vie et ne bouge pas (etat NO_POWER)
[e2e] PASS a retrouver apres relance : seconde couveuse -161, 150, 227 sans courant, compteur de coupure 301/1200 ticks, un oeuf dedans (le serveur est arrete tout de suite)
```

Relance (nouveau `runServer`, `runE2eRelance`) :

```
[e2e] PASS relance : seconde couveuse relue du disque en -161, 150, 227, compteur d'avant l'arret 301
[e2e] PASS relance : compteur de coupure 820 ticks, a l'arret 301 (il repart de la valeur sauvegardee, sans perte) ; l'oeuf est la, etat NO_POWER
[e2e] PASS relance : source remise, etat RUNNING, compteur 0, oeuf garde
```

Ce que chaque ligne mesure, et ses limites :

- **Éclosion** : l'œuf entre par un vrai clic majuscule ; sa progression est **avancée par `/data merge` à 120 ticks de l'éclosion** (vingt minutes réelles sinon) ; l'éclosion qui suit, le poussin (vu par le client, bébé) et la case vidée sont réels. La durée complète de 20 minutes n'a jamais été attendue.
- **Coupure** : source retirée et réserve vidée par `/data merge {energy:0}`. La coupure courte (200 ticks) ne perd rien et le compteur retombe à zéro au retour du courant ; la coupure longue est attendue **en temps réel** (60 s) et l'œuf disparaît à 1200 ticks. La fumée et le son d'extinction ne sont pas lus par l'essai (le film montre la fumée, voir plus bas ; le son n'est vérifié nulle part).
- **Compteur après arrêt** : le compteur lu juste avant `/stop` (301 ticks) est retrouvé après la relance à 820 ticks : il repart de la valeur sauvegardée (lue au menu 26 s après le démarrage du serveur, d'où l'écart) ; l'œuf est gardé. Le serveur a été arrêté au bout de ~15 s de coupure, bien avant le seuil.
- **Plaque** : c'est `CookingPotMenu.isHeated()`, la règle de Farmer's Delight elle-même, qui juge ; la plaque coupée (source retirée) est vue `NO_POWER`, `LIT` faux, et un second ragoût ne progresse pas en 8 s.
- **Marmite de cantine** : 2 jeux d'ingrédients et 5 bols donnés, 3 ragoûts obtenus, il reste 1 jeu et 2 bols.
- **Zombie** : avec son IA, posé contre la barre dans un enclos de pierre, il perd 2 points (18/20) et est éloigné de 0,41 à 0,62 bloc ; sans courant, un zombie **sans IA** reste 10 s à 0,025 bloc de la barre sans perdre un point. (Un zombie sans IA subit les dégâts mais **n'est pas déplacé** par le repoussement : essai fait et abandonné pour la mesure du repoussement.)

### Le défaut trouvé : le plancher de vie sous First Aid

Le cahier (feature 16) dit « pour les joueurs, le choc est non mortel ». Le premier essai du pas du joueur a pourtant **tué le joueur**. Le joueur est non opérateur (`deop Dev` par la console RCON, `hasPermissions(2)` faux côté client), en survie, contre une clôture alimentée ; pour chaque choc, le journal consigne la vie vanilla vue du client et du serveur, et les huit membres de First Aid lus sur le serveur (`data get entity Dev ForgeCaps."firstaid:cap_adv_dmg_mdl"`).

1. First Aid **fait suivre la vie vanilla** à ses membres : 34 points de membres (tête 4, torse 6, six autres de 4) valent 20 points vanilla, chaque point de membre en retire 0,588. Un `hurt` de `n` points retire `n` points de membre (vérifié : 0,706 retiré à la tête pour un choc de 0,7), répartis **au hasard** sur les membres qui ont encore de la vie.
2. Le plancher de 4 points tenait sur la vie vanilla, mais **First Aid tue dès que la tête ou le torse tombe à zéro, quelle que soit la vie totale**.

Journal du défaut (extraits ; joueur parti de 4,71 de vie vanilla, tête 1,0, torse 3,0, bras 2,0 et 2,0, autres membres 0) :

```
[e2e] choc 1 : vie client 4.29 | First Aid [head=0.294 ... rightArm=2.0, leftArm=2.0 ... body=3.0] | vivant true
[e2e] choc 2 : vie client 4.12 | First Aid [head=0.0034599304 ...] | vivant true
[e2e] choc 3 : vie client 4.049 | First Aid [head=0.00346 ... rightArm=1.88 ...] | vivant true
[e2e] choc 1 (second passage) : vie client 4.0203 | First Aid [head=0.00346 ... leftArm=1.95 ...] | vivant true
[e2e] PASS apres : vie client 0.0 | First Aid [head=0.0 ...] | vivant false
[e2e] FAIL Le plancher de vie tient : mort true, vie avant 4.049, minimale 4.020 (plancher 4.0)
```

La vie vanilla minimale (4,02) respectait le plancher, **le joueur est mort quand même** : le dernier choc, de `min(1, 4,02 - 4)` = 0,02 point de membre, est tombé au hasard sur la tête, à 0,0035. Le plancher décroît géométriquement sans s'annuler, et chaque choc reste mortel pour un membre presque vide. Plus généralement, tout joueur à tête ou torse bas peut mourir de la clôture à une vie vanilla bien au-dessus de 4, où le plancher ne se déclenche même pas.

### La correction

`fix(fence): choc non mortel sous First Aid` : pour un joueur, quand `lethalForPlayers` est faux et que First Aid est chargé, le choc est le plus petit de la règle de la vie vanilla et de `FenceRules.damageForLimbs` : la tête et le torse le plus faible gardent 1 point de membre (`min(tête, torse) - 1` au plus, nul si la marge est atteinte ; dans ce cas, repoussement seul). Le montant entier ne dépassant pas cette marge, ni la tête ni le torse ne peuvent passer sous 1 (les autres membres, non : `rightLeg=0.0` dans le journal ci-dessous), où que la répartition aléatoire de First Aid le porte. Rien ne change pour les mobs, pour `lethalForPlayers = true`, ni sans First Aid. Le choc passe toujours par `entity.hurt` d'une source de dégâts normale (aucune écriture directe dans les membres ni dans la vie). Toute la référence à First Aid est dans `fence/FirstAidLimbs.java`, atteinte derrière `ModList.get().isLoaded("firstaid")` ; l'API est une dépendance `compileOnly` (jar déjà en cache, aucun téléchargement), le mod se charge sans First Aid. Sans lecture possible des membres (joueur sans le modèle de First Aid), la règle ne donne aucun dégât : repoussement seul, par prudence (`fix(fence): sans lecture des membres, pas de degats`). Règle pure prouvée en JUnit (`FenceRulesTest` : marge des membres, marge atteinte donne 0, le plus petit des deux planchers, membres illisibles donnent 0).

### Preuve de la correction (passage unique, pas du joueur renforcé)

Le pas du joueur échoue s'il passe par chance : tête et torse sont relus à chaque choc, et l'essai exige vivant, tête et torse au moins à `min(valeur de départ, 1)`, et au moins 15 repoussements à faible vie (le repoussement s'applique donc toujours). Lignes du passage final :

```
[e2e] choc 1 (repoussement de 0,23 bloc) : vie client 19.41 | First Aid membres [head=3.0, ... body=6.0] (somme 33.0) | vivant true
[e2e] choc 2 (repoussement de 0,23 bloc) : vie client 18.82 | First Aid membres [head=2.0, ... body=6.0] (somme 32.0) | vivant true
[e2e] choc 3 (repoussement de 0,23 bloc) : vie client 18.24 | First Aid membres [head=1.0, ... body=6.0] (somme 31.0) | vivant true
[e2e] PASS apres 16 /damage de 1 point (starve) : vie client 8.82 | First Aid membres [head=1.0, leftFoot=2.0, rightLeg=0.0, rightArm=2.0, leftArm=2.0, rightFoot=3.0, leftLeg=0.0, body=5.0] (somme 15.0) | vivant true
[e2e] choc 1 .. 15 (repoussement de 0,27 bloc) : vie client 8.82 | head=1.0 ... body=5.0 | vivant true      (aucune perte, quinze fois)
[e2e] PASS joueur reel non operateur sous First Aid, 15 chocs a faible vie : vivant, vie vanilla minimale 8.82 pour un depart a 8.82 (plancher 4.0) ; membres avant [head=1.0, ...] (somme 15.0), apres [head=1.0, ...] (somme 15.0)
```

Ce que cela montre : (a) **à pleine vie**, dans ce passage, les trois premiers chocs sont tombés tous les trois sur la tête (4,0 → 3,0 → 2,0 → 1,0) ; c'est **une observation d'une répartition aléatoire** : dans un passage antérieur, trois chocs à pleine vie avaient touché la tête, un bras et une jambe. Elle montre que la tête peut atteindre la marge en trois chocs, pas que cela arrive à chaque fois. Ce qu'un quatrième choc aurait fait sans la correction **n'a pas été essayé** : sans elle, le quatrième choc (1 point) aurait pu retirer le dernier point de la tête, selon le hasard, c'est une inférence ; (b) **à faible vie, tête à 1,0**, quinze chocs consécutifs : la marge est atteinte, plus aucun dégât (head 1.0, somme des membres inchangée), le joueur est vivant, et le repoussement a eu lieu quinze fois. Limites : la tête est arrivée à 1,0 pile dans les deux cas, donc la branche « dégât réduit mais non nul » (par exemple tête à 1,5) n'est prouvée qu'en JUnit, pas en jeu ; le plancher de vie vanilla (4) n'a pas été approché en jeu avec la correction (la marge de la tête le protège avant). Le cas « joueur sans modèle de membres » (aucun dégât, repoussement seul) n'est prouvé qu'en JUnit.

## Film

`./gradlew runDemoProtectionFilm -PwithPack` (`FilmProtection`) : monde intégré plat, la nuit (luminosité du jeu au maximum, réglage du film en mémoire), une passe réussie, 824 images, `termine.json` sans échec, encodé en `run-demo/film-lot5-protection.mp4` (1 min 22 s, 1280 x 720, 10 images par seconde). **Le temps du monde n'est jamais accéléré** ; deux durées sont abrégées en mémoire et le disent à l'écran : la progression de l'œuf (avancée à 6 s du terme) et le compteur de coupure (avancé à 4 s du seuil de 60 s). Capture des images du jeu lui-même (`FilmCapture`) : aucune autre fenêtre ne peut y figurer. Images extraites du mp4 et ouvertes :

- 0:03 : la couveuse de nuit contre sa source (cube violet) ; derrière, la plaque et la marmite, la clôture, le mur de pierre de l'enclos. Le bandeau annonce vide / STOPPED. En haut à gauche, le portrait des membres de First Aid ; dans le chat, le message du tutoriel de First Aid.
- 0:07 à 0:17 : le menu de la couveuse, un œuf ; « État : Couve », 2000 / 2000 FE, la barre de progression se remplit ; la légende annonce l'abrègement (progression 23 919 puis 23 999 sur 24 000).
- 0:19 à 0:21 : le poussin est né près de la couveuse (vu de côté, sur le sol), la place est libre.
- 0:23 à 0:27 : second œuf, courant coupé : « État : Sans courant », « Couvée perdue dans 60 s », puis, après l'abrègement, « dans 4 s ».
- 0:29 à 0:31 : menu fermé ; la fumée sombre s'élève de la couveuse au moment de la perte, la case est vide. **Le son d'extinction n'est pas dans le film** (images seulement).
- 0:35 à 0:39 : la plaque allumée sous le Cooking Pot (face allumée, vapeur du pot) à côté de la marmite de cantine.
- 0:41 à 0:53 : les ingrédients (bœuf, carotte, pomme de terre) et un bol par portion entrent dans la marmite ; « État : Cuit », la barre avance, 6000 / 6000 FE.
- 0:55 à 0:59 : sortie de 3 ragoûts, ingrédients consommés ; les trois piles reprises dans la barre d'action.
- 1:01 à 1:05 : la clôture en coude vue **depuis l'extérieur du coude**, avec son électrificateur et son enclos de pierre : les rails se raccordent bien au poteau d'angle, des deux côtés. **Le coude, que la revue de la tâche 5 n'avait pu confirmer, est correct.**
- 1:07 à 1:09 : un zombie casqué dans l'enclos, contre la clôture : « 18/20 points », repoussé.
- 1:11 à 1:20 : courant coupé (réserve 0 FE, NO_POWER) ; un zombie neuf, sans IA, figé contre la clôture : « 20/20 points » pendant 8 s.

Les **machines de nuit** (visuel que la revue de la tâche 5 n'avait pu confirmer) se lisent, mais sombres : la couveuse et la marmite de cantine, de noir et de violet, se détachent peu du sol à la luminosité par défaut ; le film a été tourné à la luminosité maximale. La plaque allumée et le Cooking Pot sont les mieux lisibles. **Le film n'a pas été retourné après la correction** : ses scènes (zombie, courant coupé) ne montrent pas de joueur et ne dépendent pas d'elle. Un premier film, tourné à la luminosité par défaut, montrait bien la fumée de la perte mais à peine les blocs.

## Non prouvé

- **Le dégât réduit mais non nul sous First Aid en jeu** (tête entre 1 et 2 points) : seulement en JUnit ; en jeu, la tête était à 1,0 pile.
- **Le plancher de vie vanilla de 4 points, approché en jeu avec la correction** : la marge de la tête agit avant ; seul le JUnit éprouve les deux planchers ensemble.
- **Équilibrage réel** : vingt minutes d'incubation, 60 s de tolérance, 8 FE par tick, 30 FE par tick et 3 portions pour la marmite, 2 + 0,25 par clôture pour l'électrificateur, 2 points de dégât : jamais vécus en partie. L'incubation complète de 20 minutes n'a jamais été attendue (la progression est avancée par commande).
- **Déchargement réel d'un chunk** : pour la couveuse, la marmite et l'électrificateur, aucun chunk n'a été réellement déchargé puis rechargé en jeu ; la sauvegarde n'est éprouvée que par `saveWithoutMetadata`/`load` en GameTest (sur une copie) et, pour le compteur de la seconde couveuse seulement, par la relance du serveur.
- **Relance du serveur** : elle n'a relu que le compteur de coupure de la seconde couveuse ; la marmite et l'électrificateur ne sont pas relus du disque après un vrai arrêt.
- **Source électrique dans un chunk qui ne tourne pas, couveuse qui tourne** : la couvée peut être perdue (la source ne fournit plus de courant alors que le compteur de coupure court) ; jamais essayé.
- **Arrêts silencieux de la marmite** : le joueur ne les distingue pas en jeu (courant manquant, bols manquants, sortie pleine) ; leur distinction en jeu n'a pas été vérifiée.
- **Multijoueur** : un seul client à la fois.
- **FE d'un autre mod** : seule la source créative de Create Crafts & Additions a alimenté les machines (`creative_energy`) ; ni New Age ni Advanced Electricity.
- **Répartition des dégâts de First Aid (feature 35, à venir)** : la clôture n'a aucune règle propre ; sa répartition est celle de First Aid par défaut (au hasard sur les membres vivants). Une règle de distribution propre à la clôture relève de cette feature.
- **Mobs réels dans une longue ligne** : seuls un zombie et un joueur contre une ligne de cinq clôtures en coude ; ni un enclos d'animaux, ni la ligne de 64 clôtures, ni la végétation sur le fil en conditions réelles. La végétation est couverte par un GameTest (`laVegetationCouteDavantage`) ; la ligne de 64 (ou plafonnée) ne l'est par aucun GameTest, seulement par le JUnit `FenceLineTest` sur la fonction pure.
- **Coût serveur** : jamais mesuré (relecture de la ligne toutes les 2 s, 64 clôtures au plus).
- **Orage** : la consommation doublée de la clôture n'est vue qu'en GameTest.
- **Le son** d'extinction de la couvée.
- **Un animal de couveuse qui reste vivant et suit ses règles de vie** : seul le poussin est vu naître ; la tortue de la liste par défaut n'a pas été essayée en vrai.

## Incidents de l'essai lui-même

Le défaut du plancher mis à part, aucun défaut du code livré n'a été trouvé. Les échecs venaient du banc, chacun corrigé avant le passage qui fait foi :

1. Le gestionnaire de la couveuse range une pile entière d'œufs d'un clic majuscule (un œuf par place) : l'essai donne désormais un œuf à la fois.
2. L'expression régulière des membres de First Aid ignorait l'espace après `:` de la sortie NBT de `/data get` : tous les membres sortaient à zéro dans le premier journal.
3. Un zombie sans IA subit les dégâts mais le repoussement ne le déplace pas : l'essai du repoussement utilise un zombie avec IA, posé contre la barre dans un enclos de pierre, avec jusqu'à trois nouveaux essais s'il s'éloigne avant le premier choc (échec intermittent vu une fois en passage complet : le zombie était parti avant le choc).
4. Un joueur non opérateur ne peut plus `/tp` : l'essai replace le joueur par la console RCON.
5. À la reconnexion de la relance, le joueur est déjà près de la machine : l'essai attend la fin de la téléportation (2,5 s) avant son clic.
6. Dans le film, un clic juste après la téléportation de la caméra tombait hors de portée : une pause les sépare.
7. **Passage complet : le joueur ne revenait pas chez lui.** L'aire du lot 5 est à 70 blocs : à la relance, le joueur se reconnectait loin des machines des lots 2 à 4, dont les clics échouaient. L'étape le ramène à son point de départ avant le `/stop`, et à la relance elle passe en premier (le compteur de coupure de la seconde couveuse court tant que le monde tourne : les étapes précédentes, longues, l'auraient fait dépasser 60 s).
8. **Passage complet : les ragoûts de l'étape saturaient la barre d'action** (la plaque et la marmite en donnent quatre, de même date que celui des vivres, donc fondus en piles), si bien que le lot 4 n'y trouvait plus de case libre pour sa « main vide » et ne cliquait pas sur son disjoncteur. L'étape retire ses ragoûts à la fin (le nombre d'avant l'étape reste).
9. Un joueur resté mort ou noyé par un essai interrompu laisse le passage suivant sans emplacement libre : le fichier du joueur du serveur d'essai (`run-server/world/playerdata`, hors dépôt) a été supprimé entre les essais.
