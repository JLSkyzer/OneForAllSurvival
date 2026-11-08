#!/usr/bin/env python3
"""Génère les textures, les modèles et les états de blocs du lot 4 (gestion du réseau).

Même palette et même style que les lots 2 et 3 : du matériel de survie bricolé avec ce qu'on a sauvé de l'épave,
fer piqué, laiton terni, zinc, céramique. Aucune ressource téléchargée : tout est dessiné ici, pixel par pixel.

    python tools/gen_textures_lot4.py                  écrit les PNG, les modèles et les états de blocs
    python tools/gen_textures_lot4.py --planche X.png  écrit aussi une planche de contrôle agrandie
"""
import gen_textures_lot2 as lot2

MOD = lot2.MOD
ASSETS = lot2.ASSETS

# Teintes propres au lot 4, ajoutées à la palette commune (aucune ne remplace une teinte existante).
lot2.PALETTE.update({
    "j": (178, 182, 190),  # plomb clair
    "k": (122, 126, 136),  # plomb
    "m": (80, 82, 94),  # plomb sombre
    "0": (240, 236, 222),  # céramique émaillée, reflet
    "9": (188, 180, 160),  # céramique dans l'ombre
    "4": (38, 32, 28),  # suie
    "5": (82, 70, 60),  # céramique brûlée
    "6": (82, 138, 206),  # pluie
    "7": (150, 200, 236),  # ciel clair
    "8": (58, 62, 74),  # ciel d'orage
    "p": (238, 230, 206),  # papier
    "Q": (198, 186, 156),  # papier dans l'ombre
    "U": (52, 58, 96),  # encre
})

# Le voyant de chaque état, comme les machines du lot 3 : arrêt, en marche, sans courant, en panne.
VOYANTS = {"stopped": "z", "running": "g", "no_power": "h", "fault": "P"}
ETATS = tuple(VOYANTS)
ROTATIONS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


# ---------------------------------------------------------------------------------------------------
# Outils de dessin : une toile de 16 x 16 caractères, qu'on peint par rectangles puis qu'on transforme en image.
# ---------------------------------------------------------------------------------------------------
def toile(fond="i"):
    return [[fond] * 16 for _ in range(16)]


def rect(t, x0, y0, x1, y1, c):
    """Remplit le rectangle de (x0, y0) à (x1, y1), bornes comprises."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t[y][x] = c


def px(t, x, y, c):
    t[y][x] = c


def poser(t, x, y, lignes):
    """Pose un motif ; le point laisse voir ce qui est dessous."""
    for dy, ligne in enumerate(lignes):
        for dx, c in enumerate(ligne):
            if c != ".":
                t[y + dy][x + dx] = c


def image(t):
    return lot2.sprite(*["".join(r) for r in t])


def caisson(clair="l", corps="i", sombre="d", reflet="w"):
    """Une tôle de fer cadrée de noir : biseau clair en haut à gauche, ombre en bas à droite."""
    t = toile(corps)
    rect(t, 0, 0, 15, 0, "K")
    rect(t, 0, 15, 15, 15, "K")
    rect(t, 0, 0, 0, 15, "K")
    rect(t, 15, 0, 15, 15, "K")
    rect(t, 1, 1, 14, 1, clair)
    rect(t, 1, 1, 1, 14, clair)
    rect(t, 14, 2, 14, 14, sombre)
    rect(t, 2, 14, 14, 14, sombre)
    px(t, 1, 1, reflet)
    return t


def rivets(t, coins=((2, 2), (13, 2), (2, 13), (13, 13))):
    for x, y in coins:
        px(t, x, y, "K")


# ---------------------------------------------------------------------------------------------------
# Tableau de délestage : un panneau de fer à quatre voyants et quatre interrupteurs à levier, une étiquette de laiton.
# L'entrée (l'arrière) a sa borne de laiton et sa gaine orange ; les sorties (côtés et dessus) une borne de cuivre.
# ---------------------------------------------------------------------------------------------------
COLONNES = (2, 5, 8, 11)  # la colonne gauche de chaque voie, voyant et interrupteur


def delesteur_face(voyant):
    t = caisson()
    # La rangée des voyants, sertie de noir.
    rect(t, 1, 2, 13, 5, "K")
    for x in COLONNES:
        rect(t, x, 3, x + 1, 4, voyant)
        if voyant != "z":
            px(t, x, 3, "H" if voyant in ("h", "g") else "y")  # le reflet d'un voyant allumé
    # Les interrupteurs, levier de laiton levé sur un socle noir.
    for x in COLONNES:
        px(t, x, 7, "y")
        px(t, x + 1, 7, "B")
        px(t, x, 8, "B")
        px(t, x + 1, 8, "b")
        rect(t, x, 9, x + 1, 10, "x")
        rect(t, x, 11, x + 1, 11, "d")
    # L'étiquette de laiton, un repère sous chaque voie.
    rect(t, 2, 13, 13, 13, "B")
    for x in COLONNES:
        px(t, x + 1, 13, "K")
    px(t, 3, 12, "R")
    px(t, 12, 12, "R")
    px(t, 13, 6, "R")
    return t


def borne(t, metal_clair, metal, metal_sombre, gaine, gaine_sombre):
    """Une borne vissée au centre de la tôle, sa gaine qui descend vers le bord."""
    rect(t, 5, 4, 10, 9, "K")
    rect(t, 6, 5, 9, 8, metal)
    rect(t, 6, 5, 9, 5, metal_clair)
    rect(t, 9, 6, 9, 8, metal_sombre)
    px(t, 7, 6, "K")
    px(t, 8, 7, "K")
    rect(t, 6, 10, 9, 14, gaine)
    rect(t, 9, 10, 9, 14, gaine_sombre)
    px(t, 6, 10, "K")
    px(t, 9, 10, "K")


def delesteur_sortie():
    t = caisson()
    rivets(t)
    borne(t, "e", "C", "c", "x", "K")
    px(t, 3, 9, "R")
    px(t, 12, 5, "R")
    return t


def delesteur_entree():
    t = caisson()
    rivets(t)
    borne(t, "y", "B", "b", "v", "V")
    px(t, 3, 6, "R")
    px(t, 4, 6, "o")
    return t


def delesteur_dessous():
    t = caisson("i", "d", "x", "l")
    rivets(t)
    px(t, 6, 8, "r")
    px(t, 9, 5, "r")
    return t


def ecrire_delesteur():
    nom = "load_shedder"
    for etat, voyant in VOYANTS.items():
        lot2.ecrire_png(image(delesteur_face(voyant)), ASSETS / f"textures/block/{nom}_front_{etat}.png")
        lot2.ecrire_json({"parent": "minecraft:block/cube", "textures": {
            "particle": f"{MOD}:block/{nom}_output",
            "north": f"{MOD}:block/{nom}_front_{etat}",
            "south": f"{MOD}:block/{nom}_input",
            "east": f"{MOD}:block/{nom}_output",
            "west": f"{MOD}:block/{nom}_output",
            "up": f"{MOD}:block/{nom}_output",
            "down": f"{MOD}:block/{nom}_bottom"}}, ASSETS / f"models/block/{nom}_{etat}.json")
    lot2.ecrire_png(image(delesteur_sortie()), ASSETS / f"textures/block/{nom}_output.png")
    lot2.ecrire_png(image(delesteur_entree()), ASSETS / f"textures/block/{nom}_input.png")
    lot2.ecrire_png(image(delesteur_dessous()), ASSETS / f"textures/block/{nom}_bottom.png")
    lot2.ecrire_json({"parent": f"{MOD}:block/{nom}_running"}, ASSETS / f"models/item/{nom}.json")
    variantes = {}
    for direction, rotation in ROTATIONS:  # la face avant regarde vers FACING
        for etat in ETATS:
            modele = {"model": f"{MOD}:block/{nom}_{etat}"}
            if rotation:
                modele["y"] = rotation
            variantes[f"facing={direction},state={etat}"] = modele
    lot2.ecrire_json({"variants": variantes}, ASSETS / f"blockstates/{nom}.json")
    (ASSETS / f"models/block/{nom}.json").unlink(missing_ok=True)  # l'ancien modèle provisoire, unique


# ---------------------------------------------------------------------------------------------------
# Compteur : un boîtier de zinc, la rangée des tambours de l'index (le dernier en rouge), la roue d'aluminium qui
# tourne derrière la vitre avec son repère rouge, et le bornier de laiton.
# ---------------------------------------------------------------------------------------------------
def compteur_face():
    t = caisson("q", "Z", "z", "w")
    rect(t, 2, 2, 13, 5, "K")
    for i, x in enumerate(range(3, 13, 2)):
        rect(t, x, 3, x, 4, "P" if i == 4 else "f")
        if x + 1 <= 12:
            rect(t, x + 1, 3, x + 1, 4, "x")
    rect(t, 2, 6, 13, 11, "K")
    rect(t, 3, 7, 12, 10, "A")
    px(t, 3, 7, "a")
    px(t, 4, 7, "a")
    px(t, 3, 8, "a")
    rect(t, 3, 9, 12, 9, "l")  # la roue, vue par la tranche
    rect(t, 6, 9, 7, 9, "w")
    px(t, 10, 9, "X")  # son repère
    rect(t, 3, 10, 12, 10, "d")
    for x in (3, 6, 9, 12):  # le bornier
        px(t, x - 1, 12, "K")
        px(t, x, 12, "B")
    rect(t, 2, 13, 13, 13, "z")
    px(t, 13, 12, "K")
    return t


def compteur_cote():
    t = caisson("q", "Z", "z", "w")
    rivets(t)
    rect(t, 4, 6, 11, 6, "z")
    rect(t, 4, 9, 11, 9, "z")
    px(t, 5, 11, "R")
    return t


def compteur_dessus():
    t = caisson("q", "Z", "z", "w")
    rivets(t)
    for x in (4, 10):  # les deux presse-étoupe, un câble entre, l'autre sort
        rect(t, x, 6, x + 1, 9, "x")
        rect(t, x - 1, 7, x + 2, 8, "K")
        rect(t, x, 7, x + 1, 8, "C")
    return t


def ecrire_compteur():
    nom = "meter"
    lot2.ecrire_png(image(compteur_face()), ASSETS / f"textures/block/{nom}_front.png")
    lot2.ecrire_png(image(compteur_cote()), ASSETS / f"textures/block/{nom}_side.png")
    lot2.ecrire_png(image(compteur_dessus()), ASSETS / f"textures/block/{nom}_top.png")
    lot2.ecrire_json({"parent": "minecraft:block/orientable", "textures": {
        "particle": f"{MOD}:block/{nom}_side",
        "front": f"{MOD}:block/{nom}_front",
        "side": f"{MOD}:block/{nom}_side",
        "top": f"{MOD}:block/{nom}_top"}}, ASSETS / f"models/block/{nom}.json")
    lot2.ecrire_json({"parent": f"{MOD}:block/{nom}"}, ASSETS / f"models/item/{nom}.json")
    # Bloc en ligne : FACING est le sens du courant ; le cadran regarde le joueur qui l'a posé, donc l'arrière.
    variantes = {}
    for direction, rotation in ROTATIONS:
        modele = {"model": f"{MOD}:block/{nom}"}
        if (rotation + 180) % 360:
            modele["y"] = (rotation + 180) % 360
        variantes[f"facing={direction}"] = modele
    lot2.ecrire_json({"variants": variantes}, ASSETS / f"blockstates/{nom}.json")


# ---------------------------------------------------------------------------------------------------
# Disjoncteur d'abonné : une manette rouge dans sa fente, levée (enclenché) ou baissée (coupé), un drapeau vert ou
# rouge ; scellé, un fil passe dans la manette et finit dans un plomb.
# ---------------------------------------------------------------------------------------------------
def disjoncteur_face(ferme, scelle):
    t = caisson()
    rect(t, 5, 2, 10, 13, "K")
    rect(t, 6, 3, 9, 12, "x")
    rect(t, 6, 7, 9, 8, "b")  # l'axe
    px(t, 6, 7, "B")
    if ferme:
        rect(t, 7, 4, 8, 7, "X")
        rect(t, 5, 2, 10, 3, "P")
        rect(t, 5, 2, 10, 2, "e")
        poignee_y = 3
    else:
        rect(t, 7, 8, 8, 11, "X")
        rect(t, 5, 12, 10, 13, "P")
        rect(t, 5, 13, 10, 13, "X")
        poignee_y = 12
    # Le drapeau : vert enclenché, rouge coupé.
    rect(t, 11, 3, 13, 5, "K")
    rect(t, 12, 4, 12, 4, "G" if ferme else "P")
    # La plaque de laiton de l'abonné.
    rect(t, 2, 9, 4, 11, "B")
    px(t, 2, 9, "y")
    rect(t, 2, 11, 4, 11, "b")
    px(t, 3, 4, "R")
    if scelle:
        # Le fil de plomb, de la manette au plomb vissé à droite.
        for y in range(min(poignee_y, 9), max(poignee_y, 9) + 1):
            px(t, 11, y, "s")
        px(t, 10, poignee_y, "s")
        rect(t, 11, 9, 13, 11, "m")
        rect(t, 11, 9, 12, 10, "k")
        px(t, 11, 9, "j")
        px(t, 12, 11, "K")
    return t


def disjoncteur_cote():
    t = caisson()
    rivets(t)
    rect(t, 6, 11, 9, 14, "x")  # le câble qui sort par le bas
    rect(t, 5, 10, 10, 11, "K")
    rect(t, 6, 10, 9, 10, "d")
    px(t, 4, 5, "R")
    return t


def disjoncteur_dessus():
    t = caisson()
    rivets(t)
    rect(t, 4, 7, 11, 8, "d")
    px(t, 7, 4, "R")
    return t


DISJONCTEUR = {"closed": (True, False), "cut": (False, False), "closed_sealed": (True, True),
               "cut_sealed": (False, True)}


def ecrire_disjoncteur():
    nom = "breaker"
    for etat, (ferme, scelle) in DISJONCTEUR.items():
        lot2.ecrire_png(image(disjoncteur_face(ferme, scelle)), ASSETS / f"textures/block/{nom}_front_{etat}.png")
        lot2.ecrire_json({"parent": "minecraft:block/orientable", "textures": {
            "particle": f"{MOD}:block/{nom}_side",
            "front": f"{MOD}:block/{nom}_front_{etat}",
            "side": f"{MOD}:block/{nom}_side",
            "top": f"{MOD}:block/{nom}_top"}}, ASSETS / f"models/block/{nom}_{etat}.json")
    lot2.ecrire_png(image(disjoncteur_cote()), ASSETS / f"textures/block/{nom}_side.png")
    lot2.ecrire_png(image(disjoncteur_dessus()), ASSETS / f"textures/block/{nom}_top.png")
    lot2.ecrire_json({"parent": f"{MOD}:block/{nom}_closed"}, ASSETS / f"models/item/{nom}.json")
    variantes = {}
    for direction, rotation in ROTATIONS:  # bloc en ligne, comme le compteur
        for ferme in (True, False):
            for scelle in (True, False):
                modele = {"model": f"{MOD}:block/{nom}_{'closed' if ferme else 'cut'}{'_sealed' if scelle else ''}"}
                if (rotation + 180) % 360:
                    modele["y"] = (rotation + 180) % 360
                variantes[f"closed={str(ferme).lower()},facing={direction},sealed={str(scelle).lower()}"] = modele
    lot2.ecrire_json({"variants": variantes}, ASSETS / f"blockstates/{nom}.json")


# ---------------------------------------------------------------------------------------------------
# Parafoudre : une colonne d'isolateurs de céramique sur un socle de fer, la tige de cuivre au sommet. En panne, la
# céramique a pris le coup de trop : noircie, fendue.
# ---------------------------------------------------------------------------------------------------
def parafoudre_cote(panne):
    clair, ombre = ("5", "4") if panne else ("0", "9")
    t = toile("d")
    rect(t, 0, 0, 15, 15, "K")
    rect(t, 1, 1, 14, 14, "d")
    rect(t, 1, 1, 14, 1, "i")
    rect(t, 1, 1, 1, 14, "i")
    rect(t, 7, 0, 7, 2, "4" if panne else "C")  # la tige
    rect(t, 8, 0, 8, 2, "x" if panne else "e")
    for y in (3, 6, 9):  # trois jupes, le fût entre elles
        rect(t, 3, y, 12, y, clair)
        px(t, 12, y, ombre)
        rect(t, 4, y + 1, 11, y + 1, ombre)
        rect(t, 6, y + 2, 9, y + 2, clair)
        px(t, 9, y + 2, ombre)
    rect(t, 2, 12, 13, 12, "l")  # le socle et ses boulons
    rect(t, 2, 13, 13, 13, "i")
    px(t, 3, 13, "K")
    px(t, 12, 13, "K")
    rect(t, 13, 12, 13, 13, "d")
    if panne:
        for x, y in ((5, 3), (6, 4), (8, 6), (9, 7), (7, 9), (6, 10)):  # la fêlure
            px(t, x, y, "K")
        px(t, 10, 6, "o")
    return t


def parafoudre_bout(panne):
    clair, ombre = ("5", "4") if panne else ("0", "9")
    t = caisson()
    rect(t, 4, 3, 11, 12, clair)
    rect(t, 3, 4, 12, 11, clair)
    rect(t, 11, 4, 11, 11, ombre)
    rect(t, 4, 11, 11, 11, ombre)
    rect(t, 6, 6, 9, 9, ombre)
    rect(t, 7, 7, 8, 8, "4" if panne else "C")
    px(t, 7, 7, "x" if panne else "e")
    rivets(t)
    return t


def ecrire_parafoudre():
    nom = "surge_arrester"
    for suffixe, panne in (("", False), ("_fault", True)):
        lot2.ecrire_png(image(parafoudre_cote(panne)), ASSETS / f"textures/block/{nom}_side{suffixe}.png")
        lot2.ecrire_png(image(parafoudre_bout(panne)), ASSETS / f"textures/block/{nom}_end{suffixe}.png")
        lot2.ecrire_json({"parent": "minecraft:block/cube_column", "textures": {
            "particle": f"{MOD}:block/{nom}_side{suffixe}",
            "end": f"{MOD}:block/{nom}_end{suffixe}",
            "side": f"{MOD}:block/{nom}_side{suffixe}"}}, ASSETS / f"models/block/{nom}{suffixe}.json")
    lot2.ecrire_json({"parent": f"{MOD}:block/{nom}"}, ASSETS / f"models/item/{nom}.json")
    lot2.ecrire_json({"variants": {f"state={e}": {"model": f"{MOD}:block/{nom}{'_fault' if e == 'fault' else ''}"}
                                   for e in ETATS}}, ASSETS / f"blockstates/{nom}.json")


# ---------------------------------------------------------------------------------------------------
# Station météo : un abri à persiennes peint en blanc, la girouette sur le toit, et en façade un cadran rond cerclé
# de laiton qui montre la tendance : soleil, nuages et pluie, orage. Sans courant, le cadran est éteint.
# ---------------------------------------------------------------------------------------------------
CADRAN = (  # le disque du cadran : « o » dedans, « . » dehors
    "....oooooo....",
    "..oooooooooo..",
    ".oooooooooooo.",
    ".oooooooooooo.",
    "oooooooooooooo",
    "oooooooooooooo",
    "oooooooooooooo",
    "oooooooooooooo",
    "oooooooooooooo",
    ".oooooooooooo.",
    ".oooooooooooo.",
    "..oooooooooo..",
)

ICONES = {
    "stable": ("7", (
        "..............",
        "..............",
        "......y.......",
        "...y..yy..y...",
        "....yhhhhy....",
        "....hyyyyh....",
        "..yyhyyyyhyy..",
        "....hyyyyh....",
        "....yhhhhy....",
        "...y..yy..y...",
        "......y.......",
        "..............",
    )),
    "rain_soon": ("Z", (
        "..............",
        "..............",
        "......ss......",
        "....sswwss....",
        "...swwwwwwss..",
        "..sswwwwwwwws.",
        "..qqqqqqqqqqq.",
        "..............",
        "...6..6..6....",
        "..6..6..6..6..",
        ".....6..6.....",
        "..............",
    )),
    "storm_soon": ("8", (
        "..............",
        "..............",
        "......ll......",
        "....llwwll....",
        "...lwwwwwwll..",
        "..llwwwwwwwwl.",
        "..iiiiyiiiiii.",
        "......hy......",
        ".....yy.......",
        "......hy......",
        ".......y......",
        "..............",
    )),
    "off": ("x", (
        "..............",
        "..............",
        "..........d...",
        ".........d....",
        "........d.....",
        "..............",
        "..............",
        "..............",
        "..............",
        "..............",
        "..............",
        "..............",
    )),
}


def station_face(tendance):
    fond, icone = ICONES[tendance]
    t = caisson("s", "u", "T", "w")
    # Le cercle de laiton, puis le verre et l'icône.
    for y, ligne in enumerate(CADRAN):
        for x, c in enumerate(ligne):
            if c == "o":
                t[1 + y][1 + x] = "B"
    for y, ligne in enumerate(CADRAN):
        for x, c in enumerate(ligne):
            if c == "o" and 0 < y < 11 and 0 < x < 13 and CADRAN[y - 1][x] == "o" and CADRAN[y + 1][x] == "o" \
                    and ligne[x - 1] == "o" and ligne[x + 1] == "o":
                t[1 + y][1 + x] = fond
    poser(t, 1, 1, icone)
    rect(t, 5, 13, 10, 13, "b")  # la plaque sous le cadran
    rect(t, 6, 13, 9, 13, "y")
    return t


def station_cote():
    """Les persiennes de l'abri : des lames de bois peintes en blanc, la peinture partie par endroits."""
    t = caisson("s", "u", "T", "w")
    for y in range(3, 13, 2):
        rect(t, 2, y, 13, y, "T")
        rect(t, 2, y + 1, 13, y + 1, "s")
    for x, y in ((4, 4), (5, 4), (11, 8), (8, 10), (9, 10)):
        px(t, x, y, "N")
    return t


def station_dessus():
    """Le toit et la girouette vue d'en haut : la croix des points cardinaux, la flèche et son empennage."""
    t = caisson("s", "u", "T", "w")
    rect(t, 7, 2, 8, 13, "d")  # nord-sud
    rect(t, 2, 7, 13, 8, "d")  # est-ouest
    poser(t, 3, 3, (
        "X.........",
        ".XX.......",
        ".XXX......",
        "...XX.....",
        "....BB....",
        ".....BB...",
        "......BB..",
        ".......lll",
        ".......ll.",
        ".......l..",
    ))
    rect(t, 7, 7, 8, 8, "K")
    return t


TENDANCES = ("stable", "rain_soon", "storm_soon")


def ecrire_station():
    nom = "weather_station"
    for tendance in TENDANCES + ("off",):
        lot2.ecrire_png(image(station_face(tendance)), ASSETS / f"textures/block/{nom}_front_{tendance}.png")
        lot2.ecrire_json({"parent": "minecraft:block/orientable", "textures": {
            "particle": f"{MOD}:block/{nom}_side",
            "front": f"{MOD}:block/{nom}_front_{tendance}",
            "side": f"{MOD}:block/{nom}_side",
            "top": f"{MOD}:block/{nom}_top"}}, ASSETS / f"models/block/{nom}_{tendance}.json")
    lot2.ecrire_png(image(station_cote()), ASSETS / f"textures/block/{nom}_side.png")
    lot2.ecrire_png(image(station_dessus()), ASSETS / f"textures/block/{nom}_top.png")
    # L'objet montre la station éteinte, comme hors du monde (WeatherStationAssetsTest y tient).
    lot2.ecrire_json({"parent": f"{MOD}:block/{nom}_off"}, ASSETS / f"models/item/{nom}.json")
    variantes = {}
    for direction, rotation in ROTATIONS:
        for etat in ETATS:
            for tendance in TENDANCES:  # la face s'éteint dès que la station ne tourne pas
                modele = {"model": f"{MOD}:block/{nom}_{tendance if etat == 'running' else 'off'}"}
                if rotation:
                    modele["y"] = rotation
                variantes[f"facing={direction},state={etat},trend={tendance}"] = modele
    lot2.ecrire_json({"variants": variantes}, ASSETS / f"blockstates/{nom}.json")


# ---------------------------------------------------------------------------------------------------
# Objets : le scellé (un plomb serti sur une boucle de fil) et le relevé (une fiche de papier tamponnée).
# ---------------------------------------------------------------------------------------------------
SEAL = lot2.sprite(
    "................",
    "......KKKK......",
    ".....KlwllK.....",
    "....KlKKKKlK....",
    "....KlK..KlK....",
    "....KlK..KlK....",
    ".....KlK.KlK....",
    "......KlKlK.....",
    "...KKKKllKKKK...",
    "..KjjjkkkkkkmK..",
    "..KjjkkkkkkkmK..",
    "..KjkkKKKKkkmK..",
    "..KkkkKkkKkkmK..",
    "..KkkkkkkkkmmK..",
    "...KmmmmmmmmK...",
    "....KKKKKKKK....",
)

METER_READING = lot2.sprite(
    "................",
    "..KKKKKKKKKKK...",
    "..KppppppppQpK..",
    "..KpUUUUUppQQpK.",
    "..KpppppppppppK.",
    "..KpUUUpUUUUppK.",
    "..KpppppppppppK.",
    "..KpUUUUpUUUppK.",
    "..KpppppppppppK.",
    "..KpUUpUUUUUppK.",
    "..KpppppppXXppK.",
    "..KpUUUUppXpXpK.",
    "..KpppppppXXppK.",
    "..KQQQQQQQQQQQK.",
    "..KKKKKKKKKKKKK.",
    "................",
)

OBJETS = {"seal": SEAL, "meter_reading": METER_READING}


def ecrire_tout():
    for nom, img in OBJETS.items():
        lot2.ecrire_png(img, ASSETS / f"textures/item/{nom}.png")
        lot2.ecrire_json({"parent": "item/generated", "textures": {"layer0": f"{MOD}:item/{nom}"}},
                         ASSETS / f"models/item/{nom}.json")
    ecrire_delesteur()
    ecrire_compteur()
    ecrire_disjoncteur()
    ecrire_parafoudre()
    ecrire_station()


def planche(chemin):
    """Tout le lot à l'échelle 8 : une rangée par bloc, puis les objets, puis la taille réelle d'une case."""
    from PIL import Image, ImageDraw

    ech = 8
    pas = 16 * ech + 8
    rangees = [
        ("load_shedder", [delesteur_face(v) for v in VOYANTS.values()]
         + [delesteur_sortie(), delesteur_entree(), delesteur_dessous()]),
        ("meter", [compteur_face(), compteur_cote(), compteur_dessus()]),
        ("breaker", [disjoncteur_face(*v) for v in DISJONCTEUR.values()] + [disjoncteur_cote(), disjoncteur_dessus()]),
        ("surge_arrester", [parafoudre_cote(False), parafoudre_bout(False), parafoudre_cote(True), parafoudre_bout(True)]),
        ("weather_station", [station_face(t) for t in TENDANCES + ("off",)] + [station_cote(), station_dessus()]),
    ]
    img = Image.new("RGBA", (pas * 7 + 8, pas * (len(rangees) + 1) + 60), (139, 139, 139, 255))
    dessin = ImageDraw.Draw(img)
    y = 6
    for nom, toiles in rangees:
        for i, t in enumerate(toiles):
            img.alpha_composite(image(t).resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
        dessin.text((8, y), nom, fill=(255, 255, 255))
        y += pas
    for i, (nom, objet) in enumerate(OBJETS.items()):
        img.alpha_composite(objet.resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
        dessin.text((8 + i * pas, y), nom, fill=(255, 255, 255))
    x = 6 + 2 * pas
    for objet in OBJETS.values():
        img.alpha_composite(objet.resize((32, 32), Image.NEAREST), (x, y))
        img.alpha_composite(objet, (x + 8, y + 38))
        x += 38
    chemin.parent.mkdir(parents=True, exist_ok=True)
    img.save(chemin)


if __name__ == "__main__":
    import argparse
    from pathlib import Path

    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("--planche", type=Path, help="écrit aussi une planche de contrôle agrandie")
    options = arguments.parse_args()
    ecrire_tout()
    if options.planche:
        planche(options.planche)
