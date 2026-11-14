# Liste des joueurs « Verre ambré » : spécification

Date : 2026-11-14. Statut : **validé par Killian** (direction « Verre ambré » choisie parmi trois ; jour qui change à minuit, spectateurs non comptés, aucune animation). Maquettes : dossier [`2026-11-14-liste-joueurs/`](2026-11-14-liste-joueurs/).

Liste des joueurs (touche TAB) de Lost Island RP, Forge 1.20.1. Direction retenue : « Verre ambré », le style du F3 du mod.

Toutes les cotes sont en **pixels GUI**. À l'échelle GUI *s*, 1 px GUI = *s* px d'écran. Les maquettes de ce dossier sont dessinées par un moteur qui applique exactement cet algorithme. Chaque nombre ci-dessous a été relevé sur ces maquettes : la hauteur dessinée est égale à la hauteur calculée dans les dix cas.

| Fichier | Cas |
|---|---|
| [`a-1-joueur.png`](2026-11-14-liste-joueurs/a-1-joueur.png) | 1 joueur, pas de maire |
| [`b-12-joueurs.png`](2026-11-14-liste-joueurs/b-12-joueurs.png) | version validée, 12 joueurs, échelle 2 |
| [`c-40-joueurs.png`](2026-11-14-liste-joueurs/c-40-joueurs.png) | 40 joueurs (dont 1 spectateur), échelle 2 |
| [`d-80-joueurs.png`](2026-11-14-liste-joueurs/d-80-joueurs.png) | 80 joueurs (dont 2 spectateurs), échelle 2 |
| [`e-cas-limites.png`](2026-11-14-liste-joueurs/e-cas-limites.png) | métiers personnalisés, pseudo de 16 « W », ping inconnu, spectateur, maire citoyen |
| [`f-12-joueurs-echelle3.png`](2026-11-14-liste-joueurs/f-12-joueurs-echelle3.png) | 12 joueurs, échelle 3 |
| [`g-40-joueurs-echelle3.png`](2026-11-14-liste-joueurs/g-40-joueurs-echelle3.png), [`h-80-joueurs-echelle3.png`](2026-11-14-liste-joueurs/h-80-joueurs-echelle3.png) | foule à l'échelle 3 (h : colonnes compactes) |
| [`i-80-joueurs-854x480.png`](2026-11-14-liste-joueurs/i-80-joueurs-854x480.png) | fenêtre 854×480, échelle 2 : troncature « et 44 autres » |
| [`j-80-joueurs-echelle4.png`](2026-11-14-liste-joueurs/j-80-joueurs-echelle4.png) | échelle 4 : troncature « et 35 autres » |

Le voile sombre du décor et le HUD des maquettes servent seulement de mise en scène. La vanilla n'assombrit pas le jeu sous la liste : il ne faut pas le reproduire.

---

## 1. Police et largeurs

- La police est celle du jeu, `Minecraft.getInstance().font`.
- Tout texte est dessiné avec l'ombre vanilla : `GuiGraphics.drawString(font, texte, x, y, argb, true)`.
- L'ombre vaut `(argb & 0xFCFCFC) >> 2`, avec l'alpha du texte, décalée de (+1, +1).
- `y` désigne le haut de la cellule de 8 px. Les majuscules accentuées (É, Î) débordent de 3 px au-dessus, comme partout dans le jeu.
- Les largeurs notées `w(…)` sont `font.width(…)`. En gras (`ChatFormatting.BOLD`), on ajoute 1 px par caractère.

Largeurs de référence, mesurées sur la police 1.20.1 :

| Texte | Largeur |
|---|---|
| `LOST ISLAND` (gras) | 73 |
| `RP` (gras) | 14 |
| `· liste des joueurs` | 95 |
| `survivants sur l'île` | 98 |
| `survivant sur l'île` | 92 |
| `MAIRE` (gras) | 33 |
| `de l'île` | 34 |
| `999+` | 24 |
| `999+ ms` | 40 |
| `Jour` | 24 |
| `Heure` | 30 |
| `Vol 417 · île inconnue` | 108 |
| `Survivant` | 48 |
| `Métier` | 30 |
| `ms` | 12 |
| `…` | 8 |
| `WWWWWWWWWWWWWWWW` | 96 |

Un pseudo Minecraft (`[A-Za-z0-9_]`, 16 caractères au plus) mesure donc **96 px au plus**.

## 2. Couleurs (ARGB)

| Élément | ARGB | Remarque |
|---|---|---|
| Verre (fond du panneau) | `0x9E100E0C` | = `DebugTheme.GLASS` |
| Bord du panneau, 1 px | `0x40FFBE5A` | = `DebugTheme.BORDER` |
| Ambre (« LOST ISLAND », heure, segment du filet) | `0xFFFFB544` | `AMBER` |
| Orange (« RP », barre d'accent) | `0xFFFF7A2E` | `ORANGE` |
| Bleu île (nombre de survivants, « Vol 417 · île inconnue ») | `0xFF78B8FF` | `ISLAND` |
| Valeurs (pseudos, numéro du jour) | `0xFFFFFFFF` | `VALUE` |
| Étiquettes (sous-titre, « survivants sur l'île », en-têtes de colonnes, « Jour », « Heure », « de l'île », « et N autres », ping inconnu) | `0xFFB9AD99` | `LABEL` |
| Ping bon | `0xFF7FE08A` | `GOOD` |
| Ping moyen | `0xFFFFB544` | `AMBER` |
| Ping mauvais | `0xFFFF7B6B` | `BAD` |
| Filet sous l'en-tête et au-dessus du pied | `0x59FFB544` | ambre à 35 % |
| Séparateur vertical entre colonnes | `0x24FFBE5A` | |
| Fond des lignes impaires (2e, 4e… de chaque colonne) | `0x09FFFFFF` | |
| Barre de ping éteinte | `0x24FFFFFF` | |
| Ombre des barres de ping | `0x8C000000` | |
| Ombre de la pastille de métier | `0x99000000` | |
| Cadre de la tête du maire | `0x99000000` | |
| Fond du bandeau du maire | `0x1AFFB544` | |
| Bord du bandeau du maire | `0x38FFB544` | |
| Barre et mot « MAIRE » | `0xFFFF55FF` | `ChatFormatting.LIGHT_PURPLE` |

**Survol :** aucun. La liste TAB n'est pas interactive.

**Couleur d'un métier** (métiers de base comme métiers créés par les admins). On part de la couleur RGB du préfixe d'équipe, soit `TextColor.getValue()`. Si sa luminance `(0,2126 R + 0,7152 G + 0,0722 B) / 255` est inférieure à 0,30, chaque canal est mélangé à 40 % de blanc : `c' = round(c + (255 − c) × 0,40)`. Sinon la couleur reste telle quelle. Le texte du métier, la pastille et la bande du mode compact utilisent cette couleur.

| Code | Couleur du jeu | Couleur affichée |
|---|---|---|
| §0 | 000000 | `0xFF666666` |
| §1 | 0000AA | `0xFF6666CC` |
| §2 | 00AA00 | `0xFF00AA00` |
| §3 | 00AAAA | `0xFF00AAAA` |
| §4 | AA0000 | `0xFFCC6666` |
| §5 | AA00AA | `0xFFCC66CC` |
| §6 | FFAA00 | `0xFFFFAA00` |
| §7 | AAAAAA | `0xFFAAAAAA` |
| §8 | 555555 | `0xFF555555` |
| §9 | 5555FF | `0xFF5555FF` |
| §a | 55FF55 | `0xFF55FF55` |
| §b | 55FFFF | `0xFF55FFFF` |
| §c | FF5555 | `0xFFFF5555` |
| §d | FF55FF | `0xFFFF55FF` |
| §e | FFFF55 | `0xFFFFFF55` |
| §f | FFFFFF | `0xFFFFFFFF` |

**Spectateur :** l'alpha de chaque élément de sa ligne (tête, texte et son ombre, pastille, barres, ombres) est multiplié par 0,45, puis arrondi. Exemples : texte blanc `0x73FFFFFF`, barre éteinte `0x10FFFFFF`. Le fond de ligne impaire n'est pas atténué. Pour la tête : `graphics.setColor(1, 1, 1, 0.45f)` avec le mélange actif, puis `setColor(1, 1, 1, 1)`.

## 3. Données côté client (1.20.1)

| Donnée | Source |
|---|---|
| Joueurs listés | `minecraft.getConnection().getListedOnlinePlayers()` (`Collection<PlayerInfo>`) |
| Pseudo | `info.getProfile().getName()`. Le pseudo brut, jamais `getTabListDisplayName()` ni le préfixe d'équipe. |
| Tête | `PlayerFaceRenderer.draw(graphics, info.getSkinLocation(), x, y, taille)`, qui dessine le visage 8×8 puis la couche chapeau. Taille 8 dans les colonnes, 16 dans le bandeau du maire. |
| Ping | `info.getLatency()` (ms) |
| Spectateur | `info.getGameMode() == GameType.SPECTATOR` |
| Équipe | `info.getTeam()` (`PlayerTeam`, peut être `null`) |
| Jour et heure | `minecraft.level.getDayTime()` |

**Métier, depuis l'équipe.** Côté serveur, le mod (`RoleTeams`) crée une équipe par rôle, nommée `ofas_<id>`. Son préfixe est le littéral `"[Libellé] "`, stylé de la couleur du rôle. Le maire est seul dans l'équipe `ofas.maire`. Son préfixe est `"[Maire] "` (LIGHT_PURPLE), auquel est ajouté un composant `"[Métier] "` de la couleur du métier. Le libellé et la couleur des métiers peuvent être modifiés par commande. Il faut donc toujours les lire dans le préfixe, sans jamais les coder en dur.

Pour lire le préfixe sans l'afficher brut :

1. Parcourir `team.getPlayerPrefix().visit((style, texte) -> …, Style.EMPTY)` et retenir chaque segment non vide avec son style.
2. Pour chaque segment, retirer les espaces de fin, puis le `[` initial et le `]` final. Le reste est un libellé, sa couleur est `style.getColor()`.
3. Équipe `ofas_<id>` : le premier libellé est le métier.
4. Équipe `ofas.maire` : le joueur est **le maire**. Le premier libellé (« Maire ») est ignoré, le second est son métier.
5. Pas d'équipe, ou équipe hors `ofas` : pas de métier. On n'affiche ni pastille ni libellé.

Le rang de tri vient du nom d'équipe, ce qui reste stable même si un libellé de base est renommé :

| Équipe | Rang |
|---|---|
| `ofas_medecin` | 0 |
| `ofas_ingenieur` | 1 |
| `ofas_policier` | 2 |
| `ofas_agriculteur` | 3 |
| tout autre `ofas_*` (métier créé par les admins) | 4 |
| `ofas_citoyen` | 5 |
| sans métier | 6 |

**Libellé trop long.** Le libellé affiché est limité à **96 px** (`JOB_MAX_W`). S'il dépasse, on retire des caractères à la fin jusqu'à ce que `w(libellé.stripTrailing() + "…") ≤ 96`, puis on affiche `libellé.stripTrailing() + "…"`. Exemple : « Responsable du port de pêche » devient « Responsable du… », alors que « Gérant de casino » (86 px) passe entier. Le pseudo n'est jamais tronqué.

**Hors périmètre** (aucune donnée disponible, rien à afficher) : le nom RP, l'état blessé et l'état « en service ».

Sont aussi ignorés :
- l'en-tête et le pied envoyés par le serveur (`setHeader` / `setFooter`) ;
- l'objectif de scoreboard affiché dans la liste (cœurs ou score).

## 4. Visibilité et accroche

Les conditions d'affichage sont celles de la vanilla, telles que les applique `ForgeGui.renderPlayerList` en 1.20.1 : `options.keyPlayerList.isDown() && (!minecraft.isLocalServer() || connection.getOnlinePlayers().size() > 1 || scoreboard.getDisplayObjective(0) != null)`.

Accroche conseillée : annuler `RenderGuiOverlayEvent.Pre` pour `VanillaGuiOverlay.PLAYER_LIST`, puis dessiner cette liste en appliquant la même condition.

**Animation :** aucune. La version 1.20.1 n'a pas d'option d'accessibilité « réduire les animations » qu'il faudrait respecter. La liste apparaît et disparaît d'un coup, comme la vanilla.

## 5. Tri et contenu

- Le **maire** sort de la liste et prend place dans le bandeau en haut. Sans maire connecté, le bandeau n'existe pas et ne laisse aucun vide (maquette a).
- Les autres joueurs sont triés selon ces clés, dans l'ordre :
  1. les non-spectateurs avant les spectateurs ;
  2. le rang du métier ;
  3. pour le rang 4 seulement, le libellé du métier sans tenir compte de la casse ;
  4. le pseudo sans tenir compte de la casse (`String.CASE_INSENSITIVE_ORDER`).
- **Nombre de survivants** `N` : les joueurs listés non spectateurs, maire compris. Les spectateurs, souvent un admin qui observe, ne sont pas comptés.
- Pas de limite de 80 joueurs comme dans la vanilla : seule la place disponible décide (section 8).

**Textes exacts :**

| Où | Texte |
|---|---|
| En-tête, à gauche | `LOST ISLAND` (gras, ambre) · `RP` (gras, orange) · `· liste des joueurs` (étiquette) |
| En-tête, à droite | `N` (gras, bleu île) + `survivants sur l'île`, ou `survivant sur l'île` si N = 1 (et si N = 0, ce qui n'arrive qu'avec des spectateurs seuls) |
| Bandeau du maire | `MAIRE` (gras, #FF55FF), `de l'île` (étiquette), pseudo (gras, blanc), pastille + métier, `42 ms` + barres |
| En-têtes de colonnes | `Survivant`, `Métier`, `ms`. En mode compact : `Survivant` seul. |
| Ligne de troncature | `et N autres`, ou `et 1 autre` |
| Pied, à gauche | `Jour` (étiquette) `14` (blanc) `Heure` (étiquette) `18:40` (ambre) |
| Pied, à droite | `Vol 417 · île inconnue` (bleu île) |

**Jour et heure :** `t = level.getDayTime()`. **Le jour change à minuit** (décision de Killian, 2026-11-14) :
le tick 0 de Minecraft est 06:00, on décale donc de 6000 ticks pour compter les jours de minuit à minuit.
- `jour = Math.floorDiv(t + 6000, 24000) + 1`
- `d = Math.floorMod(t, 24000)`
- `heure = (d / 1000 + 6) % 24`
- `minute = (d % 1000) * 60 / 1000` (divisions entières)
- affichage : `String.format("%02d:%02d", heure, minute)`

Valeurs vérifiées en exécutant la formule :

| `t` | Jour | Heure |
|---|---|---|
| 0 | 1 | 06:00 |
| 1000 | 1 | 07:00 |
| 6000 | 1 | 12:00 |
| 12000 | 1 | 18:00 |
| 17999 | 1 | 23:59 |
| 18000 | 2 | 00:00 |
| 23999 | 2 | 05:59 |
| 24000 | 2 | 06:00 |
| 324667 | 14 | 18:40 (valeur des maquettes) |

`/time set` modifie le jour affiché.

## 6. Ping

| Latence | Barres allumées | Couleur (barres et ms) | Texte ms |
|---|---|---|---|
| ≤ 0 (inconnue) | 0 | étiquette `0xFFB9AD99` | `…` |
| 1–149 | 5 | `GOOD` | la valeur |
| 150–299 | 4 | `AMBER` | la valeur |
| 300–599 | 3 | `AMBER` | la valeur |
| 600–999 | 2 | `BAD` | la valeur |
| ≥ 1000 | 1 | `BAD` | `999+` |

Les seuils de barres sont ceux de `PlayerTabOverlay.renderPingIcon` en 1.20.1. Différence avec la vanilla : elle affiche 5 barres pour une latence de 0 et une icône « pas de connexion » pour une latence négative. Ici, ≤ 0 donne l'état inconnu.

**Barres :** 5 barres de 1 px de large, une tous les 2 px (x relatif 0, 2, 4, 6, 8), de hauteurs 3, 4, 5, 6, 7 alignées en bas. On dessine d'abord l'ombre de chaque barre à (+1, +1), puis la barre, allumée ou éteinte. Encombrement : 10 × 8 px.

## 7. Disposition

### Constantes

| Constante | Valeur |
|---|---|
| `MARGIN_TOP` | 6 |
| `MARGIN_BOTTOM` | 6 |
| `MARGIN_SIDE` | 8 |
| `PAD` (marge intérieure horizontale) | 8 |
| `HEADER_H` | 22 |
| `TEXT_Y` | 7 |
| `MAYOR_GAP` | 5 |
| `MAYOR_H` | 24 |
| `COLS_GAP` | 6 |
| `COLHEAD_H` | 10 |
| `ROW_H` | 11 |
| `COL_GAP` | 12 |
| `MAX_ROWS` | 20 |
| `TARGET_ROWS` | 10 |
| `FOOT_GAP` | 4 |
| `FOOT_H` | 15 |
| `JOB_MAX_W` | 96 |
| `SPLIT` (écart minimal gauche/droite dans l'en-tête et le pied) | 16 |

Notations : `GW` et `GH` désignent la taille GUI (`getGuiScaledWidth/Height`). `n` est le nombre de joueurs hors maire, spectateurs compris.

### Pile verticale

Les ordonnées sont relatives au haut du panneau.

| y | Élément |
|---|---|
| 0 | en-tête, hauteur 22 ; textes à y = 7 |
| 22 | filet, 1 px |
| 23 | si maire : vide de 5, puis bandeau de 24 |
| | si `n > 0` : vide de 6, en-têtes de colonnes (10), puis `rows × 11` |
| | vide de 4, puis pied de 15 : filet à son y, textes à y + 5 |

```
fixedH = 22 + 1 + (maire ? 29 : 0) + (n > 0 ? 16 : 0) + 19
PH     = fixedH + (n > 0 ? rows × 11 : 0)
maxRows = clamp(floor((GH − 6 − 6 − fixedH) / 11), 1, 20)
availW  = GW − 2×8 − 2×8 = GW − 32        (largeur intérieure maximale)
```

### Colonne complète

Abscisses relatives au bord gauche de la colonne. `nameW` est le `w(pseudo)` le plus long et `jobW` le `w(libellé affiché)` le plus long, sur les `n` joueurs.

| Élément | Position |
|---|---|
| tête 8×8 | (2, y+1) |
| pseudo | (13, y+2) |
| pastille 3×3 | `DOT = 13 + nameW + 8`, en (DOT, y+4) ; ombre 3×3 en (DOT+1, y+5) |
| métier | `JOB = DOT + 6`, en (JOB, y+2) |
| ms | aligné à droite sur `MSR = JOB + jobW + 6 + 24`, soit x = MSR − w(ms), en y+2 |
| barres | `BARS = MSR + 4`, en (BARS, y+2) |
| largeur | `colW = BARS + 10 + 2` |

En-têtes de colonne, à l'y du bloc : `Survivant` en x = 13, `Métier` en x = DOT, `ms` en x = MSR − 12.

### Colonne compacte

| Élément | Position |
|---|---|
| bande métier 2×9 | (0, y+1) |
| tête | (4, y+1) |
| pseudo | (15, y+2) |
| barres | `BARS = 15 + nameW + 6` |
| largeur | `colW = BARS + 12` |

En-tête de colonne : `Survivant` en x = 15. Pas de métier en texte ni de ms : la couleur du métier reste visible par la bande.

### Choix des colonnes

On essaie dans l'ordre et on garde le premier essai dont `largeur ≤ availW`, avec `largeur = cols × colW + (cols − 1) × 12` :

1. complète, `cols = ceil(n / min(10, maxRows))` ;
2. complète, `cols = ceil(n / maxRows)` ;
3. compacte, `cols = ceil(n / maxRows)`.

Dans les trois cas, `rows = ceil(n / cols)`, ce qui équilibre les colonnes. Le remplissage se fait colonne par colonne, de haut en bas, comme la vanilla.

4. Si rien ne tient : mode compact, `cols = max(1, floor((availW + 12) / (colW + 12)))` et `rows = maxRows`. On affiche les `cols × rows − 1` premiers joueurs ; la dernière case reçoit `et N autres` (étiquette, x = 15, y+2).

**Pourquoi 10 lignes avant 20 :** une liste consultée une seconde se lit mieux large et basse. La validation de 12 joueurs sur 2 colonnes de 6 en découle. On ne monte à 20 lignes (le maximum vanilla) que si la largeur manque.

**Lignes impaires :** fond `0x09FFFFFF` sur `colW × 11`.

**Séparateur vertical** entre deux colonnes : en x = bord gauche + colW + 6, depuis l'y des en-têtes de colonnes, sur une hauteur de `10 + rows × 11`.

### Largeur intérieure `IW`

```
colsW   = cols × colW + (cols − 1) × 12                     (0 si n = 0)
header  = 73 + 4 + 14 + 6 + 95 + 16 + (w_gras(N) + 4 + w(texte survivants))
foot    = 24 + 4 + w(jour) + 8 + 30 + 4 + w(HH:MM) + 16 + 108
mayorW  = 28 + max(w_gras(pseudo maire), 71) + 8 + 6 + w(métier maire) + 12 + 40 + 4 + 10 + 4
IW = max(colsW, header, foot, mayorW)
```

Si `IW > availW`, le sous-titre `· liste des joueurs` et le texte `Vol 417 · île inconnue` sont retirés, et on recalcule avec les formes courtes : `header − 6 − 95` et `foot − 16 − 108`. Les colonnes sont alignées à gauche, en x = PAD.

### Panneau

Largeur `PW = IW + 16`, hauteur `PH`. Position : x = `(GW − PW) / 2` (division entière), y = 6.

Dessin :
- verre plein ;
- bord de 1 px sur les quatre côtés, à l'intérieur du rectangle ;
- barre d'accent orange 3×9 en (0, 7) ;
- `LOST ISLAND` en (8, 7) ; `RP` en (8+77, 7) ; sous-titre en (8+97, 7) ;
- bloc de droite aligné à droite sur x = 8 + IW : le nombre, puis 4 px, puis le texte ;
- filet `0x59FFB544` en (8, 22, IW × 1), et segment ambre `0xFFFFB544` en (0, 22, 40 × 1).

### Bandeau du maire

Coordonnées relatives à (8, y du bandeau), sur une largeur IW et une hauteur 24 :

| Élément | Position et couleur |
|---|---|
| fond | `0x1AFFB544` |
| bords haut, bas et droit, 1 px | `0x38FFB544` |
| barre gauche 2×24 | `0xFFFF55FF` |
| cadre 18×18 | (5, 3), `0x99000000` |
| tête 16×16 | (6, 4) |
| `MAIRE` (gras) | (28, 4) |
| `de l'île` | (65, 4) |
| pseudo (gras, blanc) | (28, 13) |
| pastille | `DOT = 28 + max(w_gras(pseudo), 71) + 8`, en (DOT, 15) ; ombre en (DOT+1, 16) |
| métier | (DOT + 6, 13) |
| barres | (IW − 14, 13) |
| texte de ping | « 42 ms » (ou « … ») aligné à droite sur x = IW − 18 |

Un maire spectateur est atténué de la même manière qu'une ligne de spectateur.

Garde-fou pour les très petites largeurs : si `mayorW > availW`, le libellé du métier du maire est tronqué par la règle de la section 3, avec pour maximum `availW − (mayorW − w(métier))`. Ce cas ne peut se produire qu'en 320 px GUI de large, avec un pseudo de maire de 16 caractères larges et un libellé long. Il n'est pas montré dans les maquettes.

### Pied

Coordonnées relatives à (8, y du pied) :
- filet `0x59FFB544` sur IW × 1 ;
- textes à y + 5 : `Jour` en 0, valeur en 28, `Heure` en 28 + w(jour) + 8, puis l'heure 4 px après ;
- `Vol 417 · île inconnue` aligné à droite sur IW.

## 8. Échelles et fenêtres (résultats de l'algorithme)

Les 12 joueurs incluent la maire. Les foules de 40 et 80 sont celles des maquettes : pseudos de 75 px au plus, métier le plus long « Agriculteur » de 57 px, maire présent.

| Fenêtre | GUI | Taille GUI | maxRows | 12 joueurs | 40 joueurs | 80 joueurs |
|---|---|---|---|---|---|---|
| 1920×1080 | 1 | 1920×1080 | 20 | complet 2×6 | complet 4×10 | complet 8×10 |
| 1920×1080 | 2 | 960×540 | 20 | complet 2×6, PW 430 (b) | complet 4×10, PW 836 (c) | complet 4×20, PW 872, bas à 313 (d) |
| 1920×1080 | 3 | 640×360 | 20 | complet 2×6, PW 430 (f) | complet 2×20, PW 420, bas à 313 (g) | compact 4×20, PW 484, bas à 313 (h) |
| 1920×1080 | 4 | 480×270 | 15 | complet 2×6, PW 430 | compact 3×13, PW 340 | compact 3×15 + « et 35 autres », PW 364, bas à 258 (j) |
| 854×480 | 2 | 427×240 | 12 | complet 1×11, PW 340 | compact 3×12 + « et 4 autres » | compact 3×12 + « et 44 autres », PW 364, bas à 225 (i) |

Dans tous ces cas, le panneau tient dans l'écran : `PW ≤ GW − 16` et le bas reste à `GH − 6` au plus. Avec l'échelle « Auto », le jeu choisit la plus grande échelle qui garde au moins 320×240 px GUI. La taille la plus petite possible est donc 320×240, et l'algorithme y tient aussi : `maxRows` vaut 12 avec un maire, le mode compact s'applique, puis la troncature.

Le jeu n'impose rien d'autre à l'échelle : les cotes sont en pixels GUI, la taille à l'écran suit l'échelle.
