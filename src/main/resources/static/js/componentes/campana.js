// Campana de avisos de la barra superior ([data-campana] en layout.html).
//  - El contador de avisos sin leer se consulta cada minuto (data-url-contador). Si aumenta, avisa con notificar().
//  - Al abrir la campana se cargan los últimos avisos (data-url-panel, un fragmento HTML del servidor).
//  - Se cierra con un clic afuera o con Escape.
(function () {
  var campana = document.querySelector('[data-campana]');
  if (!campana) return;
  var boton = campana.querySelector('[data-campana-abrir]');
  var panel = campana.querySelector('[data-campana-panel]');
  var contador = campana.querySelector('[data-campana-contador]');
  var urlContador = campana.getAttribute('data-url-contador');
  var urlPanel = campana.getAttribute('data-url-panel');
  var ultimo = parseInt(contador.textContent, 10) || 0;
  var INTERVALO = 60000;

  function pintar(n) {
    contador.hidden = n === 0;
    contador.textContent = n > 99 ? '99+' : String(n);
    boton.title = n > 0 ? 'Avisos: ' + n + ' sin leer' : 'Avisos';
  }

  function consultar() {
    if (document.hidden) return;
    fetch(urlContador, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(function (r) { return r.ok && (r.headers.get('content-type') || '').indexOf('json') >= 0 ? r.json() : null; })
      .then(function (d) {
        if (!d) return;
        if (d.noLeidas > ultimo && window.notificar) {
          window.notificar(d.noLeidas - ultimo === 1 ? 'Tiene un aviso nuevo.' : 'Tiene ' + (d.noLeidas - ultimo) + ' avisos nuevos.', { tipo: 'info' });
        }
        ultimo = d.noLeidas;
        pintar(ultimo);
      })
      .catch(function () { /* sin conexión: se reintenta en la próxima consulta */ });
  }

  function abrir(abierto) {
    panel.hidden = !abierto;
    boton.setAttribute('aria-expanded', abierto ? 'true' : 'false');
    if (!abierto) return;
    fetch(urlPanel, { credentials: 'same-origin' })
      .then(function (r) { return r.ok ? r.text() : Promise.reject(); })
      .then(function (html) { panel.innerHTML = html; })
      .catch(function () { panel.innerHTML = '<p class="lx-campana-vacio">No se pudieron cargar los avisos.</p>'; });
  }

  boton.addEventListener('click', function (e) {
    e.stopPropagation();
    abrir(panel.hidden);
  });
  document.addEventListener('click', function (e) {
    if (!panel.hidden && !e.target.closest('[data-campana]')) abrir(false);
  });
  window.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !panel.hidden) { abrir(false); boton.focus(); }
  });
  setInterval(consultar, INTERVALO);
  document.addEventListener('visibilitychange', consultar);
})();
