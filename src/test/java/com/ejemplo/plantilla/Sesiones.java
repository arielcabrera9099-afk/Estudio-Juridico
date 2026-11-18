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
    /** Todas las pantallas del menú y todas las acciones (como el rol Administrador). */
    public static final Set<String> TODOS = Set.of(
            menu("CLIENTES_LISTA"), menu("EXPEDIENTES_LISTA"), menu("EXPEDIENTES_NUEVO"), menu("AGENDA_CALENDARIO"),
            menu("AGENDA_VENCIMIENTOS"), menu("DOCUMENTOS_BUSCAR"), menu("REPORTES_EXPEDIENTES"), menu("REPORTES_AGENDA"),
            menu("ADMIN_USUARIOS"), menu("ADMIN_ROLES"), menu("ADMIN_AUDITORIA"), menu("ADMIN_MATERIAS"), menu("ADMIN_JUZGADOS"),
            menu("ADMIN_ESTADOS"), menu("ADMIN_TIPOS"), menu("ADMIN_PAISES"), menu("ADMIN_DEPARTAMENTOS"), menu("ADMIN_CIUDADES"),
            menu("ADMIN_PARAMETROS"),
            "CLIENTES_EDITAR", "EXPEDIENTES_EDITAR", "EXPEDIENTES_CERRAR", "AGENDA_EDITAR", "DOCUMENTOS_SUBIR", "DOCUMENTOS_ELIMINAR");
    /** Lo del rol Asistente: clientes, expedientes, agenda y documentos; no cierra, no elimina, sin reportes ni administración. */
    public static final Set<String> ASISTENTE = Set.of(
            menu("CLIENTES_LISTA"), menu("EXPEDIENTES_LISTA"), menu("EXPEDIENTES_NUEVO"), menu("AGENDA_CALENDARIO"),
            menu("AGENDA_VENCIMIENTOS"), menu("DOCUMENTOS_BUSCAR"),
            "CLIENTES_EDITAR", "EXPEDIENTES_EDITAR", "AGENDA_EDITAR", "DOCUMENTOS_SUBIR");

    /** La authority de una pantalla del menú: MENU_ + código de la opción. */
    public static String menu(String codigo) {
        return "MENU_" + codigo;
    }

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
