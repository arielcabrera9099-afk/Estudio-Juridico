// Formularios de alta, baja y modificación (ABM).
//  - form[data-confirmar="¿…?"]: pide confirmación antes de enviar (después de validar los campos requeridos).
//    data-confirmar-tipo: pregunta (azul, por defecto) | aviso | error; data-confirmar-boton: texto del botón.
//  - Al enviar, los botones del formulario se deshabilitan para no mandar dos veces la misma operación.
//  - Los filtros de una lista (form[data-filtros]) se envían al cambiar un combo; "Limpiar" vuelve a la lista sin filtros.
(function () {
  function deshabilitar(form) {
    form.querySelectorAll('button[type="submit"], button:not([type])').forEach(function (b) { b.disabled = true; });
    document.querySelectorAll('button[form="' + form.id + '"]').forEach(function (b) { if (form.id) b.disabled = true; });
  }

  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!form || !form.getAttribute) return;
    var mensaje = form.getAttribute('data-confirmar');
    if (!mensaje) {
      if (!form.hasAttribute('data-filtros')) deshabilitar(form);
      return;
    }
    e.preventDefault();
    window.confirmar(mensaje, {
      tipo: form.getAttribute('data-confirmar-tipo') || 'pregunta',
      textoConfirmar: form.getAttribute('data-confirmar-boton') || 'Confirmar'
    }).then(function (si) {
      if (!si) return;
      deshabilitar(form);
      form.submit();
    });
  });

  // data-visible-si="campo:valor" (o "campo:valor1|valor2"): el elemento se muestra solo si el control "campo" del
  // formulario (select o radios) tiene ese valor. Oculto, sus controles quedan deshabilitados (no viajan ni se validan);
  // visibles, vuelven a su estado original (en Visualizar siguen deshabilitados).
  document.querySelectorAll('[data-visible-si]').forEach(function (el) {
    var partes = el.getAttribute('data-visible-si').split(':');
    var form = el.closest('form');
    if (!form || partes.length !== 2) return;
    var valores = partes[1].split('|');
    var controles = el.querySelectorAll('input, select, textarea');
    controles.forEach(function (c) { c.setAttribute('data-deshabilitado-original', c.disabled ? '1' : '0'); });
    function valor() {
      var campo = form.elements[partes[0]];
      if (!campo) return '';
      if (campo.length !== undefined && !campo.tagName) {
        for (var i = 0; i < campo.length; i++) if (campo[i].checked) return campo[i].value;
        return '';
      }
      return campo.value;
    }
    function aplicar() {
      var visible = valores.indexOf(valor()) >= 0;
      el.hidden = !visible;
      controles.forEach(function (c) { c.disabled = !visible || c.getAttribute('data-deshabilitado-original') === '1'; });
    }
    form.addEventListener('change', function (e) { if (e.target.name === partes[0]) aplicar(); });
    aplicar();
  });

  document.querySelectorAll('form[data-filtros] select').forEach(function (s) {
    s.addEventListener('change', function () { s.form.requestSubmit ? s.form.requestSubmit() : s.form.submit(); });
  });
})();
