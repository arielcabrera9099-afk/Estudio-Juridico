package com.ejemplo.plantilla.clientes;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.datos.Catalogos;
import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/** Teléfonos del cliente, desde su ficha (CLIENTES_EDITAR): alta, modificación y baja. Vuelven a la ficha. */
@Controller
@RequestMapping("/clientes/{id}/telefonos")
public class TelefonosController {

    private static final String EDITAR = "CLIENTES_EDITAR";

    private final TelefonosServicio servicio;
    private final ClientesServicio clientes;
    private final Catalogos catalogos;
    private final ModeloComun modelo;

    public TelefonosController(TelefonosServicio servicio, ClientesServicio clientes, Catalogos catalogos, ModeloComun modelo) {
        this.servicio = servicio;
        this.clientes = clientes;
        this.catalogos = catalogos;
        this.modelo = modelo;
    }

    @GetMapping("/nuevo")
    public String nuevo(@PathVariable long id, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        if (servicio.documento(id).isEmpty()) return sinDocumento(id, redirect);
        TelefonoForm t = new TelefonoForm();
        catalogos.prefijos(null).stream().filter(p -> "PY".equals(p.getCodigo())).findFirst().ifPresent(p -> t.setPaisId((Long) p.getId()));
        return pantalla(model, id, t, new Errores());
    }

    @PostMapping
    public String crear(@PathVariable long id, @ModelAttribute("telefono") TelefonoForm t, Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        if (servicio.documento(id).isEmpty()) return sinDocumento(id, redirect);
        t.setId(null);
        t.normalizar();
        Errores errores = servicio.validar(id, t);
        if (errores.hay()) return pantalla(model, id, t, errores);
        servicio.crear(id, t);
        redirect.addAttribute("ok", "Se agregó el teléfono " + t.getNumero() + ".");
        return "redirect:/clientes/" + id + "#telefonos";
    }

    @GetMapping("/{telefonoId}/editar")
    public String editar(@PathVariable long id, @PathVariable long telefonoId, Model model) {
        UsuarioActual.exigir(EDITAR);
        return pantalla(model, id, servicio.telefono(id, telefonoId).orElseThrow(NoEncontrado::new), new Errores());
    }

    @PostMapping("/{telefonoId}")
    public String modificar(@PathVariable long id, @PathVariable long telefonoId, @ModelAttribute("telefono") TelefonoForm t,
                            Model model, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        servicio.telefono(id, telefonoId).orElseThrow(NoEncontrado::new);
        t.setId(telefonoId);
        t.normalizar();
        Errores errores = servicio.validar(id, t);
        if (errores.hay()) return pantalla(model, id, t, errores);
        servicio.modificar(id, t);
        redirect.addAttribute("ok", "Se guardaron los cambios del teléfono " + t.getNumero() + ".");
        return "redirect:/clientes/" + id + "#telefonos";
    }

    @PostMapping("/{telefonoId}/eliminar")
    public String eliminar(@PathVariable long id, @PathVariable long telefonoId, RedirectAttributes redirect) {
        UsuarioActual.exigir(EDITAR);
        TelefonoForm t = servicio.telefono(id, telefonoId).orElseThrow(NoEncontrado::new);
        servicio.eliminar(id, telefonoId).ifPresentOrElse(
                motivo -> redirect.addAttribute("error", motivo),
                () -> redirect.addAttribute("ok", "Se quitó el teléfono " + t.getNumero() + "."));
        return "redirect:/clientes/" + id + "#telefonos";
    }

    private static String sinDocumento(long id, RedirectAttributes redirect) {
        redirect.addAttribute("error", "El cliente no tiene documento: complételo con Modificar antes de cargar teléfonos.");
        return "redirect:/clientes/" + id;
    }

    private String pantalla(Model model, long clienteId, TelefonoForm t, Errores errores) {
        Map<String, Object> cliente = clientes.ficha(clienteId).orElseThrow(NoEncontrado::new);
        modelo.pantalla(model, "clientes", t.getId() == null ? "Nuevo teléfono" : "Modificar teléfono");
        model.addAttribute("cliente", cliente);
        model.addAttribute("telefono", t);
        model.addAttribute("prefijos", catalogos.prefijos(t.getPaisId()));
        model.addAttribute("errores", errores);
        return "pantallas/clientes/telefono";
    }
}
