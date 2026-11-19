# Film de toute l'économie (E1 à E6)

**But :** un film monté de 3 à 4 minutes pour la communauté de Lost Island RP. Il montre toute l'économie livrée (lots E1 à E6), dans la suite de la bande-annonce du 2026-11-14 (`run-demo/sneak-peek-economie.mp4`).

Décisions de Killian, 2026-11-19 :
- le film est destiné à la communauté et il est monté ;
- l'horloge de l'économie peut être avancée pour montrer minuit, et une légende le dit.

**Spec :** `docs/superpowers/specs/archive/2026-11-08-economie-design.md`.

## Contraintes

### Infrastructure
- Même chaîne que `FilmBandeAnnonce` :
  - client qui se pilote seul dans un monde neuf, avec le pack ;
  - Maya en second joueur simulé (`JoueurDeTest`) ;
  - images lues dans le tampon à 30 images par seconde, légendes et curseur dessinés ;
  - montage ffmpeg sur le modèle de `tools/bande-annonce-montage.ps1`.
- Lancement par une tâche `runDemoEconomieFilm -PwithPack -PsansJei`. Le cadre de First Aid est coupé.
- Pas de duplication massive. Les briques communes de `FilmBandeAnnonce` sont sorties dans des classes partagées : capture, plans, clics, survols, travelling et gestes de Maya. `FilmBandeAnnonce` doit toujours compiler, et rester activable et inchangé à l'écran.
- Code de développement seulement (`src/devcontent`), jamais empaqueté, inerte sans sa propriété système.

### Contenu du film
- Ce qui est montré est vrai :
  - aucune valeur n'est truquée à l'écran ;
  - les montants affichés viennent du serveur ;
  - toute commande de l'équipe est jouée par le serveur et montrée comme telle.
- Le temps du monde n'est pas accéléré. Seule l'horloge de l'économie saute à minuit (horloge injectable, comme l'essai de bout en bout), et la légende dit « Minuit (horloge avancée pour le film) ».
- Légendes en français, courtes, lisibles pendant le plan.
- Seuls les objets de l'économie, de l'électricité utile et des objets vanilla apparaissent. L'inventaire est vidé à l'arrivée.

### Lancement
- Procédure du verrou Minecraft du `CLAUDE.md` global (§11), à suivre à la lettre :
  - prise par un script qui relit le verrou et refuse s'il est pris ;
  - vidage de sa seule ligne, dans le `finally` de ce script ;
  - créneaux laissés à AXIONENGINE.
- Fenêtre sur le second écran (x = 2560), souris rendue (SourisLibre).
- Aucun client ne reste ouvert à la fin.

## Scénario

Chaque scène a un carton titre ; l'ordre est fixé, la durée indiquée est celle du film monté.

1. **Ouverture (10 s).** Travelling sur une petite ville : la place de marché, la mairie avec le trésor, une maison raccordée, le poste du fournisseur. Titre « Lost Island — l'économie ».
2. **La monnaie, E1 (35 s, condensé).**
   - Les cinq pièces, puis le change à la table.
   - La bourse qui ramasse les pièces au sol.
   - Le coffre-fort et son écran.
   - Un coffre ordinaire qui refuse la pièce.
3. **Le commerce, E2 (35 s, condensé).**
   - Échange avec Maya à deux confirmations.
   - Achat à un étal.
   - Facture rédigée au carnet et payée par Maya.
4. **Le trésor et les salaires, E3 (30 s).**
   - Le bloc du trésor à la mairie, et le tableau de la mairie.
   - Une dépense motivée par la fenêtre du motif.
   - Minuit : le policier est payé selon son temps passé dans le rôle, montant lu au serveur.
5. **Le butin, E4 (20 s).**
   - Un zombie sauvage tué par le joueur lâche des pièces.
   - Un mob sorti d'un œuf ne lâche rien.
6. **Les quêtes, E5 (25 s).**
   - L'écran des quêtes, à la touche J.
   - Une quête journalière qui avance par le geste lui-même (récolte, par exemple).
   - Réclamation de la récompense vers la bourse.
7. **L'électricité, E6 (45 s).**
   - L'ingénieur fabrique le poste à l'établi, puis le pose : entrée derrière, sortie devant.
   - Compteur et disjoncteur de la maison de Maya jumelés au testeur, puis accord de Maya.
   - Panneau du poste : tarifs, clients, consommation.
   - Minuit : la facture d'électricité est émise et prélevée dans le coffre-fort de Maya, même absente. La caisse du poste se remplit.
   - Un client qui ne paie pas est coupé au panneau.
8. **Vu par l'équipe (15 s).** `/eco stats` et `/eco power` : créé, détruit, en circulation, électricité payée. Fondu au noir.

Les montants et les noms des scènes 4 à 7 viennent du serveur au moment du tournage. Une légende qui affiche une valeur la lit, elle ne l'écrit pas.

## Livrables
- La classe du film et les classes partagées extraites, ainsi que la tâche Gradle. Commit par chemin `feat(dev): film de toute l'economie`, puis push.
- `run-demo/film-economie.mp4`, en 1280 × 720 à 30 images par seconde, avec une version Discord de moins de 10 Mo (`run-demo/film-economie-discord.mp4`). Ces fichiers ne sont pas commités.
- `termine.json` sans échec. Les images de chaque scène sont regardées par l'agent ; une scène ratée se retourne.
- Une ligne dans `tasks/todo.md`.
