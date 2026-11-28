# Refonte du wiki : ce que les captures ont montré

Défauts ou surprises vus en capturant le jeu pour le wiki. Ils ne se corrigent pas dans la refonte (`src/main`
intouché) ; chacun dit comment il a été vu.

## Téléphone (tâche 3, 27/11)

- **Connecteur sur l'antenne : il faut s'accroupir.** Connecteur de Crafts & Additions en main, un clic droit debout
  sur l'antenne ouvre le panneau de l'opérateur et ne pose rien (`AntennaBlock.use` répond à tout objet sauf le
  testeur). Accroupi, le connecteur se pose et prend seul le mode « push » ; sur la source (bloc d'énergie créative),
  il prend seul « pull ». Vu dans l'atelier (`ScenesTelephone`, scène `fil`, constats au journal). Le wiki et
  `export/2026-11-27/TUTO-antenne.md` le disent désormais. À décider : laisser passer le placement d'un connecteur
  tenu (comme le testeur) serait plus naturel pour les joueurs.
- **Alternateur à basse vitesse.** Un alternateur collé contre l'antenne, entraîné par un moteur créatif à
  32 tr/min, la tient « En marche » (réserve +100 FE par seconde pendant 40 s) ; à 16 tr/min, l'antenne n'a rien
  reçu du tout en 40 s (0 FE, « Sans courant »), alors que 22 FE/t étaient attendus d'après la configuration. Cause non
  cherchée (seuil de l'alternateur de Crafts & Additions probable, non vérifié). Scène `alternateur`.
- **Prise murale à travers le mur** : confirmé, un connecteur « aucun » posé sur le bloc qui porte la prise passe en
  « push » dès que la prise le voit, et le téléphone branché charge (16 920 FE relus 3 s après le branchement).

## Électricité et ingénieur (tâche 4, 27/11)

Les fiches de la version 1 ont été relues contre le code (`src/main/java`, `data/`, `fr_fr.json`, classes de
configuration) avant d'être réécrites. Écarts corrigés dans le wiki :

- **Compteur nu : rien ne passe.** Depuis le lot E6, `MeterBlockEntity.isOpen` ne laisse passer le courant que par un
  compteur jumelé à un fournisseur et accepté ; la fiche du lot 4 décrivait un compteur qui « totalise les FE » posé
  sur le câble, et sa note d'équipe parlait encore d'une facturation « prévue ». Vu dans l'atelier (scène `abonne`) :
  compteur posé et inscrit, non jumelé, la maison derrière reste « Sans courant », 0 FE passés ; raccordé (raccordement
  d'essai `FournisseurDEssai`), 5 260 FE passés en quelques secondes. L'article le dit et renvoie au fournisseur.
- **Commandes de rôles.** La fiche disait tout réservé au niveau 2 ; `/role list`, `/role get` (soi-même) et
  `/role mayor` (voir le maire) sont ouverts à tous (`RoleCommands.register`).
- **Maire.** « Le statut de maire n'ouvre encore aucun geste » n'est plus vrai : l'économie et le téléphone lui en
  donnent (`TreasuryCommands`, `AntennaRules`, `PowerRules`, `TownBlockRules`).
- **Établi.** Les 20 FE/t ne sont payés que quand une tête de file est à cet établi (`wantsToWork`) ; l'écran affiche
  « À la main : vitesse x1 » tant que l'établi n'a rien à faire, même branché, et « Alimenté » pendant une fabrication.
- **Station météo.** Météo figée (`doWeatherCycle` à faux) : elle ne lit que le temps présent (`read`) ; ce n'est pas
  un défaut, mais la fiche ne le disait pas (vu dans l'atelier : pluie annoncée dans 2 500 tics, station restée au
  soleil tant que le cycle était coupé).
- **Débris.** Butins complets relevés dans `loot_tables/blocks/debris_*.json` (le fuselage donne aussi 3 à 6 pépites
  de fer, la cabine 0 à 1 ferraille…).

Constats en jeu (atelier, journal `[wiki] constat`) :

- Testeur sur un séchoir à 15 % : six lignes dans le chat (état, aspect, énergie, pièce à prévoir, corrosion).
- Réparation au poste à souder par un vrai clic droit, fusible dans la seconde main : séchoir de nouveau « En
  marche », état 100 %, poste passé de 2 000 à 1 600 FE.
- Éclair à 5 blocs de deux machines protégées par un parafoudre : le séchoir n'a rien perdu (seulement son usure
  normale), le parafoudre est passé de 100 à 90 points.
- Débris de câblage démonté en survie à la pioche de fer : le bloc part, des fils d'aviation tombent.
- Alternateur à 32 tr/min derrière un tableau de délestage, trois séchoirs en sortie : 20, 20 et 5 FE/t (45 FE/t), la
  troisième sortie « Partielle ».

### Relecture du chapitre (27/11, après-midi)

- **Compteur : le nom inscrit doit être le pseudo exact du payeur.** `PowerPairing.pairMeter` cherche le débiteur
  par ce nom (`knownPlayer`) ; un nom RP fait refuser le jumelage (« Aucun joueur connu du serveur ne s'appelle … »).
  L'indication de l'écran (« lettres, chiffres, espace, tiret ») ne cite pas le « _ », pourtant accepté
  (`MeterLedger.isNameChar`) : à corriger dans la langue. La première version de l'article montrait un nom RP.
- **Jumelé, le disjoncteur n'est plus à tous** (`BreakerBlock.toggle` → `PowerPairing.mayTouch` / `mayActOn` :
  fondateur, ingénieur, maire, équipe ; jamais l'abonné) ; et le courant ne passe qu'après l'accord de l'abonné, le
  « Accepter » du fournisseur, sans coupure (`PowerSuppliers.passes`).
- **Chaîne de la douche** : le premier montage (pompe verticale vers une pomme en hauteur) perdait l'eau ; relevé
  toutes les 20 s, le chauffe-eau restait à 0 mB d'eau potable et l'eau chaude cessait de monter dès que les 2 000 mB
  préremplis étaient passés. Cause exacte non établie (un bout de tuyau qui crachait au-dessus de la pomme, vu sur
  l'image). Remonté sans tuyau vertical (terrasse, chaîne horizontale), à partir de cuves vides : en 2 min, la pomme
  pleine (1 000 mB) puis l'eau chaude du chauffe-eau de 0 à 3 400 mB, au rythme de la production du dessalinisateur
  (2 mB/t). Le chauffe-eau relevé « à l'arrêt » à certains relevés n'est pas un arrêt : il chauffe plus vite (5 mB/t)
  qu'il ne reçoit, et attend l'eau suivante. Rien à signaler côté mod.

## Économie (tâche 5, 27/11)

Écarts entre les fiches v1 et le code, corrigés dans le wiki :

- **Où ranger.** « Le trésor et la poste s'ajouteront avec leurs lots » : le trésor (E3), la caisse du poste de
  fournisseur (E6) et celle de l'antenne (T2) sont des écrans permis (`MoneyStorageRules.register` dans
  `EconomyRegistry`, `GridRegistry`, `PhoneRegistry`) ; la poste n'existe pas.
- **Échange.** La note d'équipe décrivait l'écriture forcée de la garde « à chaque déconnexion, avec tous les joueurs
  en ligne et toutes les données de l'économie » : retirée depuis (leçon du 2026-11-14), la garde vit dans les données
  du joueur (`TradeEscrow`), écrites avec lui.
- **Étal, facture.** « Que le lot E3 rattachera au trésor », « le prélèvement arrive avec le lot E6 » : faits.
- **Quêtes.** « ~20 cuivres une journalière » : la récompense vaut celle du modèle × quantité ÷ minimum
  (`QuestTemplate.rewardFor`), soit 10 à 38 cuivres pour une journalière avec `quests.json` livré, 50 à 180 en hebdomadaire
  (× 5, × 3 pour les réparations). Vu : « Éliminer 5 zombies » paie 15 cuivres.
- **Trésor.** `/treasury board` (maire, équipe) donne un tableau de la mairie (`TreasuryCommands`) : absent des fiches.
- **Fournisseur.** Le poste se pose par n'importe quel joueur et appartient à son poseur (la fiche disait « Pose : le
  fournisseur ») ; un joueur n'a qu'un fournisseur (`already_has_one`) ; seuls le fondateur et l'équipe retirent de la
  caisse (`PowerRules.mayTakeTill`) ; le disjoncteur s'inscrit accroupi, main vide (`BreakerBlock.engineerGesture`).
- **Casse.** Coffre-fort, trésor, poste, étal : dureté -1 rendue aux seuls ayants droit, aucun outil exigé (la pioche,
  la hache pour l'étal, vont plus vite ; tags `mineable`), le bloc se ramasse.

Constats en jeu (atelier, journal `[wiki] constat`, passe du 27/11 16:27 sans échec) :

- Un joueur neuf entre : 500 cuivres sur lui (5 pièces d'or). Table : 1 or dans la grille donne 10 argent.
- Pièces jetées sur un entonnoir : après 4 s, rien dedans, 230 cuivres toujours dessus. Coffre : Maj + clic sur des
  pièces d'or, rien n'entre.
- Coffre-fort posé d'un clic, « Déposer tout » : 507 cuivres au compte ; un second coffre-fort ne se pose pas
  (« Vous avez déjà un coffre-fort : un seul par joueur »).
- Échange par `/trade` (joueur simulé) et lien « [Accepter] » : 5 pains contre 1 or, reçu remis.
- Étal (le joueur de l'atelier, opérateur, s'accroupit pour voir la vitrine) : 4 pains pour 3 argent, 140 → 110
  cuivres, monnaie dans la bourse ; une vente au registre de l'étal.
- Facture émise au carnet (formulaire, « Émettre ») ; facture reçue (45 cuivres) payée par « Payer » : 100 → 55.
- Trésor posé d'un clic, 2 or déposés, 1 or retiré avec le motif « Lanternes de la place » ; tableau : « Solde : 1 or ».
- Fournisseur fondé au panneau, tarifs du lendemain, compteur et disjoncteur inscrits puis jumelés au testeur,
  accord de l'abonnée, « Accepter » : séchoir en marche, 7 220 FE comptés ; « Couper » : disjoncteur ouvert, le séchoir
  encore en marche à l'instant du relevé (sa réserve).
- Butin : 5 zombies nés par apparition naturelle tués à l'épée, des pièces de cuivre au sol dès la première vague.
- Minuit (horloge de l'économie avancée) : 1er, salaire de 3 cuivres (Policier, 0 h 3 min) versé au coffre-fort,
  nouveaux tarifs ; aucune facture d'électricité pour un jour à tarif nul. 2e, facture de 16 cuivres (6,8 kFE à 2 argent
  les 100 000 FE + abonnement 15) « prélevée … pris sur vous ». 3e, sans pièce : « Prélèvement refusé… » et le lien
  « [Recevoir la facture] », qui remet le papier payable.

Défauts d'affichage relevés (le mod n'est pas modifié ici) :

- `/eco loot` imprime l'identifiant du palier (« common ») et `/eco quests` celui des modèles (« kill_zombies »), sans
  nom français ; pas de capture de ces deux commandes dans le wiki pour cette raison.
- `/eco ledger` : la ligne d'une facture publique prélevée montre la référence interne (`electricity:2/…`) et
  « (held) ».
- Salaire au chat : date ISO (« Salaire du 2026-11-27 », `debt.day().toString()` dans `PayrollService`) quand la
  facture écrit « 28/11/2026 » ; « 100000 FE » sans espace dans les messages de tarif et de facture, « 7220 FE » au
  panneau, alors que le plafond y est écrit « 100 000 FE ».

Relecture (lots A et B, 27/11 soir), vérifié dans le code :

- **Quêtes au plafond** : `QuestService.claim` paie `min(récompense, reste)` puis marque la quête réclamée : le
  surplus est perdu (`QuestClaimGameTests.lePlafondReduitPuisArrete`) ; la fiche v1 disait « la quête attend le
  lundi », vrai seulement quand le reste est nul.
- **Rubis** : il naît aussi d'un paiement d'au moins 10 000 cuivres rendu en grosses coupures (`CoinMath.decompose`,
  `CommerceDelivery.payTo`, caisse d'étal `StallTrade.buy`).
- **Échange, mort** : pièces et contenu des bourses vont au coffre-fort s'il y en a un (`giveBack`, `park`,
  `intoSafe`), le reste en attente.
- **Fournisseur** : le fondateur et l'équipe agissent sur leur propre raccordement (`PowerPairing.mayActOn`) ; seuls
  un ingénieur ou un maire clients sont refusés. Le disjoncteur s'inscrit accroupi ; debout, le clic le bascule.
- **Bourse** : une bourse qui contient des pièces est de l'argent (`MoneyStorageRules.isMoney`), refusée aux coffres.

- **Registre de l’économie : accents manquants** (vu sur la capture de `/eco ledger`, 27/11) : « tresor » et « somme de
  depart » sont écrits sans accent dans les motifs enregistrés par le mod. Non corrigé.

## Tâche 6 : Interface, Maire, chapitres à venir, accueil (27/11 soir)

Statuts changés, preuve à l'appui :

- **Menu F3** (`interface/menu-f3`, « Prévu » → article) : chantier 3 livré (`tasks/todo.md`), code dans
  `client/debug/` (`DebugPanels`, `IslandLines`, `DebugKeys`) ; captures d'un vrai client (`F3Shots`, run-titre,
  `-PdebugShots=frames-wiki-f3`, 0 échec). Usure, panne et index réservés à `RoleGate.canDoEngineerWork`
  (`IslandDebugInfo.collect`) ; sous `reducedDebugInfo`, réponse vide pour tous (`IslandDebugRequest.answer`) et panneau
  Lost Island masqué (`IslandLines.rows`).
- **Maire, le métier** (`metier-maire`, « Prévu » → article `maire/le-maire`) : statut `RoleBook.mayor`, préfixe
  `RoleTeams.mayorPrefix` ([Maire] [Métier], vu au-dessus d'un joueur simulé), un seul maire (`appointMayor`
  remplace), refus `power.not_mayor` / `operator.not_mayor`, salaire `mayorSalaryPerHour` (0).
- **Trésor et dépenses, tableau de la mairie, déplacer** (« Prévu » → article `maire/tresor`) : livrés au lot E3
  (`economy/town/`, `TownBlockRules`). Pas en jeu : l'alerte de l'équipe au-delà d'un seuil (nouvel élément prévu
  `alerte-depense`), le registre public et les annonces au tableau (restés prévus).
- **Réseaux, taxes et barèmes** (« Prévu » → article `maire/reseaux`) : `/power cap|tax|suppliers|set|owner|remove`
  (`PowerCommands`, lot E6) et `/operator list|cap|set|owner|remove` (`OperatorCommands`, lot T2), vus au chat d'un
  vrai client. Le maire agit sur postes, antennes et raccordements jumelés (`PowerRules.mayManage`,
  `AntennaRules.mayManage`, `PowerPairing.mayActOn`, sauf son propre raccordement), jamais sur les caisses. Pas en jeu :
  taxe des étals réglée par le maire (config `salesTaxPercent` seulement) et barème des amendes → élément prévu
  `taxe-ventes-amendes`.
- Restent prévus : bureau du maire, registre public, impôt sur les coffres-forts, annonces, appli Mairie ; tous les
  chapitres Médecin, Policier, Agriculteur, Casino, Loisirs, Substances (aucune classe de ces lots dans `src/main`).
  Médecin et Policier : l'intro dit ce qui existe déjà (métier, salaire du policier, appels 15 et 17).

Défauts vus pendant les captures (mod non modifié) :

- `/power suppliers` et les messages de plafond écrivent « 100000 FE » sans espace (déjà noté).
- Les pages de la vue détaillée du F3 et le panneau Cible affichent les lignes de Minecraft en anglais (« facing
  north », « state stopped ») : voulu (le texte vient de Minecraft), pas montré dans le wiki en dehors du rappel.
- L'écran titre garde « Copyright Mojang AB. Do not distribute! » de Minecraft : la capture publiée est recadrée.

Relecture finale de la tâche 6, vérifié dans le code :

- **Tableau de la mairie et ingénieur** : l'ingénieur outillé peut le casser (`TownBlockRules.mayMove`) mais pas le
  reposer (`mayPlaceBoard` : maire et équipe ; refus dans `TownBoardBlock.getStateForPlacement`). Le trésor, lui, se
  repose par l'ingénieur outillé s'il n'en existe plus (`mayPlaceTreasury`). Corrigé dans Maire, Téléphone (outil de
  maintenance) et Économie (note d'équipe). **Écart du mod, non corrigé ici** : le refus de casse dit « Seuls le maire,
  l'équipe et un ingénieur déplacent le tableau de la mairie » et l'info-bulle de l'outil « Réservé à l'ingénieur :
  antennes, trésor, tableau », alors que l'ingénieur ne peut que retirer le tableau.
- **Commandes du maire** : plafond, taxe et tarifs valent au lendemain ; changement de fondateur (`setFounder`) et
  suppression confirmée (`confirmRemove`, `/power` et `/operator`) prennent effet tout de suite. Le bouton
  « [Confirmer la suppression] » préremplit `… confirm` dans le chat (`SUGGEST_COMMAND`) : il faut valider par Entrée.
- **Panneau Mods du F3** : à gauche (`DebugLayout.Slot.MODS`, `right = false`) ; à droite, Performances et Cible.

## Bilan de la refonte (27/11)

- En ligne : <https://jlskyzer.github.io/OneForAllSurvival/>. Cinq chapitres en images (Téléphone 7 articles,
  Électricité 22, Économie 17, Interface 4, Maire 3), les chapitres à venir en texte. Contrôle du 27/11 sur le site
  publié : 65 pages ouvertes, aucune image cassée, aucun débordement, aucune erreur de script.
- Chaque chapitre a été relu par un agent distinct (deux pour les gros) contre le code et image par image, puis
  corrigé avant publication.
- Non prouvé : rendu sur un vrai téléphone et d'autres navigateurs qu'Edge ; lecteurs d'écran ; plusieurs gestes
  prouvés par la seule lecture du code (dits dans les rapports) ; configuration réelle du serveur de jeu.
- Reste connu : quelques couvertures de cartes qui coupent du texte ; chats et grands écrans petits à 390 px.

## Corrections (27/11 soir, après les captures)

Les quatre défauts du mod relevés pendant les captures sont corrigés (un commit chacun). Preuves : JUnit 1266/1266 ;
GameTests 1242/1242 sans le pack puis avec (`-PwithPack`), sur le code final ; essais vus rouges avant chaque
correction ; passe de mutations avec le pack, chaque mutation d'une règle de droit rougit un essai différent.

- **Connecteur sur l'antenne** : un objet de l'étiquette `oneforallsurvival:power_connectors` (Crafts & Additions :
  petit connecteur, grand connecteur, petit connecteur à lampe ; Create New Age : connecteur électrique ; Advanced
  Electricity : boîte de jonction, ces deux derniers ajoutés après relecture ; entrées facultatives) passe à sa pose,
  debout comme accroupi (`AntennaBlock.use`). Le panneau reste à la main vide et à tout autre objet. Droits inchangés :
  la pose d'un bloc contre l'antenne n'en demande aucun, un passant posait déjà un connecteur accroupi. Le connecteur se
  pose sur le dessus de l'antenne (`AntenneDroitsGameTests.unConnecteurSePoseDeboutCommeAccroupi`, avec le pack : il
  découvre les connecteurs à partir de ceux que `ReseauxDev` pose contre un bloc et de leur famille de blocs, et ne
  recopie plus de liste ; la pose sur le dessus est essayée, pas le courant reçu par l'antenne). Même défaut,
  laissé tel quel : établi de l'ingénieur, marmite, dessalinisateur, séchoir, frigo, couveuse, tableau de délestage, bain
  galvanique, chauffe-eau (tout objet ouvre l'écran) ; prise murale libre (« occupée » pour tout objet autre qu'un
  téléphone).
- **`/eco loot` et `/eco quests`** : paliers et modèles livrés nommés en clair (« Monstres courants », « Éliminer des
  zombies »), en français et en anglais ; un palier ou un modèle de datapack sans libellé, ou un événement, garde son
  identifiant (`EconomyCommands.tierLabel`, `templateLabel`).
- **Registre de l'économie** : motifs accentués (« somme de départ », « trésor : … », « dépôt au trésor », étals, taxe
  sur l'électricité, « dès demain », « caisse à », pièces « restée » ou « retirées »). Une facture publique se lit
  « facture publique 1a2b3c4d (Lagon 27/11) de Pierre : prélevée au coffre-fort » : le numéro court (celui de
  `/invoice`) et le motif remplacent la référence interne, la source est dite en français. Les anciennes lignes restent
  telles quelles ; rien ne relit ces motifs. Non traité : les motifs du registre du trésor (« taxe sur la vente a un
  etal »).
- **Tableau de la mairie** : l'ingénieur qui tient l'outil de maintenance le repose (`TownBlockRules.mayPlaceBoard`),
  comme le dit le cahier du maire (102) ; `/treasury board` (tableau neuf) reste au maire et à l'équipe
  (`mayGiveBoard`). Refus de pose : « Seuls le maire, l'équipe et un ingénieur muni de l'outil de maintenance posent un
  tableau de la mairie ».

Après relecture (27/11 soir) : en plus des deux connecteurs, des essais gardent désormais les appels eux-mêmes,
`/eco quests` sur un modèle livré (`QuestClaimGameTests.ecoQuestsMontreLesTotaux`, « Slay zombies » attendu) et les
trois écritures de motif de `DirectDebit` au registre (lot `prelevement_c` : refus, prélèvement, paiement par l'objet).
Vus rouges ensemble, avec le pack, sur l'ancienne étiquette et les anciens textes remis dans le code (cinq échecs
voulus sur 1253) ; puis JUnit 1294/1294 et GameTests 1253/1253 sans puis avec le pack. Constaté en route : un
connecteur New Age retiré dans le tic de sa pose lève une `NullPointerException` dans Create New Age (réseau pas
encore créé) ; l'essai laisse deux tics entre pose et retrait.

## Wiki du 28/11 (livraison 61bf545)

Articles mis à jour d'après le code : antenne (connecteur debout, sur le dessus), ligne (SIM en créatif), appels
(clavier, « Appuyer pour parler » et « Chuchoter », V = interface du chat vocal, lu dans le jar de Simple Voice Chat
2.6.22 : aucune touche par défaut pour parler), le téléphone (code au clavier, ligne Face ID des Réglages), outil de
maintenance, mairie et économie (pose du tableau par l'ingénieur outillé, nouveau refus), `/eco loot`, `/eco quests`
et registre (libellés), interface (aube du titre avec la fenêtre de Forge, panneau Quick-Skin, Reggadium). Deux
articles neufs : « Face ID » et « Partager son numéro », images du mode `faceid` de `TelephoneShots` (28/11).

Constaté en route, non corrigé (hors wiki) :

- Écran titre à 1920 × 1040, échelle 2 : le bouton « Solo » recouvre le bas de « Là où tout recommence. » (vu sur
  `run-titre/frames-panneau-avant` et `-apres`, `panneau-1920x1040-e2.png` : antérieur au panneau Quick-Skin). À
  1280 × 720 rien ne se chevauche.
- Le message reçu par le destinataire d'un numéro (« … veut vous donner son numéro (…). [Accepter] [Refuser] ») n'a pas
  d'image : le second joueur de l'essai est simulé, son chat n'est pas rendu.

Captures reprises le 28/11 en Robin : `TelephoneShots` modes `faceid`, `reseau` et `appel`
(`run/frames-wiki-faceid`, `-reseau-2811`, `-appel-2811`) et chapitre Économie de l'atelier ; Réglages et Contacts
changés (ligne Face ID, « Partager mon numéro »). Image du panneau Quick-Skin : `run-titre/frames-panneau-apres` (passe
du lot C, code de fedd0f2). Les demi-lignes de chat déjà connues (`minuit-preleve`) restent.
