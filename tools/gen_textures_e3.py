#!/usr/bin/env python3
"""Textures du lot E3 (tresor et salaires), 16 x 16 : le bloc du tresor de la ville, un coffre de chene sombre cercle
de fer et de laiton. Face : une piece d'or frappee au centre, une serrure dessous ; flancs : planches et bandes de fer
rivetees ; dessus : couvercle borde de laiton. Et le tableau de la mairie : bois clair pour les pieds et les tranches,
parchemin sous cadre de bois pour la face (le texte est dessine par le rendu, pas ici).

    python tools/gen_textures_e3.py
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"
OUT = ASSETS / "block"

CHENE = [(66, 43, 24), (72, 47, 27), (60, 39, 22), (78, 52, 30)]
JOINT = (38, 24, 13, 255)
FER = (70, 72, 76, 255)
FER_CLAIR = (118, 120, 126, 255)
FER_OMBRE = (40, 41, 44, 255)
LAITON = [(122, 85, 25, 255), (200, 150, 60, 255), (233, 192, 110, 255), (56, 40, 14, 255)]
OR = [(150, 104, 18, 255), (222, 170, 40, 255), (250, 222, 120, 255), (96, 64, 10, 255)]


def planches(rnd, horizontales=True):
    """Des planches de chene sombre, joints tous les 4 pixels, veinees."""
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b = rnd.choice(CHENE)
            p[x, y] = (r, g, b, 255)
    for i in range(16):
        for k in (3, 7, 11):
            if horizontales:
                p[i, k] = JOINT
            else:
                p[k, i] = JOINT
    return img


def cercler(p):
    """Le cadre de fer du coffre : bord sombre, arete claire en haut et a gauche."""
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            p[x, y] = FER_OMBRE
    for i in range(1, 15):
        p[i, 1] = FER_CLAIR
        p[1, i] = FER_CLAIR
        p[i, 14] = FER
        p[14, i] = FER


def rivet(p, x, y):
    p[x, y] = (200, 194, 180, 255)
    p[x + 1, y + 1] = FER_OMBRE


def tresor_cote():
    """Flanc : planches horizontales, deux bandes de fer verticales rivetees."""
    rnd = random.Random(731)
    img = planches(rnd)
    p = img.load()
    cercler(p)
    for x in (4, 11):
        for y in range(2, 14):
            p[x, y] = FER
            p[x + 1, y] = FER_OMBRE if y % 4 == 0 else FER_CLAIR
    for (x, y) in ((4, 3), (11, 3), (4, 11), (11, 11)):
        rivet(p, x, y)
    return img


def tresor_dessus():
    """Dessus : couvercle de planches dans l'autre sens, bordure de laiton, poignee de fer au milieu."""
    rnd = random.Random(732)
    img = planches(rnd, horizontales=False)
    p = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            p[x, y] = LAITON[3]
    for i in range(1, 15):
        for (x, y) in ((i, 1), (1, i)):
            p[x, y] = LAITON[2]
        for (x, y) in ((i, 14), (14, i)):
            p[x, y] = LAITON[0]
    for x in range(5, 11):  # la poignee
        p[x, 7] = FER_CLAIR
        p[x, 8] = FER_OMBRE
    p[5, 6] = p[10, 6] = FER
    for (x, y) in ((3, 3), (11, 3), (3, 11), (11, 11)):
        rivet(p, x, y)
    return img


def tresor_face():
    """Face : planches, cadre de fer, une piece d'or frappee au centre et une serrure de laiton dessous."""
    rnd = random.Random(733)
    img = planches(rnd)
    p = img.load()
    cercler(p)
    # bande de fer horizontale sous la piece : la ferrure de la serrure
    for x in range(2, 14):
        p[x, 11] = FER
        p[x, 12] = FER_OMBRE
    # la piece d'or : un disque de rayon 3,5 centre en (7,5 ; 6), contour fonce, reflet en haut a gauche
    for y in range(1, 11):
        for x in range(4, 12):
            dx, dy = x - 7.5, y - 5.5
            d = dx * dx + dy * dy
            if d <= 12.5:
                p[x, y] = OR[1]
            if 9.0 < d <= 12.5:
                p[x, y] = OR[3]
    for (x, y) in ((6, 3), (5, 4), (6, 4), (7, 3)):
        p[x, y] = OR[2]
    for (x, y) in ((9, 7), (8, 8), (9, 8)):
        p[x, y] = OR[0]
    # le motif frappe : une colonne (celle de la ville)
    for y in range(4, 8):
        p[7, y] = OR[0]
        p[8, y] = OR[0]
    p[6, 4] = p[9, 4] = OR[0]
    p[6, 7] = p[9, 7] = OR[0]
    # la serrure : plaque de laiton sur la ferrure, trou sombre
    for y in range(10, 14):
        for x in range(6, 10):
            p[x, y] = LAITON[1]
    for (x, y) in ((9, 10), (6, 13), (9, 13)):
        p[x, y] = LAITON[0]
    p[6, 10] = LAITON[2]
    p[7, 11] = p[8, 11] = LAITON[3]
    p[7, 12] = LAITON[3]
    for (x, y) in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(p, x, y)
    return img

BOIS = [(150, 112, 66), (158, 119, 71), (142, 105, 61), (165, 126, 77)]
BOIS_JOINT = (96, 68, 38, 255)
PARCHEMIN = [(224, 208, 168), (230, 214, 174), (218, 201, 160), (234, 220, 182)]


def tableau_bois():
    """Bois clair a veines horizontales : les pieds, les tranches et le dos du panneau."""
    rnd = random.Random(741)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b = rnd.choice(BOIS)
            p[x, y] = (r, g, b, 255)
    for y in (3, 7, 11, 15):
        for x in range(16):
            p[x, y] = BOIS_JOINT
    for (x, y) in ((4, 1), (5, 1), (10, 5), (11, 5), (2, 9), (3, 9), (12, 13), (13, 13)):
        p[x, y] = (128, 92, 52, 255)
    return img


def tableau_face():
    """Face du panneau : seules les 10 premieres lignes servent (le modele y prend l'uv 0 0 16 10). Cadre de bois,
    parchemin au centre ; le texte est pose par le rendu."""
    rnd = random.Random(742)
    img = tableau_bois()
    p = img.load()
    for y in range(10):
        for x in range(16):
            r, g, b = rnd.choice(PARCHEMIN)
            p[x, y] = (r, g, b, 255)
    for i in range(16):  # cadre de bois de 1 px, ombre en bas et a droite, arete claire en haut et a gauche
        p[i, 0] = BOIS_JOINT
        p[i, 9] = BOIS_JOINT
    for i in range(10):
        p[0, i] = BOIS_JOINT
        p[15, i] = BOIS_JOINT
    for i in range(1, 15):
        p[i, 1] = (192, 172, 128, 255)
        p[i, 8] = (190, 170, 126, 255)
    for i in range(1, 9):
        p[1, i] = (192, 172, 128, 255)
        p[14, i] = (190, 170, 126, 255)
    # des clous de laiton aux quatre coins du cadre : hors de la zone de texte (x 1 a 14, y 1 a 8)
    for (x, y) in ((0, 0), (15, 0), (0, 9), (15, 9)):
        p[x, y] = LAITON[1]
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for nom, fonction in (("treasury_front", tresor_face), ("treasury_side", tresor_cote), ("treasury_top", tresor_dessus),
                          ("town_board_wood", tableau_bois), ("town_board_front", tableau_face)):
        fonction().save(OUT / (nom + ".png"))
        print("ecrit", OUT / (nom + ".png"))


if __name__ == "__main__":
    main()
