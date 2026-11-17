package com.ejemplo.plantilla.web.menu;

/** Una opción del menú: {@code id} es la ruta de la pantalla sin la barra inicial ("clientes" es /clientes). */
public final class ItemMenu {

    private final String id;
    private final String titulo;

    public ItemMenu(String id, String titulo) {
        this.id = id;
        this.titulo = titulo;
    }

    public String id() { return id; }
    public String titulo() { return titulo; }
}
