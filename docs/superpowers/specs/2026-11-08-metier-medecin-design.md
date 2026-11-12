# Cahier des charges : le métier de médecin sur Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Ce document complète le [cahier des charges de l'électricité](archive/2026-11-06-features-lost-island-design.md)
et celui de [l'agriculteur](2026-11-08-metier-agriculteur-design.md). Il reprend leur socle : rôles,
machine électrique, établi, péremption, eau potable. Les features ont été choisies par Killian le
2026-11-08 dans une liste de propositions, avec ses précisions.

## 1. Pourquoi

Le serveur a choisi quatre mods pour le médecin : Hospitals, Medical Remedies, First Aid et
CraftTweaker. Ils apportent des objets, des blocs et un système de santé par parties du corps, mais pas
un métier :
- n'importe qui soigne n'importe qui avec n'importe quel objet ;
- les 44 pilules d'Hospitals sont des aliments sans effet médical ;
- la plupart des blocs d'Hospitals (radiographie, bloc opératoire, centrifugeuse) sont décoratifs ou
  presque ;
- la mort arrive d'un coup, sans place pour un secours.

Ce mod relie ces trois mods et en fait un métier.

**Critère de réussite : quand un joueur tombe, la ville appelle le médecin ; les citoyens savent faire
les premiers gestes, mais seul le médecin sait soigner vraiment.**

## 2. Objectifs et limites

### Objectifs
- Séparer les **premiers secours**, ouverts à tous en cas d'urgence, des **soins**, réservés au médecin.
- Donner un usage aux objets et aux blocs d'Hospitals plutôt que d'en ajouter de nouveaux.
- Remplacer la mort brutale par un moment de secours, avec un garde-fou.
- Faire dépendre l'hôpital de l'ingénieur (courant, défibrillateur) et du dessalinisateur (eau potable).

### Hors périmètre
- **Les maladies contagieuses, les allergies et les carences** : proposées, non retenues.
- **L'intoxication alimentaire** : non retenue, malgré la péremption du lot 3.
- **La banque de sang et le don** : non retenus. Le sang reste celui d'Hospitals.
- **La communication** (téléphone, tablette, ordinateur d'Hospitals) : prévue plus tard, retirée du
  pack en attendant (section 7).
- **Le soin des animaux** : il reste celui de Medical Remedies, ouvert à tous.

### Ce qui serait un échec, même si tout fonctionne
- Un joueur bloqué à terre sans médecin connecté, sans aucune issue.
- Un citoyen qui regarde quelqu'un saigner sans rien pouvoir faire.
- Une blessure ou une infection qui tue directement, sans passer par l'état à terre.
- Des soins si longs ou si compliqués que les joueurs évitent l'hôpital.

### Règle de conception
Reprise de l'ingénieur : quel besoin de survie ou de communauté la feature sert-elle ? Des survivants
pourraient-ils la fabriquer avec ce qu'ils trouvent sur l'île ? Pour l'hôpital, on ajoute une question :
**le geste existe-t-il déjà dans Hospitals, Medical Remedies ou First Aid ?** Si oui, on lui donne un
usage, on ne le refait pas.

## 3. Contraintes techniques

### Environnement
Versions et sources du cahier de l'électricité, section 3. Relevés du 2026-11-08 dans les jars du cache
Gradle (lecture des fichiers et des chaînes des classes, **rien n'a été exécuté**) :

| Mod | Ce qui compte ici |
|---|---|
| First Aid 1.20.1-1.1 | API publique : `FirstAidLivingDamageEvent` (son code contient « Event got canceled », il serait donc annulable), `HealingItemApiHelper` et `ItemHealing` (objets de soin avec choix de la partie du corps), `EnumPlayerPart`. Répartitions de dégâts et malus en JSON sous `data/firstaid/firstaid/`. Coupe la régénération naturelle par défaut. |
| Medical Remedies 1.2.1.0 (mod id `marbledsfirstaid`) | Soins par `healAmount`, utilisables sur autrui (accroupi + clic droit). Recettes : table de craft, plus des recettes `create:mixing` de compatibilité (morphine, adrénaline, antidote…). Les recettes d'alambic ne sont pas en JSON : elles sont dans le code. |
| Hospitals 4.0.0 | 237 recettes : 227 à motif, 9 sans forme, 1 cuisson. `PillItem` n'est qu'un aliment. Groupe sanguin stocké dans une variable du joueur (`blood_type`). Instruments (`scalpel`, `forceps`, `retractor`, `tourniquet`, `stethoscope`…), blocs `X-Ray Machine`, `Operating Table`, `Anaesthetic Machine`, `Centrifuge`, `Capsule Filling Machine`, `Bottle Filling Machine`, `Compounding Isolator`, `Hospital Bed`, `Scrub Sink`, `Hand Sanitiser Dispenser`. |

Le lot 0 a constaté qu'Hospitals se charge sur Forge 47.4.28 malgré sa recommandation de 47.4.10
(`docs/verifications/lot-0.md`).

**À vérifier au premier lot :**
- l'annulation de `FirstAidLivingDamageEvent` ;
- ce que First Aid fait d'un soin vanilla (`LivingHealEvent`) donné par Medical Remedies ;
- comment **neutraliser le groupe sanguin** d'Hospitals (variable `blood_type`, contrôle
  `SelfbloodcomparebagrightclickProcedure`) : Killian n'en veut pas (2026-11-11). Toute poche de sang
  convient à tout patient, et aucun écran ne montre de groupe.

### Intégration
- Aucun de ces mods n'est modifié. Leurs objets et leurs blocs reçoivent un comportement par
  événements Forge, par l'API de First Aid et par des données.
- Effets, durées, seuils, listes d'objets et d'instruments : données (JSON, tags, config), pour que
  l'équipe règle l'équilibrage et que CraftTweaker puisse agir.
- Tous les dégâts infligés par ce mod passent par une source de dégâts normale, avec une répartition
  First Aid (feature 35).
- Les durées longues (consolidation, convalescence) se comptent sur le temps du monde, comme la
  péremption.
- Hospitals, Medical Remedies et First Aid deviennent des dépendances obligatoires. CraftTweaker reste
  facultatif.

## 4. Socle du médecin

### Deux niveaux de soin
Demandé par Killian : une partie des soins n'est utilisable que par le médecin sur les joueurs, mais les
citoyens disposent de premiers secours en cas d'urgence.

| | Premiers secours | Soins médicaux |
|---|---|---|
| **Objets (tags, réglables)** | Pansement et plâtre de First Aid ; pansements, sparadraps, tissu et trousse de premiers secours de Medical Remedies ; garrot d'Hospitals ; attelle (feature 37) | Seringues, morphine, adrénaline, stimpack, antidote, élixir de Medical Remedies ; poches de sang, kit de perfusion, instruments chirurgicaux d'Hospitals ; défibrillateur ; plâtre médical |
| **Sur soi** | Tout le monde | Médecin seulement |
| **Sur un autre joueur** | Tout le monde | Médecin seulement |
| **Effet** | Partiel : soigne peu, arrête un saignement léger, immobilise une fracture | Complet |

Les pilules (feature 34) sont à part : tout le monde peut avaler une pilule qu'on lui a donnée, mais
seul le médecin les fabrique. Soigner un animal reste ouvert à tous.

### Actions réservées au médecin
Soigner avec un objet de soins médicaux, piquer, transfuser, plâtrer, opérer, radiographier, utiliser le
défibrillateur, lire un diagnostic complet au stéthoscope, consulter et compléter le dossier médical,
fabriquer à la paillasse et utiliser la chaîne de pharmacie. Les autres reçoivent un message clair.
L'équipe du serveur garde un contournement.

`RoleGate` doit être généralisé à n'importe quel rôle. Le cahier de l'agriculteur prévoit le même
travail : il ne doit être fait qu'une fois.

### Paillasse de médecin
C'est l'établi du médecin.
Demandé par Killian le 2026-11-08 : **exactement le même fonctionnement que l'établi d'ingénieur**
(cahier de l'électricité, section 4). Le code de l'établi est généralisé à un établi par métier, pas
recopié.
- Les recettes de **table de craft**, de **tailleur de pierre** et de **table de forgeron** dont le résultat
  relève de ce métier sont **retirées pour tout le monde**, assembleurs mécaniques de Create compris.
- Seul un joueur au rôle Médecin peut utiliser cet établi, avec le contournement de l'équipe du serveur. Les
  autres joueurs achètent ou reçoivent ses fabrications.
- **Mêmes fonctions** à l'écran : catégories par mod, liste défilante, recherche, coût, temps, ce qui
  manque. La disposition reste la même, pour qu'un joueur qui change de rôle s'y retrouve.
- **Habillage propre au métier**, demandé par Killian : couleurs, matières, cadres, boutons, icônes et
  modèle du bloc changent d'un établi à l'autre. Les visuels sont finaux et faits par Claude, dans le
  style bricolage et récupération du serveur. Proposition de départ :
  - **matières** : carrelage blanc, inox, papier d'ordonnance ;
  - **couleurs** : blanc, vert d'eau et bleu des blocs d'Hospitals, avec la croix rouge en accent ;
  - **détails** : onglets en fiches cartonnées, liste en tiroirs à médicaments, file en flacons
    étiquetés ;
  - **bloc** : une paillasse de laboratoire bricolée, avec une lampe, une balance et des flacons.
- Même fonctionnement : prélèvement immédiat, file d'attente, annulation qui rend tout, stock sans
  limite, file et stock liés au joueur, crafts qui avancent tant que le serveur tourne, lent à la main
  et rapide sous courant.
- L'établi lui-même se fabrique sur une table de craft normale, avec des matériaux vanilla.
- Restent hors de la règle, comme pour l'ingénieur : les fours et cuissons, et les machines de Create.
- **Recettes concernées** : celles d'**Hospitals**, de **Medical Remedies** et de **First Aid**, et les objets
  de ce mod qui portent le tag du médecin (attelle, plâtre médical, clichés, sacs). La liste des mods est
  configurable.
- **Conséquence** : les premiers secours (pansements de First Aid et de Medical Remedies, garrot) se
  fabriquent aussi à la paillasse. Les citoyens les **achètent** au médecin et les utilisent librement :
  c'est le même commerce qu'entre l'ingénieur et la ville.
- **Les blocs sans fonction d'Hospitals** (sols, murs, carrelages, rideaux, mobilier, panneaux,
  environ 150) sont **rendus à la table** pour tout le monde (choix de Killian du 2026-11-11), par la
  liste `tableAllowedItems`. Restent au médecin tout ce qui soigne ou fonctionne : lits d'hôpital,
  machines, instruments, poches et soins. La liste exacte se dresse au lot, depuis les recettes du jar,
  avec cette règle.
- **Alambic** : les recettes de Medical Remedies y sont enregistrées dans le code. Un non-médecin ne peut
  pas lancer une distillation qui produit un objet du tag. La méthode est à choisir au lot : refus à
  l'ouverture, ou résultat bloqué.
- **Mélangeur de Create** : les recettes `create:mixing` de Medical Remedies sont retirées par datapack,
  sinon n'importe qui fabriquerait de la morphine avec un mélangeur.
- Les pilules d'Hospitals ne se fabriquent plus à la paillasse ni sur la table, mais par la chaîne de
  pharmacie (feature 48).

### Coffret électrique médical
Les blocs d'Hospitals ne savent pas recevoir de FE. Plutôt que de les modifier, un bloc du mod les
alimente.
- **Le coffret** est une machine électrique du socle commun : réserve de FE, quatre états, usure et pannes
  réparées par l'ingénieur.
- Il alimente les blocs d'Hospitals d'une liste réglable (tag `oneforallsurvival:powered_medical`) dans
  un rayon de 8 blocs. Sa consommation dépend du nombre de blocs alimentés en marche.
- Un bloc alimenté s'éclaire (à définir avec les visuels). Un bloc non alimenté refuse ses fonctions
  avancées avec un message.
- Une coupure, une panne ou un coffret cassé coupe d'un coup toute la salle : voilà la conséquence d'une
  panne pour l'hôpital.

## 5. Features

La numérotation prolonge celle de l'agriculteur. Toutes les valeurs chiffrées sont des valeurs de
départ, à équilibrer.

### Soins

**33. Pansements ciblés.**
- Tous les objets de soin de Medical Remedies ouvrent, comme le pansement de First Aid, l'écran de choix
  de la partie du corps, et soignent **cette partie**. Ils passent par l'API `HealingItemApiHelper` de
  First Aid.
- Sur un autre joueur (accroupi + clic droit), l'écran s'ouvre chez le soignant et montre l'état du
  patient.
- Chaque objet est classé en premiers secours ou en soins médicaux (section 4) par tag.
- Un premier secours soigne au plus une fraction de la partie (réglable). Il stabilise, il ne guérit pas.

**34. Pilules utiles.**
- Un fichier de données associe chaque pilule d'Hospitals à un effet. Au départ :

| Famille (pilules d'Hospitals) | Effet |
|---|---|
| Antalgiques : paracétamol, ibuprofène | Suspendent les malus de First Aid pendant une courte durée, plus faiblement que la morphine |
| Opioïdes : oxycodone, hydrocodone | Suspendent les malus plus longtemps, avec une nausée passagère |
| Antibiotiques : amoxicilline, azithromycine, céphalexine | Une dose de traitement contre l'infection (feature 43) |
| Corticoïdes : prednisone, hydrocortisone | Accélèrent la consolidation d'une fracture |
| Toutes les autres | Aucun effet : elles restent des accessoires de RP, comme le placebo |

- Avaler la même famille trop souvent ne fait rien de plus et donne une nausée : pas de surdosage
  avantageux.
- L'infobulle d'une pilule indique sa famille, pas sa posologie.

**35. Nos dégâts répartis par First Aid.**
- Chaque source de dégâts de ce mod reçoit une répartition First Aid en JSON :

| Source | Parties touchées |
|---|---|
| Clôture électrique | Bras |
| Flammes d'une machine en panne | Pieds, jambes, torse |
| Foudre détournée vers une machine | Comme la foudre de First Aid |
| Saignement (feature 36) | La partie qui saigne |
| Infection grave (feature 43) | Le torse |

- On ajoute un malus d'électrocution : une courte fatigue des bras après un choc.

### Blessures

**36. Hémorragie.**
- Un coup qui retire plus qu'un seuil (réglable) à une partie, venant d'un type de dégâts listé (arme,
  projectile, attaque de mob, explosion), la fait **saigner**. Une partie qui saigne perd de la vie à
  intervalle régulier, et le joueur laisse des gouttes de sang (particules).
- **Saignement léger** : un premier secours l'arrête.
- **Saignement grave** sur un membre : le **garrot** d'Hospitals l'arrête, au prix d'un malus de ce
  membre tant qu'un médecin n'a pas soigné.
- **Hémorragie interne** au torse : aucun premier secours ne l'arrête, ils la ralentissent seulement. Elle
  exige une opération (feature 41).
- Un saignement ne tue jamais directement : il mène à l'état à terre.

**37. Fractures.**
- Une chute au-dessus d'un seuil, ou un coup très violent sur un membre, provoque une **fracture** de la
  partie touchée. Une chance de fracture **ouverte** existe sur les coups les plus forts.
- Tant que la fracture dure, les malus de First Aid de cette partie restent actifs, **même si la partie
  est soignée**.
- **L'attelle** (premiers secours, fabriquée à la paillasse et utilisable par tous) atténue le malus sans guérir.
- **Le plâtre médical** (médecin) lance la **consolidation** : une durée sur le temps du monde (une
  session RP au départ), après laquelle la fracture disparaît. Les corticoïdes l'accélèrent.
- Une fracture ouverte exige une opération avant le plâtre, et peut s'infecter.
- Certaines fractures sont **cachées** : le joueur ressent le malus, mais seule la radiographie (feature
  42) permet de les plâtrer.

**38. À terre au lieu de mort.**
- Quand la tête ou le torse tomberait à zéro, le joueur **tombe à terre** au lieu de mourir. Les dégâts
  mortels sont annulés et la partie critique reste au minimum.
- **À terre**, il rampe lentement, ne peut ni utiliser d'objet ni attaquer, peut parler. Un écran montre
  le temps restant.
- **Notification** : chaque médecin connecté reçoit un message avec le nom du blessé et la distance.
- **Repère** : un marqueur visible **uniquement par les médecins** indique sa position (colonne de
  lumière ou icône à l'écran) jusqu'à ce qu'il soit relevé.
- **Relever** :
  - un médecin relève un joueur en le soignant, ce qui prend du temps et exige qu'aucun saignement
    grave ne continue ;
  - le défibrillateur (feature 39) relève presque instantanément ;
  - les premiers secours d'un citoyen ne relèvent pas, mais peuvent arrêter un saignement léger.
- **Garde-fou**, demandé par Killian : au bout de **5 minutes** (réglable), le joueur se relève seul,
  **avec très peu de vie** sur chaque partie et l'effet de convalescence (feature 45).
- Se déconnecter à terre ne sert à rien : on revient à terre, avec le temps restant.
- Les mobs perdent leur cible quand un joueur tombe à terre, et **ne lui font plus de dégâts** tant qu'il
  y est.
- **Achever** (choix de Killian du 2026-11-11) : un autre joueur peut frapper un joueur à terre pour
  l'achever. Le coup laisse une **trace** pour la police (policier, feature 57), mais le joueur **ne
  meurt pas et ne réapparaît pas** : il reste à terre, jusqu'à ce qu'un médecin le relève ou que le
  garde-fou des 5 minutes le relève. Achever ne rallonge pas le temps à terre (choix de l'auteur), pour
  qu'on ne puisse pas garder quelqu'un au sol sans fin.
- **Protection après le relevage** (demande de Killian) : un joueur relevé ne prend **aucun dégât des
  mobs** pendant **5 minutes**, ou jusqu'à ce qu'il ait retrouvé **toute sa vie**, au premier des deux
  termes. Les dégâts des joueurs, eux, comptent.
- Certaines morts restent immédiates : vide, `/kill`, et les types de dégâts listés en config.

**39. Défibrillateur.**
- Objet à batterie FE, rechargeable comme le poste à souder du lot 2. Il se fabrique à l'**établi
  d'ingénieur** et ne s'utilise que par un médecin.
- Sur un joueur à terre, il le relève avec un peu plus de vie qu'un relevage sans lui, si aucun
  saignement grave n'est en cours.
- Chaque usage consomme une charge et impose un délai avant le suivant.

**40. Stéthoscope qui sert.**
- Le stéthoscope d'Hospitals, utilisé sur un joueur, affiche un **diagnostic**.
- **Médecin** : vie de chaque partie, saignements, fracture **suspectée** (cachée ou non), infection et
  son stade, convalescence, effets en cours.
- **Autres joueurs** : « respire » ou « ne respire pas », et « saigne » si c'est le cas. Juste de quoi
  appeler le médecin.

**41. Opérations.**
- Elles se font sur la **table d'opération** d'Hospitals, à proximité d'une **machine d'anesthésie**
  alimentée par le coffret.
- Le patient s'allonge, accepte l'anesthésie par un message de confirmation (ou y est placé d'office s'il
  est à terre), puis s'endort : écran noir, il ne bouge plus.
- Le médecin enchaîne les **instruments** d'Hospitals dans l'ordre défini par le type d'opération
  (données). Exemple : marqueur chirurgical, scalpel, écarteur, pinces, ciseaux.
  - Le bon instrument fait avancer l'opération.
  - Le mauvais coûte de la vie au patient, sans pouvoir le tuer directement.
- **Opérations de départ** : hémorragie interne, fracture ouverte, infection grave.
- Les instruments utilisés deviennent **sales** (feature 47).
- Une coupure de courant pendant l'opération la suspend ; elle reprend au retour du courant.

**42. Radiographie.**
- La **machine de radiographie** d'Hospitals, alimentée par le coffret, s'utilise sur un patient placé
  devant le **support de radiographie** (`X-RAY Bucky Stand`).
- Elle produit un **cliché**, un objet du mod qui montre la silhouette du patient, le nom, la date et les
  fractures marquées, cachées comprises.
- Poser un cliché sur un **négatoscope** d'Hospitals **l'affiche en image**, rétroéclairé (choix de
  Killian du 2026-11-11). Le cliché garde aussi son infobulle.
- Le cliché s'ajoute au dossier médical.

### Évolution

**43. Infection des plaies.**
- Une plaie ouverte (saignement, fracture ouverte, opération) peut s'infecter.
- Le risque monte quand elle reste longtemps sans soin, quand le soignant a les **mains sales**, ou
  quand l'opération s'est faite avec des **instruments sales**.
- **Mains propres** : se laver au lavabo chirurgical ou au distributeur de gel d'Hospitals donne
  « mains propres » pendant quelques minutes.
- **Trois stades** :
  - **locale** : la partie ne guérit plus ;
  - **fièvre** : faiblesse et faim plus rapide ;
  - **grave** : perte de vie lente au torse, qui peut mener à terre.
- **Traitement** : une série de doses d'antibiotiques (feature 34), plusieurs en quelques heures de jeu,
  et une opération pour le stade grave.
- Une infection ne tue jamais directement et ne progresse que tant que le joueur est connecté.

**44. Repos à l'hôpital.**
- La régénération naturelle étant coupée par First Aid, le **lit d'hôpital** d'Hospitals fait guérir
  lentement toutes les parties tant que le joueur y est allongé.
- **Bonus** :
  - si un moniteur cardiaque ou un bloc de tête de lit est alimenté à côté ;
  - si une perfusion posée par un médecin est en cours (perche d'Hospitals et kit de perfusion).
- Une coupure de courant retire les bonus, pas le repos de base.
- Le lit d'hôpital raccourcit aussi la convalescence. Le sommeil dans un lit ordinaire garde le réglage
  de First Aid, à baisser côté pack pour que l'hôpital compte.

**45. Convalescence.**
- C'est un effet du mod, donné après un relevage, une opération ou le garde-fou des 5 minutes.
- Il dure quelques minutes : lenteur et fatigue légères, pas de sprint.
- Il ne se cumule pas, et il passe deux fois plus vite au lit d'hôpital.

### L'hôpital alimenté

**46. Centrifugeuse alimentée.**
- La centrifugeuse et la centrifugeuse réfrigérée d'Hospitals refusent de fonctionner sans le coffret.
- Leur traitement prend un temps réglable, au lieu d'être instantané (à confirmer à la lecture de leur
  logique au lot).

**47. Autoclave.**
- C'est un bloc du mod et une machine électrique : elle consomme du FE et de l'**eau potable** (fluide
  par tuyau de Create, ou bouteilles du dessalinisateur).
- Elle stérilise les instruments chirurgicaux d'Hospitals, qui passent de **sale** à **stérile**.
- Un instrument porte son état dans son infobulle. Un instrument neuf est stérile, et il devient sale
  après une opération.
- Opérer avec un instrument sale augmente fortement le risque d'infection.

**48. Chaîne de pharmacie.**
- Les blocs de pharmacie d'Hospitals deviennent une chaîne, alimentée par le coffret et réservée au
  médecin :

| Étape | Bloc d'Hospitals | Rôle |
|---|---|---|
| 1 | Balance analytique | Pèse les ingrédients d'une préparation |
| 2 | Isolateur de préparation | Produit une **substance active** par famille de pilules |
| 3 | Machine à remplir les gélules | Transforme la substance en pilules |
| 4 | Machine à remplir les flacons | Met les pilules en flacon d'Hospitals |

- Les ingrédients sont des objets vanilla, de Farmer's Delight ou de ce mod, définis en données (fleurs,
  champignons, miel, os, sel…).
- C'est le seul moyen de fabriquer les pilules d'Hospitals, et elle produit en quantité.
- Si l'enchaînement de quatre blocs se révèle trop lourd à l'usage, le lot peut le ramener à deux
  étapes : isolateur, puis remplissage.

### Mémoire

**49. Dossier médical.**
- Chaque joueur a un dossier, conservé par le serveur.
- Il s'enrichit **automatiquement** : soins reçus, fractures, opérations, clichés, infections, passages à terre. Chaque ligne porte la date
  et le nom du médecin.
- **Seuls les médecins** le consultent, en utilisant le dossier ou le porte-bloc d'Hospitals sur le
  patient. Ils peuvent y ajouter une note libre.
- Le patient ne le voit pas. Un médecin peut lui en remettre une **copie** (objet), par exemple pour un
  certificat.
- Le nombre de lignes par dossier est plafonné. L'équipe du serveur peut le consulter par commande.

## 6. Découpage conseillé

Chaque lot est jouable seul, visuels finaux compris. L'ordre par rapport aux autres lots reste à fixer
par Killian.

| Lot | Contenu | Ce qu'il prouve |
|---|---|---|
| M1 | Rôles généralisés, deux niveaux de soin, paillasse, nettoyage du pack ; 33, 35, 49 | Le médecin a ses gestes réservés, les citoyens leurs premiers secours, et chaque soin laisse une trace |
| M2 | 38, 39, 40, 45 | La mort devient un secours, avec son garde-fou |
| M3 | 36, 37, 43, 34 | Les blessures durent et demandent un suivi |
| M4 | Coffret électrique ; 42, 41, 47, 46 | L'hôpital dépend du courant de l'ingénieur |
| M5 | 44, 48 | L'hôpital soigne et produit dans la durée |

Le lot M1 doit commencer par vérifier les trois points de la section 3, car tout le reste en dépend.

## 7. Hors du mod, à faire côté pack

- Retirer les échanges du **villageois pharmacien** d'Hospitals : il vend toutes les pilules et
  court-circuiterait le médecin. Le mod peut le faire par config, en vidant les échanges de ce métier.
- Empêcher la **structure de pharmacie** d'Hospitals de se générer, par datapack. La carte est faite à la
  main, mais de nouveaux chunks pourraient la recevoir.
- Retirer les recettes du **téléphone**, de la **tablette**, de l'**ordinateur** et de la **puce** d'Hospitals
  jusqu'au lot de communication.
- Retirer les recettes `create:mixing` de Medical Remedies (section 4).
- Baisser la guérison par le sommeil de First Aid, pour que le lit d'hôpital compte.
- Garder les œufs d'apparition du docteur et de l'infirmière en créatif, pour l'équipe du serveur.

## 8. Vérification

- **Lancement réel obligatoire à chaque lot.**
- **GameTests** pour les règles :
  - un citoyen ne peut pas piquer un autre joueur, mais peut lui mettre un pansement ;
  - un coup mortel met à terre ;
  - le garde-fou relève au bout du délai ;
  - le vide tue quand même ;
  - un saignement léger s'arrête au pansement ;
  - une fracture garde son malus jusqu'à la consolidation ;
  - une infection ne tue pas ;
  - un instrument sale augmente le risque ;
  - une coupure du coffret suspend une opération.
- **Scénario d'acceptation de l'état à terre**, avec deux clients : un joueur tombe, le médecin reçoit la
  notification et voit le repère, que le citoyen ne voit pas ; le médecin relève au défibrillateur. Un
  second essai sans médecin : le joueur se relève seul après 5 minutes.
- **Scénario d'acceptation de l'hôpital** : une chute avec fracture cachée, la radiographie, le plâtre, le
  lit d'hôpital, puis la consolidation, avec chaque étape visible dans le dossier médical.
- **Scénario de panne** : couper le coffret pendant une opération et pendant une radiographie, rétablir le
  courant, rien n'est perdu.
- **Compatibilité** : un objet de soin de Medical Remedies soigne la bonne partie de First Aid ; aucune
  recette d'Hospitals, de Medical Remedies ou de First Aid ne reste sur la table pour un citoyen, sauf
  celles de `tableAllowedItems`.

## 9. Décisions et choix

### Décisions de Killian (2026-11-11)
- Un joueur à terre peut être **achevé**, avec une trace, mais il ne meurt pas : il attend un médecin ou
  le garde-fou des 5 minutes.
- Après un relevage, **aucun dégât des mobs** pendant 5 minutes ou jusqu'à la vie pleine.
- Le **cliché** s'affiche sur le négatoscope.
- Les **blocs de décoration** d'Hospitals se fabriquent à la table par tout le monde.
- **Pas de groupe sanguin**, nulle part : transfusion universelle, dossier et traces de sang sans
  groupe.
- Le **charbon actif** du médecin dégrise et coupe une descente
  ([cahier des substances](2026-11-11-substances-design.md), feature 138).

### Choix faits par l'auteur du document

Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- les objets de soins médicaux sont réservés au médecin **même sur lui-même** ; seules les pilules
  données restent avalables par tous ;
- un premier secours ne soigne qu'une partie de la vie : il stabilise, il ne remplace pas le médecin ;
- les blocs d'Hospitals sont alimentés par un coffret du mod plutôt que modifiés ;
- les mobs ignorent un joueur à terre, mais d'autres dégâts peuvent encore le toucher selon la config ;
- un citoyen ne peut pas relever un joueur à terre ;
- le patient accepte l'anesthésie, sauf s'il est à terre ;
- un mauvais instrument blesse sans tuer ;
- l'infection ne progresse que joueur connecté ;
- les pilules sans famille utile restent sans effet ;
- le dossier médical est invisible pour le patient ;
- le défibrillateur se fabrique chez l'ingénieur ;
- les pilules d'Hospitals ne viennent plus que de la chaîne de pharmacie ;
- les recettes `create:mixing` de Medical Remedies sont retirées, bien que les machines de Create restent
  hors de la règle de l'établi : sinon n'importe qui fabriquerait de la morphine.

## 10. Questions ouvertes

Aucune pour l'instant.
