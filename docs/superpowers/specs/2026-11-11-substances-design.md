# Cahier des charges : tabac, alcool et drogues

Date : 2026-11-11. Statut : en attente de validation par Killian.

Killian a demandé le 2026-11-11 un cahier pour le tabac, l'alcool et les drogues, juste après celui de la
[chicha](2026-11-11-chicha-design.md), qui attendait de savoir d'où vient le tabac. Ses réponses sont
regroupées en section 9.

Les drogues sont **fictives** : leurs noms, leurs plantes et leurs recettes viennent de l'île et de ses
ruines, et ne décrivent aucune fabrication réelle.

## 1. Pourquoi

Une ville vivante a ses bars, ses fumeurs et son marché noir. Ces produits donnent :
- du commerce entre joueurs ;
- un sujet politique, décidé en RP : qu'est-ce qu'on tolère en ville ?
- du travail à la police : saisir, dépister, remonter une filière.

**Critère de réussite : un joueur cultive, transforme et vend un produit ; un autre le consomme et en
sent les effets ; un policier peut le dépister, le saisir et dresser une contravention.**

## 2. Objectifs et limites

### Objectifs
- Quatre familles : **tabac**, **alcool**, **drogue douce**, **drogue dure**.
- Une **production par tout le monde** (choix de Killian) : cultiver, sécher, brasser, distiller,
  cuisiner au labo.
- Des **effets passagers** seulement (choix de Killian) : ni dépendance, ni manque, ni overdose.
- **Aucune loi codée** (choix de Killian) : ce qui est interdit se décide en RP. Le mod donne les
  outils, pas les règles.
- Des **outils de police** pour dépister et saisir.

### Hors périmètre
- La dépendance, le manque et l'overdose.
- Des bonus durables : aucun produit ne rend plus fort sur la durée.
- Un métier dédié : tout le monde produit.

### Ce qui serait un échec, même si tout fonctionne
- Un effet qui tue, ou qui bloque le joueur sans issue.
- Un produit si fort qu'il devient obligatoire pour jouer : la drogue dure se paie par sa descente.
- Un dépistage qui accuse un joueur qui n'a rien pris.

### Règle de conception
Le jeu donne les produits, les effets et les outils. **La loi se fait en RP** : le jeu n'interdit rien.

## 3. Contraintes techniques

- Les effets passent par des **effets de statut** du mod (comme ceux de Minecraft), affichés dans
  l'inventaire avec leur durée, et levés par le lait. Les effets visuels (vue trouble, couleurs) sont
  des effets d'écran chez le joueur concerné seulement.
- Le serveur garde, pour chaque joueur, ce qu'il a consommé dans la dernière heure, pour le dépistage
  (feature 137). Rien n'est gardé au-delà.
- Les recettes de séchage, de brassage, de distillation et du labo sont des **données JSON**, comme les
  recettes du tonneau de l'agriculteur.
- La fumée suit le même rendu que celle de la chicha (cahier de la chicha, feature 127).

## 4. Socle

### Un seuil d'excès commun
Tabac, chicha et drogue douce partagent la règle de la chicha (feature 128) : **trop d'un coup** donne
une **nausée** courte, plafonnée à 30 secondes, sans dégât. L'alcool a sa propre échelle (feature 132).

### Les quatre familles

| Famille | Produits | Fabrication | Effet principal |
|---|---|---|---|
| Tabac | Cigarette, cigare, pipe, tabac à chicha | Plante de tabac séchée | Fumée, légère détente |
| Alcool | Bière, cidre, vin de baies, rhum | Tonneau, puis alambic | Ivresse par paliers |
| Drogue douce | Roulé d'herbe-songe, gâteau d'herbe-songe | Herbe-songe séchée | Couleurs, lenteur, faim |
| Drogue dure | Poussière d'épave | Labo clandestin | Montée courte et forte, puis descente |

## 5. Features

La numérotation prolonge celle du cahier de la chicha.

### Plantes

**129. Trois plantes de l'île.**
- **Le tabac** : une grande plante à larges feuilles, trouvée à l'état sauvage dans les plaines et les
  savanes de l'île.
- **L'herbe-songe** : une herbe aux reflets violets, trouvée près des rivières, rare.
- **Le champignon des ruines** : un champignon pâle et luisant, qui ne pousse qu'à l'ombre, près des
  ruines mystérieuses de l'île. Il se replante dans le noir, comme les champignons de Minecraft.
- Les deux plantes donnent des **graines** à la récolte et se cultivent comme le blé, par **tout le
  monde** (choix de Killian). Elles poussent sous la serre de l'agriculteur comme les autres cultures.
- **Graines dans l'herbe** (demande de Killian) : casser de l'herbe haute ou de la grande herbe peut
  donner une graine, comme les graines de blé de Minecraft :
  - graine de tabac : **1 chance sur 20** ;
  - graine d'herbe-songe : **1 chance sur 50**.
  Les chances sont réglables en config. Le mod les ajoute par un modificateur de butin global de Forge,
  sans toucher aux tables de butin de Minecraft.
- **Poudre d'os** (demande de Killian) : sur un bloc d'herbe, elle fait pousser autour, en plus des fleurs
  et des herbes habituelles, un **plant sauvage** de tabac (1 chance sur 15 par plante qui pousse) ou
  d'herbe-songe (1 chance sur 40). Un plant sauvage se récolte comme une culture mûre et donne feuilles
  et graines. Chances réglables en config.
- Le champignon des ruines ne vient ni de l'herbe ni de la poudre d'os : on le trouve près des ruines,
  puis on le replante.

**130. Séchage.**
- Les feuilles de tabac et d'herbe-songe se **sèchent** avant usage : au **four** (lent) ou au **séchoir
  électrique** (rapide, feature 11 du cahier général).
- Une feuille fraîche ne se fume pas : elle ne donne rien.

### Tabac

**131. Produits du tabac.**
- **Cigarette** : 1 tabac + 1 papier, à la table de craft. Donne 5 bouffées.
- **Cigare** : 3 tabacs, à la table de craft. Donne 12 bouffées.
- **Pipe** : un objet qu'on garde, fabriqué en bois. On la **charge** de tabac (clic droit avec le tabac
  dans l'autre main) : 8 bouffées par charge.
- **Tabac à chicha** : 2 tabacs + 1 sucre. C'est la charge de la chicha (feature 125).
- **Fumer** se fait comme à la chicha : maintenir clic droit pour aspirer, relâcher pour souffler un
  nuage. Une cigarette ou un cigare se consume aussi seul, lentement, tant qu'on le tient allumé.
- **Allumer** : clic droit avec un briquet (pierre à briquet) en main, ou près d'un feu.
- Effet : une légère **détente**, sans effet de jeu. Au-delà du seuil d'excès, la nausée.
- Un mégot reste après une cigarette : il se jette ou se ramasse (indice possible pour la police).

### Alcool

**132. Ivresse.**
- Chaque verre fait monter l'**ivresse**, de 0 à 5. Elle redescend d'un palier toutes les 2 minutes
  (choix de l'auteur).

  | Palier | Effet |
  |---|---|
  | 1 | Rien de visible : un verre se boit sans souci |
  | 2 | Vue légèrement trouble sur les bords |
  | 3 | Marche qui tangue : la direction dévie un peu, au hasard |
  | 4 | Nausée en plus |
  | 5 | Malaise : vue noire 5 secondes, lenteur forte 30 secondes, puis retour au palier 4 |

- Aucun palier ne fait de dégât. Le lait remet l'ivresse à zéro, ou un **café** la baisse d'un palier
  (Farmer's Delight n'en a pas : à ajouter, avec des grains de café trouvés sur l'île).
- Un joueur ivre se voit : au palier 3 et plus, les autres voient son personnage tanguer un peu.

**133. Boissons.**
- **Au tonneau** (feature 29 de l'agriculteur), de nouvelles recettes s'ajoutent :

  | Entrée | Sortie | Ivresse par verre |
  |---|---|---|
  | Blé, eau | Bière | +1 |
  | Pommes, eau | Cidre | +1 |
  | Baies sucrées, eau | Vin de baies (existant) | +1 |
  | Canne à sucre, eau | Moût de canne (ne se boit pas) | — |

- **L'alambic** : un nouveau bloc, fabriqué par tout le monde, chauffé par un feu ou un charbon en
  dessous. Il distille un moût ou une boisson en **alcool fort** :

  | Entrée | Sortie | Ivresse par verre |
  |---|---|---|
  | Moût de canne | Rhum | +2 |
  | Cidre | Eau-de-vie de pomme | +2 |

- Une boisson se sert en **bouteille** (4 verres) ; on boit verre par verre.
- Le tonneau est fabriqué par l'agriculteur, mais tout le monde peut s'en servir pour brasser.

### Drogues

**134. Herbe-songe, la drogue douce.**
- **Roulé** : 1 herbe-songe séchée + 1 papier. Il se fume comme une cigarette, en 4 bouffées.
- **Gâteau d'herbe-songe** : herbe-songe séchée + blé + sucre + œuf. Il se mange ; l'effet arrive
  après 1 minute et dure deux fois plus longtemps.
- Effets, pendant 3 minutes pour un roulé :
  - **couleurs** plus vives et ondulantes à l'écran ;
  - **lenteur** légère ;
  - **faim** qui descend plus vite ;
  - sons un peu étouffés.
- Un second roulé prolonge l'effet, jusqu'à 10 minutes au plus. Au-delà du seuil d'excès, la nausée.

**135. Poussière d'épave, la drogue dure.**
- Elle se fabrique au **labo clandestin**, un bloc fabriqué par tout le monde à partir de verre,
  d'un chaudron et de fer.
- Recette, fictive : 3 champignons des ruines séchés, 1 poudre à canon et 1 sucre, travaillés 5 minutes.
  Le labo en sort 4 doses.
- **Pendant qu'il travaille, le labo fume** : une fumée verdâtre sort du bloc et se voit de loin. C'est
  le risque du chimiste : un voisin ou un policier peut la repérer.
- Une dose se **sniffe** (clic droit) :
  - **montée de 60 secondes** : vitesse II, force I, minage plus rapide, la faim ne descend pas ;
  - puis **descente de 3 minutes** : lenteur, faiblesse, minage lent, vue assombrie.
- Une seconde dose pendant la montée **n'ajoute rien**. Pendant la descente, elle relance une montée
  mais allonge la descente qui suit de 3 minutes. On ne peut pas éviter la descente.
- Aucune dose ne tue et ne met à terre (choix de Killian : effets seulement).

### Police et médecin

**136. Pas de loi codée.**
Décision de Killian du 2026-11-11 : **le jeu ne code aucune loi**. Ce qui est légal ou interdit se décide
en RP, par le maire, les joueurs et l'équipe. Le mod ne fournit que les **outils** :
- les produits, leurs effets et leurs traces ;
- le dépistage de la police (feature 137) ;
- la fouille et la confiscation du policier (feature 52 du policier), qui marchent déjà sur tout objet ;
- le carnet de contraventions, où le policier écrit le motif (feature 60 du policier).

Aucun produit n'est marqué « interdit », aucun étal ne refuse un produit, aucune zone n'a de licence.

**137. Dépistage.**
- Deux outils, fabriqués à l'**établi de policier** et utilisés par un policier seulement :
  - l'**éthylotest** donne le palier d'ivresse actuel ;
  - le **test salivaire** dit si le joueur a pris de l'herbe-songe ou de la poussière d'épave dans la
    **dernière heure**, sans dire combien.
- Le policier vise un joueur à moins de 2 blocs. Le joueur reçoit une demande et peut **refuser** ; le
  refus s'inscrit au journal et au casier. Un joueur menotté ne peut pas refuser.
- Le résultat devient un **procès-verbal**, preuve qui se joint au casier.
- Le test ne se trompe jamais : il lit ce que le serveur a noté.

**138. Charbon actif.**
Choix de Killian du 2026-11-11.
- Un soin médical, fabriqué à la paillasse du médecin et utilisé par un **médecin** seulement
  ([cahier du médecin](2026-11-08-metier-medecin-design.md)).
- Donné à un joueur, il **remet l'ivresse à zéro** et **coupe la descente** de la poussière d'épave. Il
  arrête aussi les effets de l'herbe-songe.
- Il ne prévient pas : pris avant de boire, il n'empêche pas l'ivresse.
- Il s'inscrit au dossier médical du patient.

## 6. Branchements

| Avec | Branchement |
|---|---|
| Chicha | Son tabac vient d'ici : le tabac à chicha (131). Le seuil d'excès est commun. |
| Maire | Aucun : la loi se décide en RP (136). |
| Policier | Éthylotest et test salivaire à l'établi de policier (137). Les produits se confisquent à la fouille, comme tout objet. |
| Agriculteur | Nouvelles recettes du tonneau (133). Les nouvelles plantes poussent sous la serre. |
| Économie | Aucun : tout se vend comme d'habitude, à l'étal ou de la main à la main. |
| Médecin | Charbon actif, qui dégrise et coupe une descente (138). |
| Wiki | Une fiche par feature, chapitre « Substances ». |

## 7. Découpage conseillé

| Lot | Contenu | Ce qu'il prouve | Après |
|---|---|---|---|
| S1 | 129, 130, 131 | On cultive le tabac, on le sèche, on fume, et la chicha a sa charge | Lot CH1 de la chicha |
| S2 | 132, 133 | On brasse, on distille, on boit, et l'ivresse monte et redescend | Le tonneau de l'agriculteur |
| S3 | 134, 135 | Les deux drogues ont leurs effets, et le labo se voit de loin | S1 |
| S4 | 137, 138 | La police dépiste, et le médecin dégrise | S1 à S3, lot P1 du policier, lots du médecin |

## 8. Vérification

- **Lancement réel obligatoire à chaque lot**, avec deux clients aux lots S3 et S4.
- **GameTests** pour les règles :
  - une feuille fraîche ne se fume pas, une feuille séchée oui ;
  - sur 10 000 herbes cassées, les graines tombent à peu près aux chances réglées ; la poudre d'os sur
    l'herbe fait parfois pousser un plant sauvage ;
  - une cigarette donne 5 bouffées, un cigare 12, une charge de pipe 8 ;
  - l'ivresse monte du bon nombre de paliers selon la boisson, redescend d'un palier toutes les
    2 minutes, et le lait la remet à zéro ;
  - aucun palier d'ivresse, aucune drogue ne fait de dégât ni ne met à terre ;
  - une seconde dose de poussière d'épave pendant la montée n'ajoute rien, et pendant la descente
    allonge la descente ;
  - le labo produit 4 doses et émet sa fumée pendant tout le travail ;
  - le charbon actif remet l'ivresse à zéro et coupe une descente, et seul un médecin le donne ;
  - le test salivaire est positif dans l'heure, négatif après, et toujours négatif pour un joueur qui n'a
    rien pris ;
  - un refus de dépistage s'inscrit au casier, et un joueur menotté ne peut pas refuser.
- **Captures** : la fumée du labo vue de 30 blocs, l'écran à chaque palier d'ivresse, l'écran sous
  herbe-songe.

## 9. Décisions et choix

### Décisions de Killian (2026-11-11)
- Le cahier couvre le **tabac**, l'**alcool**, une **drogue douce** et une **drogue dure**.
- **Aucune loi codée** : le RP décide de ce qui est interdit, le mod ne fait que les outils
  (2026-11-11, remplace « le maire décide »).
- Le **médecin** dégrise et coupe une descente, avec du **charbon actif**.
- **Pas de véhicule** sur le serveur : pas d'ivresse au volant.
- **Tout le monde** peut produire.
- **Effets seulement** : ni dépendance, ni overdose.

### Choix faits par l'auteur du document
Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- les noms et la nature fictive des drogues : herbe-songe, champignon des ruines, poussière d'épave ;
- les chances : graine de tabac 1 sur 20 et d'herbe-songe 1 sur 50 dans l'herbe ; plant sauvage 1 sur 15
  et 1 sur 40 à la poudre d'os ;
- les quantités : 5 bouffées par cigarette, 12 par cigare, 8 par pipe, 4 doses par fournée de labo ;
- l'ivresse en cinq paliers, qui redescend toutes les 2 minutes ;
- le café pour dessoûler, à ajouter ;
- la montée de 60 secondes et la descente de 3 minutes de la poussière d'épave ;
- la fumée visible du labo clandestin ;
- le dépistage refusable, refus noté au casier ;
- le mégot qui reste après une cigarette.

## 10. Questions ouvertes

Aucune pour l'instant.
