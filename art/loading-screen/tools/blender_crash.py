"""Rendu Cycles de l'avion en chute, image par image, à la caméra exacte de la scène.

Le film est transparent : compose.py place l'avion entre le décor et le premier plan.
blender -b -P blender_crash.py -- --cache <dossier> --out <dossier> [--samples 48] [--frames 0,120,200]
"""
import argparse
import json
import math
import random
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
parser = argparse.ArgumentParser()
parser.add_argument("--cache", required=True)
parser.add_argument("--track", required=True)
parser.add_argument("--out", required=True)
parser.add_argument("--samples", type=int, default=48)
parser.add_argument("--frames", default=None)
opts = parser.parse_args(argv)
CACHE, OUT = Path(opts.cache), Path(opts.out)
OUT.mkdir(parents=True, exist_ok=True)
data = json.loads((CACHE / "scene.json").read_text())
track = json.loads(Path(opts.track).read_text(encoding="utf-8"))

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene


def to_bl(v):
    return Vector((v[0], v[2], v[1]))


def setup():
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for kind in ("OPTIX", "CUDA"):
        try:
            prefs.compute_device_type = kind
        except TypeError:
            continue
        prefs.get_devices()
        if any(d.type == kind for d in prefs.devices):
            for d in prefs.devices:
                d.use = d.type == kind
            break
    scene.render.engine = "CYCLES"
    scene.cycles.device = "GPU"
    scene.cycles.samples = opts.samples
    scene.cycles.use_adaptive_sampling = True
    scene.cycles.adaptive_threshold = 0.03
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 4
    scene.render.film_transparent = True
    scene.render.resolution_x, scene.render.resolution_y = data["plate"]
    scene.render.resolution_percentage = 100
    scene.render.fps = track["fps"]
    scene.render.use_motion_blur = True
    scene.render.motion_blur_shutter = 0.6
    scene.render.use_persistent_data = True
    scene.render.use_border = True
    scene.render.use_crop_to_border = False
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.view_settings.view_transform = "AgX"
    looks = [i.identifier for i in scene.view_settings.bl_rna.properties["look"].enum_items]
    scene.view_settings.look = next((l for l in looks if "Medium High Contrast" in l), "None")


def plane_material():
    material = bpy.data.materials.new("plane")
    material.use_nodes = True
    nodes, links = material.node_tree.nodes, material.node_tree.links
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    attr = nodes.new("ShaderNodeAttribute")
    attr.attribute_name = "tint"
    links.new(attr.outputs["Color"], bsdf.inputs["Base Color"])
    links.new(attr.outputs["Color"], bsdf.inputs["Emission Color"])
    links.new(attr.outputs["Alpha"], bsdf.inputs["Emission Strength"])
    bsdf.inputs["Roughness"].default_value = 0.42
    return material


def quad_object(name, verts, tints, material):
    quads = len(verts)
    n = quads * 4
    mesh = bpy.data.meshes.new(name)
    mesh.vertices.add(n)
    mesh.vertices.foreach_set("co", verts.reshape(-1))
    mesh.loops.add(n)
    mesh.polygons.add(quads)
    mesh.loops.foreach_set("vertex_index", np.arange(n, dtype=np.int32))
    mesh.polygons.foreach_set("loop_start", np.arange(0, n, 4, dtype=np.int32))
    mesh.update(calc_edges=True)
    if tints is not None:
        attribute = mesh.color_attributes.new("tint", "FLOAT_COLOR", "POINT")
        attribute.data.foreach_set("color", np.repeat(tints, 4, axis=0).reshape(-1))
    if material is not None:
        mesh.materials.append(material)
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    return obj


def pose_matrix(pose):
    f, s, u = to_bl(pose["f"]), to_bl(pose["s"]), to_bl(pose["u"])
    p = to_bl(pose["pos"])
    return Matrix(((f.x, s.x, u.x, p.x), (f.y, s.y, u.y, p.y), (f.z, s.z, u.z, p.z), (0, 0, 0, 1)))


def camera_setup():
    cam = bpy.data.cameras.new("cam")
    cam.sensor_fit = "HORIZONTAL"
    cam.sensor_width = 36.0
    cam.lens = data["camera"]["lens"]
    cam.clip_end = 6000
    obj = bpy.data.objects.new("cam", cam)
    scene.collection.objects.link(obj)
    c = data["camera"]
    right, up, forward = Vector(c["right"]), Vector(c["up"]), Vector(c["forward"])
    obj.matrix_world = Matrix((
        (right.x, up.x, -forward.x, c["pos"][0]),
        (right.y, up.y, -forward.y, c["pos"][1]),
        (right.z, up.z, -forward.z, c["pos"][2]),
        (0, 0, 0, 1),
    ))
    scene.camera = obj


def lights():
    world = bpy.data.worlds.new("sky")
    world.use_nodes = True
    scene.world = world
    background = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
    background.inputs["Color"].default_value = (0.11, 0.17, 0.32, 1)
    background.inputs["Strength"].default_value = 1.05
    # Le fond éclaire l'avion, sans se voir : le film reste transparent.
    moon = bpy.data.lights.new("moon", "SUN")
    moon.color = (0.55, 0.72, 1.0)
    moon.energy = 2.1
    moon.angle = math.radians(2)
    mo = bpy.data.objects.new("moon", moon)
    scene.collection.objects.link(mo)
    mo.rotation_mode = "QUATERNION"
    mo.rotation_quaternion = (Vector(data["moon"]) + Vector((-0.1, -0.1, 0.5))).normalized().to_track_quat("Z", "Y")
    fire = bpy.data.lights.new("engine_fire", "POINT")
    fire.color = (1.0, 0.45, 0.14)
    fire.shadow_soft_size = 3.0
    fo = bpy.data.objects.new("engine_fire", fire)
    scene.collection.objects.link(fo)
    return fire, fo


def bounds(frame_index):
    """Cadre de rendu autour de l'avion et du bout d'aile : le reste de l'image est vide."""
    c = data["camera"]
    width, height = data["plate"]
    focal = c["lens"] / 36.0 * width
    pos, right, up, forward = Vector(c["pos"]), Vector(c["right"]), Vector(c["up"]), Vector(c["forward"])
    points = []
    frames = track["frames"]
    for i in (max(0, frame_index - 1), frame_index, min(len(frames) - 1, frame_index + 1)):
        points.append((frames[i]["pos"], 62))
        if track["tips"][i]:
            points.append((track["tips"][i]["pos"], 26))
    x0, y0, x1, y1 = 1e9, 1e9, -1e9, -1e9
    for p, radius in points:
        v = to_bl(p) - pos
        depth = max(5.0, v.dot(forward))
        sx = width / 2 + v.dot(right) / depth * focal
        sy = height / 2 - v.dot(up) / depth * focal
        pad = radius / depth * focal + 12
        x0, x1, y0, y1 = min(x0, sx - pad), max(x1, sx + pad), min(y0, sy - pad), max(y1, sy + pad)
    x0, x1 = max(0, x0), min(width, x1)
    y0, y1 = max(0, y0), min(height, y1)
    if x1 <= x0 or y1 <= y0:
        return None
    return x0 / width, x1 / width, 1 - y1 / height, 1 - y0 / height


def main():
    setup()
    camera_setup()
    fire, fire_obj = lights()
    material = plane_material()
    payload = np.load(CACHE / "crash_plane.npz")
    body = quad_object("body", payload["body"], payload["body_tint"], material)
    tip = quad_object("tip", payload["tip"], payload["tip_tint"], material)
    centre = Vector(track["local_tip_center"])

    frames = track["frames"]
    tips = track["tips"]
    for obj in (body, tip):
        obj.rotation_mode = "QUATERNION"
    previous = {}

    def key_pose(obj, matrix, frame):
        # Quaternions de même signe d'une image à l'autre, sinon le flou de mouvement fait tourner l'objet.
        loc, quat, _ = matrix.decompose()
        if obj.name in previous:
            quat.make_compatible(previous[obj.name])
        previous[obj.name] = quat
        obj.location, obj.rotation_quaternion = loc, quat
        obj.keyframe_insert("location", frame=frame)
        obj.keyframe_insert("rotation_quaternion", frame=frame)

    for i, fr in enumerate(frames):
        matrix = pose_matrix(fr)
        key_pose(body, matrix, i + 1)
        # Le bout d'aile vole seul : son repère tourne autour du milieu de la pièce.
        key_pose(tip, pose_matrix(tips[i]) @ Matrix.Translation(-centre) if tips[i] else matrix, i + 1)
    wanted = range(len(frames)) if not opts.frames else [int(v) for v in opts.frames.split(",")]
    rng = random.Random(7)
    flicker = [0.7 + 0.5 * rng.random() for _ in frames]
    for i in wanted:
        scene.frame_set(i + 1)
        fire_obj.location = to_bl(frames[i]["engine"])
        fire.energy = 2600 * flicker[i]
        box = bounds(i)
        path = OUT / f"plane_{i:03d}.png"
        if box is None:
            continue
        scene.render.border_min_x, scene.render.border_max_x, scene.render.border_min_y, scene.render.border_max_y = box
        scene.render.filepath = str(path)
        bpy.ops.render.render(write_still=True)
        print("ok", i, flush=True)


main()
