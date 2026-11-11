# Cahier des charges : le casino et la roulette

Date : 2026-11-11. Statut : en attente de validation par Killian.

Killian a demandé le 2026-11-11 une grande table de roulette de casino, avec une roue et une bille animées
qui tombent sur le vrai résultat. Il a fixé le fonctionnement de l'argent : des jetons achetés à un PNJ,
un coffre du casino qui garde les pièces, un PNJ qui rachète les jetons, et un statut casino donné par
commande. Ses réponses sont regroupées en section 9.

L'argent passe par le [cahier de l'économie](2026-11-08-economie-design.md), qui reçoit ici deux
exceptions : le coffre du casino comme rangement de pièces, et les PNJ du casino.

## 1. Pourquoi

Un casino donne à la ville un lieu de sortie, une activité du soir et un commerce pour son exploitant.
La roulette est le jeu le plus lisible : tout le monde voit la bille tourner et tomber.

**Critère de réussite : un joueur achète des jetons, mise à la table, voit la bille tomber sur le numéro
que le serveur a tiré, revend ses jetons, et à aucun moment de l'argent n'est créé ou perdu en dehors du
coffre du casino.**

## 2. Objectifs et limites

### Objectifs
- Une **roulette européenne** jouable à plusieurs, en jetons.
- Une **animation fidèle** : la roue et la bille tombent exactement sur le résultat tiré par le serveur,
  et tous les joueurs voient le même lancer.
- Un circuit d'argent **fermé** : les pièces des jetons vont dans le coffre du casino, et le rachat des
  jetons en sort.

### Hors périmètre
- **Les autres jeux** (blackjack, machines à sous, poker) : un autre cahier, plus tard.
- **La fabrication** des tables : elles ne se fabriquent pas.
- **Les mises en pièces** à la table : on ne mise qu'en jetons.

### Ce qui serait un échec, même si tout fonctionne
- Un résultat que l'on peut prévoir ou influencer depuis le jeu d'un joueur.
- Une animation qui montre un numéro différent du résultat payé.
- Des pièces créées par le casino, ou perdues entre le PNJ et le coffre.
- Des jetons vendus sans que leur prix arrive dans le coffre.

### Règle de conception
Le casino **ne crée jamais de pièces**. Les jetons ne sont pas de l'argent : ils sont une promesse du
casino, payée par le coffre au rachat.

## 3. Contraintes techniques

- **Le résultat** est tiré par le serveur avec un générateur aléatoire sûr, au moment du lancer, avant
  toute animation. Le jeu des joueurs ne reçoit que de quoi la dessiner.
- **L'animation** se calcule à rebours chez chaque joueur à partir de trois valeurs envoyées par le
  serveur : le numéro gagnant, l'heure de départ du lancer et une graine (vitesse de départ, nombre de
  tours). Tous les joueurs voient donc le même lancer, et un joueur qui arrive en cours de lancer le
  reprend au bon moment.
- **Les gains** sont payés par le serveur selon le numéro tiré, jamais selon ce qu'un joueur a vu.
- **Les modèles** viennent de Blender, en OBJ, chargés par le chargeur OBJ de Forge. Le format OBJ ne
  contient pas d'animation : la roue et la bille sont des pièces séparées, animées par le code.
- Les PNJ et le coffre suivent les règles des PNJ et des coffres de l'économie : invulnérables, contenu
  gardé par le serveur, registre plafonné.

## 4. Socle

### Le statut casino
- C'est un **statut bonus** qui s'ajoute au métier, comme celui de maire. Plusieurs joueurs peuvent
  l'avoir, et chacun est rattaché à **un coffre de casino** précis.
- L'équipe du serveur le donne et le retire par commande. Exemple de syntaxe, à confirmer au lot :
  `/casino statut add <joueur> <casino>` et `/casino statut remove <joueur> <casino>`.
- Il donne accès au **coffre** de son casino et le droit de régler les **mises** de ses tables.

### Le circuit de l'argent

```
Pièces du joueur → PNJ vendeur → Coffre du casino → PNJ racheteur → Pièces du joueur
                        ↓                                   ↑
                     Jetons → Table de roulette → Jetons gagnés
```

## 5. Features

La numérotation prolonge celle du cahier du maire.

**110. Coffre du casino.**
- C'est un bloc posé par l'**équipe du serveur**. Il est **incassable** pour tout le monde sauf l'équipe.
- Il porte le **nom du casino** (« Casino du Port »), qui sert à y relier les PNJ et les tables. On peut
  avoir plusieurs casinos, chacun avec son coffre.
- Il ne contient **que des pièces**. Il s'ajoute aux rangements autorisés de l'économie (feature 84).
- Seuls les joueurs au **statut casino** de ce casino et l'équipe l'ouvrent. Ils peuvent y déposer ou y
  prendre des pièces, et chaque mouvement s'inscrit à son **registre**.
- Son contenu est gardé par le serveur : les PNJ y paient et y encaissent même si la zone n'est pas
  chargée.

**111. Jeton de casino.**
- C'est un **objet**, qui s'empile par 64. Il ne se fabrique pas : il s'achète au PNJ vendeur (112).
- Son **prix** est de **1 pièce d'argent** (10 cuivres), choix de Killian, réglable en config.
- Ce n'est **pas de l'argent** : on le range partout, dans un coffre ou un sac, et il se vole comme
  tout objet (choix de Killian).
- Un même jeton vaut dans tous les casinos du serveur.

**112. PNJ vendeur de jetons.**
- L'équipe le fait apparaître par commande, en le **reliant à un coffre de casino** dès son apparition.
  Le coffre doit donc être posé avant. Exemple : `/casino pnj vendeur <casino>`.
- Il est invulnérable et immobile, comme le PNJ changeur. Son nom et son apparence sont réglables.
- Le joueur choisit un nombre de jetons. Le PNJ prend les pièces dans l'inventaire (bourse comprise),
  rend la monnaie, puis **verse le paiement dans le coffre** relié et donne les jetons.
- L'échange est **tout ou rien** : si les pièces manquent, rien n'est pris ; si le versement au coffre
  échoue, l'achat est annulé et rien ne change.
- Chaque vente s'inscrit au registre du coffre.

**113. PNJ racheteur de jetons.**
- Même apparition, reliée au même coffre : `/casino pnj racheteur <casino>`.
- Il rachète les jetons **au prix de vente**. Les pièces sortent du coffre relié et vont dans
  l'inventaire du joueur.
- **Si le coffre ne peut pas payer**, le rachat est **refusé** en entier (choix de Killian) : « Le casino
  ne peut pas payer pour l'instant. » Le joueur garde ses jetons. Les joueurs au statut casino et
  l'équipe sont prévenus.
- Chaque rachat s'inscrit au registre du coffre.

**114. Table de roulette.**
- C'est une **grande table** d'environ 3 × 2 blocs : un bloc principal dont le modèle déborde, entouré de
  blocs de collision invisibles. Le modèle vient de Blender (feature 116).
- Elle **ne se fabrique pas**. L'équipe la donne à un joueur après un paiement en RP (choix de Killian),
  puis la **relie à un coffre de casino** à la pose. Seule l'équipe la casse.
- Plusieurs joueurs jouent en même temps à la même table.
- **Mises** : le joueur ouvre le tapis et pose des jetons sur les cases. Les jetons misés quittent son
  inventaire tout de suite. Mises proposées :

  | Mise | Paie |
  |---|---|
  | Un numéro (plein) | 35 contre 1 |
  | Deux numéros voisins (cheval) | 17 contre 1 |
  | Une ligne de trois (transversale) | 11 contre 1 |
  | Quatre numéros (carré) | 8 contre 1 |
  | Une douzaine, une colonne | 2 contre 1 |
  | Rouge ou noir, pair ou impair, 1 à 18 ou 19 à 36 | 1 contre 1 |

  Sur le **0**, les mises simples (rouge, pair, 1 à 18…) sont perdues.
- **Le lancer est automatique** (choix de Killian) : quand au moins une mise est posée, un compte à
  rebours s'ouvre (30 s au départ, réglable). À sa fin, les mises sont fermées, la roue est lancée, et le
  résultat s'affiche puis les gains sont payés.
- **Gains** : versés en jetons dans l'inventaire du gagnant ; s'il est plein, ils tombent à ses pieds.
  Un joueur parti avant la fin retrouve ses gains à sa prochaine connexion.
- **Mises minimum et maximum** : réglées par un joueur au **statut casino** pour chaque table de son
  casino, dans des bornes fixées par l'équipe.
- Une mise refusée (hors bornes, mises fermées) rend les jetons.

**115. Animation synchronisée.**
- Au lancer, le serveur tire le numéro, puis envoie aux joueurs proches le numéro, l'heure de départ et
  une graine.
- Chaque jeu calcule :
  - la **roue**, qui tourne et ralentit jusqu'à l'arrêt ;
  - la **bille**, qui tourne dans l'autre sens sur le bord, ralentit, descend, touche un ou deux
    déflecteurs, puis tombe dans la case tirée et suit la roue.
- Le mouvement est calculé **à rebours** depuis la case d'arrivée : la bille tombe toujours dans la bonne
  case, quelle que soit la graine.
- Durée d'un lancer : environ 12 s (réglable). Le résultat s'affiche au-dessus de la table, puis les gains
  sont payés.
- Un joueur qui arrive pendant un lancer le reprend là où il en est.

**116. Modèles Blender.**
Trois objets séparés, exportés en OBJ, avec une seule texture :

| Objet | Contenu | Règle |
|---|---|---|
| Table | Bois, tapis et cases de mise : tout ce qui est fixe | 1 unité Blender = 1 bloc. |
| Roue | Le cylindre et ses 37 cases | Origine exactement sur l'axe de rotation. La case du **0** est alignée sur une direction de référence (l'avant de la table), pour que le code sache où est chaque case. |
| Bille | Une petite sphère | Origine au centre, peu de polygones. |

- Ordre des cases d'une roulette européenne, dans le sens de la roue : 0, 32, 15, 19, 4, 21, 2, 25, 17,
  34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26.
- Peu de polygones : la table est vue par plusieurs joueurs à la fois.

**117. Registre et suivi du casino.**
- Le registre du coffre note les ventes et les rachats de jetons, et les dépôts et retraits des joueurs au
  statut casino.
- Chaque lancer est noté : table, numéro tiré, total misé, total payé.
- Par commande, l'équipe lit pour chaque casino : le contenu du coffre, les jetons vendus et rachetés, et
  ce que la table a gagné ou perdu. Elle voit aussi la **valeur des jetons en circulation**, comparée au
  coffre : si elle dépasse le coffre, le casino ne pourra pas tout racheter.

## 6. Branchements

| Avec | Branchement |
|---|---|
| Économie | Le coffre du casino s'ajoute aux rangements de pièces autorisés (feature 84). Les deux PNJ du casino s'ajoutent au PNJ changeur (feature 80) comme seuls PNJ qui manient des pièces. Le casino ne crée ni ne détruit de pièces. |
| Policier | Les jetons se fouillent et se confisquent comme tout objet. |
| Maire | Non branché. Une taxe sur le casino pourra s'ajouter plus tard. |
| Wiki | Une fiche par feature, chapitre « Casino ». |

## 7. Découpage conseillé

| Lot | Contenu | Ce qu'il prouve | Après |
|---|---|---|---|
| C1 | 110, 111, 112, 113, statut casino, 117 (registre) | On achète et revend des jetons, et le coffre garde l'argent | Lot E1 de l'économie |
| C2 | 114 sans animation (résultat affiché) | On mise, on gagne, on perd, et les comptes tombent juste | C1 |
| C3 | 115, 116 | La bille tombe sur le bon numéro, pour tous les joueurs | C2 et les modèles Blender |

## 8. Vérification

- **Lancement réel obligatoire à chaque lot**, avec au moins deux clients au lot C3.
- **GameTests** pour les règles :
  - acheter des jetons verse exactement leur prix au coffre, et le registre le note ;
  - un achat sans assez de pièces ne prend rien ;
  - un rachat paie depuis le coffre au prix de vente ;
  - un rachat que le coffre ne peut pas payer est refusé en entier, et prévient le statut casino ;
  - un joueur sans statut casino ne peut pas ouvrir le coffre, et personne ne le casse sauf l'équipe ;
  - un PNJ ne peut pas apparaître sans coffre relié ;
  - chaque type de mise paie le bon multiple, et le 0 fait perdre les mises simples ;
  - une mise hors bornes est refusée et ses jetons rendus ;
  - sur 100 000 lancers simulés, chaque numéro sort environ une fois sur 37 ;
  - le numéro dessiné en fin d'animation est toujours le numéro tiré, pour toute graine.
- **Scénario d'acceptation** : l'équipe pose le coffre, y met des pièces, fait apparaître les deux PNJ et
  donne une table ; deux joueurs achètent des jetons, misent, regardent le même lancer, et l'un gagne ;
  ils revendent leurs jetons ; le coffre a exactement changé de la différence entre jetons vendus et
  rachetés.

## 9. Décisions et choix

### Décisions de Killian (2026-11-11)
- Un jeton coûte **1 pièce d'argent**.
- Les **tables ne se fabriquent pas** : l'équipe les donne après un paiement en RP.
- On joue avec des **jetons de casino**, achetés à un **PNJ**.
- Un **coffre du casino**, posé par l'équipe et **incassable**, garde l'argent.
- Un **statut casino**, donné par commande, ouvre ce coffre.
- Le **PNJ est relié au coffre** à son apparition : on pose le coffre d'abord, puis le PNJ.
- Le paiement des jetons doit **arriver dans le coffre**.
- Un **autre PNJ rachète** les jetons.
- Le lancer est **automatique**.
- Les **mises minimum et maximum** sont réglées par le statut casino.
- Un rachat que le coffre ne peut pas payer est **refusé**.
- Les jetons se **rangent partout**.

### Choix faits par l'auteur du document
Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- roulette européenne à un seul 0, et mises simples perdues sur le 0 ;
- rachat au prix de vente, sans frais ;
- table de 3 × 2 blocs ;
- compte à rebours de 30 s et lancer de 12 s ;
- un même jeton pour tous les casinos ;
- plusieurs casinos possibles, chacun avec son coffre.

## 10. Questions ouvertes

- **Une seule valeur de jeton**, ou plusieurs (1, 5, 25) pour les grosses mises ?
- Le **rachat** doit-il prendre des frais, au profit du casino ?
