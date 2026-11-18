package com.ejemplo.plantilla.seguridad;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ejemplo.plantilla.datos.Filas;

/** Carga el usuario que ingresa, con sus roles activos y los permisos de esos roles. */
@Service
public class CargaUsuarios implements UserDetailsService {

    private final NamedParameterJdbcTemplate jdbc;

    public CargaUsuarios(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String nombreUsuario) {
        return buscar(nombreUsuario).orElseThrow(() -> new UsernameNotFoundException(nombreUsuario));
    }

    public Optional<UsuarioSesion> buscar(String nombreUsuario) {
        String u = nombreUsuario == null ? "" : nombreUsuario.trim().toLowerCase();
        return Filas.una(jdbc,
                "SELECT id, nombre_usuario, clave_hash, nombres, apellidos, activo, debe_cambiar_clave, "
                        + "CASE WHEN bloqueado_hasta > SYSDATETIMEOFFSET() THEN 1 ELSE 0 END AS bloqueado "
                        + "FROM usuario WHERE nombre_usuario = :u",
                Map.of("u", u))
                .map(f -> {
                    long id = ((Number) f.get("id")).longValue();
                    Map<String, Long> p = Map.of("id", id);
                    List<String> roles = jdbc.queryForList(
                            "SELECT r.nombre FROM usuario_rol ur JOIN rol r ON r.id = ur.rol_id "
                                    + "WHERE ur.usuario_id = :id AND r.activo = 1 ORDER BY r.nombre", p, String.class);
                    List<String> permisos = jdbc.queryForList(
                            "SELECT DISTINCT pe.codigo FROM usuario_rol ur "
                                    + "JOIN rol r ON r.id = ur.rol_id AND r.activo = 1 "
                                    + "JOIN rol_permiso rp ON rp.rol_id = r.id "
                                    + "JOIN permiso pe ON pe.id = rp.permiso_id WHERE ur.usuario_id = :id", p, String.class);
                    // Las pantallas que ve (rol_menu) entran como MENU_<código de la opción>.
                    permisos = new java.util.ArrayList<>(permisos);
                    permisos.addAll(jdbc.queryForList(
                            "SELECT DISTINCT CONCAT('" + UsuarioSesion.PREFIJO_MENU + "', m.codigo) FROM usuario_rol ur "
                                    + "JOIN rol r ON r.id = ur.rol_id AND r.activo = 1 "
                                    + "JOIN rol_menu rm ON rm.rol_id = r.id "
                                    + "JOIN menu m ON m.id = rm.menu_id AND m.activo = 1 WHERE ur.usuario_id = :id", p, String.class));
                    return new UsuarioSesion(id, (String) f.get("nombre_usuario"), (String) f.get("clave_hash"),
                            f.get("nombres") + " " + f.get("apellidos"), roles, new HashSet<>(permisos),
                            Boolean.TRUE.equals(f.get("activo")), ((Number) f.get("bloqueado")).intValue() == 1,
                            Boolean.TRUE.equals(f.get("debe_cambiar_clave")));
                });
    }
}
