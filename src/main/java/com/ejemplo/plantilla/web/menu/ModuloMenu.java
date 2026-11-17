package com.ejemplo.plantilla.web.menu;

import java.util.List;

/** Un módulo del menú lateral: encabezado con ícono (id del sprite sin "i-") que despliega sus secciones. */
public final class ModuloMenu {

    private final String clave;
    private final String titulo;
    private final String icono;
    private final List<SeccionMenu> secciones;

    public ModuloMenu(String clave, String titulo, String icono, List<SeccionMenu> secciones) {
        this.clave = clave;
        this.titulo = titulo;
        this.icono = icono;
        this.secciones = List.copyOf(secciones);
    }

    public String clave() { return clave; }
    public String titulo() { return titulo; }
    public String icono() { return icono; }
    public List<SeccionMenu> secciones() { return secciones; }

    /** Si la pantalla {@code pagina} es una opción de este módulo. */
    public boolean tiene(String pagina) {
        return secciones.stream().flatMap(s -> s.items().stream()).anyMatch(i -> i.id().equals(pagina));
    }
}
