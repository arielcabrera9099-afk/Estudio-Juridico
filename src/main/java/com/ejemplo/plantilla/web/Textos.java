package com.ejemplo.plantilla.web;

import java.util.Locale;

/**
 * Limpieza de los textos que llegan de los formularios. Todo texto que escribe el usuario se guarda en MAYÚSCULAS
 * (pedido del estudio): {@link #limpio} y {@link #multilinea} ya lo devuelven así. Excepciones: emails y usuario de
 * ingreso ({@link #minusculas}), y valores técnicos que no son texto libre ({@link #recortado}: códigos de color,
 * fechas, extensiones de archivo…). Las contraseñas no pasan por aquí.
 */
public final class Textos {

    private static final Locale ES = new Locale("es");

    private Textos() {
    }

    /** Sin espacios de más (adelante, atrás y repetidos) y en mayúsculas; vacío pasa a null. */
    public static String limpio(String s) {
        s = recortado(s);
        return s == null ? null : s.toUpperCase(ES);
    }

    /** Como {@link #limpio} pero conserva los saltos de línea (observaciones, descripciones). */
    public static String multilinea(String s) {
        if (s == null) return null;
        s = s.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return s.isEmpty() ? null : s.toUpperCase(ES);
    }

    /** Sin espacios de más, sin cambiar mayúsculas ni minúsculas: para valores técnicos que no son texto libre. */
    public static String recortado(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    /** Emails y usuario de ingreso: en minúsculas. */
    public static String minusculas(String s) {
        s = recortado(s);
        return s == null ? null : s.toLowerCase(ES);
    }
}
