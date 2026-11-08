# Cahier des charges : le métier d'agriculteur sur Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Ce document complète le [cahier des charges de l'électricité](2026-11-06-features-lost-island-design.md),
dont il reprend le socle (rôles, machine électrique, péremption, détection côtière). Les six features
ont été choisies par Killian le 2026-11-08 dans une liste de propositions.

## 1. Pourquoi

Le rôle Agriculteur existe depuis le lot 0, mais il ne sert qu'à l'affichage : n'importe qui cultive,
élève et conserve comme un agriculteur. Ce mod lui donne un savoir-faire propre, des outils et une
place dans les échanges de la ville.

**Critère de réussite : la ville doit compter sur l'agriculteur pour manger bien et longtemps, sans
qu'un citoyen soit privé de son potager.**

## 2. Objectifs et limites

### Objectifs
- Faire de la récolte, de l'élevage et de la conservation un métier, pas une suite de clics.
- Donner des produits qu'on échange : fromage affiné, vin, conserves, viande fumée.
- Créer des liens avec les autres métiers : la serre se fabrique et se répare chez l'ingénieur, le
  grenier se partage avec toute la ville.
- S'appuyer sur Farmer's Delight et sur le vanilla sans refaire ce qu'ils font.

### Hors périmètre
- **Le soin des animaux** (vétérinaire) : il chevaucherait le médecin.
- **Les saisons** : non prévues. Si le pack ajoute Serene Seasons, la serre devra s'y brancher plutôt
  que les recréer.
- **De nouvelles cultures** : aucune n'est ajoutée ici.
- **La monnaie** : traitée par le [cahier de l'économie](2026-11-08-economie-design.md), qui prévoit l'étal de marché et le rachat
  des dépôts du grenier.
- **L'automatisation par Create** (moissonneuse, déployeur) : elle reste possible, mais aucune machine
  dédiée n'est ajoutée.

### Ce qui serait un échec, même si tout fonctionne
- Un animal qui meurt, une récolte qui pourrit sur pied ou un stock qui disparaît pendant que son
  propriétaire est absent.
- Un citoyen empêché de faire pousser son blé ou de nourrir ses poules.
- Une mécanique d'entretien quotidien qui devient une corvée (remplir des mangeoires toutes les dix
  minutes, par exemple).
- Une serre qui ressemble à une ferme verticale industrielle plutôt qu'à un abri de verre bricolé.

### Règle de conception
Reprise de l'ingénieur : quel besoin de survie ou de communauté la feature sert-elle ? Des survivants
pourraient-ils la fabriquer avec ce qu'ils trouvent sur l'île ?

## 3. Contraintes techniques

### Environnement
Celui du cahier de l'électricité, section 3, sans ajout de mod.

Ce que Farmer's Delight 1.20.1-1.3.4 fournit déjà, relevé dans la liste des fichiers de son jar le
2026-11-08 (noms de modèles et de recettes, pas de lecture du code) :
- couteaux, planche à découper, `straw` (paille), `organic_compost`, `rich_soil` ;
- `milk_bottle`, `apple_cider`, `smoked_ham` (obtenu par fumage vanilla) ;
- des plats posés en bloc puis servis en portions (`roast_chicken_block`, `shepherds_pie_block`) ;
- des caisses de rangement (`cabbage_crate`, `rice_bag`).

Il ne fournit ni faucille, ni fromage, ni vinaigre, ni fermentation.

### Intégration
- Recettes, durées, listes d'objets et d'animaux portées par des données (recettes JSON, tags, config),
  comme pour l'ingénieur.
- Les outils et blocs **non électriques** de ce document se fabriquent sur une table de craft normale.
  Le lot 1 retire de la table toutes les recettes de ce mod : ces objets doivent donc rejoindre la liste
  `tableAllowedItems` de la config de l'établi.
- La serre, machine électrique, passe par l'établi d'ingénieur comme toutes les machines.
- Les produits conservés rejoignent les tags du lot 3 (`perishable`, `slow_perishable`,
  `non_perishable`), sans système de péremption parallèle.
- Les durées se comptent sur le temps du monde, comme la péremption : elles avancent tant que le serveur
  tourne, zones déchargées comprises, et s'arrêtent quand il est éteint. Seule exception : l'état des
  animaux (feature 28) se fige avec eux en zone déchargée.
- Textes en français, fichier anglais de repli.

## 4. Socle de l'agriculteur

### Actions réservées
Le lot 0 a écrit `RoleGate` pour l'ingénieur. Il faut le généraliser à n'importe quel rôle, avec le
même message clair et le même contournement pour l'équipe du serveur.

| Réservé à l'Agriculteur | Ouvert à tous |
|---|---|
| Utiliser la faux | Utiliser la faucille, semer, récolter à la main |
| Lancer une fermentation ou une mise en affinage | Retirer un produit fini, l'acheter, le manger |
| Lire l'état détaillé d'un animal | Remplir une mangeoire, nourrir à la main |
| Régler les droits d'un grenier | Déposer au grenier, retirer si on y a droit |
| | Utiliser le fumoir, planter sous une serre |

Un citoyen garde donc tout ce qu'il faut pour se nourrir. L'agriculteur fait mieux, plus vite et plus
longtemps, et c'est ce qu'il vend.

### Bonus de métier
Entre les mains d'un agriculteur, la faucille et la faux ont une chance de donner une graine ou un
produit de plus (valeur à équilibrer). C'est le seul bonus chiffré du métier : le reste passe par les
actions réservées.

## 5. Features

La numérotation prolonge celle du cahier de l'électricité. Toutes les valeurs chiffrées sont des
valeurs de départ, à équilibrer.

### Récolte

**27. Faucille et faux.**
- **La faucille** récolte d'un coup les cultures **mûres** d'une zone de 3 × 3. Les cultures pas encore
  mûres sont ignorées, jamais cassées.
- Elle **replante** chaque case récoltée avec une graine prise dans la récolte, si la culture en donne.
- **La faux** récolte une zone de 5 × 5, plus lentement entre deux coups. Elle est réservée à
  l'agriculteur et dure plus longtemps.
- Le butin vient de la loot table normale de chaque bloc, pour rester compatible avec Farmer's Delight
  et les autres mods. Les enchantements de fortune s'appliquent.
- Cultures prises en charge : celles dont la maturité se lit de façon standard (`CropBlock` et ses
  sous-classes), plus un tag `oneforallsurvival:sickle_harvestable`. À vérifier une à une : riz de Farmer's
  Delight, baies, cacao, verrues du Nether.
- Utilisées par un déployeur de Create, les deux outils agissent comme dans la main d'un citoyen. Le
  comportement du rôle avec un faux joueur est à vérifier.

### Élevage

**28. Mangeoires et bien-être animal.**
- Chaque animal d'élevage (liste configurable, poule, vache, mouton, cochon, chèvre et lapin au départ)
  porte un **état** : **rassasié**, **normal** ou **affamé**. La faim monte lentement avec le temps.
- **La mangeoire** contient de la nourriture. Les animaux dans un rayon de 8 blocs y mangent seuls
  quand ils ont faim, en consommant un objet qu'ils accepteraient à la main pour se reproduire. Elle se
  remplit à la main, par trémie ou par entonnoir de Create.
- Nourrir un animal à la main compte aussi comme un repas.
- **Eau** : un animal sans eau à portée (source, chaudron rempli ou **abreuvoir**) ne peut pas dépasser
  l'état normal. L'abreuvoir se remplit au seau ou par tuyau de Create.
- **Espace** : au-delà d'un nombre d'animaux dans un même rayon (12 dans 8 blocs au départ), aucun ne
  dépasse l'état normal. Entasser n'est pas élever.

| État | Effet |
|---|---|
| Rassasié, avec eau et espace | Ponte plus fréquente, laine qui repousse plus vite, une chance de viande en plus à l'abattage |
| Normal | Comportement vanilla |
| Affamé | Plus de reproduction, de ponte, de repousse de laine ni de traite |

- Un animal affamé **ne meurt jamais** et ne disparaît pas de faim. Il cesse seulement de produire.
- La faim **se fige** quand l'animal est en zone déchargée, comme une machine : partir explorer ne
  ruine pas un élevage.
- Tout le monde voit des signes discrets (particules quand un animal mange, posture d'un animal
  affamé, à définir avec les visuels). Seul l'agriculteur lit l'état exact, en visant l'animal.
- Le rythme est réglé pour qu'une mangeoire pleine tienne plusieurs sessions RP.

### Conservation

**29. Fermentation et affinage.**
- **Le tonneau** est un bloc non électrique. On y place les ingrédients, on le ferme, et il travaille
  pendant une durée fixée par la recette (type de recette dédié, en JSON). On ne peut pas l'ouvrir sans
  interrompre le travail ; l'interruption rend les ingrédients.
- Recettes de départ :

| Entrée | Sortie | Péremption |
|---|---|---|
| Baies sucrées, eau | Vin de baies | Lente |
| Cidre de Farmer's Delight | Vinaigre | Aucune |
| Chou de Farmer's Delight, sel | Choucroute | Aucune |
| Légumes, vinaigre, sel | Légumes en saumure | Aucune |
| Lait (seau ou bouteille de Farmer's Delight), sel | Caillé | Normale |

- **Un vin oublié** dans le tonneau longtemps après la fin devient du vinaigre. Rien n'est perdu, le
  produit change.
- **L'affinage** : le caillé passe dans une **presse à fromage**, qui donne une **meule de fromage**.
  Posée, la meule vieillit : **jeune**, **affinée**, **vieille**. Elle se découpe au couteau de Farmer's
  Delight en portions, comme ses plats posés.
- L'affinage avance deux fois plus vite dans un endroit sombre et frais, défini comme une lumière de 7 ou
  moins (seuil réglable). C'est la cave du fromager, sans bloc supplémentaire.
- Le sel vient du dessalinisateur, ce qui lui donne un deuxième débouché. Le sel porte le tag
  `forge:salt`, donc un autre mod peut en fournir.

**30. Fumoir à bois.**
- Bloc non électrique, qui brûle du bois (tag `minecraft:logs` et planches, ou une liste réglable) pour
  fumer viande et poisson **déjà cuits ou crus**, en quatre emplacements.
- Il est **lent** (plusieurs minutes, à régler) et produit des aliments **fumés**, plus nourrissants que
  les aliments séchés et à péremption lente.
- Avec du sel en ingrédient, le fumé-salé ne périme plus.
- **Ce n'est pas le fumoir vanilla** : celui-ci cuit vite un aliment qui reste périssable. Le nôtre
  conserve. Le nom en jeu doit éviter la confusion (« Fumoir à bois » ou « Fumerie »).
- Il complète le séchoir électrique du lot 3 : le séchoir est rapide mais dépend du courant et tombe en
  panne ; le fumoir reste disponible quand le réseau tombe.
- Il dégage une fumée visible de loin, comme un feu de camp.

### Culture sous abri

**31. Serre bricolée.**
- **Le contrôleur de serre** est une machine électrique du socle commun : réserve de FE, quatre états,
  usure et pannes réparées par l'ingénieur.
- Il surveille un **volume clos** dont les parois et le toit sont faits de blocs du tag
  `oneforallsurvival:greenhouse_glass` (verre et vitres au départ ; des vitres récupérées sur l'épave
  pourront s'y ajouter). Volume maximal réglable, environ 16 × 16 × 8 au départ.
- Sur son écran, il affiche si la serre est complète, et sinon **où se trouve l'ouverture**.
- **Alimentée et close**, la serre double la vitesse de croissance des cultures qui s'y trouvent
  (réglable).
- **Alimentée en eau potable** par tuyau de Create, elle garde humides les terres labourées sans source
  d'eau. Cela donne un troisième débouché au dessalinisateur.
- **Sans courant ou en panne**, les cultures repoussent à la vitesse normale. Rien ne meurt, rien ne se
  dessèche d'un coup.
- Un tag `oneforallsurvival:greenhouse_only`, vide au départ, permet de réserver des cultures à la serre
  si le pack en ajoute.
- Performance : la liste des cultures du volume est mise en cache et le nombre de croissances
  supplémentaires par tick est plafonné.

### Partage

**32. Grenier communal.**
- Un bloc de stockage qui n'accepte que les produits agricoles et alimentaires (tag configurable), avec
  une capacité réglable (108 cases au départ).
- **Le registre** note chaque dépôt et chaque retrait : joueur, objet, quantité, jour du monde. Il se
  consulte dans un onglet de l'interface et se recopie en un objet **relevé du grenier**, comme le relevé
  de compteur. Le nombre de lignes conservées est plafonné (500 au départ) ; les plus anciennes partent
  d'abord.
- **Déposer** est ouvert à tous.
- **Retirer** dépend des droits fixés par un agriculteur, par rôle : chaque rôle peut être autorisé ou
  non. L'équipe du serveur garde un contournement.
- Les entrées automatiques (trémie, entonnoir) sont acceptées et notées « automatique ». Les sorties
  automatiques sont refusées, sinon le registre serait contourné.
- Le grenier **ne se casse qu'une fois vide**, pour qu'aucun stock ne parte sans passer par le registre.
- Les aliments y périment à la vitesse normale : un grenier n'est pas une armoire frigorifique.

## 6. Découpage conseillé

Chaque lot est jouable seul, visuels finaux compris. L'ordre par rapport aux lots 4 à 7 de l'ingénieur
reste à fixer par Killian.

| Lot | Contenu | Ce qu'il prouve |
|---|---|---|
| A | Généralisation des actions réservées ; 27, 32 | Le rôle Agriculteur a enfin des actions propres, et la ville partage ses vivres avec une trace |
| B | 28, 30 | L'élevage demande un soin régulier mais jamais punitif ; on conserve sans courant |
| C | 29 | Le savoir-faire produit des biens d'échange qui durent |
| D | 31 | Le courant de l'ingénieur nourrit aussi la ferme |

Le lot D dépend du socle machine (déjà livré) et peut se faire à tout moment après le lot A.

## 7. Vérification

- **Lancement réel obligatoire à chaque lot**, comme pour l'ingénieur.
- **GameTests** pour les règles : la faucille ignore une culture pas mûre et replante ; la faux refusée
  à un citoyen ; un animal affamé ne meurt pas et ne pond plus ; la faim figée en zone déchargée ; un vin
  oublié devient du vinaigre ; la serre fermée double la croissance, ouverte ne fait rien, sans courant
  revient au normal ; un retrait refusé au grenier selon le rôle ; un grenier plein refuse d'être cassé.
- **Scénario d'acceptation du grenier** : deux joueurs de rôles différents déposent et retirent, puis
  on relit le registre ; un rôle non autorisé tente un retrait ; une trémie dépose, puis on tente une
  extraction par le dessous.
- **Scénario d'acceptation de l'élevage** : laisser un enclos sans nourriture pendant une session,
  revenir, et constater que tous les animaux sont vivants, affamés, et repartent après un remplissage.
- **Scénario d'acceptation de la serre** : ouvrir une vitre et lire sa position sur l'écran ; couper le
  courant pendant la croissance et constater qu'aucune culture n'est perdue.
- **Compatibilité** : faucille sur les cultures de Farmer's Delight ; mangeoire remplie par un entonnoir
  de Create ; déployeur tenant une faucille.

## 8. Choix faits par l'auteur du document

Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- les outils et blocs non électriques se fabriquent sur une table normale, par tout le monde ; la valeur
  du métier passe par les actions réservées, pas par la fabrication ;
- la faux est réservée, la faucille ouverte à tous ;
- la fermentation et l'affinage sont réservés à l'agriculteur, mais le fumoir est ouvert à tous, parce
  qu'il répond à un besoin de survie ;
- un seul bonus chiffré pour l'agriculteur, sur la récolte ;
- les animaux ne meurent jamais de faim, et leur faim se fige en zone déchargée ;
- l'eau et l'espace plafonnent le bien-être sans jamais le faire baisser sous l'état normal ;
- la cave d'affinage se reconnaît à la lumière, sans bloc ni structure dédiés ;
- la serre est un volume clos surveillé par un contrôleur, plutôt que des lampes de croissance isolées ;
- la serre s'arrose à l'eau potable, ce qui renforce le rôle du dessalinisateur ;
- le grenier n'accepte que des produits agricoles et alimentaires, refuse les sorties automatiques, ne se
  casse qu'une fois vide, et ne protège pas de la péremption ;
- les droits de retrait se règlent par rôle, pas par joueur ;
- aucune nouvelle culture, aucune saison.

## 9. Questions ouvertes

- Faut-il un **établi d'agriculteur**, sur le modèle de celui de l'ingénieur, plutôt que la table
  normale ?
- Le pack ajoutera-t-il **Serene Seasons** ? La serre prendrait alors tout son sens en hiver.
- Les droits du grenier par rôle suffisent-ils, ou faut-il pouvoir nommer des joueurs ?
