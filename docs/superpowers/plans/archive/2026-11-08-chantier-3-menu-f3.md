# Chantier 3 : menu F3 — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Le F3 devient des panneaux en verre ambré animés (Position, Monde, Lost Island, Performances, Cible, Mods), avec une vue détaillée par F3 + Tab et des graphes de FPS/TPS redessinés, sans perdre aucune information du F3 actuel.

**Architecture:** Le texte vient toujours de Minecraft et de Forge : un écouteur de `CustomizeGuiOverlayEvent.DebugText` en priorité la plus basse lit toutes les lignes (vanilla et autres mods), les classe par une logique pure (`DebugLines`), vide les listes pour taire le dessin vanilla, puis dessine les panneaux. Les données Lost Island que le client n'a pas (usure, zone d'eau salée, compteur) arrivent par un petit paquet de requête et réponse, filtré côté serveur selon le rôle. Les graphes passent par l'annulation de l'overlay `FPS_GRAPH` ; le camembert du profileur reste vanilla.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, JUnit 5, GameTests, canal réseau du mod (`BenchNetwork.CHANNEL`).

**Spec:** `docs/superpowers/specs/archive/2026-11-08-interface-lost-island-design.md`, section « Chantier 3 » (décisions de Killian du 2026-11-08 comprises). Recherche préalable (pipeline du F3 en 1.20.1, données côté client) : résumée ci-dessous dans « Faits établis ».

## Faits établis (lus par javap le 2026-11-08, non essayés en jeu)

- `VanillaGuiOverlay.DEBUG_TEXT` → `ForgeGui.renderHUDText` → poste `CustomizeGuiOverlayEvent.DebugText` (non annulable) dont `getLeft()`/`getRight()` sont les vraies listes ; un écouteur en `EventPriority.LOWEST` voit les lignes des autres mods et peut les vider. L'événement est posté même F3 fermé : tester `options.renderDebug`. Annuler `RenderGuiOverlayEvent.Pre` sur `DEBUG_TEXT` supprimerait l'événement : ne pas le faire.
- Graphes : overlay `FPS_GRAPH` (`renderFPSGraph`), activés par F3 + Alt (TPS seulement en solo) → annulable par `RenderGuiOverlayEvent.Pre`. Camembert : `Minecraft.renderFpsMeter`, privé, hors événement → laissé tel quel (décision de Killian).
- `reducedDebugInfo` : `Minecraft.showOnlyReducedInfo()` ; en mode réduit vanilla retire XYZ, Block, Chunk, Facing, lumière, biome, difficulté, spawn, et la cible.
- Touches F3 de vanilla : A B C D G H I L N P Q S T, F4, Échap. F3 + Tab est libre, mais le relâchement de F3 bascule l'affichage si le combo n'a pas été « traité » : poser `KeyboardHandler.handledDebugKey` (privé) par réflexion (`ObfuscationReflectionHelper`, nom SRG à lire dans le `joined.tsrg` du cache), avec repli sur une touche normale configurable si la réflexion échoue.
- Le client connaît déjà : `ElectricMachineBlock.STATE` (marche/panne), `BreakerBlock.CLOSED/SEALED`, le rôle (équipes `ofas_*`, maire `ofas.maire`). Il ne connaît pas : l'usure (pas de `getUpdateTag`), la zone côtière (`CoastSavedData` serveur), le compteur (abonné, index), le délesteur hors menu.

## Global Constraints

- Tout se fait sur `main` (décision de Killian) : pas de branche. Commits limités à ses propres fichiers par `git commit -- <chemins>`, `git pull --ff-only` avant de pousser, jamais `--force` ni `--no-verify` ; messages terminés par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Jamais `art/`, `docs/assets/`, `.superpowers/`, `run*/`.
- Direction A validée : cartes sombres translucides (fond ≈ `rgba(16,14,12,0.62)`, bord ambré ≈ `rgba(255,190,90,0.25)`, titres ambrés `#ffb544` en petites capitales), X `#ff7b6b`, Y `#7fe08a`, Z `#78b8ff`, panneau Lost Island bordé de bleu `#78b8ff`. Panneaux à gauche : Position, Monde, Lost Island ; à droite : Performances (FPS en grand, petit graphe, mémoire, TPS/ping), Cible.
- Animations : les panneaux glissent et apparaissent à l'ouverture ; les chiffres changent en douceur (pas de clignotement).
- **Aucune information du F3 actuel introuvable** : tout ce que la vue normale ne montre pas est dans la vue détaillée (F3 + Tab) ; les lignes des autres mods vont au panneau « Mods ».
- `reducedDebugInfo` respecté, nos lignes Lost Island comprises (mêmes règles que vanilla).
- Usure et panne seulement pour l'Ingénieur et l'équipe (`RoleGate.canDoEngineerWork`), **filtrées côté serveur** ; les autres voient « en marche / en panne ».
- Camembert du profileur inchangé. F3 + B, F3 + H, F3 + T, etc. marchent comme avant.
- Textes dans `fr_fr.json` et `en_us.json` du mod. Aucune classe cliente atteignable depuis un chemin serveur.
- Lancements du jeu annoncés à Killian ; arrêt par PID exact ; aucun client laissé ouvert ; `run/options.txt` jamais modifié (empreinte avant/après), essais en français dans `run-titre/`. Gradle `--offline`, aucun téléchargement.

## Review Focus

1. Un mod qui ajoute des lignes au F3, ou une ligne vanilla de format inattendu : elle apparaît au panneau « Mods » ou dans la vue détaillée, jamais perdue. Testé en tâche 1 (`uneLigneInconnueVaAuPanneauMods`, `toutesLesLignesSontQuelquePart`).
2. `reducedDebugInfo` actif : ni coordonnées, ni cible, ni lignes Lost Island qui situeraient le joueur. Testé en tâche 1 et vu en tâche 5.
3. Un Citoyen regarde une machine : la réponse du serveur ne contient pas l'usure (vérifié côté serveur, pas seulement masqué au client). Testé en tâche 3 (GameTest `unCitoyenNeRecoitPasLUsure`).
4. Petite fenêtre ou grande échelle d'interface : les panneaux ne se chevauchent pas, se réduisent ou passent dans la vue détaillée. Testé en tâche 2 (`petiteFenetre`).
5. Requêtes du F3 en continu : au plus 4 par seconde par joueur, distance de portée vérifiée côté serveur, aucune requête F3 fermé. Testé en tâche 3.

## Décisions prises par ce plan (à signaler à Killian)

| Sujet | Décision | Pourquoi |
|---|---|---|
| Source du texte | Les lignes de Minecraft et Forge, reclassées, jamais recalculées | Rien ne diverge du F3 vanilla, les mods restent visibles |
| Vue détaillée | F3 + Tab, avec repli sur une touche configurable « Vue détaillée du F3 » si la réflexion échoue | Combo libre en 1.20.1 ; le repli évite un F3 qui se ferme tout seul |
| Données Lost Island | Paquet requête/réponse au plus 4 fois par seconde, seulement F3 ouvert et pour le bloc regardé | Le client ne connaît ni l'usure, ni la zone côtière, ni le compteur |
| Repli des panneaux | Écran trop petit : Position, Performances et Cible restent, le reste passe dans la vue détaillée | Lisibilité d'abord |

---

### Task 1: Classement des lignes (logique pure)

**Files:** Create `src/main/java/dev/oneforallsurvival/client/debug/DebugLines.java` ; Test `src/test/java/dev/oneforallsurvival/client/debug/DebugLinesTest.java`

**Interfaces:**
- Produces: `enum Panel { POSITION, WORLD, PERFORMANCE, TARGET, MODS, DETAIL_ONLY }` ; `record Classified(Map<Panel, List<Line>> panels)` ; `record Line(String key, String text)` (clé stable pour l'animation des chiffres, ex. `xyz`, `fps`, `biome`) ; `static Classified classify(List<String> left, List<String> right, boolean reduced)`.
- Règles : par préfixe des lignes vanilla 1.20.1 (lire `DebugScreenOverlay.getGameInformation/getSystemInformation` par javap pour la liste exacte des formats : `XYZ:`, `Block:`, `Chunk:`, `Facing:`, `Client Light:`, `Server Light:`, `Biome:`, `Local Difficulty:`, `Minecraft 1.20.1`, `C:`/`E:`, `Java:`, `Mem:`, `CPU:`, `Display:`, `Targeted Block:`, `Targeted Fluid:`, `Targeted Entity:`, propriétés et tags `#…`, etc.) ; toute ligne non reconnue → `MODS` ; lignes rares (shaders, tags, CPU, display…) → `DETAIL_ONLY`.
- [ ] Tests d'abord : `chaqueLigneVanillaASonPanneau` (jeu de lignes réelles relevées par javap), `uneLigneInconnueVaAuPanneauMods`, `toutesLesLignesSontQuelquePart` (aucune perte), `enModeReduitRienNeSitueLeJoueur`, `lesClesSontStables` (deux relevés successifs, mêmes clés).
- [ ] Implémenter ; `./gradlew --offline test` vert (comptes lus dans le XML) ; commit `feat(f3): classement des lignes du F3`.

### Task 2: Mise en page et animations (logique pure)

**Files:** Create `client/debug/DebugLayout.java`, `client/debug/SmoothNumber.java`, `client/debug/PanelMotion.java` ; Tests correspondants.

**Interfaces:** `DebugLayout.place(int guiW, int guiH, Map<Panel, Size> sizes, boolean detail) → Map<Panel, Rect>` (colonnes gauche/droite, marges 4 px, écart 4 px, aucun chevauchement, repli de la règle « Repli des panneaux ») ; `SmoothNumber` (approche exponentielle, constante de temps 0,15 s, saut direct au-delà d'un écart relatif de 50 % pour les coordonnées) ; `PanelMotion` (ouverture : glissement de 6 px et fondu sur 0,25 s, décalage de 0,04 s par panneau ; fermeture immédiate).
- [ ] Tests : `aucunChevauchement` (balayage de tailles), `petiteFenetre` (320x240 : repli appliqué), `leChiffreSuitSansClignoter`, `uneTeleportationSauteDirectement`, `lesPanneauxArriventEnDecale`.
- [ ] Implémenter, tests verts, commit `feat(f3): mise en page et animations du F3`.

### Task 3: Données Lost Island (réseau, serveur)

**Files:** Create `src/main/java/dev/oneforallsurvival/debug/IslandDebugRequest.java`, `IslandDebugResponse.java` (enregistrés sur `BenchNetwork.CHANNEL` comme `SubscriberNamePacket`), `debug/IslandDebugInfo.java` (record + collecte côté serveur) ; client `client/debug/IslandDebugCache.java` ; GameTests `src/devcontent/java/dev/oneforallsurvival/dev/IslandDebugGameTests.java`.

**Interfaces:** requête `(BlockPos target or null)` ; réponse `IslandDebugInfo(coastZone, machine: state, wearPercent?, failure?, meter: subscriber?, index?, breaker: closed, sealed)` ; `wearPercent` et `failure` remplis **seulement** si `RoleGate.canDoEngineerWork(player)` ; cible ignorée au-delà de la portée du joueur + 1 bloc ou dans un chunk non chargé ; au plus 4 requêtes/s par joueur (au-delà : ignorées) ; le client n'envoie que F3 ouvert, au plus 4 Hz, et seulement si la cible ou la seconde change ; si `showOnlyReducedInfo()`, il n'envoie rien.
- [ ] GameTests d'abord : `unIngenieurRecoitLUsure`, `unCitoyenNeRecoitPasLUsure`, `uneCibleHorsDePorteeEstIgnoree`, `lesRequetesEnRafaleSontLimitees`, `laZoneCotiereEstDonnee`.
- [ ] Implémenter ; `./gradlew --offline test` et `runGameTestServer` (« All N required tests passed ») ; commit `feat(f3): données Lost Island du F3`.

### Task 4: Dessin des panneaux, vue détaillée, graphes

**Files:** Create `client/debug/DebugHud.java` (abonné `Dist.CLIENT` : `DebugText` en `LOWEST`, lit, classe, vide les listes, dessine), `client/debug/DebugPanels.java` (rendu des cartes), `client/debug/DebugKeys.java` (F3 + Tab, réflexion `handledDebugKey`, repli `KeyMapping`), `client/debug/DebugGraphs.java` (annule `FPS_GRAPH` Pre, dessine FPS et TPS solo depuis `Minecraft.getFrameTimer()` / le `FrameTimer` du serveur intégré) ; lang fr/en (titres de panneaux « Position », « Monde », « Lost Island », « Performances », « Cible », « Mods », libellés Lost Island, « Vue détaillée ») .
- [ ] Rendu fidèle à la direction A (couleurs des contraintes), FPS en grand, petit graphe de temps d'image dans Performances, chiffres lissés (`SmoothNumber`), entrée animée (`PanelMotion`), F1 (`hideGui`) masque comme vanilla, état GL de Minecraft respecté (rien laissé modifié après le dessin).
- [ ] Tests unitaires de ce qui est pur (par ex. formatage des valeurs Lost Island selon la réponse et le mode réduit) ; tests verts ; GameTests verts ; commit `feat(f3): nouveau F3 Lost Island`.

### Task 5: Captures de contrôle, preuve en jeu, vérification

**Files:** Create `src/devcontent/java/dev/oneforallsurvival/dev/F3Shots.java` (propriété `-PdebugShots=<dossier>` : monde d'essai `lostisland-essai-f3`, F3 ouvert, captures de jour et de nuit, en regardant une machine du mod en Ingénieur puis en Citoyen, gamerule `reducedDebugInfo` vrai puis faux, vue détaillée F3 + Tab, graphes F3 + Alt, F3 + B toujours actif, puis `termine.json` et arrêt propre) ; Create `docs/verifications/chantier-3-menu-f3.md` ; Modify `tasks/todo.md` ; déplacer ce plan dans `plans/archive/`.
- [ ] Annoncer à Killian, puis lancer dans `run-titre/` (français) et dans `run/` (empreinte de `options.txt` avant/après) ; regarder chaque capture ; écrire la vérification (rien de plus fort que les preuves, liste « Non prouvé ») ; commit et push.
