# Refonte du wiki : ce que les captures ont montré

Défauts ou surprises vus en capturant le jeu pour le wiki. Ils ne se corrigent pas dans la refonte (`src/main`
intouché) ; chacun dit comment il a été vu.

## Téléphone (tâche 3, 27/11)

- **Connecteur sur l'antenne : il faut s'accroupir.** Connecteur de Crafts & Additions en main, un clic droit debout
  sur l'antenne ouvre le panneau de l'opérateur et ne pose rien (`AntennaBlock.use` répond à tout objet sauf le
  testeur). Accroupi, le connecteur se pose et prend seul le mode « push » ; sur la source (bloc d'énergie créative),
  il prend seul « pull ». Vu dans l'atelier (`ScenesTelephone`, scène `fil`, constats au journal). Le wiki et
  `export/2026-11-27/TUTO-antenne.md` le disent désormais. À décider : laisser passer le placement d'un connecteur
  tenu (comme le testeur) serait plus naturel pour les joueurs.
- **Alternateur à basse vitesse.** Un alternateur collé contre l'antenne, entraîné par un moteur créatif à
  32 tr/min, la tient « En marche » (réserve +100 FE par seconde pendant 40 s) ; à 16 tr/min, l'antenne n'a rien
  reçu du tout en 40 s (0 FE, « Sans courant »), alors que 22 FE/t étaient attendus d'après la configuration. Cause non
  cherchée (seuil de l'alternateur de Crafts & Additions probable, non vérifié). Scène `alternateur`.
- **Prise murale à travers le mur** : confirmé, un connecteur « aucun » posé sur le bloc qui porte la prise passe en
  « push » dès que la prise le voit, et le téléphone branché charge (16 920 FE relus 3 s après le branchement).
