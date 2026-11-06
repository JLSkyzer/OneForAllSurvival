# Vérifications du lot 2 : métier de réparation

Relevés du 2026-11-06, sur la branche `lot-2`. Ce document couvre la tâche 7 (preuve de bout en bout et film) ; les tâches 1 à 6 ont leurs GameTests, relancés ici.

## Tests relancés

| Commande | Résultat lu |
|---|---|
| `./gradlew test build runGameTestServer -PwithPack` | 61 tests JUnit, 0 échec (`build/test-results`) ; « All 168 required tests passed » (20:52:06) |
| `./gradlew runGameTestServer` (sans pack), premier passage | **« 1 required tests failed »** : `laProgressionEtLaReserveSurviventAuRechargement`, « la progression est nulle, le test ne prouve rien » |
| Même commande, sans rien changer | « All 168 required tests passed » (21:00:19) |

Ce test échoue donc par intermittence : 1 fois sur 3 passages ce soir. Il attend 5 ticks et exige une progression non nulle. La cause n'a pas été cherchée.

Jar `build/libs/oneforallsurvival-0.1.0.jar` (334683 octets, 281 entrées) : aucune entrée sous `oneforallsurvival/dev`, aucune classe `Film` ni `E2e`, aucun `bench_overrides` d'essai (liste lue dans l'archive).

## Serveur dédié et vrai client

`./gradlew runServer -PwithPack` puis `./gradlew runE2eClient -PwithPack`, puis `/stop`, nouveau `runServer`, et `./gradlew runE2eRelance -PwithPack`. Client `Dev`, opérateur de `run-server` (`online-mode=false`, `server-ip=127.0.0.1`), qui passe en survie par `/gamemode` et remet le monde en paisible par `/difficulty`. Le client ne voit le serveur que par le réseau : messages traduisibles (clés comparées), inventaire, état du bloc. La panne, l'usure et la réserve sont posées par de vraies commandes `/data merge block`, pas par l'usure réelle.

### Première phase (`run-e2e/logs/latest.log`, lancement de 20:19)

```
[e2e] PASS connecte a un serveur distant (aucun serveur integre) : 127.0.0.1
[e2e] PASS catalogue recu par le reseau a la connexion : 1136 recettes, generation 2, apres 0,0 s
[e2e] PASS stock retire : +8 brass_bars dans l'inventaire
[e2e] PASS temoin : main vide sur l'etabli, l'ecran s'ouvre
[e2e] PASS testeur, machine neuve : etat 100 %, aspect message.oneforallsurvival.report.visible.good, energie 0, panne null, piece null, protegee false, ecran null
[e2e] PASS machine neuve : poste refuse (nothing_to_do), piece gardee (1 fuse), aucun ecran
[e2e] PASS etat du bloc lu par le client : FAULT
[e2e] PASS mauvaise piece refusee, le message demande item.oneforallsurvival.fuse, piece gardee, etat FAULT, aucun ecran
[e2e] PASS sans piece : no_part ; poste vide (0 FE) : no_energy, piece gardee, etat FAULT
[e2e] PASS accroupi, poste sur la machine : recharge, batterie 1000 FE, message Welding set recharged: +1000 FE
[e2e] PASS poste en main principale et fusible en main secondaire par useItemOn : machine reparee, fusible consomme, batterie 1000 -> 600 FE, etat STOPPED, aucun ecran
[e2e] PASS testeur, machine protegee par une piece traitee : etat 100 %, aspect message.oneforallsurvival.report.visible.good, energie 0, panne null, piece null, protegee true, ecran null
[e2e] PASS /coast check sans zone (detection de l'eau) : no (-42, 90, 231: not a coastal environment)
[e2e] PASS /coast zone add puis list : - e2e_cote (minecraft:overworld): -44, 88, 229 to -40, 92, 233 ; second ajout du meme identifiant refuse
[e2e] PASS /coast check -42, 90, 231 : yes (-42, 90, 231: coastal environment)
[e2e] PASS /coast check -22, 90, 231 : no (-22, 90, 231: not a coastal environment)
[e2e] PASS /coast zone remove puis check : de nouveau no (-42, 90, 231: not a coastal environment)
[e2e] PASS a retrouver apres relance : machine 1 protegee -39, 90, 231, machine 2 en panne -42, 90, 234, machine 3 a 37 % -45, 90, 231, zone e2e_persist, batterie 200
[e2e] bilan : TOUT PASSE
```

Extrait : le passage compte 32 lignes `PASS`, 0 `FAIL`. Toutes les lignes sont dans le journal, et les pas sans ligne `PASS` (zone inconnue au retrait, liste vide) font échouer l'essai à leur délai.

Chemin du clic : `MultiPlayerGameMode.useItemOn`, le poste en main principale, la pièce en main secondaire. Le témoin compte : le même clic à main vide ouvre l'écran de l'établi, puis le testeur et le poste ne l'ouvrent pas (`mc.screen` nul une seconde après le dernier message).

### Après un vrai arrêt et une vraie relance

Le serveur s'arrête par le `/stop` du client (« All dimensions are saved », 20:19:52), le processus se termine, un nouveau processus relit le disque. Journal client de 20:27 :

```
[e2e] PASS relance : les trois machines sont la, position du joueur BlockPos{x=-42, y=90, z=231}
[e2e] PASS relance : machine 2 FAULT dans l'etat du bloc
[e2e] PASS relance : batterie du poste dans l'inventaire : 200 FE (200 attendus)
[e2e] PASS testeur, relance, machine 1 protegee : etat 100 %, aspect ...visible.good, energie 0, panne null, piece null, protegee true, ecran null
[e2e] PASS testeur, relance, machine 2 en panne : etat 0 %, aspect ...visible.fault, energie 0, panne fault.oneforallsurvival.winding, piece item.oneforallsurvival.coil, ...
[e2e] PASS testeur, relance, machine 3 usee : etat 37 %, aspect ...visible.tired, energie 0, panne null, piece item.oneforallsurvival.cleaning_kit, ...
[e2e] PASS relance : /coast zone list : - e2e_persist (minecraft:overworld): -39, 90, 231 to -39, 90, 231
[e2e] PASS /coast check -39, 90, 231 : yes (-39, 90, 231: coastal environment)
[e2e] PASS /coast check -45, 90, 231 : no (-45, 90, 231: not a coastal environment)
[e2e] bilan : TOUT PASSE
```

À savoir : entre l'arrêt propre de 20:19 et cette lecture, trois relances du serveur ont été tuées de force après un blocage du client (voir plus bas), sans joueur connecté et sans modification. La même séquence a été rejouée à la fin (phase 1 de 20:50, relance de 20:57), avec le même résultat. Journaux serveur de ces passages : 0 ligne ERROR ou FATAL. Port 25565 fermé et aucun Java restant à la fin. `run-server/ops.json` inchangé.

### Corrections de l'essai lui-même

Aucun défaut du code livré n'a été trouvé par cet essai. Chaque échec venait du banc :

- `Dev` était resté en créatif dans ce monde, et le poste y garde la pièce, comme prévu (« main secondaire 1 fuse, batterie 600 ») ;
- en survie, `Dev` est mort dans ce monde à monstres (`DeathScreen`) : cause de la mort non relevée, aucune ligne au journal serveur. Réapparition et monde paisible dans l'essai ;
- après sa réapparition, le joueur n'avait plus de place libre : une aire plate est faite par `/fill` ;
- `/data merge` répond « Nothing changed » si la pièce annoncée, tirée au sort, a déjà la valeur demandée ;
- `sendFailure` enveloppe le message traduisible dans un composant vide.

## Blocage à la connexion : le relevé qui manquait

**24 lancements du client E2E ; 10 blocages, plus 1 blocage débloqué par l'expérience ci-dessous.** Blocage : aucun message FML reçu, les deux côtés tombent après 30 s. Les connexions réussies ont pris de 1 à 4 s.

| Série | Lancements | Blocages |
|---|---|---|
| 19:55 à 20:07, mises au point | 5 | 2 |
| 20:08 à 20:11 | 2 | 1 |
| 20:12 à 20:17 | 3 | 2 |
| 20:18 à 20:27, preuve ci-dessus | 5 | 3 |
| 20:39 à 20:50, expérience `channel.read()` | 6 | 0 tombé, 1 débloqué |
| 20:53 à 20:57, relance finale | 3 | 2 |

Relevés pris pendant chaque blocage, avant toute fermeture (trois `jstack` de chaque JVM à 5 s d'écart, `netstat`, `debug.log` des deux côtés), dans `.superpowers/sdd/2026-11-06-lot-2-metier-reparation/blocage-*` (dossier ignoré par git, local) :

```
[e2e-diag] 17.0 s, ecran de connexion toujours ouvert : canal NioSocketChannel open=true active=true autoRead=true interestOps=0 readyOps=8 readPending=false
TCP 127.0.0.1:25565 127.0.0.1:52106 ESTABLISHED 2936
TCP 127.0.0.1:52106 127.0.0.1:25565 ESTABLISHED 42252
```

`interestOps=0` : le socket du client n'écoute plus la lecture, alors que `autoRead=true` et `readPending=false`. `readyOps=8` : le dernier événement prêt est la connexion (`OP_CONNECT`), donc aucune lecture n'a jamais eu lieu. Les `jstack` montrent `Netty Client IO` au repos dans `WEPoll.wait`, sans interblocage : le serveur a bien envoyé, le client n'écoute pas.

**Mécanisme proposé, lu au bytecode** (`Connection` de Forge 47.4.28, Netty 4.1.82). Le fil Netty, dans `channelActive`, publie `channel` puis appelle `setProtocol(HANDSHAKING)`, qui diffère `setAutoRead(true)` par `eventLoop.execute`. Pendant ce temps, le fil `Server Connector` envoie son Hello : le protocole change, donc `setAutoRead(false)`, qui met en file `clearReadPending`. Si les tâches passent dans l'ordre `setAutoRead(true)`, `clearReadPending`, alors la lecture est retirée. Le `setAutoRead(true)` suivant, celui de `setProtocol(LOGIN)`, trouve `autoRead` déjà vrai et n'appelle pas `read()`. Plus rien ne remet la lecture.

Preuves, au-delà de la lecture du bytecode :

1. Reproduction hors Minecraft, avec Netty seul et ces appels dans cet ordre (`scratchpad/course/CourseAutoRead.java`, non versionné), 3 essais sur 3, et le témoin (second fil attendant la fin de `channelActive`) 2 sur 2 sain :
   ```
   course : octets recus=0 autoRead=true interestOps=0 readyOps=8 readPending=false
   course : apres channel.read() : octets recus=4
   temoin : octets recus=4 autoRead=true interestOps=1 readyOps=1 readPending=true
   ```
2. Expérience sur un vrai blocage (`-Doneforallsurvival.e2e.relire=true`, 20:49) : le même état à 17 s, un `channel.read()`, et la connexion aboutit à 18 s, puis `bilan : TOUT PASSE`.
   ```
   [e2e-diag] 17.0 s, ecran de connexion toujours ouvert : canal NioSocketChannel open=true active=true autoRead=true interestOps=0 readyOps=8 readPending=false
   [e2e-diag] channel.read() demande par l'experience
   ```

Le code du mod n'est pas sur ce chemin : il ne touche ni à `Connection` ni à `setAutoRead` (recherche dans `src/main`), et il n'a aucun mixin. Reste non prouvé : l'ordre exact des tâches dans le vrai client (les lignes `Disabled auto read` sont absentes de `debug.log`) ; le taux chez de vrais joueurs (ce soir 11 sur 24 ; au lot 1, 5 blocages puis 14 lancements sans blocage ; on ignore ce qui le fait varier) ; si un client de production, sans `userdev`, est touché. Aucun correctif n'est appliqué : c'est une décision à prendre, et un simple `channel.read()` côté client semble suffire.

## Film

`./gradlew runDemoReparationFilm -PwithPack`, monde intégré plat, sans serveur dédié. Encodé par `MfEncoder.exe` : `run-demo/lot-2-reparation.mp4`, 96,7 s (967 images à 10 i/s), H.264 1280 x 720, 30163507 octets, non versionné (`run-demo` est ignoré). J'ai relu 30 images extraites des PNG et 10 extraites du mp4.

Le source `MfEncoder.cs` ne recompile pas ce soir (référence `Windows` manquante sans SDK) : l'exécutable utilisé est celui déjà compilé depuis ce source (même date, 05:58). Rien n'a été téléchargé.

Scènes vues : machine neuve sous le testeur, écran non ouvert ; 40 crafts commandés ; en marche sur `createaddition:creative_energy` ; usure accélérée ; fumée et étincelles sous 20 % ; panne avec flammes ; le testeur nomme la pièce ; mauvaise pièce refusée (« Ce n'est pas la bonne pièce : il faut Kit de nettoyage. ») ; bonne pièce, machine repartie (file 32 -> 31) ; recharge accroupi (poste 0 -> 980 FE) ; débris à mains nues sans progression pendant 4,5 s, puis à la pioche ; butin ; bain galvanique, 200 ticks ; pièce traitée, machine protégée, confirmée au testeur.

Ce qui est truqué, et annoncé dans les légendes : la durée de vie est raccourcie en mémoire (`cachedValue`, sans écrire le fichier de réglages, dans `devcontent` seulement) le temps de l'usure, et la chance de flammes est forcée à 100 % (50 % en jeu). Les provisions sont données côté serveur ; les pièces en main secondaire passent par `/item replace`, la pièce traitée par le paquet d'échange des mains.

Observé sans l'expliquer : le butin du débris arrive dans l'inventaire moins d'une seconde après la casse, à 2,2 blocs du joueur.

## Non prouvé

- **Équilibrage réel** : durée de vie de 12 h, coût de 400 FE, multiplicateur côtier. Aucune partie longue. Le risque de corvée reste à juger en jeu.
- **Usure naturelle sur serveur dédié** : le film montre l'usure réelle, accélérée, en monde intégré ; l'E2E la pose par `/data merge`. L'usure doublée en zone côtière n'est vue qu'en GameTest.
- **Citoyen sur serveur dédié** : `Dev` est opérateur, et l'opérateur passe `RoleGate` ; l'essai ne sépare pas rôle et opérateur. Le refus d'un Citoyen n'est prouvé qu'en GameTest. `/coast` refusé à un non-opérateur non plus.
- **Multijoueur** : un seul joueur à la fois.
- **FE réel d'un autre mod dans le poste** : le poste n'a été rechargé que sur un établi (dont la réserve venait de `/data merge` ou de la source créative). Aucun bloc d'un autre mod n'a chargé le poste en jeu réel.
- **Câblage réel** : la source est collée à la machine, pas de fil tiré.
- **JEI du bain** : vu dans `run-demo/visuels-captures/jei_bain.png` (catégorie, 2 recettes sur la page 1/3), dans un monde intégré ; pas sur serveur dédié, et le film ne le montre pas.
- **Assemblages séquencés** au déployeur et à la presse : jamais vus en jeu (test en réflexion seulement).
- **Persistance après un arrêt brutal** : seul un arrêt propre par `/stop` a été suivi d'une lecture ; les arrêts forcés n'avaient rien à sauvegarder.
- **Génération de débris dans le monde** : le film pose le bloc.
