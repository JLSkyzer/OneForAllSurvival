// Pont des pages du mod vers le jeu : window.oneforall.send(message) et window.oneforall.onReady(fn).
// En jeu, MCEF fournit window.oneforallQuery (routeur de messages du mod). Hors jeu, ou avec une adresse qui finit
// par #mock, le message va au journal de la console et au rappel window.oneforall.mock(message) si la page en pose un.
(function () {
  'use strict';

  var mockMode = String(window.location.href).slice(-5) === '#mock';

  // Le journal de la console ne montre jamais un code saisi (champs code, old, new).
  var SECRET_KEYS = ['code', 'old', 'new'];

  function masked(message) {
    if (!message || typeof message !== 'object') {
      return JSON.stringify(message);
    }
    var copy = {};
    for (var key in message) {
      if (Object.prototype.hasOwnProperty.call(message, key)) {
        copy[key] = SECRET_KEYS.indexOf(key) >= 0 ? '****' : message[key];
      }
    }
    return JSON.stringify(copy);
  }

  var api = {
    send: function (message) {
      var request = JSON.stringify(message);
      if (!mockMode && typeof window.oneforallQuery === 'function') {
        window.oneforallQuery({ request: request, onSuccess: function () {}, onFailure: function () {} });
        return;
      }
      if (window.console) {
        window.console.log('[oneforall] ' + masked(message));
      }
      if (typeof api.mock === 'function') {
        api.mock(message);
      }
    },
    onReady: function (fn) {
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', fn);
      } else {
        fn();
      }
    },
    mock: null
  };

  window.oneforall = api;

  // Pas de menu contextuel de Chromium dans le jeu.
  document.addEventListener('contextmenu', function (e) {
    e.preventDefault();
  });
})();
