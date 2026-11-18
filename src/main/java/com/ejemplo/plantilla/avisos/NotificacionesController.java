package com.ejemplo.plantilla.avisos;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ejemplo.plantilla.seguridad.UsuarioActual;
import com.ejemplo.plantilla.web.ModeloComun;
import com.ejemplo.plantilla.web.NoEncontrado;

/**
 * Avisos del usuario de la sesión (cualquier usuario: cada uno ve solo los suyos). La campana de la barra superior
 * pide el contador y el panel; la lista completa tiene filtros. No hay alta ni baja: los avisos los genera la agenda.
 */
@Controller
@RequestMapping("/notificaciones")
public class NotificacionesController {

    private final AvisosServicio servicio;
    private final ModeloComun modelo;

    public NotificacionesController(AvisosServicio servicio, ModeloComun modelo) {
        this.servicio = servicio;
        this.modelo = modelo;
    }

    private static long yo() {
        return UsuarioActual.id().orElseThrow();
    }

    @GetMapping
    public String lista(@RequestParam(defaultValue = "NO_LEIDAS") String estado, @RequestParam(required = false) String tipo,
                        Model model) {
        List<Map<String, Object>> filas = servicio.buscar(yo(), estado, tipo);
        boolean recortada = filas.size() > AvisosServicio.MAXIMO_FILAS;
        modelo.pantalla(model, "notificaciones", "Mis avisos");
        model.addAttribute("filas", recortada ? filas.subList(0, AvisosServicio.MAXIMO_FILAS) : filas);
        model.addAttribute("recortada", recortada);
        model.addAttribute("maximo", AvisosServicio.MAXIMO_FILAS);
        model.addAttribute("estado", estado);
        model.addAttribute("tipo", tipo);
        return "pantallas/avisos/lista";
    }

    /** Marca el aviso como leído y abre lo que avisa (el evento de agenda). */
    @GetMapping("/{id}/abrir")
    public String abrir(@PathVariable long id) {
        return "redirect:" + servicio.abrir(id, yo()).orElseThrow(NoEncontrado::new);
    }

    @PostMapping("/{id}/leida")
    public String marcarLeida(@PathVariable long id, @RequestParam(defaultValue = "NO_LEIDAS") String estado, RedirectAttributes redirect) {
        servicio.marcarLeida(id, yo());
        redirect.addAttribute("estado", estado);
        return "redirect:/notificaciones";
    }

    @PostMapping("/leer-todas")
    public String marcarTodas(RedirectAttributes redirect) {
        int n = servicio.marcarTodas(yo());
        redirect.addAttribute("ok", n == 0 ? "No había avisos sin leer." : n == 1 ? "Se marcó 1 aviso como leído." : "Se marcaron " + n + " avisos como leídos.");
        return "redirect:/notificaciones";
    }

    /** Cantidad de avisos sin leer, para el contador de la campana (se consulta cada minuto). */
    @GetMapping("/contador")
    @ResponseBody
    public Map<String, Integer> contador() {
        return Map.of("noLeidas", servicio.noLeidas(yo()));
    }

    /** Contenido del panel de la campana: los últimos avisos. */
    @GetMapping("/panel")
    public String panel(Model model) {
        model.addAttribute("avisos", servicio.recientes(yo(), 8));
        model.addAttribute("noLeidas", servicio.noLeidas(yo()));
        return "pantallas/avisos/panel :: panel";
    }
}
