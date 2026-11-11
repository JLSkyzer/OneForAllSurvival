# Cahier des charges : le métier de policier sur Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Ce document complète les cahiers de [l'électricité](2026-11-06-features-lost-island-design.md),
de [l'agriculteur](2026-11-08-metier-agriculteur-design.md) et du [médecin](2026-11-08-metier-medecin-design.md).
Il reprend leur socle : rôles, machine électrique, établi d'ingénieur, état à terre, dossier médical.
Les features ont été choisies par Killian le 2026-11-08 dans une liste de propositions.

## 1. Pourquoi

Aucun mod n'a été retenu pour le policier. Le rôle existe depuis le lot 0, mais ne sert qu'à
l'affichage. Le cahier de l'électricité lui confie déjà une tâche : quand un compteur scellé est cassé,
« la trace visible sur le relevé suivant et le RP (Policier) prennent le relais ». Ce mod lui donne de
quoi le faire : arrêter, enquêter, garder la mémoire des affaires et répondre aux alertes.

**Critère de réussite : un délit laisse des traces qu'un policier sait trouver et suivre jusqu'à une
sanction, sans que le mod désigne lui-même le coupable.**

## 2. Objectifs et limites

### Objectifs
- Un cycle complet : alerte, constat, enquête, interpellation, fouille, sanction, casier.
- Des preuves concrètes qui se lisent, se transportent et se contestent en RP.
- Des liens avec les autres métiers : le taser et les alarmes viennent de l'ingénieur, les constats de
  blessure du médecin.
- Une police de survivants, avec du matériel bricolé, pas une police d'État moderne.

### Hors périmètre
- **Armes à feu.** Aucune arme létale n'est ajoutée.
- **Empreintes digitales, fichier d'empreintes, marquage des biens, carte d'identité, permis, salle des
  scellés, bracelet électronique, insigne** : proposés, non retenus.
- **Communication** (radio de police) : prévue plus tard.
- **Monnaie** : traitée par le [cahier de l'économie](2026-11-08-economie-design.md) ; une amende y devient une facture publique.

### Ce qui serait un échec, même si tout fonctionne
- Un mécanisme qui **désigne le coupable** à la place de l'enquête.
- Un joueur menotté, enfermé ou poursuivi qui ne peut plus jouer pendant toute une session.
- Un policier qui peut agir sur n'importe qui, n'importe quand, sans que cela se voie.
- Des serrures si sûres que le vol devient impossible : le crime fait partie du RP.

### Règle de conception
Reprise des autres métiers : quel besoin de communauté la feature sert-elle ? Des survivants
pourraient-ils la fabriquer ? Pour la police, on ajoute une question : **la feature produit-elle une
preuve ou une contrainte, plutôt qu'un verdict ?**

## 3. Contraintes techniques

### Dépendances internes
Plusieurs features s'appuient sur des parties des autres cahiers. Elles ne peuvent être livrées
qu'après celles-ci :

| Feature | Dépend de |
|---|---|
| Menottes, fouille | État à terre (médecin, 38) |
| Traces de sang | Hémorragie (médecin, 36) |
| Rapport d'agression | État à terre (38), stéthoscope (40), dossier médical (49) |
| Taser, lampe UV, alarmes, enregistreur | Machine électrique du socle, établi d'ingénieur |
| Appel à l'aide | Notification avec repère (médecin, 38), à mettre en commun |

### Intégration
- Aucun mod de police externe : tout est dans ce mod.
- Durées, seuils, motifs d'amende, plafonds et listes : données et config.
- Les dégâts de la matraque et du taser passent par une source de dégâts normale, avec une répartition
  First Aid.
- Les registres (casier, sacs de preuves, scènes de crime, enregistreurs) sont plafonnés en nombre de
  lignes, comme le grenier.
- **Les durées de contrainte** (menottes, cellule) se comptent en **temps connecté** du joueur contraint,
  pas en temps du monde : une peine ne fond pas pendant une déconnexion, et ne s'allonge pas non plus.
- Les traces et les validités (sang, mandat) se comptent sur le temps du monde, comme la péremption.

## 4. Socle du policier

### Actions réservées au policier
Menotter, escorter, fouiller, confisquer, étourdir avec la matraque, tirer au taser, prélever une trace,
sceller un sac de preuves, poser et retirer une bande de scène de crime sans laisser de trace,
consulter et compléter un casier, dresser une contravention, incarcérer, exécuter un mandat, poser et
clore un avis de recherche, lire un enregistreur de passage qui n'est pas le sien. Les autres reçoivent
un message clair. L'équipe du serveur garde un contournement.

La généralisation de `RoleGate` à tous les rôles est commune avec l'agriculteur et le médecin.

### Établi de policier
Demandé par Killian le 2026-11-08 : **exactement le même fonctionnement que l'établi d'ingénieur**
(cahier de l'électricité, section 4). Le code de l'établi est généralisé à un établi par métier, pas
recopié.
- Les recettes de **table de craft**, de **tailleur de pierre** et de **table de forgeron** dont le résultat
  relève de ce métier sont **retirées pour tout le monde**, assembleurs mécaniques de Create compris.
- Seul un joueur au rôle Policier peut utiliser cet établi, avec le contournement de l'équipe du serveur. Les
  autres joueurs achètent ou reçoivent ses fabrications.
- **Mêmes fonctions** à l'écran : catégories par mod, liste défilante, recherche, coût, temps, ce qui
  manque. La disposition reste la même, pour qu'un joueur qui change de rôle s'y retrouve.
- **Habillage propre au métier**, demandé par Killian : couleurs, matières, cadres, boutons, icônes et
  modèle du bloc changent d'un établi à l'autre. Les visuels sont finaux et faits par Claude, dans le
  style bricolage et récupération du serveur. Proposition de départ :
  - **matières** : métal riveté, papier kraft de dossier, liège d'un tableau d'affichage ;
  - **couleurs** : bleu nuit, gris acier, accents jaune de bande de scène de crime ;
  - **détails** : onglets en chemises cartonnées, coût présenté sur un formulaire tamponné, file en
    fiches punaisées ;
  - **bloc** : un bureau de poste de police récupéré, avec une lampe, un tampon et un classeur.
- Même fonctionnement : prélèvement immédiat, file d'attente, annulation qui rend tout, stock sans
  limite, file et stock liés au joueur, crafts qui avancent tant que le serveur tourne, lent à la main
  et rapide sous courant.
- L'établi lui-même se fabrique sur une table de craft normale, avec des matériaux vanilla.
- Restent hors de la règle, comme pour l'ingénieur : les fours et cuissons, et les machines de Create.
- **Recettes concernées** : les objets de ce mod qui portent le tag du policier : menottes et leur clé,
  matraque, écouvillon, sac de preuves, ruban de scène de crime, carnet de contraventions, porte de
  cellule, affiche d'avis de recherche, sifflet, serrure et clé, le contrôleur d'équité des jeux du
  casino (cahier du casino, feature 123), l'éthylotest et le test salivaire (cahier des substances,
  feature 137). Aucun mod externe n'est concerné.
- **Exceptions**, gardées sur la table par `tableAllowedItems` :
  - le **crochet**, outil de cambrioleur, que la police ne vend pas ;
  - la **copie d'une clé** par son propriétaire (clé et pépite de fer).
- Le taser, la lampe UV, le transmetteur d'alarme et l'enregistreur restent chez l'ingénieur.

### État étourdi
C'est un effet du mod, partagé par la matraque et le taser : lenteur forte, ni sprint, ni attaque, ni
usage d'objet, pendant quelques secondes. Un joueur étourdi peut être menotté.

### Alerte avec repère
C'est un mécanisme commun avec le médecin (joueur à terre) : une notification aux joueurs d'un rôle
donné, connectés, avec un nom, une distance, et un **repère visible d'eux seuls** jusqu'à ce que
l'alerte soit close ou expire. Il est écrit une seule fois et servi aux deux métiers.

### Journal des actions de police
Chaque action réservée est notée, avec l'auteur, la cible, l'heure et le lieu. L'équipe du serveur le
consulte par commande. C'est le garde-fou minimal contre l'abus de pouvoir, et il ne fait pas l'objet
d'une feature à part.

## 5. Features

La numérotation prolonge celle du médecin. Toutes les valeurs chiffrées sont des valeurs de départ, à
équilibrer.

### Interpellation

**50. Menottes.**
- Un policier menotte un joueur **à terre**, **étourdi**, ou qui **se rend** (il accepte une demande à
  l'écran).
- Un joueur menotté ne peut ni utiliser d'objet, ni attaquer, ni poser ou casser de bloc, ni ouvrir de
  conteneur, ni courir. Il marche lentement et parle librement.
- On le libère avec la **clé de menottes**. Il peut aussi **se débattre** (touche maintenue), avec une
  chance de se libérer qui grandit avec le temps.
- **Garde-fou** : les menottes s'ouvrent seules après un délai de temps connecté (30 minutes au départ),
  sauf si le joueur est en cellule.
- Se déconnecter menotté ne libère pas : on revient menotté.
- Un joueur à terre menotté reste menotté une fois relevé.

**51. Escorte.**
- Le policier « prend en charge » un joueur menotté par clic droit. Le menotté le suit à distance,
  ramené vers lui au-delà de 3 blocs. S'il est bloqué à plus de 10 blocs, il est replacé près du
  policier.
- Un second clic droit le lâche. L'escorte s'arrête si le policier se déconnecte, meurt ou tombe à
  terre.
- Un seul joueur escorté à la fois par policier.

**52. Fouille.**
- Sur un joueur menotté ou à terre, le policier ouvre son inventaire en **lecture seule** : sac, armure,
  mains.
- **Confisquer** un objet le retire au joueur et le place dans un **sac de preuves** (feature 56) dans
  l'inventaire du policier.
- Toute fouille et toute confiscation s'inscrivent au casier de la personne fouillée et au journal.

**53. Matraque.**
- C'est une arme de mêlée. Entre les mains d'un policier, un coup a une chance de **étourdir** (état
  étourdi), qui grandit sur un joueur déjà touché récemment.
- Ses dégâts sont faibles et répartis par First Aid sur la tête et les bras. **Ils ne peuvent pas mettre
  à terre** : arrivés au minimum, ils n'enlèvent plus rien.
- Entre d'autres mains, c'est un simple bâton.

**54. Taser bricolé.**
- C'est un objet à batterie FE, rechargeable comme le poste à souder du lot 2. Il se fabrique à
  l'**établi d'ingénieur** et ne s'utilise que par un policier.
- Il tire à 8 blocs maximum. Un joueur ou un mob touché est **étourdi** quelques secondes, avec un dégât
  minime au torse.
- Chaque tir consomme une charge, avec un délai entre deux tirs. Sans charge, il ne fait rien.

### Enquête

**55. Traces de sang.**
- Un joueur qui **saigne** (feature 36 du médecin) laisse des **taches de sang invisibles**, notées par
  position avec l'heure. Elles s'effacent après un délai sur le temps du monde (deux sessions au départ).
- La **lampe UV**, objet à batterie FE fabriqué à l'établi d'ingénieur, révèle les taches dans un rayon
  de 12 blocs, pour celui qui la tient seulement.
- Le policier **prélève** une tache avec un écouvillon. Il obtient un **échantillon** qui indique l'heure
  approximative et le **groupe sanguin** (variable d'Hospitals), **jamais un nom**. Faire le lien avec une
  personne passe par l'enquête, par exemple en comparant avec un dossier médical obtenu par réquisition.

**56. Sac de preuves et chaîne de garde.**
- Un sac contient une pile d'objets. Une fois **scellé** par un policier, il porte un **numéro d'affaire**,
  la description de son contenu et une **chaîne de garde** : qui l'a scellé, et chaque joueur qui l'a eu
  dans son inventaire, avec l'heure.
- Sortir l'objet le **descelle** de façon visible et définitive. Un sac descellé par un non-policier le
  montre en rouge dans son infobulle.
- Les sacs se rangent dans n'importe quel conteneur.

**57. Rapport d'agression.**
- Quand un joueur en met un autre **à terre**, le serveur garde une trace cachée : victime, agresseur,
  objet tenu, lieu, heure.
- Le **stéthoscope** du médecin indique la **nature** de la blessure : tranchante, contondante, projectile,
  chute, brûlure, électricité.
- Le policier obtient par **réquisition** au médecin un **constat** : la nature, l'objet utilisé, le lieu
  et l'heure. Il est remis sous forme d'objet et s'inscrit au dossier médical. **Le nom de l'agresseur n'y
  figure jamais.**
- Seule l'équipe du serveur peut lire l'agresseur, par commande, pour arbitrer un litige RP.
- Un joueur **achevé** à terre (médecin, feature 38) laisse une seconde trace, distincte : le constat
  dit « mis à terre » ou « achevé à terre », pour que la police sache s'il y a eu acharnement.

**58. Bande de scène de crime.**
- C'est un ruban qui se pose entre deux blocs, et qu'on peut traverser.
- Chaque joueur qui **le franchit** ou **le casse** est noté, avec l'heure. Un policier lit la liste en
  utilisant le ruban.
- Un policier la retire sans trace. Pour tous les autres, la casser laisse une trace.

### Justice

**59. Casier judiciaire.**
- Chaque joueur a un casier, conservé par le serveur. Il se remplit **automatiquement** : menottage,
  fouille, confiscation, contravention, peine de cellule, mandat exécuté chez lui, avis de recherche.
- Les policiers ajoutent des notes libres.
- **Seuls les policiers** le consultent. Un policier peut remettre un **extrait** (objet) à un joueur ou à
  un autre rôle.
- Seule l'équipe du serveur peut effacer une ligne.

**60. Carnet de contraventions.**
- Le policier vise un joueur proche, choisit un **motif** (liste réglable, plus un motif libre) et écrit un
  **montant**.
- Le contrevenant reçoit un **avis de contravention** (objet) et la ligne s'inscrit au casier comme
  **impayée**.
- Le policier la marque **payée** quand le règlement a eu lieu en RP. Avec le [cahier de l'économie](2026-11-08-economie-design.md), l'amende est prélevée à
  distance dans l'inventaire puis le coffre-fort du contrevenant, et marquée payée automatiquement.
- Avec le [cahier du maire](2026-11-08-metier-maire-design.md) (feature 105), le montant de chaque motif
  vient du **barème** fixé par le maire ; seul le motif libre garde un montant écrit par le policier.

**61. Cellule.**
- La **porte de cellule** est un bloc que seul un policier verrouille et déverrouille. Fermée, elle
  définit la cellule : le volume clos derrière elle, détecté comme la serre de l'agriculteur.
- Un policier **incarcère** un joueur menotté présent dans la cellule, pour une durée choisie sous un
  **plafond** (30 minutes au départ). Les menottes tombent à l'entrée.
- Le détenu ne peut casser aucun bloc des parois de sa cellule. La protection ne vaut que pour lui.
- La peine se compte en temps connecté. À la fin, la porte se déverrouille et le détenu est prévenu.
- Une relance du serveur ne perd rien. L'équipe du serveur peut libérer à tout moment.

**62. Mandat de perquisition.**
- Il est délivré par le **maire** (choix de Killian du 2026-11-11), depuis l'onglet Mandats de son
  bureau ([cahier du maire](2026-11-08-metier-maire-design.md), feature 102), avec un motif inscrit au
  registre public. L'équipe du serveur peut aussi en délivrer.
- Il vise une **zone** (position et rayon) ou les **serrures d'un joueur**, pour une **durée** limitée (une
  session au départ).
- Un policier porteur d'un mandat valide **ouvre les serrures** visées sans crochetage, et fouille les
  conteneurs qui s'y trouvent.
- Le propriétaire est prévenu à sa prochaine connexion. Le mandat et son exécution s'inscrivent au casier
  du visé et au journal.

**63. Avis de recherche.**
- C'est une **affiche** qui se pose comme un tableau. Un policier y écrit un nom, un motif, une
  description et une récompense en texte.
- Toutes les affiches d'une même affaire sont liées : quand le policier **clôt** l'affaire, elles affichent
  « Affaire close » puis peuvent être retirées.
- Un avis s'inscrit au casier de la personne recherchée.

### Alertes et sécurité

**64. Appel à l'aide.**
- Le **sifflet**, fabriqué à l'établi de policier et distribué ou vendu aux habitants, déclenche une **alerte avec repère** pour les policiers connectés :
  nom de l'appelant et distance.
- Un délai entre deux appels évite les abus, et chaque appel s'inscrit au journal.
- Le policier qui répond clôt l'alerte pour les autres.

**65. Alarmes reliées.**
- Le **transmetteur d'alarme** est un bloc électrique qui reçoit un signal de redstone. Il est sans usure,
  comme le compteur, pour qu'une alarme ne tombe pas en panne par hasard.
- Quand il reçoit le signal, il envoie une **alerte avec repère** aux policiers, sous le nom que son
  propriétaire lui a donné (« Boutique de… », « Grenier »).
- Il marche avec n'importe quelle source : plaque de pression, détecteur, alarme d'Advanced
  Electricity, enregistreur de passage.
- **Sans courant, il est muet**. Couper le courant d'un bâtiment devient donc un geste de cambrioleur, et
  la coupure apparaît au relevé du compteur.

**66. Enregistreur de passage.**
- C'est une machine électrique du socle (FE, usure, pannes) qui surveille une zone réglable (8 × 4 × 8 au
  maximum au départ).
- Il note chaque joueur qui y entre, avec l'heure. Un joueur dont la tête est couverte par un objet
  listé (masque chirurgical d'Hospitals, citrouille sculptée, cagoule…) est noté « **inconnu masqué** ».
- Une coupure ou une panne laisse un **trou visible** dans le registre (« aucun relevé de… à… ») : c'est
  aussi un indice.
- Le propriétaire et les policiers le lisent. Il émet un signal de redstone au passage, ce qui permet de
  le relier à un transmetteur d'alarme.

**67. Serrures et crochetage.**
- Une **serrure** se pose sur une porte, une trappe, un portillon, un coffre ou un tonneau. Elle va avec
  une **clé**. Le propriétaire fait des **copies** de la clé sur une table de craft (clé et pépite de fer).
- Un bloc verrouillé ne s'ouvre, ne se casse et ne se vide par trémie ou entonnoir qu'avec la clé, ou par
  un policier porteur d'un mandat.
- **Crochetage** : n'importe qui peut tenter de crocheter. Il faut maintenir l'action quelques secondes,
  le crochet fait du **bruit** audible à 16 blocs, et il peut casser.
  - En cas de réussite, le bloc s'ouvre et la serrure est **forcée** : la trace reste visible jusqu'à ce
    que le propriétaire la remplace.
  - Un crochetage réussi peut déclencher un signal de redstone, pour une alarme.
- Les murs autour restent cassables : la serrure protège un accès, pas un bâtiment. Le reste relève du
  RP.

## 6. Découpage conseillé

Chaque lot est jouable seul, visuels finaux compris. L'ordre par rapport aux autres lots reste à fixer
par Killian. Les dépendances de la section 3 imposent que les lots P1 et P4 viennent après les lots
M2 et M3 du médecin.

| Lot | Contenu | Ce qu'il prouve |
|---|---|---|
| P1 | Rôles généralisés, établi de policier, état étourdi, journal, alerte avec repère ; 50, 51, 52, 56, 59 | On peut interpeller, fouiller et saisir, et tout se retrouve au casier |
| P2 | 53, 54, 60, 61 | La police a des moyens non létaux et des sanctions qui ne bloquent pas un joueur |
| P3 | 67, 62, 58, 63 | Le vol devient possible et la loi aussi |
| P4 | 55, 57 | Une agression laisse des preuves sans livrer le coupable |
| P5 | 64, 65, 66 | La ville appelle la police, et le courant de l'ingénieur protège les bâtiments |

## 7. Vérification

- **Lancement réel obligatoire à chaque lot.**
- **GameTests** pour les règles :
  - on ne peut pas menotter un joueur debout qui ne s'est pas rendu ;
  - les menottes s'ouvrent après le délai ;
  - la matraque ne met pas à terre ;
  - un taser déchargé ne fait rien ;
  - un sac descellé le reste ;
  - un constat ne contient jamais le nom de l'agresseur ;
  - la peine se fige à la déconnexion ;
  - un détenu ne casse pas sa cellule ;
  - une trémie ne vide pas un coffre verrouillé ;
  - un mandat expiré n'ouvre plus rien ;
  - un transmetteur sans courant reste muet ;
  - un enregistreur note « inconnu masqué ».
- **Scénario d'acceptation de l'interpellation**, avec deux clients : un appel au sifflet, le repère vu
  du seul policier, un tir de taser, les menottes, l'escorte jusqu'à la cellule, une fouille avec
  confiscation, l'incarcération, une déconnexion du détenu puis son retour, et la sortie en fin de peine.
  Chaque étape doit apparaître au casier et au journal.
- **Scénario d'acceptation de l'enquête** : une agression avec saignement, les taches révélées à la lampe
  UV, le prélèvement, la réquisition du constat au médecin, sans aucun nom livré par le mod.
- **Scénario de cambriolage** : une coupure de courant, un crochetage, l'enregistreur qui montre le trou,
  la serrure forcée constatée.

## 8. Décisions et choix

### Décisions de Killian (2026-11-11)
- Le **maire** délivre les mandats de perquisition.
- Un joueur à terre peut être **achevé**, ce qui laisse une trace, mais il ne meurt pas (médecin,
  feature 38).

### Choix faits par l'auteur du document

Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- les contraintes (menottes, cellule) se comptent en temps connecté, avec un délai et un plafond ;
- un journal des actions de police existe pour l'équipe du serveur, même si l'idée « journal » n'a pas été
  retenue comme feature à part ;
- la matraque ne met jamais à terre, elle étourdit ;
- le taser et la lampe UV se fabriquent chez l'ingénieur ;
- un échantillon de sang donne un groupe sanguin, jamais un nom ;
- le constat d'agression ne nomme jamais l'agresseur ; seule l'équipe du serveur le lit ;
- le constat distingue une mise à terre d'un joueur achevé à terre ;
- l'enregistreur nomme les joueurs à visage découvert et note les autres « inconnu masqué » ;
- une serrure empêche de casser le bloc verrouillé, mais pas les murs autour ;
- le transmetteur d'alarme est sans usure, l'enregistreur s'use ;
- un seul joueur escorté à la fois ;
- sifflets, serrures et clés viennent de l'établi de policier, mais le crochet et la copie de clé restent
  sur la table.

## 9. Questions ouvertes

Aucune pour l'instant. Les amendes se paient en pièces depuis le [cahier de l'économie](2026-11-08-economie-design.md).
