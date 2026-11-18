package com.ejemplo.plantilla.catalogos;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ejemplo.plantilla.web.Errores;

/**
 * Reglas de los estados del expediente: un estado de cierre no cuenta como caso activo, y siempre queda al menos un
 * estado activo que cuenta como caso activo (para abrir expedientes) y uno de cierre (para cerrarlos).
 */
final class ReglasEstado {

    private ReglasEstado() {
    }

    static void validar(NamedParameterJdbcTemplate jdbc, Long id, Map<String, Object> v, Errores e) {
        boolean activo = Boolean.TRUE.equals(v.get("es_activo"));
        boolean cierre = Boolean.TRUE.equals(v.get("es_cierre"));
        if (activo && cierre) {
            e.campo("es_cierre", "Un estado de cierre no puede contar como caso activo.");
            return;
        }
        long otro = id == null ? -1L : id;
        boolean registroActivo = id == null || Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT activo FROM estado_expediente WHERE id = :id", Map.of("id", id), Boolean.class));
        if (registroActivo && !activo && otros(jdbc, otro, "es_activo") == 0) {
            e.campo("es_activo", "Es el único estado que cuenta como caso activo: debe quedar al menos uno.");
        }
        if (registroActivo && !cierre && otros(jdbc, otro, "es_cierre") == 0) {
            e.campo("es_cierre", "Es el único estado de cierre: debe quedar al menos uno.");
        }
    }

    static Optional<String> motivoBaja(NamedParameterJdbcTemplate jdbc, long id) {
        Map<String, Object> f = jdbc.queryForMap("SELECT es_activo, es_cierre FROM estado_expediente WHERE id = :id", Map.of("id", id));
        if (Boolean.TRUE.equals(f.get("es_activo")) && otros(jdbc, id, "es_activo") == 0) {
            return Optional.of("Es el único estado que cuenta como caso activo: debe quedar al menos uno.");
        }
        if (Boolean.TRUE.equals(f.get("es_cierre")) && otros(jdbc, id, "es_cierre") == 0) {
            return Optional.of("Es el único estado de cierre: debe quedar al menos uno.");
        }
        return Optional.empty();
    }

    /** Otros estados activos con la marca dada (columna fija: es_activo o es_cierre). */
    private static int otros(NamedParameterJdbcTemplate jdbc, long id, String marca) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM estado_expediente WHERE activo = 1 AND " + marca + " = 1 AND id <> :id",
                Map.of("id", id), Integer.class);
        return n == null ? 0 : n;
    }
}
