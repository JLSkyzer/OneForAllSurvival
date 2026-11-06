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
- `runGameTestServer` (tache `GameTest` demandee) exclut le pack : "All 0 required tests passed".
  `test` : NO-SOURCE (aucun test JUnit pour l'instant).
