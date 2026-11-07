"""Textures 64x64 des pièces Lost Island : même dessin, cinq palettes.

Atlas (1 unité de UV Minecraft = 4 pixels ; 1 unité de modèle = 4 pixels sur la face) :
  (0,0)-(40,40)    face, avec le logo LI
  (40,0)-(46,40)   tranche verticale
  (0,40)-(40,46)   tranche horizontale
  (40,40)-(46,46)  tranche des coins coupés
  (48,0)-(54,3)    face des coins coupés (la ligne 0 est l'arête claire)
  (48,8)-(52,12)   flancs du logo en relief
"""
import random
import sys
from pathlib import Path

from PIL import Image

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)


def hex2(c):
    # +10 % : l'éclairage des faces assombrit le rendu par rapport au design d'origine
    return tuple(min(255, round(int(c[i : i + 2], 16) * 1.1)) for i in (1, 3, 5))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


PALETTES = {
    "copper": dict(edge="#f7a45c", rim="#5a2b10", rim_lit="#6d3615", ring="#b75f24", ring_lit="#d9823a", groove="#7a3d15",
                   field="#c96d2a", field_lit="#d97c35", letter="#ffb26a", letter_lit="#ffd09a", letter_dark="#e8924b", shadow="#60300f",
                   side="#6f3614", side_lit="#8f4a1d", side_dark="#4d240d", spark="#f0a050"),
    "silver": dict(edge="#f4f8fb", rim="#4a525c", rim_lit="#5b646f", ring="#9aa5b1", ring_lit="#c4ced8", groove="#67717c",
                   field="#aab5c0", field_lit="#bcc6d0", letter="#eef3f7", letter_lit="#ffffff", letter_dark="#ced7df", shadow="#4d555f",
                   side="#616b76", side_lit="#808b96", side_dark="#3f464f", spark="#e2e9ef"),
    "gold": dict(edge="#fff0a0", rim="#6b4408", rim_lit="#815410", ring="#d39a1f", ring_lit="#f2c23e", groove="#93650f",
                 field="#e3ac2a", field_lit="#efbd3c", letter="#ffe680", letter_lit="#fff5b8", letter_dark="#f6c94a", shadow="#74490a",
                 side="#865a0e", side_lit="#ab7a16", side_dark="#5c3b07", spark="#ffe98f"),
    "emerald": dict(edge="#a8ffd2", rim="#07402c", rim_lit="#0b5138", ring="#17915f", ring_lit="#2fc486", groove="#0c5f40",
                    field="#1ca870", field_lit="#2ebd83", letter="#8ff5c3", letter_lit="#d3ffe8", letter_dark="#54dba0", shadow="#084530",
                    side="#0e5c40", side_lit="#178055", side_dark="#06392a", spark="#b8ffdc"),
    "ruby": dict(edge="#ffb0bc", rim="#4d0817", rim_lit="#620c1e", ring="#b51c3a", ring_lit="#e23a58", groove="#74102a",
                 field="#c9243f", field_lit="#dc3a54", letter="#ff9fae", letter_lit="#ffd8de", letter_dark="#f06a80", shadow="#550a1a",
                 side="#70122a", side_lit="#971b38", side_dark="#470816", spark="#ffc2cc"),
}

# Logo, en pixels de la face. Les mêmes rectangles servent au relief 3D dans build.py.
LOGO = [
    (11, 11, 15, 25),  # L : montant
    (11, 25, 20, 29),  # L : pied
    (21, 11, 29, 15),  # I : empattement haut
    (23, 15, 27, 25),  # I : fût
    (21, 25, 29, 29),  # I : empattement bas
]


def in_logo(x, y):
    return any(a <= x < c and b <= y < d for a, b, c, d in LOGO)


def face_pixel(x, y, p, rnd):
    """Anneaux concentriques du design, éclairés en haut à gauche."""
    d = min(x, y, 39 - x, 39 - y)
    lit = x + y < 39  # côté haut/gauche : le relief prend la lumière

    if d == 0:
        c = p["edge"] if lit else mix(p["edge"], p["ring"], 0.45)
    elif d <= 3:
        c = p["rim_lit"] if d == 1 and lit else p["rim"]
    elif d <= 5:
        c = p["ring_lit"] if (d == 4 and lit) else p["ring"]
    elif d == 6:
        c = p["groove"]
    elif d <= 8:
        c = p["ring_lit"] if lit else mix(p["ring_lit"], p["ring"], 0.5)
    elif d == 9:
        c = p["groove"]
    else:
        c = p["field_lit"] if (x + y) < 30 else p["field"]

    # Usure du design : quelques pixels clairs ou sombres sur les anneaux, plus nombreux aux coins.
    if 4 <= d <= 8:
        corner = min(x, 39 - x) < 9 and min(y, 39 - y) < 9
        r = rnd.random()
        if r < (0.07 if corner else 0.02):
            c = mix(p["spark"], p["ring_lit"], 0.4)
        elif r < (0.12 if corner else 0.04):
            c = p["groove"]
    elif d >= 10:
        r = rnd.random()
        if r < 0.05:
            c = mix(c, p["field_lit"], 0.7)
        elif r < 0.09:
            c = mix(c, p["groove"], 0.35)
    return c


def build(name, p):
    p = {k: hex2(v) for k, v in p.items()}
    rnd = random.Random(1999)  # dessin identique pour les cinq variantes
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    # Face
    for y in range(40):
        for x in range(40):
            px[x, y] = (*face_pixel(x, y, p, rnd), 255)

    # Ombre portée du logo : un pixel en bas et à droite (lue sur le fond seulement)
    for y in range(40):
        for x in range(40):
            if not in_logo(x, y) and (in_logo(x - 1, y) or in_logo(x, y - 1) or in_logo(x - 1, y - 1)):
                px[x, y] = (*p["shadow"], 255)

    # Logo : arête claire en haut et à gauche, sombre en bas à droite
    for y in range(40):
        for x in range(40):
            if in_logo(x, y):
                c = p["letter"]
                if not in_logo(x - 1, y) or not in_logo(x, y - 1):
                    c = p["letter_lit"]
                elif not in_logo(x + 1, y) or not in_logo(x, y + 1):
                    c = p["letter_dark"]
                px[x, y] = (*c, 255)

    # Tranches : arête claire côté face, sombre au centre
    band = [p["side_lit"], p["side"], p["side_dark"], p["side_dark"], p["side"], p["side_lit"]]
    for i in range(40):
        for j, c in enumerate(band):
            r = rnd.random()
            v = mix(c, p["side_dark"], 0.3) if r < 0.08 else (mix(c, p["side_lit"], 0.5) if r < 0.14 else c)
            px[40 + j, i] = (*v, 255)  # tranche verticale (colonnes)
            px[i, 40 + j] = (*v, 255)  # tranche horizontale (lignes)
    for x in range(40, 46):
        for j, c in enumerate(band):
            px[x, 40 + j] = (*c, 255)

    # Face des coins coupés
    for x in range(48, 54):
        px[x, 48 - 48] = (*p["edge"], 255)
        for y in (1, 2):
            px[x, y] = (*p["rim"], 255)

    # Flancs du logo en relief
    for y in range(8, 12):
        for x in range(48, 52):
            px[x, y] = (*p["letter_dark"], 255)

    img.save(OUT / f"{name}_coin.png")


for name, palette in PALETTES.items():
    build(name, palette)
print("ok", sorted(f.name for f in OUT.glob("*.png")))
