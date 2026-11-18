package com.ejemplo.plantilla.auditoria;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;

/** Consulta de la auditoría contra la base real (cada prueba se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class AuditoriaTest {

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

    private long ultimo() {
        return jdbc.queryForObject("SELECT MAX(id) FROM auditoria", Map.of(), Long.class);
    }

    @Test
    void unCambioApareceEnLaListaYSuDetalleComparaLosValores() throws Exception {
        long parametro = jdbc.queryForObject("SELECT id FROM parametro WHERE clave = 'seguridad.minutos_bloqueo'", Map.of(), Long.class);
        String antes = jdbc.queryForObject("SELECT valor FROM parametro WHERE id = :id", Map.of("id", parametro), String.class);
        String nuevo = "15".equals(antes) ? "20" : "15";
        mvc.perform(post("/admin/parametros/" + parametro).with(admin).with(csrf()).param("valor", nuevo));
        long id = ultimo();

        mvc.perform(get("/admin/auditoria").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/admin/auditoria/" + id)))
                .andExpect(content().string(Matchers.containsString("MODIFICACIÓN")));
        mvc.perform(get("/admin/auditoria").param("usuario", String.valueOf(adminId)).param("tabla", "parametro")
                        .param("registro", String.valueOf(parametro)).param("desde", "").param("hasta", "").with(admin))
                .andExpect(content().string(Matchers.containsString("1 registro")));
        mvc.perform(get("/admin/auditoria/" + id).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("<strong>" + nuevo + "</strong>")))
                .andExpect(content().string(Matchers.containsString("/admin/parametros/" + parametro)));
    }

    @Test
    void validaLosFiltros() throws Exception {
        mvc.perform(get("/admin/auditoria").param("desde", "2026-05-10").param("hasta", "2026-05-01").with(admin))
                .andExpect(content().string(Matchers.containsString("La fecha desde no puede ser posterior a la fecha hasta.")));
        mvc.perform(get("/admin/auditoria").param("registro", "abc").with(admin))
                .andExpect(content().string(Matchers.containsString("El número de registro lleva solo dígitos.")));
        mvc.perform(get("/admin/auditoria").param("desde", "1990-01-01").param("hasta", "1990-01-02").with(admin))
                .andExpect(content().string(Matchers.containsString("No hay registros con esos filtros.")));
    }

    @Test
    void elDetalleDeUnRegistroInexistenteDa404() throws Exception {
        mvc.perform(get("/admin/auditoria/999999999").with(admin)).andExpect(status().isNotFound());
    }

    @Test
    void sinPermisoDeAuditoriaNoEntra() throws Exception {
        long abogado = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        mvc.perform(get("/admin/auditoria").with(Sesiones.como(abogado, "prueba_abogado", Sesiones.ASISTENTE)))
                .andExpect(status().isForbidden());
    }
}
