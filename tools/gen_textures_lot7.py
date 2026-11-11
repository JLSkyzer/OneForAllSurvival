#!/usr/bin/env python3
"""Texture provisoire de la cache enfouie (lot 7) : une caisse de tole rouillee, 16 x 16, dessinee ici pixel par pixel.
Les visuels finaux arrivent a la tache 6 du lot.

    python tools/gen_textures_lot7.py
"""
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures/block"


def cache():
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    p = img.load()
    for y in range(16):
        for x in range(16):
            base = rnd.choice([(120, 74, 44), (132, 82, 48), (108, 66, 40), (142, 90, 52)])
            p[x, y] = base + (255,)
    for i in range(16):  # cerclage de tole
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i), (i, 7), (i, 8)):
            p[x, y] = (72, 70, 68, 255) if (x + y) % 3 else (92, 90, 88, 255)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):  # rivets
        p[x, y] = (176, 170, 160, 255)
    return img


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    cache().save(OUT / "buried_cache.png")
    print("ecrit", OUT / "buried_cache.png")
