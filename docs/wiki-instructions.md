# Mettre à jour le wiki Lost Island : fiche pour les agents

Tu mets à jour le wiki des joueurs (<https://jlskyzer.github.io/OneForAllSurvival/>). Fais **tout** ce qui suit.
Le wiki sert à comprendre puis à agir en jeu : **les images d'abord, le texte court**. La maquette validée le
27/11/2026 et les chapitres publiés (Téléphone, Électricité, Économie) sont la référence.

## 1. Quand le mettre à jour

- Un changement livré modifie un geste, un chiffre, une recette, un droit, un message ou un écran visible.
- Un élément « Prévu » arrive en jeu : il devient un article avec ses captures.
- Dans le même travail que le changement, pas « plus tard ».

## 2. Règles qui ne se négocient pas

- Français avec accents ; jamais « SolarTown ». On tutoie le joueur ; les notes d'équipe parlent à l'équipe.
- **Rien d'inventé** : chaque affirmation vérifiée dans le code ; chaque image du jeu est une vraie capture. Ce qui
  ne se montre pas (la voix, une scène à deux vrais joueurs) se dit, sans image fabriquée.
- **Rien de perdu** : chiffres, gestes, limites et notes « Pour le staff » restent (condensés au besoin, jamais
  supprimés). `check_wiki.py` le contrôle contre `wiki/tools/fiches-v1.json` : ne le régénère jamais.
- Un article « En jeu » a au moins une capture par notion. Chapeau ≤ 160 caractères, texte d'une étape, d'un repère
  ou d'un état ≤ 220.
- `wiki/` est **publié dès la poussée sur `main`** : on ne pousse qu'un état cohérent et relu. Rien de privé dedans.
- Aucun changement du mod (`src/main`) au passage : un défaut vu se note dans `docs/verifications/`.

## 3. Où est quoi

| Chemin | Rôle |
|---|---|
| `wiki/data/<chapitre>.json` | Contenu (version 2 : `articles`, `planned` ; un fichier en version 1 reste affiché) |
| `wiki/data/accueil.json` | Accueil : actions « Qu'est-ce que tu veux faire ? », couvertures, « Vu en jeu », règles |
| `wiki/data/objets.json`, `recettes.json`, `reperes.json` | **Produits** par la conversion : jamais à la main |
| `wiki/shots/`, `wiki/img/objets/` | Visuels publiés (produits par la conversion) |
| `wiki/index.html`, `app.js`, `blocks.js`, `style.css` | Moteur (petites retouches seulement ; puis `--versions`) |
| `wiki/tools/visuels.json` | Une entrée par visuel : source, recadrage, largeur, séquence, repères |
| `wiki/tools/build_visuals.py`, `check_wiki.py`, `shoot_wiki.py` | Conversion, contrôles, captures des pages |
| `src/devcontent/java/dev/oneforallsurvival/dev/wiki/` | L'atelier de captures (`WikiStudio`, une classe `Scenes<Chapitre>`) |
| `docs/superpowers/plans/2026-11-27-refonte-wiki.md` | Format détaillé des blocs (tableau « Format des données ») |

## 4. Écrire un article

Un article : `id`, `title`, `group`, `lead`, `cover`, `chips` (qui fabrique, pose, utilise, casse), `covers`
(anciennes fiches couvertes), `blocks`, `staff`. Blocs disponibles : `hero` (grande image à repères), `step`
(étapes, `actor` pour « qui joue »), `row`, `shot`, `clip` (animation), `recipe`, `items`, `states` (en marche,
sans courant, en panne), `phones`, `player` (pas à pas), `flow`, `numbers`, `problems` (« Si ça bloque »), `pass`,
`power`, `range`, `chunk`, `text` (avec `link` facultatif vers un autre article). Leurs champs : le tableau du
plan.

Structure qui marche : image d'ouverture à repères → recette en grille → les gestes un par un, chacun avec son
image ou son animation → états comparés → chiffres en pastilles → « Si ça bloque » avec les messages exacts.

## 5. Prendre les captures

- Écris ou complète une scène dans `Scenes<Chapitre>` (préparation côté serveur, caméra, vrai clic, prise, repères
  projetés, séquences ; exemple dans `ScenesControle` et les trois chapitres). Décor d'île, objet centré.
- Les écrans du téléphone viennent des modes de `TelephoneShots` :
  `runClient -PwithMcef -PtelephoneShots=frames-wiki-<mode> -PtelephoneShotsMode=<mode> -PtelephoneShotsLangue=fr_fr --args=--username=Robin`.
- L'écran titre, le menu F3, la liste des joueurs et le chargement viennent de `runTitleFr` (dossier `run-titre`, en
  français) avec `-PtitleShots`, `-PdebugShots`, `-PtabShots` ou `-PsceneFrames`.
- **Lancer Minecraft** uniquement par le lanceur, avec l'outil PowerShell :
  `& ".\tools\minecraft\lancer.ps1" -Libelle "wiki, <chapitre>" -Gradle "runWikiStudio -PwikiSeul=<chapitre>[:<scène>] --args=--username=Robin"`
  Il tient le verrou commun (`%USERPROFILE%\.claude\lancement-minecraft.txt` et sa file `…-attente.txt`) et le coupe-circuit qui
  ferme le client s'il prend la souris ou le premier plan. Code 2 : arrête-toi, lis le journal, comprends avant de
  relancer. Un seul Minecraft à la fois, toutes sessions confondues. Jamais `run/options.txt`, Prism, F10, une page
  extérieure, un téléchargement hors Gradle, ni la JVM d'un autre projet.
- Le joueur des captures s'appelle **Robin** : ajoute `--args=--username=Robin` à chaque commande Gradle de captures
  (atelier et `TelephoneShots`). Sans lui, le client s'appelle « Dev » et les images ne vont plus avec les autres.
- Un échec de scène ne change pas le code de Gradle : lis `[wiki] terminé` au journal et `captures/termine.json`.
- Convertis : `python wiki/tools/build_visuals.py` (complète `visuels.json` d'abord ; `--partiel` si des sources
  manquent exprès). Une animation attend des images nommées `f0000.png` (renomme au besoin les `frame_00000.png`) ;
  une longue animation rend la conversion lente (plusieurs minutes).

## 6. Vérifier chaque affirmation dans le code

Les anciennes fiches, un cahier des charges ou ta mémoire **ne suffisent pas**. Pour chaque consigne :

- Le geste exact : quoi en main, debout ou **accroupi**, quel côté, clic droit où ; ce qui se passe debout.
- Qui peut : métier, propriétaire, maire, équipe, abonné ; et le message exact du refus (`fr_fr.json`, mot pour mot).
- Toutes les étapes d'un parcours, y compris les évidentes (alimenter la prise, accepter des deux côtés, prendre le
  téléphone en main pour répondre).
- Les chiffres **réels** : consommation avec tout ce qui s'ajoute (cartes, appels), rendements d'usure, plafonds, et
  ce qui arrive au surplus ; les valeurs par défaut se disent comme telles.
- Casser : avec quel outil sans perdre le bloc (`requiresCorrectToolForDrops`).
- Les textes à taper : un pseudo quand le code cherche un joueur.
- Quand le code ne suffit pas, essaie dans l'atelier. Si le jeu contredit le texte, le jeu a raison.

## 7. Regarder chaque image

Ouvre **chaque** image publiée (animations image par image, avec Pillow) en face de sa légende :

- elle montre bien ce que dit la légende, et rien d'autre (pas le succès d'avancement vanilla à la place d'un
  bandeau du jeu, pas deux fichiers identiques) ;
- rien en anglais, aucun bandeau, bulle ou objet d'essai parasite, aucune demi-ligne coupée, infobulles entières ;
- chaque repère sur ce qu'il nomme et jamais sur un texte ; lisible à **390 px** (recadre sur l'utile) ;
- les couleurs des voyants décrites comme elles sont ; les blocs créatifs nommés comme tels ;
- une démonstration (tuyaux, courant) tient plusieurs minutes en partant de rien.

## 8. Contrôler, relire, publier

```powershell
python wiki/tools/check_wiki.py
python wiki/tools/check_wiki.py --versions
python wiki/tools/shoot_wiki.py
python wiki/tools/shoot_wiki.py --essai-interactif
```

- Tout doit être vert : 0 erreur, 0 défaut (captures dans `build/wiki-apercu/`, regarde-les).
- `./gradlew --offline test` si du Java partagé change.
- **Relecture par un autre agent**, en lecture seule, avec la consigne de vérifier chaque geste contre le code et
  chaque image face à sa légende (deux relecteurs au-delà d'une dizaine d'articles). Chaque relecture de la refonte
  a trouvé des consignes fausses : ne publie pas sans elle. Corrige, puis recontrôle.
- Commit par chemin (jamais `git add -A`, `-N` ou `.`), avec le trailer du dépôt ; jamais `art/`, `docs/assets/`,
  `.superpowers/`, `run*/`.
- Pousse, attends le workflow « Wiki » (`gh run watch`), puis ouvre la page publiée.
- Si le déploiement échoue avec « No artifacts named github-pages » alors que l'envoi a réussi, c'est une course
  côté GitHub : `gh workflow run wiki.yml --ref main`, puis revérifie.

## 9. Finir

- Note les écarts mod ↔ texte dans `docs/verifications/`, les pièges nouveaux dans `tasks/lessons.md` et **ici**
  (section suivante).
- Dis ce qui n'a pas été prouvé en jeu.

## 10. Erreurs déjà faites (27/11/2026) : ne les refais pas

- « Poser un connecteur sur l'antenne » sans dire de s'accroupir : debout, le clic ouvre le panneau.
- « 32 tr/min suffisent » : vrai seulement sans carte SIM ; chaque carte ajoute de la consommation.
- Compteur : oublier que le courant ne passe qu'après jumelage, accord de l'abonné **et** « Accepter » du
  fournisseur ; faire taper un nom de rôle à la place du pseudo.
- Plafond des quêtes : « la quête attend lundi » alors que le surplus est perdu.
- Sécher un fruit sans sel « pour le conserver » : frais, il ne périmait pas.
- Montage de douche qui fuyait par un tuyau ouvert, démontré avec des cuves préremplies.
- Message cité qui n'existe pas ; image vide après un recadrage ; icônes anglaises dans une liste ; outil de capture
  qui ne chargeait pas les images différées.
- « Au lendemain » pour des gestes qui prennent effet **tout de suite** et sans retour (supprimer un opérateur,
  changer de fondateur) : dis lesquels attendent minuit et lesquels sont immédiats.
- « L'ingénieur déplace le tableau » : il peut le casser, pas le reposer. Casser et reposer sont deux droits.
- Commandes inutilisables telles qu'écrites : préfixe manquant, guillemets obligatoires pour un nom à espaces,
  confirmation (clic puis Entrée) oubliée.
- Un chapitre « Prévu » dont l'intro dit « Déjà en jeu » : renvoie plutôt à ce qui existe ailleurs.
- Une image dont l'événement annoncé n'est pas visible (l'éclair de l'« impact »), un logo coupé, deux couvertures
  identiques, des pseudos d'essai ridicules, des repères posés sur des boutons.
- Une légende d'animation qui décrit un autre geste que celui filmé : regarde la séquence, pas le script.
- Un tuto prêt pour le soir même, écrit d'après le code seul : essaie les gestes en jeu.
- (28/11) Des captures reprises sans `--args=--username=Robin` : le joueur y était « Dev », toutes relancées.
