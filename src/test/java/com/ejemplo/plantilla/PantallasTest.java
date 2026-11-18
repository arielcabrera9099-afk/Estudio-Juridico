package com.ejemplo.plantilla;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Layout, acceso y menú. Usan la base de DB_URL (SQL Server con las migraciones aplicadas); sin esa variable se
 * saltean. Cada prueba se deshace al terminar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PantallasTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void sinSesionLlevaAlLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/admin/usuarios")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginSeMuestraConCsrf() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Acceso al Sistema")))
                .andExpect(content().string(Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void recursosEstaticosSonPublicos() throws Exception {
        for (String r : new String[] {"/css/tokens/tokens.css", "/css/tema/tema.css", "/css/componentes/pantalla.css",
                "/js/layout/shell.js", "/js/componentes/componentes.js", "/js/componentes/abm.js",
                "/fonts/Poppins-Regular.ttf", "/img/favicon.svg"}) {
            mvc.perform(get(r)).andExpect(status().isOk());
        }
    }

    @Test
    void elAdministradorVeTodoElMenu() throws Exception {
        long id = Sesiones.crearUsuario(jdbc, "prueba_admin", "ADMINISTRADOR", false);
        mvc.perform(get("/").with(Sesiones.como(id, "prueba_admin", Sesiones.TODOS)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"admin-shell\"")))
                .andExpect(content().string(Matchers.containsString("PRUEBA_ADMIN")))
                .andExpect(content().string(Matchers.containsString("data-titulo=\"Usuarios\"")))
                .andExpect(content().string(Matchers.containsString("data-titulo=\"Casos activos y cerrados\"")));
    }

    @Test
    void elAsistenteNoVeAdministracionNiReportes() throws Exception {
        long id = Sesiones.crearUsuario(jdbc, "prueba_asis", "ASISTENTE", false);
        mvc.perform(get("/").with(Sesiones.como(id, "prueba_asis", Sesiones.ASISTENTE)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-titulo=\"Expedientes\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("data-titulo=\"Usuarios\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("data-titulo=\"Casos activos y cerrados\""))));
        mvc.perform(get("/admin/usuarios").with(Sesiones.como(id, "prueba_asis", Sesiones.ASISTENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void todasLasOpcionesDelMenuAbrenSuPantalla() throws Exception {
        long id = Sesiones.crearUsuario(jdbc, "prueba_admin", "ADMINISTRADOR", false);
        for (String ruta : jdbc.queryForList("SELECT ruta FROM menu WHERE ruta IS NOT NULL AND activo = 1", java.util.Map.of(), String.class)) {
            mvc.perform(get(ruta).with(Sesiones.como(id, "prueba_admin", Sesiones.TODOS)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.not(Matchers.containsString("Pantalla en construcción"))))
                    // Las grillas no se navegan con clics: se ve cada registro con Visualizar (los reportes sí enlazan al detalle).
                    .andExpect(content().string(ruta.startsWith("/reportes") ? Matchers.anything() : Matchers.not(Matchers.containsString("class=\"gx-enlace\""))));
        }
    }

    @Test
    void catalogoDeComponentesParaAdministradores() throws Exception {
        long id = Sesiones.crearUsuario(jdbc, "prueba_admin", "ADMINISTRADOR", false);
        mvc.perform(get("/componentes").with(Sesiones.como(id, "prueba_admin", Sesiones.TODOS)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("gx-grilla")));
    }
}
