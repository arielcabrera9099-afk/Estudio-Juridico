package com.ejemplo.plantilla.web.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.seguridad.UsuarioSesion;

/**
 * El menú lateral, leído de la tabla {@code menu} (módulo → sección → opción) y filtrado por los permisos del usuario
 * de la sesión: una opción se ve si el usuario tiene su permiso, y un módulo o una sección si tienen alguna opción
 * visible. Las opciones que cuelgan directo de un módulo van en una sección sin rótulo. El id de cada opción es su ruta
 * sin la barra inicial ("/admin/usuarios" → "admin/usuarios"), que es también el nombre de pantalla que marca cada
 * controlador. Se arma una vez por pedido.
 */
@Component
public class MenuAplicacion {

    private static final String CACHE = MenuAplicacion.class.getName() + ".modulos";

    private final NamedParameterJdbcTemplate jdbc;

    public MenuAplicacion(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @SuppressWarnings("unchecked")
    public List<ModuloMenu> modulos() {
        RequestAttributes req = RequestContextHolder.getRequestAttributes();
        if (req != null) {
            Object c = req.getAttribute(CACHE, RequestAttributes.SCOPE_REQUEST);
            if (c != null) return (List<ModuloMenu>) c;
        }
        List<ModuloMenu> m = UsuarioActual.sesion().map(this::armar).orElse(Collections.emptyList());
        if (req != null) req.setAttribute(CACHE, m, RequestAttributes.SCOPE_REQUEST);
        return m;
    }

    /** El módulo que contiene la pantalla {@code pagina}, si está en el menú del usuario. */
    public Optional<ModuloMenu> moduloDe(String pagina) {
        return modulos().stream().filter(m -> m.tiene(pagina)).findFirst();
    }

    /** El título de la opción del menú con esa ruta, si el usuario la ve. */
    public Optional<String> tituloDe(String pagina) {
        return modulos().stream().flatMap(m -> m.secciones().stream()).flatMap(s -> s.items().stream())
                .filter(i -> i.id().equals(pagina)).map(ItemMenu::titulo).findFirst();
    }

    private List<ModuloMenu> armar(UsuarioSesion u) {
        List<Map<String, Object>> filas = Filas.lista(jdbc,
                "SELECT m.id, m.padre_id, m.nivel, m.codigo, m.titulo, m.icono, m.ruta, p.codigo AS permiso "
                        + "FROM menu m LEFT JOIN permiso p ON p.id = m.permiso_id "
                        + "WHERE m.activo = 1 ORDER BY m.orden, m.id", Map.of());
        List<ModuloMenu> modulos = new ArrayList<>();
        for (Map<String, Object> mod : filas) {
            if (!"MODULO".equals(mod.get("nivel"))) continue;
            List<SeccionMenu> secciones = new ArrayList<>();
            List<ItemMenu> directas = opciones(filas, mod.get("id"), u);
            if (!directas.isEmpty()) secciones.add(new SeccionMenu("", directas));
            for (Map<String, Object> sec : hijos(filas, mod.get("id"))) {
                if (!"SECCION".equals(sec.get("nivel"))) continue;
                List<ItemMenu> items = opciones(filas, sec.get("id"), u);
                if (!items.isEmpty()) secciones.add(new SeccionMenu((String) sec.get("titulo"), items));
            }
            if (!secciones.isEmpty()) {
                modulos.add(new ModuloMenu(((String) mod.get("codigo")).toLowerCase(), (String) mod.get("titulo"),
                        (String) mod.get("icono"), secciones));
            }
        }
        return modulos;
    }

    private static List<Map<String, Object>> hijos(List<Map<String, Object>> filas, Object padre) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Map<String, Object> f : filas) if (padre.equals(f.get("padre_id"))) l.add(f);
        return l;
    }

    private static List<ItemMenu> opciones(List<Map<String, Object>> filas, Object padre, UsuarioSesion u) {
        List<ItemMenu> l = new ArrayList<>();
        for (Map<String, Object> f : hijos(filas, padre)) {
            if (!"OPCION".equals(f.get("nivel")) || !u.tiene((String) f.get("permiso"))) continue;
            l.add(new ItemMenu(((String) f.get("ruta")).replaceFirst("^/", ""), (String) f.get("titulo")));
        }
        return l;
    }
}
