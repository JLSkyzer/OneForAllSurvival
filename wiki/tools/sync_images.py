"""Copie dans wiki/img/ les textures des blocs et objets cités par wiki/data/*.json.

À relancer après chaque lot livré : python wiki/tools/sync_images.py
Seules les textures existantes dans le mod sont copiées ; une texture absente est signalée.
"""
import json
import shutil
import sys
from pathlib import Path

WIKI = Path(__file__).resolve().parent.parent
TEXTURES = WIKI.parent / "src" / "main" / "resources" / "assets" / "oneforallsurvival" / "textures"


def wanted():
    """Renvoie l'ensemble des (dossier, fichier) cités par les fiches."""
    out = set()
    for path in sorted((WIKI / "data").glob("*.json")):
        chapter = json.loads(path.read_text(encoding="utf-8"))
        for card in chapter.get("cards", []):
            image = card.get("image") or {}
            if image.get("type") == "block":
                for face in ("top", "side", "front"):
                    if image.get(face):
                        out.add(("block", image[face]))
            elif image.get("type") == "item" and image.get("file"):
                out.add(("item", image["file"]))
            for item in card.get("items", []) or []:
                if item.get("file"):
                    out.add(("item", item["file"]))
    return out


def main():
    missing = 0
    for folder, name in sorted(wanted()):
        source = TEXTURES / folder / name
        target = WIKI / "img" / folder / name
        if not source.is_file():
            print(f"absente : textures/{folder}/{name}")
            missing += 1
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    print(f"{len(wanted()) - missing} textures copiées, {missing} absentes")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
