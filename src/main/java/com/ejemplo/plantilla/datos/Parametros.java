package com.ejemplo.plantilla.datos;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Valores de la tabla {@code parametro}: cambian el comportamiento sin tocar el código. */
@Component
public class Parametros {

    private final NamedParameterJdbcTemplate jdbc;

    public Parametros(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String texto(String clave, String porDefecto) {
        return jdbc.query("SELECT valor FROM parametro WHERE clave = :clave", Map.of("clave", clave),
                rs -> rs.next() ? rs.getString(1) : porDefecto);
    }

    public int numero(String clave, int porDefecto) {
        try {
            return Integer.parseInt(texto(clave, String.valueOf(porDefecto)).trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
