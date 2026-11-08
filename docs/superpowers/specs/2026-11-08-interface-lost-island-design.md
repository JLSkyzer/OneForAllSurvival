# Interface Lost Island : écran de chargement, écran titre, menu F3

Statut : brouillon, en attente de l'accord de Killian. Cadré avec lui le 2026-11-08.

## Intention

Donner au mod (Forge 1.20.1-47.4.28) une identité visuelle Lost Island du premier instant au
jeu : une scène animée pendant tout le chargement, qui devient l'écran titre sans coupure, et un
menu F3 redessiné. Le serveur s'appelle Lost Island RP (jamais « SolarTown »).

Trois chantiers, construits dans cet ordre, chacun livrable seul :

1. **Moteur de scène et écran de chargement** (le plus risqué, il porte les deux autres).
2. **Écran titre**, qui réutilise le moteur de scène.
3. **Menu F3**, indépendant.

## Sources fournies par Killian

Non suivies par git (`art/` et `docs/assets/` ne sont jamais ajoutés au dépôt) :

- `docs/assets/lost-island-logo.png`, `docs/assets/lost-island-banniere.png`.
- `art/loading-screen/assets/` (57 Mo) : ciel (`sky/`), plans du décor par ambiance
  (`plates/` : `far_*`, `mid_*`, `fg_*`, `precrash_mid_*`, ambiances `sun`, `pre`, `night`,
  `fire`), mer animée de 48 images (`sea/`, 27 Mo), effets (`fx/` : éclairs, feux en bandes,
  fumées, pluie, halo, rayons de lune, crash d'avion image par image), interface (`ui/` : icône
  d'avion, lettres du logo `logo/letter_00_L.png` à `letter_09_D.png`, `glow.png`,
  `face_mask.png`), et `manifest.json` (sortie 1920x1080, plans 2112x1188, mer, positions du
  soleil, de la lune, des feux, des lettres du logo).
- `art/loading-screen/tools/compose.py` : **référence de l'assemblage**. Il compose chaque image
  à partir du temps et de la progression : ordre des calques, caméra (zoom et dérive),
  ambiances interpolées nuit → pré-aube → lever, feux, halos, fumée, braises, pluie, éclairs,
  chute de l'avion, logo (respiration, reflet), barre (traînée dorée et icône d'avion), texte.
  Durée 36 s ; impact à 4,4 s ; barre visible à 5,2 s. Les autres scripts (`bake_fx.py`,
  `render_plates.py`, `crash_track.py`…) expliquent comment les calques ont été produits.
- `art/loading-screen/preview/lost-island-chargement.mp4` : le résultat attendu.
- `art/loading-screen/blender/lost-island-scene.blend` : la scène d'origine.
- Exemple d'écran titre joint par Killian (CrazyTown) : référence d'ambiance seulement, rien de
  sa marque n'est repris.

## Chantier 1 : moteur de scène et écran de chargement

### Ce que Killian a décidé

- Notre écran s'affiche **dès le début du chargement**, fenêtre anticipée de Forge comprise,
  **sans écran Mojang ni écran Forge avant**. (Défaut constaté avec FancyMenu : l'écran rouge
  de Mojang d'abord, l'écran personnalisé seulement à la fin. À ne pas reproduire.)
- Le récit de la vidéo **se joue à son rythme** (36 s : chute de nuit sous la pluie, crash,
  feux, lever du jour), **puis reste sur l'aube** jusqu'à la fin du chargement.
- Tout reste animé, y compris sur l'aube : barre, texte, fumée, feux, eau, nuages, **feuillage**.
- La barre et le texte d'état **suivent le vrai chargement**.
- Le texte d'état est **thématique Lost Island, mais on comprend l'étape réelle**. Table
  validée par Killian (le détail réel entre parenthèses) :

| Étape réelle | Message |
|---|---|
| Démarrage de Forge | « Les moteurs toussent… (démarrage) » |
| Découverte des mods | « Inventaire de la soute… (recherche des mods) » |
| Chargement des mods | « Tri des caisses de l'épave… (mods 23/154 : create) » |
| Registres et configs | « Le camp s'organise… (registres) » |
| Textures et sons | « Exploration du littoral… (textures) » |
| Fin | « Bienvenue sur Lost Island. » |

### Ce que dit Forge 47.4.28 (lu par javap le 2026-11-08)

- `ImmediateWindowHandler.load` lit `earlyWindowProvider` dans `fml.toml`, puis cherche par
  `ServiceLoader` un `net.minecraftforge.fml.loading.ImmediateWindowProvider` dans la couche
  **SERVICE**, et garde celui dont `name()` égale ce réglage.
- `ModDirTransformerDiscoverer` range dans cette couche les jars du dossier `mods` qui déclarent
  ce service (au même titre que `ITransformationService`, `IModLocator`,
  `IDependencyLocator`).
- L'interface : `name`, `initialize`, `updateFramebufferSize`, `setupMinecraftWindow`,
  `positionWindow`, `loadingOverlay`, `updateModuleReads`, `periodicTick`, `getGLVersion`.
  `loadingOverlay` fournit l'overlay affiché pendant le chargement des ressources de Minecraft :
  le même fournisseur tient donc l'écran du premier instant jusqu'au menu.
- L'implémentation de Forge est dans `fmlearlydisplay-1.20.1-47.4.28.jar` (`DisplayWindow`) :
  modèle pour la création de la fenêtre GLFW et sa remise à Minecraft.

### Mes choix (Killian peut les refuser)

- **Un jar de service à part**, construit par le même dépôt (sous-projet Gradle), posé dans
  `mods/` à côté du mod. Le `fml.toml` du pack contient `earlyWindowProvider = "lostisland"`.
  Sans ce réglage ou sans ce jar, le jeu démarre normalement avec l'écran de Forge.
- **Distribution (réponse de Killian)** : le pack passe par son launcher maison, qui livre les
  deux jars et le `fml.toml`. Une publication future sur CurseForge ou Modrinth déclarerait le
  jar de service en dépendance. FancyMenu ne sera pas dans le pack : son défaut cité est une
  expérience passée, pas un mod à faire cohabiter.
- **Le moteur de scène est écrit en OpenGL direct** (LWJGL), sans classe de Minecraft, dans le
  jar de service : c'est la seule façon de dessiner avant que le jeu existe. L'écran titre le
  réutilise (chantier 2).
- **Les calques sont copiés** de `art/loading-screen/assets/` vers les ressources du jar de
  service, sans les retoucher ; `art/` reste hors du dépôt.
- **Chargement progressif** : le ciel et le décor d'abord (premier affichage en moins d'une
  seconde visé, mesuré et rapporté, pas promis), la mer et les effets ensuite, sans gel de
  l'image.
- **Si le chargement finit avant la fin du récit**, le récit accélère jusqu'à l'aube au lieu de
  couper net.
- **Une seule barre, toujours croissante**, de la phase Forge à la phase ressources de
  Minecraft : chaque phase reçoit une part de la barre, et la barre ne recule jamais.
- **Le feuillage ondule** grâce à un masque des feuilles par plan (`far`, `mid`, `fg`), tiré de
  la scène Blender, et à une légère déformation au vent dans le rendu. Ce masque est un calque
  nouveau, à fabriquer.
- Après 100 %, la barre s'efface et l'écran titre prend la suite sur la même image.
- **Calques copiés au build** : la construction du jar de service les lit dans `art/` (non
  suivi par git) ; le dépôt ne les contient pas.
- **OpenGL visé** : le même que la fenêtre de Forge (3.2 core). Si l'initialisation du moteur
  échoue (contexte, shader, texture), retour en bloc à l'écran de Forge ; si un seul effet
  échoue (feuillage, pluie…), il est retiré et le reste s'affiche.
- **Poids (décision de Killian)** : aucune limite de taille et aucune recompression. Les calques
  sont livrés tels quels, en pleine qualité, les 48 images de mer comprises. Le poids final est
  seulement rapporté.
- **Partage de la barre** : parts fixes par phase, tirées des durées mesurées sur un vrai
  démarrage avec le pack, puis lissage pour qu'elle avance sans à-coups et ne recule jamais.
- **Masque du feuillage** : produit depuis la scène Blender (Blender est disponible par MCP sur
  la machine de Killian) avec la caméra de `render_plates.py` ; à défaut, dérivé des plans par
  la couleur. Le constructeur dit lequel il a pu faire.

### Laissé au constructeur

- Réutiliser `DisplayWindow` de Forge pour la gestion de la fenêtre (en la déléguant ou en
  s'en inspirant) plutôt que la réécrire, tant que la remise de la fenêtre à Minecraft se fait
  comme chez Forge.
- Les sources de progression : notifications de démarrage de FML pendant la phase Forge,
  progression du rechargement des ressources ensuite. Le constructeur vérifie lesquelles sont
  lisibles et fiables.
- La police du texte (celle de Forge, ou la police bitmap de Minecraft lue dans le jar du
  client, comme `compose.py`).
- Le fonctionnement en environnement de développement (`runClient`) : le jar de service doit
  pouvoir y être chargé, ou le plan dit comment l'essayer.
- Les reproductions par rapport à `compose.py` : le rendu en jeu doit en être fidèle, pas
  identique au pixel.

## Chantier 2 : écran titre

### Ce que Killian a décidé

- **Épuré, disposition « cinéma à gauche »** (maquette B) : le logo en haut à gauche, comme
  dans la scène ; les boutons empilés dessous, à gauche ; l'épave et le décor visibles à droite ;
  léger assombrissement à gauche pour la lisibilité.
- **Boutons** : Solo, **Rejoindre l'île** (doré, mis en avant), Options, Quitter le jeu.
  **Pas de Multijoueur.** Rejoindre l'île se connecte directement au serveur ; l'adresse sera
  écrite en dur quand Killian l'aura.
- **Fond : la scène animée**, sur l'aube en boucle : la dernière image du chargement, vivante
  (mer, épave qui fume, nuages, feuillage, logo qui respire). Passage du chargement au menu
  sans coupure.
- **Les boutons des autres mods** posés sur l'écran titre doivent rester visibles, cliquables et
  bien rangés.

### Mes choix (Killian peut les refuser)

- **Tant que l'adresse est vide**, Rejoindre l'île est grisé, avec l'infobulle « Serveur bientôt
  disponible ». L'adresse est une constante du code, pas un réglage.
- **On garde l'écran titre de Minecraft et on le réarrange**, au lieu de le remplacer par un
  autre écran : les mods qui ajoutent leurs boutons quand le titre s'ouvre continuent de le
  faire. Multijoueur, Realms et sa notification sont retirés.
- **Rangée discrète en bas à gauche** : langue, accessibilité, liste des mods de Forge, et tout
  bouton ajouté par un autre mod (repéré parce qu'il n'est pas à nous ni à Minecraft), replacé
  sans chevauchement. Rien n'est caché.
- **En bas, en petit** : version de Minecraft et mention de Mojang (obligatoire). La phrase
  jaune qui clignote (splash) est retirée.
- **Connexion** : Rejoindre l'île ouvre la connexion standard de Minecraft vers la constante
  (adresse et port), sans l'ajouter à la liste des serveurs ; un refus s'affiche par l'écran
  d'erreur habituel de Minecraft.
- **Mixins permis** : le mod n'en a pas aujourd'hui. Les événements de Forge d'abord ; un mixin
  seulement là où ils ne suffisent pas, avec sa raison écrite dans le plan.

## Chantier 3 : menu F3

### Ce que Killian a décidé

- **Refonte complète** : couleurs, graphisme, animations. Fini le texte brut sur fond gris.
- **Direction A, « panneaux en verre ambré »** : cartes sombres translucides aux couleurs du
  logo, titres ambrés, rangées par thème à gauche et à droite.
- Le choix des informations est laissé à Claude ; tout ce qui est utile reste lisible.

### Mes choix (Killian peut les refuser)

- **Panneaux** : Position (X, Y, Z en couleurs, bloc, chunk, direction), Monde (biome, jour et
  heure, lumière, météo, dimension), **Lost Island** (zone d'eau salée, machine regardée et son
  usure, réseau), Performances (FPS, temps d'image avec petit graphe, mémoire, TPS et ping en
  multijoueur), Cible (bloc ou entité visé, id, propriétés principales, distance).
- **Animations** : les panneaux glissent et apparaissent à l'ouverture ; les chiffres changent en
  douceur au lieu de clignoter.
- **Vue détaillée** : une combinaison de touches (à choisir parmi celles que Minecraft 1.20.1 ne
  prend pas, F3 + Tab par défaut) déplie tout ce que donne le F3 actuel (propriétés complètes,
  tags, détails du chunk), dans le même style.
- **Lignes des autres mods** : ce que les mods ajoutent au F3 par l'événement de Forge est
  affiché dans un panneau « Mods », pas perdu.
- **`reducedDebugInfo`** (règle de jeu) est respecté : coordonnées et données masquées comme
  dans le F3 actuel. Un serveur RP s'en sert.
- **Les graphiques** (F3 + Alt, camembert du profileur) sont redessinés dans le même style.
  *Décision de Killian (2026-11-08)* : le camembert du profileur (F3 + Maj) reste tel quel ; le
  redessiner demanderait un mixin, que le mod n'a pas. Seuls les graphes de FPS et de TPS sont
  redessinés.
- *Décision de Killian (2026-11-08)* : dans le panneau Lost Island, **l'usure et la panne** de la
  machine regardée ne s'affichent que pour l'Ingénieur et l'équipe du serveur (diagnostiquer reste
  leur travail) ; les autres joueurs voient l'état « en marche / en panne » déjà visible sur le bloc.
  Zone d'eau salée et réseau restent visibles par tous.
- Les combinaisons F3 + touche existantes (F3 + B, F3 + H, F3 + T…) continuent de marcher.
- **Remplacement du rendu** : le texte vient toujours de Minecraft et de l'événement de Forge
  (rien n'est recalculé à part), seul le dessin change ; un mixin sur l'écran de debug est
  permis si l'événement ne permet pas de taire le dessin d'origine et ses graphes.

### Laissé au constructeur

- Où lire chaque donnée côté client. Le bloc Lost Island n'affiche que ce que le client sait
  déjà, ou ce qui lui est déjà envoyé ; s'il faut un paquet de plus pour l'usure, le plan le dit.

## Non-objectifs

- Toucher aux écrans autres que chargement, titre et F3 (pause, options, mort…).
- Une config pour l'adresse du serveur.
- Un écran de chargement entre deux mondes ou à la connexion au serveur.
- Reproduire au pixel la vidéo de `compose.py`.

## Anti-objectifs

- Un écran Mojang ou Forge visible, même un instant, avant le nôtre, quand le pack est bien
  configuré.
- Un démarrage cassé si le jar de service manque, si `fml.toml` n'a pas le réglage, ou si la
  carte graphique ne gère pas un effet : retour à l'écran de Forge, jamais un plantage.
- Un bouton d'un autre mod disparu ou inaccessible sur l'écran titre.
- Une information du F3 actuel devenue introuvable.
- Un chargement rendu sensiblement plus lent par la scène : la durée de démarrage est mesurée
  avec et sans, et rapportée.
- Le nom « SolarTown » ou la marque CrazyTown n'importe où.

## Contraintes du projet

- Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6 ; aucun téléchargement hors dépendances en cache.
- Aucune classe cliente atteignable depuis un chemin serveur ; le jar de service ne s'active que
  côté client.
- Textes dans `fr_fr.json` avec repli `en_us.json` (dans le jar de service, qui n'a pas les
  traductions de Minecraft, les messages viennent de ses propres fichiers).
- Les fenêtres de jeu gênent Killian : tout lancement de client est annoncé avant, et aucun
  client n'est laissé ouvert.
- Ne jamais écrire `fml.toml` avec `Set-Content -Encoding utf8` (BOM, Forge échoue à le lire).

## Preuve attendue

- Chantier 1 : un enregistrement d'un vrai démarrage avec le pack, du lancement au menu, qui
  montre notre scène dès la première image de la fenêtre, sans écran Mojang ni Forge ; la durée
  de démarrage avec et sans la scène ; le démarrage sans le jar et sans le réglage (écran de
  Forge, aucun plantage).
- Chantier 2 : captures de l'écran titre avec le pack, montrant les boutons de Create et des
  autres mods rangés et cliquables ; Rejoindre l'île grisé sans adresse.
- Chantier 3 : captures du F3 de jour, de nuit, sur une machine du mod, avec `reducedDebugInfo`,
  et de la vue détaillée.
- Tests automatiques pour la logique pure : table des messages, partage de la barre entre les
  phases (jamais de recul), horloge du récit (accélération et aube), rangement des boutons.
