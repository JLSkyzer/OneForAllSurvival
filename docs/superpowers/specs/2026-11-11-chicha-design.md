# Cahier des charges : la chicha

Date : 2026-11-11. Statut : en attente de validation par Killian.

Killian a demandé le 2026-11-11 un petit bloc de 1 × 1 : une chicha, modélisée dans Blender. Quand on
prend son tuyau, il reste relié au bloc par un fil, comme une canne à pêche, et on peut fumer à volonté.
Ses réponses sont regroupées en section 9. Ce cahier ne touche pas au code : le modèle Blender est fait
(`art/chicha/`), le code viendra au lot CH1.

## 1. Pourquoi

Un objet de détente pour les lieux de vie : un bar, une terrasse, un salon de la ville. Il fait se
réunir les joueurs autour d'un bloc et donne un petit commerce : le tabac.

**Critère de réussite : un joueur charge la chicha, prend le tuyau, voit le fil le relier au bloc, tire
une bouffée et souffle un nuage que les joueurs autour voient aussi.**

## 2. Objectifs et limites

### Objectifs
- Un **bloc de 1 × 1**, au modèle fait dans Blender.
- Un **tuyau relié au bloc par un fil**, dessiné comme la ligne d'une canne à pêche.
- Une **fumée** visible par tous, avec le bruit de l'eau qui bouillonne.
- Un **petit effet** si l'on fume trop d'affilée.

### Hors périmètre
- Tout bonus durable : la chicha ne soigne pas et ne rend pas plus fort.
- Plusieurs tuyaux sur une même chicha : un seul au départ.
- Une monnaie ou une vente intégrée : le tabac se vend entre joueurs, comme tout objet.

### Ce qui serait un échec, même si tout fonctionne
- Un tuyau qui reste dans l'inventaire d'un joueur parti loin, ou qui se duplique.
- Un fil qui part d'un autre point que le bloc ou que la bouche du joueur.
- Un effet qui gêne au point d'empêcher de jouer, ou qui tue.
- Un bloc qui fait voir à travers ses voisins (leçon du 2026-11-10 du dépôt).

## 3. Contraintes techniques

- **Le modèle** vient de Blender, en OBJ, chargé par le chargeur OBJ de Forge (`forge:obj`), avec une
  seule texture. Il tient dans le cube du bloc : rien ne dépasse.
- Le modèle n'occupe pas le cube entier : le bloc reçoit `noOcclusion()` et une forme de collision
  exacte, et sa capture le montre collé à un mur.
- Le vase est en **verre teinté translucide** : le rendu du bloc est de type translucide.
- **Le fil** est dessiné chez chaque joueur, entre le haut de la tige du bloc et la main du fumeur, sur
  le modèle de la ligne de la canne à pêche de Minecraft.
- Le serveur décide qui tient le tuyau, compte les bouffées et applique l'effet. Les joueurs ne font que
  dessiner le fil et la fumée.

## 4. Socle

### Le modèle Blender
Les sources sont dans `art/chicha/` :
- `outils/construire_chicha.py` : le script qui construit le modèle, la texture et les aperçus, lancé
  par `blender --background --factory-startup --python art/chicha/outils/construire_chicha.py` ;
- `blender/chicha.blend` : la scène, avec une pièce par partie ;
- `assets/oneforallsurvival/models/block/chicha.obj` et `.mtl` : le modèle exporté ;
- `assets/oneforallsurvival/textures/block/chicha.png` : la texture, en carrés de couleur pixelisés ;
- `apercus/` : les rendus de vérification.

| Partie | Forme |
|---|---|
| Vase | Une base en verre teinté vert, ventrue, posée au centre du bloc |
| Col et tige | Une tige en cuivre qui sort du vase et monte presque jusqu'en haut du bloc |
| Plateau | Un petit plateau en cuivre à mi-hauteur de la tige, pour poser le charbon |
| Foyer | Un petit bol en terre cuite en haut de la tige, avec le charbon dessus |
| Prise du tuyau | Un embout en cuivre sur le côté de la tige, d'où part le fil |
| Tuyau au repos | Un tuyau en cuir enroulé contre le vase, visible seulement quand personne ne le tient |

- 1 unité Blender = 1 bloc. Dans l'OBJ, le bloc va de 0 à 1 sur chaque axe, le bas du modèle est à 0.
  Le modèle tient dans x de 0,15 à 0,87, y de 0 à 0,935 et z de 0,18 à 0,85 (relevé sur l'OBJ exporté).
- **Le nord** (avant du bloc, -Z) est le côté de la prise. Le bout de la prise, où le code accroche le
  fil, est en **(0,5 ; 0,52 ; 0,33)** dans l'OBJ, orientation nord.
- Chaque partie est un **groupe** de l'OBJ (`vase`, `tige`, `foyer`, `braise`, `tuyau_repos`…). Le code
  cache `tuyau_repos` quand le tuyau est pris, et `braise` quand le foyer est éteint, par la visibilité
  des groupes du chargeur OBJ de Forge.
- 14 pièces, 379 faces : c'est un objet de décor, posé en nombre dans un bar.
- La texture fait 32 × 32 pixels : une case de 8 × 8 par matière, avec un léger bruit pour l'aspect
  pixelisé. Le verre a une transparence partielle.
- À vérifier au lot CH1 : le réglage `flip_v` du chargeur OBJ de Forge (sens de la coordonnée V) et
  la lecture du MTL, qui pointe vers `oneforallsurvival:block/chicha`.

## 5. Features

La numérotation prolonge celle du cahier du casino.

**124. Chicha.**
- C'est un **bloc de 1 × 1**, qui se pose et se casse comme un bloc de décor. Cassée, elle se ramasse
  avec ce qu'elle contient.
- **Tout le monde la fabrique**, sur une table de craft normale (choix de Killian). Recette proposée :
  3 verres, 2 lingots de cuivre, 1 brique, 1 cuir et 1 ficelle.
- Elle s'oriente vers le joueur qui la pose, la prise du tuyau face à lui.

**125. Charge : tabac et charbon.**
- Pour fumer, il faut la **charger** (choix de Killian) : clic droit avec du **tabac**, puis avec du
  **charbon de bois**. Le contenu se voit sur le modèle : le charbon rougeoie dans le foyer.
- Une charge donne **30 bouffées** (choix de l'auteur). À la fin, le foyer s'éteint et il faut
  recharger.
- La charge est du **tabac à chicha**, décrit dans le [cahier des substances](2026-11-11-substances-design.md)
  (feature 131) : du tabac cultivé par tout le monde, séché, puis mélangé à du sucre.
- Accroupi + clic droit main vide : la chicha rend le tabac et le charbon qu'elle contient, si aucune
  bouffée n'a été prise de cette charge.

**126. Le tuyau, relié comme une canne à pêche.**
- Clic droit main vide sur la chicha : le joueur **prend le tuyau** (demande de Killian). Il apparaît
  dans sa main comme un objet, et un **fil** le relie au bloc, dessiné comme la ligne d'une canne à
  pêche. Tous les joueurs proches voient le fil.
- **Un seul tuyau** par chicha : tant qu'un joueur le tient, les autres ne peuvent pas le prendre. Un
  clic droit leur dit « Le tuyau est déjà pris. »
- Le tuyau **revient seul** au bloc, sans rien laisser dans l'inventaire, quand le joueur :
  - s'éloigne à plus de **4 blocs** (choix de l'auteur) ;
  - le range, change d'objet en main ou le lâche ;
  - se déconnecte ou meurt ;
  - ou quand la chicha est cassée.
- Le tuyau ne se range pas, ne se jette pas et ne se pose dans aucun contenant : il n'existe que dans
  la main de celui qui fume. Il ne peut donc pas se dupliquer.
- On passe le tuyau à quelqu'un d'autre en le rendant (il revient au bloc), puis l'autre le prend.

**127. Fumer.**
- Tuyau en main, **maintenir clic droit** : le joueur aspire, comme quand il boit une potion. On entend
  l'eau bouillonner et le charbon rougit plus fort.
- **En relâchant**, il souffle un **nuage de fumée** blanche devant lui. Plus il a aspiré longtemps,
  plus le nuage est gros, jusqu'à 3 secondes d'aspiration (« fumer comme un fou », demande de Killian).
- La fumée est vue par tous les joueurs proches, et monte lentement avant de se dissiper.
- Chaque bouffée consomme 1 des 30 de la charge. Sans charge, l'aspiration ne donne qu'un bruit d'eau
  et pas de fumée.

**128. Trop fumer.**
- Un **petit effet** (choix de Killian) si l'on fume trop d'affilée : à partir de **8 bouffées en
  2 minutes** (choix de l'auteur), le joueur reçoit une **nausée** de 8 secondes, qui fait onduler sa
  vue.
- Chaque bouffée de plus pendant la nausée l'allonge de 4 secondes, jusqu'à 30 secondes au plus.
- L'effet ne fait **aucun dégât** et ne bloque aucune action. Il disparaît seul, ou avec du lait.

## 6. Branchements

| Avec | Branchement |
|---|---|
| Économie | Aucun. Le tabac se vend entre joueurs comme tout objet. |
| Substances | Le tabac à chicha vient du cahier des substances (131). Le seuil d'excès (128) sert aussi au tabac et à la drogue douce. |
| Policier | Aucun. Le tabac n'est pas interdit. |
| Wiki | Une fiche par feature, chapitre « Loisirs ». |

## 7. Découpage conseillé

| Lot | Contenu | Ce qu'il prouve | Après |
|---|---|---|---|
| CH1 | 124 à 128 | On charge, on prend le tuyau relié par un fil, on fume, et trop fumer donne la nausée | Le modèle Blender |

## 8. Vérification

- **Lancement réel obligatoire**, avec deux clients : l'un fume, l'autre voit le fil et la fumée.
- **GameTests** pour les règles :
  - un joueur prend le tuyau, un second ne peut pas le prendre ;
  - le tuyau revient au bloc au-delà de 4 blocs, au changement d'objet, à la déconnexion, à la mort, et
    quand la chicha est cassée, sans rien laisser dans l'inventaire ;
  - le tuyau ne se met dans aucun contenant et ne se jette pas ;
  - une charge donne 30 bouffées, puis plus de fumée ;
  - 8 bouffées en 2 minutes donnent la nausée, jamais de dégâts ;
  - une chicha cassée rend son contenu.
- **Capture** du bloc posé collé à un mur, de jour et de nuit, foyer allumé et éteint.

## 9. Décisions et choix

### Décisions de Killian (2026-11-11)
- Un bloc de **1 × 1**, modélisé dans **Blender**.
- Le tuyau se **prend comme une canne à pêche**, relié au bloc par un **fil**.
- Fumer donne de la **fumée** et un **petit effet** si l'on fume trop.
- Il faut **recharger** avec du **tabac** et du **charbon**.
- **Tout le monde** la fabrique.
- Pour l'instant : le cahier et le modèle Blender, **sans toucher au code**.

### Choix faits par l'auteur du document
Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- la recette (verre, cuivre, brique, cuir, ficelle) ;
- 30 bouffées par charge ;
- un tuyau qui revient au-delà de 4 blocs ;
- la nausée à 8 bouffées en 2 minutes, plafonnée à 30 secondes ;
- un seul tuyau par chicha ;
- un vase en verre vert.

## 10. Questions ouvertes

- Faut-il **plusieurs tuyaux** pour fumer à deux ou à quatre sur la même chicha ?
