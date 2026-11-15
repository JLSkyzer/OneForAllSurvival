# Cahier des charges : les documents de lore en jeu

Date : 2026-11-15. Source du lore : `D:\LostIsland\lore` (fourni par Killian).

## 1. Pourquoi

Killian a écrit une archive de lore de Lost Island RP : 25 documents HTML (programme ARRIVAL, carnets de
Mathis Delcourt, enregistrements). Le staff
les dispose sur la map ; les joueurs les découvrent en explorant et reconstituent le mystère de l'île.

La page doit être **le vrai HTML** de Killian, avec sa mise en page, pas une interface Minecraft qui en
recopie le texte : le soin mis dans chaque document (tampons, en-têtes, tableaux, styles par type de
pièce) fait partie de l'expérience.

## 2. Objectifs et limites

### Objectifs
- Chacun des 25 documents peut être posé par le staff, n'importe où, autant de fois qu'il veut.
- Un document posé **reste sur place** : on le lit, on ne le prend pas.
- Un **ordinateur** affiche un bureau de système d'exploitation fictif, avec des dossiers et des fichiers
  à ouvrir ; le staff choisit ce que contient chaque poste.
- Des fichiers ou dossiers **verrouillés par mot de passe**, mot de passe caché ailleurs sur la map.
- Le rendu est celui d'un navigateur : la page s'affiche comme dans Chrome.

### Hors périmètre
- Garder une copie, un journal ou une liste des documents lus.
- Électricité : l'ordinateur s'allume toujours (décision de Killian).
- Vrai son pour les enregistrements : on lit leur transcription HTML.
- Lien avec le récepteur radio du mod (`explore/ReceiverBlock`) : possible plus tard, pas maintenant.
- Éditer le contenu des documents en jeu : ils se changent dans les fichiers du mod.

### Ce qui serait un échec, même si tout fonctionne
- Une page dont la mise en page diffère visiblement de son rendu dans Chrome (grilles cassées, couleurs
  perdues, texte réécrit).
- Un joueur qui casse, ramasse, pousse ou vide un document ou un ordinateur.
- Un mot de passe contournable par le client (un joueur qui débloque un fichier sans l'avoir tapé).
- Un poste dont le contenu se perd au redémarrage du serveur.

## 3. Les documents

- **25 documents** (dossier relu le 2026-11-15, après tri par Killian) : 11 dans `04_PROGRAMME_ARRIVAL`
  (`ARR-001` à `ARR-010`, `ARV-001`), 9 dans `05_MATHIS_DELCOURT` (`MAT-001` à `MAT-009`), 5 dans
  `06_ENREGISTREMENTS` (`AUD-001` à `AUD-004`, `COM-001`). Killian a retiré les dossiers Nova Corps,
  Sujets expérimentaux et Secteur Oméga, ainsi que les deux pièces de la racine. Le catalogue suit
  le dossier : ce qui n'y est plus n'entre pas dans le mod.
- **Exclus** : `INDEX.html` (la liste de tout le lore, qui gâcherait la découverte ; elle cite encore des
  documents retirés) et le PDF
  « A NE PAS PRENDRE EN COMPTE ».
- **Constat technique** (lu le 2026-11-15) : pages autonomes, CSS dans la page, **aucun script, aucune
  image, aucun lien externe**. Le CSS est moderne : grilles, `color-mix()`, `clamp()`, variables CSS,
  dégradés, `@media`. Polices système (Georgia, Arial, Segoe UI, Consolas, monospace). 870 Ko au total.
- **Rangement dans le mod** (décision de Killian : option B) : copiés tels quels dans
  `src/main/resources/assets/oneforallsurvival/html/lore/<catégorie>/<id en minuscules>.html`, sans
  retouche du HTML (chemin imposé par le schéma `mod://` de MCEF, qui sert `assets/<modid>/html/` et passe
  les chemins en minuscules ; voir le plan).
- **Identifiants** : le nom du fichier (`ARR-004`, `MAT-007`, `AUD-002`…).
- **Un manifeste** (`lore/documents.json`) liste chaque document : identifiant, titre (celui de la
  balise `<title>`), catégorie, fichier, forme posée par défaut, type de fichier sur l'ordinateur
  (`html` ou `audio`). Ajouter un document = déposer le fichier + une ligne au manifeste + une version du mod.
- **Conséquence acceptée de l'option B** : tout le lore est dans le `.jar` chez chaque joueur. Un joueur
  qui ouvre le fichier du mod peut tout lire d'avance, y compris les fichiers verrouillés. Le mot de
  passe protège le jeu, pas le fichier.

## 4. Affichage : MCEF

- Rendu par **MCEF** (Chromium dans Minecraft). Les moteurs HTML en Java (Flying Saucer et semblables)
  s'arrêtent au CSS 2.1 et casseraient ces pages : écartés.
- **Même MCEF que le téléphone** (spec du 2026-11-08) : le fork de Keksuccino, 2.2.0 pour Forge 1.20.1.
  Ce chantier est probablement le **premier** à intégrer MCEF : en faire une base réutilisable par le
  téléphone (un paquet client unique pour le navigateur, l'écran plein écran et le chargement des pages).
  Vérifier au premier lot la version de Chromium (CinemaMod annonce 116, qui gère `color-mix()`),
  l'API et la licence du fork.
- MCEF est **client seulement**. Il télécharge Chromium (environ 100 Mo) au premier lancement.
- **Livraison** (choix de l'auteur) : le `.jar` de MCEF va dans le dossier `mods` du pack, distribué par
  le launcher. Le `mods.toml` le déclare côté client seulement : le serveur ne l'exige pas. Le mod
  n'appelle MCEF qu'à travers une vérification de présence, et le repli (§ 4, § 7) couvre aussi le cas
  « MCEF absent ». Chromium reste téléchargé par MCEF lui-même.
- **Ordre des chantiers** (choix de l'auteur) : ce chantier écrit la base MCEF commune ; le téléphone la
  reprendra. Si une autre session a commencé le téléphone entre-temps, se coordonner avec elle avant le
  premier lot.
- Les pages se chargent **depuis les ressources du mod**, jamais depuis le réseau. Aucune navigation
  hors des pages du mod n'est possible.
- **Lecture** : plein écran, défilement à la molette, Échap ferme. Thème clair, comme une feuille
  imprimée (les pages déclarent `color-scheme: light dark` : forcer le clair pour que le rendu ne dépende
  pas du thème du système du joueur).
- **Si MCEF ne démarre pas** chez un joueur (Mac à puce Apple, antivirus…), le document s'ouvre dans
  **son navigateur habituel** : le client écrit la page dans un fichier temporaire et l'ouvre. C'est
  toujours le vrai HTML (accord de Killian). Le joueur est prévenu une fois.

## 5. Le document posé

- **Un bloc par document.** Le staff reçoit chaque document comme un objet dans un **onglet créatif
  « Lore » réservé**. Posé, il devient un petit bloc plat (sur une table, au sol), orienté selon le joueur.
- **Formes** (choix de l'auteur) : feuille, dossier ou classeur, carnet, cassette, clé USB. Forme par
  défaut selon le manifeste (pièce du programme ARRIVAL → classeur, carnet ou rapport de Mathis →
  carnet, enregistrement → cassette) ; le staff peut la changer en créatif (accroupi + clic droit fait défiler
  les formes).
- **Clic droit d'un joueur** : la page HTML du document s'ouvre.
- **Protection** comme le mécanisme ancien (`explore/MechanismBlock`) : dureté -1, résistance maximale,
  pas de poussée au piston, aucun butin. Il ne se casse qu'en créatif. Un survol affiche le titre du
  document (choix de l'auteur).
- Même document posé plusieurs fois : autorisé.

## 6. Le post-it

- Bloc plat posé par le staff, avec un **texte libre** qu'il écrit (accroupi + clic droit en créatif ouvre
  la saisie). Sert à cacher un mot de passe, un indice, une note griffonnée.
- Lecture : clic droit, petite page HTML générée par le mod (gabarit « papier » ; le texte du staff est
  échappé, jamais interprété comme du HTML).
- Mêmes protections que le document posé. Texte gardé par le serveur dans l'entité du bloc.
- Un mot de passe peut aussi figurer dans un document existant : c'est à Killian de l'écrire dans son HTML.

## 7. L'ordinateur

### Pour le joueur
- Bloc ordinateur (écran et tour), mêmes protections. Toujours allumé.
- Clic droit : un **bureau** plein écran, en HTML, CSS et JavaScript (fourni par le mod) : fond d'écran,
  nom du poste, icônes de dossiers, fenêtre d'explorateur, barre des tâches avec une heure.
- **Identité** (décision de Killian) : un système maison de **Nova Corps** (nom du système, logo, fond
  d'écran par défaut dans l'esprit des documents Nova Corps). Le staff peut changer le **nom du poste** et
  le **fond d'écran** de chaque poste (fonds fournis par le mod, choix de l'auteur).
- **Horloge figée** (décision de Killian) à la fermeture du site : **17 novembre 1998**, date de
  l'annonce de fermeture (`AUD-001`, seul document restant qui la donne). Le lore ne donne
  pas d'heure : l'heure affichée est réglable par poste par le staff (choix de l'auteur), elle ne bouge
  jamais. L'esthétique du bureau doit rester crédible pour une machine de 1998 (choix de l'auteur).
- Double-clic sur un fichier : le document s'ouvre **dans une fenêtre du bureau**, avec le vrai HTML
  (par exemple dans une `iframe` qui charge la page des ressources). Plusieurs fenêtres à la fois, qu'on
  déplace et ferme. Une fenêtre de document s'ouvre **large** (au moins 760 px utiles, agrandissable en
  plein bureau) : plusieurs pages passent en mise en page étroite sous 700 px, et on veut la mise en page
  de grand écran (choix de l'auteur).
- Fichiers « audio » (AUD, COM) : icône de son, fenêtre de lecteur factice autour de la transcription.
- **Lecture seule** : ni supprimer, ni renommer, ni déplacer.
- Chaque joueur a sa propre vue : deux joueurs devant le même poste ne se gênent pas.

### Mot de passe
- Le staff verrouille un **fichier** ou un **dossier entier** et choisit le mot de passe.
- **Comparaison tolérante** (choix de l'auteur) : sans distinction de majuscules, sans accents, espaces
  au début et à la fin ignorés. « omega » ouvre « OMÉGA ». Le staff le sait en écrivant ses énigmes.
- Un fichier verrouillé affiche un cadenas ; l'ouvrir demande le mot de passe. Mauvais mot de passe :
  message d'erreur du système fictif. Aucun nombre d'essais limité, aucune sanction.
- **Le serveur vérifie** : le bureau envoie la saisie au serveur par le pont JavaScript, le serveur
  compare. Le mot de passe n'est jamais envoyé au client.
- Bon mot de passe : déverrouillé **pour tout le monde**, définitivement (décision de Killian). Les
  joueurs devant ce poste voient le cadenas disparaître.
- Le staff peut **reverrouiller** (remettre l'énigme à zéro) depuis le mode édition.
- Le serveur refuse d'ouvrir un fichier verrouillé à un client qui le demande sans l'avoir débloqué
  (même si le contenu est lisible dans le `.jar`, voir § 3).

### Mode édition (staff)
- Réservé au niveau de permission 2 (comme les commandes `cache`, `signal`, `mechanism`), en créatif.
- Depuis le bureau lui-même : nommer le poste, créer, renommer, supprimer des dossiers, glisser des
  documents depuis la liste des 25, poser et retirer des mots de passe, reverrouiller.
- Le contenu de chaque poste (arborescence, mots de passe, état des verrous) est gardé par le serveur
  dans l'entité du bloc ; il survit au redémarrage et au rechargement des chunks.
- Copier la configuration d'un poste sur un autre (choix de l'auteur, confort du staff) : objet ou
  commande au choix du constructeur.

### Si MCEF ne démarre pas
- Le bureau devient une **liste simple en interface Minecraft** : dossiers, fichiers, champ de mot de
  passe. Ouvrir un fichier l'ouvre dans le navigateur du joueur, comme au § 4 (choix de l'auteur : le
  bureau n'est pas un document de Killian, seul le document doit rester du vrai HTML).

## 8. Sécurité

- Le serveur fait foi pour tout ce qui change un état : mots de passe, verrous, contenu des postes,
  texte des post-it. Toute demande venant du pont JavaScript est vérifiée (droits, existence du poste,
  distance du joueur au bloc).
- Les pages ne chargent rien depuis Internet. Le texte venant du staff est échappé avant d'entrer dans
  une page.
- Longueurs bornées : noms de dossiers, mots de passe, texte des post-it.

## 9. Découpage conseillé

1. **Base MCEF** : dépendance, écran plein écran, chargement depuis les ressources, repli navigateur.
   Premier test sur Windows ; test sur un Mac à puce Apple dès que possible.
2. **Documents posés** : manifeste, 25 pages copiées, onglet créatif, bloc à formes, protections.
3. **Ordinateur** : bureau HTML, fenêtres, pont JavaScript, mode édition, sauvegarde.
4. **Mots de passe et post-it**, puis l'écran de repli de l'ordinateur.

## 10. Vérification

- Tests automatiques (GameTest) côté serveur : protections des blocs, mot de passe juste et faux,
  déverrouillage global, reverrouillage, refus d'un fichier verrouillé, droits du mode édition, état
  conservé après rechargement.
- Comparaison visuelle : une page de chacun des 13 gabarits présents (`briefing`, `telemetry`, `matrix`,
  `fieldreport`, `directive`, `timeline`, `dossier`, `redacted`, `diary`, `report`, `agreement`,
  `transcript`, `waveform`), capturée en jeu et dans Chrome, côte à côte.
- Lancement du client selon la procédure du verrou de lancement Minecraft, fenêtre sur le second écran.

## 11. Décisions et choix

### Décisions de Killian (2026-11-15)
- Les documents présents dans `D:\LostIsland\lore` (25 après le tri du 2026-11-15, qui a retiré Nova
  Corps, Sujets, Oméga et les deux pièces de la racine) ; vrai rendu HTML, pas d'interface réécrite.
- Un document posé reste sur place.
- L'ordinateur : un bureau avec des dossiers et des fichiers à ouvrir.
- Mots de passe trouvés ailleurs sur la map ; déverrouillage pour tout le monde.
- Pas d'électricité.
- Documents rangés dans le mod (option B), risque de lecture dans le `.jar` accepté.
- Si MCEF échoue : ouverture dans le navigateur du joueur.
- Bureau : système Nova Corps ; horloge figée à la fermeture du site (17 novembre 1998).

### Choix faits par l'auteur du document
Non validés par Killian, contestables :
- le document posé est un bloc plat à cinq formes, que le staff fait défiler ;
- le post-it à texte libre ;
- le mode édition dans le bureau, par glisser-déposer ;
- le bouton reverrouiller ;
- le titre au survol ;
- l'écran de repli de l'ordinateur en interface Minecraft ;
- la copie de configuration d'un poste à l'autre ;
- thème clair forcé ;
- MCEF livré par le launcher, côté client seulement, et repli aussi quand il est absent ;
- fenêtres de documents larges (au moins 760 px) ;
- mots de passe comparés sans majuscules, accents ni espaces de bord ;
- ce chantier construit la base MCEF que reprendra le téléphone ;
- heure figée réglable par poste, fonds d'écran fournis par le mod, esthétique de 1998.
