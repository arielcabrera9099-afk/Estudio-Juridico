package com.ejemplo.plantilla.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

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

/** Reportes de casos y de agenda: totales, filtros, exportación y permisos (contra la base real). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ReportesTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private long yo;
    private RequestPostProcessor sesion;
    private long cliente;

    @BeforeEach
    void preparar() {
        yo = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        sesion = Sesiones.como(yo, "prueba_abogado", Sesiones.TODOS);
        cliente = Sesiones.crearCliente(jdbc, "Reporte", "99900004");
    }

    private void expediente(String numero, String estado, String materia) {
        jdbc.update("INSERT INTO expediente (numero_interno, caratula, cliente_id, abogado_responsable_id, materia_id, tipo_proceso_id, "
                + "estado_expediente_id, fecha_inicio) SELECT :n, N'CARÁTULA ' + :n, :c, :yo, tp.materia_id, tp.id, ee.id, '2026-03-10' "
                + "FROM tipo_proceso tp JOIN materia m ON m.id = tp.materia_id, estado_expediente ee "
                + "WHERE m.codigo = :m AND tp.id = (SELECT MIN(id) FROM tipo_proceso WHERE materia_id = m.id) AND ee.codigo = :e",
                new MapSqlParameterSource("n", numero).addValue("c", cliente).addValue("yo", yo).addValue("m", materia).addValue("e", estado));
    }

    @Test
    void casosActivosYCerradosConTotales() throws Exception {
        expediente("RP-1", "ABIERTO", "CIVIL");
        expediente("RP-2", "EN_TRAMITE", "CIVIL");
        expediente("RP-3", "CERRADO", "PENAL");
        String abogado = String.valueOf(yo);
        mvc.perform(get("/reportes/expedientes").param("abogado", abogado).with(sesion)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("3 expedientes")))
                .andExpect(content().string(Matchers.containsString("RP-3")));
        mvc.perform(get("/reportes/expedientes").param("abogado", abogado).param("situacion", "ACTIVO").param("agrupar", "MATERIA").with(sesion))
                .andExpect(content().string(Matchers.containsString("2 expedientes")))
                .andExpect(content().string(Matchers.containsString("Resumen por materia")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("RP-3"))));
        mvc.perform(get("/reportes/expedientes").param("abogado", abogado).param("desde", "2026-04-01").with(sesion))
                .andExpect(content().string(Matchers.containsString("No hay expedientes con esos filtros.")));
        mvc.perform(get("/reportes/expedientes").param("desde", "2026-05-01").param("hasta", "2026-04-01").with(sesion))
                .andExpect(content().string(Matchers.containsString("La fecha desde no puede ser posterior")));
    }

    @Test
    void exportaACsvParaExcel() throws Exception {
        expediente("RP-9", "ABIERTO", "CIVIL");
        byte[] csv = mvc.perform(get("/reportes/expedientes.csv").param("abogado", String.valueOf(yo)).with(sesion))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        String texto = new String(csv, StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿NÚMERO;CARÁTULA;CLIENTE");
        assertThat(texto).contains("RP-9;CARÁTULA RP-9;Prueba, Reporte;CIVIL;");
    }

    @Test
    void agendaSemanalYMensual() throws Exception {
        LocalDate hoy = LocalDate.now(Filas.ZONA);
        jdbc.update("INSERT INTO evento_agenda (tipo_evento_id, titulo, fecha_inicio, responsable_id) "
                + "SELECT id, N'AUDIENCIA DEL REPORTE', :f, :yo FROM tipo_evento WHERE codigo = 'AUDIENCIA'",
                new MapSqlParameterSource("f", Filas.momento(hoy.atTime(10, 0))).addValue("yo", yo));
        mvc.perform(get("/reportes/agenda").param("responsable", String.valueOf(yo)).with(sesion)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("AUDIENCIA DEL REPORTE")))
                .andExpect(content().string(Matchers.containsString("Semana del ")));
        mvc.perform(get("/reportes/agenda").param("periodo", "MES").param("agrupar", "RESPONSABLE")
                        .param("responsable", String.valueOf(yo)).with(sesion))
                .andExpect(content().string(Matchers.containsString("Resumen por responsable")))
                .andExpect(content().string(Matchers.containsString("1 evento")));
        mvc.perform(get("/reportes/agenda").param("dia", hoy.plusMonths(2).toString()).param("responsable", String.valueOf(yo)).with(sesion))
                .andExpect(content().string(Matchers.containsString("No hay eventos en el período")));
        String csv = new String(mvc.perform(get("/reportes/agenda.csv").param("responsable", String.valueOf(yo)).with(sesion))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("AUDIENCIA DEL REPORTE");
        assertThat(csv).contains(hoy.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ";10:00");
    }

    @Test
    void sinPermisoDeReportesNoEntra() throws Exception {
        long asistente = Sesiones.crearUsuario(jdbc, "prueba_asistente", "ASISTENTE", false);
        mvc.perform(get("/reportes/expedientes").with(Sesiones.como(asistente, "prueba_asistente", Sesiones.ASISTENTE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reportes/agenda.csv").with(Sesiones.como(asistente, "prueba_asistente", Set.of(Sesiones.menu("AGENDA_CALENDARIO")))))
                .andExpect(status().isForbidden());
    }
}
