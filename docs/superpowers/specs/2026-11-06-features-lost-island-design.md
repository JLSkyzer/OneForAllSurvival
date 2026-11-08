# Cahier des charges : les usages de l'électricité sur Lost Island

Date : 2026-11-06. Statut : en attente de validation par Killian.

## 1. Pourquoi

One For All Survival sert en partie Lost Island RP, un serveur de survie roleplay : après
un crash d'avion, des survivants fondent une nouvelle civilisation sur une île inconnue.

Le métier d'ingénieur consiste à crafter, installer et réparer des systèmes mécaniques et
électriques. Les mods du serveur produisent et transportent l'électricité, mais les usages
disponibles sont trop modernes ou abusifs pour l'immersion. Ce mod ajoute des usages crédibles.

**Critère de réussite : une panne de courant doit avoir de vraies conséquences pour la ville, et
l'ingénieur doit être quelqu'un dont on a besoin.**

## 2. Objectifs et limites

### Objectifs
- Donner à l'électricité des usages liés à la survie et à la vie en communauté.
- Donner à l'ingénieur un travail continu : diagnostiquer, réparer, protéger, arbitrer.
- Créer des interactions entre joueurs : facture, pénurie, réserve de nourriture, soirée commune.
- S'intégrer aux mods du serveur sans refaire ce qu'ils font déjà.

### Hors périmètre
- **Le soin** : il appartient au métier de médecin (Hospitals, Medical Remedies, First Aid).
- **La communication entre joueurs** (téléphone, radio, haut-parleurs) : prévue plus tard. *Le
  téléphone a maintenant son [cahier](2026-11-08-telephone-design.md) ; ses antennes consomment de
  l'électricité.*
- **La production et le transport d'électricité** : déjà couverts par les mods d'ingénierie.
- **Les lampes, alarmes, fusibles, poteaux, tenue de plongée** : déjà couverts.
- **L'usure des machines des autres mods** en première version (choix de l'auteur, voir 9).

### Ce qui serait un échec, même si tout fonctionne
- Une machine qui fait penser à une usine moderne plutôt qu'à du bricolage de survivants.
- Un entretien qui devient une corvée répétitive plutôt qu'une intervention de métier.
- Une panne qui détruit le travail d'un joueur absent sans qu'il ait pu réagir.
- Une feature qui impose une réponse au mystère de l'île à la place de l'équipe du serveur.

### Règle de conception
Avant d'ajouter une machine, répondre à deux questions : quel besoin de survie ou de communauté
sert-elle ? Des survivants pourraient-ils la fabriquer avec des débris et du matériel récupéré ?

## 3. Contraintes techniques

### Environnement
Versions relevées le 2026-11-06, à figer dans le pack du serveur.

| Mod | Version 1.20.1 Forge | Source |
|---|---|---|
| Forge | 47.4.28 (environnement de développement) | Maven officiel |
| Create | 6.0.8 | jar lu ; New Age impose `[6.0.8,6.1.0)` |
| Create: Crafts & Additions | 1.3.3 | jar lu ; expose Forge Energy |
| Create: New Age | 1.2.0 | jar lu ; bibliothèque `esl` embarquée |
| Create: Advanced Electricity | 1.0.9 | page CurseForge ; exige Crafts & Additions |
| Farmer's Delight | 1.20.1-1.3.4 | jar lu |
| Hospitals | 4.0.0 | jar lu ; sa page recommande Forge 47.4.10 |
| Medical Remedies | 1.2.1.0 | page CurseForge |
| First Aid | 1.20.1-1.1 | page CurseForge |
| CraftTweaker | 14.0.60 | page CurseForge |

### Électricité
Toutes les machines consomment du **Forge Energy (FE)** par la capability Forge standard.
- Crafts & Additions utilise cette interface (constaté dans son jar).
- Advanced Electricity annonce fonctionner en FE (lu sur sa page).
- New Age passe par la bibliothèque `esl`, qui contient des adaptateurs vers FE (constaté dans le
  jar). Sa page parle encore de Botarium, mais le jar 1.2.0 n'y fait aucune référence.

**Aucune de ces trois connexions n'a été testée en jeu : c'est la première preuve à apporter
(lot 0).**

### Intégration
- Farmer's Delight est une dépendance obligatoire.
- First Aid remplace la santé par des zones du corps : tous les dégâts du mod passent par une
  source de dégâts normale, sans modifier directement les points de vie.
- Recettes, durées et listes d'objets sont portées par des données (recettes JSON, tags, config)
  pour que le serveur règle l'équilibrage sans recompiler et que CraftTweaker puisse agir dessus.
- Le mod est requis côté client et côté serveur.
- Textes en jeu en français, avec un fichier anglais de repli. Rien d'accentué dans `gradle.properties`.

## 4. Socle commun

### Machine électrique
Comportement partagé par toutes les machines du mod :
- une réserve de FE et une consommation lue dans la config ;
- quatre états visibles : **à l'arrêt**, **en marche**, **sans courant**, **en panne** ;
- une machine sans courant se met en pause sans perdre sa progression, sauf règle contraire
  indiquée (couveuse) ;
- l'état se conserve au rechargement du monde ;
- une zone déchargée n'est pas une coupure : la machine se fige, sans usure ni perte.

Les machines s'usent, à deux exceptions près :
- **sans usure** : compteur, disjoncteur d'abonné, récepteur de signaux et mécanisme ancien, pour
  que ni les relevés ni les scénarios de l'équipe du serveur ne tombent en panne par hasard ;
- **usure propre** : le parafoudre, qui s'use à chaque coup encaissé.

### Système de rôles
Demandé par Killian. Il sert tout le serveur, pas seulement l'ingénieur.

- **Rôles codés en dur**, impossibles à supprimer : Médecin, Ingénieur, Policier, Agriculteur et
  Citoyen. Citoyen est le rôle par défaut de ceux qui ne représentent rien de particulier.
- **Rôles dynamiques** : l'équipe du serveur peut en ajouter, modifier et retirer en jeu, par commande.
  Ils persistent avec le monde. Un joueur dont le rôle est retiré repasse Citoyen.
- **Un seul rôle par joueur**, attribué par commande réservée à l'équipe du serveur.
- **Préfixe du rôle** (texte et couleur) visible à trois endroits : dans le **chat**, dans le **pseudo
  au-dessus de la tête** et dans le **menu TAB**. Il se met à jour immédiatement pour tous les joueurs.
- **Actions réservées à l'Ingénieur** : utiliser l'établi d'ingénieur (voir ci-dessous), réparer, relever
  un compteur, poser ou retirer un scellé. Les autres joueurs reçoivent un message clair. L'équipe du
  serveur garde un contournement.
- **Ouvert à tous** : poser n'importe quel bloc reçu ou acheté, utiliser une machine en état de marche
  (celles de Create comprises), cuire au four, casser un bloc. Un commerce de matériel entre l'ingénieur
  et les autres joueurs est donc possible.

Les rôles Médecin, Policier et Agriculteur existent pour l'affichage et pour les besoins futurs : ce
lot ne leur réserve aucune action.

### Établi d'ingénieur
Demandé par Killian. C'est le seul endroit où fabriquer le matériel du métier.

**Recettes concernées**
- Toutes les recettes de **table de craft** dont le résultat vient de Create, Crafts & Additions, New Age,
  Advanced Electricity ou de ce mod sont **retirées de la table de craft, pour tout le monde**, y compris
  pour les assembleurs mécaniques de Create qui les réutilisent. La liste des mods est configurable.
- Le retrait s'applique aussi au tailleur de pierre et à la table de forgeron pour ces mêmes résultats.
- *Ajout du 2026-11-08* : l'agriculteur, le médecin et le policier ont chacun leur établi, sur ce modèle
  (voir leurs cahiers). « Ce mod » désigne alors les objets du mod **qui ne sont attribués à aucun autre
  métier** : chaque objet du mod porte un tag de métier, et un objet sans tag revient à l'ingénieur. Le
  matériel électrique des autres métiers (serre, coffret médical, autoclave, défibrillateur, taser, lampe
  UV, transmetteur d'alarme, enregistreur) reste chez l'ingénieur. Si deux métiers revendiquent le même
  mod, le tag de l'objet tranche, puis l'ordre de la config ; le conflit est signalé en journal.
  Seul le fonctionnement est commun : chaque établi a son propre habillage (couleurs, matières, bloc).
- **Restent hors de cette règle**, sur décision de Killian : les fours et cuissons, et les machines de
  Create (presse, mélangeur, assemblage séquencé).
- L'établi lui-même se fabrique sur une table de craft normale, avec des matériaux vanilla, pour que le
  premier ingénieur puisse démarrer.

**Interface** (soignée et structurée, sur plusieurs écrans si besoin)
- Des **catégories par mod**, une **liste défilante** de tous les objets, une **barre de recherche**.
- Chaque objet affiche son coût, son temps de fabrication et ce qui manque au joueur.
- Le coût reprend la recette de table de craft retirée, ou des ingrédients de ce mod si une recette
  surchargée existe. Les surcharges sont des données, modifiables sans recompiler.
- Le bouton **Crafter** prélève aussitôt les ingrédients dans l'inventaire du joueur et ajoute le craft à
  sa file. Une quantité peut être choisie.
- La **file d'attente** montre le craft en cours et ceux en attente. L'ingénieur peut y ajouter plusieurs
  crafts pour ne pas attendre devant.
- **Annuler** un craft, en cours ou en attente, rend tous les objets prélevés.
- Le bouton **Stock** ouvre une interface à cases, à liste défilante et sans limite de taille, où attendent
  les objets finis. L'ingénieur les récupère à la main. Rien n'est livré d'office dans l'inventaire.

**Temps de fabrication**
- En secondes, calculé d'après la complexité de la recette, le coût et la rareté des ingrédients. Un craft
  peut être long. La formule est réglable et une recette surchargée peut fixer son temps.

**Fonctionnement**
- File et stock sont **liés au joueur** : privés, accessibles depuis n'importe quel établi, et conservés si
  un établi est cassé.
- Les crafts **avancent tant que le serveur tourne**, même si l'ingénieur s'éloigne ou se déconnecte. Ils
  se figent à l'arrêt du serveur et reprennent ensuite.
- **Sans courant, l'établi fonctionne lentement à la main ; alimenté, il va beaucoup plus vite.** La vitesse
  dépend de l'établi où le craft est lancé. Si cet établi est déchargé, son dernier état connu s'applique ;
  s'il est cassé, la vitesse lente s'applique.
- Un craft à la fois par ingénieur. Plusieurs ingénieurs avancent indépendamment.
- Les objets rendus par une recette (seau vide, par exemple) reviennent à la fin du craft dans le stock.

**Vérifier tôt**
- Le retrait des recettes doit aussi fonctionner avec les recettes ajoutées par CraftTweaker, et avec celles
  qui ne sont pas de simples recettes à motif ou sans forme. Ces dernières doivent être recensées et traitées
  une à une pendant le lot 1.
- JEI ne doit plus proposer ces recettes sur la table, et doit afficher celles de l'établi.

### Détection de l'eau salée
L'île est une carte construite à la main : **les biomes ne sont pas fiables et ne sont pas utilisés**.
Une position compte comme côtière si une source d'eau se trouve au niveau de la mer (réglable) dans un
rayon de 16 blocs, sauf si l'équipe du serveur a défini des zones par commande : ces zones priment
alors, ce qui permet d'exclure un lac ou un bassin intérieur. Le résultat est mis en cache pour ne pas
peser sur le serveur.

### Bien-être
Un effet de statut unique, propre au mod, remplace une jauge de moral. Il **ne rend aucun point de
vie** : il accorde un léger bonus d'activité (valeur à équilibrer). Les effets de Farmer's Delight
ne sont pas réutilisés : `Comfort` pourrait soigner, ce qui serait à vérifier et empiéterait sur le
médecin.

## 5. Features

Les numéros reprennent ceux de la liste validée. Les 14, 15, 18, 19 et 20 ont été retirés.
Toutes les valeurs chiffrées sont des valeurs de départ, à équilibrer.

### Le métier d'ingénieur

**1. Usure et pannes.** Chaque machine a un état de 0 à 100 %, qui baisse uniquement quand elle
fonctionne.
- Sous 50 % : rendement réduit. Sous 20 % : fumée et étincelles. À 0 % : panne et arrêt.
- Une panne a un type parmi quatre : fusible, bobinage, roulement, encrassement.
- Une machine bien entretenue tient plusieurs sessions RP avant d'avoir besoin d'une intervention (à
  régler pour éviter la corvée). Le serveur ne tournant que pendant les sessions, l'usure n'avance que
  pendant celles-ci.
- Une panne peut produire des flammes sur la machine. Elles ne se propagent pas, ne détruisent aucun
  bloc et s'éteignent seules : brûler une base en l'absence de ses joueurs serait un échec. Elles se
  désactivent par config.
- Le contenu d'une machine en panne n'est jamais perdu, couveuse comprise : seule une coupure de
  courant fait perdre une couvée.

**2. Trousse de diagnostic.**
- Le **testeur** affiche l'état, le type de panne et la pièce nécessaire.
- Le **poste à souder**, un outil à batterie FE rechargeable, effectue la réparation avec la pièce
  correspondante.
- **Pièces** : fusible, bobine, roulement, kit de nettoyage. Réutiliser par tag ou recette les
  matériaux existants de Create et Crafts & Additions plutôt que d'en dupliquer.
- Une mauvaise pièce est refusée sans être consommée, et réparer remet l'état à 100 %.
- Le testeur reste utilisable par tous pour lire l'état, mais seule une personne au rôle Ingénieur
  peut réparer.
- Crafts & Additions a déjà un objet `multimeter`. Vérifier son usage avant de fixer le rôle du
  testeur, pour le réutiliser comme ingrédient plutôt que créer un doublon.

**3. Corrosion marine et bain galvanique.**
- Sur une position côtière (voir « Détection de l'eau salée »), une machine s'use deux fois plus vite.
- Le **bain galvanique** consomme FE et zinc pour traiter une pièce.
- Une machine réparée avec une pièce traitée devient **protégée** et ignore la corrosion jusqu'à la
  réparation suivante avec une pièce normale.

**4. Récupération sur l'épave.**
- Blocs **débris d'avion** : fuselage, câblage, instruments, batterie, cabine.
- On les démonte avec un outil. Chaque bloc donne un butin défini par une loot table puis disparaît,
  ce qui rend le stock réellement limité.
- Les pièces rares sont refabricables plus tard, par des assemblages séquencés de Create. Aucune
  machine dédiée n'est ajoutée.
- Les débris se placent à la main par l'équipe du serveur, sans génération automatique.

**5. Foudre et parafoudre.**
- La foudre retire de l'état aux machines du mod dans un rayon de 16 blocs, et peut provoquer une
  panne de type fusible.
- Le **parafoudre** protège toutes les machines dans un rayon de 24 blocs, et s'use à chaque coup
  encaissé.
- Le bloc n'attire pas la foudre : le paratonnerre vanilla garde ce rôle.

**6. Station météo.** Elle affiche la tendance à venir : **stable**, **pluie proche**, **orage
proche**, en lisant les compteurs de météo du monde avec une marge d'imprécision. Elle émet un signal
de redstone à l'approche d'un orage, utilisable par les alarmes d'Advanced Electricity. Sans courant,
elle n'affiche rien.

**7. Tableau de délestage.** Il reçoit une arrivée de courant et alimente quatre sorties classées par
priorité. Le courant sert les sorties dans l'ordre et coupe les dernières quand il manque. L'écran
indique quelles sorties sont coupées. Les priorités se règlent dans son interface.

**8. Compteurs et factures.**
- Le **compteur** se branche en ligne, totalise les FE qui passent et porte le nom de l'abonné.
- Seul l'Ingénieur le relève, avec le testeur, et obtient un **relevé** : abonné, consommation, date.
- Le mod ne gère **aucune monnaie** : prix et paiement relèvent du RP. *Remplacé le 2026-11-08 par le
  [cahier de l'économie](2026-11-08-economie-design.md) : n'importe quel joueur peut devenir fournisseur
  d'électricité et fixer son prix sous un plafond du maire. Chaque jour à minuit, le compteur émet seul
  une facture, prélevée en pièces dans l'inventaire puis le coffre-fort du client (features 83 et 86).
  Le relevé au testeur reste possible pour contrôler un compteur.*
- Le **disjoncteur d'abonné** coupe la ligne et se verrouille par un scellé. Seul l'Ingénieur peut
  poser ou retirer un scellé, et un disjoncteur scellé ne se réenclenche pas.
- Casser un compteur ou un disjoncteur scellé reste possible, le mod ne protège pas contre la casse : la
  trace visible sur le relevé suivant et le RP (Policier) prennent le relais. *Remplacé le 2026-11-08
  par la feature 83 du cahier de l'économie : un compteur ou un disjoncteur posé par un fournisseur ne
  se casse et ne s'utilise que par ce fournisseur, un ingénieur, le maire ou l'équipe du serveur.*

### Eau et nourriture

**9. Dessalinisateur.** Il consomme FE et eau de mer (position côtière, ou tuyau Create depuis une
pompe côtière) pour produire de l'**eau potable** et du **sel**. L'eau potable existe en fluide et en
bouteille. Le sel porte le tag `forge:salt` pour rester compatible avec d'autres mods.
Il n'y a **pas de soif sur le serveur** : l'eau potable sert d'ingrédient aux recettes de cantine et
alimente le chauffe-eau. La machine est utile sans être vitale.

**10. Aliments périssables et armoire frigorifique.**
- Une liste configurable d'aliments, avec les viandes, poissons et plats de Farmer's Delight (tag
  `farmersdelight:meals`), porte une date de péremption.
- Trois états : **frais**, **rassis** (moins nourrissant), **avarié** (remplacé par un déchet).
- La date se fonde sur le temps du monde : la péremption avance partout tant que le serveur tourne,
  y compris dans les coffres des zones déchargées, et s'arrête quand il est éteint.
- Le serveur ne tourne que pendant les sessions RP. Les durées sont donc comptées en temps de session :
  au départ, quelques heures de frais et autant de rassis (à équilibrer).
- Une armoire déchargée rattrape son facteur de froid à son rechargement, selon son état au moment du
  déchargement.
- Quand deux piles fusionnent, la date retenue est une moyenne pondérée, pour que les aliments se
  rangent normalement.
- L'infobulle indique l'état, jamais une date exacte.
- **L'armoire frigorifique** (27 cases) divise la vitesse de péremption par 4 tant qu'elle est
  alimentée. Sans courant, les aliments repartent à la vitesse normale.
- Les conserves, les aliments séchés et les aliments salés ne périment pas ou très lentement.

**11. Séchoir électrique.** Il transforme viande, poisson et fruits en rations séchées, moins
nourrissantes mais durables, avec des recettes de type dédié. Le sel est un ingrédient possible pour
un résultat encore plus durable.

**12. Couveuse.** Elle accueille jusqu'à 4 œufs et fait éclore à coup sûr l'animal correspondant, selon
une liste configurable (poule et tortue au départ). Une coupure de plus d'une minute perd la couvée.
C'est la seule machine pour laquelle une coupure détruit le contenu.

**13. Cuisine de cantine.**
- La **marmite de cantine** exécute les recettes `farmersdelight:cooking` et produit plus de
  portions qu'un Cooking Pot, moyennant du FE.
- La **plaque électrique** chauffe les ustensiles de Farmer's Delight tant qu'elle est alimentée. Elle
  rejoint le tag `farmersdelight:heat_sources` et son état allumé doit couper la chaleur sans
  courant (à vérifier dans le code de Farmer's Delight).

### Sécurité

**16. Clôture électrique.** Un **électrificateur** FE alimente une ligne de clôtures contiguës (64 blocs
au plus). Le contact repousse et blesse légèrement les mobs. Pour les joueurs, le choc est non mortel
par défaut. Végétation touchant la clôture ou orage : consommation accrue. Sans courant, c'est une
barrière ordinaire. Crafts & Additions a un objet `barbed_wire`, à réutiliser comme ingrédient.

**17. Phare et projecteurs.**
- **Le phare**, un bloc lanterne au sommet d'une tour libre, projette un faisceau tournant visible de
  loin. C'est un repère et un symbole pour la ville.
- **Le projecteur** s'oriente, éclaire le point visé et fait ressortir les mobs dans son axe.
- Ces deux blocs consomment plus que les lampes des autres mods, pour que leur usage reste un choix.

### Exploration et mystère

**21. Détecteur de métaux.** Objet à batterie FE, qui indique par un son de plus en plus rapide la
proximité d'une **cache enfouie**. Les caches sont des blocs placés par l'équipe du serveur et leur
butin vient d'une loot table.

**22. Lampe frontale rechargeable.** Portée sur la tête, elle éclaire autour du joueur tant que sa batterie
FE tient. Elle occupe l'emplacement du casque, ce qui force à choisir entre protection et lumière.
Technique d'éclairage à valider au lot concerné : elle doit rester lisible et légère sur serveur.

**23. Récepteur de signaux.**
- Un bloc alimenté, réglable sur une fréquence, qui affiche les messages reçus et les archive.
- Seule l'équipe du serveur envoie des messages, par commande, vers une fréquence et une zone.
- Le mod n'embarque **aucun message** ni aucune explication du mystère.

**24. Mécanismes des ruines.**
- Un bloc **mécanisme ancien** placé par l'équipe du serveur, incassable en survie, qui exige un
  niveau de FE pendant une durée, puis émet un signal de redstone pour actionner ce que l'équipe y a
  relié.
- L'équipe du serveur règle le seuil et la durée, et le bloc reste générique.
- **Point de lore :** ce bloc suggère que l'ancienne civilisation maîtrisait l'électricité.

### Moral

**25. Chauffe-eau et douche.** Le chauffe-eau consomme FE et eau potable (par tuyau Create) et alimente une douche.
Après quelques secondes sous l'eau chaude, le joueur reçoit l'effet **Bien-être**. Sans eau chaude, pas
d'effet.

**26. Salle de projection.**
- Un **projecteur de cinéma** affiche une suite d'images sur un écran multi-bloc, à partir de **bobines**.
- Le contenu vient des ressources du mod ou du pack : **aucun téléchargement depuis Internet**.
- Pas de son intégré. Une séance complète donne l'effet **Bien-être** aux spectateurs présents.

## 6. Découpage conseillé

Chaque lot produit quelque chose de jouable et peut être livré seul, **visuels finaux compris**.

| Lot | Contenu | Ce qu'il prouve |
|---|---|---|
| 0 | Socle, machine de test et bloc en ligne branchés sur les trois réseaux ; système de rôles | Le courant arrive dans nos machines et traverse nos blocs en ligne (compteur, délestage) ; les préfixes s'affichent et les actions se réservent |
| 1 | Établi d'ingénieur, retrait des recettes | Seul l'Ingénieur fabrique le matériel, par une interface complète |
| 2 | 1, 2, 4, 3 | Le métier de réparation existe |
| 3 | 9, 10, 11 | L'électricité devient vitale |
| 4 | 7, 8, 5, 6 | La gestion du réseau et ses arbitrages |
| 5 | 12, 13, 16 | Vivres et protection |
| 6 | 25, 26, 17 | La vie de la ville |
| 7 | 21, 22, 23, 24 | L'exploration et les outils de l'équipe du serveur |

## 7. Vérification

- **Lancement réel obligatoire à chaque lot** : compiler ne prouve pas qu'un mod se charge.
- **GameTests** pour les règles : usure, panne, réparation, péremption, priorité du délestage,
  protection du parafoudre.
- **Test de pack en fin de lot 0, puis à chaque lot** : tous les mods de la section 3 chargés sur un
  serveur dédié, avec un client connecté.
- **Scénario d'acceptation de l'établi** : vérifier qu'un non-ingénieur ne peut fabriquer aucun objet des
  mods listés, ni sur une table ni dans l'établi ; lancer plusieurs crafts, se déconnecter, revenir et les
  retrouver dans le stock ; annuler un craft en cours et récupérer tous les ingrédients ; arrêter et
  relancer le serveur en pleine file sans rien perdre.
- **Scénario d'acceptation du lot 3** : couper le courant de l'armoire et constater que les aliments
  repartent à la vitesse normale, sans aucune perte immédiate.
- **Scénario d'acceptation des rôles** : attribuer un rôle à un joueur et constater le préfixe dans le
  chat, au-dessus de sa tête et dans le TAB, vus par un second client ; ajouter un rôle en jeu, relancer
  le serveur et le retrouver ; constater qu'un rôle autre qu'Ingénieur ne peut ni utiliser l'établi, ni
  réparer.

## 8. Décisions de Killian (2026-11-06)

1. **Métiers.** Le mod contient son propre système de rôles (section 4), qui réserve à l'Ingénieur la
   fabrication du matériel de tous les mods d'ingénierie, la réparation, les relevés et les scellés. La
   pose reste libre partout. Casser un compteur ou un disjoncteur scellé reste possible, avec une trace.
   Médecin, Policier et Agriculteur n'ont pas encore d'action réservée.
2. **Soif.** Pas de soif sur le serveur.
3. **Serveur.** Il tourne seulement pendant les sessions RP.
4. **Île.** Carte construite à la main.
5. **Visuels.** Réalisés par Claude et finaux : un style bricolage et récupération, cohérent avec le lore,
   et une tâche de visuels dans chaque lot.
6. **Dépôt.** Public, ce document y est publié.

## 9. Choix faits par l'auteur du document

Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- le tailleur de pierre et la table de forgeron suivent la même règle que la table de craft ;
- le retrait des recettes s'applique à tout le monde, ingénieurs compris : l'établi est leur seul accès ;
- un craft à la fois par ingénieur, et ingrédients pris dans son inventaire uniquement ;
- le matériel se paie à la commande, et les objets finis attendent dans le stock ;
- le chauffe-eau exige de l'eau potable, ce qui donne un débouché au dessalinisateur ;
- l'usure ne touche que les machines de ce mod en première version ;
- pas de jauge de moral, mais un effet Bien-être sans soin ;
- pas de monnaie gérée par le mod (remplacé par le [cahier de l'économie](2026-11-08-economie-design.md)) ;
- armoire frigorifique plutôt qu'une pièce froide multi-bloc ;
- débris et caches placés à la main ;
- Farmer's Delight comme dépendance obligatoire ;
- les flammes de panne sont actives par défaut, sans propagation ni destruction ;
- les trois réseaux restent obligatoires : si l'un échoue au lot 0, Killian décide entre écrire un
  adaptateur et retirer le mod ;
- la liste des machines sans usure (section 4) ;
- une zone déchargée n'est pas une coupure ;
- une version `0.x` tant que le lot 7 n'est pas livré.

## 10. Hors du mod, à faire côté pack

Retirer du serveur les contenus contraires à l'immersion : les PC, CPU et cartes graphiques d'Advanced
Electricity, les réacteurs nucléaires de New Age. L'établi doit permettre d'exclure ces objets par une
liste de configuration, mais leurs recettes hors table de craft relèvent d'un datapack ou de CraftTweaker.
Dresser la liste exacte au moment de constituer le pack.
