# Lot T2 du téléphone : antenne, opérateur, panneau, relais et carte SIM — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Le réseau du téléphone est construit par les joueurs et coûte de l'électricité. L'ingénieur fabrique une antenne ; n'importe qui la pose et fonde un opérateur en lui donnant un nom ; l'antenne consomme (veille, cartes actives, trafic) et tombe hors service sans courant ; le fondateur émet, suspend et réactive des cartes SIM depuis le panneau de l'opérateur, fixe un tarif qui vaut au lendemain ; une carte glissée dans le téléphone lui donne un numéro, un opérateur et un signal de 0 à 4 barres, et le mode avion coupe tout. Le fondateur gère son opérateur au panneau de l'antenne (écran Minecraft) ou dans l'appli « Antenne » de son téléphone (page HTML façon iOS ; écran de repli : le même panneau Minecraft). L'ingénieur n'a la main sur une antenne qu'avec son **outil de maintenance**, un objet nouveau fabriqué à son établi. Le trajet d'un appel entre deux opérateurs et l'état du relais se calculent ; les appels eux-mêmes arrivent au lot T3, le prélèvement de l'abonnement au lot T6.

**Architecture:**
- **Règles pures** (`phone/net/`, JUnit) : `SignalMath` (barres selon la distance), `AntennaLoad` (consommation), `SimNumbers` (numéros `06 xx xx xx xx`), `RelayPlanner` (trajet téléphone → antenne de l'appelant → antenne de l'appelé → téléphone, ou la raison de l'échec).
- **Registre** `PhoneOperators` (`SavedData`, un seul fichier pour que la suppression d'un opérateur reste cohérente) : opérateurs, caisse, tarifs, cartes, demandes, antennes, avis. Modèle : `PowerSuppliers` du lot E6.
- **Antenne** (`AntennaBlock`, `AntennaBlockEntity extends ElectricMachineBlockEntity`) : machine de base du lot 0 (usure, pannes, réparation du lot 2), consommation variable, chunk gardé chargé (`AntennaChunks`), protégée comme le poste du lot E6 (`AntennaRules`).
- **Outil de maintenance** (`maintenance_tool`, `repair/MaintenanceToolItem`) : un objet de plus au catalogue de l'établi d'ingénieur (recette de table hors du tag `table_only`, que `BenchCatalog` sort du `RecipeManager` puisque `oneforallsurvival` est un mod géré de `BenchConfig.managedMods`), tag `oneforallsurvival:maintenance_tools` ; exigé de l'ingénieur pour ouvrir, régler ou casser une antenne, et pour déplacer le trésor et le tableau (`TownBlockRules.engineerMayMove`).
- **Carte SIM** (`SimCardItem`, `PhoneSims`) : objet émis, jamais fabriqué ; un **jeton** tourné à chaque insertion fait qu'une copie de la carte ne donne jamais une seconde ligne. La carte vit dans le NBT du téléphone.
- **Façade réseau** `PhoneNetwork` : statut d'un téléphone (carte, opérateur, barres), trajet d'un appel, trafic, antenne d'urgence, état du relais. C'est l'interface que le lot T3 consommera.
- **Téléphone** : `PhoneStatePacket` gagne une vue réseau, `PhoneActionPacket` quatre gestes ; pages HTML et écran de repli montrent la carte, l'opérateur, les barres, le mode avion, la feuille « Carte SIM » et l'icône de l'appli « Antenne » du fondateur.
- **Gestion de l'opérateur, une vue et trois écrans** : le serveur construit **une seule vue** (`OperatorView`, par `OperatorViews.build`) et applique **un seul jeu de règles** (`OperatorActions.apply`) ; la vue voyage dans un seul paquet (`OperatorPanelPacket`, n° 28) et les gestes reviennent par un seul paquet (`OperatorActionPacket`, n° 29). Trois écrans la montrent sans rien décider : le **panneau Minecraft** du bloc antenne (`OperatorPanelScreen`, comme le panneau du poste d'E6), l'**appli « Antenne » en HTML** dans la page du téléphone (`window.phone.antenna(view)`, JSON produit par `OperatorViewJson` à partir du même `OperatorView`), et, dans l'écran de repli du téléphone (sans MCEF), le **même panneau Minecraft** ouvert depuis le téléphone. Tarif au lendemain par un émetteur de jour `DailyDebit` qui ne facture rien à ce lot (le lot T6 y ajoutera les factures).
- **Gestes du maire et de l'équipe** : `/operator list|cap|set|owner|remove`.

**Tech Stack:** Forge 1.20.1-47.4.28, Java 17, ForgeGradle 6, MCEF 2.1.6 (client seulement, comme T1), JUnit 5, GameTests, pack (`-PwithPack` : Create 6.0.8, Crafts & Additions, New Age, Advanced Electricity), Python/PIL pour les textures. Paquets réseau : protocole `"10"` → `"11"` (une seule fois, tâche 7), identifiants nouveaux 28 et 29 (tâche 9).

**Révision du 2026-11-20 (réponses de Killian)** : chunk gardé chargé et 6 FE/t par carte active par antenne confirmés ; l'outil de maintenance devient un **objet nouveau** (tâche 4) ; l'appli « Antenne » devient une **page HTML** (tâche 10, nouvelle). Le plan passe de 13 à **14 tâches** : l'appli HTML consomme les paquets 28 et 29 et la vue `OperatorView` de la tâche 9, elle ne peut donc pas vivre dans la tâche 8 (qui la précède), et un relecteur peut la refuser sans refuser le panneau. L'outil reste dans la tâche 4, dont il est la règle.

**Spec:** `docs/superpowers/specs/2026-11-08-telephone-design.md` (validé le 2026-11-19). Sections utiles : 3 (serveur qui fait foi ; FE par n'importe quel câble ; échéances à minuit), 4 (carte SIM, opérateur, maire et ses gestes calqués sur l'électricité), features **89, 90, 91**, 92 (pour ce que T2 prépare seulement), 101 (Réglages), 6 (branchements : ingénieur, électricité, maire), 7 (lot T2), 8 (vérification : lignes « signal », « consommation », « qui ouvre ou casse », « seul le fondateur », « appel entre deux opérateurs », « opérateur supprimé », « caisse »), 9 (décisions du 2026-11-08 et du 2026-11-19, choix de l'auteur du 2026-11-19). Autres sources : `docs/verifications/lot-t1.md` (ce qui existe, limites), `tasks/lessons.md` (toutes les leçons), plan archivé du lot E6 (`docs/superpowers/plans/archive/2026-11-17-lot-e6-prelevement-fournisseur.md`) et son code (`grid/supplier/`), `economy/town/TownBlockRules.engineerMayMove` (point d'extension « outil de maintenance » laissé par E3), cahier de l'économie archivé, ligne 281 (« un ingénieur avec son outil de maintenance, comme pour les antennes »).

## Global Constraints

Les contraintes globales des plans T1 et E6 restent valables telles quelles (sections « Global Constraints » des deux plans archivés). Rappel des points qui mordent à ce lot :
- **`gradle.properties` en ASCII seulement** (commentaires sans accents).
- **Le serveur fait foi** pour les opérateurs, les cartes, les antennes, le signal et le tarif. Rien de ce que la page, l'écran de repli ou le panneau envoie n'est cru : chaque geste est rejoué par le serveur (session ouverte, même téléphone en main, droits, position à portée, carte du bon opérateur, format).
- **Textes** dans `fr_fr.json` avec repli `en_us.json`, accents corrects, jamais « SolarTown » ; pages HTML en français en dur (limite acceptée au lot T1).
- **Aucune écriture forcée hors de la sauvegarde du monde** (leçons du 2026-11-14) ; **tout `SavedData` est marqué sale** à chaque changement persistant (`setDirty()`), et ce qui bouge à chaque tic (consommation du jour d'une antenne) est cumulé en mémoire et marqué sale au plus tous les 20 tics.
- **Horloge injectable** : tout ce qui dépend du jour prend `long now` lu par `EconomyClock.now()` à l'appelant ; jours par `EconomyCalendar` ; un GameTest qui règle l'horloge a un lot à lui avec `@BeforeBatch`/`@AfterBatch` (`EconomyClock.reset()`).
- **GameTests** : lots de 8 au plus ; `JoueurDeTest` pour tout joueur d'essai (opérateur par `getOps().add(new ServerOpListEntry(profil, 4, false))`, retiré en fin de test) ; cases au-dessus du plancher mises à l'air avant toute pose (leçon du 2026-11-20) ; entités comptées dans la boîte du gabarit (leçon du 2026-11-15) ; un test « chunk non chargé » place une frontière de chunk entre l'antenne et tout bloc d'un mod qui force des chunks (leçons du 2026-11-11 et du 2026-11-19).
- **Preuve des tests** : `./gradlew --offline test runGameTestServer`, puis `./gradlew --offline runGameTestServer -PwithPack` ; lire « All N required tests passed » dans `run-gametest/logs/latest.log` et la somme des `build/test-results/test/*.xml`, jamais le seul code de sortie. Compiler (`compileJava compileDevcontentJava`) **avant** de prendre le verrou (leçon du 2026-11-20).
- **Tout lancement de Minecraft** (GameTests, client, serveur, captures, E2E) suit la procédure du verrou : section 11 de `C:/Users/killi/.claude/CLAUDE.md` (points 1 à 8) et la note `D:/BDC/Minecraft/Deux sessions d'agents qui lancent Minecraft sur la même machine passent par un fichier de verrou et une file d'attente.md`. Prise par un script qui relit verrou et file et refuse s'il n'est pas vide ; vidage sans BOM, seulement s'il contient sa propre ligne, dans le `finally` du script qui l'a pris ; verrou cédé entre deux passes si la file n'est pas vide ; se parler entre sessions (point 8) ; un sous-agent qui ne peut pas écrire à une autre session le signale à la session qui l'a lancé. Ces règles sont recopiées dans le brief de tout sous-agent qui lance Minecraft.
- **Fenêtres d'essai sur le second écran** (`dev/FenetreSecondEcran`, x = 2560) : lire « [fenetre] second écran » au journal. **Souris rendue** : tout client passe une propriété `oneforallsurvival.*` (`dev/SourisLibre`) ; lire « [souris] rendue ». Aucun client laissé ouvert ; n'arrêter que ses propres PID ; ne jamais toucher une JVM dont la ligne de commande contient `AXIONENGINE` ; serveur arrêté par RCON (`stop`) ; ne jamais modifier `run/options.txt` ; jamais la touche F10.
- **Aucun téléchargement hors de Gradle** : Chromium vient de `run-mcef/mcef-libraries` (copie du launcher, lot T1) ; la requête `.sha256` de MCEF reste celle, déjà acceptée en développement, du lot T1.
- **Protection d'un bloc du mod** (comme le poste et le compteur d'E6) : dureté -1 et résistance maximale, `PushReaction.BLOCK`, tags `minecraft:wither_immune` et `minecraft:dragon_immune`, tag `create:non_movable` (`data/create/tags/blocks/non_movable.json`, `replace: false`), progression de casse rendue aux seuls ayants droit (`getDestroyProgress`), tout autre bris annulé (`BlockEvent.BreakEvent`), aucun faux joueur (`FakePlayer`) ni distributeur ne pose, n'ouvre ni ne casse.
- **Modèle qui n'occupe pas le cube** : `noOcclusion()` et une `getShape` exacte, comparée dans le test à des boîtes écrites en clair (leçon du 2026-11-20) ; capture collée à un mur et sous un bloc (leçon du 2026-11-10).
- **Pages du mod** (feuille « Carte SIM » et appli « Antenne ») : même discipline qu'au lot T1. Le contrat page ↔ Java est écrit à la lettre dans la tâche qui le crée et recopié tel quel par le code Java ; tout texte venu de la vue est posé par `textContent`, jamais `innerHTML` ; aucun `eval` ; chaque page garde la balise CSP du lot T1 et aucune adresse `http://` ni `https://` (`WebResourcesTest`) ; un mode `#mock` montre chaque écran hors du jeu ; la page se vérifie sur une copie du dossier `html/` sans la ligne CSP, servie en local, dans le navigateur intégré (captures **regardées**), puis dans un vrai client MCEF (clavier compris). Aucune règle ne vit dans la page : elle n'envoie que des gestes, que le serveur rejoue.
- **Objet d'ingénieur** : l'outil de maintenance suit les objets d'ingénieur existants (`RepairRegistry`, `tabItems`, recette de table hors de `table_only`, catalogue de l'établi par `BenchCatalog`) ; lire `bench/BenchCatalog.java`, `bench/BenchRegistry.java` et `repair/RepairRegistry.java` avant d'écrire.
- **Textures par script Python/PIL** (`tools/gen_textures_t2.py`) ; toute capture est **ouverte et regardée** avant d'être citée.
- **Échappements Unicode** dans un fichier source : écrits par un script Python (`chr(92)`), vérifiés au `grep` (leçon du 2026-11-20).
- **Git** : travail sur `main`, sans branche. Commit et push après chaque tâche vérifiée, **par chemin** (`git commit -- <chemins>`, jamais `git add -A`, `git mv` ni `git rm` ; supprimer par `rm` seul : leçons du 2026-11-08 et du 2026-11-15), sans `--no-verify` ni `--force`, message au format du dépôt terminé par `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. **Jamais** `art/`, `docs/assets/`, `.superpowers/`, `run*/` (dont `run-mcef/`) ni `waveletNoiseTile.bin` dans un commit. Push refusé : `git pull --rebase` puis push.

## Review Focus

1. **Une carte SIM copiée** (clic milieu en créatif, mod de duplication, NBT forgé par `/give` ou `/data`) : deux piles du même `sim_id` ne donnent jamais deux lignes ; seule la dernière glissée dans un téléphone est reconnue, l'autre s'affiche « Carte non reconnue » et ne compte pour rien (ni signal, ni consommation, ni trajet). Le jeton ne sort jamais du serveur, ni dans la pile, ni dans la vue de l'appli « Antenne » (qui ne porte que les cartes de l'opérateur du regardeur, par `sim_id`). Tests : `uneCopieDeLaCarteNeDonnePasDeuxLignes`, `uneCarteForgeeEstRefusee`, `leJetonNeVaPasAuClient` (tâche 5), `laVueNeContientNiJetonNiCarteDUnAutreOperateur` (tâche 9).
2. **L'antenne loin de tout joueur, puis le serveur relancé** : chunk déchargé, chunk forcé, tickets de chargement à la relance, antenne retirée sans que le jeu le sache. Attendu : une antenne n'est « en service » que si elle a payé son courant dans les 40 derniers tics ; jamais de réseau gratuit, jamais de ticket orphelin. Tests : `uneAntenneLoinDeTousResteEnService`, `sansChunkForceUneAntenneDechargeeEstHorsTension`, `lesTicketsDUneAntenneDisparueSontRendus` (tâche 3).
3. **Un client trafiqué ou un joueur sans droit** : paquet de panneau d'un non-fondateur, geste de l'appli « Antenne » d'une session périmée ou d'un téléphone sans carte de cet opérateur, carte d'un autre opérateur, antenne hors de portée, déployeur, perceuse et engin de Create, piston, explosion, faux joueur, **outil de maintenance volé** (un citoyen qui le tient n'a aucun droit : il faut le rôle d'ingénieur **et** l'outil). Attendu : rien ne bouge, l'état est renvoyé tel quel. Tests : `unSansDroitNeTouchePasALAntenne`, `unOutilVoleNeDonneRien` (tâche 4), les quatre tests du pack (tâche 4), `unPaquetDUnNonFondateurNEmetRien` (tâche 9), `uneAppliPerimeeNeFaitRien` (tâche 10).
4. **Un opérateur supprimé** : cartes en circulation, antennes posées, antenne démontée reposée, demandes en attente, caisse pleine, panneau ouvert. Attendu : aucune carte active, aucune antenne qui consomme ni ne relaie, l'objet d'antenne ne ressuscite rien, demandeurs prévenus, caisse versée au fondateur, somme des pièces conservée. Tests : `unOperateurSupprimeNeLaisseRienDActif` (tâche 11), `unOperateurSupprimeNeRelaiePlus` (tâche 6).
5. **Un monde et des téléphones du lot T1** : aucune donnée d'opérateur sur le disque, téléphones sans carte, ancienne vue du client (protocole 10). Attendu : tout se charge, « Aucune carte SIM », un client du protocole 10 est refusé proprement. Tests : `uneAncienneSauvegardeSansOperateurSeCharge` (tâche 2), `unTelephoneDuLotT1MontreAucuneCarte` (tâche 7).

## Décisions (inscrites telles quelles)

| Sujet | Décision |
|---|---|
| Fabrication | L'**antenne** et l'**outil de maintenance** : seul l'ingénieur (recettes de table **absentes** du tag `table_only`, que `BenchCatalog` range à l'établi d'ingénieur). Le téléphone et la prise : par tous (lot T1, inchangé). La **carte SIM** n'a aucune recette : seul un opérateur l'émet. |
| Pose | N'importe quel joueur pose une antenne : il fonde un opérateur (nom donné au premier panneau) ou ajoute une antenne au sien. |
| Ayants droit d'une antenne | Fondateur, maire, équipe (niveau 2), ingénieur **muni de l'outil de maintenance** : ouvrir, régler, casser. Jamais un faux joueur. |
| Cartes | **Seul le fondateur** crée, suspend, réactive (ni le maire, ni l'ingénieur, ni l'équipe au panneau). |
| Caisse | Une par opérateur, gardée par le serveur ; vidée par le **fondateur et l'équipe** seulement. Le maire et l'ingénieur ont la main sur l'antenne, pas sur l'argent. |
| Tarif | Par jour, fixé au panneau ; un changement vaut au **lendemain**, sous le plafond du maire ; abonnés prévenus, à leur connexion s'ils sont absents. Aucune pièce prélevée à ce lot (lot T6). |
| Maire | Casse ou modifie une antenne, supprime un opérateur (cartes désactivées, antennes qui ne relaient plus, caisse versée au fondateur), plafonne le tarif, change le fondateur. Gestes calqués sur `/power` d'E6. |
| Protection | Comme le poste du lot E6 : ni pistons, ni machines de Create, ni faux joueurs (Global Constraints). |
| Valeurs de départ (réglables, `oneforallsurvival-phone.toml`, fichier SERVER) | Portée 270 blocs ; veille 40 FE/t ; 6 FE/t par carte active ; appel 9 FE/t ; FaceTime 24 FE/t. |
| Chunk de l'antenne (Killian, 2026-11-20) | **Gardé chargé** : ticket Forge « qui tique » sur le chunk de l'antenne seul, réglage `antennaKeepsChunkLoaded = true` ; ticket rendu quand l'antenne disparaît ou que son opérateur est supprimé ; tickets revalidés au démarrage. Le générateur qui l'alimente doit être dans le même chunk ou chargé autrement (dit dans le wiki). |
| Cartes actives (Killian, 2026-11-20) | Chaque antenne en service paie 6 FE/t **par carte `ACTIVE` de son opérateur**, insérée ou non. |
| Outil de maintenance (Killian, 2026-11-20) | Un **objet nouveau**, `maintenance_tool` (« Outil de maintenance » / « Maintenance tool »), fabriqué par l'**ingénieur** à son établi ; tag `oneforallsurvival:maintenance_tools` (qui ne contient que lui). Un ingénieur n'a la main sur une antenne qu'en le tenant dans une main ; un autre joueur qui le tient n'a rien. Le même outil est exigé par `TownBlockRules.engineerMayMove` (trésor et tableau, point d'extension laissé par E3). |
| Appli « Antenne » (Killian, 2026-11-20) | Une **page HTML façon iOS** dans le téléphone du fondateur. Le **bloc antenne garde son panneau Minecraft** (feature 90 : « il s'ouvre sur l'antenne ») ; l'**écran de repli** du téléphone (sans MCEF) ouvre ce même panneau Minecraft pour l'appli. Une seule vue serveur, un seul jeu de règles, les mêmes paquets 28 et 29. |

## Choix de l'auteur du plan (à valider par Killian)

| Sujet | Choix | Pourquoi |
|---|---|---|
| Barres | Distance en 3D entre le joueur et le centre de l'antenne, même dimension seulement ; `d < R/4` : 4 ; `< R/2` : 3 ; `< 3R/4` : 2 ; `≤ R` : 1 ; au-delà : 0. Le meilleur signal parmi les antennes **en service** de l'opérateur de la carte | Lisible, une mine profonde perd du réseau |
| « En service » | État de machine `RUNNING` (veille payée) relevé par le registre, **daté** ; une antenne vue `RUNNING` il y a plus de `antennaStaleTicks` (40) tics est hors tension. Panne (`FAULT`), manque de courant (`NO_POWER`), opérateur absent ou antenne non nommée : hors service, voyant éteint | Jamais de réseau gratuit ni d'état figé |
| Trafic | Appel 9 FE/t et FaceTime 24 FE/t tant que le trafic est ouvert, sur chaque antenne du trajet ; message : 100 FE une fois (`antennaMessageFe`) par antenne du trajet. À ce lot, seule l'interface existe (`PhoneNetwork.openTraffic`, `messageSent`), appelée par les GameTests ; T3 la branchera | « Peu pour un message » |
| Réserve de l'antenne | 40 000 FE, reçoit jusqu'à 4 000 FE/t (`antennaCapacity`, `antennaMaxReceive`) : `TOWN_MAX_RECEIVE` (200) ne suffirait pas au-delà de 26 cartes. Une consommation au-delà de ce que l'antenne reçoit est signalée en rouge au panneau | Un gros opérateur reste alimentable |
| Usure | L'antenne hérite de l'usure et des pannes du lot 2 ; l'ingénieur la répare comme les autres machines | « Il les entretient » (section 6) |
| Outil de maintenance : recette et usage | Recette de table (ingrédients vanilla, sans condition de mod) `[" IR"," CI","S  "]`, `I` lingot de fer, `R` redstone, `C` lingot de cuivre, `S` bâton ; un par pile, sans usure. Clic droit d'un ingénieur avec l'outil (n'importe quelle main) sur une antenne : le panneau ; le testeur garde son rapport de machine | Un objet d'ingénieur simple, sans dépendre de Create |
| Opérateur | Un par joueur ; nom de 1 à 24 caractères, unique sans casse (règle de `PowerSuppliers.validName`) ; identifiant de 12 caractères hexadécimaux (comme `PowerSuppliers`). Une antenne démontée lâche un objet qui porte l'identifiant de l'opérateur ; reposée par un ayant droit, elle le reprend. Un opérateur sans antenne existe toujours (cartes, caisse) | Modèle du poste d'E6 |
| Antenne principale | La première posée ; le fondateur en désigne une autre au panneau ; cassée, la plus ancienne restante la remplace. La caisse ne s'ouvre qu'**en personne, sur l'antenne principale** | Le cahier parle de « la caisse de l'antenne principale » |
| Numéro | `06` puis 8 chiffres tirés au hasard, unique sur le serveur, affiché `06 12 34 56 78` ; jamais réattribué, même après désactivation | Un numéro ne change pas de main en secret |
| Demande de carte | Depuis Réglages > Carte SIM > « Demander une carte » d'un téléphone **déverrouillé** sans carte active : liste des opérateurs dont une antenne en service couvre le joueur ; une demande par joueur et par opérateur, 32 au plus par opérateur. Fondateur prévenu (à sa connexion s'il est absent). Émise : la carte (au nom du demandeur) arrive **dans l'inventaire du fondateur** (refusé, avec message, s'il est plein), qui la remet en main propre ; demandeur prévenu. Refusée : demandeur prévenu | Le cahier dit « demandes reçues » sans dire d'où elles viennent |
| Carte sans nom | Émise au panneau par le fondateur, sans titulaire ; « SIM sans nom » partout | Lettre du cahier |
| Insertion | Réglages > Carte SIM : liste des cartes de l'inventaire (case, numéro, opérateur, état) ; en choisir une la glisse dans le téléphone (l'ancienne revient à sa case) ; « Retirer » la rend à l'inventaire (refusé s'il est plein). Le serveur relit la case | « Choisir parmi celles de l'inventaire, retirer » (101) |
| Jeton de la carte | Chaque carte a un `sim_id` (son identité) et un jeton (`sim_token`) gardés dans le registre. À chaque insertion, le serveur tire un nouveau jeton, l'écrit dans le registre et sur la pile, et note le téléphone (`phone_id`) où elle est. Une pile dont le jeton ou le téléphone ne correspond pas est « Carte non reconnue ». **Le jeton n'est jamais envoyé au client** (`getShareTag` le retire, de la carte et de la carte logée dans un téléphone) | Une copie ne donne jamais une seconde ligne ; un client ne peut pas recopier un jeton qu'il ne voit pas |
| Vue verrouillée | La barre d'état montre l'opérateur et les barres (comme un vrai téléphone) ; numéro, titulaire, cartes de l'inventaire, opérateurs demandables et appli « Antenne » : seulement déverrouillé | Rien d'intime avant le code |
| Mode avion | Barres à 0, « Mode avion » dans la barre d'état, aucune demande ni appli « Antenne », trajet refusé (`CALLER_AIRPLANE`) | Il « prend effet » (feature 101) |
| Appli « Antenne » : accès | Icône visible pour le seul fondateur ; l'appli exige une carte `ACTIVE` de son opérateur et au moins une barre ; à distance, ni caisse ni nom (ces deux gestes restent sur l'antenne, en personne) | La caisse est « celle de l'antenne principale » ; le nom se donne à la pose |
| Panneau en direct | Renvoyé toutes les 20 tics tant qu'il est ouvert : par antenne, veille, clients, trafic, total FE/t, FE du jour et d'hier, état ; recette (total de la caisse) ; cartes par pages de 32 ; demandes ; relais | « Consommation en direct » |
| État du relais | Pour chaque autre opérateur : « joignable » (au moins une antenne en service), « hors tension » (antennes, aucune en service), « sans antenne » ; et pour le sien, de même | Le panneau montre « l'état du relais vers les autres opérateurs » |
| Plafond et tarif | Tarif de 0 à `phoneTariffMax` (1 000 cuivres par jour) ; plafond de départ du maire `phoneDefaultTariffCap` = 20 cuivres par jour ; un tarif au-dessus d'un nouveau plafond y est ramené à la clôture suivante, fondateur prévenu (comme E6) | Ordre de grandeur d'E6 (abonnement d'électricité jusqu'à 50) |
| Clôture du jour | Un émetteur `DailyDebit.registerIssuer("phone", OperatorDay)` qui, à ce lot, applique les tarifs et le plafond du lendemain et remet à zéro les FE du jour ; il partage la pause et le rattrapage de la paie (E3, E6). Il **n'émet aucune facture** | Le lot T6 n'aura qu'à y ajouter les factures, sans nouvelle horloge |
| Taxe téléphone | **Renvoyée au lot T6** : elle ne porte que sur des abonnements prélevés | Rien à taxer à ce lot |
| Commandes | `/operator list`, `cap`, `set <op> tariff`, `owner`, `remove … confirm` (maire ou équipe, refus clair sinon, chaque geste au journal) ; `/operator tax` au lot T6 | Gestes d'E6 qui ne dépendent que de T2 |

## Ce que T2 prépare sans le construire

- **Pour T3 (appels, messages, urgences)** : `PhoneNetwork.status`, `PhoneNetwork.route(…)` et `RelayFailure.describe()` (« Injoignable · Antenne de <opérateur> hors tension », « Injoignable · Hors réseau », « Carte suspendue », « Numéro inconnu »), `PhoneNetwork.lineOf(number)` (la carte et le `phone_id` où elle est), `PhoneNetwork.openTraffic(...)`/`TrafficHandle.close()`, `PhoneNetwork.messageSent(...)`, `PhoneNetwork.emergencyAntenna(GlobalPos)` (n'importe quelle antenne en service, tout opérateur). **Reste à T3** : retrouver le joueur qui porte le téléphone de l'appelé (`PhoneLocator`), la voix, les sonneries, la coupure d'un appel à zéro barre, le grésillement à une barre, et le **blocage croissant** du code (condition de T3 au `tasks/todo.md`). **Poignées de trafic** (aucun garde-fou en T2, décision du contrôleur) : la session d'appel possède sa poignée et la ferme en un seul point (`finally` de la fin d'appel), sur raccrocher, refus, zéro barre, déconnexion de l'un ou l'autre (`PlayerLoggedOutEvent`), mort, changement de dimension, téléphone ou carte retiré, `ServerStoppingEvent` ; fil du serveur seulement ; une poignée oubliée coûte 9 FE par tic (24 en visio) à chaque antenne du trajet jusqu'au rechargement du monde (calcul, non mesuré) ; GameTest T3 « l'appelant se déconnecte : la consommation revient à la veille ». `route` est figé : le refaire à chaque relevé et rouvrir la poignée si les antennes changent ; `calleePos` nul donne `CALLEE_NO_COVERAGE`. Ne rien sérialiser directement depuis la `SimCard` de `lineOf` (jeton, UUID du titulaire). **Limiter le débit** des appels et des messages par joueur (un composeur automatique balaierait l'espace des numéros : « Numéro inconnu », « Carte suspendue », « Opérateur supprimé » se distinguent).
- **Pour T6 (abonnement)** : `PhoneOperators.cardsOf(opId)` avec l'état et le titulaire (`holder`, nul pour une carte sans nom), `tariffToday(opId)`, `deposit/till` (caisse), `OperatorDay` (émetteur déjà inscrit, où T6 émettra les factures **avant** d'appliquer les tarifs du lendemain). **Reste à T6** : `DebitPayees.register("phone", …)`, factures par carte active, carte suspendue à minuit qui ne paie pas, impayés par carte, taxe téléphone (`/operator tax`, `MoneyTransfer`, `TreasuryLedger`), `/eco stats`, annulation des factures dues d'un opérateur supprimé, et **qui paie une carte sans nom** (question à poser au lot T6).

## Fichiers

Racine du code : `src/main/java/dev/oneforallsurvival/` ; essais : `src/devcontent/java/dev/oneforallsurvival/dev/` (abrégé `dev/`) ; JUnit : `src/test/java/dev/oneforallsurvival/` ; ressources : `src/main/resources/`.

| Fichier | Rôle |
|---|---|
| `phone/net/SignalMath.java`, `AntennaLoad.java`, `SimNumbers.java`, `RelayPlanner.java`, JUnit `phone/net/*Test.java` | Règles pures (tâche 1) |
| `phone/net/PhoneOperators.java`, `PhoneOperator.java`, `SimCard.java`, `SimRequest.java`, `AntennaRecord.java`, `phone/PhoneConfig.java` (modifié), JUnit `phone/net/PhoneOperatorsTest.java` | Registre (tâche 2) |
| `phone/net/AntennaBlock.java`, `AntennaBlockEntity.java`, `AntennaItem.java`, `AntennaChunks.java`, `phone/PhoneRegistry.java` (modifié), recette, butin, blockstate, modèles provisoires, `dev/AntenneGameTests.java` | Antenne, fondation, consommation, service, chunk (tâche 3) |
| `repair/MaintenanceToolItem.java`, `repair/RepairRegistry.java` (modifié), recette `maintenance_tool.json`, `phone/net/AntennaRules.java`, `economy/town/TownBlockRules.java` (modifié), tags `maintenance_tools`, `non_movable`, `wither_immune`, `dragon_immune`, `dev/AntenneDroitsGameTests.java`, `dev/PackGameTests.java` (modifié) | Outil de maintenance, droits et protection (tâche 4) |
| `phone/net/SimCardItem.java`, `PhoneSims.java`, `phone/PhoneItem.java` (modifié), `dev/CarteSimGameTests.java`, `dev/OperateurDEssai.java` | Carte SIM (tâche 5) |
| `phone/net/PhoneNetwork.java`, `NetworkStatus.java`, `TrafficKind.java`, `TrafficHandle.java`, `RelayFailure.java`, `dev/RelaisGameTests.java` | Façade réseau (tâche 6) |
| `phone/PhoneStatePacket.java`, `PhoneActionPacket.java`, `PhoneService.java`, `PhoneNetView.java`, `bench/BenchNetwork.java` (modifiés ou créés), `dev/JoueurDeTest.java`, `dev/TelephoneReseauGameTests.java`, JUnit `phone/PhoneStatePacketTest.java` (modifié) | Téléphone en réseau, côté serveur (tâche 7) |
| `html/phone/index.html`, `phone.css`, `phone.js`, `phone/client/PhoneViewJson.java`, `PhoneBridgeMessages.java`, `PhoneFallbackScreen.java`, `PhoneFallbackLayout.java`, JUnit `phone/client/*Test.java` (modifiés) | Téléphone en réseau, côté client (tâche 8) |
| `phone/net/OperatorView.java`, `OperatorViews.java`, `OperatorActions.java`, `OperatorPanelPacket.java`, `OperatorActionPacket.java`, `OperatorPanels.java`, `OperatorTillMenu.java`, `OperatorDay.java`, `phone/net/client/OperatorPanelScreen.java`, `OperatorPanelLayout.java`, JUnit `phone/net/OperatorViewTest.java`, `phone/net/client/OperatorPanelLayoutTest.java`, `dev/PanneauOperateurGameTests.java` | Vue, règles et panneau Minecraft (tâche 9) |
| `html/phone/index.html`, `phone.css`, `phone.js` (appli « Antenne »), `phone/client/OperatorViewJson.java`, `AntennaBridgeMessages.java`, `PhoneWebScreen.java`, `PhoneClient.java`, `PhoneFallbackScreen.java` (modifiés ou créés), JUnit `phone/client/OperatorViewJsonTest.java`, `AntennaBridgeMessagesTest.java`, `dev/AppliAntenneGameTests.java` | Appli « Antenne » en HTML et repli (tâche 10) |
| `phone/net/OperatorCommands.java`, `dev/MaireTelephoneGameTests.java` | Maire et équipe (tâche 11) |
| `tools/gen_textures_t2.py`, textures, modèles, `dev/DemoVisuels.java` (scène `telephone_reseau`) | Visuels (tâche 12) |
| `dev/E2eTelephone.java`, `dev/E2eTelephoneServeur.java`, `dev/E2eClient.java`, `dev/TelephoneShots.java` (modes `signal` et `antenne`), `build.gradle` (propriétés) | Essais réels (tâche 13) |
| `docs/verifications/lot-t2.md`, `tasks/todo.md`, `wiki/data/telephone.json`, `tasks/lessons.md` | Preuve (tâche 14) |

`OneForAllSurvival.java` reçoit les abonnements de chaque tâche ; langues à chaque tâche.

---

### Task 1 : règles pures du réseau

**Files:**
- Create : `phone/net/SignalMath.java`, `AntennaLoad.java`, `SimNumbers.java`, `RelayPlanner.java`
- Test : `src/test/java/dev/oneforallsurvival/phone/net/SignalMathTest.java`, `AntennaLoadTest.java`, `SimNumbersTest.java`, `RelayPlannerTest.java`

**Interfaces:**
- Produces (aucune classe de Minecraft hors de `BlockPos`, aucune configuration lue : les valeurs arrivent en paramètres) :
  - `SignalMath` : `static int bars(double distance, int range)` (table des choix ; `range ≤ 0` → 0) ; `static double distance(BlockPos a, BlockPos b)` (centres des blocs, 3D).
  - `AntennaLoad` : `record Breakdown(int standby, int clients, int traffic) { int total(); }` (somme saturée à `Integer.MAX_VALUE`) ; `static Breakdown of(int standbyFe, int perSimFe, int activeCards, int openCalls, int callFe, int openFacetimes, int facetimeFe)`.
  - `SimNumbers` : `static String draw(RandomSource random, Predicate<String> taken)` (`"06"` + 8 chiffres, retire jusqu'à 1 000 fois, sinon `IllegalStateException`) ; `static String format(String number)` → `"06 12 34 56 78"` ; `static Optional<String> parse(String text)` (avec ou sans espaces, 10 chiffres ASCII commençant par `06`).
  - `RelayPlanner` : `record AntennaPoint(UUID id, String operatorId, ResourceKey<Level> dimension, BlockPos pos, boolean inService)` ; `record Endpoint(String operatorId, ResourceKey<Level> dimension, BlockPos pos)` ; `record Snapshot(List<AntennaPoint> antennas, Map<String, String> operatorNames, int range)` (un opérateur absent de `operatorNames` est supprimé) ; `sealed interface Result` avec `record Route(AntennaPoint caller, AntennaPoint callee)` et `record Failed(RelayFailure.Reason reason, String operatorName)` ; `static Result plan(Endpoint caller, Endpoint callee, Snapshot net)` ; `static Optional<AntennaPoint> best(Endpoint at, Snapshot net)` (meilleure antenne en service de son opérateur, à distance égale l'identifiant le plus petit) ; `static Optional<AntennaPoint> anyInService(ResourceKey<Level>, BlockPos, Snapshot)` (urgences).
  - `RelayFailure.Reason` (enum, dans `phone/net/RelayFailure.java`, la classe complète arrive à la tâche 6) : `CALLER_NO_SIGNAL, CALLER_OPERATOR_REMOVED, CALLEE_OPERATOR_REMOVED, CALLEE_ANTENNA_DOWN, CALLEE_NO_COVERAGE` ; les raisons qui tiennent aux cartes (`CALLER_AIRPLANE`, `CALLER_SIM_INACTIVE`, `CALLEE_SIM_INACTIVE`, `CALLEE_UNKNOWN`) sont ajoutées à la tâche 6.
  - Règle de `plan` : opérateur de l'appelant supprimé → `CALLER_OPERATOR_REMOVED` ; pas d'antenne en service de son opérateur à portée → `CALLER_NO_SIGNAL` ; opérateur de l'appelé supprimé → `CALLEE_OPERATOR_REMOVED` ; au moins une antenne de son opérateur à portée de l'appelé mais aucune en service → `CALLEE_ANTENNA_DOWN` (nom de cet opérateur) ; aucune à portée → `CALLEE_NO_COVERAGE` ; sinon `Route(best(caller), best(callee))` (la même antenne si les deux sont sous la même).

- [ ] **Step 1 : tests qui échouent**
  - `SignalMathTest` : pour `range = 270` : `bars(0) == 4`, `bars(67) == 4`, `bars(68) == 3`, `bars(134) == 3`, `bars(135) == 2`, `bars(202) == 2`, `bars(203) == 1`, `bars(270) == 1`, `bars(270.01) == 0` ; `bars(5, 0) == 0` ; `distance(BlockPos(0,0,0), BlockPos(3,4,0)) == 5.0`.
  - `AntennaLoadTest` : `of(40, 6, 10, 1, 9, 1, 24)` → `Breakdown(40, 60, 33)`, `total() == 133` ; zéro carte et zéro trafic → 40 ; `of(40, 6, Integer.MAX_VALUE, 0, 9, 0, 24).total() == Integer.MAX_VALUE`.
  - `SimNumbersTest` : 1 000 tirages tous de la forme `06\d{8}` et distincts avec un `taken` qui les retient ; `format("0612345678") == "06 12 34 56 78"` ; `parse("06 12 34 56 78")` et `parse("0612345678")` présents ; vides pour `"0712345678"`, `"06123"`, `"06１２３４５６７８"`, `"06 12 34 56 7a"` ; un `taken` toujours vrai lève.
  - `RelayPlannerTest` : `memeOperateurMemeAntenne` ; `deuxOperateursDeuxAntennes` (route A → B) ; `lAppelantSansSignal` ; `antenneDeLAppeleHorsTension` (raison et nom « Mairie ») ; `appeleHorsReseau` ; `operateurDeLAppeleSupprime` ; `operateurDeLAppelantSupprime` ; `autreDimensionPasDeSignal` ; `laMeilleureEstLaPlusProcheEnService` (une antenne hors service plus proche est ignorée) ; `urgenceParNimporteQuelOperateur`.
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*phone.net*'` ; attendu : échec de compilation.
- [ ] **Step 3 : implémenter** les quatre classes et l'énumération.
- [ ] **Step 4 : vérifier** — même commande, puis `./gradlew --offline test` ; attendu : tout vert, `McefIsolationTest` compris.
- [ ] **Step 5 : commit** — `feat(telephone): regles pures du reseau (signal, consommation, numeros, relais)`.

### Task 2 : registre des opérateurs, des cartes et des antennes

**Files:**
- Create : `phone/net/PhoneOperators.java`, `PhoneOperator.java`, `SimCard.java`, `SimRequest.java`, `AntennaRecord.java`
- Modify : `phone/PhoneConfig.java` (réglages ci-dessous), `OneForAllSurvival.java`
- Test : `src/test/java/dev/oneforallsurvival/phone/net/PhoneOperatorsTest.java`

**Interfaces:**
- Consumes : `SimNumbers` (tâche 1), `PowerSuppliers.validName` (règle de nom), `Coin`, `CoinMath`.
- Produces :
  - `record PhoneOperator(String id, String name, UUID founder, String founderName, long tariff, @Nullable Long nextTariff, EnumMap<Coin, Long> till, @Nullable UUID mainAntenna, long createdAt)`.
  - `record SimCard(UUID simId, String number, String operatorId, @Nullable UUID holder, String holderName, State state, UUID token, @Nullable UUID phoneId, long issuedAt)` avec `enum State { ACTIVE, SUSPENDED, DEACTIVATED }` ; `holder` nul : carte sans nom.
  - `record SimRequest(UUID id, String operatorId, UUID player, String playerName, long at)`.
  - `record AntennaRecord(UUID id, String operatorId, GlobalPos pos, long placedAt, boolean running, long seenGameTime, long feToday, long feYesterday, AntennaLoad.Breakdown last)`.
  - `PhoneOperators extends SavedData` (clé `oneforallsurvival_phone_operators`, sur l'overworld) : `static PhoneOperators get(MinecraftServer)` ; `Result found(UUID founder, String founderName, String name, long now)` (`enum Status { FOUNDED, BAD_NAME, NAME_TAKEN, ALREADY_HAS_ONE }`) ; `Optional<PhoneOperator> byId/byName/byFounder` ; `Collection<PhoneOperator> all()` ; `boolean remove(String id)` (le registre seul ; la suite de la suppression est à la tâche 11) ; `boolean setFounder(String id, UUID, String)` ; cartes : `SimCard issue(String operatorId, @Nullable UUID holder, String holderName, RandomSource, long now)`, `Optional<SimCard> card(UUID simId)`, `Optional<SimCard> cardByNumber(String number)`, `List<SimCard> cardsOf(String operatorId)`, `int activeCards(String operatorId)`, `boolean setState(UUID simId, SimCard.State)`, `UUID rotateToken(UUID simId, UUID phoneId, RandomSource)`, `void unbind(UUID simId)` ; numéros réservés pour toujours (`Set<String> usedNumbers`) ; demandes : `Optional<SimRequest> request(String operatorId, UUID player, String name, long now)` (vide si déjà demandé ou 32 atteint), `List<SimRequest> requestsOf(String)`, `Optional<SimRequest> takeRequest(UUID id)` ; antennes : `void putAntenna(AntennaRecord)`, `Optional<AntennaRecord> antenna(UUID id)`, `List<AntennaRecord> antennasOf(String operatorId)`, `Collection<AntennaRecord> antennas()`, `void removeAntenna(UUID id)` (réélit l'antenne principale), `void noteTick(UUID id, boolean running, long gameTime, int fe, AntennaLoad.Breakdown)` (en mémoire ; `setDirty()` au plus tous les 20 tics ou dès que `running` change), `void closeAntennaDay()` ; tarifs : `boolean setNextTariff(String id, long tariff)` (refusé au-dessus de `phoneTariffMax`), `long tariffCap()`, `Optional<Long> nextTariffCap()`, `void setNextTariffCap(long)`, `List<DayChange> applyNextTariffs()` (`record DayChange(String operatorId, long before, long after, boolean capped)`) ; caisse : `EnumMap<Coin, Long> till(String id)`, `deposit`, `withdraw`, `tillRoom` (signatures de `PowerSuppliers`) ; avis : `addNotice(UUID, Component)`, `takeNotices(UUID)` ; `save`/`load`.
  - `PhoneConfig` gagne : `antennaRange()` 270 (16 à 2 048), `antennaStandbyFe()` 40, `antennaPerSimFe()` 6, `antennaCallFe()` 9, `antennaFacetimeFe()` 24, `antennaMessageFe()` 100 (0 à 100 000 chacun), `antennaCapacity()` 40 000, `antennaMaxReceive()` 4 000, `antennaKeepsChunkLoaded()` vrai, `antennaStaleTicks()` 40 (2 à 1 200), `simRequestsMax()` 32, `phoneTariffMax()` 1 000, `phoneDefaultTariffCap()` 20, `signalRefreshTicks()` 20.

- [ ] **Step 1 : tests qui échouent** (`PhoneOperatorsTest`, NBT seulement, comme `PhoneDataTest` au lot T1) :
  - `allerRetourComplet` : deux opérateurs, cinq cartes (une sans nom, une suspendue, une désactivée, une logée dans un téléphone), deux demandes, trois antennes, tarifs du jour et du lendemain, caisse, avis : `load(save())` identique ;
  - `uneAncienneSauvegardeSansOperateurSeCharge` : `load(new CompoundTag())` donne un registre vide, plafond `phoneDefaultTariffCap` ;
  - `unNomPrisEstRefuseSansCasse` (« Mairie » puis « mairie ») ; `unSeulOperateurParJoueur` ;
  - `chaqueCarteAUnNumeroUnique` (200 émissions) ; `unNumeroNeRevientJamais` (carte désactivée, son numéro reste réservé) ;
  - `leJetonTourneALInsertion` : `rotateToken` rend un jeton différent de l'ancien et note le `phoneId` ;
  - `uneDemandeParJoueurEtParOperateur` ; `pasPlusDeTrenteDeuxDemandes` ;
  - `laPrincipaleEstReelue` : retirer l'antenne principale élit la plus ancienne restante.
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*PhoneOperatorsTest*'`.
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : vérifier** — `./gradlew --offline test` ; attendu : tout vert.
- [ ] **Step 5 : commit** — `feat(telephone): registre des operateurs, des cartes et des antennes`.

### Task 3 : l'antenne, la fondation de l'opérateur, la consommation et le service

**Files:**
- Create : `phone/net/AntennaBlock.java`, `AntennaBlockEntity.java`, `AntennaItem.java`, `AntennaChunks.java`, `data/oneforallsurvival/recipes/antenna.json`, `loot_tables/blocks/antenna.json`, `blockstates/antenna.json`, `models/block/antenna*.json`, `models/item/antenna.json`, `dev/AntenneGameTests.java` (lots `antenne_a`, `antenne_chunk`)
- Modify : `phone/PhoneRegistry.java` (bloc, objet, `ANTENNA_BE`, onglet), `OneForAllSurvival.java` (rappel de validation des tickets, tic), langues

**Interfaces:**
- Consumes : `ElectricMachineBlock`, `ElectricMachineBlockEntity` (`wantsToWork`, `energyPerTick`, `workTick`, `afterTick`, `state()`), `PhoneOperators`, `AntennaLoad`, `PhoneConfig`, `BenchCatalog`, modèle de `PowerStationBlock` (objet marqué, pose, `idleOwner`).
- Produces :
  - `AntennaBlock extends ElectricMachineBlock` : pas d'orientation ; `STATE` du lot 0 (voyant allumé en `RUNNING` seulement) ; mât de 4 × 16 × 4 px centré sur une semelle de 10 × 2 × 10 px : `noOcclusion()` et `getShape` exacte ; pose refusée sans joueur ou par un `FakePlayer` ; `getStateForPlacement` et `setPlacedBy` sur le modèle de `PowerStationBlock` (objet qui porte un opérateur existant : repris par un ayant droit, refusé sinon ; objet neuf : l'opérateur du poseur s'il en a un, sinon antenne à fonder) ; `getDrops` marque l'objet (`AntennaItem.markOperator`) ; `onRemove` retire l'enregistrement et rend le ticket.
  - `static Optional<PhoneOperators.Result> found(ServerPlayer actor, BlockPos pos, String name)` (sur le modèle de `PowerStationBlock.found`) : fonde l'opérateur et inscrit l'antenne comme principale.
  - `AntennaBlockEntity extends ElectricMachineBlockEntity` : capacité `antennaCapacity`, réception `antennaMaxReceive` ; `@Nullable UUID antennaId()`, `@Nullable String operatorId()` ; `wantsToWork()` = opérateur existant **et** antenne inscrite ; `energyPerTick()` = `AntennaLoad.of(...)`.`total()` avec les cartes actives de l'opérateur et le trafic ouvert (`PhoneNetwork`, tâche 6 ; d'ici là zéro) ; `afterTick` → `PhoneOperators.noteTick(id, state == RUNNING, gameTime, fe payés, breakdown)` ; `workTick` ne fait rien.
  - `AntennaChunks` : `static void hold(ServerLevel, BlockPos)` / `release(ServerLevel, BlockPos)` (ticket « qui tique » sur le chunk de l'antenne, seulement si `antennaKeepsChunkLoaded`) ; `static void validate(...)` (rappel de validation inscrit pour le mod : garde un ticket seulement si un `AntennaRecord` existe à cette position, sinon le rend).
  - `static boolean inService(AntennaRecord, long gameTime)` dans `AntennaBlockEntity` : `running && gameTime - seenGameTime ≤ antennaStaleTicks`.
  - Recette (choix de l'auteur, ingrédients vanilla) : `[" L ","CRC","IBI"]`, `L` paratonnerre, `C` lingot de cuivre, `R` bloc de redstone, `I` lingot de fer, `B` barreaux de fer ; absente de `table_only`.

- [ ] **Step 1 : lire l'API des tickets de Forge 47.4.28** dans le jar du cache de Gradle (`javap`) : `ForgeChunkManager.forceChunk` (ou son nom exact), la forme du rappel de validation (`LoadingValidationCallback`, `TicketHelper`) et le moment où il s'inscrit ; noter les signatures relevées dans le rapport de la tâche. Rien n'est écrit sur une signature non lue.
- [ ] **Step 2 : GameTests qui échouent** (source `DevBlocks.testSource()` raccordée, lot `antenne_a`) :
  - `lAntenneNeSeFabriqueQuALEtabli` (la recette n'est pas dans le `RecipeManager` après le catalogue, elle est dans `BenchCatalog.current()` ; un citoyen ne la commande pas) ;
  - `poserPuisNommerFondeLOperateur` (premier panneau : nom ; « Mairie » pris refusé ; un second opérateur du même joueur refusé ; la seconde antenne du fondateur rejoint le sien) ;
  - `sansNomLAntenneNeConsommeRien` (état `STOPPED`, réserve intacte) ;
  - `laVeilleCoute40` (fondée, sans carte : la réserve baisse de 40 FE par tic, état `RUNNING`) ;
  - `chaqueCarteActiveCoute6` (3 cartes actives, 1 suspendue : 58 FE par tic ; `last` = `Breakdown(40, 18, 0)`) ;
  - `sansCourantHorsService` (source retirée : `NO_POWER`, voyant éteint, `inService` faux après 40 tics) ;
  - `uneAntenneDemonteeGardeSonOperateur` (casse par le fondateur : objet marqué ; reposée, même opérateur ; reposée par un autre joueur sans droit : refusée) ;
  - `unJoueurQuiPartAvecLAntenneNeLaissePasDeFantome` (casse, objet dans l'inventaire, déconnexion : aucun `AntennaRecord` à l'ancienne position, opérateur intact, reconnexion puis pose : il la reprend).
- [ ] **Step 3 : GameTests de chargement** (lot `antenne_chunk`, antenne en bord de chunk et joueur de l'autre côté de la frontière, leçons du 2026-11-11 et du 2026-11-19) :
  - `uneAntenneLoinDeTousResteEnService` (aucun joueur près d'elle : son chunk est forcé, `seenGameTime` avance, `inService` vrai) ;
  - `sansChunkForceUneAntenneDechargeeEstHorsTension` (`antennaKeepsChunkLoaded` faux le temps du test : chunk déchargé, `inService` faux après 40 tics) ;
  - `lesTicketsDUneAntenneDisparueSontRendus` (enregistrement retiré à la main, `AntennaChunks.validate` rend le ticket ; casse normale : ticket rendu aussitôt).
- [ ] **Step 4 : vérifier qu'ils échouent** (compiler, puis procédure du verrou).
- [ ] **Step 5 : implémenter** (modèles provisoires du même nom, texture violette ; les finals à la tâche 12).
- [ ] **Step 6 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed ».
- [ ] **Step 7 : commit** — `feat(telephone): antenne, fondation de l'operateur et consommation`.

### Task 4 : outil de maintenance, droits et protection de l'antenne

**Files:**
- Create : `repair/MaintenanceToolItem.java`, `data/oneforallsurvival/recipes/maintenance_tool.json`, `models/item/maintenance_tool.json` (texture provisoire ; la finale à la tâche 12), `data/oneforallsurvival/tags/items/maintenance_tools.json` (`oneforallsurvival:maintenance_tool`), `phone/net/AntennaRules.java`, `dev/AntenneDroitsGameTests.java` (lots `antenne_droits`, `outil_maintenance`)
- Modify : `repair/RepairRegistry.java` (objet `maintenance_tool`, `stacksTo(1)`, ajouté à `tabItems()` après `WELDER`), `AntennaBlock.java` (`use`, `getDestroyProgress`, `attack`, `onBreak`), `PhoneRegistry.java` (`strength(-1, 3600000)`, `PushReaction.BLOCK`), `data/create/tags/blocks/non_movable.json`, `data/minecraft/tags/blocks/wither_immune.json`, `dragon_immune.json`, `economy/town/TownBlockRules.java` (`engineerMayMove` exige l'outil), `dev/PackGameTests.java`, les GameTests du trésor et du tableau qui déplacent par un ingénieur (outil donné), `fr_fr.json` (« Outil de maintenance »), `en_us.json` (« Maintenance tool »), infobulle (« Réservé à l'ingénieur : antennes, trésor, tableau »)

**Interfaces:**
- Consumes : `RoleGate.isMayor`, `RoleGate.isEngineer`, `hasPermissions(2)`, `PhoneOperators`, `BenchCatalog.current()`, `BenchCatalog.isTableOnly`, `BenchConfig.managedMods()` (lus avant d'écrire, avec `BenchRegistry` et `RepairRegistry`).
- Produces :
  - `RepairRegistry.MAINTENANCE_TOOL` (`MaintenanceToolItem extends Item`, sans usure ni action propre : il ne fait qu'ouvrir des droits) ; recette des choix de l'auteur, hors de `table_only` : **un objet de plus au catalogue de l'établi d'ingénieur**, retiré de la table par `BenchCatalog`.
  - `AntennaRules` : `static boolean hasMaintenanceTool(Player)` (une des deux mains porte un objet du tag `oneforallsurvival:maintenance_tools`) ; `static boolean mayManage(Player, @Nullable UUID founder)` (fondateur, maire, équipe, **ingénieur qui tient l'outil** : le rôle et l'outil, jamais l'outil seul ; `FakePlayer` jamais ; côté client vrai, le serveur tranche) ; `static boolean mayManageCards(Player, PhoneOperator)` (fondateur seul) ; `static boolean mayTakeTill(Player, PhoneOperator)` (fondateur, équipe) ; `static boolean maySetTariff(Player, PhoneOperator)` (fondateur, maire, équipe).
  - `TownBlockRules.engineerMayMove(Player)` : rôle d'ingénieur **et** `AntennaRules.hasMaintenanceTool`.
  - `AntennaBlock.use` : testeur en main principale → laissé au testeur (rapport de machine, comme le lot 2) ; sinon ayant droit → panneau (tâche 9 ; d'ici là un message « panneau à venir » de développement, retiré à la tâche 9) ; sinon « Antenne de <opérateur> ».

- [ ] **Step 1 : GameTests de l'outil qui échouent** (lot `outil_maintenance`) :
  - `lOutilNeSeFabriqueQuALEtabli` (sa recette n'est plus dans le `RecipeManager` après le catalogue, elle est dans `BenchCatalog.current()` ; `isTableOnly` faux ; un citoyen ne la commande pas à l'établi, un ingénieur si) ;
  - `lOutilEstDansLeTag` (et le testeur n'y est pas) ;
  - `sansLOutilLIngenieurNeRegleNiNeDeplaceLAntenne` (rôle d'ingénieur, mains vides ou testeur : pas de panneau, progression de casse 0, `BreakEvent` annulé) ;
  - `avecLOutilEnMainLIngenieurRegleEtDeplace` (outil en main principale puis en main secondaire : panneau ouvert, casse permise, objet marqué, reposé ailleurs : même opérateur) ;
  - `unOutilVoleNeDonneRien` (un citoyen qui tient l'outil : ni panneau, ni casse) ;
  - `deplacerLeTresorExigeLOutil` (ingénieur sans l'outil refusé, avec l'outil permis ; `TownBlockRules.engineerMayMove`, trésor et tableau).
- [ ] **Step 2 : GameTests des droits qui échouent** (lot `antenne_droits`) :
  - `unSansDroitNeTouchePasALAntenne` (citoyen : ni ouvrir, ni casser, progression 0, `BreakEvent` annulé ; fondateur, maire, équipe, sans outil : oui) ;
  - `unFauxJoueurNeFaitRien` (`FakePlayer`, même avec l'outil : ni pose, ni ouverture, ni casse) ;
  - `unPistonNePoussePasLAntenne` (témoin : une pierre poussée) ;
  - `uneExplosionNeCassePasLAntenne` (puissance 4 au contact : bloc présent, enregistrement intact) ;
  - `laFormeDeLAntenne` (`getShape` égale aux boîtes écrites en clair dans le test, pour chaque `STATE`).
- [ ] **Step 3 : tests du pack** dans `PackGameTests` (seulement avec `-PwithPack`, un lot chacun, témoin à chaque fois, modèle de `unDeployeurNeCassePasLePoste`, `unePerceuseNeCassePasUnCompteurJumele`, `unEnginNEmportePasUnCompteurJumele`) : `unDeployeurNeCassePasLAntenne`, `unePerceuseNeCassePasLAntenne`, `unEnginNEmportePasLAntenne`, `lAntenneEstDansLeTagNonMovable`.
- [ ] **Step 4 : vérifier qu'ils échouent** (compiler, puis procédure du verrou).
- [ ] **Step 5 : implémenter** ; mettre à jour les GameTests existants du trésor et du tableau qui déplacent un bloc de la ville par un ingénieur (lui donner l'outil de maintenance ; leurs assertions ne changent pas) et l'étape `E2eTresor` si elle le fait (lancée avant d'être dite faite, leçon du 2026-11-19).
- [ ] **Step 6 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed ». Mutations : retirer l'antenne du tag `non_movable` fait rougir `lAntenneEstDansLeTagNonMovable` ; accepter l'outil sans le rôle fait rougir `unOutilVoleNeDonneRien` (restaurer).
- [ ] **Step 7 : commit** — `feat(telephone): outil de maintenance et protection de l'antenne`.

### Task 5 : la carte SIM

**Files:**
- Create : `phone/net/SimCardItem.java`, `PhoneSims.java`, `models/item/sim_card.json`, `dev/CarteSimGameTests.java` (lots `sim_a`, `sim_copie`), `dev/OperateurDEssai.java` (`fonder(helper, joueur, nom)` : antenne alimentée posée et nommée ; `emettre(helper, operateur, titulaire)` : carte dans l'inventaire du fondateur)
- Modify : `phone/PhoneItem.java` (NBT `sim`, `getShareTag`), `phone/PhoneRegistry.java` (objet `sim_card`, `stacksTo(1)`), langues (infobulle)

**Interfaces:**
- Consumes : `PhoneOperators` (tâche 2), `PhoneItem.phoneId/ensurePhoneId` (lot T1).
- Produces :
  - `SimCardItem extends Item` : NBT `sim_id` (UUID), `sim_token` (UUID), copies d'affichage `sim_number`, `sim_operator`, `sim_holder` (jamais lues pour décider) ; `static Optional<UUID> simId(ItemStack)` ; `static ItemStack create(SimCard, String operatorName)` ; `getShareTag` retire `sim_token` ; infobulle : numéro formaté, opérateur, titulaire ou « SIM sans nom ».
  - `PhoneItem` : `static ItemStack sim(ItemStack phone)` (copie de la carte logée, ou vide) ; `static void setSim(ItemStack phone, ItemStack sim)` (serveur) ; `getShareTag` retire le jeton de la carte logée.
  - `PhoneSims` : `enum Validity { NONE, UNKNOWN, ACTIVE, SUSPENDED, DEACTIVATED }` ; `static Validity check(MinecraftServer, ItemStack phone)` (carte logée : `sim_id` connu, jeton égal, `phoneId` égal au téléphone ; sinon `UNKNOWN`) ; `static Optional<SimCard> line(MinecraftServer, ItemStack phone)` (`ACTIVE` ou `SUSPENDED` seulement) ; `enum Insert { DONE, NOT_A_SIM, UNKNOWN_CARD, NO_PHONE }` ; `static Insert insert(ServerPlayer, ItemStack phone, int slot)` (relit la case ; carte au registre seulement — un `sim_id` inconnu est refusé ; nouveau jeton par `rotateToken`, écrit sur la pile ; l'ancienne carte du téléphone revient dans la case) ; `enum Remove { DONE, NO_SIM, INVENTORY_FULL }` ; `static Remove remove(ServerPlayer, ItemStack phone)` (`unbind`) ; `static List<SimChoice> choices(ServerPlayer)` (`record SimChoice(int slot, String number, String operator, Validity validity)`, 41 au plus).
  - Émission (appelée par le panneau à la tâche 9) : `static Optional<ItemStack> issueTo(ServerPlayer founder, PhoneOperator op, @Nullable UUID holder, String holderName, long now)` — vide (rien émis) si l'inventaire du fondateur est plein.

- [ ] **Step 1 : GameTests qui échouent** (lot `sim_a`) :
  - `aucuneRecetteNeDonneDeCarte` (aucune recette du `RecipeManager` ni de `BenchCatalog` n'a la carte pour résultat) ;
  - `uneCarteEmiseArriveChezLeFondateur` (au nom du demandeur ; inventaire plein : rien émis, aucun numéro réservé) ;
  - `glisserLaCarteDonneLaLigne` (`check == ACTIVE`, numéro, `phoneId` noté) ; `retirerLaCarteLaRendALInventaire` (inventaire plein : refusé, la carte reste dans le téléphone) ;
  - `changerDeCarteRendLAncienneASaCase` ;
  - `leNumeroSuitLaCarte` (carte retirée du téléphone A et glissée dans le téléphone B : même numéro, `phoneId` de B ; A : « Aucune carte SIM ») ;
  - `uneCarteSuspendueResteSuspendue` (`check == SUSPENDED` ; réactivée : `ACTIVE`) ;
  - `unTelephoneDuLotT1SansCarte` (pile de T1 sans NBT `sim` : `NONE`).
- [ ] **Step 2 : GameTests de la copie** (lot `sim_copie`) :
  - `uneCopieDeLaCarteNeDonnePasDeuxLignes` (deux piles du même NBT ; la première glissée dans A, la copie dans B : B `ACTIVE`, A `UNKNOWN` ; recompter : `PhoneNetwork` ne compte qu'une ligne, à la tâche 6) ;
  - `uneCopieDuTelephoneAvecSaCarte` (deux piles de téléphone au même `phone_id`, carte logée : une seule ligne, portée par ce `phone_id`, comme le partage du lot T1) ;
  - `uneCarteForgeeEstRefusee` (`sim_id` inventé, puis `sim_id` réel avec un faux jeton : `insert == UNKNOWN_CARD`, ou `check == UNKNOWN` si déjà logée par NBT) ;
  - `leJetonNeVaPasAuClient` (`getShareTag` de la carte et du téléphone qui la loge : pas de `sim_token`, `sim_id` présent).
- [ ] **Step 3 : vérifier qu'ils échouent** (procédure du verrou).
- [ ] **Step 4 : implémenter.**
- [ ] **Step 5 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed ».
- [ ] **Step 6 : commit** — `feat(telephone): carte SIM, insertion et jeton contre les copies`.

### Task 6 : façade du réseau, relais et trafic

**Files:**
- Create : `phone/net/PhoneNetwork.java`, `NetworkStatus.java`, `TrafficKind.java`, `TrafficHandle.java`, `dev/RelaisGameTests.java` (lots `relais_a`, `relais_trafic`)
- Modify : `phone/net/RelayFailure.java` (classe complète), `AntennaBlockEntity.java` (trafic dans `energyPerTick`), langues

**Interfaces:**
- Consumes : `RelayPlanner`, `SignalMath`, `PhoneSims`, `PhoneOperators`, `AntennaBlockEntity.inService`, `PhoneConfig`.
- Produces (l'interface du lot T3) :
  - `record NetworkStatus(PhoneSims.Validity sim, String number, String holderName, String operatorName, int bars, boolean airplane)` ; `static NetworkStatus PhoneNetwork.status(ServerPlayer holder, ItemStack phone, boolean airplane)` (barres 0 en mode avion, sans carte `ACTIVE` ou sans antenne en service ; carte `SUSPENDED` : opérateur affiché, barres 0).
  - `RelayFailure` : `Reason` complétée de `CALLER_AIRPLANE`, `CALLER_SIM_INACTIVE`, `CALLEE_SIM_INACTIVE`, `CALLEE_UNKNOWN` ; `record RelayFailure(Reason reason, String operatorName)` ; `Component describe()` (clés `message.oneforallsurvival.relay.<raison>`, « Injoignable · Antenne de %s hors tension » pour `CALLEE_ANTENNA_DOWN`, « Injoignable · Hors réseau », « Carte suspendue », « Numéro inconnu », « Pas de réseau », « Mode avion »).
  - `static Either<RelayPlanner.Route, RelayFailure> PhoneNetwork.route(MinecraftServer, ServerPlayer caller, ItemStack callerPhone, boolean airplane, String calleeNumber, GlobalPos calleePos)` (cartes vérifiées d'abord, puis `RelayPlanner.plan` sur un `Snapshot` construit par `snapshot(server, gameTime)`). La position de l'appelé est donnée : la retrouver est le travail du lot T3.
  - `static Optional<SimCard> PhoneNetwork.lineOf(MinecraftServer, String number)` ; `static Optional<RelayPlanner.AntennaPoint> emergencyAntenna(MinecraftServer, GlobalPos)`.
  - `enum TrafficKind { CALL, FACETIME }` ; `static TrafficHandle openTraffic(MinecraftServer, RelayPlanner.Route, TrafficKind)` (chaque antenne du trajet compte ce trafic dans sa consommation tant que la poignée est ouverte ; transitoire, rien sur le disque) ; `TrafficHandle.close()` (idempotent) ; `static void messageSent(MinecraftServer, RelayPlanner.Route)` (`antennaMessageFe` retiré une fois de la réserve de chaque antenne du trajet).
  - `record RelayState(String operatorId, String name, int antennas, int inService)` ; `static List<RelayState> relayStates(MinecraftServer)` (pour le panneau).

- [ ] **Step 1 : GameTests qui échouent** (lot `relais_a`, deux opérateurs « Mairie » et « Lagon » fondés par `OperateurDEssai`, deux joueurs d'essai, positions remises par `teleportTo` après l'entrée : leçon du 2026-11-14) :
  - `sansAntenneAlimenteeLeSignalEstNul` ; `leSignalBaisseAvecLaDistance` (0, 100, 200 et 300 blocs : 4, 3, 2, 0 à la portée de 270) ;
  - `unTelephoneNeParleQuAuxAntennesDeSonOperateur` (sous une antenne de Lagon seule, une carte de Mairie : 0 barre) ;
  - `unAppelEntreDeuxOperateursPasseQuandLesDeuxSontAlimentees` (route Mairie → Lagon) ;
  - `ilEchoueSiLAntenneDeLAppeleEstHorsTension` (source de Lagon retirée : `CALLEE_ANTENNA_DOWN`, `describe()` = « Injoignable · Antenne de Lagon hors tension » lu par `getString()` côté serveur en `fr_fr`) ;
  - `unOperateurSupprimeNeRelaiePlus` (registre : opérateur retiré → `CALLEE_OPERATOR_REMOVED` ; antenne `STOPPED`) ;
  - `uneCarteSuspendueNAppellePlus` (`CALLER_SIM_INACTIVE`) ;
  - `leModeAvionCoupeTout` (barres 0, `CALLER_AIRPLANE`).
- [ ] **Step 2 : GameTests du trafic** (lot `relais_trafic`) :
  - `uneCarteCopieeNeCompteQuUneFois` (`lineOf(numéro)` rend le seul téléphone dont le jeton est bon ; la consommation « clients » ne compte la carte qu'une fois) ;
  - `laConsommationSuitVeilleCartesEtTrafic` (veille seule 40 ; 2 cartes : 52 ; un appel ouvert : 61 sur chaque antenne du trajet ; un FaceTime ouvert en plus : 85 ; poignées fermées : 52) ;
  - `unMessageCouteUneFois` (100 FE de moins sur chaque antenne du trajet, une seule fois) ;
  - `uneUrgencePasseParNimporteQuelleAntenne` (`emergencyAntenna` sans carte) ;
  - `lEtatDuRelais` (`relayStates` : Mairie 2/2, Lagon 1/0 après coupure).
- [ ] **Step 3 : vérifier qu'ils échouent** (procédure du verrou).
- [ ] **Step 4 : implémenter.**
- [ ] **Step 5 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed ».
- [ ] **Step 6 : commit** — `feat(telephone): facade du reseau, relais entre operateurs et trafic`.

### Task 7 : le téléphone en réseau, côté serveur

**Files:**
- Create : `phone/PhoneNetView.java`, `dev/TelephoneReseauGameTests.java` (lots `telephone_reseau`, `telephone_sim`)
- Modify : `phone/PhoneStatePacket.java`, `PhoneActionPacket.java`, `PhoneService.java`, `bench/BenchNetwork.java` (`PROTOCOL = "11"`), `dev/JoueurDeTest.java`, JUnit `phone/PhoneStatePacketTest.java`, langues

**Interfaces:**
- Consumes : `PhoneNetwork.status`, `PhoneSims.insert/remove/choices`, `PhoneOperators.request`, `PhoneData.airplane` (lot T1).
- Produces :
  - `record PhoneNetView(PhoneSims.Validity sim, String operator, int bars, String number, String holder, boolean founderApp, List<PhoneSims.SimChoice> choices, List<OperatorChoice> requestable)` avec `record OperatorChoice(String id, String name, int bars)` ; `static PhoneNetView NONE` ; textes bornés à 32, `choices` à 41, `requestable` à 16 ; `encode`/`decode`.
  - `PhoneStatePacket` gagne le dernier champ `PhoneNetView net`. Hors `UNLOCKED` : seuls `sim`, `operator` et `bars` ; `number`, `holder` vides, `founderApp` faux, listes vides. `Result` gagne `SIM_INSERTED, SIM_REMOVED, SIM_UNKNOWN, INVENTORY_FULL, SIM_REQUESTED, ALREADY_REQUESTED, NO_NETWORK`.
  - `PhoneActionPacket.Action` gagne `INSERT_SIM` (`first` = case, de `"0"` à `"40"`), `REMOVE_SIM`, `REQUEST_SIM` (`first` = identifiant d'opérateur, 12 caractères hexadécimaux), `OPEN_OPERATOR_APP` ; tous en `UNLOCKED` seulement ; `REQUEST_SIM` et `OPEN_OPERATOR_APP` refusés en mode avion (`NO_NETWORK`).
  - `PhoneService` : la vue réseau est calculée à l'ouverture, après chaque geste et toutes les `signalRefreshTicks` (`UPDATE` seulement si elle a changé) ; `OPEN_OPERATOR_APP` appelle `OperatorPanels.openFromPhone` (tâche 9 ; d'ici là `NO_NETWORK`).
  - `JoueurDeTest.etatsTelephoneRecus()` inchangé, décode le nouveau champ.

- [ ] **Step 1 : tests qui échouent** :
  - `PhoneStatePacketTest` : `allerRetour` avec une vue réseau pleine (41 cartes, 16 opérateurs) ; `uneVueTropLongueNeSeLitPas` (42 cartes : lève au décodage) ; `uneVueVerrouilleeNePorteNiNumeroNiTitulaire`.
  - Lot `telephone_reseau` : `unTelephoneDuLotT1MontreAucuneCarte` (`sim == NONE`, barres 0) ; `leSignalSAfficheALOuverture` (3 barres à 100 blocs) ; `leSignalSuitLeJoueur` (téléporté à 300 blocs : un `UPDATE` à 0 dans les 20 tics, jamais deux fois la même vue) ; `verrouilleOnVoitLOperateurEtLesBarresSeulement` ; `leModeAvionPrendEffet` (`AIRPLANE` vrai : barres 0 au même `UPDATE`) ; `laCoupureDeLAntenneSeVoit` (source retirée : 0 barre après au plus 60 tics) ; `lAppliAntenneNeSeVoitQueDuFondateur`.
  - Lot `telephone_sim` : `glisserParLeTelephone` (`INSERT_SIM` de la case 5 : `SIM_INSERTED`, vue `ACTIVE`) ; `uneCaseSansCarteNeFaitRien` (`SIM_UNKNOWN`, rien ne bouge) ; `retirerParLeTelephone` ; `demanderUneCarte` (`REQUEST_SIM` : demande inscrite, fondateur prévenu ; seconde fois : `ALREADY_REQUESTED`) ; `uneSessionPerimeeNeGlissePasDeCarte` ; `verrouilleAucunGesteDeCarte`.
- [ ] **Step 2 : vérifier qu'ils échouent** (JUnit, puis GameTests par la procédure du verrou).
- [ ] **Step 3 : implémenter.** Monter le protocole d'un cran (une seule fois pour le lot).
- [ ] **Step 4 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed », les GameTests du téléphone du lot T1 compris.
- [ ] **Step 5 : commit** — `feat(telephone): vue reseau, carte SIM et mode avion cote serveur`.

### Task 8 : le téléphone en réseau, côté client (pages et repli)

**Files:**
- Modify : `html/phone/index.html`, `phone.css`, `phone.js`, `phone/client/PhoneViewJson.java`, `PhoneBridgeMessages.java`, `PhoneFallbackScreen.java`, `PhoneFallbackLayout.java`, `dev/TelephoneShots.java` (clés permises du témoin du pont : `slot`, `op`), langues
- Test : `phone/client/PhoneViewJsonTest.java`, `PhoneBridgeMessagesTest.java`, `PhoneFallbackLayoutTest.java` (modifiés)

**Interfaces:**
- Consumes : `PhoneNetView` (tâche 7), contrat page ↔ Java du lot T1.
- Produces (contrat, ajouts) :
  - Java → page : la vue gagne `net: {sim, operator, bars, number, holder, founderApp, choices: [{slot, number, operator, validity}], requestable: [{id, name, bars}]}`.
  - Page → Java : `{"a":"insertSim","slot":N}` (entier de 0 à 40) ; `{"a":"removeSim"}` ; `{"a":"requestSim","op":"<8 hexadécimaux>"}` ; `{"a":"antennaApp"}`. `PhoneBridgeMessages` refuse tout autre type ou valeur.
  - Rectangles de `layout` ajoutés : `row-sim`, `sim-choice-<slot>`, `sim-remove`, `sim-request`, `request-<id>`, `app-antenna`, `status-bars`.
  - Page : barre d'état avec nom de l'opérateur (ou « Pas de SIM », « SIM non reconnue », « SIM suspendue ») et quatre barres (pleines ou vides) ; « Mode avion » remplace les barres ; Réglages : « Carte SIM » (numéro formaté, titulaire ou « SIM sans nom ») ouvre une feuille (cartes de l'inventaire, « Retirer », « Demander une carte » et sa liste) ; « Opérateur » (nom) ; « Abonnement » (tarif du jour, « prélèvement à venir ») ; icône « Antenne » sur l'accueil si `founderApp` (elle envoie `antennaApp` ; l'appli elle-même arrive à la tâche 10). Tout texte par `textContent`. Mode `#mock` étendu (deux cartes, deux opérateurs) ; depuis l'assistant de première configuration, `#mock` s'ouvre sur un téléphone neuf (Bonjour) : les écrans déverrouillés se rejoignent par `phoneMock.locked()` puis le code `1234`.
  - Écran de repli : mêmes informations ; boutons « Carte SIM », une ligne par carte de l'inventaire, « Retirer », « Demander » et la liste, « Antenne » (qui envoie le même geste ; l'écran ouvert en retour arrive à la tâche 10).

- [ ] **Step 1 : tests qui échouent** :
  - `PhoneBridgeMessagesTest` : `{"a":"insertSim","slot":5}` → `INSERT_SIM`, `first == "5"` ; vides pour `slot` 41, -1, `"5"`, 5.5 ; `{"a":"requestSim","op":"3f2a9c1b"}` → `REQUEST_SIM` ; vides pour `op` `"3F2A9C1B"`, `"3f2a9c1"`, `"../x"` ; `{"a":"antennaApp"}` → `OPEN_OPERATOR_APP`.
  - `PhoneViewJsonTest` : `net` présent ; opérateur `"</script>\u2028"` relu à l'identique après `WebScript.call` puis `JsonParser` (séquence écrite par script Python, leçon du 2026-11-20) ; une vue verrouillée sans `number` ni `holder`.
  - `PhoneFallbackLayoutTest` : la feuille « Carte SIM » avec 41 cartes et 16 opérateurs tient en 320 × 240 (défilement), nom d'opérateur de 24 caractères coupé « … ».
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*phone.client*'`.
- [ ] **Step 3 : implémenter** (pages, puis Java).
- [ ] **Step 4 : vérifier hors du jeu** — copie du dossier `html/` sans la ligne CSP, servie en local (leçon du 2026-11-20), `phone/index.html#mock` à 390 × 844 (dans un `iframe` de 390 × 844, leçon du 2026-11-20 ; `#mock` s'ouvre sur l'assistant : `phoneMock.locked()` puis le code `1234` pour l'écran verrouillé et les Réglages, l'assistant lui-même pour l'étape « Carte SIM » s'il en a une) : capturer et regarder barre d'état à 4, 2 et 0 barres, « Mode avion », « SIM non reconnue », feuille « Carte SIM », liste des demandes, icône « Antenne ».
- [ ] **Step 5 : vérifier** — `./gradlew --offline test runGameTestServer` ; attendu : tout vert, `WebResourcesTest` et `McefIsolationTest` compris.
- [ ] **Step 6 : vérifier en jeu** (procédure du verrou, second écran) — `runClient -PwithPack -PwithMcef`, puis `runClient -PwithPack` (repli) : antenne posée, fondée, carte émise et glissée par les vrais clics de la feuille ; barres, mode avion, retrait ; clavier compris (leçon du 2026-11-20) ; chaque capture regardée.
- [ ] **Step 7 : commit** — `feat(telephone): carte SIM, operateur et signal dans les pages et le repli`.

### Task 9 : vue de l'opérateur, règles, panneau Minecraft et tarif au lendemain

**Files:**
- Create : `phone/net/OperatorView.java`, `OperatorViews.java`, `OperatorActions.java`, `OperatorPanelPacket.java`, `OperatorActionPacket.java`, `OperatorPanels.java`, `OperatorTillMenu.java`, `OperatorDay.java`, `phone/net/client/OperatorPanelScreen.java`, `OperatorPanelLayout.java`, `dev/PanneauOperateurGameTests.java` (lots `panneau_a`, `panneau_cartes`, `panneau_vue`, `panneau_tarif`)
- Test : `src/test/java/dev/oneforallsurvival/phone/net/OperatorViewTest.java`, `phone/net/client/OperatorPanelLayoutTest.java`
- Modify : `bench/BenchNetwork.java` (`ID_OPERATOR_PANEL = 28` vers le client, `ID_OPERATOR_ACTION = 29` vers le serveur ; protocole déjà à `"11"`), `AntennaBlock.java` (`use` → panneau), `phone/PhoneService.java` (`OPEN_OPERATOR_APP`), `PhoneRegistry.java` (menu de la caisse), `economy/MoneyStorageRules.java` si la caisse doit y être déclarée (feature 84), `dev/JoueurDeTest.java` (`panneauxOperateurRecus()`), `OneForAllSurvival.java` (`DailyDebit.registerIssuer("phone", new OperatorDay())`, avis à la connexion), langues

**Interfaces:**
- Consumes : `AntennaRules`, `PhoneSims.issueTo`, `PhoneOperators`, `PhoneNetwork.relayStates`, `CoinAccountMenu`, `PowerTillStore` (modèle de `OperatorTillMenu`), `DailyDebit.DayIssuer`, `CoinMath.format`.
- Produces (la vue et les règles sont **les seules** : le panneau Minecraft, l'appli HTML et le repli de la tâche 10 ne font que montrer `OperatorView` et envoyer `OperatorActionPacket`) :
  - `record OperatorView(Source source, BlockPos pos, int session, String operatorId, String name, Rights rights, long tariff, @Nullable Long nextTariff, long cap, String tillText, List<AntennaLine> antennas, int cardPage, int cardPages, int cardTotal, List<CardLine> cards, List<RequestLine> requests, List<PhoneNetwork.RelayState> relay)` avec `enum Source { BLOCK, PHONE }`, `record Rights(boolean cards, boolean tariff, boolean till, boolean name)`, `record AntennaLine(UUID id, String dimension, BlockPos pos, MachineState state, AntennaLoad.Breakdown load, long feToday, long feYesterday, boolean main, boolean overReceive)`, `record CardLine(UUID simId, String number, String holderName, SimCard.State state)`, `record RequestLine(UUID id, String playerName, long at)` ; bornes : 32 antennes, 32 cartes, 32 demandes, 32 opérateurs au relais, textes à 32 ; `encode`/`decode` (lève au-delà). Une carte n'y figure que par `sim_id`, jamais par son jeton, et seulement si elle est de cet opérateur. (`RelayState` est celui de `PhoneNetwork`, tâche 6.)
  - `OperatorViews.build(ServerPlayer viewer, PhoneOperators, @Nullable PhoneOperator, Source, BlockPos, int session, int page)` : seule fabrique de la vue ; droits calculés par `AntennaRules` (`till` faux pour la source `PHONE` et hors de l'antenne principale ; `name` vrai seulement pour une antenne à fonder, sur le bloc).
  - `OperatorPanelPacket(OperatorView view)` (serveur → client) et `OperatorActionPacket(int session, Action action, String arg, long value)` (client → serveur) : `NAME`, `SET_TARIFF`, `ISSUE` (`arg` = demande), `REFUSE`, `ISSUE_ANONYMOUS`, `SUSPEND` (`arg` = `sim_id`), `REACTIVATE`, `SET_MAIN` (`arg` = antenne), `PAGE`, `OPEN_TILL`, `CLOSE`.
  - `OperatorActions.apply(ServerPlayer, OperatorActionPacket)` : **seul jeu de règles** ; rejoue tout : panneau ouvert par ce joueur sous ce numéro (`OperatorPanels`), antenne à moins de 8 blocs pour la source `BLOCK`, session du téléphone ouverte, déverrouillée et toujours valable (carte `ACTIVE` de l'opérateur, une barre au moins, pas de mode avion) pour `PHONE`, carte ou demande **de cet opérateur**, droits de `AntennaRules` ; `NAME` et `OPEN_TILL` sur le bloc seulement, `OPEN_TILL` sur l'antenne principale ; une carte `DEACTIVATED` ne se réactive pas. Tout refus renvoie la vue seule.
  - `OperatorPanels` (transitoire) : `static void openFromBlock(ServerPlayer, BlockPos)` ; `static boolean openFromPhone(ServerPlayer, PhoneSession)` (fondateur, carte `ACTIVE` de son opérateur, au moins une barre) ; renvoi toutes les 20 tics ; fermés à la déconnexion, à `CLOSE`, à plus de 8 blocs.
  - `OperatorDay implements DailyDebit.DayIssuer` : `issue(server, day, now)` → `PhoneOperators.applyNextTariffs()` (plafond appliqué, fondateurs prévenus d'un tarif ramené, titulaires prévenus d'un changement), `closeAntennaDay()`. Javadoc : le lot T6 émet ici les factures du jour **avant** d'appliquer les tarifs du lendemain.
  - `OperatorPanelScreen(OperatorView)` : l'écran Minecraft, ouvert par le client à la réception d'un `OperatorPanelPacket` de source `BLOCK` (la source `PHONE` est routée à la tâche 10) ; il n'a aucune règle : boutons grisés selon `rights`, gestes envoyés tels quels.
  - `OperatorPanelLayout` (pur) : `static Layout of(int guiWidth, int guiHeight, ToIntFunction<String> textWidth, OperatorView view)` ; tient en 320 × 240 ; trois onglets (Réseau : consommation et relais ; Cartes : demandes, cartes, « SIM sans nom » ; Tarif et caisse) ; textes mesurés et coupés « … » avec bulle complète ; consommation au-delà de la réception en rouge.

- [ ] **Step 0 : écrire `OperatorViewTest`** : `allerRetour` (vue pleine aux bornes, `encode` puis `decode` égal) ; `uneVueTropLongueNeSeLitPas` (33 cartes : lève) ; `unNomDe33CaracteresNeSeLitPas`.
- [ ] **Step 1 : écrire `OperatorPanelLayoutTest`** (largeur factice de 6 px par caractère) : `toutTientEn320Par240` (nom de 24 caractères, 32 antennes, 32 cartes aux titulaires de 16 caractères, 32 demandes, 32 opérateurs au relais, caisse à cinq coupures) ; `uneAntenneAFonderMontreLeChampDuNom` ; `unRegardeurSansDroitAuxCartesNeVoitPasLesBoutons` ; `audelaDeLaReceptionEnRouge`.
- [ ] **Step 2 : GameTests qui échouent** (paquets traités sur place, leçon du 2026-11-12) :
  - lot `panneau_a` : `nommerParLePanneau` ; `laConsommationEnDirect` (un panneau ouvert reçoit un nouvel état toutes les 20 tics, `Breakdown` égal à celui de l'antenne) ; `leFondateurVideLaCaisse` et `lEquipeVideLaCaisse` ; `niLeMaireNiLIngenieurNeVidentLaCaisse` ; `laCaisseNeSOuvreQueSurLAntennePrincipale` (autre antenne, ou depuis le téléphone : refusé) ; `deLoinRienNePasse` (9 blocs) ; `lAppliAntenneOuvreLePanneau` (et refusée sans barre ou en mode avion).
  - lot `panneau_cartes` : `emettreUneDemande` (carte au nom du demandeur dans l'inventaire du fondateur, demandeur prévenu) ; `refuserUneDemande` ; `emettreUneSimSansNom` ; `suspendrePuisReactiver` ; `unPaquetDUnNonFondateurNEmetRien` (maire, ingénieur outillé, équipe, client : aucune carte émise, suspendue ni réactivée) ; `uneCarteDUnAutreOperateurNeBougePas` ; `uneSessionDuTelephonePerimeeNeFaitRien` ; `lInventairePleinDuFondateurNEmetRien`.
  - lot `panneau_vue` : `laVueNeContientNiJetonNiCarteDUnAutreOperateur` (deux opérateurs ; la vue de Mairie, décodée des octets envoyés, ne contient aucune carte ni demande de Lagon, et aucune suite d'octets égale à un jeton du registre) ; `laVueDuTelephoneNOffreNiCaisseNiNom` (`rights.till` et `rights.name` faux pour `PHONE`) ; `unMaireVoitSansGererLesCartes` (`rights.cards` faux, `rights.tariff` vrai) ; `leMemeGesteParLeBlocEtParLeTelephone` (`SUSPEND` par les deux sources : même effet, même vue rendue).
  - lot `panneau_tarif` (`@BeforeBatch` : horloge fixe ; `@AfterBatch` : `EconomyClock.reset()`) : `unNouveauTarifNeVautQuAuLendemain` (fixé à 14 h : tarif du jour inchangé, appliqué à la clôture de minuit, titulaires prévenus, un absent à sa connexion) ; `unTarifAuDessusDuPlafondEstRefuse` ; `unTarifAuDessusDuNouveauPlafondEstRameneAMinuit` (fondateur prévenu) ; `uneHorlogeQuiReculeNAppliqueRien` ; `lesFeDuJourRepartentAZero`.
- [ ] **Step 3 : vérifier qu'ils échouent** — `./gradlew --offline test`, puis GameTests (procédure du verrou).
- [ ] **Step 4 : implémenter.** Retirer le message provisoire de la tâche 4.
- [ ] **Step 5 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed » et le rapport JUnit.
- [ ] **Step 6 : vérifier en jeu** (procédure du verrou, second écran) : `runClient -PwithPack` ; panneau de l'antenne : nom tapé, tarif, carte sans nom, suspension, caisse ; Échap ; chaque capture regardée.
- [ ] **Step 7 : commit** — `feat(telephone): vue de l'operateur, panneau de l'antenne et tarif au lendemain`.

### Task 10 : l'appli « Antenne » en HTML, et le panneau Minecraft dans le repli

**Files:**
- Create : `phone/client/OperatorViewJson.java`, `phone/client/AntennaBridgeMessages.java`, `dev/AppliAntenneGameTests.java` (lot `appli_antenne`)
- Modify : `html/phone/index.html`, `phone.css`, `phone.js` (écran de l'appli), `phone/client/PhoneClient.java` (routage du `OperatorPanelPacket` de source `PHONE`), `PhoneWebScreen.java` (messages `op*` du pont), `PhoneFallbackScreen.java` (« Antenne » ouvre `OperatorPanelScreen`, retour à l'écran de repli à sa fermeture), `phone/net/client/OperatorPanelScreen.java` (source `PHONE` : retour au téléphone), `dev/TelephoneShots.java` (clés permises du témoin du pont : `value`, `n`), langues
- Test : `src/test/java/dev/oneforallsurvival/phone/client/OperatorViewJsonTest.java`, `AntennaBridgeMessagesTest.java`

**Interfaces:**
- Consumes : `OperatorView`, `OperatorPanelPacket`, `OperatorActionPacket`, `OperatorPanelScreen` (tâche 9) ; `PhoneWebScreen.run`, `WebScript.call`, `WebMessage` (lot T1) ; `PhoneActionPacket.Action.OPEN_OPERATOR_APP` (tâche 7).
- Produces (contrat page ↔ Java, à recopier tel quel) :
  - **Java → page** : `window.phone.antenna(view)` à chaque `OperatorPanelPacket` de source `PHONE` reçu pendant que la page du téléphone est ouverte ; `window.phone.antenna(null)` à la fermeture par le serveur. `view` = `OperatorViewJson.toJson(OperatorView)` : `{session, name, rights: {cards, tariff}, tariff, nextTariff (nombre ou null), cap, antennas: [{id, dimension, x, y, z, state, standby, clients, traffic, total, feToday, feYesterday, main, overReceive}], cards: {page, pages, total, items: [{id, number, holder, state}]}, requests: [{id, name, at}], relay: [{name, antennas, inService, status: "joignable"|"hors tension"|"sans antenne"}]}`. `number` déjà formaté (`06 12 34 56 78`), `holder` vide pour une carte sans nom ; ni `till`, ni `pos` de l'antenne cliquée, ni `rights.till`, ni `rights.name` (la caisse et le nom ne se font pas à distance) ; **chaque champ d'`OperatorView` qui n'est pas dans cette liste est absent par choix**, et la liste est vérifiée par test.
  - **Page → Java** (par `oneforall.send`) : `{"a":"opTariff","value":N}` (entier de 0 à 1 000 000) ; `{"a":"opIssue","id":"<uuid>"}` ; `{"a":"opRefuse","id":"<uuid>"}` ; `{"a":"opAnonymous"}` ; `{"a":"opSuspend","id":"<uuid>"}` ; `{"a":"opReactivate","id":"<uuid>"}` ; `{"a":"opMain","id":"<uuid>"}` ; `{"a":"opPage","n":N}` (0 à 63) ; `{"a":"opClose"}`. `AntennaBridgeMessages.toAction(JsonObject, int session)` → `Optional<OperatorActionPacket>` (UUID au format canonique en minuscules, entiers JSON seulement) ; tout autre champ ou type → vide.
  - Rectangles de `layout` ajoutés : `op-tab-network`, `op-tab-cards`, `op-tab-tariff`, `op-tariff-field`, `op-tariff-save`, `op-anonymous`, `op-issue-<id>`, `op-refuse-<id>`, `op-card-<id>`, `op-page-next`, `op-page-prev`, `op-close`.
  - Page : appli plein écran façon iOS (barre de titre au nom de l'opérateur, trois onglets en bas : Réseau — antennes avec voyant, veille, clients, trafic, total en FE/t, FE du jour, « au-delà de la réception » en rouge, et relais ; Cartes — demandes (Émettre, Refuser), cartes émises paginées (Suspendre, Réactiver, rien pour une désactivée), « SIM sans nom » ; Tarif — tarif du jour, du lendemain, plafond, champ et « Enregistrer » ; boutons absents si `rights` est faux) ; mise à jour en direct à chaque vue (toutes les 20 tics) ; retour à l'accueil par la barre d'accueil (envoie `opClose`). Tout texte par `textContent`, aucun `eval`, aucune règle dans la page. Mode `#mock` : une fausse vue (24 caractères de nom, 32 antennes, 32 cartes, demandes, relais) et un faux serveur qui applique les gestes.
  - Repli (sans MCEF) : le bouton « Antenne » envoie `OPEN_OPERATOR_APP` ; le `OperatorPanelPacket` de source `PHONE` ouvre `OperatorPanelScreen` (celui de la tâche 9, mêmes boutons, mêmes droits) ; Échap y rend l'écran de repli du téléphone, sur son dernier état, et envoie `CLOSE`.
- **Comment les deux écrans ne divergent pas** : une seule vue (`OperatorView`, construite par `OperatorViews.build`), un seul jeu de règles (`OperatorActions.apply`), deux paquets ; la page et l'écran Minecraft n'ont aucune règle et ne cachent un bouton que selon `rights`. Trois tests le gardent : `chaqueChampDeLaVueEstTraite` (réflexion sur les composants d'`OperatorView` : chacun est soit dans le JSON, soit dans la liste écrite des absents), `chaqueGesteDeLaPageAUnAction` (chaque message `op*` donne l'`Action` attendue, et chaque `Action` sauf `NAME` et `OPEN_TILL` a son message), et `leMemeGesteParLeBlocEtParLeTelephone` (tâche 9).

- [ ] **Step 1 : tests qui échouent** :
  - `OperatorViewJsonTest` : `chaqueChampDeLaVueEstTraite` ; `pasDeCaisseNiDePositionDansLaPage` ; `numeroFormate` ; un titulaire `"</script> "` relu à l'identique après `WebScript.call` puis `JsonParser` (séquence écrite par script Python) ; `null` pour `nextTariff` absent.
  - `AntennaBridgeMessagesTest` : chaque message du contrat → l'`Action` attendue avec `arg` et `value` ; `chaqueGesteDeLaPageAUnAction` ; vides pour `opTariff` à `-1`, `1000001`, `"5"`, `5.5` ; `opIssue` avec un `id` en majuscules, tronqué ou `"../x"` ; `opPage` à 64 ; `{"a":"opName"}` et `{"a":"opTill"}` (gestes qui n'existent pas dans la page).
  - Lot `appli_antenne` (paquets traités sur place) : `lAppliRecoitLaVueDuTelephone` (`OPEN_OPERATOR_APP` d'un fondateur : un `OperatorPanelPacket` de source `PHONE`) ; `uneAppliPerimeeNeFaitRien` (geste avec un numéro de session ancien, téléphone fermé, carte retirée, mode avion activé ou barres à 0 depuis l'ouverture : rien, vue rendue ou fermeture) ; `unNonFondateurNOuvrePasLAppli` (maire avec une carte de cet opérateur compris) ; `laCaisseNeSeVideJamaisParLAppli` (`OPEN_TILL` source `PHONE` : refusé).
- [ ] **Step 2 : vérifier qu'ils échouent** — `./gradlew --offline test --tests '*phone.client*'`, puis GameTests (procédure du verrou).
- [ ] **Step 3 : implémenter** (page, puis Java).
- [ ] **Step 4 : vérifier hors du jeu** — copie du dossier `html/` sans la ligne CSP, servie en local, `phone/index.html#mock` à 390 × 844 dans le navigateur intégré (`phoneMock.locked()` puis le code `1234` : `#mock` s'ouvre sur l'assistant d'un téléphone neuf) : capturer et **regarder** les trois onglets au contenu le plus long et le plus court, une carte suspendue, une demande, « au-delà de la réception », le champ du tarif et « au lendemain ».
- [ ] **Step 5 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : tout vert, `WebResourcesTest` et `McefIsolationTest` compris.
- [ ] **Step 6 : vérifier en jeu** (procédure du verrou, second écran) — `runClient -PwithPack -PwithMcef` : icône « Antenne » par un vrai clic, onglets, tarif saisi au clavier (leçon du 2026-11-20), émission, suspension par vrais clics, mise à jour en direct quand la source est coupée ; puis `runClient -PwithPack` (sans MCEF) : « Antenne » ouvre le panneau Minecraft, Échap rend le téléphone. Chaque capture regardée ; pont : 0 message étranger.
- [ ] **Step 7 : commit** — `feat(telephone): appli Antenne en HTML et panneau dans le repli`.

### Task 11 : gestes du maire et de l'équipe

**Files:**
- Create : `phone/net/OperatorCommands.java`, `dev/MaireTelephoneGameTests.java` (lot `maire_telephone`)
- Modify : `OneForAllSurvival.java` (commandes), `PhoneOperators.java` (suppression complète), langues

**Interfaces:**
- Consumes : `RoleGate.canDoMayorWork`, `PhoneOperators`, `CommerceDelivery.payToSafeFirst`, `AntennaChunks.release`, `OperatorPanels`, `EconomyLedger` (lignes du journal, comme `/power`).
- Produces (maire ou équipe ; refus clair sinon ; chaque geste au journal avec l'auteur) :
  - `/operator list` (nom, fondateur, antennes en service / posées, cartes actives, tarif du jour et du lendemain) ;
  - `/operator cap <cuivres>` (0 à `phoneTariffMax`, au lendemain) ;
  - `/operator set <opérateur> tariff <cuivres>` (au lendemain, sous le plafond) ;
  - `/operator owner <opérateur> <joueur>` (un joueur qui a déjà un opérateur est refusé ; les cartes et la caisse suivent l'opérateur) ;
  - `/operator remove <opérateur> <motif>` puis `/operator remove <opérateur> confirm` dans les 60 s : toutes les cartes `DEACTIVATED`, antennes inscrites retirées du registre (leur bloc reste, `STOPPED`, ticket rendu ; un objet d'antenne marqué de cet opérateur reposé devient une antenne à fonder, à l'ancien fondateur comme le poste d'E6), demandes retirées et demandeurs prévenus, panneaux fermés, caisse versée au fondateur (`payToSafeFirst`), fondateur et titulaires prévenus (à leur connexion s'ils sont absents), opérateur retiré.
  - **Renvoyés au lot T6** : `/operator tax` (taxe téléphone), `/eco stats` (abonnements payés), annulation des factures dues.

- [ ] **Step 1 : GameTests qui échouent** (lot `maire_telephone`) :
  - `seulsLeMaireEtLEquipeFontCesGestes` (citoyen, ingénieur outillé, fondateur : refusés) ;
  - `lePlafondVautAuLendemain` ; `leMaireFixeUnTarif` (au-dessus du plafond : refusé) ;
  - `changerDeFondateur` (le nouveau émet ; l'ancien ne peut plus) ;
  - `laSuppressionDemandeUneConfirmation` (sans `confirm` : rien ; après 60 s : expirée) ;
  - `unOperateurSupprimeNeLaisseRienDActif` (cartes `DEACTIVATED`, barres 0 sur les téléphones, `route` → `CALLEE_OPERATOR_REMOVED`, antennes `STOPPED` sans consommation, tickets rendus, demandes retirées, avis) ;
  - `laCaisseVaAuFondateur` (somme des pièces conservée : caisse + coffre-fort du fondateur avant et après) ;
  - `uneAntenneDUnOperateurSupprimeNeLeRessusciteRien` (objet marqué reposé : antenne à fonder, aucun opérateur recréé).
- [ ] **Step 2 : vérifier qu'ils échouent** (procédure du verrou).
- [ ] **Step 3 : implémenter.**
- [ ] **Step 4 : vérifier** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` ; attendu : « All N required tests passed ».
- [ ] **Step 5 : commit** — `feat(telephone): gestes du maire sur les operateurs`.

### Task 12 : visuels finaux

**Files:**
- Create : `tools/gen_textures_t2.py`, `textures/block/antenna.png`, `antenna_lit.png`, `textures/item/antenna.png`, `textures/item/sim_card.png`, `textures/item/maintenance_tool.png`, `textures/gui/operator_panel.png` (si le panneau en dessine un fond)
- Modify : `models/block/antenna*.json`, `blockstates/antenna.json`, `models/item/antenna.json`, `models/item/sim_card.json`, `models/item/maintenance_tool.json`, `dev/DemoVisuels.java` (scène `telephone_reseau`)

**Interfaces:**
- Consumes : `ElectricMachineBlock.STATE`, `AntennaBlock` (tâche 3), `RepairRegistry.MAINTENANCE_TOOL` (tâche 4), `OperatorPanelScreen` (tâche 9), page de l'appli « Antenne » (tâche 10), `PhoneFallbackScreen` (tâche 8).
- Produces : `-PvisuelsSeul=telephone_reseau` ; captures dans `run-demo/visuels-captures/telephone_reseau-*.png`.

- [ ] **Step 1 : écrire le script et le lancer** — `python tools/gen_textures_t2.py` : mât gris à haubans, semelle, voyant vert allumé en service et éteint sinon ; objet antenne ; carte SIM 16 × 16 (puce dorée, coin coupé) ; outil de maintenance 16 × 16 (clé plate et tournevis croisés, manche cuivre), distinct du testeur et du soudeur dans la barre.
- [ ] **Step 2 : écrire modèles et état de bloc** (mât de la tâche 3 ; variantes par `STATE`).
- [ ] **Step 3 : capturer** — `./gradlew runDemoVisuels -PvisuelsSeul=telephone_reseau` (procédure du verrou, second écran) : antenne collée à un mur de pierre et sous un bloc, en service, sans courant, en panne ; carte dans l'inventaire et son infobulle (nommée, sans nom, non reconnue) ; panneau au contenu le plus long et le plus court en 320 × 240 et en grande interface, ses trois onglets, une bulle de nom coupé ; repli : barre d'état, feuille « Carte SIM » et panneau ouvert depuis l'appli « Antenne » ; outil de maintenance en main (1re et 3e personne), dans la barre à côté du testeur et du soudeur, infobulle. **Page de l'appli « Antenne »** avec les textures finales (icône de l'accueil) : copie du dossier `html/` sans la ligne CSP, servie en local, `#mock` à 390 × 844 dans le navigateur intégré (`phoneMock.locked()` puis le code `1234`), trois onglets au contenu le plus long et le plus court. **Ouvrir chaque image** : rien ne déborde, aucun voisin transparent, voyant lisible. Lire « [souris] rendue » et « [fenetre] second écran » ; verrou vidé.
- [ ] **Step 4 : commit** — `art(telephone): textures et modeles de l'antenne, de la carte SIM et de l'outil de maintenance`.

### Task 13 : essais réels sur serveur dédié et vrai client à deux téléphones

**Files:**
- Create : `dev/E2eTelephone.java` (client), `dev/E2eTelephoneServeur.java` (commandes de console niveau 4 réservées au RCON, sur le modèle d'`E2eElectriciteServeur` : bâtir l'aire close et couverte, créer les joueurs simulés `Fondatrice`, `Abonne`, `Voisin`, couper ou rétablir une source, lire `PhoneOperators`)
- Modify : `dev/E2eClient.java` (phase `TELEPHONE`, variable `E2E_TELEPHONE_SEUL`), `dev/TelephoneShots.java` (modes `signal` et `antenne`), `build.gradle` (`-PtelephoneShotsMode=signal|antenne`)

**Ordre des étapes** (leçons du 2026-11-11, du 2026-11-15 et du 2026-11-19 : lister d'abord ce que chaque relance relit) :
- Passage : … → `QUETES` → `ELECTRICITE` → **`TELEPHONE`** → `TRESOR`. `TELEPHONE` lit le tarif du lendemain **avant** que `TRESOR` n'avance l'horloge de deux minuits ; à la relance, le tarif attendu se calcule sur l'état relu (leçon du 2026-11-18).
- Relance : … → `ELECTRICITE` → **`TELEPHONE`** → `QUETES` en dernier.
- Aire à part, close, couverte, loin du point de départ, **antennes en bord de chunk** (leçon du 2026-11-19) ; l'étape rend les droits et range l'inventaire case par case (leçon du 2026-11-19).

- [ ] **Step 1 : E2E sur serveur dédié avec le pack** (client `Dev`, ingénieur, en survie, non opérateur, **outil de maintenance en main** ; son absence est essayée d'abord : pas de panneau sur l'antenne de `Fondatrice`) : `Fondatrice` (simulée) fonde « Lagon » par RCON ; Dev pose la sienne (vrai clic), la nomme « Mairie » au panneau (vraie saisie), fixe un tarif (vrais clics : « au lendemain » affiché), émet une SIM sans nom et une carte demandée par `Abonne` (vrais clics) ; source de Crafts & Additions par un fil ; consommation du panneau = consommation relue par RCON (veille + 6 × cartes actives) ; `Abonne` à 100 blocs (replacé par `teleportTo`, leçon du 2026-11-14) : 3 barres relues ; source de Lagon coupée par RCON : trajet Mairie → Lagon en échec « Antenne de Lagon hors tension » (commande de développement qui appelle `PhoneNetwork.route`) ; une copie créative de la carte d'`Abonne` (RCON) glissée dans un second téléphone : une seule ligne ; un joueur simulé sans droit tente d'ouvrir et de casser l'antenne : refusé. Avant l'arrêt, tout est écrit dans `e2e-telephone.txt` ; tous les joueurs déconnectés.
- [ ] **Step 2 : relance du serveur** (`-Pe2eHorloge`, un jour de plus) : opérateurs, cartes, jetons, tarifs appliqués, antennes **en service sans joueur à côté** (tickets revalidés), copie toujours morte ; le maire simulé supprime « Lagon » (`/operator remove … confirm`) : ses cartes désactivées, ses antennes `STOPPED`, caisse au fondateur. Lancements : `runServer -PwithPack` puis `E2E_TELEPHONE_SEUL=1 runE2eClient -PwithPack` ; `runServer -PwithPack -Pe2eHorloge=<minutes>` puis `E2E_TELEPHONE_SEUL=1 runE2eRelance -PwithPack` ; puis le **passage complet** et sa relance. Lire « [e2e] bilan : TOUT PASSE » à chaque passage ; verrou cédé entre deux passages si la file n'est pas vide.
- [ ] **Step 3 : vrai client à deux téléphones** (mode `signal` de `TelephoneShots`, serveur dédié avec le pack, MCEF) : Dev porte le téléphone A (carte de « Mairie ») et le téléphone B (carte de « Lagon ») ; à 0, 100, 200 et 300 blocs de chaque antenne, ouvre A puis B par un vrai `useItem` : barres de la vue reçue (`PhoneClient.last()`) égales à celles du serveur, capture de la barre d'état ; source de Lagon coupée : B à 0 barre, A inchangé ; mode avion sur A par un vrai clic : « Mode avion » ; carte retirée puis remise par la feuille. Mode `antenne` (même serveur, MCEF) : Dev ouvre l'appli « Antenne » par un vrai clic sur l'icône, parcourt les trois onglets, suspend et réactive une carte, change le tarif au clavier ; vue de la page égale à la vue relue par RCON (cartes, consommation), coupure de la source de « Mairie » vue en direct ; pont : 0 message étranger. Puis `runClient2 -PwithPack` (sans MCEF, repli) : même relevé du signal à 100 blocs, et « Antenne » qui ouvre le panneau Minecraft, Échap qui rend le téléphone. **Regarder chaque capture.** Lire « [souris] rendue » et « [fenetre] second écran » ; PID notés ; aucun client laissé ouvert ; serveur arrêté par RCON.
- [ ] **Step 4 : commit** — `test(telephone): essai de bout en bout du reseau, vrai client a deux telephones et appli Antenne`.

### Task 14 : preuve du lot T2

**Files:**
- Create : `docs/verifications/lot-t2.md`
- Modify : `tasks/todo.md` (section « Téléphone », ligne T2), `wiki/data/telephone.json` (cartes `carte-sim`, `antenne`, `panneau-operateur` (panneau de l'antenne et appli « Antenne »), `relais` à `"dispo"` avec image ; carte nouvelle `outil-maintenance` ; `reglages` mise à jour : carte, opérateur, signal, mode avion), `tasks/lessons.md`

- [ ] **Step 1 : suite complète** — `./gradlew --offline test runGameTestServer`, puis `-PwithPack` (procédure du verrou) ; consigner les compteurs lus.
- [ ] **Step 2 : écrire `docs/verifications/lot-t2.md`** sur le modèle de `lot-t1.md` : tests (comptes, lots, mutations vues rouges), API des tickets de Forge relevée (tâche 3), E2E et relance, vrai client à deux téléphones et repli, appli « Antenne » en HTML et panneau dans le repli (contrat page ↔ Java tel que livré), outil de maintenance (catalogue de l'établi, règle « rôle et outil », trésor et tableau), captures regardées, décisions et choix, « Ce que T2 prépare » (interfaces pour T3 et T6, telles que livrées), « Non prouvé et limites » au moins : appels absents (T3) ; trafic jamais porté par un vrai appel ; générateur hors du chunk de l'antenne ; coût serveur de nombreux chunks forcés non mesuré ; copie créative d'un téléphone qui partage la ligne (choix du lot T1) ; un membre de l'équipe en créatif qui déplace une carte lui fait perdre son jeton (`getShareTag`) ; carte sans nom et abonnement (question du lot T6) ; un outil de maintenance perdu ou volé n'a pas d'effet sans le rôle, mais rien ne le rend à l'ingénieur (il en refait un) ; Mac à puce Apple toujours non vérifié.
- [ ] **Step 3 : `tasks/todo.md`** — ligne `- [x] **Lot T2 : réseau.** [Plan détaillé](../docs/superpowers/plans/archive/2026-11-20-lot-t2-telephone-reseau.md). …` (plan déplacé dans `archive/` par `mv`) ; restes ; la condition du lot T3 (blocage croissant) reste ouverte.
- [ ] **Step 4 : wiki** — au format des entrées `dispo` du lot T1 : `who.fabrique` de l'antenne et de l'outil de maintenance « Ingénieur (établi) », de la carte « Émise par l'opérateur » ; à qui sert l'outil (antennes, trésor, tableau) ; chiffres (270 blocs, 40, 6, 9, 24 FE/t, barres) ; étapes (pose, nom, demande, émission, insertion) ; bloc `staff` : `/operator`, `oneforallsurvival-phone.toml`.
- [ ] **Step 5 : leçons** — une par correction significative du lot, au format du dépôt ; la forme générale d'un fait sur les tickets de chargement de Forge dans `D:/BDC` (après lecture d'`Accueil.md` et `Conventions.md`, écritures groupées).
- [ ] **Step 6 : commit et push** — `docs(telephone): preuve du lot T2`, chemins listés un par un.

---

## Auto-relecture (faite à l'écriture)

- **Couverture du cahier** : 89 — fabriquée par l'ingénieur, posée par tous, fondateur ou ajout (tâches 3, 4) ; portée et barres (1, 6, 7) ; veille, cartes, trafic, réglables (1, 2, 3, 6) ; panne sans courant, voyant (3, 12) ; caisse fondateur et équipe (9) ; sécurité, ingénieur muni de l'outil de maintenance (objet nouveau), protection E6 (4). 90 — panneau sur l'antenne (9), appli « Antenne » en HTML et panneau dans le repli (7, 8, 10) ; consommation en direct découpée (9) ; demandes, émission, refus, suspension, réactivation, SIM sans nom, carte dans l'inventaire du fondateur (5, 9) ; tarif au lendemain sous plafond, abonnés prévenus (9, 11) ; recette, électricité, relais (9). 91 — un téléphone ne parle qu'à son opérateur, relais automatique, trajet, deux antennes portent le trafic, message d'échec (1, 6). Carte SIM — objet, numéro, titulaire ou sans nom, glissée depuis l'inventaire, numéro qui suit la carte, seul le fondateur (5, 7, 9). Maire — casse, modifie, supprime, plafond, propriétaire (4, 9, 11) ; taxe au lot T6. Section 8 : chaque ligne qui concerne T2 a son test nommé (6, 3, 4, 9, 11). Feature 101 : carte, retirer, mode avion, signal, opérateur, fondateur, abonnement (7, 8).
- **Noms** : `SignalMath.bars`, `AntennaLoad.of/Breakdown`, `SimNumbers.draw/format/parse`, `RelayPlanner.plan/best/anyInService/Snapshot/AntennaPoint/Endpoint/Route/Failed`, `RelayFailure.Reason/describe`, `PhoneOperators.found/issue/rotateToken/unbind/request/takeRequest/noteTick/applyNextTariffs/closeAntennaDay`, `AntennaBlockEntity.inService`, `AntennaChunks.hold/release/validate`, `RepairRegistry.MAINTENANCE_TOOL`, `AntennaRules.mayManage/mayManageCards/mayTakeTill/maySetTariff/hasMaintenanceTool`, `PhoneSims.check/line/insert/remove/choices/issueTo`, `PhoneNetwork.status/route/lineOf/emergencyAntenna/openTraffic/messageSent/relayStates`, `PhoneNetView`, `OperatorView`, `OperatorViews.build`, `OperatorActions.apply`, `OperatorViewJson.toJson`, `AntennaBridgeMessages.toAction`, `OperatorPanels.openFromBlock/openFromPhone`, `OperatorDay` : mêmes dans toutes les tâches.
- **Réseau** : protocole `"11"` une seule fois (tâche 7) ; paquets 28 et 29 (tâche 9).
- **Review Focus** : chaque ligne a son test dans la tâche qui possède le code (tâches 2, 3, 4, 5, 6, 7, 9, 10, 11).
