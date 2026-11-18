package com.ejemplo.plantilla.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Inicio y catálogo de componentes (referencia de la plantilla, solo administradores). Todas las opciones del menú
 * tienen su propio controlador.
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
}
