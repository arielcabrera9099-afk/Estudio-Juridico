package com.ejemplo.plantilla.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.Sesiones;

/** Documentos: carga con validación del archivo, descarga, búsqueda y baja lógica. Los archivos van a una carpeta temporal. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class DocumentosTest {

    private static final Path CARPETA;

    static {
        try {
            CARPETA = Files.createTempDirectory("lexnova-documentos-prueba");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void carpeta(DynamicPropertyRegistry r) {
        r.add("app.documentos.carpeta", CARPETA::toString);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private long yo;
    private RequestPostProcessor sesion;
    private long expediente;
    private static final byte[] PDF = "%PDF-1.4\n% documento de prueba\n".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void preparar() {
        yo = Sesiones.crearUsuario(jdbc, "prueba_abogado", "ABOGADO", false);
        sesion = Sesiones.como(yo, "prueba_abogado", Sesiones.TODOS);
        jdbc.update("UPDATE parametro SET valor = 'pdf,docx,jpg,png' WHERE clave = 'documentos.tipos_permitidos'", Map.of());
        jdbc.update("UPDATE parametro SET valor = '1' WHERE clave = 'documentos.tamano_max_mb'", Map.of());
        long cliente = Sesiones.crearCliente(jdbc, "Doc", "99900002");
        expediente = jdbc.queryForObject("INSERT INTO expediente (numero_interno, caratula, cliente_id, abogado_responsable_id, "
                + "materia_id, tipo_proceso_id, estado_expediente_id, fecha_inicio) OUTPUT inserted.id "
                + "SELECT 'PRUEBA-DOC', N'Prueba c/ Documentos', :c, :yo, tp.materia_id, tp.id, ee.id, '2026-11-01' "
                + "FROM tipo_proceso tp, estado_expediente ee WHERE tp.codigo = 'CIVIL_ORDINARIO' AND ee.codigo = 'ABIERTO'",
                Map.of("c", cliente, "yo", yo), Long.class);
    }

    private String tipo(String codigo) {
        return String.valueOf(jdbc.queryForObject("SELECT id FROM tipo_documento WHERE codigo = :c", Map.of("c", codigo), Long.class));
    }

    private MockMultipartHttpServletRequestBuilder carga(String nombreArchivo, byte[] contenido) {
        MockMultipartHttpServletRequestBuilder b = multipart("/documentos").file(new MockMultipartFile("archivo", nombreArchivo, "application/pdf", contenido));
        b.with(sesion).with(csrf()).param("expedienteId", String.valueOf(expediente)).param("tipoDocumentoId", tipo("ESCRITO"));
        return b;
    }

    @Test
    void cargaGuardaElArchivoFueraDeLaBaseYSeDescarga() throws Exception {
        mvc.perform(carga("Demanda_inicial.pdf", PDF).param("fechaDocumento", "2026-11-10"))
                .andExpect(redirectedUrlPattern("/documentos?ok=*"));
        Map<String, Object> d = jdbc.queryForMap("SELECT * FROM documento WHERE expediente_id = :e", Map.of("e", expediente));
        assertThat(d.get("nombre")).isEqualTo("Demanda inicial");
        assertThat(d.get("nombre_archivo")).isEqualTo("Demanda_inicial.pdf");
        assertThat(d.get("tipo_mime")).isEqualTo("application/pdf");
        assertThat(((Number) d.get("tamano_bytes")).longValue()).isEqualTo(PDF.length);
        assertThat((String) d.get("hash_sha256")).hasSize(64);
        String ruta = (String) d.get("ruta_almacenamiento");
        assertThat(ruta).startsWith("expedientes/" + expediente + "/").endsWith(".pdf");
        assertThat(Files.readAllBytes(CARPETA.resolve(ruta))).isEqualTo(PDF);

        long id = ((Number) d.get("id")).longValue();
        mvc.perform(get("/documentos/" + id + "/descargar").with(sesion)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
        mvc.perform(get("/documentos/" + id + "/ver").with(sesion))
                .andExpect(header().string("Content-Disposition", Matchers.startsWith("inline")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE tabla = 'documento' AND accion = 'DESCARGA' AND registro_id = :id",
                Map.of("id", id), Integer.class)).isEqualTo(2);

        mvc.perform(get("/documentos").param("expediente", "PRUEBA-DOC").with(sesion))
                .andExpect(content().string(Matchers.containsString("Demanda inicial")));
        mvc.perform(get("/expedientes/" + expediente).with(sesion))
                .andExpect(content().string(Matchers.containsString("/documentos/" + id + "/descargar")));
        mvc.perform(get("/documentos/" + id).with(sesion)).andExpect(status().isOk());
        mvc.perform(get("/documentos/" + id + "/editar").with(sesion)).andExpect(status().isOk());
    }

    @Test
    void rechazaArchivosInvalidos() throws Exception {
        mvc.perform(multipart("/documentos").with(sesion).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Elija el archivo.")))
                .andExpect(content().string(Matchers.containsString("Elija el expediente.")))
                .andExpect(content().string(Matchers.containsString("Elija el tipo de documento.")));
        mvc.perform(carga("virus.exe", PDF)).andExpect(content().string(Matchers.containsString("No se aceptan archivos .exe")));
        // Un ejecutable renombrado como PDF no pasa: el contenido no empieza como un PDF.
        mvc.perform(carga("factura.pdf", "MZ\u0090\u0000falso".getBytes(StandardCharsets.ISO_8859_1)))
                .andExpect(content().string(Matchers.containsString("El contenido del archivo no corresponde a un .pdf")));
        mvc.perform(carga("grande.pdf", grande())).andExpect(content().string(Matchers.containsString("supera el máximo de 1 MB")));
        mvc.perform(carga("futuro.pdf", PDF).param("fechaDocumento", LocalDate.now().plusDays(5).toString()))
                .andExpect(content().string(Matchers.containsString("no puede ser futura")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documento WHERE expediente_id = :e", Map.of("e", expediente), Integer.class)).isZero();
    }

    private static byte[] grande() {
        byte[] b = new byte[1024 * 1024 + 10];
        System.arraycopy(PDF, 0, b, 0, PDF.length);
        return b;
    }

    @Test
    void noSeCargaDosVecesElMismoArchivo() throws Exception {
        mvc.perform(carga("escrito.pdf", PDF)).andExpect(redirectedUrlPattern("/documentos?ok=*"));
        mvc.perform(carga("copia.pdf", PDF)).andExpect(content().string(Matchers.containsString("Ese archivo ya está cargado en el expediente")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM documento WHERE expediente_id = :e", Map.of("e", expediente), Integer.class)).isEqualTo(1);
    }

    @Test
    void desdeLaFichaDelExpedienteVuelveAElla() throws Exception {
        mvc.perform(carga("poder.pdf", PDF).param("volver", "expediente"))
                .andExpect(redirectedUrlPattern("/expedientes/" + expediente + "?ok=*"));
    }

    @Test
    void eliminarYRestaurar() throws Exception {
        mvc.perform(carga("prueba.pdf", PDF));
        long id = jdbc.queryForObject("SELECT id FROM documento WHERE expediente_id = :e", Map.of("e", expediente), Long.class);
        mvc.perform(post("/documentos/" + id + "/eliminar").with(sesion).with(csrf())).andExpect(redirectedUrlPattern("/documentos?ok=*"));
        assertThat(jdbc.queryForObject("SELECT eliminado_en FROM documento WHERE id = :id", Map.of("id", id), Object.class)).isNotNull();
        mvc.perform(get("/documentos").param("expediente", "PRUEBA-DOC").with(sesion))
                .andExpect(content().string(Matchers.containsString("No hay documentos con esos filtros.")));
        // Quien no puede eliminar tampoco descarga un documento eliminado.
        long asistente = Sesiones.crearUsuario(jdbc, "prueba_asistente", "ASISTENTE", false);
        mvc.perform(get("/documentos/" + id + "/descargar").with(Sesiones.como(asistente, "prueba_asistente", Sesiones.ASISTENTE)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/documentos/" + id + "/restaurar").with(sesion).with(csrf()));
        assertThat(jdbc.queryForObject("SELECT eliminado_en FROM documento WHERE id = :id", Map.of("id", id), Object.class)).isNull();
    }

    @Test
    void permisos() throws Exception {
        long asistente = Sesiones.crearUsuario(jdbc, "prueba_asistente", "ASISTENTE", false);
        RequestPostProcessor a = Sesiones.como(asistente, "prueba_asistente", Sesiones.ASISTENTE);
        // El asistente puede cargar (DOCUMENTOS_SUBIR) pero no eliminar.
        mvc.perform(carga("x.pdf", PDF).with(a)).andExpect(redirectedUrlPattern("/documentos?ok=*"));
        long id = jdbc.queryForObject("SELECT id FROM documento WHERE expediente_id = :e", Map.of("e", expediente), Long.class);
        mvc.perform(post("/documentos/" + id + "/eliminar").with(a).with(csrf())).andExpect(status().isForbidden());
        RequestPostProcessor lector = Sesiones.como(asistente, "prueba_asistente", Set.of("DOCUMENTOS_VER"));
        mvc.perform(get("/documentos/nuevo").with(lector)).andExpect(status().isForbidden());
        mvc.perform(get("/documentos").with(lector)).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("/documentos/nuevo"))));
    }
}
