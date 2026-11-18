package com.ejemplo.plantilla.parametros;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/**
 * Parámetros del sistema (permiso SEGURIDAD_ADMINISTRAR, controlado por la ruta): lista con filtros, visualización y
 * modificación del valor. No hay alta ni baja: los parámetros los define el sistema.
 */
@Controller
@RequestMapping("/admin/parametros")
public class ParametrosController {

    private static final String PAGINA = "admin/parametros";
    static final Map<String, String> GRUPOS = Map.of("seguridad", "Seguridad", "agenda", "Agenda",
            "documentos", "Documentos", "expediente", "Expedientes");

    private final ParametrosServicio servicio;
    private final ModeloComun modelo;

    public ParametrosController(ParametrosServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String texto, @RequestParam(required = false) String grupo,
                        Model model) {
        modelo.pantalla(model, PAGINA, "Parámetros");
        model.addAttribute("filas", servicio.buscar(texto, grupo));
        model.addAttribute("grupos", servicio.grupos());
        model.addAttribute("nombresGrupo", GRUPOS);
        model.addAttribute("texto", texto);
        model.addAttribute("grupo", grupo);
        return "pantallas/parametros/lista";
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable long id, Model model) {
        Map<String, Object> f = ficha(id);
        return formulario(model, f, (String) f.get("valor"), "ver", new Errores());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable long id, Model model) {
        Map<String, Object> f = ficha(id);
        return formulario(model, f, (String) f.get("valor"), "editar", new Errores());
    }

    @PostMapping("/{id}")
    public String modificar(@PathVariable long id, @RequestParam(defaultValue = "") String valor, Model model,
                            RedirectAttributes redirect) {
        Map<String, Object> f = ficha(id);
        String clave = (String) f.get("clave");
        Errores errores = new Errores();
        String normalizado = servicio.validar(clave, (String) f.get("tipo"), valor, errores);
        if (errores.hay()) return formulario(model, f, valor, "editar", errores);
        servicio.modificar(id, clave, (String) f.get("valor"), normalizado);
        redirect.addAttribute("ok", "Se guardó el parámetro " + clave + " = " + normalizado + ". Rige desde ahora.");
        return "redirect:/admin/parametros";
    }

    private Map<String, Object> ficha(long id) {
        return servicio.ficha(id).orElseThrow(NoEncontrado::new);
    }

    private String formulario(Model model, Map<String, Object> ficha, String valor, String modo, Errores errores) {
        modelo.pantalla(model, PAGINA, "ver".equals(modo) ? "Parámetro" : "Modificar parámetro");
        model.addAttribute("ficha", ficha);
        model.addAttribute("valor", valor);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("errores", errores);
        model.addAttribute("ayuda", servicio.ayuda((String) ficha.get("clave"), (String) ficha.get("tipo")));
        return "pantallas/parametros/form";
    }
}
