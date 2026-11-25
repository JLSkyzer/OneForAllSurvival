#!/usr/bin/env python3
"""Textures du lot T2 (reseau du telephone) : l'antenne (bloc et objet), la carte SIM et l'outil de maintenance,
dessinees ici pixel par pixel, reproductibles.

    python tools/gen_textures_t2.py

block/antenna.png et block/antenna_lit.png : 16 x 16, decoupees en zones lues par les modeles
(models/block/antenna*.json, rendu « cutout » : le treillis du mat est ajoure) :
  - x 0..3, y 0..9 : une face du mat en treillis (4 x 10), montants aux bords, traverses et croisillons, jours
    transparents ;
  - x 4..7, y 0..3 : une face de la tete (4 x 4), boitier sombre et voyant (2 x 2) : vert allume en service (_lit),
    eteint sinon ;
  - x 8..11, y 0..3 : le dessus de la tete ;
  - x 12, y 0..9 : le conduit de cables au coeur du mat (vu a travers le treillis) ;
  - x 4, y 4..12 : un hauban (cable d'acier) ;
  - x 6..15, y 4..5 : le chant de la semelle (10 x 2) ; x 6..15, y 6..15 : son dessus (10 x 10), boulons aux coins et
    anneaux d'ancrage des haubans au milieu des bords.

item/antenna.png : 16 x 16, la tour en treillis sur sa semelle, ses haubans, la tete au voyant vert.
item/sim_card.png : 16 x 16, une carte blanche au coin coupe, puce doree a contacts.
item/maintenance_tool.png : 16 x 16, une cle plate et un tournevis croises, manche de cuivre (distinct du testeur,
boitier brun, et du soudeur, laiton).
"""
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"

VIDE = (0, 0, 0, 0)

# Acier du mat (gris bleute, comme les plaques des machines du mod).
ACIER_CLAIR = (184, 192, 204, 255)
ACIER = (140, 148, 160, 255)
ACIER_SOMBRE = (92, 98, 110, 255)
ACIER_NOIR = (52, 56, 64, 255)
CABLE = (64, 68, 76, 255)
CABLE_CLAIR = (104, 110, 120, 255)
CONDUIT = (40, 42, 48, 255)
CONDUIT_REFLET = (70, 74, 84, 255)

# La tete : boitier sombre.
BOITIER = (58, 62, 72, 255)
BOITIER_CLAIR = (86, 92, 104, 255)
BOITIER_BORD = (30, 32, 38, 255)
VOYANT_ETEINT = (52, 66, 54, 255)
VOYANT_ETEINT_REFLET = (76, 92, 78, 255)
VOYANT_ALLUME = (96, 240, 104, 255)
VOYANT_ALLUME_COEUR = (206, 255, 200, 255)

# La semelle : beton.
BETON = (150, 148, 142, 255)
BETON_CLAIR = (176, 174, 168, 255)
BETON_SOMBRE = (118, 116, 110, 255)
BETON_BORD = (92, 90, 86, 255)
BOULON = (70, 72, 78, 255)


def treillis(p, x0, y0, w, h):
    """Une face de mat en treillis : montants a gauche et a droite, traverse toutes les trois lignes, un croisillon en
    zigzag entre deux traverses, jours transparents."""
    for y in range(h):
        for x in range(w):
            p[x0 + x, y0 + y] = VIDE
        p[x0, y0 + y] = ACIER_CLAIR
        p[x0 + w - 1, y0 + y] = ACIER_SOMBRE
        segment, k = divmod(y, 3)
        if k == 0:
            for x in range(1, w - 1):
                p[x0 + x, y0 + y] = ACIER
        else:
            # Zigzag : un pixel qui va de gauche a droite, puis de droite a gauche au segment suivant.
            x = k if segment % 2 == 0 else w - 1 - k
            if 0 < x < w - 1:
                p[x0 + x, y0 + y] = ACIER_SOMBRE if k == 2 else ACIER


def antenna_block(lit):
    img = Image.new("RGBA", (16, 16), VIDE)
    p = img.load()
    # Mat en treillis : x 0..3, y 0..9.
    treillis(p, 0, 0, 4, 10)
    # Tete, une face : x 4..7, y 0..3.
    for y in range(4):
        for x in range(4, 8):
            bord = x in (4, 7) or y in (0, 3)
            p[x, y] = BOITIER_BORD if bord else BOITIER
    p[5, 0] = p[6, 0] = BOITIER_CLAIR  # chanfrein clair en haut
    if lit:
        p[5, 1] = VOYANT_ALLUME_COEUR
        p[6, 1] = p[5, 2] = p[6, 2] = VOYANT_ALLUME
    else:
        p[5, 1] = VOYANT_ETEINT_REFLET
        p[6, 1] = p[5, 2] = p[6, 2] = VOYANT_ETEINT
    # Dessus de la tete : x 8..11, y 0..3, une embase de fouet au centre.
    for y in range(4):
        for x in range(8, 12):
            bord = x in (8, 11) or y in (0, 3)
            p[x, y] = BOITIER_BORD if bord else BOITIER_CLAIR
    p[9, 1] = p[10, 2] = ACIER_NOIR
    p[10, 1] = p[9, 2] = ACIER_CLAIR
    # Conduit au coeur du mat : x 12, y 0..9.
    for y in range(10):
        p[12, y] = CONDUIT_REFLET if y % 4 == 1 else CONDUIT
    # Hauban : x 4, y 4..12.
    for y in range(4, 13):
        p[4, y] = CABLE_CLAIR if y % 3 == 0 else CABLE
    # Chant de la semelle : x 6..15, y 4..5.
    for x in range(6, 16):
        p[x, 4] = BETON_CLAIR
        p[x, 5] = BETON_BORD if x in (6, 15) else BETON_SOMBRE
    p[6, 4] = p[15, 4] = BETON
    # Dessus de la semelle : x 6..15, y 6..15.
    for y in range(6, 16):
        for x in range(6, 16):
            if x in (6, 15) or y in (6, 15):
                p[x, y] = BETON_BORD
            elif x == 7 or y == 7:
                p[x, y] = BETON_CLAIR
            elif x == 14 or y == 14:
                p[x, y] = BETON_SOMBRE
            else:
                # Grain du beton, fixe (pas de hasard : le script reste reproductible).
                p[x, y] = BETON_SOMBRE if (x * 7 + y * 13) % 17 == 0 else BETON
    for (x, y) in ((8, 8), (13, 8), (8, 13), (13, 13)):  # boulons aux coins
        p[x, y] = BOULON
    # Anneaux d'ancrage des haubans, au milieu de chaque bord (la ou les cables touchent la semelle).
    for (x, y) in ((10, 7), (11, 7), (10, 14), (11, 14), (7, 10), (7, 11), (14, 10), (14, 11)):
        p[x, y] = ACIER_NOIR
    return img


# --- L'antenne (objet) ----------------------------------------------------------------------------------------------

CONTOUR = (30, 32, 38, 255)


def contour(img, couleur=CONTOUR):
    """Un contour sombre d'un pixel autour de tout ce qui est dessine (style des objets du mod)."""
    p = img.load()
    w, h = img.size
    plein = {(x, y) for y in range(h) for x in range(w) if p[x, y][3] > 0}
    for (x, y) in list(plein):
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in plein:
                p[nx, ny] = couleur
    return img


def antenna_item():
    img = Image.new("RGBA", (16, 16), VIDE)
    p = img.load()
    # Semelle : x 3..12, y 13..14.
    for x in range(3, 13):
        p[x, 13] = BETON_CLAIR
        p[x, 14] = BETON_SOMBRE
    # Haubans : de la tete du mat aux bords de la semelle.
    for i in range(7):
        p[5 - i // 3, 6 + i] = CABLE_CLAIR
        p[10 + i // 3, 6 + i] = CABLE_CLAIR
    # Mat en treillis : x 6..9, y 5..12.
    for y in range(5, 13):
        p[6, y] = ACIER_CLAIR
        p[9, y] = ACIER_SOMBRE
        k = (y - 5) % 3
        if k == 0:
            p[7, y] = p[8, y] = ACIER
        else:
            x = 6 + k if ((y - 5) // 3) % 2 == 0 else 9 - k
            p[x, y] = ACIER
    # Tete : x 6..9, y 2..4, voyant vert.
    for y in range(2, 5):
        for x in range(6, 10):
            p[x, y] = BOITIER
    p[6, 2] = p[7, 2] = BOITIER_CLAIR
    p[7, 3] = VOYANT_ALLUME_COEUR
    p[8, 3] = VOYANT_ALLUME
    # Fouet : x 8, y 0..1.
    p[8, 0] = ACIER_CLAIR
    p[8, 1] = ACIER
    return contour(img)


# --- La carte SIM ---------------------------------------------------------------------------------------------------

CARTE = (236, 236, 230, 255)
CARTE_CLAIRE = (252, 252, 248, 255)
CARTE_OMBRE = (196, 196, 190, 255)
CARTE_BORD = (78, 80, 88, 255)
BANDE = (52, 132, 196, 255)
BANDE_SOMBRE = (34, 96, 150, 255)
OR = (232, 192, 84, 255)
OR_CLAIR = (252, 228, 140, 255)
OR_SOMBRE = (176, 132, 42, 255)


def sim_card():
    """Carte de 10 x 13 (x 3..12, y 1..13), coin haut droit coupe en biais, bande bleue de l'operateur en haut, puce
    doree de 6 x 5 a contacts."""
    img = Image.new("RGBA", (16, 16), VIDE)
    p = img.load()
    x0, x1, y0, y1 = 3, 12, 1, 13
    coupe = 4  # le coin coupe
    def dedans(x, y):
        if not (x0 <= x <= x1 and y0 <= y <= y1):
            return False
        return x - (x1 - coupe) <= y - y0  # le coin haut droit coupe en biais
    for y in range(16):
        for x in range(16):
            if dedans(x, y):
                p[x, y] = CARTE
    for y in range(16):
        for x in range(16):
            if p[x, y][3] and any(not dedans(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                p[x, y] = CARTE_BORD
    # Bande bleue de l'operateur (y 3), lisere clair a gauche, ombre a droite et en bas.
    for x in range(x0 + 1, x1 - 3):
        p[x, 3] = BANDE
        p[x, 4] = BANDE_SOMBRE
    for y in range(y0 + 1, y1):
        if p[x0 + 1, y] == CARTE:
            p[x0 + 1, y] = CARTE_CLAIRE
        if p[x1 - 1, y] == CARTE:
            p[x1 - 1, y] = CARTE_OMBRE
    for x in range(x0 + 1, x1):
        if p[x, y1 - 1] == CARTE:
            p[x, y1 - 1] = CARTE_OMBRE
    # Puce : x 5..10, y 6..10.
    for y in range(6, 11):
        for x in range(5, 11):
            bord = x in (5, 10) or y in (6, 10)
            p[x, y] = OR_SOMBRE if bord else OR
    for x in range(6, 10):  # contacts : une ligne au milieu, une separation au centre
        p[x, 8] = OR_SOMBRE
    p[7, 7] = p[7, 9] = OR_SOMBRE
    p[6, 7] = p[8, 7] = OR_CLAIR
    return img


# --- L'outil de maintenance -----------------------------------------------------------------------------------------

CUIVRE = (196, 112, 62, 255)
CUIVRE_CLAIR = (236, 162, 104, 255)
CUIVRE_SOMBRE = (138, 70, 36, 255)


def maintenance_tool():
    """Une cle plate (bas-gauche vers haut-droit, machoire ouverte en haut) et un tournevis (pointe en haut a gauche,
    manche de cuivre en bas a droite), croises."""
    img = Image.new("RGBA", (16, 16), VIDE)
    p = img.load()
    # Cle : manche diagonal de (2, 13) a (9, 6), deux pixels d'epaisseur.
    for i in range(8):
        x, y = 2 + i, 13 - i
        p[x, y] = ACIER_CLAIR
        p[x + 1, y] = ACIER
        if i < 7:
            p[x + 1, y + 1] = ACIER_SOMBRE if i > 0 else ACIER_SOMBRE
    p[2, 14] = ACIER_SOMBRE
    # Machoire : un « C » ouvert vers le haut a droite (deux becs, gorge vide).
    for (x, y) in ((10, 2), (10, 3), (10, 4), (11, 5), (12, 5)):
        p[x, y] = ACIER
    for (x, y) in ((11, 1), (12, 1), (11, 4), (13, 5), (14, 4)):
        p[x, y] = ACIER_CLAIR
    for (x, y) in ((11, 2), (11, 3), (12, 2), (12, 3), (12, 4), (13, 2), (13, 3), (13, 4), (13, 1), (14, 2), (14, 3)):
        p[x, y] = VIDE
    # Tournevis : tige d'acier de (3, 3) a (9, 9), pointe plate en (2, 2).
    for i in range(7):
        p[3 + i, 3 + i] = ACIER_CLAIR if i % 2 == 0 else ACIER
    p[2, 2] = ACIER_NOIR
    p[1, 1] = ACIER_SOMBRE
    # Manche de cuivre : de (10, 10) a (13, 13), deux pixels d'epaisseur, virole d'acier.
    p[10, 10] = ACIER_SOMBRE
    for i in range(4):
        x, y = 11 + i, 11 + i
        p[x, y] = CUIVRE
        p[x - 1, y] = CUIVRE_CLAIR
        p[x, y - 1] = CUIVRE_SOMBRE
    p[14, 14] = CUIVRE_SOMBRE
    p[11, 11] = CUIVRE_CLAIR
    return contour(img)


def main():
    item = ASSETS / "item"
    block = ASSETS / "block"
    for d in (item, block):
        d.mkdir(parents=True, exist_ok=True)
    sorties = {
        block / "antenna.png": antenna_block(False),
        block / "antenna_lit.png": antenna_block(True),
        item / "antenna.png": antenna_item(),
        item / "sim_card.png": sim_card(),
        item / "maintenance_tool.png": maintenance_tool(),
    }
    for chemin, image in sorties.items():
        image.save(chemin)
        print("ecrit :", chemin)


if __name__ == "__main__":
    main()
