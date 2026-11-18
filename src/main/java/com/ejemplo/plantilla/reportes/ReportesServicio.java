package com.ejemplo.plantilla.reportes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.web.Opcion;

/**
 * Reportes: no guardan datos propios. Leen las vistas v_expedientes_por_estado y v_agenda con los filtros de la
 * pantalla y calculan los totales con GROUP BY en la consulta (una vista ya agrupada no se podría filtrar por fecha).
 */
@Service
public class ReportesServicio {

    public static final int MAXIMO_FILAS = 5000;

    private final NamedParameterJdbcTemplate jdbc;

    public ReportesServicio(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------ casos

    /** Filtros del reporte de casos. Fechas: de inicio del expediente, ambas inclusive. situacion: ACTIVO, CERRADO u otro. */
    public static final class FiltroCasos {
        public LocalDate desde;
        public LocalDate hasta;
        public Long materia;
        public Long abogado;
        public String situacion;
        /** ESTADO, MATERIA o ABOGADO. */
        public String agrupar = "ESTADO";
    }

    private static String condiciones(FiltroCasos f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (f.desde != null) {
            w.append(" AND v.fecha_inicio >= :desde");
            p.addValue("desde", f.desde);
        }
        if (f.hasta != null) {
            w.append(" AND v.fecha_inicio <= :hasta");
            p.addValue("hasta", f.hasta);
        }
        if (f.materia != null) {
            w.append(" AND v.materia_id = :materia");
            p.addValue("materia", f.materia);
        }
        if (f.abogado != null) {
            w.append(" AND v.abogado_responsable_id = :abogado");
            p.addValue("abogado", f.abogado);
        }
        if ("ACTIVO".equals(f.situacion) || "CERRADO".equals(f.situacion)) {
            w.append(" AND v.situacion = :situacion");
            p.addValue("situacion", f.situacion);
        }
        return w.toString();
    }

    /** Totales por el grupo elegido: activos, cerrados y total, del grupo más grande al más chico. */
    public List<Map<String, Object>> resumenCasos(FiltroCasos f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String grupo;
        String orden;
        switch (f.agrupar) {
            case "MATERIA":
                grupo = "v.materia_nombre";
                orden = "total DESC, grupo";
                break;
            case "ABOGADO":
                grupo = "v.abogado_nombre";
                orden = "total DESC, grupo";
                break;
            default:
                grupo = "v.estado_nombre";
                orden = "MIN(v.estado_orden), grupo";
        }
        return Filas.lista(jdbc, "SELECT " + grupo + " AS grupo, "
                + "SUM(CASE WHEN v.situacion = 'ACTIVO' THEN 1 ELSE 0 END) AS activos, "
                + "SUM(CASE WHEN v.situacion = 'CERRADO' THEN 1 ELSE 0 END) AS cerrados, COUNT(*) AS total "
                + "FROM v_expedientes_por_estado v" + condiciones(f, p) + " GROUP BY " + grupo + " ORDER BY " + orden, p);
    }

    public List<Map<String, Object>> detalleCasos(FiltroCasos f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return Filas.lista(jdbc, "SELECT TOP " + (MAXIMO_FILAS + 1) + " v.*, CASE WHEN c.tipo_persona = 'J' THEN c.razon_social "
                + "ELSE CONCAT(c.apellidos, N', ', c.nombres) END AS cliente FROM v_expedientes_por_estado v "
                + "JOIN expediente e ON e.id = v.expediente_id JOIN cliente c ON c.id = e.cliente_id"
                + condiciones(f, p) + " ORDER BY v.fecha_inicio DESC, v.numero_interno DESC", p);
    }

    // ------------------------------------------------------------------------------------------------ agenda

    /** Filtros del reporte de agenda: el período ya calculado (días de Paraguay, ambos inclusive). */
    public static final class FiltroAgenda {
        public LocalDate desde;
        public LocalDate hasta;
        public Long tipo;
        public Long responsable;
        public String estado;
    }

    private static String condiciones(FiltroAgenda f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE v.fecha_inicio >= :desde AND v.fecha_inicio < :hasta");
        p.addValue("desde", Filas.momento(f.desde.atStartOfDay()));
        p.addValue("hasta", Filas.momento(f.hasta.plusDays(1).atStartOfDay()));
        if (f.tipo != null) {
            w.append(" AND v.tipo_evento_id = :tipo");
            p.addValue("tipo", f.tipo);
        }
        if (f.responsable != null) {
            w.append(" AND v.responsable_id = :responsable");
            p.addValue("responsable", f.responsable);
        }
        if (List.of("PENDIENTE", "REALIZADO", "SUSPENDIDO", "CANCELADO").contains(f.estado)) {
            w.append(" AND v.estado = :estado");
            p.addValue("estado", f.estado);
        }
        return w.toString();
    }

    /** Totales por tipo de evento (o por responsable), con la cantidad en cada estado. */
    public List<Map<String, Object>> resumenAgenda(FiltroAgenda f, boolean porResponsable) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String grupo = porResponsable ? "v.responsable_nombre" : "v.tipo_evento_nombre";
        return Filas.lista(jdbc, "SELECT " + grupo + " AS grupo, " + (porResponsable ? "NULL" : "MIN(v.tipo_evento_color)") + " AS color, "
                + "SUM(CASE WHEN v.estado = 'PENDIENTE' THEN 1 ELSE 0 END) AS pendientes, "
                + "SUM(CASE WHEN v.estado = 'REALIZADO' THEN 1 ELSE 0 END) AS realizados, "
                + "SUM(CASE WHEN v.estado = 'SUSPENDIDO' THEN 1 ELSE 0 END) AS suspendidos, "
                + "SUM(CASE WHEN v.estado = 'CANCELADO' THEN 1 ELSE 0 END) AS cancelados, COUNT(*) AS total "
                + "FROM v_agenda v" + condiciones(f, p) + " GROUP BY " + grupo + " ORDER BY total DESC, grupo", p);
    }

    public List<Map<String, Object>> detalleAgenda(FiltroAgenda f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        return Filas.lista(jdbc, "SELECT TOP " + (MAXIMO_FILAS + 1) + " v.* FROM v_agenda v" + condiciones(f, p)
                + " ORDER BY v.fecha_inicio, v.evento_agenda_id", p);
    }

    // ------------------------------------------------------------------------------------------------ opciones

    private List<Opcion> opciones(String sql) {
        return jdbc.query(sql, Map.of(), (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto"), rs.getString("codigo")));
    }

    public List<Opcion> materias() {
        return opciones("SELECT id, nombre AS texto, NULL AS codigo FROM materia ORDER BY nombre");
    }

    /** Abogados que tienen expedientes. */
    public List<Opcion> abogados() {
        return opciones("SELECT u.id, CONCAT(u.apellidos, N', ', u.nombres) AS texto, NULL AS codigo FROM usuario u "
                + "WHERE EXISTS (SELECT 1 FROM expediente e WHERE e.abogado_responsable_id = u.id) ORDER BY texto");
    }

    public List<Opcion> tiposEvento() {
        return opciones("SELECT id, nombre AS texto, color AS codigo FROM tipo_evento ORDER BY nombre");
    }

    /** Usuarios que tienen eventos a su cargo. */
    public List<Opcion> responsables() {
        return opciones("SELECT u.id, CONCAT(u.apellidos, N', ', u.nombres) AS texto, NULL AS codigo FROM usuario u "
                + "WHERE EXISTS (SELECT 1 FROM evento_agenda ev WHERE ev.responsable_id = u.id) ORDER BY texto");
    }

    public String nombre(String tabla, Long id) {
        if (id == null) return null;
        String sql = "usuario".equals(tabla) ? "SELECT CONCAT(apellidos, N', ', nombres) FROM usuario WHERE id = :id"
                : "SELECT nombre FROM " + tabla + " WHERE id = :id";
        return jdbc.query(sql, Map.of("id", id), rs -> rs.next() ? rs.getString(1) : null);
    }
}
