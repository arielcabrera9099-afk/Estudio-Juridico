package com.ejemplo.plantilla.catalogos;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ejemplo.plantilla.datos.Auditoria;
import com.ejemplo.plantilla.datos.Filas;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.Opcion;
import com.ejemplo.plantilla.web.Textos;

/**
 * ABM genérico de los catálogos descritos en {@link Secciones}. Reglas comunes:
 * <ul>
 *   <li>Código (si el catálogo lo tiene): 2 a 30 caracteres en mayúsculas, letras, números y guion bajo; único y fijo
 *       después del alta. Si se deja vacío se arma con el nombre.</li>
 *   <li>Nombre obligatorio y único (dentro de las columnas de {@code unicoPor}).</li>
 *   <li>Cada campo según su tipo: largo, rango, patrón, opción válida, referencia a un registro activo.</li>
 *   <li>Baja lógica (activo = 0): el registro sigue en el historial pero no se ofrece en las altas. Para reactivar, los
 *       registros de los que depende deben estar activos.</li>
 * </ul>
 * Los nombres de tablas y columnas salen de las definiciones del código; los valores van siempre como parámetros.
 */
@Service
public class CatalogosServicio {

    private static final String PATRON_CODIGO = "^[A-Z][A-Z0-9_]{1,29}$";

    private final NamedParameterJdbcTemplate jdbc;
    private final Auditoria auditoria;

    public CatalogosServicio(NamedParameterJdbcTemplate jdbc, Auditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------------------------------------ consultas

    private static String select(DefCatalogo c) {
        StringBuilder s = new StringBuilder("SELECT t.*");
        StringBuilder j = new StringBuilder();
        for (Campo f : c.getCampos()) {
            if (f.getTipo() != Campo.Tipo.REFERENCIA) continue;
            String a = "r_" + f.getColumna();
            s.append(", ").append(a).append(".nombre AS ").append(f.getColumna()).append("__texto, ")
                    .append(a).append(".activo AS ").append(f.getColumna()).append("__activo");
            j.append(" LEFT JOIN ").append(f.getReferencia()).append(' ').append(a)
                    .append(" ON ").append(a).append(".id = t.").append(f.getColumna());
        }
        return s.append(" FROM ").append(c.getTabla()).append(" t").append(j).toString();
    }

    /** estado: ACTIVOS, INACTIVOS o vacío. filtros: columna de referencia → id. */
    public List<Map<String, Object>> buscar(DefCatalogo c, String texto, String estado, Map<String, Long> filtros) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (texto != null && !texto.isBlank()) {
            w.append(" AND (t.nombre LIKE :texto").append(c.isConCodigo() ? " OR t.codigo LIKE :texto)" : ")");
            p.addValue("texto", "%" + texto.trim() + "%");
        }
        if ("ACTIVOS".equals(estado)) w.append(" AND t.activo = 1");
        else if ("INACTIVOS".equals(estado)) w.append(" AND t.activo = 0");
        for (Campo f : c.getCampos()) {
            Long id = filtros.get(f.getColumna());
            if (f.isFiltro() && id != null) {
                w.append(" AND t.").append(f.getColumna()).append(" = :f_").append(f.getColumna());
                p.addValue("f_" + f.getColumna(), id);
            }
        }
        return Filas.lista(jdbc, select(c) + w + " ORDER BY " + c.getOrden(), p);
    }

    public Optional<Map<String, Object>> ficha(DefCatalogo c, long id) {
        return Filas.una(jdbc, select(c) + " WHERE t.id = :id", Map.of("id", id));
    }

    /** Los valores de un registro como texto, para el formulario. */
    public Map<String, String> formulario(DefCatalogo c, Map<String, Object> ficha) {
        Map<String, String> m = new HashMap<>();
        if (c.isConCodigo()) m.put("codigo", (String) ficha.get("codigo"));
        for (Campo f : c.getCampos()) {
            Object v = ficha.get(f.getColumna());
            m.put(f.getColumna(), v == null ? null : v.toString());
        }
        return m;
    }

    /** Opciones de los combos del formulario: registros activos, más el elegido aunque esté de baja. */
    public Map<String, List<Opcion>> opciones(DefCatalogo c, Map<String, String> valores) {
        Map<String, List<Opcion>> m = new HashMap<>();
        for (Campo f : c.getCampos()) {
            if (f.getTipo() == Campo.Tipo.OPCIONES) m.put(f.getColumna(), f.getOpciones());
            if (f.getTipo() != Campo.Tipo.REFERENCIA) continue;
            long actual = idONegativo(valores.get(f.getColumna()));
            m.put(f.getColumna(), jdbc.query("SELECT id, nombre, activo FROM " + f.getReferencia()
                            + " WHERE activo = 1 OR id = :actual ORDER BY nombre", Map.of("actual", actual),
                    (rs, i) -> new Opcion(String.valueOf(rs.getLong("id")),
                            rs.getString("nombre") + (rs.getBoolean("activo") ? "" : " (dado de baja)"))));
        }
        return m;
    }

    /** Opciones de los filtros de la lista: todos los registros. */
    public Map<String, List<Opcion>> opcionesFiltro(DefCatalogo c) {
        Map<String, List<Opcion>> m = new HashMap<>();
        for (Campo f : c.getCampos()) {
            if (!f.isFiltro()) continue;
            m.put(f.getColumna(), jdbc.query("SELECT id, nombre FROM " + f.getReferencia() + " ORDER BY nombre", Map.of(),
                    (rs, i) -> new Opcion(rs.getLong("id"), rs.getString("nombre"))));
        }
        return m;
    }

    // ------------------------------------------------------------------------------------------------ validación

    /**
     * Normaliza los valores que llegan del formulario (en el mismo mapa), los valida y devuelve los errores. En
     * {@code convertidos} deja los valores listos para la base (columna → String, Integer, Boolean o Long).
     */
    public Errores validar(DefCatalogo c, Long id, Map<String, String> valores, Map<String, Object> convertidos) {
        Errores e = new Errores();
        Map<String, Object> actual = id == null ? Map.of() : ficha(c, id).orElseThrow();
        for (Campo f : c.getCampos()) {
            String col = f.getColumna();
            if (id != null && f.isFijoTrasAlta()) valores.put(col, String.valueOf(actual.get(col)));
            String v = Textos.limpio(valores.get(col));
            valores.put(col, v);
            convertidos.put(col, null);
            if (f.getTipo() == Campo.Tipo.SINO) {
                boolean si = "true".equals(v) || "on".equals(v);
                valores.put(col, String.valueOf(si));
                convertidos.put(col, si);
                continue;
            }
            if (v == null) {
                if (f.isRequerido()) e.campo(col, f.getEtiqueta() + " es requerido.");
                continue;
            }
            switch (f.getTipo()) {
                case TEXTO:
                    if (v.length() > f.getLargo()) e.campo(col, f.getEtiqueta() + " admite hasta " + f.getLargo() + " caracteres.");
                    else if (f.getPatron() != null && !v.matches(f.getPatron())) e.campo(col, f.getMensajePatron());
                    convertidos.put(col, v);
                    break;
                case ENTERO:
                    try {
                        int n = Integer.parseInt(v);
                        if (n < f.getMinimo() || n > f.getMaximo()) {
                            e.campo(col, f.getEtiqueta() + " debe estar entre " + f.getMinimo() + " y " + f.getMaximo() + ".");
                        }
                        convertidos.put(col, n);
                    } catch (NumberFormatException ex) {
                        e.campo(col, f.getEtiqueta() + ": ingrese un número entero.");
                    }
                    break;
                case OPCIONES:
                    if (f.getOpciones().stream().noneMatch(o -> o.getId().equals(v))) e.campo(col, "Elija una opción de la lista.");
                    convertidos.put(col, v);
                    break;
                case REFERENCIA:
                    long ref = idONegativo(v);
                    boolean sinCambio = actual.get(col) != null && ((Number) actual.get(col)).longValue() == ref;
                    Boolean activo = jdbc.query("SELECT activo FROM " + f.getReferencia() + " WHERE id = :id", Map.of("id", ref),
                            rs -> rs.next() ? rs.getBoolean(1) : null);
                    if (activo == null) e.campo(col, "Elija " + f.getReferenciaSingular() + " de la lista.");
                    else if (!activo && !sinCambio) e.campo(col, "Lo elegido está dado de baja: elija otra opción de la lista.");
                    convertidos.put(col, ref);
                    break;
                default:
                    break;
            }
        }
        if (c.isConCodigo()) validarCodigo(c, id, valores, e);
        if (!e.tiene("nombre") && nombreRepetido(c, id, convertidos)) {
            e.campo("nombre", c.getMensajeRepetido());
        }
        if (!e.hay()) c.getRegla().ifPresent(r -> r.validar(jdbc, id, convertidos, e));
        return e;
    }

    private void validarCodigo(DefCatalogo c, Long id, Map<String, String> valores, Errores e) {
        if (id != null) return;
        String codigo = Textos.limpio(valores.get("codigo"));
        if (codigo == null) {
            String nombre = valores.get("nombre");
            if (nombre == null) return;
            codigo = codigoLibre(c, desdeNombre(nombre));
        } else {
            codigo = codigo.toUpperCase().replace(' ', '_');
            if (!codigo.matches(PATRON_CODIGO)) {
                e.campo("codigo", "El código debe tener de 2 a 30 caracteres: letras sin acentos, números y guion bajo, empezando por una letra.");
            } else if (existeCodigo(c, codigo)) {
                e.campo("codigo", "Ya existe " + (c.isFemenino() ? "una " : "un ") + c.getSingular() + " con ese código.");
            }
        }
        valores.put("codigo", codigo);
    }

    /** "Niñez y Adolescencia" → "NINEZ_Y_ADOLESCENCIA" (máximo 30 caracteres). */
    static String desdeNombre(String nombre) {
        String s = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) s = "C_" + s;
        if (s.length() > 30) s = s.substring(0, 30).replaceAll("_+$", "");
        return s.length() < 2 ? s + "_X" : s;
    }

    private String codigoLibre(DefCatalogo c, String base) {
        String codigo = base;
        for (int i = 2; existeCodigo(c, codigo); i++) {
            String sufijo = "_" + i;
            codigo = (base.length() + sufijo.length() > 30 ? base.substring(0, 30 - sufijo.length()) : base) + sufijo;
        }
        return codigo;
    }

    private boolean existeCodigo(DefCatalogo c, String codigo) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + c.getTabla() + " WHERE codigo = :c", Map.of("c", codigo), Integer.class);
        return n != null && n > 0;
    }

    private boolean nombreRepetido(DefCatalogo c, Long id, Map<String, Object> v) {
        MapSqlParameterSource p = new MapSqlParameterSource("nombre", v.get("nombre")).addValue("id", id == null ? -1L : id);
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM " + c.getTabla() + " WHERE nombre = :nombre AND id <> :id");
        for (String col : c.getUnicoPor()) {
            // Sin parámetros nulos en la comparación: los vacíos se comparan como cadena vacía.
            sql.append(" AND ISNULL(CAST(").append(col).append(" AS NVARCHAR(200)), N'') = :u_").append(col);
            p.addValue("u_" + col, v.get(col) == null ? "" : v.get(col).toString());
        }
        Integer n = jdbc.queryForObject(sql.toString(), p, Integer.class);
        return n != null && n > 0;
    }

    private static long idONegativo(String v) {
        try {
            return v == null ? -1L : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    // ------------------------------------------------------------------------------------------------ cambios

    @Transactional
    public long crear(DefCatalogo c, String codigo, Map<String, Object> valores) {
        MapSqlParameterSource p = new MapSqlParameterSource(valores);
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        if (c.isConCodigo()) {
            cols.append("codigo, ");
            vals.append(":codigo, ");
            p.addValue("codigo", codigo);
        }
        for (Campo f : c.getCampos()) {
            cols.append(f.getColumna()).append(", ");
            vals.append(':').append(f.getColumna()).append(", ");
        }
        Long id = jdbc.queryForObject("INSERT INTO " + c.getTabla() + " (" + cols + "activo) OUTPUT inserted.id VALUES ("
                + vals + "1)", p, Long.class);
        auditoria.registrar(Auditoria.ALTA, c.getTabla(), id, null, paraAuditoria(codigo, valores));
        return id;
    }

    @Transactional
    public void modificar(DefCatalogo c, long id, Map<String, Object> valores) {
        Map<String, Object> antes = ficha(c, id).orElseThrow();
        MapSqlParameterSource p = new MapSqlParameterSource(valores).addValue("id", id);
        StringBuilder set = new StringBuilder();
        for (Campo f : c.getCampos()) {
            if (f.isFijoTrasAlta()) continue;
            if (set.length() > 0) set.append(", ");
            set.append(f.getColumna()).append(" = :").append(f.getColumna());
        }
        jdbc.update("UPDATE " + c.getTabla() + " SET " + set + " WHERE id = :id", p);
        Map<String, Object> anteriores = new LinkedHashMap<>();
        c.getCampos().forEach(f -> anteriores.put(f.getColumna(), antes.get(f.getColumna())));
        String codigo = (String) antes.get("codigo");
        auditoria.registrar(Auditoria.MODIFICACION, c.getTabla(), id, paraAuditoria(codigo, anteriores), paraAuditoria(codigo, valores));
    }

    /** Baja lógica. Devuelve el motivo si no se puede. */
    @Transactional
    public Optional<String> darDeBaja(DefCatalogo c, long id) {
        Optional<String> motivo = c.getReglaBaja().flatMap(r -> r.motivo(jdbc, id));
        if (motivo.isEmpty()) cambiarActivo(c, id, false);
        return motivo;
    }

    /** Reactiva el registro si los registros de los que depende están activos; si no, devuelve el motivo. */
    @Transactional
    public Optional<String> reactivar(DefCatalogo c, long id) {
        Map<String, Object> f = ficha(c, id).orElseThrow();
        for (Campo campo : c.getCampos()) {
            if (campo.getTipo() == Campo.Tipo.REFERENCIA && Boolean.FALSE.equals(f.get(campo.getColumna() + "__activo"))) {
                return Optional.of("No se puede reactivar: " + campo.getReferenciaSingular() + " "
                        + f.get(campo.getColumna() + "__texto") + " está dada de baja. Reactívela primero.");
            }
        }
        cambiarActivo(c, id, true);
        return Optional.empty();
    }

    private void cambiarActivo(DefCatalogo c, long id, boolean activo) {
        jdbc.update("UPDATE " + c.getTabla() + " SET activo = :activo WHERE id = :id", Map.of("id", id, "activo", activo));
        auditoria.registrar(activo ? Auditoria.MODIFICACION : Auditoria.BAJA, c.getTabla(), id,
                Map.of("activo", !activo), Map.of("activo", activo));
    }

    private static Map<String, Object> paraAuditoria(String codigo, Map<String, Object> valores) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (codigo != null) m.put("codigo", codigo);
        m.putAll(valores);
        return m;
    }
}
