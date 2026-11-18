package com.ejemplo.plantilla.catalogos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ejemplo.plantilla.web.Errores;

/**
 * Definición de un catálogo: tabla, nombres para los mensajes, si tiene código, sus campos (el primero siempre es el
 * nombre), con qué columnas el nombre debe ser único y las reglas propias del catálogo.
 */
public final class DefCatalogo {

    /** Regla extra al guardar: valores ya convertidos (columna → valor), id null en el alta. */
    public interface Regla {
        void validar(NamedParameterJdbcTemplate jdbc, Long id, Map<String, Object> valores, Errores errores);
    }

    /** Motivo para no dar de baja el registro, si lo hay. */
    public interface ReglaBaja {
        Optional<String> motivo(NamedParameterJdbcTemplate jdbc, long id);
    }

    private final String tabla;
    private final String plural;
    private final String singular;
    private final boolean femenino;
    private String descripcion;
    private boolean conCodigo = true;
    private String orden = "t.nombre";
    private final List<Campo> campos = new ArrayList<>();
    private final List<String> unicoPor = new ArrayList<>();
    private String mensajeRepetido;
    private Regla regla;
    private ReglaBaja reglaBaja;

    /** {@code singular} en minúscula y sin artículo ("tipo de proceso"); {@code femenino} elige "la"/"el". */
    public DefCatalogo(String tabla, String plural, String singular, boolean femenino) {
        this.tabla = tabla;
        this.plural = plural;
        this.singular = singular;
        this.femenino = femenino;
    }

    public DefCatalogo descripcion(String d) { descripcion = d; return this; }
    public DefCatalogo sinCodigo() { conCodigo = false; return this; }
    public DefCatalogo orden(String o) { orden = o; return this; }
    public DefCatalogo campo(Campo c) { campos.add(c); return this; }
    /** El nombre es único entre los registros con el mismo valor en estas columnas (por ejemplo, la misma materia). */
    public DefCatalogo unicoPor(String mensaje, String... columnas) {
        mensajeRepetido = mensaje;
        unicoPor.addAll(List.of(columnas));
        return this;
    }

    public String getMensajeRepetido() {
        return mensajeRepetido != null ? mensajeRepetido
                : "Ya existe " + (femenino ? "una " : "un ") + singular + " con ese nombre.";
    }
    public DefCatalogo regla(Regla r) { regla = r; return this; }
    public DefCatalogo reglaBaja(ReglaBaja r) { reglaBaja = r; return this; }

    public String getTabla() { return tabla; }
    public String getPlural() { return plural; }
    public String getSingular() { return singular; }
    public String getDescripcion() { return descripcion; }
    public boolean isConCodigo() { return conCodigo; }
    public String getOrden() { return orden; }
    public List<Campo> getCampos() { return campos; }
    public List<String> getUnicoPor() { return unicoPor; }
    public Optional<Regla> getRegla() { return Optional.ofNullable(regla); }
    public Optional<ReglaBaja> getReglaBaja() { return Optional.ofNullable(reglaBaja); }

    /** "la materia", "el juzgado". */
    public String getConArticulo() { return (femenino ? "la " : "el ") + singular; }
    /** "Nueva materia", "Nuevo juzgado". */
    public String getTituloNuevo() { return (femenino ? "Nueva " : "Nuevo ") + singular; }
    public String getTituloSingular() { return Character.toUpperCase(singular.charAt(0)) + singular.substring(1); }
    public boolean isFemenino() { return femenino; }

    public Optional<Campo> campo(String columna) {
        return campos.stream().filter(c -> c.getColumna().equals(columna)).findFirst();
    }
}
