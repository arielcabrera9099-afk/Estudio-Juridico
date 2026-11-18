package com.ejemplo.plantilla.web;

/** Limpieza de los textos que llegan de los formularios. */
public final class Textos {

    private Textos() {
    }

    /** Sin espacios de más (adelante, atrás y repetidos); vacío pasa a null. */
    public static String limpio(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    /** Como {@link #limpio} pero conserva los saltos de línea (observaciones, descripciones). */
    public static String multilinea(String s) {
        if (s == null) return null;
        s = s.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return s.isEmpty() ? null : s;
    }

    public static String minusculas(String s) {
        s = limpio(s);
        return s == null ? null : s.toLowerCase();
    }
}
