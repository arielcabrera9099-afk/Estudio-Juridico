# Estudio-Juridico

Sistema de gestión de casos jurídicos para un abogado y su equipo (Paraguay): clientes, expedientes, agenda legal con avisos de vencimiento, control documental y reportes. Más adelante, un asistente legal con IA.

## Estado actual

- Base: la plantilla frontend (layout, sistema de diseño y componentes), sin pantallas de negocio todavía.
- Todavía **no hay base de datos**. El diseño está en [docs/modelo-de-datos.md](docs/modelo-de-datos.md): 31 tablas, roles, permisos, menú, datos iniciales y plan de implementación por módulos.
- Siguiente paso: el paso 1 del plan (PostgreSQL o H2, Flyway, Spring Data JPA) después de confirmar las preguntas abiertas del final de ese documento.

## Stack y comandos

- Java 11, Spring Boot 2.7, Thymeleaf. CSS y JS sin frameworks ni build de frontend.
- `mvn spring-boot:run` → http://localhost:8080 · `mvn package` → `target/ROOT.war` (pruebas incluidas).
- Rutas de la plantilla: `/` (inicio vacío), `/componentes` (catálogo de componentes), `/login` (solo visual).

## Cómo se construyen las pantallas

- Leer [README.md](README.md) (agregar pantallas, menú, marca) y [docs/sistema-de-diseno.md](docs/sistema-de-diseno.md) (clases, tokens, funciones JS).
- Cada pantalla: plantilla en `templates/pantallas/` con el layout `layout/layout`, ruta en un controlador con `ModeloComun.pantalla(...)`, opción en el menú.
- Reutilizar los componentes existentes (`gx-panel`, `gx-campos`, `gx-grilla`, `gx-btn`, modales `app-modal`, `confirmar`/`notificar`); no inventar estilos nuevos sin necesidad.

## Reglas del repositorio

- El repositorio es **público**: nunca subir credenciales, `.env`, datos reales de clientes ni archivos adjuntos.
- La conexión a la base va por variables de entorno (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
- Autor de los commits: `Ariel Cabrera <arielcabrera9099-afk@users.noreply.github.com>` (email privado de GitHub; en una máquina nueva configurarlo con `git config user.name` / `git config user.email` en este repositorio).
- Textos de la interfaz y documentación en español.
