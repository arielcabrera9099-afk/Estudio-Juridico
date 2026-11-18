// Comportamiento del layout: colapsar el menú, acordeón de los módulos, búsqueda de pantallas, menú móvil (< 640px: solo celulares; en una computadora, aun con zoom, el menú queda fijo)
// y tema. Cada pantalla recarga la página, así que el colapso se recuerda en localStorage y el estado de los módulos
// y secciones en sessionStorage para que no se pierdan al navegar.
(function () {
  var CLAVE_COLAPSO = 'app-sidebar-colapsado';
  // Módulo desplegado: arranca plegado y se recuerda en sessionStorage dentro de la sesión.
  var CLAVE_ABIERTO = 'app-modulo-abierto';
  var ANCHO_MOVIL = 640;

  function $(sel, raiz) { return (raiz || document).querySelector(sel); }
  function $$(sel, raiz) { return Array.prototype.slice.call((raiz || document).querySelectorAll(sel)); }
  function guardar(clave, valor) { try { localStorage.setItem(clave, valor); } catch (e) { /* modo privado */ } }
  function leer(clave) { try { return localStorage.getItem(clave); } catch (e) { return null; } }

  var shell = document.getElementById('admin-shell');
  if (!shell) return;
  var sidebar = $('[data-shell="sidebar"]');
  var backdrop = $('[data-shell="backdrop"]');
  // Módulos del menú: cada uno con su acordeón y su estado.
  var modulos = $$('[data-shell="modulo"]');
  var buscar = $('[data-shell="buscar"]');
  // Secciones plegadas dentro de los módulos (por rótulo): arrancan abiertas y se recuerdan en la sesión.
  var CLAVE_SECCIONES = 'app-secciones-plegadas';
  var plegadas = {};
  try { plegadas = JSON.parse(sessionStorage.getItem(CLAVE_SECCIONES) || '{}') || {}; } catch (e) { plegadas = {}; }
  function claveAbierto(m) {
    var k = m.getAttribute('data-modulo') || 'principal';
    return CLAVE_ABIERTO + '-' + k;
  }

  var estado = {
    colapsado: leer(CLAVE_COLAPSO) === '1',
    movil: window.innerWidth < ANCHO_MOVIL,
    movilAbierto: false,
    abiertos: modulos.map(function (m) {
      try { return sessionStorage.getItem(claveAbierto(m)) === '1'; } catch (e) { return false; }
    }),
    termino: ''
  };

  // ---------------------------------------------------------------- navegación (botones con data-href)
  document.addEventListener('click', function (ev) {
    var b = ev.target.closest('[data-href]');
    if (!b) return;
    ev.preventDefault();
    // data-target="_blank": la opción abre en otra pestaña.
    if (b.getAttribute('data-target') === '_blank') { window.open(b.getAttribute('data-href'), '_blank'); return; }
    window.location.href = b.getAttribute('data-href');
  });

  // ---------------------------------------------------------------- pintar según el estado
  function pintar() {
    var colapsado = estado.colapsado && !estado.movil;
    sidebar.className = ['admin-sidebar',
      estado.movil ? 'admin-sidebar--mobile' : '',
      estado.movil && !estado.movilAbierto ? 'admin-sidebar--mobile-closed' : '',
      colapsado ? 'admin-sidebar--collapsed' : 'admin-sidebar--expanded'].filter(Boolean).join(' ');
    backdrop.hidden = !(estado.movil && estado.movilAbierto);
    $$('[data-shell="solo-expandido"]').forEach(function (e) { e.hidden = colapsado; });
    $$('[data-shell="solo-colapsado"]').forEach(function (e) { e.hidden = !colapsado; });
    $('[data-shell="abrir-movil"]').hidden = !estado.movil;
    var btn = $('[data-shell="colapsar"]');
    btn.hidden = estado.movil;
    btn.setAttribute('aria-label', colapsado ? 'Expandir menú' : 'Colapsar menú');
    btn.title = btn.getAttribute('aria-label');
    $('[data-shell="icono-colapsar"] use').setAttribute('href', colapsado ? '#i-panel-left-open' : '#i-panel-left-close');

    // Módulos: colapsado = solo el ícono; expandido = acordeón.
    var buscando = estado.termino.length > 0;
    modulos.forEach(function (modulo, i) {
      var abierto = estado.abiertos[i] || buscando;
      // El módulo de la pantalla activa, plegado, resalta su encabezado (accent + '14').
      var activo = !!$('.admin-nav-item.is-active', modulo);
      modulo.className = colapsado ? 'admin-module admin-module--collapsed'
        : 'admin-module' + (abierto ? ' is-open' : '') + (activo ? ' has-active' : '');
      var cabeza = $('[data-shell="acordeon"]', modulo);
      cabeza.setAttribute('aria-expanded', String(abierto));
      var accent = cabeza.getAttribute('data-accent');
      cabeza.style.background = colapsado ? (activo ? accent + '18' : '') : (activo && !abierto ? accent + '14' : '');
      $('[data-shell="cuerpo-modulo"]', modulo).hidden = colapsado || !abierto;
      var icono = $('.admin-module__icon svg', cabeza);
      icono.setAttribute('width', colapsado ? 18 : 17);
      icono.setAttribute('height', colapsado ? 18 : 17);
    });
    // Secciones: plegadas solo si el usuario las plegó; buscando se ven todas.
    $$('[data-shell="seccion-titulo"]').forEach(function (t) {
      var abierta = buscando || !plegadas[t.getAttribute('data-seccion')];
      t.setAttribute('aria-expanded', String(abierta));
      t.parentNode.classList.toggle('is-plegada', !abierta);
      var items = $('[data-shell="items-seccion"]', t.parentNode);
      if (items) items.hidden = !abierta;
    });
  }

  // ---------------------------------------------------------------- colapsar / móvil
  $('[data-shell="colapsar"]').addEventListener('click', function () {
    estado.colapsado = !estado.colapsado;
    guardar(CLAVE_COLAPSO, estado.colapsado ? '1' : '0');
    pintar();
  });
  $('[data-shell="abrir-movil"]').addEventListener('click', function () { estado.movilAbierto = true; pintar(); });
  backdrop.addEventListener('click', function () { estado.movilAbierto = false; pintar(); });
  window.addEventListener('resize', function () {
    var movil = window.innerWidth < ANCHO_MOVIL;
    if (movil !== estado.movil) { estado.movil = movil; if (!movil) estado.movilAbierto = false; pintar(); }
  });

  // ---------------------------------------------------------------- acordeón
  modulos.forEach(function (modulo, i) {
    $('[data-shell="acordeon"]', modulo).addEventListener('click', function () {
      if (estado.colapsado && !estado.movil) {
        // Colapsado: el encabezado lleva a la primera pantalla del módulo.
        var primero = $('.admin-nav-item[data-href]', modulo);
        if (primero) window.location.href = primero.getAttribute('data-href');
        return;
      }
      estado.abiertos[i] = !estado.abiertos[i];
      try { sessionStorage.setItem(claveAbierto(modulo), estado.abiertos[i] ? '1' : '0'); } catch (e) { /* modo privado */ }
      pintar();
    });
  });

  // ---------------------------------------------------------------- secciones del módulo
  // La sección de la pantalla actual se abre siempre al entrar, aunque se haya plegado antes.
  $$('[data-shell="seccion-titulo"]').forEach(function (t) {
    if ($('.admin-nav-item.is-active', t.parentNode)) delete plegadas[t.getAttribute('data-seccion')];
    t.addEventListener('click', function () {
      var k = t.getAttribute('data-seccion');
      if (plegadas[k]) delete plegadas[k]; else plegadas[k] = 1;
      try { sessionStorage.setItem(CLAVE_SECCIONES, JSON.stringify(plegadas)); } catch (e) { /* modo privado */ }
      pintar();
    });
  });

  // ---------------------------------------------------------------- búsqueda
  function filtrar() {
    var q = estado.termino.toLowerCase();
    var hay = 0;
    $$('[data-shell="seccion"]').forEach(function (sec) {
      var visibles = 0;
      $$('.admin-nav-item', sec).forEach(function (it) {
        var ok = !q || (it.getAttribute('data-titulo') || '').toLowerCase().indexOf(q) >= 0;
        it.hidden = !ok; if (ok) visibles++;
      });
      // Sin búsqueda se ven todas las secciones, también las que todavía no tienen opciones.
      sec.hidden = !!q && visibles === 0; hay += visibles;
    });
    $$('.admin-quick__btn').forEach(function (it) {
      var ok = !q || (it.getAttribute('data-titulo') || '').toLowerCase().indexOf(q) >= 0;
      it.hidden = !ok; if (ok) hay++;
    });
    modulos.forEach(function (modulo) {
      // Un módulo todavía sin opciones se ve mientras no se busque.
      modulo.hidden = !!q && $$('[data-shell="seccion"]', modulo).every(function (s) { return s.hidden; });
    });
    $('[data-shell="sin-resultados"]').hidden = !(q && hay === 0);
    $('[data-shell="termino"]').textContent = estado.termino;
    $('[data-shell="limpiar"]').hidden = !q;
    $('[data-shell="buscador"]').classList.toggle('is-active', !!q);
    pintar();
  }
  function limpiar() { buscar.value = ''; estado.termino = ''; filtrar(); }
  buscar.addEventListener('input', function () { estado.termino = buscar.value.trim(); filtrar(); });
  $('[data-shell="limpiar"]').addEventListener('click', function () { limpiar(); buscar.focus(); });
  $('[data-shell="limpiar-busqueda"]').addEventListener('click', limpiar);
  window.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && document.activeElement === buscar) { limpiar(); buscar.blur(); }
  });
  $$('[data-shell="abrir-buscador"]').forEach(function (b) {
    b.addEventListener('click', function () {
      if (estado.colapsado) { estado.colapsado = false; guardar(CLAVE_COLAPSO, '0'); }
      if (estado.movil) estado.movilAbierto = true;
      pintar();
      requestAnimationFrame(function () { buscar.focus(); });
    });
  });

  // ---------------------------------------------------------------- menú del usuario (cerrar sesión)
  var abrirUsuario = $('[data-shell="abrir-usuario"]');
  var menuUsuario = $('[data-shell="menu-usuario"]');
  if (abrirUsuario && menuUsuario) {
    var verMenuUsuario = function (abierto) {
      menuUsuario.hidden = !abierto;
      abrirUsuario.setAttribute('aria-expanded', abierto ? 'true' : 'false');
    };
    abrirUsuario.addEventListener('click', function (e) {
      e.stopPropagation();
      verMenuUsuario(menuUsuario.hidden);
    });
    document.addEventListener('click', function (e) {
      if (!menuUsuario.hidden && !e.target.closest('[data-shell="usuario"]')) verMenuUsuario(false);
    });
    window.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !menuUsuario.hidden) { verMenuUsuario(false); abrirUsuario.focus(); }
    });
  }

  // ---------------------------------------------------------------- tema
  // Sin modo oscuro: la pantalla queda siempre en claro.
  function aplicarTema(tema) {
    document.body.setAttribute('data-theme', tema);
    document.documentElement.setAttribute('data-theme', tema);
    shell.classList.remove('admin-shell--light', 'admin-shell--dark');
    shell.classList.add('admin-shell--' + tema);
  }

  aplicarTema('light');
  pintar();

  // ---------------------------------------------------------------- encabezado de la pantalla en la barra superior
  // El título (con su ícono) y la descripción de la tarjeta de encabezado de cada pantalla pasan a la barra superior,
  // junto al módulo, en lugar del nombre de la pantalla. La tarjeta se va si no le queda nada (botones, avisos).
  var pagina = $('.admin-header__pagina');
  var encabezado = $('#contenido > header.gx-encabezado');
  var h1 = encabezado && $('h1', encabezado);
  if (pagina && h1) {
    var bloque = document.createElement('span');
    bloque.className = 'admin-header__pagina admin-header__pagina--encabezado';
    var titulo = document.createElement('span');
    titulo.className = 'admin-header__pagina-titulo';
    while (h1.firstChild) titulo.appendChild(h1.firstChild);
    bloque.appendChild(titulo);
    var p = $('p', encabezado);
    if (p) {
      var sub = document.createElement('span');
      sub.className = 'admin-header__pagina-sub';
      while (p.firstChild) sub.appendChild(p.firstChild);
      bloque.appendChild(sub);
      p.parentNode.removeChild(p);
    }
    h1.parentNode.removeChild(h1);
    pagina.parentNode.replaceChild(bloque, pagina);
    if (!encabezado.textContent.trim() && !$('button, a, input, select', encabezado)) encabezado.parentNode.removeChild(encabezado);
    else encabezado.classList.add('is-sin-titulo');
  }

  // ---------------------------------------------------------------- posición del menú
  // Cada pantalla recarga la página y el menú volvería arriba: se recuerda su desplazamiento al salir y se repone al
  // entrar. Sin posición guardada (primera pantalla de la sesión), se muestra la opción activa.
  var CLAVE_SCROLL = 'app-sidebar-scroll';
  var desplazable = $('.admin-sidebar__scroll', sidebar);
  if (desplazable) {
    var guardada = null;
    try { guardada = sessionStorage.getItem(CLAVE_SCROLL); } catch (e) { /* modo privado */ }
    if (guardada !== null) {
      desplazable.scrollTop = Number(guardada) || 0;
    } else {
      // Solo en vertical y solo dentro del menú: scrollIntoView también desplazaría en horizontal la barra lateral (y
      // la página) si algo del menú fuera más ancho que ella, y dejaría cortado el borde izquierdo.
      var activa = $('.admin-nav-item.is-active', desplazable);
      if (activa) {
        var ra = activa.getBoundingClientRect();
        var rd = desplazable.getBoundingClientRect();
        if (ra.bottom > rd.bottom) desplazable.scrollTop += ra.bottom - rd.bottom;
        else if (ra.top < rd.top) desplazable.scrollTop -= rd.top - ra.top;
      }
    }
    desplazable.scrollLeft = 0;
    sidebar.scrollLeft = 0;
    window.addEventListener('pagehide', function () {
      try { sessionStorage.setItem(CLAVE_SCROLL, String(desplazable.scrollTop)); } catch (e) { /* modo privado */ }
    });
  }
})();
