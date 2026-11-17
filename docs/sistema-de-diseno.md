# Sistema de diseño

Referencia de las variables, los componentes y las funciones JavaScript de la plantilla. Para verlo funcionando, abrí `/componentes`.

## 1. Variables de diseño (`css/tokens/tokens.css`)

### Colores

| Token | Valor | Uso |
|---|---|---|
| `--wwp-base` | `#001A81` | Azul principal: títulos de panel, botones, bordes, acentos |
| `--wwp-base-rgb` | `0, 26, 129` | El mismo azul para `rgba(var(--wwp-base-rgb), .08)` |
| `--wwp-action-focus` | `#000168` | Foco de inputs y botones |
| `--wwp-action-hover` / `--wwp-action-hover-bd` | `#001077` / `#00066D` | Hover de botones |
| `--wwp-base-30` | `rgba(0,26,129,.3)` | Selección de texto, bordes suaves |
| `--wwp-text` | `#2b2f33` | Texto general |
| `--wwp-required` | `#EA0101` | Asterisco y error de campos obligatorios |
| `--wwp-bg` / `--wwp-app-bg` | `#EEEEEE` / `#899eb3` | Fondo base / fondo detrás de las pantallas |
| `--wwp-data-bg` / `--wwp-data-bg-soft` | `#FFFFFF` / `#FAFAFA` | Tarjetas y contenido |
| `--wwp-gray01` a `--wwp-gray08` | `#fbfbfb` a `#575b5d` | Bordes, líneas y texto secundario |
| `--wwp-grid-title` | `#00066D` | Encabezados de grillas genéricas |
| `--wwp-danger` · `--wwp-warning` · `--wwp-success` · `--wwp-info` | `#f56a67` · `#f39c12` · `#00a65a` · `#3c8dbc` | Estados |
| `--wwp-line` | `#e6e8eb` | Borde sutil de tarjetas |

Los componentes de suite (KPI y badges) usan su propia paleta de cinco tonos: `css/tokens/suite-tokens.css` (`--suite-blue`, `-green`, `-yellow`, `-red`, `-gray`, cada uno con `-soft` y `-border`).

### Tipografía

- **Fuente:** Poppins (400 y 600/700), local en `static/fonts/`. Tokens: `--wwp-font` y `--wwp-font-bold`.
- **Escala:** `--wwp-fs-xs` 11 · `--wwp-fs-s` 14 (base) · `--wwp-fs-m` 18 · `--wwp-fs-l` 22 · `--wwp-fs-xl` 28 · `--wwp-fs-xxl` 34 · `--wwp-fs-title` 30.
- **Jerarquía en pantalla:**
  - título de la pantalla: 22 px;
  - descripción: 11 px;
  - título de panel: 14 px, Poppins 600, blanco sobre azul;
  - etiqueta de campo, valores, botones y grillas: 14 px;
  - leyenda de grupo: 18 px;
  - etiquetas de estado: 11 px.

### Formas y sombras

| Token | Valor |
|---|---|
| `--wwp-radius-s` / `-m` / `-l` | 2 / 4 / 6 px |
| `--wwp-radius-card` | 12 px (paneles y tarjetas) |
| `--wwp-border-xs` / `-s` | 1 / 2 px |
| `--wwp-shadow-card` | sombra suave de tarjeta |
| `--wwp-shadow-hover` | sombra al pasar el mouse |
| `--wwp-shadow-btn` | sombra azul del botón primario en hover |
| `--wwp-header-h` | 101 px (alto de referencia de la cabecera) |

## 2. Layout (`templates/layout/layout.html`)

- **Menú lateral** (`.admin-sidebar`):
  - franja de marca azul con logo o inicial, nombre y subtítulo;
  - buscador de pantallas;
  - acceso rápido a Inicio;
  - módulos en acordeón (`.admin-module`) con secciones de rótulo azul plegables (`.admin-subsection__label`) y opciones (`.admin-nav-item`).
  - Se colapsa a solo íconos (el estado se recuerda en `localStorage`).
- **Cabecera** (`.admin-header`): el título y la descripción del `header.gx-encabezado` de cada pantalla pasan a la barra superior (`shell.js`). El bloque de usuario aparece si el modelo trae `usuario`.
- **Contenido** (`main.admin-content > #contenido`): la raíz de cada pantalla.
- **Responsive:** por debajo de 960 px el menú pasa a ser un panel que se abre con el botón hamburguesa, con fondo oscurecido. Las grillas de campos pasan a 2 columnas o a 1 por debajo de 900 px, y los grupos a 1 columna.

## 3. Componentes de pantalla (`css/componentes/pantalla.css`)

### Estructura

| Clase | Qué es |
|---|---|
| `.gx-pantalla` | Raíz de la pantalla (`#contenido`): columna con 20 px entre bloques, cuerpo centrado en 1280 px |
| `.gx-pantalla--ancha` / `--angosta` / `--ficha` | Hasta 1500 px / centrado en 900 px / ficha centrada |
| `.gx-encabezado` | Título (`h1` con ícono) y descripción (`p`) |
| `.gx-panel` | Tarjeta blanca, radio 12, sombra |
| `.gx-panel__titulo` · `.gx-panel__cuerpo` | Barra azul con texto blanco · cuerpo con padding 16 |
| `.gx-panel--grilla` | Panel sin padding para una grilla |
| `.gx-panel--compacto` | Panel del ancho de su contenido |
| `.gx-panel[data-plegable]` | El título pliega el cuerpo (`="abierto"` arranca abierto) |
| `.gx-contenedor` (`--ancho`) | Contenedor blanco que agrupa paneles y botones |
| `.gx-ficha-seccion` (`--grupo`) · `.gx-ficha-seccion__titulo` | Secciones de una ficha |

### Formularios

| Clase | Qué es |
|---|---|
| `.gx-campos` | Grilla de campos de 2 columnas (`--3`, `--4`, `--filtros` = 4, `--ficha`) |
| `.gx-campo` | Etiqueta arriba y control con línea inferior (máx. 320 px) |
| `label.requerido` | Asterisco rojo y validación al enviar ("X es requerido.") |
| `.gx-campo--ancho` / `--completo` / `--doble` | Ocupa toda la fila o dos columnas |
| `.gx-campo__lupa` | Input con botón de ícono a la derecha |
| `.gx-campo__con-boton` | Input con `.gx-accion` a la derecha |
| `.gx-check` | Checkbox en línea con su texto |
| `.gx-grupos > fieldset.gx-grupo > legend` | Grupos de campos con borde azul y sombra, dos por fila |
| `.gx-mayusculas` | Muestra el texto en mayúsculas |
| `input[data-input-importe]` | Importe con separador de miles; `data-decimales` (2 por defecto) |

**Estados:** `:focus` pone la línea azul. `:disabled` la pone punteada con texto gris. Para mostrar un error, `.gx-campo.is-error` con un `.gx-campo__error`.

### Botones

| Clase | Qué es |
|---|---|
| `.gx-btn` | Primario azul; hover más oscuro; deshabilitado gris (en todo el sistema) |
| `.gx-btn--secundario` | Blanco con texto azul |
| `.gx-btn--peligro` | Rojo, para acciones destructivas |
| `.gx-btn--icono` | Con ícono a la izquierda |
| `.gx-btn--fila` | Botón bajo dentro de una fila de grilla |
| `.gx-acciones-pie` · `.gx-botonera` (`--derecha`) | Fila de botones |
| `.gx-enlace` | Botón con apariencia de enlace |
| `[data-solo-lectura="motivo"]` | Botón no disponible: al tocarlo, avisa el motivo |
| `[data-bloqueo="motivo"]` | Al tocarlo muestra el motivo como error y no hace nada |

También existen los botones de la base (`.sb-btn` en `boton.css`) y los de los modales (`.app-btn`, `--primary`, `--danger`).

### Grillas

| Clase / atributo | Qué es |
|---|---|
| `.gx-grilla-wrap > table.gx-grilla` | Grilla con raya azul bajo el encabezado y desplazamiento horizontal si no entra |
| `table[data-paginar="N"]` | Paginación de N filas: "Página X de Y", Ant · 1 2 3 · Sig |
| `.gx-num` | Columna numérica alineada a la derecha |
| `.gx-col-acciones` + `.gx-accion-grupo` | Botón hamburguesa con el menú de acciones de la fila |
| `.gx-col-check` | Casilla de selección (tilde azul); un clic en la fila la marca |
| `.gx-grilla--compacta` / `--bordes` / `--seleccion` | Variantes |
| `.gx-grilla-marco` | Grilla enmarcada |
| `.gx-grilla__grupo` | Título de grupo de columnas |
| `.gx-grilla__vacio` | Texto "No hay registros." |
| `tr.gx-fila--peligro` / `--roja` / `--nueva` / `.is-seleccionada` / `.gx-fila-elegible` | Estados de fila |
| `input[data-filtrar-filas="idTabla"]` | Filtra las filas por texto mientras se escribe |
| `.gx-cargando` | Capa "cargando" sobre una grilla, con `.gx-cargando__ruedita` y `__texto` |

Las tablas con clase `*-table` o `.grid` tienen un estilo genérico propio (cabecera fija) en `tema.css`.

### Avisos, etiquetas e indicadores

| Clase | Qué es |
|---|---|
| `.gx-aviso` (`--exito`, `--error`, `--alerta`, `--info`) | Aviso en la pantalla. Los de éxito y error salen además como mensaje flotante |
| `.gx-explicacion` | Recuadro informativo con ícono y borde azul a la izquierda |
| `.gx-etiqueta` (`--exito`, `--peligro`, `--aviso`, `--info`) | Etiqueta de estado redondeada |
| `.gx-badge` | Insignia azul junto a un texto |
| `.suite-badge--blue/green/yellow/red/gray` | Badge de suite (fragmento `badge`) |
| `.suite-kpi--*` | Tarjeta de indicador (fragmentos `kpi` y `kpiLink`) |
| `.gx-kpis > a.gx-kpi--aviso/info/exito/peligro` | Resumen por estado que filtra (`.is-activo`) |
| `.gx-pasos > li.is-hecho/.is-actual/.is-rechazado` | Pasos de un asistente |
| `.gx-progreso` (`.is-indeterminado`) | Barra de progreso |
| `.gx-monto-cabecera` | Cabecera de ficha con un monto destacado |
| `.app-loader` | Indicador de carga (fragmento `loader`) |

## 4. Modales y diálogos

- **Modal** (`componentes/modales.css`):

  ```html
  <div class="app-modal-overlay" id="modal-x" data-modal hidden>
    <div class="app-modal"> … <button data-cerrar-modal>Cancelar</button></div>
  </div>
  <button data-abrir-modal="modal-x">Abrir</button>
  ```

  Va al `<body>`, agrega la X arriba a la derecha, se mueve arrastrando y se cierra con Escape. `.app-modal--editor` es la versión ancha.
- **Diálogo de confirmación** (`componentes/dialogos.css`): `window.confirmar(mensaje, { tipo, textoConfirmar, textoCancelar })` devuelve una promesa con `true` o `false`. Los tipos son `pregunta` (azul), `aviso` (ámbar) y `error` (rojo). El foco arranca en Cancelar, Enter activa el botón con foco y un clic afuera sacude el diálogo.
- **Aviso** (un botón): `window.avisar(mensaje, { titulo, tipo })`.
- **Mensajes flotantes:** `window.notificar(mensaje, { tipo, titulo })`. Salen arriba a la derecha y se cierran solos a los 8 segundos. Si la página navega antes, reaparecen en la siguiente.

## 5. Funciones JavaScript (`js/componentes/componentes.js`)

| Función | Qué hace |
|---|---|
| `confirmar`, `avisar`, `notificar` | Ver la sección 4 |
| `validarRequeridos(form)` / `marcarRequerido(control, nombre)` | Validación de `label.requerido` (automática al enviar) |
| `enviarJson(url, cuerpo, metodo)` | `fetch` JSON con CSRF; rechaza con un `Error` con el mensaje para el usuario |
| `errorAccion(e)` | Muestra el error de `enviarJson` |
| `mientras(boton, promesa)` | Deshabilita el botón mientras corre la promesa |
| `paginarGrilla(tabla)` / `barraPaginacion(barra, paginas, n, ir)` | Paginación (automática con `data-paginar`) |
| `progresoPorPasos(null, { titulo, subtitulo, pasos })` | Ventana de proceso con anillo de porcentaje y pasos: `empezar(i)`, `terminar(i)`, `fraccion(i, f)`, `fallar(i, texto)`, `completar(texto)`, `ocultar()` |

Otros scripts:

- `combo.js` convierte los `<select>` en combos con búsqueda.
- `grilla.js` abre y cierra el menú de acciones de cada fila.
- `layout/shell.js` maneja el menú, la búsqueda, el modo móvil y el encabezado.

## 6. Iconografía

Íconos [Lucide](https://lucide.dev) (licencia ISC) como sprite en `templates/componentes/iconos.html`. El layout lo incluye una vez por página.

```html
<svg class="lucide" width="16" height="16"><use href="#i-search"/></svg>
<!-- o con el fragmento -->
<th:block th:replace="~{componentes/componentes :: icono('search', 16)}"/>
```

Criterios:

| Tamaño | Dónde |
|---|---|
| 13–15 px | Menús y etiquetas |
| 16 px | Botones y avisos |
| 17–18 px | Módulos del menú y diálogos |
| 20 px | Título de la pantalla |

Los íconos heredan el color del texto (`currentColor`). Para agregar un ícono, copiá su `<symbol>` de Lucide con el id `i-<nombre>`.

## 7. Fragmentos Thymeleaf (`templates/componentes/componentes.html`)

| Fragmento | Uso |
|---|---|
| `icono(nombre, tam)` | Ícono del sprite |
| `badge(texto, tono)` | `suite-badge` (tono: blue, green, yellow, red, gray) |
| `kpi(etiqueta, valor, desc, tono, icono)` | Tarjeta de indicador |
| `kpiLink(etiqueta, valor, desc, tono, icono, href, titulo)` | Tarjeta de indicador que navega |
| `loader(texto)` | Indicador de carga compacto |
