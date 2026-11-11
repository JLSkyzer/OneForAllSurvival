#!/usr/bin/env python3
"""Textures du lot 7 : la cache enfouie (une caisse de tole rouillee), le detecteur de metaux, la lampe frontale
(objet et couche portee), le recepteur de signaux (trois etats) et le mecanisme ancien (quatre etages et actif),
16 x 16, dessinees ici pixel par pixel.

    python tools/gen_textures_lot7.py
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"
OUT = ASSETS / "block"


def cache():
    """Une caisse de tole rouillee, cerclee de deux sangles rivetees, avec des coulures de rouille."""
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            p[x, y] = rnd.choice([(118, 72, 44), (128, 78, 46), (106, 64, 40), (138, 86, 50)]) + (255,)
    for _ in range(14):  # plaques de rouille vive
        p[rnd.randrange(16), rnd.randrange(16)] = rnd.choice([(168, 90, 40), (150, 78, 34)]) + (255,)
    for i in range(16):  # bord de caisse
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            p[x, y] = (62, 46, 38, 255)
    for band in ((3, 4), (11, 12)):  # sangles de fer cerclant la caisse
        for y in band:
            for x in range(16):
                p[x, y] = (78, 76, 74, 255) if y == band[0] else (58, 56, 55, 255)
        for x in range(2, 15, 4):  # rivets
            p[x, band[0]] = (184, 176, 160, 255)
    for (x, y) in ((6, 5), (6, 6), (7, 7), (10, 5), (10, 6), (10, 7), (10, 8), (3, 13), (3, 14)):  # coulures
        p[x, y] = (86, 46, 26, 255)
    return img


def detecteur():
    """Un manche de tuyau gris et un disque de tole au bout, en diagonale, comme un outil tenu."""
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for i in range(9):  # tuyau
        p[2 + i, 13 - i] = (150, 154, 158, 255)
        p[3 + i, 13 - i] = (92, 96, 102, 255)
    for (x, y) in ((1, 14), (2, 14), (3, 14), (2, 13), (4, 11), (5, 10)):  # poignee de ruban
        p[x, y] = (122, 66, 36, 255)
    cx, cy = 11, 4  # disque de tole
    for y in range(0, 9):
        for x in range(7, 16):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= 13:
                p[x, y] = (176, 180, 184, 255) if d <= 7 else (104, 108, 114, 255)
            elif d <= 17 and p[x, y][3] == 0:
                p[x, y] = (38, 34, 33, 255)
    for (x, y) in ((11, 4), (10, 4), (11, 3)):
        p[x, y] = (64, 184, 120, 255)  # voyant
    p[10, 3] = (230, 236, 240, 255)
    return img


def lampe_objet():
    """Une sangle de cuir en boucle avec un reflecteur rond et une lentille jaune."""
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for x in range(2, 14):  # sangle
        p[x, 9] = (126, 78, 44, 255)
        p[x, 10] = (88, 54, 32, 255)
    for y in range(5, 9):  # attaches
        p[2, y] = p[13, y] = (96, 60, 36, 255)
    for x in range(2, 14, 3):  # coutures
        p[x, 9] = (200, 160, 100, 255)
    cx, cy = 8, 7  # reflecteur
    for y in range(2, 13):
        for x in range(4, 13):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= 14:
                p[x, y] = (156, 160, 166, 255) if d > 6 else (250, 214, 84, 255)
            elif d <= 18 and p[x, y][3] == 0:
                p[x, y] = (36, 32, 31, 255)
    for (x, y) in ((7, 6), (8, 6), (7, 7)):
        p[x, y] = (255, 248, 200, 255)
    return img


def lampe_portee():
    """Couche 1 de l'armure (64 x 32) : seule la zone du casque est dessinee, sangle de cuir et reflecteur."""
    img = Image.new("RGBA", (64, 32))
    p = img.load()
    cuir = [(126, 78, 44, 255), (112, 68, 38, 255)]
    for x in range(0, 32):  # sangle autour de la tete (rangees 10 et 11 des quatre faces laterales)
        for y in (10, 11):
            p[x, y] = cuir[(x + y) % 2]
    for y in range(0, 8):  # sangle de dessus, de l'avant vers l'arriere (colonnes 11 et 12 du dessus)
        for x in (11, 12):
            p[x, y] = cuir[y % 2]
    for y in range(8, 10):  # attache au-dessus de la sangle, sur la face et le dos
        for x in (11, 12, 27, 28):
            p[x, y] = cuir[0]
    cx, cy = 11.5, 9.5  # reflecteur sur la face (8..15, 8..15) : disque gris, lentille jaune
    for y in range(8, 16):
        for x in range(8, 16):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= 4.5:
                p[x, y] = (156, 160, 166, 255) if d > 1.7 else (250, 214, 84, 255)
            elif d <= 6.5:
                p[x, y] = (40, 36, 34, 255)
    for (x, y) in ((10, 9), (11, 9)):
        p[x, y] = (255, 248, 200, 255)
    return img


def recepteur(led, dial, band=None):
    """Un poste de radio de recuperation : coffret de tole, haut-parleur, cadran, bouton, voyant, poignee de ficelle."""
    rnd = random.Random(23)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            v = rnd.choice((74, 78, 82, 86))
            p[x, y] = (v, v + 3, v + 6, 255)
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            p[x, y] = (36, 38, 42, 255)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        p[x, y] = (190, 182, 166, 255)  # vis
    for y in range(3, 10):  # haut-parleur
        for x in range(2, 8):
            p[x, y] = (24, 26, 28, 255) if (x + y) % 2 else (50, 52, 56, 255)
    for x in range(8, 14):  # cadran
        for y in range(3, 6):
            p[x, y] = dial
    for (x, y) in ((9, 7), (10, 7), (9, 8), (10, 8)):  # bouton
        p[x, y] = (226, 150, 84, 255) if (x, y) == (9, 7) else (176, 96, 44, 255)
    for (x, y) in ((12, 7), (13, 7), (12, 8), (13, 8)):  # voyant
        p[x, y] = led
    if band:  # message non lu : bandeaux de couleur en haut et en bas, lisibles de loin
        for x in range(1, 15):
            for y in (1, 2, 13, 14):
                p[x, y] = band
    if not band:
        for x in range(3, 13):  # poignee de ficelle
            p[x, 12] = (120, 82, 50, 255)
            p[x, 13] = (86, 58, 36, 255)
    return img


COPPER_OFF = (112, 66, 42, 255)
COPPER_ON = (238, 142, 70, 255)
COPPER_HOT = (255, 214, 140, 255)

# quatre branches de veines parties du centre, de l'interieur vers l'exterieur
VEINES = [
    [(7, 6), (7, 5), (7, 4), (6, 3), (6, 2), (6, 1)],
    [(9, 7), (10, 7), (11, 7), (12, 6), (13, 6), (14, 6)],
    [(8, 9), (8, 10), (8, 11), (9, 12), (9, 13), (9, 14)],
    [(6, 8), (5, 8), (4, 8), (3, 9), (2, 9), (1, 9)],
]


def mecanisme(stage, active=False):
    """Pierre taillee sombre, veinee de cuivre : les etages 1 a 3 allument les branches de l'interieur vers l'exterieur."""
    rnd = random.Random(31)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            v = rnd.choice((54, 58, 62, 66))
            p[x, y] = (v, v - 1, v + 3, 255)
    for i in range(16):  # joints de taille et biseau
        for (x, y) in ((i, 0), (0, i), (i, 15), (15, i)):
            p[x, y] = (30, 30, 34, 255)
        if 0 < i < 15:
            p[i, 1] = p[1, i] = (82, 82, 88, 255)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8), (6, 7), (9, 8)):  # coeur creuse
        p[x, y] = (38, 36, 38, 255)
    lit = 2 * stage
    for branch in VEINES:
        for k, (x, y) in enumerate(branch):
            if active:
                # actif : veines de deux points d'epaisseur, coeur blanc-jaune, halo orange autour du coeur
                for (dx, dy) in ((0, 0), (1, 0), (0, 1)):
                    if 0 < x + dx < 15 and 0 < y + dy < 15:
                        p[x + dx, y + dy] = COPPER_HOT if k % 2 == 0 else COPPER_ON
            else:
                p[x, y] = COPPER_ON if k < lit else COPPER_OFF
    if active:
        for y in range(5, 11):
            for x in range(5, 11):
                p[x, y] = COPPER_ON
        for y in range(6, 10):
            for x in range(6, 10):
                p[x, y] = COPPER_HOT
        for (x, y) in ((7, 7), (8, 8), (7, 8), (8, 7)):
            p[x, y] = (255, 255, 235, 255)
    core = COPPER_HOT if active else (COPPER_ON if stage >= 3 else COPPER_OFF)
    for (x, y) in ((7, 7), (8, 8)):
        p[x, y] = core
    return img


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    cache().save(OUT / "buried_cache.png")
    print("ecrit", OUT / "buried_cache.png")
    (ASSETS / "item").mkdir(parents=True, exist_ok=True)
    detecteur().save(ASSETS / "item" / "metal_detector.png")
    print("ecrit", ASSETS / "item" / "metal_detector.png")
    lampe_objet().save(ASSETS / "item" / "headlamp.png")
    print("ecrit", ASSETS / "item" / "headlamp.png")
    (ASSETS / "models" / "armor").mkdir(parents=True, exist_ok=True)
    lampe_portee().save(ASSETS / "models" / "armor" / "headlamp_layer_1.png")
    print("ecrit", ASSETS / "models" / "armor" / "headlamp_layer_1.png")
    for suffix, led, dial in (("", (70, 70, 72, 255), (38, 40, 44, 255)),
                              ("_on", (96, 214, 96, 255), (150, 190, 120, 255)),
                              ("_unread", (255, 200, 40, 255), (255, 176, 40, 255))):
        recepteur(led, dial, (240, 160, 30, 255) if suffix == "_unread" else None).save(OUT / ("signal_receiver" + suffix + ".png"))
        print("ecrit", OUT / ("signal_receiver" + suffix + ".png"))
    for stage in range(4):
        mecanisme(stage).save(OUT / ("ancient_mechanism_%d.png" % stage))
        print("ecrit", OUT / ("ancient_mechanism_%d.png" % stage))
    mecanisme(3, True).save(OUT / "ancient_mechanism_active.png")
    print("ecrit", OUT / "ancient_mechanism_active.png")
