"""Capture chaque page du wiki à 1280 et 390 px de large avec Edge sans fenêtre, et relève les défauts.

    python wiki/tools/shoot_wiki.py                      # accueil, chaque chapitre, chaque article, 
    python wiki/tools/shoot_wiki.py telephone telephone/antenne
    python wiki/tools/shoot_wiki.py --essai-interactif   # joue les blocs interactifs du chapitre Téléphone (et un essai de robustesse)

Le dossier wiki/ est servi par http.server sur un port libre. Chaque page est ouverte dans une page pilote
(/__apercu.html, servie seulement ici) qui la charge dans un cadre de la largeur voulue : les règles @media voient
donc 390 px alors qu'Edge sans fenêtre ne descend pas sous 500 ; la capture est ensuite recadrée.
Relevés : débordement horizontal, images cassées, erreurs de script, ressources absentes (404).
Sorties : build/wiki-apercu/<page>-<largeur>.png et rapport.json. Code de sortie 1 au moindre défaut.
"""
import argparse
import concurrent.futures
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import urllib.parse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

WIKI = Path(__file__).resolve().parent.parent
SORTIE = WIKI.parent / "build" / "wiki-apercu"
EDGES = [
    Path(os.environ.get("PROGRAMFILES(X86)", r"C:\Program Files (x86)")) / "Microsoft/Edge/Application/msedge.exe",
    Path(os.environ.get("PROGRAMFILES", r"C:\Program Files")) / "Microsoft/Edge/Application/msedge.exe",
]

# Injecté en tête de index.html (seulement par ce serveur) : relève les erreurs de script et les avertissements.
ESPION = """<script>
window.__erreurs=[];window.__avert=[];
addEventListener('error',function(e){if(!e.target||e.target===window||!e.target.tagName)__erreurs.push(String(e.message)+' @'+e.filename+':'+e.lineno)},true);
addEventListener('unhandledrejection',function(e){__erreurs.push('promesse rejetée : '+e.reason)});
(function(){var ce=console.error,cw=console.warn;
console.error=function(){__erreurs.push('console.error : '+[].map.call(arguments,String).join(' '));return ce.apply(console,arguments)};
console.warn=function(){__avert.push([].map.call(arguments,String).join(' '));return cw.apply(console,arguments)}})();
</script>"""

PILOTE = r"""<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>apercu</title>
<style>html,body{margin:0;background:#0E1316;overflow:hidden}iframe{border:0;display:block}#rapport{display:none}</style></head>
<body><iframe id="f"></iframe><pre id="rapport"></pre>
<script>
const P = new URLSearchParams(location.search);
const W = +P.get('w') || 1280, ROUTE = P.get('r') || 'accueil', MODE = P.get('mode') || 'page';
const f = document.getElementById('f');
f.style.width = W + 'px'; f.style.height = '900px';
const dort = (ms) => new Promise((r) => setTimeout(r, ms));
async function attendre(cond, max) { for (let t = 0; t < (max || 15000); t += 50) { try { if (cond()) return true; } catch (e) {} await dort(50); } return false; }
const doc = () => f.contentDocument, win = () => f.contentWindow;
async function images() {
  await attendre(() => [...doc().images].every((i) => i.complete), 15000);
  await dort(300);
}
async function mesure() {
  let h = 0;
  for (let k = 0; k < 6; k++) {
    await images();
    const n = Math.ceil(doc().documentElement.scrollHeight);
    if (n === h) break;
    h = n; f.style.height = h + 'px';
    await dort(200);
  }
  return h;
}
function nom(el) { return el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.className && typeof el.className === 'string' ? '.' + el.className.trim().split(/\s+/).join('.') : ''); }
function releve() {
  const d = doc(), de = d.documentElement, larg = de.clientWidth;
  const fautifs = [];
  if (de.scrollWidth > larg + 1) {
    d.querySelectorAll('body *').forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width && r.right > larg + 1 && fautifs.length < 12) fautifs.push(nom(el) + ' → ' + Math.round(r.right) + 'px');
    });
  }
  const casses = [...d.images].filter((i) => i.complete && !i.naturalWidth).map((i) => i.getAttribute('src'))
    .concat([...d.querySelectorAll('[data-manque]')].map((s) => s.dataset.manque));
  return { largeur: larg, debord: de.scrollWidth > larg + 1, scrollWidth: de.scrollWidth, fautifs, casses: [...new Set(casses)],
    erreurs: (win().__erreurs || []).slice(), avertissements: (win().__avert || []).slice(), titre: d.title,
    h1: (d.querySelector('h1') || {}).textContent || '', route: d.body.dataset.route || '' };
}

// ---------- Essais interactifs (clic au centre de l'élément visé, par ce qui s'y trouve vraiment) ----------
const R = [];
const note = (nom, ok, detail) => R.push({ nom, ok: !!ok, detail: detail == null ? '' : String(detail) });
async function clique(sel, racine) {
  const el = (racine || doc()).querySelector(sel);
  if (!el) throw new Error('introuvable : ' + sel);
  el.scrollIntoView({ block: 'center' });
  const r = el.getBoundingClientRect();
  const x = r.left + r.width / 2, y = r.top + r.height / 2;
  const vise = doc().elementFromPoint(x, y);
  if (!vise || !(vise === el || el.contains(vise))) throw new Error('recouvert : ' + sel + ' par ' + (vise ? nom(vise) : 'rien'));
  vise.click();
  await dort(80);
}
async function va(route) {
  doc().body.dataset.pret = '0';
  win().location.hash = route;
  await attendre(() => doc().body.dataset.pret === '1' && doc().body.dataset.route === route.replace(/^#/, ''), 8000);
  await images();
}
const txt = (sel) => ((doc().querySelector(sel) || {}).textContent || '').trim();
async function essai(nomEssai, fn) { try { await fn(); } catch (e) { note(nomEssai, false, e.message); } }

async function interactif() {
  await va('#telephone/antenne');
  await essai('hero : repère 2', async () => {
    await clique('[data-hero] [data-pt="1"]');
    const b = doc().querySelector('[data-hero] [data-pt="1"]');
    const info = doc().querySelector('[data-hero] [data-info="1"]');
    note('hero : repère 2', b.getAttribute('aria-pressed') === 'true' && !info.hidden && doc().querySelector('[data-hero] [data-info="0"]').hidden && txt('[data-hero] [data-num]') === '2', txt('[data-hero] [data-info="1"] h3'));
  });
  await essai('power : 3 cartes de plus', async () => {
    for (let i = 0; i < 3; i++) await clique('[data-power] [data-stp="cartes"][data-d="1"]');
    note('power : 3 cartes de plus', txt('[data-power] [data-o="cartes"]') === '6' && txt('[data-power] [data-o="besoin"]') === '85' && txt('[data-power] [data-o="verdict"]') === 'Ça tient', txt('[data-power] [data-o="besoin"]') + ' / ' + txt('[data-power] [data-o="donne"]') + ' ' + txt('[data-power] [data-o="verdict"]'));
  });
  await essai('power : vitesse 32', async () => {
    await clique('[data-power] [data-vit="32"]');
    note('power : vitesse 32', txt('[data-power] [data-o="donne"]') === '45' && /Pas assez/.test(txt('[data-power] [data-o="verdict"]')) && doc().querySelector('[data-power] [data-vit="32"]').getAttribute('aria-pressed') === 'true', txt('[data-power] [data-o="verdict"]'));
  });
  await essai('power : borne basse', async () => {
    for (let i = 0; i < 3; i++) await clique('[data-power] [data-stp="appels"][data-d="-1"]');
    note('power : borne basse', txt('[data-power] [data-o="appels"]') === '0', txt('[data-power] [data-o="appels"]'));
  });
  await essai('range : curseur', async () => {
    const i = doc().querySelector('[data-portee] input');
    const res = [];
    for (const [v, att] of [[200, '2 barres'], [300, 'Hors réseau'], [270, '1 barre'], [10, '4 barres']]) {
      i.value = v; i.dispatchEvent(new (win().Event)('input', { bubbles: true })); await dort(30);
      res.push(v + '=' + txt('[data-portee] [data-o="signal"]') + (txt('[data-portee] [data-o="signal"]') === att ? '' : ' (attendu ' + att + ')'));
    }
    note('range : curseur', res.every((s) => !/attendu/.test(s)), res.join(', '));
  });
  await essai('vue Staff', async () => {
    const det = () => doc().querySelector('details.staff');
    await clique('.vue [data-view="staff"]');
    const ouvert = det().open;
    let memo = null; try { memo = win().localStorage.getItem('wiki-view'); } catch (e) { memo = 'indisponible'; }
    await clique('.vue [data-view="joueurs"]');
    note('vue Staff', ouvert && !det().open, 'ouvert en Staff : ' + ouvert + ', mémorisé : ' + memo);
  });
  await essai('animation : lire / arrêter', async () => {
    const fig = () => doc().querySelector('[data-clip]');
    const avant = fig().dataset.joue;
    await clique('[data-clip] [data-clip-btn]');
    const apres1 = fig().dataset.joue;
    const aImg = !!fig().querySelector('img'), aCanvas = !!fig().querySelector('canvas');
    await clique('[data-clip] [data-clip-btn]');
    note('animation : lire / arrêter', avant !== apres1 && fig().dataset.joue === avant && (apres1 === 'true' ? aImg : aCanvas), 'départ ' + avant + ' → ' + apres1 + ' → ' + fig().dataset.joue);
  });
  await essai('frise : lien vers l\'étape 3', async () => {
    const cartes = txt('[data-power] [data-o="cartes"]'), repere = txt('[data-hero] [data-num]');
    await clique('.path a:nth-child(3)');
    await attendre(() => doc().body.dataset.route === 'telephone/antenne/etape-3', 4000);
    const e = doc().getElementById('etape-3');
    const garde = txt('[data-power] [data-o="cartes"]') === cartes && txt('[data-hero] [data-num]') === repere;
    note('frise : lien vers l\'étape 3, sans redessiner', e && Math.abs(e.getBoundingClientRect().top) < 40 && garde, doc().body.dataset.route + ', réglages gardés : ' + garde);
    win().scrollTo(0, 0); await dort(50);
    await clique('.path a:nth-child(3)'); await dort(100);
    note('frise : second clic sur la même étape', Math.abs(doc().getElementById('etape-3').getBoundingClientRect().top) < 40, 'y=' + Math.round(win().scrollY));
  });
  await essai('lien d\'évitement', async () => {
    const avant = win().location.hash;
    win().scrollTo(0, 0);
    doc().querySelector('a.evite').focus(); // visible seulement au focus clavier
    await clique('a.evite');
    note('lien d\'évitement', win().location.hash === avant && doc().activeElement && doc().activeElement.id === 'main', win().location.hash + ', focus ' + (doc().activeElement || {}).id);
  });
  await va('#telephone/appeler');
  await essai('player : étape 3', async () => {
    await clique('[data-lecteur] [data-pas="2"]');
    const vis = [...doc().querySelectorAll('[data-lecteur] [data-cadre]')].filter((c) => !c.hidden).map((c) => c.dataset.cadre);
    note('player : étape 3', vis.join() === '2' && doc().querySelector('[data-pas="2"]').getAttribute('aria-pressed') === 'true', 'visible : ' + vis.join());
  });
  await essai('ancien lien couvert (v2)', async () => {
    await va('#tuto-antenne').catch(() => {});
    await attendre(() => doc().body.dataset.pret === '1', 4000); await dort(100);
    note('ancien lien couvert (v2)', win().location.hash === '#telephone/antenne' && /Poser une antenne/i.test(txt('h1')), win().location.hash);
  });
  await essai('ancien lien de fiche (v1)', async () => {
    doc().body.dataset.pret = '0'; win().location.hash = '#nommer-le-maire';
    await attendre(() => doc().body.dataset.pret === '1', 4000); await dort(100);
    const f = doc().getElementById('nommer-le-maire');
    note('ancien lien de fiche (v1)', f && f.classList.contains('vise') && /maire/i.test(txt('h1')), txt('h1'));
  });
  await essai('page inconnue', async () => {
    doc().body.dataset.pret = '0'; win().location.hash = '#nexiste-pas';
    await attendre(() => doc().body.dataset.pret === '1', 4000); await dort(100);
    note('page inconnue', /n'existe pas/.test(txt('.vide-msg')) && /veux faire/i.test(txt('h1')), txt('.vide-msg'));
  });
  await essai('recherche', async () => {
    const q = doc().getElementById('q');
    const cherche = async (s) => { q.value = s; q.dispatchEvent(new (win().Event)('input', { bubbles: true })); await dort(400); return doc().querySelectorAll('#resultats .res').length; };
    const a = await cherche('antenne'), b = await cherche('ANTÉNNE'), c = await cherche('zzzqqq');
    const accueilCache = doc().querySelector('.veux').closest('[data-hors-recherche]') ? doc().querySelector('.veux').hidden : false;
    await cherche('');
    note('recherche', a > 0 && a === b && c === 0 && accueilCache && !doc().querySelector('.veux').hidden, 'antenne=' + a + ' ANTÉNNE=' + b + ' zzzqqq=' + c);
  });
  await essai('touche /', async () => {
    await va('#telephone/appeler');
    doc().activeElement && doc().activeElement.blur && doc().activeElement.blur();
    doc().dispatchEvent(new (win().KeyboardEvent)('keydown', { key: '/', bubbles: true }));
    await attendre(() => doc().activeElement && doc().activeElement.id === 'q', 4000);
    note('touche /', doc().activeElement && doc().activeElement.id === 'q', win().location.hash);
  });
  await essai('En jeu seulement', async () => {
    await va('#telephone');
    const avant = /Prévu/.test(doc().querySelector('main').textContent);
    doc().getElementById('enJeu').focus(); // un vrai clic donne le focus à la case ; click() en script, non
    await clique('#enJeu');
    await dort(100);
    const apres = /Prévu/.test(doc().querySelector('main').textContent);
    note('En jeu seulement : le focus reste sur la case', doc().activeElement && doc().activeElement.id === 'enJeu', (doc().activeElement || {}).id);
    await clique('#enJeu');
    note('En jeu seulement', avant && !apres, 'Prévu avant : ' + avant + ', après : ' + apres);
  });
}

async function robustesse() {
  await va('#telephone/antenne');
  const d = doc();
  note('robustesse : page rendue malgré un bloc inconnu et une image absente', !!d.getElementById('etape-4') && d.querySelectorAll('[data-manque]').length >= 1 && d.querySelector('.inconnu'),
    'étape 4 : ' + !!d.getElementById('etape-4') + ', cadres « manque » : ' + d.querySelectorAll('[data-manque]').length);
}

f.onload = async () => {
  f.onload = null;
  await attendre(() => doc().body && doc().body.dataset.pret === '1', 20000);
  if (MODE === 'interactif') await interactif();
  if (MODE === 'robustesse') await robustesse();
  if (MODE === 'staff') { doc().querySelector('.vue [data-view="staff"]').click(); await dort(100); }
  if (P.get('q')) { const q = doc().getElementById('q'); q.value = P.get('q'); q.dispatchEvent(new (win().Event)('input', { bubbles: true })); await dort(500); }
  // Les images en chargement différé ne partent qu'à l'approche : on les charge toutes avant la capture pleine page.
  for (const im of doc().querySelectorAll('img[loading="lazy"]')) im.loading = 'eager';
  await attendre(() => [...doc().images].every((i) => i.complete), 8000).catch(() => {});
  const h = await mesure();
  const rap = releve();
  rap.hauteur = h; rap.essais = R;
  document.getElementById('rapport').textContent = JSON.stringify(rap);
  document.title = 'PRET';
};
f.src = '/index.html#' + ROUTE;
</script></body></html>"""


class Ecoute(ThreadingHTTPServer):
    # File d'attente large : quatre Edge en parallèle ouvrent beaucoup de connexions à la fois (5 par défaut : refus).
    request_queue_size = 256
    daemon_threads = True


class Serveur(SimpleHTTPRequestHandler):
    absents = []
    robustesse = False

    def log_message(self, *a):
        pass

    def do_GET(self):
        chemin = urllib.parse.urlparse(self.path).path
        if chemin == "/__apercu.html":
            return self._envoie(PILOTE.encode("utf-8"), "text/html; charset=utf-8")
        if chemin in ("/", "/index.html"):
            page = (WIKI / "index.html").read_text(encoding="utf-8").replace("<head>", "<head>" + ESPION, 1)
            return self._envoie(page.encode("utf-8"), "text/html; charset=utf-8")
        if chemin == "/data/telephone.json" and Serveur.robustesse:
            d = json.loads((WIKI / "data/telephone.json").read_text(encoding="utf-8"))
            blocs = d["articles"][0]["blocks"]
            blocs.insert(2, {"type": "bloc-qui-n-existe-pas"})
            blocs.insert(3, {"type": "shot", "img": "shots/telephone/absente.webp", "alt": "Image volontairement absente"})
            return self._envoie(json.dumps(d).encode("utf-8"), "application/json")
        cible = WIKI / urllib.parse.unquote(chemin.lstrip("/"))
        if not cible.exists():
            Serveur.absents.append(chemin)
        return super().do_GET()

    def _envoie(self, corps, type_):
        self.send_response(200)
        self.send_header("Content-Type", type_)
        self.send_header("Content-Length", str(len(corps)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(corps)


def edge():
    for p in EDGES:
        if p.is_file():
            return str(p)
    sys.exit("Edge introuvable (msedge.exe).")


def lance(args, budget):
    profil = tempfile.mkdtemp(prefix="wiki-edge-")
    try:
        cmd = [edge(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run", "--no-default-browser-check",
               "--disable-extensions", "--mute-audio", f"--user-data-dir={profil}", f"--virtual-time-budget={budget}"] + args
        return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=180)
    finally:
        shutil.rmtree(profil, ignore_errors=True)


def page(port, route, largeur, mode="page", reduit=False, capture=True, requete=""):
    url = f"http://127.0.0.1:{port}/__apercu.html?" + urllib.parse.urlencode({"w": largeur, "r": route, "mode": mode, "q": requete})
    extra = ["--force-prefers-reduced-motion"] if reduit else []
    fen = max(largeur, 500)
    sortie = lance(extra + [f"--window-size={fen},900", "--dump-dom", url], 60000)
    m = re.search(r'<pre id="rapport">(.*?)</pre>', sortie.stdout, re.S)
    if not m or not m.group(1).strip():
        return {"route": route, "largeur": largeur, "erreurs": ["page pilote sans rapport (délai dépassé ?)"], "casses": [], "debord": False, "fautifs": []}
    rap = json.loads(html.unescape(m.group(1)))
    rap["route_demandee"], rap["mode"], rap["reduit"] = route, mode, reduit
    if capture:
        nomf = re.sub(r"[^a-z0-9-]+", "-", route.lower()).strip("-") or "accueil"
        nomf += ("-reduit" if reduit else "") + ("" if mode == "page" else "-" + mode) + ("-q-" + re.sub(r"[^a-z0-9]+", "-", requete.lower()) if requete else "")
        png = SORTIE / f"{nomf}-{largeur}.png"
        h = max(int(rap.get("hauteur") or 900), 300)
        lance(extra + [f"--window-size={fen},{h}", f"--screenshot={png}", url], 60000)
        if png.is_file() and fen != largeur:
            from PIL import Image
            with Image.open(png) as im:
                im.crop((0, 0, largeur, im.height)).save(png)
        rap["capture"] = str(png.relative_to(WIKI.parent)) if png.is_file() else None
    return rap


def routes_par_defaut():
    acc = json.loads((WIKI / "data/accueil.json").read_text(encoding="utf-8"))
    out = ["accueil"]
    for ch in acc.get("chapters", []) + acc.get("hidden", []):
        p = WIKI / "data" / f"{ch}.json"
        if not p.is_file():
            continue
        out.append(ch)
        d = json.loads(p.read_text(encoding="utf-8"))
        if d.get("v") == 2:
            out += [f"{ch}/{a['id']}" for a in d.get("articles", [])]
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pages", nargs="*", help="routes (sans #) ; par défaut toutes")
    ap.add_argument("--largeurs", default="1280,390")
    ap.add_argument("--essai-interactif", action="store_true", help="joue les blocs interactifs du chapitre Téléphone")
    ap.add_argument("--paralleles", type=int, default=4)
    ap.add_argument("--staff", action="store_true", help="capture en vue Staff")
    ap.add_argument("--recherche", default="", help="tape ce texte dans la recherche de l'accueil avant la capture")
    a = ap.parse_args()

    SORTIE.mkdir(parents=True, exist_ok=True)
    serveur = Ecoute(("127.0.0.1", 0), partial(Serveur, directory=str(WIKI)))
    threading.Thread(target=serveur.serve_forever, daemon=True).start()
    port = serveur.server_address[1]
    largeurs = [int(x) for x in a.largeurs.split(",")]

    taches = []
    if a.essai_interactif:
        for l in largeurs:
            taches.append(("telephone/antenne", l, "interactif", False))
            taches.append(("telephone/antenne", l, "interactif", True))
    else:
        for r in (a.pages or routes_par_defaut()):
            for l in largeurs:
                taches.append((r, l, "staff" if a.staff else "page", False))
        if not a.pages:
            taches.append(("telephone/antenne", 1280, "page", True))

    rapports = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=a.paralleles) as ex:
        futurs = [ex.submit(page, port, r, l, m, red, True, a.recherche) for (r, l, m, red) in taches]
        for fu in futurs:
            rapports.append(fu.result())
    if a.essai_interactif:
        Serveur.robustesse = True
        rapports.append(page(port, "telephone/antenne", 1280, "robustesse", False, capture=True))
        Serveur.robustesse = False
    serveur.shutdown()

    defauts = 0
    for r in rapports:
        tag = f"{r.get('route_demandee', r.get('route'))} @{r.get('largeur')}" + (" (mouvement réduit)" if r.get("reduit") else "") + ("" if r.get("mode", "page") == "page" else f" [{r['mode']}]")
        robuste = r.get("mode") == "robustesse"
        pbs = []
        if r.get("debord"):
            pbs.append(f"débordement horizontal ({r.get('scrollWidth')} px) : " + "; ".join(r.get("fautifs", [])))
        casses = [c for c in r.get("casses", []) if not (robuste and c.endswith("absente.webp"))]
        if casses:
            pbs.append("images cassées : " + ", ".join(casses))
        if r.get("erreurs"):
            pbs.append("erreurs de script : " + " | ".join(r["erreurs"]))
        avert = [w for w in r.get("avertissements", []) if not (robuste and "bloc-qui-n-existe-pas" in w)]
        if avert:
            pbs.append("avertissements : " + " | ".join(avert))
        for e in r.get("essais", []):
            if not e["ok"]:
                pbs.append(f"essai raté : {e['nom']} ({e['detail']})")
        defauts += len(pbs)
        print(("ÉCHEC " if pbs else "ok    ") + tag + f"  h={r.get('hauteur')}  « {r.get('h1', '').strip()[:50]} »  {r.get('capture') or ''}")
        for e in r.get("essais", []):
            print(f"         {'✓' if e['ok'] else '✗'} {e['nom']} : {e['detail']}")
        for p in pbs:
            print("         ! " + p)
    absents = sorted(set(x for x in Serveur.absents if not x.endswith("absente.webp") and x != "/favicon.ico"))
    if absents:
        print("Ressources absentes (404) : " + ", ".join(absents))
        defauts += len(absents)
    (SORTIE / "rapport.json").write_text(json.dumps(rapports, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(rapports)} pages, {defauts} défaut(s). Captures et rapport : {SORTIE.relative_to(WIKI.parent)}")
    return 1 if defauts else 0


if __name__ == "__main__":
    sys.exit(main())
