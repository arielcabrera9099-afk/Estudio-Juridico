// Tema de la pantalla: siempre claro. Corre en <head> para fijar
// data-theme antes de pintar, sin mirar lo guardado en localStorage ni la preferencia del sistema.
(function () {
  var tema = 'light';
  window.__appTema = tema;
  document.addEventListener('DOMContentLoaded', function () {
    document.body.setAttribute('data-theme', tema);
    var shell = document.getElementById('admin-shell');
    if (shell) {
      shell.classList.remove('admin-shell--light', 'admin-shell--dark');
      shell.classList.add('admin-shell--' + tema);
    }
  });
  // body todavía no existe en <head>: se fija también en el elemento raíz para el primer pintado.
  document.documentElement.setAttribute('data-theme', tema);
})();
