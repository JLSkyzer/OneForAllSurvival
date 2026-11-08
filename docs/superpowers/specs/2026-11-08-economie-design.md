# Cahier des charges : l'économie de Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Killian a demandé ce cahier le 2026-11-08, une fois les pièces de monnaie modélisées. Il veut le
livrer **avant tous les autres lots**, pour que les métiers s'y branchent au fur et à mesure.

**Ce document remplace la règle « le mod ne gère aucune monnaie »** des cahiers de
[l'électricité](2026-11-06-features-lost-island-design.md),
de [l'agriculteur](2026-11-08-metier-agriculteur-design.md)
et du [policier](2026-11-08-metier-policier-design.md).
Les passages concernés y renvoient ici.

## 1. Pourquoi

Les survivants fondent une ville et des commerces. Sans monnaie, chaque échange est un troc, chaque
service se paie en promesses, et le travail des métiers n'a pas de prix. Les pièces de Lost Island
existent déjà en modèle 3D. Il leur manque :
- une valeur ;
- une origine ;
- des endroits où elles circulent.

**Critère de réussite : un joueur peut vivre de son métier, la ville peut payer ses services publics, et
l'argent qui circule a toujours une origine connue.**

## 2. Objectifs et limites

### Objectifs
- Une monnaie **physique**, qu'on porte, qu'on range, qu'on peut se faire voler.
- Une **création monétaire contrôlée** : personne ne fabrique de l'argent sur une table de craft.
- Des **circuits** clairs :
  - d'où vient l'argent (sources) ;
  - où il disparaît (puits) ;
  - comment il passe d'un joueur à l'autre.
- Des **branchements** prêts pour les métiers : factures d'électricité, amendes, soins, commerce agricole.
- Des outils pour que l'équipe du serveur **surveille et règle** l'économie sans recompiler.

### Hors périmètre
- **Bourse, prêts à intérêt, actions** : trop modernes pour une ville naissante, et sources d'abus.
- **Économie avec des PNJ** : les villageois gardent leurs émeraudes vanilla ; aucun marchand automatique
  ne vend à la place d'un joueur.
- **Fixer les prix** : ils relèvent des joueurs, sauf les tarifs publics (électricité, amendes) que la
  ville règle.

### Ce qui serait un échec, même si tout fonctionne
- Une façon de **créer** de l'argent sans passer par la frappe (duplication, recette oubliée, échange
  de PNJ).
- Une inflation que l'équipe ne voit pas venir faute de chiffres.
- Un joueur absent qui perd tout son argent sans avoir pu réagir. Le vol reste possible, mais il
  laisse des traces.
- Un système si lourd que les joueurs préfèrent troquer.

### Règle de conception
Une feature économique doit dire **d'où vient l'argent qu'elle fait circuler et où il va**. Une feature
qui crée de l'argent sans contrepartie est refusée.

## 3. Contraintes techniques

### Existant
- Les modèles des cinq pièces (`copper_coin`, `silver_coin`, `gold_coin`, `emerald_coin`, `ruby_coin`,
  espace de noms `oneforallsurvival`) sont sur `main`, dans `art/pieces-monnaie/`. Il y a les modèles,
  les textures 64 × 64 et les projets Blockbench. **Les objets ne sont pas encore déclarés dans le mod.**
  Le premier lot les copie dans `src/main/resources` et les enregistre.
- Le système de rôles, avec les rôles dynamiques, permet de créer un rôle de banquier ou de trésorier
  sans code.

### Intégration
- Pièces, valeurs, tarifs, taxes, salaires et listes d'objets : données et config, pour que l'équipe
  règle l'équilibrage et que CraftTweaker puisse agir.
- **Aucune recette de craft ne produit une pièce.** Une vérification au démarrage le garantit, recettes
  CraftTweaker comprises, et signale en journal toute recette qui en produirait une.
- Les montants s'expriment en **cuivres**, l'unité de compte, et s'affichent dans la plus grande coupure
  possible (« 2 or 3 argent 5 cuivre »).
- Les écritures (trésor, comptes, factures) sont conservées par le serveur et survivent aux relances.
- Le serveur ne tourne que pendant les sessions RP : les salaires et les échéances se comptent en
  temps de session.

## 4. Socle

### Les pièces
Valeurs de départ, réglables :

| Pièce | Valeur en cuivres |
|---|---|
| Cuivre | 1 |
| Argent | 10 |
| Or | 100 |
| Émeraude | 1 000 |
| Rubis | 10 000 |

- **Le change** : 10 pièces d'une coupure s'échangent contre 1 de la coupure supérieure, et inversement,
  sur une table de craft. Ces recettes ne créent rien, elles échangent à valeur égale. C'est la seule
  exception à la règle « aucune recette ne produit une pièce ».
- L'infobulle d'une pièce indique sa valeur. Une pile indique son total.
- Une pièce ne se fond pas, ne se composte pas, ne se vend pas à un villageois.

### Rôle Trésorier
- C'est un **rôle dynamique** créé par l'équipe du serveur, pas un rôle codé en dur. Le nom est
  réglable : trésorier, banquier, maire.
- Il frappe la monnaie, tient le trésor et verse les salaires.
- Tant qu'aucun rôle n'est désigné en config, seule l'équipe du serveur fait ces gestes.

### Registre
Le mécanisme de registre du grenier communal sert à toute l'économie : chaque mouvement est noté, avec
l'auteur, le montant, l'heure et le motif. Le nombre de lignes est plafonné. Il est écrit une fois et
partagé.

## 5. Features

La numérotation prolonge celle du policier. Toutes les valeurs chiffrées sont des valeurs de départ, à
équilibrer.

### Création et circulation

**68. Presse à monnaie.**
- C'est le **seul** moyen de créer des pièces. Elle est réservée au trésorier, avec un contournement pour
  l'équipe du serveur.
- Chaque frappe consomme une matière (pépites de cuivre, de fer, d'or, émeraude ; le rubis par une matière
  rare réglable) et un temps. La matière coûte bien moins que la valeur frappée : la pièce vaut par la
  confiance dans la ville, pas par son métal.
- Chaque frappe s'inscrit au **registre de la frappe**, avec le total frappé depuis le début.
- La presse peut être plafonnée par session (réglable), pour qu'une erreur ou un abus reste limité.
- Une commande de l'équipe du serveur permet aussi de frapper, et elle est notée au même registre.

**69. Bourse.**
- C'est un objet porté qui **ramasse** les pièces, les range et affiche le total.
- Payer avec la bourse **rend la monnaie** automatiquement : le change se fait sans passer par la table.
- On peut la perdre, la laisser tomber, se la faire voler, et la fouiller (feature 52 du policier).

**70. Paiement de main à main.**
- C'est une **fenêtre d'échange** entre deux joueurs proches. Chacun pose objets et pièces, et l'échange
  n'a lieu que si les deux confirment l'état final.
- Elle évite l'arnaque du « jette d'abord ». Rien n'empêche de payer en jetant des pièces.
- Chaque échange peut produire un **reçu** (objet) pour les deux parties, sur demande.

### Commerce

**71. Étal de marché.**
- C'est un bloc de commerce. Le propriétaire le garnit d'objets et fixe un prix par lot.
- Un acheteur paie avec ses pièces ou sa bourse et reçoit le lot, **même si le vendeur est absent**.
- Le vendeur récupère ses recettes dans l'étal. Seul lui peut retirer la marchandise et l'argent. Les
  trémies et entonnoirs peuvent le garnir, pas le vider.
- L'étal ne se casse que vide.
- Une **taxe sur les ventes** (0 % au départ, réglable) part au trésor à chaque vente.
- L'étal peut aussi **racheter** : le propriétaire fixe un prix d'achat et une quantité
  maximale, payés sur ses recettes. L'agriculteur peut ainsi acheter le blé de ses voisins.

**72. Facture.**
- C'est un document (objet) émis par un joueur à un autre : émetteur, débiteur, motif, montant, date.
- Il est **payable** à un guichet (feature 74) ou de main à main. Payée, la facture devient un **reçu**.
- Les métiers produisent leurs factures par ce même objet (section 6).

### La ville

**73. Trésor de la ville.**
- C'est un **coffre-fort** tenu par le trésorier, avec un registre. Il reçoit les taxes, les amendes, les
  factures publiques, et il paie les salaires.
- Son solde est public, affiché sur un **tableau** posable en ville. La ville montre ses comptes.
- Il peut être cambriolé comme un coffre verrouillé (feature 67 du policier) : le registre montre alors un
  trou entre le solde attendu et le contenu.

**74. Guichet.**
- C'est un bloc relié au trésor, où l'on **paie une facture publique** (électricité, amende) sans que le
  trésorier soit présent.
- Le paiement marque la facture payée là où elle a été émise : le relevé du compteur, le casier
  judiciaire.

**75. Salaires publics.**
- Chaque rôle peut recevoir un **salaire**, réglé en config ou par commande. À la création, seuls les
  rôles publics sont proposés : policier, médecin.
- Le salaire est dû au **temps passé connecté dans le rôle**, et versé à chaque fin de session au
  **guichet**, où le joueur le retire.
- Il est payé **sur le trésor**. Si le trésor ne suffit pas, le salaire reste **dû** et s'affiche comme
  une dette de la ville : une ville pauvre a des conséquences visibles.

**76. Comptes au guichet.**
- Un joueur peut **déposer** ses pièces au guichet sur un **compte** à son nom, et les retirer plus tard.
  Un compte ne se vole pas.
- Il peut **virer** une somme à un autre compte, et payer une facture depuis son compte.
- Le relevé de compte se recopie en objet.
- Un compte ne produit **aucun intérêt**.

### Pilotage

**77. Tableau de bord de l'équipe.**
- Par commande, l'équipe du serveur lit :
  - le **total frappé** ;
  - le **total détruit** (pièces brûlées, tombées dans le vide, disparues avec un objet au sol) ;
  - la **masse en comptes** ;
  - le **solde du trésor** ;
  - les **salaires dus** ;
  - le **volume des ventes** par période.
- La masse en circulation se déduit : frappé moins détruit. Les pièces dans les coffres ne sont pas
  comptées une à une, ce qui serait trop coûteux.
- Les registres de la frappe et du trésor se consultent en entier.

## 6. Branchements avec les métiers

Chaque branchement se fait au lot du métier concerné, une fois l'économie livrée.

| Métier | Branchement |
|---|---|
| Ingénieur | Le **relevé de compteur** produit une **facture d'électricité** au tarif de la ville (par FE, réglable). Le paiement se fait au guichet. Impayée, la facture justifie un scellé en RP. Le matériel fabriqué à l'établi se vend à l'étal. |
| Agriculteur | L'**étal** vend la production et rachète les récoltes. Le **grenier communal** peut **payer les dépôts** au prix fixé par la ville, sur le trésor : la ville achète ses réserves. |
| Médecin | Le médecin émet une **facture de soins** depuis le dossier médical, avec les soins listés. Elle peut être réglée par le trésor si la ville rend certains soins gratuits (réglable par type de soin). |
| Policier | Une **contravention** devient une facture publique : payée au guichet, elle est marquée payée au casier sans geste du policier. Une **caution** peut être versée au trésor pour sortir de cellule plus tôt (réglable). Les pièces se fouillent et se confisquent comme le reste. |
| Tous | Les **salaires publics**, la **taxe sur les ventes**, le **cambriolage** du trésor ou d'un étal. |

Ces branchements modifient les cahiers des métiers : chaque lot de métier qui les livre met son cahier à
jour.

## 7. Découpage conseillé

À livrer avant les autres lots, à la demande de Killian. Chaque lot est jouable seul, visuels finaux
compris.

| Lot | Contenu | Ce qu'il prouve |
|---|---|---|
| E1 | Pièces déclarées, valeurs, change, vérification des recettes, registre partagé ; 68, 69, 77 | L'argent existe, ne vient que de la presse, et l'équipe le voit |
| E2 | 70, 71, 72 | Les joueurs commercent entre eux, même absents |
| E3 | 73, 74, 75, 76 | La ville a des finances : elle encaisse, elle paie, elle peut manquer |

Les branchements de la section 6 suivent chacun le lot de leur métier. Celui de l'ingénieur peut se faire
dès le lot 4 (compteurs), qui est en cours.

## 8. Vérification

- **Lancement réel obligatoire à chaque lot.**
- **GameTests** pour les règles :
  - aucune recette chargée ne produit de pièce, hors change à valeur égale ;
  - la presse refuse un non-trésorier ;
  - le plafond de frappe tient ;
  - la bourse rend la monnaie exacte ;
  - un échange n'a lieu qu'après les deux confirmations ;
  - l'étal vend vendeur absent ;
  - une trémie ne vide pas un étal ;
  - un étal plein ne se casse pas ;
  - la taxe arrive au trésor ;
  - un salaire impayé reste dû ;
  - une facture payée au guichet est marquée payée chez son émetteur ;
  - le total frappé moins le total détruit est juste après une pièce jetée dans la lave.
- **Scénario d'acceptation** : l'équipe désigne un trésorier ; il frappe ; il verse un salaire ; le policier
  achète du pain à l'étal de l'agriculteur absent ; l'agriculteur récupère ses recettes ; on relance le
  serveur ; on lit le tableau de bord, et chaque pièce a une origine.
- **Scénario d'abus** : tenter de dupliquer des pièces par déconnexion pendant un échange, par casse d'un
  étal, et par une recette CraftTweaker. Aucune ne doit réussir.

## 9. Choix faits par l'auteur du document

Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- monnaie physique d'abord, comptes au guichet ensuite ;
- valeurs de 1 à 10 000 cuivres, par facteur 10 ;
- la pièce vaut par la confiance, pas par son métal ; elle ne se fond pas ;
- la création est réservée à un rôle dynamique désigné en config, l'équipe du serveur à défaut ;
- la presse est plafonnable par session ;
- pas d'intérêt, pas de prêt, pas de marchand PNJ ;
- l'étal vend en l'absence du vendeur et peut aussi racheter ;
- la taxe sur les ventes est à 0 % au départ ;
- les salaires sont payés sur le trésor, jamais créés à partir de rien ;
- le solde du trésor est public ;
- le tableau de bord ne compte pas les pièces des coffres une à une.

## 10. Questions ouvertes

Elles décident de l'équilibre général et sont à trancher avant le lot E1 :
- **Qui frappe ?** Un trésorier joueur, l'équipe du serveur seule, ou les deux ?
- **D'où vient le premier argent ?** Une dotation de départ par nouveau joueur, un salaire de lancement
  versé par la ville, la vente de la première récolte au grenier, ou un mélange ?
- **Le rubis** se frappe-t-il comme les autres, ou est-il réservé à l'équipe du serveur, par exemple pour
  des récompenses d'événement ?
- **Faut-il les comptes** (feature 76) dès le lot E3, ou une monnaie uniquement physique suffit-elle au
  début ?
