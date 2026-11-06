"""Convertit le monde voxel en maillages prêts pour Blender (faces visibles seulement).

Sorties dans le cache : mesh_{far,mid,wreck,fg}.npz, atlas.png, scene.json.
Repère Blender : (x, y, z) du monde devient (x, z, y).
"""
import json

import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import gaussian_filter

from build_banner import LEDGE, LEDGE_Y
from build_world import FAR, FG, MID
from common import CACHE, PLATE_H, PLATE_W, Camera, hex_rgb, linear_to_srgb, srgb_to_linear
from render_plates import MOON_UV, SUN_UV, build_atlas

ATLAS_FACE = 16

# axe normal, signe, axe U, axe V, indice de face (0 dessus, 1 côté, 2 dessous)
DIRECTIONS = [
    (0, 1, 2, 1, 1), (0, -1, 2, 1, 1),
    (1, 1, 0, 2, 0), (1, -1, 0, 2, 2),
    (2, 1, 0, 1, 1), (2, -1, 0, 1, 1),
]


def to_blender(points):
    return points[..., [0, 2, 1]]


def exposed_quads(occ, mat, camera):
    verts, uvs, colors = [], [], []
    atlas_h = build_atlas().shape[0] * ATLAS_FACE
    for axis, sign, ua, va, face in DIRECTIONS:
        neighbour = np.roll(occ, -sign, axis=axis)
        edge = [slice(None)] * 3
        edge[axis] = -1 if sign > 0 else 0
        neighbour[tuple(edge)] = False
        cells = np.argwhere(occ & ~neighbour).astype(np.int32)
        if not len(cells):
            continue
        # Une face tournée à l'opposé de la caméra ne se voit pas ; le volume reste fermé pour les ombres.
        centre = cells + 0.5
        centre[:, axis] += 0.5 * sign
        normal = np.zeros(3)
        normal[axis] = sign
        if axis != 1 or sign < 0:
            keep = ((camera.pos - centre) @ normal) > 0
            cells = cells[keep]
        material = mat[cells[:, 0], cells[:, 1], cells[:, 2]].astype(np.int32)
        base = cells.astype(np.float32)
        base[:, axis] += 1 if sign > 0 else 0
        unit_u, unit_v = np.zeros(3, np.float32), np.zeros(3, np.float32)
        unit_u[ua], unit_v[va] = 1, 1
        corners = np.stack([base, base + unit_u, base + unit_u + unit_v, base + unit_v], axis=1)
        params = np.array([[0, 0], [1, 0], [1, 1], [0, 1]], np.float32)
        converted = to_blender(np.stack([unit_u, unit_v, normal.astype(np.float32)]))
        order = [0, 1, 2, 3]
        if np.cross(converted[0], converted[1]) @ converted[2] < 0:
            order = [0, 3, 2, 1]
        corners = corners[:, order]
        params = params[order]
        inset = 0.002
        cu = (face + inset + params[:, 0] * (1 - 2 * inset)) / 3
        cv = (inset + params[:, 1] * (1 - 2 * inset)) * ATLAS_FACE / atlas_h
        u = np.broadcast_to(cu, (len(cells), 4))
        v = 1 - (material[:, None] + 1) * ATLAS_FACE / atlas_h + cv
        verts.append(to_blender(corners))
        uvs.append(np.stack([u, v], axis=-1))
        # Variation par bloc : casse la répétition sans casser le style.
        h = (cells[:, 0] * 73856093 ^ cells[:, 1] * 19349663 ^ cells[:, 2] * 83492791) & 1023
        jitter = (0.86 + 0.28 * h / 1023.0).astype(np.float32)
        colors.append(np.repeat(jitter[:, None, None], 4, axis=1).repeat(3, axis=2))
    return (np.concatenate(verts).astype(np.float32), np.concatenate(uvs).astype(np.float32), np.concatenate(colors).astype(np.float32))


def part_list():
    """Pièces d'un personnage, en texels : (nom, mini, maxi)."""
    return [
        ("head", (-4, 24, -4), (4, 32, 4)),
        ("body", (-4, 12, -2), (4, 24, 2)),
        ("arm_l", (-8, 12, -2), (-4, 24, 2)),
        ("arm_r", (4, 12, -2), (8, 24, 2)),
        ("leg_l", (-4, 0, -2), (0, 12, 2)),
        ("leg_r", (0, 0, -2), (4, 12, 2)),
    ]


def paint_white(part, axis, side, a, b):
    """Veste claire et capuche à motif en zigzag, relevées sur la bannière."""
    if part == "head":
        col = "#e6e5df"
        if axis == 2 and side == 0:  # arrière de la capuche, tourné vers la caméra
            if (b == 3 and a <= 4) or (b == 4 and 3 <= a <= 7):
                col = "#2f4a52"
            if b == 6 and a in (3, 4):
                col = "#2f4a52"
            if b == 0 and a in (3, 4):
                col = "#2f4a52"
        return col
    if part == "body":
        if b < 2:
            return "#16181c" if (a + b) % 5 else "#d9dbe0"
        return "#d6d8dd" if (a + b) % 7 else "#bfc3cb"
    if part.startswith("arm"):
        return "#16181c" if b < 2 else ("#ced1d7" if b % 4 else "#b7bbc4")
    if b < 3:
        return "#eef0f2" if b % 2 else "#8a93a3"
    return "#aeb7c6" if b % 4 else "#97a1b2"


def paint_black(part, axis, side, a, b):
    """Silhouette sombre au visage vu de profil, accents orange au bras."""
    if part == "head":
        col = "#101014"
        if axis == 0 and side == 0:  # profil tourné vers l'autre survivant
            if b < 5:
                col = "#7c4a2d"
                if b == 3 and a in (1, 2):
                    col = "#f0ece4"
                if b == 0:
                    col = "#5d3620"
        return col
    if part == "body":
        return "#1b1a20" if (a + b) % 6 else "#26252c"
    if part == "arm_l":
        return "#d2701f" if 2 < b < 6 else ("#7c4a2d" if b < 3 else "#1b1a20")
    if part == "arm_r":
        return "#1b1a20"
    return "#121116" if b > 2 else "#0a0a0d"


def srgb_lin(value):
    return srgb_to_linear(hex_rgb(value))


def people_quads(people, atlas_rows):
    """Personnages peints texel par texel, avec leur vraie échelle de 2 blocs."""
    verts, uvs, colors = [], [], []
    white_v = 1 - 0.5 / atlas_rows
    unit = 1.0 / 14.5  # les survivants de la bannière sont un peu plus grands que nature
    cache = {}
    for (x, y, z), yaw, paint in people:
        c, s = np.cos(yaw), np.sin(yaw)

        def world(p):
            return np.array([x + c * p[0] + s * p[2], y + p[1], z - s * p[0] + c * p[2]], np.float32)

        for part, lo, hi in part_list():
            lo, hi = np.array(lo, np.float32), np.array(hi, np.float32)
            centre = to_blender(world((lo + hi) / 2 * unit))
            for axis in range(3):
                ia, ib = [i for i in range(3) if i != axis]
                for side in (0, 1):
                    for ta in range(int(hi[ia] - lo[ia])):
                        for tb in range(int(hi[ib] - lo[ib])):
                            quad = []
                            for da, db in ((0, 0), (1, 0), (1, 1), (0, 1)):
                                p = np.zeros(3, np.float32)
                                p[axis] = hi[axis] if side else lo[axis]
                                p[ia] = lo[ia] + ta + da
                                p[ib] = lo[ib] + tb + db
                                quad.append(world(p * unit))
                            bl = to_blender(np.array(quad))
                            if np.cross(bl[1] - bl[0], bl[3] - bl[0]) @ (bl.mean(axis=0) - centre) < 0:
                                bl = bl[[0, 3, 2, 1]]
                            verts.append(bl)
                            uvs.append(np.tile([0.5 / 3, white_v], (4, 1)))
                            value = paint(part, axis, side, ta, tb)
                            if value not in cache:
                                cache[value] = srgb_lin(value)
                            colors.append(np.tile(cache[value], (4, 1)))
    return np.array(verts, np.float32), np.array(uvs, np.float32), np.array(colors, np.float32)


def ledge_quads(camera, atlas_rows):
    """Replat des survivants en sous-blocs, pour suivre le bord fin de la bannière."""
    step = 0.25
    level = float(LEDGE_Y + 1)
    half = 40
    n = int(2 * half / step)
    x0, z0 = camera.pos[0] - half, -6.0
    gx = x0 + (np.arange(n) + 0.5) * step
    gz = z0 + (np.arange(n) + 0.5) * step
    xx, zz = np.meshgrid(gx, gz, indexing="ij")
    point = np.stack([xx, np.full_like(xx, level), zz], axis=-1)
    sx, sy, depth = camera.project(point)
    canvas = Image.new("L", (6000, 6000), 0)
    margin = 2000
    ImageDraw.Draw(canvas).polygon([(a + margin, b + margin) for a, b in LEDGE], fill=255)
    mask = np.asarray(canvas) > 0
    ix = np.clip(((sx - (PLATE_W - 1920) / 2) + margin).astype(np.int64), 0, 5999)
    iy = np.clip(((sy - (PLATE_H - 1080) / 2) + margin).astype(np.int64), 0, 5999)
    inside = mask[iy, ix] & (depth > 0.5) | (zz < 1.5) & (np.abs(xx - camera.pos[0]) < 14)
    # Léger relief par plaques : le sol ne doit pas être un plan parfait.
    bump = np.floor(np.clip(gaussian_filter(np.random.default_rng(4).normal(0, 1, inside.shape), 7) * 14, -1, 1)) * 0.0625
    rows = atlas_rows
    verts, uvs, colors = [], [], []
    cell = lambda material, face, u, v: ((face + u) / 3, 1 - (material + 1) / rows + v / rows)
    from build_world import GRASS, DIRT
    idx = np.argwhere(inside)
    for i, j in idx:
        x, z = float(gx[i]), float(gz[j])
        y = level + float(bump[i, j])
        a, b = x - step / 2, x + step / 2
        c, d = z - step / 2, z + step / 2
        top = to_blender(np.array([[a, y, c], [b, y, c], [b, y, d], [a, y, d]], np.float32))
        fu, fv = (a % 1.0), (c % 1.0)
        tile = [(fu, fv), (fu + step, fv), (fu + step, fv + step), (fu, fv + step)]
        if np.cross(top[1] - top[0], top[3] - top[0])[2] < 0:
            top = top[[0, 3, 2, 1]]
            tile = [tile[k] for k in (0, 3, 2, 1)]
        verts.append(top)
        uvs.append([cell(GRASS, 0, min(0.99, u), min(0.99, v)) for u, v in tile])
        colors.append(np.full((4, 3), 0.95 + 0.1 * ((i // 4 + j // 4) % 2), np.float32))
        # Flancs seulement sur le pourtour : ils font le bord de falaise.
        for di, dj in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ni, nj = i + di, j + dj
            if 0 <= ni < n and 0 <= nj < n and inside[ni, nj]:
                continue
            if di:
                xs = b if di > 0 else a
                wall = np.array([[xs, y, c], [xs, y, d], [xs, y - 6, d], [xs, y - 6, c]], np.float32)
            else:
                zs = d if dj > 0 else c
                wall = np.array([[a, y, zs], [b, y, zs], [b, y - 6, zs], [a, y - 6, zs]], np.float32)
            bl = to_blender(wall)
            normal = np.array([di, 0, dj], np.float32)
            if np.cross(bl[1] - bl[0], bl[3] - bl[0]) @ to_blender(normal) < 0:
                bl = bl[[0, 3, 2, 1]]
            verts.append(bl)
            uvs.append([cell(DIRT, 1, 0.1, 0.9), cell(DIRT, 1, 0.3, 0.9), cell(DIRT, 1, 0.3, 0.1), cell(DIRT, 1, 0.1, 0.1)])
            colors.append(np.full((4, 3), 0.8, np.float32))
    return np.array(verts, np.float32), np.array(uvs, np.float32), np.array(colors, np.float32)


def main():
    mat = np.load(CACHE / "mat.npy")
    layer = np.load(CACHE / "layer.npy")
    mat_pre = np.load(CACHE / "mat_pre.npy")
    layer_pre = np.load(CACHE / "layer_pre.npy")
    camera = Camera()

    atlas = build_atlas()
    rows = atlas.shape[0]
    image = np.zeros((rows * ATLAS_FACE, 3 * ATLAS_FACE, 3), np.float32)
    for m in range(rows):
        for f in range(3):
            image[m * ATLAS_FACE:(m + 1) * ATLAS_FACE, f * ATLAS_FACE:(f + 1) * ATLAS_FACE] = linear_to_srgb(atlas[m, f]) if m else 1.0
    Image.fromarray((np.clip(image, 0, 1) * 255 + 0.5).astype(np.uint8)).save(CACHE / "atlas.png")

    wreck = (mat > 0) & (mat_pre == 0)
    groups = {
        "far": layer_pre == FAR,
        "mid": layer_pre == MID,
        "fg": layer_pre == FG,
        "wreck": wreck,
    }
    for name, occ in groups.items():
        if occ.any():
            v, uv, c = exposed_quads(occ, mat, camera)
        else:
            v, uv, c = np.zeros((0, 4, 3), np.float32), np.zeros((0, 4, 2), np.float32), np.zeros((0, 4, 3), np.float32)
        if name == "fg":
            # Premier plan sans personnage : le replat seul, comme demandé pour l'écran de chargement.
            lv, luv, lc = ledge_quads(camera, rows)
            v, uv, c = np.concatenate([v, lv]), np.concatenate([uv, luv]), np.concatenate([c, lc])
        np.savez(CACHE / f"mesh_{name}.npz", verts=v, uvs=uv, colors=c)
        print(name, len(v), "quads", flush=True)

    moon = to_blender(camera.direction_at(*MOON_UV))
    sun = to_blender(camera.direction_at(*SUN_UV))
    fires = json.loads((CACHE / "fires.json").read_text())
    scene = {
        "camera": {
            "pos": to_blender(camera.pos).tolist(),
            "right": to_blender(camera.right).tolist(),
            "up": to_blender(camera.up).tolist(),
            "forward": to_blender(camera.forward).tolist(),
            "lens": float(camera.focal / PLATE_W * 36.0),
        },
        "plate": [PLATE_W, PLATE_H],
        "moon": moon.tolist(), "sun": sun.tolist(),
        "fires": [[f[0], f[2], f[1], f[3]] for f in fires],
    }
    (CACHE / "scene.json").write_text(json.dumps(scene, indent=1))


if __name__ == "__main__":
    main()
