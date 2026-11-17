// Grilla de pantalla (componentes/pantalla.css): el menú de acciones de cada fila (.gx-accion-grupo). La paginación
// (table[data-paginar="N"]) la arma componentes.js.
(function () {
  // Menú de acciones de la fila: el botón abre su menú; un clic afuera o Escape lo cierra.
  var cerrarMenus = function (salvo) {
    document.querySelectorAll('.gx-accion-grupo.is-abierto').forEach(function (g) {
      if (g === salvo) return;
      g.classList.remove('is-abierto');
      g.querySelector('.gx-accion-grupo__menu').hidden = true;
    });
  };
  document.addEventListener('click', function (e) {
    var b = e.target.closest('.gx-accion-grupo__boton');
    if (!b) { cerrarMenus(null); return; }
    var g = b.closest('.gx-accion-grupo');
    cerrarMenus(g);
    var abrir = !g.classList.contains('is-abierto');
    g.classList.toggle('is-abierto', abrir);
    var menu = g.querySelector('.gx-accion-grupo__menu');
    menu.hidden = !abrir;
    // Fijo respecto de la ventana: dentro de la grilla (overflow) quedaría recortado.
    var r = b.getBoundingClientRect();
    menu.style.position = 'fixed';
    menu.style.left = r.left + 'px';
    menu.style.top = (r.bottom + 4) + 'px';
  });
  window.addEventListener('scroll', function () { cerrarMenus(null); }, true);
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') cerrarMenus(null); });

})();
