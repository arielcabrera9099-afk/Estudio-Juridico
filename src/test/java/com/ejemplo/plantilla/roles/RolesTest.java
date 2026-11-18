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

    private String permiso(String codigo) {
        return String.valueOf(jdbc.queryForObject("SELECT id FROM permiso WHERE codigo = :c", Map.of("c", codigo), Long.class));
    }

    private Map<String, Object> rol(String codigo) {
        return jdbc.queryForMap("SELECT * FROM rol WHERE codigo = :c", Map.of("c", codigo));
    }

    private List<String> permisosDe(String codigo) {
        return jdbc.queryForList("SELECT p.codigo FROM rol_permiso rp JOIN rol r ON r.id = rp.rol_id "
                + "JOIN permiso p ON p.id = rp.permiso_id WHERE r.codigo = :c ORDER BY p.codigo", Map.of("c", codigo), String.class);
    }

    private long crearProcurador() throws Exception {
        mvc.perform(post("/admin/roles").with(admin).with(csrf())
                        .param("codigo", " procurador ").param("nombre", "Procurador")
                        .param("descripcion", "Sigue los expedientes en tribunales")
                        .param("permisos", permiso("EXPEDIENTES_VER")).param("permisos", permiso("AGENDA_VER")))
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
    void altaNormalizaElCodigoYGuardaLosPermisos() throws Exception {
        long id = crearProcurador();
        assertThat(rol("PROCURADOR").get("creado_por")).isEqualTo(adminId);
        assertThat(permisosDe("PROCURADOR")).containsExactly("AGENDA_VER", "EXPEDIENTES_VER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE tabla = 'rol' AND accion = 'ALTA' AND registro_id = :id",
                Map.of("id", id), Integer.class)).isEqualTo(1);
        mvc.perform(get("/admin/roles/" + id).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ningún usuario activo tiene este rol.")));
    }

    @Test
    void altaRechazaDatosInvalidos() throws Exception {
        mvc.perform(post("/admin/roles").with(admin).with(csrf()).param("codigo", "abogado").param("nombre", "Asistente"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ya existe un rol con ese código.")))
                .andExpect(content().string(Matchers.containsString("Ya existe un rol con ese nombre.")))
                .andExpect(content().string(Matchers.containsString("Elija al menos un permiso.")));
        mvc.perform(post("/admin/roles").with(admin).with(csrf()).param("codigo", "1X").param("permisos", "999999"))
                .andExpect(content().string(Matchers.containsString("El código debe tener de 3 a 30 caracteres")))
                .andExpect(content().string(Matchers.containsString("Nombre es requerido.")))
                .andExpect(content().string(Matchers.containsString("Uno de los permisos elegidos no existe.")));
    }

    @Test
    void modificarCambiaPermisosPeroNoElCodigo() throws Exception {
        long id = crearProcurador();
        mvc.perform(post("/admin/roles/" + id).with(admin).with(csrf()).param("codigo", "OTRO")
                        .param("nombre", "Procurador judicial").param("permisos", permiso("CLIENTES_VER")).param("activo", "true"))
                .andExpect(redirectedUrlPattern("/admin/roles?ok=*"));
        assertThat(rol("PROCURADOR").get("nombre")).isEqualTo("PROCURADOR JUDICIAL");
        assertThat(permisosDe("PROCURADOR")).containsExactly("CLIENTES_VER");
    }

    @Test
    void elAdministradorConservaTodosLosPermisosYNoSeDaDeBaja() throws Exception {
        long id = ((Number) rol("ADMINISTRADOR").get("id")).longValue();
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM permiso", Map.of(), Integer.class);
        mvc.perform(post("/admin/roles/" + id).with(admin).with(csrf()).param("nombre", "Administrador general")
                        .param("_activo", "on"))
                .andExpect(redirectedUrlPattern("/admin/roles?ok=*"));
        assertThat(rol("ADMINISTRADOR").get("activo")).isEqualTo(true);
        assertThat(permisosDe("ADMINISTRADOR")).hasSize(total);
        mvc.perform(post("/admin/roles/" + id + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/roles?error=*"));
        assertThat(rol("ADMINISTRADOR").get("activo")).isEqualTo(true);
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
