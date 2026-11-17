# Estudio-Juridico

Proyecto basado en la plantilla frontend (layout, sistema de diseño y componentes).

## Plantilla frontend

Plantilla base para construir sistemas web con pantallas del lado del servidor. Trae el layout (menú lateral, cabecera y área de contenido), el sistema de diseño (tokens, tipografía, colores, formas) y los componentes visuales listos para usar. No tiene pantallas de negocio: el inicio está vacío.

**Tecnología:** Java 11 · Spring Boot 2.7 · Thymeleaf · CSS y JavaScript sin frameworks ni compilación.

## Ejecutar

Requisitos: JDK 11 o superior y Maven 3.8 o superior.

```bash
# Desarrollo
mvn spring-boot:run
# → http://localhost:8080

# Pruebas y empaquetado
mvn package
# → target/ROOT.war

# Ejecutar el WAR
java -jar target/ROOT.war
```

El mismo `ROOT.war` también se puede copiar a la carpeta `webapps/` de un Tomcat 9. Se publica en `/`.

| Ruta | Qué muestra |
|---|---|
| `/` | Inicio: el layout con el contenido vacío |
| `/componentes` | Catálogo de todos los componentes, con datos de ejemplo |
| `/login` | Pantalla de acceso (solo visual, sin autenticación) |

## Estructura

```
src/main/java/com/ejemplo/plantilla/
├── PlantillaApplication.java      arranque
├── ServletInitializer.java        arranque en un Tomcat externo
├── config/AppProperties.java      nombre, subtítulo y logo (app.* en application.yml)
└── web/
    ├── ModeloComun.java           modelo común del layout (marca, menú, pantalla actual)
    ├── PantallasController.java   rutas de la plantilla
    └── menu/                      definición del menú lateral (MenuAplicacion)

src/main/resources/
├── application.yml
├── templates/
│   ├── layout/                    layout.html (layout principal), modulo-menu.html
│   ├── componentes/               componentes.html (fragmentos), iconos.html (sprite SVG)
│   ├── pantallas/                 inicio.html, catalogo.html, login.html
│   └── error.html                 página de error
└── static/
    ├── css/
    │   ├── tokens/                variables de diseño (paleta, tipografía, formas)
    │   ├── base/                  reset, controles base y ajustes globales
    │   ├── layout/                menú lateral, cabecera, área de contenido
    │   ├── componentes/           pantalla, botones, modales, diálogos, suite, cargador, login
    │   └── tema/                  capa de marca: impone el look sobre todo lo anterior
    ├── js/
    │   ├── layout/                shell.js (menú, búsqueda, móvil), tema.js
    │   └── componentes/           componentes.js, combo.js, grilla.js
    ├── fonts/                     Poppins (licencia SIL OFL)
    └── img/                       favicon.svg
```

Orden de carga del CSS (en `layout.html`): base → layout → componentes → CSS propio de la pantalla → tokens → tema. El tema va al final para que mande a igual especificidad.

## Agregar una pantalla

1. **Plantilla** `templates/pantallas/clientes.html`:

   ```html
   <!doctype html>
   <html lang="es" xmlns:th="http://www.thymeleaf.org"
         th:replace="~{layout/layout :: layout(~{::title}, ~{::#contenido}, ~{::estilos})}">
   <head>
     <title>Clientes</title>
     <th:block th:fragment="estilos"></th:block>
   </head>
   <body>
   <div id="contenido" class="gx-pantalla">
     <header class="gx-encabezado">
       <h1><svg class="lucide" width="20" height="20"><use href="#i-users"/></svg>Clientes</h1>
       <p>Descripción breve de la pantalla</p>
     </header>
     <section class="gx-panel">
       <h2 class="gx-panel__titulo">Datos</h2>
       <div class="gx-panel__cuerpo">…</div>
     </section>
   </div>
   </body>
   </html>
   ```

   Si la pantalla necesita CSS propio, va dentro del bloque `estilos`, por ejemplo `<link rel="stylesheet" th:href="@{/css/pantallas/clientes.css}">`.

2. **Ruta** en un controlador:

   ```java
   @GetMapping("/clientes")
   public String clientes(Model model) {
       modelo.pantalla(model, "clientes", "Clientes");   // opción activa del menú y título de la cabecera
       return "pantallas/clientes";
   }
   ```

3. **Menú** en `MenuAplicacion`:

   ```java
   new ModuloMenu("gestion", "Gestión", "users", List.of(
       new SeccionMenu("Maestros", List.of(new ItemMenu("clientes", "Clientes")))))
   ```

   El ícono es el id del sprite sin el prefijo `i-`. Con varias secciones, cada una tiene un rótulo plegable.

## Reutilizar los componentes

Abrí `/componentes`: cada bloque del catálogo es HTML listo para copiar desde [catalogo.html](src/main/resources/templates/pantallas/catalogo.html). La referencia completa de clases, tokens y funciones JavaScript está en [docs/sistema-de-diseno.md](docs/sistema-de-diseno.md).

Resumen:

| Necesito | Uso |
|---|---|
| Panel con título azul | `section.gx-panel > h2.gx-panel__titulo + div.gx-panel__cuerpo` |
| Formulario | `div.gx-campos > div.gx-campo > label + input` (`label.requerido` agrega el asterisco y la validación) |
| Botones | `.gx-btn`, `.gx-btn--secundario`, `.gx-btn--peligro`, `.gx-btn--icono` |
| Grilla | `table.gx-grilla` dentro de `.gx-grilla-wrap`; `data-paginar="10"` agrega la paginación |
| Acciones por fila | `.gx-accion-grupo` con `.gx-accion-grupo__boton` y `.gx-accion-grupo__menu` |
| Combo con búsqueda | automático en todo `<select>` (se excluye con `data-combo="no"`) |
| Modal | `.app-modal-overlay[data-modal][hidden] > .app-modal`; se abre con `data-abrir-modal="id"` |
| Confirmación | `window.confirmar('¿Confirma?').then(si => …)` |
| Mensaje flotante | `window.notificar('Guardado', { tipo: 'exito' })` (tipos: `exito`, `aviso`, `error`, `info`) |
| Íconos | `<svg class="lucide" width="16" height="16"><use href="#i-search"/></svg>` |

## Personalizar la marca

- **Nombre, subtítulo y logo:** `app.*` en `application.yml`. El logo va en `static/img/`, con `app.logo: /img/logo.png`.
- **Colores, tipografía, radios y sombras:** `static/css/tokens/tokens.css` (`--wwp-base` es el azul principal) y los `--brand-*` de `static/css/base/base.css`.
- **Color de acento de los módulos del menú:** `ModeloComun.ACENTO`.
- **Fondo detrás de las pantallas:** `--wwp-app-bg` en `tokens.css`. Para usar una imagen, ver el comentario en `tema/tema.css`.

## Qué no incluye

- **Autenticación:** `/login` es solo visual. El layout ya está preparado. Muestra el bloque de usuario si el modelo trae `usuario` (y opcionalmente `usuarioNombre` y `usuarioDetalle`), y `componentes.js` manda el CSRF si el layout publica las metas `_csrf`.
- **Base de datos y servicios externos.**
- **Modo oscuro:** la base tiene las variables, pero el tema fija el modo claro (`tema.js`).

## Licencias de terceros

- **Poppins** (`static/fonts/`): SIL Open Font License 1.1.
- **Íconos Lucide** (`templates/componentes/iconos.html`): licencia ISC.
