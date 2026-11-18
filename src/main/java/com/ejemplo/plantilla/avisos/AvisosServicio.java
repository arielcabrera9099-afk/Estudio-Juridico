package com.ejemplo.plantilla.avisos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.datos.Filas;

/**
 * Avisos en el sistema (la campana). Los recordatorios de la agenda cuya hora ya llegó se convierten en notificaciones
 * para su usuario ({@link #procesarPendientes()}, lo llama la tarea programada); cada usuario ve y marca como leídas
 * solo las suyas. Solo canal SISTEMA: no se envían emails.
 */
@Service
public class AvisosServicio {

    public static final int MAXIMO_FILAS = 500;
    private static final Locale ES = new Locale("es", "PY");
    /** Cuántos recordatorios procesa cada pasada de la tarea. */
    static final int LOTE = 200;
    private static final DateTimeFormatter CON_HORA = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy HH:mm", new Locale("es", "PY"));
    private static final DateTimeFormatter SIN_HORA = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy", new Locale("es", "PY"));

    private final NamedParameterJdbcTemplate jdbc;

    public AvisosServicio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------ tarea

    /**
     * Convierte en notificaciones los recordatorios pendientes cuya hora ya llegó. Los bloquea al leerlos y salta los
     * que otra pasada tiene tomados (UPDLOCK, READPAST), así dos pasadas simultáneas no avisan dos veces. Si el evento
     * ya no está pendiente, el recordatorio se anula. Devuelve cuántas notificaciones creó.
     */
    @Transactional
    public int procesarPendientes() {
        List<Map<String, Object>> pendientes = Filas.lista(jdbc, "SELECT TOP " + LOTE + " r.id, r.usuario_id, ev.id AS evento_id, "
                + "ev.titulo, ev.fecha_inicio, ev.todo_el_dia, ev.estado, te.nombre AS tipo, te.codigo AS tipo_codigo, te.es_plazo, "
                + "ex.numero_interno, ex.caratula FROM recordatorio r WITH (UPDLOCK, READPAST) "
                + "JOIN evento_agenda ev ON ev.id = r.evento_agenda_id JOIN tipo_evento te ON te.id = ev.tipo_evento_id "
                + "LEFT JOIN expediente ex ON ex.id = ev.expediente_id "
                + "WHERE r.estado = 'PENDIENTE' AND r.canal = 'SISTEMA' AND r.fecha_aviso <= SYSDATETIMEOFFSET() ORDER BY r.fecha_aviso",
                Map.of());
        LocalDate hoy = LocalDate.now(Filas.ZONA);
        int creadas = 0;
        for (Map<String, Object> r : pendientes) {
            if (!"PENDIENTE".equals(r.get("estado"))) {
                jdbc.update("UPDATE recordatorio SET estado = 'ANULADO' WHERE id = :id", Map.of("id", r.get("id")));
                continue;
            }
            LocalDateTime inicio = (LocalDateTime) r.get("fecha_inicio");
            boolean plazo = Boolean.TRUE.equals(r.get("es_plazo"));
            String tipo = plazo ? "VENCIMIENTO" : "AUDIENCIA".equals(r.get("tipo_codigo")) ? "AUDIENCIA" : "SISTEMA";
            String titulo = recortar((r.get("tipo") + " " + cuando(hoy, inicio.toLocalDate(), plazo) + ": " + r.get("titulo")).toUpperCase(ES), 150);
            String mensaje = (Boolean.TRUE.equals(r.get("todo_el_dia")) ? inicio.format(SIN_HORA) : inicio.format(CON_HORA))
                    + (r.get("numero_interno") == null ? "" : " · Expediente " + r.get("numero_interno") + " · " + r.get("caratula"));
            mensaje = mensaje.toUpperCase(ES);
            jdbc.update("INSERT INTO notificacion (usuario_id, titulo, mensaje, tipo, referencia_tipo, referencia_id) "
                    + "VALUES (:usuario, :titulo, :mensaje, :tipo, 'EVENTO_AGENDA', :evento)",
                    new MapSqlParameterSource("usuario", r.get("usuario_id")).addValue("titulo", titulo).addValue("mensaje", mensaje)
                            .addValue("tipo", tipo).addValue("evento", r.get("evento_id")));
            jdbc.update("UPDATE recordatorio SET estado = 'ENVIADO', enviado_en = SYSUTCDATETIME() WHERE id = :id", Map.of("id", r.get("id")));
            creadas++;
        }
        return creadas;
    }

    /** "hoy", "mañana", "en 3 días", "ayer"; en los plazos vencidos, "venció hace 2 días". */
    static String cuando(LocalDate hoy, LocalDate dia, boolean plazo) {
        long d = ChronoUnit.DAYS.between(hoy, dia);
        if (d == 0) return "hoy";
        if (d == 1) return "mañana";
        if (d > 1) return "en " + d + " días";
        if (plazo) return d == -1 ? "venció ayer" : "venció hace " + (-d) + " días";
        return d == -1 ? "fue ayer" : "fue hace " + (-d) + " días";
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ------------------------------------------------------------------------------------------------ campana

    public int noLeidas(long usuario) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE usuario_id = :u AND leida_en IS NULL",
                Map.of("u", usuario), Integer.class);
        return n == null ? 0 : n;
    }

    /** Las últimas del usuario para el panel de la campana: primero las no leídas. */
    public List<Map<String, Object>> recientes(long usuario, int cantidad) {
        return Filas.lista(jdbc, "SELECT TOP " + cantidad + " * FROM notificacion WHERE usuario_id = :u "
                + "ORDER BY CASE WHEN leida_en IS NULL THEN 0 ELSE 1 END, creado_en DESC, id DESC", Map.of("u", usuario));
    }

    /** estado: NO_LEIDAS, LEIDAS o cualquier otro (todas). */
    public List<Map<String, Object>> buscar(long usuario, String estado, String tipo) {
        MapSqlParameterSource p = new MapSqlParameterSource("u", usuario);
        StringBuilder w = new StringBuilder(" WHERE usuario_id = :u");
        if ("NO_LEIDAS".equals(estado)) w.append(" AND leida_en IS NULL");
        else if ("LEIDAS".equals(estado)) w.append(" AND leida_en IS NOT NULL");
        if (tipo != null && List.of("VENCIMIENTO", "AUDIENCIA", "SISTEMA").contains(tipo)) {
            w.append(" AND tipo = :tipo");
            p.addValue("tipo", tipo);
        }
        return Filas.lista(jdbc, "SELECT TOP " + (MAXIMO_FILAS + 1) + " * FROM notificacion" + w + " ORDER BY creado_en DESC, id DESC", p);
    }

    /** Marca leída una notificación del usuario y devuelve adónde lleva. Vacío si no es suya o no existe. */
    @Transactional
    public Optional<String> abrir(long id, long usuario) {
        Optional<Map<String, Object>> n = Filas.una(jdbc, "SELECT referencia_tipo, referencia_id FROM notificacion "
                + "WHERE id = :id AND usuario_id = :u", Map.of("id", id, "u", usuario));
        if (n.isEmpty()) return Optional.empty();
        marcarLeida(id, usuario);
        return Optional.of(destino((String) n.get().get("referencia_tipo"), n.get().get("referencia_id")));
    }

    public static String destino(String referencia, Object id) {
        if ("EVENTO_AGENDA".equals(referencia) && id != null) return "/agenda/" + id;
        if ("EXPEDIENTE".equals(referencia) && id != null) return "/expedientes/" + id;
        return "/notificaciones";
    }

    @Transactional
    public boolean marcarLeida(long id, long usuario) {
        return jdbc.update("UPDATE notificacion SET leida_en = SYSUTCDATETIME() WHERE id = :id AND usuario_id = :u AND leida_en IS NULL",
                Map.of("id", id, "u", usuario)) > 0;
    }

    @Transactional
    public int marcarTodas(long usuario) {
        return jdbc.update("UPDATE notificacion SET leida_en = SYSUTCDATETIME() WHERE usuario_id = :u AND leida_en IS NULL", Map.of("u", usuario));
    }
}
