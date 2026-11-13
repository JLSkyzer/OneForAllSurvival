#!/usr/bin/env python3
"""Textures du lot E2 (commerce), 16 x 16. Pour l'instant : le recu (feuille de papier ecrue, un peu de biais, lignes
d'ecriture a l'encre, un tampon de laiton en bas a droite et un coin corne).

    python tools/gen_textures_e2.py
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"
OUT_ITEM = ASSETS / "item"

PAPIER = [(232, 222, 196), (226, 214, 186), (238, 230, 206), (220, 208, 178)]
BORD = (128, 112, 84, 255)
ENCRE = (58, 54, 66, 255)
ENCRE_PALE = (120, 112, 120, 255)
LAITON = [(122, 85, 25, 255), (200, 150, 60, 255), (233, 192, 110, 255)]


def recu():
    """Une feuille de 10 x 14, lignes d'ecriture, total souligne, tampon de laiton, coin haut droit corne."""
    rnd = random.Random(702)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    x0, x1, y0, y1 = 3, 12, 1, 14
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p[x, y] = rnd.choice(PAPIER) + (255,)
    # Le coin corne en haut a droite : le pli, puis le vide derriere.
    for (x, y) in ((12, 1), (12, 2), (11, 1)):
        p[x, y] = (0, 0, 0, 0)
    p[11, 2] = (196, 182, 150, 255)
    # Le contour.
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if not p[x, y][3]:
                continue
            for (nx, ny) in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if not (x0 <= nx <= x1 and y0 <= ny <= y1) or not p[nx, ny][3]:
                    p[x, y] = BORD
                    break
    p[11, 2] = BORD
    # L'en-tete, puis les lignes d'ecriture (longueurs variees).
    for x in range(5, 9):
        p[x, 3] = ENCRE
    for y, longueur in ((5, 6), (7, 5), (9, 6)):
        for x in range(5, 5 + longueur):
            p[x, y] = ENCRE_PALE if (x + y) % 4 == 0 else ENCRE
    # Le total, souligne.
    for x in range(5, 9):
        p[x, 11] = ENCRE
    # Le tampon de laiton, rond, en bas a droite.
    for (x, y) in ((9, 11), (10, 11), (9, 12), (10, 12)):
        p[x, y] = LAITON[1]
    p[9, 11] = LAITON[2]
    for (x, y) in ((8, 12), (11, 11), (9, 13), (10, 10)):
        p[x, y] = LAITON[0]
    return img


def main():
    OUT_ITEM.mkdir(parents=True, exist_ok=True)
    recu().save(OUT_ITEM / "receipt.png")
    print("ecrit", OUT_ITEM / "receipt.png")


if __name__ == "__main__":
    main()
