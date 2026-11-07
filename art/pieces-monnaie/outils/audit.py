"""Contrôle des fichiers livrés. Usage : audit.py DOSSIER MODID"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

root, modid = Path(sys.argv[1]), sys.argv[2]
LEGAL = {-45, -22.5, 22.5, 45}
bad = 0


def fail(msg):
    global bad
    bad += 1
    print("  ÉCHEC", msg)


for v in ["copper", "silver", "gold", "emerald", "ruby"]:
    name = f"{v}_coin"
    j = json.loads((root / "assets" / modid / "models" / "item" / f"{name}.json").read_text(encoding="utf-8"))
    png = root / "assets" / modid / "textures" / "item" / f"{name}.png"
    im = Image.open(png)
    bb = json.loads((root / "blockbench" / f"{name}.bbmodel").read_text(encoding="utf-8"))
    src = bb["textures"][0].get("source", "")
    embedded = src.startswith("data:image")
    if embedded:
        emb = Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")
        same = emb.tobytes() == im.convert("RGBA").tobytes()
    else:
        same = False
    refs = j["textures"]
    faces = sum(len(e["faces"]) for e in j["elements"])
    minx = min(e["from"][0] for e in j["elements"])
    maxx = max(e["to"][0] for e in j["elements"])
    miny = min(e["from"][1] for e in j["elements"])
    maxy = max(e["to"][1] for e in j["elements"])
    minz = min(e["from"][2] for e in j["elements"])
    maxz = max(e["to"][2] for e in j["elements"])
    print(f"{name}: v{j.get('format_version')} {len(j['elements'])} éléments, {faces} faces, x {minx}..{maxx} y {miny}..{maxy} z {minz}..{maxz}, png {im.size}, bbmodel v{bb.get('java_block_version')} texture intégrée={embedded} identique={same}, slots {sorted(j['display'])}")
    if j.get("format_version") != "1.9.0" or bb.get("java_block_version") != "1.9.0":
        fail("version")
    if refs != {"0": f"{modid}:item/{name}", "particle": f"{modid}:item/{name}"}:
        fail(f"textures {refs}")
    if im.size != (64, 64) or not same:
        fail("png")
    if set(j["display"]) != {"gui", "ground", "fixed", "head", "thirdperson_righthand", "firstperson_righthand"}:
        fail(f"display {sorted(j['display'])}")
    for e in j["elements"]:
        r = e.get("rotation")
        if r and (r["angle"] not in LEGAL or r["axis"] != "z"):
            fail(f"rotation {e['name']} {r}")
        for f, d in e["faces"].items():
            if d.get("texture") != "#0":
                fail(f"face {e['name']}.{f} {d}")
            if any(not 0 <= c <= 16 for c in d["uv"]):
                fail(f"uv {e['name']}.{f} {d['uv']}")
        if any(c < -16 or c > 32 for c in e["from"] + e["to"]):
            fail(f"limites {e['name']}")
print("échecs :", bad)
sys.exit(1 if bad else 0)
