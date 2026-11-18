package com.ejemplo.plantilla.parametros;

import java.util.Arrays;
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
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;

/**
 * Parámetros del sistema. Los crea el sistema (migraciones) porque el código los lee por su clave: aquí solo se
 * consultan y se modifica su valor, que rige de inmediato. Cada clave conocida tiene su regla (rango, formato); las
 * demás se validan según su tipo.
 */
@Service
public class ParametrosServicio {

    /** Extensiones que nunca se aceptan como documento: se ejecutan al abrirlas. */
    private static final Set<String> EXTENSIONES_PELIGROSAS = Set.of("exe", "bat", "cmd", "com", "msi", "scr", "ps1",
            "vbs", "js", "jar", "sh", "dll", "hta", "lnk");

    private static final Map<String, String> AYUDAS = Map.of(
            "seguridad.max_intentos", "Número entero de 3 a 20.",
            "seguridad.minutos_bloqueo", "Número entero de 1 a 1440 (un día).",
            "agenda.dias_aviso", "Números de 0 a 60 separados por comas, sin repetir (0 = el mismo día). Por ejemplo: 7,3,1,0.",
            "documentos.tamano_max_mb", "Número entero de 1 a 100.",
            "documentos.tipos_permitidos", "Extensiones separadas por comas, sin punto. Por ejemplo: pdf,docx,jpg. No se aceptan ejecutables.",
            "expediente.formato_numero", "AAAA es el año y una serie de 3 a 8 N es el número correlativo (con ceros a la izquierda). "
                    + "Se admiten letras, números, guion, barra y punto. Por ejemplo: AAAA-NNNN o EXP/AAAA/NNNNN.");

    private final NamedParameterJdbcTemplate jdbc;
    private final Auditoria auditoria;

    public ParametrosServicio(NamedParameterJdbcTemplate jdbc, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    public List<Map<String, Object>> buscar(String texto, String grupo) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (texto != null && !texto.isBlank()) {
            w.append(" AND (pa.clave LIKE :texto OR pa.descripcion LIKE :texto OR pa.valor LIKE :texto)");
            p.addValue("texto", "%" + texto.trim() + "%");
        }
        if (grupo != null && !grupo.isBlank()) {
            w.append(" AND pa.clave LIKE :grupo");
            p.addValue("grupo", grupo + ".%");
        }
        return Filas.lista(jdbc, "SELECT pa.*, m.nombre_usuario AS modificado_por_usuario FROM parametro pa "
                + "LEFT JOIN usuario m ON m.id = pa.modificado_por" + w + " ORDER BY pa.clave", p);
    }

    /** Los grupos (lo que va antes del punto en la clave), para el filtro. */
    public List<String> grupos() {
        return jdbc.queryForList("SELECT DISTINCT LEFT(clave, CHARINDEX('.', clave + '.') - 1) FROM parametro ORDER BY 1",
                Map.of(), String.class);
    }

    public Optional<Map<String, Object>> ficha(long id) {
        return Filas.una(jdbc, "SELECT pa.*, c.nombre_usuario AS creado_por_usuario, m.nombre_usuario AS modificado_por_usuario "
                + "FROM parametro pa LEFT JOIN usuario c ON c.id = pa.creado_por LEFT JOIN usuario m ON m.id = pa.modificado_por "
                + "WHERE pa.id = :id", Map.of("id", id));
    }

    public String ayuda(String clave, String tipo) {
        String a = AYUDAS.get(clave);
        if (a != null) return a;
        switch (tipo) {
            case "NUMERO": return "Número entero.";
            case "BOOLEANO": return "SI o NO.";
            case "LISTA": return "Valores separados por comas.";
            default: return "Texto de hasta 500 caracteres.";
        }
    }

    // ------------------------------------------------------------------------------------------------ validación

    /**
     * Valida y normaliza el valor (sin espacios de más; las listas ordenadas y sin repetidos). Devuelve el valor a
     * guardar, o los errores en {@code e}.
     */
    public String validar(String clave, String tipo, String valor, Errores e) {
        String v = valor == null ? "" : valor.trim();
        if (v.isEmpty()) {
            e.campo("valor", "Valor es requerido.");
            return v;
        }
        switch (clave) {
            case "seguridad.max_intentos": return entero(v, 3, 20, e);
            case "seguridad.minutos_bloqueo": return entero(v, 1, 1440, e);
            case "documentos.tamano_max_mb": return entero(v, 1, 100, e);
            case "agenda.dias_aviso": return diasAviso(v, e);
            case "documentos.tipos_permitidos": return extensiones(v, e);
            case "expediente.formato_numero": return formatoNumero(v, e);
            default: return porTipo(tipo, v, e);
        }
    }

    private static String entero(String v, int min, int max, Errores e) {
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) e.campo("valor", "El valor debe estar entre " + min + " y " + max + ".");
            return String.valueOf(n);
        } catch (NumberFormatException ex) {
            e.campo("valor", "Ingrese un número entero.");
            return v;
        }
    }

    private static List<String> partes(String v) {
        return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private static String diasAviso(String v, Errores e) {
        List<String> partes = partes(v);
        if (!partes.stream().allMatch(s -> s.matches("\\d{1,2}"))) {
            e.campo("valor", "Ingrese números de 0 a 60 separados por comas.");
            return v;
        }
        List<Integer> dias = partes.stream().map(Integer::valueOf).collect(Collectors.toList());
        if (dias.stream().anyMatch(d -> d > 60)) e.campo("valor", "Cada día debe estar entre 0 y 60.");
        else if (dias.stream().distinct().count() != dias.size()) e.campo("valor", "Hay días repetidos.");
        else if (dias.size() > 10) e.campo("valor", "Se admiten hasta 10 avisos.");
        return dias.stream().sorted((a, b) -> b - a).map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String extensiones(String v, Errores e) {
        List<String> ext = partes(v.toLowerCase()).stream().map(s -> s.replaceFirst("^\\.", "")).collect(Collectors.toList());
        if (!ext.stream().allMatch(s -> s.matches("[a-z0-9]{1,10}"))) {
            e.campo("valor", "Cada extensión lleva solo letras y números (hasta 10), separadas por comas.");
            return v;
        }
        List<String> peligrosas = ext.stream().filter(EXTENSIONES_PELIGROSAS::contains).collect(Collectors.toList());
        if (!peligrosas.isEmpty()) e.campo("valor", "No se aceptan archivos ejecutables: " + String.join(", ", peligrosas) + ".");
        else if (ext.stream().distinct().count() != ext.size()) e.campo("valor", "Hay extensiones repetidas.");
        return String.join(",", ext);
    }

    private static String formatoNumero(String v, Errores e) {
        String f = v.toUpperCase();
        if (f.length() > 16) e.campo("valor", "El formato admite hasta 16 caracteres (el número interno guarda 20 y el correlativo puede crecer).");
        else if (!f.matches("[A-Z0-9/.\\-]+")) e.campo("valor", "Solo se admiten letras, números, guion, barra y punto (sin espacios).");
        else if (!f.matches(".*N{3,8}.*") || f.replaceFirst("N{3,8}", "").contains("N")) {
            // Una sola serie de N: cualquier otra N (suelta, una segunda serie o más de 8) es un error.
            e.campo("valor", "Incluya una sola serie de 3 a 8 N para el número correlativo.");
        }
        return f;
    }

    private static String porTipo(String tipo, String v, Errores e) {
        switch (tipo) {
            case "NUMERO":
                if (!v.matches("-?\\d{1,9}")) e.campo("valor", "Ingrese un número entero.");
                return v;
            case "BOOLEANO":
                String b = v.toUpperCase();
                if (!b.equals("SI") && !b.equals("NO")) e.campo("valor", "Ingrese SI o NO.");
                return b;
            case "LISTA":
                return String.join(",", partes(v));
            default:
                if (v.length() > 500) e.campo("valor", "El valor admite hasta 500 caracteres.");
                return v;
        }
    }

    // ------------------------------------------------------------------------------------------------ cambios

    @Transactional
    public void modificar(long id, String clave, String anterior, String valor) {
        jdbc.update("UPDATE parametro SET valor = :valor, modificado_en = SYSUTCDATETIME(), modificado_por = :yo WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("valor", valor).addValue("yo", UsuarioActual.id().orElse(null)));
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("clave", clave);
        antes.put("valor", anterior);
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("clave", clave);
        despues.put("valor", valor);
        auditoria.registrar(Auditoria.MODIFICACION, "parametro", id, antes, despues);
    }
}
