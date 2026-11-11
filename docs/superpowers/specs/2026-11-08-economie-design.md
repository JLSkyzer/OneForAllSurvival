# Cahier des charges : l'économie de Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Killian a demandé ce cahier le 2026-11-08, une fois les pièces de monnaie modélisées. Il veut le
livrer **avant tous les autres lots**, pour que les métiers s'y branchent au fur et à mesure.
Le même jour, il a ajouté le butin des mobs, les quêtes journalières et hebdomadaires, un PNJ changeur
(features 78 à 80), puis les fournisseurs d'électricité (83). Il a enfin rendu la monnaie
**entièrement physique** : plus de presse, de comptes, de distributeur ni de carte bancaire, mais un
coffre-fort par joueur, des factures prélevées en pièces, une somme de départ et les injections de
l'équipe (features 84 à 88). Ses réponses sont
regroupées en section 9.

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
- Une monnaie **entièrement physique** : des pièces qu'on porte, qu'on range dans son coffre-fort, qu'on
  peut se faire voler. Il n'existe aucun argent qui ne soit pas une pièce quelque part.
- Une **création monétaire contrôlée** : personne ne fabrique de l'argent, et chaque source est
  déclarée et comptée.
- Des **raisons de sortir et de jouer** qui rapportent : chasser, accomplir des quêtes.
- Des **circuits** clairs :
  - d'où vient l'argent (sources) ;
  - où il disparaît (puits) ;
  - comment il passe d'un joueur à l'autre.
- Des **branchements** prêts pour les métiers : factures d'électricité, amendes, soins, commerce agricole.
- Des outils pour que l'équipe du serveur **surveille et règle** l'économie sans recompiler.

### Hors périmètre
- **Comptes en banque, virements, cartes bancaires, distributeurs** : retirés par Killian le
  2026-11-08. L'argent n'existe qu'en pièces.
- **Bourse des valeurs, prêts à intérêt, actions** : trop modernes pour une ville naissante, et sources
  d'abus. (À ne pas confondre avec la bourse de la feature 69, le porte-monnaie.)
- **Les villageois vanilla** gardent leurs émeraudes et n'acceptent pas les pièces. Les seuls PNJ qui
  manient la monnaie sont le changeur (feature 80) et les deux PNJ du casino, qui vendent et rachètent
  des jetons contre des pièces du coffre du casino ([cahier du casino](2026-11-11-casino-roulette-design.md)).
  Tous sont placés par l'équipe du serveur.
- **Fixer les prix** : ils relèvent des joueurs, sauf les tarifs publics (amendes) que la ville règle.
  Le prix de l'électricité est fixé par chaque fournisseur, sous un plafond fixé par le maire
  (feature 83).

### Ce qui serait un échec, même si tout fonctionne
- Une façon de **créer** de l'argent hors des sources déclarées (duplication, recette oubliée, ferme à
  mobs, quête répétée en boucle).
- Une façon de **cacher** des pièces hors des stockages autorisés (feature 84).
- Une inflation que l'équipe ne voit pas venir faute de chiffres.
- Un joueur absent qui perd tout son argent sans avoir pu réagir. Le vol reste possible, mais il
  laisse des traces.
- Un système si lourd que les joueurs préfèrent troquer.
- Des quêtes qui deviennent une corvée quotidienne obligatoire pour gagner sa vie.

### Règle de conception
Une feature économique doit dire **d'où vient l'argent qu'elle fait circuler et où il va**.

Il existe exactement quatre **sources** qui créent de l'argent :
- le butin des mobs (78) ;
- les récompenses de quêtes (79) ;
- la somme de départ d'un nouveau joueur (88) ;
- l'injection par l'équipe du serveur (87).

Chacune est réglable et comptée à part au tableau de bord (77). Une feature qui crée de
l'argent hors de ces sources est refusée. Il n'y a **pas de frappe** : aucun joueur ne fabrique de
pièces.

## 3. Contraintes techniques

### Existant
- Les modèles des cinq pièces (`copper_coin`, `silver_coin`, `gold_coin`, `emerald_coin`, `ruby_coin`,
  espace de noms `oneforallsurvival`) sont sur `main`, dans `art/pieces-monnaie/`. Il y a les modèles,
  les textures 64 × 64 et les projets Blockbench. **Les objets ne sont pas encore déclarés dans le mod.**
  Le premier lot les copie dans `src/main/resources` et les enregistre.
- Le serveur garde l'inventaire à la mort (`keepInventory`), et un joueur ne meurt pas hors d'un `/kill`
  de l'équipe (cahier du médecin) : les pièces ne se perdent pas à la mort.

### Intégration
- Pièces, valeurs, tarifs, taxes, salaires et listes d'objets : données et config, pour que l'équipe
  règle l'équilibrage et que CraftTweaker puisse agir.
- **Aucune recette de craft ne produit une pièce.** Une vérification au démarrage le garantit, recettes
  CraftTweaker comprises, et signale en journal toute recette qui en produirait une.
- Les montants s'expriment en **cuivres**, l'unité de compte, et s'affichent dans la plus grande coupure
  possible (« 2 or 3 argent 5 cuivre »).
- Les écritures (trésor, coffres-forts, factures) sont conservées par le serveur et survivent aux
  relances.
- **Échéances au calendrier réel.** Les factures automatiques et les salaires se comptent sur le calendrier réel, à minuit (heure et fuseau réglables). Si le serveur est éteint à
  minuit, ce qui était dû est traité à son démarrage, une fois par jour manqué.

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
  sur une table de craft ou auprès du PNJ changeur (feature 80). Ces recettes ne créent rien, elles échangent à
  valeur égale. C'est la seule exception à la règle « aucune recette ne produit une pièce ».
- L'infobulle d'une pièce indique sa valeur. Une pile indique son total.
- Une pièce ne se fond pas, ne se composte pas, ne se vend pas à un villageois.

### Le maire
- Le **maire** est un rôle **codé en dur** dans le mod, comme les métiers qui le sont déjà. Il a son
  [cahier](2026-11-08-metier-maire-design.md).
- Il est **élu en RP**, puis l'équipe du serveur lui donne le grade par commande.
- Dans l'économie, il tient le trésor de la ville (73), y dépense publiquement, règle les taxes et
  l'**impôt sur les coffres-forts** (0 à 35 %), plafonne le prix de l'électricité, et peut modifier
  ou supprimer un fournisseur (83). Le cahier du téléphone lui donne les mêmes pouvoirs sur les
  opérateurs.
- Tant qu'aucun maire n'est désigné, seule l'équipe du serveur fait ces gestes.

### Qui fabrique, qui pose, qui gère

| Bloc ou objet | Fabriqué par | Posé par | Géré par | Volable |
|---|---|---|---|---|
| Bourse (69) | n'importe quel joueur | — | son porteur | oui, comme tout objet |
| Coffre-fort (85) | n'importe quel joueur | son propriétaire, un seul | son propriétaire | oui, au crochetage, avec trace |
| Étal de marché (71) | n'importe quel joueur | son propriétaire, autant qu'il veut | son propriétaire | oui, au crochetage, avec trace |
| Trésor de la ville (73) | — | l'équipe du serveur, un seul ; déplaçable par un ingénieur, le maire ou l'équipe | le maire | oui, au crochetage, avec trace |
| Tableau de la mairie (73) | — | le maire, plusieurs ; déplaçable par un ingénieur, le maire ou l'équipe | affichage seul | — |
| Poste de fournisseur (83) | l'ingénieur | n'importe quel joueur | le fournisseur | non, sa caisse se vide par le fournisseur |
| PNJ changeur (80) | — | l'équipe, par commande | — | non, invulnérable |
| Coffre du casino (cahier du casino) | — | l'équipe, incassable | le statut casino | non |
| PNJ vendeur et racheteur de jetons (cahier du casino) | — | l'équipe, reliés au coffre | — | non, invulnérables |
| Bureau du maire (cahier du maire) | — | l'équipe, un seul ; déplaçable par un ingénieur, le maire ou l'équipe | le maire | non |

### Registre
Le mécanisme de registre du grenier communal sert à toute l'économie : chaque mouvement est noté, avec
l'auteur, le montant, l'heure et le motif. Le nombre de lignes est plafonné. Il est écrit une fois et
partagé.

## 5. Features

La numérotation prolonge celle du policier. Toutes les valeurs chiffrées sont des valeurs de départ, à
équilibrer.

**Features retirées le 2026-11-08 par Killian** : 68 (presse à monnaie), 74 (guichet), 76 (comptes au guichet), 81
(distributeur), 82 (carte bancaire). Leurs numéros ne sont pas réutilisés.

### Garder son argent

**84. Où les pièces peuvent être rangées.**
Demandé par Killian le 2026-11-08.
- Une pièce ne peut être que dans :
  - l'**inventaire** d'un joueur ;
  - une **bourse** (69) ;
  - le **coffre-fort** de son propriétaire (85) ;
  - la caisse d'un **étal de marché** (71) ;
  - le **trésor de la ville** (73) ;
  - la caisse d'un **poste de fournisseur** (83) ou d'une **antenne** d'opérateur (cahier du
    téléphone), qui reçoivent les prélèvements ;
  - le **coffre d'un casino** ([cahier du casino](2026-11-11-casino-roulette-design.md), feature 110).
- **Partout ailleurs, elle est refusée** : coffres, tonneaux, boîtes de Shulker, coffre de l'Ender,
  sacs et stockages des autres mods, cadres, porte-armures.
  - Dans un **écran de stockage**, la case refuse la pièce. Tous les écrans du jeu et des mods
    passent par le même mécanisme de case, ce qui couvre aussi les mods.
  - Les **automatismes** (entonnoirs, tapis et entonnoirs de Create, tuyaux) ne prennent pas une
    pièce au sol et n'en poussent pas dans un stockage interdit.
- Une pièce peut être **jetée au sol** et ramassée par n'importe qui.
- C'est le point le plus fragile de l'économie : un mod de stockage peut passer par un chemin non
  prévu. Chaque cas trouvé est ajouté à une liste en config. Un test passe en revue les stockages des
  mods installés.

**85. Coffre-fort personnel.**
Demandé par Killian le 2026-11-08.
- **N'importe quel joueur** le fabrique (recette en données), choix de Killian.
- Chaque joueur peut poser **un seul** coffre-fort. Pour en poser un autre, il doit d'abord casser le
  premier, vide.
- Il ne contient **que des pièces**, sans limite de quantité. Son total s'affiche à l'ouverture.
- Son contenu est soumis à l'**impôt** fixé par le maire, prélevé chaque jour à minuit au profit du
  trésor (cahier du maire, feature 106).
- Seuls son propriétaire et l'équipe du serveur l'ouvrent et le cassent.
- Il **peut être cambriolé, difficilement** : il se force avec l'outil de crochetage du policier
  (feature 67), plus lentement qu'un coffre verrouillé. L'effraction laisse une trace visible et
  s'inscrit au registre du coffre, que son propriétaire lit.
- Son contenu est gardé par le serveur, pas seulement dans le bloc. Un prélèvement (86) peut donc s'y
  faire même si le propriétaire est déconnecté et que la zone n'est pas chargée.

### Création et circulation

**69. Bourse.**
- C'est un objet porté qui **ramasse** les pièces, les range et affiche le total.
- **N'importe quel joueur** la fabrique, par une recette simple (choix de Killian).
- Payer avec la bourse **rend la monnaie** automatiquement : le change se fait sans passer par la table.
- On peut la perdre, la laisser tomber, se la faire voler, et la fouiller (feature 52 du policier).
- C'est le seul objet qui contient des pièces (feature 84). Une bourse ne se range pas dans un stockage
  interdit si elle contient des pièces.

**70. Paiement de main à main.**
- C'est une **fenêtre d'échange** entre deux joueurs proches. Chacun pose objets et pièces, et l'échange
  n'a lieu que si les deux confirment l'état final.
- Elle évite l'arnaque du « jette d'abord ». Rien n'empêche de payer en jetant des pièces.
- Chaque échange peut produire un **reçu** (objet) pour les deux parties, sur demande.

**86. Prélèvement automatique.**
Demandé par Killian le 2026-11-08.
- Il paie toutes les sommes dues **à distance**, sans que personne ait à se déplacer :
  - l'électricité (83) et l'abonnement du téléphone ;
  - les **factures publiques** : amendes, cautions, factures de la ville ;
  - l'impôt sur les coffres-forts (cahier du maire).
- Un joueur ne peut pas émettre lui-même une facture prélevable. Les factures entre joueurs (soins,
  travaux) se règlent de main à main.
- À l'échéance, le montant est pris **en pièces** :
  1. dans l'**inventaire** du joueur, bourse comprise, s'il est connecté ;
  2. sinon, ou s'il n'y en a pas assez, dans son **coffre-fort** ;
  3. sinon, le **paiement est refusé**.
- Le prélèvement se fait **en entier ou pas du tout** : pas de paiement partiel. La monnaie est rendue
  au besoin.
- Les pièces vont au **trésor** pour une somme publique, ou dans la caisse du poste du fournisseur ou de
  l'antenne de l'opérateur, où le créancier les retire.
- Un paiement refusé laisse la facture **due**. Le débiteur est **prévenu** (message à la connexion, et
  notification du téléphone s'il en a un), ainsi que le créancier, qui décide de couper ou non.
- Une facture due est **représentée chaque jour à minuit** jusqu'à ce qu'elle soit payée. Le débiteur
  peut aussi la payer **tout de suite** : en utilisant l'objet facture (ou l'avis de contravention), qui
  prend les pièces de son inventaire, ou depuis son téléphone.
- Le paiement marque la facture payée là où elle a été émise : le relevé du compteur, le casier
  judiciaire.
- Chaque prélèvement s'inscrit au registre.

### Commerce

**71. Étal de marché.**
- C'est un bloc de commerce. **N'importe quel joueur** le fabrique, et celui qui le pose en est le
  **propriétaire** (choix de Killian). Il en pose autant qu'il veut.
- Le propriétaire le garnit d'objets et fixe un prix par lot.
- Un acheteur paie avec ses pièces ou sa bourse et reçoit le lot, **même si le vendeur est absent**.
- Le vendeur récupère ses recettes dans l'étal. Seul lui peut retirer la marchandise et l'argent. Les
  trémies et entonnoirs peuvent le garnir, pas le vider.
- L'étal ne se casse que vide, par son propriétaire ou l'équipe du serveur.
- Il **peut être cambriolé**, comme le coffre-fort (choix de Killian) : il se force avec l'outil de
  crochetage du policier (feature 67), lentement. Le voleur prend la caisse et la marchandise.
  L'effraction laisse une trace visible et s'inscrit au registre de l'étal, que son propriétaire lit.
- Une **taxe sur les ventes** (0 % au départ, réglable) part au trésor à chaque vente.
- L'étal peut aussi **racheter** : le propriétaire fixe un prix d'achat et une quantité
  maximale, payés sur ses recettes. L'agriculteur peut ainsi acheter le blé de ses voisins.

**72. Facture.**
- C'est un document (objet) émis par un joueur à un autre : émetteur, débiteur, motif, montant, date.
- Il est **payable** de main à main, ou en l'utilisant, ce qui prend les pièces de l'inventaire. Payée,
  la facture devient un **reçu**.
- Les factures publiques et automatiques (amendes, électricité, téléphone) sont prélevées à distance
  (feature 86).
- Les métiers produisent leurs factures par ce même objet (section 6).

### La ville

**73. Trésor de la ville.**
- C'est la **banque de l'île** : un **coffre-fort** tenu par le maire, avec un registre. C'est le
  **seul bloc** de la ville qui manie l'argent public (choix de Killian, qui a retiré le guichet).
- Il **reçoit à distance** les taxes, les amendes, les factures publiques et l'impôt, prélevés chez
  les joueurs (feature 86). Il **envoie à distance** les salaires (feature 75). Personne n'a besoin de
  venir à la mairie pour payer ou être payé.
- Il n'y en a **qu'un**, posé par l'**équipe du serveur** à la mairie (choix de Killian). Seuls le maire
  et l'équipe l'ouvrent.
- **Déplacement** : un ingénieur (avec son outil de maintenance, comme pour les antennes), le maire ou
  l'équipe peut le casser pour le reposer ailleurs (choix de Killian). Les pièces ne tombent pas :
  le contenu est gardé par le serveur et le bloc cassé redevient le trésor unique, avec son contenu et
  son registre, là où on le repose. Il ne peut pas exister deux trésors, et le déplacement s'inscrit au
  registre.
- **En créatif**, casser un bloc ne rend pas d'objet. L'équipe casse donc le trésor (accroupie, pour
  éviter un accident), puis en pose un neuf pris dans l'onglet créatif du mod : il reprend aussitôt le
  trésor unique, avec son contenu et son registre. Poser un trésor alors qu'il en existe déjà un est
  refusé.
- **Sans emplacement**, entre la casse et la repose, le trésor continue de fonctionner : prélèvements,
  salaires et impôt passent, puisque son contenu est gardé par le serveur. Seuls l'ouverture et le
  crochetage sont impossibles.
- La même règle vaut pour le **bureau du maire**.
- Son solde est public, affiché sur un **tableau de la mairie**. Le maire peut poser ce tableau en
  plusieurs exemplaires dans la ville ; il affiche aussi les annonces et le registre des décisions
  (cahier du maire). La ville montre ses comptes.
- Il peut être cambriolé comme un coffre verrouillé (feature 67 du policier) : le registre montre alors un
  trou entre le solde attendu et le contenu.

**75. Salaires publics.**
- Chaque rôle peut recevoir un **salaire**, réglé en config ou par commande. À la création, seuls les
  rôles publics sont proposés : policier, médecin, maire.
- Le salaire est dû au **temps passé connecté dans le rôle**. Il est arrêté chaque jour à minuit et
  **envoyé à distance** au joueur : dans son **coffre-fort** ; s'il n'en a pas, dans son inventaire s'il
  est connecté ; sinon, il attend sa prochaine connexion.
- Il est payé **sur le trésor**. Si le trésor ne suffit pas, le salaire reste **dû** et s'affiche comme
  une dette de la ville : une ville pauvre a des conséquences visibles.

**83. Fournisseur d'électricité.**
Demandé par Killian le 2026-11-08, sur le modèle des opérateurs du téléphone.
- **N'importe quel joueur peut devenir fournisseur.** Il pose un **poste de fournisseur**, un bloc
  fabriqué par l'ingénieur, en tête du réseau qu'alimentent ses générateurs. Le poste porte le nom du
  fournisseur. La ville peut être un fournisseur comme les autres.
- **Le fournisseur fixe son prix** : un prix par FE consommé et, s'il le veut, un abonnement fixe par
  jour. Le prix par FE ne peut pas dépasser le **plafond fixé par le maire**. Le fournisseur le change
  depuis le panneau du poste. Le nouveau prix s'applique à la facture suivante, et les clients en sont
  prévenus.
- **Branchement.** Le courant suit ce chemin :

  ```
  générateurs → poste du fournisseur → câbles du fournisseur → compteur → disjoncteur → maison du client
  ```

  - Le **poste** est en tête du réseau : tout le courant vendu passe par lui, et il compte ce qui sort.
  - Le **compteur** (feature 8) est posé en ligne à l'entrée de chaque client. Il compte les FE qui
    entrent dans cette maison, et c'est cette mesure qui fait la facture.
  - Le **disjoncteur d'abonné** (feature 8) suit le compteur et coupe la maison.
  - **Les câbles viennent de n'importe quel mod.** Le mod n'ajoute pas de câble à lui. Le poste, le
    compteur et le disjoncteur exposent l'énergie Forge (FE) sur toutes leurs faces : tout câble,
    connecteur ou conduit qui transporte des FE s'y branche, quel que soit son mod. Les fils suspendus
    de Create: Crafts & Additions, qui pendent entre deux connecteurs comme de vraies lignes, sont le
    cas de référence pour les essais.
  - Le compteur doit mesurer juste quel que soit le câble : il compte ce qui le traverse réellement,
    et ne laisse passer que ce que la maison consomme.
- **Rattachement.** Le mod ne sait pas de quel réseau vient le courant, car les câbles des mods
  d'ingénierie ne le disent pas. Le fournisseur ou un ingénieur **jumelle** donc chaque compteur au
  poste avec le testeur : un clic sur le poste, puis un sur le compteur, qui affiche le nom du
  fournisseur. Le client apparaît alors dans le panneau. Tant qu'un compteur n'est pas rattaché et
  accepté, il ne laisse pas passer le courant.
- Le panneau liste les **clients** : un compteur au nom de chacun, avec sa consommation et ses
  impayés. Le fournisseur y accepte ou refuse une demande de raccordement, et peut **couper** un client
  en ouvrant son disjoncteur à distance. Le scellé reste un geste d'ingénieur.
- **Vol de courant.** Le panneau affiche l'écart entre ce que le poste a fait sortir et ce que les
  compteurs ont mesuré. Un écart élevé signale une perte ou un branchement pirate sur un câble, avant un
  compteur. Le policier prend le relais en RP.
- **La facture est automatique et quotidienne.** Chaque jour à minuit, chaque compteur se relève seul
  et émet une facture (feature 72) : consommation du jour fois prix, plus l'abonnement. Elle est
  **prélevée en pièces** (feature 86), et les pièces vont dans la **caisse du poste**, où le
  fournisseur les retire.
- Le compteur mesure les FE qui passent vraiment : un fournisseur ne facture que ce qu'il a livré.
- Une **taxe sur l'électricité** (0 % au départ, réglable) part au trésor à chaque facture payée.
- **Sécurité.** Le poste, les compteurs et les disjoncteurs posés par un fournisseur lui appartiennent.
  Seuls ce fournisseur, un **ingénieur**, le **maire** et l'**équipe du serveur** peuvent les casser
  ou interagir avec eux (ouvrir, régler, jumeler, réenclencher). Le client lui-même ne peut pas toucher
  à son compteur ni à son disjoncteur. Le maire peut aussi **modifier** les réglages d'un fournisseur
  ou le **supprimer** : ses clients sont détachés, et leurs compteurs ne facturent plus.
- **La ligne reste vulnérable**, par choix de Killian : les câbles et les connecteurs, qui viennent
  d'autres mods, ne sont pas protégés. N'importe qui peut couper une ligne ou s'y piquer. L'écart
  affiché au poste et le RP (policier) prennent le relais.
- Pour changer de fournisseur, le client demande un raccordement à un autre. Le nouveau fournisseur ou
  un ingénieur rebranche ses câbles et rejumelle son compteur au nouveau poste.
- Les pièces passent du client au fournisseur : la feature ne crée rien.

### Gagner sa vie

**78. Butin des mobs.**
- Tuer un mob **hostile** donne une **chance** de faire tomber des pièces. Une table par type de mob, en
  données, fixe la chance et le montant. Au départ :
  - quelques cuivres sur un zombie ou une araignée ;
  - de l'argent sur un mob plus dangereux (sorcière, creeper chargé) ;
  - davantage sur un boss.
- Les **animaux** ne donnent rien : élever ne doit pas devenir une planche à billets.
- **Pas de plafond** (choix de Killian) : seuls les mobs hostiles donnent des pièces, et chaque mob
  tué peut en donner.
- **Seul garde-fou contre les fermes automatiques** : rien pour un mob tué sans joueur, ou apparu par un
  générateur (spawner) ou par un œuf d'apparition.
- Le butin passe par un modificateur de butin global de Forge : les mobs des autres mods sont couverts par
  leur tag ou par la table.

**79. Quêtes journalières et hebdomadaires.**
- Chaque joueur reçoit **3 quêtes journalières** et **5 quêtes hebdomadaires**, générées pour lui par un
  algorithme.
- **Renouvellement** : à heure fixe, sur le calendrier réel (fuseau réglable), tous les jours pour les
  journalières et chaque lundi pour les hebdomadaires. Une quête non terminée expire simplement, sans
  pénalité.
- **Génération** :
  - un tirage pondéré dans une réserve de **modèles** de quêtes, en données ;
  - chaque modèle a une catégorie, une cible facultative (un bloc, un mob, un objet précis, ou un tag), une
    fourchette de quantité et une récompense de base ;
  - la quantité est tirée dans la fourchette, et la récompense suit la quantité et la difficulté ;
  - pas deux quêtes du même modèle dans une même série ;
  - une hebdomadaire vise environ cinq fois une journalière, en effort et en récompense ;
  - une partie des modèles peut être **pondérée par rôle**, pour proposer plus souvent à un agriculteur
    des quêtes de récolte, sans jamais l'y obliger.
- **Une relance** par jour : le joueur peut redemander une quête qui ne lui plaît pas.
- Un **écran de quêtes** (touche réglable) montre la progression. Une notification discrète annonce une
  quête terminée. La récompense se **réclame** dans l'écran et arrive dans la bourse ou l'inventaire.
- **Récompenses** : des **pièces seulement**, comptées comme source au tableau de bord, avec un plafond
  hebdomadaire par joueur.

Catégories demandées par Killian :

| Catégorie | Exemple |
|---|---|
| Casser des blocs | Casser 200 blocs de pierre |
| Casser un bloc précis | Extraire 12 minerais de fer |
| Poser des blocs, éventuellement précis | Poser 64 briques |
| Tuer des mobs, éventuellement précis | Tuer 10 squelettes |

Catégories proposées en plus, choisies pour ne pas être des corvées :

| Catégorie | Exemple | Pourquoi |
|---|---|---|
| Récolter | Récolter 32 carottes mûres | Fait vivre les champs |
| Pêcher | Pêcher 8 poissons | Une activité calme |
| Cuisiner | Préparer 3 plats de Farmer's Delight | Donne un usage aux recettes |
| Livrer | Déposer 16 pains au grenier communal | L'effort profite à toute la ville |
| Élever | Faire naître 4 animaux | Fait vivre les enclos |
| Métier | Réparer 2 machines, soigner 3 joueurs, dresser 2 contraventions | Valorise les rôles, proposé seulement au rôle concerné |
| Conserver | Sécher ou fumer 10 aliments | Prépare les réserves |

- **Garde-fous** :
  - un bloc **posé par un joueur** ne compte pas pour une quête de casse, et un bloc posé puis cassé
    aussitôt ne compte pas pour une quête de pose ;
  - les mobs comptent selon les mêmes règles que le butin (pas de générateur, pas de ferme) ;
  Le suivi des blocs posés est limité en taille et oublie les plus anciens.
- L'équipe du serveur peut ajouter des **modèles** et lancer des **quêtes d'événement** communes à tous,
  par commande.

**80. PNJ changeur.**
- C'est un personnage que l'équipe du serveur **fait apparaître** par commande où elle veut, au spawn par
  exemple. Il est **invulnérable**, immobile, et ne disparaît jamais. Son nom et son apparence sont
  réglables (skin de joueur ou modèle du mod).
- Il ne fait **qu'une chose : le change**. Il convertit les pièces en plus grosses ou en plus petites
  coupures (dix argents contre un or, un or contre dix argents), à valeur égale et sans frais.
- Il ne vend rien et n'achète rien : il ne crée ni ne détruit d'argent.
- L'équipe peut en poser plusieurs, par exemple au port et à la mine.

**88. Somme de départ.**
Demandé par Killian le 2026-11-08.
- À sa **première connexion**, un joueur reçoit une petite somme dans son inventaire, de quoi acheter
  à manger les premiers jours : **500 cuivres**, soit 5 pièces d'or (choix de Killian, réglable).
- Une seule fois par joueur, jamais à une reconnexion ni après un changement de nom.
- C'est une source, comptée à part au tableau de bord.

**87. Injection par l'équipe du serveur.**
Demandé par Killian le 2026-11-08.
- L'équipe du serveur peut **donner des pièces** à un joueur, ou en verser au trésor, par commande.
  C'est son levier pour **piloter l'économie** : relancer une ville pauvre, récompenser un événement.
- C'est le **seul** moyen d'obtenir un **rubis** : aucune autre source n'en donne.
- Chaque injection s'inscrit au registre, avec l'auteur et le motif, et compte comme source au tableau
  de bord.
- L'équipe peut aussi **retirer** des pièces par commande (puits), notées de la même façon.

### Pilotage

**77. Tableau de bord de l'équipe.**
- Par commande, l'équipe du serveur lit :
  - le **total créé par source** : butin des mobs, quêtes, somme de départ, injections de l'équipe ;
  - le **total détruit** (pièces brûlées, tombées dans le vide, disparues avec un objet au sol,
    retirées par l'équipe) ;
  - la **masse dans les coffres-forts**, que le serveur connaît exactement ;
  - le **solde du trésor** ;
  - les **salaires dus** ;
  - les **factures dues** après un prélèvement refusé ;
  - le **volume des ventes** par période.
- La masse en circulation se déduit : total créé, moins total détruit. Les pièces dans les
  inventaires, les bourses et les étals ne sont pas comptées une à une, ce qui serait trop coûteux.
- Les registres du trésor et des injections se consultent en entier.

## 6. Branchements avec les métiers

Chaque branchement se fait au lot du métier concerné, une fois l'économie livrée.

| Métier | Branchement |
|---|---|
| Ingénieur | Il fabrique les **postes de fournisseur** (feature 83), pose et rebranche les compteurs, et pose les scellés. Les factures d'électricité sont émises automatiquement au prix de chaque fournisseur et prélevées en pièces (feature 86). Impayée, une facture justifie un scellé en RP. Le matériel fabriqué à l'établi se vend à l'étal. |
| Agriculteur | L'**étal** vend la production et rachète les récoltes. Le **grenier communal** peut **payer les dépôts** au prix fixé par la ville, sur le trésor : la ville achète ses réserves. |
| Médecin | Le médecin émet une **facture de soins** depuis le dossier médical, avec les soins listés. Elle peut être réglée par le trésor si la ville rend certains soins gratuits (réglable par type de soin). |
| Policier | Une **contravention** devient une facture publique, prélevée à distance (feature 86) : payée, elle est marquée payée au casier sans geste du policier. Une **caution** peut être versée au trésor pour sortir de cellule plus tôt (réglable). Les pièces se fouillent et se confisquent comme le reste. Son outil de crochetage force aussi un coffre-fort (feature 85). |
| Maire | Voir son [cahier](2026-11-08-metier-maire-design.md) : trésor et dépenses publiques, taxes, impôt sur les coffres-forts, barème des amendes, plafond du prix de l'électricité, fournisseurs. |
| Communication | L'**abonnement du téléphone** est prélevé chaque jour en pièces (feature 86) et va dans la caisse de l'antenne de l'opérateur. Voir le [cahier du téléphone](2026-11-08-telephone-design.md). |
| Tous | Les **salaires publics**, la **taxe sur les ventes**, le **cambriolage** du trésor, d'un étal ou d'un coffre-fort. |

Ces branchements modifient les cahiers des métiers : chaque lot de métier qui les livre met son cahier à
jour.

## 7. Découpage conseillé

À livrer avant les autres lots, à la demande de Killian. Chaque lot est jouable seul, visuels finaux
compris.

| Lot | Contenu | Ce qu'il prouve |
|---|---|---|
| E1 | Pièces déclarées, valeurs, change, vérification des recettes, registre partagé ; 84, 85, 69, 87, 88, 77 | L'argent existe, ne vient que de sources déclarées, ne se range qu'aux endroits permis, et l'équipe le voit |
| E2 | 70, 71, 72 | Les joueurs commercent entre eux, même absents |
| E3 | 73, 75 | La ville a des finances : elle encaisse, elle paie, elle peut manquer |
| E4 | 78, 80 | On gagne sa vie en chassant, et on fait la monnaie auprès d'un changeur |
| E5 | 79 | Des objectifs renouvelés chaque jour et chaque semaine |
| E6 | 86, 83 | Les factures se paient seules en pièces, et chacun peut vendre son électricité |

Le lot E4 peut passer avant E2 si la ville a besoin de premiers revenus plus tôt : le butin des mobs
répond en partie à la question « d'où vient le premier argent », avec la somme de départ. Le lot E6
vient après E2 (factures) et a besoin du réseau électrique de l'ingénieur. Il **modifie** le compteur
et le disjoncteur de la feature 8, déjà livrés au lot 4 de l'électricité : jumelage au poste,
protection, facture quotidienne et coupure à distance. Les quêtes de métier
(feature 79) arrivent avec chaque métier. Les branchements de la section 6 suivent chacun le lot de
leur métier. Celui de l'ingénieur vient avec le lot E6.

## 8. Vérification

- **Lancement réel obligatoire à chaque lot.**
- **GameTests** pour les règles :
  - aucune recette chargée ne produit de pièce, hors change à valeur égale ;
  - une pièce est refusée dans un coffre, une boîte de Shulker, le coffre de l'Ender et le stockage de
    chaque mod installé, et un entonnoir ne la ramasse pas ;
  - une pièce est acceptée dans l'inventaire, la bourse, le coffre-fort, l'étal et le trésor ;
  - un joueur ne peut pas poser un deuxième coffre-fort ;
  - un autre joueur ne peut ni ouvrir ni casser un coffre-fort, sauf à le forcer avec l'outil de
    crochetage, qui laisse une trace ;
  - la bourse rend la monnaie exacte ;
  - un échange n'a lieu qu'après les deux confirmations ;
  - l'étal vend vendeur absent ;
  - une trémie ne vide pas un étal ;
  - un étal plein ne se casse pas ;
  - la taxe arrive au trésor ;
  - un salaire impayé reste dû ;
  - une amende prélevée à distance arrive au trésor et est marquée payée au casier ;
  - une facture due est représentée à chaque minuit, et se paie aussi en utilisant l'objet facture ;
  - un salaire arrive au coffre-fort à minuit, ou attend la connexion d'un joueur sans coffre-fort ;
  - le total créé moins le total détruit est juste après une pièce jetée dans la lave ;
  - la somme de départ n'est donnée qu'une fois par joueur ;
  - une injection de l'équipe est notée au registre et au tableau de bord ;
  - un mob de générateur ou d'œuf ne donne rien, un animal ne donne rien ;
  - trois journalières différentes sont générées, et une relance en donne une autre ;
  - un bloc posé puis cassé ne fait avancer aucune quête ;
  - une quête de métier n'est pas proposée à un autre rôle ;
  - le PNJ changeur rend exactement la même valeur dans les deux sens ;
  - un prélèvement prend d'abord dans l'inventaire, puis dans le coffre-fort, rend la monnaie, et
    n'est jamais partiel ;
  - un prélèvement sur un joueur déconnecté prend dans son coffre-fort, même zone non chargée ;
  - un prélèvement sans assez de pièces est refusé, laisse la facture due, et prévient le débiteur et
    le créancier ;
  - à minuit, la facture d'un compteur vaut sa consommation du jour fois le prix du fournisseur, plus
    l'abonnement, et les pièces arrivent dans la caisse du poste ;
  - un fournisseur ne peut pas dépasser le plafond de prix du maire ;
  - un serveur éteint deux jours émet deux factures par compteur à son démarrage ;
  - un changement de prix ne touche que la facture suivante ;
  - un fournisseur supprimé par le maire ne facture plus personne ;
  - un compteur non jumelé ou non accepté ne laisse pas passer le courant ;
  - le compteur mesure la même consommation avec un câble de Crafts & Additions, de Create: New Age et
    un câble FE d'un autre mod ;
  - un joueur sans droit (client compris) ne peut ni casser ni ouvrir le poste, un compteur ou un
    disjoncteur d'un fournisseur ; le fournisseur, un ingénieur, le maire et l'équipe le peuvent.
- **Scénario d'acceptation** : un nouveau joueur reçoit sa somme de départ ; l'équipe désigne un maire et
  injecte de l'argent au trésor ; le maire verse un salaire ; le policier achète du pain à l'étal de
  l'agriculteur absent ; l'agriculteur range ses recettes dans son coffre-fort ; à minuit, sa facture
  d'électricité est prélevée dans ce coffre-fort ; on relance le serveur ; on lit le tableau de bord, et
  chaque pièce a une origine.
- **Scénario d'abus** : tenter de dupliquer des pièces par déconnexion pendant un échange, par casse d'un
  étal ou d'un coffre-fort, par une recette CraftTweaker, par une ferme à mobs à générateur, et par une
  boucle « poser, casser » pour une quête. Tenter de cacher des pièces dans le stockage de chaque mod
  installé. Aucune tentative ne doit réussir.
- **Scénario des quêtes** : se connecter, lire ses 3 journalières et 5 hebdomadaires, en terminer une,
  réclamer la récompense, relancer le serveur, retrouver sa progression ; changer la date du serveur et
  constater le renouvellement.

## 9. Décisions et choix

### Décisions de Killian (2026-11-08)
- Plus de **presse à monnaie** : l'argent vient des quêtes, du butin des mobs, de la somme de départ et
  des injections de l'équipe du serveur.
- Le **PNJ ne fait que le change** de coupures : pas de boutique, ni achat ni vente.
- Le **butin des mobs** n'a pas de plafond : seuls les mobs hostiles donnent des pièces.
- Les quêtes ne récompensent **qu'en pièces**.
- Le **rubis** n'est donné que par l'équipe du serveur.
- Une **somme de départ** de 500 cuivres pour chaque nouveau joueur.
- **N'importe quel joueur** fabrique son coffre-fort.
- **Monnaie entièrement physique** : pas de comptes, pas de distributeur, pas de carte bancaire, pas
  d'appli de banque.
- **Pas de guichet** : le trésor est le seul bloc de l'argent public. Taxes, amendes et impôt sont
  prélevés à distance, et les salaires envoyés à distance dans le coffre-fort.
- **Un seul coffre-fort par joueur**, seul stockage personnel des pièces, cambriolable difficilement.
- Les pièces ne vont que dans l'inventaire, la bourse, le coffre-fort, l'étal, le trésor et les caisses
  des fournisseurs et opérateurs.
- Les **factures automatiques** se prélèvent dans l'inventaire, puis dans le coffre-fort, sinon le
  paiement est refusé et le joueur prévenu.
- Électricité et téléphone sont prélevés **chaque jour**.
- **Fournisseurs d'électricité** : n'importe qui peut l'être, avec son prix sous un **plafond du
  maire** ; poste, compteurs et disjoncteurs protégés, câbles de n'importe quel mod, ligne vulnérable.
- Le **maire** est un rôle codé en dur, élu en RP puis nommé par l'équipe.

### Choix faits par l'auteur du document
Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- valeurs de 1 à 10 000 cuivres, par facteur 10 ;
- la pièce vaut par la confiance, pas par son métal ; elle ne se fond pas ;
- pas d'intérêt, pas de prêt ;
- l'étal vend en l'absence du vendeur et peut aussi racheter ;
- la taxe sur les ventes et celle sur l'électricité sont à 0 % au départ ;
- les salaires sont payés sur le trésor, jamais créés à partir de rien ;
- le solde du trésor est public ;
- seuls les mobs hostiles donnent des pièces, jamais ceux des générateurs ;
- les quêtes se renouvellent sur le calendrier réel, avec une relance par jour, et expirent sans
  pénalité ;
- les quêtes de métier ne sont proposées qu'au rôle concerné ;
- un prélèvement n'est jamais partiel ;
- les pièces prélevées vont dans une caisse (poste, antenne) que le créancier vide, plutôt que dans son
  coffre-fort ;
- le coffre-fort ne contient que des pièces.

## 10. Questions ouvertes

- **La table du butin** (pièces par mob) et le **plafond par semaine des quêtes**, à régler aux
  premiers essais.
