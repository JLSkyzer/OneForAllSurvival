"""Compose l'écran de chargement à partir des seuls assets, puis l'encode en vidéo.

Chaque image dépend du temps et de la progression : la version en jeu pourra piloter
les mêmes calques avec la vraie progression de chargement.

Usage : python compose.py --frames 3.0,6.0,14,22,30   (images de contrôle, en secondes)
        python compose.py --video sortie.mp4
"""
import argparse
import json
import math
import multiprocessing as mp
import subprocess
import time

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

from common import ASSETS, CACHE, FFMPEG, JAR, OUT_H, OUT_W, PLATE_H, PLATE_W, PREVIEW, Camera, jar_image, jar_json, hex_rgb

FPS = 60
DURATION = 36.0
IMPACT = 4.4
BAR_IN = 5.2
LOAD_START = 5.8
LOAD_END = 29.0
FADE_OUT = 33.6

STATUS = [
    (0.00, "Signal de détresse émis…"),
    (0.14, "Recherche des survivants…"),
    (0.30, "Récupération des débris…"),
    (0.46, "Allumage du feu de camp…"),
    (0.62, "Exploration du littoral…"),
    (0.78, "Examen des ruines…"),
    (0.92, "Fondation de la colonie…"),
]


def load(path):
    return np.asarray(Image.open(ASSETS / path).convert("RGBA"), dtype=np.float32) / 255


def premult(image):
    out = image.copy()
    out[..., :3] *= out[..., 3:]
    return out


def ease_out_back(t):
    t = np.clip(t, 0, 1)
    c = 1.70158
    return 1 + (c + 1) * (t - 1) ** 3 + c * (t - 1) ** 2


def sstep(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def lerp3(values, k):
    """Interpolation entre nuit (k=0), pré-aube (k=1) et lever de soleil (k=2)."""
    k = min(2.0, max(0.0, k))
    i = min(1, int(k))
    f = k - i
    return values[i] * (1 - f) + values[i + 1] * f


def progress_at(t):
    """Courbe réaliste : le chargement avance par à-coups, mais la barre reste fluide."""
    if t <= LOAD_START:
        return 0.0
    if t >= LOAD_END:
        return 1.0
    x = (t - LOAD_START) / (LOAD_END - LOAD_START)
    raw = x + 0.035 * math.sin(x * 9.0) + 0.018 * math.sin(x * 23.0)
    return min(1.0, max(0.0, raw * (0.5 + 0.5 * x) + x * (0.5 - 0.5 * x)))


def rgb_palette(*colors):
    return [hex_rgb(c) for c in colors]


class Font:
    """Rendu avec la police bitmap du jar, comme le ferait le jeu."""

    def __init__(self):
        data = jar_json("font/include/default.json")
        self.glyphs = {}
        for provider in data["providers"]:
            if provider.get("type") != "bitmap":
                continue
            image = jar_image(provider["file"].split(":")[1].removesuffix(".png"))
            rows = provider["chars"]
            ch = image.shape[0] // len(rows)
            cw = image.shape[1] // len(rows[0])
            scale = provider.get("height", 8) / ch
            ascent = provider.get("ascent", 7)
            for r, row in enumerate(rows):
                for c, char in enumerate(row):
                    if char in self.glyphs or char == "\0":
                        continue
                    cell = image[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw, 3]
                    columns = np.where(cell.max(axis=0) > 0)[0]
                    width = int(columns.max()) + 1 if len(columns) else 3
                    if scale != 1:
                        continue
                    self.glyphs[char] = (cell[:, :width], width, ascent)
        self.cache = {}

    def render(self, text, scale):
        key = (text, scale)
        if key in self.cache:
            return self.cache[key]
        chars = [self.glyphs.get(c, self.glyphs["?"]) for c in text]
        width = sum((g[1] + 1) if t != " " else 4 for g, t in zip(chars, text))
        canvas = np.zeros((18, width + 2), np.float32)
        x = 0
        for (glyph, w, ascent), t in zip(chars, text):
            if t == " ":
                x += 4
                continue
            y = 11 - ascent  # les majuscules accentuées montent au-dessus de la ligne
            h = min(glyph.shape[0], canvas.shape[0] - y)
            canvas[y:y + h, x:x + w] = np.maximum(canvas[y:y + h, x:x + w], glyph[:h])
            x += w + 1
        canvas = np.kron(canvas, np.ones((scale, scale), np.float32))
        self.cache[key] = canvas
        return canvas

    def draw(self, frame, text, x, y, scale, color, alpha=1.0, shadow=True):
        mask = self.render(text, scale)
        if shadow:
            blit_mask(frame, mask, x + scale, y + scale, np.array(color) * 0.25, alpha * 0.9)
        blit_mask(frame, mask, x, y, np.array(color), alpha)
        return mask.shape[1]

    def width(self, text, scale):
        return self.render(text, scale).shape[1]


def clip_rect(frame, x, y, w, h):
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(frame.shape[1], x + w), min(frame.shape[0], y + h)
    if x0 >= x1 or y0 >= y1:
        return None
    return x0, y0, x1, y1, x0 - x, y0 - y


def blit_mask(frame, mask, x, y, color, alpha=1.0):
    r = clip_rect(frame, int(x), int(y), mask.shape[1], mask.shape[0])
    if not r:
        return
    x0, y0, x1, y1, sx, sy = r
    a = mask[sy:sy + y1 - y0, sx:sx + x1 - x0, None] * alpha
    frame[y0:y1, x0:x1] = frame[y0:y1, x0:x1] * (1 - a) + color * a


def blit_over(frame, sprite, x, y, alpha=1.0, tint=None):
    """Alpha classique, à une position entière. Sprite non prémultiplié."""
    r = clip_rect(frame, int(x), int(y), sprite.shape[1], sprite.shape[0])
    if not r:
        return
    x0, y0, x1, y1, sx, sy = r
    s = sprite[sy:sy + y1 - y0, sx:sx + x1 - x0]
    a = s[..., 3:] * alpha
    rgb = s[..., :3] if tint is None else s[..., :3] * tint
    frame[y0:y1, x0:x1] = frame[y0:y1, x0:x1] * (1 - a) + rgb * a


def blit_add(frame, sprite, x, y, color, alpha=1.0):
    r = clip_rect(frame, int(x), int(y), sprite.shape[1], sprite.shape[0])
    if not r:
        return
    x0, y0, x1, y1, sx, sy = r
    a = sprite[sy:sy + y1 - y0, sx:sx + x1 - x0, 3:] * alpha
    frame[y0:y1, x0:x1] += color * a


def resized(sprite, size, cache, key, smooth=True):
    k = (key, int(size))
    if k not in cache:
        mode = Image.BILINEAR if smooth else Image.NEAREST
        s = max(2, int(size))
        h = max(2, int(sprite.shape[0] * s / sprite.shape[1]))
        channels = [np.asarray(Image.fromarray(sprite[..., i]).resize((s, h), mode)) for i in range(4)]
        cache[k] = np.stack(channels, axis=-1)
    return cache[k]


class View:
    """Transformation d'un calque : recadrage et zoom autour du centre de l'image."""

    def __init__(self, zoom, off_x, off_y, src_w=PLATE_W, src_h=PLATE_H, src_scale=1.0):
        self.zoom, self.off_x, self.off_y = zoom, off_x, off_y
        xs = (np.arange(OUT_W) + 0.5 - OUT_W / 2) / zoom + PLATE_W / 2 + off_x
        ys = (np.arange(OUT_H) + 0.5 - OUT_H / 2) / zoom + PLATE_H / 2 + off_y
        xs, ys = xs / src_scale - 0.5, ys / src_scale - 0.5
        xs = np.clip(xs, 0, src_w - 1.001)
        ys = np.clip(ys, 0, src_h - 1.001)
        self.x0 = xs.astype(np.int32)
        self.y0 = ys.astype(np.int32)
        self.fx = (xs - self.x0)[None, :, None].astype(np.float32)
        self.fy = (ys - self.y0)[:, None, None].astype(np.float32)

    def sample(self, image):
        rows = image[self.y0]
        rows1 = image[self.y0 + 1]
        top = rows[:, self.x0] * (1 - self.fx) + rows[:, self.x0 + 1] * self.fx
        low = rows1[:, self.x0] * (1 - self.fx) + rows1[:, self.x0 + 1] * self.fx
        return top * (1 - self.fy) + low * self.fy

    def point(self, x, y):
        """Pixel du calque vers pixel de l'image finale."""
        return (x - PLATE_W / 2 - self.off_x) * self.zoom + OUT_W / 2, (y - PLATE_H / 2 - self.off_y) * self.zoom + OUT_H / 2


class Assets:
    pass


def npy_plate(name):
    """Convertit un calque PNG en tableau prémultiplié, partagé entre processus."""
    cached = CACHE / "plate_npy" / (name + ".npy")
    if not cached.exists():
        cached.parent.mkdir(exist_ok=True)
        image = np.asarray(Image.open(ASSETS / "plates" / (name + ".png")).convert("RGBA"), dtype=np.float32) / 255
        out = image[..., :3] * image[..., 3:]
        out = np.dstack([out, image[..., 3]])
        np.save(cached, (out * 255 + 0.5).astype(np.uint8))
    return np.load(cached, mmap_mode="r")


def fire_plate(name, layer):
    cached = CACHE / "plate_npy" / (name + ".npy")
    if not cached.exists():
        cached.parent.mkdir(exist_ok=True)
        image = np.asarray(Image.open(ASSETS / "plates" / (name + ".png")).convert("RGB"), dtype=np.float32) / 255
        alpha = np.asarray(Image.open(ASSETS / "plates" / (layer + "_night.png")).convert("RGBA"), dtype=np.float32)[..., 3:] / 255
        np.save(cached, ((image * alpha) * 255 + 0.5).astype(np.uint8))
    return np.load(cached, mmap_mode="r")


def init_assets():
    A = Assets
    A.m = json.loads((ASSETS / "manifest.json").read_text(encoding="utf-8"))
    A.plates = {layer: [npy_plate(f"{layer}_{light}") for light in ("night", "pre", "sun")] for layer in ("far", "mid", "fg")}
    A.precrash = npy_plate("precrash_mid_night")
    A.fire = {layer: fire_plate(f"{layer}_fire", layer) for layer in ("mid", "fg")}
    A.sky = [np.asarray(Image.open(ASSETS / f"sky/sky_{k}.png").convert("RGB"), dtype=np.float32) / 255 for k in ("night", "pre", "sun")]
    A.stars = [premult(load(f"sky/stars_{k}.png")) for k in "ab"]
    A.moon, A.sun = load("sky/moon.png"), load("sky/sun.png")
    A.halo = load("fx/halo.png")
    A.bank = [load(f"sky/cloudbank_{i}.png") for i in range(2)]
    A.rain = load("fx/rain.png")
    A.bolts = [load(f"fx/bolt_{i}.png") for i in range(3)]
    A.smoke = [load(f"fx/smoke_{i}.png") for i in range(6)]
    A.fire_strip = [load(f"fx/fire_{i}_strip.png") for i in range(2)]
    A.plane_icon = load("ui/plane_icon.png")
    A.crash = Crash()
    A.letters = [dict(item, image=load(item["file"])) for item in A.m["logo"]["letters"]]
    A.logo_glow, A.logo_face = load("ui/logo/glow.png"), load("ui/logo/face_mask.png")
    A.font = Font()
    A.cache = {}
    A.sea_frames = {}
    A.sea_shallow = np.asarray(Image.open(ASSETS / "sea/shallow.png").convert("L"), dtype=np.float32) / 255
    A.sea_depth = np.asarray(Image.open(ASSETS / "sea/depth.png").convert("L"), dtype=np.float32) / 255
    A.sea_mask = (np.asarray(Image.open(ASSETS / "sea/mask.png").convert("L"), dtype=np.float32) / 255)[..., None]
    A.moon_rays = load("fx/moon_rays.png")
    A.glint = {k: np.asarray(Image.open(ASSETS / f"sea/glint_{k}.png").convert("L"), dtype=np.float32) / 255 for k in ("moon", "sun")}
    # Plan des nuages voxel (texture vanilla) calculé une seule fois.
    A.cloud_tex = jar_image("environment/clouds")[..., 3] > 0.5
    camera = Camera(PLATE_W // 2, PLATE_H // 2)
    hh, ww = camera.height, camera.width
    yy, xx = np.mgrid[0:hh, 0:ww].astype(np.float64) + 0.5
    dirs = camera.rays(xx, yy)
    up = np.maximum(dirs[..., 1], 1e-3)
    A.cloud_rows = int((dirs[..., 1] > 0.01).any(axis=1).nonzero()[0].max()) + 1
    A.cloud_uv = []
    for level in (118.0, 122.0, 126.0, 130.0):
        t = level / up
        A.cloud_uv.append(((dirs[..., 0] * t)[: A.cloud_rows].astype(np.float32), (dirs[..., 2] * t)[: A.cloud_rows].astype(np.float32)))
    A.cloud_fade = np.clip((up[: A.cloud_rows] - 0.012) / 0.10, 0, 1).astype(np.float32)
    grid_y, grid_x = np.mgrid[0:OUT_H, 0:OUT_W].astype(np.float32)
    r = np.hypot((grid_x - OUT_W / 2) / (OUT_W / 2), (grid_y - OUT_H / 2) / (OUT_H / 2))
    A.vignette = (1 - 0.42 * np.clip(r - 0.35, 0, 1) ** 1.6)[..., None].astype(np.float32)
    A.bottom = (1 - 0.72 * np.clip((grid_y - OUT_H * 0.78) / (OUT_H * 0.22), 0, 1) ** 1.3)[..., None].astype(np.float32)
    A.grain = np.random.default_rng(1).normal(0, 1, (512, 512, 1)).astype(np.float32)


def sea_frame(i):
    A = Assets
    if i not in A.sea_frames:
        if len(A.sea_frames) > 12:
            A.sea_frames.pop(next(iter(A.sea_frames)))
        A.sea_frames[i] = np.asarray(Image.open(ASSETS / f"sea/sea_{i:02d}.png").convert("RGB"), dtype=np.float32) / 255
    return A.sea_frames[i]


def state(t):
    p = progress_at(t)
    day = sstep(0.28, 0.66, p) + sstep(0.66, 1.0, p)
    day = day + 0.0 if t < LOAD_END else min(2.0, day)
    s = {
        "t": t, "p": p, "day": day,
        "storm": 1 - sstep(0.16, 0.46, p),
        "stars": sstep(0.22, 0.42, p) * (1 - sstep(0.54, 0.74, p)),
        "moon": sstep(0.18, 0.40, p) * (1 - sstep(0.6, 0.82, p)),
        "fire": sstep(IMPACT, IMPACT + 0.25, t) * (1 - 0.8 * sstep(0.55, 1.0, p)),
    }
    return s


FLASHES = [(1.55, 0.7), (2.65, 1.0), (7.3, 0.8), (7.48, 0.5), (10.6, 0.65), (12.9, 0.45)]


def flash_at(t, storm):
    value = 0.0
    for at, power in FLASHES:
        dt = t - at
        if dt >= 0:
            value = max(value, power * math.exp(-dt * 7.5) * (0.8 + 0.2 * math.sin(dt * 90)))
    impact = 0.0
    if t >= IMPACT:
        impact = math.exp(-(t - IMPACT) * 3.2)
    return value * max(0.3, storm), impact


def draw_clouds(frame, view_sky, s):
    """Nuages voxel vanilla : dessous sombre, flancs plus clairs."""
    A = Assets
    t, day = s["t"], s["day"]
    drift = t * 1.3
    rows = A.cloud_rows
    masks = []
    for u, v in A.cloud_uv:
        iu = ((u + 40 + drift) / 12.0).astype(np.int32) % 256
        iv = ((v + 10 - drift * 0.18) / 12.0).astype(np.int32) % 256
        masks.append(A.cloud_tex[iv, iu])
    bottom = masks[0]
    side = (masks[1] | masks[2] | masks[3]) & ~bottom
    under = lerp3(rgb_palette("#10162b", "#7a5678", "#f2b48e"), day)
    wall = lerp3(rgb_palette("#242d4c", "#dc8d86", "#fff0d6"), day)
    color = np.zeros(bottom.shape + (4,), np.float32)
    color[bottom, :3] = under
    color[side, :3] = wall
    color[..., 3] = (bottom | side) * 1.0
    night_opacity = 0.22 + 0.63 * sstep(0.7, 1.7, s["day"])
    color[..., 3] *= A.cloud_fade * (night_opacity + 0.3 * s["storm"])
    color[..., :3] *= color[..., 3:]
    full = np.zeros((PLATE_H // 2, PLATE_W // 2, 4), np.float32)
    full[:rows] = color
    sampled = A.view_half.sample(full)
    frame[:] = frame * (1 - sampled[..., 3:]) + sampled[..., :3]


def draw_bank(frame, s, index, speed, opacity, palette):
    A = Assets
    tex = A.bank[index]
    h, w = tex.shape[:2]
    shift = int(s["t"] * speed) % w
    rows = min(OUT_H, int(OUT_H * 0.56))
    source = tex[(np.linspace(0, h - 1, rows)).astype(np.int32)]
    cols = (np.arange(OUT_W) + shift) % w
    slab = source[:, cols]
    color = lerp3(palette, s["day"])
    a = slab[..., 3:] * opacity * np.clip(1.2 - np.linspace(0, 1, rows)[:, None, None] * 0.6, 0, 1)
    shade = color * (0.75 + 0.5 * slab[..., :1])
    frame[:rows] = frame[:rows] * (1 - a) + shade * a


def draw_sea(frame, view_sea, s):
    A = Assets
    m = A.m["sea"]
    pos = s["t"] * 16.0
    i0 = int(pos) % m["frames"]
    i1 = (i0 + 1) % m["frames"]
    f = pos - int(pos)
    data = sea_frame(i0) * (1 - f) + sea_frame(i1) * f
    ripple, foam, spark = data[..., 0], data[..., 1], data[..., 2]
    day = s["day"]
    far_c = lerp3(rgb_palette("#0d1a38", "#a26677", "#f8c186"), day)
    near_c = lerp3(rgb_palette("#040a1c", "#2a3760", "#2e6d94"), day)
    shallow_c = lerp3(rgb_palette("#0d3748", "#4d7f8e", "#49c0bb"), day)
    mix = (1 - A.sea_depth)[..., None]
    base = far_c[None, None] * (1 - mix) + near_c[None, None] * mix
    shallow = A.sea_shallow[..., None] * 0.85
    base = base * (1 - shallow) + shallow_c * shallow
    color = base * (0.72 + 0.56 * ripple[..., None])
    foam_c = lerp3(rgb_palette("#7a90b8", "#f0c2bd", "#fff3e0"), day)
    color = color * (1 - foam[..., None] * 0.86) + foam_c * foam[..., None] * 0.86
    moon_power = s["moon"] * 0.75
    sun_power = sstep(1.0, 2.0, day) * 1.2 + sstep(0.6, 1.2, day) * 0.25
    color = color + A.glint["moon"][..., None] * spark[..., None] * hex_rgb("#c7d6ff") * moon_power
    color = color + A.glint["sun"][..., None] * spark[..., None] * hex_rgb("#ffcc7a") * sun_power
    color = view_sea.sample(np.clip(color, 0, 1.2))
    alpha = view_sea.sample(A.sea_mask)
    frame[:] = frame * (1 - alpha) + color * alpha


def layer_rgba(layer, day, precrash=False):
    plates = Assets.plates[layer]
    k = min(2.0, max(0.0, day))
    i = min(1, int(k))
    f = k - i
    if precrash:
        return np.asarray(Assets.precrash, dtype=np.float32) / 255
    if f < 1e-3:
        return np.asarray(plates[i], dtype=np.float32) / 255
    return (np.asarray(plates[i], dtype=np.float32) * (1 - f) + np.asarray(plates[i + 1], dtype=np.float32) * f) / 255


def over_premult(frame, sample, gain=1.0):
    frame[:] = frame * (1 - sample[..., 3:]) + sample[..., :3] * gain


def draw_fires(frame, view, s):
    A = Assets
    level = s["fire"]
    if level <= 0.01:
        return
    t = s["t"]
    for fire in A.m["fires"]:
        size = fire["size"] * (0.55 + 0.45 * level) * view.zoom
        strip = A.fire_strip[fire["strip"]]
        n = strip.shape[0] // strip.shape[1]
        index = (int(t * 22) + fire["phase"]) % n
        tile = strip[index * strip.shape[1]:(index + 1) * strip.shape[1]]
        sprite = resized(tile, size, A.cache, ("fire", fire["strip"], index))
        x, y = view.point(fire["x"], fire["y"])
        blit_over(frame, sprite, x - sprite.shape[1] / 2, y - sprite.shape[0] * 0.8, alpha=min(1.0, level * 1.4))
        blit_add(frame, sprite, x - sprite.shape[1] / 2, y - sprite.shape[0] * 0.8, hex_rgb("#ff9a3a"), 0.35 * level)


def draw_halos(frame, view, s, flicker):
    """Lueur douce autour de l'épave et lumières du camp."""
    A = Assets
    level = s["fire"]
    if level > 0.01:
        for plume in A.m["plumes"]:
            x, y = view.point(plume["x"], plume["y"])
            size = plume["scale"] * 34 * (0.7 + plume["power"] * 0.09)
            halo = resized(A.halo, size, A.cache, "halo")
            blit_add(frame, halo, x - size / 2, y - size / 2, hex_rgb("#ff7a2a"), 0.42 * level * flicker)
    night = 1 - sstep(1.1, 1.9, s["day"])
    for i, camp in enumerate(A.m["camp"]):
        on = sstep(camp["at"], camp["at"] + 0.04, s["p"]) * (0.25 + 0.75 * night)
        if on <= 0.01:
            continue
        wobble = 0.86 + 0.14 * math.sin(s["t"] * (9 + i) + i * 2.1)
        x, y = view.point(camp["x"], camp["y"])
        halo = resized(A.halo, 64, A.cache, "halo")
        blit_add(frame, halo, x - 32, y - 32, hex_rgb("#ffb35c"), 0.5 * on * wobble)
        frame[int(y) - 2:int(y) + 2, int(x) - 2:int(x) + 2] = frame[int(y) - 2:int(y) + 2, int(x) - 2:int(x) + 2] * (1 - on) + hex_rgb("#fff0c0") * on


def draw_smoke(frame, view, s):
    A = Assets
    t = s["t"]
    if t < IMPACT:
        return
    day = s["day"]
    dark = lerp3(rgb_palette("#2a2c38", "#6b5766", "#b49a8d"), day)
    lit = hex_rgb("#ff8a3c")
    rng_table = A.grain
    for pi, plume in enumerate(A.m["plumes"]):
        rate = 2.4 + plume["power"] * 0.45
        life = 7.5
        count = int(rate * life)
        scale = plume["scale"]
        for k in range(count):
            # Émission déterministe : chaque particule se déduit du temps courant.
            serial = math.floor((t - IMPACT) * rate) - k
            if serial < 0:
                continue
            born = IMPACT + serial / rate
            age = t - born
            if age < 0 or age > life:
                continue
            h1 = (math.sin(serial * 12.9898 + pi * 78.233) * 43758.5453) % 1
            h2 = (math.sin(serial * 39.346 + pi * 11.135) * 24634.6345) % 1
            u = age / life
            rise = (age ** 0.86) * scale * 3.4
            wind = (age ** 1.35) * scale * 1.15
            wob = math.sin(age * 1.3 + h1 * 6.28) * scale * (0.5 + age * 0.55)
            size = scale * (5 + age * 3.4) * (0.8 + h2 * 0.5)
            x, y = view.point(plume["x"] + wind + wob, plume["y"] - rise)
            level = 0.5 + 0.5 * s["fire"]
            alpha = sstep(0, 0.08, u) * (1 - u) ** 1.25 * 0.3 * level
            sprite = resized(A.smoke[int(h1 * 6) % 6], max(8, round(size / 6) * 6), A.cache, ("smoke", int(h1 * 6) % 6))
            heat = (1 - sstep(0.02, 0.22, u)) * s["fire"]
            color = dark * (1 - heat) + lit * heat
            blit_over(frame, sprite, x - sprite.shape[1] / 2, y - sprite.shape[0] / 2, alpha=alpha, tint=color)


def draw_embers(frame, view, s):
    A = Assets
    t = s["t"]
    if t < IMPACT:
        return
    level = s["fire"]
    wx, wy = A.m["wreck"]
    for i in range(170):
        life = 2.5 + (i * 0.37) % 3.0
        offset = (i * 0.7713) % life
        age = (t - IMPACT + offset) % life
        if t - IMPACT < age and t - IMPACT < offset:
            continue
        h = (i * 0.61803) % 1
        spread = (h - 0.5) * 460
        rise = age * (42 + h * 90)
        side = age ** 1.3 * 16 + math.sin(age * 2.5 + i) * 13
        px, py = view.point(wx + spread + side, wy + 18 - rise)
        size = 2 + (i % 3) * 1
        alpha = (1 - age / life) * level
        x, y = int(px), int(py)
        if 0 <= x < OUT_W - size and 0 <= y < OUT_H - size and alpha > 0.02:
            frame[y:y + size, x:x + size] += hex_rgb("#ff8a2c") * alpha * 1.2


def draw_rain(frame, s, flash):
    A = Assets
    storm = s["storm"]
    if storm < 0.01:
        return
    tex = A.rain
    t = s["t"]
    for speed, drift, alpha, tile in ((2400, -520, 0.55, 1.0), (1500, -340, 0.32, 1.0)):
        oy = int(t * speed) % 1024
        ox = int(t * -drift) % 1024
        xs = (np.arange(OUT_W) + ox) % 1024
        ys = (np.arange(OUT_H) - oy) % 1024
        mask = tex[ys][:, xs, 3:] * alpha * storm * (0.75 + flash)
        frame += mask * hex_rgb("#a9bce0") * 0.5


def draw_bolt(frame, s):
    t = s["t"]
    for index, (at, power) in enumerate(FLASHES):
        dt = t - at
        if 0 <= dt < 0.16 and power > 0.6:
            sprite = Assets.bolts[index % 3]
            x = [520, 1230, 760, 900, 1050, 300][index]
            blit_add(frame, sprite, x, -20, np.array([1.0, 1.0, 1.0]), power * (1 - dt / 0.16))


class Crash:
    """Chute de l'avion, lue dans la trajectoire 3D : même caméra que le décor."""

    def __init__(self):
        out = ASSETS / "fx/crash"
        data = json.loads((out / "track.json").read_text(encoding="utf-8"))
        self.start, self.fps = data["start"], data["fps"]
        self.crest_t, self.touch_t = data["crest_t"], data["touch_t"]
        self.crest = np.array(data["crest"])
        self.engine = np.array([f["engine"] for f in data["frames"]])
        self.tear = np.array([f["break"] for f in data["frames"]])
        self.pos = np.array([f["pos"] for f in data["frames"]])
        self.forward = np.array([f["f"] for f in data["frames"]])
        self.side = np.array([f["s"] for f in data["frames"]])
        self.offsets = json.loads((out / "sprites.json").read_text(encoding="utf-8"))
        self.dir = out
        self.camera = Camera()

    def index(self, t):
        return int(np.clip(round((t - self.start) * self.fps), 0, len(self.pos) - 1))

    def born(self, frame):
        return self.start + frame / self.fps

    def to_screen(self, view, point):
        """Point du monde vers l'image finale ; renvoie aussi les pixels par bloc."""
        x, y, depth = self.camera.project(point)
        depth = max(float(depth), 3.0)
        sx, sy = view.point(float(x), float(y))
        return sx, sy, self.camera.focal / depth


def hash01(a, b=0.0):
    return (math.sin(a * 12.9898 + b * 78.233) * 43758.5453) % 1


def draw_puff(frame, sprite_index, size, x, y, alpha, tint):
    A = Assets
    size = max(8, min(900, round(size / 6) * 6))
    sprite = resized(A.smoke[sprite_index % 6], size, A.cache, ("smoke", sprite_index % 6))
    blit_over(frame, sprite, x - sprite.shape[1] / 2, y - sprite.shape[0] / 2, alpha=alpha, tint=tint)


def draw_glow(frame, size, x, y, color, alpha):
    A = Assets
    size = max(8, min(1400, round(size / 4) * 4))
    halo = resized(A.halo, size, A.cache, "halo")
    blit_add(frame, halo, x - halo.shape[1] / 2, y - halo.shape[0] / 2, color, alpha)


def draw_crash_trail(frame, view, s):
    """Fumée émise le long de la trajectoire réelle : elle reste où l'avion est passé."""
    crash = Assets.crash
    t = s["t"]
    if t < crash.start:
        return
    now = crash.index(t)
    life = 4.2
    dark = hex_rgb("#5a5c6e")
    hot = hex_rgb("#c9662a")
    level = 1 - sstep(IMPACT + 1.0, IMPACT + 4.0, t)
    if level <= 0:
        return
    torn_from = crash.index(crash.crest_t)
    for j in range(0, now + 1, 2):
        age = t - crash.born(j)
        if not 0 <= age <= life:
            continue
        u = age / life
        for source, points in ((0, crash.engine), (1, crash.tear)):
            if source == 1 and j < torn_from:
                continue
            h1, h2 = hash01(j, source), hash01(j + 7.1, source + 3.0)
            spread = 0.9 + age * 2.6
            point = points[j] + np.array([
                (h1 - 0.5) * spread + age * 5.5 + age ** 1.5 * 1.5,
                (h2 - 0.5) * spread + age * 3.0,
                (hash01(j + 3.3, source) - 0.5) * spread,
            ])
            x, y, scale = crash.to_screen(view, point)
            size = (4.5 + age * 7.5) * scale * (0.8 + 0.4 * h1)
            alpha = sstep(0, 0.03, u) * (1 - u) ** 1.25 * 0.8 * level * (0.8 if source else 1.0)
            heat = 1 - sstep(0.04, 0.42, age)
            draw_puff(frame, j, size, x, y, alpha, dark * (1 - heat) + hot * heat)


def draw_crash_particles(frame, view, s, origin, born, count, seed, speed, spread, gravity, life, palette, size_blocks):
    """Éclats projetés au moment d'un choc : balistique simple, déterminée par le temps."""
    crash = Assets.crash
    age = s["t"] - born
    if age < 0:
        return
    for k in range(count):
        h = [hash01(k + 0.13 * n, seed) for n in range(6)]
        local = age * (0.6 + 0.8 * h[4])
        if local > life:
            continue
        velocity = speed + np.array([h[0] - 0.5, h[1] * 0.8, h[2] - 0.5]) * spread
        drag = (1 - math.exp(-2.2 * local)) / 2.2
        point = origin + velocity * drag + np.array([0.0, -gravity * local * local, 0.0])
        x, y, scale = crash.to_screen(view, point)
        side = max(2, int(size_blocks * scale * (0.5 + h[3])))
        ix, iy = int(x), int(y)
        if ix < 0 or iy < 0 or ix + side >= OUT_W or iy + side >= OUT_H:
            continue
        color = palette[k % len(palette)]
        fade = 1 - local / life
        frame[iy:iy + side, ix:ix + side] = frame[iy:iy + side, ix:ix + side] * (1 - fade) + color * fade


def draw_falling_plane(frame, s):
    """L'avion, ses flammes et ses chocs : ouverture du récit jusqu'à l'impact."""
    A = Assets
    crash = A.crash
    t = s["t"]
    view = A.view_mid
    draw_crash_trail(frame, view, s)
    if not crash.start <= t < IMPACT + 0.02:
        return
    i = crash.index(t)
    orange, red, core = hex_rgb("#ff8d32"), hex_rgb("#d8401a"), hex_rgb("#ffe2a0")

    # Flammes derrière le réacteur en feu, puis derrière l'aile arrachée.
    torn = crash.index(crash.crest_t)
    flicker = 0.8 + 0.2 * math.sin(t * 47) * math.sin(t * 31)
    for points, first in ((crash.engine, 0), (crash.tear, torn)):
        if i < first:
            continue
        for k in range(0, 16):
            j = i - k
            if j < first:
                break
            x, y, scale = crash.to_screen(view, points[j])
            fall = 1 - k / 16
            draw_glow(frame, (5 + 7 * fall) * scale, x, y, orange * fall + red * (1 - fall), 0.5 * fall * flicker)

    # Crête : les arbres fauchés, des éclats, et une lueur qui reste accrochée au relief.
    age = t - crash.crest_t
    if age >= 0:
        x, y, scale = crash.to_screen(view, crash.crest)
        draw_glow(frame, (44 * math.exp(-age * 3.5) + 24) * scale, x, y, orange, 0.55 * math.exp(-age * 3.5) + 0.34)
        velocity = crash.forward[crash.index(crash.crest_t)] * 110
        leaves = [hex_rgb("#2f6b2b"), hex_rgb("#3f8234"), hex_rgb("#6c4a26"), hex_rgb("#d7dbe2"), hex_rgb("#ffb050")]
        draw_crash_particles(frame, view, s, crash.crest, crash.crest_t, 90, 1.0, velocity, 95, 34, 1.5, leaves, 0.7)
        for k in range(14):
            local = age - k * 0.012
            if 0 <= local < 1.1:
                h = hash01(k, 5.0)
                draw_puff(frame, k, (10 + local * 34) * scale, x + (h - 0.5) * 22 * scale, y - local * 12 * scale, 0.48 * (1 - local / 1.1), hex_rgb("#2c2a2e"))

    # Glissade : poussière soulevée, gerbes d'étincelles sous le ventre, lueur au sol.
    touch = crash.index(crash.touch_t)
    if i >= touch:
        x, y, scale = crash.to_screen(view, crash.pos[i] + crash.forward[i] * 26 + np.array([0.0, -6.0, 0.0]))
        draw_glow(frame, 62 * scale, x, y, orange, 0.5)
        dust_dark, dust_lit = hex_rgb("#3e3b42"), hex_rgb("#8a6548")
        for j in range(touch, i + 1, 2):
            local = t - crash.born(j)
            h1, h2 = hash01(j, 8.0), hash01(j, 9.0)
            for side_sign in (-1, 1):
                point = crash.pos[j] - crash.forward[j] * (6 + 40 * h1) + crash.side[j] * side_sign * (4 + 7 * h2)
                point = point + np.array([0.0, -3.0 + local * 7, 0.0])
                x, y, scale = crash.to_screen(view, point)
                draw_puff(frame, j + side_sign, (9 + local * 30) * scale, x, y, 0.72 * (1 - local * 0.45), dust_dark * 0.45 + dust_lit * 0.55)

    sprite_key = str(i)
    if sprite_key in crash.offsets:
        image = np.asarray(Image.open(crash.dir / f"plane_{i:03d}.png"), dtype=np.float32) / 255
        ox, oy = crash.offsets[sprite_key]
        px, py = view.point(ox, oy)
        # L'air de la tempête voile l'avion tant qu'il est loin.
        blit_over(frame, image, px, py, alpha=0.62 + 0.38 * sstep(0.7, 1.9, t))

    # Coeur des flammes, devant la carlingue : le réacteur reste la source la plus vive.
    x, y, scale = crash.to_screen(view, crash.engine[i])
    draw_glow(frame, 13 * scale, x, y, orange, 0.8 * flicker)
    draw_glow(frame, 5 * scale, x, y, core, 0.95 * flicker)
    if i >= torn:
        x, y, scale = crash.to_screen(view, crash.tear[i])
        draw_glow(frame, 10 * scale, x, y, orange, 0.7 * flicker)

    if i >= touch:
        sparks = [hex_rgb("#ffd27a"), hex_rgb("#ff9a3a"), hex_rgb("#fff1c6")]
        for n, base in enumerate(range(touch, i + 1, 4)):
            origin = crash.pos[base] + crash.forward[base] * (20 - n * 2) + np.array([0.0, -4.5, 0.0])
            speed = -crash.forward[base] * 70
            draw_crash_particles(frame, view, s, origin, crash.born(base), 18, 20.0 + n, speed, 90, 22, 0.45, sparks, 0.28)


def draw_logo(frame, s):
    A = Assets
    t = s["t"]
    start = BAR_IN + 0.1
    if t < start:
        return
    m = A.m["logo"]
    ox, oy = 72, 58
    # Légère respiration, pour que le logo reste vivant pendant le chargement.
    bob = math.sin(t * 0.8) * 3
    glow_alpha = sstep(start + 0.6, start + 1.6, t) * (0.25 + 0.35 * sstep(0.9, 1.0, s["p"]))
    if glow_alpha > 0:
        pad = m["glow_pad"]
        blit_add(frame, A.logo_glow, ox - pad, oy - pad + bob, hex_rgb("#ffb040"), glow_alpha * 0.55)
    for i, letter in enumerate(A.letters):
        local = (t - start - i * 0.085) / 0.62
        if local <= 0:
            continue
        value = float(ease_out_back(local))
        y_offset = (1 - value) * -230
        alpha = sstep(0, 0.22, local)
        image = letter["image"]
        blit_over(frame, image, ox + letter["x"], oy + letter["y"] + y_offset + bob, alpha=alpha)
    # Reflet qui balaie le logo : à l'arrivée, puis une fois chargé.
    sweeps = [start + 1.4, LOAD_END + 0.25]
    face = A.logo_face
    h, w = face.shape[:2]
    for at in sweeps:
        u = (t - at) / 1.1
        if 0 <= u <= 1:
            yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
            line = xx + yy * 0.55 - (-200 + (w + 400) * u)
            band = np.exp(-(line / 46) ** 2) * face[..., 3]
            sprite = np.dstack([np.ones((h, w, 3), np.float32), band])
            blit_add(frame, sprite, ox, oy + bob, hex_rgb("#fff4d0"), 0.75)
    # Devise du projet, une fois le chargement terminé.
    tag = sstep(LOAD_END + 0.6, LOAD_END + 1.5, t)
    if tag > 0:
        A.font.draw(frame, "Là où tout recommence.", ox + 12, oy + m["height"] - 6, 3, hex_rgb("#3a1a08"), tag, shadow=False)


def draw_ui(frame, s):
    A = Assets
    t, p = s["t"], s["p"]
    alpha = sstep(BAR_IN, BAR_IN + 0.7, t)
    if alpha <= 0:
        return
    font = A.font
    x0, x1, y = 96, 1240, 1006
    width = x1 - x0
    slide = int((1 - alpha) * 26)
    y += slide
    # Rail : traits pointillés de la route vers l'île inconnue.
    for x in range(x0, x1, 18):
        frame[y:y + 4, x:x + 10] = frame[y:y + 4, x:x + 10] * (1 - 0.42 * alpha) + hex_rgb("#ffffff") * 0.42 * alpha
    # Traînée dorée : c'est elle, la barre de progression.
    head = x0 + int(width * p)
    if head > x0:
        trail = np.linspace(0, 1, head - x0, dtype=np.float32)[None, :, None]
        tip = hex_rgb("#ffe07a")
        tail = hex_rgb("#c8561a")
        color = tail * (1 - trail) + tip * trail
        a = (0.65 + 0.35 * trail) * alpha
        frame[y - 2:y + 6, x0:head] = frame[y - 2:y + 6, x0:head] * (1 - a) + color * a
        frame[y - 2:y, x0:head] = np.minimum(1.2, frame[y - 2:y, x0:head] + 0.18 * trail * alpha)
    plane = A.plane_icon
    bob = math.sin(t * 3.1) * 3
    blit_over(frame, plane, head - plane.shape[1] + 8, y - plane.shape[0] / 2 + 2 + bob, alpha=alpha)
    if p > 0:
        halo = resized(A.halo, 120, A.cache, "halo")
        blit_add(frame, halo, head - 60, y - 58, hex_rgb("#ffb24d"), 0.34 * alpha)

    # Repères de départ et d'arrivée, et état du chargement.
    white = hex_rgb("#f4f1e8")
    gold = hex_rgb("#ffd76a")
    font.draw(frame, "LOST ISLAND RP", x0, y + 16, 2, hex_rgb("#c9c6bc"), alpha)
    end = "ÎLE INCONNUE"
    font.draw(frame, end, x1 - font.width(end, 2), y + 16, 2, gold if p > 0.98 else hex_rgb("#c9c6bc"), alpha)
    text = STATUS[0][1]
    for at, label in STATUS:
        if p >= at:
            text = label
    if p >= 1:
        text = "Bienvenue sur Lost Island."
    font.draw(frame, text, x0, y - 62, 3, white, alpha)
    percent = f"{int(p * 100 + 1e-4)} %"
    font.draw(frame, percent, x1 - font.width(percent, 4), y - 72, 4, gold, alpha)


def render(t):
    A = Assets
    s = state(t)
    day = s["day"]
    flash, impact = flash_at(t, s["storm"])

    # Caméra : lente avancée, shake bref à l'impact.
    zoom = 1.0 + 0.042 * sstep(0, DURATION, t)
    ox = -14 + 28 * sstep(0, DURATION, t) + math.sin(t * 0.21) * 4
    oy = math.sin(t * 0.17) * 3
    # Les chocs de l'avion se transmettent à l'image : crête, sol, puis impact final.
    for at, power, decay in ((A.crash.crest_t, 4.0, 6.0), (A.crash.touch_t, 8.0, 5.0), (IMPACT, 15.0, 4.2)):
        if t >= at:
            shake = math.exp(-(t - at) * decay) * power
            ox += math.sin(t * 61) * shake
            oy += math.cos(t * 47) * shake
    A.view_mid = View(zoom, ox, oy)
    view_far = View(zoom, ox * 0.82, oy * 0.82)
    view_fg = View(1 + (zoom - 1) * 1.8, ox * 1.9, oy * 1.9)
    view_sky = View(1 + (zoom - 1) * 0.3, ox * 0.25, oy * 0.25, PLATE_W // 2, PLATE_H // 2, 2.0)
    A.view_half = view_sky
    view_sea = View(zoom, ox, oy, PLATE_W // 2, PLATE_H // 2, 2.0)

    frame = view_sky.sample(lerp3(A.sky, day))

    if s["stars"] > 0.01:
        twinkle = 0.5 + 0.5 * math.sin(t * 2.3)
        sky_view = View(1 + (zoom - 1) * 0.15, ox * 0.12, oy * 0.12)
        for i, star in enumerate(A.stars):
            sample = sky_view.sample(star)
            gain = s["stars"] * (twinkle if i == 0 else 1 - twinkle * 0.6)
            frame += sample[..., :3] * gain

    view_astre = View(1 + (zoom - 1) * 0.15, ox * 0.2, oy * 0.2)
    sun_up = sstep(0.8, 2.0, day)
    if sun_up > 0.01:
        sx = (A.m["sun_uv"][0] - 0.5) * OUT_W + PLATE_W / 2
        target_y = (A.m["sun_uv"][1] - 0.5) * OUT_H + PLATE_H / 2 + 44
        sy = target_y + (1 - sun_up) * 150
        px, py = view_astre.point(sx, sy)
        halo = resized(A.halo, 780, A.cache, "halo")
        blit_add(frame, halo, px - 390, py - 390, hex_rgb("#ffb04f"), 0.72 * sun_up)
        blit_add(frame, A.sun, px - A.sun.shape[1] / 2, py - A.sun.shape[0] / 2, hex_rgb("#fff4cf"), 1.3 * sun_up)

    # Bancs doux éclairés par la lune : le gros des nuages de la bannière.
    draw_bank(frame, s, 0, 14, 0.62 - 0.36 * sstep(0.7, 1.7, day), rgb_palette("#39436a", "#9c6682", "#ffc8a4"))
    draw_clouds(frame, view_sky, s)
    # La lune passe devant les nuages voxel : elle doit rester entière, comme sur la bannière.
    mx, my = view_astre.point((A.m["moon_uv"][0] - 0.5) * OUT_W + PLATE_W / 2, (A.m["moon_uv"][1] - 0.5) * OUT_H + PLATE_H / 2)
    if s["moon"] > 0.01:
        halo = resized(A.halo, 760, A.cache, "halo")
        blit_add(frame, halo, mx - 380, my - 380, hex_rgb("#a9c2f2"), 0.62 * s["moon"])
        blit_over(frame, A.moon, mx - A.moon.shape[1] / 2, my - A.moon.shape[0] / 2, alpha=s["moon"])
    if s["storm"] > 0.01:
        draw_bank(frame, s, 1, 44, 0.97 * s["storm"], rgb_palette("#0e1224", "#3d3a58", "#7d6a78"))
        draw_bank(frame, s, 0, 80, 0.8 * s["storm"], rgb_palette("#090c1a", "#2c2a44", "#5f5462"))
    draw_bolt(frame, s)
    if s["moon"] > 0.01:
        rays = view_sky.sample(A.moon_rays)
        breathing = 0.8 + 0.2 * math.sin(t * 0.7)
        frame += rays[..., 3:] * hex_rgb("#9db6e6") * 0.36 * s["moon"] * breathing

    draw_sea(frame, view_sea, s)
    over_premult(frame, view_far.sample(layer_rgba("far", day)))

    # Lueur du temple, signe discret qu'il reste quelque chose de vivant sur l'îlot.
    glimmer = sstep(0.74, 0.96, s["p"]) * (0.55 + 0.45 * math.sin(t * 1.7))
    if glimmer > 0.01:
        tx, ty = A.view_mid.point(*A.m["temple"])
        halo = resized(A.halo, 150, A.cache, "halo")
        blit_add(frame, halo, tx - 75, ty - 58, hex_rgb("#6fe6d8"), 0.48 * glimmer)

    # Avant l'impact, le décor n'a pas encore d'épave.
    over_premult(frame, A.view_mid.sample(layer_rgba("mid", day, precrash=t < IMPACT)))
    flicker = 0.82 + 0.1 * math.sin(t * 13.1) + 0.08 * math.sin(t * 31.7)
    if s["fire"] > 0.01:
        fire = A.view_mid.sample(A.fire["mid"]) / 255
        frame += fire * s["fire"] * flicker * 1.8
    draw_halos(frame, A.view_mid, s, flicker)
    draw_smoke(frame, A.view_mid, s)
    draw_fires(frame, A.view_mid, s)
    draw_embers(frame, A.view_mid, s)

    # Pendant la tempête, le monde est plus sombre et baigné d'une brume bleue.
    storm_dark = 1 - 0.34 * s["storm"]
    frame *= storm_dark
    draw_falling_plane(frame, s)

    fg = view_fg.sample(layer_rgba("fg", day))
    over_premult(frame, fg, gain=1 - 0.28 * s["storm"])
    if s["fire"] > 0.01:
        frame += view_fg.sample(A.fire["fg"]) / 255 * s["fire"] * flicker * 0.9

    draw_rain(frame, s, flash)

    # Éclair et impact : flash global, puis retombée.
    if flash > 0:
        frame = frame * (1 + 2.2 * flash) + hex_rgb("#9db8ff") * 0.18 * flash
    if impact > 0:
        wreck = A.view_mid.point(*A.m["wreck"])
        size = 1800
        halo = resized(A.halo, size, A.cache, "halo")
        blit_add(frame, halo, wreck[0] - size / 2, wreck[1] - size / 2, hex_rgb("#ffcf8a"), 2.4 * impact)
        frame = frame + hex_rgb("#ffe6c2") * impact ** 2 * 0.9

    # Bloom léger : c'est ce qui fait respirer les feux et le soleil.
    small = np.asarray(frame[::4, ::4])
    bright = np.clip(small - 0.72, 0, 2)
    blur = gaussian_filter(bright, (9, 9, 0))
    bloom = np.repeat(np.repeat(blur, 4, axis=0), 4, axis=1)[:OUT_H, :OUT_W]
    frame = frame + bloom * 0.9

    # Étalonnage : vignette et assombrissement du bas pour le texte.
    frame = frame * A.vignette
    if t >= BAR_IN:
        k = sstep(BAR_IN, BAR_IN + 0.7, t)
        frame = frame * (1 - k + k * A.bottom)

    draw_logo(frame, s)
    draw_ui(frame, s)

    # Ouverture depuis le noir, sortie vers le menu dans un voile chaud.
    frame *= sstep(0.0, 1.2, t)
    fade = sstep(FADE_OUT, DURATION - 0.3, t)
    if fade > 0:
        frame = frame * (1 - fade) + hex_rgb("#fff3dc") * fade

    # Grain fin : évite les aplats en escalier dans les dégradés une fois compressé.
    index = int(t * FPS)
    gy, gx = (index * 73) % 512, (index * 151) % 512
    grain = np.roll(np.roll(A.grain, gy, axis=0), gx, axis=1)
    h, w = frame.shape[:2]
    tiled = np.tile(grain, (h // 512 + 1, w // 512 + 1, 1))[:h, :w]
    frame = frame + tiled * (1.3 / 255)
    return (np.clip(frame, 0, 1) * 255 + 0.5).astype(np.uint8)


def render_index(i):
    return i, render(i / FPS).tobytes()


def write_video(path, start, end):
    command = [
        str(FFMPEG), "-y", "-hide_banner", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{OUT_W}x{OUT_H}", "-r", str(FPS), "-i", "-",
        "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-profile:v", "high", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
        str(path),
    ]
    encoder = subprocess.Popen(command, stdin=subprocess.PIPE)
    begin = time.time()
    frames = range(start, end)
    with mp.Pool(max(1, mp.cpu_count() - 3), initializer=init_assets, maxtasksperchild=200) as pool:
        for n, (index, data) in enumerate(pool.imap(render_index, frames, chunksize=2)):
            encoder.stdin.write(data)
            if n % 120 == 0:
                print(f"{n}/{len(frames)} images, {time.time() - begin:.0f}s", flush=True)
    encoder.stdin.close()
    encoder.wait()
    print("video ecrite", path, f"{time.time() - begin:.0f}s", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--frames", default=None)
    parser.add_argument("--video", default=None)
    parser.add_argument("--out", default=None)
    parser.add_argument("--range", default=None, help="début,fin en secondes")
    opts = parser.parse_args()
    if opts.frames:
        init_assets()
        out = PREVIEW if opts.out is None else __import__("pathlib").Path(opts.out)
        out.mkdir(parents=True, exist_ok=True)
        for value in opts.frames.split(","):
            begin = time.time()
            image = render(float(value))
            Image.fromarray(image).save(out / f"frame_{float(value):05.1f}.png")
            print(value, f"{time.time() - begin:.2f}s", flush=True)
    if opts.video:
        start, end = 0, int(DURATION * FPS)
        if opts.range:
            a, b = opts.range.split(",")
            start, end = int(float(a) * FPS), int(float(b) * FPS)
        write_video(opts.video, start, end)


if __name__ == "__main__":
    main()
