"""Construit l'île en voxels : relief, jungle, épave, débris et temple.

Sorties dans le cache : mat.npy (matériau), layer.npy (calque), hmap.npy, shore.npy, world.json.
"""
import json
import math

import numpy as np
from scipy.ndimage import gaussian_filter

from common import CACHE, Camera, fbm, smoothstep

W, H, D = 480, 150, 560
SEA = 30

GRASS, DIRT, SAND, STONE, LEAF, LOG, PALM, HULL, IRON, BURNT, PAINT, GLASS, BRICK, MOSSY, ASH, SANDSTONE, PLANK, DARKLEAF = range(1, 19)
FG, MID, FAR = 1, 2, 3

WRECK = np.array([192.0, 138.0])
WRECK_ANGLE = math.radians(-20.0)
S = 1.3  # échelle de l'épave
TEMPLE = (62, 345)


def rng_for(seed):
    return np.random.default_rng(seed)


def build_terrain():
    x, z = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(D, dtype=np.float32), indexing="ij")
    shore = 182 - 0.30 * z + 0.9 * np.maximum(0, z - 300) + 0.55 * np.maximum(0, z - 190) + 34 * np.exp(-((z - 130) / 62) ** 2) - 18 * np.exp(-((z - 300) / 50) ** 2)
    d = x - shore + 20 * fbm((W, D), 36, 11) + 6 * fbm((W, D), 9, 12)
    d = d - np.maximum(0, z - 520) * 3.0
    # Îlot du temple, séparé de la côte par un chenal.
    islet = np.hypot(x - TEMPLE[0], z - TEMPLE[1])
    d = np.maximum(d, (37 - islet) * 1.3 + 5 * fbm((W, D), 9, 17))

    ridge = fbm((W, D), 42, 13, octaves=5)
    h = SEA + 0.6 + np.clip(d, -40, 16) * 0.16
    h = h + smoothstep(14, 170, d) * 22
    h = h + (ridge * 0.5 + 0.5) * 34 * smoothstep(50, 210, d)
    h = h + smoothstep(280, 460, x) * 36 * smoothstep(40, 200, z)

    # Volcan au loin, avec un cratère.
    r = np.hypot(x - 392, z - 480)
    h = np.maximum(h, SEA + 96 - r * 0.72 + 7 * fbm((W, D), 8, 14))
    h = np.where(r < 11, np.minimum(h, SEA + 84), h)

    # Clairière d'impact et sillon laissé par l'avion.
    ax, az = math.cos(WRECK_ANGLE), math.sin(WRECK_ANGLE)
    u = (x - WRECK[0]) * ax + (z - WRECK[1]) * az
    v = -(x - WRECK[0]) * az + (z - WRECK[1]) * ax
    clearing = np.exp(-((u / 72) ** 2 + (v / 40) ** 2) ** 1.6) * smoothstep(-2, 10, d)
    # Le relief s'efface devant la caméra pour dégager la vue.
    h_cut = smoothstep(150, 40, np.hypot(x - 250, z)) * 22 * smoothstep(30, 90, d)
    groove = smoothstep(17, 6, np.abs(v + 0.05 * u + 3 * fbm((W, D), 12, 15))) * smoothstep(-260, -60, u) * smoothstep(40, -20, u)
    flat_level = SEA + 3 + 0.02 * np.clip(u, -40, 90)
    h = (h - h_cut) * (1 - clearing) + flat_level * clearing
    h = h - groove * 2

    # Le temple repose sur un plateau.
    tr = np.hypot(x - TEMPLE[0], z - TEMPLE[1])
    h = h * (1 - smoothstep(30, 20, tr)) + (SEA + 6) * smoothstep(30, 20, tr)

    hmap = np.floor(h).astype(np.int32)
    burn = np.maximum(groove, clearing ** 3 * 0.9) + 0.25 * fbm((W, D), 5, 16)
    return hmap, d, burn, clearing, groove


def mound_field():
    """Promontoire du premier plan, d'où regardent les deux survivants."""
    x, z = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(D, dtype=np.float32), indexing="ij")
    wobble = 4.5 * fbm((W, D), 7, 21) + 2.0 * fbm((W, D), 2.5, 22)
    edge = 16 + 1.5 * (x - 250) + wobble
    inside = z < edge
    top = 69 + 0.05 * (x - 250) + 2.4 * fbm((W, D), 8, 23) - 0.02 * z
    return inside, np.floor(top).astype(np.int32)


class World:
    def __init__(self):
        self.mat = np.zeros((W, H, D), dtype=np.uint8)
        self.layer = np.zeros((W, H, D), dtype=np.uint8)

    def put(self, x, y, z, material, layer):
        if 0 <= x < W and 0 <= y < H and 0 <= z < D:
            self.mat[x, y, z] = material
            self.layer[x, y, z] = layer

    def layer_of(self, z):
        return FAR if z >= 330 else MID

    def blob(self, cx, cy, cz, rx, ry, rz, material, layer, rng, only_empty=True):
        x0, x1 = max(0, int(cx - rx - 1)), min(W, int(cx + rx + 2))
        y0, y1 = max(0, int(cy - ry - 1)), min(H, int(cy + ry + 2))
        z0, z1 = max(0, int(cz - rz - 1)), min(D, int(cz + rz + 2))
        if x0 >= x1 or y0 >= y1 or z0 >= z1:
            return
        gx, gy, gz = np.ogrid[x0:x1, y0:y1, z0:z1]
        dist = ((gx - cx) / rx) ** 2 + ((gy - cy) / ry) ** 2 + ((gz - cz) / rz) ** 2
        mask = dist < 1.0 + rng.uniform(-0.28, 0.14, dist.shape)
        if only_empty:
            mask &= self.mat[x0:x1, y0:y1, z0:z1] == 0
        self.mat[x0:x1, y0:y1, z0:z1][mask] = material
        self.layer[x0:x1, y0:y1, z0:z1][mask] = layer

    def jungle_tree(self, x, y, z, height, layer, rng):
        lean = rng.uniform(-0.05, 0.05, 2)
        for i in range(height):
            self.put(int(x + lean[0] * i), y + i, int(z + lean[1] * i), LOG, layer)
        leaf = LEAF if rng.random() < 0.6 else DARKLEAF
        top = y + height
        rx = rng.uniform(4.5, 7.5)
        self.blob(x, top, z, rx, rng.uniform(2.6, 3.8), rx, leaf, layer, rng)
        for _ in range(rng.integers(2, 5)):
            ox, oz = rng.uniform(-rx, rx, 2)
            oy = rng.uniform(-height * 0.45, -1)
            r2 = rng.uniform(2.5, 4.5)
            self.blob(x + ox, top + oy, z + oz, r2, r2 * 0.6, r2, leaf, layer, rng)

    def palm_tree(self, x, y, z, height, layer, rng):
        bx, bz = rng.uniform(-0.6, 0.6, 2)
        tx, tz = x, z
        for i in range(height):
            b = (i / height) ** 2 * 3
            tx, tz = x + bx * b, z + bz * b
            self.put(int(tx), y + i, int(tz), LOG, layer)
        top = y + height
        self.put(int(tx), top, int(tz), PALM, layer)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1), (1, -1), (-1, -1)):
            length = rng.integers(4, 7)
            for s in range(1, length + 1):
                drop = int(s * s * 0.11)
                self.put(int(tx + dx * s), top - drop, int(tz + dz * s), PALM, layer)
                if s > 2 and rng.random() < 0.4:
                    self.put(int(tx + dx * s), top - drop - 1, int(tz + dz * s), PALM, layer)


def fill_terrain(world, hmap, d, burn, rng):
    grad = np.hypot(*np.gradient(gaussian_filter(hmap.astype(np.float32), 1.0)))
    for x in range(W):
        for z in range(D):
            top = hmap[x, z]
            if top < SEA:
                continue
            layer = world.layer_of(z)
            depth = top - SEA + 2
            ys = np.arange(SEA - 2, top + 1)
            sand = d[x, z] < 15 + 2.5 * rng.random()
            column = np.full(depth + 1, STONE if grad[x, z] > 1.1 else DIRT, dtype=np.uint8)[: len(ys)]
            column[: max(0, len(ys) - 4)] = STONE
            if burn[x, z] > 0.62:
                surface = BURNT if rng.random() < 0.5 else ASH
            elif burn[x, z] > 0.42:
                surface = ASH
            elif sand:
                surface = SAND
                column[:] = SAND
            elif grad[x, z] > 1.35:
                surface = STONE
            else:
                surface = GRASS
            column[-1] = surface
            world.mat[x, ys, z] = column
            world.layer[x, ys, z] = layer


def visible_columns(camera, margin=40):
    """Masque grossier du champ de vision, pour ne pas planter d'arbres invisibles."""
    x, z = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(D, dtype=np.float32), indexing="ij")
    vx, vz = x - camera.pos[0], z - camera.pos[2]
    fx, fz = camera.forward[0], camera.forward[2]
    n = math.hypot(fx, fz)
    forward = (vx * fx + vz * fz) / n
    right = vx * camera.right[0] + vz * camera.right[2]
    return (forward > -5) & (np.abs(right) < forward * 0.9 + margin)


def plant_forest(world, hmap, d, burn, clearing, groove, mound, rng):
    camera = Camera()
    visible = visible_columns(camera)
    # Rectangle de l'épave à l'écran : aucun arbre ne doit se dresser devant elle.
    ax, az = math.cos(WRECK_ANGLE), math.sin(WRECK_ANGLE)
    corners = []
    for u, v, y in ((-62, -32, SEA + 34), (62, -32, SEA + 34), (-62, 32, SEA + 34), (62, 32, SEA + 34), (-62, -32, SEA), (62, -32, SEA), (-62, 32, SEA), (62, 32, SEA)):
        corners.append(camera.project((WRECK[0] + u * ax - v * az, y, WRECK[1] + u * az + v * ax)))
    box_left = min(c[0] for c in corners) - 30
    box_right = max(c[0] for c in corners) + 30
    box_top = min(c[1] for c in corners)
    box_bottom = max(c[1] for c in corners) + 20
    box_depth = camera.project((WRECK[0], SEA, WRECK[1]))[2] - 8
    density = fbm((W, D), 20, 31)
    occupied = np.zeros((W, D), dtype=bool)
    candidates = np.argwhere(visible & (hmap >= SEA + 1))
    rng.shuffle(candidates)
    placed = 0
    for x, z in candidates:
        depth = d[x, z]
        if mound[x, z] or occupied[max(0, x - 2):x + 3, max(0, z - 2):z + 3].any():
            continue
        if burn[x, z] > 0.42 or clearing[x, z] > 0.72 or groove[x, z] > 0.2 or z < 62:
            continue
        y = hmap[x, z] + 1
        layer = world.layer_of(z)
        if depth < 3 or math.hypot(x - TEMPLE[0], z - TEMPLE[1]) < 27:
            continue
        tx, ty, td = camera.project((float(x), float(y) + 22, float(z)))
        bx, by, _ = camera.project((float(x), float(y), float(z)))
        if td < box_depth and box_left < tx < box_right and ty < box_bottom and by > box_top:
            if by > box_bottom and depth > 22 and z > 110 and rng.random() < 0.04:
                world.blob(int(x), int(y) + 1, int(z), 3.6, 1.6, 3.6, LEAF if rng.random() < 0.5 else DARKLEAF, layer, rng)
            continue
        if depth < 19:
            if rng.random() < 0.03:
                world.palm_tree(int(x), int(y), int(z), int(rng.integers(6, 11)), layer, rng)
                occupied[max(0, x - 4):x + 5, max(0, z - 4):z + 5] = True
                placed += 1
            continue
        # Plus on s'éloigne, plus les arbres sont grands et serrés pour rester lisibles.
        chance = 0.055 + 0.07 * max(0.0, density[x, z])
        if z > 260:
            chance *= 0.8
        if rng.random() > chance:
            continue
        height = int(rng.integers(8, 17) + (z > 200) * 3)
        world.jungle_tree(int(x), int(y), int(z), height, layer, rng)
        occupied[max(0, x - 5):x + 6, max(0, z - 5):z + 6] = True
        placed += 1
    # Palmiers de la plage, placés à la main pour rythmer la côte.
    for z in (72, 88, 96, 118, 196, 214, 238, 262, 280):
        xs = np.where((d[:, z] > 6) & (d[:, z] < 13))[0]
        if len(xs):
            x = int(xs[len(xs) // 2])
            if clearing[x, z] < 0.3:
                world.palm_tree(x, int(hmap[x, z]) + 1, z, int(rng.integers(8, 13)), world.layer_of(z), rng)
    return placed


def build_mound(world, hmap, rng):
    inside, top = mound_field()
    mask = inside & (top > hmap)
    for x, z in np.argwhere(mask):
        y0, y1 = hmap[x, z] + 1, top[x, z]
        ys = np.arange(y0, y1 + 1)
        column = np.full(len(ys), STONE, dtype=np.uint8)
        column[-4:] = DIRT
        column[-1] = GRASS
        world.mat[x, ys, z] = column
        world.layer[x, ys, z] = FG
    # Quelques arbres et buissons encadrent l'image à droite.
    for x, z, height in ((282, 41, 15), (296, 40, 18), (307, 60, 16), (272, 24, 9)):
        if 0 <= x < W and z < D and mask[x, z]:
            world.jungle_tree(x, int(top[x, z]) + 1, z, height, FG, rng)
    for x, z in ((265, 36), (260, 23), (272, 42), (280, 25), (262, 30)):
        if mask[x, z]:
            world.blob(x, top[x, z] + 1.5, z, 2.6, 1.8, 2.6, LEAF, FG, rng)
    # Herbes et petites fougères : le sol du premier plan ne doit pas rester un tapis uni.
    for x, z in np.argwhere(mask):
        if rng.random() < 0.035 and not (249 < x < 258 and 9 < z < 16):
            world.put(int(x), int(top[x, z]) + 1, int(z), PALM if rng.random() < 0.6 else DARKLEAF, FG)
    return mask, top


def build_wreck(world, hmap, rng):
    ax, az = math.cos(WRECK_ANGLE), math.sin(WRECK_ANGLE)
    wx, wz = WRECK
    burn_noise = fbm((512, 512), 7, 41, octaves=3) * 0.5 + 0.5
    speck = rng.random((512, 512))

    def place(u, v, w, material, tilt=0.0, shift=(0.0, 0.0)):
        u, v, w = u * S, v * S, w * S
        x = wx + (u + shift[0]) * ax - (v + shift[1]) * az
        z = wz + (u + shift[0]) * az + (v + shift[1]) * ax
        ix, iz = int(round(x)), int(round(z))
        if not (0 <= ix < W and 0 <= iz < D):
            return
        ground = max(hmap[ix, iz], SEA)
        y = int(round(ground + 4.2 * S + w + tilt * u))
        world.put(ix, y, iz, material, MID)

    def hull_material(u, v, w, r):
        key = burn_noise[int(u * 4) % 512, int((v + w) * 4 + 256) % 512]
        char = smoothstep(14, 1, abs(u - (-4)))
        if key < 0.30 + 0.34 * float(char):
            return BURNT
        if -1.3 < w < -0.2:
            return PAINT
        if 0.8 < w < 1.8 and int(u) % 3 == 0 and abs(v) > r * 0.6:
            return GLASS
        return HULL

    # Fuselage cassé en deux : avant vers la plage, arrière dans le sillon.
    radius = 4.6
    for u in np.arange(-44, 31, 0.45):
        if -6.5 < u < -2.5:
            continue
        r = radius * (1.0 if u < 21 else math.sqrt(max(0.05, 1 - ((u - 21) / 10.5) ** 2)))
        if u < -30:
            r = radius * max(0.35, 1 - (-30 - u) / 22)
        tilt = -0.08 if u < -5 else 0.035
        shift = (0.0, 2.6) if u < -5 else (0.0, 0.0)
        for a in np.arange(0, 2 * math.pi, 0.12):
            v, w = r * math.cos(a), r * math.sin(a)
            # L'arrachement laisse des trous dans la coque près de la cassure.
            if abs(u + 4.5) < 6 and burn_noise[int(a * 30) % 512, int(u * 8) % 512] < 0.48:
                continue
            place(u, v, w, hull_material(u, v, w, r), tilt, shift)
    # Intérieur sombre visible à travers la cassure.
    for u in np.arange(-10, 3, 0.7):
        for v in np.arange(-3.4, 3.5, 0.8):
            for w in np.arange(-3.2, 3.2, 0.8):
                place(u, v, w, BURNT, -0.02, (0.0, 1.0))

    # Ailes : une entière, une arrachée et plantée au sol.
    for v in np.arange(4, 34, 0.5):
        chord = 13 - v * 0.26
        lift = 0.10 * v - 3.0
        for u in np.arange(-chord * 0.5, chord * 0.5, 0.5):
            key = burn_noise[int(v * 5) % 512, int(u * 5 + 200) % 512]
            place(u + 4 + v * -0.32, v, lift, BURNT if key < 0.3 else (IRON if key < 0.42 else HULL))
    for v in np.arange(4, 25, 0.5):
        chord = 13 - v * 0.26
        for u in np.arange(-chord * 0.5, chord * 0.5, 0.5):
            key = burn_noise[int(v * 5 + 90) % 512, int(u * 5) % 512]
            place(u + 15 + v * -0.32, -v - 7, -3.0 + 0.38 * v, BURNT if key < 0.38 else (IRON if key < 0.48 else HULL))

    # Empennage.
    for u in np.arange(-44, -32, 0.5):
        span = (u + 44) * 0.75
        for w in np.arange(0, span, 0.5):
            place(u + w * -0.35, 2.6, 3.2 + w, PAINT if w > span * 0.55 else HULL, -0.08)
        for v in np.arange(-span, span, 0.5):
            place(u, 2.6 + v, 2.5, HULL, -0.08)

    # Moteur tombé au sol.
    for u in np.arange(-5, 5, 0.5):
        for a in np.arange(0, 2 * math.pi, 0.25):
            place(u + 28, -22 + 2.4 * math.cos(a), -2.2 + 2.4 * math.sin(a), IRON if u > -3 else BURNT)

    # Débris projetés en éventail, valises et sièges.
    for i in range(120):
        spread = rng.normal(0, 0.75)
        along = rng.uniform(-170, 70)
        lateral = spread * (12 + 0.28 * abs(along))
        material = rng.choice([HULL, HULL, IRON, BURNT, PAINT, PLANK])
        size = rng.integers(1, 4)
        probe_x = int(wx + along * S * ax - lateral * S * az)
        probe_z = int(wz + along * S * az + lateral * S * ax)
        if not (0 <= probe_x < W and 0 <= probe_z < D) or hmap[probe_x, probe_z] < SEA + 1:
            continue
        for s in range(size * 2):
            place(along + rng.integers(-size, size + 1), lateral + rng.integers(-size, size + 1), -4.0 + rng.integers(0, size), material)


def build_temple(world, hmap, rng):
    cx, cz, base = TEMPLE[0], TEMPLE[1], 22
    ground = int(hmap[cx, cz])
    level = 0
    half = base
    while half > 4:
        for x in range(cx - half, cx + half + 1):
            for z in range(cz - half, cz + half + 1):
                edge = max(abs(x - cx), abs(z - cz)) == half
                erosion = rng.random()
                # Silhouette ébréchée : la pierre a été rongée par le temps.
                if edge and erosion < 0.12:
                    continue
                material = MOSSY if rng.random() < 0.42 else BRICK
                for y in range(ground + 1 + level, ground + 5 + level):
                    world.put(x, y, z, material, FAR)
        level += 4
        half -= 4
    # Sanctuaire et deux pylônes.
    top = ground + level + 1
    for x, z, height in ((cx - 4, cz, 11), (cx + 4, cz, 11)):
        for y in range(top, top + height):
            world.put(x, y, z, BRICK, FAR)
            world.put(x, y, z + 1, BRICK, FAR)
    for x in range(cx - 6, cx + 7):
        world.put(x, top + 11, cz, BRICK, FAR)
        world.put(x, top + 11, cz + 1, MOSSY, FAR)
    # Colonnes effondrées le long de l'allée qui mène à la mer.
    for i in range(2):
        z = cz - base - 3 - i * 5
        for x in (cx - 12, cx + 12):
            if 0 <= x < W and 0 <= z < D:
                height = int(rng.integers(3, 14))
                for y in range(hmap[x, z] + 1, hmap[x, z] + 1 + height):
                    world.put(x, y, z, BRICK, FAR)
    return top


def main():
    rng = rng_for(2026)
    world = World()
    hmap, d, burn, clearing, groove = build_terrain()
    print("relief ok", hmap.min(), hmap.max())
    fill_terrain(world, hmap, d, burn, rng)
    inside, top = mound_field()
    mound = inside & (top > hmap)
    trees = plant_forest(world, hmap, d, burn, clearing, groove, mound, rng)
    print("arbres", trees)
    build_mound(world, hmap, rng)
    temple_top = build_temple(world, hmap, rng)
    # Version sans épave : le décor tel qu'il est avant l'impact, dans l'ouverture.
    np.save(CACHE / "mat_pre.npy", world.mat)
    np.save(CACHE / "layer_pre.npy", world.layer)
    build_wreck(world, hmap, rng)

    np.save(CACHE / "mat.npy", world.mat)
    np.save(CACHE / "layer.npy", world.layer)
    np.save(CACHE / "hmap.npy", hmap)
    np.save(CACHE / "shore.npy", d.astype(np.float32))
    top_map = np.where(mound, top, hmap)
    np.save(CACHE / "topmap.npy", top_map)
    (CACHE / "world.json").write_text(json.dumps({
        "W": W, "H": H, "D": D, "SEA": SEA,
        "wreck": WRECK.tolist(), "wreck_angle": WRECK_ANGLE,
        "temple": [TEMPLE[0], temple_top, TEMPLE[1]],
    }))
    print("voxels", int((world.mat > 0).sum()), "epave ok, temple au sommet", temple_top)


if __name__ == "__main__":
    main()
