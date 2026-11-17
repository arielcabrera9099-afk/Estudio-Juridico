package com.ejemplo.plantilla.web;

import java.util.List;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ejemplo.plantilla.config.AppProperties;
import com.ejemplo.plantilla.web.menu.MenuAplicacion;
import com.ejemplo.plantilla.web.menu.ModuloMenu;

/**
 * Lo que el layout necesita en todas las pantallas: la identidad de la aplicación, el menú y el color de acento de
 * los módulos. Cada controlador marca la pantalla actual con {@link #pantalla(Model, String, String)}.
 */
@ControllerAdvice
public class ModeloComun {

    /** Color de acento de los módulos del menú (el azul de marca de tokens.css, --wwp-base). */
    static final String ACENTO = "#001A81";

    private final AppProperties app;
    private final MenuAplicacion menu;

    public ModeloComun(AppProperties app, MenuAplicacion menu) {
        this.app = app;
        this.menu = menu;
    }

    @ModelAttribute("app")
    public AppProperties app() {
        return app;
    }

    @ModelAttribute("modulos")
    public List<ModuloMenu> modulos() {
        return menu.modulos();
    }

    @ModelAttribute("moduloAccent")
    public String moduloAccent() {
        return ACENTO;
    }

    /**
     * Marca la pantalla actual: la opción activa del menú (pagina), el título de la cabecera (paginaTitulo) y el
     * módulo al que pertenece (moduloTitulo).
     */
    public void pantalla(Model model, String pagina, String titulo) {
        model.addAttribute("pagina", pagina);
        model.addAttribute("paginaTitulo", titulo);
        model.addAttribute("moduloTitulo", menu.moduloDe(pagina).map(ModuloMenu::titulo).orElse(app.getNombre()));
    }
}
