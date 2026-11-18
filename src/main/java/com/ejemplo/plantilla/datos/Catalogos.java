package com.ejemplo.plantilla.datos;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.ejemplo.plantilla.web.Opcion;

/** Opciones de los catálogos para los combos de las pantallas (solo los registros activos). */
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

    /** Ciudades con su departamento ("Luque (Central)"), ordenadas por nombre. */
    public List<Opcion> ciudades() {
        return jdbc.query("SELECT c.id, CONCAT(c.nombre, N' (', d.nombre, N')') AS texto FROM ciudad c "
                        + "JOIN departamento d ON d.id = c.departamento_id WHERE c.activo = 1 ORDER BY c.nombre",
                Map.of(), (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("texto")));
    }

    /** Si existe un registro activo con ese id en la tabla de catálogo dada (nombre fijo del código, nunca del usuario). */
    public boolean existe(String tabla, Long id) {
        if (id == null) return false;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE id = :id AND activo = 1",
                Map.of("id", id), Integer.class);
        return n != null && n > 0;
    }
}
