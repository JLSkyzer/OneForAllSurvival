// Wiki Lost Island, version 2 : navigation par « # », accueil, chapitres, articles, recherche.
// Contenu : data/<chapitre>.json (version 2 : articles ; version 1 : fiches « cards », affichées dans le même style).
(function () {
  "use strict";

  const W = window.WikiBlocs;
  const main = document.getElementById("main");
  const nav = document.getElementById("nav");
  const enJeu = document.getElementById("enJeu");
  // Page et scripts de deux versions différentes (cache du navigateur juste après une publication).
  if (!W || !main || !nav || !enJeu) {
    const ou = main || document.body;
    ou.insertAdjacentHTML("afterbegin", '<p style="padding:24px;font:18px sans-serif">Le wiki vient d\'être mis à jour. <a href="javascript:location.reload()">Recharge la page</a>.</p>');
    document.body.dataset.pret = "1";
    return;
  }
  const { esc, rich, norm, img } = W;

  const vide = () => Object.create(null);
  const S = { accueil: {}, objets: vide(), recettes: [], reperes: vide(), data: vide(), ordre: [], caches: [], echecs: [] };
  const a = (t, k) => (t && Object.prototype.hasOwnProperty.call(t, k) ? t[k] : undefined);
  let requete = "";
  let premier = true;
  let versRecherche = false;
  let vue = null; // clé de la page affichée : un changement d'ancre seul ne la redessine pas

  // ---------- Stockage protégé ----------
  const lire = (k) => { try { return window.localStorage.getItem(k); } catch (e) { return null; } };
  const ecrire = (k, v) => { try { window.localStorage.setItem(k, v); } catch (e) { /* stockage indisponible */ } };
  const staff = () => document.body.classList.contains("v-staff");

  // ---------- Données ----------
  // « no-cache » : le contenu change à chaque publication, le navigateur revalide toujours.
  const json = (url) => fetch(url, { cache: "no-cache" }).then((r) => (r.ok ? r.json() : Promise.reject(r.status))).then((d) => ({ ok: true, d }), () => ({ ok: false }));
  const objetJSON = (x) => x && typeof x === "object" && !Array.isArray(x);
  const table = (x) => Object.assign(vide(), objetJSON(x) ? x : {});
  const estV2 = (d) => d && d.v === 2;
  const enJeuV1 = (c) => c.status === "dispo";
  const nbEnJeu = (d) => (estV2(d) ? d.articles.length : d.cards.filter(enJeuV1).length);
  const ctx = () => ({ objets: S.objets, recettes: S.recettes, reperes: S.reperes, lien: titreLien, etapes: new WeakMap() });
  const ident = (x) => objetJSON(x) && typeof x.id === "string" && x.id;
  // Un chapitre illisible ne doit jamais bloquer les autres.
  function propre(id, d) {
    if (!objetJSON(d)) return null;
    if (estV2(d)) {
      d.articles = (Array.isArray(d.articles) ? d.articles : []).filter(ident);
      d.planned = (Array.isArray(d.planned) ? d.planned : []).filter(ident);
    } else if (Array.isArray(d.cards)) {
      d.cards = d.cards.filter(ident);
    } else return null;
    d.title = typeof d.title === "string" ? d.title : id;
    return d;
  }

  // Lien « #… » → titre lisible (problèmes).
  function titreLien(href) {
    const r = resoudre(String(href || "").replace(/^#/, ""));
    if (r.art) return r.art.title;
    if (r.fiche) return r.fiche.name;
    if (r.prevu) return r.prevu.name;
    if (r.chap) return S.data[r.chap].title;
    return "";
  }

  // Identifiant de fiche → article qui la couvre, élément prévu (version 2) ou fiche (version 1).
  // `ch` d'abord : #economie/maire vise la fiche « maire » d'Économie, pas celle d'un autre chapitre.
  function cherchePartout(id, ch) {
    const tous = (ch ? [ch] : []).concat(S.ordre, S.caches);
    for (const c of tous) {
      const d = S.data[c];
      if (estV2(d)) {
        const art = d.articles.find((x) => Array.isArray(x.covers) && x.covers.includes(id));
        if (art) return { chap: c, art };
        const p = d.planned.find((x) => x.id === id);
        if (p) return { chap: c, prevu: p };
      } else {
        const f = d.cards.find((x) => x.id === id);
        if (f) return { chap: c, fiche: f };
      }
    }
    return null;
  }

  function resoudre(h) {
    const parts = h.split("/").map((p) => { try { return decodeURIComponent(p); } catch (e) { return p; } });
    if (!parts[0] || parts[0] === "accueil") return { page: "accueil" };
    if (S.echecs.includes(parts[0])) return { page: "erreur", quoi: parts[0] };
    const d = a(S.data, parts[0]);
    if (d) {
      if (!parts[1]) return { page: "chapitre", chap: parts[0] };
      if (estV2(d)) {
        const art = d.articles.find((x) => x.id === parts[1]);
        if (art) return { page: "article", chap: parts[0], art, ancre: parts[2] };
      }
      const o = cherchePartout(parts[1], parts[0]);
      if (o) return Object.assign({ page: o.art ? "article" : "chapitre", redirige: !!o.art }, o);
      return { page: "chapitre", chap: parts[0], ancre: parts[1] };
    }
    const o = cherchePartout(parts[0]);
    if (o) return Object.assign({ page: o.art ? "article" : "chapitre", redirige: !!o.art }, o);
    return { page: "accueil", perdu: h };
  }
  const cle = (r) => r.page + ":" + (r.chap || "") + "/" + (r.art ? r.art.id : "");

  // ---------- En-tête ----------
  function renderNav(actif) {
    const liens = [`<a href="#accueil"${actif === "accueil" ? ' class="on" aria-current="page"' : ""}>Accueil</a>`];
    S.ordre.forEach((id) => {
      const d = S.data[id];
      if (!nbEnJeu(d)) return;
      const o = typeof d.icon === "string" ? a(S.objets, d.icon) : null;
      const ic = o && o.icone ? `<img class="px" src="${esc(o.icone)}" alt="" width="24" height="24">` : "";
      liens.push(`<a href="#${esc(id)}"${actif === id ? ' class="on" aria-current="page"' : ""}>${ic}${esc(d.title)}</a>`);
    });
    nav.innerHTML = liens.join("");
    // Sur téléphone la barre défile : le chapitre ouvert reste visible.
    const on = nav.querySelector(".on");
    if (on && nav.scrollWidth > nav.clientWidth) nav.scrollLeft = Math.max(0, on.offsetLeft - nav.offsetLeft - 16);
  }

  // ---------- Accueil ----------
  function suite(p) {
    return (Array.isArray(p) ? p : []).map((x, i, l) => {
      const sep = i && !l[i - 1].or ? '<span aria-hidden="true">›</span>' : "";
      if (x.item) return sep + `<span class="ico">${W.icone(ctx(), x.item)}</span>`;
      if (x.or) return `<span>ou</span>`;
      return sep + `<span class="${x.tone === "ok" ? "ok" : x.tone === "bad" ? "bad" : "t"}">${esc(x.text)}</span>`;
    }).join("");
  }
  // Pictogrammes des règles de l'accueil (accueil.json, rules[].icon).
  const PICTO = {
    metier: '<path d="M14 4l6 6-3 3-6-6z M11 7L4 14l3 3 7-7"/>',
    coffre: '<rect x="4" y="5" width="16" height="14" rx="2"/><circle cx="12" cy="12" r="3"/><path d="M12 9v-1 M15 12h1 M7 19v2 M17 19v2"/>',
    courant: '<path d="M13 2L5 14h6l-1 8 8-12h-6z"/>',
  };

  function alerteChargement() {
    if (!S.echecs.length) return "";
    return `<p class="vide-msg" role="alert">Chargement impossible : ${S.echecs.map((x) => `data/${esc(x)}.json`).join(", ")}. Recharge la page ; si ça continue, préviens le staff.</p>`;
  }

  function renderAccueil(r) {
    const A = S.accueil;
    const actions = (Array.isArray(A.actions) ? A.actions : []).filter(objetJSON).map((x) => {
      const ims = (Array.isArray(x.imgs) ? x.imgs : []).filter(objetJSON);
      const haut = ims.length === 1 ? img(ims[0].img, ims[0].alt, "f", ' loading="eager"') : ims.map((m) => img(m.img, m.alt, "t", ' loading="eager"')).join("");
      return `<a class="act${x.tone === "bad" ? " urgence" : ""}" href="${esc(x.href)}">
        ${ims.length ? `<div class="img">${haut}</div>` : ""}
        <div class="bas"><strong>${esc(x.title)}</strong><div class="suite">${suite(x.path)}</div></div></a>`;
    }).join("");
    const avec = S.ordre.filter((id) => nbEnJeu(S.data[id]));
    const sans = S.ordre.filter((id) => !nbEnJeu(S.data[id]));
    const tuiles = avec.map((id) => {
      const d = S.data[id];
      const n = nbEnJeu(d);
      const cov = d.cover || a(A.chapterCovers, id);
      const compte = estV2(d) ? `${n} article${n > 1 ? "s" : ""}` : `${n} fiche${n > 1 ? "s" : ""} en jeu`;
      return cov
        ? `<a class="tuile" href="#${esc(id)}">${img(cov, "")}<span>${esc(d.title)}<small>${compte}</small></span></a>`
        : `<a class="tuile vide" href="#${esc(id)}"><span>${esc(d.title)}<small>${compte}</small></span></a>`;
    }).join("");
    const regles = (Array.isArray(A.rules) ? A.rules : []).filter(objetJSON).map((x) =>
      `<div class="regle"><span class="ico" aria-hidden="true">${svg(PICTO[x.icon] || PICTO.metier)}</span><span>${rich(x.text)}</span></div>`).join("");
    const vus = (Array.isArray(A.seen) ? A.seen : []).filter(objetJSON).map((v) => `<a class="tuile" href="${esc(v.href)}">${img(v.img, v.alt, "", v.position ? ` style="object-position:${esc(v.position)}"` : "")}<span>${esc(v.title)}</span></a>`).join("");
    main.innerHTML = `<div class="w">
      <section class="sec">
        ${alerteChargement()}
        ${r.perdu ? `<p class="vide-msg" role="status">La page « ${esc(r.perdu)} » ${S.echecs.length ? "est introuvable pour l'instant (chargement incomplet)" : "n'existe pas ou plus"}. Voici l'accueil.</p>` : ""}
        <h1>Qu'est-ce que tu veux faire ?</h1>
        <div class="cherche" role="search">
          <label for="q" class="cache-vue">Chercher dans le wiki</label>
          <input id="q" type="search" placeholder="Un objet, un bloc, un métier…" autocomplete="off" value="${esc(requete)}" aria-describedby="q-aide">
          <span id="q-aide" class="cache-vue">Touche « / » pour revenir ici depuis n'importe quelle page.</span>
        </div>
        <div class="resultats" id="resultats" aria-live="polite" hidden></div>
        <div class="veux" data-hors-recherche>${actions}</div>
      </section>
      ${regles ? `<section class="sec" data-hors-recherche><h2>Les trois règles à retenir</h2><div class="regles">${regles}</div></section>` : ""}
      <section class="sec" data-hors-recherche>
        <h2>Les chapitres</h2>
        <div class="chap">${tuiles}</div>
        ${sans.length ? `<div class="prevu"><span class="mu">Bientôt :</span>${sans.map((id) => `<a href="#${esc(id)}">${esc(S.data[id].title)}</a>`).join("")}</div>` : ""}
      </section>
      ${vus ? `<section class="sec" data-hors-recherche><h2>Vu en jeu</h2><div class="chap">${vus}</div></section>` : ""}
    </div>`;
    const q = document.getElementById("q");
    let t;
    q.addEventListener("input", () => {
      clearTimeout(t);
      t = setTimeout(() => {
        requete = q.value;
        // Gardé dans l'historique : un retour arrière depuis un résultat retrouve la recherche.
        try { history.replaceState({ q: requete }, "", "#accueil"); } catch (e) { /* historique indisponible */ }
        chercher();
      }, 120);
    });
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
      if (k === "item" && typeof x[k] === "string") out.push((a(S.objets, x[k]) || {}).nom || "");
      else textes(x[k], out);
    });
    return out;
  }
  function construireIndex() {
    index = [];
    const c = ctx();
    S.ordre.forEach((ch) => {
      const d = S.data[ch];
      const nomCh = norm(d.title);
      index.push({ titre: d.title, ou: "Chapitre", href: `#${ch}`, chapitre: true, plan: !nbEnJeu(d),
        vig: d.cover || a(S.accueil.chapterCovers, ch) ? img(d.cover || a(S.accueil.chapterCovers, ch), "") : svg(KIND.systeme),
        t: nomCh, x: norm(textes([d.intro, d.tagline, d.diagrams], []).join(" ")) });
      if (estV2(d)) {
        d.articles.forEach((art) => {
          const vig = art.cover ? img(art.cover, "") : W.vignette({ blocks: art.blocks }, c);
          index.push({ titre: art.title, ou: d.title + (art.group ? " › " + art.group : ""), href: `#${ch}/${art.id}`, vig, plan: false,
            t: norm(art.title), x: nomCh + " " + norm(textes([art.lead, art.chips, art.blocks, art.staff, art.group], []).join(" ")) });
        });
        d.planned.forEach((p) => index.push({ titre: p.name, ou: d.title, href: `#${ch}/${p.id}`, vig: svg(KIND.systeme), plan: true,
          t: norm(p.name), x: nomCh + " " + norm(textes([p.what, p.lot, p.staff], []).join(" ")) }));
      } else {
        d.cards.forEach((f) => {
          index.push({ titre: f.name, ou: d.title, href: `#${ch}/${f.id}`, vig: visuelV1(f), plan: !enJeuV1(f),
            t: norm(f.name), x: nomCh + " " + norm(textes([f.what, f.steps, f.numbers, f.flow, f.who, (f.items || []).map((i) => i && i.name), f.staff, f.lot], []).join(" ")) });
        });
      }
    });
  }
  const MAX_RES = 60;
  function chercher() {
    const box = document.getElementById("resultats");
    if (!box) return;
    const mots = norm(requete).split(/\s+/).filter(Boolean);
    document.querySelectorAll("[data-hors-recherche]").forEach((e) => { e.hidden = mots.length > 0; });
    box.hidden = !mots.length;
    if (!mots.length) { box.innerHTML = ""; return; }
    if (!index) construireIndex();
    const tous = index.filter((e) => (!enJeu.checked || !e.plan) && mots.every((m) => e.t.includes(m) || e.x.includes(m)))
      .map((e) => ({ e, s: mots.filter((m) => e.t.includes(m)).length * 2 + (e.plan ? 0 : 1) + (e.chapitre && e.t === mots.join(" ") ? 5 : 0) }))
      .sort((x, y) => y.s - x.s).map((h) => h.e);
    const hits = tous.slice(0, MAX_RES);
    const n = tous.length;
    box.innerHTML = `<p class="mu" style="margin:0">${n} résultat${n > 1 ? "s" : ""} pour « ${esc(requete.trim())} »${n > MAX_RES ? ` : les ${MAX_RES} premiers sont affichés, ajoute un mot pour préciser` : ""}.${enJeu.checked ? " Ce qui est prévu est caché (« En jeu seulement »)." : ""}</p>` +
      (hits.length ? hits.map((e) => `<a class="res" href="${esc(e.href)}"><span class="vig">${e.vig}</span><div><b>${esc(e.titre)}</b><span class="ou">${esc(e.ou)}</span>${e.plan ? '<span class="badge plan">Prévu</span>' : ""}</div></a>`).join("")
        : `<p class="vide-msg">Rien ne correspond. Essaie un mot plus court : « antenne », « coffre », « amende ».</p>`);
  }

  // ---------- Notes d'équipe ----------
  const notes = (l) => (Array.isArray(l) && l.length ? `<details class="staff"${staff() ? " open" : ""}><summary>Pour le staff</summary><ul>${l.map((s) => `<li>${rich(s)}</li>`).join("")}</ul></details>` : "");
  const montrePrevu = (r) => !enJeu.checked || !!(r.prevu || (r.fiche && !enJeuV1(r.fiche)));
  const avisPrevu = (r) => (enJeu.checked && montrePrevu(r) ? `<p class="mu" role="status" style="margin:0">Montré pour ce lien, bien que « En jeu seulement » soit coché.</p>` : "");
  const decoche = "Décoche « En jeu seulement » pour voir ce qui est prévu.";

  // ---------- Chapitre version 2 ----------
  function renderChapitreV2(id, d, r) {
    const groupes = [];
    d.articles.forEach((art) => {
      let g = groupes.find((x) => x.nom === (art.group || ""));
      if (!g) groupes.push((g = { nom: art.group || "", arts: [] }));
      g.arts.push(art);
    });
    const c = ctx();
    const tuile = (art) => {
      const vig = art.cover ? img(art.cover, "") : W.vignette({ blocks: art.blocks }, c);
      return `<a href="#${esc(id)}/${esc(art.id)}"><div class="vig">${vig}</div><div class="bas"><strong>${esc(art.title)}</strong>${art.lead ? `<span class="mu">${rich(art.lead)}</span>` : ""}</div></a>`;
    };
    const plans = d.planned.map((p) => `<div class="card" id="${esc(p.id)}" tabindex="-1"><span class="k">${p.num ? "n° " + esc(p.num) + " · " : ""}${esc(p.lot || "Prévu")}</span><b>${esc(p.name)}</b>${p.what ? `<span>${rich(p.what)}</span>` : ""}${notes(p.staff)}</div>`).join("");
    const voir = montrePrevu(r);
    main.innerHTML = `<div class="w">
      <section class="sec">
        <nav class="ariane" aria-label="Fil d'Ariane"><a href="#accueil">Accueil</a><span aria-hidden="true">›</span></nav>
        <h1>${esc(d.title)}</h1>
        ${d.tagline ? `<p class="chapeau">${rich(d.tagline)}</p>` : ""}
      </section>
      ${groupes.map((g) => `<section class="sec">${g.nom ? `<h2>${esc(g.nom)}</h2>` : ""}<div class="arts">${g.arts.map(tuile).join("")}</div></section>`).join("")}
      ${!groupes.length ? `<p class="vide-msg">Rien de ce chapitre n'est encore en jeu.${plans && !voir ? " " + decoche : ""}</p>` : ""}
      ${plans && voir ? `<section class="sec"><h2>Prévu</h2>${avisPrevu(r)}<div class="plans">${plans}</div></section>` : ""}
    </div>`;
  }

  // ---------- Article ----------
  function renderArticle(id, d, art) {
    const c = ctx();
    const blocs = (Array.isArray(art.blocks) ? art.blocks : []).slice();
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
      frise = `<nav class="path" aria-label="Les ${etapes.length} étapes">${etapes.map((s, i) => `<a href="#${esc(id)}/${esc(art.id)}/etape-${i + 1}"><div class="vig">${W.vignette(s, c)}</div><span><em>${i + 1}</em>${esc(s.title)}</span></a>`).join("")}</nav>`;
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

    const arts = d.articles;
    const suivant = arts[arts.indexOf(art) + 1];
    const puces = (Array.isArray(art.chips) ? art.chips : []).filter(objetJSON).map((p) => `<div class="puce"><span class="k">${esc(p.k)}</span><b>${rich(p.v)}</b></div>`).join("");
    main.innerHTML = `<div class="w art">
      <section class="sec">
        <div class="tete-art">
          <div>
            <nav class="ariane" aria-label="Fil d'Ariane"><a href="#${esc(id)}">${esc(d.title)}</a>${art.group ? `<span aria-hidden="true">›</span><span>${esc(art.group)}</span>` : ""}</nav>
            <h1>${esc(art.title)}</h1>
            ${art.lead ? `<p class="chapeau">${rich(art.lead)}</p>` : ""}
          </div>
          ${puces ? `<div class="puces">${puces}</div>` : ""}
        </div>
        ${legende}
        ${hero ? W.bloc(hero, c, 1) : ""}
      </section>
      ${frise}
      ${html.join("")}
      ${notes(art.staff)}
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
  function svg(p) { return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${p}</svg>`; }

  function visuelV1(f) {
    const im = objetJSON(f.image) ? f.image : null;
    if (im && im.type === "block") {
      const face = (n) => (n ? `background-image:url('img/block/${encodeURIComponent(n)}')` : "");
      return `<div class="cube" aria-hidden="true"><div class="f-top" style="${face(im.top || im.side)}"></div><div class="f-front" style="${face(im.front || im.side)}"></div><div class="f-side" style="${face(im.side || im.front)}"></div></div>`;
    }
    if (im && im.type === "item") return `<img class="px" src="img/item/${encodeURIComponent(im.file)}" alt="">`;
    return svg(a(KIND, f.kind) || KIND.systeme);
  }
  function flux(l) {
    l = Array.isArray(l) ? l : [];
    return `<div class="flux">${l.map((s, i) => `<span class="etp"><span class="noeud seul"><span>${rich(s)}</span></span>${i < l.length - 1 ? '<span class="sep" aria-hidden="true">›</span>' : ""}</span>`).join("")}</div>`;
  }
  const liste = (x) => (Array.isArray(x) ? x : []);
  function fiche(f) {
    const jeu = enJeuV1(f);
    const qui = objetJSON(f.who) ? Object.keys(QUI).filter((k) => f.who[k]).map((k) => `<span><em>${QUI[k]}</em> ${esc(f.who[k])}</span>`).join("") : "";
    return `<article class="fiche${jeu ? "" : " plan"}" id="${esc(f.id)}" tabindex="-1">
      <div class="haut"><div class="vis">${visuelV1(f)}</div><div>
        <span class="badge ${jeu ? "jeu" : "plan"}">${jeu ? "En jeu" : "Prévu" + (f.lot ? " · " + esc(f.lot) : "")}${f.num ? ` · n° ${esc(f.num)}` : ""}</span>
        <h3>${esc(f.name)}</h3></div></div>
      <div class="corps">
        ${f.what ? `<p class="quoi">${rich(f.what)}</p>` : ""}
        ${qui ? `<div class="qui-v1">${qui}</div>` : ""}
        ${liste(f.flow).length ? flux(f.flow) : ""}
        ${liste(f.steps).length ? `<ol class="v1-etapes">${f.steps.map((s) => `<li><span>${rich(s)}</span></li>`).join("")}</ol>` : ""}
        ${liste(f.numbers).length ? `<div class="v1-chiffres">${f.numbers.filter(objetJSON).map((n) => `<div><small>${esc(n.k)}</small><b>${rich(n.v)}</b></div>`).join("")}</div>` : ""}
        ${liste(f.items).length ? `<div class="v1-objets">${f.items.filter(objetJSON).map((it) => `<span>${it.file ? `<img class="px" src="img/item/${encodeURIComponent(it.file)}" alt="">` : ""}${esc(it.name)}</span>`).join("")}</div>` : ""}
        ${notes(f.staff)}
      </div></article>`;
  }
  function renderChapitreV1(id, d, r) {
    const jeu = d.cards.filter(enJeuV1);
    const plan = d.cards.filter((c) => !enJeuV1(c));
    const voir = montrePrevu(r);
    main.innerHTML = `<div class="w">
      <section class="sec">
        <nav class="ariane" aria-label="Fil d'Ariane"><a href="#accueil">Accueil</a><span aria-hidden="true">›</span></nav>
        <h1>${esc(d.title)}</h1>
        ${d.intro ? `<p class="chapeau">${rich(d.intro)}</p>` : ""}
      </section>
      ${liste(d.diagrams).length ? `<section class="sec"><h2>En schémas</h2><div class="schemas">${d.diagrams.filter(objetJSON).map((g) => `<div class="card"><h3>${esc(g.title)}</h3>${flux(g.steps)}</div>`).join("")}</div></section>` : ""}
      <section class="sec"><h2>En jeu</h2>${jeu.length ? `<div class="v1-grille">${jeu.map(fiche).join("")}</div>` : `<p class="vide-msg">Rien de ce chapitre n'est encore en jeu.${plan.length ? " " + (voir ? "Ce qui est prévu suit." : decoche) : ""}</p>`}</section>
      ${plan.length && voir ? `<section class="sec"><h2>Prévu</h2>${avisPrevu(r)}<div class="v1-grille">${plan.map(fiche).join("")}</div></section>` : ""}
    </div>`;
  }

  function renderErreur(r) {
    main.innerHTML = `<div class="w"><section class="sec"><h1>Chargement impossible</h1>
      <p class="vide-msg" role="alert">Le chapitre « ${esc(r.quoi)} » n'a pas pu être chargé (data/${esc(r.quoi)}.json). Recharge la page ; si ça continue, préviens le staff.</p>
      <p><a href="#accueil">Retour à l'accueil</a></p></section></div>`;
  }

  // ---------- Routage ----------
  // Défile vers la cible de l'adresse (fiche, élément prévu, étape) sans redessiner la page.
  function aller(r, opts) {
    document.querySelectorAll(".vise").forEach((e) => e.classList.remove("vise"));
    const id = r.fiche ? r.fiche.id : r.prevu ? r.prevu.id : r.ancre;
    const cible = id ? document.getElementById(id) : null;
    if (cible && (r.fiche || r.prevu)) cible.classList.add("vise");
    if (opts && opts.garder) return;
    if (cible) { cible.scrollIntoView({ block: "start" }); if (!premier) cible.focus({ preventScroll: true }); }
    else { window.scrollTo(0, 0); if (!premier) main.focus({ preventScroll: true }); }
  }

  function route(opts) {
    opts = opts || {};
    const h = location.hash.slice(1);
    const r = resoudre(h);
    if (r.redirige) history.replaceState(null, "", `#${r.chap}/${r.art.id}`);
    const k = cle(r);
    if (!opts.force && vue === k && r.page !== "accueil" && r.page !== "erreur") {
      aller(r, opts);
      document.body.dataset.route = h || "accueil";
      document.body.dataset.pret = "1";
      return;
    }
    if (r.page === "accueil") requete = (history.state && typeof history.state.q === "string") ? history.state.q : "";
    renderNav(r.page === "accueil" ? "accueil" : r.chap);
    const d = r.chap && S.data[r.chap];
    try {
      if (r.page === "accueil") renderAccueil(r);
      else if (r.page === "erreur") renderErreur(r);
      else if (r.page === "article") renderArticle(r.chap, d, r.art);
      else if (estV2(d)) renderChapitreV2(r.chap, d, r);
      else renderChapitreV1(r.chap, d, r);
      W.apres(main);
    } catch (e) {
      console.error("Wiki : page impossible à afficher", e);
      main.innerHTML = `<div class="w"><p class="vide-msg" role="alert">Cette page n'a pas pu s'afficher. <a href="#accueil">Retour à l'accueil</a></p></div>`;
    }
    vue = k;
    document.title = (r.art ? r.art.title + " · " : d ? d.title + " · " : "") + "Wiki Lost Island";
    aller(r, opts);
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
  // « En jeu seulement » : seule la partie concernée change, le focus reste sur la case et la page ne remonte pas.
  enJeu.addEventListener("change", () => {
    ecrire("wiki-en-jeu", enJeu.checked ? "1" : "0");
    if (document.getElementById("q")) { chercher(); return; }
    const r = resoudre(location.hash.slice(1));
    if (r.page !== "chapitre") return;
    const y = window.scrollY;
    route({ force: true, garder: true });
    window.scrollTo(0, y);
  });
  window.addEventListener("hashchange", () => route());
  document.addEventListener("click", (e) => {
    if (e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
    const lien = e.target.closest && e.target.closest("a[href^='#']");
    if (!lien) return;
    // Lien d'évitement : le focus va au contenu, l'adresse ne change pas.
    if (lien.classList.contains("evite")) {
      e.preventDefault();
      main.focus({ preventScroll: true });
      main.scrollIntoView({ block: "start" });
      return;
    }
    // Lien vers l'adresse déjà affichée (frise cliquée deux fois…) : pas d'événement « hashchange », on défile quand même.
    if (lien.getAttribute("href") === location.hash) { e.preventDefault(); route(); }
  });
  const SAISIE = new Set(["text", "search", "email", "number", "password", "tel", "url"]);
  document.addEventListener("keydown", (e) => {
    if (e.key !== "/" || e.ctrlKey || e.metaKey || e.altKey) return;
    const el = document.activeElement;
    if (el && (el.tagName === "TEXTAREA" || el.isContentEditable || (el.tagName === "INPUT" && SAISIE.has(el.type)))) return;
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
  Promise.all(["accueil", "objets", "recettes", "reperes"].map((n) => json(`data/${n}.json`)))
    .then(([acc, obj, rec, rep]) => {
      if (!acc.ok || !objetJSON(acc.d)) S.echecs.push("accueil");
      S.accueil = acc.ok && objetJSON(acc.d) ? acc.d : {};
      S.objets = table(obj.d); S.recettes = Array.isArray(rec.d) ? rec.d : []; S.reperes = table(rep.d);
      const noms = (x) => (Array.isArray(x) ? x.filter((id) => typeof id === "string" && /^[a-z0-9-]+$/.test(id)) : []);
      S.ordre = noms(S.accueil.chapters);
      S.caches = noms(S.accueil.hidden);
      return Promise.all(S.ordre.concat(S.caches).map((id) => json(`data/${id}.json`).then((x) => {
        const d = x.ok ? propre(id, x.d) : null;
        if (d) S.data[id] = d; else S.echecs.push(id);
      })));
    })
    .then(() => {
      S.ordre = S.ordre.filter((id) => S.data[id]);
      S.caches = S.caches.filter((id) => S.data[id]);
      route();
    })
    .catch((e) => {
      console.error("Wiki : démarrage impossible", e);
      main.innerHTML = `<div class="w"><p class="vide-msg" role="alert">Chargement impossible. Recharge la page.</p></div>`;
      document.body.dataset.pret = "1";
    });
})();
