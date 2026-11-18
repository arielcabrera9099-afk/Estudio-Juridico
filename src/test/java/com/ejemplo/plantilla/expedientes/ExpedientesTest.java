package com.ejemplo.plantilla.expedientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Expedientes: alta con número automático, validaciones, partes, bitácora y cambios de estado (contra la base real). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ExpedientesTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private long abogadoId;
    private RequestPostProcessor abogado;
    private long clienteId;
    private final String hoy = LocalDate.now().toString();

    @BeforeEach
    void preparar() {
        abogadoId = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        abogado = Sesiones.como(abogadoId, "prueba_abogado", Sesiones.TODOS);
        clienteId = jdbc.queryForObject("INSERT INTO cliente (tipo_persona, nombres, apellidos, telefono) OUTPUT inserted.id "
                + "VALUES ('F', N'Juana', N'Prueba', '0981000000')", Map.of(), Long.class);
        jdbc.update("UPDATE parametro SET valor = 'AAAA-NNNN' WHERE clave = 'expediente.formato_numero'", Map.of());
    }

    private String id(String tabla, String codigo) {
        return String.valueOf(jdbc.queryForObject("SELECT id FROM " + tabla + " WHERE codigo = :c", Map.of("c", codigo), Long.class));
    }

    private MockHttpServletRequestBuilder alta(String caratula) {
        return alta(caratula, abogadoId);
    }

    private MockHttpServletRequestBuilder alta(String caratula, long responsable) {
        return post("/expedientes").with(abogado).with(csrf()).param("caratula", caratula)
                .param("clienteId", String.valueOf(clienteId)).param("abogadoId", String.valueOf(responsable))
                .param("materiaId", id("materia", "CIVIL")).param("tipoProcesoId", id("tipo_proceso", "CIVIL_ORDINARIO"))
                .param("estadoId", id("estado_expediente", "ABIERTO")).param("fechaInicio", hoy)
                .param("monto", "1.500.000").param("moneda", "PYG");
    }

    private Map<String, Object> expediente(String caratula) {
        return jdbc.queryForMap("SELECT * FROM expediente WHERE caratula = :c", Map.of("c", caratula));
    }

    private long crear(String caratula) throws Exception {
        mvc.perform(alta(caratula)).andExpect(redirectedUrlPattern("/expedientes?ok=*"));
        return ((Number) expediente(caratula).get("id")).longValue();
    }

    @Test
    void altaNumeraPorAnioYGuardaElHistorial() throws Exception {
        long primero = crear("Prueba c/ Uno s/ cobro");
        long segundo = crear("Prueba c/ Dos s/ cobro");
        String n1 = (String) expediente("Prueba c/ Uno s/ cobro").get("numero_interno");
        String n2 = (String) expediente("Prueba c/ Dos s/ cobro").get("numero_interno");
        int anio = LocalDate.now().getYear();
        assertThat(n1).matches(anio + "-\\d{4}");
        assertThat(Integer.parseInt(n2.substring(5))).isEqualTo(Integer.parseInt(n1.substring(5)) + 1);
        assertThat(expediente("Prueba c/ Uno s/ cobro").get("monto_reclamado")).isEqualTo(new BigDecimal("1500000.00"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expediente_estado_historial WHERE expediente_id = :id "
                + "AND estado_anterior_id IS NULL", Map.of("id", primero), Integer.class)).isEqualTo(1);
        mvc.perform(get("/expedientes/" + segundo).with(abogado)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(n2)))
                .andExpect(content().string(Matchers.containsString("1.500.000")));
        mvc.perform(get("/expedientes").param("texto", "Prueba c/ Uno").with(abogado))
                .andExpect(content().string(Matchers.containsString("1 expediente")));
    }

    @Test
    void altaRechazaDatosInvalidos() throws Exception {
        mvc.perform(post("/expedientes").with(abogado).with(csrf())
                        .param("clienteId", "999999").param("materiaId", id("materia", "PENAL"))
                        .param("tipoProcesoId", id("tipo_proceso", "CIVIL_ORDINARIO")).param("estadoId", id("estado_expediente", "CERRADO"))
                        .param("fechaInicio", LocalDate.now().plusDays(3).toString()).param("numeroJudicial", "123")
                        .param("monto", "1.500,50").param("moneda", "PYG"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Carátula es requerida.")))
                .andExpect(content().string(Matchers.containsString("Elija el cliente de la lista.")))
                .andExpect(content().string(Matchers.containsString("Elija el abogado responsable.")))
                .andExpect(content().string(Matchers.containsString("El tipo de proceso no corresponde a la materia elegida.")))
                .andExpect(content().string(Matchers.containsString("Elija un estado de la lista (no de cierre).")))
                .andExpect(content().string(Matchers.containsString("La fecha de inicio no puede ser futura.")))
                .andExpect(content().string(Matchers.containsString("Indique el año del número judicial.")))
                .andExpect(content().string(Matchers.containsString("Los guaraníes no llevan decimales.")));
        // Un asistente no puede ser responsable: solo usuarios con rol Abogado.
        long asistente = Sesiones.crearUsuario(jdbc, "prueba_asistente", "ASISTENTE", false);
        mvc.perform(alta("X", asistente))
                .andExpect(content().string(Matchers.containsString("Elija el abogado responsable de la lista.")));
    }

    @Test
    void partesYBitacora() throws Exception {
        long id = crear("Prueba c/ Partes");
        mvc.perform(post("/expedientes/" + id + "/partes").with(abogado).with(csrf())
                        .param("rolParteId", id("rol_parte", "DEMANDADO")).param("nombre", "Pedro Contraparte")
                        .param("numeroDocumento", "1.234.567"))
                .andExpect(redirectedUrlPattern("/expedientes/" + id + "?ok=*"));
        long parte = jdbc.queryForObject("SELECT id FROM expediente_parte WHERE expediente_id = :id", Map.of("id", id), Long.class);
        mvc.perform(post("/expedientes/" + id + "/partes/" + parte + "/eliminar").with(abogado).with(csrf()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expediente_parte WHERE expediente_id = :id", Map.of("id", id), Integer.class)).isZero();

        mvc.perform(post("/expedientes/" + id + "/actuaciones").with(abogado).with(csrf())
                        .param("tipoActuacionId", id("tipo_actuacion", "ESCRITO")).param("fecha", LocalDate.now().plusDays(1).toString())
                        .param("descripcion", "Futuro"))
                .andExpect(content().string(Matchers.containsString("use la agenda")));
        mvc.perform(post("/expedientes/" + id + "/actuaciones").with(abogado).with(csrf())
                        .param("tipoActuacionId", id("tipo_actuacion", "ESCRITO")).param("fecha", hoy)
                        .param("descripcion", "Se presentó la demanda.\nCon documentos."))
                .andExpect(redirectedUrlPattern("/expedientes/" + id + "?ok=*"));
        Map<String, Object> a = jdbc.queryForMap("SELECT * FROM actuacion WHERE expediente_id = :id", Map.of("id", id));
        assertThat(a.get("usuario_id")).isEqualTo(abogadoId);
        assertThat(a.get("descripcion")).isEqualTo("Se presentó la demanda.\nCon documentos.");
    }

    @Test
    void cerrarExigeFechaYMotivoYDejaElExpedienteDeSoloLectura() throws Exception {
        long id = crear("Prueba c/ Cierre");
        String cerrado = id("estado_expediente", "CERRADO");
        mvc.perform(post("/expedientes/" + id + "/estado").with(abogado).with(csrf()).param("estadoId", cerrado).param("fechaCierre", ""))
                .andExpect(content().string(Matchers.containsString("Fecha de cierre es requerida.")))
                .andExpect(content().string(Matchers.containsString("Indique el motivo del cierre")));
        mvc.perform(post("/expedientes/" + id + "/estado").with(abogado).with(csrf()).param("estadoId", cerrado)
                        .param("fechaCierre", hoy).param("motivoCierre", "Acuerdo homologado"))
                .andExpect(redirectedUrlPattern("/expedientes?ok=*"));
        Map<String, Object> x = jdbc.queryForMap("SELECT * FROM expediente WHERE id = :id", Map.of("id", id));
        assertThat(x.get("motivo_cierre")).isEqualTo("Acuerdo homologado");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expediente_estado_historial WHERE expediente_id = :id", Map.of("id", id), Integer.class)).isEqualTo(2);

        // Cerrado: no se modifica ni se le agregan partes.
        mvc.perform(get("/expedientes/" + id + "/editar").with(abogado)).andExpect(redirectedUrlPattern("/expedientes/" + id + "?error=*"));
        mvc.perform(post("/expedientes/" + id + "/partes").with(abogado).with(csrf()).param("nombre", "X"))
                .andExpect(redirectedUrlPattern("/expedientes/" + id + "?error=*"));

        // Reabrir pide el motivo y borra fecha y motivo de cierre.
        String tramite = id("estado_expediente", "EN_TRAMITE");
        mvc.perform(post("/expedientes/" + id + "/estado").with(abogado).with(csrf()).param("estadoId", tramite))
                .andExpect(content().string(Matchers.containsString("Indique por qué se reabre el expediente.")));
        mvc.perform(post("/expedientes/" + id + "/estado").with(abogado).with(csrf()).param("estadoId", tramite)
                        .param("observacion", "Se anuló el acuerdo"))
                .andExpect(redirectedUrlPattern("/expedientes?ok=*"));
        x = jdbc.queryForMap("SELECT * FROM expediente WHERE id = :id", Map.of("id", id));
        assertThat(x.get("fecha_cierre")).isNull();
        assertThat(x.get("motivo_cierre")).isNull();
    }

    @Test
    void todasLasPantallasSeDibujan() throws Exception {
        long id = crear("Prueba c/ Pantallas");
        mvc.perform(get("/expedientes/nuevo").param("cliente", String.valueOf(clienteId)).with(abogado)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-depende=\"materiaId\"")));
        for (String ruta : new String[] {"/editar", "/estado", "/partes/nuevo", "/actuaciones/nuevo"}) {
            mvc.perform(get("/expedientes/" + id + ruta).with(abogado)).andExpect(status().isOk());
        }
        mvc.perform(get("/clientes/" + clienteId).with(abogado)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/expedientes/" + id)));
        mvc.perform(get("/expedientes").param("situacion", "TODOS").param("cliente", String.valueOf(clienteId)).with(abogado))
                .andExpect(content().string(Matchers.containsString("1 expediente")));
    }

    @Test
    void unAsistenteNoCierra() throws Exception {
        long id = crear("Prueba c/ Asistente");
        long asistente = Sesiones.crearUsuario(jdbc, "prueba_asistente", "ASISTENTE", false);
        mvc.perform(post("/expedientes/" + id + "/estado").with(Sesiones.como(asistente, "prueba_asistente", Sesiones.ASISTENTE)).with(csrf())
                        .param("estadoId", id("estado_expediente", "CERRADO")).param("fechaCierre", hoy).param("motivoCierre", "X"))
                .andExpect(content().string(Matchers.containsString("Elija un estado de la lista.")));
        assertThat(jdbc.queryForObject("SELECT fecha_cierre FROM expediente WHERE id = :id", Map.of("id", id), LocalDate.class)).isNull();
    }

    @Test
    void sinPermisoDeEdicionNoDaDeAlta() throws Exception {
        long lector = Sesiones.crearUsuario(jdbc, "prueba_lector", "ASISTENTE", false);
        RequestPostProcessor soloVer = Sesiones.como(lector, "prueba_lector", java.util.Set.of("EXPEDIENTES_VER"));
        mvc.perform(get("/expedientes").with(soloVer)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("/expedientes/nuevo"))));
        mvc.perform(get("/expedientes/nuevo").with(soloVer)).andExpect(status().isForbidden());
    }
}
