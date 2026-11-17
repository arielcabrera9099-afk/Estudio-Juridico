/*
 * Combo buscable: convierte cada <select> en un campo de texto donde se tipea y se muestran las coincidencias
 * para elegir. Busca por la descripción de la opción Y por su valor (código), sin distinguir mayúsculas ni acentos:
 * "norte" o "12" encuentran "12 — ZONA NORTE".
 *
 * El <select> original queda en el DOM (oculto) y es el que viaja en el formulario: se le pone el valor elegido
 * y se dispara "change", así siguen funcionando los onchange y los scripts de cada pantalla.
 * Para excluir un select: data-combo="no".
 */
(function () {
  'use strict';

  function normalizar(t) {
    return (t || '').toString().normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  }

  function etiqueta(opt) {
    return opt ? opt.textContent.replace(/\s+/g, ' ').trim() : '';
  }

  var lista = null;      // un único desplegable, en <body> (no lo recorta el overflow de los paneles)
  var activo = null;     // combo abierto

  function asegurarLista() {
    if (lista) return lista;
    lista = document.createElement('ul');
    lista.className = 'wwp-combo__lista';
    lista.setAttribute('role', 'listbox');
    lista.hidden = true;
    document.body.appendChild(lista);
    // mousedown: elegir antes de que el input pierda el foco
    lista.addEventListener('mousedown', function (e) {
      var li = e.target.closest('li[data-i]');
      if (!li || !activo) return;
      e.preventDefault();
      activo.elegir(parseInt(li.getAttribute('data-i'), 10));
    });
    window.addEventListener('resize', function () { if (activo) activo.ubicar(); });
    document.addEventListener('scroll', function () { if (activo) activo.ubicar(); }, true);
    return lista;
  }

  function Combo(select) {
    this.select = select;
    this.marcado = -1;
    this.visibles = [];

    var envoltura = document.createElement('span');
    envoltura.className = 'wwp-combo';
    select.parentNode.insertBefore(envoltura, select);
    envoltura.appendChild(select);
    select.classList.add('wwp-combo__select');
    select.tabIndex = -1;
    select.setAttribute('aria-hidden', 'true');

    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'wwp-combo__input';
    input.autocomplete = 'off';
    input.setAttribute('role', 'combobox');
    input.setAttribute('aria-autocomplete', 'list');
    input.setAttribute('aria-expanded', 'false');
    if (select.getAttribute('aria-label')) input.setAttribute('aria-label', select.getAttribute('aria-label'));
    if (select.id) { input.id = select.id + '__texto'; }
    envoltura.insertBefore(input, select);

    var flecha = document.createElement('span');
    flecha.className = 'wwp-combo__flecha';
    flecha.setAttribute('aria-hidden', 'true');
    envoltura.appendChild(flecha);

    this.envoltura = envoltura;
    this.input = input;
    this.refrescarTexto();

    var self = this;
    input.addEventListener('focus', function () { input.select(); self.abrir(''); });
    input.addEventListener('click', function () { if (lista.hidden) self.abrir(''); });
    input.addEventListener('input', function () { self.abrir(input.value); });
    input.addEventListener('keydown', function (e) { self.tecla(e); });
    input.addEventListener('blur', function () { setTimeout(function () { self.cerrar(true); }, 120); });
    flecha.addEventListener('mousedown', function (e) { e.preventDefault(); input.focus(); });
    // si otro script cambia el select, el texto lo acompaña
    select.addEventListener('change', function () { self.refrescarTexto(); });
    new MutationObserver(function () { self.refrescarTexto(); self.sincronizarEstado(); })
      .observe(select, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled'] });
    this.sincronizarEstado();
  }

  Combo.prototype.sincronizarEstado = function () {
    this.input.disabled = this.select.disabled;
    this.input.required = this.select.required;
    this.envoltura.classList.toggle('is-disabled', this.select.disabled);
  };

  Combo.prototype.refrescarTexto = function () {
    var opt = this.select.options[this.select.selectedIndex];
    this.input.value = etiqueta(opt);
    this.input.placeholder = this.input.value ? '' : 'Escriba para buscar…';
  };

  Combo.prototype.abrir = function (texto) {
    if (this.select.disabled) return;
    asegurarLista();
    activo = this;
    var q = normalizar(texto);
    var opciones = this.select.options;
    this.visibles = [];
    for (var i = 0; i < opciones.length; i++) {
      var o = opciones[i];
      if (o.hidden) continue;
      var t = normalizar(etiqueta(o)), v = normalizar(o.value);
      if (!q || t.indexOf(q) >= 0 || (v && v.indexOf(q) >= 0)) this.visibles.push(i);
    }
    var html = '';
    var self = this;
    this.visibles.forEach(function (i, pos) {
      var o = opciones[i];
      var txt = etiqueta(o) || ' ';
      var marca = q ? resaltar(txt, q) : escapar(txt);
      html += '<li role="option" data-i="' + i + '"'
        + (o.disabled ? ' aria-disabled="true" class="is-disabled"' : '')
        + (i === self.select.selectedIndex ? ' aria-selected="true"' : '') + '>' + marca + '</li>';
    });
    if (!this.visibles.length) html = '<li class="wwp-combo__vacio">Sin coincidencias</li>';
    lista.innerHTML = html;
    lista.hidden = false;
    this.input.setAttribute('aria-expanded', 'true');
    this.marcado = q && this.visibles.length ? 0 : this.visibles.indexOf(this.select.selectedIndex);
    this.pintarMarca();
    this.ubicar();
  };

  Combo.prototype.ubicar = function () {
    if (!lista || lista.hidden) return;
    var r = this.input.getBoundingClientRect();
    var alto = Math.min(280, window.innerHeight - r.bottom - 12);
    var arriba = alto < 140 && r.top > 300;
    lista.style.left = r.left + 'px';
    lista.style.minWidth = r.width + 'px';
    if (arriba) {
      lista.style.top = ''; lista.style.bottom = (window.innerHeight - r.top + 2) + 'px';
      lista.style.maxHeight = Math.min(280, r.top - 12) + 'px';
    } else {
      lista.style.bottom = ''; lista.style.top = (r.bottom + 2) + 'px';
      lista.style.maxHeight = Math.max(140, alto) + 'px';
    }
  };

  Combo.prototype.pintarMarca = function () {
    var items = lista.querySelectorAll('li[data-i]');
    for (var k = 0; k < items.length; k++) items[k].classList.toggle('is-marcado', k === this.marcado);
    var m = items[this.marcado];
    if (m) m.scrollIntoView({ block: 'nearest' });
  };

  Combo.prototype.tecla = function (e) {
    if (lista && lista.hidden && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) { this.abrir(''); e.preventDefault(); return; }
    if (!lista || lista.hidden) return;
    if (e.key === 'ArrowDown') { this.marcado = Math.min(this.visibles.length - 1, this.marcado + 1); this.pintarMarca(); e.preventDefault(); }
    else if (e.key === 'ArrowUp') { this.marcado = Math.max(0, this.marcado - 1); this.pintarMarca(); e.preventDefault(); }
    else if (e.key === 'Enter') { if (this.marcado >= 0) { this.elegir(this.visibles[this.marcado]); e.preventDefault(); } }
    else if (e.key === 'Escape') { this.cerrar(true); e.preventDefault(); }
    else if (e.key === 'Tab') { if (this.marcado >= 0 && this.input.value !== etiqueta(this.select.options[this.select.selectedIndex])) this.elegir(this.visibles[this.marcado]); }
  };

  Combo.prototype.elegir = function (i) {
    var o = this.select.options[i];
    if (!o || o.disabled) return;
    var cambio = this.select.selectedIndex !== i;
    this.select.selectedIndex = i;
    this.refrescarTexto();
    this.cerrar(false);
    if (cambio) this.select.dispatchEvent(new Event('change', { bubbles: true }));
  };

  Combo.prototype.cerrar = function (restaurar) {
    if (activo !== this) return;
    if (lista) lista.hidden = true;
    this.input.setAttribute('aria-expanded', 'false');
    if (restaurar) this.refrescarTexto();   // lo tipeado sin elegir no queda a medias
    activo = null;
  };

  function escapar(t) {
    return t.replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; });
  }

  function resaltar(txt, q) {
    var n = normalizar(txt);
    var p = n.indexOf(q);
    if (p < 0) return escapar(txt);
    return escapar(txt.slice(0, p)) + '<mark>' + escapar(txt.slice(p, p + q.length)) + '</mark>' + escapar(txt.slice(p + q.length));
  }

  function iniciar(raiz) {
    (raiz || document).querySelectorAll('select:not([multiple]):not([data-combo="no"]):not(.wwp-combo__select)')
      .forEach(function (s) { try { new Combo(s); } catch (e) { /* si falla, queda el select nativo */ } });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { iniciar(); });
  else iniciar();
  window.wwpCombo = { iniciar: iniciar };
})();
