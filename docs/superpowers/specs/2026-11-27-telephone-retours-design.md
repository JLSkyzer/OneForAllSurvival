# Téléphone : retours d'essai du 27/11, Face ID et AirDrop

Demande de Killian après son essai sur le serveur : « on peux pas utiliser le clavier numérique pour écrire les
numéro », « taper le code de déverouillage du tel avec notre clavier aussi », « un système de faceID », « Airdrop
pour partager son numéro », la touche de voice chat qui ne marche pas téléphone ouvert (« quand je quitte le gui
du téléphone l'appel marche ») et « les bug téléphone et SIM quand on est en crea ».

## 1. Chiffres au clavier

Sur le pavé du code (déverrouillage, assistant, feuilles de code) et sur le clavier de l'appli Téléphone, les
chiffres se tapent avec la rangée du haut **sans Maj** (clavier AZERTY compris) et avec le pavé numérique ; Retour
arrière efface, Entrée (aussi celle du pavé) appelle ou valide comme aujourd'hui. Dans un champ de texte, la saisie
reste celle d'un champ. Un chiffre ne compte jamais deux fois. Vaut pour le téléphone web et l'écran de repli.

Cause relevée le 27/11 : le navigateur du jeu (MCEF 2.1.6) reçoit le code de touche de GLFW tel quel et la page ne
reconnaît pas le pavé ; l'écran de repli ne lit que le caractère tapé, donc pas la rangée du haut sans Maj. Le jeu
traduit lui-même la touche et la donne à la page, au lieu de compter sur la traduction du navigateur.

## 2. Parler avec le voice chat, téléphone ouvert

Les touches « parler » et « chuchoter » de Simple Voice Chat (`key.push_to_talk`, `key.whisper`) fonctionnent
téléphone ouvert, à l'appui comme au relâchement, quelle que soit la touche choisie. Cause : le voice chat écoute
l'événement clavier de Forge, que le jeu ne publie pas quand l'écran dit avoir pris la touche ; la souris passe par
un événement publié avant l'écran. L'écran du téléphone ne s'attribue donc plus ces deux touches. Les autres
touches du voice chat (muet, menus) ne passent pas : un menu remplacerait le téléphone.

## 3. SIM et téléphone en créatif

Le jeton secret d'une carte n'est jamais envoyé au client. En créatif, l'inventaire renvoie au serveur la pile que
le client connaît : la carte, ou la carte logée dans un téléphone, perd son jeton et devient « non reconnue ».
Attendu : après n'importe quelle manipulation de l'inventaire créatif (déplacer, poser dans un autre emplacement,
jeter puis ramasser, rouvrir l'inventaire pendant que le serveur met le téléphone à jour), la carte reste reconnue.
Le serveur rend à la pile le jeton **qu'il a vu chez ce joueur** : il ne le prend pas au registre sur la foi du
client, et ne l'envoie toujours pas au client. Une copie créative d'une carte reste une copie : après insertion, une
seule vaut, comme aujourd'hui. Les cartes déjà abîmées se remplacent à l'antenne.

## 4. Face ID

- Interrupteur « Face ID » dans Réglages, posé par le propriétaire du téléphone, code demandé pour l'activer
  (pas pour le couper). Coupé par défaut ; l'effacement du téléphone et un changement de propriétaire l'oublient.
- Téléphone verrouillé, Face ID actif : à l'ouverture par **le propriétaire, visage découvert** (emplacement de tête
  vide), il se déverrouille seul, avec une courte animation « Face ID » sur l'écran verrouillé.
- Visage couvert : le pavé du code s'affiche, avec « Visage non reconnu » (décision de Killian).
- Quelqu'un d'autre ne profite jamais de Face ID, même en braquant le téléphone sur le propriétaire (décision de
  Killian).
- Pas de Face ID pendant une attente après trop de codes faux, ni en rallumant l'écran juste après avoir touché
  « verrouiller » (seulement à une ouverture).
- Le serveur seul décide ; la page ne fait que montrer.

## 5. AirDrop : partager son numéro

- Dans Contacts, « Partager mon numéro » : la liste des joueurs à 8 blocs ou moins, en vue (aucun mur entre), qui
  ont un téléphone sur eux avec une carte SIM. Spectateurs et joueurs invisibles n'y sont pas. Chaque ligne porte le
  nom du téléphone (son propriétaire).
- L'envoyeur doit avoir une carte active : c'est son numéro qui part, sous le nom du propriétaire du téléphone.
- Le receveur est prévenu même téléphone rangé (décision de Killian) : message dans le chat avec « Accepter » et
  « Refuser » cliquables, et un son. Acceptée, la fiche s'ajoute aux contacts de la carte de son téléphone (main
  d'abord). Numéro déjà enregistré : la fiche existante est gardée, on le dit.
- Une demande expire après 60 s, à la déconnexion ou si l'envoyeur n'a plus sa carte ; une seule en attente entre
  deux mêmes joueurs ; au plus une demande toutes les 3 s par envoyeur. Une réponse ne vaut que pour son
  destinataire. L'envoyeur apprend « accepté », « refusé » ou « sans réponse ».
- Distance et vue sont vérifiées à l'envoi ; l'acceptation n'exige plus la proximité.
- Écran de repli (sans navigateur) : pas de bouton d'envoi ; on peut recevoir.

## Hors périmètre

Rien d'autre du téléphone ne change : réseau, appels, antennes, formats des données existantes (les téléphones et
cartes d'aujourd'hui se lisent sans conversion). Le serveur et le client doivent avoir le même jar.
