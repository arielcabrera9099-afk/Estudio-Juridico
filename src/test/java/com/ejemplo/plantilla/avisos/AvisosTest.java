package com.ejemplo.plantilla.avisos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;
import com.ejemplo.plantilla.datos.Filas;

/** Avisos (campana) y vencimientos, contra la base real (cada prueba se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class AvisosTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private AvisosServicio servicio;

    private long yo;
    private RequestPostProcessor sesion;
    private final LocalDateTime ahora = LocalDateTime.now(Filas.ZONA);

    @BeforeEach
    void preparar() {
        yo = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        sesion = Sesiones.como(yo, "prueba_abogado", Sesiones.TODOS);
    }

    private long evento(String titulo, String tipo, LocalDateTime inicio, String estado) {
        return jdbc.queryForObject("INSERT INTO evento_agenda (tipo_evento_id, titulo, fecha_inicio, responsable_id, estado) OUTPUT inserted.id "
                + "SELECT id, :titulo, :inicio, :yo, :estado FROM tipo_evento WHERE codigo = :tipo",
                new MapSqlParameterSource("titulo", titulo).addValue("inicio", Filas.momento(inicio)).addValue("yo", yo)
                        .addValue("estado", estado).addValue("tipo", tipo), Long.class);
    }

    private long recordatorioVencido(long evento) {
        return jdbc.queryForObject("INSERT INTO recordatorio (evento_agenda_id, usuario_id, fecha_aviso) OUTPUT inserted.id "
                + "VALUES (:e, :yo, :aviso)", new MapSqlParameterSource("e", evento).addValue("yo", yo)
                .addValue("aviso", Filas.momento(ahora.minusMinutes(5))), Long.class);
    }

    private String estado(long recordatorio) {
        return jdbc.queryForObject("SELECT estado FROM recordatorio WHERE id = :id", Map.of("id", recordatorio), String.class);
    }

    @Test
    void laTareaConvierteLosRecordatoriosEnAvisos() {
        long vence = evento("Contestar demanda", "VENCIMIENTO", ahora.plusDays(2), "PENDIENTE");
        long r1 = recordatorioVencido(vence);
        long cancelado = evento("Reunión cancelada", "REUNION", ahora.plusDays(1), "CANCELADO");
        long r2 = recordatorioVencido(cancelado);

        assertThat(servicio.procesarPendientes()).isGreaterThanOrEqualTo(1);
        assertThat(estado(r1)).isEqualTo("ENVIADO");
        assertThat(estado(r2)).isEqualTo("ANULADO");
        Map<String, Object> n = jdbc.queryForMap("SELECT * FROM notificacion WHERE usuario_id = :yo", Map.of("yo", yo));
        assertThat(n.get("titulo")).isEqualTo("Vencimiento en 2 días: Contestar demanda");
        assertThat(n.get("tipo")).isEqualTo("VENCIMIENTO");
        assertThat(n.get("referencia_id")).isEqualTo(vence);
        // Una segunda pasada no vuelve a avisar.
        servicio.procesarPendientes();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE usuario_id = :yo", Map.of("yo", yo), Integer.class)).isEqualTo(1);
    }

    @Test
    void textosDeCuando() {
        LocalDate hoy = LocalDate.of(2026, 11, 18);
        assertThat(AvisosServicio.cuando(hoy, hoy, true)).isEqualTo("hoy");
        assertThat(AvisosServicio.cuando(hoy, hoy.plusDays(1), true)).isEqualTo("mañana");
        assertThat(AvisosServicio.cuando(hoy, hoy.plusDays(3), false)).isEqualTo("en 3 días");
        assertThat(AvisosServicio.cuando(hoy, hoy.minusDays(1), true)).isEqualTo("venció ayer");
        assertThat(AvisosServicio.cuando(hoy, hoy.minusDays(4), true)).isEqualTo("venció hace 4 días");
    }

    @Test
    void campanaListaYAbrir() throws Exception {
        long ev = evento("Audiencia de prueba", "AUDIENCIA", ahora.plusDays(1), "PENDIENTE");
        recordatorioVencido(ev);
        servicio.procesarPendientes();
        long aviso = jdbc.queryForObject("SELECT id FROM notificacion WHERE usuario_id = :yo", Map.of("yo", yo), Long.class);

        mvc.perform(get("/agenda").with(sesion)).andExpect(content().string(Matchers.containsString("data-campana")))
                .andExpect(content().string(Matchers.containsString("Avisos: 1 sin leer")));
        mvc.perform(get("/notificaciones/contador").with(sesion)).andExpect(jsonPath("$.noLeidas").value(1));
        mvc.perform(get("/notificaciones/panel").with(sesion)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Audiencia mañana: Audiencia de prueba")));
        mvc.perform(get("/notificaciones").with(sesion)).andExpect(content().string(Matchers.containsString("1 aviso")));

        // Otro usuario no puede abrir un aviso ajeno.
        long otro = Sesiones.crearUsuario(jdbc, "prueba_otro", "ASISTENTE", false);
        mvc.perform(get("/notificaciones/" + aviso + "/abrir").with(Sesiones.como(otro, "prueba_otro", Sesiones.ASISTENTE)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/notificaciones/" + aviso + "/abrir").with(sesion)).andExpect(redirectedUrl("/agenda/" + ev));
        mvc.perform(get("/notificaciones/contador").with(sesion)).andExpect(jsonPath("$.noLeidas").value(0));
        mvc.perform(get("/notificaciones").with(sesion)).andExpect(content().string(Matchers.containsString("No hay avisos con esos filtros.")));
    }

    @Test
    void marcarTodosLeidos() throws Exception {
        recordatorioVencido(evento("Uno", "VENCIMIENTO", ahora.plusDays(3), "PENDIENTE"));
        recordatorioVencido(evento("Dos", "VENCIMIENTO", ahora.plusDays(4), "PENDIENTE"));
        servicio.procesarPendientes();
        mvc.perform(post("/notificaciones/leer-todas").with(sesion).with(csrf()));
        assertThat(servicio.noLeidas(yo)).isZero();
    }

    @Test
    void vencimientos() throws Exception {
        evento("Plazo vencido", "VENCIMIENTO", ahora.minusDays(1), "PENDIENTE");
        evento("Plazo de hoy", "VENCIMIENTO", ahora.toLocalDate().atTime(23, 0), "PENDIENTE");
        evento("Plazo lejano", "VENCIMIENTO", ahora.plusDays(20), "PENDIENTE");
        evento("Audiencia mañana", "AUDIENCIA", ahora.plusDays(1), "PENDIENTE");
        mvc.perform(get("/agenda/vencimientos").param("responsable", String.valueOf(yo)).with(sesion)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("VENCIÓ AYER")))
                .andExpect(content().string(Matchers.containsString("VENCE HOY")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Plazo lejano"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Audiencia mañana"))))
                .andExpect(content().string(Matchers.containsString("Hay 1 plazo vencido")));
        mvc.perform(get("/agenda/vencimientos").param("responsable", String.valueOf(yo)).param("horizonte", "30").with(sesion))
                .andExpect(content().string(Matchers.containsString("Plazo lejano")));
        mvc.perform(get("/agenda/vencimientos").param("responsable", String.valueOf(yo)).param("horizonte", "VENCIDOS").with(sesion))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Plazo de hoy"))));
    }
}
