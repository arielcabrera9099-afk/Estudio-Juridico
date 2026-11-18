package com.ejemplo.plantilla.clientes;

import java.util.List;
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

import com.ejemplo.plantilla.datos.Catalogos;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/**
 * Clientes: lista con filtros y, desde ella, alta, visualización (con contactos e historial de casos), modificación,
 * baja lógica y reactivación. Ver requiere CLIENTES_VER (por la ruta); cualquier cambio, CLIENTES_EDITAR.
 */
@Controller
@RequestMapping("/clientes")
public class ClientesController {

    private static final String PAGINA = "clientes";
    private static final String EDITAR = "CLIENTES_EDITAR";

    private final ClientesServicio servicio;
    private final Catalogos catalogos;
    private final ModeloComun modelo;

    public ClientesController(ClientesServicio servicio, Catalogos catalogos, ModeloComun modelo) {
        this.servicio = servicio;
        this.catalogos = catalogos;
        this.modelo = modelo;
    }

    @ModelAttribute("puedeEditar")
    public boolean puedeEditar() {
        return UsuarioActual.tiene(EDITAR);
    }

    // ------------------------------------------------------------------------------------------------ lista

    @GetMapping
    public String lista(@RequestParam(required = false) String texto, @RequestParam(defaultValue = "") String tipo,
                        @RequestParam(defaultValue = "ACTIVOS") String estado, Model model) {
        List<Map<String, Object>> filas = servicio.buscar(texto, tipo, estado);
        boolean recortada = filas.size() > ClientesServicio.MAXIMO_FILAS;
        modelo.pantalla(model, PAGINA, "Clientes");
        model.addAttribute("filas", recortada ? filas.subList(0, ClientesServicio.MAXIMO_FILAS) : filas);
        model.addAttribute("recortada", recortada);
        model.addAttribute("maximo", ClientesServicio.MAXIMO_FILAS);
        model.addAttribute("texto", texto);
        model.addAttribute("tipo", tipo);
        model.addAttribute("estado", estado);
        return "pantallas/clientes/lista";
    }

    // ------------------------------------------------------------------------------------------------ ABM

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        UsuarioActual.exigir(EDITAR);
        return formulario(model, new ClienteForm(), "alta", new Errores());
    }

    @PostMapping
    public String crear(@ModelAttribute("form") ClienteForm form, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        form.setId(null);
        form.setActivo(true);
        form.normalizar();
        Errores errores = servicio.validar(form);
        if (errores.hay()) return formulario(model, form, "alta", errores);
        long id = servicio.crear(form);
        redirect.addAttribute("ok", "Se dio de alta el cliente " + form.getNombreMostrar() + ".");
        return "redirect:/clientes/" + id;
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable long id, Model model) {
        ClienteForm form = servicio.formulario(id).orElseThrow(NoEncontrado::new);
        model.addAttribute("contactos", servicio.contactos(id));
        model.addAttribute("historial", servicio.historial(id));
        return formulario(model, form, "ver", new Errores());
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable long id, Model model) {
        UsuarioActual.exigir(EDITAR);
        return formulario(model, servicio.formulario(id).orElseThrow(NoEncontrado::new), "editar", new Errores());
    }

    @PostMapping("/{id}")
    public String modificar(@PathVariable long id, @ModelAttribute("form") ClienteForm form, Model model,
                            RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        servicio.formulario(id).orElseThrow(NoEncontrado::new);
        form.setId(id);
        form.normalizar();
        Errores errores = servicio.validar(form);
        if (errores.hay()) return formulario(model, form, "editar", errores);
        servicio.modificar(form);
        redirect.addAttribute("ok", "Se guardaron los cambios del cliente " + form.getNombreMostrar() + ".");
        return "redirect:/clientes";
    }

    @PostMapping("/{id}/baja")
    public String baja(@PathVariable long id, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        String nombre = nombre(id);
        servicio.darDeBaja(id).ifPresentOrElse(
                motivo -> redirect.addAttribute("error", motivo),
                () -> redirect.addAttribute("ok", "Se dio de baja el cliente " + nombre + ". Conserva su historial."));
        return "redirect:/clientes";
    }

    @PostMapping("/{id}/reactivar")
    public String reactivar(@PathVariable long id, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        String nombre = nombre(id);
        servicio.reactivar(id);
        redirect.addAttribute("ok", "Se reactivó el cliente " + nombre + ".");
        return "redirect:/clientes";
    }

    // ------------------------------------------------------------------------------------------------ contactos

    @GetMapping("/{id}/contactos/nuevo")
    public String nuevoContacto(@PathVariable long id, Model model) {
        UsuarioActual.exigir(EDITAR);
        return formularioContacto(model, id, new ContactoForm(), new Errores());
    }

    @PostMapping("/{id}/contactos")
    public String crearContacto(@PathVariable long id, @ModelAttribute("contacto") ContactoForm form, Model model,
                                RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        nombre(id);
        form.setId(null);
        form.normalizar();
        Errores errores = servicio.validar(form);
        if (errores.hay()) return formularioContacto(model, id, form, errores);
        servicio.crearContacto(id, form);
        redirect.addAttribute("ok", "Se agregó el contacto " + form.getNombre() + ".");
        return "redirect:/clientes/" + id + "#contactos";
    }

    @GetMapping("/{id}/contactos/{contactoId}/editar")
    public String editarContacto(@PathVariable long id, @PathVariable long contactoId, Model model) {
        UsuarioActual.exigir(EDITAR);
        return formularioContacto(model, id, servicio.contacto(id, contactoId).orElseThrow(NoEncontrado::new), new Errores());
    }

    @PostMapping("/{id}/contactos/{contactoId}")
    public String modificarContacto(@PathVariable long id, @PathVariable long contactoId,
                                    @ModelAttribute("contacto") ContactoForm form, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        servicio.contacto(id, contactoId).orElseThrow(NoEncontrado::new);
        form.setId(contactoId);
        form.normalizar();
        Errores errores = servicio.validar(form);
        if (errores.hay()) return formularioContacto(model, id, form, errores);
        servicio.modificarContacto(id, form);
        redirect.addAttribute("ok", "Se guardaron los cambios del contacto " + form.getNombre() + ".");
        return "redirect:/clientes/" + id + "#contactos";
    }

    @PostMapping("/{id}/contactos/{contactoId}/eliminar")
    public String eliminarContacto(@PathVariable long id, @PathVariable long contactoId, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        ContactoForm c = servicio.contacto(id, contactoId).orElseThrow(NoEncontrado::new);
        servicio.eliminarContacto(id, contactoId);
        redirect.addAttribute("ok", "Se eliminó el contacto " + c.getNombre() + ".");
        return "redirect:/clientes/" + id + "#contactos";
    }

    // ------------------------------------------------------------------------------------------------ auxiliares

    private String nombre(long id) {
        return servicio.ficha(id).map(f -> (String) f.get("nombre")).orElseThrow(NoEncontrado::new);
    }

    private String formulario(Model model, ClienteForm form, String modo, Errores errores) {
        String titulo = "alta".equals(modo) ? "Nuevo cliente" : "ver".equals(modo) ? "Cliente" : "Modificar cliente";
        modelo.pantalla(model, PAGINA, titulo);
        model.addAttribute("form", form);
        model.addAttribute("modo", modo);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("errores", errores);
        model.addAttribute("tiposDocumento", catalogos.tiposDocumentoIdentidad());
        model.addAttribute("ciudades", catalogos.ciudades());
        if (form.getId() != null) model.addAttribute("ficha", servicio.ficha(form.getId()).orElseThrow(NoEncontrado::new));
        return "pantallas/clientes/form";
    }

    private String formularioContacto(Model model, long clienteId, ContactoForm form, Errores errores) {
        modelo.pantalla(model, PAGINA, form.getId() == null ? "Nuevo contacto" : "Modificar contacto");
        model.addAttribute("cliente", servicio.ficha(clienteId).orElseThrow(NoEncontrado::new));
        model.addAttribute("contacto", form);
        model.addAttribute("errores", errores);
        return "pantallas/clientes/contacto";
    }
}
