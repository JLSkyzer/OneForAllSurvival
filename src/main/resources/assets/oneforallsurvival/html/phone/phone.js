// Le téléphone du mod. Java appelle window.phone.render(view) à chaque état reçu du serveur ; la page répond par
// window.oneforall.send(...) (bridge.js). Contrat :
//   page -> Java : {"a":"ready"} ; {"a":"layout","rects":{"<id>":[x,y,w,h],...}} (px CSS, après chaque changement
//   d'écran, défilement ou redimensionnement) ; {"a":"unlock","code":"…"} ; {"a":"setup","code":"…"} ; {"a":"lock"} ;
//   {"a":"changeCode","old":"…","new":"…"} ; {"a":"wallpaper","id":"…"} ; {"a":"airplane","on":true|false} ;
//   {"a":"erase","code":"…"}.
//   Java -> page : view = {session, lock: "SETUP"|"LOCKED"|"UNLOCKED", battery, serverMillis, zoneOffsetSeconds,
//   wallpaper, airplane, owner, ownerName, attemptsLeft, lockoutSeconds, last}.
// Le serveur fait foi : la page ne juge aucun code, elle les transmet. Tout texte venu de la vue passe par textContent.
// Avec une adresse qui finit par #mock, une fausse vue et un faux serveur (code 1234) tiennent lieu du jeu.
(function () {
  'use strict';

  var CODE_LENGTH = 4;
  var WALLPAPERS = ['aube', 'lagon', 'jungle', 'nuit'];
  var LOCKS = ['SETUP', 'LOCKED', 'UNLOCKED'];
  var DAYS = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];
  var MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre',
    'novembre', 'décembre'];
  var PENDING_MS = 2500; // le serveur ne répond pas à un geste trop rapproché : la page n'attend pas sans fin
  var LAYOUT_DELAY_MS = 720; // après la fin des animations d'écran (0,63 s au plus)

  var mockMode = String(window.location.href).slice(-5) === '#mock';

  function $(id) { return document.getElementById(id); }

  var phoneEl = $('phone');

  // ------------------------------------------------------------------------------------------- État de la page

  var state = {
    view: null,
    session: null,
    screen: 'lock', // setup | lock | home | settings
    clockBase: 0, // heure locale du serveur (ms) à la réception
    clockAt: 0, // performance.now() à la réception
    lockoutEnd: 0, // performance.now() de fin du blocage, 0 sans blocage
    showAttempts: false,
    pending: null, // geste envoyé, en attente de réponse : { kind, timer }
    setupFirst: null, // première saisie du choix du code
    sheet: null, // { mode: 'change'|'erase', step, old, fresh }
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

  function clean(v) {
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
      last: typeof v.last === 'string' ? v.last : 'NONE'
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

  function paintStatic(v) {
    var w = $('wall');
    w.className = 'wall wp-' + v.wallpaper;

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
  }

  function paintChrome() {
    var light = state.screen === 'settings' || phoneEl.classList.contains('sheet-open');
    $('status').classList.toggle('ink-dark', light && state.screen !== 'lock' && state.screen !== 'setup');
    phoneEl.classList.toggle('ink-dark', light && state.screen !== 'lock' && state.screen !== 'setup');
  }

  // ------------------------------------------------------------------------------------------- Écrans

  function setScreen(screen) {
    state.screen = screen;
    phoneEl.classList.remove('scr-setup', 'scr-lock', 'scr-home', 'scr-settings');
    phoneEl.classList.add('scr-' + screen);
    phoneEl.classList.toggle('app-open', screen === 'settings');
    placePad();
    paintChrome();
    scheduleLayout();
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
    if (phoneEl.classList.contains('alert-open')) {
      return null;
    }
    if (phoneEl.classList.contains('sheet-open')) {
      return 'sheet';
    }
    if (state.screen === 'lock' || state.screen === 'setup') {
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
      send({ a: 'setup', code: code });
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
      phoneEl.classList.remove('sheet-open', 'alert-open');
      state.sheet = null;
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
        phoneEl.classList.remove('sheet-open', 'alert-open');
        state.sheet = null;
        clearEntry('lock');
        state.setupFirst = null;
        state.showAttempts = false;
      }
      setScreen(v.lock === 'SETUP' ? 'setup' : v.lock === 'LOCKED' ? 'lock' : 'home');
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
      }
    } else if (state.screen === 'home') {
      add('app-settings');
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
    var mock = {
      code: '1234',
      session: 1,
      lock: 'LOCKED',
      battery: 76,
      wallpaper: 'aube',
      airplane: false,
      owner: true,
      ownerName: 'Alex',
      failures: 0,
      lockedUntil: 0,
      maxAttempts: 5,
      lockoutMillis: 60000
    };

    function view(last) {
      var now = Date.now();
      var remaining = Math.max(0, mock.lockedUntil - now);
      var lockout = remaining > 0 ? Math.ceil(remaining / 1000) : 0;
      var unlocked = mock.lock === 'UNLOCKED';
      return {
        session: mock.session, lock: mock.lock, battery: mock.battery, serverMillis: now, zoneOffsetSeconds: 3600,
        wallpaper: mock.wallpaper, airplane: unlocked && mock.airplane, owner: unlocked && mock.owner,
        ownerName: unlocked ? mock.ownerName : '', attemptsLeft: lockout > 0 ? 0 : mock.maxAttempts - mock.failures,
        lockoutSeconds: lockout, last: last || 'NONE'
      };
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
        default:
          break;
      }
    }

    window.oneforall.mock = handle;
    // Commandes de la console, pour l'essai : phoneMock.setup(), phoneMock.notOwner(), phoneMock.battery(12).
    window.phoneMock = {
      state: mock,
      setup: function () { mock.session++; mock.code = null; mock.lock = 'SETUP'; render(view('NONE')); },
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
