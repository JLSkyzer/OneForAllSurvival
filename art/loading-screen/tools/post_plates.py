"""Finit les calques sortis de Blender : brume de distance et saturation, puis copie dans assets/plates."""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from common import ASSETS, CACHE

FOG = {
    "night": ("#16284c", 1500), "pre": ("#b57a86", 1400), "sun": ("#ffc58f", 1500),
}
SATURATION = {"night": 1.35, "pre": 1.25, "sun": 1.25}


def rgb(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else CACHE / "blender"
    out = ASSETS / "plates"
    out.mkdir(parents=True, exist_ok=True)
    depth = np.load(CACHE / "depth.npz")
    for path in sorted(source.glob("*.png")):
        name = path.stem
        image = np.asarray(Image.open(path).convert("RGBA"), np.float32) / 255
        if name.endswith("_fire"):
            Image.fromarray((image * 255 + 0.5).astype(np.uint8)).save(out / path.name)
            continue
        layer, light = name.replace("precrash_", "").split("_")
        color = image[..., :3]
        grey = color.mean(axis=-1, keepdims=True)
        color = np.clip(grey + (color - grey) * SATURATION[light], 0, 1)
        if layer != "fg":  # le replat est trop proche pour la brume
            fog_color, distance = FOG[light]
            t = depth[layer]
            amount = (1 - np.exp(-np.minimum(t, 3000) / distance))[..., None].astype(np.float32)
            color = color * (1 - amount) + rgb(fog_color) * amount
        result = np.dstack([color, image[..., 3]])
        Image.fromarray((result * 255 + 0.5).astype(np.uint8)).save(out / path.name)
        print("ok", name, flush=True)


if __name__ == "__main__":
    main()
