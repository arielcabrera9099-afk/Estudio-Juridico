package com.ejemplo.plantilla;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.ejemplo.plantilla.seguridad.UsuarioSesion;

/** Usuarios de prueba: se insertan dentro de la transacción de cada prueba (que se deshace al terminar). */
public final class Sesiones {

    public static final String CLAVE = "Prueba1234";
    public static final Set<String> TODOS = Set.of("CLIENTES_VER", "CLIENTES_EDITAR", "EXPEDIENTES_VER", "EXPEDIENTES_EDITAR",
            "EXPEDIENTES_CERRAR", "AGENDA_VER", "AGENDA_EDITAR", "DOCUMENTOS_VER", "DOCUMENTOS_SUBIR", "DOCUMENTOS_ELIMINAR",
            "REPORTES_VER", "CATALOGOS_EDITAR", "SEGURIDAD_ADMINISTRAR", "AUDITORIA_VER");
    public static final Set<String> ASISTENTE = Set.of("CLIENTES_VER", "CLIENTES_EDITAR", "EXPEDIENTES_VER",
            "EXPEDIENTES_EDITAR", "AGENDA_VER", "AGENDA_EDITAR", "DOCUMENTOS_VER", "DOCUMENTOS_SUBIR");

    private Sesiones() {
    }

    /** Inserta un usuario con un rol y la clave {@link #CLAVE}; devuelve su id. */
    public static long crearUsuario(NamedParameterJdbcTemplate jdbc, String usuario, String rol, boolean debeCambiarClave) {
        Long id = jdbc.queryForObject("INSERT INTO usuario (nombre_usuario, email, clave_hash, nombres, apellidos, "
                + "debe_cambiar_clave) OUTPUT inserted.id VALUES (:u, :email, :hash, N'Prueba', N'Automática', :debe)",
                Map.of("u", usuario, "email", usuario + "@prueba.local", "hash", new BCryptPasswordEncoder().encode(CLAVE),
                        "debe", debeCambiarClave), Long.class);
        jdbc.update("INSERT INTO usuario_rol (usuario_id, rol_id) SELECT :id, id FROM rol WHERE codigo = :rol",
                Map.of("id", id, "rol", rol));
        return id;
    }

    /** Inserta un cliente de prueba (persona física con cédula) y devuelve su id. */
    public static long crearCliente(NamedParameterJdbcTemplate jdbc, String nombres, String cedula) {
        return jdbc.queryForObject("INSERT INTO cliente (tipo_persona, nombres, apellidos, tipo_documento_identidad_id, numero_documento) "
                + "OUTPUT inserted.id SELECT 'F', :n, N'Prueba', id, :ci FROM tipo_documento_identidad WHERE codigo = 'CI'",
                Map.of("n", nombres, "ci", cedula), Long.class);
    }

    /** La sesión de un usuario ya insertado, con los permisos dados. */
    public static RequestPostProcessor como(long id, String usuario, Set<String> permisos) {
        return SecurityMockMvcRequestPostProcessors.user(
                new UsuarioSesion(id, usuario, "", "Prueba Automática", List.of("Prueba"), permisos, true, false, false));
    }
}
