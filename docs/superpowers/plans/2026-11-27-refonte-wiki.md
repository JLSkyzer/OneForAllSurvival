# Refonte visuelle du wiki Lost Island

> **Exécution :** sous-agents, une tâche à la fois pour tout ce qui lance Minecraft, relecture en lecture seule
> après chaque tâche. Autonomie accordée par Killian le 27/11 (« en autonomie tu fais la refonte totale du wiki »,
> lancements du jeu libres).

**But :** un wiki qu'on comprend d'abord par l'image : captures du jeu, montages annotés, animations, schémas
manipulables ; le texte reste court.

**Maquette validée (27/11, « C'est juste parfait je valide ») :** <https://claude.ai/artifact/QphuL7GM6aXgiUCQUS1GeC>.
Sources locales : `build/maquette-wiki/` et le dossier `wiki-maquette/canvas/project/*.dc.html` du bac à sable de la
session. C'est la référence visuelle : couleurs, polices (Barlow et Barlow Condensed), composants, ton.

**Architecture :** site statique sans étape de construction (`wiki/`, publié par GitHub Pages à chaque poussée sur
`main` touchant `wiki/**`), contenu en JSON, visuels produits par un atelier de captures en développement
(`src/devcontent`) puis convertis par `wiki/tools/`.

## Contraintes pour toutes les tâches

- Français avec accents ; jamais « SolarTown ». Le wiki parle au joueur (tutoiement), les notes d'équipe à l'équipe.
- Aucune invention : chaque chiffre, geste et commande vient des fiches actuelles ou du code lu ; chaque image du jeu
  est une vraie capture. Ce qu'on ne peut pas montrer (voix, ce qui demande deux vrais joueurs) se dit, sans
  image fabriquée.
- Aucune information perdue : chaque fiche « En jeu » actuelle est couverte par un article (`covers`), les notes
  « Pour le staff » sont conservées (condensées au besoin, jamais supprimées).
- Visuel d'abord : un article « En jeu » a au moins une capture réelle par notion ; chapeau ≤ 160 caractères, texte
  d'une étape ≤ 220.
- Aucun changement de comportement du mod (`src/main` intouché). Un défaut vu en capturant se note dans
  `docs/verifications/refonte-wiki.md`, il ne se corrige pas ici.
- Images en WebP : image fixe ≤ 250 Ko, animation ≤ 1,5 Mo, wiki entier ≤ 80 Mo. Icônes rendues par le jeu.
- Captures en français (`fr_fr`), interface lisible, aucune bulle de débogage ni message parasite à l'écran.
- Lancements : verrou `%USERPROFILE%\.claude\lancement-minecraft.txt` et sa file, fenêtre sur le second écran,
  jamais la souris ni le premier plan (garde et coupe-circuit), `run/options.txt` intouché, pas de Prism, aucun
  téléchargement hors ce que Gradle fait seul, jamais arrêter la JVM d'un autre projet.
- Git : commit par chemin avec le trailer du dépôt, poussée après vérification ; jamais `art/`, `docs/assets/`,
  `.superpowers/`, `run*/`. On ne pousse sous `wiki/**` qu'un état cohérent (il est publié aussitôt).

## À surveiller en relecture

1. Une image qui montre autre chose que sa légende (mauvais état, mauvais bloc, repère à côté) : chaque image est
   regardée en face de son texte.
2. Un ancien lien (`#antenne`, `#etabli-ingenieur`…) qui n'ouvre plus rien.
3. Débordement horizontal ou texte illisible à 390 px de large.
4. Un écran capturé en anglais, ou à une échelle où le texte du jeu est coupé.
5. Une recette ou un chiffre qui dépend d'une configuration : le dire (« réglages par défaut »).

## Format des données

`wiki/data/<chapitre>.json`, version 2 :

```json
{"v": 2, "id": "telephone", "title": "Téléphone", "tagline": "…", "icon": "oneforallsurvival:phone",
 "cover": "shots/telephone/cover.webp",
 "articles": [{"id": "antenne", "title": "Poser une antenne", "group": "Le réseau", "lead": "…",
   "cover": "shots/telephone/antenne-service.webp", "covers": ["antenne", "tuto-antenne"],
   "chips": [{"k": "Fabrique", "v": "Ingénieur"}], "blocks": [], "staff": ["…"]}],
 "planned": [{"id": "facetime", "num": 94, "name": "FaceTime", "what": "…", "lot": "Lot T4"}]}
```

Un fichier encore en version 1 (`cards`) reste affiché, dans le nouveau style.

| Bloc | Champs |
|---|---|
| `hero` | `img`, `alt`, `points[]` : `{at | x,y, title, text}` |
| `step` | `title`, `blocks[]`, `actor?` (`toi` ou `autre`), `actorLabel?` |
| `row` | `blocks[]` (colonnes qui passent l'une sous l'autre) |
| `shot` | `img`, `alt`, `marks[]?` : `{at | x,y, text}`, `note?`, `title?` |
| `clip` | `img` (WebP animé), `alt`, `note?`, `title?` |
| `recipe` | `item` (la grille vient de `data/recettes.json`), `title?` |
| `items` | `list[]` : `{item, label?, text?}`, `title?` |
| `states` | `list[]` : `{img, alt, tone (ok, off, bad, info), title, text}` |
| `phones` | `list[]` : `{img, alt, points[]?, caption?}` |
| `player` | `steps[]` : `{img, alt, label, points[]?}`, `hint?` |
| `flow` | `nodes[]` : `{item? | img?, label}`, `title?` |
| `numbers` | `list[]` : `{k, v, item?}` |
| `problems` | `list[]` : `{msg, text, link?}` |
| `pass` | `item`, `text` (un objet qui change de mains) |
| `power` | calculateur du courant d'une antenne : `standby`, `perCard`, `perCall`, `speeds[]`, `feAt256` |
| `range` | curseur de portée : `item`, `bars[]` (seuils), `max` |
| `chunk` | schéma « même chunk » : `item`, `text` |
| `text` | `text` (court), `link?` (`#chapitre/article`, affiché « Voir : <titre> ») |

`at` désigne un repère nommé de `data/reperes.json`, écrit par la conversion à partir de la capture ; il survit à une
reprise de la capture. Le texte accepte `` `code` `` et `**gras**`.

Fichiers produits (jamais écrits à la main) : `data/recettes.json`, `data/objets.json` (identifiant → nom français et
icône), `data/reperes.json`, `shots/**`, `img/objets/**`.

## Atelier de captures

- Tâche Gradle `runWikiStudio` (dossier `run-wiki/`, ignoré par Git, MCEF préparé avec `-PwithMcef` ;
  `-PwikiSeul=<chapitre>[:<scène>]`), gardée comme les autres lancements automatiques ; sortie
  `run-wiki/captures/<chapitre>/<scène>.png` (et `.json` des repères projetés à l'écran), séquences dans
  `<scène>/f0000.png`, exports dans `_export/` (icônes, recettes, noms).
- Les écrans du téléphone viennent des modes existants de `TelephoneShots` quand ils conviennent (dossiers
  `run/frames-*`), de l'atelier sinon.
- Une classe de scènes par chapitre ; le socle (monde, temps, caméra, masquage de l'interface, ouverture d'écran,
  capture, repères) est commun.
- `wiki/tools/visuels.json` décrit chaque visuel publié (source, recadrage, largeur, repères) ;
  `python wiki/tools/build_visuals.py` produit les fichiers du wiki.

## Contrôles

- `python wiki/tools/check_wiki.py` : JSON et blocs connus, images, objets et repères présents, fiches « En jeu »
  toutes couvertes, limites de texte et de poids. Doit sortir en erreur sur un manque.
- `python wiki/tools/shoot_wiki.py [chapitre]` : sert le dossier en local, capture chaque page à 1280 et 390 px
  avec Edge sans fenêtre (`build/wiki-apercu/`), signale débordements, images cassées et erreurs de script.

## Tâches

- [x] **1. Moteur du wiki** : `index.html`, `style.css`, `app.js` (et `blocks.js`), accueil « Qu'est-ce que tu veux
  faire ? », pages de chapitre et d'article, recherche, vue Joueurs / Staff, « En jeu seulement », anciens liens,
  affichage des chapitres en version 1, `check_wiki.py`, `shoot_wiki.py`.
- [x] **2. Atelier de captures** : socle, `runWikiStudio`, exports des icônes, recettes et noms, `build_visuals.py`,
  une première scène de contrôle.
- [x] **3. Téléphone** : scènes, conversion, articles ; relecture ; publication du moteur et du chapitre.
- [x] **4. Électricité et ingénieur** : idem.
- [x] **5. Économie** : idem.
- [x] **6. Interface, Maire, chapitres à venir, accueil définitif.**
- [x] **7. Clôture** : relecture d'ensemble, anciennes images retirées, `wiki/README.md`,
  `docs/verifications/refonte-wiki.md`, `tasks/todo.md`.
- [x] **8. Fiche d'instructions des agents du wiki** (demande de Killian du 27/11) : tout ce qu'un agent doit faire
  pour mettre le wiki à jour (quand le faire, format, captures à l'atelier, lancements, exactitude contre le code,
  regard sur chaque image, contrôles, relecture, publication, pièges des relectures de cette refonte). Dans `docs/`
  (hors de `wiki/`, qui est publié), avec un renvoi depuis les instructions du projet pour qu'elle soit trouvée.
- [ ] **9. Base de connaissances** (demande de Killian du 27/11, autorise ces écritures dans `D:\BDC`) : une maquette
  du wiki (pages qui s'ouvrent seules, avec leurs images), une fiche sur le style (couleurs, polices, composants,
  principes) et la même fiche d'instructions, rangées ensemble comme idée de conception pour de futurs projets.
  Lire d'abord `D:\BDC\Accueil.md` et `Conventions.md`, chercher les notes existantes, commit et push du coffre.

## Choix faits par l'auteur du plan (à confirmer au retour)

- Thème sombre seul (celui de la maquette) ; l'ancien thème clair disparaît.
- Les contenus « Prévu » restent en texte court sans image (rien n'existe encore en jeu).
- Publication chapitre par chapitre, l'ancien contenu restant lisible entre-temps ; le téléphone passe en premier
  (essai sur serveur le soir du 27/11).
