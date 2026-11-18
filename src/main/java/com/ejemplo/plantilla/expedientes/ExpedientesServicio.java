package com.ejemplo.plantilla.expedientes;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.datos.Parametros;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.Opcion;

/**
 * Expedientes: búsqueda, alta, modificación, cambios de estado (con historial), partes y bitácora de actuaciones.
 * Reglas:
 * <ul>
 *   <li>El número interno lo genera el sistema con el parámetro expediente.formato_numero (AAAA = año del alta, serie
 *       de N = correlativo, que vuelve a 1 cada año si el formato lleva el año).</li>
 *   <li>Cliente, abogado responsable (usuario activo con rol Abogado), materia y tipo de proceso de esa materia son
 *       obligatorios; el juzgado es opcional. Al elegirlos deben estar activos (en una modificación se conserva el
 *       valor anterior aunque se haya dado de baja).</li>
 *   <li>Fecha de inicio no futura. Número judicial con su año. Monto no negativo; en guaraníes sin decimales.</li>
 *   <li>Cada cambio de estado se guarda en el historial en la misma transacción. Pasar a un estado de cierre exige
 *       fecha (no anterior al inicio ni futura) y motivo; cerrar y reabrir requieren el permiso EXPEDIENTES_CERRAR.</li>
 *   <li>Un expediente cerrado no se modifica (ni sus partes ni su bitácora) hasta reabrirlo. Los expedientes no se
 *       borran: se cierran.</li>
 * </ul>
 */
@Service
public class ExpedientesServicio {

    public static final int MAXIMO_FILAS = 500;
    public static final String CERRAR = "EXPEDIENTES_CERRAR";
    private static final String CLIENTE = "CASE WHEN c.tipo_persona = 'J' THEN c.razon_social "
            + "ELSE CONCAT(c.apellidos, N', ', c.nombres) END";
    private static final Pattern SERIE = Pattern.compile("N{3,8}");

    private final NamedParameterJdbcTemplate jdbc;
    private final Auditoria auditoria;
    private final Parametros parametros;

    public ExpedientesServicio(NamedParameterJdbcTemplate jdbc, Auditoria auditoria, Parametros parametros) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.parametros = parametros;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    /** Filtros de la lista. situacion: EN_CURSO (no cerrados), CERRADOS o vacío (todos). */
    public static final class Filtro {
        public String texto;
        public String situacion;
        public Long estado;
        public Long materia;
        public Long abogado;
        public Long cliente;
    }

    public List<Map<String, Object>> buscar(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (f.texto != null && !f.texto.isBlank()) {
            w.append(" AND (e.numero_interno LIKE :texto OR e.caratula LIKE :texto OR e.numero_expediente_judicial LIKE :texto "
                    + "OR ").append(CLIENTE).append(" LIKE :texto OR c.numero_documento LIKE :texto)");
            p.addValue("texto", "%" + f.texto.trim() + "%");
        }
        if ("EN_CURSO".equals(f.situacion)) w.append(" AND ee.es_cierre = 0");
        else if ("CERRADOS".equals(f.situacion)) w.append(" AND ee.es_cierre = 1");
        filtro(w, p, f.estado, "e.estado_expediente_id", "estado");
        filtro(w, p, f.materia, "e.materia_id", "materia");
        filtro(w, p, f.abogado, "e.abogado_responsable_id", "abogado");
        filtro(w, p, f.cliente, "e.cliente_id", "cliente");
        return Filas.lista(jdbc, "SELECT TOP " + (MAXIMO_FILAS + 1) + " e.id, e.numero_interno, e.caratula, e.fecha_inicio, "
                + "e.fecha_cierre, e.numero_expediente_judicial, e.anio_judicial, " + CLIENTE + " AS cliente, c.id AS cliente_id, "
                + "m.nombre AS materia, tp.nombre AS tipo_proceso, j.nombre AS juzgado, j.turno AS juzgado_turno, "
                + "CONCAT(u.apellidos, N', ', u.nombres) AS abogado, ee.nombre AS estado, ee.es_activo, ee.es_cierre "
                + "FROM expediente e JOIN cliente c ON c.id = e.cliente_id JOIN materia m ON m.id = e.materia_id "
                + "JOIN tipo_proceso tp ON tp.id = e.tipo_proceso_id LEFT JOIN juzgado j ON j.id = e.juzgado_id "
                + "JOIN usuario u ON u.id = e.abogado_responsable_id JOIN estado_expediente ee ON ee.id = e.estado_expediente_id"
                + w + " ORDER BY e.fecha_inicio DESC, e.id DESC", p);
    }

    private static void filtro(StringBuilder w, MapSqlParameterSource p, Long valor, String columna, String nombre) {
        if (valor == null) return;
        w.append(" AND ").append(columna).append(" = :").append(nombre);
        p.addValue(nombre, valor);
    }

    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, "SELECT e.*, " + CLIENTE + " AS cliente, c.activo AS cliente_activo, m.nombre AS materia, "
                + "tp.nombre AS tipo_proceso, j.nombre AS juzgado, j.turno AS juzgado_turno, ci.nombre AS circunscripcion, "
                + "CONCAT(u.apellidos, N', ', u.nombres) AS abogado, ee.nombre AS estado, ee.es_activo, ee.es_cierre, "
                + "cr.nombre_usuario AS creado_por_usuario, mo.nombre_usuario AS modificado_por_usuario "
                + "FROM expediente e JOIN cliente c ON c.id = e.cliente_id JOIN materia m ON m.id = e.materia_id "
                + "JOIN tipo_proceso tp ON tp.id = e.tipo_proceso_id LEFT JOIN juzgado j ON j.id = e.juzgado_id "
                + "LEFT JOIN circunscripcion ci ON ci.id = j.circunscripcion_id "
                + "JOIN usuario u ON u.id = e.abogado_responsable_id JOIN estado_expediente ee ON ee.id = e.estado_expediente_id "
                + "LEFT JOIN usuario cr ON cr.id = e.creado_por LEFT JOIN usuario mo ON mo.id = e.modificado_por "
                + "WHERE e.id = :id", Map.of("id", id));
    }

    public boolean cerrado(Map<String, Object> ficha) {
        return Boolean.TRUE.equals(ficha.get("es_cierre"));
    }

    public Optional<ExpedienteForm> formulario(long id) {
        return ficha(id).map(f -> {
            ExpedienteForm x = new ExpedienteForm();
            x.setId(id);
            x.setNumeroInterno((String) f.get("numero_interno"));
            x.setCaratula((String) f.get("caratula"));
            x.setClienteId(numero(f.get("cliente_id")));
            x.setAbogadoId(numero(f.get("abogado_responsable_id")));
            x.setMateriaId(numero(f.get("materia_id")));
            x.setTipoProcesoId(numero(f.get("tipo_proceso_id")));
            x.setJuzgadoId(numero(f.get("juzgado_id")));
            x.setNumeroJudicial((String) f.get("numero_expediente_judicial"));
            x.setAnioJudicial(f.get("anio_judicial") == null ? null : f.get("anio_judicial").toString());
            x.setEstadoId(numero(f.get("estado_expediente_id")));
            x.setFechaInicio(f.get("fecha_inicio").toString());
            x.setDescripcion((String) f.get("descripcion"));
            x.setMoneda((String) f.get("moneda"));
            x.setMonto(montoTexto((BigDecimal) f.get("monto_reclamado"), x.getMoneda()));
            return x;
        });
    }

    private static Long numero(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    /** 1500000 → "1.500.000"; en dólares, con dos decimales ("1.500,50"). */
    public static String montoTexto(BigDecimal monto, String moneda) {
        if (monto == null) return null;
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return new DecimalFormat("PYG".equals(moneda) ? "#,##0" : "#,##0.00", s).format(monto);
    }

    public List<Map<String, Object>> partes(long id) {
        return Filas.lista(jdbc, "SELECT p.*, r.nombre AS rol FROM expediente_parte p JOIN rol_parte r ON r.id = p.rol_parte_id "
                + "WHERE p.expediente_id = :id ORDER BY r.nombre, p.nombre", Map.of("id", id));
    }

    public List<Map<String, Object>> actuaciones(long id) {
        return Filas.lista(jdbc, "SELECT a.*, t.nombre AS tipo, u.nombre_usuario AS usuario FROM actuacion a "
                + "JOIN tipo_actuacion t ON t.id = a.tipo_actuacion_id JOIN usuario u ON u.id = a.usuario_id "
                + "WHERE a.expediente_id = :id ORDER BY a.fecha DESC, a.id DESC", Map.of("id", id));
    }

    public List<Map<String, Object>> historial(long id) {
        return Filas.lista(jdbc, "SELECT h.*, ea.nombre AS anterior, en.nombre AS nuevo, u.nombre_usuario AS usuario "
                + "FROM expediente_estado_historial h LEFT JOIN estado_expediente ea ON ea.id = h.estado_anterior_id "
                + "JOIN estado_expediente en ON en.id = h.estado_nuevo_id JOIN usuario u ON u.id = h.usuario_id "
                + "WHERE h.expediente_id = :id ORDER BY h.fecha DESC, h.id DESC", Map.of("id", id));
    }

    public Optional<ParteForm> parte(long expedienteId, long parteId) {
        return Filas.una(jdbc, "SELECT * FROM expediente_parte WHERE id = :p AND expediente_id = :e",
                Map.of("p", parteId, "e", expedienteId)).map(f -> {
            ParteForm p = new ParteForm();
            p.setId(parteId);
            p.setRolParteId(numero(f.get("rol_parte_id")));
            p.setNombre((String) f.get("nombre"));
            p.setNumeroDocumento((String) f.get("numero_documento"));
            p.setAbogado((String) f.get("abogado"));
            p.setContacto((String) f.get("contacto"));
            p.setObservaciones((String) f.get("observaciones"));
            return p;
        });
    }

    public Optional<ActuacionForm> actuacion(long expedienteId, long actuacionId) {
        return Filas.una(jdbc, "SELECT * FROM actuacion WHERE id = :a AND expediente_id = :e",
                Map.of("a", actuacionId, "e", expedienteId)).map(f -> {
            ActuacionForm a = new ActuacionForm();
            a.setId(actuacionId);
            a.setTipoActuacionId(numero(f.get("tipo_actuacion_id")));
            a.setFecha(f.get("fecha").toString());
            a.setDescripcion((String) f.get("descripcion"));
            return a;
        });
    }

    // ------------------------------------------------------------------------------------------------ opciones

    private List<Opcion> opciones(String sql, Long actual) {
        return jdbc.query(sql, Map.of("actual", actual == null ? -1L : actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto"), rs.getString("padre")));
    }

    public List<Opcion> clientes(Long actual) {
        return opciones("SELECT c.id, CONCAT(" + CLIENTE + ", CASE WHEN c.numero_documento IS NULL THEN N'' "
                + "ELSE CONCAT(N' (', c.numero_documento, N')') END) AS texto, NULL AS padre FROM cliente c "
                + "WHERE c.activo = 1 OR c.id = :actual ORDER BY texto", actual);
    }

    public List<Opcion> abogados(Long actual) {
        return opciones("SELECT u.id, CONCAT(u.apellidos, N', ', u.nombres) AS texto, NULL AS padre FROM usuario u "
                + "WHERE (u.activo = 1 AND EXISTS (SELECT 1 FROM usuario_rol ur JOIN rol r ON r.id = ur.rol_id "
                + "WHERE ur.usuario_id = u.id AND r.codigo = 'ABOGADO' AND r.activo = 1)) OR u.id = :actual ORDER BY texto", actual);
    }

    public List<Opcion> materias(Long actual) {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM materia WHERE activo = 1 OR id = :actual ORDER BY nombre", actual);
    }

    /** Tipos de proceso con su materia en {@code codigo}, para filtrarlos al elegir la materia. */
    public List<Opcion> tiposProceso(Long actual) {
        return opciones("SELECT t.id, t.nombre AS texto, CAST(t.materia_id AS VARCHAR(20)) AS padre FROM tipo_proceso t "
                + "JOIN materia m ON m.id = t.materia_id WHERE (t.activo = 1 AND m.activo = 1) OR t.id = :actual ORDER BY t.nombre", actual);
    }

    public List<Opcion> juzgados(Long actual) {
        return opciones("SELECT j.id, CONCAT(j.nombre, CASE WHEN j.turno IS NULL THEN N'' ELSE CONCAT(N' · ', j.turno) END, "
                + "N' (', ci.nombre, N')') AS texto, NULL AS padre FROM juzgado j JOIN circunscripcion ci ON ci.id = j.circunscripcion_id "
                + "WHERE (j.activo = 1 AND ci.activo = 1) OR j.id = :actual ORDER BY ci.nombre, j.nombre", actual);
    }

    /** Estados para el alta: los activos que no son de cierre, en su orden. */
    public List<Opcion> estadosIniciales() {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM estado_expediente WHERE activo = 1 AND es_cierre = 0 "
                + "AND id <> :actual ORDER BY orden, nombre", null);
    }

    /** Estados a los que puede pasar: activos, distintos del actual; los de cierre solo con permiso de cierre. */
    public List<Opcion> estadosDestino(long actual, boolean puedeCerrar) {
        return jdbc.query("SELECT id, nombre, es_cierre FROM estado_expediente WHERE activo = 1 AND id <> :actual "
                        + (puedeCerrar ? "" : "AND es_cierre = 0 ") + "ORDER BY orden, nombre", Map.of("actual", actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getBoolean("es_cierre") ? "CIERRE" : null));
    }

    public List<Opcion> rolesParte(Long actual) {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM rol_parte WHERE activo = 1 OR id = :actual ORDER BY nombre", actual);
    }

    public List<Opcion> tiposActuacion(Long actual) {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM tipo_actuacion WHERE activo = 1 OR id = :actual ORDER BY nombre", actual);
    }

    public List<Opcion> materiasFiltro() {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM materia WHERE id <> :actual ORDER BY nombre", null);
    }

    public List<Opcion> estadosFiltro() {
        return opciones("SELECT id, nombre AS texto, NULL AS padre FROM estado_expediente WHERE id <> :actual ORDER BY orden, nombre", null);
    }

    public List<Opcion> abogadosFiltro() {
        return opciones("SELECT u.id, CONCAT(u.apellidos, N', ', u.nombres) AS texto, NULL AS padre FROM usuario u "
                + "WHERE u.id <> :actual AND EXISTS (SELECT 1 FROM expediente e WHERE e.abogado_responsable_id = u.id) ORDER BY texto", null);
    }

    // ------------------------------------------------------------------------------------------------ validación

    /** Valores ya convertidos de un formulario de expediente válido. */
    static final class Valores {
        LocalDate fechaInicio;
        Integer anioJudicial;
        BigDecimal monto;
    }

    public Errores validar(ExpedienteForm f, Valores v) {
        Errores e = new Errores();
        ExpedienteForm antes = f.getId() == null ? new ExpedienteForm() : formulario(f.getId()).orElseThrow();
        if (f.getCaratula() == null) e.campo("caratula", "Carátula es requerida.");
        else if (f.getCaratula().length() > 250) e.campo("caratula", "Carátula admite hasta 250 caracteres.");

        elegido(e, "clienteId", "el cliente", f.getClienteId(), antes.getClienteId(), clientes(antes.getClienteId()));
        elegido(e, "abogadoId", "el abogado responsable", f.getAbogadoId(), antes.getAbogadoId(), abogados(antes.getAbogadoId()));
        elegido(e, "materiaId", "la materia", f.getMateriaId(), antes.getMateriaId(), materias(antes.getMateriaId()));
        List<Opcion> tipos = tiposProceso(antes.getTipoProcesoId());
        if (elegido(e, "tipoProcesoId", "el tipo de proceso", f.getTipoProcesoId(), antes.getTipoProcesoId(), tipos)
                && f.getMateriaId() != null && tipos.stream().noneMatch(o -> o.getId().equals(f.getTipoProcesoId())
                && String.valueOf(f.getMateriaId()).equals(o.getCodigo()))) {
            e.campo("tipoProcesoId", "El tipo de proceso no corresponde a la materia elegida.");
        }
        if (f.getJuzgadoId() != null) {
            elegido(e, "juzgadoId", "el juzgado", f.getJuzgadoId(), antes.getJuzgadoId(), juzgados(antes.getJuzgadoId()));
        }

        if (f.getNumeroJudicial() != null && f.getNumeroJudicial().length() > 40) {
            e.campo("numeroJudicial", "Número judicial admite hasta 40 caracteres.");
        }
        int anioActual = LocalDate.now(Filas.ZONA).getYear();
        if (f.getAnioJudicial() != null) {
            if (!f.getAnioJudicial().matches("\\d{4}")) e.campo("anioJudicial", "Ingrese el año con 4 dígitos.");
            else {
                v.anioJudicial = Integer.valueOf(f.getAnioJudicial());
                if (v.anioJudicial < 1900 || v.anioJudicial > anioActual) e.campo("anioJudicial", "El año debe estar entre 1900 y " + anioActual + ".");
            }
        } else if (f.getNumeroJudicial() != null) {
            e.campo("anioJudicial", "Indique el año del número judicial.");
        }

        if (f.getId() == null) {
            if (f.getEstadoId() == null) e.campo("estadoId", "Estado es requerido.");
            else if (estadosIniciales().stream().noneMatch(o -> o.getId().equals(f.getEstadoId()))) {
                e.campo("estadoId", "Elija un estado de la lista (no de cierre).");
            }
        }
        v.fechaInicio = fecha(e, "fechaInicio", "Fecha de inicio", f.getFechaInicio());
        if (v.fechaInicio != null && v.fechaInicio.isAfter(LocalDate.now(Filas.ZONA))) {
            e.campo("fechaInicio", "La fecha de inicio no puede ser futura.");
        }
        if (v.fechaInicio != null && f.getId() != null) {
            Object cierre = ficha(f.getId()).map(x -> x.get("fecha_cierre")).orElse(null);
            if (cierre != null && v.fechaInicio.isAfter((LocalDate) cierre)) e.campo("fechaInicio", "La fecha de inicio no puede ser posterior al cierre.");
        }
        if (f.getDescripcion() != null && f.getDescripcion().length() > 4000) e.campo("descripcion", "Descripción admite hasta 4000 caracteres.");

        if (!"PYG".equals(f.getMoneda()) && !"USD".equals(f.getMoneda())) e.campo("moneda", "Elija la moneda.");
        if (f.getMonto() != null) {
            String s = f.getMonto().replace(" ", "").replace(".", "").replace(',', '.');
            if (!s.matches("\\d{1,13}(\\.\\d{1,2})?")) {
                e.campo("monto", "Ingrese el monto con punto para los miles y coma para los decimales (hasta 2).");
            } else {
                v.monto = new BigDecimal(s);
                if ("PYG".equals(f.getMoneda()) && v.monto.stripTrailingZeros().scale() > 0) e.campo("monto", "Los guaraníes no llevan decimales.");
            }
        }
        return e;
    }

    /**
     * Comprueba que el valor elegido esté entre las opciones (las activas más el valor anterior, que ya vienen así de la
     * consulta). Devuelve true si es válido.
     */
    private static boolean elegido(Errores e, String campo, String nombre, Long valor, Long anterior, List<Opcion> opciones) {
        if (valor == null) {
            e.campo(campo, "Elija " + nombre + ".");
            return false;
        }
        if (opciones.stream().noneMatch(o -> o.getId().equals(valor))) {
            e.campo(campo, "Elija " + nombre + " de la lista.");
            return false;
        }
        return true;
    }

    static LocalDate fecha(Errores e, String campo, String nombre, String valor) {
        if (valor == null) {
            e.campo(campo, nombre + " es requerida.");
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException ex) {
            e.campo(campo, nombre + " no es una fecha válida.");
            return null;
        }
    }

    /** {@code rolAnterior}: el rol guardado (en una modificación se puede conservar aunque se haya dado de baja). */
    public Errores validar(ParteForm p, Long rolAnterior) {
        Errores e = new Errores();
        elegido(e, "rolParteId", "el rol de la parte", p.getRolParteId(), rolAnterior, rolesParte(rolAnterior));
        if (p.getNombre() == null) e.campo("nombre", "Nombre es requerido.");
        else if (p.getNombre().length() > 150) e.campo("nombre", "Nombre admite hasta 150 caracteres.");
        if (p.getNumeroDocumento() != null && !p.getNumeroDocumento().matches("[A-Z0-9.\\-]{3,20}")) {
            e.campo("numeroDocumento", "El documento admite letras, números, punto y guion (3 a 20 caracteres).");
        }
        if (p.getAbogado() != null && p.getAbogado().length() > 120) e.campo("abogado", "Abogado admite hasta 120 caracteres.");
        if (p.getContacto() != null && p.getContacto().length() > 150) e.campo("contacto", "Contacto admite hasta 150 caracteres.");
        if (p.getObservaciones() != null && p.getObservaciones().length() > 2000) e.campo("observaciones", "Observaciones admite hasta 2000 caracteres.");
        return e;
    }

    /** {@code tipoAnterior}: el tipo guardado. En {@code fecha[0]} deja la fecha convertida. */
    public Errores validar(ActuacionForm a, Long tipoAnterior, LocalDate inicio, LocalDate[] fecha) {
        Errores e = new Errores();
        elegido(e, "tipoActuacionId", "el tipo de actuación", a.getTipoActuacionId(), tipoAnterior, tiposActuacion(tipoAnterior));
        fecha[0] = fecha(e, "fecha", "Fecha", a.getFecha());
        if (fecha[0] != null && fecha[0].isAfter(LocalDate.now(Filas.ZONA))) {
            e.campo("fecha", "La fecha no puede ser futura: para lo que vendrá, use la agenda.");
        } else if (fecha[0] != null && fecha[0].isBefore(inicio)) {
            e.campo("fecha", "La fecha no puede ser anterior al inicio del expediente.");
        }
        if (a.getDescripcion() == null) e.campo("descripcion", "Descripción es requerida.");
        else if (a.getDescripcion().length() > 4000) e.campo("descripcion", "Descripción admite hasta 4000 caracteres.");
        return e;
    }

    // ------------------------------------------------------------------------------------------------ cambios

    private MapSqlParameterSource parametros(ExpedienteForm f, Valores v) {
        return new MapSqlParameterSource()
                .addValue("caratula", f.getCaratula())
                .addValue("cliente", f.getClienteId())
                .addValue("abogado", f.getAbogadoId())
                .addValue("materia", f.getMateriaId())
                .addValue("tipo", f.getTipoProcesoId())
                .addValue("juzgado", f.getJuzgadoId())
                .addValue("judicial", f.getNumeroJudicial())
                .addValue("anio", v.anioJudicial)
                .addValue("inicio", v.fechaInicio)
                .addValue("descripcion", f.getDescripcion())
                .addValue("monto", v.monto)
                .addValue("moneda", f.getMoneda())
                .addValue("yo", UsuarioActual.id().orElseThrow());
    }

    @Transactional
    public long crear(ExpedienteForm f, Valores v) {
        f.setNumeroInterno(siguienteNumero());
        Long id = jdbc.queryForObject("INSERT INTO expediente (numero_interno, caratula, cliente_id, abogado_responsable_id, "
                + "materia_id, tipo_proceso_id, juzgado_id, numero_expediente_judicial, anio_judicial, estado_expediente_id, "
                + "fecha_inicio, descripcion, monto_reclamado, moneda, creado_por) OUTPUT inserted.id VALUES (:numero, :caratula, "
                + ":cliente, :abogado, :materia, :tipo, :juzgado, :judicial, :anio, :estado, :inicio, :descripcion, :monto, "
                + ":moneda, :yo)", parametros(f, v).addValue("numero", f.getNumeroInterno()).addValue("estado", f.getEstadoId()), Long.class);
        jdbc.update("INSERT INTO expediente_estado_historial (expediente_id, estado_anterior_id, estado_nuevo_id, usuario_id, observacion) "
                + "VALUES (:id, NULL, :estado, :yo, N'Alta del expediente')",
                Map.of("id", id, "estado", f.getEstadoId(), "yo", UsuarioActual.id().orElseThrow()));
        Map<String, Object> nuevos = f.paraAuditoria();
        nuevos.put("estado_expediente_id", f.getEstadoId());
        auditoria.registrar(Auditoria.ALTA, "expediente", id, null, nuevos);
        return id;
    }

    /**
     * El próximo número interno según el formato del parámetro. Bloquea la lectura hasta el fin de la transacción
     * (UPDLOCK, HOLDLOCK) para que dos altas simultáneas no tomen el mismo número.
     */
    String siguienteNumero() {
        String formato = parametros.texto("expediente.formato_numero", "AAAA-NNNN").trim().toUpperCase();
        if (!SERIE.matcher(formato).find()) formato = "AAAA-NNNN";
        String conAnio = formato.replace("AAAA", String.valueOf(LocalDate.now(Filas.ZONA).getYear()));
        Matcher m = SERIE.matcher(conAnio);
        m.find();
        String prefijo = conAnio.substring(0, m.start());
        String sufijo = conAnio.substring(m.end());
        int ancho = m.end() - m.start();
        // El formato solo admite letras, números, guion, barra y punto: no hay comodines de LIKE que escapar.
        List<String> numeros = jdbc.queryForList("SELECT numero_interno FROM expediente WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE numero_interno LIKE :patron", Map.of("patron", prefijo + "%" + sufijo), String.class);
        int maximo = 0;
        for (String n : numeros) {
            if (n.length() <= prefijo.length() + sufijo.length()) continue;
            String serie = n.substring(prefijo.length(), n.length() - sufijo.length());
            if (serie.matches("\\d{1,9}")) maximo = Math.max(maximo, Integer.parseInt(serie));
        }
        return prefijo + String.format("%0" + ancho + "d", maximo + 1) + sufijo;
    }

    @Transactional
    public void modificar(ExpedienteForm f, Valores v) {
        ExpedienteForm antes = formulario(f.getId()).orElseThrow();
        jdbc.update("UPDATE expediente SET caratula = :caratula, cliente_id = :cliente, abogado_responsable_id = :abogado, "
                + "materia_id = :materia, tipo_proceso_id = :tipo, juzgado_id = :juzgado, numero_expediente_judicial = :judicial, "
                + "anio_judicial = :anio, fecha_inicio = :inicio, descripcion = :descripcion, monto_reclamado = :monto, "
                + "moneda = :moneda, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                parametros(f, v).addValue("id", f.getId()));
        f.setNumeroInterno(antes.getNumeroInterno());
        auditoria.registrar(Auditoria.MODIFICACION, "expediente", f.getId(), antes.paraAuditoria(), f.paraAuditoria());
    }

    /** Valida y aplica un cambio de estado. Devuelve los errores (vacíos si se aplicó). */
    @Transactional
    public Errores cambiarEstado(long id, EstadoForm f, boolean puedeCerrar) {
        Errores e = new Errores();
        Map<String, Object> x = ficha(id).orElseThrow();
        long actual = numero(x.get("estado_expediente_id"));
        boolean estabaCerrado = cerrado(x);
        Optional<Opcion> destino = f.getEstadoId() == null ? Optional.empty()
                : estadosDestino(actual, puedeCerrar).stream().filter(o -> o.getId().equals(f.getEstadoId())).findFirst();
        if (f.getEstadoId() == null) e.campo("estadoId", "Elija el estado nuevo.");
        else if (destino.isEmpty()) e.campo("estadoId", "Elija un estado de la lista.");
        if (estabaCerrado && !puedeCerrar) e.general("Reabrir un expediente requiere el permiso de cierre.");
        boolean cierra = destino.map(o -> "CIERRE".equals(o.getCodigo())).orElse(false);
        LocalDate fechaCierre = null;
        if (cierra) {
            fechaCierre = fecha(e, "fechaCierre", "Fecha de cierre", f.getFechaCierre());
            if (fechaCierre != null && fechaCierre.isAfter(LocalDate.now(Filas.ZONA))) e.campo("fechaCierre", "La fecha de cierre no puede ser futura.");
            else if (fechaCierre != null && fechaCierre.isBefore((LocalDate) x.get("fecha_inicio"))) {
                e.campo("fechaCierre", "La fecha de cierre no puede ser anterior al inicio del expediente.");
            }
            if (f.getMotivoCierre() == null) e.campo("motivoCierre", "Indique el motivo del cierre (sentencia, acuerdo, desistimiento…).");
            else if (f.getMotivoCierre().length() > 2000) e.campo("motivoCierre", "Motivo admite hasta 2000 caracteres.");
        }
        if (estabaCerrado && !cierra && f.getObservacion() == null) e.campo("observacion", "Indique por qué se reabre el expediente.");
        if (f.getObservacion() != null && f.getObservacion().length() > 2000) e.campo("observacion", "Observación admite hasta 2000 caracteres.");
        if (e.hay()) return e;

        long yo = UsuarioActual.id().orElseThrow();
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("estado", f.getEstadoId());
        p.put("yo", yo);
        // Al cerrar se guardan fecha y motivo; al pasar a un estado que no es de cierre se limpian (reapertura).
        p.put("fecha", cierra ? fechaCierre : null);
        p.put("motivo", cierra ? f.getMotivoCierre() : null);
        jdbc.update("UPDATE expediente SET estado_expediente_id = :estado, fecha_cierre = :fecha, motivo_cierre = :motivo, "
                + "modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id", p);
        Map<String, Object> h = new HashMap<>();
        h.put("id", id);
        h.put("anterior", actual);
        h.put("nuevo", f.getEstadoId());
        h.put("yo", yo);
        h.put("obs", f.getObservacion());
        jdbc.update("INSERT INTO expediente_estado_historial (expediente_id, estado_anterior_id, estado_nuevo_id, usuario_id, observacion) "
                + "VALUES (:id, :anterior, :nuevo, :yo, :obs)", h);
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("estado_expediente_id", actual);
        antes.put("fecha_cierre", x.get("fecha_cierre"));
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("estado_expediente_id", f.getEstadoId());
        despues.put("fecha_cierre", fechaCierre);
        despues.put("motivo_cierre", cierra ? f.getMotivoCierre() : null);
        despues.put("observacion", f.getObservacion());
        auditoria.registrar(Auditoria.MODIFICACION, "expediente", id, antes, despues);
        return e;
    }

    @Transactional
    public long crearParte(long expedienteId, ParteForm p) {
        Long id = jdbc.queryForObject("INSERT INTO expediente_parte (expediente_id, rol_parte_id, nombre, numero_documento, abogado, "
                + "contacto, observaciones, creado_por) OUTPUT inserted.id VALUES (:e, :rol, :nombre, :doc, :abogado, :contacto, :obs, :yo)",
                parametros(p).addValue("e", expedienteId), Long.class);
        auditoria.registrar(Auditoria.ALTA, "expediente_parte", id, null, p.paraAuditoria());
        return id;
    }

    @Transactional
    public void modificarParte(long expedienteId, ParteForm p) {
        ParteForm antes = parte(expedienteId, p.getId()).orElseThrow();
        jdbc.update("UPDATE expediente_parte SET rol_parte_id = :rol, nombre = :nombre, numero_documento = :doc, abogado = :abogado, "
                + "contacto = :contacto, observaciones = :obs, modificado_en = SYSUTCDATETIME(), modificado_por = :yo "
                + "WHERE id = :id AND expediente_id = :e", parametros(p).addValue("id", p.getId()).addValue("e", expedienteId));
        auditoria.registrar(Auditoria.MODIFICACION, "expediente_parte", p.getId(), antes.paraAuditoria(), p.paraAuditoria());
    }

    @Transactional
    public void eliminarParte(long expedienteId, long parteId) {
        ParteForm antes = parte(expedienteId, parteId).orElseThrow();
        jdbc.update("DELETE FROM expediente_parte WHERE id = :id AND expediente_id = :e", Map.of("id", parteId, "e", expedienteId));
        auditoria.registrar(Auditoria.BAJA, "expediente_parte", parteId, antes.paraAuditoria(), null);
    }

    private static MapSqlParameterSource parametros(ParteForm p) {
        return new MapSqlParameterSource().addValue("rol", p.getRolParteId()).addValue("nombre", p.getNombre())
                .addValue("doc", p.getNumeroDocumento()).addValue("abogado", p.getAbogado()).addValue("contacto", p.getContacto())
                .addValue("obs", p.getObservaciones()).addValue("yo", UsuarioActual.id().orElseThrow());
    }

    @Transactional
    public long crearActuacion(long expedienteId, ActuacionForm a, LocalDate fecha) {
        Long id = jdbc.queryForObject("INSERT INTO actuacion (expediente_id, tipo_actuacion_id, fecha, descripcion, usuario_id, creado_por) "
                + "OUTPUT inserted.id VALUES (:e, :tipo, :fecha, :descripcion, :yo, :yo)",
                parametros(a, fecha).addValue("e", expedienteId), Long.class);
        auditoria.registrar(Auditoria.ALTA, "actuacion", id, null, a.paraAuditoria());
        return id;
    }

    @Transactional
    public void modificarActuacion(long expedienteId, ActuacionForm a, LocalDate fecha) {
        ActuacionForm antes = actuacion(expedienteId, a.getId()).orElseThrow();
        jdbc.update("UPDATE actuacion SET tipo_actuacion_id = :tipo, fecha = :fecha, descripcion = :descripcion, "
                + "modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id AND expediente_id = :e",
                parametros(a, fecha).addValue("id", a.getId()).addValue("e", expedienteId));
        auditoria.registrar(Auditoria.MODIFICACION, "actuacion", a.getId(), antes.paraAuditoria(), a.paraAuditoria());
    }

    /** Elimina una actuación. No se puede si tiene documentos adjuntos. Devuelve el motivo si no se puede. */
    @Transactional
    public Optional<String> eliminarActuacion(long expedienteId, long actuacionId) {
        ActuacionForm antes = actuacion(expedienteId, actuacionId).orElseThrow();
        Integer docs = jdbc.queryForObject("SELECT COUNT(*) FROM documento WHERE actuacion_id = :id", Map.of("id", actuacionId), Integer.class);
        if (docs != null && docs > 0) return Optional.of("La actuación tiene documentos adjuntos: no se puede eliminar.");
        jdbc.update("DELETE FROM actuacion WHERE id = :id AND expediente_id = :e", Map.of("id", actuacionId, "e", expedienteId));
        auditoria.registrar(Auditoria.BAJA, "actuacion", actuacionId, antes.paraAuditoria(), null);
        return Optional.empty();
    }

    private static MapSqlParameterSource parametros(ActuacionForm a, LocalDate fecha) {
        return new MapSqlParameterSource().addValue("tipo", a.getTipoActuacionId()).addValue("fecha", fecha)
                .addValue("descripcion", a.getDescripcion()).addValue("yo", UsuarioActual.id().orElseThrow());
    }
}
