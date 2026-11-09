#!/usr/bin/env python3
"""Génère les textures, les modèles et les états de blocs du lot 5 (vivres et protection).

Même palette et même style que les lots 2 à 4 : du matériel de survie bricolé avec ce qu'on a sauvé de l'épave, tôle
piquée, laiton terni, bois, mousse isolante. Aucune ressource téléchargée : tout est dessiné ici, pixel par pixel.

    python tools/gen_textures_lot5.py                  écrit les PNG, les modèles et les états de blocs
    python tools/gen_textures_lot5.py --planche X.png  écrit aussi une planche de contrôle agrandie

Les cinq blocs : couveuse (caisse isolée à vitre et lampe chauffante), plaque chauffante (résistance rouge quand elle
est allumée), marmite de cantine (grand fût de tôle à couvercle), électrificateur (boîtier à isolateurs) et clôture
électrique (poteaux et fil barbelé).
"""
import gen_textures_lot4 as base

lot2 = base.lot2
MOD = base.MOD
ASSETS = base.ASSETS
toile, rect, px, image, caisson, rivets = base.toile, base.rect, base.px, base.image, base.caisson, base.rivets
VOYANTS, ETATS, ROTATIONS = base.VOYANTS, base.ETATS, base.ROTATIONS

# Teintes propres au lot 5, ajoutées à la palette commune (aucune ne remplace une teinte existante).
lot2.PALETTE.update({
    "W": (226, 218, 196),  # mousse isolante
    "S": (184, 174, 148),  # mousse dans l'ombre
    "D": (255, 96, 48),  # résistance chauffée
    "L": (88, 18, 14),  # tôle rougie, sombre
    "I": (255, 214, 120),  # ampoule allumée
    "Y": (232, 190, 40),  # jaune de signalisation
})


def lampe(t, x, y, voyant):
    """Un voyant serti de noir de 2 x 2 : sa pastille et son reflet, éteinte (zinc) si la machine est à l'arrêt."""
    rect(t, x - 1, y - 1, x + 2, y + 2, "K")
    rect(t, x, y, x + 1, y + 1, voyant)
    if voyant != "z":
        px(t, x, y, "H" if voyant in ("h", "g") else "y")


def cadre_bois(t):
    """Le liseré noir, puis un cadre de bois (clair en haut à gauche) autour d'un panneau de mousse ombré."""
    rect(t, 0, 0, 15, 15, "K")
    rect(t, 1, 1, 14, 14, "N")
    rect(t, 1, 1, 14, 1, "M")
    rect(t, 1, 1, 1, 14, "M")
    rect(t, 14, 2, 14, 14, "n")
    rect(t, 2, 14, 14, 14, "n")
    rect(t, 2, 2, 13, 13, "W")
    rect(t, 13, 3, 13, 13, "S")
    rect(t, 3, 13, 13, 13, "S")


# ---------------------------------------------------------------------------------------------------
# Couveuse : une caisse de mousse cerclée de bois, une vitre sur la lampe chauffante et les œufs sur la paille.
# Chauffée, la vitre est orange ; sans courant elle est éteinte ; en panne, fêlée et noircie.
# ---------------------------------------------------------------------------------------------------
def couveuse_face(voyant, etat):
    t = toile("W")
    cadre_bois(t)
    chaud = etat == "running"
    panne = etat == "fault"
    # La fenêtre : liseré noir, puis la vitre sur l'intérieur de la caisse.
    rect(t, 2, 2, 13, 9, "K")
    rect(t, 3, 3, 12, 8, {"running": "v", "stopped": "x", "no_power": "d", "fault": "x"}[etat])
    if chaud:
        rect(t, 3, 3, 12, 4, "h")
    # La lampe chauffante au plafond de la caisse, au centre.
    if chaud:
        rect(t, 5, 3, 10, 3, "H")
        rect(t, 6, 4, 9, 4, "I")
    else:
        rect(t, 6, 3, 9, 3, "d" if panne else "i")
        rect(t, 7, 3, 8, 3, "d" if panne else "l")
    # La paille et les œufs.
    rect(t, 3, 8, 12, 8, "T")
    for x in (4, 7, 10):
        rect(t, x, 7, x + 1, 7, "9" if panne else "f")
        px(t, x, 8, "u")
    if panne:  # la vitre fêlée et le noir de suie
        for x, y in ((4, 3), (5, 4), (6, 5), (8, 5), (9, 6), (10, 7)):
            px(t, x, y, "l")
        for x, y in ((11, 4), (3, 6), (12, 7)):
            px(t, x, y, "4")
    else:  # le reflet de la vitre
        for x, y in ((3, 3), (4, 3), (3, 4)):
            px(t, x, y, "a")
        px(t, 12, 8, "A")
    # Les fentes d'aération, le voyant, et un clou à chaque coin.
    rect(t, 3, 11, 8, 11, "K")
    rect(t, 3, 12, 8, 12, "S")
    lampe(t, 10, 11, voyant)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(t, x, y, "K")
    return t


def couveuse_cote():
    t = toile("W")
    cadre_bois(t)
    # Une planche de renfort en travers, une étiquette de fret décolorée, une tache d'humidité.
    rect(t, 2, 7, 13, 7, "S")
    rect(t, 2, 8, 13, 8, "M")
    rect(t, 5, 3, 10, 6, "Q")
    rect(t, 5, 3, 10, 3, "p")
    for x, y in ((6, 4), (7, 4), (9, 5)):
        px(t, x, y, "U")
    rect(t, 3, 10, 6, 11, "S")
    px(t, 4, 10, "R")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(t, x, y, "K")
    return t


def couveuse_dessus():
    t = toile("M")
    rect(t, 0, 0, 15, 15, "K")
    rect(t, 1, 1, 14, 14, "M")
    rect(t, 14, 2, 14, 14, "n")
    rect(t, 2, 14, 14, 14, "n")
    for y in (4, 7, 10):  # les lattes du couvercle
        rect(t, 2, y, 13, y, "n")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(t, x, y, "K")
    # Le presse-étoupe du câble d'alimentation.
    rect(t, 6, 5, 9, 9, "K")
    rect(t, 7, 6, 8, 8, "C")
    px(t, 7, 6, "e")
    rect(t, 7, 10, 8, 13, "x")
    return t


# ---------------------------------------------------------------------------------------------------
# Plaque chauffante : un caisson de tôle, le dessus en serpentin de résistance. Froid, le fil est noir ; allumée, la
# plaque rougit et le fil est chauffé à blanc par endroits. Un voyant sur chaque côté, comme les autres machines.
# ---------------------------------------------------------------------------------------------------
def plaque_dessus(chaude):
    t = toile("L" if chaude else "i")
    rect(t, 0, 0, 15, 15, "K")
    rect(t, 1, 1, 14, 14, "L" if chaude else "i")
    if chaude:
        rect(t, 1, 1, 14, 1, "E")
        rect(t, 1, 1, 1, 14, "E")
    else:
        rect(t, 1, 1, 14, 1, "l")
        rect(t, 1, 1, 1, 14, "l")
        rect(t, 14, 2, 14, 14, "d")
        rect(t, 2, 14, 14, 14, "d")
    # Le serpentin : cinq passes, reliées alternativement à droite et à gauche.
    fil = "D" if chaude else "x"
    for y in (3, 5, 7, 9, 11):
        rect(t, 3, y, 12, y, fil)
    for y, x in ((4, 12), (6, 3), (8, 12), (10, 3)):
        px(t, x, y, fil)
    if chaude:
        for x, y in ((4, 3), (8, 5), (11, 7), (5, 9), (9, 11), (3, 6), (12, 8)):
            px(t, x, y, "h")
        for y in (3, 5, 7, 9, 11):  # le halo autour du fil
            for x in (2, 13):
                px(t, x, y, "F")
    else:
        for x, y in ((6, 3), (10, 5), (4, 7), (8, 9), (11, 11)):
            px(t, x, y, "d")
    # Les deux bornes d'alimentation en bas.
    rect(t, 3, 13, 4, 13, "B")
    rect(t, 11, 13, 12, 13, "B")
    return t


def plaque_cote(voyant):
    t = caisson()
    rivets(t)
    # La grille d'aération en haut, le voyant en bas.
    for y in (3, 5, 7):
        rect(t, 3, y, 12, y, "K")
    rect(t, 3, 4, 12, 4, "d")
    rect(t, 3, 6, 12, 6, "d")
    lampe(t, 7, 10, voyant)
    px(t, 3, 12, "R")
    return t


def tole_dessous():
    t = caisson("i", "d", "x", "l")
    rivets(t)
    px(t, 6, 8, "r")
    px(t, 10, 5, "r")
    return t


# ---------------------------------------------------------------------------------------------------
# Marmite de cantine : un grand fût de tôle cerclé, un couvercle à poignée, un robinet de laiton et un tableau à
# voyant sur le devant. De côté, le même fût sans robinet.
# ---------------------------------------------------------------------------------------------------
def fut(t):
    """Le fût vu de côté : tôle cintrée (clair au milieu, sombre aux bords), deux cercles de renfort."""
    rect(t, 0, 0, 15, 15, "K")
    rect(t, 1, 1, 14, 14, "i")
    rect(t, 1, 1, 2, 14, "d")
    rect(t, 13, 1, 14, 14, "d")
    rect(t, 5, 1, 7, 14, "l")
    rect(t, 6, 1, 6, 14, "w")
    for y in (4, 11):  # les cercles
        rect(t, 1, y, 14, y, "K")
        rect(t, 1, y + 1, 14, y + 1, "d")
    px(t, 3, 8, "R")
    px(t, 4, 8, "o")
    px(t, 11, 6, "R")


def marmite_face(voyant):
    t = toile("i")
    fut(t)
    # Le tableau : un petit boîtier de zinc entre les cercles, avec le voyant et le cadran.
    rect(t, 3, 6, 12, 9, "K")
    rect(t, 4, 7, 11, 8, "Z")
    rect(t, 4, 7, 11, 7, "q")
    lampe(t, 4, 7, voyant)
    rect(t, 9, 7, 10, 8, "f")  # le cadran
    px(t, 10, 7, "X")
    # Le robinet de laiton en bas du fût.
    rect(t, 6, 12, 9, 13, "K")
    rect(t, 7, 12, 8, 12, "B")
    rect(t, 7, 13, 8, 13, "b")
    px(t, 7, 12, "y")
    return t


def marmite_cote():
    t = toile("i")
    fut(t)
    rect(t, 9, 6, 11, 9, "K")  # une soudure de réparation
    rect(t, 10, 7, 10, 8, "l")
    return t


DISQUE = (  # le couvercle vu d'en haut : « K » le bord, « . » le sol autour
    "....KKKKKKKK....",
    "..KKiiiiiiiiKK..",
    ".KiiiiiiiiiiiiK.",
    ".KiiiiiiiiiiiiK.",
    "KiiiiiiiiiiiiiiK",
    "KiiiiiiiiiiiiiiK",
    "KiiiiiiiiiiiiiiK",
    "KiiiiiiiiiiiiiiK",
    "KiiiiiiiiiiiiiiK",
    "KiiiiiiiiiiiiiiK",
    "KiiiiiiiiiiiiiiK",
    "KiiiiiiiiiiiiiiK",
    ".KiiiiiiiiiiiiK.",
    ".KiiiiiiiiiiiiK.",
    "..KKiiiiiiiiKK..",
    "....KKKKKKKK....",
)


def marmite_dessus():
    """Le couvercle vu d'en haut : un disque cerclé, la poignée de fer, la soupape de vapeur."""
    t = toile("d")
    for y, ligne in enumerate(DISQUE):
        for x, c in enumerate(ligne):
            t[y][x] = "d" if c == "." else c
    # Reflet en haut à gauche, ombre à droite et en bas, et le bourrelet du couvercle.
    rect(t, 3, 3, 12, 3, "l")
    rect(t, 3, 12, 12, 12, "d")
    rect(t, 13, 4, 13, 11, "d")
    rect(t, 2, 4, 2, 11, "l")
    # La poignée en travers, et la soupape en haut à gauche.
    rect(t, 5, 7, 10, 8, "K")
    rect(t, 6, 7, 9, 8, "x")
    px(t, 6, 7, "l")
    rect(t, 4, 5, 5, 6, "K")
    px(t, 4, 5, "B")
    px(t, 5, 5, "b")
    px(t, 4, 6, "y")
    return t


# ---------------------------------------------------------------------------------------------------
# Électrificateur : un boîtier de tôle bas, trois isolateurs de céramique sur le dessus. Le bloc n'a pas d'orientation :
# le voyant et l'éclair de mise en garde sont répétés sur les quatre côtés. Le côté ne montre que onze pixels de haut,
# le dessus du boîtier étant à 11/16 ; les isolateurs montent jusqu'à 16/16.
# ---------------------------------------------------------------------------------------------------
BOLT = (  # l'éclair, noir sur le panneau jaune (cinq sur cinq)
    "..KK.",
    ".KK..",
    ".KKK.",
    "..KK.",
    ".KK..",
)


def electrificateur_cote(voyant):
    t = toile("i")
    rect(t, 0, 0, 15, 10, "K")
    rect(t, 1, 1, 14, 9, "i")
    rect(t, 1, 1, 14, 1, "l")
    rect(t, 1, 1, 1, 9, "l")
    rect(t, 14, 2, 14, 9, "d")
    rect(t, 2, 9, 14, 9, "d")
    lampe(t, 3, 3, voyant)
    # Le panneau de mise en garde, et l'éclair dedans.
    rect(t, 7, 2, 13, 8, "K")
    rect(t, 8, 3, 12, 7, "Y")
    for y, ligne in enumerate(BOLT):
        for x, c in enumerate(ligne):
            if c != ".":
                t[3 + y][8 + x] = "K"
    px(t, 3, 8, "R")
    px(t, 5, 8, "r")
    return t


def electrificateur_dessus():
    t = caisson()
    rivets(t)
    for x in (2, 7, 12):  # les empreintes des trois isolateurs
        rect(t, x, 6, x + 1, 9, "K")
    px(t, 3, 12, "R")
    return t


def isolateur():
    """La céramique émaillée, sur toutes les faces des isolateurs : crème à gauche, ombrée à droite, une rainure brune
    tous les quatre pixels pour que les jupes se lisent."""
    t = toile("0")
    rect(t, 5, 0, 15, 15, "9")
    rect(t, 0, 0, 1, 15, "w")
    for y in (3, 7, 11, 15):
        rect(t, 0, y, 15, y, "5")
    return t


# ---------------------------------------------------------------------------------------------------
# Clôture électrique : un poteau de fer gris, deux fils barbelés en travers, des bandes de signalisation en bas.
# Calée sur le découpage des modèles de clôture de Minecraft : poteau sur les colonnes 6 à 9, fils sur les lignes
# 1 à 3 (haut) et 7 à 9 (bas), sur toute la largeur.
# ---------------------------------------------------------------------------------------------------
def cloture():
    t = toile("d")
    # Le poteau : colonnes 6 à 9, clair à gauche et sombre à droite.
    rect(t, 6, 0, 6, 15, "l")
    rect(t, 7, 0, 8, 15, "i")
    rect(t, 9, 0, 9, 15, "d")
    px(t, 6, 5, "w")
    px(t, 7, 12, "R")
    px(t, 8, 4, "r")
    # Les deux fils : un fond sombre pour le fil et ses ardillons, qui ressortent en clair.
    for haut in (1, 7):
        rect(t, 0, haut, 15, haut + 2, "x")
        for x in range(0, 16, 2):
            px(t, x, haut + 1, "l")
        for x in range(1, 16, 2):
            px(t, x, haut + 1, "i")
        for x in (1, 5, 9, 13):  # les ardillons, en croix autour du fil
            px(t, x, haut, "w")
            px(t, x, haut + 2, "w")
        px(t, 7, haut + 1, "B")  # l'attache de laiton au poteau
        px(t, 8, haut + 1, "y")
    # Les bandes de signalisation en bas du poteau.
    for y in (12, 13):
        for x in range(6, 10):
            px(t, x, y, "Y" if (x + y) % 2 == 0 else "K")
    return t


# ---------------------------------------------------------------------------------------------------
# Écriture : textures, modèles, états de blocs et modèles d'objets.
# ---------------------------------------------------------------------------------------------------
def png(nom, t):
    lot2.ecrire_png(image(t), ASSETS / f"textures/block/{nom}.png")


def modele(nom, valeur):
    lot2.ecrire_json(valeur, ASSETS / f"models/block/{nom}.json")


def objet(nom, parent):
    lot2.ecrire_json({"parent": parent}, ASSETS / f"models/item/{nom}.json")


def machine_orientable(nom, face, cote, dessus):
    """Un bloc orienté à quatre voyants : la face avant regarde vers FACING, comme le séchoir."""
    for etat, voyant in VOYANTS.items():
        png(f"{nom}_front_{etat}", face(voyant, etat))
        modele(f"{nom}_{etat}", {"parent": "minecraft:block/orientable", "textures": {
            "particle": f"{MOD}:block/{nom}_side",
            "front": f"{MOD}:block/{nom}_front_{etat}",
            "side": f"{MOD}:block/{nom}_side",
            "top": f"{MOD}:block/{nom}_top"}})
    png(f"{nom}_side", cote())
    png(f"{nom}_top", dessus())
    objet(nom, f"{MOD}:block/{nom}_running")
    variantes = {}
    for direction, rotation in ROTATIONS:
        for etat in ETATS:
            m = {"model": f"{MOD}:block/{nom}_{etat}"}
            if rotation:
                m["y"] = rotation
            variantes[f"facing={direction},state={etat}"] = m
    lot2.ecrire_json({"variants": variantes}, ASSETS / f"blockstates/{nom}.json")


def ecrire_plaque():
    nom = "hot_plate"
    png(f"{nom}_top", plaque_dessus(False))
    png(f"{nom}_top_lit", plaque_dessus(True))
    png(f"{nom}_bottom", tole_dessous())
    variantes = {}
    for etat, voyant in VOYANTS.items():
        png(f"{nom}_side_{etat}", plaque_cote(voyant))
        for allumee in (False, True):
            suffixe = "lit" if allumee else "off"
            modele(f"{nom}_{etat}_{suffixe}", {"parent": "minecraft:block/cube", "textures": {
                "particle": f"{MOD}:block/{nom}_side_{etat}",
                "down": f"{MOD}:block/{nom}_bottom",
                "up": f"{MOD}:block/{nom}_top{'_lit' if allumee else ''}",
                "north": f"{MOD}:block/{nom}_side_{etat}",
                "south": f"{MOD}:block/{nom}_side_{etat}",
                "east": f"{MOD}:block/{nom}_side_{etat}",
                "west": f"{MOD}:block/{nom}_side_{etat}"}})
            variantes[f"lit={str(allumee).lower()},state={etat}"] = {"model": f"{MOD}:block/{nom}_{etat}_{suffixe}"}
    objet(nom, f"{MOD}:block/{nom}_running_off")
    lot2.ecrire_json({"variants": variantes}, ASSETS / f"blockstates/{nom}.json")


def ecrire_electrificateur():
    nom = "electrifier"
    png(f"{nom}_top", electrificateur_dessus())
    png(f"{nom}_bottom", tole_dessous())
    png(f"{nom}_insulator", isolateur())
    elements = [{
        "from": [0, 0, 0], "to": [16, 11, 16],
        "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
            "north": {"uv": [0, 0, 16, 11], "texture": "#side", "cullface": "north"},
            "south": {"uv": [0, 0, 16, 11], "texture": "#side", "cullface": "south"},
            "west": {"uv": [0, 0, 16, 11], "texture": "#side", "cullface": "west"},
            "east": {"uv": [0, 0, 16, 11], "texture": "#side", "cullface": "east"}}}]
    for x in (2, 7, 12):
        elements.append({"from": [x, 11, 6], "to": [x + 2, 16, 10], "faces": {
            face: {"uv": [0, 0, 8, 8], "texture": "#insulator"} for face in ("north", "south", "west", "east", "up")}})
        for bas in (12, 14):  # les jupes
            elements.append({"from": [x - 1, bas, 5], "to": [x + 3, bas + 1, 11], "faces": {
                face: {"uv": [0, 0, 8, 8], "texture": "#insulator"}
                for face in ("north", "south", "west", "east", "up", "down")}})
    modele(f"{nom}_base", {"parent": "minecraft:block/block", "textures": {"particle": "#side"}, "elements": elements})
    for etat, voyant in VOYANTS.items():
        png(f"{nom}_side_{etat}", electrificateur_cote(voyant))
        modele(f"{nom}_{etat}", {"parent": f"{MOD}:block/{nom}_base", "textures": {
            "side": f"{MOD}:block/{nom}_side_{etat}",
            "top": f"{MOD}:block/{nom}_top",
            "bottom": f"{MOD}:block/{nom}_bottom",
            "insulator": f"{MOD}:block/{nom}_insulator"}})
    objet(nom, f"{MOD}:block/{nom}_running")
    lot2.ecrire_json({"variants": {f"state={etat}": {"model": f"{MOD}:block/{nom}_{etat}"} for etat in ETATS}},
                     ASSETS / f"blockstates/{nom}.json")


def ecrire_cloture():
    nom = "electric_fence"
    png(nom, cloture())
    for partie in ("post", "side", "inventory"):
        modele(f"{nom}_{partie}", {"parent": f"minecraft:block/fence_{partie}", "textures": {
            "texture": f"{MOD}:block/{nom}"}})
    objet(nom, f"{MOD}:block/{nom}_inventory")
    multipart = [{"apply": {"model": f"{MOD}:block/{nom}_post"}}]
    for direction, rotation in ROTATIONS:
        application = {"model": f"{MOD}:block/{nom}_side", "uvlock": True}
        if rotation:
            application["y"] = rotation
        multipart.append({"when": {direction: "true"}, "apply": application})
    lot2.ecrire_json({"multipart": multipart}, ASSETS / f"blockstates/{nom}.json")


def ecrire_tout():
    machine_orientable("incubator", couveuse_face, couveuse_cote, couveuse_dessus)
    ecrire_plaque()
    machine_orientable("canteen_pot", lambda voyant, etat: marmite_face(voyant), marmite_cote, marmite_dessus)
    ecrire_electrificateur()
    ecrire_cloture()


def planche(chemin):
    """Tout le lot à l'échelle 8 : une rangée par bloc."""
    from PIL import Image, ImageDraw

    ech = 8
    pas = 16 * ech + 8
    rangees = [
        ("incubator", [couveuse_face(v, e) for e, v in VOYANTS.items()] + [couveuse_cote(), couveuse_dessus()]),
        ("hot_plate", [plaque_dessus(False), plaque_dessus(True), tole_dessous()]
         + [plaque_cote(v) for v in VOYANTS.values()]),
        ("canteen_pot", [marmite_face(v) for v in VOYANTS.values()] + [marmite_cote(), marmite_dessus()]),
        ("electrifier", [electrificateur_cote(v) for v in VOYANTS.values()]
         + [electrificateur_dessus(), tole_dessous(), isolateur()]),
        ("electric_fence", [cloture()]),
    ]
    img = Image.new("RGBA", (pas * 7 + 8, pas * len(rangees) + 8), (139, 139, 139, 255))
    dessin = ImageDraw.Draw(img)
    y = 6
    for nom, toiles in rangees:
        for i, t in enumerate(toiles):
            img.alpha_composite(image(t).resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
        dessin.text((8, y), nom, fill=(255, 255, 255))
        y += pas
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
