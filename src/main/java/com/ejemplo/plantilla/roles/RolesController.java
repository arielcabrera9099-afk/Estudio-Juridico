package com.ejemplo.plantilla.roles;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/**
 * Roles y permisos (permiso SEGURIDAD_ADMINISTRAR, controlado por la ruta): lista con filtros y, desde ella, alta,
 * visualización, modificación (incluida la matriz de permisos), baja lógica y reactivación.
 */
@Controller
@RequestMapping("/admin/roles")
public class RolesController {

    private static final String PAGINA = "admin/roles";
    private static final String LISTA = "redirect:/admin/roles";
    private static final Map<String, String> NOMBRES_MODULO = Map.of("CLIENTES", "Clientes", "EXPEDIENTES", "Expedientes",
            "AGENDA", "Agenda", "DOCUMENTOS", "Documentos", "REPORTES", "Reportes", "CATALOGOS", "Catálogos",
            "SEGURIDAD", "Seguridad y auditoría");

    private final RolesServicio servicio;
    private final ModeloComun modelo;

    public RolesController(RolesServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String texto,
                        @RequestParam(defaultValue = "ACTIVOS") String estado, Model model) {
        modelo.pantalla(model, PAGINA, "Roles y permisos");
        model.addAttribute("filas", servicio.buscar(texto, estado));
        model.addAttribute("texto", texto);
        model.addAttribute("estado", estado);
        return "pantallas/roles/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new RolForm(), "alta", new Errores());
    }

    @PostMapping
    public String crear(@ModelAttribute("form") RolForm form, Model model, RedirectAttributes redirect) {
        form.setId(null);
        form.normalizar();
        Errores errores = servicio.validar(form, true);
        if (errores.hay()) return formulario(model, form, "alta", errores);
        servicio.crear(form);
        redirect.addAttribute("ok", "Se dio de alta el rol " + form.getNombre() + ". Ya puede asignarse a los usuarios.");
        return LISTA;
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable long id, Model model) {
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "ver", new Errores());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable long id, Model model) {
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "editar", new Errores());
    }

    @PostMapping("/{id}")
    public String modificar(@PathVariable long id, @ModelAttribute("form") RolForm form, Model model,
                            RedirectAttributes redirect) {
        RolForm actual = servicio.formulario(id).orElseThrow(NoEncontrado::new);
        form.setId(id);
        form.normalizar();
        form.setCodigo(actual.getCodigo());
        if (servicio.esAdministrador(form)) {
            // En el formulario sus permisos y su estado van deshabilitados: se conservan los actuales.
            form.setActivo(true);
            form.setPermisos(actual.getPermisos());
        }
        Errores errores = servicio.validar(form, false);
        if (errores.hay()) return formulario(model, form, "editar", errores);
        servicio.modificar(form);
        redirect.addAttribute("ok", "Se guardaron los cambios del rol " + form.getNombre()
                + ". Los permisos nuevos rigen desde el próximo ingreso de cada usuario.");
        return LISTA;
    }

    @PostMapping("/{id}/baja")
    public String baja(@PathVariable long id, RedirectAttributes redirect) {
        String nombre = nombre(id);
        servicio.darDeBaja(id).ifPresentOrElse(
                motivo -> redirect.addAttribute("error", motivo),
                () -> redirect.addAttribute("ok", "Se dio de baja el rol " + nombre + ". Ya no puede asignarse a usuarios."));
        return LISTA;
    }

    @PostMapping("/{id}/reactivar")
    public String reactivar(@PathVariable long id, RedirectAttributes redirect) {
        String nombre = nombre(id);
        servicio.reactivar(id);
        redirect.addAttribute("ok", "Se reactivó el rol " + nombre + ".");
        return LISTA;
    }

    private String nombre(long id) {
        return servicio.ficha(id).map(f -> (String) f.get("nombre")).orElseThrow(NoEncontrado::new);
    }

    private String formulario(Model model, RolForm form, String modo, Errores errores) {
        String titulo = "alta".equals(modo) ? "Nuevo rol" : "ver".equals(modo) ? "Rol" : "Modificar rol";
        modelo.pantalla(model, PAGINA, titulo);
        model.addAttribute("form", form);
        model.addAttribute("modo", modo);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("administrador", servicio.esAdministrador(form));
        model.addAttribute("errores", errores);
        model.addAttribute("gruposPermisos", servicio.permisosPorModulo());
        model.addAttribute("nombresModulo", NOMBRES_MODULO);
        if (form.getId() != null) {
            model.addAttribute("ficha", servicio.ficha(form.getId()).orElseThrow(NoEncontrado::new));
            model.addAttribute("usuarios", servicio.usuarios(form.getId()));
        }
        return "pantallas/roles/form";
    }
}
