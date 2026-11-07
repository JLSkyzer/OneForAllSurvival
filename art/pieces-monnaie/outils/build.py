"""Construit les cinq pièces dans Blockbench par le plugin MCP, puis les exporte.

Usage : build.py MODID DOSSIER_SORTIE [variante ...]
Ne ferme et ne modifie que les projets <variante>_coin créés ici.
"""
import json
import runpy
import sys
from pathlib import Path

MODID, OUT = sys.argv[1], Path(sys.argv[2]).resolve()
WANTED = sys.argv[3:] or ["copper", "silver", "gold", "emerald", "ruby"]
sys.argv = ["bb.py"]
ns = runpy.run_path(str(Path(__file__).with_name("bb.py")))
call = ns["call"]

MODELS = OUT / "assets" / MODID / "models" / "item"
TEXTURES = OUT / "assets" / MODID / "textures" / "item"
BB = OUT / "blockbench"
MODELS.mkdir(parents=True, exist_ok=True)
BB.mkdir(parents=True, exist_ok=True)

Z0, Z1 = 7.25, 8.75   # épaisseur 1,5
RELIEF = 0.25
EDGE = [10, 10, 11.4142, 11.5]            # tranche des coins coupés
BAR_TOP = [12, 0, 13.4142, 0.7071]        # face des coins coupés, arête claire vers l'extérieur
BAR_BOTTOM = [12, 0.7071, 13.4142, 0]
SIDE_V = [10, 1, 11.5, 9]                 # tranche verticale
SIDE_H = [1, 10, 9, 11.5]                 # tranche horizontale
LETTER_SIDE = [12, 2, 13, 3]
LOGO = [(11, 11, 15, 25), (11, 25, 20, 29), (21, 11, 29, 15), (23, 15, 27, 25), (21, 25, 29, 29)]
LOGO_NAMES = ["l_stem", "l_foot", "i_top", "i_stem", "i_bottom"]

DISPLAY = {
    "gui": dict(rotation=[18, -28, 0], translation=[0, 0, 0], scale=[1.35, 1.35, 1.35]),
    "ground": dict(rotation=[0, 0, 0], translation=[0, 3, 0], scale=[0.6, 0.6, 0.6]),
    "fixed": dict(rotation=[0, 180, 0], translation=[0, 0, 0], scale=[1.1, 1.1, 1.1]),
    "head": dict(rotation=[0, 180, 0], translation=[0, 8.5, 4.6], scale=[0.9, 0.9, 0.9]),
    "thirdperson_righthand": dict(rotation=[70, 0, 0], translation=[0, 2, -1], scale=[0.6, 0.6, 0.6]),
    "firstperson_righthand": dict(rotation=[8, -58, 12], translation=[-2, 5.5, 0], scale=[0.62, 0.62, 0.62]),
}


def cubes():
    out = [
        dict(name="plate_center", a=[4, 3, Z0], b=[12, 13, Z1], faces=dict(south=[1, 0, 9, 10], north=[1, 0, 9, 10], up=SIDE_H, down=SIDE_H)),
        dict(name="plate_left", a=[3, 4, Z0], b=[4, 12, Z1], faces=dict(south=[0, 1, 1, 9], north=[9, 1, 10, 9], west=SIDE_V)),
        dict(name="plate_right", a=[12, 4, Z0], b=[13, 12, Z1], faces=dict(south=[9, 1, 10, 9], north=[0, 1, 1, 9], east=SIDE_V)),
    ]
    half_l, half_d = 0.7071, 0.35355
    for name, cx, cy, angle, outer in [
        ("corner_top_right", 12.25, 12.25, -45, "up"),
        ("corner_top_left", 3.75, 12.25, 45, "up"),
        ("corner_bottom_right", 12.25, 3.75, 45, "down"),
        ("corner_bottom_left", 3.75, 3.75, -45, "down"),
    ]:
        front = BAR_TOP if outer == "up" else BAR_BOTTOM
        out.append(dict(
            name=name, a=[cx - half_l, cy - half_d, Z0 + 0.01], b=[cx + half_l, cy + half_d, Z1 - 0.01],
            origin=[cx, cy, 8], rotation=[0, 0, angle], faces={"south": front, "north": front, outer: EDGE},
        ))
    for (x0, y0, x1, y1), name in zip(LOGO, LOGO_NAMES):
        uv = [x0 / 4, y0 / 4, x1 / 4, y1 / 4]
        fx, tx, fy, ty = 3 + x0 / 4, 3 + x1 / 4, 13 - y1 / 4, 13 - y0 / 4
        sides = {s: LETTER_SIDE for s in ("up", "down", "east", "west")}
        out.append(dict(name=f"logo_front_{name}", a=[fx, fy, Z1], b=[tx, ty, Z1 + RELIEF], faces={"south": uv, **sides}))
        out.append(dict(name=f"logo_back_{name}", a=[16 - tx, fy, Z0 - RELIEF], b=[16 - fx, ty, Z0], faces={"north": uv, **sides}))
    return out


def must(name, args):
    r = call(name, args)
    if r.startswith(("ISERROR", "ERREUR")):
        raise SystemExit(f"{name} {json.dumps(args)[:200]} -> {r[:300]}")
    return r


def close_mine(project):
    call("risky_eval", {"code": f"(async()=>{{const p=ModelProject.all.find(p=>p.name==='{project}'); if(p){{p.saved=true; await p.close(true);}} return ModelProject.all.map(p=>p.name)}})()"})


for variant in WANTED:
    project = f"{variant}_coin"
    close_mine(project)
    must("create_project", {"name": project, "format": "java_block"})
    must("risky_eval", {"code": 'Project.java_block_version = "1.9.0"'})
    must("set_mode", {"mode_id": "edit"})
    png = (TEXTURES / f"{project}.png").as_posix()
    must("create_texture", {"name": project, "width": 64, "height": 64, "data": png})
    must("risky_eval", {"code": f'(()=>{{const t=Texture.all[0]; t.namespace="{MODID}"; t.folder="item"; t.name="{project}.png"; return t.name}})()'})
    specs = cubes()
    for c in specs:
        el = {"name": c["name"], "from": c["a"], "to": c["b"]}
        if "rotation" in c:
            el.update(origin=c["origin"], rotation=c["rotation"])
        must("place_cube", {"elements": [el]})
        must("set_cube_uv", {"id": c["name"], "faces": {f: {"uv": uv, "texture": f"{project}.png"} for f, uv in c["faces"].items()}})
    # Les faces non décrites sont cachées dans la géométrie : on les retire.
    keep = {c["name"]: list(c["faces"]) for c in specs}
    must("risky_eval", {"code": f"(()=>{{const keep={json.dumps(keep)}; let n=0; Cube.all.forEach(c=>{{for(const f in c.faces){{ if(!keep[c.name].includes(f)){{c.faces[f].texture=null; n++}} }}}}); Canvas.updateAll(); return n}})()"})
    for slot, d in DISPLAY.items():
        must("set_display_transform", {"slot": slot, **d})
    must("export_model", {"codec_id": "java_block", "path": (MODELS / f"{project}.json").as_posix(), "overwrite": True})
    must("export_model", {"codec_id": "project", "path": (BB / f"{project}.bbmodel").as_posix(), "overwrite": True})
    # L'exporteur n'écrit pas la texture de particules.
    path = MODELS / f"{project}.json"
    j = json.loads(path.read_text(encoding="utf-8"))
    j["textures"]["particle"] = j["textures"]["0"]
    path.write_text(json.dumps(j, indent="\t", ensure_ascii=False) + "\n", encoding="utf-8")
    print("ok", project, len(j["elements"]), "éléments", j["textures"], j.get("format_version"))
