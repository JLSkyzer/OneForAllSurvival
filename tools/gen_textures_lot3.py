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
    "S": (244, 244, 238),  # sel blanc
    "j": (186, 204, 214),  # sel bleuté
    "k": (128, 148, 164),  # sel dans l'ombre
    "p": (182, 86, 78),  # chair salée
    "Q": (122, 50, 40),  # chair sombre
    "U": (92, 52, 38),  # viande séchée
    "W": (130, 76, 54),  # viande séchée claire
    "Y": (150, 158, 150),  # poisson gris
    "I": (104, 114, 110),  # poisson sombre
    "J": (200, 172, 122),  # poisson séché
    "D": (112, 44, 96),  # baie sombre
    "O": (214, 128, 48),  # fruit orangé
    "L": (146, 84, 40),  # fruit séché
    "6": (143, 220, 235),  # eau potable, la teinte de PotableWaterRendering
    "7": (84, 164, 190),  # eau potable dans l'ombre
    "8": (204, 246, 252),  # eau potable claire
    "m": (238, 236, 228),  # étiquette de bouteille
    "0": (244, 242, 230),  # émail crème, reflet
    "9": (190, 184, 164),  # émail crème, ombre
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


POTABLE_WATER_BOTTLE = lot2.sprite(
    "................",
    ".....KKKKKK.....",
    ".....KMMNMK.....",
    ".....KNMMNK.....",
    ".....KKKKKK.....",
    "......KaAK......",
    "......KaAK......",
    ".....KawAAK.....",
    "....KawA88AAK...",
    "...KaA8666667K..",
    "...KA866666677K.",
    "...KA866666677K.",
    "...KA666777777K.",
    "...KA667777777K.",
    "....KA7777777K..",
    ".....KKKKKKKK...",
)

SALT = lot2.sprite(
    "................",
    "................",
    "................",
    ".......KK.......",
    "......KSSK......",
    ".....KSSjSK.....",
    "....KSjSSSjK....",
    "...KSSSjSSSSK...",
    "..KSjSSSSjSSjK..",
    "..KSSSjSSSSSSkK.",
    ".KSjSSSSjSSSjkkK",
    ".KjSSjSSSSSSkkkK",
    ".KKkjSSkkjSkkKK.",
    "..KKKKKKKKKKKK..",
    "................",
    "................",
)

# Les six rations tiennent chacune sa silhouette : viande en lanière ou en pavé, poisson en poisson, fruit en
# boules. Les séchées pendent d'une ficelle (t) ; les salées sont piquetées de cristaux de sel (S).
DRIED_MEAT = lot2.sprite(
    "................",
    "..tt............",
    ".t..t...........",
    ".t..tKKKKK......",
    "..ttKUWWUUKK....",
    "....KUWUUWWUK...",
    "...KUWWUUQUWUK..",
    "...KUUWUWWUUUK..",
    "..KUWUUQUUWUUK..",
    "..KUUWWUUWUUK...",
    "..KQUUWUUUUKK...",
    "...KQUUWUUKK....",
    "....KKUUUKK.....",
    ".....KKKKK......",
    "................",
    "................",
)

SALTED_MEAT = lot2.sprite(
    "................",
    "................",
    "...KKKKKKKKKKK..",
    "..KSSjSSSjSSSSK.",
    "..KpppSppppSppK.",
    "..KpQppppSpppK..",
    "..KppppSpppQpK..",
    "..KpSpppppppK...",
    "..KppppQpSpppK..",
    "..KQppppSpppQK..",
    "..KQQppppQppQK..",
    "..KQQQQQQQQQQK..",
    "...KKKKKKKKKK...",
    "................",
    "................",
    "................",
)

DRIED_FISH = lot2.sprite(
    "................",
    "...tt...........",
    "..t..t..........",
    "..t..t.KKK......",
    "...ttKKJJJKK....",
    "...KKJJJJJJJKK.K",
    "..KJJKJJIJJJJJKK",
    "..KJKJIJJJIJJJJK",
    "..KJJKJJJJJJJKK.",
    "...KKJJIJJJKK.K.",
    "....KKJJJKK..KK.",
    ".....KKKK......."
    ,"................",
    "................",
    "................",
    "................",
)

SALTED_FISH = lot2.sprite(
    "................",
    "................",
    "................",
    "......KKKK......",
    "...KKKYYSYKK..KK",
    "..KYYYSYYYYYKKYK",
    ".KYSYIYYYSYYYYSK",
    ".KYYYYSYYYIYYYYK",
    "..KIYYYYSYYYKKYK",
    "...KKYSYYYYKK.KK",
    ".....KKSYKK.....",
    ".......KK.......",
    "................",
    "................",
    "................",
    "................",
)

def composer(*motifs):
    """Pose des motifs (x, y, lignes) sur une toile vide de 16 x 16 : de quoi dessiner des objets en plusieurs pièces."""
    toile = [["."] * 16 for _ in range(16)]
    for x, y, lignes in motifs:
        for dy, ligne in enumerate(lignes):
            for dx, c in enumerate(ligne):
                if c != ".":
                    toile[y + dy][x + dx] = c
    return lot2.sprite(*["".join(r) for r in toile])


# Une rondelle de fruit séché, le trou traversé par la ficelle.
RONDELLE = ("..KKK..", ".KLOLK.", "KLOtOLK", ".KLOLK.", "..KKK..")

DRIED_FRUIT = composer(
    (7, 0, ("t",)),
    (4, 1, RONDELLE), (4, 5, RONDELLE), (4, 9, RONDELLE),
    (6, 13, ("tt",)), (7, 14, ("t",)),
)

SALTED_FRUIT = lot2.sprite(
    "................",
    "......KK........",
    ".....KGGK.......",
    "....KKKKKKK.....",
    "...KDDDDDDDK....",
    "..KDDSDDDDSDK...",
    "..KDDDDSDDDDDK..",
    "..KDSDDDDDDSDK..",
    "..KDDDDDSDDDDK..",
    "..KDDSDDDDDSDK..",
    "..KDDDDDDSDDDK..",
    "...KDDDDDDDDK...",
    "....KKKKKKKK....",
    "................",
    "................",
    "................",
)

OBJETS = {
    "spoiled_food": SPOILED_FOOD,
    "salt": SALT,
    "potable_water_bottle": POTABLE_WATER_BOTTLE,
    "dried_meat": DRIED_MEAT,
    "salted_meat": SALTED_MEAT,
    "dried_fish": DRIED_FISH,
    "salted_fish": SALTED_FISH,
    "dried_fruit": DRIED_FRUIT,
    "salted_fruit": SALTED_FRUIT,
}

# ---------------------------------------------------------------------------------------------------
# Armoire frigorifique : un caisson de fer récupéré, porte à poignée de laiton, voyant d'état.
# ---------------------------------------------------------------------------------------------------
VOYANTS = {"stopped": "z", "running": "g", "no_power": "h", "fault": "P"}  # arrêt, au froid, sans courant, panne


def armoire_face(voyant):
    """Un vieux réfrigérateur de cabine : émail crème usé, petit compartiment froid en haut, grande porte dessous,
    poignées de laiton sur le côté, rouille au bas de la porte, voyant sur le compartiment du haut."""
    lignes = [
        "KKKKKKKKKKKKKKKK",
        "K0ffffffffffff9K",
        "K0fKKKffffffBf9K",
        "K0fKLKffffffbf9K",
        "K0fKKKffffffbf9K",
        "KKKKKKKKKKKKKKKK",
        "K0ffffffffffff9K",
        "K0ffffffffffBb9K",
        "K0ffffffffffBy9K",
        "K0ffffffffffBb9K",
        "K0ffffffffffBb9K",
        "K0ffffffffffBb9K",
        "K0fffffffffffb9K",
        "K0fffRffffffff9K",
        "K0fRRrfffRfff99K",
        "KKKKKKKKKKKKKKKK",
    ]
    lignes[3] = "K0fK" + voyant + "Kffffffbf9K"
    return lot2.sprite(*lignes)


ARMOIRE_COTE = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "KKKKKKKKKKKKKKKK",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K0fffffffRffff9K",
    "K0ffffffffffff9K",
    "K0ffffRfffffff9K",
    "K0fffRRffffRff9K",
    "K0ffRRrfffRRr99K",
    "KKKKKKKKKKKKKKKK",
)

ARMOIRE_DESSUS = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "K0000000000000fK",
    "K0ffffffffffff9K",
    "K0ffKKKKKKKKff9K",
    "K0ffKiiiiiidKf9K",
    "K0ffKKKKKKKKff9K",
    "K0ffKiiiiiidKf9K",
    "K0ffKKKKKKKKff9K",
    "K0ffffffffffff9K",
    "K0fffffffffRff9K",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K0ffffffffffff9K",
    "K99999999999999K",
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


# ---------------------------------------------------------------------------------------------------
# Dessalinisateur : une cuve de tôle zinguée, un serpentin de cuivre derrière une fenêtre, un robinet de laiton.
# ---------------------------------------------------------------------------------------------------
def dessalinisateur_face(voyant):
    lignes = [
        "KKKKKKKKKKKKKKKK",
        "KqZZZZZZZZZZZZzK",
        "KZZZZZZZZKKKKZzK",
        "KZZZZZZZZKLLKZzK",
        "KZZZZZZZZKLLKZzK",
        "KZKKKKKKKKKKKKzK",
        "KZKxCeCeCeCeCKzK",
        "KZKxxxxxxxxxeKzK",
        "KZKxeCeCeCeCCKzK",
        "KZKCxxxxxxxxxKzK",
        "KZKCeCeCeCeCxKzK",
        "KZKKKKKKKKKKKKzK",
        "KZZZZZZbBZZZZZzK",
        "KZRRZZZbyZZZRRzK",
        "KddddddKAKdddddK",
        "KKKKKKKKKKKKKKKK",
    ]
    return lot2.sprite(*[ligne.replace("L", voyant) for ligne in lignes])


DESSALINISATEUR_COTE = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "KqZZZZZZZZZZZZzK",
    "KZZZZZZZZZZZZZzK",
    "KZZZZZZZZZZZZZzK",
    "KZZqZZZZZZZZZZzK",
    "KZZZZZZZZZZZZZzK",
    "KBBBBBBBBBBBBBbK",
    "KyyyyyyyyyyyyyBK",
    "KbbbbbbbbbbbbbbK",
    "KZZZZZZZZZZZZZzK",
    "KZZZZZZZZZZZZZzK",
    "KZRRZZZZZZZZZZzK",
    "KZZRRZZZZZZZZZzK",
    "KZZZRRZZZZZZRRzK",
    "KddddddddddddddK",
    "KKKKKKKKKKKKKKKK",
)

DESSALINISATEUR_DESSUS = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "KqZZZZZZZZZZZZzK",
    "KZKKKKKKKKKKKKzK",
    "KZKaaAAAAAAAAKzK",
    "KZKa866666677KzK",
    "KZKA666666677KzK",
    "KZKA666666677KzK",
    "KZKA666666677KzK",
    "KZKA666666777KzK",
    "KZKAA77777777KzK",
    "KZKKKKKKKKKKKKzK",
    "KZZZZZZZZZZZZZzK",
    "KZZZZBByZZZZZZzK",
    "KZZZZbBBZZZZRRzK",
    "KddddddddddddddK",
    "KKKKKKKKKKKKKKKK",
)


# ---------------------------------------------------------------------------------------------------
# Séchoir : un meuble de fer à claies de bois, de la viande suspendue ; la lampe est sur le linteau.
# ---------------------------------------------------------------------------------------------------
def sechoir_face(voyant):
    lignes = [
        "KKKKKKKKKKKKKKKK",
        "KlllllllllKKKKdK",
        "KliiiiiiiiKLLKdK",
        "KliiiiiiiiKLLKdK",
        "KKKKKKKKKKKKKKKK",
        "KixtRtxtUtxtRtiK",
        "KixtUtxtWtxtUtiK",
        "KixxQxxxUxxxQxiK",
        "KiMMMMMMMMMMMMiK",
        "KinNNNNNNNNNNniK",
        "KixxxxxxxxxxxxiK",
        "KiMMMMMMMMMMMMiK",
        "KinNNNNNNNNNNniK",
        "KixxxxxxxxxxxxiK",
        "KiMMMMMMMMMMMMiK",
        "KKKKKKKKKKKKKKKK",
    ]
    return lot2.sprite(*[ligne.replace("L", voyant) for ligne in lignes])


SECHOIR_COTE = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "KllllllllllllldK",
    "KliiiiiiiiiiiidK",
    "KKKKKKKKKKKKKKKK",
    "KitTtTtTtTtTtTiK",
    "KiTutTutTutTutiK",
    "KitTtTtTtTtTtTiK",
    "KiTutTutTutTutiK",
    "KitTtTtTtTtTtTiK",
    "KiTutTutTutTutiK",
    "KitTtTtTtTtTtTiK",
    "KiTutTutTutTutiK",
    "KitTtTtTtTtTtTiK",
    "KidddddddddddddK",
    "KddddddddddddddK",
    "KKKKKKKKKKKKKKKK",
)

SECHOIR_DESSUS = lot2.sprite(
    "KKKKKKKKKKKKKKKK",
    "KllllllllllllllK",
    "KliKKKKKKKKKKidK",
    "KliKxxKxxKxxKidK",
    "KliKxxKxxKxxKidK",
    "KliKKKKKKKKKKidK",
    "KliKxxKxxKxxKidK",
    "KliKxxKxxKxxKidK",
    "KliKKKKKKKKKKidK",
    "KliiiiiiiiiiiidK",
    "KliiRoiiiiiiiidK",
    "KliiiRiiiiBByidK",
    "KliiiiiiiiibbBdK",
    "KliiiiiiiiiiiidK",
    "KddddddddddddddK",
    "KKKKKKKKKKKKKKKK",
)

ETATS = ("stopped", "running", "no_power", "fault")
# Pour chaque machine : le dessin de la face, le côté et le dessus.
MACHINES = {
    "desalinator": (dessalinisateur_face, DESSALINISATEUR_COTE, DESSALINISATEUR_DESSUS),
    "dryer": (sechoir_face, SECHOIR_COTE, SECHOIR_DESSUS),
}
ROTATIONS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def ecrire_machine(nom, face, cote, dessus):
    """Quatre faces avant (un voyant par état), un côté, un dessus ; un modèle par état, orienté par FACING."""
    for etat, voyant in VOYANTS.items():
        lot2.ecrire_png(face(voyant), lot2.ASSETS / f"textures/block/{nom}_front_{etat}.png")
        lot2.ecrire_json({"parent": "minecraft:block/orientable", "textures": {
            "particle": f"{lot2.MOD}:block/{nom}_side",
            "front": f"{lot2.MOD}:block/{nom}_front_{etat}",
            "side": f"{lot2.MOD}:block/{nom}_side",
            "top": f"{lot2.MOD}:block/{nom}_top"}}, lot2.ASSETS / f"models/block/{nom}_{etat}.json")
    lot2.ecrire_png(cote, lot2.ASSETS / f"textures/block/{nom}_side.png")
    lot2.ecrire_png(dessus, lot2.ASSETS / f"textures/block/{nom}_top.png")
    lot2.ecrire_json({"parent": f"{lot2.MOD}:block/{nom}_stopped"}, lot2.ASSETS / f"models/item/{nom}.json")
    variantes = {}
    for etat in VOYANTS:
        for direction, rotation in ROTATIONS:
            modele = {"model": f"{lot2.MOD}:block/{nom}_{etat}"}
            if rotation:
                modele["y"] = rotation
            variantes[f"facing={direction},state={etat}"] = modele
    lot2.ecrire_json({"variants": variantes}, lot2.ASSETS / f"blockstates/{nom}.json")


def ecrire_tout():
    for nom, image in OBJETS.items():
        lot2.ecrire_png(image, lot2.ASSETS / f"textures/item/{nom}.png")
        lot2.ecrire_json({"parent": "item/generated", "textures": {"layer0": f"{lot2.MOD}:item/{nom}"}},
                         lot2.ASSETS / f"models/item/{nom}.json")
    ecrire_armoire()
    for nom, (face, cote, dessus) in MACHINES.items():
        ecrire_machine(nom, face, cote, dessus)


def planche(chemin):
    """Tout le lot sur une planche de contrôle : objets à l'échelle 8, machines (quatre états) et taille réelle."""
    from PIL import Image, ImageDraw

    ech = 8
    pas = 16 * ech + 8
    blocs = [("fridge", armoire_face, ARMOIRE_COTE, ARMOIRE_DESSUS)] + [(n, *m) for n, m in MACHINES.items()]
    img = Image.new("RGBA", (pas * 6 + 8, pas * (len(blocs) + 3) + 20), (139, 139, 139, 255))
    dessin = ImageDraw.Draw(img)
    y = 6
    for nom, face, cote, dessus in blocs:
        for i, voyant in enumerate(VOYANTS.values()):
            img.alpha_composite(face(voyant).resize((16 * ech, 16 * ech), Image.NEAREST), (6 + i * pas, y))
        img.alpha_composite(cote.resize((16 * ech, 16 * ech), Image.NEAREST), (6 + 4 * pas, y))
        img.alpha_composite(dessus.resize((16 * ech, 16 * ech), Image.NEAREST), (6 + 5 * pas, y))
        dessin.text((8, y), nom, fill=(255, 255, 255))
        y += pas
    noms = list(OBJETS)
    for i, nom in enumerate(noms):
        col, ligne = i % 6, i // 6
        img.alpha_composite(OBJETS[nom].resize((16 * ech, 16 * ech), Image.NEAREST), (6 + col * pas, y + ligne * pas))
        dessin.text((8 + col * pas, y + ligne * pas), nom, fill=(255, 255, 255))
    y += pas * 2
    x = 6  # taille d'une case d'inventaire, à l'échelle 2 puis réelle
    for nom in noms:
        img.alpha_composite(OBJETS[nom].resize((32, 32), Image.NEAREST), (x, y))
        img.alpha_composite(OBJETS[nom], (x + 8, y + 38))
        x += 38
    chemin.parent.mkdir(parents=True, exist_ok=True)
    img.save(chemin)


if __name__ == "__main__":
    import argparse
    from pathlib import Path

    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("--planche", type=Path, help="écrit aussi une planche de contrôle agrandie")
    options = arguments.parse_args()
    ecrire_tout()
    if options.planche:
        planche(options.planche)
