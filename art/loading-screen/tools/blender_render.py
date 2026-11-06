"""Rendu Cycles des calques. À lancer dans Blender :

blender -b -P blender_render.py -- --cache <dossier> --out <dossier> [--scale 0.5] [--samples 64] [--only mid_night,mid_sun] [--save scene.blend]
"""
import argparse
import json
import math
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
parser = argparse.ArgumentParser()
parser.add_argument("--cache", required=True)
parser.add_argument("--out", required=True)
parser.add_argument("--scale", type=float, default=1.0)
parser.add_argument("--samples", type=int, default=96)
parser.add_argument("--only", default=None)
parser.add_argument("--save", default=None)
opts = parser.parse_args(argv)
CACHE, OUT = Path(opts.cache), Path(opts.out)
OUT.mkdir(parents=True, exist_ok=True)
data = json.loads((CACHE / "scene.json").read_text())

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene


def setup_cycles():
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for device_type in ("OPTIX", "CUDA"):
        try:
            prefs.compute_device_type = device_type
        except TypeError:
            continue
        prefs.get_devices()
        gpus = [d for d in prefs.devices if d.type == device_type]
        if gpus:
            for d in prefs.devices:
                d.use = d.type == device_type
            print("GPU :", device_type, [d.name for d in gpus], flush=True)
            break
    scene.render.engine = "CYCLES"
    scene.cycles.device = "GPU"
    scene.cycles.samples = opts.samples
    scene.cycles.use_adaptive_sampling = True
    scene.cycles.adaptive_threshold = 0.02
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 5
    scene.cycles.diffuse_bounces = 3
    scene.cycles.glossy_bounces = 1
    scene.cycles.transmission_bounces = 0
    scene.cycles.volume_bounces = 0
    scene.render.film_transparent = True
    scene.render.resolution_x = int(data["plate"][0] * opts.scale)
    scene.render.resolution_y = int(data["plate"][1] * opts.scale)
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "8"
    scene.render.image_settings.compression = 40


def make_material():
    image = bpy.data.images.load(str(CACHE / "atlas.png"))
    material = bpy.data.materials.new("voxel")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = image
    tex.interpolation = "Closest"
    attr = nodes.new("ShaderNodeAttribute")
    attr.attribute_name = "tint"
    mix = nodes.new("ShaderNodeVectorMath")
    mix.operation = "MULTIPLY"
    links.new(tex.outputs["Color"], mix.inputs[0])
    links.new(attr.outputs["Color"], mix.inputs[1])
    links.new(mix.outputs["Vector"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.82
    return material


def make_object(name, material):
    payload = np.load(CACHE / f"mesh_{name}.npz")
    verts, uvs, colors = payload["verts"], payload["uvs"], payload["colors"]
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
    layer = mesh.uv_layers.new(name="uv")
    layer.uv.foreach_set("vector", uvs.reshape(-1))
    attribute = mesh.color_attributes.new("tint", "FLOAT_COLOR", "POINT")
    rgba = np.concatenate([colors.reshape(-1, 3), np.ones((n, 1), np.float32)], axis=1)
    attribute.data.foreach_set("color", rgba.reshape(-1))
    mesh.materials.append(material)
    obj = bpy.data.objects.new(name, mesh)
    scene.collection.objects.link(obj)
    print(name, quads, "quads", flush=True)
    return obj


def make_camera():
    cam = bpy.data.cameras.new("cam")
    cam.sensor_fit = "HORIZONTAL"
    cam.sensor_width = 36.0
    cam.lens = data["camera"]["lens"]
    cam.clip_end = 3000
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


def make_sun(name, direction, color, strength, angle):
    light = bpy.data.lights.new(name, "SUN")
    light.color = color
    light.energy = strength
    light.angle = math.radians(angle)
    obj = bpy.data.objects.new(name, light)
    scene.collection.objects.link(obj)
    obj.rotation_mode = "QUATERNION"
    obj.rotation_quaternion = Vector(direction).normalized().to_track_quat("Z", "Y")
    return obj


def make_world():
    world = bpy.data.worlds.new("sky")
    world.use_nodes = True
    scene.world = world
    nodes = world.node_tree.nodes
    links = world.node_tree.links
    background = next(n for n in nodes if n.type == "BACKGROUND")
    coord = nodes.new("ShaderNodeTexCoord")
    split = nodes.new("ShaderNodeSeparateXYZ")
    ramp = nodes.new("ShaderNodeValToRGB")
    links.new(coord.outputs["Generated"], split.inputs[0])
    links.new(split.outputs["Z"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], background.inputs["Color"])
    return background, ramp


def lin(value):
    value = value.lstrip("#")
    out = []
    for i in (0, 2, 4):
        c = int(value[i:i + 2], 16) / 255
        out.append(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
    return (*out, 1.0)


LIGHTINGS = {
    "night": dict(horizon="#2c5468", zenith="#14305a", world=1.7, moon=2.2, sun=0.0, look="night"),
    "pre": dict(horizon="#e88c7a", zenith="#5a5aa8", world=1.25, moon=0.0, sun=0.0),
    "sun": dict(horizon="#ffc58a", zenith="#88b2ec", world=1.5, moon=0.0, sun=6.5),
    "fire": dict(horizon="#000000", zenith="#000000", world=0.0, moon=0.0, sun=0.0),
}


def main():
    setup_cycles()
    scene.view_settings.view_transform = "AgX"
    looks = [i.identifier for i in scene.view_settings.bl_rna.properties["look"].enum_items]
    print("looks", looks[:12], flush=True)
    wanted = next((l for l in looks if "Medium High Contrast" in l), "None")
    material = make_material()
    objects = {name: make_object(name, material) for name in ("far", "mid", "wreck", "fg")}
    make_camera()
    background, ramp = make_world()
    moon = make_sun("moon", data["moon"], (0.52, 0.84, 0.95), 1.0, 1.0)
    # Le soleil est bas : on le relève un peu pour modeler le relief, sans perdre le contre-jour.
    sun_dir = Vector(data["sun"]) + Vector((-0.12, -0.25, 0.32))
    sun = make_sun("sun", sun_dir, (1.0, 0.68, 0.40), 1.0, 2.5)
    moon_dir = Vector(data["moon"]) + Vector((-0.1, -0.1, 0.5))
    moon.rotation_quaternion = moon_dir.normalized().to_track_quat("Z", "Y")
    fires = []
    for i, (x, y, z, power) in enumerate(data["fires"]):
        light = bpy.data.lights.new(f"fire{i}", "POINT")
        light.color = (1.0, 0.42, 0.13)
        light.shadow_soft_size = 2.0
        obj = bpy.data.objects.new(f"fire{i}", light)
        obj.location = (x, y, z)
        scene.collection.objects.link(obj)
        fires.append((obj, light, power))

    jobs = []
    for layer in ("far", "mid", "fg"):
        for light in ("night", "pre", "sun"):
            jobs.append((f"{layer}_{light}", layer, light, True))
    jobs.append(("mid_fire", "mid", "fire", True))
    jobs.append(("fg_fire", "fg", "fire", True))
    jobs.append(("precrash_mid_night", "mid", "night", False))
    if opts.only:
        allowed = set(opts.only.split(","))
        jobs = [j for j in jobs if j[0] in allowed]

    for file_name, layer, lighting, with_wreck in jobs:
        cfg = LIGHTINGS[lighting]
        ramp.color_ramp.elements[0].position = 0.45
        ramp.color_ramp.elements[0].color = lin(cfg["horizon"])
        ramp.color_ramp.elements[1].position = 0.75
        ramp.color_ramp.elements[1].color = lin(cfg["zenith"])
        background.inputs["Strength"].default_value = cfg["world"]
        moon.data.energy = cfg["moon"]
        sun.data.energy = cfg["sun"]
        for obj, light, power in fires:
            light.energy = power * 170 if lighting == "fire" else 0.0
        for name, obj in objects.items():
            is_shown = name == layer or (name == "wreck" and layer == "mid" and with_wreck)
            obj.visible_camera = is_shown
            obj.hide_render = name == "wreck" and not with_wreck
        # Les feux doivent rester additifs : pas de courbe qui écrase leur intensité.
        scene.view_settings.view_transform = "Standard" if lighting == "fire" else "AgX"
        scene.view_settings.look = "None" if lighting == "fire" else wanted
        scene.view_settings.exposure = 0.0
        scene.render.filepath = str(OUT / f"{file_name}.png")
        bpy.ops.render.render(write_still=True)
        print("ok", file_name, flush=True)
    if opts.save:
        bpy.ops.wm.save_as_mainfile(filepath=opts.save)


main()
