package com.ejemplo.plantilla;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Las pantallas de la plantilla se arman sin errores con el layout y sus recursos. */
@SpringBootTest
@AutoConfigureMockMvc
class PantallasTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void inicioMuestraElLayoutVacio() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("id=\"admin-shell\"")))
                .andExpect(content().string(Matchers.containsString("<div id=\"contenido\" class=\"gx-pantalla\"></div>")));
    }

    @Test
    void catalogoMuestraLosComponentes() throws Exception {
        mvc.perform(get("/componentes"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("gx-grilla")))
                .andExpect(content().string(Matchers.containsString("suite-kpi")));
    }

    @Test
    void loginSeMuestra() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Acceso al Sistema")));
    }

    @Test
    void recursosEstaticosExisten() throws Exception {
        for (String r : new String[] {"/css/tokens/tokens.css", "/css/tema/tema.css", "/css/componentes/pantalla.css",
                "/js/layout/shell.js", "/js/componentes/componentes.js", "/fonts/Poppins-Regular.ttf", "/img/favicon.svg"}) {
            mvc.perform(get(r)).andExpect(status().isOk());
        }
    }
}
