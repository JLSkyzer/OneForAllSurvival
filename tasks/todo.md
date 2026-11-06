# Suivi : One For All Survival

Cahier des charges : [features Lost Island](../docs/superpowers/specs/2026-11-06-features-lost-island-design.md).

Chaque lot reçoit son plan détaillé au moment de démarrer : ce qu'un lot prouve peut changer le suivant.

## Feuille de route

- [x] **Lot 0 : socle et rôles.** [Plan détaillé](../docs/superpowers/plans/2026-11-06-lot-0-socle-roles.md). Machine de base, bloc en ligne, preuve sur les trois réseaux, système de rôles. Réseaux prouvés depuis une source créative seulement : voir [la vérification](../docs/verifications/lot-0.md).
- [x] **Lot 1 : établi d'ingénieur.** [Plan détaillé](../docs/superpowers/plans/2026-11-06-lot-1-etabli-ingenieur.md). Retrait des recettes, interface, file d'attente, annulation, stock, JEI. Prouvé en GameTests, par un film et par un vrai client sur serveur dédié, avec les limites de la liste « Non prouvé » de [la vérification](../docs/verifications/lot-1.md) (rôle Citoyen sur serveur dédié, `/reload` avec JEI, multijoueur). Reste ouvert : blocage intermittent du client d'essai à la connexion, mécanisme relevé au lot 2 ([vérification](../docs/verifications/lot-2.md)).
- [x] **Lot 2 : métier de réparation.** [Plan détaillé](../docs/superpowers/plans/2026-11-06-lot-2-metier-reparation.md). Features 1, 2, 4, 3 : usure et pannes, trousse de diagnostic, débris d'avion, corrosion et bain galvanique, détection de l'eau salée et ses zones. Prouvé en GameTests, par un film et par un vrai client sur serveur dédié, relance du serveur comprise : voir [la vérification](../docs/verifications/lot-2.md). Sur la branche `lot-2`, pas fusionné : Killian décide après le film. Reste ouvert : équilibrage en jeu, Citoyen sur serveur dédié, multijoueur, FE d'un autre mod dans le poste, câblage réel, assemblages séquencés vus en jeu ; le GameTest `laProgressionEtLaReserveSurviventAuRechargement`, intermittent ; le blocage du client à la connexion, dont le mécanisme est maintenant relevé, sans correctif appliqué (même document).
- [ ] **Lot 3 : électricité vitale.** Features 9, 10, 11 : dessalinisateur, aliments périssables et armoire, séchoir. Le plus gros risque technique : la péremption touche tous les inventaires.
- [ ] **Lot 4 : gestion du réseau.** Features 7, 8, 5, 6 : délestage, compteurs et scellés, foudre et parafoudre, station météo. S'appuie sur le bloc en ligne du lot 0.
- [ ] **Lot 5 : vivres et protection.** Features 12, 13, 16 : couveuse, cuisine de cantine, clôture électrique.
- [ ] **Lot 6 : vie de la ville.** Features 25, 26, 17 : chauffe-eau et douche, salle de projection, phare et projecteurs. Ajoute aussi l'effet Bien-être.
- [ ] **Lot 7 : exploration.** Features 21, 22, 23, 24 : détecteur de métaux, lampe frontale, récepteur de signaux, mécanismes des ruines.

Les visuels finaux font partie de chaque lot qui ajoute un bloc ou un objet livré.

## Idées pour plus tard

- **Config des recettes de l'établi par mod** (demande de Killian, 2026-11-06) : un fichier de config, par exemple JSON, associant un mod id à une liste de types de recettes séparés par des virgules. Au chargement, les recettes de ces types partent à l'établi. À cadrer : seuls les types à ingrédients simples et résultat unique peuvent se payer d'avance ; les fluides et les résultats aléatoires (`create:mixing`, `create:milling`) ne le peuvent pas tels quels.

## Hors du mod, côté pack

- [ ] Retirer les PC, CPU et cartes graphiques d'Advanced Electricity, et les réacteurs nucléaires de New Age.
- [ ] Figer les versions des mods du serveur.
