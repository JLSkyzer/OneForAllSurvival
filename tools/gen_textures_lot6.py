#!/usr/bin/env python3
"""Génère les textures du lot 6 (vie de la ville) : pour l'instant l'icône de l'effet Bien-être.

Aucune ressource téléchargée : l'icône (18 x 18, comme les effets vanilla) est dessinée ici, pixel par pixel :
une goutte d'eau chaude ambrée et sa vapeur.

    python tools/gen_textures_lot6.py
"""
from pathlib import Path

from PIL import Image

SORTIE = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures/mob_effect"

AMBRE = (242, 180, 90, 255)
AMBRE_CLAIR = (255, 220, 150, 255)
AMBRE_SOMBRE = (186, 116, 40, 255)
VAPEUR = (255, 255, 255, 200)


def goutte():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = img.load()
    # Corps de la goutte : un disque en bas, une pointe en haut.
    for y in range(18):
        for x in range(18):
            if y >= 8:
                inside = (x - 8.5) ** 2 + (y - 11.5) ** 2 <= 5.2 ** 2
            else:
                inside = abs(x - 8.5) <= (y - 3) * 0.95 and y >= 3
            if inside:
                px[x, y] = AMBRE
    for y in range(18):
        for x in range(18):
            if px[x, y] == AMBRE and (x - 8.5) ** 2 + (y - 11.5) ** 2 > 3.9 ** 2 and y >= 9:
                px[x, y] = AMBRE_SOMBRE
    for x, y in ((6, 11), (6, 12), (7, 10), (7, 13)):
        px[x, y] = AMBRE_CLAIR
    # Deux filets de vapeur au-dessus.
    for x, y in ((6, 0), (7, 1), (6, 2), (11, 0), (12, 1), (11, 2)):
        px[x, y] = VAPEUR
    return img


if __name__ == "__main__":
    SORTIE.mkdir(parents=True, exist_ok=True)
    goutte().save(SORTIE / "well_being.png")
    print("écrit", SORTIE / "well_being.png")
