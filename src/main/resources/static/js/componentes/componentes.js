// Componentes de interacción comunes (se carga en el layout, en todas las pantallas):
//  - avisar / confirmar / notificar: diálogos y mensajes flotantes (nunca alert/confirm del navegador)
//  - validación de campos requeridos (label.requerido)
//  - enviarJson: pedidos JSON al servidor con el CSRF del layout
//  - paginación de grillas (table[data-paginar])
//  - modales (data-abrir-modal / data-cerrar-modal), paneles plegables (data-plegable)
//  - campo de importe (data-input-importe: muestra 500.000,5 y envía 500000.5)
//  - progreso por pasos (window.progresoPorPasos)
(function () {
  var ICONOS = {
    exito: 'check-circle2',
    aviso: 'alert-triangle',
    error: 'alert-triangle',
    pregunta: 'shield-check'
  };

  // El diálogo de confirmación de todas las pantallas: arriba al centro, con la franja y el ícono del tipo (pregunta,
  // aviso, error = acción que borra o anula), el mensaje y el pie con Cancelar y la acción a la derecha. Enter activa el
  // botón con el foco (arranca en Cancelar: un Enter de más no confirma), Escape cancela y el foco no sale del diálogo.
  function abrir(op) {
    return new Promise(function (resolve) {
      var tipo = ICONOS[op.tipo] ? op.tipo : 'aviso';
      var anterior = document.activeElement;
      var overlay = document.createElement('div');
      overlay.className = 'app-modal-overlay gx-dialogo-fondo';
      overlay.style.zIndex = 10000;
      var id = 'gx-dialogo-' + Date.now();
      // El título al centro, una raya, el mensaje al centro, otra raya y los botones a la derecha (las rayas van del
      // color del tipo).
      overlay.innerHTML = '<div class="gx-dialogo gx-dialogo--' + tipo + '" role="alertdialog" aria-modal="true"'
        + ' aria-labelledby="' + id + '-t" aria-describedby="' + id + '-m">'
        + '<div class="gx-dialogo__cuerpo">'
        + '<div class="gx-dialogo__cabecera">'
        + '<span class="gx-dialogo__icono" aria-hidden="true"><svg class="lucide" width="18" height="18"><use href="#i-' + ICONOS[tipo]
        + '"/></svg></span><h3 class="gx-dialogo__titulo" id="' + id + '-t"></h3>'
        + '<button type="button" class="gx-dialogo__cerrar" data-v="0" aria-label="Cerrar">'
        + '<svg class="lucide" width="18" height="18"><use href="#i-x"/></svg></button></div>'
        + '<p class="gx-dialogo__mensaje" id="' + id + '-m"></p></div>'
        + '<div class="gx-dialogo__pie">'
        + (op.confirmacion ? '<button type="button" class="gx-btn gx-btn--secundario" data-v="0"></button>' : '')
        + '<button type="button" class="gx-btn gx-dialogo__accion" data-v="1"></button>'
        + '</div></div>';
      overlay.querySelector('.gx-dialogo__titulo').textContent = op.titulo || 'Confirmación';
      overlay.querySelector('.gx-dialogo__mensaje').textContent = op.mensaje;
      var cancelar = overlay.querySelector('.gx-dialogo__pie [data-v="0"]');
      if (cancelar) cancelar.textContent = op.textoCancelar || 'Cancelar';
      overlay.querySelector('.gx-dialogo__pie [data-v="1"]').textContent = op.textoConfirmar || (op.confirmacion ? 'Confirmar' : 'Aceptar');
      var ventana = overlay.querySelector('.gx-dialogo');
      var scroll = document.body.style.overflow;

      function cerrar(valor) {
        window.removeEventListener('keydown', teclas, true);
        document.body.style.overflow = scroll;
        overlay.remove();
        if (anterior && anterior.focus && document.contains(anterior)) {
          try { anterior.focus({ preventScroll: true }); } catch (e) { /* sin foco */ }
        }
        resolve(valor);
      }
      function teclas(e) {
        if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); cerrar(false); return; }
        if (e.key === 'Enter') {
          e.preventDefault();
          e.stopPropagation();
          var b = document.activeElement && document.activeElement.closest && document.activeElement.closest('.gx-dialogo [data-v]');
          if (b && ventana.contains(b)) cerrar(b.getAttribute('data-v') === '1');
          return;
        }
        if (e.key === 'Tab') {
          var botones = Array.prototype.slice.call(ventana.querySelectorAll('.gx-dialogo__pie button'));
          var i = botones.indexOf(document.activeElement);
          e.preventDefault();
          botones[(i + (e.shiftKey ? botones.length - 1 : 1)) % botones.length].focus();
        }
      }
      overlay.addEventListener('click', function (e) {
        var b = e.target.closest('[data-v]');
        if (b) return cerrar(b.getAttribute('data-v') === '1');
        // Fuera del diálogo: una confirmación no se decide sola; un aviso se cierra.
        if (e.target === overlay) {
          if (op.confirmacion) {
            ventana.classList.remove('gx-dialogo--llamar');
            void ventana.offsetWidth;
            ventana.classList.add('gx-dialogo--llamar');
          } else {
            cerrar(true);
          }
        }
      });
      window.addEventListener('keydown', teclas, true);
      document.body.style.overflow = 'hidden';
      document.body.appendChild(overlay);
      // Se arrastra con el mouse desde el cuerpo (no desde los botones), sin salir de la ventana. Va en la propiedad
      // translate: las animaciones de entrada y de "llamar" usan transform y no la pisan.
      var cuerpo = overlay.querySelector('.gx-dialogo__cuerpo');
      var dx = 0, dy = 0;
      cuerpo.addEventListener('pointerdown', function (e) {
        if (e.button !== 0 || e.target.closest('button')) return;
        e.preventDefault();
        var x0 = e.clientX - dx, y0 = e.clientY - dy;
        var r = ventana.getBoundingClientRect();
        var baseX = r.left - dx, baseY = r.top - dy;
        ventana.classList.add('gx-dialogo--moviendo');
        var mover = function (ev) {
          var minX = 8 - baseX, maxX = window.innerWidth - 8 - r.width - baseX;
          var minY = 8 - baseY, maxY = window.innerHeight - 8 - r.height - baseY;
          dx = Math.min(Math.max(ev.clientX - x0, minX), maxX);
          dy = Math.min(Math.max(ev.clientY - y0, minY), maxY);
          ventana.style.translate = dx + 'px ' + dy + 'px';
        };
        var soltar = function () {
          ventana.classList.remove('gx-dialogo--moviendo');
          document.removeEventListener('pointermove', mover);
          document.removeEventListener('pointerup', soltar);
        };
        document.addEventListener('pointermove', mover);
        document.addEventListener('pointerup', soltar);
      });
      (cancelar || overlay.querySelector('[data-v="1"]')).focus({ preventScroll: true });
    });
  }

  // Mensaje flotante: arriba a la derecha, apilados, semitransparentes hasta pasar el mouse, con título, texto y la x;
  // se van solos a los 8 segundos.
  var TOAST = { error: 'error', aviso: 'aviso', exito: 'exito', info: 'info', pregunta: 'info' };
  var TITULO_TOAST = { error: 'ERROR', aviso: 'Atención', exito: 'Éxito', info: 'Información' };
  var pila;
  // Al salir de la página el navegador corta los pedidos en curso y fetch falla con su texto en inglés ("Failed to
  // fetch"); ese error no es real (la página ya se va) y no se muestra ni se guarda para la siguiente. Fuera de eso,
  // el error de red sale en castellano.
  var saliendo = false;
  window.addEventListener('beforeunload', function () { saliendo = true; });
  window.addEventListener('pagehide', function () { saliendo = true; });
  window.addEventListener('pageshow', function () { saliendo = false; });
  var ERROR_RED = /^(Failed to fetch|NetworkError when attempting to fetch resource\.?|Load failed|network error)$/i;
  window.notificar = function (mensaje, op) {
    op = op || {};
    if (ERROR_RED.test(String(mensaje || '').trim())) {
      if (saliendo) return Promise.resolve(false);
      mensaje = 'No hay conexión con el servidor. Revisá la conexión y repetí la operación.';
    }
    var tipo = TOAST[op.tipo] || 'aviso';
    if (!pila) {
      pila = document.createElement('div');
      pila.className = 'gx-notificaciones';
      pila.setAttribute('aria-live', 'polite');
      document.body.appendChild(pila);
    }
    var n = document.createElement('div');
    n.className = 'gx-notificacion gx-notificacion--' + tipo;
    n.setAttribute('role', tipo === 'error' ? 'alert' : 'status');
    n.innerHTML = '<button type="button" class="gx-notificacion__cerrar" aria-label="Cerrar">'
      + '<svg class="lucide" width="15" height="15" aria-hidden="true"><use href="#i-x"/></svg></button>'
      + '<strong class="gx-notificacion__titulo"></strong><span class="gx-notificacion__texto"></span>';
    // Título vacío explícito (''): sale sin título. Los errores siempre con la cabecera ERROR (aunque la pantalla
    // pida otro título).
    var titulo = tipo === 'error' ? 'ERROR' : (op.titulo != null ? op.titulo : TITULO_TOAST[tipo]);
    n.querySelector('strong').textContent = titulo;
    n.querySelector('strong').hidden = titulo === '';
    n.querySelector('span').textContent = mensaje;
    // Si la página se recarga o navega antes de que se cierre, vuelve a salir en la siguiente, pero solo por el tiempo
    // que le quedaba (op.vence): no reinicia los 8 segundos en cada pantalla.
    var clave = 'gx-notif-' + Date.now() + '-' + Math.random().toString(36).slice(2);
    var vence = op.vence || Date.now() + 8000;
    var recordar = function () {
      if (op.recordar === false) return;
      try {
        sessionStorage.setItem(clave, JSON.stringify({ mensaje: mensaje, tipo: tipo, titulo: op.titulo != null ? op.titulo : null, vence: vence }));
      } catch (e) { /* sin storage */ }
    };
    recordar();
    var quitar = function () {
      try { sessionStorage.removeItem(clave); } catch (e) { /* sin storage */ }
      if (n.parentNode) n.parentNode.removeChild(n);
    };
    var reloj = setTimeout(quitar, Math.max(0, vence - Date.now()));
    n.addEventListener('mouseenter', function () { clearTimeout(reloj); });
    n.addEventListener('mouseleave', function () {
      vence = Date.now() + 3000;
      recordar();
      reloj = setTimeout(quitar, 3000);
    });
    n.querySelector('button').addEventListener('click', quitar);
    pila.appendChild(n);
    return Promise.resolve(true);
  };
  window.avisar = function (mensaje, op) {
    // "X es requerido." (de la pantalla o del servidor): debajo del campo X, si está a la vista.
    if (marcarPorTexto(mensaje)) return Promise.resolve(true);
    return window.notificar(mensaje, op);
  };

  function marcarPorTexto(mensaje) {
    var m = /^(.+?) es requerido\./.exec(String(mensaje || '').trim());
    if (!m || !window.marcarRequerido) return false;
    var nombre = m[1].trim().toLowerCase();
    var labels = document.querySelectorAll('.gx-campo > label, .gx-campo label');
    for (var i = 0; i < labels.length; i++) {
      var l = labels[i];
      if (l.textContent.replace(/\*/g, '').replace(/\s+/g, ' ').trim().toLowerCase() !== nombre) continue;
      var id = l.getAttribute('for');
      var c = id ? document.getElementById(id) : l.parentNode.querySelector('input, select, textarea');
      if (!c || c.offsetParent === null && !(c.closest('.gx-campo') || {}).offsetParent) continue;
      window.marcarRequerido(c, m[1].trim());
      try { c.focus(); } catch (e) { /* oculto por el combo */ }
      return true;
    }
    return false;
  }
  // Los mensajes que quedaron pendientes de la página anterior, por lo que les queda; los vencidos ya no salen.
  try {
    var ahora = Date.now();
    Object.keys(sessionStorage).filter(function (k) { return k.indexOf('gx-notif-') === 0; }).sort().forEach(function (k) {
      var d = JSON.parse(sessionStorage.getItem(k) || '{}');
      sessionStorage.removeItem(k);
      var vence = Number(d.vence) || 0;
      if (d.mensaje && vence > ahora) {
        window.notificar(d.mensaje, { tipo: d.tipo, titulo: d.titulo != null ? d.titulo : undefined, vence: vence });
      }
    });
  } catch (e) { /* sin storage */ }
  // Campos obligatorios (label.requerido, el asterisco rojo): se validan al enviar el formulario y ANTES de la
  // confirmación ("%1 es requerido."). Solo los visibles y habilitados.
  window.validarRequeridos = function (form) {
    var faltan = [];
    form.querySelectorAll('label.requerido').forEach(function (l) {
      var id = l.getAttribute('for');
      var c = id ? document.getElementById(id) : l.parentNode.querySelector('input, select, textarea');
      if (!c || !/^(INPUT|SELECT|TEXTAREA)$/.test(c.tagName) || /^(checkbox|radio|hidden)$/.test(c.type)) return;
      if (c.disabled || c.readOnly || c.offsetParent === null) return;
      var v = String(c.value == null ? '' : c.value).trim();
      // Un select con valor 0 cuenta como sin elegir (la opción SELECCIONAR).
      if (v === '' || (c.tagName === 'SELECT' && v === '0')) {
        faltan.push({ control: c, nombre: l.textContent.replace(/\*/g, '').replace(/\s+/g, ' ').trim() });
      }
    });
    faltan.forEach(function (f) { window.marcarRequerido(f.control, f.nombre); });
    if (faltan.length) {
      var c0 = faltan[0].control;
      ((c0.classList.contains('wwp-combo__select') && c0.parentNode.querySelector('.wwp-combo__input')) || c0).focus();
    }
    return faltan.length === 0;
  };

  // "%1 es requerido." en rojo debajo del campo (no un mensaje flotante). Se va al cargar el campo.
  window.marcarRequerido = function (control, nombre) {
    var campo = control.closest('.gx-campo') || control.parentNode;
    var msg = campo.querySelector('.gx-campo__requerido');
    if (!msg) {
      msg = document.createElement('span');
      msg.className = 'gx-campo__requerido';
      msg.setAttribute('role', 'alert');
      campo.appendChild(msg);
    }
    msg.textContent = nombre + ' es requerido.';
    // En un combo buscable (combo.js) el select está oculto: el borde rojo va en su campo de texto.
    var visible = (control.classList.contains('wwp-combo__select') && control.parentNode.querySelector('.wwp-combo__input')) || control;
    visible.classList.add('gx-campo--falta');
    var limpiar = function () {
      if (!String(control.value || '').trim()) return;
      visible.classList.remove('gx-campo--falta');
      if (msg.parentNode) msg.parentNode.removeChild(msg);
      control.removeEventListener('input', limpiar);
      control.removeEventListener('change', limpiar);
    };
    control.addEventListener('input', limpiar);
    control.addEventListener('change', limpiar);
  };
  // En captura: corre antes que el submit de cada pantalla (que es el que pide la confirmación).
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!form.querySelector || !form.querySelector('label.requerido')) return;
    if (!window.validarRequeridos(form)) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }, true);

  // Un ?error= en la URL (una pantalla que no se pudo abrir y volvió acá) sale como mensaje flotante, salvo que la
  // página ya lo muestre.
  try {
    var errUrl = new URLSearchParams(window.location.search).get('error');
    if (errUrl && !document.querySelector('#contenido .gx-aviso--error')) window.notificar(errUrl, { tipo: 'error' });
  } catch (e) { /* URL rara */ }
  // Un botón con data-bloqueo (por ejemplo Agregar sin permiso para dar de alta): al presionarlo avisa el motivo y
  // no hace nada más.
  document.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('[data-bloqueo]');
    if (!b) return;
    e.preventDefault();
    window.notificar(b.getAttribute('data-bloqueo'), { tipo: 'error', recordar: false });
  });
  // Los avisos de error y de éxito que arma el servidor también salen flotantes, y los que piden otro tipo con
  // data-notificar (una advertencia: "aviso"); estos la página los vuelve a armar en cada carga, así que no se recuerdan.
  document.querySelectorAll('#contenido .gx-aviso--error, #contenido .gx-aviso--exito, #contenido [data-notificar]').forEach(function (p) {
    var tipo = p.getAttribute('data-notificar');
    if (p.hidden && !tipo) return;
    window.notificar(p.textContent.replace(/\s+/g, ' ').trim(),
      tipo ? { tipo: tipo, recordar: false } : { tipo: p.classList.contains('gx-aviso--error') ? 'error' : 'exito' });
    p.hidden = true;
    p.style.display = 'none';
  });
  // El ?ok= con el que algunas pantallas vuelven a su lista ya se mostró: se saca de la dirección, así no queda a la
  // vista ni vuelve a salir al recargar.
  try {
    var dir = new URL(window.location.href);
    if (dir.searchParams.has('ok')) {
      dir.searchParams.delete('ok');
      window.history.replaceState(window.history.state, '', dir.pathname + dir.search + dir.hash);
    }
  } catch (e) { /* navegador sin URL */ }
  // Los botones son los mismos en todas las pantallas: "Sí" / "No" pasan a Confirmar / Cancelar; una acción con su
  // verbo (Liberar, Anular, Rechazar…) lo conserva.
  var TEXTOS_BOTON = { 'sí': 'Confirmar', 'si': 'Confirmar', 'aceptar': 'Confirmar', 'no': 'Cancelar' };
  function boton(texto) {
    var t = String(texto || '').trim();
    return TEXTOS_BOTON[t.toLowerCase()] || t || undefined;
  }
  window.confirmar = function (mensaje, op) {
    op = op || {};
    return abrir({ mensaje: mensaje, titulo: 'Confirmación', tipo: op.tipo || 'pregunta',
      confirmacion: true, textoConfirmar: boton(op.textoConfirmar), textoCancelar: boton(op.textoCancelar) });
  };

  // Botones de acciones que todavía no se pueden usar: se muestran deshabilitados y al tocarlos explican por qué en
  // vez de no hacer nada. El motivo concreto viene en el atributo data-solo-lectura.
  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-solo-lectura]');
    if (!b) return;
    e.preventDefault();
    e.stopPropagation();
    window.avisar(b.getAttribute('data-solo-lectura') ||
      'Esta acción todavía no está implementada en este módulo.', { titulo: 'No disponible', tipo: 'aviso' });
  }, true);

  // Pedido JSON al servidor con la cookie de sesión y, si el layout lo publica, el CSRF (meta _csrf / _csrf_header).
  // POST salvo que se indique otro método. Resuelve con la respuesta; si falla rechaza con un Error que trae el
  // mensaje para el usuario y, si el servidor lo mandó, codigo/datos de un Problem Details (RFC 7807).
  window.enviarJson = function (url, cuerpo, metodo) {
    var token = document.querySelector('meta[name="_csrf"]');
    var cabecera = document.querySelector('meta[name="_csrf_header"]');
    var h = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    if (token && cabecera) h[cabecera.content] = token.content;
    function falla(mensaje, d, status) {
      var e = new Error(mensaje);
      e.codigo = d && d.codigo;
      e.datos = d && d.datos;
      e.status = status;
      return e;
    }
    return fetch(url, { method: metodo || 'POST', headers: h, credentials: 'same-origin', body: JSON.stringify(cuerpo || {}) })
      .then(function (r) {
        var json = (r.headers.get('content-type') || '').indexOf('json') >= 0;
        // Con la sesión vencida, el servidor redirige al login: llega HTML, no JSON.
        if (r.redirected || !json) {
          throw falla('Tu sesión venció. Volvé a ingresar y repetí la operación.', null, r.status);
        }
        return r.json().then(function (d) {
          if (r.ok) return d;
          if (d.detail) throw falla(d.detail, d, r.status);
          throw falla(r.status === 403
            ? 'La página quedó desactualizada o tu sesión cambió. Actualizala y repetí la operación.'
            : 'El servidor respondió ' + r.status + '. No se hizo ningún cambio.', d, r.status);
        });
      }, function () {
        throw falla('No hay conexión con el servidor. No se hizo ningún cambio.', null, 0);
      });
  };

  /** Muestra el error de una acción. */
  window.errorAccion = function (e) {
    return window.avisar(e && e.message ? e.message : String(e), { titulo: 'ERROR', tipo: 'error' });
  };

  /**
   * La barra de paginación estándar de las grillas: "Página N de M" a la izquierda y
   * Ant · una ventana de 5 páginas · Sig a la derecha. ir(n) cambia a la página n (base 0). La usan todas las grillas
   * paginadas (paginarGrilla y las que se dibujan por JavaScript).
   */
  window.barraPaginacion = function (barra, paginas, n, ir) {
    barra.className = 'gx-paginacion';
    barra.innerHTML = '';
    var info = document.createElement('span');
    info.textContent = 'Página ' + (n + 1) + ' de ' + paginas;
    barra.appendChild(info);
    var btn = function (texto, destino, actual, deshabilitado) {
      var b = document.createElement('button');
      b.type = 'button';
      b.textContent = texto;
      if (actual) b.className = 'is-actual';
      b.disabled = !!deshabilitado;
      b.addEventListener('click', function () { ir(destino); });
      barra.appendChild(b);
    };
    var desde = Math.max(0, Math.min(n - 2, paginas - 5));
    var hasta = Math.min(paginas, desde + 5);
    btn('Ant', n - 1, false, n === 0);
    for (var i = desde; i < hasta; i++) btn(String(i + 1), i, i === n, false);
    btn('Sig', n + 1, false, n === paginas - 1);
  };

  /**
   * Pagina una grilla del servidor (table[data-paginar="N"], 10 por defecto) con la barra estándar debajo, siempre a la
   * vista mientras haya filas ("Página 1 de 1"). La grilla va en su marco (.gx-grilla-marco) y la barra
   * queda dentro, pegada a la grilla. Se puede volver a llamar si la grilla se rellena por JavaScript.
   */
  window.paginarGrilla = function (tabla) {
    var porPagina = Number(tabla.getAttribute('data-paginar')) || 10;
    var filas = Array.prototype.slice.call(tabla.tBodies[0].rows);
    if (tabla._barraPaginas) { tabla._barraPaginas.remove(); tabla._barraPaginas = null; }
    var paginas = Math.ceil(filas.length / porPagina);
    if (paginas < 1) return;
    var envoltura = tabla.closest('.gx-grilla-wrap') || tabla;
    if (!envoltura.parentNode.classList.contains('gx-grilla-marco')) {
      var marco = document.createElement('div');
      marco.className = 'gx-grilla-marco gx-grilla-marco--paginada';
      envoltura.parentNode.insertBefore(marco, envoltura);
      marco.appendChild(envoltura);
    }
    var barra = document.createElement('div');
    tabla._barraPaginas = barra;
    envoltura.parentNode.insertBefore(barra, envoltura.nextSibling);
    var mostrar = function (n) {
      filas.forEach(function (tr, i) { tr.hidden = Math.floor(i / porPagina) !== n; });
      window.barraPaginacion(barra, paginas, n, mostrar);
    };
    mostrar(0);
  };
  // Todas las grillas paginadas de la pantalla.
  document.querySelectorAll('#contenido table[data-paginar]').forEach(window.paginarGrilla);

  /** Deshabilita el botón mientras corre la promesa, para no mandar dos veces la misma operación. */
  window.mientras = function (boton, promesa) {
    if (boton) boton.disabled = true;
    return promesa.then(function (v) { if (boton) boton.disabled = false; return v; },
      function (e) { if (boton) boton.disabled = false; throw e; });
  };

  // Los modales de las pantallas (.app-modal-overlay[data-modal]) van al <body>, como los diálogos: dentro del
  // contenido quedan posicionados respecto de esa zona y el menú lateral los tapa.
  document.querySelectorAll('#contenido .app-modal-overlay[data-modal]').forEach(function (m) {
    document.body.appendChild(m);
    // Todo popup lleva la X arriba a la derecha para salir.
    var ventana = m.querySelector('.app-modal');
    if (ventana && !ventana.querySelector('.app-modal__cerrar')) {
      var x = document.createElement('button');
      x.type = 'button';
      x.className = 'app-modal__cerrar';
      x.setAttribute('data-cerrar-modal', '');
      x.setAttribute('aria-label', 'Cerrar');
      x.title = 'Cerrar';
      x.innerHTML = '<svg class="lucide" width="18" height="18" aria-hidden="true"><use href="#i-x"/></svg>';
      ventana.insertBefore(x, ventana.firstChild);
    }
    // Se mueve arrastrándolo con el mouse desde el título o cualquier zona libre del popup (bordes, franja superior,
    // pie); no desde los controles ni las filas de las grillas, que se usan con clic.
    if (ventana) {
      var titulo = ventana.querySelector('.gx-panel__titulo, h2, h3');
      if (titulo) titulo.classList.add('app-modal__asa');
      ventana.classList.add('app-modal--movible');
      var asa = ventana;
      var dx = 0, dy = 0;
      asa.addEventListener('pointerdown', function (e) {
        if (e.button !== 0) return;
        if (e.target.closest('button, input, select, textarea, a, label, tbody, tfoot, .gx-paginacion')) return;
        var x0 = e.clientX - dx, y0 = e.clientY - dy;
        var mover = function (ev) {
          dx = ev.clientX - x0;
          dy = ev.clientY - y0;
          ventana.style.transform = 'translate(' + dx + 'px,' + dy + 'px)';
        };
        var soltar = function () {
          document.removeEventListener('pointermove', mover);
          document.removeEventListener('pointerup', soltar);
          document.body.classList.remove('arrastrando-modal');
        };
        document.addEventListener('pointermove', mover);
        document.addEventListener('pointerup', soltar);
        document.body.classList.add('arrastrando-modal');
        e.preventDefault();
      });
      // Al volver a abrirlo arranca centrado.
      new MutationObserver(function () {
        if (!m.hidden) { dx = 0; dy = 0; ventana.style.transform = ''; }
      }).observe(m, { attributes: true, attributeFilter: ['hidden'] });
    }
  });

  // Esc cierra el popup abierto de más arriba (los diálogos de avisar/confirmar manejan su propio Esc).
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    var abiertos = [].slice.call(document.querySelectorAll('.app-modal-overlay[data-modal]')).filter(function (m) { return !m.hidden; });
    if (!abiertos.length || document.querySelector('.app-modal-overlay:not([data-modal])')) return;
    abiertos[abiertos.length - 1].hidden = true;
  });

  // Modales: data-abrir-modal="id" y data-cerrar-modal.
  document.addEventListener('click', function (e) {
    var a = e.target.closest('[data-abrir-modal]');
    if (a) { var m = document.getElementById(a.getAttribute('data-abrir-modal')); if (m) m.hidden = false; return; }
    var c = e.target.closest('[data-cerrar-modal]');
    if (c) { var o = c.closest('[data-modal]'); if (o) o.hidden = true; }
  });

  // Paneles plegables: un .gx-panel con data-plegable empieza plegado (data-plegable="abierto" empieza abierto) y un
  // clic (o Enter/Espacio) en el título pliega o despliega el cuerpo del panel.
  document.querySelectorAll('.gx-panel[data-plegable] > .gx-panel__titulo').forEach(function (t) {
    var cuerpo = t.parentNode.querySelector(':scope > .gx-panel__cuerpo');
    if (!cuerpo) return;
    var abierto = t.parentNode.getAttribute('data-plegable') === 'abierto';
    t.classList.add('gx-panel__titulo--plegable');
    t.setAttribute('role', 'button');
    t.setAttribute('tabindex', '0');
    t.setAttribute('aria-expanded', abierto ? 'true' : 'false');
    cuerpo.hidden = !abierto;
    t.title = 'Mostrar u ocultar';
    var alternar = function () {
      var abierto = t.getAttribute('aria-expanded') !== 'false';
      t.setAttribute('aria-expanded', abierto ? 'false' : 'true');
      cuerpo.hidden = abierto;
    };
    t.addEventListener('click', alternar);
    t.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); alternar(); }
    });
  });


  // ---------------------------------------------------------------- campo de importe
  // data-input-importe: agrupa con puntos y coma decimal al escribir; en el formulario viaja el valor crudo (en un
  // input oculto con el mismo name). Los decimales salen de data-decimales (2 por defecto; 0 = sin coma decimal).
  function formatear(raw) {
    if (raw === '' || raw == null) return '';
    var partes = String(raw).split('.');
    var entero = partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return partes.length > 1 ? entero + ',' + partes[1] : entero;
  }
  function crudo(texto, decimales) {
    var v = String(texto).replace(/\./g, '').replace(',', '.').replace(/[^\d.]/g, '');
    var partes = v.split('.');
    if (partes.length < 2) return v;
    return decimales === 0 ? partes[0] : partes[0] + '.' + partes.slice(1).join('').slice(0, decimales);
  }
  function decimalesDelCampo(inp) {
    var d = Number(inp.getAttribute('data-decimales'));
    return inp.hasAttribute('data-decimales') && !isNaN(d) ? d : 2;
  }
  /**
   * En un importe sin decimales la tecla coma no hace nada y avisa una vez: si solo se sacara la coma, los dígitos que
   * siguen se sumarían al entero (1500000,75 quedaría 150.000.075). Lo pegado con decimales se corta en la coma.
   */
  var avisoSinDecimales = 0;
  function sinComaDecimal(inp) {
    inp.addEventListener('beforeinput', function (e) {
      if (decimalesDelCampo(inp) !== 0 || !e.data || e.data.indexOf(',') < 0) return;
      if (e.inputType === 'insertFromPaste' || e.inputType === 'insertFromDrop') return;
      e.preventDefault();
      if (Date.now() - avisoSinDecimales > 4000) {
        avisoSinDecimales = Date.now();
        window.notificar('Este importe va sin decimales.', { tipo: 'aviso' });
      }
    });
    inp.addEventListener('input', function () {
      if (decimalesDelCampo(inp) !== 0) return;
      var i = inp.value.indexOf(',');
      if (i >= 0) inp.value = inp.value.slice(0, i);
    });
  }
  document.querySelectorAll('input[data-input-importe]').forEach(function (inp) {
    var oculto = document.createElement('input');
    oculto.type = 'hidden';
    oculto.name = inp.name;
    inp.removeAttribute('name');
    inp.after(oculto);
    var aplicar = function () {
      var dec = decimalesDelCampo(inp);
      inp.inputMode = dec === 0 ? 'numeric' : 'decimal';
      oculto.value = crudo(inp.value, dec);
      inp.value = formatear(oculto.value);
    };
    sinComaDecimal(inp);
    aplicar();
    inp.addEventListener('input', aplicar);
  });

  // ---------------------------------------------------------------- selección con clic en la fila
  // En las grillas que se marcan con un tilde (columna gx-col-check), un clic en cualquier parte de la fila hace lo
  // mismo que el tilde: dispara su click, así cada pantalla aplica su regla (selección única o varias). Botones,
  // enlaces, campos y el menú de acciones siguen igual.
  document.addEventListener('click', function (e) {
    var tr = e.target.closest('table.gx-grilla tbody tr');
    if (!tr || e.target.closest('a, button, input, select, textarea, label, .gx-accion-grupo, [contenteditable]')) return;
    if (window.getSelection && String(window.getSelection()).length) return; // seleccionando texto para copiar
    var marca = tr.querySelector('td.gx-col-check input[type="checkbox"]');
    if (!marca || marca.disabled) return;
    marca.click();
  });

  // ---------------------------------------------------------------- filtro de filas: data-filtrar-filas="id de la tabla"
  document.querySelectorAll('[data-filtrar-filas]').forEach(function (inp) {
    var tabla = document.getElementById(inp.getAttribute('data-filtrar-filas'));
    if (!tabla) return;
    inp.addEventListener('input', function () {
      var q = inp.value.trim().toLowerCase();
      tabla.querySelectorAll('tbody tr').forEach(function (tr) {
        tr.hidden = !!q && tr.textContent.toLowerCase().indexOf(q) < 0;
      });
    });
  });

  // ---------------------------------------------------------------- barra de progreso por pasos
  /**
   * La tarjeta de un proceso por pasos (gx-proceso): anillo con el porcentaje, título, subtítulo, reloj total, la barra
   * y la línea de tiempo de los pasos. Cada paso es un pedido al servidor (o la recarga), así que el avance es el real:
   * mientras un paso corre la barra se acerca de a poco al final de su tramo sin llegar, y salta al terminar; un paso
   * largo con partes (por ejemplo un aviso por registro) puede informar su fracción y un detalle de lo que va haciendo.
   *
   * ventana: el elemento con data-progreso-titulo, -pct, -arco, -reloj, -relleno, -pasos y el progressbar (sin ventana propia, se
   * arma una tarjeta en una ventana modal que bloquea la pantalla y se puede mover arrastrando su cabecera). op:
   * titulo, subtitulo, pasos (textos), pesos (la
   * parte de la barra de cada paso; por defecto iguales) y bloquear(si). Devuelve empezar, detallar, fraccion,
   * terminar, completar, fallar y ocultar.
   */
  /**
   * La ventana se arrastra desde asa (mouse, lápiz o dedo) y no se sale de la pantalla: queda corrida con un
   * translate, así el centrado del modal no cambia.
   */
  function moverConElMouse(ventana, asa) {
    if (!asa) return;
    asa.classList.add('is-arrastrable');
    asa.title = 'Arrastrar para mover';
    var dx = 0, dy = 0;
    asa.addEventListener('pointerdown', function (ev) {
      if (ev.button !== 0) return;
      ev.preventDefault();
      var x0 = ev.clientX - dx, y0 = ev.clientY - dy;
      var r = ventana.getBoundingClientRect();
      // Cuánto se puede correr sin que la ventana salga de la pantalla (8px de margen).
      var minX = dx - (r.left - 8), maxX = dx + (window.innerWidth - 8 - r.right);
      var minY = dy - (r.top - 8), maxY = dy + (window.innerHeight - 8 - r.bottom);
      asa.setPointerCapture(ev.pointerId);
      ventana.classList.add('is-arrastrando');
      var mover = function (e) {
        dx = Math.min(maxX, Math.max(minX, e.clientX - x0));
        dy = Math.min(maxY, Math.max(minY, e.clientY - y0));
        ventana.style.transform = 'translate(' + dx + 'px, ' + dy + 'px)';
      };
      var soltar = function () {
        ventana.classList.remove('is-arrastrando');
        asa.removeEventListener('pointermove', mover);
        asa.removeEventListener('pointerup', soltar);
        asa.removeEventListener('pointercancel', soltar);
      };
      asa.addEventListener('pointermove', mover);
      asa.addEventListener('pointerup', soltar);
      asa.addEventListener('pointercancel', soltar);
    });
  }

  window.progresoPorPasos = function (ventana, op) {
    op = op || {};
    var modal = null;
    if (!ventana) {
      modal = document.createElement('div');
      modal.className = 'gx-proceso-modal';
      modal.setAttribute('role', 'dialog');
      modal.setAttribute('aria-modal', 'true');
      modal.innerHTML = '<div class="gx-progreso gx-proceso" data-progreso>'
        + '<div class="gx-proceso__cabecera"><div class="gx-proceso__anillo" aria-hidden="true">'
        + '<svg viewBox="0 0 48 48"><circle class="gx-proceso__pista" cx="24" cy="24" r="20"/>'
        + '<circle class="gx-proceso__arco" data-progreso-arco cx="24" cy="24" r="20"/></svg>'
        + '<span class="gx-proceso__pct" data-progreso-pct>0%</span></div>'
        + '<div class="gx-proceso__titulos"><strong data-progreso-titulo></strong>'
        + '<span class="gx-proceso__sub" data-progreso-sub></span></div>'
        + '<span class="gx-proceso__reloj" data-progreso-reloj title="Tiempo total">0,0 s</span></div>'
        + '<div class="gx-progreso__barra gx-proceso__barra" role="progressbar" aria-valuemin="0" aria-valuemax="100"'
        + ' aria-valuenow="0"><div class="gx-progreso__relleno" data-progreso-relleno></div></div>'
        + '<ol class="gx-avance" data-progreso-pasos aria-live="polite"></ol></div>';
      document.body.appendChild(modal);
      ventana = modal.firstChild;
      moverConElMouse(ventana, ventana.querySelector('.gx-proceso__cabecera'));
    }
    var relleno = ventana.querySelector('[data-progreso-relleno]');
    var barra = ventana.querySelector('[role="progressbar"]');
    var pct = ventana.querySelector('[data-progreso-pct]');
    var arco = ventana.querySelector('[data-progreso-arco]');
    var relojTotal = ventana.querySelector('[data-progreso-reloj]');
    var titular = ventana.querySelector('[data-progreso-titulo]');
    var sub = ventana.querySelector('[data-progreso-sub]');
    var lista = ventana.querySelector('[data-progreso-pasos]');
    var VUELTA = 125.66;
    var bloquear = op.bloquear || function () {};
    var ICONOS = { 'is-hecho': '✓', 'is-aviso': '!', 'is-error': '✕' };
    var textos = op.pasos || [];
    // Dónde empieza y termina el tramo de cada paso (en %).
    var pesos = op.pesos && op.pesos.length === textos.length ? op.pesos : textos.map(function () { return 1; });
    var suma = pesos.reduce(function (a, b) { return a + b; }, 0) || 1;
    var bordes = [0];
    pesos.forEach(function (w, i) { bordes.push(bordes[i] + 100 * w / suma); });
    var pasos = textos.map(function (t) {
      var li = document.createElement('li');
      li.className = 'gx-avance__paso';
      li.innerHTML = '<span class="gx-avance__icono" aria-hidden="true"></span>'
        + '<span class="gx-avance__texto"><span data-paso-texto></span><span class="gx-avance__detalle" hidden></span></span>'
        + '<span class="gx-avance__tiempo"></span>';
      li.querySelector('[data-paso-texto]').textContent = t;
      return { li: li, inicio: 0 };
    });
    var actual = -1, valor = 0, reloj = null, inicioTotal = Date.now(), relojTotalId = null, piso = 0, techo = 0;
    var poner = function (v) {
      valor = Math.max(valor, Math.min(100, v));
      var n = Math.round(valor);
      relleno.style.width = valor + '%';
      barra.setAttribute('aria-valuenow', n);
      pct.textContent = n + '%';
      if (arco) arco.style.strokeDashoffset = String(VUELTA * (1 - valor / 100));
    };
    var desde = function (t) { return ((Date.now() - t) / 1000).toFixed(1).replace('.', ',') + ' s'; };
    var pararTotal = function () { if (relojTotalId) clearInterval(relojTotalId); relojTotalId = null; };
    var detalle = function (i, texto) {
      var d = pasos[i].li.querySelector('.gx-avance__detalle');
      d.hidden = !texto;
      d.textContent = texto || '';
    };
    var marcar = function (i, clase, texto) {
      var p = pasos[i];
      p.li.className = 'gx-avance__paso ' + clase;
      // Pendiente o en curso: su número; terminado: el ícono del estado.
      p.li.querySelector('.gx-avance__icono').textContent = ICONOS[clase] || String(i + 1);
      if (p.inicio) p.li.querySelector('.gx-avance__tiempo').textContent = desde(p.inicio);
      detalle(i, texto);
    };
    var detener = function () { if (reloj) clearInterval(reloj); reloj = null; };
    lista.innerHTML = '';
    pasos.forEach(function (p, i) { lista.appendChild(p.li); marcar(i, ''); });
    titular.textContent = op.titulo || '';
    if (sub && op.subtitulo != null) sub.textContent = op.subtitulo;
    ventana.classList.remove('is-indeterminado', 'is-error', 'is-completo');
    ventana.classList.add('is-trabajando');
    relleno.style.transition = 'none';
    if (arco) arco.style.transition = 'none';
    poner(0);
    void relleno.offsetWidth;
    relleno.style.transition = '';
    if (arco) arco.style.transition = '';
    if (relojTotal) {
      relojTotal.textContent = '0,0 s';
      relojTotalId = setInterval(function () { relojTotal.textContent = desde(inicioTotal); }, 100);
    }
    ventana.hidden = false;
    bloquear(true);
    var p = {
      /** Empieza el paso i (termina bien el anterior si seguía en curso). */
      empezar: function (i) {
        if (actual >= 0 && actual !== i && pasos[actual].li.classList.contains('is-curso')) p.terminar(actual);
        actual = i;
        pasos[i].inicio = Date.now();
        marcar(i, 'is-curso');
        piso = bordes[i];
        techo = bordes[i + 1];
        poner(piso);
        detener();
        reloj = setInterval(function () {
          // Se acerca al 90% de lo que falta del tramo sin llegar: el final lo pone la respuesta del servidor.
          poner(valor + (piso + (techo - piso) * 0.9 - valor) * 0.08);
          pasos[i].li.querySelector('.gx-avance__tiempo').textContent = desde(pasos[i].inicio);
        }, 200);
      },
      /** El paso en curso cuenta lo que está haciendo (debajo de su texto). */
      detallar: function (i, texto) { detalle(i, texto); },
      /** El paso i ya hizo la fracción f (0 a 1) de su trabajo: la barra no baja de ahí. */
      fraccion: function (i, f) {
        var a = bordes[i], b = bordes[i + 1];
        var nuevo = a + (b - a) * Math.max(0, Math.min(1, f));
        if (i === actual) piso = Math.max(piso, nuevo);
        poner(nuevo);
      },
      /** Termina el paso i: bien o con aviso ('is-aviso'), con un detalle opcional. */
      terminar: function (i, clase, texto) {
        if (i === actual) detener();
        marcar(i, clase || 'is-hecho', texto);
        poner(bordes[i + 1]);
      },
      /** Todo lo del servidor terminó: la tarjeta en verde con su título (puede quedar en curso la recarga). */
      completar: function (texto) {
        poner(100);
        pararTotal();
        ventana.classList.remove('is-trabajando');
        ventana.classList.add('is-completo');
        titular.textContent = texto;
      },
      /** El paso i falló: la tarjeta en rojo; ocultar() la saca y desbloquea. */
      fallar: function (i, texto) {
        detener();
        pararTotal();
        marcar(i, 'is-error', texto);
        ventana.classList.remove('is-trabajando');
        ventana.classList.add('is-error');
        titular.textContent = 'NO SE PUDO COMPLETAR';
      },
      ocultar: function () {
        detener();
        pararTotal();
        if (modal) { if (modal.parentNode) modal.parentNode.removeChild(modal); } else ventana.hidden = true;
        bloquear(false);
      }
    };
    return p;
  };
})();
