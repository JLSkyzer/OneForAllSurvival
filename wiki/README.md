# Wiki Lost Island

Le wiki des joueurs et de l'équipe, publié par GitHub Pages (workflow `.github/workflows/wiki.yml`) à chaque
poussée sur `main` qui touche ce dossier.

Il est visuel : captures du jeu, animations, schémas manipulables ; le texte est court.

**Pour le mettre à jour, suis la fiche [docs/wiki-instructions.md](../docs/wiki-instructions.md)** (quand, comment
écrire un article, prendre les captures, vérifier, relire, publier).

- `data/*.json` : le contenu, un fichier par chapitre ; `objets.json`, `recettes.json`, `reperes.json` et `shots/`
  sont produits par `tools/build_visuals.py`.
- `index.html`, `style.css`, `app.js`, `blocks.js` : l'affichage, sans étape de construction.
- `tools/` : conversion des captures, contrôles, captures des pages.

Aperçu local : `python -m http.server -d wiki 8000`, puis http://localhost:8000.
