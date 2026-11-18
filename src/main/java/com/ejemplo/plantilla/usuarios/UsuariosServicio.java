package com.ejemplo.plantilla.usuarios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.seguridad.ReglasClave;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.Opcion;

/**
 * Usuarios del sistema: búsqueda, alta, modificación, baja lógica (inactivar), reactivación, desbloqueo y reinicio de
 * clave. Reglas:
 * <ul>
 *   <li>Usuario: 3 a 30 caracteres en minúsculas (letras, números, punto, guion y guion bajo), único y fijo después
 *       del alta.</li>
 *   <li>Email válido y único. Nombres y apellidos obligatorios. Al menos un rol.</li>
 *   <li>Matrícula profesional obligatoria para el rol Abogado.</li>
 *   <li>Siempre queda al menos un administrador activo, y nadie se da de baja a sí mismo.</li>
 * </ul>
 */
@Service
public class UsuariosServicio {

    public static final int MAXIMO_FILAS = 500;
    private static final String PATRON_USUARIO = "^[a-z0-9._-]{3,30}$";
    private static final String PATRON_EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final String PATRON_TELEFONO = "^[0-9+()\\s-]{6,30}$";

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Auditoria auditoria;

    public UsuariosServicio(NamedParameterJdbcTemplate jdbc, PasswordEncoder encoder, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    /** Usuarios según los filtros. estado: ACTIVOS, INACTIVOS, BLOQUEADOS o vacío (todos). */
    public List<Map<String, Object>> buscar(String texto, Long rolId, String estado) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (texto != null && !texto.isBlank()) {
            w.append(" AND (u.nombre_usuario LIKE :texto OR u.nombres LIKE :texto OR u.apellidos LIKE :texto "
                    + "OR u.email LIKE :texto OR CONCAT(u.nombres, ' ', u.apellidos) LIKE :texto)");
            p.addValue("texto", "%" + texto.trim() + "%");
        }
        if (rolId != null) {
            w.append(" AND EXISTS (SELECT 1 FROM usuario_rol x WHERE x.usuario_id = u.id AND x.rol_id = :rol)");
            p.addValue("rol", rolId);
        }
        if ("ACTIVOS".equals(estado)) w.append(" AND u.activo = 1");
        else if ("INACTIVOS".equals(estado)) w.append(" AND u.activo = 0");
        else if ("BLOQUEADOS".equals(estado)) w.append(" AND u.bloqueado_hasta > SYSDATETIMEOFFSET()");
        return Filas.lista(jdbc, "SELECT TOP " + (MAXIMO_FILAS + 1) + " u.id, u.nombre_usuario, u.nombres, u.apellidos, "
                + "u.email, u.matricula, u.activo, u.ultimo_acceso, u.debe_cambiar_clave, u.bloqueado_hasta, "
                + "CASE WHEN u.bloqueado_hasta > SYSDATETIMEOFFSET() THEN 1 ELSE 0 END AS bloqueado, "
                + "(SELECT STRING_AGG(r.nombre, ', ') WITHIN GROUP (ORDER BY r.nombre) FROM usuario_rol ur "
                + "JOIN rol r ON r.id = ur.rol_id WHERE ur.usuario_id = u.id) AS roles "
                + "FROM usuario u" + w + " ORDER BY u.apellidos, u.nombres", p);
    }

    /** Ficha de un usuario: los datos del formulario y lo que solo se muestra (último acceso, bloqueo, auditoría). */
    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, "SELECT u.*, CASE WHEN u.bloqueado_hasta > SYSDATETIMEOFFSET() THEN 1 ELSE 0 END AS bloqueado, "
                + "c.nombre_usuario AS creado_por_usuario, m.nombre_usuario AS modificado_por_usuario "
                + "FROM usuario u LEFT JOIN usuario c ON c.id = u.creado_por LEFT JOIN usuario m ON m.id = u.modificado_por "
                + "WHERE u.id = :id", Map.of("id", id));
    }

    public Optional<UsuarioForm> formulario(long id) {
        return ficha(id).map(f -> {
            UsuarioForm form = new UsuarioForm();
            form.setId(id);
            form.setNombreUsuario((String) f.get("nombre_usuario"));
            form.setEmail((String) f.get("email"));
            form.setNombres((String) f.get("nombres"));
            form.setApellidos((String) f.get("apellidos"));
            form.setTelefono((String) f.get("telefono"));
            form.setMatricula((String) f.get("matricula"));
            form.setActivo(Boolean.TRUE.equals(f.get("activo")));
            form.setRoles(jdbc.queryForList("SELECT rol_id FROM usuario_rol WHERE usuario_id = :id", Map.of("id", id), Long.class));
            return form;
        });
    }

    /** Roles activos para elegir. */
    public List<Opcion> roles() {
        return jdbc.query("SELECT id, nombre, codigo FROM rol WHERE activo = 1 ORDER BY nombre", Map.of(),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getString("codigo")));
    }

    // ------------------------------------------------------------------------------------------------ validación

    public Errores validar(UsuarioForm f, boolean alta) {
        Errores e = new Errores();
        if (alta) {
            if (f.getNombreUsuario() == null) e.campo("nombreUsuario", "Usuario es requerido.");
            else if (!f.getNombreUsuario().matches(PATRON_USUARIO)) {
                e.campo("nombreUsuario", "El usuario debe tener de 3 a 30 caracteres: letras sin acentos, números, punto, guion o guion bajo.");
            } else if (existe("SELECT COUNT(*) FROM usuario WHERE nombre_usuario = :v", f.getNombreUsuario(), null)) {
                e.campo("nombreUsuario", "Ya existe un usuario con ese nombre.");
            }
        }
        if (f.getEmail() == null) e.campo("email", "Email es requerido.");
        else if (f.getEmail().length() > 120 || !f.getEmail().matches(PATRON_EMAIL)) e.campo("email", "El email no es válido.");
        else if (existe("SELECT COUNT(*) FROM usuario WHERE email = :v AND id <> :id", f.getEmail(), f.getId())) {
            e.campo("email", "Ya existe un usuario con ese email.");
        }
        if (f.getNombres() == null) e.campo("nombres", "Nombres es requerido.");
        else if (f.getNombres().length() > 80) e.campo("nombres", "Nombres admite hasta 80 caracteres.");
        if (f.getApellidos() == null) e.campo("apellidos", "Apellidos es requerido.");
        else if (f.getApellidos().length() > 80) e.campo("apellidos", "Apellidos admite hasta 80 caracteres.");
        if (f.getTelefono() != null && !f.getTelefono().matches(PATRON_TELEFONO)) {
            e.campo("telefono", "El teléfono solo admite números, espacios, +, guiones y paréntesis (6 a 30 caracteres).");
        }
        if (f.getMatricula() != null && f.getMatricula().length() > 30) e.campo("matricula", "Matrícula admite hasta 30 caracteres.");

        List<Opcion> roles = roles();
        List<Long> validos = roles.stream().map(r -> (Long) r.getId()).collect(Collectors.toList());
        if (f.getRoles().isEmpty()) e.campo("roles", "Elija al menos un rol.");
        else if (!validos.containsAll(f.getRoles())) e.campo("roles", "Uno de los roles elegidos no existe o está inactivo.");
        boolean abogado = roles.stream().anyMatch(r -> "ABOGADO".equals(r.getCodigo()) && f.getRoles().contains((Long) r.getId()));
        if (abogado && f.getMatricula() == null) e.campo("matricula", "La matrícula es requerida para el rol Abogado.");

        if (alta) {
            ReglasClave.validar(f.getClave(), f.getNombreUsuario()).ifPresent(m -> e.campo("clave", m));
            if (f.getClave() != null && !f.getClave().equals(f.getClaveRepetir())) e.campo("claveRepetir", "Las contraseñas no coinciden.");
        } else {
            Long yo = UsuarioActual.id().orElse(null);
            if (f.getId().equals(yo) && !f.isActivo()) e.campo("activo", "No puede darse de baja a sí mismo.");
            boolean quedaAdmin = f.isActivo() && roles.stream()
                    .anyMatch(r -> "ADMINISTRADOR".equals(r.getCodigo()) && f.getRoles().contains((Long) r.getId()));
            if (!quedaAdmin && otrosAdministradoresActivos(f.getId()) == 0) {
                e.general("Debe quedar al menos un administrador activo: este es el único.");
            }
        }
        return e;
    }

    private boolean existe(String sql, String valor, Long id) {
        Map<String, Object> p = new HashMap<>();
        p.put("v", valor);
        p.put("id", id == null ? -1L : id);
        Integer n = jdbc.queryForObject(sql, p, Integer.class);
        return n != null && n > 0;
    }

    private int otrosAdministradoresActivos(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(DISTINCT u.id) FROM usuario u JOIN usuario_rol ur ON ur.usuario_id = u.id "
                + "JOIN rol r ON r.id = ur.rol_id WHERE r.codigo = 'ADMINISTRADOR' AND u.activo = 1 AND u.id <> :id",
                Map.of("id", id), Integer.class);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------------------------------------ cambios

    @Transactional
    public long crear(UsuarioForm f) {
        MapSqlParameterSource p = parametros(f).addValue("hash", encoder.encode(f.getClave()))
                .addValue("usuario", f.getNombreUsuario());
        Long id = jdbc.queryForObject("INSERT INTO usuario (nombre_usuario, email, clave_hash, nombres, apellidos, telefono, "
                + "matricula, debe_cambiar_clave, activo, creado_por) OUTPUT inserted.id VALUES (:usuario, :email, :hash, "
                + ":nombres, :apellidos, :telefono, :matricula, 1, 1, :yo)", p, Long.class);
        guardarRoles(id, f.getRoles());
        auditoria.registrar(Auditoria.ALTA, "usuario", id, null, f.paraAuditoria());
        return id;
    }

    @Transactional
    public void modificar(UsuarioForm f) {
        UsuarioForm antes = formulario(f.getId()).orElseThrow();
        jdbc.update("UPDATE usuario SET email = :email, nombres = :nombres, apellidos = :apellidos, telefono = :telefono, "
                + "matricula = :matricula, activo = :activo, modificado_en = SYSUTCDATETIME(), modificado_por = :yo "
                + "WHERE id = :id", parametros(f).addValue("id", f.getId()).addValue("activo", f.isActivo()));
        guardarRoles(f.getId(), f.getRoles());
        f.setNombreUsuario(antes.getNombreUsuario());
        auditoria.registrar(Auditoria.MODIFICACION, "usuario", f.getId(), antes.paraAuditoria(), f.paraAuditoria());
    }

    /** Baja lógica. Devuelve el motivo si no se puede. */
    @Transactional
    public Optional<String> darDeBaja(long id) {
        if (UsuarioActual.id().map(yo -> yo == id).orElse(false)) return Optional.of("No puede darse de baja a sí mismo.");
        if (esAdministrador(id) && otrosAdministradoresActivos(id) == 0) {
            return Optional.of("Debe quedar al menos un administrador activo: este es el único.");
        }
        cambiarActivo(id, false);
        return Optional.empty();
    }

    @Transactional
    public void reactivar(long id) {
        cambiarActivo(id, true);
    }

    @Transactional
    public void desbloquear(long id) {
        jdbc.update("UPDATE usuario SET bloqueado_hasta = NULL, intentos_fallidos = 0, modificado_en = SYSUTCDATETIME(), "
                + "modificado_por = :yo WHERE id = :id", Map.of("id", id, "yo", UsuarioActual.id().orElseThrow()));
        auditoria.registrar(Auditoria.MODIFICACION, "usuario", id, null, Map.of("desbloqueado", true));
    }

    /** Reinicia la clave: el usuario deberá cambiarla al ingresar. */
    @Transactional
    public void reiniciarClave(long id, String clave) {
        jdbc.update("UPDATE usuario SET clave_hash = :hash, debe_cambiar_clave = 1, bloqueado_hasta = NULL, "
                + "intentos_fallidos = 0, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                Map.of("id", id, "hash", encoder.encode(clave), "yo", UsuarioActual.id().orElseThrow()));
        auditoria.registrar(Auditoria.MODIFICACION, "usuario", id, null, Map.of("clave", "reiniciada por un administrador"));
    }

    private void cambiarActivo(long id, boolean activo) {
        jdbc.update("UPDATE usuario SET activo = :activo, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                Map.of("id", id, "activo", activo, "yo", UsuarioActual.id().orElseThrow()));
        auditoria.registrar(activo ? Auditoria.MODIFICACION : Auditoria.BAJA, "usuario", id,
                Map.of("activo", !activo), Map.of("activo", activo));
    }

    private boolean esAdministrador(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol ur JOIN rol r ON r.id = ur.rol_id "
                + "WHERE ur.usuario_id = :id AND r.codigo = 'ADMINISTRADOR'", Map.of("id", id), Integer.class);
        return n != null && n > 0;
    }

    private void guardarRoles(long id, List<Long> roles) {
        jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = :id", Map.of("id", id));
        for (Long rol : roles) {
            jdbc.update("INSERT INTO usuario_rol (usuario_id, rol_id) VALUES (:id, :rol)", Map.of("id", id, "rol", rol));
        }
    }

    private static MapSqlParameterSource parametros(UsuarioForm f) {
        return new MapSqlParameterSource()
                .addValue("email", f.getEmail())
                .addValue("nombres", f.getNombres())
                .addValue("apellidos", f.getApellidos())
                .addValue("telefono", f.getTelefono())
                .addValue("matricula", f.getMatricula())
                .addValue("yo", UsuarioActual.id().orElse(null));
    }
}
