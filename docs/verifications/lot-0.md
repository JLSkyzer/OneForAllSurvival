# Verifications du lot 0

## Tache 1 : pack de developpement (2026-11-06)

Verifie en lancant `./gradlew runClient` jusqu'au menu principal (`Sound engine started` dans
`run/logs/latest.log`), puis en arretant le processus. Aucun `Failed to create mod instance`.

Environnement : Forge 47.4.28, Minecraft 1.20.1, JDK 17.

| Mod id | Version lue dans le log | Coordonnee Gradle |
|---|---|---|
| oneforallsurvival | 0.1.0 | (ce depot) |
| farmersdelight | 1.20.1-1.3.4 | `maven.modrinth:farmers-delight:1.20.1-1.3.4` |
| create | 6.0.8 | `maven.modrinth:create:mc1.20.1-6.0.8` |
| createaddition | 1.20.1-1.3.3 | `maven.modrinth:createaddition:forge-1.20.1-1.3.3` |
| create_new_age | 1.2.0+mc1.20.1 | `maven.modrinth:create-new-age:7vnwbouk` |
| light_bulb_addon_create_crafts_and__addions (Create Advanced Electricity) | 1.20.1_1.0.9 | `curse.maven:create-advanced-electricity-addon-1743391:9362725` |
| hospitals | 4.0.0 | `maven.modrinth:hospitals:h2fZRbSr` |
| marbledsfirstaid (Medical Remedies) | 1.2.1.0 | `curse.maven:medical-remedies-1185929:8023446` |
| firstaid | 1.20.1-1.1 | `curse.maven:first-aid-276837:5531834` |
| crafttweaker | 14.0.60 | `com.blamejared.crafttweaker:CraftTweaker-forge-1.20.1:14.0.60` |

Charges en plus, embarques dans les jars (rien a declarer) : flywheel 1.0.5, ponder 1.0.91
(Create 6.0.8), esl 1.1.3 (Create New Age), mixinextras 0.3.6 et 0.4.1.

### Points d'attention

- **Modrinth : un numero de version partage par plusieurs loaders designe le mauvais jar.**
  `create-new-age:1.2.0+mc1.20.1` a donne le jar Fabric (aucun mod charge, sans erreur) et
  `hospitals:4.0.0` le jar NeoForge (`neoforge.mods.toml`). Les deux sont donc references par
  l'identifiant de version Modrinth du jar Forge. Symptome : le mod est absent du log, sans erreur.
- CraftTweaker tire `org.openzen.zencode` (0.3.8) depuis `maven.blamejared.com` : ce groupe
  figure dans le `content` du depot.
- Hospitals journalise `Unable to load model: 'minecraft:room_flag'` : defaut du mod, sans effet
  sur le chargement.
- Farmer's Delight est dependance obligatoire en `[0,)` : le format `1.20.1-1.3.4` n'est pas
  semver, la plage n'est pas resserree.
- `runGameTestServer` sans `-PwithPack` exclut le pack : voir « Commande de preuve » plus bas pour ce que son décompte vaut.

## Commande de preuve (2026-11-06)

`./gradlew runGameTestServer -PwithPack`. Sans l'option, les tests de réseaux et de pack réussissent
sans rien vérifier (ils journalisent « IGNORE, pack absent ») : le décompte ne dit alors rien des
réseaux. Lire la ligne « All N required tests passed » dans `run-gametest/logs/latest.log`, car la
tâche rend 0 même quand le mod échoue à se charger. Dernier passage avec le pack : 35 tests.

## Rôles : validés en jeu

Killian, avec deux clients, le 2026-11-06 :

- préfixes et accents corrects dans le chat, au-dessus de la tête et dans le TAB ;
- `/role set` refusé à un joueur sans droits ;
- persistance après redémarrage du serveur (préfixes et `/role list`) ;
- `/role delete` : le porteur redevient Citoyen aussitôt ;
- suppression d'un rôle codé en dur refusée.

## Réseaux électriques

Prouvé par GameTests et par la démonstration filmée (`runDemo`) : le courant des trois réseaux
(Create Crafts & Additions, Create New Age, Advanced Electricity) atteint la machine d'essai, en direct
et à travers `test_inline`, avec le bloc créatif de Create Crafts & Additions comme source. Des
contrôles négatifs (fil non tiré) donnent bien 0 FE.

**Non prouvé** :

- les générateurs propres aux mods (aucune scène ne part d'autre chose que le bloc créatif) ;
- la `wall_socket` d'Advanced Electricity : d'après l'analyse de la classe (pas une mesure), une
  consommatrice passive ne serait pas alimentée ; seul le chemin `power_box` est éprouvé ;
- le rechargement du monde avec de vrais réseaux ;
- plus d'un chunk ;
- les débits de l'ordre du kFE par tick.

La lecture `NO_POWER 0/1000` de Killian, avec un bloc créatif contre la machine, n'a pas été
reproduite. Hypothèse (non démontrée) : le bloc créatif garde en cache ses voisins et ne le rafraîchit
qu'à son premier tick ou sur `neighborChanged`. Si la capability de la machine est invalidée puis
recréée sans qu'il en soit prévenu, il pousse dans le vide. Un diagnostic de GameTest montre ce
mécanisme (1000 puis 200 FE sans arrivée, remontée à 1000 après une mise à jour de voisin). Si le cas
se reproduit : casser puis reposer un bloc voisin de la source.

## Risques connus

- Create 6.0.8 : `ConcurrentModificationException` dans `AllSoundEvents.prepare` au démarrage, vue une
  fois sur un client, intermittente. `CNASounds` de New Age est le suspect probable, sans preuve.
  Elle peut toucher de vrais joueurs ; relancer le jeu la fait disparaître. Décision en attente côté
  Killian.
- Lancer deux clients en même temps a provoqué le délai d'attente précoce de la fenêtre de Forge sur
  l'un d'eux (une fois ; cause seulement probable).

## Environnement de développement

`libs/esl-*.jar` (avec sa licence) est l'ESL que New Age embarque dans son jar. `fg.deobf` ne remappe
pas les jars imbriqués : son mixin garde des noms SRG et plante en dev (`NoSuchMethodError` sur un nom
`m_XXXX_`). On le fournit donc remappé, sous `-PwithPack`. À tenir à jour avec la version d'ESL de
New Age. Cela ne concerne pas le mod livré (le jeu de production tourne en SRG : déduit, non testé).

## Équipes de rôle

Le mod possède l'appartenance des joueurs aux équipes `ofas_*`. Un `/team join` d'un administrateur est
défait par la prochaine commande `/role` ou la prochaine connexion. L'appartenance se fait par nom : un
joueur renommé laisse une ancienne entrée sans effet.
