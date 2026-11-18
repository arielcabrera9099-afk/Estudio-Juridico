package com.ejemplo.plantilla.roles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;

/**
 * Roles. Un rol se define por su menú: las pantallas que ve (rol_menu) y, en cada una, las acciones que puede hacer
 * (rol_permiso; cada permiso cuelga de una pantalla). Reglas:
 * <ul>
 *   <li>Código: 3 a 30 caracteres en mayúsculas (letras, números y guion bajo), único y fijo después del alta (el
 *       sistema usa ADMINISTRADOR y ABOGADO en sus reglas).</li>
 *   <li>Nombre obligatorio y único. Al menos una pantalla. Una acción solo vale si el rol ve su pantalla: las demás se
 *       descartan al guardar.</li>
 *   <li>El rol Administrador ve todo y hace todo, y no se da de baja: así nadie queda sin acceso a la administración.</li>
 *   <li>Un rol asignado a usuarios activos no se da de baja: primero hay que quitárselo.</li>
 *   <li>Los cambios rigen desde el próximo ingreso de cada usuario.</li>
 * </ul>
 */
@Service
public class RolesServicio {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    private static final String PATRON_CODIGO = "^[A-Z][A-Z0-9_]{2,29}$";

    private final NamedParameterJdbcTemplate jdbc;
    private final Auditoria auditoria;

    public RolesServicio(NamedParameterJdbcTemplate jdbc, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    /** Roles según los filtros. estado: ACTIVOS, INACTIVOS o cualquier otro (todos). */
    public List<Map<String, Object>> buscar(String texto, String estado) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (texto != null && !texto.isBlank()) {
            w.append(" AND (r.codigo LIKE :texto OR r.nombre LIKE :texto OR r.descripcion LIKE :texto)");
            p.addValue("texto", "%" + texto.trim() + "%");
        }
        if ("ACTIVOS".equals(estado)) w.append(" AND r.activo = 1");
        else if ("INACTIVOS".equals(estado)) w.append(" AND r.activo = 0");
        return Filas.lista(jdbc, "SELECT r.id, r.codigo, r.nombre, r.descripcion, r.activo, "
                + "(SELECT COUNT(*) FROM rol_menu rm WHERE rm.rol_id = r.id) AS pantallas, "
                + "(SELECT COUNT(*) FROM rol_permiso rp WHERE rp.rol_id = r.id) AS acciones, "
                + "(SELECT COUNT(*) FROM usuario_rol ur JOIN usuario u ON u.id = ur.usuario_id "
                + " WHERE ur.rol_id = r.id AND u.activo = 1) AS usuarios "
                + "FROM rol r" + w + " ORDER BY r.nombre", p);
    }

    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, "SELECT r.*, c.nombre_usuario AS creado_por_usuario, m.nombre_usuario AS modificado_por_usuario, "
                + "(SELECT COUNT(*) FROM usuario_rol ur JOIN usuario u ON u.id = ur.usuario_id "
                + " WHERE ur.rol_id = r.id AND u.activo = 1) AS usuarios "
                + "FROM rol r LEFT JOIN usuario c ON c.id = r.creado_por LEFT JOIN usuario m ON m.id = r.modificado_por "
                + "WHERE r.id = :id", Map.of("id", id));
    }

    public Optional<RolForm> formulario(long id) {
        return ficha(id).map(f -> {
            RolForm form = new RolForm();
            form.setId(id);
            form.setCodigo((String) f.get("codigo"));
            form.setNombre((String) f.get("nombre"));
            form.setDescripcion((String) f.get("descripcion"));
            form.setActivo(Boolean.TRUE.equals(f.get("activo")));
            form.setMenus(jdbc.queryForList("SELECT menu_id FROM rol_menu WHERE rol_id = :id", Map.of("id", id), Long.class));
            form.setPermisos(jdbc.queryForList("SELECT permiso_id FROM rol_permiso WHERE rol_id = :id", Map.of("id", id), Long.class));
            return form;
        });
    }

    /** El menú completo (opciones activas) con las acciones de cada pantalla, en el orden del menú lateral. */
    public List<ArbolMenu.Modulo> arbol() {
        List<Map<String, Object>> filas = Filas.lista(jdbc, "SELECT id, padre_id, nivel, titulo, icono, ruta FROM menu "
                + "WHERE activo = 1 ORDER BY orden, id", Map.of());
        Map<Long, List<ArbolMenu.Accion>> acciones = new HashMap<>();
        for (Map<String, Object> a : Filas.lista(jdbc, "SELECT id, codigo, descripcion, menu_id FROM permiso ORDER BY id", Map.of())) {
            acciones.computeIfAbsent(numero(a.get("menu_id")), k -> new ArrayList<>())
                    .add(new ArbolMenu.Accion(numero(a.get("id")), (String) a.get("codigo"), (String) a.get("descripcion")));
        }
        List<ArbolMenu.Modulo> modulos = new ArrayList<>();
        for (Map<String, Object> mod : filas) {
            if (!"MODULO".equals(mod.get("nivel"))) continue;
            ArbolMenu.Modulo m = new ArbolMenu.Modulo(numero(mod.get("id")), (String) mod.get("titulo"), (String) mod.get("icono"));
            ArbolMenu.Seccion directas = new ArbolMenu.Seccion("");
            for (Map<String, Object> hijo : hijos(filas, mod.get("id"))) {
                if ("OPCION".equals(hijo.get("nivel"))) directas.getPantallas().add(pantalla(hijo, acciones));
            }
            if (!directas.getPantallas().isEmpty()) m.getSecciones().add(directas);
            for (Map<String, Object> sec : hijos(filas, mod.get("id"))) {
                if (!"SECCION".equals(sec.get("nivel"))) continue;
                ArbolMenu.Seccion s = new ArbolMenu.Seccion((String) sec.get("titulo"));
                for (Map<String, Object> op : hijos(filas, sec.get("id"))) {
                    if ("OPCION".equals(op.get("nivel"))) s.getPantallas().add(pantalla(op, acciones));
                }
                if (!s.getPantallas().isEmpty()) m.getSecciones().add(s);
            }
            if (!m.getSecciones().isEmpty()) modulos.add(m);
        }
        return modulos;
    }

    private static ArbolMenu.Pantalla pantalla(Map<String, Object> f, Map<Long, List<ArbolMenu.Accion>> acciones) {
        ArbolMenu.Pantalla p = new ArbolMenu.Pantalla(numero(f.get("id")), (String) f.get("titulo"), (String) f.get("ruta"));
        p.getAcciones().addAll(acciones.getOrDefault(p.getId(), List.of()));
        return p;
    }

    private static List<Map<String, Object>> hijos(List<Map<String, Object>> filas, Object padre) {
        return filas.stream().filter(f -> padre.equals(f.get("padre_id"))).collect(Collectors.toList());
    }

    private static Long numero(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    /** Usuarios activos que tienen el rol (para la ficha). */
    public List<Map<String, Object>> usuarios(long id) {
        return Filas.lista(jdbc, "SELECT u.id, u.nombre_usuario, u.nombres, u.apellidos FROM usuario_rol ur "
                + "JOIN usuario u ON u.id = ur.usuario_id WHERE ur.rol_id = :id AND u.activo = 1 "
                + "ORDER BY u.apellidos, u.nombres", Map.of("id", id));
    }

    public boolean esAdministrador(RolForm f) {
        return ADMINISTRADOR.equals(f.getCodigo());
    }

    /** Todas las pantallas y acciones: lo que tiene el Administrador. */
    private void todo(RolForm f) {
        f.setMenus(jdbc.queryForList("SELECT id FROM menu WHERE nivel = 'OPCION' AND activo = 1", Map.of(), Long.class));
        f.setPermisos(jdbc.queryForList("SELECT id FROM permiso", Map.of(), Long.class));
    }

    // ------------------------------------------------------------------------------------------------ validación

    /** Valida los datos y deja en el formulario solo las pantallas y acciones válidas (las acciones de pantallas elegidas). */
    public Errores validar(RolForm f, boolean alta) {
        Errores e = new Errores();
        if (alta) {
            if (f.getCodigo() == null) e.campo("codigo", "Código es requerido.");
            else if (!f.getCodigo().matches(PATRON_CODIGO)) {
                e.campo("codigo", "El código debe tener de 3 a 30 caracteres: letras sin acentos, números y guion bajo, empezando por una letra.");
            } else if (existe("SELECT COUNT(*) FROM rol WHERE codigo = :v", f.getCodigo(), null)) {
                e.campo("codigo", "Ya existe un rol con ese código.");
            }
        }
        if (f.getNombre() == null) e.campo("nombre", "Nombre es requerido.");
        else if (f.getNombre().length() > 60) e.campo("nombre", "Nombre admite hasta 60 caracteres.");
        else if (existe("SELECT COUNT(*) FROM rol WHERE nombre = :v AND id <> :id", f.getNombre(), f.getId())) {
            e.campo("nombre", "Ya existe un rol con ese nombre.");
        }
        if (f.getDescripcion() != null && f.getDescripcion().length() > 500) {
            e.campo("descripcion", "Descripción admite hasta 500 caracteres.");
        }
        if (esAdministrador(f)) {
            todo(f);
        } else {
            List<Long> pantallas = jdbc.queryForList("SELECT id FROM menu WHERE nivel = 'OPCION' AND activo = 1", Map.of(), Long.class);
            f.setMenus(f.getMenus().stream().distinct().filter(pantallas::contains).collect(Collectors.toList()));
            if (f.getMenus().isEmpty()) e.campo("menus", "Elija al menos una pantalla.");
            Map<Long, Long> pantallaDe = new HashMap<>();
            jdbc.query("SELECT id, menu_id FROM permiso", Map.of(), rs -> {
                pantallaDe.put(rs.getLong(1), rs.getLong(2));
            });
            f.setPermisos(f.getPermisos().stream().distinct()
                    .filter(p -> pantallaDe.containsKey(p) && f.getMenus().contains(pantallaDe.get(p))).collect(Collectors.toList()));
        }
        if (!alta && !f.isActivo()) motivoParaNoDarDeBaja(f.getId()).ifPresent(m -> e.campo("activo", m));
        return e;
    }

    private Optional<String> motivoParaNoDarDeBaja(long id) {
        String codigo = jdbc.queryForObject("SELECT codigo FROM rol WHERE id = :id", Map.of("id", id), String.class);
        if (ADMINISTRADOR.equals(codigo)) return Optional.of("El rol Administrador no se puede dar de baja.");
        int usuarios = usuarios(id).size();
        if (usuarios > 0) {
            return Optional.of(usuarios == 1
                    ? "El rol está asignado a 1 usuario activo: quíteselo antes de darlo de baja."
                    : "El rol está asignado a " + usuarios + " usuarios activos: quíteselo antes de darlo de baja.");
        }
        return Optional.empty();
    }

    private boolean existe(String sql, String valor, Long id) {
        Map<String, Object> p = new HashMap<>();
        p.put("v", valor);
        p.put("id", id == null ? -1L : id);
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
        return n != null && n > 0;
    }

    // ------------------------------------------------------------------------------------------------ cambios

    @Transactional
    public long crear(RolForm f) {
        Long id = jdbc.queryForObject("INSERT INTO rol (codigo, nombre, descripcion, activo, creado_por) OUTPUT inserted.id "
                + "VALUES (:codigo, :nombre, :descripcion, 1, :yo)", parametros(f).addValue("codigo", f.getCodigo()), Long.class);
        guardarMenu(id, f);
        auditoria.registrar(Auditoria.ALTA, "rol", id, null, f.paraAuditoria());
        return id;
    }

    /** Modifica nombre, descripción, menú y estado. El Administrador conserva todo y queda activo. */
    @Transactional
    public void modificar(RolForm f) {
        RolForm antes = formulario(f.getId()).orElseThrow();
        f.setCodigo(antes.getCodigo());
        if (esAdministrador(f)) {
            f.setActivo(true);
            todo(f);
        }
        jdbc.update("UPDATE rol SET nombre = :nombre, descripcion = :descripcion, activo = :activo, "
                + "modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                parametros(f).addValue("id", f.getId()).addValue("activo", f.isActivo()));
        guardarMenu(f.getId(), f);
        auditoria.registrar(Auditoria.MODIFICACION, "rol", f.getId(), antes.paraAuditoria(), f.paraAuditoria());
    }

    /** Baja lógica. Devuelve el motivo si no se puede. */
    @Transactional
    public Optional<String> darDeBaja(long id) {
        Optional<String> motivo = motivoParaNoDarDeBaja(id);
        if (motivo.isEmpty()) cambiarActivo(id, false);
        return motivo;
    }

    @Transactional
    public void reactivar(long id) {
        cambiarActivo(id, true);
    }

    private void cambiarActivo(long id, boolean activo) {
        jdbc.update("UPDATE rol SET activo = :activo, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                Map.of("id", id, "activo", activo, "yo", UsuarioActual.id().orElseThrow()));
        auditoria.registrar(activo ? Auditoria.MODIFICACION : Auditoria.BAJA, "rol", id,
                Map.of("activo", !activo), Map.of("activo", activo));
    }

    private void guardarMenu(long id, RolForm f) {
        jdbc.update("DELETE FROM rol_menu WHERE rol_id = :id", Map.of("id", id));
        jdbc.update("DELETE FROM rol_permiso WHERE rol_id = :id", Map.of("id", id));
        for (Long menu : f.getMenus()) {
            jdbc.update("INSERT INTO rol_menu (rol_id, menu_id) VALUES (:id, :m)", Map.of("id", id, "m", menu));
        }
        for (Long permiso : f.getPermisos()) {
            jdbc.update("INSERT INTO rol_permiso (rol_id, permiso_id) VALUES (:id, :p)", Map.of("id", id, "p", permiso));
        }
    }

    private static MapSqlParameterSource parametros(RolForm f) {
        return new MapSqlParameterSource()
                .addValue("nombre", f.getNombre())
                .addValue("descripcion", f.getDescripcion())
                .addValue("yo", UsuarioActual.id().orElse(null));
    }

}
