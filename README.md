# LEX NOVA (Estudio-Juridico)

Proyecto basado en la plantilla frontend (layout, sistema de diseño y componentes).

## Plantilla frontend

Plantilla base para construir sistemas web con pantallas del lado del servidor. Trae el layout (menú lateral, cabecera y área de contenido), el sistema de diseño (tokens, tipografía, colores, formas) y los componentes visuales listos para usar. No tiene pantallas de negocio: el inicio está vacío.

**Tecnología:** Java 11 · Spring Boot 2.7 · Thymeleaf · CSS y JavaScript sin frameworks ni compilación.

## Ejecutar

Requisitos: JDK 11 o superior, Maven 3.8 o superior y SQL Server 2019 o superior.

### Base de datos

1. Crear la base vacía, con la intercalación en español (búsquedas sin distinguir mayúsculas ni acentos):

   ```sql
   CREATE DATABASE EstudioJuridico COLLATE Modern_Spanish_CI_AI;
   ```

2. Definir la conexión en variables de entorno (nunca en el repositorio). En PowerShell:

   ```powershell
   $env:DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=EstudioJuridico;encrypt=true;trustServerCertificate=true"
   $env:DB_USER = "usuario_sql"
   $env:DB_PASSWORD = "clave"
   ```

   SQL Server debe aceptar conexiones TCP/IP (puerto 1433) y autenticación de SQL Server.

3. Al arrancar, Flyway crea las tablas, las vistas y los datos iniciales con los scripts de `src/main/resources/db/migration`. Un cambio de esquema se hace con un script nuevo (`V8__descripcion.sql`); los ya aplicados no se tocan.

### Primer ingreso

En el primer arranque, si la base no tiene usuarios, se crea el usuario **`admin`** (rol Administrador) con la clave de la variable de entorno `APP_ADMIN_CLAVE`. Si no está definida, se genera una al azar y se muestra una sola vez en el log. El sistema pide cambiarla en el primer ingreso. Después, los demás usuarios se crean desde **Administración → Seguridad → Usuarios**.

- La contraseña debe tener al menos 8 caracteres, con letras y números, y no contener el nombre de usuario.
- Tras 5 intentos fallidos el usuario queda bloqueado 15 minutos (parámetros `seguridad.max_intentos` y `seguridad.minutos_bloqueo`); un administrador puede desbloquearlo antes.
- Un administrador puede reiniciar la clave de cualquier usuario; el usuario deberá cambiarla al ingresar.

### Pruebas

`mvn test` usa la base de `DB_URL` (con las migraciones aplicadas); cada prueba deshace sus cambios. Sin `DB_URL` las pruebas se saltean.

### Comandos

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
| `/login` | Ingreso con usuario y contraseña |
| `/` | Inicio |
| `/cambiar-clave` | Cambio de la propia contraseña (también desde el menú del usuario, arriba a la derecha) |
| `/admin/usuarios` | Usuarios: lista con filtros y ABM (solo administradores) |
| `/componentes` | Catálogo de todos los componentes, con datos de ejemplo (solo administradores) |

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
    └── img/                       favicon.png, lex-nova-logo.png
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

- **Pantallas de negocio:** se construyen de a una (ver el plan en [docs/modelo-de-datos.md](docs/modelo-de-datos.md)); las opciones del menú que faltan muestran "en construcción".
- **Modo oscuro:** la base tiene las variables, pero el tema fija el modo claro (`tema.js`).

## Licencias de terceros

- **Poppins** (`static/fonts/`): SIL Open Font License 1.1.
- **Íconos Lucide** (`templates/componentes/iconos.html`): licencia ISC.
