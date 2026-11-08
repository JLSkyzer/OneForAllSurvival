# Cahier des charges : l'économie de Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Killian a demandé ce cahier le 2026-11-08, une fois les pièces de monnaie modélisées. Il veut le
livrer **avant tous les autres lots**, pour que les métiers s'y branchent au fur et à mesure.
Le même jour, il a ajouté le butin des mobs, les quêtes journalières et hebdomadaires, et un PNJ de la
ville (features 78 à 80).

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
- Une **création monétaire contrôlée** : personne ne fabrique de l'argent sur une table de craft, et
  chaque source d'argent est déclarée, plafonnable et comptée.
- Des **raisons de sortir et de jouer** qui rapportent : chasser, accomplir des quêtes.
- Des **circuits** clairs :
  - d'où vient l'argent (sources) ;
  - où il disparaît (puits) ;
  - comment il passe d'un joueur à l'autre.
- Des **branchements** prêts pour les métiers : factures d'électricité, amendes, soins, commerce agricole.
- Des outils pour que l'équipe du serveur **surveille et règle** l'économie sans recompiler.

### Hors périmètre
- **Bourse, prêts à intérêt, actions** : trop modernes pour une ville naissante, et sources d'abus.
- **Les villageois vanilla** gardent leurs émeraudes et n'acceptent pas les pièces. Le seul PNJ qui
  manie la monnaie est celui de la ville (feature 80), placé et réglé par l'équipe du serveur.
- **Fixer les prix** : ils relèvent des joueurs, sauf les tarifs publics (amendes) que la ville règle.
  Le prix de l'électricité est fixé par chaque fournisseur (feature 83).

### Ce qui serait un échec, même si tout fonctionne
- Une façon de **créer** de l'argent hors des sources déclarées (duplication, recette oubliée, ferme à
  mobs, quête répétée en boucle).
- Une inflation que l'équipe ne voit pas venir faute de chiffres.
- Un joueur absent qui perd tout son argent sans avoir pu réagir. Le vol reste possible, mais il
  laisse des traces.
- Un système si lourd que les joueurs préfèrent troquer.
- Des quêtes qui deviennent une corvée quotidienne obligatoire pour gagner sa vie.

### Règle de conception
Une feature économique doit dire **d'où vient l'argent qu'elle fait circuler et où il va**.

Il existe exactement quatre **sources** qui créent de l'argent :
- la presse à monnaie (68) ;
- le butin des mobs (78) ;
- les récompenses de quêtes (79) ;
- les achats du PNJ de la ville (80).

Chacune est réglable, plafonnable et comptée à part au tableau de bord (77). Une feature qui crée de
l'argent hors de ces sources est refusée.

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
  sur une table de craft ou auprès du PNJ de la ville. Ces recettes ne créent rien, elles échangent à valeur égale. C'est la seule
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
- C'est un bloc relié au trésor, où l'on **paie une facture publique** (amende) sans que le
  trésorier soit présent. On y paie aussi une facture d'électricité restée impayée (feature 83).
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

**81. Distributeur (ATM).**
Demandé par Killian le 2026-11-08.
- C'est un bloc **fabriqué par l'ingénieur** à son établi et posé où il veut : en ville, au port, dans un
  commerce.
- Il se branche au réseau électrique. **Chaque opération consomme de l'énergie** (réglable, en FE par
  opération). Sans courant, il est hors service et l'écran le dit.
- Il fait ce que fait un distributeur, sur les **mêmes comptes que le guichet** (feature 76) :
  - consulter le solde et les dernières opérations ;
  - retirer et déposer des pièces ;
  - virer une somme à un autre compte ;
  - payer une facture.
- Il sert aussi à **créer des cartes bancaires** (feature 82), une ou plusieurs par compte (3 au plus au
  départ, réglable), et à faire **opposition** sur une carte perdue ou volée.
- Il **ne crée aucun argent**. Un retrait est pris sur le compte, un dépôt y est ajouté. Chaque
  opération s'inscrit au registre, avec le distributeur, la carte et le joueur qui l'a utilisée.
- Seuls son poseur, un ingénieur muni de l'outil de maintenance et l'équipe du serveur peuvent le casser
  ou le régler.

**82. Carte bancaire.**
Demandé par Killian le 2026-11-08.
- C'est un **objet** lié à un compte, créé au distributeur. Elle porte le nom du titulaire et les quatre
  derniers chiffres de son numéro. Son titulaire choisit un **code** à quatre chiffres à la création.
- Elle sert :
  - au distributeur, où un **retrait demande le code** ;
  - dans le **téléphone**, où on l'enregistre pour **payer l'abonnement réseau** à son opérateur.
    L'abonnement est prélevé chaque semaine sur le compte de la carte.
- **Elle peut être perdue ou volée** comme tout objet, fouillée et confisquée (feature 52 du policier).
  Le compte, lui, ne se vole pas : un voleur sans le code ne retire rien, et un voleur qui l'a obtenu
  reste limité par un **plafond de dépense par jour** (réglable).
- L'**opposition** au distributeur ou au guichet désactive la carte aussitôt, partout. Une carte en
  opposition est refusée et l'abonnement réseau qu'elle payait devient impayé.
- Chaque usage s'inscrit au registre avec le joueur qui tenait la carte : le vol est possible, mais il
  laisse des traces.
- Un prélèvement refusé (solde insuffisant, carte en opposition) est signalé au titulaire et à
  l'opérateur, qui décide de suspendre ou non la carte SIM.
- On peut aussi la lier à son compteur électrique, pour payer son fournisseur (feature 83).

**83. Fournisseur d'électricité.**
Demandé par Killian le 2026-11-08, sur le modèle des opérateurs du téléphone.
- **N'importe quel joueur peut devenir fournisseur.** Il pose un **poste de fournisseur**, un bloc
  fabriqué par l'ingénieur, en tête du réseau qu'alimentent ses générateurs. Le poste porte le nom du
  fournisseur. La ville peut être un fournisseur comme les autres.
- **Le fournisseur fixe son prix** : un prix par FE consommé et, s'il le veut, un abonnement fixe par
  jour. Il le change depuis le panneau du poste. Le nouveau prix s'applique à la facture suivante, et
  les clients en sont prévenus.
- **Branchement.** Le courant suit ce chemin :

  ```
  générateurs → poste du fournisseur → câbles du fournisseur → compteur → disjoncteur → maison du client
  ```

  - Le **poste** est en tête du réseau : tout le courant vendu passe par lui, et il compte ce qui sort.
  - Le **compteur** (feature 8) est posé en ligne à l'entrée de chaque client. Il compte les FE qui
    entrent dans cette maison, et c'est cette mesure qui fait la facture.
  - Le **disjoncteur d'abonné** (feature 8) suit le compteur et coupe la maison.
  - **Les câbles sont ceux de Create: Crafts & Additions** : des connecteurs posés sur les blocs, reliés
    par des fils tirés d'une bobine, qui pendent entre deux connecteurs comme de vraies lignes
    électriques. Le mod n'ajoute pas de câble à lui. Le poste, le compteur et le disjoncteur acceptent
    et rendent des FE par leurs faces, pour qu'on y pose un connecteur. Demandé par Killian le
    2026-11-08.
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
- **La facture est automatique et quotidienne.** Chaque jour, à une heure fixe du calendrier réel
  (minuit au départ, réglable), chaque compteur se relève seul et émet une facture (feature 72) :
  consommation du jour fois prix, plus l'abonnement. Si le serveur est éteint à cette heure, les
  factures en retard sont émises à son démarrage, une par jour manqué. Elle est **prélevée sur la
  carte bancaire** (feature 82) que le client a liée à son compteur, et l'argent va sur le compte du
  fournisseur.
- Sans carte liée, ou si le prélèvement est refusé (solde insuffisant, carte en opposition), la facture
  reste due. Le client la paie au guichet ou au distributeur. Le fournisseur est prévenu et décide de
  couper ou non.
- Le compteur mesure les FE qui passent vraiment : un fournisseur ne facture que ce qu'il a livré.
- Une **taxe sur l'électricité** (0 % au départ, réglable) part au trésor à chaque facture payée.
- **Sécurité.** Le poste, les compteurs et les disjoncteurs posés par un fournisseur lui appartiennent.
  Seuls ce fournisseur, un **ingénieur**, le **maire** et l'**équipe du serveur** peuvent les casser
  ou interagir avec eux (ouvrir, régler, jumeler, réenclencher). Le client lui-même ne peut pas toucher
  à son compteur ni à son disjoncteur. Le maire peut aussi **supprimer un fournisseur** : ses clients
  sont détachés, et leurs compteurs ne facturent plus.
- Pour changer de fournisseur, le client demande un raccordement à un autre. Le nouveau fournisseur ou
  un ingénieur rebranche ses câbles et rejumelle son compteur au nouveau poste.
- L'argent ne fait que passer d'un compte à l'autre : la feature ne crée rien.

### Gagner sa vie

**78. Butin des mobs.**
- Tuer un mob **hostile** donne une **chance** de faire tomber des pièces. Une table par type de mob, en
  données, fixe la chance et le montant. Au départ :
  - quelques cuivres sur un zombie ou une araignée ;
  - de l'argent sur un mob plus dangereux (sorcière, creeper chargé) ;
  - davantage sur un boss.
- Les **animaux** ne donnent rien : élever ne doit pas devenir une planche à billets.
- **Garde-fous contre les fermes** :
  - rien pour un mob tué sans joueur, ou apparu par un générateur (spawner) ou par un œuf d'apparition ;
  - le gain baisse quand un joueur tue beaucoup de mobs au même endroit en peu de temps.
  Les seuils sont réglables.
- Un **plafond par joueur et par session** borne le total. Au-delà, plus rien ne tombe jusqu'à la session
  suivante.
- Le butin passe par un modificateur de butin global de Forge : les mobs des autres mods sont couverts par
  leur tag ou par la table.

**79. Quêtes journalières et hebdomadaires.**
- Chaque joueur reçoit **3 quêtes journalières** et **5 quêtes hebdomadaires**, générées pour lui par un
  algorithme.
- **Renouvellement** : à heure fixe, sur le calendrier réel (fuseau réglable), tous les jours pour les
  journalières et chaque lundi pour les hebdomadaires. Le serveur ne tournant que pendant les sessions,
  une quête non terminée expire simplement, sans pénalité.
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
- **Récompenses** : des pièces, comptées comme source au tableau de bord, avec un plafond hebdomadaire
  par joueur.

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

**80. PNJ de la ville.**
- C'est un personnage que l'équipe du serveur **fait apparaître** par commande où elle veut, au spawn par
  exemple. Il est **invulnérable**, immobile, et ne disparaît jamais. Son nom et son apparence sont
  réglables (skin de joueur ou modèle du mod).
- **Le change** : il convertit les pièces entre coupures, sans frais par défaut (frais réglables, versés
  au trésor).
- **Les échanges** : l'équipe lui donne une liste d'offres, en données ou par une interface d'édition
  réservée :
  - des objets qu'il **vend** contre des pièces ;
  - des objets qu'il **achète** contre des pièces.
- Les pièces qu'il **encaisse** sortent de la circulation (puits). Celles qu'il **paie** sont une source,
  comptée au tableau de bord.
- **Garde-fous** :
  - chaque offre a un **stock** ou une **limite par joueur et par session** ;
  - l'équipe est prévenue si une offre d'achat est plus chère que l'offre de vente du même objet, ce qui
    créerait de l'argent en boucle ;
  - l'équipe peut **fermer** une offre ou tout le PNJ à tout moment.
- Plusieurs PNJ peuvent exister, chacun avec ses offres : un changeur au port, un acheteur de minerais à
  la mine.
- Un PNJ ne remplace pas un métier : les offres par défaut ne contiennent ni nourriture préparée, ni
  matériel d'ingénieur, ni remèdes.

### Pilotage

**77. Tableau de bord de l'équipe.**
- Par commande, l'équipe du serveur lit :
  - le **total créé par source** : frappe, butin des mobs, quêtes, achats du PNJ ;
  - le **total détruit** (pièces brûlées, tombées dans le vide, disparues avec un objet au sol) ;
  - la **masse en comptes** ;
  - le **solde du trésor** ;
  - les **salaires dus** ;
  - le **volume des ventes** par période.
- La masse en circulation se déduit : total créé, moins total détruit, moins ce que le PNJ a encaissé. Les pièces dans les coffres ne sont pas
  comptées une à une, ce qui serait trop coûteux.
- Les registres de la frappe et du trésor se consultent en entier.

## 6. Branchements avec les métiers

Chaque branchement se fait au lot du métier concerné, une fois l'économie livrée.

| Métier | Branchement |
|---|---|
| Ingénieur | Il fabrique les **postes de fournisseur** (feature 83), pose et rebranche les compteurs, et pose les scellés. Les factures d'électricité sont émises automatiquement au prix de chaque fournisseur et prélevées sur la carte du client. Impayée, une facture justifie un scellé en RP. Le matériel fabriqué à l'établi se vend à l'étal. Il **fabrique et pose les distributeurs** (feature 81), qui consomment son électricité à chaque opération. |
| Agriculteur | L'**étal** vend la production et rachète les récoltes. Le **grenier communal** peut **payer les dépôts** au prix fixé par la ville, sur le trésor : la ville achète ses réserves. |
| Médecin | Le médecin émet une **facture de soins** depuis le dossier médical, avec les soins listés. Elle peut être réglée par le trésor si la ville rend certains soins gratuits (réglable par type de soin). |
| Policier | Une **contravention** devient une facture publique : payée au guichet, elle est marquée payée au casier sans geste du policier. Une **caution** peut être versée au trésor pour sortir de cellule plus tôt (réglable). Les pièces se fouillent et se confisquent comme le reste. |
| Communication | Le **téléphone** accepte une carte bancaire (feature 82) pour payer l'abonnement réseau. Un prélèvement refusé est signalé à l'opérateur. Ce branchement suit le lot du téléphone. |
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
| E4 | 78, 80 | On gagne sa vie en chassant, et l'équipe ouvre un comptoir |
| E5 | 79 | Des objectifs renouvelés chaque jour et chaque semaine |
| E6 | 81, 82, 83 | Les comptes s'utilisent loin du guichet, une carte volée ne vide pas un compte, et chacun peut vendre son électricité |

Le lot E4 peut passer avant E2 si la ville a besoin de premiers revenus plus tôt : le butin des mobs
répond en partie à la question « d'où vient le premier argent ». Le lot E6 vient après E3, puisqu'il
utilise les comptes, et a besoin du réseau électrique de l'ingénieur. Les quêtes de métier (feature 79)
arrivent avec chaque métier. Les branchements de la section 6 suivent chacun le lot de leur métier. Celui de l'ingénieur vient avec
le lot E6, puisque les factures d'électricité passent par les fournisseurs et les cartes.

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
  - le total frappé moins le total détruit est juste après une pièce jetée dans la lave ;
  - un mob de générateur ne donne rien, et le plafond de butin tient ;
  - trois journalières différentes sont générées, et une relance en donne une autre ;
  - un bloc posé puis cassé ne fait avancer aucune quête ;
  - une quête de métier n'est pas proposée à un autre rôle ;
  - le PNJ refuse une offre épuisée ;
  - l'alerte se déclenche quand une offre d'achat dépasse l'offre de vente ;
  - un distributeur sans courant refuse toute opération, et chaque opération consomme l'énergie réglée ;
  - un retrait au distributeur ne change pas la masse totale (compte moins, pièces plus) ;
  - un retrait sans le bon code est refusé, et le plafond par jour tient ;
  - une carte en opposition est refusée au distributeur et au prélèvement de l'abonnement ;
  - à minuit, la facture d'un compteur vaut sa consommation du jour fois le prix du fournisseur, plus
    l'abonnement, et le prélèvement passe du compte du client à celui du fournisseur ;
  - un serveur éteint deux jours émet deux factures par compteur à son démarrage ;
  - un changement de prix ne touche que la facture suivante ;
  - un prélèvement refusé laisse la facture due et prévient le fournisseur ;
  - un fournisseur supprimé par le maire ne facture plus personne ;
  - un compteur non jumelé ou non accepté ne laisse pas passer le courant ;
  - un joueur sans droit (client compris) ne peut ni casser ni ouvrir le poste, un compteur ou un
    disjoncteur d'un fournisseur ; le fournisseur, un ingénieur, le maire et l'équipe le peuvent.
- **Scénario d'acceptation** : l'équipe désigne un trésorier ; il frappe ; il verse un salaire ; le policier
  achète du pain à l'étal de l'agriculteur absent ; l'agriculteur récupère ses recettes ; on relance le
  serveur ; on lit le tableau de bord, et chaque pièce a une origine.
- **Scénario d'abus** : tenter de dupliquer des pièces par déconnexion pendant un échange, par casse d'un
  étal, par une recette CraftTweaker, par une ferme à mobs à générateur, et par une boucle « poser,
  casser » pour une quête. Aucune ne doit rapporter.
- **Scénario des quêtes** : se connecter, lire ses 3 journalières et 5 hebdomadaires, en terminer une,
  réclamer la récompense, relancer le serveur, retrouver sa progression ; changer la date du serveur et
  constater le renouvellement.

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
- le tableau de bord ne compte pas les pièces des coffres une à une ;
- seuls les mobs hostiles donnent des pièces, jamais ceux des générateurs ;
- les quêtes se renouvellent sur le calendrier réel, avec une relance par jour, et expirent sans
  pénalité ;
- les quêtes de métier ne sont proposées qu'au rôle concerné ;
- le PNJ ne vend ni nourriture préparée, ni matériel d'ingénieur, ni remèdes par défaut ;
- le distributeur n'a pas de réserve de pièces : il puise dans les comptes, il n'y a donc rien à y
  cambrioler ;
- une carte bancaire demande un code pour retirer et a un plafond par jour ;
- la facture d'électricité tombe chaque jour à minuit, et la taxe sur l'électricité est à 0 % au
  départ.

## 10. Questions ouvertes

Elles décident de l'équilibre général et sont à trancher avant le lot E1 :
- **Qui frappe ?** Un trésorier joueur, l'équipe du serveur seule, ou les deux ?
- **D'où vient le premier argent ?** Le butin des mobs et les quêtes en apportent maintenant. Faut-il
  en plus une dotation de départ par nouveau joueur ?
- **Les récompenses de quêtes** : des pièces seulement, ou aussi des objets (graines rares, matériel) ?
- **Le rubis** se frappe-t-il comme les autres, ou est-il réservé à l'équipe du serveur, par exemple pour
  des récompenses d'événement ?
- **Faut-il les comptes** (feature 76) dès le lot E3, ou une monnaie uniquement physique suffit-elle au
  début ? Les distributeurs et les cartes (81, 82) en dépendent.
- **Le prix de l'électricité est-il libre**, ou la ville fixe-t-elle un plafond par FE contre les abus ?
- **Les connecteurs et les fils d'un fournisseur** sont des blocs de Create: Crafts & Additions, que la
  règle de sécurité de la feature 83 ne couvre pas. Faut-il les protéger aussi (le mod retiendrait
  qui les a posés), ou laisser la ligne vulnérable, avec l'écart du poste pour repérer les pirates ?
- **La carte bancaire paie-t-elle aussi aux étals** (feature 71), ou seulement au distributeur et dans
  le téléphone ?
