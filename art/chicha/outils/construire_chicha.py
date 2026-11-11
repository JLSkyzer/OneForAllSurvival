"""Construit la chicha (bloc 1 x 1) dans Blender, l'exporte en OBJ et rend les aperçus.

Lancement, sans interface :
    blender --background --factory-startup --python art/chicha/outils/construire_chicha.py

Repère : 1 unité Blender = 1 bloc. Le bloc occupe x dans [0, 1], y dans [-1, 0], z dans [0, 1] :
à l'export (haut = Y, avant = -Z), cela donne x, y, z dans [0, 1] dans l'OBJ. L'avant du bloc (nord,
-Z dans Minecraft) est +Y dans Blender : la prise du tuyau y pointe.
"""
import math
import random
from pathlib import Path

import bmesh
import bpy
from mathutils import Vector

ICI = Path(__file__).resolve().parent
RACINE = ICI.parent
SORTIE_MODELE = RACINE / "assets/oneforallsurvival/models/block"
SORTIE_TEXTURE = RACINE / "assets/oneforallsurvival/textures/block"
SORTIE_APERCUS = RACINE / "apercus"
SORTIE_BLEND = RACINE / "blender"
for d in (SORTIE_MODELE, SORTIE_TEXTURE, SORTIE_APERCUS, SORTIE_BLEND):
    d.mkdir(parents=True, exist_ok=True)

CX, CY = 0.5, -0.5  # centre du bloc dans Blender

# --- Texture : une grille de 4 x 4 cases de 8 pixels, une couleur par matière, bruitée ----------------
CASE, GRILLE = 8, 4
TAILLE = CASE * GRILLE
MATIERES = {
    # nom: (case x, case y, couleur RVBA 0-255, bruit)
    "verre": (0, 0, (46, 140, 92, 165), 14),
    "verre_clair": (1, 0, (120, 200, 150, 150), 12),
    "cuivre": (2, 0, (196, 112, 64, 255), 18),
    "cuivre_sombre": (3, 0, (128, 66, 40, 255), 14),
    "terre": (0, 1, (170, 86, 52, 255), 16),
    "charbon": (1, 1, (38, 34, 34, 255), 10),
    "braise": (2, 1, (240, 120, 40, 255), 30),
    "cuir": (3, 1, (92, 58, 38, 255), 12),
    "alu": (0, 2, (196, 198, 202, 255), 12),
}


def creer_texture():
    random.seed(7)
    img = bpy.data.images.new("chicha", TAILLE, TAILLE, alpha=True)
    px = [0.0] * (TAILLE * TAILLE * 4)
    for nom, (cx, cy, (r, g, b, a), bruit) in MATIERES.items():
        for y in range(CASE):
            for x in range(CASE):
                n = random.randint(-bruit, bruit)
                if nom == "braise" and random.random() < 0.25:
                    n += 40  # quelques points plus vifs
                gx, gy = cx * CASE + x, cy * CASE + y
                i = (gy * TAILLE + gx) * 4
                px[i:i + 4] = [max(0, min(255, c + n)) / 255 for c in (r, g, b)] + [a / 255]
    img.pixels = px
    chemin = SORTIE_TEXTURE / "chicha.png"
    img.filepath_raw = str(chemin)
    img.file_format = "PNG"
    img.save()
    return img, chemin


def materiau(img):
    mat = bpy.data.materials.new("chicha")
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = img
    tex.interpolation = "Closest"
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    bsdf.inputs["Roughness"].default_value = 0.6
    try:
        mat.surface_render_method = "BLENDED"
    except (AttributeError, TypeError):
        pass
    return mat


# --- Géométrie --------------------------------------------------------------------------------------
def tour(nom, profil, segments=12, ferme_bas=True, ferme_haut=True):
    """Surface de révolution autour de l'axe du bloc. profil = [(rayon, z), ...] de bas en haut."""
    bm = bmesh.new()
    anneaux = []
    for r, z in profil:
        anneaux.append([bm.verts.new((CX + r * math.cos(2 * math.pi * k / segments),
                                      CY + r * math.sin(2 * math.pi * k / segments), z))
                        for k in range(segments)])
    for a, b in zip(anneaux, anneaux[1:]):
        for k in range(segments):
            bm.faces.new((a[k], a[(k + 1) % segments], b[(k + 1) % segments], b[k]))
    if ferme_bas:
        bm.faces.new(list(reversed(anneaux[0])))
    if ferme_haut:
        bm.faces.new(anneaux[-1])
    me = bpy.data.meshes.new(nom)
    bm.normal_update()
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(nom, me)
    bpy.context.collection.objects.link(ob)
    return ob


def boite(nom, centre, taille):
    bpy.ops.mesh.primitive_cube_add(size=1, location=centre)
    ob = bpy.context.active_object
    ob.name = nom
    ob.scale = taille
    bpy.ops.object.transform_apply(scale=True)
    return ob


def tube_horizontal(nom, depart, direction, longueur, rayon, segments=8):
    d = Vector(direction).normalized()
    bpy.ops.mesh.primitive_cylinder_add(vertices=segments, radius=rayon, depth=longueur,
                                        location=Vector(depart) + d * (longueur / 2))
    ob = bpy.context.active_object
    ob.name = nom
    ob.rotation_euler = d.to_track_quat("Z", "Y").to_euler()
    bpy.ops.object.transform_apply(rotation=True)
    return ob


def tuyau_repos():
    """Tuyau en cuir qui part de la prise et s'enroule contre le vase."""
    cu = bpy.data.curves.new("tuyau_repos", "CURVE")
    cu.dimensions = "3D"
    cu.bevel_depth = 0.022
    cu.bevel_resolution = 1
    cu.resolution_u = 4
    sp = cu.splines.new("BEZIER")
    pts = [
        (CX, CY + 0.17, 0.52),          # bout de la prise
        (CX + 0.10, CY + 0.30, 0.42),
        (CX + 0.32, CY + 0.12, 0.22),
        (CX + 0.30, CY - 0.18, 0.10),
        (CX + 0.02, CY - 0.33, 0.05),
        (CX - 0.28, CY - 0.20, 0.04),
        (CX - 0.33, CY + 0.12, 0.04),   # embout posé au sol
    ]
    sp.bezier_points.add(len(pts) - 1)
    for bp, p in zip(sp.bezier_points, pts):
        bp.co = p
        bp.handle_left_type = bp.handle_right_type = "AUTO"
    ob = bpy.data.objects.new("tuyau_repos", cu)
    bpy.context.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.convert(target="MESH")
    return bpy.context.active_object


def construire():
    parties = {}
    # Vase : verre teinté, ventru, posé au centre.
    parties["vase"] = (tour("vase", [(0.13, 0.0), (0.19, 0.04), (0.25, 0.13), (0.24, 0.22),
                                     (0.16, 0.31), (0.08, 0.37), (0.07, 0.40)],
                            segments=12, ferme_haut=False), "verre")
    # Socle en cuivre sous le vase.
    parties["socle"] = (tour("socle", [(0.15, 0.0), (0.15, 0.02)], segments=12), "cuivre_sombre")
    # Collier en cuivre sur le col.
    parties["collier"] = (tour("collier", [(0.10, 0.37), (0.10, 0.43), (0.06, 0.45)], segments=12),
                          "cuivre")
    # Tige.
    parties["tige"] = (tour("tige", [(0.035, 0.44), (0.035, 0.80)], segments=8), "cuivre")
    # Deux bagues décoratives.
    parties["bague_basse"] = (tour("bague_basse", [(0.05, 0.50), (0.06, 0.53), (0.05, 0.56)],
                                   segments=8), "cuivre_sombre")
    parties["bague_haute"] = (tour("bague_haute", [(0.045, 0.72), (0.055, 0.745), (0.045, 0.77)],
                                   segments=8), "cuivre_sombre")
    # Plateau à charbon.
    parties["plateau"] = (tour("plateau", [(0.17, 0.655), (0.17, 0.67)], segments=12), "cuivre")
    # Foyer en terre cuite et papier alu.
    parties["foyer"] = (tour("foyer", [(0.045, 0.80), (0.085, 0.86), (0.09, 0.885)], segments=10),
                        "terre")
    parties["alu"] = (tour("alu", [(0.092, 0.885), (0.092, 0.89)], segments=10), "alu")
    # Charbon : deux cubes, et leur braise (visible quand le foyer est allumé).
    parties["charbon"] = (boite("charbon", (CX - 0.025, CY + 0.01, 0.91), (0.05, 0.05, 0.04)), "charbon")
    parties["charbon_2"] = (boite("charbon_2", (CX + 0.03, CY - 0.02, 0.905), (0.045, 0.045, 0.03)),
                            "charbon")
    parties["braise"] = (boite("braise", (CX, CY, 0.932), (0.075, 0.06, 0.006)), "braise")
    # Prise du tuyau, vers l'avant du bloc (+Y dans Blender, nord dans Minecraft).
    parties["prise"] = (tube_horizontal("prise", (CX, CY + 0.03, 0.52), (0, 1, 0), 0.14, 0.02), "cuivre")
    parties["tuyau_repos"] = (tuyau_repos(), "cuir")
    return parties


def uv_dans_case(ob, matiere):
    """Projette les UV de l'objet puis les ramène dans la case de sa matière (marge d'un pixel)."""
    bpy.ops.object.select_all(action="DESELECT")
    ob.select_set(True)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.0)
    bpy.ops.object.mode_set(mode="OBJECT")
    cx, cy = MATIERES[matiere][0], MATIERES[matiere][1]
    marge = 1 / TAILLE
    # Les pixels de Blender partent du bas de l'image, comme la coordonnée V : la ligne cy part du bas.
    x0, y0 = cx / GRILLE + marge, cy / GRILLE + marge
    cote = 1 / GRILLE - 2 * marge
    uv = ob.data.uv_layers.active.data
    for l in uv:
        u, v = l.uv
        l.uv = (x0 + u * cote, y0 + v * cote)


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    img, chemin_tex = creer_texture()
    mat = materiau(img)
    parties = construire()
    for nom, (ob, matiere) in parties.items():
        uv_dans_case(ob, matiere)
        ob.data.materials.clear()
        ob.data.materials.append(mat)
        if matiere not in ("charbon",) and nom not in ("braise",):
            bpy.context.view_layer.objects.active = ob
            ob.select_set(True)
            bpy.ops.object.shade_smooth()

    # Contrôle : tout tient dans le bloc.
    mins = [min((ob.matrix_world @ v.co)[i] for ob, _ in parties.values() for v in ob.data.vertices)
            for i in range(3)]
    maxs = [max((ob.matrix_world @ v.co)[i] for ob, _ in parties.values() for v in ob.data.vertices)
            for i in range(3)]
    print("BORNES", [round(m, 3) for m in mins], [round(m, 3) for m in maxs])
    assert mins[0] >= 0 and maxs[0] <= 1 and mins[1] >= -1 and maxs[1] <= 0 and mins[2] >= 0 and maxs[2] <= 1
    print("TRIANGLES", sum(len(ob.data.polygons) for ob, _ in parties.values()), "faces")

    # Export OBJ : un groupe (objet) par partie, pour que le code puisse cacher le tuyau ou la braise.
    bpy.ops.object.select_all(action="DESELECT")
    for ob, _ in parties.values():
        ob.select_set(True)
    chemin_obj = SORTIE_MODELE / "chicha.obj"
    bpy.ops.wm.obj_export(filepath=str(chemin_obj), export_selected_objects=True,
                          forward_axis="NEGATIVE_Z", up_axis="Y", export_materials=True,
                          export_normals=True, export_uv=True, export_triangulated_mesh=False,
                          path_mode="STRIP")
    # Le MTL pointe vers la texture du mod, à la façon du chargeur OBJ de Forge.
    (SORTIE_MODELE / "chicha.mtl").write_text(
        "newmtl chicha\nKd 1.0 1.0 1.0\nd 1.0\nmap_Kd oneforallsurvival:block/chicha\n", encoding="utf-8")

    bpy.ops.wm.save_as_mainfile(filepath=str(SORTIE_BLEND / "chicha.blend"))
    img.filepath = str(chemin_tex)
    rendre(parties)


def rendre(parties):
    sc = bpy.context.scene
    try:
        sc.render.engine = "CYCLES"
        sc.cycles.samples = 48
        sc.cycles.device = "CPU"
    except (TypeError, AttributeError):
        pass
    sc.render.resolution_x = sc.render.resolution_y = 720
    sc.render.film_transparent = False
    world = bpy.data.worlds.new("fond")
    world.use_nodes = True
    bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
    bg.inputs["Color"].default_value = (0.55, 0.62, 0.70, 1)
    bg.inputs["Strength"].default_value = 0.8
    sc.world = world

    # Sol et mur en « blocs » de pierre unie, pour juger la place dans le cube.
    for nom, centre, taille, coul in (("sol", (0.5, -0.5, -0.05), (3, 3, 0.1), (0.42, 0.40, 0.38, 1)),
                                       ("mur", (0.5, -1.05, 0.75), (3, 0.1, 1.6), (0.52, 0.48, 0.44, 1))):
        ob = boite(nom, centre, taille)
        m = bpy.data.materials.new(nom)
        m.use_nodes = True
        next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = coul
        ob.data.materials.append(m)
    # Contour du bloc, en fil.
    cadre = boite("cadre", (0.5, -0.5, 0.5), (1, 1, 1))
    mod = cadre.modifiers.new("fil", "WIREFRAME")
    mod.thickness = 0.008
    m = bpy.data.materials.new("cadre")
    m.use_nodes = True
    next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = (0.9, 0.9, 0.2, 1)
    cadre.data.materials.append(m)

    soleil = bpy.data.lights.new("soleil", "SUN")
    soleil.energy = 3.5
    ob = bpy.data.objects.new("soleil", soleil)
    ob.rotation_euler = (math.radians(50), math.radians(15), math.radians(35))
    sc.collection.objects.link(ob)
    braise = bpy.data.lights.new("lueur", "POINT")
    braise.energy = 3
    braise.color = (1.0, 0.5, 0.2)
    ob = bpy.data.objects.new("lueur", braise)
    ob.location = (0.5, -0.5, 0.97)
    sc.collection.objects.link(ob)

    cam = bpy.data.cameras.new("cam")
    cam.lens = 50
    camo = bpy.data.objects.new("cam", cam)
    sc.collection.objects.link(camo)
    sc.camera = camo

    def vue(nom, pos, cible=(0.5, -0.5, 0.45)):
        camo.location = pos
        camo.rotation_euler = (Vector(cible) - Vector(pos)).to_track_quat("-Z", "Y").to_euler()
        sc.render.filepath = str(SORTIE_APERCUS / f"{nom}.png")
        bpy.ops.render.render(write_still=True)

    vue("trois-quarts", (2.2, 1.6, 1.5))
    vue("face", (0.5, 2.4, 0.6))
    # Tuyau pris : on cache le tuyau au repos.
    parties["tuyau_repos"][0].hide_render = True
    vue("tuyau-pris", (2.2, 1.6, 1.5))


main()
