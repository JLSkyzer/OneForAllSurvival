#!/usr/bin/env python3
"""Génère les textures et les modèles du lot 6 (vie de la ville) : chauffe-eau, pomme de douche, phare, icône du
Bien-être et texture du faisceau du phare.

Même palette et même style que les lots 2 à 5 : du matériel de survie bricolé avec ce qu'on a sauvé de l'épave, tôle
piquée, cuivre, laiton terni, verre. Aucune ressource téléchargée : tout est dessiné ici, pixel par pixel.

    python tools/gen_textures_lot6.py                  écrit les PNG et les modèles
    python tools/gen_textures_lot6.py --planche X.png  écrit aussi une planche de contrôle agrandie

Le chauffe-eau est un ballon de tôle cerclé avec un manomètre et un voyant, dans les quatre états des machines. La
pomme de douche est un arrosoir de récupération : un tuyau de cuivre et une rose de fer-blanc percée (modèle partiel,
sa forme exacte est dans ShowerHeadBlock). Le phare est une lanterne à lentille sous un dôme (forme dans LighthouseBlock).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from PIL import Image  # noqa: E402

import gen_textures_lot5 as base  # noqa: E402

lot2 = base.lot2
MOD = base.MOD
ASSETS = base.ASSETS
toile, rect, px, image, caisson, rivets, lampe = (base.toile, base.rect, base.px, base.image, base.caisson,
                                                  base.rivets, base.lampe)
VOYANTS, ETATS = base.VOYANTS, base.ETATS

# Teintes propres au lot 6 (aucune ne remplace une teinte existante).
lot2.PALETTE.update({
    "1": (255, 250, 214),  # cœur de la lampe allumée
    "2": (255, 226, 140),  # lampe allumée
    "3": (214, 150, 52),  # lampe allumée, bord
    "J": (150, 208, 244),  # filet d'eau, bleu clair
})

# L'icône du Bien-être (18 x 18, comme les effets de Minecraft).
AMBRE = (242, 180, 90, 255)
AMBRE_CLAIR = (255, 220, 150, 255)
AMBRE_SOMBRE = (186, 116, 40, 255)
VAPEUR = (255, 255, 255, 200)


def goutte():
    """Une goutte d'eau chaude ambrée et deux filets de vapeur."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    p = img.load()
    for y in range(18):
        for x in range(18):
            if y >= 8:
                dedans = (x - 8.5) ** 2 + (y - 11.5) ** 2 <= 5.2 ** 2
            else:
                dedans = abs(x - 8.5) <= (y - 3) * 0.95 and y >= 3
            if dedans:
                p[x, y] = AMBRE
    for y in range(18):
        for x in range(18):
            if p[x, y] == AMBRE and (x - 8.5) ** 2 + (y - 11.5) ** 2 > 3.9 ** 2 and y >= 9:
                p[x, y] = AMBRE_SOMBRE
    for x, y in ((6, 11), (6, 12), (7, 10), (7, 13)):
        p[x, y] = AMBRE_CLAIR
    for x, y in ((6, 0), (7, 1), (6, 2), (11, 0), (12, 1), (11, 2)):
        p[x, y] = VAPEUR
    return img


# ---------------------------------------------------------------------------------------------------
# Chauffe-eau : un ballon de tôle cerclé (le fût des lots précédents), un manomètre sur le devant et un voyant. L'aiguille
# dit l'état : couchée à gauche à l'arrêt et sans courant, dans le vert en chauffe, dans le rouge en panne. Un tuyau de
# cuivre sort en bas à gauche, une soupape de laiton coiffe le dessus.
# ---------------------------------------------------------------------------------------------------
def ballon():
    """Le ballon vu de côté, sans cadre : tôle de zinc rivetée, bords ombrés et bande claire verticale qui l'arrondit,
    trois cerclages de cuivre sur toute la largeur. Les faces se raccordent d'un côté à l'autre du bloc."""
    t = toile("Z")
    for x0, x1, c in ((0, 1, "z"), (2, 3, "Z"), (4, 5, "q"), (6, 7, "q"), (8, 9, "Z"), (10, 13, "Z"), (14, 15, "z")):
        rect(t, x0, 0, x1, 15, c)
    rect(t, 5, 0, 6, 15, "q")
    for y in (4, 10):  # les rivets de la tôle, entre les cerclages
        px(t, 3, y, "z")
        px(t, 12, y, "z")
    for y in (1, 7, 13):  # un cerclage : liseré clair, cuivre, ombre, rivets de laiton
        rect(t, 0, y, 15, y, "e")
        rect(t, 0, y + 1, 15, y + 1, "C")
        rect(t, 0, y + 2, 15, y + 2, "c")
        for x in (2, 8, 13):
            px(t, x, y + 1, "y")
    return t


CADRAN = (  # le manomètre (6 x 6) : « K » le boîtier, « f » le cadran
    ".KKKK.",
    "KffffK",
    "KffffK",
    "KffffK",
    "KffffK",
    ".KKKK.",
)
AIGUILLES = {  # pixels de l'aiguille, dans le cadran posé en (3, 5) ; le pivot est en (5, 9)
    "stopped": ((4, 9), (4, 8)),
    "no_power": ((4, 9), (4, 8)),
    "running": ((6, 8), (7, 7)),
    "fault": ((5, 8), (5, 7), (5, 6)),
}


def chauffe_eau_face(voyant, etat):
    t = ballon()
    # Le manomètre, posé sur le cerclage du milieu : zone rouge à gauche, verte à droite, l'aiguille de l'état.
    for dy, ligne in enumerate(CADRAN):
        for dx, c in enumerate(ligne):
            if c != ".":
                px(t, 3 + dx, 5 + dy, c)
    px(t, 4, 6, "X")
    px(t, 7, 6, "G")
    px(t, 7, 7, "G")
    for x, y in AIGUILLES[etat]:
        px(t, x, y, "X" if etat == "fault" else "K")
    px(t, 5, 9, "K")
    # Le voyant, serti à droite du manomètre.
    lampe(t, 11, 7, voyant)
    # Le tuyau de cuivre qui sort du ballon, sous le manomètre.
    rect(t, 0, 11, 9, 11, "e")
    rect(t, 0, 12, 9, 12, "c")
    rect(t, 10, 11, 11, 12, "K")
    px(t, 10, 11, "B")
    px(t, 10, 12, "b")
    return t


def chauffe_eau_cote():
    t = ballon()
    # Une plaque de réparation rivetée entre les deux cerclages du haut, une trace de rouille sous le cerclage du milieu.
    rect(t, 9, 3, 13, 5, "K")
    rect(t, 10, 4, 12, 4, "l")
    px(t, 10, 3, "K")
    px(t, 3, 10, "R")
    px(t, 3, 11, "r")
    return t


def chauffe_eau_dessus():
    t = toile("Z")
    rect(t, 0, 0, 15, 0, "q")
    rect(t, 0, 0, 0, 15, "q")
    rect(t, 15, 0, 15, 15, "z")
    rect(t, 0, 15, 15, 15, "z")
    # Le couvercle : un cerclage de cuivre carré, la soupape de laiton au centre, le départ du tuyau.
    rect(t, 2, 2, 13, 13, "c")
    rect(t, 3, 3, 12, 12, "C")
    rect(t, 4, 4, 11, 11, "Z")
    rect(t, 4, 4, 11, 4, "q")
    rect(t, 6, 6, 9, 9, "b")
    rect(t, 7, 7, 8, 8, "B")
    px(t, 7, 7, "y")
    rect(t, 11, 7, 13, 8, "C")
    px(t, 12, 7, "e")
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(t, x, y, "K")
    return t


# ---------------------------------------------------------------------------------------------------
# Pomme de douche : un arrosoir de récupération. Une seule texture de 16 x 16 : le tuyau en haut à gauche, la bordure de
# la rose en dessous, le dessus de la rose en haut à droite, le dessous percé en bas à droite.
# ---------------------------------------------------------------------------------------------------
def pomme():
    t = [["." for _ in range(16)] for _ in range(16)]
    # Le tuyau (2 x 5) : cuivre clair à gauche, sombre à droite, un collier de laiton.
    rect(t, 0, 0, 0, 4, "e")
    rect(t, 1, 0, 1, 4, "c")
    rect(t, 0, 2, 1, 2, "B")
    px(t, 1, 2, "b")
    # La bordure de la rose (8 x 2) : fer-blanc, liseré noir.
    rect(t, 0, 6, 7, 6, "q")
    rect(t, 0, 7, 7, 7, "Z")
    px(t, 0, 6, "w")
    for x in (2, 5):
        px(t, x, 7, "z")
    rect(t, 0, 8, 7, 8, "K")
    # Le dessus de la rose (8 x 8) : un disque de fer-blanc, la vis de fixation du tuyau au centre.
    rect(t, 8, 0, 15, 7, "Z")
    rect(t, 8, 0, 15, 0, "K")
    rect(t, 8, 7, 15, 7, "K")
    rect(t, 8, 0, 8, 7, "K")
    rect(t, 15, 0, 15, 7, "K")
    rect(t, 9, 1, 14, 1, "q")
    rect(t, 9, 2, 9, 6, "q")
    rect(t, 14, 2, 14, 6, "z")
    rect(t, 10, 6, 13, 6, "z")
    rect(t, 11, 3, 12, 4, "B")
    px(t, 11, 3, "y")
    # Le dessous (8 x 8) : le disque percé, quinze trous noirs en quinconce.
    rect(t, 8, 8, 15, 15, "z")
    rect(t, 8, 8, 15, 8, "K")
    rect(t, 8, 15, 15, 15, "K")
    rect(t, 8, 8, 8, 15, "K")
    rect(t, 15, 8, 15, 15, "K")
    for y in (10, 12, 14):
        for x in range(9 + (y // 2) % 2, 15, 2):
            if 9 <= x <= 14 and y <= 14:
                px(t, x, y, "x")
    rect(t, 9, 9, 14, 9, "Z")
    return t


def filets():
    """Des filets d'eau (6 x 9) : fond transparent (rendu en découpe), des traits verticaux d'un pixel, d'un seul bleu
    clair, décalés d'une colonne à l'autre, et quelques gouttes blanches."""
    t = [["." for _ in range(16)] for _ in range(16)]
    for x, haut, bas in ((1, 0, 8), (3, 2, 7), (5, 0, 5)):
        for y in range(haut, bas + 1):
            px(t, x, y, "J")
    for x, y in ((0, 4), (2, 8), (4, 3), (4, 8), (5, 7)):
        px(t, x, y, "w")
    return t


def modele_pomme(actif):
    face4 = {"north": [0, 0, 2, 5], "east": [0, 0, 2, 5], "south": [0, 0, 2, 5], "west": [0, 0, 2, 5]}
    elements = [
        {"from": [7, 11, 7], "to": [9, 16, 9], "faces": {f: {"uv": uv, "texture": "#0"} for f, uv in face4.items()}},
        {"from": [4, 9, 4], "to": [12, 11, 12], "faces": {
            "north": {"uv": [0, 6, 8, 8], "texture": "#0"}, "east": {"uv": [0, 6, 8, 8], "texture": "#0"},
            "south": {"uv": [0, 6, 8, 8], "texture": "#0"}, "west": {"uv": [0, 6, 8, 8], "texture": "#0"},
            "up": {"uv": [8, 0, 16, 8], "texture": "#0"}, "down": {"uv": [8, 8, 16, 16], "texture": "#0"}}},
    ]
    textures = {"particle": f"{MOD}:block/shower_head", "0": f"{MOD}:block/shower_head"}
    modele = {"textures": textures, "elements": elements}
    if actif:
        textures["2"] = f"{MOD}:block/shower_water"
        modele["render_type"] = "minecraft:cutout"
        elements.append({"from": [5, 0, 5], "to": [11, 9, 11], "faces": {
            f: {"uv": [0, 0, 6, 9], "texture": "#2"} for f in ("north", "east", "south", "west")}})
    return modele


# ---------------------------------------------------------------------------------------------------
# Phare : une embase de tôle rivetée à bande de signalisation, une lanterne à lentille sous un dôme de tôle. Allumée, la
# lentille est blanche au cœur et jaune chaud sur le bord, comme une lampe de phare.
# ---------------------------------------------------------------------------------------------------
def phare_base():
    t = caisson("l", "i", "d", "w")
    rivets(t)
    for x, y in ((7, 3), (4, 7), (11, 9), (8, 11)):
        px(t, x, y, "r")
    px(t, 5, 5, "R")
    # La bande de signalisation, en bas des côtés de l'embase.
    for x in range(1, 15):
        px(t, x, 13, "Y" if x % 2 == 0 else "K")
        px(t, x, 14, "K" if x % 2 == 0 else "Y")
    return t


def phare_lampe(allumee):
    t = toile("K")
    # Le verre occupe les colonnes 5 à 10 et les lignes 4 à 10 : c'est la fenêtre de l'élément du modèle.
    verre = ("2", "1", "3") if allumee else ("A", "a", "z")  # bord, cœur, ombre
    rect(t, 5, 4, 10, 10, verre[0])
    # Les anneaux de la lentille de Fresnel : un disque clair au centre, deux anneaux autour.
    rect(t, 6, 5, 9, 9, verre[2] if not allumee else "2")
    rect(t, 6, 6, 9, 8, verre[0] if not allumee else "1")
    rect(t, 7, 6, 8, 8, verre[1])
    if not allumee:
        px(t, 7, 7, "z")
    # Les deux montants de fer sur les bords.
    rect(t, 5, 4, 5, 10, "d")
    rect(t, 10, 4, 10, 10, "d")
    px(t, 5, 4, "K")
    px(t, 10, 10, "K")
    return t


def faisceau():
    """La texture du faisceau (16 x 16) : blanc-jaune chaud, vif au centre et qui s'efface vers les bords en largeur ;
    constante en longueur, puisque le faisceau la répète."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        centre = 1.0 - abs((x - 7.5) / 8.0)
        alpha = int(255 * (0.45 + 0.55 * centre ** 1.5))
        for y in range(16):
            img.putpixel((x, y), (255, 244, 190, alpha))
    return img


def modele_phare(allumee):
    suffixe = "_lit" if allumee else ""
    uv_cote = [4, 4, 12, 12]
    elements = [
        {"from": [2, 0, 2], "to": [14, 6, 14], "faces": {
            **{f: {"uv": [2, 10, 14, 16], "texture": "#0"} for f in ("north", "south", "east", "west")},
            "up": {"uv": [2, 2, 14, 14], "texture": "#0"}, "down": {"uv": [2, 2, 14, 14], "texture": "#0"}}},
        {"from": [4, 6, 4], "to": [12, 7, 12], "faces": {
            **{f: {"uv": [4, 8, 12, 9], "texture": "#0"} for f in ("north", "south", "east", "west")},
            "up": {"uv": [4, 4, 12, 12], "texture": "#0"}}},
        {"from": [5, 7, 5], "to": [11, 14, 11], "faces": {
            f: {"uv": [5, 4, 11, 11], "texture": "#1"} for f in ("north", "south", "east", "west")}},
        {"from": [4, 14, 4], "to": [12, 15, 12], "faces": {
            **{f: {"uv": [4, 8, 12, 9], "texture": "#0"} for f in ("north", "south", "east", "west")},
            "up": {"uv": uv_cote, "texture": "#0"}, "down": {"uv": uv_cote, "texture": "#0"}}},
        {"from": [6, 15, 6], "to": [10, 16, 10], "faces": {
            **{f: {"uv": [6, 8, 10, 9], "texture": "#0"} for f in ("north", "south", "east", "west")},
            "up": {"uv": [6, 6, 10, 10], "texture": "#0"}}},
    ]
    return {"textures": {"particle": f"{MOD}:block/lighthouse_base", "0": f"{MOD}:block/lighthouse_base",
                         "1": f"{MOD}:block/lighthouse_lamp{suffixe}"}, "elements": elements}


# ---------------------------------------------------------------------------------------------------
# Écriture
# ---------------------------------------------------------------------------------------------------
def png(nom, t):
    lot2.ecrire_png(image(t), ASSETS / f"textures/block/{nom}.png")


def ecrire_tout():
    icone = ASSETS / "textures/mob_effect/well_being.png"
    if not icone.exists() or Image.open(icone).convert("RGBA").tobytes() != goutte().tobytes():
        lot2.ecrire_png(goutte(), icone)
    for etat, voyant in VOYANTS.items():
        png(f"water_heater_front_{etat}", chauffe_eau_face(voyant, etat))
    png("water_heater_side", chauffe_eau_cote())
    png("water_heater_top", chauffe_eau_dessus())
    png("shower_head", pomme())
    png("shower_water", filets())
    lot2.ecrire_json(modele_pomme(False), ASSETS / "models/block/shower_head.json")
    lot2.ecrire_json(modele_pomme(True), ASSETS / "models/block/shower_head_active.json")
    png("lighthouse_base", phare_base())
    png("lighthouse_lamp", phare_lampe(False))
    png("lighthouse_lamp_lit", phare_lampe(True))
    lot2.ecrire_json(modele_phare(False), ASSETS / "models/block/lighthouse.json")
    lot2.ecrire_json({"parent": f"{MOD}:block/lighthouse", "textures": {"1": f"{MOD}:block/lighthouse_lamp_lit"}},
                     ASSETS / "models/block/lighthouse_lit.json")
    lot2.ecrire_png(faisceau(), ASSETS / "textures/misc/lighthouse_beam.png")


def planche(chemin):
    """Tout le lot à l'échelle 8 : une rangée par bloc."""
    from PIL import ImageDraw

    ech = 8
    pas = 16 * ech + 8
    rangees = [
        ("water_heater", [chauffe_eau_face(v, e) for e, v in VOYANTS.items()] + [chauffe_eau_cote(),
                                                                                   chauffe_eau_dessus()]),
        ("shower_head", [pomme(), filets()]),
        ("lighthouse", [phare_base(), phare_lampe(False), phare_lampe(True)]),
    ]
    img = Image.new("RGBA", (pas * 6 + 8, pas * len(rangees) + 8), (139, 139, 139, 255))
    dessin = ImageDraw.Draw(img)
    y = 6
    for nom, toiles in rangees:
        for i, t in enumerate(toiles):
            tuile = t if isinstance(t, Image.Image) else image(t)
            img.alpha_composite(tuile.resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
        dessin.text((8, y), nom, fill=(255, 255, 255))
        y += pas
    chemin.parent.mkdir(parents=True, exist_ok=True)
    img.save(chemin)


if __name__ == "__main__":
    import argparse

    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("--planche", type=Path, help="écrit aussi une planche de contrôle agrandie")
    options = arguments.parse_args()
    ecrire_tout()
    if options.planche:
        planche(options.planche)
