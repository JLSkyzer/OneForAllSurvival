"""Produit les visuels du wiki à partir des captures de l'atelier (runWikiStudio) et des exports du jeu.

    python wiki/tools/build_visuals.py [--partiel] [--config visuels.json] [--wiki dossier]

Lit wiki/tools/visuels.json (une entrée par visuel publié) et écrit, dans wiki/ :
  - shots/…webp : image fixe ou animée (recadrage en pixels de la source, largeur, coins arrondis au besoin) ;
  - img/objets/<espace>__<chemin>.png : les icônes rendues par le jeu (run-wiki/captures/_export/icones) ;
  - data/objets.json, data/recettes.json : noms français, icônes et recettes exportés par le jeu ;
  - data/reperes.json : les repères projetés à la prise, ramenés au recadrage ({"shots/…webp": {"nom": [x%, y%]}}).

Idempotent : un fichier n'est réécrit que si son contenu change. Une source absente est une erreur, sauf avec
--partiel (le visuel déjà publié et ses repères sont alors gardés tels quels ; reperes.json n'est que complété).
Poids : image fixe ≤ 250 Ko, animation ≤ 1,5 Mo (la qualité baisse par paliers pour tenir, sinon erreur), wiki
entier ≤ 80 Mo. Rien n'est écrit ni retiré tant qu'un contrôle échoue.

Seul ce que le wiki cite est publié : les recettes des blocs `recipe`, les objets nommés par un champ `item` ou
`icon` des fiches (data/*.json hors fichiers produits) et ceux de ces recettes, avec leurs icônes. Une capture de
l'atelier (run-wiki/captures/<chapitre>/…) est refusée si le dernier bilan (termine.json) signale un échec de ce
chapitre ou de l'atelier entier.

Champs d'une entrée de visuels.json :
  sortie     chemin sous wiki/ (shots/<chapitre>/<nom>.webp), obligatoire
  source     image PNG ou JPEG (chemin depuis la racine du dépôt) ; ou
  sequence   dossier d'images f0000.png… (ou f00001.jpg… des films) pour une animation
  recadrage  [x, y, largeur, hauteur] en pixels de la source (défaut : l'image entière)
  largeur    largeur de sortie en pixels (défaut : celle du recadrage ; jamais agrandie)
  coins      rayon des coins arrondis en pixels de sortie, fond transparent (le téléphone seul)
  qualite    qualité WebP de départ (défaut 86 pour une image fixe, 80 pour une animation)
  pas        séquence : une image sur « pas » (défaut 1)
  debut, fin séquence : indices de la première et de la dernière image gardées (défaut : toutes)
  duree      séquence : durée d'une image de sortie en ms (défaut 100 × pas)
  reperes    fichier .json de repères à utiliser (défaut : <source>.json s'il existe ; pour une séquence,
             <sequence>.json s'il existe) ; false : aucun repère (couverture, vignette recadrée)
"""
import argparse
import io
import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

WIKI = Path(__file__).resolve().parent.parent
RACINE = WIKI.parent
EXPORT = RACINE / "run-wiki" / "captures" / "_export"
CONFIG = WIKI / "tools" / "visuels.json"
FIXE_MAX = 250 * 1024
ANIM_MAX = 1536 * 1024
WIKI_MAX = 80 * 1024 * 1024
PALIERS = (0, 6, 12, 20, 30, 40)
PRODUITS = {"objets.json", "recettes.json", "reperes.json"}


class Erreur(Exception):
    pass


def ecrire_si_change(chemin: Path, contenu: bytes, ecrits: list) -> None:
    """Note l'écriture ; rien ne touche le disque avant que tous les contrôles soient passés (voir appliquer)."""
    if chemin.is_file() and chemin.read_bytes() == contenu:
        return
    ecrits.append((chemin, contenu))


def appliquer(ecrits: list) -> None:
    """Écrit (contenu en octets) ou retire (contenu None) chaque fichier noté."""
    for chemin, contenu in ecrits:
        if contenu is None:
            chemin.unlink()
        else:
            chemin.parent.mkdir(parents=True, exist_ok=True)
            chemin.write_bytes(contenu)


def echecs_de_l_atelier() -> list:
    """Les échecs du dernier bilan de l'atelier (run-wiki/captures/termine.json), ou [] s'il n'y en a pas."""
    bilan = RACINE / "run-wiki" / "captures" / "termine.json"
    if not bilan.is_file():
        return []
    donnees = json.loads(bilan.read_text(encoding="utf-8"))
    return list(donnees.get("echecs", [])) + ([] if donnees.get("fin") == "normale" else [f"fin : {donnees.get('fin')}"])


def source_refusee(chemin: str, echecs: list) -> str:
    """Une capture de l'atelier dont le chapitre a échoué au dernier bilan ne se publie pas : la raison, ou ''."""
    m = re.match(r"run-wiki/captures/([^/]+)/", chemin.replace("\\", "/"))
    if not m:
        return ""
    chapitre = m.group(1)
    for e in echecs:
        lieu = e.split(" : ", 1)[0]
        if lieu.startswith(chapitre + ":") or ":" not in lieu:
            return f"le dernier bilan de l'atelier signale un échec : {e}"
    return ""


def cites_par_le_wiki() -> tuple[set, set]:
    """Les objets cités par les fiches (champs item et icon) et les sorties des blocs recipe."""
    objets, recettes = set(), set()

    def parcourir(x):
        if isinstance(x, dict):
            for cle in ("item", "icon"):
                if isinstance(x.get(cle), str) and ":" in x[cle]:
                    objets.add(x[cle])
            if x.get("type") == "recipe" and isinstance(x.get("item"), str):
                recettes.add(x["item"])
            for v in x.values():
                parcourir(v)
        elif isinstance(x, list):
            for v in x:
                parcourir(v)

    for f in sorted((WIKI / "data").glob("*.json")):
        if f.name not in PRODUITS:
            parcourir(json.loads(f.read_text(encoding="utf-8")))
    return objets, recettes


def json_octets(donnees) -> bytes:
    return (json.dumps(donnees, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode("utf-8")


def ouvrir(chemin: Path) -> Image.Image:
    with Image.open(chemin) as im:
        return im.convert("RGBA")


def recadrer(im: Image.Image, entree: dict, nom: str) -> tuple[Image.Image, tuple[int, int, int, int]]:
    cadre = entree.get("recadrage") or [0, 0, im.width, im.height]
    if len(cadre) != 4:
        raise Erreur(f"{nom} : recadrage [x, y, largeur, hauteur] attendu, lu {cadre}")
    x, y, l, h = (int(v) for v in cadre)
    if l <= 0 or h <= 0 or x < 0 or y < 0 or x + l > im.width or y + h > im.height:
        raise Erreur(f"{nom} : recadrage {cadre} hors de l'image source ({im.width} x {im.height})")
    im = im.crop((x, y, x + l, y + h))
    largeur = int(entree.get("largeur") or l)
    if largeur > l:
        raise Erreur(f"{nom} : largeur {largeur} plus grande que le recadrage ({l}) : on n'agrandit pas")
    if largeur != l:
        im = im.resize((largeur, max(1, round(h * largeur / l))), Image.LANCZOS)
    rayon = entree.get("coins")
    if rayon:
        im = arrondir(im, int(rayon))
    return im, (x, y, l, h)


def arrondir(im: Image.Image, rayon: int) -> Image.Image:
    """Coins arrondis lissés (masque dessiné à 4 fois la taille puis réduit)."""
    f = 4
    masque = Image.new("L", (im.width * f, im.height * f), 0)
    ImageDraw.Draw(masque).rounded_rectangle((0, 0, im.width * f - 1, im.height * f - 1), radius=rayon * f, fill=255)
    masque = masque.resize(im.size, Image.LANCZOS)
    alpha = Image.composite(im.getchannel("A"), Image.new("L", im.size, 0), masque)
    sortie = im.copy()
    sortie.putalpha(alpha)
    return sortie


def opaque(im: Image.Image) -> bool:
    return im.getchannel("A").getextrema()[0] == 255


def webp_fixe(im: Image.Image, qualite: int, nom: str) -> bytes:
    for palier in PALIERS:
        q = max(30, qualite - palier)
        tampon = io.BytesIO()
        (im.convert("RGB") if opaque(im) else im).save(tampon, "WEBP", quality=q, method=6)
        if tampon.tell() <= FIXE_MAX:
            return tampon.getvalue()
    raise Erreur(f"{nom} : plus de 250 Ko même à la qualité {max(30, qualite - PALIERS[-1])} : recadrer ou réduire")


def webp_anime(images: list, duree: int, qualite: int, nom: str) -> bytes:
    for palier in PALIERS:
        q = max(30, qualite - palier)
        tampon = io.BytesIO()
        tout_opaque = all(opaque(i) for i in images)
        cadres = [i.convert("RGB") if tout_opaque else i for i in images]
        cadres[0].save(tampon, "WEBP", save_all=True, append_images=cadres[1:], duration=duree, loop=0,
                       quality=q, method=6, minimize_size=True)
        if tampon.tell() <= ANIM_MAX:
            return tampon.getvalue()
    raise Erreur(f"{nom} : plus de 1,5 Mo même à la qualité {max(30, qualite - PALIERS[-1])} : "
                 "moins d'images (pas), plus petit ou plus court")


def reperes_ramenes(fichier: Path, taille_source: tuple[int, int], cadre: tuple[int, int, int, int],
                    nom: str) -> dict:
    """Les repères de la prise (pourcentage de l'image source) en pourcentage du recadrage."""
    donnees = json.loads(fichier.read_text(encoding="utf-8"))
    largeur, hauteur = taille_source
    x0, y0, l, h = cadre
    sortie = {}
    for cle, (px, py) in donnees.get("reperes", {}).items():
        x = (px / 100 * largeur - x0) / l * 100
        y = (py / 100 * hauteur - y0) / h * 100
        if not (0 <= x <= 100 and 0 <= y <= 100):
            raise Erreur(f"{nom} : le repère « {cle} » ({px} %, {py} % de la source) tombe hors du recadrage")
        sortie[cle] = [round(x, 2), round(y, 2)]
    return sortie


def images_de_sequence(dossier: Path) -> list[Path]:
    fichiers = sorted(p for p in dossier.iterdir() if re.fullmatch(r"f\d+\.(png|jpg)", p.name))
    if not fichiers:
        raise Erreur(f"séquence vide : {dossier.relative_to(RACINE)}")
    return fichiers


def construire_visuel(entree: dict, partiel: bool, ecrits: list, anciens_reperes: dict, reperes: dict,
                      echecs: list) -> str:
    sortie = entree.get("sortie", "")
    if not re.fullmatch(r"shots/[a-z0-9_-]+/[a-z0-9_.-]+\.webp", sortie):
        raise Erreur(f"sortie « {sortie} » : shots/<chapitre>/<nom>.webp attendu")
    cible = WIKI / sortie
    if ("source" in entree) == ("sequence" in entree):
        raise Erreur(f"{sortie} : « source » ou « sequence », l'un des deux")
    refus = source_refusee(entree.get("source") or entree["sequence"], echecs)
    if refus:
        raise Erreur(f"{sortie} : {refus}")
    chemin = RACINE / (entree.get("source") or entree["sequence"])
    if not chemin.exists():
        if partiel:
            if sortie in anciens_reperes:
                reperes[sortie] = anciens_reperes[sortie]
            return f"gardé (source absente : {chemin.relative_to(RACINE)})"
        raise Erreur(f"{sortie} : source absente {chemin.relative_to(RACINE)} (relancer l'atelier, ou --partiel)")
    if "source" in entree:
        source = ouvrir(chemin)
        im, cadre = recadrer(source, entree, sortie)
        octets = webp_fixe(im, int(entree.get("qualite", 86)), sortie)
        taille = source.size
        fichier_reperes = (RACINE / entree["reperes"] if isinstance(entree.get("reperes"), str)
                           else chemin.with_suffix(".json"))
    else:
        fichiers = images_de_sequence(chemin)
        debut = int(entree.get("debut", 0))
        fin = int(entree.get("fin", len(fichiers) - 1))
        pas = int(entree.get("pas", 1))
        choisis = fichiers[debut:fin + 1:pas]
        if len(choisis) < 2:
            raise Erreur(f"{sortie} : moins de deux images retenues dans la séquence")
        cadres = []
        taille = None
        cadre = None
        for f in choisis:
            source = ouvrir(f)
            taille = source.size
            im, cadre = recadrer(source, entree, sortie)
            cadres.append(im)
        octets = webp_anime(cadres, int(entree.get("duree", 100 * pas)), int(entree.get("qualite", 80)), sortie)
        fichier_reperes = (RACINE / entree["reperes"] if isinstance(entree.get("reperes"), str)
                           else chemin.parent / (chemin.name + ".json"))
    if entree.get("reperes") is False:
        pass  # vignette ou couverture : aucun repère n'y est montré
    elif fichier_reperes.is_file():
        points = reperes_ramenes(fichier_reperes, taille, cadre, sortie)
        if points:
            reperes[sortie] = points
    elif entree.get("reperes"):
        raise Erreur(f"{sortie} : fichier de repères absent {entree['reperes']}")
    ecrire_si_change(cible, octets, ecrits)
    return f"{len(octets) / 1024:.0f} Ko" + (f", {len(reperes.get(sortie, {}))} repère(s)" if sortie in reperes else "")


def construire_exports(partiel: bool, ecrits: list) -> str:
    noms_fichier = EXPORT / "objets.json"
    recettes_fichier = EXPORT / "recettes.json"
    icones = EXPORT / "icones"
    if not (noms_fichier.is_file() and recettes_fichier.is_file() and icones.is_dir()):
        if partiel:
            return "exports absents : objets, recettes et icônes gardés tels quels"
        raise Erreur(f"exports absents ({EXPORT.relative_to(RACINE)}) : lancer runWikiStudio -PwikiSeul=_export, "
                     "ou --partiel")
    noms = json.loads(noms_fichier.read_text(encoding="utf-8"))
    toutes = json.loads(recettes_fichier.read_text(encoding="utf-8"))
    # Seul ce que le wiki cite est publié : les recettes des blocs recipe, les objets nommés par les fiches et ceux de
    # ces recettes (l'export complet compte plus d'un millier d'objets et de recettes).
    cites, sorties = cites_par_le_wiki()
    recettes = [r for r in toutes if r["sortie"] in sorties]
    sans_recette = sorted(sorties - {r["sortie"] for r in recettes})
    if sans_recette:
        raise Erreur(f"blocs recipe sans recette exportée : {sans_recette}")
    voulus = set(cites) | {i for r in recettes for i in [r["sortie"]] + [g["item"] for g in r["ingredients"]]
                           + [c for ligne in r.get("grille", []) for c in ligne if c]}
    manquants = sorted(i for i in voulus if i not in noms)
    if manquants:
        raise Erreur(f"objets cités sans nom ni icône exportés {manquants[:10]}")
    dossier = WIKI / "img" / "objets"
    objets = {}
    presentes = set()
    for ident in sorted(voulus):
        espace, chemin = ident.split(":", 1)
        fichier = f"{espace}__{chemin.replace('/', '-')}.png"
        source = icones / fichier
        if not source.is_file():
            raise Erreur(f"icône absente pour {ident} : {source.relative_to(RACINE)}")
        ecrire_si_change(dossier / fichier, source.read_bytes(), ecrits)
        presentes.add(fichier)
        objets[ident] = {"nom": noms[ident]["nom"], "icone": f"img/objets/{fichier}"}
    if dossier.is_dir():
        for vieux in dossier.glob("*.png"):
            if vieux.name not in presentes:
                ecrits.append((vieux, None))
    ecrire_si_change(WIKI / "data" / "objets.json", json_octets(objets), ecrits)
    ecrire_si_change(WIKI / "data" / "recettes.json",
                     (json.dumps(recettes, ensure_ascii=False, indent=1) + "\n").encode("utf-8"), ecrits)
    table = sum(1 for r in recettes if r["poste"] == "table")
    return (f"{len(objets)} objets et icônes cités, {len(recettes)} recettes citées ({table} de table, "
            f"{len(recettes) - table} d'établi) sur {len(noms)} objets et {len(toutes)} recettes exportés")


def poids_du_wiki() -> int:
    return sum(p.stat().st_size for p in WIKI.rglob("*") if p.is_file() and "tools" not in p.relative_to(WIKI).parts)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--partiel", action="store_true", help="une source absente garde le visuel déjà publié")
    parser.add_argument("--config", type=Path, default=CONFIG, help="autre liste de visuels (essai)")
    parser.add_argument("--wiki", type=Path, default=None, help="autre dossier de sortie que wiki/ (essai)")
    args = parser.parse_args()
    global WIKI
    if args.wiki:
        WIKI = args.wiki.resolve()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    entrees = config.get("visuels", [])
    vues = set()
    for e in entrees:
        if e.get("sortie") in vues:
            print(f"ERREUR sortie en double : {e.get('sortie')}")
            return 1
        vues.add(e.get("sortie"))
    fichier_reperes = WIKI / "data" / "reperes.json"
    anciens = json.loads(fichier_reperes.read_text(encoding="utf-8")) if fichier_reperes.is_file() else {}
    # Avec --partiel, les repères déjà publiés restent (seuls ceux des visuels reconstruits changent).
    reperes = dict(anciens) if args.partiel else {}
    ecrits = []
    erreurs = []
    echecs = echecs_de_l_atelier()
    for e in entrees:
        try:
            print(f"  {e.get('sortie')} : {construire_visuel(e, args.partiel, ecrits, anciens, reperes, echecs)}")
        except Erreur as ex:
            erreurs.append(str(ex))
    try:
        print(f"  exports : {construire_exports(args.partiel, ecrits)}")
    except Erreur as ex:
        erreurs.append(str(ex))
    ecrire_si_change(fichier_reperes, json_octets(reperes), ecrits)
    # Poids estimé après les écritures notées (rien n'est encore écrit).
    poids = poids_du_wiki() + sum((len(c) if c is not None else 0) - (p.stat().st_size if p.is_file() else 0)
                                  for p, c in ecrits)
    if poids > WIKI_MAX:
        erreurs.append(f"wiki de {poids / 1024 / 1024:.1f} Mo : plus de 80 Mo")
    if erreurs:
        print(f"{len(entrees)} visuel(s) : rien n'est écrit ni retiré ({len(ecrits)} changement(s) en attente)")
    else:
        appliquer(ecrits)
        print(f"{len(entrees)} visuel(s), {len(ecrits)} fichier(s) écrit(s) ou retiré(s), wiki {poids / 1024 / 1024:.1f} Mo")
    for e in erreurs:
        print(f"ERREUR {e}")
    return 1 if erreurs else 0


if __name__ == "__main__":
    sys.exit(main())
