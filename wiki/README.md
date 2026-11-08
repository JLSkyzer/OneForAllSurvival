# Wiki Lost Island

Le wiki des joueurs et du staff, publié par GitHub Pages (workflow `.github/workflows/wiki.yml`).

- `data/*.json` : une fiche par bloc ou système, un fichier par chapitre. C'est le seul endroit à modifier
  pour changer le contenu.
- `img/` : textures copiées depuis le mod par `python wiki/tools/sync_images.py`. À relancer quand une fiche
  passe à `"status": "dispo"` avec une image.
- `index.html`, `style.css`, `app.js` : l'affichage, sans étape de construction.

Les fiches résument les cahiers de `docs/superpowers/specs/`. Quand un cahier change ou qu'un lot est livré,
mettre la fiche à jour dans le même commit : `"status": "dispo"`, l'image du bloc, les vraies commandes.

Aperçu local : `python -m http.server -d wiki 8000`, puis http://localhost:8000.
