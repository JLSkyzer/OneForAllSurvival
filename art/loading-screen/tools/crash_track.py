"""Chute de l'avion, en 3D : trajectoire image par image et modèle voxel de l'appareil intact.

L'avion arrive de loin dans l'axe de l'épave de la bannière, accroche la crête en feu,
perd un bout d'aile, puis se pose sur le ventre et glisse jusqu'à sa position finale.

Sorties :
    <cache>/crash_plane.npz    quads locaux du corps et du bout d'aile
    assets/fx/crash/track.json poses, foyers, chocs et débris, partagés avec Blender et compose.py

Usage : python crash_track.py [--check]   (--check trace la trajectoire sur l'image de la scène)
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy.interpolate import CubicSpline
from scipy.ndimage import gaussian_filter1d

from common import ASSETS, CACHE, OUT_H, OUT_W, PLATE_H, PLATE_W, Camera, hex_rgb, srgb_to_linear

FPS = 60
START = 0.6     # l'avion apparaît pendant l'ouverture depuis le noir
IMPACT = 4.4    # image où l'épave définitive remplace l'avion, sous l'éclair du choc
CREST_T = 3.32  # il accroche les arbres de la crête
TOUCH_T = 3.92  # le ventre touche le sol
WRECK_Y = 41
OX, OY = (PLATE_W - OUT_W) / 2, (PLATE_H - OUT_H) / 2


def on_plane(camera, sx, sy, level):
    d = camera.rays(np.array([sx + OX]), np.array([sy + OY]))[0]
    return camera.pos + d * (level - camera.pos[1]) / d[1]


def wreck_axis(camera):
    """Repères de la bannière : mêmes points que l'épave de build_banner."""
    nose, tail = on_plane(camera, 715, 935, WRECK_Y + 1), on_plane(camera, 1012, 702, WRECK_Y + 1)
    length = float(np.linalg.norm(tail - nose))
    return nose, tail, length


def build_plane(length):
    """Appareil intact, nez vers +u : mêmes proportions que l'épave, en cubes d'un bloc."""
    radius = 4.8
    span = 34.0
    root = length * 0.42
    body, tip = {}, {}
    white, belly, blue, dark, metal = "#e8ebef", "#aab2bd", "#2f6fb7", "#3a3f48", "#7d8590"
    glass_day, glass = "#203040", "#ffd98a"
    rng = np.random.default_rng(12)

    def put(store, u, v, w, color, glow=0.0):
        store[(int(round(u)), int(round(v)), int(round(w)))] = (color, glow)

    # Fuselage. u décroît vers la queue : le nez est à u = length / 2.
    for s in np.arange(0, length, 0.5):
        r = radius * min(1.0, math.sqrt(max(0.04, s / 9.0)))
        r *= 1 - 0.55 * max(0.0, (s - length * 0.78) / (length * 0.22))
        for ang in np.arange(0, 2 * math.pi, 0.08):
            v, w = r * math.cos(ang), r * math.sin(ang)
            color, glow = white, 0.0
            if w < -r * 0.45:
                color = belly
            elif r * 0.0 < w < r * 0.26:
                color = blue
            elif r * 0.3 < w < r * 0.62 and abs(v) > r * 0.4 and int(s) % 3 == 0 and 8 < s < length * 0.85:
                color, glow = glass, 2.4
            if s < 4.5 and w > r * 0.2:
                color = glass_day
            put(body, length / 2 - s, v, w, color, glow)

    # Ailes : flèche, dièdre léger. La partie extérieure droite se détache à la crête.
    for sign in (-1, 1):
        for v in np.arange(radius - 1, span, 0.5):
            t = v / span
            chord = 19 + (6 - 19) * t
            for du in np.arange(-chord / 2, chord / 2, 0.5):
                u = length / 2 - root + du - 14 * t
                color = white if int(v) % 5 else metal
                if du < -chord / 2 + 1.5:
                    color = metal
                if t > 0.92:
                    color = blue
                w = -1.5 + 4.0 * t
                store = tip if sign > 0 and t > 0.58 else body
                put(store, u, sign * v, w, color)
        # Réacteur sous l'aile.
        eu = length / 2 - root + 6 - 14 * (12 / span)
        for du in np.arange(-5, 5, 0.5):
            for ang in np.arange(0, 2 * math.pi, 0.25):
                put(body, eu + du, sign * 12 + 2.3 * math.cos(ang), -4.5 + 2.3 * math.sin(ang), dark if du > -3.5 else metal)

    # Empennage et dérive bleue.
    for sign in (-1, 1):
        for v in np.arange(0, 12, 0.5):
            for du in np.arange(-4 + v * 0.2, 4 - v * 0.2, 0.5):
                put(body, -length / 2 + 6 + du - v * 0.3, sign * v, 1.5, white)
    for i in np.arange(0, 20, 0.5):
        chord = 8.5 - i * 0.22
        for du in np.arange(-chord / 2, chord / 2, 0.5):
            put(body, -length / 2 + 5 + du - i * 0.35, 0, 3 + i, blue if i > 3 else white)

    def quads(store):
        verts, tints = [], []
        cache = {}
        for (u, v, w), (color, glow) in store.items():
            if color not in cache:
                cache[color] = srgb_to_linear(hex_rgb(color))
            tint = cache[color] * (0.9 + 0.2 * rng.random())
            for axis in range(3):
                for side in (0, 1):
                    n = [u, v, w]
                    n[axis] += 1 if side else -1
                    if tuple(n) in store:
                        continue
                    ia, ib = [(1, 2), (2, 0), (0, 1)][axis]
                    corner = np.array([u, v, w], np.float32) - 0.5
                    corner[axis] += side
                    a, b = np.zeros(3, np.float32), np.zeros(3, np.float32)
                    a[ia], b[ib] = 1, 1
                    quad = [corner, corner + a, corner + a + b, corner + b]
                    if not side:
                        quad = [quad[0], quad[3], quad[2], quad[1]]
                    verts.append(quad)
                    tints.append([*tint, glow])
        return np.array(verts, np.float32), np.array(tints, np.float32)

    bv, bt = quads(body)
    tv, tt = quads(tip)
    engine = (length / 2 - root + 2 - 14 * (12 / span), 12.0, -4.5)
    break_point = (length / 2 - root - 8, 0.58 * span, 0.8)
    return bv, bt, tv, tt, engine, break_point, 0.79 * span


def ease(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def build_track(camera):
    nose, tail, length = wreck_axis(camera)
    heading = (nose - tail) / length
    final = (nose + tail) / 2 + np.array([0, 4.8, 0])
    side = np.array([-heading[2], 0, heading[0]])

    def along(back, lateral, y):
        p = final - heading * back + side * lateral
        return [p[0], y, p[2]]

    # (temps, position) : longue approche courbe, crête, puis glissade droite dans l'axe de l'épave.
    knots = [
        (START, along(1250, -40, 475)),
        (1.5, along(990, -95, 372)),
        (2.4, along(650, -90, 240)),
        (CREST_T, along(238, 0, 116)),
        (TOUCH_T, along(46, 0, 52.5)),
        (TOUCH_T + 0.2, along(20, 0, final[1])),
        (IMPACT, along(0, 0, final[1])),
    ]
    times = np.array([k[0] for k in knots])
    spline = CubicSpline(times, np.array([k[1] for k in knots], np.float64))
    count = int(round((IMPACT - START) * FPS)) + 1
    t = START + np.arange(count) / FPS
    pos = spline(t)

    # Le nez suit la trajectoire, avec du retard : un avion ne pivote pas instantanément.
    velocity = spline(t, 1)
    forward = velocity / np.linalg.norm(velocity, axis=1, keepdims=True)
    forward = gaussian_filter1d(forward, 7, axis=0, mode="nearest")
    # En fin de glissade, l'orientation rejoint exactement celle de l'épave.
    settle = ease(TOUCH_T - 0.05, TOUCH_T + 0.25, t)[:, None]
    forward = forward * (1 - settle) + heading * settle
    forward /= np.linalg.norm(forward, axis=1, keepdims=True)

    # Tangage : nez bas et réaction au choc de la crête, arrondi juste avant le sol, rebond.
    bump = np.exp(-np.maximum(t - TOUCH_T, 0) * 6) * (t > TOUCH_T)
    pitch = (
        np.radians(-5) * (1 - ease(TOUCH_T - 0.5, TOUCH_T, t))
        + np.radians(7) * ease(TOUCH_T - 0.45, TOUCH_T - 0.05, t) * (1 - ease(TOUCH_T, TOUCH_T + 0.3, t))
        + np.radians(-9) * np.exp(-((t - CREST_T - 0.1) / 0.14) ** 2)
        + np.radians(3) * np.sin((t - TOUCH_T) * 26) * bump
    )

    # Roulis : virage de l'approche, secousses de la tempête, chute sur l'aile abîmée, remise à plat.
    bank = np.radians(-24) * np.gradient(gaussian_filter1d(np.arctan2(forward[:, 0], forward[:, 2]), 10, mode="nearest"), 1 / FPS) * 3.5
    gust = np.radians(4.5) * (np.sin(t * 7.3) + 0.6 * np.sin(t * 13.7 + 1.0) + 0.3 * np.sin(t * 23.1)) * (1 - ease(TOUCH_T - 0.2, TOUCH_T + 0.1, t))
    hurt = np.radians(30) * ease(CREST_T, CREST_T + 0.34, t)
    roll = bank + gust + hurt
    roll *= 1 - ease(TOUCH_T - 0.3, TOUCH_T + 0.02, t)
    roll += np.radians(2.5) * np.sin((t - TOUCH_T) * 31) * bump

    # Secousses de position, de plus en plus visibles quand l'avion est proche.
    shake = np.stack([np.sin(t * 19.1), np.sin(t * 27.3 + 2.0) * 0.8, np.sin(t * 16.3 + 4.0)], axis=1)
    pos = pos + shake * (0.9 * (1 - ease(TOUCH_T - 0.1, TOUCH_T, t)))[:, None]
    pos[-1] = final

    up_ref = np.array([0.0, 1.0, 0.0])
    frames = []
    for i in range(count):
        f = forward[i]
        s = np.cross(f, up_ref)
        s /= np.linalg.norm(s)
        u = np.cross(s, f)
        # Tangage autour de s, roulis autour de f.
        cp, sp = math.cos(pitch[i]), math.sin(pitch[i])
        f2 = f * cp + u * sp
        u2 = u * cp - f * sp
        cr, sr = math.cos(roll[i]), math.sin(roll[i])
        s2 = s * cr + u2 * sr
        u3 = u2 * cr - s * sr
        frames.append({"t": float(t[i]), "pos": pos[i].tolist(), "f": f2.tolist(), "s": s2.tolist(), "u": u3.tolist()})
    return frames, pos, final, heading, length


def tip_flight(frames, tip_center):
    """Bout d'aile arraché : il garde son élan, freine vite, tombe et tourbillonne."""
    hit = next(i for i, fr in enumerate(frames) if fr["t"] >= CREST_T)
    fr = frames[hit]
    start = np.array(fr["pos"]) + np.array(fr["s"]) * tip_center
    speed = (np.array(frames[hit + 3]["pos"]) - np.array(frames[hit]["pos"])) / (3 / FPS)
    out = []
    axis = np.array([0.5, 0.3, 0.8])
    axis /= np.linalg.norm(axis)
    for i, frame in enumerate(frames):
        if i <= hit:
            out.append(None)
            continue
        age = frame["t"] - fr["t"]
        drag = 1.7
        travel = speed * (1 - math.exp(-drag * age)) / drag
        p = start + travel + np.array([9, -95, 4]) * age ** 2 + np.array(fr["s"]) * 26 * age
        angle = age * 11.0
        c, s_ = math.cos(angle), math.sin(angle)

        def rot(v):
            v = np.array(v)
            return v * c + np.cross(axis, v) * s_ + axis * (axis @ v) * (1 - c)

        out.append({"pos": p.tolist(), "f": rot(fr["f"]).tolist(), "s": rot(fr["s"]).tolist(), "u": rot(fr["u"]).tolist()})
    return out


def crop_sprites(source):
    """Découpe chaque image Blender à son contenu : compose.py n'a plus qu'à la poser."""
    out = ASSETS / "fx/crash"
    offsets = {}
    for path in sorted(Path(source).glob("plane_*.png")):
        image = Image.open(path).convert("RGBA")
        box = image.getchannel("A").getbbox()
        if box is None:
            continue
        image.crop(box).save(out / path.name, compress_level=6)
        offsets[str(int(path.stem.split("_")[1]))] = [box[0], box[1]]
    (out / "sprites.json").write_text(json.dumps(offsets), encoding="utf-8")
    print("sprites", len(offsets))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--sprites", default=None, help="dossier des images Blender à découper")
    opts = parser.parse_args()
    if opts.sprites:
        crop_sprites(opts.sprites)
        return
    camera = Camera()
    frames, pos, final, heading, length = build_track(camera)
    bv, bt, tv, tt, engine, break_point, tip_center = build_plane(length)
    np.savez(CACHE / "crash_plane.npz", body=bv, body_tint=bt, tip=tv, tip_tint=tt)
    # La pièce arrachée est centrée sur elle-même, pour tourner autour de son milieu.
    centre_local = tv.reshape(-1, 3).mean(axis=0)
    tips = tip_flight(frames, 0.0)
    hit = next(i for i, fr in enumerate(frames) if fr["t"] >= CREST_T)
    anchor = frames[hit]
    a_pos = np.array(anchor["pos"]) + np.array(anchor["f"]) * centre_local[0] + np.array(anchor["s"]) * centre_local[1] + np.array(anchor["u"]) * centre_local[2]
    shift = a_pos - np.array(anchor["pos"])
    for tip in tips:
        if tip:
            tip["pos"] = (np.array(tip["pos"]) + shift).tolist()

    hmap = np.load(CACHE / "hmap.npy")
    clearance = []
    for i, fr in enumerate(frames):
        x, y, z = fr["pos"]
        ix, iz = int(x), int(z)
        if 0 <= ix < hmap.shape[0] and 0 <= iz < hmap.shape[1]:
            clearance.append(round(y - float(hmap[ix, iz]), 1))
    print("longueur", round(length, 1), "final", np.round(final, 1).tolist(), "cap", np.round(heading, 3).tolist())
    print("marge minimale au-dessus du terrain", min(clearance) if clearance else None)

    def local(fr, p):
        return (np.array(fr["pos"]) + np.array(fr["f"]) * p[0] + np.array(fr["s"]) * p[1] + np.array(fr["u"]) * p[2]).tolist()

    for fr in frames:
        x, y, d = camera.project(fr["pos"])
        fr["screen"] = [float(x), float(y), float(d)]
        fr["engine"] = local(fr, engine)
        fr["break"] = local(fr, break_point)
        fr["tail"] = local(fr, (-length / 2, 0, 3))
    crest = np.array(frames[hit]["pos"]) - np.array(frames[hit]["u"]) * 6
    ground_hit = next(i for i, fr in enumerate(frames) if fr["t"] >= TOUCH_T)
    data = {
        "fps": FPS, "start": START, "impact": IMPACT, "crest_t": CREST_T, "touch_t": TOUCH_T,
        "crest": crest.tolist(), "touch": frames[ground_hit]["pos"], "length": length,
        "frames": frames, "tips": tips, "local_tip_center": centre_local.tolist(),
    }
    out = ASSETS / "fx/crash"
    out.mkdir(parents=True, exist_ok=True)
    (out / "track.json").write_text(json.dumps(data), encoding="utf-8")
    print("images", len(frames), "corps", len(bv), "quads, bout d'aile", len(tv), "quads")

    if opts.check:
        base = Image.open(ASSETS / "plates/mid_night.png").convert("RGBA")
        canvas = Image.new("RGBA", base.size, (10, 12, 26, 255))
        canvas.alpha_composite(base)
        draw = ImageDraw.Draw(canvas)
        for i, fr in enumerate(frames):
            x, y, d = fr["screen"]
            draw.ellipse((x - 3, y - 3, x + 3, y + 3), fill=(255, 150, 50, 255))
            if i % 30 == 0:
                half = 0.5 * 68 * camera.focal / d
                sx, sy, _ = camera.project(np.array(fr["pos"]) + np.array(fr["s"]) * 34)
                ex, ey, _ = camera.project(np.array(fr["pos"]) - np.array(fr["s"]) * 34)
                draw.line((sx, sy, ex, ey), fill=(255, 255, 255, 255), width=3)
                draw.text((x + 8, y - 8), f"{fr['t']:.1f}s {2 * half:.0f}px", fill=(255, 255, 0, 255))
        canvas.convert("RGB").resize((PLATE_W // 2, PLATE_H // 2)).save(CACHE / "crash_check.png")


if __name__ == "__main__":
    main()
