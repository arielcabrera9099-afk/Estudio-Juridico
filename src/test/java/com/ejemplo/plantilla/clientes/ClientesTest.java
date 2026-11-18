package com.ejemplo.plantilla.clientes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** ABM de clientes, contactos e historial contra la base real (cada prueba se deshace al terminar). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class ClientesTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private long usuarioId;
    private RequestPostProcessor asistente;

    @BeforeEach
    void sesion() {
        usuarioId = Sesiones.crearUsuario(jdbc, "prueba_asis", "ASISTENTE", false);
        asistente = Sesiones.como(usuarioId, "prueba_asis", Sesiones.ASISTENTE);
    }

    private long tipoDoc(String codigo) {
        return jdbc.queryForObject("SELECT id FROM tipo_documento_identidad WHERE codigo = :c", Map.of("c", codigo), Long.class);
    }

    private MockHttpServletRequestBuilder altaFisica() {
        return post("/clientes").with(asistente).with(csrf())
                .param("tipoPersona", "F").param("nombres", " Juan  Carlos ").param("apellidos", "Pérez Gómez")
                .param("tipoDocumentoIdentidadId", String.valueOf(tipoDoc("CI"))).param("numeroDocumento", "4.123.456")
                .param("fechaNacimiento", "1980-05-10").param("celular", "0981 123456").param("email", "JUAN@correo.py")
                .param("razonSocial", "no debe guardarse");
    }

    private Map<String, Object> cliente(String numero) {
        return jdbc.queryForMap("SELECT * FROM cliente WHERE numero_documento = :n", Map.of("n", numero));
    }

    @Test
    void altaDePersonaFisicaNormalizaYVuelveALaLista() throws Exception {
        mvc.perform(altaFisica()).andExpect(redirectedUrlPattern("/clientes?ok=*"));
        Map<String, Object> c = cliente("4123456");
        assertThat(c.get("nombres")).isEqualTo("Juan Carlos");
        assertThat(c.get("razon_social")).isNull();
        assertThat(c.get("email")).isEqualTo("juan@correo.py");
        assertThat(c.get("creado_por")).isEqualTo(usuarioId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE tabla = 'cliente' AND accion = 'ALTA' "
                + "AND registro_id = :id", Map.of("id", c.get("id")), Integer.class)).isEqualTo(1);

        mvc.perform(get("/clientes/" + c.get("id")).with(asistente)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Pérez Gómez, Juan Carlos")))
                .andExpect(content().string(Matchers.containsString("El cliente no tiene contactos cargados.")))
                .andExpect(content().string(Matchers.containsString("todavía no tiene casos registrados")));
        mvc.perform(get("/clientes").param("texto", "4.123.456").with(asistente))
                .andExpect(content().string(Matchers.containsString("1 cliente")));
    }

    @Test
    void altaDePersonaJuridicaSinDatosDePersonaFisica() throws Exception {
        mvc.perform(post("/clientes").with(asistente).with(csrf()).param("tipoPersona", "J")
                        .param("razonSocial", "Comercial Asunción S.A.").param("nombres", "x").param("fechaNacimiento", "1990-01-01")
                        .param("tipoDocumentoIdentidadId", String.valueOf(tipoDoc("RUC"))).param("numeroDocumento", "80012345-6")
                        .param("telefono", "021 600 000"))
                .andExpect(redirectedUrlPattern("/clientes?ok=*"));
        Map<String, Object> c = cliente("80012345-6");
        assertThat(c.get("razon_social")).isEqualTo("Comercial Asunción S.A.");
        assertThat(c.get("nombres")).isNull();
        assertThat(c.get("fecha_nacimiento")).isNull();
    }

    @Test
    void altaRechazaDatosInvalidos() throws Exception {
        mvc.perform(post("/clientes").with(asistente).with(csrf()).param("tipoPersona", "F")
                        .param("fechaNacimiento", "2999-01-01").param("email", "mal")
                        .param("tipoDocumentoIdentidadId", String.valueOf(tipoDoc("RUC"))).param("numeroDocumento", "800123456"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Nombres es requerido.")))
                .andExpect(content().string(Matchers.containsString("Apellidos es requerido.")))
                .andExpect(content().string(Matchers.containsString("La fecha de nacimiento no puede ser futura.")))
                .andExpect(content().string(Matchers.containsString("El RUC debe tener el número, un guion")))
                .andExpect(content().string(Matchers.containsString("El email no es válido.")))
                .andExpect(content().string(Matchers.containsString("Ingrese al menos un teléfono o un celular.")));
        mvc.perform(post("/clientes").with(asistente).with(csrf()).param("tipoPersona", "J").param("telefono", "021 600 000")
                        .param("numeroDocumento", "123"))
                .andExpect(content().string(Matchers.containsString("Razón social es requerido.")))
                .andExpect(content().string(Matchers.containsString("Tipo de documento es requerido.")));
        mvc.perform(post("/clientes").with(asistente).with(csrf()).param("tipoPersona", "J").param("razonSocial", "Sin Número S.A.")
                        .param("telefono", "021 600 000").param("tipoDocumentoIdentidadId", String.valueOf(tipoDoc("RUC"))))
                .andExpect(content().string(Matchers.containsString("Número de documento es requerido.")));
        mvc.perform(altaFisica());
        mvc.perform(altaFisica()).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ya existe un cliente con ese documento.")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cliente WHERE numero_documento = '4123456'", Map.of(), Integer.class))
                .isEqualTo(1);
    }

    private Map<String, Object> clientePorId(long id) {
        return jdbc.queryForMap("SELECT * FROM cliente WHERE id = :id", Map.of("id", id));
    }

    /** Inserta un expediente del cliente en el estado dado, con lo mínimo que exige la tabla. */
    private long expediente(long clienteId, String estado) {
        return jdbc.queryForObject("INSERT INTO expediente (numero_interno, caratula, cliente_id, abogado_responsable_id, "
                + "materia_id, tipo_proceso_id, estado_expediente_id, fecha_inicio) OUTPUT inserted.id "
                + "SELECT 'PRUEBA-' + :estado, N'Pérez c/ Gómez s/ cobro', :cliente, :abogado, tp.materia_id, tp.id, ee.id, '2026-11-01' "
                + "FROM tipo_proceso tp, estado_expediente ee WHERE tp.codigo = 'CIVIL_EJECUTIVO' AND ee.codigo = :estado",
                Map.of("cliente", clienteId, "abogado", usuarioId, "estado", estado), Long.class);
    }

    @Test
    void modificarCambiaElTipoDePersonaYLimpiaLoQueNoCorresponde() throws Exception {
        mvc.perform(altaFisica());
        long id = ((Number) cliente("4123456").get("id")).longValue();
        mvc.perform(get("/clientes/" + id + "/editar").with(asistente)).andExpect(status().isOk());
        mvc.perform(post("/clientes/" + id).with(asistente).with(csrf()).param("tipoPersona", "J")
                        .param("razonSocial", "Pérez Hermanos S.R.L.").param("nombres", "Juan").param("telefono", "021 444 555")
                        .param("tipoDocumentoIdentidadId", String.valueOf(tipoDoc("RUC"))).param("numeroDocumento", "80012345-6")
                        .param("_activo", "on").param("activo", "true"))
                .andExpect(redirectedUrlPattern("/clientes?ok=*"));
        Map<String, Object> c = clientePorId(id);
        assertThat(c.get("tipo_persona")).isEqualTo("J");
        assertThat(c.get("nombres")).isNull();
        assertThat(c.get("fecha_nacimiento")).isNull();
        assertThat(c.get("numero_documento")).isEqualTo("80012345-6");
        assertThat(c.get("modificado_por")).isEqualTo(usuarioId);
    }

    @Test
    void laBajaNoSePermiteConCasosActivos() throws Exception {
        mvc.perform(altaFisica());
        long id = ((Number) cliente("4123456").get("id")).longValue();
        long exp = expediente(id, "EN_TRAMITE");
        mvc.perform(post("/clientes/" + id + "/baja").with(asistente).with(csrf()))
                .andExpect(redirectedUrlPattern("/clientes?error=*"));
        assertThat(clientePorId(id).get("activo")).isEqualTo(true);
        mvc.perform(get("/clientes/" + id).with(asistente))
                .andExpect(content().string(Matchers.containsString("PRUEBA-EN_TRAMITE")))
                .andExpect(content().string(Matchers.containsString("EN TRÁMITE")));

        jdbc.update("UPDATE expediente SET estado_expediente_id = (SELECT id FROM estado_expediente WHERE codigo = 'CERRADO'), "
                + "fecha_cierre = '2026-11-10', motivo_cierre = N'Acuerdo' WHERE id = :id", Map.of("id", exp));
        mvc.perform(post("/clientes/" + id + "/baja").with(asistente).with(csrf()))
                .andExpect(redirectedUrlPattern("/clientes?ok=*"));
        assertThat(clientePorId(id).get("activo")).isEqualTo(false);
        mvc.perform(get("/clientes").param("estado", "INACTIVOS").param("texto", "4123456").with(asistente))
                .andExpect(content().string(Matchers.containsString("DADO DE BAJA")));
        mvc.perform(post("/clientes/" + id + "/reactivar").with(asistente).with(csrf()));
        assertThat(clientePorId(id).get("activo")).isEqualTo(true);
    }

    @Test
    void contactosConUnSoloPrincipal() throws Exception {
        mvc.perform(altaFisica());
        long id = ((Number) cliente("4123456").get("id")).longValue();
        mvc.perform(post("/clientes/" + id + "/contactos").with(asistente).with(csrf()).param("nombre", "Ana López"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ingrese al menos un teléfono o un email.")));
        mvc.perform(post("/clientes/" + id + "/contactos").with(asistente).with(csrf())
                        .param("nombre", "Ana López").param("cargo", "Gerente").param("telefono", "0981 222 333"))
                .andExpect(redirectedUrlPattern("/clientes/" + id + "?ok=*"));
        mvc.perform(post("/clientes/" + id + "/contactos").with(asistente).with(csrf())
                .param("nombre", "Luis Benítez").param("email", "luis@empresa.py").param("esPrincipal", "true"));
        assertThat(jdbc.queryForList("SELECT nombre FROM cliente_contacto WHERE cliente_id = :id AND es_principal = 1",
                Map.of("id", id), String.class)).containsExactly("Luis Benítez");

        long luis = jdbc.queryForObject("SELECT id FROM cliente_contacto WHERE nombre = N'Luis Benítez'", Map.of(), Long.class);
        mvc.perform(post("/clientes/" + id + "/contactos/" + luis + "/eliminar").with(asistente).with(csrf()))
                .andExpect(redirectedUrlPattern("/clientes/" + id + "?ok=*"));
        assertThat(jdbc.queryForList("SELECT nombre FROM cliente_contacto WHERE cliente_id = :id AND es_principal = 1",
                Map.of("id", id), String.class)).containsExactly("Ana López");
        mvc.perform(get("/clientes/" + id).with(asistente))
                .andExpect(content().string(Matchers.containsString("Ana López")))
                .andExpect(content().string(Matchers.containsString("PRINCIPAL")));
    }

    @Test
    void sinPermisoDeEdicionSoloConsulta() throws Exception {
        long id = Sesiones.crearUsuario(jdbc, "prueba_lector", "ASISTENTE", false);
        RequestPostProcessor lector = Sesiones.como(id, "prueba_lector", Set.of("CLIENTES_VER"));
        mvc.perform(get("/clientes").with(lector)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("/clientes/nuevo"))));
        mvc.perform(get("/clientes/nuevo").with(lector)).andExpect(status().isForbidden());
        mvc.perform(post("/clientes").with(lector).with(csrf()).param("tipoPersona", "J").param("razonSocial", "X")
                .param("telefono", "021 600 000")).andExpect(status().isForbidden());
    }
}
