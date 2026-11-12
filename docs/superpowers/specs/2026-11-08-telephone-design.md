# Cahier des charges : le téléphone de Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Killian a demandé le 2026-11-08 un système de téléphone complet, dans le style d'un iPhone, avec des
antennes posées par les joueurs, des opérateurs, des cartes SIM, des appels par Simple Voice Chat et des
applications. Une **maquette jouable** a servi à valider les choix : elle simule l'écran, les antennes,
l'électricité et le trajet des appels
([lien privé](https://claude.ai/artifact/L6feFRchmz4WhcXG58ohuq), à partager depuis son menu).
Ses réponses sont regroupées en section 9.

Ce cahier lève, pour le téléphone, le « prévu plus tard » des cahiers de
[l'électricité](archive/2026-11-06-features-lost-island-design.md) et du
[médecin](2026-11-08-metier-medecin-design.md). L'argent passe par le
[cahier de l'économie](2026-11-08-economie-design.md).

## 1. Pourquoi

Une île sans moyen de se joindre à distance garde chaque joueur dans son coin. Le téléphone donne :
- une raison de **construire un réseau** : des antennes qui coûtent de l'électricité, posées et
  entretenues par des joueurs ;
- un **commerce** : être opérateur, vendre des abonnements et des cartes SIM ;
- du **RP** : appeler les secours, un réseau anonyme pour le marché noir, un fil d'actualité de la
  ville, des photos.

**Critère de réussite : deux joueurs éloignés, chez deux opérateurs différents, s'appellent ; le
réseau ne fonctionne que si des joueurs l'ont construit et l'alimentent ; et les gens autour d'un
joueur l'entendent parler au téléphone.**

## 2. Objectifs et limites

### Objectifs
- Une interface **moderne**, animée, dans le style d'iOS, en HTML, CSS et JavaScript.
- Un **réseau construit par les joueurs** : sans antenne alimentée, pas de signal.
- Des **opérateurs** joueurs, maîtres de leurs cartes SIM et de leurs prix.
- Des **appels vocaux réalistes** : la voix part au correspondant, et les gens autour entendent aussi.
- Des **applications** utiles au RP, et un magasin d'applications que l'équipe garnit.

### Hors périmètre
- **L'appli de banque** et les cartes bancaires : retirées par Killian, la monnaie est physique.
- **Internet réel** : aucune page ne charge un site extérieur. Toutes les pages viennent du mod.
- **Une vraie vidéo** : FaceTime affiche le personnage du correspondant, pas un flux d'image.
- **La radio et les haut-parleurs publics** : un autre lot.

### Ce qui serait un échec, même si tout fonctionne
- Un écran Chromium qui fait chuter les images par seconde ou plante sur une partie des joueurs, sans
  solution de repli.
- Un réseau qui marche partout sans que personne n'ait posé d'antenne.
- Une conversation privée entendue par tout le serveur, ou une voix coupée pour les joueurs proches.
- Un réseau anonyme qui sert à harceler sans que l'équipe puisse agir.
- Des photos inappropriées que l'équipe ne peut pas retirer.

## 3. Contraintes techniques

### Interface : MCEF
- Le téléphone s'affiche avec **MCEF** (Minecraft Chromium Embedded Framework), choix de Killian.
- **Version retenue : le fork de Keksuccino** (2.2.0 pour Forge 1.20.1, mars 2026), choisi par
  l'auteur à la demande de Killian. C'est la version la plus récente trouvée. Le mod n'utilise que la
  bibliothèque MCEF, aucun autre mod de cet auteur. La version de CinemaMod (`mcef-forge` 2.1.6) date d'octobre 2024. Relevé sur
  CurseForge et les pages de mods le 2026-11-08. **Au lot T1, vérifier** sa version de Chromium, son
  API et sa licence ; revenir à CinemaMod si le fork pose problème.
- MCEF est un mod **client seulement**. Il télécharge Chromium (environ 100 Mo, chiffre de la page
  CraftBrowser) au premier lancement, depuis `mcef-download.cinemamod.com`. Certains antivirus le
  bloquent.
- **Mac** : le README de MCEF annonce macOS 11 et plus, sur Intel et sur puce Apple. Une autre page
  (CraftBrowser) dit le contraire pour ARM. **À vérifier sur un vrai Mac au premier lot.**
- **Repli** : si MCEF ne démarre pas, le téléphone affiche un écran simplifié en interface Minecraft
  classique, qui garde les appels, les messages et les urgences. Le joueur est prévenu une fois.
- Les pages sont dans les ressources du mod et chargées par un schéma interne, jamais par le réseau.
- Le **serveur fait foi** : SIM, messages, publications, photos, abonnements sont gardés par le
  serveur. La page ne reçoit que ce que le joueur a le droit de voir, par paquets réseau, et renvoie ses
  actions par un pont JavaScript vers Java. Rien de ce que la page envoie n'est cru sans vérification.

### Voix : Simple Voice Chat
- Les appels utilisent l'**API serveur de Simple Voice Chat** (`voicechat-api`), par un greffon déclaré
  par le mod. Simple Voice Chat devient une dépendance du serveur et des clients.
- Le mod relaie les paquets de micro de l'appelant vers son correspondant, sur un canal audio privé.
  En haut-parleur, il joue la voix du correspondant comme un son placé sur le téléphone, et relaie les
  voix des joueurs proches au correspondant. **Les noms exacts des classes et événements sont à
  vérifier dans l'API au premier lot de voix** : seul le principe est validé.
- La voix de proximité normale de Simple Voice Chat n'est jamais coupée par le téléphone.

### Autres
- L'électricité des antennes passe par l'énergie Forge (FE), branchée par n'importe quel câble, comme
  les blocs de l'économie.
- Les échéances (abonnements) suivent le calendrier réel, à minuit, avec rattrapage au démarrage.

## 4. Socle

### Le téléphone
- C'est un **objet** que **n'importe quel joueur** fabrique (choix de Killian), qu'on porte, qu'on peut
  perdre ou se faire voler.
- Il a une **batterie** qui se vide à l'usage, plus vite en FaceTime, et se recharge sur une prise
  alimentée en FE. Vide, il s'éteint.
- Il a un **code de verrouillage** à 4 chiffres, choisi par son propriétaire. Un téléphone volé reste
  verrouillé ; seuls les appels d'urgence passent sans code.
- Ses données (photos, notes, réglages) sont liées au téléphone ; ses contacts et ses conversations,
  à la carte SIM.

### La carte SIM
- C'est un **objet**, émis par un opérateur. Elle porte un **numéro** (format `06 xx xx xx xx`) et
  le nom de son titulaire, ou rien pour une **SIM sans nom**.
- Elle se glisse dans le téléphone depuis l'inventaire. **Le numéro suit la carte**, pas le téléphone.
- Elle se donne, se vend, se perd et se vole comme tout objet, et se fouille (feature 52 du policier).
- Seul le **fondateur** de l'opérateur crée, suspend ou réactive les cartes de son réseau.

### L'opérateur
- Un opérateur est fondé par le joueur qui pose sa **première antenne** et lui donne un nom. Ce joueur
  en est le **fondateur**.
- La ville peut être un opérateur comme les autres.

### Le maire
- Le maire ([son cahier](2026-11-08-metier-maire-design.md)) peut **casser ou modifier** une antenne, et
  **supprimer un opérateur** : toutes ses cartes SIM sont alors désactivées et ses antennes ne relaient
  plus rien. Il publie ses annonces sur Écho par un compte officiel, et gère la ville depuis l'appli
  Mairie.

## 5. Features

La numérotation prolonge celle du cahier de l'économie.

### Réseau

**89. Antenne.**
- C'est un bloc **fabriqué par l'ingénieur** à son établi. **N'importe quel joueur** peut la poser :
  il devient fondateur d'un opérateur, ou ajoute une antenne au sien.
- Elle couvre une **portée** (270 blocs au départ, réglable). Le **signal**, de 0 à 4 barres, baisse
  avec la distance. À une barre, la voix grésille ; à zéro, plus rien ne passe et un appel en cours
  coupe.
- Elle consomme de l'électricité :
  - une **veille** (40 FE/t au départ) ;
  - un supplément **par carte SIM active** (6 FE/t) ;
  - un supplément selon le **trafic** : peu pour un message, plus pour un appel (9 FE/t), beaucoup pour
    un FaceTime (24 FE/t).
  Sans courant, elle tombe en panne et son voyant s'éteint. Toutes les valeurs sont réglables.
- Elle a une **caisse** qui reçoit les abonnements prélevés (feature 92), et que le fondateur vide.
- **Sécurité** : seuls son fondateur, le **maire**, l'**équipe du serveur** et un **ingénieur muni
  de l'outil de maintenance** peuvent l'ouvrir, la régler ou la casser.

**90. Panneau de l'opérateur.**
- Il s'ouvre sur l'antenne et dans l'application **Antenne** du téléphone du fondateur.
- Il montre la consommation en direct, découpée en veille, clients et trafic.
- Il gère les **cartes SIM** : demandes reçues (émettre ou refuser), cartes émises (suspendre,
  réactiver), émission d'une **SIM sans nom**. Une carte émise arrive dans l'inventaire du fondateur,
  qui la remet en main propre.
- Il fixe le **tarif** de l'abonnement, par jour.
- Il montre la recette, l'électricité consommée et l'état du relais vers les autres opérateurs.

**91. Relais entre opérateurs.**
- Un téléphone ne parle **qu'aux antennes de son propre opérateur**.
- Pour joindre un autre opérateur, les antennes se **relaient automatiquement** dès qu'elles sont
  alimentées, sans réglage ni portée entre elles (choix de Killian).
- Le trajet d'un appel : téléphone → antenne de l'appelant → antenne de l'appelé → téléphone. Les deux
  antennes portent le trafic, et chacune consomme pour lui.
- Si l'antenne de l'appelé est en panne ou son opérateur supprimé, l'appel échoue avec un message
  clair : « Injoignable · Antenne de la mairie hors tension ».

**92. Abonnement.**
- Le tarif est **par jour**, fixé par le fondateur. Il est **prélevé en pièces** chaque jour à minuit
  (feature 86 de l'économie) : dans l'inventaire, puis dans le coffre-fort, sinon refusé.
- Les pièces vont dans la caisse de l'antenne principale de l'opérateur.
- Un prélèvement refusé prévient l'abonné et le fondateur, qui décide de **suspendre** la carte ou non.
- Une carte suspendue n'appelle plus et ne reçoit plus rien, sauf les urgences.

### Appels

**93. Appel vocal.**
- On appelle depuis les contacts, les récents ou le clavier. Un appel entrant sonne et s'affiche même
  téléphone verrouillé.
- **Ce que chacun entend** :
  - les joueurs proches entendent toujours celui qui parle au téléphone, par la proximité normale ;
  - le correspondant entend l'appelant ;
  - l'appelant entend le correspondant dans son téléphone, seul.
- **Silence** : le micro n'est plus envoyé au correspondant ; les gens autour entendent toujours.
- **Haut-parleur** : la voix du correspondant sort du téléphone et s'entend à 8 blocs autour ; les voix
  des joueurs à moins de 16 blocs partent aussi au correspondant. Distances réglables.
- Pendant un appel, l'**île dynamique** du haut de l'écran montre le minuteur, et ramène à l'appel.

**94. FaceTime.**
- Un appel vocal avec image : chacun voit le **personnage** de l'autre, rendu par son propre jeu à
  partir du skin, des mouvements de tête et de l'état « parle » envoyés par le serveur. Ce n'est pas
  une vidéo.
- On passe d'un appel vocal à FaceTime et inversement. FaceTime consomme plus de batterie et de
  trafic.

**95. Urgences.**
- Les numéros **17** (police) et **15** (hôpital) passent par **n'importe quelle antenne**, sans
  abonnement, sans SIM et téléphone verrouillé.
- L'appel sonne chez les joueurs **en service** du rôle, avec la position de l'appelant.
- Un appel abusif est enregistré et peut valoir une amende en RP.

### Applications

**96. Messages.**
- Conversations par numéro, avec indicateur de saisie et accusé « non distribué » quand le message ne
  part pas (pas de signal, carte du destinataire suspendue, opérateur supprimé).
- Les conversations sont gardées par le serveur et suivent la carte SIM.

**97. Appareil photo et Photos.**
- L'appareil prend une **image de la vue du joueur**, sans l'interface, en mode photo ou selfie.
- L'image est réduite et compressée, puis envoyée au serveur, avec un **quota** par joueur. Les photos
  se rangent dans le téléphone et se partagent dans Messages et Écho.
- L'équipe du serveur peut **retirer** une photo, et chaque envoi est noté avec son auteur.

**98. Écho.**
- Un fil d'actualité public de l'île, sur le modèle de Twitter : publications courtes, photos,
  mentions, « j'aime ».
- Il faut du réseau pour publier. Chaque publication porte le nom du joueur.
- L'équipe du serveur peut retirer une publication et suspendre un compte.
- Le compte officiel **« Mairie »**, marqué comme vérifié, publie les annonces du maire (cahier du
  maire, feature 108).

**99. Ombre.**
Direction revue par Killian le 2026-11-08.
- Le « dark chat » de l'île, **anonyme** et **caché**.
- **Pseudo** : à la première ouverture, le joueur choisit un pseudo. Il signe tous ses messages et
  reste lié au **téléphone** : un téléphone volé et déverrouillé livre le pseudo de son propriétaire.
  Deux joueurs ne peuvent pas avoir le même pseudo.
- **Canaux** : n'importe qui peut **créer un canal** en lui donnant un nom.
- **Aucune liste des canaux** n'existe. Pour **rejoindre** un canal, il faut connaître son nom
  exact et le taper. Le nom se transmet de bouche à oreille, en RP.
- L'écran d'accueil d'Ombre ne montre que les canaux que le joueur a déjà rejoints.
- Les messages **disparaissent après 24 heures** réelles.
- **Personne ne lève l'anonymat en jeu**, pas même la police : pour démasquer quelqu'un, elle doit le
  **piéger** en RP (rejoindre un canal, fixer un rendez-vous, saisir un téléphone). Choix de Killian.
- L'équipe du serveur voit l'auteur réel, uniquement pour la modération hors RP.

**100. App Store.**
- Il installe des applications supplémentaires. L'**équipe du serveur choisit** celles qui sont
  publiées et leur prix, payé en pièces depuis l'inventaire.
- Au départ : **Annonces** (petites annonces), **Carte** (couverture des antennes connues),
  **Notes** (carnet).
- Un téléchargement demande du réseau.

**101. Réglages.**
- Carte SIM (choisir parmi celles de l'inventaire, retirer), mode avion, signal, opérateur, fondateur,
  abonnement, code de verrouillage, batterie.

## 6. Branchements

| Avec | Branchement |
|---|---|
| Ingénieur | Il fabrique les antennes et les entretient avec l'outil de maintenance. Les téléphones, eux, se fabriquent par n'importe qui. |
| Électricité | Chaque antenne est une machine qui consomme des FE, et dont la panne coupe un réseau. |
| Économie | L'abonnement est prélevé en pièces (feature 86). Les apps payantes se paient en pièces. |
| Maire | Il casse ou modifie une antenne, et supprime un opérateur. |
| Policier | Les urgences (17), la fouille d'un téléphone ou d'une carte SIM. Il ne peut pas lever l'anonymat d'Ombre : il doit piéger ses utilisateurs en RP. |
| Médecin | Les urgences (15). |

## 7. Découpage conseillé

| Lot | Contenu | Ce qu'il prouve |
|---|---|---|
| T1 | MCEF intégré, objet téléphone, écran verrouillé et code, accueil, Réglages, batterie, écran de repli | Une interface HTML fluide s'ouvre en jeu, et un repli existe |
| T2 | 89, 90, 91, carte SIM | Un réseau construit par les joueurs, qui coûte de l'électricité |
| T3 | 93, 95, 96 | On s'appelle et on s'écrit, entre opérateurs, et les secours répondent |
| T4 | 94, 97 | On se voit et on se photographie |
| T5 | 98, 99, 100 | La vie sociale de l'île passe par le téléphone |
| T6 | 92 | Le réseau se paie seul, chaque jour (après le lot E6 de l'économie) |

## 8. Vérification

- **Lancement réel obligatoire à chaque lot**, avec au moins deux clients pour les appels.
- **GameTests** pour les règles :
  - sans antenne alimentée, le signal est nul ; il baisse avec la distance ;
  - la consommation d'une antenne suit veille, cartes actives et trafic ;
  - seuls le fondateur, le maire, l'équipe et un ingénieur équipé peuvent ouvrir ou casser une antenne ;
  - seul le fondateur émet, suspend ou réactive une carte ;
  - une carte suspendue n'appelle plus, sauf les urgences ;
  - un appel entre deux opérateurs passe quand les deux antennes sont alimentées, et échoue sinon ;
  - un opérateur supprimé par le maire désactive toutes ses cartes ;
  - le 17 et le 15 passent sans SIM ;
  - l'abonnement est prélevé à minuit et arrive dans la caisse de l'antenne, et un refus prévient les
    deux parties ;
  - un message d'Ombre disparaît après 24 heures, et l'équipe voit son auteur réel ;
  - un canal d'Ombre ne se rejoint qu'en tapant son nom exact, et aucune liste ne le révèle ;
  - deux joueurs ne peuvent pas prendre le même pseudo ;
  - le quota de photos tient.
- **Scénario des voix**, à trois joueurs : A appelle B ; C, à côté de A, entend A mais pas B ; A active
  le haut-parleur, C entend B et B entend C ; A coupe son micro, B n'entend plus A ni C, C entend
  toujours A.
- **Scénario de panne** : couper le courant de l'antenne de B pendant un appel ; l'appel coupe avec un
  message ; rétablir le courant ; rappeler.
- **Scénario de performance** : téléphone ouvert pendant une minute, en FaceTime ; mesurer les images
  par seconde avant et après ; vérifier la mémoire du client.

## 9. Décisions et choix

### Décisions de Killian (2026-11-08)
- Interface en HTML, CSS et JavaScript par **MCEF**, dans le style d'iOS.
- Les **ingénieurs** fabriquent les antennes ; **tout le monde** peut en poser et devenir opérateur.
- L'opérateur a un **panneau** pour accepter et retirer ses clients, et fixe son prix.
- Une antenne coûte de l'électricité, plus par client et par bande passante.
- Seuls le fondateur, les admins, un ingénieur avec un outil, et le **maire**, peuvent toucher ou
  casser une antenne. Le maire peut aussi **supprimer un opérateur**.
- Seul le **fondateur** crée et gère les cartes SIM.
- Les appels entre opérateurs passent par les antennes, qui se **relaient automatiquement**.
- Appels par **Simple Voice Chat** : les gens autour entendent ; haut-parleur ; silence ; FaceTime.
- Applications : dark chat (Ombre), Twitter (Écho), appareil photo, App Store, carte SIM.
- **Pas d'appli de banque** ; l'abonnement est prélevé **chaque jour** en pièces.
- **N'importe quel joueur** fabrique un téléphone.
- **Ombre** : un pseudo choisi, des canaux nommés qu'on rejoint en tapant leur nom, et aucune levée
  d'anonymat par la police, qui doit piéger les utilisateurs.
- Le choix du fork de MCEF est laissé à l'auteur.

### Choix faits par l'auteur du document
Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- noms des applications (Écho, Ombre) et des opérateurs dans la maquette ;
- portée de 270 blocs et consommations de 40, 6, 9 et 24 FE/t ;
- urgences sans SIM ni abonnement ;
- batterie rechargée sur une prise FE ;
- code de verrouillage, numéro qui suit la SIM ;
- FaceTime par rendu du personnage, pas par vidéo ;
- messages d'Ombre effacés après 24 heures, auteur visible par l'équipe seulement ;
- le pseudo d'Ombre est lié au téléphone, pas à la carte SIM ;
- le fork de Keksuccino pour MCEF ;
- écran de repli en interface classique si MCEF échoue.

## 10. Questions ouvertes

- **Les Mac à puce Apple** : à tester avant le lot T1. Si MCEF n'y marche pas, l'**écran de repli**
  suffit (choix de Killian du 2026-11-11) : mêmes fonctions, en interface Minecraft classique.
