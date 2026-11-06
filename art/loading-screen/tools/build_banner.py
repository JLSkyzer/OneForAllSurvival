"""Reconstruit en voxels le monde de la bannière Lost Island, par calque depuis sa caméra.

Toutes les coordonnées « écran » sont celles de la bannière ramenée en 1920x1080.
L'image n'a pas de vraie grille de blocs : les positions sont fidèles depuis cet
angle, mais les cubes sont une réinterprétation.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy.interpolate import griddata
from scipy.ndimage import distance_transform_edt, gaussian_filter

from build_world import ASH, BRICK, BURNT, D, DARKLEAF, DIRT, FAR, FG, GLASS, GRASS, H, HULL, IRON, LEAF, LOG, MID, MOSSY, PAINT, PALM, PLANK, SAND, SEA, STONE, W, World
from common import CACHE, PLATE_H, PLATE_W, ROOT, Camera, fbm, smoothstep

BANNER = ROOT.parent.parent / "docs/assets/lost-island-banniere.png"
LEDGE_Y = 90
OX, OY = (PLATE_W - 1920) / 2, (PLATE_H - 1080) / 2

COAST = [(640, 470), (570, 505), (530, 560), (505, 625), (440, 660), (340, 695), (268, 740), (300, 800), (335, 870), (295, 930), (200, 950), (90, 985), (0, 1050), (-900, 1500), (3800, 3400), (3800, -400), (1200, 410)]
SAND_ZONE = [(505, 625), (440, 660), (340, 695), (268, 740), (300, 800), (335, 870), (295, 930), (400, 905), (455, 820), (480, 740), (505, 680)]
LEDGE = [(830, 1090), (940, 965), (1000, 880), (1180, 812), (1350, 805), (1500, 735), (1960, 620), (1960, 1090)]

# Points de hauteur : (décalage x depuis la caméra, z, y).
ANCHORS = [
    (-26, 50, 62), (0, 50, 60), (30, 50, 66), (-60, 100, 48), (-20, 110, 45), (22, 110, 45), (62, 92, 70),
    (-70, 200, 46), (-62, 280, 76), (-30, 322, 92), (10, 345, 95), (-112, 262, 52), (-132, 212, 38),
    (40, 252, 50), (46, 282, 60), (52, 302, 68),
    (30, 410, 94), (92, 402, 102), (122, 300, 102), (102, 222, 74), (92, 160, 68),
    (60, 60, 82), (120, 60, 106), (75, 22, 84), (110, 22, 96), (160, 100, 126), (142, 240, 122), (-40, 20, 76), (-80, 40, 58),
    (-175, 345, 34), (-160, 260, 33), (-120, 480, 90), (40, 520, 96), (190, 460, 140),
]

# Canopées : (x écran, y écran du sommet, profondeur le long du rayon, rayon).
CANOPIES = [
    (640, 465, 330, 13), (730, 430, 320, 12), (810, 395, 330, 14), (880, 380, 340, 13), (950, 370, 350, 12), (580, 510, 300, 11), (540, 530, 300, 9),
    (660, 540, 250, 12), (760, 500, 250, 13), (850, 460, 270, 12), (610, 620, 220, 10), (690, 620, 210, 11), (790, 590, 210, 10), (560, 620, 240, 9), (640, 720, 170, 9),
    (90, 910, 82, 6), (150, 895, 82, 5), (215, 900, 82, 6), (280, 890, 84, 5), (350, 895, 84, 6), (415, 900, 84, 5), (480, 905, 84, 6), (550, 930, 80, 5), (40, 985, 62, 4), (120, 980, 62, 5), (200, 985, 62, 4), (280, 1000, 60, 5), (360, 990, 60, 4), (440, 1010, 60, 5), (520, 1010, 56, 4), (600, 1020, 54, 4), (680, 1040, 50, 4), (760, 1050, 50, 3),
    (1135, 815, 110, 6), (800, 990, 72, 4), (880, 965, 72, 4), (725, 1005, 72, 3), (950, 925, 72, 3), (985, 860, 100, 5), (1220, 680, 120, 8), (1140, 680, 150, 6), (1250, 560, 190, 9), (1340, 500, 200, 10), (1430, 410, 170, 8),
    (1400, 530, 60, 3.4), (1580, 400, 70, 3), (1700, 350, 60, 3.5), (1640, 160, 70, 4), (1800, 170, 60, 4), (1610, 215, 80, 4), (1880, 20, 50, 4), (1740, 110, 60, 3), (1520, 260, 100, 5), (1450, 330, 120, 6), (1830, 330, 55, 3), (1900, 300, 50, 3),
    (1120, 330, 340, 8), (1260, 320, 340, 8), (1370, 310, 300, 8),
]

# Palmiers de la plage : (x écran, y écran du sommet).
PALMS = [(470, 570), (410, 575), (380, 625), (320, 650), (345, 720), (385, 690), (545, 545)]

# Feux : (x écran, y écran, puissance), posés sur la première surface rencontrée.
FIRES = [(858, 838, 9), (950, 748, 7), (975, 738, 6), (925, 885, 3), (795, 880, 5), (880, 812, 4), (775, 770, 4), (1000, 790, 4), (745, 915, 3), (1022, 665, 6), (1028, 590, 3), (1132, 592, 7), (1172, 382, 8), (1215, 385, 5), (1090, 355, 4), (1290, 385, 3)]

FUSELAGE = ((715, 935), (1012, 702))  # du nez à la queue
WRECK_ZONE = [(695, 965), (690, 860), (745, 790), (740, 725), (800, 735), (872, 788), (960, 728), (1000, 695), (1045, 645), (1062, 690), (1012, 762), (1090, 785), (1080, 806), (992, 795), (955, 870), (840, 960), (760, 968)]
WRECK_Y = 41


def out_rays(camera, sx, sy):
    return camera.rays(np.asarray(sx, np.float64) + OX, np.asarray(sy, np.float64) + OY)


def to_plane(camera, sx, sy, level):
    d = out_rays(camera, np.array([sx]), np.array([sy]))[0]
    return camera.pos + d * (level - camera.pos[1]) / d[1]


def project_grid(camera, x, y, z):
    point = np.stack([x, y, z], axis=-1).astype(np.float64)
    sx, sy, depth = camera.project(point)
    return sx - OX, sy - OY, depth


def polygon_lookup(points, sx, sy, depth, outside=False):
    margin = 2500
    canvas = Image.new("L", (1920 + margin * 2, 1080 + margin * 2), 0)
    ImageDraw.Draw(canvas).polygon([(x + margin, y + margin) for x, y in points], fill=255)
    mask = np.asarray(canvas) > 0
    ix, iy = (sx + margin).astype(np.int64), (sy + margin).astype(np.int64)
    inside = (ix >= 0) & (iy >= 0) & (ix < mask.shape[1]) & (iy < mask.shape[0])
    out = np.full(sx.shape, outside)
    out[inside] = mask[iy[inside], ix[inside]]
    return out & (depth > 1)


def build_terrain(camera):
    x, z = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(D, dtype=np.float32), indexing="ij")
    sx, sy, depth = project_grid(camera, x, np.full_like(x, SEA + 1.0), z)
    land = polygon_lookup(COAST, sx, sy, depth, outside=True) | (depth <= 1)
    land[:, :] = land & (x > 40)
    d = distance_transform_edt(land) - distance_transform_edt(~land)
    # Côte découpée en dents, comme l'écume en zigzag de la bannière.
    d = d + 3.5 * fbm((W, D), 2.2, 19, octaves=2)
    land = d > 0
    sand = polygon_lookup(SAND_ZONE, sx, sy, depth)
    sand = gaussian_filter(sand.astype(np.float32), 1.5) > 0.5

    # Le relief passe par les points relevés ; la côte le ramène au niveau de la mer.
    cam_x = camera.pos[0]
    points = [(cam_x + a, b) for a, b, _ in ANCHORS]
    values = [c for _, _, c in ANCHORS]
    coast_cells = np.argwhere((d > 0) & (d < 2))[::7]
    for cx, cz in coast_cells:
        points.append((cx, cz))
        values.append(SEA + 0.4)
    h = griddata(np.array(points), np.array(values, np.float32), (x, z), method="linear")
    nearest = griddata(np.array(points), np.array(values, np.float32), (x, z), method="nearest")
    h = np.where(np.isfinite(h), h, nearest).astype(np.float32)
    h = gaussian_filter(h, 3.0)
    h += 2.6 * fbm((W, D), 7, 13, octaves=4) * smoothstep(4, 30, d)

    # Le replat de l'épave.
    wreck = to_plane(camera, 865, 830, WRECK_Y)
    r = np.hypot(x - wreck[0], (z - wreck[2]) * 1.2)
    flat = smoothstep(58, 30, r)
    h = h * (1 - flat) + (WRECK_Y - 1) * flat
    # Sous la silhouette de l'avion, le sol reste bas : aucune aile ne doit s'enterrer.
    fx, fy, fd = project_grid(camera, x, np.full_like(x, WRECK_Y + 2.0), z)
    under = polygon_lookup(WRECK_ZONE + [], fx, fy, fd)
    under = gaussian_filter(under.astype(np.float32), 2.0)
    h = h * (1 - np.clip(under * 2, 0, 1)) + np.minimum(h, WRECK_Y - 1) * np.clip(under * 2, 0, 1)

    # La plage reste basse et douce.
    beach = gaussian_filter(sand.astype(np.float32), 3.0)
    h = h * (1 - beach) + (SEA + 0.6 + np.clip(d, 0, 30) * 0.05) * beach
    # Aucun relief ne doit dépasser le replat des survivants dans leur dos ou juste devant eux.
    near_cam = smoothstep(46, 22, np.hypot(x - cam_x, z)) * (1 - smoothstep(60, 95, x - cam_x))
    h = h * (1 - near_cam) + np.minimum(h, LEDGE_Y - 5) * near_cam
    h = np.where(d <= 0, SEA - 3, h)
    hmap = np.floor(h).astype(np.int32)

    # Sillon d'escaliers et ruines qui remontent de l'épave vers le temple.
    ravine_a = np.array([wreck[0], wreck[2]])
    ravine_b = np.array([cam_x + 52, 305.0])
    axis = ravine_b - ravine_a
    length = np.linalg.norm(axis)
    axis /= length
    along = (x - ravine_a[0]) * axis[0] + (z - ravine_a[1]) * axis[1]
    across = (x - ravine_a[0]) * -axis[1] + (z - ravine_a[1]) * axis[0]
    ravine = smoothstep(17, 7, np.abs(across + 4 * fbm((W, D), 14, 18))) * smoothstep(-10, 25, along) * smoothstep(length + 12, length - 8, along)
    return hmap, d.astype(np.float32), sand, ravine, along


def fill_terrain(world, hmap, d, sand, ravine, along, wreck_flat, rng):
    grad = np.hypot(*np.gradient(gaussian_filter(hmap.astype(np.float32), 1.0)))
    for x in range(W):
        for z in range(D):
            top = hmap[x, z]
            if top < SEA:
                continue
            layer = FAR if z >= 380 else MID
            ys = np.arange(SEA - 2, top + 1)
            column = np.full(len(ys), DIRT, dtype=np.uint8)
            column[: max(0, len(ys) - 4)] = STONE
            r = ravine[x, z]
            if sand[x, z]:
                column[:] = SAND
            elif wreck_flat[x, z] > 0.6:
                column[-1] = BURNT if rng.random() < 0.6 else ASH
            elif r > 0.45:
                # Marches en bois et pierre : la lecture « escalier » du ravin vient de là.
                step = int((along[x, z] + 3 * rng.random()) / 5)
                column[-1] = PLANK if step % 2 == 0 else (BRICK if rng.random() < 0.7 else ASH)
                # Gravats : deux ou trois blocs en désordre, pour casser les lignes du ravin.
                if rng.random() < 0.10:
                    extra = int(rng.integers(1, 3))
                    material = rng.choice([PLANK, BRICK, MOSSY, ASH])
                    world.mat[x, top + 1:top + 1 + extra, z] = material
                    world.layer[x, top + 1:top + 1 + extra, z] = layer
            elif grad[x, z] > 1.5:
                column[-1] = STONE
            else:
                column[-1] = GRASS
            world.mat[x, ys, z] = column
            world.layer[x, ys, z] = layer


def ground_at(hmap, x, z):
    ix, iz = int(np.clip(x, 0, W - 1)), int(np.clip(z, 0, D - 1))
    return int(hmap[ix, iz])


def plant_canopies(world, camera, hmap, rng):
    for sx, sy, depth, radius in CANOPIES:
        d = out_rays(camera, np.array([sx]), np.array([sy]))[0]
        top = camera.pos + d * depth
        x, y, z = top
        if not (0 <= x < W and 0 <= z < D):
            continue
        layer = FAR if z >= 380 else MID
        ground = ground_at(hmap, x, z)
        leaf = LEAF if rng.random() < 0.6 else DARKLEAF
        cy = y - radius * 0.55
        world.blob(x, cy, z, radius, radius * 0.62, radius, leaf, layer, rng)
        for _ in range(3):
            ox, oz = rng.uniform(-radius, radius, 2) * 0.8
            r2 = radius * rng.uniform(0.4, 0.65)
            world.blob(x + ox, cy - radius * rng.uniform(0.2, 0.8), z + oz, r2, r2 * 0.6, r2, leaf, layer, rng)
        for yy in range(ground + 1, int(cy)):
            world.put(int(x), yy, int(z), LOG, layer)


def fill_forest(world, camera, hmap, d, sand, ravine, wreck_flat, rng):
    """Remplit la jungle entre les canopées relevées, sans masquer ce qui doit se voir."""
    zones = [
        ([(510, 620), (590, 480), (780, 400), (1010, 380), (1030, 500), (980, 680), (850, 700), (700, 730), (620, 880), (500, 900), (470, 760)], 0.05),
        ([(690, 760), (1060, 620), (1110, 790), (960, 900), (860, 980), (680, 980)], 0.10),
        ([(1190, 640), (1300, 420), (1500, 400), (1530, 720), (1350, 790), (1180, 790), (1120, 790)], 0.05),
        ([(0, 1100), (60, 960), (300, 900), (520, 900), (640, 960), (800, 1000), (830, 1100)], 0.07),
        ([(1180, 420), (1250, 280), (1540, 160), (1950, -60), (1950, 500), (1550, 560), (1300, 520)], 0.10),
        ([(860, 700), (1010, 480), (1060, 540), (1040, 620), (960, 720)], 0.10),
    ]
    x, z = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(D, dtype=np.float32), indexing="ij")
    y = hmap.astype(np.float32) + 14
    sx, sy, depth = project_grid(camera, x, y, z)
    occupied = np.zeros((W, D), dtype=bool)
    count = 0
    wreck_z = to_plane(camera, 865, 830, WRECK_Y)[2]
    for polygon, chance in zones:
        mask = polygon_lookup(polygon, sx, sy, depth) & (d > 12) & ~sand & (ravine < 0.3)
        cells = np.argwhere(mask)
        rng.shuffle(cells)
        for cx, cz in cells:
            if occupied[max(0, cx - 4):cx + 5, max(0, cz - 4):cz + 5].any() or rng.random() > chance:
                continue
            # Aucun arbre ne se dresse devant l'avion : sa silhouette doit rester entière.
            gx, gy, _ = camera.project((float(cx), float(hmap[cx, cz]), float(cz)))
            if polygon_test(gx - OX, gy - OY):
                continue
            if cz < wreck_z + 25:
                hides = False
                for level in (0, 7, 14, 21):
                    px, py, _ = camera.project((float(cx), float(hmap[cx, cz] + level), float(cz)))
                    px, py = px - OX, py - OY
                    for dx in (-50, 0, 50):
                        hides = hides or polygon_test(px + dx, py)
                if hides:
                    continue
            world.jungle_tree(int(cx), int(hmap[cx, cz]) + 1, int(cz), int(rng.integers(9, 18)), FAR if cz >= 380 else MID, rng)
            occupied[max(0, cx - 5):cx + 6, max(0, cz - 5):cz + 6] = True
            count += 1
    print("arbres de remplissage", count)


def plant_palms(world, camera, hmap, rng):
    for sx, sy in PALMS:
        # Le sommet d'un palmier se situe une dizaine de blocs au-dessus de la plage.
        p = to_plane(camera, sx, sy, SEA + 12)
        if 0 <= p[0] < W and 0 <= p[2] < D:
            base = max(ground_at(hmap, p[0], p[2]), SEA)
            world.palm_tree(int(p[0]), base + 1, int(p[2]), max(6, SEA + 12 - base), MID, rng)


def build_temple(world, camera, hmap, rng):
    """Ruines en terrasses, au bout du ravin, comme sur la bannière."""
    centre = camera.pos + out_rays(camera, np.array([1160]), np.array([445]))[0] * 300
    cx, cz = int(centre[0]), int(centre[2])
    ground = ground_at(hmap, cx, cz) + 1
    half, level = 32, 0
    while half > 6:
        for x in range(cx - half, cx + half + 1):
            for z in range(cz - half, cz + half + 1):
                edge = max(abs(x - cx), abs(z - cz))
                if edge == half and rng.random() < 0.18:
                    continue
                material = MOSSY if rng.random() < 0.3 else (PLANK if rng.random() < 0.18 else BRICK)
                for y in range(ground + level, ground + level + 3):
                    if edge >= half - 1 or y == ground + level + 2:
                        world.put(x, y, z, material, MID)
        level += 3
        half -= 3
    top = ground + level
    # Une part du calque : colonnes et pans de mur effondrés sur les flancs.
    for _ in range(26):
        x, z = cx + int(rng.integers(-34, 35)), cz + int(rng.integers(-34, 35))
        for y in range(ground, ground + int(rng.integers(6, 30))):
            world.put(x, y, z, BRICK, MID)
            world.put(x + 1, y, z, BRICK, MID)
    return cx, top, cz


def build_wreck(world, camera, hmap, rng):
    """Avion en vrais cubes : fuselage, ailes et dérive, posés sur les repères de la bannière."""
    def on_ground(sx, sy):
        p = to_plane(camera, sx, sy, WRECK_Y + 1)
        return np.array([p[0], p[2]])

    nose, tail = on_ground(715, 935), on_ground(1012, 702)
    axis = tail - nose
    length = np.linalg.norm(axis)
    axis /= length
    side = np.array([-axis[1], axis[0]])
    noise = fbm((512, 512), 5, 44, octaves=3) * 0.5 + 0.5

    def scorch(u, v):
        return noise[int(u * 3) % 512, int(v * 3 + 256) % 512]

    def put_at(u, v, y, material):
        p = nose + axis * u + side * v
        world.put(int(p[0]), int(y), int(p[1]), material, MID)

    # Fuselage : un cylindre de cubes, peinture bleue en bande et hublots.
    radius = 4.8
    for u in np.arange(0, length, 0.45):
        r = radius * min(1.0, math.sqrt(max(0.04, u / 9.0)))  # nez arrondi
        r *= 1 - 0.55 * max(0.0, (u - length * 0.78) / (length * 0.22))  # queue effilée
        gap = abs(u - length * 0.40) < 1.6  # cassure au milieu
        if gap:
            continue
        for ang in np.arange(0, 2 * math.pi, 0.1):
            v, w = r * math.cos(ang), r * math.sin(ang) + r
            burn = scorch(u, ang * 12)
            material = HULL
            if r * 0.15 < w - r < r * 0.42:
                material = PAINT
            elif w - r > r * 0.5 and int(u) % 3 == 0 and abs(v) > r * 0.35 and w - r < r * 0.8:
                material = GLASS
            near_break = abs(u - length * 0.40) < 7
            if burn < 0.30 + (0.28 if near_break else 0):
                material = BURNT
            put_at(u, v, WRECK_Y + w, material)
    # Aile gauche, large et décomposée en panneaux, avec ses plaques bleues.
    root_l = axis * 0  # repère relatif
    def wing(u0, sign, span, chord_root, chord_tip, sweep, lift, blue):
        for v in np.arange(radius - 1, span, 0.45):
            t = v / span
            chord = chord_root + (chord_tip - chord_root) * t
            for du in np.arange(-chord / 2, chord / 2, 0.45):
                u = u0 + du - sweep * t
                panel = (int(du // 3) + int(v // 4)) % 2
                y = WRECK_Y + 2 + lift * t + panel
                burn = scorch(u + 40, v + sign * 30)
                material = HULL
                if blue and 0.18 < t and (int(du // 3) + int(v // 5)) % 3 == 0:
                    material = PAINT
                elif panel and int(v) % 4 == 0:
                    material = IRON
                if burn < 0.27:
                    material = BURNT
                put_at(u, sign * v, y, material)
    # Côtés relevés sur l'image : côté -v vers le haut à gauche, côté +v vers la droite.
    left = on_ground(760, 745)
    right = on_ground(1085, 795)
    span_l = abs((left - nose) @ side)
    span_r = abs((right - nose) @ side)
    sign_l = 1.0 if (left - nose) @ side > 0 else -1.0
    root_u = length * 0.40
    wing(root_u, sign_l, span_l, 19, 7, ((root_u - (left - nose) @ axis)) , 2.5, True)
    wing(root_u + 2, -sign_l, span_r, 17, 5, root_u + 2 - (right - nose) @ axis, 3.5, False)
    # Empennage : deux petits plans et la dérive bleue dressée.
    for sign in (-1.0, 1.0):
        for v in np.arange(0, 9, 0.45):
            for du in np.arange(-3.5 + v * 0.2, 3.5 - v * 0.2, 0.45):
                put_at(length - 4 + du - v * 0.3, sign * v, WRECK_Y + 4 + v * 0.05, HULL)
    fin_height = 22
    for i in np.arange(0, fin_height, 0.45):
        chord = 7.5 - i * 0.2
        for du in np.arange(-chord / 2, chord / 2, 0.45):
            put_at(length - 2 + du + i * 0.25, 0, WRECK_Y + 5 + i, PAINT if i > 4 else HULL)
            put_at(length - 2 + du + i * 0.25, 0.6, WRECK_Y + 5 + i, PAINT if i > 4 else HULL)
    # Débris projetés autour de l'épave.
    for _ in range(90):
        u, v = rng.uniform(-14, length + 14), rng.normal(0, 22)
        material = rng.choice([HULL, HULL, IRON, BURNT, PAINT])
        for k in range(int(rng.integers(1, 5))):
            put_at(u + rng.integers(-1, 2), v + rng.integers(-1, 2), WRECK_Y + rng.integers(0, 2), material)
    print("avion : longueur", round(float(length), 1), "envergures", round(float(span_l), 1), round(float(span_r), 1))


_wreck_mask = None


def polygon_test(sx, sy):
    global _wreck_mask
    if _wreck_mask is None:
        canvas = Image.new("L", (1920, 1080), 0)
        ImageDraw.Draw(canvas).polygon(WRECK_ZONE, fill=255)
        _wreck_mask = np.asarray(canvas) > 0
    return 0 <= sx < 1920 and 0 <= sy < 1080 and _wreck_mask[int(sy), int(sx)]


def build_ledge(world, camera, hmap, rng):
    x, z = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(D, dtype=np.float32), indexing="ij")
    sx, sy, depth = project_grid(camera, x, np.full_like(x, LEDGE_Y + 1.0), z)
    ledge = polygon_lookup(LEDGE, sx, sy, depth)
    near = (np.hypot(x - camera.pos[0], z) < 34) & (z > -6)
    behind = (z >= -8) & (z < 2) & (np.abs(x - camera.pos[0]) < 14)
    mask = (ledge & near) | behind
    # Le sol du replat est un maillage fin (voir export_mesh) ; ici, le massif qui le porte.
    for cx, cz in np.argwhere(mask):
        ys = np.arange(max(SEA, hmap[cx, cz] + 1), LEDGE_Y - 5)
        if len(ys):
            world.mat[cx, ys, cz] = STONE
            world.layer[cx, ys, cz] = MID
    # Tronc au bord droit de l'image.
    trunk = to_plane(camera, 1985, 860, LEDGE_Y + 1)
    for yy in range(LEDGE_Y + 1, LEDGE_Y + 4):
        world.put(int(trunk[0]), yy, int(trunk[2]), LOG, FG)
    return mask


def carve_view(world, camera):
    """Retire les voxels entre la caméra et la silhouette de l'avion."""
    cells = np.argwhere(world.mat > 0)
    point = cells.astype(np.float64) + 0.5
    sx, sy, depth = camera.project(point)
    sx, sy = sx - OX, sy - OY
    polygon_test(0, 0)
    inside = (sx >= 0) & (sx < 1920) & (sy >= 0) & (sy < 1080)
    hit = np.zeros(len(cells), dtype=bool)
    hit[inside] = _wreck_mask[sy[inside].astype(np.int32), sx[inside].astype(np.int32)]
    # Profondeur du plan de l'épave le long de chaque rayon.
    v = point - camera.pos
    surface = (WRECK_Y + 7 - camera.pos[1]) / np.minimum(v[:, 1] / np.linalg.norm(v, axis=1), -1e-3)
    front = hit & (np.linalg.norm(v, axis=1) < surface)
    gone = cells[front]
    world.mat[gone[:, 0], gone[:, 1], gone[:, 2]] = 0
    world.layer[gone[:, 0], gone[:, 1], gone[:, 2]] = 0
    print("voxels retires devant l'avion", len(gone))


def first_hit(camera, world_mat, sx, sy):
    d = out_rays(camera, np.array([sx]), np.array([sy]))[0]
    for t in np.arange(8, 700, 0.5):
        p = camera.pos + d * t
        ix, iy, iz = int(p[0]), int(p[1]), int(p[2])
        if 0 <= ix < W and 0 <= iy < H and 0 <= iz < D and world_mat[ix, iy, iz]:
            return p
    return None


def main():
    rng = np.random.default_rng(2026)
    camera = Camera()
    world = World()
    hmap, d, sand, ravine, along = build_terrain(camera)
    wreck = to_plane(camera, 865, 830, WRECK_Y)
    x, z = np.meshgrid(np.arange(W, dtype=np.float32), np.arange(D, dtype=np.float32), indexing="ij")
    wreck_flat = smoothstep(58, 30, np.hypot(x - wreck[0], (z - wreck[2]) * 1.2))
    print("relief", hmap.min(), hmap.max())
    fill_terrain(world, hmap, d, sand, ravine, along, wreck_flat, rng)
    plant_canopies(world, camera, hmap, rng)
    fill_forest(world, camera, hmap, d, sand, ravine, wreck_flat, rng)
    plant_palms(world, camera, hmap, rng)
    temple = build_temple(world, camera, hmap, rng)
    build_ledge(world, camera, hmap, rng)
    carve_view(world, camera)
    np.save(CACHE / "mat_pre.npy", world.mat)
    np.save(CACHE / "layer_pre.npy", world.layer)
    build_wreck(world, camera, hmap, rng)

    fires = []
    for sx, sy, power in FIRES:
        p = first_hit(camera, world.mat, sx, sy)
        if p is None:
            print("feu sans surface", sx, sy)
            continue
        fires.append([float(p[0]), float(p[1]) + 1.5, float(p[2]), float(power)])
    (CACHE / "fires.json").write_text(json.dumps(fires))
    np.save(CACHE / "mat.npy", world.mat)
    np.save(CACHE / "layer.npy", world.layer)
    np.save(CACHE / "hmap.npy", hmap)
    np.save(CACHE / "shore.npy", d)
    np.save(CACHE / "topmap.npy", hmap)
    white = to_plane(camera, 1600, 885, LEDGE_Y + 1)
    black = to_plane(camera, 1790, 830, LEDGE_Y + 1)
    (CACHE / "world.json").write_text(json.dumps({
        "W": W, "H": H, "D": D, "SEA": SEA, "banner": True,
        "wreck": [float(wreck[0]), float(wreck[2])], "wreck_angle": 0.0,
        "temple": [temple[0], temple[1], temple[2]],
        "people": {"white": [float(white[0]), LEDGE_Y + 1, float(white[2])], "black": [float(black[0]), LEDGE_Y + 1, float(black[2])]},
    }))
    print("voxels", int((world.mat > 0).sum()), "feux", len(fires), "personnes", white.round(1).tolist(), black.round(1).tolist())


if __name__ == "__main__":
    main()
