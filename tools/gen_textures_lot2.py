#!/usr/bin/env python3
"""Génère les textures et les modèles du lot 2 (métier de réparation).

Tout est dessiné ici, sans aucune ressource téléchargée, et reproductible : chaque texture a sa graine fixe.
Style : bricolage de survie, récupéré sur l'épave. Métal rouillé, laiton terni, toile usée, comme l'établi.

    python tools/gen_textures_lot2.py                  écrit les PNG, les modèles et l'état du bain
    python tools/gen_textures_lot2.py --planche X.png  écrit aussi une planche de contrôle agrandie
"""
import argparse
import colorsys
import json
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival"
MOD = "oneforallsurvival"
TAILLE = 16

# ---------------------------------------------------------------------------------------------------
# Palette commune : un caractère, une couleur. La même rouille et le même laiton partout.
# ---------------------------------------------------------------------------------------------------
PALETTE = {
    ".": None,
    "K": (34, 29, 27),  # contour
    "d": (66, 68, 72),  # fer sombre
    "i": (104, 106, 110),  # fer
    "l": (150, 152, 154),  # fer clair
    "w": (206, 206, 202),  # reflet
    "r": (92, 50, 28),  # rouille sombre
    "R": (146, 80, 38),  # rouille
    "o": (186, 112, 50),  # rouille vive
    "b": (122, 86, 32),  # laiton sombre
    "B": (184, 140, 54),  # laiton
    "y": (232, 196, 100),  # laiton clair
    "c": (112, 58, 32),  # cuivre sombre
    "C": (178, 100, 56),  # cuivre
    "e": (230, 156, 100),  # cuivre clair
    "g": (86, 150, 118),  # vert-de-gris
    "t": (88, 78, 56),  # toile sombre
    "T": (138, 124, 90),  # toile
    "u": (178, 164, 124),  # toile claire
    "n": (66, 44, 28),  # bois sombre
    "N": (112, 78, 46),  # bois
    "M": (146, 106, 66),  # bois clair
    "a": (196, 224, 228),  # verre clair
    "A": (128, 168, 178),  # verre
    "x": (42, 40, 40),  # gaine noire
    "X": (168, 42, 34),  # rouge
    "P": (222, 72, 56),  # rouge clair
    "z": (88, 100, 112),  # zinc sombre
    "Z": (132, 146, 160),  # zinc
    "q": (190, 204, 216),  # zinc clair
    "f": (228, 222, 198),  # cadran
    "h": (255, 186, 66),  # chaud
    "H": (255, 242, 190),  # chauffé à blanc
    "v": (216, 122, 38),  # gaine orange
    "V": (146, 78, 24),  # gaine orange sombre
    "s": (222, 216, 200),  # filet blanc
    "G": (96, 192, 92),  # vert vif
    "E": (120, 14, 10),  # pâte rouge sombre
    "F": (216, 40, 24),  # pâte rouge
}


def sprite(*lignes):
    """Une image de 16 x 16 à partir de seize lignes de seize caractères."""
    assert len(lignes) == TAILLE, f"{len(lignes)} lignes"
    image = Image.new("RGBA", (TAILLE, TAILLE), (0, 0, 0, 0))
    for y, ligne in enumerate(lignes):
        assert len(ligne) == TAILLE, f"ligne {y} : {len(ligne)} caractères : {ligne!r}"
        for x, c in enumerate(ligne):
            couleur = PALETTE[c]
            if couleur is not None:
                image.putpixel((x, y), couleur + (255,))
    return image


# ---------------------------------------------------------------------------------------------------
# Objets
# ---------------------------------------------------------------------------------------------------
FUSE = sprite(
    "................",
    "................",
    "................",
    "................",
    ".KKKKKKKKKKKKKK.",
    "KyyBBKaqqqaKBByK",
    "KyBBbKaAAAAKBBbK",
    "KBBRbKeeeeeKBRbK",
    "KBBBbKAAAAAKBBbK",
    "KBbbbKAAdAAKbbbK",
    "KKKKKKKKKKKKKKKK",
    ".KKKK......KKKK.",
    "................",
    "................",
    "................",
    "................",
)

COIL = sprite(
    "................",
    "..KKKKKKKKKKKK..",
    ".KlwlliiiiiidK..",
    ".KKKKKKKKKKKKKK.",
    "..KcCeeeCCCcK...",
    "..KccCCCCccrK...",
    "..KcCeeeCCCcK...",
    "..KccCCCCccrK...",
    "..KcCeeeCCCcKKK.",
    "..KccCCCCccrKeK.",
    "..KcCeeeCCCcCCK.",
    "..KccCCCCccrKKK.",
    ".KKKKKKKKKKKKK..",
    ".KlliiRiiiddrK..",
    "..KKKKKKKKKKK...",
    "................",
)

BEARING = sprite(
    "................",
    ".....KKKKKK.....",
    "...KKwlllliKK...",
    "..KwlKKKKKKiiK..",
    "..KlKqKddKqKiK..",
    ".KlKqdKKKKdqKiK.",
    ".KlKdKKllKKdKiK.",
    ".KlKKKl..lKKKRK.",
    ".KlKdKl..iKdKRK.",
    ".KiKqKKiiKKqKdK.",
    ".KiKddKKKKddKdK.",
    "..KiKqKddKqKdK..",
    "..KiiKKKKKKdRK..",
    "...KKiiiRddKK...",
    ".....KKKKKK.....",
    "................",
)

CLEANING_KIT = sprite(
    "................",
    "..KKKKK.........",
    ".KsTsTsK.KKKK...",
    ".KsTsTsKKooRoK..",
    ".KsTsTsKKRoooK..",
    "..KBBBK.KoRRoK..",
    "..KMNNK..KoRK...",
    ".KKKKKKKKKKKKKK.",
    "KTuuTTTTTTTuTTtK",
    "KuTTTTKKKKTTTTtK",
    "KTTTTTKyBKTTTttK",
    "KKKKKKKBbKKKKKKK",
    "KTTuTTKKKKTTTttK",
    "KTTTTtTTTTtTTttK",
    ".KKKKKKKKKKKKKK.",
    "................",
)

TESTER = sprite(
    "................",
    ".KKKKKKKKKKKKKK.",
    ".KMNNNNNNNNNNnK.",
    ".KNKKKKKKKKKKnK.",
    ".KNKffffffXfKnK.",
    ".KNKfKfffXffKnK.",
    ".KNKfffKXfKfKnK.",
    ".KNKKKKKKKKKKnK.",
    ".KNNNNNNNNNNNnK.",
    ".KNKKKNNNuuTNnK.",
    ".KNKlKNNNuTtNnK.",
    ".KnKKKnnnnnnnnK.",
    "..KKKKKKKKKKKK..",
    "..KXK......KxK..",
    ".KXPK.......xxK.",
    ".KlK.........lK.",
)

WELDER = sprite(
    "............H...",
    "..........HhHh..",
    "...........HhH..",
    "..........Kl....",
    ".........KlK....",
    "........KdiK....",
    ".......KxdK.....",
    "......KxxK......",
    ".....KxxK.......",
    "..KKKKKKKKKKKK..",
    ".KyyBBBBBBBBbbK.",
    ".KBKKKKBBRRBBbK.",
    ".KBKlHKBBBBRBbK.",
    ".KBKKKKBKKBKKbK.",
    ".KbbbRbbbbbbbbK.",
    "..KKKKKKKKKKKK..",
)

SCRAP_METAL = sprite(
    "................",
    ".......KKK......",
    "....KKKlwlKK....",
    "..KKllwwlliiKK..",
    ".KllwwliiKiiiiK.",
    ".KlwKliiiiiiRRK.",
    "..KlliiiKiiRroK.",
    "...KKiiiiiirRK..",
    "..KdKKddKKiiKK..",
    ".KddddddddKKrK..",
    ".KdKddRdddddrK..",
    "..KddRrRddKddK..",
    "...KKdRddddKK...",
    ".....KKKKKK.....",
    "................",
    "................",
)

AVIATION_WIRE = sprite(
    "................",
    ".....KKKKKK.....",
    "...KKvvsvvvKK...",
    "..KvvVKKKKVvvK..",
    "..KvVK....KVvK..",
    ".KsvK......KvvK.",
    ".KvvK......KvsK.",
    "KKTTKK......KvK.",
    "KuTTtK......KvK.",
    "KKTTKK.....KvvK.",
    ".KvvK......KvsK.",
    ".KsvVK....KVvK..",
    "..KvvVKKKKVvvK..",
    "...KKvvsvvvKKK..",
    ".....KKKKKKKeeK.",
    "..............K.",
)

INSTRUMENT_CORE = sprite(
    "......KKKK......",
    "...KKKyBBbKKK...",
    "..KyBBKKKKbbbK..",
    "..KBKKffffKKbK..",
    ".KBKfffffXffKbK.",
    "KyBKfKfffXffKbbK",
    "KBKffffKXfffKKbK",
    "KBKffffXKfffKbbK",
    "KBKfKfXffffKfKbK",
    "KbBKfffffffffKbK",
    ".KBKffffKfffKbK.",
    "..KbKKffffKKbbK.",
    "..KbbbKKKKbbbK..",
    "...KKKbbbbKKKK..",
    "......KKKK.KhK..",
    "............K...",
)

INCOMPLETE_INSTRUMENT_CORE = sprite(
    "......KKKK......",
    "...KKKyBBbKKK...",
    "..KyBBKKKKbbbK..",
    "..KBKKddddKKbK..",
    ".KBKddKKddddKbK.",
    "KyBKdKllKddddKbK",
    "KBKddKliKdKKdKbK",
    "KBKddKKKdKBBKKbK",
    "KBKdddddKBybKKbK",
    "KbBKddddKbBbKbbK",
    ".KBKvdddKKKKKbK.",
    "..KbKvddddKKbbK.",
    "..KbbKvKKKsbbK..",
    "...KKKvKbbsKKK..",
    "......vK..KsK...",
    "......Ke...Ke...",
)

BATTERY_CELL = sprite(
    "................",
    "...KKK....KKK...",
    "...KPK....KlK...",
    "..KKKKKKKKKKKK..",
    ".KqqZZZZZZZZzzK.",
    ".KKKKKKKKKKKKKK.",
    ".KZqZZZZZZZZzzK.",
    ".KqZZZZZZZZzzzK.",
    ".KCeCCCCCCCcgcK.",
    ".KeCCgCCCCCCccK.",
    ".KZqZZZZZZzzzzK.",
    ".KZZZZZRZZZzzdK.",
    ".KZZZZZZRZzzzdK.",
    ".KzZZZZZZzzzddK.",
    ".KKKKKKKKKKKKKK.",
    "................",
)

INCOMPLETE_BATTERY_CELL = sprite(
    "................",
    "...KK.KK.KK.....",
    "...KeKKCKKeK....",
    "...KCKKiKKCK.K..",
    "...KCKKiKKcKKlK.",
    ".KKKKKKKKKKKKKK.",
    ".KEFFEFEFFEFEEK.",
    ".KKKKKKKKKKKKKK.",
    ".KZqZZZZZZzzzzK.",
    ".KqZZKZZZZZzzzK.",
    ".KZZZZZZZZZKzzK.",
    ".KZZZZZZZzzzzdK.",
    ".KZZKZZZZZzzzdK.",
    ".KzZZZZZZzzzddK.",
    ".KKKKKKKKKKKKKK.",
    "................",
)


def traite(image):
    """La pièce traitée : la même pièce, passée au zinc. Tout vire au bleu froid, avec un reflet en biais."""
    sortie = image.copy()
    for y in range(TAILLE):
        for x in range(TAILLE):
            r, v, b, a = image.getpixel((x, y))
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * v + 0.11 * b) / 255
            if lum < 0.16:  # le contour reste sombre, un peu bleui
                sortie.putpixel((x, y), (26, 34, 48, 255))
                continue
            # Le ton d'origine survit un peu : le cuivre reste chaud sous le zinc, le fer non.
            h, s, l = colorsys.rgb_to_hls(r / 255, v / 255, b / 255)
            bleu = colorsys.hls_to_rgb(0.57, min(0.92, 0.22 + 0.72 * l), 0.42)
            mix = [0.62 * bleu[i] + 0.38 * (r, v, b)[i] / 255 for i in range(3)]
            # Le reflet : une bande diagonale, nette, deux pixels de large.
            bande = (x + y) % 16
            if bande in (6, 7):
                mix = [0.5 * m + 0.5 * c for m, c in zip(mix, (0.80, 0.96, 1.0))]
            elif bande in (5, 8):
                mix = [0.8 * m + 0.2 * c for m, c in zip(mix, (0.70, 0.92, 1.0))]
            sortie.putpixel((x, y), tuple(min(255, round(m * 255)) for m in mix) + (255,))
    return sortie


OBJETS = {
    "fuse": FUSE,
    "coil": COIL,
    "bearing": BEARING,
    "cleaning_kit": CLEANING_KIT,
    "tester": TESTER,
    "welder": WELDER,
    "scrap_metal": SCRAP_METAL,
    "aviation_wire": AVIATION_WIRE,
    "instrument_core": INSTRUMENT_CORE,
    "incomplete_instrument_core": INCOMPLETE_INSTRUMENT_CORE,
    "battery_cell": BATTERY_CELL,
    "incomplete_battery_cell": INCOMPLETE_BATTERY_CELL,
}
for piece in ("fuse", "coil", "bearing", "cleaning_kit"):
    OBJETS["treated_" + piece] = traite(OBJETS[piece])


# ---------------------------------------------------------------------------------------------------
# Outils des faces de blocs
# ---------------------------------------------------------------------------------------------------
def varie(couleur, hasard, ecart):
    delta = hasard.randint(-ecart, ecart)
    return tuple(max(0, min(255, c + delta)) for c in couleur)


def fond(couleur, hasard, ecart=6):
    image = Image.new("RGBA", (TAILLE, TAILLE))
    for y in range(TAILLE):
        for x in range(TAILLE):
            image.putpixel((x, y), varie(couleur, hasard, ecart) + (255,))
    return image


def point(image, x, y, couleur):
    if 0 <= x < TAILLE and 0 <= y < TAILLE:
        image.putpixel((x, y), tuple(couleur) + (255,))


def rivet(image, x, y):
    point(image, x, y, (212, 214, 214))
    point(image, x + 1, y + 1, (52, 52, 54))
    point(image, x + 1, y, (120, 122, 124))
    point(image, x, y + 1, (120, 122, 124))


def coulure(image, x, y, longueur):
    """Une traînée de rouille qui descend sous un rivet."""
    for i in range(longueur):
        fondu = 1 - i / (longueur + 1)
        r, v, b, _ = image.getpixel((x, min(TAILLE - 1, y + i)))
        rouille = (168, 104, 58)
        melange = [round(c * (1 - fondu * 0.55) + d * fondu * 0.55) for c, d in zip((r, v, b), rouille)]
        point(image, x, y + i, melange)


# ---------------------------------------------------------------------------------------------------
# Débris : cinq faces qu'on doit reconnaître de loin
# ---------------------------------------------------------------------------------------------------
def debris_fuselage():
    """Tôle rivetée : aluminium griffé, bande de peinture délavée, deux lignes de rivets, une déchirure."""
    h = random.Random(201)
    img = fond((148, 152, 154), h, 5)
    for y in range(9, 13):  # la bande de peinture de la compagnie, délavée
        for x in range(TAILLE):
            couleur = (56, 84, 122) if h.random() > 0.12 else (150, 154, 156)
            point(img, x, y, varie(couleur, h, 5))
    for x in range(TAILLE):  # les joints des tôles
        point(img, x, 6, (92, 94, 98))
        point(img, x, 7, (196, 198, 200))
    for x in range(1, 16, 5):
        rivet(img, x, 3)
        rivet(img, x + 2, 14)
    coulure(img, 1, 5, 1)
    coulure(img, 11, 8, 2)
    coulure(img, 6, 8, 1)
    # La déchirure : un trou noir, petit et irrégulier, au bord de métal plié.
    for x, y0, y1 in ((10, 11, 12), (11, 10, 13), (12, 11, 14), (13, 12, 13)):
        for y in range(y0, y1 + 1):
            point(img, x, y, (24, 22, 22))
    for x, y in ((9, 11), (10, 10), (11, 9), (12, 10), (13, 11), (14, 12)):
        point(img, x, y, (222, 224, 222))
    for x, y in ((10, 13), (11, 14), (12, 15), (13, 14)):
        point(img, x, y, (150, 82, 38))
    return img


def debris_wiring():
    """Faisceau de câbles : gaines de couleur qui pendent sur fond noir, colliers de toile, âmes de cuivre."""
    h = random.Random(202)
    img = fond((30, 27, 26), h, 3)
    couleurs = [((186, 110, 48), (112, 64, 28)), ((186, 182, 166), (108, 104, 96)),
                ((138, 50, 40), (84, 28, 24)), ((64, 92, 128), (38, 54, 78)),
                ((182, 152, 66), (108, 90, 38)), ((186, 110, 48), (112, 64, 28))]
    for i, (clair, sombre) in enumerate(couleurs):
        base = 1 + i * 2.6
        creux = 1.0 + (i % 3) * 0.6
        phase = i * 1.3
        for x in range(TAILLE):
            # Période 16 : le faisceau se raccorde d'un bloc au suivant.
            y = round(base + creux * math.sin(2 * math.pi * x / TAILLE + phase))
            point(img, x, y % TAILLE, clair)
            point(img, x, (y + 1) % TAILLE, sombre)
    for x in (4, 11):  # les colliers qui serrent le faisceau
        for y in range(TAILLE):
            if h.random() > 0.08:
                point(img, x, y, varie((132, 118, 86), h, 8))
                point(img, x + 1, y, varie((92, 82, 58), h, 6))
    for x, y in ((7, 4), (8, 4), (8, 5), (14, 11), (14, 12), (13, 12)):  # du cuivre à nu, là où la gaine a fondu
        point(img, x, y, (232, 158, 100))
    return img


def cadran(img, cx, cy, rayon, angle, fele=False):
    for y in range(cy - rayon - 1, cy + rayon + 2):
        for x in range(cx - rayon - 1, cx + rayon + 2):
            d = math.hypot(x - cx, y - cy)
            if d <= rayon - 0.5:
                point(img, x, y, (226, 220, 196))
            elif d <= rayon + 0.6:
                point(img, x, y, (146, 108, 44) if (x + y) % 3 else (214, 176, 88))
    for pas in range(5):  # les graduations
        a = math.radians(200 + pas * 35)
        point(img, round(cx + (rayon - 1) * math.cos(a)), round(cy + (rayon - 1) * math.sin(a)), (40, 38, 36))
    for i in range(rayon):  # l'aiguille
        point(img, round(cx + i * math.cos(angle)), round(cy + i * math.sin(angle)), (186, 36, 28))
    point(img, cx, cy, (24, 22, 22))
    if fele:
        for i, (dx, dy) in enumerate(((1, 2), (2, 2), (2, 3), (3, 3))):
            point(img, cx + dx, cy + dy - 1, (92, 96, 98))


def debris_instruments():
    """Cadrans : planche de bord noire, deux cadrans à aiguille, une rangée d'interrupteurs, un voyant rouge."""
    h = random.Random(203)
    img = fond((46, 46, 50), h, 3)
    for x in range(TAILLE):  # le bord de la planche, usé
        point(img, x, 0, (84, 84, 88))
        point(img, x, 15, (24, 24, 26))
    cadran(img, 4, 5, 3, math.radians(-40))
    cadran(img, 11, 7, 4, math.radians(200), fele=True)
    for x in (1, 4, 7):  # les interrupteurs à levier
        point(img, x, 13, (24, 24, 26))
        point(img, x, 12, (200, 200, 196))
        point(img, x + 1, 13, (84, 84, 88))
    point(img, 13, 13, (216, 60, 44))  # un voyant
    point(img, 14, 13, (120, 26, 20))
    point(img, 13, 14, (120, 26, 20))
    point(img, 1, 10, (200, 196, 176))  # une étiquette arrachée
    point(img, 2, 10, (200, 196, 176))
    point(img, 3, 10, (160, 156, 138))
    rivet(img, 14, 1)
    return img


def debris_battery():
    """Éléments de batterie : trois bacs noirs côte à côte, bornes de plomb, croûte de sulfate vert et blanc."""
    h = random.Random(204)
    img = fond((22, 22, 24), h, 2)
    for i, x0 in enumerate((0, 5, 11)):
        largeur = 5 if i == 0 else (6 if i == 1 else 5)
        for x in range(x0, x0 + largeur - 1):
            for y in range(3, 16):
                point(img, x, y, varie((58, 58, 64), h, 4))
            point(img, x, 3, (98, 98, 106))  # le couvercle
            point(img, x, 4, (34, 34, 38))
        for y in range(5, 16):
            point(img, x0, y, (84, 84, 92))  # l'arête éclairée
        milieu = x0 + (largeur - 1) // 2
        for y in range(8, 12):
            point(img, milieu, y, (112, 116, 124))  # le niveau de la jauge
        for bx in (x0 + 1, x0 + largeur - 2 - (1 if largeur > 5 else 0)):
            point(img, bx, 2, (150, 150, 160))  # les bornes de plomb
            point(img, bx, 1, (186, 186, 196))
    # Les barrettes qui relient les éléments.
    for x in range(3, 8):
        point(img, x, 1, (126, 126, 134))
    for x in range(9, 14):
        point(img, x, 1, (126, 126, 134))
    # Le sulfate qui ronge les bornes et coule sur les bacs.
    for x, y in ((1, 3), (2, 3), (1, 2), (6, 3), (7, 4), (8, 3), (12, 3), (13, 3), (13, 4), (13, 5), (2, 5), (7, 5),
                 (12, 2)):
        point(img, x, y, (116, 186, 150) if h.random() < 0.6 else (214, 228, 214))
    return img


def debris_cabin():
    """Sièges déchirés : tissu bleu passé, coutures de matelassage, entaille d'où sort la mousse jaune."""
    h = random.Random(205)
    img = fond((70, 84, 112), h, 5)
    for x in (0, 5, 10, 15):  # le matelassage
        for y in range(TAILLE):
            point(img, x, y, (44, 54, 76))
        for y in range(1, TAILLE, 2):
            point(img, (x + 1) % TAILLE, y, (100, 114, 142))
    for y in (0, 1, 2, 3):  # l'appui-tête, housse de tissu clair
        for x in range(TAILLE):
            if x not in (0, 15):
                point(img, x, y, varie((186, 182, 166), h, 5))
    for x in range(TAILLE):
        point(img, x, 4, (34, 40, 56))
    # L'entaille, en biais et en dents de scie : la mousse jaune déborde, le fer du dossier apparaît au fond.
    entaille = ((2, 7, 7), (3, 7, 8), (4, 8, 9), (5, 8, 10), (6, 9, 10), (7, 10, 12), (8, 11, 12), (9, 12, 13),
                (10, 13, 13))
    for x, y0, y1 in entaille:
        for y in range(y0, y1 + 1):
            point(img, x, y, varie((214, 190, 116), h, 12))
        point(img, x, y0 - 1, (28, 28, 36))
        point(img, x, y1 + 1, (28, 28, 36))
    for x, y in ((5, 9), (7, 11), (6, 10)):
        point(img, x, y, (92, 56, 32))
    for x, y in ((3, 10), (8, 9), (11, 11)):  # des touffes de mousse échappées
        point(img, x, y, (226, 206, 138))
    # Une ceinture arrachée, boucle comprise.
    for y in range(5, 12):
        point(img, 12, y, (146, 134, 104))
        point(img, 13, y, (112, 102, 78))
    point(img, 12, 12, (196, 198, 198))
    point(img, 13, 12, (150, 152, 152))
    point(img, 12, 13, (150, 152, 152))
    return img


DEBRIS = {
    "debris_fuselage": debris_fuselage,
    "debris_wiring": debris_wiring,
    "debris_instruments": debris_instruments,
    "debris_battery": debris_battery,
    "debris_cabin": debris_cabin,
}


# ---------------------------------------------------------------------------------------------------
# Bain galvanique
# ---------------------------------------------------------------------------------------------------
def bain_cuve():
    """La tôle galvanisée du bac : fleurs de zinc, un joint riveté, un rapiéçage, de la rouille en bas."""
    h = random.Random(301)
    img = fond((138, 150, 160), h, 7)
    for _ in range(14):  # les fleurs de zinc
        point(img, h.randrange(16), h.randrange(16), (176, 190, 200))
    for y in range(TAILLE):  # le joint
        point(img, 11, y, (88, 98, 108))
    for y in (2, 7, 12):
        point(img, 12, y, (206, 214, 220))
        point(img, 12, y + 1, (70, 76, 84))
    for x in range(1, 7):  # le rapiéçage : une plaque rouillée, boulonnée
        for y in range(8, 13):
            point(img, x, y, varie((126, 84, 52), h, 8))
    for x, y in ((1, 8), (6, 8), (1, 12), (6, 12)):
        point(img, x, y, (46, 42, 40))
    for x in range(TAILLE):  # le bas du bac, rongé
        if h.random() < 0.7:
            point(img, x, 15, (112, 66, 36))
        if h.random() < 0.3:
            point(img, x, 14, (136, 88, 52))
    return img


def bain_anode():
    """La plaque de zinc qui pend dans le bain : claire, piquée, rongée en bas."""
    h = random.Random(302)
    img = fond((186, 198, 208), h, 6)
    for x in range(TAILLE):
        point(img, x, 0, (226, 234, 240))
    for _ in range(10):
        point(img, h.randrange(16), 6 + h.randrange(10), (112, 124, 136))
    return img


def bain_liquide(graine, couleur, ecart, taches=(), bulles=False, images=1):
    """Le liquide du bac, vu de dessus. En marche il bouillonne : plusieurs images, animées par un .mcmeta."""
    h = random.Random(graine)
    sortie = Image.new("RGBA", (TAILLE, TAILLE * images))
    # Les remous fixes : le même fond à chaque image, seules les bulles bougent.
    base = fond(couleur, h, ecart)
    for c, nombre in taches:
        for _ in range(nombre):
            point(base, h.randrange(16), h.randrange(16), c)
    sources = [(h.randrange(16), h.randrange(16), h.randrange(images)) for _ in range(9)]
    for i in range(images):
        image = base.copy()
        if bulles:
            for x, y, depart in sources:
                age = (i + depart) % images
                if age == 0:
                    point(image, x, y, (178, 240, 234))
                elif age == 1:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        point(image, x + dx, y + dy, (178, 240, 234))
                    point(image, x, y, (54, 150, 146))
                elif age == 2:
                    point(image, x, y, (232, 255, 250))
        sortie.paste(image, (0, i * TAILLE))
    return sortie


BAIN = {
    "galvanic_bath_tub": bain_cuve(),
    "galvanic_bath_anode": bain_anode(),
    "galvanic_bath_liquid_stopped": bain_liquide(303, (92, 136, 136), 4),
    "galvanic_bath_liquid_running": bain_liquide(304, (38, 170, 160), 8, bulles=True, images=4),
    "galvanic_bath_liquid_no_power": bain_liquide(305, (58, 82, 84), 3, taches=(((80, 104, 104), 6),)),
    "galvanic_bath_liquid_fault": bain_liquide(306, (96, 86, 50), 7, taches=(((136, 78, 36), 16), ((48, 44, 30), 12))),
}

# La lampe de chaque état et son éclairage propre : les mêmes que l'établi.
ETATS_BAIN = {
    "stopped": {"lamp": "engineer_bench_lamp_off", "forge": None},
    "running": {"lamp": "engineer_bench_lamp_on", "forge": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}},
    "no_power": {"lamp": "engineer_bench_lamp_dim", "forge": {"block_light": 7, "sky_light": 0, "ambient_occlusion": False}},
    "fault": {"lamp": "engineer_bench_lamp_fault", "forge": {"block_light": 5, "sky_light": 0, "ambient_occlusion": False}},
}

# Le modèle : (nom, de, à, texture ou textures par face). GalvanicBathBlock.SHAPE reprend ces boîtes, sans le fil :
# les deux doivent rester d'accord.
ELEMENTS_BAIN = [
    ("pied_no", (1, 0, 1), (4, 6, 4), "#frame"),
    ("pied_ne", (12, 0, 1), (15, 6, 4), "#frame"),
    ("pied_so", (1, 0, 12), (4, 6, 15), "#frame"),
    ("pied_se", (12, 0, 12), (15, 6, 15), "#frame"),
    ("traverse_nord", (4, 2, 1.5), (12, 4, 3.5), "#frame"),
    ("traverse_sud", (4, 2, 12.5), (12, 4, 14.5), "#frame"),
    ("traverse_ouest", (1.5, 2, 4), (3.5, 4, 12), "#frame"),
    ("traverse_est", (12.5, 2, 4), (14.5, 4, 12), "#frame"),
    ("cuve", (1, 6, 1), (15, 12, 15), {"up": "#liquid", "*": "#tub"}),
    ("paroi_nord", (1, 12, 1), (15, 13.5, 2), "#tub"),
    ("paroi_sud", (1, 12, 14), (15, 13.5, 15), "#tub"),
    ("paroi_ouest", (1, 12, 2), (2, 13.5, 14), "#tub"),
    ("paroi_est", (14, 12, 2), (15, 13.5, 14), "#tub"),
    ("rebord_nord", (0.5, 13.5, 0.5), (15.5, 14.25, 2.25), "#anode"),
    ("rebord_sud", (0.5, 13.5, 13.75), (15.5, 14.25, 15.5), "#anode"),
    ("rebord_ouest", (0.5, 13.5, 2.25), (2.25, 14.25, 13.75), "#anode"),
    ("rebord_est", (13.75, 13.5, 2.25), (15.5, 14.25, 13.75), "#anode"),
    ("montant_nord", (7.25, 14.25, 0.75), (8.75, 15, 2.25), "#iron"),
    ("montant_sud", (7.25, 14.25, 13.75), (8.75, 15, 15.25), "#iron"),
    ("barre", (7.5, 15, 1), (8.5, 16, 15), "#brass"),
    ("anode", (7.25, 12, 4), (8.75, 15, 12), "#anode"),
    ("lampe_socle_no", (1, 14.25, 1), (4, 14.75, 4), "#brass"),
    ("lampe_socle_se", (12, 14.25, 12), (15, 14.75, 15), "#brass"),
    ("lampe_no", (1.5, 14.75, 1.5), (3.5, 16, 3.5), "#lamp"),
    ("lampe_se", (12.5, 14.75, 12.5), (14.5, 16, 14.5), "#lamp"),
    ("boitier", (5, 7.5, 15), (11, 10.5, 16), "#iron"),
    ("fil_boitier", (7.5, 10.5, 15.25), (8.5, 13.5, 15.75), "#cable"),
]


def modele_bain(etat):
    regles = ETATS_BAIN[etat]
    elements = []
    for nom, de, a, texture in ELEMENTS_BAIN:
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            t = texture if isinstance(texture, str) else texture.get(face, texture["*"])
            faces[face] = {"texture": t}
            if t == "#lamp":
                faces[face]["uv"] = [0, 0, 2, 2]
        element = {"name": nom, "from": list(de), "to": list(a), "faces": faces}
        if texture == "#lamp" and regles["forge"]:
            element["forge_data"] = regles["forge"]
        elements.append(element)
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"{MOD}:block/galvanic_bath_tub",
            "tub": f"{MOD}:block/galvanic_bath_tub",
            "anode": f"{MOD}:block/galvanic_bath_anode",
            "liquid": f"{MOD}:block/galvanic_bath_liquid_{etat}",
            "frame": f"{MOD}:block/engineer_bench_frame",
            "iron": f"{MOD}:block/engineer_bench_iron",
            "brass": f"{MOD}:block/engineer_bench_brass",
            "cable": f"{MOD}:block/engineer_bench_cable",
            "lamp": f"{MOD}:block/{regles['lamp']}",
        },
        "elements": elements,
    }


# ---------------------------------------------------------------------------------------------------
# Écriture
# ---------------------------------------------------------------------------------------------------
def ecrire_png(image, chemin):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    image.save(chemin, optimize=True)


def ecrire_json(valeur, chemin):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(valeur, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def ecrire_tout():
    for nom, image in OBJETS.items():
        ecrire_png(image, ASSETS / f"textures/item/{nom}.png")
        ecrire_json({"parent": "item/generated", "textures": {"layer0": f"{MOD}:item/{nom}"}},
                    ASSETS / f"models/item/{nom}.json")
    for nom, dessin in DEBRIS.items():
        ecrire_png(dessin(), ASSETS / f"textures/block/{nom}.png")
        ecrire_json({"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{nom}"}},
                    ASSETS / f"models/block/{nom}.json")
    for nom, image in BAIN.items():
        ecrire_png(image, ASSETS / f"textures/block/{nom}.png")
    ecrire_json({"animation": {"frametime": 3}}, ASSETS / "textures/block/galvanic_bath_liquid_running.png.mcmeta")
    for etat in ETATS_BAIN:
        ecrire_json(modele_bain(etat), ASSETS / f"models/block/galvanic_bath_{etat}.json")
    ecrire_json({"variants": {f"state={e}": {"model": f"{MOD}:block/galvanic_bath_{e}"} for e in ETATS_BAIN}},
                ASSETS / "blockstates/galvanic_bath.json")
    ecrire_json({"parent": f"{MOD}:block/galvanic_bath_stopped"}, ASSETS / "models/item/galvanic_bath.json")


def planche(chemin):
    """Tout sur une planche : à l'échelle 8 sur deux fonds, avec la taille réelle en bas pour juger la lisibilité."""
    from PIL import ImageDraw

    groupes = [
        ["fuse", "treated_fuse", "coil", "treated_coil", "bearing", "treated_bearing", "cleaning_kit",
         "treated_cleaning_kit"],
        ["tester", "welder", "scrap_metal", "aviation_wire", "instrument_core", "incomplete_instrument_core",
         "battery_cell", "incomplete_battery_cell"],
    ]
    blocs = [DEBRIS[n]() for n in DEBRIS] + [BAIN["galvanic_bath_tub"], BAIN["galvanic_bath_anode"]]
    liquides = [BAIN[f"galvanic_bath_liquid_{e}"].crop((0, 0, 16, 16)) for e in ETATS_BAIN]
    ech = 8
    pas = 16 * ech + 10
    img = Image.new("RGBA", (pas * 8 + 10, pas * 4 + 90), (139, 139, 139, 255))
    dessin = ImageDraw.Draw(img)
    y = 6
    for rangee in groupes:
        for i, nom in enumerate(rangee):
            img.alpha_composite(OBJETS[nom].resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
            dessin.text((8 + i * pas, y), nom.replace("incomplete_", "inc_").replace("treated_", "tr_"), fill=(255, 255, 255))
        y += pas
    for i, bloc in enumerate(blocs):
        img.alpha_composite(bloc.resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
    y += pas
    for i, bloc in enumerate(liquides):
        img.alpha_composite(bloc.resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
    y += pas
    # La taille d'une case d'inventaire à l'échelle 2 et à l'échelle réelle.
    x = 6
    for nom in groupes[0] + groupes[1]:
        img.alpha_composite(OBJETS[nom].resize((32, 32), Image.NEAREST), (x, y))
        img.alpha_composite(OBJETS[nom], (x + 8, y + 38))
        x += 38
    chemin.parent.mkdir(parents=True, exist_ok=True)
    img.save(chemin)


if __name__ == "__main__":
    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("--planche", type=Path, help="écrit aussi une planche de contrôle agrandie")
    options = arguments.parse_args()
    ecrire_tout()
    if options.planche:
        planche(options.planche)
