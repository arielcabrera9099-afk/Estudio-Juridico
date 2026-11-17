package com.ejemplo.plantilla.web.menu;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Definición del menú lateral. Para agregar pantallas: sumar un {@link ModuloMenu} (o una opción a uno existente)
 * y su ruta en un controlador. El módulo "Referencia" muestra el catálogo de componentes y el login de ejemplo;
 * se puede quitar cuando ya no haga falta.
 */
@Component
public class MenuAplicacion {

    private final List<ModuloMenu> modulos = List.of(
            new ModuloMenu("referencia", "Referencia", "layers", List.of(
                    new SeccionMenu("", List.of(
                            new ItemMenu("componentes", "Componentes"),
                            new ItemMenu("login", "Acceso (login)"))))));

    public List<ModuloMenu> modulos() {
        return modulos;
    }

    /** El módulo que contiene la pantalla {@code pagina}, si está en el menú. */
    public Optional<ModuloMenu> moduloDe(String pagina) {
        return modulos.stream().filter(m -> m.tiene(pagina)).findFirst();
    }
}
