"""Lance des rayons dans l'île voxel et produit les calques éclairés.

Pour chaque calque (far, mid, fg) : trois éclairages (night, pre, sun) et une passe
de lumière des feux, séparée pour pouvoir la faire vaciller à l'exécution.

Usage : python render_plates.py [--scale 0.5] [--ss 2]
"""
import argparse
import json
import multiprocessing as mp
import time

import numpy as np
from PIL import Image
from scipy.ndimage import distance_transform_cdt, gaussian_filter

from build_world import ASH, BRICK, BURNT, DARKLEAF, DIRT, FAR, FG, GLASS, GRASS, HULL, IRON, LEAF, LOG, MID, MOSSY, PAINT, PALM, PLANK, SAND, SANDSTONE, STONE
from common import ASSETS, CACHE, PLATE_H, PLATE_W, Camera, hex_rgb, jar_image, linear_to_srgb, save_rgba, srgb_to_linear, tonemap

LAYERS = {"far": FAR, "mid": MID, "fg": FG}

# Direction des astres, définie par leur position à l'écran pour rester cohérente avec le ciel.
MOON_UV = (1385 / 1920, 135 / 1080)
SUN_UV = (430 / 1920, 430 / 1080)

LIGHTS = {
    "night": dict(sky=(0.085, 0.125, 0.25), ground=(0.03, 0.04, 0.07), key=(0.62, 0.74, 1.15), key_dir="moon", fog=(0.030, 0.050, 0.105), fog_dist=1100),
    "pre": dict(sky=(0.21, 0.20, 0.34), ground=(0.06, 0.05, 0.075), key=(0.0, 0.0, 0.0), key_dir=None, fog=(0.24, 0.17, 0.27), fog_dist=1300),
    "sun": dict(sky=(0.46, 0.46, 0.54), ground=(0.2, 0.15, 0.11), key=(2.1, 1.28, 0.66), key_dir="sun", fog=(0.82, 0.48, 0.30), fog_dist=1500),
}


def tint(texture, color):
    return texture[..., :3] * hex_rgb(color)


def over(top, bottom):
    alpha = top[..., 3:]
    return top[..., :3] * alpha + bottom * (1 - alpha)


def first_frame(name):
    image = jar_image(f"block/{name}")
    return image[:16, :16]


def build_atlas():
    """Atlas [matériau, face, v, u, rgb] en linéaire. Face : 0 dessus, 1 côté, 2 dessous."""
    t = first_frame
    grass_tint = "#3f9c3f"
    grass_top = tint(t("grass_block_top"), grass_tint)
    grass_side = over(np.dstack([tint(t("grass_block_side_overlay"), grass_tint), t("grass_block_side_overlay")[..., 3]]), t("dirt")[..., :3])
    leaf = t("jungle_leaves")
    leaf_rgb = over(np.dstack([tint(leaf, "#2fa32c"), leaf[..., 3]]), np.full((16, 16, 3), hex_rgb("#0f4a18")))
    dark_rgb = over(np.dstack([tint(leaf, "#1f8a2c"), leaf[..., 3]]), np.full((16, 16, 3), hex_rgb("#0b3a18")))
    palm_rgb = over(np.dstack([tint(leaf, "#6fb23b"), leaf[..., 3]]), np.full((16, 16, 3), hex_rgb("#1d4a15")))
    plain = lambda name: (t(name)[..., :3],) * 3
    entries = {
        GRASS: (grass_top, grass_side, t("dirt")[..., :3]),
        DIRT: plain("dirt"),
        SAND: plain("sand"),
        STONE: plain("stone"),
        LEAF: (leaf_rgb,) * 3,
        DARKLEAF: (dark_rgb,) * 3,
        PALM: (palm_rgb,) * 3,
        LOG: (t("jungle_log_top")[..., :3], t("jungle_log")[..., :3], t("jungle_log_top")[..., :3]),
        HULL: plain("white_concrete"),
        IRON: plain("iron_block"),
        BURNT: plain("coal_block"),
        PAINT: plain("blue_concrete"),
        GLASS: plain("light_blue_concrete"),
        BRICK: plain("stone_bricks"),
        MOSSY: plain("mossy_stone_bricks"),
        ASH: plain("coarse_dirt"),
        SANDSTONE: (t("sandstone_top")[..., :3], t("sandstone")[..., :3], t("sandstone_bottom")[..., :3]),
        PLANK: plain("spruce_planks"),
    }
    atlas = np.zeros((max(entries) + 1, 3, 16, 16, 3), dtype=np.float32)
    for material, faces in entries.items():
        for f, face in enumerate(faces):
            atlas[material, f] = srgb_to_linear(face)
    # Le sable de plage et la coque restent lumineux même sous la lune.
    atlas[BURNT] *= 0.75
    atlas[HULL] *= 0.8
    return atlas


def survivor_boxes():
    """Les deux survivants, vus de dos : boîtes (min, max, couleur haut, couleur bas)."""
    boxes = []

    def person(x, y, z, yaw, hair, shirt, pants, shoes, accent):
        c, s = np.cos(yaw), np.sin(yaw)
        unit = 1.0 / 16
        parts = [
            ((-4, 24, -4), (4, 32, 4), hair, hair),
            ((-4, 12, -2), (4, 24, 2), shirt, shirt),
            ((-8, 12, -2), (-4, 24, 2), shirt, accent),
            ((4, 12, -2), (8, 24, 2), shirt, accent),
            ((-4, 3, -2), (0, 12, 2), pants, pants),
            ((0, 3, -2), (4, 12, 2), pants, pants),
            ((-4, 0, -2), (0, 3, 2), shoes, shoes),
            ((0, 0, -2), (4, 3, 2), shoes, shoes),
        ]
        for lo, hi, top, bottom in parts:
            lo = np.array(lo) * unit
            hi = np.array(hi) * unit
            boxes.append((x, y, z, c, s, lo, hi, srgb_to_linear(hex_rgb(top)), srgb_to_linear(hex_rgb(bottom))))

    return person, boxes


def get_people(topmap):
    person, boxes = survivor_boxes()
    people = json.loads((CACHE / "world.json").read_text())["people"]
    x, y, z = people["white"]
    person(x, y, z, -0.42, "#e8e6df", "#dcdbd4", "#50627c", "#2b2b30", "#c2c0b8")
    x, y, z = people["black"]
    person(x, y, z, -0.2, "#1b1a20", "#23222a", "#17161b", "#0e0e12", "#d36a22")
    return boxes


def intersect_boxes(origin, dirs, boxes):
    """Intersection analytique avec les personnages ; renvoie t, normale, albédo."""
    n = len(dirs)
    best = np.full(n, np.inf, dtype=np.float32)
    normal = np.zeros((n, 3), dtype=np.float32)
    albedo = np.zeros((n, 3), dtype=np.float32)
    for x, y, z, c, s, lo, hi, top_col, low_col in boxes:
        # Passage dans le repère du personnage (rotation autour de y).
        ox, oy, oz = origin[..., 0] - x, origin[..., 1] - y, origin[..., 2] - z
        dx, dy, dz = dirs[:, 0], dirs[:, 1], dirs[:, 2]
        o = np.stack([c * ox - s * oz, oy * np.ones_like(ox), s * ox + c * oz], axis=-1)
        d = np.stack([c * dx - s * dz, dy, s * dx + c * dz], axis=-1)
        with np.errstate(divide="ignore", invalid="ignore"):
            t1, t2 = (lo - o) / d, (hi - o) / d
        tn = np.minimum(t1, t2)
        tf = np.maximum(t1, t2)
        near, far = np.nanmax(tn, axis=1), np.nanmin(tf, axis=1)
        hit = (near < far) & (near > 0) & (near < best)
        if not hit.any():
            continue
        axis = np.nanargmax(tn[hit], axis=1)
        local = np.zeros((hit.sum(), 3), dtype=np.float32)
        local[np.arange(len(axis)), axis] = -np.sign(d[hit][np.arange(len(axis)), axis])
        # Retour dans le repère du monde.
        wn = np.stack([c * local[:, 0] + s * local[:, 2], local[:, 1], -s * local[:, 0] + c * local[:, 2]], axis=-1)
        pos = o[hit] + d[hit] * near[hit, None]
        fy = np.clip((pos[:, 1] - lo[1]) / max(hi[1] - lo[1], 1e-4), 0, 1)[:, None]
        pixel = 0.92 + 0.14 * ((np.floor(pos[:, 0] * 16) + np.floor(pos[:, 1] * 16) + np.floor(pos[:, 2] * 16)) % 2)[:, None]
        best[hit] = near[hit]
        normal[hit] = wn
        albedo[hit] = (low_col * (1 - fy) + top_col * fy) * pixel
    return best, normal, albedo


def trace(o, d, occ, dist, tmax=2000.0):
    """Parcours de grille vectorisé, accéléré par un champ de distance de Tchebychev."""
    n = len(d)
    shape = np.array(dist.shape)
    t_hit = np.full(n, np.inf, dtype=np.float32)
    ids = np.zeros((n, 3), dtype=np.int32)
    axis_hit = np.zeros(n, dtype=np.int8)

    with np.errstate(divide="ignore", invalid="ignore"):
        inv = 1.0 / d
        t0 = (0.0 - o) * inv
        t1 = (shape - o) * inv
    tmin = np.nanmax(np.minimum(t0, t1), axis=1)
    tout = np.nanmin(np.maximum(t0, t1), axis=1)
    tin = np.maximum(tmin, 0.0) + 1e-4
    active = np.where(tin < np.minimum(tout, tmax))[0]
    if not len(active):
        return t_hit, ids, axis_hit

    step = np.where(d >= 0, 1, -1).astype(np.int32)
    delta = np.abs(inv)
    max_comp = np.abs(d).max(axis=1)
    t = tin.astype(np.float64)
    last_axis = np.argmax(np.abs(d), axis=1).astype(np.int8)

    def setup(idx, t_now):
        p = o[idx] + d[idx] * t_now[..., None]
        cell = np.clip(np.floor(p).astype(np.int32), 0, shape - 1)
        with np.errstate(divide="ignore", invalid="ignore"):
            nxt = (cell + (step[idx] > 0) - o[idx]) * inv[idx]
        nxt = np.where(np.isfinite(nxt), nxt, np.inf)
        return cell, nxt

    cell = np.zeros((n, 3), dtype=np.int32)
    nxt = np.zeros((n, 3), dtype=np.float64)
    cell[active], nxt[active] = setup(active, t[active])

    for _ in range(4000):
        if not len(active):
            break
        c = cell[active]
        distance = dist[c[:, 0], c[:, 1], c[:, 2]].astype(np.int32)
        solid = distance == 0
        if solid.any():
            done = active[solid]
            t_hit[done] = t[done]
            ids[done] = cell[done]
            axis_hit[done] = last_axis[done]
            active = active[~solid]
            distance = distance[~solid]
            if not len(active):
                break
        far = distance > 2
        if far.any():
            jump = active[far]
            t[jump] += (distance[far] - 1) / max_comp[jump]
            cell[jump], nxt[jump] = setup(jump, t[jump])
        near = active[~far]
        if len(near):
            axis = np.argmin(nxt[near], axis=1)
            r = np.arange(len(near))
            t[near] = nxt[near, axis]
            cell[near, axis] += step[near, axis]
            nxt[near, axis] += delta[near, axis]
            last_axis[near] = axis
        c = cell[active]
        alive = ((c >= 0) & (c < shape)).all(axis=1) & (t[active] < tout[active]) & (t[active] < tmax)
        active = active[alive]
    return t_hit, ids, axis_hit


class Shared:
    pass


def init_worker(args):
    suffix = args["suffix"]
    Shared.mat = np.load(CACHE / f"mat{suffix}.npy", mmap_mode="r")
    Shared.dist_all = np.load(CACHE / f"dist_all{suffix}.npy", mmap_mode="r")
    Shared.dists = {k: np.load(CACHE / f"dist_{k}{suffix}.npy", mmap_mode="r") for k in LAYERS}
    Shared.ao1 = np.load(CACHE / f"ao1{suffix}.npy", mmap_mode="r")
    Shared.ao2 = np.load(CACHE / f"ao2{suffix}.npy", mmap_mode="r")
    Shared.atlas = build_atlas()
    Shared.people = get_people(np.load(CACHE / "topmap.npy"))
    Shared.fires = json.loads((CACHE / "fires.json").read_text())
    Shared.args = args
    Shared.cam = Camera(args["width"], args["height"])
    Shared.dirs = {"moon": Shared.cam.direction_at(*MOON_UV), "sun": Shared.cam.direction_at(*SUN_UV)}
    # Les astres sont bas : on relève et décale la lumière pour modeler le relief.
    Shared.dirs["moon"] = Shared.dirs["moon"] + np.array([-0.2, 0.55, -0.45])
    Shared.dirs["moon"] /= np.linalg.norm(Shared.dirs["moon"])
    Shared.dirs["sun"] = Shared.dirs["sun"] + np.array([-0.45, 0.42, -0.35])
    Shared.dirs["sun"] /= np.linalg.norm(Shared.dirs["sun"])


def shade(hit_pos, normal, albedo, t_hit):
    """Calcule les éclairages d'une série de points, en linéaire."""
    out = {}
    up = np.clip(normal[:, 1] * 0.5 + 0.5, 0, 1)[:, None]
    p = hit_pos + normal * 1.0
    ix = np.clip(p.astype(np.int32), 0, np.array(Shared.ao1.shape) - 1)
    ao_near = Shared.ao1[ix[:, 0], ix[:, 1], ix[:, 2]].astype(np.float32) / 255
    ao_far = Shared.ao2[ix[:, 0], ix[:, 1], ix[:, 2]].astype(np.float32) / 255
    ao = (np.clip(1.15 - ao_near * 1.5, 0.18, 1) * np.clip(1.25 - ao_far * 1.5, 0.3, 1))[:, None]

    shadows = {}
    origin = hit_pos + normal * 0.02
    for key, direction in Shared.dirs.items():
        facing = normal @ direction
        lit = facing > 0
        visible = np.zeros(len(hit_pos), dtype=np.float32)
        if lit.any():
            jitter = direction + np.random.default_rng(int(hit_pos[0, 0] * 1000) % 2 ** 31).normal(0, 0.008, (lit.sum(), 3))
            jitter /= np.linalg.norm(jitter, axis=1, keepdims=True)
            hit, _, _ = trace(origin[lit], jitter, None, Shared.dist_all, 700.0)
            visible[lit] = ~np.isfinite(hit)
        shadows[key] = (np.clip(facing, 0, 1) * visible)[:, None].astype(np.float32)

    for name, light in LIGHTS.items():
        ambient = np.array(light["sky"], dtype=np.float32) * up + np.array(light["ground"], dtype=np.float32) * (1 - up)
        color = albedo * ambient * ao
        if light["key_dir"]:
            color = color + albedo * np.array(light["key"], dtype=np.float32) * shadows[light["key_dir"]] * (0.45 + 0.55 * ao)
        fog = 1 - np.exp(-t_hit[:, None] / light["fog_dist"])
        out[name] = color * (1 - fog) + np.array(light["fog"], dtype=np.float32) * fog
    fire = np.zeros_like(albedo)
    for fx, fy, fz, power in Shared.fires:
        v = np.array([fx, fy, fz], dtype=np.float32) - hit_pos
        d2 = (v * v).sum(axis=1, keepdims=True)
        lam = np.clip((normal * v).sum(axis=1, keepdims=True) / np.sqrt(d2 + 1e-3), 0.12, 1)
        fire += power * 0.45 * lam / (d2 / 60.0 + 1.0)
    out["fire"] = albedo * fire * np.array([3.4, 1.35, 0.42], dtype=np.float32) * (0.35 + 0.65 * ao)
    return out


def render_rows(job):
    layer_name, y0, y1 = job
    args, cam = Shared.args, Shared.cam
    ss = args["ss"]
    width = args["width"] * ss
    ys, xs = np.mgrid[y0:y1, 0:width]
    px = (xs.ravel() + 0.5) / ss
    py = (ys.ravel() + 0.5) / ss
    dirs = cam.rays(px, py).astype(np.float64)
    origin = np.broadcast_to(cam.pos, dirs.shape).astype(np.float64)
    t, ids, axis = trace(origin, dirs, None, Shared.dists[layer_name])
    if layer_name == "fg":
        t_people, n_people, a_people = intersect_boxes(origin, dirs, Shared.people)
    n = len(dirs)
    result = {k: np.zeros((n, 3), dtype=np.float32) for k in list(LIGHTS) + ["fire"]}
    alpha = np.zeros(n, dtype=np.float32)
    depth = np.full(n, np.inf, dtype=np.float32)
    hit = np.isfinite(t)
    if hit.any():
        idx = np.where(hit)[0]
        pos = origin[idx] + dirs[idx] * t[idx, None]
        ax = axis[idx]
        normal = np.zeros((len(idx), 3), dtype=np.float32)
        sign = -np.sign(dirs[idx][np.arange(len(idx)), ax])
        normal[np.arange(len(idx)), ax] = sign
        cell = ids[idx]
        frac = pos - cell
        # Coordonnées de texture selon la face touchée.
        u = np.where(ax == 0, frac[:, 2], frac[:, 0])
        v = np.where(ax == 1, frac[:, 2], 1 - frac[:, 1])
        ui = np.clip((u * 16).astype(np.int32), 0, 15)
        vi = np.clip((v * 16).astype(np.int32), 0, 15)
        material = np.asarray(Shared.mat[cell[:, 0], cell[:, 1], cell[:, 2]])
        face = np.where(ax == 1, np.where(sign > 0, 0, 2), 1)
        albedo = Shared.atlas[material, face, vi, ui].copy()
        # Variation par bloc : casse la répétition sans casser le style.
        h = (cell[:, 0] * 73856093 ^ cell[:, 1] * 19349663 ^ cell[:, 2] * 83492791) & 1023
        albedo *= (0.86 + 0.28 * (h / 1023.0))[:, None].astype(np.float32)
        light = shade(pos.astype(np.float32), normal, albedo, t[idx])
        for k, value in light.items():
            result[k][idx] = value
        alpha[idx] = 1
        depth[idx] = t[idx]
    if layer_name == "fg":
        people = np.isfinite(t_people) & (t_people < depth)
        if people.any():
            idx = np.where(people)[0]
            pos = origin[idx] + dirs[idx] * t_people[idx, None]
            light = shade(pos.astype(np.float32), n_people[idx], a_people[idx], t_people[idx])
            for k, value in light.items():
                result[k][idx] = value
            alpha[idx] = 1
            depth[idx] = t_people[idx]
    shape = (y1 - y0, width)
    return layer_name, y0, y1, {k: v.reshape(*shape, 3) for k, v in result.items()}, alpha.reshape(shape), depth.reshape(shape)


def prepare_fields():
    print("champs de distance…", flush=True)
    for suffix in ("", "_pre"):
        mat = np.load(CACHE / f"mat{suffix}.npy", mmap_mode="r")
        layer = np.load(CACHE / f"layer{suffix}.npy", mmap_mode="r")
        occ = np.asarray(mat) > 0
        np.save(CACHE / f"dist_all{suffix}.npy", np.minimum(distance_transform_cdt(~occ, metric="chessboard"), 250).astype(np.uint8))
        for name, value in LAYERS.items():
            np.save(CACHE / f"dist_{name}{suffix}.npy", np.minimum(distance_transform_cdt(~(np.asarray(layer) == value), metric="chessboard"), 250).astype(np.uint8))
        f = occ.astype(np.float32)
        np.save(CACHE / f"ao1{suffix}.npy", (gaussian_filter(f, 1.1) * 255).astype(np.uint8))
        np.save(CACHE / f"ao2{suffix}.npy", (gaussian_filter(f, 4.5) * 255).astype(np.uint8))


def fire_points():
    """Foyers de l'épave : servent à éclairer les calques et à placer les flammes."""
    world = json.loads((CACHE / "world.json").read_text())
    hmap = np.load(CACHE / "hmap.npy")
    wx, wz = world["wreck"]
    angle = world["wreck_angle"]
    ax, az = np.cos(angle), np.sin(angle)
    fires = []
    for u, v, w, power in [(-5, 0, 12, 9.0), (-15, 3, 12, 6.0), (8, -1, 12, 6.5), (24, 0, 11, 4.0), (-34, 4, 10, 3.5), (13, 21, 6, 5.0), (19, -22, 9, 4.5), (36, -28, 4, 4.0), (-90, 2, 3, 3.5), (-130, -3, 3, 2.5), (-60, 8, 3, 3.0)]:
        x = wx + u * ax - v * az
        z = wz + u * az + v * ax
        y = float(hmap[int(x), int(z)]) + w
        fires.append([float(x), y, float(z), power])
    (CACHE / "fires.json").write_text(json.dumps(fires))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scale", type=float, default=1.0)
    parser.add_argument("--ss", type=int, default=2)
    parser.add_argument("--fields", action="store_true", help="recalcule les champs")
    parser.add_argument("--out", default=None)
    parser.add_argument("--pre", action="store_true", help="décor sans épave, calque mid seul")
    opts = parser.parse_args()
    if opts.fields or not (CACHE / "dist_all.npy").exists():
        prepare_fields()
    if not json.loads((CACHE / "world.json").read_text()).get("banner"):
        fire_points()
    args = {"width": int(PLATE_W * opts.scale), "height": int(PLATE_H * opts.scale), "ss": opts.ss, "suffix": "_pre" if opts.pre else ""}
    names = ["mid"] if opts.pre else list(LAYERS)
    out_dir = ASSETS / "plates" if opts.out is None else CACHE / opts.out
    total_rows = args["height"] * opts.ss
    band = max(8, total_rows // 96)
    jobs = [(name, y, min(total_rows, y + band)) for name in names for y in range(0, total_rows, band)]
    start = time.time()
    buffers = {
        name: {
            "color": {k: np.zeros((total_rows, args["width"] * opts.ss, 3), dtype=np.float32) for k in list(LIGHTS) + ["fire"]},
            "alpha": np.zeros((total_rows, args["width"] * opts.ss), dtype=np.float32),
            "depth": np.zeros((total_rows, args["width"] * opts.ss), dtype=np.float32),
        } for name in names
    }
    with mp.Pool(max(1, mp.cpu_count() - 2), initializer=init_worker, initargs=(args,)) as pool:
        for i, (name, y0, y1, color, alpha, depth) in enumerate(pool.imap_unordered(render_rows, jobs)):
            for k, value in color.items():
                buffers[name]["color"][k][y0:y1] = value
            buffers[name]["alpha"][y0:y1] = alpha
            buffers[name]["depth"][y0:y1] = depth
            if i % 24 == 0:
                print(f"{i}/{len(jobs)} {time.time() - start:.0f}s", flush=True)

    depth_all = {}
    for name, buffer in buffers.items():
        ss = opts.ss
        alpha = buffer["alpha"]
        a = alpha.reshape(args["height"], ss, args["width"], ss).mean(axis=(1, 3))
        for k, color in buffer["color"].items():
            c = color.reshape(args["height"], ss, args["width"], ss, 3).mean(axis=(1, 3))
            c = c / np.maximum(a[..., None], 1e-3)
            if k == "fire":
                rgb = linear_to_srgb(tonemap(c))
                if not opts.pre:
                    save_rgba(out_dir / f"{name}_fire.png", rgb)
            else:
                rgb = linear_to_srgb(tonemap(c))
                prefix = "precrash_" if opts.pre else ""
                save_rgba(out_dir / f"{prefix}{name}_{k}.png", np.dstack([rgb, a]))
        depth_all[name] = np.where(np.isfinite(buffer["depth"]), buffer["depth"], 1e6).reshape(args["height"], ss, args["width"], ss).min(axis=(1, 3))
    if not opts.pre:
        np.savez_compressed(CACHE / "depth.npz", **depth_all)
    print(f"calques ecrits dans {out_dir} en {time.time() - start:.0f}s", flush=True)


if __name__ == "__main__":
    main()
