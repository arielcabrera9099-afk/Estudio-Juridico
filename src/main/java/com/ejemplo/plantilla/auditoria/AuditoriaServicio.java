package com.ejemplo.plantilla.auditoria;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.ejemplo.plantilla.catalogos.Secciones;
import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.web.Opcion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consulta de la auditoría. Es solo lectura: la tabla admite únicamente inserciones desde la aplicación, así que no hay
 * alta, modificación ni baja. Muestra quién hizo qué y cuándo, y en el detalle compara los datos anteriores y nuevos.
 */
@Service
public class AuditoriaServicio {

    public static final int MAXIMO_FILAS = 1000;

    static final Map<String, String> ACCIONES = new LinkedHashMap<>();
    static final Map<String, String> TABLAS = new LinkedHashMap<>();

    static {
        ACCIONES.put("INGRESO", "Ingreso");
        ACCIONES.put("INGRESO_FALLIDO", "Ingreso fallido");
        ACCIONES.put("SALIDA", "Salida");
        ACCIONES.put("ALTA", "Alta");
        ACCIONES.put("MODIFICACION", "Modificación");
        ACCIONES.put("BAJA", "Baja");
        ACCIONES.put("DESCARGA", "Descarga");

        TABLAS.put("usuario", "Usuarios");
        TABLAS.put("rol", "Roles");
        TABLAS.put("parametro", "Parámetros");
        TABLAS.put("cliente", "Clientes");
        TABLAS.put("cliente_contacto", "Contactos de clientes");
        TABLAS.put("materia", "Materias");
        TABLAS.put("tipo_proceso", "Tipos de proceso");
        TABLAS.put("juzgado", "Juzgados");
        TABLAS.put("circunscripcion", "Circunscripciones");
        TABLAS.put("estado_expediente", "Estados del expediente");
        TABLAS.put("tipo_documento", "Tipos de documento");
        TABLAS.put("tipo_evento", "Tipos de evento");
        TABLAS.put("tipo_actuacion", "Tipos de actuación");
        TABLAS.put("rol_parte", "Roles de parte");
        TABLAS.put("expediente", "Expedientes");
        TABLAS.put("expediente_parte", "Partes de expedientes");
        TABLAS.put("actuacion", "Actuaciones");
        TABLAS.put("evento_agenda", "Agenda");
        TABLAS.put("documento", "Documentos");
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Secciones secciones;
    private final ObjectMapper json = new ObjectMapper();

    public AuditoriaServicio(NamedParameterJdbcTemplate jdbc, Secciones secciones) {
        this.jdbc = jdbc;
        this.secciones = secciones;
    }

    /** Filtros de la lista. Las fechas son días de Paraguay, ambos inclusive. */
    public static final class Filtro {
        LocalDate desde;
        LocalDate hasta;
        Long usuario;
        String accion;
        String tabla;
        Long registro;
    }

    public List<Map<String, Object>> buscar(Filtro f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (f.desde != null) {
            w.append(" AND a.fecha >= :desde");
            p.addValue("desde", Filas.momento(f.desde.atStartOfDay()));
        }
        if (f.hasta != null) {
            w.append(" AND a.fecha < :hasta");
            p.addValue("hasta", Filas.momento(f.hasta.plusDays(1).atStartOfDay()));
        }
        if (f.usuario != null) {
            w.append(" AND a.usuario_id = :usuario");
            p.addValue("usuario", f.usuario);
        }
        if (f.accion != null && ACCIONES.containsKey(f.accion)) {
            w.append(" AND a.accion = :accion");
            p.addValue("accion", f.accion);
        }
        if (f.tabla != null && !f.tabla.isBlank()) {
            w.append(" AND a.tabla = :tabla");
            p.addValue("tabla", f.tabla);
        }
        if (f.registro != null) {
            w.append(" AND a.registro_id = :registro");
            p.addValue("registro", f.registro);
        }
        return Filas.lista(jdbc, "SELECT TOP " + (MAXIMO_FILAS + 1) + " a.id, a.fecha, a.accion, a.tabla, a.registro_id, a.ip, "
                + "u.nombre_usuario, CONCAT(u.nombres, ' ', u.apellidos) AS usuario_nombre, "
                + "CASE WHEN a.accion = 'INGRESO_FALLIDO' THEN JSON_VALUE(a.datos_nuevos, '$.usuario') END AS usuario_intentado "
                + "FROM auditoria a LEFT JOIN usuario u ON u.id = a.usuario_id" + w + " ORDER BY a.fecha DESC, a.id DESC", p);
    }

    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, "SELECT a.*, u.nombre_usuario, CONCAT(u.nombres, ' ', u.apellidos) AS usuario_nombre "
                + "FROM auditoria a LEFT JOIN usuario u ON u.id = a.usuario_id WHERE a.id = :id", Map.of("id", id));
    }

    /** Usuarios que aparecen en la auditoría, para el filtro. */
    public List<Opcion> usuarios() {
        return jdbc.query("SELECT u.id, CONCAT(u.nombre_usuario, ' (', u.nombres, ' ', u.apellidos, ')') AS texto FROM usuario u "
                        + "WHERE EXISTS (SELECT 1 FROM auditoria a WHERE a.usuario_id = u.id) ORDER BY u.nombre_usuario",
                Map.of(), (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto")));
    }

    /** Tablas que aparecen en la auditoría, con su nombre legible. */
    public List<Opcion> tablas() {
        List<Opcion> l = new ArrayList<>();
        for (String t : jdbc.queryForList("SELECT DISTINCT tabla FROM auditoria WHERE tabla IS NOT NULL", Map.of(), String.class)) {
            l.add(new Opcion(t, nombreTabla(t)));
        }
        l.sort((a, b) -> a.getTexto().compareToIgnoreCase(b.getTexto()));
        return l;
    }

    public static String nombreTabla(String tabla) {
        return tabla == null ? "" : TABLAS.getOrDefault(tabla, tabla);
    }

    /** La pantalla donde se ve el registro auditado, si existe. */
    public Optional<String> enlace(String tabla, Object registroId) {
        if (tabla == null || registroId == null) return Optional.empty();
        switch (tabla) {
            case "usuario": return Optional.of("/admin/usuarios/" + registroId);
            case "rol": return Optional.of("/admin/roles/" + registroId);
            case "parametro": return Optional.of("/admin/parametros/" + registroId);
            case "cliente": return Optional.of("/clientes/" + registroId);
            case "expediente": return Optional.of("/expedientes/" + registroId);
            case "evento_agenda": return Optional.of("/agenda/" + registroId);
            default:
                return secciones.seccionDe(tabla).map(s -> "/admin/" + s + "/" + tabla + "/" + registroId);
        }
    }

    /** Una fila de la comparación: el campo, su valor anterior y el nuevo, y si cambió. */
    public static final class Cambio {
        private final String campo;
        private final String anterior;
        private final String nuevo;

        Cambio(String campo, String anterior, String nuevo) {
            this.campo = campo;
            this.anterior = anterior;
            this.nuevo = nuevo;
        }

        public String getCampo() { return campo; }
        public String getAnterior() { return anterior; }
        public String getNuevo() { return nuevo; }
        public boolean isCambio() { return !Objects.equals(anterior, nuevo); }
    }

    /** Compara los JSON de datos anteriores y nuevos, campo por campo, en el orden en que aparecen. */
    public List<Cambio> comparar(String anteriores, String nuevos) {
        Map<String, Object> a = leer(anteriores);
        Map<String, Object> n = leer(nuevos);
        Set<String> campos = new LinkedHashSet<>(a.keySet());
        campos.addAll(n.keySet());
        List<Cambio> l = new ArrayList<>();
        for (String c : campos) l.add(new Cambio(c, texto(a, c), texto(n, c)));
        return l;
    }

    private Map<String, Object> leer(String s) {
        if (s == null || s.isBlank()) return Map.of();
        try {
            return json.readValue(s, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            return Map.of("(dato)", s);
        }
    }

    private static String texto(Map<String, Object> m, String campo) {
        if (!m.containsKey(campo)) return null;
        Object v = m.get(campo);
        if (v == null) return "(vacío)";
        if (v instanceof Boolean) return (Boolean) v ? "Sí" : "No";
        if (v instanceof List) return String.join(", ", ((List<?>) v).stream().map(String::valueOf).toArray(String[]::new));
        return v.toString();
    }
}
