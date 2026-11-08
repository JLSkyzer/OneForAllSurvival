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

## Tâche 8 : phase des ressources et fin (overlay du mod)

Lancements du 8 novembre 2026 (`./gradlew --offline installEarlyWindow runClient -PwithPack`, poste de Killian, RTX 4060).

- `loadingOverlay` trouve `LostIslandLoadingOverlay` ; le module du mod (couche du jeu) lit le jar de service (couche des services) sans `addReads` : journal `module oneforallsurvival (couche de 20 modules) lit oneforallsurvival.earlywindow (couche de 1 modules) : true`.
- La barre reste en découverte pendant les libellés « Scanning mod candidates », « Launching minecraft », « Loading bootstrap resources » de la barre EARLY, et ne passe aux mods qu'à « Loading mods » (12,9 s).
- Fin : rechargement fini, la barre et le texte s'effacent en 1 s (logo gardé), l'overlay se retire, la scène est libérée (`overlay du mod : scène libérée`).
- Durée jusqu'à l'écran titre (première ligne de `latest.log` → ligne Realms) : **24,3 s** avec la scène (sans film), **27,1 s** avec la scène et le film d'images, **23,8 s** avec l'écran de Forge (`fmlearlywindow`). Un lancement de chaque, pas une moyenne.
- `reload.getActualProgress()` est très en avance sur le temps : 0,70 à la première image, 0,95 une seconde plus tard, 1,00 à 5 s, alors que le rechargement finit à 7,6 s. La barre passe donc vite à ≈ 97 % et y reste.
- Review Focus 5 (`-PreloadAfterTitle`, `ReloadAfterTitle` en devcontent) : `Minecraft.reloadResourcePacks()` 4 s après l'écran titre ; overlay vu `net.minecraft.client.gui.screens.LoadingOverlay` (celui de Mojang), rechargement fini en 5,9 s, retour à `TitleScreen`, aucune exception nouvelle (mêmes messages que sans rechargement, répétés par le second chargement des modèles). La scène était libérée avant : plus rien ne la dessine.
