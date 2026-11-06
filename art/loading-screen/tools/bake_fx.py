"""Génère les assets 2D : ciel, astres, nuages, mer en boucle, effets, logo, interface.

Tous les fichiers vont dans assets/. Le manifeste assets/manifest.json garde les
positions dont l'animation a besoin, pour que la version en jeu réutilise les mêmes.
"""
import json
import multiprocessing as mp

import numpy as np
from PIL import Image
from scipy.ndimage import binary_dilation, gaussian_filter, zoom

from build_world import SEA
from common import ASSETS, CACHE, OUT_H, OUT_W, PLATE_H, PLATE_W, Camera, fbm, hex_rgb, jar_image, save_rgba, smoothstep
from render_plates import MOON_UV, SUN_UV

SEA_FRAMES = 48
SEA_SCALE = 2  # La mer est stockée à mi-résolution puis agrandie à l'affichage.


def gradient(stops, height):
    ys = np.linspace(0, 1, height)
    positions = [s[0] for s in stops]
    colors = np.array([hex_rgb(s[1]) for s in stops])
    return np.stack([np.interp(ys, positions, colors[:, i]) for i in range(3)], axis=-1).astype(np.float32)


SKY_STOPS = {
    "night": [(0, "#04060f"), (0.2, "#070c20"), (0.36, "#101c3d"), (0.5, "#16264a"), (1, "#16264a")],
    "pre": [(0, "#10163a"), (0.14, "#2a2c5e"), (0.28, "#7a5583"), (0.36, "#e0836f"), (0.5, "#f0a071"), (1, "#f0a071")],
    "sun": [(0, "#37639f"), (0.14, "#7f94ba"), (0.26, "#e9b58f"), (0.36, "#ffd08a"), (0.5, "#ffdc9c"), (1, "#ffdc9c")],
}


def bake_sky():
    h, w = PLATE_H // 2, PLATE_W // 2
    sun_x, sun_y = (0.5 + (SUN_UV[0] - 0.5) * OUT_W / PLATE_W) * w, (0.5 + (SUN_UV[1] - 0.5) * OUT_H / PLATE_H) * h
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    r = np.hypot(xx - sun_x, (yy - sun_y) * 1.6) / w
    for name, stops in SKY_STOPS.items():
        ramp = gradient([(min(1.0, p * 1.15), c) for p, c in stops], h)
        tilted = np.clip(yy + (xx - w / 2) * 0.158, 0, h - 1).astype(np.int32)
        sky = ramp[tilted]
        if name == "sun":
            sky += np.exp(-(r / 0.11) ** 2)[..., None] * np.array([0.55, 0.38, 0.14], np.float32)
            sky += np.exp(-(r / 0.36) ** 2)[..., None] * np.array([0.26, 0.13, 0.03], np.float32)
        if name == "pre":
            sky += np.exp(-(r / 0.30) ** 2)[..., None] * np.array([0.26, 0.10, 0.02], np.float32)
        save_rgba(ASSETS / f"sky/sky_{name}.png", np.clip(sky, 0, 1))

    # Étoiles carrées comme dans le ciel vanilla, en deux jeux pour le scintillement.
    rng = np.random.default_rng(5)
    for suffix in "ab":
        stars = np.zeros((PLATE_H, PLATE_W, 4), np.float32)
        for _ in range(900):
            x, y = rng.integers(0, PLATE_W - 4), int(rng.beta(1, 1.5) * PLATE_H * 0.5)
            size = rng.choice([1, 2, 2, 3], p=[0.5, 0.3, 0.15, 0.05])
            value = rng.uniform(0.3, 1.0)
            stars[y:y + size, x:x + size] = (0.86 + rng.uniform(0, 0.14), 0.9, 1.0, value)
        save_rgba(ASSETS / f"sky/stars_{suffix}.png", stars)

    # Astres vanilla, agrandis sans lissage.
    size = 216
    yy2, xx2 = np.mgrid[0:size * 2, 0:size * 2].astype(np.float32)
    r = np.hypot(xx2 - size, yy2 - size)
    inner = np.hypot(xx2 - size * 1.28, yy2 - size * 0.86)
    disc = smoothstep(size * 0.92, size * 0.91, r)
    crescent = disc * smoothstep(size * 0.70, size * 0.73, inner)
    craters = fbm((size * 2, size * 2), 11, 301, octaves=5) * 0.5 + 0.5
    albedo = 0.72 + 0.34 * craters
    ghost = disc * (1 - crescent) * 0.10  # lumière cendrée de la partie sombre
    moon = np.dstack([albedo * 0.98, albedo, albedo * 1.04, np.clip(crescent + ghost, 0, 1)])
    Image.fromarray((np.clip(moon, 0, 1) * 255).astype(np.uint8)).resize((size, size), Image.LANCZOS).save(ASSETS / "sky/moon.png")
    # Rayons de lune en éventail, vers le bas et la gauche de l'image.
    yy3, xx3 = np.mgrid[0:PLATE_H // 2, 0:PLATE_W // 2].astype(np.float32)
    mx, my = (MOON_UV[0] * OUT_W + (PLATE_W - OUT_W) / 2) / 2, (MOON_UV[1] * OUT_H + (PLATE_H - OUT_H) / 2) / 2
    angle = np.arctan2(yy3 - my, xx3 - mx)
    dist = np.hypot(xx3 - mx, yy3 - my)
    beams = np.interp(angle, np.linspace(-np.pi, np.pi, 720), gaussian_filter(np.random.default_rng(6).random(720), 3, mode="wrap") * 3 - 1)
    beams = np.clip(beams, 0, 1) * smoothstep(np.pi * 0.62, np.pi * 0.78, angle) * smoothstep(np.pi * 1.0, np.pi * 0.93, angle)
    beams = beams * smoothstep(60, 240, dist) * smoothstep(760, 260, dist)
    save_rgba(ASSETS / "fx/moon_rays.png", np.dstack([np.full(beams.shape + (3,), 1.0), np.clip(beams * 0.9, 0, 1)]))
    sun = jar_image("environment/sun")
    alpha = sun[..., :3].max(axis=-1)
    sun_rgba = np.dstack([sun[..., :3], np.clip(alpha * 1.6, 0, 1)])
    Image.fromarray((np.clip(sun_rgba, 0, 1) * 255).astype(np.uint8)).resize((sun.shape[1] * 6, sun.shape[0] * 6), Image.NEAREST).save(ASSETS / "sky/sun.png")

    # Halo commun, teinté à l'exécution.
    size = 512
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    r = np.hypot(xx - size / 2, yy - size / 2) / (size / 2)
    halo = np.exp(-(r * 2.6) ** 2) * 0.75 + np.exp(-(r * 1.2) ** 2) * 0.25
    halo = np.clip(halo * smoothstep(1.0, 0.8, r), 0, 1)
    save_rgba(ASSETS / "fx/halo.png", np.dstack([np.ones((size, size, 3)), halo]))

    # Bancs de nuages doux, raccordables horizontalement.
    for i, (scale, cover) in enumerate(((46, 0.05), (22, 0.0))):
        n = fbm((512, 2112), scale, 60 + i, octaves=6, wrap=True)
        band = np.linspace(0, 1, 512)[:, None]
        n = n + 0.4 * (band - 0.35)
        mask = smoothstep(cover - 0.02, cover + 0.34, n)
        detail = fbm((512, 2112), 5, 70 + i, octaves=3, wrap=True) * 0.5 + 0.5
        save_rgba(ASSETS / f"sky/cloudbank_{i}.png", np.dstack([detail, detail, detail, mask]))

    # Voile de pluie : traits fins inclinés, raccordables.
    rng = np.random.default_rng(9)
    rain = np.zeros((1024, 1024), np.float32)
    for _ in range(620):
        x, y = rng.integers(0, 1024, 2)
        length = rng.integers(30, 90)
        value = rng.uniform(0.15, 0.7)
        for s in range(length):
            rain[(y + s) % 1024, (x - s // 4) % 1024] = max(rain[(y + s) % 1024, (x - s // 4) % 1024], value * (1 - s / length) ** 0.5)
    save_rgba(ASSETS / "fx/rain.png", np.dstack([np.full((1024, 1024, 3), hex_rgb("#c8d6f0")), rain]))


def sea_plane():
    """Coordonnées du plan de la mer pour chaque pixel de la mer stockée."""
    camera = Camera(PLATE_W // SEA_SCALE, PLATE_H // SEA_SCALE)
    h, w = camera.height, camera.width
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float64) + 0.5
    dirs = camera.rays(xx, yy)
    below = dirs[..., 1] < -1e-4
    t = np.where(below, (SEA + 0.8 - camera.pos[1]) / np.minimum(dirs[..., 1], -1e-4), 1e5)
    wx = camera.pos[0] + dirs[..., 0] * t
    wz = camera.pos[2] + dirs[..., 2] * t
    return wx.astype(np.float32), wz.astype(np.float32), t.astype(np.float32), below


class Sea:
    pass


def sea_init():
    Sea.wx, Sea.wz, Sea.t, Sea.below = sea_plane()
    shore = np.load(CACHE / "shore.npy")
    ix = np.clip(np.floor(Sea.wx).astype(np.int32), 0, shore.shape[0] - 1)
    iz = np.clip(np.floor(Sea.wz).astype(np.int32), 0, shore.shape[1] - 1)
    Sea.d = shore[ix, iz]
    Sea.d = np.where((Sea.wx < 0) | (Sea.wz < 0) | (Sea.wz >= shore.shape[1]) | (Sea.wx >= shore.shape[0]), -200, Sea.d)
    water = jar_image("block/water_still")
    Sea.tile = water[..., 0] * 0.3 + water[..., 1] * 0.59 + water[..., 2] * 0.11
    Sea.tile = Sea.tile / Sea.tile.max()
    Sea.noise = fbm((512, 512), 18, 80, octaves=3, wrap=True)
    Sea.spark = np.random.default_rng(81).random((256, 256), dtype=np.float32)


def sea_frame(i):
    phase = i / SEA_FRAMES * 2 * np.pi
    wx, wz, t = Sea.wx, Sea.wz, Sea.t
    warp = Sea.noise[(wz * 2).astype(np.int32) % 512, (wx * 2).astype(np.int32) % 512] * 2.5
    swell = (
        np.sin(wx * 0.26 + wz * 0.17 + warp + phase * 2) * 0.5
        + np.sin(wx * -0.13 + wz * 0.41 + warp * 1.3 + phase * 3) * 0.3
        + np.sin(wx * 0.57 + wz * 0.09 - warp + phase * 5) * 0.2
    )
    # Texture vanilla de l'eau, qui s'estompe au loin pour éviter le scintillement.
    nframes = Sea.tile.shape[0] // Sea.tile.shape[1]
    frame = int(i / SEA_FRAMES * nframes) % nframes
    u = (np.floor(wx * 16).astype(np.int32)) % 16
    v = (np.floor(wz * 16).astype(np.int32)) % 16 + frame * 16
    near = smoothstep(420, 130, t)
    ripple = swell * 0.55 * smoothstep(1500, 300, t) + (Sea.tile[v, u] - 0.72) * 2.0 * near
    ripple = ripple * 0.5 + 0.5

    # Écume en voxels : des vagues montent vers la plage.
    block_d = Sea.d + Sea.noise[np.floor(wz).astype(np.int32) % 512, np.floor(wx).astype(np.int32) % 512] * 4
    wave = np.sin(block_d * 0.55 + phase * 2)
    foam = smoothstep(0.55, 0.92, wave) * smoothstep(-22, -6, block_d)
    foam = np.maximum(foam, smoothstep(-5.5, -1.5, block_d))

    # Éclats pour les reflets : l'animation boucle en glissant sur la table de bruit.
    sx = (np.floor(wx * 0.9).astype(np.int32) + i * 3) % 256
    sz = (np.floor(wz * 0.5).astype(np.int32) - i * 5 * (1 + (i % 2 == 0))) % 256
    spark = Sea.spark[sz, sx]
    pulse = 0.5 + 0.5 * np.sin(phase * 2 + spark * 40)
    spark = np.where(spark > 0.62, pulse, 0.1 * pulse)

    image = np.dstack([ripple, foam, spark])
    image[~Sea.below] = 0
    save_rgba(ASSETS / f"sea/sea_{i:02d}.png", image)
    return i


def bake_sea():
    sea_init()
    h, w = Sea.d.shape
    shallow = smoothstep(-34, -3, Sea.d) * Sea.below
    save_rgba(ASSETS / "sea/shallow.png", np.dstack([shallow, shallow, shallow]))
    # Colonnes de reflet sous la lune et le soleil.
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    horizon = Sea.below.argmax(axis=0).min()
    sea_depth = np.clip((Sea.t / 1600.0) ** 0.42, 0, 1) * Sea.below
    save_rgba(ASSETS / "sea/depth.png", np.dstack([sea_depth, sea_depth, sea_depth]))
    mask = Sea.below.astype(np.float32)
    save_rgba(ASSETS / "sea/mask.png", np.dstack([mask, mask, mask]))
    for name, uv in (("moon", MOON_UV), ("sun", SUN_UV)):
        cx = (0.5 + (uv[0] - 0.5) * OUT_W / PLATE_W) * w
        depth = np.clip((yy - horizon) / (h - horizon), 0, 1)
        width = 18 + 190 * depth ** 1.2
        glint = np.exp(-((xx - cx) / width) ** 2) * Sea.below * smoothstep(0.92, 0.25, depth)
        save_rgba(ASSETS / f"sea/glint_{name}.png", np.dstack([glint, glint, glint]))
    with mp.Pool(max(1, mp.cpu_count() - 2), initializer=sea_init) as pool:
        for _ in pool.imap_unordered(sea_frame, range(SEA_FRAMES)):
            pass
    return int(horizon)


# Lettres du logo, dessinées à la main sur une grille de pixels.
LETTERS = {
    "L": ["XXX....", "XXX....", "XXX....", "XXX....", "XXX....", "XXX....", "XXX....", "XXXXXXX", "XXXXXXX"],
    "O": [".XXXXX.", "XXXXXXX", "XXX.XXX", "XXX.XXX", "XXX.XXX", "XXX.XXX", "XXX.XXX", "XXXXXXX", ".XXXXX."],
    "S": [".XXXXXX", "XXXXXXX", "XXX....", "XXXXX..", ".XXXXX.", "..XXXXX", "....XXX", "XXXXXXX", "XXXXXX."],
    "T": ["XXXXXXX", "XXXXXXX", "..XXX..", "..XXX..", "..XXX..", "..XXX..", "..XXX..", "..XXX..", "..XXX.."],
    "I": ["XXX", "XXX", "XXX", "XXX", "XXX", "XXX", "XXX", "XXX", "XXX"],
    "A": ["..XXX..", ".XXXXX.", "XXX.XXX", "XXX.XXX", "XXX.XXX", "XXXXXXX", "XXXXXXX", "XXX.XXX", "XXX.XXX"],
    "N": ["XXX..XX", "XXXX.XX", "XXXX.XX", "XXXXXXX", "XXXXXXX", "XX.XXXX", "XX.XXXX", "XX..XXX", "XX..XXX"],
    "D": ["XXXXXX.", "XXXXXXX", "XXX.XXX", "XXX.XXX", "XXX.XXX", "XXX.XXX", "XXX.XXX", "XXXXXXX", "XXXXXX."],
}


def letter_sprite(char, unit, seed, extrude, purple=False):
    grid = np.array([[c == "X" for c in row] for row in LETTERS[char]])
    mask = np.kron(grid, np.ones((unit, unit), dtype=bool))
    margin = extrude + 6
    canvas = np.zeros((mask.shape[0] + margin * 2, mask.shape[1] + margin * 2), dtype=bool)
    canvas[margin - 3:margin - 3 + mask.shape[0], margin - 3:margin - 3 + mask.shape[1]] = mask
    face = canvas
    h, w = canvas.shape
    rgba = np.zeros((h, w, 4), np.float32)

    side = np.zeros_like(face)
    side_depth = np.zeros((h, w), np.float32)
    for k in range(extrude, 0, -1):
        shifted = np.roll(np.roll(face, k, axis=0), k, axis=1)
        side |= shifted
        side_depth[shifted] = k / extrude
    outline = binary_dilation(side | face, iterations=3)

    rng = np.random.default_rng(seed)
    cell = max(2, unit // 4)
    noise = zoom(rng.random((h // cell + 2, w // cell + 2)), cell, order=0)[:h, :w].astype(np.float32)

    rgba[outline] = (*hex_rgb("#1b0a06"), 1)
    side_color = hex_rgb("#8a3a12")[None, None] * (1 - side_depth[..., None]) + hex_rgb("#3c1408")[None, None] * side_depth[..., None]
    side_color = side_color * (0.9 + 0.2 * noise[..., None])
    rgba[side, :3] = side_color[side]
    rgba[side, 3] = 1

    ys = np.linspace(0, 1, h)[:, None]
    top, mid, low = hex_rgb("#ffe97f"), hex_rgb("#fdb52a"), hex_rgb("#d96d10")
    if purple:
        top, mid, low = hex_rgb("#9a43e8"), hex_rgb("#7a22c9"), hex_rgb("#55149b")
    t = np.clip((ys - margin / h) / (1 - 2 * margin / h), 0, 1)
    color = np.where(t < 0.5, top * (1 - t * 2) + mid * (t * 2), mid * (2 - t * 2) + low * (t * 2 - 1))
    color = np.repeat(color[:, None, :].reshape(h, 1, 3), w, axis=1) if color.ndim == 2 else color
    color = color * (0.9 + 0.18 * noise[..., None])
    # Arêtes : lumière en haut à gauche, ombre en bas à droite.
    lit = face & ~(np.roll(face, 3, axis=0) & np.roll(face, 3, axis=1))
    dark = face & ~(np.roll(face, -3, axis=0) & np.roll(face, -3, axis=1))
    color[lit] = hex_rgb("#fff6c4")
    color[dark] = color[dark] * 0.72
    if purple:
        color[lit] = hex_rgb("#c58bff")
    rgba[face, :3] = color[face]
    rgba[face, 3] = 1
    if purple:
        # Tête de creeper, sur le haut de la lettre.
        u = unit
        pattern = ["XX.XX", "XX.XX", "..X..", ".XXX.", ".X.X."]
        cell_px = max(2, u * 3 // 5)
        x0 = margin - 3 + (mask.shape[1] - 5 * cell_px) // 2
        y0 = margin - 3 + u // 2
        for py, row in enumerate(pattern):
            for px, c in enumerate(row):
                if c == "X":
                    rgba[y0 + py * cell_px:y0 + (py + 1) * cell_px, x0 + px * cell_px:x0 + (px + 1) * cell_px, :3] = hex_rgb("#1a0a2a")
    return rgba, face


def bake_logo():
    rows = [("LOST", 13, 11), ("ISLAND", 13, 11)]
    placements = []
    x_margin = 0
    y = 0
    for row_index, (word, unit, extrude) in enumerate(rows):
        sprites = [letter_sprite(c, unit, 100 + row_index * 10 + i, extrude, purple=(word == "ISLAND" and c == "A")) for i, c in enumerate(word)]
        gap = unit - 2 * (extrude + 6)
        widths = [s[0].shape[1] for s in sprites]
        total = sum(widths) + gap * (len(word) - 1)
        placements.append((word, sprites, gap, total, y, unit))
        y += sprites[0][0].shape[0] + (-2 * (extrude + 6) + 12)
    width = max(p[3] for p in placements)
    height = y + 40
    manifest = []
    glow = np.zeros((height, width), np.float32)
    face_all = np.zeros((height, width), np.float32)
    count = 0
    for word, sprites, gap, total, row_y, unit in placements:
        x = (width - total) // 2
        for (rgba, face), char in zip(sprites, word):
            save_rgba(ASSETS / f"ui/logo/letter_{count:02d}_{char}.png", rgba)
            manifest.append({"file": f"ui/logo/letter_{count:02d}_{char}.png", "x": int(x), "y": int(row_y)})
            hh, ww = rgba.shape[:2]
            yy0, xx0 = max(0, row_y), max(0, x)
            region = rgba[yy0 - row_y:min(height - row_y, hh), xx0 - x:min(width - x, ww), 3]
            glow[yy0:yy0 + region.shape[0], xx0:xx0 + region.shape[1]] = np.maximum(glow[yy0:yy0 + region.shape[0], xx0:xx0 + region.shape[1]], region)
            fregion = face[yy0 - row_y:min(height - row_y, hh), xx0 - x:min(width - x, ww)]
            face_all[yy0:yy0 + region.shape[0], xx0:xx0 + region.shape[1]] = np.maximum(face_all[yy0:yy0 + region.shape[0], xx0:xx0 + region.shape[1]], fregion)
            x += ww + gap
            count += 1
    pad = 60
    soft = gaussian_filter(np.pad(glow, pad), 22)
    save_rgba(ASSETS / "ui/logo/glow.png", np.dstack([np.ones(soft.shape + (3,)), np.clip(soft * 1.2, 0, 1)]))
    save_rgba(ASSETS / "ui/logo/face_mask.png", np.dstack([np.ones(face_all.shape + (3,)), face_all]))
    return {"letters": manifest, "width": int(width), "height": int(height), "glow_pad": pad}


def pixel_sprite(rows, palette, scale):
    h, w = len(rows), len(rows[0])
    rgba = np.zeros((h, w, 4), np.float32)
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != ".":
                rgba[y, x] = (*hex_rgb(palette[c]), 1)
    return np.kron(rgba, np.ones((scale, scale, 1), np.float32))


def bake_fx():
    # Flammes vanilla, bandes de 32 images.
    for name in ("fire_0", "fire_1"):
        strip = jar_image(f"block/{name}")
        save_rgba(ASSETS / f"fx/{name}_strip.png", strip)

    # Fumée : quelques bouffées pixelisées à bords doux.
    rng = np.random.default_rng(12)
    for i in range(6):
        size = 128
        yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
        r = np.hypot(xx - size / 2, yy - size / 2) / (size / 2)
        n = fbm((size, size), 11, 200 + i, octaves=4)
        a = smoothstep(1.0, 0.25, r + n * 0.45)
        shade = 0.72 + 0.28 * np.clip((size - yy) / size + n * 0.5, 0, 1)
        save_rgba(ASSETS / f"fx/smoke_{i}.png", np.dstack([shade, shade, shade, a]))

    # Trois éclairs en zigzag, à arêtes franches.
    for i in range(3):
        h, w = 720, 420
        bolt = np.zeros((h, w), np.float32)
        def branch(x, y, length, width, depth):
            for _ in range(length):
                seg = int(rng.integers(14, 42))
                step = int(rng.integers(-26, 27))
                x1 = int(np.clip(x + step, 8, w - 8))
                for k in range(seg):
                    px = int(x + (x1 - x) * k / seg)
                    py = y + k
                    if py >= h:
                        return
                    bolt[py, max(0, px - width):px + width] = 1
                x, y = x1, y + seg
                if depth < 2 and rng.random() < 0.22:
                    branch(x, y, int(rng.integers(3, 7)), max(1, width - 1), depth + 1)
        branch(int(w * rng.uniform(0.35, 0.65)), 0, 40, 3, 0)
        soft = gaussian_filter(bolt, 7) * 1.3
        color = np.dstack([np.minimum(1, soft * 0.65 + bolt), np.minimum(1, soft * 0.75 + bolt), np.minimum(1, soft + bolt), np.clip(soft + bolt, 0, 1)])
        save_rgba(ASSETS / f"fx/bolt_{i}.png", color)

    # Icône de progression, dessinée au pixel.
    palette = {"w": "#f2f3f5", "g": "#aeb6c2", "d": "#5d6675", "b": "#2f6fb7", "c": "#7fd0f2"}
    plane = [
        "........d.......",
        ".......dbd......",
        "......dbbd......",
        "dd....dwwd......",
        "dbd...dww...ww..",
        "dwwwwwwwwwwwwww.",
        "dgwcwcwcwcwcwgww",
        ".dgggggggggggggd",
        "......dwwd......",
        ".....dwgd.......",
        "....dwgd........",
        "....ddd.........",
    ]
    save_rgba(ASSETS / "ui/plane_icon.png", pixel_sprite(plane, palette, 3))


def bake_all():
    bake_sky()
    horizon = bake_sea()
    bake_fx()
    logo = bake_logo()
    camera = Camera()
    world = json.loads((CACHE / "world.json").read_text())
    fires = json.loads((CACHE / "fires.json").read_text())
    depth = np.load(CACHE / "depth.npz")["mid"]
    billboards = []
    rng = np.random.default_rng(3)
    for fx, fy, fz, power in fires:
        px, py, d = camera.project((fx, fy, fz))
        for _ in range(max(3, int(power * 0.9))):
            wx, wy, wz = fx + rng.normal(0, 2.2), fy + rng.normal(0, 1.2) - 3, fz + rng.normal(0, 2.2)
            x, y, dd = camera.project((wx, wy, wz))
            ix, iy = int(np.clip(x, 0, PLATE_W - 1)), int(np.clip(y, 0, PLATE_H - 1))
            if depth[iy, ix] + 4 < dd:
                continue
            billboards.append({"x": float(x), "y": float(y), "size": float(2.4 * camera.focal / dd), "phase": int(rng.integers(0, 32)), "strip": int(rng.integers(0, 2))})
    # Colonnes de fumée aux emplacements de la bannière : autour de l'avion, jamais dessus.
    ox, oy = (PLATE_W - OUT_W) / 2, (PLATE_H - OUT_H) / 2
    plumes = [
        {"x": sx + ox, "y": sy + oy, "scale": scale, "power": power}
        for sx, sy, scale, power in ((735, 770, 7.0, 6), (935, 690, 6.5, 6), (1130, 585, 5.5, 7), (1170, 375, 4.0, 9), (1215, 380, 4.0, 6))
    ]
    temple = camera.project(tuple(world["temple"]))
    wreck = camera.project((world["wreck"][0], 44, world["wreck"][1]))
    # Lumières du camp : elles s'allument le long du rivage à mesure que le chargement avance.
    camp = []
    for i, (sx, sy) in enumerate(((452, 690), (405, 742), (372, 806), (392, 860), (340, 772), (470, 780), (430, 850), (360, 890))):
        camp.append({"x": float(sx + (PLATE_W - OUT_W) / 2), "y": float(sy + (PLATE_H - OUT_H) / 2), "at": 0.42 + 0.05 * i})
    manifest = {
        "output": [OUT_W, OUT_H], "plate": [PLATE_W, PLATE_H],
        "sea": {"frames": SEA_FRAMES, "scale": SEA_SCALE, "horizon_row": horizon},
        "moon_uv": list(MOON_UV), "sun_uv": list(SUN_UV),
        "fires": billboards, "plumes": plumes, "camp": camp,
        "temple": [temple[0], temple[1]], "wreck": [wreck[0], wreck[1]],
        "logo": logo,
    }
    (ASSETS / "manifest.json").write_text(json.dumps(manifest, indent=1), encoding="utf-8")
    print("assets ok :", len(billboards), "flammes,", len(plumes), "panaches,", len(camp), "lumieres")


if __name__ == "__main__":
    bake_all()
