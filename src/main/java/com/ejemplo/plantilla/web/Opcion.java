package com.ejemplo.plantilla.web;

/** Una opción de un combo o de una lista de casillas: el valor que viaja ({@code id}) y el texto que se ve. */
public class Opcion {

    private final Object id;
    private final String texto;
    private final String codigo;

    public Opcion(Object id, String texto) {
        this(id, texto, null);
    }

    public Opcion(Object id, String texto, String codigo) {
        this.id = id;
        this.texto = texto;
        this.codigo = codigo;
    }

    public Object getId() { return id; }
    public String getTexto() { return texto; }
    /** Código del catálogo (ABOGADO, CIVIL…), para las reglas que dependen de él. */
    public String getCodigo() { return codigo; }
}
