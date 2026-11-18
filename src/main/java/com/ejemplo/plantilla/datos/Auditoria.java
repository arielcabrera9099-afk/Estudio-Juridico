package com.ejemplo.plantilla.datos;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Registro de la tabla {@code auditoria}: quién hizo qué y cuándo. Los datos anteriores y nuevos se guardan como JSON;
 * nunca deben incluir claves ni sus hashes.
 */
@Component
public class Auditoria {

    public static final String INGRESO = "INGRESO";
    public static final String INGRESO_FALLIDO = "INGRESO_FALLIDO";
    public static final String SALIDA = "SALIDA";
    public static final String ALTA = "ALTA";
    public static final String MODIFICACION = "MODIFICACION";
    public static final String BAJA = "BAJA";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public Auditoria(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Registra una acción del usuario de la sesión. */
    public void registrar(String accion, String tabla, Long registroId, Object anteriores, Object nuevos) {
        registrar(UsuarioActual.id().orElse(null), accion, tabla, registroId, anteriores, nuevos);
    }

    /** Registra una acción de un usuario dado (por ejemplo, en el ingreso, antes de que haya sesión). */
    public void registrar(Long usuarioId, String accion, String tabla, Long registroId, Object anteriores, Object nuevos) {
        Map<String, Object> p = new HashMap<>();
        p.put("usuario", usuarioId);
        p.put("accion", accion);
        p.put("tabla", tabla);
        p.put("registro", registroId);
        p.put("anteriores", aJson(anteriores));
        p.put("nuevos", aJson(nuevos));
        p.put("ip", ip());
        jdbc.update("INSERT INTO auditoria (usuario_id, accion, tabla, registro_id, datos_anteriores, datos_nuevos, ip) "
                + "VALUES (:usuario, :accion, :tabla, :registro, :anteriores, :nuevos, :ip)", p);
    }

    private String aJson(Object o) {
        if (o == null) return null;
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo convertir a JSON el dato de auditoría", e);
        }
    }

    private static String ip() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) return null;
        HttpServletRequest r = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = r.getRemoteAddr();
        return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
