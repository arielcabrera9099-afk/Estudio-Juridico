package com.ejemplo.plantilla.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;

/** Ingreso, bloqueo por intentos, cambio de clave obligatorio y salida, contra la base real (se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class IngresoTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;

    private long id;

    @BeforeEach
    void crearUsuario() {
        id = Sesiones.crearUsuario(jdbc, "prueba_ingreso", "ABOGADO", false);
    }

    private int auditorias(String accion) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE usuario_id = :id AND accion = :a",
                Map.of("id", id, "a", accion), Integer.class);
    }

    @Test
    void ingresoCorrectoRegistraAccesoYAuditoria() throws Exception {
        mvc.perform(formLogin("/login").user("PRUEBA_INGRESO").password(Sesiones.CLAVE))
                .andExpect(redirectedUrl("/"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE id = :id AND ultimo_acceso IS NOT NULL",
                Map.of("id", id), Integer.class)).isEqualTo(1);
        assertThat(auditorias("INGRESO")).isEqualTo(1);
    }

    @Test
    void claveIncorrectaNoIngresaYSeAudita() throws Exception {
        mvc.perform(formLogin("/login").user("prueba_ingreso").password("otra"))
                .andExpect(redirectedUrl("/login?error=credenciales"));
        mvc.perform(formLogin("/login").user("no_existe").password("otra"))
                .andExpect(redirectedUrl("/login?error=credenciales"));
        assertThat(auditorias("INGRESO_FALLIDO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id = :id", Map.of("id", id), Integer.class))
                .isEqualTo(1);
    }

    @Test
    void cincoIntentosFallidosBloqueanAunConLaClaveCorrecta() throws Exception {
        for (int i = 1; i <= 4; i++) {
            mvc.perform(formLogin("/login").user("prueba_ingreso").password("mal" + i))
                    .andExpect(redirectedUrl("/login?error=credenciales"));
        }
        mvc.perform(formLogin("/login").user("prueba_ingreso").password("mal5"))
                .andExpect(redirectedUrl("/login?error=bloqueado"));
        mvc.perform(formLogin("/login").user("prueba_ingreso").password(Sesiones.CLAVE))
                .andExpect(redirectedUrl("/login?error=bloqueado"));
    }

    @Test
    void usuarioDadoDeBajaNoIngresa() throws Exception {
        jdbc.update("UPDATE usuario SET activo = 0 WHERE id = :id", Map.of("id", id));
        mvc.perform(formLogin("/login").user("prueba_ingreso").password(Sesiones.CLAVE))
                .andExpect(redirectedUrl("/login?error=inactivo"));
    }

    @Test
    void conClaveProvisoriaDebeCambiarlaAntesDeSeguir() throws Exception {
        jdbc.update("UPDATE usuario SET debe_cambiar_clave = 1 WHERE id = :id", Map.of("id", id));
        MvcResult r = mvc.perform(formLogin("/login").user("prueba_ingreso").password(Sesiones.CLAVE))
                .andExpect(redirectedUrl("/cambiar-clave")).andReturn();
        MockHttpSession sesion = (MockHttpSession) r.getRequest().getSession(false);

        mvc.perform(get("/clientes").session(sesion)).andExpect(redirectedUrl("/cambiar-clave"));
        mvc.perform(get("/cambiar-clave").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("La que tiene ahora es provisoria")));

        // Reglas: la actual tiene que coincidir, la nueva cumplir las reglas y repetirse igual.
        mvc.perform(post("/cambiar-clave").session(sesion).with(csrf())
                        .param("actual", "incorrecta").param("nueva", "corta").param("repetir", "otra"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("La contraseña actual no es correcta.")))
                .andExpect(content().string(Matchers.containsString("al menos 8 caracteres")))
                .andExpect(content().string(Matchers.containsString("Las contraseñas no coinciden.")));
        mvc.perform(post("/cambiar-clave").session(sesion).with(csrf())
                        .param("actual", Sesiones.CLAVE).param("nueva", "prueba_ingreso99").param("repetir", "prueba_ingreso99"))
                .andExpect(content().string(Matchers.containsString("no puede contener el nombre de usuario")));

        mvc.perform(post("/cambiar-clave").session(sesion).with(csrf())
                        .param("actual", Sesiones.CLAVE).param("nueva", "NuevaClave2026").param("repetir", "NuevaClave2026"))
                .andExpect(status().is3xxRedirection());
        String hash = jdbc.queryForObject("SELECT clave_hash FROM usuario WHERE id = :id", Map.of("id", id), String.class);
        assertThat(encoder.matches("NuevaClave2026", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT debe_cambiar_clave FROM usuario WHERE id = :id", Map.of("id", id), Boolean.class))
                .isFalse();
        mvc.perform(get("/clientes").session(sesion)).andExpect(status().isOk());
    }

    @Test
    void salirCierraLaSesionYSeAudita() throws Exception {
        MvcResult r = mvc.perform(formLogin("/login").user("prueba_ingreso").password(Sesiones.CLAVE)).andReturn();
        MockHttpSession sesion = (MockHttpSession) r.getRequest().getSession(false);
        mvc.perform(post("/salir").session(sesion).with(csrf())).andExpect(redirectedUrl("/login?salio"));
        assertThat(auditorias("SALIDA")).isEqualTo(1);
    }
}
