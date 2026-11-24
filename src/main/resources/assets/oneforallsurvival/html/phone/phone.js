// Le téléphone du mod. Java appelle window.phone.render(view) à chaque état reçu du serveur ; la page répond par
// window.oneforall.send(...) (bridge.js). Contrat :
//   page -> Java : {"a":"ready"} ; {"a":"layout","rects":{"<id>":[x,y,w,h],...}} (px CSS, après chaque changement
//   d'écran, défilement ou redimensionnement) ; {"a":"unlock","code":"…"} ;
//   {"a":"setup","code":"…","wallpaper":"…"} (le fond choisi par l'assistant, envoyé avec le code) ; {"a":"lock"} ;
//   {"a":"changeCode","old":"…","new":"…"} ; {"a":"wallpaper","id":"…"} ; {"a":"airplane","on":true|false} ;
//   {"a":"erase","code":"…"} ; lot T2 : {"a":"insertSim","slot":N} (entier de 0 à 40) ; {"a":"removeSim"} ;
//   {"a":"requestSim","op":"<identifiant d'opérateur, 12 hexadécimaux>"} ; {"a":"antennaApp"}.
//   Java -> page : view = {session, lock: "SETUP"|"LOCKED"|"UNLOCKED", battery, serverMillis, zoneOffsetSeconds,
//   wallpaper, airplane, owner, ownerName, attemptsLeft, lockoutSeconds, last, net}, avec net = {sim, operator, bars,
//   number, holder, founderApp, choices: [{slot, number, operator, validity}], requestable: [{id, name, bars}]}.
//   Verrouillé, net n'a ni number ni holder, ses listes sont vides et sim est grossier (ACTIVE s'y lit « présente ») :
//   la page montre ce qu'elle reçoit, sans rien deviner. Boîtes de layout du lot T2 : row-sim, sim-choice-<case>,
//   sim-remove, sim-request, request-<id>, app-antenna, status-bars (et sim-close, la fermeture de la feuille).
// Le serveur fait foi : la page ne juge aucun code, elle les transmet. Tout texte venu de la vue passe par textContent.
// Un téléphone neuf (lock SETUP) montre l'assistant de première configuration : Bonjour, Configuration rapide, Fond
// d'écran, le code (écran #lock), puis Tout est prêt. Rien n'est configuré avant l'envoi du code : fermer avant laisse
// un téléphone neuf, et la prochaine ouverture reprend l'assistant au début.
// Avec une adresse qui finit par #mock, une fausse vue et un faux serveur tiennent lieu du jeu : un téléphone neuf
// (l'assistant), ou déjà codé (1234) après phoneMock.locked().
(function () {
  'use strict';

  var CODE_LENGTH = 4;
  var WALLPAPERS = ['aube', 'lagon', 'jungle', 'nuit'];
  var LOCKS = ['SETUP', 'LOCKED', 'UNLOCKED'];
  var SIMS = ['NONE', 'UNKNOWN', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'];
  // Les textes d'état de la carte et du réseau : mêmes noms que PhoneStatusText.Shown et mêmes textes que fr_fr.json
  // (garde : PhoneStatusTextTest, qui relit cette table). OPERATOR (le nom de l'opérateur) et NUMBER (le numéro) viennent
  // de la vue.
  var STATUS_TEXTS = {
    NO_SIM: 'Pas de SIM',
    UNRECOGNIZED: 'SIM non reconnue',
    SUSPENDED: 'SIM suspendue',
    INACTIVE: 'Carte SIM inactive',
    NO_SERVICE: 'Pas de service',
    AIRPLANE: 'Mode avion',
    NO_CARD: 'Aucune carte SIM'
  };
  var SLOTS = 41; // les cases d'un inventaire de joueur (0 à 40)
  var REQUESTABLE = 16;
  var OPERATOR_ID = /^[0-9a-f]{12}$/;
  // Ce que vaut une carte de l'inventaire (validity), tel que la feuille l'écrit.
  var VALIDITY_TEXT = { ACTIVE: 'Active', SUSPENDED: 'Suspendue', DEACTIVATED: 'Désactivée', UNKNOWN: 'Non reconnue' };
  // L'issue d'un geste de carte (last) : un bandeau bref pour une réussite, une alerte pour un refus. Mêmes textes que
  // fr_fr.json (screen.oneforallsurvival.phone.*), figés à la tâche 7.
  var SIM_TOASTS = {
    SIM_INSERTED: 'Carte SIM insérée.',
    SIM_REMOVED: 'Carte SIM retirée.',
    SIM_REQUESTED: "Demande envoyée à l'opérateur."
  };
  var SIM_ALERTS = {
    SIM_UNKNOWN: ['Carte SIM', 'Aucune carte SIM valable dans cette case.'],
    INVENTORY_FULL: ['Carte SIM', 'Inventaire plein : la carte reste dans le téléphone.'],
    ALREADY_REQUESTED: ['Demande de carte', 'Vous avez déjà demandé une carte à cet opérateur.'],
    REQUESTS_FULL: ['Demande de carte', 'La file de demandes de cet opérateur est pleine. Réessayez plus tard.'],
    NO_NETWORK: ['Réseau', 'Pas de réseau.']
  };
  var DAYS = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];
  var MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre',
    'novembre', 'décembre'];
  var PENDING_MS = 2500; // le serveur ne répond pas à un geste trop rapproché : la page n'attend pas sans fin
  var LAYOUT_DELAY_MS = 720; // après la fin des animations d'écran (0,63 s au plus)
  // Les étapes de l'assistant, dans l'ordre ; 'code' est l'écran #lock, 'ready' l'écran « Tout est prêt ».
  // Point d'extension : le lot T2 (réseau, carte SIM) ajoutera une étape 'sim' entre 'code' et 'ready'. L'ajouter
  // à cette liste ne suffit pas : render() envoie à l'écran 'ready' dès que la vue passe de SETUP à UNLOCKED (il
  // faudra viser 'sim'), goWizard() refuse 'ready' et non 'sim', wizardStep() ne connaît que 'setup' et 'ready',
  // et collectRects(), wizardTap() et index.html (un .wz-step data-step="sim") devront connaître l'étape.
  var WIZARD = ['hello', 'quick', 'wallpaper', 'code', 'ready'];
  var HELLO = ['Bonjour', 'Hello', 'Hola', 'Ciao', 'Hallo', 'Olá', 'Hej', 'Bonjour'];
  var HELLO_SHOW_MS = 1500; // un mot reste lisible ; le fondu dure 0,45 s (phone.css)
  var HELLO_FADE_MS = 450;

  var mockMode = String(window.location.href).slice(-5) === '#mock';

  function $(id) { return document.getElementById(id); }

  var phoneEl = $('phone');

  // ------------------------------------------------------------------------------------------- État de la page

  var state = {
    view: null,
    session: null,
    screen: 'lock', // setup | ready | lock | home | settings
    wizard: 'hello', // étape de l'assistant pendant l'écran setup (voir WIZARD)
    setupWallpaper: 'aube', // fond choisi dans l'assistant, envoyé avec le code
    helloTimer: null,
    helloIndex: 0,
    clockBase: 0, // heure locale du serveur (ms) à la réception
    clockAt: 0, // performance.now() à la réception
    lockoutEnd: 0, // performance.now() de fin du blocage, 0 sans blocage
    showAttempts: false,
    pending: null, // geste envoyé, en attente de réponse : { kind, timer }
    setupFirst: null, // première saisie du choix du code
    sheet: null, // { mode: 'change'|'erase', step, old, fresh }
    simOpen: false, // feuille « Carte SIM » ouverte (Réglages)
    requestsOpen: false, // liste « Demander une carte » dépliée
    alertActions: [null, null]
  };

  function send(message) {
    if (window.oneforall) {
      window.oneforall.send(message);
    }
  }

  // ------------------------------------------------------------------------------------------- Lecture de la vue

  function finite(x) {
    return typeof x === 'number' && isFinite(x);
  }

  function num(x, min, max, fallback) {
    var n = typeof x === 'number' && isFinite(x) ? Math.floor(x) : fallback;
    return Math.max(min, Math.min(max, n));
  }

  function text(x, max) {
    return typeof x === 'string' ? x.slice(0, max) : '';
  }

  function integer(x, min, max) {
    return typeof x === 'number' && isFinite(x) && Math.floor(x) === x && x >= min && x <= max;
  }

  /** La vue réseau, bornée comme le serveur la borne ; verrouillé, rien d'autre que la carte, l'opérateur, les barres. */
  function cleanNet(n, unlocked) {
    n = n && typeof n === 'object' ? n : {};
    var out = {
      sim: SIMS.indexOf(n.sim) >= 0 ? n.sim : 'NONE',
      operator: text(n.operator, 32),
      bars: num(n.bars, 0, 4, 0),
      number: unlocked ? text(n.number, 32) : '',
      holder: unlocked ? text(n.holder, 32) : '',
      founderApp: unlocked && n.founderApp === true,
      choices: [],
      requestable: []
    };
    var i;
    if (unlocked && Array.isArray(n.choices)) {
      for (i = 0; i < n.choices.length && out.choices.length < SLOTS; i++) {
        var c = n.choices[i];
        if (c && typeof c === 'object' && integer(c.slot, 0, SLOTS - 1)) {
          out.choices.push({ slot: c.slot, number: text(c.number, 32), operator: text(c.operator, 32),
            validity: SIMS.indexOf(c.validity) > 0 ? c.validity : 'UNKNOWN' });
        }
      }
    }
    if (unlocked && Array.isArray(n.requestable)) {
      for (i = 0; i < n.requestable.length && out.requestable.length < REQUESTABLE; i++) {
        var o = n.requestable[i];
        if (o && typeof o === 'object' && typeof o.id === 'string' && OPERATOR_ID.test(o.id)) {
          out.requestable.push({ id: o.id, name: text(o.name, 32), bars: num(o.bars, 0, 4, 0) });
        }
      }
    }
    return out;
  }

  function clean(v) {
    var unlocked = v.lock === 'UNLOCKED';
    return {
      session: typeof v.session === 'number' ? v.session : 0,
      lock: LOCKS.indexOf(v.lock) >= 0 ? v.lock : 'LOCKED',
      battery: num(v.battery, 0, 100, 0),
      serverMillis: typeof v.serverMillis === 'number' && isFinite(v.serverMillis) ? v.serverMillis : Date.now(),
      zoneOffsetSeconds: num(v.zoneOffsetSeconds, -64800, 64800, 0),
      wallpaper: WALLPAPERS.indexOf(v.wallpaper) >= 0 ? v.wallpaper : 'aube',
      airplane: v.airplane === true,
      owner: v.owner === true,
      ownerName: typeof v.ownerName === 'string' ? v.ownerName.slice(0, 32) : '',
      attemptsLeft: num(v.attemptsLeft, 0, 99, 0),
      lockoutSeconds: num(v.lockoutSeconds, 0, 86400, 0),
      last: typeof v.last === 'string' ? v.last : 'NONE',
      net: cleanNet(v.net, unlocked)
    };
  }

  // ------------------------------------------------------------------------------------------- Heure

  function two(n) { return n < 10 ? '0' + n : String(n); }

  function nowLocal() {
    return state.clockBase + (performance.now() - state.clockAt);
  }

  function tickClock() {
    if (!state.view) {
      return;
    }
    var d = new Date(nowLocal());
    var time = two(d.getUTCHours()) + ':' + two(d.getUTCMinutes());
    var date = DAYS[d.getUTCDay()] + ' ' + d.getUTCDate() + ' ' + MONTHS[d.getUTCMonth()];
    var nodes = document.querySelectorAll('[data-clock]');
    for (var i = 0; i < nodes.length; i++) {
      var text = nodes[i].getAttribute('data-clock') === 'time' ? time : date;
      if (nodes[i].textContent !== text) {
        nodes[i].textContent = text;
      }
    }
  }

  // ------------------------------------------------------------------------------------------- Barre d'état, fond

  /** Le fond montré : pendant l'assistant, celui que l'on est en train de choisir ; sinon celui du serveur. */
  function shownWallpaper() {
    if (state.screen === 'setup') {
      return state.setupWallpaper;
    }
    return state.view ? state.view.wallpaper : 'aube';
  }

  function paintWall() {
    $('wall').className = 'wall wp-' + shownWallpaper();
    var thumbs = document.querySelectorAll('.wz-thumb');
    for (var i = 0; i < thumbs.length; i++) {
      thumbs[i].classList.toggle('sel', thumbs[i].getAttribute('data-wp') === state.setupWallpaper);
    }
  }

  function paintStatic(v) {
    paintWall();

    phoneEl.classList.toggle('airplane', v.airplane);

    var pct = v.battery;
    var low = pct <= 20;
    $('st-batt-fill').style.width = pct + '%';
    $('st-batt').classList.toggle('low', low);
    $('st-batt').classList.toggle('full', pct >= 96);
    $('st-batt-num').textContent = String(pct);

    $('w-pct').textContent = pct + ' %';
    $('w-ring').style.setProperty('--p', String(pct));
    $('w-ring').classList.toggle('low', low);

    $('set-batt-pct').textContent = pct + ' %';
    $('set-batt-bar').style.width = pct + '%';
    $('set-batt-bar').parentNode.classList.toggle('low', low);

    var sw = $('sw-airplane');
    sw.classList.toggle('on', v.airplane);
    sw.setAttribute('aria-checked', v.airplane ? 'true' : 'false');

    var thumbs = document.querySelectorAll('.thumb');
    for (var i = 0; i < thumbs.length; i++) {
      thumbs[i].classList.toggle('sel', thumbs[i].getAttribute('data-wp') === v.wallpaper);
    }

    $('set-owner').textContent = 'Téléphone de ' + (v.ownerName || '—');
    $('wz-owner').textContent = 'Téléphone de ' + (v.ownerName || '—');
    paintNet(v);
  }

  // ------------------------------------------------------------------------------------------- Réseau (lot T2)

  /**
   * Ce que la barre d'état écrit à la place de l'opérateur. La page ne devine rien : verrouillé, la vue ne dit que
   * « aucune carte », « non reconnue » ou « présente » (ACTIVE), et 0 barre s'y lit « Pas de service », quelle qu'en soit
   * la raison (mode avion compris : son drapeau n'est pas envoyé verrouillé).
   */
  function carrierKey(v) {
    var n = v.net;
    var unlocked = v.lock === 'UNLOCKED';
    if (unlocked && v.airplane) {
      return 'AIRPLANE';
    }
    if (n.sim === 'NONE') {
      return 'NO_SIM';
    }
    if (n.sim === 'UNKNOWN') {
      return 'UNRECOGNIZED';
    }
    if (unlocked && n.sim === 'SUSPENDED') {
      return 'SUSPENDED';
    }
    if (unlocked && n.sim === 'DEACTIVATED') {
      return 'INACTIVE';
    }
    return n.bars > 0 && n.operator ? 'OPERATOR' : 'NO_SERVICE';
  }

  function carrierText(v) {
    var key = carrierKey(v);
    return key === 'OPERATOR' ? v.net.operator : STATUS_TEXTS[key];
  }

  /**
   * La carte logée en une ligne (Réglages, tête de la feuille) : le numéro d'une ligne (carte active ou suspendue),
   * sinon son état. L'état vient de la vue, jamais d'un numéro vide : une carte désactivée n'a pas de numéro sans être
   * « non reconnue » pour autant (PhoneStatusText.card, côté Java).
   */
  function cardKey(sim) {
    return sim === 'NONE' ? 'NO_CARD' : sim === 'UNKNOWN' ? 'UNRECOGNIZED' : sim === 'DEACTIVATED' ? 'INACTIVE'
      : 'NUMBER';
  }

  function cardText(n) {
    var key = cardKey(n.sim);
    return key === 'NUMBER' ? (n.number || '—') : STATUS_TEXTS[key];
  }

  /** « Demander une carte » : ni avec une carte active ni en mode avion (PhoneStatusText.requestOffered). */
  function requestOffered(v) {
    return v.net.sim !== 'ACTIVE' && !v.airplane;
  }

  function paintBars(el, bars) {
    var cells = el.children;
    for (var i = 0; i < cells.length; i++) {
      cells[i].classList.toggle('on', i < bars);
    }
  }

  /** L'opérateur et l'état d'une ligne (feuille « Carte SIM ») ; rien sans ligne : l'état est déjà en tête. */
  function simStateText(n) {
    if (n.sim !== 'ACTIVE' && n.sim !== 'SUSPENDED') {
      return '';
    }
    var st = n.sim === 'ACTIVE' ? 'Active' : STATUS_TEXTS.SUSPENDED;
    return (n.operator ? n.operator + ' · ' : '') + st;
  }

  function paintNet(v) {
    var n = v.net;
    var key = carrierKey(v);
    var carrier = carrierText(v);
    $('st-carrier-left').textContent = carrier;
    $('st-carrier-right').textContent = carrier;
    // Un état plutôt que le nom de l'opérateur : écrit un peu plus petit, pour tenir entier.
    $('st-carrier-right').classList.toggle('st-msg', key !== 'OPERATOR');
    $('st-carrier-left').classList.toggle('st-msg', key !== 'OPERATOR');
    // Sans ligne (aucune carte, non reconnue, suspendue, inactive) : l'état prend la place des barres, vides de toute
    // façon. « Pas de service » garde ses quatre barres vides ; « Mode avion » a son avion.
    phoneEl.classList.toggle('no-line', key !== 'OPERATOR' && key !== 'NO_SERVICE' && key !== 'AIRPLANE');
    paintBars($('status-bars'), v.airplane ? 0 : n.bars);
    $('app-antenna').classList.toggle('gone', !n.founderApp);

    // Réglages : la carte, l'opérateur, l'abonnement (le prélèvement arrive au lot T6 ; la vue ne porte pas le tarif).
    var line = cardKey(n.sim) === 'NUMBER';
    $('set-sim').textContent = cardText(n);
    var sub = $('set-sim-sub');
    sub.textContent = line ? (n.holder || 'SIM sans nom') + (n.sim === 'SUSPENDED' ? ' · ' + STATUS_TEXTS.SUSPENDED : '')
      : '';
    sub.classList.toggle('gone', !line);
    $('set-carrier').textContent = n.operator || '—';
    $('set-plan').textContent = line ? 'Prélèvement à venir' : '—';

    // La feuille « Carte SIM ».
    $('sim-number').textContent = cardText(n);
    $('sim-holder').textContent = line ? (n.holder || 'SIM sans nom')
      : n.sim === 'NONE' ? 'Glissez une carte de votre inventaire' : '';
    var st = $('sim-state');
    st.textContent = simStateText(n);
    st.classList.toggle('warn', n.sim !== 'ACTIVE');
    $('sim-remove-group').classList.toggle('gone', n.sim === 'NONE');
    var offered = requestOffered(v);
    if (!offered) {
      state.requestsOpen = false;
    }
    $('sim-request').classList.toggle('disabled', !offered);
    paintChoices(n);
    paintRequests(n);
  }

  /** Une ligne de la feuille : un titre, une ligne fine dessous (facultative), une valeur à droite (facultative). */
  function simRow(id, label, sub, value, warn) {
    var r = document.createElement('div');
    r.className = 'row tap sim-row';
    r.id = id;
    var stack = document.createElement('div');
    stack.className = 'stack';
    var l = document.createElement('span');
    l.className = 'label';
    l.textContent = label;
    stack.appendChild(l);
    if (sub) {
      var s = document.createElement('span');
      s.className = 'sub';
      s.textContent = sub;
      stack.appendChild(s);
    }
    r.appendChild(stack);
    if (value !== null) {
      var val = document.createElement('span');
      val.className = 'value' + (warn ? ' warn' : '');
      val.textContent = value;
      r.appendChild(val);
    }
    return r;
  }

  function emptyRow(message) {
    var r = document.createElement('div');
    r.className = 'row empty';
    r.textContent = message;
    return r;
  }

  function clearBox(box) {
    while (box.firstChild) {
      box.removeChild(box.firstChild);
    }
  }

  function paintChoices(n) {
    var box = $('sim-choices');
    clearBox(box);
    if (n.choices.length === 0) {
      box.appendChild(emptyRow("Aucune carte SIM dans l'inventaire"));
      return;
    }
    for (var i = 0; i < n.choices.length; i++) {
      var c = n.choices[i];
      var r = simRow('sim-choice-' + c.slot, c.number || '—', c.operator, VALIDITY_TEXT[c.validity],
        c.validity !== 'ACTIVE');
      r.setAttribute('data-slot', String(c.slot));
      box.appendChild(r);
    }
  }

  function paintRequests(n) {
    var box = $('sim-requests');
    clearBox(box);
    $('sim-request').classList.toggle('open', state.requestsOpen);
    if (!state.requestsOpen) {
      return;
    }
    if (n.requestable.length === 0) {
      box.appendChild(emptyRow('Aucun opérateur à qui demander ici'));
      return;
    }
    for (var i = 0; i < n.requestable.length; i++) {
      var o = n.requestable[i];
      var r = simRow('request-' + o.id, o.name, '', null, false);
      r.setAttribute('data-op', o.id);
      var bars = document.createElement('span');
      bars.className = 'st-bars req-bars';
      for (var k = 0; k < 4; k++) {
        var cell = document.createElement('i');
        if (k < o.bars) {
          cell.className = 'on';
        }
        bars.appendChild(cell);
      }
      r.appendChild(bars);
      box.appendChild(r);
    }
  }

  function openSim() {
    if (state.screen !== 'settings' || phoneEl.classList.contains('sheet-open') || state.simOpen) {
      return;
    }
    state.simOpen = true;
    state.requestsOpen = false;
    if (state.view) {
      paintNet(state.view);
    }
    $('sim-scroll').scrollTop = 0;
    phoneEl.classList.add('sim-open');
    paintChrome();
    scheduleLayout();
  }

  function closeSim() {
    state.simOpen = false;
    state.requestsOpen = false;
    phoneEl.classList.remove('sim-open');
    paintChrome();
    scheduleLayout();
  }

  /** Un geste de carte : il part, et la page attend la vue du serveur (rien n'est changé avant). */
  function simGesture(message) {
    if (state.pending) {
      return;
    }
    startPending('sim');
    send(message);
  }

  function paintChrome() {
    var light = state.screen === 'settings' || phoneEl.classList.contains('sheet-open')
      || phoneEl.classList.contains('sim-open');
    $('status').classList.toggle('ink-dark', light && state.screen !== 'lock' && state.screen !== 'setup');
    phoneEl.classList.toggle('ink-dark', light && state.screen !== 'lock' && state.screen !== 'setup');
  }

  // ------------------------------------------------------------------------------------------- Écrans

  function setScreen(screen) {
    // « Tout est prêt » vers l'accueil : l'écran verrouillé, déjà sorti à gauche, ne doit pas glisser vers le haut ;
    // il redevient visible au prochain verrouillage.
    if (state.screen === 'ready' && screen !== 'ready') {
      phoneEl.classList.add('wz-skip');
    } else if (screen === 'lock' || screen === 'setup') {
      phoneEl.classList.remove('wz-skip');
    }
    state.screen = screen;
    if (screen !== 'settings' && state.simOpen) {
      state.simOpen = false;
      state.requestsOpen = false;
      phoneEl.classList.remove('sim-open');
    }
    phoneEl.classList.remove('scr-setup', 'scr-ready', 'scr-lock', 'scr-home', 'scr-settings');
    phoneEl.classList.add('scr-' + screen);
    phoneEl.classList.toggle('app-open', screen === 'settings');
    paintWizard();
    paintWall();
    placePad();
    paintChrome();
    scheduleLayout();
  }

  // ------------------------------------------------------------------------------------------- Assistant

  /** Repart du début de l'assistant : Bonjour, fond du serveur (celui d'un téléphone neuf), rien de saisi. */
  function resetWizard(v) {
    stopHello();
    state.wizard = 'hello';
    state.setupWallpaper = v.wallpaper;
    state.setupFirst = null;
    state.helloIndex = 0;
    $('wz-word').textContent = HELLO[0];
    state.helloTimer = setTimeout(nextHello, HELLO_SHOW_MS);
  }

  /** « Bonjour » dans une langue, puis la suivante en fondu ; à la fin du tour, le français reste. */
  function nextHello() {
    state.helloTimer = null;
    if (state.helloIndex >= HELLO.length - 1) {
      return;
    }
    var word = $('wz-word');
    word.classList.add('fade');
    state.helloTimer = setTimeout(function () {
      state.helloIndex++;
      word.textContent = HELLO[state.helloIndex];
      word.classList.remove('fade');
      state.helloTimer = setTimeout(nextHello, HELLO_SHOW_MS);
    }, HELLO_FADE_MS);
  }

  /** Arrête le tour des langues et remet « Bonjour » (on quitte l'écran, ou on y revient). */
  function stopHello() {
    if (state.helloTimer) {
      clearTimeout(state.helloTimer);
      state.helloTimer = null;
    }
    state.helloIndex = HELLO.length - 1;
    $('wz-word').textContent = HELLO[HELLO.length - 1];
    $('wz-word').classList.remove('fade');
  }

  function wizardStep() {
    return state.screen === 'ready' ? 'ready' : state.screen === 'setup' ? state.wizard : null;
  }

  function paintWizard() {
    var step = wizardStep();
    var at = step === null ? -1 : WIZARD.indexOf(step);
    var panels = document.querySelectorAll('.wz-step');
    for (var i = 0; i < panels.length; i++) {
      var k = WIZARD.indexOf(panels[i].getAttribute('data-step'));
      panels[i].classList.toggle('cur', k === at);
      panels[i].classList.toggle('past', at >= 0 && k < at);
    }
    for (var j = 0; j < WIZARD.length; j++) {
      phoneEl.classList.toggle('wz-at-' + WIZARD[j], step === WIZARD[j]);
    }
    phoneEl.classList.toggle('wz-before-code', at >= 0 && at < WIZARD.indexOf('code'));
  }

  function goWizard(step) {
    if (state.screen !== 'setup' || state.pending || WIZARD.indexOf(step) < 0 || step === 'ready') {
      return;
    }
    if (state.wizard === 'hello') {
      stopHello();
    }
    // En entrant dans le code comme en le quittant (Retour), la saisie en cours est oubliée.
    state.setupFirst = null;
    clearEntry('lock');
    state.wizard = step;
    paintLockTexts();
    paintWizard();
    paintWall();
    placePad();
    scheduleLayout();
  }

  function chooseWallpaper(id) {
    if (state.screen !== 'setup' || state.wizard !== 'wallpaper' || WALLPAPERS.indexOf(id) < 0) {
      return;
    }
    state.setupWallpaper = id;
    paintWall();
  }

  function openSettings() {
    if (state.screen !== 'home') {
      return;
    }
    var icon = $('app-settings').querySelector('.app-icon').getBoundingClientRect();
    var box = phoneEl.getBoundingClientRect();
    $('settings').style.transformOrigin =
      Math.round(icon.left - box.left + icon.width / 2) + 'px ' + Math.round(icon.top - box.top + icon.height / 2) + 'px';
    $('settings-scroll').scrollTop = 0;
    $('nav').classList.remove('solid');
    setScreen('settings');
  }

  function closeSettings() {
    if (state.screen === 'settings' && !phoneEl.classList.contains('sheet-open')) {
      setScreen('home');
    }
  }

  // ------------------------------------------------------------------------------------------- Saisie du code

  var entries = {
    lock: { value: '', dots: 'lock-dots', prompt: 'lock-prompt', msg: 'lock-msg' },
    sheet: { value: '', dots: 'sheet-dots', prompt: 'sheet-prompt', msg: 'sheet-msg' }
  };

  function activeEntry() {
    if (phoneEl.classList.contains('alert-open') || phoneEl.classList.contains('sim-open')) {
      return null;
    }
    if (phoneEl.classList.contains('sheet-open')) {
      return 'sheet';
    }
    if (state.screen === 'lock' || (state.screen === 'setup' && state.wizard === 'code')) {
      return 'lock';
    }
    return null;
  }

  function placePad() {
    var slot = phoneEl.classList.contains('sheet-open') ? $('sheet-slot')
      : (state.screen === 'lock' || state.screen === 'setup') ? $('lock-slot') : null;
    var pad = $('pad');
    if (slot) {
      if (pad.parentNode !== slot) {
        slot.appendChild(pad);
      }
      pad.classList.remove('pad-park');
    } else if (state.screen === 'home' || state.screen === 'settings') {
      // Le clavier attend dans l'écran verrouillé, caché par lui (il glisse avec lui au déverrouillage).
      if (pad.parentNode !== $('lock-slot')) {
        $('lock-slot').appendChild(pad);
      }
    }
    pad.classList.toggle('off', lockedOut() && slot === $('lock-slot'));
  }

  function paintDots(name) {
    var e = entries[name];
    var dots = $(e.dots).children;
    for (var i = 0; i < dots.length; i++) {
      dots[i].classList.toggle('on', i < e.value.length);
    }
  }

  function clearEntry(name) {
    entries[name].value = '';
    paintDots(name);
  }

  function setText(id, text) {
    $(id).textContent = text;
  }

  function shake(name, then) {
    var el = $(entries[name].dots);
    el.classList.remove('shake');
    void el.offsetWidth; // relance l'animation
    el.classList.add('shake');
    var done = false;
    function end() {
      if (done) {
        return;
      }
      done = true;
      el.classList.remove('shake');
      clearEntry(name);
      if (then) {
        then();
      }
    }
    el.addEventListener('animationend', end, { once: true });
    setTimeout(end, 650);
  }

  function lockedOut() {
    return state.lockoutEnd > 0 && performance.now() < state.lockoutEnd;
  }

  function press(k) {
    var name = activeEntry();
    if (!name || state.pending) {
      return;
    }
    if (name === 'lock' && lockedOut()) {
      return;
    }
    var e = entries[name];
    if (k === 'back') {
      e.value = e.value.slice(0, -1);
      paintDots(name);
      return;
    }
    if (!/^[0-9]$/.test(k) || e.value.length >= CODE_LENGTH) {
      return;
    }
    e.value += k;
    paintDots(name);
    if (e.value.length === CODE_LENGTH) {
      var code = e.value;
      // Un court temps pour voir le quatrième point se remplir.
      setTimeout(function () {
        // Retour arrière, fermeture ou changement d'écran pendant le délai : la saisie n'est plus ce code, rien ne part.
        if (entries[name].value !== code || activeEntry() !== name || state.pending) {
          return;
        }
        if (name === 'lock') {
          lockComplete(code);
        } else {
          sheetComplete(code);
        }
      }, 140);
    }
  }

  /** Le code parti n'est plus gardé ; les points restent peints (le glissement du déverrouillage les emporte). */
  function forget(name) {
    entries[name].value = '';
  }

  function startPending(kind) {
    clearPending();
    state.pending = {
      kind: kind,
      timer: setTimeout(function () {
        // Pas de réponse (geste trop rapproché, ignoré par le serveur) : on rend la main.
        state.pending = null;
        clearEntry('lock');
        clearEntry('sheet');
      }, PENDING_MS)
    };
  }

  function clearPending() {
    if (state.pending) {
      clearTimeout(state.pending.timer);
      state.pending = null;
    }
  }

  function lockComplete(code) {
    if (state.screen === 'setup') {
      if (state.setupFirst === null) {
        state.setupFirst = code;
        clearEntry('lock');
        setText('lock-prompt', 'Confirmez le code');
        setText('lock-msg', 'Saisissez à nouveau les quatre chiffres');
        return;
      }
      if (state.setupFirst !== code) {
        state.setupFirst = null;
        setText('lock-msg', 'Les codes ne correspondent pas. Recommencez.');
        shake('lock', function () { setText('lock-prompt', 'Choisissez un code'); });
        return;
      }
      state.setupFirst = null;
      startPending('setup');
      send({ a: 'setup', code: code, wallpaper: state.setupWallpaper });
      forget('lock');
      return;
    }
    if (state.screen === 'lock') {
      startPending('unlock');
      send({ a: 'unlock', code: code });
      forget('lock');
    }
  }

  function paintLockTexts() {
    if (state.screen === 'setup') {
      setText('lock-prompt', state.setupFirst === null ? 'Choisissez un code' : 'Confirmez le code');
      setText('lock-msg', state.setupFirst === null ? 'Quatre chiffres pour protéger ce téléphone'
        : 'Saisissez à nouveau les quatre chiffres');
      return;
    }
    if (lockedOut()) {
      var s = Math.max(1, Math.ceil((state.lockoutEnd - performance.now()) / 1000));
      setText('lock-prompt', 'Téléphone bloqué');
      setText('lock-msg', 'Réessayez dans ' + s + ' s');
      return;
    }
    setText('lock-prompt', 'Saisissez le code');
    setText('lock-msg', state.showAttempts && state.view ? 'Essais restants : ' + state.view.attemptsLeft : '');
  }

  function tickLockout() {
    if (state.lockoutEnd > 0 && !lockedOut()) {
      // Fin du blocage : le serveur a remis le compte à zéro.
      state.lockoutEnd = 0;
      state.showAttempts = false;
      placePad();
      scheduleLayout(0);
    }
    if (state.screen === 'lock') {
      paintLockTexts();
    }
  }

  // ------------------------------------------------------------------------------------------- Feuille du code

  var SHEET_STEPS = {
    change: ['Saisissez l\'ancien code', 'Saisissez le nouveau code', 'Confirmez le nouveau code'],
    erase: ['Saisissez le code']
  };

  function openSheet(mode) {
    state.sheet = { mode: mode, step: 0, old: null, fresh: null };
    setText('sheet-title', mode === 'change' ? 'Modifier le code' : 'Effacer le téléphone');
    sheetStep(0, mode === 'change' ? '' : 'Le code est demandé avant d\'effacer', false);
    clearEntry('sheet');
    phoneEl.classList.add('sheet-open');
    placePad();
    paintChrome();
    scheduleLayout();
  }

  function closeSheet() {
    state.sheet = null;
    phoneEl.classList.remove('sheet-open');
    clearEntry('sheet');
    placePad();
    paintChrome();
    scheduleLayout();
  }

  function sheetStep(step, message, error) {
    state.sheet.step = step;
    setText('sheet-prompt', SHEET_STEPS[state.sheet.mode][step]);
    setText('sheet-msg', message || '');
    $('sheet-msg').classList.toggle('err', !!error);
  }

  function sheetComplete(code) {
    var s = state.sheet;
    if (!s) {
      return;
    }
    if (s.mode === 'erase') {
      clearEntry('sheet');
      showAlert('Effacer le téléphone ?',
        'Le code, le propriétaire et les réglages seront supprimés. Cette action est définitive.',
        [{ label: 'Annuler', strong: false, run: null },
          { label: 'Effacer', danger: true, run: function () {
            startPending('erase');
            send({ a: 'erase', code: code });
          } }]);
      return;
    }
    if (s.step === 0) {
      s.old = code;
      clearEntry('sheet');
      sheetStep(1, '', false);
    } else if (s.step === 1) {
      s.fresh = code;
      clearEntry('sheet');
      sheetStep(2, '', false);
    } else if (s.fresh !== code) {
      s.fresh = null;
      shake('sheet', function () { sheetStep(1, 'Les codes ne correspondent pas. Recommencez.', true); });
    } else {
      startPending('changeCode');
      send({ a: 'changeCode', old: s.old, new: code });
      s.old = null;
      s.fresh = null;
      forget('sheet');
    }
  }

  function notOwner(what) {
    showAlert('Code', what, [{ label: 'OK', strong: true, run: null }]);
  }

  // ------------------------------------------------------------------------------------------- Alerte et bandeau

  function showAlert(title, message, buttons) {
    setText('alert-title', title);
    setText('alert-msg', message);
    for (var i = 0; i < 2; i++) {
      var b = $('alert-' + i);
      var spec = buttons[i];
      b.className = 'alert-btn' + (!spec ? ' gone' : spec.danger ? ' danger' : spec.strong ? ' strong' : '');
      b.textContent = spec ? spec.label : '';
      state.alertActions[i] = spec ? spec.run : null;
    }
    phoneEl.classList.add('alert-open');
    scheduleLayout();
  }

  function alertButton(i) {
    if (!phoneEl.classList.contains('alert-open')) {
      return;
    }
    var run = state.alertActions[i];
    state.alertActions = [null, null];
    phoneEl.classList.remove('alert-open');
    if (run) {
      run();
    }
    scheduleLayout();
  }

  var toastTimer = null;

  function toast(text) {
    setText('toast-text', text);
    $('toast').classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { $('toast').classList.remove('show'); }, 2200);
  }

  // ------------------------------------------------------------------------------------------- Rendu d'une vue

  function render(raw) {
    if (!raw || typeof raw !== 'object' || !finite(raw.session) || !finite(raw.serverMillis)) {
      return; // vue incomplète : on n'invente ni la session ni l'heure, l'écran reste tel quel
    }
    var v = clean(raw);
    var fresh = state.session !== v.session || state.view === null;
    var before = fresh ? null : state.view.lock;
    clearPending();

    state.view = v;
    state.session = v.session;
    state.clockBase = v.serverMillis + v.zoneOffsetSeconds * 1000;
    state.clockAt = performance.now();

    if (fresh) {
      // Nouvelle session : tout repart de zéro, sans animation.
      phoneEl.classList.add('no-anim');
      phoneEl.classList.remove('sheet-open', 'alert-open', 'sim-open');
      state.sheet = null;
      state.simOpen = false;
      state.requestsOpen = false;
      state.setupFirst = null;
      state.showAttempts = false;
      clearEntry('lock');
      clearEntry('sheet');
    }

    state.lockoutEnd = v.lockoutSeconds > 0 ? performance.now() + v.lockoutSeconds * 1000 : 0;

    paintStatic(v);
    tickClock();

    if (v.lock !== before) {
      if (v.lock !== 'UNLOCKED') {
        phoneEl.classList.remove('sheet-open', 'alert-open', 'sim-open');
        state.sheet = null;
        state.simOpen = false;
        state.requestsOpen = false;
        clearEntry('lock');
        state.setupFirst = null;
        state.showAttempts = false;
      }
      if (v.lock === 'SETUP') {
        resetWizard(v); // téléphone neuf (ou effacé) : l'assistant reprend au début
      } else {
        stopHello();
      }
      // SETUP puis UNLOCKED dans la même session : notre code vient d'être posé, « Tout est prêt ».
      setScreen(v.lock === 'SETUP' ? 'setup' : v.lock === 'LOCKED' ? 'lock' : before === 'SETUP' ? 'ready' : 'home');
    }

    handleLast(v);

    if (state.screen === 'lock' || state.screen === 'setup') {
      paintLockTexts();
    }
    placePad();
    paintChrome();

    if (fresh) {
      void phoneEl.offsetWidth;
      // Un délai plutôt que requestAnimationFrame : il ne vient pas tant que la page n'est pas peinte.
      setTimeout(function () { phoneEl.classList.remove('no-anim'); }, 60);
    }
    scheduleLayout();
  }

  function handleLast(v) {
    var last = v.last;
    if (last === 'NONE') {
      return;
    }
    var inSheet = state.sheet !== null && phoneEl.classList.contains('sheet-open');
    if (Object.prototype.hasOwnProperty.call(SIM_TOASTS, last)) {
      toast(SIM_TOASTS[last]);
      return;
    }
    if (Object.prototype.hasOwnProperty.call(SIM_ALERTS, last)) {
      showAlert(SIM_ALERTS[last][0], SIM_ALERTS[last][1], [{ label: 'OK', strong: true, run: null }]);
      return;
    }
    if (last === 'NOT_OWNER' && state.simOpen) {
      showAlert('Carte SIM', 'Seul le propriétaire peut le faire.', [{ label: 'OK', strong: true, run: null }]);
      return;
    }
    if (last === 'WRONG_CODE') {
      if (inSheet) {
        var left = 'Essais restants : ' + v.attemptsLeft;
        if (state.sheet.mode === 'change') {
          state.sheet.old = null;
          state.sheet.fresh = null;
          shake('sheet', function () { sheetStep(0, 'Ancien code incorrect. ' + left, true); });
        } else {
          shake('sheet', function () { sheetStep(0, 'Code incorrect. ' + left, true); });
        }
      } else if (state.screen === 'lock') {
        state.showAttempts = true;
        shake('lock');
      }
    } else if (last === 'LOCKED_OUT') {
      // Le serveur a reverrouillé la session : la vue est LOCKED, avec lockoutSeconds.
      if (state.screen === 'lock') {
        state.showAttempts = false;
        shake('lock');
      }
    } else if (last === 'INVALID_CODE') {
      if (inSheet) {
        shake('sheet', function () { sheetStep(state.sheet ? state.sheet.step : 0, 'Code non valide.', true); });
      } else {
        shake('lock', function () { setText('lock-msg', 'Code non valide.'); });
      }
    } else if (last === 'CODE_CHANGED') {
      if (inSheet) {
        closeSheet();
      }
      toast('Code modifié');
    } else if (last === 'NOT_OWNER') {
      var mode = inSheet ? state.sheet.mode : 'change';
      if (inSheet) {
        closeSheet();
      }
      notOwner(mode === 'erase' ? 'Seul le propriétaire peut effacer le téléphone'
        : 'Seul le propriétaire peut modifier le code');
    }
  }

  // ------------------------------------------------------------------------------------------- Mise en page envoyée

  var layoutTimer = null;
  var lastLayout = '';

  function scheduleLayout(delay) {
    clearTimeout(layoutTimer);
    layoutTimer = setTimeout(sendLayout, delay === undefined ? LAYOUT_DELAY_MS : delay);
  }

  function visibleIn(el, clip) {
    var r = el.getBoundingClientRect();
    var x1 = Math.max(r.left, clip.left);
    var y1 = Math.max(r.top, clip.top);
    var x2 = Math.min(r.right, clip.right);
    var y2 = Math.min(r.bottom, clip.bottom);
    if (x2 - x1 < 1 || y2 - y1 < 1) {
      return null;
    }
    return [Math.round(x1), Math.round(y1), Math.round(x2 - x1), Math.round(y2 - y1)];
  }

  /** Les éléments que l'on peut toucher dans l'écran du dessus, avec leur boîte visible en px CSS. */
  function collectRects() {
    var rects = {};
    var view = { left: 0, top: 0, right: window.innerWidth, bottom: window.innerHeight };
    function add(id, clip) {
      var el = $(id);
      var r = el ? visibleIn(el, clip || view) : null;
      if (r) {
        rects[id] = r;
      }
    }
    var i;
    if (phoneEl.classList.contains('alert-open')) {
      add('alert-0');
      if (!$('alert-1').classList.contains('gone')) {
        add('alert-1');
      }
      return rects;
    }
    add('status-bars');
    if (phoneEl.classList.contains('sim-open')) {
      var ss = $('sim-scroll').getBoundingClientRect();
      var simClip = { left: ss.left, top: ss.top, right: ss.right, bottom: ss.bottom };
      add('sim-close');
      if (!$('sim-remove-group').classList.contains('gone')) {
        add('sim-remove', simClip);
      }
      var simRows = $('simsheet').querySelectorAll('[id^="sim-choice-"], [id^="request-"]');
      for (i = 0; i < simRows.length; i++) {
        add(simRows[i].id, simClip);
      }
      if (!$('sim-request').classList.contains('disabled')) {
        add('sim-request', simClip);
      }
      return rects;
    }
    var entry = activeEntry();
    if (entry) {
      if (!(entry === 'lock' && lockedOut())) {
        for (i = 0; i <= 9; i++) {
          add('key-' + i);
        }
        add('key-back');
      }
      if (entry === 'sheet') {
        add('sheet-cancel');
      } else if (state.screen === 'setup') {
        add('setup-back');
      }
    } else if (state.screen === 'setup' || state.screen === 'ready') {
      var step = wizardStep();
      var ids = step === 'hello' ? ['wz-start']
        : step === 'quick' ? ['wz-quick-back', 'wz-quick-next']
          : step === 'wallpaper' ? ['wz-wp-back', 'wz-wp-aube', 'wz-wp-lagon', 'wz-wp-jungle', 'wz-wp-nuit', 'wz-wp-next']
            : step === 'ready' ? ['wz-done-start'] : [];
      for (i = 0; i < ids.length; i++) {
        add(ids[i]);
      }
    } else if (state.screen === 'home') {
      add('app-settings');
      if (!$('app-antenna').classList.contains('gone')) {
        add('app-antenna');
      }
    } else if (state.screen === 'settings') {
      var sc = $('settings-scroll').getBoundingClientRect();
      var clip = { left: sc.left, top: $('nav').getBoundingClientRect().bottom, right: sc.right, bottom: sc.bottom };
      var rows = $('settings').querySelectorAll('[id^="row-"]');
      for (i = 0; i < rows.length; i++) {
        add(rows[i].id, clip);
      }
      for (i = 0; i < WALLPAPERS.length; i++) {
        add('wp-' + WALLPAPERS[i], clip);
      }
    }
    if (entry !== 'sheet') {
      add('home-bar');
    }
    return rects;
  }

  function sendLayout() {
    var rects = collectRects();
    var key = JSON.stringify(rects);
    if (key === lastLayout) {
      return;
    }
    lastLayout = key;
    send({ a: 'layout', rects: rects });
  }

  // ------------------------------------------------------------------------------------------- Gestes

  function onTap(e) {
    var t = e.target;
    var key = t.closest('.key');
    if (key && key.hasAttribute('data-k')) {
      flash(key);
      press(key.getAttribute('data-k'));
      return;
    }
    if (t.closest('#alert-0')) {
      alertButton(0);
      return;
    }
    if (t.closest('#alert-1')) {
      alertButton(1);
      return;
    }
    if (phoneEl.classList.contains('alert-open')) {
      return;
    }
    if (wizardTap(t)) {
      return;
    }
    if (phoneEl.classList.contains('sim-open')) {
      simTap(t);
      return;
    }
    if (t.closest('#sheet-cancel')) {
      if (!state.pending) {
        closeSheet();
      }
      return;
    }
    if (t.closest('#home-bar')) {
      closeSettings();
      return;
    }
    if (t.closest('#app-settings')) {
      openSettings();
      return;
    }
    if (t.closest('#app-antenna')) {
      // L'appli elle-même arrive à la tâche 10 : le geste part, le serveur répond.
      if (state.screen === 'home' && !$('app-antenna').classList.contains('gone')) {
        simGesture({ a: 'antennaApp' });
      }
      return;
    }
    if (state.screen !== 'settings' || phoneEl.classList.contains('sheet-open') || !state.view) {
      return;
    }
    var thumb = t.closest('.thumb');
    if (thumb) {
      var id = thumb.getAttribute('data-wp');
      if (WALLPAPERS.indexOf(id) >= 0 && id !== state.view.wallpaper) {
        send({ a: 'wallpaper', id: id });
      }
      return;
    }
    if (t.closest('#row-sim')) {
      openSim();
      return;
    }
    if (t.closest('#row-airplane')) {
      var on = !state.view.airplane;
      $('sw-airplane').classList.toggle('on', on); // réponse visible tout de suite, la vue suivante fait foi
      send({ a: 'airplane', on: on });
      // Geste ignoré par le serveur (trop rapproché) : l'interrupteur revient à ce que dit la dernière vue.
      setTimeout(function () { if (state.view) { paintStatic(state.view); } }, PENDING_MS);
      return;
    }
    if (t.closest('#row-code')) {
      if (state.view.owner) {
        openSheet('change');
      } else {
        notOwner('Seul le propriétaire peut modifier le code');
      }
      return;
    }
    if (t.closest('#row-erase')) {
      if (state.view.owner) {
        openSheet('erase');
      } else {
        notOwner('Seul le propriétaire peut effacer le téléphone');
      }
      return;
    }
    if (t.closest('#row-lock')) {
      startPending('lock');
      send({ a: 'lock' });
    }
  }

  /** Les touchers de la feuille « Carte SIM » : chacun envoie un geste, ou ferme, ou déplie la liste des demandes. */
  function simTap(t) {
    if (t.closest('#sim-close')) {
      closeSim();
      return;
    }
    if (!state.view) {
      return;
    }
    var choice = t.closest('[data-slot]');
    if (choice) {
      var slot = Number(choice.getAttribute('data-slot'));
      if (integer(slot, 0, SLOTS - 1)) {
        simGesture({ a: 'insertSim', slot: slot });
      }
      return;
    }
    var op = t.closest('[data-op]');
    if (op) {
      var id = op.getAttribute('data-op');
      if (OPERATOR_ID.test(id)) {
        simGesture({ a: 'requestSim', op: id });
      }
      return;
    }
    if (t.closest('#sim-remove')) {
      simGesture({ a: 'removeSim' });
      return;
    }
    if (t.closest('#sim-request')) {
      if ($('sim-request').classList.contains('disabled')) {
        return;
      }
      state.requestsOpen = !state.requestsOpen;
      paintRequests(state.view.net);
      scheduleLayout(150);
    }
  }

  /** Les boutons de l'assistant ; vrai si le toucher lui revenait. */
  function wizardTap(t) {
    var step = wizardStep();
    if (step === null) {
      return false;
    }
    if (step === 'ready') {
      if (t.closest('#wz-done-start')) {
        setScreen('home');
      }
      return true;
    }
    if (step === 'code') {
      if (t.closest('#setup-back')) {
        goWizard('wallpaper');
        return true;
      }
      return false; // le clavier
    }
    if (t.closest('#wz-start')) {
      goWizard('quick');
    } else if (t.closest('#wz-quick-back')) {
      goWizard('hello');
    } else if (t.closest('#wz-quick-next')) {
      goWizard('wallpaper');
    } else if (t.closest('#wz-wp-back')) {
      goWizard('quick');
    } else if (t.closest('#wz-wp-next')) {
      goWizard('code');
    } else {
      var thumb = t.closest('.wz-thumb');
      if (thumb) {
        chooseWallpaper(thumb.getAttribute('data-wp'));
      }
    }
    return true;
  }

  function flash(key) {
    key.classList.add('down');
    setTimeout(function () { key.classList.remove('down'); }, 120);
  }

  // Dans MCEF, la touche arrive sans `key` lisible (le code de balayage n'a pas la forme que Chromium attend sous
  // Windows) : on se rabat sur keyCode (chiffres du haut, pavé numérique, Retour arrière).
  function keyOf(e) {
    if (/^[0-9]$/.test(e.key)) {
      return e.key;
    }
    if (e.key === 'Backspace') {
      return 'back';
    }
    var c = e.keyCode || e.which || 0;
    if (c >= 48 && c <= 57) {
      return String(c - 48);
    }
    if (c >= 96 && c <= 105) {
      return String(c - 96);
    }
    return c === 8 ? 'back' : null;
  }

  function onKey(e) {
    var k = keyOf(e);
    if (k === null || !activeEntry()) {
      return;
    }
    e.preventDefault();
    var el = $(k === 'back' ? 'key-back' : 'key-' + k);
    if (el) {
      flash(el);
    }
    press(k);
  }

  function onScroll() {
    $('nav').classList.toggle('solid', $('settings-scroll').scrollTop > 36);
    scheduleLayout(150);
  }

  // ------------------------------------------------------------------------------------------- Faux serveur (#mock)

  function startMock() {
    // Un téléphone neuf : l'assistant de première configuration. phoneMock.locked() passe au téléphone déjà codé.
    var mock = {
      code: null,
      session: 1,
      lock: 'SETUP',
      battery: 76,
      wallpaper: 'aube',
      airplane: false,
      owner: true,
      ownerName: 'Alex',
      failures: 0,
      lockedUntil: 0,
      maxAttempts: 5,
      lockoutMillis: 60000,
      // Lot T2 : la carte logée, deux cartes dans l'inventaire, deux opérateurs (dont un nom de 24 caractères).
      lodged: { number: '06 12 34 56 78', holder: 'Alex', op: 'a1b2c3d4e5f6', validity: 'ACTIVE' },
      inventory: [
        { slot: 3, number: '07 41 85 29 63', holder: '', op: '3f2a9c1b0d4e', validity: 'ACTIVE' },
        { slot: 12, number: '06 55 44 33 22', holder: 'Alex', op: 'a1b2c3d4e5f6', validity: 'UNKNOWN' }
      ],
      operators: [
        { id: 'a1b2c3d4e5f6', name: 'Mairie', bars: 4 },
        { id: '3f2a9c1b0d4e', name: 'Coopérative du Lagon Sud', bars: 2 }
      ],
      requested: {},
      requestsFull: false,
      founder: true
    };

    function operatorOf(id) {
      for (var i = 0; i < mock.operators.length; i++) {
        if (mock.operators[i].id === id) {
          return mock.operators[i];
        }
      }
      return null;
    }

    /** Ce que le serveur calcule (PhoneService.netView) : le faux serveur en suit les règles, la page n'en a aucune. */
    function netView(unlocked) {
      var c = mock.lodged;
      var line = c && (c.validity === 'ACTIVE' || c.validity === 'SUSPENDED') ? c : null;
      var op = line ? operatorOf(line.op) : null;
      var bars = line && op && line.validity === 'ACTIVE' && !mock.airplane ? op.bars : 0;
      var sim = c ? c.validity : 'NONE';
      if (!unlocked) {
        // PhoneNetView.locked() : l'état grossier, l'opérateur, les barres.
        return { sim: sim === 'NONE' || sim === 'UNKNOWN' ? sim : 'ACTIVE', operator: op ? op.name : '', bars: bars,
          founderApp: false, choices: [], requestable: [] };
      }
      var choices = [];
      for (var i = 0; i < mock.inventory.length; i++) {
        var k = mock.inventory[i];
        var ko = operatorOf(k.op);
        choices.push({ slot: k.slot, number: k.number, operator: ko ? ko.name : '', validity: k.validity });
      }
      var requestable = [];
      if (!mock.airplane && sim !== 'ACTIVE' && mock.owner) {
        for (var j = 0; j < mock.operators.length; j++) {
          requestable.push({ id: mock.operators[j].id, name: mock.operators[j].name, bars: mock.operators[j].bars });
        }
      }
      return {
        sim: sim, operator: op ? op.name : '', bars: bars, number: line ? line.number : '',
        holder: line ? line.holder : '', founderApp: mock.owner && mock.founder && !mock.airplane,
        choices: mock.owner ? choices : [], requestable: requestable
      };
    }

    function view(last) {
      var now = Date.now();
      var remaining = Math.max(0, mock.lockedUntil - now);
      var lockout = remaining > 0 ? Math.ceil(remaining / 1000) : 0;
      var unlocked = mock.lock === 'UNLOCKED';
      return {
        session: mock.session, lock: mock.lock, battery: mock.battery, serverMillis: now, zoneOffsetSeconds: 3600,
        wallpaper: mock.wallpaper, airplane: unlocked && mock.airplane, owner: unlocked && mock.owner,
        ownerName: unlocked ? mock.ownerName : '', attemptsLeft: lockout > 0 ? 0 : mock.maxAttempts - mock.failures,
        lockoutSeconds: lockout, last: last || 'NONE', net: netView(unlocked)
      };
    }

    function simGesture(m) {
      var i;
      if (mock.lock !== 'UNLOCKED') {
        return null;
      }
      if (!mock.owner) {
        return 'NOT_OWNER';
      }
      if (m.a === 'insertSim') {
        for (i = 0; i < mock.inventory.length; i++) {
          var k = mock.inventory[i];
          if (k.slot === m.slot) {
            if (k.validity === 'UNKNOWN') {
              return 'SIM_UNKNOWN';
            }
            var before = mock.lodged;
            mock.inventory.splice(i, 1);
            if (before) {
              before.slot = m.slot;
              mock.inventory.splice(i, 0, before);
            }
            mock.lodged = k;
            return 'SIM_INSERTED';
          }
        }
        return 'SIM_UNKNOWN';
      }
      if (m.a === 'removeSim') {
        if (!mock.lodged) {
          return 'NONE';
        }
        var free = 0;
        for (i = 0; i < mock.inventory.length; i++) {
          free = Math.max(free, mock.inventory[i].slot + 1);
        }
        mock.lodged.slot = free;
        mock.inventory.push(mock.lodged);
        mock.lodged = null;
        return 'SIM_REMOVED';
      }
      if (m.a === 'requestSim') {
        if (mock.airplane || !operatorOf(m.op)) {
          return 'NO_NETWORK';
        }
        if (mock.requested[m.op]) {
          return 'ALREADY_REQUESTED';
        }
        if (mock.requestsFull) {
          return 'REQUESTS_FULL';
        }
        mock.requested[m.op] = true;
        return 'SIM_REQUESTED';
      }
      // antennaApp : jusqu'à la tâche 10, le serveur répond NO_NETWORK.
      return 'NO_NETWORK';
    }

    function check(code) {
      var now = Date.now();
      if (now < mock.lockedUntil) {
        return 'LOCKED_OUT';
      }
      if (code === mock.code) {
        mock.failures = 0;
        return 'NONE';
      }
      mock.failures++;
      if (mock.failures >= mock.maxAttempts) {
        mock.failures = 0;
        mock.lockedUntil = now + mock.lockoutMillis;
        return 'LOCKED_OUT';
      }
      return 'WRONG_CODE';
    }

    function valid(code) { return typeof code === 'string' && /^[0-9]{4}$/.test(code); }

    function reply(last) {
      setTimeout(function () { render(view(last)); }, 220);
    }

    function handle(m) {
      var r;
      switch (m.a) {
        case 'ready':
          reply('NONE');
          break;
        case 'setup':
          if (mock.lock !== 'SETUP') { return; }
          if (!valid(m.code)) { reply('INVALID_CODE'); return; }
          mock.code = m.code;
          if (WALLPAPERS.indexOf(m.wallpaper) >= 0) { mock.wallpaper = m.wallpaper; }
          mock.lock = 'UNLOCKED';
          reply('NONE');
          break;
        case 'unlock':
          if (mock.lock !== 'LOCKED') { return; }
          r = check(m.code);
          if (r === 'NONE') { mock.lock = 'UNLOCKED'; }
          reply(r);
          break;
        case 'lock':
          mock.lock = 'LOCKED';
          reply('NONE');
          break;
        case 'wallpaper':
          if (mock.lock === 'UNLOCKED' && WALLPAPERS.indexOf(m.id) >= 0) { mock.wallpaper = m.id; }
          reply('NONE');
          break;
        case 'airplane':
          if (mock.lock === 'UNLOCKED') { mock.airplane = m.on === true; }
          reply('NONE');
          break;
        case 'changeCode':
          if (!mock.owner) { reply('NOT_OWNER'); return; }
          if (!valid(m['new'])) { reply('INVALID_CODE'); return; }
          r = check(m.old);
          if (r === 'LOCKED_OUT') { mock.lock = 'LOCKED'; }
          if (r === 'NONE') { mock.code = m['new']; r = 'CODE_CHANGED'; }
          reply(r);
          break;
        case 'erase':
          if (!mock.owner) { reply('NOT_OWNER'); return; }
          r = check(m.code);
          if (r === 'LOCKED_OUT') { mock.lock = 'LOCKED'; }
          if (r !== 'NONE') { reply(r); return; }
          // En jeu, le serveur ferme l'écran ; ici, une nouvelle session repart du choix du code.
          mock.code = null;
          mock.session++;
          mock.lock = 'SETUP';
          mock.wallpaper = 'aube';
          mock.airplane = false;
          reply('NONE');
          break;
        case 'insertSim':
        case 'removeSim':
        case 'requestSim':
        case 'antennaApp':
          r = simGesture(m);
          if (r !== null) {
            reply(r);
          }
          break;
        default:
          break;
      }
    }

    window.oneforall.mock = handle;
    // Commandes de la console, pour l'essai : phoneMock.setup(), phoneMock.locked(), phoneMock.notOwner(),
    // phoneMock.battery(12) ; lot T2 : phoneMock.bars(2) (barres de l'opérateur de la carte logée),
    // phoneMock.sim('UNKNOWN' | 'SUSPENDED' | 'DEACTIVATED' | 'ACTIVE' | 'NONE'), phoneMock.full(true) (file de demandes
    // pleine), phoneMock.founder(false), phoneMock.airplane(true), phoneMock.many() (41 cartes, 16 opérateurs).
    window.phoneMock = {
      state: mock,
      bars: function (n) {
        var op = mock.lodged ? operatorOf(mock.lodged.op) : null;
        if (op) { op.bars = n; }
        render(view('NONE'));
      },
      sim: function (validity) {
        if (validity === 'NONE') {
          mock.lodged = null;
        } else {
          if (!mock.lodged) {
            mock.lodged = { number: '06 12 34 56 78', holder: 'Alex', op: 'a1b2c3d4e5f6', validity: 'ACTIVE' };
          }
          mock.lodged.validity = validity;
        }
        render(view('NONE'));
      },
      full: function (on) { mock.requestsFull = on !== false; render(view('NONE')); },
      founder: function (on) { mock.founder = on !== false; render(view('NONE')); },
      airplane: function (on) { mock.airplane = on !== false; render(view('NONE')); },
      many: function () {
        mock.inventory = [];
        for (var s = 0; s < 41; s++) {
          mock.inventory.push({ slot: s, number: '07 00 00 ' + (s < 10 ? '0' : '') + s + ' 00', holder: '',
            op: s % 2 ? '3f2a9c1b0d4e' : 'a1b2c3d4e5f6', validity: s % 3 ? 'ACTIVE' : 'SUSPENDED' });
        }
        mock.operators = mock.operators.slice(0, 2);
        for (var o = 2; o < 16; o++) {
          var hex = o.toString(16);
          mock.operators.push({ id: '00000000000' + hex, name: 'Opérateur numéro ' + o, bars: o % 5 });
        }
        render(view('NONE'));
      },
      setup: function () {
        mock.session++; mock.code = null; mock.lock = 'SETUP'; mock.wallpaper = 'aube'; render(view('NONE'));
      },
      locked: function () { mock.session++; mock.code = '1234'; mock.lock = 'LOCKED'; render(view('NONE')); },
      notOwner: function () { mock.owner = false; render(view('NONE')); },
      battery: function (pct) { mock.battery = pct; render(view('NONE')); }
    };
  }

  // ------------------------------------------------------------------------------------------- Démarrage

  window.phone = { render: render };

  function start() {
    $('pad').classList.add('pad-park');
    $('lock-slot').appendChild($('pad'));
    phoneEl.addEventListener('click', onTap);
    document.addEventListener('keydown', onKey);
    $('settings-scroll').addEventListener('scroll', onScroll, { passive: true });
    $('sim-scroll').addEventListener('scroll', function () { scheduleLayout(150); }, { passive: true });
    window.addEventListener('resize', function () { lastLayout = ''; scheduleLayout(100); });
    setInterval(tickClock, 1000);
    setInterval(tickLockout, 250);
    if (mockMode && window.oneforall) {
      startMock();
    }
    send({ a: 'ready' });
  }

  if (window.oneforall) {
    window.oneforall.onReady(start);
  } else if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
