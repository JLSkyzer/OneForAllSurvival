# Cahier des charges : la livraison des achats de la boutique

Date : 2026-11-13. Statut : en attente de validation par Killian.

Le site Lost Island (`https://lostisland.eriniumlab.com`) a une boutique. Un joueur y paie un produit
(« Krokmou », un grade, des pièces) ; il doit le recevoir en jeu sans qu'un membre de l'équipe tape une
commande. Le contrat entre le site et le serveur est fixé côté site, dans
`D:\LostIsland\site\docs\api-serveur-minecraft.md` (dépôt du site), **version révisée du 2026-11-13**,
déployée sur le vrai site. **Ce cahier en est le pendant côté mod et ne le contredit pas.** Les points que
le contrat laisse encore ouverts sont listés en section 13.

Le cahier touche à l'argent : l'action `pieces` crée des pièces, ce que le
[cahier de l'économie](2026-11-08-economie-design.md) n'autorise que pour quatre sources déclarées. Cette
nouvelle source est **à trancher par Killian** (section 13).

## 1. Pourquoi

Sans livraison automatique, chaque achat attend qu'un membre de l'équipe soit connecté, lise la commande et
tape les bonnes commandes, avec le risque d'oublier, de se tromper de joueur ou de donner deux fois.

**Critère de réussite : un joueur paie sur le site, se connecte, et reçoit exactement ce qu'il a acheté,
une seule fois, même si le serveur, le site ou le réseau tombent à n'importe quel moment.**

## 2. Objectifs et limites

### Objectifs
- **Lier** un compte Minecraft au compte Discord du site, par un code tapé en jeu.
- **Livrer** chaque achat à un joueur connecté : objets, commandes du serveur, pièces.
- **Ne jamais livrer deux fois**, et ne jamais perdre une livraison : un journal persistant tient l'état de
  chaque livraison, et les résultats que le site n'a pas reçus lui sont renvoyés plus tard.
- **Dire au site que le serveur vit** (battement de cœur), pour l'affichage « en ligne » du site.
- Laisser l'équipe **voir et débloquer** les livraisons par commande, sans ouvrir de fichier.

### Hors périmètre
- **Le paiement.** Le mod ne voit aucun prix, aucune carte, ne connaît ni Stripe ni PayPal. Il ne sait
  d'une commande que ce qu'il doit donner.
- **Ouvrir un port.** C'est le serveur qui appelle le site, en HTTPS sortant, jamais l'inverse (contrat,
  « Principes »). Le mod n'embarque aucun serveur HTTP.
- **Le catalogue.** Les produits et leurs actions se règlent dans l'administration du site, et sont figés
  au moment de l'achat (contrat) ; le mod n'a rien à en savoir.
- **Le remboursement** et l'annulation : décidés sur le site ; le mod apprend seulement qu'une livraison est
  `annulee` (réponse `409`).
- **Livrer à un joueur hors ligne.** Le contrat l'interdit (« Panne et reprise ») : on attend sa connexion.
- **Retirer** en jeu ce qui a été livré après un remboursement tardif : affaire de l'équipe, à la main.

### Ce qui serait un échec, même si tout fonctionne
- Un achat livré deux fois, quelle que soit la panne.
- Un achat payé qui ne parvient jamais au joueur sans que l'équipe le voie (échec silencieux).
- Une livraison à moitié faite que personne ne peut repérer.
- La clé du site visible dans un journal, un message en jeu, un fichier du dépôt, ou envoyée aux joueurs.
- Un site lent ou en panne qui fait ramer le serveur.
- Des pièces créées par la boutique sans être comptées au tableau de bord de l'économie.

### Règle de conception
**Rien n'est donné tant que le journal n'a pas écrit, sur le disque, qu'on va le donner ; et rien n'est
annoncé au site comme livré tant que ce qui a été donné n'est pas sauvegardé.** En cas de doute, le mod
ne redonne pas : il signale à l'équipe.

## 3. Contraintes techniques

### Existant (lu dans le code le 2026-11-13)
- **Monnaie** : `economy/PlayerCoins` est « la seule porte par où [les pièces] entrent et sortent » de chez
  un joueur. `PlayerCoins.receive(ServerPlayer, Coin, long)` fait entrer des pièces dans la première bourse
  qui a de la place, puis dans l'inventaire, et **rend le nombre réellement entré** (le reste n'est pas
  touché). `PlayerCoins.giveOrDrop` fait tomber le reste au sol, réservé au joueur.
  `CoinMath.decompose(long)` découpe un montant en cuivres en coupures. `EconomyLedger.get(server)
  .created(MoneySource, montant, auteur, motif)` compte une création au registre et au tableau de bord.
  `MoneySource` a quatre valeurs : `MOB_LOOT`, `QUEST`, `STARTING`, `STAFF`. Le cuivre, qui vaut toujours
  1 (`EconomyConfig`), est la plus petite unité de la monnaie.
- **Piège déjà relevé** (commentaire de `PlayerCoins.receiveLoose`, lu dans le bytecode de la 1.20.1) :
  pour un joueur en créatif, `Inventory.add` vide la pile et répond vrai même quand rien n'est entré. Ce qui
  est entré se compte donc dans l'inventaire avant et après, jamais dans la pile passée.
- **Données du monde** : les `SavedData` du mod (`bench/WorkshopSavedData`, `economy/EconomyLedger`,
  `economy/SafeAccounts`) se rangent dans le stockage du monde principal par
  `server.overworld().getDataStorage().computeIfAbsent(load, new, KEY)`, avec une clé
  `oneforallsurvival_<nom>`. Ils ne se touchent que depuis le fil du serveur. `EconomyLedgerTest` montre
  qu'un `SavedData` se teste en JUnit sans lancer Minecraft.
- **Configuration** : les fichiers du mod sont des `ForgeConfigSpec` déclarés dans `OneForAllSurvival`
  (`COMMON` ou `SERVER`, par exemple `oneforallsurvival-economy.toml`). Le cahier du casino note qu'une
  config `SERVER` est **envoyée aux joueurs** à la connexion et propose, pour un secret, un fichier JSON lu
  par le serveur seul (`config/oneforall/alertes-discord.json`), pas encore codé. Le mod n'a aucun client
  HTTP aujourd'hui.
- **Commandes** : chaque domaine a sa classe (`EconomyCommands`, `RoleCommands`…) branchée sur
  `RegisterCommandsEvent`, avec des textes traduits (`fr_fr.json`, `en_us.json`), au vouvoiement.
- **Essais** : JUnit dans `src/test`, GameTests et E2E dans `src/devcontent` (`JoueurDeTest`,
  `E2eMonnaieServeur`), lots de 8 GameTests au plus (leçons du dépôt).

### Intégration
- Le serveur garde l'inventaire à la mort (`keepInventory`, cahier de l'économie).
- **Tout le réseau est hors du fil du serveur.** Tout ce qui touche au monde (joueur, inventaire, journal,
  commandes) **repasse sur le fil du serveur** par `server.execute(...)`.
- **Aucune bibliothèque ajoutée** : `java.net.http.HttpClient` (Java 17) et Gson, déjà fourni par Minecraft.
- Les textes affichés au joueur passent par les fichiers de langue, en français et en anglais.
- **Statuts du contrat** : le mod n'envoie et ne compare que les statuts ASCII (`a_livrer`, `reservee`,
  `livree`, `echec`, `annulee`).

## 4. Socle

### Configuration du serveur
- **Où** : un fichier lu par le serveur seul, `config/oneforall/boutique.json` (même dossier que le webhook
  du casino), créé au premier lancement avec une clé vide. **Ce n'est pas une config Forge `SERVER`**, qui
  part chez les joueurs. Il n'est jamais dans le dépôt : les dossiers `run*` sont déjà ignorés par Git,
  et le fichier de production vit sur la machine du serveur.
- **La clé** : la variable d'environnement `MINECRAFT_SERVER_API_KEY` (le nom du contrat) l'emporte sur le
  champ `cle` du fichier. Elle permet de ne jamais écrire la clé sur le disque du serveur.
- **Contenu de départ** :

  ```json
  {
    "actif": true,
    "url": "https://lostisland.eriniumlab.com/api/serveur",
    "cle": "",
    "serveur": "principal",
    "livrer": true,
    "battementSecondes": 30,
    "sondageSecondes": 60,
    "piecesMax": 1000000,
    "commandesPermises": ["give", "effect", "title", "tellraw", "lp", "experience"]
  }
  ```

  | Champ | Rôle | Bornes |
  |---|---|---|
  | `actif` | coupe toute la boutique | — |
  | `url` | base des routes | `https://` obligatoire ; `http://` accepté seulement vers `localhost`/`127.0.0.1` (essais) |
  | `cle` | clé du site, si la variable d'environnement est absente | non vide pour fonctionner |
  | `serveur` | nom envoyé dans l'en-tête `X-Serveur` | lettres, chiffres, `_ . -`, 1 à 40 caractères (contrat) ; invalide = boutique désactivée |
  | `livrer` | ce serveur livre-t-il ? Faux sur un serveur qui ne doit que battre le cœur (section 6) | — |
  | `battementSecondes` | intervalle de `/etat` | 10 à 60 (le site déclare hors ligne après 90 s) |
  | `sondageSecondes` | intervalle du sondage des joueurs en ligne | 30 à 600 |
  | `piecesMax` | plafond du mod pour une action `pieces`, en cuivres | 1 à 1 000 000 (plafond du site) |
  | `commandesPermises` | premiers mots autorisés pour l'action `commande` (feature 143) | liste vide = aucune commande |

- **Sans clé, ou `actif` faux** : la boutique est **désactivée**. Une seule ligne au journal du serveur au
  démarrage : « Boutique désactivée : aucune clé (variable MINECRAFT_SERVER_API_KEY ou
  config/oneforall/boutique.json). » Aucun appel réseau. `/boutique lier` répond « La boutique n'est pas
  branchée sur ce serveur. »
- **Fichier illisible** (JSON invalide, URL invalide, nom de serveur invalide) : désactivée, avec la cause
  au journal (sans la clé). Les valeurs numériques hors bornes sont ramenées dans les bornes, avec un
  avertissement.
- `/boutique recharger` relit le fichier et la variable sans redémarrer.

### Client HTTP
- Un `java.net.http.HttpClient` unique, créé à l'activation, avec son **propre exécuteur** (un petit
  ensemble de fils démons nommés `boutique-http`), pour ne jamais occuper le fil du serveur ni le
  `ForkJoinPool` commun. Tous les appels sont asynchrones (`sendAsync`).
- **Délais** : connexion 5 s, réponse 10 s par appel. Pas de redirection suivie (`Redirect.NEVER`) : la clé
  ne doit jamais partir vers une autre adresse.
- **En-têtes** : `Authorization: Bearer <clé>`, `X-Serveur: <serveur>` (contrat), `Content-Type:
  application/json; charset=utf-8`, `User-Agent: OneForAllSurvival/<version du mod>`.
- **Réponses, selon la table des erreurs du contrat** :

  | Réponse | Le mod |
  |---|---|
  | `200` | traite la réponse |
  | `400` | ne réessaie pas ; avertissement au journal (requête et code, sans la clé) |
  | `401` | arrête tout (section 6) ; erreur au journal une fois ; réessaie après un long délai |
  | `404`, `409` | réponses propres à chaque route (features 141, 142) ; ne réessaie pas |
  | `413` | ne réessaie pas ; erreur au journal (corps de plus de 200 000 octets : défaut du mod) |
  | `5xx`, réseau, délai dépassé, autre code | réessaie avec temporisation |

- **Temporisation exponentielle** : 2 s, 4 s, 8 s… plafonnée à 5 minutes, avec un aléa de ±20 % pour que
  les appels ne repartent pas tous ensemble. Le site n'envoie pas de `Retry-After` (contrat). Toute route
  étant idempotente, un réessai n'a jamais d'effet de bord.
- **Disjoncteur** : après 5 échecs de suite, le site est déclaré **injoignable**. Seuls le battement de
  cœur et l'envoi des résultats en attente continuent, à leur rythme de temporisation ; aucune nouvelle
  réservation n'est tentée. Le premier appel réussi le rétablit.
- **Taille** : une réponse de plus de 1 Mio est refusée sans être lue jusqu'au bout ; un corps envoyé reste
  sous 200 000 octets (limite du site), ce que garantissent les limites de la section 8.
- **Au retour**, le corps est lu et validé hors du fil du serveur ; seul le résultat, déjà converti en
  objets Java immuables, est remis au fil du serveur.
- **Arrêt du serveur** (`ServerStoppingEvent`) : plus de sondage ni de réservation ; les résultats en
  attente sont tentés une dernière fois, 5 s au plus ; le reste attend le prochain démarrage dans le
  journal. Un appel en vol dont la réponse arrive après l'arrêt est ignoré.

### Le journal des livraisons
Un `SavedData`, `BoutiqueJournal`, clé `oneforallsurvival_boutique`, sur le modèle de `EconomyLedger`.
Il garde, par identifiant de livraison (stable, même après une relance du staff : contrat) :

| Champ | Contenu |
|---|---|
| `id` | identifiant de la livraison (UUID du site) |
| `commande`, `produit` | pour l'équipe et les messages (`LI-1043`, `krokmou`) |
| `joueur` | UUID du destinataire |
| `etat` | voir ci-dessous |
| `raison` | pour un échec |
| `dates` | réservation, début, fin |
| `resultatTransmis` | vrai quand le site a accepté le résultat |

États :

```
RESERVEE ──► EN_COURS ──► LIVREE ──────────────► (résultat livree transmis)
   │             │
   │             └─(relance après panne)─► vérifié contre la marque du joueur
   └──► ECHEC (rien donné) ───────────────────► (résultat echec transmis)
PARTIELLE (quelque chose donné, puis une panne) : résultat echec « PARTIELLE … », bloquée jusqu'à l'équipe
```

Une livraison refusée à la validation, avant toute réservation, entre directement en `ECHEC`.

- **La file des résultats non transmis** est l'ensemble des entrées `LIVREE`, `ECHEC` ou `PARTIELLE` dont
  `resultatTransmis` est faux. Elle est renvoyée à chaque battement réussi et au démarrage.
- **La marque du joueur** : en plus du journal, l'identifiant de chaque livraison donnée est écrit dans les
  données persistantes du joueur (`getPersistentData()`, sous-étiquette persistée de Forge), **dans le même
  fichier que son inventaire** (`playerdata/<uuid>.dat`). Les objets donnés et la marque « donné » sont donc
  sauvegardés ensemble ou perdus ensemble. C'est ce qui permet de trancher un `EN_COURS` après un plantage
  (section 6). À vérifier au lot dans le code de la 1.20.1 : que `PlayerDataStorage` écrit ce fichier
  d'un bloc (fichier temporaire puis remplacement), et que la sous-étiquette persistée survit à la mort.
- **Taille** : les entrées dont le résultat est transmis depuis plus de 180 jours sont effacées ; la
  marque du joueur, elle, reste (quelques dizaines d'identifiants par joueur au plus, plafonnée à 2 000,
  les plus anciennes effacées d'abord). Une livraison effacée du journal mais marquée chez le joueur n'est
  toujours pas redonnée.
- **Écriture immédiate** : contrairement aux autres `SavedData`, qui attendent la sauvegarde périodique du
  monde, le journal est **écrit sur le disque tout de suite** aux deux moments de la règle de conception
  (avant de donner, après avoir donné). Le moyen exact (`SavedData.save(File)` sur le seul fichier du
  journal, ou `DimensionDataStorage.save()`) est à trancher au lot.

## 5. Features

La numérotation prolonge celle des cahiers de la chicha et des substances (dernière : 139).

**140. Battement de cœur.**
- Toutes les `battementSecondes` (30 s), `POST /etat` avec la liste des joueurs connectés (UUID avec
  tirets, pseudo en jeu), le nombre maximal de joueurs du serveur, la version du jeu (`1.20.1`, champ
  `version`), le MOTD sans ses codes de couleur `§`, et la version du mod dans le champ `mod` (le site ignore
  les champs inconnus, et le contrat cite celui-ci). Le site s'en sert aussi pour suivre les changements de
  pseudo des comptes liés.
- La liste est relevée sur le fil du serveur, puis envoyée hors de lui. L'horloge est l'heure réelle, pas
  les tics : un serveur qui rame continue de battre.
- Un battement raté n'est pas réessayé : le suivant le remplace. Le journal du serveur note **le premier**
  échec d'une coupure et le retour (« Boutique : le site répond de nouveau après 4 min »), jamais un message
  toutes les 30 s.
- Un battement réussi déclenche l'envoi des résultats en attente (feature 142).
- Le battement part même si `livrer` est faux : le site doit voir le serveur.

**141. Liaison de compte.**
- Le joueur prend un code de 6 caractères dans « Mon compte » sur le site (valable 10 minutes, une seule
  fois), puis tape `/boutique lier <code>`. Commande ouverte à tous (permission 0), réservée à un joueur
  (pas à la console).
- Le code est mis en majuscules (le site accepte les deux casses) et vérifié sur place : exactement 6
  caractères de l'alphabet du site, lettres et chiffres **sans `0`, `O`, `1`, `I`**. Sinon « Ce code n'a pas
  la bonne forme : il fait 6 lettres ou chiffres, sans 0, O, 1 ni I. » sans appel au site.
- **Limite** : un essai toutes les 10 s par joueur, et 5 codes refusés en 10 minutes bloquent la commande
  10 minutes pour lui (« Trop d'essais. Réessayez dans quelques minutes. »).
- Le mod envoie `POST /liaison` avec le code, l'UUID et le pseudo **actuel** du joueur. Pendant l'appel :
  « Liaison en cours… ».
- Réponses, revenues sur le fil du serveur (si le joueur s'est déconnecté entre-temps, rien n'est affiché,
  la liaison reste faite côté site) :

  | Réponse du site | Message au joueur |
  |---|---|
  | `200`, `discord` = `tomtom` | « Votre compte est lié au compte Discord tomtom. Vos achats arriveront ici. » |
  | `404` | « Code inconnu ou expiré. Demandez-en un nouveau dans « Mon compte » sur le site. » |
  | `400` | « La liaison a échoué. Si cela se reproduit, prévenez l'équipe. » (UUID ou pseudo refusé : avertissement au journal) |
  | `401` | « La boutique est indisponible pour l'instant. » (et l'erreur de la section 6) |
  | injoignable après réessais (30 s au plus pour cette commande) | « Le site ne répond pas. Réessayez dans un moment. » |
  | autre | « La liaison a échoué. Si cela se reproduit, prévenez l'équipe. » (code HTTP au journal) |

- Le pseudo Discord est nettoyé avant affichage (codes `§`, caractères de contrôle, 32 caractères au plus).
- Le journal du serveur note la liaison avec le pseudo et le code **masqué** (`K7****`).
- Une liaison réussie lance aussitôt un sondage de ce joueur (feature 142) : un achat fait avant la liaison
  arrive tout de suite.

**142. Livraison automatique.**

*Quand le mod cherche des livraisons*
- **À la connexion** d'un joueur, 5 s après (le temps que le joueur soit chargé) :
  `GET /livraisons?uuid=<uuid>&limite=50`. Le site renvoie ses livraisons `a_livrer` et ses `reservee`
  dont la réservation a expiré.
- **Toutes les `sondageSecondes`** (60 s) pour chaque joueur en ligne, par la même route avec `uuid`, comme
  le demande le contrat (pas de pagination côté site). Les appels sont étalés sur la minute (60 joueurs :
  un appel par seconde) plutôt que tirés d'un bloc.
- **Au démarrage** : envoi de la file des résultats en attente, puis `GET /livraisons?limite=200` sans
  `uuid`, pour **rapprocher le journal** : une livraison que le journal marque `LIVREE` mais que le site
  propose encore reçoit son résultat `livree`, sans rien donner. Les autres attendent la connexion de leur
  joueur. Sans pagination, cette liste peut être tronquée ; ce n'est pas grave, la file des résultats et le
  sondage par joueur suffisent à la sûreté.
- `/boutique sonder [joueur]` force un sondage (feature 145).
- Une livraison n'est traitée que si son joueur est **en ligne, vivant, et entièrement connecté**, et si son
  `joueur.uuid` est celui du joueur sondé. Un joueur mort (écran de réapparition) est repris au sondage
  suivant. Le `joueur.pseudo` du site n'est qu'indicatif (contrat) : le mod livre à l'UUID.
- **Une seule livraison à la fois par joueur**, dans l'ordre du site (les plus anciennes d'abord), et une
  livraison déjà en cours de traitement n'est jamais reprise par un second sondage (ensemble « en vol » en
  mémoire, sur le fil du serveur).

*Avant de réserver*
- Si le journal ou la marque du joueur disent déjà que la livraison a été donnée : **rien n'est donné**, le
  résultat `livree` est (r)envoyé ; le site l'accepte toujours (contrat).
- Si le journal la dit `PARTIELLE` : rien n'est donné, rien n'est réservé, l'équipe est prévenue
  (feature 145).
- **Valider toutes les actions** (feature 143), sur le fil du serveur, sans rien donner. Une seule action
  invalide fait échouer la livraison entière : journal `ECHEC` avec la raison, résultat `echec` envoyé
  **sans réserver** (le contrat l'accepte), **rien n'est donné**.

*L'ordre strict d'une livraison*
1. **Réserver** : `POST /livraisons/{id}/reserver`, hors du fil du serveur. Toute autre réponse que `200`
   arrête là (section 6). Le journal note `RESERVEE`.
2. **Revenir sur le fil du serveur** et revérifier : le joueur est toujours en ligne et vivant, la livraison
   n'est pas donnée, les actions sont toujours valides (le registre et la place du colis ont pu changer).
   Sinon on s'arrête là, rien n'a été donné (la réservation expirera seule) ; une action devenue invalide
   donne un `ECHEC` comme ci-dessus.
3. **Écrire « en cours »** : journal `EN_COURS`, **écrit sur le disque** tout de suite. Si l'écriture
   échoue, on s'arrête sans rien donner.
4. **Donner** : les actions dans l'ordre du site (feature 143). Ce qui ne rentre pas dans l'inventaire va
   au colis (feature 144). Tout se fait dans le même tic, sur le fil du serveur : le joueur ne peut pas se
   déconnecter au milieu.
5. **Écrire « livrée »** : la marque dans les données du joueur, puis le journal `LIVREE`.
6. **Sauvegarder** : le fichier du joueur (inventaire, colis et marque ensemble), le registre de
   l'économie s'il y a eu des pièces, puis le journal. Le moyen de sauvegarder le seul joueur
   (`PlayerList.save` est protégé : transformateur d'accès, ou `PlayerList.saveAll()`) est à trancher au lot.
7. **Envoyer le résultat** : `POST /livraisons/{id}/resultat` avec `{"statut": "livree", "livreeLe": <heure
   UTC>}`. Sur `200`, `resultatTransmis` passe à vrai (le journal suivra la sauvegarde normale). Sinon le
   résultat reste dans la file et repart plus tard. `livree` est accepté par le site même si la livraison a
   été annulée entre-temps : l'objet est dans le jeu, le site l'enregistre tel quel.

*Le joueur*
- À la fin de l'étape 6 : « Votre achat « Krokmou » est arrivé (commande LI-1043). », avec le son de
  ramassage d'un objet. Si une partie est au colis : « Votre inventaire était plein : le reste vous attend
  dans votre colis. Tapez /boutique colis pour le récupérer. »
- Un échec : « Votre achat « Krokmou » n'a pas pu être livré. L'équipe est prévenue et va s'en occuper. »
  La raison technique n'est pas montrée au joueur ; elle est au journal du serveur et sur le site.
- Le nom du produit vient du site : nettoyé (codes `§`, contrôles) et coupé à 48 caractères.

**143. Les actions.**
Toutes les actions d'une livraison sont **validées avant d'en exécuter une seule** : la livraison est
atomique vu du site (contrat). La validation ne change rien au monde.

*Validation commune*
- Au plus 32 actions par livraison ; un champ `type` connu (`objet`, `commande`, `pieces`), sinon échec
  « action inconnue : <type> » (texte du contrat).
- Un `id` qui n'est pas un UUID fait ignorer la livraison (on ne pourrait même pas en rendre le résultat),
  avec un avertissement au journal.
- Les raisons envoyées au site font 500 caractères au plus (contrat).

*`objet`*
- `objet` est un identifiant de registre valide (`espace:chemin`), présent dans le registre des objets de
  Forge, et n'est pas `minecraft:air`. Sinon : « objet inconnu : <identifiant> » (exemple du contrat).
- `quantite` est un entier de 1 à 6 400 (contrat). Sinon : « quantité invalide : <valeur> ».
- `nbt` est `null` ou une chaîne SNBT de 32 Kio au plus, lue par l'analyseur SNBT du jeu (`TagParser`) en
  une étiquette composée. Sinon : « NBT invalide pour <identifiant> ».
- **Don** : en piles de la taille maximale de l'objet, l'étiquette appliquée à chaque pile. Ce qui entre se
  compte dans l'inventaire avant et après (piège du créatif, section 3) ; le reste va au colis.

*`commande`*
- `commande` est une chaîne d'une seule ligne, de 500 caractères au plus, sans `/` en tête (contrat).
- `{joueur}` est remplacé par le **pseudo en jeu du joueur au moment de la livraison** (contrat), `{uuid}`
  par son UUID avec tirets. Aucune autre substitution.
- Le **premier mot** doit être dans `commandesPermises`. Le site est de confiance, mais une administration
  piratée ou une faute de frappe ne doit pas pouvoir faire `stop`, `op` ou `deop`. Sinon : « commande non
  permise : <premier mot> ».
- La commande est **analysée** par le répartiteur du serveur avant toute exécution : une erreur de syntaxe
  ou un argument non lu fait échouer la livraison (« commande invalide : <message du jeu> »), rien donné.
- **Exécution** : au nom du serveur, **permission 4**, à la position et dans la dimension du joueur (pour
  que `~ ~ ~` et `@p` visent l'acheteur), sortie captée et écrite au journal du serveur (« Boutique LI-1043 :
  give Naufrage_Tom minecraft:apple 1 → Gave 1 [Apple] to Naufrage_Tom »), jamais affichée aux joueurs.
- Une commande qui **lève une erreur à l'exécution** alors que d'autres actions ont déjà été faites rend la
  livraison `PARTIELLE` (feature 145). Le résultat envoyé au site est `echec`, avec une raison qui
  **commence par `PARTIELLE`**, comme le demande le contrat : « PARTIELLE : <action en erreur> ; déjà
  donné : <actions faites>. » Une commande qui réussit en renvoyant 0 n'est pas une erreur ; la valeur est
  notée au journal.
- Pour l'équipe du site : préférer des commandes **sans effet si on les rejoue** (« donner le grade » plutôt
  que « ajouter un jour de grade ») : c'est la meilleure protection contre le risque résiduel de la
  section 6.

*`pieces`* — **à trancher par Killian** (section 13). Proposition :
- `montant` est un entier de 1 à `piecesMax` (1 000 000 au plus, plafond du site), **en cuivres**, la plus
  petite unité de la monnaie du mod (contrat). Au-delà du plafond du mod : échec « montant au-delà du
  plafond du serveur : <valeur> » (le contrat permet ce plafond plus bas). Sinon : « montant invalide :
  <valeur> ».
- **Don** : `CoinMath.decompose(montant)`, puis `PlayerCoins.receive` coupure par coupure (bourse d'abord,
  puis inventaire) ; ce qui n'est pas entré va au colis, **pas au sol** : une pièce au sol vit dans les
  données du tronçon, pas dans le fichier du joueur, et échapperait à la règle « donné et marqué ensemble ».
- **Comptage** : `EconomyLedger.created(MoneySource.SHOP, montant, null, "boutique LI-1043 <id>")`, avec
  une cinquième source `SHOP` (« Boutique ») à ajouter à `MoneySource`, et sa ligne au tableau de bord
  `/eco stats`. **Cela modifie la règle des quatre sources du cahier de l'économie.** Tant que Killian n'a
  pas tranché, l'action `pieces` est refusée (« action non prise en charge : pieces »), sans rien donner, et
  le reste du cahier tient.

**144. Colis.**
- Ce qui ne rentre pas dans l'inventaire (ou la bourse, pour les pièces) n'est **ni jeté au sol ni perdu** :
  il est mis dans le **colis** du joueur, gardé par le serveur dans ses données persistantes, avec la marque
  « donné » (feature 142). La livraison compte comme **livrée** une fois en colis.
- `/boutique colis` donne tout ce qui rentre, dans l'ordre d'arrivée, et garde le reste : « Colis : 3 objets
  récupérés, 2 restent (inventaire plein). » Sans colis : « Votre colis est vide. »
- À chaque connexion, s'il n'est pas vide : « Un colis vous attend : /boutique colis. »
- On ne peut **rien déposer** dans un colis : ce n'est pas un rangement. Il s'ajoute pourtant à la liste des
  endroits où des pièces peuvent se trouver (feature 84 de l'économie), comme le coffre-fort ; une pièce ne
  peut y entrer que par une livraison. Lié à la décision sur `pieces`.
- Taille : 256 piles au plus. La validation (feature 142) vérifie que l'inventaire plus la place libre du
  colis suffisent ; sinon la livraison échoue **avant** de rien donner (« colis plein »).

**145. Commandes de l'équipe et alertes.**
Permission 2, comme `/eco`, sauf mention.

| Commande | Effet |
|---|---|
| `/boutique statut` | Activée ou non (et pourquoi), clé « présente » ou « absente » (jamais la clé), nom `X-Serveur`, `livrer`, site joignable ou non et depuis quand, dernier battement réussi, résultats en attente, livraisons `EN_COURS` et `PARTIELLE`, dernière erreur. |
| `/boutique sonder [joueur]` | Sondage immédiat d'un joueur, ou de tous les joueurs en ligne. |
| `/boutique journal [joueur] [page]` | Les livraisons du journal, les plus récentes d'abord : commande, produit, joueur, état, date. |
| `/boutique colis <joueur>` | Ce que contient le colis d'un joueur en ligne. |
| `/boutique renvoyer` | Envoie tout de suite les résultats en attente. |
| `/boutique recharger` | Permission 4. Relit la configuration. |
| `/boutique debloquer <id> livree\|rien` | Permission 4. Tranche une livraison `PARTIELLE` ou `EN_COURS` douteuse après vérification à la main : `livree` la clôt comme livrée (résultat `livree` envoyé) ; `rien` l'efface du journal et de la marque du joueur, pour que la relance du staff sur le site la redonne en entier. Noté au journal du serveur avec l'auteur. |

- **Alertes** : les membres de l'équipe connectés (opérateurs de niveau 2 et plus) reçoivent un message
  pour une livraison `PARTIELLE`, un `EN_COURS` qui n'a pu être tranché, un `401`, un `409 reservee` (autre
  serveur), et la panne du site au-delà de 10 minutes. Un membre qui se connecte voit un rappel s'il reste
  une livraison `PARTIELLE`.
- Le même message part, si le fichier existe un jour, par le webhook Discord du cahier du casino
  (feature 121) ; pas de dépendance à ce lot.

## 6. Pannes et reprise

Chaque cas, avec le comportement exact.

| Cas | Comportement |
|---|---|
| **Joueur hors ligne** | Rien n'est réservé. La livraison reste `a_livrer` sur le site ; elle arrive à sa connexion (`?uuid=`). |
| **Joueur qui se déconnecte pendant la livraison** | Le don (étapes 3 à 6) tient dans un seul tic du fil du serveur : la déconnexion tombe avant ou après, jamais au milieu. Pendant la réservation en vol : la revérification de l'étape 2 arrête tout, rien n'est donné, le journal reste `RESERVEE`. À sa reconnexion, le mod re-réserve (le `200` prolonge, ou réserve de nouveau après expiration) et livre. Après l'étape 6 : le message ne s'affiche pas, le résultat part quand même. |
| **Serveur éteint** | Rien ne se perd : les livraisons attendent sur le site. Au démarrage, les résultats en attente sont envoyés et le journal est rapproché (feature 142). |
| **Serveur qui plante entre « donner » et « sauvegarder »** | Le journal sur le disque dit `EN_COURS`. À la connexion du joueur, son fichier est chargé et le mod regarde **sa marque** : présente, les objets ont été sauvegardés avec elle, la livraison passe `LIVREE` et le résultat `livree` part ; absente, les objets ont été perdus avec le plantage (même fichier, même sauvegarde), le journal repasse à `RESERVEE` et la livraison est refaite après une nouvelle réservation. Il n'y a donc ni double ni perte **pour les objets, les pièces et le colis**. **Risque résiduel, dit honnêtement** : (1) une action `commande` dont l'effet vit ailleurs que dans le fichier du joueur (grade d'un autre mod, base de LuckPerms, blocs du monde) peut avoir survécu au plantage alors que la marque non : elle sera **rejouée**. Réduit par l'écriture immédiate (la fenêtre est de quelques millisecondes) et par des commandes sans effet au second passage (feature 143). (2) Le registre de l'économie peut avoir compté une création perdue, ou l'inverse : écart du tableau de bord égal au montant, noté au journal du serveur. (3) Si l'écriture du fichier du joueur n'est pas d'un bloc (à vérifier, section 4), un fichier à moitié écrit relève de la panne du monde, pas de ce lot. Un `EN_COURS` que le mod ne peut pas trancher (fichier du joueur illisible) devient `PARTIELLE` (résultat `echec` « PARTIELLE : panne du serveur pendant la livraison ») et une alerte de l'équipe, jamais un nouveau don. |
| **Site injoignable** | Aucune réservation, donc aucun don. Les résultats déjà acquis restent dans la file du journal et repartent au premier appel réussi. Le jeu ne ralentit pas (tout est hors du fil du serveur). Au-delà de 10 minutes : alerte de l'équipe. |
| **Réservation refusée `409 annulee`** | Rien n'est donné ; l'entrée est effacée du journal. Rien n'est dit au joueur (le remboursement lui a été annoncé par le site). |
| **Réservation refusée `409 livree`** | Rien n'est donné. Si le journal ou la marque la disent donnée, le mod renvoie `resultat livree` (sans effet, contrat), marque le résultat transmis et l'oublie. Sinon : avertissement au journal du serveur (« livrée par un autre serveur ? ») et l'entrée est oubliée. |
| **Réservation refusée `409 echec`** | Rien n'est donné : la livraison attend une relance du staff sur le site. Le journal garde son état ; elle reviendra `a_livrer` après la relance. |
| **Réservation refusée `409 reservee`** | Un **autre** serveur tient la réservation : rien n'est donné, avertissement au journal du serveur et alerte de l'équipe (deux serveurs livrent avec la même clé). Le mod ne réessaie pas avant le sondage suivant. |
| **Résultat `echec` refusé `409 livree`** | Le site sait la livraison livrée (par ce serveur ou un autre) : le résultat est retiré de la file, avertissement au journal du serveur. |
| **`404`** | Livraison inconnue : oubliée, avertissement au journal du serveur. Pour un résultat, un `404` vide l'entrée de la file (le site ne la connaît plus). |
| **`400`, `413`** | Requête refusée : pas de réessai, erreur au journal du serveur. Pour un résultat, l'entrée reste dans la file, marquée en erreur, visible dans `/boutique statut` (défaut du mod à corriger). |
| **`401`** | Clé refusée : erreur au journal du serveur, une fois (« Boutique : clé refusée par le site (401). Vérifiez MINECRAFT_SERVER_API_KEY. »), alerte de l'équipe. Plus de sondage ni de réservation ; un battement toutes les 5 minutes vérifie si la clé a été corrigée côté site. `/boutique recharger` relance tout de suite. Les résultats en attente sont gardés. |
| **Inventaire plein** | Le reste va au colis (feature 144). La livraison est `livree`. |
| **Objet, commande ou action inconnus** | Échec à la validation, **rien n'est donné**, résultat `echec` avec la raison, sans réservation. Le staff corrige le produit sur le site et relance la livraison ; elle revient `a_livrer` avec le même `id` et le journal, qui la dit `ECHEC`, la laisse livrer. |
| **Erreur à l'exécution d'une commande après d'autres actions** | `PARTIELLE` (feature 143) : résultat `echec` « PARTIELLE : … », alerte de l'équipe. Si le staff relance sur le site sans être passé par `/boutique debloquer`, le mod ne redonne rien : il renvoie `echec` « PARTIELLE : à débloquer en jeu avant relance » et prévient l'équipe. |
| **Redémarrage pendant une réservation** | Journal `RESERVEE` (rien donné) : à la connexion du joueur, re-réservation puis livraison. Journal `EN_COURS` : voir le plantage ci-dessus. Le journal suffit à reprendre, que la réservation ait expiré ou non. |
| **Deux serveurs avec la même clé** | Chacun envoie son nom `X-Serveur` ; une livraison réservée par l'un est refusée à l'autre (`409 reservee`) tant que la réservation court (contrat). **Reste un trou** : si le premier a donné mais n'a pas pu transmettre son résultat dans les 10 minutes, la réservation expire et le second peut livrer de nouveau (la marque du joueur, propre à chaque monde, ne l'empêche pas). Mesure côté mod : un seul serveur a `livrer` à vrai ; les autres ne font que battre le cœur, et chaque serveur a un nom `X-Serveur` distinct (section 13). |
| **Restauration du monde depuis une sauvegarde** | Journal et fichiers des joueurs reviennent ensemble à la date de la sauvegarde : les livraisons faites depuis sont proposées de nouveau par le site si leur résultat n'avait pas été transmis ; sinon elles sont `livree` sur le site et perdues en jeu. L'équipe les relance à la main. Hors de portée du mod, à écrire dans la fiche du wiki. |

## 7. Messages et journaux

- **Au joueur** : français et anglais par les fichiers de langue, au vouvoiement, sans jargon (ni « 409 » ni
  « UUID »). Les textes de la section 5 sont les textes de départ.
- **Journal du serveur**, préfixe `Boutique :` :
  - `INFO` : activation (URL et nom `X-Serveur`, sans la clé), liaison (pseudo, code masqué), chaque
    livraison donnée (commande, produit, joueur, contenu), chaque sortie de commande, retour du site après
    une panne ;
  - `WARN` : livraison refusée ou inconnue, `409 reservee`, `400`, valeur hors bornes, échec d'une
    livraison avec sa raison, données du site invalides ;
  - `ERROR` : `401`, `413`, livraison `PARTIELLE`, `EN_COURS` non tranchable, échec d'écriture du journal.
- **Jamais au journal** : la clé, l'en-tête `Authorization`, un code de liaison en clair, un corps de
  réponse complet (seulement les champs utiles, coupés).

## 8. Sécurité

- **La clé** ne sort jamais du serveur que dans l'en-tête `Authorization` d'un appel HTTPS vers l'URL
  configurée. Pas de redirection suivie. La classe de configuration ne l'affiche pas dans son `toString`.
  `/boutique statut` dit « présente ». Le fichier `boutique.json` n'est ni synchronisé aux joueurs ni dans
  le dépôt.
- **Le site est de confiance, pas infaillible** : toute donnée reçue est validée avant usage (section 5),
  les champs inconnus sont ignorés, les champs manquants ou d'un mauvais type font échouer la livraison
  concernée sans toucher aux autres.
- **Limites** : réponse reçue 1 Mio ; 200 livraisons par réponse ; 32 actions par livraison ; SNBT 32 Kio ;
  commande 500 caractères (contrat) ; textes affichés (produit, Discord) nettoyés et coupés ; raison
  envoyée au site 500 caractères (contrat) ; corps envoyé sous 200 000 octets (contrat).
- **Commandes** : liste de premiers mots permis, analyse avant exécution, permission 4 seulement pour ce qui
  vient de la boutique, sortie jamais montrée aux joueurs.
- **Liaison** : limite d'essais par joueur, code masqué dans les journaux.

## 9. Branchements

| Avec | Branchement |
|---|---|
| Économie | Si Killian l'accepte : une cinquième source de création, `SHOP`, comptée au tableau de bord, et le colis ajouté aux endroits où une pièce peut se trouver (feature 84). |
| Site Lost Island | Le contrat `api-serveur-minecraft.md`, version révisée du 2026-11-13. Les points encore ouverts (section 13) s'y règlent, côté site. |
| Casino | Le dossier `config/oneforall/` et, plus tard, le webhook Discord des alertes (feature 121). |
| Wiki | Une fiche « Boutique » : lier son compte, le colis, et pour l'équipe les commandes, les cas de panne, la règle des commandes rejouables et le passage par `/boutique debloquer` avant de relancer une livraison `PARTIELLE`. |

## 10. Découpage conseillé

| Lot | Contenu | Ce qu'il prouve | Après |
|---|---|---|---|
| B1 | Configuration, client HTTP (délais, table des erreurs, temporisation, disjoncteur), 140, `/boutique statut` et `recharger` | Le site voit le serveur en ligne ; sans clé, rien ne part ; le jeu ne rame pas quand le site tombe | — |
| B2 | 141 | Un joueur lie son compte en jeu | B1 |
| B3 | Journal, marque du joueur, file des résultats, 142, actions `objet`, 144, reste de 145 | Un achat d'objets arrive une seule fois, malgré pannes et inventaire plein | B1 |
| B4 | Action `commande` (liste permise, analyse, `PARTIELLE`, `debloquer`) | Un grade ou une commande s'achète sans risque de `stop` | B3 |
| B5 | Action `pieces`, source `SHOP` | Des pièces s'achètent et sont comptées au tableau de bord | B3, décision de Killian |
| B6 | Essai de bout en bout contre le site | Le circuit complet marche avec le vrai site | B3 (B4, B5 s'ils sont faits) |

## 11. Vérification

- **Lancement réel obligatoire à chaque lot.** Tout lancement de Minecraft (serveur, client, GameTests,
  E2E) passe par le verrou partagé : lire `C:\Users\killi\.claude\lancement-minecraft.txt`, s'inscrire dans
  la file s'il est pris, le prendre, et le **vider** à la fin, réussite ou échec. Ce cahier ne lance rien.
- **Tests unitaires (JUnit, sans Minecraft lancé)**, comme `EconomyLedgerTest` :
  - journal : chaque transition permise, et le refus des autres (pas de `LIVREE` sans `EN_COURS`, pas de
    don si `LIVREE` ou `PARTIELLE`) ; sauvegarde et relecture à l'identique ; purge à 180 jours qui garde les
    résultats non transmis ;
  - file des résultats : un résultat non transmis survit à une relecture ; un `200` le retire ; un `404` le
    retire ; un `409 livree` sur un `echec` le retire ; un `5xx` le garde ; un `400` le garde marqué en
    erreur ;
  - temporisation : 2, 4, 8 s… plafonnée à 5 min, aléa dans ±20 % ; `400`, `401`, `404`, `409`, `413` jamais
    réessayés ;
  - lecture et validation du JSON du site : livraison du contrat acceptée ; `type` inconnu, `quantite` 0 ou
    6 401, `montant` 0 ou au-delà de `piecesMax`, SNBT invalide, commande de 501 caractères ou sur deux
    lignes, `id` non UUID, champ manquant, champ de mauvais type, réponse de plus de 1 Mio refusés avec la
    bonne raison ; champ inconnu ignoré ; statuts lus en ASCII ;
  - raison `PARTIELLE` : commence bien par `PARTIELLE`, coupée à 500 caractères ;
  - substitution de `{joueur}` et `{uuid}`, refus d'un premier mot hors liste, d'un `/` en tête ;
  - code de liaison : alphabet sans `0 O 1 I`, casse indifférente, limite d'essais ;
  - configuration : clé absente → désactivée ; variable d'environnement prioritaire ; `http://` refusé hors
    `localhost` ; nom `X-Serveur` invalide refusé ; la clé n'apparaît dans aucun `toString` ni message
    produit.
- **Client HTTP contre un faux site** (JUnit) : un `com.sun.net.httpserver.HttpServer` du JDK sur
  `127.0.0.1` joue les réponses du contrat (`200`, `400`, `401`, `404`, `409` des quatre sortes, `413`,
  `500`, réponse lente, connexion coupée) ; on vérifie les en-têtes `Authorization` et `X-Serveur`,
  l'absence de redirection suivie, les délais, et qu'un site lent ne bloque pas l'appelant.
- **GameTests** (8 par lot au plus), avec le faux site et un `JoueurDeTest` :
  - une livraison d'objets arrive, la marque est posée, le journal dit `LIVREE`, le résultat `livree` part ;
  - la même livraison proposée deux fois (sondage et connexion en même temps, puis de nouveau après
    `LIVREE`) n'est donnée qu'une fois ;
  - inventaire plein : le reste est au colis, `/boutique colis` le rend, la livraison est `livree` ;
  - colis plein : échec avant tout don ;
  - un objet inconnu au milieu de trois actions : rien n'est donné, aucune réservation, résultat `echec`
    avec « objet inconnu » ;
  - `409 annulee`, `409 echec`, `409 reservee` : rien n'est donné ; `401` : plus de réservation, alerte ;
  - joueur retiré de la liste entre la réservation et le retour : rien n'est donné, journal `RESERVEE` ;
  - plantage simulé : journal `EN_COURS` avec marque présente → `LIVREE` sans don ; sans marque → refaite ;
  - joueur en créatif : le compte de ce qui est entré est juste ;
  - action `commande` : `{joueur}` remplacé par le pseudo en jeu, sortie au journal, commande hors liste
    refusée, erreur d'exécution → `PARTIELLE` et raison qui commence par `PARTIELLE` ;
  - action `pieces` (si Killian l'accepte) : le montant arrive (bourse, puis inventaire, puis colis) et
    `/eco stats` le compte à la source Boutique ; sinon, refus sans rien donner.
- **Essai de bout en bout contre le site** (lot B6), sur un serveur dédié avec un client, `livrer` à vrai,
  contre une instance du site (locale, ou de production avec un produit d'essai à 0 € masqué) :
  1. démarrer le serveur : le site l'affiche en ligne en moins de 30 s ; l'arrêter : hors ligne après 90 s ;
  2. se connecter, lier le compte par un code pris sur le site ; un mauvais code donne le bon message ;
  3. acheter deux unités d'un produit à trois actions : deux livraisons arrivent, une fois chacune, et le
     site les marque `livree` ;
  4. acheter, inventaire plein : colis, puis `/boutique colis` ;
  5. acheter avec le joueur déconnecté : rien ; se connecter : la livraison arrive ;
  6. couper le réseau du serveur (ou mettre une mauvaise URL) pendant un achat : rien n'est donné, le jeu
     ne ralentit pas ; rétablir : la livraison arrive ;
  7. tuer le processus du serveur juste après une livraison (point d'arrêt d'essai entre l'étape 4 et
     l'étape 6, propriété `oneforallsurvival.*` de l'E2E) : au redémarrage, la livraison n'est ni
     doublée ni perdue ;
  8. un second serveur avec la même clé et un autre `X-Serveur` : il reçoit `409 reservee` sur une
     livraison réservée par le premier ;
  9. mauvaise clé : `401` au journal, sans la clé ; `/boutique statut` le dit ;
  10. relire tous les journaux du serveur : aucune trace de la clé ni d'un code de liaison en clair.
- **Scénario d'abus** : tenter de faire livrer deux fois (double connexion rapide, `/boutique sonder` en
  boucle, relance du serveur au milieu), de faire exécuter `op` par un produit, de lier le compte d'un autre
  en devinant des codes, et de ranger quelque chose dans un colis. Aucune tentative ne doit réussir.

## 12. Décisions et choix

### Fixé par le contrat d'API du site (révisé le 2026-11-13)
- Le serveur appelle le site, jamais l'inverse ; pas de port ouvert.
- Clé `MINECRAFT_SERVER_API_KEY` en `Bearer`, nom du serveur en `X-Serveur`, routes idempotentes.
- Statuts ASCII : `a_livrer`, `reservee`, `livree`, `echec`, `annulee`.
- Battement toutes les 30 s ; sondage par `?uuid=` à la connexion et toutes les 60 s ; pas de pagination.
- Réserver juste avant de donner ; réservation de 10 minutes ; `409 reservee` pour un autre serveur,
  `409 echec` tant que le staff n'a pas relancé ; ne jamais livrer un joueur hors ligne.
- `livree` toujours accepté ; `echec` accepté sans réservation ; `id` stable après relance.
- Livraison atomique : tout vérifier avant d'exécuter ; panne au milieu → `echec` dont la raison commence
  par `PARTIELLE`.
- Trois actions : `objet`, `commande` (500 caractères, `{joueur}` = pseudo en jeu), `pieces` (plus petite
  unité, 1 à 1 000 000, plafond du mod possible) ; un type inconnu fait échouer sans rien donner.
- Liaison par `/boutique lier <code>`, code de 6 caractères sans `0 O 1 I`, casse indifférente, valable
  10 minutes, une seule fois.
- Table des erreurs : `400` et `413` sans réessai, `401` long délai, `5xx` temporisation ; pas de
  `Retry-After`.

### Demandé pour ce cahier
- Ordre réserver → « en cours » → donner → « livrée » → sauvegarder → envoyer, journal persistant, file des
  résultats non transmis.
- Proposer un colis pour l'inventaire plein, récupérable par `/boutique colis`, la livraison comptant comme
  livrée une fois en colis.

### Choix faits par l'auteur du document
Ces choix n'ont pas été validés par Killian et peuvent être contestés :
- la clé dans un fichier JSON du serveur seul ou dans la variable d'environnement, jamais dans une config
  Forge ;
- la **marque dans le fichier du joueur**, pour trancher un plantage entre « donner » et « sauvegarder » ;
- valider avant de réserver, et revalider après ;
- la liste des commandes permises, et ses valeurs de départ ;
- l'état `PARTIELLE` bloquant et la commande `/boutique debloquer` avant toute relance ;
- les pièces en trop au colis plutôt qu'au sol ;
- le colis gardé dans les données du joueur, 256 piles au plus ;
- la temporisation (2 s à 5 min), le disjoncteur après 5 échecs, la purge du journal à 180 jours ;
- un seul serveur qui livre (`livrer`), les autres ne font que battre ;
- le nom `X-Serveur` par défaut `principal`.

## 13. Questions ouvertes

**À trancher par Killian**
1. **L'action `pieces`** : la boutique peut-elle créer des pièces ? Le cahier de l'économie dit « exactement
   quatre sources ». Une cinquième, « Boutique », change ce principe (et ouvre l'achat de monnaie du jeu
   contre de l'argent réel). La proposition de la feature 143 reste en l'état ; sans décision, `pieces` est
   refusée et le lot B5 n'est pas fait.
2. **Le colis** peut-il contenir des pièces (exception à la feature 84) ? Lié à la question 1.
3. Les **commandes permises** de départ conviennent-elles (`give`, `effect`, `title`, `tellraw`, `lp`,
   `experience`) ?

**Points du contrat d'API encore ouverts, à régler côté site** (le mod suit les choix prudents ci-dessus)
1. **Relance d'une livraison `PARTIELLE`** : le contrat demande au staff de vérifier en jeu avant de
   relancer, mais ne dit pas ce que la relance doit livrer. Une fois relancée (`a_livrer`, même `id`), le mod
   ne sait pas si le staff veut tout redonner ou rien. Le mod exige `/boutique debloquer` en jeu d'abord ;
   à écrire dans le contrat (ou prévoir sur le site une relance « clore comme livrée »).
2. **Deux serveurs, réservation expirée** : si un serveur a donné mais n'a pas pu transmettre son résultat
   dans les 10 minutes, la réservation expire et un autre serveur peut livrer de nouveau. Le contrat pourrait
   réserver la reprise d'une réservation expirée au serveur qui la tenait, ou le dire explicitement comme
   limite (le mod ne fait livrer qu'un serveur).
3. **Limite d'essais de liaison côté site** : non décrite. Seul le serveur appelle `/liaison` (il a la
   clé) et le mod limite déjà par joueur ; reste à dire si le site limite aussi, par UUID, en cas de
   serveur mal réglé.
4. **`429`** : absent de la table des erreurs. Le mod le traite comme un `5xx` (temporisation).
