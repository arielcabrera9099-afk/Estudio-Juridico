package com.ejemplo.plantilla.catalogos;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;

/** Catálogos: el ABM genérico y las reglas de cada catálogo, contra la base real (cada prueba se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class CatalogosTest {

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

    private long id(String tabla, String codigo) {
        return jdbc.queryForObject("SELECT id FROM " + tabla + " WHERE codigo = :c", Map.of("c", codigo), Long.class);
    }

    @Test
    void cadaSeccionMuestraSuListaYElCatalogoElegido() throws Exception {
        mvc.perform(get("/admin/materias").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("NINEZ")));
        mvc.perform(get("/admin/materias").param("catalogo", "tipo_proceso")
                        .param("f_materia_id", String.valueOf(id("materia", "PENAL"))).with(admin))
                .andExpect(content().string(Matchers.containsString("Acción penal pública")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Divorcio"))));
        mvc.perform(get("/admin/tipos").param("catalogo", "tipo_evento").with(admin))
                .andExpect(content().string(Matchers.containsString("var(--wwp-danger)")));
        mvc.perform(get("/admin/estados").with(admin)).andExpect(content().string(Matchers.containsString("CON_SENTENCIA")));
        mvc.perform(get("/admin/juzgados").param("catalogo", "no_existe").with(admin)).andExpect(status().isNotFound());
    }

    @Test
    void altaArmaElCodigoConElNombreYAudita() throws Exception {
        mvc.perform(post("/admin/materias/materia").with(admin).with(csrf()).param("nombre", "  Derecho   Tributario "))
                .andExpect(redirectedUrlPattern("/admin/materias?catalogo=materia&ok=*"));
        long id = id("materia", "DERECHO_TRIBUTARIO");
        assertThat(jdbc.queryForObject("SELECT nombre FROM materia WHERE id = :id", Map.of("id", id), String.class))
                .isEqualTo("Derecho Tributario");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE tabla = 'materia' AND accion = 'ALTA' AND registro_id = :id",
                Map.of("id", id), Integer.class)).isEqualTo(1);
        // Con el mismo nombre en mayúsculas el código ya existe: se rechaza el nombre repetido.
        mvc.perform(post("/admin/materias/materia").with(admin).with(csrf()).param("nombre", "Penal"))
                .andExpect(content().string(Matchers.containsString("Ya existe una materia con ese nombre.")));
        mvc.perform(post("/admin/materias/materia").with(admin).with(csrf()).param("codigo", "civil").param("nombre", "Otra"))
                .andExpect(content().string(Matchers.containsString("Ya existe una materia con ese código.")));
    }

    @Test
    void juzgadoValidaReferenciasTelefonoYUnicidad() throws Exception {
        String capital = String.valueOf(id("circunscripcion", "CAPITAL"));
        String civil = String.valueOf(id("materia", "CIVIL"));
        mvc.perform(post("/admin/juzgados/juzgado").with(admin).with(csrf())
                        .param("nombre", "Juzgado de Primera Instancia en lo Civil y Comercial")
                        .param("circunscripcion_id", capital).param("materia_id", civil).param("turno", "Primer turno")
                        .param("telefono", "021 123 456"))
                .andExpect(redirectedUrlPattern("/admin/juzgados?catalogo=juzgado&ok=*"));
        mvc.perform(post("/admin/juzgados/juzgado").with(admin).with(csrf())
                        .param("nombre", "Juzgado de Primera Instancia en lo Civil y Comercial")
                        .param("circunscripcion_id", capital).param("materia_id", civil).param("turno", "Primer turno"))
                .andExpect(content().string(Matchers.containsString("Ya existe un juzgado con ese nombre y turno en la circunscripción.")));
        mvc.perform(post("/admin/juzgados/juzgado").with(admin).with(csrf())
                        .param("nombre", "Juzgado de Primera Instancia en lo Civil y Comercial")
                        .param("circunscripcion_id", capital).param("materia_id", civil).param("turno", "Segundo turno"))
                .andExpect(redirectedUrlPattern("/admin/juzgados?*"));
        mvc.perform(post("/admin/juzgados/juzgado").with(admin).with(csrf()).param("nombre", "X")
                        .param("circunscripcion_id", "999999").param("telefono", "abc"))
                .andExpect(content().string(Matchers.containsString("Elija la circunscripción de la lista.")))
                .andExpect(content().string(Matchers.containsString("Materia es requerido.")))
                .andExpect(content().string(Matchers.containsString("El teléfono solo admite números")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM juzgado", Map.of(), Integer.class)).isEqualTo(2);
    }

    @Test
    void tipoDeProcesoNoCambiaDeMateriaYNoSeReactivaSinSuMateria() throws Exception {
        long tipo = id("tipo_proceso", "FAMILIA_DIVORCIO");
        long familia = id("materia", "FAMILIA");
        mvc.perform(post("/admin/materias/tipo_proceso/" + tipo).with(admin).with(csrf())
                        .param("nombre", "Divorcio vincular").param("materia_id", String.valueOf(id("materia", "PENAL"))))
                .andExpect(redirectedUrlPattern("/admin/materias?*ok=*"));
        Map<String, Object> t = jdbc.queryForMap("SELECT * FROM tipo_proceso WHERE id = :id", Map.of("id", tipo));
        assertThat(t.get("nombre")).isEqualTo("Divorcio vincular");
        assertThat(t.get("materia_id")).isEqualTo(familia);

        mvc.perform(post("/admin/materias/tipo_proceso/" + tipo + "/baja").with(admin).with(csrf()));
        mvc.perform(post("/admin/materias/materia/" + familia + "/baja").with(admin).with(csrf()));
        mvc.perform(post("/admin/materias/tipo_proceso/" + tipo + "/reactivar").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/materias?*error=*"));
        mvc.perform(post("/admin/materias/materia/" + familia + "/reactivar").with(admin).with(csrf()));
        mvc.perform(post("/admin/materias/tipo_proceso/" + tipo + "/reactivar").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/materias?*ok=*"));
        assertThat(jdbc.queryForObject("SELECT activo FROM tipo_proceso WHERE id = :id", Map.of("id", tipo), Boolean.class)).isTrue();
    }

    @Test
    void losEstadosConservanUnoActivoYUnoDeCierre() throws Exception {
        mvc.perform(post("/admin/estados/estado_expediente").with(admin).with(csrf()).param("nombre", "Raro")
                        .param("orden", "9").param("es_activo", "true").param("es_cierre", "true"))
                .andExpect(content().string(Matchers.containsString("Un estado de cierre no puede contar como caso activo.")));
        // Se dan de baja todos los de cierre menos uno: ese ya no se puede bajar ni dejar de ser de cierre.
        long cerrado = id("estado_expediente", "CERRADO");
        mvc.perform(post("/admin/estados/estado_expediente/" + id("estado_expediente", "ARCHIVADO") + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/estados?*ok=*"));
        mvc.perform(post("/admin/estados/estado_expediente/" + cerrado + "/baja").with(admin).with(csrf()))
                .andExpect(redirectedUrlPattern("/admin/estados?*error=*"));
        mvc.perform(post("/admin/estados/estado_expediente/" + cerrado).with(admin).with(csrf())
                        .param("nombre", "Cerrado").param("orden", "5"))
                .andExpect(content().string(Matchers.containsString("Es el único estado de cierre")));
        mvc.perform(post("/admin/estados/estado_expediente/" + cerrado).with(admin).with(csrf())
                        .param("nombre", "Cerrado").param("orden", "1000").param("es_cierre", "true"))
                .andExpect(content().string(Matchers.containsString("Orden debe estar entre 0 y 999.")));
    }

    @Test
    void tipoDeEventoValidaColorYDias() throws Exception {
        mvc.perform(post("/admin/tipos/tipo_evento").with(admin).with(csrf()).param("nombre", "Mediación")
                        .param("color", "rojo-fuego").param("aviso_dias_defecto", "90"))
                .andExpect(content().string(Matchers.containsString("Elija una opción de la lista.")))
                .andExpect(content().string(Matchers.containsString("Días de aviso debe estar entre 0 y 60.")));
        mvc.perform(post("/admin/tipos/tipo_evento").with(admin).with(csrf()).param("nombre", "Mediación")
                        .param("color", "wwp-success").param("es_plazo", "true").param("aviso_dias_defecto", "2"))
                .andExpect(redirectedUrlPattern("/admin/tipos?catalogo=tipo_evento&ok=*"));
        Map<String, Object> t = jdbc.queryForMap("SELECT * FROM tipo_evento WHERE codigo = 'MEDIACION'", Map.of());
        assertThat(t.get("es_plazo")).isEqualTo(true);
        assertThat(((Number) t.get("aviso_dias_defecto")).intValue()).isEqualTo(2);
    }

    @Test
    void sinPermisoDeCatalogosNoEntra() throws Exception {
        long abogado = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        mvc.perform(get("/admin/juzgados").with(Sesiones.como(abogado, "prueba_abogado", Sesiones.ASISTENTE)))
                .andExpect(status().isForbidden());
    }
}
