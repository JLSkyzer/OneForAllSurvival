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

# ---------------------------------------------------------------------------------------------------
# Armoire frigorifique : un caisson de fer récupéré, porte à poignée de laiton, voyant d'état.
# ---------------------------------------------------------------------------------------------------
VOYANTS = {"stopped": "z", "running": "g", "no_power": "h", "fault": "P"}  # arrêt, au froid, sans courant, panne


def armoire_face(voyant):
    lignes = [
        "KKKKKKKKKKKKKKKK",
        "KllllllllllllllK",
        "KliiiiiiiiiiiidK",
        "KliKKKiiiiiiiidK",
        "KliK%KiiiiiiiidK",
        "KliKKKiiiiiiiidK",
        "KliiiiiiiiiiiidK",
        "KKKKKKKKKKKKKKKK",
        "KliiiiiiiiiiBbdK",
        "KliiiiiiiiiiBydK",
        "KliiiiiiiiiiBbdK",
        "KliiiiiiiiiiBbdK",
        "KliiiiiiiiiiiidK",
        "KldddddddddddddK",
        "KldddddddddddddK",
        "KKKKKKKKKKKKKKKK",
    ]
    lignes[4] = "KliK" + voyant + "KiiiiiiiidK"
    return lot2.sprite(*lignes)


ARMOIRE_COTE = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "KllllllllllllllK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KKKKKKKKKKKKKKKK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KldddddddddddddK",
    "KKKKKKKKKKKKKKKK",
)

ARMOIRE_DESSUS = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "KllllllllllllllK",
    "KlwliiiiiiiilidK",
    "KliiiiiiiiiiiidK",
    "KliKKKKKKKKKKidK",
    "KliKddddddddKidK",
    "KliKKKKKKKKKKidK",
    "KliKddddddddKidK",
    "KliKKKKKKKKKKidK",
    "KliKddddddddKidK",
    "KliKKKKKKKKKKidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KliiiiiiiiiiiidK",
    "KddddddddddddddK",
    "KKKKKKKKKKKKKKKK",
)


def ecrire_armoire():
    for etat, voyant in VOYANTS.items():
        lot2.ecrire_png(armoire_face(voyant), lot2.ASSETS / f"textures/block/fridge_front_{etat}.png")
        lot2.ecrire_json({"parent": "minecraft:block/orientable", "textures": {
            "particle": f"{lot2.MOD}:block/fridge_side",
            "front": f"{lot2.MOD}:block/fridge_front_{etat}",
            "side": f"{lot2.MOD}:block/fridge_side",
            "top": f"{lot2.MOD}:block/fridge_top"}}, lot2.ASSETS / f"models/block/fridge_{etat}.json")
    lot2.ecrire_png(ARMOIRE_COTE, lot2.ASSETS / "textures/block/fridge_side.png")
    lot2.ecrire_png(ARMOIRE_DESSUS, lot2.ASSETS / "textures/block/fridge_top.png")
    lot2.ecrire_json({"parent": f"{lot2.MOD}:block/fridge_stopped"}, lot2.ASSETS / "models/item/fridge.json")
    variantes = {}
    for etat in VOYANTS:
        for direction, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            modele = {"model": f"{lot2.MOD}:block/fridge_{etat}"}
            if rotation:
                modele["y"] = rotation
            variantes[f"facing={direction},state={etat}"] = modele
    lot2.ecrire_json({"variants": variantes}, lot2.ASSETS / "blockstates/fridge.json")


def ecrire_tout():
    for nom, image in OBJETS.items():
        lot2.ecrire_png(image, lot2.ASSETS / f"textures/item/{nom}.png")
        lot2.ecrire_json({"parent": "item/generated", "textures": {"layer0": f"{lot2.MOD}:item/{nom}"}},
                         lot2.ASSETS / f"models/item/{nom}.json")


if __name__ == "__main__":
    ecrire_tout()
    ecrire_armoire()
