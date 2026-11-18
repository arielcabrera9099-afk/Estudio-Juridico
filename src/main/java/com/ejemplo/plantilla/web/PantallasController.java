package com.ejemplo.plantilla.web;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ejemplo.plantilla.web.menu.MenuAplicacion;

/**
 * Inicio, catálogo de componentes (referencia de la plantilla, solo administradores) y las opciones del menú cuyas
 * pantallas todavía no están construidas: muestran un aviso en vez de una página de error. Cada pantalla nueva sale de
 * la lista de {@link #pendiente}.
 */
@Controller
public class PantallasController {

    private final ModeloComun modelo;
    private final MenuAplicacion menu;

    public PantallasController(ModeloComun modelo, MenuAplicacion menu) {
        this.modelo = modelo;
        this.menu = menu;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        modelo.pantalla(model, "inicio", "Inicio");
        return "pantallas/inicio";
    }

    @GetMapping("/componentes")
    public String componentes(Model model) {
        modelo.pantalla(model, "componentes", "Componentes");
        return "pantallas/catalogo";
    }

    @GetMapping({"/documentos",
            "/reportes/expedientes", "/reportes/agenda"})
    public String pendiente(HttpServletRequest request, Model model) {
        String pagina = request.getRequestURI().substring(request.getContextPath().length()).replaceFirst("^/", "");
        modelo.pantalla(model, pagina, menu.tituloDe(pagina).orElse("En construcción"));
        return "pantallas/en-construccion";
    }
}
