package com.ejemplo.plantilla.datos;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.ejemplo.plantilla.web.Opcion;

/**
 * Opciones de los catálogos para los combos de las pantallas: los registros activos (y, donde se indica, el valor ya
 * guardado aunque se haya dado de baja). País, departamento y ciudad se eligen en cascada: el {@code codigo} de cada
 * departamento es el id de su país, y el de cada ciudad, el de su departamento.
 */
@Component
public class Catalogos {

    private final NamedParameterJdbcTemplate jdbc;

    public Catalogos(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Opcion> tiposDocumentoIdentidad() {
        return jdbc.query("SELECT id, nombre, codigo FROM tipo_documento_identidad WHERE activo = 1 ORDER BY id", Map.of(),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getString("codigo")));
    }

    /** Prefijos telefónicos de los países activos: "+595 (Paraguay)". Paraguay primero; el id es el del país. */
    public List<Opcion> prefijos(Long actual) {
        return jdbc.query("SELECT id, CONCAT(prefijo_telefonico, N' (', nombre, N')') AS texto, codigo FROM pais "
                        + "WHERE activo = 1 OR id = :actual ORDER BY CASE WHEN codigo = 'PY' THEN 0 ELSE 1 END, nombre",
                Map.of("actual", actual == null ? -1L : actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto"), rs.getString("codigo")));
    }

    /** Países activos (solo el nombre). Paraguay primero. */
    public List<Opcion> paises(Long actual) {
        return jdbc.query("SELECT id, nombre AS texto, codigo FROM pais "
                        + "WHERE activo = 1 OR id = :actual ORDER BY CASE WHEN codigo = 'PY' THEN 0 ELSE 1 END, nombre",
                Map.of("actual", actual == null ? -1L : actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto"), rs.getString("codigo")));
    }

    /** Departamentos activos de países activos; en {@code codigo}, el id del país. */
    public List<Opcion> departamentos(Long actual) {
        return jdbc.query("SELECT d.id, d.nombre, CAST(d.pais_id AS VARCHAR(20)) AS padre FROM departamento d JOIN pais p ON p.id = d.pais_id "
                        + "WHERE (d.activo = 1 AND p.activo = 1) OR d.id = :actual ORDER BY d.nombre",
                Map.of("actual", actual == null ? -1L : actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getString("padre")));
    }

    /** Ciudades activas de departamentos y países activos; en {@code codigo}, el id del departamento. */
    public List<Opcion> ciudades(Long actual) {
        return jdbc.query("SELECT c.id, c.nombre, CAST(c.departamento_id AS VARCHAR(20)) AS padre FROM ciudad c "
                        + "JOIN departamento d ON d.id = c.departamento_id JOIN pais p ON p.id = d.pais_id "
                        + "WHERE (c.activo = 1 AND d.activo = 1 AND p.activo = 1) OR c.id = :actual ORDER BY c.nombre",
                Map.of("actual", actual == null ? -1L : actual),
                (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("nombre"), rs.getString("padre")));
    }

    /** Si existe un registro activo con ese id en la tabla de catálogo dada (nombre fijo del código, nunca del usuario). */
    public boolean existe(String tabla, Long id) {
        if (id == null) return false;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE id = :id AND activo = 1",
                Map.of("id", id), Integer.class);
        return n != null && n > 0;
    }
}
