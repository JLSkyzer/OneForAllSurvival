// Wiki Lost Island : charge les fiches de data/*.json et les affiche par chapitre.
(function () {
  "use strict";

  const CHAPTERS = ["electricite", "economie", "telephone", "interface", "medecin", "agriculteur", "policier", "maire", "casino", "loisirs", "substances"];
  const main = document.getElementById("main");
  const tabs = document.getElementById("tabs");
  const q = document.getElementById("q");
  const onlyLive = document.getElementById("onlyLive");
  const data = {};

  const esc = (s) => String(s == null ? "" : s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  // `code` entre accents graves devient <code>.
  const rich = (s) => esc(s).replace(/`([^`]+)`/g, "<code>$1</code>");
  const norm = (s) => String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");

  const ICON = {
    machine: '<path d="M4 7h16v12H4z M8 7V4h8v3 M8 12h3 M13 12h3 M8 15h8"/>',
    objet: '<path d="M6 4h12l-2 16H8z M9 9h6"/>',
    bloc: '<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z M12 12l8-4.5 M12 12v9 M12 12L4 7.5"/>',
    systeme: '<path d="M4 6h6v5H4z M14 13h6v5h-6z M10 8.5h2.5a1.5 1.5 0 011.5 1.5v3"/>',
    role: '<circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-4 3.2-6 7-6s7 2 7 6"/>',
    appli: '<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/>',
    commande: '<path d="M4 6l5 6-5 6 M11 18h9"/>',
  };
  const WHO = {
    fabrique: ["Fabriqué par", '<path d="M14 4l6 6-3 3-6-6z M11 7L4 14l3 3 7-7"/>'],
    pose: ["Posé par", '<path d="M12 3l7 4v8l-7 4-7-4V7z"/>'],
    utilise: ["Utilisé par", '<circle cx="12" cy="12" r="3"/><path d="M12 3v3 M12 18v3 M3 12h3 M18 12h3"/>'],
    gere: ["Géré par", '<path d="M5 19V9 M12 19V5 M19 19v-7"/>'],
    casse: ["Cassé par", '<path d="M4 20l7-7 M13 11l7-7 M9 4l2 5 M15 15l5 2"/>'],
  };
  const svg = (p) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`;

  function art(c) {
    const im = c.image;
    if (im && im.type === "block") {
      const f = (n) => (n ? `background-image:url(img/block/${encodeURIComponent(n)})` : "");
      return `<div class="cube" aria-hidden="true"><div class="f-top" style="${f(im.top || im.side)}"></div><div class="f-front" style="${f(im.front || im.side)}"></div><div class="f-side" style="${f(im.side || im.front)}"></div></div>`;
    }
    if (im && im.type === "item") return `<div class="item-big" style="background-image:url(img/item/${encodeURIComponent(im.file)})" aria-hidden="true"></div>`;
    return `<div class="glyph" aria-hidden="true">${svg(ICON[c.kind] || ICON.systeme)}</div>`;
  }

  function flow(steps) {
    return `<div class="flow">${steps.map((s, i) => (i ? "<i>→</i>" : "") + `<span>${esc(s)}</span>`).join("")}</div>`;
  }

  function card(c) {
    const live = c.status === "dispo";
    const who = c.who ? Object.keys(WHO).filter((k) => c.who[k]).map((k) => `<span title="${WHO[k][0]}">${svg(WHO[k][1])}<em>${WHO[k][0]}</em> ${esc(c.who[k])}</span>`).join("") : "";
    return `<article class="card ${live ? "live" : "plan"}" id="${esc(c.id)}">
      <div class="art"><span class="badge ${live ? "live" : "plan"}">${live ? "En jeu" : "Prévu" + (c.lot ? " · " + esc(c.lot) : "")}</span>${c.num ? `<span class="num">n° ${esc(c.num)}</span>` : ""}${art(c)}</div>
      <div class="body">
        <h3>${esc(c.name)}</h3>
        ${c.what ? `<p class="what">${rich(c.what)}</p>` : ""}
        ${who ? `<div class="who">${who}</div>` : ""}
        ${c.flow && c.flow.length ? flow(c.flow) : ""}
        ${c.steps && c.steps.length ? `<ol class="steps">${c.steps.map((s) => `<li>${rich(s)}</li>`).join("")}</ol>` : ""}
        ${c.numbers && c.numbers.length ? `<div class="nums">${c.numbers.map((n) => `<div><small>${esc(n.k)}</small><b>${esc(n.v)}</b></div>`).join("")}</div>` : ""}
        ${c.items && c.items.length ? `<div class="items">${c.items.map((it) => `<span>${it.file ? `<i class="px" style="background-image:url(img/item/${encodeURIComponent(it.file)})"></i>` : ""}${esc(it.name)}</span>`).join("")}</div>` : ""}
        ${c.staff && c.staff.length ? `<details class="staff"${document.body.classList.contains("v-staff") ? " open" : ""}><summary>Pour le staff</summary><ul>${c.staff.map((s) => `<li>${rich(s)}</li>`).join("")}</ul></details>` : ""}
      </div></article>`;
  }

  const visible = (c) => !onlyLive.checked || c.status === "dispo";

  function renderTabs(active) {
    const t = [`<a href="#accueil" class="${active === "accueil" ? "on" : ""}">Accueil</a>`];
    CHAPTERS.forEach((id) => {
      const d = data[id];
      if (!d) return;
      const n = d.cards.filter((c) => c.status === "dispo").length;
      t.push(`<a href="#${id}" class="${active === id ? "on" : ""}">${esc(d.title)}${n ? `<small>● ${n}</small>` : ""}</a>`);
    });
    tabs.innerHTML = t.join("");
  }

  function renderHome() {
    const rows = (pred) => CHAPTERS.filter((id) => data[id]).map((id) => {
      const cs = data[id].cards.filter(pred);
      return cs.length ? `<div class="row"><a href="#${id}">${esc(data[id].title)}</a><span class="pill ${pred === isLive ? "live" : "plan"}">${cs.length} fiche${cs.length > 1 ? "s" : ""}</span></div>` : "";
    }).join("");
    main.innerHTML = `<h1>Lost Island, en fiches</h1>
      <p class="intro">Chaque bloc et chaque système tient sur une fiche : à quoi il sert, qui le fabrique, qui le pose, qui peut le casser. Les fiches <b>En jeu</b> existent sur le serveur ; les fiches <b>Prévu</b> sont décidées mais pas encore codées.</p>
      <div class="board">
        <div class="panel"><h2>En jeu</h2>${rows(isLive)}</div>
        <div class="panel"><h2>Prévu</h2>${rows(isPlan)}</div>
        <div class="panel"><h2>Les trois règles à retenir</h2>
          <div class="row">Ton métier décide de ce que tu fabriques.</div>
          <div class="row">Tes pièces vont sur toi ou dans ton coffre-fort, nulle part ailleurs.</div>
          <div class="row">Électricité et téléphone se paient seuls à minuit.</div>
        </div>
      </div>`;
  }
  const isLive = (c) => c.status === "dispo";
  const isPlan = (c) => c.status !== "dispo";

  function renderChapter(id) {
    const d = data[id];
    const cards = d.cards.filter(visible);
    main.innerHTML = `<h1>${esc(d.title)}</h1><p class="intro">${rich(d.intro || "")}</p>
      ${d.diagrams && d.diagrams.length ? `<div class="diagrams">${d.diagrams.map((g) => `<div class="diagram"><b>${esc(g.title)}</b>${flow(g.steps || [])}</div>`).join("")}</div>` : ""}
      <h2>Fiches</h2>
      ${cards.length ? `<div class="grid">${cards.map(card).join("")}</div>` : `<p class="empty">Rien de ce chapitre n'est encore en jeu. Décoche « En jeu seulement » pour voir ce qui est prévu.</p>`}`;
  }

  function renderSearch(term) {
    const t = norm(term).split(/\s+/).filter(Boolean);
    const hits = [];
    CHAPTERS.forEach((id) => (data[id] ? data[id].cards : []).forEach((c) => {
      if (!visible(c)) return;
      const txt = norm(JSON.stringify(c) + " " + data[id].title);
      if (t.every((w) => txt.includes(w))) hits.push(c);
    }));
    main.innerHTML = `<h1>Recherche</h1><p class="intro">${hits.length} fiche${hits.length > 1 ? "s" : ""} pour « ${esc(term)} ».</p>
      ${hits.length ? `<div class="grid">${hits.map(card).join("")}</div>` : `<p class="empty">Aucune fiche ne correspond. Essaie un mot plus court : « coffre », « antenne », « amende ».</p>`}`;
  }

  function route() {
    const term = q.value.trim();
    let id = location.hash.slice(1) || "accueil";
    if (term) { renderTabs(""); renderSearch(term); return; }
    if (!data[id] && id !== "accueil") {
      // Lien direct vers une fiche : ouvrir son chapitre puis y défiler.
      const owner = CHAPTERS.find((ch) => data[ch] && data[ch].cards.some((c) => c.id === id));
      if (owner) { renderTabs(owner); renderChapter(owner); const el = document.getElementById(id); if (el) el.scrollIntoView(); return; }
      id = "accueil";
    }
    renderTabs(id);
    if (id === "accueil") renderHome(); else renderChapter(id);
    window.scrollTo(0, 0);
  }

  function setView(v) {
    document.body.classList.toggle("v-joueurs", v === "joueurs");
    document.body.classList.toggle("v-staff", v === "staff");
    document.querySelectorAll(".seg button").forEach((b) => b.setAttribute("aria-pressed", b.dataset.view === v));
    try { localStorage.setItem("wiki-view", v); } catch (e) { /* stockage indisponible */ }
    route();
  }

  document.querySelectorAll(".seg button").forEach((b) => b.addEventListener("click", () => setView(b.dataset.view)));
  let timer;
  q.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(route, 150); });
  onlyLive.addEventListener("change", route);
  window.addEventListener("hashchange", () => { q.value = ""; route(); });
  document.addEventListener("keydown", (e) => { if (e.key === "/" && document.activeElement !== q) { e.preventDefault(); q.focus(); } });

  Promise.all(CHAPTERS.map((id) => fetch(`data/${id}.json`).then((r) => (r.ok ? r.json() : null)).then((d) => { if (d) data[id] = d; }).catch(() => {})))
    .then(() => {
      let v = "joueurs";
      try { v = localStorage.getItem("wiki-view") || v; } catch (e) { /* stockage indisponible */ }
      setView(v);
    });
})();
