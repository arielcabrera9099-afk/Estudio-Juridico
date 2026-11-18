package com.ejemplo.plantilla.catalogos;

import java.util.List;

import com.ejemplo.plantilla.web.Opcion;

/**
 * Un campo de un catálogo: su columna (también es el nombre del parámetro del formulario), cómo se ve y cómo se valida.
 * Las columnas y tablas salen siempre de las definiciones del código ({@link Secciones}), nunca del usuario.
 */
public final class Campo {

    public enum Tipo { TEXTO, ENTERO, SINO, OPCIONES, REFERENCIA }

    private final String columna;
    private final String etiqueta;
    private final Tipo tipo;
    private boolean requerido;
    private int largo = 60;
    private int minimo;
    private int maximo = Integer.MAX_VALUE;
    private String patron;
    private String mensajePatron;
    private String ayuda;
    private String referencia;
    private String referenciaSingular;
    private List<Opcion> opciones = List.of();
    private boolean enLista;
    private boolean filtro;
    private boolean fijoTrasAlta;

    private Campo(String columna, String etiqueta, Tipo tipo) {
        this.columna = columna;
        this.etiqueta = etiqueta;
        this.tipo = tipo;
    }

    public static Campo texto(String columna, String etiqueta, int largo) {
        Campo c = new Campo(columna, etiqueta, Tipo.TEXTO);
        c.largo = largo;
        return c;
    }

    public static Campo entero(String columna, String etiqueta, int minimo, int maximo) {
        Campo c = new Campo(columna, etiqueta, Tipo.ENTERO);
        c.minimo = minimo;
        c.maximo = maximo;
        return c;
    }

    public static Campo sino(String columna, String etiqueta) {
        return new Campo(columna, etiqueta, Tipo.SINO);
    }

    public static Campo opciones(String columna, String etiqueta, List<Opcion> opciones) {
        Campo c = new Campo(columna, etiqueta, Tipo.OPCIONES);
        c.opciones = opciones;
        return c;
    }

    /** Clave foránea a otro catálogo (tabla con id, nombre y activo). {@code singular}: "la materia", "la circunscripción". */
    public static Campo referencia(String columna, String etiqueta, String tabla, String singular) {
        Campo c = new Campo(columna, etiqueta, Tipo.REFERENCIA);
        c.referencia = tabla;
        c.referenciaSingular = singular;
        return c;
    }

    public Campo requerido() { requerido = true; return this; }
    public Campo enLista() { enLista = true; return this; }
    /** Además de en la lista, se puede filtrar por él (solo referencias). */
    public Campo filtro() { filtro = true; enLista = true; return this; }
    public Campo ayuda(String ayuda) { this.ayuda = ayuda; return this; }
    public Campo patron(String patron, String mensaje) { this.patron = patron; this.mensajePatron = mensaje; return this; }
    /** No se cambia después del alta (por ejemplo, porque otras tablas lo usan en su clave foránea). */
    public Campo fijoTrasAlta() { fijoTrasAlta = true; return this; }

    public String getColumna() { return columna; }
    public String getEtiqueta() { return etiqueta; }
    public Tipo getTipo() { return tipo; }
    public String getTipoNombre() { return tipo.name(); }
    public boolean isRequerido() { return requerido; }
    public int getLargo() { return largo; }
    public int getMinimo() { return minimo; }
    public int getMaximo() { return maximo; }
    public String getPatron() { return patron; }
    public String getMensajePatron() { return mensajePatron; }
    public String getAyuda() { return ayuda; }
    public String getReferencia() { return referencia; }
    public String getReferenciaSingular() { return referenciaSingular; }
    public List<Opcion> getOpciones() { return opciones; }
    public boolean isEnLista() { return enLista; }
    public boolean isFiltro() { return filtro; }
    public boolean isFijoTrasAlta() { return fijoTrasAlta; }

    /** El texto de una opción fija (por ejemplo, el nombre de un color). */
    public String textoDe(Object valor) {
        if (valor == null) return "";
        return opciones.stream().filter(o -> o.getId().equals(valor.toString())).map(Opcion::getTexto).findFirst()
                .orElse(valor.toString());
    }
}
