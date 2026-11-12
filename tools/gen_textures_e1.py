#!/usr/bin/env python3
"""Textures du lot E1 (monnaie), 16 x 16 : le coffre-fort personnel (coffre d'acier riveté, face a cadran de laiton et
levier, flancs et dessus rivetes) et la bourse (sac de cuir noue d'une cordelette). Les pieces, elles, viennent de
art/pieces-monnaie.

    python tools/gen_textures_e1.py
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"
OUT = ASSETS / "block"
OUT_ITEM = ASSETS / "item"


ACIER = [(92, 96, 102), (98, 102, 108), (86, 90, 96), (104, 108, 114)]
CADRE = (38, 40, 44, 255)
CLAIR = (134, 138, 144, 255)
OMBRE = (62, 65, 70, 255)
LAITON = [(122, 85, 25, 255), (200, 150, 60, 255), (233, 192, 110, 255), (56, 40, 14, 255)]


def tole(rnd, cadre=True):
    """Une tole d'acier grise, un peu tachee, cerclee d'un cadre sombre et biseautee."""
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            p[x, y] = rnd.choice(ACIER) + (255,)
    if cadre:
        for i in range(16):
            for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
                p[x, y] = CADRE
        for i in range(1, 15):
            p[i, 1] = CLAIR
            p[1, i] = CLAIR
            p[i, 14] = OMBRE
            p[14, i] = OMBRE
    return img


def rivet(p, x, y):
    """Un rivet : tete claire, ombre portee en bas a droite."""
    p[x, y] = (196, 190, 176, 255)
    p[x + 1, y + 1] = (50, 52, 56, 255)


def coffre_fort_cote():
    """Flanc : tole rivetee sur le pourtour, une soudure de renfort au milieu."""
    rnd = random.Random(411)
    img = tole(rnd)
    p = img.load()
    for (x, y) in ((3, 3), (11, 3), (3, 11), (11, 11)):
        rivet(p, x, y)
    for x in range(3, 13):  # cordon de soudure, irregulier
        p[x, 7 + (x % 3 == 0)] = (70, 73, 79, 255)
        p[x, 8 - (x % 2 == 0)] = (112, 116, 122, 255)
    return img


def coffre_fort_dessus():
    """Dessus : tole plus claire, quatre rivets aux coins et un cordon de renfort en croix."""
    rnd = random.Random(412)
    img = tole(rnd)
    p = img.load()
    for i in range(3, 13):
        p[i, 7] = (78, 82, 88, 255)
        p[7, i] = (78, 82, 88, 255)
        p[i, 8] = (118, 122, 128, 255)
        p[8, i] = (118, 122, 128, 255)
    for (x, y) in ((3, 3), (11, 3), (3, 11), (11, 11)):
        rivet(p, x, y)
    return img


def coffre_fort_face():
    """Face : la porte. Rivets, cadran de laiton a aiguille a gauche, levier de verrouillage a droite, gond sombre."""
    rnd = random.Random(413)
    img = tole(rnd)
    p = img.load()
    # le pourtour de la porte, en retrait
    for i in range(3, 13):
        for (x, y) in ((i, 3), (i, 12), (3, i), (12, i)):
            p[x, y] = (54, 57, 62, 255)
    for i in range(4, 12):
        p[i, 4] = (120, 124, 130, 255)
        p[4, i] = (120, 124, 130, 255)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):  # rivets de coin, hors porte
        p[x, y] = (196, 190, 176, 255)
    for y in (5, 9):  # deux gonds a gauche
        p[3, y] = p[3, y + 1] = (30, 31, 34, 255)
    cx, cy = 7, 7  # cadran
    for y in range(16):
        for x in range(16):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= 6:
                p[x, y] = LAITON[1] if d <= 3 else LAITON[0]
    p[cx, cy] = LAITON[3]
    p[cx, cy - 1] = LAITON[3]  # aiguille
    p[cx, cy - 3] = LAITON[2]  # repere haut du cadran
    p[cx + 3, cy] = LAITON[2]
    for y in range(6, 11):  # levier : poignee verticale de laiton
        p[11, y] = LAITON[2]
    p[11, 10] = LAITON[1]
    p[10, 10] = p[10, 11] = (30, 31, 34, 255)  # son embase
    p[11, 11] = (30, 31, 34, 255)
    return img


def bourse():
    """Un sac de cuir rond, noue en haut d'une cordelette, couture sur le flanc, une piece de laiton au ventre."""
    rnd = random.Random(402)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    cuir = [(124, 82, 46), (134, 90, 52), (112, 74, 40), (140, 96, 56)]
    for y in range(5, 15):  # le ventre : une ellipse
        for x in range(2, 14):
            dx, dy = (x - 7.5) / 6.0, (y - 10.0) / 5.0
            if dx * dx + dy * dy <= 1.0:
                p[x, y] = rnd.choice(cuir) + (255,)
    for y in range(3, 6):  # le col
        for x in range(5, 11):
            p[x, y] = rnd.choice(cuir) + (255,)
    for x in (4, 5, 10, 11):  # le revers du col qui s'evase
        p[x, 2] = rnd.choice(cuir) + (255,)
    # contour sombre : tout pixel plein voisin du vide
    plein = [(x, y) for y in range(16) for x in range(16) if p[x, y][3]]
    for (x, y) in plein:
        for (nx, ny) in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if not (0 <= nx < 16 and 0 <= ny < 16) or not p[nx, ny][3]:
                p[x, y] = (72, 46, 24, 255)
                break
    for x in range(5, 11):  # la cordelette serree au col
        p[x, 5] = (214, 190, 120, 255)
    p[7, 6] = p[8, 6] = (214, 190, 120, 255)  # le noeud
    p[6, 6] = (176, 150, 88, 255)
    for (x, y) in ((6, 1), (9, 1), (5, 0), (10, 0)):  # les deux bouts de la cordelette, qui pendent
        p[x, y] = (214, 190, 120, 255)
    for (x, y) in ((4, 10), (4, 11), (5, 12), (6, 13), (7, 13)):  # ombre a gauche, en bas
        if p[x, y][3] and p[x, y] != (72, 46, 24, 255):
            p[x, y] = (96, 62, 34, 255)
    for (x, y) in ((10, 7), (11, 8), (11, 9)):  # reflet en haut a droite
        p[x, y] = (156, 110, 68, 255)
    for y in (8, 10, 12):  # points de couture sur le flanc droit
        p[12, y] = (214, 190, 120, 255)
    for y in range(8, 12):  # la piece de laiton, bosse sous le cuir
        for x in range(6, 10):
            p[x, y] = LAITON[1]
    for (x, y) in ((6, 8), (9, 8), (6, 11), (9, 11)):
        p[x, y] = LAITON[0]
    p[7, 9] = LAITON[2]
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for nom, fonction in (("safe_front", coffre_fort_face), ("safe_side", coffre_fort_cote), ("safe_top", coffre_fort_dessus)):
        fonction().save(OUT / (nom + ".png"))
        print("ecrit", OUT / (nom + ".png"))
    OUT_ITEM.mkdir(parents=True, exist_ok=True)
    bourse().save(OUT_ITEM / "purse.png")
    print("ecrit", OUT_ITEM / "purse.png")


if __name__ == "__main__":
    main()
