# LEX NOVA (Estudio-Juridico)

**LEX NOVA**: sistema de gestión de casos jurídicos para un abogado y su equipo (Paraguay): clientes, expedientes, agenda legal con avisos de vencimiento, control documental y reportes. Más adelante, un asistente legal con IA.

Identidad visual: estilo GeneXus 18 / WorkWithPlus, barra lateral grafito, área gris claro, dorado solo como acento. Paleta, emblema y fondo en [docs/sistema-de-diseno.md](docs/sistema-de-diseno.md) (sección 0); capa de marca en `static/css/tema/lex-nova.css`. No usar estilos de landing page ni de dashboard moderno.

## Estado actual

- Base: la plantilla frontend (layout, sistema de diseño y componentes), sin pantallas de negocio todavía.
- Base de datos: **SQL Server** (local: SQL Server 2022 Developer, base `EstudioJuridico`, intercalación `Modern_Spanish_CI_AI`). El diseño está en [docs/modelo-de-datos.md](docs/modelo-de-datos.md) y el esquema en las migraciones Flyway de `src/main/resources/db/migration` (`V1` a `V7`: 31 tablas, 4 vistas, roles, permisos, menú y catálogos).
- Hecho: ingreso real (Spring Security), cambio de clave obligatorio, menú desde la tabla `menu` según permisos, y las pantallas **Usuarios** (`/admin/usuarios`), **Roles y permisos** (`/admin/roles`; el rol Administrador conserva siempre todos los permisos y no se da de baja), **Parámetros** (`/admin/parametros`: solo se modifica el valor; la regla de cada clave está en `ParametrosServicio#validar`, y un parámetro nuevo va por migración con su regla), **Catálogos** (`/admin/materias|juzgados|estados|tipos`: un ABM genérico en el paquete `catalogos`; cada catálogo se describe en `Secciones` con sus campos y reglas, y sumar uno es crear la tabla y describirlo ahí) y **Clientes** (`/clientes`, con contactos e historial de casos). Las demás opciones del menú muestran "en construcción" (`PantallasController#pendiente`): al construir una pantalla, sacar su ruta de esa lista.
- Orden acordado para las próximas pantallas: la configuración ya está completa (incluida **Auditoría**, `/admin/auditoria`, solo consulta); **Expedientes** hecho (`/expedientes`: ficha con partes, bitácora e historial; cerrado = solo lectura); sigue Agenda → Vencimientos y avisos → Documentos → Reporte de casos → Reporte de agenda.
- En los controladores no usar el atributo de modelo `modulos`: lo usa el layout para el menú.
- `@RequestParam(defaultValue=…)` también se aplica cuando el parámetro llega vacío: en los filtros, la opción "todos" lleva un valor explícito (`TODOS`), nunca `value=""`.
- Resultado de una acción: redirigir con `?ok=` o `?error=`; el fragmento `componentes :: ok` muestra los dos.
- Las migraciones ya aplicadas no se modifican: un cambio de esquema es un script nuevo (`V8__...sql`). Escribirlas en T-SQL, una vista por lote separada con `GO`.

## Stack y comandos

- Java 11, Spring Boot 2.7, Thymeleaf. CSS y JS sin frameworks ni build de frontend.
- `mvn spring-boot:run` → http://localhost:8080 · `mvn package` → `target/ROOT.war` (pruebas incluidas).
- Para arrancar hacen falta `DB_URL`, `DB_USER` y `DB_PASSWORD` (ver README), y `APP_ADMIN_CLAVE` para el primer arranque (clave inicial del usuario `admin`). Flyway aplica las migraciones al arrancar.
- Las pruebas usan la base real de `DB_URL` y cada una se deshace al terminar (`@Transactional`); sin `DB_URL` se saltean. Usuarios de prueba: `Sesiones` en `src/test`.
- Acceso a datos con `NamedParameterJdbcTemplate` y SQL explícito (no JPA). `Filas.lista/una` devuelve mapas con fechas java.time; los `DATETIMEOFFSET` salen en hora de Paraguay. No anidar consultas dentro de un RowMapper (SQL Server no admite dos lecturas abiertas en la misma conexión). No pasar parámetros nulos en comparaciones (`:id IS NULL`): usar un valor centinela.
- Rutas de la plantilla: `/` (inicio vacío), `/componentes` (catálogo de componentes), `/login` (solo visual).

## Cómo se construyen las pantallas

- Leer [README.md](README.md) (agregar pantallas, menú, marca) y [docs/sistema-de-diseno.md](docs/sistema-de-diseno.md) (clases, tokens, funciones JS).
- Cada pantalla: plantilla en `templates/pantallas/` con el layout `layout/layout`, ruta en un controlador con `ModeloComun.pantalla(...)`, opción en el menú.
- Campos que dependen de otro (persona física/jurídica): `data-visible-si="campo:valor"` (abm.js). Permiso de edición en el controlador con `UsuarioActual.exigir("X_EDITAR")` y `puedeEditar` en el modelo para ocultar botones.
- Reutilizar los componentes existentes (`gx-panel`, `gx-campos`, `gx-grilla`, `gx-btn`, modales `app-modal`, `confirmar`/`notificar`); no inventar estilos nuevos sin necesidad.
- **Patrón de cada pantalla (pedido del usuario):** primero la lista por defecto (panel "Filtros" con el botón Agregar + grilla `data-paginar` con menú de acciones por fila), y desde ella el ABM: Agregar (`/x/nuevo`), Visualizar (`/x/{id}`, campos deshabilitados), Modificar (`/x/{id}/editar`) y la baja. La referencia es Usuarios: `UsuariosController`, `UsuariosServicio`, `templates/pantallas/usuarios/`.
  - Validación en el servidor con `Errores` (por campo y general) y en el navegador con `label.requerido`; los errores por campo con el fragmento `componentes :: error('campo')`.
  - Confirmación al guardar o dar de baja: `form[data-confirmar]` (`abm.js`). Las acciones de fila que cambian datos son formularios POST con `<button>` sin `type` (con `type="submit"` el tema lo pinta como botón primario).
  - Después de guardar, redirigir a la lista con `?ok=mensaje` (o `?error=` si la acción no se pudo hacer).
  - Auditar alta, modificación y baja con `Auditoria.registrar`, sin claves.
  - Permisos: el de ver va por ruta en `SeguridadConfig`; los de editar/cerrar/eliminar, en cada controlador.

## Reglas del repositorio

- El repositorio es **público**: nunca subir credenciales, `.env`, datos reales de clientes ni archivos adjuntos.
- La conexión a la base va por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
- Autor de los commits: `Ariel Cabrera <arielcabrera9099-afk@users.noreply.github.com>` (email privado de GitHub; en una máquina nueva configurarlo con `git config user.name` / `git config user.email` en este repositorio).
- Textos de la interfaz y documentación en español.
