// Wiki Lost Island, version 2 : rendu des blocs d'article (format décrit dans docs/superpowers/plans/2026-11-27-refonte-wiki.md).
// Chaque bloc devient du HTML ; les blocs interactifs se branchent par délégation (voir brancher()).
(function () {
  "use strict";

  // ---------- Outils communs (aussi utilisés par app.js) ----------
  // Échappe le texte venu des JSON ; espaces insécables du français devant « : ; ? ! » et dans les guillemets.
  const esc = (s) => String(s == null ? "" : s)
    .replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]))
    .replace(/« /g, "«\u00a0").replace(/ »/g, "\u00a0»").replace(/ ([:;?!])/g, "\u00a0$1");
  // Texte riche : `code` et **gras**, après échappement.
  function rich(s) {
    return String(s == null ? "" : s).split("`").map((part, i) => (i % 2 ? `<code>${esc(part)}</code>` : esc(part).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>"))).join("");
  }
  const norm = (s) => String(s == null ? "" : s).toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
  const nombre = (n) => String(n).replace(".", ",");
  const reduit = () => { try { return window.matchMedia("(prefers-reduced-motion: reduce)").matches; } catch (e) { return false; } };

  function img(src, alt, cls, extra) {
    if (!src) return `<span class="manque">${esc(alt || "Image manquante")}</span>`;
    return `<img src="${esc(src)}" alt="${esc(alt || "")}"${cls ? ` class="${cls}"` : ""} loading="lazy" decoding="async"${extra || ""}>`;
  }

  // ctx : { objets, recettes, reperes, lien(href) → titre, staff }
  function objet(ctx, id) {
    const o = (ctx.objets || {})[id];
    return o ? { nom: o.nom || id, icone: o.icone } : { nom: id, icone: null };
  }
  function icone(ctx, id, taille, alt) {
    const o = objet(ctx, id);
    if (!o.icone) return `<span class="mini-manque" title="${esc(id)}">?</span>`;
    const t = taille ? ` width="${taille}" height="${taille}"` : "";
    return `<img class="px" src="${esc(o.icone)}" alt="${esc(alt == null ? o.nom : alt)}"${t} loading="lazy">`;
  }
  // Position d'un repère : `at` (nom dans data/reperes.json pour cette image) ou x, y en pourcentage.
  function pos(ctx, p, src) {
    if (p && p.at) {
      const r = ((ctx.reperes || {})[src] || {})[p.at];
      return Array.isArray(r) && r.length >= 2 ? r : null;
    }
    return p && typeof p.x === "number" && typeof p.y === "number" ? [p.x, p.y] : null;
  }
  const place = (xy) => `left:${xy[0]}%;top:${xy[1]}%`;

  let uid = 0;
  const id = (p) => `${p}-${++uid}`;

  // ---------- Blocs ----------
  const R = {};

  R.text = (b) => `<p class="texte">${rich(b.text)}</p>`;

  R.hero = function (b, ctx) {
    const pts = (b.points || []).map((p, i) => ({ p, i, xy: pos(ctx, p, b.img) }));
    const btns = pts.filter((o) => o.xy).map((o) => `<button type="button" class="pt" style="${place(o.xy)}" data-pt="${o.i}" aria-pressed="${o.i === 0}" aria-label="Repère ${o.i + 1} : ${esc(o.p.title)}">${o.i + 1}</button>`).join("");
    const infos = pts.map((o) => `<div data-info="${o.i}"${o.i ? " hidden" : ""}><h3>${esc(o.p.title)}</h3><span>${rich(o.p.text)}</span></div>`).join("");
    return `<div class="fig" data-hero>
      <div class="shot">${img(b.img, b.alt, "", ' loading="eager"')}${btns}</div>
      ${pts.length ? `<div class="card info-pt" aria-live="polite"><span class="num" data-num>1</span>${infos}</div>` : ""}
    </div>`;
  };

  R.shot = function (b, ctx) {
    const marks = (b.marks || []).map((m, i) => ({ m, i, xy: pos(ctx, m, b.img) }));
    return `<div class="fig">
      <div class="shot">${img(b.img, b.alt)}${marks.filter((o) => o.xy).map((o) => `<span class="mk" style="${place(o.xy)}" aria-hidden="true">${o.i + 1}</span>`).join("")}</div>
      ${marks.length ? `<ol class="legs">${marks.map((o) => `<li class="leg"><b aria-hidden="true">${o.i + 1}</b><span><span class="cache-vue">Repère ${o.i + 1} : </span>${rich(o.m.text)}</span></li>`).join("")}</ol>` : ""}
      ${b.note ? `<p class="note">${rich(b.note)}</p>` : ""}
    </div>`;
  };

  // Animation : jouée d'office, sauf si l'utilisateur a demandé moins de mouvement (bouton « Lire »).
  R.clip = function (b) {
    const joue = !reduit();
    return `<div class="fig" data-clip="${esc(b.img)}" data-alt="${esc(b.alt)}" data-joue="${joue}">
      ${clipCorps(b.img, b.alt, joue)}
      ${b.note ? `<p class="note">${rich(b.note)}</p>` : ""}
    </div>`;
  };
  function clipCorps(src, alt, joue) {
    if (joue) return `<div class="shot clip-c">${img(src, alt)}</div><button type="button" class="arret" data-clip-btn>Arrêter l'animation</button>`;
    return `<div class="shot clip-c clip-lire"><canvas data-affiche role="img" aria-label="${esc(alt)} (image fixe)"></canvas><button type="button" class="lire" data-clip-btn aria-label="Lire l'animation : ${esc(alt)}">▶ Lire</button></div>`;
  }

  const POSTE = { table: "À la table de fabrication", etabli: "À l'établi d'ingénieur" };
  R.recipe = function (b, ctx) {
    const list = (ctx.recettes || []).filter((r) => r.sortie === b.item);
    if (!list.length) return `<p class="vide-msg">Recette de ${esc(objet(ctx, b.item).nom)} introuvable.</p>`;
    return list.map((r) => {
      const rows = r.grille || [];
      const cols = Math.max(1, ...rows.map((l) => l.length));
      const decrit = rows.map((l, i) => `ligne ${i + 1} : ` + Array.from({ length: cols }, (_, j) => (l[j] ? objet(ctx, l[j]).nom : "vide")).join(", ")).join(" ; ");
      const sortie = objet(ctx, r.sortie);
      return `<div class="craft">
        <div class="grid" style="--cols:${cols}" role="img" aria-label="Recette de ${esc(sortie.nom)}, ${esc(decrit)}">
          ${rows.map((l) => Array.from({ length: cols }, (_, j) => `<div class="slot">${l[j] ? icone(ctx, l[j], null, "") : ""}</div>`).join("")).join("")}
        </div>
        <svg class="fleche" width="44" height="32" viewBox="0 0 44 32" aria-hidden="true"><path d="M2 16h36M26 4l13 12-13 12" fill="none" stroke="#F2B44A" stroke-width="5"/></svg>
        <div class="slot sortie">${icone(ctx, r.sortie)}${r.nombre > 1 ? `<span class="n" aria-label="${r.nombre} exemplaires">${r.nombre}</span>` : ""}</div>
      </div>
      <p class="poste">${esc(POSTE[r.poste] || r.poste || "")}${r.nombre > 1 ? ` · donne ${r.nombre}` : ""}</p>
      <ul class="ing">${(r.ingredients || []).map((g) => `<li>${icone(ctx, g.item, null, "")}<span><b>${esc(g.nombre)}</b> ${esc(objet(ctx, g.item).nom)}</span></li>`).join("")}</ul>`;
    }).join("");
  };
  R.recipe.titre = (b, ctx) => "La recette : " + objet(ctx, b.item).nom;

  R.items = (b, ctx) => `<ul class="objets">${(b.list || []).map((it) => `<li><span class="ico">${icone(ctx, it.item, null, "")}</span><div><b>${esc(it.label || objet(ctx, it.item).nom)}</b>${it.text ? `<span>${rich(it.text)}</span>` : ""}</div></li>`).join("")}</ul>`;

  R.states = (b) => `<div class="etats">${(b.list || []).map((s) => `<div class="etat ${["ok", "off", "bad", "info"].includes(s.tone) ? s.tone : "info"}">${img(s.img, s.alt)}<div><h3>${esc(s.title)}</h3><span>${rich(s.text)}</span></div></div>`).join("")}</div>`;

  function ronds(ctx, points, src) {
    return (points || []).map((p) => pos(ctx, p, src)).filter(Boolean).map((xy) => `<span class="rond" style="${place(xy)}" aria-hidden="true"></span>`).join("");
  }
  R.phones = function (b, ctx) {
    const l = b.list || [];
    return `<div class="tels">${l.map((t) => `<figure class="tel${l.length >= 4 ? " mini" : ""}"><div class="ecr">${img(t.img, t.alt)}${ronds(ctx, t.points, t.img)}</div>${t.caption ? `<figcaption>${rich(t.caption)}</figcaption>` : ""}</figure>`).join("")}</div>`;
  };

  R.player = function (b, ctx) {
    const st = b.steps || [];
    const k = id("lecteur");
    return `<div class="lecteur" data-lecteur>
      <div class="ecran" id="${k}" aria-live="polite">${st.map((s, i) => `<div class="cadre" data-cadre="${i}"${i ? " hidden" : ""}>${img(s.img, s.alt, "", i ? "" : ' loading="eager"')}${ronds(ctx, s.points, s.img)}</div>`).join("")}</div>
      <ol class="fil">${st.map((s, i) => `<li><button type="button" data-pas="${i}" aria-pressed="${i === 0}" aria-controls="${k}"><b>${i + 1}</b><span>${rich(s.label)}</span></button></li>`).join("")}
        ${b.hint ? `<li class="mu" style="padding-top:6px">${rich(b.hint)}</li>` : ""}</ol>
    </div>`;
  };

  R.flow = (b, ctx) => `<div class="flux">${(b.nodes || []).map((n, i) => {
    const vis = n.item ? icone(ctx, n.item, null, "") : n.img ? img(n.img, "") : "";
    return `<span class="etp"><span class="noeud${vis ? "" : " seul"}">${vis}<span>${rich(n.label || (n.item ? objet(ctx, n.item).nom : ""))}</span></span>${i < b.nodes.length - 1 ? '<span class="sep" aria-hidden="true">›</span>' : ""}</span>`;
  }).join("")}</div>`;

  R.numbers = (b, ctx) => `<div class="chiffres">${(b.list || []).map((n) => `<div class="puce${n.item ? " avec-ico" : ""}">${n.item ? icone(ctx, n.item, null, "") + "<div>" : ""}<span class="k">${esc(n.k)}</span><b style="display:block">${rich(n.v)}</b>${n.item ? "</div>" : ""}</div>`).join("")}</div>`;

  R.problems = (b, ctx) => `<div class="pb">${(b.list || []).map((p) => {
    let lien = "";
    if (p.link) {
      const href = typeof p.link === "string" ? p.link : p.link.href;
      const label = (typeof p.link === "object" && p.link.label) || (ctx.lien && ctx.lien(href) ? "Voir : " + ctx.lien(href) : "En savoir plus");
      lien = `<a href="${esc(href)}">${esc(label)}</a>`;
    }
    return `<div class="card"><span class="msg">${esc(p.msg)}</span><span>${rich(p.text)}</span>${lien}</div>`;
  }).join("")}</div>`;

  R.pass = (b, ctx) => `<div class="passe">${icone(ctx, b.item, 64, "")}<span>${rich(b.text)}</span></div>`;

  // Calculateur : besoin = veille + cartes × perCard + appels × perCall ; donne = feAt256 × vitesse / 256 (arrondi bas).
  R.power = function (b, ctx) {
    const vit = b.speeds && b.speeds.length ? b.speeds : [32, 64, 128, 256];
    const v0 = vit[Math.min(1, vit.length - 1)];
    const sim = (ctx.objets || {})["oneforallsurvival:sim_card"] ? icone(ctx, "oneforallsurvival:sim_card", 36, "") : "";
    const tel = (ctx.objets || {})["oneforallsurvival:phone"] ? icone(ctx, "oneforallsurvival:phone", 36, "") : "";
    const out = `<div data-power data-standby="${+b.standby || 0}" data-card="${+b.perCard || 0}" data-call="${+b.perCall || 0}" data-fe="${+b.feAt256 || 0}" data-cartes="3" data-appels="1" data-vit="${v0}" style="display:flex;flex-direction:column;gap:14px">
      <div class="ligne-calc"><span>${sim}Cartes SIM actives</span><div class="stp"><button type="button" data-stp="cartes" data-d="-1" aria-label="Une carte de moins">−</button><output data-o="cartes" aria-live="polite">3</output><button type="button" data-stp="cartes" data-d="1" aria-label="Une carte de plus">+</button></div></div>
      <div class="ligne-calc"><span>${tel}Appels en même temps</span><div class="stp"><button type="button" data-stp="appels" data-d="-1" aria-label="Un appel de moins">−</button><output data-o="appels" aria-live="polite">1</output><button type="button" data-stp="appels" data-d="1" aria-label="Un appel de plus">+</button></div></div>
      <span class="mu" id="${id("vit")}">Vitesse de l'alternateur (tr/min)</span>
      <div class="seg" role="group" aria-label="Vitesse de l'alternateur, en tours par minute">${vit.map((t) => `<button type="button" data-vit="${t}" aria-pressed="${t === v0}">${t}</button>`).join("")}</div>
      <div class="ligne-calc"><span>${esc(b.needLabel || "L'antenne demande")}</span><b><span data-o="besoin"></span> FE/t</b></div>
      <div class="jauge" aria-hidden="true"><div data-j="besoin" style="background:var(--or)"></div></div>
      <div class="ligne-calc"><span>L'alternateur donne</span><b><span data-o="donne"></span> FE/t</b></div>
      <div class="jauge" aria-hidden="true"><div data-j="donne"></div></div>
      <div class="verdict" role="status" data-o="verdict"></div>
      <span class="mu petit">${rich(b.note || "Réglages par défaut de Crafts & Additions.")}</span>
    </div>`;
    return out;
  };
  R.power.titre = () => "Combien de courant ?";
  function majPower(el) {
    const d = el.dataset;
    const besoin = +d.standby + d.cartes * d.card + d.appels * d.call;
    const donne = Math.floor((+d.fe * +d.vit) / 256);
    const ok = donne >= besoin;
    const max = Math.max(besoin, donne, 1);
    const coul = ok ? "var(--vert)" : "var(--rouge)";
    el.querySelector('[data-o="cartes"]').textContent = d.cartes;
    el.querySelector('[data-o="appels"]').textContent = d.appels;
    el.querySelector('[data-o="besoin"]').textContent = besoin;
    el.querySelector('[data-o="donne"]').textContent = donne;
    el.querySelector('[data-j="besoin"]').style.width = Math.round((100 * besoin) / max) + "%";
    const jd = el.querySelector('[data-j="donne"]');
    jd.style.width = Math.round((100 * donne) / max) + "%";
    jd.style.background = coul;
    const v = el.querySelector('[data-o="verdict"]');
    v.textContent = ok ? "Ça tient" : "Pas assez : accélérer";
    v.style.color = coul;
    el.querySelectorAll("[data-vit]").forEach((btn) => btn.setAttribute("aria-pressed", btn.dataset.vit === d.vit));
  }

  // Portée : un seuil par barre, du plus proche au plus loin (4 barres sous bars[0], …, 1 jusqu'à bars[n-1]).
  const FONDS = ["#1B252C", "#22303A", "#2B3D49", "#354B59", "#3E5868", "#476577"];
  R.range = function (b, ctx) {
    const bars = (b.bars || []).slice().sort((x, y) => x - y);
    const n = bars.length;
    if (!n) return "";
    const last = bars[n - 1];
    const max = +b.max || last;
    const d0 = n >= 2 ? Math.round(bars[0] + (bars[1] - bars[0]) / 3) : Math.round(bars[0] / 2);
    const k = id("dist");
    const nom = objet(ctx, b.item).nom;
    const anneaux = bars.map((t, i) => {
      const dia = (100 * t) / last;
      return `<i data-zone="${n - i}" style="left:${(100 - dia) / 2}%;top:${(100 - dia) / 2}%;width:${dia}%;height:${dia}%"></i>`;
    }).reverse().join("");
    const phrase = bars.map((t, i) => {
      const c = n - i;
      if (i === n - 1) return `${c === 1 && n === 1 ? "1 barre" : "1"} jusqu'à ${nombre(t)}${n === 1 ? " blocs" : ""}`;
      return i === 0 ? `${c} ${c > 1 ? "barres" : "barre"} sous ${nombre(t)} blocs` : `${c} sous ${nombre(t)}`;
    }).join(", ") + ".";
    return `<div class="portee" data-portee data-bars="${bars.join(",")}">
      <div class="ondes" role="img" aria-label="${n} anneaux de réception autour de : ${esc(nom)}, jusqu'à ${nombre(last)} blocs">${anneaux}${icone(ctx, b.item, null, "")}</div>
      <div>
        <label for="${k}">${esc(b.label || "Distance")} : <span data-o="dist">${d0}</span> blocs</label>
        <input id="${k}" type="range" min="0" max="${max}" step="1" value="${d0}" data-dist aria-valuetext="${d0} blocs">
        <div style="display:flex;align-items:flex-end;gap:24px;flex-wrap:wrap">
          <div class="barres" aria-hidden="true">${bars.map((_, i) => `<i data-barre="${i + 1}" style="height:${(100 * (i + 1)) / n}%"></i>`).join("")}</div>
          <span class="signal" role="status" data-o="signal"></span>
        </div>
        <span class="mu">${esc(phrase)}</span>
      </div>
    </div>`;
  };
  R.range.titre = () => "Jusqu'où ça porte ?";
  function majPortee(el) {
    const bars = el.dataset.bars.split(",").map(Number);
    const input = el.querySelector("[data-dist]");
    const d = +input.value;
    const n = bars.filter((t, i) => (i === bars.length - 1 ? d <= t : d < t)).length;
    el.querySelector('[data-o="dist"]').textContent = d;
    input.setAttribute("aria-valuetext", d + " blocs");
    el.querySelector('[data-o="signal"]').textContent = n === 0 ? "Hors réseau" : n + (n > 1 ? " barres" : " barre");
    el.querySelectorAll("[data-zone]").forEach((z) => { const k = +z.dataset.zone; z.style.background = k === n ? "var(--or)" : FONDS[Math.min(k - 1, FONDS.length - 1)]; });
    el.querySelectorAll("[data-barre]").forEach((x) => { x.style.background = +x.dataset.barre <= n ? "#EAF0F2" : "#33414A"; });
  }

  R.chunk = function (b, ctx) {
    const nom = objet(ctx, b.item).nom;
    const ic = (st) => { const o = objet(ctx, b.item); return o.icone ? `<img class="px" src="${esc(o.icone)}" alt="" style="${st}">` : ""; };
    return `<div class="chunks">
      <div><div class="chunk" role="img" aria-label="${esc(nom)} et sa source dans le même chunk">${ic("left:10%;top:16%;width:40%;height:40%")}<i style="left:60%;top:60%;width:26%;height:26%"></i></div><b class="oui">Oui</b></div>
      <div><div style="display:flex;gap:6px" role="img" aria-label="Source dans le chunk voisin de celui de : ${esc(nom)}"><div class="chunk">${ic("left:30%;top:30%;width:40%;height:40%")}</div><div class="chunk non"><i style="left:37%;top:37%;width:26%;height:26%;opacity:.5"></i></div></div><b class="nn">Non</b></div>
      <span>${rich(b.text)}</span>
    </div>`;
  };
  R.chunk.titre = () => "Même chunk";

  R.row = (b, ctx, prof) => `<div class="row">${(b.blocks || []).map((x) => `<div class="col fig">${bloc(x, ctx, prof + 1)}</div>`).join("")}</div>`;

  // Étape : numéro et acteur donnés par app.js (ctx.etapes : WeakMap bloc → {n, mode}).
  const GAUCHE = new Set(["text", "recipe", "items", "numbers", "pass"]);
  R.step = function (b, ctx, prof) {
    const info = (ctx.etapes && ctx.etapes.get(b)) || { n: "", mode: "simple" };
    const anc = info.n ? ` id="etape-${info.n}"` : "";
    const corps = (b.blocks || []);
    if (info.mode === "acteurs") {
      const qui = b.actor === "autre" ? "autre" : b.actor === "toi" ? "toi" : "";
      const label = b.actorLabel || (qui === "autre" ? "Un autre joueur" : qui === "toi" ? "Toi" : "");
      const g = corps.filter((x) => GAUCHE.has(x && x.type));
      const d = corps.filter((x) => !GAUCHE.has(x && x.type));
      return `<section class="etape ${qui || "neutre"}"${anc}>
        <div><div class="qn">${info.n ? `<span class="num p">${info.n}</span>` : ""}${qui ? `<span class="qui ${qui}">${esc(label)}</span>` : ""}</div>
          <h2>${esc(b.title)}</h2>${g.map((x) => bloc(x, ctx, prof + 1)).join("")}</div>
        ${d.length ? `<div>${d.map((x) => bloc(x, ctx, prof + 1)).join("")}</div>` : ""}
      </section>`;
    }
    return `<section class="sec"${anc}>
      <div class="tete">${info.n ? `<span class="num">${info.n}</span>` : ""}<h2>${esc(b.title)}</h2></div>
      ${corps.map((x) => bloc(x, ctx, prof + 1)).join("")}
    </section>`;
  };

  // Blocs toujours présentés dans une carte (titre par défaut quand le bloc n'en donne pas).
  const EN_CARTE = new Set(["recipe", "power", "range", "chunk", "items", "player", "clip"]);

  function bloc(b, ctx, prof) {
    prof = prof || 0;
    if (!b || typeof b !== "object" || !R[b.type]) {
      console.warn("Wiki : bloc inconnu", b && b.type);
      return `<p class="inconnu">Bloc inconnu : ${esc(b && b.type)}</p>`;
    }
    let corps;
    try {
      corps = R[b.type](b, ctx, prof);
    } catch (e) {
      console.warn("Wiki : bloc illisible", b.type, e);
      return `<p class="inconnu">Bloc illisible : ${esc(b.type)}</p>`;
    }
    if (b.type === "step" || b.type === "row" || b.type === "text") return corps;
    const titre = b.title || (R[b.type].titre ? R[b.type].titre(b, ctx) : "");
    // Au premier niveau, un titre devient une section h2 ; dans une étape, une carte avec h3.
    if (prof === 0) {
      const inner = EN_CARTE.has(b.type) ? `<div class="card">${corps}</div>` : corps;
      return titre ? `<section class="sec"><h2>${esc(titre)}</h2>${inner}</section>` : inner;
    }
    if (EN_CARTE.has(b.type) || b.title) return `<div class="card">${titre ? `<h3>${esc(titre)}</h3>` : ""}${corps}</div>`;
    return corps;
  }

  // Vignette d'une étape pour la frise : première capture, sinon première icône d'objet.
  function vignette(b, ctx) {
    const imgs = [], icos = [], clips = [];
    (function parcourt(x) {
      if (!x || typeof x !== "object") return;
      if (Array.isArray(x)) return x.forEach(parcourt);
      switch (x.type) {
        case "shot": case "hero": imgs.push(x.img); break;
        case "clip": clips.push(x.img); break;
        case "states": case "phones": (x.list || []).forEach((s) => imgs.push(s.img)); break;
        case "player": (x.steps || []).forEach((s) => imgs.push(s.img)); break;
        case "recipe": case "pass": case "range": case "chunk": icos.push(x.item); break;
        case "items": (x.list || []).forEach((s) => icos.push(s.item)); break;
        case "flow": (x.nodes || []).forEach((s) => (s.img ? imgs.push(s.img) : s.item && icos.push(s.item))); break;
      }
      (x.blocks || []).forEach(parcourt);
    })(b.blocks || []);
    if (imgs.length) return img(imgs[0], "");
    if (icos.length) { const o = objet(ctx, icos[0]); if (o.icone) return `<img class="px" src="${esc(o.icone)}" alt="" loading="lazy">`; }
    if (clips.length && !reduit()) return img(clips[0], "");
    return "";
  }

  // ---------- Interactions (délégation sur le document) ----------
  function affiche(fig) {
    const c = fig.querySelector("canvas[data-affiche]");
    if (!c) return;
    const im = new Image();
    im.onload = () => {
      c.width = im.naturalWidth; c.height = im.naturalHeight;
      try { c.getContext("2d").drawImage(im, 0, 0); } catch (e) { /* image illisible */ }
    };
    im.onerror = () => {
      const s = document.createElement("span");
      s.className = "manque"; s.dataset.manque = fig.dataset.clip; s.textContent = fig.dataset.alt || "Image manquante";
      c.replaceWith(s);
      (window.__wikiManques = window.__wikiManques || []).push(fig.dataset.clip);
    };
    im.src = fig.dataset.clip;
  }

  function apres(root) {
    root.querySelectorAll("[data-power]").forEach(majPower);
    root.querySelectorAll("[data-portee]").forEach(majPortee);
    root.querySelectorAll('[data-clip][data-joue="false"]').forEach(affiche);
  }

  function brancher(doc) {
    doc.addEventListener("click", (e) => {
      const t = e.target.closest("button");
      if (!t) return;
      if (t.dataset.pt != null) {
        const fig = t.closest("[data-hero]");
        const i = t.dataset.pt;
        fig.querySelectorAll("[data-pt]").forEach((b) => b.setAttribute("aria-pressed", b === t));
        fig.querySelectorAll("[data-info]").forEach((d) => { d.hidden = d.dataset.info !== i; });
        const n = fig.querySelector("[data-num]");
        if (n) n.textContent = +i + 1;
      } else if (t.dataset.pas != null) {
        const lec = t.closest("[data-lecteur]");
        lec.querySelectorAll("[data-pas]").forEach((b) => b.setAttribute("aria-pressed", b === t));
        lec.querySelectorAll("[data-cadre]").forEach((c) => { c.hidden = c.dataset.cadre !== t.dataset.pas; });
      } else if (t.dataset.stp) {
        const el = t.closest("[data-power]");
        const k = t.dataset.stp;
        const lim = k === "cartes" ? 40 : 10;
        el.dataset[k] = Math.max(0, Math.min(lim, +el.dataset[k] + +t.dataset.d));
        majPower(el);
      } else if (t.dataset.vit) {
        const el = t.closest("[data-power]");
        el.dataset.vit = t.dataset.vit;
        majPower(el);
      } else if (t.hasAttribute("data-clip-btn")) {
        const fig = t.closest("[data-clip]");
        const joue = fig.dataset.joue !== "true";
        fig.dataset.joue = joue;
        const note = fig.querySelector(":scope > .note");
        fig.innerHTML = clipCorps(fig.dataset.clip, fig.dataset.alt, joue) + (note ? note.outerHTML : "");
        if (!joue) affiche(fig);
        const b = fig.querySelector("[data-clip-btn]");
        if (b) b.focus();
      }
    });
    doc.addEventListener("input", (e) => {
      const el = e.target.closest && e.target.closest("[data-portee]");
      if (el && e.target.matches("[data-dist]")) majPortee(el);
    });
  }

  window.WikiBlocs = { esc, rich, norm, img, icone, objet, bloc, vignette, apres, brancher, types: Object.keys(R) };
})();
