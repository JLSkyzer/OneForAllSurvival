// Wiki Lost Island, version 2 : navigation par « # », accueil, chapitres, articles, recherche.
// Contenu : data/<chapitre>.json (version 2 : articles ; version 1 : fiches « cards », affichées dans le même style).
(function () {
  "use strict";

  const W = window.WikiBlocs;
  const { esc, rich, norm, img } = W;
  const main = document.getElementById("main");
  const nav = document.getElementById("nav");
  const enJeu = document.getElementById("enJeu");

  const S = { accueil: {}, objets: {}, recettes: [], reperes: {}, data: {}, ordre: [], caches: [] };
  let requete = "";
  let premier = true;
  let versRecherche = false;

  // ---------- Stockage protégé ----------
  const lire = (k) => { try { return window.localStorage.getItem(k); } catch (e) { return null; } };
  const ecrire = (k, v) => { try { window.localStorage.setItem(k, v); } catch (e) { /* stockage indisponible */ } };
  const staff = () => document.body.classList.contains("v-staff");

  // ---------- Données ----------
  const json = (url, defaut) => fetch(url).then((r) => (r.ok ? r.json() : defaut)).catch(() => defaut);
  const estV2 = (d) => d && d.v === 2;
  const enJeuV1 = (c) => c.status === "dispo";
  const nbEnJeu = (d) => (estV2(d) ? (d.articles || []).length : (d.cards || []).filter(enJeuV1).length);
  const ctx = () => ({ objets: S.objets, recettes: S.recettes, reperes: S.reperes, lien: titreLien, etapes: new WeakMap() });

  // Lien « #… » → titre lisible (problèmes, recherche).
  function titreLien(href) {
    const r = resoudre(String(href || "").replace(/^#/, ""));
    if (r.art) return r.art.title;
    if (r.fiche) return r.fiche.name;
    if (r.chap) return S.data[r.chap].title;
    return "";
  }

  // Ancien identifiant de fiche → article qui la couvre (version 2) ou chapitre qui la porte (version 1).
  function ancien(id) {
    const tous = S.ordre.concat(S.caches);
    for (const ch of tous) {
      const d = S.data[ch];
      if (estV2(d)) { const a = (d.articles || []).find((x) => (x.covers || []).includes(id)); if (a) return { chap: ch, art: a }; }
    }
    for (const ch of tous) {
      const d = S.data[ch];
      if (!estV2(d)) { const c = (d.cards || []).find((x) => x.id === id); if (c) return { chap: ch, fiche: c }; }
    }
    return null;
  }

  function resoudre(h) {
    const parts = h.split("/").map((p) => { try { return decodeURIComponent(p); } catch (e) { return p; } });
    if (!parts[0] || parts[0] === "accueil") return { page: "accueil" };
    const d = S.data[parts[0]];
    if (d) {
      if (!parts[1]) return { page: "chapitre", chap: parts[0] };
      if (estV2(d)) {
        const a = (d.articles || []).find((x) => x.id === parts[1]);
        if (a) return { page: "article", chap: parts[0], art: a, ancre: parts[2] };
      }
      const o = ancien(parts[1]);
      if (o) return Object.assign({ page: o.art ? "article" : "chapitre", redirige: true }, o);
      return { page: "chapitre", chap: parts[0], perdu: true };
    }
    const o = ancien(parts[0]);
    if (o) return Object.assign({ page: o.art ? "article" : "chapitre", redirige: true }, o);
    return { page: "accueil", perdu: h };
  }

  // ---------- En-tête ----------
  function renderNav(actif) {
    const liens = [`<a href="#accueil"${actif === "accueil" ? ' class="on" aria-current="page"' : ""}>Accueil</a>`];
    S.ordre.forEach((id) => {
      const d = S.data[id];
      if (!nbEnJeu(d)) return;
      const o = d.icon && S.objets[d.icon];
      const ic = o && o.icone ? `<img class="px" src="${esc(o.icone)}" alt="" width="24" height="24">` : "";
      liens.push(`<a href="#${esc(id)}"${actif === id ? ' class="on" aria-current="page"' : ""}>${ic}${esc(d.title)}</a>`);
    });
    nav.innerHTML = liens.join("");
  }

  // ---------- Accueil ----------
  function suite(p) {
    return (p || []).map((x, i, l) => {
      const sep = i && !l[i - 1].or ? '<span aria-hidden="true">›</span>' : "";
      if (x.item) return sep + `<span class="ico">${W.icone(ctx(), x.item)}</span>`;
      if (x.or) return `<span>ou</span>`;
      return sep + `<span class="${x.tone === "ok" ? "ok" : x.tone === "bad" ? "bad" : "t"}">${esc(x.text)}</span>`;
    }).join("");
  }

  function renderAccueil(r) {
    const A = S.accueil;
    const actions = (A.actions || []).map((a) => {
      const ims = a.imgs || [];
      const haut = ims.length === 1 ? img(ims[0].img, ims[0].alt, "f", ' loading="eager"') : ims.map((m) => img(m.img, m.alt, "t", ' loading="eager"')).join("");
      return `<a class="act${a.tone === "bad" ? " urgence" : ""}" href="${esc(a.href)}">
        ${ims.length ? `<div class="img">${haut}</div>` : ""}
        <div class="bas"><strong>${esc(a.title)}</strong><div class="suite">${suite(a.path)}</div></div></a>`;
    }).join("");
    const avec = S.ordre.filter((id) => nbEnJeu(S.data[id]));
    const sans = S.ordre.filter((id) => !nbEnJeu(S.data[id]));
    const tuiles = avec.map((id) => {
      const d = S.data[id];
      const n = nbEnJeu(d);
      const cov = d.cover || (A.chapterCovers || {})[id];
      const compte = estV2(d) ? `${n} article${n > 1 ? "s" : ""}` : `${n} fiche${n > 1 ? "s" : ""} en jeu`;
      return cov
        ? `<a class="tuile" href="#${esc(id)}">${img(cov, "")}<span>${esc(d.title)}<small>${compte}</small></span></a>`
        : `<a class="tuile vide" href="#${esc(id)}"><span>${esc(d.title)}<small>${compte}</small></span></a>`;
    }).join("");
    const vus = (A.seen || []).map((v) => `<a class="tuile" href="${esc(v.href)}">${img(v.img, v.alt, "", v.position ? ` style="object-position:${esc(v.position)}"` : "")}<span>${esc(v.title)}</span></a>`).join("");
    main.innerHTML = `<div class="w">
      <section class="sec">
        ${r.perdu ? `<p class="vide-msg" role="status">La page « ${esc(r.perdu)} » n'existe pas ou plus. Voici l'accueil.</p>` : ""}
        <h1>Qu'est-ce que tu veux faire ?</h1>
        <div class="cherche" role="search">
          <label for="q" class="cache-vue">Chercher dans le wiki</label>
          <input id="q" type="search" placeholder="Un objet, un bloc, un métier…" autocomplete="off" value="${esc(requete)}" aria-describedby="q-aide">
          <span id="q-aide" class="cache-vue">Touche « / » pour revenir ici depuis n'importe quelle page.</span>
        </div>
        <div class="resultats" id="resultats" aria-live="polite" hidden></div>
        <div class="veux" data-hors-recherche>${actions}</div>
      </section>
      <section class="sec" data-hors-recherche>
        <h2>Les chapitres</h2>
        <div class="chap">${tuiles}</div>
        ${sans.length ? `<div class="prevu"><span class="mu">Bientôt :</span>${sans.map((id) => `<a href="#${esc(id)}">${esc(S.data[id].title)}</a>`).join("")}</div>` : ""}
      </section>
      ${vus ? `<section class="sec" data-hors-recherche><h2>Vu en jeu</h2><div class="chap">${vus}</div></section>` : ""}
    </div>`;
    const q = document.getElementById("q");
    let t;
    q.addEventListener("input", () => { clearTimeout(t); t = setTimeout(() => { requete = q.value; chercher(); }, 120); });
    chercher();
  }

  // ---------- Recherche ----------
  let index = null;
  function textes(x, out) {
    if (x == null) return out;
    if (typeof x === "string") out.push(x);
    else if (Array.isArray(x)) x.forEach((y) => textes(y, out));
    else if (typeof x === "object") Object.keys(x).forEach((k) => {
      if (k === "img" || k === "at" || k === "type" || k === "tone" || k === "link" || k === "x" || k === "y") return;
      if (k === "item" && typeof x[k] === "string") out.push((S.objets[x[k]] || {}).nom || "");
      else textes(x[k], out);
    });
    return out;
  }
  function construireIndex() {
    index = [];
    const c = ctx();
    S.ordre.forEach((ch) => {
      const d = S.data[ch];
      if (estV2(d)) {
        (d.articles || []).forEach((a) => {
          const vig = a.cover ? img(a.cover, "") : W.vignette({ blocks: a.blocks }, c);
          index.push({ titre: a.title, ou: d.title + (a.group ? " › " + a.group : ""), href: `#${ch}/${a.id}`, vig, plan: false,
            t: norm(a.title), x: norm(textes([a.lead, a.chips, a.blocks, a.staff, a.group], []).join(" ")) });
        });
        (d.planned || []).forEach((p) => index.push({ titre: p.name, ou: d.title + " · prévu", href: `#${ch}`, vig: svg(KIND.systeme), plan: true,
          t: norm(p.name), x: norm([p.what, p.lot].join(" ")) }));
      } else {
        (d.cards || []).forEach((f) => {
          const vig = visuelV1(f);
          index.push({ titre: f.name, ou: d.title, href: `#${f.id}`, vig, plan: !enJeuV1(f),
            t: norm(f.name), x: norm(textes([f.what, f.steps, f.numbers, f.flow, f.who, (f.items || []).map((i) => i.name), f.staff, f.lot], []).join(" ")) });
        });
      }
    });
  }
  function chercher() {
    const box = document.getElementById("resultats");
    if (!box) return;
    const mots = norm(requete).split(/\s+/).filter(Boolean);
    document.querySelectorAll("[data-hors-recherche]").forEach((e) => { e.hidden = mots.length > 0; });
    box.hidden = !mots.length;
    if (!mots.length) { box.innerHTML = ""; return; }
    if (!index) construireIndex();
    const hits = index.filter((e) => (!enJeu.checked || !e.plan) && mots.every((m) => e.t.includes(m) || e.x.includes(m)))
      .map((e) => ({ e, s: mots.filter((m) => e.t.includes(m)).length * 2 + (e.plan ? 0 : 1) }))
      .sort((a, b) => b.s - a.s).slice(0, 40).map((h) => h.e);
    box.innerHTML = `<p class="mu" style="margin:0">${hits.length} résultat${hits.length > 1 ? "s" : ""} pour « ${esc(requete.trim())} ».</p>` +
      (hits.length ? hits.map((e) => `<a class="res" href="${esc(e.href)}"><span class="vig">${e.vig}</span><div><b>${esc(e.titre)}</b><span class="ou">${esc(e.ou)}</span>${e.plan ? '<span class="badge plan">Prévu</span>' : ""}</div></a>`).join("")
        : `<p class="vide-msg">Rien ne correspond. Essaie un mot plus court : « antenne », « coffre », « amende ».</p>`);
  }

  // ---------- Notes d'équipe ----------
  const notes = (l) => (l && l.length ? `<details class="staff"${staff() ? " open" : ""}><summary>Pour le staff</summary><ul>${l.map((s) => `<li>${rich(s)}</li>`).join("")}</ul></details>` : "");

  // ---------- Chapitre version 2 ----------
  function renderChapitreV2(id, d) {
    const groupes = [];
    (d.articles || []).forEach((a) => {
      let g = groupes.find((x) => x.nom === (a.group || ""));
      if (!g) groupes.push((g = { nom: a.group || "", arts: [] }));
      g.arts.push(a);
    });
    const c = ctx();
    const tuile = (a) => {
      const vig = a.cover ? img(a.cover, "") : W.vignette({ blocks: a.blocks }, c);
      return `<a href="#${esc(id)}/${esc(a.id)}"><div class="vig">${vig}</div><div class="bas"><strong>${esc(a.title)}</strong>${a.lead ? `<span class="mu">${rich(a.lead)}</span>` : ""}</div></a>`;
    };
    const plans = (d.planned || []).map((p) => `<div class="card"><span class="k">${p.num ? "n° " + esc(p.num) + " · " : ""}${esc(p.lot || "Prévu")}</span><b>${esc(p.name)}</b>${p.what ? `<span>${rich(p.what)}</span>` : ""}</div>`).join("");
    main.innerHTML = `<div class="w">
      <section class="sec">
        <nav class="ariane" aria-label="Fil d'Ariane"><a href="#accueil">Accueil</a><span aria-hidden="true">›</span></nav>
        <h1>${esc(d.title)}</h1>
        ${d.tagline ? `<p class="chapeau">${rich(d.tagline)}</p>` : ""}
      </section>
      ${groupes.map((g) => `<section class="sec">${g.nom ? `<h2>${esc(g.nom)}</h2>` : ""}<div class="arts">${g.arts.map(tuile).join("")}</div></section>`).join("")}
      ${!groupes.length ? `<p class="vide-msg">Rien de ce chapitre n'est encore en jeu.</p>` : ""}
      ${plans && !enJeu.checked ? `<section class="sec"><h2>Prévu</h2><div class="plans">${plans}</div></section>` : ""}
    </div>`;
  }

  // ---------- Article ----------
  function renderArticle(id, d, a) {
    const c = ctx();
    const blocs = (a.blocks || []).slice();
    const etapes = blocs.filter((b) => b && b.type === "step");
    const acteurs = etapes.some((s) => s.actor);
    etapes.forEach((s, i) => c.etapes.set(s, { n: i + 1, mode: acteurs ? "acteurs" : "simple" }));
    const hero = blocs[0] && blocs[0].type === "hero" ? blocs.shift() : null;

    let legende = "";
    if (acteurs) {
      const autres = [...new Set(etapes.filter((s) => s.actor === "autre").map((s) => s.actorLabel || "Un autre joueur"))];
      legende = `<div class="legende-qui"><span class="qui toi">Toi</span>${autres.map((l) => `<span class="qui autre">${esc(l)}</span>`).join("")}<span class="mu">Suis la couleur : elle dit qui joue.</span></div>`;
    }
    let frise = "";
    if (etapes.length >= 3) {
      frise = `<nav class="path" aria-label="Les ${etapes.length} étapes">${etapes.map((s, i) => `<a href="#${esc(id)}/${esc(a.id)}/etape-${i + 1}"><div class="vig">${W.vignette(s, c)}</div><span><em>${i + 1}</em>${esc(s.title)}</span></a>`).join("")}</nav>`;
    }
    // Les étapes à acteurs qui se suivent sont regroupées (comme dans la maquette).
    const html = [];
    let groupe = null;
    blocs.forEach((b) => {
      const h = W.bloc(b, c, 0);
      if (acteurs && b && b.type === "step") { (groupe = groupe || []).push(h); return; }
      if (groupe) { html.push(`<div class="etapes">${groupe.join("")}</div>`); groupe = null; }
      html.push(h);
    });
    if (groupe) html.push(`<div class="etapes">${groupe.join("")}</div>`);

    const arts = d.articles || [];
    const suivant = arts[arts.indexOf(a) + 1];
    const puces = (a.chips || []).map((p) => `<div class="puce"><span class="k">${esc(p.k)}</span><b>${rich(p.v)}</b></div>`).join("");
    main.innerHTML = `<div class="w art">
      <section class="sec">
        <div class="tete-art">
          <div>
            <nav class="ariane" aria-label="Fil d'Ariane"><a href="#${esc(id)}">${esc(d.title)}</a>${a.group ? `<span aria-hidden="true">›</span><span>${esc(a.group)}</span>` : ""}</nav>
            <h1>${esc(a.title)}</h1>
            ${a.lead ? `<p class="chapeau">${rich(a.lead)}</p>` : ""}
          </div>
          ${puces ? `<div class="puces">${puces}</div>` : ""}
        </div>
        ${legende}
        ${hero ? W.bloc(hero, c, 1) : ""}
      </section>
      ${frise}
      ${html.join("")}
      ${notes(a.staff)}
      ${suivant ? `<a class="ensuite" href="#${esc(id)}/${esc(suivant.id)}"><div><span>Ensuite :</span><b>${esc(suivant.title)}</b></div>${suivant.cover ? img(suivant.cover, "") : ""}</a>` : ""}
    </div>`;
  }

  // ---------- Chapitre version 1 (fiches), dans le nouveau style ----------
  const KIND = {
    machine: '<path d="M4 7h16v12H4z M8 7V4h8v3 M8 12h3 M13 12h3 M8 15h8"/>',
    objet: '<path d="M6 4h12l-2 16H8z M9 9h6"/>',
    bloc: '<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z M12 12l8-4.5 M12 12v9 M12 12L4 7.5"/>',
    systeme: '<path d="M4 6h6v5H4z M14 13h6v5h-6z M10 8.5h2.5a1.5 1.5 0 011.5 1.5v3"/>',
    role: '<circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-4 3.2-6 7-6s7 2 7 6"/>',
    appli: '<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/>',
    commande: '<path d="M4 6l5 6-5 6 M11 18h9"/>',
  };
  const QUI = { fabrique: "Fabriqué par", pose: "Posé par", utilise: "Utilisé par", gere: "Géré par", casse: "Cassé par" };
  const svg = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${p}</svg>`;

  function visuelV1(f) {
    const im = f.image;
    if (im && im.type === "block") {
      const face = (n) => (n ? `background-image:url('img/block/${encodeURIComponent(n)}')` : "");
      return `<div class="cube" aria-hidden="true"><div class="f-top" style="${face(im.top || im.side)}"></div><div class="f-front" style="${face(im.front || im.side)}"></div><div class="f-side" style="${face(im.side || im.front)}"></div></div>`;
    }
    if (im && im.type === "item") return `<img class="px" src="img/item/${encodeURIComponent(im.file)}" alt="">`;
    return svg(KIND[f.kind] || KIND.systeme);
  }
  function flux(l) {
    return `<div class="flux">${(l || []).map((s, i) => `<span class="etp"><span class="noeud seul"><span>${rich(s)}</span></span>${i < l.length - 1 ? '<span class="sep" aria-hidden="true">›</span>' : ""}</span>`).join("")}</div>`;
  }
  function fiche(f) {
    const jeu = enJeuV1(f);
    const qui = f.who ? Object.keys(QUI).filter((k) => f.who[k]).map((k) => `<span><em>${QUI[k]}</em> ${esc(f.who[k])}</span>`).join("") : "";
    return `<article class="fiche${jeu ? "" : " plan"}" id="${esc(f.id)}" tabindex="-1">
      <div class="haut"><div class="vis">${visuelV1(f)}</div><div>
        <span class="badge ${jeu ? "jeu" : "plan"}">${jeu ? "En jeu" : "Prévu" + (f.lot ? " · " + esc(f.lot) : "")}${f.num ? ` · n° ${esc(f.num)}` : ""}</span>
        <h3>${esc(f.name)}</h3></div></div>
      <div class="corps">
        ${f.what ? `<p class="quoi">${rich(f.what)}</p>` : ""}
        ${qui ? `<div class="qui-v1">${qui}</div>` : ""}
        ${f.flow && f.flow.length ? flux(f.flow) : ""}
        ${f.steps && f.steps.length ? `<ol class="v1-etapes">${f.steps.map((s) => `<li><span>${rich(s)}</span></li>`).join("")}</ol>` : ""}
        ${f.numbers && f.numbers.length ? `<div class="v1-chiffres">${f.numbers.map((n) => `<div><small>${esc(n.k)}</small><b>${rich(n.v)}</b></div>`).join("")}</div>` : ""}
        ${f.items && f.items.length ? `<div class="v1-objets">${f.items.map((it) => `<span>${it.file ? `<img class="px" src="img/item/${encodeURIComponent(it.file)}" alt="">` : ""}${esc(it.name)}</span>`).join("")}</div>` : ""}
        ${notes(f.staff)}
      </div></article>`;
  }
  function renderChapitreV1(id, d) {
    const jeu = (d.cards || []).filter(enJeuV1);
    const plan = (d.cards || []).filter((c) => !enJeuV1(c));
    main.innerHTML = `<div class="w">
      <section class="sec">
        <nav class="ariane" aria-label="Fil d'Ariane"><a href="#accueil">Accueil</a><span aria-hidden="true">›</span></nav>
        <h1>${esc(d.title)}</h1>
        ${d.intro ? `<p class="chapeau">${rich(d.intro)}</p>` : ""}
      </section>
      ${d.diagrams && d.diagrams.length ? `<section class="sec"><h2>En schémas</h2><div class="schemas">${d.diagrams.map((g) => `<div class="card"><h3>${esc(g.title)}</h3>${flux(g.steps)}</div>`).join("")}</div></section>` : ""}
      <section class="sec"><h2>En jeu</h2>${jeu.length ? `<div class="v1-grille">${jeu.map(fiche).join("")}</div>` : `<p class="vide-msg">Rien de ce chapitre n'est encore en jeu.${enJeu.checked ? "" : " Ce qui est prévu suit."}</p>`}</section>
      ${plan.length && !enJeu.checked ? `<section class="sec"><h2>Prévu</h2><div class="v1-grille">${plan.map(fiche).join("")}</div></section>` : ""}
    </div>`;
  }

  // ---------- Routage ----------
  function route() {
    const h = location.hash.slice(1);
    const r = resoudre(h);
    if (r.redirige) {
      const cible = r.art ? `#${r.chap}/${r.art.id}` : `#${r.chap}`;
      if (r.art) { history.replaceState(null, "", cible); }
    }
    if (r.page !== "accueil") requete = "";
    renderNav(r.page === "accueil" ? "accueil" : r.chap);
    const d = r.chap && S.data[r.chap];
    if (r.page === "accueil") renderAccueil(r);
    else if (r.page === "article") renderArticle(r.chap, d, r.art);
    else if (estV2(d)) renderChapitreV2(r.chap, d);
    else renderChapitreV1(r.chap, d);
    W.apres(main);
    document.title = (r.art ? r.art.title + " · " : d ? d.title + " · " : "") + "Wiki Lost Island";

    let cible = null;
    if (r.fiche) cible = document.getElementById(r.fiche.id);
    else if (r.ancre) cible = document.getElementById(r.ancre);
    if (r.fiche && cible) cible.classList.add("vise");
    if (cible) { cible.scrollIntoView({ block: "start" }); if (!premier) cible.focus({ preventScroll: true }); }
    else { window.scrollTo(0, 0); if (!premier) main.focus({ preventScroll: true }); }
    if (versRecherche && r.page === "accueil") { const q = document.getElementById("q"); if (q) q.focus(); }
    versRecherche = false;
    premier = false;
    document.body.dataset.route = h || "accueil";
    document.body.dataset.pret = "1";
  }

  function setVue(v) {
    document.body.classList.toggle("v-staff", v === "staff");
    document.body.classList.toggle("v-joueurs", v !== "staff");
    document.querySelectorAll(".vue button").forEach((b) => b.setAttribute("aria-pressed", b.dataset.view === v));
    document.querySelectorAll("details.staff").forEach((x) => { x.open = v === "staff"; });
    ecrire("wiki-view", v);
  }

  document.querySelectorAll(".vue button").forEach((b) => b.addEventListener("click", () => setVue(b.dataset.view)));
  enJeu.addEventListener("change", () => { ecrire("wiki-en-jeu", enJeu.checked ? "1" : "0"); route(); });
  window.addEventListener("hashchange", route);
  document.addEventListener("keydown", (e) => {
    if (e.key !== "/" || e.ctrlKey || e.metaKey || e.altKey) return;
    const a = document.activeElement;
    if (a && (a.tagName === "INPUT" || a.tagName === "TEXTAREA" || a.isContentEditable)) return;
    e.preventDefault();
    const q = document.getElementById("q");
    if (q) q.focus();
    else { versRecherche = true; location.hash = "#accueil"; }
  });
  // Image absente : un cadre avec son texte de remplacement, le reste de la page continue.
  document.addEventListener("error", (e) => {
    const t = e.target;
    if (!t || t.tagName !== "IMG" || t.dataset.manque) return;
    const src = t.getAttribute("src");
    (window.__wikiManques = window.__wikiManques || []).push(src);
    const s = document.createElement("span");
    s.className = t.classList.contains("px") ? "mini-manque" : "manque";
    s.dataset.manque = src;
    s.textContent = t.classList.contains("px") ? "?" : t.alt || "Image manquante";
    if (t.alt) s.title = t.alt;
    t.replaceWith(s);
  }, true);
  W.brancher(document);

  // ---------- Démarrage ----------
  setVue(lire("wiki-view") === "staff" ? "staff" : "joueurs");
  enJeu.checked = lire("wiki-en-jeu") === "1";
  Promise.all([json("data/accueil.json", {}), json("data/objets.json", {}), json("data/recettes.json", []), json("data/reperes.json", {})])
    .then(([acc, obj, rec, rep]) => {
      S.accueil = acc || {}; S.objets = obj || {}; S.recettes = Array.isArray(rec) ? rec : []; S.reperes = rep || {};
      S.ordre = (S.accueil.chapters || []).slice();
      S.caches = (S.accueil.hidden || []).slice();
      return Promise.all(S.ordre.concat(S.caches).map((id) => json(`data/${id}.json`, null).then((d) => { if (d) S.data[id] = d; })));
    })
    .then(() => {
      S.ordre = S.ordre.filter((id) => S.data[id]);
      S.caches = S.caches.filter((id) => S.data[id]);
      route();
    });
})();
