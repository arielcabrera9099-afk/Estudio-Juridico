package com.ejemplo.plantilla.datos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Filas de una consulta como mapas (columna → valor), listas para las plantillas: {@code ${fila.nombre}}.
 * Las fechas pasan a java.time: DATE a {@link java.time.LocalDate}, DATETIME2 a {@link LocalDateTime} y
 * DATETIMEOFFSET (momentos guardados en UTC) a la hora local de Paraguay, también como {@link LocalDateTime}.
 * Las claves no distinguen mayúsculas.
 */
public final class Filas {

    /** Hora en la que se muestran los momentos. */
    public static final ZoneId ZONA = ZoneId.of("America/Asuncion");
    /** Código JDBC de SQL Server para DATETIMEOFFSET (microsoft.sql.Types.DATETIMEOFFSET). */
    private static final int DATETIMEOFFSET = -155;

    private static final ColumnMapRowMapper MAPEADOR = new ColumnMapRowMapper() {
        @Override
        protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
            if (rs.getMetaData().getColumnType(index) == DATETIMEOFFSET) {
                OffsetDateTime o = rs.getObject(index, OffsetDateTime.class);
                return o == null ? null : o.atZoneSameInstant(ZONA).toLocalDateTime();
            }
            Object v = super.getColumnValue(rs, index);
            if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime();
            if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate();
            return v;
        }
    };

    private Filas() {
    }

    public static List<Map<String, Object>> lista(NamedParameterJdbcTemplate jdbc, String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, MAPEADOR);
    }

    public static List<Map<String, Object>> lista(NamedParameterJdbcTemplate jdbc, String sql, SqlParameterSource params) {
        return jdbc.query(sql, params, MAPEADOR);
    }

    public static Optional<Map<String, Object>> una(NamedParameterJdbcTemplate jdbc, String sql, Map<String, ?> params) {
        List<Map<String, Object>> l = lista(jdbc, sql, params);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    /** Un momento local de Paraguay como DATETIMEOFFSET, para guardarlo. */
    public static OffsetDateTime momento(LocalDateTime local) {
        return local == null ? null : local.atZone(ZONA).toOffsetDateTime();
    }
}
