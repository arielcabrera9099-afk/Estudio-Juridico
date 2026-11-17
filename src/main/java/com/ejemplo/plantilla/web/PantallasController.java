package com.ejemplo.plantilla.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rutas de la plantilla: el inicio (vacío), el catálogo de componentes y el login de ejemplo. Las pantallas nuevas
 * van en sus propios controladores, con la misma forma.
 */
@Controller
public class PantallasController {

    private final ModeloComun modelo;

    public PantallasController(ModeloComun modelo) {
        this.modelo = modelo;
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

    @GetMapping("/login")
    public String login() {
        return "pantallas/login";
    }
}
