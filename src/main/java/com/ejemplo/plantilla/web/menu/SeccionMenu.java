package com.ejemplo.plantilla.web.menu;

import java.util.List;

/** Un grupo de opciones dentro de un módulo, con un rótulo plegable (título vacío = sin rótulo). */
public final class SeccionMenu {

    private final String titulo;
    private final List<ItemMenu> items;

    public SeccionMenu(String titulo, List<ItemMenu> items) {
        this.titulo = titulo;
        this.items = List.copyOf(items);
    }

    public String titulo() { return titulo; }
    public List<ItemMenu> items() { return items; }
}
