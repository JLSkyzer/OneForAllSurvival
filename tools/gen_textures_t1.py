#!/usr/bin/env python3
"""Textures du lot T1 (telephone). Ici le cadre de l'ecran du telephone seulement (tache 9) ; la tache 10 ajoute
l'objet et la prise.

    python tools/gen_textures_t1.py

phone_frame.png : 124 x 256, un bord de 6 px autour d'un ecran de 112 x 244 (rapport de la page, 390 : 844).
L'ecran est noir : la page s'y dessine, ses coins arrondis laissent voir ce noir comme le verre d'un vrai telephone.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/oneforallsurvival/textures"

FRAME_W, FRAME_H, BORDER = 124, 256, 6


def phone_frame():
    """Cadre graphite arrondi, liseré clair, verre noir au milieu ; boutons du volume et de veille sur les flancs."""
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
    d.rounded_rectangle((b, b, w - 1 - b, h - 1 - b), radius=radius - b + 2 * scale, fill=(0, 0, 0, 255))  # verre
    img = img.resize((FRAME_W, FRAME_H), Image.LANCZOS)
    p = img.load()
    for (x0, y0, y1) in ((0, 44, 58), (0, 64, 78), (FRAME_W - 1, 56, 80)):  # boutons sur le liseré
        for y in range(y0, y1):
            p[x0, y] = (120, 122, 130, 255)
    return img


def main():
    out = ASSETS / "gui"
    out.mkdir(parents=True, exist_ok=True)
    phone_frame().save(out / "phone_frame.png")
    print("ecrit :", out / "phone_frame.png")


if __name__ == "__main__":
    main()
