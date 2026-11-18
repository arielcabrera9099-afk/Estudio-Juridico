package com.ejemplo.plantilla.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;

/** ABM de roles y su matriz de permisos contra la base real (cada prueba se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class RolesTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private long adminId;
    private RequestPostProcessor admin;

    @BeforeEach
    void sesion() {
        adminId = Sesiones.crearUsuario(jdbc, "prueba_admin", "ADMINISTRADOR", false);
        admin = Sesiones.como(adminId, "prueba_admin", Sesiones.TODOS);
    }


    @Autowired
    private UserDetailsService usuarios;

    private String menu(String codigo) {
        return String.valueOf(jdbc.queryForObject("SELECT id FROM menu WHERE codigo = :c", Map.of("c", codigo), Long.class));
    }

    private String permiso(String codigo) {
        return String.valueOf(jdbc.queryForObject("SELECT id FROM permiso WHERE codigo = :c", Map.of("c", codigo), Long.class));
    }

    private Map<String, Object> rol(String codigo) {
        return jdbc.queryForMap("SELECT * FROM rol WHERE codigo = :c", Map.of("c", codigo));
    }

    private List<String> pantallasDe(String codigo) {
        return jdbc.queryForList("SELECT m.codigo FROM rol_menu rm JOIN rol r ON r.id = rm.rol_id "
                + "JOIN menu m ON m.id = rm.menu_id WHERE r.codigo = :c ORDER BY m.codigo", Map.of("c", codigo), String.class);
    }

    private List<String> accionesDe(String codigo) {
        return jdbc.queryForList("SELECT p.codigo FROM rol_permiso rp JOIN rol r ON r.id = rp.rol_id "
                + "JOIN permiso p ON p.id = rp.permiso_id WHERE r.codigo = :c ORDER BY p.codigo", Map.of("c", codigo), String.class);
    }

    /** Procurador: ve Expedientes y el Calendario, y puede modificar expedientes. */
    private long crearProcurador() throws Exception {
        mvc.perform(post("/admin/roles").with(admin).with(csrf())
                        .param("codigo", " procurador ").param("nombre", "Procurador")
                        .param("descripcion", "Sigue los expedientes en tribunales")
                        .param("menus", menu("EXPEDIENTES_LISTA")).param("menus", menu("AGENDA_CALENDARIO"))
                        .param("permisos", permiso("EXPEDIENTES_EDITAR"))
                        // Acción de una pantalla que el rol no ve: se descarta.
                        .param("permisos", permiso("DOCUMENTOS_ELIMINAR")))
                .andExpect(redirectedUrlPattern("/admin/roles?ok=*"));
        return ((Number) rol("PROCURADOR").get("id")).longValue();
    }

    @Test
    void laListaMuestraLosRolesConSusCantidades() throws Exception {
        mvc.perform(get("/admin/roles").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ADMINISTRADOR")))
                .andExpect(content().string(Matchers.containsString("ASISTENTE")));
        mvc.perform(get("/admin/roles").param("texto", "no-existe").with(admin))
                .andExpect(content().string(Matchers.containsString("No hay roles con esos filtros.")));
    }

    @Test
    void altaGuardaElMenuYDescartaAccionesDePantallasQueNoVe() throws Exception {
        long id = crearProcurador();
        assertThat(rol("PROCURADOR").get("creado_por")).isEqualTo(adminId);
        assertThat(pantallasDe("PROCURADOR")).containsExactly("AGENDA_CALENDARIO", "EXPEDIENTES_LISTA");
        assertThat(accionesDe("PROCURADOR")).containsExactly("EXPEDIENTES_EDITAR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE tabla = 'rol' AND accion = 'ALTA' AND registro_id = :id",
                Map.of("id", id), Integer.class)).isEqualTo(1);
        mvc.perform(get("/admin/roles/" + id).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Menú del rol")))
                .andExpect(content().string(Matchers.containsString("Cerrar y reabrir expedientes")))
                .andExpect(content().string(Matchers.containsString("Ningún usuario activo tiene este rol.")));
    }

    @Test
    void elUsuarioVeSoloElMenuDeSuRol() throws Exception {
        crearProcurador();
        Sesiones.crearUsuario(jdbc, "prueba_procurador", "PROCURADOR", false);
        RequestPostProcessor procurador = SecurityMockMvcRequestPostProcessors.user(usuarios.loadUserByUsername("prueba_procurador"));
        mvc.perform(get("/expedientes").with(procurador)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/agenda")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/clientes\""))));
        mvc.perform(get("/clientes").with(procurador)).andExpect(status().isForbidden());
        mvc.perform(get("/agenda/vencimientos").with(procurador)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/roles").with(procurador)).andExpect(status().isForbidden());
        mvc.perform(get("/reportes/expedientes.csv").with(procurador)).andExpect(status().isForbidden());
        // Puede modificar expedientes pero no cerrarlos: el estado de cierre no se le ofrece.
        mvc.perform(get("/expedientes/nuevo").with(procurador)).andExpect(status().isOk());
        // Lo que no es de ninguna pantalla solo pide haber ingresado.
        mvc.perform(get("/").with(procurador)).andExpect(status().isOk());
        mvc.perform(get("/notificaciones").with(procurador)).andExpect(status().isOk());
    }

    @Test
    void altaRechazaDatosInvalidos() throws Exception {
        mvc.perform(post("/admin/roles").with(admin).with(csrf()).param("codigo", "abogado").param("nombre", "Asistente"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ya existe un rol con ese código.")))
                .andExpect(content().string(Matchers.containsString("Ya existe un rol con ese nombre.")))
                .andExpect(content().string(Matchers.containsString("Elija al menos una pantalla.")));
        mvc.perform(post("/admin/roles").with(admin).with(csrf()).param("codigo", "1X").param("menus", "999999"))
                .andExpect(content().string(Matchers.containsString("El código debe tener de 3 a 30 caracteres")))
                .andExpect(content().string(Matchers.containsString("Nombre es requerido.")))
                .andExpect(content().string(Matchers.containsString("Elija al menos una pantalla.")));
    }

    @Test
    void modificarCambiaElMenuPeroNoElCodigo() throws Exception {
        long id = crearProcurador();
        mvc.perform(post("/admin/roles/" + id).with(admin).with(csrf()).param("codigo", "OTRO")
                        .param("nombre", "Procurador judicial").param("menus", menu("CLIENTES_LISTA"))
                        .param("permisos", permiso("CLIENTES_EDITAR")).param("activo", "true"))
                .andExpect(redirectedUrlPattern("/admin/roles?ok=*"));
        assertThat(rol("PROCURADOR").get("nombre")).isEqualTo("PROCURADOR JUDICIAL");
        assertThat(pantallasDe("PROCURADOR")).containsExactly("CLIENTES_LISTA");
        assertThat(accionesDe("PROCURADOR")).containsExactly("CLIENTES_EDITAR");
    }

    @Test
    void elAdministradorVeTodoYNoSeDaDeBaja() throws Exception {
        long id = ((Number) rol("ADMINISTRADOR").get("id")).longValue();
        int pantallas = jdbc.queryForObject("SELECT COUNT(*) FROM menu WHERE nivel = 'OPCION' AND activo = 1", Map.of(), Integer.class);
        int acciones = jdbc.queryForObject("SELECT COUNT(*) FROM permiso", Map.of(), Integer.class);
        assertThat(pantallasDe("ADMINISTRADOR")).hasSize(pantallas);
        mvc.perform(post("/admin/roles/" + id).with(admin).with(csrf()).param("nombre", "Administrador general")
                        .param("_activo", "on"))
                .andExpect(redirectedUrlPattern("/admin/roles?ok=*"));
        assertThat(rol("ADMINISTRADOR").get("activo")).isEqualTo(true);
        assertThat(pantallasDe("ADMINISTRADOR")).hasSize(pantallas);
        assertThat(accionesDe("ADMINISTRADOR")).hasSize(acciones);
        mvc.perform(post("/admin/roles/" + id + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/roles?error=*"));
        assertThat(rol("ADMINISTRADOR").get("activo")).isEqualTo(true);
    }

    @Test
    void losRolesConservanLoQueVeianAntes() {
        // V13 pasó los permisos de "ver" a pantallas: el Asistente ve lo suyo y no ve reportes ni administración.
        assertThat(pantallasDe("ASISTENTE")).contains("CLIENTES_LISTA", "EXPEDIENTES_LISTA", "AGENDA_CALENDARIO", "DOCUMENTOS_BUSCAR")
                .doesNotContain("REPORTES_EXPEDIENTES", "ADMIN_USUARIOS");
        assertThat(accionesDe("ASISTENTE")).doesNotContain("EXPEDIENTES_CERRAR", "DOCUMENTOS_ELIMINAR");
        assertThat(accionesDe("ABOGADO")).contains("EXPEDIENTES_CERRAR");
    }

    @Test
    void unRolConUsuariosActivosNoSeDaDeBaja() throws Exception {
        long id = crearProcurador();
        long usuario = Sesiones.crearUsuario(jdbc, "prueba_procurador", "PROCURADOR", false);
        mvc.perform(post("/admin/roles/" + id + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/roles?error=*"));
        assertThat(rol("PROCURADOR").get("activo")).isEqualTo(true);

        jdbc.update("UPDATE usuario SET activo = 0 WHERE id = :id", Map.of("id", usuario));
        mvc.perform(post("/admin/roles/" + id + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/roles?ok=*"));
        assertThat(rol("PROCURADOR").get("activo")).isEqualTo(false);
        mvc.perform(post("/admin/roles/" + id + "/reactivar").with(admin).with(csrf()));
        assertThat(rol("PROCURADOR").get("activo")).isEqualTo(true);
    }

    @Test
    void sinPermisoDeSeguridadNoEntra() throws Exception {
        long asistente = Sesiones.crearUsuario(jdbc, "prueba_asistente", "ASISTENTE", false);
        mvc.perform(get("/admin/roles").with(Sesiones.como(asistente, "prueba_asistente", Sesiones.ASISTENTE)))
                .andExpect(status().isForbidden());
    }
}
