package com.ejemplo.plantilla.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.ejemplo.plantilla.datos.Filas;

/** Agenda: alta con avisos, validaciones, estados, calendario y relación con el expediente (contra la base real). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class AgendaTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private long yo;
    private RequestPostProcessor sesion;
    private final LocalDate hoy = LocalDate.now(Filas.ZONA);

    @BeforeEach
    void preparar() {
        yo = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        sesion = Sesiones.como(yo, "prueba_abogado", Sesiones.TODOS);
        jdbc.update("UPDATE parametro SET valor = '3,1,0' WHERE clave = 'agenda.dias_aviso'", Map.of());
    }

    private String tipo(String codigo) {
        return String.valueOf(jdbc.queryForObject("SELECT id FROM tipo_evento WHERE codigo = :c", Map.of("c", codigo), Long.class));
    }

    private MockHttpServletRequestBuilder alta(String titulo, String tipo, LocalDate fecha, String hora) {
        return post("/agenda").with(sesion).with(csrf()).param("tipoEventoId", tipo(tipo)).param("titulo", titulo)
                .param("fecha", fecha.toString()).param("todoElDia", "false").param("horaInicio", hora)
                .param("responsableId", String.valueOf(yo));
    }

    private long id(String titulo) {
        return jdbc.queryForObject("SELECT id FROM evento_agenda WHERE titulo = :t", Map.of("t", titulo), Long.class);
    }

    private List<String> avisos(long evento, String estado) {
        return jdbc.queryForList("SELECT CONVERT(VARCHAR(16), fecha_aviso AT TIME ZONE 'Paraguay Standard Time', 120) FROM recordatorio "
                + "WHERE evento_agenda_id = :id AND estado = :e ORDER BY fecha_aviso", Map.of("id", evento, "e", estado), String.class);
    }

    @Test
    void unVencimientoGeneraSusAvisosYSinHoraDeFin() throws Exception {
        LocalDate fecha = hoy.plusDays(10);
        mvc.perform(alta("Vence contestación", "VENCIMIENTO", fecha, "11:00").param("horaFin", "12:00"))
                .andExpect(redirectedUrlPattern("/agenda/*?ok=*"));
        long id = id("Vence contestación");
        Map<String, Object> ev = jdbc.queryForMap("SELECT fecha_fin, estado FROM evento_agenda WHERE id = :id", Map.of("id", id));
        assertThat(ev.get("fecha_fin")).isNull();
        assertThat(ev.get("estado")).isEqualTo("PENDIENTE");
        assertThat(avisos(id, "PENDIENTE")).containsExactly(fecha.minusDays(3) + " 08:00", fecha.minusDays(1) + " 08:00", fecha + " 08:00");

        // Si cambia de fecha, los avisos se recalculan; los anteriores quedan anulados.
        LocalDate otra = fecha.plusDays(5);
        mvc.perform(post("/agenda/" + id).with(sesion).with(csrf()).param("tipoEventoId", tipo("VENCIMIENTO"))
                        .param("titulo", "Vence contestación").param("fecha", otra.toString()).param("todoElDia", "false")
                        .param("horaInicio", "07:30").param("responsableId", String.valueOf(yo)))
                .andExpect(redirectedUrlPattern("/agenda/" + id + "?ok=*"));
        assertThat(avisos(id, "ANULADO")).hasSize(3);
        // El mismo día avisa a la hora del evento, porque es antes de las 8.
        assertThat(avisos(id, "PENDIENTE")).containsExactly(otra.minusDays(3) + " 08:00", otra.minusDays(1) + " 08:00", otra + " 07:30");
    }

    @Test
    void validaFechasHorasYResponsable() throws Exception {
        mvc.perform(post("/agenda").with(sesion).with(csrf()).param("todoElDia", "false").param("fecha", hoy.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Elija el tipo de evento.")))
                .andExpect(content().string(Matchers.containsString("Título es requerido.")))
                .andExpect(content().string(Matchers.containsString("Hora de inicio es requerida")))
                .andExpect(content().string(Matchers.containsString("Elija el responsable.")));
        mvc.perform(alta("Pasado", "AUDIENCIA", hoy.minusDays(1), "09:00"))
                .andExpect(content().string(Matchers.containsString("La fecha no puede ser anterior a hoy")));
        mvc.perform(alta("Al revés", "AUDIENCIA", hoy.plusDays(1), "10:00").param("horaFin", "09:00"))
                .andExpect(content().string(Matchers.containsString("La hora de fin debe ser posterior a la de inicio.")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_agenda WHERE titulo IN ('Pasado', 'Al revés')", Map.of(), Integer.class)).isZero();
    }

    @Test
    void estados() throws Exception {
        mvc.perform(alta("Reunión futura", "REUNION", hoy.plusDays(2), "15:00").param("horaFin", "16:00"));
        long id = id("Reunión futura");
        mvc.perform(post("/agenda/" + id + "/estado").with(sesion).with(csrf()).param("estado", "REALIZADO").param("resultado", "Ok"))
                .andExpect(content().string(Matchers.containsString("Todavía no ocurrió")));
        mvc.perform(post("/agenda/" + id + "/estado").with(sesion).with(csrf()).param("estado", "SUSPENDIDO"))
                .andExpect(content().string(Matchers.containsString("Indique el motivo.")));
        mvc.perform(post("/agenda/" + id + "/estado").with(sesion).with(csrf()).param("estado", "SUSPENDIDO").param("resultado", "Pidió el cliente"))
                .andExpect(redirectedUrlPattern("/agenda/" + id + "?ok=*"));
        assertThat(avisos(id, "PENDIENTE")).isEmpty();
        mvc.perform(post("/agenda/" + id + "/estado").with(sesion).with(csrf()).param("estado", "CANCELADO").param("resultado", "Se desistió"))
                .andExpect(redirectedUrlPattern("/agenda/" + id + "?ok=*"));
        // Cancelado es final: no se modifica.
        mvc.perform(get("/agenda/" + id + "/editar").with(sesion)).andExpect(redirectedUrlPattern("/agenda/" + id + "?error=*"));

        // Uno que ya ocurrió se marca realizado con su resultado.
        jdbc.update("INSERT INTO evento_agenda (tipo_evento_id, titulo, fecha_inicio, responsable_id) VALUES (:t, N'Audiencia de ayer', :f, :yo)",
                Map.of("t", Long.valueOf(tipo("AUDIENCIA")), "f", Filas.momento(LocalDateTime.now(Filas.ZONA).minusDays(1)), "yo", yo));
        long ayer = id("Audiencia de ayer");
        mvc.perform(post("/agenda/" + ayer + "/estado").with(sesion).with(csrf()).param("estado", "REALIZADO").param("resultado", "Se fijó nueva fecha"))
                .andExpect(redirectedUrlPattern("/agenda/" + ayer + "?ok=*"));
        assertThat(jdbc.queryForObject("SELECT resultado FROM evento_agenda WHERE id = :id", Map.of("id", ayer), String.class)).isEqualTo("Se fijó nueva fecha");
    }

    @Test
    void listaCalendarioYExpediente() throws Exception {
        long cliente = jdbc.queryForObject("INSERT INTO cliente (tipo_persona, nombres, apellidos, telefono) OUTPUT inserted.id "
                + "VALUES ('F', N'Ana', N'Agenda', '0981000000')", Map.of(), Long.class);
        mvc.perform(post("/expedientes").with(sesion).with(csrf()).param("caratula", "Agenda c/ Prueba").param("clienteId", String.valueOf(cliente))
                .param("abogadoId", String.valueOf(yo)).param("materiaId", String.valueOf(jdbc.queryForObject("SELECT id FROM materia WHERE codigo = 'CIVIL'", Map.of(), Long.class)))
                .param("tipoProcesoId", String.valueOf(jdbc.queryForObject("SELECT id FROM tipo_proceso WHERE codigo = 'CIVIL_ORDINARIO'", Map.of(), Long.class)))
                .param("estadoId", String.valueOf(jdbc.queryForObject("SELECT id FROM estado_expediente WHERE codigo = 'ABIERTO'", Map.of(), Long.class)))
                .param("fechaInicio", hoy.toString()).param("moneda", "PYG"));
        long expediente = jdbc.queryForObject("SELECT id FROM expediente WHERE caratula = 'Agenda c/ Prueba'", Map.of(), Long.class);

        mvc.perform(get("/agenda/nuevo").param("expediente", String.valueOf(expediente)).with(sesion)).andExpect(status().isOk());
        mvc.perform(alta("Audiencia preliminar", "AUDIENCIA", hoy.plusDays(1), "09:00").param("expedienteId", String.valueOf(expediente)))
                .andExpect(redirectedUrlPattern("/agenda/*?ok=*"));
        long id = id("Audiencia preliminar");

        mvc.perform(get("/agenda").with(sesion)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Audiencia preliminar")));
        mvc.perform(get("/agenda").param("vista", "MES").param("mes", hoy.plusDays(1).toString().substring(0, 7)).with(sesion))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("/agenda/" + id)));
        mvc.perform(get("/agenda/" + id).with(sesion)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Avisos programados")));
        mvc.perform(get("/agenda/" + id + "/estado").with(sesion)).andExpect(status().isOk());
        mvc.perform(get("/expedientes/" + expediente).with(sesion))
                .andExpect(content().string(Matchers.containsString("/agenda/" + id)));
    }

    @Test
    void sinPermisoDeEdicionSoloConsulta() throws Exception {
        RequestPostProcessor lector = Sesiones.como(yo, "prueba_abogado", Set.of("AGENDA_VER"));
        mvc.perform(get("/agenda").with(lector)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("/agenda/nuevo"))));
        mvc.perform(get("/agenda/nuevo").with(lector)).andExpect(status().isForbidden());
    }
}
