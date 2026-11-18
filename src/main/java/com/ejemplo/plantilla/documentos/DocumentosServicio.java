package com.ejemplo.plantilla.documentos;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.datos.Parametros;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.Opcion;

/**
 * Documentos de los expedientes: carga, búsqueda, datos, descarga y baja lógica. Reglas:
 * <ul>
 *   <li>Expediente (no cerrado), tipo y archivo obligatorios. El nombre, si se deja vacío, es el del archivo.</li>
 *   <li>Archivo: extensión entre las del parámetro documentos.tipos_permitidos, tamaño hasta documentos.tamano_max_mb,
 *       y contenido que corresponde a la extensión (un PDF empieza como PDF, una imagen como imagen…). El tipo MIME
 *       lo decide el sistema por la extensión, no lo que manda el navegador.</li>
 *   <li>No se carga dos veces el mismo archivo (mismo SHA-256) en un expediente.</li>
 *   <li>Fecha del documento opcional, no futura. La actuación, si se indica, es del mismo expediente.</li>
 *   <li>La baja es lógica (eliminado_en): el archivo se conserva y el documento se puede restaurar.</li>
 * </ul>
 */
@Service
public class DocumentosServicio {

    public static final int MAXIMO_FILAS = 500;
    public static final String SUBIR = "DOCUMENTOS_SUBIR";
    public static final String ELIMINAR = "DOCUMENTOS_ELIMINAR";
    private static final String CLIENTE = "CASE WHEN c.tipo_persona = 'J' THEN c.razon_social ELSE CONCAT(c.apellidos, N', ', c.nombres) END";

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("txt", "text/plain"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"));
    /** Tipos que el navegador puede mostrar sin descargar ("Ver"). */
    public static final List<String> VISIBLES = List.of("pdf", "jpg", "jpeg", "png", "gif");

    private final NamedParameterJdbcTemplate jdbc;
    private final Auditoria auditoria;
    private final Parametros parametros;
    private final AlmacenDocumentos almacen;

    public DocumentosServicio(NamedParameterJdbcTemplate jdbc, Auditoria auditoria, Parametros parametros, AlmacenDocumentos almacen) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.parametros = parametros;
        this.almacen = almacen;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    private static final String SELECT = "SELECT d.id, d.nombre, d.descripcion, d.fecha_documento, d.nombre_archivo, d.tipo_mime, "
            + "d.tamano_bytes, d.hash_sha256, d.ruta_almacenamiento, d.eliminado_en, d.creado_en, d.modificado_en, "
            + "d.expediente_id, d.tipo_documento_id, d.actuacion_id, td.nombre AS tipo, e.numero_interno, e.caratula, "
            + "ee.es_cierre AS expediente_cerrado, c.id AS cliente_id, " + CLIENTE + " AS cliente, "
            + "cr.nombre_usuario AS creado_por_usuario, mo.nombre_usuario AS modificado_por_usuario, "
            + "a.fecha AS actuacion_fecha, ta.nombre AS actuacion_tipo "
            + "FROM documento d JOIN tipo_documento td ON td.id = d.tipo_documento_id JOIN expediente e ON e.id = d.expediente_id "
            + "JOIN estado_expediente ee ON ee.id = e.estado_expediente_id JOIN cliente c ON c.id = e.cliente_id "
            + "LEFT JOIN usuario cr ON cr.id = d.creado_por LEFT JOIN usuario mo ON mo.id = d.modificado_por "
            + "LEFT JOIN actuacion a ON a.id = d.actuacion_id LEFT JOIN tipo_actuacion ta ON ta.id = a.tipo_actuacion_id";

    /** Filtros del buscador. estado: VIGENTES, ELIMINADOS u otro (todos). Fechas: del documento, ambas inclusive. */
    public static final class Filtro {
        public String texto;
        public Long tipo;
        public String expediente;
        public Long expedienteId;
        public LocalDate desde;
        public LocalDate hasta;
        public String estado;
    }

    public List<Map<String, Object>> buscar(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if ("VIGENTES".equals(f.estado)) w.append(" AND d.eliminado_en IS NULL");
        else if ("ELIMINADOS".equals(f.estado)) w.append(" AND d.eliminado_en IS NOT NULL");
        if (f.texto != null && !f.texto.isBlank()) {
            w.append(" AND (d.nombre LIKE :texto OR d.nombre_archivo LIKE :texto OR d.descripcion LIKE :texto)");
            p.addValue("texto", "%" + f.texto.trim() + "%");
        }
        if (f.tipo != null) {
            w.append(" AND d.tipo_documento_id = :tipo");
            p.addValue("tipo", f.tipo);
        }
        if (f.expedienteId != null) {
            w.append(" AND d.expediente_id = :exp");
            p.addValue("exp", f.expedienteId);
        }
        if (f.expediente != null && !f.expediente.isBlank()) {
            w.append(" AND (e.numero_interno LIKE :expediente OR e.caratula LIKE :expediente OR e.numero_expediente_judicial LIKE :expediente "
                    + "OR " + CLIENTE + " LIKE :expediente OR c.numero_documento LIKE :expediente)");
            p.addValue("expediente", "%" + f.expediente.trim() + "%");
        }
        if (f.desde != null) {
            w.append(" AND d.fecha_documento >= :desde");
            p.addValue("desde", f.desde);
        }
        if (f.hasta != null) {
            w.append(" AND d.fecha_documento <= :hasta");
            p.addValue("hasta", f.hasta);
        }
        return Filas.lista(jdbc, SELECT.replace("SELECT ", "SELECT TOP " + (MAXIMO_FILAS + 1) + " ") + w
                + " ORDER BY COALESCE(d.fecha_documento, CAST(d.creado_en AS DATE)) DESC, d.id DESC", p);
    }

    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, SELECT + " WHERE d.id = :id", Map.of("id", id));
    }

    public Optional<DocumentoForm> formulario(long id) {
        return ficha(id).map(f -> {
            DocumentoForm d = new DocumentoForm();
            d.setId(id);
            d.setExpedienteId(numero(f.get("expediente_id")));
            d.setTipoDocumentoId(numero(f.get("tipo_documento_id")));
            d.setNombre((String) f.get("nombre"));
            d.setDescripcion((String) f.get("descripcion"));
            d.setFechaDocumento(f.get("fecha_documento") == null ? null : f.get("fecha_documento").toString());
            d.setActuacionId(numero(f.get("actuacion_id")));
            return d;
        });
    }

    /** Documentos vigentes de un expediente, para su ficha. */
    public List<Map<String, Object>> deExpediente(long expedienteId) {
        Filtro f = new Filtro();
        f.expedienteId = expedienteId;
        f.estado = "VIGENTES";
        return buscar(f);
    }

    private static Long numero(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    public static String extension(String nombreArchivo) {
        if (nombreArchivo == null) return "";
        int punto = nombreArchivo.lastIndexOf('.');
        return punto < 0 ? "" : nombreArchivo.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    public static String tipoMime(String extension) {
        return MIME.getOrDefault(extension, "application/octet-stream");
    }

    public List<String> extensionesPermitidas() {
        return Arrays.stream(parametros.texto("documentos.tipos_permitidos", "pdf,doc,docx,jpg,png").split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public int tamanoMaximoMb() {
        return Math.max(1, parametros.numero("documentos.tamano_max_mb", 20));
    }

    // ------------------------------------------------------------------------------------------------ opciones

    private List<Opcion> opciones(String sql, Long actual) {
        return jdbc.query(sql, Map.of("actual", actual == null ? -1L : actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto"), rs.getString("padre")));
    }

    public List<Opcion> tipos(Long actual) {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM tipo_documento WHERE activo = 1 OR id = :actual ORDER BY nombre", actual);
    }

    public List<Opcion> tiposFiltro() {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM tipo_documento WHERE id <> :actual ORDER BY nombre", null);
    }

    /** Expedientes no cerrados (más el actual). */
    public List<Opcion> expedientes(Long actual) {
        return opciones("SELECT e.id, CONCAT(e.numero_interno, N' · ', e.caratula) AS texto, NULL AS padre FROM expediente e "
                + "JOIN estado_expediente ee ON ee.id = e.estado_expediente_id WHERE ee.es_cierre = 0 OR e.id = :actual "
                + "ORDER BY e.numero_interno DESC", actual);
    }

    /** Actuaciones de los expedientes no cerrados, con su expediente en {@code codigo} para filtrarlas al elegirlo. */
    public List<Opcion> actuaciones(Long expedienteActual) {
        return opciones("SELECT a.id, CONCAT(CONVERT(VARCHAR(10), a.fecha, 103), N' · ', t.nombre, N' · ', LEFT(a.descripcion, 60)) AS texto, "
                + "CAST(a.expediente_id AS VARCHAR(20)) AS padre FROM actuacion a JOIN tipo_actuacion t ON t.id = a.tipo_actuacion_id "
                + "JOIN expediente e ON e.id = a.expediente_id JOIN estado_expediente ee ON ee.id = e.estado_expediente_id "
                + "WHERE ee.es_cierre = 0 OR e.id = :actual ORDER BY a.fecha DESC, a.id DESC", expedienteActual);
    }

    // ------------------------------------------------------------------------------------------------ validación

    /** Valida los datos (y en el alta, el archivo). En {@code fecha[0]} deja la fecha convertida. */
    public Errores validar(DocumentoForm f, MultipartFile archivo, LocalDate[] fecha) {
        Errores e = new Errores();
        DocumentoForm antes = f.getId() == null ? new DocumentoForm() : formulario(f.getId()).orElseThrow();
        if (f.getExpedienteId() == null) e.campo("expedienteId", "Elija el expediente.");
        else if (expedientes(antes.getExpedienteId()).stream().noneMatch(o -> o.getId().equals(f.getExpedienteId()))) {
            e.campo("expedienteId", "Elija un expediente de la lista (no cerrado).");
        }
        if (f.getTipoDocumentoId() == null) e.campo("tipoDocumentoId", "Elija el tipo de documento.");
        else if (tipos(antes.getTipoDocumentoId()).stream().noneMatch(o -> o.getId().equals(f.getTipoDocumentoId()))) {
            e.campo("tipoDocumentoId", "Elija el tipo de documento de la lista.");
        }
        if (f.getNombre() == null && archivo != null && !archivo.isEmpty()) f.setNombre(nombreSinExtension(archivo.getOriginalFilename()));
        if (f.getNombre() == null) e.campo("nombre", "Nombre es requerido.");
        else if (f.getNombre().length() > 200) e.campo("nombre", "Nombre admite hasta 200 caracteres.");
        if (f.getDescripcion() != null && f.getDescripcion().length() > 4000) e.campo("descripcion", "Descripción admite hasta 4000 caracteres.");
        if (f.getFechaDocumento() != null) {
            try {
                fecha[0] = LocalDate.parse(f.getFechaDocumento());
                if (fecha[0].isAfter(LocalDate.now(Filas.ZONA))) e.campo("fechaDocumento", "La fecha del documento no puede ser futura.");
            } catch (DateTimeParseException ex) {
                e.campo("fechaDocumento", "La fecha no es válida.");
            }
        }
        if (f.getActuacionId() != null && f.getExpedienteId() != null && actuaciones(antes.getExpedienteId()).stream()
                .noneMatch(o -> o.getId().equals(f.getActuacionId()) && String.valueOf(f.getExpedienteId()).equals(o.getCodigo()))) {
            e.campo("actuacionId", "La actuación no es de ese expediente.");
        }
        if (f.getId() == null) validarArchivo(archivo, e);
        return e;
    }

    private void validarArchivo(MultipartFile archivo, Errores e) {
        if (archivo == null || archivo.isEmpty()) {
            e.campo("archivo", "Elija el archivo.");
            return;
        }
        String nombre = archivo.getOriginalFilename();
        String ext = extension(nombre);
        List<String> permitidas = extensionesPermitidas();
        if (nombre == null || nombre.length() > 255) e.campo("archivo", "El nombre del archivo admite hasta 255 caracteres.");
        else if (!permitidas.contains(ext)) {
            e.campo("archivo", "No se aceptan archivos ." + (ext.isEmpty() ? "(sin extensión)" : ext) + ". Tipos permitidos: " + String.join(", ", permitidas) + ".");
        } else if (archivo.getSize() > tamanoMaximoMb() * 1024L * 1024L) {
            e.campo("archivo", "El archivo supera el máximo de " + tamanoMaximoMb() + " MB.");
        } else if (!contenidoCorresponde(archivo, ext)) {
            e.campo("archivo", "El contenido del archivo no corresponde a un ." + ext + ": puede estar dañado o tener la extensión cambiada.");
        }
    }

    /** Compara los primeros bytes del archivo con la firma de su tipo. Los tipos sin firma conocida se aceptan. */
    static boolean contenidoCorresponde(MultipartFile archivo, String ext) {
        byte[] b = new byte[8];
        int n;
        try (InputStream in = archivo.getInputStream()) {
            n = in.readNBytes(b, 0, b.length);
        } catch (IOException ex) {
            return false;
        }
        switch (ext) {
            case "pdf": return empieza(b, n, 0x25, 0x50, 0x44, 0x46);                          // %PDF
            case "jpg": case "jpeg": return empieza(b, n, 0xFF, 0xD8, 0xFF);
            case "png": return empieza(b, n, 0x89, 0x50, 0x4E, 0x47);
            case "gif": return empieza(b, n, 0x47, 0x49, 0x46, 0x38);                          // GIF8
            case "docx": case "xlsx": case "pptx": case "odt": case "ods": return empieza(b, n, 0x50, 0x4B, 0x03, 0x04); // ZIP
            case "doc": case "xls": case "ppt": return empieza(b, n, 0xD0, 0xCF, 0x11, 0xE0);  // OLE
            case "rtf": return empieza(b, n, 0x7B, 0x5C, 0x72, 0x74, 0x66);                    // {\rtf
            default: return true;
        }
    }

    private static boolean empieza(byte[] b, int n, int... firma) {
        if (n < firma.length) return false;
        for (int i = 0; i < firma.length; i++) if ((b[i] & 0xFF) != firma[i]) return false;
        return true;
    }

    private static String nombreSinExtension(String archivo) {
        if (archivo == null) return null;
        String base = archivo.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        int punto = base.lastIndexOf('.');
        String s = (punto > 0 ? base.substring(0, punto) : base).replace('_', ' ').trim();
        return s.isEmpty() ? null : (s.length() > 200 ? s.substring(0, 200) : s);
    }

    // ------------------------------------------------------------------------------------------------ cambios

    /**
     * Guarda el archivo y da de alta el documento. Si el mismo archivo ya está en el expediente, no lo guarda y
     * devuelve el error. Si la base falla, borra el archivo recién escrito.
     */
    @Transactional
    public Errores crear(DocumentoForm f, MultipartFile archivo, LocalDate fecha, long[] id) {
        Errores e = new Errores();
        String ext = extension(archivo.getOriginalFilename());
        AlmacenDocumentos.Guardado g;
        try (InputStream in = archivo.getInputStream()) {
            g = almacen.guardar(f.getExpedienteId(), ext, in);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        try {
            Optional<String> repetido = jdbc.query("SELECT TOP 1 nombre FROM documento WHERE expediente_id = :e AND hash_sha256 = :h "
                    + "AND eliminado_en IS NULL", Map.of("e", f.getExpedienteId(), "h", g.hash), rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty());
            if (repetido.isPresent()) {
                almacen.descartar(g.ruta);
                return e.campo("archivo", "Ese archivo ya está cargado en el expediente como «" + repetido.get() + "».");
            }
            String nombreArchivo = archivo.getOriginalFilename().replace('\\', '/');
            nombreArchivo = nombreArchivo.substring(nombreArchivo.lastIndexOf('/') + 1);
            id[0] = jdbc.queryForObject("INSERT INTO documento (expediente_id, tipo_documento_id, nombre, descripcion, fecha_documento, "
                    + "nombre_archivo, ruta_almacenamiento, tipo_mime, tamano_bytes, hash_sha256, actuacion_id, creado_por) OUTPUT inserted.id "
                    + "VALUES (:exp, :tipo, :nombre, :descripcion, :fecha, :archivo, :ruta, :mime, :tamano, :hash, :actuacion, :yo)",
                    datos(f, fecha).addValue("archivo", nombreArchivo).addValue("ruta", g.ruta).addValue("mime", tipoMime(ext))
                            .addValue("tamano", g.tamano).addValue("hash", g.hash), Long.class);
            Map<String, Object> nuevos = f.paraAuditoria();
            nuevos.put("nombre_archivo", nombreArchivo);
            nuevos.put("tamano_bytes", g.tamano);
            nuevos.put("hash_sha256", g.hash);
            auditoria.registrar(Auditoria.ALTA, "documento", id[0], null, nuevos);
            return e;
        } catch (RuntimeException ex) {
            almacen.descartar(g.ruta);
            throw ex;
        }
    }

    private static MapSqlParameterSource datos(DocumentoForm f, LocalDate fecha) {
        return new MapSqlParameterSource().addValue("exp", f.getExpedienteId()).addValue("tipo", f.getTipoDocumentoId())
                .addValue("nombre", f.getNombre()).addValue("descripcion", f.getDescripcion()).addValue("fecha", fecha)
                .addValue("actuacion", f.getActuacionId()).addValue("yo", UsuarioActual.id().orElseThrow());
    }

    /** Modifica los datos del documento (no el archivo). El expediente no cambia: el archivo vive en su carpeta. */
    @Transactional
    public void modificar(DocumentoForm f, LocalDate fecha) {
        DocumentoForm antes = formulario(f.getId()).orElseThrow();
        f.setExpedienteId(antes.getExpedienteId());
        jdbc.update("UPDATE documento SET tipo_documento_id = :tipo, nombre = :nombre, descripcion = :descripcion, fecha_documento = :fecha, "
                + "actuacion_id = :actuacion, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                datos(f, fecha).addValue("id", f.getId()));
        auditoria.registrar(Auditoria.MODIFICACION, "documento", f.getId(), antes.paraAuditoria(), f.paraAuditoria());
    }

    @Transactional
    public void eliminar(long id) {
        jdbc.update("UPDATE documento SET eliminado_en = SYSUTCDATETIME(), modificado_en = SYSUTCDATETIME(), modificado_por = :yo "
                + "WHERE id = :id AND eliminado_en IS NULL", Map.of("id", id, "yo", UsuarioActual.id().orElseThrow()));
        auditoria.registrar(Auditoria.BAJA, "documento", id, Map.of("eliminado", false), Map.of("eliminado", true));
    }

    @Transactional
    public void restaurar(long id) {
        jdbc.update("UPDATE documento SET eliminado_en = NULL, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                Map.of("id", id, "yo", UsuarioActual.id().orElseThrow()));
        auditoria.registrar(Auditoria.MODIFICACION, "documento", id, Map.of("eliminado", true), Map.of("eliminado", false));
    }

    public void registrarDescarga(long id, String modo) {
        auditoria.registrar(Auditoria.DESCARGA, "documento", id, null, Map.of("modo", modo));
    }
}
