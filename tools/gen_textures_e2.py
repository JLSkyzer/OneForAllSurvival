#!/usr/bin/env python3
"""Textures du lot E2 (commerce), 16 x 16 : le recu (feuille de papier ecrue, lignes d'ecriture a l'encre, un tampon
de laiton en bas a droite et un coin corne), la facture (feuille a en-tete rouge, montant souligne, cachet de cire) et
le carnet de factures (couverture de cuir bordeaux, coins de laiton, tranche de papier, etiquette).

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


CIRE = [(110, 20, 24, 255), (168, 36, 40, 255), (214, 84, 78, 255)]
CUIR = [(74, 24, 30), (96, 32, 38), (118, 42, 46), (84, 28, 34)]
CUIR_BORD = (40, 12, 16, 255)


def feuille(rnd, img, x0, x1, y0, y1):
    """Une feuille de papier ecru, au contour d'encre pale."""
    p = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p[x, y] = rnd.choice(PAPIER) + (255,)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y in (y0, y1):
                p[x, y] = BORD


def facture():
    """Une feuille de 10 x 14 : en-tete rouge, lignes du motif, montant souligne deux fois, cachet de cire."""
    rnd = random.Random(721)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    feuille(rnd, img, 3, 12, 1, 14)
    p = img.load()
    # L'en-tete : un bandeau rouge sombre sur toute la largeur.
    for x in range(4, 12):
        p[x, 2] = CIRE[0]
        p[x, 3] = CIRE[1] if x % 3 else CIRE[0]
    # Les lignes du motif.
    for y, longueur in ((5, 6), (7, 4)):
        for x in range(5, 5 + longueur):
            p[x, y] = ENCRE_PALE if (x + y) % 3 == 0 else ENCRE
    # Le montant, a droite, souligne deux fois.
    for x in range(8, 11):
        p[x, 9] = ENCRE
    for x in range(7, 11):
        p[x, 10] = ENCRE_PALE
        p[x, 11] = ENCRE
    # Le cachet de cire, en bas a gauche.
    for (x, y) in ((5, 11), (6, 11), (5, 12), (6, 12)):
        p[x, y] = CIRE[1]
    p[5, 11] = CIRE[2]
    for (x, y) in ((4, 12), (7, 12), (5, 13), (6, 10)):
        p[x, y] = CIRE[0]
    return img


def carnet():
    """Un carnet de 11 x 13 : couverture de cuir, coins de laiton, tranche de papier a droite, etiquette ecrue."""
    rnd = random.Random(722)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    x0, x1, y0, y1 = 2, 12, 2, 14
    # La tranche des pages, visible a droite et en bas.
    for y in range(y0 + 1, y1 + 2):
        p[x1 + 1, y] = rnd.choice(PAPIER) + (255,)
    for x in range(x0 + 1, x1 + 2):
        p[x, y1 + 1] = rnd.choice(PAPIER) + (255,)
    p[x1 + 1, y1 + 1] = BORD
    # La couverture.
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y in (y0, y1):
                p[x, y] = CUIR_BORD
            else:
                p[x, y] = rnd.choice(CUIR) + (255,)
    # Le dos, plus sombre, a gauche.
    for y in range(y0 + 1, y1):
        p[x0 + 1, y] = CUIR[0] + (255,)
    # Les coins de laiton.
    for (x, y) in ((x1, y0), (x1 - 1, y0), (x1, y0 + 1), (x1, y1), (x1 - 1, y1), (x1, y1 - 1)):
        p[x, y] = LAITON[1]
    p[x1, y0] = LAITON[2]
    p[x1, y1] = LAITON[0]
    # L'etiquette ecrue, avec deux lignes d'encre.
    for y in range(5, 9):
        for x in range(5, 11):
            p[x, y] = rnd.choice(PAPIER) + (255,)
    for x in range(5, 11):
        p[x, 5] = LAITON[0]
        p[x, 8] = LAITON[0]
    for x in range(6, 10):
        p[x, 6] = ENCRE
    for x in range(6, 9):
        p[x, 7] = ENCRE_PALE
    return img


def main():
    OUT_ITEM.mkdir(parents=True, exist_ok=True)
    for nom, image in (("receipt", recu()), ("invoice", facture()), ("invoice_book", carnet())):
        image.save(OUT_ITEM / (nom + ".png"))
        print("ecrit", OUT_ITEM / (nom + ".png"))


if __name__ == "__main__":
    main()
