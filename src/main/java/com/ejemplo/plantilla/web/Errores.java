package com.ejemplo.plantilla.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Errores de validación de un formulario: por campo (se muestran debajo del campo, en rojo) y generales (arriba del
 * formulario). En las plantillas: {@code ${errores.tiene('email')}}, {@code ${errores.de('email')}}.
 */
public class Errores {

    private final Map<String, String> campos = new LinkedHashMap<>();
    private final List<String> generales = new ArrayList<>();

    /** Error de un campo; si el campo ya tiene uno, queda el primero. */
    public Errores campo(String campo, String mensaje) {
        campos.putIfAbsent(campo, mensaje);
        return this;
    }

    public Errores general(String mensaje) {
        generales.add(mensaje);
        return this;
    }

    public boolean tiene(String campo) { return campos.containsKey(campo); }
    public String de(String campo) { return campos.get(campo); }
    public boolean hay() { return !campos.isEmpty() || !generales.isEmpty(); }
    public Map<String, String> getCampos() { return campos; }
    public List<String> getGenerales() { return generales; }

    /** El mensaje de arriba del formulario. */
    public String getResumen() {
        if (!generales.isEmpty()) return String.join(" ", generales);
        return campos.size() == 1 ? campos.values().iterator().next() : "Revise los datos marcados en rojo.";
    }
}
