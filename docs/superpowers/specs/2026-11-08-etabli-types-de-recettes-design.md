# Établi d'ingénieur : types de recettes transférés par configuration

Statut : brouillon, en attente de l'accord de Killian.

## Intention

Aujourd'hui l'établi d'ingénieur ne reprend que trois types de recettes : table de craft,
tailleur de pierre et forgeron, et seulement celles dont le résultat appartient à un mod de
`managedMods` (voir `BenchCatalog.isConcerned` et `isManaged`). Les autres machines (presse,
scie, broyeur de Create, four, etc.) gardent leurs recettes.

Killian n'a pas de cas précis aujourd'hui. Il veut un levier prêt pour le jour où un mod ou une
machine lui déplaît sur Lost Island RP : l'inscrire dans la config, et ses recettes passent par
l'ingénieur, sans écrire de code.

## Ce qui a été décidé avec Killian

1. **Réglage par mod, liste de types.** Une entrée associe un mod id à une liste de types de
   recettes séparés par des virgules.
2. **Transfert par type, quel que soit l'auteur de la recette.** `create=create:pressing` transfère
   toutes les recettes de pressage, y compris celles qu'un autre mod (Create Crafts & Additions…)
   ajoute à la presse. Le mod id sert à regrouper et à savoir si le mod est installé.
3. **Liquides et gaz exclus.** Une recette qui demande ou produit un fluide (ou un gaz) ne peut pas
   passer à l'établi : elle reste dans sa machine.
4. **Seul l'objet garanti.** Pour une recette à sorties aléatoires (broyeur : poudre + pépite au
   hasard), l'établi ne donne que l'objet garanti. Le bonus est abandonné, sans le reproduire,
   même pour Create.

## Mes choix (Killian peut les refuser)

- **Format :** nouvelle clé dans la config COMMON existante de l'établi (`BenchConfig`), pas de
  fichier JSON séparé. Liste de chaînes, une par mod :
  `transferredTypes = ["create=create:pressing,create:cutting"]`. Défaut : liste vide (aucun
  changement de comportement tant que Killian n'écrit rien).
- **Mod absent :** si le mod id n'est pas chargé (`ModList`), la ligne est ignorée sans
  avertissement. Mod présent mais type inconnu du registre : avertissement dans le journal.
  Ligne mal formée : avertissement, ligne ignorée, le reste de la config s'applique.
- **La machine d'origine perd la recette.** La recette sort du `RecipeManager`, comme les
  recettes de table aujourd'hui : la machine, JEI côté machine et les automatisations ne la voient
  plus. Sinon le joueur contournerait l'ingénieur.
- **Filtres existants :** `tableAllowedItems` vaut aussi ici (l'objet reste dans sa machine
  d'origine), `excludedItems` aussi (la recette quitte la machine sans apparaître à l'établi,
  comme aujourd'hui pour la table). L'établi lui-même (`ALWAYS_ON_TABLE`) n'est jamais transféré,
  même si quelqu'un liste `minecraft:crafting`.
- **Types déjà gérés :** lister `minecraft:crafting`, `minecraft:stonecutting` ou
  `minecraft:smithing` est permis et étend le transfert à toutes leurs recettes, quel que soit le
  mod du résultat. Sans cette ligne, le comportement actuel par `managedMods` ne change pas.
- **Conditions de machine perdues :** chaleur du brûleur, vitesse de rotation, durée de la
  machine, expérience du four : rien de cela n'est reproduit. La durée à l'établi est celle de
  `BenchTimes`, comme pour les autres recettes. Même logique que le bonus abandonné.
- **Catégorie affichée :** la catégorie d'une recette transférée est l'id de son type
  (`create:pressing`), pour que l'interface et JEI puissent la distinguer. Une recette de table,
  de tailleur ou de forgeron garde sa catégorie actuelle (`crafting`, `stonecutting`,
  `smithing`), même transférée par la nouvelle clé.
- **Repère de machine d'origine (accord de Killian) :** une recette transférée d'un autre type
  que table, tailleur ou forgeron porte dans l'interface de l'établi un repère de sa machine
  d'origine, par exemple « Presse ». Mon choix de forme : une ligne dans l'infobulle de la
  recette, pas de filtre ni d'onglet supplémentaire. Le nom vient d'une traduction si le mod en
  fournit une (au constructeur de trouver la source la plus fiable, par exemple la catégorie
  JEI ou l'objet de la machine) ; à défaut, l'id du type (`create:pressing`).
- **Clé de mod :** elle ne sert qu'à savoir si le mod est installé. Le type peut appartenir à
  un autre namespace (`create=minecraft:smelting` est valide : la ligne ne s'applique que si
  Create est chargé).
- **Garde de classe :** pour les trois types vanilla déjà gérés, `hasStandardClass` reste
  exigé, y compris quand ils sont listés dans la nouvelle clé. Pour un type modé, aucune liste
  de classes n'est possible : seules les règles de sécurité ci-dessous s'appliquent, et le
  risque d'un résultat dynamique est une limite connue, à écrire dans la vérification.
- **Types toujours refusés :** `create:deploying` (l'outil tenu n'est pas consommé dans la
  machine, il le serait à l'établi) et `create:sequenced_assembly` (objet de transition) ne
  sont jamais transférés, même listés. Le journal le dit.
- **Quantités :** l'établi consomme une unité par entrée de `getIngredients`. Create répète
  l'ingrédient pour en demander plusieurs (à vérifier par le constructeur sur une recette
  réelle). Un mod qui porte un compte à part, hors de `Ingredient`, serait moins cher à
  l'établi qu'à la machine : limite connue, à écrire dans la vérification.
- **Identifiants :** le `RecipeManager` de 1.20.1 indexe toutes les recettes par id, tous
  types confondus (`byName`) ; deux recettes transférées ne peuvent donc pas avoir le même id.

## Règles de sécurité (non négociables)

Une recette d'un type configuré n'est transférée que si toutes ces conditions tiennent ; sinon
elle **reste dans sa machine** et le journal dit pourquoi (une ligne par recette, comme les
« recettes spéciales » actuelles, plus un total par type) :

- pas de fluide ni de gaz, en entrée comme en sortie ;
- un résultat lisible et non vide par `getResultItem` ;
- au moins un ingrédient non vide dans `getIngredients`.

En cas de doute, ne pas transférer. Une recette laissée dans sa machine est un défaut visible et
réparable ; une recette transférée sans son fluide est un objet fabriqué à partir de rien.

## Non-objectifs

- Reproduire les bonus aléatoires, la chaleur, la vitesse ou l'expérience.
- Transférer des recettes à fluide en remplaçant le fluide par un objet (seau, bidon).
- Une interface en jeu pour éditer la config.
- Une dépendance de compilation à Create ou à un autre mod.

## Anti-objectifs

- Une recette à fluide qui arrive à l'établi sans son fluide.
- Une ligne de config fautive qui empêche le serveur de démarrer ou vide le catalogue.
- Un changement de comportement pour qui ne touche pas à la nouvelle clé.

## Laissé au constructeur

- **Comment détecter un fluide sans dépendre des mods.** Rien dans l'API vanilla ne l'expose.
  Pistes : appel réfléchi de méthodes connues (Create : `getFluidIngredients`,
  `getFluidResults`), lecture du JSON source de la recette, ou les deux. La règle de sécurité
  prime : un type dont on ne sait pas lire les fluides doit être traité prudemment. Le
  constructeur dit dans le plan ce qu'il retient et pourquoi.
- Ce qu'est l'« objet garanti » quand `getResultItem` renvoie la première sortie d'une liste
  aléatoire (à vérifier sur Create : la première sortie du broyeur est-elle toujours garantie ?).
- Les assemblages séquencés (`create:sequenced_assembly`), objet de transition et sorties
  aléatoires : à transférer ou à refuser, à justifier.
- L'affichage de la catégorie dans JEI (`BenchJeiPlugin`) ; les onglets de `CategoryPane`
  restent par mod du résultat.

## Preuve attendue

- JUnit sur la lecture de la config (lignes valides, mal formées, mod absent, type inconnu).
- GameTests sans le pack avec un type vanilla, par exemple `minecraft:smelting` : recettes sorties
  du four, présentes à l'établi avec le bon résultat et les bons ingrédients ; l'établi lui-même
  jamais transféré ; `tableAllowedItems` et `excludedItems` respectés ; config vide = catalogue
  identique à aujourd'hui.
- GameTests avec `-PwithPack` : `create:pressing` transféré, une recette Create à fluide
  (`create:mixing` avec liquide) restée dans sa machine, une recette `create:crushing` arrivée à
  l'établi avec son seul objet garanti.
- « All N required tests passed » lu dans `run-gametest/logs/latest.log`, avec et sans pack.
