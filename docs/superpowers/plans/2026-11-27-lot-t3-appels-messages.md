# Lot T3 : appels, messages et urgences, plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan
> task-by-task.

**Goal:** deux joueurs chez deux opérateurs différents s'appellent et s'écrivent, les secours répondent au 17 et au
15, et tout s'arrête quand le réseau tombe.

**Architecture:** la logique d'appel est une machine à états pure (`phone/call/CallBook`) pilotée par un service serveur
(`CallService`) qui s'appuie sur `PhoneNetwork` (signal, trajet, trafic). Contacts, récents et conversations suivent
la carte SIM dans des `SavedData` propres. La page et le repli reçoivent des paquets à part de `PhoneStatePacket`. La
voix passe par l'API serveur de Simple Voice Chat derrière une interface `VoiceRelay`, pour que les GameTests s'en
passent.

**Tech Stack:** Forge 47.4.28, Java 17, MCEF 2.1.6 (client), `voicechat-api` 2.6.20, JUnit, GameTests.

**Spec:** [le téléphone](../specs/2026-11-08-telephone-design.md), features 93, 95, 96 (validé le 19/11) ; « Ce que T2
prépare » de [la vérification T2](../../verifications/lot-t2.md).

## Global Constraints

- Textes joueurs en français avec accents (langues `fr_fr` et `en_us`) ; jamais « SolarTown ».
- Le serveur fait foi ; rien de la page n'est cru sans vérification ; textes bornés à la construction des paquets ;
  `toString` des paquets masquant numéros, messages et codes.
- Aucun jeton de carte ni UUID de titulaire sérialisé ou journalisé (`SimCard.toString` masque déjà le jeton).
- Toute donnée créée par un joueur est plafonnée ; ids par `UUID.randomUUID()` ; `Locale.ROOT`.
- Chaque `TrafficHandle` ouvert se ferme en un seul point ; GameTest « un départ : consommation revenue à la veille ».
- `CLOSE`, raccrocher et refuser ne passent jamais par le frein anti-rafale.
- GameTests : 8 par lot au plus, `@AfterBatch` qui fait partir les joueurs, date par `EconomyClock`, air posé
  au-dessus du plancher.
- Canal réseau : protocole `"12"` dès le premier paquet nouveau, essai « client en 11 refusé ».
- Commits par chemins, jamais `-A` ; verrou Minecraft du poste ; aucun client sans coupe-circuit.

## Review Focus

1. Deux copies d'un même téléphone chez deux joueurs : un seul sonne et parle, jamais les deux.
2. Raccrocher, mourir, se déconnecter, changer de dimension, jeter le téléphone ou retirer la carte pendant la
   sonnerie ou l'appel : l'appel finit pour les deux et la consommation revient à la veille.
3. Composer en rafale des numéros au hasard : débit limité, aucune fuite de l'existence d'un numéro au-delà des
   messages prévus.
4. Arrêt du serveur pendant un appel puis relance : aucun appel fantôme, aucune poignée de trafic restante.
5. Contact au nom piégé (codes `§`, très long, caractères de contrôle) : refusé ou nettoyé.

---

## Choix de l'auteur (à confirmer par Killian, `questions.md`)

- Blocage du code : plafond d'une heure (tâche 1).
- Un appel vit tant que le téléphone est porté : fermer l'écran ne raccroche pas ; on rouvre le téléphone pour
  raccrocher. Sonnerie 30 s.
- Changer de dimension ou mourir coupe l'appel.
- « En service » pour le 17 et le 15 : joueur connecté ayant le rôle `policier` ou `medecin` (aucune prise de service
  n'existe).
- Mode avion ou batterie vide : ni appel ni urgence.
- Message vers un destinataire injoignable : « non distribué », renvoyable (pas de remise différée).
- Grésillement à une barre : bruit léger ajouté et quelques trames coupées (décodage puis réencodage Opus ; sauter
  une trame sans la remplacer ne s'entend pas comme un trou).
- Simple Voice Chat est une dépendance **obligatoire** du serveur et des clients, comme le dit le cahier.
- Un joueur dans un groupe vocal fermé garde le comportement de Simple Voice Chat (sa voix n'atteint plus la
  proximité) ; le haut-parleur ne capte pas les membres d'un groupe fermé.
- A et B à portée de voix s'entendent deux fois (proximité et téléphone) : accepté. Le chuchotement est relayé.

## Relevé de l'API vocale (étude du 27/11, non exécutée)

- Maven : `de.maxhenkel.voicechat:voicechat-api:2.6.20` (`compileOnly`, dépôt
  `https://maven.maxhenkel.de/repository/public`, groupe seul) ; mod `maven.modrinth:simple-voice-chat:S11m0QIb`
  (`fg.deobf`, version du pack, 2.6.22).
- Greffon : classe publique annotée `@ForgeVoicechatPlugin implements VoicechatPlugin` ; événements
  `VoicechatServerStarted/StoppedEvent`, `MicrophonePacketEvent` (fil vocal unique : ne jamais bloquer, ne jamais
  `cancel()`, aucune lecture du monde), `PlayerDisconnectedEvent`, `PlayerStateChangedEvent`.
- Canaux : un `StaticAudioChannel` par flux (appel, parleur, auditeur) ; haut-parleur `LocationalAudioChannel`
  (`setDistance(8)`, filtre à 8 blocs, exclure l'appelant). Données vides = fin de parole : `flush()` et
  `resetState()` des codecs.
- Micro muet inconnu du serveur ; `VoicechatConnection.isDisabled/isInGroup/getGroup`.
- Conception : le tic serveur publie un instantané immuable `parleur -> routes` (champ `volatile`) lu par le fil vocal.

---

**Avancement au 27/11, 09:05** : tâches 1 à 8 et l'essai en jeu des appels et des urgences faits ; restent les
tâches 9 et 10 (messages) et la suite de la tâche 11 (serveur dédié avec le pack, voix à deux clients, wiki).

### Task 1: Blocage croissant du code

Brief : `.superpowers/sdd/2026-11-27-lot-t3-appels-messages/task-1-brief.md` (condition d'entrée du lot).

### Task 2: Carnet de la carte (contacts et récents)

**Files:**
- Create: `src/main/java/dev/oneforallsurvival/phone/call/PhoneDirectory.java`,
  `src/main/java/dev/oneforallsurvival/phone/call/ContactRules.java`
- Test: `src/test/java/dev/oneforallsurvival/phone/call/ContactRulesTest.java`,
  `PhoneDirectoryTest.java` ; GameTest `CarnetGameTests` (lot `telephone_carnet`)

**Interfaces (produces):**
- `public record Contact(String number, String name)` ; `public record Recent(String number, Recent.Kind kind, long atMillis, int seconds)` avec `Kind { OUTGOING, INCOMING, MISSED }`.
- `ContactRules` : `MAX_CONTACTS = 100`, `MAX_NAME = 32`, `MAX_RECENTS = 50` ; `static Optional<String> cleanName(String raw)` (espaces rognés et réduits, caractères de contrôle et `§` retirés, vide ou trop long après nettoyage = vide).
- `PhoneDirectory extends SavedData`, clé `oneforallsurvival_phone_directory` : `static PhoneDirectory get(MinecraftServer)` ; `List<Contact> contacts(UUID simId)` (triés par nom, sans casse) ; `SaveResult saveContact(UUID simId, String rawNumber, String rawName)` avec `SaveResult { SAVED, BAD_NUMBER, BAD_NAME, FULL }` (numéro passé par `SimNumbers.parse` ; même numéro = renommage) ; `boolean removeContact(UUID simId, String number)` ; `void log(UUID simId, Recent entry)` (plus récent en tête, au-delà de 50 le plus ancien tombe) ; `List<Recent> recents(UUID simId)` ; `Optional<String> nameOf(UUID simId, String number)`.
- Les données suivent `simId` (une carte remplacée les garde). Chargement tolérant : entrée illisible ignorée, listes bornées à la lecture.

Preuves : JUnit (nettoyage, tri, plafonds à la borne et au-delà, aller-retour NBT, NBT corrompu) ; GameTest (écrit,
relu après écriture/lecture du fichier, carte remplacée).

### Task 3: Appels côté serveur, sans voix

**Files:**
- Create: `phone/call/CallBook.java` (pur), `phone/call/CallService.java`, `phone/call/PhoneCarriers.java`,
  `phone/call/CallListener.java`
- Modify: `PhoneConfig` (`callRingSeconds` 30, `callDialsPerMinute` 10), `OneForAllSurvival` (évènements)
- Test: `src/test/java/.../phone/call/CallBookTest.java` ; GameTest `AppelGameTests` (lots `telephone_appels_a`, `_b`)

**Interfaces (consumes):** `PhoneNetwork.status/route/lineOf/openTraffic`, `TrafficHandle`, `PhoneItem.phoneId`,
`PhoneSims.check/line`, `PhoneData.record`, `SimNumbers.parse`, `PhoneDirectory.log`.

**Interfaces (produces):**
- `CallBook` : `record Party(UUID player, UUID phoneId, UUID simId, String number)` ; `enum State { RINGING, ACTIVE }` ; `enum End { HUNG_UP, DECLINED, NO_ANSWER, NO_SIGNAL, PARTY_LEFT, PHONE_GONE, DIED, CHANGED_DIMENSION, SERVER_STOPPING }` ; `record Call(int id, Party caller, Party callee, State state, long startedTick, long answeredTick, boolean callerMuted, boolean calleeMuted, boolean callerSpeaker, boolean calleeSpeaker)` ; `Optional<Call> start(Party caller, Party callee, long tick)` (vide si un des deux est déjà en appel, par joueur, téléphone ou carte) ; `Optional<Call> answer(int id, UUID player, long tick)` (appelé seul, sonnerie seule) ; `Optional<Call> end(int id)` ; `Optional<Call> of(UUID player)` ; `Optional<Call> setMuted(UUID player, boolean)`, `setSpeaker(UUID player, boolean)` ; `List<Call> expired(long tick, int ringTicks)` ; `List<Call> all()` ; `void clear()`.
- `PhoneCarriers.find(MinecraftServer, UUID phoneId) : Optional<Carrier>` où `record Carrier(ServerPlayer player, ItemStack phone)` : joueurs connectés, inventaire entier et curseur, `PhoneSims.check == ACTIVE` exigé ; premier trouvé.
- `CallService` : `static Dial dial(ServerPlayer caller, ItemStack phone, boolean airplane, String rawNumber)` avec `record Dial(Outcome outcome, @Nullable Component message, int callId)` et `enum Outcome { RINGING, BAD_NUMBER, SELF, BUSY, UNREACHABLE, FAILED, TOO_FAST }` (échec de trajet : `FAILED` avec `RelayFailure.describe()`) ; `static boolean answer(ServerPlayer callee, int callId)` ; `static boolean hangUp(ServerPlayer player, int callId)` (refus si appelé en sonnerie) ; `static Optional<CallBook.Call> callOf(ServerPlayer)` ; `static void addListener(CallListener)` ; évènements : tick (toutes les 20 : parties présentes, téléphone et carte actifs, trajet refait, 0 barre = `NO_SIGNAL`, poignées rouvertes si le trajet change, sonnerie expirée), départ, mort (priorité haute), dimension, arrêt du serveur.
- `CallListener { void changed(CallBook.Call call); void ended(CallBook.Call call, CallBook.End reason); }`.
- Toute fin passe par une seule méthode privée qui ferme la poignée, écrit les récents des deux cartes (`MISSED` pour
  l'appelé qui n'a pas répondu) et prévient les écouteurs.
- Appelé injoignable (aucun porteur, mode avion, batterie vide) : `UNREACHABLE`. Urgences (17, 15) : `BAD_NUMBER`
  jusqu'à la tâche 8.

Preuves : JUnit du `CallBook` (occupé par joueur, téléphone, carte ; réponses refusées ; expiration à la borne) ;
GameTests : sonnerie, décroché, raccroché avec consommation revenue à la veille ; départ de l'appelant ; mort ;
dimension ; téléphone jeté ; carte retirée ; hors portée ; numéro inconnu, soi-même, occupé, avion, batterie vide ;
débit ; arrêt (`clear`). Mutations vues rouges : poignée non fermée, trajet non refait.

### Task 4: Paquets d'appel, sonnerie et application Téléphone

Paquets nouveaux (ids 31, 32, protocole `"12"`) : gestes `DIAL, ANSWER, HANGUP, MUTE, SPEAKER, SAVE_CONTACT,
REMOVE_CONTACT, OPEN_PHONE_APP` ; état d'appel et carnet poussés au porteur, écran fermé compris (sonnerie, barre
d'action « Appel entrant »). Page : application Téléphone (contacts, récents, clavier avec 17 et 15), appel entrant
par-dessus l'écran verrouillé, écran d'appel, île dynamique avec minuteur. Détail écrit avant l'envoi, après la
tâche 3.

### Task 5: Voix par Simple Voice Chat

Dépendance, greffon, `VoiceRelay`, appel privé et silence, perte de trames à une barre. Détail écrit après l'étude de
l'API.

### Task 6: Haut-parleur
### Task 7: Repli Minecraft des appels
### Task 8: Urgences 17 et 15
### Task 9: Messages côté serveur
### Task 10: Application Messages et repli
### Task 11: Bout en bout, vérification, wiki

Les tâches 6 à 11 sont détaillées avant leur envoi, d'après les interfaces livrées.
