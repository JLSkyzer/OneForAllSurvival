# Vérifications du lot T3 : appels, messages et urgences

Plan : [2026-11-27-lot-t3-appels-messages](../superpowers/plans/2026-11-27-lot-t3-appels-messages.md).
Lot **en cours** : les appels (feature 93, haut-parleur compris), les urgences (95) et leur repli Minecraft sont
écrits et vus en jeu ; les **messages (96)**, le bout en bout sur serveur dédié avec le pack, l'essai de la voix à
deux clients et le wiki restent à faire.

## Ce qui est livré

| Partie | Commits | Contenu |
|---|---|---|
| Condition d'entrée | `21571d1`, `b497198` | Le blocage du code double à chaque série ratée (60 s à 1920 s puis `lockoutMaxSeconds`, 3600 s) ; niveau sauvegardé, remis à 0 par un bon code ou un effacement ; temps affiché en minutes au-delà de 119 s |
| Carnet de la carte | `fafe650`, `b668234` | 100 contacts et 50 récents par carte, suivant son identifiant ; au plus 2048 carnets (ceux de cartes désactivées partent d'abord) |
| Appels (serveur) | `ae76996`, `9fb39cb` | Composer, sonner, décrocher, refuser, raccrocher ; fin sur départ, mort, changement de dimension, téléphone ou carte retirés, zéro barre, arrêt ; trafic d'antenne fermé en un seul point ; 10 compositions par minute |
| Paquets et sonnerie | `fa3d996`, `a84aa0c` | Protocole `12` ; sonnerie audible à 8 blocs, bip pour l'appelant ; batterie consommée pendant l'appel, écran fermé compris |
| Application Téléphone | `27e7b54`, `b494946`, `e1cedf7` | Récents, contacts, clavier, appel entrant (verrouillé aussi), sortant, en cours, île dynamique, message de fin |
| Urgences | `09dffa8`, `4323ee0`, `d575ad8` | 17 et 15 sans carte, écran verrouillé compris ; secours connectés avec le rôle, premier qui répond ; position aux seuls secours ; « Aucun service disponible », « Aucune réponse » après 30 s ; un appel par minute et par joueur ; journal de 200 entrées (`/phone urgences`) |
| Repli Minecraft | `8423d28`, `7926dbb` | Appels et urgences sans MCEF : appel entrant, en cours, clavier, 17 et 15, « Urgence » sur l'écran verrouillé (ni contacts ni récents) |
| Voix | `c18ac98`, `d6ada47` | Simple Voice Chat 2.6.22 **obligatoire** (serveur et clients) ; appel privé, silence, haut-parleur (entendu à 8 blocs, capte à 16), grésillement à une barre ; la proximité n'est jamais coupée |

## Ce qui est prouvé

- JUnit : 1260 essais, 0 échec (dernier passage, `c9b162f`).
- GameTests sans le pack : « All 1240 required tests passed » (`c9b162f`, 08:59 à 09:01, rapport de l'auteur). Chaque tâche a vu ses mutations rouges
  (rapports de tâche).
- Page : vue dans un navigateur à 390 × 844 sur une copie sans CSP (captures et touchers), puis en jeu.
- Chaque tâche relue par un relecteur distinct en lecture seule, constats importants corrigés ; revue d'ensemble de
  la partie livrée (serveur et voix, pas la page ni le repli côté client) : **approuvée**, deux de ses trois mineurs
  corrigés (`c9b162f` : batterie des urgences qui sonnent, copie chargée trouvée).

## Dans un vrai client (tâche 11a, 27/11, 07:14 à 07:30)

Monde solo, client avec MCEF, correspondant simulé côté serveur, vrais clics sur les boîtes de la page, chaque étape
relue au serveur : **« TOUT PASSE, 81 pas », 20 images** (troisième lancement).

- Contact « Zoé Hélène » saisi dans le champ natif (accents relus), présent au serveur.
- Appel composé au clavier : sonnerie au serveur ; décroché : minuteur, silence et haut-parleur relus au serveur.
- Île dynamique touchée au centre : retour à l'appel ; raccroché : « Appel terminé », consommation des deux antennes
  revenue à celle d'avant l'appel.
- Appel entrant écran déverrouillé et verrouillé, refusé puis accepté verrouillé ; numéro inconnu : message.
- Bouton vert à champ vide pendant un appel : retour à l'appel, « Déjà en appel », rien d'envoyé.

Le premier lancement a été coupé par le coupe-circuit : la fenêtre a pris le premier plan 40 ms (utilisateur absent,
curseur intact), à cause d'un jar de fenêtre périmé dans `run/mods` ; corrigé (`fbe3c34` : chaque lancement d'automate
recopie le jar frais). La recopie à partir d'un jar réellement périmé n'a pas été revue. Les deux lancements suivants :
aucune alerte. Non essayés : Entrée et Échap du vrai clavier, appel manqué, sonnerie entendue.

### Urgences et repli (tâche 11b, 27/11, 08:40 à 08:43)

Deux lancements, coupe-circuit sans alerte. **MCEF, 48 pas, TOUT PASSE** : écran verrouillé, « Urgence », « Police
17 » touché sur sa boîte, policier simulé qui décroche, appel actif relu au serveur, raccroché ; puis appel entrant
répondu. **Repli sans MCEF, 45 pas, TOUT PASSE** : le même parcours par les widgets de l'écran ; le plantage trouvé
en relecture (bouton retiré gardant le focus du clavier) rejoué : Espace n'agit plus. « Secours 15 », la position
chez le secours et la voix d'une urgence ne sont pas joués.

## Non prouvé et limites

- **La voix n'a jamais été entendue** : aucun essai avec deux clients et de vrais micros ; le vrai puits Simple Voice
  Chat (`SvcSink`) n'est gardé que par la lecture de ses références (`09dffa8`) ; codec Opus jamais exécuté.
- Aucun essai à deux vrais clients : le correspondant et les secours sont simulés côté serveur.
- Les mondes sont lus au démarrage du serveur vocal : une dimension créée plus tard n'aurait pas de haut-parleur.
- Rien avec le pack de mods ni sur serveur dédié pour ce lot ; aucun bout en bout.
- Repli : à 320 × 240, la ligne d'un secours (nom, position, minuteur) peut être coupée (estimation, relecture) ;
  clavier sans ligne visible tant que le numéro est vide ; « Appel terminé » en rouge.
- Un second appel d'urgence pendant qu'un premier sonne ne sonne pas chez les secours déjà occupés (question 17).

- Deux copies d'un téléphone : côté urgences, la première trouvée est encore prise avant de regarder sa batterie
  (lu, non essayé) ; une dimension créée après le démarrage du serveur vocal n'a pas de haut-parleur.

## Décisions prises en autonomie (à confirmer)

Voir `.superpowers/sdd/2026-11-27-lot-t3-appels-messages/questions.md`, reprises dans la discussion du 27/11.

## Pour le pack

- **Simple Voice Chat devient obligatoire sur le serveur de jeu** (`voicechat` dans `mods.toml`,
  `[1.20.1-2.6.22,1.20.1-2.7)`) : le serveur ne démarrera pas sans lui. Seul le pack des joueurs a été vérifié
  (2.6.22 présent). Port UDP vocal à ouvrir (24454 par défaut).
- Client et serveur se livrent ensemble : protocole `12`.
