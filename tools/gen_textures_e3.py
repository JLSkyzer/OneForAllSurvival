#!/usr/bin/env python3
"""Textures du lot E3 (tresor et salaires), 16 x 16 : le bloc du tresor de la ville, un coffre de chene sombre cercle
de fer et de laiton. Face : une piece d'or frappee au centre, une serrure dessous ; flancs : planches et bandes de fer
rivetees ; dessus : couvercle borde de laiton. Et le tableau de la mairie, 3 blocs de large et 2 de haut : bois clair
pour les pieds, le chapeau et les tranches, parchemin sous cadre de bois pour la face (une tranche de 16 x 16 par
partie ; le texte est dessine par le rendu, pas ici), ses six modeles, son blockstate et l'icone de l'objet.

    python tools/gen_textures_e3.py
"""
import json
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

# ------------------------------------------------------------------------------------------------
# Le tableau de la mairie : 3 blocs de large, 2 de haut (six parties, une texture de face par partie)
# ------------------------------------------------------------------------------------------------

BOIS = [(150, 112, 66), (158, 119, 71), (142, 105, 61), (165, 126, 77)]
BOIS_JOINT = (96, 68, 38, 255)
PARCHEMIN = [(224, 208, 168), (230, 214, 174), (218, 201, 160), (234, 220, 182)]
CADRE_CLAIR = (186, 146, 92, 255)
CADRE_OMBRE = (110, 78, 44, 255)

MODELS = ASSETS.parent / "models"
BLOCKSTATES = ASSETS.parent / "blockstates"

# Les six parties : nom, colonne vue de face (-1 a gauche, 1 a droite), rangee (0 en bas). Comme TownBoardPart.
PARTIES = [("bottom_left", -1, 0), ("bottom_center", 0, 0), ("bottom_right", 1, 0),
           ("top_left", -1, 1), ("top_center", 0, 1), ("top_right", 1, 1)]

# Les boites du tableau entier tourne vers le nord, en pixels, la maitresse (bas du milieu) en x 0..16, y 0..16 ; la
# droite du lecteur est l'ouest (-x). DOIT rester TownBoardBlock.BOARD_NORTH (le GameTest laFormeSuitLeModele compare).
PANNEAU = (-16, 4, 6, 32, 30, 8)
CHAPEAU = (-16, 30, 5, 32, 31, 9)
PIEDS = [(-11, 1, 6, -9, 4, 8), (25, 1, 6, 27, 4, 8)]
PATINS = [(-12, 0, 5, -8, 1, 9), (24, 0, 5, 28, 1, 9)]


def tableau_bois():
    """Bois clair a veines horizontales : les pieds, le chapeau, les tranches et le dos du panneau."""
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


def tableau_face_entiere():
    """La face des 3 x 2 blocs, 48 x 32 (colonne 0 = bord gauche vu de face, rangee 0 = haut, y = 32) : panneau des
    rangees 2 a 27 (y 30 a 4), cadre de bois de 2 px (arete claire en haut et a gauche, ombre en bas et a droite),
    parchemin vieilli des rangees 4 a 25 et des colonnes 2 a 45, clous de laiton sur le cadre. Le texte est pose par le
    rendu, pas ici (zone de texte : 43 x 21 px au milieu du parchemin)."""
    rnd = random.Random(742)
    img = Image.new("RGBA", (48, 32))
    p = img.load()
    bois = tableau_bois().load()
    for y in range(32):
        for x in range(48):
            p[x, y] = bois[x % 16, y % 16]
    for y in range(4, 26):
        for x in range(2, 46):
            r, g, b = rnd.choice(PARCHEMIN)
            if x in (2, 45) or y in (4, 25):  # un bord un peu plus fonce, comme une feuille tendue
                r, g, b = r - 18, g - 20, b - 24
            p[x, y] = (r, g, b, 255)
    for (x, y) in ((9, 7), (10, 7), (31, 21), (32, 21), (40, 9), (17, 18)):  # taches d'age
        r, g, b, a = p[x, y]
        p[x, y] = (r - 14, g - 16, b - 22, 255)
    for x in range(48):
        p[x, 2] = CADRE_CLAIR
        p[x, 3] = BOIS[1] + (255,)
        p[x, 26] = BOIS[2] + (255,)
        p[x, 27] = CADRE_OMBRE
    for y in range(2, 28):
        p[0, y] = CADRE_CLAIR
        p[1, y] = BOIS[1] + (255,)
        p[46, y] = BOIS[2] + (255,)
        p[47, y] = CADRE_OMBRE
    p[0, 27] = BOIS_JOINT
    p[47, 2] = BOIS_JOINT
    for (x, y) in ((1, 3), (46, 3), (1, 26), (46, 26), (24, 3), (24, 26)):  # les clous, sur le cadre
        p[x, y] = LAITON[2]
    return img


def tableau_faces():
    """Les six tranches de 16 x 16 de la face entiere, une par partie (nom de la partie -> image)."""
    entiere = tableau_face_entiere()
    out = {}
    for nom, colonne, rangee in PARTIES:
        u0 = 16 + 16 * colonne
        v0 = 16 - 16 * rangee
        out[nom] = entiere.crop((u0, v0, u0 + 16, v0 + 16))
    return out


def tableau_icone():
    """L'objet : le tableau vu de face, en petit (parchemin, trois lignes d'encre aux couleurs du rendu, deux pieds)."""
    rnd = random.Random(743)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y in range(1, 12):
        for x in range(16):
            r, g, b = rnd.choice(BOIS)
            p[x, y] = (r, g, b, 255)
    for x in range(16):
        p[x, 1] = CADRE_CLAIR
        p[x, 11] = CADRE_OMBRE
    for y in range(1, 12):
        p[0, y] = CADRE_CLAIR
        p[15, y] = CADRE_OMBRE
    for y in range(3, 10):
        for x in range(2, 14):
            r, g, b = rnd.choice(PARCHEMIN)
            p[x, y] = (r, g, b, 255)
    for x in range(5, 11):
        p[x, 4] = (43, 27, 14, 255)
    for x in range(3, 13):
        if x != 7:
            p[x, 6] = (30, 90, 30, 255)
    for x in range(4, 12):
        if x != 8:
            p[x, 8] = (139, 26, 26, 255)
    for y in range(12, 16):
        for x in (3, 11):
            p[x, y] = BOIS[2] + (255,)
            p[x + 1, y] = BOIS_JOINT
    return img


def boite_partie(boite, colonne, rangee):
    """La boite du tableau entier qui tombe dans cette partie, en pixels de la partie, ou None."""
    x0, y0 = -16 * colonne, 16 * rangee
    b = (max(boite[0], x0) - x0, max(boite[1], y0) - y0, boite[2],
         min(boite[3], x0 + 16) - x0, min(boite[4], y0 + 16) - y0, boite[5])
    return b if b[0] < b[3] and b[1] < b[4] else None


def faces_bois(b, cacher):
    """Les faces de bois d'un element (uv tirees de sa position), sauf celles de {cacher}."""
    x0, y0, z0, x1, y1, z1 = b
    faces = {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "up": [x0, z0, x1, z1],
        "down": [x0, 16 - z1, x1, 16 - z0],
    }
    out = {}
    for nom, uv in faces.items():
        if nom in cacher:
            continue
        face = {"uv": uv, "texture": "#wood"}
        if nom == "down" and y0 == 0:
            face["cullface"] = "down"
        out[nom] = face
    return out


def modele_partie(nom, colonne, rangee):
    """Le modele d'une partie, tournee vers le nord : panneau (face de parchemin), chapeau, pied et patin s'il y en a.
    Les faces collees a une autre partie du tableau, ou cachees par un autre element, ne sont pas dessinees."""
    # Vu de face, la gauche du lecteur est l'est (colonne -1), sa droite l'ouest (colonne 1).
    cacher_cotes = set()
    if colonne != -1:
        cacher_cotes.add("east")
    if colonne != 1:
        cacher_cotes.add("west")
    elements = []
    panneau = boite_partie(PANNEAU, colonne, rangee)
    # le dessus du panneau est colle a la rangee du haut (bas) ou cache par le chapeau (haut) ; son dessous, en haut,
    # est colle a la rangee du bas
    cacher = cacher_cotes | {"north", "up"} | ({"down"} if rangee == 1 else set())
    faces = faces_bois(panneau, cacher)
    x0, y0, z0, x1, y1, z1 = panneau
    faces["north"] = {"uv": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "texture": "#front"}
    elements.append({"name": "panneau", "from": list(panneau[:3]), "to": list(panneau[3:]), "faces": faces})
    chapeau = boite_partie(CHAPEAU, colonne, rangee)
    if chapeau:
        elements.append({"name": "chapeau", "from": list(chapeau[:3]), "to": list(chapeau[3:]),
                         "faces": faces_bois(chapeau, cacher_cotes)})
    for pied in PIEDS:
        b = boite_partie(pied, colonne, rangee)
        if b:
            elements.append({"name": "pied", "from": list(b[:3]), "to": list(b[3:]),
                             "faces": faces_bois(b, {"up", "down"})})
    for patin in PATINS:
        b = boite_partie(patin, colonne, rangee)
        if b:
            elements.append({"name": "patin", "from": list(b[:3]), "to": list(b[3:]), "faces": faces_bois(b, set())})
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "oneforallsurvival:block/town_board_wood",
            "wood": "oneforallsurvival:block/town_board_wood",
            "front": "oneforallsurvival:block/town_board_front_" + nom,
        },
        "elements": elements,
    }


def ecrire_json(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("ecrit", chemin)


def tableau():
    """Les six faces, les six modeles, le blockstate (4 orientations x 6 parties) et l'objet (icone plate)."""
    for nom, image in tableau_faces().items():
        image.save(OUT / ("town_board_front_" + nom + ".png"))
        print("ecrit", OUT / ("town_board_front_" + nom + ".png"))
    for nom, colonne, rangee in PARTIES:
        ecrire_json(MODELS / "block" / ("town_board_" + nom + ".json"), modele_partie(nom, colonne, rangee))
    variantes = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for nom, _, _ in PARTIES:
            v = {"model": "oneforallsurvival:block/town_board_" + nom}
            if y:
                v["y"] = y
            variantes["facing=" + facing + ",part=" + nom] = v
    ecrire_json(BLOCKSTATES / "town_board.json", {"variants": variantes})
    ecrire_json(MODELS / "item" / "town_board.json",
                {"parent": "minecraft:item/generated", "textures": {"layer0": "oneforallsurvival:item/town_board"}})
    (ASSETS / "item").mkdir(parents=True, exist_ok=True)
    tableau_icone().save(ASSETS / "item" / "town_board.png")
    print("ecrit", ASSETS / "item" / "town_board.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for nom, fonction in (("treasury_front", tresor_face), ("treasury_side", tresor_cote), ("treasury_top", tresor_dessus),
                          ("town_board_wood", tableau_bois)):
        fonction().save(OUT / (nom + ".png"))
        print("ecrit", OUT / (nom + ".png"))
    tableau()


if __name__ == "__main__":
    main()
