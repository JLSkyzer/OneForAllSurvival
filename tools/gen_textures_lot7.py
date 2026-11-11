#!/usr/bin/env python3
"""Textures provisoires du lot 7 : la cache enfouie (une caisse de tole rouillee), le detecteur de metaux et la lampe frontale (objet et couche portee), 16 x 16, dessinee ici pixel par pixel.
Les visuels finaux arrivent a la tache 6 du lot.

    python tools/gen_textures_lot7.py
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"
OUT = ASSETS / "block"


def cache():
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            base = rnd.choice([(120, 74, 44), (132, 82, 48), (108, 66, 40), (142, 90, 52)])
            p[x, y] = base + (255,)
    for i in range(16):  # cerclage de tole
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i), (i, 7), (i, 8)):
            p[x, y] = (72, 70, 68, 255) if (x + y) % 3 else (92, 90, 88, 255)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):  # rivets
        p[x, y] = (176, 170, 160, 255)
    return img


def detecteur():
    """Un manche de bois, un disque de fer et un voyant : en diagonale, comme un outil tenu."""
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for i in range(10):  # manche
        p[3 + i, 12 - i] = (112, 78, 46, 255)
        p[4 + i, 12 - i] = (66, 44, 28, 255)
    for (x, y) in ((11, 3), (12, 2), (13, 3), (12, 4), (11, 4), (13, 4), (12, 3), (10, 3), (14, 3), (12, 5), (11, 2), (13, 2)):
        p[x, y] = (104, 106, 110, 255)  # tete
    for (x, y) in ((11, 2), (12, 2), (13, 2), (10, 3), (14, 3), (11, 5), (12, 5), (13, 5)):
        p[x, y] = (34, 29, 27, 255)  # contour
    p[12, 3] = (111, 183, 216, 255)  # voyant
    for (x, y) in ((2, 13), (3, 14), (4, 14), (2, 14)):
        p[x, y] = (146, 80, 38, 255)  # bout rouille
    return img


def lampe_objet():
    """Un casque de tole grise avec un phare jaune sur le front."""
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(4, 13):
        for x in range(2, 14):
            if y == 4 and x in (2, 13) or y == 12 and 4 < x < 11:
                continue
            p[x, y] = (112, 116, 120, 255) if y < 10 else (84, 88, 92, 255)
    for x in range(2, 14):
        p[x, 4] = (34, 29, 27, 255) if p[x, 4][3] else p[x, 4]
        p[x, 9] = (60, 62, 66, 255)
    for (x, y) in ((6, 6), (7, 6), (8, 6), (9, 6), (6, 7), (7, 7), (8, 7), (9, 7)):
        p[x, y] = (244, 208, 72, 255)
    for (x, y) in ((7, 6), (8, 6)):
        p[x, y] = (255, 246, 190, 255)
    return img


def lampe_portee():
    """Couche 1 de l'armure (64 x 32) : seule la tete est dessinee. Provisoire, a refaire a la tache 6."""
    img = Image.new("RGBA", (64, 32))
    p = img.load()
    rnd = random.Random(11)
    # les six faces de la tete : dessus, dessous, droite, face, gauche, dos
    for (x0, y0, w, h) in ((8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                v = rnd.choice((104, 108, 112, 116))
                p[x, y] = (v, v + 4, v + 8, 255)
    for x in range(0, 32):  # bandeau sombre autour de la tete
        p[x, 14] = (60, 62, 66, 255)
    for x in range(8, 16):
        p[x, 8] = (46, 48, 52, 255)
    for (x, y) in ((10, 10), (11, 10), (12, 10), (13, 10), (10, 11), (11, 11), (12, 11), (13, 11)):
        p[x, y] = (244, 208, 72, 255)  # phare de face
    for (x, y) in ((11, 10), (12, 10)):
        p[x, y] = (255, 246, 190, 255)
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
