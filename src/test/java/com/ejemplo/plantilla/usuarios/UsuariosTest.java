package com.ejemplo.plantilla.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;

/** ABM de usuarios contra la base real (cada prueba se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class UsuariosTest {

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

    private long rol(String codigo) {
        return jdbc.queryForObject("SELECT id FROM rol WHERE codigo = :c", Map.of("c", codigo), Long.class);
    }

    private MockHttpServletRequestBuilder alta() {
        return post("/admin/usuarios").with(admin).with(csrf())
                .param("nombreUsuario", " JPerez ").param("email", "JPEREZ@estudio.py")
                .param("nombres", "Juan  Carlos").param("apellidos", "Pérez").param("telefono", "0981 123 456")
                .param("matricula", "12345").param("roles", String.valueOf(rol("ABOGADO")))
                .param("clave", "Inicial2026").param("claveRepetir", "Inicial2026");
    }

    private Map<String, Object> usuario(String nombre) {
        return jdbc.queryForMap("SELECT * FROM usuario WHERE nombre_usuario = :u", Map.of("u", nombre));
    }

    @Test
    void laListaFiltraPorTextoYEstado() throws Exception {
        mvc.perform(get("/admin/usuarios").param("texto", "prueba_admin").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("prueba_admin")))
                .andExpect(content().string(Matchers.containsString("1 usuario")));
        mvc.perform(get("/admin/usuarios").param("texto", "prueba_admin").param("estado", "INACTIVOS").with(admin))
                .andExpect(content().string(Matchers.containsString("No hay usuarios con esos filtros.")));
    }

    @Test
    void altaNormalizaYGuardaConClaveProvisoria() throws Exception {
        mvc.perform(alta()).andExpect(redirectedUrlPattern("/admin/usuarios?ok=*"));
        Map<String, Object> u = usuario("jperez");
        assertThat(u.get("email")).isEqualTo("jperez@estudio.py");
        assertThat(u.get("nombres")).isEqualTo("JUAN CARLOS");
        assertThat(u.get("debe_cambiar_clave")).isEqualTo(true);
        assertThat(u.get("creado_por")).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario_rol WHERE usuario_id = :id",
                Map.of("id", u.get("id")), Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE tabla = 'usuario' AND accion = 'ALTA' "
                + "AND registro_id = :id AND datos_nuevos NOT LIKE '%clave%'", Map.of("id", u.get("id")), Integer.class)).isEqualTo(1);
    }

    @Test
    void altaRechazaDatosInvalidos() throws Exception {
        mvc.perform(post("/admin/usuarios").with(admin).with(csrf())
                        .param("nombreUsuario", "PRUEBA_ADMIN").param("email", "no-es-email")
                        .param("roles", String.valueOf(rol("ABOGADO")))
                        .param("clave", "corta").param("claveRepetir", "otra"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ya existe un usuario con ese nombre.")))
                .andExpect(content().string(Matchers.containsString("El email no es válido.")))
                .andExpect(content().string(Matchers.containsString("Nombres es requerido.")))
                .andExpect(content().string(Matchers.containsString("La matrícula es requerida para el rol Abogado.")))
                .andExpect(content().string(Matchers.containsString("al menos 8 caracteres")))
                .andExpect(content().string(Matchers.containsString("Las contraseñas no coinciden.")));
        mvc.perform(post("/admin/usuarios").with(admin).with(csrf()).param("nombreUsuario", "sin roles")
                        .param("email", "prueba_admin@prueba.local"))
                .andExpect(content().string(Matchers.containsString("Elija al menos un rol.")))
                .andExpect(content().string(Matchers.containsString("Ya existe un usuario con ese email.")))
                .andExpect(content().string(Matchers.containsString("El usuario debe tener de 3 a 30 caracteres")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = 'no-es-email'", Map.of(), Integer.class)).isZero();
    }

    @Test
    void modificarCambiaDatosPeroNoElNombreDeUsuario() throws Exception {
        mvc.perform(alta());
        long id = ((Number) usuario("jperez").get("id")).longValue();
        mvc.perform(get("/admin/usuarios/" + id).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("jperez@estudio.py")));
        mvc.perform(post("/admin/usuarios/" + id).with(admin).with(csrf())
                        .param("nombreUsuario", "otro").param("email", "juan@estudio.py").param("nombres", "Juan")
                        .param("apellidos", "Pérez").param("roles", String.valueOf(rol("ASISTENTE"))).param("activo", "true"))
                .andExpect(redirectedUrlPattern("/admin/usuarios?ok=*"));
        Map<String, Object> u = usuario("jperez");
        assertThat(u.get("email")).isEqualTo("juan@estudio.py");
        assertThat(u.get("matricula")).isNull();
        assertThat(u.get("modificado_por")).isEqualTo(adminId);
    }

    @Test
    void nadieSeDaDeBajaASiMismo() throws Exception {
        mvc.perform(post("/admin/usuarios/" + adminId + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/usuarios?error=*"));
        mvc.perform(post("/admin/usuarios/" + adminId).with(admin).with(csrf())
                        .param("email", "prueba_admin@prueba.local").param("nombres", "A").param("apellidos", "B")
                        .param("roles", String.valueOf(rol("ADMINISTRADOR"))).param("_activo", "on"))
                .andExpect(content().string(Matchers.containsString("No puede darse de baja a sí mismo.")));
        assertThat(usuario("prueba_admin").get("activo")).isEqualTo(true);
    }

    @Test
    void bajaReactivacionDesbloqueoYReinicioDeClave() throws Exception {
        mvc.perform(alta());
        long id = ((Number) usuario("jperez").get("id")).longValue();

        mvc.perform(post("/admin/usuarios/" + id + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/usuarios?ok=*"));
        assertThat(usuario("jperez").get("activo")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE accion = 'BAJA' AND registro_id = :id",
                Map.of("id", id), Integer.class)).isEqualTo(1);

        mvc.perform(post("/admin/usuarios/" + id + "/reactivar").with(admin).with(csrf()));
        assertThat(usuario("jperez").get("activo")).isEqualTo(true);

        jdbc.update("UPDATE usuario SET bloqueado_hasta = DATEADD(MINUTE, 10, SYSUTCDATETIME()), debe_cambiar_clave = 0 "
                + "WHERE id = :id", Map.of("id", id));
        mvc.perform(get("/admin/usuarios").param("estado", "BLOQUEADOS").with(admin))
                .andExpect(content().string(Matchers.containsString("BLOQUEADO")));
        mvc.perform(post("/admin/usuarios/" + id + "/desbloquear").with(admin).with(csrf()));
        assertThat(usuario("jperez").get("bloqueado_hasta")).isNull();

        mvc.perform(post("/admin/usuarios/" + id + "/clave").with(admin).with(csrf()).param("clave", "x").param("repetir", "x"))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("al menos 8 caracteres")));
        mvc.perform(post("/admin/usuarios/" + id + "/clave").with(admin).with(csrf())
                        .param("clave", "Provisoria99").param("repetir", "Provisoria99"))
                .andExpect(redirectedUrlPattern("/admin/usuarios?ok=*"));
        assertThat(usuario("jperez").get("debe_cambiar_clave")).isEqualTo(true);
    }

    @Test
    void unUsuarioInexistenteDa404() throws Exception {
        mvc.perform(get("/admin/usuarios/999999999").with(admin)).andExpect(status().isNotFound());
    }
}
