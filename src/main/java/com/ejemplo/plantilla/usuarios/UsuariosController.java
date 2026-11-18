package com.ejemplo.plantilla.usuarios;

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

import com.ejemplo.plantilla.seguridad.ReglasClave;
import com.ejemplo.plantilla.web.Errores;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/**
 * Administración de usuarios (permiso SEGURIDAD_ADMINISTRAR, controlado por la ruta): lista con filtros y, desde
 * ella, alta, visualización, modificación, baja lógica, reactivación, desbloqueo y reinicio de clave.
 */
@Controller
@RequestMapping("/admin/usuarios")
public class UsuariosController {

    private static final String PAGINA = "admin/usuarios";
    private static final String LISTA = "redirect:/admin/usuarios";

    private final UsuariosServicio servicio;
    private final ModeloComun modelo;

    public UsuariosController(UsuariosServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    @GetMapping
    public String lista(@RequestParam(required = false) String texto, @RequestParam(required = false) Long rol,
                        @RequestParam(defaultValue = "ACTIVOS") String estado, Model model) {
        List<Map<String, Object>> filas = servicio.buscar(texto, rol, estado);
        boolean recortada = filas.size() > UsuariosServicio.MAXIMO_FILAS;
        modelo.pantalla(model, PAGINA, "Usuarios");
        model.addAttribute("filas", recortada ? filas.subList(0, UsuariosServicio.MAXIMO_FILAS) : filas);
        model.addAttribute("recortada", recortada);
        model.addAttribute("maximo", UsuariosServicio.MAXIMO_FILAS);
        model.addAttribute("roles", servicio.roles());
        model.addAttribute("texto", texto);
        model.addAttribute("rol", rol);
        model.addAttribute("estado", estado);
        return "pantallas/usuarios/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new UsuarioForm(), "alta", new Errores());
    }

    @PostMapping
    public String crear(@ModelAttribute("form") UsuarioForm form, Model model, RedirectAttributes redirect) {
        form.setId(null);
        form.normalizar();
        Errores errores = servicio.validar(form, true);
        if (errores.hay()) return formulario(model, form, "alta", errores);
        servicio.crear(form);
        redirect.addAttribute("ok", "Se dio de alta el usuario " + form.getNombreUsuario()
                + ". Deberá cambiar la contraseña en su primer ingreso.");
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
    public String modificar(@PathVariable long id, @ModelAttribute("form") UsuarioForm form, Model model,
                            RedirectAttributes redirect) {
        UsuarioForm actual = servicio.formulario(id).orElseThrow(NoEncontrado::new);
        form.setId(id);
        form.setNombreUsuario(actual.getNombreUsuario());
        form.normalizar();
        Errores errores = servicio.validar(form, false);
        if (errores.hay()) return formulario(model, form, "editar", errores);
        servicio.modificar(form);
        redirect.addAttribute("ok", "Se guardaron los cambios del usuario " + actual.getNombreUsuario() + ".");
        return LISTA;
    }

    @PostMapping("/{id}/baja")
    public String baja(@PathVariable long id, RedirectAttributes redirect) {
        String usuario = nombre(id);
        servicio.darDeBaja(id).ifPresentOrElse(
                motivo -> redirect.addAttribute("error", motivo),
                () -> redirect.addAttribute("ok", "Se dio de baja el usuario " + usuario + ". Ya no puede ingresar."));
        return LISTA;
    }

    @PostMapping("/{id}/reactivar")
    public String reactivar(@PathVariable long id, RedirectAttributes redirect) {
        String usuario = nombre(id);
        servicio.reactivar(id);
        redirect.addAttribute("ok", "Se reactivó el usuario " + usuario + ".");
        return LISTA;
    }

    @PostMapping("/{id}/desbloquear")
    public String desbloquear(@PathVariable long id, RedirectAttributes redirect) {
        String usuario = nombre(id);
        servicio.desbloquear(id);
        redirect.addAttribute("ok", "Se desbloqueó el usuario " + usuario + ".");
        return LISTA;
    }

    @GetMapping("/{id}/clave")
    public String clave(@PathVariable long id, Model model) {
        return pantallaClave(model, id, new Errores());
    }

    @PostMapping("/{id}/clave")
    public String reiniciarClave(@PathVariable long id, @RequestParam(defaultValue = "") String clave,
                                 @RequestParam(defaultValue = "") String repetir, Model model, RedirectAttributes redirect) {
        String usuario = nombre(id);
        Errores errores = new Errores();
        ReglasClave.validar(clave, usuario).ifPresent(m -> errores.campo("clave", m));
        if (!clave.equals(repetir)) errores.campo("repetir", "Las contraseñas no coinciden.");
        if (errores.hay()) return pantallaClave(model, id, errores);
        servicio.reiniciarClave(id, clave);
        redirect.addAttribute("ok", "Se reinició la contraseña de " + usuario + ". Deberá cambiarla en su próximo ingreso.");
        return LISTA;
    }

    private String nombre(long id) {
        return servicio.ficha(id).map(f -> (String) f.get("nombre_usuario")).orElseThrow(NoEncontrado::new);
    }

    private String pantallaClave(Model model, long id, Errores errores) {
        modelo.pantalla(model, PAGINA, "Reiniciar contraseña");
        model.addAttribute("usuarioFicha", servicio.ficha(id).orElseThrow(NoEncontrado::new));
        model.addAttribute("errores", errores);
        model.addAttribute("reglas", ReglasClave.DESCRIPCION);
        return "pantallas/usuarios/clave";
    }

    private String formulario(Model model, UsuarioForm form, String modo, Errores errores) {
        String titulo = "alta".equals(modo) ? "Nuevo usuario" : "ver".equals(modo) ? "Usuario" : "Modificar usuario";
        modelo.pantalla(model, PAGINA, titulo);
        model.addAttribute("form", form);
        model.addAttribute("modo", modo);
        model.addAttribute("ver", "ver".equals(modo));
        model.addAttribute("errores", errores);
        model.addAttribute("roles", servicio.roles());
        model.addAttribute("reglas", ReglasClave.DESCRIPCION);
        if (form.getId() != null) model.addAttribute("ficha", servicio.ficha(form.getId()).orElseThrow(NoEncontrado::new));
        return "pantallas/usuarios/form";
    }
}
