#!/usr/bin/env python3
"""Génère les textures et les modèles du lot 3 (électricité vitale, nourriture). Provisoires.

Réutilise la palette et les outils du lot 2 : même style de bricolage de survie, sans aucune ressource téléchargée.

    python tools/gen_textures_lot3.py
"""
import gen_textures_lot2 as lot2

# Teintes propres aux aliments avariés, ajoutées à la palette commune.
lot2.PALETTE.update({
    "1": (52, 60, 34),  # moisissure sombre
    "2": (88, 104, 52),  # moisissure
    "3": (128, 146, 70),  # moisissure claire
    "4": (70, 52, 36),  # chair brunie
    "5": (104, 78, 50),  # chair brune
})

SPOILED_FOOD = lot2.sprite(
    "................",
    "................",
    "......KKKK......",
    "....KKK55KKK....",
    "...K54455555K...",
    "..K5455532554K..",
    "..K5532222554K..",
    ".K4555223255554K",
    ".K4552212322554K",
    ".K4552322225554K",
    "..K45552255554K.",
    "..K44555555544K.",
    "...KK4444444KK..",
    ".....KKKKKKK....",
    "................",
    "................",
)

OBJETS = {"spoiled_food": SPOILED_FOOD}


def ecrire_tout():
    for nom, image in OBJETS.items():
        lot2.ecrire_png(image, lot2.ASSETS / f"textures/item/{nom}.png")
        lot2.ecrire_json({"parent": "item/generated", "textures": {"layer0": f"{lot2.MOD}:item/{nom}"}},
                         lot2.ASSETS / f"models/item/{nom}.json")


if __name__ == "__main__":
    ecrire_tout()
