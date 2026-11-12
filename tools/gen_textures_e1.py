#!/usr/bin/env python3
"""Textures du lot E1 (monnaie), PROVISOIRES : la tache 6 du lot fait les visuels definitifs.

Pour l'instant : le coffre-fort personnel (tole d'acier rivetee, cadran et poignee de laiton), 16 x 16.

    python tools/gen_textures_e1.py
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"
OUT = ASSETS / "block"


def coffre_fort():
    """Une tole d'acier grise cerclee d'un cadre, quatre rivets, un cadran et une poignee de laiton."""
    rnd = random.Random(401)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            p[x, y] = rnd.choice([(92, 96, 102), (98, 102, 108), (86, 90, 96), (104, 108, 114)]) + (255,)
    for i in range(16):  # cadre sombre
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            p[x, y] = (38, 40, 44, 255)
    for i in range(1, 15):  # biseau clair en haut et a gauche, sombre en bas et a droite
        p[i, 1] = (134, 138, 144, 255)
        p[1, i] = (134, 138, 144, 255)
        p[i, 14] = (62, 65, 70, 255)
        p[14, i] = (62, 65, 70, 255)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):  # rivets
        p[x, y] = (190, 184, 170, 255)
        p[x + 1, y + 1] = (50, 52, 56, 255)
    cx, cy = 7, 7  # cadran de laiton
    for y in range(16):
        for x in range(16):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= 11:
                p[x, y] = (200, 150, 60, 255) if d <= 6 else (122, 85, 25, 255)
    p[7, 7] = (56, 40, 14, 255)
    for (x, y) in ((7, 4), (7, 5), (7, 6)):  # aiguille du cadran
        p[x, y] = (56, 40, 14, 255)
    for y in range(9, 13):  # poignee de laiton
        p[11, y] = (233, 192, 110, 255)
        p[12, y] = (122, 85, 25, 255)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    coffre_fort().save(OUT / "safe.png")
    print("ecrit", OUT / "safe.png")


if __name__ == "__main__":
    main()
