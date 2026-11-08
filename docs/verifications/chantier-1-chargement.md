# Vérifications du chantier 1 : écran de chargement Lost Island

Relevés du 2026-11-08, sur la branche `interface-chargement`.

## Tâche 0 : branchement du fournisseur `lostisland`

`./gradlew earlyWindowJar --offline` : `build/libs/oneforallsurvival-earlywindow-0.1.0.jar` (5 128 octets). `jar tf` n'y montre que `META-INF/MANIFEST.MF`, `META-INF/services/net.minecraftforge.fml.loading.ImmediateWindowProvider` et `dev/oneforallsurvival/earlywindow/LostIslandWindow.class`.

`run/config/fml.toml` : `earlyWindowProvider = "lostisland"`, écrit sans BOM (premiers octets lus : `23 45 61`, « #Ea »).

`./gradlew installEarlyWindow runClient -PwithPack --offline`, client arrêté par son PID une fois l'écran titre atteint. Lu dans `run/logs/latest.log` :

```
[11:00:16.409] [main/INFO] [net.minecraftforge.fml.loading.ImmediateWindowHandler/]: Loading ImmediateWindowProvider lostisland
```

Le jeu atteint le menu comme avant (« Sound engine started » à 11:00:59.126, vérificateur Realms de l'écran titre à 11:01:00.311). Aucune erreur liée au fournisseur dans le journal.

## Relevé du démarrage

Source : `run/logs/lostisland-earlywindow.log` (temps depuis `initialize`, t0 ≈ 11:00:16.4) croisé avec `run/logs/latest.log`. Un seul lancement, pack complet, poste de Killian : ces durées sont **un relevé, pas une mesure répétée**.

### Barres de Forge (`StartupNotificationManager.getCurrentProgress()`)

| Nom exact de la barre | Pas | Libellé observé |
|---|---|---|
| `EARLY` | 0/0 (sans pas) | `Loading mods` |
| `Mod Gather` | 9/18 → 18/18 | `Mod Gather: dispatching CONSTRUCT`, puis `Mod Gather working` |
| `State transition LOAD_REGISTRIES running` | 0/0 | (aucun) |

### Messages caractéristiques (`getMessages()`)

- `Forge loading 47.4.28`
- `Forge version 47.4.28`
- `REGISTERING forge:structure_modifier_serializers`
- `REGISTERING minecraft:worldgen/biome`

Relevés une fois par seconde : les messages qui passent entre deux relevés ne sont pas tous vus (`getMessages()` renvoie une liste courte, les plus récents en tête).

### Phases et durées

| Phase | Début (t) | Fin (t) | Durée | Repère |
|---|---|---|---|---|
| Fenêtre créée (`initialize` du `DisplayWindow`) | 0,0 s | 0,7 s | 0,7 s | journal du fournisseur |
| Découverte, services, couche du jeu | 0,7 s | 6,4 s | 5,7 s | `updateModuleReads` à 6,4 s |
| Préparation du jeu (mixins, classes de Minecraft) | 6,4 s | 20,5 s | 14,1 s | « Setting user: Dev » à 11:00:36.5 ; `setupMinecraftWindow` à 20,5 s |
| Chargement des mods (barres `EARLY`, `Mod Gather`, registres) | 20,5 s | 29,9 s | 9,4 s | `Mod Gather` 9/18 à 21,9 s, 18/18 à 28,9 s |
| Ressources de Minecraft (`loadingOverlay`) | 29,9 s | ≈ 43,9 s | ≈ 14 s | « Reloading ResourceManager » à 11:00:46.1 ; écran titre ≈ 11:01:00.3 |

Total jusqu'à l'écran titre : environ 44 s depuis le chargement du fournisseur.

`periodicTick` n'est appelé que pendant le chargement des mods (de 20,5 s à 29,9 s). Pendant la découverte et la préparation du jeu, puis pendant les ressources (que dessine l'overlay), il ne l'est pas : le journal n'a aucune ligne entre 0,7 s et 20,5 s, ni après 29,9 s.
