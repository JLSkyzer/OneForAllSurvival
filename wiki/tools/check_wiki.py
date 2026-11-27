"""Contrôle le contenu du wiki : JSON, blocs connus et complets, images, objets, recettes, repères, anciennes fiches
couvertes, limites de texte et de poids. Sort en erreur (code 1) au moindre manque.

    python wiki/tools/check_wiki.py

Bibliothèque standard ; Pillow sert seulement à reconnaître une animation WebP (sinon : avertissement).
Référence des anciennes fiches « En jeu » : wiki/tools/fiches-en-jeu.json (relevé des chapitres en version 1,
le 27/11/2026). Un chapitre passé en version 2 doit couvrir (champ `covers`) chacune de ses anciennes fiches.
"""
import json
import re
import sys
from pathlib import Path

WIKI = Path(__file__).resolve().parent.parent
DATA = WIKI / "data"

LEAD_MAX = 160
TEXTE_MAX = 220
FIXE_MAX = 250 * 1024
ANIM_MAX = int(1.5 * 1024 * 1024)
WIKI_MAX = 80 * 1024 * 1024
TONS = {"ok", "off", "bad", "info"}

# Champs obligatoires de chaque bloc (format : docs/superpowers/plans/2026-11-27-refonte-wiki.md).
BLOCS = {
    "hero": ["img", "alt", "points"],
    "step": ["title", "blocks"],
    "row": ["blocks"],
    "shot": ["img", "alt"],
    "clip": ["img", "alt"],
    "recipe": ["item"],
    "items": ["list"],
    "states": ["list"],
    "phones": ["list"],
    "player": ["steps"],
    "flow": ["nodes"],
    "numbers": ["list"],
    "problems": ["list"],
    "pass": ["item", "text"],
    "power": ["standby", "perCard", "perCall", "speeds", "feAt256"],
    "range": ["item", "bars", "max"],
    "chunk": ["item", "text"],
    "text": ["text"],
}

erreurs, avertissements = [], []


def err(ou, msg):
    erreurs.append(f"{ou} : {msg}")


def avert(ou, msg):
    avertissements.append(f"{ou} : {msg}")


def charge(p, defaut=None):
    if not p.is_file():
        if defaut is None:
            err(p.relative_to(WIKI), "fichier absent")
        return defaut
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except (ValueError, UnicodeDecodeError) as e:
        err(p.relative_to(WIKI), f"JSON illisible ({e})")
        return defaut


def est_anime(p):
    try:
        from PIL import Image
    except ImportError:
        return None
    try:
        with Image.open(p) as im:
            return getattr(im, "n_frames", 1) > 1
    except OSError:
        return None


images_vues = {}


def image(ou, src, anim_attendue=False):
    if not isinstance(src, str) or not src:
        err(ou, "image sans chemin")
        return
    if src.startswith(("http:", "https:", "/", "..")) or "\\" in src:
        err(ou, f"chemin d'image non relatif au wiki : {src}")
        return
    p = WIKI / src
    if not p.is_file():
        err(ou, f"image absente : {src}")
        return
    if src.startswith("shots/"):
        if p.suffix.lower() != ".webp":
            err(ou, f"image en {p.suffix} (WebP attendu) : {src}")
        anime = est_anime(p)
        taille = p.stat().st_size
        if anime is None:
            avert(ou, f"animation non vérifiable (Pillow absent) : {src}")
        elif anime and taille > ANIM_MAX:
            err(ou, f"animation de {taille // 1024} Ko (max 1,5 Mo) : {src}")
        elif not anime and taille > FIXE_MAX:
            err(ou, f"image fixe de {taille // 1024} Ko (max 250 Ko) : {src}")
        if anim_attendue and anime is False:
            avert(ou, f"bloc clip sur une image fixe : {src}")
    images_vues[src] = True


def texte(ou, s, limite=None, champ="texte"):
    if not isinstance(s, str) or not s.strip():
        err(ou, f"{champ} vide")
        return
    if limite and len(s) > limite:
        err(ou, f"{champ} de {len(s)} caractères (max {limite}) : « {s[:60]}… »")
    if s.count("`") % 2:
        err(ou, f"accent grave non refermé dans le {champ}")
    if s.count("**") % 2:
        err(ou, f"« ** » non refermé dans le {champ}")


class Contexte:
    def __init__(self, objets, recettes, reperes, cibles):
        self.objets, self.recettes, self.reperes, self.cibles = objets, recettes, reperes, cibles
        self.types = set()


def objet(ctx, ou, ident):
    if not isinstance(ident, str) or ":" not in ident:
        err(ou, f"identifiant d'objet invalide : {ident!r}")
    elif ident not in ctx.objets:
        err(ou, f"objet inconnu de data/objets.json : {ident}")


def point(ctx, ou, p, src, avec_texte=None):
    if not isinstance(p, dict):
        err(ou, "repère illisible")
        return
    if "at" in p:
        if p["at"] not in (ctx.reperes.get(src) or {}):
            err(ou, f"repère « {p['at']} » absent de data/reperes.json pour {src}")
    elif not (isinstance(p.get("x"), (int, float)) and isinstance(p.get("y"), (int, float))):
        err(ou, "repère sans `at` ni x, y")
    elif not (0 <= p["x"] <= 100 and 0 <= p["y"] <= 100):
        err(ou, f"repère hors de l'image ({p['x']}, {p['y']})")
    for k in avec_texte or []:
        texte(ou, p.get(k), champ=k)


def lien(ctx, ou, href):
    if not isinstance(href, str) or not href.startswith("#"):
        err(ou, f"lien qui n'est pas une ancre « # » : {href!r}")
    elif href[1:].split("/")[0] not in ctx.cibles and href[1:] not in ctx.cibles:
        err(ou, f"lien vers une page inconnue : {href}")


def liste(ou, b, k="list"):
    v = b.get(k)
    if not isinstance(v, list) or not v:
        err(ou, f"`{k}` vide")
        return []
    return [x for x in v if isinstance(x, dict) or err(ou, f"élément de `{k}` illisible")]


def bloc(ctx, ou, b):
    if not isinstance(b, dict) or "type" not in b:
        err(ou, "bloc sans type")
        return
    t = b["type"]
    ou = f"{ou} [{t}]"
    if t not in BLOCS:
        err(ou, "type de bloc inconnu")
        return
    ctx.types.add(t)
    for k in BLOCS[t]:
        if k not in b:
            err(ou, f"champ `{k}` manquant")
    if t in ("shot", "clip", "hero"):
        image(ou, b.get("img"), anim_attendue=(t == "clip"))
        texte(ou, b.get("alt"), champ="alt")
    if b.get("note") is not None:
        texte(ou, b["note"], TEXTE_MAX, "note")
    if t == "text":
        texte(ou, b.get("text"), TEXTE_MAX)
    elif t == "hero":
        for p in b.get("points") or []:
            point(ctx, ou, p, b.get("img"), ["title", "text"])
    elif t == "shot":
        for m in b.get("marks") or []:
            point(ctx, ou, m, b.get("img"), ["text"])
    elif t in ("step", "row"):
        if t == "step":
            texte(ou, b.get("title"), champ="titre")
            if b.get("actor") not in (None, "toi", "autre"):
                err(ou, f"actor « {b.get('actor')} » (toi ou autre)")
        if not b.get("blocks"):
            err(ou, "aucun bloc dedans")
        for i, x in enumerate(b.get("blocks") or []):
            bloc(ctx, f"{ou}.{i}", x)
    elif t == "recipe":
        objet(ctx, ou, b.get("item"))
        if not any(r.get("sortie") == b.get("item") for r in ctx.recettes):
            err(ou, f"aucune recette de {b.get('item')} dans data/recettes.json")
    elif t == "items":
        for x in liste(ou, b):
            objet(ctx, ou, x.get("item"))
    elif t == "states":
        for x in liste(ou, b):
            image(ou, x.get("img"))
            texte(ou, x.get("alt"), champ="alt")
            texte(ou, x.get("title"), champ="titre")
            if x.get("tone") not in TONS:
                err(ou, f"tone « {x.get('tone')} » (ok, off, bad ou info)")
    elif t == "phones":
        for x in liste(ou, b):
            image(ou, x.get("img"))
            texte(ou, x.get("alt"), champ="alt")
            for p in x.get("points") or []:
                point(ctx, ou, p, x.get("img"))
    elif t == "player":
        for x in liste(ou, b, "steps"):
            image(ou, x.get("img"))
            texte(ou, x.get("alt"), champ="alt")
            texte(ou, x.get("label"), champ="label")
            for p in x.get("points") or []:
                point(ctx, ou, p, x.get("img"))
    elif t == "flow":
        for x in liste(ou, b, "nodes"):
            if x.get("item"):
                objet(ctx, ou, x["item"])
            elif x.get("img"):
                image(ou, x["img"])
            if not x.get("label") and not x.get("item"):
                err(ou, "nœud sans label")
    elif t == "numbers":
        for x in liste(ou, b):
            if not x.get("k") or x.get("v") in (None, ""):
                err(ou, "chiffre sans k ou v")
            if x.get("item"):
                objet(ctx, ou, x["item"])
    elif t == "problems":
        for x in liste(ou, b):
            texte(ou, x.get("msg"), champ="msg")
            texte(ou, x.get("text"), TEXTE_MAX)
            if x.get("link"):
                lien(ctx, ou, x["link"] if isinstance(x["link"], str) else x["link"].get("href"))
    elif t in ("pass", "chunk"):
        objet(ctx, ou, b.get("item"))
        texte(ou, b.get("text"), TEXTE_MAX)
    elif t == "power":
        if not isinstance(b.get("speeds"), list) or not b.get("speeds"):
            err(ou, "`speeds` vide")
    elif t == "range":
        objet(ctx, ou, b.get("item"))
        bars = b.get("bars")
        if not isinstance(bars, list) or not bars or bars != sorted(bars):
            err(ou, "`bars` : seuils croissants attendus")
        elif isinstance(b.get("max"), (int, float)) and b["max"] < bars[-1]:
            err(ou, "`max` plus petit que le dernier seuil")


def main():
    accueil = charge(DATA / "accueil.json")
    objets = charge(DATA / "objets.json", {})
    recettes = charge(DATA / "recettes.json", [])
    reperes = charge(DATA / "reperes.json", {})
    if not accueil:
        return fin()

    for ident, o in objets.items():
        ou = f"objets.json {ident}"
        if not o.get("nom"):
            err(ou, "nom vide")
        attendu = "img/objets/" + ident.replace(":", "__").replace("/", "__") + ".png"
        if o.get("icone") != attendu:
            err(ou, f"icône « {o.get('icone')} » (attendu {attendu})")
        else:
            image(ou, o["icone"])
    for i, r in enumerate(recettes):
        ou = f"recettes.json #{i} {r.get('sortie')}"
        if r.get("poste") not in ("table", "etabli"):
            err(ou, f"poste « {r.get('poste')} » (table ou etabli)")
        ids = [r.get("sortie")] + [x for l in r.get("grille") or [] for x in l if x] + [g.get("item") for g in r.get("ingredients") or []]
        for x in ids:
            if x not in objets:
                err(ou, f"objet inconnu de objets.json : {x}")
        compte = {}
        for l in r.get("grille") or []:
            for x in l:
                if x:
                    compte[x] = compte.get(x, 0) + 1
        if compte != {g.get("item"): g.get("nombre") for g in r.get("ingredients") or []}:
            err(ou, "les ingrédients ne correspondent pas à la grille")
    for src in reperes:
        if not (WIKI / src).is_file():
            err(f"reperes.json {src}", "image absente")

    chapitres = accueil.get("chapters") or []
    caches = accueil.get("hidden") or []
    data = {}
    for ch in chapitres + caches:
        d = charge(DATA / f"{ch}.json")
        if d is not None:
            data[ch] = d
    for p in sorted(DATA.glob("*.json")):
        if p.stem not in data and p.stem not in ("accueil", "objets", "recettes", "reperes"):
            err(p.name, "chapitre absent de accueil.json (chapters ou hidden)")
    if set(chapitres) & set(caches):
        err("accueil.json", "chapitre à la fois visible et caché")

    # Pages joignables : chapitres, articles, anciennes fiches couvertes ou encore en version 1.
    cibles = {"accueil"} | set(data)
    for ch, d in data.items():
        if d.get("v") == 2:
            for a in d.get("articles") or []:
                cibles.add(f"{ch}/{a.get('id')}")
                cibles.update(a.get("covers") or [])
        else:
            cibles.update(c.get("id") for c in d.get("cards") or [])
    ctx = Contexte(objets, recettes, reperes, cibles)

    couverts = {}
    for ch, d in data.items():
        ou = f"{ch}.json"
        if d.get("id") != ch:
            err(ou, f"id « {d.get('id')} » différent du nom du fichier")
        if not d.get("title"):
            err(ou, "titre vide")
        if d.get("v") == 2:
            if d.get("icon"):
                objet(ctx, ou, d["icon"])
            if d.get("cover"):
                image(ou, d["cover"])
            if d.get("tagline"):
                texte(ou, d["tagline"], LEAD_MAX * 2, "tagline")
            ids = set()
            for a in d.get("articles") or []:
                oa = f"{ch}/{a.get('id')}"
                if not re.fullmatch(r"[a-z0-9-]+", str(a.get("id"))):
                    err(oa, "id d'article invalide (a-z, 0-9, -)")
                if a.get("id") in ids:
                    err(oa, "id d'article en double")
                ids.add(a.get("id"))
                texte(oa, a.get("title"), champ="titre")
                texte(oa, a.get("lead"), LEAD_MAX, "chapeau")
                if a.get("cover"):
                    image(oa, a["cover"])
                for c in a.get("covers") or []:
                    if c in couverts:
                        err(oa, f"ancienne fiche « {c} » déjà couverte par {couverts[c]}")
                    couverts[c] = oa
                for k in a.get("chips") or []:
                    if not k.get("k") or not k.get("v"):
                        err(oa, "puce sans k ou v")
                for s in a.get("staff") or []:
                    texte(oa, s, champ="note d'équipe")
                if not a.get("blocks"):
                    err(oa, "article sans bloc")
                for i, b in enumerate(a.get("blocks") or []):
                    bloc(ctx, f"{oa} bloc {i}", b)
                if not any(b.get("type") in ("hero", "shot", "clip", "states", "phones", "player", "step", "row") for b in a.get("blocks") or []):
                    avert(oa, "aucune capture : un article « En jeu » en montre au moins une par notion")
            for p in d.get("planned") or []:
                if not p.get("name") or not p.get("id"):
                    err(f"{ch} prévu", "élément prévu sans id ou nom")
        else:
            if not isinstance(d.get("cards"), list):
                err(ou, "ni `articles` (version 2) ni `cards` (version 1)")
                continue
            for c in d["cards"]:
                im = c.get("image") or {}
                fichiers = [("block", im.get(f)) for f in ("top", "side", "front") if im.get("type") == "block" and im.get(f)]
                if im.get("type") == "item":
                    fichiers.append(("item", im.get("file")))
                fichiers += [("item", it.get("file")) for it in c.get("items") or [] if it.get("file")]
                for dossier, f in fichiers:
                    if not (WIKI / "img" / dossier / f).is_file():
                        err(f"{ch}/{c.get('id')}", f"texture absente : img/{dossier}/{f}")

    # Anciennes fiches « En jeu » : celles d'un chapitre passé en version 2 doivent être couvertes.
    ref = charge(WIKI / "tools" / "fiches-en-jeu.json", {})
    for ch, ids in ref.items():
        if ch in data and data[ch].get("v") == 2:
            for i in ids:
                if i not in couverts:
                    err(f"{ch}.json", f"ancienne fiche « {i} » couverte par aucun article (`covers`)")

    # Accueil.
    for a in accueil.get("actions") or []:
        ou = f"accueil.json « {a.get('title')} »"
        lien(ctx, ou, a.get("href"))
        for m in a.get("imgs") or []:
            image(ou, m.get("img"))
            texte(ou, m.get("alt"), champ="alt")
        for x in a.get("path") or []:
            if x.get("item"):
                objet(ctx, ou, x["item"])
    for v in accueil.get("seen") or []:
        ou = f"accueil.json vu « {v.get('title')} »"
        lien(ctx, ou, v.get("href"))
        image(ou, v.get("img"))
    for ch, src in (accueil.get("chapterCovers") or {}).items():
        if ch not in data:
            err("accueil.json chapterCovers", f"chapitre inconnu : {ch}")
        image(f"accueil.json couverture {ch}", src)

    # Le chapitre d'essai prouve chaque type de bloc.
    if "essai" in data:
        vus = set()

        def parcourt(b):
            if isinstance(b, dict):
                vus.add(b.get("type"))
                for x in b.get("blocks") or []:
                    parcourt(x)
        for a in data["essai"].get("articles") or []:
            for b in a.get("blocks") or []:
                parcourt(b)
        manque = set(BLOCS) - vus
        if manque:
            err("essai.json", "types de blocs non montrés : " + ", ".join(sorted(manque)))

    # Interdits et poids.
    for p in sorted(DATA.glob("*.json")):
        if re.search(r"solar\s*town", p.read_text(encoding="utf-8"), re.I):
            err(p.name, "« SolarTown » est interdit")
    for p in sorted((WIKI / "shots").rglob("*")) if (WIKI / "shots").is_dir() else []:
        if p.is_file():
            src = p.relative_to(WIKI).as_posix()
            if src not in images_vues:
                avert(src, "image publiée que rien n'utilise")
    total = sum(p.stat().st_size for p in WIKI.rglob("*") if p.is_file() and "tools" not in p.relative_to(WIKI).parts)
    if total > WIKI_MAX:
        err("wiki", f"{total / 1048576:.1f} Mo (max 80 Mo)")
    print(f"Wiki : {len(data)} chapitres, {len(images_vues)} images vérifiées, {total / 1048576:.1f} Mo.")
    return fin()


def fin():
    for a in avertissements:
        print("avertissement  " + a)
    for e in erreurs:
        print("ERREUR  " + e)
    print(f"{len(erreurs)} erreur(s), {len(avertissements)} avertissement(s).")
    return 1 if erreurs else 0


if __name__ == "__main__":
    sys.exit(main())
