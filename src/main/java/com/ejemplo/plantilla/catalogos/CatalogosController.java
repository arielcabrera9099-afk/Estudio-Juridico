package com.ejemplo.plantilla.catalogos;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.catalogos.Secciones.Seccion;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/**
 * Catálogos (permiso CATALOGOS_EDITAR, controlado por la ruta /admin/**). Cada opción del menú es una sección con uno
 * o más catálogos: la lista con filtros (el catálogo se elige en los filtros) y, desde ella, el ABM.
 * Rutas: /admin/{seccion}?catalogo=…, /admin/{seccion}/{catalogo}/nuevo, /{id}, /{id}/editar, /{id}/baja, /{id}/reactivar.
 */
@Controller
@RequestMapping("/admin/{seccion:materias|juzgados|ubicaciones|estados|tipos}")
public class CatalogosController {

    private final Secciones secciones;
    private final CatalogosServicio servicio;
    private final ModeloComun modelo;

    public CatalogosController(Secciones secciones, CatalogosServicio servicio, ModeloComun modelo) {
        this.secciones = secciones;
        this.servicio = servicio;
        this.modelo = modelo;
    }

    @GetMapping
    public String lista(@PathVariable String seccion, @RequestParam(required = false) String catalogo,
                        @RequestParam(required = false) String texto, @RequestParam(defaultValue = "ACTIVOS") String estado,
                        @RequestParam Map<String, String> params, Model model) {
        Seccion s = seccion(seccion);
        DefCatalogo c = catalogo == null ? s.getPrimero() : catalogo(s, catalogo);
        Map<String, Long> filtros = new HashMap<>();
        for (Campo f : c.getCampos()) {
            String v = params.get("f_" + f.getColumna());
            if (f.isFiltro() && v != null && v.matches("\\d{1,18}")) filtros.put(f.getColumna(), Long.valueOf(v));
        }
        modelo.pantalla(model, "admin/" + seccion, s.getTitulo());
        model.addAttribute("seccion", s);
        model.addAttribute("cat", c);
        model.addAttribute("filas", servicio.buscar(c, texto, estado, filtros));
        model.addAttribute("opcionesFiltro", servicio.opcionesFiltro(c));
        model.addAttribute("filtros", filtros);
        StringBuilder agregar = new StringBuilder("/admin/" + seccion + "/" + c.getTabla() + "/nuevo");
        filtros.forEach((col, id) -> agregar.append(agregar.indexOf("?") < 0 ? '?' : '&').append("f_").append(col).append('=').append(id));
        model.addAttribute("agregarUrl", agregar.toString());
        model.addAttribute("texto", texto);
        model.addAttribute("estado", estado);
        return "pantallas/catalogos/lista";
    }

    @GetMapping("/{catalogo}/nuevo")
    public String nuevo(@PathVariable String seccion, @PathVariable String catalogo, @RequestParam Map<String, String> params,
                        Model model) {
        Seccion s = seccion(seccion);
        DefCatalogo c = catalogo(s, catalogo);
        Map<String, String> valores = new HashMap<>();
        // Si se viene de la lista filtrada (por ejemplo, los tipos de proceso de una materia), ese filtro queda elegido.
        for (Campo f : c.getCampos()) {
            if (f.isFiltro() && params.get("f_" + f.getColumna()) != null) valores.put(f.getColumna(), params.get("f_" + f.getColumna()));
        }
        return formulario(model, s, c, null, valores, "alta", new Errores());
    }

    @PostMapping("/{catalogo}")
    public String crear(@PathVariable String seccion, @PathVariable String catalogo, @RequestParam Map<String, String> params,
                        Model model, RedirectAttributes redirect) {
        Seccion s = seccion(seccion);
        DefCatalogo c = catalogo(s, catalogo);
        Map<String, String> valores = new HashMap<>(params);
        Map<String, Object> convertidos = new LinkedHashMap<>();
        Errores errores = servicio.validar(c, null, valores, convertidos);
        if (errores.hay()) return formulario(model, s, c, null, valores, "alta", errores);
        servicio.crear(c, valores.get("codigo"), convertidos);
        redirect.addAttribute("ok", "Se dio de alta " + c.getConArticulo() + " " + convertidos.get("nombre") + ".");
        return lista(seccion, c);
    }

    @GetMapping("/{catalogo}/{id}")
    public String ver(@PathVariable String seccion, @PathVariable String catalogo, @PathVariable long id, Model model) {
        return mostrar(seccion, catalogo, id, "ver", model);
    }

    @GetMapping("/{catalogo}/{id}/editar")
    public String editar(@PathVariable String seccion, @PathVariable String catalogo, @PathVariable long id, Model model) {
        return mostrar(seccion, catalogo, id, "editar", model);
    }

    @PostMapping("/{catalogo}/{id}")
    public String modificar(@PathVariable String seccion, @PathVariable String catalogo, @PathVariable long id,
                            @RequestParam Map<String, String> params, Model model, RedirectAttributes redirect) {
        Seccion s = seccion(seccion);
        DefCatalogo c = catalogo(s, catalogo);
        Map<String, Object> ficha = servicio.ficha(c, id).orElseThrow(NoEncontrado::new);
        Map<String, String> valores = new HashMap<>(params);
        valores.put("codigo", (String) ficha.get("codigo"));
        Map<String, Object> convertidos = new LinkedHashMap<>();
        Errores errores = servicio.validar(c, id, valores, convertidos);
        if (errores.hay()) return formulario(model, s, c, ficha, valores, "editar", errores);
        servicio.modificar(c, id, convertidos);
        redirect.addAttribute("ok", "Se guardaron los cambios de " + c.getConArticulo() + " " + convertidos.get("nombre") + ".");
        return lista(seccion, c);
    }

    @PostMapping("/{catalogo}/{id}/baja")
    public String baja(@PathVariable String seccion, @PathVariable String catalogo, @PathVariable long id,
                       RedirectAttributes redirect) {
        DefCatalogo c = catalogo(seccion(seccion), catalogo);
        String nombre = nombre(c, id);
        servicio.darDeBaja(c, id).ifPresentOrElse(
                motivo -> redirect.addAttribute("error", motivo),
                () -> redirect.addAttribute("ok", "Se dio de baja " + c.getConArticulo() + " " + nombre
                        + ". Ya no se ofrece en las altas, pero se conserva en los registros que lo usan."));
        return lista(seccion, c);
    }

    @PostMapping("/{catalogo}/{id}/reactivar")
    public String reactivar(@PathVariable String seccion, @PathVariable String catalogo, @PathVariable long id,
                            RedirectAttributes redirect) {
        DefCatalogo c = catalogo(seccion(seccion), catalogo);
        String nombre = nombre(c, id);
        servicio.reactivar(c, id).ifPresentOrElse(
                motivo -> redirect.addAttribute("error", motivo),
                () -> redirect.addAttribute("ok", "Se reactivó " + c.getConArticulo() + " " + nombre + "."));
        return lista(seccion, c);
    }

    // ------------------------------------------------------------------------------------------------ auxiliares

    private Seccion seccion(String clave) {
        return secciones.seccion(clave).orElseThrow(NoEncontrado::new);
    }

    private static DefCatalogo catalogo(Seccion s, String tabla) {
        DefCatalogo c = s.getCatalogos().get(tabla);
        if (c == null) throw new NoEncontrado();
        return c;
    }

    private String nombre(DefCatalogo c, long id) {
        return servicio.ficha(c, id).map(f -> (String) f.get("nombre")).orElseThrow(NoEncontrado::new);
    }

    private static String lista(String seccion, DefCatalogo c) {
        return "redirect:/admin/" + seccion + "?catalogo=" + c.getTabla();
    }

    private String mostrar(String seccion, String catalogo, long id, String modo, Model model) {
        Seccion s = seccion(seccion);
        DefCatalogo c = catalogo(s, catalogo);
        Map<String, Object> ficha = servicio.ficha(c, id).orElseThrow(NoEncontrado::new);
        return formulario(model, s, c, ficha, servicio.formulario(c, ficha), modo, new Errores());
    }

    private String formulario(Model model, Seccion s, DefCatalogo c, Map<String, Object> ficha, Map<String, String> valores,
                              String modo, Errores errores) {
        String titulo = "alta".equals(modo) ? c.getTituloNuevo()
                : "ver".equals(modo) ? c.getTituloSingular() : "Modificar " + c.getSingular();
        modelo.pantalla(model, "admin/" + s.getClave(), titulo);
        model.addAttribute("seccion", s);
        model.addAttribute("cat", c);
        model.addAttribute("ficha", ficha);
        model.addAttribute("valores", valores);
        model.addAttribute("opciones", servicio.opciones(c, valores));
        model.addAttribute("modo", modo);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("errores", errores);
        return "pantallas/catalogos/form";
    }
}
