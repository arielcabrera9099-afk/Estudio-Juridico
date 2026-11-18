package com.ejemplo.plantilla.roles;

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
 * Roles y sus permisos. Los permisos los define el sistema (migraciones); aquí solo se asignan a los roles. Reglas:
 * <ul>
 *   <li>Código: 3 a 30 caracteres en mayúsculas (letras, números y guion bajo), único y fijo después del alta (el
 *       sistema usa ADMINISTRADOR y ABOGADO en sus reglas).</li>
 *   <li>Nombre obligatorio y único. Al menos un permiso.</li>
 *   <li>El rol Administrador tiene siempre todos los permisos y no se da de baja: así nadie queda sin acceso a la
 *       administración.</li>
 *   <li>Un rol asignado a usuarios activos no se da de baja: primero hay que quitárselo.</li>
 *   <li>Los cambios de permisos rigen desde el próximo ingreso de cada usuario.</li>
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

    /** Roles según los filtros. estado: ACTIVOS, INACTIVOS o vacío (todos). */
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
                + "(SELECT COUNT(*) FROM rol_permiso rp WHERE rp.rol_id = r.id) AS permisos, "
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
            form.setPermisos(jdbc.queryForList("SELECT permiso_id FROM rol_permiso WHERE rol_id = :id", Map.of("id", id), Long.class));
            return form;
        });
    }

    /** Todos los permisos agrupados por módulo, en el orden en que se muestran. */
    public Map<String, List<Map<String, Object>>> permisosPorModulo() {
        return Filas.lista(jdbc, "SELECT id, codigo, modulo, descripcion FROM permiso ORDER BY "
                        + "CASE modulo WHEN 'CLIENTES' THEN 1 WHEN 'EXPEDIENTES' THEN 2 WHEN 'AGENDA' THEN 3 "
                        + "WHEN 'DOCUMENTOS' THEN 4 WHEN 'REPORTES' THEN 5 WHEN 'CATALOGOS' THEN 6 ELSE 7 END, id", Map.of())
                .stream().collect(Collectors.groupingBy(p -> (String) p.get("modulo"), LinkedHashMap::new, Collectors.toList()));
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

    // ------------------------------------------------------------------------------------------------ validación

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
        if (!esAdministrador(f)) {
            List<Long> validos = jdbc.queryForList("SELECT id FROM permiso", Map.of(), Long.class);
            if (f.getPermisos().isEmpty()) e.campo("permisos", "Elija al menos un permiso.");
            else if (!validos.containsAll(f.getPermisos())) e.campo("permisos", "Uno de los permisos elegidos no existe.");
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
        guardarPermisos(id, f.getPermisos());
        auditoria.registrar(Auditoria.ALTA, "rol", id, null, f.paraAuditoria());
        return id;
    }

    /** Modifica nombre, descripción, permisos y estado. El Administrador conserva todos los permisos y queda activo. */
    @Transactional
    public void modificar(RolForm f) {
        RolForm antes = formulario(f.getId()).orElseThrow();
        f.setCodigo(antes.getCodigo());
        if (esAdministrador(f)) {
            f.setActivo(true);
            f.setPermisos(antes.getPermisos());
        }
        jdbc.update("UPDATE rol SET nombre = :nombre, descripcion = :descripcion, activo = :activo, "
                + "modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                parametros(f).addValue("id", f.getId()).addValue("activo", f.isActivo()));
        if (!esAdministrador(f)) guardarPermisos(f.getId(), f.getPermisos());
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

    private void guardarPermisos(long id, List<Long> permisos) {
        jdbc.update("DELETE FROM rol_permiso WHERE rol_id = :id", Map.of("id", id));
        for (Long permiso : permisos.stream().distinct().collect(Collectors.toList())) {
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
