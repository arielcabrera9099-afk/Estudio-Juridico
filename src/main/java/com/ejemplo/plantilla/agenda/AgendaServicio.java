package com.ejemplo.plantilla.agenda;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
 * Agenda legal: audiencias, vencimientos, reuniones y diligencias. Reglas:
 * <ul>
 *   <li>Tipo, título, fecha, hora de inicio (salvo "todo el día") y responsable obligatorios. El evento ocurre en un
 *       día; la hora de fin, si se indica, es posterior a la de inicio. Los vencimientos (tipo de plazo) no tienen fin.</li>
 *   <li>Un evento nuevo, o uno al que se le cambia la fecha, no puede quedar en un día pasado.</li>
 *   <li>El expediente es opcional; no se agendan eventos en expedientes cerrados.</li>
 *   <li>Estados: PENDIENTE → REALIZADO (con resultado, cuando ya ocurrió), SUSPENDIDO o CANCELADO (con motivo);
 *       SUSPENDIDO → PENDIENTE (con fecha vigente) o CANCELADO. Realizado y cancelado son finales: no se modifican.</li>
 *   <li>Recordatorios: al guardar un evento pendiente se generan sus avisos al responsable (los plazos según el
 *       parámetro agenda.dias_aviso; los demás tipos, según sus días de aviso). Si cambia la fecha o el responsable
 *       se recalculan; si deja de estar pendiente, se anulan.</li>
 * </ul>
 */
@Service
public class AgendaServicio {

    public static final int MAXIMO_FILAS = 1000;
    public static final String EDITAR = "AGENDA_EDITAR";
    public static final Set<String> ESTADOS = Set.of("PENDIENTE", "REALIZADO", "SUSPENDIDO", "CANCELADO");
    /** Hora a la que se avisa, salvo que el evento sea antes. */
    static final LocalTime HORA_AVISO = LocalTime.of(8, 0);

    private final NamedParameterJdbcTemplate jdbc;
    private final Auditoria auditoria;
    private final Parametros parametros;

    public AgendaServicio(NamedParameterJdbcTemplate jdbc, Auditoria auditoria, Parametros parametros) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.parametros = parametros;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    private static final String SELECT = "SELECT ev.id, ev.titulo, ev.fecha_inicio, ev.fecha_fin, ev.todo_el_dia, ev.lugar, "
            + "ev.estado, ev.resultado, ev.descripcion, ev.expediente_id, ev.juzgado_id, ev.responsable_id, ev.tipo_evento_id, "
            + "te.nombre AS tipo, te.color, te.es_plazo, ex.numero_interno, ex.caratula, j.nombre AS juzgado, "
            + "u.nombre_usuario AS responsable_usuario, CONCAT(u.apellidos, N', ', u.nombres) AS responsable "
            + "FROM evento_agenda ev JOIN tipo_evento te ON te.id = ev.tipo_evento_id JOIN usuario u ON u.id = ev.responsable_id "
            + "LEFT JOIN expediente ex ON ex.id = ev.expediente_id LEFT JOIN juzgado j ON j.id = ev.juzgado_id";

    /** Filtros de la lista y del calendario. Fechas: días de Paraguay, ambos inclusive. */
    public static final class Filtro {
        public LocalDate desde;
        public LocalDate hasta;
        public Long tipo;
        public Long responsable;
        public String estado;
        public String texto;
        public Long expediente;
    }

    public List<Map<String, Object>> buscar(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (f.desde != null) {
            w.append(" AND ev.fecha_inicio >= :desde");
            p.addValue("desde", Filas.momento(f.desde.atStartOfDay()));
        }
        if (f.hasta != null) {
            w.append(" AND ev.fecha_inicio < :hasta");
            p.addValue("hasta", Filas.momento(f.hasta.plusDays(1).atStartOfDay()));
        }
        if (f.tipo != null) {
            w.append(" AND ev.tipo_evento_id = :tipo");
            p.addValue("tipo", f.tipo);
        }
        if (f.responsable != null) {
            w.append(" AND ev.responsable_id = :responsable");
            p.addValue("responsable", f.responsable);
        }
        if (f.expediente != null) {
            w.append(" AND ev.expediente_id = :expediente");
            p.addValue("expediente", f.expediente);
        }
        if (f.estado != null && ESTADOS.contains(f.estado)) {
            w.append(" AND ev.estado = :estado");
            p.addValue("estado", f.estado);
        }
        if (f.texto != null && !f.texto.isBlank()) {
            w.append(" AND (ev.titulo LIKE :texto OR ev.lugar LIKE :texto OR ex.numero_interno LIKE :texto OR ex.caratula LIKE :texto)");
            p.addValue("texto", "%" + f.texto.trim() + "%");
        }
        return Filas.lista(jdbc, SELECT.replace("SELECT ", "SELECT TOP " + (MAXIMO_FILAS + 1) + " ") + w
                + " ORDER BY ev.fecha_inicio, ev.id", p);
    }

    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, SELECT.replace(" FROM evento_agenda ev", ", cr.nombre_usuario AS creado_por_usuario, ev.creado_en, "
                + "ev.modificado_en, mo.nombre_usuario AS modificado_por_usuario FROM evento_agenda ev")
                + " LEFT JOIN usuario cr ON cr.id = ev.creado_por LEFT JOIN usuario mo ON mo.id = ev.modificado_por WHERE ev.id = :id",
                Map.of("id", id));
    }

    public Optional<EventoForm> formulario(long id) {
        return ficha(id).map(f -> {
            EventoForm e = new EventoForm();
            e.setId(id);
            e.setTipoEventoId(numero(f.get("tipo_evento_id")));
            e.setTitulo((String) f.get("titulo"));
            e.setExpedienteId(numero(f.get("expediente_id")));
            LocalDateTime inicio = (LocalDateTime) f.get("fecha_inicio");
            e.setFecha(inicio.toLocalDate().toString());
            e.setTodoElDia(Boolean.TRUE.equals(f.get("todo_el_dia")));
            if (!e.isTodoElDia()) e.setHoraInicio(inicio.toLocalTime().toString());
            LocalDateTime fin = (LocalDateTime) f.get("fecha_fin");
            if (fin != null && !e.isTodoElDia()) e.setHoraFin(fin.toLocalTime().toString());
            e.setLugar((String) f.get("lugar"));
            e.setJuzgadoId(numero(f.get("juzgado_id")));
            e.setResponsableId(numero(f.get("responsable_id")));
            e.setDescripcion((String) f.get("descripcion"));
            return e;
        });
    }

    public List<Map<String, Object>> recordatorios(long eventoId) {
        return Filas.lista(jdbc, "SELECT r.*, u.nombre_usuario AS usuario FROM recordatorio r JOIN usuario u ON u.id = r.usuario_id "
                + "WHERE r.evento_agenda_id = :id ORDER BY r.fecha_aviso", Map.of("id", eventoId));
    }

    private static Long numero(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    public static boolean modificable(Map<String, Object> ficha) {
        return "PENDIENTE".equals(ficha.get("estado")) || "SUSPENDIDO".equals(ficha.get("estado"));
    }

    // ------------------------------------------------------------------------------------------------ opciones

    private List<Opcion> opciones(String sql, Long actual) {
        return jdbc.query(sql, Map.of("actual", actual == null ? -1L : actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto"), rs.getString("codigo")));
    }

    /** Tipos de evento; en {@code codigo}, "PLAZO" para los vencimientos. */
    public List<Opcion> tipos(Long actual) {
        return opciones("SELECT id, nombre AS texto, CASE WHEN es_plazo = 1 THEN 'PLAZO' END AS codigo FROM tipo_evento "
                + "WHERE activo = 1 OR id = :actual ORDER BY nombre", actual);
    }

    public List<Opcion> tiposFiltro() {
        return opciones("SELECT id, nombre AS texto, color AS codigo FROM tipo_evento WHERE id <> :actual ORDER BY nombre", null);
    }

    /** Expedientes no cerrados (más el actual). */
    public List<Opcion> expedientes(Long actual) {
        return opciones("SELECT e.id, CONCAT(e.numero_interno, N' · ', e.caratula) AS texto, NULL AS codigo FROM expediente e "
                + "JOIN estado_expediente ee ON ee.id = e.estado_expediente_id WHERE ee.es_cierre = 0 OR e.id = :actual "
                + "ORDER BY e.numero_interno DESC", actual);
    }

    public List<Opcion> responsables(Long actual) {
        return opciones("SELECT id, CONCAT(apellidos, N', ', nombres) AS texto, NULL AS codigo FROM usuario "
                + "WHERE activo = 1 OR id = :actual ORDER BY texto", actual);
    }

    public List<Opcion> juzgados(Long actual) {
        return opciones("SELECT j.id, CONCAT(j.nombre, CASE WHEN j.turno IS NULL THEN N'' ELSE CONCAT(N' · ', j.turno) END, "
                + "N' (', ci.nombre, N')') AS texto, NULL AS codigo FROM juzgado j JOIN circunscripcion ci ON ci.id = j.circunscripcion_id "
                + "WHERE (j.activo = 1 AND ci.activo = 1) OR j.id = :actual ORDER BY ci.nombre, j.nombre", actual);
    }

    /** Juzgado del expediente, para proponerlo en una audiencia. */
    public Optional<Long> juzgadoDe(long expedienteId) {
        return jdbc.query("SELECT juzgado_id FROM expediente WHERE id = :id", Map.of("id", expedienteId),
                rs -> rs.next() ? Optional.ofNullable(numero(rs.getObject(1))) : Optional.empty());
    }

    // ------------------------------------------------------------------------------------------------ validación

    /** Momentos ya calculados de un formulario válido (hora local). */
    static final class Momentos {
        LocalDateTime inicio;
        LocalDateTime fin;
    }

    public Errores validar(EventoForm f, Momentos m) {
        Errores e = new Errores();
        EventoForm antes = f.getId() == null ? new EventoForm() : formulario(f.getId()).orElseThrow();
        List<Opcion> tipos = tipos(antes.getTipoEventoId());
        Optional<Opcion> tipo = f.getTipoEventoId() == null ? Optional.empty()
                : tipos.stream().filter(o -> o.getId().equals(f.getTipoEventoId())).findFirst();
        if (f.getTipoEventoId() == null) e.campo("tipoEventoId", "Elija el tipo de evento.");
        else if (tipo.isEmpty()) e.campo("tipoEventoId", "Elija el tipo de evento de la lista.");
        boolean plazo = tipo.map(o -> "PLAZO".equals(o.getCodigo())).orElse(false);

        if (f.getTitulo() == null) e.campo("titulo", "Título es requerido.");
        else if (f.getTitulo().length() > 150) e.campo("titulo", "Título admite hasta 150 caracteres.");

        LocalDate dia = null;
        if (f.getFecha() == null) e.campo("fecha", "Fecha es requerida.");
        else {
            try {
                dia = LocalDate.parse(f.getFecha());
            } catch (DateTimeParseException ex) {
                e.campo("fecha", "Fecha no es una fecha válida.");
            }
        }
        LocalTime desde = null;
        LocalTime hasta = null;
        if (!f.isTodoElDia()) {
            desde = hora(e, "horaInicio", "Hora de inicio", f.getHoraInicio(), true);
            if (plazo) f.setHoraFin(null);
            hasta = hora(e, "horaFin", "Hora de fin", f.getHoraFin(), false);
            if (desde != null && hasta != null && !hasta.isAfter(desde)) e.campo("horaFin", "La hora de fin debe ser posterior a la de inicio.");
        }
        if (dia != null && (desde != null || f.isTodoElDia())) {
            m.inicio = dia.atTime(f.isTodoElDia() ? LocalTime.MIDNIGHT : desde);
            m.fin = hasta == null ? null : dia.atTime(hasta);
            boolean cambioFecha = antes.getFecha() == null || !antes.getFecha().equals(f.getFecha());
            if (cambioFecha && dia.isBefore(LocalDate.now(Filas.ZONA))) {
                e.campo("fecha", "La fecha no puede ser anterior a hoy: lo que ya pasó se registra en la bitácora del expediente.");
            }
        }

        if (f.getExpedienteId() != null && expedientes(antes.getExpedienteId()).stream().noneMatch(o -> o.getId().equals(f.getExpedienteId()))) {
            e.campo("expedienteId", "Elija un expediente de la lista (no cerrado).");
        }
        if (f.getJuzgadoId() != null && juzgados(antes.getJuzgadoId()).stream().noneMatch(o -> o.getId().equals(f.getJuzgadoId()))) {
            e.campo("juzgadoId", "Elija el juzgado de la lista.");
        }
        if (f.getResponsableId() == null) e.campo("responsableId", "Elija el responsable.");
        else if (responsables(antes.getResponsableId()).stream().noneMatch(o -> o.getId().equals(f.getResponsableId()))) {
            e.campo("responsableId", "Elija el responsable de la lista.");
        }
        if (f.getLugar() != null && f.getLugar().length() > 200) e.campo("lugar", "Lugar admite hasta 200 caracteres.");
        if (f.getDescripcion() != null && f.getDescripcion().length() > 4000) e.campo("descripcion", "Descripción admite hasta 4000 caracteres.");
        return e;
    }

    private static LocalTime hora(Errores e, String campo, String nombre, String valor, boolean requerida) {
        if (valor == null) {
            if (requerida) e.campo(campo, nombre + " es requerida (o marque «Todo el día»).");
            return null;
        }
        try {
            return LocalTime.parse(valor);
        } catch (DateTimeParseException ex) {
            e.campo(campo, nombre + " no es una hora válida.");
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------------ cambios

    private MapSqlParameterSource parametros(EventoForm f, Momentos m) {
        return new MapSqlParameterSource()
                .addValue("tipo", f.getTipoEventoId())
                .addValue("expediente", f.getExpedienteId())
                .addValue("titulo", f.getTitulo())
                .addValue("descripcion", f.getDescripcion())
                .addValue("inicio", Filas.momento(m.inicio))
                .addValue("fin", Filas.momento(m.fin))
                .addValue("todo", f.isTodoElDia())
                .addValue("lugar", f.getLugar())
                .addValue("juzgado", f.getJuzgadoId())
                .addValue("responsable", f.getResponsableId())
                .addValue("yo", UsuarioActual.id().orElseThrow());
    }

    @Transactional
    public long crear(EventoForm f, Momentos m) {
        Long id = jdbc.queryForObject("INSERT INTO evento_agenda (tipo_evento_id, expediente_id, titulo, descripcion, fecha_inicio, "
                + "fecha_fin, todo_el_dia, lugar, juzgado_id, responsable_id, creado_por) OUTPUT inserted.id VALUES (:tipo, :expediente, "
                + ":titulo, :descripcion, :inicio, :fin, :todo, :lugar, :juzgado, :responsable, :yo)", parametros(f, m), Long.class);
        generarRecordatorios(id);
        auditoria.registrar(Auditoria.ALTA, "evento_agenda", id, null, f.paraAuditoria());
        return id;
    }

    @Transactional
    public void modificar(EventoForm f, Momentos m) {
        EventoForm antes = formulario(f.getId()).orElseThrow();
        jdbc.update("UPDATE evento_agenda SET tipo_evento_id = :tipo, expediente_id = :expediente, titulo = :titulo, "
                + "descripcion = :descripcion, fecha_inicio = :inicio, fecha_fin = :fin, todo_el_dia = :todo, lugar = :lugar, "
                + "juzgado_id = :juzgado, responsable_id = :responsable, modificado_en = SYSUTCDATETIME(), modificado_por = :yo "
                + "WHERE id = :id", parametros(f, m).addValue("id", f.getId()));
        generarRecordatorios(f.getId());
        auditoria.registrar(Auditoria.MODIFICACION, "evento_agenda", f.getId(), antes.paraAuditoria(), f.paraAuditoria());
    }

    /**
     * Cambia el estado. Devuelve los errores (vacíos si se aplicó). {@code resultado}: qué pasó (realizado) o el motivo
     * (suspendido, cancelado).
     */
    @Transactional
    public Errores cambiarEstado(long id, String nuevo, String resultado) {
        Errores e = new Errores();
        Map<String, Object> f = ficha(id).orElseThrow();
        String actual = (String) f.get("estado");
        List<String> posibles = destinos(actual);
        if (nuevo == null || !posibles.contains(nuevo)) {
            e.campo("estado", posibles.isEmpty() ? "El evento está " + actual.toLowerCase() + ": ya no cambia de estado."
                    : "Elija un estado de la lista.");
            return e;
        }
        LocalDateTime inicio = (LocalDateTime) f.get("fecha_inicio");
        LocalDateTime ahora = LocalDateTime.now(Filas.ZONA);
        if ("REALIZADO".equals(nuevo) && inicio.isAfter(ahora)) {
            e.campo("estado", "Todavía no ocurrió: se marca realizado desde su fecha y hora.");
        }
        if ("PENDIENTE".equals(nuevo) && inicio.toLocalDate().isBefore(ahora.toLocalDate())) {
            e.campo("estado", "Su fecha ya pasó: modifíquela antes de volver a dejarlo pendiente.");
        }
        if (resultado == null && !"PENDIENTE".equals(nuevo)) {
            e.campo("resultado", "REALIZADO".equals(nuevo) ? "Indique el resultado (qué pasó)." : "Indique el motivo.");
        } else if (resultado != null && resultado.length() > 4000) {
            e.campo("resultado", "El texto admite hasta 4000 caracteres.");
        }
        if (e.hay()) return e;

        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("estado", nuevo);
        p.put("resultado", resultado);
        p.put("yo", UsuarioActual.id().orElseThrow());
        // Volver a pendiente no lleva texto: se conserva el motivo de la suspensión.
        jdbc.update("UPDATE evento_agenda SET estado = :estado" + (resultado == null ? "" : ", resultado = :resultado")
                + ", modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id", p);
        generarRecordatorios(id);
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("estado", nuevo);
        despues.put("resultado", resultado);
        auditoria.registrar(Auditoria.MODIFICACION, "evento_agenda", id, Map.of("estado", actual), despues);
        return e;
    }

    /** Estados a los que puede pasar un evento desde el actual. */
    public static List<String> destinos(String actual) {
        if ("PENDIENTE".equals(actual)) return List.of("REALIZADO", "SUSPENDIDO", "CANCELADO");
        if ("SUSPENDIDO".equals(actual)) return List.of("PENDIENTE", "CANCELADO");
        return List.of();
    }

    /**
     * Rehace los recordatorios pendientes del evento: anula los que no se enviaron y, si el evento está pendiente,
     * crea los nuevos (solo los que caen en el futuro), para su responsable.
     */
    void generarRecordatorios(long id) {
        jdbc.update("UPDATE recordatorio SET estado = 'ANULADO' WHERE evento_agenda_id = :id AND estado = 'PENDIENTE'", Map.of("id", id));
        Map<String, Object> f = Filas.una(jdbc, "SELECT ev.fecha_inicio, ev.estado, ev.responsable_id, te.es_plazo, "
                + "te.aviso_dias_defecto FROM evento_agenda ev JOIN tipo_evento te ON te.id = ev.tipo_evento_id WHERE ev.id = :id",
                Map.of("id", id)).orElseThrow();
        if (!"PENDIENTE".equals(f.get("estado"))) return;
        List<Integer> dias = diasDeAviso(Boolean.TRUE.equals(f.get("es_plazo")), f.get("aviso_dias_defecto"));
        LocalDateTime inicio = (LocalDateTime) f.get("fecha_inicio");
        LocalDateTime ahora = LocalDateTime.now(Filas.ZONA);
        for (LocalDateTime aviso : momentosDeAviso(inicio, dias)) {
            if (!aviso.isAfter(ahora)) continue;
            jdbc.update("INSERT INTO recordatorio (evento_agenda_id, usuario_id, fecha_aviso, canal) VALUES (:id, :usuario, :aviso, 'SISTEMA')",
                    new MapSqlParameterSource("id", id).addValue("usuario", f.get("responsable_id")).addValue("aviso", Filas.momento(aviso)));
        }
    }

    /** Los plazos usan el parámetro agenda.dias_aviso; los demás tipos, sus días de aviso (o ninguno). */
    List<Integer> diasDeAviso(boolean plazo, Object diasDelTipo) {
        if (plazo) {
            try {
                return Arrays.stream(parametros.texto("agenda.dias_aviso", "3,1,0").split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).map(Integer::valueOf).distinct().collect(Collectors.toList());
            } catch (NumberFormatException ex) {
                return List.of(3, 1, 0);
            }
        }
        return diasDelTipo == null ? List.of() : List.of(((Number) diasDelTipo).intValue());
    }

    /** Cada aviso, a las 8:00 del día que corresponde; si el evento es antes de esa hora, en el momento del evento. */
    static List<LocalDateTime> momentosDeAviso(LocalDateTime inicio, List<Integer> dias) {
        List<LocalDateTime> l = new ArrayList<>();
        for (int d : dias) {
            LocalDateTime aviso = inicio.toLocalDate().minusDays(d).atTime(HORA_AVISO);
            l.add(aviso.isAfter(inicio) ? inicio : aviso);
        }
        return l.stream().distinct().sorted().collect(Collectors.toList());
    }
}
