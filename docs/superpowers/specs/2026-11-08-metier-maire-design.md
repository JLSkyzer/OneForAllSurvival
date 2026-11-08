# Cahier des charges : le métier de maire sur Lost Island

Date : 2026-11-08. Statut : en attente de validation par Killian.

Les cahiers de l'[économie](2026-11-08-economie-design.md) et du [téléphone](2026-11-08-telephone-design.md)
donnent déjà des pouvoirs au maire. Killian a demandé le 2026-11-08 un cahier pour le métier lui-même :
ses fonctions, son panneau, et la façon dont il gère la ville. Ses réponses sont regroupées en
section 9.

## 1. Pourquoi

Les survivants fondent une ville. Il faut quelqu'un pour en tenir la caisse, fixer les règles communes
et arbitrer quand un fournisseur ou un opérateur abuse. Sans maire, ces gestes reviennent à l'équipe du
serveur, hors RP. Le maire les ramène dans le jeu, sous le regard des habitants.

**Critère de réussite : le maire gère la ville depuis son bureau ou son téléphone ; chacune de ses
décisions est publique ; et la ville continue de tourner quand il est absent.**

## 2. Objectifs et limites

### Objectifs
- Un **rôle unique**, codé en dur, élu en RP et nommé par l'équipe du serveur.
- Un **panneau clair** pour tout ce qu'il gère : trésor, taxes, réseaux, annonces.
- Une **transparence totale** : chaque décision s'inscrit à un registre que tout le monde lit.
- Des pouvoirs **bornés** par l'équipe du serveur : le maire règle, il ne casse pas l'économie.

### Hors périmètre
- **Les élections en jeu** : elles se font en RP. L'équipe donne ensuite le grade par commande.
- **Les adjoints** : le maire décide seul (choix de Killian).
- **Les nominations** de policiers et de médecins : elles restent à l'équipe du serveur.
- **Les terrains et parcelles** : pas dans ce cahier.
- **Les salaires** : leurs montants restent réglés par l'équipe (économie, feature 75).

### Ce qui serait un échec, même si tout fonctionne
- Un maire qui vide le trésor ou ruine les joueurs **sans que personne le voie**.
- Une ville bloquée parce que le maire ne se connecte plus.
- Un panneau si chargé que le maire ne trouve pas ce qu'il cherche.
- Un impôt qui pousse tout le monde à ne plus utiliser son coffre-fort, sans que l'équipe le voie venir.

### Règle de conception
Toute décision du maire est **publique, motivée et bornée** : elle s'inscrit au registre des décisions
(feature 103) avec son motif, et ses valeurs restent entre des limites fixées par l'équipe du serveur.

## 3. Contraintes techniques

- Le rôle `maire` s'ajoute aux rôles codés en dur de `RoleBook` (médecin, ingénieur, policier,
  agriculteur, citoyen), avec son libellé et sa couleur.
- **Un seul maire à la fois.** Donner le grade à un joueur le retire à l'ancien maire.
- Le grade se donne et se retire par la commande de rôle existante. **Le mandat n'a pas de durée** :
  il s'arrête quand l'équipe du serveur le décide (choix de Killian).
- Les actions réservées au maire passent par la généralisation de `RoleGate`, commune aux autres métiers.
  L'équipe du serveur garde un contournement pour tout.
- Le **bureau** (feature 102) utilise une interface Minecraft classique : il marche sans le téléphone.
  L'**appli Mairie** (feature 109) passe par MCEF, comme le reste du téléphone.
- Toutes les bornes (taux maximum, plafonds, nombre d'annonces par jour) sont en config.
- Les échéances suivent le calendrier réel, à minuit, avec rattrapage au démarrage, comme l'économie.

## 4. Socle du maire

### Actions réservées au maire
Ouvrir le bureau du maire, dépenser sur le trésor, régler une taxe, un barème ou l'impôt, fixer le
plafond du prix de l'électricité, modifier ou supprimer un fournisseur ou un opérateur, casser ou ouvrir
une antenne, un poste de fournisseur, un compteur ou un disjoncteur, publier une annonce officielle.
Les autres joueurs reçoivent un message clair.

### Salaire
Le maire touche un **salaire** sur le trésor, comme les rôles publics (économie, feature 75). Son
montant est réglé par l'équipe du serveur. Il le retire au guichet.

### Absence du maire
Rien ne dépend de sa présence : les salaires, les prélèvements, les taxes et l'impôt tournent seuls.
Les réglages restent ceux de sa dernière décision. Sans maire nommé, l'équipe du serveur fait ses
gestes.

## 5. Features

La numérotation prolonge celle du cahier du téléphone.

**102. Bureau du maire.**
- C'est un **bloc** posé à la mairie par l'équipe du serveur. Il n'y en a qu'un. Seuls le maire et
  l'équipe l'ouvrent et le cassent.
- Son interface a cinq onglets :

  | Onglet | Contenu |
  |---|---|
  | Trésor | Solde, entrées et sorties récentes, salaires dus, dépenses (feature 104) |
  | Taxes | Taxe sur les ventes, taxe sur l'électricité, impôt sur les coffres-forts, barème des amendes (features 105 et 106) |
  | Réseaux | Fournisseurs d'électricité et opérateurs du téléphone, plafond du prix de l'électricité (feature 107) |
  | Annonces | Rédiger et publier une annonce officielle, voir les précédentes (feature 108) |
  | Registre | Toutes les décisions, les siennes et celles de ses prédécesseurs (feature 103) |

- Chaque décision demande un **motif** court, et une **confirmation** quand elle est lourde (supprimer
  un opérateur, monter un taux).
- C'est le seul endroit où le maire **sort des pièces** du trésor : une dépense est un geste physique,
  à la mairie.

**103. Registre public des décisions.**
- Chaque décision du maire s'y inscrit : date, maire, décision, ancienne et nouvelle valeur, motif. Les
  dépenses y figurent avec leur montant et leur bénéficiaire.
- **Tout le monde le lit** (choix de Killian) : sur le **tableau de la mairie**, le même que celui qui
  affiche le solde du trésor (économie, feature 73), et dans l'appli Mairie en lecture.
- Ni le maire ni personne ne peut effacer une ligne. Seule l'équipe du serveur peut corriger, et sa
  correction est notée.
- Les habitants peuvent contester une décision en RP. Le jeu, lui, ne fait que la rendre visible.

**104. Trésor et dépenses.**
- Le maire voit le solde, les entrées (taxes, amendes, impôt, factures publiques) et les sorties
  (salaires, dépenses).
- Il peut **dépenser** librement pour la ville : sortir des pièces vers son inventaire, ou les verser
  directement à un joueur, par exemple une prime ou un achat. Chaque dépense exige un motif et
  s'inscrit au registre public (choix de Killian). Un détournement reste possible, mais il se voit.
- Il ne peut **pas** payer les salaires à la place du système, ni les modifier : ils tombent seuls chaque
  jour (économie, feature 75).
- Une dépense supérieure à un seuil (réglable) prévient aussi l'équipe du serveur.

**105. Taxes et barèmes.**
- Le maire règle, dans les bornes de l'équipe :
  - la **taxe sur les ventes** des étals (économie, feature 71) ;
  - la **taxe sur l'électricité** (économie, feature 83) ;
  - le **barème des amendes** : le montant de chaque motif du carnet de contraventions (policier,
    feature 60) ;
  - l'**impôt sur les coffres-forts** (feature 106).
- Un changement prend effet **le lendemain à minuit**, et il est annoncé automatiquement à tous
  (feature 108). Personne n'est surpris par une taxe changée dans la journée.

**106. Impôt sur les coffres-forts.**
Demandé par Killian le 2026-11-08.
- Le maire fixe un taux **entre 0 % et 35 %** (choix de Killian). L'équipe peut abaisser ce maximum
  en config.
- Chaque jour à minuit, le taux est appliqué au contenu de chaque **coffre-fort** (économie,
  feature 85), et les pièces vont au trésor.
- L'impôt est arrondi au cuivre inférieur. Un coffre vide ne paie rien.
- Chaque joueur imposé reçoit une notification avec le montant, et le prélèvement s'inscrit au registre
  du coffre-fort.
- Le tableau de bord de l'équipe (économie, feature 77) montre le total prélevé chaque jour et
  l'évolution de la masse dans les coffres-forts. **Voir la question ouverte sur la fréquence**.

**107. Réseaux.**
- Le maire voit la liste des **fournisseurs d'électricité** et des **opérateurs** : fondateur, nombre de
  clients, prix.
- Il fixe le **plafond du prix de l'électricité** par FE (économie, feature 83). Un fournisseur au-dessus
  du nouveau plafond y est ramené le lendemain à minuit, et prévenu.
- Il peut **modifier** les réglages d'un fournisseur ou d'un opérateur, ou le **supprimer** : ses
  compteurs ne facturent plus, ou ses cartes SIM sont désactivées. La suppression demande une
  confirmation, un motif, et prévient le fondateur et ses clients.
- Sur le terrain, il peut ouvrir ou casser une antenne, un poste de fournisseur, un compteur ou un
  disjoncteur (cahiers de l'économie et du téléphone).

**108. Annonces officielles.**
- Le maire rédige une annonce courte (titre et texte). Elle est publiée :
  - sur le **tableau de la mairie** ;
  - en **notification** à tous les joueurs connectés, et à la connexion des autres pendant 24 heures ;
  - sur **Écho**, par un compte officiel « Mairie » marqué comme vérifié (cahier du téléphone,
    feature 98).
- Un nombre maximum d'annonces par jour (3 au départ, réglable) évite le spam.
- Les changements de taxe et les suppressions de fournisseur ou d'opérateur produisent leur annonce
  automatiquement.

**109. Appli Mairie.**
- Elle s'installe seule sur le téléphone du maire, et disparaît quand il perd le grade.
- Elle reprend les onglets du bureau pour les **gestes courants** à distance : lire le trésor, régler
  les taxes, gérer les réseaux, publier une annonce, lire le registre.
- Elle ne permet **pas** de sortir des pièces : une dépense se fait au bureau (feature 102).
- Les autres joueurs peuvent installer une version **en lecture seule** depuis l'App Store : solde du
  trésor, taux en vigueur, registre et annonces.

## 6. Branchements

| Avec | Branchement |
|---|---|
| Économie | Trésor (73), salaires (75), taxes des étals (71) et de l'électricité (83), plafond du prix de l'électricité, impôt sur les coffres-forts (85, 106), tableau de bord (77). |
| Téléphone | Suppression d'un opérateur, antennes, compte officiel sur Écho, appli Mairie. |
| Policier | Barème des amendes du carnet de contraventions (60). |
| Ingénieur | Le maire peut ouvrir et casser les postes, compteurs, disjoncteurs et antennes qu'il pose. |

## 7. Découpage conseillé

| Lot | Contenu | Ce qu'il prouve | Après |
|---|---|---|---|
| M1 | Rôle `maire`, 102 (onglets Trésor et Registre), 103, 104, salaire | Un maire tient la caisse de la ville, sous les yeux de tous | Lot E3 de l'économie |
| M2 | 105, 106 | Le maire règle les taxes et l'impôt, dans des bornes | Lots E2 et E6 |
| M3 | 107 | Le maire arbitre les fournisseurs et les opérateurs | Lot E6, lot T2 du téléphone |
| M4 | 108, 109 | La mairie parle à toute l'île, et le maire gère à distance | Lots T1 et T5 du téléphone |

## 8. Vérification

- **Lancement réel obligatoire à chaque lot.**
- **GameTests** pour les règles :
  - un seul maire à la fois : nommer un joueur retire le grade à l'ancien ;
  - un non-maire ne peut ni ouvrir le bureau ni utiliser l'appli Mairie en écriture ;
  - chaque décision s'inscrit au registre avec son motif, et aucune ligne ne s'efface ;
  - une dépense sort les pièces du trésor, exige un motif, et prévient l'équipe au-delà du seuil ;
  - l'appli Mairie refuse une dépense ;
  - un taux hors des bornes est refusé ;
  - un changement de taxe ne s'applique qu'au lendemain minuit, et produit son annonce ;
  - l'impôt prélève le bon pourcentage de chaque coffre-fort à minuit, arrondi au cuivre inférieur, et
    le verse au trésor ;
  - un fournisseur au-dessus d'un nouveau plafond y est ramené à minuit ;
  - la suppression d'un opérateur désactive ses cartes SIM et prévient ses clients ;
  - la limite d'annonces par jour tient ;
  - sans maire, salaires, prélèvements et impôt continuent de tourner.
- **Scénario d'acceptation** : l'équipe nomme un maire ; il fixe la taxe sur les ventes à 5 % et l'impôt
  à 2 % avec un motif ; le lendemain, une vente à l'étal paie la taxe et les coffres-forts paient
  l'impôt ; il publie une annonce, lue sur le tableau, en notification et sur Écho ; il dépense 50
  pièces pour une prime ; un citoyen lit tout au registre ; l'équipe retire le grade ; la ville continue
  de tourner.

## 9. Décisions et choix

### Décisions de Killian (2026-11-08)
- Le maire est un rôle **codé en dur**, **élu en RP** puis nommé par l'équipe du serveur.
- Son mandat s'arrête **quand l'équipe le décide**.
- Il gère la ville depuis un **bloc** et une **appli** du téléphone.
- Pouvoirs en plus de ceux déjà décidés : **taxes et barèmes**, **annonces officielles**. Pas de
  nominations, pas de terrains.
- **Pas d'adjoints.**
- Il touche un **salaire** sur le trésor.
- Il peut **dépenser** le trésor librement, mais chaque dépense est **publique**.
- Il fixe un **impôt sur les coffres-forts**, entre **0 % et 35 %**.
- **Tout le monde** lit le registre de ses décisions.

### Choix faits par l'auteur du document
Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- un seul bureau, posé par l'équipe ;
- une dépense ne se fait qu'au bureau, pas depuis l'appli ;
- les changements de taxe prennent effet le lendemain à minuit et sont annoncés automatiquement ;
- l'impôt est prélevé chaque jour, arrondi au cuivre inférieur ;
- 3 annonces officielles par jour au plus ;
- une version en lecture seule de l'appli Mairie pour tous ;
- l'équipe est prévenue d'une dépense au-delà d'un seuil.

## 10. Questions ouvertes

- **La fréquence de l'impôt.** Prélevé chaque jour, un taux de 35 % laisse environ 5 % d'un coffre au
  bout d'une semaine (0,65 puissance 7). Faut-il un prélèvement **par semaine** plutôt que par jour,
  ou un maximum plus bas en usage courant ?
- **L'évitement de l'impôt.** Les pièces gardées dans l'inventaire, la bourse ou un étal ne sont pas
  imposées. Avec un taux élevé, les joueurs videront leur coffre-fort, au risque de se faire voler.
  Est-ce un effet voulu ?
- **Un seuil d'exemption** pour les petits coffres-forts (par exemple, rien en dessous de 500 cuivres) ?
