#!/usr/bin/env python3
"""Textures du lot T1 (telephone) : le cadre de l'ecran du telephone (tache 9), l'objet telephone et la prise murale
(tache 10), dessines ici pixel par pixel, reproductibles.

    python tools/gen_textures_t1.py

phone_frame.png : 124 x 256, un bord de 6 px autour d'un ecran de 112 x 244 (rapport de la page, 390 : 844).
Le verre est transparent : l'ecran web peint du noir dessous avant la page, l'ecran de repli y peint son fond, puis le
cadre se pose par-dessus et arrondit les coins du fond.

item/phone.png : 16 x 16, un telephone noir a bords arrondis, ecran bleu nuit, ile dynamique en haut.

block/wall_socket.png et block/wall_socket_lit.png : 16 x 16, decoupees en zones lues par les modeles
(models/block/wall_socket*.json) :
  - x 4..11, y 3..12 : face avant de la plaque (8 x 10), un voyant en haut, une plaque claire de 6 x 6, sa broche de
    terre et ses deux trous ;
  - x 12..13, y 3..12 : chant de la plaque (cotes) ; x 4..11, y 0..1 : chant du haut et du bas ;
  - x 0..3, y 0..6 : face du telephone pose (4 x 7) ; x 0, y 8..14 : son flanc ; x 0..3, y 15 : son dessus.
La version allumee (_lit) a le voyant vert et l'eclair de charge sur l'ecran du telephone pose.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"

FRAME_W, FRAME_H, BORDER = 124, 256, 6


def phone_frame():
    """Cadre graphite arrondi, liseré clair, verre transparent au milieu ; boutons du volume et de veille sur les flancs."""
    scale = 4  # dessin agrandi puis réduit : bords adoucis
    w, h, b = FRAME_W * scale, FRAME_H * scale, BORDER * scale
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    radius = 20 * scale
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=(92, 94, 102, 255))  # liseré
    d.rounded_rectangle((scale, scale, w - 1 - scale, h - 1 - scale), radius=radius - scale,
                        fill=(36, 37, 41, 255))  # graphite
    d.rounded_rectangle((2 * scale, 2 * scale, w - 1 - 2 * scale, h - 1 - 2 * scale), radius=radius - 2 * scale,
                        fill=(24, 24, 27, 255))
    # Le verre : transparent (les écrans peignent dessous). Ses coins arrondis gardent le bord graphite.
    d.rounded_rectangle((b, b, w - 1 - b, h - 1 - b), radius=radius - b + 2 * scale, fill=(0, 0, 0, 0))
    img = img.resize((FRAME_W, FRAME_H), Image.LANCZOS)
    p = img.load()
    # Le verre réduit garde des pixels à demi transparents sur son bord : on les rend opaques (graphite) ou vides, pour
    # que le cadre se dessine net, avec ou sans mélange.
    for y in range(FRAME_H):
        for x in range(FRAME_W):
            r, g, bl, a = p[x, y]
            inside = BORDER <= x < FRAME_W - BORDER and BORDER <= y < FRAME_H - BORDER
            if inside:
                p[x, y] = (24, 24, 27, 255) if a >= 128 else (0, 0, 0, 0)
    for (x0, y0, y1) in ((0, 44, 58), (0, 64, 78), (FRAME_W - 1, 56, 80)):  # boutons sur le liseré
        for y in range(y0, y1):
            p[x0, y] = (120, 122, 130, 255)
    return img


# --- Le téléphone (objet) -------------------------------------------------------------------------------------------

CONTOUR = (14, 14, 18, 255)
COQUE = (40, 41, 47, 255)
COQUE_CLAIRE = (78, 80, 90, 255)
COQUE_SOMBRE = (26, 26, 31, 255)
ECRAN_HAUT = (26, 40, 92)
ECRAN_BAS = (12, 18, 50)
REFLET = (86, 118, 190, 255)
ILE = (6, 6, 8, 255)


def phone_item():
    """Un téléphone de 8 x 13 px (lignes 0 à 12 : la barre de charge du jeu, lignes 13 et 14, reste sous lui), coins
    arrondis, écran bleu nuit en dégradé avec un reflet, île dynamique, bouton."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    x0, x1, y0, y1 = 4, 11, 0, 12
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            coin = (x in (x0, x1)) and (y in (y0, y1))
            if coin:
                continue  # coins arrondis
            bord = x in (x0, x1) or y in (y0, y1)
            p[x, y] = CONTOUR if bord else COQUE
    # Liseré : clair à gauche et en haut, sombre à droite et en bas.
    for y in range(y0 + 1, y1):
        p[x0 + 1, y] = COQUE_CLAIRE
        p[x1 - 1, y] = COQUE_SOMBRE
    for x in range(x0 + 2, x1 - 1):
        p[x, y0 + 1] = COQUE_CLAIRE
        p[x, y1 - 1] = COQUE_SOMBRE
    # Écran : x 6..9, y 2..10, dégradé du haut vers le bas.
    for y in range(2, 11):
        t = (y - 2) / 8
        c = tuple(round(ECRAN_HAUT[i] + (ECRAN_BAS[i] - ECRAN_HAUT[i]) * t) for i in range(3)) + (255,)
        for x in range(6, 10):
            p[x, y] = c
    p[7, 2] = p[8, 2] = ILE  # île dynamique
    for (x, y) in ((6, 4), (6, 5), (7, 4)):  # reflet en biais
        p[x, y] = REFLET
    p[8, 9] = p[7, 9] = (70, 80, 118, 255)  # barre d'accueil
    p[x1 + 1, 3] = p[x1 + 1, 4] = (96, 98, 108, 255)  # bouton de veille sur le flanc
    return img


# --- La prise murale ------------------------------------------------------------------------------------------------

CREME = (226, 220, 204, 255)
CREME_CLAIRE = (244, 240, 230, 255)
CREME_OMBRE = (186, 178, 160, 255)
CREME_CHANT = (200, 192, 174, 255)
CONTOUR_PRISE = (120, 112, 98, 255)
PUITS = (208, 200, 182, 255)
CERCLE = (158, 148, 130, 255)
TROU = (34, 30, 28, 255)
VOYANT_ETEINT = (62, 74, 62, 255)
VOYANT_ALLUME = (96, 240, 104, 255)


def wall_socket(lit):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    # Face avant de la plaque : x 4..11, y 3..12.
    fx0, fx1, fy0, fy1 = 4, 11, 3, 12
    for y in range(fy0, fy1 + 1):
        for x in range(fx0, fx1 + 1):
            if x in (fx0, fx1) or y in (fy0, fy1):
                p[x, y] = CONTOUR_PRISE
            elif x == fx0 + 1 or y == fy0 + 1:
                p[x, y] = CREME_CLAIRE
            elif x == fx1 - 1 or y == fy1 - 1:
                p[x, y] = CREME_OMBRE
            else:
                p[x, y] = CREME
    # Le voyant, en haut de la plaque (ligne 4) : le téléphone posé (lignes 5 à 11, colonnes 6 à 9) ne le cache pas.
    p[7, 4] = p[8, 4] = VOYANT_ALLUME if lit else VOYANT_ETEINT
    # La plaque claire de 6 x 6, un puits rond (x 5..10, y 5..10) cerclé d'ombre, coins coupés.
    for y in range(5, 11):
        for x in range(5, 11):
            if (x in (5, 10)) and (y in (5, 10)):
                continue
            bord = x in (5, 10) or y in (5, 10)
            p[x, y] = CERCLE if bord else PUITS
    # La broche de terre en haut du puits, les deux trous côte à côte au milieu.
    p[7, 6] = p[8, 6] = (150, 150, 152, 255)
    for (x, y) in ((6, 8), (9, 8)):
        p[x, y] = TROU
        p[x, y + 1] = CREME_OMBRE
    # Chants : côtés (x 12..13, y 3..12), haut et bas (x 4..11, y 0..1).
    for y in range(3, 13):
        p[12, y] = CREME_CHANT
        p[13, y] = CREME_OMBRE
    for x in range(4, 12):
        p[x, 0] = CREME_CHANT
        p[x, 1] = CREME_OMBRE
    # Le téléphone posé : face 4 x 7 (x 0..3, y 0..6).
    for y in range(0, 7):
        for x in range(0, 4):
            p[x, y] = CONTOUR
    for y in range(1, 6):
        t = (y - 1) / 4
        c = tuple(round(ECRAN_HAUT[i] + (ECRAN_BAS[i] - ECRAN_HAUT[i]) * t) for i in range(3)) + (255,)
        p[1, y] = p[2, y] = c
    if lit:  # éclair de charge vert
        p[2, 2] = VOYANT_ALLUME
        p[1, 3] = p[2, 3] = VOYANT_ALLUME
        p[1, 4] = VOYANT_ALLUME
    else:
        p[1, 2] = REFLET
    # Son flanc (x 0, y 8..14) et son dessus (x 0..3, y 15).
    for y in range(8, 15):
        p[0, y] = COQUE
    for x in range(0, 4):
        p[x, 15] = COQUE
    return img


def main():
    gui = ASSETS / "gui"
    item = ASSETS / "item"
    block = ASSETS / "block"
    for d in (gui, item, block):
        d.mkdir(parents=True, exist_ok=True)
    sorties = {
        gui / "phone_frame.png": phone_frame(),
        item / "phone.png": phone_item(),
        block / "wall_socket.png": wall_socket(False),
        block / "wall_socket_lit.png": wall_socket(True),
    }
    for chemin, image in sorties.items():
        image.save(chemin)
        print("ecrit :", chemin)


if __name__ == "__main__":
    main()
