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

## Électricité et ingénieur (tâche 4, 27/11)

Les fiches de la version 1 ont été relues contre le code (`src/main/java`, `data/`, `fr_fr.json`, classes de
configuration) avant d'être réécrites. Écarts corrigés dans le wiki :

- **Compteur nu : rien ne passe.** Depuis le lot E6, `MeterBlockEntity.isOpen` ne laisse passer le courant que par un
  compteur jumelé à un fournisseur et accepté ; la fiche du lot 4 décrivait un compteur qui « totalise les FE » posé
  sur le câble, et sa note d'équipe parlait encore d'une facturation « prévue ». Vu dans l'atelier (scène `abonne`) :
  compteur posé et inscrit, non jumelé, la maison derrière reste « Sans courant », 0 FE passés ; raccordé (raccordement
  d'essai `FournisseurDEssai`), 5 260 FE passés en quelques secondes. L'article le dit et renvoie au fournisseur.
- **Commandes de rôles.** La fiche disait tout réservé au niveau 2 ; `/role list`, `/role get` (soi-même) et
  `/role mayor` (voir le maire) sont ouverts à tous (`RoleCommands.register`).
- **Maire.** « Le statut de maire n'ouvre encore aucun geste » n'est plus vrai : l'économie et le téléphone lui en
  donnent (`TreasuryCommands`, `AntennaRules`, `PowerRules`, `TownBlockRules`).
- **Établi.** Les 20 FE/t ne sont payés que quand une tête de file est à cet établi (`wantsToWork`) ; l'écran affiche
  « À la main : vitesse x1 » tant que l'établi n'a rien à faire, même branché, et « Alimenté » pendant une fabrication.
- **Station météo.** Météo figée (`doWeatherCycle` à faux) : elle ne lit que le temps présent (`read`) ; ce n'est pas
  un défaut, mais la fiche ne le disait pas (vu dans l'atelier : pluie annoncée dans 2 500 tics, station restée au
  soleil tant que le cycle était coupé).
- **Débris.** Butins complets relevés dans `loot_tables/blocks/debris_*.json` (le fuselage donne aussi 3 à 6 pépites
  de fer, la cabine 0 à 1 ferraille…).

Constats en jeu (atelier, journal `[wiki] constat`) :

- Testeur sur un séchoir à 15 % : six lignes dans le chat (état, aspect, énergie, pièce à prévoir, corrosion).
- Réparation au poste à souder par un vrai clic droit, fusible dans la seconde main : séchoir de nouveau « En
  marche », état 100 %, poste passé de 2 000 à 1 600 FE.
- Éclair à 5 blocs de deux machines protégées par un parafoudre : le séchoir n'a rien perdu (seulement son usure
  normale), le parafoudre est passé de 100 à 90 points.
- Débris de câblage démonté en survie à la pioche de fer : le bloc part, des fils d'aviation tombent.
- Alternateur à 32 tr/min derrière un tableau de délestage, trois séchoirs en sortie : 20, 20 et 5 FE/t (45 FE/t), la
  troisième sortie « Partielle ».
