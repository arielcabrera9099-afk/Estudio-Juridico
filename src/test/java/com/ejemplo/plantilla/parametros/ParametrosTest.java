package com.ejemplo.plantilla.parametros;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;
import com.ejemplo.plantilla.datos.Parametros;

/** Parámetros: consulta y modificación con la regla de cada clave (cada prueba se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ParametrosTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private Parametros parametros;

    private long adminId;
    private RequestPostProcessor admin;

    @BeforeEach
    void sesion() {
        adminId = Sesiones.crearUsuario(jdbc, "prueba_admin", "ADMINISTRADOR", false);
        admin = Sesiones.como(adminId, "prueba_admin", Sesiones.TODOS);
    }

    private long id(String clave) {
        return jdbc.queryForObject("SELECT id FROM parametro WHERE clave = :c", Map.of("c", clave), Long.class);
    }

    private ResultActions guardar(String clave, String valor) throws Exception {
        return mvc.perform(post("/admin/parametros/" + id(clave)).with(admin).with(csrf()).param("valor", valor));
    }

    @Test
    void laListaFiltraPorGrupo() throws Exception {
        mvc.perform(get("/admin/parametros").param("grupo", "seguridad").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("seguridad.max_intentos")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("agenda.dias_aviso"))))
                .andExpect(content().string(Matchers.containsString("2 parámetros")));
        mvc.perform(get("/admin/parametros/" + id("agenda.dias_aviso")).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Números de 0 a 60")));
    }

    @Test
    void modificarRigeDeInmediatoYSeAudita() throws Exception {
        guardar("seguridad.max_intentos", " 7 ").andExpect(redirectedUrlPattern("/admin/parametros?ok=*"));
        assertThat(parametros.numero("seguridad.max_intentos", 5)).isEqualTo(7);
        Map<String, Object> p = jdbc.queryForMap("SELECT * FROM parametro WHERE clave = 'seguridad.max_intentos'", Map.of());
        assertThat(p.get("modificado_por")).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE tabla = 'parametro' AND registro_id = :id "
                + "AND datos_nuevos LIKE '%\"7\"%'", Map.of("id", p.get("id")), Integer.class)).isEqualTo(1);
    }

    @Test
    void losNumerosRespetanSuRango() throws Exception {
        guardar("seguridad.max_intentos", "1").andExpect(content().string(Matchers.containsString("entre 3 y 20")));
        guardar("seguridad.minutos_bloqueo", "abc").andExpect(content().string(Matchers.containsString("Ingrese un número entero.")));
        guardar("documentos.tamano_max_mb", "").andExpect(content().string(Matchers.containsString("Valor es requerido.")));
        assertThat(parametros.numero("seguridad.max_intentos", -1)).isEqualTo(5);
    }

    @Test
    void lasListasSeNormalizanYValidan() throws Exception {
        guardar("agenda.dias_aviso", "1, 7,0 ,3").andExpect(redirectedUrlPattern("/admin/parametros?ok=*"));
        assertThat(parametros.texto("agenda.dias_aviso", "")).isEqualTo("7,3,1,0");
        guardar("agenda.dias_aviso", "3,3").andExpect(content().string(Matchers.containsString("Hay días repetidos.")));
        guardar("agenda.dias_aviso", "90").andExpect(content().string(Matchers.containsString("entre 0 y 60")));

        guardar("documentos.tipos_permitidos", ".PDF, docx").andExpect(redirectedUrlPattern("/admin/parametros?ok=*"));
        assertThat(parametros.texto("documentos.tipos_permitidos", "")).isEqualTo("pdf,docx");
        guardar("documentos.tipos_permitidos", "pdf,exe").andExpect(content().string(Matchers.containsString("No se aceptan archivos ejecutables: exe.")));
    }

    @Test
    void elFormatoDeNumeroLlevaUnaSolaSerieDeN() throws Exception {
        guardar("expediente.formato_numero", "exp/aaaa/nnnnn").andExpect(redirectedUrlPattern("/admin/parametros?ok=*"));
        assertThat(parametros.texto("expediente.formato_numero", "")).isEqualTo("EXP/AAAA/NNNNN");
        guardar("expediente.formato_numero", "AAAA-NN").andExpect(content().string(Matchers.containsString("una sola serie de 3 a 8 N")));
        guardar("expediente.formato_numero", "NNN-NNN").andExpect(content().string(Matchers.containsString("una sola serie de 3 a 8 N")));
        guardar("expediente.formato_numero", "AAAA NNNN").andExpect(content().string(Matchers.containsString("sin espacios")));
    }

    @Test
    void sinPermisoDeSeguridadNoEntra() throws Exception {
        long abogado = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        mvc.perform(get("/admin/parametros").with(Sesiones.como(abogado, "prueba_abogado", Sesiones.ASISTENTE)))
                .andExpect(status().isForbidden());
    }
}
